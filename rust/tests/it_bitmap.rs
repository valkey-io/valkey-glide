// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command bitmap integration tests (RESP2 + RESP3).

mod common;

use glide::commands::bitmap::{
    BitEncoding, BitFieldOffset, BitFieldSubcommand, BitOverflow, BitmapIndexType,
};
use glide::{AsyncTypedCommands, BitmapCommands};

matrix_test!(setbit_getbit, c, {
    let k = common::key("bit");
    // SETBIT returns the previous bit (0 initially).
    let prev: bool = c.setbit(&k, 7, true).await.unwrap();
    assert!(!prev);
    let bit: bool = c.getbit(&k, 7).await.unwrap();
    assert!(bit);
    let bit: bool = c.getbit(&k, 0).await.unwrap();
    assert!(!bit);
    // Setting again returns the previous value (1).
    let prev: bool = c.setbit(&k, 7, false).await.unwrap();
    assert!(prev);
});

matrix_test!(getbit_missing_zero, c, {
    let bit: bool = c.getbit(common::key("bit"), 100).await.unwrap();
    assert!(!bit);
});

matrix_test!(bitcount, c, {
    assert_eq!(c.bitcount(common::key("bit")).await.unwrap(), 0);

    let k = common::key("bit");
    let _: bool = c.setbit(&k, 0, true).await.unwrap();
    let _: bool = c.setbit(&k, 1, true).await.unwrap();
    let _: bool = c.setbit(&k, 7, true).await.unwrap();
    assert_eq!(c.bitcount(&k).await.unwrap(), 3);
});

matrix_test!(bitcount_range_byte, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::key("bit");
    // Two bytes: first byte has 8 set bits, second has 0.
    for i in 0..8usize {
        let _: bool = c.setbit(&k, i, true).await.unwrap();
    }
    assert_eq!(
        c.bitpos_range(&k, 1, 0, 0, Some(BitmapIndexType::Byte))
            .await
            .unwrap(),
        0
    );
    // Use bitcount_range via compat
    assert_eq!(c.bitcount_range(&k, 0, 0).await.unwrap(), 8);
    assert_eq!(c.bitcount_range(&k, 1, 1).await.unwrap(), 0);
});

// TODO #7082: replace the raw `BITCOUNT` with a typed `bitcount_range` that
// accepts the BYTE/BIT index unit.
matrix_test!(bitcount_range_bit, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let k = common::key("bit");
    let _: bool = c.setbit(&k, 5, true).await.unwrap();
    let _: bool = c.setbit(&k, 6, true).await.unwrap();
    let count: i64 = glide::AsyncCommands::glide_send_command_as(
        &c,
        glide::cmd("BITCOUNT")
            .arg(&k)
            .arg(0i64)
            .arg(7i64)
            .arg("BIT")
            .clone(),
    )
    .await
    .unwrap();
    assert_eq!(count, 2);
});

matrix_test!(bitpos, c, {
    let k = common::key("bit");
    let _: bool = c.setbit(&k, 10, true).await.unwrap();
    assert_eq!(c.bitpos(&k, 1).await.unwrap(), 10);
});

matrix_test!(bitop_and, c, {
    let a = common::tkey("bo", "a");
    let b = common::tkey("bo", "b");
    let dst = common::tkey("bo", "dst");
    let _: bool = c.setbit(&a, 0, true).await.unwrap();
    let _: bool = c.setbit(&a, 1, true).await.unwrap();
    let _: bool = c.setbit(&b, 1, true).await.unwrap();
    let _: usize = c.bit_and(&dst, &[&a, &b]).await.unwrap();
    let bit: bool = c.getbit(&dst, 0).await.unwrap();
    assert!(!bit);
    let bit: bool = c.getbit(&dst, 1).await.unwrap();
    assert!(bit);
});

matrix_test!(bitop_or, c, {
    let a = common::tkey("bo", "a");
    let b = common::tkey("bo", "b");
    let dst = common::tkey("bo", "dst");
    let _: bool = c.setbit(&a, 0, true).await.unwrap();
    let _: bool = c.setbit(&b, 3, true).await.unwrap();
    let _: usize = c.bit_or(&dst, &[&a, &b]).await.unwrap();
    let bit: bool = c.getbit(&dst, 0).await.unwrap();
    assert!(bit);
    let bit: bool = c.getbit(&dst, 3).await.unwrap();
    assert!(bit);
});

matrix_test!(bitop_xor, c, {
    let a = common::tkey("bo", "a");
    let b = common::tkey("bo", "b");
    let dst = common::tkey("bo", "dst");
    let _: bool = c.setbit(&a, 0, true).await.unwrap();
    let _: bool = c.setbit(&b, 0, true).await.unwrap();
    let _: bool = c.setbit(&b, 1, true).await.unwrap();
    let _: usize = c.bit_xor(&dst, &[&a, &b]).await.unwrap();
    let bit: bool = c.getbit(&dst, 0).await.unwrap();
    assert!(!bit);
    let bit: bool = c.getbit(&dst, 1).await.unwrap();
    assert!(bit);
});

matrix_test!(bitop_not, c, {
    let a = common::tkey("bo", "a");
    let dst = common::tkey("bo", "dst");
    let _: bool = c.setbit(&a, 0, true).await.unwrap();
    let _: usize = c.bit_not(&dst, &a).await.unwrap();
    // NOT flips the first bit to 0 and the rest of the byte to 1.
    let bit: bool = c.getbit(&dst, 0).await.unwrap();
    assert!(!bit);
    let bit: bool = c.getbit(&dst, 1).await.unwrap();
    assert!(bit);
});

matrix_test!(bitmap_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: usize = c.rpush(&k, &["x"]).await.unwrap();
    let res: glide::ValkeyResult<bool> = c.setbit(&k, 0, true).await;
    assert!(res.is_err());
});

matrix_test!(bitfield, c, {
    let k = common::key("bf");
    let encoding = BitEncoding::Unsigned(8);
    let offset = BitFieldOffset::Bit(0);
    let results = c
        .bitfield(
            &k,
            &[
                BitFieldSubcommand::Set {
                    encoding,
                    offset,
                    value: 200,
                },
                BitFieldSubcommand::Overflow(BitOverflow::Fail),
                BitFieldSubcommand::IncrBy {
                    encoding,
                    offset,
                    increment: 100,
                },
                BitFieldSubcommand::Get { encoding, offset },
            ],
        )
        .await
        .unwrap();
    // SET returns the old value; the overflowing INCRBY fails under `FAIL`.
    assert_eq!(results, vec![Some(0), None, Some(200)]);
});

matrix_test!(bitfield_readonly, c, {
    let k = common::key("bfro");
    let encoding = BitEncoding::Unsigned(8);
    let _ = c
        .bitfield(
            &k,
            &[BitFieldSubcommand::Set {
                encoding,
                offset: BitFieldOffset::Bit(0),
                value: 200,
            }],
        )
        .await
        .unwrap();
    let results = c
        .bitfield_readonly(
            &k,
            &[
                BitFieldSubcommand::Get {
                    encoding,
                    offset: BitFieldOffset::Bit(0),
                },
                BitFieldSubcommand::Get {
                    encoding,
                    offset: BitFieldOffset::Multiplier(1),
                },
            ],
        )
        .await
        .unwrap();
    assert_eq!(results, vec![Some(200), Some(0)]);
});
