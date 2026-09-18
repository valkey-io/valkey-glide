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
//! The pool maintains idle `ScopedConnection`s, each tagged with a `ScopeTarget`: the
//! standalone server, or a cluster primary keyed by its `host:port`. On acquire, the
//! requested slot is resolved to its current primary through the parent `Client`'s slot
//! map, and idle connections are matched by exact target equality, so every slot owned by
//! one primary shares that primary's idle sockets. Mismatched connections are kept for
//! later acquires; when the pool is full and nothing matches, the oldest idle one is
//! evicted to make room. When no match exists, a new connection is created for the
//! resolved target. A slot that cannot be resolved (parent not registered, slot unmapped,
//! topology lock held) never falls back to a seed node; the acquire reports "retry".
//! Language bindings (Java JNI, Python CFFI, Node N-API, Go CGO) should call
//! these functions rather than duplicating the logic.
//!
//! Pub/sub is not supported on a scoped connection. SUBSCRIBE puts the connection
//! into push-message mode, which needs a dedicated message handler that scopes do
//! not wire up; use the parent client's pub/sub API, which keeps its own
//! subscription connections.

use crate::client::Client;
use crate::pool::{
    ScopedConnection, get_client_scope_pools, get_scope_registry, update_state_for_command,
    validate_scope_slot,
};
use redis::{Cmd, RedisError, RedisResult, Value};

#[cfg(feature = "proto")]
use crate::client::SlotAddressError;
#[cfg(feature = "proto")]
use crate::pool::{
    ConnectionState, POOL_RUNNING, ScopeAcquire, ScopePool, ScopeTarget, ScopeTargetUnresolved,
};
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
/// Command execution against a scope's connection.
///
/// Bindings should call [`send_scope_command`] instead: it adds the circuit
/// breaker, inflight limit, compression and latency recording, and requires a
/// parent client. This function handles:
/// - State tracking (WATCH, MULTI, SELECT, subscriptions, etc.)
/// - Cluster slot validation (cross-slot errors)
/// - Command execution via Client::send_command_on_connection (timeout, decompression, IAM)
///
/// # Arguments
/// - `scope_id`: The scope identifier (must be currently in-use)
/// - `cmd_name`: The command name (e.g., "GET", "SET", "WATCH")
/// - `args`: Command arguments as byte slices
/// - `client`: The parent Client. `None` sends the command raw — no timeout, no
///   decompression and no IAM re-authentication — which is only useful for tests
///   that need to observe a connection's state before any re-auth can mask it.
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
    // Captured before the update below, which clears multi_active on the very
    // command that closes the transaction (EXEC/DISCARD); the post-update state
    // would attempt AUTH while the server is still in the old mode.
    let multi_active_before_command = conn.state.multi_active;
    let subscribed_before_command = conn.state.has_subscriptions();
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
            let ScopedConnection {
                connection,
                last_iam_generation,
                ..
            } = &mut *conn;
            c.send_command_on_connection(
                &cmd,
                connection,
                last_iam_generation,
                multi_active_before_command || subscribed_before_command,
            )
            .await
        }
        None => conn.connection.send_packed_command(&cmd).await,
    };

    // A failed re-auth makes this connection unusable for every later command.
    if matches!(&result, Err(e) if e.kind() == redis::ErrorKind::AuthenticationFailed) {
        conn.state.must_discard = true;
    }

    // A timeout, IO error, dropped connection, or protocol desync can leave a
    // server-side waiter armed on the connection (or the connection itself in an
    // unknown state after a failover/CLIENT KILL/restart); a clean protocol error
    // (WRONGTYPE, MOVED/ASK, NOAUTH, or a client-side rejected timeout arg) never
    // reached that state, so the connection stays reusable. Keep the flag set only
    // for the poisoning cases — and never clear a poison a prior command left behind.
    if is_blocking {
        // `is_timeout()` is a strict subset of `is_io_error()` in redis-rs (it only
        // checks for IO errors of kind TimedOut/WouldBlock), so it adds no coverage
        // beyond `is_io_error()` and is omitted here.
        let poisoned = matches!(&result, Err(e) if e.is_io_error()
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
/// The parent client is required: every concern above is derived from it, so a
/// caller without one must fail rather than send. Resolve it with
/// [`resolve_scope_parent`].
///
/// # Errors
/// - `CircuitBreakerOpen` if parent's circuit breaker is open
/// - `ClientError("Reached maximum inflight requests")` if inflight is exhausted
/// - Any error from `execute_scope_command` (timeout, IO, cross-slot, etc.)
pub async fn send_scope_command(
    scope_id: u64,
    cmd_name: &str,
    args: &mut [Vec<u8>],
    client: &Client,
) -> RedisResult<Value> {
    // 1. Circuit breaker check
    if !client.is_circuit_breaker_healthy() {
        return Err(RedisError::from((
            redis::ErrorKind::CircuitBreakerOpen,
            "Client circuit breaker is open - core unhealthy",
        )));
    }

    // 2. Inflight request reservation (reject if exhausted)
    let _inflight_tracker = match client.reserve_inflight_request() {
        Some(t) => t,
        None => {
            return Err(RedisError::from((
                redis::ErrorKind::ClientError,
                "Reached maximum inflight requests",
            )));
        }
    };

    // 3. Compression on write
    if let Some(cm) = client.compression_manager()
        && cm.is_enabled()
    {
        // Resolve command type for compression routing
        let effective_type = crate::request_type::RequestType::from_command_name(cmd_name)
            .unwrap_or(crate::request_type::RequestType::CustomCommand);
        if let Err(e) = crate::compression::process_command_args_for_compression(
            args,
            effective_type,
            Some(cm.as_ref()),
        ) {
            // An incompatible command would operate on compressed bytes — e.g. INCR or
            // APPEND against a compressed value — so reject it as the ordinary dispatch
            // paths do. Other compression failures fall back to the original args.
            if e.is_incompatible_command() {
                return Err(RedisError::from((
                    redis::ErrorKind::ClientError,
                    "Command is incompatible with compression",
                    e.to_string(),
                )));
            }
        }
    }

    // 4. Execute
    let cmd_start = std::time::Instant::now();
    let result = execute_scope_command(scope_id, cmd_name, args, Some(client)).await;

    // 5. Record latency
    client.latency_tracker().record(cmd_start.elapsed());

    result
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND CONNECTION CREATION
// ═══════════════════════════════════════════════════════════════════════════════

/// Timeout for each network phase of scoped connection creation (TCP/TLS
/// connect, and the post-connect init pipeline).
#[cfg(feature = "proto")]
const SCOPE_CONNECT_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(5);

/// Why a scoped connection could not be created and seated in the pool.
///
/// Every variant releases the caller's `max_total` reservation exactly once, at the
/// single failure exit in [`create_scope_connection`], and is logged there so a
/// borrower's eventual "pool exhausted" timeout can be traced back to its cause.
#[cfg(feature = "proto")]
#[derive(Debug)]
pub enum ScopeCreateError {
    /// The stored `ConnectionRequest` bytes did not parse.
    InvalidConnectionRequest(protobuf::Error),
    /// The configured `lib_name` failed validation.
    InvalidLibName,
    /// Standalone target, but the request carries no seed address.
    NoSeedAddress,
    /// redis-rs rejected the URL built from the target.
    InvalidUrl(RedisError),
    /// The connect attempt failed.
    ConnectFailed(RedisError),
    /// The connect attempt did not complete within [`SCOPE_CONNECT_TIMEOUT`].
    ConnectTimedOut,
    /// IAM is configured on the parent but no token is currently available.
    IamTokenUnavailable,
    /// The AUTH/SELECT/CLIENT SETNAME init pipeline failed.
    InitFailed(RedisError),
    /// The init pipeline did not complete within [`SCOPE_CONNECT_TIMEOUT`].
    InitTimedOut,
    /// The pool stopped running while the connection was being created.
    PoolClosed,
}

#[cfg(feature = "proto")]
impl std::fmt::Display for ScopeCreateError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidConnectionRequest(e) => write!(f, "invalid connection request: {e}"),
            Self::InvalidLibName => f.write_str("invalid lib_name"),
            Self::NoSeedAddress => f.write_str("connection request has no seed address"),
            Self::InvalidUrl(e) => write!(f, "invalid target url: {e}"),
            Self::ConnectFailed(e) => write!(f, "connect failed: {e}"),
            Self::ConnectTimedOut => write!(f, "connect timed out after {SCOPE_CONNECT_TIMEOUT:?}"),
            Self::IamTokenUnavailable => f.write_str("IAM token unavailable; cannot AUTH"),
            Self::InitFailed(e) => write!(f, "init pipeline failed: {e}"),
            Self::InitTimedOut => {
                write!(f, "init pipeline timed out after {SCOPE_CONNECT_TIMEOUT:?}")
            }
            Self::PoolClosed => f.write_str("pool is no longer running"),
        }
    }
}

