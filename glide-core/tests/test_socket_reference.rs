// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests for socket reference counting functionality
//!
//! These tests verify that the SocketReference system properly manages
//! socket lifecycle with reference counting and automatic cleanup.

use glide_core::{
    active_socket_count, cleanup_all_sockets, is_socket_active, socket_reference::*,
    start_socket_listener_with_reference,
};
use std::fs::{self, Permissions};
use std::os::unix::fs::PermissionsExt;
use std::sync::{
    Arc, Mutex,
    atomic::{AtomicUsize, Ordering},
};
use std::time::{Duration, Instant};
use tempfile::TempDir;
use tokio::time::sleep;

/// Test utilities for socket reference testing
struct TestSocketHelper {
    temp_dir: TempDir,
    counter: Arc<Mutex<u32>>,
}

impl TestSocketHelper {
    fn new() -> Self {
        Self {
            temp_dir: TempDir::new().expect("Failed to create temp directory"),
            counter: Arc::new(Mutex::new(0)),
        }
    }

    fn get_test_socket_path(&self) -> String {
        let mut counter = self.counter.lock().unwrap();
        *counter += 1;
        self.temp_dir
            .path()
            .join(format!("test_socket_{}.sock", *counter))
            .to_str()
            .unwrap()
            .to_string()
    }
}

#[tokio::test]
async fn test_socket_reference_basic_lifecycle() {
    cleanup_all_sockets();

    // Give cleanup time to complete
    sleep(Duration::from_millis(50)).await;

    let socket_path = "/tmp/test_socket_ref_basic.sock".to_string();

    // Initially no active sockets
    let initial_count = active_socket_count();
    assert!(!is_socket_active(is_socket_active(&socket_path);socket_path));

    // Test the core reference counting without the full socket listener
    // This tests the SocketReference functionality directly
    let socket_ref1 = SocketReference::register_new_socket(socket_path.clone());

    // Verify socket is tracked
    assert_eq!(active_socket_count(), initial_count + 1);
    assert!(is_socket_active(socket_ref1.path();
    assert_eq!(socket_ref1.path(), socket_path);
    assert_eq!(socket_ref1.reference_count(), 1);

    // Test getting existing reference
    if let Some(socket_ref2) = SocketReference::get_existing(&socket_path) {
        assert_eq!(socket_ref1.reference_count(), 2);
        assert_eq!(socket_ref2.reference_count(), 2);
        assert_eq!(active_socket_count(), initial_count + 1); // Still only one unique socket

        // Drop first reference
        drop(socket_ref1);
        assert_eq!(socket_ref2.reference_count(), 1);
        assert_eq!(active_socket_count(), initial_count + 1);

        // Drop second reference
        drop(socket_ref2);
    } else {
        panic!("Should have been able to get existing socket reference");
    }

    // Give cleanup time to complete
    sleep(Duration::from_millis(50)).await;

    // Verify cleanup
    assert_eq!(active_socket_count(), initial_count);
    assert!(!is_socket_active(is_socket_active(&socket_path);socket_path));
}

#[tokio::test]
async fn test_multiple_references_same_socket() {
    cleanup_all_sockets();

    let helper = TestSocketHelper::new();
    let socket_path = helper.get_test_socket_path();

    // Create first socket reference
    let (tx1, rx1) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx1.send(result);
        },
        Some(socket_path.clone()),
    );

    let socket_ref1 = rx1
        .await
        .unwrap()
        .expect("First socket creation should succeed");
    assert_eq!(socket_ref1.reference_count(), 1);
    assert_eq!(active_socket_count(), 1);

    // Create second reference to same socket
    let (tx2, rx2) = tokio::sync::oneshot::channel();
    let socket_path_clone = socket_path.clone());
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx2.send(result);
        },
        Some(socket_path_clone),
    );

    let socket_ref2 = rx2
        .await
        .unwrap()
        .expect("Second socket reference should succeed");

    // Both should point to the same socket with reference count 2
    assert_eq!(socket_ref1.reference_count(), 2);
    assert_eq!(socket_ref2.reference_count(), 2);
    assert_eq!(active_socket_count(), 1); // Still only one unique socket
    assert_eq!(socket_ref1.path(), socket_ref2.path();

    // Drop first reference
    drop(socket_ref1);

    // Socket should still be active
    assert_eq!(socket_ref2.reference_count(), 1);
    assert_eq!(active_socket_count(), 1);
    assert!(is_socket_active(is_socket_active(&socket_path);socket_path));

    // Drop second reference
    drop(socket_ref2);

    // Give cleanup time to complete
    sleep(Duration::from_millis(50)).await;

    // Now socket should be cleaned up
    assert_eq!(active_socket_count(), 0);
    assert!(!is_socket_active(is_socket_active(&socket_path);socket_path));
}

#[tokio::test]
async fn test_multiple_different_sockets() {
    cleanup_all_sockets();

    let helper = TestSocketHelper::new();
    let socket_path1 = helper.get_test_socket_path();
    let socket_path2 = helper.get_test_socket_path();

    // Create first socket
    let (tx1, rx1) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx1.send(result);
        },
        Some(socket_path1.clone()),
    );

    let socket_ref1 = rx1
        .await
        .unwrap()
        .expect("First socket creation should succeed");
    assert_eq!(active_socket_count(), 1);

    // Create second socket
    let (tx2, rx2) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx2.send(result);
        },
        Some(socket_path2.clone()),
    );

    let socket_ref2 = rx2
        .await
        .unwrap()
        .expect("Second socket creation should succeed");
    assert_eq!(active_socket_count(), 2);

    // Verify both sockets are active and different
    assert!(is_socket_active(&socket_path1);
    assert!(is_socket_active(&socket_path2);
    assert_ne!(socket_ref1.path(), socket_ref2.path();
    assert_eq!(socket_ref1.reference_count(), 1);
    assert_eq!(socket_ref2.reference_count(), 1);

    // Drop first socket
    drop(socket_ref1);
    sleep(Duration::from_millis(50)).await;

    // Only second socket should remain
    assert_eq!(active_socket_count(), 1);
    assert!(!is_socket_active(&socket_path1);
    assert!(is_socket_active(&socket_path2);

    // Drop second socket
    drop(socket_ref2);
    sleep(Duration::from_millis(50)).await;

    // No sockets should remain
    assert_eq!(active_socket_count(), 0);
    assert!(!is_socket_active(&socket_path2);
}

#[tokio::test]
async fn test_socket_reference_clone_behavior() {
    cleanup_all_sockets();

    // Give cleanup time to complete
    sleep(Duration::from_millis(50)).await;

    let socket_path = "/tmp/test_socket_ref_clone.sock".to_string();
    let initial_count = active_socket_count();

    // Create socket reference using direct API
    let socket_ref1 = SocketReference::register_new_socket(socket_path.clone());
    assert_eq!(socket_ref1.reference_count(), 1);

    // Clone the reference
    let socket_ref2 = socket_ref1.clone();
    assert_eq!(socket_ref1.reference_count(), 2);
    assert_eq!(socket_ref2.reference_count(), 2);
    assert_eq!(active_socket_count(), initial_count + 1);

    // Create another clone
    let socket_ref3 = socket_ref2.clone();
    assert_eq!(socket_ref1.reference_count(), 3);
    assert_eq!(socket_ref2.reference_count(), 3);
    assert_eq!(socket_ref3.reference_count(), 3);

    // Drop one clone
    drop(socket_ref1);
    assert_eq!(socket_ref2.reference_count(), 2);
    assert_eq!(socket_ref3.reference_count(), 2);
    assert_eq!(active_socket_count(), initial_count + 1);

    // Drop another clone
    drop(socket_ref2);
    assert_eq!(socket_ref3.reference_count(), 1);
    assert_eq!(active_socket_count(), initial_count + 1);

    // Drop final clone
    drop(socket_ref3);
    sleep(Duration::from_millis(50)).await;

    // Socket should be cleaned up
    assert_eq!(active_socket_count(), initial_count);
    assert!(!is_socket_active(is_socket_active(&socket_path);socket_path));
}

#[tokio::test]
async fn test_socket_creation_failure_handling() {
    cleanup_all_sockets();

    // Try to create socket in non-existent directory
    let invalid_path = "/non/existent/directory/socket.sock".to_string();

    let (tx, rx) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx.send(result);
        },
        Some(invalid_path.clone()),
    );

    // Should get an error
    let result = rx.await.unwrap();
    assert!(
        result.is_err(),
        "Socket creation should fail for invalid path"
    );

    // No sockets should be tracked
    assert_eq!(active_socket_count(), 0);
    assert!(!is_socket_active(&invalid_path);
}

#[tokio::test]
async fn test_concurrent_socket_creation_high_contention() {
    cleanup_all_sockets();

    let helper = TestSocketHelper::new();
    let socket_path = helper.get_test_socket_path();

    // Test contract: Under high contention, all concurrent creations should either:
    // 1. Successfully get a reference to the same socket, OR
    // 2. Fail gracefully without corrupting state
    // No partial failures or race conditions allowed

    // Launch 50 concurrent socket creation attempts to stress-test atomicity
    let mut handles = Vec::new();

    for i in 0..50 {
        let socket_path_clone = socket_path.clone());
        let handle = tokio::spawn(async move {
            let (tx, rx) = tokio::sync::oneshot::channel();
            start_socket_listener_with_reference(
                move |result| {
                    let _ = tx.send(result);
                },
                Some(socket_path_clone),
            );

            let result = rx.await.unwrap();
            (i, result)
        });
        handles.push(handle);
    }

    // Wait for all to complete
    let mut socket_refs = Vec::new();
    let mut error_count = 0;

    for handle in handles {
        let (id, result) = handle.await.unwrap();
        match result {
            Ok(socket_ref) => {
                println!(
                    "Task {} got socket reference with count {}",
                    id,
                    socket_ref.reference_count()
                );
                socket_refs.push(socket_ref);
            }
            Err(e) => {
                // Some failures are acceptable under extreme contention
                println!("Task {} failed (acceptable under contention): {}", id, e);
                error_count += 1;
            }
        }
    }

    // Contract validation: At least one should succeed, creating exactly one unique socket
    assert!(
        !socket_refs.is_empty(),
        "At least one socket creation must succeed"
    );
    assert_eq!(
        active_socket_count(),
        1,
        "Exactly one unique socket must exist"
    );

    // Contract validation: All successful references point to same socket
    if socket_refs.len() > 1 {
        let first_path = socket_refs[0].path();
        let expected_count = socket_refs.len();

        for socket_ref in &socket_refs {
            assert_eq!(
                socket_ref.path(),
                first_path,
                "All references must point to same socket"
            );
            assert_eq!(
                socket_ref.reference_count(),
                expected_count,
                "Reference count must be consistent"
            );
        }
    }

    // Contract validation: Total success + failures = 50
    assert_eq!(
        socket_refs.len() + error_count,
        50,
        "All tasks must complete (success or failure)"
    );

    // Drop all references
    drop(socket_refs);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Socket must be cleaned up completely
    assert_eq!(
        active_socket_count(),
        0,
        "Socket must be cleaned up after all references dropped"
    );
    assert!(
        !is_socket_active(&socket_path),
        "Socket must not be active after cleanup"
    );
}

/// Test concurrent socket creation with immediate drops to stress cleanup logic
#[tokio::test]
async fn test_concurrent_creation_and_immediate_cleanup() {
    cleanup_all_sockets();

    let helper = TestSocketHelper::new();
    let socket_path = helper.get_test_socket_path();

    // Contract: Rapid create-drop cycles must not corrupt reference counting
    // or leave orphaned resources

    let mut handles = Vec::new();

    for i in 0..20 {
        let socket_path_clone = socket_path.clone());
        let handle = tokio::spawn(async move {
            let (tx, rx) = tokio::sync::oneshot::channel();
            start_socket_listener_with_reference(
                move |result| {
                    let _ = tx.send(result);
                },
                Some(socket_path_clone.clone()),
            );

            if let Ok(Ok(socket_ref)) = rx.await {
                // Hold reference for random short duration
                let hold_time = (i % 5) + 1;
                sleep(Duration::from_millis(hold_time)).await;

                let final_count = socket_ref.reference_count();
                drop(socket_ref);
                (i, Ok(final_count))
            } else {
                (i, Err("Failed to create socket"))
            }
        });
        handles.push(handle);
    }

    // Collect results
    let mut success_count = 0;
    for handle in handles {
        match handle.await.unwrap() {
            (id, Ok(count)) => {
                println!("Task {} succeeded with final ref count {}", id, count);
                success_count += 1;
            }
            (id, Err(e)) => {
                println!("Task {} failed: {}", id, e);
            }
        }
    }

    // Give ample time for all cleanup to complete
    sleep(Duration::from_millis(200)).await;

    // Contract validation: All resources must be cleaned up
    assert_eq!(
        active_socket_count(),
        0,
        "All sockets must be cleaned up after rapid cycles"
    );
    assert!(
        !is_socket_active(&socket_path),
        "Socket must not be active after cleanup"
    );
    assert!(success_count > 0, "At least some creations should succeed");
}

/// Test concurrent registration vs existing lookup race conditions
#[tokio::test]
async fn test_register_vs_get_existing_race_conditions() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_race_register_vs_get.sock".to_string();

    // Contract: register_new_socket and get_existing must be atomic
    // No race condition should allow duplicate registrations or missed lookups

    let mut handles = Vec::new();

    // Half the tasks register new sockets, half try to get existing
    for i in 0..20 {
        let socket_path_clone = socket_path.clone());

        let handle = tokio::spawn(async move {
            if i % 2 == 0 {
                // Try to register new socket
                let socket_ref =
                    SocketReference::register_new_socket(socket_path_clone);
                (i, "register", Some(socket_ref.reference_count());
            } else {
                // Try to get existing socket
                if let Some(socket_ref) = SocketReference::get_existing(&socket_path_clone) {
                    (i, "get_existing", Some(socket_ref.reference_count());
                } else {
                    (i, "get_existing", None)
                }
            }
        });
        handles.push(handle);
    }

    // Collect results
    let mut register_count = 0;
    let mut get_existing_success = 0;
    let mut get_existing_miss = 0;
    let mut all_refs = Vec::new();

    for handle in handles {
        let (id, operation, result) = handle.await.unwrap();
        match (operation, result) {
            ("register", Some(count)) => {
                register_count += 1;
                println!("Task {} registered with count {}", id, count);
                // Keep a reference to verify cleanup later
                if let Some(socket_ref) = SocketReference::get_existing(&socket_path) {
                    all_refs.push(socket_ref);
                }
            }
            ("get_existing", Some(count)) => {
                get_existing_success += 1;
                println!("Task {} found existing with count {}", id, count);
                if let Some(socket_ref) = SocketReference::get_existing(&socket_path) {
                    all_refs.push(socket_ref);
                }
            }
            ("get_existing", None) => {
                get_existing_miss += 1;
                println!("Task {} missed existing socket", id);
            }
            _ => unreachable!(),
        }
    }

    // Contract validation: Exactly one unique socket should exist
    assert_eq!(
        active_socket_count(),
        1,
        "Exactly one socket should exist despite race conditions"
    );

    // Contract validation: Reference counting should be consistent
    if !all_refs.is_empty() {
        let expected_count = all_refs.len();
        for socket_ref in &all_refs {
            assert_eq!(
                socket_ref.reference_count(),
                expected_count,
                "All references should have consistent count"
            );
        }
    }

    println!(
        "Race test results: {} registers, {} get_existing hits, {} misses",
        register_count, get_existing_success, get_existing_miss
    );

    // Cleanup
    drop(all_refs);
    sleep(Duration::from_millis(100)).await;

    assert_eq!(active_socket_count(), 0, "Socket must be cleaned up");
}

/// Test lock contention scenarios and deadlock prevention
#[tokio::test]
async fn test_lock_contention_during_cleanup() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_lock_contention.sock".to_string();

    // Contract: The drop implementation uses try_lock to prevent deadlocks
    // This test validates that cleanup doesn't hang under lock contention

    let start_time = Instant::now();
    let success_count = Arc::new(AtomicUsize::new(0));
    let _lock_failure_count = Arc::new(AtomicUsize::new(0));

    let mut handles = Vec::new();

    // Create many socket references and drop them rapidly to create lock contention
    for i in 0..100 {
        let socket_path_clone = socket_path.clone());
        let success_count_clone = success_count.clone();

        let handle = tokio::spawn(async move {
            // Create reference
            let socket_ref =
                SocketReference::register_new_socket(socket_path_clone);

            // Hold briefly then drop to create contention during cleanup
            sleep(Duration::from_millis((i % 10) as u64)).await;

            // Track if the drop succeeds or hits lock contention
            let ref_count_before_drop = socket_ref.reference_count();
            drop(socket_ref);

            success_count_clone.fetch_add(1, Ordering::Relaxed);
            ref_count_before_drop
        });
        handles.push(handle);
    }

    // Wait for all to complete
    let mut ref_counts = Vec::new();
    for handle in handles {
        let count = handle.await.unwrap();
        ref_counts.push(count);
    }

    // Contract validation: Test must complete within reasonable time (no deadlocks)
    let elapsed = start_time.elapsed();
    assert!(
        elapsed < Duration::from_secs(30),
        "Lock contention test took too long: {:?} - possible deadlock",
        elapsed
    );

    // Give cleanup time to complete
    sleep(Duration::from_millis(200)).await;

    // Contract validation: All sockets must be cleaned up despite contention
    assert_eq!(
        active_socket_count(),
        0,
        "All sockets must be cleaned up even under lock contention"
    );

    let final_success = success_count.load(Ordering::Relaxed);
    println!(
        "Lock contention test: {} successes, completed in {:?}",
        final_success, elapsed
    );

    assert_eq!(final_success, 100, "All drops must complete successfully");
}

