// ═══════════════════════════════════════════════════════════════════════════════
// CLIENT-INSTANCE POOL FFI
//
// These functions expose glide-core's ClientPool to all language bindings.
// The pool manages GlideClient lifecycle (creation, LIFO reuse, bounded size).
// Language bindings call these via their FFI mechanism (CFFI, Ruby FFI, JNI, CGO).
// ═══════════════════════════════════════════════════════════════════════════════

use super::*;
use glide_core::pool::{self, ClientPool, ClientState, POOL_RUNNING, PoolConfig, PooledClient};
use glide_core::scope;
use std::sync::atomic::Ordering as AtomicOrdering;

/// Pool creation/acquire error codes
const POOL_ERROR_INVALID_CONFIG: i64 = -1;
#[allow(dead_code)] // used in future pool expansion (documented in FFI contract)
const POOL_ERROR_CREATION_FAILED: i64 = -2;
const POOL_ERROR_UNSUPPORTED_CONFIG: i64 = -3;

/// Shared Tokio runtime for pool background operations (client creation, eviction).
/// All pooled clients share this runtime rather than each getting their own.
static POOL_RUNTIME: std::sync::OnceLock<tokio::runtime::Runtime> = std::sync::OnceLock::new();

/// Maps client_id → (ClientAdapter raw pointer, PooledClient).
/// When a client is acquired, the entry moves here. On release, it moves back to pool.idle.
static POOL_CLIENTS: std::sync::OnceLock<dashmap::DashMap<u64, PoolClientEntry>> =
    std::sync::OnceLock::new();

/// Reverse lookup: adapter_ptr → (pool_id, client_id).
/// Populated at client creation, used by command dispatch to detect pool-borrowed clients
/// and mark them as blocking when executing blocking commands.
static POOL_ADAPTER_MAP: std::sync::OnceLock<dashmap::DashMap<usize, (u64, u64)>> =
    std::sync::OnceLock::new();

#[allow(dead_code)]
struct PoolClientEntry {
    adapter_ptr: usize, // *const ClientAdapter as usize (for command dispatch)
    client: glide_core::client::Client,
    created_at: std::time::Instant,
}

fn get_pool_runtime() -> &'static tokio::runtime::Runtime {
    POOL_RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .worker_threads(2)
            .thread_name("glide-pool")
            .build()
            .expect("Failed to create pool runtime")
    })
}

fn get_pool_clients() -> &'static dashmap::DashMap<u64, PoolClientEntry> {
    POOL_CLIENTS.get_or_init(dashmap::DashMap::new)
}

pub(crate) fn get_pool_adapter_map() -> &'static dashmap::DashMap<usize, (u64, u64)> {
    POOL_ADAPTER_MAP.get_or_init(dashmap::DashMap::new)
}

/// Maps pool_id → ClientType for background client creation.
static POOL_CLIENT_TYPES: std::sync::OnceLock<dashmap::DashMap<u64, ClientType>> =
    std::sync::OnceLock::new();

fn get_pool_client_types() -> &'static dashmap::DashMap<u64, ClientType> {
    POOL_CLIENT_TYPES.get_or_init(dashmap::DashMap::new)
}

/// Create a GlideClient + ClientAdapter for the pool.
/// Runs on a dedicated thread (not inside an existing runtime) to avoid nesting.
/// Create a pool client using the standard create_client_internal path.
/// This ensures full feature parity: pipe integration, cluster support, pubsub, etc.
///
/// For sync pools: creates a SyncClient adapter.
/// For async pools: creates an AsyncClient adapter with callbacks.
fn create_pool_client(
    connection_request_bytes: &[u8],
    client_type: ClientType,
    client_id: usize,
) -> Result<(usize, glide_core::client::Client), String> {
    let adapter_ptr = create_client_internal(
        connection_request_bytes,
        client_type,
        None, // no pubsub callback for pooled clients (managed at pool level)
        None, // no address resolver (uses the one in ConnectionRequest if any)
        client_id,
    )?;

    // Extract the Client from the adapter for pool bookkeeping
    let adapter = unsafe {
        Arc::increment_strong_count(adapter_ptr);
        Arc::from_raw(adapter_ptr)
    };
    let client = adapter.core.client.clone();
    let ptr = adapter_ptr as usize;
    // Don't drop — the Arc is owned by the pool now
    std::mem::forget(adapter);

    Ok((ptr, client))
}

