// ═══════════════════════════════════════════════════════════════════════════════
// CLIENT-INSTANCE POOL FFI
//
// These functions expose glide-core's ClientPool to all language bindings.
// The pool manages GlideClient lifecycle (creation, LIFO reuse, bounded size).
// Language bindings call these via their FFI mechanism (CFFI, Ruby FFI, JNI, CGO).
// ═══════════════════════════════════════════════════════════════════════════════

use super::*;
use glide_core::fork_slot::ForkSlot;
use glide_core::pool::{self, ClientPool, ClientState, PoolConfig, PoolState, PooledClient};
use glide_core::scope;

/// Drop an adapter [`Arc`] whose owning reference `create_pool_client` handed
/// out as a raw pointer. Must be called before any early return that occurs after
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
        // SAFETY: $ptr was produced by Arc::into_raw in create_pool_client.
        // Reconstructing and dropping it here is the only safe way to release
        // the allocation.
        unsafe {
            drop(std::sync::Arc::from_raw($ptr as *const ClientAdapter));
        }
    };
}

/// The watchdog's error for a scoped command that outlived its deadline, with
/// the diagnostic logged through core so the event shape is shared with the
/// acquire path.
fn scope_watchdog_timeout(
    command: &'static str,
    configured_timeout: std::time::Duration,
    actual_elapsed: std::time::Duration,
    p99: Option<std::time::Duration>,
) -> RedisError {
    scope::log_scope_watchdog_timeout(command, configured_timeout, actual_elapsed, p99);
    std::io::Error::from(std::io::ErrorKind::TimedOut).into()
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
/// A forked child gets a fresh one: the inherited runtime has no worker threads.
static POOL_RUNTIME: ForkSlot<PoolRuntime> = ForkSlot::new();

/// Shuts the runtime down without waiting when dropped, as a runtime built by a
/// losing racer is.
struct PoolRuntime(Option<tokio::runtime::Runtime>);

impl Drop for PoolRuntime {
    fn drop(&mut self) {
        if let Some(runtime) = self.0.take() {
            runtime.shutdown_background();
        }
    }
}

/// Maps client_id → (ClientAdapter raw pointer, PooledClient).
/// When a client is acquired, the entry moves here. On release, it moves back to pool.idle.
static POOL_CLIENTS: ForkSlot<dashmap::DashMap<u64, PoolClientEntry>> = ForkSlot::new();

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
static POOL_ADAPTER_MAP: ForkSlot<dashmap::DashMap<usize, (u64, u64)>> = ForkSlot::new();

fn get_pool_runtime() -> &'static tokio::runtime::Runtime {
    POOL_RUNTIME
        .get_or_init(|| {
            PoolRuntime(Some(
                worker_runtime("glide-pool", Some(2)).expect("Failed to create pool runtime"),
            ))
        })
        .0
        .as_ref()
        .expect("runtime is present until drop")
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

/// [`glide_core::pool::track_scope_command`] for a scope whose parent adapter is
/// pool-borrowed; `None` otherwise.
fn track_scope_command(
    scope_id: u64,
    cmd_name: &str,
    args: &[Vec<u8>],
) -> Option<glide_core::pool::BlockingGuard> {
    let parent = scope::scope_parent_id(scope_id)? as usize;
    let (pool_id, client_id) = *get_pool_adapter_map().get(&parent)?.value();
    glide_core::pool::track_scope_command(pool_id, client_id, cmd_name, args)
}

/// Maps pool_id → ClientType for background client creation.
static POOL_CLIENT_TYPES: ForkSlot<dashmap::DashMap<u64, ClientType>> = ForkSlot::new();

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
        None, // custom credential providers are rejected by glide_pool_create
        client_id,
    )
    .map_err(|err| err.message)?;

    // Extract the Client from the adapter for pool bookkeeping
    let adapter = unsafe {
        Arc::increment_strong_count(adapter_ptr);
        Arc::from_raw(adapter_ptr)
    };
    let client = adapter.core.client.clone();
    // Enable the borrow-time IAM reconcile in send_command for pooled clients.
    client.mark_pool_managed();
    let ptr = adapter_ptr as usize;
    // The pool keeps the reference create_client_internal returned. A binding
    // wrapper takes its own through glide_pool_lease_client, so the pool can
    // free the adapter on discard while a stale wrapper still holds it.
    drop(adapter);

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
            if r.credential_provider_key.is_some() {
                glide_logger::log_error(
                    "pool",
                    "Cannot create pool with a credential_provider_key. Custom IAM credential \
                     providers are not supported for pools.",
                );
                return POOL_ERROR_UNSUPPORTED_CONFIG;
            }
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
            // TODO: support RDMA in pools.
            if r.rdma_config.is_some() {
                glide_logger::log_error(
                    "pool",
                    "Cannot create pool with an RDMA configuration: RDMA is not yet \
                     supported with pools. Use a client outside a pool instead.",
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

    // Reserve the warmup slots up front so should_create() reads the pool as full
    // while these connections are still in flight; see ClientPool::reserve_warmup_slots.
    if min_idle > 0 {
        let pool_arc = pool::get_pool(pool_id).unwrap();
        let reserved = pool_arc.lock().reserve_warmup_slots(min_idle);
        for _ in 0..reserved {
            let pool_clone = pool_arc.clone();
            let bytes = connection_request.clone();
            let ct_clone = ct.clone();
            std::thread::spawn(move || {
                let pre_cid = glide_core::pool::allocate_client_id() as usize;
                match create_pool_client(&bytes, ct_clone, pre_cid) {
                    Ok((adapter_ptr, client)) => {
                        let mut pool = pool_clone.lock();
                        if pool.state != PoolState::Running {
                            // Give the reserved slot back; the pool is gone.
                            pool.release_reservation();
                            drop(pool);
                            // Never stored in get_pool_clients(), so glide_pool_destroy cannot reclaim it.
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
                        // Slot already reserved above — do NOT increment total_count.
                        pool.idle.push_back(entry);
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
                    }
                    Err(e) => {
                        glide_logger::log_error_lazy!(
                            "pool",
                            format!("Background client creation failed: {}", e)
                        );
                        // Return the slot this warmup task reserved but never filled.
                        pool_clone.lock().release_reservation();
                    }
                }
            });
        }
    }

    pool_id as i64
}

/// Take the adapters of clients core has discarded out of the binding maps.
/// Call under the pool lock; drop the returned references after releasing it,
/// since dropping the last one shuts down the adapter's runtimes.
fn take_discarded_adapters(pool: &mut ClientPool) -> Vec<usize> {
    let mut adapters = Vec::new();
    for cid in pool.drain_discarded_ids() {
        if let Some((_, entry)) = get_pool_clients().remove(&cid) {
            get_pool_adapter_map().remove(&entry.adapter_ptr);
            glide_core::scope::unregister_client(entry.adapter_ptr as u64);
            adapters.push(entry.adapter_ptr);
        }
    }
    adapters
}

/// Drop the pool's references to adapters from [`take_discarded_adapters`].
fn release_pool_adapters(adapters: Vec<usize>) {
    for adapter_ptr in adapters {
        // SAFETY: the pool's reference, from Arc::into_raw in create_client_internal.
        unsafe { drop(Arc::from_raw(adapter_ptr as *const ClientAdapter)) };
    }
}

/// Non-blocking acquire. Returns the borrow's lease id >= 0 (see
/// [`glide_pool_lease_client`]), -1 if closed, -3 if exhausted, -2 if invalid pool.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_try_acquire(pool_id: u64) -> i64 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -2,
    };

    let discarded = take_discarded_adapters(&mut pool_arc.lock());
    release_pool_adapters(discarded);

    let mut pool = pool_arc.lock();
    let result = pool.try_acquire_lease();

    // Record OTel metrics for pool hit/miss
    if result >= 0 {
        let _ = GlideOpenTelemetry::record_pool_hit();
    } else {
        let _ = GlideOpenTelemetry::record_pool_miss();
    }

    if result < 0 && pool.should_create() {
        // Trigger background creation
        pool.total_count += 1;
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
                    let mut pool = pool_clone.lock();
                    if pool.state != PoolState::Running {
                        pool.release_reservation();
                        drop(pool);
                        // Never stored in get_pool_clients(), so glide_pool_destroy cannot reclaim it.
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
                }
                Err(e) => {
                    glide_logger::log_error_lazy!(
                        "pool",
                        format!("Background creation failed: {}", e)
                    );
                    pool_clone.lock().release_reservation();
                }
            }
        });
    }
    result
}

