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
//!
//! While a raw MULTI is open the local cross-slot check is skipped (see
//! [`execute_scope_command`]) so every queued command reaches the server and its
//! own EXECABORT governs the transaction, as on the non-scoped path.

use crate::client::{AuthGeneration, Client};
use crate::errors::RequestErrorType;
use crate::pool::{
    ScopedConnection, get_client_scope_pools, get_scope_registry, slot_for_key,
    update_state_for_command,
};
use redis::{Cmd, RedisError, RedisResult, Value};

#[cfg(feature = "proto")]
use crate::client::SlotAddressError;
#[cfg(feature = "proto")]
use crate::pool::{
    ConnectionState, POOL_RUNNING, ScopeAcquire, ScopePool, ScopePoolHandle, ScopeTarget,
    ScopeTargetUnresolved,
};
#[cfg(feature = "proto")]
use std::str::FromStr;
#[cfg(feature = "proto")]
use std::sync::Arc;
#[cfg(feature = "proto")]
use std::sync::atomic::Ordering;
#[cfg(feature = "proto")]
use std::time::{Duration, Instant};
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

/// The slot a command constrains a scope to, derived from cluster routing.
#[derive(Debug, PartialEq)]
pub enum ScopeSlot {
    /// The command does not constrain the scope (no keys, random/keyless routing,
    /// or an admin fan-out that a single-connection scope treats as unconstrained).
    Unconstrained,
    /// The command targets exactly this slot.
    Slot(u16),
    /// The command's keys span more than one slot. A scope is a single pinned
    /// connection and cannot fan out, so this is a cross-slot error (the same
    /// outcome the cluster layer gives an atomic pipeline it cannot split).
    CrossSlot,
}

/// Resolve the slot a command constrains a scope to, using the same routing table
/// (`RoutingInfo::for_routable`) the ordinary dispatch path uses — rather than a
/// hand-maintained key-position table that drifts and misclassifies commands.
///
/// The mapping reflects a scope's defining constraint: one pinned connection, no
/// fan-out. So it mirrors how the cluster layer treats a command it cannot split:
/// - `SpecificNode(slot)` — the command's slot; constrain to it.
/// - `MultiSlot` — a multi-key command whose keys span slots; unanimous slot
///   constrains, otherwise `CrossSlot` (the atomic-pipeline outcome).
/// - `Random` / `RandomPrimary` / `None` — keyless or free routing; unconstrained.
/// - `MultiNode` (`AllNodes` / `AllMasters`) — an admin fan-out; inapplicable to a
///   single-connection scope, so unconstrained (it dispatches to the one connection).
/// - `ByAddress` — no slot; unconstrained.
///
/// `EVAL`/`FCALL` resolve to `SpecificNode(first-key slot)` and are NOT scattered by
/// the router, so a cross-slot multi-key script would otherwise slip through. We
/// cross-check the script's declared keys explicitly to keep it consistent with the
/// multi-key `MultiSlot` commands above.
pub fn scope_slot_for_command(cmd_name: &str, args: &[&[u8]]) -> ScopeSlot {
    use redis::cluster_routing::{MultipleNodeRoutingInfo, RoutingInfo, SingleNodeRoutingInfo};

    // Scripts declare their keys explicitly (`EVAL script numkeys k1 k2 ...`); the
    // router routes them to the first key's slot without checking the rest, so verify
    // they share a slot here. A malformed or zero-key invocation yields no keys.
    if matches!(
        cmd_name.to_uppercase().as_str(),
        "EVAL" | "EVALSHA" | "EVAL_RO" | "EVALSHA_RO" | "FCALL" | "FCALL_RO"
    ) {
        let numkeys = args
            .get(1)
            .and_then(|n| std::str::from_utf8(n).ok())
            .and_then(|n| n.parse::<usize>().ok())
            .unwrap_or(0);
        let keys = args.get(2..2 + numkeys).unwrap_or(&[]);
        return slot_of_unanimous_keys(keys);
    }

    let mut cmd = Cmd::new();
    cmd.arg(cmd_name.as_bytes());
    for arg in args {
        cmd.arg(*arg);
    }

    match RoutingInfo::for_routable(&cmd) {
        // Keyless / free routing, and admin fan-outs (AllNodes/AllMasters) which a
        // single-connection scope cannot and need not spread: unconstrained.
        None
        | Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::Random
            | SingleNodeRoutingInfo::RandomPrimary
            | SingleNodeRoutingInfo::ByAddress { .. },
        ))
        | Some(RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, _)))
        | Some(RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, _))) => {
            ScopeSlot::Unconstrained
        }
        // Single-slot command: constrain to it.
        Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(route))) => {
            ScopeSlot::Slot(route.slot())
        }
        // Multi-key command (MGET/DEL/MSET/...). Unanimous slot constrains; otherwise
        // cross-slot — the outcome the cluster layer gives an unsplittable atomic unit.
        Some(RoutingInfo::MultiNode((MultipleNodeRoutingInfo::MultiSlot((routes, _)), _))) => {
            let Some(((first, _), rest)) = routes.split_first() else {
                return ScopeSlot::Unconstrained;
            };
            let slot = first.slot();
            if rest.iter().all(|(r, _)| r.slot() == slot) {
                ScopeSlot::Slot(slot)
            } else {
                ScopeSlot::CrossSlot
            }
        }
    }
}