/// Create a new client-instance pool.
///
/// Creates pooled clients of the specified type. All languages use this single
/// entry point — pass the appropriate ClientType:
/// - Python sync/Ruby: `ClientType { tag: SyncClient }`
/// - Go/Java: `ClientType { tag: AsyncClient, success_callback, failure_callback }`
/// - Python async: `ClientType { tag: AsyncClient }` with pipe (no callbacks needed)
///
/// Returns pool_id (positive) on success, -1 on invalid config, -2 on other errors.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
/// `client_type` must be a valid pointer to a `ClientType`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_pool_create(
    max_size: u32,
    min_idle: u32,
    idle_timeout_ms: u64,
    request_timeout_ms: u64,
    abandon_timeout_ms: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    client_type: *const ClientType,
) -> i64 {
    let connection_request = if connection_request_ptr.is_null() || connection_request_len == 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec()
    };

    let ct = if client_type.is_null() {
        ClientType::SyncClient
    } else {
        unsafe { (*client_type).clone() }
    };

    let is_async = !matches!(ct, ClientType::SyncClient);

    // Parse database_id from connection request for state reset on release
    let configured_database_id = {
        use protobuf::Message as _;
        let req = connection_request::ConnectionRequest::parse_from_bytes(&connection_request);
        if let Ok(ref r) = req {
            // Reject pubsub subscriptions in pool config — pool state reset on release
            // sends DISCARD + SELECT to clean connection state, but cannot UNSUBSCRIBE
            // from channels/patterns. A subscribed connection enters a special mode where
            // only (P|S)SUBSCRIBE/(P|S)UNSUBSCRIBE/PING are allowed, making it unusable
            // for the next borrower. Rather than silently breaking, we reject upfront.
            if r.pubsub_subscriptions.is_some() {
                logger_core::log_error(
                    "pool",
                    "Cannot create pool with pubsub subscriptions in client config. \
                     Use the main client's pubsub API instead.",
                );
                return POOL_ERROR_UNSUPPORTED_CONFIG;
            }
        }
        req.ok()
            .and_then(|r| {
                let db = r.database_id;
                if db != 0 { Some(db) } else { None }
            })
            .unwrap_or(0)
    };

    let config = PoolConfig {
        max_size,
        min_idle,
        idle_timeout: std::time::Duration::from_millis(idle_timeout_ms),
        request_timeout: std::time::Duration::from_millis(request_timeout_ms),
        test_on_borrow: false,
        connection_request: connection_request.clone(),
        is_async,
        configured_database_id,
        abandon_timeout: std::time::Duration::from_millis(abandon_timeout_ms),
    };

    let pool = match ClientPool::new(config) {
        Ok(p) => p,
        Err(_) => return POOL_ERROR_INVALID_CONFIG,
    };

    let pool_id = pool::register_pool(pool);

    // Start abandon monitor
    let rt = get_pool_runtime();
    pool::start_abandon_monitor(pool_id, rt.handle());

    // Store the ClientType for background creation
    get_pool_client_types().insert(pool_id, ct.clone());

    // Spawn min_idle background client creation
    if min_idle > 0 {
        let pool_arc = pool::get_pool(pool_id).unwrap();
        for _ in 0..min_idle {
            let pool_clone = pool_arc.clone();
            let bytes = connection_request.clone();
            let ct_clone = ct.clone();
            std::thread::spawn(move || {
                let pre_cid = glide_core::pool::allocate_client_id() as usize;
                match create_pool_client(&bytes, ct_clone, pre_cid) {
                    Ok((adapter_ptr, client)) => {
                        // Use block_on for warmup — this runs during pool creation
                        // (initialization), not during acquire/release contention,
                        // so blocking is safe and guarantees clients are ready.
                        let rt = get_pool_runtime();
                        rt.block_on(async {
                            let mut pool = pool_clone.lock().await;
                            if pool.state.load(AtomicOrdering::Acquire) != POOL_RUNNING {
                                return;
                            }
                            let client_id = pre_cid as u64;
                            let entry = PooledClient {
                                client_id,
                                client: client.clone(),
                                created_at: std::time::Instant::now(),
                                last_idle_at: std::time::Instant::now(),
                                borrowed_at: None,
                                state: ClientState::Idle,
                                is_blocking: std::sync::Arc::new(
                                    std::sync::atomic::AtomicBool::new(false),
                                ),
                            };
                            pool.idle.push_back(entry);
                            pool.total_count.fetch_add(1, AtomicOrdering::AcqRel);
                            // Store adapter mapping
                            get_pool_clients().insert(
                                client_id,
                                PoolClientEntry {
                                    adapter_ptr,
                                    client,
                                    created_at: std::time::Instant::now(),
                                },
                            );
                            get_pool_adapter_map().insert(adapter_ptr, (pool_id, client_id));
                        });
                    }
                    Err(e) => {
                        logger_core::log_error_lazy!(
                            "pool",
                            format!("Background client creation failed: {}", e)
                        );
                    }
                }
            });
        }
    }

    pool_id as i64
}

