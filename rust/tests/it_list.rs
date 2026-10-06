// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command list integration tests (RESP2 + RESP3).

mod common;

use glide::AsyncTypedCommands;
use std::num::NonZeroUsize;

matrix_test!(rpush_lpush_llen, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c"]).await.unwrap();
    c.lpush(&k, &["z"]).await.unwrap();
    let len: usize = c.llen(&k).await.unwrap();
    assert_eq!(len, 4);
});

matrix_test!(llen_missing_is_zero, c, {
    assert_eq!(c.llen(common::key("l")).await.unwrap(), 0);
});

matrix_test!(lpushx_rpushx_require_existing, c, {
    let k = common::key("l");
    // X variants no-op on a missing key.
    assert_eq!(c.lpush_exists(&k, "a").await.unwrap(), 0);
    assert_eq!(c.rpush_exists(&k, "a").await.unwrap(), 0);
    c.rpush(&k, "x").await.unwrap();
    let n: usize = c.lpush_exists(&k, "y").await.unwrap();
    assert_eq!(n, 2);
    let n: usize = c.rpush_exists(&k, "z").await.unwrap();
    assert_eq!(n, 3);
});

matrix_test!(lrange_full_and_negative, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c", "d"]).await.unwrap();
    let all: Vec<String> = c.lrange(&k, 0, -1).await.unwrap();
    assert_eq!(all.len(), 4);
    let tail: Vec<String> = c.lrange(&k, -2, -1).await.unwrap();
    assert_eq!(tail, vec!["c", "d"]);
});

matrix_test!(lrange_missing_empty, c, {
    let r: Vec<String> = c.lrange(common::key("l"), 0, -1).await.unwrap();
    assert!(r.is_empty());
});

matrix_test!(lindex, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c"]).await.unwrap();
    let v: Option<String> = c.lindex(&k, 0).await.unwrap();
    assert_eq!(v.as_deref(), Some("a"));
    let v: Option<String> = c.lindex(&k, -1).await.unwrap();
    assert_eq!(v.as_deref(), Some("c"));
    // Out-of-range index -> None.
    let v: Option<String> = c.lindex(&k, 99).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(lpop_rpop, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c"]).await.unwrap();
    let v: Option<String> = c.lpop(&k, None).await.unwrap();
    assert_eq!(v.as_deref(), Some("a"));
    let v: Option<String> = c.rpop(&k, None).await.unwrap();
    assert_eq!(v.as_deref(), Some("c"));
});

matrix_test!(lpop_rpop_missing_none, c, {
    let v: Option<String> = c.lpop(common::key("l"), None).await.unwrap();
    assert_eq!(v, None);
    let v: Option<String> = c.rpop(common::key("l2"), None).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(lpop_rpop_count, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c", "d"]).await.unwrap();
    let front: Vec<String> = c
        .lpop(&k, Some(NonZeroUsize::new(2).unwrap()))
        .await
        .unwrap();
    assert_eq!(front, vec!["a", "b"]);
    let back: Vec<String> = c
        .rpop(&k, Some(NonZeroUsize::new(2).unwrap()))
        .await
        .unwrap();
    assert_eq!(back, vec!["d", "c"]);
});

matrix_test!(lset, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c"]).await.unwrap();
    c.lset(&k, 1, "B").await.unwrap();
    let v: Option<String> = c.lindex(&k, 1).await.unwrap();
    assert_eq!(v.as_deref(), Some("B"));
});

matrix_test!(lset_out_of_range_errors, c, {
    let k = common::key("l");
    c.rpush(&k, "a").await.unwrap();
    let res: glide::ValkeyResult<()> = c.lset(&k, 5, "x").await;
    assert!(res.is_err());
});

matrix_test!(ltrim, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c", "d", "e"]).await.unwrap();
    c.ltrim(&k, 1, 3).await.unwrap();
    let remaining: Vec<String> = c.lrange(&k, 0, -1).await.unwrap();
    assert_eq!(remaining, vec!["b", "c", "d"]);
});

matrix_test!(lrem, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "a", "c", "a"]).await.unwrap();
    // Remove 2 occurrences from the head.
    let removed: usize = c.lrem(&k, 2, "a").await.unwrap();
    assert_eq!(removed, 2);
    let len: usize = c.llen(&k).await.unwrap();
    assert_eq!(len, 3);
});

matrix_test!(linsert_before, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "c"]).await.unwrap();
    c.linsert_before(&k, "c", "b").await.unwrap();
    let all: Vec<String> = c.lrange(&k, 0, -1).await.unwrap();
    assert_eq!(all, vec!["a", "b", "c"]);
});

