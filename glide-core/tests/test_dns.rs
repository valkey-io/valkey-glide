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

use test_constants::*;

#[cfg(test)]
mod dns_tests {
    use super::*;
    use crate::utilities::{cluster::RedisCluster, cluster::SHORT_CLUSTER_TEST_TIMEOUT, *};
    use glide_core::{
        client::{Client, StandaloneClient},
        connection_request::TlsMode,
    };
    use once_cell::sync::Lazy;
    use rstest::rstest;
    use std::env;

    // Shared temp directory and TLS paths for all DNS tests
    static TLS_TEMPDIR: Lazy<tempfile::TempDir> =
        Lazy::new(|| tempfile::tempdir().expect("Failed to create temp dir for TLS certs"));
    static TLS_PATHS: Lazy<TlsFilePaths> = Lazy::new(|| build_tls_file_paths(&TLS_TEMPDIR));
    static CA_CERT_BYTES: Lazy<Vec<u8>> = Lazy::new(|| TLS_PATHS.read_ca_cert_as_bytes());

    fn dns_tests_enabled() -> bool {
        env::var("VALKEY_GLIDE_DNS_TESTS_ENABLED").is_ok()
    }

    fn extract_port(addr: &redis::ConnectionAddr) -> u16 {
        match addr {
            redis::ConnectionAddr::Tcp(_, p) => *p,
            redis::ConnectionAddr::TcpTls { port, .. } => *port,
            _ => panic!("Unexpected address type"),
        }
    }

    /// Builds and returns a non-TLS cluster client with the specified hostname.
    async fn build_cluster_client(hostname: &str) -> Option<(Client, RedisCluster)> {
        let cluster = RedisCluster::new(false, &None, None, None);
        let port = extract_port(&cluster.get_server_addresses()[0]);
        let addr = redis::ConnectionAddr::Tcp(hostname.to_string(), port);

        let connection_request = create_connection_request(
            &[addr],
            &TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: false,
                ..Default::default()
            },
        );

        // Wait to ensure server is ready before connecting.
        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;

        let client = Client::new(connection_request.into(), None).await.ok()?;
        Some((client, cluster))
    }

    /// Builds and returns a TLS cluster client with the specified hostname.
    async fn build_tls_cluster_client(hostname: &str) -> Option<(Client, RedisCluster)> {
        let cluster = RedisCluster::new_with_tls(3, 0, Some(TLS_PATHS.clone()));

        let port = extract_port(&cluster.get_server_addresses()[0]);
        let addr = redis::ConnectionAddr::TcpTls {
            host: hostname.to_string(),
            port,
            insecure: false,
            tls_params: None,
        };

        let mut connection_request = create_connection_request(
            &[addr],
            &TestConfiguration {
                use_tls: true,
                cluster_mode: ClusterMode::Enabled,
                shared_server: false,
                ..Default::default()
            },
        );
        connection_request.tls_mode = TlsMode::SecureTls.into();
        connection_request.root_certs = vec![CA_CERT_BYTES.clone().into()];

        // Wait to ensure server is ready before connecting.
        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;

        let client = Client::new(connection_request.into(), None).await.ok()?;
        Some((client, cluster))
    }

    /// Builds and returns a non-TLS standalone client with the specified hostname.
    async fn build_standalone_client(hostname: &str) -> Option<(StandaloneClient, RedisServer)> {
        let server = RedisServer::new(ServerType::Tcp { tls: false });
        let port = extract_port(&server.get_client_addr());
        let addr = redis::ConnectionAddr::Tcp(hostname.to_string(), port);

        let connection_request = create_connection_request(
            &[addr],
            &TestConfiguration {
                shared_server: false,
                ..Default::default()
            },
        );

        // Wait to ensure server is ready before connecting.
        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;

        let client = StandaloneClient::create_client(connection_request.into(), None, None, None)
            .await
            .ok()?;
        Some((client, server))
    }

    async fn build_tls_standalone_client(
        hostname: &str,
    ) -> Option<(StandaloneClient, RedisServer)> {
        let server = RedisServer::new_with_tls(true, Some(TLS_PATHS.clone()));

        let port = extract_port(&server.get_client_addr());
        let addr = redis::ConnectionAddr::TcpTls {
            host: hostname.to_string(),
            port,
            insecure: false,
            tls_params: None,
        };

        let mut connection_request = create_connection_request(
            &[addr],
            &TestConfiguration {
                use_tls: true,
                shared_server: false,
                ..Default::default()
            },
        );
        connection_request.tls_mode = TlsMode::SecureTls.into();
        connection_request.root_certs = vec![CA_CERT_BYTES.clone().into()];

        // Wait to ensure server is ready before connecting.
        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;

        let client = StandaloneClient::create_client(connection_request.into(), None, None, None)
            .await
            .ok()?;
        Some((client, server))
    }

    // ==================== Standalone Tests ====================

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_standalone_connect_with_valid_hostname_no_tls() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let (mut client, _server) = build_standalone_client(HOSTNAME_NO_TLS)
                .await
                .expect("Failed to connect");
            assert_connected(&mut client).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_standalone_connect_with_invalid_hostname_no_tls() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let result = build_standalone_client("nonexistent.invalid").await;
            assert!(result.is_none());
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_standalone_tls_connect_with_hostname_in_cert() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let (mut client, _server) = build_tls_standalone_client(HOSTNAME_TLS)
                .await
                .expect("Failed to connect");
            assert_connected(&mut client).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_standalone_tls_connect_with_hostname_not_in_cert() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let result = build_tls_standalone_client(HOSTNAME_NO_TLS).await;
            assert!(result.is_none());
        });
    }

    // ==================== Cluster Tests ====================

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cluster_connect_with_valid_hostname_no_tls() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let (mut client, _cluster) = build_cluster_client(HOSTNAME_NO_TLS)
                .await
                .expect("Failed to connect");
            assert_connected(&mut client).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cluster_connect_with_invalid_hostname_no_tls() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let result = build_cluster_client("nonexistent.invalid").await;
            assert!(result.is_none());
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cluster_tls_connect_with_hostname_in_cert() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let (mut client, _cluster) = build_tls_cluster_client(HOSTNAME_TLS)
                .await
                .expect("Failed to connect");
            assert_connected(&mut client).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cluster_tls_connect_with_hostname_not_in_cert() {
        if !dns_tests_enabled() {
            return;
        }

        block_on_all(async move {
            let result = build_tls_cluster_client(HOSTNAME_NO_TLS).await;
            assert!(result.is_none());
        });
    }
}