/// Non-blocking acquire. Returns client_id >= 0, -1 if exhausted, -2 if invalid pool.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_try_acquire(pool_id: u64) -> i64 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -2,
    };

    match pool_arc.try_lock() {
        Ok(mut pool) => {
            // Clean up any clients discarded by the abandon monitor
            let discarded = pool.drain_discarded_ids();
            for cid in discarded {
                if let Some((_, entry)) = get_pool_clients().remove(&cid) {
                    get_pool_adapter_map().remove(&entry.adapter_ptr);
                }
            }

            let result = pool.try_acquire();

            // Record OTel metrics for pool hit/miss
            if result >= 0 {
                let _ = GlideOpenTelemetry::record_pool_hit();
            } else {
                let _ = GlideOpenTelemetry::record_pool_miss();
            }

            if result < 0 && pool.should_create() {
                // Trigger background creation
                pool.total_count.fetch_add(1, AtomicOrdering::AcqRel);
                let pool_clone = pool_arc.clone();
                let bytes = pool.config.connection_request.clone();
                drop(pool);
                std::thread::spawn(move || {
                    let pre_cid = glide_core::pool::allocate_client_id() as usize;
                    let bg_ct = get_pool_client_types()
                        .get(&pool_id)
                        .map(|e| e.value().clone())
                        .unwrap_or(ClientType::SyncClient);
                    match create_pool_client(&bytes, bg_ct, pre_cid) {
                        Ok((adapter_ptr, client)) => {
                            let rt = get_pool_runtime();
                            rt.spawn(async move {
                                let mut pool = pool_clone.lock().await;
                                if pool.state.load(AtomicOrdering::Acquire) != POOL_RUNNING {
                                    pool.total_count.fetch_sub(1, AtomicOrdering::AcqRel);
                                    return;
                                }
                                let client_id = pre_cid as u64;
                                let entry = PooledClient {
                                    client_id,
                                    client: client.clone(),
                                    created_at: std::time::Instant::now(),
                                    last_idle_at: std::time::Instant::now(),
                                    borrowed_at: None,
                                    state: ClientState::Idle,
                                    is_blocking: std::sync::Arc::new(
                                        std::sync::atomic::AtomicBool::new(false),
                                    ),
                                };
                                pool.idle.push_back(entry);
                                get_pool_clients().insert(
                                    client_id,
                                    PoolClientEntry {
                                        adapter_ptr,
                                        client,
                                        created_at: std::time::Instant::now(),
                                    },
                                );
                                get_pool_adapter_map().insert(adapter_ptr, (pool_id, client_id));
                            });
                        }
                        Err(e) => {
                            logger_core::log_error_lazy!(
                                "pool",
                                format!("Background creation failed: {}", e)
                            );
                            let rt = get_pool_runtime();
                            rt.spawn(async move {
                                let pool = pool_clone.lock().await;
                                pool.total_count.fetch_sub(1, AtomicOrdering::AcqRel);
                            });
                        }
                    }
                });
            }
            result
        }
        Err(_) => -1,
    }
}