/// Return `Slot(s)` if every key in `keys` hashes to the same slot `s`, `CrossSlot`
/// if they disagree, `Unconstrained` if there are none.
fn slot_of_unanimous_keys(keys: &[&[u8]]) -> ScopeSlot {
    let Some((first, rest)) = keys.split_first() else {
        return ScopeSlot::Unconstrained;
    };
    let slot = slot_for_key(first);
    if rest.iter().all(|k| slot_for_key(k) == slot) {
        ScopeSlot::Slot(slot)
    } else {
        ScopeSlot::CrossSlot
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
///   decompression and no rotation re-authentication — which is only useful for tests
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

    // Cluster mode: validate slot consistency (skip in standalone — no slots).
    // Skip too while a MULTI is already open: a locally-rejected command never
    // reaches the server, so EXEC would commit the rest of the queue instead of
    // EXECABORTing. Let every queued command through and let the server's own
    // cross-slot check flag the transaction, as the non-scoped path does.
    let is_cluster = match client {
        Some(c) => c.is_cluster_mode(),
        None => false,
    };
    if is_cluster && !multi_active_before_command {
        match scope_slot_for_command(cmd_name, &arg_refs) {
            // No slot constraint (keyless, random/fan-out routing): nothing to check.
            ScopeSlot::Unconstrained => {}
            // The command's keys span slots; a single-connection scope cannot fan out.
            ScopeSlot::CrossSlot => {
                return Err(RedisError::from((
                    redis::ErrorKind::CrossSlot,
                    "CROSSSLOT",
                    "Cross-slot error: command keys span multiple slots".to_string(),
                )));
            }
            ScopeSlot::Slot(slot) => {
                // Seed from the acquisition slot so the first keyed command must hash
                // to it: the physical connection already targets that slot, so a first
                // command for a different slot would only fail as a server MOVED (or
                // silently "succeed" when the two slots share a primary). A no-op once
                // pinned.
                match conn.pinned_slot.or(conn.acquisition_slot) {
                    None => conn.pinned_slot = Some(slot),
                    Some(pinned) if pinned == slot => conn.pinned_slot = Some(pinned),
                    Some(pinned) => {
                        return Err(RedisError::from((
                            redis::ErrorKind::CrossSlot,
                            "CROSSSLOT",
                            format!(
                                "Cross-slot error: command targets slot {slot} but scope is pinned to slot {pinned}"
                            ),
                        )));
                    }
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
                last_auth_generation,
                ..
            } = &mut *conn;
            c.send_command_on_connection(
                &cmd,
                connection,
                last_auth_generation,
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
/// single failure exit in [`create_scope_connection`], is logged there (once per
/// episode, see [`ScopePool::last_create_warn`]), and is returned as its
/// [`ScopeCreateErrorKind`] so the acquire that reserved the slot can stop or dial
/// again.
#[cfg(feature = "proto")]
#[derive(Debug)]
pub enum ScopeCreateError {
    /// The stored `ConnectionRequest` bytes did not parse.
    InvalidConnectionRequest(protobuf::Error),
    /// The configured `lib_name` failed validation.
    InvalidLibName,
    /// Standalone target, but the request carries no seed address.
    NoSeedAddress,
    /// A `ClusterPrimary` target string was not a parseable `host:port`.
    InvalidClusterTarget(Arc<String>),
    /// The request configures custom TLS certificate material, but none is
    /// available from the parent — a lazy parent that has not connected yet has
    /// nothing to inherit. Connecting anyway would silently fall back to system
    /// trust roots with no client certificate.
    ParentCertMaterialUnavailable,
    /// The standalone seed is an invalid Unix domain socket address (for example, one
    /// combined with TLS).
    InvalidUnixSocketAddress(String),
    /// `redis::Client::open` rejected the constructed `ConnectionInfo`.
    ClientOpenFailed(RedisError),
    /// The connect attempt failed.
    ConnectFailed(RedisError),
    /// The connect attempt did not complete within `SCOPE_CONNECT_TIMEOUT`.
    ConnectTimedOut,
    /// IAM is configured on the parent but no token is currently available.
    IamTokenUnavailable,
    /// IAM authentication is configured but TLS is disabled. An IAM token is a
    /// bearer credential and must never be sent over plaintext TCP.
    IamRequiresTls,
    /// The AUTH/SELECT/CLIENT SETNAME init pipeline failed.
    InitFailed(RedisError),
    /// The init pipeline did not complete within `SCOPE_CONNECT_TIMEOUT`.
    InitTimedOut,
    /// The pool stopped running while the connection was being created.
    PoolClosed,
}

/// The counterpart of [`crate::pool::ScopeTargetUnresolved::same_kind`] for
/// creation failures, so repeats of one cause are recognized by variant. A
/// payload-free copy rather than the error itself: the pool only needs equality
/// for the warn-once record, and the acquire needs classification
/// ([`Self::fail_cause`]) and a name; retaining a `RedisError` (or the parse
/// error) for either would be wasteful.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScopeCreateErrorKind {
    InvalidConnectionRequest,
    InvalidLibName,
    NoSeedAddress,
    InvalidClusterTarget,
    InvalidUnixSocketAddress,
    ParentCertMaterialUnavailable,
    ClientOpenFailed,
    ConnectFailed,
    ConnectTimedOut,
    IamTokenUnavailable,
    IamRequiresTls,
    InitFailed,
    InitTimedOut,
    PoolClosed,
}

impl ScopeCreateErrorKind {
    /// The terminal cause this kind reports to the borrower, or `None` when
    /// retrying may change the outcome.
    ///
    /// Terminal kinds are properties of the parent's configuration or the pool's
    /// lifecycle, so no later dial can succeed until something outside the acquire
    /// changes. Network and server-side failures are transient: the next dial may
    /// succeed.
    pub fn fail_cause(self) -> Option<ScopeFailCause> {
        match self {
            Self::InvalidConnectionRequest
            | Self::InvalidLibName
            | Self::NoSeedAddress
            | Self::InvalidClusterTarget
            | Self::InvalidUnixSocketAddress => Some(ScopeFailCause::InvalidConfiguration),
            Self::IamRequiresTls => Some(ScopeFailCause::IamRequiresTls),
            Self::ParentCertMaterialUnavailable => {
                Some(ScopeFailCause::ParentCertMaterialUnavailable)
            }
            Self::PoolClosed => Some(ScopeFailCause::PoolClosed),
            Self::ClientOpenFailed
            | Self::ConnectFailed
            | Self::ConnectTimedOut
            | Self::IamTokenUnavailable
            | Self::InitFailed
            | Self::InitTimedOut => None,
        }
    }
}

#[cfg(feature = "proto")]
impl ScopeCreateError {
    /// This failure's variant, for the warn-once-per-episode record on
    /// [`ScopePool::last_create_warn`].
    pub fn kind(&self) -> ScopeCreateErrorKind {
        match self {
            Self::InvalidConnectionRequest(_) => ScopeCreateErrorKind::InvalidConnectionRequest,
            Self::InvalidLibName => ScopeCreateErrorKind::InvalidLibName,
            Self::NoSeedAddress => ScopeCreateErrorKind::NoSeedAddress,
            Self::InvalidClusterTarget(_) => ScopeCreateErrorKind::InvalidClusterTarget,
            Self::InvalidUnixSocketAddress(_) => ScopeCreateErrorKind::InvalidUnixSocketAddress,
            Self::ParentCertMaterialUnavailable => {
                ScopeCreateErrorKind::ParentCertMaterialUnavailable
            }
            Self::ClientOpenFailed(_) => ScopeCreateErrorKind::ClientOpenFailed,
            Self::ConnectFailed(_) => ScopeCreateErrorKind::ConnectFailed,
            Self::ConnectTimedOut => ScopeCreateErrorKind::ConnectTimedOut,
            Self::IamTokenUnavailable => ScopeCreateErrorKind::IamTokenUnavailable,
            Self::IamRequiresTls => ScopeCreateErrorKind::IamRequiresTls,
            Self::InitFailed(_) => ScopeCreateErrorKind::InitFailed,
            Self::InitTimedOut => ScopeCreateErrorKind::InitTimedOut,
            Self::PoolClosed => ScopeCreateErrorKind::PoolClosed,
        }
    }
}

#[cfg(feature = "proto")]
impl std::fmt::Display for ScopeCreateError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidConnectionRequest(e) => write!(f, "invalid connection request: {e}"),
            Self::InvalidLibName => f.write_str("invalid lib_name"),
            Self::NoSeedAddress => f.write_str("connection request has no seed address"),
            Self::InvalidClusterTarget(addr) => {
                write!(f, "cluster target is not a valid host:port: {addr}")
            }
            Self::ParentCertMaterialUnavailable => f.write_str(
                "parent client's TLS certificate material is not available; connect the parent client before taking a scope",
            ),
            Self::InvalidUnixSocketAddress(reason) => {
                write!(f, "invalid Unix domain socket address: {reason}")
            }
            Self::ClientOpenFailed(e) => write!(f, "client open failed: {e}"),
            Self::ConnectFailed(e) => write!(f, "connect failed: {e}"),
            Self::ConnectTimedOut => write!(f, "connect timed out after {SCOPE_CONNECT_TIMEOUT:?}"),
            Self::IamTokenUnavailable => f.write_str("IAM token unavailable; cannot AUTH"),
            Self::IamRequiresTls => f.write_str(
                "IAM authentication requires TLS; refusing to send an IAM token over a plaintext connection",
            ),
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
    /// Credential generation the initial AUTH was built from: the IAM token
    /// generation for an IAM client, otherwise `CredentialsHandle::generation`
    /// (`AuthGeneration::NEVER` when the credentials came from the request bytes,
    /// i.e. no owning Client was passed).
    initial_auth_generation: AuthGeneration,
}

/// Strip surrounding `[ ]` from an IPv6 host. A bracketed literal is a URL-authority
/// convention; redis-rs hands the host to `lookup_host((host, port))`, which wants a
/// bare `::1`. Every connection path — the parent client (`client::get_connection_info`)
/// and both scope branches — trims through here so a configured `[::1]` works everywhere.
pub(crate) fn strip_host_brackets(host: &str) -> &str {
    host.trim_start_matches('[').trim_end_matches(']')
}

/// Parse a `ClusterPrimary` `host:port` target into `(host, port)`. The IPv6 host
/// may or may not be bracketed depending on the source, so split and trim exactly as
/// `cluster.rs`'s `get_connection_info` does, so the two paths cannot drift.
#[cfg(feature = "proto")]
fn parse_cluster_target(addr: &str) -> Option<(String, u16)> {
    addr.rsplit_once(':').and_then(|(host, port)| {
        Some(strip_host_brackets(host))
            .filter(|h| !h.is_empty())
            .zip(u16::from_str(port).ok())
            .map(|(host, port)| (host.to_string(), port))
    })
}

/// Build a `redis::ConnectionAddr` from a host/port and TLS mode. Mirrors only the
/// TLS-mode mapping of `client::get_connection_info`; `tls_params` carries the
/// parent client's certificate material so a scope off an mTLS parent presents the
/// same client certificate and trusts the same roots.
///
/// Scoped connections do apply the parent's `address_resolver`, but only to a
/// standalone seed address: a `ClusterPrimary` target is read from the parent's
/// slot map, which the resolver already rewrote, so resolving it again here would
/// resolve the address twice.
#[cfg(feature = "proto")]
fn build_scope_connection_addr(
    host: String,
    port: u16,
    tls_mode: crate::connection_request::TlsMode,
    tls_params: Option<redis::TlsConnParams>,
) -> redis::ConnectionAddr {
    use crate::connection_request::TlsMode;
    match tls_mode {
        TlsMode::NoTls => redis::ConnectionAddr::Tcp(host, port),
        _ => redis::ConnectionAddr::TcpTls {
            host,
            port,
            insecure: tls_mode == TlsMode::InsecureTls,
            tls_params,
        },
    }
}

/// The address of a standalone scope whose seed is a Unix domain socket, or `None` for
/// a TCP seed: a parent configured with a socket opens its scopes over the same socket.
#[cfg(feature = "proto")]
fn unix_socket_scope_addr(
    seed: &crate::connection_request::NodeAddress,
    tls_mode: crate::connection_request::TlsMode,
) -> Result<Option<redis::ConnectionAddr>, ScopeCreateError> {
    let address = crate::client::NodeAddress::from(seed);
    let Some(path) = address.unix_socket_path.clone() else {
        return Ok(None);
    };
    let tls = tls_mode != crate::connection_request::TlsMode::NoTls;
    crate::client::validate_unix_socket_address(&address, tls)
        .map_err(ScopeCreateError::InvalidUnixSocketAddress)?;
    Ok(Some(redis::ConnectionAddr::Unix(path)))
}

/// The certificate material a scope inherits from its parent.
///
/// The reload handle wins over the parent's `tls_params`: that snapshot is frozen
/// at parent-connect, so it is empty for a lazy parent and stale after a rotation.
/// A scope must present the freshest adopted material, not the certificate the
/// parent started with. The snapshot is the fallback for inline (byte-based) cert
/// material, which configures no reload handle.
#[cfg(feature = "proto")]
async fn inherited_tls_params(client: Option<&Client>) -> Option<redis::TlsConnParams> {
    match client.and_then(|c| c.cert_reload_handle()) {
        Some(handle) => Some(handle.current_params().await),
        None => client.and_then(|c| c.tls_params()),
    }
}

/// Open and initialize a connection to `target`, without touching the pool.
///
/// Pure pipeline: parse request → validate lib name → build ConnectionInfo →
/// connect → AUTH/SELECT/CLIENT SETNAME. Any step failing short-circuits with the
/// reason; reservation accounting is the caller's job.
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

    // Build a structured `ConnectionInfo` directly rather than a `redis://` URL,
    // which would require a bare IPv6 host to be bracketed. Every other connection
    // path in glide-core avoids the URL layer for this reason.
    // An unrecognized tls_mode must fail closed: default to TLS, never plaintext.
    let tls_mode = proto
        .tls_mode
        .enum_value()
        .unwrap_or(crate::connection_request::TlsMode::SecureTls);
    // An IAM token is a bearer credential: never transmit it over plaintext. When
    // the parent authenticates as an IAM identity, reject a no-TLS scope before any
    // connect or token fetch, so a misconfigured scope fails closed instead of
    // leaking the token in a cleartext AUTH on the first command / reconnect.
    if tls_mode == crate::connection_request::TlsMode::NoTls
        && client.and_then(|c| c.iam_token_manager()).is_some()
    {
        return Err(ScopeCreateError::IamRequiresTls);
    }
    let tls_params = inherited_tls_params(client).await;
    // The parent configured certificate material, but none is reachable yet (a
    // lazy parent that has not connected). Connecting would downgrade to system
    // trust with no client certificate, so fail the acquire instead. Read off the
    // parent, not the scope request: a caller-supplied request need not repeat the
    // cert fields, and the parent is the authority on what was configured.
    if tls_mode != crate::connection_request::TlsMode::NoTls
        && tls_params.is_none()
        && client
            .map(|c| c.configures_cert_material())
            .unwrap_or(false)
    {
        return Err(ScopeCreateError::ParentCertMaterialUnavailable);
    }
    let connection_addr = match target {
        ScopeTarget::Standalone => {
            let addr = proto
                .addresses
                .first()
                .ok_or(ScopeCreateError::NoSeedAddress)?;
            if let Some(unix_addr) = unix_socket_scope_addr(addr, tls_mode)? {
                unix_addr
            } else {
                let port = if addr.port == 0 {
                    6379
                } else {
                    addr.port as u16
                };
                // Trim brackets here too, so a configured `[::1]` standalone host works
                // like the cluster branch — redis-rs's tuple `lookup_host` wants bare `::1`.
                let host = strip_host_brackets(&addr.host);
                // The seed is still the raw configured address, resolved here the same
                // way `client::get_connection_info` does. The cluster arm must not.
                let (host, port) = match client.and_then(|c| c.address_resolver()) {
                    Some(resolver) => resolver.resolve(host, port),
                    None => (host.to_string(), port),
                };
                build_scope_connection_addr(host, port, tls_mode, tls_params)
            }
        }
        ScopeTarget::ClusterPrimary(addr) => {
            let (host, port) = parse_cluster_target(addr)
                .ok_or_else(|| ScopeCreateError::InvalidClusterTarget(Arc::clone(addr)))?;
            build_scope_connection_addr(host, port, tls_mode, tls_params)
        }
    };
    let redis_client = redis::Client::open(redis::ConnectionInfo {
        addr: connection_addr,
        // The old `redis://` URL carried no `resp3` param, so redis-rs parsed it
        // as RESP2; `RedisConnectionInfo::default()` is RESP3, which would change
        // the handshake. Everything else (db/auth) is set by the init pipeline below.
        redis: redis::RedisConnectionInfo {
            protocol: redis::ProtocolVersion::RESP2,
            ..Default::default()
        },
    })
    .map_err(ScopeCreateError::ClientOpenFailed)?;
    let opts = redis::GlideConnectionOptions {
        push_sender: None,
        disconnect_notifier: None,
        discover_az: false,
        connection_timeout: Some(SCOPE_CONNECT_TIMEOUT),
        connection_retry_strategy: None,
        tcp_nodelay: true,
        pubsub_synchronizer: None,
        iam_token_provider: None,
        // A plain multiplexed connection has no reconnect loop to re-read this on
        // (the scope does not go through `ReconnectingConnection`), and only the
        // cluster path reads the provider at all, so it is inert here either way.
        // The scope takes the freshest material at creation via `current_params()`.
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
    // detect whether the credential has rotated again since this connection's
    // initial AUTH (rather than always re-authenticating on the very first
    // command, or missing a rotation that lands between here and the first
    // command).
    let mut initial_auth_generation = AuthGeneration::NEVER;

    // AUTH: IAM authentication takes priority when configured on the parent client
    // (matching the documented priority in `AuthenticationInfo`'s doc comment).
    // Otherwise the owning Client's current credentials: the request bytes are
    // the pool's first-acquire snapshot and never see a later
    // `update_connection_password`. Only the raw test path, which has no Client,
    // reads them.
    if let Some(manager) = client.and_then(|c| c.iam_token_manager()) {
        // Read the generation before the token: if a refresh lands in this gap,
        // `initial_auth_generation` records the (now-stale) pre-refresh generation,
        // so the mismatch on the first scope command still triggers a
        // reauthentication. Reading generation after the token could otherwise
        // suppress it — a refresh landing there would mean the initial AUTH used
        // the old token, but the recorded generation already matches the new one.
        let generation_before_auth = AuthGeneration::from_source(manager.token_generation());
        let current_token = manager.get_token().await;
        if current_token.is_empty() {
            return Err(ScopeCreateError::IamTokenUnavailable);
        }
        initial_auth_generation = generation_before_auth;
        init_pipe
            .cmd("AUTH")
            .arg(manager.username())
            .arg(current_token.as_str());
        init_count += 1;
    } else if let Some(handle) = client.map(|c| c.credentials()) {
        initial_auth_generation = handle.generation();
        if let Some(auth_cmd) = handle.current().auth_command() {
            init_pipe.add_command(auth_cmd);
            init_count += 1;
        }
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
        let init_result = tokio::time::timeout(
            SCOPE_CONNECT_TIMEOUT,
            conn.send_packed_commands(&init_pipe, 0, init_count),
        )
        .await;
        let replies_ok = crate::pool::pipeline_replies_ok(&init_result);
        match init_result {
            Ok(Ok(_)) if replies_ok => {}
            Ok(Ok(replies)) => {
                // Init rejected by the server (see pipeline_replies_ok) — surface the
                // embedded error rather than recording a db we never selected.
                let err = replies
                    .into_iter()
                    .find_map(|v| match v {
                        redis::Value::ServerError(e) => Some(redis::RedisError::from(e)),
                        _ => None,
                    })
                    .unwrap_or_else(|| {
                        redis::RedisError::from((
                            redis::ErrorKind::ResponseError,
                            "scope connection initialization failed",
                        ))
                    });
                return Err(ScopeCreateError::InitFailed(err));
            }
            Ok(Err(e)) => return Err(ScopeCreateError::InitFailed(e)),
            Err(_) => return Err(ScopeCreateError::InitTimedOut),
        }
    }

    Ok(PreparedScopeConnection {
        connection: conn,
        database_id,
        initial_auth_generation,
    })
}

/// Create a new scope connection in the background and add it to the pool.
///
/// The caller hands over the `max_total` reservation (see
/// [`ScopeAcquire::Reserved`]). On success it is committed as the seated
/// connection; on any failure — including a cancel or panic mid-await — the
/// guard's `Drop` gives the slot back. Failures are logged once, naming the
/// target and the cause, and returned so the acquire that reserved the slot
/// can stop waiting on a cause that will not clear.
///
/// # Arguments
/// - `pool`: Arc to the scope pool (locked async)
/// - `client`: Optional reference to the parent Client (for IAM and runtime DB)
/// - `connection_request_bytes`: Serialized protobuf ConnectionRequest
/// - `target`: Standalone server or concrete cluster primary
/// - `reservation`: the reservation guard for the slot being filled
#[cfg(feature = "proto")]
pub async fn create_scope_connection(
    pool: Arc<TokioMutex<ScopePool>>,
    client: Option<&Client>,
    connection_request_bytes: &[u8],
    target: ScopeTarget,
    reservation: crate::pool::ScopeReservation,
) -> Result<(), ScopeCreateErrorKind> {
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
            if pool_guard.last_create_warn.take().is_some() {
                glide_logger::log_debug(
                    "create_scope_connection",
                    format!("scoped connection to {target:?} created again"),
                );
            }
            let scope_id = pool_guard.next_id();
            pool_guard.idle.push_back(ScopedConnection {
                scope_id,
                connection: prepared.connection,
                created_at: Instant::now(),
                last_idle_at: Instant::now(),
                borrowed_at: None,
                state: ConnectionState::with_configured_db(prepared.database_id),
                pinned_slot: None,
                // Freshly created and seated idle; the acquisition slot is set when
                // a borrow picks it up.
                acquisition_slot: None,
                target,
                last_auth_generation: prepared.initial_auth_generation,
            });
            reservation.commit();
            Ok(())
        }
        Err(err) => {
            // The uncommitted `reservation` gives the slot back on drop. A pool
            // shutting down is expected, not a fault. Everything else warns once
            // per kind and logs the repeats at debug, since an acquire that keeps
            // retrying a persistent cause (a lazy mTLS parent, an unreachable
            // shard) would otherwise warn on every attempt. Same shape as
            // `log_unresolved_target`.
            let message = format!("scoped connection to {target:?} not created: {err}");
            let kind = err.kind();
            if matches!(err, ScopeCreateError::PoolClosed) {
                glide_logger::log_debug("create_scope_connection", message);
            } else {
                let repeated = pool_guard.last_create_warn == Some(kind);
                pool_guard.last_create_warn = Some(kind);
                if repeated {
                    glide_logger::log_debug("create_scope_connection", message);
                } else {
                    glide_logger::log_warn("create_scope_connection", message);
                }
            }
            Err(kind)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCOPE ACQUIRE / RELEASE (NON-BLOCKING WRAPPERS)
// ═══════════════════════════════════════════════════════════════════════════════

/// Why one acquire poll did not hand out a scope but a later poll may.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScopeRetryCause {
    /// Work this acquire is waiting on is in progress: its connection is being
    /// created, an idle connection is being re-`SELECT`ed, or another acquire
    /// briefly holds the pool lock.
    Pending,
    Exhausted,
    /// The routing slot has no mapped primary yet, or the topology is being
    /// refreshed.
    TargetUnresolved,
    /// This acquire's own creation failed for a reason that may not recur; the
    /// next attempt reserves and dials again.
    CreateFailed(ScopeCreateErrorKind),
}

/// Why no poll of this acquire can succeed until something outside the retry
/// loop changes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, strum_macros::EnumIter)]
pub enum ScopeFailCause {
    /// The binding has not registered the parent client, or has closed it.
    ParentUnregistered,
    /// The parent is configured with custom TLS certificate material but has not
    /// connected yet, so there is nothing for a scoped connection to inherit.
    ParentCertMaterialUnavailable,
    /// The stored connection request cannot produce a scoped connection
    /// (unparseable, no seed address, invalid `lib_name` or cluster target).
    InvalidConfiguration,
    /// IAM authentication is configured but the scope request disables TLS. Kept
    /// distinct from `InvalidConfiguration` so the borrower-facing error names
    /// the TLS requirement instead of a generic configuration failure.
    IamRequiresTls,
    /// The pool is closing or closed; it will not serve again.
    PoolClosed,
}

/// Result of one acquire attempt, split by what the caller should do next.
/// Every cause the acquire path can hit is classified here, in one place, so
/// the retry decision and the error name cannot drift apart.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScopeAcquireOutcome {
    Acquired(u64),
    Retry(ScopeRetryCause),
    Fail(ScopeFailCause),
}

/// What one classification produced: a plain outcome, or a creation this
/// acquire reserved the slot for and must wait on. Kept apart from
/// [`ScopeAcquireOutcome`] because a [`tokio::task::JoinHandle`] is neither
/// `Copy` nor comparable, and only the waiting path needs it.
#[cfg(feature = "proto")]
enum Classified {
    Outcome(ScopeAcquireOutcome),
    /// The slot is reserved and `create_scope_connection` is running in this
    /// task; its result says whether to re-check idle or stop.
    Creating(tokio::task::JoinHandle<Result<(), ScopeCreateErrorKind>>),
}

/// The slot a borrow must constrain its commands to, if any.
///
/// Only a cluster scope acquired with a caller-supplied routing key is
/// constrained; a keyless cluster scope (`None`) and any standalone scope are
/// unconstrained, so their first command is accepted as the pin.
#[cfg(feature = "proto")]
fn acquisition_slot_for(target: &ScopeTarget, routing_slot: Option<u16>) -> Option<u16> {
    match target {
        ScopeTarget::ClusterPrimary(_) => routing_slot,
        _ => None,
    }
}

/// Test-only single attempt: one pass of the classifier under `try_lock`, so a
/// cause that needs time to clear is reported as [`ScopeAcquireOutcome::Retry`]
/// instead of waited on. Lets a test observe what one attempt sees (for example
/// the first attempt reporting `NeedsResync`) without the wait that
/// [`acquire_scope`] adds. Not an entry point: no native interface calls it.
///
/// `routing_slot` determines which cluster node the scope connects to. In cluster
/// mode, pass `Some(slot)` with the hash slot of the key(s) the scope will operate
/// on, or `None` when the caller supplied no routing key (any primary is
/// acceptable). `Some(slot)` additionally requires every keyed command — including
/// the first — to hash to it; `None` leaves the scope unconstrained, like a keyless
/// cluster command's `Random` routing. In standalone mode this parameter is ignored
/// (all slots route to the same node).
///
/// A creation this attempt reserved a slot for keeps running after the attempt
/// returns [`ScopeRetryCause::Pending`]; it seats its connection in idle for
/// the next attempt (or another borrower) to reuse. Only [`acquire_scope`]
/// waits on the creation itself.
#[cfg(all(test, feature = "proto"))]
pub(crate) fn acquire_scope_outcome(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
    runtime: &tokio::runtime::Handle,
    routing_slot: Option<u16>,
) -> ScopeAcquireOutcome {
    let handle = scope_pool_for(client_id, connection_request_bytes);
    let Ok(pool) = handle.pool.try_lock() else {
        return ScopeAcquireOutcome::Retry(ScopeRetryCause::Pending);
    };
    match classify_acquire(&handle.pool, pool, client_id, runtime, routing_slot) {
        Classified::Outcome(outcome) => outcome,
        Classified::Creating(_) => ScopeAcquireOutcome::Retry(ScopeRetryCause::Pending),
    }
}

/// Why `acquire_scope` returned no scope. Owns the message text and the
/// [`RequestErrorType`] a binding turns into its exception class, so neither is
/// written per language.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScopeAcquireError {
    /// The deadline passed while every attempt was retryable; `last` is the
    /// cause of the final attempt, which is the one worth reporting.
    DeadlineExceeded { last: ScopeRetryCause },
    /// An attempt hit a cause no amount of waiting clears.
    Terminal(ScopeFailCause),
    /// The watchdog backstop fired before the core deadline did, so the runtime
    /// was too starved to run its own timer and the cause of the wait is
    /// unknown.
    RuntimeStalled,
}

impl ScopeAcquireError {
    /// `Timeout` for the deadline. Terminal causes are `Disconnect` when the
    /// parent or pool is gone (every binding maps that to its closed-client
    /// error) and `Unspecified` for a configuration problem (the generic request
    /// error, whose message carries the specifics). No new variant is added to
    /// the `#[repr(C)]` enum, so the native interfaces are unchanged here.
    pub fn request_error_type(self) -> RequestErrorType {
        match self {
            Self::DeadlineExceeded { .. } | Self::RuntimeStalled => RequestErrorType::Timeout,
            Self::Terminal(ScopeFailCause::ParentUnregistered | ScopeFailCause::PoolClosed) => {
                RequestErrorType::Disconnect
            }
            Self::Terminal(
                ScopeFailCause::ParentCertMaterialUnavailable
                | ScopeFailCause::InvalidConfiguration
                | ScopeFailCause::IamRequiresTls,
            ) => RequestErrorType::Unspecified,
        }
    }
}

/// The native interfaces deliver every command failure as a `RedisError` run
/// through [`crate::errors::error_type`], so the acquire error travels the same
/// way rather than growing a second delivery path per language. The `io` kinds
/// are chosen so `error_type` lands on the same [`RequestErrorType`] as
/// [`ScopeAcquireError::request_error_type`]; a test pins the two together.
impl From<ScopeAcquireError> for RedisError {
    fn from(err: ScopeAcquireError) -> Self {
        let message = err.to_string();
        match err.request_error_type() {
            RequestErrorType::Timeout => {
                std::io::Error::new(std::io::ErrorKind::TimedOut, message).into()
            }
            RequestErrorType::Disconnect => {
                std::io::Error::new(std::io::ErrorKind::NotConnected, message).into()
            }
            _ => RedisError::from((redis::ErrorKind::ClientError, "scope acquire", message)),
        }
    }
}

impl std::fmt::Display for ScopeRetryCause {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Pending => f.write_str("scoped connection still being prepared"),
            Self::Exhausted => f.write_str("pool exhausted"),
            Self::TargetUnresolved => {
                f.write_str("routing slot has no mapped primary or the topology is refreshing")
            }
            Self::CreateFailed(kind) => {
                write!(f, "last scoped connection attempt failed: {kind:?}")
            }
        }
    }
}

