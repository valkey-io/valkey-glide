use glide_core::client::Client;
use redis::Cmd;
use std::time::{Duration, Instant};

/// Tests for timeout enforcement under various conditions.
///
/// Regression tests for: https://github.com/valkey-io/valkey-glide/issues/5284
///
/// The bug: Timeouts were applied AFTER connection acquisition, not including it.
/// Under high load, connection acquisition could take 70ms+, then the 30ms timeout
/// would start, resulting in total times of 100ms+.
///
/// The fix: Wrap `get_or_initialize_client()` in `run_with_timeout()` so the timeout
/// includes connection acquisition time.
#[cfg(test)]
mod timeout_tests {
    use super::*;

    /// Simple test: Command that exceeds timeout should timeout at configured duration.
    #[tokio::test]
    async fn test_timeout_enforced_on_slow_command() {
        let config = glide_core::client::ConnectionRequest {
            addresses: vec![glide_core::client::NodeAddress {
                host: "localhost".to_string(),
                port: 6379,
            }],
            request_timeout: Some(30),       // 30ms
            connection_timeout: Some(10000), // 10s for connection
            ..Default::default()
        };

        let mut client = match Client::new(config, None).await {
            Ok(c) => c,
            Err(_) => {
                eprintln!("Skipping test: Cannot connect to Valkey at localhost:6379");
                return;
            }
        };

        // Command that takes 100ms should timeout at 30ms
        let mut slow_cmd = Cmd::new();
        slow_cmd.arg("DEBUG").arg("SLEEP").arg("0.1"); // 100ms

        let start = Instant::now();
        let result = client.send_command(&mut slow_cmd, None).await;
        let elapsed = start.elapsed();

        assert!(result.is_err(), "Command should timeout");
        assert!(
            elapsed < Duration::from_millis(70),
            "Timeout should occur at ~30ms, but took {:?}",
            elapsed
        );
    }

    /// Test multiple concurrent clients all timing out correctly.
    #[tokio::test]
    async fn test_multiple_clients_timeout_correctly() {
        const NUM_CLIENTS: usize = 5;
        let mut handles = vec![];

        for i in 0..NUM_CLIENTS {
            let handle = tokio::spawn(async move {
                let config = glide_core::client::ConnectionRequest {
                    addresses: vec![glide_core::client::NodeAddress {
                        host: "localhost".to_string(),
                        port: 6379,
                    }],
                    request_timeout: Some(30),
                    connection_timeout: Some(10000), // 10s for connection
                    ..Default::default()
                };

                let mut client = match Client::new(config, None).await {
                    Ok(c) => c,
                    Err(_) => return (i, true, Duration::from_millis(0)), // Skip if can't connect
                };

                let mut cmd = Cmd::new();
                cmd.arg("DEBUG").arg("SLEEP").arg("0.1"); // 100ms

                let start = Instant::now();
                let result = client.send_command(&mut cmd, None).await;
                let elapsed = start.elapsed();

                (i, result.is_ok(), elapsed)
            });

            handles.push(handle);
        }

        let results: Vec<_> = futures::future::join_all(handles)
            .await
            .into_iter()
            .map(|r| r.unwrap())
            .collect();

        // All should timeout at ~30ms
        for (id, ok, elapsed) in &results {
            if *elapsed == Duration::from_millis(0) {
                continue; // Skip clients that couldn't connect
            }
            assert!(!ok, "Client {} should have timed out", id);
            assert!(
                *elapsed < Duration::from_millis(70),
                "Client {} timed out but took {:?} (should be ~30ms)",
                id,
                elapsed
            );
        }
    }

