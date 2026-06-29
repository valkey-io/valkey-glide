// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub use glide_core::client::{GlideRt, get_or_init_runtime};

/// Mirrors glide_core::client::NodeAddress (internal type, not protobuf).
pub struct NodeAddress {
    pub host: String,
    pub port: u16,
}

/// Mirrors glide_core::client::TlsMode (internal type).
#[derive(Clone, Copy, PartialEq)]
pub enum TlsMode {
    NoTls,
    SecureTls,
    InsecureTls,
}

use crate::ConnectionRequest;
use redis::{
    ClusterScanArgs, Cmd, Pipeline, PipelineRetryStrategy, PushInfo, RedisResult, RoutingInfo,
    ScanStateRC, Value,
};

pub struct ConnectionError;

/// Mock inflight tracker for Miri tests — no-op Drop.
pub struct MockInflightTracker;

use std::fmt;
impl fmt::Display for ConnectionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}

#[derive(Clone)]
pub struct Client {
    _push_sender: Option<tokio::sync::mpsc::UnboundedSender<PushInfo>>,
}

impl Client {
    pub async fn new(
        _request: ConnectionRequest,
        push_sender: Option<tokio::sync::mpsc::UnboundedSender<PushInfo>>,
    ) -> Result<Self, ConnectionError> {
        Ok(Client {
            _push_sender: push_sender,
        })
    }

    pub fn send_pipeline<'a>(
        &'a mut self,
        _pipeline: &'a Pipeline,
        _routing: Option<RoutingInfo>,
        _raise_on_error: bool,
        _pipeline_timeout: Option<u32>,
        _pipeline_retry_strategy: PipelineRetryStrategy,
    ) -> redis::RedisFuture<'a, Value> {
        todo!()
    }

    pub fn send_transaction<'a>(
        &'a mut self,
        _pipeline: &'a Pipeline,
        _routing: Option<RoutingInfo>,
        _transaction_timeout: Option<u32>,
        _raise_on_error: bool,
    ) -> redis::RedisFuture<'a, Value> {
        todo!()
    }

    pub async fn invoke_script<'a>(
        &'a mut self,
        _hash: &'a str,
        _keys: &Vec<&[u8]>,
        _args: &Vec<&[u8]>,
        _routing: Option<RoutingInfo>,
    ) -> redis::RedisResult<Value> {
        todo!()
    }

    pub async fn update_connection_password(
        &mut self,
        _password: Option<String>,
        _immediate_auth: bool,
    ) -> redis::RedisResult<Value> {
        todo!()
    }

    pub fn send_command<'a>(
        &'a mut self,
        _cmd: &'a Cmd,
        _routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        todo!()
    }

    pub async fn cluster_scan<'a>(
        &'a mut self,
        _scan_state_cursor: &'a ScanStateRC,
        _cluster_scan_args: ClusterScanArgs,
    ) -> RedisResult<Value> {
        todo!()
    }

    pub async fn refresh_iam_token(&mut self) -> RedisResult<()> {
        todo!()
    }

    /// Mock compression_manager method for Miri tests
    pub fn compression_manager(
        &self,
    ) -> Option<std::sync::Arc<crate::compression::CompressionManager>> {
        None
    }

    /// Mock reserve_inflight_request method for Miri tests
    pub fn reserve_inflight_request(&self) -> Option<MockInflightTracker> {
        Some(MockInflightTracker) // Always allow in mock
    }

    /// Mock cache_hit_rate method for Miri tests
    pub fn cache_hit_rate(&self) -> RedisResult<Value> {
        todo!()
    }

    /// Mock cache_miss_rate method for Miri tests
    pub fn cache_miss_rate(&self) -> RedisResult<Value> {
        todo!()
    }

    /// Mock cache_entry_count method for Miri tests
    pub fn cache_entry_count(&self) -> RedisResult<Value> {
        todo!()
    }

    /// Mock cache_evictions method for Miri tests
    pub fn cache_evictions(&self) -> RedisResult<Value> {
        todo!()
    }

    /// Mock cache_expirations method for Miri tests
    pub fn cache_expirations(&self) -> RedisResult<Value> {
        todo!()
    }

    /// Mock cache_total_lookups method for Miri tests
    pub fn cache_total_lookups(&self) -> RedisResult<Value> {
        todo!()
    }
}

// ─── MonitorClient stubs for miri-tests ──────────────────────────────────────

pub struct MonitorLine {
    pub timestamp: f64,
    pub db: i64,
    pub client_addr: String,
    pub command: String,
    pub args: Vec<String>,
}

pub type MonitorLineCallback = std::sync::Arc<dyn Fn(MonitorLine) + Send + Sync>;

pub struct MonitorClient;

impl MonitorClient {
    pub async fn new(
        _address: &NodeAddress,
        _redis_connection_info: redis::RedisConnectionInfo,
        _tls_mode: TlsMode,
        _on_line: MonitorLineCallback,
    ) -> redis::RedisResult<Self> {
        Ok(MonitorClient)
    }

    pub async fn stop_async(self) {}

    pub fn stop(&mut self) {}
}