impl std::fmt::Display for ScopeFailCause {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::ParentUnregistered => f.write_str("parent client is closed or not registered"),
            Self::ParentCertMaterialUnavailable => f.write_str(
                "parent client's TLS certificate material is not available; \
                 connect the parent client before taking a scope",
            ),
            Self::InvalidConfiguration => {
                f.write_str("connection configuration cannot produce a scoped connection")
            }
            Self::IamRequiresTls => f.write_str(
                "IAM authentication requires TLS; refusing to send an IAM token over a plaintext connection",
            ),
            Self::PoolClosed => f.write_str("scope pool is closed"),
        }
    }
}

impl std::fmt::Display for ScopeAcquireError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::DeadlineExceeded { last } => {
                write!(f, "timed out waiting for isolated scope ({last})")
            }
            Self::Terminal(cause) => write!(f, "cannot acquire isolated scope: {cause}"),
            Self::RuntimeStalled => f.write_str(
                "timed out waiting for isolated scope (runtime stalled; watchdog backstop fired)",
            ),
        }
    }
}

impl std::error::Error for ScopeAcquireError {}

/// Upper bound on one wait between attempts. One retry cause produces no pool
/// event when it clears: the parent client refreshing its slot map
/// (`TargetUnresolved`). The bound keeps that case from waiting out the whole
/// deadline; everything else wakes on [`ScopePoolHandle::wakeup`] immediately.
#[cfg(feature = "proto")]
const SCOPE_ACQUIRE_WAIT_CAP: Duration = Duration::from_millis(50);

/// First wait after a retryable creation failure, doubling per consecutive
/// failure up to [`SCOPE_REDIAL_BACKOFF_CAP`]. A scoped connection dials with
/// `connection_retry_strategy: None`, so a refused connect or a fast init error
/// (WRONGPASS, max clients) fails within a millisecond; without a wait one
/// acquire redials as fast as the runtime can spin until its deadline, against a
/// server that is already refusing. The deleted binding loops waited 1 to
/// 500 ms between attempts; this keeps that bound in core.
#[cfg(feature = "proto")]
const SCOPE_REDIAL_BACKOFF_START: Duration = Duration::from_millis(10);
#[cfg(feature = "proto")]
const SCOPE_REDIAL_BACKOFF_CAP: Duration = Duration::from_millis(200);

/// Acquire a scope, waiting up to `deadline` for a retryable cause to clear.
///
/// One call, one result: the wait lives here, not in the caller, so the
/// retry/terminal decision and the backoff exist once. Each attempt runs
/// `classify_acquire` under the awaited pool lock, so lock contention is a
/// wait rather than a retry. Between attempts the call sleeps on
/// [`ScopePoolHandle::wakeup`], armed before the attempt so a release that lands
/// between classifying and sleeping is not missed, capped by
/// `SCOPE_ACQUIRE_WAIT_CAP` and the remaining deadline. After a dial of its own
/// fails it instead sleeps (`SCOPE_REDIAL_BACKOFF_START` doubling to
/// `SCOPE_REDIAL_BACKOFF_CAP`) with no early wake, since every failed dial also
/// fires the wakeup; a release during that sleep waits at most one step. A
/// terminal cause returns at once.
///
/// Cancel-safe: dropping the future between attempts leaks nothing, and a
/// creation spawned by an earlier attempt finishes on its own and seats its
/// connection in idle for the next borrower.
#[cfg(feature = "proto")]
pub async fn acquire_scope(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
    runtime: &tokio::runtime::Handle,
    routing_slot: Option<u16>,
    deadline: Duration,
) -> Result<u64, ScopeAcquireError> {
    acquire_scope_with_wait_cap(
        client_id,
        connection_request_bytes,
        runtime,
        routing_slot,
        deadline,
        SCOPE_ACQUIRE_WAIT_CAP,
    )
    .await
}

/// How much later than the core deadline the watchdog backstop fires on a scope
/// acquire. Without a margin the watchdog's OS thread often observes the shared
/// deadline before the tokio timer does, and the caller gets a bare "timed out"
/// instead of the core's cause ("pool exhausted"). Costs one second on a starved
/// runtime, where the backstop is the only timer that fires.
#[cfg(feature = "proto")]
pub const SCOPE_ACQUIRE_WATCHDOG_MARGIN: Duration = Duration::from_secs(1);

/// Log the watchdog's diagnostic for a scoped operation that outlived its
/// deadline. One place for the cause classification and the event shape, so
/// the acquire path here and the C ABI's execute race sites cannot drift. `p99`
/// comes from the parent client's latency tracker when the caller has one;
/// `None` leaves the suggested timeout unset.
#[cfg(feature = "proto")]
pub fn log_scope_watchdog_timeout(
    command: &'static str,
    configured_timeout: Duration,
    actual_elapsed: Duration,
    p99: Option<Duration>,
) {
    use crate::timeout_watchdog::{CommandPhase, TimeoutCause, TimeoutEvent};
    let pending = crate::timeout_watchdog::pending_count();
    let cause = if pending > 100 {
        TimeoutCause::SystemOverload {
            pending_total: pending,
        }
    } else {
        TimeoutCause::ServerUnresponsive {
            node: "scope".to_owned(),
        }
    };
    let event = TimeoutEvent {
        cause,
        command,
        node: "scope".to_owned(),
        phase: CommandPhase::Sent,
        configured_timeout,
        actual_elapsed,
        pending_commands: pending,
        recent_p99_latency: p99,
        rss_bytes: crate::timeout_watchdog::get_rss(),
        suggested_timeout: p99.map(|p| (p * 3).max(configured_timeout)),
        inflight_at_register: None,
        inflight_at_timeout: None,
        retry_count: 0,
    };
    glide_logger::log_warn("timeout_watchdog", event.to_string());
}

/// [`acquire_scope`] under the [`TimeoutWatchdog`](crate::timeout_watchdog::TimeoutWatchdog)
/// backstop (#6673, #5806). The core deadline is a tokio timer, which a starved
/// runtime may never run; the watchdog's own OS thread fires at
/// `deadline + SCOPE_ACQUIRE_WATCHDOG_MARGIN` and the caller gets
/// [`ScopeAcquireError::RuntimeStalled`]. Always armed: an acquire has a finite
/// deadline, unlike the unbounded commands `should_arm_watchdog` exists for.
/// Every native interface awaits this one future, so no binding's wait is
/// unbounded and the deadline semantics cannot drift between them.
#[cfg(feature = "proto")]
pub async fn acquire_scope_with_watchdog(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
    runtime: &tokio::runtime::Handle,
    routing_slot: Option<u16>,
    deadline: Duration,
) -> Result<u64, ScopeAcquireError> {
    let acquire_start = Instant::now();
    let timeout_rx = crate::timeout_watchdog::TimeoutWatchdog::global()
        .register(deadline + SCOPE_ACQUIRE_WATCHDOG_MARGIN, acquire_start);
    let acquire = acquire_scope(
        client_id,
        connection_request_bytes,
        runtime,
        routing_slot,
        deadline,
    );
    tokio::pin!(acquire);
    tokio::select! {
        result = &mut acquire => result,
        recv_result = timeout_rx => {
            match recv_result {
                Err(_) => acquire.await,
                Ok(()) => {
                    let p99 = get_parent_client(client_id)
                        .and_then(|client| client.latency_tracker().p99());
                    log_scope_watchdog_timeout(
                        "SCOPE_ACQUIRE",
                        deadline,
                        acquire_start.elapsed(),
                        p99,
                    );
                    Err(ScopeAcquireError::RuntimeStalled)
                }
            }
        }
    }
}