/// Blocking acquire with timeout. Waits on a condvar until a client becomes
/// available or the timeout expires.
///
/// Returns the borrow's lease id >= 0 on success, -1 on timeout, -2 on invalid pool.
/// This eliminates the polling loop in language bindings — single FFI call
/// instead of N retries.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_acquire_blocking(pool_id: u64, timeout_ms: u64) -> i64 {
    let pool_arc = match pool::get_pool(pool_id) {
        Some(arc) => arc,
        None => return -2,
    };

    let deadline = std::time::Instant::now() + std::time::Duration::from_millis(timeout_ms);
    let notify = pool_arc.lock().release_notify.clone();

    loop {
        let discarded = take_discarded_adapters(&mut pool_arc.lock());
        release_pool_adapters(discarded);
        let result = {
            let mut pool = pool_arc.lock();
            let r = pool.try_acquire_lease();
            if r >= 0 {
                let _ = GlideOpenTelemetry::record_pool_hit();
            }
            // Trigger background creation if needed
            if r < 0 && pool.should_create() {
                pool.total_count += 1;
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
                            let mut p = pool_clone.lock();
                            if p.state != PoolState::Running {
                                p.release_reservation();
                                drop(p);
                                // Never stored in get_pool_clients(), so glide_pool_destroy cannot reclaim it.
                                drop_orphaned_adapter!(adapter_ptr);
                                return;
                            }
                            // Use pre_cid (allocated before lock) to match POOL_ADAPTER_MAP entry;
                            // p.next_id() would generate a different ID, breaking the adapter lookup.
                            let cid = pre_cid as u64;
                            let flag = std::sync::Arc::new(std::sync::atomic::AtomicU32::new(0));
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
                        }
                        Err(_) => pool_clone.lock().release_reservation(),
                    }
                });
            }
            r
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

