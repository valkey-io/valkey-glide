// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Integration tests for the blocking (`sync`) clients.

#![cfg(feature = "sync")]

mod common;

use glide::AsyncTypedCommands;
use glide::CustomCommand;
use glide::FromValkeyValue;
use glide::GlideClientConfiguration;
use glide::GlideClusterClientConfiguration;
use glide::IntegerReplyOrNoOp;
use glide::Route;
use glide::Script;
use glide::TypedCommands;
use glide::cmd;
use glide::pipeline_options::PipelineOptions;
use glide::sync::SyncGlideClient;
use glide::sync::SyncGlideClusterClient;
use std::collections::HashSet;

#[test]
fn sync_cmd_query() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let k = common::key("sync:cmd_query");
    let _: () = cmd("SET").arg(&k).arg(9).query(&c).unwrap();
    let v = cmd("GET").arg(&k).query::<i64>(&c).unwrap();
    assert_eq!(v, 9);
}

#[test]
fn sync_cmd_exec() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let k = common::key("sync:cmd_exec");
    cmd("SET").arg(&k).arg(9).exec(&c).unwrap();
    assert_eq!(c.get(&k).unwrap().as_deref(), Some("9"));
}

#[test]
fn sync_glide_send_command_as() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let k = common::key("sync:glide_send_command_as");
    let mut set = cmd("SET");
    set.arg(&k).arg(9);
    let _: () = glide::Commands::glide_send_command_as(&c, set).unwrap();
    let mut get = cmd("GET");
    get.arg(&k);
    let v: i64 = glide::Commands::glide_send_command_as(&c, get).unwrap();
    assert_eq!(v, 9);
}

#[test]
fn sync_standalone_common_commands() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let k = common::key("sync:str");

    let _: () = c.set(&k, "hello").unwrap();
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v.as_deref(), Some("hello"));
    let exists: bool = c.exists(&k).unwrap();
    assert!(exists);
    assert_eq!(c.ping().unwrap(), "PONG");

    let ctr = common::key("sync:ctr");
    let v: isize = c.incr(&ctr, 1i64).unwrap();
    assert_eq!(v, 1);
    let v: isize = c.incr(&ctr, 1i64).unwrap();
    assert_eq!(v, 2);

    let set: bool = c.expire(&k, 100).unwrap();
    assert!(set);
    let ttl: IntegerReplyOrNoOp = c.ttl(&k).unwrap();
    assert!(matches!(ttl, IntegerReplyOrNoOp::IntegerReply(1..)));
    let deleted: usize = c.del(&k).unwrap();
    assert_eq!(deleted, 1);
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v, None);
}

#[test]
fn sync_standalone_set_options() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let k = common::key("sync:opt");

    let _: () = c.set(&k, "first").unwrap();
    // NX must not overwrite an existing key. Use SetOptions.
    let opts = glide::SetOptions::default()
        .conditional_set(glide::ExistenceCheck::NX)
        .with_expiration(glide::SetExpiry::EX(50));
    let _: Option<String> = c.set_options(&k, "second", opts).unwrap();
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v.as_deref(), Some("first"));
}

#[test]
fn sync_standalone_custom_command_and_pipeline() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let k = common::key("sync:cc");

    c.custom_command(&["SET", &k, "42"]).unwrap();
    let v = c.custom_command(&["GET", &k]).unwrap();
    assert_eq!(String::from_owned_valkey_value(v).unwrap(), "42");

    // Atomic transaction via pipeline
    let bk = common::key("sync:batch");

    let mut pipe = glide::pipe();
    pipe.atomic()
        .cmd("SET")
        .arg(&bk)
        .arg("10")
        .cmd("INCRBY")
        .arg(&bk)
        .arg(1)
        .cmd("INCRBY")
        .arg(&bk)
        .arg(1)
        .cmd("GET")
        .arg(&bk);

    let results = c.exec(&pipe, true, &PipelineOptions::default()).unwrap();

    assert_eq!(results.len(), 4);
    assert_eq!(i64::from_valkey_value(&results[2]).unwrap(), 12);
    assert_eq!(String::from_valkey_value(&results[3]).unwrap(), "12");
}

