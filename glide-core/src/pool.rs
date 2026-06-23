// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Client-Instance Pool (RFC #5815 Feature 1)
//!
//! A shared, cross-language connection pool that manages `GlideClient` instances.
//! Callers borrow a client via `try_acquire`, use it for commands, and return it
//! via `release`. The pool handles LIFO reuse and bounded size; background creation
//! is implemented by the embedding FFI/JNI layer. Idle eviction and health checks
//! are deferred to follow-up work.
//!
//! This module lives in `glide-core` so all language bindings (Java JNI, Python CFFI,
//! Ruby FFI, Go CGO, Node N-API) share the same Rust implementation.
//!
//! # Architecture
//!
//! ```text
//! Language Binding (Java/Python/Ruby/Go/Node)
//!     │
//!     ▼ FFI calls (glide_pool_create, try_acquire, release, destroy)
//! ┌─────────────────────────────────────┐
//! │  glide-core::pool                   │
//! │  ┌─────────────────────────────┐    │
//! │  │ Pool Registry (DashMap)     │    │
//! │  │  pool_id → Arc<Mutex<Pool>> │    │
//! │  └─────────────────────────────┘    │
//! │  ┌─────────────────────────────┐    │
//! │  │ ClientPool                  │    │
//! │  │  idle: VecDeque (LIFO)      │    │
//! │  │  in_use: DashMap            │    │
//! │  │  config: PoolConfig         │    │
//! │  └─────────────────────────────┘    │
//! └─────────────────────────────────────┘
//! ```

use crate::client::Client as GlideClient;
use dashmap::DashMap;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicU32, AtomicU64, AtomicU8, Ordering};
use std::sync::{Arc, OnceLock};
use std::time::{Duration, Instant};
use tokio::sync::Mutex as TokioMutex;

// ═══════════════════════════════════════════════════════════════════════════════
// POOL STATES
// ═══════════════════════════════════════════════════════════════════════════════

pub const POOL_RUNNING: u8 = 0;
pub const POOL_CLOSING: u8 = 1;
pub const POOL_CLOSED: u8 = 2;

// ═══════════════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════════════

/// Configuration for a client-instance pool.
pub struct PoolConfig {
    /// Maximum number of clients. Must be >= 1.
    pub max_size: u32,
    /// Minimum idle clients to pre-warm at creation. Must be <= max_size.
    pub min_idle: u32,
    /// Evict idle clients after this duration.
    pub idle_timeout: Duration,
    /// Request timeout (used for cleanup: 2×).
    pub request_timeout: Duration,
    /// Send PING on borrow to verify connection health. Default: false.
    pub test_on_borrow: bool,
    /// Serialized protobuf ConnectionRequest for background client creation.
    pub connection_request: Vec<u8>,
}

// ═══════════════════════════════════════════════════════════════════════════════
// POOLED CLIENT ENTRY
// ═══════════════════════════════════════════════════════════════════════════════

/// Lifecycle state of a pooled client.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ClientState {
    Idle,
    InUse,
}

/// A client managed by the pool.
pub struct PooledClient {
    /// Unique ID for this client (used as the handle returned to language bindings).
    pub client_id: u64,
    /// The actual Valkey connection.
    pub client: GlideClient,
    /// When this client was created.
    pub created_at: Instant,
    /// When last returned to idle.
    pub last_idle_at: Instant,
    /// When borrowed (for leak detection).
    pub borrowed_at: Option<Instant>,
    /// Current state.
    pub state: ClientState,
}

// ═══════════════════════════════════════════════════════════════════════════════
// CLIENT POOL
// ═══════════════════════════════════════════════════════════════════════════════

/// The client-instance pool. Thread-safe via TokioMutex at the registry level.
pub struct ClientPool {
    pub config: PoolConfig,
    /// LIFO idle stack — most recently returned client is at the back.
    pub idle: VecDeque<PooledClient>,
    /// Currently borrowed clients (client_id → placeholder for tracking).
    pub in_use: DashMap<u64, ()>,
    /// Current total count (idle + in_use + creating).
    pub total_count: AtomicU32,
    /// Pool lifecycle state.
    pub state: AtomicU8,
}

