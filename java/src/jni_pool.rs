// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for client-instance pooling.
//!
//! Delegates to glide-core::pool for all pool state management.
//! Background client creation produces entries in the JNI_HANDLE_TABLE
//! so that commands flow through the existing Java command dispatch path.

use crate::jni_client::{get_handle_table, get_runtime};
use glide_core::pool::{self, ClientPool, PoolConfig, PoolState};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jint, jlong};
use std::time::Duration;

/// Maps handle_id (== client_id for pool clients) → pool_id.
/// Used by the command dispatch path to detect pool-borrowed clients
/// and mark them as blocking / refresh activity.
static JNI_POOL_CLIENT_MAP: std::sync::OnceLock<dashmap::DashMap<u64, u64>> =
    std::sync::OnceLock::new();

pub(crate) fn get_pool_client_map() -> &'static dashmap::DashMap<u64, u64> {
    JNI_POOL_CLIENT_MAP.get_or_init(dashmap::DashMap::new)
}

/// Create a new pool. Returns pool_id > 0 on success, -1 on invalid config.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolCreate(
    env: JNIEnv,
    _class: JClass,
    max_size: jint,
    min_idle: jint,
    idle_timeout_ms: jlong,
    request_timeout_ms: jlong,
    abandon_timeout_ms: jlong,
    connection_request_bytes: JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    use protobuf::Message as _;
    let request = match glide_core::connection_request::ConnectionRequest::parse_from_bytes(&bytes)
    {
        Ok(request) => request,
        Err(error) => {
            log::error!("Failed to parse pool ConnectionRequest protobuf: {error}");
            return -2;
        }
    };
    if request.credential_provider_key.is_some() {
        log::error!(
            "The credential_provider_key protobuf field is not supported by Java JNI pools; custom IAM credential providers cannot be forwarded to pooled clients."
        );
        return -3;
    }

    let config = PoolConfig {
        max_size: max_size as u32,
        min_idle: min_idle as u32,
        idle_timeout: Duration::from_millis(idle_timeout_ms as u64),
        request_timeout: Duration::from_millis(request_timeout_ms as u64),
        test_on_borrow: false,
        connection_request: bytes.clone(),
        is_async: true,
        configured_database_id: request.database_id,
        abandon_timeout: Duration::from_millis(abandon_timeout_ms as u64),
    };

    let pool = match ClientPool::new(config) {
        Ok(p) => p,
        Err(_) => return -1,
    };

    let pool_id = pool::register_pool(pool);

    // Start abandon monitor
    let runtime = get_runtime();
    pool::start_abandon_monitor(pool_id, runtime.handle());

    // Reserve the warmup slots up front so should_create() reads the pool as full
    // while these connections are still in flight; see ClientPool::reserve_warmup_slots.
    if min_idle > 0 {
        let pool_arc = pool::get_pool(pool_id).unwrap();
        let reserved = pool_arc.lock().reserve_warmup_slots(min_idle as u32);
        for _ in 0..reserved {
            let pool_clone = pool_arc.clone();
            let conn_bytes = bytes.clone();
            runtime.spawn(async move {
                match create_pool_client(&conn_bytes).await {
                    Ok(client) => {
                        let mut pool = pool_clone.lock();
                        if pool.state != PoolState::Running {
                            pool.release_reservation();
                            return;
                        }
                        // Slot already reserved above — add_client_reserved does not
                        // increment total_count.
                        let client_id = pool.add_client_reserved(client.clone());
                        // Also register in JNI handle table for command dispatch
                        get_handle_table().insert(client_id, client.clone());
                        // Register in scope client registry too
                        glide_core::scope::register_client(client_id, client);
                        // pool_client_map is populated only when the client is actually
                        // acquired (borrowed), not here at idle-creation time.
                    }
                    Err(e) => {
                        log::error!("Pool background client creation failed: {}", e);
                        // Return the slot this warmup task reserved but never filled.
                        pool_clone.lock().release_reservation();
                    }
                }
            });
        }
    }

    pool_id as jlong
}

