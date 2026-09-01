// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command stream integration tests (RESP2 + RESP3).

mod common;

use glide::{AsyncCommands, StreamCommands};

matrix_test!(xadd_xlen, c, {
    let k = common::key("stream");
    let id = c.xadd(&k, "*", &[("field", "value")]).await.unwrap();
    assert!(id.is_some());
    assert_eq!(c.xlen(&k).await.unwrap(), 1);
    c.xadd(&k, "*", &[("f2", "v2")]).await.unwrap();
    assert_eq!(c.xlen(&k).await.unwrap(), 2);
});

matrix_test!(xlen_missing_zero, c, {
    assert_eq!(c.xlen(common::key("stream")).await.unwrap(), 0);
});

matrix_test!(xadd_explicit_id, c, {
    let k = common::key("stream");
    let id = c.xadd(&k, "1-1", &[("f", "v")]).await.unwrap();
    assert_eq!(id.as_deref(), Some("1-1"));
});

matrix_test!(xrange, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("field", "value")]).await.unwrap();
    let entries = c.xrange(&k, "-", "+").await.unwrap();
    assert_eq!(entries.len(), 1);
    assert_eq!(entries[0].0, "1-1");
    assert_eq!(entries[0].1[0].0.as_ref(), b"field");
    assert_eq!(entries[0].1[0].1.as_ref(), b"value");
});

matrix_test!(xrange_empty, c, {
    assert!(
        c.xrange(common::key("stream"), "-", "+")
            .await
            .unwrap()
            .is_empty()
    );
});

matrix_test!(xrevrange, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("a", "1")]).await.unwrap();
    c.xadd(&k, "2-1", &[("b", "2")]).await.unwrap();
    let entries = c.xrevrange(&k, "+", "-").await.unwrap();
    assert_eq!(entries.len(), 2);
    // Reverse order: newest first.
    assert_eq!(entries[0].0, "2-1");
});

matrix_test!(xdel, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("a", "1")]).await.unwrap();
    c.xadd(&k, "2-1", &[("b", "2")]).await.unwrap();
    assert_eq!(c.xdel(&k, &["1-1", "9-9"]).await.unwrap(), 1);
    assert_eq!(c.xlen(&k).await.unwrap(), 1);
});

matrix_test!(xtrim_maxlen, c, {
    let k = common::key("stream");
    for i in 1..=5 {
        c.xadd(&k, &format!("{i}-1"), &[("f", "v")]).await.unwrap();
    }
    let trimmed = c.xtrim_maxlen(&k, 2, false).await.unwrap();
    assert_eq!(trimmed, 3);
    assert_eq!(c.xlen(&k).await.unwrap(), 2);
});

matrix_test!(xgroup_create_destroy, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "v")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0", false).await.unwrap();
    assert!(c.xgroup_destroy(&k, "grp").await.unwrap());
    // Destroying a non-existent group returns false.
    assert!(!c.xgroup_destroy(&k, "nope").await.unwrap());
});

matrix_test!(xgroup_create_mkstream, c, {
    let k = common::key("stream");
    // MKSTREAM creates the stream if absent.
    c.xgroup_create(&k, "grp", "0", true).await.unwrap();
    assert_eq!(c.xlen(&k).await.unwrap(), 0);
});

matrix_test!(xack, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "v")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0", false).await.unwrap();
    // No entries have been read/pending, so ack returns 0.
    assert_eq!(c.xack(&k, "grp", &["1-1"]).await.unwrap(), 0);
});

matrix_test!(stream_wrong_type_errors, c, {
    let k = common::key("wt");
    c.set::<_, _, ()>(&k, "notastream").await.unwrap();
    assert_request_error!(c.xlen(&k).await);
});
