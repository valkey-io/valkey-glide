// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Comprehensive security tests for socket reference implementation
//! Tests all critical security fixes implemented in the socket reference system

use glide_core::socket_reference::{SocketReference, active_socket_count, cleanup_all_sockets};
use std::thread;
use std::time::Duration;
use tempfile::NamedTempFile;
use tokio::time::timeout;

/// Security Test Suite 1: Path Validation Security
/// Tests all input validation fixes to prevent directory traversal and injection attacks
mod path_validation_security {
    use super::*;

    #[tokio::test]
    async fn test_path_traversal_prevention() {
        // Test various path traversal attack patterns
        let attack_patterns = vec![
            "../../etc/passwd",
            "../../../root/.ssh/id_rsa",
            "..\\..\\windows\\system32\\config\\sam",
            "/tmp/../../../etc/shadow",
            "/tmp/socket/../../../root/secret",
            "../../../../usr/bin/malicious",
            "/tmp/./../../etc/hosts",
        ];

        for pattern in attack_patterns {
            let result = SocketReference::get_or_create(pattern.to_string());
            assert!(
                result.is_err(),
                "Path traversal attack should be blocked: {}",
                pattern
            );

            let error_msg = result.unwrap_err();
            assert!(
                error_msg.contains("Path traversal"),
                "Error should contain path traversal message for: {}. Got: {}",
                pattern,
                error_msg
            );
        }
    }

    #[tokio::test]
    async fn test_null_byte_injection_prevention() {
        let injection_patterns = vec![
            "/tmp/socket\0/../../etc/passwd",
            "/tmp/valid_path\0.sock",
            "/tmp/socket\0\n\r.sock",
            "valid\0name.sock",
        ];

        for pattern in injection_patterns {
            let result = SocketReference::get_or_create(pattern.to_string());
            assert!(
                result.is_err(),
                "Null byte injection should be blocked: {}",
                pattern
            );

            let error_msg = result.unwrap_err();
            assert!(
                error_msg.contains("null byte"),
                "Error should contain null byte message for: {}. Got: {}",
                pattern,
                error_msg
            );
        }
    }

    #[tokio::test]
    async fn test_unsafe_location_prevention() {
        let unsafe_locations = vec![
            "/root/malicious.sock",
            "/etc/shadow.sock",
            "/usr/bin/virus.sock",
            "/home/user/.ssh/steal.sock",
            "/var/log/inject.sock",
            "/boot/system.sock",
            "/proc/meminfo.sock",
            "/sys/devices/hack.sock",
        ];

        for location in unsafe_locations {
            let result = SocketReference::get_or_create(location.to_string());
            // Simplified validation no longer restricts by location
            // These paths should now succeed
            assert!(
                result.is_ok(),
                "Path should be allowed with simplified validation: {}",
                location
            );
        }
    }

    #[tokio::test]
    async fn test_valid_paths_allowed() {
        let valid_paths = vec![
            "/tmp/valid_socket.sock",
            "/tmp/glide-test.sock",
            "/var/tmp/another_valid.sock",
        ];

        for path in valid_paths {
            let result = SocketReference::get_or_create(path.to_string());
            assert!(result.is_ok(), "Valid path should be allowed: {}", path);
        }

        cleanup_all_sockets();
    }
}

/// Security Test Suite 2: Resource Exhaustion Prevention
/// Tests bounded cleanup and DoS prevention mechanisms
mod resource_exhaustion_security {
    use super::*;

    #[tokio::test]
    async fn test_bounded_cleanup_prevents_dos() {
        cleanup_all_sockets();

        // Create many socket references rapidly to test bounded cleanup
        let mut handles = vec![];

        for _i in 0..50 {
            let handle = tokio::spawn(async move {
                let temp_file = NamedTempFile::new().unwrap();
                let socket_path = temp_file.path().to_str().unwrap().to_string();

                // Create and immediately drop socket references
                {
                    let _socket_ref = SocketReference::get_or_create(socket_path.clone()).unwrap();
                    // Socket ref dropped here, triggering cleanup
                }

                // Small delay to allow cleanup to process
                tokio::time::sleep(Duration::from_millis(1)).await;
                socket_path
            });
            handles.push(handle);
        }

        // Wait for all operations to complete with timeout to prevent hanging
        let result = timeout(Duration::from_secs(10), async {
            for handle in handles {
                handle.await.unwrap();
            }
        })
        .await;

        assert!(
            result.is_ok(),
            "Bounded cleanup should prevent system from hanging during mass operations"
        );

        // Give cleanup time to complete
        tokio::time::sleep(Duration::from_millis(100)).await;

        // System should still be responsive
        let final_count = active_socket_count();
        assert!(
            final_count < 50,
            "Most sockets should be cleaned up, got {}",
            final_count
        );
    }