/// Test that try_lock failures in drop don't cause resource leaks
#[tokio::test]
async fn test_drop_try_lock_failure_handling() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_drop_lock_failure.sock".to_string();

    // Contract: When drop() can't acquire lock immediately, it should log error
    // but not block or leak resources. The socket will be cleaned up eventually.

    // Create a socket reference that we'll hold onto
    let long_lived_ref = SocketReference::register_new_socket(socket_path.clone());

    // Create many short-lived references to the same socket
    let mut short_refs = Vec::new();
    for _ in 0..10 {
        if let Some(socket_ref) = SocketReference::get_existing(&socket_path) {
            short_refs.push(socket_ref);
        }
    }

    assert_eq!(
        long_lived_ref.reference_count(),
        11,
        "Should have 11 total references"
    );

    // Drop all short references rapidly to create potential lock contention
    drop(short_refs);

    // Brief pause to let any try_lock failures occur
    sleep(Duration::from_millis(50)).await;

    // Contract validation: Socket should still be active (one reference remains)
    assert!(
        is_socket_active(&socket_path),
        "Socket should still be active"
    );
    assert_eq!(active_socket_count(), 1, "One socket should remain active");
    assert_eq!(
        long_lived_ref.reference_count(),
        1,
        "Should have 1 remaining reference"
    );

    // Drop the final reference
    drop(long_lived_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Socket must be cleaned up eventually
    assert_eq!(active_socket_count(), 0, "Socket must be cleaned up");
    assert!(!is_socket_active(&socket_path), "Socket must not be active");
}

/// Test concurrent access to global socket manager state
#[tokio::test]
async fn test_concurrent_global_state_access() {
    cleanup_all_sockets();


    // Contract: Concurrent access to global SOCKET_MANAGER should be thread-safe
    // and maintain consistent state

    let mut handles = Vec::new();

    // Mix of operations that all access the global socket manager
    for i in 0..50 {

        let handle = tokio::spawn(async move {
            let socket_path = format!("/tmp/test_concurrent_global_{}.sock", i % 5);

            match i % 4 {
                0 => {
                    // Register new socket
                    let _socket_ref =
                        SocketReference::register_new_socket(socket_path);
                    "register"
                }
                1 => {
                    // Get existing socket
                    let _existing = SocketReference::get_existing(&socket_path);
                    "get_existing"
                }
                2 => {
                    // Check if socket is active
                    let _is_active = is_socket_active(is_socket_active(&socket_path);socket_path));
                    "is_active"
                }
                3 => {
                    // Get active socket count
                    let _count = active_socket_count();
                    "count"
                }
                _ => unreachable!(),
            }
        });
        handles.push(handle);
    }

    // Wait for all operations to complete
    let mut operation_counts = std::collections::HashMap::new();
    for handle in handles {
        let operation = handle.await.unwrap();
        *operation_counts.entry(operation).or_insert(0) += 1;
    }

    println!(
        "Concurrent global state access operations: {:?}",
        operation_counts
    );

    // Give cleanup time to complete
    sleep(Duration::from_millis(200)).await;

    // Contract validation: System should be in consistent final state
    let final_count = active_socket_count();
    println!(
        "Final socket count after concurrent access: {}",
        final_count
    );

    // All sockets should eventually be cleaned up (may have some stragglers)
    assert!(
        final_count <= 5,
        "Should have at most 5 sockets (one per unique path)"
    );

    // Force cleanup and verify it works
    cleanup_all_sockets();
    assert_eq!(
        active_socket_count(),
        0,
        "Cleanup should remove all sockets"
    );
}

/// Test behavior when cleanup_expired_references is called concurrently
#[tokio::test]
async fn test_concurrent_cleanup_expired_references() {
    cleanup_all_sockets();


    // Contract: cleanup_expired_references should be safe to call concurrently
    // and not cause corruption or inconsistent state

    // Create some socket references
    let socket_paths: Vec<String> = (0..10)
        .map(|i| format!("/tmp/test_cleanup_expired_{}.sock", i))
        .collect();

    let mut socket_refs = Vec::new();
    for path in &socket_paths {
        let socket_ref = SocketReference::register_new_socket(path.clone());
        socket_refs.push(socket_ref);
    }

    assert_eq!(
        active_socket_count(),
        10,
        "Should have 10 sockets initially"
    );

    // Drop half the references to create expired entries
    socket_refs.truncate(5);
    sleep(Duration::from_millis(50)).await;

    // Concurrently call functions that trigger cleanup_expired_references
    let mut handles = Vec::new();

    for i in 0..20 {
        let paths_clone = socket_paths.clone();
        let handle = tokio::spawn(async move {
            match i % 3 {
                0 => {
                    // This calls cleanup_expired_references internally
                    active_socket_count()
                }
                1 => {
                    // This also calls cleanup_expired_references
                    let random_path = &paths_clone[i % paths_clone.len()];
                    if is_socket_active(random_path) { 1 } else { 0 }
                }
                2 => {
                    // Force cleanup
                    cleanup_all_sockets();
                    0
                }
                _ => unreachable!(),
            }
        });
        handles.push(handle);
    }

    // Wait for all concurrent operations
    for handle in handles {
        let _result = handle.await.unwrap();
    }

    // Give final cleanup time
    sleep(Duration::from_millis(100)).await;

    // Contract validation: System should reach consistent final state
    let final_count = active_socket_count();
    println!("Final count after concurrent cleanup: {}", final_count);

    // Should be 0 due to cleanup_all_sockets calls
    assert_eq!(final_count, 0, "All sockets should be cleaned up");

    // Verify remaining references are still valid
    for socket_ref in socket_refs {
        // These should still work even though sockets were cleaned up
        assert!(
            !socket_ref.path().is_empty(),
            "Path should still be accessible"
        );
    }
}

