// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command hash integration tests (RESP2 + RESP3).

mod common;

use glide::AsyncTypedCommands;
use glide::ExpireOption;
use glide::Expiry;
use glide::FieldExistenceCheck;
use glide::HashCommands;
use glide::HashFieldExpirationOptions;
use glide::IntegerReplyOrNoOp;
use glide::SetExpiry;

/// Max seconds and milliseconds for future expiry.
const FUTURE_EXPIRY_SECS: u64 = (i64::MAX / 1_000_i64) as u64;
const FUTURE_EXPIRY_MS: u64 = i64::MAX as u64;

matrix_test!(hset, c, {
    let k = common::key("h");

    // Returns `true` when the field is added.
    assert!(c.hset(&k, "f", "v1").await.unwrap());

    // Returns `false` when an existing field is overwritten.
    assert!(!c.hset(&k, "f", "v2").await.unwrap());
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v2"));
});

matrix_test!(hset_multiple, c, {
    let k = common::key("h");

    // Returns the number of added fields.
    let added = c
        .hset_multiple(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    assert_eq!(added, 2);

    // Overwritten fields are not counted: `f2` exists, `f3` is new.
    let added = c
        .hset_multiple(&k, &[("f2", "v2b"), ("f3", "v3")])
        .await
        .unwrap();
    assert_eq!(added, 1);
    let vals = c.hmget(&k, &["f1", "f2", "f3"]).await.unwrap();
    assert_eq!(vals[0].as_deref(), Some(&b"v1"[..]));
    assert_eq!(vals[1].as_deref(), Some(&b"v2b"[..]));
    assert_eq!(vals[2].as_deref(), Some(&b"v3"[..]));
});

matrix_test!(hmset, c, {
    let k = common::key("h");

    // Returns `true` whether fields are added or overwritten.
    assert!(c.hmset(&k, &[("f1", "v1"), ("f2", "v2")]).await.unwrap());
    assert!(c.hmset(&k, &[("f2", "v2b"), ("f3", "v3")]).await.unwrap());

    let vals = c.hmget(&k, &["f1", "f2", "f3"]).await.unwrap();
    assert_eq!(vals[0].as_deref(), Some(&b"v1"[..]));
    assert_eq!(vals[1].as_deref(), Some(&b"v2b"[..]));
    assert_eq!(vals[2].as_deref(), Some(&b"v3"[..]));
});

matrix_test!(hget_missing_field, c, {
    let k = common::key("h");
    c.hset_multiple(&k, &[("f", "v")]).await.unwrap();

    let v: Option<String> = c.hget(&k, "nope").await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(hget_missing_key, c, {
    let v: Option<String> = c.hget(common::key("h"), "f").await.unwrap();
    assert_eq!(v, None);
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
    let _: usize = c
        .hset_multiple(&k, &[("a", "1"), ("b", "2"), ("d", "3")])
        .await
        .unwrap();
    let n: usize = c.hdel(&k, &["a", "b", "missing"]).await.unwrap();
    assert_eq!(n, 2);
    let len: usize = c.hlen(&k).await.unwrap();
    assert_eq!(len, 1);
});

matrix_test!(hgetall, c, {
    let k = common::key("h");
    let _: usize = c
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
    let _: usize = c
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
    let _: usize = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    let exists: bool = c.hexists(&k, "f").await.unwrap();
    assert!(exists);
    let exists: bool = c.hexists(&k, "nope").await.unwrap();
    assert!(!exists);
});

matrix_test!(hlen, c, {
    let k = common::key("h");
    assert_eq!(c.hlen(&k).await.unwrap(), 0);
    let _: usize = c
        .hset_multiple(&k, &[("a", "1"), ("b", "2")])
        .await
        .unwrap();
    let len: usize = c.hlen(&k).await.unwrap();
    assert_eq!(len, 2);
});

matrix_test!(hkeys_hvals, c, {
    let k = common::key("h");
    let _: usize = c
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
    let n: f64 = c.hincr(&k, "n", 5i64).await.unwrap();
    assert_eq!(n, 5.0);
    let n: f64 = c.hincr(&k, "n", -2i64).await.unwrap();
    assert_eq!(n, 3.0);
});

matrix_test!(hincr_by_float, c, {
    let k = common::key("h");
    let v: f64 = c.hincr(&k, "n", 1.5f64).await.unwrap();
    assert!((v - 1.5).abs() < 1e-9);
});

matrix_test!(hstrlen, c, {
    let k = common::key("h");
    let _: usize = c.hset_multiple(&k, &[("f", "hello")]).await.unwrap();
    assert_eq!(c.hstrlen(&k, "f").await.unwrap(), 5);
    assert_eq!(c.hstrlen(&k, "missing").await.unwrap(), 0);
});

matrix_test!(hrandfield, c, {
    let k = common::key("h");
    let _: usize = c.hset_multiple(&k, &[("only", "v")]).await.unwrap();
    assert_eq!(
        c.hrandfield(&k).await.unwrap().as_deref(),
        Some(&b"only"[..])
    );
    // Missing key -> None.
    assert_eq!(c.hrandfield(common::key("x")).await.unwrap(), None);
});

matrix_test!(hrandfield_count, c, {
    let k = common::key("h");
    let _: usize = c
        .hset_multiple(&k, &[("a", "1"), ("b", "2"), ("d", "3")])
        .await
        .unwrap();
    let fields = c.hrandfield_count(&k, 2).await.unwrap();
    assert_eq!(fields.len(), 2);
});

matrix_test!(hset_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: usize = c.rpush(&k, &["x"]).await.unwrap();
    let result: glide::ValkeyResult<Option<String>> = c.hget(&k, "f").await;
    assert!(result.is_err());
});

// ---------------------------------------------------------------------------
// Hash-field TTL (Valkey/Redis 7.4+).
// ---------------------------------------------------------------------------

matrix_test!(hexpire_and_httl, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let k = common::key("h_ttl");
    let _: usize = c
        .hset_multiple(&k, &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();

    // Set a 100s TTL on f1 only.
    let res: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 100, ExpireOption::NONE, "f1").await.unwrap();
    assert_eq!(res, vec![1]); // 1 = expiry set

    // HTTL: f1 has a positive TTL, f2 has none (-1), missing field is -2.
    let ttls: Vec<IntegerReplyOrNoOp> = c.httl(&k, &["f1", "f2", "missing"]).await.unwrap();
    assert!(matches!(ttls[0], IntegerReplyOrNoOp::IntegerReply(1..=100)));
    assert_eq!(ttls[1], IntegerReplyOrNoOp::ExistsButNotRelevant);
    assert_eq!(ttls[2], IntegerReplyOrNoOp::NotExists);
});

matrix_test!(hexpire_conditions, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let k = common::key("h_ttlc");
    let _: usize = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();

    // NX: set only when no TTL exists -> succeeds.
    let res: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 100, ExpireOption::NX, "f").await.unwrap();
    assert_eq!(res, vec![1]);

    // NX again -> 0 (a TTL already exists).
    let res: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 200, ExpireOption::NX, "f").await.unwrap();
    assert_eq!(res, vec![0]);

    // XX: set only when a TTL exists -> succeeds.
    let res: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 200, ExpireOption::XX, "f").await.unwrap();
    assert_eq!(res, vec![1]);

    // GT: the new TTL (100) is not greater than the current one (200) -> 0.
    let res: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 100, ExpireOption::GT, "f").await.unwrap();
    assert_eq!(res, vec![0]);

    // LT: the new TTL (100) is less than the current one (200) -> succeeds.
    let res: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 100, ExpireOption::LT, "f").await.unwrap();
    assert_eq!(res, vec![1]);
});