/// [`acquire_scope`] with the wait cap as a parameter. Tests pass a cap longer
/// than the deadline so that returning before the deadline proves a wakeup
/// fired; with the production cap the timer alone would satisfy the same assert.
#[cfg(feature = "proto")]
async fn acquire_scope_with_wait_cap(
    client_id: u64,
    connection_request_bytes: Vec<u8>,
    runtime: &tokio::runtime::Handle,
    routing_slot: Option<u16>,
    deadline: Duration,
    wait_cap: Duration,
) -> Result<u64, ScopeAcquireError> {
    let started = Instant::now();
    let ScopePoolHandle {
        pool: scope_pool,
        wakeup,
    } = scope_pool_for(client_id, connection_request_bytes);
    let mut redial_backoff = SCOPE_REDIAL_BACKOFF_START;

    loop {
        let notified = wakeup.notified();
        tokio::pin!(notified);
        notified.as_mut().enable();

        let pool = scope_pool.lock().await;
        let remaining = deadline.saturating_sub(started.elapsed());
        match classify_acquire(&scope_pool, pool, client_id, runtime, routing_slot) {
            Classified::Outcome(ScopeAcquireOutcome::Acquired(scope_id)) => return Ok(scope_id),
            Classified::Outcome(ScopeAcquireOutcome::Fail(cause)) => {
                return Err(ScopeAcquireError::Terminal(cause));
            }
            Classified::Outcome(ScopeAcquireOutcome::Retry(last)) => {
                if remaining.is_zero() {
                    return Err(ScopeAcquireError::DeadlineExceeded { last });
                }
                tokio::select! {
                    _ = &mut notified => {}
                    _ = tokio::time::sleep(remaining.min(wait_cap)) => {}
                }
            }
            Classified::Creating(creation) => {
                // This acquire owns the reserved slot, so it waits on its own
                // creation rather than re-checking the pool: one acquire, one
                // dial. On the deadline the task keeps running and seats its
                // connection for the next borrower.
                let created = tokio::select! {
                    created = creation => created,
                    _ = tokio::time::sleep(remaining) => {
                        return Err(ScopeAcquireError::DeadlineExceeded {
                            last: ScopeRetryCause::Pending,
                        });
                    }
                };
                // A seated connection is taken by the next pass at once. A
                // retryable failure, or a panicked task whose guard gave the slot
                // back, both redial, so both wait first.
                let failed_kind = match created {
                    Ok(Ok(())) => continue,
                    Ok(Err(kind)) => match kind.fail_cause() {
                        Some(cause) => return Err(ScopeAcquireError::Terminal(cause)),
                        None => Some(kind),
                    },
                    Err(_) => None,
                };
                let remaining = deadline.saturating_sub(started.elapsed());
                if remaining.is_zero() {
                    return Err(ScopeAcquireError::DeadlineExceeded {
                        last: failed_kind.map_or(ScopeRetryCause::Pending, |kind| {
                            ScopeRetryCause::CreateFailed(kind)
                        }),
                    });
                }
                // No early wake: every failed dial's `ScopeReservation::drop`
                // fires `wakeup`, so concurrent acquires would cut each other's
                // backoff short and redial in lockstep. A release that lands
                // during the backoff waits at most one step.
                tokio::time::sleep(remaining.min(redial_backoff)).await;
                redial_backoff = (redial_backoff * 2).min(SCOPE_REDIAL_BACKOFF_CAP);
            }
        }
    }
}

#[cfg(feature = "proto")]
fn scope_pool_for(client_id: u64, connection_request_bytes: Vec<u8>) -> ScopePoolHandle {
    // Fast path: check if scope pool exists before cloning bytes
    let pools = crate::pool::get_client_scope_pools();
    match pools.get(&client_id) {
        Some(existing) => existing.value().clone(),
        None => crate::pool::get_or_create_scope_pool(client_id, connection_request_bytes),
    }
}

/// The classification step behind [`acquire_scope`] (and the test-only single
/// attempt `acquire_scope_outcome`). Takes the guard by value because the
/// `NeedsResync` arm releases it before spawning.
#[cfg(feature = "proto")]
fn classify_acquire(
    scope_pool: &Arc<TokioMutex<ScopePool>>,
    mut pool: tokio::sync::MutexGuard<'_, ScopePool>,
    client_id: u64,
    runtime: &tokio::runtime::Handle,
    routing_slot: Option<u16>,
) -> Classified {
    use Classified::{Creating, Outcome};
    use ScopeAcquireOutcome::{Acquired, Fail, Retry};

    let registry = get_scope_registry();

    if pool.state.load(Ordering::Acquire) != crate::pool::POOL_RUNNING {
        return Outcome(Fail(ScopeFailCause::PoolClosed));
    }

    // A no-routing-key acquire arrives as None; it still needs a concrete
    // primary to dial, so target resolution uses slot 0 (any primary is
    // acceptable), while command validation below stays unconstrained.
    let connection_slot = routing_slot.unwrap_or(0);
    // Resolve the slot's current primary before touching the pool so a
    // stale or unmapped slot never matches (or creates) a connection to the
    // wrong node. Unresolved never means "use the seed"; whether it means
    // "retry" depends on the cause (see `ScopeTargetUnresolved`).
    let client = get_parent_client(pool.parent_client_id);
    let target = match try_resolve_scope_target(client.as_ref(), connection_slot) {
        Ok(target) => {
            if let Some(cleared) = pool.last_unresolved_target.take() {
                glide_logger::log_debug(
                    "acquire_scope_outcome",
                    format!(
                        "client {client_id}: scope target resolves again \
                         (was: {cleared})"
                    ),
                );
            }
            target
        }
        Err(cause) => {
            log_unresolved_target(&mut pool, client_id, connection_slot, cause);
            return Outcome(match cause {
                ScopeTargetUnresolved::ParentUnregistered => {
                    Fail(ScopeFailCause::ParentUnregistered)
                }
                ScopeTargetUnresolved::SlotUnmapped(_) | ScopeTargetUnresolved::TopologyLocked => {
                    Retry(ScopeRetryCause::TargetUnresolved)
                }
            });
        }
    };

    // The database the borrowed connection must be on: the parent client's
    // current runtime database (updated by runtime SELECT). Derived from the
    // resolved parent so it fails closed — an unresolvable parent yields no
    // scope rather than silently defaulting to db 0. Target resolution above
    // already guarantees the parent is registered; `client` is the same local,
    // so this arm is a fail-closed guard, not a reachable path.
    let Some(runtime_db) = client.as_ref().map(|c| c.current_database()) else {
        return Outcome(Fail(ScopeFailCause::ParentUnregistered));
    };
    let acquisition_slot = acquisition_slot_for(&target, routing_slot);
    match pool.try_acquire(registry, target.clone(), runtime_db, acquisition_slot) {
        ScopeAcquire::Reused(scope_id) => {
            let _ = glide_telemetry::GlideOpenTelemetry::record_scope_acquire();
            Outcome(Acquired(scope_id))
        }
        ScopeAcquire::Reserved(reservation) => {
            // The guard moves into the creation task, so an early return,
            // cancel, or panic there gives the slot back.
            let pool_clone = scope_pool.clone();
            let conn_bytes = pool.connection_request_bytes.clone();
            Creating(runtime.spawn(async move {
                create_scope_connection(
                    pool_clone,
                    client.as_ref(),
                    &conn_bytes,
                    target,
                    reservation,
                )
                .await
            }))
        }
        ScopeAcquire::NeedsResync => {
            // The only reusable connection for this target is on a different
            // database. Re-SELECT it onto the runtime database off-lock, then
            // retry — the borrower never sees a connection on the wrong database.
            let pool_clone = scope_pool.clone();
            drop(pool);
            runtime.spawn(async move {
                resync_idle_connection_database(pool_clone, target, runtime_db).await;
            });
            Outcome(Retry(ScopeRetryCause::Pending))
        }
        ScopeAcquire::Exhausted => Outcome(Retry(ScopeRetryCause::Exhausted)),
    }
}

/// Re-`SELECT` one slot-matching idle connection that is on the wrong database onto
/// `runtime_db`, off the pool lock, then return it to idle. Backs the
/// [`ScopeAcquire::NeedsResync`] retry path in [`acquire_scope`]. If the
/// SELECT fails, the connection is discarded (its slot reclaimed).
#[cfg(feature = "proto")]
pub(crate) async fn resync_idle_connection_database(
    pool: Arc<TokioMutex<ScopePool>>,
    target: ScopeTarget,
    runtime_db: u32,
) {
    // Take the wrong-db connection out of idle, capturing the request timeout in the
    // same critical section so the round-trip below needs no extra lock. Hold a
    // slot guard across the off-lock re-SELECT: the popped connection's slot stays
    // counted, and a cancel/panic mid-await would otherwise leak it (neither
    // `reidle_after_resync` nor the failure decrement runs). `commit()` on success
    // keeps the count (connection returns to idle); `Drop` reclaims the slot when
    // the connection is discarded or the task unwinds.
    let (mut conn, request_timeout, reservation) = {
        let mut guard = pool.lock().await;
        match guard.take_idle_for_resync(target, runtime_db) {
            Some(c) => {
                let reservation = crate::pool::ScopeReservation::for_slot(
                    guard.total_count.clone(),
                    guard.wakeup.clone(),
                );
                (c, guard.config.request_timeout, reservation)
            }
            // Another acquire raced us and took or fixed it; nothing to do.
            None => return,
        }
    };

    // SELECT onto the runtime database off-lock (single round-trip).
    let mut pipe = redis::Pipeline::new();
    pipe.cmd("SELECT").arg(runtime_db.to_string());
    let result = tokio::time::timeout(
        request_timeout * 2,
        conn.connection.send_packed_commands(&pipe, 0, 1),
    )
    .await;

    let mut guard = pool.lock().await;
    // A rejected SELECT surfaces inside the reply, not as an outer Err — see pipeline_replies_ok.
    let resync_ok = crate::pool::pipeline_replies_ok(&result);
    if resync_ok {
        // Record the connection's new actual database and return it to idle.
        conn.state = ConnectionState::with_configured_db(runtime_db);
        conn.last_idle_at = std::time::Instant::now();
        guard.reidle_after_resync(conn);
        reservation.commit();
    }
    // else: re-SELECT failed — drop the connection and let `reservation`'s `Drop`
    // reclaim the slot via `saturating_dec`.
}

