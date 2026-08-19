// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command string integration tests, run against a live server across both
//! RESP2 and RESP3. Mirrors `test_async_client.py` string coverage: happy path,
//! missing-key behaviour, wrong-type errors, bounds and SET conditions.

mod common;

use glide::AsyncCommands;
use glide::StringCommands; // surviving native extension: lcs, lcs_len, lcs_idx
use redis::{ExistenceCheck, SetExpiry, SetOptions};

matrix_test!(set_and_get, c, {
    let k = common::key("str");
    let _: () = c.set(&k, "hello").await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("hello"));
});

matrix_test!(get_missing_is_none, c, {
    let v: Option<String> = c.get(common::key("missing")).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(set_overwrites, c, {
    let k = common::key("str");
    let _: () = c.set(&k, "a").await.unwrap();
    let _: () = c.set(&k, "b").await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("b"));
});

matrix_test!(set_binary_value, c, {
    let k = common::key("bin");
    let payload = vec![0u8, 1, 2, 255, 0, 128];
    let _: () = c.set(&k, payload.clone()).await.unwrap();
    let v: Option<Vec<u8>> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some(&payload[..]));
});

matrix_test!(append_to_missing_creates, c, {
    let k = common::key("app");
    let n: i64 = c.append(&k, "abc").await.unwrap();
    assert_eq!(n, 3);
    let n: i64 = c.append(&k, "de").await.unwrap();
    assert_eq!(n, 5);
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("abcde"));
});

matrix_test!(strlen_present_and_missing, c, {
    let k = common::key("sl");
    let _: () = c.set(&k, "hello").await.unwrap();
    let len: i64 = c.strlen(&k).await.unwrap();
    assert_eq!(len, 5);
    let len: i64 = c.strlen(common::key("nope")).await.unwrap();
    assert_eq!(len, 0);
});

matrix_test!(getrange_bounds, c, {
    let k = common::key("gr");
    let _: () = c.set(&k, "Hello World").await.unwrap();
    let v: Vec<u8> = c.getrange(&k, 0, 4).await.unwrap();
    assert_eq!(&v, b"Hello");
    // Negative indices count from the end.
    let v: Vec<u8> = c.getrange(&k, -5, -1).await.unwrap();
    assert_eq!(&v, b"World");
    // Out-of-range start yields empty.
    let v: Vec<u8> = c.getrange(&k, 100, 200).await.unwrap();
    assert_eq!(&v, b"");
});

matrix_test!(setrange_extends, c, {
    let k = common::key("sr");
    let _: () = c.set(&k, "Hello World").await.unwrap();
    let len: i64 = c.setrange(&k, 6, "Redis").await.unwrap();
    assert_eq!(len, 11);
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("Hello Redis"));
});

matrix_test!(setrange_zero_pads, c, {
    let k = common::key("srp");
    let len: i64 = c.setrange(&k, 5, "x").await.unwrap();
    assert_eq!(len, 6);
    let v: Option<Vec<u8>> = c.get(&k).await.unwrap();
    assert_eq!(v.unwrap().len(), 6);
});

matrix_test!(incr_decr_family, c, {
    let k = common::key("ctr");
    let n: i64 = c.incr(&k, 1i64).await.unwrap();
    assert_eq!(n, 1);
    let n: i64 = c.incr(&k, 9i64).await.unwrap();
    assert_eq!(n, 10);
    let n: i64 = c.decr(&k, 1i64).await.unwrap();
    assert_eq!(n, 9);
    let n: i64 = c.decr(&k, 4i64).await.unwrap();
    assert_eq!(n, 5);
});

matrix_test!(incr_on_missing_starts_at_zero, c, {
    let k = common::key("ctr0");
    let n: i64 = c.incr(&k, 5i64).await.unwrap();
    assert_eq!(n, 5);
});

matrix_test!(incr_by_float, c, {
    let k = common::key("f");
    let _: () = c.set(&k, "10.5").await.unwrap();
    let v: f64 = c.incr(&k, 0.1f64).await.unwrap();
    assert!((v - 10.6).abs() < 1e-9);
});

