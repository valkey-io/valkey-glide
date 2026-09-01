// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Integration tests for the blocking (`sync`) clients.
//!
//! These are plain `#[test]`s (NOT `#[tokio::test]`): the sync client drives the
//! shared process-wide runtime via `block_on`, which must not run inside another
//! Tokio runtime. Restores and extends the sync coverage that was lost when the
//! monolithic `integration.rs` was split.

mod common;

use glide::pipeline_options::PipelineOptions;
use glide::sync::{SyncGlideClient, SyncGlideClusterClient};
use glide::{CustomCommand, GlideClientConfiguration, GlideClusterClientConfiguration, Route};
// Bring the unified command traits into scope.
use glide::Commands;
// Bring async command traits into scope for the `run` combinator closures.
use glide::AsyncCommands;

fn sync_client(port: u16) -> SyncGlideClient {
    SyncGlideClient::connect(GlideClientConfiguration::with_address("127.0.0.1", port))
        .expect("connect sync client")
}

#[test]
fn sync_standalone_common_commands() {
    let srv = server_or_skip!();
    let c = sync_client(srv.port);
    let k = common::key("sync:str");

    let _: () = c.set(&k, "hello").unwrap();
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v.as_deref(), Some("hello"));
    let exists: i64 = c.exists(&k).unwrap();
    assert_eq!(exists, 1);
    assert_eq!(c.ping().unwrap(), "PONG");

    let ctr = common::key("sync:ctr");
    let v: i64 = c.incr(&ctr, 1i64).unwrap();
    assert_eq!(v, 1);
    let v: i64 = c.incr(&ctr, 1i64).unwrap();
    assert_eq!(v, 2);

    let set: bool = c.expire(&k, 100).unwrap();
    assert!(set);
    let ttl: i64 = c.ttl(&k).unwrap();
    assert!(ttl > 0);
    let deleted: i64 = c.del(&k).unwrap();
    assert_eq!(deleted, 1);
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v, None);
}

#[test]
fn sync_standalone_set_options() {
    let srv = server_or_skip!();
    let c = sync_client(srv.port);
    let k = common::key("sync:opt");

    let _: () = c.set(&k, "first").unwrap();
    // NX must not overwrite an existing key. Use redis::SetOptions.
    let opts = glide::redis::SetOptions::default()
        .conditional_set(glide::redis::ExistenceCheck::NX)
        .with_expiration(glide::redis::SetExpiry::EX(50));
    let _: () = c.set_options(&k, "second", opts).unwrap();
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v.as_deref(), Some("first"));
}

#[test]
fn sync_standalone_custom_command_and_pipeline() {
    let srv = server_or_skip!();
    let c = sync_client(srv.port);
    let k = common::key("sync:cc");

    c.custom_command(&["SET", &k, "42"]).unwrap();
    let v = c.custom_command(&["GET", &k]).unwrap();
    assert_eq!(glide::value::to_string(v).unwrap(), "42");

    // Atomic transaction via redis::Pipeline
    let bk = common::key("sync:batch");
    let mut pipe = redis::Pipeline::new();
    pipe.atomic();
    pipe.cmd("SET").arg(&bk).arg("10");
    pipe.cmd("INCRBY").arg(&bk).arg(1);
    pipe.cmd("INCRBY").arg(&bk).arg(1);
    pipe.cmd("GET").arg(&bk);
    let results = c
        .execute_pipeline(&pipe, true, &PipelineOptions::default())
        .unwrap();
    assert_eq!(results.len(), 4);
    assert_eq!(glide::value::to_i64(results[2].clone()).unwrap(), 12);
    assert_eq!(glide::value::to_string(results[3].clone()).unwrap(), "12");
}

#[test]
fn sync_standalone_run_full_async_surface() {
    let srv = server_or_skip!();
    let c = sync_client(srv.port);
    let h = common::key("sync:hash");
    let l = common::key("sync:list");
    let z = common::key("sync:zset");
    let s = common::key("sync:set");

    // The `run` combinator unlocks the entire async command surface from sync code.
    let (hlen, llen, zscore, scard): (i64, i64, Option<f64>, i64) = c.run(|client| {
        let (h, l, z, s) = (h.clone(), l.clone(), z.clone(), s.clone());
        async move {
            let _: () = client
                .hset_multiple(&h, &[("f1", "v1"), ("f2", "v2")])
                .await
                .unwrap();
            let _: i64 = client.rpush(&l, &["a", "b", "c"]).await.unwrap();
            let _: i64 = client.zadd(&z, "m1", 1.0f64).await.unwrap();
            let _: i64 = client.zadd(&z, "m2", 2.0f64).await.unwrap();
            let _: i64 = client.sadd(&s, &["x", "y", "z"]).await.unwrap();
            (
                client.hlen::<_, i64>(&h).await.unwrap(),
                client.llen::<_, i64>(&l).await.unwrap(),
                client.zscore::<_, _, Option<f64>>(&z, "m2").await.unwrap(),
                client.scard::<_, i64>(&s).await.unwrap(),
            )
        }
    });
    assert_eq!(hlen, 2);
    assert_eq!(llen, 3);
    assert_eq!(zscore, Some(2.0));
    assert_eq!(scard, 3);
}

#[test]
fn sync_cluster_commands() {
    let cluster = cluster_or_skip!();
    let client = SyncGlideClusterClient::connect(
        GlideClusterClientConfiguration::with_address("127.0.0.1", cluster.seed_port())
            .request_timeout(std::time::Duration::from_secs(5)),
    );
    let client = match client {
        Ok(c) => c,
        Err(_) => {
            eprintln!("SKIP: could not connect sync cluster client");
            return;
        }
    };

    assert_eq!(client.ping().unwrap(), "PONG");

    let k = common::key("sync:cluster:k");
    client.custom_command(&["SET", &k, "v"]).unwrap();
    let v = client.custom_command(&["GET", &k]).unwrap();
    assert_eq!(glide::value::to_string(v).unwrap(), "v");

    // Routed command to all primaries.
    client
        .custom_command_with_route(&["PING"], Route::AllPrimaries)
        .unwrap();

    // The run combinator against the cluster client.
    let got = client.run(|c| {
        let k = k.clone();
        async move { c.custom_command(&["GET", &k]).await.unwrap() }
    });
    assert_eq!(glide::value::to_string(got).unwrap(), "v");
}
