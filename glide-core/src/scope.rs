// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Isolated Execution — Core Logic
//!
//! This module contains the language-agnostic core logic for scoped connections:
//! - Command deserialization (wire format)
//! - Slot validation and key extraction
//! - Command execution on a scoped connection (with timeout, decompression, IAM)
//! - Background connection creation
//!
//! Each client has a per-client `ScopePool` (stored in `CLIENT_SCOPE_POOLS`).
//! The pool maintains idle `ScopedConnection`s, each pinned to a cluster slot's node.
//! On acquire, idle connections are filtered by `target_slot` — only matching connections
//! are reused; mismatched ones are preserved for future acquires. When no match exists,
//! a new connection is created targeting the correct node.
//! Language bindings (Java JNI, Python CFFI, Node N-API, Go CGO) should call
//! these functions rather than duplicating the logic.

use crate::client::Client;
use crate::pool::{
    get_client_scope_pools, get_scope_registry, update_state_for_command, validate_scope_slot,
};
use redis::{Cmd, RedisError, RedisResult, Value};

#[cfg(feature = "proto")]
use crate::pool::{ConnectionState, POOL_RUNNING, ScopePool, ScopedConnection};
#[cfg(feature = "proto")]
use std::sync::Arc;
#[cfg(feature = "proto")]
use std::sync::atomic::Ordering;
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
///   `[4 bytes: cmd_name_len][cmd_name bytes][4 bytes: num_args]`
///   `[4 bytes: arg1_len][arg1 bytes]...[4 bytes: argN_len][argN bytes]`
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
        "GET" | "SET" | "DEL" | "INCR" | "DECR" | "INCRBY" | "DECRBY" | "SETNX" | "SETEX"
        | "PSETEX" | "GETSET" | "GETDEL" | "GETEX" | "APPEND" | "STRLEN" | "TYPE" | "EXISTS"
        | "EXPIRE" | "EXPIREAT" | "TTL" | "PTTL" | "PERSIST" | "DUMP" | "RESTORE" | "HGET"
        | "HSET" | "HDEL" | "HLEN" | "HGETALL" | "HMGET" | "HMSET" | "LPUSH" | "RPUSH" | "LPOP"
        | "RPOP" | "LLEN" | "LRANGE" | "SADD" | "SREM" | "SMEMBERS" | "SCARD" | "SISMEMBER"
        | "ZADD" | "ZREM" | "ZRANGE" | "ZCARD" | "ZSCORE" | "SUBSCRIBE" | "UNSUBSCRIBE"
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
        "MULTI" | "EXEC" | "DISCARD" | "UNWATCH" | "PING" | "SELECT" | "AUTH" | "CLIENT"
        | "INFO" | "DBSIZE" | "FLUSHDB" | "FLUSHALL" | "RESET" | "QUIT" | "COMMAND" | "CONFIG"
        | "CLUSTER" | "TIME" | "WAIT" | "OBJECT" | "DEBUG" | "SLOWLOG" | "LATENCY" | "MEMORY" => {
            vec![]
        }
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

    // Cluster mode: validate slot consistency (skip in standalone — no slots)
    let is_cluster = match client {
        Some(c) => c.is_cluster_mode(),
        None => false,
    };
    if is_cluster {
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
    }

    // Build redis command
    let mut cmd = Cmd::new();
    cmd.arg(cmd_name.as_bytes());
    for arg in args {
        cmd.arg(arg.as_slice());
    }

    // Presume a blocking command's connection unsafe until we know the outcome;
    // set before dispatch so a mid-flight cancellation (future dropped) leaves it
    // poisoned and release discards it. Remember whether a *previous* command on
    // this scope already poisoned the connection, so a later command that
    // completes cleanly can't clear that earlier poison.
    let is_blocking = crate::client::is_blocking_command(&cmd);
    let already_poisoned = conn.state.blocking_in_flight;
    if is_blocking {
        conn.state.blocking_in_flight = true;
    }

    // Execute via Client (gets timeout, decompression, IAM refresh) or raw fallback
    let result = match client {
        Some(c) => {
            c.send_command_on_connection(&cmd, &mut conn.connection)
                .await
        }
        None => conn.connection.send_packed_command(&cmd).await,
    };

    // A timeout, IO error, dropped connection, or protocol desync can leave a
    // server-side waiter armed on the connection (or the connection itself in an
    // unknown state after a failover/CLIENT KILL/restart); a clean protocol error
    // (WRONGTYPE, MOVED/ASK, NOAUTH, or a client-side rejected timeout arg) never
    // reached that state, so the connection stays reusable. Keep the flag set only
    // for the poisoning cases — and never clear a poison a prior command left behind.
    if is_blocking {
        let poisoned = matches!(&result, Err(e) if e.is_timeout()
            || e.is_io_error()
            || e.is_connection_dropped()
            || e.kind() == redis::ErrorKind::ProtocolDesync);
        conn.state.blocking_in_flight = already_poisoned || poisoned;
    }

    result
}

