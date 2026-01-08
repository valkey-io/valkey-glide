// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::client::ClientWrapper;
use async_trait::async_trait;
use logger_core::{log_debug, log_warn};
use once_cell::sync::Lazy;
use once_cell::sync::OnceCell;
use redis::cluster_routing::Routable;
use redis::{Cmd, ErrorKind, PushInfo, PushKind, RedisError, RedisResult, Value};
use redis::{
    PubSubChannelOrPattern, PubSubSubscriptionInfo, PubSubSubscriptionKind, PubSubSynchronizer,
    SlotMap,
};
use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, RwLock, Weak};
use std::time::Duration;
use telemetrylib::GlideOpenTelemetry;
use tokio::sync::{Notify, RwLock as TokioRwLock, mpsc};
use tokio::time::sleep;

/// Default reconciliation interval for mock (3 seconds)
pub const DEFAULT_MOCK_RECONCILIATION_INTERVAL: Duration = Duration::from_secs(3);

/// Global singleton mock PubSub broker
static MOCK_BROKER: Lazy<Arc<MockPubSubBroker>> = Lazy::new(|| Arc::new(MockPubSubBroker::new()));

// Client ID generation
static MOCK_CLIENT_COUNTER: AtomicU64 = AtomicU64::new(0);

/// Get the global mock broker instance
pub fn get_mock_broker() -> Arc<MockPubSubBroker> {
    Arc::clone(&MOCK_BROKER)
}

const LOCK_ERR: &str = "Lock poisoned";

/// Mock implementation of PubSub synchronizer
pub struct MockPubSubSynchronizer {
    client_id: String,
    internal_client: OnceCell<Weak<TokioRwLock<ClientWrapper>>>,
    is_cluster: bool,

    desired_subscriptions: RwLock<PubSubSubscriptionInfo>,
    actual_subscriptions: RwLock<PubSubSubscriptionInfo>,
    can_subscribe: RwLock<bool>,

    broker: Arc<MockPubSubBroker>,
    reconciliation_notify: Arc<Notify>,
    reconciliation_complete_notify: Arc<Notify>,
    reconciliation_task_handle: Arc<Mutex<Option<tokio::task::JoinHandle<()>>>>,
    reconciliation_interval: Duration,
}

impl MockPubSubSynchronizer {
    fn new_internal(
        client_id: String,
        broker: Arc<MockPubSubBroker>,
        is_cluster: bool,
        reconciliation_interval: Duration,
    ) -> Arc<Self> {
        Arc::new(Self {
            client_id,
            internal_client: OnceCell::new(),
            is_cluster,
            desired_subscriptions: RwLock::new(HashMap::new()),
            actual_subscriptions: RwLock::new(HashMap::new()),
            can_subscribe: RwLock::new(true),
            broker,
            reconciliation_notify: Arc::new(Notify::new()),
            reconciliation_complete_notify: Arc::new(Notify::new()),
            reconciliation_task_handle: Arc::new(Mutex::new(None)),
            reconciliation_interval,
        })
    }

    pub async fn create(
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
        initial_subscriptions: Option<redis::PubSubSubscriptionInfo>,
        is_cluster: bool,
        reconciliation_interval: Option<Duration>,
    ) -> Arc<dyn PubSubSynchronizer> {
        let client_id = format!(
            "mock_client_{}",
            MOCK_CLIENT_COUNTER.fetch_add(1, Ordering::SeqCst)
        );

        // Apply default for reconciliation interval
        let interval = reconciliation_interval.unwrap_or(DEFAULT_MOCK_RECONCILIATION_INTERVAL);

        let broker = get_mock_broker();
        let synchronizer =
            Self::new_internal(client_id.clone(), Arc::clone(&broker), is_cluster, interval);

        // Apply initial subscriptions before starting reconciliation task
        // For config-based subscriptions, immediately sync desired and actual
        if let Some(subs) = initial_subscriptions {
            let mut desired = synchronizer.desired_subscriptions.write().expect(LOCK_ERR);
            let mut actual = synchronizer.actual_subscriptions.write().expect(LOCK_ERR);

            if let Some(channels) = subs.get(&PubSubSubscriptionKind::Exact) {
                desired
                    .entry(PubSubSubscriptionKind::Exact)
                    .or_default()
                    .extend(channels.clone());
                actual
                    .entry(PubSubSubscriptionKind::Exact)
                    .or_default()
                    .extend(channels.clone());
            }
            if let Some(patterns) = subs.get(&PubSubSubscriptionKind::Pattern) {
                desired
                    .entry(PubSubSubscriptionKind::Pattern)
                    .or_default()
                    .extend(patterns.clone());
                actual
                    .entry(PubSubSubscriptionKind::Pattern)
                    .or_default()
                    .extend(patterns.clone());
            }
            if let Some(sharded) = subs.get(&PubSubSubscriptionKind::Sharded) {
                desired
                    .entry(PubSubSubscriptionKind::Sharded)
                    .or_default()
                    .extend(sharded.clone());
                actual
                    .entry(PubSubSubscriptionKind::Sharded)
                    .or_default()
                    .extend(sharded.clone());
            }
        }

        synchronizer.start_reconciliation_task();

        if let Some(sender) = push_sender {
            broker
                .register_client(client_id, sender, synchronizer.clone())
                .await;
        }

        // Trigger initial reconciliation
        synchronizer.trigger_reconciliation();

        synchronizer
    }