impl ClientPool {
    /// Create a new pool with validated config.
    pub fn new(config: PoolConfig) -> Result<Self, PoolError> {
        if config.max_size < 1 {
            return Err(PoolError::InvalidConfig("max_size must be >= 1".into()));
        }
        if config.min_idle > config.max_size {
            return Err(PoolError::InvalidConfig("min_idle must be <= max_size".into()));
        }
        if config.idle_timeout.is_zero() {
            return Err(PoolError::InvalidConfig("idle_timeout must be > 0".into()));
        }

        Ok(Self {
            config,
            idle: VecDeque::new(),
            in_use: DashMap::new(),
            total_count: AtomicU32::new(0),
            state: AtomicU8::new(POOL_RUNNING),
        })
    }

    /// Generate a globally unique client_id (unique across all pools).
    pub fn next_id(&self) -> u64 {
        NEXT_CLIENT_ID.fetch_add(1, Ordering::Relaxed)
    }

    /// Non-blocking acquire. Returns client_id on success.
    /// Returns -1 if pool is closed/closing, -3 if no idle client available.
    /// Evicts idle connections past idle_timeout before returning.
    /// Evicted client_ids are appended to `evicted` for caller cleanup.
    pub fn try_acquire(&mut self, evicted: &mut Vec<u64>) -> i64 {
        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            return -1;
        }

        while let Some(mut entry) = self.idle.pop_back() {
            let idle_duration = Instant::now().duration_since(entry.last_idle_at);
            if idle_duration > self.config.idle_timeout {
                evicted.push(entry.client_id);
                self.total_count.fetch_sub(1, Ordering::AcqRel);
                continue;
            }
            let client_id = entry.client_id;
            entry.state = ClientState::InUse;
            entry.borrowed_at = Some(Instant::now());
            self.in_use.insert(client_id, ());
            return client_id as i64;
        }

        -3
    }

    /// Whether background creation should be triggered (room below max_size).
    pub fn should_create(&self) -> bool {
        self.state.load(Ordering::Acquire) == POOL_RUNNING
            && self.total_count.load(Ordering::Acquire) < self.config.max_size
    }

    /// Release a client back to the idle pool. Returns false if not found.
    pub fn release(&mut self, client_id: u64, client: PooledClient) -> bool {
        self.in_use.remove(&client_id);
        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            self.total_count.fetch_sub(1, Ordering::AcqRel);
            return true;
        }
        let mut entry = client;
        entry.state = ClientState::Idle;
        entry.last_idle_at = Instant::now();
        entry.borrowed_at = None;
        self.idle.push_back(entry);
        true
    }

    /// Destroy the pool — drop all clients.
    pub fn destroy(&mut self) {
        self.state.store(POOL_CLOSED, Ordering::Release);
        self.idle.clear();
        let keys: Vec<u64> = self.in_use.iter().map(|e| *e.key()).collect();
        for key in keys {
            self.in_use.remove(&key);
        }
        self.total_count.store(0, Ordering::Release);
    }

    /// Get idle count.
    pub fn idle_count(&self) -> u32 {
        self.idle.len() as u32
    }

    /// Get active (in-use) count.
    pub fn active_count(&self) -> u32 {
        self.in_use.len() as u32
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLOBAL REGISTRY
// ═══════════════════════════════════════════════════════════════════════════════

/// Global pool registry: pool_id → Pool instance.
static POOL_REGISTRY: OnceLock<DashMap<u64, Arc<TokioMutex<ClientPool>>>> = OnceLock::new();
static NEXT_POOL_ID: AtomicU64 = AtomicU64::new(1);
/// Global client_id allocator — ensures uniqueness across all pools.
static NEXT_CLIENT_ID: AtomicU64 = AtomicU64::new(1);

pub fn get_pool_registry() -> &'static DashMap<u64, Arc<TokioMutex<ClientPool>>> {
    POOL_REGISTRY.get_or_init(DashMap::new)
}

/// Register a pool. Returns assigned pool_id.
pub fn register_pool(pool: ClientPool) -> u64 {
    let pool_id = NEXT_POOL_ID.fetch_add(1, Ordering::Relaxed);
    get_pool_registry().insert(pool_id, Arc::new(TokioMutex::new(pool)));
    pool_id
}