    /// Manual test: High concurrent load to verify timeout includes connection acquisition time.
    ///
    /// This test simulates the production scenario where many concurrent clients create
    /// contention in connection management. Under high load, connection acquisition can
    /// take significant time, and this time must be counted toward the timeout.
    ///
    /// To run this test manually:
    /// 1. Start Valkey with DEBUG commands: valkey-server --enable-debug-command yes
    /// 2. Run: cargo test --test test_timeout -- --ignored --nocapture
    ///
    /// Expected WITH fix: All timeouts occur at ~30ms
    /// Expected WITHOUT fix: Timeouts occur at 50-100ms+ (connection wait + timeout)
    #[tokio::test]
    #[ignore = "Manual test - requires high load simulation"]
    async fn test_timeout_enforced_under_high_load() {
        const TIMEOUT_MS: u32 = 30;
        const NUM_CLIENTS: usize = 50;

        println!("\n=== Timeout Under High Load Test ===");
        println!("Timeout: {}ms", TIMEOUT_MS);
        println!("Clients: {}", NUM_CLIENTS);
        println!();

        let mut handles = vec![];

        // Create many clients that all try to execute a slow command simultaneously
        // This creates contention in get_or_initialize_client()
        for i in 0..NUM_CLIENTS {
            let handle = tokio::spawn(async move {
                let config = glide_core::client::ConnectionRequest {
                    addresses: vec![glide_core::client::NodeAddress {
                        host: "localhost".to_string(),
                        port: 6379,
                    }],
                    request_timeout: Some(TIMEOUT_MS),
                    connection_timeout: Some(10000),
                    lazy_connect: true, // Force connection on first command
                    ..Default::default()
                };

                let mut client = match Client::new(config, None).await {
                    Ok(c) => c,
                    Err(_) => return (i, None), // Skip if can't connect
                };

                // Single slow command per client
                let mut cmd = Cmd::new();
                cmd.arg("DEBUG").arg("SLEEP").arg("0.05"); // 50ms command

                let start = Instant::now();
                let result = client.send_command(&mut cmd, None).await;
                let elapsed = start.elapsed();

                if result.is_err() {
                    (i, Some(elapsed))
                } else {
                    (i, None)
                }
            });

            handles.push(handle);
        }

        let results: Vec<_> = futures::future::join_all(handles)
            .await
            .into_iter()
            .map(|r| r.unwrap())
            .collect();

        // Collect all timeout durations
        let timeouts: Vec<_> = results
            .iter()
            .filter_map(|(id, elapsed)| elapsed.map(|e| (*id, e)))
            .collect();

        if !timeouts.is_empty() {
            let durations: Vec<_> = timeouts.iter().map(|(_, e)| *e).collect();
            let min = durations.iter().min().unwrap();
            let max = durations.iter().max().unwrap();
            let avg = durations.iter().sum::<Duration>() / durations.len() as u32;

            println!("\n=== Results ===");
            println!("Total clients: {}", NUM_CLIENTS);
            println!("Timeouts: {}", timeouts.len());
            println!("Successes: {}", NUM_CLIENTS - timeouts.len());
            println!("\nTimeout durations:");
            println!("  Min: {:?}", min);
            println!("  Max: {:?}", max);
            println!("  Avg: {:?}", avg);

            let under_40ms = durations
                .iter()
                .filter(|d| **d < Duration::from_millis(40))
                .count();
            let between_40_60ms = durations
                .iter()
                .filter(|d| **d >= Duration::from_millis(40) && **d < Duration::from_millis(60))
                .count();
            let over_60ms = durations
                .iter()
                .filter(|d| **d >= Duration::from_millis(60))
                .count();

            println!("\nDistribution:");
            println!("  < 40ms: {} (expected with fix)", under_40ms);
            println!("  40-60ms: {}", between_40_60ms);
            println!("  > 60ms: {} (indicates bug)", over_60ms);

            let proper_timeouts = durations
                .iter()
                .filter(|d| **d < Duration::from_millis(50))
                .count();
            let proper_percent = (proper_timeouts as f64 / durations.len() as f64) * 100.0;

            println!("\nProper timeouts (< 50ms): {:.1}%", proper_percent);

            if proper_percent <= 80.0 {
                panic!(
                    "Expected >80% of timeouts to be <50ms, got {:.1}%",
                    proper_percent
                );
            }
        } else {
            println!("No timeouts occurred (commands completed quickly)");
        }
    }