/// Blocking acquire with timeout. Waits on a condvar until a client becomes
/// available or the timeout expires.
///
/// Returns client_id >= 0 on success, -1 on timeout, -2 on invalid pool.
/// This eliminates the polling loop in language bindings — single FFI call
/// instead of N retries.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_acquire_blocking(pool_id: u64, timeout_ms: u64) -> i64 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -2,
    };

    let deadline = std::time::Instant::now() + std::time::Duration::from_millis(timeout_ms);

    // Get the condvar handle (try_lock is synchronous — no runtime needed)
    let notify = loop {
        match pool_arc.try_lock() {
            Ok(pool) => break pool.release_notify.clone(),
            Err(_) => std::thread::sleep(std::time::Duration::from_millis(1)),
        }
        if std::time::Instant::now() > deadline {
            let _ = GlideOpenTelemetry::record_pool_miss();
            return -1;
        }
    };

    loop {
        // Try to acquire (try_lock is synchronous on TokioMutex — no runtime needed)
        let result = match pool_arc.try_lock() {
            Ok(mut pool) => {
                let r = pool.try_acquire();
                if r >= 0 {
                    let _ = GlideOpenTelemetry::record_pool_hit();
                }
                // Trigger background creation if needed
                if r < 0 && pool.should_create() {
                    pool.total_count.fetch_add(1, AtomicOrdering::AcqRel);
                    let pool_clone = pool_arc.clone();
                    let bytes = pool.config.connection_request.clone();
                    drop(pool);
                    std::thread::spawn(move || {
                        let pre_cid = glide_core::pool::allocate_client_id() as usize;
                        let bg_ct = get_pool_client_types()
                            .get(&pool_id)
                            .map(|e| e.value().clone())
                            .unwrap_or(ClientType::SyncClient);
                        match create_pool_client(&bytes, bg_ct, pre_cid) {
                            Ok((adapter_ptr, client)) => {
                                // Spawn pool insertion as an async task instead of
                                // block_on to avoid starving the pool runtime and
                                // deadlocking with release_client_async.
                                let rt = get_pool_runtime();
                                rt.spawn(async move {
                                    let mut p = pool_clone.lock().await;
                                    if p.state.load(AtomicOrdering::Acquire) != POOL_RUNNING {
                                        p.total_count.fetch_sub(1, AtomicOrdering::AcqRel);
                                        return;
                                    }
                                    let cid = p.next_id();
                                    let entry = PooledClient {
                                        client_id: cid,
                                        client: client.clone(),
                                        created_at: std::time::Instant::now(),
                                        last_idle_at: std::time::Instant::now(),
                                        borrowed_at: None,
                                        state: ClientState::Idle,
                                        is_blocking: std::sync::Arc::new(
                                            std::sync::atomic::AtomicBool::new(false),
                                        ),
                                    };
                                    p.idle.push_back(entry);
                                    get_pool_clients().insert(
                                        cid,
                                        PoolClientEntry {
                                            adapter_ptr,
                                            client,
                                            created_at: std::time::Instant::now(),
                                        },
                                    );
                                    get_pool_adapter_map().insert(adapter_ptr, (pool_id, cid));
                                    // Notify waiters that a new client is available
                                    let (_, cv) = &*p.release_notify;
                                    cv.notify_one();
                                });
                            }
                            Err(_) => {
                                let rt = get_pool_runtime();
                                rt.spawn(async move {
                                    let p = pool_clone.lock().await;
                                    p.total_count.fetch_sub(1, AtomicOrdering::AcqRel);
                                });
                            }
                        }
                    });
                }
                r
            }
            Err(_) => -1,
        };

        if result >= 0 {
            return result;
        }

        // Check timeout
        let remaining = deadline.saturating_duration_since(std::time::Instant::now());
        if remaining.is_zero() {
            let _ = GlideOpenTelemetry::record_pool_miss();
            return -1; // Timeout
        }

        // Wait on condvar until notified or timeout
        let (lock, condvar) = &*notify;
        let guard = lock.lock().unwrap();
        let _ = condvar.wait_timeout(guard, remaining.min(std::time::Duration::from_millis(50)));
        // Loop back to try_acquire
    }
}

/// Release a borrowed client back to the pool. Fire-and-forget.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_release(pool_id: u64, client_id: u64) -> i32 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -1,
    };

    let rt = get_pool_runtime();
    rt.spawn(pool::release_client_async(pool_arc, client_id));
    0
}

