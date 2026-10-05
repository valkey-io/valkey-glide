// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command generic (key-space) integration tests (RESP2 + RESP3).

mod common;

use glide::AsyncTypedCommands;
use glide::CopyOptions;
use glide::GenericCommands;
use glide::IntegerReplyOrNoOp;
use glide::ServerManagementCommands;
use glide::ValueType;
use glide::commands::options::{Limit, OrderBy};

matrix_test!(del_and_exists, c, {
    let a = common::tkey("g", "a");
    let b = common::tkey("g", "b");
    let _: () = c.set(&a, "1").await.unwrap();
    let _: () = c.set(&b, "2").await.unwrap();
    let any: bool = c.exists(&[&a, &b, &common::tkey("g", "m")]).await.unwrap();
    assert!(any);
    let deleted: usize = c.del(&[&a, &b]).await.unwrap();
    assert_eq!(deleted, 2);
    let exists: bool = c.exists(&[&a]).await.unwrap();
    assert!(!exists);
});

matrix_test!(del_missing_zero, c, {
    assert_eq!(c.del(&[common::key("nope")]).await.unwrap(), 0);
});

matrix_test!(keys, c, {
    let prefix = common::key("keys");
    let a = format!("{prefix}:a");
    let b = format!("{prefix}:b");
    let _: () = c.set(&a, "1").await.unwrap();
    let _: () = c.set(&b, "2").await.unwrap();

    let mut found: Vec<String> = c.keys(format!("{prefix}:*")).await.unwrap();
    found.sort();
    assert_eq!(found, vec![a, b]);
});

matrix_test!(unlink, c, {
    let a = common::key("a");
    let _: () = c.set(&a, "1").await.unwrap();
    let unlinked: usize = c.unlink(&[&a]).await.unwrap();
    assert_eq!(unlinked, 1);
});

matrix_test!(touch, c, {
    let a = common::tkey("g", "a");
    let b = common::tkey("g", "b");
    let _: () = c.set(&a, "1").await.unwrap();
    let _: () = c.set(&b, "2").await.unwrap();
    assert_eq!(
        c.touch(&[&a, &b, &common::tkey("g", "m")]).await.unwrap(),
        2
    );
});

matrix_test!(expire_and_ttl, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let set: bool = c.expire(&k, 100).await.unwrap();
    assert!(set);
    let ttl: IntegerReplyOrNoOp = c.ttl(&k).await.unwrap();
    assert!(matches!(ttl, IntegerReplyOrNoOp::IntegerReply(1..=100)));
    let persisted: bool = c.persist(&k).await.unwrap();
    assert!(persisted);
    let ttl: IntegerReplyOrNoOp = c.ttl(&k).await.unwrap();
    assert_eq!(ttl, IntegerReplyOrNoOp::ExistsButNotRelevant);
});

matrix_test!(ttl_missing_and_no_expiry, c, {
    let k = common::key("k");

    // Missing key -> -2.
    let ttl: IntegerReplyOrNoOp = c.ttl(&k).await.unwrap();
    assert_eq!(ttl, IntegerReplyOrNoOp::NotExists);
    let _: () = c.set(&k, "v").await.unwrap();

    // No expiry -> -1.
    let ttl: IntegerReplyOrNoOp = c.ttl(&k).await.unwrap();
    assert_eq!(ttl, IntegerReplyOrNoOp::ExistsButNotRelevant);
});

// TODO #7082: replace the raw `EXPIRE` commands here with a typed `expire` that
// accepts the NX/XX condition options.
matrix_test!(expire_nx_xx, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();

    // NX sets only when no expiry exists — use raw cmd for EXPIRE with options.
    let nx_set: bool = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("EXPIRE").arg(&k).arg(100).arg("NX").clone(),
    )
    .await
    .unwrap();
    assert!(nx_set);

    // NX again fails since an expiry now exists.
    let nx_set2: bool = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("EXPIRE").arg(&k).arg(200).arg("NX").clone(),
    )
    .await
    .unwrap();
    assert!(!nx_set2);

    // XX succeeds since an expiry exists.
    let xx_set: bool = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("EXPIRE").arg(&k).arg(200).arg("XX").clone(),
    )
    .await
    .unwrap();
    assert!(xx_set);
});

