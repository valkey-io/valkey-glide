// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Regression tests for extension methods restored after the unified-API
//! command audit: `zadd_incr` and `zrank_withscore` / `zrevrank_withscore`.

mod common;

use glide::{AsyncTypedCommands, SortedSetCommands};

matrix_test!(zadd_incr_conditional_increment, c, {
    let k = common::key("rest_zi");
    let _: usize = c.zadd(&k, "m", 1.0).await.unwrap();
    // ZADD ... INCR: increments and returns the new score.
    let v = c.zadd_incr(&k, "m", 2.5).await.unwrap();
    assert_eq!(v, Some(3.5));
});

matrix_test!(zrank_withscore_variants, c, {
    skip_if_version_below!(c, 7, 2, 0);
    let k = common::key("rest_zr");
    let _: usize = c
        .zadd_multiple(&k, &[(1.0, "a"), (2.0, "b")])
        .await
        .unwrap();
    let r = c.zrank_withscore(&k, "b").await.unwrap();
    assert_eq!(r, Some((1, 2.0)));
    let r = c.zrevrank_withscore(&k, "b").await.unwrap();
    assert_eq!(r, Some((0, 2.0)));
    let none = c.zrank_withscore(&k, "missing").await.unwrap();
    assert_eq!(none, None);
});
