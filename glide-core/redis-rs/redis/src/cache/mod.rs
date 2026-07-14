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

use crate::{ErrorKind, RedisError, RedisResult, Value};

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

/// Cache metric types that can be queried from the registry.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CacheMetricType {
    /// Cache hit rate (hits / total lookups)
    HitRate,
    /// Cache miss rate (misses / total lookups)
    MissRate,
    /// Current number of entries stored in the cache
    EntryCount,
    /// Total number of entries evicted due to memory constraints
    Evictions,
    /// Total number of entries removed due to TTL expiration
    Expirations,
    /// Total number of cache lookups (hits + misses)
    TotalLookups,
}

/// Query a specific cache metric directly from the global cache registry.
///
/// This provides a synchronous path to read cache metrics (which are atomic counters)
/// without going through the async protobuf/UDS path.
///
/// # Arguments
/// * `cache_id` - The unique identifier of the cache to query
/// * `metric` - The metric type to retrieve
///
/// # Returns
/// * `Value::Double` for rate metrics (hit_rate, miss_rate)
/// * `Value::Int` for count metrics (entry_count, evictions, expirations, total_lookups)
pub fn query_cache_metric(cache_id: &str, metric: CacheMetricType) -> RedisResult<Value> {
    let registry = CACHE_REGISTRY.read().unwrap();
    let cache = registry
        .get(cache_id)
        .and_then(Weak::upgrade)
        .ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Client-side caching is not enabled",
            ))
        })?;
    // Safe to drop: Weak::upgrade() returned an Arc, so the cache is kept alive
    // by our strong reference regardless of the registry state.
    drop(registry);

    match metric {
        CacheMetricType::HitRate => {
            let metrics = cache.metrics()?;
            Ok(Value::Double(metrics.hit_rate()))
        }
        CacheMetricType::MissRate => {
            let metrics = cache.metrics()?;
            Ok(Value::Double(metrics.miss_rate()))
        }
        CacheMetricType::EntryCount => Ok(Value::Int(cache.entry_count() as i64)),
        CacheMetricType::Evictions => {
            let metrics = cache.metrics()?;
            Ok(Value::Int(metrics.evictions() as i64))
        }
        CacheMetricType::Expirations => {
            let metrics = cache.metrics()?;
            Ok(Value::Int(metrics.expirations() as i64))
        }
        CacheMetricType::TotalLookups => {
            let metrics = cache.metrics()?;
            Ok(Value::Int(metrics.total_lookups() as i64))
        }
    }
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
            Value::BulkString(b"value1".to_vec().into()),
        );
        assert_eq!(cache.entry_count(), 1);

        let result = cache.get(b"key1", CachedKeyType::String);
        assert!(result.is_some());
        assert_eq!(
            result.unwrap(),
            Value::BulkString(b"value1".to_vec().into())
        );

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
                crate::Value::BulkString(format!("val{i}").into_bytes().into()),
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
                        crate::Value::BulkString(b"new_val".to_vec().into()),
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

    // ==================== query_cache_metric ====================

    #[tokio::test]
    async fn test_query_cache_metric_not_found() {
        let result = query_cache_metric("nonexistent_cache", CacheMetricType::HitRate);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(err.to_string().contains("not enabled"));
    }

    #[tokio::test]
    async fn test_query_cache_metric_entry_count() {
        use glide_cache::CachedKeyType;

        let cache_id = "test_query_entry_count";
        let cache = get_or_create_cache(cache_id, 10_000, 0, None, false);

        // Entry count works without metrics enabled
        let result = query_cache_metric(cache_id, CacheMetricType::EntryCount);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), crate::Value::Int(0));

        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            crate::Value::BulkString(b"val".to_vec().into()),
        );
        let result = query_cache_metric(cache_id, CacheMetricType::EntryCount);
        assert_eq!(result.unwrap(), crate::Value::Int(1));

        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_query_cache_metric_requires_metrics_enabled() {
        let cache_id = "test_query_no_metrics";
        let _cache = get_or_create_cache(cache_id, 1024, 0, None, false);

        // Rate/count metrics should fail when metrics not enabled
        assert!(query_cache_metric(cache_id, CacheMetricType::HitRate).is_err());
        assert!(query_cache_metric(cache_id, CacheMetricType::MissRate).is_err());
        assert!(query_cache_metric(cache_id, CacheMetricType::Evictions).is_err());
        assert!(query_cache_metric(cache_id, CacheMetricType::Expirations).is_err());
        assert!(query_cache_metric(cache_id, CacheMetricType::TotalLookups).is_err());

        // Entry count should still work
        assert!(query_cache_metric(cache_id, CacheMetricType::EntryCount).is_ok());

        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_query_cache_metric_with_metrics_enabled() {
        use glide_cache::CachedKeyType;

        let cache_id = "test_query_with_metrics";
        let cache = get_or_create_cache(cache_id, 10_000, 0, None, true);

        // Initial state: all zeros
        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::HitRate).unwrap(),
            crate::Value::Double(0.0)
        );
        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::TotalLookups).unwrap(),
            crate::Value::Int(0)
        );

        // Simulate a miss then a hit
        cache.insert(
            b"key1".to_vec(),
            CachedKeyType::String,
            crate::Value::BulkString(b"val".to_vec().into()),
        );
        cache.increment_miss();
        cache.increment_hit();

        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::TotalLookups).unwrap(),
            crate::Value::Int(2)
        );
        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::HitRate).unwrap(),
            crate::Value::Double(0.5)
        );
        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::MissRate).unwrap(),
            crate::Value::Double(0.5)
        );

        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_query_cache_metric_evictions_and_expirations() {
        let cache_id = "test_query_evict_expire";
        let cache = get_or_create_cache(cache_id, 10_000, 0, None, true);

        // Simulate evictions and expirations via the metrics counters
        if let Some(stats) = cache.core().stats() {
            stats.record_eviction();
            stats.record_eviction();
            stats.record_expiration();
        }

        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::Evictions).unwrap(),
            crate::Value::Int(2)
        );
        assert_eq!(
            query_cache_metric(cache_id, CacheMetricType::Expirations).unwrap(),
            crate::Value::Int(1)
        );

        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_query_cache_metric_after_drop() {
        let cache_id = "test_query_after_drop";
        let cache = get_or_create_cache(cache_id, 1024, 0, None, true);
        drop(cache);

        // Weak reference should be dead now
        let result = query_cache_metric(cache_id, CacheMetricType::EntryCount);
        assert!(result.is_err());

        cleanup_cache(cache_id);
    }
}
