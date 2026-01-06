// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use super::glide_cache::{
    calculate_entry_size, CacheConfig, CacheMetrics, CachedKeyType, GlideCache,
};

use crate::{ErrorKind, RedisError, RedisResult, Value};
use logger_core::{log_debug, log_warn};
use lru::LruCache;
use std::{
    sync::{Arc, Mutex},
    time::Instant,
};

/// LRU (Least Recently Used) Cache Implementation with Lazy TTL Expiration
#[derive(Debug)]
pub struct GlideLruCache {
    /// LRU cache with automatic access ordering
    cache: Mutex<LruCache<Vec<u8>, CacheEntry>>,

    /// Cache configuration
    config: CacheConfig,

    /// Current memory usage (tracked separately)
    current_memory: Mutex<u64>,

    /// Performance statistics
    stats: Option<Mutex<CacheMetrics>>,
}

/// Cache entry containing all cached command responses for a single key
#[derive(Debug)]
struct CacheEntry {
    /// The cached value
    value: Value,
    /// Type of the Redis key
    key_type: CachedKeyType,
    /// Expiration time for this entry (None = no expiration)
    expires_at: Option<Instant>,
    /// Size of this entry in bytes
    size: u64,
}

impl CacheEntry {
    fn new(value: Value, key_type: CachedKeyType, expires_at: Option<Instant>, size: u64) -> Self {
        Self {
            value,
            key_type,
            expires_at,
            size,
        }
    }

    /// Check if this entry is expired
    #[inline]
    fn is_expired(&self) -> bool {
        self.expires_at.map_or(false, |exp| Instant::now() >= exp)
    }
}

impl GlideLruCache {
    /// Creates a new LRU cache with the given configuration.
    pub fn new(config: CacheConfig) -> Arc<Self> {
        log_debug(
            "lru_cache",
            format!(
                "Creating LRU cache with max_memory={}KB,{} metrics_enabled={}",
                config.max_memory_bytes / 1024,
                config
                    .ttl
                    .map_or("".to_string(), |ttl| format!(" ttl={:?},", ttl)),
                config.enable_metrics
            ),
        );

        let stats = if config.enable_metrics {
            Some(Mutex::new(CacheMetrics::default()))
        } else {
            None
        };

        Arc::new(Self {
            cache: Mutex::new(LruCache::unbounded()),
            config,
            current_memory: Mutex::new(0),
            stats,
        })
    }

    /// Evict LRU entries until we have enough space
    /// Also removes expired entries encountered during eviction
    fn evict_until_space_available(
        &self,
        cache: &mut LruCache<Vec<u8>, CacheEntry>,
        current_memory: &mut u64,
        required_space: u64,
    ) {
        while *current_memory + required_space > self.config.max_memory_bytes {
            if let Some((_, entry)) = cache.pop_lru() {
                *current_memory = current_memory.saturating_sub(entry.size);

                let is_expired = entry.is_expired();

                // Update stats
                if let Some(stats) = self.stats.as_ref() {
                    if let Ok(mut stats) = stats.lock() {
                        if is_expired {
                            stats.expirations += 1;
                        } else {
                            stats.evictions += 1;
                        }
                    }
                }

                log_debug(
                    format!("lru_{}", if is_expired { "expiration" } else { "eviction" }),
                    format!(
                        "{} entry (type={:?}, size={}B, remaining_memory={}B)",
                        if is_expired { "Expired" } else { "Evicted" },
                        entry.key_type,
                        entry.size,
                        *current_memory
                    ),
                );
            } else {
                // Cache is empty
                break;
            }
        }
    }

    /// Remove an expired entry if present, updating memory and stats.
    /// Returns true if an entry was removed.
    fn remove_if_expired(&self, cache: &mut LruCache<Vec<u8>, CacheEntry>, key: &[u8]) -> bool {
        // Use peek to check expiration without affecting LRU order
        let is_expired = cache.peek(key).map_or(false, |e| e.is_expired());

        if is_expired {
            if let Some(entry) = cache.pop(key) {
                let mut current_memory = self.current_memory.lock().unwrap();
                *current_memory = current_memory.saturating_sub(entry.size);

                if let Some(stats) = self.stats.as_ref() {
                    if let Ok(mut stats) = stats.lock() {
                        stats.expirations += 1;
                    }
                }

                log_debug(
                    "lru_expiration",
                    format!(
                        "Expired entry (type={:?}, size={}B, remaining_memory={}B)",
                        entry.key_type, entry.size, *current_memory
                    ),
                );
                return true;
            }
        }
        false
    }
}

