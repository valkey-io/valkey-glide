// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command generic (key-space) integration tests (RESP2 + RESP3).

mod common;

use glide::AsyncCommands;
use glide::GenericCommands;
use glide::commands::options::{Limit, OrderBy};

matrix_test!(del_and_exists, c, {
    let a = common::tkey("g", "a");
    let b = common::tkey("g", "b");
    let _: () = c.set(&a, "1").await.unwrap();
    let _: () = c.set(&b, "2").await.unwrap();
    let count: i64 = c.exists(&[&a, &b, &common::tkey("g", "m")]).await.unwrap();
    assert_eq!(count, 2);
    let deleted: i64 = c.del(&[&a, &b]).await.unwrap();
    assert_eq!(deleted, 2);
    let exists: i64 = c.exists(&[&a]).await.unwrap();
    assert_eq!(exists, 0);
});

matrix_test!(del_missing_zero, c, {
    let deleted: i64 = c.del(&[common::key("nope")]).await.unwrap();
    assert_eq!(deleted, 0);
});

matrix_test!(unlink, c, {
    let a = common::key("a");
    let _: () = c.set(&a, "1").await.unwrap();
    let unlinked: i64 = c.unlink(&[&a]).await.unwrap();
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
    let ttl: i64 = c.ttl(&k).await.unwrap();
    assert!(ttl > 0 && ttl <= 100);
    let persisted: bool = c.persist(&k).await.unwrap();
    assert!(persisted);
    let ttl: i64 = c.ttl(&k).await.unwrap();
    assert_eq!(ttl, -1);
});

matrix_test!(ttl_missing_and_no_expiry, c, {
    let k = common::key("k");
    // Missing key -> -2.
    let ttl: i64 = c.ttl(&k).await.unwrap();
    assert_eq!(ttl, -2);
    let _: () = c.set(&k, "v").await.unwrap();
    // No expiry -> -1.
    let ttl: i64 = c.ttl(&k).await.unwrap();
    assert_eq!(ttl, -1);
});

matrix_test!(expire_nx_xx, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    // NX sets only when no expiry exists — use raw cmd for EXPIRE with options.
    let nx_set: bool = c
        .glide_send(redis::cmd("EXPIRE").arg(&k).arg(100).arg("NX").clone())
        .await
        .unwrap();
    assert!(nx_set);
    // NX again fails since an expiry now exists.
    let nx_set2: bool = c
        .glide_send(redis::cmd("EXPIRE").arg(&k).arg(200).arg("NX").clone())
        .await
        .unwrap();
    assert!(!nx_set2);
    // XX succeeds since an expiry exists.
    let xx_set: bool = c
        .glide_send(redis::cmd("EXPIRE").arg(&k).arg(200).arg("XX").clone())
        .await
        .unwrap();
    assert!(xx_set);
});

matrix_test!(expire_gt_lt, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let _: bool = c.expire(&k, 100).await.unwrap();
    // GT only applies when new > current.
    let gt_set: bool = c
        .glide_send(redis::cmd("EXPIRE").arg(&k).arg(200).arg("GT").clone())
        .await
        .unwrap();
    assert!(gt_set);
    let gt_fail: bool = c
        .glide_send(redis::cmd("EXPIRE").arg(&k).arg(50).arg("GT").clone())
        .await
        .unwrap();
    assert!(!gt_fail);
    // LT only applies when new < current.
    let lt_set: bool = c
        .glide_send(redis::cmd("EXPIRE").arg(&k).arg(10).arg("LT").clone())
        .await
        .unwrap();
    assert!(lt_set);
});

matrix_test!(pexpire_and_pttl, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let set: bool = c.pexpire(&k, 100_000).await.unwrap();
    assert!(set);
    let pttl: i64 = c.pttl(&k).await.unwrap();
    assert!(pttl > 0);
});

matrix_test!(expireat_pexpireat, c, {
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
    let _: i64 = c.rpush(&l, &["a"]).await.unwrap();
    let t: String = c.key_type(&s).await.unwrap();
    assert_eq!(t, "string");
    let t: String = c.key_type(&l).await.unwrap();
    assert_eq!(t, "list");
    let t: String = c.key_type(common::key("m")).await.unwrap();
    assert_eq!(t, "none");
});

matrix_test!(rename, c, {
    let k = common::tkey("g", "k");
    let n = common::tkey("g", "n");
    let _: () = c.set(&k, "v").await.unwrap();
    let _: () = c.rename(&k, &n).await.unwrap();
    let exists: i64 = c.exists(&[&k]).await.unwrap();
    assert_eq!(exists, 0);
    let v: Option<String> = c.get(&n).await.unwrap();
    assert_eq!(v.as_deref(), Some("v"));
});

matrix_test!(rename_missing_errors, c, {
    let res: redis::RedisResult<()> = c
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
    assert!(c.copy(&src, &dst, false).await.unwrap());
    let v: Option<String> = c.get(&dst).await.unwrap();
    assert_eq!(v.as_deref(), Some("v"));
    // Without REPLACE, copying onto an existing key fails.
    let _: () = c.set(&src, "w").await.unwrap();
    assert!(!c.copy(&src, &dst, false).await.unwrap());
    assert!(c.copy(&src, &dst, true).await.unwrap());
    let v: Option<String> = c.get(&dst).await.unwrap();
    assert_eq!(v.as_deref(), Some("w"));
});

matrix_test!(object_encoding, c, {
    let k = common::key("k");
    let _: () = c.set(&k, "12345").await.unwrap();
    let enc: Option<String> = c.object_encoding(&k).await.unwrap();
    assert!(enc.is_some());
    let enc: Option<String> = c.object_encoding(common::key("nope")).await.unwrap();
    assert_eq!(enc, None);
});

matrix_test!(sort_numeric, c, {
    let k = common::key("l");
    let _: i64 = c.rpush(&k, &["3", "1", "2"]).await.unwrap();
    let asc = c.sort(&k, Some(OrderBy::Asc), None, false).await.unwrap();
    let asc: Vec<_> = asc.iter().map(|b| b.as_ref()).collect();
    assert_eq!(asc, vec![&b"1"[..], &b"2"[..], &b"3"[..]]);
});

matrix_test!(sort_with_limit, c, {
    let k = common::key("l");
    let _: i64 = c.rpush(&k, &["5", "4", "3", "2", "1"]).await.unwrap();
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
    let _: i64 = c.rpush(&k, &["banana", "apple", "cherry"]).await.unwrap();
    let sorted = c.sort(&k, Some(OrderBy::Asc), None, true).await.unwrap();
    let sorted: Vec<_> = sorted.iter().map(|b| b.as_ref()).collect();
    assert_eq!(sorted, vec![&b"apple"[..], &b"banana"[..], &b"cherry"[..]]);
});
