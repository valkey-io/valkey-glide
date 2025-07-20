// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Async/sync boundary handling for the JNI bridge.
//!
//! This module provides utilities for bridging between the async glide-core
//! and the synchronous JNI interface, using callbacks instead of blocking
//! operations to maintain performance and prevent deadlocks.

use glide_core::client::Client;
use redis::cluster_routing::RoutingInfo;
use redis::{Cmd, Value};
use std::time::Duration;

use crate::callback::CallbackRegistry;
use crate::error::{JniError, JniResult};
use crate::runtime::JniRuntime;
use logger_core::{log_debug, log_warn};

/// Bridge for executing async operations from sync JNI context
#[derive(Clone)]
pub struct AsyncBridge {
    /// The runtime for executing async operations
    runtime: JniRuntime,
    /// Callback registry for request/response correlation
    callback_registry: CallbackRegistry,
}

impl AsyncBridge {
    /// Create a new async bridge with the given runtime
    pub fn new(runtime: JniRuntime) -> Self {
        Self {
            runtime,
            callback_registry: CallbackRegistry::new(),
        }
    }

    /// Execute a command asynchronously using callbacks
    pub fn execute_command_async(
        &self,
        client: Client,
        cmd: Cmd,
        timeout: Duration,
    ) -> JniResult<Value> {
        // Register callback for this request
        let (callback_id, receiver) = self.callback_registry.register_callback(timeout)?;

        // Clone client for the async task (Client is Clone and thread-safe)
        let mut client_clone = client.clone();
        let callback_registry = self.callback_registry.clone();

        // Spawn async task
        let task = async move {
            let result = client_clone.send_command(&cmd, None).await;

            // Complete the callback with the result
            if let Err(e) = callback_registry.complete_callback(callback_id, result) {
                log_warn(
                    "jni-async-bridge",
                    format!("Failed to complete callback {}: {}", callback_id, e),
                );
            }
        };

        // Spawn the task on the runtime
        self.runtime.spawn(task)?;

        // Wait for the callback to complete using sync channel with timeout
        match receiver.recv_timeout(timeout) {
            Ok(result) => result.map_err(JniError::from),
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                // Clean up the callback on timeout
                self.callback_registry.cleanup_expired_callbacks().ok();
                Err(JniError::Timeout("Command timeout".to_string()))
            }
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                Err(JniError::Runtime("Callback receiver dropped".to_string()))
            }
        }
    }

    /// Execute a command with a specific routing
    pub fn execute_command_with_routing_async(
        &self,
        client: Client,
        cmd: Cmd,
        routing: Option<RoutingInfo>,
        timeout: Duration,
    ) -> JniResult<Value> {
        // Register callback for this request
        let (callback_id, receiver) = self.callback_registry.register_callback(timeout)?;

        // Clone client and routing for the async task
        let mut client_clone = client.clone();
        let callback_registry = self.callback_registry.clone();

        // Spawn async task
        let task = async move {
            let result = client_clone.send_command(&cmd, routing).await;

            // Complete the callback with the result
            if let Err(e) = callback_registry.complete_callback(callback_id, result) {
                log_warn(
                    "jni-async-bridge",
                    format!("Failed to complete callback {}: {}", callback_id, e),
                );
            }
        };

        // Spawn the task on the runtime
        self.runtime.spawn(task)?;

        // Wait for the callback to complete using sync channel with timeout
        match receiver.recv_timeout(timeout) {
            Ok(result) => result.map_err(JniError::from),
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                // Clean up the callback on timeout
                self.callback_registry.cleanup_expired_callbacks().ok();
                Err(JniError::Timeout("Command timeout".to_string()))
            }
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                Err(JniError::Runtime("Callback receiver dropped".to_string()))
            }
        }
    }

    /// Execute a pipeline asynchronously
    pub fn execute_pipeline_async(
        &self,
        client: Client,
        pipeline: redis::Pipeline,
        routing: Option<RoutingInfo>,
        timeout: Duration,
        raise_on_error: bool,
    ) -> JniResult<Value> {
        // Register callback for this request
        let (callback_id, receiver) = self.callback_registry.register_callback(timeout)?;

        // Clone client for the async task
        let mut client_clone = client.clone();
        let callback_registry = self.callback_registry.clone();

        // Spawn async task
        let task = async move {
            let result = client_clone
                .send_pipeline(&pipeline, routing, raise_on_error, None, Default::default())
                .await;

            // Complete the callback with the result
            if let Err(e) = callback_registry.complete_callback(callback_id, result) {
                log_warn(
                    "jni-async-bridge",
                    format!("Failed to complete callback {}: {}", callback_id, e),
                );
            }
        };

        // Spawn the task on the runtime
        self.runtime.spawn(task)?;

        // Wait for the callback to complete using sync channel with timeout
        log_debug(
            "jni-async-bridge",
            format!(
                "Waiting for callback {} with timeout {:?}",
                callback_id, timeout
            ),
        );
        match receiver.recv_timeout(timeout) {
            Ok(result) => {
                log_debug(
                    "jni-async-bridge",
                    format!("Callback {} received result: {:?}", callback_id, result),
                );
                result.map_err(JniError::from)
            }
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                log_debug(
                    "jni-async-bridge",
                    format!("Callback {} timed out", callback_id),
                );
                // Clean up the callback on timeout
                self.callback_registry.cleanup_expired_callbacks().ok();
                Err(JniError::Timeout("Operation timeout".to_string()))
            }
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                log_debug(
                    "jni-async-bridge",
                    format!("Callback {} receiver dropped", callback_id),
                );
                Err(JniError::Runtime("Callback receiver dropped".to_string()))
            }
        }
    }

    /// Execute a transaction asynchronously
    pub fn execute_transaction_async(
        &self,
        client: Client,
        transaction: redis::Pipeline,
        routing: Option<RoutingInfo>,
        timeout: Duration,
        raise_on_error: bool,
    ) -> JniResult<Value> {
        // Register callback for this request
        let (callback_id, receiver) = self.callback_registry.register_callback(timeout)?;

        // Clone client for the async task
        let mut client_clone = client.clone();
        let callback_registry = self.callback_registry.clone();

        // Spawn async task
        let task = async move {
            let result = client_clone
                .send_transaction(&transaction, routing, None, raise_on_error)
                .await;

            // Complete the callback with the result
            if let Err(e) = callback_registry.complete_callback(callback_id, result) {
                log_warn(
                    "jni-async-bridge",
                    format!("Failed to complete callback {}: {}", callback_id, e),
                );
            }
        };

        // Spawn the task on the runtime
        self.runtime.spawn(task)?;

        // Wait for the callback to complete using sync channel with timeout
        log_debug(
            "jni-async-bridge",
            format!(
                "Waiting for callback {} with timeout {:?}",
                callback_id, timeout
            ),
        );
        match receiver.recv_timeout(timeout) {
            Ok(result) => {
                log_debug(
                    "jni-async-bridge",
                    format!("Callback {} received result: {:?}", callback_id, result),
                );
                result.map_err(JniError::from)
            }
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                log_debug(
                    "jni-async-bridge",
                    format!("Callback {} timed out", callback_id),
                );
                // Clean up the callback on timeout
                self.callback_registry.cleanup_expired_callbacks().ok();
                Err(JniError::Timeout("Operation timeout".to_string()))
            }
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                log_debug(
                    "jni-async-bridge",
                    format!("Callback {} receiver dropped", callback_id),
                );
                Err(JniError::Runtime("Callback receiver dropped".to_string()))
            }
        }
    }

    /// Get the number of pending callbacks
    pub fn pending_callbacks(&self) -> JniResult<usize> {
        self.callback_registry.pending_count()
    }

    /// Clean up expired callbacks
    pub fn cleanup_expired_callbacks(&self) -> JniResult<usize> {
        let expired = self.callback_registry.cleanup_expired_callbacks()?;
        Ok(expired.len())
    }

    /// Check if the runtime is shutting down
    pub fn is_shutting_down(&self) -> bool {
        self.runtime.is_shutting_down()
    }

    /// Shutdown the async bridge
    pub fn shutdown(&self) {
        self.runtime.shutdown();
    }

    /// Get access to the runtime for client creation
    pub fn runtime(&self) -> &JniRuntime {
        &self.runtime
    }
}

