// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Reference counting solution for socket management
//!
//! This module provides a reference-counted approach to socket lifecycle management,
//! replacing the simple HashSet-based tracking with proper reference counting.
//! When the last reference to a socket is dropped, the socket is automatically cleaned up.

use logger_core::{log_debug, log_error, log_info};
use once_cell::sync::Lazy;
use std::collections::HashMap;
use std::path::Path;
use std::sync::{Arc, Mutex, Weak};

/// Internal socket data that tracks the socket state and cleanup information
#[derive(Debug)]
struct SocketData {
    /// The file system path to the socket
    path: String,
    /// Whether the socket has been marked for cleanup
    cleanup_initiated: bool,
}

/// A reference-counted handle to a socket
///
/// When all SocketReference instances for a given socket path are dropped,
/// the socket will be automatically cleaned up and removed from the filesystem.
#[derive(Debug, Clone)]
pub struct SocketReference {
    /// Arc to the socket data - when this drops to zero references, cleanup occurs
    data: Arc<SocketData>,
}

/// Manages all active sockets with reference counting
///
/// This struct replaces the INITIALIZED_SOCKETS HashSet with a more sophisticated
/// reference counting system. It tracks weak references to active sockets and
/// automatically cleans up sockets when their reference count reaches zero.
#[derive(Debug)]
struct SocketManager {
    /// Map from socket path to weak reference of socket data
    /// Using Weak references prevents circular references and allows automatic cleanup
    sockets: HashMap<String, Weak<SocketData>>,
}

impl SocketManager {
    fn new() -> Self {
        Self {
            sockets: HashMap::new(),
        }
    }

    /// Remove expired weak references from the sockets map
    fn cleanup_expired_references(&mut self) {
        self.sockets.retain(|path, weak_ref| {
            let is_alive = weak_ref.upgrade().is_some();
            if !is_alive {
                log_debug(
                    "SocketManager",
                    format!("Cleaned up expired reference for {}", path),
                );
            }
            is_alive
        });
    }

    /// Check if a socket path is currently active (has live references)
    fn is_socket_active(&mut self, path: &str) -> bool {
        self.cleanup_expired_references();

        if let Some(weak_ref) = self.sockets.get(path) {
            weak_ref.upgrade().is_some()
        } else {
            false
        }
    }

    /// Force cleanup of a specific socket (mainly for testing)
    fn force_cleanup_socket(&mut self, path: &str) {
        self.sockets.remove(path);
        if Path::new(path).exists() {
            if let Err(e) = std::fs::remove_file(path) {
                log_error(
                    "SocketManager",
                    format!("Failed to remove socket file {}: {}", path, e),
                );
            } else {
                log_info(
                    "SocketManager",
                    format!("Force cleaned up socket file: {}", path),
                );
            }
        }
    }
}

/// Global socket manager instance
static SOCKET_MANAGER: Lazy<Mutex<SocketManager>> = Lazy::new(|| Mutex::new(SocketManager::new()));

impl SocketReference {
    /// Get or create a socket reference atomically
    ///
    /// This method ensures thread-safe access to sockets by checking for existing
    /// references and creating new ones under the same lock, preventing race conditions.
    ///
    /// # Thread Safety
    /// This function acquires a lock on the global socket manager to ensure atomic operations.
    ///
    /// # Arguments
    /// * `socket_path` - The file system path to the socket
    ///
    /// # Returns
    /// A SocketReference that will automatically clean up the socket when dropped
    pub fn get_or_create(socket_path: String) -> Self {
        let mut manager = match SOCKET_MANAGER.lock() {
            Ok(m) => m,
            Err(e) => {
                log_error(
                    "SocketReference::get_or_create",
                    format!("Failed to lock socket manager: {}", e),
                );
                // Recover from poisoned lock by creating new manager state
                e.into_inner()
            }
        };

        // Check for existing socket under lock
        if let Some(weak_ref) = manager.sockets.get(&socket_path)
            && let Some(arc_data) = weak_ref.upgrade()
        {
            log_debug(
                "SocketReference::get_or_create",
                format!("Returning existing socket reference for {}", socket_path),
            );
            return SocketReference { data: arc_data };
        }

        // Create new socket under same lock
        let socket_data = Arc::new(SocketData {
            path: socket_path.clone(),
            cleanup_initiated: false,
        });

        manager
            .sockets
            .insert(socket_path.clone(), Arc::downgrade(&socket_data));
        log_info(
            "SocketReference::get_or_create",
            format!("Registered new socket reference for {}", socket_path),
        );

        SocketReference { data: socket_data }
    }

    /// Get an existing socket reference for the given path
    ///
    /// Returns Some(SocketReference) if there are existing references to the socket.
    /// Returns None if no active socket exists for this path.
    ///
    /// Note: This is kept for backward compatibility but get_or_create is preferred
    /// for most use cases to avoid race conditions.
    pub fn get_existing(socket_path: &str) -> Option<Self> {
        let manager = SOCKET_MANAGER.lock().ok()?;
        if let Some(weak_ref) = manager.sockets.get(socket_path)
            && let Some(arc_data) = weak_ref.upgrade()
        {
            log_debug(
                "SocketReference::get_existing",
                format!("Returning existing socket reference for {}", socket_path),
            );
            return Some(SocketReference { data: arc_data });
        }
        None
    }

