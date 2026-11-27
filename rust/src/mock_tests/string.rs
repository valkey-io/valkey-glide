// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor mock-executor tests for the string command family.
use super::Mock;
use crate::commands::string::StringCommands;

#[tokio::test]
async fn lcs_len_encoding() {
    let m = Mock::int(3);
    assert_eq!(m.lcs_len("k1", "k2").await.unwrap(), 3);
    m.assert_args(&["LCS", "k1", "k2", "LEN"]);
}

#[tokio::test]
async fn del_if_eq_encoding() {
    let m = Mock::int(1);
    assert!(m.del_if_eq("k", "v").await.unwrap());
    m.assert_args(&["DELIFEQ", "k", "v"]);

    let m = Mock::int(0);
    assert!(!m.del_if_eq("k", "v").await.unwrap());
}