    pub(crate) fn set_can_subscribe(&self, can_subscribe: bool) {
        *self.can_subscribe.write().expect(LOCK_ERR) = can_subscribe;
    }

    fn get_can_subscribe(&self) -> bool {
        *self.can_subscribe.read().expect(LOCK_ERR)
    }

    pub(crate) fn is_synchronized(&self) -> bool {
        let desired = self.desired_subscriptions.read().expect(LOCK_ERR);
        let actual = self.actual_subscriptions.read().expect(LOCK_ERR);
        *desired == *actual
    }

    fn start_reconciliation_task(self: &Arc<Self>) {
        let sync_weak = Arc::downgrade(self);
        let notify = Arc::clone(&self.reconciliation_notify);
        let complete_notify = Arc::clone(&self.reconciliation_complete_notify);
        let broker = Arc::clone(&self.broker);
        let interval = self.reconciliation_interval;

        let handle = tokio::spawn(async move {
            loop {
                tokio::select! {
                    _ = notify.notified() => {},
                    _ = tokio::time::sleep(interval) => {},
                }

                let Some(sync) = sync_weak.upgrade() else {
                    log_debug("reconciliation_task", "Synchronizer dropped, exiting task");
                    break;
                };

                let delay = broker.get_max_application_delay_ms().await;
                if delay > 0 {
                    tokio::time::sleep(Duration::from_millis(delay)).await;
                }

                if let Err(e) = sync.reconcile_internal() {
                    log_warn(
                        "reconciliation_task",
                        format!("Reconciliation failed for client {}: {}", sync.client_id, e),
                    );
                }
                sync.check_and_record_sync_state();
                complete_notify.notify_waiters();
            }
        });

        *self.reconciliation_task_handle.lock().unwrap() = Some(handle);
    }

    pub(crate) fn reconcile_internal(&self) -> Result<(), String> {
        let can_subscribe = self.get_can_subscribe();

        if !can_subscribe {
            return Err("Reconciliation blocked: no subscription permission".to_string());
        }

        let desired = self.desired_subscriptions.read().expect(LOCK_ERR).clone();
        let mut actual = self.actual_subscriptions.write().expect(LOCK_ERR);

        for (kind, desired_channels) in &desired {
            let actual_channels = actual.entry(*kind).or_default();
            for channel in desired_channels {
                actual_channels.insert(channel.clone());
            }
            actual_channels.retain(|ch| desired_channels.contains(ch));
        }

        actual.retain(|kind, _| desired.contains_key(kind));

        Ok(())
    }

    pub fn set_internal_client(&self, client: Weak<TokioRwLock<ClientWrapper>>) {
        let _ = self.internal_client.set(client);
    }

    /// Check sync state and update metrics accordingly
    pub(crate) fn check_and_record_sync_state(&self) {
        let is_synced = self.is_synchronized();
        if is_synced {
            let _ = GlideOpenTelemetry::update_subscription_last_sync_timestamp();
            log_debug(
                "mock_pubsub",
                format!("Client {} subscriptions in sync", self.client_id),
            );
        } else {
            let _ = GlideOpenTelemetry::record_subscription_out_of_sync();
            let (desired, actual) = self.get_subscription_state();
            log_debug(
                "mock_pubsub",
                format!(
                    "Client {} subscriptions out of sync - desired: {:?}, actual: {:?}",
                    self.client_id, desired, actual
                ),
            );
        }
    }
}

impl Drop for MockPubSubSynchronizer {
    fn drop(&mut self) {
        let client_id = self.client_id.clone();
        let broker = Arc::clone(&self.broker);

        if let Some(handle) = self.reconciliation_task_handle.lock().unwrap().take() {
            handle.abort();
        }

        tokio::spawn(async move {
            broker.unregister_client(&client_id).await;
        });
    }
}

#[async_trait]
impl PubSubSynchronizer for MockPubSubSynchronizer {
    fn as_any(&self) -> &dyn std::any::Any {
        self
    }

