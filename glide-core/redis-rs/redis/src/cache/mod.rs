// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use dashmap::DashMap;
/// Glide Cache Module
pub mod glide_cache;
/// LFU Cache Implementation
pub mod lfu_cache;
/// LRU Cache Implementation
pub mod lru_cache;
use glide_cache::{CacheConfig, GlideCache};
use lfu_cache::GlideLfuCache;
use logger_core::{log_debug, log_info};
use lru_cache::GlideLruCache;
use std::{
    sync::{Arc, LazyLock, Weak},
    time::Duration,
};
use tokio::task::JoinHandle;

/// Registry of all active caches (weak references)
static CACHE_REGISTRY: LazyLock<DashMap<String, Weak<dyn GlideCache>>> =
    LazyLock::new(DashMap::new);

/// Handle to the background housekeeping task
static HOUSEKEEPING_HANDLE: LazyLock<std::sync::Mutex<Option<JoinHandle<()>>>> =
    LazyLock::new(|| std::sync::Mutex::new(None));

/// Cache eviction policy
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvictionPolicy {
    /// Least Recently Used - Evicts the least recently accessed entry.
    /// Best for workloads with temporal locality (recent items are likely to be accessed again).
    Lru,

    /// Least Frequently Used - Evicts entries with the lowest access count.
    /// Best for workloads where popular items should stay cached regardless of recency.
    Lfu,
}

impl Default for EvictionPolicy {
    fn default() -> Self {
        Self::Lru
    }
}

/// Creates (or retrieves) a cache with the given ID.
/// If the cache already exists, returns the existing one.
/// If it doesn't exist, creates a new one with the specified configuration.
///
/// # Arguments
/// * `cache_id` - Unique identifier for the cache
/// * `max_cache_kb` - Maximum cache size in kilobytes
/// * `ttl_sec` - Optional time-to-live in seconds (None = no expiration)
/// * `eviction_policy` - Eviction policy (LRU or LFU, defaults to LRU)
/// * `enable_metrics` - Whether to enable metrics tracking, such as hit/miss counts.
#[must_use]
pub fn get_or_create_cache(
    cache_id: &str,
    max_cache_kb: u64,
    ttl_sec: Option<u64>,
    eviction_policy: Option<EvictionPolicy>,
    enable_metrics: bool,
) -> Arc<dyn GlideCache> {
    // Try to get existing cache
    if let Some(weak_ref) = CACHE_REGISTRY.get(cache_id) {
        if let Some(cache) = weak_ref.upgrade() {
            log_debug(
                "cache_lifetime",
                format!("Retrieved existing cache `{cache_id}`"),
            );
            return cache;
        }
        // Cache was dropped - release read lock before removing
        drop(weak_ref);
        CACHE_REGISTRY.remove(cache_id);
    }

    // Cache does not exist - create cache configuration
    let config = CacheConfig {
        max_memory_bytes: max_cache_kb * 1024, // Convert KB to bytes
        ttl: ttl_sec.map(Duration::from_secs),
        enable_metrics,
    };

    // Create cache based on eviction policy
    let policy = eviction_policy.unwrap_or_default();
    let cache: Arc<dyn GlideCache> = match policy {
        EvictionPolicy::Lru => GlideLruCache::new(config),
        EvictionPolicy::Lfu => GlideLfuCache::new(config),
    };

    log_info(
        "cache_creation",
        format!(
            "Creating {policy:?} cache `{cache_id}` (max={}KB, ttl={:?})",
            max_cache_kb, ttl_sec
        ),
    );

    // Store weak reference in registry
    CACHE_REGISTRY.insert(cache_id.to_string(), Arc::downgrade(&cache));

    // Start housekeeping task if this is the first cache
    start_cache_housekeeping();

    cache
}