    /// Register a newly created socket
    ///
    /// This should be called after successfully binding a new socket.
    /// This is separate from `get_existing()` to avoid holding locks during socket creation.
    ///
    /// Note: Consider using get_or_create instead to avoid race conditions.
    pub fn register_new_socket(socket_path: String) -> Self {
        let socket_data = Arc::new(SocketData {
            path: socket_path.clone(),
            cleanup_initiated: false,
        });

        // Store weak reference for tracking
        if let Ok(mut manager) = SOCKET_MANAGER.lock() {
            manager
                .sockets
                .insert(socket_path.clone(), Arc::downgrade(&socket_data));
            log_info(
                "SocketReference::register_new_socket",
                format!("Registered new socket reference for {}", socket_path),
            );
        } else {
            log_error(
                "SocketReference::register_new_socket",
                "Failed to lock socket manager",
            );
        }

        SocketReference { data: socket_data }
    }

    /// Get the socket path
    pub fn path(&self) -> &str {
        &self.data.path
    }

    /// Check if this socket is still active (for debugging/testing)
    pub fn is_active(&self) -> bool {
        !self.data.cleanup_initiated
    }

    /// Get the current reference count (for debugging/testing)
    /// Note: This is approximate due to potential race conditions
    pub fn reference_count(&self) -> usize {
        Arc::strong_count(&self.data)
    }
}

impl Drop for SocketData {
    /// Automatic cleanup when the last reference is dropped
    fn drop(&mut self) {
        // Mark cleanup as initiated to prevent double cleanup
        if self.cleanup_initiated {
            return;
        }
        self.cleanup_initiated = true;

        let path = self.path.clone();
        log_info(
            "SocketData::drop",
            format!(
                "Last reference dropped for socket {}, initiating cleanup",
                path
            ),
        );

        // Attempt immediate cleanup with try_lock to avoid deadlock
        if let Ok(mut manager) = SOCKET_MANAGER.try_lock() {
            manager.force_cleanup_socket(&path);
        } else {
            // If we can't get the lock immediately, use Tokio's blocking thread pool
            // for cleanup to avoid spawning new threads and improve performance
            let path_clone = path.clone();

            // Try to use tokio's blocking pool if available, otherwise fall back to thread spawn
            if let Ok(handle) = tokio::runtime::Handle::try_current() {
                // Use Tokio's blocking thread pool for better resource management
                handle.spawn_blocking(move || {
                    cleanup_socket_deferred(path_clone);
                });
            } else {
                // Fallback: spawn a thread if not in Tokio context
                std::thread::spawn(move || {
                    cleanup_socket_deferred(path_clone);
                });
            }

            log_info(
                "SocketData::drop",
                format!("Scheduled async cleanup for socket: {}", path),
            );
        }
    }
}

/// Deferred cleanup function that attempts to clean up a socket file
/// This is called from a background thread/task when immediate cleanup isn't possible
fn cleanup_socket_deferred(path: String) {
    // Wait briefly to allow lock contention to clear
    std::thread::sleep(std::time::Duration::from_millis(10));

    // Try to acquire lock and cleanup through manager
    if let Ok(mut manager) = SOCKET_MANAGER.lock() {
        manager.force_cleanup_socket(&path);
    } else {
        // Last resort: direct file removal
        if Path::new(&path).exists() {
            if let Err(e) = std::fs::remove_file(&path) {
                log_error(
                    "cleanup_socket_deferred",
                    format!("Failed to remove socket file {}: {}", path, e),
                );
            } else {
                log_info(
                    "cleanup_socket_deferred",
                    format!("Cleaned up socket file via direct removal: {}", path),
                );
            }
        }
    }
}

// Public API functions for socket reference management

/// Check if a socket is currently active (has live references)
pub fn is_socket_active(socket_path: &str) -> bool {
    if let Ok(mut manager) = SOCKET_MANAGER.lock() {
        manager.is_socket_active(socket_path)
    } else {
        log_error("is_socket_active", "Failed to lock socket manager");
        false
    }
}

/// Get the current number of active sockets (for monitoring/debugging)
pub fn active_socket_count() -> usize {
    if let Ok(mut manager) = SOCKET_MANAGER.lock() {
        manager.cleanup_expired_references();
        manager.sockets.len()
    } else {
        log_error("active_socket_count", "Failed to lock socket manager");
        0
    }
}

