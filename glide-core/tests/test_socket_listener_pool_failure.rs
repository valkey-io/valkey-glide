// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Kept in its own test binary: the test lowers the process-wide `RLIMIT_NOFILE`, which
//! would make unrelated tests running in the same process fail with `EMFILE`.

#![cfg(all(feature = "socket-layer", target_os = "linux"))]

use glide_core::client::get_or_init_runtime;
use glide_core::{close_socket, get_socket_path_from_name, start_socket_listener_internal};
use std::os::unix::net::UnixStream;
use std::path::Path;
use std::sync::mpsc;

fn nofile_limit() -> libc::rlimit {
    let mut limit = libc::rlimit {
        rlim_cur: 0,
        rlim_max: 0,
    };
    assert_eq!(
        unsafe { libc::getrlimit(libc::RLIMIT_NOFILE, &mut limit) },
        0
    );
    limit
}

fn set_nofile_soft_limit(rlim_cur: libc::rlim_t) {
    let limit = libc::rlimit {
        rlim_cur,
        rlim_max: nofile_limit().rlim_max,
    };
    assert_eq!(unsafe { libc::setrlimit(libc::RLIMIT_NOFILE, &limit) }, 0);
}

fn highest_open_fd() -> i32 {
    std::fs::read_dir("/proc/self/fd")
        .unwrap()
        .filter_map(|entry| entry.ok()?.file_name().to_str()?.parse::<i32>().ok())
        .max()
        .unwrap()
}

fn start_listener(socket_path: &str) -> Result<String, String> {
    let (tx, rx) = mpsc::channel();
    start_socket_listener_internal(
        move |result| tx.send(result).unwrap(),
        Some(socket_path.to_string()),
    );
    rx.recv().unwrap()
}

#[test]
fn pool_creation_failure_is_reported_through_the_init_callback() {
    let socket_path = get_socket_path_from_name(format!(
        "{}_test_socket_listener_pool_failure",
        std::process::id()
    ));
    close_socket(&socket_path);
    get_or_init_runtime().expect("Failed to create the shared runtime");

    // Wiring check: the configured size must actually reach `LocalPoolHandle::new`. The
    // pool is built synchronously on the caller's thread, so the worker threads exist by
    // the time `start_listener` returns. Single-test binary, so setting the variable
    // cannot race another test. `close_socket` only unlinks the file — the sized
    // listener stays alive (and `sized_path` stays in `INITIALIZED_SOCKETS`) until the
    // process exits, which a single-test binary can afford.
    unsafe { std::env::set_var("GLIDE_SOCKET_LISTENER_WORKERS", "3") };
    let sized_path = format!("{socket_path}_sized");
    close_socket(&sized_path);
    let threads = || std::fs::read_dir("/proc/self/task").unwrap().count();
    let before = threads();
    start_listener(&sized_path).expect("The sized listener should start");
    assert_eq!(threads() - before, 3, "3 configured workers should be spawned");
    close_socket(&sized_path);
    unsafe { std::env::remove_var("GLIDE_SOCKET_LISTENER_WORKERS") };

    let original_limit = nofile_limit();
    let highest = highest_open_fd();
    // Zero openable descriptors (RLIMIT_NOFILE is one greater than the maximum fd
    // number, so rlim_cur = highest allows no new opens). The pool is built before the
    // listener socket is bound, so its first worker fails on its first descriptor and
    // the creation panics — independent of the pool size and of how many descriptors a
    // worker runtime costs, so a tokio upgrade cannot silently change which pool sizes
    // pass, and a single-CPU host (pool of 1) behaves the same as a many-core one.
    set_nofile_soft_limit(highest as libc::rlim_t);
    let result = start_listener(&socket_path);
    set_nofile_soft_limit(original_limit.rlim_cur);

    let error = result.expect_err("The listener should report the pool failure");
    assert!(
        error.contains("Failed to create the socket listener worker pool"),
        "{error}"
    );
    // The tokio-util panic prints the Debug of `io::Error`. The numeric `code` field
    // does not depend on the toolchain's `ErrorKind` mapping or on the strerror text.
    assert!(
        error.contains(&format!("code: {}", libc::EMFILE)),
        "{error}"
    );
    // The pool failure happens before `bind` runs, so the socket file was never
    // created. The assertion stays as a guard: if a future change binds before the
    // pool build, a failure would leave the file unless the failure path cleans up.
    assert!(
        !Path::new(&socket_path).exists(),
        "The socket file should be removed after the failure"
    );

    // The failed attempt must not be recorded as initialized: with the limit restored,
    // the retry verifies cleanup hygiene (socket file removed, path not poisoned in
    // `INITIALIZED_SOCKETS`), not leak-freedom under the constrained limit.
    let path = start_listener(&socket_path).expect("The retry should succeed");
    UnixStream::connect(&path).expect("The retried listener should accept connections");
    close_socket(&socket_path);
}