matrix_test!(hpexpire_and_hpttl, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let k = common::key("h_pttl");
    let _: usize = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();

    let res: Vec<IntegerReplyOrNoOp> = c
        .hpexpire(&k, 100_000, ExpireOption::NONE, "f")
        .await
        .unwrap();
    assert_eq!(res, vec![1]);

    let pttl: Vec<IntegerReplyOrNoOp> = c.hpttl(&k, "f").await.unwrap();
    assert!(matches!(
        pttl[0],
        IntegerReplyOrNoOp::IntegerReply(1..=100_000)
    ));
});

matrix_test!(hexpire_time_absolute, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let k = common::key("h_et");
    let _: usize = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    let res: Vec<IntegerReplyOrNoOp> = c
        .hexpire_at(&k, FUTURE_EXPIRY_SECS as i64, ExpireOption::NONE, "f")
        .await
        .unwrap();
    assert_eq!(res, vec![1]);
    let et: Vec<IntegerReplyOrNoOp> = c.hexpire_time(&k, "f").await.unwrap();
    assert_eq!(et, vec![FUTURE_EXPIRY_SECS as isize]);

    let res: Vec<IntegerReplyOrNoOp> = c
        .hpexpire_at(&k, FUTURE_EXPIRY_MS as i64, ExpireOption::NONE, "f")
        .await
        .unwrap();
    assert_eq!(res, vec![1]);
    let pet: Vec<IntegerReplyOrNoOp> = c.hpexpire_time(&k, "f").await.unwrap();
    assert_eq!(pet, vec![FUTURE_EXPIRY_MS as isize]);
});