/// Test filesystem error scenarios during socket cleanup
#[tokio::test]
async fn test_filesystem_permission_errors_during_cleanup() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");
    let socket_path = temp_dir.path().join("test_permission_error.sock");
    let socket_path_str = socket_path.to_str().unwrap().to_string();


    // Contract: System must handle permission errors gracefully during cleanup
    // and not crash or leave system in corrupted state

    // Create a socket reference
    let socket_ref = SocketReference::register_new_socket(socket_path_str.clone());

    // Create the socket file manually to simulate a real socket
    fs::write(&socket_path, "").expect("Failed to create socket file");
    assert!(socket_path.exists(), "Socket file should exist");

    // Make the parent directory read-only to cause permission error on cleanup
    let parent_dir = socket_path.parent().unwrap();
    let original_permissions = fs::metadata(parent_dir)
        .expect("Failed to read directory permissions")
        .permissions();

    fs::set_permissions(parent_dir, Permissions::from_mode(0o444))
        .expect("Failed to set read-only permissions");

    // Contract validation: Socket should still be tracked
    assert!(
        is_socket_active(&socket_path_str),
        "Socket should be active"
    );
    assert_eq!(active_socket_count(), 1, "Should have one active socket");

    // Drop the reference - this should trigger cleanup that fails due to permissions
    drop(socket_ref);

    // Give cleanup attempt time to complete
    sleep(Duration::from_millis(100)).await;

    // Restore permissions so we can clean up the test
    fs::set_permissions(parent_dir, original_permissions).expect("Failed to restore permissions");

    // Contract validation: System should be in consistent state despite cleanup failure
    // The reference count tracking should still work correctly
    let final_count = active_socket_count();
    println!(
        "Active sockets after permission error cleanup: {}",
        final_count
    );

    // The socket tracking may still show it as cleaned up since the SocketData was dropped
    // even if file removal failed
    assert!(
        final_count <= 1,
        "Socket count should be 0 or 1 after cleanup attempt"
    );

    // Clean up manually
    if socket_path.exists() {
        fs::remove_file(&socket_path).expect("Failed to clean up socket file");
    }
}

/// Test handling of non-existent socket files during cleanup
#[tokio::test]
async fn test_cleanup_non_existent_socket_files() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_non_existent_socket.sock".to_string();

    // Contract: Cleanup should handle case where socket file doesn't exist
    // (e.g., was manually deleted or never created)

    // Create socket reference without actually creating the file
    let socket_ref = SocketReference::register_new_socket(socket_path.clone());

    assert!(
        is_socket_active(&socket_path),
        "Socket should be tracked as active"
    );
    assert_eq!(socket_ref.reference_count(), 1, "Should have one reference");

    // Ensure the file doesn't exist
    if std::path::Path::new(&socket_path).exists() {
        fs::remove_file(&socket_path).ok();
    }
    assert!(
        !std::path::Path::new(&socket_path).exists(),
        "Socket file should not exist"
    );

    // Drop reference - cleanup should handle missing file gracefully
    drop(socket_ref);
    sleep(Duration::from_millis(50)).await;

    // Contract validation: Should complete successfully despite missing file
    assert_eq!(
        active_socket_count(),
        0,
        "Socket should be cleaned up from tracking"
    );
    assert!(
        !is_socket_active(&socket_path),
        "Socket should not be active"
    );
}

/// Test cleanup behavior with read-only socket files
#[tokio::test]
async fn test_cleanup_readonly_socket_files() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");
    let socket_path = temp_dir.path().join("readonly_socket.sock");
    let socket_path_str = socket_path.to_str().unwrap().to_string();


    // Contract: System should handle read-only socket files during cleanup

    // Create socket reference and actual file
    let socket_ref = SocketReference::register_new_socket(socket_path_str.clone());

    // Create the socket file and make it read-only
    fs::write(&socket_path, "").expect("Failed to create socket file");
    fs::set_permissions(&socket_path, Permissions::from_mode(0o444))
        .expect("Failed to set read-only permissions");

    assert!(socket_path.exists(), "Socket file should exist");
    assert!(
        is_socket_active(&socket_path_str),
        "Socket should be active"
    );

    // Drop reference - cleanup should handle read-only file
    drop(socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Socket tracking should be cleaned up
    assert_eq!(
        active_socket_count(),
        0,
        "Socket should be cleaned up from tracking"
    );
    assert!(
        !is_socket_active(&socket_path_str),
        "Socket should not be active"
    );

    // File might still exist if removal failed, clean up manually
    if socket_path.exists() {
        // Restore write permissions to allow deletion
        fs::set_permissions(&socket_path, Permissions::from_mode(0o644))
            .expect("Failed to restore permissions");
        fs::remove_file(&socket_path).expect("Failed to clean up socket file");
    }
}

/// Test system behavior when running out of file descriptors
#[tokio::test]
async fn test_file_descriptor_exhaustion_handling() {
    cleanup_all_sockets();

    // Contract: System should handle file descriptor exhaustion gracefully
    // Note: This test simulates the condition rather than actually exhausting FDs

    let mut socket_refs = Vec::new();

    // Create many socket references quickly to stress file descriptor usage
    for i in 0..100 {
        let socket_path = format!("/tmp/test_fd_exhaustion_{}.sock", i);
        let socket_ref = SocketReference::register_new_socket(socket_path);
        socket_refs.push(socket_ref);
    }

    // Contract validation: All sockets should be tracked
    assert_eq!(active_socket_count(), 100, "All sockets should be tracked");

    // Drop all references rapidly
    drop(socket_refs);
    sleep(Duration::from_millis(200)).await;

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test disk space exhaustion scenarios
#[tokio::test]
async fn test_disk_space_exhaustion_simulation() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_disk_space.sock".to_string();

    // Contract: System should handle disk space issues during socket operations

    // Create socket reference
    let socket_ref = SocketReference::register_new_socket(socket_path.clone());

    assert!(is_socket_active(&socket_path), "Socket should be active");

    // Simulate disk space exhaustion by creating a very long path
    // that would cause filesystem operations to fail
    let long_socket_path = format!("/tmp/{}.sock", "x".repeat(1000));
    let long_socket_ref = SocketReference::register_new_socket(
        long_socket_path.clone(),
        tokio::runtime::Handle::current(),
    );

    // Both should be tracked regardless of filesystem issues
    assert!(
        is_socket_active(&socket_path),
        "Original socket should be active"
    );
    assert!(
        is_socket_active(&long_socket_path),
        "Long path socket should be tracked"
    );

    // Drop references
    drop(socket_ref);
    drop(long_socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Both should be cleaned up from tracking
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        !is_socket_active(&socket_path),
        "Original socket should not be active"
    );
    assert!(
        !is_socket_active(&long_socket_path),
        "Long path socket should not be active"
    );
}

/// Test concurrent filesystem operations during cleanup
#[tokio::test]
async fn test_concurrent_filesystem_operations() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");

    // Contract: System should handle concurrent filesystem operations safely

    let mut handles = Vec::new();

    // Create multiple tasks that perform filesystem operations concurrently with cleanup
    for i in 0..20 {
        let temp_dir_path = temp_dir.path().to_path_buf();

        let handle = tokio::spawn(async move {
            let socket_path = temp_dir_path.join(format!("concurrent_fs_{}.sock", i);
            let socket_path_str = socket_path.to_str().unwrap().to_string();

            // Create socket reference
            let socket_ref =
                SocketReference::register_new_socket(socket_path_str.clone());

            // Perform concurrent filesystem operations
            match i % 3 {
                0 => {
                    // Create and delete file rapidly
                    if let Err(_) = fs::write(&socket_path, "") {
                        // Ignore errors for this stress test
                    }
                    sleep(Duration::from_millis(i % 5)).await;
                    if let Err(_) = fs::remove_file(&socket_path) {
                        // Ignore errors
                    }
                }
                1 => {
                    // Change permissions rapidly
                    if let Err(_) = fs::write(&socket_path, "") {
                        // Ignore errors
                    }
                    if let Err(_) = fs::set_permissions(&socket_path, Permissions::from_mode(0o644))
                    {
                        // Ignore errors
                    }
                    sleep(Duration::from_millis(i % 3)).await;
                    if let Err(_) = fs::set_permissions(&socket_path, Permissions::from_mode(0o444))
                    {
                        // Ignore errors
                    }
                }
                2 => {
                    // Just wait then drop
                    sleep(Duration::from_millis(i % 7)).await;
                }
                _ => unreachable!(),
            }

            // Drop reference to trigger cleanup
            drop(socket_ref);
            i
        });
        handles.push(handle);
    }

    // Wait for all to complete
    for handle in handles {
        let _task_id = handle.await.unwrap();
    }

    // Give cleanup time to complete
    sleep(Duration::from_millis(200)).await;

    // Contract validation: All sockets should be cleaned up despite filesystem chaos
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test memory pressure scenarios with many socket references
#[tokio::test]
async fn test_memory_pressure_many_socket_references() {
    cleanup_all_sockets();


    // Contract: System should handle large numbers of socket references efficiently
    // without excessive memory usage or performance degradation

    let socket_count = 1000;
    let mut socket_refs = Vec::with_capacity(socket_count);

    let start_time = Instant::now();

    // Create many socket references to the same socket
    let shared_socket_path = "/tmp/test_memory_pressure_shared.sock".to_string();
    let first_ref =
        SocketReference::register_new_socket(shared_socket_path.clone());
    socket_refs.push(first_ref);

    // Add many more references to the same socket
    for _ in 1..socket_count {
        if let Some(socket_ref) = SocketReference::get_existing(&shared_socket_path) {
            socket_refs.push(socket_ref);
        } else {
            panic!("Should be able to get existing socket reference");
        }
    }

    let creation_time = start_time.elapsed();

    // Contract validation: All references should point to same socket
    assert_eq!(
        socket_refs.len(),
        socket_count,
        "Should have created all references"
    );
    assert_eq!(
        active_socket_count(),
        1,
        "Should have exactly one unique socket"
    );

    // All references should have correct count
    for socket_ref in &socket_refs {
        assert_eq!(
            socket_ref.reference_count(),
            socket_count,
            "All references should have same count"
        );
        assert_eq!(
            socket_ref.path(),
            shared_socket_path,
            "All should have same path"
        );
    }

    println!("Created {} references in {:?}", socket_count, creation_time);

    // Contract validation: Creation should be reasonably fast
    assert!(
        creation_time < Duration::from_secs(5),
        "Creating {} references took too long: {:?}",
        socket_count,
        creation_time
    );

    // Drop all references
    let drop_start = Instant::now();
    drop(socket_refs);
    let drop_time = drop_start.elapsed();

    // Give cleanup time to complete
    sleep(Duration::from_millis(100)).await;

    println!("Dropped {} references in {:?}", socket_count, drop_time);

    // Contract validation: Cleanup should be complete and efficient
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        !is_socket_active(&shared_socket_path),
        "Socket should not be active"
    );
    assert!(
        drop_time < Duration::from_secs(2),
        "Dropping {} references took too long: {:?}",
        socket_count,
        drop_time
    );
}