// TODO #7082: replace the raw `EXPIRE` commands here with a typed `expire` that
// accepts the GT/LT condition options.
matrix_test!(expire_gt_lt, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let _: bool = c.expire(&k, 100).await.unwrap();

    // GT only applies when new > current.
    let gt_set: bool = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("EXPIRE").arg(&k).arg(200).arg("GT").clone(),
    )
    .await
    .unwrap();
    assert!(gt_set);

    let gt_fail: bool = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("EXPIRE").arg(&k).arg(50).arg("GT").clone(),
    )
    .await
    .unwrap();
    assert!(!gt_fail);

    // LT only applies when new < current.
    let lt_set: bool = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("EXPIRE").arg(&k).arg(10).arg("LT").clone(),
    )
    .await
    .unwrap();
    assert!(lt_set);
});

matrix_test!(pexpire_and_pttl, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let set: bool = c.pexpire(&k, 100_000).await.unwrap();
    assert!(set);
    let pttl: IntegerReplyOrNoOp = c.pttl(&k).await.unwrap();
    assert!(matches!(pttl, IntegerReplyOrNoOp::IntegerReply(1..)));
});

matrix_test!(expireat_pexpireat, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let future = 4_102_444_800i64; // year 2100 in seconds
    let set: bool = c.expire_at(&k, future).await.unwrap();
    assert!(set);
    assert!(c.expiretime(&k).await.unwrap() > 0);
    let set: bool = c.pexpire_at(&k, future * 1000).await.unwrap();
    assert!(set);
    assert!(c.pexpiretime(&k).await.unwrap() > 0);
});

matrix_test!(key_type, c, {
    let s = common::key("s");
    let l = common::key("l");
    let _: () = c.set(&s, "v").await.unwrap();
    let _: usize = c.rpush(&l, &["a"]).await.unwrap();
    let t: ValueType = c.key_type(&s).await.unwrap();
    assert_eq!(t, ValueType::String);
    let t: ValueType = c.key_type(&l).await.unwrap();
    assert_eq!(t, ValueType::List);
    let t: ValueType = c.key_type(common::key("m")).await.unwrap();
    assert_eq!(t, ValueType::None);
});

matrix_test!(rename, c, {
    let k = common::tkey("g", "k");
    let n = common::tkey("g", "n");
    let _: () = c.set(&k, "v").await.unwrap();
    let _: () = c.rename(&k, &n).await.unwrap();
    let exists: bool = c.exists(&[&k]).await.unwrap();
    assert!(!exists);
    let v: Option<String> = c.get(&n).await.unwrap();
    assert_eq!(v.as_deref(), Some("v"));
});

matrix_test!(rename_missing_errors, c, {
    let res: glide::ValkeyResult<()> = c
        .rename(common::tkey("g", "nope"), common::tkey("g", "dst"))
        .await;
    assert!(res.is_err());
});

matrix_test!(renamenx, c, {
    let k = common::tkey("g", "k");
    let n = common::tkey("g", "n");
    let _: () = c.set(&k, "v").await.unwrap();
    let set: bool = c.rename_nx(&k, &n).await.unwrap();
    assert!(set);
    // Now target exists; renaming another key onto it fails.
    let k2 = common::tkey("g", "k2");
    let _: () = c.set(&k2, "w").await.unwrap();
    let set: bool = c.rename_nx(&k2, &n).await.unwrap();
    assert!(!set);
});

// `randomkey` is routed to a random node in cluster mode, which may not hold our
// key, so this stays standalone-only (RESP2 + RESP3).
resp_test!(randomkey_present, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    assert!(c.randomkey().await.unwrap().is_some());
});

matrix_test!(dump_missing_none, c, {
    assert_eq!(c.dump(common::key("nope")).await.unwrap(), None);
});

