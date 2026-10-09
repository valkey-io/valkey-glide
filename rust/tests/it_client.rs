// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Client-level integration tests that exercise client construction and the
//! parts of `client.rs` not covered by the per-family command suites: the
//! cluster-scan iterator, `route_command`, the Pub/Sub
//! subscribe→publish→receive path, and connecting from a URL.

// TODO #7236: this file groups unrelated client-level tests (Pub/Sub, cluster
// scan, `route_command`, connection URLs), some overlapping the per-feature
// suites (`it_pubsub.rs`, `it_scan.rs`). Split or move them into specific files.

mod common;

use glide::client::{ClusterScanCursor, PubSubMessageKind};
use glide::cmd;
use glide::config::{PubSubChannelMode, PubSubSubscriptions};
use glide::{
    AsyncTypedCommands, CustomCommand, FromValkeyValue, GlideClient, GlideClientConfiguration,
    GlideClusterClientConfiguration, Route,
};
use std::collections::HashSet;
use std::time::Duration;

// ---------------------------------------------------------------------------
// Pub/Sub (standalone)
// ---------------------------------------------------------------------------

timed_tokio_test!(
    async fn pubsub_publish_receive_exact() {
        let server = common::TestServer::start();
        let channel = common::key("chan");

        let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Exact, channel.clone());
        let subscriber = GlideClient::connect(
            GlideClientConfiguration::with_address("127.0.0.1", server.port).subscriptions(subs),
        )
        .await
        .expect("connect subscriber");
        let publisher = server.client().await;

        // Wait until the subscription is registered server-side (poll, not sleep).
        assert!(
            common::wait_for_numsub(&publisher, &channel, |n| n >= 1, Duration::from_secs(3)).await,
            "subscription was not registered server-side in time"
        );
        let n: usize = publisher.publish(&channel, "hello").await.unwrap();
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
        let server = common::TestServer::start();
        let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Pattern, "news.*");
        let subscriber = GlideClient::connect(
            GlideClientConfiguration::with_address("127.0.0.1", server.port).subscriptions(subs),
        )
        .await
        .expect("connect subscriber");
        let publisher = server.client().await;

        assert!(
            common::wait_for_numpat(&publisher, |n| n >= 1, Duration::from_secs(3)).await,
            "pattern subscription was not registered server-side in time"
        );
        let _: usize = publisher.publish("news.tech", "breaking").await.unwrap();

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
        let server = common::TestServer::start();
        let subs = PubSubSubscriptions::new().subscribe(PubSubChannelMode::Exact, "quiet");
        let subscriber = GlideClient::connect(
            GlideClientConfiguration::with_address("127.0.0.1", server.port).subscriptions(subs),
        )
        .await
        .expect("connect subscriber");
        // Nothing published yet → no message available.
        assert!(subscriber.try_get_pubsub_message().await.unwrap().is_none());
    }
);

timed_tokio_test!(
    async fn pubsub_without_subscriptions_errors() {
        let server = common::TestServer::start();
        let client = server.client().await;
        // A client not configured with subscriptions cannot receive.
        assert!(client.get_pubsub_message().await.is_err());
        assert!(client.try_get_pubsub_message().await.is_err());
    }
);

// ---------------------------------------------------------------------------
// Client library name and version
// ---------------------------------------------------------------------------

timed_tokio_test!(
    async fn client_info_reports_lib_name_and_ver() {
        let server = common::TestServer::start();
        let client = server.client().await;

        skip_if_version_below!(client, 7, 2, 0);

        let reply = client.custom_command(&["CLIENT", "INFO"]).await.unwrap();
        let info = String::from_owned_valkey_value(reply).unwrap();

        let expected_lib_name = format!("lib-name={}", "GlideRust");
        assert!(info.contains(&expected_lib_name));

        let expected_lib_ver = format!("lib-ver={}", env!("CARGO_PKG_VERSION"));
        assert!(info.contains(&expected_lib_ver));
    }
);

timed_tokio_test!(
    async fn cluster_client_info_reports_lib_name_and_ver() {
        let cluster = common::ClusterHarness::start().await;
        let client = cluster.client().await;

        skip_if_version_below!(client, 7, 2, 0);

        let reply = client
            .custom_command_with_route(&["CLIENT", "INFO"], Route::RandomNode)
            .await
            .unwrap();
        let info = String::from_owned_valkey_value(reply).unwrap();

        let expected_lib_name = format!("lib-name={}", "GlideRust");
        assert!(info.contains(&expected_lib_name));

        let expected_lib_ver = format!("lib-ver={}", env!("CARGO_PKG_VERSION"));
        assert!(info.contains(&expected_lib_ver));
    }
);

