// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

/// Glide Cache Module
pub mod glide_cache;
/// LFU Cache Implementation
pub mod lfu_cache;
/// LRU Cache Implementation
pub mod lru_cache;

use glide_cache::{CacheConfig, GlideCache};
use lazy_static::lazy_static;
use std::{
    collections::HashMap,
    sync::{Arc, RwLock, Weak},
    time::Duration,
};
use tokio::task::JoinHandle;
use tracing::{debug, info};

/// Interval between cache registry housekeeping runs (cleanup of dead weak references)
const HOUSEKEEPING_INTERVAL: Duration = Duration::from_secs(60);

lazy_static! {
    /// Registry of all active caches (weak references)
    static ref CACHE_REGISTRY: RwLock<HashMap<String, Weak<dyn GlideCache>>> =
        RwLock::new(HashMap::new());

    /// Handle to the background housekeeping task
    static ref HOUSEKEEPING_HANDLE: std::sync::Mutex<Option<JoinHandle<()>>> =
        std::sync::Mutex::new(None);
}

/// Cache eviction policy
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum EvictionPolicy {
    /// Least Recently Used - Evicts the least recently accessed entry.
    /// Best for workloads with temporal locality (recent items are likely to be accessed again).
    #[default]
    Lru,

    /// Least Frequently Used - Evicts entries with the lowest access count.
    /// Best for workloads where popular items should stay cached regardless of recency.
    Lfu,
}

/// Creates (or retrieves) a cache with the given ID.
/// If the cache already exists, returns the existing one (new config is ignored).
/// If it doesn't exist, creates a new one with the specified configuration.
///
/// # Arguments
/// * `cache_id` - Unique identifier for the cache
/// * `max_cache_kb` - Maximum cache size in kilobytes
/// * `ttl_ms` - Time-to-live in milliseconds (0 = no expiration)
/// * `eviction_policy` - Eviction policy (LRU or LFU, defaults to LRU)
/// * `enable_metrics` - Whether to enable metrics tracking, such as hit/miss counts.
#[must_use]
pub fn get_or_create_cache(
    cache_id: &str,
    max_cache_kb: u64,
    ttl_ms: u64,
    eviction_policy: Option<EvictionPolicy>,
    enable_metrics: bool,
) -> Arc<dyn GlideCache> {
    // Fast path: try to get existing cache with read lock
    if let Some(cache) = CACHE_REGISTRY
        .read()
        .unwrap()
        .get(cache_id)
        .and_then(Weak::upgrade)
    {
        debug!(
            "cache_lifetime - Cache `{cache_id}` already exists — returning existing instance. \
             New config parameters (max_cache_kb={max_cache_kb}, ttl_ms={ttl_ms}, \
             eviction_policy={eviction_policy:?}, enable_metrics={enable_metrics}) are ignored. \
             Drop all references to recreate with different config."
        );
        return cache;
    }

    // Slow path: acquire write lock and double-check
    let mut registry = CACHE_REGISTRY.write().unwrap();

    // Double-check: another thread may have created the cache while we waited
    if let Some(cache) = registry.get(cache_id).and_then(Weak::upgrade) {
        debug!(
            "cache_lifetime - Cache `{cache_id}` already exists (after write lock) — returning existing instance. \
             New config parameters are ignored."
        );
        return cache;
    }

    // Create cache configuration
    let config = CacheConfig {
        max_memory_bytes: max_cache_kb.saturating_mul(1024), // Convert KB to bytes
        ttl: if ttl_ms > 0 {
            Some(Duration::from_millis(ttl_ms))
        } else {
            None
        },
        enable_metrics,
    };

    // Create cache based on eviction policy
    let policy = eviction_policy.unwrap_or_default();
    let cache: Arc<dyn GlideCache> = match policy {
        EvictionPolicy::Lru => lru_cache::new_lru_cache(config),
        EvictionPolicy::Lfu => lfu_cache::new_lfu_cache(config),
    };

    info!(
        "cache_creation - Creating {policy:?} cache `{cache_id}` (max={}KB, ttl={}ms)",
        max_cache_kb, ttl_ms
    );

    // Store weak reference in registry
    registry.insert(cache_id.to_string(), Arc::downgrade(&cache));
    drop(registry); // Release write lock

    // Start housekeeping task if this is the first cache
    start_cache_housekeeping();

    cache
}