/// Periodically cleans up dead weak references from the cache registry
async fn periodic_cache_housekeeping(interval_duration: Duration) {
    log_info(
        "cache_housekeeping",
        format!(
            "Started cache registry cleanup task (interval: {:?})",
            interval_duration
        ),
    );

    loop {
        tokio::time::sleep(interval_duration).await;

        let mut live_count = 0;
        let mut dead_keys = Vec::new();

        // Scan for dead caches
        for entry in CACHE_REGISTRY.iter() {
            match entry.value().upgrade() {
                Some(_cache) => {
                    // Cache is alive
                    live_count += 1;
                }
                None => {
                    // Cache is dead, mark for removal
                    dead_keys.push(entry.key().clone());
                }
            }
        }

        // Clean up dead cache entries
        if !dead_keys.is_empty() {
            for key in &dead_keys {
                CACHE_REGISTRY.remove(key);
            }
            log_debug(
                "cache_housekeeping",
                format!("Cleaned up {} dead cache references", dead_keys.len()),
            );
        }

        // If no live caches remain, stop the housekeeping task
        if live_count == 0 && CACHE_REGISTRY.is_empty() {
            log_info(
                "cache_housekeeping",
                "No live caches remaining, stopping registry cleanup task",
            );
            break;
        }

        log_debug(
            "cache_housekeeping",
            format!("Registry health: {} live caches", live_count),
        );
    }

    log_info("cache_housekeeping", "Cache registry cleanup task stopped");
}

/// Start the cache housekeeping background task
fn start_cache_housekeeping() {
    let mut handle_guard = HOUSEKEEPING_HANDLE.lock().unwrap();

    // Check if task exists AND is still running
    if let Some(handle) = handle_guard.as_ref() {
        if !handle.is_finished() {
            log_debug("cache_housekeeping", "Housekeeping task already running");
            return;
        }
        // Task finished, clear the old handle
        log_debug("cache_housekeeping", "Previous housekeeping task finished");
    }

    let task = tokio::spawn(periodic_cache_housekeeping(Duration::from_secs(5 * 60)));
    *handle_guard = Some(task);

    log_info("cache_housekeeping", "Started cache housekeeping task");
}

#[cfg(test)]
mod tests {
    use super::*;

    // ==================== EvictionPolicy ====================

    #[tokio::test]
    async fn test_eviction_policy_default() {
        let policy = EvictionPolicy::default();
        assert_eq!(policy, EvictionPolicy::Lru);
    }

    #[tokio::test]
    async fn test_eviction_policy_debug() {
        assert_eq!(format!("{:?}", EvictionPolicy::Lru), "Lru");
        assert_eq!(format!("{:?}", EvictionPolicy::Lfu), "Lfu");
    }

    #[tokio::test]
    async fn test_eviction_policy_clone() {
        let policy = EvictionPolicy::Lfu;
        let cloned = policy.clone();
        assert_eq!(policy, cloned);
    }

    // ==================== get_or_create_cache ====================

    #[tokio::test]
    async fn test_create_lru_cache() {
        let cache = get_or_create_cache(
            "test_lru_cache",
            1024,
            None,
            Some(EvictionPolicy::Lru),
            false,
        );

        assert_eq!(cache.entry_count(), 0);

        // Cleanup
        CACHE_REGISTRY.remove("test_lru_cache");
    }

    #[tokio::test]
    async fn test_create_lfu_cache() {
        let cache = get_or_create_cache(
            "test_lfu_cache",
            1024,
            None,
            Some(EvictionPolicy::Lfu),
            false,
        );

        assert_eq!(cache.entry_count(), 0);

        // Cleanup
        CACHE_REGISTRY.remove("test_lfu_cache");
    }

    #[tokio::test]
    async fn test_create_cache_with_metrics() {
        let cache = get_or_create_cache(
            "test_metrics_cache",
            1024,
            None,
            None,
            true, // Enable metrics
        );

        // Metrics should work
        let metrics = cache.metrics();
        assert!(metrics.is_ok());

        // Cleanup
        CACHE_REGISTRY.remove("test_metrics_cache");
    }

