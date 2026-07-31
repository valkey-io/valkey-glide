// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Integration tests for the timeout watchdog.
//!
//! These tests exercise the watchdog in scenarios that simulate real client
//! behavior: concurrent registrations, timing accuracy, and interaction
//! with the global singleton. The watchdog is now a pure timer — diagnostic
//! event construction is the consumer's responsibility.

use glide_core::timeout_watchdog::{LatencyTracker, TimeoutWatchdog, pending_count};
use std::sync::Arc;
use std::time::{Duration, Instant};

// ─── Global Singleton Tests ──────────────────────────────────────────────────

#[tokio::test]
async fn global_watchdog_is_singleton() {
    let w1 = TimeoutWatchdog::global();
    let w2 = TimeoutWatchdog::global();
    // Same pointer — only one instance
    assert!(std::ptr::eq(w1, w2));
}

#[tokio::test]
async fn global_watchdog_fires_timeout() {
    let watchdog = TimeoutWatchdog::global();
    let rx = watchdog.register(Duration::from_millis(40), Instant::now());
    let result = rx.await;
    assert!(result.is_ok());
}

// ─── Simulated Command Lifecycle ─────────────────────────────────────────────

/// Simulates a command that completes before the timeout — the watchdog should
/// not produce a signal (receiver is dropped).
#[tokio::test]
async fn command_completes_before_timeout() {
    let watchdog = TimeoutWatchdog::start();
    let rx = watchdog.register(Duration::from_millis(200), Instant::now());

    // Simulate command completing after 50ms
    tokio::time::sleep(Duration::from_millis(50)).await;
    drop(rx); // Command done — drop the receiver

    // Wait past the deadline — nothing should panic or leak
    tokio::time::sleep(Duration::from_millis(200)).await;
}

/// Simulates a burst of commands where some complete and some timeout.
#[tokio::test]
async fn mixed_completion_and_timeout() {
    let watchdog = TimeoutWatchdog::start();

    // Register 5 commands: first 3 will "complete", last 2 will timeout
    let mut timeout_receivers = Vec::new();
    for i in 0..5 {
        let rx = watchdog.register(Duration::from_millis(100), Instant::now());
        if i < 3 {
            // Simulate completion
            drop(rx);
        } else {
            timeout_receivers.push(rx);
        }
    }

    // The 2 remaining should fire
    for rx in timeout_receivers {
        let result = rx.await;
        assert!(result.is_ok());
    }
}

// ─── Pending Count Tests ─────────────────────────────────────────────────────

#[tokio::test]
async fn pending_count_tracks_registrations() {
    let watchdog = TimeoutWatchdog::global();

    let rx1 = watchdog.register(Duration::from_millis(500), Instant::now());
    let rx2 = watchdog.register(Duration::from_millis(500), Instant::now());
    let rx3 = watchdog.register(Duration::from_millis(500), Instant::now());

    // The global watchdog thread publishes the count asynchronously after draining.
    // Give it time to process and publish.
    tokio::time::sleep(Duration::from_millis(100)).await;
    let during = pending_count();
    assert!(during >= 3, "pending should be at least 3: got {during}");

    // Wait for all to fire
    rx1.await.unwrap();
    rx2.await.unwrap();
    rx3.await.unwrap();
}

// ─── Timing Accuracy ─────────────────────────────────────────────────────────

/// Verify that the timeout fires at approximately the right time.
#[tokio::test]
async fn actual_elapsed_accuracy() {
    let watchdog = TimeoutWatchdog::start();
    let start = Instant::now();
    let rx = watchdog.register(Duration::from_millis(75), start);

    rx.await.unwrap();
    let wall_elapsed = start.elapsed();

    // Should fire within a reasonable window of the configured timeout
    assert!(
        wall_elapsed >= Duration::from_millis(50),
        "Fired too early: {:?}",
        wall_elapsed
    );
    assert!(
        wall_elapsed < Duration::from_millis(500),
        "Fired too late: {:?}",
        wall_elapsed
    );
}

// ─── Latency Tracker Integration ─────────────────────────────────────────────

/// Latency tracker shared across multiple commands accumulates data correctly.
#[tokio::test]
async fn shared_tracker_across_commands() {
    let tracker = Arc::new(LatencyTracker::new(1024));

    // Simulate 200 successful commands with varying latency
    for i in 0..200 {
        let latency = Duration::from_millis(5 + (i % 20));
        tracker.record(latency);
    }

    let p99 = tracker.p99().unwrap();
    // Latencies range from 5ms to 24ms, p99 should be near the high end
    assert!(p99 >= Duration::from_millis(20));
    assert!(p99 <= Duration::from_millis(25));
}