/// Destroy a pool.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_destroy(pool_id: u64) -> i32 {
    let pool_arc = match pool::unregister_pool(pool_id) {
        Some(arc) => arc,
        None => return -1,
    };
    {
        // try_lock rather than blocking_lock: the abandon monitor may hold the
        // lock briefly during a scan. Using blocking_lock here can deadlock if
        // the monitor's async sleep is pending on the same runtime. The pool was
        // already unregistered above, so the monitor will exit on its next iteration.
        if let Ok(mut pool) = pool_arc.try_lock() {
            // Clean up POOL_CLIENTS entries for all clients owned by this pool
            // (includes discarded clients that the abandon monitor removed from in_use)
            let discarded = pool.drain_discarded_ids();
            let client_ids: Vec<u64> = pool
                .idle
                .iter()
                .map(|e| e.client_id)
                .chain(pool.in_use.iter().map(|e| *e.key()))
                .chain(discarded)
                .collect();
            for cid in client_ids {
                if let Some((_, entry)) = get_pool_clients().remove(&cid) {
                    get_pool_adapter_map().remove(&entry.adapter_ptr);
                }
            }
            pool.destroy();
        }
    }
    // Clean up stored ClientType for this pool
    get_pool_client_types().remove(&pool_id);
    0
}

/// Get the ClientAdapter pointer for a borrowed client_id.
/// Language bindings pass this to `command()` for dispatch.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_get_client_ptr(client_id: u64) -> *const c_void {
    get_pool_clients()
        .get(&client_id)
        .map(|e| e.adapter_ptr as *const c_void)
        .unwrap_or(std::ptr::null())
}

/// Set the pipe_client_id on a pooled client adapter.
/// Required for async clients (Python async, Node) that use the shared pipe
/// for response delivery. Call this after acquire, before sending commands.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_set_pipe_client_id(client_id: u64, pipe_client_id: u64) -> i32 {
    let adapter_ptr = match get_pool_clients().get(&client_id) {
        Some(e) => e.adapter_ptr,
        None => return -1,
    };

    // Safety: adapter_ptr was created via Arc::into_raw in create_pool_client.
    unsafe {
        Arc::increment_strong_count(adapter_ptr as *const ClientAdapter);
        let adapter = Arc::from_raw(adapter_ptr as *const ClientAdapter);
        adapter
            .pipe_client_id
            .store(pipe_client_id, std::sync::atomic::Ordering::Release);
        std::mem::forget(adapter);
    }
    0
}

