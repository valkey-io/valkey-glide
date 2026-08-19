// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the search (`FT.*`) command family.
use super::Mock;
use bytes::Bytes;
use glide::commands::ft::FtCommands;
use redis::Value;

#[tokio::test]
async fn ft_create_encoding() {
    let m = Mock::ok();
    m.ft_create("idx", &["ON", "HASH", "SCHEMA", "title", "TEXT"])
        .await
        .unwrap();
    m.assert_args(&["FT.CREATE", "idx", "ON", "HASH", "SCHEMA", "title", "TEXT"]);
}

#[tokio::test]
async fn ft_dropindex_with_dd() {
    let m = Mock::ok();
    m.ft_dropindex("idx", true).await.unwrap();
    m.assert_args(&["FT.DROPINDEX", "idx", "DD"]);

    let m = Mock::ok();
    m.ft_dropindex("idx", false).await.unwrap();
    m.assert_args(&["FT.DROPINDEX", "idx"]);
}

#[tokio::test]
async fn ft_list_returns_names() {
    let m = Mock::array(vec![Value::BulkString(b"idx1".to_vec())]);
    let names = m.ft_list().await.unwrap();
    m.assert_args(&["FT._LIST"]);
    assert_eq!(names, vec![Bytes::from_static(b"idx1")]);
}

#[tokio::test]
async fn ft_search_encoding() {
    let m = Mock::array(vec![Value::Int(0)]);
    let _ = m
        .ft_search("idx", "*", &["LIMIT", "0", "10"])
        .await
        .unwrap();
    m.assert_args(&["FT.SEARCH", "idx", "*", "LIMIT", "0", "10"]);
}

#[tokio::test]
async fn ft_aggregate_encoding() {
    let m = Mock::array(vec![]);
    let _ = m
        .ft_aggregate("idx", "*", &["GROUPBY", "1", "@x"])
        .await
        .unwrap();
    m.assert_args(&["FT.AGGREGATE", "idx", "*", "GROUPBY", "1", "@x"]);
}

#[tokio::test]
async fn ft_alias_commands() {
    let m = Mock::ok();
    m.ft_aliasadd("a", "idx").await.unwrap();
    m.assert_args(&["FT.ALIASADD", "a", "idx"]);

    let m = Mock::ok();
    m.ft_aliasupdate("a", "idx2").await.unwrap();
    m.assert_args(&["FT.ALIASUPDATE", "a", "idx2"]);

    let m = Mock::ok();
    m.ft_aliasdel("a").await.unwrap();
    m.assert_args(&["FT.ALIASDEL", "a"]);
}

#[tokio::test]
async fn ft_profile_encoding() {
    let m = Mock::array(vec![]);
    let _ = m.ft_profile("idx", "SEARCH", true, &["*"]).await.unwrap();
    m.assert_args(&["FT.PROFILE", "idx", "SEARCH", "LIMITED", "QUERY", "*"]);
}