/// Get a pool by ID (cheap Arc clone).
pub fn get_pool(pool_id: u64) -> Option<Arc<TokioMutex<ClientPool>>> {
    get_pool_registry().get(&pool_id).map(|e| e.value().clone())
}

/// Remove a pool from the registry.
pub fn unregister_pool(pool_id: u64) -> Option<Arc<TokioMutex<ClientPool>>> {
    get_pool_registry().remove(&pool_id).map(|(_, v)| v)
}

// ═══════════════════════════════════════════════════════════════════════════════
// ERRORS
// ═══════════════════════════════════════════════════════════════════════════════

#[derive(Debug)]
pub enum PoolError {
    InvalidConfig(String),
    PoolClosed,
    ClientCreationFailed(String),
}

impl std::fmt::Display for PoolError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PoolError::InvalidConfig(msg) => write!(f, "Invalid pool config: {}", msg),
            PoolError::PoolClosed => write!(f, "Pool is closed"),
            PoolError::ClientCreationFailed(msg) => write!(f, "Client creation failed: {}", msg),
        }
    }
}

impl std::error::Error for PoolError {}


// ═══════════════════════════════════════════════════════════════════════════════
// FEATURE 2: ISOLATED EXECUTION (SCOPE POOL)
//
// Per-client pool of dedicated connections for operations requiring
// per-connection server state (WATCH, CLIENT TRACKING, BLPOP, pub/sub).
// ═══════════════════════════════════════════════════════════════════════════════

/// Tracks per-connection state mutations during a scope borrow.
/// Used for conditional cleanup on release (zero-cost if clean).
#[derive(Default)]
pub struct ConnectionState {
    pub watch_active: bool,
    pub multi_active: bool,
    pub tracking_enabled: bool,
    pub db_selected: u8,
    pub client_name_changed: bool,
    pub subscriptions: Vec<ScopeSubscription>,
}

impl ConnectionState {
    pub fn is_clean(&self) -> bool {
        !self.watch_active
            && !self.multi_active
            && !self.tracking_enabled
            && self.db_selected == 0
            && !self.client_name_changed
            && self.subscriptions.is_empty()
    }

    pub fn has_subscriptions(&self) -> bool {
        !self.subscriptions.is_empty()
    }
}

pub enum ScopeSubscription {
    Channel(Vec<u8>),
    Pattern(Vec<u8>),
    ShardedChannel(Vec<u8>),
}

/// Update ConnectionState based on a command about to execute.
pub fn update_state_for_command(state: &mut ConnectionState, cmd: &str, args: &[&[u8]]) {
    match cmd.to_uppercase().as_str() {
        "WATCH" => state.watch_active = true,
        "UNWATCH" => state.watch_active = false,
        "MULTI" => state.multi_active = true,
        "EXEC" | "DISCARD" => {
            state.watch_active = false;
            state.multi_active = false;
        }
        "SELECT" => {
            if let Some(b) = args.first() {
                if let Ok(s) = std::str::from_utf8(b) {
                    if let Ok(db) = s.parse::<u8>() {
                        state.db_selected = db;
                    }
                }
            }
        }
        "CLIENT" => {
            if args.len() >= 2 {
                let sub = std::str::from_utf8(args[0]).unwrap_or("").to_uppercase();
                if sub == "TRACKING" {
                    let v = std::str::from_utf8(args[1]).unwrap_or("").to_uppercase();
                    state.tracking_enabled = v == "ON";
                } else if sub == "SETNAME" {
                    state.client_name_changed = true;
                }
            }
        }
        "SUBSCRIBE" | "PSUBSCRIBE" | "SSUBSCRIBE" => {
            for arg in args {
                let s = match cmd.to_uppercase().as_str() {
                    "PSUBSCRIBE" => ScopeSubscription::Pattern(arg.to_vec()),
                    "SSUBSCRIBE" => ScopeSubscription::ShardedChannel(arg.to_vec()),
                    _ => ScopeSubscription::Channel(arg.to_vec()),
                };
                state.subscriptions.push(s);
            }
        }
        _ => {}
    }
}

