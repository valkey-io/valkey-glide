// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command server-management integration tests (RESP2 + RESP3).

mod common;

use glide::FlushAllOptions;
use glide::FlushDbOptions;
use glide::{AsyncTypedCommands, ServerManagementCommands};

// TODO #7362: Change to `matrix_test!` once the typed `INFO` methods return per-node replies.
resp_test!(info_returns_expected_fields, c, {
    // Default fields
    for info in common::get_info(&c, &[]).await {
        for field in common::INFO_DEFAULT_FIELDS {
            assert!(info.contains_key(field));
        }
    }

    // Valkey 9.2+ fields
    // ------------------

    if common::version_below(&c, (9, 2, 0)).await {
        return;
    }

    // Persistence fields
    for persistence in common::get_info(&c, &["persistence"]).await {
        assert!(common::BGSAVE_TYPES.contains(&persistence["rdb_current_bgsave_type"].as_str()));
        assert!(common::BGSAVE_TYPES.contains(&persistence["rdb_last_bgsave_type"].as_str()));

        let remaining_name = "forkless_estimated_seconds_remaining";
        let remaining_value = common::parse_int(&persistence[remaining_name], remaining_name);
        assert!(remaining_value >= -1);
    }

    // Cluster info fields
    // TODO #7362: Uncomment once the typed `INFO` methods return per-node replies.
    // let is_cluster = (&c as &dyn std::any::Any).is::<glide::GlideClusterClient>();
    // if is_cluster {
    //     for cluster_info in common::get_info(&c, &["cluster_info"]).await {
    //         let fallbacks_name = "cluster_io_main_thread_fallbacks";
    //         let fallbacks_value = common::parse_int(&cluster_info[fallbacks_name], fallbacks_name);
    //         assert!(fallbacks_value >= 0);
    //     }
    // }
});

resp_test!(dbsize, c, {
    // Fresh server: start empty, add keys, count grows.
    let before = c.dbsize().await.unwrap();
    c.set(common::key("k"), "v").await.unwrap();
    assert!(c.dbsize().await.unwrap() > before);
});

resp_test!(flushdb, c, {
    c.set(common::key("k"), "v").await.unwrap();
    c.flushdb().await.unwrap();
    assert_eq!(c.dbsize().await.unwrap(), 0);
});

resp_test!(flushdb_options, c, {
    c.set(common::key("k"), "v").await.unwrap();
    let options = FlushDbOptions::default().blocking(true);
    c.flushdb_options(&options).await.unwrap();
    assert_eq!(c.dbsize().await.unwrap(), 0);
});

resp_test!(flushall, c, {
    c.set(common::key("k"), "v").await.unwrap();
    c.flushall().await.unwrap();
    assert_eq!(c.dbsize().await.unwrap(), 0);
});

resp_test!(flushall_options, c, {
    c.set(common::key("k"), "v").await.unwrap();
    c.flushall_options(&FlushAllOptions::default())
        .await
        .unwrap();
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