/// Query pool metrics. Writes idle/active/total to out pointers.
///
/// # Safety
/// Out pointers must be valid for writing a u32.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_pool_metrics(
    pool_id: u64,
    idle_out: *mut u32,
    active_out: *mut u32,
    total_out: *mut u32,
) -> i32 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -1,
    };
    match pool_arc.try_lock() {
        Ok(pool) => {
            if !idle_out.is_null() {
                unsafe {
                    *idle_out = pool.idle_count();
                }
            }
            if !active_out.is_null() {
                unsafe {
                    *active_out = pool.active_count();
                }
            }
            if !total_out.is_null() {
                unsafe {
                    *total_out = pool.total_count.load(AtomicOrdering::Acquire);
                }
            }
            0
        }
        Err(_) => -1,
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ISOLATED EXECUTION SCOPES — C-ABI FFI
// ═══════════════════════════════════════════════════════════════════════════════

/// Execute a command on a scoped connection (async — non-blocking, fires callback).
///
/// Same wire format as `glide_scope_execute`, but returns immediately and calls
/// `success_callback(request_id, response)` or `failure_callback(request_id, error, type)`
/// when the command completes.
///
/// Suitable for Go/Java where blocking an OS thread is expensive.
///
/// # Safety
/// `command_ptr` must point to `command_len` valid bytes.
/// `success_callback` and `failure_callback` must be valid function pointers.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_execute_async(
    scope_id: u64,
    command_ptr: *const u8,
    command_len: usize,
    request_id: usize,
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
) -> i32 {
    if command_ptr.is_null() || command_len == 0 {
        return -2;
    }

    let bytes = unsafe { std::slice::from_raw_parts(command_ptr, command_len) }.to_vec();

    let (cmd_name, mut args) = match scope::deserialize_command(&bytes) {
        Some(p) => p,
        None => return -2,
    };

    // Verify scope exists
    let registry = glide_core::pool::get_scope_registry();
    if registry.get(&scope_id).is_none() {
        return -1;
    }

    let runtime = get_pool_runtime();

    runtime.spawn(async move {
        // Get the parent client for timeout/decompression/IAM
        let client_registry = scope::get_client_registry();
        let client = {
            let pools = glide_core::pool::get_client_scope_pools();
            let parent_id = pools
                .iter()
                .find(|e| {
                    e.value()
                        .try_lock()
                        .map(|p| p.in_use.contains_key(&scope_id))
                        .unwrap_or(false)
                })
                .map(|e| *e.key());

            parent_id.and_then(|pid| client_registry.get(&pid).map(|e| e.value().clone()))
        };

        // OTel: create span for scope command
        let span_ptr = if GlideOpenTelemetry::is_initialized() {
            create_otel_span(RequestType::CustomCommand)
        } else {
            0
        };

        // Watchdog: register for timeout diagnostics
        let cmd_start = std::time::Instant::now();
        let timeout_duration = client
            .as_ref()
            .map(|c| c.get_request_timeout())
            .unwrap_or(std::time::Duration::from_millis(250));
        let timeout_rx = glide_core::timeout_watchdog::TimeoutWatchdog::global()
            .register(timeout_duration, cmd_start);

        // Execute with watchdog race — send_scope_command handles CB, inflight,
        // compression, latency recording internally
        let result = {
            let execute =
                scope::send_scope_command(scope_id, &cmd_name, &mut args, client.as_ref());
            tokio::pin!(execute);
            tokio::select! {
                result = &mut execute => result,
                recv_result = timeout_rx => {
                    match recv_result {
                        Err(_) => execute.await,
                        Ok(()) => {
                            let actual_elapsed = cmd_start.elapsed();
                            let pending = glide_core::timeout_watchdog::pending_count();
                            let p99 = client.as_ref().and_then(|c| c.latency_tracker().p99());
                            let cause = if pending > 100 {
                                glide_core::timeout_watchdog::TimeoutCause::SystemOverload {
                                    pending_total: pending,
                                }
                            } else {
                                glide_core::timeout_watchdog::TimeoutCause::ServerUnresponsive {
                                    node: "scope".to_owned(),
                                }
                            };
                            let event = glide_core::timeout_watchdog::TimeoutEvent {
                                cause,
                                command: glide_core::timeout_watchdog::cmd_name_from_bytes(
                                    cmd_name.as_bytes(),
                                ),
                                node: "scope".to_owned(),
                                phase: glide_core::timeout_watchdog::CommandPhase::Sent,
                                configured_timeout: timeout_duration,
                                actual_elapsed,
                                pending_commands: pending,
                                recent_p99_latency: p99,
                                rss_bytes: glide_core::timeout_watchdog::get_rss(),
                                suggested_timeout: p99.map(|p| (p * 3).max(timeout_duration)),
                                inflight_at_register: None,
                                inflight_at_timeout: None,
                                retry_count: 0,
                            };
                            logger_core::log_warn("timeout_watchdog", event.to_string());
                            Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into())
                        }
                    }
                }
            }
        };

        // OTel: end span
        if span_ptr != 0 {
            unsafe { drop_otel_span(span_ptr) };
        }

        match result {
            Ok(value) => {
                // Fast path for OK/Nil
                let response_ptr: *const CommandResponse = match &value {
                    Value::Okay => Box::into_raw(Box::new(CommandResponse {
                        response_type: ResponseType::Ok,
                        int_value: 0,
                        float_value: 0.0,
                        bool_value: false,
                        string_value: std::ptr::null_mut(),
                        string_value_len: 0,
                        array_value: std::ptr::null_mut(),
                        array_value_len: 0,
                        map_key: std::ptr::null_mut(),
                        map_value: std::ptr::null_mut(),
                        sets_value: std::ptr::null_mut(),
                        sets_value_len: 0,
                        arena_ptr: std::ptr::null_mut(),
                    })),
                    Value::Nil => std::ptr::null(),
                    _ => match valkey_value_to_arena_response(value, &[]) {
                        Ok((ptr, _arena)) => ptr as *const CommandResponse,
                        Err(err) => {
                            let msg = errors::error_message(&err);
                            let c_msg = CString::new(msg).unwrap_or_default();
                            unsafe {
                                failure_callback(
                                    request_id,
                                    c_msg.into_raw(),
                                    errors::RequestErrorType::Unspecified,
                                );
                            }
                            return;
                        }
                    },
                };
                unsafe {
                    success_callback(request_id, response_ptr);
                }
            }
            Err(err) => {
                let error_type = errors::error_type(&err);
                let msg = errors::error_message(&err);
                let c_msg = CString::new(msg).unwrap_or_default();
                unsafe {
                    failure_callback(request_id, c_msg.into_raw(), error_type);
                }
            }
        }
    });

    0 // success — callback will fire later
}

