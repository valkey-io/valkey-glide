// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Fork-safety tests for the process-global `TimeoutWatchdog`, run against a
//! real server.
//!
//! `fork()` copies only the calling thread, so a child that kept the parent's
//! watchdog would register deadlines on a thread that does not exist. On macOS
//! that send traps (`SIGTRAP`) and kills the child.
//!
//! These check what a caller sees: the child keeps running, its commands
//! succeed, and the parent still works. The unit tests in
//! `glide-core/src/timeout_watchdog.rs` check that a child's deadline fires.

#![cfg(unix)]

mod constants;
mod utilities;

use crate::utilities::{
    RedisServer, ServerType, TestConfiguration, create_connection_request,
    wait_for_server_to_become_ready,
};
use glide_core::client::Client;
use glide_core::timeout_watchdog::pending_count;
use redis::{ConnectionAddr, Value};
use serial_test::serial;
use std::sync::{Arc, Barrier};
use std::time::{Duration, Instant};

const REQUEST_TIMEOUT: Duration = Duration::from_millis(250);

/// A child still running after this is killed, so a hung child fails the
/// test instead of stalling the job.
const CHILD_DEADLINE: Duration = Duration::from_secs(30);

/// Children report through their exit code, because printing from a forked
/// child can deadlock on an inherited stdio lock.
mod verdict {
    pub const OK: i32 = 0;
    pub const CLIENT_CREATE_FAILED: i32 = 20;
    pub const COMMAND_FAILED: i32 = 21;
}

// ─── Test server and clients ─────────────────────────────────────────────────

fn start_server() -> RedisServer {
    RedisServer::new(ServerType::Tcp { tls: false })
}

fn test_configuration() -> TestConfiguration {
    TestConfiguration {
        request_timeout: Some(REQUEST_TIMEOUT.as_millis() as u32),
        ..Default::default()
    }
}

/// Returns `None` instead of panicking, because forked children call it too.
async fn connect(addr: &ConnectionAddr) -> Option<Client> {
    Client::new(
        create_connection_request(std::slice::from_ref(addr), &test_configuration()).into(),
        None,
    )
    .await
    .ok()
}

async fn ping_succeeds(client: &mut Client) -> bool {
    let mut ping = redis::cmd("PING");
    matches!(
        client.send_command(&mut ping, None).await,
        Ok(Value::SimpleString(ref reply)) if reply == "PONG"
    )
}

fn build_runtime() -> tokio::runtime::Runtime {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .expect("failed to build runtime")
}

/// A child cannot reuse the parent's runtime, whose worker threads did not
/// survive the fork. Leaked, because the child exits with `_exit` anyway.
fn leaked_runtime() -> &'static tokio::runtime::Runtime {
    Box::leak(Box::new(build_runtime()))
}

/// Connect a parent client and send one command, which starts the global
/// watchdog. Then wait until the watchdog has no pending deadline, so the
/// fork happens while it is idle.
fn parent_with_watchdog_installed(
    runtime: &tokio::runtime::Runtime,
    addr: &ConnectionAddr,
) -> Client {
    let mut parent = runtime.block_on(async {
        wait_for_server_to_become_ready(addr).await;
        connect(addr).await.expect("parent client")
    });
    assert!(
        runtime.block_on(ping_succeeds(&mut parent)),
        "parent before fork: PING failed"
    );
    let deadline = Instant::now() + Duration::from_secs(5);
    while pending_count() != 0 {
        assert!(
            Instant::now() < deadline,
            "parent watchdog still had {} pending deadlines after 5 s",
            pending_count()
        );
        std::thread::sleep(Duration::from_millis(10));
    }
    parent
}

// ─── Fork helpers ────────────────────────────────────────────────────────────

/// How a forked child ended.
#[derive(Debug)]
enum ChildOutcome {
    Exited(i32),
    Signaled(i32),
    /// Still running at [`CHILD_DEADLINE`], and killed.
    TimedOut,
}

/// Run `child` in a forked child, which leaves through `_exit` so no
/// destructor or harness teardown runs there.
fn run_in_forked_child(child: impl FnOnce() -> i32) -> ChildOutcome {
    // Safety: the child only talks to its own client and then `_exit`s.
    let pid = unsafe { libc::fork() };
    assert!(pid >= 0, "fork() failed");

    if pid == 0 {
        let code = child();
        unsafe { libc::_exit(code) };
    }

    match reap_before(pid, Instant::now() + CHILD_DEADLINE) {
        Some(outcome) => outcome,
        None => {
            unsafe { libc::kill(pid, libc::SIGKILL) };
            let mut status: libc::c_int = 0;
            unsafe { libc::waitpid(pid, &mut status, 0) };
            ChildOutcome::TimedOut
        }
    }
}