#[test]
fn sync_standalone_run_full_async_surface() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let h = common::key("sync:hash");
    let l = common::key("sync:list");
    let z = common::key("sync:zset");
    let s = common::key("sync:set");

    // The `run` combinator unlocks the entire async command surface from sync code.
    let (hlen, llen, zscore, scard): (usize, usize, Option<f64>, usize) = c.run(|client| {
        let (h, l, z, s) = (h.clone(), l.clone(), z.clone(), s.clone());
        async move {
            let _: usize = client
                .hset_multiple(&h, &[("f1", "v1"), ("f2", "v2")])
                .await
                .unwrap();
            let _: usize = client.rpush(&l, &["a", "b", "c"]).await.unwrap();
            let _: usize = client.zadd(&z, "m1", 1.0f64).await.unwrap();
            let _: usize = client.zadd(&z, "m2", 2.0f64).await.unwrap();
            let _: usize = client.sadd(&s, &["x", "y", "z"]).await.unwrap();
            (
                client.hlen(&h).await.unwrap(),
                client.llen(&l).await.unwrap(),
                client.zscore(&z, "m2").await.unwrap(),
                client.scard(&s).await.unwrap(),
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
    let cluster = common::ClusterHarness::start_blocking();
    let config = GlideClusterClientConfiguration::with_address("127.0.0.1", cluster.seed_port())
        .request_timeout(std::time::Duration::from_secs(5));
    let client = SyncGlideClusterClient::connect(config).expect("connect sync cluster client");

    assert_eq!(client.ping().unwrap(), "PONG");

    let k = common::key("sync:cluster:k");
    client.custom_command(&["SET", &k, "v"]).unwrap();
    let v = client.custom_command(&["GET", &k]).unwrap();
    assert_eq!(String::from_owned_valkey_value(v).unwrap(), "v");

    // Routed command to all primaries.
    client
        .custom_command_with_route(&["PING"], Route::AllPrimaries)
        .unwrap();

    // The run combinator against the cluster client.
    let got = client.run(|c| {
        let k = k.clone();
        async move { c.custom_command(&["GET", &k]).await.unwrap() }
    });
    assert_eq!(String::from_owned_valkey_value(got).unwrap(), "v");
}

#[test]
fn sync_standalone_scan() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let prefix = common::key("sync:scan");
    for i in 0..20 {
        let _: () = c.set(format!("{prefix}:keep:{i}"), "v").unwrap();
        let _: () = c.set(format!("{prefix}:skip:{i}"), "v").unwrap();
    }

    let kept: HashSet<String> = c
        .scan_match(format!("{prefix}:keep:*"))
        .unwrap()
        .map(Result::unwrap)
        .collect();
    assert_eq!(kept.len(), 20);
    assert!(kept.iter().all(|k| k.contains(":keep:")));

    let h = common::key("sync:hscan");
    let _: usize = c.hset(&h, "f1", "v1").unwrap();
    let _: usize = c.hset(&h, "f2", "v2").unwrap();
    let fields: HashSet<(String, String)> = c.hscan(&h).unwrap().map(Result::unwrap).collect();
    assert_eq!(
        fields,
        HashSet::from([
            ("f1".to_string(), "v1".to_string()),
            ("f2".to_string(), "v2".to_string())
        ])
    );
}

fn sync_client(port: u16) -> SyncGlideClient {
    SyncGlideClient::connect(GlideClientConfiguration::with_address("127.0.0.1", port))
        .expect("connect sync client")
}

#[test]
fn sync_pipeline_and_transaction() {
    use glide::sync::PipelineExt;
    let server = common::TestServer::start();
    let c = sync_client(server.port);

    let k1 = common::tkey("cmd_sp", "k1");
    let k2 = common::tkey("cmd_sp", "k2");
    let (v1, v2) = glide::pipe()
        .set(&k1, "x")
        .ignore()
        .set(&k2, 9)
        .ignore()
        .get(&k1)
        .get(&k2)
        .query::<(String, i64)>(&c)
        .unwrap();
    assert_eq!((v1.as_str(), v2), ("x", 9));

    let k3 = common::tkey("cmd_sp", "k3");
    glide::pipe().incr(&k3, 1).incr(&k3, 1).exec(&c).unwrap();
    assert_eq!(c.get(&k3).unwrap().as_deref(), Some("2"));

    let ctr = common::tkey("cmd_sp", "ctr");
    let (a, b): (i64, i64) = glide::pipe()
        .atomic()
        .incr(&ctr, 1)
        .incr(&ctr, 1)
        .query(&c)
        .unwrap();
    assert_eq!((a, b), (1, 2));

    // Native-copy path: PipelineExt::query (borrows &client, sends the
    // built Pipeline directly — no packed-byte round-trip) must honor
    // .ignore() handling and atomic transactions.
    let k3 = common::tkey("cmd_sp", "k3");
    let (v3, cnt): (String, i64) = glide::pipe()
        .set(&k3, "y")
        .ignore()
        .get(&k3)
        .incr(&ctr, 5)
        .query(&c)
        .unwrap();
    assert_eq!((v3.as_str(), cnt), ("y", 7));

    let ctr2 = common::tkey("cmd_sp", "ctr2");
    let (x, y): (i64, i64) = glide::pipe()
        .atomic()
        .incr(&ctr2, 3)
        .incr(&ctr2, 4)
        .query(&c)
        .unwrap();
    assert_eq!((x, y), (3, 7));
}

#[test]
fn sync_pipeline_with_literal_multi_exec_is_not_atomic() {
    // A plain (non-atomic) pipeline containing literal MULTI/EXEC commands —
    // manual transaction management, a real migration pattern. This must NOT
    // be collapsed into a glide-core transaction (only `.atomic()` is): each
    // command gets its own reply.
    use glide::sync::PipelineExt;
    let server = common::TestServer::start();
    let c = sync_client(server.port);

    let ctr = common::tkey("cmd_literal_tx", "ctr");
    let (multi_ok, queued, exec_replies): (String, String, Vec<i64>) = glide::pipe()
        .cmd("MULTI")
        .cmd("INCR")
        .arg(&ctr)
        .cmd("EXEC")
        .query(&c)
        .unwrap();
    assert_eq!(multi_ok, "OK");
    assert_eq!(queued, "QUEUED");
    assert_eq!(exec_replies, vec![1]);
}

#[test]
fn sync_script_invoke_and_load() {
    // Blocking Script API: invoke() + load() on the sync client.
    let server = common::TestServer::start();
    let c = sync_client(server.port);

    let script = Script::new("return redis.call('SET', KEYS[1], ARGV[1])");
    let k = common::key("cmd_sync_script");
    let _: () = script.key(&k).arg("stored-sync").invoke(&c).unwrap();
    let v: Option<String> = c.get(&k).unwrap();
    assert_eq!(v.as_deref(), Some("stored-sync"));

    // Typed return through the sync path.
    let sum_script = Script::new("return tonumber(ARGV[1]) + tonumber(ARGV[2])");
    let sum: i64 = sum_script.arg(20).arg(22).invoke(&c).unwrap();
    assert_eq!(sum, 42);

    // load() returns the script's SHA-1 and populates the server cache.
    let hash = sum_script.load(&c).unwrap();
    assert_eq!(hash, sum_script.get_hash());
}

#[test]
fn sync_cluster_commands_trait() {
    let cluster = common::ClusterHarness::start_blocking();
    let config = GlideClusterClientConfiguration::with_address("127.0.0.1", cluster.seed_port());
    let client = SyncGlideClusterClient::connect(config).expect("connect sync cluster client");

    // Blocking typed API on the cluster client.
    let k = format!("cmd_sync_cluster:{}", common::key("k"));
    client.set(&k, 123).unwrap();
    let v: Option<String> = client.get(&k).unwrap();
    assert_eq!(v.as_deref(), Some("123"));
    let v: isize = client.incr(&k, 7).unwrap();
    assert_eq!(v, 130);
}

// ---- generic command-trait bounds --------------------------------------------------------------

// A bound on one command trait brings only that trait's methods into scope, so
// calls on the type parameter are not ambiguous. Matches redis-rs's traits.

#[test]
fn sync_generic_command_trait_bounds() {
    let server = common::TestServer::start();
    let c = sync_client(server.port);
    let typed = common::key("sync:typed_bound");
    assert_eq!(typed_bound(&c, &typed).unwrap(), vec![typed]);
    let untyped = common::key("sync:untyped_bound");
    assert_eq!(untyped_bound(&c, &untyped).unwrap(), vec![untyped]);
}

fn typed_bound<C: TypedCommands>(c: &C, key: &str) -> glide::ValkeyResult<Vec<String>> {
    c.set(key, "v")?;
    assert_eq!(c.get(key)?.as_deref(), Some("v"));
    c.scan_match(key)?.collect()
}

fn untyped_bound<C: glide::Commands>(c: &C, key: &str) -> glide::ValkeyResult<Vec<String>> {
    let _: () = c.set(key, "v")?;
    let value: Option<String> = c.get(key)?;
    assert_eq!(value.as_deref(), Some("v"));
    c.scan_match(key)?.collect()
}
