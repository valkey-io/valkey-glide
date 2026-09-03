// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Shared integration-test harness.
//!
//! Provides:
//! * [`TestServer`] — boots an ephemeral standalone `valkey-server` on a free
//!   port and tears it down on drop, with RESP2/RESP3 client helpers.
//! * [`ClusterHarness`] — boots a real 3-primary cluster (one replica each) via
//!   `valkey-glide/utils/cluster_manager.py` and connects a
//!   [`glide::GlideClusterClient`]. Requires `python3` + `valkey-cli`/`redis-cli`
//!   on `PATH`; panics if the cluster cannot be started.
//! * The [`resp_test!`] macro — expands a single test body into two
//!   `#[tokio::test]`s, one per RESP protocol version (the ~2x multiplier;
//!   combined with standalone the effective coverage mirrors Python's
//!   RESP2/RESP3 parametrization).
//! * [`assert_request_error!`] — asserts a result is a server `RequestError`.

// Each integration-test crate compiles this harness separately and uses a
// different subset of it, so unused-item/import lints are expected noise here.
#![allow(dead_code)]
#![allow(unused_imports)]

mod cluster;
mod helpers;
mod macros;
mod pubsub;
mod server;
mod timeout;

pub use cluster::{ClusterHarness, is_transient_cluster_error};
pub use helpers::{command_exists, key, server_version, tkey, version_below};
pub use pubsub::{wait_for_numpat, wait_for_numsub};
pub use server::{TestServer, free_port, server_binary};
pub use timeout::{test_timeout, with_test_timeout};
