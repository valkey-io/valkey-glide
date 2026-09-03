// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the bitmap command family (command dispatch).
use super::Mock;
use glide::commands::bitmap::{
    BitEncoding, BitFieldOffset, BitFieldSubcommand, BitmapCommands, BitmapIndexType,
};
use redis::Value;

#[tokio::test]
async fn bitpos_and_range() {
    let m = Mock::int(3);
    assert_eq!(m.bitpos("k", 1).await.unwrap(), 3);
    m.assert_args(&["BITPOS", "k", "1"]);

    let m = Mock::int(3);
    m.bitpos_range("k", 1, 0, 10, Some(BitmapIndexType::Bit))
        .await
        .unwrap();
    m.assert_args(&["BITPOS", "k", "1", "0", "10", "BIT"]);
}

#[tokio::test]
async fn bitfield_get_set_incrby() {
    let m = Mock::array(vec![Value::Int(5)]);
    let r = m
        .bitfield(
            "k",
            &[BitFieldSubcommand::Get {
                encoding: BitEncoding::Unsigned(8),
                offset: BitFieldOffset::Bit(0),
            }],
        )
        .await
        .unwrap();
    m.assert_args(&["BITFIELD", "k", "GET", "u8", "0"]);
    assert_eq!(r, vec![Some(5)]);

    let m = Mock::array(vec![Value::Int(0), Value::Nil]);
    let r = m
        .bitfield(
            "k",
            &[
                BitFieldSubcommand::Set {
                    encoding: BitEncoding::Signed(5),
                    offset: BitFieldOffset::Multiplier(1),
                    value: 12,
                },
                BitFieldSubcommand::IncrBy {
                    encoding: BitEncoding::Unsigned(4),
                    offset: BitFieldOffset::Bit(2),
                    increment: 3,
                },
            ],
        )
        .await
        .unwrap();
    m.assert_args(&[
        "BITFIELD", "k", "SET", "i5", "#1", "12", "INCRBY", "u4", "2", "3",
    ]);
    assert_eq!(r, vec![Some(0), None]);
}

#[tokio::test]
async fn bitfield_readonly() {
    let m = Mock::array(vec![Value::Int(7)]);
    m.bitfield_readonly(
        "k",
        &[BitFieldSubcommand::Get {
            encoding: BitEncoding::Unsigned(8),
            offset: BitFieldOffset::Bit(0),
        }],
    )
    .await
    .unwrap();
    m.assert_args(&["BITFIELD_RO", "k", "GET", "u8", "0"]);
}