    #[tokio::test]
    async fn test_create_cache_without_metrics() {
        let cache = get_or_create_cache(
            "test_no_metrics_cache",
            1024,
            None,
            None,
            false, // Disable metrics
        );

        // Metrics should fail
        let metrics = cache.metrics();
        assert!(metrics.is_err());

        // Cleanup
        CACHE_REGISTRY.remove("test_no_metrics_cache");
    }

    #[tokio::test]
    async fn test_get_existing_cache() {
        let cache_id = "test_get_existing";

        let cache1 = get_or_create_cache(cache_id, 1024, None, None, false);
        let cache2 = get_or_create_cache(cache_id, 2048, Some(30), Some(EvictionPolicy::Lfu), true);

        // Should return same cache (Arc pointer equality)
        assert!(Arc::ptr_eq(&cache1, &cache2));

        // Cleanup
        CACHE_REGISTRY.remove(cache_id);
    }

    #[tokio::test]
    async fn test_different_cache_ids_create_different_caches() {
        let cache1 = get_or_create_cache("test_diff_1", 1024, None, None, false);
        let cache2 = get_or_create_cache("test_diff_2", 1024, None, None, false);

        // Should be different caches
        assert!(!Arc::ptr_eq(&cache1, &cache2));

        // Cleanup
        CACHE_REGISTRY.remove("test_diff_1");
        CACHE_REGISTRY.remove("test_diff_2");
    }

    // ==================== Cache Registry ====================

    #[tokio::test]
    async fn test_cache_registered_after_creation() {
        let cache_id = "test_registered";

        // Should not exist initially
        let exists_before = CACHE_REGISTRY.contains_key(cache_id);

        let _cache = get_or_create_cache(cache_id, 1024, None, None, false);

        // Should exist after creation
        let exists_after = CACHE_REGISTRY.contains_key(cache_id);

        assert!(!exists_before);
        assert!(exists_after);

        // Cleanup
        CACHE_REGISTRY.remove(cache_id);
    }

    #[tokio::test]
    async fn test_weak_reference_upgrades_while_cache_alive() {
        let cache_id = "test_weak_upgrade";

        let cache = get_or_create_cache(cache_id, 1024, None, None, false);

        // Weak reference should upgrade while cache is held
        let upgraded = {
            let weak = CACHE_REGISTRY.get(cache_id).unwrap();
            weak.upgrade()
        };
        assert!(upgraded.is_some());

        // Should be the same cache
        assert!(Arc::ptr_eq(&cache, &upgraded.unwrap()));

        // Cleanup
        CACHE_REGISTRY.remove(cache_id);
    }

    #[tokio::test]
    async fn test_cache_recreated_after_drop() {
        let cache_id = "test_recreate";

        // Create cache WITHOUT metrics
        let cache1 = get_or_create_cache(cache_id, 1024, None, None, false);
        assert!(cache1.metrics().is_err()); // Metrics disabled

        // Drop cache
        drop(cache1);

        // Create new cache WITH metrics (different config)
        let cache2 = get_or_create_cache(cache_id, 1024, None, None, true);

        // If it's truly a new cache, metrics should now work
        assert!(cache2.metrics().is_ok()); // Metrics enabled

        // Cleanup
        CACHE_REGISTRY.remove(cache_id);
    }

    // ==================== Cache Operations Through Registry ====================

    #[tokio::test]
    async fn test_cache_operations_work() {
        use crate::Value;
        use glide_cache::CachedKeyType;

        let cache = get_or_create_cache("test_operations", 10_000, None, None, false);

        // Insert
        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );
        assert_eq!(cache.entry_count(), 1);

        // Get
        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_some());
        assert_eq!(result.unwrap(), Value::BulkString(b"value1".to_vec()));

        // Invalidate
        cache.invalidate(b"key1");
        assert_eq!(cache.entry_count(), 0);

        // Cleanup
        CACHE_REGISTRY.remove("test_operations");
    }
}
