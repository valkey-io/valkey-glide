// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use tracing::{debug, warn};

use crate::{
    cluster_routing::{Routable, RoutingInfo},
    cmd::cacheable_cmd_type,
    Cmd, ErrorKind, RedisError, RedisResult, Value,
};
use std::{
    fmt::Debug,
    sync::{
        atomic::{AtomicU64, Ordering},
        Mutex,
    },
    time::{Duration, Instant},
};

// ==================== Configuration ====================

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

// ==================== Metrics ====================

/// Metrics about cache performance
#[derive(Debug, Default)]
pub struct CacheMetrics {
    /// Total number of successful get operations
    hits: AtomicU64,

    /// Total number of failed get operations (key not found or expired)
    misses: AtomicU64,

    /// Total number of expired entries removed
    expirations: AtomicU64,

    /// Total number of entries invalidated
    invalidations: AtomicU64,

    /// Total number of entries evicted due to memory constraints
    evictions: AtomicU64,
}

impl CacheMetrics {
    // ==================== Getters ====================

    /// Returns the total number of cache hits
    pub fn hits(&self) -> u64 {
        self.hits.load(Ordering::Relaxed)
    }

    /// Returns the total number of cache misses
    pub fn misses(&self) -> u64 {
        self.misses.load(Ordering::Relaxed)
    }

    /// Returns the total number of expirations
    pub fn expirations(&self) -> u64 {
        self.expirations.load(Ordering::Relaxed)
    }

    /// Returns the total number of invalidations
    pub fn invalidations(&self) -> u64 {
        self.invalidations.load(Ordering::Relaxed)
    }

    /// Returns the total number of evictions
    pub fn evictions(&self) -> u64 {
        self.evictions.load(Ordering::Relaxed)
    }

    // ==================== Recording ====================

    pub(crate) fn record_hit(&self) {
        self.hits.fetch_add(1, Ordering::Relaxed);
    }
    pub(crate) fn record_miss(&self) {
        self.misses.fetch_add(1, Ordering::Relaxed);
    }
    pub(crate) fn record_eviction(&self) {
        self.evictions.fetch_add(1, Ordering::Relaxed);
    }
    pub(crate) fn record_expiration(&self) {
        self.expirations.fetch_add(1, Ordering::Relaxed);
    }
    pub(crate) fn record_invalidation(&self) {
        self.invalidations.fetch_add(1, Ordering::Relaxed);
    }

    // ==================== Aggregates ====================

    /// Returns the total number of cache lookups (hits + misses)
    pub fn total_lookups(&self) -> u64 {
        self.hits() + self.misses()
    }

    /// Calculate the hit rate (hits / total lookups)
    pub fn hit_rate(&self) -> f64 {
        let total = self.total_lookups();
        if total == 0 {
            0.0
        } else {
            self.hits() as f64 / total as f64
        }
    }

    /// Calculate the miss rate (misses / total lookups)
    pub fn miss_rate(&self) -> f64 {
        let total = self.total_lookups();
        if total == 0 {
            0.0
        } else {
            self.misses() as f64 / total as f64
        }
    }
}

impl Clone for CacheMetrics {
    fn clone(&self) -> Self {
        Self {
            hits: AtomicU64::new(self.hits()),
            misses: AtomicU64::new(self.misses()),
            expirations: AtomicU64::new(self.expirations()),
            invalidations: AtomicU64::new(self.invalidations()),
            evictions: AtomicU64::new(self.evictions()),
        }
    }
}

// ==================== Cache Entry ====================

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

/// Cache entry containing the cached value and metadata
#[derive(Debug)]
pub struct CacheEntry {
    /// The cached Valkey value
    pub value: Value,

    /// Type of the key (String, Hash, etc.)
    pub key_type: CachedKeyType,

    /// Expiration time for this entry (None = no expiration)
    pub expires_at: Option<Instant>,

    /// Size of this entry in bytes
    pub size: u64,
}

impl CacheEntry {
    /// Creates a new cache entry
    pub fn new(
        value: Value,
        key_type: CachedKeyType,
        expires_at: Option<Instant>,
        size: u64,
    ) -> Self {
        Self {
            value,
            key_type,
            expires_at,
            size,
        }
    }

    /// Check if this entry is expired
    #[inline]
    pub fn is_expired(&self) -> bool {
        self.expires_at.is_some_and(|exp| Instant::now() >= exp)
    }
}

// ==================== Cache Core ====================

/// Shared cache core that handles configuration, memory tracking, and metrics
#[derive(Debug)]
pub struct CacheCore {
    /// Cache configuration
    config: CacheConfig,

