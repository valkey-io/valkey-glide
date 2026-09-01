// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Regression tests for extension methods restored after the unified-API
//! command audit: `zadd_incr`, `zrank_withscore` / `zrevrank_withscore`, and
//! the cursor-style `scan_cursor` (MATCH/COUNT/TYPE).

mod common;

use glide::{AsyncCommands, GenericCommands, SortedSetCommands};

matrix_test!(zadd_incr_conditional_increment, c, {
    let k = common::key("rest_zi");
    let _: () = c.zadd(&k, "m", 1.0).await.unwrap();
    // ZADD ... INCR: increments and returns the new score.
    let v = c.zadd_incr(&k, "m", 2.5).await.unwrap();
    assert_eq!(v, Some(3.5));
});

matrix_test!(zrank_withscore_variants, c, {
    if common::version_below(&c, (7, 2, 0)).await {
        return;
    }
    let k = common::key("rest_zr");
    let _: () = c
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

resp_test!(scan_cursor_with_count_and_type, c, {
    let prefix = common::key("rest_scan");
    for i in 0..10 {
        let _: () = c.set(format!("{prefix}:s:{i}"), i).await.unwrap();
        let _: () = c.zadd(format!("{prefix}:z:{i}"), "m", 1.0).await.unwrap();
    }
    // TYPE filter: only the zsets match.
    let mut cursor = "0".to_string();
    let mut found = 0usize;
    loop {
        let (next, keys) = c
            .scan_cursor(
                &cursor,
                Some(format!("{prefix}:*").as_bytes()),
                Some(100),
                Some(glide::ObjectType::ZSet),
            )
            .await
            .unwrap();
        found += keys.len();
        if next == "0" {
            break;
        }
        cursor = next;
    }
    assert_eq!(
        found, 10,
        "TYPE zset filter must match exactly the 10 zsets"
    );
});
