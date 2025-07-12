pub use redis::{RedisFuture, Value, ErrorKind, RedisError, RedisResult, ObjectType, PushKind};
use telemetrylib::GlideSpan;

pub mod cluster_routing;
pub mod cluster_topology;

pub use cluster_routing::*;
pub use cluster_topology::*;

pub use redis::ToRedisArgs;

pub struct Cmd;

impl Routable for Cmd {}

impl Cmd {
    pub fn arg<T: ToRedisArgs>(&mut self, arg: T) -> &mut Cmd {
        self
    }

    pub fn set_span(&mut self, span: Option<GlideSpan>) -> &mut Cmd {
        self
    }
}

pub struct Pipeline;

impl Pipeline {
    pub fn with_capacity(capacity: usize) -> Self {
        Pipeline
    }

    pub fn atomic(&mut self) -> &mut Self {
        self
    }

    pub fn is_atomic(&self) -> bool {
        true
    }

    pub fn add_command(&mut self, cmd: Cmd) -> &mut Self {
        self
    }

    pub fn set_pipeline_span(&mut self, span: Option<GlideSpan>) {}
    
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

    pub fn with_count(mut self, count: u32) -> Self {
        self
    }

    pub fn with_object_type(mut self, object_type: ObjectType) -> Self {
        self
    }

    pub fn with_match_pattern<T: Into<Vec<u8>>>(mut self, pattern: T) -> Self {
        self
    }
}

pub struct PushInfo {
    pub kind: redis::PushKind,
    pub data: Vec<redis::Value>
}

pub struct PipelineRetryStrategy;

impl PipelineRetryStrategy {
    pub fn new(retry_server_error: bool, retry_connection_error: bool) -> Self {
        PipelineRetryStrategy
    }
}

