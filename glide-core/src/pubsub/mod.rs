// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::PushInfo;
pub use redis::{PubSubSynchronizer, SubscriptionType};
use std::sync::{Arc, Weak};
use tokio::sync::{RwLock, mpsc};

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
) -> Arc<dyn PubSubSynchronizer> {
    #[cfg(feature = "mock-pubsub")]
    {
        mock::MockPubSubSynchronizer::create(_push_sender, initial_subscriptions).await
    }

    #[cfg(not(feature = "mock-pubsub"))]
    {
        real::RealPubSubSynchronizer::create(initial_subscriptions).await
    }
}

/// Helper function to set client reference on synchronizer
pub fn set_synchronizer_client(
    sync: &Arc<dyn PubSubSynchronizer>,
    client: Weak<RwLock<crate::client::Client>>,
    is_cluster: bool, // ← Add this parameter
) -> Result<(), String> {
    let any = sync.as_any();

    #[cfg(feature = "mock-pubsub")]
    {
        if let Some(mock_sync) = any.downcast_ref::<mock::MockPubSubSynchronizer>() {
            return mock_sync.set_client(client, is_cluster);
        }
    }

    #[cfg(not(feature = "mock-pubsub"))]
    {
        if let Some(real_sync) = any.downcast_ref::<real::RealPubSubSynchronizer>() {
            return real_sync.set_client(client, is_cluster);
        }
    }

    Err("Unknown synchronizer type".to_string())
}
