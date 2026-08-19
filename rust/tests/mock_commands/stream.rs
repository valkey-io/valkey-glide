// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the stream command family.
use super::Mock;
use glide::commands::stream::{
    StreamAddOptions, StreamCommands, StreamGroupCreateOptions, StreamReadGroupOptions,
    StreamReadOptions, StreamTrimOptions,
};
use redis::Value;

fn entry(id: &str, field: &str, val: &str) -> Value {
    Value::Array(vec![
        Value::BulkString(id.as_bytes().to_vec()),
        Value::Array(vec![
            Value::BulkString(field.as_bytes().to_vec()),
            Value::BulkString(val.as_bytes().to_vec()),
        ]),
    ])
}

#[tokio::test]
async fn xadd_encoding() {
    let m = Mock::bulk("1526919030474-0");
    let id = m
        .xadd("s", "*", &[("f1", "v1"), ("f2", "v2")])
        .await
        .unwrap();
    m.assert_args(&["XADD", "s", "*", "f1", "v1", "f2", "v2"]);
    assert_eq!(id.as_deref(), Some("1526919030474-0"));
}

#[tokio::test]
async fn xadd_options_encoding() {
    let m = Mock::bulk("1-0");
    let opts = StreamAddOptions {
        make_stream: false,
        trim: Some(StreamTrimOptions::max_len(true, 5, None)),
    };
    m.xadd_options("s", "*", &[("f", "v")], &opts)
        .await
        .unwrap();
    m.assert_args(&["XADD", "s", "NOMKSTREAM", "MAXLEN", "=", "5", "*", "f", "v"]);
}

#[tokio::test]
async fn xlen_xdel() {
    let m = Mock::int(3);
    assert_eq!(m.xlen("s").await.unwrap(), 3);
    m.assert_args(&["XLEN", "s"]);

    let m = Mock::int(2);
    assert_eq!(m.xdel("s", &["1-0", "2-0"]).await.unwrap(), 2);
    m.assert_args(&["XDEL", "s", "1-0", "2-0"]);
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
async fn xrange_xrevrange() {
    let m = Mock::array(vec![entry("1-0", "f", "v")]);
    let entries = m.xrange("s", "-", "+").await.unwrap();
    m.assert_args(&["XRANGE", "s", "-", "+"]);
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].0, "1-0");
    assert_eq!(entries[0].1.len(), 1);

    let m = Mock::array(vec![entry("1-0", "f", "v")]);
    m.xrevrange("s", "+", "-").await.unwrap();
    m.assert_args(&["XREVRANGE", "s", "+", "-"]);
}

#[tokio::test]
async fn xgroup_create_destroy_ack() {
    let m = Mock::ok();
    m.xgroup_create("s", "g", "$", true).await.unwrap();
    m.assert_args(&["XGROUP", "CREATE", "s", "g", "$", "MKSTREAM"]);

    let m = Mock::int(1);
    assert!(m.xgroup_destroy("s", "g").await.unwrap());
    m.assert_args(&["XGROUP", "DESTROY", "s", "g"]);

    let m = Mock::int(2);
    assert_eq!(m.xack("s", "g", &["1-0", "2-0"]).await.unwrap(), 2);
    m.assert_args(&["XACK", "s", "g", "1-0", "2-0"]);
}

#[tokio::test]
async fn xread_and_options() {
    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"s".to_vec()),
        Value::Array(vec![entry("1-0", "f", "v")]),
    ])]);
    let res = m.xread(&[("s", "0")], None).await.unwrap();
    m.assert_args(&["XREAD", "STREAMS", "s", "0"]);
    assert_eq!(res.len(), 1);

    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"s".to_vec()),
        Value::Array(vec![entry("1-0", "f", "v")]),
    ])]);
    let opts = StreamReadOptions {
        block_ms: Some(100),
        count: Some(10),
    };
    m.xread(&[("s", "0")], Some(opts)).await.unwrap();
    m.assert_args(&["XREAD", "BLOCK", "100", "COUNT", "10", "STREAMS", "s", "0"]);
}

#[tokio::test]
async fn xreadgroup_encoding() {
    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"s".to_vec()),
        Value::Array(vec![entry("1-0", "f", "v")]),
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
async fn xclaim_and_justid() {
    let m = Mock::array(vec![entry("1-0", "f", "v")]);
    m.xclaim("s", "g", "c", 0, &["1-0"], None).await.unwrap();
    m.assert_args(&["XCLAIM", "s", "g", "c", "0", "1-0"]);

    let m = Mock::array(vec![Value::BulkString(b"1-0".to_vec())]);
    let ids = m
        .xclaim_justid("s", "g", "c", 0, &["1-0"], None)
        .await
        .unwrap();
    m.assert_args(&["XCLAIM", "s", "g", "c", "0", "1-0", "JUSTID"]);
    assert_eq!(ids, vec!["1-0".to_string()]);
}

#[tokio::test]
async fn xautoclaim_and_justid() {
    let m = Mock::array(vec![
        Value::BulkString(b"0-0".to_vec()),
        Value::Array(vec![entry("1-0", "f", "v")]),
        Value::Array(vec![]),
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
        Value::BulkString(b"0-0".to_vec()),
        Value::Array(vec![Value::BulkString(b"1-0".to_vec())]),
        Value::Array(vec![]),
    ]);
    let (_, ids, _) = m
        .xautoclaim_justid("s", "g", "c", 0, "0-0", None)
        .await
        .unwrap();
    m.assert_args(&["XAUTOCLAIM", "s", "g", "c", "0", "0-0", "JUSTID"]);
    assert_eq!(ids, vec!["1-0".to_string()]);
}

#[tokio::test]
async fn xpending_summary_and_range() {
    let m = Mock::array(vec![
        Value::Int(2),
        Value::BulkString(b"1-0".to_vec()),
        Value::BulkString(b"2-0".to_vec()),
        Value::Array(vec![Value::Array(vec![
            Value::BulkString(b"c1".to_vec()),
            Value::Int(2),
        ])]),
    ]);
    let summary = m.xpending("s", "g").await.unwrap();
    m.assert_args(&["XPENDING", "s", "g"]);
    assert_eq!(summary.count, 2);
    assert_eq!(summary.consumers.len(), 1);

    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"1-0".to_vec()),
        Value::BulkString(b"c1".to_vec()),
        Value::Int(100),
        Value::Int(3),
    ])]);
    let entries = m
        .xpending_range("s", "g", "-", "+", 10, Some(50), Some("c1"))
        .await
        .unwrap();
    m.assert_args(&["XPENDING", "s", "g", "IDLE", "50", "-", "+", "10", "c1"]);
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].delivery_count, 3);
}

#[tokio::test]
async fn xinfo_stream_groups_consumers() {
    let m = Mock::array(vec![Value::BulkString(b"length".to_vec()), Value::Int(5)]);
    let info = m.xinfo_stream("s").await.unwrap();
    m.assert_args(&["XINFO", "STREAM", "s"]);
    assert_eq!(info[0].0.as_ref(), b"length");

    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"name".to_vec()),
        Value::BulkString(b"g1".to_vec()),
    ])]);
    let groups = m.xinfo_groups("s").await.unwrap();
    m.assert_args(&["XINFO", "GROUPS", "s"]);
    assert_eq!(groups.len(), 1);

    let m = Mock::array(vec![Value::Array(vec![
        Value::BulkString(b"name".to_vec()),
        Value::BulkString(b"c1".to_vec()),
    ])]);
    m.xinfo_consumers("s", "g").await.unwrap();
    m.assert_args(&["XINFO", "CONSUMERS", "s", "g"]);
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
