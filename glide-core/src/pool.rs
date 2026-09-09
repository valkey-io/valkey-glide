// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Client-Instance Pool (RFC #5815)
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
use std::sync::atomic::{AtomicBool, AtomicU8, AtomicU32, AtomicU64, Ordering};
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
    /// Client type tag: 0 = SyncClient, 1 = AsyncClient.
    /// Stored as u8 to avoid storing function pointers in the config.
    /// The actual ClientType (with callbacks) is passed at pool creation time
    /// and stored separately in the pool registry for background creation.
    pub is_async: bool,
    /// The database_id from the connection config (for reset on release).
    /// Defaults to 0 if not specified in the connection request.
    pub configured_database_id: u32,
    /// Maximum inactivity time for a borrowed client. The timer resets on every
    /// command sent. When a borrowed client has no command activity for this duration,
    /// the monitor logs a warning and discards the connection (the pool creates a
    /// fresh one on the next acquire). Set to Duration::ZERO to disable abandon detection.
    /// Default: 300 seconds (5 minutes).
    pub abandon_timeout: Duration,
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
    /// True while the client is executing a blocking command (BLPOP, XREAD BLOCK, etc.).
    /// The abandon monitor skips clients with this flag set.
    pub is_blocking: Arc<AtomicBool>,
}

// ═══════════════════════════════════════════════════════════════════════════════
// CLIENT POOL
// ═══════════════════════════════════════════════════════════════════════════════

/// The client-instance pool. Thread-safe via TokioMutex at the registry level.
pub struct ClientPool {
    pub config: PoolConfig,
    /// LIFO idle stack — most recently returned client is at the back.
    pub idle: VecDeque<PooledClient>,
    /// Currently borrowed clients (client_id → PooledClient).
    pub in_use: DashMap<u64, PooledClient>,
    /// Current total count (idle + in_use + creating).
    pub total_count: AtomicU32,
    /// Pool lifecycle state.
    pub state: AtomicU8,
    /// Condvar notified when a client is returned to idle (for blocking acquire).
    pub release_notify: Arc<(std::sync::Mutex<()>, std::sync::Condvar)>,
    /// Client IDs discarded by the abandon monitor. The FFI layer drains this
    /// on acquire/destroy to clean up adapter mappings and close connections.
    pub discarded_ids: Vec<u64>,
}

