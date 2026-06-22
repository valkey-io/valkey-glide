// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! JNI bridge for isolated execution (Feature 2 scopes).

use crate::jni_client::{complete_callback, get_runtime, JVM};
use glide_core::pool::{
    get_or_create_scope_pool, get_scope_registry, update_state_for_command, POOL_RUNNING,
};
use jni::objects::{JByteArray, JClass};
use jni::sys::{jint, jlong};
use jni::JNIEnv;
use protobuf::Message;
use redis::Cmd;
use std::sync::atomic::Ordering;

/// Acquire a scope from the client's internal connection pool.
/// Returns scope_id >= 0, -1 if exhausted, -2 if invalid.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeTryAcquire(
    env: JNIEnv,
    _class: JClass,
    client_id: jlong,
    connection_request_bytes: JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&connection_request_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let scope_pool = get_or_create_scope_pool(client_id as u64, bytes.clone());
    let registry = get_scope_registry();

    match scope_pool.try_lock() {
        Ok(mut pool) => {
            let result = pool.try_acquire(registry);
            if result >= 0 { let _ = telemetrylib::GlideOpenTelemetry::record_scope_acquire(); }
            if result < 0 && pool.total_count.load(Ordering::Acquire) <= pool.config.max_total {
                // Spawn background scope connection creation
                let pool_clone = scope_pool.clone();
                let conn_bytes = pool.connection_request_bytes.clone();
                let runtime = get_runtime();
                runtime.spawn(async move {
                    // Create a new MultiplexedConnection
                    let proto = match glide_core::connection_request::ConnectionRequest::parse_from_bytes(&conn_bytes) {
                        Ok(p) => p,
                        Err(_) => return,
                    };
                    let addr = match proto.addresses.first() {
                        Some(a) => a,
                        None => return,
                    };
                    let port = if addr.port == 0 { 6379 } else { addr.port as u16 };
                    let use_tls = proto.tls_mode.value() != 0;
                    let scheme = if use_tls { "rediss" } else { "redis" };
                    let url = format!("{}://{}:{}", scheme, &addr.host, port);
                    let client = match redis::Client::open(url.as_str()) {
                        Ok(c) => c,
                        Err(_) => return,
                    };
                    let opts = redis::GlideConnectionOptions {
                        push_sender: None,
                        disconnect_notifier: None,
                        discover_az: false,
                        connection_timeout: Some(std::time::Duration::from_secs(5)),
                        connection_retry_strategy: None,
                        tcp_nodelay: true,
                        pubsub_synchronizer: None,
                        iam_token_provider: None,
                    };
                    let conn = match tokio::time::timeout(
                        std::time::Duration::from_secs(5),
                        client.get_multiplexed_async_connection(opts),
                    ).await {
                        Ok(Ok(c)) => c,
                        _ => return,
                    };

                    let mut pool = pool_clone.lock().await;
                    if pool.state.load(Ordering::Acquire) != glide_core::pool::POOL_RUNNING {
                        pool.total_count.fetch_sub(1, Ordering::AcqRel);
                        return;
                    }
                    let scope_id = pool.next_id();
                    let entry = glide_core::pool::ScopedConnection {
                        scope_id,
                        connection: conn,
                        created_at: std::time::Instant::now(),
                        last_idle_at: std::time::Instant::now(),
                        borrowed_at: None,
                        state: glide_core::pool::ConnectionState::default(),
                        pinned_slot: None,
                    };
                    pool.idle.push_back(entry);
                });
            }
            result
        }
        Err(_) => -1,
    }
}

/// Release a scope back to the pool. Fire-and-forget.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeRelease(
    _env: JNIEnv,
    _class: JClass,
    scope_id: jlong,
    client_id: jlong,
) -> jint {
    let pools = glide_core::pool::get_client_scope_pools();
    let scope_pool = match pools.get(&(client_id as u64)) {
        Some(p) => p.value().clone(),
        None => return -1,
    };
    let registry = get_scope_registry();

    let pool_clone = scope_pool.clone();
    match scope_pool.try_lock() {
        Ok(mut pool) => {
            pool.release(scope_id as u64, registry);
            let _ = telemetrylib::GlideOpenTelemetry::record_scope_release();
            0
        }
        Err(_) => {
            let runtime = get_runtime();
            let sid = scope_id as u64;
            runtime.spawn(async move {
                let mut pool = pool_clone.lock().await;
                pool.release(sid, get_scope_registry());
            });
            0
        }
    }
}

