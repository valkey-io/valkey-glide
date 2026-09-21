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

/// Drop an adapter [`Arc`] that was previously leaked via `mem::forget` inside
/// `create_pool_client`. Must be called before any early return that occurs after
/// `create_pool_client` succeeds but **before** `adapter_ptr` is stored in
/// `get_pool_clients()` — at that point `glide_pool_destroy` cannot find it and
/// it would leak forever.
///
/// # Safety
/// `$ptr` must have been produced by `Arc::into_raw` (as done inside
/// `create_pool_client`). Reconstructing the `Arc` and immediately dropping it
/// is the only correct way to release the allocation.
macro_rules! drop_orphaned_adapter {
    ($ptr:expr) => {
        // SAFETY: $ptr was produced by Arc::into_raw (via mem::forget) in
        // create_pool_client. Reconstructing and dropping it here is the
        // only safe way to release the allocation.
        unsafe {
            drop(std::sync::Arc::from_raw($ptr as *const ClientAdapter));
        }
    };
}

/// Whether the diagnostic timeout watchdog should be armed for a scoped command.
///
/// The watchdog arms at the flat client request timeout and aborts the command
/// when it fires. That is meaningless for a blocking command, which is expected
/// to wait far longer than the request timeout (the authoritative deadline for
/// blocking commands is derived per-command in `send_command_on_connection`).
/// Arming it would wrongly abort a blocking scoped command, so skip it for those
/// and let the command block for as long as its own semantics allow (see #6780).
///
/// Takes the command name and args directly (rather than a `redis::Cmd`) so the
/// hot path avoids allocating a `Cmd` and copying every argument byte — notably a
/// large SET payload — just to answer this yes/no question.
fn should_arm_watchdog(cmd_name: &str, args: &[Vec<u8>]) -> bool {
    !glide_core::client::is_blocking_command_name(cmd_name.as_bytes(), args)
}

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
///
/// Note: FFI-managed pool clients are NOT registered in glide-core's CLIENT_TO_POOL
/// map. Pool membership and activity refresh for FFI clients are tracked via
/// POOL_ADAPTER_MAP (adapter_ptr → (pool_id, client_id)) in this file.
/// The is_pool_client() and refresh_activity_by_client() glide-core APIs therefore
/// do not apply to FFI clients; activity refresh happens directly via
/// refresh_client_activity(pool_id, client_id) at dispatch time.
static POOL_ADAPTER_MAP: std::sync::OnceLock<dashmap::DashMap<usize, (u64, u64)>> =
    std::sync::OnceLock::new();

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