/// Configuration for per-client scope pool.
pub struct ScopePoolConfig {
    pub max_total: u32,
    pub idle_timeout: Duration,
    pub request_timeout: Duration,
}

impl Default for ScopePoolConfig {
    fn default() -> Self {
        Self {
            max_total: 64,
            idle_timeout: Duration::from_secs(30),
            request_timeout: Duration::from_secs(5),
        }
    }
}

/// A dedicated connection for isolated execution.
///
/// Cluster mode support:
/// - `pinned_slot`: set after first command with keys (from key hash slot)
/// - Subsequent commands must target the same slot or have no keys
/// - MOVED errors surface to caller (WATCH state can't survive migration)
pub struct ScopedConnection {
    pub scope_id: u64,
    pub connection: redis::aio::MultiplexedConnection,
    pub created_at: Instant,
    pub last_idle_at: Instant,
    pub borrowed_at: Option<Instant>,
    pub state: ConnectionState,
    /// In cluster mode: the slot this scope is pinned to after first keyed command.
    /// None means not yet pinned (no keyed command issued).
    pub pinned_slot: Option<u16>,
}

/// Per-client scope pool.
pub struct ScopePool {
    pub config: ScopePoolConfig,
    pub idle: VecDeque<ScopedConnection>,
    pub in_use: DashMap<u64, ()>,
    next_scope_id: AtomicU64,
    pub total_count: AtomicU32,
    pub state: AtomicU8,
    pub connection_request_bytes: Vec<u8>,
    /// The parent client_id that owns this scope pool (for accessing client config).
    pub parent_client_id: u64,
}

impl ScopePool {
    pub fn new(config: ScopePoolConfig, connection_request_bytes: Vec<u8>, parent_client_id: u64) -> Self {
        Self {
            config,
            idle: VecDeque::new(),
            in_use: DashMap::new(),
            next_scope_id: AtomicU64::new(1),
            total_count: AtomicU32::new(0),
            state: AtomicU8::new(POOL_RUNNING),
            connection_request_bytes,
            parent_client_id,
        }
    }

    pub fn next_id(&self) -> u64 {
        self.next_scope_id.fetch_add(1, Ordering::Relaxed)
    }