    fn add_desired_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
    ) {
        let mut desired = self.desired_subscriptions.write().expect(LOCK_ERR);
        desired
            .entry(subscription_type)
            .or_default()
            .extend(channels);
        drop(desired);

        self.trigger_reconciliation();
    }

    fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<PubSubChannelOrPattern>>,
        subscription_type: PubSubSubscriptionKind,
    ) {
        let mut desired = self.desired_subscriptions.write().expect(LOCK_ERR);
        if let Some(channels_to_remove) = channels {
            if let Some(existing) = desired.get_mut(&subscription_type) {
                for channel in channels_to_remove {
                    existing.remove(&channel);
                }
            }
        } else {
            desired.remove(&subscription_type);
        }
        drop(desired);

        self.trigger_reconciliation();
    }

    fn add_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        _address: String,
    ) {
        let mut current = self.actual_subscriptions.write().expect(LOCK_ERR);
        current
            .entry(subscription_type)
            .or_default()
            .extend(channels);
    }

    fn remove_current_subscriptions(
        &self,
        channels: HashSet<PubSubChannelOrPattern>,
        subscription_type: PubSubSubscriptionKind,
        _address: String,
    ) {
        let mut current = self.actual_subscriptions.write().expect(LOCK_ERR);
        if let Some(existing) = current.get_mut(&subscription_type) {
            for channel in channels {
                existing.remove(&channel);
            }
        }
    }

    fn handle_topology_refresh(&self, _new_slot_map: &SlotMap) {
        // No-op, mock doesn't track topology
    }

    fn get_subscription_state(&self) -> (PubSubSubscriptionInfo, PubSubSubscriptionInfo) {
        let desired_lock = self.desired_subscriptions.read().expect(LOCK_ERR);
        let actual_lock = self.actual_subscriptions.read().expect(LOCK_ERR);

        let mut desired = PubSubSubscriptionInfo::new();
        let mut actual = PubSubSubscriptionInfo::new();

        desired.insert(
            PubSubSubscriptionKind::Exact,
            desired_lock
                .get(&PubSubSubscriptionKind::Exact)
                .cloned()
                .unwrap_or_default(),
        );
        desired.insert(
            PubSubSubscriptionKind::Pattern,
            desired_lock
                .get(&PubSubSubscriptionKind::Pattern)
                .cloned()
                .unwrap_or_default(),
        );

        actual.insert(
            PubSubSubscriptionKind::Exact,
            actual_lock
                .get(&PubSubSubscriptionKind::Exact)
                .cloned()
                .unwrap_or_default(),
        );
        actual.insert(
            PubSubSubscriptionKind::Pattern,
            actual_lock
                .get(&PubSubSubscriptionKind::Pattern)
                .cloned()
                .unwrap_or_default(),
        );

        if self.is_cluster {
            desired.insert(
                PubSubSubscriptionKind::Sharded,
                desired_lock
                    .get(&PubSubSubscriptionKind::Sharded)
                    .cloned()
                    .unwrap_or_default(),
            );
            actual.insert(
                PubSubSubscriptionKind::Sharded,
                actual_lock
                    .get(&PubSubSubscriptionKind::Sharded)
                    .cloned()
                    .unwrap_or_default(),
            );
        }

        (desired, actual)
    }

    async fn intercept_pubsub_command(&self, cmd: &Cmd) -> Option<RedisResult<Value>> {
        if MockPubSubBroker::is_pubsub_command(cmd) {
            Some(
                self.broker
                    .handle_pubsub_command(&self.client_id, cmd)
                    .await,
            )
        } else {
            None
        }
    }

    fn remove_current_subscriptions_for_addresses(&self, _addresses: &HashSet<String>) {
        // Mock doesn't track by address, no-op
    }

    fn trigger_reconciliation(&self) {
        self.reconciliation_notify.notify_one();
    }
}

/// Client-specific data stored in the broker
pub(crate) struct ClientData {
    pub(crate) synchronizer: Arc<MockPubSubSynchronizer>,
    pub(crate) push_sender: mpsc::UnboundedSender<PushInfo>,
    pub(crate) username: Option<String>,
}

/// Mock PubSub broker that simulates server-side behavior
pub struct MockPubSubBroker {
    pub(crate) clients: Arc<TokioRwLock<HashMap<String, ClientData>>>,
    max_application_delay_ms: Arc<TokioRwLock<u64>>,
    username_permissions: Arc<TokioRwLock<HashMap<String, bool>>>,
}

impl MockPubSubBroker {
    fn new() -> Self {
        let broker = Self {
            clients: Arc::new(TokioRwLock::new(HashMap::new())),
            max_application_delay_ms: Arc::new(TokioRwLock::new(50)),
            username_permissions: Arc::new(TokioRwLock::new(HashMap::new())),
        };
        broker.start_reconciliation_loop();
        broker
    }

    async fn get_max_application_delay_ms(&self) -> u64 {
        *self.max_application_delay_ms.read().await
    }

    fn start_reconciliation_loop(&self) {
        let clients_arc = Arc::clone(&self.clients);

        tokio::spawn(async move {
            loop {
                sleep(Duration::from_millis(5000)).await;

                let client_ids: Vec<String> = {
                    let clients = clients_arc.read().await;
                    clients.keys().cloned().collect()
                };

                for client_id in client_ids {
                    Self::reconcile_client_static(&clients_arc, &client_id).await;
                }
            }
        });
    }

    async fn reconcile_client_static(
        clients_arc: &Arc<TokioRwLock<HashMap<String, ClientData>>>,
        client_id: &str,
    ) {
        let synchronizer = {
            let clients = clients_arc.read().await;
            clients.get(client_id).map(|c| Arc::clone(&c.synchronizer))
        };

        if let Some(sync) = synchronizer {
            let _ = sync.reconcile_internal();
            sync.check_and_record_sync_state();
        }
    }

