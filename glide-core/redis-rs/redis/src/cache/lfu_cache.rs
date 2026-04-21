// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::cache::glide_cache::{CacheEntry, EvictionStrategy, GlideCache, GlideCacheImpl};

use super::glide_cache::CacheConfig;
use std::collections::{HashMap, HashSet};
use std::{sync::Arc, time::Instant};

/// LFU (Least Frequently Used) eviction strategy with O(1) minimum frequency tracking.
///
/// This cache evicts entries based on access frequency. When multiple entries
/// have the same frequency, the oldest one (by last access time) is evicted.
/// TTL expiration is handled lazily - entries are checked when accessed or during eviction.
///
/// All shared logic (TTL, memory, metrics) is handled by `GlideCacheImpl`.
#[derive(Debug)]
pub(crate) struct LfuStrategy {
    /// Main storage: key -> LFU entry
    cache: HashMap<Vec<u8>, LfuEntry>,

    /// Frequency bucket management
    freq_buckets: FrequencyBuckets,
}

/// LFU-specific cache entry wrapping the shared CacheEntry with frequency tracking
#[derive(Debug)]
struct LfuEntry {
    /// Base cache entry (value, key_type, expires_at, size)
    base: CacheEntry,

    /// Access frequency counter
    frequency: u64,

    /// Last access time (for tie-breaking within same frequency)
    last_access: Instant,
}

impl LfuEntry {
    fn new(base: CacheEntry) -> Self {
        Self {
            base,
            frequency: 1,
            last_access: Instant::now(),
        }
    }

    /// Increment frequency and update last access time
    fn touch(&mut self) {
        self.frequency += 1;
        self.last_access = Instant::now();
    }
}

/// Manages frequency buckets for LFU eviction with O(1) minimum lookup
#[derive(Debug)]
struct FrequencyBuckets {
    /// Map from frequency to set of keys with that frequency
    buckets: HashMap<u64, HashSet<Vec<u8>>>,

    /// Track minimum frequency for O(1) eviction target lookup
    min_frequency: u64,
}

impl FrequencyBuckets {
    fn new() -> Self {
        Self {
            buckets: HashMap::new(),
            min_frequency: 0,
        }
    }

    /// Add a key to a frequency bucket
    fn add(&mut self, key: Vec<u8>, frequency: u64) {
        if self.buckets.is_empty() || frequency < self.min_frequency {
            self.min_frequency = frequency;
        }
        self.buckets.entry(frequency).or_default().insert(key);
    }

    /// Remove a key from a frequency bucket
    fn remove(&mut self, key: &[u8], frequency: u64) {
        if let Some(bucket) = self.buckets.get_mut(&frequency) {
            bucket.remove(key);

            // If bucket is empty, remove it and potentially update min_frequency
            if bucket.is_empty() {
                self.buckets.remove(&frequency);

                // If we just emptied the min frequency bucket, find new min
                if frequency == self.min_frequency {
                    self.update_min_frequency();
                }
            }
        }
    }

    /// Move a key from one frequency to another (on access)
    fn increment(&mut self, key: &[u8], old_freq: u64) -> u64 {
        let new_freq = old_freq + 1;
        self.remove(key, old_freq);
        self.add(key.to_vec(), new_freq);
        new_freq
    }

    /// Update min_frequency to the smallest frequency in buckets
    #[inline]
    fn update_min_frequency(&mut self) {
        self.min_frequency = self.buckets.keys().copied().min().unwrap_or(0);
    }

    /// Check if buckets are empty
    fn is_empty(&self) -> bool {
        self.buckets.is_empty()
    }
}

impl LfuStrategy {
    pub fn new() -> Self {
        Self {
            cache: HashMap::new(),
            freq_buckets: FrequencyBuckets::new(),
        }
    }
}

