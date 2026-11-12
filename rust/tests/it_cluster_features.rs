// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster-mode integration tests for the parity features (pipeline options,
//! routed FCALL, runtime pub/sub incl. sharded) against a real multi-primary
//! cluster.

mod common;

use glide::commands::pubsub::PubSubCommands;
use glide::{
    AsyncTypedCommands, CustomCommand, FromValkeyValue, GlideClusterClient,
    GlideClusterClientConfiguration, PipelineOptions, PubSubMessageKind, Route, ScriptingCommands,
    SingleNodeRoute, SortedSetCommands, pipe,
};
use redis::cluster_topology::get_slot;
use std::time::Duration;

/// One primary from `CLUSTER NODES`: its node id, port, and owned slot ranges.
#[derive(Debug)]
struct ClusterNode {
    id: String,
    port: u16,
    ranges: Vec<(u16, u16)>,
}

/// Read the live cluster topology with an unrouted `CLUSTER NODES` and parse
/// each primary's id, port, and owned slot ranges. The expected node id for a
/// routed batch comes from this snapshot, not from the route under test.
async fn cluster_nodes(c: &GlideClusterClient) -> Vec<ClusterNode> {
    let mut pipeline = pipe();
    pipeline.cmd("CLUSTER").arg("NODES");
    let results = c
        .exec(&pipeline, true, None, &PipelineOptions::new())
        .await
        .unwrap();
    let text = String::from_valkey_value(&results[0]).unwrap();
    let mut nodes = Vec::new();
    for line in text.lines() {
        let fields: Vec<&str> = line.split_whitespace().collect();
        if fields.len() < 9 {
            continue;
        }
        let addr = fields[1].split('@').next().unwrap_or("");
        let Some((_, port)) = addr.rsplit_once(':') else {
            continue;
        };
        let Ok(port) = port.parse::<u16>() else {
            continue;
        };
        let mut ranges = Vec::new();
        for token in &fields[8..] {
            if token.starts_with('[') {
                continue; // slot being imported/migrated, not owned
            }
            if let Some((start, end)) = token.split_once('-') {
                if let (Ok(start), Ok(end)) = (start.parse::<u16>(), end.parse::<u16>()) {
                    ranges.push((start, end));
                }
            } else if let Ok(slot) = token.parse::<u16>() {
                ranges.push((slot, slot));
            }
        }
        if ranges.is_empty() {
            continue; // replica: owns no slots
        }
        nodes.push(ClusterNode {
            id: fields[0].to_string(),
            port,
            ranges,
        });
    }
    nodes
}

/// The primary owning `slot`, from a topology snapshot.
fn slot_owner(nodes: &[ClusterNode], slot: u16) -> &ClusterNode {
    nodes
        .iter()
        .find(|n| n.ranges.iter().any(|(s, e)| slot >= *s && slot <= *e))
        .unwrap_or_else(|| panic!("no primary owns slot {slot}"))
}

timed_tokio_test!(
    async fn cluster_exec_with_options() {
        let cluster = common::ClusterHarness::start().await;
        let c = cluster.client().await;

        // Same-slot keys (hash tag) so the pipeline routes to one shard; options
        // carry a timeout + explicit (disabled) retry strategy.
        let k = common::tkey("cbo", "k");
        let mut pipeline = pipe();
        pipeline.set(&k, "1").incr(&k, 1i64).get(&k);

        let opts = PipelineOptions::new()
            .with_timeout(Duration::from_secs(5))
            .with_retry_server_error(true);
        let results = c.exec(&pipeline, true, None, &opts).await.unwrap();

        assert_eq!(results.len(), 3);
        // results[0] = SET reply (OK), results[1] = INCR reply (2), results[2] = GET reply ("2")
        assert_eq!(i64::from_valkey_value(&results[1]).unwrap(), 2);
        assert_eq!(String::from_valkey_value(&results[2]).unwrap(), "2");

        // Atomic transaction with options routed to the key's slot.
        let k2 = common::tkey("cbo", "tx");
        let mut tx = pipe();
        tx.atomic().set(&k2, "5").incr(&k2, 1i64);

        let res2 = c
            .exec(&tx, true, None, &PipelineOptions::new())
            .await
            .unwrap();

        // res2[0] = SET reply (OK), res2[1] = INCR reply (6)
        assert_eq!(i64::from_valkey_value(&res2[1]).unwrap(), 6);
    }
);