matrix_test!(incr_non_integer_errors, c, {
    let k = common::key("nonint");
    let _: () = c.set(&k, "notanumber").await.unwrap();
    let result: redis::RedisResult<i64> = c.incr(&k, 1i64).await;
    assert!(result.is_err());
});

matrix_test!(mset_mget, c, {
    let a = common::tkey("ms", "a");
    let b = common::tkey("ms", "b");
    let missing = common::tkey("ms", "m");
    let _: () = c.mset(&[(&a, "1"), (&b, "2")]).await.unwrap();
    let got: Vec<Option<String>> = c.mget(&[&a, &b, &missing]).await.unwrap();
    assert_eq!(got[0].as_deref(), Some("1"));
    assert_eq!(got[1].as_deref(), Some("2"));
    assert_eq!(got[2], None);
});

matrix_test!(msetnx_all_or_nothing, c, {
    let a = common::tkey("mn", "a");
    let b = common::tkey("mn", "b");
    let ok: bool = c.mset_nx(&[(&a, "1"), (&b, "2")]).await.unwrap();
    assert!(ok);
    // Second call fails because keys already exist.
    let ok: bool = c
        .mset_nx(&[(&a, "x"), (&common::tkey("mn", "c"), "3")])
        .await
        .unwrap();
    assert!(!ok);
    let v: Option<String> = c.get(&a).await.unwrap();
    assert_eq!(v.as_deref(), Some("1"));
});

matrix_test!(getdel_returns_and_removes, c, {
    let k = common::key("gd");
    let _: () = c.set(&k, "value").await.unwrap();
    let v: Option<String> = c.get_del(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("value"));
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v, None);
    // GETDEL on a missing key is None.
    let v: Option<String> = c.get_del(common::key("x")).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(getex_sets_expiry, c, {
    let k = common::key("gx");
    let _: () = c.set(&k, "v").await.unwrap();
    let v: Option<String> = c.get_ex(&k, redis::Expiry::EX(100)).await.unwrap();
    assert_eq!(v.as_deref(), Some("v"));
});

matrix_test!(set_nx_does_not_overwrite, c, {
    let k = common::key("nx");
    let _: () = c.set(&k, "first").await.unwrap();
    let opts = SetOptions::default().conditional_set(ExistenceCheck::NX);
    let _: Option<String> = c.set_options(&k, "second", opts).await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("first"));
});

matrix_test!(set_xx_only_if_exists, c, {
    let k = common::key("xx");
    let opts = SetOptions::default().conditional_set(ExistenceCheck::XX);
    // XX on a missing key does not set.
    let _: Option<String> = c.set_options(&k, "v", opts).await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v, None);
    // After it exists, XX succeeds.
    let _: () = c.set(&k, "a").await.unwrap();
    let opts = SetOptions::default().conditional_set(ExistenceCheck::XX);
    let _: Option<String> = c.set_options(&k, "b", opts).await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("b"));
});

matrix_test!(set_get_returns_old_value, c, {
    let k = common::key("go");
    let _: () = c.set(&k, "old").await.unwrap();
    let opts = SetOptions::default().get(true);
    let old: Option<String> = c.set_options(&k, "new", opts).await.unwrap();
    assert_eq!(old.as_deref(), Some("old"));
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("new"));
});

matrix_test!(set_with_expiry, c, {
    let k = common::key("ex");
    let opts = SetOptions::default().with_expiration(SetExpiry::EX(100));
    let _: Option<String> = c.set_options(&k, "v", opts).await.unwrap();
    let v: Option<String> = c.get(&k).await.unwrap();
    assert_eq!(v.as_deref(), Some("v"));
});

matrix_test!(get_wrong_type_errors, c, {
    // GET against a list key must be an error (WRONGTYPE).
    let k = common::key("wt");
    let _: i64 = c.rpush(&k, &["a"]).await.unwrap();
    let result: redis::RedisResult<Option<String>> = c.get(&k).await;
    assert!(result.is_err());
});

matrix_test!(lcs_len, c, {
    let k1 = common::tkey("lcs", "1");
    let k2 = common::tkey("lcs", "2");
    let _: () = c.set(&k1, "ohmytext").await.unwrap();
    let _: () = c.set(&k2, "mynewtext").await.unwrap();
    assert_eq!(c.lcs_len(&k1, &k2).await.unwrap(), 6);
});
