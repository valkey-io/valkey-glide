// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Common constants for testing.
#![allow(dead_code)]

// Host names and addresses for tests.
// See 'cluster_manager.py' for details.
pub const HOSTNAME_TLS: &str = "valkey.glide.test.tls.com";
pub const HOSTNAME_NO_TLS: &str = "valkey.glide.test.no_tls.com";
pub const IP_ADDRESS_V4: &str = "127.0.0.1";
pub const IP_ADDRESS_V6: &str = "::1";

// IAM authentication test constants
pub const IAM_USERNAME: &str = "default";
pub const IAM_TEST_CLUSTER_NAME: &str = "test-cluster";
pub const IAM_TEST_REGION_US_EAST_1: &str = "us-east-1";