matrix_test!(hpersist, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let k = common::key("h_persist");
    let _: usize = c.hset_multiple(&k, &[("f", "v")]).await.unwrap();
    let _: Vec<IntegerReplyOrNoOp> = c.hexpire(&k, 100, ExpireOption::NONE, "f").await.unwrap();

    let res: Vec<IntegerReplyOrNoOp> = c.hpersist(&k, "f").await.unwrap();
    assert_eq!(res, vec![1]); // 1 = expiry removed
    let ttl: Vec<IntegerReplyOrNoOp> = c.httl(&k, "f").await.unwrap();
    assert_eq!(ttl, vec![IntegerReplyOrNoOp::ExistsButNotRelevant]);
});

// ---------------------------------------------------------------------------
// HGETEX / HSETEX (Valkey/Redis 9.0+).
// ---------------------------------------------------------------------------

matrix_test!(hget_ex, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let k = common::key("h_getex");
    c.hset(&k, "f", "v").await.unwrap();

    // HGETEX with EX.
    let vals: Vec<Option<String>> = c.hget_ex(&k, "f", Expiry::EX(100)).await.unwrap();
    assert_eq!(vals, vec![Some("v".to_string())]);
    let ttl: Vec<IntegerReplyOrNoOp> = c.httl(&k, "f").await.unwrap();
    assert!(matches!(ttl[0], IntegerReplyOrNoOp::IntegerReply(1..=100)));

    // HGETEX with PX.
    let vals: Vec<Option<String>> = c.hget_ex(&k, "f", Expiry::PX(100_000)).await.unwrap();
    assert_eq!(vals, vec![Some("v".to_string())]);
    let pttl: Vec<IntegerReplyOrNoOp> = c.hpttl(&k, "f").await.unwrap();
    assert!(matches!(
        pttl[0],
        IntegerReplyOrNoOp::IntegerReply(1..=100_000)
    ));

    // HGETEX with EXAT.
    let vals: Vec<Option<String>> = c
        .hget_ex(&k, "f", Expiry::EXAT(FUTURE_EXPIRY_SECS))
        .await
        .unwrap();
    assert_eq!(vals, vec![Some("v".to_string())]);
    let et: Vec<IntegerReplyOrNoOp> = c.hexpire_time(&k, "f").await.unwrap();
    assert_eq!(et, vec![FUTURE_EXPIRY_SECS as isize]);

    // HGETEX with PXAT.
    let vals: Vec<Option<String>> = c
        .hget_ex(&k, "f", Expiry::PXAT(FUTURE_EXPIRY_MS))
        .await
        .unwrap();
    assert_eq!(vals, vec![Some("v".to_string())]);
    let pet: Vec<IntegerReplyOrNoOp> = c.hpexpire_time(&k, "f").await.unwrap();
    assert_eq!(pet, vec![FUTURE_EXPIRY_MS as isize]);

    // HGETEX with PERSIST.
    let vals: Vec<Option<String>> = c
        .hget_ex(&k, &["f", "missing"], Expiry::PERSIST)
        .await
        .unwrap();
    assert_eq!(vals, vec![Some("v".to_string()), None]);
    let ttl: Vec<IntegerReplyOrNoOp> = c.httl(&k, "f").await.unwrap();
    // The field exists but has no TTL.
    assert_eq!(ttl, vec![IntegerReplyOrNoOp::ExistsButNotRelevant]);
});

