// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Read-from-strategy integration tests, mirroring Python's
//! `test_read_from_strategy.py`. Verifies that reads return correct data under
//! each [`ReadFrom`] strategy — on a standalone server (replica-less: strategies
//! fall back to the primary) and on a real cluster with **replicas** (only when
//! the cluster_manager.py backend is active; the native backend has no replicas
//! and those tests SKIP).

mod common;

use glide::{
    AsyncCommands, GlideClient, GlideClientConfiguration, GlideClusterClient,
    GlideClusterClientConfiguration, ProtocolVersion, ReadFrom,
};
use std::time::Duration;

/// Read the key with a short retry loop to tolerate replica replication lag.
async fn get_with_retry(c: &GlideClusterClient, k: &str) -> Option<glide::Bytes> {
    for _ in 0..40 {
        if let Ok(Some(v)) = c.get::<_, Option<glide::Bytes>>(k).await {
            return Some(v);
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    None
}

async fn standalone_roundtrip(read_from: ReadFrom, protocol: ProtocolVersion) {
    let srv = server_or_skip!();
    let cfg = GlideClientConfiguration::with_address("127.0.0.1", srv.port)
        .read_from(read_from)
        .protocol(protocol);
    let c = GlideClient::connect(cfg).await.expect("connect");
    let k = common::key("rf");
    let _: () = c.set(&k, "v").await.unwrap();
    // With no replica, every strategy resolves to the primary → immediate read.
    let got: Option<glide::Bytes> = c.get(&k).await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));
}

#[tokio::test]
async fn standalone_primary_resp2() {
    standalone_roundtrip(ReadFrom::Primary, ProtocolVersion::RESP2).await;
}
#[tokio::test]
async fn standalone_primary_resp3() {
    standalone_roundtrip(ReadFrom::Primary, ProtocolVersion::RESP3).await;
}
#[tokio::test]
async fn standalone_prefer_replica_resp2() {
    standalone_roundtrip(ReadFrom::PreferReplica, ProtocolVersion::RESP2).await;
}
#[tokio::test]
async fn standalone_prefer_replica_resp3() {
    standalone_roundtrip(ReadFrom::PreferReplica, ProtocolVersion::RESP3).await;
}

/// PreferReplica on a real replica-ful cluster: the value written to a primary is
/// eventually readable via a replica. SKIPs on the native (replica-less) backend.
#[tokio::test]
async fn cluster_prefer_replica_reads_data() {
    let h = cluster_or_skip!();
    if h.replica_ports.is_empty() {
        eprintln!("SKIP: cluster has no replicas (native backend); set GLIDE_CLUSTER_MANAGER");
        return;
    }
    let cfg = GlideClusterClientConfiguration::with_address("127.0.0.1", h.seed_port())
        .read_from(ReadFrom::PreferReplica)
        .request_timeout(Duration::from_secs(5));
    let c = GlideClusterClient::connect(cfg).await.expect("connect");
    let k = common::key("rf_cluster");
    let _: () = c.set(&k, "replicated").await.unwrap();
    assert_eq!(
        get_with_retry(&c, &k).await.as_deref(),
        Some(&b"replicated"[..])
    );
}

/// AZAffinity is accepted end-to-end and (with no AZ configured on the nodes)
/// falls back gracefully so commands still succeed. Works on any cluster backend.
#[tokio::test]
async fn cluster_az_affinity_config_is_accepted() {
    let h = cluster_or_skip!();
    let cfg = GlideClusterClientConfiguration::with_address("127.0.0.1", h.seed_port())
        .read_from(ReadFrom::AZAffinity("use1-az1".to_string()))
        .request_timeout(Duration::from_secs(5));
    let c = GlideClusterClient::connect(cfg).await.expect("connect");
    let k = common::key("rf_az");
    let _: () = c.set(&k, "v").await.unwrap();
    assert_eq!(get_with_retry(&c, &k).await.as_deref(), Some(&b"v"[..]));
}

/// AllNodes read strategy is accepted and commands succeed on a cluster.
#[tokio::test]
async fn cluster_all_nodes_config_is_accepted() {
    let h = cluster_or_skip!();
    let cfg = GlideClusterClientConfiguration::with_address("127.0.0.1", h.seed_port())
        .read_from(ReadFrom::AllNodes)
        .request_timeout(Duration::from_secs(5));
    let c = GlideClusterClient::connect(cfg).await.expect("connect");
    let k = common::key("rf_all");
    let _: () = c.set(&k, "v").await.unwrap();
    assert_eq!(get_with_retry(&c, &k).await.as_deref(), Some(&b"v"[..]));
}
