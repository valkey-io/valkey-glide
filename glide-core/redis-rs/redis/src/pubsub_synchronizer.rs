// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use async_trait::async_trait;
use crate::{Cmd, RedisResult, Value};
use std::collections::{HashMap, HashSet};

/// Type of PubSub subscription
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum SubscriptionType {
    /// Exact channel name match
    Exact,
    /// Pattern-based subscription (glob patterns)
    Pattern,
    /// Sharded PubSub channel (cluster mode only)
    Sharded,
}

/// Trait for managing PubSub subscription synchronization between desired and actual state.
#[async_trait]
pub trait PubSubSynchronizer: Send + Sync {
    /// Add channels to desired subscriptions
    async fn add_desired_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    );

    /// Remove channels from desired subscriptions
    /// If channels is None, remove all subscriptions of this type
    async fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<String>>,
        subscription_type: SubscriptionType,
    );

    /// Add channels to current (actual) subscriptions
    async fn add_current_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    );

    /// Remove channels from current (actual) subscriptions
    async fn remove_current_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    );

    /// Get the current state of both desired and actual subscriptions
    async fn get_subscription_state(
        &self,
    ) -> (
        HashMap<String, HashSet<String>>,
        HashMap<String, HashSet<String>>,
    );

    /// Reconcile desired and actual subscriptions
    async fn reconcile(&self) -> Result<(), String>;

    /// Check if desired and actual subscriptions are synchronized
    async fn is_synchronized(&self) -> bool {
        let (desired, actual) = self.get_subscription_state().await;
        desired == actual
    }

    /// Remove all current subscriptions associated with a specific address
    /// This is called when a node disconnects, so reconciliation can restore them
    async fn remove_current_subscriptions_for_address(&self, _address: &str) {
        // Default: no-op
    }

    /// Try to intercept and handle a pubsub command
    /// Returns Some(result) if the command was handled, None if it should go through normal path
    async fn intercept_pubsub_command(&self, _cmd: &Cmd) -> Option<RedisResult<Value>> {
        // Default: don't intercept
        None
    }

    /// Set initial subscriptions from client configuration and trigger immediate reconciliation
    async fn set_initial_subscriptions(
        &self,
        channels: HashSet<String>,
        patterns: HashSet<String>,
        sharded: HashSet<String>,
    ) {
        if !channels.is_empty() {
            self.add_desired_subscriptions(channels, SubscriptionType::Exact).await;
        }
        if !patterns.is_empty() {
            self.add_desired_subscriptions(patterns, SubscriptionType::Pattern).await;
        }
        if !sharded.is_empty() {
            self.add_desired_subscriptions(sharded, SubscriptionType::Sharded).await;
        }
        let _ = self.reconcile().await;
    }
    
    /// Allows downcasting to concrete types
    /// This is needed for glide-core to call implementation-specific methods
    fn as_any(&self) -> &dyn std::any::Any;
}