    /// Test with shared client and many concurrent requests.
    ///
    /// This test mimics the production scenario where a single client is reused
    /// across many concurrent requests. Under high concurrency, tasks may contend
    /// for the client's internal lock, and this wait time must be counted in timeout.
    ///
    /// Regression test for: https://github.com/valkey-io/valkey-glide/issues/5284
    #[tokio::test]
    #[ignore = "Manual test - shared client with high concurrency"]
    async fn test_shared_client_high_concurrency() {
        const TIMEOUT_MS: u32 = 30;
        const NUM_REQUESTS: usize = 10_000;  // High but not overwhelming

        println!("\n=== Shared Client High Concurrency Test ===");
        println!("Timeout: {}ms", TIMEOUT_MS);
        println!("Concurrent requests: {}", NUM_REQUESTS);
        println!();

        // Single shared client - production scenario
        let config = glide_core::client::ConnectionRequest {
            addresses: vec![glide_core::client::NodeAddress {
                host: "localhost".to_string(),
                port: 6379,
            }],
            request_timeout: Some(TIMEOUT_MS),
            connection_timeout: Some(10000),
            ..Default::default()
        };

        let client = std::sync::Arc::new(
            match Client::new(config, None).await {
                Ok(c) => c,
                Err(_) => {
                    println!("⚠️  Cannot connect - skipping");
                    return;
                }
            },
        );

        // Pre-initialize connection
        {
            let mut client_clone = (*client).clone();
            let mut ping = Cmd::new();
            ping.arg("PING");
            if client_clone.send_command(&mut ping, None).await.is_err() {
                println!("⚠️  Cannot ping - skipping");
                return;
            }
        }

        println!("✓ Client connected and ready");
        println!("Launching {} concurrent requests...\n", NUM_REQUESTS);

        let mut handles = vec![];

        for i in 0..NUM_REQUESTS {
            let client = client.clone();

            let handle = tokio::spawn(async move {
                let mut cmd = Cmd::new();
                cmd.arg("DEBUG").arg("SLEEP").arg("0.05"); // 50ms

                let start = Instant::now();
                let mut client_clone = (*client).clone();
                let result = client_clone.send_command(&mut cmd, None).await;
                let elapsed = start.elapsed();

                (i, result.is_err(), elapsed)
            });

            handles.push(handle);
        }

        let results: Vec<_> = futures::future::join_all(handles)
            .await
            .into_iter()
            .map(|r| r.unwrap())
            .collect();

        let timeouts: Vec<_> = results
            .iter()
            .filter(|(_, is_err, _)| *is_err)
            .map(|(id, _, elapsed)| (*id, *elapsed))
            .collect();

        let successes: Vec<_> = results
            .iter()
            .filter(|(_, is_err, _)| !*is_err)
            .map(|(id, _, elapsed)| (*id, *elapsed))
            .collect();

        println!("\n=== Results ===");
        println!("Total requests: {}", NUM_REQUESTS);
        println!("Timeouts: {}", timeouts.len());
        println!("Successes: {}", successes.len());

        if !timeouts.is_empty() {
            let durations: Vec<_> = timeouts.iter().map(|(_, e)| *e).collect();
            let min = durations.iter().min().unwrap();
            let max = durations.iter().max().unwrap();
            let avg = durations.iter().sum::<Duration>() / durations.len() as u32;

            println!("\nTimeout durations:");
            println!("  Min: {:?}", min);
            println!("  Max: {:?}", max);
            println!("  Avg: {:?}", avg);

            let under_40ms = durations
                .iter()
                .filter(|d| **d < Duration::from_millis(40))
                .count();
            let over_60ms = durations
                .iter()
                .filter(|d| **d >= Duration::from_millis(60))
                .count();

            println!("\nDistribution:");
            println!("  < 40ms: {} (expected with fix)", under_40ms);
            println!("  > 60ms: {} (indicates bug)", over_60ms);

            let proper = durations
                .iter()
                .filter(|d| **d < Duration::from_millis(50))
                .count();
            let percent = (proper as f64 / durations.len() as f64) * 100.0;

            println!("\nProper timeouts (< 50ms): {:.1}%", percent);

            if percent > 80.0 {
                println!("✅ FIX WORKING: Timeouts enforced correctly");
            } else {
                println!("❌ BUG PRESENT: Timeouts taking too long");
                panic!("Expected >80% < 50ms, got {:.1}%", percent);
            }
        }

        if !successes.is_empty() {
            let durations: Vec<_> = successes.iter().map(|(_, e)| *e).collect();
            let avg = durations.iter().sum::<Duration>() / durations.len() as u32;
            println!("\nSuccess durations:");
            println!("  Avg: {:?}", avg);
        }
    }
}
