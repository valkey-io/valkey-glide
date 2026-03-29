// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use super::glide_cache::{CacheConfig, CacheEntry, EvictionStrategy, GlideCacheImpl};
use super::GlideCache;
use lru::LruCache;
use std::sync::Arc;

/// LRU eviction strategy — thin wrapper around `lru::LruCache`.
///
/// All shared logic (TTL, memory, metrics) is handled by `GlideCacheImpl`.
/// This only implements the 6 data structure operations.
#[derive(Debug)]
pub(crate) struct LruStrategy {
    cache: LruCache<Vec<u8>, CacheEntry>,
}

impl LruStrategy {
    pub fn new() -> Self {
        Self {
            cache: LruCache::unbounded(),
        }
    }
}

impl EvictionStrategy for LruStrategy {
    fn policy_name(&self) -> &'static str {
        "LRU"
    }

    fn promote(&mut self, key: &[u8]) {
        self.cache.promote(key);
    }

    fn peek(&self, key: &[u8]) -> Option<&CacheEntry> {
        self.cache.peek(key)
    }

    fn insert(&mut self, key: Vec<u8>, entry: CacheEntry) {
        self.cache.push(key, entry);
    }

    fn remove(&mut self, key: &[u8]) -> Option<CacheEntry> {
        self.cache.pop(key)
    }

    fn evict_one(&mut self) -> Option<CacheEntry> {
        self.cache.pop_lru().map(|(_, entry)| entry)
    }

    fn len(&self) -> usize {
        self.cache.len()
    }
}

/// Creates a new LRU cache with the given configuration.
pub fn new_lru_cache(config: CacheConfig) -> Arc<dyn GlideCache> {
    GlideCacheImpl::new(LruStrategy::new(), config)
}

#[cfg(test)]
mod tests {
    use crate::cache::glide_cache::CachedKeyType;
    use crate::Value;

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
        let cache = new_lru_cache(make_config(10_000));

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
        let cache = new_lru_cache(make_config(10_000));

        let result = cache.get(b"nonexistent", CachedKeyType::String);
        assert!(result.is_none());
    }

    #[test]
    fn test_type_mismatch_returns_none() {
        let cache = new_lru_cache(make_config(10_000));

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
        let cache = new_lru_cache(make_config(10_000));

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
        let cache = new_lru_cache(make_config(10_000));

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
        let cache = new_lru_cache(make_config(10_000));

        // Should not panic
        cache.invalidate(b"nonexistent");
        assert_eq!(cache.entry_count(), 0);
    }

    // ==================== TTL Expiration ====================

    #[test]
    fn test_ttl_expiration() {
        let cache = new_lru_cache(make_config_with_ttl(10_000, Duration::from_millis(100)));

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
        let cache = new_lru_cache(make_config(150));

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
        let cache = new_lru_cache(make_config(100));

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
        let cache = new_lru_cache(make_config(10_000));

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
        assert_eq!(metrics.hits(), 2);
        assert_eq!(metrics.misses(), 1);
    }

    #[test]
    fn test_metrics_invalidations() {
        let cache = new_lru_cache(make_config(10_000));

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
        assert_eq!(metrics.invalidations(), 2);
    }

    #[test]
    fn test_metrics_evictions() {
        // Small cache to force eviction
        let cache = new_lru_cache(make_config(150));

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
        assert_eq!(metrics.evictions(), 1);
    }

    #[test]
    fn test_metrics_disabled() {
        let config = CacheConfig {
            max_memory_bytes: 10_000,
            ttl: None,
            enable_metrics: false,
        };
        let cache = new_lru_cache(config);

        let result = cache.metrics();
        assert!(result.is_err());
    }

    // ==================== Entry Count ====================

    #[test]
    fn test_entry_count() {
        let cache = new_lru_cache(make_config(10_000));

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