impl ClientPool {
    /// Create a new pool with validated config.
    pub fn new(config: PoolConfig) -> Result<Self, PoolError> {
        if config.max_size < 1 {
            return Err(PoolError::InvalidConfig("max_size must be >= 1".into()));
        }
        if config.min_idle > config.max_size {
            return Err(PoolError::InvalidConfig(
                "min_idle must be <= max_size".into(),
            ));
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
            release_notify: Arc::new((std::sync::Mutex::new(()), std::sync::Condvar::new())),
            discarded_ids: Vec::new(),
        })
    }

    /// Generate a globally unique client_id (unique across all pools).
    pub fn next_id(&self) -> u64 {
        NEXT_CLIENT_ID.fetch_add(1, Ordering::Relaxed)
    }

    /// Non-blocking acquire. Returns client_id on success.
    /// Returns -1 if pool is closed/closing, -3 if no idle client available.
    /// Evicts idle connections past idle_timeout internally.
    pub fn try_acquire(&mut self) -> i64 {
        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            return -1;
        }

        while let Some(mut entry) = self.idle.pop_back() {
            let idle_duration = Instant::now().duration_since(entry.last_idle_at);
            if idle_duration > self.config.idle_timeout {
                self.total_count.fetch_sub(1, Ordering::AcqRel);
                logger_core::log_debug(
                    "pool",
                    format!(
                        "Evicted idle client {} (idle {:?}, threshold {:?})",
                        entry.client_id, idle_duration, self.config.idle_timeout
                    ),
                );
                continue;
            }
            let client_id = entry.client_id;
            entry.state = ClientState::InUse;
            entry.borrowed_at = Some(Instant::now());
            self.in_use.insert(client_id, entry);
            return client_id as i64;
        }

        -3
    }

    /// Whether background creation should be triggered (room below max_size).
    pub fn should_create(&self) -> bool {
        self.state.load(Ordering::Acquire) == POOL_RUNNING
            && self.total_count.load(Ordering::Acquire) < self.config.max_size
    }

    /// Add a newly created client to the idle pool. Returns the assigned client_id.
    /// Increments total_count. Use `add_client_reserved` if the slot was pre-reserved.
    pub fn add_client(&mut self, client: GlideClient) -> u64 {
        let client_id = self.next_id();
        let entry = PooledClient {
            client_id,
            client,
            created_at: Instant::now(),
            last_idle_at: Instant::now(),
            borrowed_at: None,
            state: ClientState::Idle,
            is_blocking: Arc::new(AtomicBool::new(false)),
        };
        self.idle.push_back(entry);
        self.total_count.fetch_add(1, Ordering::AcqRel);
        client_id
    }

    /// Add a newly created client to the idle pool when the caller already
    /// pre-incremented total_count (e.g., background creation after should_create check).
    /// Returns the assigned client_id.
    pub fn add_client_reserved(&mut self, client: GlideClient) -> u64 {
        let client_id = self.next_id();
        let entry = PooledClient {
            client_id,
            client,
            created_at: Instant::now(),
            last_idle_at: Instant::now(),
            borrowed_at: None,
            state: ClientState::Idle,
            is_blocking: Arc::new(AtomicBool::new(false)),
        };
        self.idle.push_back(entry);
        client_id
    }

    /// Release a client back to the idle pool by client_id.
    ///
    /// Removes the client from `in_use` and returns it as `Some(entry)` for the
    /// caller to perform async state reset before returning to idle.
    /// Returns `None` if client_id was not found in `in_use`.
    ///
    /// The caller is responsible for:
    /// 1. Calling `client.reset_connection_state(configured_db)` on the entry
    /// 2. Calling `return_to_idle(entry)` to put it back in the idle pool
    ///    Or on failure, calling `discard_client()` to decrement the total count.
    pub fn take_for_release(&mut self, client_id: u64) -> Option<PooledClient> {
        let entry = self.in_use.remove(&client_id);
        entry.map(|(_, e)| e)
    }

    /// Return a client to the idle pool after successful state reset.
    pub fn return_to_idle(&mut self, mut entry: PooledClient) {
        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            self.total_count.fetch_sub(1, Ordering::AcqRel);
            return;
        }
        entry.state = ClientState::Idle;
        entry.last_idle_at = Instant::now();
        entry.borrowed_at = None;
        entry.is_blocking.store(false, Ordering::Release);
        self.idle.push_back(entry);

        // Notify any threads waiting in blocking acquire
        let (_, condvar) = &*self.release_notify;
        condvar.notify_one();
    }

    /// Discard a client (after failed state reset). Decrements total count.
    pub fn discard_client(&mut self) {
        self.total_count.fetch_sub(1, Ordering::AcqRel);
        // Notify waiters since capacity freed up for a new connection
        let (_, condvar) = &*self.release_notify;
        condvar.notify_one();
    }

    /// Destroy the pool — drop all clients.
    pub fn destroy(&mut self) {
        // Warn if any clients are still borrowed (likely leak)
        let in_use_count = self.in_use.len();
        if in_use_count > 0 {
            logger_core::log_warn(
                "pool",
                format!(
                    "Pool destroyed with {} client(s) still borrowed — possible connection leak. \
                     Ensure all acquired clients are released before closing the pool.",
                    in_use_count
                ),
            );
        }

        self.state.store(POOL_CLOSED, Ordering::Release);
        self.idle.clear();
        self.in_use.clear();
        self.total_count.store(0, Ordering::Release);
        logger_core::log_info("pool", "Pool destroyed");
    }

    /// Get idle count.
    pub fn idle_count(&self) -> u32 {
        self.idle.len() as u32
    }

    /// Drain client IDs discarded by the abandon monitor.
    /// The FFI layer calls this to clean up adapter mappings and close connections.
    pub fn drain_discarded_ids(&mut self) -> Vec<u64> {
        std::mem::take(&mut self.discarded_ids)
    }

    /// Get active (in-use) count.
    pub fn active_count(&self) -> u32 {
        self.in_use.len() as u32
    }
}