/// Force cleanup of all sockets (mainly for testing)
pub fn cleanup_all_sockets() {
    if let Ok(mut manager) = SOCKET_MANAGER.lock() {
        // Use drain to avoid unnecessary clones
        let paths: Vec<String> = manager.sockets.drain().map(|(k, _)| k).collect();
        for path in paths {
            if Path::new(&path).exists() {
                if let Err(e) = std::fs::remove_file(&path) {
                    log_error(
                        "cleanup_all_sockets",
                        format!("Failed to remove socket file {}: {}", path, e),
                    );
                } else {
                    log_info(
                        "cleanup_all_sockets",
                        format!("Cleaned up socket file: {}", path),
                    );
                }
            }
        }
        log_info("cleanup_all_sockets", "Cleaned up all sockets");
    } else {
        log_error("cleanup_all_sockets", "Failed to lock socket manager");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::NamedTempFile;

    #[tokio::test]
    async fn test_socket_reference_creation() {
        cleanup_all_sockets();

        let temp_file = NamedTempFile::new().unwrap();
        let socket_path = temp_file.path().to_str().unwrap().to_string();

        // First create a socket reference using get_or_create
        let ref1 = SocketReference::get_or_create(socket_path.clone());
        assert_eq!(ref1.path(), socket_path);
        assert_eq!(ref1.reference_count(), 1);

        // Second reference using get_or_create should reuse existing socket data
        let ref2 = SocketReference::get_or_create(socket_path.clone());
        assert_eq!(ref2.path(), socket_path);
        assert_eq!(ref1.reference_count(), 2);
        assert_eq!(ref2.reference_count(), 2);

        // Both references should point to same data
        assert!(Arc::ptr_eq(&ref1.data, &ref2.data));

        // Test get_existing as well
        let ref3 = SocketReference::get_existing(&socket_path);
        assert!(ref3.is_some());
        let ref3 = ref3.unwrap();
        assert_eq!(ref3.reference_count(), 3);
    }

    #[tokio::test]
    async fn test_automatic_cleanup() {
        cleanup_all_sockets();

        let temp_file = NamedTempFile::new().unwrap();
        let socket_path = temp_file.path().to_str().unwrap().to_string();

        // Create a socket reference
        {
            let _ref1 = SocketReference::get_or_create(socket_path.clone());
            assert!(is_socket_active(&socket_path));
        }
        // Reference dropped here - socket should be cleaned up

        // Give cleanup a moment to complete (async cleanup may be scheduled)
        tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;

        // Socket should no longer be active
        assert!(!is_socket_active(&socket_path));
    }

    #[tokio::test]
    async fn test_multiple_references_single_cleanup() {
        cleanup_all_sockets();

        let temp_file = NamedTempFile::new().unwrap();
        let socket_path = temp_file.path().to_str().unwrap().to_string();

        let ref1 = SocketReference::get_or_create(socket_path.clone());
        let ref2 = SocketReference::get_or_create(socket_path.clone());
        let ref3 = ref1.clone();

        assert_eq!(ref1.reference_count(), 3);
        assert!(is_socket_active(&socket_path));

        // Drop two references
        drop(ref1);
        drop(ref2);

        // Socket should still be active
        assert!(is_socket_active(&socket_path));
        assert_eq!(ref3.reference_count(), 1);

        // Drop last reference
        drop(ref3);

        // Give cleanup a moment to complete (async cleanup may be scheduled)
        tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;

        // Socket should now be cleaned up
        assert!(!is_socket_active(&socket_path));
    }

    #[tokio::test]
    async fn test_socket_manager_operations() {
        cleanup_all_sockets();

        // Give cleanup a moment to complete
        tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;

        // Should start with no active sockets - if not, force cleanup again
        let mut initial_count = active_socket_count();
        if initial_count != 0 {
            eprintln!(
                "Warning: Starting with {} active sockets, forcing cleanup",
                initial_count
            );
            cleanup_all_sockets();
            tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
            initial_count = active_socket_count();
        }

        let socket_path = "/tmp/test_socket_123.sock";

        // Create first reference
        let _ref1 = SocketReference::get_or_create(socket_path.to_string());
        let count_after_ref1 = active_socket_count();
        eprintln!("After creating ref1: {} active sockets", count_after_ref1);
        assert_eq!(count_after_ref1, initial_count + 1);
        assert!(is_socket_active(socket_path));

        // Create second reference to same socket - should find existing one
        let _ref2 = SocketReference::get_or_create(socket_path.to_string());
        let count_after_ref2 = active_socket_count();
        eprintln!("After creating ref2: {} active sockets", count_after_ref2);
        assert_eq!(count_after_ref2, initial_count + 1); // Still only one unique socket

        // Create reference to different socket
        let socket_path2 = "/tmp/test_socket_456.sock";
        let _ref3 = SocketReference::get_or_create(socket_path2.to_string());
        let count_after_ref3 = active_socket_count();
        eprintln!("After creating ref3: {} active sockets", count_after_ref3);
        assert_eq!(count_after_ref3, initial_count + 2); // Now two unique sockets

        cleanup_all_sockets();
        let final_count = active_socket_count();
        eprintln!("After cleanup: {} active sockets", final_count);
        assert_eq!(final_count, 0);
    }
}