#[allow(dead_code)]
struct PoolClientEntry {
    adapter_ptr: usize, // *const ClientAdapter as usize (for command dispatch)
    client: glide_core::client::Client,
    created_at: std::time::Instant,
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
                glide_logger::log_error(
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
                                // Reconstruct and drop the Arc to avoid a memory leak:
                                // create_pool_client transferred ownership into a raw pointer
                                // via mem::forget; glide_pool_destroy cannot find this orphaned
                                // pointer because it was never stored in get_pool_clients().
                                drop_orphaned_adapter!(adapter_ptr);
                                return;
                            }
                            // Use pre_cid (allocated before lock) to match POOL_ADAPTER_MAP entry;
                            // p.next_id() would generate a different ID, breaking the adapter lookup.
                            let client_id = pre_cid as u64;
                            let flag = std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0));
                            let entry = PooledClient {
                                client_id,
                                client: client.clone(),
                                created_at: std::time::Instant::now(),
                                last_idle_at: std::time::Instant::now(),
                                borrowed_at: None,
                                state: ClientState::Idle,
                                is_blocking: flag.clone(),
                            };
                            pool.idle.push_back(entry);
                            pool.total_count.fetch_add(1, AtomicOrdering::AcqRel);
                            glide_core::pool::register_blocking_flag(client_id, flag);
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
                        glide_logger::log_error_lazy!(
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

/// Reconcile a just-acquired pooled client's IAM auth before it is lent.
///
/// `just_acquired_id` is already in `in_use` (a `try_acquire()` that returned
/// `>= 0`). Runs [`prepare_for_borrow`](glide_core::client::Client::prepare_for_borrow),
/// which re-AUTHs the live connection only when the token rotated while the client
/// sat idle. On failure the client is discarded like the abandon-monitor cleanup
/// and the next idle client is reconciled in turn; when none remain, `miss` is
/// returned so the caller falls through to its create/timeout path.
///
/// The AUTH round-trip is async and MUST run off the pool lock: the guard is held
/// only for synchronous bookkeeping and dropped before `block_on`, mirroring
/// `glide_scope_execute`, so it cannot deadlock with `release_client_async`. Safe
/// because the acquire FFI is always called from a binding thread, never from
/// inside the pool runtime.
fn reconcile_borrowed_client(
    pool_arc: &Arc<tokio::sync::Mutex<ClientPool>>,
    just_acquired_id: i64,
    miss: i64,
) -> i64 {
    let mut client_id = just_acquired_id;
    loop {
        // A `Client` clone shares the live connection and the `last_iam_generation`
        // bookmark, so the re-AUTH and bookmark advance are seen by every handle.
        let Some(mut client) = get_pool_clients()
            .get(&(client_id as u64))
            .map(|e| e.client.clone())
        else {
            // No adapter entry (e.g. destroyed concurrently) — nothing to reconcile.
            return client_id;
        };

        // AUTH round-trip strictly OFF the pool lock.
        if get_pool_runtime()
            .block_on(client.prepare_for_borrow())
            .is_ok()
        {
            return client_id;
        }

        logger_core::log_error_lazy!(
            "pool",
            format!("Discarding pooled client {client_id}: IAM re-auth on borrow failed")
        );

        // Discard this client and try the next idle one. Acquire the pool lock via
        // the runtime (not `try_lock`) so a contended lock still removes the broken
        // client immediately — leaving it in `in_use` would strand it until
        // `abandon_timeout`, which a shorter borrower timeout can outlast. The
        // critical section is synchronous (no await under the guard).
        let next = get_pool_runtime().block_on(async {
            let mut pool = pool_arc.lock().await;
            // Only decrement if this call is the one removing the client: the abandon
            // monitor may have reclaimed it (and already decremented) while the AUTH
            // ran, in which case take_for_release returns None and a second
            // discard_client would underflow total_count.
            if pool.take_for_release(client_id as u64).is_some() {
                pool.discard_client();
            }

            if let Some((_, entry)) = get_pool_clients().remove(&(client_id as u64)) {
                get_pool_adapter_map().remove(&entry.adapter_ptr);
                glide_core::scope::unregister_client(entry.adapter_ptr as u64);
                // Release the adapter Arc kept alive via mem::forget in
                // create_pool_client — drops the broken connection.
                unsafe {
                    drop(Arc::from_raw(entry.adapter_ptr as *const ClientAdapter));
                }
            }

            pool.try_acquire()
        });

        // try_acquire returns -3 for exhaustion (map to the caller's miss) but other
        // negatives (e.g. -1 for a closed pool) are distinct sentinels — pass them
        // through unchanged rather than flattening every negative to miss.
        if next == -3 {
            return miss;
        }
        if next < 0 {
            return next;
        }
        client_id = next;
        // Loop reconciles `next` off-lock.
    }
}

/// Non-blocking acquire. Returns client_id >= 0, -1 if exhausted, -2 if invalid pool.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_try_acquire(pool_id: u64) -> i64 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -2,
    };

    let acquired = match pool_arc.try_lock() {
        Ok(mut pool) => {
            // Clean up any clients discarded by the abandon monitor
            let discarded = pool.drain_discarded_ids();
            for cid in discarded {
                glide_core::pool::unregister_blocking_flag(cid);
                if let Some((_, entry)) = get_pool_clients().remove(&cid) {
                    get_pool_adapter_map().remove(&entry.adapter_ptr);
                    glide_core::scope::unregister_client(entry.adapter_ptr as u64);
                    // Release the adapter Arc that was kept alive via mem::forget
                    // in create_pool_client. This drops the connection properly.
                    unsafe {
                        drop(Arc::from_raw(entry.adapter_ptr as *const ClientAdapter));
                    }
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
                                    // Reconstruct and drop the Arc to avoid a memory leak:
                                    // create_pool_client transferred ownership into a raw pointer
                                    // via mem::forget; glide_pool_destroy cannot find this orphaned
                                    // pointer because it was never stored in get_pool_clients().
                                    drop_orphaned_adapter!(adapter_ptr);
                                    return;
                                }
                                // Use pre_cid (allocated before lock) to match POOL_ADAPTER_MAP entry;
                                // p.next_id() would generate a different ID, breaking the adapter lookup.
                                let client_id = pre_cid as u64;
                                let flag =
                                    std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0));
                                let entry = PooledClient {
                                    client_id,
                                    client: client.clone(),
                                    created_at: std::time::Instant::now(),
                                    last_idle_at: std::time::Instant::now(),
                                    borrowed_at: None,
                                    state: ClientState::Idle,
                                    is_blocking: flag.clone(),
                                };
                                pool.idle.push_back(entry);
                                glide_core::pool::register_blocking_flag(client_id, flag);
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
                            glide_logger::log_error_lazy!(
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
    };

    // Reconcile IAM auth off the pool lock. No-op unless the token rotated.
    if acquired >= 0 {
        reconcile_borrowed_client(&pool_arc, acquired, -3)
    } else {
        acquired
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
                // Clean up any clients discarded by the abandon monitor
                let discarded = pool.drain_discarded_ids();
                for cid in discarded {
                    glide_core::pool::unregister_blocking_flag(cid);
                    if let Some((_, entry)) = get_pool_clients().remove(&cid) {
                        get_pool_adapter_map().remove(&entry.adapter_ptr);
                        glide_core::scope::unregister_client(entry.adapter_ptr as u64);
                        unsafe {
                            drop(Arc::from_raw(entry.adapter_ptr as *const ClientAdapter));
                        }
                    }
                }

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
                                        // Reconstruct and drop the Arc to avoid a memory leak:
                                        // create_pool_client transferred ownership into a raw pointer
                                        // via mem::forget; glide_pool_destroy cannot find this orphaned
                                        // pointer because it was never stored in get_pool_clients().
                                        drop_orphaned_adapter!(adapter_ptr);
                                        return;
                                    }
                                    // Use pre_cid (allocated before lock) to match POOL_ADAPTER_MAP entry;
                                    // p.next_id() would generate a different ID, breaking the adapter lookup.
                                    let cid = pre_cid as u64;
                                    let flag =
                                        std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0));
                                    let entry = PooledClient {
                                        client_id: cid,
                                        client: client.clone(),
                                        created_at: std::time::Instant::now(),
                                        last_idle_at: std::time::Instant::now(),
                                        borrowed_at: None,
                                        state: ClientState::Idle,
                                        is_blocking: flag.clone(),
                                    };
                                    p.idle.push_back(entry);
                                    glide_core::pool::register_blocking_flag(cid, flag);
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
            // Reconcile IAM auth off the pool lock. On failure with no other idle
            // client this returns the -3 miss sentinel, so fall through to the wait
            // loop rather than returning a spurious client_id.
            let reconciled = reconcile_borrowed_client(&pool_arc, result, -3);
            if reconciled >= 0 {
                return reconciled;
            }
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

    // Do NOT call unregister_blocking_flag here: the registry entry must live for
    // the entire lifetime the client_id exists in the pool (from creation until
    // permanent discard). Removing it on a normal release (return-to-idle) would
    // delete the entry so the next acquire of the recycled client has no registry
    // entry and cannot set the flag. Cleanup happens only on permanent discard:
    // in glide_pool_destroy, in the discard loop inside glide_pool_try_acquire, and
    // in release_client_async's discard (failed reset) path.
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

    // Invalidate this pool's scopes before returning. The cleanup below may be
    // deferred to a spawned task when the pool lock is contended, and until it ran
    // a caller could still dispatch on a scope whose pool is already destroyed.
    // The adapter map is keyed independently of the pool lock, so this needs no lock.
    let owned: Vec<usize> = get_pool_adapter_map()
        .iter()
        .filter(|e| e.value().0 == pool_id)
        .map(|e| *e.key())
        .collect();
    for adapter_ptr in owned {
        glide_core::scope::unregister_client(adapter_ptr as u64);
    }

    {
        // try_lock rather than blocking_lock: the abandon monitor may hold the
        // lock briefly during a scan. Using blocking_lock here can deadlock if
        // the monitor's async sleep is pending on the same runtime.
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
                glide_core::pool::unregister_blocking_flag(cid);
                if let Some((_, entry)) = get_pool_clients().remove(&cid) {
                    get_pool_adapter_map().remove(&entry.adapter_ptr);
                    glide_core::scope::unregister_client(entry.adapter_ptr as u64);
                    unsafe {
                        drop(Arc::from_raw(entry.adapter_ptr as *const ClientAdapter));
                    }
                }
            }
            pool.destroy();
        } else {
            // Lock contended — schedule async cleanup so resources are eventually freed.
            let pool_arc_async = pool_arc.clone();
            get_pool_runtime().spawn(async move {
                let mut pool = pool_arc_async.lock().await;
                let discarded = pool.drain_discarded_ids();
                let client_ids: Vec<u64> = pool
                    .idle
                    .iter()
                    .map(|e| e.client_id)
                    .chain(pool.in_use.iter().map(|e| *e.key()))
                    .chain(discarded)
                    .collect();
                for cid in client_ids {
                    glide_core::pool::unregister_blocking_flag(cid);
                    if let Some((_, entry)) = get_pool_clients().remove(&cid) {
                        get_pool_adapter_map().remove(&entry.adapter_ptr);
                        glide_core::scope::unregister_client(entry.adapter_ptr as u64);
                        unsafe {
                            drop(Arc::from_raw(entry.adapter_ptr as *const ClientAdapter));
                        }
                    }
                }
                pool.destroy();
            });
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

    let client = match scope::resolve_scope_parent(scope_id) {
        Some(c) => c,
        None => return -1,
    };

    let runtime = get_pool_runtime();

    runtime.spawn(async move {
        // OTel: create span for scope command
        let span_ptr = if GlideOpenTelemetry::is_initialized() {
            create_otel_span(RequestType::CustomCommand)
        } else {
            0
        };

        // Watchdog: register for timeout diagnostics
        let cmd_start = std::time::Instant::now();
        let timeout_duration = client.get_request_timeout();

        // Skip the watchdog for blocking commands: it would abort them at the flat
        // request timeout. Blocking commands manage their own deadline in the core.
        let arm_watchdog = should_arm_watchdog(&cmd_name, &args);

        // Execute with watchdog race — send_scope_command handles CB, inflight,
        // compression, latency recording internally
        let result = if !arm_watchdog {
            scope::send_scope_command(scope_id, &cmd_name, &mut args, &client).await
        } else {
            let timeout_rx = glide_core::timeout_watchdog::TimeoutWatchdog::global()
                .register(timeout_duration, cmd_start);
            let execute = scope::send_scope_command(scope_id, &cmd_name, &mut args, &client);
            tokio::pin!(execute);
            tokio::select! {
                result = &mut execute => result,
                recv_result = timeout_rx => {
                    match recv_result {
                        Err(_) => execute.await,
                        Ok(()) => {
                            let actual_elapsed = cmd_start.elapsed();
                            let pending = glide_core::timeout_watchdog::pending_count();
                            let p99 = client.latency_tracker().p99();
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
                            glide_logger::log_warn("timeout_watchdog", event.to_string());
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
                                    c_msg.as_ptr(),
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
                    failure_callback(request_id, c_msg.as_ptr(), error_type);
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

    // Spawn min_idle background creation tasks. Each resolves slot 0 through the
    // parent client's current topology first, then reserves a slot against
    // max_total via the target-aware helper (for slot accounting; the marker is
    // never matched, since each task mints its own token — see below), skipping if
    // full or closed. Resolving before reserving means an unresolvable target never
    // holds a slot. Each task carries a unique attempt token, so the min_idle
    // prewarms are distinct dials that do not dedupe against each other or against
    // a concurrent acquire. An unresolvable target skips the connection —
    // expected for a lazily connected cluster client (no slot map until its first
    // command), so logged at debug rather than warn. The guard means a failed or
    // cancelled prewarm always gives its slot back.
    for _ in 0..min_idle {
        let pool_clone = pool.clone();
        let bytes = conn_bytes.clone();
        let cid = client_id;
        runtime.spawn(async move {
            let client = scope::get_parent_client(cid);
            let target = match scope::resolve_scope_target(client.as_ref(), 0).await {
                Ok(target) => target,
                Err(cause) => {
                    glide_logger::log_debug(
                        "glide_scope_prewarm",
                        format!("client {cid}: prewarm skipped, target unresolved: {cause}"),
                    );
                    return;
                }
            };
            // Reserve respecting max_total; skip if full or closed. Unique token per
            // prewarm task so they do not dedupe against each other.
            let token = glide_core::pool::next_scope_attempt_token();
            let reservation = match pool_clone
                .lock()
                .await
                .reserve_slot_for(target.clone(), token)
            {
                Some(reservation) => reservation,
                None => return,
            };
            scope::create_scope_connection(
                pool_clone,
                client.as_ref(),
                &bytes,
                target,
                reservation,
            )
            .await;
        });
    }
}

/// Allocate a unique scope-acquire attempt token.
///
/// A binding calls this once per `acquire()` and passes the returned value as the
/// `attempt_token` argument on every retry poll of [`glide_scope_try_acquire`], so
/// the core dedupes that acquire's retries to a single in-flight creation without
/// serializing distinct concurrent borrowers. The value is opaque and never reused.
#[unsafe(no_mangle)]
pub extern "C" fn glide_scope_next_attempt_token() -> u64 {
    glide_core::pool::next_scope_attempt_token()
}

/// Acquire a scope from the client's internal scope pool.
///
/// Returns scope_id >= 0 on success, -1 if pool exhausted, -2 on error.
///
/// `attempt_token` identifies one logical acquire. The binding generates it once
/// per `acquire()` call (via [`glide_core::pool::next_scope_attempt_token`]) and
/// passes the same value on every retry poll, so the core dedupes a single
/// acquire's retries while letting distinct concurrent borrowers each dial.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_try_acquire(
    client_id: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    routing_slot: u16,
    attempt_token: u64,
) -> i64 {
    let conn_bytes = if connection_request_ptr.is_null() || connection_request_len == 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec()
    };

    let runtime = get_pool_runtime();
    scope::try_acquire_scope(
        client_id,
        conn_bytes,
        runtime.handle(),
        routing_slot,
        attempt_token,
    )
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

    let parent_client = match scope::resolve_scope_parent(scope_id) {
        Some(c) => c,
        None => return std::ptr::null_mut(),
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
    let timeout_duration = parent_client.get_request_timeout();

    // Skip the watchdog for blocking commands: it would abort them at the flat
    // request timeout. Blocking commands manage their own deadline in the core.
    let arm_watchdog = should_arm_watchdog(&cmd_name, &args);

    let result = runtime.block_on(async {
        if !arm_watchdog {
            return scope::send_scope_command(scope_id, &cmd_name, &mut args, &parent_client).await;
        }
        let timeout_rx = glide_core::timeout_watchdog::TimeoutWatchdog::global()
            .register(timeout_duration, cmd_start);
        let execute = scope::send_scope_command(scope_id, &cmd_name, &mut args, &parent_client);
        tokio::pin!(execute);
        tokio::select! {
            result = &mut execute => result,
            recv_result = timeout_rx => {
                match recv_result {
                    Err(_) => execute.await,
                    Ok(()) => {
                        let actual_elapsed = cmd_start.elapsed();
                        let pending = glide_core::timeout_watchdog::pending_count();
                        let p99 = parent_client.latency_tracker().p99();
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
                        glide_logger::log_warn("timeout_watchdog", event.to_string());
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

#[cfg(test)]
mod watchdog_gating_tests {
    use super::should_arm_watchdog;

    fn arms(cmd_name: &str, args: &[&str]) -> bool {
        let args: Vec<Vec<u8>> = args.iter().map(|a| a.as_bytes().to_vec()).collect();
        should_arm_watchdog(cmd_name, &args)
    }

    #[test]
    fn non_blocking_commands_arm_the_watchdog() {
        assert!(arms("GET", &["key"]));
        assert!(arms("SET", &["key", "value"]));
        assert!(arms("LPUSH", &["key", "value"]));
    }

    #[test]
    fn blocking_commands_skip_the_watchdog() {
        // Arming the diagnostic watchdog for these would abort them at the flat
        // request timeout, defeating their blocking semantics — regardless of
        // whether the command's own timeout is bounded or unbounded (#6780).
        assert!(!arms("BLPOP", &["key", "0"]));
        assert!(!arms("BLPOP", &["key", "5"]));
        assert!(!arms("BRPOP", &["key", "0"]));
        assert!(!arms("BLMOVE", &["src", "dst", "LEFT", "RIGHT", "0"]));
        assert!(!arms("BRPOPLPUSH", &["src", "dst", "0"]));
        assert!(!arms("BZPOPMIN", &["key", "0"]));
        assert!(!arms("BZPOPMAX", &["key", "0"]));
        assert!(!arms("BLMPOP", &["0", "1", "k", "LEFT"]));
        assert!(!arms("BZMPOP", &["0", "1", "k", "MIN"]));
        assert!(!arms("WAIT", &["0", "100"]));
        assert!(!arms("WAITAOF", &["1", "0", "0"]));
    }

    #[test]
    fn xread_is_blocking_only_with_the_block_option() {
        // XREAD/XREADGROUP block only when BLOCK is present; a plain XREAD is a
        // normal command and should keep watchdog coverage.
        assert!(!arms("XREAD", &["BLOCK", "0", "STREAMS", "s", "$"]));
        assert!(!arms("XREAD", &["BLOCK", "5000", "STREAMS", "s", "$"]));
        assert!(arms("XREAD", &["COUNT", "10", "STREAMS", "s", "0"]));
        assert!(!arms(
            "XREADGROUP",
            &["GROUP", "g", "c", "BLOCK", "0", "STREAMS", "s", ">"]
        ));
    }
}
