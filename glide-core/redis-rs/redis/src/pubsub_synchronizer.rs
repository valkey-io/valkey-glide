// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::cluster_slotmap::SlotMap;
use crate::connection::{PubSubChannelOrPattern, PubSubSubscriptionKind};
use crate::{Cmd, RedisResult, Value};
use async_trait::async_trait;
use std::collections::{HashMap, HashSet};

/// Trait for managing PubSub subscription synchronization between desired and actual state.
#[async_trait]
pub trait PubSubSynchronizer: Send + Sync {
    /// Add channels to desired subscriptions
    async fn add_desired_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
    );

    /// Remove channels from desired subscriptions
    /// If channels is None, remove all subscriptions of this type
    async fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<PubSubChannelOrPattern>>,
        subscription_type: PubSubSubscriptionKind,
    );

    /// Add channels to current (actual) subscriptions
    async fn add_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    );

    /// Remove channels from current (actual) subscriptions
    async fn remove_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    );

    /// Get the current state of both desired and actual subscriptions
    /// Returns (desired, actual) where each is a map of subscription type name to channels
    async fn get_subscription_state(
        &self,
    ) -> (
        HashMap<PubSubSubscriptionKind, HashSet<PubSubChannelOrPattern>>,
        HashMap<PubSubSubscriptionKind, HashSet<PubSubChannelOrPattern>>,
    );

    /// Trigger the reconciliation task to run immediately (non-blocking)
    async fn trigger_reconciliation(&self);

    /// Check if desired and actual subscriptions are synchronized
    async fn is_synchronized(&self) -> bool {
        let (desired, actual) = self.get_subscription_state().await;
        desired == actual
    }

    /// Remove all current subscriptions associated with specific addresses
    /// This is called when a node disconnects, so reconciliation can restore them
    fn remove_current_subscriptions_for_addresses(
        &self,
        _addresses: &std::collections::HashSet<String>,
    ) {
        // Default: no-op
    }

    /// Handle a topology refresh event.
    ///
    /// This method is called when the cluster topology changes (e.g., after slot refresh,
    /// node failover, or cluster rebalancing).
    fn handle_topology_refresh(&self, _new_slot_map: &SlotMap) {
        // Default: no-op
    }

    /// Try to intercept and handle a pubsub command.
    /// Returns Some(result) if the command was handled, None if it should go through normal path.
    async fn intercept_pubsub_command(&self, _cmd: &Cmd) -> Option<RedisResult<Value>> {
        // Default: don't intercept
        None
    }

    /// Set initial subscriptions from client configuration and trigger immediate reconciliation
    async fn set_initial_subscriptions(
        &self,
        _channels: HashSet<PubSubChannelOrPattern>,
        _patterns: HashSet<PubSubChannelOrPattern>,
        _sharded: HashSet<PubSubChannelOrPattern>,
    ) {
        // Default: no-op
    }

    /// Allows downcasting to concrete types
    /// This is needed for glide-core to call implementation-specific methods
    fn as_any(&self) -> &dyn std::any::Any;
}