/// Poll with `WNOHANG` until `deadline`, so a hung child cannot block the
/// test.
fn reap_before(pid: libc::pid_t, deadline: Instant) -> Option<ChildOutcome> {
    loop {
        let mut status: libc::c_int = 0;
        match unsafe { libc::waitpid(pid, &mut status, libc::WNOHANG) } {
            0 if Instant::now() >= deadline => return None,
            0 => std::thread::sleep(Duration::from_millis(20)),
            reaped if reaped == pid => return Some(child_outcome(status)),
            -1 => panic!("waitpid failed: {}", std::io::Error::last_os_error()),
            other => panic!("waitpid returned unexpected pid {other}, expected {pid}"),
        }
    }
}

fn child_outcome(status: libc::c_int) -> ChildOutcome {
    if libc::WIFEXITED(status) {
        ChildOutcome::Exited(libc::WEXITSTATUS(status))
    } else if libc::WIFSIGNALED(status) {
        ChildOutcome::Signaled(libc::WTERMSIG(status))
    } else {
        panic!("child neither exited nor was signaled (raw status {status})")
    }
}

fn assert_child_ok(outcome: ChildOutcome, what: &str) {
    let code = match outcome {
        ChildOutcome::Exited(code) => code,
        ChildOutcome::Signaled(signal) => panic!(
            "{what}: child was killed by signal {signal}{} instead of exiting. \
             It likely kept the parent's TimeoutWatchdog",
            match signal {
                libc::SIGTRAP => " (SIGTRAP)",
                libc::SIGABRT => " (SIGABRT)",
                libc::SIGSEGV => " (SIGSEGV)",
                libc::SIGBUS => " (SIGBUS)",
                _ => "",
            }
        ),
        ChildOutcome::TimedOut => {
            panic!("{what}: child was still running after {CHILD_DEADLINE:?} and was killed")
        }
    };

    let explanation = match code {
        verdict::OK => return,
        verdict::CLIENT_CREATE_FAILED => "the child could not create a fresh client".into(),
        verdict::COMMAND_FAILED => "a command failed in the child".into(),
        n => format!("unexpected child exit code {n}"),
    };
    panic!("{what}: {explanation}");
}

// ─── Tests ───────────────────────────────────────────────────────────────────

#[test]
#[serial]
fn child_can_use_a_fresh_client_after_fork() {
    const COMMANDS: usize = 3;

    let server = start_server();
    let addr = server.get_client_addr();
    let runtime = build_runtime();
    let mut parent = parent_with_watchdog_installed(&runtime, &addr);

    let outcome = run_in_forked_child(|| {
        leaked_runtime().block_on(async {
            let Some(mut child) = connect(&addr).await else {
                return verdict::CLIENT_CREATE_FAILED;
            };
            for _ in 0..COMMANDS {
                if !ping_succeeds(&mut child).await {
                    return verdict::COMMAND_FAILED;
                }
            }
            verdict::OK
        })
    });
    assert_child_ok(outcome, "fresh client in child");

    assert!(
        runtime.block_on(ping_succeeds(&mut parent)),
        "parent after fork: PING failed"
    );
}

#[test]
#[serial]
fn concurrent_commands_in_child_dont_crash() {
    const THREADS: usize = 4;
    const COMMANDS_PER_THREAD: usize = 3;

    let server = start_server();
    let addr = server.get_client_addr();
    let runtime = build_runtime();
    let _parent = parent_with_watchdog_installed(&runtime, &addr);

    let outcome = run_in_forked_child(|| {
        let child_runtime = leaked_runtime();
        let Some(client) = child_runtime.block_on(connect(&addr)) else {
            return verdict::CLIENT_CREATE_FAILED;
        };

        let barrier = Arc::new(Barrier::new(THREADS));
        let workers: Vec<_> = (0..THREADS)
            .map(|_| {
                let barrier = Arc::clone(&barrier);
                // Clones share the connection, as the bindings' workers do.
                let mut client = client.clone();
                std::thread::spawn(move || {
                    barrier.wait();
                    child_runtime.block_on(async {
                        for _ in 0..COMMANDS_PER_THREAD {
                            if !ping_succeeds(&mut client).await {
                                return false;
                            }
                        }
                        true
                    })
                })
            })
            .collect();

        // Collect rather than short-circuit, so no worker outlives the check.
        let results: Vec<bool> = workers
            .into_iter()
            .map(|worker| worker.join().unwrap_or(false))
            .collect();
        if results.contains(&false) {
            return verdict::COMMAND_FAILED;
        }
        verdict::OK
    });
    assert_child_ok(outcome, "concurrent commands in child");
}
