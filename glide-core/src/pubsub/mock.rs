// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use super::{PubSubSynchronizer, SubscriptionType};
use async_trait::async_trait;
use logger_core::{log_debug, log_error, log_warn};
use once_cell::sync::Lazy;
use redis::{Cmd, ErrorKind, PushInfo, PushKind, RedisError, RedisResult, Value};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::time::Duration;
use telemetrylib::GlideOpenTelemetry;
use tokio::sync::{RwLock, mpsc, Notify};
use tokio::time::sleep;
use redis::cluster_routing::Routable;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Mutex;

/// Global singleton mock PubSub broker
static MOCK_BROKER: Lazy<Arc<MockPubSubBroker>> = Lazy::new(|| Arc::new(MockPubSubBroker::new()));

// Client ID generation
static MOCK_CLIENT_COUNTER: AtomicU64 = AtomicU64::new(0);

/// Get the global mock broker instance
pub fn get_mock_broker() -> Arc<MockPubSubBroker> {
    Arc::clone(&MOCK_BROKER)
}

/// Mock implementation of PubSub synchronizer
pub struct MockPubSubSynchronizer {
    client_id: String,
    self_ref: once_cell::sync::OnceCell<std::sync::Weak<Self>>,
    client: once_cell::sync::OnceCell<std::sync::Weak<tokio::sync::RwLock<crate::client::Client>>>,
    
    desired_channels: Arc<RwLock<HashSet<String>>>,
    desired_patterns: Arc<RwLock<HashSet<String>>>,
    desired_sharded: Arc<RwLock<HashSet<String>>>,
    
    actual_channels: Arc<RwLock<HashSet<String>>>,
    actual_patterns: Arc<RwLock<HashSet<String>>>,
    actual_sharded: Arc<RwLock<HashSet<String>>>,
    
    can_subscribe: Arc<RwLock<bool>>,
    is_cluster: bool,
    broker: Arc<MockPubSubBroker>,
    reconciliation_notify: Arc<Notify>,
    reconciliation_complete_notify: Arc<Notify>,
    reconciliation_task_handle: Arc<Mutex<Option<tokio::task::JoinHandle<()>>>>,
}

impl MockPubSubSynchronizer {
    fn new_internal(client_id: String, is_cluster: bool, broker: Arc<MockPubSubBroker>) -> Arc<Self> {
        let sync = Arc::new(Self {
            client_id,
            self_ref: once_cell::sync::OnceCell::new(),
            client: once_cell::sync::OnceCell::new(),
            desired_channels: Arc::new(RwLock::new(HashSet::new())),
            desired_patterns: Arc::new(RwLock::new(HashSet::new())),
            desired_sharded: Arc::new(RwLock::new(HashSet::new())),
            actual_channels: Arc::new(RwLock::new(HashSet::new())),
            actual_patterns: Arc::new(RwLock::new(HashSet::new())),
            actual_sharded: Arc::new(RwLock::new(HashSet::new())),
            can_subscribe: Arc::new(RwLock::new(true)),
            is_cluster,
            broker,
            reconciliation_notify: Arc::new(Notify::new()),
            reconciliation_complete_notify: Arc::new(Notify::new()),
            reconciliation_task_handle: Arc::new(Mutex::new(None)),
        });
        
        let _ = sync.self_ref.set(Arc::downgrade(&sync));
        sync
    }

