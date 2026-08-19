// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Ephemeral standalone server harness.

use glide::{GlideClient, GlideClientConfiguration, ProtocolVersion};
use std::net::{TcpListener, TcpStream};
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

/// Locate a usable `valkey-server`/`redis-server` binary. Set the
/// `VALKEY_SERVER_PATH` environment variable to point at a specific binary;
/// otherwise the first `valkey-server`/`redis-server` found on `PATH` is used.
/// Returns `None` (so tests SKIP) when no binary is available.
pub fn server_binary() -> Option<String> {
    if let Ok(p) = std::env::var("VALKEY_SERVER_PATH")
        && std::path::Path::new(&p).exists()
    {
        return Some(p);
    }
    for name in ["valkey-server", "redis-server"] {
        if let Ok(output) = Command::new("which").arg(name).output()
            && output.status.success()
        {
            let p = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !p.is_empty() {
                return Some(p);
            }
        }
    }
    None
}

/// Grab a currently-free TCP port on loopback.
pub fn free_port() -> u16 {
    let listener = TcpListener::bind("127.0.0.1:0").expect("bind ephemeral port");
    listener.local_addr().unwrap().port()
}

/// Block until `port` accepts a TCP connection, or `deadline` elapses.
pub(crate) fn wait_for_port(port: u16, timeout: Duration) -> bool {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
        if TcpStream::connect(("127.0.0.1", port)).is_ok() {
            return true;
        }
        std::thread::sleep(Duration::from_millis(30));
    }
    false
}

/// A running standalone server; killed on drop.
pub struct TestServer {
    child: Child,
    pub port: u16,
}

impl TestServer {
    /// Start a fresh standalone server, or `None` when no binary is available.
    pub fn start() -> Option<TestServer> {
        Self::start_with_args(&[])
    }

    /// Start a standalone server with extra CLI arguments (e.g. `--requirepass`).
    pub fn start_with_args(extra: &[&str]) -> Option<TestServer> {
        let bin = server_binary()?;
        let port = free_port();
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
        let child = Command::new(&bin)
            .args(&args)
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn()
            .ok()?;
        let mut server = TestServer { child, port };
        if wait_for_port(port, Duration::from_secs(10)) {
            std::thread::sleep(Duration::from_millis(100));
            Some(server)
        } else {
            let _ = server.child.kill();
            None
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
    ) -> glide::Result<GlideClient> {
        GlideClient::connect(config).await
    }
}

impl Drop for TestServer {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
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
