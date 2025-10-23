// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub use glide_core::client::{GlideRt, get_or_init_runtime};

use crate::connection_request::ConnectionRequest;
use redis::{Pipeline, PipelineRetryStrategy, ScanStateRC, Cmd, PushInfo, Value, ClusterScanArgs, RoutingInfo, RedisResult};

pub struct ConnectionError;

use std::fmt;
impl fmt::Display for ConnectionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}

#[derive(Clone)]
pub struct Client {
    _push_sender: Option<tokio::sync::mpsc::UnboundedSender<PushInfo>>
}

impl Client {
    pub async fn new(
        _request: ConnectionRequest,
        push_sender: Option<tokio::sync::mpsc::UnboundedSender<PushInfo>>,
    ) -> Result<Self, ConnectionError> {
        Ok(Client {
            _push_sender: push_sender
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
}