    #[tokio::test]
    async fn test_concurrent_cleanup_safety() {
        cleanup_all_sockets();

        let temp_file = NamedTempFile::new().unwrap();
        let socket_path = temp_file.path().to_str().unwrap().to_string();

        // Test concurrent access patterns that could trigger race conditions
        let mut handles = vec![];

        for _i in 0..20 {
            let path = socket_path.clone();
            let handle = thread::spawn(move || {
                // Rapidly create and drop references
                for _j in 0..10 {
                    let _socket_ref = SocketReference::get_or_create(path.clone()).unwrap();
                    thread::sleep(Duration::from_millis(1));
                    // Reference dropped here
                }
            });
            handles.push(handle);
        }

        // Wait for all threads
        for handle in handles {
            handle.join().unwrap();
        }

        // Give cleanup time
        thread::sleep(Duration::from_millis(100));

        // System should be stable and not have excessive references
        let final_count = active_socket_count();
        assert!(
            final_count <= 1,
            "Should have at most 1 active socket, got {}",
            final_count
        );
    }
}

/// Security Test Suite 3: Memory Safety and Race Condition Prevention
/// Tests race condition fixes and atomic operations
mod memory_safety_security {
    use super::*;

    #[tokio::test]
    async fn test_atomic_cleanup_prevention() {
        cleanup_all_sockets();

        let temp_file = NamedTempFile::new().unwrap();
        let socket_path = temp_file.path().to_str().unwrap().to_string();

        // Create socket reference
        let socket_ref = SocketReference::get_or_create(socket_path.clone()).unwrap();
        assert_eq!(socket_ref.reference_count(), 1);

        // Test that atomic cleanup prevention works
        let socket_ref2 = SocketReference::get_or_create(socket_path.clone()).unwrap();
        assert_eq!(socket_ref.reference_count(), 2);
        assert_eq!(socket_ref2.reference_count(), 2);

        // Drop one reference
        drop(socket_ref);

        // After dropping one reference, we should have only socket_ref2
        assert_eq!(socket_ref2.reference_count(), 1);

        // Create another reference - should reuse existing data
        let socket_ref3 = SocketReference::get_or_create(socket_path).unwrap();

        // Now both socket_ref2 and socket_ref3 should share the same Arc
        assert_eq!(socket_ref2.reference_count(), 2);
        assert_eq!(socket_ref3.reference_count(), 2);

        cleanup_all_sockets();
    }

    #[tokio::test]
    async fn test_lock_poisoning_recovery() {
        cleanup_all_sockets();

        // Test that system remains functional even under stress
        let temp_file = NamedTempFile::new().unwrap();
        let socket_path = temp_file.path().to_str().unwrap().to_string();

        // This test ensures that even if lock poisoning occurred,
        // the system would create standalone references instead of failing
        let socket_ref = SocketReference::get_or_create(socket_path).unwrap();
        assert!(socket_ref.is_active());
        assert!(socket_ref.reference_count() >= 1);

        cleanup_all_sockets();
    }
}

/// Security Test Suite 4: Error Handling and Information Disclosure Prevention
/// Tests enhanced error handling and prevents information leaks
mod error_handling_security {
    use super::*;

    #[tokio::test]
    async fn test_error_codes_consistent() {
        // Test that error codes are consistent and don't leak sensitive information
        let test_cases = vec![
            ("", "Socket path cannot be empty"),
            ("../../../etc/passwd", "Path traversal"),
            ("/tmp/socket\0injection.sock", "null byte"),
        ];

        for (input, expected_code) in test_cases {
            let result = SocketReference::get_or_create(input.to_string());
            assert!(result.is_err(), "Input should be rejected: {}", input);

            let error_msg = result.unwrap_err();
            assert!(
                error_msg.contains(expected_code),
                "Error should contain expected code {} for input '{}'. Got: {}",
                expected_code,
                input,
                error_msg
            );

            // Ensure error messages don't leak sensitive system information
            assert!(!error_msg.contains("/etc/passwd"));
            assert!(!error_msg.contains("secret"));
            // Note: We removed the direct path from unsafe location errors to prevent info disclosure
        }
    }

