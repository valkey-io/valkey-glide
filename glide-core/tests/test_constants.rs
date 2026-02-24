// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Shared test constants for Valkey GLIDE Rust core tests.

/// Supported test server hostnames.
/// TLS hostname is included in TLS certificate SAN.
pub const HOSTNAME_TLS: &str = "valkey.glide.test.tls.com";
pub const HOSTNAME_NO_TLS: &str = "valkey.glide.test.no_tls.com";