/// Periodically cleans up dead weak references from the cache registry
async fn periodic_cache_housekeeping(interval: Duration) {
    info!("cache_housekeeping - Started cache registry cleanup task (interval: {interval:?})");

    loop {
        tokio::time::sleep(interval).await;

        let live_count = {
            let mut registry = CACHE_REGISTRY.write().unwrap();
            let before = registry.len();
            registry.retain(|_, weak| weak.upgrade().is_some());
            let after = registry.len();

            if before > after {
                debug!(
                    "cache_housekeeping - Cleaned up {} dead cache references",
                    before - after
                );
            }
            after
        };

        // If no live caches remain, stop the housekeeping task
        if live_count == 0 {
            info!("cache_housekeeping - No live caches remaining, stopping registry cleanup task");
            break;
        }

        debug!("cache_housekeeping - Registry health: {live_count} live caches");
    }

    info!("cache_housekeeping - Cache registry cleanup task stopped");
}

/// Start the cache housekeeping background task if not already running
fn start_cache_housekeeping() {
    let mut handle_guard = HOUSEKEEPING_HANDLE.lock().unwrap();

    // Check if task exists AND is still running
    if handle_guard.as_ref().is_some_and(|h| !h.is_finished()) {
        debug!("cache_housekeeping - Housekeeping task already running");
        return;
    }

    info!("cache_housekeeping - Started cache housekeeping task");

    *handle_guard = Some(tokio::spawn(periodic_cache_housekeeping(
        HOUSEKEEPING_INTERVAL,
    )));
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cleanup_cache(cache_id: &str) {
        CACHE_REGISTRY.write().unwrap().remove(cache_id);
    }

    // ==================== EvictionPolicy ====================

    #[tokio::test]
    async fn test_eviction_policy_default() {
        assert_eq!(EvictionPolicy::default(), EvictionPolicy::Lru);
    }

    #[tokio::test]
    async fn test_eviction_policy_debug() {
        assert_eq!(format!("{:?}", EvictionPolicy::Lru), "Lru");
        assert_eq!(format!("{:?}", EvictionPolicy::Lfu), "Lfu");
    }

    #[tokio::test]
    async fn test_eviction_policy_clone() {
        let policy = EvictionPolicy::Lfu;
        assert_eq!(policy, policy.clone());
    }

    // ==================== get_or_create_cache ====================

    #[tokio::test]
    async fn test_create_lru_cache() {
        let cache =
            get_or_create_cache("test_lru_cache", 1024, 0, Some(EvictionPolicy::Lru), false);
        assert_eq!(cache.entry_count(), 0);
        cleanup_cache("test_lru_cache");
    }

    #[tokio::test]
    async fn test_create_lfu_cache() {
        let cache =
            get_or_create_cache("test_lfu_cache", 1024, 0, Some(EvictionPolicy::Lfu), false);
        assert_eq!(cache.entry_count(), 0);
        cleanup_cache("test_lfu_cache");
    }

    #[tokio::test]
    async fn test_create_cache_with_metrics() {
        let cache = get_or_create_cache("test_metrics_cache", 1024, 0, None, true);
        assert!(cache.metrics().is_ok());
        cleanup_cache("test_metrics_cache");
    }

    #[tokio::test]
    async fn test_create_cache_without_metrics() {
        let cache = get_or_create_cache("test_no_metrics_cache", 1024, 0, None, false);
        assert!(cache.metrics().is_err());
        cleanup_cache("test_no_metrics_cache");
    }

    #[tokio::test]
    async fn test_get_existing_cache() {
        let cache_id = "test_get_existing";
        let cache1 = get_or_create_cache(cache_id, 1024, 0, None, false);
        let cache2 = get_or_create_cache(cache_id, 2048, 30000, Some(EvictionPolicy::Lfu), true);

        assert!(Arc::ptr_eq(&cache1, &cache2));
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_different_cache_ids_create_different_caches() {
        let cache1 = get_or_create_cache("test_diff_1", 1024, 0, None, false);
        let cache2 = get_or_create_cache("test_diff_2", 1024, 0, None, false);

        assert!(!Arc::ptr_eq(&cache1, &cache2));
        cleanup_cache("test_diff_1");
        cleanup_cache("test_diff_2");
    }

    // ==================== Cache Registry ====================

    #[tokio::test]
    async fn test_cache_registered_after_creation() {
        let cache_id = "test_registered";
        let exists_before = CACHE_REGISTRY.read().unwrap().contains_key(cache_id);

        let _cache = get_or_create_cache(cache_id, 1024, 0, None, false);

        let exists_after = CACHE_REGISTRY.read().unwrap().contains_key(cache_id);

        assert!(!exists_before);
        assert!(exists_after);
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_weak_reference_upgrades_while_cache_alive() {
        let cache_id = "test_weak_upgrade";
        let cache = get_or_create_cache(cache_id, 1024, 0, None, false);

        let upgraded = CACHE_REGISTRY
            .read()
            .unwrap()
            .get(cache_id)
            .and_then(Weak::upgrade);

        assert!(upgraded.is_some());
        assert!(Arc::ptr_eq(&cache, &upgraded.unwrap()));
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_cache_recreated_after_drop() {
        let cache_id = "test_recreate";

        let cache1 = get_or_create_cache(cache_id, 1024, 0, None, false);
        assert!(cache1.metrics().is_err());
        drop(cache1);

        let cache2 = get_or_create_cache(cache_id, 1024, 0, None, true);
        assert!(cache2.metrics().is_ok());
        cleanup_cache(cache_id);
    }

    // ==================== Cache Operations Through Registry ====================

    #[tokio::test]
    async fn test_cache_operations_work() {
        use crate::Value;
        use glide_cache::CachedKeyType;

        let cache = get_or_create_cache("test_operations", 10_000, 0, None, false);

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"value1".to_vec()),
        );
        assert_eq!(cache.entry_count(), 1);

        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_some());
        assert_eq!(result.unwrap(), Value::BulkString(b"value1".to_vec()));

        cache.invalidate(b"key1");
        assert_eq!(cache.entry_count(), 0);

        cleanup_cache("test_operations");
    }

    // ==================== Concurrent Access ====================

    fn run_concurrent_cache_test(cache: std::sync::Arc<dyn glide_cache::GlideCache>) {
        use glide_cache::CachedKeyType;
        use std::sync::Arc;
        use std::thread;

        // Pre-populate
        for i in 0..50 {
            cache.insert(
                format!("key{i}").into_bytes(),
                CachedKeyType::String,
                crate::Value::BulkString(format!("val{i}").into_bytes()),
            );
        }

        let mut handles = vec![];

        // Spawn readers
        for _ in 0..4 {
            let c = Arc::clone(&cache);
            handles.push(thread::spawn(move || {
                for i in 0..200 {
                    let key = format!("key{}", i % 50);
                    c.get(key.as_bytes(), CachedKeyType::String);
                }
            }));
        }

        // Spawn writers
        for t in 0..4 {
            let c = Arc::clone(&cache);
            handles.push(thread::spawn(move || {
                for i in 0..200 {
                    let key = format!("key{}", (t * 200) + i);
                    c.insert(
                        key.into_bytes(),
                        CachedKeyType::String,
                        crate::Value::BulkString(b"new_val".to_vec()),
                    );
                }
            }));
        }

        // Spawn invalidators
        for _ in 0..2 {
            let c = Arc::clone(&cache);
            handles.push(thread::spawn(move || {
                for i in 0..100 {
                    let key = format!("key{}", i % 50);
                    c.invalidate(key.as_bytes());
                }
            }));
        }

        for h in handles {
            h.join().expect("Thread panicked");
        }
    }

    #[tokio::test]
    async fn test_concurrent_lru_cache() {
        let cache = get_or_create_cache(
            "test_concurrent_lru",
            100,
            0,
            Some(EvictionPolicy::Lru),
            true,
        );
        run_concurrent_cache_test(cache);
        cleanup_cache("test_concurrent_lru");
    }

    #[tokio::test]
    async fn test_concurrent_lfu_cache() {
        let cache = get_or_create_cache(
            "test_concurrent_lfu",
            100,
            0,
            Some(EvictionPolicy::Lfu),
            true,
        );
        run_concurrent_cache_test(cache);
        cleanup_cache("test_concurrent_lfu");
    }
}
