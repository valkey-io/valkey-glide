// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Foreign callers pick the thread that creates a client. On musl a Go goroutine runs on a
// 128 KiB pthread stack, which a TLS cluster connect outgrows.

mod common;

use common::{MUSL_THREAD_STACK, NODE_ENV, TlsCluster, on_musl_sized_stack, request, run_child};
use glide_core::connection_request::TlsMode;
use glide_core::errors::RequestErrorType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_char, c_void};
use std::sync::{Condvar, Mutex};
use std::time::Duration;

/// Below what a TLS connect needs on a runtime worker, so workers only pass with an explicit size.
const SMALL_RUST_MIN_STACK: usize = 64 * 1024;

fn request_bytes(node: &str, tls_mode: TlsMode) -> Vec<u8> {
    request(node, tls_mode)
        .write_to_bytes()
        .expect("serialize connection request")
}

unsafe extern "C-unwind" fn ignore_success(_: usize, _: *const CommandResponse) {}
unsafe extern "C-unwind" fn ignore_failure(_: usize, _: *const c_char, _: RequestErrorType) {}
#[allow(clippy::too_many_arguments)]
unsafe extern "C-unwind" fn ignore_monitor_line(
    _: usize,
    _: f64,
    _: i64,
    _: *const u8,
    _: i64,
    _: *const u8,
    _: i64,
    _: *const u8,
    _: i64,
) {
}

fn async_client() -> ClientType {
    ClientType::AsyncClient {
        success_callback: ignore_success,
        failure_callback: ignore_failure,
        allow_stack_response: false,
    }
}

/// Passes the client in `response` to `use_client`, which must close it, then frees `response`.
unsafe fn consume(
    response: *const ConnectionResponse,
    use_client: impl FnOnce(*const c_void) -> Result<(), String>,
) -> Result<(), String> {
    let (conn_ptr, error) = unsafe { ((*response).conn_ptr, (*response).connection_error_message) };
    let result = if error.is_null() {
        assert!(!conn_ptr.is_null());
        use_client(conn_ptr)
    } else {
        Err(unsafe { CStr::from_ptr(error) }
            .to_string_lossy()
            .into_owned())
    };
    unsafe { free_connection_response(response as *mut ConnectionResponse) };
    result
}

fn create_client_then(
    request: &[u8],
    client_type: ClientType,
    use_client: impl FnOnce(*const c_void) -> Result<(), String>,
) -> Result<(), String> {
    unsafe {
        let response = create_client(request.as_ptr(), request.len(), &client_type, None, None, 0);
        consume(response, |client| {
            let result = use_client(client);
            close_client(client);
            result
        })
    }
}

fn create_monitor_and_close(request: &[u8]) -> Result<(), String> {
    unsafe {
        let response = create_monitor_client(request.as_ptr(), request.len(), ignore_monitor_line);
        consume(response, |client| {
            close_monitor_client(client);
            Ok(())
        })
    }
}

static SCOPE_OUTCOME: Mutex<Option<Result<u64, String>>> = Mutex::new(None);
static SCOPE_REPORTED: Condvar = Condvar::new();

fn report_scope(outcome: Result<u64, String>) {
    *SCOPE_OUTCOME.lock().unwrap() = Some(outcome);
    SCOPE_REPORTED.notify_all();
}

unsafe extern "C-unwind" fn scope_acquired(_: usize, response: *const CommandResponse) {
    let scope_id = unsafe { (*response).int_value } as u64;
    unsafe { free_command_response(response as *mut CommandResponse) };
    report_scope(Ok(scope_id));
}

unsafe extern "C-unwind" fn scope_failed(_: usize, message: *const c_char, _: RequestErrorType) {
    report_scope(Err(unsafe { CStr::from_ptr(message) }
        .to_string_lossy()
        .into_owned()));
}

/// Acquires a scope the way Go's IsolatedScope does, so its connection opens on a pool worker.
fn acquire_and_release_scope(client: *const c_void, request: &[u8]) -> Result<(), String> {
    let client_id = client as u64;
    let queued = unsafe {
        glide_scope_acquire_async(
            client_id,
            request.as_ptr(),
            request.len(),
            false,
            0,
            10_000,
            0,
            scope_acquired,
            scope_failed,
        )
    };
    if queued != 0 {
        return Err(format!("glide_scope_acquire_async returned {queued}"));
    }
    let (mut outcome, _) = SCOPE_REPORTED
        .wait_timeout_while(
            SCOPE_OUTCOME.lock().unwrap(),
            Duration::from_secs(30),
            |outcome| outcome.is_none(),
        )
        .unwrap();
    let scope_id = outcome
        .take()
        .ok_or("the scope acquire reported no outcome")??;
    match glide_scope_release(scope_id, client_id) {
        0 => Ok(()),
        code => Err(format!("glide_scope_release returned {code}")),
    }
}

// RUST_MIN_STACK sets the default stack of every thread the process spawns, tokio's included.
#[test]
fn tls_connects_do_not_depend_on_the_callers_or_the_default_thread_stack() {
    let cluster = TlsCluster::start();
    let clients = "child_creates_tls_clients_on_a_musl_sized_thread";
    run_child(clients, &cluster.node, None);
    run_child(clients, &cluster.node, Some(MUSL_THREAD_STACK));
    run_child(
        "child_connects_monitor_and_scope_on_runtime_workers",
        &cluster.node,
        Some(SMALL_RUST_MIN_STACK),
    );
}

#[test]
#[ignore = "child process of tls_connects_do_not_depend_on_the_callers_or_the_default_thread_stack"]
fn child_creates_tls_clients_on_a_musl_sized_thread() {
    let node = std::env::var(NODE_ENV)
        .unwrap_or_else(|_| panic!("run by its parent test, which sets {NODE_ENV}"));
    let verified = request_bytes(&node, TlsMode::SecureTls);
    for (name, client_type) in [("async", async_client()), ("sync", ClientType::SyncClient)] {
        let request = verified.clone();
        let result = on_musl_sized_stack(name, move || {
            create_client_then(&request, client_type, |_| Ok(()))
        });
        assert_eq!(result, Ok(()), "{name} TLS cluster client");
    }
    // Monitor clients take no root certificates, so they cannot verify the test CA.
    let insecure = request_bytes(&node, TlsMode::InsecureTls);
    let result = on_musl_sized_stack("monitor", move || create_monitor_and_close(&insecure));
    assert_eq!(result, Ok(()), "TLS monitor client");
}

#[test]
#[ignore = "child process of tls_connects_do_not_depend_on_the_callers_or_the_default_thread_stack"]
fn child_connects_monitor_and_scope_on_runtime_workers() {
    let node = std::env::var(NODE_ENV)
        .unwrap_or_else(|_| panic!("run by its parent test, which sets {NODE_ENV}"));
    let insecure = request_bytes(&node, TlsMode::InsecureTls);
    let result = on_musl_sized_stack("monitor", move || create_monitor_and_close(&insecure));
    assert_eq!(result, Ok(()), "TLS monitor client");
    let verified = request_bytes(&node, TlsMode::SecureTls);
    let result = on_musl_sized_stack("scope", move || {
        create_client_then(&verified, async_client(), |client| {
            acquire_and_release_scope(client, &verified)
        })
    });
    assert_eq!(result, Ok(()), "TLS scope on the pool runtime");
}
