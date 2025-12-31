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
    fn add_desired_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
    );

    /// Remove channels from desired subscriptions
    /// If channels is None, remove all subscriptions of this type
    fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<PubSubChannelOrPattern>>,
        subscription_type: PubSubSubscriptionKind,
    );

    /// Add channels to current (actual) subscriptions
    fn add_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    );

    /// Remove channels from current (actual) subscriptions
    fn remove_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    );

    /// Get the current state of both desired and actual subscriptions
    fn get_subscription_state(
        &self,
    ) -> (
        HashMap<PubSubSubscriptionKind, HashSet<PubSubChannelOrPattern>>,
        HashMap<PubSubSubscriptionKind, HashSet<PubSubChannelOrPattern>>,
    );

    /// Trigger the reconciliation task to run immediately (non-blocking)
    fn trigger_reconciliation(&self);

    /// Check if desired and actual subscriptions are synchronized
    fn is_synchronized(&self) -> bool {
        let (desired, actual) = self.get_subscription_state();
        desired == actual
    }

    /// Remove all current subscriptions associated with specific addresses
    fn remove_current_subscriptions_for_addresses(&self, _addresses: &HashSet<String>) {
        // Default: no-op
    }

    /// Handle a topology refresh event
    fn handle_topology_refresh(&self, _new_slot_map: &SlotMap) {
        // Default: no-op
    }

    /// Try to intercept and handle a pubsub command.
    /// Returns Some(result) if the command was handled, None if it should go through normal path.
    /// This is async because it may involve blocking waits and message delivery.
    async fn intercept_pubsub_command(&self, _cmd: &Cmd) -> Option<RedisResult<Value>> {
        None
    }

    /// Wait for initial subscriptions (set via config) to be synchronized.
    /// Returns Ok(()) when all desired subscriptions are established, or error on timeout.
    /// Default implementation returns immediately (no-op for clients without initial subscriptions).
    async fn wait_for_initial_sync(&self, _timeout_ms: u64) -> RedisResult<()> {
        Ok(())
    }

    /// Allows downcasting to concrete types
    fn as_any(&self) -> &dyn std::any::Any;
}
