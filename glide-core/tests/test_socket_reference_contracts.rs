// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Comprehensive contract validation tests for socket reference counting
//!
//! These tests enforce the behavioral contracts and edge cases identified
//! by TDD analysis to ensure robust socket lifecycle management.

use glide_core::{
    active_socket_count, cleanup_all_sockets, is_socket_active, socket_reference::SocketReference,
};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::sync::{
    Arc, Mutex,
    atomic::{AtomicUsize, Ordering},
};
use std::time::{Duration, Instant};
use tempfile::TempDir;
use tokio::time::sleep;

/// Test helper for managing test environments
struct TestEnv {
    temp_dir: TempDir,
    initial_socket_count: usize,
}

impl TestEnv {
    async fn new() -> Self {
        cleanup_all_sockets();
        sleep(Duration::from_millis(100)).await;

        let initial_socket_count = active_socket_count();
        if initial_socket_count > 0 {
            eprintln!(
                "Warning: Starting with {} active sockets",
                initial_socket_count
            );
            cleanup_all_sockets();
            sleep(Duration::from_millis(100)).await;
        }

        Self {
            temp_dir: TempDir::new().expect("Failed to create temp directory"),
            initial_socket_count: active_socket_count(),
        }
    }

    fn get_socket_path(&self) -> String {
        static COUNTER: AtomicUsize = AtomicUsize::new(0);
        let id = COUNTER.fetch_add(1, Ordering::SeqCst);
        self.temp_dir
            .path()
            .join(format!("test_socket_{}.sock", id))
            .to_str()
            .unwrap()
            .to_string()
    }

    async fn cleanup(&self) {
        cleanup_all_sockets();
        sleep(Duration::from_millis(100)).await;
        assert_eq!(
            active_socket_count(),
            self.initial_socket_count,
            "Socket leak detected"
        );
    }
}

// ============================================================================
// CONCURRENCY CONTRACT TESTS
// ============================================================================

#[tokio::test]
async fn test_concurrent_socket_creation_high_contention() {
    let env = TestEnv::new().await;
    let socket_path = env.get_socket_path();

    // Launch 50 concurrent tasks trying to create/get the same socket
    let mut handles = Vec::new();
    let success_count = Arc::new(AtomicUsize::new(0));
    let reference_count = Arc::new(Mutex::new(Vec::new()));

    for i in 0..50 {
        let socket_path = socket_path.clone();
        let success_count = Arc::clone(&success_count);
        let reference_count = Arc::clone(&reference_count);

        let handle = tokio::spawn(async move {
            // Random delay to increase contention variety
            sleep(Duration::from_micros(i as u64 * 10)).await;

            // Use atomic get_or_create to avoid race conditions
            let socket_ref = SocketReference::get_or_create(socket_path);

            success_count.fetch_add(1, Ordering::SeqCst);
            reference_count.lock().unwrap().push(socket_ref.clone());
            socket_ref
        });
        handles.push(handle);
    }

    // Wait for all tasks to complete
    let mut results = Vec::new();
    for handle in handles {
        results.push(handle.await.unwrap());
    }

    // Validate contract: All tasks should succeed
    assert_eq!(success_count.load(Ordering::SeqCst), 50);

    // Validate contract: Only one unique socket should exist
    assert_eq!(active_socket_count(), env.initial_socket_count + 1);

    // Validate contract: All references point to the same socket
    let first_path = results[0].path();
    let expected_count = results.len();
    for socket_ref in &results {
        assert_eq!(socket_ref.path(), first_path);
        assert_eq!(socket_ref.reference_count(), expected_count);
    }

    // Cleanup
    drop(results);
    env.cleanup().await;
}

#[tokio::test]
async fn test_concurrent_cleanup_with_lock_contention() {
    let env = TestEnv::new().await;

    // Create 100 references across 10 sockets
    let mut all_refs = Vec::new();
    for _ in 0..10 {
        let socket_path = env.get_socket_path();
        for _ in 0..10 {
            let socket_ref = SocketReference::get_or_create(socket_path.clone());
            all_refs.push(socket_ref);
        }
    }

    assert_eq!(active_socket_count(), env.initial_socket_count + 10);

    // Drop all references concurrently to create lock contention
    let handles: Vec<_> = all_refs
        .into_iter()
        .map(|socket_ref| {
            tokio::spawn(async move {
                // Random small delay to increase contention
                sleep(Duration::from_micros(rand::random::<u64>() % 100)).await;
                drop(socket_ref);
            })
        })
        .collect();

    // Wait for all drops to complete
    for handle in handles {
        handle.await.unwrap();
    }

    // Give cleanup time to complete
    sleep(Duration::from_millis(200)).await;

    // Validate contract: All sockets should be cleaned up
    assert_eq!(active_socket_count(), env.initial_socket_count);
}

