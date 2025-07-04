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
    push_sender: Option<tokio::sync::mpsc::UnboundedSender<PushInfo>>
}

impl Client {
    pub async fn new(
        request: ConnectionRequest,
        push_sender: Option<tokio::sync::mpsc::UnboundedSender<PushInfo>>,
    ) -> Result<Self, ConnectionError> {
        Ok(Client {
            push_sender
        })
    }

    pub fn send_pipeline<'a>(
        &'a mut self,
        pipeline: &'a Pipeline,
        routing: Option<RoutingInfo>,
        raise_on_error: bool,
        pipeline_timeout: Option<u32>,
        pipeline_retry_strategy: PipelineRetryStrategy,
    ) -> redis::RedisFuture<'a, Value> {
        todo!()
    }

    pub fn send_transaction<'a>(
        &'a mut self,
        pipeline: &'a Pipeline,
        routing: Option<RoutingInfo>,
        transaction_timeout: Option<u32>,
        raise_on_error: bool,
    ) -> redis::RedisFuture<'a, Value> {
        todo!()
    }

    pub async fn invoke_script<'a>(
        &'a mut self,
        hash: &'a str,
        keys: &Vec<&[u8]>,
        args: &Vec<&[u8]>,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisResult<Value> {
        todo!()
    }

    pub async fn update_connection_password(
        &mut self,
        password: Option<String>,
        immediate_auth: bool,
    ) -> redis::RedisResult<Value> {
        todo!()
    }

    pub fn send_command<'a>(
        &'a mut self,
        cmd: &'a Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        todo!()
    }

    pub async fn cluster_scan<'a>(
        &'a mut self,
        scan_state_cursor: &'a ScanStateRC,
        cluster_scan_args: ClusterScanArgs,
    ) -> RedisResult<Value> {
        todo!()
    }
}