impl GlideCache for GlideLruCache {
    fn get(&self, key: &[u8], expected_type: CachedKeyType) -> Option<Value> {
        let mut cache = self.cache.lock().unwrap();

        // First check if expired and remove if so
        if self.remove_if_expired(&mut cache, key) {
            return None;
        }

        if let Some(entry) = cache.get(key) {
            // Check type match
            if entry.key_type != expected_type {
                log_debug(
                    "lru_type_mismatch",
                    format!(
                        "Type mismatch: cached as {:?}, requested as {:?}",
                        entry.key_type, expected_type
                    ),
                );
                return None;
            }

            return Some(entry.value.clone());
        }

        None
    }

    fn insert(&self, key: Vec<u8>, key_type: CachedKeyType, value: Value) {
        let entry_size = calculate_entry_size(&key, &value);

        // Check if entry is too large for cache
        if entry_size > self.config.max_memory_bytes {
            log_warn(
                "lru_insert",
                format!(
                    "Entry too large for cache: {}B > {}B (max), skipping",
                    entry_size, self.config.max_memory_bytes
                ),
            );
            return;
        }

        let mut cache = self.cache.lock().unwrap();
        let mut current_memory = self.current_memory.lock().unwrap();

        // Remove existing entry if present
        if let Some(existing) = cache.pop(&key) {
            *current_memory = current_memory.saturating_sub(existing.size);
        }

        // Evict until space available
        self.evict_until_space_available(&mut cache, &mut current_memory, entry_size);

        // Insert new entry
        let expires_at = self.config.ttl.map(|ttl| Instant::now() + ttl);
        let entry = CacheEntry::new(value, key_type, expires_at, entry_size);

        cache.push(key, entry);
        *current_memory += entry_size;

        log_debug(
            "lru_insert",
            format!(
                "Inserted entry (type={:?}, size={}B{})",
                key_type,
                entry_size,
                if expires_at.is_some() {
                    ", with TTL"
                } else {
                    ""
                }
            ),
        );
    }

    fn invalidate(&self, key: &[u8]) {
        let mut cache = self.cache.lock().unwrap();
        let mut current_memory = self.current_memory.lock().unwrap();

        // Remove from cache
        if let Some(entry) = cache.pop(key) {
            // Update memory usage
            *current_memory = current_memory.saturating_sub(entry.size);

            log_debug(
                "lru_invalidate",
                format!(
                    "Invalidated entry (type={:?}, size={}B, remaining_memory={}B)",
                    entry.key_type, entry.size, *current_memory
                ),
            );

            // Update stats
            if let Some(stats) = self.stats.as_ref() {
                let mut stats = stats.lock().unwrap();
                stats.invalidations += 1;
            }
        }
    }

