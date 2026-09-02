// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command HyperLogLog integration tests (RESP2 + RESP3).

mod common;

use glide::AsyncCommands;

matrix_test!(pfadd_pfcount, c, {
    let k = common::key("hll");
    let changed: bool = c.pfadd(&k, &["a", "b", "c"]).await.unwrap();
    assert!(changed);
    let count: i64 = c.pfcount(&k).await.unwrap();
    assert_eq!(count, 3);
});

matrix_test!(pfadd_duplicate_no_change, c, {
    let k = common::key("hll");
    let _: bool = c.pfadd(&k, &["a", "b", "c"]).await.unwrap();
    // Adding only existing elements should not alter the registers.
    let changed: bool = c.pfadd(&k, &["a"]).await.unwrap();
    assert!(!changed);
});

matrix_test!(pfcount_missing_zero, c, {
    let count: i64 = c.pfcount(common::key("hll")).await.unwrap();
    assert_eq!(count, 0);
});

matrix_test!(pfcount_union, c, {
    let k1 = common::tkey("hll", "1");
    let k2 = common::tkey("hll", "2");
    let _: bool = c.pfadd(&k1, &["a", "b", "c"]).await.unwrap();
    let _: bool = c.pfadd(&k2, &["c", "d", "e"]).await.unwrap();
    // Union cardinality across keys ~5 unique.
    let count: i64 = c.pfcount(&[&k1, &k2]).await.unwrap();
    assert_eq!(count, 5);
});

matrix_test!(pfmerge, c, {
    let k1 = common::tkey("hll", "1");
    let k2 = common::tkey("hll", "2");
    let dst = common::tkey("hll", "merged");
    let _: bool = c.pfadd(&k1, &["a", "b"]).await.unwrap();
    let _: bool = c.pfadd(&k2, &["c", "d"]).await.unwrap();
    let _: () = c.pfmerge(&dst, &[&k1, &k2]).await.unwrap();
    let count: i64 = c.pfcount(&dst).await.unwrap();
    assert_eq!(count, 4);
});

matrix_test!(pf_wrong_type_errors, c, {
    // A plain string that is not an HLL raises when counted.
    let k = common::key("wt");
    let _: () = c.set(&k, "not-an-hll-value").await.unwrap();
    let result: redis::RedisResult<i64> = c.pfcount(&k).await;
    assert!(result.is_err());
});
