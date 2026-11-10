// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Foreign callers pick the thread that creates a client. On musl a Go goroutine runs on a
// 128 KiB pthread stack, which a TLS cluster connect outgrows. If the connect runs on the
// caller's thread, this test overflows the stack and aborts the whole test binary, so it
// lives in its own file.

use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::errors::RequestErrorType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{CStr, c_char, c_void};
use std::path::PathBuf;
use std::process::Command;

/// musl's default pthread stack size. On Alpine every thread that Go creates gets this much.
const MUSL_THREAD_STACK: usize = 128 * 1024;

struct TlsCluster {
    folder: String,
    nodes: Vec<(String, u16)>,
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
        let nodes = value("CLUSTER_NODES=")
            .split(',')
            .map(|node| {
                let (host, port) = node.rsplit_once(':').expect("host:port");
                (host.to_string(), port.parse().expect("port"))
            })
            .collect();
        Self { folder, nodes }
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

fn request(cluster: &TlsCluster, tls_mode: TlsMode) -> Vec<u8> {
    let (host, port) = &cluster.nodes[0];
    let mut request = ConnectionRequest::new();
    request.cluster_mode_enabled = true;
    request.tls_mode = tls_mode.into();
    if tls_mode == TlsMode::SecureTls {
        let ca = std::fs::read(utils_dir().join("tls_crts/ca.crt")).expect("read CA certificate");
        request.root_certs.push(ca.into());
    }
    let mut address = NodeAddress::new();
    address.host = host.as_str().into();
    address.port = u32::from(*port);
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

/// Frees `response`, closing the client it holds with `close`, and returns its error.
unsafe fn consume(
    response: *const ConnectionResponse,
    close: impl FnOnce(*const c_void),
) -> Result<(), String> {
    let (conn_ptr, error) = unsafe { ((*response).conn_ptr, (*response).connection_error_message) };
    let result = if error.is_null() {
        assert!(!conn_ptr.is_null());
        close(conn_ptr);
        Ok(())
    } else {
        Err(unsafe { CStr::from_ptr(error) }
            .to_string_lossy()
            .into_owned())
    };
    unsafe { free_connection_response(response as *mut ConnectionResponse) };
    result
}

fn create_client_and_close(request: &[u8], client_type: ClientType) -> Result<(), String> {
    unsafe {
        let response = create_client(request.as_ptr(), request.len(), &client_type, None, None, 0);
        consume(response, |client| close_client(client))
    }
}

fn create_monitor_and_close(request: &[u8]) -> Result<(), String> {
    unsafe {
        let response = create_monitor_client(request.as_ptr(), request.len(), ignore_monitor_line);
        consume(response, |client| close_monitor_client(client))
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

#[test]
fn tls_clients_are_created_from_a_musl_sized_thread_stack() {
    let cluster = TlsCluster::start();
    let verified = request(&cluster, TlsMode::SecureTls);
    let async_client = ClientType::AsyncClient {
        success_callback: ignore_success,
        failure_callback: ignore_failure,
        allow_stack_response: false,
    };
    for (name, client_type) in [("async", async_client), ("sync", ClientType::SyncClient)] {
        let request = verified.clone();
        let result =
            on_musl_sized_stack(name, move || create_client_and_close(&request, client_type));
        assert_eq!(result, Ok(()), "{name} TLS cluster client");
    }
    // Monitor clients take no root certificates, so they cannot verify the test CA.
    let insecure = request(&cluster, TlsMode::InsecureTls);
    let result = on_musl_sized_stack("monitor", move || create_monitor_and_close(&insecure));
    assert_eq!(result, Ok(()), "TLS monitor client");
}
