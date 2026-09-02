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

fn auth_server() -> Option<TestServer> {
    TestServer::start_with_args(&["--requirepass", PASSWORD])
}

#[tokio::test]
async fn auth_success_with_correct_password() {
    let srv = match auth_server() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let config = GlideClientConfiguration::with_address("127.0.0.1", srv.port)
        .credentials(ServerCredentials::password(PASSWORD));
    let client = srv.try_connect(config).await.expect("auth should succeed");
    assert_eq!(client.ping().await.unwrap(), "PONG");
    let _: () = client.set("k", "v").await.unwrap();
    let got: Option<glide::Bytes> = client.get("k").await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));
}

#[tokio::test]
async fn auth_failure_with_wrong_password() {
    let srv = match auth_server() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let config = GlideClientConfiguration::with_address("127.0.0.1", srv.port)
        .credentials(ServerCredentials::password("wrong-password"));
    // Either the connect fails during the auth handshake, or a subsequent
    // command fails with an auth error.
    match srv.try_connect(config).await {
        Err(_) => {} // expected
        Ok(client) => {
            let res = client.ping().await;
            assert!(res.is_err(), "wrong password must not allow commands");
        }
    }
}

#[tokio::test]
async fn no_credentials_fails_against_protected_server() {
    let srv = match auth_server() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let config = GlideClientConfiguration::with_address("127.0.0.1", srv.port);
    match srv.try_connect(config).await {
        Err(_) => {} // expected: NOAUTH during handshake
        Ok(client) => {
            let res = client.ping().await;
            assert!(res.is_err(), "no credentials must not allow commands");
        }
    }
}

#[tokio::test]
async fn auth_with_username_default_user() {
    let srv = match auth_server() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    // The built-in `default` user with the configured password.
    let config = GlideClientConfiguration::with_address("127.0.0.1", srv.port)
        .credentials(ServerCredentials::new("default", PASSWORD));
    let client = srv
        .try_connect(config)
        .await
        .expect("default-user auth should succeed");
    assert_eq!(client.ping().await.unwrap(), "PONG");
}