/// Helper function to create a command from string and arguments
pub fn create_command(command: &str, args: &[&[u8]]) -> redis::Cmd {
    let mut cmd = redis::cmd(command);
    for arg in args {
        cmd.arg(arg);
    }
    cmd
}

/// Helper function to validate timeout duration
pub fn validate_timeout(timeout_ms: i32) -> JniResult<Duration> {
    if timeout_ms < 0 {
        return Err(JniError::InvalidInput(
            "Timeout cannot be negative".to_string(),
        ));
    }

    if timeout_ms == 0 {
        // Default timeout
        Ok(Duration::from_millis(5000))
    } else if timeout_ms > 300_000 {
        // Maximum 5 minutes
        Err(JniError::InvalidInput(
            "Timeout too large (max 5 minutes)".to_string(),
        ))
    } else {
        Ok(Duration::from_millis(timeout_ms as u64))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::runtime::JniRuntime;
    use std::time::Duration;

    #[test]
    fn test_async_bridge_creation() {
        let runtime = JniRuntime::new("test-bridge").unwrap();
        let bridge = AsyncBridge::new(runtime);

        assert_eq!(bridge.pending_callbacks().unwrap(), 0);
        assert!(!bridge.is_shutting_down());
    }

    #[test]
    fn test_create_command() {
        let cmd = create_command("SET", &[b"key", b"value"]);
        // Basic validation that command was created
        assert!(cmd.arg_idx(0).is_some());
    }

    #[test]
    fn test_validate_timeout() {
        // Valid timeout
        assert!(validate_timeout(1000).is_ok());
        assert_eq!(validate_timeout(1000).unwrap(), Duration::from_millis(1000));

        // Zero timeout (default)
        assert!(validate_timeout(0).is_ok());
        assert_eq!(validate_timeout(0).unwrap(), Duration::from_millis(5000));

        // Negative timeout
        assert!(validate_timeout(-1).is_err());

        // Too large timeout
        assert!(validate_timeout(400_000).is_err());
    }

    #[test]
    fn test_async_bridge_shutdown() {
        let runtime = JniRuntime::new("test-bridge").unwrap();
        let bridge = AsyncBridge::new(runtime);

        bridge.shutdown();
        assert!(bridge.is_shutting_down());
    }
}
