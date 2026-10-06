// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command set integration tests (RESP2 + RESP3).

mod common;

use glide::{AsyncTypedCommands, SetCommands, ValkeyResult};
use std::collections::HashSet;

matrix_test!(sadd_scard, c, {
    let k = common::key("s");
    let added: usize = c.sadd(&k, &["a", "b", "c"][..]).await.unwrap();
    assert_eq!(added, 3);

    // Re-adding existing members returns 0 new.
    assert_eq!(c.sadd(&k, "a").await.unwrap(), 0);
    assert_eq!(c.scard(&k).await.unwrap(), 3);
});

matrix_test!(scard_missing_zero, c, {
    assert_eq!(c.scard(common::key("s")).await.unwrap(), 0);
});

matrix_test!(srem, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, &["a", "b", "c"][..]).await.unwrap();
    let removed: usize = c.srem(&k, &["a", "missing"][..]).await.unwrap();
    assert_eq!(removed, 1);
    let card: usize = c.scard(&k).await.unwrap();
    assert_eq!(card, 2);
});

matrix_test!(smembers, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, &["a", "b", "c"][..]).await.unwrap();
    let members: HashSet<String> = c.smembers(&k).await.unwrap();
    assert_eq!(members.len(), 3);
    assert!(members.contains("a"));
});

matrix_test!(smembers_missing_empty, c, {
    let members: HashSet<String> = c.smembers(common::key("s")).await.unwrap();
    assert!(members.is_empty());
});

matrix_test!(sismember, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, "a").await.unwrap();
    let yes: bool = c.sismember(&k, "a").await.unwrap();
    assert!(yes);
    let no: bool = c.sismember(&k, "b").await.unwrap();
    assert!(!no);
});

matrix_test!(smismember, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, &["a", "b"][..]).await.unwrap();
    let result: Vec<bool> = c.smismember(&k, &["a", "x", "b"][..]).await.unwrap();
    assert_eq!(result, vec![true, false, true]);
});

matrix_test!(spop, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, "only").await.unwrap();
    let popped: Option<String> = c.spop(&k).await.unwrap();
    assert_eq!(popped.as_deref(), Some("only"));
    // Set now empty -> None.
    let popped: Option<String> = c.spop(&k).await.unwrap();
    assert_eq!(popped, None);
});

// TODO #7082: replace the raw `SPOP` with a typed `spop` that accepts a count.
matrix_test!(spop_count, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, &["a", "b", "c"][..]).await.unwrap();
    let mut cmd = glide::Cmd::new();
    cmd.arg("SPOP").arg(&k).arg(2);
    let popped: HashSet<String> = glide::AsyncCommands::glide_send_command_as(&c, cmd)
        .await
        .unwrap();
    assert_eq!(popped.len(), 2);
    let card: usize = c.scard(&k).await.unwrap();
    assert_eq!(card, 1);
});

matrix_test!(srandmember, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, "only").await.unwrap();
    let member: Option<String> = c.srandmember(&k).await.unwrap();
    assert_eq!(member.as_deref(), Some("only"));
    // Not removed.
    let card: usize = c.scard(&k).await.unwrap();
    assert_eq!(card, 1);
    let missing: Option<String> = c.srandmember(common::key("x")).await.unwrap();
    assert_eq!(missing, None);
});

matrix_test!(srandmember_multiple, c, {
    let k = common::key("s");
    let _: usize = c.sadd(&k, &["a", "b", "c"][..]).await.unwrap();

    // A positive count returns up to that many distinct members.
    let members: Vec<String> = c.srandmember_multiple(&k, 2).await.unwrap();
    assert_eq!(members.len(), 2);

    let members: Vec<String> = c.srandmember_multiple(&k, 10).await.unwrap();
    assert_eq!(members.len(), 3);

    // A negative count returns exactly that many members, possibly repeated.
    let only = common::key("s1");
    let _: usize = c.sadd(&only, "x").await.unwrap();
    let members: Vec<String> = c.srandmember_multiple(&only, -3).await.unwrap();
    assert_eq!(members, ["x", "x", "x"]);
});

matrix_test!(sunion, c, {
    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let _: usize = c.sadd(&s1, &["a", "b"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, &["b", "c"][..]).await.unwrap();
    let u: HashSet<String> = c.sunion(&[&s1, &s2][..]).await.unwrap();
    assert_eq!(u.len(), 3);
});

matrix_test!(sinter, c, {
    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let _: usize = c.sadd(&s1, &["a", "b"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, &["b", "c"][..]).await.unwrap();
    let i: HashSet<String> = c.sinter(&[&s1, &s2][..]).await.unwrap();
    assert_eq!(i.len(), 1);
    assert!(i.contains("b"));
});

matrix_test!(sdiff, c, {
    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let _: usize = c.sadd(&s1, &["a", "b", "c"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, "b").await.unwrap();
    let d: HashSet<String> = c.sdiff(&[&s1, &s2][..]).await.unwrap();
    assert_eq!(d.len(), 2);
    assert!(!d.contains("b"));
});

matrix_test!(sintercard, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let _: usize = c.sadd(&s1, &["a", "b", "c"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, &["b", "c", "d"][..]).await.unwrap();
    assert_eq!(c.sintercard(&[&s1, &s2]).await.unwrap(), 2);
});

matrix_test!(sunionstore, c, {
    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let dst = common::tkey("st", "dst");
    let _: usize = c.sadd(&s1, &["a", "b"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, &["b", "c"][..]).await.unwrap();
    let stored: usize = c.sunionstore(&dst, &[&s1, &s2][..]).await.unwrap();
    assert_eq!(stored, 3);
    let card: usize = c.scard(&dst).await.unwrap();
    assert_eq!(card, 3);
});

matrix_test!(sinterstore, c, {
    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let dst = common::tkey("st", "dst");
    let _: usize = c.sadd(&s1, &["a", "b"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, &["b", "c"][..]).await.unwrap();
    let stored: usize = c.sinterstore(&dst, &[&s1, &s2][..]).await.unwrap();
    assert_eq!(stored, 1);
});

matrix_test!(sdiffstore, c, {
    let s1 = common::tkey("st", "s1");
    let s2 = common::tkey("st", "s2");
    let dst = common::tkey("st", "dst");
    let _: usize = c.sadd(&s1, &["a", "b", "c"][..]).await.unwrap();
    let _: usize = c.sadd(&s2, "b").await.unwrap();
    let stored: usize = c.sdiffstore(&dst, &[&s1, &s2][..]).await.unwrap();
    assert_eq!(stored, 2);
});

matrix_test!(smove, c, {
    let src = common::tkey("st", "src");
    let dst = common::tkey("st", "dst");
    let _: usize = c.sadd(&src, &["a", "b"][..]).await.unwrap();
    let moved: bool = c.smove(&src, &dst, "a").await.unwrap();
    assert!(moved);
    let not_moved: bool = c.smove(&src, &dst, "missing").await.unwrap();
    assert!(!not_moved);
    let is_member: bool = c.sismember(&dst, "a").await.unwrap();
    assert!(is_member);
});

matrix_test!(set_wrong_type_errors, c, {
    let k = common::key("wt");
    let _: () = c.set(&k, "notaset").await.unwrap();
    let res: ValkeyResult<usize> = c.sadd(&k, "x").await;
    assert!(res.is_err());
});