// ---------------------------------------------------------------------------
// Cluster: cluster_scan + route_command
// ---------------------------------------------------------------------------

timed_tokio_test!(
    async fn cluster_scan_iterates_all_keys() {
        let cluster = common::ClusterHarness::start().await;
        let client = cluster.client().await;

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
        let mut ids: Vec<String> = Vec::new();

        let mut cursor = ClusterScanCursor::new();
        loop {
            let (next, keys) =
                retry_transient!(client.cluster_scan(&cursor, None, Some(10), None)).unwrap();

            for k in keys {
                found.insert(k.to_vec());
            }

            cursor = next;

            if !cursor.is_finished() {
                ids.push(cursor.id().to_owned());
            } else {
                break;
            }
        }

        // Verify that all inserted keys were found.
        for key in &expected {
            assert!(found.contains(key), "cluster_scan missed a key");
        }

        // Verify that all intermediate cursors were cleaned up.
        for id in ids {
            assert!(
                glide_core::cluster_scan_container::get_cluster_scan_cursor(id.clone()).is_err()
            );
        }
    }
);

timed_tokio_test!(
    async fn cluster_scan_with_match_pattern() {
        let cluster = common::ClusterHarness::start().await;
        let client = cluster.client().await;

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
        let cluster = common::ClusterHarness::start().await;
        let client = cluster.client().await;

        // ECHO to all primaries returns reply per primary node.
        let msg = "glide-route-probe";
        let echo = cmd("ECHO").arg(msg).clone();
        let r = client
            .route_command(echo, Route::AllPrimaries)
            .await
            .unwrap();

        let echoed = match &r {
            glide::ValkeyValue::Map(pairs) => pairs
                .iter()
                .filter(|(_, v)| String::from_valkey_value(v).ok().as_deref() == Some(msg))
                .count(),
            _ => 0,
        };
        assert_eq!(
            echoed,
            cluster.primary_ports.len(),
            "expected one echo reply per primary, got {r:?}"
        );

        // PING to a random node returns PONG.
        let ping = cmd("PING");
        let r2 = client.route_command(ping, Route::RandomNode).await.unwrap();
        assert_eq!(String::from_owned_valkey_value(r2).unwrap(), "PONG");

        // A key-routed SET then GET through the slot-key route.
        let k = common::key("route:k");
        let set = cmd("SET").arg(&k).arg("v").clone();
        client
            .route_command(set, Route::slot_key(k.clone(), glide::SlotType::Primary))
            .await
            .unwrap();
        let got: Option<String> = client.get(&k).await.unwrap();
        assert_eq!(got.as_deref(), Some("v"));
    }
);

// ---------------------------------------------------------------------------
// Connection URLs
// ---------------------------------------------------------------------------

timed_tokio_test!(
    async fn from_url_connects_and_selects_db() {
        let server = common::TestServer::start();
        let url = format!("redis://127.0.0.1:{}/1", server.port);
        let cfg = GlideClientConfiguration::from_url(&url).unwrap();
        assert_eq!(cfg.database_id, 1);
        let c1 = GlideClient::connect(cfg).await.unwrap();

        let k = common::key("cmd_url_db");
        c1.set(&k, "in-db-1").await.unwrap();

        // A db-0 client must not see the key; a second db-1 client must.
        let c0 = GlideClient::connect(
            GlideClientConfiguration::from_url(format!("redis://127.0.0.1:{}", server.port))
                .unwrap(),
        )
        .await
        .unwrap();
        let miss: Option<String> = c0.get(&k).await.unwrap();
        assert_eq!(miss, None);
        let hit: Option<String> = c1.get(&k).await.unwrap();
        assert_eq!(hit.as_deref(), Some("in-db-1"));
    }
);

timed_tokio_test!(
    async fn cluster_from_urls_connects_and_routes() {
        let cluster = common::ClusterHarness::start().await;
        let urls: Vec<String> = cluster
            .primary_ports
            .iter()
            .map(|p| format!("redis://127.0.0.1:{p}"))
            .collect();
        let cfg =
            GlideClusterClientConfiguration::from_urls(urls.iter().map(String::as_str)).unwrap();
        assert_eq!(cfg.addresses.len(), cluster.primary_ports.len());

        let client: glide::GlideClusterClient = glide::GlideClusterClient::connect(cfg)
            .await
            .expect("connect cluster client");

        // Keys hash to different slots; each is routed to its owning node.
        for i in 0..20 {
            let k = format!("cmd_cluster_url:{i}");
            client.set(&k, i).await.unwrap();
            let v: Option<String> = client.get(&k).await.unwrap();
            assert_eq!(v, Some(i.to_string()));
        }
    }
);