/// Release a borrow back to the pool. The lease ends before this returns; the
/// connection-state reset is a server round-trip, so it runs on the pool
/// runtime and the client rejoins idle when that finishes. A lease whose
/// borrow already ended is ignored.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_release(pool_id: u64, lease_id: u64) -> i32 {
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
    if let Some(release) = pool::release_lease(pool_arc, lease_id) {
        rt.spawn(release);
    }
    0
}

/// Destroy a pool.
#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_destroy(pool_id: u64) -> i32 {
    let pool_arc = match pool::unregister_pool(pool_id) {
        Some(arc) => arc,
        None => return -1,
    };

    let mut pool = pool_arc.lock();
    // The adapter map records each client's owning pool, so it reaches every
    // client of this pool: idle, borrowed, discarded but not yet drained, and
    // one whose release is mid-reset (in none of the pool's collections).
    let owned: Vec<(usize, u64)> = get_pool_adapter_map()
        .iter()
        .filter(|e| e.value().0 == pool_id)
        .map(|e| (*e.key(), e.value().1))
        .collect();
    pool.drain_discarded_ids();
    let mut adapters = Vec::new();
    for (adapter_ptr, cid) in owned {
        glide_core::pool::unregister_blocking_flag(cid);
        get_pool_adapter_map().remove(&adapter_ptr);
        glide_core::scope::unregister_client(adapter_ptr as u64);
        if get_pool_clients().remove(&cid).is_some() {
            adapters.push(adapter_ptr);
        }
    }
    ClientPool::destroy(pool);
    release_pool_adapters(adapters);
    // Clean up stored ClientType for this pool
    get_pool_client_types().remove(&pool_id);
    0
}

/// The `ClientAdapter` of a current borrow, for `command()` dispatch, or null
/// once that borrow has ended. Writes the pooled client's id (its pipe
/// client id) to `client_id_out`.
///
/// The pointer carries its own reference, so the adapter outlives a pool
/// discard while the binding still holds it; give it back once with
/// [`glide_pool_release_client_ref`].
///
/// # Safety
/// `client_id_out` must be null or point to a writable `u64`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_pool_lease_client(
    pool_id: u64,
    lease_id: u64,
    client_id_out: *mut u64,
) -> *const c_void {
    let Some(pool_arc) = pool::get_pool(pool_id) else {
        return std::ptr::null();
    };
    // Under the pool lock, so a drain cannot free the adapter before the increment.
    let pool = pool_arc.lock();
    let Some(client_id) = pool.client_for_lease(lease_id) else {
        return std::ptr::null();
    };
    let Some(adapter_ptr) = get_pool_clients().get(&client_id).map(|e| e.adapter_ptr) else {
        return std::ptr::null();
    };
    // SAFETY: the pool holds a reference until the drain, which needs this lock.
    unsafe { Arc::increment_strong_count(adapter_ptr as *const ClientAdapter) };
    drop(pool);
    if !client_id_out.is_null() {
        unsafe { *client_id_out = client_id };
    }
    adapter_ptr as *const c_void
}

/// Give back a reference taken by [`glide_pool_lease_client`].
///
/// # Safety
/// `adapter_ptr` must come from `glide_pool_lease_client` and be given back once.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_pool_release_client_ref(adapter_ptr: *const c_void) {
    if !adapter_ptr.is_null() {
        unsafe { drop(Arc::from_raw(adapter_ptr as *const ClientAdapter)) };
    }
}

/// Whether the counts in a [`PoolMetrics`] were read.
///
/// Mirrored in the CFFI declarations in
/// python/glide-shared/glide_shared/_glide_ffi.py; a change here must be
/// reflected there.
#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PoolMetricsStatus {
    /// The counts were read under the pool lock.
    Read = 0,
    /// No pool has this id.
    UnknownPool = 1,
}

/// The result of [`glide_pool_metrics`]: the counts and whether they were
/// read, in one value, so a caller cannot take the counts without the status
/// in hand. When `status` is not `Read`, the counts are zero and mean nothing.
///
/// Mirrored in the CFFI declarations beside [`PoolMetricsStatus`].
#[repr(C)]
#[must_use]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PoolMetrics {
    pub status: PoolMetricsStatus,
    pub idle: u32,
    pub active: u32,
    pub total: u32,
}