    pub fn new(
        cluster_mode: bool,
        initial_subscriptions: Option<redis::PubSubSubscriptionInfo>,
    ) -> Arc<dyn PubSubSynchronizer> {
        let client_id = format!(
            "mock_client_{}",
            MOCK_CLIENT_COUNTER.fetch_add(1, Ordering::SeqCst)
        );
        
        let broker = get_mock_broker();
        let synchronizer = Self::new_internal(client_id, cluster_mode, Arc::clone(&broker));
        
        synchronizer.start_reconciliation_task();
        
        if let Some(subs) = initial_subscriptions {
            let channels = extract_channels_from_subscription_info(
                &subs,
                redis::PubSubSubscriptionKind::Exact,
            );
            let patterns = extract_channels_from_subscription_info(
                &subs,
                redis::PubSubSubscriptionKind::Pattern,
            );
            let sharded = if cluster_mode {
                extract_channels_from_subscription_info(
                    &subs,
                    redis::PubSubSubscriptionKind::Sharded,
                )
            } else {
                HashSet::new()
            };
            
            let sync_clone = synchronizer.clone();
            tokio::spawn(async move {
                sync_clone.set_initial_subscriptions(channels, patterns, sharded).await;
            });
        }
        
        synchronizer
    }

    /// Public method to set client reference
    pub fn set_client(&self, client: std::sync::Weak<tokio::sync::RwLock<crate::client::Client>>) -> Result<(), String> {
        self.client.set(client)
            .map_err(|_| "Client already set".to_string())?;
        
        let self_weak = self.self_ref.get()
            .ok_or("Self ref not set")?
            .clone();
        
        tokio::spawn(async move {
            if let Some(sync) = self_weak.upgrade() {
                if let Err(e) = sync.register_with_broker().await {
                    log_error(
                        "MockPubSubSynchronizer",
                        format!("Failed to register with broker: {}", e),
                    );
                }
            }
        });
        
        Ok(())
    }

    async fn register_with_broker(&self) -> Result<(), String> {
        log_debug(
            "MockPubSubSynchronizer::register_with_broker",
            format!("Registering client {}", self.client_id),
        );
        
        let client_weak = self.client.get()
            .ok_or("Client not set")?;
        
        let client_arc = client_weak.upgrade()
            .ok_or("Client dropped")?;
        
        let client_guard = client_arc.read().await;
        
        let push_sender = client_guard.get_push_sender()
            .ok_or("No push sender configured")?;
        
        let self_arc = self.self_ref.get()
            .ok_or("Self ref not set")?
            .upgrade()
            .ok_or("Self dropped")?;
        
        self.broker.register_client(
            self.client_id.clone(),
            push_sender,
            self_arc,
        ).await;
        
        log_debug(
            "MockPubSubSynchronizer::register_with_broker",
            format!("Successfully registered client {}", self.client_id),
        );
        
        Ok(())
    }

    pub(crate) async fn set_can_subscribe(&self, can_subscribe: bool) {
        *self.can_subscribe.write().await = can_subscribe;
    }

    async fn get_can_subscribe(&self) -> bool {
        *self.can_subscribe.read().await
    }

    pub(crate) async fn is_synchronized(&self) -> bool {
        let desired_channels = self.desired_channels.read().await;
        let actual_channels = self.actual_channels.read().await;
        let desired_patterns = self.desired_patterns.read().await;
        let actual_patterns = self.actual_patterns.read().await;
        let desired_sharded = self.desired_sharded.read().await;
        let actual_sharded = self.actual_sharded.read().await;
        
        *desired_channels == *actual_channels
            && *desired_patterns == *actual_patterns
            && *desired_sharded == *actual_sharded
    }

    async fn trigger_reconciliation(&self) {
        self.reconciliation_notify.notify_one();
    }

