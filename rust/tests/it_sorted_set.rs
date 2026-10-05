// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command sorted-set integration tests (RESP2 + RESP3).

mod common;

use glide::commands::options::Limit;
use glide::commands::sorted_set::{LexBound, ScoreBound};
use glide::{AsyncTypedCommands, GlideError, SortedSetCommands};

matrix_test!(zadd_zcard, c, {
    let k = common::key("z");
    // zadd_multiple takes &[(score, member)]
    let added: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    assert_eq!(added, 3);

    // Re-adding updates score, returns 0 new.
    assert_eq!(c.zadd_multiple(&k, &[(10.0, "a")]).await.unwrap(), 0);
    assert_eq!(c.zcard(&k).await.unwrap(), 3);
});

matrix_test!(zcard_missing_zero, c, {
    assert_eq!(c.zcard(common::key("z")).await.unwrap(), 0);
});

matrix_test!(zadd_incr, c, {
    let k = common::key("z");
    // zadd(key, member, score) — member before score!
    let _: usize = c.zadd(&k, "a", 1.0).await.unwrap();
    // zincr(key, member, delta)
    let v: f64 = c.zincr(&k, "a", 4.0).await.unwrap();
    assert_eq!(v, 5.0);
});

matrix_test!(zrem, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let removed: usize = c.zrem(&k, &["a", "missing"]).await.unwrap();
    assert_eq!(removed, 1);
    let card: usize = c.zcard(&k).await.unwrap();
    assert_eq!(card, 1);
});

matrix_test!(zscore, c, {
    let k = common::key("z");
    let _: usize = c.zadd(&k, "a", 1.5).await.unwrap();
    let s: Option<f64> = c.zscore(&k, "a").await.unwrap();
    assert_eq!(s, Some(1.5));
    let s: Option<f64> = c.zscore(&k, "missing").await.unwrap();
    assert_eq!(s, None);
});

matrix_test!(zmscore, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let scores: Option<Vec<f64>> = c.zscore_multiple(&k, &["a", "b"]).await.unwrap();
    assert_eq!(scores, Some(vec![1.0, 2.0]));
});

// The Valkey server returns `nil` when a requested member does not exist.
// Typed `zscore_multiple` raises an error if this happens. Callers need
// to use the untyped version if they want to handle this case.
// This matches redis-rs behaviour.

matrix_test!(zmscore_typed_with_nil, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();

    let err = c.zscore_multiple(&k, &["a", "x", "b"]).await.unwrap_err();
    assert!(matches!(err, GlideError::Request(_)));
    assert!(err.message().contains("not convertible to f64"));
});

matrix_test!(zmscore_untyped_with_nil, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();

    let scores: Vec<Option<f64>> = glide::AsyncCommands::zscore_multiple(&c, &k, &["a", "x", "b"])
        .await
        .unwrap();
    assert_eq!(scores, vec![Some(1.0), None, Some(2.0)]);
});

matrix_test!(zcount, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let n: usize = c.zcount(&k, "-inf", "+inf").await.unwrap();
    assert_eq!(n, 3);
    let n: usize = c.zcount(&k, "2", "+inf").await.unwrap();
    assert_eq!(n, 2);
    let n: usize = c.zcount(&k, "(2", "+inf").await.unwrap();
    assert_eq!(n, 1);
});

matrix_test!(zlexcount, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(0.0, "a"), (0.0, "b"), (0.0, "d")])
        .await
        .unwrap();
    let n: usize = c.zlexcount(&k, "-", "+").await.unwrap();
    assert_eq!(n, 3);
    let n: usize = c.zlexcount(&k, "[b", "+").await.unwrap();
    assert_eq!(n, 2);
});

matrix_test!(zrange_by_index, c, {
    let k = common::key("z");
    let _: usize = c
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
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let ws: Vec<(String, f64)> = c.zrange_withscores(&k, 0, -1).await.unwrap();
    assert_eq!(ws.len(), 2);
    assert_eq!(ws[0].0, "a");
    assert_eq!(ws[0].1, 1.0);
    assert_eq!(ws[1].1, 2.0);
});

