// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command connection-management integration tests (RESP2 + RESP3).

mod common;

use glide::{AsyncCommands, ConnectionManagementCommands};

resp_test!(ping, c, {
    assert_eq!(c.ping().await.unwrap(), "PONG");
});

resp_test!(ping_message, c, {
    assert_eq!(c.ping_message("hello").await.unwrap().as_ref(), b"hello");
});

resp_test!(echo, c, {
    assert_eq!(c.echo("round-trip").await.unwrap().as_ref(), b"round-trip");
});

resp_test!(echo_binary, c, {
    let payload = vec![0u8, 1, 2, 255];
    assert_eq!(
        c.echo(payload.clone()).await.unwrap().as_ref(),
        &payload[..]
    );
});

resp_test!(client_id_positive, c, {
    assert!(c.client_id().await.unwrap() > 0);
});

resp_test!(client_setname_getname, c, {
    c.client_setname("myconn").await.unwrap();
    assert_eq!(
        c.client_getname().await.unwrap().as_deref(),
        Some(&b"myconn"[..])
    );
});

resp_test!(select_database, c, {
    // SELECT another DB then operate there.
    c.select(1).await.unwrap();
    let k = common::key("k");
    let _: () = c.set(&k, "v").await.unwrap();
    let got: Option<glide::Bytes> = c.get(&k).await.unwrap();
    assert_eq!(got.as_deref(), Some(&b"v"[..]));
    c.select(0).await.unwrap();
});
