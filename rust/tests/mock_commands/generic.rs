// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the generic (key) command family.
use super::Mock;
use glide::commands::generic::GenericCommands;
use glide::commands::options::{Limit, OrderBy, RestoreOptions};
use redis::Value;

#[tokio::test]
async fn copy_variants() {
    let m = Mock::int(1);
    assert!(m.copy("src", "dst", false).await.unwrap());
    m.assert_args(&["COPY", "src", "dst"]);

    let m = Mock::int(1);
    m.copy("src", "dst", true).await.unwrap();
    m.assert_args(&["COPY", "src", "dst", "REPLACE"]);

    let m = Mock::int(1);
    m.copy_with_options("src", "dst", Some(2), true)
        .await
        .unwrap();
    m.assert_args(&["COPY", "src", "dst", "DB", "2", "REPLACE"]);
}

#[tokio::test]
async fn sort_and_sort_store_and_ro() {
    let m = Mock::array(vec![Value::BulkString(b"1".to_vec())]);
    m.sort(
        "k",
        Some(OrderBy::Desc),
        Some(Limit {
            offset: 0,
            count: 10,
        }),
        true,
    )
    .await
    .unwrap();
    m.assert_args(&["SORT", "k", "LIMIT", "0", "10", "DESC", "ALPHA"]);

    let m = Mock::int(3);
    m.sort_store("k", "dst", None, None, false).await.unwrap();
    m.assert_args(&["SORT", "k", "STORE", "dst"]);

    let m = Mock::array(vec![Value::BulkString(b"1".to_vec())]);
    m.sort_ro("k", Some(OrderBy::Asc), None, false)
        .await
        .unwrap();
    m.assert_args(&["SORT_RO", "k", "ASC"]);
}

#[tokio::test]
async fn restore_encoding() {
    let m = Mock::ok();
    let opts = RestoreOptions {
        replace: true,
        absttl: false,
        idletime: Some(5),
        frequency: None,
    };
    m.restore("k", 0, "payload", opts).await.unwrap();
    m.assert_args(&["RESTORE", "k", "0", "payload", "REPLACE", "IDLETIME", "5"]);
}

#[tokio::test]
async fn wait_move_encoding() {
    let m = Mock::int(2);
    assert_eq!(m.wait(2, 100).await.unwrap(), 2);
    m.assert_args(&["WAIT", "2", "100"]);

    let m = Mock::int(1);
    assert!(m.move_key("k", 1).await.unwrap());
    m.assert_args(&["MOVE", "k", "1"]);
}

#[tokio::test]
async fn watch_unwatch() {
    let m = Mock::ok();
    m.watch(&["k1", "k2"]).await.unwrap();
    m.assert_args(&["WATCH", "k1", "k2"]);

    let m = Mock::ok();
    m.unwatch().await.unwrap();
    m.assert_args(&["UNWATCH"]);
}
