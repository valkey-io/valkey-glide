// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Client-level integration tests that exercise the parts of `client.rs` not
//! covered by the per-family command suites: the cluster-scan iterator,
//! `route_command`, and the Pub/Sub subscribe→publish→receive path.

mod common;

use glide::client::{ClusterScanCursor, PubSubMessageKind};
use glide::config::{PubSubChannelMode, PubSubSubscriptions};
use glide::{AsyncCommands, GlideClient, GlideClientConfiguration, Route};
use redis::Cmd;
use std::collections::HashSet;
use std::time::Duration;

// ---------------------------------------------------------------------------
// Pub/Sub (standalone)
// ---------------------------------------------------------------------------

timed_tokio_test!(
    async fn pubsub_publish_receive_exact() {
        let srv = server_or_skip!();
        let channel = common::key("chan");

        let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Exact, channel.clone());
        let subscriber = GlideClient::connect(
            GlideClientConfiguration::with_address("127.0.0.1", srv.port).subscriptions(subs),
        )
        .await
        .expect("connect subscriber");
        let publisher = srv.client().await;

        // Wait until the subscription is registered server-side (poll, not sleep).
        assert!(
            common::wait_for_numsub(&publisher, &channel, |n| n >= 1, Duration::from_secs(3)).await,
            "subscription was not registered server-side in time"
        );
        let n: i64 = publisher.publish(&channel, "hello").await.unwrap();
        assert!(n >= 1, "expected at least one receiver, got {n}");

        let msg = tokio::time::timeout(Duration::from_secs(3), subscriber.get_pubsub_message())
            .await
            .expect("timed out waiting for message")
            .expect("receive error");
        assert_eq!(msg.kind, PubSubMessageKind::Message);
        assert_eq!(msg.channel.as_ref(), channel.as_bytes());
        assert_eq!(msg.payload.as_ref(), b"hello");
        assert!(msg.pattern.is_none());
    }
);

timed_tokio_test!(
    async fn pubsub_pattern_receive() {
        let srv = server_or_skip!();
        let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Pattern, "news.*");
        let subscriber = GlideClient::connect(
            GlideClientConfiguration::with_address("127.0.0.1", srv.port).subscriptions(subs),
        )
        .await
        .expect("connect subscriber");
        let publisher = srv.client().await;

        assert!(
            common::wait_for_numpat(&publisher, |n| n >= 1, Duration::from_secs(3)).await,
            "pattern subscription was not registered server-side in time"
        );
        let _: i64 = publisher.publish("news.tech", "breaking").await.unwrap();

        let msg = tokio::time::timeout(Duration::from_secs(3), subscriber.get_pubsub_message())
            .await
            .expect("timed out")
            .expect("receive error");
        assert_eq!(msg.kind, PubSubMessageKind::PMessage);
        assert_eq!(msg.channel.as_ref(), b"news.tech");
        assert_eq!(msg.payload.as_ref(), b"breaking");
        assert_eq!(msg.pattern.as_deref(), Some(&b"news.*"[..]));
    }
);

timed_tokio_test!(
    async fn pubsub_try_get_empty_returns_none() {
        let srv = server_or_skip!();
        let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Exact, "quiet");
        let subscriber = GlideClient::connect(
            GlideClientConfiguration::with_address("127.0.0.1", srv.port).subscriptions(subs),
        )
        .await
        .expect("connect subscriber");
        // Nothing published yet → no message available.
        assert!(subscriber.try_get_pubsub_message().await.unwrap().is_none());
    }
);

timed_tokio_test!(
    async fn pubsub_without_subscriptions_errors() {
        let srv = server_or_skip!();
        let c = srv.client().await;
        // A client not configured with subscriptions cannot receive.
        assert!(c.get_pubsub_message().await.is_err());
        assert!(c.try_get_pubsub_message().await.is_err());
    }
);

// ---------------------------------------------------------------------------
// Cluster: cluster_scan + route_command
// ---------------------------------------------------------------------------

timed_tokio_test!(
    async fn cluster_scan_iterates_all_keys() {
        let cluster = cluster_or_skip!();
        let client = match cluster.client().await {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster client connect failed");
                return;
            }
        };

        // Insert a known set of keys (routed automatically across shards).
        let prefix = common::key("cscan");
        let mut expected = HashSet::new();
        for i in 0..50 {
            let k = format!("{prefix}:{i}");
            let _: () = client.set(&k, "v").await.unwrap();
            expected.insert(k.into_bytes());
        }

        // Iterate the whole keyspace via the cluster-scan cursor.
        let mut found: HashSet<Vec<u8>> = HashSet::new();
        let mut cursor = ClusterScanCursor::new();
        let mut guard = 0;
        loop {
            let (next, keys) =
                retry_transient!(client.cluster_scan(&cursor, None, Some(100), None)).unwrap();
            for k in keys {
                found.insert(k.to_vec());
            }
            cursor = next;
            guard += 1;
            if cursor.is_finished() || guard > 100 {
                break;
            }
        }
        assert!(cursor.is_finished(), "scan did not finish");
        // Every inserted key must have been observed.
        for k in &expected {
            assert!(found.contains(k), "cluster_scan missed a key");
        }
    }
);

timed_tokio_test!(
    async fn cluster_scan_with_match_pattern() {
        let cluster = cluster_or_skip!();
        let client = match cluster.client().await {
            Some(c) => c,
            None => return,
        };
        let uniq = common::key("m");
        let matching = format!("{uniq}:match:1");
        let _: () = client.set(&matching, "v").await.unwrap();
        let _: () = client.set(format!("{uniq}:other:1"), "v").await.unwrap();

        let pattern = format!("{uniq}:match:*");
        let mut found = Vec::new();
        let mut cursor = ClusterScanCursor::new();
        let mut guard = 0;
        loop {
            let (next, keys) = retry_transient!(client.cluster_scan(
                &cursor,
                Some(pattern.as_bytes()),
                Some(100),
                None
            ))
            .unwrap();
            found.extend(keys.into_iter().map(|k| k.to_vec()));
            cursor = next;
            guard += 1;
            if cursor.is_finished() || guard > 100 {
                break;
            }
        }
        assert!(found.iter().any(|k| k == matching.as_bytes()));
        assert!(found.iter().all(|k| !k.ends_with(b":other:1")));
    }
);

timed_tokio_test!(
    async fn route_command_ping_variants() {
        let cluster = cluster_or_skip!();
        let client = match cluster.client().await {
            Some(c) => c,
            None => return,
        };

        // PING to all primaries.
        let mut ping = Cmd::new();
        ping.arg("PING");
        let r = client
            .route_command(ping, Route::AllPrimaries)
            .await
            .unwrap();
        // Multi-node PING returns a per-node aggregation; just assert success shape.
        assert!(!matches!(r, redis::Value::Nil));

        // PING to a random node returns PONG.
        let mut ping2 = Cmd::new();
        ping2.arg("PING");
        let r2 = client
            .route_command(ping2, Route::RandomNode)
            .await
            .unwrap();
        assert_eq!(glide::value::to_string(r2).unwrap(), "PONG");

        // A key-routed SET then GET through the slot-key route.
        let k = common::key("route:k");
        let mut set = Cmd::new();
        set.arg("SET").arg(&k).arg("v");
        client
            .route_command(set, Route::slot_key(k.clone(), glide::SlotType::Primary))
            .await
            .unwrap();
        let got: Option<glide::Bytes> = client.get(&k).await.unwrap();
        assert_eq!(got.as_deref(), Some(&b"v"[..]));
    }
);
