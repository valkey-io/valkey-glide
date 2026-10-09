// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the custom-command escape hatches.
use super::Mock;
use crate::Route;
use crate::executor::CustomCommand;

#[tokio::test]
async fn custom_command_encodes_each_arg() {
    let m = Mock::ok();
    m.custom_command(&["SET", "k", "v"]).await.unwrap();
    m.assert_args(&["SET", "k", "v"]);
    assert!(m.routing().is_none());
}

#[tokio::test]
async fn custom_command_with_route_passes_route() {
    let m = Mock::simple("PONG");
    m.custom_command_with_route(&["PING"], Route::RandomNode)
        .await
        .unwrap();
    m.assert_args(&["PING"]);
    assert!(matches!(m.routing(), Some(Route::RandomNode)));
}