impl EvictionStrategy for LfuStrategy {
    fn policy_name(&self) -> &'static str {
        "LFU"
    }

    fn peek(&self, key: &[u8]) -> Option<&CacheEntry> {
        self.cache.get(key).map(|e| &e.base)
    }

    fn promote(&mut self, key: &[u8]) {
        if let Some(entry) = self.cache.get_mut(key) {
            let old_frequency = entry.frequency;
            entry.touch();
            self.freq_buckets.increment(key, old_frequency);
        }
    }

    fn insert(&mut self, key: Vec<u8>, entry: CacheEntry) {
        let lfu_entry = LfuEntry::new(entry);
        self.freq_buckets.add(key.clone(), lfu_entry.frequency);
        self.cache.insert(key, lfu_entry);
    }

    fn remove(&mut self, key: &[u8]) -> Option<CacheEntry> {
        let entry = self.cache.remove(key)?;
        self.freq_buckets.remove(key, entry.frequency);
        Some(entry.base)
    }

    fn evict_one(&mut self) -> Option<CacheEntry> {
        if self.freq_buckets.is_empty() {
            return None;
        }

        let min_freq = self.freq_buckets.min_frequency;
        let bucket = self.freq_buckets.buckets.get(&min_freq)?;

        // Find the oldest entry in the min-frequency bucket
        // O(n) scan of bucket, but no allocation — bucket is typically small
        let victim_key = bucket
            .iter()
            .filter_map(|key| {
                self.cache
                    .get(key)
                    .map(|entry| (key.clone(), entry.last_access))
            })
            .min_by_key(|(_, access_time)| *access_time)
            .map(|(key, _)| key)?;

        let entry = self.cache.remove(&victim_key)?;
        self.freq_buckets.remove(&victim_key, entry.frequency);
        Some(entry.base)
    }

    fn len(&self) -> usize {
        self.cache.len()
    }
}

/// Creates a new LFU cache with the given configuration.
pub fn new_lfu_cache(config: CacheConfig) -> Arc<dyn GlideCache> {
    GlideCacheImpl::new(LfuStrategy::new(), config)
}

#[cfg(test)]
mod tests {
    use crate::cache::glide_cache::{CachedKeyType, GlideCache};
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
        let cache = new_lfu_cache(make_config(10_000));

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
        let cache = new_lfu_cache(make_config(10_000));

