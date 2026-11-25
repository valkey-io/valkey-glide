// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::{ClientWrapper, PubSubCommandApplier};
use async_trait::async_trait;
use glide_logger::{log_debug, log_error, log_warn};
use glide_telemetry::GlideOpenTelemetry;
use once_cell::sync::OnceCell;
use redis::{
    Cmd, ErrorKind, PubSubChannelOrPattern, PubSubSubscriptionInfo, PubSubSubscriptionKind,
    PubSubSynchronizer, RedisError, RedisResult, SlotMap, Value, cluster_routing::Routable,
    cluster_routing::SingleNodeRoutingInfo,
};
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex, RwLock, Weak};
use std::time::{Duration, Instant};
use tokio::sync::{Notify, RwLock as TokioRwLock};

const LOCK_ERR: &str = "Lock poisoned";
const DEFAULT_RECONCILIATION_INTERVAL: Duration = Duration::from_secs(3);

/// Static slices for subscription kinds - no allocation
const CLUSTER_SUBSCRIPTION_KINDS: &[PubSubSubscriptionKind] = &[
    PubSubSubscriptionKind::Exact,
    PubSubSubscriptionKind::Pattern,
    PubSubSubscriptionKind::Sharded,
];

// TODO #7690: Reject sharded subscriptions on a standalone client with an error, instead of
// ignoring them because they aren't in this list.
const STANDALONE_SUBSCRIPTION_KINDS: &[PubSubSubscriptionKind] = &[
    PubSubSubscriptionKind::Exact,
    PubSubSubscriptionKind::Pattern,
];

/// What reconciliation needs to send
struct SyncDiff {
    is_synchronized: bool,
    to_subscribe: PubSubSubscriptionInfo,
    to_unsubscribe_by_address: HashMap<String, PubSubSubscriptionInfo>,
}

/// Desired and current subscriptions. `held` and `out_of_sync` are derived from
/// `desired` and `by_address` on every change, so reconciliation and sync checks cost
/// O(changed channels), not O(all channels). One lock covers all four so they can't drift.
struct SubscriptionState {
    /// Kinds this client reconciles (sharded only in cluster mode)
    kinds: &'static [PubSubSubscriptionKind],
    /// What the user wants to be subscribed to (modified by API calls)
    desired: PubSubSubscriptionInfo,
    /// What we're actually subscribed to, tracked by address for topology handling
    by_address: HashMap<String, PubSubSubscriptionInfo>,
    /// Number of addresses holding each channel, per kind
    held: HashMap<PubSubSubscriptionKind, HashMap<PubSubChannelOrPattern, usize>>,
    /// Channels that are desired but not held, or held but not desired. No empty sets.
    out_of_sync: PubSubSubscriptionInfo,
}

impl SubscriptionState {
    fn new(kinds: &'static [PubSubSubscriptionKind], desired: PubSubSubscriptionInfo) -> Self {
        let mut state = Self {
            kinds,
            desired,
            by_address: HashMap::new(),
            held: HashMap::new(),
            out_of_sync: PubSubSubscriptionInfo::new(),
        };
        state.out_of_sync = state.full_out_of_sync();
        state
    }

    fn is_desired(&self, kind: PubSubSubscriptionKind, channel: &[u8]) -> bool {
        self.desired.get(&kind).is_some_and(|d| d.contains(channel))
    }

    fn is_held(&self, kind: PubSubSubscriptionKind, channel: &[u8]) -> bool {
        self.held
            .get(&kind)
            .is_some_and(|h| h.contains_key(channel))
    }

    /// Re-derive `out_of_sync` membership for one channel after it changed.
    fn refresh(&mut self, kind: PubSubSubscriptionKind, channel: &[u8]) {
        if !self.kinds.contains(&kind) {
            return;
        }
        if self.is_desired(kind, channel) != self.is_held(kind, channel) {
            let set = self.out_of_sync.entry(kind).or_default();
            if !set.contains(channel) {
                set.insert(channel.to_vec());
            }
        } else if let Some(set) = self.out_of_sync.get_mut(&kind) {
            set.remove(channel);
            if set.is_empty() {
                self.out_of_sync.remove(&kind);
            }
        }
    }

