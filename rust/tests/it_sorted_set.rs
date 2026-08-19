// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command sorted-set integration tests (RESP2 + RESP3).

mod common;

use glide::commands::sorted_set::{LexBound, ScoreBound};
use glide::{AsyncCommands, SortedSetCommands};

matrix_test!(zadd_zcard, c, {
    let k = common::key("z");
    // zadd_multiple takes &[(score, member)]
    let added: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    assert_eq!(added, 3);
    // Re-adding updates score, returns 0 new.
    let added: i64 = c.zadd_multiple(&k, &[(10.0, "a")]).await.unwrap();
    assert_eq!(added, 0);
    let card: i64 = c.zcard(&k).await.unwrap();
    assert_eq!(card, 3);
});

matrix_test!(zcard_missing_zero, c, {
    let card: i64 = c.zcard(common::key("z")).await.unwrap();
    assert_eq!(card, 0);
});

matrix_test!(zadd_incr, c, {
    let k = common::key("z");
    // zadd(key, member, score) — member before score!
    let _: i64 = c.zadd(&k, "a", 1.0).await.unwrap();
    // zincr(key, member, delta)
    let v: f64 = c.zincr(&k, "a", 4.0).await.unwrap();
    assert_eq!(v, 5.0);
});

matrix_test!(zrem, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let removed: i64 = c.zrem(&k, &["a", "missing"]).await.unwrap();
    assert_eq!(removed, 1);
    let card: i64 = c.zcard(&k).await.unwrap();
    assert_eq!(card, 1);
});

matrix_test!(zscore, c, {
    let k = common::key("z");
    let _: i64 = c.zadd(&k, "a", 1.5).await.unwrap();
    let s: Option<f64> = c.zscore(&k, "a").await.unwrap();
    assert_eq!(s, Some(1.5));
    let s: Option<f64> = c.zscore(&k, "missing").await.unwrap();
    assert_eq!(s, None);
});

matrix_test!(zmscore, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let scores: Vec<Option<f64>> = c.zscore_multiple(&k, &["a", "x", "b"]).await.unwrap();
    assert_eq!(scores, vec![Some(1.0), None, Some(2.0)]);
});

matrix_test!(zcount, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let n: i64 = c.zcount(&k, "-inf", "+inf").await.unwrap();
    assert_eq!(n, 3);
    let n: i64 = c.zcount(&k, "2", "+inf").await.unwrap();
    assert_eq!(n, 2);
    let n: i64 = c.zcount(&k, "(2", "+inf").await.unwrap();
    assert_eq!(n, 1);
});

matrix_test!(zlexcount, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(0.0, "a"), (0.0, "b"), (0.0, "d")])
        .await
        .unwrap();
    let n: i64 = c.zlexcount(&k, "-", "+").await.unwrap();
    assert_eq!(n, 3);
    let n: i64 = c.zlexcount(&k, "[b", "+").await.unwrap();
    assert_eq!(n, 2);
});

matrix_test!(zrange_by_index, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let asc: Vec<String> = c.zrange(&k, 0, -1).await.unwrap();
    assert_eq!(asc, vec!["a", "b", "d"]);
    let rev: Vec<String> = c.zrevrange(&k, 0, -1).await.unwrap();
    assert_eq!(rev, vec!["d", "b", "a"]);
});

matrix_test!(zrange_withscores, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let ws: Vec<(String, f64)> = c.zrange_withscores(&k, 0, -1).await.unwrap();
    assert_eq!(ws.len(), 2);
    assert_eq!(ws[0].0, "a");
    assert_eq!(ws[0].1, 1.0);
    assert_eq!(ws[1].1, 2.0);
});

matrix_test!(zrangebyscore, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let r: Vec<String> = c.zrangebyscore(&k, "2", "+inf").await.unwrap();
    assert_eq!(r, vec!["b", "d"]);
});

matrix_test!(zrank_zrevrank, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let r: Option<i64> = c.zrank(&k, "a").await.unwrap();
    assert_eq!(r, Some(0));
    let r: Option<i64> = c.zrank(&k, "d").await.unwrap();
    assert_eq!(r, Some(2));
    let r: Option<i64> = c.zrevrank(&k, "d").await.unwrap();
    assert_eq!(r, Some(0));
    let r: Option<i64> = c.zrank(&k, "missing").await.unwrap();
    assert_eq!(r, None);
});

