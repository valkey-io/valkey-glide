// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::{ClientWrapper, PubSubCommandApplier};
use async_trait::async_trait;
use logger_core::{log_debug, log_error, log_warn};
use once_cell::sync::OnceCell;
use redis::{
    Cmd, ErrorKind, PubSubChannelOrPattern, PubSubSubscriptionInfo, PubSubSubscriptionKind,
    PubSubSynchronizer, RedisError, RedisResult, SlotMap, Value, cluster_routing::Routable,
    cluster_routing::SingleNodeRoutingInfo,
};
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex, RwLock, Weak};
use std::time::{Duration, Instant};
use telemetrylib::GlideOpenTelemetry;
use tokio::sync::{Notify, RwLock as TokioRwLock};

const LOCK_ERR: &str = "Lock poisoned";
const DEFAULT_RECONCILIATION_INTERVAL: Duration = Duration::from_secs(3);

/// Static slices for subscription kinds - no allocation
const CLUSTER_SUBSCRIPTION_KINDS: &[PubSubSubscriptionKind] = &[
    PubSubSubscriptionKind::Exact,
    PubSubSubscriptionKind::Pattern,
    PubSubSubscriptionKind::Sharded,
];

const STANDALONE_SUBSCRIPTION_KINDS: &[PubSubSubscriptionKind] = &[
    PubSubSubscriptionKind::Exact,
    PubSubSubscriptionKind::Pattern,
];

/// Result of checking synchronization state - avoids recomputation
struct SyncDiff {
    is_synchronized: bool,
    to_subscribe: PubSubSubscriptionInfo,
    to_unsubscribe_by_address: HashMap<String, PubSubSubscriptionInfo>,
}

/// Glide PubSub Synchronizer
///
/// Implements the observer pattern for managing PubSub subscriptions:
/// - `desired_subscriptions`: What the user wants to be subscribed to (modified by API calls)
/// - `current_subscriptions_by_address`: What we're actually subscribed to (updated by push notifications)
///
/// A background reconciliation task continuously aligns current subscriptions with desired subscriptions.
pub struct GlidePubSubSynchronizer {
    /// Weak reference to internal client wrapper (set once during init)
    internal_client: OnceCell<Weak<TokioRwLock<ClientWrapper>>>,

    /// Whether this is a cluster client
    is_cluster: bool,

    /// What the user wants to be subscribed to (modified by API calls)
    desired_subscriptions: RwLock<PubSubSubscriptionInfo>,

    /// What we're actually subscribed to, tracked by address for topology handling
    current_subscriptions_by_address: RwLock<HashMap<String, PubSubSubscriptionInfo>>,

    /// Notifier to trigger reconciliation task
    reconciliation_notify: Notify,

    /// Notifier for when reconciliation completes a cycle
    reconciliation_complete_notify: Notify,

    /// Handle to the reconciliation task
    reconciliation_task_handle: Mutex<Option<tokio::task::JoinHandle<()>>>,

    /// Pending unsubscribes due to topology change that need to be sent to specific addresses
    pending_unsubscribes: RwLock<HashMap<String, PubSubSubscriptionInfo>>,

    /// Configurable reconciliation interval
    reconciliation_interval: Duration,

    /// Request timeout for non-blocking operations
    request_timeout: Duration,
}

impl GlidePubSubSynchronizer {
    pub fn new(
        initial_subscriptions: Option<PubSubSubscriptionInfo>,
        is_cluster: bool,
        reconciliation_interval: Option<Duration>,
        request_timeout: Duration,
    ) -> Arc<Self> {
        let interval = reconciliation_interval.unwrap_or(DEFAULT_RECONCILIATION_INTERVAL);

        let sync = Arc::new(Self {
            internal_client: OnceCell::new(),
            is_cluster,
            desired_subscriptions: RwLock::new(initial_subscriptions.unwrap_or_default()),
            current_subscriptions_by_address: RwLock::new(HashMap::new()),
            reconciliation_notify: Notify::new(),
            reconciliation_complete_notify: Notify::new(),
            reconciliation_task_handle: Mutex::new(None),
            pending_unsubscribes: RwLock::new(HashMap::new()),
            reconciliation_interval: interval,
            request_timeout,
        });

        sync.start_reconciliation_task();
        sync
    }

