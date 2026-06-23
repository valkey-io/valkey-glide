// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Isolated Execution (Feature 2) — Core Logic
//!
//! This module contains the language-agnostic core logic for scoped connections:
//! - Command deserialization (wire format)
//! - Slot validation and key extraction
//! - Command execution on a scoped connection (with timeout, decompression, IAM)
//! - Background connection creation
//!
//! Language bindings (Java JNI, Python CFFI, Node N-API, Go CGO) should call
//! these functions rather than duplicating the logic.

use crate::client::Client;
use crate::pool::{
    get_client_scope_pools, get_scope_registry, update_state_for_command, validate_scope_slot,
};
use redis::{Cmd, RedisError, RedisResult, Value};

#[cfg(feature = "proto")]
use crate::pool::{ConnectionState, ScopePool, ScopedConnection, POOL_RUNNING};
#[cfg(feature = "proto")]
use std::sync::atomic::Ordering;
#[cfg(feature = "proto")]
use std::sync::Arc;
#[cfg(feature = "proto")]
use std::time::Instant;
#[cfg(feature = "proto")]
use tokio::sync::Mutex as TokioMutex;

// ═══════════════════════════════════════════════════════════════════════════════
// COMMAND DESERIALIZATION
// ═══════════════════════════════════════════════════════════════════════════════

/// Deserialize a command from the wire format used by language bindings.
///
/// Wire format (little-endian):
///   [4 bytes: cmd_name_len][cmd_name bytes][4 bytes: num_args]
///   [4 bytes: arg1_len][arg1 bytes]...[4 bytes: argN_len][argN bytes]
pub fn deserialize_command(bytes: &[u8]) -> Option<(String, Vec<Vec<u8>>)> {
    if bytes.len() < 4 {
        return None;
    }
    let mut off = 0;

    let cmd_len = u32::from_le_bytes(bytes[off..off + 4].try_into().ok()?) as usize;
    off += 4;
    if off + cmd_len > bytes.len() {
        return None;
    }
    let cmd = String::from_utf8(bytes[off..off + cmd_len].to_vec()).ok()?;
    off += cmd_len;

    if off + 4 > bytes.len() {
        return None;
    }
    let num_args = u32::from_le_bytes(bytes[off..off + 4].try_into().ok()?) as usize;
    off += 4;

    let mut args = Vec::with_capacity(num_args);
    for _ in 0..num_args {
        if off + 4 > bytes.len() {
            return None;
        }
        let len = u32::from_le_bytes(bytes[off..off + 4].try_into().ok()?) as usize;
        off += 4;
        if off + len > bytes.len() {
            return None;
        }
        args.push(bytes[off..off + len].to_vec());
        off += len;
    }

    Some((cmd, args))
}

// ═══════════════════════════════════════════════════════════════════════════════
// KEY EXTRACTION
// ═══════════════════════════════════════════════════════════════════════════════

