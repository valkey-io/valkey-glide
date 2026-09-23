// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! What happens on a host with no libfabric.

#![cfg(feature = "libfabric")]

use glide_rdma::{FabricConfig, Provider, RdmaError, RdmaFabric, discover_domains, ensure_loaded};

#[test]
fn a_missing_libfabric_is_a_readable_error_rather_than_a_dead_process() {
    // Reaching this line at all is half the result. Under a hard link the
    // binary would not have started.
    let nowhere = "/nonexistent/glide-rdma-spike/libfabric.so.1";

    // SAFETY: set_var races any thread reading the environment, and the loader
    // below reads it. Keeping every case in a single test is what makes this
    // safe: cargo runs tests on parallel threads, so a second test here would
    // be writing this variable while this one's load() was reading it.
    unsafe { std::env::set_var("GLIDE_LIBFABRIC_PATH", nowhere) };

    let error = ensure_loaded().expect_err("a path that does not exist cannot load");

    let RdmaError::LibfabricUnavailable { detail } = error else {
        panic!("expected LibfabricUnavailable, got {error:?}");
    };

    assert!(
        detail.contains(nowhere),
        "the error should name the path it tried, got: {detail}"
    );

    // The two public ways in have to say the same thing. The failed load above
    // is remembered for the life of the process, so no second set_var is needed.
    let config = FabricConfig::new(Provider::Tcp);

    let error = RdmaFabric::open(&config).expect_err("no libfabric, no fabric");
    assert!(
        matches!(error, RdmaError::LibfabricUnavailable { .. }),
        "opening a fabric should say libfabric is unavailable, got {error:?}"
    );

    let error = discover_domains(&config).expect_err("no libfabric, no domains");
    assert!(
        matches!(error, RdmaError::LibfabricUnavailable { .. }),
        "discovery should say libfabric is unavailable, got {error:?}"
    );
}
