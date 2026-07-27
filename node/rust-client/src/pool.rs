// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Node.js N-API bindings for client-instance pool and isolated execution scopes.
//!
//! Uses the same deferred Promise pattern as GlideClientHandle for async work:
//! synchronous N-API call spawns work on a runtime, resolves/rejects via Deferred.

#![allow(dead_code)]

use glide_core::client::{Client, ConnectionRequest};
use glide_core::connection_request::ConnectionRequest as ProtobufConnectionRequest;
use glide_core::pool::{self, ClientPool, POOL_RUNNING, PoolConfig};
use glide_core::scope;
use napi::bindgen_prelude::*;
use napi::{Env, Error, Result, Status};
use napi_derive::napi;
use protobuf::Message;
use std::sync::OnceLock;
use std::sync::atomic::Ordering;
use std::time::Duration;

// ═══════════════════════════════════════════════════════════════════════════════

static POOL_RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();

fn get_pool_runtime() -> &'static tokio::runtime::Runtime {
    POOL_RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .worker_threads(2)
            .thread_name("glide-node-pool")
            .build()
            .expect("Failed to create pool runtime")
    })
}

// ═══════════════════════════════════════════════════════════════════════════════
// POOL CONFIG / METRICS
// ═══════════════════════════════════════════════════════════════════════════════

#[napi(object)]
pub struct PoolConfigNapi {
    pub max_size: u32,
    pub min_idle: u32,
    pub idle_timeout_ms: u32,
    pub request_timeout_ms: u32,
    /// Maximum inactivity time for a borrowed client before the pool reclaims it (ms).
    /// The timer resets on every command sent.
    /// The abandon monitor skips clients executing blocking commands (BLPOP, XREAD BLOCK, etc.).
    /// Set to 0 to disable abandon detection. Default: 300000 (5 minutes).
    pub abandon_timeout_ms: u32,
}

#[napi(object)]
pub struct PoolMetrics {
    pub idle: u32,
    pub active: u32,
    pub total: u32,
}

// ═══════════════════════════════════════════════════════════════════════════════
// POOL LIFECYCLE (synchronous N-API — no runtime needed)
// ═══════════════════════════════════════════════════════════════════════════════

#[napi]
pub fn create_pool(
    connection_request_bytes: Uint8Array,
    pool_config: PoolConfigNapi,
) -> Result<i64> {
    let conn_req_bytes = connection_request_bytes.as_ref().to_vec();

    let proto_req = ProtobufConnectionRequest::parse_from_bytes(&conn_req_bytes).map_err(|e| {
        Error::new(
            Status::InvalidArg,
            format!("Invalid connection request: {e}"),
        )
    })?;

    if proto_req.pubsub_subscriptions.is_some() {
        return Err(Error::new(
            Status::InvalidArg,
            "Pool clients cannot have pubsub subscriptions configured.",
        ));
    }

    let configured_database_id = proto_req.database_id;

    let config = PoolConfig {
        max_size: pool_config.max_size,
        min_idle: pool_config.min_idle,
        idle_timeout: Duration::from_millis(pool_config.idle_timeout_ms as u64),
        request_timeout: Duration::from_millis(pool_config.request_timeout_ms as u64),
        test_on_borrow: false,
        connection_request: conn_req_bytes.clone(),
        is_async: true,
        configured_database_id,
        abandon_timeout: Duration::from_millis(pool_config.abandon_timeout_ms as u64),
    };

    let pool = ClientPool::new(config)
        .map_err(|e| Error::new(Status::InvalidArg, format!("Invalid pool config: {e}")))?;

    let pool_id = pool::register_pool(pool) as i64;

    // Start abandon monitor
    pool::start_abandon_monitor(pool_id as u64, get_pool_runtime().handle());

    // Background warmup
    let min_idle = pool_config.min_idle;
    let conn_bytes = conn_req_bytes;
    get_pool_runtime().spawn(async move {
        for _ in 0..min_idle {
            let connection_request = match ProtobufConnectionRequest::parse_from_bytes(&conn_bytes)
            {
                Ok(req) => req,
                Err(_) => break,
            };
            let mut internal_req: ConnectionRequest = connection_request.into();
            internal_req.address_resolver = None;

            match Client::new(internal_req, None).await {
                Ok(client) => {
                    let registry = pool::get_pool_registry();
                    if let Some(pool_entry) = registry.get(&(pool_id as u64)) {
                        let mut pool_guard = pool_entry.value().lock().await;
                        if pool_guard.state.load(Ordering::Acquire) == POOL_RUNNING {
                            let cid = pool_guard.add_client(client.clone());
                            scope::register_client(cid, client);
                            let (_, condvar) = &*pool_guard.release_notify;
                            condvar.notify_all();
                        }
                    }
                }
                Err(e) => {
                    logger_core::log_warn("pool", format!("Background warmup failed: {e}"));
                }
            }
        }
    });

    Ok(pool_id)
}

