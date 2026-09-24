// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
use tracing::{debug, warn};

use crate::{
    cluster_routing::{Routable, RoutingInfo},
    cmd::{cacheable_cmd_type, Arg},
    Cmd, ErrorKind, RedisError, RedisResult, Value,
};
use std::{
    collections::{HashMap, HashSet},
    fmt::Debug,
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc, Mutex, RwLock,
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

    /// Fills awaiting a server response. They are scoped to the requested key
    /// so an invalidation only cancels fills for that key.
    active_fills: Arc<CacheFillRegistry>,

    /// Serializes server-assisted invalidations with default cache fills.
    ///
    /// `GlideCacheImpl` additionally uses its store lock for this purpose, but
    /// external `GlideCache` implementations need a synchronization point that
    /// is shared with the connection's invalidation handler.
    fill_barrier: Mutex<()>,

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
            active_fills: Arc::new(CacheFillRegistry::default()),
            fill_barrier: Mutex::new(()),
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

    /// Registers a pending cache fill for `key`.
    fn begin_cache_fill(&self, key: &[u8]) -> CacheFillToken {
        self.active_fills.begin(key)
    }

    /// Cancels fills for a key while the cache store lock is held.
    fn invalidate_cache_fills(&self, key: &[u8]) {
        self.active_fills.invalidate(key);
    }

    /// Cancels all pending fills while the cache store lock is held.
    fn invalidate_all_cache_fills(&self) {
        self.active_fills.invalidate_all();
    }

    fn lock_fill_barrier(&self) -> std::sync::MutexGuard<'_, ()> {
        self.fill_barrier.lock().unwrap()
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

/// Opaque handle for a cache fill that is waiting on a server response.
/// Dropping the token cancels the fill and releases its pending state.
#[derive(Debug)]
pub struct CacheFillToken {
    registry: Arc<CacheFillRegistry>,
    key: Vec<u8>,
    id: u64,
    active: bool,
}

impl CacheFillToken {
    /// Claims the fill if it has not been invalidated, releasing its pending state.
    fn take_if_active(mut self) -> bool {
        if !self.active {
            return false;
        }
        self.active = false;
        self.registry.remove(&self.key, self.id)
    }
}

impl Drop for CacheFillToken {
    fn drop(&mut self) {
        if self.active {
            self.registry.remove(&self.key, self.id);
        }
    }
}

#[derive(Debug, Default)]
struct CacheFillRegistry {
    next_id: AtomicU64,
    fills: Mutex<HashMap<Vec<u8>, HashSet<u64>>>,
}

impl CacheFillRegistry {
    fn begin(self: &Arc<Self>, key: &[u8]) -> CacheFillToken {
        let id = self.next_id.fetch_add(1, Ordering::Relaxed);
        let key = key.to_vec();
        self.fills
            .lock()
            .unwrap()
            .entry(key.clone())
            .or_default()
            .insert(id);
        CacheFillToken {
            registry: self.clone(),
            key,
            id,
            active: true,
        }
    }

    fn remove(&self, key: &[u8], id: u64) -> bool {
        let mut fills = self.fills.lock().unwrap();
        let Some(ids) = fills.get_mut(key) else {
            return false;
        };
        let active = ids.remove(&id);
        if ids.is_empty() {
            fills.remove(key);
        }
        active
    }

    fn invalidate(&self, key: &[u8]) {
        self.fills.lock().unwrap().remove(key);
    }

    fn invalidate_all(&self) {
        self.fills.lock().unwrap().clear();
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
    pub(crate) store: RwLock<S>,
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
            store: RwLock::new(strategy),
            core: CacheCore::new(config),
        })
    }

    /// Remove an expired entry if present, updating memory and stats.
    /// Caller must hold a write lock on the store.
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

    /// Inserts an already-sized, detached value while holding the store write lock.
    fn insert_with_lock(
        &self,
        store: &mut S,
        key: Vec<u8>,
        key_type: CachedKeyType,
        value: Value,
        entry_size: u64,
    ) {
        if let Some(existing) = store.remove(&key) {
            self.core.uncharge(existing.size);
        }

        self.evict_until_space_available(store, entry_size);

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

    /// Inserts a value only when its pending fill has not been invalidated.
    fn insert_if_active_fill(
        &self,
        key: Vec<u8>,
        key_type: CachedKeyType,
        value: Value,
        fill: CacheFillToken,
    ) {
        // Keep the claim and insertion atomic with server-assisted invalidations
        // for custom cache implementations. GlideCacheImpl uses its store lock
        // instead, which also serializes its override with invalidations.
        let _fill_barrier = self.core().lock_fill_barrier();
        if fill.take_if_active() {
            self.insert(key, key_type, value);
        }
    }

    /// Invalidates a key from the cache
    ///
    /// # Arguments
    /// * `key` - The key to invalidate
    fn invalidate(&self, key: &[u8]);

    /// Applies a server-assisted invalidation without allowing an in-flight
    /// default cache fill to reinsert a stale value.
    fn invalidate_server_assisted(&self, key: &[u8]) {
        let _fill_barrier = self.core().lock_fill_barrier();
        self.core().invalidate_cache_fills(key);
        self.invalidate(key);
    }

    /// Removes all entries from the cache
    fn flush_all(&self);

    /// Applies a server-assisted flush without allowing an in-flight default
    /// cache fill to reinsert a stale value.
    fn flush_all_server_assisted(&self) {
        let _fill_barrier = self.core().lock_fill_barrier();
        self.core().invalidate_all_cache_fills();
        self.flush_all();
    }

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

    /// Retrieves a cached command result and starts a fill only for a cache miss.
    fn get_cached_cmd_with_fill(&self, cmd: &Cmd) -> (Option<Value>, Option<CacheFillToken>) {
        let Some(cmd_name) = cmd.command() else {
            return (None, None);
        };
        let Some(key_type) = cacheable_cmd_type(cmd_name.as_ref()) else {
            return (None, None);
        };
        let Some(cmd_key) = RoutingInfo::key_for_command(cmd) else {
            return (None, None);
        };

        match self.get(cmd_key, key_type) {
            Some(value) => {
                self.increment_hit();
                (Some(value), None)
            }
            None => {
                self.increment_miss();
                (None, Some(self.core().begin_cache_fill(cmd_key)))
            }
        }
    }

    /// Completes a pending cache fill for a cacheable command.
    fn complete_cached_cmd(&self, cmd: &Cmd, value: Value, fill: CacheFillToken) {
        let Some(cmd_name) = cmd.command() else {
            return;
        };
        let Some(key_type) = cacheable_cmd_type(cmd_name.as_ref()) else {
            return;
        };
        let Some(cmd_key) = RoutingInfo::key_for_command(cmd) else {
            return;
        };
        self.insert_if_active_fill(cmd_key.to_vec(), key_type, value, fill);
    }
}

