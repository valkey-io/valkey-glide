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
const RECONCILIATION_INTERVAL: Duration = Duration::from_secs(5);

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
    /// Format: (address, kind, channels)
    pending_unsubscribes: RwLock<
        Vec<(
            String,
            PubSubSubscriptionKind,
            HashSet<PubSubChannelOrPattern>,
        )>,
    >,
}

impl GlidePubSubSynchronizer {
    pub async fn create(
        initial_subscriptions: Option<PubSubSubscriptionInfo>,
        is_cluster: bool,
    ) -> Arc<dyn PubSubSynchronizer> {
        let sync = Arc::new(Self {
            internal_client: OnceCell::new(),
            is_cluster,
            desired_subscriptions: RwLock::new(initial_subscriptions.unwrap_or_default()),
            current_subscriptions_by_address: RwLock::new(HashMap::new()),
            reconciliation_notify: Notify::new(),
            reconciliation_complete_notify: Notify::new(),
            reconciliation_task_handle: Mutex::new(None),
            pending_unsubscribes: RwLock::new(Vec::new()),
        });

        sync.start_reconciliation_task();
        sync
    }

    pub fn set_internal_client(&self, client: Weak<TokioRwLock<ClientWrapper>>) {
        let _ = self.internal_client.set(client);
    }

    // ========================================================================
    // Command Execution
    // ========================================================================

