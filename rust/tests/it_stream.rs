// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-command stream integration tests (RESP2 + RESP3).

mod common;

use glide::{
    AsyncTypedCommands, StreamAddOptions, StreamAutoClaimOptions, StreamClaimOptions,
    StreamClaimReply, StreamCommands, StreamGroupCreateOptions, StreamMaxlen, StreamPendingReply,
    StreamReadGroupOptions, StreamReadOptions, StreamTrimOptions, StreamTrimStrategy,
    StreamTrimmingMode,
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

matrix_test!(xread_options, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();

    // COUNT limits the entries per stream.
    let options = StreamReadOptions::default().count(1);
    let reply = c
        .xread_options(&[&k], &["0"], &options)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(reply.keys[0].ids.len(), 1);
    assert_eq!(reply.keys[0].ids[0].id, "1-1");

    // BLOCK times out with no reply when nothing newer arrives.
    let options = StreamReadOptions::default().block(10);
    assert!(
        c.xread_options(&[&k], &["2-1"], &options)
            .await
            .unwrap()
            .is_none()
    );
});

matrix_test!(xtrim, c, {
    let k = common::key("stream");
    for i in 1..=5 {
        c.xadd(&k, format!("{i}-1"), &[("f", "v")]).await.unwrap();
    }
    assert_eq!(c.xtrim(&k, StreamMaxlen::Equals(4)).await.unwrap(), 1);
    assert_eq!(c.xlen(&k).await.unwrap(), 4);

    // MAXLEN.
    let options = StreamTrimOptions::maxlen(StreamTrimmingMode::Exact, 3);
    assert_eq!(c.xtrim_options(&k, &options).await.unwrap(), 1);
    assert_eq!(c.xlen(&k).await.unwrap(), 3);

    // MINID.
    let options = StreamTrimOptions::minid(StreamTrimmingMode::Exact, "4-1");
    assert_eq!(c.xtrim_options(&k, &options).await.unwrap(), 1);
    assert_eq!(c.xrange(&k, "-", "+").await.unwrap().ids[0].id, "4-1");
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

    let k = common::key("stream");
    c.xgroup_create_mkstream(&k, "grp", "0").await.unwrap();
    assert_eq!(c.xlen(&k).await.unwrap(), 0);
    assert_eq!(c.xinfo_groups(&k).await.unwrap().groups[0].name, "grp");
});

matrix_test!(xgroup_consumers_setid, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();

    assert!(c.xgroup_createconsumer(&k, "grp", "c1").await.unwrap());
    assert!(!c.xgroup_createconsumer(&k, "grp", "c1").await.unwrap());

    // SETID moves the group past the first entry.
    c.xgroup_setid(&k, "grp", "1-1").await.unwrap();
    let entries = c.xreadgroup("grp", "c1", &[(&k, ">")], None).await.unwrap();
    assert_eq!(entries[0].1.len(), 1);

    // DELCONSUMER returns the consumer's pending count.
    assert_eq!(c.xgroup_delconsumer(&k, "grp", "c1").await.unwrap(), 1);
    assert_eq!(c.xgroup_delconsumer(&k, "grp", "c1").await.unwrap(), 0);
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

matrix_test!(xpending_count, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();
    c.xreadgroup(
        "grp",
        "c1",
        &[(&k, ">")],
        Some(StreamReadGroupOptions {
            count: Some(1),
            ..Default::default()
        }),
    )
    .await
    .unwrap();
    c.xreadgroup("grp", "c2", &[(&k, ">")], None).await.unwrap();

    let reply = c.xpending_count(&k, "grp", "-", "+", 10).await.unwrap();
    let pending: Vec<_> = reply
        .ids
        .iter()
        .map(|p| (p.id.as_str(), p.consumer.as_str(), p.times_delivered))
        .collect();
    assert_eq!(pending, [("1-1", "c1", 1), ("2-1", "c2", 1)]);

    let reply = c
        .xpending_consumer_count(&k, "grp", "-", "+", 10, "c2")
        .await
        .unwrap();
    assert_eq!(reply.ids.len(), 1);
    assert_eq!(reply.ids[0].id, "2-1");
});

matrix_test!(xautoclaim_options, c, {
    skip_if_version_below!(c, 6, 2, 0);

    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();
    c.xreadgroup("grp", "c1", &[(&k, ">")], None).await.unwrap();

    // COUNT claims one entry and returns the next start ID.
    let options = StreamAutoClaimOptions::default().count(1);
    let reply = c
        .xautoclaim_options(&k, "grp", "c2", 0, "0-0", options)
        .await
        .unwrap();
    assert_eq!(reply.next_stream_id, "2-1");
    assert_eq!(reply.claimed.len(), 1);
    assert_eq!(reply.claimed[0].id, "1-1");
    assert_eq!(reply.claimed[0].get::<String>("f").as_deref(), Some("a"));
    assert!(reply.deleted_ids.is_empty());
    assert!(!reply.invalid_entries);

    // JUSTID returns only the IDs.
    let options = StreamAutoClaimOptions::default().with_justid();
    let reply = c
        .xautoclaim_options(&k, "grp", "c2", 0, &reply.next_stream_id, options)
        .await
        .unwrap();
    assert_eq!(reply.next_stream_id, "0-0");
    assert_eq!(reply.claimed.len(), 1);
    assert_eq!(reply.claimed[0].id, "2-1");
    assert!(reply.claimed[0].is_empty());
});

matrix_test!(xclaim_options, c, {
    let k = common::key("stream");
    c.xadd(&k, "1-1", &[("f", "a")]).await.unwrap();
    c.xadd(&k, "2-1", &[("f", "b")]).await.unwrap();
    c.xgroup_create(&k, "grp", "0").await.unwrap();
    c.xreadgroup("grp", "c1", &[(&k, ">")], None).await.unwrap();

    // IDLE / RETRYCOUNT return the claimed entries.
    let options = StreamClaimOptions::default().idle(0).retry(5);
    let claimed: StreamClaimReply = c
        .xclaim_options(&k, "grp", "c2", 0, &["1-1"], options)
        .await
        .unwrap();
    assert_eq!(claimed.ids.len(), 1);
    assert_eq!(claimed.ids[0].get::<String>("f").as_deref(), Some("a"));

    // JUSTID returns only the IDs.
    let options = StreamClaimOptions::default().with_justid();
    let ids: Vec<String> = c
        .xclaim_options(&k, "grp", "c2", 0, &["2-1"], options)
        .await
        .unwrap();
    assert_eq!(ids, vec!["2-1"]);

    // FORCE claims an ID that is not pending.
    c.xadd(&k, "3-1", &[("f", "c")]).await.unwrap();
    let options = StreamClaimOptions::default().with_force().with_justid();
    let ids: Vec<String> = c
        .xclaim_options(&k, "grp", "c2", 0, &["3-1"], options)
        .await
        .unwrap();
    assert_eq!(ids, vec!["3-1"]);
    assert_eq!(c.xpending(&k, "grp").await.unwrap().count(), 3);
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
