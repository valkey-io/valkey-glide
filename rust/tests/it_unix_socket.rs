// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Live tests for connecting over a Unix domain socket. The server listens only on the
//! socket (`--port 0`), so a connected client must be using it.
#![cfg(unix)]

mod common;

use common::TestServer;
use glide::{
    AsyncCommands, GlideClient, GlideClientConfiguration, GlideClusterClient,
    GlideClusterClientConfiguration, GlideError, NodeAddress, TlsConfig, cmd,
};
use std::os::unix::fs::DirBuilderExt;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Duration;

/// A server listening on a socket in a private directory, which is removed on drop since the
/// server is killed without a chance to clean up.
struct UnixSocketServer {
    _server: TestServer,
    dir: String,
    path: String,
}

impl Drop for UnixSocketServer {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.dir);
    }
}

fn unix_socket_server() -> UnixSocketServer {
    static NEXT: AtomicUsize = AtomicUsize::new(0);
    // Unix socket paths are limited to ~104-108 bytes, so avoid long TMPDIRs.
    let dir = format!(
        "/tmp/glide-rs-uds-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    );
    // Fails if the directory already exists, so no other user controls it.
    std::fs::DirBuilder::new()
        .mode(0o700)
        .create(&dir)
        .unwrap_or_else(|e| panic!("could not create {dir}: {e}"));
    let path = format!("{dir}/valkey.sock");
    UnixSocketServer {
        _server: TestServer::start_with_unix_socket(&path),
        dir,
        path,
    }
}

fn socket_config(path: &str) -> GlideClientConfiguration {
    GlideClientConfiguration::new(vec![NodeAddress::unix(path)])
        .request_timeout(Duration::from_secs(5))
        .connection_timeout(Duration::from_secs(5))
}

/// Asserts `CLIENT INFO` shows a Unix socket client (`U` flag) and returns the line.
async fn assert_uses_unix_socket(client: &GlideClient) -> String {
    let info: String = cmd("CLIENT").arg("INFO").query_async(client).await.unwrap();
    let flags = info
        .split_whitespace()
        .find_map(|field| field.strip_prefix("flags="))
        .unwrap_or_else(|| panic!("no flags in CLIENT INFO: {info}"));
    assert!(flags.contains('U'), "expected a Unix socket client: {info}");
    info
}

timed_tokio_test!(
    async fn connects_over_unix_socket() {
        let server = unix_socket_server();
        let client = GlideClient::connect(socket_config(&server.path).database_id(2))
            .await
            .unwrap();

        let key = common::key("uds");
        client.set::<_, _, ()>(&key, "uds-value").await.unwrap();
        let value: Option<String> = client.get(&key).await.unwrap();
        assert_eq!(value.as_deref(), Some("uds-value"));
        let info = assert_uses_unix_socket(&client).await;
        assert!(info.contains(" db=2 "), "{info}");
    }
);

fn assert_configuration_error<T>(result: Result<T, GlideError>, expected: &str) {
    match result {
        Err(GlideError::Configuration(message)) => {
            assert!(message.contains(expected), "{expected}: {message}")
        }
        Err(other) => panic!("{expected}: expected a configuration error, got {other}"),
        Ok(_) => panic!("{expected}: expected a configuration error"),
    }
}

timed_tokio_test!(
    async fn connect_rejects_unix_socket_misconfiguration() {
        // The core rejects these before any I/O, so no server is needed.
        let socket = || NodeAddress::unix("/run/valkey/valkey.sock");
        for (config, expected) in [
            (
                GlideClientConfiguration::new(vec![socket()]).tls(TlsConfig::SecureTls),
                "TLS is not supported",
            ),
            (
                GlideClientConfiguration::new(vec![socket(), NodeAddress::default()]),
                "cannot be combined with TCP addresses",
            ),
        ] {
            assert_configuration_error(GlideClient::connect(config).await, expected);
        }

        let cluster = GlideClusterClientConfiguration::new(vec![socket()]);
        assert_configuration_error(
            GlideClusterClient::connect(cluster).await,
            "not supported in cluster mode",
        );
    }
);
