// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Re-export everything from the real protobuf connection_request module.
// The FFI code accesses protobuf types via `glide_core::connection_request::*`.
pub use glide_core::connection_request::{
    AuthenticationInfo,
    CompressionBackend,
    CompressionConfig,
    ConnectionRequest,
    ConnectionRetryStrategy,
    IamCredentials,
    NodeAddress,
    NodeDiscoveryMode,
    PeriodicChecksDisabled,
    PeriodicChecksManualInterval,
    ProtocolVersion,
    PubSubChannelType,
    PubSubChannelsOrPatterns,
    PubSubSubscriptions,
    ReadFrom,
    ServiceType,
    TlsMode,
};
