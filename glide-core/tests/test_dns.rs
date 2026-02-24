// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! DNS resolution tests.
//!
//! These tests require host file entries and environment variable:
//!
//! Host file entries:
//! - 127.0.0.1 valkey.glide.test.tls.com
//! - 127.0.0.1 valkey.glide.test.no_tls.com
//! - ::1 valkey.glide.test.tls.com
//! - ::1 valkey.glide.test.no_tls.com
//!
//! Environment variable:
//! - VALKEY_GLIDE_DNS_TESTS_ENABLED=1

mod test_constants;
mod utilities;

#[cfg(test)]
mod dns_tests {
    use super::*;
    use glide_core::{
        client::{Client, StandaloneClient},
        connection_request::TlsMode,
    };
    use redis::Value;
    use rstest::rstest;
    use std::env;
    use test_constants::*;
    use utilities::*;
    use utilities::cluster::SHORT_CLUSTER_TEST_TIMEOUT;

    /// Check if DNS tests are enabled via environment variable
    fn dns_tests_enabled() -> bool {
        env::var("VALKEY_GLIDE_DNS_TESTS_ENABLED").is_ok()
    }

    // Test functions will be added in Step 6
}