    fn metrics(&self) -> RedisResult<CacheMetrics> {
        let stats = self.stats.as_ref().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Cache metrics tracking is not enabled",
            ))
        })?;
        let stats = stats.lock().unwrap().clone();
        Ok(stats)
    }

    fn increment_hit(&self) {
        if let Some(stats) = self.stats.as_ref() {
            let mut stats = stats.lock().unwrap();
            stats.hits += 1;
        }
    }

    fn increment_miss(&self) {
        if let Some(stats) = self.stats.as_ref() {
            let mut stats = stats.lock().unwrap();
            stats.misses += 1;
        }
    }

    fn entry_count(&self) -> u64 {
        let cache = self.cache.lock().unwrap();
        cache.len() as u64
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread::sleep;
    use std::time::Duration;

    fn make_config(max_memory: u64) -> CacheConfig {
        CacheConfig {
            max_memory_bytes: max_memory,
            ttl: None,
            enable_metrics: true,
        }
    }

    fn make_config_with_ttl(max_memory: u64, ttl: Duration) -> CacheConfig {
        CacheConfig {
            max_memory_bytes: max_memory,
            ttl: Some(ttl),
            enable_metrics: true,
        }
    }

    // ==================== Basic Operations ====================

    #[test]
    fn test_insert_and_get() {
        let cache = GlideLruCache::new(make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );

        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_some());
        assert_eq!(result.unwrap(), Value::BulkString(b"value1".to_vec()));
    }

    #[test]
    fn test_get_nonexistent_key() {
        let cache = GlideLruCache::new(make_config(10_000));

        let result = cache.get(b"nonexistent", CachedKeyType::String);
        assert!(result.is_none());
    }

    #[test]
    fn test_type_mismatch_returns_none() {
        let cache = GlideLruCache::new(make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );

        // Request with wrong type
        let result = cache.get(b"key1", CachedKeyType::Hash);
        assert!(result.is_none());

        // Correct type still works
        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_some());
    }

    #[test]
    fn test_overwrite_existing_key() {
        let cache = GlideLruCache::new(make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );
        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value2".to_vec()),
        );

        let result = cache.get(b"key1", CachedKeyType::String);
        assert_eq!(result.unwrap(), Value::BulkString(b"value2".to_vec()));
        assert_eq!(cache.entry_count(), 1);
    }

    // ==================== Invalidation ====================

    #[test]
    fn test_invalidate() {
        let cache = GlideLruCache::new(make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );
        assert_eq!(cache.entry_count(), 1);

        cache.invalidate(b"key1");
        assert_eq!(cache.entry_count(), 0);

        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_none());
    }

    #[test]
    fn test_invalidate_nonexistent_key() {
        let cache = GlideLruCache::new(make_config(10_000));

        // Should not panic
        cache.invalidate(b"nonexistent");
        assert_eq!(cache.entry_count(), 0);
    }

    // ==================== TTL Expiration ====================

    #[test]
    fn test_ttl_expiration() {
        let cache = GlideLruCache::new(make_config_with_ttl(10_000, Duration::from_millis(100)));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );

        // Should exist before TTL expires
        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_some());

        // Wait for TTL to expire
        sleep(Duration::from_millis(110));

        // Should be expired now
        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_none());
    }

    // ==================== LRU Eviction ====================

    #[test]
    fn test_lru_eviction_least_recent_evicted() {
        // Small cache to force eviction
        let cache = GlideLruCache::new(make_config(150));

        // Insert key1 and access it to make it recently used
        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"val1".to_vec()),
        ); // Entry size ~60B

        // Insert key2 (now key1 is older)
        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"val2".to_vec()),
        );

        // Access key1 to make it most recently used
        cache.get(b"key1", CachedKeyType::String);

        // Insert key3 to trigger eviction - key2 should be evicted (least recently used)
        cache.insert(
            b"key3".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"val3".to_vec()),
        );

        // key1 should survive (most recently used)
        assert!(cache.get(b"key1", CachedKeyType::String).is_some());

        // key2 should be evicted
        assert!(cache.get(b"key2", CachedKeyType::String).is_none());

        // key3 should exist
        assert!(cache.get(b"key3", CachedKeyType::String).is_some());
    }

    #[test]
    fn test_entry_too_large_rejected() {
        let cache = GlideLruCache::new(make_config(100));

        // Try to insert entry larger than max cache size
        let large_value = Value::BulkString(vec![0u8; 200]);
        cache.insert(b"large".to_vec(), CachedKeyType::String, large_value);

        // Should not be inserted
        assert!(cache.get(b"large", CachedKeyType::String).is_none());
        assert_eq!(cache.entry_count(), 0);
    }

    // ==================== Metrics ====================

    #[test]
    fn test_metrics_hits_and_misses() {
        let cache = GlideLruCache::new(make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );

        // Simulate hits
        cache.increment_hit();
        cache.increment_hit();

        // Simulate misses
        cache.increment_miss();

        let metrics = cache.metrics().unwrap();
        assert_eq!(metrics.hits, 2);
        assert_eq!(metrics.misses, 1);
    }

    #[test]
    fn test_metrics_invalidations() {
        let cache = GlideLruCache::new(make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );
        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value2".to_vec()),
        );

        cache.invalidate(b"key1");
        cache.invalidate(b"key2");

        let metrics = cache.metrics().unwrap();
        assert_eq!(metrics.invalidations, 2);
    }

    #[test]
    fn test_metrics_evictions() {
        // Small cache to force eviction
        let cache = GlideLruCache::new(make_config(150));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        ); // Entry size ~60B
        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value2".to_vec()),
        );
        // This should trigger eviction
        cache.insert(
            b"key3".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value3".to_vec()),
        );

        let metrics = cache.metrics().unwrap();
        assert_eq!(metrics.evictions, 1);
    }

    #[test]
    fn test_metrics_disabled() {
        let config = CacheConfig {
            max_memory_bytes: 10_000,
            ttl: None,
            enable_metrics: false,
        };
        let cache = GlideLruCache::new(config);

        let result = cache.metrics();
        assert!(result.is_err());
    }

    // ==================== Entry Count ====================

    #[test]
    fn test_entry_count() {
        let cache = GlideLruCache::new(make_config(10_000));

        assert_eq!(cache.entry_count(), 0);

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"v1".to_vec()),
        );
        assert_eq!(cache.entry_count(), 1);

        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"v2".to_vec()),
        );
        assert_eq!(cache.entry_count(), 2);

        cache.invalidate(b"key1");
        assert_eq!(cache.entry_count(), 1);
    }
}
