// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Ephemeral standalone server harness.

use glide::{GlideClient, GlideClientConfiguration, ProtocolVersion};
use std::fs::File;
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

// Maximum start attempts and timeout.
// Matches `utils/cluster_manager.py`.
const START_MAX_ATTEMPTS: u32 = 5;
const START_TIMEOUT: Duration = Duration::from_secs(10);

// Constants for parsing server logs.
const LOG_READY: &str = "Ready to accept connections";
const LOG_PORT_IN_USE: &str = "Address already in use";

/// Locate a usable `valkey-server`/`redis-server` binary. Set the
/// `VALKEY_SERVER_PATH` environment variable to point at a specific binary;
/// otherwise the first `valkey-server`/`redis-server` found on `PATH` is used.
///
/// Panics when no binary is available.
fn server_binary() -> String {
    // [1] From environment variable:
    if let Ok(p) = std::env::var("VALKEY_SERVER_PATH") {
        assert!(
            Path::new(&p).exists(),
            "VALKEY_SERVER_PATH={p} does not exist"
        );
        return p;
    }

    // [2] From binary:
    for name in ["valkey-server", "redis-server"] {
        if let Ok(output) = Command::new("which").arg(name).output()
            && output.status.success()
        {
            let p = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !p.is_empty() {
                return p;
            }
        }
    }

    panic!("no valkey-server or redis-server binary found. Install one or set VALKEY_SERVER_PATH");
}

/// Grab a currently-free TCP port on loopback.
fn free_port() -> u16 {
    let listener = TcpListener::bind("127.0.0.1:0").expect("bind ephemeral port");
    listener.local_addr().unwrap().port()
}

/// A running standalone server; killed on drop.
pub struct TestServer {
    child: Child,
    pub port: u16,
    log_path: PathBuf,
}

impl TestServer {
    /// Start a standalone server.
    /// Panics if it cannot be started.
    pub fn start() -> TestServer {
        Self::start_with_args(&[])
    }

    /// Start a standalone server with extra CLI arguments (e.g. `--requirepass`).
    /// Panics if it cannot be started.
    pub fn start_with_args(extra: &[&str]) -> TestServer {
        let bin = server_binary();
        for _ in 0..START_MAX_ATTEMPTS {
            if let Some(server) = Self::try_start_on(&bin, free_port(), extra) {
                return server;
            }
        }
        panic!("{bin} could not bind a free port after {START_MAX_ATTEMPTS} attempts");
    }

    /// Attempts to start a server on the given port.
    /// Returns `None` if the port is already in use.
    /// Panics on any other failure.
    fn try_start_on(bin: &str, port: u16, extra: &[&str]) -> Option<TestServer> {
        // [1] Start logging.
        let log_path = log_path(port);
        let log = File::create(&log_path)
            .unwrap_or_else(|e| panic!("could not create {}: {e}", log_path.display()));
        let log_err = log
            .try_clone()
            .unwrap_or_else(|e| panic!("could not clone log handle: {e}"));

        // [2] Build arguments.
        let mut args: Vec<String> = vec![
            "--port".into(),
            port.to_string(),
            "--bind".into(),
            "127.0.0.1".into(),
            "--save".into(),
            "".into(),
            "--appendonly".into(),
            "no".into(),
            "--daemonize".into(),
            "no".into(),
        ];
        args.extend(extra.iter().map(|s| s.to_string()));

        // [3] Spawn the server.
        let child = match Command::new(bin)
            .args(&args)
            .stdout(Stdio::from(log))
            .stderr(Stdio::from(log_err))
            .spawn()
        {
            Ok(child) => child,
            Err(e) => {
                let _ = std::fs::remove_file(&log_path);
                panic!("could not spawn {bin}: {e}");
            }
        };

        // On every failure path below, dropping `server`
        // kills the child and removes the log.
        let mut server = TestServer {
            child,
            port,
            log_path,
        };

        // [4] Wait for the server to become ready.
        let deadline = Instant::now() + START_TIMEOUT;
        loop {
            let log = std::fs::read_to_string(&server.log_path).unwrap_or_default();
            if log.contains(LOG_READY) {
                return Some(server);
            }
            if let Ok(Some(status)) = server.child.try_wait() {
                if log.contains(LOG_PORT_IN_USE) {
                    return None;
                }
                panic!("{bin} exited with {status} before becoming ready on port {port}:\n{log}");
            }
            if Instant::now() >= deadline {
                panic!("{bin} not ready on port {port} after {START_TIMEOUT:?}:\n{log}");
            }
            std::thread::sleep(Duration::from_millis(30));
        }
    }

    /// Connect an async client with the default protocol (RESP3).
    pub async fn client(&self) -> GlideClient {
        self.client_with_protocol(ProtocolVersion::RESP3).await
    }

    /// Connect an async client using the given RESP protocol version.
    pub async fn client_with_protocol(&self, protocol: ProtocolVersion) -> GlideClient {
        // Generous connect/request timeouts: under heavy load (e.g. the coverage
        // job runs the whole suite under llvm-cov instrumentation, with many
        // ephemeral servers spawned in parallel), a freshly-started server can be
        // slow to accept — the default connect timeout can then race and fail.
        let config = GlideClientConfiguration::with_address("127.0.0.1", self.port)
            .protocol(protocol)
            .connection_timeout(Duration::from_secs(10))
            .request_timeout(Duration::from_secs(10));
        connect_standalone_with_retry(config).await
    }

    /// Try to connect a client with the given configuration (for auth tests).
    pub async fn try_connect(
        &self,
        config: GlideClientConfiguration,
    ) -> glide::ValkeyResult<GlideClient> {
        GlideClient::connect(config).await
    }
}

impl Drop for TestServer {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
        let _ = std::fs::remove_file(&self.log_path);
    }
}

/// A unique per-server log path in the system temp directory.
fn log_path(port: u16) -> PathBuf {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let n = COUNTER.fetch_add(1, Ordering::Relaxed);
    let pid = std::process::id();
    std::env::temp_dir().join(format!("glide-rust-test-{pid}-{n}-{port}.log"))
}

/// Connect a standalone client, retrying on transient connect failures. Under
/// heavy load (e.g. many `llvm-cov`-instrumented ephemeral servers spawned in
/// parallel), a freshly-started server can be briefly slow to accept or finish
/// the handshake; a single attempt can lose that race even with a generous
/// connection timeout. Mirrors glide-core's test connect-retry patch.
async fn connect_standalone_with_retry(config: GlideClientConfiguration) -> GlideClient {
    let mut last_err = None;
    for attempt in 0..10u32 {
        match GlideClient::connect(config.clone()).await {
            Ok(c) => return c,
            Err(e) => {
                last_err = Some(e);
                tokio::time::sleep(Duration::from_millis(100 * (attempt + 1) as u64)).await;
            }
        }
    }
    panic!("connect to test server failed after retries: {last_err:?}");
}
