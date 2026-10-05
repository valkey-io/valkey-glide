// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the hash command family.

use super::Mock;
use crate::ValkeyValue;
use crate::commands::hash::HashCommands;
use bytes::Bytes;

#[tokio::test]
async fn hstrlen_encoding() {
    let m = Mock::int(4);
    assert_eq!(m.hstrlen("h", "f").await.unwrap(), 4);
    m.assert_args(&["HSTRLEN", "h", "f"]);
}

#[tokio::test]
async fn hrandfield_variants() {
    let m = Mock::bulk("f1");
    assert_eq!(
        m.hrandfield("h").await.unwrap(),
        Some(Bytes::from_static(b"f1"))
    );
    m.assert_args(&["HRANDFIELD", "h"]);

    let m = Mock::array(vec![ValkeyValue::BulkString(b"f1".to_vec().into())]);
    m.hrandfield_count("h", 2).await.unwrap();
    m.assert_args(&["HRANDFIELD", "h", "2"]);

    let m = Mock::array(vec![
        ValkeyValue::BulkString(b"f1".to_vec().into()),
        ValkeyValue::BulkString(b"v1".to_vec().into()),
    ]);
    let pairs = m.hrandfield_withvalues("h", 1).await.unwrap();
    m.assert_args(&["HRANDFIELD", "h", "1", "WITHVALUES"]);
    assert_eq!(
        pairs,
        vec![(Bytes::from_static(b"f1"), Bytes::from_static(b"v1"))]
    );
}