matrix_test!(zincrby, c, {
    let k = common::key("z");
    let _: i64 = c.zadd(&k, "a", 1.0).await.unwrap();
    let v: f64 = c.zincr(&k, "a", 5.0).await.unwrap();
    assert!((v - 6.0).abs() < 1e-9);
    // ZINCRBY on missing member creates it.
    let v: f64 = c.zincr(&k, "new", 2.0).await.unwrap();
    assert!((v - 2.0).abs() < 1e-9);
});

matrix_test!(zpopmin_zpopmax, c, {
    let k = common::key("z");
    let _: i64 = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let min: Vec<(String, f64)> = c.zpopmin(&k, 1).await.unwrap();
    assert_eq!(min[0].0, "a");
    assert_eq!(min[0].1, 1.0);
    let max: Vec<(String, f64)> = c.zpopmax(&k, 1).await.unwrap();
    assert_eq!(max[0].0, "d");
    assert_eq!(max[0].1, 3.0);
});

matrix_test!(zpopmin_empty, c, {
    let r: Vec<(String, f64)> = c.zpopmin(common::key("z"), 1).await.unwrap();
    assert!(r.is_empty());
});

matrix_test!(zrandmember, c, {
    let k = common::key("z");
    let _: i64 = c.zadd(&k, "only", 1.0).await.unwrap();
    let v: Option<String> = c.zrandmember(&k, None).await.unwrap();
    assert_eq!(v.as_deref(), Some("only"));
    let v: Option<String> = c.zrandmember(common::key("x"), None).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(zunionstore, c, {
    let z1 = common::tkey("zs", "z1");
    let z2 = common::tkey("zs", "z2");
    let dst = common::tkey("zs", "dst");
    let _: i64 = c
        .zadd_multiple(&z1, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let _: i64 = c
        .zadd_multiple(&z2, &[(10.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    // zunionstore_max for MAX aggregation
    let n: i64 = c.zunionstore_max(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 3);
    let s: Option<f64> = c.zscore(&dst, "b").await.unwrap();
    assert_eq!(s, Some(10.0));
});

matrix_test!(zinterstore, c, {
    let z1 = common::tkey("zs", "z1");
    let z2 = common::tkey("zs", "z2");
    let dst = common::tkey("zs", "dst");
    let _: i64 = c
        .zadd_multiple(&z1, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let _: i64 = c
        .zadd_multiple(&z2, &[(10.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    // zunionstore (SUM) is the default
    let n: i64 = c.zinterstore(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 1);
    let s: Option<f64> = c.zscore(&dst, "b").await.unwrap();
    assert_eq!(s, Some(12.0));
});

matrix_test!(zset_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: () = c.set(&k, "notazset").await.unwrap();
    let res: redis::RedisResult<i64> = c.zadd(&k, "a", 1.0).await;
    assert!(res.is_err());
});

matrix_test!(zrangestore_by_score_stores_count, c, {
    let src = common::tkey("zrss", "src");
    let dst = common::tkey("zrss", "dst");
    let _: i64 = c
        .zadd_multiple(&src, &[(1.0, "a"), (2.0, "b"), (3.0, "c")])
        .await
        .unwrap();
    let n = c
        .zrangestore_by_score(
            &dst,
            &src,
            ScoreBound::Inclusive(1.0),
            ScoreBound::Inclusive(2.0),
            false,
            None,
        )
        .await
        .unwrap();
    assert_eq!(n, 2);
    let card: i64 = c.zcard(&dst).await.unwrap();
    assert_eq!(card, 2);
});

matrix_test!(zrangestore_by_lex_stores_count, c, {
    let src = common::tkey("zrsl", "src");
    let dst = common::tkey("zrsl", "dst");
    // Equal scores => well-defined lexicographic ordering.
    let _: i64 = c
        .zadd_multiple(&src, &[(0.0, "a"), (0.0, "b"), (0.0, "c")])
        .await
        .unwrap();
    let n = c
        .zrangestore_by_lex(
            &dst,
            &src,
            &LexBound::NegativeInfinity,
            &LexBound::PositiveInfinity,
            false,
            None,
        )
        .await
        .unwrap();
    assert_eq!(n, 3);
    let card: i64 = c.zcard(&dst).await.unwrap();
    assert_eq!(card, 3);
});
