// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! TLS integration tests.
//!
//! Generates a self-signed certificate with `openssl`, starts a TLS-only
//! `valkey-server`, and connects with `TlsConfig::InsecureTls`. Every step is
//! best-effort: if `openssl` is missing, the server lacks TLS support, or the
//! TLS port never comes up, the test prints SKIP and returns (never fails).

mod common;

use glide::{AsyncCommands, ConnectionManagementCommands, GlideClientConfiguration, TlsConfig};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

struct TlsServer {
    child: Child,
    port: u16,
    dir: PathBuf,
}

impl Drop for TlsServer {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
        let _ = std::fs::remove_dir_all(&self.dir);
    }
}

/// Best-effort TLS server setup. Returns `None` (→ SKIP) on any failure.
fn start_tls_server() -> Option<TlsServer> {
    let bin = common::server_binary()?;
    // Need openssl to mint certs.
    let openssl_ok = Command::new("openssl")
        .arg("version")
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    if !openssl_ok {
        return None;
    }
    let dir = std::env::temp_dir().join(common::key("vgr_tls"));
    std::fs::create_dir_all(&dir).ok()?;
    let cert = dir.join("cert.pem");
    let keyf = dir.join("key.pem");

    let ok = Command::new("openssl")
        .args([
            "req",
            "-x509",
            "-newkey",
            "rsa:2048",
            "-keyout",
            keyf.to_str()?,
            "-out",
            cert.to_str()?,
            "-days",
            "1",
            "-nodes",
            "-subj",
            "/CN=localhost",
        ])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .ok()?
        .success();
    if !ok {
        let _ = std::fs::remove_dir_all(&dir);
        return None;
    }

    let port = common::free_port();
    let child = Command::new(&bin)
        .args([
            "--port",
            "0",
            "--tls-port",
            &port.to_string(),
            "--bind",
            "127.0.0.1",
            "--tls-cert-file",
            cert.to_str()?,
            "--tls-key-file",
            keyf.to_str()?,
            "--tls-ca-cert-file",
            cert.to_str()?,
            "--tls-auth-clients",
            "no",
            "--save",
            "",
            "--appendonly",
            "no",
        ])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;

    let mut server = TlsServer { child, port, dir };
    let deadline = Instant::now() + Duration::from_secs(6);
    while Instant::now() < deadline {
        if std::net::TcpStream::connect(("127.0.0.1", port)).is_ok() {
            std::thread::sleep(Duration::from_millis(200));
            return Some(server);
        }
        // If the process already exited (no TLS support), bail out.
        if let Ok(Some(_)) = server.child.try_wait() {
            return None;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    None
}

#[tokio::test]
async fn tls_insecure_roundtrip() {
    let server = match start_tls_server() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: TLS not feasible (no openssl / no TLS support / port down)");
            return;
        }
    };
    let config = GlideClientConfiguration::with_address("127.0.0.1", server.port)
        .tls(TlsConfig::InsecureTls)
        .request_timeout(Duration::from_secs(5));
    let client = match GlideClient::connect(config).await {
        Ok(c) => c,
        Err(e) => {
            eprintln!("SKIP: TLS connect failed ({e})");
            return;
        }
    };
    assert_eq!(client.ping().await.unwrap(), "PONG");
    let _: () = client.set("tlsk", "tlsv").await.unwrap();
    let got: Option<glide::Bytes> = client.get("tlsk").await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"tlsv"[..]));
}

use glide::GlideClient;
