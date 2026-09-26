// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Authentication integration tests.
//!
//! Boots a password-protected server (`--requirepass`) and asserts that the
//! correct credentials connect while wrong/absent credentials fail.

mod common;

use common::TestServer;
use glide::{
    AsyncCommands, ConnectionManagementCommands, GlideClientConfiguration, ServerCredentials,
};

const PASSWORD: &str = "s3cr3t-p4ss";

#[tokio::test]
async fn auth_success_with_correct_password() {
    let server = TestServer::start_with_args(&["--requirepass", PASSWORD]);
    let config = GlideClientConfiguration::with_address("127.0.0.1", server.port)
        .credentials(ServerCredentials::password(PASSWORD));
    let client = server
        .try_connect(config)
        .await
        .expect("auth should succeed");
    assert_eq!(client.ping().await.unwrap(), "PONG");
    let _: () = client.set("k", "v").await.unwrap();
    let got: Option<glide::Bytes> = client.get("k").await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));
}

#[tokio::test]
async fn auth_failure_with_wrong_password() {
    let server = TestServer::start_with_args(&["--requirepass", PASSWORD]);
    let config = GlideClientConfiguration::with_address("127.0.0.1", server.port)
        .credentials(ServerCredentials::password("wrong-password"));
    // Either the connect fails during the auth handshake, or a subsequent
    // command fails with an auth error.
    match server.try_connect(config).await {
        Err(_) => {} // expected
        Ok(client) => {
            let res = client.ping().await;
            assert!(res.is_err(), "wrong password must not allow commands");
        }
    }
}

#[tokio::test]
async fn no_credentials_fails_against_protected_server() {
    let server = TestServer::start_with_args(&["--requirepass", PASSWORD]);
    let config = GlideClientConfiguration::with_address("127.0.0.1", server.port);
    match server.try_connect(config).await {
        Err(_) => {} // expected: NOAUTH during handshake
        Ok(client) => {
            let res = client.ping().await;
            assert!(res.is_err(), "no credentials must not allow commands");
        }
    }
}

#[tokio::test]
async fn auth_with_username_default_user() {
    let server = TestServer::start_with_args(&["--requirepass", PASSWORD]);
    // The built-in `default` user with the configured password.
    let config = GlideClientConfiguration::with_address("127.0.0.1", server.port)
        .credentials(ServerCredentials::username_password("default", PASSWORD));
    let client = server
        .try_connect(config)
        .await
        .expect("default-user auth should succeed");
    assert_eq!(client.ping().await.unwrap(), "PONG");
}