    /// Register a client with the broker
    pub async fn register_client(
        &self,
        client_id: String,
        push_sender: mpsc::UnboundedSender<PushInfo>,
        synchronizer: Arc<MockPubSubSynchronizer>,
    ) {
        let mut clients = self.clients.write().await;
        clients.insert(
            client_id.clone(),
            ClientData {
                synchronizer,
                push_sender,
                username: None,
            },
        );
    }

    pub fn is_pubsub_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();
        let command_str = std::str::from_utf8(&command_name).unwrap_or("");

        let is_regular_pubsub = matches!(
            command_str,
            // Non-blocking (lazy) - base names
            "SUBSCRIBE"
                | "UNSUBSCRIBE"
                | "PSUBSCRIBE"
                | "PUNSUBSCRIBE"
                | "SSUBSCRIBE"
                | "SUNSUBSCRIBE"
                // Blocking - with _BLOCKING suffix
                | "SUBSCRIBE_BLOCKING"
                | "UNSUBSCRIBE_BLOCKING"
                | "PSUBSCRIBE_BLOCKING"
                | "PUNSUBSCRIBE_BLOCKING"
                | "SSUBSCRIBE_BLOCKING"
                | "SUNSUBSCRIBE_BLOCKING"
                | "PUBLISH"
                | "SPUBLISH"
                | "GET_SUBSCRIPTIONS"
                // PUBSUB info commands
                | "PUBSUB CHANNELS"
                | "PUBSUB NUMPAT"
                | "PUBSUB NUMSUB"
                | "PUBSUB SHARDCHANNELS"
                | "PUBSUB SHARDNUMSUB"
        );