#[napi]
pub fn pool_try_acquire(pool_id: i64) -> Result<i64> {
    let registry = pool::get_pool_registry();
    let pool_entry = registry
        .get(&(pool_id as u64))
        .ok_or_else(|| Error::new(Status::InvalidArg, "Invalid pool_id"))?;

    let result = get_pool_runtime().block_on(async {
        let mut pool_guard = pool_entry.value().lock().await;
        pool_guard.try_acquire()
    });

    Ok(result)
}

/// Acquire with timeout — returns `Promise<number>` via deferred.
#[napi(ts_return_type = "Promise<number>")]
pub fn pool_acquire_blocking<'a>(
    env: &'a Env,
    pool_id: i64,
    timeout_ms: u32,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let pool_id_u64 = pool_id as u64;
    let timeout = Duration::from_millis(timeout_ms as u64);

    get_pool_runtime().spawn(async move {
        let deadline = tokio::time::Instant::now() + timeout;
        let poll_interval = Duration::from_millis(5);
        let result_value: i64;

        loop {
            let registry = pool::get_pool_registry();
            let pool_entry = match registry.get(&pool_id_u64) {
                Some(e) => e.value().clone(),
                None => {
                    result_value = -2;
                    break;
                }
            };

            let result = {
                let mut pool_guard = pool_entry.lock().await;
                pool_guard.try_acquire()
            };

            if result >= 0 {
                result_value = result;
                break;
            }
            if result == -1 {
                result_value = -2;
                break;
            }

            if tokio::time::Instant::now() >= deadline {
                result_value = -1;
                break;
            }

            tokio::time::sleep(poll_interval).await;
        }

        deferred.resolve(move |_| Ok(result_value));
    });

    Ok(promise)
}

/// Release a client back to the pool. Returns Promise that resolves when release completes.
#[napi(ts_return_type = "Promise<void>")]
pub fn pool_release<'a>(env: &'a Env, pool_id: i64, client_id: i64) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let pool_arc = pool::get_pool(pool_id as u64);

    get_pool_runtime().spawn(async move {
        if let Some(arc) = pool_arc {
            pool::release_client_async(arc, client_id as u64).await;
        }
        deferred.resolve(move |_| Ok(()));
    });

    Ok(promise)
}

#[napi]
pub fn pool_metrics(pool_id: i64) -> Result<PoolMetrics> {
    let registry = pool::get_pool_registry();
    let pool_entry = registry
        .get(&(pool_id as u64))
        .ok_or_else(|| Error::new(Status::InvalidArg, "Invalid pool_id"))?;

    let (idle, active, total) = get_pool_runtime().block_on(async {
        let pool_guard = pool_entry.value().lock().await;
        let idle = pool_guard.idle.len() as u32;
        let active = pool_guard.in_use.len() as u32;
        let total = pool_guard.total_count.load(Ordering::Acquire);
        (idle, active, total)
    });

    Ok(PoolMetrics {
        idle,
        active,
        total,
    })
}

