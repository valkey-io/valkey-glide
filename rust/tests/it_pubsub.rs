// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pub/Sub integration tests (RESP2 + RESP3).
//!
//! Covers the publish side and `PUBSUB` introspection, plus
//! the runtime subscribe/receive path: with the push channel enabled
//! (`enable_pubsub()` or connect-time `subscriptions`), `subscribe`/`psubscribe`
//! deliver messages through `get_pubsub_message`.

mod common;

use glide::AsyncTypedCommands;
use glide::GlideClient;
use glide::GlideClientConfiguration;
use glide::PubSubMessageKind;
use glide::commands::pubsub::PubSubCommands;
use std::time::Duration;

resp_test!(publish_no_subscribers_returns_zero, c, {
    let chan = common::key("chan");
    assert_eq!(c.publish(&chan, "hello").await.unwrap(), 0);
});

resp_test!(pubsub_channels_empty, c, {
    let channels = c.pubsub_channels(None).await.unwrap();
    assert!(channels.is_empty(), "got: {channels:?}");
});

resp_test!(pubsub_numpat_zero, c, {
    assert_eq!(c.pubsub_numpat().await.unwrap(), 0);
});

resp_test!(spublish_no_subscribers, c, {
    skip_if_version_below!(c, 7, 0, 0);

    let chan = common::key("schan");
    assert_eq!(c.spublish(&chan, "msg").await.unwrap(), 0);
});

timed_tokio_test!(
    async fn runtime_subscribe_receives_then_unsubscribe() {
        let server = common::TestServer::start();
        let subscriber_config =
            GlideClientConfiguration::with_address("127.0.0.1", server.port).enable_pubsub();
        let subscriber = GlideClient::connect(subscriber_config)
            .await
            .expect("connect subscriber");
        let publisher = server.client().await;

        // Subscribe at runtime, then wait until the server has registered it
        // (poll-until-state instead of a fixed sleep).
        let chan = common::key("rt-chan");
        subscriber.subscribe(&[chan.as_str()]).await.unwrap();
        assert!(
            common::wait_for_numsub(&publisher, &chan, |n| n >= 1, Duration::from_secs(3)).await,
            "subscription was not registered server-side in time"
        );

        let n: usize = publisher.publish(&chan, "runtime-hello").await.unwrap();
        assert!(n >= 1, "expected >=1 subscriber, got {n}");

        let msg = tokio::time::timeout(Duration::from_secs(3), subscriber.get_pubsub_message())
            .await
            .expect("timed out waiting for runtime-subscribed message")
            .expect("receive error");
        assert_eq!(msg.channel.as_ref(), chan.as_bytes());
        assert_eq!(msg.payload.as_ref(), b"runtime-hello");

        // Unsubscribe; wait until the server reports no subscribers, then confirm.
        subscriber.unsubscribe(&[chan.as_str()]).await.unwrap();
        assert!(
            common::wait_for_numsub(&publisher, &chan, |n| n == 0, Duration::from_secs(3)).await,
            "unsubscribe did not take effect server-side in time"
        );
        let n2: usize = publisher.publish(&chan, "after-unsub").await.unwrap();
        assert_eq!(n2, 0, "no subscribers should remain after unsubscribe");
    }
);

timed_tokio_test!(
    async fn runtime_psubscribe_pattern_receive() {
        let server = common::TestServer::start();
        let subscriber_config =
            GlideClientConfiguration::with_address("127.0.0.1", server.port).enable_pubsub();
        let subscriber = GlideClient::connect(subscriber_config)
            .await
            .expect("connect subscriber");
        let publisher = server.client().await;

        subscriber.psubscribe(&["news.*"]).await.unwrap();
        assert!(
            common::wait_for_numpat(&publisher, |n| n >= 1, Duration::from_secs(3)).await,
            "pattern subscription was not registered server-side in time"
        );

        let _: usize = publisher.publish("news.tech", "breaking").await.unwrap();

        let msg = tokio::time::timeout(Duration::from_secs(3), subscriber.get_pubsub_message())
            .await
            .expect("timed out")
            .expect("receive error");
        assert_eq!(msg.kind, PubSubMessageKind::PMessage);
        assert_eq!(msg.channel.as_ref(), b"news.tech");
        assert_eq!(msg.pattern.as_deref(), Some(&b"news.*"[..]));
    }
);

timed_tokio_test!(
    async fn runtime_unsubscribe_all_stops_delivery() {
        let server = common::TestServer::start();
        let subscriber_config =
            GlideClientConfiguration::with_address("127.0.0.1", server.port).enable_pubsub();
        let subscriber = GlideClient::connect(subscriber_config)
            .await
            .expect("connect subscriber");
        let publisher = server.client().await;

        let c1 = common::key("uc1");
        let c2 = common::key("uc2");
        subscriber
            .subscribe(&[c1.as_str(), c2.as_str()])
            .await
            .unwrap();
        assert!(
            common::wait_for_numsub(&publisher, &c1, |n| n >= 1, Duration::from_secs(3)).await,
            "subscription c1 not registered in time"
        );
        assert!(publisher.publish(&c1, "x").await.unwrap() >= 1);

        // Unsubscribe from ALL exact channels (empty slice).
        subscriber.unsubscribe(&[] as &[&str]).await.unwrap();
        assert!(
            common::wait_for_numsub(&publisher, &c1, |n| n == 0, Duration::from_secs(3)).await
                && common::wait_for_numsub(&publisher, &c2, |n| n == 0, Duration::from_secs(3))
                    .await,
            "unsubscribe-all did not take effect server-side in time"
        );
        assert_eq!(publisher.publish(&c1, "y").await.unwrap(), 0);
        assert_eq!(publisher.publish(&c2, "z").await.unwrap(), 0);
    }
);