/// Internal MGET cache operations used by the native connection path.
pub(crate) trait MGetCacheExt: GlideCache {
    /// Looks up the individual keys of an MGET command in the cache.
    ///
    /// Returns `None` when `cmd` is not a valid MGET command, leaving the
    /// caller to execute it unchanged. Metrics are recorded once per key.
    fn get_cached_mget(&self, cmd: &Cmd) -> Option<MGetCacheLookup> {
        let mut args = cmd.args_iter();
        match args.next()? {
            Arg::Simple(command) if command.eq_ignore_ascii_case(b"MGET") => {}
            _ => return None,
        }

        let keys: Vec<&[u8]> = args
            .map(|arg| match arg {
                Arg::Simple(key) => Some(key),
                Arg::Cursor => None,
            })
            .collect::<Option<_>>()?;
        if keys.is_empty() {
            return None;
        }

        let mut lookup = MGetCacheLookup::with_capacity(keys.len());
        for key in keys {
            match self.get(key, CachedKeyType::String) {
                Some(value) => {
                    self.increment_hit();
                    // Preserve this hit for the current MGET; a later invalidation affects only subsequent requests.
                    lookup.push_hit(value);
                }
                None => {
                    self.increment_miss();
                    lookup.push_miss(key.to_vec(), self.core().begin_cache_fill(key));
                }
            }
        }
        Some(lookup)
    }

    /// Merges an MGET response for cache misses into a previous lookup and
    /// stores the non-nil values returned by the server.
    fn complete_cached_mget(
        &self,
        mut lookup: MGetCacheLookup,
        response: Value,
    ) -> RedisResult<Value> {
        let Value::Array(miss_values) = response else {
            return Err((
                ErrorKind::TypeError,
                "expected array of values as MGET response",
            )
                .into());
        };

        if miss_values.len() != lookup.missing_keys.len() {
            return Err((
                ErrorKind::TypeError,
                "MGET response length does not match requested cache misses",
            )
                .into());
        }

        let missing_keys = std::mem::take(&mut lookup.missing_keys);
        for ((index, key, fill), value) in missing_keys.into_iter().zip(miss_values) {
            if value != Value::Nil {
                self.insert_if_active_fill(key, CachedKeyType::String, value.clone(), fill);
            }
            lookup.values[index] = Some(value);
        }

        lookup.into_value()
    }
}

