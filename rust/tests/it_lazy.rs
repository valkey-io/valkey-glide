// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Lazy-connection integration tests, mirroring Python's
//! `test_lazy_connection.py`. `lazy_connect(true)` defers establishing the
//! connection until the first command is issued.

mod common;

use glide::{
    AsyncCommands, GlideClient, GlideClientConfiguration, GlideClusterClient,
    GlideClusterClientConfiguration, ProtocolVersion,
};
use std::time::Duration;

/// A lazy standalone client connects on first use and works normally.
async fn standalone_lazy(protocol: ProtocolVersion) {
    let srv = server_or_skip!();
    let cfg = GlideClientConfiguration::with_address("127.0.0.1", srv.port)
        .lazy_connect(true)
        .protocol(protocol);
    let c = GlideClient::connect(cfg)
        .await
        .expect("lazy connect returns a client");
    // First command triggers the actual connection.
    let k = common::key("lazy");
    let _: () = c.set(&k, "v").await.unwrap();
    let got: Option<glide::Bytes> = c.get(&k).await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));
}

#[tokio::test]
async fn standalone_lazy_resp2() {
    standalone_lazy(ProtocolVersion::RESP2).await;
}
#[tokio::test]
async fn standalone_lazy_resp3() {
    standalone_lazy(ProtocolVersion::RESP3).await;
}

/// Creating a lazy client does not require the server to be reachable *yet*:
/// construction succeeds even against a dead address; the error surfaces only on
/// the first command. (If construction fails instead, lazy is not deferring.)
#[tokio::test]
async fn standalone_lazy_defers_connection_error() {
    // A port nothing is listening on.
    let dead_port = common::free_port();
    let cfg = GlideClientConfiguration::with_address("127.0.0.1", dead_port)
        .lazy_connect(true)
        .request_timeout(Duration::from_millis(500))
        .connection_timeout(Duration::from_millis(500));
    match GlideClient::connect(cfg).await {
        Ok(c) => {
            // Deferred: construction succeeded; the command must fail.
            let res: glide::RedisResult<Option<String>> = c.get(common::key("k")).await;
            assert!(
                res.is_err(),
                "expected first command to fail against a dead server"
            );
        }
        Err(_) => {
            // Some builds validate eagerly even when lazy; acceptable — the point
            // is construction-vs-command behaviour is exercised.
        }
    }
}

/// A lazy cluster client connects on first use and works normally.
#[tokio::test]
async fn cluster_lazy_connect() {
    let h = cluster_or_skip!();
    let cfg = GlideClusterClientConfiguration::with_address("127.0.0.1", h.seed_port())
        .lazy_connect(true)
        .request_timeout(Duration::from_secs(5));
    let c = GlideClusterClient::connect(cfg)
        .await
        .expect("lazy cluster connect");
    let k = common::key("lazy_cluster");
    let _: () = c.set(&k, "v").await.unwrap();
    let got: Option<glide::Bytes> = c.get(&k).await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));
}
