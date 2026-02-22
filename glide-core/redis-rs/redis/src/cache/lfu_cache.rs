// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use logger_core::{log_debug, log_warn};

use crate::cache::glide_cache::{
    calculate_entry_size, CacheCore, CacheEntry, CachedKeyType, GlideCache,
};
use crate::Value;

use super::glide_cache::CacheConfig;
use std::collections::{HashMap, HashSet};
use std::{
    sync::{Arc, Mutex},
    time::Instant,
};

/// LFU (Least Frequently Used) Cache Implementation with Lazy TTL Expiration
///
/// This cache evicts entries based on access frequency. When multiple entries
/// have the same frequency, the oldest one (by last access time) is evicted.
/// TTL expiration is handled lazily - entries are checked when accessed or during eviction.
///
/// Uses O(1) minimum frequency tracking for efficient eviction.
#[derive(Debug)]
pub struct GlideLfuCache {
    /// All mutable LFU state behind a single mutex to prevent lock-ordering issues and reduce locking overhead.
    inner: Mutex<LfuInner>,

    /// Shared cache core (config, memory tracking, metrics)
    core: CacheCore,
}

/// All mutable LFU state grouped under a single lock
#[derive(Debug)]
struct LfuInner {
    /// Main storage: key -> cache entry
    cache: HashMap<Vec<u8>, LfuCacheEntry>,
    /// Frequency bucket management with O(1) min tracking
    freq_buckets: FrequencyBuckets,
}

/// LFU-specific cache entry wrapping the shared CacheEntry with frequency tracking
#[derive(Debug)]
struct LfuCacheEntry {
    /// Base cache entry (value, key_type, expires_at, size)
    base: CacheEntry,

    /// Access frequency counter
    frequency: u64,

    /// Last access time (for tie-breaking within same frequency)
    last_access: Instant,
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
    fn update_min_frequency(&mut self) {
        self.min_frequency = self.buckets.keys().copied().min().unwrap_or(0);
    }

    /// Check if buckets are empty
    fn is_empty(&self) -> bool {
        self.buckets.is_empty()
    }
}

impl LfuCacheEntry {
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

impl GlideLfuCache {
    /// Creates a new LFU cache with the given configuration.
    pub fn new(config: CacheConfig) -> Arc<Self> {
        log_debug(
            "lfu_cache",
            format!(
                "Creating LFU cache with max_memory={}KB,{} metrics_enabled={}",
                config.max_memory_bytes / 1024,
                config
                    .ttl
                    .map_or(String::new(), |ttl| format!(" ttl={:?},", ttl)),
                config.enable_metrics
            ),
        );

        Arc::new(Self {
            inner: Mutex::new(LfuInner {
                cache: HashMap::new(),
                freq_buckets: FrequencyBuckets::new(),
            }),
            core: CacheCore::new(config),
        })
    }

    /// Evict LFU entries until we have enough space.
    /// Collects candidates per frequency bucket and sorts by access time once,
    /// then evicts in order
    fn evict_until_space_available(&self, inner: &mut LfuInner, required_space: u64) {
        while self.core.needs_eviction(required_space) {
            if inner.freq_buckets.is_empty() {
                break;
            }

            let min_freq = inner.freq_buckets.min_frequency;

            // Collect all candidates from min frequency bucket
            let Some(bucket) = inner.freq_buckets.buckets.get(&min_freq) else {
                // Bucket doesn't exist (shouldn't happen), update min and retry
                inner.freq_buckets.update_min_frequency();
                continue;
            };

            let mut candidates: Vec<(Vec<u8>, Instant)> = bucket
                .iter()
                .filter_map(|key| {
                    inner
                        .cache
                        .get(key)
                        .map(|entry| (key.clone(), entry.last_access))
                })
                .collect();

            if candidates.is_empty() {
                // Empty bucket, update min frequency and continue
                inner.freq_buckets.buckets.remove(&min_freq);
                inner.freq_buckets.update_min_frequency();
                continue;
            }

            // Sort by access time (oldest first)
            candidates.sort_unstable_by_key(|(_, access_time)| *access_time);

            // Evict in order until we have enough space or exhaust this frequency bucket
            for (key, _) in candidates {
                // Check if we have enough space now
                if !self.core.needs_eviction(required_space) {
                    return;
                }

                if let Some(entry) = inner.cache.remove(&key) {
                    // Remove from frequency bucket
                    inner.freq_buckets.remove(&key, entry.frequency);

                    // Update memory
                    self.core.uncharge(entry.base.size);
                    let is_expired = entry.base.is_expired();

                    if let Some(stats) = self.core.stats() {
                        if is_expired {
                            stats.record_expiration();
                        } else {
                            stats.record_eviction();
                        }
                    }

                    log_debug(
                        format!("lfu_{}", if is_expired { "expiration" } else { "eviction" }),
                        format!(
                            "{} entry (freq={}, type={:?}, size={}B, remaining_memory={}B)",
                            if is_expired { "Expired" } else { "Evicted" },
                            entry.frequency,
                            entry.base.key_type,
                            entry.base.size,
                            self.core.current_memory()
                        ),
                    );
                }
            }

            // After exhausting this frequency bucket, loop continues
            // min_frequency has been updated by remove() calls
        }
    }