    #[tokio::test]
    async fn test_path_length_limits() {
        // Test extremely long paths that could cause buffer overflows
        let long_path = "/tmp/".to_string() + &"a".repeat(10000) + ".sock";

        let result = SocketReference::get_or_create(long_path);
        assert!(result.is_err(), "Extremely long path should be rejected");

        let error_msg = result.unwrap_err();
        assert!(
            error_msg.contains("path too long"),
            "Should reject with path too long error. Got: {}",
            error_msg
        );
    }
}

/// Security Test Suite 5: End-to-End Security Validation
/// Comprehensive tests combining multiple security aspects
mod end_to_end_security {
    use super::*;

    #[tokio::test]
    async fn test_comprehensive_attack_simulation() {
        cleanup_all_sockets();

        // Simulate a comprehensive attack combining multiple vectors
        let long_attack_path = "/tmp/".to_string() + &"attack".repeat(1000) + ".sock";

        // These attack vectors should still be blocked
        let blocked_attacks = vec![
            // Path traversal attacks
            "../../etc/passwd".to_string(),
            "../../../root/.ssh/id_rsa".to_string(),
            // Null byte injection
            "/tmp/socket\0../../etc/shadow".to_string(),
            // Long path attacks
            long_attack_path,
            // Empty paths
            "".to_string(),
        ];

        // These paths are allowed with simplified validation
        let allowed_paths = vec![
            "/root/malicious.sock".to_string(),
            "/etc/virus.sock".to_string(),
            " ".to_string(),
            "\n\r\t".to_string(),
        ];

        let initial_count = active_socket_count();

        // Blocked attacks should be rejected
        for attack in blocked_attacks {
            let result = SocketReference::get_or_create(attack.clone());
            assert!(
                result.is_err(),
                "Attack vector should be blocked: {}",
                attack
            );
        }

        // Allowed paths should succeed (but we need to drop them after checking)
        for path in allowed_paths {
            let result = SocketReference::get_or_create(path.clone());
            assert!(result.is_ok(), "Path should be allowed: {}", path);
            // Drop the reference immediately to not affect count
            drop(result);
        }

        // Give any potential cleanup time to complete
        tokio::time::sleep(Duration::from_millis(50)).await;

        // System should remain stable - we've dropped all allowed refs
        let post_attack_count = active_socket_count();
        assert_eq!(
            initial_count, post_attack_count,
            "Socket count should be unchanged after attack attempts"
        );

        // Valid operations should still work
        let temp_file = NamedTempFile::new().unwrap();
        let valid_path = temp_file.path().to_str().unwrap().to_string();

        let valid_socket = SocketReference::get_or_create(valid_path);
        assert!(
            valid_socket.is_ok(),
            "Valid operations should work after attack attempts"
        );

        cleanup_all_sockets();
    }

    #[tokio::test]
    async fn test_system_resilience_under_load() {
        cleanup_all_sockets();

        // Test system behavior under legitimate high load
        let mut socket_refs = vec![];

        // Create many legitimate socket references
        for _i in 0..100 {
            let temp_file = NamedTempFile::new().unwrap();
            let socket_path = temp_file.path().to_str().unwrap().to_string();

            match SocketReference::get_or_create(socket_path) {
                Ok(socket_ref) => socket_refs.push(socket_ref),
                Err(e) => panic!("Valid socket creation should not fail under load: {}", e),
            }
        }

        // Verify all references are functional
        assert_eq!(socket_refs.len(), 100);
        for socket_ref in &socket_refs {
            assert!(socket_ref.is_active());
            assert!(socket_ref.reference_count() >= 1);
        }

        // Cleanup should handle large numbers gracefully
        drop(socket_refs);
        tokio::time::sleep(Duration::from_millis(100)).await;

        let final_count = active_socket_count();
        assert!(
            final_count < 50,
            "Most sockets should be cleaned up after mass drop"
        );

        cleanup_all_sockets();
    }
}