    pub fn set_internal_client(&self, client: Weak<TokioRwLock<ClientWrapper>>) {
        let _ = self.internal_client.set(client);
    }

    /// Returns slice of applicable subscription kinds - zero allocation
    #[inline]
    fn subscription_kinds(&self) -> &'static [PubSubSubscriptionKind] {
        if self.is_cluster {
            CLUSTER_SUBSCRIPTION_KINDS
        } else {
            STANDALONE_SUBSCRIPTION_KINDS
        }
    }

    // get the actual subscriptions not by address
    fn compute_actual_subscriptions(&self) -> PubSubSubscriptionInfo {
        let current_by_addr = self
            .current_subscriptions_by_address
            .read()
            .expect(LOCK_ERR);

        let mut actual: PubSubSubscriptionInfo = self
            .subscription_kinds()
            .iter()
            .map(|k| (*k, HashSet::new()))
            .collect();

        for subs in current_by_addr.values() {
            for (kind, channels) in subs.iter() {
                actual
                    .get_mut(kind)
                    .unwrap()
                    .extend(channels.iter().cloned());
            }
        }

        actual
    }

    /// Compute synchronization diff - what needs to be subscribed/unsubscribed
    fn compute_sync_diff(&self) -> SyncDiff {
        let desired = self.desired_subscriptions.read().expect(LOCK_ERR).clone();
        let current_by_addr = self
            .current_subscriptions_by_address
            .read()
            .expect(LOCK_ERR);

        let mut actual: PubSubSubscriptionInfo = self
            .subscription_kinds()
            .iter()
            .map(|k| (*k, HashSet::new()))
            .collect();

        let mut to_unsubscribe_by_address: HashMap<String, PubSubSubscriptionInfo> = HashMap::new();

        // Pass 1: O(current_subscriptions)
        // Iterate over current subscriptions and add to to_unsub each subscription not in desired
        for (addr, subs) in current_by_addr.iter() {
            for (kind, channels) in subs.iter() {
                actual
                    .get_mut(kind)
                    .unwrap()
                    .extend(channels.iter().cloned());

                let desired_for_kind = desired.get(kind);

                let to_unsub: HashSet<_> = channels
                    .iter()
                    .filter(|ch| desired_for_kind.is_none_or(|d| !d.contains(*ch)))
                    .cloned()
                    .collect();

                if !to_unsub.is_empty() {
                    to_unsubscribe_by_address
                        .entry(addr.clone())
                        .or_default()
                        .entry(*kind)
                        .or_default()
                        .extend(to_unsub);
                }
            }
        }

        let mut to_subscribe = PubSubSubscriptionInfo::new();

        // Pass 2: O(desired_subscriptions)
        // Iterate over desired subscriptions and add to to_sub each subscription not in actual
        for kind in self.subscription_kinds() {
            if let Some(desired_channels) = desired.get(kind) {
                let actual_channels = actual.get(kind);

                let to_sub: HashSet<_> = desired_channels
                    .iter()
                    .filter(|ch| actual_channels.is_none_or(|a| !a.contains(*ch)))
                    .cloned()
                    .collect();

                if !to_sub.is_empty() {
                    to_subscribe.insert(*kind, to_sub);
                }
            }
        }

        let is_synchronized = to_subscribe.is_empty() && to_unsubscribe_by_address.is_empty();

        SyncDiff {
            is_synchronized,
            to_subscribe,
            to_unsubscribe_by_address,
        }
    }

    /// Check sync state and update metrics - single computation
    fn check_and_record_sync_state(&self) {
        let state = self.compute_sync_diff();

        if state.is_synchronized {
            let _ = GlideOpenTelemetry::update_subscription_last_sync_timestamp();
            return;
        }

        let _ = GlideOpenTelemetry::record_subscription_out_of_sync();
    }

    async fn apply_pubsub(
        &self,
        cmd: &mut Cmd,
        routing: Option<SingleNodeRoutingInfo>,
    ) -> RedisResult<Value> {
        let client_arc = self
            .internal_client
            .get()
            .ok_or_else(|| {
                RedisError::from((
                    ErrorKind::ClientError,
                    "Internal client not set in synchronizer",
                ))
            })?
            .upgrade()
            .ok_or_else(|| {
                RedisError::from((ErrorKind::ClientError, "Internal client has been dropped"))
            })?;

        // Clone the client wrapper to release lock before await
        let mut client_wrapper = {
            let guard = client_arc.read().await;
            guard.clone()
        };

        client_wrapper.apply_pubsub_command(cmd, routing).await
    }

    /// Parse an address string into SingleNodeRoutingInfo::ByAddress
    fn parse_address_to_routing(address: &str) -> RedisResult<SingleNodeRoutingInfo> {
        let (host, port_str) = address.rsplit_once(':').ok_or_else(|| {
            RedisError::from((
                ErrorKind::ClientError,
                "Invalid address format",
                address.to_string(),
            ))
        })?;

        let port = port_str
            .parse()
            .map_err(|_| RedisError::from((ErrorKind::ClientError, "Invalid port")))?;

        Ok(SingleNodeRoutingInfo::ByAddress {
            host: host.to_string(),
            port,
        })
    }

    fn start_reconciliation_task(self: &Arc<Self>) {
        // Create a Weak pointer for the spawned task. This is necessary because:
        // 1. tokio::spawn requires 'static lifetime - we can't use a borrowed reference
        // 2. Using Arc::clone would prevent the synchronizer from ever being dropped (memory leak)
        // 3. Weak doesn't increment the strong count, so when all external strong refs are dropped,
        //    the synchronizer is dropped and weak.upgrade() returns None, signaling the task to exit
        let sync_weak = Arc::downgrade(self);
        let interval = self.reconciliation_interval;

        let handle = tokio::spawn(async move {
            loop {
                let Some(sync) = sync_weak.upgrade() else {
                    break;
                };
                tokio::select! {
                    _ = sync.reconciliation_notify.notified() => {},
                    _ = tokio::time::sleep(interval) => {},
                }

                if let Err(e) = sync.reconcile().await {
                    log_error(
                        "pubsub_synchronizer",
                        format!("Reconciliation failed: {:?}", e),
                    );
                }

                sync.check_and_record_sync_state();
                sync.reconciliation_complete_notify.notify_waiters();
            }
        });

        *self.reconciliation_task_handle.lock().unwrap() = Some(handle);
    }

    async fn reconcile(&self) -> RedisResult<()> {
        // This are unsubscription stemming from slot migrations (we do them for load balancing reasons)
        // We need to process them first in order to get a correct view of our sync state during reconciliation
        self.process_pending_unsubscribes().await;

        let diff = self.compute_sync_diff();

        if diff.is_synchronized {
            return Ok(());
        }

        for (kind, channels) in diff.to_subscribe {
            self.execute_subscription_change(channels, kind, true, None)
                .await;
        }

        for (addr, subs_by_kind) in diff.to_unsubscribe_by_address {
            let routing = Self::parse_address_to_routing(&addr).ok();

            for (kind, channels) in subs_by_kind {
                if kind == PubSubSubscriptionKind::Sharded {
                    self.execute_sharded_unsubscribe_by_slot(channels, routing.clone())
                        .await;
                } else {
                    self.execute_subscription_change(channels, kind, false, routing.clone())
                        .await;
                }
            }
        }

        Ok(())
    }

    async fn process_pending_unsubscribes(&self) {
        let pending = {
            let mut guard = self.pending_unsubscribes.write().expect(LOCK_ERR);
            std::mem::take(&mut *guard)
        };

        if pending.is_empty() {
            return;
        }

        for (address, subs_by_kind) in pending {
            let routing = match Self::parse_address_to_routing(&address) {
                Ok(r) => Some(r),
                Err(e) => {
                    log_warn(
                        "pubsub_synchronizer",
                        format!("Failed to parse address '{}': {:?}", address, e),
                    );
                    continue;
                }
            };

            for (kind, channels) in subs_by_kind {
                if channels.is_empty() {
                    continue;
                }

                if kind == PubSubSubscriptionKind::Sharded {
                    self.execute_sharded_unsubscribe_by_slot(channels, routing.clone())
                        .await;
                } else {
                    self.execute_subscription_change(channels, kind, false, routing.clone())
                        .await;
                }
            }
        }
    }

    /// Execute a subscription change (subscribe or unsubscribe)
    async fn execute_subscription_change(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        kind: PubSubSubscriptionKind,
        is_subscribe: bool,
        routing: Option<SingleNodeRoutingInfo>,
    ) {
        if channels.is_empty() {
            return;
        }

        let cmd_name = match (kind, is_subscribe) {
            (PubSubSubscriptionKind::Exact, true) => "SUBSCRIBE",
            (PubSubSubscriptionKind::Exact, false) => "UNSUBSCRIBE",
            (PubSubSubscriptionKind::Pattern, true) => "PSUBSCRIBE",
            (PubSubSubscriptionKind::Pattern, false) => "PUNSUBSCRIBE",
            (PubSubSubscriptionKind::Sharded, true) => "SSUBSCRIBE",
            (PubSubSubscriptionKind::Sharded, false) => "SUNSUBSCRIBE",
        };

        let mut cmd = redis::cmd(cmd_name);
        for channel in &channels {
            cmd.arg(channel.as_slice());
        }

        if kind == PubSubSubscriptionKind::Sharded && !is_subscribe {
            cmd.set_fenced(true);
        }

        let action = if is_subscribe {
            "subscribe"
        } else {
            "unsubscribe"
        };
        match self.apply_pubsub(&mut cmd, routing).await {
            Ok(_) => {}
            Err(e) => {
                log_error(
                    "pubsub_synchronizer",
                    format!("Failed to {} {:?} channels: {:?}", action, kind, e),
                );
            }
        }
    }

    /// Execute sharded unsubscribe, grouping by slot
    async fn execute_sharded_unsubscribe_by_slot(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        routing: Option<SingleNodeRoutingInfo>,
    ) {
        // Group channels by slot
        let channels_by_slot: HashMap<u16, HashSet<_>> =
            channels.into_iter().fold(HashMap::new(), |mut acc, chan| {
                let slot = redis::cluster_topology::get_slot(&chan);
                acc.entry(slot).or_default().insert(chan);
                acc
            });

        for (_, slot_channels) in channels_by_slot {
            self.execute_subscription_change(
                slot_channels,
                PubSubSubscriptionKind::Sharded,
                false,
                routing.clone(),
            )
            .await;
        }
    }

    fn extract_channels_from_cmd(cmd: &Cmd) -> Vec<PubSubChannelOrPattern> {
        cmd.args_iter()
            .skip(1)
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(bytes.to_vec()),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    /// Extract channels and timeout from a blocking subscription command.
    ///
    /// For blocking operations, the format is: COMMAND channel1 channel2 ... timeout_ms
    /// The last argument is always the timeout in milliseconds.
    /// A timeout of 0 means block indefinitely until the operation completes.
    fn extract_channels_and_timeout(cmd: &Cmd) -> (Vec<PubSubChannelOrPattern>, u64) {
        let args: Vec<_> = cmd
            .args_iter()
            .skip(1) // Skip command name
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(bytes.to_vec()),
                redis::Arg::Cursor => None,
            })
            .collect();

        // Must have at least the timeout argument
        if args.is_empty() {
            return (Vec::new(), 0);
        }

        // Last argument is always the timeout for blocking operations
        let timeout_ms = args
            .last()
            .and_then(|arg| String::from_utf8_lossy(arg).parse::<u64>().ok())
            .unwrap_or(0);

        // All arguments except the last are channels
        let channels = if args.len() > 1 {
            args[..args.len() - 1].to_vec()
        } else {
            Vec::new()
        };

        (channels, timeout_ms)
    }

    /// Handle lazy subscription change (subscribe or unsubscribe)
    fn handle_lazy_subscription(
        &self,
        cmd: &Cmd,
        kind: PubSubSubscriptionKind,
        is_subscribe: bool,
    ) -> RedisResult<Value> {
        let channels = Self::extract_channels_from_cmd(cmd);

        if is_subscribe && channels.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "No channels provided for subscription",
            )));
        }

        let channels_set = if channels.is_empty() {
            None
        } else {
            Some(channels.into_iter().collect())
        };

        if is_subscribe {
            self.add_desired_subscriptions(channels_set.unwrap(), kind);
        } else {
            self.remove_desired_subscriptions(channels_set, kind);
        }

        Ok(Value::Nil)
    }

    /// Handle blocking subscription change (subscribe or unsubscribe)
    async fn handle_blocking_subscription(
        &self,
        cmd: &Cmd,
        kind: PubSubSubscriptionKind,
        is_subscribe: bool,
    ) -> RedisResult<Value> {
        let (channels, timeout_ms) = Self::extract_channels_and_timeout(cmd);

        if is_subscribe && channels.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "No channels provided for subscription",
            )));
        }

        let channels_set: HashSet<PubSubChannelOrPattern> = channels.into_iter().collect();

        if is_subscribe {
            self.add_desired_subscriptions(channels_set.clone(), kind);
        } else {
            let to_remove = if channels_set.is_empty() {
                None
            } else {
                Some(channels_set.clone())
            };
            self.remove_desired_subscriptions(to_remove, kind);
        }

        // Build expected args based on subscription kind
        let (expected_channels, expected_patterns, expected_sharded) = match kind {
            PubSubSubscriptionKind::Exact => (Some(channels_set), None, None),
            PubSubSubscriptionKind::Pattern => (None, Some(channels_set), None),
            PubSubSubscriptionKind::Sharded => (None, None, Some(channels_set)),
        };

        self.wait_for_sync(
            timeout_ms,
            expected_channels,
            expected_patterns,
            expected_sharded,
        )
        .await?;

        Ok(Value::Nil)
    }

    /// Convert subscription state to Value for GET_SUBSCRIPTIONS response
    fn get_subscriptions_as_value(&self) -> Value {
        let (desired, actual) = self.get_subscription_state();

        Value::Array(vec![
            Value::BulkString(b"desired".to_vec()),
            Self::convert_sub_map_to_value(desired),
            Value::BulkString(b"actual".to_vec()),
            Self::convert_sub_map_to_value(actual),
        ])
    }

    fn convert_sub_map_to_value(map: PubSubSubscriptionInfo) -> Value {
        let subscriptions_map: Vec<_> = map
            .into_iter()
            .map(|(kind, values)| {
                let key = match kind {
                    PubSubSubscriptionKind::Exact => "Exact",
                    PubSubSubscriptionKind::Pattern => "Pattern",
                    PubSubSubscriptionKind::Sharded => "Sharded",
                };
                let values_array: Vec<Value> = values.into_iter().map(Value::BulkString).collect();
                (
                    Value::BulkString(key.as_bytes().to_vec()),
                    Value::Array(values_array),
                )
            })
            .collect();
        Value::Map(subscriptions_map)
    }

    /// Get current subscriptions organized by address.
    /// Used for testing to verify subscriptions moved to different addresses after slot migration.
    pub fn get_current_subscriptions_by_address(&self) -> HashMap<String, PubSubSubscriptionInfo> {
        self.current_subscriptions_by_address
            .read()
            .expect(LOCK_ERR)
            .clone()
    }

    /// Run a synchronous operation with a timeout
    async fn run_sync_with_timeout<T, F>(&self, f: F) -> RedisResult<T>
    where
        F: FnOnce() -> RedisResult<T> + Send,
        T: Send,
    {
        match tokio::time::timeout(self.request_timeout, async move { f() }).await {
            Ok(result) => result,
            Err(_) => Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into()),
        }
    }
}