matrix_test!(copy, c, {
    let src = common::tkey("g", "src");
    let dst = common::tkey("g", "dst");
    let _: () = c.set(&src, "v").await.unwrap();
    let copied: bool = c.copy(&src, &dst, CopyOptions::default()).await.unwrap();
    assert!(copied);

    let v: Option<String> = c.get(&dst).await.unwrap();
    assert_eq!(v.as_deref(), Some("v"));
    // Without REPLACE, copying onto an existing key fails.
    let _: () = c.set(&src, "w").await.unwrap();
    let copied: bool = c.copy(&src, &dst, CopyOptions::default()).await.unwrap();
    assert!(!copied);

    let copied: bool = c
        .copy(&src, &dst, CopyOptions::default().replace(true))
        .await
        .unwrap();
    assert!(copied);

    let v: Option<String> = c.get(&dst).await.unwrap();
    assert_eq!(v.as_deref(), Some("w"));
});

// Standalone only: cluster mode only supports database 0.
resp_test!(copy_to_db, c, {
    let src = common::key("src");
    let dst = common::key("dst");
    let _: () = c.set(&src, "v").await.unwrap();

    // Copying into database 1 leaves database 0 without the destination.
    let copied: bool = c
        .copy(&src, &dst, CopyOptions::default().db(1))
        .await
        .unwrap();
    assert!(copied);
    let exists: bool = c.exists(&dst).await.unwrap();
    assert!(!exists);

    // The destination now exists in database 1: copying again needs REPLACE.
    let copied: bool = c
        .copy(&src, &dst, CopyOptions::default().db(1))
        .await
        .unwrap();
    assert!(!copied);
    let copied: bool = c
        .copy(&src, &dst, CopyOptions::default().db(1).replace(true))
        .await
        .unwrap();
    assert!(copied);
});

matrix_test!(object_encoding, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "12345").await.unwrap();
    let enc: Option<String> = c.object_encoding(&k).await.unwrap();
    assert!(enc.is_some());
    let enc: Option<String> = c.object_encoding(common::key("nope")).await.unwrap();
    assert_eq!(enc, None);
});

matrix_test!(object_idletime, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let idle: Option<usize> = c.object_idletime(&k).await.unwrap();
    assert!(idle.is_some());
    let idle: Option<usize> = c.object_idletime(common::key("nope")).await.unwrap();
    assert_eq!(idle, None);
});

matrix_test!(object_freq, c, {
    // `OBJECT FREQ` requires an LFU eviction policy.
    c.config_set("maxmemory-policy", "allkeys-lfu")
        .await
        .unwrap();
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let freq: Option<usize> = c.object_freq(&k).await.unwrap();
    assert!(freq.is_some());
    let freq: Option<usize> = c.object_freq(common::key("nope")).await.unwrap();
    assert_eq!(freq, None);
});

matrix_test!(object_refcount, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let refcount: Option<usize> = c.object_refcount(&k).await.unwrap();
    assert!(refcount.is_some_and(|n| n >= 1));
    let refcount: Option<usize> = c.object_refcount(common::key("nope")).await.unwrap();
    assert_eq!(refcount, None);
});

matrix_test!(sort_numeric, c, {
    let k = common::key("l");
    let _: usize = c.rpush(&k, &["3", "1", "2"]).await.unwrap();
    let asc = c.sort(&k, Some(OrderBy::Asc), None, false).await.unwrap();
    let asc: Vec<_> = asc.iter().map(|b| b.as_ref()).collect();
    assert_eq!(asc, vec![&b"1"[..], &b"2"[..], &b"3"[..]]);
});

matrix_test!(sort_with_limit, c, {
    let k = common::key("l");
    let _: usize = c.rpush(&k, &["5", "4", "3", "2", "1"]).await.unwrap();
    let limited = c
        .sort(
            &k,
            Some(OrderBy::Asc),
            Some(Limit {
                offset: 1,
                count: 2,
            }),
            false,
        )
        .await
        .unwrap();
    let limited: Vec<_> = limited.iter().map(|b| b.as_ref()).collect();
    assert_eq!(limited, vec![&b"2"[..], &b"3"[..]]);
});

matrix_test!(sort_alpha, c, {
    let k = common::key("l");
    let _: usize = c.rpush(&k, &["banana", "apple", "cherry"]).await.unwrap();
    let sorted = c.sort(&k, Some(OrderBy::Asc), None, true).await.unwrap();
    let sorted: Vec<_> = sorted.iter().map(|b| b.as_ref()).collect();
    assert_eq!(sorted, vec![&b"apple"[..], &b"banana"[..], &b"cherry"[..]]);
});