timed_tokio_test!(
    async fn cluster_exec_with_single_node_route() {
        let cluster = common::ClusterHarness::start().await;
        let c = cluster.client().await;
        let nodes = cluster_nodes(&c).await;
        assert!(
            nodes.len() >= 2,
            "expected at least two primaries, got {nodes:?}"
        );

        // `CLUSTER MYID` is keyless, so it stays on the routed node: the
        // returned id proves the route selected that node. Dropping the
        // route sends it to a random node instead.
        for node in nodes.iter().take(2) {
            let mut pipeline = pipe();
            pipeline.cmd("CLUSTER").arg("MYID");
            let results = c
                .exec(
                    &pipeline,
                    true,
                    Some(SingleNodeRoute::ByAddress {
                        host: "127.0.0.1".to_string(),
                        port: node.port,
                    }),
                    &PipelineOptions::new(),
                )
                .await
                .unwrap();
            assert_eq!(results.len(), 1);
            assert_eq!(
                String::from_valkey_value(&results[0]).unwrap(),
                node.id.as_str(),
                "MYID routed by address to port {} must be that node's id",
                node.port
            );
        }

        // Slot-id route: a slot taken from the second primary's ranges.
        let slot = nodes[1].ranges[0].0;
        let mut pipeline = pipe();
        pipeline.cmd("CLUSTER").arg("MYID");
        let results = c
            .exec(
                &pipeline,
                true,
                Some(SingleNodeRoute::SlotId {
                    slot_id: slot,
                    slot_type: glide::SlotType::Primary,
                }),
                &PipelineOptions::new(),
            )
            .await
            .unwrap();
        assert_eq!(
            String::from_valkey_value(&results[0]).unwrap(),
            nodes[1].id.as_str(),
            "MYID routed to slot {slot} must be its owner's id"
        );

        // Slot-key route: the key's slot owner.
        let k = common::tkey("csr", "k");
        let slot = get_slot(k.as_bytes());
        let owner = slot_owner(&nodes, slot);
        let mut pipeline = pipe();
        pipeline.cmd("CLUSTER").arg("MYID");
        let results = c
            .exec(
                &pipeline,
                true,
                Some(SingleNodeRoute::slot_key(
                    k.as_str(),
                    glide::SlotType::Primary,
                )),
                &PipelineOptions::new(),
            )
            .await
            .unwrap();
        assert_eq!(
            String::from_valkey_value(&results[0]).unwrap(),
            owner.id.as_str(),
            "MYID routed by the key's slot must be the slot owner's id"
        );

        // Slot-key routed batch on same-slot keys.
        let mut routed = pipe();
        routed.set(&k, "1").incr(&k, 1i64).get(&k);
        let results = c
            .exec(
                &routed,
                true,
                Some(SingleNodeRoute::slot_key(
                    k.as_str(),
                    glide::SlotType::Primary,
                )),
                &PipelineOptions::new(),
            )
            .await
            .unwrap();
        assert_eq!(results.len(), 3);
        assert_eq!(i64::from_valkey_value(&results[1]).unwrap(), 2);
        assert_eq!(String::from_valkey_value(&results[2]).unwrap(), "2");
    }
);

