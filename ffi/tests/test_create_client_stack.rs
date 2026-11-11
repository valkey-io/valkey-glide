// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Foreign callers pick the thread that creates a client. On musl a Go goroutine runs on a
// 128 KiB pthread stack, which a TLS cluster connect outgrows. A connect that overflows aborts
// the process, so the connects run in child processes and the parent owns the cluster, which
// it stops even when a child dies.

use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::errors::RequestErrorType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_char, c_void};
use std::path::PathBuf;
use std::process::Command;
use std::sync::{Condvar, Mutex};
use std::time::Duration;

/// musl's default pthread stack size. On Alpine every thread that Go creates gets this much.
const MUSL_THREAD_STACK: usize = 128 * 1024;
/// Below what a TLS connect needs on a runtime worker, so workers only pass with an explicit size.
const SMALL_RUST_MIN_STACK: usize = 64 * 1024;
/// Set only in child processes: the `host:port` of a node of the parent's TLS cluster.
const NODE_ENV: &str = "GLIDE_STACK_TEST_TLS_NODE";

struct TlsCluster {
    folder: String,
    node: String,
}

impl TlsCluster {
    fn start() -> Self {
        let stdout = cluster_manager(&["start", "--cluster-mode", "-n", "3", "-r", "0"]);
        let value = |key: &str| {
            stdout
                .lines()
                .find_map(|line| line.trim().strip_prefix(key))
                .unwrap_or_else(|| panic!("no {key} in cluster_manager.py output:\n{stdout}"))
                .to_string()
        };
        let folder = value("CLUSTER_FOLDER=");
        let nodes = value("CLUSTER_NODES=");
        let node = nodes.split(',').next().expect("a node").to_string();
        Self { folder, node }
    }
}

impl Drop for TlsCluster {
    fn drop(&mut self) {
        cluster_manager(&["stop", "--cluster-folder", &self.folder]);
    }
}

fn utils_dir() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../utils")
}

fn cluster_manager(args: &[&str]) -> String {
    let output = Command::new("python3")
        .arg(utils_dir().join("cluster_manager.py"))
        .arg("--tls")
        .args(args)
        .output()
        .expect("failed to run cluster_manager.py");
    assert!(
        output.status.success(),
        "cluster_manager.py {args:?} failed:\n{}",
        String::from_utf8_lossy(&output.stderr)
    );
    String::from_utf8_lossy(&output.stdout).into_owned()
}

fn request(node: &str, tls_mode: TlsMode) -> Vec<u8> {
    let (host, port) = node.rsplit_once(':').expect("host:port");
    let mut request = ConnectionRequest::new();
    request.cluster_mode_enabled = true;
    request.tls_mode = tls_mode.into();
    if tls_mode == TlsMode::SecureTls {
        let ca = std::fs::read(utils_dir().join("tls_crts/ca.crt")).expect("read CA certificate");
        request.root_certs.push(ca.into());
    }
    let mut address = NodeAddress::new();
    address.host = host.into();
    address.port = port.parse().expect("port");
    request.addresses.push(address);
    request
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

fn on_musl_sized_stack(
    name: &str,
    create: impl FnOnce() -> Result<(), String> + Send + 'static,
) -> Result<(), String> {
    std::thread::Builder::new()
        .name(name.to_string())
        .stack_size(MUSL_THREAD_STACK)
        .spawn(create)
        .expect("spawn client creator thread")
        .join()
        .expect("client creator thread panicked")
}

/// Runs the child-only `test` in a fresh copy of this binary and asserts it passed.
fn run_child(test: &str, node: &str, rust_min_stack: Option<usize>) {
    let mut child = Command::new(std::env::current_exe().expect("test binary path"));
    child.args([test, "--exact"]).env(NODE_ENV, node);
    if let Some(size) = rust_min_stack {
        child.env("RUST_MIN_STACK", size.to_string());
    }
    let output = child.output().expect("run the test binary");
    let stdout = String::from_utf8_lossy(&output.stdout);
    assert!(
        output.status.success() && stdout.contains("1 passed"),
        "{test} with RUST_MIN_STACK={rust_min_stack:?}: {}\n{stdout}\n{}",
        output.status,
        String::from_utf8_lossy(&output.stderr)
    );
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
fn child_creates_tls_clients_on_a_musl_sized_thread() {
    let Ok(node) = std::env::var(NODE_ENV) else {
        return;
    };
    let verified = request(&node, TlsMode::SecureTls);
    for (name, client_type) in [("async", async_client()), ("sync", ClientType::SyncClient)] {
        let request = verified.clone();
        let result = on_musl_sized_stack(name, move || {
            create_client_then(&request, client_type, |_| Ok(()))
        });
        assert_eq!(result, Ok(()), "{name} TLS cluster client");
    }
    // Monitor clients take no root certificates, so they cannot verify the test CA.
    let insecure = request(&node, TlsMode::InsecureTls);
    let result = on_musl_sized_stack("monitor", move || create_monitor_and_close(&insecure));
    assert_eq!(result, Ok(()), "TLS monitor client");
}

#[test]
fn child_connects_monitor_and_scope_on_runtime_workers() {
    let Ok(node) = std::env::var(NODE_ENV) else {
        return;
    };
    let insecure = request(&node, TlsMode::InsecureTls);
    let result = on_musl_sized_stack("monitor", move || create_monitor_and_close(&insecure));
    assert_eq!(result, Ok(()), "TLS monitor client");
    let verified = request(&node, TlsMode::SecureTls);
    let result = on_musl_sized_stack("scope", move || {
        create_client_then(&verified, async_client(), |client| {
            acquire_and_release_scope(client, &verified)
        })
    });
    assert_eq!(result, Ok(()), "TLS scope on the pool runtime");
}
