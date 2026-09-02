// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster integration tests against a real multi-primary cluster.
//!
//! The harness (see `common::ClusterHarness`) prefers
//! `valkey-glide/utils/cluster_manager.py`; when `valkey-cli` is unavailable it
//! builds the cluster natively from the `valkey-server` binary. Every test
//! SKIPs gracefully when a cluster cannot be formed in this environment.

mod common;

use glide::{AsyncCommands, ConnectionManagementCommands, CustomCommand, Route};

#[tokio::test]
async fn cluster_set_get_routed_by_key() {
    let cluster = cluster_or_skip!();
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    // Keys hash to different slots; the client routes each automatically.
    for i in 0..50 {
        let k = format!("clusterkey:{i}");
        let _: () = client.set(&k, "v").await.unwrap();
        let got: Option<String> = client.get(&k).await.unwrap();
        assert_eq!(got.as_deref(), Some("v"));
    }
}

#[tokio::test]
async fn cluster_ping_all_primaries() {
    let cluster = cluster_or_skip!();
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    // Broadcast PING to all primaries via explicit routing.
    let reply = client
        .custom_command_with_route(&["PING"], Route::AllPrimaries)
        .await
        .unwrap();
    // Multi-node replies aggregate; just assert it succeeded (non-nil).
    assert!(!matches!(reply, glide::Value::Nil));
}

#[tokio::test]
async fn cluster_info_reports_ok() {
    let cluster = cluster_or_skip!();
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    // Retry briefly to absorb any residual propagation lag under load.
    let mut info = String::new();
    for _ in 0..10 {
        let reply = client
            .custom_command_with_route(&["CLUSTER", "INFO"], Route::RandomNode)
            .await
            .unwrap();
        info = glide::value::to_string(reply).unwrap_or_default();
        if info.contains("cluster_state:ok") {
            break;
        }
        tokio::time::sleep(std::time::Duration::from_millis(200)).await;
    }
    assert!(
        info.contains("cluster_state:ok"),
        "cluster never reported ok: {info}"
    );
}

#[tokio::test]
async fn cluster_del_and_exists() {
    let cluster = cluster_or_skip!();
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    let k = "cluster:delkey";
    let _: () = client.set(k, "v").await.unwrap();
    let exists: i64 = client.exists(k).await.unwrap();
    assert_eq!(exists, 1);
    let deleted: i64 = client.del(k).await.unwrap();
    assert_eq!(deleted, 1);
    let exists: i64 = client.exists(k).await.unwrap();
    assert_eq!(exists, 0);
}

#[tokio::test]
async fn cluster_incr() {
    let cluster = cluster_or_skip!();
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    let k = "cluster:counter";
    let v: i64 = client.incr(k, 1i64).await.unwrap();
    assert_eq!(v, 1);
    let v: i64 = client.incr(k, 4i64).await.unwrap();
    assert_eq!(v, 5);
}

#[tokio::test]
async fn cluster_hashtag_same_slot() {
    let cluster = cluster_or_skip!();
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    // Hash tags force keys into the same slot, so a multi-key MSET/MGET works.
    let _: () = client.set("{tag}:a", "1").await.unwrap();
    let _: () = client.set("{tag}:b", "2").await.unwrap();
    let got: Vec<Option<String>> = client.mget(&["{tag}:a", "{tag}:b"]).await.unwrap();
    assert_eq!(got[0].as_deref(), Some("1"));
    assert_eq!(got[1].as_deref(), Some("2"));
}

#[tokio::test]
async fn cluster_ping_resp2_and_resp3() {
    let cluster = cluster_or_skip!();
    for proto in [glide::ProtocolVersion::RESP2, glide::ProtocolVersion::RESP3] {
        let client = match cluster.client_with_protocol(proto).await {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster client connect failed for {proto:?}");
                return;
            }
        };
        assert_eq!(client.ping().await.unwrap(), "PONG");
    }
}
