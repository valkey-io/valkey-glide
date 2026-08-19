// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the sorted-set command family.
use super::Mock;
use glide::commands::sorted_set::{AggregationType, LexBound, ScoreBound, SortedSetCommands};
use redis::Value;

#[tokio::test]
async fn zdiff_zunion_zinter() {
    let m = Mock::array(vec![Value::BulkString(b"m1".to_vec())]);
    m.zdiff(&["z1", "z2"]).await.unwrap();
    m.assert_args(&["ZDIFF", "2", "z1", "z2"]);

    let m = Mock::array(vec![
        Value::BulkString(b"m1".to_vec()),
        Value::BulkString(b"1.5".to_vec()),
    ]);
    m.zdiff_withscores(&["z1", "z2"]).await.unwrap();
    m.assert_args(&["ZDIFF", "2", "z1", "z2", "WITHSCORES"]);

    let m = Mock::array(vec![Value::BulkString(b"m1".to_vec())]);
    m.zunion(&["z1", "z2"], Some(AggregationType::Sum))
        .await
        .unwrap();
    m.assert_args(&["ZUNION", "2", "z1", "z2", "AGGREGATE", "SUM"]);

    let m = Mock::array(vec![Value::BulkString(b"m1".to_vec())]);
    m.zinter(&["z1", "z2"], None).await.unwrap();
    m.assert_args(&["ZINTER", "2", "z1", "z2"]);
}

#[tokio::test]
async fn zintercard_and_rangestore() {
    let m = Mock::int(1);
    m.zintercard(&["z1", "z2"], Some(10)).await.unwrap();
    m.assert_args(&["ZINTERCARD", "2", "z1", "z2", "LIMIT", "10"]);

    let m = Mock::int(1);
    m.zintercard(&["z1", "z2"], None).await.unwrap();
    m.assert_args(&["ZINTERCARD", "2", "z1", "z2"]);

    let m = Mock::int(3);
    m.zrangestore_by_index("dest", "src", 0, -1, true)
        .await
        .unwrap();
    m.assert_args(&["ZRANGESTORE", "dest", "src", "0", "-1", "REV"]);
}

#[tokio::test]
async fn zrangestore_by_score_forward_no_limit() {
    let m = Mock::int(3);
    let n = m
        .zrangestore_by_score(
            "d",
            "s",
            ScoreBound::Inclusive(1.0),
            ScoreBound::Inclusive(5.0),
            false,
            None,
        )
        .await
        .unwrap();
    assert_eq!(n, 3);
    m.assert_args(&["ZRANGESTORE", "d", "s", "1", "5", "BYSCORE"]);
}

#[tokio::test]
async fn zrangestore_by_score_rev_swaps_bounds_and_limit() {
    let m = Mock::int(2);
    m.zrangestore_by_score(
        "d",
        "s",
        ScoreBound::Inclusive(1.0),
        ScoreBound::Inclusive(5.0),
        true,
        Some(glide::commands::options::Limit {
            offset: 0,
            count: 10,
        }),
    )
    .await
    .unwrap();
    // REV => high bound emitted first, plus REV + LIMIT.
    m.assert_args(&[
        "ZRANGESTORE",
        "d",
        "s",
        "5",
        "1",
        "BYSCORE",
        "REV",
        "LIMIT",
        "0",
        "10",
    ]);
}

#[tokio::test]
async fn zrangestore_by_lex_forward() {
    let m = Mock::int(1);
    m.zrangestore_by_lex(
        "d",
        "s",
        &LexBound::Inclusive(b"a".to_vec()),
        &LexBound::PositiveInfinity,
        false,
        None,
    )
    .await
    .unwrap();
    m.assert_args(&["ZRANGESTORE", "d", "s", "[a", "+", "BYLEX"]);
}

#[tokio::test]
async fn zrangestore_by_lex_rev_swaps_bounds() {
    let m = Mock::int(1);
    m.zrangestore_by_lex(
        "d",
        "s",
        &LexBound::NegativeInfinity,
        &LexBound::Inclusive(b"z".to_vec()),
        true,
        None,
    )
    .await
    .unwrap();
    // REV => (max, min) order.
    m.assert_args(&["ZRANGESTORE", "d", "s", "[z", "-", "BYLEX", "REV"]);
}