impl<T: GlideCache + ?Sized> MGetCacheExt for T {}

/// Cached MGET values and the original positions of keys that were not cached.
pub(crate) struct MGetCacheLookup {
    values: Vec<Option<Value>>,
    missing_keys: Vec<(usize, Vec<u8>, CacheFillToken)>,
}

impl MGetCacheLookup {
    fn with_capacity(key_count: usize) -> Self {
        Self {
            values: Vec::with_capacity(key_count),
            missing_keys: Vec::new(),
        }
    }

    fn push_hit(&mut self, value: Value) {
        self.values.push(Some(value));
    }

    fn push_miss(&mut self, key: Vec<u8>, fill: CacheFillToken) {
        self.missing_keys.push((self.values.len(), key, fill));
        self.values.push(None);
    }

    pub(crate) fn is_complete(&self) -> bool {
        self.missing_keys.is_empty()
    }

    pub(crate) fn missing_count(&self) -> usize {
        self.missing_keys.len()
    }

    pub(crate) fn missing_keys(&self) -> impl Iterator<Item = &[u8]> {
        self.missing_keys.iter().map(|(_, key, _)| key.as_slice())
    }

    pub(crate) fn into_value(self) -> RedisResult<Value> {
        self.values
            .into_iter()
            .collect::<Option<Vec<_>>>()
            .map(Value::Array)
            .ok_or_else(|| {
                RedisError::from((ErrorKind::TypeError, "incomplete MGET cache lookup result"))
            })
    }
}

// ==================== GlideCache for GlideCacheImpl ====================