    /// Non-blocking acquire. Returns scope_id >= 0, -1 if exhausted.
    pub fn try_acquire(&mut self, registry: &DashMap<u64, ScopeEntry>) -> i64 {
        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            return -1;
        }
        if let Some(mut conn) = self.idle.pop_back() {
            // Evict if idle too long
            let idle_duration = Instant::now().duration_since(conn.last_idle_at);
            if idle_duration > self.config.idle_timeout {
                self.total_count.fetch_sub(1, Ordering::AcqRel);
                // Try next idle connection (recursive pop)
                while let Some(mut next) = self.idle.pop_back() {
                    let d = Instant::now().duration_since(next.last_idle_at);
                    if d <= self.config.idle_timeout {
                        let scope_id = next.scope_id;
                        next.borrowed_at = Some(Instant::now());
                        next.state = ConnectionState::default();
                        registry.insert(scope_id, ScopeEntry {
                            connection: Arc::new(TokioMutex::new(next)),
                        });
                        self.in_use.insert(scope_id, ());
                        return scope_id as i64;
                    }
                    self.total_count.fetch_sub(1, Ordering::AcqRel);
                }
            } else {
                let scope_id = conn.scope_id;
                conn.borrowed_at = Some(Instant::now());
                conn.state = ConnectionState::default();
                registry.insert(scope_id, ScopeEntry {
                    connection: Arc::new(TokioMutex::new(conn)),
                });
                self.in_use.insert(scope_id, ());
                return scope_id as i64;
            }
        }
        if self.total_count.load(Ordering::Acquire) < self.config.max_total {
            self.total_count.fetch_add(1, Ordering::AcqRel);
        }
        -1
    }

    /// Release a scope. Zero-cost if state is clean.
    pub fn release(&mut self, scope_id: u64, registry: &DashMap<u64, ScopeEntry>) -> bool {
        if self.in_use.remove(&scope_id).is_none() {
            return false;
        }
        let entry = registry.remove(&scope_id);
        let Some((_, entry)) = entry else { return false; };

        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            self.total_count.fetch_sub(1, Ordering::AcqRel);
            return true;
        }

        match entry.connection.try_lock() {
            Ok(conn) => {
                if conn.state.is_clean() {
                    let idle_conn = ScopedConnection {
                        scope_id: conn.scope_id,
                        connection: conn.connection.clone(),
                        created_at: conn.created_at,
                        last_idle_at: Instant::now(),
                        borrowed_at: None,
                        state: ConnectionState::default(),
                        pinned_slot: None,
                    };
                    drop(conn);
                    self.idle.push_back(idle_conn);
                } else {
                    // Dirty state — spawn async cleanup (UNSUBSCRIBE, DISCARD, etc.)
                    // After successful cleanup, return the connection to the idle pool.
                    let conn_arc = entry.connection.clone();
                    let request_timeout = self.config.request_timeout;

                    // We need the pool Arc to re-insert after cleanup.
                    // The caller must provide it via the scope pool registry.
                    let client_id = self.parent_client_id;
                    let pools = get_client_scope_pools();
                    let pool_arc = pools.get(&client_id).map(|p| p.value().clone());

                    tokio::spawn(async move {
                        let mut guard = conn_arc.lock().await;
                        let timeout = request_timeout * 2;
                        let cleanup_result = tokio::time::timeout(timeout, async {
                            if guard.state.has_subscriptions() {
                                let mut channels = Vec::new();
                                let mut patterns = Vec::new();
                                let mut sharded = Vec::new();
                                for sub in &guard.state.subscriptions {
                                    match sub {
                                        ScopeSubscription::Channel(c) => channels.push(c.clone()),
                                        ScopeSubscription::Pattern(p) => patterns.push(p.clone()),
                                        ScopeSubscription::ShardedChannel(s) => sharded.push(s.clone()),
                                    }
                                }
                                if !channels.is_empty() {
                                    let mut cmd = redis::Cmd::new();
                                    cmd.arg("UNSUBSCRIBE");
                                    for c in &channels { cmd.arg(c.as_slice()); }
                                    let _ = guard.connection.send_packed_command(&cmd).await;
                                }
                                if !patterns.is_empty() {
                                    let mut cmd = redis::Cmd::new();
                                    cmd.arg("PUNSUBSCRIBE");
                                    for p in &patterns { cmd.arg(p.as_slice()); }
                                    let _ = guard.connection.send_packed_command(&cmd).await;
                                }
                                if !sharded.is_empty() {
                                    let mut cmd = redis::Cmd::new();
                                    cmd.arg("SUNSUBSCRIBE");
                                    for s in &sharded { cmd.arg(s.as_slice()); }
                                    let _ = guard.connection.send_packed_command(&cmd).await;
                                }
                            }
                            if guard.state.multi_active {
                                let _ = guard.connection.send_packed_command(
                                    &redis::Cmd::new().arg("DISCARD")).await;
                            } else if guard.state.watch_active {
                                let _ = guard.connection.send_packed_command(
                                    &redis::Cmd::new().arg("UNWATCH")).await;
                            }
                            if guard.state.tracking_enabled {
                                let _ = guard.connection.send_packed_command(
                                    &redis::Cmd::new().arg("CLIENT").arg("TRACKING").arg("OFF")).await;
                            }
                            if guard.state.db_selected != 0 {
                                let _ = guard.connection.send_packed_command(
                                    &redis::Cmd::new().arg("SELECT").arg("0")).await;
                            }
                        }).await;

                        // If cleanup succeeded and we have a pool reference, return to idle.
                        if cleanup_result.is_ok() {
                            if let Some(pool_arc) = pool_arc {
                                let idle_conn = ScopedConnection {
                                    scope_id: guard.scope_id,
                                    connection: guard.connection.clone(),
                                    created_at: guard.created_at,
                                    last_idle_at: Instant::now(),
                                    borrowed_at: None,
                                    state: ConnectionState::default(),
                                    pinned_slot: None,
                                };
                                drop(guard);

                                let mut pool = pool_arc.lock().await;
                                if pool.state.load(Ordering::Acquire) == POOL_RUNNING {
                                    pool.idle.push_back(idle_conn);
                                    // total_count was never decremented — connection is back in pool
                                } else {
                                    pool.total_count.fetch_sub(1, Ordering::AcqRel);
                                }
                            } else {
                                // No pool reference — discard connection
                                drop(guard);
                            }
                        } else {
                            // Cleanup timed out — discard the connection
                            drop(guard);
                            if let Some(pool_arc) = pool_arc {
                                let pool = pool_arc.lock().await;
                                pool.total_count.fetch_sub(1, Ordering::AcqRel);
                            }
                        }
                    });
                }
                true
            }
            Err(_) => {
                self.total_count.fetch_sub(1, Ordering::AcqRel);
                true
            }
        }
    }

    pub fn destroy(&mut self, registry: &DashMap<u64, ScopeEntry>) {
        self.state.store(POOL_CLOSED, Ordering::Release);
        self.idle.clear();
        let keys: Vec<u64> = self.in_use.iter().map(|e| *e.key()).collect();
        for key in keys {
            self.in_use.remove(&key);
            registry.remove(&key);
        }
        self.total_count.store(0, Ordering::Release);
    }
}