matrix_test!(hset_ex, c, {
    skip_if_version_below!(c, 9, 0, 0);

    let fnx = HashFieldExpirationOptions::default().set_existence_check(FieldExistenceCheck::FNX);
    let fxx = HashFieldExpirationOptions::default().set_existence_check(FieldExistenceCheck::FXX);

    // HSETEX with FNX and EX.
    let k = common::key("h_setex");
    let res: bool = c
        .hset_ex(&k, &fnx.set_expiration(SetExpiry::EX(100)), &[("f", "v1")])
        .await
        .unwrap();
    assert!(res);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v1"));
    let ttl: Vec<IntegerReplyOrNoOp> = c.httl(&k, "f").await.unwrap();
    assert!(matches!(ttl[0], IntegerReplyOrNoOp::IntegerReply(1..=100)));

    let res: bool = c.hset_ex(&k, &fnx, &[("f", "v2")]).await.unwrap();
    assert!(!res);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v1"));

    // HSETEX with FXX and EX.
    let res: bool = c
        .hset_ex(&k, &fxx.set_expiration(SetExpiry::EX(50)), &[("f", "v3")])
        .await
        .unwrap();
    assert!(res);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v3"));
    let ttl: Vec<IntegerReplyOrNoOp> = c.httl(&k, "f").await.unwrap();
    assert!(matches!(ttl[0], IntegerReplyOrNoOp::IntegerReply(1..=50)));

    let res: bool = c.hset_ex(&k, &fxx, &[("g", "gv")]).await.unwrap();
    assert!(!res);
    let g: Option<String> = c.hget(&k, "g").await.unwrap();
    assert_eq!(g, None);

    // HSETEX with PX.
    let options = HashFieldExpirationOptions::default().set_expiration(SetExpiry::PX(100_000));
    let res: bool = c.hset_ex(&k, &options, &[("f", "v4")]).await.unwrap();
    assert!(res);
    let pttl: Vec<IntegerReplyOrNoOp> = c.hpttl(&k, "f").await.unwrap();
    assert!(matches!(
        pttl[0],
        IntegerReplyOrNoOp::IntegerReply(1..=100_000)
    ));

    // HSETEX with EXAT.
    let options =
        HashFieldExpirationOptions::default().set_expiration(SetExpiry::EXAT(FUTURE_EXPIRY_SECS));
    let res: bool = c.hset_ex(&k, &options, &[("f", "v5")]).await.unwrap();
    assert!(res);
    let et: Vec<IntegerReplyOrNoOp> = c.hexpire_time(&k, "f").await.unwrap();
    assert_eq!(et, vec![FUTURE_EXPIRY_SECS as isize]);

    // HSETEX with PXAT.
    let options =
        HashFieldExpirationOptions::default().set_expiration(SetExpiry::PXAT(FUTURE_EXPIRY_MS));
    let res: bool = c.hset_ex(&k, &options, &[("f", "v6")]).await.unwrap();
    assert!(res);
    let pet: Vec<IntegerReplyOrNoOp> = c.hpexpire_time(&k, "f").await.unwrap();
    assert_eq!(pet, vec![FUTURE_EXPIRY_MS as isize]);

    // HSETEX with KEEPTTL.
    let before = c.hpttl(&k, "f").await.unwrap()[0]
        .integer()
        .expect("TTL set");
    let options = HashFieldExpirationOptions::default().set_expiration(SetExpiry::KEEPTTL);
    let res: bool = c.hset_ex(&k, &options, &[("f", "v7")]).await.unwrap();
    assert!(res);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v7"));
    let after = c.hpttl(&k, "f").await.unwrap()[0]
        .integer()
        .expect("TTL retained");
    assert!((1..=before).contains(&after));

    // HSETEX with no expiry option.
    let options = HashFieldExpirationOptions::default();
    let res: bool = c.hset_ex(&k, &options, &[("f", "v8")]).await.unwrap();
    assert!(res);
    let v: Option<String> = c.hget(&k, "f").await.unwrap();
    assert_eq!(v.as_deref(), Some("v8"));
    let ttl: Vec<IntegerReplyOrNoOp> = c.httl(&k, "f").await.unwrap();
    assert_eq!(ttl, vec![IntegerReplyOrNoOp::ExistsButNotRelevant]);
});