matrix_test!(linsert_missing_pivot_returns_neg1, c, {
    let k = common::key("l");
    c.rpush(&k, "a").await.unwrap();
    let n: isize = c.linsert_after(&k, "zzz", "x").await.unwrap();
    assert_eq!(n, -1);
});

matrix_test!(lmove, c, {
    let src = common::tkey("ls", "src");
    let dst = common::tkey("ls", "dst");
    c.rpush(&src, &["a", "b", "c"]).await.unwrap();
    let moved: String = c
        .lmove(&src, &dst, glide::Direction::Left, glide::Direction::Right)
        .await
        .unwrap();
    assert_eq!(moved, "a");
    let dst_items: Vec<String> = c.lrange(&dst, 0, -1).await.unwrap();
    assert_eq!(dst_items[0], "a");
});

matrix_test!(blmove, c, {
    let src = common::tkey("ls", "src");
    let dst = common::tkey("ls", "dst");
    c.rpush(&src, &["a", "b", "c"]).await.unwrap();
    let moved: Option<String> = c
        .blmove(
            &src,
            &dst,
            glide::Direction::Left,
            glide::Direction::Right,
            0.1,
        )
        .await
        .unwrap();
    assert_eq!(moved.as_deref(), Some("a"));
    let dst_items: Vec<String> = c.lrange(&dst, 0, -1).await.unwrap();
    assert_eq!(dst_items, vec!["a".to_string()]);
});

matrix_test!(blpop, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b"]).await.unwrap();
    let popped: Option<[String; 2]> = c.blpop(&k, 0.1).await.unwrap();
    assert_eq!(popped, Some([k.clone(), "a".to_string()]));
});

matrix_test!(brpop, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b"]).await.unwrap();
    let popped: Option<[String; 2]> = c.brpop(&k, 0.1).await.unwrap();
    assert_eq!(popped, Some([k.clone(), "b".to_string()]));
});

matrix_test!(brpoplpush, c, {
    let src = common::tkey("ls", "src");
    let dst = common::tkey("ls", "dst");
    c.rpush(&src, &["a", "b"]).await.unwrap();
    let moved: Option<String> = c.brpoplpush(&src, &dst, 0.1).await.unwrap();
    assert_eq!(moved.as_deref(), Some("b"));
    let dst_items: Vec<String> = c.lrange(&dst, 0, -1).await.unwrap();
    assert_eq!(dst_items, vec!["b".to_string()]);
});

matrix_test!(lpos, c, {
    let k = common::key("l");
    c.rpush(&k, &["a", "b", "c", "b"]).await.unwrap();
    let pos: Option<i64> = c
        .lpos(&k, "b", glide::LposOptions::default())
        .await
        .unwrap();
    assert_eq!(pos, Some(1));
    let pos: Option<i64> = c
        .lpos(&k, "missing", glide::LposOptions::default())
        .await
        .unwrap();
    assert_eq!(pos, None);
});

matrix_test!(list_wrong_type_errors, c, {
    let k = common::key("wt");
    c.set(&k, "notalist").await.unwrap();
    let res: glide::ValkeyResult<usize> = c.lpush(&k, "x").await;
    assert!(res.is_err());
});

matrix_test!(lmpop, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::tkey("cmd_lmpop", "l1");
    c.rpush(&k, &["a", "b"]).await.unwrap();

    let popped: Option<(String, Vec<String>)> =
        c.lmpop(1, &k, glide::Direction::Left, 2).await.unwrap();

    let (key, elements) = popped.unwrap();
    assert_eq!(key, k);
    assert_eq!(elements, vec!["a".to_string(), "b".to_string()]);
});

matrix_test!(blmpop, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::tkey("cmd_blmpop", "l1");
    c.rpush(&k, &["a", "b", "c"]).await.unwrap();

    let popped: Option<(String, Vec<String>)> = c
        .blmpop(0.1, 1, &k, glide::Direction::Right, 2)
        .await
        .unwrap();

    let (key, elements) = popped.unwrap();
    assert_eq!(key, k);
    assert_eq!(elements, vec!["c".to_string(), "b".to_string()]);

    let empty = common::tkey("cmd_blmpop", "empty");
    let popped: Option<(String, Vec<String>)> = c
        .blmpop(0.1, 1, &empty, glide::Direction::Left, 1)
        .await
        .unwrap();
    assert_eq!(popped, None);
});