matrix_test!(zrangebyscore_withscores, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let r: Vec<(String, usize)> = c.zrangebyscore_withscores(&k, "2", "+inf").await.unwrap();
    assert_eq!(r, vec![("b".to_string(), 2), ("d".to_string(), 3)]);
    let r: Vec<(String, usize)> = c
        .zrangebyscore_limit_withscores(&k, "-inf", "+inf", 1, 1)
        .await
        .unwrap();
    assert_eq!(r, vec![("b".to_string(), 2)]);
});

// Valkey returns non-integer scores as floats. Typed `zrangebyscore_withscores`
// decodes scores as `usize` and raises an error if this happens. Callers need to use
// the untyped version if they want to handle this case. This matches redis-rs behaviour.

matrix_test!(zrangebyscore_withscores_typed_with_float, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.5, "b")])
        .await
        .unwrap();
    let err = c
        .zrangebyscore_withscores(&k, "-inf", "+inf")
        .await
        .unwrap_err();
    assert!(matches!(err, GlideError::Request(_)));
    assert!(err.message().contains("usize"));
});

matrix_test!(zrangebyscore_withscores_untyped_with_float, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.5, "b")])
        .await
        .unwrap();
    let r: Vec<(String, f64)> =
        glide::AsyncCommands::zrangebyscore_withscores(&c, &k, "-inf", "+inf")
            .await
            .unwrap();
    assert_eq!(r, vec![("a".to_string(), 1.0), ("b".to_string(), 2.5)]);
});

matrix_test!(zrevrange_withscores, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.5, "b")])
        .await
        .unwrap();
    let expected = vec![("b".to_string(), 2.5), ("a".to_string(), 1.0)];
    let r: Vec<(String, f64)> = c.zrevrange_withscores(&k, 0, -1).await.unwrap();
    assert_eq!(r, expected);
    let r: Vec<(String, f64)> = c
        .zrevrangebyscore_withscores(&k, "+inf", "-inf")
        .await
        .unwrap();
    assert_eq!(r, expected);
    let r: Vec<(String, f64)> = c
        .zrevrangebyscore_limit_withscores(&k, "+inf", "-inf", 1, 1)
        .await
        .unwrap();
    assert_eq!(r, vec![("a".to_string(), 1.0)]);
});

matrix_test!(zrangebyscore, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let r: Vec<String> = c.zrangebyscore(&k, "2", "+inf").await.unwrap();
    assert_eq!(r, vec!["b", "d"]);
});

matrix_test!(zrank_zrevrank, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    let r: Option<usize> = c.zrank(&k, "a").await.unwrap();
    assert_eq!(r, Some(0));
    let r: Option<usize> = c.zrank(&k, "d").await.unwrap();
    assert_eq!(r, Some(2));
    let r: Option<usize> = c.zrevrank(&k, "d").await.unwrap();
    assert_eq!(r, Some(0));
    let r: Option<usize> = c.zrank(&k, "missing").await.unwrap();
    assert_eq!(r, None);
});

matrix_test!(zincrby, c, {
    let k = common::key("z");
    let _: usize = c.zadd(&k, "a", 1.0).await.unwrap();
    let v: f64 = c.zincr(&k, "a", 5.0).await.unwrap();
    assert!((v - 6.0).abs() < 1e-9);
    // ZINCRBY on missing member creates it.
    let v: f64 = c.zincr(&k, "new", 2.0).await.unwrap();
    assert!((v - 2.0).abs() < 1e-9);
});

matrix_test!(zpopmin_zpopmax, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b"), (3.0, "d")])
        .await
        .unwrap();

    let min: Vec<(String, f64)> = c.zpopmin(&k, 1).await.unwrap();
    assert_eq!(min, vec![("a".to_string(), 1.0)]);

    let max: Vec<(String, f64)> = c.zpopmax(&k, 1).await.unwrap();
    assert_eq!(max, vec![("d".to_string(), 3.0)]);
});

matrix_test!(zpopmin_zpopmax_count, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.5, "b"), (3.0, "d"), (4.0, "e")])
        .await
        .unwrap();

    let min: Vec<(String, f64)> = c.zpopmin(&k, 2).await.unwrap();
    assert_eq!(min, vec![("a".to_string(), 1.0), ("b".to_string(), 2.5)]);

    let max: Vec<(String, f64)> = c.zpopmax(&k, 2).await.unwrap();
    assert_eq!(max, vec![("e".to_string(), 4.0), ("d".to_string(), 3.0)]);
});

