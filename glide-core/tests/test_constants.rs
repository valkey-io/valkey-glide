// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Shared test constants for Valkey GLIDE Rust core tests.

/// Test server IP addresses.
pub const HOST_IPV4: &str = "127.0.0.1";
pub const HOST_IPV6: &str = "::1";

/// Test server hostnames.
pub const HOSTNAME_TLS: &str = "valkey.glide.test.tls.com"; // Included in TLS certificate SAN.
pub const HOSTNAME_NO_TLS: &str = "valkey.glide.test.no_tls.com"; // Not included in TLS certificate SAN.

