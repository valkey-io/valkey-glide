// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use crate::{
    cluster_routing::{Routable, RoutingInfo},
    cmd::cacheable_cmd_type,
    Cmd, RedisResult, Value,
};
/// Core caching interfaces and utilities for Glide
use std::{fmt::Debug, time::Duration};

/// Configuration for cache instances
#[derive(Debug, Clone)]
pub struct CacheConfig {
    /// Maximum memory usage in bytes
    pub max_memory_bytes: u64,

    /// Time-to-live for entries (None = no expiration)
    pub ttl: Option<Duration>,

    /// Enable metrics collection (hits, misses, evictions, expirations)
    pub enable_metrics: bool,
}

/// Metrics about cache performance
#[derive(Debug, Clone, Default)]
pub struct CacheMetrics {
    /// Total number of successful get operations
    pub hits: u64,

    /// Total number of failed get operations (key not found or expired)
    pub misses: u64,

    /// Total number of expired entries removed
    pub expirations: u64,

    /// Total number of entries invalidated
    pub invalidations: u64,

    /// Total number of entries evicted due to memory constraints
    pub evictions: u64,
}

impl CacheMetrics {
    /// Calculate the hit rate (hits / total requests)
    pub fn hit_rate(&self) -> f64 {
        let total = self.hits + self.misses;
        if total == 0 {
            0.0
        } else {
            self.hits as f64 / total as f64
        }
    }

    /// Calculate the miss rate (misses / total requests)
    pub fn miss_rate(&self) -> f64 {
        let total = self.hits + self.misses;
        if total == 0 {
            0.0
        } else {
            self.misses as f64 / total as f64
        }
    }
}

/// Type of Valkey key being cached
/// Used to prevent type mismatches (e.g., running HGETALL on a string key)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum CachedKeyType {
    /// String type (GET)
    String,
    /// Hash type (HGETALL)
    Hash,
    /// Set type (SMEMBERS)
    Set,
}

/// Core caching interface for Glide
pub trait GlideCache: Send + Sync + Debug {
    // ==================== Core Operations ====================

    /// Retrieves a value from the cache
    ///
    /// Returns `Some(value)` if:
    /// - The key exists
    /// - The key is not expired
    /// - The key type matches the expected type
    ///
    /// This operation may update internal access tracking (e.g., LRU order, LFU count).
    /// Expired entries may be lazily removed during this call.
    ///
    /// # Arguments
    /// * `key` - The key to look up
    /// * `expected_type` - The expected type of the cached key
    fn get(&self, key: &[u8], expected_type: CachedKeyType) -> Option<Value>;

    /// Inserts or updates a key-value pair in the cache
    ///
    /// If the key already exists, its value is replaced.
    /// If the cache is at capacity, entries will be evicted according to the
    /// implementation's eviction policy (LRU, LFU, etc.).
    ///
    /// If the entry is too large to fit in the cache (exceeds `max_memory_bytes`),
    /// it will not be inserted and the operation will silently fail.
    ///
    /// # Arguments
    /// * `key` - The key to insert
    /// * `key_type` - The type of the key being cached
    /// * `value` - The value to associate with the key
    fn insert(&self, key: Vec<u8>, key_type: CachedKeyType, value: Value);

    /// Invalidates a key from the cache
    ///
    /// # Arguments
    /// * `key` - The key to invalidate
    fn invalidate(&self, key: &[u8]);

    // ==================== Metrics ====================

    /// Returns current cache metrics (hits, misses, etc.)
    /// Returns an error if metrics tracking is not enabled.
    fn metrics(&self) -> RedisResult<CacheMetrics>;

    /// Increases the hit count for the cache
    fn increment_hit(&self);

    /// Increases the miss count for the cache
    fn increment_miss(&self);

    /// Get the current entry count in the cache
    fn entry_count(&self) -> u64;

    // ==================== Utility Methods ====================

    /// Checks if a command is cacheable and retrieves the cached value if available.
    /// Automatically updates hit/miss statistics.
    ///
    /// # Returns
    /// - `Some(value)` if the command is cacheable and the value is in cache
    /// - `None` if the command is not cacheable or the value is not in cache
    fn get_cached_cmd(&self, cmd: &Cmd) -> Option<Value> {
        let cmd_name = cmd.command()?;
        let key_type = cacheable_cmd_type(cmd_name.as_ref())?;
        let cmd_key = RoutingInfo::key_for_routable(cmd)?;

        let result = self.get(&cmd_key, key_type);

        if result.is_some() {
            self.increment_hit();
        } else {
            self.increment_miss();
        }

        result
    }

    /// Caches the result of a cacheable command.
    /// Only caches if the command is a supported cacheable command.
    ///
    /// # Arguments
    /// * `cmd` - The command that was executed
    /// * `value` - The value returned by the server
    fn set_cached_cmd(&self, cmd: &Cmd, value: Value) {
        let cmd_name = match cmd.command() {
            Some(name) => name,
            None => return,
        };

        let key_type = match cacheable_cmd_type(cmd_name.as_ref()) {
            Some(kt) => kt,
            None => return,
        };

        if let Some(cmd_key) = RoutingInfo::key_for_routable(cmd) {
            self.insert(cmd_key.to_vec(), key_type, value);
        }
    }
}

// ==================== Helper Functions ====================

