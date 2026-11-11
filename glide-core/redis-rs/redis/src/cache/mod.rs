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
/// If the cache already exists, returns the existing one. Tuning parameters of the new config
/// (size, TTL, eviction policy, metrics) are ignored in that case, but the tracking prefix
/// must match: a shared cache fed by connections tracking different prefixes would hold keys
/// that some of those connections never invalidate.
/// If it doesn't exist, creates a new one with the specified configuration.
///
/// # Arguments
/// * `cache_id` - Unique identifier for the cache
/// * `max_cache_kb` - Maximum cache size in kilobytes
/// * `ttl_ms` - Time-to-live in milliseconds (0 = no expiration)
/// * `eviction_policy` - Eviction policy (LRU or LFU, defaults to LRU)
/// * `enable_metrics` - Whether to enable metrics tracking, such as hit/miss counts.
/// * `tracking_prefix` - Key prefix the cache is restricted to (`None` = all keys). Must equal
///   the prefix of an already registered cache with the same `cache_id`.
///
/// # Errors
/// Returns `ErrorKind::InvalidClientConfig` when a live cache with the same `cache_id` was
/// created with a different `tracking_prefix`.
pub fn get_or_create_cache(
    cache_id: &str,
    max_cache_kb: u64,
    ttl_ms: u64,
    eviction_policy: Option<EvictionPolicy>,
    enable_metrics: bool,
    tracking_prefix: Option<Vec<u8>>,
) -> RedisResult<Arc<dyn GlideCache>> {
    // Fast path: try to get existing cache with read lock
    if let Some(cache) = CACHE_REGISTRY
        .read()
        .unwrap()
        .get(cache_id)
        .and_then(Weak::upgrade)
    {
        check_tracking_prefix(cache_id, cache.as_ref(), tracking_prefix.as_deref())?;
        debug!(
            "cache_lifetime - Cache `{cache_id}` already exists — returning existing instance. \
             New config parameters (max_cache_kb={max_cache_kb}, ttl_ms={ttl_ms}, \
             eviction_policy={eviction_policy:?}, enable_metrics={enable_metrics}) are ignored. \
             Drop all references to recreate with different config."
        );
        return Ok(cache);
    }

    // Slow path: acquire write lock and double-check
    let mut registry = CACHE_REGISTRY.write().unwrap();

    // Double-check: another thread may have created the cache while we waited
    if let Some(cache) = registry.get(cache_id).and_then(Weak::upgrade) {
        check_tracking_prefix(cache_id, cache.as_ref(), tracking_prefix.as_deref())?;
        debug!(
            "cache_lifetime - Cache `{cache_id}` already exists (after write lock) — returning existing instance. \
             New config parameters are ignored."
        );
        return Ok(cache);
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
        tracking_prefix,
    };

    // Create cache based on eviction policy
    let policy = eviction_policy.unwrap_or_default();
    let cache: Arc<dyn GlideCache> = match policy {
        EvictionPolicy::Lru => lru_cache::new_lru_cache(config),
        EvictionPolicy::Lfu => lfu_cache::new_lfu_cache(config),
    };

    info!(
        "cache_creation - Creating {policy:?} cache `{cache_id}` (max={}KB, ttl={}ms, tracking_prefix_set={})",
        max_cache_kb,
        ttl_ms,
        // Only whether a prefix is set: its value can reveal key naming.
        cache.core().tracking_prefix().is_some()
    );

    // Store weak reference in registry
    registry.insert(cache_id.to_string(), Arc::downgrade(&cache));
    drop(registry); // Release write lock

    // Start housekeeping task if this is the first cache
    start_cache_housekeeping();

    Ok(cache)
}