impl<S: EvictionStrategy + 'static> GlideCache for GlideCacheImpl<S> {
    fn core(&self) -> &CacheCore {
        &self.core
    }

    fn get(&self, key: &[u8], expected_type: CachedKeyType) -> Option<Value> {
        // Fast path: read lock for peek + clone (concurrent readers allowed)
        let value = {
            let store = self.store.read().unwrap();

            // Check expiration via peek (read-only)
            let is_expired = store.peek(key).is_some_and(|e| e.is_expired());
            if is_expired {
                // Need write lock to remove expired entry — drop read lock first
                drop(store);
                let mut store = self.store.write().unwrap();
                // Re-check and remove under write lock (another thread may have already removed it)
                self.remove_if_expired(&mut store, key);
                return None;
            }

            // Peek: check type + clone value (read-only, no promotion yet)
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
            // Read lock dropped here
        };

        // Slow path: write lock only for promote (mutates LRU order / LFU frequency)
        {
            let mut store = self.store.write().unwrap();
            store.promote(key);
        }

        Some(value)
    }

    fn insert(&self, key: Vec<u8>, key_type: CachedKeyType, value: Value) {
        // Cached values outlive the request; deep-copy so they don't pin the
        // connection read buffers their BulkStrings were zero-copy sliced from.
        let value = value.detach_buffers();
        let entry_size = calculate_entry_size(&key, &value);

        if self.core.entry_too_big(entry_size) {
            warn!(
                "cache_insert - Entry too large for cache: {}B > {}B (max), skipping",
                entry_size,
                self.core.max_memory()
            );
            return;
        }

        let mut store = self.store.write().unwrap();

        self.insert_with_lock(&mut store, key, key_type, value, entry_size);
    }

    fn insert_if_active_fill(
        &self,
        key: Vec<u8>,
        key_type: CachedKeyType,
        value: Value,
        fill: CacheFillToken,
    ) {
        // Cached values outlive the request; deep-copy so they don't pin the
        // connection read buffers their BulkStrings were zero-copy sliced from.
        let value = value.detach_buffers();
        let entry_size = calculate_entry_size(&key, &value);

        if self.core.entry_too_big(entry_size) {
            warn!(
                "cache_insert - Entry too large for cache: {}B > {}B (max), skipping",
                entry_size,
                self.core.max_memory()
            );
            return;
        }

        let mut store = self.store.write().unwrap();
        if !fill.take_if_active() {
            return;
        }
        self.insert_with_lock(&mut store, key, key_type, value, entry_size);
    }

    fn invalidate(&self, key: &[u8]) {
        let mut store = self.store.write().unwrap();
        self.core.invalidate_cache_fills(key);

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

    fn flush_all(&self) {
        let mut store = self.store.write().unwrap();
        self.core.invalidate_all_cache_fills();
        while let Some(entry) = store.evict_one() {
            self.core.uncharge(entry.size);
            if let Some(stats) = self.core.stats() {
                stats.record_invalidation();
            }
        }
        debug!(
            "cache_flush_all - [{}] Flushed all entries",
            store.policy_name()
        );
    }

    fn entry_count(&self) -> u64 {
        self.store.read().unwrap().len() as u64
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
    use std::{
        collections::HashMap,
        sync::{Barrier, Mutex},
        thread,
    };

    fn test_cache() -> std::sync::Arc<dyn GlideCache> {
        crate::cache::lru_cache::new_lru_cache(CacheConfig {
            max_memory_bytes: 10_000,
            ttl: None,
            enable_metrics: true,
        })
    }

    #[derive(Debug)]
    struct ExternalCache {
        core: CacheCore,
        entries: Mutex<HashMap<Vec<u8>, Value>>,
        insert_started: Barrier,
        allow_insert: Barrier,
    }

    impl ExternalCache {
        fn new() -> Self {
            Self {
                core: CacheCore::new(CacheConfig {
                    max_memory_bytes: 10_000,
                    ttl: None,
                    enable_metrics: false,
                }),
                entries: Mutex::new(HashMap::new()),
                insert_started: Barrier::new(2),
                allow_insert: Barrier::new(2),
            }
        }
    }

    impl GlideCache for ExternalCache {
        fn core(&self) -> &CacheCore {
            &self.core
        }

        fn get(&self, key: &[u8], _expected_type: CachedKeyType) -> Option<Value> {
            self.entries.lock().unwrap().get(key).cloned()
        }

        fn insert(&self, key: Vec<u8>, _key_type: CachedKeyType, value: Value) {
            self.insert_started.wait();
            self.allow_insert.wait();
            self.entries.lock().unwrap().insert(key, value);
        }

        fn invalidate(&self, key: &[u8]) {
            self.entries.lock().unwrap().remove(key);
        }

        fn flush_all(&self) {
            self.entries.lock().unwrap().clear();
        }

        fn entry_count(&self) -> u64 {
            self.entries.lock().unwrap().len() as u64
        }
    }

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
    fn test_mget_lookup_merges_misses_and_caches_non_nil_values() {
        let cache = test_cache();
        cache.insert(
            b"cached".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"cached value".to_vec().into()),
        );

        let mut cmd = Cmd::new();
        cmd.arg("MGET").arg("cached").arg("missing").arg("cached");
        let lookup = cache.get_cached_mget(&cmd).unwrap();
        assert_eq!(lookup.missing_count(), 1);

        let result = cache
            .complete_cached_mget(
                lookup,
                Value::Array(vec![Value::BulkString(b"missing value".to_vec().into())]),
            )
            .unwrap();
        assert_eq!(
            result,
            Value::Array(vec![
                Value::BulkString(b"cached value".to_vec().into()),
                Value::BulkString(b"missing value".to_vec().into()),
                Value::BulkString(b"cached value".to_vec().into()),
            ])
        );
        assert_eq!(
            cache.get(b"missing", CachedKeyType::String),
            Some(Value::BulkString(b"missing value".to_vec().into()))
        );

        let metrics = cache.metrics().unwrap();
        assert_eq!(metrics.hits(), 2);
        assert_eq!(metrics.misses(), 1);
    }

    #[test]
    fn test_mget_does_not_cache_nil_misses() {
        let cache = test_cache();
        let mut cmd = Cmd::new();
        cmd.arg("MGET").arg("missing");

        let lookup = cache.get_cached_mget(&cmd).unwrap();
        let result = cache
            .complete_cached_mget(lookup, Value::Array(vec![Value::Nil]))
            .unwrap();
        assert_eq!(result, Value::Array(vec![Value::Nil]));

        let second_lookup = cache.get_cached_mget(&cmd).unwrap();
        assert_eq!(second_lookup.missing_count(), 1);
        assert_eq!(cache.entry_count(), 0);
        assert_eq!(cache.metrics().unwrap().misses(), 2);
    }

    #[test]
    fn test_mget_invalidation_cancels_only_the_matching_fill() {
        let cache = test_cache();
        let mut cmd = Cmd::new();
        cmd.arg("MGET").arg("first").arg("second");

        let lookup = cache.get_cached_mget(&cmd).unwrap();
        cache.invalidate(b"first");
        cache
            .complete_cached_mget(
                lookup,
                Value::Array(vec![
                    Value::BulkString(b"stale first".to_vec().into()),
                    Value::BulkString(b"current second".to_vec().into()),
                ]),
            )
            .unwrap();

        assert_eq!(cache.get(b"first", CachedKeyType::String), None);
        assert_eq!(
            cache.get(b"second", CachedKeyType::String),
            Some(Value::BulkString(b"current second".to_vec().into()))
        );
    }

    #[test]
    fn test_server_assisted_invalidation_blocks_default_fill_reinsertion() {
        let cache = std::sync::Arc::new(ExternalCache::new());
        let mut cmd = Cmd::new();
        cmd.arg("MGET").arg("key");
        let lookup = cache.get_cached_mget(&cmd).unwrap();

        let fill_cache = cache.clone();
        let fill = thread::spawn(move || {
            fill_cache
                .complete_cached_mget(
                    lookup,
                    Value::Array(vec![Value::BulkString(b"stale".to_vec().into())]),
                )
                .unwrap();
        });

        cache.insert_started.wait();
        let invalidation_cache = cache.clone();
        let invalidation = thread::spawn(move || {
            invalidation_cache.invalidate_server_assisted(b"key");
        });
        cache.allow_insert.wait();

        fill.join().unwrap();
        invalidation.join().unwrap();

        assert_eq!(cache.get(b"key", CachedKeyType::String), None);
    }

    #[test]
    fn test_server_assisted_flush_blocks_default_fill_reinsertion() {
        let cache = std::sync::Arc::new(ExternalCache::new());
        let mut cmd = Cmd::new();
        cmd.arg("MGET").arg("key");
        let lookup = cache.get_cached_mget(&cmd).unwrap();

        let fill_cache = cache.clone();
        let fill = thread::spawn(move || {
            fill_cache
                .complete_cached_mget(
                    lookup,
                    Value::Array(vec![Value::BulkString(b"stale".to_vec().into())]),
                )
                .unwrap();
        });

        cache.insert_started.wait();
        let flush_cache = cache.clone();
        let flush = thread::spawn(move || {
            flush_cache.flush_all_server_assisted();
        });
        cache.allow_insert.wait();

        fill.join().unwrap();
        flush.join().unwrap();

        assert_eq!(cache.get(b"key", CachedKeyType::String), None);
    }

    #[test]
    fn test_dropped_cache_fill_releases_pending_state() {
        let cache = test_cache();
        let fill = cache.core().begin_cache_fill(b"key");
        assert_eq!(cache.core().active_fills.fills.lock().unwrap().len(), 1);

        drop(fill);

        assert!(cache.core().active_fills.fills.lock().unwrap().is_empty());
    }

    #[test]
    fn test_flush_all_cancels_pending_cache_fills() {
        let cache = test_cache();
        let fill = cache.core().begin_cache_fill(b"key");

        cache.flush_all();
        cache.insert_if_active_fill(
            b"key".to_vec(),
            CachedKeyType::String,
            Value::BulkString(b"stale".to_vec().into()),
            fill,
        );

        assert_eq!(cache.entry_count(), 0);
    }

    #[test]
    fn test_mget_rejects_response_length_mismatch() {
        let cache = test_cache();
        let mut cmd = Cmd::new();
        cmd.arg("MGET").arg("first").arg("second");
        let lookup = cache.get_cached_mget(&cmd).unwrap();

        let error = cache
            .complete_cached_mget(
                lookup,
                Value::Array(vec![Value::BulkString(b"first value".to_vec().into())]),
            )
            .unwrap_err();
        assert!(error
            .to_string()
            .contains("MGET response length does not match requested cache misses"));
        assert_eq!(cache.entry_count(), 0);
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
            Value::BulkString(b"test".to_vec().into()),
            CachedKeyType::String,
            None,
            100,
        );
        assert!(!entry.is_expired());
    }
    #[test]
    fn test_cache_entry_not_expired_with_future_ttl() {
        let entry = CacheEntry::new(
            Value::BulkString(b"test".to_vec().into()),
            CachedKeyType::String,
            Some(Instant::now() + Duration::from_secs(60)),
            100,
        );
        assert!(!entry.is_expired());
    }

    #[test]
    fn test_cache_entry_expired() {
        let entry = CacheEntry::new(
            Value::BulkString(b"test".to_vec().into()),
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
        let value = Value::BulkString(data.into());
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