/// Calculates the total memory size of a Redis Value in bytes
///
/// This includes:
/// - The enum discriminant overhead
/// - All allocated data (strings, arrays, maps, etc.)
/// - Recursive calculation for nested structures
pub fn calculate_value_size(value: &Value) -> usize {
    // Every Value has a base overhead for the enum discriminant and largest variant
    let base_overhead = std::mem::size_of::<Value>();

    // Plus any additional allocated data
    let additional_data = match value {
        Value::Nil | Value::Int(_) | Value::Double(_) | Value::Boolean(_) => 0,
        Value::BulkString(data) => data.len(),
        Value::Array(arr) => {
            arr.len() * std::mem::size_of::<Value>()
                + arr
                    .iter()
                    .map(|v| calculate_value_additional_data(v))
                    .sum::<usize>()
        }
        Value::SimpleString(s) => s.len(),
        Value::Okay => 0,
        Value::Map(map) => {
            map.len() * std::mem::size_of::<(Value, Value)>()
                + map
                    .iter()
                    .map(|(k, v)| {
                        calculate_value_additional_data(k) + calculate_value_additional_data(v)
                    })
                    .sum::<usize>()
        }
        Value::Attribute { data, attributes } => {
            std::mem::size_of::<Value>() // boxed value overhead
                + calculate_value_additional_data(data)
                + attributes.len() * std::mem::size_of::<(Value, Value)>()
                + attributes
                    .iter()
                    .map(|(k, v)| calculate_value_additional_data(k) + calculate_value_additional_data(v))
                    .sum::<usize>()
        }
        Value::Set(set) => {
            set.len() * std::mem::size_of::<Value>()
                + set
                    .iter()
                    .map(|v| calculate_value_additional_data(v))
                    .sum::<usize>()
        }
        Value::VerbatimString { format: _, text } => text.len(),
        Value::BigNumber(big_int) => {
            // BigInt allocates memory based on the number size
            ((big_int.bits() + 7) / 8) as usize // Convert bits to bytes
        }
        Value::Push { kind: _, data } => {
            data.len() * std::mem::size_of::<Value>()
                + data
                    .iter()
                    .map(|v| calculate_value_additional_data(v))
                    .sum::<usize>()
        }
        Value::ServerError(err) => {
            // Adjust based on ServerError's actual structure
            std::mem::size_of_val(err)
        }
    };

    base_overhead + additional_data
}

/// Helper function that calculates only the additional allocated data
/// (without the base enum overhead)
fn calculate_value_additional_data(value: &Value) -> usize {
    calculate_value_size(value) - std::mem::size_of::<Value>()
}

/// Calculates the total size of a cache entry (key + value)
#[inline]
pub fn calculate_entry_size(key: &[u8], value: &Value) -> u64 {
    (key.len() + calculate_value_size(value)) as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    // ==================== CacheMetrics ====================
    #[test]
    fn test_hit_rate_zero_requests() {
        let metrics = CacheMetrics::default();
        assert_eq!(metrics.hit_rate(), 0.0);
    }

    #[test]
    fn test_miss_rate_zero_requests() {
        let metrics = CacheMetrics::default();
        assert_eq!(metrics.miss_rate(), 0.0);
    }

    #[test]
    fn test_hit_rate_mixed() {
        let metrics = CacheMetrics {
            hits: 75,
            misses: 25,
            ..Default::default()
        };
        assert_eq!(metrics.hit_rate(), 0.75);
    }

    #[test]
    fn test_miss_rate_mixed() {
        let metrics = CacheMetrics {
            hits: 75,
            misses: 25,
            ..Default::default()
        };
        assert_eq!(metrics.miss_rate(), 0.25);
    }

    // ==================== calculate_value_size ====================

    #[test]
    fn test_value_size_nil() {
        let value = Value::Nil;
        let size = calculate_value_size(&value);
        assert_eq!(size, std::mem::size_of::<Value>());
    }

    #[test]
    fn test_value_size_int() {
        let value = Value::Int(42);
        let size = calculate_value_size(&value);
        assert_eq!(size, std::mem::size_of::<Value>());
    }

    #[test]
    fn test_value_size_boolean() {
        let value = Value::Boolean(true);
        let size = calculate_value_size(&value);
        assert_eq!(size, std::mem::size_of::<Value>());
    }

    #[test]
    fn test_value_size_bulk_string() {
        let data = b"hello world".to_vec();
        let data_len = data.len();
        let value = Value::BulkString(data);
        let size = calculate_value_size(&value);
        assert_eq!(size, std::mem::size_of::<Value>() + data_len);
    }

    #[test]
    fn test_value_size_simple_string() {
        let s = "hello".to_string();
        let s_len = s.len();
        let value = Value::SimpleString(s);
        let size = calculate_value_size(&value);
        assert_eq!(size, std::mem::size_of::<Value>() + s_len);
    }

    #[test]
    fn test_value_size_okay() {
        let value = Value::Okay;
        let size = calculate_value_size(&value);
        assert_eq!(size, std::mem::size_of::<Value>());
    }

    #[test]
    fn test_value_size_array() {
        let value = Value::Array(vec![Value::Int(1), Value::Int(2), Value::Int(3)]);
        let size = calculate_value_size(&value);
        // Base + 3 * sizeof(Value) for array elements
        assert!(size == 4 * std::mem::size_of::<Value>());
    }

    #[test]
    fn test_value_size_nested_array() {
        let inner = Value::Array(vec![Value::Int(1), Value::Int(2)]);
        let value = Value::Array(vec![inner]);
        let size = calculate_value_size(&value);
        // Should include nested array size
        assert!(size == 4 * std::mem::size_of::<Value>());
    }
}
