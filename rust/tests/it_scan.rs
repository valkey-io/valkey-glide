// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! SCAN full-iteration correctness tests (RESP2 + RESP3).
//!
//! Each test runs against a fresh server, so a full SCAN sees exactly the keys
//! this test inserted.

mod common;

use glide::AsyncTypedCommands;
use std::collections::HashSet;

resp_test!(scan_full_iteration, c, {
    let prefix = common::key("scan");
    let mut expected = HashSet::new();
    for i in 0..200 {
        let k = format!("{prefix}:{i}");
        let _: () = c.set(&k, "v").await.unwrap();
        expected.insert(k.into_bytes());
    }
    let seen = scan_all(&c, None).await;
    // Every inserted key must appear (SCAN may return duplicates, but the set
    // must be a superset of what we inserted).
    for k in &expected {
        assert!(seen.contains(k), "missing key from SCAN");
    }
});

resp_test!(scan_match_pattern, c, {
    let prefix = common::key("m");
    for i in 0..20 {
        let _: () = c.set(format!("{prefix}:keep:{i}"), "v").await.unwrap();
        let _: () = c.set(format!("{prefix}:skip:{i}"), "v").await.unwrap();
    }
    let pattern = format!("{prefix}:keep:*");
    let seen = scan_all(&c, Some(&pattern)).await;
    assert_eq!(seen.len(), 20);
    assert!(
        seen.iter()
            .all(|k| String::from_utf8_lossy(k).contains(":keep:"))
    );
});

resp_test!(scan_empty_keyspace, c, {
    // A pattern that matches nothing returns no keys and terminates.
    let pattern = common::key("nomatch");
    let seen = scan_all(&c, Some(&pattern)).await;
    assert!(seen.is_empty());
});

matrix_test!(hscan, c, {
    let k = common::key("hscan");
    for i in 0..20 {
        let _: usize = c.hset(&k, format!("keep:{i}"), i).await.unwrap();
        let _: usize = c.hset(&k, format!("skip:{i}"), i).await.unwrap();
    }

    let all: HashSet<(String, String)> = collect(c.hscan(&k).await.unwrap()).await;
    assert_eq!(all.len(), 40);
    assert!(all.contains(&("keep:3".to_string(), "3".to_string())));

    let kept: HashSet<(String, String)> = collect(c.hscan_match(&k, "keep:*").await.unwrap()).await;
    assert_eq!(kept.len(), 20);
    assert!(kept.iter().all(|(field, _)| field.starts_with("keep:")));
});

matrix_test!(sscan, c, {
    let k = common::key("sscan");
    for i in 0..20 {
        let _: usize = c.sadd(&k, format!("keep:{i}")).await.unwrap();
        let _: usize = c.sadd(&k, format!("skip:{i}")).await.unwrap();
    }

    let all: HashSet<String> = collect(c.sscan(&k).await.unwrap()).await;
    assert_eq!(all.len(), 40);

    let kept: HashSet<String> = collect(c.sscan_match(&k, "keep:*").await.unwrap()).await;
    assert_eq!(kept.len(), 20);
    assert!(kept.iter().all(|member| member.starts_with("keep:")));
});

matrix_test!(zscan, c, {
    let k = common::key("zscan");
    for i in 0..20 {
        let _: usize = c
            .zadd(&k, format!("keep:{i}"), i as f64 + 0.5)
            .await
            .unwrap();
        let _: usize = c.zadd(&k, format!("skip:{i}"), i as f64).await.unwrap();
    }

    let all: Vec<(String, f64)> = collect(c.zscan(&k).await.unwrap()).await;
    assert_eq!(all.len(), 40);
    assert!(all.contains(&("keep:3".to_string(), 3.5)));

    let kept: Vec<(String, f64)> = collect(c.zscan_match(&k, "keep:*").await.unwrap()).await;
    assert_eq!(kept.len(), 20);
    assert!(kept.iter().all(|(member, _)| member.starts_with("keep:")));
});

/// Drive a key scan to completion, collecting every returned item.
async fn collect<C, RV, Out>(mut iter: glide::commands::scan::ScanIter<'_, C, RV>) -> Out
where
    C: glide::AsyncCommands,
    RV: glide::FromValkeyValue,
    Out: Default + Extend<RV>,
{
    let mut out = Out::default();
    while let Some(item) = iter.next_item().await {
        out.extend([item.unwrap()]);
    }
    out
}

/// Drive SCAN to completion, collecting every returned key.
// TODO #7060: cover COUNT/TYPE once `scan_options` is implemented.
async fn scan_all<C: AsyncTypedCommands>(c: &C, pattern: Option<&str>) -> HashSet<Vec<u8>> {
    let mut iter = match pattern {
        Some(p) => AsyncTypedCommands::scan_match(c, p).await.unwrap(),
        None => AsyncTypedCommands::scan(c).await.unwrap(),
    };
    let mut seen = HashSet::new();
    while let Some(k) = iter.next_item().await {
        seen.insert(k.unwrap());
    }
    seen
}
