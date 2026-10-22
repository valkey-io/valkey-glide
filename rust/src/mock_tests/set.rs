// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the set command family.
use super::Mock;
use crate::commands::set::{SIsMemberStatusCode, SetCommands};

#[tokio::test]
async fn sintercard_variants() {
    let m = Mock::int(2);
    assert_eq!(m.sintercard(&["s1", "s2"]).await.unwrap(), 2);
    m.assert_args(&["SINTERCARD", "2", "s1", "s2"]);

    let m = Mock::int(1);
    m.sintercard_limit(&["s1", "s2"], 1).await.unwrap();
    m.assert_args(&["SINTERCARD", "2", "s1", "s2", "LIMIT", "1"]);
}

#[tokio::test]
async fn sismember_if_exists() {
    let m = Mock::int(-1);
    assert_eq!(
        m.sismember_if_exists("s", "m").await.unwrap(),
        SIsMemberStatusCode::KeyNotFound
    );
    m.assert_args(&["SISMEMBER", "s", "m", "XX"]);
}