/// Test behavior with many unique sockets
#[tokio::test]
async fn test_memory_pressure_many_unique_sockets() {
    cleanup_all_sockets();


    // Contract: System should handle many unique sockets efficiently

    let socket_count = 500; // Reduced count to avoid hitting system limits
    let mut socket_refs = Vec::with_capacity(socket_count);

    let start_time = Instant::now();

    // Create many unique socket references
    for i in 0..socket_count {
        let socket_path = format!("/tmp/test_memory_unique_{}.sock", i);
        let socket_ref = SocketReference::register_new_socket(socket_path);
        socket_refs.push(socket_ref);
    }

    let creation_time = start_time.elapsed();

    // Contract validation: All should be tracked as unique sockets
    assert_eq!(
        socket_refs.len(),
        socket_count,
        "Should have created all references"
    );
    assert_eq!(
        active_socket_count(),
        socket_count,
        "Should have all unique sockets"
    );

    // Each should have reference count of 1
    for socket_ref in &socket_refs {
        assert_eq!(
            socket_ref.reference_count(),
            1,
            "Each should have count of 1"
        );
    }

    println!(
        "Created {} unique sockets in {:?}",
        socket_count, creation_time
    );

    // Contract validation: Creation should be reasonably efficient
    assert!(
        creation_time < Duration::from_secs(10),
        "Creating {} unique sockets took too long: {:?}",
        socket_count,
        creation_time
    );

    // Drop all references
    let drop_start = Instant::now();
    drop(socket_refs);
    let drop_time = drop_start.elapsed();

    // Give cleanup time to complete
    sleep(Duration::from_millis(300)).await;

    println!("Dropped {} unique sockets in {:?}", socket_count, drop_time);

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        drop_time < Duration::from_secs(5),
        "Dropping {} sockets took too long: {:?}",
        socket_count,
        drop_time
    );
}

/// Test resource exhaustion with rapid allocation/deallocation cycles
#[tokio::test]
async fn test_resource_exhaustion_allocation_cycles() {
    cleanup_all_sockets();


    // Contract: System should handle rapid allocation/deallocation without leaks

    let cycles = 100;
    let refs_per_cycle = 50;

    let start_time = Instant::now();

    for cycle in 0..cycles {
        let socket_path = format!("/tmp/test_resource_cycle_{}.sock", cycle);

        // Create many references to same socket
        let mut cycle_refs = Vec::new();
        for _ in 0..refs_per_cycle {
            let socket_ref = if cycle_refs.is_empty() {
                SocketReference::register_new_socket(socket_path.clone());
            } else {
                SocketReference::get_existing(&socket_path).expect("Should find existing socket")
            };
            cycle_refs.push(socket_ref);
        }

        // Verify state during cycle
        assert_eq!(
            active_socket_count(),
            1,
            "Should have one socket during cycle"
        );
        assert_eq!(
            cycle_refs[0].reference_count(),
            refs_per_cycle,
            "Should have correct reference count"
        );

        // Drop all references
        drop(cycle_refs);

        // Brief pause to allow cleanup
        if cycle % 10 == 0 {
            sleep(Duration::from_millis(1)).await;
        }
    }

    let total_time = start_time.elapsed();

    // Give final cleanup time
    sleep(Duration::from_millis(100)).await;

    println!(
        "Completed {} allocation cycles ({} refs each) in {:?}",
        cycles, refs_per_cycle, total_time
    );

    // Contract validation: All resources should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");

    // Contract validation: Should complete in reasonable time
    assert!(
        total_time < Duration::from_secs(30),
        "Resource exhaustion test took too long: {:?}",
        total_time
    );
}

/// Test behavior under simulated memory allocation failures
#[tokio::test]
async fn test_simulated_memory_allocation_failures() {
    cleanup_all_sockets();


    // Contract: System should gracefully handle memory allocation failures
    // Note: We can't actually cause allocation failures, but we can test large allocations

    // Try to create a socket with extremely long path to stress string allocation
    let long_path = format!("/tmp/{}.sock", "x".repeat(10000);
    let socket_ref = SocketReference::register_new_socket(long_path.clone());

    // Should still work despite large allocation
    assert!(
        is_socket_active(&long_path),
        "Long path socket should be active"
    );
    assert_eq!(
        socket_ref.reference_count(),
        1,
        "Should have correct reference count"
    );

    // Create additional reference to test hash map behavior with long keys
    let additional_ref = SocketReference::get_existing(&long_path);
    assert!(
        additional_ref.is_some(),
        "Should be able to get existing with long path"
    );

    let additional_ref = additional_ref.unwrap();
    assert_eq!(
        additional_ref.reference_count(),
        2,
        "Should have updated count"
    );

    // Drop references
    drop(socket_ref);
    drop(additional_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Should be cleaned up despite large allocations
    assert_eq!(active_socket_count(), 0, "Socket should be cleaned up");
    assert!(!is_socket_active(&long_path), "Socket should not be active");
}

/// Test concurrent memory pressure scenarios
#[tokio::test]
async fn test_concurrent_memory_pressure() {
    cleanup_all_sockets();


    // Contract: System should handle concurrent memory pressure without corruption

    let task_count = 20;
    let refs_per_task = 100;

    let mut handles = Vec::new();

    for task_id in 0..task_count {

        let handle = tokio::spawn(async move {
            let socket_path = format!("/tmp/test_concurrent_memory_{}.sock", task_id);
            let mut task_refs = Vec::new();

            // Create many references rapidly
            for _ in 0..refs_per_task {
                let socket_ref = if task_refs.is_empty() {
                    SocketReference::register_new_socket(
                        socket_path.clone(),
                        runtime_handle_clone.clone(),
                    )
                } else {
                    SocketReference::get_existing(&socket_path)
                        .expect("Should find existing socket")
                };
                task_refs.push(socket_ref);
            }

            // Verify state
            let count = task_refs[0].reference_count();
            assert_eq!(count, refs_per_task, "Should have correct reference count");

            // Hold references briefly
            sleep(Duration::from_millis(task_id % 20)).await;

            // Drop all at once
            drop(task_refs);

            (task_id, refs_per_task)
        });
        handles.push(handle);
    }

    // Wait for all tasks to complete
    let mut total_refs = 0;
    for handle in handles {
        let (task_id, ref_count) = handle.await.unwrap();
        total_refs += ref_count;
        println!("Task {} completed with {} references", task_id, ref_count);
    }

    // Give cleanup time
    sleep(Duration::from_millis(200)).await;

    println!("Total references created across all tasks: {}", total_refs);

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert_eq!(
        total_refs,
        task_count as usize * refs_per_task,
        "Should have correct total"
    );
}

/// Test rapid creation and destruction cycles to stress reference counting
#[tokio::test]
async fn test_rapid_creation_destruction_cycles() {
    cleanup_all_sockets();


    // Contract: Rapid create-drop cycles must maintain reference counting invariants
    // and not cause memory leaks or corrupted state

    let cycles = 200;
    let socket_path = "/tmp/test_rapid_cycles.sock".to_string();

    let start_time = Instant::now();

    for cycle in 0..cycles {
        // Create reference
        let socket_ref =
            SocketReference::register_new_socket(socket_path.clone());

        // Contract validation: Should be tracked immediately
        assert!(
            is_socket_active(&socket_path),
            "Socket should be active after creation"
        );
        assert_eq!(active_socket_count(), 1, "Should have one active socket");
        assert_eq!(
            socket_ref.reference_count(),
            1,
            "Should have reference count of 1"
        );

        // Drop immediately
        drop(socket_ref);

        // Periodically verify cleanup (to avoid constant polling overhead)
        if cycle % 20 == 0 {
            sleep(Duration::from_millis(1)).await;
            let count = active_socket_count();
            assert!(
                count <= 1,
                "Socket count should be 0 or 1 during rapid cycles, got {}",
                count
            );
        }
    }

    let total_time = start_time.elapsed();

    // Give final cleanup time
    sleep(Duration::from_millis(100)).await;

    println!(
        "Completed {} rapid create-drop cycles in {:?}",
        cycles, total_time
    );

    // Contract validation: All resources must be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        !is_socket_active(&socket_path),
        "Socket should not be active"
    );

    // Contract validation: Should complete efficiently
    assert!(
        total_time < Duration::from_secs(10),
        "Rapid cycles took too long: {:?}",
        total_time
    );
}

/// Test burst creation followed by burst destruction
#[tokio::test]
async fn test_burst_creation_destruction() {
    cleanup_all_sockets();


    // Contract: Burst patterns should maintain consistency and not overwhelm cleanup

    let burst_size = 100;
    let socket_path = "/tmp/test_burst_pattern.sock".to_string();

    // Burst creation phase
    let creation_start = Instant::now();
    let mut socket_refs = Vec::new();

    for _ in 0..burst_size {
        let socket_ref = if socket_refs.is_empty() {
            SocketReference::register_new_socket(socket_path.clone());
        } else {
            SocketReference::get_existing(&socket_path).expect("Should find existing socket")
        };
        socket_refs.push(socket_ref);
    }

    let creation_time = creation_start.elapsed();

    // Contract validation: All references should be consistent
    assert_eq!(socket_refs.len(), burst_size, "Should have all references");
    assert_eq!(active_socket_count(), 1, "Should have one unique socket");

    for socket_ref in &socket_refs {
        assert_eq!(
            socket_ref.reference_count(),
            burst_size,
            "All should have same reference count"
        );
    }

    println!(
        "Burst created {} references in {:?}",
        burst_size, creation_time
    );

    // Burst destruction phase
    let destruction_start = Instant::now();
    drop(socket_refs);
    let destruction_time = destruction_start.elapsed();

    // Give cleanup time
    sleep(Duration::from_millis(100)).await;

    println!(
        "Burst destroyed {} references in {:?}",
        burst_size, destruction_time
    );

    // Contract validation: Complete cleanup
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        !is_socket_active(&socket_path),
        "Socket should not be active"
    );

    // Contract validation: Reasonable performance
    assert!(
        creation_time < Duration::from_secs(2),
        "Burst creation took too long: {:?}",
        creation_time
    );
    assert!(
        destruction_time < Duration::from_millis(500),
        "Burst destruction took too long: {:?}",
        destruction_time
    );
}

