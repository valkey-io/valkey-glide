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
