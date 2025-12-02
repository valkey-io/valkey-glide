// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use logger_core::{log_debug, log_warn};
use once_cell::sync::Lazy;
use redis::cluster_routing::Routable;
use redis::{Cmd, ErrorKind, PushInfo, PushKind, RedisError, RedisResult, Value};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use std::time::Duration;
use telemetrylib::GlideOpenTelemetry;
use tokio::sync::{RwLock, mpsc};
use tokio::time::sleep;

/// Global singleton mock PubSub broker
static MOCK_BROKER: Lazy<Arc<MockPubSubBroker>> = Lazy::new(|| Arc::new(MockPubSubBroker::new()));

/// Get the global mock broker instance
pub fn get_mock_broker() -> Arc<MockPubSubBroker> {
    Arc::clone(&MOCK_BROKER)
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum SubscriptionType {
    Exact,
    Pattern,
    Sharded,
}

#[derive(Debug, Clone)]
struct ClientSubscriptions {
    desired_channels: HashSet<String>,
    desired_patterns: HashSet<String>,
    desired_sharded: HashSet<String>,

    actual_channels: HashSet<String>,
    actual_patterns: HashSet<String>,
    actual_sharded: HashSet<String>,

    push_sender: mpsc::UnboundedSender<PushInfo>,
    is_cluster: bool,
    can_subscribe: bool,
    username: Option<String>,
}

pub struct MockPubSubBroker {
    clients: Arc<RwLock<HashMap<String, ClientSubscriptions>>>,
    max_application_delay_ms: Arc<RwLock<u64>>,
    username_permissions: Arc<RwLock<HashMap<String, bool>>>,
}

impl MockPubSubBroker {
    fn new() -> Self {
        let broker = Self {
            clients: Arc::new(RwLock::new(HashMap::new())),
            max_application_delay_ms: Arc::new(RwLock::new(500)),
            username_permissions: Arc::new(RwLock::new(HashMap::new())),
        };

        // Start background reconciliation task
        broker.start_reconciliation_loop();

        broker
    }

    /// Start a background task that reconciles all clients every 5 seconds
    fn start_reconciliation_loop(&self) {
        let clients_arc = Arc::clone(&self.clients);

        tokio::spawn(async move {
            loop {
                sleep(Duration::from_secs(5)).await;

                // Get all client IDs and their can_subscribe flags
                let client_states: Vec<(String, bool)> = {
                    let clients = clients_arc.read().await;
                    clients
                        .iter()
                        .map(|(id, client)| (id.clone(), client.can_subscribe))
                        .collect()
                };

                // Reconcile each client with its own can_subscribe flag
                for (client_id, can_subscribe) in client_states {
                    Self::reconcile_client_static(&clients_arc, &client_id, can_subscribe).await;
                }
            }
        });
    }

    /// Static reconciliation method that can be called from anywhere
    async fn reconcile_client_static(
        clients_arc: &Arc<RwLock<HashMap<String, ClientSubscriptions>>>,
        client_id: &str,
        can_subscribe: bool,
    ) {
        if can_subscribe {
            // Apply desired state to actual state
            let mut clients = clients_arc.write().await;
            if let Some(client) = clients.get_mut(client_id) {
                // Sync channels: add missing, remove extra
                for channel in &client.desired_channels.clone() {
                    client.actual_channels.insert(channel.clone());
                }
                client
                    .actual_channels
                    .retain(|ch| client.desired_channels.contains(ch));

                // Sync patterns
                for pattern in &client.desired_patterns.clone() {
                    client.actual_patterns.insert(pattern.clone());
                }
                client
                    .actual_patterns
                    .retain(|p| client.desired_patterns.contains(p));

                // Sync sharded
                for channel in &client.desired_sharded.clone() {
                    client.actual_sharded.insert(channel.clone());
                }
                client
                    .actual_sharded
                    .retain(|ch| client.desired_sharded.contains(ch));
            }
            drop(clients);
        }

        // Check and record sync state
        let broker = get_mock_broker();
        broker.check_and_record_sync_state(client_id).await;
    }

    /// Register a client with the broker
    pub async fn register_client(
        &self,
        client_id: String,
        push_sender: mpsc::UnboundedSender<PushInfo>,
        is_cluster: bool,
    ) {
        let mut clients = self.clients.write().await;
        clients.insert(
            client_id.clone(),
            ClientSubscriptions {
                desired_channels: HashSet::new(),
                desired_patterns: HashSet::new(),
                desired_sharded: HashSet::new(),
                actual_channels: HashSet::new(),
                actual_patterns: HashSet::new(),
                actual_sharded: HashSet::new(),
                push_sender,
                is_cluster,
                can_subscribe: true,
                username: None,
            },
        );
    }

    /// Check if command is any PubSub command
    pub fn is_pubsub_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();
        let command_str = std::str::from_utf8(&command_name).unwrap_or("");

        // Check regular pubsub commands
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

        // Also route ACL commands that affect pubsub through the pubsub handler
        is_regular_pubsub
            || Self::is_mock_auth_command(cmd)
            || Self::is_acl_pubsub_permission_command(cmd)
    }

    pub async fn handle_pubsub_command(&self, client_id: &str, cmd: &Cmd) -> RedisResult<Value> {
        let command_name = cmd.command().unwrap_or_default();
        let command_str = std::str::from_utf8(&command_name).unwrap_or("");

        // Core PubSub commands
        let result = match command_str {
            "PUBLISH" => self.handle_publish(cmd, false).await,
            "SPUBLISH" => self.handle_publish(cmd, true).await,

            "SUBSCRIBE_LAZY" => {
                self.handle_lazy_subscribe_from_cmd(client_id, cmd, SubscriptionType::Exact)
                    .await
            }
            "PSUBSCRIBE_LAZY" => {
                self.handle_lazy_subscribe_from_cmd(client_id, cmd, SubscriptionType::Pattern)
                    .await
            }
            "SSUBSCRIBE_LAZY" => {
                self.handle_lazy_subscribe_from_cmd(client_id, cmd, SubscriptionType::Sharded)
                    .await
            }

            "UNSUBSCRIBE_LAZY" => {
                self.handle_lazy_unsubscribe_from_cmd(client_id, cmd, SubscriptionType::Exact)
                    .await
            }
            "PUNSUBSCRIBE_LAZY" => {
                self.handle_lazy_unsubscribe_from_cmd(client_id, cmd, SubscriptionType::Pattern)
                    .await
            }
            "SUNSUBSCRIBE_LAZY" => {
                self.handle_lazy_unsubscribe_from_cmd(client_id, cmd, SubscriptionType::Sharded)
                    .await
            }

            "SUBSCRIBE" => {
                self.handle_blocking_subscribe_from_cmd(client_id, cmd, SubscriptionType::Exact)
                    .await
            }
            "PSUBSCRIBE" => {
                self.handle_blocking_subscribe_from_cmd(client_id, cmd, SubscriptionType::Pattern)
                    .await
            }
            "SSUBSCRIBE" => {
                self.handle_blocking_subscribe_from_cmd(client_id, cmd, SubscriptionType::Sharded)
                    .await
            }

            "UNSUBSCRIBE" => {
                self.handle_blocking_unsubscribe_from_cmd(client_id, cmd, SubscriptionType::Exact)
                    .await
            }
            "PUNSUBSCRIBE" => {
                self.handle_blocking_unsubscribe_from_cmd(client_id, cmd, SubscriptionType::Pattern)
                    .await
            }
            "SUNSUBSCRIBE" => {
                self.handle_blocking_unsubscribe_from_cmd(client_id, cmd, SubscriptionType::Sharded)
                    .await
            }

            "GET_SUBSCRIPTIONS" => Ok(self.get_subscriptions_as_value(client_id).await),

            _ => {
                // Unknown PubSub command, return error for now
                Err(RedisError::from((
                    ErrorKind::ClientError,
                    "Unknown PubSub command",
                )))
            }
        };

        // If the main match resolved successfully or failed with a known error, return it
        if result.is_ok() {
            return result;
        }

        // Handle mock AUTH - set client's can_subscribe based on username
        if Self::is_mock_auth_command(cmd) {
            if let Some(username_bytes) = cmd.arg_idx(1) {
                let username = String::from_utf8_lossy(username_bytes).to_string();

                // Look up username permissions and apply to this client
                let can_subscribe = {
                    let perms = self.username_permissions.read().await;
                    perms.get(&username).copied().unwrap_or(true)
                };

                let mut clients = self.clients.write().await;
                if let Some(client) = clients.get_mut(client_id) {
                    client.can_subscribe = can_subscribe;
                    client.username = Some(username.clone());
                }
            }
            return Ok(Value::Okay);
        }

        if MockPubSubBroker::is_acl_pubsub_permission_command(cmd) {
            self.handle_acl_pubsub_permission(client_id, cmd).await;
            return Ok(Value::Okay);
        }

        result
    }

    /// Extract channels from a command (skip the command name)
    pub fn extract_channels_from_cmd(cmd: &Cmd) -> Vec<String> {
        cmd.args_iter()
            .skip(1) // Skip command name
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).to_string()),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    /// Extract channels and timeout from a command
    /// Returns (channels, timeout_ms) where timeout_ms defaults to 5000
    pub fn extract_channels_and_timeout(cmd: &Cmd) -> (Vec<String>, u64) {
        let mut channels = Vec::new();
        let mut timeout_ms = 5000u64; // Default 5 seconds

        let args: Vec<_> = cmd
            .args_iter()
            .skip(1) // Skip command name
            .filter_map(|arg| match arg {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).to_string()),
                redis::Arg::Cursor => None,
            })
            .collect();

        if args.is_empty() {
            return (channels, timeout_ms);
        }

        // Try to parse last arg as timeout
        if let Some(last_arg) = args.last() {
            if let Ok(timeout) = last_arg.parse::<u64>() {
                // Last arg is timeout, rest are channels
                timeout_ms = timeout;
                channels = args[..args.len() - 1].to_vec();
            } else {
                // Last arg is not a number, all are channels
                channels = args;
            }
        } else {
            channels = args;
        }

        (channels, timeout_ms)
    }

    /// Convert subscription map to Redis Value
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

    /// Handle lazy subscribe from a command
    pub async fn handle_lazy_subscribe_from_cmd(
        &self,
        client_id: &str,
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

        self.subscribe_lazy(client_id, channels, sub_type).await;
        Ok(Value::Nil)
    }

    /// Handle lazy unsubscribe from a command
    pub async fn handle_lazy_unsubscribe_from_cmd(
        &self,
        client_id: &str,
        cmd: &Cmd,
        sub_type: SubscriptionType,
    ) -> RedisResult<Value> {
        let channels = Self::extract_channels_from_cmd(cmd);
        let channels = if channels.is_empty() {
            None
        } else {
            Some(channels)
        };

        self.unsubscribe_lazy(client_id, channels, sub_type).await;
        Ok(Value::Nil)
    }

    /// Handle blocking subscribe from a command
    pub async fn handle_blocking_subscribe_from_cmd(
        &self,
        client_id: &str,
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

        self.subscribe_blocking(client_id, channels, sub_type, timeout_ms)
            .await
            .map_err(|e| RedisError::from((ErrorKind::IoError, "Subscription timeout", e)))?;

        Ok(Value::Nil)
    }

    /// Handle blocking unsubscribe from a command
    pub async fn handle_blocking_unsubscribe_from_cmd(
        &self,
        client_id: &str,
        cmd: &Cmd,
        sub_type: SubscriptionType,
    ) -> RedisResult<Value> {
        let (channels, timeout_ms) = Self::extract_channels_and_timeout(cmd);
        let channels = if channels.is_empty() {
            None
        } else {
            Some(channels)
        };

        self.unsubscribe_blocking(client_id, channels, sub_type, timeout_ms)
            .await
            .map_err(|e| RedisError::from((ErrorKind::IoError, "Unsubscription timeout", e)))?;

        Ok(Value::Nil)
    }

    /// Handle publish command
    pub async fn handle_publish(&self, cmd: &Cmd, is_sharded: bool) -> RedisResult<Value> {
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

    /// Get subscription state and convert to Redis Value
    pub async fn get_subscriptions_as_value(&self, client_id: &str) -> Value {
        if let Some((desired, actual)) = self.get_subscriptions(client_id).await {
            let result = vec![
                Value::BulkString(b"desired".to_vec()),
                Self::convert_sub_map_to_value(desired),
                Value::BulkString(b"actual".to_vec()),
                Self::convert_sub_map_to_value(actual),
            ];

            Value::Array(result)
        } else {
            // Client not registered - return empty state
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

    pub async fn subscribe_lazy(
        &self,
        client_id: &str,
        channels: Vec<String>,
        sub_type: SubscriptionType,
    ) {
        let clients_arc = Arc::clone(&self.clients);
        let delay = *self.max_application_delay_ms.read().await;

        // Update desired state immediately
        {
            let mut clients = clients_arc.write().await;
            if let Some(client) = clients.get_mut(client_id) {
                let set = match sub_type {
                    SubscriptionType::Exact => &mut client.desired_channels,
                    SubscriptionType::Pattern => &mut client.desired_patterns,
                    SubscriptionType::Sharded => &mut client.desired_sharded,
                };
                for channel in &channels {
                    set.insert(channel.clone());
                }
            }
        }

        // Schedule IMMEDIATE reconciliation attempt after delay
        let client_id = client_id.to_string();

        tokio::spawn(async move {
            if delay > 0 {
                sleep(Duration::from_millis(delay)).await;
            }

            // Read per-client can_subscribe
            let can_subscribe = {
                let clients = clients_arc.read().await;
                clients
                    .get(&client_id)
                    .map(|c| c.can_subscribe)
                    .unwrap_or(true)
            };

            Self::reconcile_client_static(&clients_arc, &client_id, can_subscribe).await;
        });
    }

    /// Subscribe with blocking (waits for actual state to match)
    pub async fn subscribe_blocking(
        &self,
        client_id: &str,
        channels: Vec<String>,
        sub_type: SubscriptionType,
        timeout_ms: u64,
    ) -> Result<(), String> {
        // First do lazy subscribe
        self.subscribe_lazy(client_id, channels.clone(), sub_type.clone())
            .await;

        let timeout_ms = if timeout_ms == 0 { 5000 } else { timeout_ms };

        // Wait for actual state to match
        let start = std::time::Instant::now();
        loop {
            {
                let clients = self.clients.read().await;
                if let Some(client) = clients.get(client_id) {
                    let actual_set = match sub_type {
                        SubscriptionType::Exact => &client.actual_channels,
                        SubscriptionType::Pattern => &client.actual_patterns,
                        SubscriptionType::Sharded => &client.actual_sharded,
                    };

                    if channels.iter().all(|ch| actual_set.contains(ch)) {
                        return Ok(());
                    }
                }
            }

            if timeout_ms > 0 && start.elapsed().as_millis() as u64 >= timeout_ms {
                return Err(format!("Timeout waiting for subscription to {channels:?}",));
            }

            sleep(Duration::from_millis(10)).await;
        }
    }

    pub async fn unsubscribe_lazy(
        &self,
        client_id: &str,
        channels: Option<Vec<String>>,
        sub_type: SubscriptionType,
    ) {
        let clients_arc = Arc::clone(&self.clients);
        let delay = *self.max_application_delay_ms.read().await;

        // Update desired state immediately
        {
            let mut clients = clients_arc.write().await;
            if let Some(client) = clients.get_mut(client_id) {
                let set = match sub_type {
                    SubscriptionType::Exact => &mut client.desired_channels,
                    SubscriptionType::Pattern => &mut client.desired_patterns,
                    SubscriptionType::Sharded => &mut client.desired_sharded,
                };

                if let Some(channels) = &channels {
                    for channel in channels {
                        set.remove(channel);
                    }
                } else {
                    set.clear();
                }
            }
        }

        // Schedule IMMEDIATE reconciliation attempt after delay
        let client_id = client_id.to_string();

        tokio::spawn(async move {
            if delay > 0 {
                sleep(Duration::from_millis(delay)).await;
            }

            // Read per-client can_subscribe (though unsubscribe always succeeds)
            let can_subscribe = {
                let clients = clients_arc.read().await;
                clients
                    .get(&client_id)
                    .map(|c| c.can_subscribe)
                    .unwrap_or(true)
            };

            Self::reconcile_client_static(&clients_arc, &client_id, can_subscribe).await;
        });
    }

    /// Unsubscribe with blocking
    pub async fn unsubscribe_blocking(
        &self,
        client_id: &str,
        channels: Option<Vec<String>>,
        sub_type: SubscriptionType,
        timeout_ms: u64,
    ) -> Result<(), String> {
        self.unsubscribe_lazy(client_id, channels.clone(), sub_type.clone())
            .await;

        let timeout_ms = if timeout_ms == 0 { 5000 } else { timeout_ms };

        let start = std::time::Instant::now();
        loop {
            {
                let clients = self.clients.read().await;
                if let Some(client) = clients.get(client_id) {
                    let actual_set = match sub_type {
                        SubscriptionType::Exact => &client.actual_channels,
                        SubscriptionType::Pattern => &client.actual_patterns,
                        SubscriptionType::Sharded => &client.actual_sharded,
                    };

                    let is_removed = if let Some(ref channels) = channels {
                        channels.iter().all(|ch| !actual_set.contains(ch))
                    } else {
                        actual_set.is_empty()
                    };

                    if is_removed {
                        return Ok(());
                    }
                }
            }

            if timeout_ms > 0 && start.elapsed().as_millis() as u64 >= timeout_ms {
                return Err("Timeout waiting for unsubscribe".to_string());
            }

            sleep(Duration::from_millis(10)).await;
        }
    }

    /// Publish a message to a channel
    pub async fn publish(&self, channel: &str, message: &[u8], sharded: bool) -> i64 {
        let clients = self.clients.read().await;
        let mut recipient_count = 0;

        for (client_id, client_sub) in clients.iter() {
            if sharded {
                if client_sub.actual_sharded.contains(channel) {
                    let push_info = create_push_info(channel, message, None, sharded);
                    if let Err(e) = client_sub.push_sender.send(push_info) {
                        log_warn(
                            "mock_pubsub",
                            format!("Failed to send to client {client_id}: {e}"),
                        );
                    } else {
                        recipient_count += 1;
                    }
                }
            } else {
                // Send message for exact channel match
                if client_sub.actual_channels.contains(channel) {
                    let push_info = create_push_info(channel, message, None, false);
                    if let Err(e) = client_sub.push_sender.send(push_info) {
                        log_warn(
                            "mock_pubsub",
                            format!("Failed to send to client {client_id} (exact): {e}"),
                        );
                    } else {
                        recipient_count += 1;
                    }
                }

                // Send separate message for each pattern match
                for pat in &client_sub.actual_patterns {
                    if glob_match(pat, channel) {
                        let push_info = create_push_info(channel, message, Some(pat), false);
                        if let Err(e) = client_sub.push_sender.send(push_info) {
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

    /// Get subscription state for a client
    pub async fn get_subscriptions(
        &self,
        client_id: &str,
    ) -> Option<(
        HashMap<String, HashSet<String>>,
        HashMap<String, HashSet<String>>,
    )> {
        let clients = self.clients.read().await;
        clients.get(client_id).map(|client| {
            let mut desired = HashMap::new();
            desired.insert("Exact".to_string(), client.desired_channels.clone());
            desired.insert("Pattern".to_string(), client.desired_patterns.clone());
            if client.is_cluster {
                desired.insert("Sharded".to_string(), client.desired_sharded.clone());
            }

            let mut actual = HashMap::new();
            actual.insert("Exact".to_string(), client.actual_channels.clone());
            actual.insert("Pattern".to_string(), client.actual_patterns.clone());
            if client.is_cluster {
                actual.insert("Sharded".to_string(), client.actual_sharded.clone());
            }

            (desired, actual)
        })
    }

    /// Check if desired and actual subscription states match and record metrics
    async fn check_and_record_sync_state(&self, client_id: &str) {
        let clients = self.clients.read().await;
        if let Some(client) = clients.get(client_id) {
            let channels_match = client.desired_channels == client.actual_channels;
            let patterns_match = client.desired_patterns == client.actual_patterns;
            let sharded_match = client.desired_sharded == client.actual_sharded;

            let is_synced = channels_match && patterns_match && sharded_match;

            if is_synced {
                // Record that we're in sync
                let _ = GlideOpenTelemetry::update_subscription_last_sync_timestamp();
                log_debug(
                    "mock_pubsub",
                    format!("Client {client_id} subscriptions in sync"),
                );
            } else {
                // Record that we're out of sync
                let _ = GlideOpenTelemetry::record_subscription_out_of_sync();
                log_debug(
                    "mock_pubsub",
                    format!(
                        "Client {} subscriptions out of sync - desired channels: {:?}, actual: {:?}, desired patterns: {:?}, actual: {:?}, desired sharded: {:?}, actual: {:?}",
                        client_id,
                        client.desired_channels,
                        client.actual_channels,
                        client.desired_patterns,
                        client.actual_patterns,
                        client.desired_sharded,
                        client.actual_sharded
                    ),
                );
            }
        }
    }

    pub fn is_acl_pubsub_permission_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();

        // Check if it's "ACL SETUSER"
        if command_name != b"ACL SETUSER" {
            return false;
        }

        // Check if the username (arg 2) starts with "mock_test_user_"
        if let Some(username_bytes) = cmd.arg_idx(2) {
            let username = String::from_utf8_lossy(username_bytes);
            if !username.starts_with("mock_test_user_") {
                return false;
            }
        } else {
            return false;
        }

        // Now check if it has pubsub permission patterns
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

    /// Check if this is an AUTH command for a mock test user
    pub fn is_mock_auth_command(cmd: &Cmd) -> bool {
        let command_name = cmd.command().unwrap_or_default();
        if command_name != b"AUTH" {
            return false;
        }

        let arg_count = cmd.args_iter().count();

        if arg_count >= 2 {
            // AUTH username password - check username at arg_idx(1)
            if let Some(username_bytes) = cmd.arg_idx(1) {
                let username = String::from_utf8_lossy(username_bytes);
                return username.starts_with("mock_test_user_");
            }
        }
        false
    }

    /// Handle ACL command that affects PubSub permissions (mock only)
    pub async fn handle_acl_pubsub_permission(&self, _client_id: &str, cmd: &Cmd) {
        // Extract username from ACL SETUSER command
        let username = if let Some(username_bytes) = cmd.arg_idx(2) {
            String::from_utf8_lossy(username_bytes).to_string()
        } else {
            return;
        };

        // Parse ACL command to determine if we're blocking or allowing pubsub
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

        // Store permission by username
        {
            let mut perms = self.username_permissions.write().await;
            perms.insert(username.clone(), can_subscribe);
        }

        // Update all clients that authenticated with this username
        {
            let mut clients = self.clients.write().await;
            for (client_id, client) in clients.iter_mut() {
                if client.username.as_ref() == Some(&username) {
                    client.can_subscribe = can_subscribe;
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
}

/// Simple glob pattern matching (supports * wildcard)
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
            // Must match at start
            if !text.starts_with(part) {
                return false;
            }
            text_idx = part.len();
        } else if i == pattern_parts.len() - 1 {
            // Must match at end
            return text[text_idx..].ends_with(part);
        } else {
            // Must match somewhere in middle
            if let Some(pos) = text[text_idx..].find(part) {
                text_idx += pos + part.len();
            } else {
                return false;
            }
        }
    }

    true
}

/// Create a PushInfo for message delivery
fn create_push_info(
    channel: &str,
    message: &[u8],
    pattern: Option<&str>,
    sharded: bool,
) -> PushInfo {
    let kind = if sharded {
        PushKind::Message // Could be extended to SMessage if redis-rs supports it
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