    fn add_desired(
        &mut self,
        kind: PubSubSubscriptionKind,
        channels: HashSet<PubSubChannelOrPattern>,
    ) {
        for channel in channels {
            if self
                .desired
                .entry(kind)
                .or_default()
                .insert(channel.clone())
            {
                self.refresh(kind, &channel);
            }
        }
    }

    /// Remove channels from desired; `None` removes every channel of this kind.
    fn remove_desired(
        &mut self,
        kind: PubSubSubscriptionKind,
        channels: Option<HashSet<PubSubChannelOrPattern>>,
    ) {
        let removed: Vec<PubSubChannelOrPattern> = match channels {
            Some(channels) => match self.desired.get_mut(&kind) {
                Some(existing) => channels
                    .into_iter()
                    .filter(|ch| existing.remove(ch))
                    .collect(),
                None => Vec::new(),
            },
            None => self
                .desired
                .remove(&kind)
                .map(|set| set.into_iter().collect())
                .unwrap_or_default(),
        };
        for channel in &removed {
            self.refresh(kind, channel);
        }
    }

    fn add_current(&mut self, address: &str, kind: PubSubSubscriptionKind, channel: &[u8]) {
        let added = match self.by_address.get_mut(address) {
            Some(subs) => subs.entry(kind).or_default().insert(channel.to_vec()),
            None => {
                self.by_address.insert(
                    address.to_string(),
                    HashMap::from([(kind, HashSet::from([channel.to_vec()]))]),
                );
                true
            }
        };
        if added {
            let counts = self.held.entry(kind).or_default();
            match counts.get_mut(channel) {
                Some(count) => *count += 1,
                None => {
                    counts.insert(channel.to_vec(), 1);
                }
            }
            self.refresh(kind, channel);
        }
    }

    fn remove_current(&mut self, address: &str, kind: PubSubSubscriptionKind, channel: &[u8]) {
        let Some(subs) = self.by_address.get_mut(address) else {
            return;
        };
        let removed = subs.get_mut(&kind).is_some_and(|set| set.remove(channel));
        if subs.get(&kind).is_some_and(|set| set.is_empty()) {
            subs.remove(&kind);
        }
        if subs.is_empty() {
            self.by_address.remove(address);
        }
        if removed {
            self.release_held(kind, channel);
        }
    }

    /// Drop every subscription recorded for `address`, returning what it held.
    fn remove_address(&mut self, address: &str) -> Option<PubSubSubscriptionInfo> {
        let subs = self.by_address.remove(address)?;
        for (kind, channels) in &subs {
            for channel in channels {
                self.release_held(*kind, channel);
            }
        }
        Some(subs)
    }

    fn release_held(&mut self, kind: PubSubSubscriptionKind, channel: &[u8]) {
        if let Some(counts) = self.held.get_mut(&kind)
            && let Some(count) = counts.get_mut(channel)
        {
            *count -= 1;
            if *count == 0 {
                counts.remove(channel);
                if counts.is_empty() {
                    self.held.remove(&kind);
                }
            }
        }
        self.refresh(kind, channel);
    }

    /// Whether every given channel has matching desired and held state. An empty
    /// set (unsubscribe-all) means nothing of this kind is desired or held.
    fn channels_synced(
        &self,
        kind: PubSubSubscriptionKind,
        channels: &HashSet<PubSubChannelOrPattern>,
    ) -> bool {
        if channels.is_empty() {
            return self.desired.get(&kind).is_none_or(|d| d.is_empty())
                && !self.held.contains_key(&kind);
        }
        channels
            .iter()
            .all(|ch| self.is_desired(kind, ch) == self.is_held(kind, ch))
    }

