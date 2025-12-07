// glide-core/src/pubsub/mod.rs

use std::sync::{Arc, Weak};
use tokio::sync::RwLock;
pub use redis::pubsub_synchronizer::{PubSubSynchronizer, SubscriptionType};

#[cfg(feature = "mock-pubsub")]
mod mock;

#[cfg(feature = "mock-pubsub")]
pub use mock::{MockPubSubBroker, get_mock_broker};

#[cfg(not(feature = "mock-pubsub"))]
mod real;

/// Factory function to create a synchronizer
pub fn create_pubsub_synchronizer(
    cluster_mode: bool,
    initial_subscriptions: Option<redis::PubSubSubscriptionInfo>,
) -> Arc<dyn PubSubSynchronizer> {
    #[cfg(feature = "mock-pubsub")]
    {
        mock::MockPubSubSynchronizer::new(cluster_mode, initial_subscriptions)
    }
    
    #[cfg(not(feature = "mock-pubsub"))]
    {
        real::RealPubSubSynchronizer::new(cluster_mode, initial_subscriptions)
    }
}

/// Helper function to set client reference on synchronizer
pub fn set_synchronizer_client(
    sync: &Arc<dyn PubSubSynchronizer>,
    client: Weak<RwLock<crate::client::Client>>,
) -> Result<(), String> {
    let any = sync.as_any();
    
    #[cfg(feature = "mock-pubsub")]
    {
        if let Some(mock_sync) = any.downcast_ref::<mock::MockPubSubSynchronizer>() {
            return mock_sync.set_client(client);
        }
    }
    
    #[cfg(not(feature = "mock-pubsub"))]
    {
        if let Some(real_sync) = any.downcast_ref::<real::RealPubSubSynchronizer>() {
            return real_sync.set_client(client);
        }
    }
    
    Err("Unknown synchronizer type".to_string())
}