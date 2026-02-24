// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! DNS resolution tests.
//!
//! These tests require host file entries and environment variable:
//!
//! Host file entries:
//! - 127.0.0.1 valkey.glide.test.tls.com
//! - 127.0.0.1 valkey.glide.test.no_tls.com
//! - ::1 valkey.glide.test.tls.com
//! - ::1 valkey.glide.test.no_tls.com
//!
//! Environment variable:
//! - VALKEY_GLIDE_DNS_TESTS_ENABLED=1

mod test_constants;
mod utilities;

#[cfg(test)]
mod dns_tests {
    use super::*;
    use glide_core::{
        client::{Client, StandaloneClient},
        connection_request::TlsMode,
    };
    use redis::Value;
    use rstest::rstest;
    use std::env;
    use test_constants::*;
    use utilities::cluster::SHORT_CLUSTER_TEST_TIMEOUT;
    use utilities::*;

    /// Check if DNS tests are enabled via environment variable
    fn dns_tests_enabled() -> bool {
        env::var("VALKEY_GLIDE_DNS_TESTS_ENABLED").is_ok()
    }

    /// Create connection request with TLS configuration
    fn create_tls_connection_request(
        addr: redis::ConnectionAddr,
        cluster_mode: bool,
        ca_cert_bytes: Vec<u8>,
    ) -> glide_core::connection_request::ConnectionRequest {
        let mut connection_request = create_connection_request(
            &[addr],
            &TestConfiguration {
                use_tls: true,
                cluster_mode: if cluster_mode {
                    ClusterMode::Enabled
                } else {
                    ClusterMode::Disabled
                },
                shared_server: false,
                ..Default::default()
            },
        );
        connection_request.tls_mode = TlsMode::SecureTls.into();
        connection_request.root_certs = vec![ca_cert_bytes.into()];
        connection_request
    }