    /// Send a command through the internal client with optional routing
    async fn send_command(
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

    // ========================================================================
    // State Management
    // ========================================================================

    /// Get aggregated current subscriptions across all addresses
    fn get_aggregated_current_subscriptions(&self) -> PubSubSubscriptionInfo {
        let current_by_addr = self
            .current_subscriptions_by_address
            .read()
            .expect(LOCK_ERR);
        let mut aggregated: PubSubSubscriptionInfo = HashMap::new();

        for subs in current_by_addr.values() {
            for (kind, channels) in subs.iter() {
                aggregated
                    .entry(*kind)
                    .or_default()
                    .extend(channels.clone());
            }
        }

        aggregated
    }

    /// Check if subscriptions are synchronized
    fn check_synchronized(&self) -> bool {
        let desired = self.desired_subscriptions.read().expect(LOCK_ERR);
        let current = self.get_aggregated_current_subscriptions();

        self.subscription_kinds().all(|kind| {
            let desired_set = desired.get(&kind).cloned().unwrap_or_default();
            let current_set = current.get(&kind).cloned().unwrap_or_default();
            desired_set == current_set
        })
    }

    /// Returns iterator over applicable subscription kinds based on cluster mode
    fn subscription_kinds(&self) -> impl Iterator<Item = PubSubSubscriptionKind> {
        let kinds = if self.is_cluster {
            vec![
                PubSubSubscriptionKind::Exact,
                PubSubSubscriptionKind::Pattern,
                PubSubSubscriptionKind::Sharded,
            ]
        } else {
            vec![
                PubSubSubscriptionKind::Exact,
                PubSubSubscriptionKind::Pattern,
            ]
        };
        kinds.into_iter()
    }

    // ========================================================================
    // Reconciliation
    // ========================================================================

    /// Start the background reconciliation task
    fn start_reconciliation_task(self: &Arc<Self>) {
        let sync_weak = Arc::downgrade(self);

        let handle = tokio::spawn(async move {
            loop {
                let Some(sync) = sync_weak.upgrade() else {
                    log_warn("reconciliation_task", "Synchronizer dropped, exiting task");
                    break;
                };
                tokio::select! {
                    _ = sync.reconciliation_notify.notified() => {},
                    _ = tokio::time::sleep(RECONCILIATION_INTERVAL) => {},
                }

                if let Err(e) = sync.reconcile().await {
                    log_error(
                        "reconciliation_task",
                        format!("Reconciliation failed: {:?}", e),
                    );
                }

                sync.check_and_record_sync_state();
                sync.reconciliation_complete_notify.notify_waiters();
            }
        });

        *self.reconciliation_task_handle.lock().unwrap() = Some(handle);
    }

    /// Perform reconciliation - align current subscriptions with desired
    async fn reconcile(&self) -> RedisResult<()> {
        // First, process any pending unsubscribes from topology changes
        self.process_pending_unsubscribes().await;

        let desired = self.desired_subscriptions.read().expect(LOCK_ERR).clone();
        let current_by_addr = self
            .current_subscriptions_by_address
            .read()
            .expect(LOCK_ERR)
            .clone();

        for kind in self.subscription_kinds() {
            let desired_set = desired.get(&kind).cloned().unwrap_or_default();

            // Get aggregated current subscriptions for this kind
            let current_set: HashSet<_> = current_by_addr
                .values()
                .filter_map(|subs| subs.get(&kind))
                .flat_map(|channels| channels.iter().cloned())
                .collect();

            // Subscribe to channels in desired but not in current
            let to_subscribe: HashSet<_> = desired_set.difference(&current_set).cloned().collect();
            if !to_subscribe.is_empty() {
                self.execute_subscription_change(to_subscribe, kind, true, None)
                    .await;
            }

            // Unsubscribe from channels in current but not in desired
            let to_unsubscribe: HashSet<_> =
                current_set.difference(&desired_set).cloned().collect();
            if !to_unsubscribe.is_empty() {
                self.execute_unsubscribe_by_address(&current_by_addr, to_unsubscribe, kind)
                    .await;
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

        log_debug(
            "process_pending_unsubscribes",
            format!("Processing {} pending unsubscribe batches", pending.len()),
        );

        for (address, kind, channels) in pending {
            if channels.is_empty() {
                continue;
            }

            let routing = match Self::parse_address_to_routing(&address) {
                Ok(r) => Some(r),
                Err(e) => {
                    log_warn(
                        "process_pending_unsubscribes",
                        format!("Failed to parse address '{}': {:?}", address, e),
                    );
                    continue;
                }
            };

            log_debug(
                "process_pending_unsubscribes",
                format!(
                    "Sending {:?} unsubscribe to {} for {} channels: {:?}",
                    kind,
                    address,
                    channels.len(),
                    channels
                        .iter()
                        .map(|c| String::from_utf8_lossy(c).to_string())
                        .collect::<Vec<_>>()
                ),
            );

            if kind == PubSubSubscriptionKind::Sharded {
                // For sharded channels, group by slot to avoid CrossSlot errors
                self.execute_sharded_unsubscribe_by_slot(channels, routing)
                    .await;
            } else {
                self.execute_subscription_change(channels, kind, false, routing)
                    .await;
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

        // Set fenced flag for SUNSUBSCRIBE to handle slot migration edge cases
        if kind == PubSubSubscriptionKind::Sharded && !is_subscribe {
            cmd.set_fenced(true);
        }

        let action = if is_subscribe {
            "subscribe"
        } else {
            "unsubscribe"
        };
        match self.send_command(&mut cmd, routing).await {
            Ok(_) => {
                log_debug(
                    "execute_subscription_change",
                    format!(
                        "Sent {} for {:?} channels: {:?}",
                        action,
                        kind,
                        channels
                            .iter()
                            .map(|c| String::from_utf8_lossy(c).to_string())
                            .collect::<Vec<_>>()
                    ),
                );
            }
            Err(e) => {
                log_error(
                    "execute_subscription_change",
                    format!("Failed to {} {:?} channels: {:?}", action, kind, e),
                );
            }
        }
    }

    /// Execute unsubscribe for each address based on previously subscribed channels.
    /// It's needed specifically for unsubscribe operations since unlike subscribe, which uses the
    /// current topology map in glide-core, this uses the addresses recorded at the time of subscription.
    async fn execute_unsubscribe_by_address(
        &self,
        current_by_addr: &HashMap<String, PubSubSubscriptionInfo>,
        channels_to_unsubscribe: HashSet<PubSubChannelOrPattern>,
        kind: PubSubSubscriptionKind,
    ) {
        if channels_to_unsubscribe.is_empty() {
            return;
        }

        // Group channels by the address where they're currently subscribed
        let channels_by_address =
            Self::group_channels_by_address(current_by_addr, channels_to_unsubscribe, kind);

        for (addr, channels) in channels_by_address {
            let routing = match Self::parse_address_to_routing(&addr) {
                Ok(r) => Some(r),
                Err(e) => {
                    log_warn(
                        "execute_unsubscribe_by_address",
                        format!("Failed to parse address '{}': {:?}", addr, e),
                    );
                    continue;
                }
            };

            if kind == PubSubSubscriptionKind::Sharded {
                // For sharded channels, further group by slot to avoid CrossSlot errors
                self.execute_sharded_unsubscribe_by_slot(channels, routing)
                    .await;
            } else {
                log_debug(
                    "unsubscribe",
                    format!(
                        "Sending {:?} unsubscribe to {} for {} channels",
                        kind,
                        addr,
                        channels.len()
                    ),
                );
                self.execute_subscription_change(channels, kind, false, routing)
                    .await;
            }
        }
    }

    /// Group channels by the address where they're subscribed
    fn group_channels_by_address(
        current_by_addr: &HashMap<String, PubSubSubscriptionInfo>,
        channels: HashSet<PubSubChannelOrPattern>,
        kind: PubSubSubscriptionKind,
    ) -> HashMap<String, HashSet<PubSubChannelOrPattern>> {
        channels.into_iter().fold(HashMap::new(), |mut acc, chan| {
            if let Some((addr, _)) = current_by_addr
                .iter()
                .find(|(_, subs)| subs.get(&kind).map_or(false, |c| c.contains(&chan)))
            {
                acc.entry(addr.clone()).or_default().insert(chan);
            }
            acc
        })
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

        for (slot, slot_channels) in channels_by_slot {
            log_debug(
                "unsubscribe",
                format!(
                    "Sending SUNSUBSCRIBE for slot {} with {} channels",
                    slot,
                    slot_channels.len()
                ),
            );
            self.execute_subscription_change(
                slot_channels,
                PubSubSubscriptionKind::Sharded,
                false,
                routing.clone(),
            )
            .await;
        }
    }

    // ========================================================================
    // Metrics & Sync State
    // ========================================================================

    /// Check sync state and update metrics accordingly
    fn check_and_record_sync_state(&self) {
        if self.check_synchronized() {
            let _ = GlideOpenTelemetry::update_subscription_last_sync_timestamp();
            log_debug("sync_state", "Subscriptions are synchronized");
            return;
        }
        let _ = GlideOpenTelemetry::record_subscription_out_of_sync();
        let (desired, actual) = self.get_subscription_state();
        log_warn(
            "sync_state",
            format!(
                "Subscriptions out of sync - desired: {}, actual: {}",
                Self::format_subscription_info(&desired),
                Self::format_subscription_info(&actual)
            ),
        );
    }

    fn format_subscription_info(info: &PubSubSubscriptionInfo) -> String {
        let formatted: Vec<_> = info
            .iter()
            .map(|(kind, channels)| {
                let kind_str = match kind {
                    PubSubSubscriptionKind::Exact => "Exact",
                    PubSubSubscriptionKind::Pattern => "Pattern",
                    PubSubSubscriptionKind::Sharded => "Sharded",
                };
                let channels_str: Vec<_> = channels
                    .iter()
                    .map(|c| String::from_utf8_lossy(c).to_string())
                    .collect();
                format!("{}: {:?}", kind_str, channels_str)
            })
            .collect();
        format!("{{{}}}", formatted.join(", "))
    }

    // ========================================================================
    // Blocking Wait (Unified)
    // ========================================================================

    /// Wait for subscription state to match expected condition
    ///
    /// # Arguments
    /// * `channels` - Channels to check. If None, checks if the kind's subscriptions are empty.
    /// * `kind` - The subscription kind to check
    /// * `timeout_ms` - Timeout in milliseconds. 0 means no timeout.
    /// * `wait_for_presence` - If true, waits until channels ARE subscribed.
    ///                         If false, waits until channels are NOT subscribed.
    async fn wait_for_subscription_state(
        &self,
        channels: Option<&HashSet<PubSubChannelOrPattern>>,
        kind: PubSubSubscriptionKind,
        timeout_ms: u64,
        wait_for_presence: bool,
    ) -> RedisResult<()> {
        let deadline = if timeout_ms > 0 {
            Some(Instant::now() + Duration::from_millis(timeout_ms))
        } else {
            None
        };

        loop {
            // This ensures we don't miss notifications that happen during the check
            let notified = self.reconciliation_complete_notify.notified();

            let current = self.get_aggregated_current_subscriptions();
            let current_set = current.get(&kind).cloned().unwrap_or_default();

            let condition_met = match (channels, wait_for_presence) {
                (Some(chs), true) => chs.iter().all(|ch| current_set.contains(ch)),
                (Some(chs), false) => chs.iter().all(|ch| !current_set.contains(ch)),
                (None, true) => false, // Can't wait for "all" to be present without specifying channels
                (None, false) => current_set.is_empty(),
            };

            if condition_met {
                return Ok(());
            }

            // Wait for reconciliation notification or timeout
            if let Some(deadline) = deadline {
                let remaining = deadline.saturating_duration_since(Instant::now());
                if remaining.is_zero() {
                    return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                }

                tokio::select! {
                    _ = notified => {
                    }
                    _ = tokio::time::sleep(remaining) => {
                        return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                    }
                }
            } else {
                notified.await;
            }
        }
    }

    // ========================================================================
    // Command Handlers (Unified)
    // ========================================================================

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

        Ok(Value::Okay)
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

        let channels_set: Option<HashSet<_>> = if channels.is_empty() {
            None
        } else {
            Some(channels.into_iter().collect())
        };

        if is_subscribe {
            let chs = channels_set.clone().unwrap();
            self.add_desired_subscriptions(chs.clone(), kind);
            self.wait_for_subscription_state(Some(&chs), kind, timeout_ms, true)
                .await?;
        } else {
            self.remove_desired_subscriptions(channels_set.clone(), kind);
            self.wait_for_subscription_state(channels_set.as_ref(), kind, timeout_ms, false)
                .await?;
        }

        Ok(Value::Okay)
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
        let redis_map: Vec<_> = map
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
        Value::Map(redis_map)
    }

    /// Get current subscriptions organized by address.
    /// Used for testing to verify subscriptions moved to different addresses after slot migration.
    pub fn get_current_subscriptions_by_address(&self) -> HashMap<String, PubSubSubscriptionInfo> {
        self.current_subscriptions_by_address
            .read()
            .expect(LOCK_ERR)
            .clone()
    }
}

impl Drop for GlidePubSubSynchronizer {
    fn drop(&mut self) {
        if let Some(handle) = self.reconciliation_task_handle.lock().unwrap().take() {
            handle.abort();
        }
    }
}

// ============================================================================
// PubSubSynchronizer Trait Implementation
// ============================================================================

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
        log_debug(
            "add_desired_subscriptions",
            format!(
                "Adding {:?} to desired: {:?}",
                subscription_type,
                channels
                    .iter()
                    .map(|c| String::from_utf8_lossy(c).to_string())
                    .collect::<Vec<_>>()
            ),
        );

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
        log_debug(
            "remove_desired_subscriptions",
            format!(
                "Removing {:?} from desired: {:?}",
                subscription_type,
                channels
                    .as_ref()
                    .map(|chs| chs
                        .iter()
                        .map(|c| String::from_utf8_lossy(c).to_string())
                        .collect::<Vec<_>>())
                    .unwrap_or_else(|| vec!["<all>".to_string()])
            ),
        );

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
        log_debug(
            "add_current_subscriptions",
            format!(
                "Adding {:?} to address '{}': {:?}",
                subscription_type,
                address,
                channels
                    .iter()
                    .map(|c| String::from_utf8_lossy(c).to_string())
                    .collect::<Vec<_>>()
            ),
        );

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
        log_debug(
            "remove_current_subscriptions",
            format!(
                "Removing {:?} channels {:?} (notification from '{}')",
                subscription_type,
                channels
                    .iter()
                    .map(|c| String::from_utf8_lossy(c).to_string())
                    .collect::<Vec<_>>(),
                address
            ),
        );

        let mut current_by_addr = self
            .current_subscriptions_by_address
            .write()
            .expect(LOCK_ERR);

        // An unsubscribe push notification is authoritative - if the server says
        // we're unsubscribed, remove from ALL addresses where this channel exists.
        for (addr, addr_subs) in current_by_addr.iter_mut() {
            if let Some(existing) = addr_subs.get_mut(&subscription_type) {
                for channel in &channels {
                    if existing.remove(channel) {
                        log_debug(
                            "remove_current_subscriptions",
                            format!(
                                "Removed '{}' from address '{}'",
                                String::from_utf8_lossy(channel),
                                addr
                            ),
                        );
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
            "remove_current_subscriptions_for_addresses",
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
            if let Some(removed_subs) = current_by_addr.remove(address) {
                let channels_count: usize = removed_subs.values().map(|s| s.len()).sum();
                log_debug(
                    "remove_current_subscriptions_for_addresses",
                    format!(
                        "Removed {} subscription(s) for address '{}': {:?}",
                        channels_count,
                        address,
                        removed_subs
                            .iter()
                            .flat_map(|(_, channels)| channels.iter())
                            .map(|c| String::from_utf8_lossy(c).to_string())
                            .collect::<Vec<_>>()
                    ),
                );
            }
        }
    }

    fn handle_topology_refresh(&self, new_slot_map: &SlotMap) {
        log_debug("handle_topology_refresh", format!("in handle topology"));
        let new_addresses: HashSet<String> = new_slot_map
            .all_node_addresses()
            .iter()
            .map(|arc| arc.to_string())
            .collect();

        let mut modified = false;
        let mut unsubscribes_to_queue: Vec<(
            String,
            PubSubSubscriptionKind,
            HashSet<PubSubChannelOrPattern>,
        )> = Vec::new();

        {
            let mut current_by_addr = self
                .current_subscriptions_by_address
                .write()
                .expect(LOCK_ERR);

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
                            log_debug(
                                "topology_refresh",
                                format!(
                                    "Queueing unsubscribe for {} {:?} channels from removed address {}",
                                    channels.len(),
                                    kind,
                                    addr
                                ),
                            );
                            unsubscribes_to_queue.push((addr.clone(), kind, channels));
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
                        log_debug(
                            "topology_refresh",
                            format!(
                                "Queueing unsubscribe for {} migrated {:?} channels from {}",
                                migrated_channels.len(),
                                kind,
                                addr
                            ),
                        );
                        unsubscribes_to_queue.push((addr.clone(), *kind, migrated_channels));
                    }
                }

                // Clean up empty entries
                addr_subs.retain(|_, channels| !channels.is_empty());
            }

            // Clean up addresses with no subscriptions
            current_by_addr.retain(|_, addr_subs| !addr_subs.is_empty());
        }

        // Add queued unsubscribes to pending list
        if !unsubscribes_to_queue.is_empty() {
            let mut pending = self.pending_unsubscribes.write().expect(LOCK_ERR);
            pending.extend(unsubscribes_to_queue);
        }

        if modified {
            self.trigger_reconciliation();
        }
    }

    fn get_subscription_state(&self) -> (PubSubSubscriptionInfo, PubSubSubscriptionInfo) {
        let desired = self.desired_subscriptions.read().expect(LOCK_ERR).clone();
        let current = self.get_aggregated_current_subscriptions();

        let mut desired_result = PubSubSubscriptionInfo::new();
        let mut current_result = PubSubSubscriptionInfo::new();

        for kind in self.subscription_kinds() {
            desired_result.insert(kind, desired.get(&kind).cloned().unwrap_or_default());
            current_result.insert(kind, current.get(&kind).cloned().unwrap_or_default());
        }

        (desired_result, current_result)
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
                Some(self.handle_lazy_subscription(cmd, PubSubSubscriptionKind::Exact, true))
            }
            "PSUBSCRIBE" => {
                Some(self.handle_lazy_subscription(cmd, PubSubSubscriptionKind::Pattern, true))
            }
            "SSUBSCRIBE" => {
                Some(self.handle_lazy_subscription(cmd, PubSubSubscriptionKind::Sharded, true))
            }

            // Non-blocking unsubscribe commands
            "UNSUBSCRIBE" => {
                Some(self.handle_lazy_subscription(cmd, PubSubSubscriptionKind::Exact, false))
            }
            "PUNSUBSCRIBE" => {
                Some(self.handle_lazy_subscription(cmd, PubSubSubscriptionKind::Pattern, false))
            }
            "SUNSUBSCRIBE" => {
                Some(self.handle_lazy_subscription(cmd, PubSubSubscriptionKind::Sharded, false))
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
            "GET_SUBSCRIPTIONS" => Some(Ok(self.get_subscriptions_as_value())),

            // Not a pubsub command we handle
            _ => None,
        }
    }

    async fn wait_for_initial_sync(&self, timeout_ms: u64) -> RedisResult<()> {
        self.trigger_reconciliation();
        let desired = self.desired_subscriptions.read().expect(LOCK_ERR).clone();

        // If no desired subscriptions, nothing to wait for
        let has_any_desired = self
            .subscription_kinds()
            .any(|kind| desired.get(&kind).map(|s| !s.is_empty()).unwrap_or(false));

        if !has_any_desired {
            log_debug(
                "wait_for_initial_sync",
                "No initial subscriptions to wait for",
            );
            return Ok(());
        }

        log_debug(
            "wait_for_initial_sync",
            format!(
                "Waiting for initial subscriptions to sync (timeout: {}ms)",
                timeout_ms
            ),
        );

        // Wait for each subscription type that has desired subscriptions
        for kind in self.subscription_kinds() {
            let desired_set = desired.get(&kind).cloned().unwrap_or_default();
            if !desired_set.is_empty() {
                log_debug(
                    "wait_for_initial_sync",
                    format!(
                        "Waiting for {:?} subscriptions: {:?}",
                        kind,
                        desired_set
                            .iter()
                            .map(|c| String::from_utf8_lossy(c).to_string())
                            .collect::<Vec<_>>()
                    ),
                );

                self.wait_for_subscription_state(Some(&desired_set), kind, timeout_ms, true)
                    .await?;
            }
        }

        log_debug(
            "wait_for_initial_sync",
            "All initial subscriptions synchronized",
        );
        Ok(())
    }
}