/// Pre-warm scope connections for a client.
///
/// Creates the scope pool (if not exists) and spawns min_idle background
/// connection creation tasks. Call this after client creation to ensure
/// the first scoped_connection() has a ready connection.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_prewarm(
    client_id: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    min_idle: u32,
) {
    let conn_bytes = if connection_request_ptr.is_null() || connection_request_len == 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec()
    };

    let runtime = get_pool_runtime();

    // Create the scope pool (registers it if not exists)
    let pool = glide_core::pool::get_or_create_scope_pool(client_id, conn_bytes.clone());

    // Spawn min_idle background connection creation tasks on the scope runtime
    for _ in 0..min_idle {
        let pool_clone = pool.clone();
        let bytes = conn_bytes.clone();
        let cid = client_id;
        runtime.spawn(async move {
            let client = scope::get_parent_client(cid).await;
            scope::create_scope_connection(pool_clone, client.as_ref(), &bytes, 0).await;
        });
    }
}

/// Acquire a scope from the client's internal scope pool.
///
/// Returns scope_id >= 0 on success, -1 if pool exhausted, -2 on error.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_try_acquire(
    client_id: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    routing_slot: u16,
) -> i64 {
    let conn_bytes = if connection_request_ptr.is_null() || connection_request_len == 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec()
    };

    let runtime = get_pool_runtime();
    scope::try_acquire_scope(client_id, conn_bytes, runtime.handle(), routing_slot)
}

/// Release a scope back to the pool. Fire-and-forget.
///
/// Returns 0 on success, -1 on error (invalid scope/client).
#[unsafe(no_mangle)]
pub extern "C" fn glide_scope_release(scope_id: u64, client_id: u64) -> i32 {
    let runtime = get_pool_runtime();
    scope::release_scope(scope_id, client_id, runtime.handle())
}

