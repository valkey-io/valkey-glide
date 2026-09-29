// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Per-test wall-clock timeout guard.

use std::time::Duration;

/// Wall-clock budget for a single test. A wedged `await` — e.g. a pub/sub
/// receive that never arrives, or a cluster op that never returns — would
/// otherwise block the whole CI job indefinitely. This bounds every test the
/// way glide-core's `#[timeout(...)]` attribute does.
///
/// Overridable via `GLIDE_TEST_TIMEOUT_SECS` (default 120s — generous so it
/// never false-trips under `llvm-cov` instrumentation, yet still catches a
/// genuine hang, which is unbounded).
pub fn test_timeout() -> Duration {
    let secs = std::env::var("GLIDE_TEST_TIMEOUT_SECS")
        .ok()
        .and_then(|s| s.parse::<u64>().ok())
        .unwrap_or(120);
    Duration::from_secs(secs)
}

/// Run a test future under [`test_timeout`], panicking (failing the test) if it
/// does not complete in time — so a hang surfaces as a fast, clear failure
/// instead of a stuck CI job. Used automatically by [`resp_test!`],
/// [`matrix_test!`] and [`timed_tokio_test!`].
pub async fn with_test_timeout<F: std::future::Future>(fut: F) -> F::Output {
    let budget = test_timeout();
    match tokio::time::timeout(budget, fut).await {
        Ok(v) => v,
        Err(_) => panic!(
            "test exceeded {budget:?} wall-clock timeout \
             (set GLIDE_TEST_TIMEOUT_SECS to adjust)"
        ),
    }
}
