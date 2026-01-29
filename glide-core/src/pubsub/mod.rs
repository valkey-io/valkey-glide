// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::ClientWrapper;
use redis::PushInfo;
pub use redis::{
    ErrorKind, PubSubChannelOrPattern, PubSubSubscriptionInfo, PubSubSubscriptionKind,
    PubSubSynchronizer, RedisError,
};
use std::sync::{Arc, Weak};
use std::time::Duration;
use tokio::sync::{RwLock, mpsc};

#[cfg(feature = "mock-pubsub")]
mod mock;

#[cfg(feature = "mock-pubsub")]
pub use mock::MockPubSubBroker;

#[cfg(not(feature = "mock-pubsub"))]
pub mod synchronizer;

/// Factory function to create a synchronizer with internal client reference
pub async fn create_pubsub_synchronizer(
    _push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    initial_subscriptions: Option<redis::PubSubSubscriptionInfo>,
    is_cluster: bool,
    internal_client: Weak<RwLock<ClientWrapper>>,
    reconciliation_interval: Option<Duration>,
    _request_timeout: Duration,
) -> Arc<dyn PubSubSynchronizer> {
    #[cfg(feature = "mock-pubsub")]
    {
        let sync = mock::MockPubSubSynchronizer::create(
            _push_sender,
            initial_subscriptions,
            is_cluster,
            reconciliation_interval,
        )
        .await;
        // Only set if the weak pointer can be upgraded (is not empty)
        if internal_client.upgrade().is_some() {
            sync.as_any()
                .downcast_ref::<mock::MockPubSubSynchronizer>()
                .expect("Expected MockPubSubSynchronizer")
                .set_internal_client(internal_client);
        }
        sync
    }

    #[cfg(not(feature = "mock-pubsub"))]
    {
        let sync = synchronizer::GlidePubSubSynchronizer::new(
            initial_subscriptions,
            is_cluster,
            reconciliation_interval,
            _request_timeout,
        );
        // Only set if the weak pointer can be upgraded (is not empty)
        // This is because OnceCell::set only works once - if we set an empty Weak::new(),
        // tests that create the synchronizer before the client won't be able to set
        // the real client later.
        if internal_client.upgrade().is_some() {
            sync.as_any()
                .downcast_ref::<synchronizer::GlidePubSubSynchronizer>()
                .expect("Expected GlidePubSubSynchronizer")
                .set_internal_client(internal_client);
        }
        sync
    }
}