        is_regular_pubsub
            || Self::is_mock_auth_command(cmd)
            || Self::is_acl_pubsub_permission_command(cmd)
    }

    pub async fn handle_pubsub_command(&self, client_id: &str, cmd: &Cmd) -> RedisResult<Value> {
        let command_name = cmd.command().unwrap_or_default();
        let command_str = std::str::from_utf8(&command_name).unwrap_or("");

        let sync = {
            let clients = self.clients.read().await;
            clients.get(client_id).map(|c| Arc::clone(&c.synchronizer))
        };

        let result = match command_str {
            "PUBLISH" => self.handle_publish(cmd, false).await,
            "SPUBLISH" => self.handle_publish(cmd, true).await,

            // Non-blocking (lazy) subscribe - base names
            "SUBSCRIBE" => {
                self.handle_lazy_subscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Exact)
            }
            "PSUBSCRIBE" => {
                self.handle_lazy_subscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Pattern)
            }
            "SSUBSCRIBE" => {
                self.handle_lazy_subscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Sharded)
            }

            // Non-blocking (lazy) unsubscribe - base names
            "UNSUBSCRIBE" => {
                self.handle_lazy_unsubscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Exact)
            }
            "PUNSUBSCRIBE" => {
                self.handle_lazy_unsubscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Pattern)
            }
            "SUNSUBSCRIBE" => {
                self.handle_lazy_unsubscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Sharded)
            }

            // Blocking subscribe - with _BLOCKING suffix
            "SUBSCRIBE_BLOCKING" => {
                self.handle_blocking_subscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Exact)
                    .await
            }
            "PSUBSCRIBE_BLOCKING" => {
                self.handle_blocking_subscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Pattern)
                    .await
            }
            "SSUBSCRIBE_BLOCKING" => {
                self.handle_blocking_subscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Sharded)
                    .await
            }

            // Blocking unsubscribe - with _BLOCKING suffix
            "UNSUBSCRIBE_BLOCKING" => {
                self.handle_blocking_unsubscribe(sync.as_ref(), cmd, PubSubSubscriptionKind::Exact)
                    .await
            }
            "PUNSUBSCRIBE_BLOCKING" => {
                self.handle_blocking_unsubscribe(
                    sync.as_ref(),
                    cmd,
                    PubSubSubscriptionKind::Pattern,
                )
                .await
            }
            "SUNSUBSCRIBE_BLOCKING" => {
                self.handle_blocking_unsubscribe(
                    sync.as_ref(),
                    cmd,
                    PubSubSubscriptionKind::Sharded,
                )
                .await
            }

            "GET_SUBSCRIPTIONS" => Ok(self.get_subscriptions_as_value(sync.as_ref())),
            "PUBSUB CHANNELS" => self.handle_pubsub_channels(cmd).await,
            "PUBSUB NUMPAT" => self.handle_pubsub_numpat().await,
            "PUBSUB NUMSUB" => self.handle_pubsub_numsub(cmd).await,
            "PUBSUB SHARDCHANNELS" => self.handle_pubsub_shardchannels(cmd).await,
            "PUBSUB SHARDNUMSUB" => self.handle_pubsub_shardnumsub(cmd).await,

            _ => Err(RedisError::from((
                ErrorKind::ClientError,
                "Unknown PubSub command",
            ))),
        };

        if result.is_ok() {
            return result;
        }

        if Self::is_mock_auth_command(cmd) {
            if let Some(username_bytes) = cmd.arg_idx(1) {
                let username = String::from_utf8_lossy(username_bytes).to_string();

                let can_subscribe = {
                    let perms = self.username_permissions.read().await;
                    perms.get(&username).copied().unwrap_or(true)
                };

                if let Some(sync) = sync {
                    sync.set_can_subscribe(can_subscribe);
                }

                let mut clients = self.clients.write().await;
                if let Some(client) = clients.get_mut(client_id) {
                    client.username = Some(username.clone());
                }
            }
            return Ok(Value::Okay);
        }

        if MockPubSubBroker::is_acl_pubsub_permission_command(cmd) {
            self.handle_acl_pubsub_permission(cmd).await;
            return Ok(Value::Okay);
        }

        result
    }

    /// Handle PUBSUB CHANNELS
    /// Returns all active exact channels, optionally filtered by pattern
    async fn handle_pubsub_channels(&self, cmd: &Cmd) -> RedisResult<Value> {
        // Pattern is at arg_idx(2) - after "PUBSUB" and "CHANNELS"
        let pattern = cmd
            .arg_idx(2)
            .map(|p| String::from_utf8_lossy(p).to_string());

        let clients = self.clients.read().await;
        let mut channels: HashSet<Vec<u8>> = HashSet::new();

        for (_, client_data) in clients.iter() {
            let actual = client_data
                .synchronizer
                .actual_subscriptions
                .read()
                .expect(LOCK_ERR);
            if let Some(exact_channels) = actual.get(&PubSubSubscriptionKind::Exact) {
                for channel in exact_channels {
                    if let Some(ref pat) = pattern {
                        let channel_str = String::from_utf8_lossy(channel);
                        if glob_match(pat, &channel_str) {
                            channels.insert(channel.clone());
                        }
                    } else {
                        channels.insert(channel.clone());
                    }
                }
            }
        }

        let result: Vec<Value> = channels.into_iter().map(Value::BulkString).collect();
        Ok(Value::Array(result))
    }

    /// Handle PUBSUB NUMPAT
    /// Returns the number of unique patterns that are subscribed to
    async fn handle_pubsub_numpat(&self) -> RedisResult<Value> {
        let clients = self.clients.read().await;
        let mut patterns: HashSet<Vec<u8>> = HashSet::new();

        for (_, client_data) in clients.iter() {
            let actual = client_data
                .synchronizer
                .actual_subscriptions
                .read()
                .expect(LOCK_ERR);
            if let Some(pattern_channels) = actual.get(&PubSubSubscriptionKind::Pattern) {
                patterns.extend(pattern_channels.clone());
            }
        }

        Ok(Value::Int(patterns.len() as i64))
    }

    /// Handle PUBSUB NUMSUB
    /// Returns the number of subscribers for each specified channel
    async fn handle_pubsub_numsub(&self, cmd: &Cmd) -> RedisResult<Value> {
        // Channels start at arg_idx(2) - after "PUBSUB" and "NUMSUB"
        let channels: Vec<Vec<u8>> = cmd
            .args_iter()
            .skip(2) // Skip "PUBSUB" and "NUMSUB"
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(bytes.to_vec()),
                redis::Arg::Cursor => None,
            })
            .collect();

        // If no channels specified, return empty map
        if channels.is_empty() {
            return Ok(Value::Map(vec![]));
        }

        let clients = self.clients.read().await;

        // Count subscribers for each channel
        let mut result: Vec<(Value, Value)> = Vec::new();

        for channel in &channels {
            let mut count = 0i64;
            for (_, client_data) in clients.iter() {
                let actual = client_data
                    .synchronizer
                    .actual_subscriptions
                    .read()
                    .expect(LOCK_ERR);
                if let Some(exact_channels) = actual.get(&PubSubSubscriptionKind::Exact)
                    && exact_channels.contains(channel)
                {
                    count += 1;
                }
            }
            result.push((Value::BulkString(channel.clone()), Value::Int(count)));
        }

        Ok(Value::Map(result))
    }

    /// Handle PUBSUB SHARDCHANNELS
    /// Returns all active sharded channels, optionally filtered by pattern
    async fn handle_pubsub_shardchannels(&self, cmd: &Cmd) -> RedisResult<Value> {
        // Pattern is at arg_idx(2) - after "PUBSUB" and "SHARDCHANNELS"
        let pattern = cmd
            .arg_idx(2)
            .map(|p| String::from_utf8_lossy(p).to_string());

        let clients = self.clients.read().await;
        let mut channels: HashSet<Vec<u8>> = HashSet::new();

        for (_, client_data) in clients.iter() {
            let actual = client_data
                .synchronizer
                .actual_subscriptions
                .read()
                .expect(LOCK_ERR);
            if let Some(sharded_channels) = actual.get(&PubSubSubscriptionKind::Sharded) {
                for channel in sharded_channels {
                    if let Some(ref pat) = pattern {
                        let channel_str = String::from_utf8_lossy(channel);
                        if glob_match(pat, &channel_str) {
                            channels.insert(channel.clone());
                        }
                    } else {
                        channels.insert(channel.clone());
                    }
                }
            }
        }

        let result: Vec<Value> = channels.into_iter().map(Value::BulkString).collect();
        Ok(Value::Array(result))
    }

    /// Handle PUBSUB SHARDNUMSUB
    /// Returns the number of subscribers for each specified sharded channel
    async fn handle_pubsub_shardnumsub(&self, cmd: &Cmd) -> RedisResult<Value> {
        // Channels start at arg_idx(2) - after "PUBSUB" and "SHARDNUMSUB"
        let channels: Vec<Vec<u8>> = cmd
            .args_iter()
            .skip(2) // Skip "PUBSUB" and "SHARDNUMSUB"
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(bytes.to_vec()),
                redis::Arg::Cursor => None,
            })
            .collect();

        // If no channels specified, return empty map
        if channels.is_empty() {
            return Ok(Value::Map(vec![]));
        }

        let clients = self.clients.read().await;

        // Count subscribers for each channel
        let mut result: Vec<(Value, Value)> = Vec::new();

        for channel in &channels {
            let mut count = 0i64;
            for (_, client_data) in clients.iter() {
                let actual = client_data
                    .synchronizer
                    .actual_subscriptions
                    .read()
                    .expect(LOCK_ERR);
                if let Some(sharded_channels) = actual.get(&PubSubSubscriptionKind::Sharded)
                    && sharded_channels.contains(channel)
                {
                    count += 1;
                }
            }
            result.push((Value::BulkString(channel.clone()), Value::Int(count)));
        }

        Ok(Value::Map(result))
    }

    pub fn extract_channels_from_cmd(cmd: &Cmd) -> Vec<PubSubChannelOrPattern> {
        cmd.args_iter()
            .skip(1)
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(bytes.to_vec()),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    pub fn extract_channels_and_timeout(cmd: &Cmd) -> (Vec<String>, u64) {
        let mut channels = Vec::new();
        let mut timeout_ms = 5000u64;

        let args: Vec<_> = cmd
            .args_iter()
            .skip(1)
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).to_string()),
                redis::Arg::Cursor => None,
            })
            .collect();

        if args.is_empty() {
            return (channels, timeout_ms);
        }

        if let Some(last_arg) = args.last() {
            if let Ok(timeout) = last_arg.parse::<u64>() {
                timeout_ms = timeout;
                channels = args[..args.len() - 1].to_vec();
            } else {
                channels = args;
            }
        } else {
            channels = args;
        }

        (channels, timeout_ms)
    }

    pub fn convert_sub_map_to_value(map: PubSubSubscriptionInfo) -> Value {
        let mut redis_map = Vec::new();
        for (kind, values) in map {
            let key = match kind {
                PubSubSubscriptionKind::Exact => "Exact",
                PubSubSubscriptionKind::Pattern => "Pattern",
                PubSubSubscriptionKind::Sharded => "Sharded",
            };
            let values_array: Vec<Value> = values.into_iter().map(Value::BulkString).collect();
            redis_map.push((
                Value::BulkString(key.as_bytes().to_vec()),
                Value::Array(values_array),
            ));
        }
        Value::Map(redis_map)
    }

    fn handle_lazy_subscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: PubSubSubscriptionKind,
    ) -> RedisResult<Value> {
        let channels = Self::extract_channels_from_cmd(cmd);
        if channels.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "No channels provided for subscription",
            )));
        }

        if let Some(sync) = sync {
            sync.add_desired_subscriptions(channels.into_iter().collect(), sub_type);
        }

        Ok(Value::Nil)
    }

    fn handle_lazy_unsubscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: PubSubSubscriptionKind,
    ) -> RedisResult<Value> {
        let channels = Self::extract_channels_from_cmd(cmd);
        let channels = if channels.is_empty() {
            None
        } else {
            Some(channels.into_iter().collect())
        };

        if let Some(sync) = sync {
            sync.remove_desired_subscriptions(channels, sub_type);
        }

        Ok(Value::Nil)
    }

    async fn handle_blocking_subscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: PubSubSubscriptionKind,
    ) -> RedisResult<Value> {
        let (channels, timeout_ms) = Self::extract_channels_and_timeout(cmd);
        if channels.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "No channels provided for subscription",
            )));
        }

        let channels_bytes: Vec<PubSubChannelOrPattern> =
            channels.iter().map(|s| s.as_bytes().to_vec()).collect();

        if let Some(sync) = sync {
            sync.add_desired_subscriptions(channels_bytes.clone().into_iter().collect(), sub_type);

            let start = std::time::Instant::now();
            loop {
                let actual_set = sync
                    .actual_subscriptions
                    .read()
                    .expect(LOCK_ERR)
                    .get(&sub_type)
                    .cloned()
                    .unwrap_or_default();

                if channels_bytes.iter().all(|ch| actual_set.contains(ch)) {
                    return Ok(Value::Nil);
                }

                let elapsed_ms = start.elapsed().as_millis() as u64;
                if timeout_ms > 0 && elapsed_ms >= timeout_ms {
                    return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                }

                let remaining_ms = if timeout_ms > 0 {
                    timeout_ms.saturating_sub(elapsed_ms)
                } else {
                    u64::MAX
                };

                tokio::select! {
                    _ = sync.reconciliation_complete_notify.notified() => {
                        continue;
                    }
                    _ = tokio::time::sleep(Duration::from_millis(remaining_ms.min(100))) => {
                        if timeout_ms > 0 && start.elapsed().as_millis() as u64 >= timeout_ms {
                            return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                        }
                    }
                }
            }
        }

        Ok(Value::Nil)
    }

    async fn handle_blocking_unsubscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: PubSubSubscriptionKind,
    ) -> RedisResult<Value> {
        let (channels, timeout_ms) = Self::extract_channels_and_timeout(cmd);

        let channels_bytes: Option<Vec<PubSubChannelOrPattern>> = if channels.is_empty() {
            None
        } else {
            Some(channels.iter().map(|s| s.as_bytes().to_vec()).collect())
        };

        if let Some(sync) = sync {
            sync.remove_desired_subscriptions(
                channels_bytes.clone().map(|v| v.into_iter().collect()),
                sub_type,
            );

            let start = std::time::Instant::now();
            loop {
                let actual_set = sync
                    .actual_subscriptions
                    .read()
                    .expect(LOCK_ERR)
                    .get(&sub_type)
                    .cloned()
                    .unwrap_or_default();

                let is_removed = if let Some(ref channels) = channels_bytes {
                    channels.iter().all(|ch| !actual_set.contains(ch))
                } else {
                    actual_set.is_empty()
                };

                if is_removed {
                    return Ok(Value::Nil);
                }

                let elapsed_ms = start.elapsed().as_millis() as u64;
                if timeout_ms > 0 && elapsed_ms >= timeout_ms {
                    return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                }

                let remaining_ms = if timeout_ms > 0 {
                    timeout_ms.saturating_sub(elapsed_ms)
                } else {
                    u64::MAX
                };

                tokio::select! {
                    _ = sync.reconciliation_complete_notify.notified() => {
                        continue;
                    }
                    _ = tokio::time::sleep(Duration::from_millis(remaining_ms.min(100))) => {
                        if timeout_ms > 0 && start.elapsed().as_millis() as u64 >= timeout_ms {
                            return Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into());
                        }
                    }
                }
            }
        }

        Ok(Value::Nil)
    }

    async fn handle_publish(&self, cmd: &Cmd, is_sharded: bool) -> RedisResult<Value> {
        let channel = cmd
            .arg_idx(1)
            .ok_or_else(|| RedisError::from((ErrorKind::ResponseError, "Missing channel")))?;
        let message = cmd
            .arg_idx(2)
            .ok_or_else(|| RedisError::from((ErrorKind::ResponseError, "Missing message")))?;

        let channel_str = std::str::from_utf8(channel)
            .map_err(|_| RedisError::from((ErrorKind::ResponseError, "Invalid channel UTF-8")))?;

        let count = self.publish(channel_str, message, is_sharded).await;

        Ok(Value::Int(count))
    }

    fn get_subscriptions_as_value(&self, sync: Option<&Arc<MockPubSubSynchronizer>>) -> Value {
        if let Some(sync) = sync {
            let (desired, actual) = sync.get_subscription_state();

            let result = vec![
                Value::BulkString(b"desired".to_vec()),
                Self::convert_sub_map_to_value(desired),
                Value::BulkString(b"actual".to_vec()),
                Self::convert_sub_map_to_value(actual),
            ];

            Value::Array(result)
        } else {
            let empty_map = HashMap::new();
            let result = vec![
                Value::BulkString(b"desired".to_vec()),
                Self::convert_sub_map_to_value(empty_map.clone()),
                Value::BulkString(b"actual".to_vec()),
                Self::convert_sub_map_to_value(empty_map),
            ];
            Value::Array(result)
        }
    }

    pub async fn publish(&self, channel: &str, message: &[u8], sharded: bool) -> i64 {
        let clients = self.clients.read().await;
        let mut recipient_count = 0;
        let channel_bytes = channel.as_bytes().to_vec();

        for (client_id, client_data) in clients.iter() {
            let sync = &client_data.synchronizer;
            let actual = sync.actual_subscriptions.read().expect(LOCK_ERR);

            if sharded {
                let actual_sharded = actual
                    .get(&PubSubSubscriptionKind::Sharded)
                    .cloned()
                    .unwrap_or_default();
                drop(actual); // Release lock before send
                if actual_sharded.contains(&channel_bytes) {
                    let push_info = create_push_info(channel, message, None, sharded);
                    if let Err(e) = client_data.push_sender.send(push_info) {
                        log_warn(
                            "mock_pubsub",
                            format!("Failed to send to client {client_id}: {e}"),
                        );
                    } else {
                        recipient_count += 1;
                    }
                }
            } else {
                let actual_channels = actual
                    .get(&PubSubSubscriptionKind::Exact)
                    .cloned()
                    .unwrap_or_default();
                let actual_patterns = actual
                    .get(&PubSubSubscriptionKind::Pattern)
                    .cloned()
                    .unwrap_or_default();
                drop(actual); // Release lock before send

                if actual_channels.contains(&channel_bytes) {
                    let push_info = create_push_info(channel, message, None, false);
                    if let Err(e) = client_data.push_sender.send(push_info) {
                        log_warn(
                            "mock_pubsub",
                            format!("Failed to send to client {client_id} (exact): {e}"),
                        );
                    } else {
                        recipient_count += 1;
                    }
                }

                for pat_bytes in actual_patterns.iter() {
                    let pat = String::from_utf8_lossy(pat_bytes);
                    if glob_match(&pat, channel) {
                        let push_info = create_push_info(channel, message, Some(&pat), false);
                        if let Err(e) = client_data.push_sender.send(push_info) {
                            log_warn(
                                "mock_pubsub",
                                format!("Failed to send to client {client_id}: {e}"),
                            );
                        } else {
                            recipient_count += 1;
                        }
                    }
                }
            }
        }

        recipient_count
    }

    pub fn is_acl_pubsub_permission_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();

        if command_name != b"ACL SETUSER" {
            return false;
        }

        if let Some(username_bytes) = cmd.arg_idx(2) {
            let username = String::from_utf8_lossy(username_bytes);
            if !username.starts_with("mock_test_user_") {
                return false;
            }
        } else {
            return false;
        }

        for i in 3..cmd.args_iter().count() {
            if let Some(arg) = cmd.arg_idx(i) {
                let arg_str = String::from_utf8_lossy(arg);
                if arg_str.starts_with("-@pubsub")
                    || arg_str.starts_with("+@pubsub")
                    || arg_str.starts_with("&")
                    || arg_str == "resetchannels"
                {
                    return true;
                }
            }
        }
        false
    }

    pub fn is_mock_auth_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();
        if command_name != b"AUTH" {
            return false;
        }

        let arg_count = cmd.args_iter().count();

        if arg_count >= 2
            && let Some(username_bytes) = cmd.arg_idx(1)
        {
            let username = String::from_utf8_lossy(username_bytes);
            return username.starts_with("mock_test_user_");
        }
        false
    }

    pub async fn handle_acl_pubsub_permission(&self, cmd: &Cmd) {
        let username = if let Some(username_bytes) = cmd.arg_idx(2) {
            String::from_utf8_lossy(username_bytes).to_string()
        } else {
            return;
        };

        let mut can_subscribe = true;

        for i in 3..cmd.args_iter().len() {
            if let Some(arg) = cmd.arg_idx(i) {
                let arg_str = String::from_utf8_lossy(arg);
                if arg_str.starts_with("-@pubsub") || arg_str == "resetchannels" {
                    can_subscribe = false;
                    break;
                } else if arg_str.starts_with("+@pubsub") {
                    can_subscribe = true;
                    break;
                }
            }
        }

        {
            let mut perms = self.username_permissions.write().await;
            perms.insert(username.clone(), can_subscribe);
        }

        {
            let mut clients = self.clients.write().await;
            for (client_id, client) in clients.iter_mut() {
                if client.username.as_ref() == Some(&username) {
                    client.synchronizer.set_can_subscribe(can_subscribe);
                    log_debug(
                        "mock_pubsub",
                        format!(
                            "Updated client {client_id} can_subscribe to {can_subscribe} due to ACL change for user {username}"
                        ),
                    );
                }
            }
        }

        log_debug(
            "mock_pubsub",
            format!("ACL command for username {username}, set can_subscribe to {can_subscribe}"),
        );
    }

    pub async fn unregister_client(&self, client_id: &str) {
        let mut clients = self.clients.write().await;
        clients.remove(client_id);
    }
}

