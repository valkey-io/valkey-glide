// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Kept in its own test binary: the test lowers the process-wide `RLIMIT_NOFILE`, which
//! would make unrelated tests running in the same process fail with `EMFILE`.

#![cfg(all(feature = "socket-layer", target_os = "linux"))]

use glide_core::client::get_or_init_runtime;
use glide_core::{close_socket, get_socket_path_from_name, start_socket_listener_internal};
use std::fs::File;
use std::os::fd::AsRawFd;
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

/// Opens `/dev/null` into every unused descriptor below `highest`, so that the number of
/// descriptors that can still be opened is exactly the headroom above `highest`.
fn fill_fd_gaps(highest: i32) -> Vec<File> {
    let mut fillers = Vec::new();
    loop {
        let filler = File::open("/dev/null").unwrap();
        if filler.as_raw_fd() > highest {
            return fillers;
        }
        fillers.push(filler);
    }
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

    let original_limit = nofile_limit();
    let highest = highest_open_fd();
    let _fillers = fill_fd_gaps(highest);
    // Room for the listener socket, but not for the four descriptors of a worker runtime.
    set_nofile_soft_limit(highest as libc::rlim_t + 1 + 3);
    let result = start_listener(&socket_path);
    set_nofile_soft_limit(original_limit.rlim_cur);

    let error = result.expect_err("The listener should report the pool failure");
    assert!(
        error.contains("Failed to create the socket listener worker pool"),
        "{error}"
    );
    // The tokio-util panic prints the Debug of `io::Error`, whose `kind` field is
    // locale- and format-independent, unlike the strerror text.
    assert!(error.contains("TooManyOpenFiles"), "{error}");
    assert!(
        !Path::new(&socket_path).exists(),
        "The socket file should be removed after the failure"
    );

    // The failed attempt must not be recorded as initialized: a retry on the same path
    // starts a listener that actually accepts connections.
    let path = start_listener(&socket_path).expect("The retry should succeed");
    UnixStream::connect(&path).expect("The retried listener should accept connections");
    close_socket(&socket_path);
}
