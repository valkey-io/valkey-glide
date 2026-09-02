// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Integration tests for dynamic connection-password management
//! (`update_connection_password`).
//!
//! Strategy: boot an unprotected server, set `requirepass` at runtime via
//! `CONFIG SET` (which does not de-authenticate the live connection), then drive
//! the client's stored password with `update_connection_password` and assert the
//! immediate-`AUTH` behaviour. `requirepass` is cleared again before drop.

mod common;

use common::TestServer;
use glide::{AsyncCommands, ConnectionManagementCommands, CustomCommand};

const NEW_PASS: &str = "rotated-p4ss";

#[tokio::test]
async fn update_password_immediate_auth_succeeds() {
    let srv = match TestServer::start() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let client = srv.client().await;
    assert_eq!(client.ping().await.unwrap(), "PONG");

    // Turn on auth at runtime; the live connection stays authenticated.
    client
        .custom_command(&["CONFIG", "SET", "requirepass", NEW_PASS])
        .await
        .unwrap();

    // Rotate the client's stored password and re-AUTH immediately.
    client
        .update_connection_password(Some(NEW_PASS.to_string()), true)
        .await
        .expect("immediate re-auth with the correct password should succeed");

    // Commands still work after the rotation.
    let _: () = client.set("pw-k", "v").await.unwrap();
    let got: Option<glide::Bytes> = client.get("pw-k").await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));

    // Clear auth so the server drops cleanly.
    client
        .update_connection_password(None, false)
        .await
        .unwrap();
    client
        .custom_command(&["CONFIG", "SET", "requirepass", ""])
        .await
        .unwrap();
}

#[tokio::test]
async fn update_password_immediate_auth_wrong_password_errors() {
    let srv = match TestServer::start() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let client = srv.client().await;
    client
        .custom_command(&["CONFIG", "SET", "requirepass", NEW_PASS])
        .await
        .unwrap();

    // Wrong password with immediate AUTH must surface an error.
    let res = client
        .update_connection_password(Some("not-the-password".to_string()), true)
        .await;
    assert!(
        res.is_err(),
        "immediate AUTH with a wrong password must error"
    );

    // Recover: correct password, then clear.
    client
        .update_connection_password(Some(NEW_PASS.to_string()), true)
        .await
        .unwrap();
    client
        .custom_command(&["CONFIG", "SET", "requirepass", ""])
        .await
        .unwrap();
}

#[tokio::test]
async fn update_password_store_only_is_ok_without_auth() {
    let srv = match TestServer::start() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let client = srv.client().await;
    // Storing a password without immediate auth is a no-op on the wire and must
    // succeed even against an unprotected server.
    client
        .update_connection_password(Some("staged".to_string()), false)
        .await
        .unwrap();
    // Clearing it again is likewise fine.
    client
        .update_connection_password(None, false)
        .await
        .unwrap();
    assert_eq!(client.ping().await.unwrap(), "PONG");
}

#[cfg(feature = "sync")]
#[test]
fn sync_update_password_store_only() {
    use glide::GlideClientConfiguration;
    use glide::sync::SyncGlideClient;

    let srv = match TestServer::start() {
        Some(s) => s,
        None => {
            eprintln!("SKIP: no valkey-server binary available");
            return;
        }
    };
    let config = GlideClientConfiguration::with_address("127.0.0.1", srv.port);
    let client = SyncGlideClient::connect(config).expect("connect");
    client
        .update_connection_password(Some("staged".to_string()), false)
        .expect("store-only password update should succeed");
    client
        .update_connection_password(None, false)
        .expect("clearing password should succeed");
    assert_eq!(client.ping().unwrap(), "PONG");
}

#[tokio::test]
async fn cluster_update_password_store_only() {
    let cluster = match common::ClusterHarness::start() {
        Some(cl) => cl,
        None => {
            eprintln!("SKIP: cluster harness unavailable");
            return;
        }
    };
    let client = match cluster.client().await {
        Some(c) => c,
        None => {
            eprintln!("SKIP: cluster client connect failed");
            return;
        }
    };
    // Store-then-clear on the cluster client (no server-side requirepass dance):
    // exercises the cluster update_connection_password path end to end.
    client
        .update_connection_password(Some("staged".to_string()), false)
        .await
        .unwrap();
    client
        .update_connection_password(None, false)
        .await
        .unwrap();
    assert_eq!(client.ping().await.unwrap(), "PONG");
}
