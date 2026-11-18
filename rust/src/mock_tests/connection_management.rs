// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the connection-management command family.
use super::Mock;
use crate::commands::connection_management::ConnectionManagementCommands;
use bytes::Bytes;

#[tokio::test]
async fn echo_encoding() {
    let m = Mock::bulk("msg");
    assert_eq!(m.echo("msg").await.unwrap(), Bytes::from_static(b"msg"));
    m.assert_args(&["ECHO", "msg"]);
}

#[tokio::test]
async fn select_encoding() {
    let m = Mock::ok();
    m.select(2).await.unwrap();
    m.assert_args(&["SELECT", "2"]);
}

#[tokio::test]
async fn client_no_evict_on_off() {
    let m = Mock::ok();
    m.client_no_evict(true).await.unwrap();
    m.assert_args(&["CLIENT", "NO-EVICT", "ON"]);

    let m = Mock::ok();
    m.client_no_evict(false).await.unwrap();
    m.assert_args(&["CLIENT", "NO-EVICT", "OFF"]);
}

#[tokio::test]
async fn client_no_touch_on_off() {
    let m = Mock::ok();
    m.client_no_touch(true).await.unwrap();
    m.assert_args(&["CLIENT", "NO-TOUCH", "ON"]);

    let m = Mock::ok();
    m.client_no_touch(false).await.unwrap();
    m.assert_args(&["CLIENT", "NO-TOUCH", "OFF"]);
}

#[tokio::test]
async fn reset_encoding() {
    let m = Mock::ok();
    m.reset().await.unwrap();
    m.assert_args(&["RESET"]);
}