        let result = cache.get(b"nonexistent", CachedKeyType::String);
        assert!(result.is_none());
    }

    #[test]
    fn test_type_mismatch_returns_none() {
        let cache = new_lfu_cache(make_config(10_000));

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
        let cache = new_lfu_cache(make_config(10_000));

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
        let cache = new_lfu_cache(make_config(10_000));

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
        let cache = new_lfu_cache(make_config(10_000));

        // Should not panic
        cache.invalidate(b"nonexistent");
        assert_eq!(cache.entry_count(), 0);
    }

    // ==================== TTL Expiration ====================

    #[test]
    fn test_ttl_expiration() {
        let cache = new_lfu_cache(make_config_with_ttl(10_000, Duration::from_millis(100)));

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

    // ==================== LFU Eviction ====================

    #[test]
    fn test_lfu_eviction_least_frequent_evicted() {
        // Small cache to force eviction
        let cache = new_lfu_cache(make_config(150));

        // Insert key1 and access it multiple times (high frequency)
        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"val1".to_vec()),
        ); // Entry size ~60B
        cache.get(b"key1", CachedKeyType::String); // freq = 2
        cache.get(b"key1", CachedKeyType::String); // freq = 3
        cache.get(b"key1", CachedKeyType::String); // freq = 4

        // Insert key2 with no extra accesses (low frequency = 1)
        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"val2".to_vec()),
        );

        // Insert key3 to trigger eviction
        cache.insert(
            b"key3".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"val3".to_vec()),
        );

        // key1 should survive (high frequency)
        assert!(cache.get(b"key1", CachedKeyType::String).is_some());

        // key2 should be evicted (lowest frequency)
        assert!(cache.get(b"key2", CachedKeyType::String).is_none());

        // key3 should be present
        assert!(cache.get(b"key3", CachedKeyType::String).is_some());
    }

    #[test]
    fn test_lfu_eviction_tie_breaker_by_access_time() {
        let cache = new_lfu_cache(make_config(150));

        // Insert two keys with same frequency
        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"v1".to_vec()),
        ); // Entry size ~60B
        sleep(Duration::from_millis(10)); // Ensure different timestamps
        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"v2".to_vec()),
        ); // Entry size ~60B

        // Both have frequency 1, key1 is older

        // Insert key3 to trigger eviction
        cache.insert(
            b"key3".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"v3".to_vec()),
        );

        // key2 should survive (newer), key1 should be evicted (older)
        assert!(cache.get(b"key2", CachedKeyType::String).is_some());
        assert!(cache.get(b"key1", CachedKeyType::String).is_none());
        assert!(cache.get(b"key3", CachedKeyType::String).is_some());
    }

    #[test]
    fn test_entry_too_large_rejected() {
        let cache = new_lfu_cache(make_config(100));

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
        let cache = new_lfu_cache(make_config(10_000));

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
        let cache = new_lfu_cache(make_config(10_000));

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
        let cache = new_lfu_cache(make_config(150));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        ); // Entry size ~60B
        cache.insert(
            b"key2".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value2".to_vec()),
        ); // Entry size ~60B

        // This insert should trigger eviction
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
        let cache = new_lfu_cache(config);

        let result = cache.metrics();
        assert!(result.is_err());
    }

    // ==================== Frequency Buckets ====================

    #[test]
    fn test_frequency_buckets_add_remove() {
        let mut buckets = FrequencyBuckets::new();

        buckets.add(b"key1".to_vec(), 1);
        buckets.add(b"key2".to_vec(), 1);
        buckets.add(b"key3".to_vec(), 5);

        assert_eq!(buckets.min_frequency, 1);
        assert!(!buckets.is_empty());

        buckets.remove(b"key1", 1);
        buckets.remove(b"key2", 1);

        // min should update to 5 after removing all freq=1 entries
        assert_eq!(buckets.min_frequency, 5);

        buckets.remove(b"key3", 5);
        assert!(buckets.is_empty());
    }

    #[test]
    fn test_frequency_buckets_increment() {
        let mut buckets = FrequencyBuckets::new();

        buckets.add(b"key1".to_vec(), 1);
        assert_eq!(buckets.min_frequency, 1);

        let new_freq = buckets.increment(b"key1", 1);
        assert_eq!(new_freq, 2);
        assert_eq!(buckets.min_frequency, 2);

        // Check that bucket for freq=1 is empty
        assert!(!buckets.buckets.contains_key(&1));
    }

    #[test]
    fn test_frequency_buckets_min_tracking() {
        let mut buckets = FrequencyBuckets::new();

        buckets.add(b"key1".to_vec(), 5);
        assert_eq!(buckets.min_frequency, 5);

        buckets.add(b"key2".to_vec(), 3);
        assert_eq!(buckets.min_frequency, 3);

        buckets.add(b"key3".to_vec(), 7);
        assert_eq!(buckets.min_frequency, 3); // Still 3

        buckets.remove(b"key2", 3);
        assert_eq!(buckets.min_frequency, 5); // Updated to next min
    }

    #[test]
    fn test_frequency_increases_on_get() {
        let cache = GlideCacheImpl::new(LfuStrategy::new(), make_config(10_000));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"v1".to_vec()),
        );

        // Access multiple times
        for _ in 0..5 {
            cache.get(b"key1", CachedKeyType::String);
        }

        // Verify frequency increased by checking internal state
        let inner_lock = cache.store.lock().unwrap();
        let entry = inner_lock.cache.get(&b"key1".to_vec()).unwrap();
        assert_eq!(entry.frequency, 6); // 1 initial + 5 gets
    }

    // ==================== Entry Count ====================

    #[test]
    fn test_entry_count() {
        let cache = new_lfu_cache(make_config(10_000));

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