    /// Extract port from ConnectionAddr
    fn extract_port(addr: &redis::ConnectionAddr) -> u16 {
        match addr {
            redis::ConnectionAddr::Tcp(_, p) => *p,
            redis::ConnectionAddr::TcpTls { port, .. } => *port,
            _ => panic!("Unexpected address type"),
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_connect_with_valid_hostname_no_tls(#[values(false, true)] cluster_mode: bool) {
        if !dns_tests_enabled() {
            eprintln!("Skipping DNS test: VALKEY_GLIDE_DNS_TESTS_ENABLED not set");
            return;
        }

        block_on_all(async move {
            let addr = if cluster_mode {
                let test_basics =
                    utilities::cluster::setup_test_basics_internal(TestConfiguration {
                        use_tls: false,
                        cluster_mode: ClusterMode::Enabled,
                        shared_server: false,
                        ..Default::default()
                    })
                    .await;
                test_basics.cluster.as_ref().unwrap().get_server_addresses()[0].clone()
            } else {
                let test_basics = setup_test_basics_internal(&TestConfiguration {
                    use_tls: false,
                    shared_server: false,
                    ..Default::default()
                })
                .await;
                test_basics.server.as_ref().unwrap().get_client_addr()
            };

            let hostname_addr =
                redis::ConnectionAddr::Tcp(HOSTNAME_NO_TLS.to_string(), extract_port(&addr));
            let connection_request = create_connection_request(
                &[hostname_addr],
                &TestConfiguration {
                    use_tls: false,
                    cluster_mode: if cluster_mode {
                        ClusterMode::Enabled
                    } else {
                        ClusterMode::Disabled
                    },
                    shared_server: false,
                    ..Default::default()
                },
            );

            let mut ping_cmd = redis::cmd("PING");
            let result = if cluster_mode {
                let mut client = Client::new(connection_request.into(), None)
                    .await
                    .expect("Failed to connect with valid hostname");
                client.send_command(&mut ping_cmd, None).await.unwrap()
            } else {
                let mut client =
                    StandaloneClient::create_client(connection_request.into(), None, None, None)
                        .await
                        .expect("Failed to connect with valid hostname");
                client.send_command(&mut ping_cmd).await.unwrap()
            };

            assert_eq!(result, Value::SimpleString("PONG".to_string()));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_connect_with_invalid_hostname_no_tls(#[values(false, true)] cluster_mode: bool) {
        if !dns_tests_enabled() {
            eprintln!("Skipping DNS test: VALKEY_GLIDE_DNS_TESTS_ENABLED not set");
            return;
        }

        block_on_all(async move {
            let invalid_addr = redis::ConnectionAddr::Tcp("nonexistent.invalid".to_string(), 6379);
            let connection_request = create_connection_request(
                &[invalid_addr],
                &TestConfiguration {
                    use_tls: false,
                    cluster_mode: if cluster_mode {
                        ClusterMode::Enabled
                    } else {
                        ClusterMode::Disabled
                    },
                    shared_server: false,
                    ..Default::default()
                },
            );

            if cluster_mode {
                let result = Client::new(connection_request.into(), None).await;
                assert!(
                    result.is_err(),
                    "Expected connection to fail with invalid hostname"
                );
            } else {
                let result =
                    StandaloneClient::create_client(connection_request.into(), None, None, None)
                        .await;
                assert!(
                    result.is_err(),
                    "Expected connection to fail with invalid hostname"
                );
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_tls_connect_with_hostname_in_cert(#[values(false, true)] cluster_mode: bool) {
        if !dns_tests_enabled() {
            eprintln!("Skipping DNS test: VALKEY_GLIDE_DNS_TESTS_ENABLED not set");
            return;
        }

        block_on_all(async move {
            let tls_paths = build_tls_file_paths();
            let ca_cert_bytes = tls_paths.read_ca_cert_as_bytes();

            let (addr, _server) = if cluster_mode {
                let cluster = utilities::cluster::RedisCluster::new_with_tls(3, 0, Some(tls_paths));
                let addr = cluster.get_server_addresses()[0].clone();
                (addr, None)
            } else {
                let server = RedisServer::new_with_addr_tls_modules_and_spawner(
                    redis::ConnectionAddr::TcpTls {
                        host: HOSTNAME_TLS.to_string(),
                        port: get_available_port(),
                        insecure: false,
                        tls_params: None,
                    },
                    Some(tls_paths),
                    &[],
                    false,
                    |cmd| cmd.spawn().expect("Failed to spawn server"),
                );
                let addr = server.get_client_addr();
                tokio::time::sleep(std::time::Duration::from_millis(200)).await;
                (addr, Some(server))
            };

            let hostname_addr = redis::ConnectionAddr::TcpTls {
                host: HOSTNAME_TLS.to_string(),
                port: extract_port(&addr),
                insecure: false,
                tls_params: None,
            };

            let connection_request =
                create_tls_connection_request(hostname_addr, cluster_mode, ca_cert_bytes);

            let mut ping_cmd = redis::cmd("PING");
            let result = if cluster_mode {
                let mut client = Client::new(connection_request.into(), None)
                    .await
                    .expect("Failed to connect with hostname in cert");
                client.send_command(&mut ping_cmd, None).await.unwrap()
            } else {
                let mut client =
                    StandaloneClient::create_client(connection_request.into(), None, None, None)
                        .await
                        .expect("Failed to connect with hostname in cert");
                client.send_command(&mut ping_cmd).await.unwrap()
            };

            assert_eq!(result, Value::SimpleString("PONG".to_string()));
            drop(_server); // Keep server alive
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_tls_connect_with_hostname_not_in_cert(#[values(false, true)] cluster_mode: bool) {
        if !dns_tests_enabled() {
            eprintln!("Skipping DNS test: VALKEY_GLIDE_DNS_TESTS_ENABLED not set");
            return;
        }

        block_on_all(async move {
            let tls_paths = build_tls_file_paths();
            let ca_cert_bytes = tls_paths.read_ca_cert_as_bytes();

            let (addr, _server) = if cluster_mode {
                let cluster = utilities::cluster::RedisCluster::new_with_tls(3, 0, Some(tls_paths));
                let addr = cluster.get_server_addresses()[0].clone();
                (addr, None)
            } else {
                let server = RedisServer::new_with_addr_tls_modules_and_spawner(
                    redis::ConnectionAddr::TcpTls {
                        host: HOST_IPV4.to_string(),
                        port: get_available_port(),
                        insecure: false,
                        tls_params: None,
                    },
                    Some(tls_paths),
                    &[],
                    false,
                    |cmd| cmd.spawn().expect("Failed to spawn server"),
                );
                let addr = server.get_client_addr();
                tokio::time::sleep(std::time::Duration::from_millis(200)).await;
                (addr, Some(server))
            };

            let hostname_addr = redis::ConnectionAddr::TcpTls {
                host: HOSTNAME_NO_TLS.to_string(),
                port: extract_port(&addr),
                insecure: false,
                tls_params: None,
            };

            let connection_request =
                create_tls_connection_request(hostname_addr, cluster_mode, ca_cert_bytes);

            let is_err = if cluster_mode {
                Client::new(connection_request.into(), None).await.is_err()
            } else {
                StandaloneClient::create_client(connection_request.into(), None, None, None)
                    .await
                    .is_err()
            };

            assert!(
                is_err,
                "Expected connection to fail with hostname not in cert"
            );
            drop(_server); // Keep server alive
        });
    }
}