impl Drop for GlidePubSubSynchronizer {
    fn drop(&mut self) {
        if let Some(handle) = self.reconciliation_task_handle.lock().unwrap().take() {
            handle.abort();
        }
    }
}

#[async_trait]
impl PubSubSynchronizer for GlidePubSubSynchronizer {
    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    fn add_desired_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
    ) {
        {
            let mut desired = self.desired_subscriptions.write().expect(LOCK_ERR);
            desired
                .entry(subscription_type)
                .or_default()
                .extend(channels);
        }

        self.trigger_reconciliation();
    }

    fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<PubSubChannelOrPattern>>,
        subscription_type: PubSubSubscriptionKind,
    ) {
        {
            let mut desired = self.desired_subscriptions.write().expect(LOCK_ERR);
            match channels {
                Some(channels_to_remove) => {
                    if let Some(existing) = desired.get_mut(&subscription_type) {
                        for channel in channels_to_remove {
                            existing.remove(&channel);
                        }
                    }
                }
                None => {
                    desired.remove(&subscription_type);
                }
            }
        }

        self.trigger_reconciliation();
    }

    fn add_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    ) {
        let mut current_by_addr = self
            .current_subscriptions_by_address
            .write()
            .expect(LOCK_ERR);
        current_by_addr
            .entry(address)
            .or_default()
            .entry(subscription_type)
            .or_default()
            .extend(channels);
    }

    fn remove_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    ) {
        let mut current_by_addr = self
            .current_subscriptions_by_address
            .write()
            .expect(LOCK_ERR);

        // For sharded subscriptions, only remove from the specific address.
        // Sharded subscriptions are slot-deterministic - an unsubscribe from Node A
        // doesn't invalidate a valid subscription on Node B (the new slot owner).
        if subscription_type == PubSubSubscriptionKind::Sharded {
            if let Some(addr_subs) = current_by_addr.get_mut(&address)
                && let Some(existing) = addr_subs.get_mut(&subscription_type)
            {
                for channel in &channels {
                    existing.remove(channel);
                }
            }
        } else {
            // For regular subscriptions (Exact/Pattern), remove from ALL addresses.
            // These are not slot-bound, and the server's unsubscribe is authoritative.
            for (_, addr_subs) in current_by_addr.iter_mut() {
                if let Some(existing) = addr_subs.get_mut(&subscription_type) {
                    for channel in &channels {
                        existing.remove(channel);
                    }
                }
            }
        }

        // Clean up empty entries
        current_by_addr.retain(|_, addr_subs| {
            addr_subs.retain(|_, channels| !channels.is_empty());
            !addr_subs.is_empty()
        });
    }

    fn remove_current_subscriptions_for_addresses(&self, addresses: &HashSet<String>) {
        if addresses.is_empty() {
            return;
        }

        log_debug(
            "pubsub_synchronizer",
            format!(
                "Clearing subscriptions for disconnected addresses: {:?}",
                addresses
            ),
        );

        let mut current_by_addr = self
            .current_subscriptions_by_address
            .write()
            .expect(LOCK_ERR);

        for address in addresses {
            current_by_addr.remove(address);
        }
        self.trigger_reconciliation();
    }

    fn handle_topology_refresh(&self, new_slot_map: &SlotMap) {
        let new_addresses: HashSet<String> = new_slot_map
            .all_node_addresses()
            .iter()
            .map(|arc| arc.to_string())
            .collect();

        let mut modified = false;

        {
            let mut current_by_addr = self
                .current_subscriptions_by_address
                .write()
                .expect(LOCK_ERR);
            let mut pending = self.pending_unsubscribes.write().expect(LOCK_ERR);

            // Helper to queue an unsubscribe
            let mut queue_unsubscribe =
                |addr: &str,
                 kind: PubSubSubscriptionKind,
                 channels: HashSet<PubSubChannelOrPattern>| {
                    if channels.is_empty() {
                        return;
                    }
                    log_debug(
                        "pubsub_synchronizer",
                        format!(
                            "Slot migration detected, queueing unsubscribe for {} {:?} channels from {}",
                            channels.len(),
                            kind,
                            addr
                        ),
                    );
                    pending
                        .entry(addr.to_string())
                        .or_default()
                        .entry(kind)
                        .or_default()
                        .extend(channels);
                };

            // Collect addresses to remove entirely (node no longer exists)
            let addresses_to_remove: Vec<String> = current_by_addr
                .keys()
                .filter(|addr| !new_addresses.contains(*addr))
                .cloned()
                .collect();

            // Queue unsubscribes for removed addresses
            for addr in &addresses_to_remove {
                if let Some(addr_subs) = current_by_addr.remove(addr) {
                    for (kind, channels) in addr_subs {
                        if !channels.is_empty() {
                            queue_unsubscribe(addr, kind, channels);
                            modified = true;
                        }
                    }
                }
            }

            // Check for slot migrations on remaining addresses
            for (addr, addr_subs) in current_by_addr.iter_mut() {
                for (kind, channels) in addr_subs.iter_mut() {
                    let mut migrated_channels: HashSet<PubSubChannelOrPattern> = HashSet::new();

                    channels.retain(|channel| {
                        let slot = redis::cluster_topology::get_slot(channel);

                        match new_slot_map.shard_addrs_for_slot(slot) {
                            Some(shard_addrs) => {
                                let new_primary = shard_addrs.primary();
                                if new_primary.as_str() != addr {
                                    // Slot migrated to a different node
                                    migrated_channels.insert(channel.clone());
                                    modified = true;
                                    false
                                } else {
                                    true
                                }
                            }
                            None => {
                                // Slot has no owner - queue unsubscribe
                                migrated_channels.insert(channel.clone());
                                modified = true;
                                false
                            }
                        }
                    });

                    // Queue unsubscribes for migrated channels from this address
                    if !migrated_channels.is_empty() {
                        queue_unsubscribe(addr, *kind, migrated_channels);
                    }
                }

                // Clean up empty entries
                addr_subs.retain(|_, channels| !channels.is_empty());
            }

            // Clean up addresses with no subscriptions
            current_by_addr.retain(|_, addr_subs| !addr_subs.is_empty());
        }

        if modified {
            self.trigger_reconciliation();
        }
    }

    fn get_subscription_state(&self) -> (PubSubSubscriptionInfo, PubSubSubscriptionInfo) {
        let desired = self.desired_subscriptions.read().expect(LOCK_ERR).clone();
        let actual = self.compute_actual_subscriptions();
        (desired, actual)
    }

    fn trigger_reconciliation(&self) {
        self.reconciliation_notify.notify_one();
    }

    async fn intercept_pubsub_command(&self, cmd: &Cmd) -> Option<RedisResult<Value>> {
        let command_name = cmd.command().unwrap_or_default();
        let command_str = std::str::from_utf8(&command_name).unwrap_or("");

        match command_str {
            // Non-blocking subscribe commands
            "SUBSCRIBE" => {
                let cmd = cmd.clone();
                Some(
                    self.run_sync_with_timeout(|| {
                        self.handle_lazy_subscription(&cmd, PubSubSubscriptionKind::Exact, true)
                    })
                    .await,
                )
            }
            "PSUBSCRIBE" => {
                let cmd = cmd.clone();
                Some(
                    self.run_sync_with_timeout(|| {
                        self.handle_lazy_subscription(&cmd, PubSubSubscriptionKind::Pattern, true)
                    })
                    .await,
                )
            }
            "SSUBSCRIBE" => {
                let cmd = cmd.clone();
                Some(
                    self.run_sync_with_timeout(|| {
                        self.handle_lazy_subscription(&cmd, PubSubSubscriptionKind::Sharded, true)
                    })
                    .await,
                )
            }

            // Non-blocking unsubscribe commands
            "UNSUBSCRIBE" => {
                let cmd = cmd.clone();
                Some(
                    self.run_sync_with_timeout(|| {
                        self.handle_lazy_subscription(&cmd, PubSubSubscriptionKind::Exact, false)
                    })
                    .await,
                )
            }
            "PUNSUBSCRIBE" => {
                let cmd = cmd.clone();
                Some(
                    self.run_sync_with_timeout(|| {
                        self.handle_lazy_subscription(&cmd, PubSubSubscriptionKind::Pattern, false)
                    })
                    .await,
                )
            }
            "SUNSUBSCRIBE" => {
                let cmd = cmd.clone();
                Some(
                    self.run_sync_with_timeout(|| {
                        self.handle_lazy_subscription(&cmd, PubSubSubscriptionKind::Sharded, false)
                    })
                    .await,
                )
            }

            // Blocking subscribe commands
            "SUBSCRIBE_BLOCKING" => Some(
                self.handle_blocking_subscription(cmd, PubSubSubscriptionKind::Exact, true)
                    .await,
            ),
            "PSUBSCRIBE_BLOCKING" => Some(
                self.handle_blocking_subscription(cmd, PubSubSubscriptionKind::Pattern, true)
                    .await,
            ),
            "SSUBSCRIBE_BLOCKING" => Some(
                self.handle_blocking_subscription(cmd, PubSubSubscriptionKind::Sharded, true)
                    .await,
            ),

            // Blocking unsubscribe commands
            "UNSUBSCRIBE_BLOCKING" => Some(
                self.handle_blocking_subscription(cmd, PubSubSubscriptionKind::Exact, false)
                    .await,
            ),
            "PUNSUBSCRIBE_BLOCKING" => Some(
                self.handle_blocking_subscription(cmd, PubSubSubscriptionKind::Pattern, false)
                    .await,
            ),
            "SUNSUBSCRIBE_BLOCKING" => Some(
                self.handle_blocking_subscription(cmd, PubSubSubscriptionKind::Sharded, false)
                    .await,
            ),

            // Get subscriptions
            "GET_SUBSCRIPTIONS" => Some(
                self.run_sync_with_timeout(|| Ok(self.get_subscriptions_as_value()))
                    .await,
            ),

            // Not a pubsub command we handle
            _ => None,
        }
    }

    async fn wait_for_sync(
        &self,
        timeout_ms: u64,
        expected_channels: Option<HashSet<PubSubChannelOrPattern>>,
        expected_patterns: Option<HashSet<PubSubChannelOrPattern>>,
        expected_sharded: Option<HashSet<PubSubChannelOrPattern>>,
    ) -> RedisResult<()> {
        let deadline = if timeout_ms > 0 {
            Some(Instant::now() + Duration::from_millis(timeout_ms))
        } else {
            None
        };

        loop {
            let notified = self.reconciliation_complete_notify.notified();

            let condition_met = {
                // If no specific expectations, just check overall synchronization
                if expected_channels.is_none()
                    && expected_patterns.is_none()
                    && expected_sharded.is_none()
                {
                    self.compute_sync_diff().is_synchronized
                } else {
                    // Check that specified channels are synced (desired == actual for those channels)
                    let (desired, actual) = self.get_subscription_state();

                    let is_synced_for_channels = |channels: &Option<
                        HashSet<PubSubChannelOrPattern>,
                    >,
                                                  kind: PubSubSubscriptionKind|
                     -> bool {
                        channels.as_ref().is_none_or(|chs| {
                            let desired_set = desired.get(&kind);
                            let actual_set = actual.get(&kind);

                            if chs.is_empty() {
                                // When expecting empty set (unsubscribe-all), verify both
                                // desired and actual are empty for this subscription type
                                let desired_empty = desired_set.is_none_or(|d| d.is_empty());
                                let actual_empty = actual_set.is_none_or(|a| a.is_empty());
                                desired_empty && actual_empty
                            } else {
                                // Check each specified channel has matching state in desired and actual
                                chs.iter().all(|ch| {
                                    let in_desired = desired_set.is_some_and(|d| d.contains(ch));
                                    let in_actual = actual_set.is_some_and(|a| a.contains(ch));
                                    in_desired == in_actual
                                })
                            }
                        })
                    };

                    is_synced_for_channels(&expected_channels, PubSubSubscriptionKind::Exact)
                        && is_synced_for_channels(
                            &expected_patterns,
                            PubSubSubscriptionKind::Pattern,
                        )
                        && is_synced_for_channels(
                            &expected_sharded,
                            PubSubSubscriptionKind::Sharded,
                        )
                }
            };

            if condition_met {
                self.check_and_record_sync_state();
                return Ok(());
            }

            self.trigger_reconciliation();

            if let Some(deadline) = deadline {
                let remaining = deadline.saturating_duration_since(Instant::now());
                if remaining.is_zero() {
                    return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                }

                tokio::select! {
                    _ = notified => {}
                    _ = tokio::time::sleep(remaining) => {
                        return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                    }
                }
            } else {
                notified.await;
            }
        }
    }
}
