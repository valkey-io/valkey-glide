// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::PubSubCommandApplier;
use redis::PushInfo;
pub use redis::{
    ErrorKind, PubSubChannelOrPattern, PubSubSubscriptionInfo, PubSubSubscriptionKind,
    PubSubSynchronizer, RedisError,
};
use std::sync::{Arc, Weak};
use tokio::sync::mpsc;

#[cfg(feature = "mock-pubsub")]
mod mock;

#[cfg(feature = "mock-pubsub")]
pub use mock::MockPubSubBroker;

#[cfg(not(feature = "mock-pubsub"))]
mod synchronizer;

/// Factory function to create a synchronizer
pub async fn create_pubsub_synchronizer(
    _push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    initial_subscriptions: Option<redis::PubSubSubscriptionInfo>,
    is_cluster: bool,
) -> Arc<dyn PubSubSynchronizer> {
    #[cfg(feature = "mock-pubsub")]
    {
        mock::MockPubSubSynchronizer::create(_push_sender, initial_subscriptions, is_cluster).await
    }

    #[cfg(not(feature = "mock-pubsub"))]
    {
        synchronizer::GlidePubSubSynchronizer::create(initial_subscriptions, is_cluster).await
    }
}

/// Helper function to set command applier on synchronizer
pub fn set_synchronizer_applier(
    sync: &Arc<dyn PubSubSynchronizer>,
    applier: Weak<dyn PubSubCommandApplier>,
) {
    let any = sync.as_any();

    #[cfg(feature = "mock-pubsub")]
    {
        let mock_sync = any
            .downcast_ref::<mock::MockPubSubSynchronizer>()
            .expect("Expected MockPubSubSynchronizer");
        mock_sync.set_applier(applier);
    }

    #[cfg(not(feature = "mock-pubsub"))]
    {
        let real_sync = any
            .downcast_ref::<synchronizer::GlidePubSubSynchronizer>()
            .expect("Expected GlidePubSubSynchronizer");
        real_sync.set_applier(applier);
    }
}
