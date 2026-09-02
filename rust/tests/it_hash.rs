// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command hash integration tests (RESP2 + RESP3).

mod common;

use glide::AsyncCommands;
use glide::HashCommands;
use glide::commands::options::ExpireOptions; // surviving native extensions: hmget, hstrlen, hrandfield*, hexpire/httl/etc.

matrix_test!(hset_hget, c, {
    let k = common::key("h");
    // HSET with multiple fields returns the count of NEW fields added.
    // compat hset_multiple uses HMSET (returns OK), so use glide_send_owned for the count.
    let mut cmd = redis::Cmd::new();
    cmd.arg("HSET")
        .arg(&k)
        .arg("f1")
        .arg("v1")
        .arg("f2")
        .arg("v2");
    let n: i64 = redis::from_owned_redis_value(c.glide_send_owned(cmd).await.unwrap()).unwrap();
    assert_eq!(n, 2);
    let v: Option<String> = c.hget(&k, "f1").await.unwrap();
    assert_eq!(v.as_deref(), Some("v1"));
});

matrix_test!(hget_missing_field, c, {
    let k = common::key("h");
    let _: () = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    let v: Option<String> = c.hget(&k, "nope").await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(hget_missing_key, c, {
    let v: Option<String> = c.hget(common::key("h"), "f").await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(hset_updates_existing_returns_zero, c, {
    let k = common::key("h");
    let _: () = c.hset_multiple(&k, &[("f", "v1")]).await.unwrap();
    // Updating an existing field returns 0 new fields via HSET.
    let mut cmd = redis::Cmd::new();
    cmd.arg("HSET").arg(&k).arg("f").arg("v2");
    let n: i64 = redis::from_owned_redis_value(c.glide_send_owned(cmd).await.unwrap()).unwrap();
    assert_eq!(n, 0);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v2"));
});

matrix_test!(hsetnx, c, {
    let k = common::key("h");
    let ok: bool = c.hset_nx(&k, "f", "v1").await.unwrap();
    assert!(ok);
    let ok: bool = c.hset_nx(&k, "f", "v2").await.unwrap();
    assert!(!ok);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v1"));
});

matrix_test!(hdel, c, {
    let k = common::key("h");
    let _: () = c
        .hset_multiple(&k, &[("a", "1"), ("b", "2"), ("d", "3")])
        .await
        .unwrap();
    let n: i64 = c.hdel(&k, &["a", "b", "missing"]).await.unwrap();
    assert_eq!(n, 2);
    let len: i64 = c.hlen(&k).await.unwrap();
    assert_eq!(len, 1);
});

matrix_test!(hgetall, c, {
    let k = common::key("h");
    let _: () = c
        .hset_multiple(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    let all: std::collections::HashMap<String, String> = c.hgetall(&k).await.unwrap();
    assert_eq!(all.len(), 2);
    assert_eq!(all.get("f1").map(|s| s.as_str()), Some("v1"));
    assert_eq!(all.get("f2").map(|s| s.as_str()), Some("v2"));
});

matrix_test!(hgetall_missing_is_empty, c, {
    let all: std::collections::HashMap<String, String> = c.hgetall(common::key("h")).await.unwrap();
    assert!(all.is_empty());
});

matrix_test!(hmget, c, {
    let k = common::key("h");
    let _: () = c
        .hset_multiple(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    let vals = c.hmget(&k, &["f1", "missing", "f2"]).await.unwrap();
    assert_eq!(vals[0].as_deref(), Some(&b"v1"[..]));
    assert_eq!(vals[1], None);
    assert_eq!(vals[2].as_deref(), Some(&b"v2"[..]));
});

matrix_test!(hexists, c, {
    let k = common::key("h");
    let _: () = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    let exists: bool = c.hexists(&k, "f").await.unwrap();
    assert!(exists);
    let exists: bool = c.hexists(&k, "nope").await.unwrap();
    assert!(!exists);
});

matrix_test!(hlen, c, {
    let k = common::key("h");
    let len: i64 = c.hlen(&k).await.unwrap();
    assert_eq!(len, 0);
    let _: () = c
        .hset_multiple(&k, &[("a", "1"), ("b", "2")])
        .await
        .unwrap();
    let len: i64 = c.hlen(&k).await.unwrap();
    assert_eq!(len, 2);
});

matrix_test!(hkeys_hvals, c, {
    let k = common::key("h");
    let _: () = c
        .hset_multiple(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    let mut keys: Vec<String> = c.hkeys(&k).await.unwrap();
    keys.sort();
    assert_eq!(keys, vec!["f1".to_string(), "f2".to_string()]);
    let mut vals: Vec<String> = c.hvals(&k).await.unwrap();
    vals.sort();
    assert_eq!(vals, vec!["v1".to_string(), "v2".to_string()]);
});

matrix_test!(hincr_by, c, {
    let k = common::key("h");
    let n: i64 = c.hincr(&k, "n", 5i64).await.unwrap();
    assert_eq!(n, 5);
    let n: i64 = c.hincr(&k, "n", -2i64).await.unwrap();
    assert_eq!(n, 3);
});

matrix_test!(hincr_by_float, c, {
    let k = common::key("h");
    let v: f64 = c.hincr(&k, "n", 1.5f64).await.unwrap();
    assert!((v - 1.5).abs() < 1e-9);
});

matrix_test!(hstrlen, c, {
    let k = common::key("h");
    let _: () = c.hset_multiple(&k, &[("f", "hello")]).await.unwrap();
    assert_eq!(c.hstrlen(&k, "f").await.unwrap(), 5);
    assert_eq!(c.hstrlen(&k, "missing").await.unwrap(), 0);
});

matrix_test!(hrandfield, c, {
    let k = common::key("h");
    let _: () = c.hset_multiple(&k, &[("only", "v")]).await.unwrap();
    assert_eq!(
        c.hrandfield(&k).await.unwrap().as_deref(),
        Some(&b"only"[..])
    );
    // Missing key -> None.
    assert_eq!(c.hrandfield(common::key("x")).await.unwrap(), None);
});

matrix_test!(hrandfield_count, c, {
    let k = common::key("h");
    let _: () = c
        .hset_multiple(&k, &[("a", "1"), ("b", "2"), ("d", "3")])
        .await
        .unwrap();
    let fields = c.hrandfield_count(&k, 2).await.unwrap();
    assert_eq!(fields.len(), 2);
});

matrix_test!(hset_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: i64 = c.rpush(&k, &["x"]).await.unwrap();
    let result: redis::RedisResult<Option<String>> = c.hget(&k, "f").await;
    assert!(result.is_err());
});

// ---------------------------------------------------------------------------
// Hash-field TTL (Valkey/Redis 7.4+). Gated on the server actually supporting
// HEXPIRE (via COMMAND INFO) rather than a version number.
// ---------------------------------------------------------------------------

matrix_test!(hexpire_and_httl, c, {
    skip_unless_command!(c, "HEXPIRE");
    let k = common::key("h_ttl");
    let _: () = c
        .hset_multiple(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    // Set a 100s TTL on f1 only.
    let res = c.hexpire(&k, 100, &["f1"], None).await.unwrap();
    assert_eq!(res, vec![1]); // 1 = expiry set
    // HTTL: f1 has a positive TTL, f2 has none (-1), missing field is -2.
    let ttls = c.httl(&k, &["f1", "f2", "missing"]).await.unwrap();
    assert!(ttls[0] > 0 && ttls[0] <= 100);
    assert_eq!(ttls[1], -1);
    assert_eq!(ttls[2], -2);
});

matrix_test!(hexpire_conditions, c, {
    skip_unless_command!(c, "HEXPIRE");
    let k = common::key("h_ttlc");
    let _: () = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    // NX: set only when no TTL exists -> succeeds.
    assert_eq!(
        c.hexpire(&k, 100, &["f"], Some(ExpireOptions::HasNoExpiry))
            .await
            .unwrap(),
        vec![1]
    );
    // NX again -> 0 (a TTL already exists).
    assert_eq!(
        c.hexpire(&k, 200, &["f"], Some(ExpireOptions::HasNoExpiry))
            .await
            .unwrap(),
        vec![0]
    );
    // XX: set only when a TTL exists -> succeeds.
    assert_eq!(
        c.hexpire(&k, 200, &["f"], Some(ExpireOptions::HasExistingExpiry))
            .await
            .unwrap(),
        vec![1]
    );
});

matrix_test!(hpexpire_and_hpttl, c, {
    skip_unless_command!(c, "HEXPIRE");
    let k = common::key("h_pttl");
    let _: () = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    assert_eq!(
        c.hpexpire(&k, 100_000, &["f"], None).await.unwrap(),
        vec![1]
    );
    let pttls = c.hpttl(&k, &["f"]).await.unwrap();
    assert!(pttls[0] > 0 && pttls[0] <= 100_000);
});

matrix_test!(hexpiretime_absolute, c, {
    skip_unless_command!(c, "HEXPIRE");
    let k = common::key("h_et");
    let _: () = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    let future = 4_102_444_800; // year 2100 (seconds)
    assert_eq!(
        c.hexpireat(&k, future, &["f"], None).await.unwrap(),
        vec![1]
    );
    let et = c.hexpiretime(&k, &["f"]).await.unwrap();
    assert_eq!(et[0], future);
});
