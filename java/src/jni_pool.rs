// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for client-instance pooling.
//!
//! Delegates to glide-core::pool for all pool state management.
//! Background client creation produces entries in the JNI_HANDLE_TABLE
//! so that commands flow through the existing Java command dispatch path.

use crate::iam_token_callback::{JavaIamTokenCallback, make_iam_provider_callback};
use crate::jni_client::{get_handle_table, get_runtime};
use glide_core::pool::{self, ClientPool, POOL_RUNNING, PoolConfig};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JObject};
use jni::sys::{jint, jlong};
use std::sync::atomic::Ordering;
use std::time::Duration;

/// Maps pool_id → CredentialsProvider for pools created with a custom credential provider.
static JNI_POOL_CREDENTIAL_PROVIDERS: std::sync::OnceLock<
    dashmap::DashMap<u64, glide_core::iam::CredentialsProvider>,
> = std::sync::OnceLock::new();

fn get_jni_pool_credential_providers()
-> &'static dashmap::DashMap<u64, glide_core::iam::CredentialsProvider> {
    JNI_POOL_CREDENTIAL_PROVIDERS.get_or_init(dashmap::DashMap::new)
}

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
    mut env: JNIEnv,
    _class: JClass,
    max_size: jint,
    min_idle: jint,
    idle_timeout_ms: jlong,
    request_timeout_ms: jlong,
    abandon_timeout_ms: jlong,
    connection_request_bytes: JByteArray,
    iam_credentials_provider: JObject,
) -> jlong {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    // If an IAM credentials provider is provided, create a reusable CredentialsProvider Arc
    // and store it keyed by pool_id so create_pool_client can look it up.
    let credentials_provider: Option<glide_core::iam::CredentialsProvider> =
        if !iam_credentials_provider.is_null() {
            match crate::jni_client::JVM.get().cloned() {
                Some(jvm) => {
                    match JavaIamTokenCallback::new(&mut env, jvm, &iam_credentials_provider) {
                        Some(cb) => Some(make_iam_provider_callback(cb)),
                        None => return -2,
                    }
                }
                None => {
                    log::error!(
                        "IAM credentials provider supplied for pool but JVM is not initialised."
                    );
                    return -2;
                }
            }
        } else {
            None
        };

    let config = PoolConfig {
        max_size: max_size as u32,
        min_idle: min_idle as u32,
        idle_timeout: Duration::from_millis(idle_timeout_ms as u64),
        request_timeout: Duration::from_millis(request_timeout_ms as u64),
        test_on_borrow: false,
        connection_request: bytes.clone(),
        is_async: true,
        configured_database_id: {
            use protobuf::Message as _;
            glide_core::connection_request::ConnectionRequest::parse_from_bytes(&bytes)
                .ok()
                .map(|req| req.database_id)
                .unwrap_or(0)
        },
        abandon_timeout: Duration::from_millis(abandon_timeout_ms as u64),
    };

    let pool = match ClientPool::new(config) {
        Ok(p) => p,
        Err(_) => return -1,
    };

    let pool_id = pool::register_pool(pool);

    // Store credential provider for this pool (for background warmup clients)
    if let Some(provider) = credentials_provider {
        get_jni_pool_credential_providers().insert(pool_id as u64, provider);
    }

    // Start abandon monitor
    let runtime = get_runtime();
    pool::start_abandon_monitor(pool_id, runtime.handle());

    // Spawn min_idle background client creation
    if min_idle > 0 {
        let pool_arc = pool::get_pool(pool_id).unwrap();
        for _ in 0..(min_idle as u32) {
            let pool_clone = pool_arc.clone();
            let conn_bytes = bytes.clone();
            let runtime = get_runtime();
            let cp = get_jni_pool_credential_providers()
                .get(&(pool_id as u64))
                .map(|e| e.value().clone());
            runtime.spawn(async move {
                match create_pool_client(&conn_bytes, cp).await {
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
                        // pool_client_map is populated only when the client is actually
                        // acquired (borrowed), not here at idle-creation time.
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
            // Clean up any clients discarded by the abandon monitor
            let discarded = pool.drain_discarded_ids();
            for cid in discarded {
                get_handle_table().remove(&cid);
                glide_core::scope::unregister_client(cid);
                get_pool_client_map().remove(&cid);
                glide_core::pool::unregister_blocking_flag(cid);
            }

            let result = pool.try_acquire();
            if result >= 0 {
                // Register in CLIENT_TO_POOL and pool_client_map so activity refresh
                // fires at command dispatch time for this borrowed client.
                glide_core::pool::register_pool_client(pool_id as u64, result as u64);
                get_pool_client_map().insert(result as u64, pool_id as u64);
            }
            if result < 0 && pool.should_create() {
                pool.total_count.fetch_add(1, Ordering::AcqRel);
                let pool_clone = pool_arc.clone();
                let bytes = pool.config.connection_request.clone();
                drop(pool);
                let runtime = get_runtime();
                let cp = get_jni_pool_credential_providers()
                    .get(&(pool_id as u64))
                    .map(|e| e.value().clone());
                runtime.spawn(async move {
                    match create_pool_client(&bytes, cp).await {
                        Ok(client) => {
                            let mut pool = pool_clone.lock().await;
                            if pool.state.load(Ordering::Acquire) != POOL_RUNNING {
                                pool.total_count.fetch_sub(1, Ordering::AcqRel);
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

    let runtime = get_runtime();
    // Do NOT call unregister_blocking_flag here: the registry entry must live for
    // the entire lifetime the client_id exists in the pool (from creation until
    // permanent discard). Removing it on a normal release (return-to-idle) would
    // delete the entry so the next acquire of the recycled client has no registry
    // entry and cannot set the flag. Cleanup happens only on permanent discard:
    // in glidePoolDestroy, in the discard loop inside glidePoolTryAcquire, and in
    // release_client_async's discard (failed reset) path.
    runtime.spawn(pool::release_client_async(pool_arc, client_id as u64));
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
    // Clean up credential provider for this pool
    get_jni_pool_credential_providers().remove(&(pool_id as u64));
    let handle_table = get_handle_table();
    let runtime = get_runtime();
    runtime.spawn(async move {
        let mut pool = pool_arc.lock().await;
        // Clean up JNI handle table entries for all pooled clients
        // (includes discarded clients from the abandon monitor)
        let discarded = pool.drain_discarded_ids();
        for entry in pool.idle.iter() {
            handle_table.remove(&entry.client_id);
            glide_core::scope::unregister_client(entry.client_id);
            get_pool_client_map().remove(&entry.client_id);
            glide_core::pool::unregister_blocking_flag(entry.client_id);
        }
        for entry in pool.in_use.iter() {
            handle_table.remove(entry.key());
            glide_core::scope::unregister_client(*entry.key());
            get_pool_client_map().remove(entry.key());
            glide_core::pool::unregister_blocking_flag(*entry.key());
        }
        for cid in discarded {
            handle_table.remove(&cid);
            glide_core::scope::unregister_client(cid);
            get_pool_client_map().remove(&cid);
            glide_core::pool::unregister_blocking_flag(cid);
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

    let (idle, active, total) = {
        // Use blocking_lock() instead of try_lock(): the metrics call is not on the
        // hot path, and using try_lock() returns (0,0,0) when the lock is contended
        // (e.g. during a heavy test load), causing getActiveCount() to spuriously
        // return 0 even when clients are borrowed. A brief blocking wait gives
        // accurate metrics without introducing a deadlock risk, since this function
        // runs on a JNI thread (not on the Tokio runtime's async executor).
        let pool = pool_arc.blocking_lock();
        (
            pool.idle_count() as i32,
            pool.active_count() as i32,
            pool.total_count.load(Ordering::Acquire) as i32,
        )
    };

    let result = match env.new_int_array(3) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    if env
        .set_int_array_region(&result, 0, &[idle, active, total])
        .is_err()
    {
        return std::ptr::null_mut();
    }
    result.into_raw()
}

// ═══════════════════════════════════════════════════════════════════════════════

/// Create a GlideClient from connection request bytes.
async fn create_pool_client(
    bytes: &[u8],
    credentials_provider: Option<glide_core::iam::CredentialsProvider>,
) -> Result<glide_core::client::Client, String> {
    use protobuf::Message;
    let proto = glide_core::connection_request::ConnectionRequest::parse_from_bytes(bytes)
        .map_err(|e| format!("Protobuf parse error: {}", e))?;
    let mut req = glide_core::client::ConnectionRequest::from(proto);
    // Inject credential provider if one was provided
    if let Some(provider) = credentials_provider
        && let Some(auth_info) = req.authentication_info.as_mut()
        && let Some(iam_config) = auth_info.iam_config.as_mut()
    {
        iam_config.credentials_provider = Some(provider);
    }
    glide_core::client::Client::new(req, None)
        .await
        .map_err(|e| format!("Client creation failed: {}", e))
}
