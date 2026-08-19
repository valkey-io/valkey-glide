// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the Pub/Sub command family.
use super::Mock;
use bytes::Bytes;
use glide::commands::pubsub::PubSubCommands;
use redis::Value;

// NOTE: no `publish` test here — `PUBLISH` moved to the unified command table
// (`glide::AsyncCommands`), whose encoding delegates to the fork's own
// `Cmd::publish` constructor (covered by the signature-parity guard).

#[tokio::test]
async fn spublish_encoding() {
    let m = Mock::int(1);
    assert_eq!(m.spublish("sch", "hi").await.unwrap(), 1);
    m.assert_args(&["SPUBLISH", "sch", "hi"]);
}

#[tokio::test]
async fn pubsub_channels_with_and_without_pattern() {
    let m = Mock::array(vec![Value::BulkString(b"news".to_vec())]);
    let chans = m.pubsub_channels(Some(b"n*")).await.unwrap();
    m.assert_args(&["PUBSUB", "CHANNELS", "n*"]);
    assert_eq!(chans, vec![Bytes::from_static(b"news")]);

    let m = Mock::array(vec![]);
    let _ = m.pubsub_channels(None).await.unwrap();
    m.assert_args(&["PUBSUB", "CHANNELS"]);
}

#[tokio::test]
async fn pubsub_numpat_int() {
    let m = Mock::int(5);
    assert_eq!(m.pubsub_numpat().await.unwrap(), 5);
    m.assert_args(&["PUBSUB", "NUMPAT"]);
}

#[tokio::test]
async fn pubsub_numsub_pairs() {
    let m = Mock::array(vec![
        Value::BulkString(b"a".to_vec()),
        Value::Int(2),
        Value::BulkString(b"b".to_vec()),
        Value::Int(0),
    ]);
    let subs = m.pubsub_numsub(&["a", "b"]).await.unwrap();
    m.assert_args(&["PUBSUB", "NUMSUB", "a", "b"]);
    assert_eq!(
        subs,
        vec![(Bytes::from_static(b"a"), 2), (Bytes::from_static(b"b"), 0)]
    );
}

#[tokio::test]
async fn pubsub_shard_variants() {
    let m = Mock::array(vec![Value::BulkString(b"s".to_vec())]);
    let _ = m.pubsub_shardchannels(None).await.unwrap();
    m.assert_args(&["PUBSUB", "SHARDCHANNELS"]);

    let m = Mock::array(vec![Value::BulkString(b"s".to_vec()), Value::Int(1)]);
    let subs = m.pubsub_shardnumsub(&["s"]).await.unwrap();
    m.assert_args(&["PUBSUB", "SHARDNUMSUB", "s"]);
    assert_eq!(subs, vec![(Bytes::from_static(b"s"), 1)]);
}

#[tokio::test]
async fn subscribe_encodes_channels() {
    let m = Mock::nil();
    m.subscribe(&["c1", "c2"]).await.unwrap();
    m.assert_args(&["SUBSCRIBE", "c1", "c2"]);
}

#[tokio::test]
async fn psubscribe_encodes_patterns() {
    let m = Mock::nil();
    m.psubscribe(&["news.*"]).await.unwrap();
    m.assert_args(&["PSUBSCRIBE", "news.*"]);
}

#[tokio::test]
async fn ssubscribe_encodes_channels() {
    let m = Mock::nil();
    m.ssubscribe(&["shard1"]).await.unwrap();
    m.assert_args(&["SSUBSCRIBE", "shard1"]);
}

#[tokio::test]
async fn unsubscribe_all_has_no_channel_args() {
    let m = Mock::nil();
    m.unsubscribe(&[] as &[&str]).await.unwrap();
    m.assert_args(&["UNSUBSCRIBE"]);
}

#[tokio::test]
async fn punsubscribe_specific() {
    let m = Mock::nil();
    m.punsubscribe(&["news.*"]).await.unwrap();
    m.assert_args(&["PUNSUBSCRIBE", "news.*"]);
}

#[tokio::test]
async fn sunsubscribe_all() {
    let m = Mock::nil();
    m.sunsubscribe(&[] as &[&str]).await.unwrap();
    m.assert_args(&["SUNSUBSCRIBE"]);
}