/// Extract key arguments from a command for slot validation.
///
/// Returns references to the argument bytes that represent keys.
/// Commands with no keys (MULTI, EXEC, PING, etc.) return empty.
pub fn extract_key_args<'a>(cmd_name: &str, args: &[&'a [u8]]) -> Vec<&'a [u8]> {
    match cmd_name.to_uppercase().as_str() {
        // Commands with first arg as key
        "GET" | "SET" | "DEL" | "INCR" | "DECR" | "INCRBY" | "DECRBY"
        | "SETNX" | "SETEX" | "PSETEX" | "GETSET" | "GETDEL" | "GETEX"
        | "APPEND" | "STRLEN" | "TYPE" | "EXISTS" | "EXPIRE" | "EXPIREAT"
        | "TTL" | "PTTL" | "PERSIST" | "DUMP" | "RESTORE"
        | "HGET" | "HSET" | "HDEL" | "HLEN" | "HGETALL" | "HMGET" | "HMSET"
        | "LPUSH" | "RPUSH" | "LPOP" | "RPOP" | "LLEN" | "LRANGE"
        | "SADD" | "SREM" | "SMEMBERS" | "SCARD" | "SISMEMBER"
        | "ZADD" | "ZREM" | "ZRANGE" | "ZCARD" | "ZSCORE"
        | "SUBSCRIBE" | "UNSUBSCRIBE"
        | "BLPOP" | "BRPOP" | "BLMOVE" => {
            if !args.is_empty() {
                vec![args[0]]
            } else {
                vec![]
            }
        }
        // WATCH can have multiple keys — all must be in same slot
        "WATCH" => args.to_vec(),
        // MGET: all args are keys
        "MGET" => args.to_vec(),
        // MSET: keys at even positions (key, value, key, value, ...)
        "MSET" | "MSETNX" => args.iter().step_by(2).copied().collect(),
        // Commands with no keys
        "MULTI" | "EXEC" | "DISCARD" | "UNWATCH" | "PING" | "SELECT"
        | "AUTH" | "CLIENT" | "INFO" | "DBSIZE" | "FLUSHDB" | "FLUSHALL"
        | "RESET" | "QUIT" | "COMMAND" | "CONFIG" | "CLUSTER" | "TIME"
        | "WAIT" | "OBJECT" | "DEBUG" | "SLOWLOG" | "LATENCY" | "MEMORY" => vec![],
        // Default: assume first arg is a key (safe approximation for unknown commands)
        _ => {
            if !args.is_empty() {
                vec![args[0]]
            } else {
                vec![]
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCOPE COMMAND EXECUTION
// ═══════════════════════════════════════════════════════════════════════════════

/// Execute a command on a scoped connection.
///
/// This is the core function that all language bindings should call. It handles:
/// - State tracking (WATCH, MULTI, SELECT, subscriptions, etc.)
/// - Cluster slot validation (cross-slot errors)
/// - Command execution via Client::send_command_on_connection (timeout, decompression, IAM)
/// - Fallback to raw send if no parent client is available
///
/// # Arguments
/// - `scope_id`: The scope identifier (must be currently in-use)
/// - `cmd_name`: The command name (e.g., "GET", "SET", "WATCH")
/// - `args`: Command arguments as byte slices
/// - `client`: Optional reference to the parent Client (for timeout, decompression, IAM)
///
/// # Returns
/// `Ok(Value)` on success, `Err(RedisError)` on failure (including cross-slot errors).
pub async fn execute_scope_command(
    scope_id: u64,
    cmd_name: &str,
    args: &[Vec<u8>],
    client: Option<&Client>,
) -> RedisResult<Value> {
    let registry = get_scope_registry();
    let entry = match registry.get(&scope_id) {
        Some(e) => e.connection.clone(),
        None => {
            return Err(RedisError::from((
                redis::ErrorKind::ClientError,
                "Invalid scope_id: scope not found in registry",
            )));
        }
    };

    let mut conn = entry.lock().await;

    // State tracking (for conditional cleanup on release)
    let arg_refs: Vec<&[u8]> = args.iter().map(|a| a.as_slice()).collect();
    update_state_for_command(&mut conn.state, cmd_name, &arg_refs);

    // Cluster mode: validate slot consistency
    let key_args = extract_key_args(cmd_name, &arg_refs);
    if !key_args.is_empty() {
        match validate_scope_slot(conn.pinned_slot, &key_args) {
            Ok(new_slot) => {
                conn.pinned_slot = new_slot;
            }
            Err(e) => {
                return Err(RedisError::from((
                    redis::ErrorKind::CrossSlot,
                    "CROSSSLOT",
                    e,
                )));
            }
        }
    }

    // Build redis command
    let mut cmd = Cmd::new();
    cmd.arg(cmd_name.as_bytes());
    for arg in args {
        cmd.arg(arg.as_slice());
    }

    // Execute via Client (gets timeout, decompression, IAM refresh) or raw fallback
    match client {
        Some(c) => c.send_command_on_connection(&cmd, &mut conn.connection).await,
        None => conn.connection.send_packed_command(&cmd).await,
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND CONNECTION CREATION
// ═══════════════════════════════════════════════════════════════════════════════

/// Create a new scope connection in the background and add it to the pool.
///
/// This function resolves the target address (cluster-aware via the parent Client,
/// or falling back to the seed node for standalone mode) and opens a new
/// MultiplexedConnection.
///
/// # Arguments
/// - `pool`: Arc to the scope pool (locked async)
/// - `client`: Optional reference to the parent Client (for cluster slot resolution)
/// - `connection_request_bytes`: Serialized protobuf ConnectionRequest
#[cfg(feature = "proto")]
pub async fn create_scope_connection(
    pool: Arc<TokioMutex<ScopePool>>,
    client: Option<&Client>,
    connection_request_bytes: &[u8],
) {
    use protobuf::Message as _;

    let proto = match crate::connection_request::ConnectionRequest::parse_from_bytes(
        connection_request_bytes,
    ) {
        Ok(p) => p,
        Err(_) => return,
    };
    let use_tls = proto.tls_mode.value() != 0;
    let scheme = if use_tls { "rediss" } else { "redis" };

    // Determine target address:
    // - Cluster mode: use Client::address_for_slot() to get a primary node address
    // - Standalone mode: fall back to the seed node address from the config
    let url = {
        let cluster_addr = match client {
            Some(c) => c.address_for_slot(0).await,
            None => None,
        };

        if let Some(addr) = cluster_addr {
            // address_for_slot returns "host:port"
            format!("{}://{}", scheme, addr)
        } else {
            // Standalone mode or cluster lookup failed — use seed node
            let addr = match proto.addresses.first() {
                Some(a) => a,
                None => return,
            };
            let port = if addr.port == 0 { 6379 } else { addr.port as u16 };
            format!("{}://{}:{}", scheme, &addr.host, port)
        }
    };

    let redis_client = match redis::Client::open(url.as_str()) {
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
        redis_client.get_multiplexed_async_connection(opts),
    )
    .await
    {
        Ok(Ok(c)) => c,
        _ => return,
    };

    let mut pool_guard = pool.lock().await;
    if pool_guard.state.load(Ordering::Acquire) != POOL_RUNNING {
        pool_guard.total_count.fetch_sub(1, Ordering::AcqRel);
        return;
    }
    let scope_id = pool_guard.next_id();
    let entry = ScopedConnection {
        scope_id,
        connection: conn,
        created_at: Instant::now(),
        last_idle_at: Instant::now(),
        borrowed_at: None,
        state: ConnectionState::default(),
        pinned_slot: None,
    };
    pool_guard.idle.push_back(entry);
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCOPE ACQUIRE / RELEASE (NON-BLOCKING WRAPPERS)
// ═══════════════════════════════════════════════════════════════════════════════

/// Non-blocking scope acquire. Returns scope_id >= 0, -1 if exhausted, -2 if invalid.
///
/// If the pool is exhausted but below max capacity, spawns background connection creation.
/// Language bindings should call this from their FFI layer, passing the client_id,
/// serialized ConnectionRequest bytes, and a tokio runtime handle for async spawning.
#[cfg(feature = "proto")]
pub fn try_acquire_scope(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
    runtime: &tokio::runtime::Handle,
) -> i64 {
    let scope_pool =
        crate::pool::get_or_create_scope_pool(client_id, connection_request_bytes.clone());
    let registry = get_scope_registry();

    match scope_pool.try_lock() {
        Ok(mut pool) => {
            let result = pool.try_acquire(registry);
            if result >= 0 {
                let _ = telemetrylib::GlideOpenTelemetry::record_scope_acquire();
            }
            if result < 0 && pool.total_count.load(Ordering::Acquire) <= pool.config.max_total {
                // Spawn background connection creation
                let pool_clone = scope_pool.clone();
                let conn_bytes = pool.connection_request_bytes.clone();
                let parent_client_id = pool.parent_client_id;
                runtime.spawn(async move {
                    // Get the parent client for cluster-aware address resolution
                    let client = get_parent_client(parent_client_id).await;
                    create_scope_connection(
                        pool_clone,
                        client.as_ref(),
                        &conn_bytes,
                    )
                    .await;
                });
            }
            result
        }
        Err(_) => -1,
    }
}

/// Release a scope back to the pool. Fire-and-forget, non-blocking.
///
/// Returns 0 on success, -1 if client not found.
pub fn release_scope(scope_id: u64, client_id: u64, runtime: &tokio::runtime::Handle) -> i32 {
    let pools = get_client_scope_pools();
    let scope_pool = match pools.get(&client_id) {
        Some(p) => p.value().clone(),
        None => return -1,
    };
    let registry = get_scope_registry();

    let pool_clone = scope_pool.clone();
    match scope_pool.try_lock() {
        Ok(mut pool) => {
            pool.release(scope_id, registry);
            let _ = telemetrylib::GlideOpenTelemetry::record_scope_release();
            0
        }
        Err(_) => {
            runtime.spawn(async move {
                let mut pool = pool_clone.lock().await;
                pool.release(scope_id, get_scope_registry());
            });
            0
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

/// Get the parent Client for a given client_id.
///
/// This uses the client registry that each language binding populates.
/// The registry is in `glide-core::pool` (via the client_scope_pools map)
/// but the actual Client instances are stored per-binding. This function
/// is a hook point — language bindings should register their clients in a
/// shared registry accessible from glide-core.
///
/// For now, we use the scope pool's parent_client_id to look up from the
/// global CLIENT_REGISTRY that language bindings populate.
#[cfg(feature = "proto")]
async fn get_parent_client(client_id: u64) -> Option<Client> {
    let registry = get_client_registry();
    registry.get(&client_id).map(|e| e.value().clone())
}

/// Global client registry: client_id → Client.
/// Language bindings register their Client instances here so that
/// scope execution can access timeout, decompression, and IAM features.
static CLIENT_REGISTRY: std::sync::OnceLock<dashmap::DashMap<u64, Client>> =
    std::sync::OnceLock::new();

pub fn get_client_registry() -> &'static dashmap::DashMap<u64, Client> {
    CLIENT_REGISTRY.get_or_init(dashmap::DashMap::new)
}

/// Register a Client in the global registry (called by language bindings after creation).
pub fn register_client(client_id: u64, client: Client) {
    get_client_registry().insert(client_id, client);
}

/// Unregister a Client from the global registry (called on client close).
pub fn unregister_client(client_id: u64) {
    get_client_registry().remove(&client_id);
}
