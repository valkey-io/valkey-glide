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
