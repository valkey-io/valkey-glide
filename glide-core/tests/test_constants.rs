// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Shared test constants for Valkey GLIDE Rust core tests.
#![allow(dead_code)]

// Host names and addresses for tests.
// See 'cluster_manager.py' for details.
pub const HOSTNAME_TLS: &str = "valkey.glide.test.tls.com";
pub const HOSTNAME_NO_TLS: &str = "valkey.glide.test.no_tls.com";
pub const HOST_IPV4: &str = "127.0.0.1";
pub const HOST_IPV6: &str = "::1";