/// Latency tracker under concurrent writes doesn't corrupt data.
#[tokio::test]
async fn concurrent_latency_recording() {
    let tracker = Arc::new(LatencyTracker::new(1024));

    let mut handles = Vec::new();
    for t in 0..10 {
        let tr = tracker.clone();
        handles.push(tokio::spawn(async move {
            for i in 0..100 {
                tr.record(Duration::from_micros(100 * (t * 100 + i)));
            }
        }));
    }
    for h in handles {
        h.await.unwrap();
    }

    // Should have data and not panic
    let p99 = tracker.p99();
    assert!(p99.is_some());
}

// ─── Stress / Reliability ────────────────────────────────────────────────────

/// Many short-lived registrations followed by a real timeout — watchdog stays healthy.
#[tokio::test]
async fn watchdog_survives_rapid_register_cancel_cycles() {
    let watchdog = TimeoutWatchdog::start();

    // Rapid register + cancel (simulates fast commands)
    for _ in 0..5000 {
        let rx = watchdog.register(Duration::from_secs(10), Instant::now());
        drop(rx);
    }

    // Now register one that should actually fire
    let rx = watchdog.register(Duration::from_millis(30), Instant::now());

    let result = tokio::time::timeout(Duration::from_millis(200), rx).await;
    assert!(result.is_ok());
    assert!(result.unwrap().is_ok());
}

/// Watchdog handles zero-duration timeout gracefully.
#[tokio::test]
async fn zero_duration_timeout_fires_immediately() {
    let watchdog = TimeoutWatchdog::start();
    let rx = watchdog.register(Duration::from_millis(0), Instant::now());

    let result = tokio::time::timeout(Duration::from_millis(100), rx).await;
    assert!(
        result.is_ok(),
        "Zero-duration timeout should fire immediately"
    );
}

/// Very long timeout doesn't block other shorter timeouts.
#[tokio::test]
async fn long_timeout_doesnt_block_short() {
    let watchdog = TimeoutWatchdog::start();

    // Register a 10-second timeout first
    let _long_rx = watchdog.register(Duration::from_secs(10), Instant::now());

    // Then a 50ms timeout — should fire on time
    let start = Instant::now();
    let short_rx = watchdog.register(Duration::from_millis(50), Instant::now());

    short_rx.await.unwrap();
    let elapsed = start.elapsed();
    assert!(
        elapsed < Duration::from_millis(500),
        "Short timeout blocked: {:?}",
        elapsed
    );
}

/// Concurrent register from multiple tasks all fire correctly.
#[tokio::test]
async fn concurrent_register_from_multiple_tasks() {
    let watchdog = TimeoutWatchdog::start();
    let mut handles = Vec::new();
    for _ in 0..10 {
        let w = watchdog.clone();
        handles.push(tokio::spawn(async move {
            let mut rxs = Vec::new();
            for _ in 0..100 {
                let rx = w.register(Duration::from_millis(50), Instant::now());
                rxs.push(rx);
            }
            for rx in rxs {
                let _ = rx.await;
            }
        }));
    }
    for h in handles {
        h.await.unwrap();
    }
}

// ─── Tokio Starvation ────────────────────────────────────────────────────────

/// Watchdog fires even when Tokio runtime is starved (dedicated OS thread).
#[tokio::test(flavor = "current_thread")]
async fn watchdog_fires_under_tokio_starvation() {
    let watchdog = TimeoutWatchdog::start();
    let rx = watchdog.register(Duration::from_millis(100), Instant::now());

    let blocker = tokio::spawn(async {
        let start = Instant::now();
        while start.elapsed() < Duration::from_secs(2) {
            tokio::task::yield_now().await;
            std::thread::sleep(Duration::from_millis(50));
        }
    });

    let result = tokio::time::timeout(Duration::from_secs(1), rx).await;
    assert!(
        result.is_ok(),
        "Watchdog should fire despite Tokio starvation"
    );
    blocker.abort();
}

// ─── RSS Diagnostic ──────────────────────────────────────────────────────────

#[tokio::test]
async fn get_rss_returns_nonzero_on_supported_platforms() {
    let rss = glide_core::timeout_watchdog::get_rss();
    if cfg!(any(target_os = "linux", target_os = "macos")) {
        let bytes = rss.expect("get_rss() should return Some on Linux/macOS");
        assert!(bytes > 0, "RSS should be non-zero, got {bytes}");
        // Sanity: a Rust test binary is at least a few MB
        assert!(bytes > 1_000_000, "RSS suspiciously low: {bytes} bytes");
    }
}

#[tokio::test]
async fn get_rss_caches_result() {
    // Two calls within 1s should return the same value (cached).
    let rss1 = glide_core::timeout_watchdog::get_rss();
    let rss2 = glide_core::timeout_watchdog::get_rss();
    assert_eq!(
        rss1, rss2,
        "consecutive get_rss() calls should return cached value"
    );
}
