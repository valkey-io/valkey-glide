// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster-mode integration tests for the parity features (pipeline options,
//! routed FCALL, runtime pub/sub incl. sharded) against a real multi-primary
//! cluster. Each test SKIPs gracefully when a cluster cannot be formed.

mod common;

use glide::commands::pubsub::PubSubCommands;
use glide::{
    AsyncCommands, CustomCommand, GlideClusterClient, GlideClusterClientConfiguration,
    PipelineOptions, PubSubMessageKind, Route, ScriptingCommands, SortedSetCommands, pipe,
};
use std::time::Duration;

/// Connect a default cluster client, or SKIP.
macro_rules! cluster_client {
    ($cluster:expr) => {
        match $cluster.client().await {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster client connect failed");
                return;
            }
        }
    };
}

timed_tokio_test!(
    async fn cluster_exec_with_options() {
        let cluster = match common::ClusterHarness::start() {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster harness unavailable");
                return;
            }
        };
        let c = cluster_client!(cluster);

        // Same-slot keys (hash tag) so the pipeline routes to one shard; options
        // carry a timeout + explicit (disabled) retry strategy.
        let k = common::tkey("cbo", "k");
        let mut pipeline = pipe();
        pipeline.set(&k, "1").incr(&k, 1i64).get(&k);
        let opts = PipelineOptions::new()
            .with_timeout(Duration::from_secs(5))
            .with_retry_server_error(true);
        let results = c
            .execute_pipeline(&pipeline, true, None, &opts)
            .await
            .unwrap();
        assert_eq!(results.len(), 3);
        // results[0] = SET reply (OK), results[1] = INCR reply (2), results[2] = GET reply ("2")
        assert_eq!(glide::value::to_i64(results[1].clone()).unwrap(), 2);
        assert_eq!(glide::value::to_string(results[2].clone()).unwrap(), "2");

        // Atomic transaction with options routed to the key's slot.
        let k2 = common::tkey("cbo", "tx");
        let mut tx = pipe();
        tx.atomic().set(&k2, "5").incr(&k2, 1i64);
        let res2 = c
            .execute_pipeline(&tx, true, None, &PipelineOptions::new())
            .await
            .unwrap();
        // res2[0] = SET reply (OK), res2[1] = INCR reply (6)
        assert_eq!(glide::value::to_i64(res2[1].clone()).unwrap(), 6);
    }
);

timed_tokio_test!(
    async fn cluster_fcall_route() {
        let cluster = match common::ClusterHarness::start() {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster harness unavailable");
                return;
            }
        };
        let c = cluster_client!(cluster);

        // Load the library on every primary so a routed FCALL resolves on any node.
        let lib = "#!lua name=glideclib\n\
               redis.register_function{function_name='gc_echo', \
               callback=function(keys, args) return args[1] end, flags={'no-writes'}}";
        if let Err(e) = c
            .custom_command_with_route(&["FUNCTION", "LOAD", "REPLACE", lib], Route::AllPrimaries)
            .await
        {
            eprintln!("SKIP: FUNCTION unsupported: {e:?}");
            return;
        }

        // Routed to a single node -> scalar reply.
        let r = c
            .fcall_route("gc_echo", &[] as &[&str], &["hi"], Route::RandomNode)
            .await
            .unwrap();
        assert_eq!(glide::value::to_string(r).unwrap(), "hi");

        let r = c
            .fcall_ro_route("gc_echo", &[] as &[&str], &["ro"], Route::RandomNode)
            .await
            .unwrap();
        assert_eq!(glide::value::to_string(r).unwrap(), "ro");

        // Broadcast to all primaries -> one reply per node (map/array), all echo.
        let all = c
            .fcall_route("gc_echo", &[] as &[&str], &["x"], Route::AllPrimaries)
            .await
            .unwrap();
        let echoed = format!("{all:?}").matches("\"x\"").count();
        assert!(echoed >= 1, "expected per-node echo replies, got {all:?}");
    }
);

timed_tokio_test!(
    async fn cluster_runtime_subscribe_receive() {
        let cluster = match common::ClusterHarness::start() {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster harness unavailable");
                return;
            }
        };
        let publisher = cluster_client!(cluster);
        let subscriber = GlideClusterClient::connect(
            GlideClusterClientConfiguration::with_address("127.0.0.1", cluster.seed_port())
                .enable_pubsub(),
        )
        .await
        .expect("connect pubsub cluster client");

        let chan = common::key("c-chan");
        subscriber.subscribe(&[chan.as_str()]).await.unwrap();
        tokio::time::sleep(Duration::from_millis(300)).await;

        let n: i64 = publisher.publish(&chan, "hello").await.unwrap();
        assert!(n >= 1, "expected >=1 subscriber, got {n}");

        let msg = tokio::time::timeout(Duration::from_secs(3), subscriber.get_pubsub_message())
            .await
            .expect("timed out")
            .expect("receive error");
        assert_eq!(msg.kind, PubSubMessageKind::Message);
        assert_eq!(msg.payload.as_ref(), b"hello");
    }
);

timed_tokio_test!(
    async fn cluster_ssubscribe_sharded_receive() {
        let cluster = match common::ClusterHarness::start() {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster harness unavailable");
                return;
            }
        };
        let publisher = cluster_client!(cluster);
        let subscriber = GlideClusterClient::connect(
            GlideClusterClientConfiguration::with_address("127.0.0.1", cluster.seed_port())
                .enable_pubsub(),
        )
        .await
        .expect("connect pubsub cluster client");

        let chan = common::key("c-shard");
        subscriber.ssubscribe(&[chan.as_str()]).await.unwrap();
        tokio::time::sleep(Duration::from_millis(300)).await;

        let n = publisher.spublish(&chan, "shard-hello").await.unwrap();
        assert!(n >= 1, "expected >=1 shard subscriber, got {n}");

        let msg = tokio::time::timeout(Duration::from_secs(3), subscriber.get_pubsub_message())
            .await
            .expect("timed out")
            .expect("receive error");
        assert_eq!(msg.kind, PubSubMessageKind::SMessage);
        assert_eq!(msg.channel.as_ref(), chan.as_bytes());
        assert_eq!(msg.payload.as_ref(), b"shard-hello");
    }
);

timed_tokio_test!(
    async fn cluster_zrangestore_by_score_same_slot() {
        let cluster = match common::ClusterHarness::start() {
            Some(c) => c,
            None => {
                eprintln!("SKIP: cluster harness unavailable");
                return;
            }
        };
        let c = cluster_client!(cluster);
        // src + dst must share a slot in cluster mode (multi-key command).
        let src = common::tkey("czr", "src");
        let dst = common::tkey("czr", "dst");
        let _: i64 = c
            .zadd_multiple(&src, &[(1.0, "a"), (2.0, "b"), (3.0, "c")])
            .await
            .unwrap();
        let n = c
            .zrangestore_by_score(
                &dst,
                &src,
                glide::commands::sorted_set::ScoreBound::Inclusive(1.0),
                glide::commands::sorted_set::ScoreBound::Inclusive(2.0),
                false,
                None,
            )
            .await
            .unwrap();
        assert_eq!(n, 2);
        let card: i64 = c.zcard(&dst).await.unwrap();
        assert_eq!(card, 2);
    }
);