    /// Aggregated current subscriptions (not by address), one entry per reconciled kind.
    fn actual(&self) -> PubSubSubscriptionInfo {
        self.kinds
            .iter()
            .map(|kind| {
                let channels = self
                    .held
                    .get(kind)
                    .map(|counts| counts.keys().cloned().collect())
                    .unwrap_or_default();
                (*kind, channels)
            })
            .collect()
    }

    /// What to send to reach the desired state, from `out_of_sync` only.
    fn diff(&self) -> SyncDiff {
        let mut to_subscribe = PubSubSubscriptionInfo::new();
        let mut to_unsubscribe_by_address: HashMap<String, PubSubSubscriptionInfo> = HashMap::new();

        for (kind, channels) in &self.out_of_sync {
            for channel in channels {
                if self.is_desired(*kind, channel) {
                    to_subscribe
                        .entry(*kind)
                        .or_default()
                        .insert(channel.clone());
                    continue;
                }
                for (addr, subs) in &self.by_address {
                    if subs.get(kind).is_some_and(|set| set.contains(channel)) {
                        to_unsubscribe_by_address
                            .entry(addr.clone())
                            .or_default()
                            .entry(*kind)
                            .or_default()
                            .insert(channel.clone());
                    }
                }
            }
        }

        SyncDiff {
            is_synchronized: self.out_of_sync.is_empty(),
            to_subscribe,
            to_unsubscribe_by_address,
        }
    }

    /// Full O(all channels) recomputation of `out_of_sync`, independent of the incremental updates.
    fn full_out_of_sync(&self) -> PubSubSubscriptionInfo {
        let mut out_of_sync = PubSubSubscriptionInfo::new();
        for kind in self.kinds {
            let mut channels: HashSet<PubSubChannelOrPattern> = self
                .desired
                .get(kind)
                .into_iter()
                .flatten()
                .filter(|ch| !self.is_held(*kind, ch))
                .cloned()
                .collect();
            channels.extend(
                self.held
                    .get(kind)
                    .into_iter()
                    .flat_map(|counts| counts.keys())
                    .filter(|ch| !self.is_desired(*kind, ch))
                    .cloned(),
            );
            if !channels.is_empty() {
                out_of_sync.insert(*kind, channels);
            }
        }
        out_of_sync
    }

    /// Full O(all channels) recomputation of `held` from `by_address`.
    fn full_held(&self) -> HashMap<PubSubSubscriptionKind, HashMap<PubSubChannelOrPattern, usize>> {
        let mut held: HashMap<PubSubSubscriptionKind, HashMap<PubSubChannelOrPattern, usize>> =
            HashMap::new();
        for subs in self.by_address.values() {
            for (kind, channels) in subs {
                for channel in channels {
                    *held
                        .entry(*kind)
                        .or_default()
                        .entry(channel.clone())
                        .or_insert(0) += 1;
                }
            }
        }
        held
    }

    /// Recompute the derived state from scratch; returns whether it had drifted.
    fn repair(&mut self) -> bool {
        let held = self.full_held();
        let held_drifted = held != self.held;
        self.held = held;
        let out_of_sync = self.full_out_of_sync();
        let drifted = held_drifted || out_of_sync != self.out_of_sync;
        self.out_of_sync = out_of_sync;
        drifted
    }
}

/// Glide PubSub Synchronizer
///
/// Implements the observer pattern for managing PubSub subscriptions:
/// - desired subscriptions: What the user wants to be subscribed to (modified by API calls)
/// - current subscriptions by address: What we're actually subscribed to (updated by push notifications)
///
/// A background reconciliation task continuously aligns current subscriptions with desired subscriptions.
pub struct GlidePubSubSynchronizer {
    /// Weak reference to internal client wrapper (set once during init)
    internal_client: OnceCell<Weak<TokioRwLock<ClientWrapper>>>,

    /// Desired and current subscriptions
    state: RwLock<SubscriptionState>,

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

        let kinds = if is_cluster {
            CLUSTER_SUBSCRIPTION_KINDS
        } else {
            STANDALONE_SUBSCRIPTION_KINDS
        };