/// Non-blocking acquire. Returns the borrow's lease id >= 0 (see
/// `glidePoolLeaseClient`), -1 if closed, -3 if exhausted, -2 if invalid.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolTryAcquire(
    _env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
) -> jlong {
    let pool_arc = match pool::get_pool(pool_id as u64) {
        Some(arc) => arc,
        None => return -2,
    };

    let mut pool = pool_arc.lock();
    // Reclaim the Java-side handles of clients core has discarded
    let discarded = pool.drain_discarded_ids();
    for cid in discarded {
        get_handle_table().remove(&cid);
        glide_core::scope::unregister_client(cid);
        get_pool_client_map().remove(&cid);
    }

    let result = pool.try_acquire_lease();
    if let Some(client_id) = u64::try_from(result)
        .ok()
        .and_then(|lease| pool.client_for_lease(lease))
    {
        // Register in CLIENT_TO_POOL and pool_client_map so activity refresh
        // fires at command dispatch time for this borrowed client.
        glide_core::pool::register_pool_client(pool_id as u64, client_id);
        get_pool_client_map().insert(client_id, pool_id as u64);
    }
    if result < 0 && pool.should_create() {
        pool.total_count += 1;
        let pool_clone = pool_arc.clone();
        let bytes = pool.config.connection_request.clone();
        drop(pool);
        let runtime = get_runtime();
        runtime.spawn(async move {
            match create_pool_client(&bytes).await {
                Ok(client) => {
                    let mut pool = pool_clone.lock();
                    if pool.state != PoolState::Running {
                        pool.release_reservation();
                        return;
                    }
                    let client_id = pool.add_client_reserved(client.clone());
                    get_handle_table().insert(client_id, client.clone());
                    glide_core::scope::register_client(client_id, client);
                    // pool_client_map is populated only when the client is actually
                    // acquired (borrowed), not here at idle-creation time.
                }
                Err(e) => {
                    log::error!("Pool background client creation failed: {}", e);
                    pool_clone.lock().release_reservation();
                }
            }
        });
    }
    result
}

/// The pooled client id (the JNI handle) a current lease borrows, or -1 once
/// that borrow has ended.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolLeaseClient(
    _env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
    lease_id: jlong,
) -> jlong {
    pool::get_pool(pool_id as u64)
        .and_then(|pool_arc| pool_arc.lock().client_for_lease(lease_id as u64))
        .map_or(-1, |client_id| client_id as jlong)
}

/// Release a borrow back to the pool. A lease whose borrow already ended is ignored.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolRelease(
    _env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
    lease_id: jlong,
) -> jint {
    let pool_arc = match pool::get_pool(pool_id as u64) {
        Some(arc) => arc,
        None => return -1,
    };

    let runtime = get_runtime();
    // Do NOT call unregister_blocking_flag here: the registry entry must live for
    // the entire lifetime the client_id exists in the pool (from creation until
    // permanent discard). Removing it on a normal release (return-to-idle) would
    // delete the entry so the next acquire of the recycled client has no registry
    // entry and cannot set the flag. Cleanup happens only on permanent discard:
    // in glidePoolDestroy, in the discard loop inside glidePoolTryAcquire, and in
    // release_client_async's discard (failed reset) path.
    if let Some(release) = pool::release_lease(pool_arc, lease_id as u64) {
        runtime.spawn(release);
    }
    0
}

/// Destroy a pool.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolDestroy(
    _env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
) -> jint {
    let pool_arc = match pool::unregister_pool(pool_id as u64) {
        Some(arc) => arc,
        None => return -1,
    };
    let handle_table = get_handle_table();
    let mut pool = pool_arc.lock();
    // Every client of this pool: idle, borrowed, discarded, and any ever-borrowed
    // client whose release is mid-reset (in no pool collection, but still in
    // pool_client_map, which keeps an entry until discard or destroy).
    let mut client_ids: std::collections::HashSet<u64> =
        pool.drain_discarded_ids().into_iter().collect();
    client_ids.extend(pool.idle.iter().map(|e| e.client_id));
    client_ids.extend(pool.in_use.keys().copied());
    client_ids.extend(
        get_pool_client_map()
            .iter()
            .filter(|e| *e.value() == pool_id as u64)
            .map(|e| *e.key()),
    );
    for cid in client_ids {
        handle_table.remove(&cid);
        glide_core::scope::unregister_client(cid);
        get_pool_client_map().remove(&cid);
        glide_core::pool::unregister_blocking_flag(cid);
    }
    ClientPool::destroy(pool);
    0
}

/// Query pool metrics. Returns [idle, active, total] as jintArray.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolMetrics(
    env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
) -> jni::sys::jintArray {
    let Some(counts) = pool::pool_counts(pool_id as u64) else {
        return std::ptr::null_mut();
    };

    let result = match env.new_int_array(3) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    if env
        .set_int_array_region(
            &result,
            0,
            &[
                counts.idle as i32,
                counts.active as i32,
                counts.total as i32,
            ],
        )
        .is_err()
    {
        return std::ptr::null_mut();
    }
    result.into_raw()
}

// ═══════════════════════════════════════════════════════════════════════════════

/// Create a GlideClient from connection request bytes.
async fn create_pool_client(bytes: &[u8]) -> Result<glide_core::client::Client, String> {
    use protobuf::Message;
    let proto = glide_core::connection_request::ConnectionRequest::parse_from_bytes(bytes)
        .map_err(|e| format!("Protobuf parse error: {}", e))?;
    let req = glide_core::client::ConnectionRequest::from(proto);
    glide_core::client::Client::new(req, None)
        .await
        .map_err(|e| format!("Client creation failed: {}", e))
}