/// An authenticated, initialized connection that has not yet been seated in a pool.
#[cfg(feature = "proto")]
struct PreparedScopeConnection {
    connection: redis::aio::MultiplexedConnection,
    /// Database the connection was SELECTed into (parent runtime DB, else config).
    database_id: u32,
    /// IAM token generation the initial AUTH was built from (0 when IAM is not in use).
    initial_iam_generation: u64,
}

/// Open and initialize a connection to `target`, without touching the pool.
///
/// Pure pipeline: parse request → validate lib name → build URL → connect →
/// AUTH/SELECT/CLIENT SETNAME. Any step failing short-circuits with the reason;
/// reservation accounting is the caller's job.
#[cfg(feature = "proto")]
async fn build_scope_connection(
    client: Option<&Client>,
    connection_request_bytes: &[u8],
    target: &ScopeTarget,
) -> Result<PreparedScopeConnection, ScopeCreateError> {
    use protobuf::Message as _;

    let proto =
        crate::connection_request::ConnectionRequest::parse_from_bytes(connection_request_bytes)
            .map_err(ScopeCreateError::InvalidConnectionRequest)?;
    if !proto.lib_name.is_empty()
        && crate::client::validate_effective_lib_name(proto.lib_name.as_ref()).is_err()
    {
        return Err(ScopeCreateError::InvalidLibName);
    }

    let use_tls = proto.tls_mode.value() != 0;
    let scheme = if use_tls { "rediss" } else { "redis" };
    let url = match target {
        ScopeTarget::Standalone => {
            let addr = proto
                .addresses
                .first()
                .ok_or(ScopeCreateError::NoSeedAddress)?;
            let port = if addr.port == 0 {
                6379
            } else {
                addr.port as u16
            };
            format!("{}://{}:{}", scheme, addr.host, port)
        }
        ScopeTarget::ClusterPrimary(addr) => format!("{}://{}", scheme, addr),
    };

    let redis_client = redis::Client::open(url.as_str()).map_err(ScopeCreateError::InvalidUrl)?;
    let opts = redis::GlideConnectionOptions {
        push_sender: None,
        disconnect_notifier: None,
        discover_az: false,
        connection_timeout: Some(SCOPE_CONNECT_TIMEOUT),
        connection_retry_strategy: None,
        tcp_nodelay: true,
        pubsub_synchronizer: None,
        iam_token_provider: None,
        cert_params_provider: None,
    };
    let mut conn = match tokio::time::timeout(
        SCOPE_CONNECT_TIMEOUT,
        redis_client.get_multiplexed_async_connection(opts),
    )
    .await
    {
        Ok(Ok(c)) => c,
        Ok(Err(e)) => return Err(ScopeCreateError::ConnectFailed(e)),
        Err(_) => return Err(ScopeCreateError::ConnectTimedOut),
    };

    // Post-connect initialization: AUTH + SELECT to match parent client config.
    // Build a pipeline of init commands (batched, single round-trip).
    let mut init_pipe = redis::Pipeline::new();
    let mut init_count = 0;

    // Generation observed at AUTH-build time, so send_command_on_connection can
    // detect whether the token has rotated again since this connection's initial
    // AUTH (rather than always re-authenticating on the very first command, or
    // missing a rotation that lands between here and the first command).
    let mut initial_iam_generation: u64 = 0;

    // AUTH: IAM authentication takes priority when configured on the parent client
    // (matching the documented priority in `AuthenticationInfo`'s doc comment), otherwise
    // fall back to the protobuf-configured password/username.
    if let Some(manager) = client.and_then(|c| c.iam_token_manager()) {
        // Read the generation before the token: if a refresh lands in this gap,
        // `initial_iam_generation` records the (now-stale) pre-refresh generation,
        // so the mismatch on the first scope command still triggers a
        // reauthentication. Reading generation after the token could otherwise
        // suppress it — a refresh landing there would mean the initial AUTH used
        // the old token, but the recorded generation already matches the new one.
        let generation_before_auth = manager.token_generation();
        let current_token = manager.get_token().await;
        if current_token.is_empty() {
            return Err(ScopeCreateError::IamTokenUnavailable);
        }
        initial_iam_generation = generation_before_auth;
        init_pipe
            .cmd("AUTH")
            .arg(manager.username())
            .arg(current_token.as_str());
        init_count += 1;
    } else if let Some(ref auth_info) = proto.authentication_info.0 {
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

    if init_count > 0 {
        match tokio::time::timeout(
            SCOPE_CONNECT_TIMEOUT,
            conn.send_packed_commands(&init_pipe, 0, init_count),
        )
        .await
        {
            Ok(Ok(_)) => {}
            Ok(Err(e)) => return Err(ScopeCreateError::InitFailed(e)),
            Err(_) => return Err(ScopeCreateError::InitTimedOut),
        }
    }

    Ok(PreparedScopeConnection {
        connection: conn,
        database_id,
        initial_iam_generation,
    })
}

/// Create a new scope connection in the background and add it to the pool.
///
/// The caller holds a `max_total` reservation (see [`ScopeAcquire::Reserved`]).
/// On success the reservation is consumed by the seated connection; on any
/// failure it is released here, exactly once, with a single log line naming the
/// target and the cause.
///
/// # Arguments
/// - `pool`: Arc to the scope pool (locked async)
/// - `client`: Optional reference to the parent Client (for IAM and runtime DB)
/// - `connection_request_bytes`: Serialized protobuf ConnectionRequest
/// - `target`: Standalone server or concrete cluster primary
#[cfg(feature = "proto")]
pub async fn create_scope_connection(
    pool: Arc<TokioMutex<ScopePool>>,
    client: Option<&Client>,
    connection_request_bytes: &[u8],
    target: ScopeTarget,
) {
    let connection = build_scope_connection(client, connection_request_bytes, &target).await;

    let mut pool_guard = pool.lock().await;
    let connection = connection.and_then(|prepared| {
        if pool_guard.state.load(Ordering::Acquire) == POOL_RUNNING {
            Ok(prepared)
        } else {
            Err(ScopeCreateError::PoolClosed)
        }
    });

    match connection {
        Ok(prepared) => {
            let scope_id = pool_guard.next_id();
            pool_guard.idle.push_back(ScopedConnection {
                scope_id,
                connection: prepared.connection,
                created_at: Instant::now(),
                last_idle_at: Instant::now(),
                borrowed_at: None,
                state: ConnectionState::with_configured_db(prepared.database_id as u8),
                pinned_slot: None,
                target,
                last_iam_generation: std::sync::atomic::AtomicU64::new(
                    prepared.initial_iam_generation,
                ),
            });
        }
        Err(err) => {
            // Single release point for the reservation. A pool shutting down is
            // expected, not a fault; everything else is worth a warning because
            // the borrower only ever sees a generic "pool exhausted" timeout.
            let message = format!("scoped connection to {target:?} not created: {err}");
            if matches!(err, ScopeCreateError::PoolClosed) {
                logger_core::log_debug("create_scope_connection", message);
            } else {
                logger_core::log_warn("create_scope_connection", message);
            }
            pool_guard.total_count.fetch_sub(1, Ordering::AcqRel);
        }
    }
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
            // Resolve the slot's current primary before touching the pool so a
            // stale or unmapped slot never matches (or creates) a connection to the
            // wrong node. Unresolved never means "use the seed"; whether it means
            // "retry" depends on the cause (see `ScopeTargetUnresolved`).
            let client = get_parent_client(pool.parent_client_id);
            let target = match try_resolve_scope_target(client.as_ref(), routing_slot) {
                Ok(target) => {
                    if let Some(cleared) = pool.last_unresolved_target.take() {
                        logger_core::log_debug(
                            "try_acquire_scope",
                            format!(
                                "client {client_id}: scope target resolves again \
                                 (was: {cleared})"
                            ),
                        );
                    }
                    target
                }
                Err(cause) => {
                    log_unresolved_target(&mut pool, client_id, routing_slot, cause);
                    return -1;
                }
            };
            match pool.try_acquire(registry, target.clone()) {
                ScopeAcquire::Reused(scope_id) => {
                    let _ = telemetrylib::GlideOpenTelemetry::record_scope_acquire();
                    scope_id as i64
                }
                ScopeAcquire::Reserved => {
                    // Spawn background connection creation with the same normalized target
                    // used for idle matching.
                    let pool_clone = scope_pool.clone();
                    let conn_bytes = pool.connection_request_bytes.clone();
                    runtime.spawn(async move {
                        create_scope_connection(pool_clone, client.as_ref(), &conn_bytes, target)
                            .await;
                    });
                    -1
                }
                ScopeAcquire::Exhausted => -1,
            }
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

/// Look up the parent `Client` that owns a scope pool, by the `client_id` the
/// binding registered it under (see [`register_client`]).
///
/// Synchronous and cheap (a `DashMap` read plus an `Arc` bump), so it is safe to
/// call from the non-blocking acquire path as well as from async creation tasks.
/// Returns `None` if the binding has not registered the client or has already
/// unregistered it on close.
#[cfg(feature = "proto")]
pub fn get_parent_client(client_id: u64) -> Option<Client> {
    let registry = get_client_registry();
    registry.get(&client_id).map(|e| e.value().clone())
}

/// Report an unresolved scope target without flooding the log.
///
/// Bindings retry `try_acquire_scope` on a 1-50 ms backoff until their acquire
/// timeout, so an outage of a few seconds would otherwise produce hundreds of
/// identical warnings per caller. The pool remembers the last cause it reported:
/// a cause of a new kind is a warning, a repeat of the same kind is a debug line,
/// and the acquire path clears the memory (with one debug line) once resolution
/// succeeds again. Kind, not value: concurrent acquires for different unmapped
/// slots (every slot, on a lazily connected cluster client before its first
/// command) would otherwise flip the record on each retry and warn every time.
/// Without any of this the borrower only ever sees "pool exhausted" while the
/// slot stays uncovered.
#[cfg(feature = "proto")]
fn log_unresolved_target(
    pool: &mut ScopePool,
    client_id: u64,
    routing_slot: u16,
    cause: ScopeTargetUnresolved,
) {
    let repeated = pool
        .last_unresolved_target
        .is_some_and(|last| last.same_kind(cause));
    pool.last_unresolved_target = Some(cause);
    if repeated {
        logger_core::log_debug(
            "try_acquire_scope",
            format!(
                "client {client_id}: scope target for slot {routing_slot} still unresolved: {cause}"
            ),
        );
        return;
    }
    let outlook = match cause {
        ScopeTargetUnresolved::ParentUnregistered => {
            "retrying cannot help until the binding registers the client"
        }
        ScopeTargetUnresolved::SlotUnmapped(_) | ScopeTargetUnresolved::TopologyLocked => {
            "scope acquire will be retried"
        }
    };
    logger_core::log_warn(
        "try_acquire_scope",
        format!(
            "client {client_id}: cannot resolve scope target for slot {routing_slot}: \
             {cause}; {outlook}"
        ),
    );
}

/// Turn a routing slot into the target its scoped connection must reach.
///
/// Standalone parents always resolve to [`ScopeTarget::Standalone`]; the slot is
/// meaningless there. Cluster parents resolve to the primary currently mapped for
/// the slot. Every failure names its cause (see [`ScopeTargetUnresolved`]) so the
/// caller can tell a transient gap from a parent that is gone, and never falls
/// back to a seed node.
///
/// Non-blocking: a held client wrapper lock is reported as
/// [`ScopeTargetUnresolved::TopologyLocked`] rather than waited on, because the
/// acquire path runs on the binding's thread outside a runtime context.
#[cfg(feature = "proto")]
pub fn try_resolve_scope_target(
    client: Option<&Client>,
    routing_slot: u16,
) -> Result<ScopeTarget, ScopeTargetUnresolved> {
    let client = client.ok_or(ScopeTargetUnresolved::ParentUnregistered)?;
    if !client.is_cluster_mode() {
        return Ok(ScopeTarget::Standalone);
    }
    target_from_slot_lookup(client.try_address_for_slot(routing_slot), routing_slot)
}

/// Async counterpart of [`try_resolve_scope_target`] for callers already on the
/// runtime (prewarm, tests).
///
/// Same rules and same source of truth. The difference is that this variant waits
/// for the client wrapper lock, so it never yields
/// [`ScopeTargetUnresolved::TopologyLocked`].
#[cfg(feature = "proto")]
pub async fn resolve_scope_target(
    client: Option<&Client>,
    routing_slot: u16,
) -> Result<ScopeTarget, ScopeTargetUnresolved> {
    let client = client.ok_or(ScopeTargetUnresolved::ParentUnregistered)?;
    if !client.is_cluster_mode() {
        return Ok(ScopeTarget::Standalone);
    }
    target_from_slot_lookup(client.address_for_slot(routing_slot).await, routing_slot)
}

/// Shared tail of the two resolvers, so their cluster-mode mapping cannot drift.
///
/// `NotClusterMode` maps to the standalone target because that is what it means;
/// in practice the resolvers only reach this after `is_cluster_mode()` returned
/// true, and a cluster client never holds a standalone wrapper, so the arm exists
/// to keep the match exhaustive without a wildcard rather than to handle a live
/// case. A lazily connected cluster client reports `Unmapped`, not
/// `NotClusterMode`: it has no topology yet, and retrying is the right answer.
#[cfg(feature = "proto")]
fn target_from_slot_lookup(
    lookup: Result<String, SlotAddressError>,
    routing_slot: u16,
) -> Result<ScopeTarget, ScopeTargetUnresolved> {
    match lookup {
        Ok(address) => Ok(ScopeTarget::cluster_primary(address)),
        Err(SlotAddressError::NotClusterMode) => Ok(ScopeTarget::Standalone),
        Err(SlotAddressError::Unmapped) => Err(ScopeTargetUnresolved::SlotUnmapped(routing_slot)),
        Err(SlotAddressError::TopologyLocked) => Err(ScopeTargetUnresolved::TopologyLocked),
    }
}

/// Resolve the client that owns `scope_id` without taking a pool lock.
///
/// `None` means the scope is unregistered or its parent has been closed. Callers
/// must fail the command: dispatch used to carry on without a parent, silently
/// running it with none of the guardrails listed on `ScopeEntry::parent_client_id`.
pub fn resolve_scope_parent(scope_id: u64) -> Option<Client> {
    let parent_client_id = get_scope_registry().get(&scope_id)?.parent_client_id;
    get_client_registry()
        .get(&parent_client_id)
        .map(|e| e.value().clone())
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
///
/// Also tears down the client's scope pool, so every binding's close path
/// invalidates outstanding scopes without having to remember to do it.
///
/// `client_id` must be the handle the binding registered and opens scopes with —
/// the adapter pointer for the C FFI, the client id for JNI. Passing the other id
/// silently tears down nothing.
pub fn unregister_client(client_id: u64) {
    get_client_registry().remove(&client_id);
    crate::pool::destroy_client_scope_pool(client_id);
}

#[cfg(all(test, feature = "proto"))]
mod tests {
    use std::io::{ErrorKind, Read, Write};
    use std::net::TcpListener;
    use std::sync::Arc;
    use std::sync::atomic::Ordering;
    use std::sync::mpsc::{self, Sender};
    use std::thread::JoinHandle;
    use std::time::{Duration, Instant};

    use protobuf::Message as _;
    use tokio::sync::Mutex as TokioMutex;

    use super::{
        create_scope_connection, resolve_scope_parent, try_acquire_scope, try_resolve_scope_target,
    };

    use super::Client;
    use crate::client::{ConnectionRequest as ClientRequest, NodeAddress as ClientAddress};

    use crate::connection_request::{ConnectionRequest, NodeAddress};
    use crate::pool::{
        ScopeAcquire, ScopePool, ScopePoolConfig, ScopeTarget, ScopeTargetUnresolved,
        get_client_scope_pools, get_scope_registry,
    };
    use crate::scope::{register_client, unregister_client};

    const DEFAULT_ROUTING_SLOT: u16 = 0;
    const MAX_CLUSTER_SLOT: u16 = 16_383;
    const PRIMARY_A: &str = "10.0.0.1:6379";
    const PRIMARY_B: &str = "10.0.0.2:6379";

    fn reused_scope_id(outcome: ScopeAcquire) -> u64 {
        match outcome {
            ScopeAcquire::Reused(scope_id) => scope_id,
            other => panic!("expected reused scope, got {other:?}"),
        }
    }

    fn request_bytes_with_mode(lib_name: &str, port: u16, cluster_mode_enabled: bool) -> Vec<u8> {
        let mut request = ConnectionRequest::new();
        request.addresses.push(NodeAddress {
            host: "127.0.0.1".into(),
            port: port.into(),
            ..Default::default()
        });
        request.lib_name = lib_name.into();
        request.cluster_mode_enabled = cluster_mode_enabled;
        request.write_to_bytes().expect("serialize scope request")
    }

    fn request_bytes(lib_name: &str, port: u16) -> Vec<u8> {
        request_bytes_with_mode(lib_name, port, false)
    }

    fn reserved_pool(request_bytes: Vec<u8>) -> Arc<TokioMutex<ScopePool>> {
        let pool = ScopePool::new(ScopePoolConfig::default(), request_bytes, 1);
        pool.total_count.store(1, Ordering::Release);
        Arc::new(TokioMutex::new(pool))
    }

    async fn lazy_parent(cluster: bool) -> Client {
        let request = ClientRequest {
            addresses: vec![ClientAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
            }],
            cluster_mode_enabled: cluster,
            lazy_connect: true,
            ..Default::default()
        };

        Client::new(request, None)
            .await
            .expect("lazy client construction does not touch the network")
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

        create_scope_connection(pool.clone(), None, &request_bytes, ScopeTarget::Standalone).await;

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

            create_scope_connection(pool.clone(), None, &request_bytes, ScopeTarget::Standalone)
                .await;

            {
                let pool = pool.lock().await;
                assert_eq!(pool.total_count.load(Ordering::Acquire), 1, "{lib_name}");
                assert_eq!(pool.idle.len(), 1, "{lib_name}");
                assert_eq!(pool.idle[0].target, ScopeTarget::Standalone, "{lib_name}");
                assert!(pool.in_use.is_empty(), "{lib_name}");
            }

            shutdown_sender.send(()).expect("stop mock server");
            server.join().expect("mock server exits cleanly");
        }
    }

    /// A cluster acquire whose slot owner cannot be resolved must neither reserve
    /// capacity nor open a connection to the seed address.
    ///
    /// The parent here is a registered cluster client whose slot map does not know
    /// slot 42, so resolution fails on the slot rather than on a missing parent
    /// (covered by `resolves_scope_targets_from_parent_client`).
    ///
    /// Boundary: a lazily connected client has no slot map at all, which is a real
    /// unresolvable state but not the same as a connected cluster whose slot is
    /// temporarily unmapped mid-resharding. That case needs a live cluster and is
    /// covered by the scope tests in `tests/test_client.rs`.
    #[tokio::test]
    async fn unresolved_cluster_target_does_not_use_seed_and_does_not_reserve() {
        let listener = listening_endpoint();
        let port = listener.local_addr().expect("listener address").port();
        let request_bytes = request_bytes_with_mode("", port, true);

        let client_id = 67_950_001_u64;
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            client_id,
        )));
        get_client_scope_pools().insert(client_id, pool.clone());

        let parent = lazy_parent(true).await;
        register_client(client_id, parent.clone());

        assert!(parent.is_cluster_mode());
        assert_eq!(
            try_resolve_scope_target(Some(&parent), 42),
            Err(ScopeTargetUnresolved::SlotUnmapped(42))
        );
        let acquired = try_acquire_scope(
            client_id,
            request_bytes.clone(),
            &tokio::runtime::Handle::current(),
            42,
        );
        get_client_scope_pools().remove(&client_id);

        unregister_client(client_id);

        assert_eq!(acquired, -1);
        {
            let pool = pool.lock().await;
            assert_eq!(pool.total_count.load(Ordering::Acquire), 0);
            assert!(pool.idle.is_empty());
            assert!(pool.in_use.is_empty());
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
        assert_eq!(
            listener
                .accept()
                .expect_err("unresolved cluster target must not connect to the seed")
                .kind(),
            ErrorKind::WouldBlock
        );
    }

    /// Polls the listener, awaiting between attempts so the spawned creation task
    /// gets to run on a current-thread runtime.
    async fn accept_within(listener: &TcpListener, timeout: Duration) -> bool {
        let deadline = Instant::now() + timeout;
        while Instant::now() < deadline {
            match listener.accept() {
                Ok(_) => return true,
                Err(e) if e.kind() == ErrorKind::WouldBlock => {
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
                Err(e) => panic!("unexpected listener error: {e}"),
            }
        }
        false
    }

    /// `max_total = N` must permit N concurrent scopes, which means the Nth
    /// reservation has to be filled like any other. `max_total = 1` makes the very
    /// first acquire that boundary case: the pool reserves the only slot, so a
    /// caller that re-checks capacity after the reservation sees the pool already
    /// full, never creates the connection, and the borrower times out.
    #[tokio::test]
    async fn acquire_creates_the_connection_for_the_final_slot() {
        let listener = listening_endpoint();
        let port = listener.local_addr().expect("listener address").port();
        let request_bytes = request_bytes("GlideRust", port);

        let client_id = 67_950_000_u64;
        let config = ScopePoolConfig {
            max_total: 1,
            ..ScopePoolConfig::default()
        };
        get_client_scope_pools().insert(
            client_id,
            Arc::new(TokioMutex::new(ScopePool::new(
                config,
                request_bytes.clone(),
                client_id,
            ))),
        );

        register_client(client_id, lazy_parent(false).await);

        let acquired = try_acquire_scope(
            client_id,
            request_bytes.clone(),
            &tokio::runtime::Handle::current(),
            0,
        );

        unregister_client(client_id);

        get_client_scope_pools().remove(&client_id);

        assert_eq!(
            acquired, -1,
            "no idle connection yet, so the caller retries"
        );
        assert!(
            accept_within(&listener, Duration::from_secs(5)).await,
            "reserving the last slot must still create its connection"
        );
    }

    /// Scope target resolution derives topology exclusively from the registered parent Client,
    /// keeping it as the single source of truth rather than duplicating state in the pool.
    /// A missing parent fails closed; standalone parents ignore the slot, while cluster parents
    /// require a mapped primary and otherwise name the unmapped slot. Each failure
    /// carries its cause, so the acquire path can tell a gone parent from a gap.
    #[tokio::test]
    async fn resolves_scope_targets_from_parent_client() {
        let standalone = lazy_parent(false).await;

        assert_eq!(
            try_resolve_scope_target(None, DEFAULT_ROUTING_SLOT),
            Err(ScopeTargetUnresolved::ParentUnregistered)
        );

        assert_eq!(
            try_resolve_scope_target(Some(&standalone), DEFAULT_ROUTING_SLOT),
            Ok(ScopeTarget::Standalone)
        );
        assert_eq!(
            try_resolve_scope_target(Some(&standalone), MAX_CLUSTER_SLOT),
            Ok(ScopeTarget::Standalone)
        );

        let cluster = lazy_parent(true).await;
        assert_eq!(
            try_resolve_scope_target(Some(&cluster), DEFAULT_ROUTING_SLOT),
            Err(ScopeTargetUnresolved::SlotUnmapped(DEFAULT_ROUTING_SLOT))
        );
        assert_eq!(
            try_resolve_scope_target(Some(&cluster), MAX_CLUSTER_SLOT),
            Err(ScopeTargetUnresolved::SlotUnmapped(MAX_CLUSTER_SLOT))
        );
    }

    /// An unresolved target is recorded on the pool and always holds the latest
    /// cause, so the acquire path can tell a repeat of the same kind (debug) from a
    /// new kind of cause (warn) instead of warning on every binding retry. A
    /// different unmapped slot is the same kind, so interleaved acquires for
    /// distinct slots do not flip it back to a warn. A successful resolution
    /// clears it.
    ///
    /// The log lines themselves are not observable here; the field they key on and
    /// `same_kind` are. A lazily connected cluster parent has no slot map, so every
    /// slot is `SlotUnmapped`; unregistering the parent switches the kind to
    /// `ParentUnregistered`; re-registering a standalone parent under the same id
    /// makes resolution succeed.
    #[tokio::test]
    async fn unresolved_target_is_recorded_once_per_cause_and_cleared_on_success() {
        let listener = listening_endpoint();
        let port = listener.local_addr().expect("listener address").port();
        let request_bytes = request_bytes_with_mode("", port, true);

        let client_id = 67_950_002_u64;
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            client_id,
        )));
        get_client_scope_pools().insert(client_id, pool.clone());
        register_client(client_id, lazy_parent(true).await);

        let acquire = |slot: u16| {
            try_acquire_scope(
                client_id,
                request_bytes.clone(),
                &tokio::runtime::Handle::current(),
                slot,
            )
        };
        let recorded = || {
            pool.try_lock()
                .expect("pool lock is free between acquires")
                .last_unresolved_target
        };

        assert_eq!(
            recorded(),
            None,
            "nothing recorded before the first acquire"
        );

        assert_eq!(acquire(42), -1);
        assert_eq!(recorded(), Some(ScopeTargetUnresolved::SlotUnmapped(42)));

        // Same cause again: the record is unchanged (a repeat, logged at debug).
        assert_eq!(acquire(42), -1);
        assert_eq!(recorded(), Some(ScopeTargetUnresolved::SlotUnmapped(42)));

        // A different unmapped slot updates the recorded value (so the message
        // names the current slot) but is the same kind, so it counts as a repeat.
        assert_eq!(acquire(7), -1);
        let after_other_slot = recorded();
        assert_eq!(
            after_other_slot,
            Some(ScopeTargetUnresolved::SlotUnmapped(7))
        );
        assert!(
            after_other_slot.is_some_and(|c| c.same_kind(ScopeTargetUnresolved::SlotUnmapped(42))),
            "interleaved unmapped slots are one episode, not a new warning each"
        );
        assert!(
            !ScopeTargetUnresolved::SlotUnmapped(7)
                .same_kind(ScopeTargetUnresolved::ParentUnregistered),
            "a different variant is a different kind"
        );

        // The parent going away is a new kind of cause.
        unregister_client(client_id);
        // unregister_client tears the pool down with the client; reseat it so the
        // remaining acquires observe the same pool instance.
        get_client_scope_pools().insert(client_id, pool.clone());
        assert_eq!(acquire(7), -1);
        assert_eq!(recorded(), Some(ScopeTargetUnresolved::ParentUnregistered));

        // Resolution succeeding clears the record. The standalone parent resolves
        // without touching the network; the acquire then reserves and spawns a
        // creation task we do not wait for.
        register_client(client_id, lazy_parent(false).await);
        assert_eq!(
            acquire(7),
            -1,
            "no idle connection yet, so the caller retries"
        );
        assert_eq!(recorded(), None);

        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);
    }

    #[tokio::test]
    async fn standalone_reuses_released_connection_across_ignored_routing_slots() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes("", port);
        let config = ScopePoolConfig {
            max_total: 1,
            ..ScopePoolConfig::default()
        };
        let pool = ScopePool::new(config, request_bytes.clone(), 1);
        pool.total_count.store(1, Ordering::Release);
        let pool = Arc::new(TokioMutex::new(pool));

        let parent = lazy_parent(false).await;
        let initial_target = try_resolve_scope_target(Some(&parent), DEFAULT_ROUTING_SLOT)
            .expect("standalone always resolves");
        assert_eq!(initial_target, ScopeTarget::Standalone);
        create_scope_connection(pool.clone(), None, &request_bytes, initial_target).await;

        let registry = crate::pool::get_scope_registry();
        let first_scope_id = {
            let mut pool = pool.lock().await;
            assert_eq!(pool.idle.len(), 1);
            let target = try_resolve_scope_target(Some(&parent), DEFAULT_ROUTING_SLOT)
                .expect("standalone always resolves");
            reused_scope_id(pool.try_acquire(registry, target))
        };

        {
            let mut pool = pool.lock().await;
            assert!(pool.release(first_scope_id, registry));
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
        }

        let second_scope_id = {
            let mut pool = pool.lock().await;
            let alternate_target = try_resolve_scope_target(Some(&parent), MAX_CLUSTER_SLOT)
                .expect("standalone always resolves");
            assert_eq!(alternate_target, ScopeTarget::Standalone);
            reused_scope_id(pool.try_acquire(registry, alternate_target))
        };
        assert_eq!(second_scope_id, first_scope_id);

        {
            let mut pool = pool.lock().await;
            assert!(pool.release(second_scope_id, registry));
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.idle[0].target, ScopeTarget::Standalone);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
        }

        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
    }

    /// Two different slots owned by the same primary produce equal targets, so one
    /// idle socket serves both, the same-primary fragmentation the slot-keyed model
    /// could not avoid.
    ///
    /// This covers target matching and reuse only. The pool and its connection are
    /// standalone against a mock endpoint, and the cluster target is written onto
    /// `idle[0].target` directly, since a connection genuinely created for `PRIMARY_A`
    /// would have to reach that address.
    ///
    /// Boundary: resolving a slot to a real primary and connecting to it needs a live
    /// cluster, and is covered by the scope tests in `tests/test_client.rs`.
    #[tokio::test]
    async fn same_primary_target_reuses_released_connection() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes("", port);
        let pool = reserved_pool(request_bytes.clone());
        create_scope_connection(pool.clone(), None, &request_bytes, ScopeTarget::Standalone).await;

        let registry = crate::pool::get_scope_registry();
        let target = ScopeTarget::cluster_primary(PRIMARY_A);
        let first_scope_id = {
            let mut pool = pool.lock().await;
            pool.idle[0].target = target.clone();
            reused_scope_id(pool.try_acquire(registry, target.clone()))
        };

        {
            let mut pool = pool.lock().await;
            assert!(pool.release(first_scope_id, registry));
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.idle[0].target, target);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
        }

        // A separately constructed target for the same primary (as a different slot
        // would produce) is equal by address, not by Arc identity.
        let second_scope_id = {
            let mut pool = pool.lock().await;
            reused_scope_id(pool.try_acquire(registry, ScopeTarget::cluster_primary(PRIMARY_A)))
        };
        assert_eq!(second_scope_id, first_scope_id);

        {
            let mut pool = pool.lock().await;
            assert!(pool.release(second_scope_id, registry));
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.idle[0].target, target);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
        }

        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
    }

    #[tokio::test]
    async fn exact_target_matching_preserves_mismatches_and_release_target() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes("", port);
        let pool = reserved_pool(request_bytes.clone());
        create_scope_connection(pool.clone(), None, &request_bytes, ScopeTarget::Standalone).await;

        let registry = crate::pool::get_scope_registry();
        let scope_id = {
            let mut pool = pool.lock().await;
            // Below max_total, a mismatched idle connection is kept and a new
            // slot is reserved for the other primary. Each reservation is counted
            // against max_total, so the running total is asserted alongside it.
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
            pool.idle[0].target = ScopeTarget::cluster_primary(PRIMARY_A);
            assert_eq!(
                pool.try_acquire(registry, ScopeTarget::cluster_primary(PRIMARY_B)),
                ScopeAcquire::Reserved
            );
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);

            pool.idle[0].target = ScopeTarget::cluster_primary(PRIMARY_B);
            assert_eq!(
                pool.try_acquire(registry, ScopeTarget::cluster_primary(PRIMARY_A)),
                ScopeAcquire::Reserved
            );
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 3);

            // Reuse consumes no additional capacity.
            pool.idle[0].target = ScopeTarget::Standalone;
            let reused = reused_scope_id(pool.try_acquire(registry, ScopeTarget::Standalone));
            assert_eq!(pool.total_count.load(Ordering::Acquire), 3);
            reused
        };

        {
            let entry = registry.get(&(scope_id)).expect("registered scope");
            entry.connection.lock().await.pinned_slot = Some(0);
        }
        {
            let mut pool = pool.lock().await;
            assert!(pool.release(scope_id, registry));
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.idle[0].target, ScopeTarget::Standalone);
            assert_eq!(pool.idle[0].pinned_slot, None);
        }

        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
    }

    /// A full pool whose only idle connections point at other primaries must evict
    /// the oldest idle one and reserve, not report exhaustion while capacity sits
    /// unused. With nothing idle it is still exhausted.
    #[tokio::test]
    async fn full_pool_evicts_oldest_mismatched_idle_instead_of_exhausting() {
        let (port_a, shutdown_a, server_a) = responsive_endpoint();
        let (port_b, shutdown_b, server_b) = responsive_endpoint();
        let config = ScopePoolConfig {
            max_total: 2,
            ..ScopePoolConfig::default()
        };
        let pool = ScopePool::new(config, request_bytes("", port_a), 1);
        pool.total_count.store(2, Ordering::Release);
        let pool = Arc::new(TokioMutex::new(pool));

        // Seat two idle connections; the first seated is the oldest (front).
        create_scope_connection(
            pool.clone(),
            None,
            &request_bytes("", port_a),
            ScopeTarget::Standalone,
        )
        .await;
        create_scope_connection(
            pool.clone(),
            None,
            &request_bytes("", port_b),
            ScopeTarget::Standalone,
        )
        .await;

        let registry = crate::pool::get_scope_registry();
        {
            let mut pool = pool.lock().await;
            assert_eq!(pool.idle.len(), 2);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);
            pool.idle[0].target = ScopeTarget::cluster_primary(PRIMARY_A);
            pool.idle[1].target = ScopeTarget::cluster_primary(PRIMARY_B);
            let oldest_id = pool.idle[0].scope_id;
            let newest_id = pool.idle[1].scope_id;

            // Full + all mismatched: evict the oldest, reserve for the new target.
            assert_eq!(
                pool.try_acquire(registry, ScopeTarget::cluster_primary("10.0.0.3:6379")),
                ScopeAcquire::Reserved
            );
            assert_eq!(pool.idle.len(), 1, "exactly one idle connection evicted");
            assert_eq!(
                pool.idle[0].scope_id, newest_id,
                "the oldest idle connection is the one evicted"
            );
            assert_ne!(pool.idle[0].scope_id, oldest_id);
            assert_eq!(
                pool.total_count.load(Ordering::Acquire),
                2,
                "eviction frees the slot the reservation then consumes"
            );

            // Still full; the remaining idle connection is a match and is reused,
            // so nothing is evicted.
            let reused = reused_scope_id(
                pool.try_acquire(registry, ScopeTarget::cluster_primary(PRIMARY_B)),
            );
            assert_eq!(reused, newest_id);
            assert!(pool.idle.is_empty());
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);

            // Full with nothing idle: genuinely exhausted, and no reservation leaks.
            assert_eq!(
                pool.try_acquire(registry, ScopeTarget::cluster_primary(PRIMARY_A)),
                ScopeAcquire::Exhausted
            );
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);

            assert!(pool.release(reused, registry));
        }

        shutdown_a.send(()).expect("stop mock server a");
        shutdown_b.send(()).expect("stop mock server b");
        server_a.join().expect("mock server a exits cleanly");
        server_b.join().expect("mock server b exits cleanly");
    }

    /// Holding the pool lock reproduces the contention that made the old
    /// try_lock scan report no parent.
    #[tokio::test]
    #[serial_test::serial]
    async fn parent_resolves_while_the_pool_lock_is_held() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes("", port);
        // reserved_pool() seats the pool under parent client id 1.
        let pool = reserved_pool(request_bytes.clone());
        let parent_client_id = 1;

        let mut parent_request = crate::client::ConnectionRequest::default();
        parent_request.addresses.push(crate::client::NodeAddress {
            host: "127.0.0.1".into(),
            port,
        });
        parent_request.lazy_connect = true;
        let parent = Client::new(parent_request, None)
            .await
            .expect("lazy parent client creation should succeed");
        register_client(parent_client_id, parent);

        create_scope_connection(pool.clone(), None, &request_bytes, ScopeTarget::Standalone).await;
        let acquired = {
            let mut pool = pool.lock().await;
            pool.try_acquire(get_scope_registry(), ScopeTarget::Standalone)
        };

        let resolved = if let ScopeAcquire::Reused(scope_id) = acquired {
            let held = pool.lock().await;
            let resolved = resolve_scope_parent(scope_id).is_some();
            drop(held);
            get_scope_registry().remove(&scope_id);
            Some(resolved)
        } else {
            None
        };

        // Clean up the global registries before asserting, so a failure here
        // cannot leak this client into later tests in the same binary.
        unregister_client(parent_client_id);
        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");

        assert_eq!(
            resolved,
            Some(true),
            "a contended pool lock must not hide the scope's parent client"
        );
    }

    /// Closing the parent must invalidate its outstanding scopes, otherwise a
    /// scope keeps executing against a connection whose owner is gone.
    #[tokio::test]
    #[serial_test::serial]
    async fn closing_the_parent_invalidates_its_outstanding_scopes() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes("", port);
        // reserved_pool() seats the pool under parent client id 1.
        let parent_client_id = 1;
        let pool = reserved_pool(request_bytes.clone());
        get_client_scope_pools().insert(parent_client_id, pool.clone());

        let mut parent_request = crate::client::ConnectionRequest::default();
        parent_request.addresses.push(crate::client::NodeAddress {
            host: "127.0.0.1".into(),
            port,
        });
        parent_request.lazy_connect = true;
        let parent = Client::new(parent_request, None)
            .await
            .expect("lazy parent client creation should succeed");
        register_client(parent_client_id, parent);

        create_scope_connection(pool.clone(), None, &request_bytes, ScopeTarget::Standalone).await;
        let acquired = {
            let mut pool = pool.lock().await;
            pool.try_acquire(get_scope_registry(), ScopeTarget::Standalone)
        };

        let observed = if let ScopeAcquire::Reused(scope_id) = acquired {
            let registered_before = get_scope_registry().contains_key(&scope_id);

            unregister_client(parent_client_id);

            Some((
                registered_before,
                get_scope_registry().contains_key(&scope_id),
                get_client_scope_pools().contains_key(&parent_client_id),
                resolve_scope_parent(scope_id).is_some(),
            ))
        } else {
            None
        };

        // Clean up before asserting so a failure cannot leak into later tests.
        if let ScopeAcquire::Reused(scope_id) = acquired {
            get_scope_registry().remove(&scope_id);
        }
        get_client_scope_pools().remove(&parent_client_id);
        unregister_client(parent_client_id);
        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");

        assert_eq!(
            observed,
            Some((true, false, false, false)),
            "closing the parent must drop the scope entry, its pool, and any parent resolution"
        );
    }

    /// Mirrors the poison predicate from `execute_scope_command` directly against
    /// synthetic errors, since constructing a real `FatalReceiveError`/`FatalSendError`
    /// (multiplexed connection driver errors) or `ProtocolDesync` requires a live
    /// server round-trip and isn't practical to reproduce as a pure unit test here.
    fn is_poisoning_error(e: &redis::RedisError) -> bool {
        e.is_io_error() || e.is_connection_dropped() || e.kind() == redis::ErrorKind::ProtocolDesync
    }

    #[test]
    fn poison_predicate_catches_dropped_connections_and_protocol_desync() {
        use std::io;

        // A broken pipe / connection reset is an IO error AND a dropped-connection
        // error (both is_io_error() and is_connection_dropped() are true) — the
        // connection is dead either way.
        let broken_pipe = redis::RedisError::from(io::Error::from(io::ErrorKind::BrokenPipe));
        assert!(is_poisoning_error(&broken_pipe));

        // FatalSendError/FatalReceiveError are dropped-connection errors from the
        // multiplexed connection driver but are NOT is_io_error() (they don't wrap
        // an io::Error) — this is the case the earlier `is_io_error()`-only
        // predicate missed.
        let fatal_send =
            redis::RedisError::from((redis::ErrorKind::FatalSendError, "failed to send command"));
        assert!(fatal_send.is_connection_dropped());
        assert!(!fatal_send.is_io_error());
        assert!(is_poisoning_error(&fatal_send));

        let fatal_receive = redis::RedisError::from((
            redis::ErrorKind::FatalReceiveError,
            "failed to receive response",
        ));
        assert!(is_poisoning_error(&fatal_receive));

        // ProtocolDesync means the client and server have lost sync on the wire
        // protocol (e.g. after a failover mid-response) — also neither an IO error
        // nor a "dropped connection" error by redis-rs's own classification, so it
        // must be matched explicitly.
        let protocol_desync =
            redis::RedisError::from((redis::ErrorKind::ProtocolDesync, "protocol desync"));
        assert!(!protocol_desync.is_io_error());
        assert!(!protocol_desync.is_connection_dropped());
        assert!(is_poisoning_error(&protocol_desync));

        // A clean protocol-level error (e.g. WRONGTYPE) never leaves the
        // connection in a bad state and must not be treated as poisoning.
        let wrong_type = redis::RedisError::from((redis::ErrorKind::TypeError, "WRONGTYPE"));
        assert!(!is_poisoning_error(&wrong_type));
    }
}
