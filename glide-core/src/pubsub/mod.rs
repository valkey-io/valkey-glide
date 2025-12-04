// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use async_trait::async_trait;
use redis::{Cmd, PushInfo, RedisResult, Value};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use tokio::sync::mpsc;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum SubscriptionType {
    Exact,
    Pattern,
    Sharded,
}

/// Trait for managing PubSub subscription synchronization between desired and actual state.
#[async_trait]
pub trait PubSubSynchronizer: Send + Sync {
    async fn add_desired_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    );

    async fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<String>>,
        subscription_type: SubscriptionType,
    );

    async fn add_current_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    );

    async fn remove_current_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    );

    async fn get_subscription_state(
        &self,
    ) -> (
        HashMap<String, HashSet<String>>,
        HashMap<String, HashSet<String>>,
    );

    async fn reconcile(&self) -> Result<(), String>;

    async fn is_synchronized(&self) -> bool {
        let (desired, actual) = self.get_subscription_state().await;
        desired == actual
    }

    /// Try to intercept and handle a pubsub command
    /// Returns Some(result) if the command was handled, None if it should go through normal path
    async fn intercept_pubsub_command(&self, cmd: &Cmd) -> Option<RedisResult<Value>>;

    /// Set initial subscriptions from client configuration and trigger immediate reconciliation
    async fn set_initial_subscriptions(
        &self,
        channels: HashSet<String>,
        patterns: HashSet<String>,
        sharded: HashSet<String>,
    );
}

// Include mock implementation when feature is enabled
#[cfg(feature = "mock-pubsub")]
mod mock;

#[cfg(feature = "mock-pubsub")]
pub use mock::MockPubSubBroker;

// Include real implementation when mock is not enabled
#[cfg(not(feature = "mock-pubsub"))]
mod real;

/// Factory function to create a synchronizer
/// Client code doesn't need to know if it's mock or real
pub fn create_pubsub_synchronizer(
    cluster_mode: bool,
    push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    initial_subscriptions: Option<redis::PubSubSubscriptionInfo>,
) -> Arc<dyn PubSubSynchronizer> {
    #[cfg(feature = "mock-pubsub")]
    {
        mock::MockPubSubSynchronizer::new(cluster_mode, push_sender, initial_subscriptions)
    }
    
    #[cfg(not(feature = "mock-pubsub"))]
    {
        real::RealPubSubSynchronizer::new(cluster_mode, push_sender, initial_subscriptions)
    }
}