// ============================================================================
// MEMORY PRESSURE CONTRACT TESTS
// ============================================================================

#[tokio::test]
async fn test_memory_pressure_many_references() {
    let env = TestEnv::new().await;
    let socket_path = env.get_socket_path();

    // Create 1000 references to the same socket
    let mut refs = Vec::with_capacity(1000);

    // First reference
    refs.push(SocketReference::get_or_create(socket_path.clone()));

    // Create 999 more references
    for _ in 1..1000 {
        refs.push(SocketReference::get_existing(&socket_path).unwrap());
    }

    // Validate contract: Reference count should be 1000
    assert_eq!(refs[0].reference_count(), 1000);
    assert_eq!(active_socket_count(), env.initial_socket_count + 1);

    // Drop half the references
    refs.truncate(500);
    assert_eq!(refs[0].reference_count(), 500);

    // Drop remaining references
    drop(refs);

    // Give cleanup time
    sleep(Duration::from_millis(100)).await;

    // Validate contract: Socket should be cleaned up
    assert_eq!(active_socket_count(), env.initial_socket_count);
    assert!(!is_socket_active(&socket_path));
}

#[tokio::test]
async fn test_memory_pressure_many_unique_sockets() {
    let env = TestEnv::new().await;

    // Create 500 unique sockets
    let mut socket_refs = Vec::with_capacity(500);

    for _ in 0..500 {
        let socket_path = env.get_socket_path();
        socket_refs.push(SocketReference::get_or_create(socket_path));
    }

    // Validate contract: All 500 sockets should be active
    assert_eq!(active_socket_count(), env.initial_socket_count + 500);

    // Drop sockets in random order
    use rand::seq::SliceRandom;
    let mut rng = rand::thread_rng();
    socket_refs.shuffle(&mut rng);

    while !socket_refs.is_empty() {
        // Drop in batches
        let batch_size = std::cmp::min(50, socket_refs.len());
        socket_refs.truncate(socket_refs.len() - batch_size);

        // Allow cleanup between batches
        sleep(Duration::from_millis(10)).await;
    }

    // Give final cleanup time
    sleep(Duration::from_millis(200)).await;

    // Validate contract: All sockets should be cleaned up
    assert_eq!(active_socket_count(), env.initial_socket_count);
}

// ============================================================================
// RAPID CYCLE CONTRACT TESTS
// ============================================================================

#[tokio::test]
async fn test_rapid_creation_destruction_cycles() {
    let env = TestEnv::new().await;

    for cycle in 0..200 {
        let socket_path = env.get_socket_path();

        // Create socket
        let socket_ref = SocketReference::get_or_create(socket_path.clone());

        // Validate it's active
        assert!(is_socket_active(&socket_path));
        assert_eq!(socket_ref.reference_count(), 1);

        // Create additional references
        let ref2 = SocketReference::get_existing(&socket_path).unwrap();
        let ref3 = ref2.clone();
        assert_eq!(socket_ref.reference_count(), 3);

        // Drop all references
        drop(socket_ref);
        drop(ref2);
        drop(ref3);

        // Minimal delay between cycles
        if cycle % 10 == 0 {
            sleep(Duration::from_millis(1)).await;
        }
    }

    // Give cleanup time
    sleep(Duration::from_millis(200)).await;

    // Validate contract: No socket leaks after 200 cycles
    assert_eq!(active_socket_count(), env.initial_socket_count);
}

#[tokio::test]
async fn test_burst_creation_destruction() {
    let env = TestEnv::new().await;

    // Create bursts of sockets
    for _burst in 0..10 {
        let mut refs = Vec::new();

        // Create burst of 50 sockets
        for _ in 0..50 {
            let socket_path = env.get_socket_path();
            refs.push(SocketReference::get_or_create(socket_path));
        }

        // Validate all are active
        assert_eq!(active_socket_count(), env.initial_socket_count + 50);

        // Destroy burst
        drop(refs);

        // Wait for cleanup
        sleep(Duration::from_millis(50)).await;

        // Validate all cleaned up
        assert_eq!(active_socket_count(), env.initial_socket_count);
    }
}