impl PoolMetrics {
    fn not_read(status: PoolMetricsStatus) -> Self {
        Self {
            status,
            idle: 0,
            active: 0,
            total: 0,
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn glide_pool_metrics(pool_id: u64) -> PoolMetrics {
    match pool::pool_counts(pool_id) {
        Some(counts) => PoolMetrics {
            status: PoolMetricsStatus::Read,
            idle: counts.idle,
            active: counts.active,
            total: counts.total,
        },
        None => PoolMetrics::not_read(PoolMetricsStatus::UnknownPool),
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
    // Marked before spawning so the abandon monitor never sees the gap.
    let blocking_guard = track_scope_command(scope_id, &cmd_name, &args);

    runtime.spawn(async move {
        let _blocking_guard = blocking_guard;
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
                        Ok(()) => Err(scope_watchdog_timeout(
                            glide_core::timeout_watchdog::cmd_name_from_bytes(cmd_name.as_bytes()),
                            timeout_duration,
                            cmd_start.elapsed(),
                            client.latency_tracker().p99(),
                        ))
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

/// `CommandResponse` carrying a scope id, the success value of both acquire entry
/// points. Heap-allocated like the Ok/Nil fast paths; freed by the binding through
/// `free_command_response`.
fn scope_id_response(scope_id: u64) -> *mut CommandResponse {
    Box::into_raw(Box::new(CommandResponse {
        response_type: ResponseType::Int,
        int_value: scope_id as i64,
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
    }))
}

/// Acquire a scope from the client's scope pool (async — non-blocking, fires callback).
///
/// Waits inside the core for up to `timeout_ms` for a retryable cause to clear.
/// The success response is an `Int` holding the scope id. The error type is
/// `Timeout` when the deadline passes, `Disconnect` when the parent client is
/// closed, `Unspecified` for a configuration problem the message describes.
///
/// The timeout watchdog is armed one second past `timeout_ms` as a backstop, so
/// a deadline still fires when the runtime is too starved to run the core's
/// timer; the callback then carries a `Timeout` without the core's cause text.
/// Delivering that callback needs one poll of this task, so the backstop helps
/// while any worker is alive and cannot help a fully stalled runtime.
///
/// Returns 0 once the acquire is queued; -2 if the arguments are invalid, in which
/// case no callback fires.
///
/// `routing_slot` is read only when `has_routing_slot` is true; it is then the
/// cluster hash slot the scope's commands must target. When `has_routing_slot` is
/// false the caller supplied no routing key and the slot is ignored (any primary is
/// acceptable), mirroring the `has_timeout`/`has_sample_percentage` optional-field
/// convention elsewhere in this ABI.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
/// `success_callback` and `failure_callback` must be valid function pointers.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_acquire_async(
    client_id: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    has_routing_slot: bool,
    routing_slot: u16,
    timeout_ms: u64,
    request_id: usize,
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
) -> i32 {
    if connection_request_ptr.is_null() || connection_request_len == 0 {
        return -2;
    }
    let conn_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec();

    let runtime = get_pool_runtime();
    let handle = runtime.handle().clone();
    runtime.spawn(async move {
        let result = scope::acquire_scope_with_watchdog(
            client_id,
            conn_bytes,
            &handle,
            has_routing_slot.then_some(routing_slot),
            std::time::Duration::from_millis(timeout_ms),
        )
        .await
        .map_err(RedisError::from);
        match result {
            Ok(scope_id) => unsafe {
                success_callback(request_id, scope_id_response(scope_id));
            },
            Err(err) => {
                let (c_msg, error_type) = to_c_error(err);
                unsafe {
                    failure_callback(request_id, c_msg, error_type);
                    drop(CString::from_raw(c_msg as *mut c_char));
                }
            }
        }
    });

    0
}

/// Acquire a scope and deliver the outcome as a frame on the shared async pipe.
///
/// Same acquire as [`glide_scope_acquire_async`]; only the delivery differs.
/// The Python async client reads every command result from the pipe on its
/// event loop, so an acquire delivered the same way costs no thread while it
/// waits, and the deadline is core's alone. Blocking on an executor thread
/// instead puts a second queue in front of core's: once every worker in the
/// executor is parked in an acquire, the next caller waits for a thread before
/// its deadline even starts. The success frame carries an `Int` response whose
/// value is the scope id, built in an arena so the reader frees it with
/// `free_response_arena`; the error frame carries core's error type and
/// message.
///
/// Returns 0 once the acquire is queued; -2 if the arguments are invalid; -3 if
/// no pipe is registered (`init_async_pipe` has not run), in which case no
/// frame is written.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_acquire_pipe(
    client_id: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    has_routing_slot: bool,
    routing_slot: u16,
    timeout_ms: u64,
    request_id: usize,
    pipe_client_id: u64,
) -> i32 {
    if connection_request_ptr.is_null() || connection_request_len == 0 {
        return -2;
    }
    if get_async_pipe().is_none() {
        return -3;
    }
    let conn_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec();

    let runtime = get_pool_runtime();
    let handle = runtime.handle().clone();
    runtime.spawn(async move {
        let result = scope::acquire_scope_with_watchdog(
            client_id,
            conn_bytes,
            &handle,
            has_routing_slot.then_some(routing_slot),
            std::time::Duration::from_millis(timeout_ms),
        )
        .await
        .map_err(RedisError::from);
        let Some(pipe) = get_async_pipe() else {
            // No reader remains for a frame; give the slot back.
            if let Ok(scope_id) = result {
                scope::release_scope(scope_id, client_id, &handle);
            }
            return;
        };
        let push_error = |err: RedisError| {
            pipe.push_error(
                pipe_client_id,
                request_id,
                errors::error_type(&err),
                errors::error_message(&err),
            )
        };
        match result {
            Ok(scope_id) => {
                match valkey_value_to_arena_response(Value::Int(scope_id as i64), &[]) {
                    Ok((root, arena)) => {
                        pipe.push_success(pipe_client_id, request_id, root as usize, arena as usize)
                    }
                    Err(err) => {
                        scope::release_scope(scope_id, client_id, &handle);
                        push_error(err)
                    }
                }
            }
            Err(err) => push_error(err),
        }
    });

    0
}

/// Acquire a scope from the client's scope pool (synchronous — blocks until result).
///
/// Same semantics as [`glide_scope_acquire_async`], returned as a `CommandResult`
/// whose `response` is an `Int` holding the scope id, or whose `command_error`
/// carries the failure. Used by the Python sync client. Because the calling
/// thread polls the future itself, the watchdog backstop returns here even when
/// every runtime worker is stalled.
///
/// # Safety
/// `connection_request_ptr` must point to `connection_request_len` valid bytes.
/// The returned pointer must be freed by the caller via `free_command_result`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_scope_acquire_blocking(
    client_id: u64,
    connection_request_ptr: *const u8,
    connection_request_len: usize,
    has_routing_slot: bool,
    routing_slot: u16,
    timeout_ms: u64,
) -> *mut CommandResult {
    if connection_request_ptr.is_null() || connection_request_len == 0 {
        return create_error_result_with_custom_error(
            "connection request bytes are required".to_owned(),
            RequestErrorType::Unspecified,
        );
    }
    let conn_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_ptr, connection_request_len) }
            .to_vec();

    let runtime = get_pool_runtime();
    let result = runtime.block_on(scope::acquire_scope_with_watchdog(
        client_id,
        conn_bytes,
        runtime.handle(),
        has_routing_slot.then_some(routing_slot),
        std::time::Duration::from_millis(timeout_ms),
    ));
    match result {
        Ok(scope_id) => Box::into_raw(Box::new(CommandResult {
            response: scope_id_response(scope_id),
            command_error: std::ptr::null_mut(),
            arena: std::ptr::null_mut(),
        })),
        Err(err) => create_error_result_with_redis_error(RedisError::from(err)),
    }
}

/// Release a scope back to the pool. Waits for the pool lock; the scope is
/// back in idle (or discarded) when this returns. A dirty connection's cleanup
/// round-trip still runs on the pool runtime afterwards.
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
    let _blocking_guard = track_scope_command(scope_id, &cmd_name, &args);

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
                    Ok(()) => Err(scope_watchdog_timeout(
                        glide_core::timeout_watchdog::cmd_name_from_bytes(cmd_name.as_bytes()),
                        timeout_duration,
                        cmd_start.elapsed(),
                        parent_client.latency_tracker().p99(),
                    ))
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
mod adapter_ownership_tests {
    use super::*;

    /// The last adapter reference may be released from a task on the pool
    /// runtime (a discard of a client whose pool stopped mid-creation), so the
    /// adapter's own runtimes must be releasable from inside another runtime.
    #[test]
    fn adapter_can_be_dropped_inside_a_runtime() {
        let mut request = connection_request::ConnectionRequest::new();
        let mut addr = connection_request::NodeAddress::new();
        addr.host = "127.0.0.1".into();
        addr.port = 1;
        request.addresses.push(addr);
        request.lazy_connect = true;
        let bytes = request.write_to_bytes().unwrap();

        let (ptr, client) =
            create_pool_client(&bytes, ClientType::SyncClient, 1).expect("lazy client");
        drop(client);
        let adapter = unsafe { Arc::from_raw(ptr as *const ClientAdapter) };
        assert_eq!(Arc::strong_count(&adapter), 1);

        let rt = tokio::runtime::Builder::new_current_thread()
            .build()
            .unwrap();
        rt.block_on(async move { drop(adapter) });
    }
}

#[cfg(test)]
mod pool_metrics_tests {
    use super::*;
    use std::time::Duration;

    /// Unregisters the pool when dropped, so a failing assertion does not leave
    /// it in the global registry.
    struct RegisteredPool(u64);

    impl Drop for RegisteredPool {
        fn drop(&mut self) {
            pool::unregister_pool(self.0);
        }
    }

    fn registered_pool_with_two_reserved_slots() -> RegisteredPool {
        let mut pool = ClientPool::new(PoolConfig {
            max_size: 4,
            min_idle: 0,
            idle_timeout: Duration::from_secs(300),
            request_timeout: Duration::from_millis(250),
            test_on_borrow: false,
            connection_request: vec![],
            is_async: false,
            configured_database_id: 0,
            abandon_timeout: Duration::ZERO,
        })
        .unwrap();
        assert_eq!(pool.reserve_warmup_slots(2), 2);
        RegisteredPool(pool::register_pool(pool))
    }

    /// The pool lock, held on another thread until `release` is called.
    struct HeldLock {
        release: std::sync::mpsc::Sender<()>,
        holder: std::thread::JoinHandle<()>,
    }

    impl HeldLock {
        fn release(self) {
            self.release.send(()).unwrap();
            self.holder.join().unwrap();
        }
    }

    /// Returns once the lock is held.
    fn hold_lock(pool_arc: Arc<pool::PoolMutex>) -> HeldLock {
        let (held_tx, held_rx) = std::sync::mpsc::channel();
        let (release_tx, release_rx) = std::sync::mpsc::channel::<()>();
        let holder = std::thread::spawn(move || {
            let guard = pool_arc.lock();
            held_tx.send(()).unwrap();
            release_rx.recv().unwrap();
            drop(guard);
        });
        held_rx.recv().unwrap();
        HeldLock {
            release: release_tx,
            holder,
        }
    }

    #[test]
    fn uncontended_read_reports_read_with_the_reserved_total() {
        let pool = registered_pool_with_two_reserved_slots();

        let metrics = glide_pool_metrics(pool.0);

        assert_eq!(
            metrics,
            PoolMetrics {
                status: PoolMetricsStatus::Read,
                idle: 0,
                active: 0,
                total: 2,
            }
        );
    }

    #[test]
    fn contended_read_waits_for_the_lock_and_reports_read() {
        let pool = registered_pool_with_two_reserved_slots();
        let held = hold_lock(pool::get_pool(pool.0).unwrap());
        let (reading_tx, reading_rx) = std::sync::mpsc::channel();
        let reader = {
            let pool_id = pool.0;
            std::thread::spawn(move || {
                reading_tx.send(()).unwrap();
                glide_pool_metrics(pool_id)
            })
        };
        reading_rx.recv().unwrap();
        std::thread::sleep(Duration::from_millis(50));
        assert!(
            !reader.is_finished(),
            "the read returned while the lock was held"
        );

        held.release();
        let metrics = reader.join().unwrap();

        assert_eq!(
            metrics,
            PoolMetrics {
                status: PoolMetricsStatus::Read,
                idle: 0,
                active: 0,
                total: 2,
            }
        );
    }

    #[test]
    fn contended_read_on_a_runtime_thread_waits_for_the_lock_and_reports_read() {
        let pool = registered_pool_with_two_reserved_slots();
        let held = hold_lock(pool::get_pool(pool.0).unwrap());
        let (reading_tx, reading_rx) = std::sync::mpsc::channel();
        let reader = {
            let pool_id = pool.0;
            std::thread::spawn(move || {
                let rt = tokio::runtime::Builder::new_current_thread()
                    .build()
                    .unwrap();
                rt.block_on(async {
                    reading_tx.send(()).unwrap();
                    glide_pool_metrics(pool_id)
                })
            })
        };
        reading_rx.recv().unwrap();
        std::thread::sleep(Duration::from_millis(50));
        assert!(
            !reader.is_finished(),
            "the read returned while the lock was held"
        );

        held.release();
        let metrics = reader.join().unwrap();

        assert_eq!(
            metrics,
            PoolMetrics {
                status: PoolMetricsStatus::Read,
                idle: 0,
                active: 0,
                total: 2,
            }
        );
    }

    #[test]
    fn unknown_pool_reports_unknown_pool_with_no_counts() {
        let metrics = glide_pool_metrics(u64::MAX);

        assert_eq!(
            metrics,
            PoolMetrics::not_read(PoolMetricsStatus::UnknownPool)
        );
    }
}

#[cfg(test)]
mod pool_destroy_tests {
    use super::*;
    use std::net::TcpListener;
    use std::time::{Duration, Instant};

    /// A client whose release is mid-reset is in none of the pool's collections,
    /// so destroy must find its scope parent some other way; otherwise scopes
    /// keep dispatching on a parent whose pool is gone.
    #[test]
    fn destroy_unregisters_the_scope_parent_of_a_client_mid_release() {
        // Accepts but never speaks: the release's reset connects, then waits on
        // the handshake until its timeout, which keeps the client mid-release
        // for the length of the test.
        let blackhole = TcpListener::bind("127.0.0.1:0").unwrap();
        let mut request = connection_request::ConnectionRequest::new();
        let mut addr = connection_request::NodeAddress::new();
        addr.host = "127.0.0.1".into();
        addr.port = blackhole.local_addr().unwrap().port() as u32;
        request.addresses.push(addr);
        request.lazy_connect = true;
        request.request_timeout = 10_000;
        let bytes = request.write_to_bytes().unwrap();

        let pool = ClientPool::new(PoolConfig {
            max_size: 1,
            min_idle: 0,
            idle_timeout: Duration::from_secs(300),
            request_timeout: Duration::from_secs(10),
            test_on_borrow: false,
            connection_request: bytes.clone(),
            is_async: false,
            // Not the client's db, so the reset has a SELECT to send.
            configured_database_id: 1,
            abandon_timeout: Duration::ZERO,
        })
        .unwrap();
        let pool_id = pool::register_pool(pool);
        let pool_arc = pool::get_pool(pool_id).unwrap();

        // Seat one client the way the warmup path does.
        let client_id = glide_core::pool::allocate_client_id();
        let (adapter_ptr, client) =
            create_pool_client(&bytes, ClientType::SyncClient, client_id as usize)
                .expect("lazy client");
        {
            let mut pool = pool_arc.lock();
            let flag = Arc::new(std::sync::atomic::AtomicU32::new(0));
            pool.idle.push_back(PooledClient {
                client_id,
                client: client.clone(),
                created_at: Instant::now(),
                last_idle_at: Instant::now(),
                borrowed_at: None,
                state: ClientState::Idle,
                is_blocking: flag.clone(),
            });
            pool.total_count += 1;
            glide_core::pool::register_blocking_flag(client_id, flag);
            get_pool_clients().insert(
                client_id,
                PoolClientEntry {
                    adapter_ptr,
                    client,
                    created_at: Instant::now(),
                },
            );
            get_pool_adapter_map().insert(adapter_ptr, (pool_id, client_id));
        }
        let lease = glide_pool_try_acquire(pool_id);
        assert_eq!(
            pool_arc.lock().client_for_lease(lease as u64),
            Some(client_id)
        );
        assert!(scope::get_parent_client(adapter_ptr as u64).is_some());

        assert_eq!(glide_pool_release(pool_id, lease as u64), 0);
        let deadline = Instant::now() + Duration::from_secs(5);
        while pool_arc.lock().in_use.contains_key(&client_id) {
            assert!(Instant::now() < deadline, "release never took the client");
            std::thread::sleep(Duration::from_millis(5));
        }
        assert!(
            pool_arc.lock().idle.is_empty(),
            "the release finished before destroy, so destroy's id loop would cover the client"
        );

        assert_eq!(glide_pool_destroy(pool_id), 0);

        assert!(
            scope::get_parent_client(adapter_ptr as u64).is_none(),
            "the scope parent of a client mid-release survived destroy"
        );
        assert!(
            get_pool_clients().get(&client_id).is_none(),
            "destroy kept the adapter of a client mid-release"
        );
        drop(blackhole);
    }
}

#[cfg(test)]
mod pool_lease_tests {
    use super::*;
    use std::time::{Duration, Instant};

    /// A pool with one lazily-connecting client seated idle, as warmup seats it.
    fn pool_with_one_client() -> (u64, u64, usize) {
        let mut request = connection_request::ConnectionRequest::new();
        let mut addr = connection_request::NodeAddress::new();
        addr.host = "127.0.0.1".into();
        addr.port = 1;
        request.addresses.push(addr);
        request.lazy_connect = true;
        let bytes = request.write_to_bytes().unwrap();
        let pool_id = pool::register_pool(
            ClientPool::new(PoolConfig {
                max_size: 1,
                min_idle: 0,
                idle_timeout: Duration::from_secs(300),
                request_timeout: Duration::from_secs(5),
                test_on_borrow: false,
                connection_request: bytes.clone(),
                is_async: false,
                configured_database_id: 0,
                abandon_timeout: Duration::ZERO,
            })
            .unwrap(),
        );
        let client_id = glide_core::pool::allocate_client_id();
        let (adapter_ptr, client) =
            create_pool_client(&bytes, ClientType::SyncClient, client_id as usize)
                .expect("lazy client");
        let pool_arc = pool::get_pool(pool_id).unwrap();
        let mut pool = pool_arc.lock();
        let flag = Arc::new(std::sync::atomic::AtomicU32::new(0));
        pool.idle.push_back(PooledClient {
            client_id,
            client: client.clone(),
            created_at: Instant::now(),
            last_idle_at: Instant::now(),
            borrowed_at: None,
            state: ClientState::Idle,
            is_blocking: flag.clone(),
        });
        pool.total_count += 1;
        glide_core::pool::register_blocking_flag(client_id, flag);
        get_pool_clients().insert(
            client_id,
            PoolClientEntry {
                adapter_ptr,
                client,
                created_at: Instant::now(),
            },
        );
        get_pool_adapter_map().insert(adapter_ptr, (pool_id, client_id));
        (pool_id, client_id, adapter_ptr)
    }

    /// A wrapper's reference keeps the adapter alive after the pool discards
    /// the client and frees its own reference, and the lease no longer
    /// resolves. Freeing it while a wrapper still held the raw pointer was a
    /// use-after-free.
    #[test]
    fn wrapper_reference_outlives_a_discard() {
        let (pool_id, client_id, adapter_ptr) = pool_with_one_client();
        let lease = glide_pool_try_acquire(pool_id) as u64;
        let mut leased_id = 0u64;
        let held = unsafe { glide_pool_lease_client(pool_id, lease, &mut leased_id) };
        assert_eq!(held as usize, adapter_ptr);
        assert_eq!(leased_id, client_id);

        {
            let pool_arc = pool::get_pool(pool_id).unwrap();
            let mut pool = pool_arc.lock();
            let entry = pool.take_for_release(client_id).expect("borrowed");
            pool.discard_entry(entry);
        }
        // The next acquire drains the discard and frees the pool's reference.
        assert!(glide_pool_try_acquire(pool_id) < 0);
        assert!(get_pool_clients().get(&client_id).is_none());
        assert!(unsafe { glide_pool_lease_client(pool_id, lease, std::ptr::null_mut()) }.is_null());

        // SAFETY: `held` is the wrapper's reference, still live.
        let adapter = unsafe {
            Arc::increment_strong_count(held as *const ClientAdapter);
            Arc::from_raw(held as *const ClientAdapter)
        };
        assert_eq!(
            Arc::strong_count(&adapter),
            2,
            "only the wrapper and this test hold it"
        );
        let mut client = adapter.core.client.clone();
        let err = adapter
            .runtime
            .block_on(client.send_command(&mut redis::cmd("PING"), None))
            .unwrap_err();
        assert_eq!(err.kind(), redis::ErrorKind::ClientClosed);
        drop(client);
        drop(adapter);

        unsafe { glide_pool_release_client_ref(held) };
        assert_eq!(glide_pool_destroy(pool_id), 0);
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

#[cfg(all(test, unix))]
mod fork_tests {
    use super::*;
    use std::time::Duration;

    fn wait_for_clean_exit(pid: libc::pid_t, what: &str) {
        let mut status = 0;
        assert_eq!(unsafe { libc::waitpid(pid, &mut status, 0) }, pid);
        assert!(
            libc::WIFEXITED(status) && libc::WEXITSTATUS(status) == 0,
            "{what} (wait status {status})"
        );
    }

    /// A forked child inherits the pool runtime without its worker threads, so
    /// a task spawned on it would never run.
    #[test]
    fn child_spawns_work_on_a_live_pool_runtime() {
        let (tx, rx) = std::sync::mpsc::channel();
        get_pool_runtime().spawn(async move { tx.send(()).unwrap() });
        rx.recv_timeout(Duration::from_secs(5)).unwrap();

        let pid = unsafe { libc::fork() };
        assert!(pid >= 0, "fork failed");
        if pid == 0 {
            let (tx, rx) = std::sync::mpsc::channel();
            get_pool_runtime().spawn(async move { tx.send(()).unwrap() });
            // Poll instead of parking: on macOS the parker is a libdispatch
            // semaphore this thread created before the fork, and libdispatch
            // crashes the child if it is used again.
            let deadline = std::time::Instant::now() + Duration::from_secs(5);
            let mut ran = false;
            while std::time::Instant::now() < deadline {
                if rx.try_recv().is_ok() {
                    ran = true;
                    break;
                }
                std::thread::sleep(Duration::from_millis(10));
            }
            unsafe { libc::_exit(if ran { 0 } else { 1 }) };
        }
        wait_for_clean_exit(pid, "pool runtime did not run work in the forked child");
    }

    /// A pool or scope pool the parent registered is unknown in the child, so
    /// every binding gets an error code instead of a hang.
    #[test]
    fn inherited_pool_and_scope_ids_are_unknown_in_a_child() {
        let pool_id = pool::register_pool(
            ClientPool::new(PoolConfig {
                max_size: 2,
                min_idle: 0,
                idle_timeout: Duration::from_secs(300),
                request_timeout: Duration::from_secs(5),
                test_on_borrow: false,
                connection_request: vec![],
                is_async: false,
                configured_database_id: 0,
                abandon_timeout: Duration::ZERO,
            })
            .unwrap(),
        );
        let scope_client_id = 0xF0_0000_u64;
        pool::get_client_scope_pools().insert(
            scope_client_id,
            pool::ScopePoolHandle::new(pool::ScopePool::new(
                Default::default(),
                vec![],
                scope_client_id,
            )),
        );
        assert_eq!(glide_scope_release(1, scope_client_id), 0, "control");
        assert!(matches!(
            glide_pool_metrics(pool_id).status,
            PoolMetricsStatus::Read
        ));

        let pid = unsafe { libc::fork() };
        assert!(pid >= 0, "fork failed");
        if pid == 0 {
            let ok = glide_pool_try_acquire(pool_id) == -2
                && glide_pool_acquire_blocking(pool_id, 100) == -2
                && glide_pool_release(pool_id, 1) == -1
                && glide_pool_destroy(pool_id) == -1
                && !matches!(glide_pool_metrics(pool_id).status, PoolMetricsStatus::Read)
                && glide_scope_release(1, scope_client_id) == -1;
            unsafe { libc::_exit(if ok { 0 } else { 1 }) };
        }
        wait_for_clean_exit(pid, "inherited ids were not rejected in the child");

        // The child's calls must not have touched the parent's registrations.
        assert!(matches!(
            glide_pool_metrics(pool_id).status,
            PoolMetricsStatus::Read
        ));
        assert_eq!(glide_scope_release(1, scope_client_id), 0);
        glide_pool_destroy(pool_id);
        pool::get_client_scope_pools().remove(&scope_client_id);
    }
}
