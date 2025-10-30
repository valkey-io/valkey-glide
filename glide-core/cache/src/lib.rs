// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use dashmap::DashMap;
use logger_core::log_info;
use moka::policy::EvictionPolicy as MokaEvictionPolicy;
use moka::sync::Cache;
use once_cell::sync::Lazy;
use redis::Value;
use std::sync::{Arc, Weak};
use std::time::Duration;

static CACHE_REGISTRY: Lazy<DashMap<String, Weak<Cache<Vec<u8>, Value>>>> =
    Lazy::new(|| DashMap::new());

/// Cache eviction policy
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvictionPolicy {
    /// Least Recently Used - Evicts the least recently accessed entry.
    Lru,
    /// TinyLFU (Frequency-based) - Combines frequency and recency based eviction.
    /// When the cache is full, it only admits new entries if they're accessed more frequently than existing ones.
    /// This prevents rarely-accessed or one-time keys from evicting popular entries. **Once admitted,
    /// entries are evicted based on least recent access (LRU)**. Best for most workloads.
    TinyLfu,
}

impl Default for EvictionPolicy {
    fn default() -> Self {
        Self::Lru
    }
}

/// Gets a cache by ID.
/// Returns None if the cache doesn't exist or has been dropped.
pub fn get_cache(cache_id: &str) -> Option<Arc<Cache<Vec<u8>, Value>>> {
    CACHE_REGISTRY.get(cache_id).and_then(|entry| {
        match entry.upgrade() {
            Some(cache) => {
                log_info("cache_lifetime", format!("Retrieved cache `{cache_id}`"));
                Some(cache)
            }
            None => {
                // Cache was dropped, clean up the weak reference
                drop(entry);
                CACHE_REGISTRY.remove(cache_id);
                log_info(
                    "cache_lifetime",
                    format!("Cache `{cache_id}` was already dropped"),
                );
                None
            }
        }
    })
}

/// Creates (or retrieves) a cache with the given ID.
/// If the cache already exists, returns the existing one.
/// If it doesn't exist, creates a new one.
pub fn get_or_create_cache(
    cache_id: &str,
    max_cache_kb: u64,
    ttl_sec: Option<u64>,
    eviction_policy: Option<EvictionPolicy>,
) -> Arc<Cache<Vec<u8>, Value>> {
    // First, try to get existing cache
    if let Some(cache) = get_cache(cache_id) {
        return cache;
    }

    // Create cache with weigher to measure actual byte size
    let mut builder = Cache::builder()
        .max_capacity(max_cache_kb * 1024) // Convert KB to bytes
        .weigher(cache_entry_weigher);

    if let Some(ttl) = ttl_sec {
        builder = builder.time_to_live(Duration::from_secs(ttl));
    }

    // Configure eviction policy
    let policy = eviction_policy.unwrap_or_default();
    match policy {
        EvictionPolicy::TinyLfu => {
            builder = builder.eviction_policy(MokaEvictionPolicy::tiny_lfu());
        }
        EvictionPolicy::Lru => {
            builder = builder.eviction_policy(MokaEvictionPolicy::lru());
        }
    }

    let cache = Arc::new(builder.build());

    log_info(
        "cache_lifetime",
        format!("Created cache with ID: `{cache_id}`"),
    );

    // Store weak reference in registry
    CACHE_REGISTRY.insert(cache_id.to_string(), Arc::downgrade(&cache));

    cache
}

/// Calculates the total memory size of a Value in bytes.
/// This includes the enum overhead and all allocated data.
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

/// Weigher function for Moka cache
/// Returns the total size of the cache entry (key + value) in bytes
fn cache_entry_weigher(key: &Vec<u8>, value: &Value) -> u32 {
    let total_size = key.len() + calculate_value_size(value);
    println!(
        "Cache entry weigher: key_len = {}, value_size = {}",
        key.len(),
        calculate_value_size(value)
    );
    total_size.try_into().unwrap_or(u32::MAX)
}

#[cfg(test)]
mod tests {
    use super::*;

    // Helper to generate unique cache IDs for isolated tests
    fn test_cache_id(suffix: &str) -> String {
        format!(
            "test-cache-{}-{}",
            suffix,
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        )
    }

    #[test]
    fn test_get_or_create() {
        let cache_id = "my-cache-123";
        let cache1 = get_or_create_cache(cache_id, 100, Some(60), None);
        let cache2 = get_or_create_cache(cache_id, 100, Some(60), None);

        // Should be the same cache instance
        assert!(Arc::ptr_eq(&cache1, &cache2));
    }

    #[test]
    fn test_get_cache_nonexistent() {
        let cache_id = test_cache_id("nonexistent");

        let result = get_cache(&cache_id);
        assert!(result.is_none());
    }

    #[test]
    fn test_cache_weak_reference_cleanup() {
        let cache_id = test_cache_id("weak-ref");

        {
            let _cache = get_or_create_cache(&cache_id, 100, None, None);
            assert!(get_cache(&cache_id).is_some());
            // cache is dropped here
        }

        // Registry still has the entry (weak reference)
        assert!(CACHE_REGISTRY.contains_key(&cache_id));

        // But get_cache should clean it up and return None
        let result = get_cache(&cache_id);
        assert!(result.is_none());

        // Now it should be removed from registry
        assert!(!CACHE_REGISTRY.contains_key(&cache_id));
    }
}
