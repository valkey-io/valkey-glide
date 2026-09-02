// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! SCAN full-iteration correctness tests (RESP2 + RESP3).
//!
//! Each test runs against a fresh server, so a full SCAN sees exactly the keys
//! this test inserted.

mod common;

use glide::AsyncCommands;
use glide::commands::options::ObjectType;
use std::collections::HashSet;

/// Drive SCAN to completion using raw commands (cursor + MATCH + COUNT + TYPE)
/// through the typed owned-send escape hatch, collecting every returned key.
async fn scan_all<C>(
    c: &C,
    pattern: Option<&str>,
    count: Option<i64>,
    type_filter: Option<ObjectType>,
) -> HashSet<Vec<u8>>
where
    C: AsyncCommands,
{
    let mut cursor: u64 = 0;
    let mut seen = HashSet::new();
    loop {
        let mut cmd = redis::cmd("SCAN");
        cmd.arg(cursor);
        if let Some(p) = pattern {
            cmd.arg("MATCH").arg(p);
        }
        if let Some(cnt) = count {
            cmd.arg("COUNT").arg(cnt);
        }
        if let Some(ref t) = type_filter {
            cmd.arg("TYPE").arg(match t {
                ObjectType::String => "string",
                ObjectType::List => "list",
                ObjectType::Set => "set",
                ObjectType::ZSet => "zset",
                ObjectType::Hash => "hash",
                ObjectType::Stream => "stream",
            });
        }
        let result: (u64, Vec<Vec<u8>>) = c.glide_send(cmd).await.unwrap();
        for k in result.1 {
            seen.insert(k);
        }
        if result.0 == 0 {
            break;
        }
        cursor = result.0;
    }
    seen
}

resp_test!(scan_full_iteration, c, {
    let prefix = common::key("scan");
    let mut expected = HashSet::new();
    for i in 0..200 {
        let k = format!("{prefix}:{i}");
        let _: () = c.set(&k, "v").await.unwrap();
        expected.insert(k.into_bytes());
    }
    // Small COUNT forces multiple round-trips.
    let seen = scan_all(&c, None, Some(10), None).await;
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
    let seen = scan_all(&c, Some(&pattern), Some(5), None).await;
    assert_eq!(seen.len(), 20);
    assert!(
        seen.iter()
            .all(|k| String::from_utf8_lossy(k).contains(":keep:"))
    );
});

resp_test!(scan_count_hint, c, {
    let prefix = common::key("cnt");
    for i in 0..50 {
        let _: () = c.set(format!("{prefix}:{i}"), "v").await.unwrap();
    }
    // A large COUNT should still return all keys across iteration.
    let pattern = format!("{prefix}:*");
    let seen = scan_all(&c, Some(&pattern), Some(1000), None).await;
    assert_eq!(seen.len(), 50);
});

resp_test!(scan_type_filter, c, {
    let prefix = common::key("t");
    for i in 0..10 {
        let _: () = c.set(format!("{prefix}:str:{i}"), "v").await.unwrap();
    }
    for i in 0..10 {
        let _: i64 = c.rpush(format!("{prefix}:list:{i}"), &["a"]).await.unwrap();
    }
    let pattern = format!("{prefix}:*");
    let strings = scan_all(&c, Some(&pattern), Some(20), Some(ObjectType::String)).await;
    assert!(
        strings
            .iter()
            .all(|k| String::from_utf8_lossy(k).contains(":str:"))
    );
    assert_eq!(strings.len(), 10);
});

resp_test!(scan_empty_keyspace, c, {
    // A pattern that matches nothing returns no keys and terminates.
    let pattern = common::key("nomatch");
    let seen = scan_all(&c, Some(&pattern), Some(10), None).await;
    assert!(seen.is_empty());
});