matrix_test!(bzpopmin_bzpopmax, c, {
    let k = common::key("z");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.5, "b"), (3.0, "d")])
        .await
        .unwrap();

    let min: Option<(String, String, f64)> = c.bzpopmin(&k, 1.0).await.unwrap();
    assert_eq!(min, Some((k.clone(), "a".to_string(), 1.0)));

    let max: Option<(String, String, f64)> = c.bzpopmax(&k, 1.0).await.unwrap();
    assert_eq!(max, Some((k.clone(), "d".to_string(), 3.0)));
});

matrix_test!(bzpopmin_timeout, c, {
    let r: Option<(String, String, f64)> = c.bzpopmin(common::key("z"), 0.1).await.unwrap();
    assert_eq!(r, None);
});

matrix_test!(zpopmin_empty, c, {
    let r: Vec<(String, f64)> = c.zpopmin(common::key("z"), 1).await.unwrap();
    assert!(r.is_empty());
});

matrix_test!(zrandmember, c, {
    let k = common::key("z");
    let _: usize = c.zadd(&k, "only", 1.0).await.unwrap();
    let v: Option<String> = c.zrandmember(&k, None).await.unwrap();
    assert_eq!(v.as_deref(), Some("only"));
    let v: Option<String> = c.zrandmember(common::key("x"), None).await.unwrap();
    assert_eq!(v, None);
});

matrix_test!(zunionstore, c, {
    let (z1, z2, dst) = store_sources(&c, "zus").await;
    let n: usize = c.zunionstore(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 3);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(
        stored,
        vec![
            ("a".to_string(), 1.0),
            ("d".to_string(), 3.0),
            ("b".to_string(), 12.0)
        ]
    );
});

matrix_test!(zunionstore_min, c, {
    let (z1, z2, dst) = store_sources(&c, "zusmin").await;
    let n: usize = c.zunionstore_min(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 3);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(
        stored,
        vec![
            ("a".to_string(), 1.0),
            ("b".to_string(), 2.0),
            ("d".to_string(), 3.0)
        ]
    );
});

matrix_test!(zunionstore_max, c, {
    let (z1, z2, dst) = store_sources(&c, "zusmax").await;
    let n: usize = c.zunionstore_max(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 3);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(
        stored,
        vec![
            ("a".to_string(), 1.0),
            ("d".to_string(), 3.0),
            ("b".to_string(), 10.0)
        ]
    );
});

matrix_test!(zinterstore, c, {
    let (z1, z2, dst) = store_sources(&c, "zis").await;
    let n: usize = c.zinterstore(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 1);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(stored, vec![("b".to_string(), 12.0)]);
});

matrix_test!(zinterstore_min, c, {
    let (z1, z2, dst) = store_sources(&c, "zismin").await;
    let n: usize = c.zinterstore_min(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 1);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(stored, vec![("b".to_string(), 2.0)]);
});

matrix_test!(zinterstore_max, c, {
    let (z1, z2, dst) = store_sources(&c, "zismax").await;
    let n: usize = c.zinterstore_max(&dst, &[&z1, &z2]).await.unwrap();
    assert_eq!(n, 1);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(stored, vec![("b".to_string(), 10.0)]);
});

matrix_test!(zmpop_min, c, {
    skip_if_version_below!(c, 7, 0, 0);
    let (z1, z2) = mpop_sources(&c, "zmpmin").await;
    // Pops the lowest-scored members from the first non-empty key.
    let popped = c.zmpop_min(&[&z1, &z2], 2).await.unwrap();
    assert_eq!(
        popped,
        Some((
            z2.clone(),
            vec![("a".to_string(), 1.0), ("b".to_string(), 2.0)]
        ))
    );
    // Returns `None` when every key is empty.
    let popped = c.zmpop_min(&[&z1], 1).await.unwrap();
    assert_eq!(popped, None);
});

matrix_test!(zmpop_max, c, {
    skip_if_version_below!(c, 7, 0, 0);
    let (z1, z2) = mpop_sources(&c, "zmpmax").await;
    // Pops the highest-scored members from the first non-empty key.
    let popped = c.zmpop_max(&[&z1, &z2], 2).await.unwrap();
    assert_eq!(
        popped,
        Some((
            z2.clone(),
            vec![("d".to_string(), 4.0), ("c".to_string(), 3.0)]
        ))
    );
    // Returns `None` when every key is empty.
    let popped = c.zmpop_max(&[&z1], 1).await.unwrap();
    assert_eq!(popped, None);
});

