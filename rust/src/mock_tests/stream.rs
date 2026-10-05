// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the stream command family.
use super::Mock;
use crate::ValkeyValue;
use crate::commands::stream::StreamCommands;
use crate::commands::stream::StreamGroupCreateOptions;
use crate::commands::stream::StreamReadGroupOptions;

fn entry(id: &str, field: &str, val: &str) -> ValkeyValue {
    ValkeyValue::Array(vec![
        ValkeyValue::BulkString(id.as_bytes().to_vec().into()),
        ValkeyValue::Array(vec![
            ValkeyValue::BulkString(field.as_bytes().to_vec().into()),
            ValkeyValue::BulkString(val.as_bytes().to_vec().into()),
        ]),
    ])
}

#[tokio::test]
async fn xtrim_variants() {
    let m = Mock::int(1);
    m.xtrim_maxlen("s", 100, true).await.unwrap();
    m.assert_args(&["XTRIM", "s", "MAXLEN", "~", "100"]);

    let m = Mock::int(1);
    m.xtrim_maxlen("s", 100, false).await.unwrap();
    m.assert_args(&["XTRIM", "s", "MAXLEN", "100"]);

    let m = Mock::int(1);
    m.xtrim_minid("s", "1-0", false).await.unwrap();
    m.assert_args(&["XTRIM", "s", "MINID", "1-0"]);
}

#[tokio::test]
async fn xreadgroup_encoding() {
    let m = Mock::array(vec![ValkeyValue::Array(vec![
        ValkeyValue::BulkString(b"s".to_vec().into()),
        ValkeyValue::Array(vec![entry("1-0", "f", "v")]),
    ])]);
    let opts = StreamReadGroupOptions {
        block_ms: None,
        count: Some(5),
        no_ack: true,
    };
    m.xreadgroup("g", "c", &[("s", ">")], Some(opts))
        .await
        .unwrap();
    m.assert_args(&[
        "XREADGROUP",
        "GROUP",
        "g",
        "c",
        "COUNT",
        "5",
        "NOACK",
        "STREAMS",
        "s",
        ">",
    ]);
}

#[tokio::test]
async fn xautoclaim_and_justid() {
    let m = Mock::array(vec![
        ValkeyValue::BulkString(b"0-0".to_vec().into()),
        ValkeyValue::Array(vec![entry("1-0", "f", "v")]),
        ValkeyValue::Array(vec![]),
    ]);
    let (cursor, entries, deleted) = m
        .xautoclaim("s", "g", "c", 0, "0-0", Some(10))
        .await
        .unwrap();
    m.assert_args(&["XAUTOCLAIM", "s", "g", "c", "0", "0-0", "COUNT", "10"]);
    assert_eq!(cursor, "0-0");
    assert_eq!(entries.len(), 1);
    assert!(deleted.is_empty());

    let m = Mock::array(vec![
        ValkeyValue::BulkString(b"0-0".to_vec().into()),
        ValkeyValue::Array(vec![ValkeyValue::BulkString(b"1-0".to_vec().into())]),
        ValkeyValue::Array(vec![]),
    ]);
    let (_, ids, _) = m
        .xautoclaim_justid("s", "g", "c", 0, "0-0", None)
        .await
        .unwrap();
    m.assert_args(&["XAUTOCLAIM", "s", "g", "c", "0", "0-0", "JUSTID"]);
    assert_eq!(ids, vec!["1-0".to_string()]);
}

#[tokio::test]
async fn xsetid_encoding() {
    let m = Mock::ok();
    m.xsetid("s", "5-0", Some(10), Some("4-0")).await.unwrap();
    m.assert_args(&[
        "XSETID",
        "s",
        "5-0",
        "ENTRIESADDED",
        "10",
        "MAXDELETEDID",
        "4-0",
    ]);
}

#[tokio::test]
async fn xgroup_option_variants() {
    let m = Mock::ok();
    let opts = StreamGroupCreateOptions {
        make_stream: true,
        entries_read: Some(0),
    };
    m.xgroup_create_options("s", "g", "$", &opts).await.unwrap();
    m.assert_args(&[
        "XGROUP",
        "CREATE",
        "s",
        "g",
        "$",
        "MKSTREAM",
        "ENTRIESREAD",
        "0",
    ]);

    let m = Mock::int(1);
    assert!(m.xgroup_create_consumer("s", "g", "c").await.unwrap());
    m.assert_args(&["XGROUP", "CREATECONSUMER", "s", "g", "c"]);

    let m = Mock::int(3);
    assert_eq!(m.xgroup_del_consumer("s", "g", "c").await.unwrap(), 3);
    m.assert_args(&["XGROUP", "DELCONSUMER", "s", "g", "c"]);

    let m = Mock::ok();
    m.xgroup_set_id("s", "g", "0", None).await.unwrap();
    m.assert_args(&["XGROUP", "SETID", "s", "g", "0"]);
}

#[tokio::test]
async fn xpending_range_encoding() {
    let m = Mock::array(vec![ValkeyValue::Array(vec![
        ValkeyValue::BulkString(b"1-0".to_vec().into()),
        ValkeyValue::BulkString(b"c1".to_vec().into()),
        ValkeyValue::Int(100),
        ValkeyValue::Int(3),
    ])]);
    let entries = m
        .xpending_range("s", "g", "-", "+", 10, Some(50), Some("c1"))
        .await
        .unwrap();
    m.assert_args(&["XPENDING", "s", "g", "IDLE", "50", "-", "+", "10", "c1"]);
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].delivery_count, 3);
}