// ============================================================================
// FILESYSTEM ERROR CONTRACT TESTS
// ============================================================================

#[tokio::test]
async fn test_orphaned_socket_file_handling() {
    let env = TestEnv::new().await;
    let socket_path = env.get_socket_path();

    // Create an orphaned socket file
    fs::write(&socket_path, b"orphaned socket data").unwrap();
    assert!(std::path::Path::new(&socket_path).exists());

    // Register new socket should handle the orphaned file
    let socket_ref = SocketReference::get_or_create(socket_path.clone());

    // Validate socket is tracked
    assert!(is_socket_active(&socket_path));
    assert_eq!(socket_ref.reference_count(), 1);

    // Cleanup should remove the file
    drop(socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Validate file is removed
    assert!(!std::path::Path::new(&socket_path).exists());
    assert!(!is_socket_active(&socket_path));
}

#[tokio::test]
async fn test_cleanup_with_permission_errors() {
    let env = TestEnv::new().await;
    let socket_path = env.get_socket_path();

    // Create socket
    let socket_ref = SocketReference::get_or_create(socket_path.clone());

    // Create the socket file and make it read-only
    fs::write(&socket_path, b"socket data").unwrap();
    let metadata = fs::metadata(&socket_path).unwrap();
    let mut permissions = metadata.permissions();
    permissions.set_mode(0o444); // Read-only
    fs::set_permissions(&socket_path, permissions).unwrap();

    // Drop reference - cleanup will fail but should not panic
    drop(socket_ref);
    sleep(Duration::from_millis(100)).await;

    // File might still exist due to permission error, but socket should not be active
    assert!(!is_socket_active(&socket_path));

    // Clean up manually
    let mut permissions = fs::metadata(&socket_path).unwrap().permissions();
    permissions.set_mode(0o644);
    fs::set_permissions(&socket_path, permissions).unwrap();
    fs::remove_file(&socket_path).ok();
}

// ============================================================================
// PROPERTY-BASED CONTRACT TESTS
// ============================================================================

#[tokio::test]
async fn test_reference_counting_invariants() {
    let env = TestEnv::new().await;
    let socket_path = env.get_socket_path();

    // Property 1: Initial reference count is 1
    let ref1 = SocketReference::get_or_create(socket_path.clone());
    assert_eq!(ref1.reference_count(), 1);

    // Property 2: Clone increases count by exactly 1
    let ref2 = ref1.clone();
    assert_eq!(ref1.reference_count(), 2);
    assert_eq!(ref2.reference_count(), 2);

    // Property 3: Getting existing reference increases count
    let ref3 = SocketReference::get_existing(&socket_path).unwrap();
    assert_eq!(ref1.reference_count(), 3);
    assert_eq!(ref2.reference_count(), 3);
    assert_eq!(ref3.reference_count(), 3);

    // Property 4: Drop decreases count by exactly 1
    drop(ref1);
    assert_eq!(ref2.reference_count(), 2);
    assert_eq!(ref3.reference_count(), 2);

    drop(ref2);
    assert_eq!(ref3.reference_count(), 1);

    // Property 5: Last drop triggers cleanup
    drop(ref3);
    sleep(Duration::from_millis(100)).await;
    assert!(!is_socket_active(&socket_path));
    assert_eq!(active_socket_count(), env.initial_socket_count);
}

#[tokio::test]
async fn test_arc_pointer_equality_invariants() {
    let env = TestEnv::new().await;
    let socket_path = env.get_socket_path();

    let ref1 = SocketReference::get_or_create(socket_path.clone());
    let ref2 = SocketReference::get_existing(&socket_path).unwrap();
    let ref3 = ref1.clone();

    // Property: All references to same socket share the same Arc
    // This is validated by reference count being shared
    assert_eq!(ref1.reference_count(), 3);
    assert_eq!(ref2.reference_count(), 3);
    assert_eq!(ref3.reference_count(), 3);

    // Property: All point to the same path
    assert_eq!(ref1.path(), ref2.path());
    assert_eq!(ref2.path(), ref3.path());
}

// ============================================================================
// PERFORMANCE CONTRACT TESTS
// ============================================================================

#[tokio::test]
async fn test_performance_lock_contention_benchmark() {
    let env = TestEnv::new().await;

    let measurements = Arc::new(Mutex::new(Vec::new()));

    // Test with increasing contention levels
    for contention_level in [1, 5, 10, 20, 50] {
        let socket_path = env.get_socket_path();
        let start = Instant::now();

        let mut handles = Vec::new();
        for _ in 0..contention_level {
            let socket_path = socket_path.clone();

            let handle = tokio::spawn(async move {
                for _ in 0..10 {
                    let socket_ref =
                        if let Some(existing) = SocketReference::get_existing(&socket_path) {
                            existing
                        } else {
                            SocketReference::get_or_create(socket_path.clone())
                        };
                    // Immediate drop to create more contention
                    drop(socket_ref);
                }
            });
            handles.push(handle);
        }

        // Wait for all tasks
        for handle in handles {
            handle.await.unwrap();
        }

        let duration = start.elapsed();
        measurements
            .lock()
            .unwrap()
            .push((contention_level, duration));

        // Cleanup between tests
        cleanup_all_sockets();
        sleep(Duration::from_millis(100)).await;
    }

    // Validate performance doesn't degrade exponentially
    let measurements = measurements.lock().unwrap();
    for i in 1..measurements.len() {
        let (prev_level, prev_duration) = measurements[i - 1];
        let (curr_level, curr_duration) = measurements[i];

        let scaling_factor = curr_level as f64 / prev_level as f64;
        let duration_factor = curr_duration.as_secs_f64() / prev_duration.as_secs_f64();

        // Contract: Performance degradation should be sub-linear
        assert!(
            duration_factor < scaling_factor * 2.0,
            "Performance degraded too much: {}x threads caused {}x slowdown",
            scaling_factor,
            duration_factor
        );
    }
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_stress_mixed_operations() {
    let env = TestEnv::new().await;

    let start = Instant::now();
    let operation_count = Arc::new(AtomicUsize::new(0));
    let active_refs = Arc::new(Mutex::new(Vec::new()));

    // Run stress test for 5 seconds
    let mut handles = Vec::new();

    for thread_id in 0..4 {
        let operation_count = Arc::clone(&operation_count);
        let active_refs = Arc::clone(&active_refs);
        let temp_dir = env.temp_dir.path().to_path_buf();

        let handle = tokio::spawn(async move {
            let mut local_refs = Vec::new();
            use rand::{Rng, SeedableRng};
            let mut rng = rand::rngs::StdRng::from_entropy();

            while start.elapsed() < Duration::from_secs(5) {
                let operation = rng.gen_range(0..4);

                match operation {
                    0 => {
                        // Create new socket
                        let socket_path = temp_dir
                            .join(format!(
                                "stress_socket_{}_{}.sock",
                                thread_id,
                                operation_count.fetch_add(1, Ordering::SeqCst)
                            ))
                            .to_str()
                            .unwrap()
                            .to_string();

                        let socket_ref = SocketReference::get_or_create(socket_path);
                        local_refs.push(socket_ref);
                    }
                    1 if !local_refs.is_empty() => {
                        // Clone existing reference
                        use rand::seq::SliceRandom;
                        if let Some(socket_ref) = local_refs.choose(&mut rng) {
                            local_refs.push(socket_ref.clone());
                        }
                    }
                    2 if !local_refs.is_empty() => {
                        // Drop random reference
                        let idx = rng.gen_range(0..local_refs.len());
                        local_refs.remove(idx);
                    }
                    3 => {
                        // Transfer references to global pool
                        if !local_refs.is_empty() {
                            let mut global = active_refs.lock().unwrap();
                            global.extend(local_refs.drain(..));
                        }
                    }
                    _ => {}
                }

                operation_count.fetch_add(1, Ordering::SeqCst);

                // Yield occasionally
                if operation_count.load(Ordering::SeqCst) % 100 == 0 {
                    tokio::task::yield_now().await;
                }
            }

            // Clean up remaining references
            drop(local_refs);
        });
        handles.push(handle);
    }

    // Wait for all threads to complete
    for handle in handles {
        handle.await.unwrap();
    }

    let total_ops = operation_count.load(Ordering::SeqCst);
    let ops_per_sec = total_ops as f64 / 5.0;

    println!(
        "Stress test completed: {} operations ({:.1} ops/sec)",
        total_ops, ops_per_sec
    );

    // Contract: Should handle at least 100 ops/sec under stress
    assert!(
        ops_per_sec > 100.0,
        "Performance too low: {:.1} ops/sec",
        ops_per_sec
    );

    // Cleanup all remaining references
    active_refs.lock().unwrap().clear();
    cleanup_all_sockets();
    sleep(Duration::from_millis(500)).await;

    // Contract: No leaks after stress test
    assert_eq!(active_socket_count(), env.initial_socket_count);
}