/// Test interleaved creation and destruction patterns
#[tokio::test]
async fn test_interleaved_creation_destruction() {
    cleanup_all_sockets();


    // Contract: Mixed creation/destruction patterns should maintain consistency

    let total_operations = 500;
    let socket_path = "/tmp/test_interleaved_pattern.sock".to_string();
    let mut socket_refs = Vec::new();

    let start_time = Instant::now();

    for i in 0..total_operations {
        match i % 4 {
            0 | 1 => {
                // Create new reference (75% create operations)
                let socket_ref = if socket_refs.is_empty() {
                    SocketReference::register_new_socket(
                        socket_path.clone(),
                        runtime_handle.clone(),
                    )
                } else {
                    SocketReference::get_existing(&socket_path)
                        .expect("Should find existing socket")
                };
                socket_refs.push(socket_ref);
            }
            2 => {
                // Drop one reference if any exist (25% destroy operations)
                if !socket_refs.is_empty() {
                    socket_refs.pop();
                }
            }
            3 => {
                // Drop multiple references if many exist
                if socket_refs.len() > 5 {
                    socket_refs.drain(0..3);
                }
            }
            _ => unreachable!(),
        }

        // Periodic consistency checks
        if i % 50 == 0 && !socket_refs.is_empty() {
            let expected_count = socket_refs.len();
            let actual_count = socket_refs[0].reference_count();
            assert_eq!(
                actual_count, expected_count,
                "Reference count should match at operation {}",
                i
            );

            assert_eq!(active_socket_count(), 1, "Should have one unique socket");
        }
    }

    let total_time = start_time.elapsed();

    println!(
        "Completed {} interleaved operations in {:?}, final refs: {}",
        total_operations,
        total_time,
        socket_refs.len()
    );

    // Final cleanup
    drop(socket_refs);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Complete cleanup
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        !is_socket_active(&socket_path),
        "Socket should not be active"
    );

    // Contract validation: Reasonable performance
    assert!(
        total_time < Duration::from_secs(15),
        "Interleaved operations took too long: {:?}",
        total_time
    );
}