/// Entry in the global scope registry for command routing.
pub struct ScopeEntry {
    pub connection: Arc<TokioMutex<ScopedConnection>>,
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCOPE REGISTRIES
// ═══════════════════════════════════════════════════════════════════════════════

/// Global scope registry: scope_id → ScopeEntry (for command dispatch).
static SCOPE_REGISTRY: OnceLock<DashMap<u64, ScopeEntry>> = OnceLock::new();

/// Per-client scope pools: client_id → ScopePool.
static CLIENT_SCOPE_POOLS: OnceLock<DashMap<u64, Arc<TokioMutex<ScopePool>>>> = OnceLock::new();

pub fn get_scope_registry() -> &'static DashMap<u64, ScopeEntry> {
    SCOPE_REGISTRY.get_or_init(DashMap::new)
}

pub fn get_client_scope_pools() -> &'static DashMap<u64, Arc<TokioMutex<ScopePool>>> {
    CLIENT_SCOPE_POOLS.get_or_init(DashMap::new)
}

/// Get or create a scope pool for a client (atomic via DashMap entry API).
pub fn get_or_create_scope_pool(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
) -> Arc<TokioMutex<ScopePool>> {
    get_client_scope_pools()
        .entry(client_id)
        .or_insert_with(|| {
            Arc::new(TokioMutex::new(ScopePool::new(
                ScopePoolConfig::default(),
                connection_request_bytes,
                client_id,
            )))
        })
        .value()
        .clone()
}


// ═══════════════════════════════════════════════════════════════════════════════
// CLUSTER SLOT VALIDATION
// ═══════════════════════════════════════════════════════════════════════════════

/// Compute the hash slot for a key (CRC16 mod 16384).
/// Re-exports redis-rs's slot computation for use by binding layers.
pub fn slot_for_key(key: &[u8]) -> u16 {
    redis::cluster_topology::get_slot(key)
}

/// Validate that a command's keys target the scope's pinned slot.
/// Returns Ok(slot) if consistent, Err if cross-slot.
/// If scope has no pinned slot yet, returns the slot from the first key.
pub fn validate_scope_slot(
    pinned: Option<u16>,
    keys: &[&[u8]],
) -> Result<Option<u16>, String> {
    if keys.is_empty() {
        return Ok(pinned); // No keys — no slot constraint
    }

    let first_slot = slot_for_key(keys[0]);

    // Validate all keys are in the same slot
    for key in &keys[1..] {
        let s = slot_for_key(key);
        if s != first_slot {
            return Err(format!(
                "Cross-slot error: key targets slot {} but scope is pinned to slot {}",
                s, first_slot
            ));
        }
    }

    // Validate against pinned slot
    match pinned {
        None => Ok(Some(first_slot)), // First keyed command — pin to this slot
        Some(p) if p == first_slot => Ok(Some(p)), // Consistent
        Some(p) => Err(format!(
            "Cross-slot error: command targets slot {} but scope is pinned to slot {}",
            first_slot, p
        )),
    }
}
