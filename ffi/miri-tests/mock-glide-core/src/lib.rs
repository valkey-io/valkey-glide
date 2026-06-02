// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

pub mod client;
pub mod cluster_scan_container;
pub mod command_request;
pub mod compression;
pub mod connection_request;
pub mod errors;
pub mod otel_db_semantics;
pub mod request_type;
pub mod scripts_container;

pub use client::*;
pub use cluster_scan_container::*;
pub use command_request::*;
pub use compression::*;
pub use errors::*;
pub use request_type::*;
pub use scripts_container::*;

// Selectively re-export from connection_request, excluding ConnectionRequest
// (which is the protobuf type). The crate-root `ConnectionRequest` is the internal
// type defined below, matching the real glide-core's `pub use client::ConnectionRequest`.
pub use connection_request::{
    AuthenticationInfo, CompressionBackend, CompressionConfig, ConnectionRetryStrategy,
    IamCredentials, NodeDiscoveryMode, PeriodicChecksDisabled, PeriodicChecksManualInterval,
    ProtocolVersion, PubSubChannelType, PubSubChannelsOrPatterns, PubSubSubscriptions, ReadFrom,
    ServiceType,
};

pub use telemetrylib::*;

pub const DEFAULT_FLUSH_SIGNAL_INTERVAL_MS: u32 = 0;

// Mock address_resolver_registry module — no-op implementations for miri tests.
pub mod address_resolver_registry {
    use redis::AddressResolver;
    use std::sync::Arc;

    pub fn register(
        _key: String,
        _resolver: Arc<dyn AddressResolver>,
    ) -> Option<Arc<dyn AddressResolver>> {
        None
    }

    pub fn remove(_key: &str) -> Option<Arc<dyn AddressResolver>> {
        None
    }
}

/// Internal ConnectionRequest type that mirrors the real glide-core's `client::ConnectionRequest`.
/// The FFI code imports this as `use glide_core::ConnectionRequest`.
/// It has the `address_resolver` field that the FFI code sets after converting from protobuf.
#[derive(Default)]
pub struct ConnectionRequest {
    pub address_resolver: Option<std::sync::Arc<dyn redis::AddressResolver>>,
}

impl From<connection_request::ConnectionRequest> for ConnectionRequest {
    fn from(_value: connection_request::ConnectionRequest) -> Self {
        ConnectionRequest {
            address_resolver: None,
        }
    }
}