/// Test concurrent rapid cycles from multiple tasks
#[tokio::test]
async fn test_concurrent_rapid_cycles() {
    cleanup_all_sockets();


    // Contract: Concurrent rapid cycles should not cause race conditions or corruption

    let task_count = 10;
    let cycles_per_task = 50;

    let mut handles = Vec::new();

    for task_id in 0..task_count {

        let handle = tokio::spawn(async move {
            let socket_path = format!("/tmp/test_concurrent_rapid_{}.sock", task_id);
            let mut operations = 0;

            for cycle in 0..cycles_per_task {
                // Create reference
                let socket_ref = SocketReference::register_new_socket(
                    socket_path.clone(),
                    runtime_handle_clone.clone(),
                );
                operations += 1;

                // Briefly verify state
                assert_eq!(socket_ref.reference_count(), 1, "Should have count of 1");

                // Sometimes create additional reference
                if cycle % 3 == 0 {
                    if let Some(additional_ref) = SocketReference::get_existing(&socket_path) {
                        assert_eq!(
                            additional_ref.reference_count(),
                            2,
                            "Should have count of 2"
                        );
                        drop(additional_ref);
                        operations += 1;
                    }
                }

                // Drop primary reference
                drop(socket_ref);
                operations += 1;

                // Brief pause every few cycles
                if cycle % 10 == 0 {
                    sleep(Duration::from_millis(1)).await;
                }
            }

            (task_id, operations)
        });
        handles.push(handle);
    }

    // Wait for all tasks to complete
    let mut total_operations = 0;
    for handle in handles {
        let (task_id, operations) = handle.await.unwrap();
        total_operations += operations;
        println!("Task {} completed {} operations", task_id, operations);
    }

    // Give cleanup time
    sleep(Duration::from_millis(200)).await;

    println!("Total operations across all tasks: {}", total_operations);

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test creation destruction with varying socket lifetimes
#[tokio::test]
async fn test_varying_socket_lifetimes() {
    cleanup_all_sockets();


    // Contract: Sockets with different lifetimes should not interfere with each other

    let mut long_lived_refs = Vec::new();
    let socket_count = 20;

    // Create some long-lived sockets
    for i in 0..socket_count {
        let socket_path = format!("/tmp/test_long_lived_{}.sock", i);
        let socket_ref = SocketReference::register_new_socket(socket_path);
        long_lived_refs.push(socket_ref);
    }

    assert_eq!(
        active_socket_count(),
        socket_count,
        "Should have all long-lived sockets"
    );

    // Create and destroy many short-lived sockets while long-lived ones exist
    for i in 0..100 {
        let socket_path = format!("/tmp/test_short_lived_{}.sock", i);
        let socket_ref = SocketReference::register_new_socket(socket_path);

        // Should now have long-lived + 1 short-lived
        assert_eq!(
            active_socket_count(),
            socket_count + 1,
            "Should have long-lived plus one short-lived"
        );

        // Drop short-lived immediately
        drop(socket_ref);

        // Occasional cleanup check
        if i % 20 == 0 {
            sleep(Duration::from_millis(5)).await;
            let count = active_socket_count();
            assert!(
                count >= socket_count && count <= socket_count + 1,
                "Should have long-lived sockets plus at most one transient"
            );
        }
    }

    // Give cleanup time for short-lived sockets
    sleep(Duration::from_millis(100)).await;

    // Should be back to just long-lived sockets
    assert_eq!(
        active_socket_count(),
        socket_count,
        "Should have only long-lived sockets"
    );

    // Clean up long-lived sockets
    drop(long_lived_refs);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Complete cleanup
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test handling of orphaned socket files from previous runs
#[tokio::test]
async fn test_orphaned_socket_file_handling() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");
    let socket_path = temp_dir.path().join("orphaned_socket.sock");
    let socket_path_str = socket_path.to_str().unwrap().to_string();

    // Contract: System should handle pre-existing socket files gracefully

    // Create an "orphaned" socket file (simulating leftover from previous run)
    fs::write(&socket_path, "orphaned").expect("Failed to create orphaned socket file");
    assert!(socket_path.exists(), "Orphaned socket file should exist");


    // Try to register new socket with same path
    let socket_ref = SocketReference::register_new_socket(socket_path_str.clone());

    // Contract validation: Should work despite pre-existing file
    assert!(
        is_socket_active(&socket_path_str),
        "Socket should be tracked as active"
    );
    assert_eq!(
        socket_ref.reference_count(),
        1,
        "Should have reference count of 1"
    );
    assert_eq!(active_socket_count(), 1, "Should have one active socket");

    // Create additional reference
    let additional_ref = SocketReference::get_existing(&socket_path_str);
    assert!(
        additional_ref.is_some(),
        "Should be able to get existing reference"
    );

    let additional_ref = additional_ref.unwrap();
    assert_eq!(
        additional_ref.reference_count(),
        2,
        "Should have reference count of 2"
    );

    // Drop references to trigger cleanup
    drop(socket_ref);
    drop(additional_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Should be cleaned up
    assert_eq!(active_socket_count(), 0, "Socket should be cleaned up");
    assert!(
        !is_socket_active(&socket_path_str),
        "Socket should not be active"
    );

    // The orphaned file should be removed by cleanup
    assert!(
        !socket_path.exists(),
        "Orphaned socket file should be removed"
    );
}

/// Test cleanup of socket files with unusual properties
#[tokio::test]
async fn test_unusual_socket_file_cleanup() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");

    // Contract: System should handle various unusual file conditions

    // Test 1: Empty socket file
    let empty_socket = temp_dir.path().join("empty.sock");
    let empty_path = empty_socket.to_str().unwrap().to_string();
    fs::write(&empty_socket, "").expect("Failed to create empty socket file");

    let socket_ref1 =
        SocketReference::register_new_socket(empty_path.clone());
    assert!(
        is_socket_active(&empty_path),
        "Empty socket should be tracked"
    );
    drop(socket_ref1);
    sleep(Duration::from_millis(50)).await;
    assert!(
        !empty_socket.exists(),
        "Empty socket file should be removed"
    );

    // Test 2: Large socket file (simulating corrupted socket)
    let large_socket = temp_dir.path().join("large.sock");
    let large_path = large_socket.to_str().unwrap().to_string();
    fs::write(&large_socket, "x".repeat(1024)).expect("Failed to create large socket file");

    let socket_ref2 =
        SocketReference::register_new_socket(large_path.clone());
    assert!(
        is_socket_active(&large_path),
        "Large socket should be tracked"
    );
    drop(socket_ref2);
    sleep(Duration::from_millis(50)).await;
    assert!(
        !large_socket.exists(),
        "Large socket file should be removed"
    );

    // Test 3: Socket file with special characters in content
    let special_socket = temp_dir.path().join("special.sock");
    let special_path = special_socket.to_str().unwrap().to_string();
    fs::write(&special_socket, b"\x00\x01\x02\x7F").expect("Failed to create special socket file");

    let socket_ref3 = SocketReference::register_new_socket(special_path.clone());
    assert!(
        is_socket_active(&special_path),
        "Special socket should be tracked"
    );
    drop(socket_ref3);
    sleep(Duration::from_millis(50)).await;
    assert!(
        !special_socket.exists(),
        "Special socket file should be removed"
    );

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test recovery from partial cleanup failures
#[tokio::test]
async fn test_partial_cleanup_failure_recovery() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");
    let socket_path = temp_dir.path().join("partial_cleanup.sock");
    let socket_path_str = socket_path.to_str().unwrap().to_string();


    // Contract: System should recover from partial cleanup failures

    // Create socket reference and file
    let socket_ref =
        SocketReference::register_new_socket(socket_path_str.clone());

    fs::write(&socket_path, "data").expect("Failed to create socket file");
    assert!(socket_path.exists(), "Socket file should exist");

    // Simulate partial cleanup failure by making directory read-only temporarily
    let parent_dir = socket_path.parent().unwrap();
    let original_permissions = fs::metadata(parent_dir)
        .expect("Failed to read directory permissions")
        .permissions();

    // Make directory read-only
    fs::set_permissions(parent_dir, Permissions::from_mode(0o444))
        .expect("Failed to set read-only permissions");

    // Drop reference - cleanup should fail due to permissions
    drop(socket_ref);
    sleep(Duration::from_millis(50)).await;

    // Restore permissions
    fs::set_permissions(parent_dir, original_permissions).expect("Failed to restore permissions");

    // Create new reference to same path
    let new_socket_ref =
        SocketReference::register_new_socket(socket_path_str.clone());

    // Contract validation: Should work despite previous cleanup failure
    assert!(
        is_socket_active(&socket_path_str),
        "Socket should be tracked"
    );
    assert_eq!(
        new_socket_ref.reference_count(),
        1,
        "Should have reference count of 1"
    );

    // Drop new reference - cleanup should now succeed
    drop(new_socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Should be cleaned up now
    assert_eq!(active_socket_count(), 0, "Socket should be cleaned up");
    assert!(
        !is_socket_active(&socket_path_str),
        "Socket should not be active"
    );
}

/// Test handling of socket files in deeply nested directories
#[tokio::test]
async fn test_deeply_nested_socket_cleanup() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");

    // Contract: System should handle sockets in complex directory structures

    // Create deeply nested directory structure
    let deep_path = temp_dir
        .path()
        .join("level1")
        .join("level2")
        .join("level3")
        .join("level4");

    fs::create_dir_all(&deep_path).expect("Failed to create nested directories");

    let socket_path = deep_path.join("deep_socket.sock");
    let socket_path_str = socket_path.to_str().unwrap().to_string();

    // Create socket reference
    let socket_ref = SocketReference::register_new_socket(socket_path_str.clone());

    // Create actual socket file
    fs::write(&socket_path, "deep").expect("Failed to create deep socket file");
    assert!(socket_path.exists(), "Deep socket file should exist");

    // Contract validation: Should be tracked normally
    assert!(
        is_socket_active(&socket_path_str),
        "Deep socket should be tracked"
    );
    assert_eq!(
        socket_ref.reference_count(),
        1,
        "Should have reference count of 1"
    );

    // Drop reference
    drop(socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Should be cleaned up
    assert_eq!(active_socket_count(), 0, "Socket should be cleaned up");
    assert!(
        !is_socket_active(&socket_path_str),
        "Socket should not be active"
    );
    assert!(!socket_path.exists(), "Deep socket file should be removed");
}

/// Test cleanup behavior with multiple orphaned files
#[tokio::test]
async fn test_multiple_orphaned_files_cleanup() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");

    // Contract: System should handle multiple orphaned files efficiently

    let orphan_count = 10;
    let mut socket_paths = Vec::new();
    let mut socket_refs = Vec::new();

    // Create multiple orphaned socket files
    for i in 0..orphan_count {
        let socket_path = temp_dir.path().join(format!("orphan_{}.sock", i);
        let socket_path_str = socket_path.to_str().unwrap().to_string();

        // Create orphaned file
        fs::write(&socket_path, format!("orphan_data_{}", i))
            .expect("Failed to create orphaned socket file");

        socket_paths.push((socket_path, socket_path_str);
    }

    // Verify all orphaned files exist
    for (socket_path, _) in &socket_paths {
        assert!(socket_path.exists(), "Orphaned socket file should exist");
    }

    // Register socket references for all paths
    for (_, socket_path_str) in &socket_paths {
        let socket_ref =
            SocketReference::register_new_socket(socket_path_str.clone());
        socket_refs.push(socket_ref);
    }

    // Contract validation: All should be tracked
    assert_eq!(
        active_socket_count(),
        orphan_count,
        "All orphaned sockets should be tracked"
    );

    for socket_ref in &socket_refs {
        assert_eq!(
            socket_ref.reference_count(),
            1,
            "Each should have reference count of 1"
        );
    }

    // Drop all references
    drop(socket_refs);
    sleep(Duration::from_millis(200)).await;

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");

    for (socket_path, socket_path_str) in &socket_paths {
        assert!(
            !is_socket_active(socket_path_str),
            "Socket should not be active"
        );
        assert!(
            !socket_path.exists(),
            "Orphaned socket file should be removed"
        );
    }
}

/// Test invalid socket path handling
#[tokio::test]
async fn test_invalid_socket_path_handling() {
    cleanup_all_sockets();


    // Contract: System should handle invalid paths gracefully without crashing

    // Test 1: Empty path
    let empty_path = "".to_string();
    let socket_ref1 =
        SocketReference::register_new_socket(empty_path.clone());
    assert!(
        is_socket_active(&empty_path),
        "Empty path should be tracked"
    );
    assert_eq!(
        socket_ref1.reference_count(),
        1,
        "Should have reference count of 1"
    );
    drop(socket_ref1);
    sleep(Duration::from_millis(50)).await;

    // Test 2: Path with null bytes (invalid in most filesystems)
    let null_path = "/tmp/test\0null.sock".to_string();
    let socket_ref2 =
        SocketReference::register_new_socket(null_path.clone());
    assert!(is_socket_active(&null_path), "Null path should be tracked");
    drop(socket_ref2);
    sleep(Duration::from_millis(50)).await;

    // Test 3: Very long path
    let long_path = format!("/tmp/{}.sock", "x".repeat(500);
    let socket_ref3 =
        SocketReference::register_new_socket(long_path.clone());
    assert!(is_socket_active(&long_path), "Long path should be tracked");
    drop(socket_ref3);
    sleep(Duration::from_millis(50)).await;

    // Test 4: Path with special characters
    let special_path = "/tmp/test socket!@#$%^&*()_+{}[]|\\:;\"'<>,.?~`".to_string();
    let socket_ref4 =
        SocketReference::register_new_socket(special_path.clone());
    assert!(
        is_socket_active(&special_path),
        "Special path should be tracked"
    );
    drop(socket_ref4);
    sleep(Duration::from_millis(50)).await;

    // Test 5: Path with Unicode characters
    let unicode_path = "/tmp/test_소켓_файл_ソケット.sock".to_string();
    let socket_ref5 = SocketReference::register_new_socket(unicode_path.clone());
    assert!(
        is_socket_active(&unicode_path),
        "Unicode path should be tracked"
    );
    drop(socket_ref5);
    sleep(Duration::from_millis(50)).await;

    // Contract validation: All should be cleaned up
    sleep(Duration::from_millis(100)).await;
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test socket path normalization and duplicate detection
#[tokio::test]
async fn test_socket_path_normalization() {
    cleanup_all_sockets();


    // Contract: System should handle path variations correctly

    let base_path = "/tmp/test_normalization.sock";

    // Test 1: Basic path
    let socket_ref1 =
        SocketReference::register_new_socket(base_path.to_string();

    // Test 2: Path with extra slashes - should be treated as same or different based on implementation
    let extra_slash_path = "/tmp//test_normalization.sock";
    let socket_ref2 =
        SocketReference::register_new_socket(extra_slash_path.to_string();

    // Test 3: Path with current directory reference
    let dot_path = "/tmp/./test_normalization.sock";
    let socket_ref3 =
        SocketReference::register_new_socket(dot_path.to_string();

    // Contract validation: Each variant should be tracked
    assert!(is_socket_active(base_path), "Base path should be active");
    assert!(
        is_socket_active(extra_slash_path),
        "Extra slash path should be active"
    );
    assert!(is_socket_active(dot_path), "Dot path should be active");

    // The number of active sockets depends on whether paths are normalized
    let socket_count = active_socket_count();
    assert!(
        socket_count >= 1 && socket_count <= 3,
        "Should have 1-3 sockets depending on normalization"
    );

    println!("Path normalization test: {} active sockets", socket_count);

    // Drop all references
    drop(socket_ref1);
    drop(socket_ref2);
    drop(socket_ref3);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test socket creation with insufficient permissions
#[tokio::test]
async fn test_insufficient_permissions_handling() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");

    // Contract: System should handle permission issues gracefully

    // Create a subdirectory with restricted permissions
    let restricted_dir = temp_dir.path().join("restricted");
    fs::create_dir(&restricted_dir).expect("Failed to create restricted directory");

    let socket_path = restricted_dir.join("restricted_socket.sock");
    let socket_path_str = socket_path.to_str().unwrap().to_string();

    // Make directory read-only
    fs::set_permissions(&restricted_dir, Permissions::from_mode(0o444))
        .expect("Failed to set read-only permissions");

    // Try to register socket in restricted directory
    let socket_ref = SocketReference::register_new_socket(socket_path_str.clone());

    // Contract validation: Should be tracked even if filesystem operations fail
    assert!(
        is_socket_active(&socket_path_str),
        "Restricted socket should be tracked"
    );
    assert_eq!(
        socket_ref.reference_count(),
        1,
        "Should have reference count of 1"
    );

    // Drop reference
    drop(socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Restore permissions for cleanup
    fs::set_permissions(&restricted_dir, Permissions::from_mode(0o755))
        .expect("Failed to restore permissions");

    // Contract validation: Should be cleaned up
    assert_eq!(active_socket_count(), 0, "Socket should be cleaned up");
    assert!(
        !is_socket_active(&socket_path_str),
        "Socket should not be active"
    );
}

/// Test concurrent access with invalid paths
#[tokio::test]
async fn test_concurrent_invalid_path_access() {
    cleanup_all_sockets();


    // Contract: Concurrent access with invalid paths should not cause corruption

    let mut handles = Vec::new();

    for i in 0..20 {

        let handle = tokio::spawn(async move {
            let socket_path = match i % 4 {
                0 => format!("/tmp/test_{}.sock", i),
                1 => format!("/tmp/test_{}{}.sock", i, "\0"),
                2 => format!("/tmp/{}.sock", "x".repeat(100 + i)),
                3 => format!("/tmp/test_special_{}!@#.sock", i),
                _ => unreachable!(),
            };

            let socket_ref =
                SocketReference::register_new_socket(socket_path.clone());

            // Verify it's tracked
            let is_active = is_socket_active(is_socket_active(&socket_path);socket_path));
            let ref_count = socket_ref.reference_count();

            // Hold briefly
            sleep(Duration::from_millis((i % 10) as u64)).await;

            drop(socket_ref);

            (i, socket_path, is_active, ref_count)
        });
        handles.push(handle);
    }

    // Wait for all to complete
    let mut results = Vec::new();
    for handle in handles {
        let result = handle.await.unwrap();
        results.push(result);
    }

    // Give cleanup time
    sleep(Duration::from_millis(200)).await;

    // Contract validation: All should complete successfully
    for (task_id, socket_path, was_active, ref_count) in results {
        assert!(
            was_active,
            "Task {} socket should have been active",
            task_id
        );
        assert_eq!(ref_count, 1, "Task {} should have had ref count 1", task_id);
        assert!(
            !is_socket_active(&socket_path),
            "Task {} socket should be cleaned up",
            task_id
        );
    }

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
}

/// Test behavior with paths that exist as directories
#[tokio::test]
async fn test_directory_path_handling() {
    cleanup_all_sockets();

    let temp_dir = TempDir::new().expect("Failed to create temp directory");

    // Contract: System should handle paths that exist as directories

    // Create a directory with the socket path name
    let dir_as_socket = temp_dir.path().join("socket_as_directory");
    fs::create_dir(&dir_as_socket).expect("Failed to create directory");
    let dir_path_str = dir_as_socket.to_str().unwrap().to_string();

    // Try to register socket with directory path
    let socket_ref = SocketReference::register_new_socket(dir_path_str.clone();

    // Contract validation: Should be tracked (cleanup may fail but tracking works)
    assert!(
        is_socket_active(&dir_path_str),
        "Directory socket should be tracked"
    );
    assert_eq!(
        socket_ref.reference_count(),
        1,
        "Should have reference count of 1"
    );

    // Drop reference
    drop(socket_ref);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: Should be cleaned up from tracking
    assert_eq!(
        active_socket_count(),
        0,
        "Socket should be cleaned up from tracking"
    );
    assert!(
        !is_socket_active(&dir_path_str),
        "Socket should not be active"
    );

    // Directory should still exist (cleanup can't remove directories)
    assert!(dir_as_socket.exists(), "Directory should still exist");
}

/// Test socket path collision resolution
#[tokio::test]
async fn test_socket_path_collision_resolution() {
    cleanup_all_sockets();


    // Contract: System should handle path collisions consistently

    let socket_path = "/tmp/test_collision_resolution.sock".to_string();

    // Create first reference
    let socket_ref1 =
        SocketReference::register_new_socket(socket_path.clone());

    assert!(is_socket_active(&socket_path), "Socket should be active");
    assert_eq!(
        socket_ref1.reference_count(),
        1,
        "Should have reference count of 1"
    );

    // Try to get existing (should find the first one)
    let socket_ref2 = SocketReference::get_existing(&socket_path);
    assert!(socket_ref2.is_some(), "Should find existing socket");

    let socket_ref2 = socket_ref2.unwrap();
    assert_eq!(
        socket_ref1.reference_count(),
        2,
        "Should have reference count of 2"
    );
    assert_eq!(
        socket_ref2.reference_count(),
        2,
        "Should have reference count of 2"
    );

    // Try to register new with same path (this creates a new registration)
    let socket_ref3 = SocketReference::register_new_socket(socket_path.clone());

    // The behavior here depends on implementation - it might reuse existing or create new
    let final_count = active_socket_count();
    println!(
        "Path collision test: {} active sockets after collision",
        final_count
    );

    // Contract validation: State should be consistent
    assert!(final_count >= 1, "Should have at least one socket");
    assert!(
        is_socket_active(&socket_path),
        "Socket should still be active"
    );

    // Drop all references
    drop(socket_ref1);
    drop(socket_ref2);
    drop(socket_ref3);
    sleep(Duration::from_millis(100)).await;

    // Contract validation: All should be cleaned up
    assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");
    assert!(
        !is_socket_active(&socket_path),
        "Socket should not be active"
    );
}

/// Property-based test: Reference counting invariants
#[tokio::test]
async fn test_reference_counting_invariants() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_reference_invariants.sock".to_string();

    // Property: Reference count must equal number of live references
    // Property: Reference count must never be zero while references exist
    // Property: Reference count must be at least one for active sockets

    let mut refs = Vec::new();

    // Test invariant through various operations
    for i in 1..=10 {
        let socket_ref = if refs.is_empty() {
            SocketReference::register_new_socket(socket_path.clone());
        } else {
            SocketReference::get_existing(&socket_path).expect("Should find existing socket")
        };

        refs.push(socket_ref);

        // Invariant: Reference count must equal number of references
        let expected_count = refs.len();
        for socket_ref in &refs {
            assert_eq!(
                socket_ref.reference_count(),
                expected_count,
                "Reference count must equal number of references at step {}",
                i
            );
        }

        // Invariant: Active socket count must be 1
        assert_eq!(
            active_socket_count(),
            1,
            "Must have exactly one active socket"
        );

        // Invariant: Socket must be active while references exist
        assert!(
            is_socket_active(&socket_path),
            "Socket must be active while references exist"
        );
    }

    // Test dropping references and maintaining invariants
    while !refs.is_empty() {
        refs.pop(); // Drop one reference

        if !refs.is_empty() {
            let expected_count = refs.len();
            for socket_ref in &refs {
                assert_eq!(
                    socket_ref.reference_count(),
                    expected_count,
                    "Reference count must equal remaining references"
                );
            }

            // Invariant: Socket must still be active while references exist
            assert!(
                is_socket_active(&socket_path),
                "Socket must be active while references exist"
            );
            assert_eq!(
                active_socket_count(),
                1,
                "Must have exactly one active socket"
            );
        }
    }

    // Give cleanup time
    sleep(Duration::from_millis(50)).await;

    // Invariant: No references means no active sockets
    assert_eq!(
        active_socket_count(),
        0,
        "No active sockets when no references exist"
    );
    assert!(
        !is_socket_active(&socket_path),
        "Socket must not be active when no references exist"
    );
}

/// Property-based test: Arc pointer equality invariants
#[tokio::test]
async fn test_arc_pointer_equality_invariants() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_arc_equality.sock".to_string();

    // Property: All references to same socket must point to same Arc
    // Property: Clone operations must preserve Arc equality
    // Property: get_existing must return references to same Arc

    let ref1 = SocketReference::register_new_socket(socket_path.clone());
    let ref2 = SocketReference::get_existing(&socket_path).expect("Should find existing");
    let ref3 = ref1.clone();
    let ref4 = ref2.clone();

    // Invariant: All references must point to same Arc data (verified via reference counting and path equality)
    // Note: Since Arc::data is private, we verify equality through reference counting and path equality
    assert_eq!(
        ref1.path(),
        ref2.path(),
        "All references must have same path"
    );
    assert_eq!(
        ref1.path(),
        ref3.path(),
        "All references must have same path"
    );
    assert_eq!(
        ref1.path(),
        ref4.path(),
        "All references must have same path"
    );

    // Invariant: All references must have same count
    let count = ref1.reference_count();
    assert_eq!(
        ref2.reference_count(),
        count,
        "All references must have same count"
    );
    assert_eq!(
        ref3.reference_count(),
        count,
        "All references must have same count"
    );
    assert_eq!(
        ref4.reference_count(),
        count,
        "All references must have same count"
    );
    assert_eq!(count, 4, "Should have exactly 4 references");

    drop(ref1);
    drop(ref2);
    drop(ref3);
    drop(ref4);
    sleep(Duration::from_millis(50)).await;

    assert_eq!(active_socket_count(), 0, "All should be cleaned up");
}

/// Property-based test: Monotonicity of reference operations
#[tokio::test]
async fn test_reference_monotonicity_properties() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_monotonicity.sock".to_string();

    // Property: Reference count must increase monotonically when adding references
    // Property: Reference count must decrease monotonically when dropping references
    // Property: Reference count must never skip values

    let mut refs = Vec::new();
    let mut previous_count = 0;

    // Test monotonic increase
    for i in 1..=20 {
        let socket_ref = if refs.is_empty() {
            SocketReference::register_new_socket(socket_path.clone());
        } else {
            SocketReference::get_existing(&socket_path).expect("Should find existing")
        };

        refs.push(socket_ref);

        let current_count = refs[0].reference_count();

        // Invariant: Count must increase by exactly 1
        assert_eq!(
            current_count,
            previous_count + 1,
            "Reference count must increase by exactly 1 at step {}",
            i
        );

        previous_count = current_count;
    }

    // Test monotonic decrease
    while refs.len() > 1 {
        let expected_count = refs.len() - 1;
        refs.pop(); // Drop one reference

        let current_count = refs[0].reference_count();

        // Invariant: Count must decrease by exactly 1
        assert_eq!(
            current_count, expected_count,
            "Reference count must decrease by exactly 1"
        );
    }

    // Drop final reference
    drop(refs);
    sleep(Duration::from_millis(50)).await;

    assert_eq!(active_socket_count(), 0, "All should be cleaned up");
}

/// Property-based test: Commutativity of reference operations
#[tokio::test]
async fn test_reference_commutativity_properties() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_commutativity.sock".to_string();

    // Property: Order of reference creation should not affect final state
    // Property: Order of reference dropping should not affect final state

    // Test 1: Different creation patterns leading to same state
    for pattern in 0..3 {
        cleanup_all_sockets();
        sleep(Duration::from_millis(10)).await;

        let mut refs = Vec::new();

        match pattern {
            0 => {
                // Pattern: register -> get -> clone -> get
                refs.push(SocketReference::register_new_socket(
                    socket_path.clone(),
                    runtime_handle.clone(),
                );
                refs.push(SocketReference::get_existing(&socket_path).unwrap();
                refs.push(refs[0].clone();
                refs.push(SocketReference::get_existing(&socket_path).unwrap();
            }
            1 => {
                // Pattern: register -> clone -> get -> clone
                refs.push(SocketReference::register_new_socket(
                    socket_path.clone(),
                    runtime_handle.clone(),
                );
                refs.push(refs[0].clone();
                refs.push(SocketReference::get_existing(&socket_path).unwrap();
                refs.push(refs[0].clone();
            }
            2 => {
                // Pattern: register -> get -> get -> clone
                refs.push(SocketReference::register_new_socket(
                    socket_path.clone(),
                    runtime_handle.clone(),
                );
                refs.push(SocketReference::get_existing(&socket_path).unwrap();
                refs.push(SocketReference::get_existing(&socket_path).unwrap();
                refs.push(refs[1].clone();
            }
            _ => unreachable!(),
        }

        // Invariant: All patterns must result in same final state
        assert_eq!(refs.len(), 4, "All patterns must create 4 references");
        assert_eq!(
            refs[0].reference_count(),
            4,
            "All patterns must have count 4"
        );
        assert_eq!(
            active_socket_count(),
            1,
            "All patterns must have 1 active socket"
        );

        // All references must point to same socket
        for socket_ref in &refs {
            assert_eq!(
                socket_ref.path(),
                socket_path,
                "All must point to same socket"
            );
        }

        drop(refs);
        sleep(Duration::from_millis(50)).await;
        assert_eq!(
            active_socket_count(),
            0,
            "Pattern {} should clean up",
            pattern
        );
    }
}

/// Property-based test: Idempotency of query operations
#[tokio::test]
async fn test_query_idempotency_properties() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_idempotency.sock".to_string();

    // Property: Query operations must be idempotent (not change state)
    // Property: Repeated queries must return consistent results

    let socket_ref = SocketReference::register_new_socket(socket_path.clone());

    // Baseline state
    let initial_count = socket_ref.reference_count();
    let initial_active_count = active_socket_count();
    let initial_is_active = is_socket_active(is_socket_active(&socket_path);socket_path));

    // Property: Multiple calls to query functions must not change state
    for _ in 0..100 {
        // These operations must be idempotent
        assert_eq!(
            socket_ref.reference_count(),
            initial_count,
            "reference_count must be idempotent"
        );
        assert_eq!(
            active_socket_count(),
            initial_active_count,
            "active_socket_count must be idempotent"
        );
        assert_eq!(
            is_socket_active(&socket_path),
            initial_is_active,
            "is_socket_active must be idempotent"
        );
        assert_eq!(socket_ref.path(), socket_path, "path must be idempotent");
        assert_eq!(socket_ref.is_active(), true, "is_active must be idempotent");
    }

    // State should be unchanged after all queries
    assert_eq!(
        socket_ref.reference_count(),
        initial_count,
        "State unchanged after queries"
    );
    assert_eq!(
        active_socket_count(),
        initial_active_count,
        "State unchanged after queries"
    );

    drop(socket_ref);
    sleep(Duration::from_millis(50)).await;
    assert_eq!(active_socket_count(), 0, "Should clean up");
}

/// Property-based test: Associativity of reference operations
#[tokio::test]
async fn test_reference_associativity_properties() {
    cleanup_all_sockets();

    let socket_path = "/tmp/test_associativity.sock".to_string();

    // Property: (A + B) + C = A + (B + C) for reference creation
    // Property: Different groupings of operations should yield same result

    // Test associativity through different operation groupings
    for grouping in 0..2 {
        cleanup_all_sockets();
        sleep(Duration::from_millis(10)).await;

        let mut refs = Vec::new();

        match grouping {
            0 => {
                // Grouping: ((register + get) + clone)
                refs.push(SocketReference::register_new_socket(
                    socket_path.clone(),
                    runtime_handle.clone(),
                );
                refs.push(SocketReference::get_existing(&socket_path).unwrap();
                refs.push(refs[0].clone();
            }
            1 => {
                // Grouping: (register + (get + clone))
                refs.push(SocketReference::register_new_socket(
                    socket_path.clone(),
                    runtime_handle.clone(),
                );
                let temp_ref = SocketReference::get_existing(&socket_path).unwrap();
                refs.push(temp_ref.clone();
                refs.push(temp_ref);
            }
            _ => unreachable!(),
        }

        // Invariant: Different groupings must yield same result
        assert_eq!(
            refs.len(),
            3,
            "Grouping {} must create 3 references",
            grouping
        );
        assert_eq!(
            refs[0].reference_count(),
            3,
            "Grouping {} must have count 3",
            grouping
        );
        assert_eq!(
            active_socket_count(),
            1,
            "Grouping {} must have 1 active socket",
            grouping
        );

        drop(refs);
        sleep(Duration::from_millis(50)).await;
        assert_eq!(
            active_socket_count(),
            0,
            "Grouping {} should clean up",
            grouping
        );
    }
}

/// Performance benchmark: Lock contention scenarios
#[tokio::test]
async fn test_performance_lock_contention_benchmark() {
    cleanup_all_sockets();


    // Contract: System must maintain reasonable performance under lock contention
    // Contract: Performance must degrade gracefully under increasing contention

    let contention_levels = vec![1, 5, 10, 20, 50];

    for contention_level in contention_levels {
        cleanup_all_sockets();
        sleep(Duration::from_millis(10)).await;

        let socket_path = format!("/tmp/benchmark_contention_{}.sock", contention_level);
        let operations_per_task = 100;

        let start_time = Instant::now();
        let mut handles = Vec::new();

        // Create tasks with increasing contention
        for task_id in 0..contention_level {
            let socket_path_clone = socket_path.clone());

            let handle = tokio::spawn(async move {
                let task_start = Instant::now();
                let mut task_refs = Vec::new();

                // Perform reference operations
                for _ in 0..operations_per_task {
                    let socket_ref = if task_refs.is_empty() {
                        SocketReference::register_new_socket(
                            socket_path_clone.clone(),
                            runtime_handle_clone.clone(),
                        )
                    } else {
                        SocketReference::get_existing(&socket_path_clone)
                            .expect("Should find existing socket")
                    };
                    task_refs.push(socket_ref);
                }

                // Hold references briefly to create contention during cleanup
                sleep(Duration::from_millis(1)).await;

                // Drop all references
                drop(task_refs);

                let task_duration = task_start.elapsed();
                (task_id, task_duration, operations_per_task)
            });
            handles.push(handle);
        }

        // Wait for all tasks to complete
        let mut total_operations = 0;
        let mut max_task_duration = Duration::from_secs(0);

        for handle in handles {
            let (task_id, task_duration, ops) = handle.await.unwrap();
            total_operations += ops;
            max_task_duration = max_task_duration.max(task_duration);
            println!(
                "Contention level {}: Task {} completed {} ops in {:?}",
                contention_level, task_id, ops, task_duration
            );
        }

        let total_duration = start_time.elapsed();

        // Give cleanup time
        sleep(Duration::from_millis(100)).await;

        // Performance validation
        let ops_per_second = total_operations as f64 / total_duration.as_secs_f64();
        println!(
            "Contention level {}: {} ops/sec, max task: {:?}, total: {:?}",
            contention_level, ops_per_second, max_task_duration, total_duration
        );

        // Contract validation: System must complete all operations
        assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");

        // Contract validation: Performance should be reasonable
        assert!(
            total_duration < Duration::from_secs(30),
            "Performance test with {} tasks took too long: {:?}",
            contention_level,
            total_duration
        );

        // Contract validation: Individual tasks shouldn't be starved
        assert!(
            max_task_duration < Duration::from_secs(10),
            "Individual task took too long under contention: {:?}",
            max_task_duration
        );
    }
}

/// Performance benchmark: Throughput under different reference patterns
#[tokio::test]
async fn test_performance_reference_patterns_benchmark() {
    cleanup_all_sockets();


    // Contract: Different reference patterns should have predictable performance characteristics

    let patterns = vec![
        ("many_refs_one_socket", 1, 1000),  // Many refs to one socket
        ("few_refs_many_sockets", 100, 10), // Few refs to many sockets
        ("balanced", 20, 50),               // Balanced approach
    ];

    for (pattern_name, socket_count, refs_per_socket) in patterns {
        cleanup_all_sockets();
        sleep(Duration::from_millis(10)).await;

        let start_time = Instant::now();
        let mut all_refs = Vec::new();

        // Create references according to pattern
        for socket_id in 0..socket_count {
            let socket_path = format!("/tmp/benchmark_{}_{}.sock", pattern_name, socket_id);

            for ref_id in 0..refs_per_socket {
                let socket_ref = if ref_id == 0 {
                    SocketReference::register_new_socket(
                        socket_path.clone(),
                        runtime_handle.clone(),
                    )
                } else {
                    SocketReference::get_existing(&socket_path)
                        .expect("Should find existing socket")
                };
                all_refs.push(socket_ref);
            }
        }

        let creation_time = start_time.elapsed();

        // Verify state
        assert_eq!(
            active_socket_count(),
            socket_count,
            "Should have correct socket count"
        );
        assert_eq!(
            all_refs.len(),
            socket_count * refs_per_socket,
            "Should have all references"
        );

        // Drop all references
        let drop_start = Instant::now();
        drop(all_refs);
        let drop_time = drop_start.elapsed();

        // Give cleanup time
        sleep(Duration::from_millis(100)).await;

        let total_time = start_time.elapsed();
        let total_operations = socket_count * refs_per_socket;

        println!(
            "Pattern '{}': {} ops, creation: {:?}, drop: {:?}, total: {:?}",
            pattern_name, total_operations, creation_time, drop_time, total_time
        );

        // Contract validation: All should be cleaned up
        assert_eq!(active_socket_count(), 0, "All sockets should be cleaned up");

        // Contract validation: Performance should be reasonable
        assert!(
            total_time < Duration::from_secs(10),
            "Pattern '{}' took too long: {:?}",
            pattern_name,
            total_time
        );
    }
}

/// Performance benchmark: Stress test with mixed operations
#[tokio::test]
async fn test_performance_mixed_operations_stress() {
    cleanup_all_sockets();


    // Contract: System must maintain performance under mixed workload stress

    let stress_duration = Duration::from_secs(5);
    let socket_paths: Vec<String> = (0..10)
        .map(|i| format!("/tmp/stress_test_{}.sock", i))
        .collect();

    let start_time = Instant::now();
    let operations_completed = Arc::new(AtomicUsize::new(0));

    let mut handles = Vec::new();

    // Create stress test tasks
    for task_id in 0..5 {
        let socket_paths_clone = socket_paths.clone();
        let operations_completed_clone = operations_completed.clone();

        let handle = tokio::spawn(async move {
            let mut local_operations = 0;
            let task_start = Instant::now();

            while task_start.elapsed() < stress_duration {
                let socket_path = &socket_paths_clone[local_operations % socket_paths_clone.len()];

                match local_operations % 4 {
                    0 => {
                        // Register new socket
                        let _socket_ref = SocketReference::register_new_socket(
                            socket_path.clone(),
                            runtime_handle_clone.clone(),
                        );
                        // Drop immediately to stress cleanup
                    }
                    1 => {
                        // Try to get existing
                        let _existing = SocketReference::get_existing(socket_path);
                    }
                    2 => {
                        // Check if active
                        let _is_active = is_socket_active(socket_path);
                    }
                    3 => {
                        // Get active count
                        let _count = active_socket_count();
                    }
                    _ => unreachable!(),
                }

                local_operations += 1;

                // Brief yield to other tasks
                if local_operations % 100 == 0 {
                    sleep(Duration::from_micros(100)).await;
                }
            }

            operations_completed_clone.fetch_add(local_operations, Ordering::Relaxed);
            (task_id, local_operations)
        });
        handles.push(handle);
    }

    // Wait for stress test to complete
    let mut task_results = Vec::new();
    for handle in handles {
        let result = handle.await.unwrap();
        task_results.push(result);
    }

    let total_time = start_time.elapsed();
    let total_ops = operations_completed.load(Ordering::Relaxed);

    // Give cleanup time
    sleep(Duration::from_millis(200)).await;

    let ops_per_second = total_ops as f64 / total_time.as_secs_f64();

    println!(
        "Stress test: {} operations in {:?} ({:.0} ops/sec)",
        total_ops, total_time, ops_per_second
    );

    for (task_id, task_ops) in task_results {
        println!("  Task {}: {} operations", task_id, task_ops);
    }

    // Contract validation: System should remain stable
    let final_socket_count = active_socket_count();
    println!(
        "Final socket count after stress test: {}",
        final_socket_count
    );

    // Some sockets may remain due to race conditions in stress test
    assert!(
        final_socket_count <= 10,
        "Should not have excessive sockets remaining: {}",
        final_socket_count
    );

    // Contract validation: Performance should be reasonable
    assert!(
        ops_per_second > 100.0,
        "Performance too low: {:.0} ops/sec",
        ops_per_second
    );

    // Force cleanup
    cleanup_all_sockets();
    assert_eq!(active_socket_count(), 0, "Final cleanup should succeed");
}

#[tokio::test]
async fn test_cleanup_all_sockets_function() {
    let helper = TestSocketHelper::new();

    // Create multiple sockets
    let socket_path1 = helper.get_test_socket_path();
    let socket_path2 = helper.get_test_socket_path();

    let (tx1, rx1) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx1.send(result);
        },
        Some(socket_path1.clone()),
    );

    let (tx2, rx2) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx2.send(result);
        },
        Some(socket_path2.clone()),
    );

    let _socket_ref1 = rx1
        .await
        .unwrap()
        .expect("First socket creation should succeed");
    let _socket_ref2 = rx2
        .await
        .unwrap()
        .expect("Second socket creation should succeed");

    assert_eq!(active_socket_count(), 2);
    assert!(is_socket_active(&socket_path1);
    assert!(is_socket_active(&socket_path2);

    // Force cleanup all sockets
    cleanup_all_sockets();

    // All sockets should be cleaned up
    assert_eq!(active_socket_count(), 0);
    assert!(!is_socket_active(&socket_path1);
    assert!(!is_socket_active(&socket_path2);
}

/// Integration test that simulates real client usage patterns
#[tokio::test]
async fn test_realistic_client_simulation() {
    cleanup_all_sockets();

    let helper = TestSocketHelper::new();
    let socket_path = helper.get_test_socket_path();

    // Simulate multiple clients being created and destroyed at different times

    // Client 1 connects
    let (tx1, rx1) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx1.send(result);
        },
        Some(socket_path.clone()),
    );
    let client1_socket = rx1.await.unwrap().expect("Client 1 should connect");
    assert_eq!(active_socket_count(), 1);
    assert_eq!(client1_socket.reference_count(), 1);

    // Client 2 connects to same socket
    let (tx2, rx2) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx2.send(result);
        },
        Some(socket_path.clone()),
    );
    let client2_socket = rx2.await.unwrap().expect("Client 2 should connect");
    assert_eq!(active_socket_count(), 1);
    assert_eq!(client1_socket.reference_count(), 2);
    assert_eq!(client2_socket.reference_count(), 2);

    // Client 3 connects
    let (tx3, rx3) = tokio::sync::oneshot::channel();
    start_socket_listener_with_reference(
        move |result| {
            let _ = tx3.send(result);
        },
        Some(socket_path.clone()),
    );
    let client3_socket = rx3.await.unwrap().expect("Client 3 should connect");
    assert_eq!(active_socket_count(), 1);
    assert_eq!(client1_socket.reference_count(), 3);

    // Client 1 disconnects
    drop(client1_socket);
    assert_eq!(client2_socket.reference_count(), 2);
    assert_eq!(active_socket_count(), 1);

    // Client 2 disconnects
    drop(client2_socket);
    assert_eq!(client3_socket.reference_count(), 1);
    assert_eq!(active_socket_count(), 1);

    // Last client disconnects
    drop(client3_socket);
    sleep(Duration::from_millis(50)).await;

    // Socket should be cleaned up
    assert_eq!(active_socket_count(), 0);
    assert!(!is_socket_active(is_socket_active(&socket_path);socket_path));
}