#[napi]
pub fn pool_destroy(pool_id: i64) {
    if let Some(pool_arc) = pool::unregister_pool(pool_id as u64) {
        get_pool_runtime().block_on(async {
            let mut pool_guard = pool_arc.lock().await;
            pool_guard.destroy();
        });
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// POOL COMMAND EXECUTION — deferred Promise pattern
// ═══════════════════════════════════════════════════════════════════════════════

/// Execute a command on a pool client. Returns `Promise<string | null>`.
#[napi(ts_return_type = "Promise<string | null>")]
pub fn pool_execute_command<'a>(
    env: &'a Env,
    client_id: i64,
    cmd_bytes: Uint8Array,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let cmd_data = cmd_bytes.as_ref().to_vec();
    let client_id_u64 = client_id as u64;

    get_pool_runtime().spawn(async move {
        let parsed = scope::deserialize_command(&cmd_data);
        let (cmd_name, args) = match parsed {
            Some(p) => p,
            None => {
                deferred.reject(Error::new(
                    Status::InvalidArg,
                    "Invalid command wire format",
                ));
                return;
            }
        };

        let client_registry = scope::get_client_registry();
        let mut client = match client_registry.get_mut(&client_id_u64) {
            Some(c) => c,
            None => {
                deferred.reject(Error::new(
                    Status::InvalidArg,
                    format!("No client registered for client_id {client_id_u64}"),
                ));
                return;
            }
        };

        let mut cmd = redis::Cmd::new();
        cmd.arg(cmd_name.as_bytes());
        for arg in &args {
            cmd.arg(arg.as_slice());
        }

        match client.send_command(&mut cmd, None).await {
            Ok(value) => {
                let result = value_to_string(value);
                deferred.resolve(|_| Ok(result));
            }
            Err(e) => {
                deferred.reject(Error::new(Status::GenericFailure, format!("{e}")));
            }
        }
    });

    Ok(promise)
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCOPE FUNCTIONS — deferred Promise pattern
// ═══════════════════════════════════════════════════════════════════════════════

#[napi]
pub fn scope_try_acquire(
    client_id: i64,
    connection_request_bytes: Uint8Array,
    routing_slot: u16,
) -> Result<i64> {
    let conn_bytes = connection_request_bytes.as_ref().to_vec();
    let runtime = get_pool_runtime();
    let result =
        scope::try_acquire_scope(client_id as u64, conn_bytes, runtime.handle(), routing_slot);
    Ok(result)
}

/// Execute a command on a scoped connection. Returns `Promise<string | null>`.
#[napi(ts_return_type = "Promise<string | null>")]
pub fn scope_execute<'a>(
    env: &'a Env,
    scope_id: i64,
    client_id: i64,
    cmd_bytes: Uint8Array,
) -> Result<Object<'a>> {
    let (deferred, promise) = env.create_deferred()?;
    let cmd_data = cmd_bytes.as_ref().to_vec();
    let scope_id_u64 = scope_id as u64;
    let client_id_u64 = client_id as u64;

    get_pool_runtime().spawn(async move {
        let parsed = scope::deserialize_command(&cmd_data);
        let (cmd_name, args) = match parsed {
            Some(p) => p,
            None => {
                deferred.reject(Error::new(
                    Status::InvalidArg,
                    "Invalid command wire format",
                ));
                return;
            }
        };

        let client_registry = scope::get_client_registry();
        let client = client_registry
            .get(&client_id_u64)
            .map(|e| e.value().clone());

        match scope::execute_scope_command(scope_id_u64, &cmd_name, &args, client.as_ref()).await {
            Ok(value) => {
                let result = value_to_string(value);
                deferred.resolve(|_| Ok(result));
            }
            Err(e) => {
                deferred.reject(Error::new(Status::GenericFailure, format!("{e}")));
            }
        }
    });

    Ok(promise)
}

#[napi]
pub fn scope_release(scope_id: i64, client_id: i64) {
    let runtime = get_pool_runtime();
    scope::release_scope(scope_id as u64, client_id as u64, runtime.handle());
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

fn value_to_string(value: redis::Value) -> Option<String> {
    match value {
        redis::Value::Nil => None,
        redis::Value::Okay => Some("OK".to_string()),
        redis::Value::Int(i) => Some(i.to_string()),
        redis::Value::BulkString(bytes) => Some(String::from_utf8_lossy(&bytes).to_string()),
        redis::Value::SimpleString(s) => Some(s),
        redis::Value::Array(arr) => {
            let parts: Vec<String> = arr
                .into_iter()
                .map(|v| match v {
                    redis::Value::Nil => "null".to_string(),
                    redis::Value::Okay => "OK".to_string(),
                    redis::Value::Int(i) => i.to_string(),
                    redis::Value::BulkString(b) => String::from_utf8_lossy(&b).to_string(),
                    redis::Value::SimpleString(s) => s,
                    _ => format!("{:?}", v),
                })
                .collect();
            Some(format!("[{}]", parts.join(",")))
        }
        other => Some(format!("{:?}", other)),
    }
}