/// Release a scope back to the pool. Fire-and-forget, non-blocking.
///
/// Returns 0 on success, -1 if client not found.
pub fn release_scope(scope_id: u64, client_id: u64, runtime: &tokio::runtime::Handle) -> i32 {
    let pools = get_client_scope_pools();
    let scope_pool = match pools.get(&client_id) {
        Some(p) => p.value().pool.clone(),
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
            let _ = glide_telemetry::GlideOpenTelemetry::record_scope_release();
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
/// A waiting acquire re-attempts every few milliseconds until its deadline, so an
/// outage of a few seconds would otherwise produce hundreds of identical warnings
/// per caller. The pool remembers the last cause it reported:
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
        glide_logger::log_debug(
            "acquire_scope_outcome",
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
    glide_logger::log_warn(
        "acquire_scope_outcome",
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
        ScopeAcquireOutcome, ScopeCreateError, ScopeFailCause, ScopeRetryCause,
        acquire_scope_outcome, acquisition_slot_for, build_scope_connection,
        create_scope_connection, execute_scope_command, get_parent_client, inherited_tls_params,
        resolve_scope_parent, try_resolve_scope_target,
    };
    use super::{build_scope_connection_addr, parse_cluster_target, strip_host_brackets};

    use super::Client;
    use crate::client::{ConnectionRequest as ClientRequest, NodeAddress as ClientAddress};

    use crate::connection_request::{ConnectionRequest, NodeAddress};
    use crate::pool::{
        ScopeAcquire, ScopePool, ScopePoolConfig, ScopePoolHandle, ScopeReservation, ScopeTarget,
        ScopeTargetUnresolved, get_client_scope_pools, get_scope_registry,
    };
    use crate::scope::{register_client, unregister_client};

    /// A binding classifies the acquire error by `error_type` of the converted
    /// `RedisError`, so that must agree with `request_error_type` for every
    /// variant; the message text survives the conversion as well.
    #[test]
    fn scope_acquire_error_keeps_its_request_error_type_through_redis_error() {
        use super::{ScopeAcquireError, ScopeCreateErrorKind};
        use strum::IntoEnumIterator;
        // Every terminal cause by iteration, so a variant added later is covered
        // without anyone remembering this list; the deadline cases by hand, since
        // `CreateFailed` carries a payload.
        let errors = ScopeFailCause::iter()
            .map(ScopeAcquireError::Terminal)
            .chain([
                ScopeAcquireError::DeadlineExceeded {
                    last: ScopeRetryCause::Exhausted,
                },
                ScopeAcquireError::DeadlineExceeded {
                    last: ScopeRetryCause::CreateFailed(ScopeCreateErrorKind::ConnectFailed),
                },
                ScopeAcquireError::RuntimeStalled,
            ]);
        for err in errors {
            let converted = redis::RedisError::from(err);
            assert_eq!(
                crate::errors::error_type(&converted),
                err.request_error_type(),
                "{err}"
            );
            assert!(
                converted.to_string().contains(&err.to_string()),
                "message lost: {converted}"
            );
        }
    }

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

    /// Re-read interval for a test reload manager. No test here starts the
    /// background re-read task, so this only has to stay under the over-long-interval
    /// warning.
    const RELOAD_INTERVAL_SECS: u32 = 60;

    /// The matching self-signed pairs the cert-reload tests already use.
    const CERT_A: &str = include_str!("tls_reload/test_data/cert_a.pem");
    const KEY_A: &str = include_str!("tls_reload/test_data/key_a.pem");
    const CERT_B: &str = include_str!("tls_reload/test_data/cert_b.pem");
    const KEY_B: &str = include_str!("tls_reload/test_data/key_b.pem");

    fn test_tls_params(cert: &str, key: &str) -> redis::TlsConnParams {
        redis::retrieve_tls_certificates(redis::TlsCertificates {
            client_tls: Some(redis::ClientTlsConfig {
                client_cert: cert.as_bytes().to_vec(),
                client_key: key.as_bytes().to_vec(),
            }),
            root_cert: Some(cert.as_bytes().to_vec()),
        })
        .expect("test cert/key pair should parse")
    }

    /// A reload manager serving `cert`/`key` from a temp dir. The returned
    /// `TempDir` must outlive the manager.
    async fn test_reload_manager(
        cert: &str,
        key: &str,
    ) -> (tempfile::TempDir, crate::tls_reload::CertReloadManager) {
        let dir = tempfile::tempdir().expect("temp dir");
        let cert_path = dir.path().join("cert.pem");
        let key_path = dir.path().join("key.pem");
        std::fs::write(&cert_path, cert).expect("write cert");
        std::fs::write(&key_path, key).expect("write key");
        let manager = crate::tls_reload::CertReloadManager::new(
            cert_path,
            key_path,
            None,
            Some(RELOAD_INTERVAL_SECS),
        )
        .await
        .expect("matching pair should load");
        (dir, manager)
    }

    /// A TLS request whose seed port is closed.
    fn mtls_request_bytes() -> Vec<u8> {
        let mut request = ConnectionRequest::new();
        request.addresses.push(NodeAddress {
            host: "127.0.0.1".into(),
            port: 1,
            ..Default::default()
        });
        request.tls_mode = crate::connection_request::TlsMode::SecureTls.into();
        request.write_to_bytes().expect("serialize scope request")
    }

    fn reserved_pool(request_bytes: Vec<u8>) -> Arc<TokioMutex<ScopePool>> {
        let pool = ScopePool::new(ScopePoolConfig::default(), request_bytes, 1);
        pool.total_count.store(1, Ordering::Release);
        Arc::new(TokioMutex::new(pool))
    }

    async fn lazy_parent(cluster: bool) -> Client {
        lazy_parent_with_resolver(cluster, None).await
    }

    /// A lazy parent that configured certificate material, so the inherit-or-fail
    /// guard arms, but has not connected, so it has none to hand out yet.
    async fn lazy_mtls_parent() -> Client {
        let request = ClientRequest {
            addresses: vec![ClientAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
                unix_socket_path: None,
            }],
            lazy_connect: true,
            root_certs: vec![CERT_A.into()],
            ..Default::default()
        };

        Client::new(request, None)
            .await
            .expect("lazy client construction does not touch the network")
    }

    async fn lazy_parent_with_resolver(
        cluster: bool,
        address_resolver: Option<Arc<dyn redis::AddressResolver>>,
    ) -> Client {
        let request = ClientRequest {
            addresses: vec![ClientAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
                unix_socket_path: None,
            }],
            cluster_mode_enabled: cluster,
            lazy_connect: true,
            address_resolver,
            ..Default::default()
        };

        Client::new(request, None)
            .await
            .expect("lazy client construction does not touch the network")
    }

    /// The reservation guard `try_acquire` would hand the creation task, bound to
    /// `pool`'s pre-incremented counter (see `reserved_pool`).
    async fn reservation_for(pool: &Arc<TokioMutex<ScopePool>>) -> ScopeReservation {
        ScopeReservation::for_test(pool.lock().await.total_count.clone())
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

        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;

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

    /// The defect's layer: `create_scope_connection` runs in a detached task and
    /// can be cancelled mid-connect. Reserve through the real `try_acquire`, spawn
    /// creation against an endpoint that accepts TCP but never completes the
    /// handshake — so the task parks — then abort it. The guard's `Drop` must
    /// reclaim the slot; pre-guard code leaked it because no give-back path ran on
    /// cancel.
    #[tokio::test]
    async fn cancelled_create_scope_connection_reclaims_slot() {
        // Accept-only listener: the client's TCP connect succeeds at the kernel,
        // but the redis handshake never gets a response, so build_scope_connection
        // parks on its connect/init await (well within SCOPE_CONNECT_TIMEOUT).
        let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("bind");
        let port = listener.local_addr().expect("addr").port();
        let request_bytes = request_bytes("", port);

        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            1,
        )));
        let registry = crate::pool::get_scope_registry();

        let reservation = {
            let mut guard = pool.lock().await;
            match guard.try_acquire(registry, ScopeTarget::Standalone, 0, None) {
                ScopeAcquire::Reserved(r) => r,
                other => panic!("expected a reservation, got {other:?}"),
            }
        };
        assert_eq!(pool.lock().await.total_count.load(Ordering::Acquire), 1);

        let pool_clone = pool.clone();
        let handle = tokio::spawn(async move {
            let _ = create_scope_connection(
                pool_clone,
                None,
                &request_bytes,
                ScopeTarget::Standalone,
                reservation,
            )
            .await;
        });
        tokio::task::yield_now().await;
        handle.abort();
        let _ = handle.await;

        let pool = pool.lock().await;
        assert_eq!(
            pool.total_count.load(Ordering::Acquire),
            0,
            "a cancelled create must give the reserved slot back, not leak it"
        );
        assert!(pool.idle.is_empty());
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

    /// Like [`responsive_endpoint`], but answers `extra_ok_replies` further commands
    /// with `+OK` after the handshake before falling silent — so a test can drive N
    /// commands cleanly and observe the (N+1)th hang on a reply that never comes.
    fn endpoint_answering_n_more_commands(
        extra_ok_replies: usize,
    ) -> (u16, Sender<()>, JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind test listener");
        let port = listener.local_addr().expect("listener address").port();
        let (shutdown_sender, shutdown_receiver) = mpsc::channel();
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept scope connection");
            stream
                .set_read_timeout(Some(std::time::Duration::from_secs(10)))
                .expect("set read timeout");
            let mut request = [0_u8; 1024];
            let bytes_read = stream.read(&mut request).expect("read startup commands");
            assert!(bytes_read > 0, "startup commands must not be empty");
            stream
                .write_all(b"+OK\r\n+OK\r\n")
                .expect("respond to startup commands");

            // Each client write here is one packed RESP command; read it and
            // answer +OK, then move on.
            for _ in 0..extra_ok_replies {
                let mut buf = [0_u8; 1024];
                match stream.read(&mut buf) {
                    Ok(n) if n > 0 => {}
                    _ => break,
                }
                if stream.write_all(b"+OK\r\n").is_err() {
                    break;
                }
            }
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

            let reservation = reservation_for(&pool).await;
            let _ = create_scope_connection(
                pool.clone(),
                None,
                &request_bytes,
                ScopeTarget::Standalone,
                reservation,
            )
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
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));

        let parent = lazy_parent(true).await;
        register_client(client_id, parent.clone());

        assert!(parent.is_cluster_mode());
        assert_eq!(
            try_resolve_scope_target(Some(&parent), 42),
            Err(ScopeTargetUnresolved::SlotUnmapped(42))
        );
        let acquired = acquire_scope_outcome(
            client_id,
            request_bytes.clone(),
            &tokio::runtime::Handle::current(),
            Some(42),
        );
        get_client_scope_pools().remove(&client_id);

        unregister_client(client_id);

        assert_eq!(
            acquired,
            ScopeAcquireOutcome::Retry(ScopeRetryCause::TargetUnresolved)
        );
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
            ScopePoolHandle::new(ScopePool::new(config, request_bytes.clone(), client_id)),
        );

        register_client(client_id, lazy_parent(false).await);

        let acquired = acquire_scope_outcome(
            client_id,
            request_bytes.clone(),
            &tokio::runtime::Handle::current(),
            None,
        );

        unregister_client(client_id);

        get_client_scope_pools().remove(&client_id);

        assert_eq!(
            acquired,
            ScopeAcquireOutcome::Retry(ScopeRetryCause::Pending),
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
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        register_client(client_id, lazy_parent(true).await);

        let acquire = |slot: u16| {
            acquire_scope_outcome(
                client_id,
                request_bytes.clone(),
                &tokio::runtime::Handle::current(),
                Some(slot),
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

        assert_eq!(
            acquire(42),
            ScopeAcquireOutcome::Retry(ScopeRetryCause::TargetUnresolved)
        );
        assert_eq!(recorded(), Some(ScopeTargetUnresolved::SlotUnmapped(42)));

        // Same cause again: the record is unchanged (a repeat, logged at debug).
        assert_eq!(
            acquire(42),
            ScopeAcquireOutcome::Retry(ScopeRetryCause::TargetUnresolved)
        );
        assert_eq!(recorded(), Some(ScopeTargetUnresolved::SlotUnmapped(42)));

        // A different unmapped slot updates the recorded value (so the message
        // names the current slot) but is the same kind, so it counts as a repeat.
        assert_eq!(
            acquire(7),
            ScopeAcquireOutcome::Retry(ScopeRetryCause::TargetUnresolved)
        );
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
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        assert_eq!(
            acquire(7),
            ScopeAcquireOutcome::Fail(ScopeFailCause::ParentUnregistered)
        );
        assert_eq!(recorded(), Some(ScopeTargetUnresolved::ParentUnregistered));

        // Resolution succeeding clears the record. The standalone parent resolves
        // without touching the network; the acquire then reserves and spawns a
        // creation task we do not wait for.
        register_client(client_id, lazy_parent(false).await);
        assert_eq!(
            acquire(7),
            ScopeAcquireOutcome::Retry(ScopeRetryCause::Pending),
            "no idle connection yet, so the caller retries"
        );
        assert_eq!(recorded(), None);

        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);
    }

    /// Creation failures repeat as fast as the binding retries, because each retry
    /// poll spawns another creation. `create_scope_connection` must therefore warn
    /// once per episode and log the repeats at debug, clearing the record once a
    /// creation succeeds — the same shape `log_unresolved_target` applies to an
    /// unresolved target. As there, the log lines are not observable here; the
    /// field they key on is.
    #[tokio::test]
    async fn create_failure_is_recorded_once_per_kind_and_cleared_on_success() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let good_bytes = request_bytes("", port);
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            good_bytes.clone(),
            1,
        )));

        // A standalone request with no seed address, so creation fails with a
        // different variant than the mTLS parent's and before any connect.
        let no_seed_bytes = ConnectionRequest::new()
            .write_to_bytes()
            .expect("serialize scope request");
        let mtls_bytes = mtls_request_bytes();
        let cert_parent = lazy_mtls_parent().await;

        let create = |client: Option<Client>, bytes: Vec<u8>| {
            let pool = pool.clone();
            async move {
                // Each failure gives its slot back, so re-reserve before the next.
                pool.lock().await.total_count.store(1, Ordering::Release);
                let reservation = reservation_for(&pool).await;
                let _ = create_scope_connection(
                    pool.clone(),
                    client.as_ref(),
                    &bytes,
                    ScopeTarget::Standalone,
                    reservation,
                )
                .await;
            }
        };
        let recorded =
            |pool: Arc<TokioMutex<ScopePool>>| async move { pool.lock().await.last_create_warn };

        assert_eq!(
            recorded(pool.clone()).await,
            None,
            "nothing recorded before the first creation"
        );

        create(Some(cert_parent.clone()), mtls_bytes.clone()).await;
        assert_eq!(
            recorded(pool.clone()).await,
            Some(ScopeCreateError::ParentCertMaterialUnavailable.kind()),
            "the first failure of a kind is the one that warns"
        );

        // Same cause again: the record is unchanged (a repeat, logged at debug).
        create(Some(cert_parent), mtls_bytes).await;
        assert_eq!(
            recorded(pool.clone()).await,
            Some(ScopeCreateError::ParentCertMaterialUnavailable.kind()),
        );

        // A different variant is a new kind of cause, so it warns in its own right.
        create(None, no_seed_bytes).await;
        assert_eq!(
            recorded(pool.clone()).await,
            Some(ScopeCreateError::NoSeedAddress.kind()),
        );
        assert_ne!(
            ScopeCreateError::NoSeedAddress.kind(),
            ScopeCreateError::ParentCertMaterialUnavailable.kind(),
            "the two causes must differ for this test to discriminate"
        );

        // A creation succeeding ends the episode, so the next failure warns again.
        create(None, good_bytes).await;
        assert_eq!(pool.lock().await.idle.len(), 1, "connection seated");
        assert_eq!(recorded(pool.clone()).await, None);

        shutdown_sender.send(()).expect("signal server shutdown");
        server.join().expect("join server thread");
    }
    /// A closed pool cannot hand out a scope, so it is a terminal outcome, not the
    /// generic retry that `ScopePool::try_acquire` reports as `Exhausted`.
    #[tokio::test]
    async fn acquire_on_a_closed_pool_fails_instead_of_retrying() {
        let client_id = 67_950_004_u64;
        let request_bytes = request_bytes("", 1);
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            client_id,
        )));
        pool.lock()
            .await
            .state
            .store(crate::pool::POOL_CLOSED, Ordering::Release);
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        register_client(client_id, lazy_parent(false).await);

        let acquired = acquire_scope_outcome(
            client_id,
            request_bytes,
            &tokio::runtime::Handle::current(),
            None,
        );

        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);
        assert_eq!(
            acquired,
            ScopeAcquireOutcome::Fail(ScopeFailCause::PoolClosed)
        );
    }

    /// Seat one connection in a `max_total = 1` pool, register the parent, and hand
    /// back `(pool, scope_id of the held connection, request bytes, server)`.
    async fn held_single_slot_pool(
        client_id: u64,
    ) -> (
        Arc<TokioMutex<ScopePool>>,
        u64,
        Vec<u8>,
        (Sender<()>, JoinHandle<()>),
    ) {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes("", port);
        let config = ScopePoolConfig {
            max_total: 1,
            ..ScopePoolConfig::default()
        };
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            config,
            request_bytes.clone(),
            client_id,
        )));
        pool.lock().await.total_count.store(1, Ordering::Release);
        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        register_client(client_id, lazy_parent(false).await);

        let held = {
            let mut guard = pool.lock().await;
            reused_scope_id(guard.try_acquire(
                crate::pool::get_scope_registry(),
                ScopeTarget::Standalone,
                0,
                None,
            ))
        };
        (pool, held, request_bytes, (shutdown_sender, server))
    }

    fn stop_endpoint((shutdown_sender, server): (Sender<()>, JoinHandle<()>)) {
        shutdown_sender.send(()).expect("signal server shutdown");
        server.join().expect("join server thread");
    }

    /// Wait cap for the wake tests: longer than their deadline, so the timer arm
    /// of the wait can never fire and only a `Notify` can end the wait early.
    const NO_TIMER_WAKE: Duration = Duration::from_secs(60);

    /// The waiting acquire is woken by the release itself, not by a timer: the
    /// borrower gets the connection in roughly the time the other holder kept it,
    /// nowhere near the deadline.
    #[tokio::test]
    async fn acquire_scope_wakes_when_another_borrower_releases() {
        let client_id = 67_950_005_u64;
        let (pool, held, request_bytes, endpoint) = held_single_slot_pool(client_id).await;

        let releaser = {
            let pool = pool.clone();
            tokio::spawn(async move {
                tokio::time::sleep(Duration::from_millis(150)).await;
                assert!(
                    pool.lock()
                        .await
                        .release(held, crate::pool::get_scope_registry())
                );
            })
        };

        let started = Instant::now();
        let acquired = super::acquire_scope_with_wait_cap(
            client_id,
            request_bytes,
            &tokio::runtime::Handle::current(),
            None,
            Duration::from_secs(5),
            NO_TIMER_WAKE,
        )
        .await;
        let elapsed = started.elapsed();
        releaser.await.expect("releaser task");

        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);
        stop_endpoint(endpoint);

        assert_eq!(acquired, Ok(held), "the released connection is reused");
        assert!(
            elapsed < Duration::from_secs(2),
            "woke {elapsed:?} after start; the release was at 150 ms"
        );
    }

    /// When nothing frees up, the error names the cause of the final attempt so
    /// the caller learns it was exhaustion, not some other retryable condition.
    #[tokio::test]
    async fn acquire_scope_deadline_names_the_last_retry_cause() {
        let client_id = 67_950_006_u64;
        let (pool, held, request_bytes, endpoint) = held_single_slot_pool(client_id).await;

        let started = Instant::now();
        let acquired = super::acquire_scope(
            client_id,
            request_bytes,
            &tokio::runtime::Handle::current(),
            None,
            Duration::from_millis(100),
        )
        .await;
        let elapsed = started.elapsed();

        assert!(
            pool.lock()
                .await
                .release(held, crate::pool::get_scope_registry())
        );
        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);
        stop_endpoint(endpoint);

        assert_eq!(
            acquired,
            Err(super::ScopeAcquireError::DeadlineExceeded {
                last: ScopeRetryCause::Exhausted,
            })
        );
        assert!(
            elapsed >= Duration::from_millis(100) && elapsed < Duration::from_secs(2),
            "deadline honoured: {elapsed:?}"
        );
    }

    /// A server that refuses every connection is dialed a bounded number of
    /// times per waiting acquire, including when several acquires wait at once,
    /// so a node that is already rejecting clients is not flooded by the clients
    /// trying to reach it.
    #[tokio::test]
    async fn acquire_scope_backs_off_between_failed_dials() {
        let client_id = 67_950_010_u64;
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind test listener");
        listener
            .set_nonblocking(true)
            .expect("nonblocking listener");
        let port = listener.local_addr().expect("listener address").port();
        let accepted = Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let stop = Arc::new(std::sync::atomic::AtomicBool::new(false));
        let server = {
            let accepted = accepted.clone();
            let stop = stop.clone();
            std::thread::spawn(move || {
                while !stop.load(Ordering::Acquire) {
                    match listener.accept() {
                        // Dropping the stream closes it before any handshake byte.
                        Ok(_) => {
                            accepted.fetch_add(1, Ordering::Release);
                        }
                        Err(e) if e.kind() == ErrorKind::WouldBlock => {
                            std::thread::sleep(Duration::from_micros(200));
                        }
                        Err(e) => panic!("accept failed: {e}"),
                    }
                }
            })
        };
        let request_bytes = request_bytes("", port);
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            client_id,
        )));
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        register_client(client_id, lazy_parent(false).await);

        let deadline = Duration::from_millis(500);
        let started = Instant::now();
        // Two at once: a failed dial's slot return fires the pool wakeup, so a
        // second waiter is what would turn one acquire's backoff into lockstep.
        let handle = tokio::runtime::Handle::current();
        let (acquired, second) = tokio::join!(
            super::acquire_scope(client_id, request_bytes.clone(), &handle, None, deadline),
            super::acquire_scope(client_id, request_bytes, &handle, None, deadline),
        );
        let elapsed = started.elapsed();

        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);
        stop.store(true, Ordering::Release);
        server.join().expect("join listener thread");

        // The last attempt may still be mid-dial when the deadline lands, so the
        // final cause is `CreateFailed` or `Pending`; the dial count is the claim.
        assert!(
            matches!(
                acquired,
                Err(super::ScopeAcquireError::DeadlineExceeded { .. })
            ),
            "{acquired:?}"
        );
        assert!(
            elapsed >= deadline && elapsed < Duration::from_secs(2),
            "deadline honoured: {elapsed:?}"
        );
        assert!(
            matches!(
                second,
                Err(super::ScopeAcquireError::DeadlineExceeded { .. })
            ),
            "{second:?}"
        );
        // 10 ms doubling to 200 ms covers 500 ms in about seven dials per acquire;
        // slack for a fast first failure before the first wait.
        let dials = accepted.load(Ordering::Acquire);
        assert!(
            (4..=40).contains(&dials),
            "{dials} dials in {elapsed:?}; a refusing server must not be redialed in a tight loop"
        );
    }

    /// Closing the parent while an acquire is waiting ends the wait with the
    /// closed-parent cause, not a timeout at the deadline. The close runs outside
    /// the pool lock, so the wake has to come from the handle's `Notify`.
    #[tokio::test]
    async fn acquire_scope_stops_when_parent_closes_mid_wait() {
        let client_id = 67_950_007_u64;
        let (pool, held, request_bytes, endpoint) = held_single_slot_pool(client_id).await;

        let closer = tokio::spawn(async move {
            tokio::time::sleep(Duration::from_millis(150)).await;
            unregister_client(client_id);
        });

        let started = Instant::now();
        let acquired = super::acquire_scope_with_wait_cap(
            client_id,
            request_bytes,
            &tokio::runtime::Handle::current(),
            None,
            Duration::from_secs(5),
            NO_TIMER_WAKE,
        )
        .await;
        let elapsed = started.elapsed();
        closer.await.expect("closer task");

        // Reclaim what the test seated; the registry entry went with the client.
        pool.lock().await.in_use.remove(&held);
        get_client_scope_pools().remove(&client_id);
        stop_endpoint(endpoint);

        assert_eq!(
            acquired,
            Err(super::ScopeAcquireError::Terminal(
                ScopeFailCause::ParentUnregistered
            ))
        );
        assert!(
            elapsed < Duration::from_secs(2),
            "stopped {elapsed:?} after start; the close was at 150 ms"
        );
    }

    /// A creation failure that no wait can clear ends the acquire as soon as the
    /// failed task has recorded it, instead of respawning until the deadline.
    #[tokio::test]
    async fn acquire_scope_returns_terminal_create_failure_promptly() {
        let client_id = 67_950_008_u64;
        let request_bytes = mtls_request_bytes();
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            client_id,
        )));
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        register_client(client_id, lazy_mtls_parent().await);

        let started = Instant::now();
        let acquired = super::acquire_scope(
            client_id,
            request_bytes,
            &tokio::runtime::Handle::current(),
            None,
            Duration::from_secs(5),
        )
        .await;
        let elapsed = started.elapsed();

        unregister_client(client_id);
        get_client_scope_pools().remove(&client_id);

        assert_eq!(
            acquired,
            Err(super::ScopeAcquireError::Terminal(
                ScopeFailCause::ParentCertMaterialUnavailable
            ))
        );
        assert!(
            elapsed < Duration::from_secs(2),
            "terminal cause reported {elapsed:?} after start"
        );
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
        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            initial_target,
            reservation,
        )
        .await;

        let registry = crate::pool::get_scope_registry();
        let first_scope_id = {
            let mut pool = pool.lock().await;
            assert_eq!(pool.idle.len(), 1);
            let target = try_resolve_scope_target(Some(&parent), DEFAULT_ROUTING_SLOT)
                .expect("standalone always resolves");
            reused_scope_id(pool.try_acquire(registry, target, 0, None))
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
            reused_scope_id(pool.try_acquire(registry, alternate_target, 0, None))
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
        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;

        let registry = crate::pool::get_scope_registry();
        let target = ScopeTarget::cluster_primary(PRIMARY_A);
        let first_scope_id = {
            let mut pool = pool.lock().await;
            pool.idle[0].target = target.clone();
            reused_scope_id(pool.try_acquire(registry, target.clone(), 0, None))
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
            reused_scope_id(pool.try_acquire(
                registry,
                ScopeTarget::cluster_primary(PRIMARY_A),
                0,
                None,
            ))
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
        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;

        let registry = crate::pool::get_scope_registry();
        let scope_id = {
            let mut pool = pool.lock().await;
            // Below max_total, a mismatched idle connection is kept and a new
            // slot is reserved for the other primary. Each reservation is counted
            // against max_total, so the running total is asserted alongside it.
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
            pool.idle[0].target = ScopeTarget::cluster_primary(PRIMARY_A);
            // Hold each reserved guard: in production the creation task holds it
            // and commits on seat. Dropping it here would reclaim the slot.
            let reservation_b = match pool.try_acquire(
                registry,
                ScopeTarget::cluster_primary(PRIMARY_B),
                0,
                None,
            ) {
                ScopeAcquire::Reserved(reservation) => reservation,
                other => panic!("expected a reservation, got {other:?}"),
            };
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);

            pool.idle[0].target = ScopeTarget::cluster_primary(PRIMARY_B);
            let reservation_a = match pool.try_acquire(
                registry,
                ScopeTarget::cluster_primary(PRIMARY_A),
                0,
                None,
            ) {
                ScopeAcquire::Reserved(reservation) => reservation,
                other => panic!("expected a reservation, got {other:?}"),
            };
            assert_eq!(pool.idle.len(), 1);
            assert_eq!(pool.total_count.load(Ordering::Acquire), 3);

            // Reuse consumes no additional capacity.
            pool.idle[0].target = ScopeTarget::Standalone;
            let reused =
                reused_scope_id(pool.try_acquire(registry, ScopeTarget::Standalone, 0, None));
            assert_eq!(pool.total_count.load(Ordering::Acquire), 3);
            // Keep the two held reservations counted, as their in-flight creations
            // would; they are the two slots the running total above accounts for.
            reservation_a.commit();
            reservation_b.commit();
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
        let reservation_a = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes("", port_a),
            ScopeTarget::Standalone,
            reservation_a,
        )
        .await;
        let reservation_b = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes("", port_b),
            ScopeTarget::Standalone,
            reservation_b,
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
            // Hold the guard as the in-flight creation would; dropping it here
            // would reclaim the slot the eviction just freed for it.
            let reservation = match pool.try_acquire(
                registry,
                ScopeTarget::cluster_primary("10.0.0.3:6379"),
                0,
                None,
            ) {
                ScopeAcquire::Reserved(reservation) => reservation,
                other => panic!("expected a reservation, got {other:?}"),
            };
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
            let reused = reused_scope_id(pool.try_acquire(
                registry,
                ScopeTarget::cluster_primary(PRIMARY_B),
                0,
                None,
            ));
            assert_eq!(reused, newest_id);
            assert!(pool.idle.is_empty());
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);

            // Full with nothing idle: genuinely exhausted, and no reservation leaks.
            assert!(matches!(
                pool.try_acquire(registry, ScopeTarget::cluster_primary(PRIMARY_A), 0, None),
                ScopeAcquire::Exhausted
            ));
            assert_eq!(pool.total_count.load(Ordering::Acquire), 2);

            // The held reservation's creation would commit; keep the slot counted.
            reservation.commit();
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
            unix_socket_path: None,
        });
        parent_request.lazy_connect = true;
        let parent = Client::new(parent_request, None)
            .await
            .expect("lazy parent client creation should succeed");
        register_client(parent_client_id, parent);

        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;
        let acquired = {
            let mut pool = pool.lock().await;
            pool.try_acquire(get_scope_registry(), ScopeTarget::Standalone, 0, None)
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
        get_client_scope_pools().insert(parent_client_id, ScopePoolHandle::for_test(&pool));

        let mut parent_request = crate::client::ConnectionRequest::default();
        parent_request.addresses.push(crate::client::NodeAddress {
            host: "127.0.0.1".into(),
            port,
            unix_socket_path: None,
        });
        parent_request.lazy_connect = true;
        let parent = Client::new(parent_request, None)
            .await
            .expect("lazy parent client creation should succeed");
        register_client(parent_client_id, parent);

        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;
        let acquired = {
            let mut pool = pool.lock().await;
            pool.try_acquire(get_scope_registry(), ScopeTarget::Standalone, 0, None)
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

    // ── IPv6 addressing (#7124) ──────────────────────────────────────────────
    // Scoped connections build a structured `ConnectionInfo` rather than a
    // `redis://` URL, so a bare IPv6 host does not need bracketing.

    #[test]
    fn parse_cluster_target_handles_ipv4_and_ipv6() {
        assert_eq!(
            parse_cluster_target("10.0.0.1:6379"),
            Some(("10.0.0.1".to_string(), 6379))
        );

        // Bare IPv6: the final `:` before the port is the split point, and the
        // host has no brackets to trim. This is the string the old URL path rejected.
        assert_eq!(
            parse_cluster_target("::1:7801"),
            Some(("::1".to_string(), 7801))
        );

        // Bracketed IPv6 trims to the same host.
        assert_eq!(
            parse_cluster_target("[::1]:7801"),
            Some(("::1".to_string(), 7801))
        );
        assert_eq!(
            parse_cluster_target("[2001:db8::1]:6380"),
            Some(("2001:db8::1".to_string(), 6380))
        );
    }

    #[test]
    fn parse_cluster_target_rejects_malformed() {
        assert_eq!(parse_cluster_target("nohostport"), None); // no ':'
        assert_eq!(parse_cluster_target(":6379"), None); // empty host
        assert_eq!(parse_cluster_target("host:notaport"), None); // non-numeric port
    }

    #[test]
    fn strip_host_brackets_trims_only_ipv6_brackets() {
        assert_eq!(strip_host_brackets("[::1]"), "::1"); // bracketed IPv6
        assert_eq!(strip_host_brackets("::1"), "::1"); // bare IPv6 unchanged
        assert_eq!(strip_host_brackets("[2001:db8::1]"), "2001:db8::1");
        assert_eq!(strip_host_brackets("10.0.0.1"), "10.0.0.1"); // IPv4 unchanged
        assert_eq!(strip_host_brackets("localhost"), "localhost"); // hostname unchanged
    }

    #[test]
    fn build_scope_connection_addr_ipv6_no_tls() {
        use crate::connection_request::TlsMode;
        let addr = build_scope_connection_addr("::1".to_string(), 7801, TlsMode::NoTls, None);
        match addr {
            redis::ConnectionAddr::Tcp(host, port) => {
                assert_eq!(host, "::1");
                assert_eq!(port, 7801);
            }
            other => panic!("expected Tcp(::1, 7801), got {other:?}"),
        }
    }

    #[test]
    fn build_scope_connection_addr_ipv6_secure_and_insecure_tls() {
        use crate::connection_request::TlsMode;

        let secure = build_scope_connection_addr("::1".to_string(), 6379, TlsMode::SecureTls, None);
        match secure {
            redis::ConnectionAddr::TcpTls {
                host,
                port,
                insecure,
                ..
            } => {
                assert_eq!(host, "::1");
                assert_eq!(port, 6379);
                assert!(!insecure, "SecureTls must verify");
            }
            other => panic!("expected verifying TcpTls, got {other:?}"),
        }

        let insecure =
            build_scope_connection_addr("::1".to_string(), 6379, TlsMode::InsecureTls, None);
        match insecure {
            redis::ConnectionAddr::TcpTls { host, insecure, .. } => {
                assert_eq!(host, "::1");
                assert!(insecure, "InsecureTls must skip verification");
            }
            other => panic!("expected insecure TcpTls, got {other:?}"),
        }
    }

    /// The parent's certificate material has to reach the scoped connection's
    /// address, otherwise the handshake falls back to system trust roots and sends
    /// no client certificate.
    #[test]
    fn build_scope_connection_addr_carries_parent_tls_params() {
        use crate::connection_request::TlsMode;

        let parent_params = test_tls_params(CERT_A, KEY_A);

        let inherited = build_scope_connection_addr(
            "127.0.0.1".to_string(),
            6379,
            TlsMode::SecureTls,
            Some(parent_params),
        );
        match inherited {
            redis::ConnectionAddr::TcpTls { tls_params, .. } => {
                let params = tls_params.expect("parent tls_params must be inherited");
                assert!(
                    !params.client_cert_chain_der().is_empty(),
                    "inherited params must carry the parent's client certificate"
                );
            }
            other => panic!("expected TcpTls, got {other:?}"),
        }

        // No parent material configured stays `None` rather than inventing any.
        let bare =
            build_scope_connection_addr("127.0.0.1".to_string(), 6379, TlsMode::SecureTls, None);
        match bare {
            redis::ConnectionAddr::TcpTls { tls_params, .. } => {
                assert!(tls_params.is_none(), "expected no tls_params, got Some");
            }
            other => panic!("expected TcpTls, got {other:?}"),
        }
    }

    /// `build_scope_connection`'s failure, for the TLS cases below. `expect_err` is
    /// unavailable: the success type holds a connection, which is not `Debug`.
    async fn scope_connect_error(parent: &Client, request_bytes: &[u8]) -> ScopeCreateError {
        match build_scope_connection(Some(parent), request_bytes, &ScopeTarget::Standalone).await {
            Err(err) => err,
            Ok(_) => panic!("nothing listens on the seed port, so no scope can be created"),
        }
    }

    /// A scope must connect with the parent's *current* certificate material. The
    /// parent's `tls_params` are a snapshot taken when it connected: empty for a
    /// lazy parent, stale after a rotation. Resolving from that snapshot here
    /// yields `None`, which the fail-fast guard then rejects before any connect.
    #[tokio::test]
    async fn scope_connection_reads_cert_material_from_the_parents_reload_handle() {
        let (_dir, manager) = test_reload_manager(CERT_A, KEY_A).await;

        let parent = lazy_mtls_parent().await;
        // The shape a lazy parent with path-based reload has: no snapshot, live handle.
        parent.set_cert_material_for_test(crate::client::InheritedCertMaterial {
            tls_params: None,
            reload_handle: Some(manager.get_handle()),
        });

        let err = scope_connect_error(&parent, &mtls_request_bytes()).await;

        assert!(
            matches!(
                err,
                ScopeCreateError::ConnectFailed(_) | ScopeCreateError::ConnectTimedOut
            ),
            "the handle's material must reach the connection attempt, got: {err}"
        );
    }

    /// The handle must win over a *populated* snapshot, not just an empty one: a
    /// parent that connected with cert_a and has since adopted cert_b hands a new
    /// scope cert_b. A snapshot-first read would hand out the retired certificate,
    /// which the server stops accepting once the rotation completes.
    #[tokio::test]
    async fn inherited_tls_params_prefers_the_reload_handle_over_a_stale_snapshot() {
        let (_dir, manager) = test_reload_manager(CERT_B, KEY_B).await;

        let parent = lazy_parent(false).await;
        parent.set_cert_material_for_test(crate::client::InheritedCertMaterial {
            tls_params: Some(test_tls_params(CERT_A, KEY_A)),
            reload_handle: Some(manager.get_handle()),
        });

        let inherited = inherited_tls_params(Some(&parent))
            .await
            .expect("the parent carries material on both sources");

        let adopted = test_tls_params(CERT_B, KEY_B);
        let retired = test_tls_params(CERT_A, KEY_A);
        assert_ne!(
            adopted.client_cert_chain_der(),
            retired.client_cert_chain_der(),
            "the two test pairs must differ for this test to discriminate"
        );
        assert_eq!(
            inherited.client_cert_chain_der(),
            adopted.client_cert_chain_der(),
            "a scope must present the adopted certificate, not the parent-connect snapshot"
        );
    }

    /// Inline (byte-based) certificate material configures no reload handle, so
    /// the parent's snapshot is the only source there is — the guard must not fire
    /// and the material must still reach the connection attempt.
    #[tokio::test]
    async fn scope_connection_falls_back_to_the_snapshot_for_inline_cert_material() {
        let parent = lazy_mtls_parent().await;
        parent.set_cert_material_for_test(crate::client::InheritedCertMaterial {
            tls_params: Some(test_tls_params(CERT_A, KEY_A)),
            reload_handle: None,
        });

        let err = scope_connect_error(&parent, &mtls_request_bytes()).await;

        assert!(
            matches!(
                err,
                ScopeCreateError::ConnectFailed(_) | ScopeCreateError::ConnectTimedOut
            ),
            "the inline snapshot must reach the connection attempt, got: {err}"
        );
    }

    /// An mTLS parent that has not connected yet has nothing to inherit, so the
    /// acquire must fail rather than hand redis-rs no material — which would
    /// connect against system trust roots with no client certificate.
    #[tokio::test]
    async fn scope_connection_fails_fast_when_parent_cert_material_is_unavailable() {
        let parent = lazy_mtls_parent().await;

        let err = scope_connect_error(&parent, &mtls_request_bytes()).await;
        assert!(
            matches!(err, ScopeCreateError::ParentCertMaterialUnavailable),
            "expected a fail-fast, got: {err}"
        );

        // The guard must not fire under a parent that never configured material:
        // system trust with no client certificate is the intended behaviour there.
        let parent = lazy_parent(false).await;
        let err = scope_connect_error(&parent, &mtls_request_bytes()).await;
        assert!(
            !matches!(err, ScopeCreateError::ParentCertMaterialUnavailable),
            "system-trust TLS must still be allowed, got: {err}"
        );
    }

    /// Fake AWS credentials so `IAMTokenManager::new` can locally sign a SigV4
    /// token without reaching real AWS.
    fn setup_iam_test_credentials() {
        unsafe {
            std::env::set_var("AWS_ACCESS_KEY_ID", "test_access_key");
            std::env::set_var("AWS_SECRET_ACCESS_KEY", "test_secret_key");
            std::env::set_var("AWS_SESSION_TOKEN", "test_session_token");
        }
    }

    /// A lazy IAM parent built the way production does — `Client::new` with IAM
    /// config and TLS enabled. This is the reachable state the scope guard defends
    /// against: a TLS IAM parent whose scope request disables TLS. (A no-TLS IAM
    /// parent cannot exist: `Client::new` rejects it.)
    async fn lazy_iam_parent() -> Client {
        setup_iam_test_credentials();
        let request = ClientRequest {
            addresses: vec![ClientAddress {
                host: "127.0.0.1".to_string(),
                port: 1,
                unix_socket_path: None,
            }],
            lazy_connect: true,
            tls_mode: Some(crate::client::TlsMode::SecureTls),
            authentication_info: Some(crate::client::AuthenticationInfo {
                username: Some("iam-test-user".to_string()),
                password: None,
                iam_config: Some(crate::client::IamAuthenticationConfig {
                    cluster_name: "test-cluster".to_string(),
                    region: "us-east-1".to_string(),
                    service_type: crate::iam::ServiceType::ElastiCache,
                    refresh_interval_seconds: None,
                    credentials_provider: None,
                }),
            }),
            ..Default::default()
        };
        Client::new(request, None)
            .await
            .expect("lazy IAM client construction does not touch the network")
    }

    /// A scope off an IAM parent whose request disables TLS must fail with
    /// `IamRequiresTls` before obtaining or sending the token.
    #[tokio::test]
    async fn scope_rejects_iam_over_plaintext() {
        let parent = lazy_iam_parent().await;

        // `request_bytes` builds a standalone NoTls request (no tls_mode set).
        let err = scope_connect_error(&parent, &request_bytes("GlideRust", 1)).await;
        assert!(
            matches!(err, ScopeCreateError::IamRequiresTls),
            "IAM + NoTls scope must fail closed, got: {err}"
        );
    }

    /// A-B companion: without an IAM manager, the same NoTls scope request is NOT
    /// rejected by the IAM guard (it proceeds to the connect attempt and fails
    /// there, since nothing listens on the seed port). This proves the guard keys
    /// on IAM, not merely on `NoTls`.
    #[tokio::test]
    async fn scope_allows_plaintext_without_iam() {
        let parent = lazy_parent(false).await;

        let err = scope_connect_error(&parent, &request_bytes("GlideRust", 1)).await;
        assert!(
            !matches!(err, ScopeCreateError::IamRequiresTls),
            "a non-IAM plaintext scope must not hit the IAM/TLS guard, got: {err}"
        );
    }

    // ── Address resolution ───────────────────────────────────────────────────
    // A standalone scope resolves its seed through the parent's resolver; a
    // cluster scope must not, because its target left the slot map resolved.

    /// Rewrites every address to a fixed one and records each call, so a test can
    /// assert both that a branch resolved and that another never did.
    #[derive(Debug)]
    struct RecordingResolver {
        rewrite_to: (String, u16),
        calls: std::sync::Mutex<Vec<(String, u16)>>,
    }

    impl RecordingResolver {
        fn rewriting_to(host: &str, port: u16) -> Arc<Self> {
            Arc::new(Self {
                rewrite_to: (host.to_string(), port),
                calls: std::sync::Mutex::new(Vec::new()),
            })
        }

        fn calls(&self) -> Vec<(String, u16)> {
            self.calls.lock().expect("resolver call log").clone()
        }
    }

    impl redis::AddressResolver for RecordingResolver {
        fn resolve(&self, host: &str, port: u16) -> (String, u16) {
            self.calls
                .lock()
                .expect("resolver call log")
                .push((host.to_string(), port));
            self.rewrite_to.clone()
        }
    }

    /// Privileged and unbindable, so a connect is refused at once instead of
    /// hanging — the dead address a resolver must move off of.
    const UNUSED_PORT: u16 = 1;

    #[tokio::test]
    async fn standalone_scope_resolves_its_seed_through_the_parent_resolver() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        // Only the rewrite can reach the live endpoint: the seed is a dead port.
        let request_bytes = request_bytes("", UNUSED_PORT);
        let pool = reserved_pool(request_bytes.clone());
        let resolver = RecordingResolver::rewriting_to("127.0.0.1", port);
        let parent = lazy_parent_with_resolver(
            false,
            Some(resolver.clone() as Arc<dyn redis::AddressResolver>),
        )
        .await;

        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            Some(&parent),
            &request_bytes,
            ScopeTarget::Standalone,
            reservation,
        )
        .await;

        assert_eq!(
            resolver.calls(),
            vec![("127.0.0.1".to_string(), UNUSED_PORT)],
            "the seed address must be handed to the parent's resolver"
        );
        {
            let pool = pool.lock().await;
            assert_eq!(
                pool.idle.len(),
                1,
                "the resolved address must be the one connected to"
            );
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
        }

        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
    }

    /// Re-resolving a slot-map target (already resolved by redis-rs on its way in)
    /// would move the connection off its routed primary — here, onto a dead port.
    #[tokio::test]
    async fn cluster_scope_does_not_re_resolve_the_slot_map_target() {
        let (port, shutdown_sender, server) = responsive_endpoint();
        let request_bytes = request_bytes_with_mode("", UNUSED_PORT, true);
        let pool = reserved_pool(request_bytes.clone());
        // Rewrites to the dead port, so a second resolve is visible as a failed
        // connection as well as a recorded call.
        let resolver = RecordingResolver::rewriting_to("127.0.0.1", UNUSED_PORT);
        let parent = lazy_parent_with_resolver(
            true,
            Some(resolver.clone() as Arc<dyn redis::AddressResolver>),
        )
        .await;

        let reservation = reservation_for(&pool).await;
        let _ = create_scope_connection(
            pool.clone(),
            Some(&parent),
            &request_bytes,
            ScopeTarget::cluster_primary(format!("127.0.0.1:{port}")),
            reservation,
        )
        .await;

        assert!(
            resolver.calls().is_empty(),
            "an already-resolved slot-map target must not be resolved again, got {:?}",
            resolver.calls()
        );
        {
            let pool = pool.lock().await;
            assert_eq!(
                pool.idle.len(),
                1,
                "the connection must land on the slot-map address"
            );
            assert_eq!(pool.total_count.load(Ordering::Acquire), 1);
        }

        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
    }

    // ── Acquisition-slot command validation ──────────────────────────────────
    // A cluster scope acquired for slot A must reject its very first keyed command
    // if that command hashes to a different slot, locally and before dispatch —
    // rather than accepting it unconstrained and only failing as a server MOVED.

    #[test]
    fn scope_slot_for_command_classifies_via_router() {
        use super::{ScopeSlot, scope_slot_for_command};
        use crate::pool::slot_for_key;

        let slot = |k: &[u8]| slot_for_key(k);

        // Single-key command → that key's slot.
        assert_eq!(
            scope_slot_for_command("GET", &[b"k"]),
            ScopeSlot::Slot(slot(b"k"))
        );
        // Container key-bearing subcommand (OBJECT ENCODING <key>, MEMORY USAGE <key>).
        assert_eq!(
            scope_slot_for_command("OBJECT", &[b"ENCODING", b"k"]),
            ScopeSlot::Slot(slot(b"k"))
        );
        assert_eq!(
            scope_slot_for_command("MEMORY", &[b"USAGE", b"k"]),
            ScopeSlot::Slot(slot(b"k"))
        );

        // Variadic command, keys on one slot → that slot; cross-slot → CrossSlot.
        assert_eq!(
            scope_slot_for_command("DEL", &[b"{t}a", b"{t}b"]),
            ScopeSlot::Slot(slot(b"{t}a"))
        );
        assert_eq!(
            scope_slot_for_command("MGET", &[b"a", b"b", b"c"]),
            // a/b/c land on different slots → cross-slot
            ScopeSlot::CrossSlot
        );

        // EVAL/FCALL: numkeys keys after the script; cross-check them, never the script.
        assert_eq!(
            scope_slot_for_command("EVAL", &[b"script", b"2", b"{t}k1", b"{t}k2"]),
            ScopeSlot::Slot(slot(b"{t}k1"))
        );
        assert_eq!(
            scope_slot_for_command("EVAL", &[b"script", b"2", b"a", b"b"]),
            ScopeSlot::CrossSlot
        );
        // numkeys 0 / malformed / over-count → unconstrained, never the script.
        assert_eq!(
            scope_slot_for_command("EVAL", &[b"script", b"0"]),
            ScopeSlot::Unconstrained
        );
        assert_eq!(
            scope_slot_for_command("EVAL", &[b"script"]),
            ScopeSlot::Unconstrained
        );
        assert_eq!(
            scope_slot_for_command("FCALL", &[b"fn", b"5", b"k1"]),
            ScopeSlot::Unconstrained
        );

        // Keyless / admin / unknown-first-arg commands → unconstrained (not pinned to
        // a junk slot). These are the regression cases: before the router swap, the
        // first-arg fallback pinned them to a bogus slot and a keyed scope then
        // rejected them locally even though the server would accept them.
        let unconstrained: &[(&str, &[&[u8]])] = &[
            ("SCAN", &[b"0"]),
            ("ECHO", &[b"hi"]),
            ("KEYS", &[b"pattern*"]),
            ("SCRIPT", &[b"LOAD", b"return 1"]),
            ("FUNCTION", &[b"LOAD", b"code"]),
            ("PING", &[]),
        ];
        for (name, args) in unconstrained {
            assert_eq!(
                scope_slot_for_command(name, args),
                ScopeSlot::Unconstrained,
                "{name} should be unconstrained"
            );
        }
    }

    #[test]
    fn acquisition_slot_for_only_constrains_keyed_cluster_scopes() {
        let cluster = ScopeTarget::cluster_primary("127.0.0.1:7000");
        // A caller-supplied routing key constrains the scope.
        assert_eq!(acquisition_slot_for(&cluster, Some(42)), Some(42));
        assert_eq!(acquisition_slot_for(&cluster, Some(0)), Some(0));
        // No routing key leaves even a cluster scope unconstrained.
        assert_eq!(acquisition_slot_for(&cluster, None), None);
        // Standalone is never constrained, whatever the slot.
        assert_eq!(
            acquisition_slot_for(&ScopeTarget::Standalone, Some(42)),
            None
        );
        assert_eq!(acquisition_slot_for(&ScopeTarget::Standalone, None), None);
    }

    /// Seat one cluster-target connection against the mock endpoint and borrow it
    /// with `acquisition_slot`, returning the live scope id and its pool. The
    /// caller drives commands through `execute_scope_command` and cleans up.
    ///
    /// `client_id` must be unique per call: it keys the process-global
    /// `get_client_scope_pools()` / client registry, so two borrows sharing an id
    /// (e.g. both seeded from the same key) stomp each other's pool/parent entries
    /// under concurrent test execution.
    async fn borrow_cluster_scope(
        port: u16,
        acquisition_slot: u16,
    ) -> (u64, Arc<TokioMutex<ScopePool>>, u64) {
        // Arbitrary base; the counter guarantees uniqueness, the value has no meaning.
        const FIRST_TEST_CLIENT_ID: u64 = 70_570_000;
        static NEXT_TEST_CLIENT_ID: std::sync::atomic::AtomicU64 =
            std::sync::atomic::AtomicU64::new(FIRST_TEST_CLIENT_ID);
        let request_bytes = request_bytes_with_mode("", port, true);
        let client_id = NEXT_TEST_CLIENT_ID.fetch_add(1, Ordering::Relaxed);
        let pool = Arc::new(TokioMutex::new(ScopePool::new(
            ScopePoolConfig::default(),
            request_bytes.clone(),
            client_id,
        )));
        get_client_scope_pools().insert(client_id, ScopePoolHandle::for_test(&pool));
        register_client(client_id, lazy_parent(true).await);

        // Pre-count the slot the seated connection will occupy, matching the
        // reserve-before-create accounting (`reservation_for` binds a for_test guard
        // to the counter without incrementing it).
        pool.lock().await.total_count.store(1, Ordering::Release);

        let target = ScopeTarget::cluster_primary(format!("127.0.0.1:{port}"));
        let reservation = reservation_for(&pool).await;
        create_scope_connection(
            pool.clone(),
            None,
            &request_bytes,
            target.clone(),
            reservation,
        )
        .await
        .expect("seat the cluster connection");

        let registry = get_scope_registry();
        let scope_id = {
            let mut guard = pool.lock().await;
            assert_eq!(guard.idle.len(), 1, "the connection must be seated idle");
            match guard.try_acquire(registry, target, 0, Some(acquisition_slot)) {
                ScopeAcquire::Reused(id) => id,
                other => panic!("expected to reuse the seated connection, got {other:?}"),
            }
        };
        (scope_id, pool, client_id)
    }

    /// The first keyed command through a cluster scope must be checked against the
    /// slot the scope was acquired for. Acquire for `slot(foo)`, issue the first
    /// command for `bar` (a different slot): it must fail CROSSSLOT locally, the
    /// rejected command must not pin the connection, and the connection must stay
    /// reusable (not poisoned) and return to idle on release.
    ///
    /// A-B: without the acquisition-slot seed the first command is unconstrained,
    /// so `GET bar` pins to `slot(bar)` and is accepted — this test fails on the
    /// pre-fix code. The seed makes the first command check against `slot(foo)`.
    #[tokio::test]
    async fn first_cluster_command_must_match_acquisition_slot() {
        let (port, shutdown_sender, server) = responsive_endpoint();

        let slot_a = crate::pool::slot_for_key(b"foo");
        let slot_b = crate::pool::slot_for_key(b"bar");
        assert_ne!(slot_a, slot_b, "the two keys must hash to different slots");

        let (scope_id, pool, client_id) = borrow_cluster_scope(port, slot_a).await;
        let parent = get_parent_client(client_id).expect("parent registered");

        // First keyed command for a different slot than the acquisition slot.
        let err = execute_scope_command(scope_id, "GET", &[b"bar".to_vec()], Some(&parent))
            .await
            .expect_err("a first command for the wrong slot must be rejected locally");
        assert_eq!(
            err.kind(),
            redis::ErrorKind::CrossSlot,
            "the rejection must be a local cross-slot error, not a server MOVED"
        );

        let registry = get_scope_registry();

        // Release and confirm the connection stays reusable: a rejected first
        // command neither poisons the connection nor leaves a pin behind, so it
        // returns to idle (synchronously on the clean path; poll to be robust).
        {
            let mut guard = pool.lock().await;
            assert!(guard.release(scope_id, registry));
        }
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
        loop {
            if pool.lock().await.idle.len() == 1 {
                break;
            }
            assert!(
                std::time::Instant::now() < deadline,
                "the connection must stay reusable and return to idle"
            );
            tokio::time::sleep(std::time::Duration::from_millis(20)).await;
        }
        {
            let guard = pool.lock().await;
            assert_eq!(
                guard.idle[0].pinned_slot, None,
                "a rejected first command must not leave a pin"
            );
            assert_eq!(
                guard.idle[0].acquisition_slot, None,
                "the acquisition slot must not outlive the borrow"
            );
            assert_eq!(guard.total_count.load(Ordering::Acquire), 1);
        }

        get_client_scope_pools().remove(&client_id);
        unregister_client(client_id);
        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
    }

    /// A cross-slot command queued inside an open MULTI must reach the server, not
    /// be rejected locally: a local rejection drops it from the server's queue, so
    /// EXEC commits the rest instead of EXECABORTing. The mock answers the handshake
    /// plus the two commands before the queued one then falls silent, so the
    /// cross-slot `SET` hangs on a reply that never comes (harness or request
    /// timeout, never `CrossSlot`); a local rejection would return `CrossSlot` at
    /// once. The companion case — the same command outside a MULTI still rejected
    /// locally — bounds the fix to the open-transaction case, not a blanket removal.
    #[tokio::test]
    async fn multi_active_scope_skips_local_slot_check_for_queued_commands() {
        let (port, shutdown_sender, server) = endpoint_answering_n_more_commands(2);

        let slot_a = crate::pool::slot_for_key(b"foo");
        let slot_b = crate::pool::slot_for_key(b"bar");
        assert_ne!(slot_a, slot_b, "the two keys must hash to different slots");

        let (scope_id, pool, client_id) = borrow_cluster_scope(port, slot_a).await;
        let parent = get_parent_client(client_id).expect("parent registered");

        // Open a MULTI, then pin the scope with a same-slot command.
        execute_scope_command(scope_id, "MULTI", &[], Some(&parent))
            .await
            .expect("MULTI is keyless and must not be rejected");
        execute_scope_command(
            scope_id,
            "SET",
            &[b"foo".to_vec(), b"1".to_vec()],
            Some(&parent),
        )
        .await
        .expect("same-slot command inside MULTI must not be rejected");

        // The cross-slot command queued inside the open MULTI must reach the wire,
        // not be rejected locally; the mock has stopped replying, so a dispatched
        // command hangs (timeout) while a local rejection returns CrossSlot at once.
        let queued = tokio::time::timeout(
            std::time::Duration::from_millis(300),
            execute_scope_command(
                scope_id,
                "SET",
                &[b"bar".to_vec(), b"2".to_vec()],
                Some(&parent),
            ),
        )
        .await;
        match queued {
            Err(_) => {} // harness timeout: command reached the wire, never completed
            Ok(Ok(_)) => panic!("a cross-slot command queued inside MULTI must not succeed"),
            Ok(Err(e)) => assert_ne!(
                e.kind(),
                redis::ErrorKind::CrossSlot,
                "must not be rejected locally; it must reach the server and fail only \
                 for network/timeout reasons, got: {e:?}"
            ),
        }

        shutdown_sender.send(()).expect("stop mock server");
        server.join().expect("mock server exits cleanly");
        get_client_scope_pools().remove(&client_id);
        unregister_client(client_id);

        // Companion (A-B bound): the same cross-slot command OUTSIDE a MULTI must
        // still be rejected locally and at once.
        let (port2, shutdown_sender2, server2) = endpoint_answering_n_more_commands(1);
        let (scope_id2, pool2, client_id2) = borrow_cluster_scope(port2, slot_a).await;
        let parent2 = get_parent_client(client_id2).expect("parent registered");
        execute_scope_command(
            scope_id2,
            "SET",
            &[b"foo".to_vec(), b"1".to_vec()],
            Some(&parent2),
        )
        .await
        .expect("same-slot command must not be rejected");
        let err = execute_scope_command(
            scope_id2,
            "SET",
            &[b"bar".to_vec(), b"2".to_vec()],
            Some(&parent2),
        )
        .await
        .expect_err("a cross-slot command outside MULTI must still be rejected locally");
        assert_eq!(err.kind(), redis::ErrorKind::CrossSlot);

        let _ = pool.lock().await;
        let _ = pool2.lock().await;
        shutdown_sender2.send(()).expect("stop mock server");
        server2.join().expect("mock server exits cleanly");
        get_client_scope_pools().remove(&client_id2);
        unregister_client(client_id2);
    }

    // ── Unix domain sockets (#4878) ─────────────────────────────────────────

    #[cfg(unix)]
    #[test]
    fn unix_socket_scope_addr_uses_the_socket_seed() {
        use super::unix_socket_scope_addr;
        use crate::connection_request::TlsMode;
        let unix = NodeAddress {
            unix_socket_path: Some("/tmp/valkey.sock".into()),
            ..Default::default()
        };
        let addr = unix_socket_scope_addr(&unix, TlsMode::NoTls).expect("valid socket seed");
        assert_eq!(
            addr,
            Some(redis::ConnectionAddr::Unix("/tmp/valkey.sock".into()))
        );

        for tls_mode in [TlsMode::SecureTls, TlsMode::InsecureTls] {
            let err = unix_socket_scope_addr(&unix, tls_mode)
                .expect_err("TLS over a Unix socket must fail closed");
            assert!(
                matches!(&err, ScopeCreateError::InvalidUnixSocketAddress(reason) if reason.contains("TLS")),
                "{tls_mode:?}: {err}"
            );
        }

        let tcp = NodeAddress {
            host: "127.0.0.1".into(),
            port: 6379,
            ..Default::default()
        };
        assert_eq!(unix_socket_scope_addr(&tcp, TlsMode::NoTls).unwrap(), None);
    }
}
