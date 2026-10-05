// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command stream integration tests (RESP2 + RESP3).

mod common;

use glide::{
    AsyncTypedCommands, StreamAddOptions, StreamCommands, StreamGroupCreateOptions,
    StreamPendingReply, StreamTrimStrategy, StreamTrimmingMode,
};

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

matrix_test!(xadd_options, c, {
    let k = common::key("stream");

    // NOMKSTREAM does not create a missing stream.
    let options = StreamAddOptions::default().nomkstream();
    let id = c
        .xadd_options(&k, "*", &[("f", "v")], &options)
        .await
        .unwrap();
    assert_eq!(id, None);
    assert_eq!(c.xlen(&k).await.unwrap(), 0);

    // Trimming as part of the add.
    let options =
        StreamAddOptions::default().trim(StreamTrimStrategy::maxlen(StreamTrimmingMode::Exact, 2));
    for i in 1..=3 {
        let id = format!("{i}-1");
        c.xadd_options(&k, &id, &[("f", "v")], &options)
            .await
            .unwrap();
    }
    assert_eq!(c.xlen(&k).await.unwrap(), 2);
});

matrix_test!(xrange, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("field", "value")]).await.unwrap();
    let reply = c.xrange(&k, "-", "+").await.unwrap();
    assert_eq!(reply.ids.len(), 1);
    assert_eq!(reply.ids[0].id, "1-1");
    assert_eq!(
        reply.ids[0].get::<String>("field").as_deref(),
        Some("value")
    );
});

matrix_test!(xrange_empty, c, {
    let reply = c.xrange(common::key("stream"), "-", "+").await.unwrap();
    assert!(reply.ids.is_empty());
});

matrix_test!(xrevrange, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("a", "1")]).await.unwrap();
    c.xadd(&k, "2-1", &[("b", "2")]).await.unwrap();
    let reply = c.xrevrange(&k, "+", "-").await.unwrap();
    let ids: Vec<_> = reply.ids.iter().map(|entry| entry.id.as_str()).collect();
    // Reverse order: newest first.
    assert_eq!(ids, vec!["2-1", "1-1"]);
});

matrix_test!(xdel, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("a", "1")]).await.unwrap();
    c.xadd(&k, "2-1", &[("b", "2")]).await.unwrap();
    assert_eq!(c.xdel(&k, &["1-1", "9-9"]).await.unwrap(), 1);
    assert_eq!(c.xlen(&k).await.unwrap(), 1);
});

matrix_test!(xread, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();

    let reply = c.xread(&[&k], &["1-1"]).await.unwrap().unwrap();
    assert_eq!(reply.keys.len(), 1);
    assert_eq!(reply.keys[0].key, k);
    assert_eq!(reply.keys[0].ids.len(), 1);
    assert_eq!(reply.keys[0].ids[0].id, "2-1");
    assert_eq!(
        reply.keys[0].ids[0].get::<String>("f").as_deref(),
        Some("b")
    );

    // Nothing newer than the last entry.
    assert!(c.xread(&[&k], &["2-1"]).await.unwrap().is_none());
});

matrix_test!(xtrim_maxlen, c, {
    let k = common::key("stream");
    for i in 1..=5 {
        c.xadd(&k, format!("{i}-1"), &[("f", "v")]).await.unwrap();
    }
    let trimmed = c.xtrim_maxlen(&k, 2, false).await.unwrap();
    assert_eq!(trimmed, 3);
    assert_eq!(c.xlen(&k).await.unwrap(), 2);
});

matrix_test!(xgroup_create_destroy, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "v")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();
    assert!(c.xgroup_destroy(&k, "grp").await.unwrap());
    // Destroying a non-existent group returns false.
    assert!(!c.xgroup_destroy(&k, "nope").await.unwrap());
});

matrix_test!(xgroup_create_mkstream, c, {
    let k = common::key("stream");
    // MKSTREAM creates the stream if absent.
    let options = StreamGroupCreateOptions {
        make_stream: true,
        ..Default::default()
    };
    c.xgroup_create_options(&k, "grp", "0", &options)
        .await
        .unwrap();
    assert_eq!(c.xlen(&k).await.unwrap(), 0);
});

matrix_test!(xack, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "v")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();
    // No entries have been read/pending, so ack returns 0.
    assert_eq!(c.xack(&k, "grp", &["1-1"]).await.unwrap(), 0);

    c.xreadgroup("grp", "c1", &[(&k, ">")], None).await.unwrap();
    assert_eq!(c.xack(&k, "grp", &["1-1"]).await.unwrap(), 1);
});

matrix_test!(xpending_xclaim, c, {
    let k = common::key("stream");
    c.xgroup_create_options(
        &k,
        "grp",
        "0",
        &StreamGroupCreateOptions {
            make_stream: true,
            ..Default::default()
        },
    )
    .await
    .unwrap();
    assert!(matches!(
        c.xpending(&k, "grp").await.unwrap(),
        StreamPendingReply::Empty
    ));

    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();
    c.xreadgroup("grp", "c1", &[(&k, ">")], None).await.unwrap();

    let pending = c.xpending(&k, "grp").await.unwrap();
    assert_eq!(pending.count(), 2);
    let StreamPendingReply::Data(data) = pending else {
        panic!("expected pending entries");
    };
    assert_eq!(
        (data.start_id.as_str(), data.end_id.as_str()),
        ("1-1", "2-1")
    );
    assert_eq!(data.consumers.len(), 1);
    assert_eq!(
        (data.consumers[0].name.as_str(), data.consumers[0].pending),
        ("c1", 2)
    );

    let claimed = c.xclaim(&k, "grp", "c2", 0, &["1-1"]).await.unwrap();
    assert_eq!(claimed.ids.len(), 1);
    assert_eq!(claimed.ids[0].id, "1-1");
    assert_eq!(claimed.ids[0].get::<String>("f").as_deref(), Some("a"));
});

matrix_test!(xinfo, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();
    c.xreadgroup("grp", "c1", &[(&k, ">")], None).await.unwrap();

    let stream = c.xinfo_stream(&k).await.unwrap();
    assert_eq!((stream.length, stream.groups), (2, 1));
    assert_eq!(stream.last_generated_id, "2-1");
    assert_eq!(stream.first_entry.id, "1-1");
    assert_eq!(stream.last_entry.get::<String>("f").as_deref(), Some("b"));

    let groups = c.xinfo_groups(&k).await.unwrap();
    assert_eq!(groups.groups.len(), 1);
    let group = &groups.groups[0];
    assert_eq!(
        (group.name.as_str(), group.consumers, group.pending),
        ("grp", 1, 2)
    );
    assert_eq!(group.last_delivered_id, "2-1");

    let consumers = c.xinfo_consumers(&k, "grp").await.unwrap();
    assert_eq!(consumers.consumers.len(), 1);
    assert_eq!(
        (
            consumers.consumers[0].name.as_str(),
            consumers.consumers[0].pending
        ),
        ("c1", 2)
    );
});

matrix_test!(stream_wrong_type_errors, c, {
    let k = common::key("wt");
    c.set(&k, "notastream").await.unwrap();
    assert_request_error!(c.xlen(&k).await);
});