        let sync = Arc::new(Self {
            internal_client: OnceCell::new(),
            state: RwLock::new(SubscriptionState::new(
                kinds,
                initial_subscriptions.unwrap_or_default(),
            )),
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

    /// Update sync metrics from the current state
    fn check_and_record_sync_state(&self) {
        let is_synchronized = self.state.read().expect(LOCK_ERR).out_of_sync.is_empty();

        if is_synchronized {
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
            let mut last_full = Instant::now();
            loop {
                let Some(sync) = sync_weak.upgrade() else {
                    break;
                };
                tokio::select! {
                    _ = sync.reconciliation_notify.notified() => {},
                    _ = tokio::time::sleep(interval) => {},
                }

                // Time the full pass from the last one, not from the sleep: under steady
                // churn, notifications always win the select and the sleep never completes.
                let full = last_full.elapsed() >= interval;
                if full {
                    last_full = Instant::now();
                }

                if let Err(e) = sync.reconcile(full).await {
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

    /// On a timer tick, first recompute the derived state from scratch, so a missed
    /// incremental update is repaired within one interval instead of never.
    async fn reconcile(&self, full: bool) -> RedisResult<()> {
        // This are unsubscription stemming from slot migrations (we do them for load balancing reasons)
        // We need to process them first in order to get a correct view of our sync state during reconciliation
        self.process_pending_unsubscribes().await;

        let diff = if full {
            let mut state = self.state.write().expect(LOCK_ERR);
            if state.repair() {
                log_warn(
                    "pubsub_synchronizer",
                    "Subscription state drifted from incremental tracking; repaired",
                );
            }
            state.diff()
        } else {
            self.state.read().expect(LOCK_ERR).diff()
        };

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
            Value::BulkString(b"desired".to_vec().into()),
            Self::convert_sub_map_to_value(desired),
            Value::BulkString(b"actual".to_vec().into()),
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
                let values_array: Vec<Value> = values
                    .into_iter()
                    .map(|v| Value::BulkString(v.into()))
                    .collect();
                (
                    Value::BulkString(key.as_bytes().to_vec().into()),
                    Value::Array(values_array),
                )
            })
            .collect();
        Value::Map(subscriptions_map)
    }

    /// Get current subscriptions organized by address.
    /// Used for testing to verify subscriptions moved to different addresses after slot migration.
    pub fn get_current_subscriptions_by_address(&self) -> HashMap<String, PubSubSubscriptionInfo> {
        self.state.read().expect(LOCK_ERR).by_address.clone()
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
        self.state
            .write()
            .expect(LOCK_ERR)
            .add_desired(subscription_type, channels);

        self.trigger_reconciliation();
    }

    fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<PubSubChannelOrPattern>>,
        subscription_type: PubSubSubscriptionKind,
    ) {
        self.state
            .write()
            .expect(LOCK_ERR)
            .remove_desired(subscription_type, channels);

        self.trigger_reconciliation();
    }

    fn add_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    ) {
        let mut state = self.state.write().expect(LOCK_ERR);
        for channel in &channels {
            state.add_current(&address, subscription_type, channel);
        }
    }

    fn is_current_subscription(
        &self,
        subscription_type: PubSubSubscriptionKind,
        channel: &[u8],
        address: &str,
    ) -> bool {
        self.state
            .read()
            .expect(LOCK_ERR)
            .by_address
            .get(address)
            .and_then(|subs| subs.get(&subscription_type))
            .is_some_and(|set| set.contains(channel))
    }

    fn remove_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        address: String,
    ) {
        let mut state = self.state.write().expect(LOCK_ERR);

        // For sharded subscriptions, only remove from the specific address.
        // Sharded subscriptions are slot-deterministic - an unsubscribe from Node A
        // doesn't invalidate a valid subscription on Node B (the new slot owner).
        if subscription_type == PubSubSubscriptionKind::Sharded {
            for channel in &channels {
                state.remove_current(&address, subscription_type, channel);
            }
        } else {
            // For regular subscriptions (Exact/Pattern), remove from ALL addresses.
            // These are not slot-bound, and the server's unsubscribe is authoritative.
            for channel in &channels {
                let holders: Vec<String> = state
                    .by_address
                    .iter()
                    .filter(|(_, subs)| {
                        subs.get(&subscription_type)
                            .is_some_and(|set| set.contains(channel))
                    })
                    .map(|(addr, _)| addr.clone())
                    .collect();
                for addr in holders {
                    state.remove_current(&addr, subscription_type, channel);
                }
            }
        }
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

        {
            let mut state = self.state.write().expect(LOCK_ERR);
            for address in addresses {
                state.remove_address(address);
            }
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
            let mut state = self.state.write().expect(LOCK_ERR);
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
            let addresses_to_remove: Vec<String> = state
                .by_address
                .keys()
                .filter(|addr| !new_addresses.contains(*addr))
                .cloned()
                .collect();

            // Queue unsubscribes for removed addresses
            for addr in &addresses_to_remove {
                if let Some(addr_subs) = state.remove_address(addr) {
                    for (kind, channels) in addr_subs {
                        if !channels.is_empty() {
                            queue_unsubscribe(addr, kind, channels);
                            modified = true;
                        }
                    }
                }
            }

            // Check for slot migrations on remaining addresses
            let mut migrated: Vec<(
                String,
                PubSubSubscriptionKind,
                HashSet<PubSubChannelOrPattern>,
            )> = Vec::new();
            for (addr, addr_subs) in &state.by_address {
                for (kind, channels) in addr_subs {
                    let migrated_channels: HashSet<PubSubChannelOrPattern> = channels
                        .iter()
                        .filter(|channel| {
                            let slot = redis::cluster_topology::get_slot(channel);
                            // Moved to a different node, or the slot has no owner
                            new_slot_map
                                .shard_addrs_for_slot(slot)
                                .is_none_or(|shard_addrs| shard_addrs.primary().as_str() != addr)
                        })
                        .cloned()
                        .collect();
                    if !migrated_channels.is_empty() {
                        migrated.push((addr.clone(), *kind, migrated_channels));
                    }
                }
            }

            // Queue unsubscribes for migrated channels from their old address
            for (addr, kind, channels) in migrated {
                for channel in &channels {
                    state.remove_current(&addr, kind, channel);
                }
                queue_unsubscribe(&addr, kind, channels);
                modified = true;
            }
        }

        if modified {
            self.trigger_reconciliation();
        }
    }

    fn get_subscription_state(&self) -> (PubSubSubscriptionInfo, PubSubSubscriptionInfo) {
        let state = self.state.read().expect(LOCK_ERR);
        (state.desired.clone(), state.actual())
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
                let state = self.state.read().expect(LOCK_ERR);
                // If no specific expectations, just check overall synchronization
                if expected_channels.is_none()
                    && expected_patterns.is_none()
                    && expected_sharded.is_none()
                {
                    state.out_of_sync.is_empty()
                } else {
                    // Check only the specified channels, so a waiter's cost doesn't grow
                    // with the total number of subscriptions
                    let is_synced_for_channels =
                        |channels: &Option<HashSet<PubSubChannelOrPattern>>,
                         kind: PubSubSubscriptionKind| {
                            channels
                                .as_ref()
                                .is_none_or(|chs| state.channels_synced(kind, chs))
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

#[cfg(test)]
mod tests {
    use super::*;
    use PubSubSubscriptionKind::{Exact, Pattern, Sharded};
    use rand::rngs::StdRng;
    use rand::{Rng, SeedableRng};

    const ADDRS: [&str; 3] = ["node-a:6379", "node-b:6379", "node-c:6379"];

    fn ch(i: u64) -> PubSubChannelOrPattern {
        format!("ch{i}").into_bytes()
    }

    fn assert_matches_full_recompute(state: &SubscriptionState, step: usize) {
        assert_eq!(state.held, state.full_held(), "held drifted at step {step}");
        assert_eq!(
            state.out_of_sync,
            state.full_out_of_sync(),
            "out_of_sync drifted at step {step}"
        );

        let diff = state.diff();
        assert_eq!(diff.is_synchronized, state.out_of_sync.is_empty());
        for (kind, channels) in &diff.to_subscribe {
            for channel in channels {
                assert!(state.is_desired(*kind, channel) && !state.is_held(*kind, channel));
            }
        }
        for (addr, subs) in &diff.to_unsubscribe_by_address {
            for (kind, channels) in subs {
                for channel in channels {
                    assert!(!state.is_desired(*kind, channel));
                    assert!(state.by_address[addr][kind].contains(channel));
                }
            }
        }
    }

    #[test]
    fn test_incremental_state_matches_full_recompute() {
        let mut state = SubscriptionState::new(
            CLUSTER_SUBSCRIPTION_KINDS,
            HashMap::from([(Exact, HashSet::from([ch(0), ch(1)]))]),
        );
        assert_matches_full_recompute(&state, 0);

        let kinds = [Exact, Pattern, Sharded];
        // Fixed seed, so a failure reproduces
        let mut rng = StdRng::seed_from_u64(7);
        for step in 1..=5000 {
            let kind = kinds[rng.gen_range(0..3)];
            let addr = ADDRS[rng.gen_range(0..3)];
            let channel = ch(rng.gen_range(0..40));
            match rng.gen_range(0..6) {
                0 => {
                    let count = rng.gen_range(1..=4);
                    let channels = (0..count).map(|_| ch(rng.gen_range(0..40))).collect();
                    state.add_desired(kind, channels);
                }
                1 => state.remove_desired(kind, Some(HashSet::from([channel]))),
                2 if rng.gen_range(0..20) == 0 => state.remove_desired(kind, None),
                2 | 3 => state.add_current(addr, kind, &channel),
                4 => state.remove_current(addr, kind, &channel),
                _ if rng.gen_range(0..20) == 0 => {
                    state.remove_address(addr);
                }
                _ => state.add_current(addr, kind, &channel),
            }
            assert_matches_full_recompute(&state, step);
        }
    }

    #[test]
    fn test_sharded_ignored_on_standalone() {
        let mut state = SubscriptionState::new(STANDALONE_SUBSCRIPTION_KINDS, HashMap::new());
        state.add_desired(Sharded, HashSet::from([ch(1)]));
        assert!(state.out_of_sync.is_empty());
        assert_eq!(state.full_out_of_sync(), state.out_of_sync);
    }

    #[test]
    fn test_channels_synced() {
        let mut state = SubscriptionState::new(CLUSTER_SUBSCRIPTION_KINDS, HashMap::new());
        state.add_desired(Exact, HashSet::from([ch(1), ch(2)]));
        state.add_current(ADDRS[0], Exact, &ch(1));

        assert!(state.channels_synced(Exact, &HashSet::from([ch(1)])));
        assert!(!state.channels_synced(Exact, &HashSet::from([ch(1), ch(2)])));
        // Not desired and not held counts as synced (a completed unsubscribe)
        assert!(state.channels_synced(Exact, &HashSet::from([ch(3)])));
        // Unsubscribe-all waits until nothing of the kind is desired or held
        assert!(!state.channels_synced(Exact, &HashSet::new()));
        state.remove_desired(Exact, None);
        assert!(!state.channels_synced(Exact, &HashSet::new()));
        state.remove_current(ADDRS[0], Exact, &ch(1));
        assert!(state.channels_synced(Exact, &HashSet::new()));
    }

    #[test]
    fn test_held_counts_every_address() {
        let mut state = SubscriptionState::new(CLUSTER_SUBSCRIPTION_KINDS, HashMap::new());
        state.add_current(ADDRS[0], Exact, &ch(1));
        state.add_current(ADDRS[1], Exact, &ch(1));
        state.remove_address(ADDRS[0]);
        assert!(state.is_held(Exact, &ch(1)));
        state.remove_current(ADDRS[1], Exact, &ch(1));
        assert!(!state.is_held(Exact, &ch(1)));
        assert!(state.held.is_empty() && state.by_address.is_empty());
    }

    #[test]
    fn test_repair_fixes_drift() {
        let mut state = SubscriptionState::new(CLUSTER_SUBSCRIPTION_KINDS, HashMap::new());
        state.add_desired(Exact, HashSet::from([ch(1)]));
        state.add_current(ADDRS[0], Pattern, &ch(2));
        assert!(!state.repair(), "consistent state must not report drift");

        state.out_of_sync.clear();
        state.held.clear();
        assert!(state.repair());
        assert_eq!(state.held, state.full_held());
        assert_eq!(state.out_of_sync, state.full_out_of_sync());
        assert!(!state.out_of_sync.is_empty());
    }

    fn synchronizer() -> Arc<GlidePubSubSynchronizer> {
        // Long interval so only the test drives reconciliation by timer
        GlidePubSubSynchronizer::new(
            None,
            true,
            Some(Duration::from_secs(3600)),
            Duration::from_secs(1),
        )
    }

    #[tokio::test]
    async fn test_timer_reconcile_repairs_drift() {
        let sync = synchronizer();
        sync.add_desired_subscriptions(HashSet::from([ch(1)]), Exact);
        sync.state.write().unwrap().out_of_sync.clear();

        // A notify-driven pass trusts the incremental state
        let _ = sync.reconcile(false).await;
        assert!(sync.state.read().unwrap().out_of_sync.is_empty());

        // A timer pass recomputes it
        let _ = sync.reconcile(true).await;
        let state = sync.state.read().unwrap();
        assert!(
            state
                .out_of_sync
                .get(&Exact)
                .is_some_and(|s| s.contains(&ch(1)))
        );
    }

    #[tokio::test]
    async fn test_failed_subscribe_is_retried() {
        // No internal client, so every subscribe fails
        let sync = synchronizer();
        sync.add_desired_subscriptions(HashSet::from([ch(1)]), Sharded);
        let _ = sync.reconcile(false).await;

        let diff = sync.state.read().unwrap().diff();
        assert!(
            diff.to_subscribe
                .get(&Sharded)
                .is_some_and(|s| s.contains(&ch(1)))
        );
    }

    #[tokio::test]
    async fn test_disconnect_clears_held() {
        let sync = synchronizer();
        sync.add_desired_subscriptions(HashSet::from([ch(1)]), Exact);
        sync.add_current_subscriptions(HashSet::from([ch(1)]), Exact, ADDRS[0].to_string());
        assert!(sync.state.read().unwrap().out_of_sync.is_empty());

        sync.remove_current_subscriptions_for_addresses(&HashSet::from([ADDRS[0].to_string()]));
        let state = sync.state.read().unwrap();
        assert!(!state.is_held(Exact, &ch(1)));
        assert!(
            state
                .out_of_sync
                .get(&Exact)
                .is_some_and(|s| s.contains(&ch(1)))
        );
    }

    #[tokio::test]
    async fn test_unsubscribe_confirmation_scope() {
        let sync = synchronizer();
        for addr in &ADDRS[..2] {
            sync.add_current_subscriptions(HashSet::from([ch(1)]), Exact, addr.to_string());
            sync.add_current_subscriptions(HashSet::from([ch(1)]), Sharded, addr.to_string());
        }

        // Exact/Pattern: one confirmation clears every address
        sync.remove_current_subscriptions(HashSet::from([ch(1)]), Exact, ADDRS[0].to_string());
        // Sharded: only the confirming address
        sync.remove_current_subscriptions(HashSet::from([ch(1)]), Sharded, ADDRS[0].to_string());

        let state = sync.state.read().unwrap();
        assert!(!state.is_held(Exact, &ch(1)));
        assert_eq!(state.held[&Sharded][&ch(1)], 1);
        assert!(state.by_address[ADDRS[1]][&Sharded].contains(&ch(1)));
        assert_eq!(state.held, state.full_held());
        assert_eq!(state.out_of_sync, state.full_out_of_sync());
    }

    #[tokio::test]
    async fn test_is_current_subscription() {
        let sync = synchronizer();
        sync.add_current_subscriptions(HashSet::from([ch(1)]), Pattern, ADDRS[0].to_string());
        assert!(sync.is_current_subscription(Pattern, &ch(1), ADDRS[0]));
        assert!(!sync.is_current_subscription(Pattern, &ch(1), ADDRS[1]));
        assert!(!sync.is_current_subscription(Exact, &ch(1), ADDRS[0]));
    }

    #[tokio::test]
    async fn test_full_repair_runs_under_continuous_notifications() {
        let interval = Duration::from_millis(50);
        let sync = GlidePubSubSynchronizer::new(None, true, Some(interval), Duration::from_secs(1));
        sync.add_desired_subscriptions(HashSet::from([ch(1)]), Exact);
        tokio::time::sleep(Duration::from_millis(20)).await;
        sync.state.write().unwrap().out_of_sync.clear();

        // Notify more often than the interval, so the timer's sleep never completes
        let repaired = || {
            sync.state
                .read()
                .unwrap()
                .out_of_sync
                .get(&Exact)
                .is_some_and(|s| s.contains(&ch(1)))
        };
        let deadline = Instant::now() + Duration::from_secs(5);
        while !repaired() && Instant::now() < deadline {
            sync.trigger_reconciliation();
            tokio::time::sleep(interval / 10).await;
        }
        assert!(
            repaired(),
            "no full repair within 5 s of continuous notifications"
        );
    }

    #[tokio::test(flavor = "multi_thread", worker_threads = 4)]
    async fn test_concurrent_mutations_keep_state_consistent() {
        let sync = synchronizer();
        let tasks = (0..4u64).map(|seed| {
            let sync = sync.clone();
            tokio::spawn(async move {
                let mut rng = StdRng::seed_from_u64(seed);
                for _ in 0..2000 {
                    let kind = [Exact, Pattern, Sharded][rng.gen_range(0..3)];
                    let addr = ADDRS[rng.gen_range(0..3)].to_string();
                    let channels = HashSet::from([ch(rng.gen_range(0..30))]);
                    match rng.gen_range(0..7) {
                        0 => sync.add_desired_subscriptions(channels, kind),
                        1 => sync.remove_desired_subscriptions(Some(channels), kind),
                        2 | 3 => sync.add_current_subscriptions(channels, kind, addr),
                        4 => sync.remove_current_subscriptions(channels, kind, addr),
                        5 => {
                            let channel = channels.into_iter().next().unwrap();
                            sync.is_current_subscription(kind, &channel, &addr);
                        }
                        _ if rng.gen_range(0..20) == 0 => {
                            sync.remove_current_subscriptions_for_addresses(&HashSet::from([addr]))
                        }
                        _ => sync.add_current_subscriptions(channels, kind, addr),
                    }
                    tokio::task::yield_now().await;
                }
            })
        });
        for task in tasks.collect::<Vec<_>>() {
            task.await.unwrap();
        }

        let state = sync.state.read().unwrap();
        assert_eq!(state.held, state.full_held());
        assert_eq!(state.out_of_sync, state.full_out_of_sync());
    }

    #[tokio::test]
    async fn test_timed_out_blocking_subscribe_keeps_desired() {
        // No internal client, so the subscribe never completes
        let sync = synchronizer();
        let mut cmd = redis::cmd("SUBSCRIBE_BLOCKING");
        cmd.arg(ch(1)).arg(100);

        let result = sync.intercept_pubsub_command(&cmd).await;
        assert!(result.is_some_and(|r| r.is_err_and(|e| e.is_timeout())));
        assert!(sync.state.read().unwrap().is_desired(Exact, &ch(1)));
    }
}
