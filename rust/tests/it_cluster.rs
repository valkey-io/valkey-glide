// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster integration tests against a real multi-primary cluster.

mod common;

use glide::{AsyncTypedCommands, CustomCommand, FromValkeyValue, GlideError, Route};
use std::time::Duration;

#[tokio::test]
async fn cluster_set_get_routed_by_key() {
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

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
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

    // Broadcast PING to all primaries via explicit routing.
    let reply = client
        .custom_command_with_route(&["PING"], Route::AllPrimaries)
        .await
        .unwrap();
    // Multi-node replies aggregate; just assert it succeeded (non-nil).
    assert!(!matches!(reply, glide::ValkeyValue::Nil));
}

#[tokio::test]
async fn cluster_info_reports_ok() {
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

    // Retry briefly to absorb any residual propagation lag under load.
    let mut info = String::new();
    for _ in 0..10 {
        let reply = client
            .custom_command_with_route(&["CLUSTER", "INFO"], Route::RandomNode)
            .await
            .unwrap();
        info = String::from_owned_valkey_value(reply).unwrap_or_default();
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
async fn cluster_info_with_partial_results_keeps_replies_when_a_primary_is_slow() {
    let cluster = common::ClusterHarness::start().await;
    let client = cluster
        .client_with_config(cluster.config().request_timeout(Duration::from_millis(500)))
        .await;
    // A second client pauses the primary, so the pause is not queued behind the
    // client under test.
    let pauser = cluster.client().await;

    let healthy = client.info_with_partial_results(&[], None).await.unwrap();
    let mut primaries: Vec<String> = healthy.keys().cloned().collect();
    primaries.sort();
    assert_eq!(primaries.len(), cluster.primary_ports.len());
    for reply in healthy.values() {
        assert!(reply.as_ref().unwrap().contains("# Server"), "{reply:?}");
    }

    let (host, port) = primaries[0].rsplit_once(':').unwrap();
    let route = Route::ByAddress {
        host: host.to_string(),
        port: port.parse().unwrap(),
    };
    let err = client
        .info_with_partial_results(&[], Some(route.clone()))
        .await
        .unwrap_err();
    assert!(matches!(err, GlideError::Request(_)), "{err:?}");

    pauser
        // Long enough for both calls below to see the node as slow.
        .custom_command_with_route(&["CLIENT", "PAUSE", "3000", "ALL"], route.clone())
        .await
        .unwrap();

    // Without partial results, one slow primary fails the whole call.
    let err = client
        .custom_command_with_route(&["INFO"], Route::AllPrimaries)
        .await
        .unwrap_err();
    assert!(matches!(err, GlideError::Timeout(_)), "{err:?}");

    let replies = client.info_with_partial_results(&[], None).await.unwrap();
    let mut addresses: Vec<String> = replies.keys().cloned().collect();
    addresses.sort();
    assert_eq!(addresses, primaries);
    assert!(
        matches!(replies[&primaries[0]], Err(GlideError::Timeout(_))),
        "{:?}",
        replies[&primaries[0]]
    );
    for address in &primaries[1..] {
        assert!(replies[address].as_ref().unwrap().contains("# Server"));
    }

    // CLIENT UNPAUSE would wait for the pause too; wait for it to end.
    let mut recovered = false;
    for _ in 0..50 {
        if pauser
            .custom_command_with_route(&["PING"], route.clone())
            .await
            .is_ok()
        {
            recovered = true;
            break;
        }
    }
    assert!(recovered, "paused primary did not recover");
}

#[tokio::test]
async fn cluster_del_and_exists() {
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

    let k = "cluster:delkey";
    let _: () = client.set(k, "v").await.unwrap();
    let exists: bool = client.exists(k).await.unwrap();
    assert!(exists);
    let deleted: usize = client.del(k).await.unwrap();
    assert_eq!(deleted, 1);
    let exists: bool = client.exists(k).await.unwrap();
    assert!(!exists);
}

#[tokio::test]
async fn cluster_incr() {
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

    let k = "cluster:counter";
    let v: isize = client.incr(k, 1i64).await.unwrap();
    assert_eq!(v, 1);
    let v: isize = client.incr(k, 4i64).await.unwrap();
    assert_eq!(v, 5);
}

#[tokio::test]
async fn cluster_hashtag_same_slot() {
    let cluster = common::ClusterHarness::start().await;
    let client = cluster.client().await;

    // Hash tags force keys into the same slot, so a multi-key MSET/MGET works.
    let _: () = client.set("{tag}:a", "1").await.unwrap();
    let _: () = client.set("{tag}:b", "2").await.unwrap();
    let got: Vec<Option<String>> = client.mget(&["{tag}:a", "{tag}:b"]).await.unwrap();
    assert_eq!(got[0].as_deref(), Some("1"));
    assert_eq!(got[1].as_deref(), Some("2"));
}

#[tokio::test]
async fn cluster_ping_resp2_and_resp3() {
    let cluster = common::ClusterHarness::start().await;
    for proto in [glide::ProtocolVersion::RESP2, glide::ProtocolVersion::RESP3] {
        let client = cluster.client_with_protocol(proto).await;
        assert_eq!(client.ping().await.unwrap(), "PONG");
    }
}