fn glob_match(pattern: &str, text: &str) -> bool {
    let pattern_parts: Vec<&str> = pattern.split('*').collect();

    if pattern_parts.len() == 1 {
        return pattern == text;
    }

    let mut text_idx = 0;
    for (i, part) in pattern_parts.iter().enumerate() {
        if part.is_empty() {
            continue;
        }

        if i == 0 {
            if !text.starts_with(part) {
                return false;
            }
            text_idx = part.len();
        } else if i == pattern_parts.len() - 1 {
            return text[text_idx..].ends_with(part);
        } else if let Some(pos) = text[text_idx..].find(part) {
            text_idx += pos + part.len();
        } else {
            return false;
        }
    }
    true
}

fn create_push_info(
    channel: &str,
    message: &[u8],
    pattern: Option<&str>,
    sharded: bool,
) -> PushInfo {
    let kind = if sharded {
        PushKind::SMessage
    } else if pattern.is_some() {
        PushKind::PMessage
    } else {
        PushKind::Message
    };

    let mut data = Vec::new();
    if let Some(pat) = pattern {
        data.push(Value::BulkString(pat.as_bytes().to_vec()));
    }
    data.push(Value::BulkString(channel.as_bytes().to_vec()));
    data.push(Value::BulkString(message.to_vec()));

    PushInfo { kind, data }
}
