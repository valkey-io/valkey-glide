// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor mock-executor tests for the string command family.
use super::Mock;
use glide::commands::string::StringCommands;

#[tokio::test]
async fn lcs_len_encoding() {
    let m = Mock::int(3);
    assert_eq!(m.lcs_len("k1", "k2").await.unwrap(), 3);
    m.assert_args(&["LCS", "k1", "k2", "LEN"]);
}
