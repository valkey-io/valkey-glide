// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Shared by the stack tests. A connect that overflows aborts the process, so those tests run
// their connects in child processes and the parent owns the cluster, which it stops even when
// a child dies.

use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use std::path::PathBuf;
use std::process::Command;

/// musl's default pthread stack size. On Alpine every thread that Go creates gets this much.
pub const MUSL_THREAD_STACK: usize = 128 * 1024;
/// Set only in child processes: the `host:port` of a node of the parent's TLS cluster.
pub const NODE_ENV: &str = "GLIDE_STACK_TEST_TLS_NODE";

pub struct TlsCluster {
    folder: String,
    pub node: String,
}

impl TlsCluster {
    pub fn start() -> Self {
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

pub fn request(node: &str, tls_mode: TlsMode) -> ConnectionRequest {
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
}

pub fn on_musl_sized_stack<T: Send + 'static>(
    name: &str,
    run: impl FnOnce() -> T + Send + 'static,
) -> T {
    std::thread::Builder::new()
        .name(name.to_string())
        .stack_size(MUSL_THREAD_STACK)
        .spawn(run)
        .expect("spawn a musl-sized thread")
        .join()
        .expect("the musl-sized thread panicked")
}

/// Runs the child-only `test` in a fresh copy of this binary and asserts it passed.
pub fn run_child(test: &str, node: &str, rust_min_stack: Option<usize>) {
    let mut child = Command::new(std::env::current_exe().expect("test binary path"));
    child
        .args([test, "--exact", "--ignored"])
        .env(NODE_ENV, node);
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
