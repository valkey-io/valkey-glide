// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Per-client runtime management for the JNI bridge.
//!
//! This module provides isolated Tokio runtime instances for each client,
//! ensuring complete resource isolation and preventing interference between
//! different client instances.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::runtime::{Builder, Runtime};

use crate::error::{JniError, JniResult};

/// Per-client runtime wrapper providing isolated async execution
pub struct JniRuntime {
    /// The Tokio runtime instance
    runtime: Arc<Runtime>,
    /// Flag indicating if the runtime is being shut down
    shutdown: Arc<AtomicBool>,
}

impl Clone for JniRuntime {
    fn clone(&self) -> Self {
        Self {
            runtime: self.runtime.clone(),
            shutdown: self.shutdown.clone(),
        }
    }
}

impl JniRuntime {
    /// Create a new per-client runtime
    pub fn new(thread_name_prefix: &str) -> JniResult<Self> {
        let runtime = Builder::new_multi_thread()
            .enable_all()
            .thread_name(thread_name_prefix)
            .worker_threads(2) // Limit threads per client
            .max_blocking_threads(4) // Limit blocking threads per client
            .build()
            .map_err(|e| JniError::Runtime(format!("Failed to create runtime: {}", e)))?;

        Ok(Self {
            runtime: Arc::new(runtime),
            shutdown: Arc::new(AtomicBool::new(false)),
        })
    }

    /// Execute a future on this runtime
    pub fn block_on<F>(&self, future: F) -> JniResult<F::Output>
    where
        F: std::future::Future + Send,
        F::Output: Send,
    {
        if self.shutdown.load(Ordering::Acquire) {
            return Err(JniError::RuntimeShutdown("Runtime is shutting down".to_string()));
        }

        Ok(self.runtime.block_on(future))
    }

    /// Spawn a task on this runtime
    pub fn spawn<F>(&self, future: F) -> JniResult<tokio::task::JoinHandle<F::Output>>
    where
        F: std::future::Future + Send + 'static,
        F::Output: Send + 'static,
    {
        if self.shutdown.load(Ordering::Acquire) {
            return Err(JniError::RuntimeShutdown("Runtime is shutting down".to_string()));
        }

        Ok(self.runtime.spawn(future))
    }

    /// Spawn a task with timeout
    pub fn spawn_with_timeout<F>(
        &self,
        future: F,
        timeout_duration: Duration,
    ) -> JniResult<tokio::task::JoinHandle<JniResult<F::Output>>>
    where
        F: std::future::Future + Send + 'static,
        F::Output: Send + 'static,
    {
        if self.shutdown.load(Ordering::Acquire) {
            return Err(JniError::RuntimeShutdown("Runtime is shutting down".to_string()));
        }

        let handle = self.runtime.spawn(async move {
            match tokio::time::timeout(timeout_duration, future).await {
                Ok(result) => Ok(result),
                Err(_) => Err(JniError::Timeout("Operation timeout".to_string())),
            }
        });

        Ok(handle)
    }

    /// Initiate shutdown of this runtime
    pub fn shutdown(&self) {
        self.shutdown.store(true, Ordering::Release);
        // Note: We don't shutdown the runtime as it's shared via Arc
        // The runtime will be dropped when all references are gone
    }

    /// Check if the runtime is shutting down
    pub fn is_shutting_down(&self) -> bool {
        self.shutdown.load(Ordering::Acquire)
    }
}

/// Runtime manager for handling cleanup tasks
pub struct RuntimeManager {
    /// Background task handle for cleanup
    cleanup_handle: Option<tokio::task::JoinHandle<()>>,
}

impl RuntimeManager {
    /// Create a new runtime manager
    pub fn new() -> Self {
        Self {
            cleanup_handle: None,
        }
    }

    /// Start background cleanup task
    pub fn start_cleanup_task(&mut self, _interval: Duration) -> JniResult<()> {
        if self.cleanup_handle.is_some() {
            return Err(JniError::Runtime("Cleanup task already running".to_string()));
        }

        // This would typically use a global runtime for cleanup tasks
        // For now, we'll skip this as cleanup will be handled per-client
        Ok(())
    }

    /// Stop the cleanup task
    pub fn stop_cleanup_task(&mut self) {
        if let Some(handle) = self.cleanup_handle.take() {
            handle.abort();
        }
    }
}

impl Default for RuntimeManager {
    fn default() -> Self {
        Self::new()
    }
}

impl Drop for JniRuntime {
    fn drop(&mut self) {
        // Ensure graceful shutdown
        self.shutdown();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[test]
    fn test_runtime_creation() {
        let runtime = JniRuntime::new("test-client").unwrap();
        assert!(!runtime.is_shutting_down());
    }

    #[test]
    fn test_runtime_block_on() {
        let runtime = JniRuntime::new("test-client").unwrap();
        
        let result = runtime.block_on(async {
            tokio::time::sleep(Duration::from_millis(10)).await;
            42
        }).unwrap();

        assert_eq!(result, 42);
    }

    #[test]
    fn test_runtime_spawn() {
        let runtime = JniRuntime::new("test-client").unwrap();
        
        let handle = runtime.spawn(async {
            tokio::time::sleep(Duration::from_millis(10)).await;
            "test"
        }).unwrap();

        let result = runtime.block_on(handle).unwrap().unwrap();
        assert_eq!(result, "test");
    }

    #[test]
    fn test_runtime_spawn_with_timeout() {
        let runtime = JniRuntime::new("test-client").unwrap();
        
        // Test successful completion within timeout
        let handle = runtime.spawn_with_timeout(
            async {
                tokio::time::sleep(Duration::from_millis(10)).await;
                "success"
            },
            Duration::from_millis(100),
        ).unwrap();

        let result = runtime.block_on(handle).unwrap().unwrap().unwrap();
        assert_eq!(result, "success");

        // Test timeout
        let handle = runtime.spawn_with_timeout(
            async {
                tokio::time::sleep(Duration::from_millis(100)).await;
                "timeout"
            },
            Duration::from_millis(10),
        ).unwrap();

        let result = runtime.block_on(handle).unwrap().unwrap();
        assert!(result.is_err());
        if let Err(JniError::Timeout(_)) = result {
            // Expected timeout error
        } else {
            panic!("Expected timeout error");
        }
    }

    #[test]
    fn test_runtime_shutdown() {
        let runtime = JniRuntime::new("test-client").unwrap();
        
        runtime.shutdown();
        assert!(runtime.is_shutting_down());

        // Should return error after shutdown
        let result = runtime.block_on(async { 42 });
        assert!(result.is_err());
    }
}