/// Rejects attaching to an existing cache whose tracking prefix differs from the requested one.
///
/// Only the prefix is compared: unlike size or TTL, a prefix mismatch is a correctness problem.
/// The connection registers `CLIENT TRACKING ... PREFIX <requested>` on the server while the
/// cache filters keys by `<existing>`, so keys could be cached without ever being invalidated.
fn check_tracking_prefix(
    cache_id: &str,
    existing: &dyn GlideCache,
    requested: Option<&[u8]>,
) -> RedisResult<()> {
    let existing_prefix = existing.core().tracking_prefix();
    if existing_prefix == requested {
        return Ok(());
    }
    Err(RedisError::from((
        ErrorKind::InvalidClientConfig,
        "Client-side cache is already in use with a different tracking prefix",
        format!(
            "cache_id={cache_id}, existing={:?}, requested={:?}",
            existing_prefix.map(String::from_utf8_lossy),
            requested.map(String::from_utf8_lossy)
        ),
    )))
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
        let cache = get_or_create_cache(
            "test_lru_cache",
            1024,
            0,
            Some(EvictionPolicy::Lru),
            false,
            None,
        )
        .unwrap();
        assert_eq!(cache.entry_count(), 0);
        cleanup_cache("test_lru_cache");
    }

    #[tokio::test]
    async fn test_create_lfu_cache() {
        let cache = get_or_create_cache(
            "test_lfu_cache",
            1024,
            0,
            Some(EvictionPolicy::Lfu),
            false,
            None,
        )
        .unwrap();
        assert_eq!(cache.entry_count(), 0);
        cleanup_cache("test_lfu_cache");
    }

    #[tokio::test]
    async fn test_create_cache_with_metrics() {
        let cache = get_or_create_cache("test_metrics_cache", 1024, 0, None, true, None).unwrap();
        assert!(cache.metrics().is_ok());
        cleanup_cache("test_metrics_cache");
    }

    #[tokio::test]
    async fn test_create_cache_without_metrics() {
        let cache =
            get_or_create_cache("test_no_metrics_cache", 1024, 0, None, false, None).unwrap();
        assert!(cache.metrics().is_err());
        cleanup_cache("test_no_metrics_cache");
    }

    #[tokio::test]
    async fn test_get_existing_cache() {
        let cache_id = "test_get_existing";
        let cache1 = get_or_create_cache(cache_id, 1024, 0, None, false, None).unwrap();
        let cache2 =
            get_or_create_cache(cache_id, 2048, 30000, Some(EvictionPolicy::Lfu), true, None)
                .unwrap();

        assert!(Arc::ptr_eq(&cache1, &cache2));
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_different_cache_ids_create_different_caches() {
        let cache1 = get_or_create_cache("test_diff_1", 1024, 0, None, false, None).unwrap();
        let cache2 = get_or_create_cache("test_diff_2", 1024, 0, None, false, None).unwrap();

        assert!(!Arc::ptr_eq(&cache1, &cache2));
        cleanup_cache("test_diff_1");
        cleanup_cache("test_diff_2");
    }

    // ==================== Cache Registry ====================

    #[tokio::test]
    async fn test_cache_registered_after_creation() {
        let cache_id = "test_registered";
        let exists_before = CACHE_REGISTRY.read().unwrap().contains_key(cache_id);

        let _cache = get_or_create_cache(cache_id, 1024, 0, None, false, None).unwrap();

        let exists_after = CACHE_REGISTRY.read().unwrap().contains_key(cache_id);

        assert!(!exists_before);
        assert!(exists_after);
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_weak_reference_upgrades_while_cache_alive() {
        let cache_id = "test_weak_upgrade";
        let cache = get_or_create_cache(cache_id, 1024, 0, None, false, None).unwrap();

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

        let cache1 = get_or_create_cache(cache_id, 1024, 0, None, false, None).unwrap();
        assert!(cache1.metrics().is_err());
        drop(cache1);

        let cache2 = get_or_create_cache(cache_id, 1024, 0, None, true, None).unwrap();
        assert!(cache2.metrics().is_ok());
        cleanup_cache(cache_id);
    }

    // ==================== Cache Operations Through Registry ====================

    #[tokio::test]
    async fn test_cache_operations_work() {
        use crate::Value;
        use glide_cache::CachedKeyType;

        let cache = get_or_create_cache("test_operations", 10_000, 0, None, false, None).unwrap();

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
            None,
        )
        .unwrap();
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
            None,
        )
        .unwrap();
        run_concurrent_cache_test(cache);
        cleanup_cache("test_concurrent_lfu");
    }

    // ==================== Tracking prefix ====================

    fn create(cache_id: &str, prefix: Option<&[u8]>) -> RedisResult<Arc<dyn GlideCache>> {
        get_or_create_cache(cache_id, 1024, 0, None, false, prefix.map(<[u8]>::to_vec))
    }

    #[tokio::test]
    async fn test_create_cache_stores_tracking_prefix() {
        let cache_id = "test_prefix_stored";
        let cache = create(cache_id, Some(b"xxx:")).unwrap();
        assert_eq!(cache.core().tracking_prefix(), Some(&b"xxx:"[..]));

        let unprefixed = create("test_prefix_stored_none", None).unwrap();
        assert_eq!(unprefixed.core().tracking_prefix(), None);
        cleanup_cache(cache_id);
        cleanup_cache("test_prefix_stored_none");
    }

    #[tokio::test]
    async fn test_same_cache_id_with_same_prefix_returns_same_instance() {
        let cache_id = "test_prefix_same";
        let first = create(cache_id, Some(b"xxx:")).unwrap();
        let second = create(cache_id, Some(b"xxx:")).unwrap();
        assert!(Arc::ptr_eq(&first, &second));
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_same_cache_id_with_different_prefix_is_rejected() {
        let cache_id = "test_prefix_mismatch";
        let existing = create(cache_id, Some(b"xxx:")).unwrap();

        let err = create(cache_id, Some(b"yyy:")).unwrap_err();
        assert_eq!(err.kind(), ErrorKind::InvalidClientConfig);
        let message = err.to_string();
        assert!(
            message.contains(cache_id),
            "error names the cache id: {message}"
        );
        assert!(
            message.contains("xxx:"),
            "error shows the existing prefix: {message}"
        );
        assert!(
            message.contains("yyy:"),
            "error shows the requested prefix: {message}"
        );

        // The registered cache is untouched.
        assert_eq!(existing.core().tracking_prefix(), Some(&b"xxx:"[..]));
        let again = create(cache_id, Some(b"xxx:")).unwrap();
        assert!(Arc::ptr_eq(&existing, &again));
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_prefixed_and_unprefixed_cache_cannot_share_an_id() {
        let cache_id = "test_prefix_vs_none";
        let _prefixed = create(cache_id, Some(b"xxx:")).unwrap();
        assert_eq!(
            create(cache_id, None).unwrap_err().kind(),
            ErrorKind::InvalidClientConfig,
            "None means all keys, which is a different filter"
        );
        cleanup_cache(cache_id);

        let cache_id = "test_none_vs_prefix";
        let _unprefixed = create(cache_id, None).unwrap();
        assert_eq!(
            create(cache_id, Some(b"xxx:")).unwrap_err().kind(),
            ErrorKind::InvalidClientConfig
        );
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_cache_recreated_with_new_prefix_after_all_references_dropped() {
        let cache_id = "test_prefix_recreate";
        let first = create(cache_id, Some(b"xxx:")).unwrap();
        drop(first);

        // The weak registry entry is dead, so a different prefix is simply a new cache.
        let second = create(cache_id, Some(b"yyy:")).unwrap();
        assert_eq!(second.core().tracking_prefix(), Some(&b"yyy:"[..]));
        cleanup_cache(cache_id);
    }

    #[tokio::test]
    async fn test_concurrent_creation_with_different_prefixes_lets_exactly_one_win() {
        use std::thread;

        let cache_id = "test_prefix_concurrent";
        // The winning thread starts the housekeeping task with `tokio::spawn`, which needs
        // the runtime context that plain threads do not inherit.
        let runtime = tokio::runtime::Handle::current();
        let handles: Vec<_> = (0..8)
            .map(|i| {
                let runtime = runtime.clone();
                thread::spawn(move || {
                    let _runtime_guard = runtime.enter();
                    let prefix = format!("p{i}:");
                    create(cache_id, Some(prefix.as_bytes()))
                })
            })
            .collect();

        // Keep every successful Arc alive until all threads are done so the registry entry
        // cannot die (and be recreated) in the middle of the race.
        let results: Vec<_> = handles.into_iter().map(|h| h.join().unwrap()).collect();
        let winners: Vec<_> = results.iter().filter(|r| r.is_ok()).collect();
        assert_eq!(winners.len(), 1, "exactly one prefix can own a cache id");
        assert!(results
            .iter()
            .filter_map(|r| r.as_ref().err())
            .all(|e| e.kind() == ErrorKind::InvalidClientConfig));
        cleanup_cache(cache_id);
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
        let cache = get_or_create_cache(cache_id, 10_000, 0, None, false, None).unwrap();

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
        let _cache = get_or_create_cache(cache_id, 1024, 0, None, false, None).unwrap();

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
        let cache = get_or_create_cache(cache_id, 10_000, 0, None, true, None).unwrap();

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
        let cache = get_or_create_cache(cache_id, 10_000, 0, None, true, None).unwrap();

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
        let cache = get_or_create_cache(cache_id, 1024, 0, None, true, None).unwrap();
        drop(cache);

        // Weak reference should be dead now
        let result = query_cache_metric(cache_id, CacheMetricType::EntryCount);
        assert!(result.is_err());

        cleanup_cache(cache_id);
    }
}