    /// Current memory usage in bytes
    current_memory: AtomicU64,

    /// Performance statistics (None if metrics disabled)
    stats: Option<CacheMetrics>,
}

impl CacheCore {
    /// Creates a new cache core with the given configuration
    pub fn new(config: CacheConfig) -> Self {
        let stats = config.enable_metrics.then(CacheMetrics::default);

        Self {
            config,
            current_memory: AtomicU64::new(0),
            stats,
        }
    }

    // ==================== Config Access ====================

    /// Returns the cache configuration
    pub fn config(&self) -> &CacheConfig {
        &self.config
    }

    /// Returns the maximum memory in bytes
    pub fn max_memory(&self) -> u64 {
        self.config.max_memory_bytes
    }

    /// Computes the expiration time based on TTL config
    pub fn compute_expires_at(&self) -> Option<Instant> {
        self.config.ttl.map(|ttl| Instant::now() + ttl)
    }

    // ==================== Memory Management ====================

    /// Checks if an entry is too large for the cache
    pub fn entry_too_big(&self, size: u64) -> bool {
        size > self.config.max_memory_bytes
    }

    /// Checks if eviction is needed to fit the required space
    pub fn needs_eviction(&self, required_space: u64) -> bool {
        let current = self.current_memory.load(Ordering::Relaxed);
        required_space > self.config.max_memory_bytes.saturating_sub(current)
    }

    /// Returns the current memory usage in bytes
    pub fn current_memory(&self) -> u64 {
        self.current_memory.load(Ordering::Relaxed)
    }

    /// Adds bytes to memory tracking
    pub fn charge(&self, bytes: u64) {
        self.current_memory.fetch_add(bytes, Ordering::Relaxed);
    }

    /// Subtracts bytes from memory tracking (saturating)
    pub fn uncharge(&self, bytes: u64) {
        let _ = self
            .current_memory
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |current| {
                Some(current.saturating_sub(bytes))
            });
    }

    // ==================== Metrics ====================

    /// Returns a reference to the metrics if enabled
    pub fn stats(&self) -> Option<&CacheMetrics> {
        self.stats.as_ref()
    }

    /// Returns a clone of the current cache metrics
    pub fn metrics(&self) -> RedisResult<CacheMetrics> {
        self.stats.clone().ok_or_else(|| {
            RedisError::from((
                ErrorKind::InvalidClientConfig,
                "Cache metrics tracking is not enabled",
            ))
        })
    }
}

// ==================== Eviction Strategy ====================

/// Pluggable eviction strategy — only the data structure operations.
///
/// Implementors only need to handle storage and ordering.
/// All shared logic (TTL, memory, metrics) is handled by `GlideCacheImpl`.
pub trait EvictionStrategy: Send + Sync + Debug {
    /// Returns a display name for logging (e.g., "LRU", "LFU").
    fn policy_name(&self) -> &'static str;

    // Promote an entry according to the eviction policy.
    /// (LRU: moves to front, LFU: increments frequency)
    /// Called after peek() confirms the entry is valid.
    fn promote(&mut self, key: &[u8]);

    /// Look up an entry **without** promoting it.
    /// Used for expiration checks to avoid polluting eviction ordering.
    fn peek(&self, key: &[u8]) -> Option<&CacheEntry>;

    /// Insert a new entry. Caller guarantees the key does not already exist.
    fn insert(&mut self, key: Vec<u8>, entry: CacheEntry);

    /// Remove a specific key, returning its entry if present.
    fn remove(&mut self, key: &[u8]) -> Option<CacheEntry>;

    /// Evict one entry according to the policy. Returns the evicted entry.
    fn evict_one(&mut self) -> Option<CacheEntry>;

    /// Current number of entries.
    fn len(&self) -> usize;

    /// Returns true if the cache is empty.
    fn is_empty(&self) -> bool {
        self.len() == 0
    }
}

// ==================== GlideCacheImpl ====================
/// Generic cache implementation parameterized by eviction strategy.
///
/// All shared logic lives here:
/// - TTL expiration (lazy, on access and during eviction)
/// - Memory accounting (charge/uncharge)
/// - Metrics (hits, misses, evictions, expirations, invalidations)
/// - Entry-too-big rejection
/// - Evict-until-space-available loop
#[derive(Debug)]
pub struct GlideCacheImpl<S: EvictionStrategy> {
    pub(crate) store: Mutex<S>,
    core: CacheCore,
}
impl<S: EvictionStrategy> GlideCacheImpl<S> {
    /// Creates a new GlideCacheImpl with the given eviction strategy and configuration
    pub fn new(strategy: S, config: CacheConfig) -> std::sync::Arc<Self> {
        debug!(
            "cache - Creating {} cache with max_memory={}KB,{} metrics_enabled={}",
            strategy.policy_name(),
            config.max_memory_bytes / 1024,
            config
                .ttl
                .map_or(String::new(), |ttl| format!(" ttl={:?},", ttl)),
            config.enable_metrics
        );
        std::sync::Arc::new(Self {
            store: Mutex::new(strategy),
            core: CacheCore::new(config),
        })
    }