matrix_test!(bzmpop_min, c, {
    skip_if_version_below!(c, 7, 0, 0);
    let (z1, z2) = mpop_sources(&c, "bzmpmin").await;
    // Pops the lowest-scored members from the first non-empty key.
    let popped = c.bzmpop_min(0.1, &[&z1, &z2], 2).await.unwrap();
    assert_eq!(
        popped,
        Some((
            z2.clone(),
            vec![("a".to_string(), 1.0), ("b".to_string(), 2.0)]
        ))
    );
    // Returns `None` when the timeout elapses with every key empty.
    let popped = c.bzmpop_min(0.1, &[&z1], 1).await.unwrap();
    assert_eq!(popped, None);
});

matrix_test!(bzmpop_max, c, {
    skip_if_version_below!(c, 7, 0, 0);
    let (z1, z2) = mpop_sources(&c, "bzmpmax").await;
    // Pops the highest-scored members from the first non-empty key.
    let popped = c.bzmpop_max(0.1, &[&z1, &z2], 2).await.unwrap();
    assert_eq!(
        popped,
        Some((
            z2.clone(),
            vec![("d".to_string(), 4.0), ("c".to_string(), 3.0)]
        ))
    );
    // Returns `None` when the timeout elapses with every key empty.
    let popped = c.bzmpop_max(0.1, &[&z1], 1).await.unwrap();
    assert_eq!(popped, None);
});

matrix_test!(zset_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: () = c.set(&k, "notazset").await.unwrap();
    let res: glide::ValkeyResult<usize> = c.zadd(&k, "a", 1.0).await;
    assert!(res.is_err());
});

matrix_test!(zrangestore_by_score, c, {
    let src = common::tkey("zrss", "src");
    let dst = common::tkey("zrss", "dst");
    let _: usize = c
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

    let card: usize = c.zcard(&dst).await.unwrap();
    assert_eq!(card, 2);

    let members: Vec<String> = c.zrange(&dst, 0, -1).await.unwrap();
    assert_eq!(members, vec!["a".to_string(), "b".to_string()]);
});

matrix_test!(zrangestore_by_score_rev, c, {
    let src = common::tkey("zrssrev", "src");
    let dst = common::tkey("zrssrev", "dst");
    let _: usize = c
        .zadd_multiple(&src, &[(1.0, "a"), (2.0, "b"), (3.0, "c")])
        .await
        .unwrap();
    let n = c
        .zrangestore_by_score(
            &dst,
            &src,
            ScoreBound::Inclusive(1.0),
            ScoreBound::Inclusive(2.0),
            true,
            None,
        )
        .await
        .unwrap();
    assert_eq!(n, 2);

    let card: usize = c.zcard(&dst).await.unwrap();
    assert_eq!(card, 2);

    let members: Vec<String> = c.zrange(&dst, 0, -1).await.unwrap();
    assert_eq!(members, vec!["a".to_string(), "b".to_string()]);
});

matrix_test!(zrangestore_by_score_limit, c, {
    let src = common::tkey("zrsslim", "src");
    let dst = common::tkey("zrsslim", "dst");
    let _: usize = c
        .zadd_multiple(&src, &[(1.0, "a"), (2.0, "b"), (3.0, "c")])
        .await
        .unwrap();
    let n = c
        .zrangestore_by_score(
            &dst,
            &src,
            ScoreBound::Inclusive(1.0),
            ScoreBound::Inclusive(3.0),
            false,
            Some(Limit {
                offset: 0,
                count: 2,
            }),
        )
        .await
        .unwrap();
    assert_eq!(n, 2);

    let card: usize = c.zcard(&dst).await.unwrap();
    assert_eq!(card, 2);

    let members: Vec<String> = c.zrange(&dst, 0, -1).await.unwrap();
    assert_eq!(members, vec!["a".to_string(), "b".to_string()]);
});

