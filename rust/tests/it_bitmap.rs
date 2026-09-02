// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command bitmap integration tests (RESP2 + RESP3).

mod common;

use glide::commands::bitmap::BitmapIndexType;
use glide::{AsyncCommands, BitmapCommands};

matrix_test!(setbit_getbit, c, {
    let k = common::key("bit");
    // SETBIT returns the previous bit (0 initially).
    let prev: i64 = c.setbit(&k, 7, true).await.unwrap();
    assert_eq!(prev, 0);
    let bit: i64 = c.getbit(&k, 7).await.unwrap();
    assert_eq!(bit, 1);
    let bit: i64 = c.getbit(&k, 0).await.unwrap();
    assert_eq!(bit, 0);
    // Setting again returns the previous value (1).
    let prev: i64 = c.setbit(&k, 7, false).await.unwrap();
    assert_eq!(prev, 1);
});

matrix_test!(getbit_missing_zero, c, {
    let bit: i64 = c.getbit(common::key("bit"), 100).await.unwrap();
    assert_eq!(bit, 0);
});

matrix_test!(bitcount, c, {
    let k = common::key("bit");
    let _: i64 = c.setbit(&k, 0, true).await.unwrap();
    let _: i64 = c.setbit(&k, 1, true).await.unwrap();
    let _: i64 = c.setbit(&k, 7, true).await.unwrap();
    let count: i64 = c.bitcount(&k).await.unwrap();
    assert_eq!(count, 3);
});

matrix_test!(bitcount_missing_zero, c, {
    let count: i64 = c.bitcount(common::key("bit")).await.unwrap();
    assert_eq!(count, 0);
});

matrix_test!(bitcount_range_byte, c, {
    let k = common::key("bit");
    // Two bytes: first byte has 8 set bits, second has 0.
    for i in 0..8usize {
        let _: i64 = c.setbit(&k, i, true).await.unwrap();
    }
    assert_eq!(
        c.bitpos_range(&k, 1, 0, 0, Some(BitmapIndexType::Byte))
            .await
            .unwrap(),
        0
    );
    // Use bitcount_range via compat
    let count: i64 = c.bitcount_range(&k, 0, 0).await.unwrap();
    assert_eq!(count, 8);
    let count: i64 = c.bitcount_range(&k, 1, 1).await.unwrap();
    assert_eq!(count, 0);
});

matrix_test!(bitcount_range_bit, c, {
    let k = common::key("bit");
    let _: i64 = c.setbit(&k, 5, true).await.unwrap();
    let _: i64 = c.setbit(&k, 6, true).await.unwrap();
    // bitcount_range in compat takes byte offsets; use native bitpos_range for BIT index type
    // Use cmd for BITCOUNT with BIT index type
    let count: i64 = c
        .glide_send(
            redis::cmd("BITCOUNT")
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
    let _: i64 = c.setbit(&k, 10, true).await.unwrap();
    assert_eq!(c.bitpos(&k, 1).await.unwrap(), 10);
});

matrix_test!(bitop_and, c, {
    let a = common::tkey("bo", "a");
    let b = common::tkey("bo", "b");
    let dst = common::tkey("bo", "dst");
    let _: i64 = c.setbit(&a, 0, true).await.unwrap();
    let _: i64 = c.setbit(&a, 1, true).await.unwrap();
    let _: i64 = c.setbit(&b, 1, true).await.unwrap();
    let _: i64 = c.bit_and(&dst, &[&a, &b]).await.unwrap();
    let bit: i64 = c.getbit(&dst, 0).await.unwrap();
    assert_eq!(bit, 0);
    let bit: i64 = c.getbit(&dst, 1).await.unwrap();
    assert_eq!(bit, 1);
});

matrix_test!(bitop_or, c, {
    let a = common::tkey("bo", "a");
    let b = common::tkey("bo", "b");
    let dst = common::tkey("bo", "dst");
    let _: i64 = c.setbit(&a, 0, true).await.unwrap();
    let _: i64 = c.setbit(&b, 3, true).await.unwrap();
    let _: i64 = c.bit_or(&dst, &[&a, &b]).await.unwrap();
    let bit: i64 = c.getbit(&dst, 0).await.unwrap();
    assert_eq!(bit, 1);
    let bit: i64 = c.getbit(&dst, 3).await.unwrap();
    assert_eq!(bit, 1);
});

matrix_test!(bitop_xor, c, {
    let a = common::tkey("bo", "a");
    let b = common::tkey("bo", "b");
    let dst = common::tkey("bo", "dst");
    let _: i64 = c.setbit(&a, 0, true).await.unwrap();
    let _: i64 = c.setbit(&b, 0, true).await.unwrap();
    let _: i64 = c.setbit(&b, 1, true).await.unwrap();
    let _: i64 = c.bit_xor(&dst, &[&a, &b]).await.unwrap();
    let bit: i64 = c.getbit(&dst, 0).await.unwrap();
    assert_eq!(bit, 0);
    let bit: i64 = c.getbit(&dst, 1).await.unwrap();
    assert_eq!(bit, 1);
});

matrix_test!(bitop_not, c, {
    let a = common::tkey("bo", "a");
    let dst = common::tkey("bo", "dst");
    let _: i64 = c.setbit(&a, 0, true).await.unwrap();
    let _: i64 = c.bit_not(&dst, &a).await.unwrap();
    // NOT flips the first bit to 0 and the rest of the byte to 1.
    let bit: i64 = c.getbit(&dst, 0).await.unwrap();
    assert_eq!(bit, 0);
    let bit: i64 = c.getbit(&dst, 1).await.unwrap();
    assert_eq!(bit, 1);
});

matrix_test!(bitmap_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: i64 = c.rpush(&k, &["x"]).await.unwrap();
    let res: redis::RedisResult<i64> = c.setbit(&k, 0, true).await;
    assert!(res.is_err());
});