/// Async release of a pooled client with state reset and leak protection.
///
/// This is the shared implementation used by all language bindings (FFI, JNI).
/// It performs:
/// 1. Takes the client out of `in_use`
/// 2. Sends DISCARD + SELECT (batched reset) with a timeout of 2× request_timeout
/// 3. Returns the client to idle on success, or discards it on failure
/// 4. A `LeakGuard` ensures `discard_client()` is called if the future is cancelled
///
/// Call this from a spawned task. The pool_arc should already be cloned for the task.
pub async fn release_client_async(pool_arc: Arc<TokioMutex<ClientPool>>, client_id: u64) {
    let (mut entry, configured_db, timeout_duration) = {
        let mut pool = pool_arc.lock().await;
        let configured_db = pool.config.configured_database_id;
        let timeout = pool.config.request_timeout * 2;
        match pool.take_for_release(client_id) {
            Some(e) => (e, configured_db, timeout),
            None => return,
        }
    };

    // Safety: if this task is cancelled after take_for_release but before
    // return_to_idle/discard_client, decrement total_count to prevent slot leak.
    // Note: blocking_lock() is safe here because this code runs on the dedicated
    // POOL_RUNTIME (not the main tokio runtime), and cancellation only occurs when
    // the pool is being destroyed (no other task holds the lock on this runtime).
    let pool_for_guard = pool_arc.clone();
    struct LeakGuard {
        pool: Option<Arc<TokioMutex<ClientPool>>>,
    }
    impl Drop for LeakGuard {
        fn drop(&mut self) {
            if let Some(pool_arc) = self.pool.take() {
                if let Ok(mut pool) = pool_arc.try_lock() {
                    pool.discard_client();
                } else {
                    pool_arc.blocking_lock().discard_client();
                }
            }
        }
    }
    let mut guard = LeakGuard {
        pool: Some(pool_for_guard),
    };

    // Reset state: DISCARD (cancel MULTI/WATCH) + SELECT <configured_db>
    let reset_result = tokio::time::timeout(
        timeout_duration,
        entry.client.reset_connection_state(configured_db),
    )
    .await;

    // Disarm the guard — we handle the outcome explicitly
    guard.pool = None;

    let mut pool = pool_arc.lock().await;
    match reset_result {
        Ok(Ok(_)) => pool.return_to_idle(entry),
        _ => {
            logger_core::log_warn_rate_limited!(
                "pool",
                10,
                "Client reset failed on release — discarding connection"
            );
            pool.discard_client();
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLOBAL REGISTRY
// ═══════════════════════════════════════════════════════════════════════════════

/// Global pool registry: pool_id → Pool instance.
static POOL_REGISTRY: OnceLock<DashMap<u64, Arc<TokioMutex<ClientPool>>>> = OnceLock::new();
static NEXT_POOL_ID: AtomicU64 = AtomicU64::new(1);
/// Abandon monitor task handles, keyed by pool_id. Stored outside the pool
/// mutex to allow abort on destroy without locking.
static MONITOR_HANDLES: OnceLock<DashMap<u64, tokio::task::JoinHandle<()>>> = OnceLock::new();

fn get_monitor_handles() -> &'static DashMap<u64, tokio::task::JoinHandle<()>> {
    MONITOR_HANDLES.get_or_init(DashMap::new)
}
/// Global client_id allocator — ensures uniqueness across all pools.
static NEXT_CLIENT_ID: AtomicU64 = AtomicU64::new(1);

/// Allocate a globally unique client_id (can be called without holding a pool lock).
pub fn allocate_client_id() -> u64 {
    NEXT_CLIENT_ID.fetch_add(1, Ordering::Relaxed)
}

pub fn get_pool_registry() -> &'static DashMap<u64, Arc<TokioMutex<ClientPool>>> {
    POOL_REGISTRY.get_or_init(DashMap::new)
}

/// Register a pool. Returns assigned pool_id.
pub fn register_pool(pool: ClientPool) -> u64 {
    let pool_id = NEXT_POOL_ID.fetch_add(1, Ordering::Relaxed);
    logger_core::log_info(
        "pool",
        format!(
            "Pool {} created (max_size={}, min_idle={}, abandon_timeout={:?})",
            pool_id, pool.config.max_size, pool.config.min_idle, pool.config.abandon_timeout
        ),
    );
    get_pool_registry().insert(pool_id, Arc::new(TokioMutex::new(pool)));
    pool_id
}

/// Start the abandon monitor for a registered pool.
/// Must be called from within a tokio runtime context.
/// No-op if `abandon_timeout` is zero (disabled).
pub fn start_abandon_monitor(pool_id: u64, runtime_handle: &tokio::runtime::Handle) {
    let pool_arc = match get_pool(pool_id) {
        Some(arc) => arc,
        None => {
            logger_core::log_debug(
                "pool",
                format!(
                    "start_abandon_monitor: pool {} not found (already destroyed?)",
                    pool_id
                ),
            );
            return;
        }
    };

    let abandon_timeout = {
        let pool = pool_arc.blocking_lock();
        pool.config.abandon_timeout
    };

    if abandon_timeout.is_zero() {
        logger_core::log_debug("pool", "Abandon monitor disabled (timeout=0)");
        return;
    }

    // Wake at half the abandon timeout for timely detection
    let scan_interval = abandon_timeout / 2;
    logger_core::log_debug(
        "pool",
        format!(
            "Abandon monitor started for pool {} (timeout={:?}, scan_interval={:?})",
            pool_id, abandon_timeout, scan_interval
        ),
    );
    let pool_arc_monitor = pool_arc.clone();

    let handle = runtime_handle.spawn(async move {
        loop {
            tokio::time::sleep(scan_interval).await;

            let abandoned_ids: Vec<u64> = {
                let pool = pool_arc_monitor.lock().await;
                if pool.state.load(Ordering::Acquire) != POOL_RUNNING {
                    break;
                }
                let now = Instant::now();
                pool.in_use
                    .iter()
                    .filter_map(|entry| {
                        // Skip clients currently executing blocking commands
                        if entry.value().is_blocking.load(Ordering::Acquire) {
                            return None;
                        }
                        let borrowed_at = entry.value().borrowed_at?;
                        if now.duration_since(borrowed_at) > abandon_timeout {
                            Some(*entry.key())
                        } else {
                            None
                        }
                    })
                    .collect()
            };

            for client_id in abandoned_ids {
                logger_core::log_warn(
                    "pool",
                    format!(
                        "Abandon detection: client {} exceeded inactivity timeout ({:?}) — \
                         discarding connection (stale release safety)",
                        client_id, abandon_timeout
                    ),
                );
                // Discard rather than return-to-idle: a force-released client may still
                // have a stale release pending from the original borrower. Discarding
                // guarantees two borrowers never share a connection.
                let mut pool = pool_arc_monitor.lock().await;
                // Revalidate under lock: activity may have been refreshed or blocking
                // flag set between the scan and this removal.
                if let Some(entry) = pool.in_use.get(&client_id) {
                    if entry.value().is_blocking.load(Ordering::Acquire) {
                        continue;
                    }
                    if entry.value().borrowed_at.is_some_and(|borrowed_at| {
                        Instant::now().duration_since(borrowed_at) <= abandon_timeout
                    }) {
                        continue;
                    }
                }
                if pool.in_use.remove(&client_id).is_some() {
                    pool.discard_client();
                    pool.discarded_ids.push(client_id);
                }
            }
        }
    });

    // Store handle outside the pool mutex so destroy() can abort without locking.
    get_monitor_handles().insert(pool_id, handle);
}

/// Mark a borrowed client as currently executing a blocking command.
/// The abandon monitor will skip this client until unmarked.
/// This is a no-op if the client is not found in any pool's `in_use` map.
pub fn mark_client_blocking(pool_id: u64, client_id: u64, blocking: bool) -> bool {
    let pool_arc = match get_pool(pool_id) {
        Some(arc) => arc,
        None => return false,
    };
    // Use try_lock to avoid blocking the command dispatch path.
    // If the pool is locked (e.g., during release), skip — the client
    // will either be released soon or caught on the next monitor scan.
    #[allow(clippy::collapsible_if)]
    if let Ok(pool) = pool_arc.try_lock() {
        if let Some(entry) = pool.in_use.get(&client_id) {
            entry.value().is_blocking.store(blocking, Ordering::Release);
        } else {
            return false;
        }
        // When unmarking (command completed), refresh borrowed_at so the client
        // isn't instantly reclaimable after a long-running blocking command.
        if !blocking {
            if let Some(mut entry) = pool.in_use.get_mut(&client_id) {
                entry.value_mut().borrowed_at = Some(Instant::now());
            }
        }
        return true;
    }
    false
}

/// Get the `is_blocking` flag Arc for a client (for use by the command dispatch path).
/// Returns None if the client is not currently borrowed from this pool.
pub fn get_client_blocking_flag(pool_id: u64, client_id: u64) -> Option<Arc<AtomicBool>> {
    let pool_arc = get_pool(pool_id)?;
    #[allow(clippy::collapsible_if)]
    if let Ok(pool) = pool_arc.try_lock() {
        if let Some(entry) = pool.in_use.get(&client_id) {
            return Some(entry.value().is_blocking.clone());
        }
    }
    None
}

/// Refresh a borrowed client's `borrowed_at` timestamp to the current instant.
/// Called on every command dispatch for pool-borrowed clients so the abandon
/// monitor measures inactivity (time since last command) rather than total
/// borrow duration. No-op if the pool or client is not found.
pub fn refresh_client_activity(pool_id: u64, client_id: u64) {
    let pool_arc = match get_pool(pool_id) {
        Some(arc) => arc,
        None => return,
    };
    #[allow(clippy::collapsible_if)]
    if let Ok(pool) = pool_arc.try_lock() {
        if let Some(mut entry) = pool.in_use.get_mut(&client_id) {
            entry.value_mut().borrowed_at = Some(Instant::now());
        }
    }
}

/// Get a pool by ID (cheap Arc clone).
pub fn get_pool(pool_id: u64) -> Option<Arc<TokioMutex<ClientPool>>> {
    get_pool_registry().get(&pool_id).map(|e| e.value().clone())
}

/// Remove a pool from the registry.
pub fn unregister_pool(pool_id: u64) -> Option<Arc<TokioMutex<ClientPool>>> {
    // Abort the abandon monitor before removing the pool
    if let Some((_, handle)) = get_monitor_handles().remove(&pool_id) {
        handle.abort();
    }
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
    /// Set while a blocking command is in flight; kept set only when it ends in a
    /// timeout or IO error (or is cancelled mid-flight), the cases that can leave a
    /// server-side waiter armed. A still-set connection is discarded on release;
    /// clean protocol errors clear it so the connection is reused.
    pub blocking_in_flight: bool,
}

impl ConnectionState {
    /// Create a new state with the given configured database as the "clean" baseline.
    pub fn with_configured_db(db: u8) -> Self {
        Self {
            db_selected: db,
            ..Default::default()
        }
    }

    /// Check if state is clean (no mutations from the initial configured state).
    /// `configured_db` is the database the connection was initialized with.
    pub fn is_clean_for(&self, configured_db: u8) -> bool {
        !self.watch_active
            && !self.multi_active
            && !self.tracking_enabled
            && self.db_selected == configured_db
            && !self.client_name_changed
            && self.subscriptions.is_empty()
            && !self.blocking_in_flight
    }

    /// Legacy check — clean means no state mutations at all (db must be 0).
    pub fn is_clean(&self) -> bool {
        self.is_clean_for(0)
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
#[allow(clippy::collapsible_if)]
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
        "CLIENT" if args.len() >= 2 => {
            let sub = std::str::from_utf8(args[0]).unwrap_or("").to_uppercase();
            if sub == "TRACKING" {
                let v = std::str::from_utf8(args[1]).unwrap_or("").to_uppercase();
                state.tracking_enabled = v == "ON";
            } else if sub == "SETNAME" {
                state.client_name_changed = true;
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
    pub min_idle: u32,
    pub idle_timeout: Duration,
    pub request_timeout: Duration,
    /// If true, send PING on borrow to verify connection health.
    /// Adds one round-trip per acquire but catches stale connections early.
    pub test_on_borrow: bool,
}

impl Default for ScopePoolConfig {
    fn default() -> Self {
        Self {
            max_total: 64,
            min_idle: 1,
            idle_timeout: Duration::from_secs(30),
            request_timeout: Duration::from_secs(5),
            test_on_borrow: false,
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
    /// The target slot this connection was created for (cluster routing).
    /// Used to match idle connections to acquire requests for the same slot range.
    pub target_slot: u16,
}

/// Per-client scope pool.
pub struct ScopePool {
    pub config: ScopePoolConfig,
    pub idle: VecDeque<ScopedConnection>,
    pub in_use: DashMap<u64, ()>,
    pub total_count: AtomicU32,
    pub state: AtomicU8,
    pub connection_request_bytes: Vec<u8>,
    /// The parent client_id that owns this scope pool (for accessing client config).
    pub parent_client_id: u64,
    /// The database_id from the connection config (for reset on release).
    pub configured_database_id: u32,
}

impl ScopePool {
    pub fn new(
        config: ScopePoolConfig,
        connection_request_bytes: Vec<u8>,
        parent_client_id: u64,
    ) -> Self {
        // Parse configured_database_id from the connection request
        #[cfg(feature = "proto")]
        let configured_database_id = {
            use protobuf::Message as _;
            crate::connection_request::ConnectionRequest::parse_from_bytes(
                &connection_request_bytes,
            )
            .ok()
            .map(|req| req.database_id)
            .unwrap_or(0)
        };
        #[cfg(not(feature = "proto"))]
        let configured_database_id = 0u32;

        Self {
            config,
            idle: VecDeque::new(),
            in_use: DashMap::new(),
            total_count: AtomicU32::new(0),
            state: AtomicU8::new(POOL_RUNNING),
            connection_request_bytes,
            parent_client_id,
            configured_database_id,
        }
    }

    pub fn next_id(&self) -> u64 {
        allocate_scope_id()
    }

    /// Non-blocking acquire. Returns scope_id >= 0, -1 if exhausted.
    pub fn try_acquire(&mut self, registry: &DashMap<u64, ScopeEntry>, routing_slot: u16) -> i64 {
        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            return -1;
        }

        // Scan idle connections for one matching the requested routing slot.
        // Connections targeting a different slot are kept aside and pushed back.
        let mut mismatched: Vec<ScopedConnection> = Vec::new();
        let mut found: Option<ScopedConnection> = None;

        while let Some(conn) = self.idle.pop_back() {
            // Evict if idle too long
            let idle_duration = Instant::now().duration_since(conn.last_idle_at);
            if idle_duration > self.config.idle_timeout {
                self.total_count.fetch_sub(1, Ordering::AcqRel);
                continue;
            }
            // Slot 0 is the default/standalone wildcard — always matches.
            // Otherwise, only reuse if target_slot matches.
            if conn.target_slot == routing_slot || routing_slot == 0 || conn.target_slot == 0 {
                found = Some(conn);
                break;
            }
            mismatched.push(conn);
        }

        // Push back mismatched connections (preserve them for future acquires)
        for conn in mismatched.into_iter().rev() {
            self.idle.push_back(conn);
        }

        if let Some(mut conn) = found {
            let scope_id = conn.scope_id;
            conn.borrowed_at = Some(Instant::now());
            conn.state = ConnectionState::default();
            registry.insert(
                scope_id,
                ScopeEntry {
                    connection: Arc::new(TokioMutex::new(conn)),
                },
            );
            self.in_use.insert(scope_id, ());
            return scope_id as i64;
        }

        if self.total_count.load(Ordering::Acquire) < self.config.max_total {
            self.total_count.fetch_add(1, Ordering::AcqRel);
        }
        -1
    }

    /// Release a scope. Zero-cost if state is clean.
    #[allow(clippy::needless_borrow)]
    pub fn release(&mut self, scope_id: u64, registry: &DashMap<u64, ScopeEntry>) -> bool {
        if self.in_use.remove(&scope_id).is_none() {
            return false;
        }
        let entry = registry.remove(&scope_id);
        let Some((_, entry)) = entry else {
            return false;
        };

        if self.state.load(Ordering::Acquire) != POOL_RUNNING {
            self.total_count.fetch_sub(1, Ordering::AcqRel);
            return true;
        }

        match entry.connection.try_lock() {
            Ok(conn) => {
                if conn.state.is_clean_for(self.configured_database_id as u8) {
                    let idle_conn = ScopedConnection {
                        scope_id: conn.scope_id,
                        connection: conn.connection.clone(),
                        created_at: conn.created_at,
                        last_idle_at: Instant::now(),
                        borrowed_at: None,
                        state: ConnectionState::default(),
                        pinned_slot: None,
                        target_slot: conn.target_slot,
                    };
                    drop(conn);
                    self.idle.push_back(idle_conn);
                } else {
                    // A blocking command left the connection unrecoverable (armed
                    // waiter); no cleanup command fixes that, so discard rather than
                    // return to idle.
                    if conn.state.blocking_in_flight {
                        drop(conn);
                        self.total_count.fetch_sub(1, Ordering::AcqRel);
                        return true;
                    }
                    // Dirty state — pipeline all cleanup commands in a single round-trip.
                    // If any command fails or the pipeline times out, discard the connection.
                    let conn_arc = entry.connection.clone();
                    let request_timeout = self.config.request_timeout;
                    let self_configured_db = self.configured_database_id;

                    let client_id = self.parent_client_id;
                    let pools = get_client_scope_pools();
                    let pool_arc = pools.get(&client_id).map(|p| p.value().clone());

                    tokio::spawn(async move {
                        let mut guard = conn_arc.lock().await;
                        let timeout = request_timeout * 2;

                        // Build a single pipeline with all cleanup commands
                        let mut pipe = redis::Pipeline::new();
                        let mut cmd_count = 0;

                        // DISCARD (implicitly unwatches) or UNWATCH
                        if guard.state.multi_active {
                            pipe.cmd("DISCARD");
                            cmd_count += 1;
                        } else if guard.state.watch_active {
                            pipe.cmd("UNWATCH");
                            cmd_count += 1;
                        }

                        // Subscription cleanup
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
                                for c in &channels {
                                    cmd.arg(c.as_slice());
                                }
                                pipe.add_command(cmd);
                                cmd_count += 1;
                            }
                            if !patterns.is_empty() {
                                let mut cmd = redis::Cmd::new();
                                cmd.arg("PUNSUBSCRIBE");
                                for p in &patterns {
                                    cmd.arg(p.as_slice());
                                }
                                pipe.add_command(cmd);
                                cmd_count += 1;
                            }
                            if !sharded.is_empty() {
                                let mut cmd = redis::Cmd::new();
                                cmd.arg("SUNSUBSCRIBE");
                                for s in &sharded {
                                    cmd.arg(s.as_slice());
                                }
                                pipe.add_command(cmd);
                                cmd_count += 1;
                            }
                        }

                        // CLIENT TRACKING OFF
                        if guard.state.tracking_enabled {
                            pipe.cmd("CLIENT").arg("TRACKING").arg("OFF");
                            cmd_count += 1;
                        }

                        // SELECT <configured_db> (reset database)
                        if guard.state.db_selected != self_configured_db as u8 {
                            pipe.cmd("SELECT").arg(self_configured_db.to_string());
                            cmd_count += 1;
                        }

                        // Send the entire pipeline as one round-trip with timeout
                        let cleanup_result = if cmd_count > 0 {
                            tokio::time::timeout(
                                timeout,
                                guard.connection.send_packed_commands(&pipe, 0, cmd_count),
                            )
                            .await
                        } else {
                            // No cleanup needed (shouldn't reach here, but handle gracefully)
                            Ok(Ok(vec![]))
                        };

                        // If cleanup succeeded, return connection to idle.
                        // If any error (timeout, command failure), discard the connection.
                        let success = matches!(cleanup_result, Ok(Ok(_)));

                        if success {
                            if let Some(pool_arc) = pool_arc {
                                let idle_conn = ScopedConnection {
                                    scope_id: guard.scope_id,
                                    connection: guard.connection.clone(),
                                    created_at: guard.created_at,
                                    last_idle_at: Instant::now(),
                                    borrowed_at: None,
                                    state: ConnectionState::default(),
                                    pinned_slot: None,
                                    target_slot: guard.target_slot,
                                };
                                drop(guard);

                                let mut pool = pool_arc.lock().await;
                                if pool.state.load(Ordering::Acquire) == POOL_RUNNING {
                                    pool.idle.push_back(idle_conn);
                                } else {
                                    pool.total_count.fetch_sub(1, Ordering::AcqRel);
                                }
                            } else {
                                drop(guard);
                            }
                        } else {
                            // Cleanup failed — discard the connection entirely
                            logger_core::log_warn_rate_limited!(
                                "pool",
                                10,
                                "Scope connection cleanup failed — discarding connection"
                            );
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
                // A command still holds the connection lock at release time (any
                // in-flight command, though almost always a blocking one whose binding
                // was cancelled). Discard rather than reuse: the connection may have an
                // armed server-side waiter, and re-checking state after the command
                // finishes would race a late push that clears it and makes the
                // connection look reusable after the push was already consumed.
                //
                // Reclaim the slot synchronously — we hold the pool lock and checked
                // POOL_RUNNING above. It can't be deferred: for an unbounded blocking
                // command (BLPOP key 0) the lock may never free, so a decrement gated
                // on it would leak the slot.
                self.total_count.fetch_sub(1, Ordering::AcqRel);
                let conn_arc = entry.connection.clone();
                tokio::spawn(async move {
                    // Best-effort: drop the connection once the command frees the lock
                    // (accounting already done above). Parks harmlessly if it never does.
                    let conn = conn_arc.lock().await;
                    drop(conn);
                });
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

/// Global scope_id allocator — ensures uniqueness across all scope pools.
///
/// IDs are allocated sequentially from a single atomic counter shared by all pools.
/// This means IDs are not sequential per-client — if client A gets scope 1 and client B
/// gets scope 2, client A's next scope will be 3, not 2. This is intentional: it prevents
/// ID collisions when multiple pool clients each have their own scope pool, and simplifies
/// the global SCOPE_REGISTRY lookup (every ID is unique regardless of origin).
static NEXT_SCOPE_ID: AtomicU64 = AtomicU64::new(1);

/// Allocate a globally unique scope_id.
pub fn allocate_scope_id() -> u64 {
    NEXT_SCOPE_ID.fetch_add(1, Ordering::Relaxed)
}

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
/// On first creation, spawns `min_idle` background connection tasks.
pub fn get_or_create_scope_pool(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
) -> Arc<TokioMutex<ScopePool>> {
    let pools = get_client_scope_pools();
    // Fast path: pool already exists
    if let Some(existing) = pools.get(&client_id) {
        return existing.value().clone();
    }

    // Slow path: create pool
    let config = ScopePoolConfig::default();
    let pool = Arc::new(TokioMutex::new(ScopePool::new(
        config,
        connection_request_bytes.clone(),
        client_id,
    )));

    let inserted = pools.entry(client_id).or_insert_with(|| pool.clone());
    inserted.value().clone()
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
pub fn validate_scope_slot(pinned: Option<u16>, keys: &[&[u8]]) -> Result<Option<u16>, String> {
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

#[cfg(test)]
mod connection_state_tests {
    use super::ConnectionState;

    const CONFIGURED_DB: u8 = 0;

    #[test]
    fn default_state_is_clean() {
        let state = ConnectionState::default();
        assert!(state.is_clean_for(CONFIGURED_DB));
    }

    #[test]
    fn blocking_in_flight_marks_state_not_clean() {
        // A connection whose blocking command has not cleanly completed must never
        // be classified clean, so release discards it instead of returning it to
        // idle with a possibly-armed server-side waiter.
        let state = ConnectionState {
            blocking_in_flight: true,
            ..Default::default()
        };
        assert!(!state.is_clean_for(CONFIGURED_DB));
    }

    #[test]
    fn blocking_in_flight_is_independent_of_other_dirty_flags() {
        // The blocking flag taints on its own, and the other tracked mutations
        // taint on their own — neither masks the other. A connection dirty only
        // via db_selected (blocking flag clear) is not-clean, and a connection
        // dirty only via the blocking flag (db at baseline) is not-clean too.
        let db_only = ConnectionState {
            db_selected: CONFIGURED_DB + 1,
            blocking_in_flight: false,
            ..Default::default()
        };
        assert!(!db_only.is_clean_for(CONFIGURED_DB));

        let blocking_only = ConnectionState {
            blocking_in_flight: true,
            ..Default::default()
        };
        assert!(!blocking_only.is_clean_for(CONFIGURED_DB));
    }
}