matrix_test!(zrangestore_by_lex_stores_count, c, {
    let src = common::tkey("zrsl", "src");
    let dst = common::tkey("zrsl", "dst");
    // Equal scores => well-defined lexicographic ordering.
    let _: usize = c
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
    let card: usize = c.zcard(&dst).await.unwrap();
    assert_eq!(card, 3);
});

matrix_test!(zunionstore_weights, c, {
    let (z1, z2, dst) = store_sources(&c, "zunionstore_weights").await;
    let n: usize = c
        .zunionstore_weights(&dst, &[(&z1, 2), (&z2, 1)])
        .await
        .unwrap();
    assert_eq!(n, 3);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(
        stored,
        vec![
            ("a".to_string(), 2.0),
            ("d".to_string(), 3.0),
            ("b".to_string(), 14.0)
        ]
    );
});

matrix_test!(zunionstore_min_weights, c, {
    let (z1, z2, dst) = store_sources(&c, "zunionstore_min_weights").await;
    let n: usize = c
        .zunionstore_min_weights(&dst, &[(&z1, 2), (&z2, 1)])
        .await
        .unwrap();
    assert_eq!(n, 3);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(
        stored,
        vec![
            ("a".to_string(), 2.0),
            ("d".to_string(), 3.0),
            ("b".to_string(), 4.0)
        ]
    );
});

matrix_test!(zunionstore_max_weights, c, {
    let (z1, z2, dst) = store_sources(&c, "zunionstore_max_weights").await;
    let n: usize = c
        .zunionstore_max_weights(&dst, &[(&z1, 2), (&z2, 1)])
        .await
        .unwrap();
    assert_eq!(n, 3);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(
        stored,
        vec![
            ("a".to_string(), 2.0),
            ("d".to_string(), 3.0),
            ("b".to_string(), 10.0)
        ]
    );
});

matrix_test!(zinterstore_weights, c, {
    let (z1, z2, dst) = store_sources(&c, "zinterstore_weights").await;
    let n: usize = c
        .zinterstore_weights(&dst, &[(&z1, 2), (&z2, 1)])
        .await
        .unwrap();
    assert_eq!(n, 1);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(stored, vec![("b".to_string(), 14.0)]);
});

matrix_test!(zinterstore_min_weights, c, {
    let (z1, z2, dst) = store_sources(&c, "zinterstore_min_weights").await;
    let n: usize = c
        .zinterstore_min_weights(&dst, &[(&z1, 2), (&z2, 1)])
        .await
        .unwrap();
    assert_eq!(n, 1);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(stored, vec![("b".to_string(), 4.0)]);
});

matrix_test!(zinterstore_max_weights, c, {
    let (z1, z2, dst) = store_sources(&c, "zinterstore_max_weights").await;
    let n: usize = c
        .zinterstore_max_weights(&dst, &[(&z1, 2), (&z2, 1)])
        .await
        .unwrap();
    assert_eq!(n, 1);
    let stored: Vec<(String, f64)> = c.zrange_withscores(&dst, 0, -1).await.unwrap();
    assert_eq!(stored, vec![("b".to_string(), 10.0)]);
});

matrix_test!(zrandmember_withscores, c, {
    let k = common::key("z");
    let _: usize = c.zadd(&k, "only", 1.5).await.unwrap();
    let v: Vec<(String, f64)> = c.zrandmember_withscores(&k, 1).await.unwrap();
    assert_eq!(v, vec![("only".to_string(), 1.5)]);
});

matrix_test!(zrangebylex, c, {
    let k = lex_source(&c).await;
    let r: Vec<String> = c.zrangebylex(&k, "[b", "+").await.unwrap();
    assert_eq!(r, vec!["b", "c", "d"]);
});

matrix_test!(zrangebylex_limit, c, {
    let k = lex_source(&c).await;
    let r: Vec<String> = c.zrangebylex_limit(&k, "[b", "+", 1, 1).await.unwrap();
    assert_eq!(r, vec!["c"]);
});

matrix_test!(zrangebyscore_limit, c, {
    let k = score_source(&c).await;
    let r: Vec<String> = c
        .zrangebyscore_limit(&k, "-inf", "+inf", 1, 2)
        .await
        .unwrap();
    assert_eq!(r, vec!["b", "c"]);
});

