// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command server-management integration tests (RESP2 + RESP3).

mod common;

use glide::commands::options::FlushMode;
use glide::{AsyncCommands, ServerManagementCommands};

resp_test!(info_non_empty, c, {
    let info = c.info().await.unwrap();
    assert!(!info.is_empty());
    // INFO always contains the server section header.
    let text = String::from_utf8_lossy(&info);
    assert!(
        text.contains("redis_version")
            || text.contains("valkey_version")
            || text.contains("# Server")
    );
});

resp_test!(info_sections, c, {
    let info = c.info_sections(&["server"]).await.unwrap();
    assert!(!info.is_empty());
});

resp_test!(dbsize, c, {
    // Fresh server: start empty, add keys, count grows.
    let before = c.dbsize().await.unwrap();
    c.set::<_, _, ()>(common::key("k"), "v").await.unwrap();
    assert!(c.dbsize().await.unwrap() > before);
});

resp_test!(flushdb, c, {
    c.set::<_, _, ()>(common::key("k"), "v").await.unwrap();
    c.flushdb(Some(FlushMode::Sync)).await.unwrap();
    assert_eq!(c.dbsize().await.unwrap(), 0);
});

resp_test!(flushall, c, {
    c.set::<_, _, ()>(common::key("k"), "v").await.unwrap();
    c.flushall(None).await.unwrap();
    assert_eq!(c.dbsize().await.unwrap(), 0);
});

resp_test!(config_get, c, {
    let cfg = c.config_get("maxmemory").await.unwrap();
    assert!(cfg.contains_key("maxmemory"));
});

resp_test!(config_set_get_roundtrip, c, {
    c.config_set("maxmemory-policy", "allkeys-lru")
        .await
        .unwrap();
    let cfg = c.config_get("maxmemory-policy").await.unwrap();
    assert_eq!(
        cfg.get("maxmemory-policy").map(|b| b.as_ref()),
        Some(&b"allkeys-lru"[..])
    );
});

resp_test!(config_resetstat, c, {
    c.config_resetstat().await.unwrap();
});

resp_test!(time, c, {
    let (secs, micros) = c.time().await.unwrap();
    assert!(secs > 1_600_000_000); // after 2020
    assert!((0..1_000_000).contains(&micros));
});

resp_test!(lastsave, c, {
    assert!(c.lastsave().await.unwrap() > 0);
});