    fn start_reconciliation_task(self: &Arc<Self>) {
        let sync_weak = Arc::downgrade(self);
        let notify = Arc::clone(&self.reconciliation_notify);
        let complete_notify = Arc::clone(&self.reconciliation_complete_notify);
        let broker = Arc::clone(&self.broker);
        
        let handle = tokio::spawn(async move {
            loop {
                tokio::select! {
                    _ = notify.notified() => {},
                    _ = tokio::time::sleep(Duration::from_secs(5)) => {},
                }
                
                let Some(sync) = sync_weak.upgrade() else {
                    log_debug("reconciliation_task", "Synchronizer dropped, exiting task");
                    break;
                };
                
                let delay = *broker.max_application_delay_ms.read().await;
                if delay > 0 {
                    tokio::time::sleep(Duration::from_millis(delay)).await;
                }
                
                if let Err(e) = sync.reconcile().await {
                    log_warn(
                        "reconciliation_task",
                        format!("Reconciliation failed for client {}: {}", sync.client_id, e),
                    );
                }
                broker.check_and_record_sync_state(&sync).await;
                complete_notify.notify_waiters();
            }
        });
        
        *self.reconciliation_task_handle.lock().unwrap() = Some(handle);
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

    async fn add_desired_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    ) {
        let mut set = match subscription_type {
            SubscriptionType::Exact => self.desired_channels.write().await,
            SubscriptionType::Pattern => self.desired_patterns.write().await,
            SubscriptionType::Sharded => self.desired_sharded.write().await,
        };
        
        for channel in channels {
            set.insert(channel);
        }
        drop(set);
        
        self.trigger_reconciliation().await;
    }

    async fn remove_desired_subscriptions(
        &self,
        channels: Option<HashSet<String>>,
        subscription_type: SubscriptionType,
    ) {
        let mut set = match subscription_type {
            SubscriptionType::Exact => self.desired_channels.write().await,
            SubscriptionType::Pattern => self.desired_patterns.write().await,
            SubscriptionType::Sharded => self.desired_sharded.write().await,
        };
        
        if let Some(channels) = channels {
            for channel in channels {
                set.remove(&channel);
            }
        } else {
            set.clear();
        }
        drop(set);
        
        self.trigger_reconciliation().await;
    }

    async fn add_current_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    ) {
        let mut set = match subscription_type {
            SubscriptionType::Exact => self.actual_channels.write().await,
            SubscriptionType::Pattern => self.actual_patterns.write().await,
            SubscriptionType::Sharded => self.actual_sharded.write().await,
        };
        
        for channel in channels {
            set.insert(channel);
        }
    }

    async fn remove_current_subscriptions(
        &self,
        channels: HashSet<String>,
        subscription_type: SubscriptionType,
    ) {
        let mut set = match subscription_type {
            SubscriptionType::Exact => self.actual_channels.write().await,
            SubscriptionType::Pattern => self.actual_patterns.write().await,
            SubscriptionType::Sharded => self.actual_sharded.write().await,
        };
        
        for channel in channels {
            set.remove(&channel);
        }
    }

    async fn get_subscription_state(
        &self,
    ) -> (
        HashMap<String, HashSet<String>>,
        HashMap<String, HashSet<String>>,
    ) {
        let mut desired = HashMap::new();
        desired.insert("Exact".to_string(), self.desired_channels.read().await.clone());
        desired.insert("Pattern".to_string(), self.desired_patterns.read().await.clone());
        if self.is_cluster {
            desired.insert("Sharded".to_string(), self.desired_sharded.read().await.clone());
        }

        let mut actual = HashMap::new();
        actual.insert("Exact".to_string(), self.actual_channels.read().await.clone());
        actual.insert("Pattern".to_string(), self.actual_patterns.read().await.clone());
        if self.is_cluster {
            actual.insert("Sharded".to_string(), self.actual_sharded.read().await.clone());
        }

        (desired, actual)
    }

    async fn reconcile(&self) -> Result<(), String> {
        let can_subscribe = self.get_can_subscribe().await;
        
        if !can_subscribe {
            return Err("Reconciliation blocked: no subscription permission".to_string());
        }

        {
            let desired = self.desired_channels.read().await.clone();
            let mut actual = self.actual_channels.write().await;
            for channel in &desired {
                actual.insert(channel.clone());
            }
            actual.retain(|ch| desired.contains(ch));
        }

        {
            let desired = self.desired_patterns.read().await.clone();
            let mut actual = self.actual_patterns.write().await;
            for pattern in &desired {
                actual.insert(pattern.clone());
            }
            actual.retain(|p| desired.contains(p));
        }

        {
            let desired = self.desired_sharded.read().await.clone();
            let mut actual = self.actual_sharded.write().await;
            for channel in &desired {
                actual.insert(channel.clone());
            }
            actual.retain(|ch| desired.contains(ch));
        }

        Ok(())
    }

    async fn intercept_pubsub_command(&self, cmd: &Cmd) -> Option<RedisResult<Value>> {
        if MockPubSubBroker::is_pubsub_command(cmd) {
            Some(self.broker.handle_pubsub_command(&self.client_id, cmd).await)
        } else {
            None
        }
    }

    async fn set_initial_subscriptions(
        &self,
        channels: HashSet<String>,
        patterns: HashSet<String>,
        sharded: HashSet<String>,
    ) {
        if !channels.is_empty() {
            let mut desired_channels = self.desired_channels.write().await;
            desired_channels.extend(channels);
        }
        
        if !patterns.is_empty() {
            let mut desired_patterns = self.desired_patterns.write().await;
            desired_patterns.extend(patterns);
        }
        
        if !sharded.is_empty() {
            let mut desired_sharded = self.desired_sharded.write().await;
            desired_sharded.extend(sharded);
        }
        
        if let Err(e) = self.reconcile().await {
            log_warn(
                "set_initial_subscriptions",
                format!("Failed to reconcile initial subscriptions: {e}"),
            );
        }
        
        self.broker.check_and_record_sync_state(self).await;
    }

    async fn remove_current_subscriptions_for_address(&self, _address: &str) {
        // Mock doesn't track by address, no-op
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
    pub(crate) clients: Arc<RwLock<HashMap<String, ClientData>>>,
    max_application_delay_ms: Arc<RwLock<u64>>,
    username_permissions: Arc<RwLock<HashMap<String, bool>>>,
}