matrix_test!(zrevrangebylex, c, {
    let k = lex_source(&c).await;
    let r: Vec<String> = c.zrevrangebylex(&k, "+", "[b").await.unwrap();
    assert_eq!(r, vec!["d", "c", "b"]);
});

matrix_test!(zrevrangebylex_limit, c, {
    let k = lex_source(&c).await;
    let r: Vec<String> = c.zrevrangebylex_limit(&k, "+", "[b", 0, 2).await.unwrap();
    assert_eq!(r, vec!["d", "c"]);
});

matrix_test!(zrevrangebyscore, c, {
    let k = score_source(&c).await;
    let r: Vec<String> = c.zrevrangebyscore(&k, "+inf", "2").await.unwrap();
    assert_eq!(r, vec!["d", "c", "b"]);
});

matrix_test!(zrevrangebyscore_limit, c, {
    let k = score_source(&c).await;
    let r: Vec<String> = c
        .zrevrangebyscore_limit(&k, "+inf", "2", 0, 2)
        .await
        .unwrap();
    assert_eq!(r, vec!["d", "c"]);
});

matrix_test!(zrembylex, c, {
    let k = lex_source(&c).await;
    let n: usize = c.zrembylex(&k, "[a", "[b").await.unwrap();
    assert_eq!(n, 2);
    let r: Vec<String> = c.zrange(&k, 0, -1).await.unwrap();
    assert_eq!(r, vec!["c", "d"]);
});

matrix_test!(zrembyscore, c, {
    let k = score_source(&c).await;
    let n: usize = c.zrembyscore(&k, "2", "3").await.unwrap();
    assert_eq!(n, 2);
    let r: Vec<String> = c.zrange(&k, 0, -1).await.unwrap();
    assert_eq!(r, vec!["a", "d"]);
});

matrix_test!(zremrangebyrank, c, {
    let k = score_source(&c).await;
    let n: usize = c.zremrangebyrank(&k, 0, 1).await.unwrap();
    assert_eq!(n, 2);
    let r: Vec<String> = c.zrange(&k, 0, -1).await.unwrap();
    assert_eq!(r, vec!["c", "d"]);
});

/// Creates `{a: 0, b: 0, c: 0, d: 0}`, returning its key.
async fn lex_source<C: AsyncTypedCommands>(c: &C) -> String {
    let k = common::key("z");
    let _: usize =
        AsyncTypedCommands::zadd_multiple(c, &k, &[(0.0, "a"), (0.0, "b"), (0.0, "c"), (0.0, "d")])
            .await
            .unwrap();
    k
}

/// Creates `{a: 1, b: 2, c: 3, d: 4}`, returning its key.
async fn score_source<C: AsyncTypedCommands>(c: &C) -> String {
    let k = common::key("z");
    let _: usize =
        AsyncTypedCommands::zadd_multiple(c, &k, &[(1.0, "a"), (2.0, "b"), (3.0, "c"), (4.0, "d")])
            .await
            .unwrap();
    k
}

/// Creates `z1 = {a: 1, b: 2}` and `z2 = {b: 10, d: 3}` in one slot,
/// returning `(z1, z2, dst)`.
async fn store_sources<C: AsyncTypedCommands>(c: &C, tag: &str) -> (String, String, String) {
    let z1 = common::tkey(tag, "z1");
    let z2 = common::tkey(tag, "z2");
    let dst = common::tkey(tag, "dst");
    let _: usize = AsyncTypedCommands::zadd_multiple(c, &z1, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let _: usize = AsyncTypedCommands::zadd_multiple(c, &z2, &[(10.0, "b"), (3.0, "d")])
        .await
        .unwrap();
    (z1, z2, dst)
}

/// Creates an empty `z1` and `z2 = {a: 1, b: 2, c: 3, d: 4}` in one slot,
/// returning `(z1, z2)`.
async fn mpop_sources<C: AsyncTypedCommands>(c: &C, tag: &str) -> (String, String) {
    let z1 = common::tkey(tag, "z1");
    let z2 = common::tkey(tag, "z2");
    let _: usize = AsyncTypedCommands::zadd_multiple(
        c,
        &z2,
        &[(1.0, "a"), (2.0, "b"), (3.0, "c"), (4.0, "d")],
    )
    .await
    .unwrap();
    (z1, z2)
}
