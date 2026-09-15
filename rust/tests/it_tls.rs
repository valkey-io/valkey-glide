// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! TLS integration tests.
//!
//! TODO #6996: standalone (`GlideClient`) TLS coverage

mod common;

use common::ClusterHarness;
use glide::{ConnectionManagementCommands, GlideClusterClient, TlsConfig};

timed_tokio_test!(
    async fn tls_cluster_connects() {
        let h = ClusterHarness::start_tls();
        assert_connected(h.client_with_tls().await).await;
    }
);

timed_tokio_test!(
    async fn tls_cluster_untrusted_rejected() {
        let h = ClusterHarness::start_tls();
        let untrusted = h.config().tls(TlsConfig::SecureTls);
        assert_not_connected(GlideClusterClient::connect(untrusted).await).await;
    }
);

timed_tokio_test!(
    async fn insecure_tls_cluster_connects() {
        let h = ClusterHarness::start_tls();
        assert_connected(h.client_with_insecure_tls().await).await;
    }
);

timed_tokio_test!(
    async fn mtls_cluster_connects() {
        let h = ClusterHarness::start_tls_mtls();
        assert_connected(h.client_with_mtls().await).await;
    }
);

timed_tokio_test!(
    async fn mtls_cluster_missing_client_cert_rejected() {
        let h = ClusterHarness::start_tls_mtls();
        assert_not_connected(GlideClusterClient::connect(h.config_with_tls()).await).await;
    }
);

// Asserts that the given client is connected.
async fn assert_connected(client: GlideClusterClient) {
    assert_eq!(client.ping().await.unwrap(), "PONG");
}

// Asserts that the given client is not connected.
async fn assert_not_connected(result: glide::Result<GlideClusterClient>) {
    match result {
        Err(_) => {} // expected
        Ok(client) => {
            let res = client.ping().await;
            assert!(
                res.is_err(),
                "untrusted/invalid TLS config must not allow cluster commands"
            );
        }
    }
}
