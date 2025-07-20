// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Callback system for async request/response correlation in JNI bridge.
//!
//! This module provides a callback registry system that allows proper correlation
//! between async requests and responses in the JNI bridge, eliminating the need
//! for blocking operations.

use redis::Value;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use crate::error::{JniError, JniResult};

/// Unique callback ID type
pub type CallbackId = u32;

/// Callback data structure containing the response sender and metadata
pub struct CallbackData {
    pub sender: Sender<redis::RedisResult<Value>>,
    pub created_at: Instant,
    pub timeout_duration: Duration,
}

/// Registry for managing async callbacks
#[derive(Clone)]
pub struct CallbackRegistry {
    /// Map of callback ID to callback data
    callbacks: std::sync::Arc<Mutex<HashMap<CallbackId, CallbackData>>>,
    /// Atomic counter for generating unique callback IDs
    next_callback_id: std::sync::Arc<AtomicU32>,
}

impl CallbackRegistry {
    /// Create a new callback registry
    pub fn new() -> Self {
        Self {
            callbacks: std::sync::Arc::new(Mutex::new(HashMap::new())),
            next_callback_id: std::sync::Arc::new(AtomicU32::new(1)),
        }
    }

    /// Register a new callback and return its ID
    pub fn register_callback(
        &self,
        timeout_duration: Duration,
    ) -> JniResult<(CallbackId, Receiver<redis::RedisResult<Value>>)> {
        let callback_id = self.next_callback_id.fetch_add(1, Ordering::SeqCst);
        let (sender, receiver) = mpsc::channel();

        let callback_data = CallbackData {
            sender,
            created_at: Instant::now(),
            timeout_duration,
        };

        let mut callbacks = self
            .callbacks
            .lock()
            .map_err(|_| JniError::LockPoisoned("Callback registry lock poisoned".to_string()))?;

        callbacks.insert(callback_id, callback_data);
        Ok((callback_id, receiver))
    }

    /// Complete a callback with a result
    pub fn complete_callback(
        &self,
        callback_id: CallbackId,
        result: redis::RedisResult<Value>,
    ) -> JniResult<()> {
        let mut callbacks = self
            .callbacks
            .lock()
            .map_err(|_| JniError::LockPoisoned("Callback registry lock poisoned".to_string()))?;

        if let Some(callback_data) = callbacks.remove(&callback_id) {
            // Send the result to the waiting receiver
            callback_data.sender.send(result).map_err(|_| {
                JniError::Runtime("Failed to send callback result - receiver dropped".to_string())
            })?;
        } else {
            return Err(JniError::InvalidHandle(format!(
                "Callback ID {} not found",
                callback_id
            )));
        }

        Ok(())
    }

    /// Clean up expired callbacks
    pub fn cleanup_expired_callbacks(&self) -> JniResult<Vec<CallbackId>> {
        let mut callbacks = self
            .callbacks
            .lock()
            .map_err(|_| JniError::LockPoisoned("Callback registry lock poisoned".to_string()))?;

        let now = Instant::now();
        let mut expired_ids = Vec::new();

        // Find expired callbacks
        callbacks.retain(|&callback_id, callback_data| {
            if now.duration_since(callback_data.created_at) > callback_data.timeout_duration {
                expired_ids.push(callback_id);
                false // Remove from map
            } else {
                true // Keep in map
            }
        });

        // Send timeout errors to expired callbacks
        for _callback_id in &expired_ids {
            // The callback has already been removed from the map, so we can't send to it
            // The receiver will get a RecvError which indicates the sender was dropped
        }

        Ok(expired_ids)
    }

    /// Get the current number of pending callbacks
    pub fn pending_count(&self) -> JniResult<usize> {
        let callbacks = self
            .callbacks
            .lock()
            .map_err(|_| JniError::LockPoisoned("Callback registry lock poisoned".to_string()))?;
        Ok(callbacks.len())
    }
}

impl Default for CallbackRegistry {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use redis::Value;
    use std::time::Duration;

    #[tokio::test]
    async fn test_callback_registry_basic_flow() {
        let registry = CallbackRegistry::new();
        let timeout_duration = Duration::from_secs(1);

        // Register a callback
        let (callback_id, receiver) = registry.register_callback(timeout_duration).unwrap();
        assert_eq!(callback_id, 1);

        // Check pending count
        assert_eq!(registry.pending_count().unwrap(), 1);

        // Complete the callback
        let result = Ok(Value::SimpleString("test".to_string()));
        registry
            .complete_callback(callback_id, result.clone())
            .unwrap();

        // Check pending count after completion
        assert_eq!(registry.pending_count().unwrap(), 0);

        // Receive the result
        let received = receiver.await.unwrap().unwrap();
        if let Value::SimpleString(s) = received {
            assert_eq!(s, "test");
        } else {
            panic!("Expected SimpleString");
        }
    }

    #[tokio::test]
    async fn test_callback_timeout() {
        let registry = CallbackRegistry::new();
        let timeout_duration = Duration::from_millis(10);

        // Register a callback
        let (callback_id, receiver) = registry.register_callback(timeout_duration).unwrap();

        // Wait for timeout
        tokio::time::sleep(Duration::from_millis(20)).await;

        // Clean up expired callbacks
        let expired = registry.cleanup_expired_callbacks().unwrap();
        assert_eq!(expired, vec![callback_id]);

        // Check that receiver fails
        let result = receiver.await;
        assert!(result.is_err()); // Should be RecvError because sender was dropped
    }

    #[tokio::test]
    async fn test_multiple_callbacks() {
        let registry = CallbackRegistry::new();
        let timeout_duration = Duration::from_secs(1);

        // Register multiple callbacks
        let (id1, receiver1) = registry.register_callback(timeout_duration).unwrap();
        let (id2, receiver2) = registry.register_callback(timeout_duration).unwrap();

        assert_eq!(id1, 1);
        assert_eq!(id2, 2);
        assert_eq!(registry.pending_count().unwrap(), 2);

        // Complete both callbacks
        registry
            .complete_callback(id1, Ok(Value::SimpleString("first".to_string())))
            .unwrap();
        registry
            .complete_callback(id2, Ok(Value::SimpleString("second".to_string())))
            .unwrap();

        // Check results
        let result1 = receiver1.await.unwrap().unwrap();
        let result2 = receiver2.await.unwrap().unwrap();

        if let Value::SimpleString(s) = result1 {
            assert_eq!(s, "first");
        } else {
            panic!("Expected SimpleString");
        }

        if let Value::SimpleString(s) = result2 {
            assert_eq!(s, "second");
        } else {
            panic!("Expected SimpleString");
        }

        assert_eq!(registry.pending_count().unwrap(), 0);
    }
}
