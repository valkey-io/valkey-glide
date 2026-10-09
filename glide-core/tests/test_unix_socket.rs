// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests for connecting to a standalone server over a Unix domain socket.
//! Every server here is started with `--port 0`, so a successful connection can
//! only have gone through the socket.

mod constants;
mod utilities;

#[cfg(test)]
mod unix_socket_tests {
    use super::utilities::*;
    use glide_core::client::{
        Client, ConnectionError, MonitorClient, MonitorLine, MonitorLineCallback, NodeAddress,
        TlsMode,
    };
    use glide_core::connection_request;
    use redis::{ConnectionAddr, RedisConnectionInfo, Value};
    use rstest::rstest;
    use std::path::PathBuf;
    use std::sync::{Arc, Mutex};
    use std::time::Duration;

    /// Starts a Unix-socket-only server and waits until it accepts connections.
    async fn start_unix_server() -> (RedisServer, ConnectionAddr) {
        let server = RedisServer::new(ServerType::Unix);
        let addr = server.get_client_addr();
        wait_for_server_to_become_ready(&addr).await;
        (server, addr)
    }

    fn socket_path(addr: &ConnectionAddr) -> PathBuf {
        match addr {
            ConnectionAddr::Unix(path) => path.clone(),
            other => panic!("expected a Unix address, got {other:?}"),
        }
    }

    fn unix_request(
        addr: &ConnectionAddr,
        configuration: TestConfiguration,
    ) -> connection_request::ConnectionRequest {
        create_connection_request(std::slice::from_ref(addr), &configuration)
    }

    async fn set_and_get(client: &mut impl glide_core::client::GlideClientForTests, key: &str) {
        let mut set_cmd = redis::cmd("SET");
        set_cmd.arg(key).arg("unix-value");
        let set_result = client.send_command(&mut set_cmd, None).await.unwrap();
        assert_eq!(set_result, Value::Okay);

        let mut get_cmd = redis::cmd("GET");
        get_cmd.arg(key);
        let get_result = client.send_command(&mut get_cmd, None).await.unwrap();
        assert_eq!(get_result, Value::BulkString(b"unix-value".to_vec().into()));
    }

    /// Asserts that the server sees this client's connection as a Unix socket client
    /// (the `U` flag in `CLIENT INFO`), and returns the `CLIENT INFO` line.
    async fn assert_client_uses_socket(
        client: &mut impl glide_core::client::GlideClientForTests,
    ) -> String {
        let mut info_cmd = redis::cmd("CLIENT");
        info_cmd.arg("INFO");
        let info: String =
            redis::from_owned_redis_value(client.send_command(&mut info_cmd, None).await.unwrap())
                .unwrap();
        let flags = info
            .split_whitespace()
            .find_map(|field| field.strip_prefix("flags="))
            .unwrap_or_else(|| panic!("no flags in CLIENT INFO: {info}"));
        assert!(
            flags.contains('U'),
            "expected a Unix socket client, got: {info}"
        );
        info
    }

