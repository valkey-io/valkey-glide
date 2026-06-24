// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for client-instance pooling (Feature 1).
//!
//! Delegates to glide-core::pool for all pool state management.
//! Background client creation produces entries in the JNI_HANDLE_TABLE
//! so that commands flow through the existing Java command dispatch path.

use crate::jni_client::{get_handle_table, get_runtime};
use glide_core::pool::{self, ClientPool, PoolConfig, POOL_RUNNING};
use jni::objects::{JByteArray, JClass};
use jni::sys::{jint, jlong};
use jni::JNIEnv;
use std::sync::atomic::Ordering;
use std::time::Duration;

/// Create a new pool. Returns pool_id > 0 on success, -1 on invalid config.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolCreate(
    env: JNIEnv,
    _class: JClass,
    max_size: jint,
    min_idle: jint,
    idle_timeout_ms: jlong,
    request_timeout_ms: jlong,
    connection_request_bytes: JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let config = PoolConfig {
        max_size: max_size as u32,
        min_idle: min_idle as u32,
        idle_timeout: Duration::from_millis(idle_timeout_ms as u64),
        request_timeout: Duration::from_millis(request_timeout_ms as u64),
        test_on_borrow: false,
        connection_request: bytes.clone(),
    };

    let pool = match ClientPool::new(config) {
        Ok(p) => p,
        Err(_) => return -1,
    };

    let pool_id = pool::register_pool(pool);

    // Spawn min_idle background client creation
    if min_idle > 0 {
        let pool_arc = pool::get_pool(pool_id).unwrap();
        for _ in 0..(min_idle as u32) {
            let pool_clone = pool_arc.clone();
            let conn_bytes = bytes.clone();
            let runtime = get_runtime();
            runtime.spawn(async move {
                match create_pool_client(&conn_bytes).await {
                    Ok(client) => {
                        let mut pool = pool_clone.lock().await;
                        if pool.state.load(Ordering::Acquire) != POOL_RUNNING {
                            return;
                        }
                        // add_client returns the assigned client_id
                        let client_id = pool.add_client(client.clone());
                        // Also register in JNI handle table for command dispatch
                        get_handle_table().insert(client_id, client.clone());
                        // Register in scope client registry too
                        glide_core::scope::register_client(client_id, client);
                    }
                    Err(e) => log::error!("Pool background client creation failed: {}", e),
                }
            });
        }
    }

    pool_id as jlong
}

/// Non-blocking acquire. Returns client_id >= 0, -1 if exhausted, -2 if invalid.
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

    match pool_arc.try_lock() {
        Ok(mut pool) => {
            let result = pool.try_acquire();
            if result < 0 && pool.should_create() {
                pool.total_count.fetch_add(1, Ordering::AcqRel);
                let pool_clone = pool_arc.clone();
                let bytes = pool.config.connection_request.clone();
                drop(pool);
                let runtime = get_runtime();
                runtime.spawn(async move {
                    match create_pool_client(&bytes).await {
                        Ok(client) => {
                            let mut pool = pool_clone.lock().await;
                            if pool.state.load(Ordering::Acquire) != POOL_RUNNING {
                                pool.total_count.fetch_sub(1, Ordering::AcqRel);
                                return;
                            }
                            let client_id = pool.add_client_reserved(client.clone());
                            get_handle_table().insert(client_id, client.clone());
                            glide_core::scope::register_client(client_id, client);
                        }
                        Err(e) => {
                            log::error!("Pool background client creation failed: {}", e);
                            let pool = pool_clone.lock().await;
                            pool.total_count.fetch_sub(1, Ordering::AcqRel);
                        }
                    }
                });
            }
            result
        }
        Err(_) => -1,
    }
}

/// Release a client back to the pool.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolRelease(
    _env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
    client_id: jlong,
) -> jint {
    let pool_arc = match pool::get_pool(pool_id as u64) {
        Some(arc) => arc,
        None => return -1,
    };

    let pool_clone = pool_arc.clone();
    match pool_arc.try_lock() {
        Ok(mut pool) => {
            pool.release(client_id as u64);
            0
        }
        Err(_) => {
            let runtime = get_runtime();
            let cid = client_id as u64;
            runtime.spawn(async move {
                let mut pool = pool_clone.lock().await;
                pool.release(cid);
            });
            0
        }
    }
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
    let runtime = get_runtime();
    runtime.spawn(async move {
        let mut pool = pool_arc.lock().await;
        // Clean up JNI handle table entries for all pooled clients
        for entry in pool.idle.iter() {
            handle_table.remove(&entry.client_id);
            glide_core::scope::unregister_client(entry.client_id);
        }
        for entry in pool.in_use.iter() {
            handle_table.remove(entry.key());
            glide_core::scope::unregister_client(*entry.key());
        }
        pool.destroy();
    });
    0
}

/// Query pool metrics. Returns [idle, active, total] as jintArray.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlidePoolResolver_glidePoolMetrics(
    env: JNIEnv,
    _class: JClass,
    pool_id: jlong,
) -> jni::sys::jintArray {
    let pool_arc = match pool::get_pool(pool_id as u64) {
        Some(arc) => arc,
        None => return std::ptr::null_mut(),
    };

    let (idle, active, total) = match pool_arc.try_lock() {
        Ok(pool) => (
            pool.idle_count() as i32,
            pool.active_count() as i32,
            pool.total_count.load(Ordering::Acquire) as i32,
        ),
        Err(_) => (0, 0, 0),
    };

    let result = match env.new_int_array(3) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    if env.set_int_array_region(&result, 0, &[idle, active, total]).is_err() {
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
