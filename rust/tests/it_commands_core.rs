// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Live tests for GLIDE's command API (`glide::AsyncCommands`): typed
//! commands, `Pipeline` (plain and atomic), scan iterators, and the error
//! surface, exercised end-to-end against a live server on RESP2 and RESP3 —
//! including commands whose replies glide-core normalizes (maps, sets,
//! doubles, booleans), to prove typed decoding (`FromRedisValue`) behaves as
//! migrated call sites expect.

mod common;

use glide::{AsyncCommands, PipelineExt, RedisResult, pipe};
use std::collections::{HashMap, HashSet};

// ---- typed AsyncCommands methods -----------------------------------------------

matrix_test!(set_get_typed, c, {
    let c = c;
    let k = common::key("rrs");
    c.set::<_, _, ()>(&k, 42).await.unwrap();
    let as_int: i64 = c.get(&k).await.unwrap();
    assert_eq!(as_int, 42);
    let as_string: String = c.get(&k).await.unwrap();
    assert_eq!(as_string, "42");
});

matrix_test!(get_missing_option_none, c, {
    let c = c;
    let v: Option<String> = c.get(common::key("cmd_missing")).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(incr_decr_typed, c, {
    let c = c;
    let k = common::key("cmd_ctr");
    let v: i64 = c.incr(&k, 5).await.unwrap();
    assert_eq!(v, 5);
    let v: i64 = c.decr(&k, 2).await.unwrap();
    assert_eq!(v, 3);
});

matrix_test!(migrated_method_names_work, c, {
    // Methods whose table names differ from the old native trait names.
    let c = c;
    let k = common::key("cmd_names");
    c.set_ex::<_, _, ()>(&k, "v", 100).await.unwrap();
    let ttl: i64 = c.ttl(&k).await.unwrap();
    assert!(ttl > 0 && ttl <= 100);
    let old: String = c.getset(&k, "new").await.unwrap();
    assert_eq!(old, "v");
    let deleted: String = c.get_del(&k).await.unwrap();
    assert_eq!(deleted, "new");
    let exists: bool = c.exists(&k).await.unwrap();
    assert!(!exists);
});

matrix_test!(deprecated_commands_still_work, c, {
    // The fork keeps deprecated commands (HMSET, RPOPLPUSH); same-slot keys.
    let c = c;
    let src = common::tkey("cmd_dep", "src");
    let dst = common::tkey("cmd_dep", "dst");
    c.rpush::<_, _, ()>(&src, &["a", "b"]).await.unwrap();
    let moved: String = c.rpoplpush(&src, &dst).await.unwrap();
    assert_eq!(moved, "b");
});

// ---- normalized-value decoding (the Phase-0 behavioral question) -------------

matrix_test!(hgetall_decodes_to_hashmap, c, {
    // glide-core normalizes HGETALL to a map on both RESP2 and RESP3;
    // HashMap decoding must accept it.
    let c = c;
    let k = common::key("cmd_hash");
    c.hset_multiple::<_, _, _, ()>(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    let all: HashMap<String, String> = c.hgetall(&k).await.unwrap();
    assert_eq!(all.len(), 2);
    assert_eq!(all["f1"], "v1");
    assert_eq!(all["f2"], "v2");
});

matrix_test!(bool_normalization_decodes, c, {
    let c = c;
    let k = common::key("cmd_set");
    c.sadd::<_, _, ()>(&k, "member").await.unwrap();
    let yes: bool = c.sismember(&k, "member").await.unwrap();
    let no: bool = c.sismember(&k, "nope").await.unwrap();
    assert!(yes);
    assert!(!no);
    let expired: bool = c.expire(&k, 1000).await.unwrap();
    assert!(expired);
});

matrix_test!(smembers_decodes_to_hashset, c, {
    let c = c;
    let k = common::key("cmd_sm");
    c.sadd::<_, _, ()>(&k, &["a", "b", "c"]).await.unwrap();
    let members: HashSet<String> = c.smembers(&k).await.unwrap();
    assert_eq!(
        members,
        HashSet::from(["a".to_string(), "b".to_string(), "c".to_string()])
    );
});

matrix_test!(zset_double_normalization_decodes, c, {
    let c = c;
    let k = common::key("cmd_z");
    let added: i64 = c
        .zadd_multiple(&k, &[(1.5, "one"), (2.5, "two")])
        .await
        .unwrap();
    assert_eq!(added, 2);
    let score: f64 = c.zscore(&k, "one").await.unwrap();
    assert_eq!(score, 1.5);
    // Increment by 0.4 (not 1.0): a 2.5/2.5 tie would make ZPOPMIN pop "one"
    // (lexicographic tiebreak), which is not what this test wants to observe.
    let incremented: f64 = c.zincr(&k, "one", 0.4).await.unwrap();
    assert_eq!(incremented, 1.9);
    // ZPOPMIN reply is normalized; decodes as member/score pairs.
    let popped: Vec<(String, f64)> = c.zpopmin(&k, 1).await.unwrap();
    assert_eq!(popped, vec![("one".to_string(), 1.9)]);
});

matrix_test!(zrange_withscores_decodes, c, {
    let c = c;
    let k = common::key("cmd_zr");
    c.zadd_multiple::<_, _, _, ()>(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let pairs: Vec<(String, f64)> = c.zrange_withscores(&k, 0, -1).await.unwrap();
    assert_eq!(pairs, vec![("a".to_string(), 1.0), ("b".to_string(), 2.0)]);
});

// ---- pipelines & transactions ------------------------------------------------

matrix_test!(pipeline_query_glide, c, {
    let c = c;
    let k1 = common::tkey("cmd_pipe", "k1");
    let k2 = common::tkey("cmd_pipe", "k2");
    let (v1, v2): (String, i64) = pipe()
        .set(&k1, "hello")
        .ignore()
        .set(&k2, 7)
        .ignore()
        .get(&k1)
        .get(&k2)
        .query_glide(&c)
        .await
        .unwrap();
    assert_eq!(v1, "hello");
    assert_eq!(v2, 7);
});

matrix_test!(atomic_transaction_query_glide, c, {
    let c = c;
    let k = common::tkey("cmd_tx", "ctr");
    let (a, b): (i64, i64) = pipe()
        .atomic()
        .incr(&k, 1)
        .incr(&k, 1)
        .query_glide(&c)
        .await
        .unwrap();
    assert_eq!((a, b), (1, 2));
});

// ---- error surface -----------------------------------------------------------

matrix_test!(wrong_type_returns_redis_error, c, {
    let c = c;
    let k = common::key("cmd_err");
    c.set::<_, _, ()>(&k, "text").await.unwrap();
    let res: RedisResult<Vec<String>> = c.lrange(&k, 0, -1).await;
    let err = res.unwrap_err();
    // Exact fork semantics: the vendored fork (unlike upstream releases)
    // does not map WRONGTYPE to ErrorKind::TypeError — server WRONGTYPE
    // surfaces as ExtensionError with code() == "WRONGTYPE". Our compat path
    // must be fork-faithful; assert both kind and code.
    assert_eq!(err.kind(), glide::ErrorKind::ExtensionError, "got: {err}");
    assert_eq!(err.code(), Some("WRONGTYPE"), "got: {err}");
});

matrix_test!(error_inside_pipeline_surfaces_as_err, c, {
    // A mid-pipeline server error must surface as Err (glide-core's
    // raise_on_error path ≙ the fork's `make_pipeline_results` extraction).
    let c = c;
    let k = common::tkey("cmd_pipe_err", "k");
    c.set::<_, _, ()>(&k, "text").await.unwrap();
    let res: RedisResult<(String, Vec<String>, String)> = pipe()
        .get(&k)
        .lrange(&k, 0, -1) // WRONGTYPE in the middle
        .get(&k)
        .query_glide(&c)
        .await;
    let err = res.unwrap_err();
    assert_eq!(err.code(), Some("WRONGTYPE"), "got: {err}");
});

matrix_test!(error_inside_transaction_surfaces_as_err, c, {
    // Same for an atomic transaction: EXEC's per-command error must become Err.
    let c = c;
    let k = common::tkey("cmd_tx_err", "k");
    c.set::<_, _, ()>(&k, "text").await.unwrap();
    let res: RedisResult<(String, Vec<String>)> = pipe()
        .atomic()
        .get(&k)
        .lrange(&k, 0, -1) // WRONGTYPE inside MULTI/EXEC
        .query_glide(&c)
        .await;
    let err = res.unwrap_err();
    assert_eq!(err.code(), Some("WRONGTYPE"), "got: {err}");
});

// ---- scan iterators (standalone only: cursor iteration is per-node) -----------

resp_test!(scan_match_iterator, c, {
    let c = c;
    let prefix = common::key("cmd_scan");
    for i in 0..10 {
        c.set::<_, _, ()>(format!("{prefix}:{i}"), i).await.unwrap();
    }
    let mut found: Vec<String> = Vec::new();
    {
        let mut iter = c.scan_match(format!("{prefix}:*")).await.unwrap();
        while let Some(k) = iter.next_item().await {
            found.push(k);
        }
    }
    assert_eq!(found.len(), 10);
});