    async fn expect_configuration_error(request: connection_request::ConnectionRequest) -> String {
        match Client::new(request.into(), None).await {
            Ok(_) => panic!("client creation should have been rejected"),
            Err(ConnectionError::Configuration(message)) => message,
            Err(other) => panic!("expected a configuration error, got: {other}"),
        }
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_client_connects_over_unix_socket(#[values(false, true)] lazy_connect: bool) {
        block_on_all(async move {
            let (_server, addr) = start_unix_server().await;
            let request = unix_request(
                &addr,
                TestConfiguration {
                    lazy_connect,
                    database_id: 3,
                    ..Default::default()
                },
            );

            let mut client = Client::new(request.into(), None)
                .await
                .expect("client over a Unix socket should connect");

            set_and_get(&mut client, "client-unix-key").await;
            let info = assert_client_uses_socket(&mut client).await;
            assert!(info.contains(" db=3 "), "expected db=3, got: {info}");
        });
    }

    #[rstest]
    #[timeout(LONG_STANDALONE_TEST_TIMEOUT)]
    fn test_client_reconnects_over_unix_socket_after_server_restart() {
        block_on_all(async {
            let (server, addr) = start_unix_server().await;
            let request = unix_request(&addr, TestConfiguration::default());
            let mut client = Client::new(request.into(), None).await.unwrap();
            assert_connected(&mut client).await;

            // Stopping the server also removes the socket file.
            drop(server);
            let _new_server = RedisServer::new_with_addr_and_modules(addr.clone(), &[]);
            wait_for_server_to_become_ready(&addr).await;

            retry_until_timeout(
                || async {
                    let mut client = client.clone();
                    let mut ping = redis::cmd("PING");
                    client.send_command(&mut ping, None).await.ok()
                },
                Duration::from_secs(10),
            )
            .await;
            set_and_get(&mut client, "restarted-unix-key").await;
        });
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_client_fails_when_unix_socket_does_not_exist() {
        block_on_all(async {
            let (a, b) = rand::random::<(u64, u64)>();
            let addr =
                ConnectionAddr::Unix(PathBuf::from(format!("/tmp/glide-missing-{a}-{b}.sock")));
            let mut request = unix_request(&addr, TestConfiguration::default());
            request.connection_timeout = 500;

            let error = match Client::new(request.into(), None).await {
                Ok(_) => panic!("connecting to a missing socket path should fail"),
                Err(error) => error.to_string(),
            };
            let endpoint = format!("unix:/tmp/glide-missing-{a}-{b}.sock");
            assert!(
                error.contains(&endpoint),
                "expected the error to name `{endpoint}`, got: {error}"
            );
        });
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_unix_socket_rejected_in_cluster_mode(#[values(false, true)] lazy_connect: bool) {
        block_on_all(async move {
            let addr = ConnectionAddr::Unix(PathBuf::from("/tmp/glide-cluster.sock"));
            let request = unix_request(
                &addr,
                TestConfiguration {
                    cluster_mode: ClusterMode::Enabled,
                    lazy_connect,
                    ..Default::default()
                },
            );

            let message = expect_configuration_error(request).await;
            assert!(
                message.contains("cluster mode"),
                "unexpected error: {message}"
            );
        });
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_monitor_over_unix_socket() {
        block_on_all(async {
            let (_server, addr) = start_unix_server().await;
            let path = socket_path(&addr);

            let lines: Arc<Mutex<Vec<MonitorLine>>> = Arc::new(Mutex::new(Vec::new()));
            let lines_clone = lines.clone();
            let on_line: MonitorLineCallback = Arc::new(move |line| {
                lines_clone.lock().unwrap().push(line);
            });
            let address = NodeAddress {
                host: String::new(),
                port: 0,
                unix_socket_path: Some(path.clone()),
            };
            let monitor = MonitorClient::new(
                &address,
                RedisConnectionInfo::default(),
                TlsMode::NoTls,
                on_line,
            )
            .await
            .expect("MonitorClient over a Unix socket should connect");

            let request = unix_request(&addr, TestConfiguration::default());
            let mut client = Client::new(request.into(), None).await.unwrap();
            set_and_get(&mut client, "monitor-unix-key").await;

            let expected_client_addr = format!("unix:{}", path.display());
            // Generous deadline for slow/loaded CI, as in test_monitor.rs.
            let deadline = std::time::Instant::now() + Duration::from_secs(15);
            loop {
                let seen = lines.lock().unwrap().iter().any(|l| {
                    l.command == "SET"
                        && l.args.first().map(String::as_str) == Some("monitor-unix-key")
                        && l.client_addr == expected_client_addr
                });
                if seen {
                    break;
                }
                assert!(
                    std::time::Instant::now() < deadline,
                    "timed out waiting for the SET line; diagnostics: {:?}",
                    monitor.diagnostics()
                );
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
            monitor.stop_async().await;
        });
    }

    #[tokio::test]
    async fn test_monitor_rejects_unix_socket_with_tls() {
        let address = NodeAddress {
            host: String::new(),
            port: 0,
            unix_socket_path: Some(PathBuf::from("/tmp/glide-monitor.sock")),
        };
        let on_line: MonitorLineCallback = Arc::new(|_| {});
        let result = MonitorClient::new(
            &address,
            RedisConnectionInfo::default(),
            TlsMode::SecureTls,
            on_line,
        )
        .await;
        let err = result
            .err()
            .expect("MonitorClient should reject TLS over a Unix socket");
        assert_eq!(err.kind(), redis::ErrorKind::InvalidClientConfig);
        assert!(
            err.to_string()
                .contains("TLS is not supported over Unix domain sockets"),
            "unexpected error: {err}"
        );
    }
}