    /// Remove an expired entry if present, updating memory and stats.
    /// Returns true if an entry was removed.
    fn remove_if_expired(&self, inner: &mut LfuInner, key: &[u8]) -> bool {
        let is_expired = inner.cache.get(key).map_or(false, |e| e.base.is_expired());

        if is_expired {
            if let Some(entry) = inner.cache.remove(key) {
                inner.freq_buckets.remove(key, entry.frequency);

                self.core.uncharge(entry.base.size);

                if let Some(stats) = self.core.stats() {
                    stats.record_expiration();
                }

                log_debug(
                    "lfu_expiration",
                    format!(
                        "Expired entry (freq={}, type={:?}, size={}B, remaining_memory={}B)",
                        entry.frequency,
                        entry.base.key_type,
                        entry.base.size,
                        self.core.current_memory()
                    ),
                );
                return true;
            }
        }
        false
    }
}

impl GlideCache for GlideLfuCache {
    fn core(&self) -> &CacheCore {
        &self.core
    }

    fn get(&self, key: &[u8], expected_type: CachedKeyType) -> Option<Value> {
        let mut inner = self.inner.lock().unwrap();

        // Check if expired and remove if so
        if self.remove_if_expired(&mut inner, key) {
            return None;
        }

        let entry = inner.cache.get_mut(key)?;

        // Check type match
        if entry.base.key_type != expected_type {
            log_debug(
                "lfu_type_mismatch",
                format!(
                    "Type mismatch: cached as {:?}, requested as {:?}",
                    entry.base.key_type, expected_type
                ),
            );
            return None;
        }

        let value = entry.base.value.clone();

        // Update frequency on successful access
        let old_frequency = entry.frequency;
        entry.touch();
        inner.freq_buckets.increment(key, old_frequency);

        Some(value)
    }

    fn insert(&self, key: Vec<u8>, key_type: CachedKeyType, value: Value) {
        let entry_size = calculate_entry_size(&key, &value);

        // Check if entry is too large for cache
        if self.core.entry_too_big(entry_size) {
            log_warn(
                "lfu_insert",
                format!(
                    "Entry too large for cache: {}B > {}B (max), skipping",
                    entry_size,
                    self.core.max_memory()
                ),
            );
            return;
        }

        let mut inner = self.inner.lock().unwrap();

        // Remove existing entry if present
        if let Some(existing) = inner.cache.remove(&key) {
            inner.freq_buckets.remove(&key, existing.frequency);
            self.core.uncharge(existing.base.size);
        }

        // Evict until space available
        self.evict_until_space_available(&mut inner, entry_size);

        // Insert new entry
        let expires_at = self.core.compute_expires_at();
        let base_entry = CacheEntry::new(value, key_type, expires_at, entry_size);
        let entry = LfuCacheEntry::new(base_entry);

        inner.freq_buckets.add(key.clone(), entry.frequency);
        inner.cache.insert(key, entry);
        self.core.charge(entry_size);

        log_debug(
            "lfu_insert",
            format!(
                "Inserted entry (type={:?}, size={}B, freq=1{})",
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
        let mut inner = self.inner.lock().unwrap();

        // Remove from cache
        if let Some(entry) = inner.cache.remove(key) {
            // Remove from frequency bucket
            inner.freq_buckets.remove(key, entry.frequency);

            // Update memory usage
            self.core.uncharge(entry.base.size);

            log_debug(
                "lfu_invalidate",
                format!(
                    "Invalidated entry (freq={}, type={:?}, size={}B, remaining_memory={}B)",
                    entry.frequency,
                    entry.base.key_type,
                    entry.base.size,
                    self.core.current_memory()
                ),
            );

            // Update stats
            if let Some(stats) = self.core.stats() {
                stats.record_invalidation();
            }
        }
    }

    fn entry_count(&self) -> u64 {
        self.inner.lock().unwrap().cache.len() as u64
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
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let cache = GlideLfuCache::new(make_config(10_000));

        let result = cache.get(b"nonexistent", CachedKeyType::String);
        assert!(result.is_none());
    }

    #[test]
    fn test_type_mismatch_returns_none() {
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let cache = GlideLfuCache::new(make_config(10_000));

        // Should not panic
        cache.invalidate(b"nonexistent");
        assert_eq!(cache.entry_count(), 0);
    }

    // ==================== TTL Expiration ====================

    #[test]
    fn test_ttl_expiration() {
        let cache = GlideLfuCache::new(make_config_with_ttl(10_000, Duration::from_millis(100)));

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
        let cache = GlideLfuCache::new(make_config(150));

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
        let cache = GlideLfuCache::new(make_config(150));

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
        let cache = GlideLfuCache::new(make_config(100));

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
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let cache = GlideLfuCache::new(make_config(150));

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
        let cache = GlideLfuCache::new(config);

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
        let cache = GlideLfuCache::new(make_config(10_000));

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
        let inner_lock = cache.inner.lock().unwrap();
        let entry = inner_lock.cache.get(&b"key1".to_vec()).unwrap();
        assert_eq!(entry.frequency, 6); // 1 initial + 5 gets
    }

    // ==================== Entry Count ====================

    #[test]
    fn test_entry_count() {
        let cache = GlideLfuCache::new(make_config(10_000));

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
