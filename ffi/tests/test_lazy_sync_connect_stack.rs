// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// A lazy client connects inside its first command. For a sync client that command must still
// connect on a runtime worker, not on the caller's thread, whose stack can be 128 KiB on musl.

mod common;

use common::{NODE_ENV, TlsCluster, on_musl_sized_stack, request, run_child};
use glide_core::connection_request::TlsMode;
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_void};
use std::sync::Mutex;

const FIRST_COMMAND_THREAD: &str = "first-command";

/// Names of the threads the connect called the address resolver on.
static RESOLVER_THREADS: Mutex<Vec<String>> = Mutex::new(Vec::new());

unsafe extern "C-unwind" fn record_resolver_thread(
    _: usize,
    _: *const u8,
    _: usize,
    _: u16,
    _: *mut u8,
    _: usize,
    _: *mut usize,
) -> u16 {
    let name = std::thread::current()
        .name()
        .unwrap_or_default()
        .to_string();
    RESOLVER_THREADS.lock().unwrap().push(name);
    0 // keep the address as given
}

struct Client(*const c_void);
unsafe impl Send for Client {}

fn ping(client: &Client) -> Result<(), String> {
    unsafe {
        let result = command(
            client.0,
            0,
            RequestType::Ping,
            0,
            std::ptr::null(),
            std::ptr::null(),
            std::ptr::null(),
            0,
            0,
        );
        assert!(!result.is_null());
        let error = (*result).command_error;
        let outcome = if error.is_null() {
            Ok(())
        } else {
            Err(CStr::from_ptr((*error).command_error_message)
                .to_string_lossy()
                .into_owned())
        };
        free_command_result(result);
        outcome
    }
}

#[test]
fn lazy_sync_client_connects_on_a_runtime_worker() {
    let cluster = TlsCluster::start();
    run_child(
        "child_sends_the_first_command_from_a_musl_sized_thread",
        &cluster.node,
        None,
    );
}

#[test]
#[ignore = "child process of lazy_sync_client_connects_on_a_runtime_worker"]
fn child_sends_the_first_command_from_a_musl_sized_thread() {
    let node = std::env::var(NODE_ENV)
        .unwrap_or_else(|_| panic!("run by its parent test, which sets {NODE_ENV}"));
    let mut request = request(&node, TlsMode::SecureTls);
    request.lazy_connect = true;
    let request = request
        .write_to_bytes()
        .expect("serialize connection request");
    let client = unsafe {
        let response = create_client(
            request.as_ptr(),
            request.len(),
            &ClientType::SyncClient,
            None,
            Some(record_resolver_thread),
            0,
        );
        assert!(
            (*response).connection_error_message.is_null(),
            "create_client failed"
        );
        let client = Client((*response).conn_ptr);
        free_connection_response(response as *mut ConnectionResponse);
        client
    };
    assert!(
        RESOLVER_THREADS.lock().unwrap().is_empty(),
        "a lazy client connected at creation"
    );

    let (client, result) = on_musl_sized_stack(FIRST_COMMAND_THREAD, move || {
        let result = ping(&client);
        (client, result)
    });
    unsafe { close_client(client.0) };
    assert_eq!(result, Ok(()), "first PING on a lazy TLS cluster client");

    let threads = RESOLVER_THREADS.lock().unwrap();
    assert!(!threads.is_empty(), "the first command did not connect");
    assert!(
        threads.iter().all(|name| name != FIRST_COMMAND_THREAD),
        "the connect ran on the caller's thread: {threads:?}"
    );
}