/// Execute a command on a scoped connection (synchronous — blocks until result).
///
/// Used by Python async scopes via `run_in_executor` (blocking FFI call in a thread pool).
/// Go uses `glide_scope_execute_async` instead.
///
/// # Safety
/// `command_ptr` must point to `command_len` valid bytes.
/// The returned pointer must be freed by the caller via `free_command_result`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_execute(
    scope_id: u64,
    command_ptr: *const u8,
    command_len: usize,
) -> *mut CommandResult {
    if command_ptr.is_null() || command_len == 0 {
        return std::ptr::null_mut();
    }

    let bytes = unsafe { std::slice::from_raw_parts(command_ptr, command_len) };

    let (cmd_name, mut args) = match scope::deserialize_command(bytes) {
        Some(p) => p,
        None => return std::ptr::null_mut(),
    };

    // Verify scope exists
    let registry = glide_core::pool::get_scope_registry();
    if registry.get(&scope_id).is_none() {
        return std::ptr::null_mut();
    }

    let client_registry = scope::get_client_registry();
    let parent_client = {
        let pools = glide_core::pool::get_client_scope_pools();
        let parent_id = pools
            .iter()
            .find(|e| {
                e.value()
                    .try_lock()
                    .map(|p| p.in_use.contains_key(&scope_id))
                    .unwrap_or(false)
            })
            .map(|e| *e.key());
        parent_id.and_then(|pid| client_registry.get(&pid).map(|e| e.value().clone()))
    };

    let runtime = get_pool_runtime();

    // OTel: create span for scope command
    let span_ptr = if GlideOpenTelemetry::is_initialized() {
        create_otel_span(RequestType::CustomCommand)
    } else {
        0
    };

    // Watchdog: register for timeout diagnostics
    let cmd_start = std::time::Instant::now();
    let timeout_duration = parent_client
        .as_ref()
        .map(|c| c.get_request_timeout())
        .unwrap_or(std::time::Duration::from_millis(250));
    let timeout_rx = glide_core::timeout_watchdog::TimeoutWatchdog::global()
        .register(timeout_duration, cmd_start);

    let result = runtime.block_on(async {
        let execute =
            scope::send_scope_command(scope_id, &cmd_name, &mut args, parent_client.as_ref());
        tokio::pin!(execute);
        tokio::select! {
            result = &mut execute => result,
            recv_result = timeout_rx => {
                match recv_result {
                    Err(_) => execute.await,
                    Ok(()) => {
                        let actual_elapsed = cmd_start.elapsed();
                        let pending = glide_core::timeout_watchdog::pending_count();
                        let p99 = parent_client.as_ref().and_then(|c| c.latency_tracker().p99());
                        let cause = if pending > 100 {
                            glide_core::timeout_watchdog::TimeoutCause::SystemOverload {
                                pending_total: pending,
                            }
                        } else {
                            glide_core::timeout_watchdog::TimeoutCause::ServerUnresponsive {
                                node: "scope".to_owned(),
                            }
                        };
                        let event = glide_core::timeout_watchdog::TimeoutEvent {
                            cause,
                            command: glide_core::timeout_watchdog::cmd_name_from_bytes(
                                cmd_name.as_bytes(),
                            ),
                            node: "scope".to_owned(),
                            phase: glide_core::timeout_watchdog::CommandPhase::Sent,
                            configured_timeout: timeout_duration,
                            actual_elapsed,
                            pending_commands: pending,
                            recent_p99_latency: p99,
                            rss_bytes: glide_core::timeout_watchdog::get_rss(),
                            suggested_timeout: p99.map(|p| (p * 3).max(timeout_duration)),
                            inflight_at_register: None,
                            inflight_at_timeout: None,
                            retry_count: 0,
                        };
                        logger_core::log_warn("timeout_watchdog", event.to_string());
                        Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into())
                    }
                }
            }
        }
    });

    // OTel: end span
    if span_ptr != 0 {
        unsafe { drop_otel_span(span_ptr) };
    }

    match result {
        Ok(Value::Okay) => {
            let resp = Box::into_raw(Box::new(CommandResponse {
                response_type: ResponseType::Ok,
                int_value: 0,
                float_value: 0.0,
                bool_value: false,
                string_value: std::ptr::null_mut(),
                string_value_len: 0,
                array_value: std::ptr::null_mut(),
                array_value_len: 0,
                map_key: std::ptr::null_mut(),
                map_value: std::ptr::null_mut(),
                sets_value: std::ptr::null_mut(),
                sets_value_len: 0,
                arena_ptr: std::ptr::null_mut(),
            }));
            Box::into_raw(Box::new(CommandResult {
                response: resp,
                command_error: std::ptr::null_mut(),
                arena: std::ptr::null_mut(),
            }))
        }
        Ok(Value::Nil) => {
            let resp = Box::into_raw(Box::new(CommandResponse {
                response_type: ResponseType::Null,
                int_value: 0,
                float_value: 0.0,
                bool_value: false,
                string_value: std::ptr::null_mut(),
                string_value_len: 0,
                array_value: std::ptr::null_mut(),
                array_value_len: 0,
                map_key: std::ptr::null_mut(),
                map_value: std::ptr::null_mut(),
                sets_value: std::ptr::null_mut(),
                sets_value_len: 0,
                arena_ptr: std::ptr::null_mut(),
            }));
            Box::into_raw(Box::new(CommandResult {
                response: resp,
                command_error: std::ptr::null_mut(),
                arena: std::ptr::null_mut(),
            }))
        }
        Ok(value) => match valkey_value_to_arena_response(value, &[]) {
            Ok((response_ptr, arena_ptr)) => Box::into_raw(Box::new(CommandResult {
                response: response_ptr,
                command_error: std::ptr::null_mut(),
                arena: arena_ptr,
            })),
            Err(err) => {
                let msg = format!("{}", err);
                let c_msg = CString::new(msg).unwrap_or_default();
                let error = Box::into_raw(Box::new(CommandError {
                    command_error_type: errors::RequestErrorType::Unspecified,
                    command_error_message: c_msg.into_raw(),
                }));
                Box::into_raw(Box::new(CommandResult {
                    response: std::ptr::null_mut(),
                    command_error: error,
                    arena: std::ptr::null_mut(),
                }))
            }
        },
        Err(err) => {
            let error_type = errors::error_type(&err);
            let msg = errors::error_message(&err);
            let c_msg = CString::new(msg).unwrap_or_default();
            let error = Box::into_raw(Box::new(CommandError {
                command_error_type: error_type,
                command_error_message: c_msg.into_raw(),
            }));
            Box::into_raw(Box::new(CommandResult {
                response: std::ptr::null_mut(),
                command_error: error,
                arena: std::ptr::null_mut(),
            }))
        }
    }
}
