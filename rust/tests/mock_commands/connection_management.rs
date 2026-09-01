// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the connection-management command family.
use super::Mock;
use bytes::Bytes;
use glide::commands::connection_management::ConnectionManagementCommands;

#[tokio::test]
async fn ping_returns_pong() {
    let m = Mock::simple("PONG");
    assert_eq!(m.ping().await.unwrap(), "PONG");
    m.assert_args(&["PING"]);
}

#[tokio::test]
async fn ping_message_echoes() {
    let m = Mock::bulk("hello");
    assert_eq!(
        m.ping_message("hello").await.unwrap(),
        Bytes::from_static(b"hello")
    );
    m.assert_args(&["PING", "hello"]);
}

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
async fn client_id_encoding() {
    let m = Mock::int(12345);
    assert_eq!(m.client_id().await.unwrap(), 12345);
    m.assert_args(&["CLIENT", "ID"]);
}

#[tokio::test]
async fn client_getname_present_and_absent() {
    let m = Mock::bulk("app");
    assert_eq!(
        m.client_getname().await.unwrap(),
        Some(Bytes::from_static(b"app"))
    );
    m.assert_args(&["CLIENT", "GETNAME"]);

    let m = Mock::nil();
    assert_eq!(m.client_getname().await.unwrap(), None);
}

#[tokio::test]
async fn client_setname_encoding() {
    let m = Mock::ok();
    m.client_setname("app-1").await.unwrap();
    m.assert_args(&["CLIENT", "SETNAME", "app-1"]);
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