/// Execute a command on a scoped connection.
///
/// Applies timeout enforcement (same as the main client path) but bypasses
/// the multiplexer to target the dedicated scoped connection. Compression and
/// OTel integration require access to the Client's config which would need a
/// larger refactor to Client internals (adding a connection override parameter
/// to execute_command_owned). For standalone mode, compression is applied at
/// the binding layer if configured.
///
/// TODO(production): Refactor Client::execute_command_owned to accept an optional
/// connection override, so scope commands get compression + OTel + circuit breaker
/// automatically without duplicating logic here.
///
/// TODO(cluster): Resolve target node from key hash slot, handle MOVED/ASK.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideScopeResolver_glideScopeExecute(
    env: JNIEnv,
    _class: JClass,
    scope_id: jlong,
    command_bytes: JByteArray,
    callback_id: jlong,
) -> jint {
    let bytes = match env.convert_byte_array(&command_bytes) {
        Ok(b) => b,
        Err(_) => return -2,
    };

    let (cmd_name, args) = match deserialize_command(&bytes) {
        Some(p) => p,
        None => return -2,
    };

    let registry = get_scope_registry();
    let entry = match registry.get(&(scope_id as u64)) {
        Some(e) => e.connection.clone(),
        None => return -1,
    };

    let runtime = get_runtime();
    let jvm = JVM.get().unwrap().clone();

    runtime.spawn(async move {
        let mut conn = entry.lock().await;

        // State tracking (for conditional cleanup on release)
        let arg_refs: Vec<&[u8]> = args.iter().map(|a| a.as_slice()).collect();
        update_state_for_command(&mut conn.state, &cmd_name, &arg_refs);

        // Cluster mode: validate slot consistency
        // Commands like WATCH, GET, SET have keys — validate they target the same slot.
        // Commands like MULTI, EXEC, DISCARD, PING have no keys — always allowed.
        let key_args = extract_key_args(&cmd_name, &arg_refs);
        if !key_args.is_empty() {
            match glide_core::pool::validate_scope_slot(conn.pinned_slot, &key_args) {
                Ok(new_slot) => {
                    conn.pinned_slot = new_slot;
                }
                Err(e) => {
                    let err = redis::RedisError::from((
                        redis::ErrorKind::CrossSlot,
                        "CROSSSLOT",
                        e,
                    ));
                    complete_callback(jvm, callback_id, Err(err), false);
                    return;
                }
            }
        }

        // Build redis command
        let mut cmd = Cmd::new();
        cmd.arg(cmd_name.as_bytes());
        for arg in &args {
            cmd.arg(arg.as_slice());
        }

        // Use Client::send_command_on_connection for timeout + decompression.
        // Get the parent client from handle table using the scope pool's stored client_id.
        let result = {
            let handle_table = crate::jni_client::get_handle_table();
            // Look up the parent client that owns this scope pool
            let pools = glide_core::pool::get_client_scope_pools();
            let parent_id = pools.iter()
                .find(|e| e.value().try_lock().map(|p| p.in_use.contains_key(&(scope_id as u64))).unwrap_or(false))
                .map(|e| *e.key());

            if let Some(pid) = parent_id {
                if let Some(client_entry) = handle_table.get(&pid) {
                    let client = client_entry.value();
                    client.send_command_on_connection(&cmd, &mut conn.connection).await
                } else {
                    conn.connection.send_packed_command(&cmd).await
                }
            } else {
                conn.connection.send_packed_command(&cmd).await
            }
        };

        complete_callback(jvm, callback_id, result, false);
    });

    0
}

fn deserialize_command(bytes: &[u8]) -> Option<(String, Vec<Vec<u8>>)> {
    if bytes.len() < 4 { return None; }
    let mut off = 0;

    let cmd_len = u32::from_le_bytes(bytes[off..off + 4].try_into().ok()?) as usize;
    off += 4;
    if off + cmd_len > bytes.len() { return None; }
    let cmd = String::from_utf8(bytes[off..off + cmd_len].to_vec()).ok()?;
    off += cmd_len;

    if off + 4 > bytes.len() { return None; }
    let num_args = u32::from_le_bytes(bytes[off..off + 4].try_into().ok()?) as usize;
    off += 4;

    let mut args = Vec::with_capacity(num_args);
    for _ in 0..num_args {
        if off + 4 > bytes.len() { return None; }
        let len = u32::from_le_bytes(bytes[off..off + 4].try_into().ok()?) as usize;
        off += 4;
        if off + len > bytes.len() { return None; }
        args.push(bytes[off..off + len].to_vec());
        off += len;
    }

    Some((cmd, args))
}


/// Extract key arguments from a command for slot validation.
/// Returns the args that are keys (for slot computation).
/// Commands with no keys (MULTI, EXEC, PING, etc.) return empty.
fn extract_key_args<'a>(cmd_name: &str, args: &[&'a [u8]]) -> Vec<&'a [u8]> {
    match cmd_name.to_uppercase().as_str() {
        // Commands with keys as first N arguments
        "GET" | "SET" | "DEL" | "INCR" | "DECR" | "INCRBY" | "DECRBY"
        | "SETNX" | "SETEX" | "PSETEX" | "GETSET" | "GETDEL" | "GETEX"
        | "APPEND" | "STRLEN" | "TYPE" | "EXISTS" | "EXPIRE" | "EXPIREAT"
        | "TTL" | "PTTL" | "PERSIST" | "DUMP" | "RESTORE"
        | "HGET" | "HSET" | "HDEL" | "HLEN" | "HGETALL" | "HMGET" | "HMSET"
        | "LPUSH" | "RPUSH" | "LPOP" | "RPOP" | "LLEN" | "LRANGE"
        | "SADD" | "SREM" | "SMEMBERS" | "SCARD" | "SISMEMBER"
        | "ZADD" | "ZREM" | "ZRANGE" | "ZCARD" | "ZSCORE"
        | "SUBSCRIBE" | "UNSUBSCRIBE" => {
            // First arg is the key
            if !args.is_empty() { vec![args[0]] } else { vec![] }
        }
        // WATCH can have multiple keys — all must be in same slot
        "WATCH" => args.to_vec(),
        // MGET / MSET / DEL with multiple keys
        "MGET" => args.to_vec(),
        "MSET" => args.iter().step_by(2).copied().collect(), // keys at even positions
        // Commands with no keys
        "MULTI" | "EXEC" | "DISCARD" | "UNWATCH" | "PING" | "SELECT"
        | "AUTH" | "CLIENT" | "INFO" | "DBSIZE" | "FLUSHDB" | "FLUSHALL" => vec![],
        // Default: assume first arg is a key (safe approximation)
        _ => {
            if !args.is_empty() { vec![args[0]] } else { vec![] }
        }
    }
}