    /// Remove an expired entry if present, updating memory and stats.
    /// Uses `peek` to avoid affecting eviction ordering.
    fn remove_if_expired(&self, store: &mut S, key: &[u8]) -> bool {
        let is_expired = store.peek(key).is_some_and(|e| e.is_expired());
        if !is_expired {
            return false;
        }

        if let Some(entry) = store.remove(key) {
            self.core.uncharge(entry.size);
            if let Some(stats) = self.core.stats() {
                stats.record_expiration();
            }

            debug!(
                "cache_expiration - [{}] Expired entry (type={:?}, size={}B, remaining_memory={}B)",
                store.policy_name(),
                entry.key_type,
                entry.size,
                self.core.current_memory()
            );
            return true;
        }
        false
    }

    /// Evict entries until we have enough space for `required_space` bytes.
    /// Expired entries encountered during eviction are counted as expirations.
    fn evict_until_space_available(&self, store: &mut S, required_space: u64) {
        while self.core.needs_eviction(required_space) {
            let Some(entry) = store.evict_one() else {
                break;
            };

            self.core.uncharge(entry.size);
            let is_expired = entry.is_expired();
            if let Some(stats) = self.core.stats() {
                if is_expired {
                    stats.record_expiration();
                } else {
                    stats.record_eviction();
                }
            }
            debug!(
                "cache_{} - [{}] {} entry (type={:?}, size={}B, remaining_memory={}B)",
                if is_expired { "expiration" } else { "eviction" },
                store.policy_name(),
                if is_expired { "Expired" } else { "Evicted" },
                entry.key_type,
                entry.size,
                self.core.current_memory()
            );
        }
    }
}

// ==================== GlideCache Trait ====================

/// Core caching interface for Glide
pub trait GlideCache: Send + Sync + Debug {
    // ==================== Core Access ====================
    /// Access the shared cache core
    fn core(&self) -> &CacheCore;

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
    fn metrics(&self) -> RedisResult<CacheMetrics> {
        self.core().metrics()
    }

    /// Increases the hit count for the cache
    fn increment_hit(&self) {
        if let Some(stats) = self.core().stats() {
            stats.record_hit();
        }
    }

    /// Increases the miss count for the cache
    fn increment_miss(&self) {
        if let Some(stats) = self.core().stats() {
            stats.record_miss();
        }
    }

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
        let cmd_key = RoutingInfo::key_for_command(cmd)?;

        let result = self.get(cmd_key, key_type);

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

        if let Some(cmd_key) = RoutingInfo::key_for_command(cmd) {
            self.insert(cmd_key.to_vec(), key_type, value);
        }
    }
}

// ==================== GlideCache for GlideCacheImpl ====================

impl<S: EvictionStrategy + 'static> GlideCache for GlideCacheImpl<S> {
    fn core(&self) -> &CacheCore {
        &self.core
    }

    fn get(&self, key: &[u8], expected_type: CachedKeyType) -> Option<Value> {
        let mut store = self.store.lock().unwrap();

        // Check expiration without promoting
        if self.remove_if_expired(&mut store, key) {
            return None;
        }

        // Peek first (no promotion) — check type + clone value
        let value = {
            let entry = store.peek(key)?;
            if entry.key_type != expected_type {
                debug!(
                    "cache_type_mismatch - [{}] Type mismatch: cached as {:?}, requested as {:?}",
                    store.policy_name(),
                    entry.key_type,
                    expected_type
                );
                return None;
            }
            entry.value.clone()
        };
        // Immutable borrow dropped here

        // Now promote (mutates LRU order / LFU frequency)
        store.promote(key);

        Some(value)
    }

    fn insert(&self, key: Vec<u8>, key_type: CachedKeyType, value: Value) {
        let entry_size = calculate_entry_size(&key, &value);

        if self.core.entry_too_big(entry_size) {
            warn!(
                "cache_insert - Entry too large for cache: {}B > {}B (max), skipping",
                entry_size,
                self.core.max_memory()
            );
            return;
        }

        let mut store = self.store.lock().unwrap();

        // Remove existing entry if present
        if let Some(existing) = store.remove(&key) {
            self.core.uncharge(existing.size);
        }

        // Evict until space available
        self.evict_until_space_available(&mut store, entry_size);

        // Insert new entry
        let expires_at = self.core.compute_expires_at();
        let entry = CacheEntry::new(value, key_type, expires_at, entry_size);

        store.insert(key, entry);
        self.core.charge(entry_size);

        debug!(
            "cache_insert - [{}] Inserted entry (type={:?}, size={}B{})",
            store.policy_name(),
            key_type,
            entry_size,
            if expires_at.is_some() {
                ", with TTL"
            } else {
                ""
            }
        );
    }

    fn invalidate(&self, key: &[u8]) {
        let mut store = self.store.lock().unwrap();

        if let Some(entry) = store.remove(key) {
            self.core.uncharge(entry.size);

            if let Some(stats) = self.core.stats() {
                stats.record_invalidation();
            }

            debug!(
                "cache_invalidate - [{}] Invalidated entry (type={:?}, size={}B, remaining_memory={}B)",
                store.policy_name(),
                entry.key_type,
                entry.size,
                self.core.current_memory()
            );
        }
    }

    fn entry_count(&self) -> u64 {
        self.store.lock().unwrap().len() as u64
    }
}

