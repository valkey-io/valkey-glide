// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub use redis::{ErrorKind, ObjectType, PushKind, RedisError, RedisFuture, RedisResult, Value};
use telemetrylib::GlideSpan;

pub mod cluster_routing;
pub mod cluster_topology;

pub use cluster_routing::*;
pub use cluster_topology::*;

pub use redis::ToRedisArgs;

pub struct Cmd {
    command_bytes: Vec<u8>,
}

impl Default for Cmd {
    fn default() -> Self {
        Self {
            command_bytes: b"GET".to_vec(),
        }
    }
}

impl Routable for Cmd {}

impl Cmd {
    pub fn arg<T: ToRedisArgs>(&mut self, _arg: T) -> &mut Cmd {
        self
    }

    pub fn set_span(&mut self, _span: Option<GlideSpan>) -> &mut Cmd {
        self
    }

    pub fn span(&self) -> Option<GlideSpan> {
        None
    }

    pub fn command(&self) -> Option<Vec<u8>> {
        Some(self.command_bytes.clone())
    }
}

pub struct Pipeline;

impl Pipeline {
    pub fn with_capacity(_capacity: usize) -> Self {
        Pipeline
    }

    pub fn atomic(&mut self) -> &mut Self {
        self
    }

    pub fn is_atomic(&self) -> bool {
        true
    }

    pub fn add_command(&mut self, _cmd: Cmd) -> &mut Self {
        self
    }

    pub fn set_pipeline_span(&mut self, _span: Option<GlideSpan>) {}

    pub fn span(&self) -> Option<GlideSpan> {
        Some(GlideSpan)
    }
}

pub struct ScanStateRC;

impl ScanStateRC {
    pub fn new() -> Self {
        ScanStateRC
    }
}

pub struct ClusterScanArgs;

impl ClusterScanArgs {
    pub fn builder() -> ClusterScanArgsBuilder {
        ClusterScanArgsBuilder::default()
    }
}

pub struct ClusterScanArgsBuilder;

impl Default for ClusterScanArgsBuilder {
    fn default() -> Self {
        ClusterScanArgsBuilder
    }
}

impl ClusterScanArgsBuilder {
    pub fn build(self) -> ClusterScanArgs {
        ClusterScanArgs
    }

    pub fn with_count(self, _count: u32) -> Self {
        self
    }

    pub fn with_object_type(self, _object_type: ObjectType) -> Self {
        self
    }

    pub fn with_match_pattern<T: Into<Vec<u8>>>(self, _pattern: T) -> Self {
        self
    }

    pub fn allow_non_covered_slots(self, _allow: bool) -> Self {
        self
    }

}

pub struct PushInfo {
    pub kind: redis::PushKind,
    pub data: Vec<redis::Value>,
}

pub struct PipelineRetryStrategy;

impl PipelineRetryStrategy {
    pub fn new(_retry_server_error: bool, _retry_connection_error: bool) -> Self {
        PipelineRetryStrategy
    }
}