impl MockPubSubBroker {
    fn new() -> Self {
        let broker = Self {
            clients: Arc::new(RwLock::new(HashMap::new())),
            max_application_delay_ms: Arc::new(RwLock::new(50)),
            username_permissions: Arc::new(RwLock::new(HashMap::new())),
        };
        broker.start_reconciliation_loop();
        broker
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
        clients_arc: &Arc<RwLock<HashMap<String, ClientData>>>,
        client_id: &str,
    ) {
        let synchronizer = {
            let clients = clients_arc.read().await;
            clients.get(client_id).map(|c| Arc::clone(&c.synchronizer))
        };

        if let Some(sync) = synchronizer {
            let _ = sync.reconcile().await;
            let broker = get_mock_broker();
            broker.check_and_record_sync_state(&sync).await;
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
        log_debug(
            "MockPubSubBroker",
            format!("Registered client {}", client_id),
        );
    }

    pub fn is_pubsub_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();
        let command_str = std::str::from_utf8(&command_name).unwrap_or("");

        let is_regular_pubsub = matches!(
            command_str,
            "SUBSCRIBE"
                | "UNSUBSCRIBE"
                | "PSUBSCRIBE"
                | "PUNSUBSCRIBE"
                | "SSUBSCRIBE"
                | "SUNSUBSCRIBE"
                | "SUBSCRIBE_LAZY"
                | "UNSUBSCRIBE_LAZY"
                | "PSUBSCRIBE_LAZY"
                | "PUNSUBSCRIBE_LAZY"
                | "SSUBSCRIBE_LAZY"
                | "SUNSUBSCRIBE_LAZY"
                | "PUBLISH"
                | "SPUBLISH"
                | "GET_SUBSCRIPTIONS"
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

            "SUBSCRIBE_LAZY" => {
                self.handle_lazy_subscribe(sync.as_ref(), cmd, SubscriptionType::Exact).await
            }
            "PSUBSCRIBE_LAZY" => {
                self.handle_lazy_subscribe(sync.as_ref(), cmd, SubscriptionType::Pattern).await
            }
            "SSUBSCRIBE_LAZY" => {
                self.handle_lazy_subscribe(sync.as_ref(), cmd, SubscriptionType::Sharded).await
            }

            "UNSUBSCRIBE_LAZY" => {
                self.handle_lazy_unsubscribe(sync.as_ref(), cmd, SubscriptionType::Exact).await
            }
            "PUNSUBSCRIBE_LAZY" => {
                self.handle_lazy_unsubscribe(sync.as_ref(), cmd, SubscriptionType::Pattern).await
            }
            "SUNSUBSCRIBE_LAZY" => {
                self.handle_lazy_unsubscribe(sync.as_ref(), cmd, SubscriptionType::Sharded).await
            }

            "SUBSCRIBE" => {
                self.handle_blocking_subscribe(sync.as_ref(), cmd, SubscriptionType::Exact).await
            }
            "PSUBSCRIBE" => {
                self.handle_blocking_subscribe(sync.as_ref(), cmd, SubscriptionType::Pattern).await
            }
            "SSUBSCRIBE" => {
                self.handle_blocking_subscribe(sync.as_ref(), cmd, SubscriptionType::Sharded).await
            }

            "UNSUBSCRIBE" => {
                self.handle_blocking_unsubscribe(sync.as_ref(), cmd, SubscriptionType::Exact).await
            }
            "PUNSUBSCRIBE" => {
                self.handle_blocking_unsubscribe(sync.as_ref(), cmd, SubscriptionType::Pattern).await
            }
            "SUNSUBSCRIBE" => {
                self.handle_blocking_unsubscribe(sync.as_ref(), cmd, SubscriptionType::Sharded).await
            }

            "GET_SUBSCRIPTIONS" => Ok(self.get_subscriptions_as_value(sync.as_ref()).await),

            _ => {
                Err(RedisError::from((
                    ErrorKind::ClientError,
                    "Unknown PubSub command",
                )))
            }
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
                    sync.set_can_subscribe(can_subscribe).await;
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

    pub fn extract_channels_from_cmd(cmd: &Cmd) -> Vec<String> {
        cmd.args_iter()
            .skip(1)
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).to_string()),
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

    pub fn convert_sub_map_to_value(map: HashMap<String, HashSet<String>>) -> Value {
        let mut redis_map = Vec::new();
        for (key, values) in map {
            let values_array: Vec<Value> = values
                .into_iter()
                .map(|v| Value::BulkString(v.into_bytes()))
                .collect();
            redis_map.push((
                Value::BulkString(key.into_bytes()),
                Value::Array(values_array),
            ));
        }
        Value::Map(redis_map)
    }

    async fn handle_lazy_subscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: SubscriptionType,
    ) -> RedisResult<Value> {
        let channels = Self::extract_channels_from_cmd(cmd);
        if channels.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "No channels provided for subscription",
            )));
        }

        if let Some(sync) = sync {
            sync.add_desired_subscriptions(channels.into_iter().collect(), sub_type).await;
        }
        
        Ok(Value::Nil)
    }

    async fn handle_lazy_unsubscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: SubscriptionType,
    ) -> RedisResult<Value> {
        let channels = Self::extract_channels_from_cmd(cmd);
        let channels = if channels.is_empty() {
            None
        } else {
            Some(channels.into_iter().collect())
        };

        if let Some(sync) = sync {
            sync.remove_desired_subscriptions(channels, sub_type).await;
        }
        
        Ok(Value::Nil)
    }

    async fn handle_blocking_subscribe(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
        cmd: &Cmd,
        sub_type: SubscriptionType,
    ) -> RedisResult<Value> {
        let (channels, timeout_ms) = Self::extract_channels_and_timeout(cmd);
        if channels.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "No channels provided for subscription",
            )));
        }

        if let Some(sync) = sync {
            sync.add_desired_subscriptions(channels.clone().into_iter().collect(), sub_type.clone()).await;
            
            let start = std::time::Instant::now();
            loop {
                let actual_set = match sub_type {
                    SubscriptionType::Exact => sync.actual_channels.read().await.clone(),
                    SubscriptionType::Pattern => sync.actual_patterns.read().await.clone(),
                    SubscriptionType::Sharded => sync.actual_sharded.read().await.clone(),
                };

                if channels.iter().all(|ch| actual_set.contains(ch)) {
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
        sub_type: SubscriptionType,
    ) -> RedisResult<Value> {
        let (channels, timeout_ms) = Self::extract_channels_and_timeout(cmd);
        let channels_opt = if channels.is_empty() {
            None
        } else {
            Some(channels.clone())
        };

        if let Some(sync) = sync {
            sync.remove_desired_subscriptions(
                channels_opt.clone().map(|v| v.into_iter().collect()),
                sub_type.clone()
            ).await;
            
            let start = std::time::Instant::now();
            loop {
                let actual_set = match sub_type {
                    SubscriptionType::Exact => sync.actual_channels.read().await.clone(),
                    SubscriptionType::Pattern => sync.actual_patterns.read().await.clone(),
                    SubscriptionType::Sharded => sync.actual_sharded.read().await.clone(),
                };

                let is_removed = if let Some(ref channels) = channels_opt {
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

    async fn get_subscriptions_as_value(
        &self,
        sync: Option<&Arc<MockPubSubSynchronizer>>,
    ) -> Value {
        if let Some(sync) = sync {
            let (desired, actual) = sync.get_subscription_state().await;
            
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

        for (client_id, client_data) in clients.iter() {
            let sync = &client_data.synchronizer;
            
            if sharded {
                let actual_sharded = sync.actual_sharded.read().await;
                if actual_sharded.contains(channel) {
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
                let actual_channels = sync.actual_channels.read().await;
                if actual_channels.contains(channel) {
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

                let actual_patterns = sync.actual_patterns.read().await;
                for pat in actual_patterns.iter() {
                    if glob_match(pat, channel) {
                        let push_info = create_push_info(channel, message, Some(pat), false);
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

    pub(crate) async fn check_and_record_sync_state(&self, sync: &MockPubSubSynchronizer) {
        let is_synced = sync.is_synchronized().await;

        if is_synced {
            let _ = GlideOpenTelemetry::update_subscription_last_sync_timestamp();
            log_debug(
                "mock_pubsub",
                format!("Client {} subscriptions in sync", sync.client_id),
            );
        } else {
            let _ = GlideOpenTelemetry::record_subscription_out_of_sync();
            let (desired, actual) = sync.get_subscription_state().await;
            log_debug(
                "mock_pubsub",
                format!(
                    "Client {} subscriptions out of sync - desired: {:?}, actual: {:?}",
                    sync.client_id, desired, actual
                ),
            );
        }
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

        if arg_count >= 2 {
            if let Some(username_bytes) = cmd.arg_idx(1) {
                let username = String::from_utf8_lossy(username_bytes);
                return username.starts_with("mock_test_user_");
            }
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
                    client.synchronizer.set_can_subscribe(can_subscribe).await;
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
        if clients.remove(client_id).is_some() {
            log_debug(
                "mock_pubsub",
                format!("Unregistered client {}", client_id),
            );
        }
    }
}

fn extract_channels_from_subscription_info(
    subs: &redis::PubSubSubscriptionInfo,
    kind: redis::PubSubSubscriptionKind,
) -> HashSet<String> {
    subs.get(&kind)
        .map(|set| {
            set.iter()
                .filter_map(|bytes| String::from_utf8(bytes.clone()).ok())
                .collect()
        })
        .unwrap_or_default()
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
        } else {
            if let Some(pos) = text[text_idx..].find(part) {
                text_idx += pos + part.len();
            } else {
                return false;
            }
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
        PushKind::Message
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