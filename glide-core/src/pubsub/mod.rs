// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::PubSubCommandApplier; // Import from client
use redis::PushInfo;
pub use redis::{PubSubSubscriptionKind, PubSubSynchronizer};
use std::sync::{Arc, Weak};
use tokio::sync::mpsc;

#[cfg(feature = "mock-pubsub")]
mod mock;

#[cfg(feature = "mock-pubsub")]
pub use mock::MockPubSubBroker;

#[cfg(not(feature = "mock-pubsub"))]
mod real;

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
        real::RealPubSubSynchronizer::create(initial_subscriptions, is_cluster).await
    }
}

/// Helper function to set command applier on synchronizer
pub fn set_synchronizer_applier(
    sync: &Arc<dyn PubSubSynchronizer>,
    applier: Weak<dyn PubSubCommandApplier>,
    // REMOVE is_cluster parameter
) -> Result<(), String> {
    let any = sync.as_any();

    #[cfg(feature = "mock-pubsub")]
    {
        if let Some(mock_sync) = any.downcast_ref::<mock::MockPubSubSynchronizer>() {
            return mock_sync.set_applier(applier);
        }
    }

    #[cfg(not(feature = "mock-pubsub"))]
    {
        if let Some(real_sync) = any.downcast_ref::<real::RealPubSubSynchronizer>() {
            return real_sync.set_applier(applier);
        }
    }

    Err("Unknown synchronizer type".to_string())
}
