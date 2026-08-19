// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Client configuration.
//!
//! Mirrors the Python `glide_shared.config` module. [`GlideClientConfiguration`]
//! (standalone) and [`GlideClusterClientConfiguration`] (cluster) are builder
//! structs that lower into a `glide_core::client::ConnectionRequest`.
//!
//! Layout:
//! - `common` — types shared by both configurations ([`NodeAddress`],
//!   [`TlsConfig`], [`ServerCredentials`], …) plus the shared builder-setter
//!   macro and request-lowering helpers.
//! - `standalone` — [`GlideClientConfiguration`].
//! - `cluster` — [`GlideClusterClientConfiguration`].

mod cluster;
mod common;
mod standalone;

pub use cluster::GlideClusterClientConfiguration;
pub use common::{
    BackoffStrategy, ClientIdentity, IamAuthConfig, NodeAddress, PeriodicChecks, ProtocolVersion,
    PubSubChannelMode, PubSubSubscriptions, ReadFrom, ServerCredentials, ServiceType, TlsConfig,
};
pub use standalone::GlideClientConfiguration;

pub use glide_core::client::NodeDiscoveryMode;

#[cfg(test)]
mod tests;