/// Full-featured scope command execution with all cross-cutting concerns.
///
/// This is the single entry point that all language bindings should use.
/// It applies (in order):
/// 1. Circuit breaker check (reject if open)
/// 2. Inflight request reservation (reject if exhausted)
/// 3. Compression on write (if parent has compression enabled)
/// 4. Command execution via `execute_scope_command`
/// 5. Latency recording on the parent's tracker
///
/// The watchdog (timeout diagnostics via `tokio::select!`) is NOT included here
/// because it requires wrapping the future at the call site. Callers should
/// wrap `send_scope_command` in a watchdog select if desired.
///
/// # Errors
/// - `CircuitBreakerOpen` if parent's circuit breaker is open
/// - `ClientError("Reached maximum inflight requests")` if inflight is exhausted
/// - Any error from `execute_scope_command` (timeout, IO, cross-slot, etc.)
pub async fn send_scope_command(
    scope_id: u64,
    cmd_name: &str,
    args: &mut [Vec<u8>],
    client: Option<&Client>,
) -> RedisResult<Value> {
    // 1. Circuit breaker check
    if let Some(c) = client
        && !c.is_circuit_breaker_healthy()
    {
        return Err(RedisError::from((
            redis::ErrorKind::CircuitBreakerOpen,
            "Client circuit breaker is open - core unhealthy",
        )));
    }

    // 2. Inflight request reservation (reject if exhausted)
    let _inflight_tracker = if let Some(c) = client {
        match c.reserve_inflight_request() {
            Some(t) => Some(t),
            None => {
                return Err(RedisError::from((
                    redis::ErrorKind::ClientError,
                    "Reached maximum inflight requests",
                )));
            }
        }
    } else {
        None
    };

    // 3. Compression on write
    if let Some(c) = client
        && let Some(cm) = c.compression_manager()
        && cm.is_enabled()
    {
        // Resolve command type for compression routing
        let effective_type = crate::request_type::RequestType::from_command_name(cmd_name)
            .unwrap_or(crate::request_type::RequestType::CustomCommand);
        let _ = crate::compression::process_command_args_for_compression(
            args,
            effective_type,
            Some(cm.as_ref()),
        );
    }

    // 4. Execute
    let cmd_start = std::time::Instant::now();
    let result = execute_scope_command(scope_id, cmd_name, args, client).await;

    // 5. Record latency
    if let Some(c) = client {
        c.latency_tracker().record(cmd_start.elapsed());
    }

    result
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
    routing_slot: u16,
) {
    use protobuf::Message as _;

    let proto = match crate::connection_request::ConnectionRequest::parse_from_bytes(
        connection_request_bytes,
    ) {
        Ok(p) => p,
        Err(_) => {
            pool.lock().await.total_count.fetch_sub(1, Ordering::AcqRel);
            return;
        }
    };
    if crate::client::validate_effective_lib_name(Some(proto.lib_name.as_ref())).is_err() {
        pool.lock().await.total_count.fetch_sub(1, Ordering::AcqRel);
        return;
    }
    let use_tls = proto.tls_mode.value() != 0;
    let scheme = if use_tls { "rediss" } else { "redis" };

    // Determine target address:
    // - Cluster mode: use Client::address_for_slot() to get a primary node address.
    //   Connects to the node owning the requested routing_slot. In cluster mode,
    //   scoped connections only work for keys that hash to this node's slots. Keys on
    //   other nodes will receive MOVED errors (scopes cannot follow redirects due to
    //   per-connection state).
    // - Standalone mode: fall back to the seed node address from the config
    let url = {
        let cluster_addr = match client {
            Some(c) => c.address_for_slot(routing_slot).await,
            None => None,
        };

        if let Some(addr) = cluster_addr {
            // address_for_slot returns "host:port"
            format!("{}://{}", scheme, addr)
        } else {
            // Standalone mode or cluster lookup failed — use seed node
            let addr = match proto.addresses.first() {
                Some(a) => a,
                None => {
                    pool.lock().await.total_count.fetch_sub(1, Ordering::AcqRel);
                    return;
                }
            };
            let port = if addr.port == 0 {
                6379
            } else {
                addr.port as u16
            };
            format!("{}://{}:{}", scheme, addr.host, port)
        }
    };

    let redis_client = match redis::Client::open(url.as_str()) {
        Ok(c) => c,
        Err(_) => {
            pool.lock().await.total_count.fetch_sub(1, Ordering::AcqRel);
            return;
        }
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
        cert_params_provider: None,
    };
    let mut conn = match tokio::time::timeout(
        std::time::Duration::from_secs(5),
        redis_client.get_multiplexed_async_connection(opts),
    )
    .await
    {
        Ok(Ok(c)) => c,
        _ => {
            pool.lock().await.total_count.fetch_sub(1, Ordering::AcqRel);
            return;
        }
    };

    // Post-connect initialization: AUTH + SELECT to match parent client config.
    // Build a pipeline of init commands (batched, single round-trip).
    let mut init_pipe = redis::Pipeline::new();
    let mut init_count = 0;

    // AUTH: send credentials if configured
    if let Some(ref auth_info) = proto.authentication_info.0 {
        let password = &auth_info.password;
        let username = &auth_info.username;
        if !password.is_empty() {
            if !username.is_empty() {
                init_pipe.cmd("AUTH").arg(&**username).arg(&**password);
            } else {
                init_pipe.cmd("AUTH").arg(&**password);
            }
            init_count += 1;
        }
    }

    // SELECT: use the parent client's current database (runtime state) if available,
    // otherwise fall back to the static config. This ensures scoped connections
    // inherit the parent's current database even after runtime SELECT calls.
    let database_id = client
        .map(|c| c.current_database())
        .unwrap_or(proto.database_id);
    if database_id != 0 {
        init_pipe.cmd("SELECT").arg(database_id.to_string());
        init_count += 1;
    }

    // CLIENT SETNAME: set client name if configured
    let client_name = &proto.client_name;
    if !client_name.is_empty() {
        init_pipe
            .cmd("CLIENT")
            .arg("SETNAME")
            .arg(client_name.as_bytes());
        init_count += 1;
    }

    // Execute init pipeline if any commands are needed
    if init_count > 0 {
        let init_result = tokio::time::timeout(
            std::time::Duration::from_secs(5),
            conn.send_packed_commands(&init_pipe, 0, init_count),
        )
        .await;
        if !matches!(init_result, Ok(Ok(_))) {
            // Init failed — discard this connection
            pool.lock().await.total_count.fetch_sub(1, Ordering::AcqRel);
            return;
        }
    }

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
        state: ConnectionState::with_configured_db(database_id as u8),
        pinned_slot: None,
        target_slot: routing_slot,
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
///
/// `routing_slot` determines which cluster node the scope connects to. In cluster mode,
/// pass the hash slot of the key(s) the scope will operate on. In standalone mode, this
/// parameter is ignored (all slots route to the same node).
#[cfg(feature = "proto")]
pub fn try_acquire_scope(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
    runtime: &tokio::runtime::Handle,
    routing_slot: u16,
) -> i64 {
    // Fast path: check if scope pool exists before cloning bytes
    let scope_pool = {
        let pools = crate::pool::get_client_scope_pools();
        match pools.get(&client_id) {
            Some(existing) => existing.value().clone(),
            None => crate::pool::get_or_create_scope_pool(client_id, connection_request_bytes),
        }
    };
    let registry = get_scope_registry();

    match scope_pool.try_lock() {
        Ok(mut pool) => {
            let result = pool.try_acquire(registry, routing_slot);
            if result >= 0 {
                let _ = telemetrylib::GlideOpenTelemetry::record_scope_acquire();
            }
            if result < 0 && pool.total_count.load(Ordering::Acquire) < pool.config.max_total {
                // Spawn background connection creation
                let pool_clone = scope_pool.clone();
                let conn_bytes = pool.connection_request_bytes.clone();
                let parent_client_id = pool.parent_client_id;
                let target_slot = routing_slot;
                runtime.spawn(async move {
                    let client = get_parent_client(parent_client_id).await;
                    create_scope_connection(pool_clone, client.as_ref(), &conn_bytes, target_slot)
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
            // Enter the runtime context so tokio::spawn inside pool.release()
            // (for dirty-state cleanup) has a reactor available.
            let _guard = runtime.enter();
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
pub async fn get_parent_client(client_id: u64) -> Option<Client> {
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

#[cfg(all(test, feature = "proto"))]
mod tests {
    use std::io::{ErrorKind, Read, Write};
    use std::net::TcpListener;
    use std::sync::Arc;
    use std::sync::atomic::Ordering;
    use std::sync::mpsc::{self, Sender};
    use std::thread::JoinHandle;

    use protobuf::Message as _;
    use tokio::sync::Mutex as TokioMutex;

    use super::create_scope_connection;
    use crate::connection_request::{ConnectionRequest, NodeAddress};
    use crate::pool::{ScopePool, ScopePoolConfig};

    fn request_bytes(lib_name: &str, port: u16) -> Vec<u8> {
        let mut request = ConnectionRequest::new();
        request.addresses.push(NodeAddress {
            host: "127.0.0.1".into(),
            port: port.into(),
            ..Default::default()
        });
        request.lib_name = lib_name.into();
        request.write_to_bytes().expect("serialize scope request")
    }

    fn reserved_pool(request_bytes: Vec<u8>) -> Arc<TokioMutex<ScopePool>> {
        let pool = ScopePool::new(ScopePoolConfig::default(), request_bytes, 1);
        pool.total_count.store(1, Ordering::Release);
        Arc::new(TokioMutex::new(pool))
    }

    fn listening_endpoint() -> TcpListener {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind test listener");
        listener
            .set_nonblocking(true)
            .expect("configure test listener");
        listener
    }

    async fn assert_invalid_name_is_rejected(lib_name: &str) {
        let listener = listening_endpoint();
        let port = listener.local_addr().expect("listener address").port();
        let request_bytes = request_bytes(lib_name, port);
        let pool = reserved_pool(request_bytes.clone());

        create_scope_connection(pool.clone(), None, &request_bytes, 0).await;

        let pool = pool.lock().await;
        assert_eq!(pool.total_count.load(Ordering::Acquire), 0, "{lib_name}");
        assert!(pool.idle.is_empty(), "{lib_name}");
        assert!(pool.in_use.is_empty(), "{lib_name}");
        assert_eq!(
            listener
                .accept()
                .expect_err("invalid name must not connect")
                .kind(),
            ErrorKind::WouldBlock,
            "{lib_name}"
        );
    }

    fn responsive_endpoint() -> (u16, Sender<()>, JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind test listener");
        let port = listener.local_addr().expect("listener address").port();
        let (shutdown_sender, shutdown_receiver) = mpsc::channel();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept scope connection");
            let mut request = [0_u8; 1024];
            let bytes_read = stream.read(&mut request).expect("read startup commands");
            assert!(bytes_read > 0, "startup commands must not be empty");
            stream
                .write_all(b"+OK\r\n+OK\r\n")
                .expect("respond to startup commands");
            shutdown_receiver.recv().expect("receive server shutdown");
        });
        (port, shutdown_sender, server)
    }

    #[tokio::test]
    async fn rejects_invalid_library_name_before_network_activity() {
        assert_invalid_name_is_rejected("invalid name").await;
    }

    #[tokio::test]
    async fn rejects_malformed_library_name_compositions_before_network_activity() {
        for lib_name in ["GlideRust()", "GlideRust(tag)suffix"] {
            assert_invalid_name_is_rejected(lib_name).await;
        }
    }

    #[tokio::test]
    async fn accepts_supported_library_names() {
        // The protobuf scalar represents both an omitted and explicitly empty value as "".
        for lib_name in ["", "GlideRust", "GlideRust(framework:1.2)"] {
            let (port, shutdown_sender, server) = responsive_endpoint();
            let request_bytes = request_bytes(lib_name, port);
            let pool = reserved_pool(request_bytes.clone());

            create_scope_connection(pool.clone(), None, &request_bytes, 0).await;

            {
                let pool = pool.lock().await;
                assert_eq!(pool.total_count.load(Ordering::Acquire), 1, "{lib_name}");
                assert_eq!(pool.idle.len(), 1, "{lib_name}");
                assert!(pool.in_use.is_empty(), "{lib_name}");
            }

            shutdown_sender.send(()).expect("stop mock server");
            server.join().expect("mock server exits cleanly");
        }
    }
}
