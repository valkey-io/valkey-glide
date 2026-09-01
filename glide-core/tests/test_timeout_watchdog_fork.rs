// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Fork-safety tests for the process-global [`TimeoutWatchdog`].
//!
//! `fork()` copies only the calling thread, so a child inherits the watchdog
//! pointer but not its thread. Registering on it then unparks a primitive owned
//! by the parent, which on macOS makes libdispatch trap (`SIGTRAP`) and kills the
//! child below the language VM, where no Ruby/Python exception can catch it.
//!
//! These assert only what a caller can observe — the child keeps running,
//! commands succeed, the parent is unaffected. Two properties are asserted
//! directly by unit tests in `glide-core/src/timeout_watchdog.rs` instead, because
//! there they are visible without a server in the way:
//! `forked_child_gets_a_working_watchdog` (a child's deadline fires) and
//! `concurrent_global_calls_converge_on_one_instance` (one instance is elected).
//!
//! Separate from `test_timeout_watchdog.rs` because these are the only watchdog
//! tests needing a real server, and forking is only safe from an idle process.

#![cfg(unix)]

mod constants;
mod utilities;

use crate::utilities::{
    RedisServer, ServerType, TestConfiguration, create_connection_request,
    wait_for_server_to_become_ready,
};
use glide_core::client::Client;
use redis::{ConnectionAddr, Value};
use serial_test::serial;
use std::sync::{Arc, Barrier};
use std::time::{Duration, Instant};

const REQUEST_TIMEOUT: Duration = Duration::from_millis(250);

/// How long a child may run before it is presumed hung and killed, so that a
/// child which never terminates fails the test instead of stalling the job.
const CHILD_DEADLINE: Duration = Duration::from_secs(30);

/// Children report through their exit code, because printing from a forked child
/// can deadlock on an inherited stdio lock. Kept away from 0 so a genuine
/// `abort`/`panic` exit is never mistaken for a verdict.
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

/// `None` rather than a panic: this also runs in forked children, where
/// unwinding would drag the test harness into a process that has lost every
/// thread but one.
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

/// A child cannot reuse the parent's runtime — its worker threads did not
/// survive the fork. Leaked because dropping a runtime waits on its threads and
/// the child is about to `_exit`.
fn leaked_runtime() -> &'static tokio::runtime::Runtime {
    Box::leak(Box::new(build_runtime()))
}

/// Connect a parent client and issue the one command that installs the
/// process-global watchdog the child inherits, then let the parent go idle so the
/// child cannot inherit an allocator or logger lock held by a dead thread.
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
    std::thread::sleep(Duration::from_millis(100));
    parent
}

// ─── Fork helpers ────────────────────────────────────────────────────────────

/// How a forked child terminated. Death by signal is the regression these tests
/// exist to catch, so it is a variant rather than a funny exit code.
#[derive(Debug)]
enum ChildOutcome {
    Exited(i32),
    Signaled(i32),
    /// Still running at [`CHILD_DEADLINE`], and killed.
    TimedOut,
}

/// The child leaves via `_exit`, skipping destructors, atexit handlers and
/// harness teardown that assume the parent's address space.
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

/// Polls with `WNOHANG` rather than blocking, so a child that deadlocks post-fork
/// fails the test instead of stalling the job on an unbounded `waitpid`.
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
            "{what}: child was killed by signal {signal}{} instead of exiting — the \
             process-global TimeoutWatchdog was inherited across fork()",
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