// ==================== Size Calculation ====================

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
        Value::Nil | Value::Int(_) | Value::Double(_) | Value::Boolean(_) | Value::Okay => 0,

        Value::BulkString(data) => data.len(),
        Value::SimpleString(s) => s.len(),
        Value::VerbatimString { format: _, text } => text.len(),

        Value::BigNumber(big_int) => {
            // BigInt allocates memory based on the number size
            ((big_int.bits() + 7) / 8) as usize // Convert bits to bytes
        }

        Value::Array(arr) => {
            arr.len() * std::mem::size_of::<Value>()
                + arr
                    .iter()
                    .map(calculate_value_additional_data)
                    .sum::<usize>()
        }
        Value::Set(set) => {
            set.len() * std::mem::size_of::<Value>()
                + set
                    .iter()
                    .map(calculate_value_additional_data)
                    .sum::<usize>()
        }
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
        Value::Push { kind: _, data } => {
            data.len() * std::mem::size_of::<Value>()
                + data
                    .iter()
                    .map(calculate_value_additional_data)
                    .sum::<usize>()
        }

        Value::ServerError(err) => err.to_string().len(),
    };

    base_overhead + additional_data
}

/// Helper function that calculates only the additional allocated data
/// (without the base enum overhead)
fn calculate_value_additional_data(value: &Value) -> usize {
    calculate_value_size(value).saturating_sub(std::mem::size_of::<Value>())
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
    fn test_metrics_default() {
        let metrics = CacheMetrics::default();
        assert_eq!(metrics.hits(), 0);
        assert_eq!(metrics.misses(), 0);
        assert_eq!(metrics.expirations(), 0);
        assert_eq!(metrics.invalidations(), 0);
        assert_eq!(metrics.evictions(), 0);
    }
    #[test]
    fn test_metrics_recording() {
        let metrics = CacheMetrics::default();
        metrics.record_hit();
        metrics.record_hit();
        metrics.record_miss();
        metrics.record_eviction();
        metrics.record_expiration();
        metrics.record_expiration();
        metrics.record_invalidation();
        assert_eq!(metrics.hits(), 2);
        assert_eq!(metrics.misses(), 1);
        assert_eq!(metrics.evictions(), 1);
        assert_eq!(metrics.expirations(), 2);
        assert_eq!(metrics.invalidations(), 1);
    }
    #[test]
    fn test_metrics_clone() {
        let metrics = CacheMetrics::default();
        metrics.record_hit();
        metrics.record_hit();
        metrics.record_miss();
        let cloned = metrics.clone();
        assert_eq!(cloned.hits(), 2);
        assert_eq!(cloned.misses(), 1);
        // Original and clone are independent
        metrics.record_hit();
        assert_eq!(metrics.hits(), 3);
        assert_eq!(cloned.hits(), 2);
    }

    #[test]
    fn test_hit_miss_rate_zero_requests() {
        let metrics = CacheMetrics::default();
        assert_eq!(metrics.total_lookups(), 0);
        assert_eq!(metrics.hit_rate(), 0.0);
        assert_eq!(metrics.miss_rate(), 0.0);
    }

    #[test]
    fn test_hit_miss_rate_mixed() {
        let metrics = CacheMetrics {
            hits: 75.into(),
            misses: 25.into(),
            ..Default::default()
        };
        assert_eq!(metrics.total_lookups(), 100);
        assert_eq!(metrics.hit_rate(), 0.75);
        assert_eq!(metrics.miss_rate(), 0.25);
    }

    // ==================== CacheEntry ====================
    #[test]
    fn test_cache_entry_not_expired_no_ttl() {
        let entry = CacheEntry::new(
            Value::BulkString(b"test".to_vec()),
            CachedKeyType::String,
            None,
            100,
        );
        assert!(!entry.is_expired());
    }
    #[test]
    fn test_cache_entry_not_expired_with_future_ttl() {
        let entry = CacheEntry::new(
            Value::BulkString(b"test".to_vec()),
            CachedKeyType::String,
            Some(Instant::now() + Duration::from_secs(60)),
            100,
        );
        assert!(!entry.is_expired());
    }

    #[test]
    fn test_cache_entry_expired() {
        let entry = CacheEntry::new(
            Value::BulkString(b"test".to_vec()),
            CachedKeyType::String,
            Some(Instant::now() - Duration::from_secs(1)),
            100,
        );
        assert!(entry.is_expired());
    }

    // ==================== CacheCore ====================
    #[test]
    fn test_cache_core_new_with_metrics() {
        let config = CacheConfig {
            max_memory_bytes: 1024,
            ttl: Some(Duration::from_secs(60)),
            enable_metrics: true,
        };
        let core = CacheCore::new(config);
        assert_eq!(core.max_memory(), 1024);
        assert!(core.stats.is_some());
    }

    #[test]
    fn test_cache_core_new_without_metrics() {
        let config = CacheConfig {
            max_memory_bytes: 1024,
            ttl: None,
            enable_metrics: false,
        };
        let core = CacheCore::new(config);
        assert!(core.stats.is_none());
        assert!(core.metrics().is_err());
    }

    #[test]
    fn test_cache_core_compute_expires_at() {
        let with_ttl = CacheCore::new(CacheConfig {
            max_memory_bytes: 1024,
            ttl: Some(Duration::from_secs(60)),
            enable_metrics: false,
        });
        assert!(with_ttl.compute_expires_at().is_some());
        let without_ttl = CacheCore::new(CacheConfig {
            max_memory_bytes: 1024,
            ttl: None,
            enable_metrics: false,
        });
        assert!(without_ttl.compute_expires_at().is_none());
    }
    #[test]
    fn test_cache_core_entry_too_big() {
        let core = CacheCore::new(CacheConfig {
            max_memory_bytes: 100,
            ttl: None,
            enable_metrics: false,
        });
        assert!(!core.entry_too_big(50));
        assert!(!core.entry_too_big(100));
        assert!(core.entry_too_big(101));
    }
    #[test]
    fn test_cache_core_memory_tracking() {
        let core = CacheCore::new(CacheConfig {
            max_memory_bytes: 1000,
            ttl: None,
            enable_metrics: false,
        });
        assert_eq!(core.current_memory(), 0);
        core.charge(100);
        assert_eq!(core.current_memory(), 100);
        core.charge(50);
        assert_eq!(core.current_memory(), 150);
        core.uncharge(30);
        assert_eq!(core.current_memory(), 120);
        // Test saturating subtraction
        core.uncharge(1000);
        assert_eq!(core.current_memory(), 0);
    }
    #[test]
    fn test_cache_core_needs_eviction() {
        let core = CacheCore::new(CacheConfig {
            max_memory_bytes: 100,
            ttl: None,
            enable_metrics: false,
        });
        assert!(!core.needs_eviction(50));
        assert!(!core.needs_eviction(100));
        assert!(core.needs_eviction(101));
        core.charge(60);
        assert!(!core.needs_eviction(40));
        assert!(core.needs_eviction(41));
    }
    #[test]
    fn test_cache_core_metrics_recording() {
        let core = CacheCore::new(CacheConfig {
            max_memory_bytes: 1024,
            ttl: None,
            enable_metrics: true,
        });
        let stats = core.stats().unwrap();
        stats.record_hit();
        stats.record_hit();
        stats.record_miss();
        stats.record_eviction();
        stats.record_expiration();
        stats.record_expiration();
        stats.record_invalidation();
        let metrics = core.metrics().unwrap();
        assert_eq!(metrics.hits(), 2);
        assert_eq!(metrics.misses(), 1);
        assert_eq!(metrics.evictions(), 1);
        assert_eq!(metrics.expirations(), 2);
        assert_eq!(metrics.invalidations(), 1);
    }
    #[test]
    fn test_cache_core_metrics_disabled_no_panic() {
        let core = CacheCore::new(CacheConfig {
            max_memory_bytes: 1024,
            ttl: None,
            enable_metrics: false,
        });

        assert!(core.stats().is_none());
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