timed_tokio_test!(
    async fn cluster_exec_with_single_node_route_atomic() {
        let cluster = common::ClusterHarness::start().await;
        let c = cluster.client().await;
        let nodes = cluster_nodes(&c).await;
        assert!(
            nodes.len() >= 2,
            "expected at least two primaries, got {nodes:?}"
        );

        // An atomic batch is one unit, so a keyless `CLUSTER MYID` inside
        // it still reports the node the transaction ran on. This exercises
        // the `send_transaction` path; the non-atomic test above only
        // covers `send_pipeline`.
        for node in nodes.iter().take(2) {
            let mut tx = pipe();
            tx.atomic().cmd("CLUSTER").arg("MYID");
            let results = c
                .exec(
                    &tx,
                    true,
                    Some(SingleNodeRoute::ByAddress {
                        host: "127.0.0.1".to_string(),
                        port: node.port,
                    }),
                    &PipelineOptions::new(),
                )
                .await
                .unwrap();
            assert_eq!(results.len(), 1);
            assert_eq!(
                String::from_valkey_value(&results[0]).unwrap(),
                node.id.as_str(),
                "MYID in a transaction routed by address to port {} must be that node's id",
                node.port
            );
        }

        // A valid single-node route delivers an atomic batch of key commands.
        let k = common::tkey("csa", "k");
        let mut tx = pipe();
        tx.atomic().set(&k, "5").incr(&k, 1i64);
        let results = c
            .exec(
                &tx,
                true,
                Some(SingleNodeRoute::slot_key(
                    k.as_str(),
                    glide::SlotType::Primary,
                )),
                &PipelineOptions::new(),
            )
            .await
            .unwrap();
        assert_eq!(results.len(), 2);
        assert_eq!(i64::from_valkey_value(&results[1]).unwrap(), 6);
    }
);

timed_tokio_test!(
    async fn cluster_fcall_route() {
        let cluster = common::ClusterHarness::start().await;
        let client = cluster.client().await;

        skip_if_version_below!(client, 7, 0, 0);

        // Load the library on every primary so a routed FCALL resolves on any node.
        let lib = "#!lua name=glideclib\n\
               redis.register_function{function_name='gc_echo', \
               callback=function(keys, args) return args[1] end, flags={'no-writes'}}";
        client
            .custom_command_with_route(&["FUNCTION", "LOAD", "REPLACE", lib], Route::AllPrimaries)
            .await
            .expect("FUNCTION LOAD");

        // Routed to a single node -> scalar reply.
        let r = client
            .fcall_route("gc_echo", &[] as &[&str], &["hi"], Route::RandomNode)
            .await
            .unwrap();
        assert_eq!(String::from_owned_valkey_value(r).unwrap(), "hi");

        let r = client
            .fcall_ro_route("gc_echo", &[] as &[&str], &["ro"], Route::RandomNode)
            .await
            .unwrap();
        assert_eq!(String::from_owned_valkey_value(r).unwrap(), "ro");

        // Broadcast to all primaries -> one reply per node (map/array), all echo.
        let all = client
            .fcall_route("gc_echo", &[] as &[&str], &["x"], Route::AllPrimaries)
            .await
            .unwrap();
        let echoed = format!("{all:?}").matches("\"x\"").count();
        assert!(echoed >= 1, "expected per-node echo replies, got {all:?}");
    }
);

timed_tokio_test!(
    async fn cluster_runtime_subscribe_receive() {
        let cluster = common::ClusterHarness::start().await;
        let publisher = cluster.client().await;
        let subscriber = GlideClusterClient::connect(
            GlideClusterClientConfiguration::with_address("127.0.0.1", cluster.seed_port())
                .enable_pubsub(),
        )
        .await
        .expect("connect pubsub cluster client");

        let chan = common::key("c-chan");
        subscriber.subscribe(&[chan.as_str()]).await.unwrap();
        tokio::time::sleep(Duration::from_millis(300)).await;

        let n: usize = publisher.publish(&chan, "hello").await.unwrap();
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
        let cluster = common::ClusterHarness::start().await;
        let publisher = cluster.client().await;

        // Sharded pub/sub is Valkey 7.0+
        skip_if_version_below!(publisher, 7, 0, 0);

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
        let cluster = common::ClusterHarness::start().await;
        let client = cluster.client().await;

        // src + dst must share a slot in cluster mode (multi-key command).
        let src = common::tkey("czr", "src");
        let dst = common::tkey("czr", "dst");
        let _: usize = client
            .zadd_multiple(&src, &[(1.0, "a"), (2.0, "b"), (3.0, "c")])
            .await
            .unwrap();
        let n = client
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
        let card: usize = client.zcard(&dst).await.unwrap();
        assert_eq!(card, 2);
    }
);
