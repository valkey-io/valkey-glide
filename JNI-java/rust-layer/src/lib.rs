//! Valkey-Glide JNI layer.
//! Concise, reliable bridge between Java and glide-core with explicit resource management.

use anyhow::Result;
use dashmap::DashMap;
use glide_core::client::Client as GlideClient;
use glide_core::client::{
    AuthenticationInfo, ConnectionRequest, DEFAULT_MAX_INFLIGHT_REQUESTS, NodeAddress, TlsMode,
};
use jni::JNIEnv;
use jni::JavaVM;
use jni::objects::{GlobalRef, JByteArray, JClass, JObject, JStaticMethodID, JString, JValue};
use jni::sys::{jboolean, jint, jlong, jobject, jstring};
use redis::{ProtocolVersion, PubSubSubscriptionKind};

// Type aliases for complex types to satisfy clippy::type_complexity
type PushMessageTuple = (Vec<u8>, Vec<u8>, Option<Vec<u8>>);
type BatchParseResult = (
    Vec<ValkeyCmd>,
    bool,
    Option<u32>,
    bool,
    Option<crate::command_parser::RouteInfo>,
    bool,
);

// ==================== SCRIPT RESOLVER JNI ====================

// We don't need local script storage - glide-core handles this
// Just track refcounts for cleanup

static SCRIPT_REFCOUNT: std::sync::OnceLock<
    parking_lot::RwLock<std::collections::HashMap<String, usize>>,
> = std::sync::OnceLock::new();
fn get_script_refcount() -> &'static parking_lot::RwLock<std::collections::HashMap<String, usize>> {
    SCRIPT_REFCOUNT.get_or_init(|| parking_lot::RwLock::new(std::collections::HashMap::new()))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript(
    env: JNIEnv,
    _class: JClass,
    code: JByteArray,
) -> jstring {
    // Convert byte[] to Vec<u8>
    let bytes = match env.convert_byte_array(&code) {
        Ok(b) => b,
        Err(e) => {
            log::error!("ScriptResolver.storeScript: failed to read bytes: {e}");
            return std::ptr::null_mut();
        }
    };

    // Use glide-core's script container to store the script
    let sha1_hash = glide_core::scripts_container::add_script(&bytes);

    // Track refcount for Java-side lifecycle
    let refcount = get_script_refcount();
    {
        let mut map = refcount.write();
        let c = map.entry(sha1_hash.clone()).or_insert(0);
        let old_count = *c;
        *c += 1;
        let new_count = *c;
        log::info!(
            "storeScript: hash={}, old_count={}, new_count={}",
            sha1_hash,
            old_count,
            new_count
        );
    }
    match env.new_string(sha1_hash) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            log::error!("ScriptResolver.storeScript: failed to create jstring: {e}");
            std::ptr::null_mut()
        }
    }
}

/// Drop a script from the script resolver.
///
/// # Safety
/// This function is unsafe because it dereferences raw JNI pointers.
/// The caller must ensure that the JNI environment and hash parameter are valid.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript(
    mut env: JNIEnv,
    _class: JClass,
    hash: jstring,
) {
    unsafe {
        if hash.is_null() {
            return;
        }
        let h = JString::from_raw(hash);
        let s = match env.get_string(&h) {
            Ok(js) => js.to_string_lossy().to_string(),
            Err(e) => {
                log::error!("ScriptResolver.dropScript: failed to read hash: {e}");
                return;
            }
        };
        let refcount = get_script_refcount();
        let mut map = refcount.write();
        if let Some(c) = map.get_mut(&s) {
            let old_count = *c;
            if *c > 0 {
                *c -= 1;
            }
            let new_count = *c;
            log::info!(
                "dropScript: hash={}, old_count={}, new_count={}",
                s,
                old_count,
                new_count
            );
            if *c == 0 {
                map.remove(&s);
                // Also remove from glide-core's script container
                glide_core::scripts_container::remove_script(&s);
                log::info!("dropScript: removed from both refcount maps: hash={}", s);
            }
        } else {
            log::info!("dropScript: hash not found in refcount map: hash={}", s);
        }
    }
}

use redis::{Cmd as ValkeyCmd, Pipeline as ValkeyPipeline};
use redis::{Value as ServerValue, cmd};
use std::sync::{Arc, Mutex};
static JVM: std::sync::OnceLock<Arc<JavaVM>> = std::sync::OnceLock::new();
use std::sync::mpsc::{channel, Sender};
use std::thread;
use tokio::runtime::Runtime;

// Optional: jemalloc for better concurrent allocation (Unix-like systems)
#[cfg(all(feature = "fast-alloc", not(target_os = "windows")))]
#[global_allocator]
static ALLOC: tikv_jemallocator::Jemalloc = tikv_jemallocator::Jemalloc;

mod binary_protocol;
mod command_parser;
mod large_data_handler;
mod response_converter;
mod stats;
pub use command_parser::*;
pub use large_data_handler::*;
pub use response_converter::*;
use stats::{
    is_enabled as stats_enabled, record_client_closed, record_client_created, record_lazy_realized,
};

/// Export response class cache for efficient JNI conversion operations
pub(crate) fn get_cached_response_classes(
    env: &mut JNIEnv,
) -> Result<crate::response_converter::ResponseClassCache> {
    crate::response_converter::get_response_class_cache(env)
}

// ==================== HELPER FUNCTIONS ====================

/// Configuration struct to avoid too many function parameters
#[derive(Debug)]
struct ValkeyClientConfig {
    addresses: Vec<String>,
    database_id: u32,
    username: Option<String>,
    password: Option<String>,
    use_tls: bool,
    insecure_tls: bool,
    cluster_mode: bool,
    request_timeout_ms: u64,
    connection_timeout_ms: u64,
    read_from: Option<String>,
    client_az: Option<String>,
    lazy_connect: bool,
    client_name: Option<String>,
}

/// Helper function to create Valkey connection configuration
fn create_valkey_connection_config(config: ValkeyClientConfig) -> Result<ConnectionRequest> {
    let ValkeyClientConfig {
        addresses,
        database_id,
        username,
        password,
        use_tls,
        insecure_tls,
        cluster_mode,
        request_timeout_ms,
        connection_timeout_ms,
        read_from,
        client_az,
        lazy_connect,
        client_name,
    } = config;
    if addresses.is_empty() {
        return Err(anyhow::anyhow!("No addresses provided"));
    }

    // Parse addresses into node addresses
    let mut node_addresses = Vec::new();
    for addr in addresses {
        let parts: Vec<&str> = addr.split(':').collect();
        if parts.len() != 2 {
            return Err(anyhow::anyhow!("Invalid address format: {addr}"));
        }
        let host = parts[0].to_string();
        let port = parts[1]
            .parse::<u16>()
            .map_err(|_| anyhow::anyhow!("Invalid port in address: {addr}"))?;
        node_addresses.push(NodeAddress { host, port });
    }

    let connection_request = ConnectionRequest {
        addresses: node_addresses,
        tls_mode: Some(if insecure_tls { TlsMode::InsecureTls } else if use_tls { TlsMode::SecureTls } else { TlsMode::NoTls }),
        cluster_mode_enabled: cluster_mode,
        database_id: database_id as i64,
        authentication_info: match (username, password) {
            (Some(user), Some(pass)) => Some(AuthenticationInfo {
                username: Some(user),
                password: Some(pass),
            }),
            (None, Some(pass)) => Some(AuthenticationInfo {
                username: None,
                password: Some(pass),
            }),
            _ => None,
        },
        request_timeout: Some(request_timeout_ms as u32),
        connection_timeout: Some(connection_timeout_ms as u32),
        client_name: client_name.clone(),
        read_from: read_from.map(|rf| {
            match rf.as_str() {
                "AZ_AFFINITY" => {
                    if let Some(az) = client_az.clone() {
                        glide_core::client::ReadFrom::AZAffinity(az)
                    } else {
                        log::warn!("AZ_AFFINITY requires client_az to be set, falling back to PreferReplica");
                        glide_core::client::ReadFrom::PreferReplica
                    }
                }
                "AZ_AFFINITY_REPLICAS_AND_PRIMARY" => {
                    if let Some(az) = client_az.clone() {
                        glide_core::client::ReadFrom::AZAffinityReplicasAndPrimary(az)
                    } else {
                        log::warn!("AZ_AFFINITY_REPLICAS_AND_PRIMARY requires client_az to be set, falling back to PreferReplica");
                        glide_core::client::ReadFrom::PreferReplica
                    }
                }
                "PREFER_REPLICA" => glide_core::client::ReadFrom::PreferReplica,
                "PRIMARY" => glide_core::client::ReadFrom::Primary,
                _ => {
                    log::warn!("Unknown read_from strategy: {}, using Primary", rf);
                    glide_core::client::ReadFrom::Primary
                }
            }
        }),
        protocol: None,
        connection_retry_strategy: None,
        periodic_checks: None,
        pubsub_subscriptions: None,
        inflight_requests_limit: None,
        lazy_connect,
    };

    Ok(connection_request)
}

/// Create actual glide-core Valkey client with specified configuration
async fn create_glide_client(
    connection_request: ConnectionRequest,
    push_tx: Option<tokio::sync::mpsc::UnboundedSender<redis::PushInfo>>,
) -> Result<GlideClient> {
    // Create the actual glide-core client with push sender
    let client = GlideClient::new(connection_request, push_tx)
        .await
        .map_err(|e| {
            log::error!("Failed to create glide-core client: {e}");
            anyhow::anyhow!("Failed to create glide-core client: {e}")
        })?;

    log::debug!("Successfully created glide-core client");
    Ok(client)
}

/// Runtime used for async operations.
static RUNTIME: std::sync::OnceLock<Runtime> = std::sync::OnceLock::new();

// Defaults for runtime and callback workers
const DEFAULT_RUNTIME_WORKER_THREADS: usize = 1; // Tokio runtime threads
const DEFAULT_CALLBACK_WORKER_THREADS: usize = 2; // Callback worker threads

/// Initialize or return the shared Tokio runtime.
pub(crate) fn get_runtime() -> &'static Runtime {
    RUNTIME.get_or_init(|| {
        let worker_threads = if let Ok(threads_str) = std::env::var("GLIDE_TOKIO_WORKER_THREADS") {
            threads_str.parse::<usize>().unwrap_or_else(|_| {
                log::debug!("Invalid GLIDE_TOKIO_WORKER_THREADS; using default");
                DEFAULT_RUNTIME_WORKER_THREADS
            })
        } else {
            DEFAULT_RUNTIME_WORKER_THREADS
        };

        log::debug!("Initializing Tokio runtime with {worker_threads} worker threads");

        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(worker_threads)
            .max_blocking_threads(worker_threads * 2)
            .enable_all()
            .thread_name("glide-worker")
            .thread_stack_size(2 * 1024 * 1024)
            .thread_keep_alive(std::time::Duration::from_secs(60))
            .build()
            .expect("Failed to create Tokio runtime")
    })
}

/// Handle table for native clients.
type JniHandleTable = Arc<DashMap<u64, GlideClient>>;
type PendingMap = Arc<DashMap<u64, ConnectionRequest>>;

static JNI_HANDLE_TABLE: std::sync::OnceLock<JniHandleTable> = std::sync::OnceLock::new();
static PENDING_CONFIGS: std::sync::OnceLock<PendingMap> = std::sync::OnceLock::new();

pub(crate) fn get_handle_table() -> &'static JniHandleTable {
    JNI_HANDLE_TABLE.get_or_init(|| Arc::new(DashMap::new()))
}

pub(crate) fn get_pending_map() -> &'static PendingMap {
    PENDING_CONFIGS.get_or_init(|| Arc::new(DashMap::new()))
}

async fn ensure_client_for_handle(handle_id: u64) -> Result<GlideClient> {
    let table = get_handle_table();
    if let Some(entry) = table.get(&handle_id) {
        return Ok(entry.value().clone());
    }
    // Check for pending config and create lazily
    let pending = {
        let pm = get_pending_map();
        pm.remove(&handle_id).map(|(_, cfg)| cfg)
    };
    if let Some(mut cfg) = pending {
        cfg.lazy_connect = false;
        // Setup push channel if subscriptions configured
        let has_pubsub = cfg
            .pubsub_subscriptions
            .as_ref()
            .map(|m| !m.is_empty())
            .unwrap_or(false);
        let (tx_opt, rx_opt) = if has_pubsub {
            let (tx, rx) = tokio::sync::mpsc::unbounded_channel::<redis::PushInfo>();
            (Some(tx), Some(rx))
        } else {
            (None, None)
        };
        let client = create_glide_client(cfg, tx_opt).await?;
        table.insert(handle_id, client.clone());
        if stats_enabled() {
            record_lazy_realized();
        }
        if let Some(mut rx) = rx_opt {
            let jvm_arc = JVM.get().cloned();
            let handle_for_java = handle_id as jlong;
            get_runtime().spawn(async move {
                while let Some(push) = rx.recv().await {
                    if let Some(jvm) = jvm_arc.as_ref()
                        && let Ok(mut env) = jvm.attach_current_thread_permanently()
                    {
                        use redis::{PushKind, Value};
                        let as_bytes = |v: &Value| -> Option<Vec<u8>> {
                            match v {
                                Value::BulkString(b) => Some(b.clone()),
                                _ => None,
                            }
                        };
                        type PushMessageTuple = (Vec<u8>, Vec<u8>, Option<Vec<u8>>);
                        let mapped: Option<PushMessageTuple> = match push.kind {
                            PushKind::Message | PushKind::SMessage => {
                                if push.data.len() >= 2 {
                                    let channel = as_bytes(&push.data[0]).unwrap_or_default();
                                    let message = as_bytes(&push.data[1]).unwrap_or_default();
                                    Some((message, channel, None))
                                } else {
                                    None
                                }
                            }
                            PushKind::PMessage => {
                                if push.data.len() >= 3 {
                                    let pattern = as_bytes(&push.data[0]).unwrap_or_default();
                                    let channel = as_bytes(&push.data[1]).unwrap_or_default();
                                    let message = as_bytes(&push.data[2]).unwrap_or_default();
                                    Some((message, channel, Some(pattern)))
                                } else {
                                    None
                                }
                            }
                            _ => None,
                        };
                        if let Some((m, c, p)) = mapped
                            && let Ok(class) = env.find_class("glide/internal/GlideCoreClient")
                        {
                            let jhandle = JValue::Long(handle_for_java);
                            let jm = env.byte_array_from_slice(&m).ok();
                            let jc = env.byte_array_from_slice(&c).ok();
                            let jp = p.as_ref().and_then(|pp| env.byte_array_from_slice(pp).ok());
                            if let (Some(jm), Some(jc)) = (jm, jc) {
                                let jm_obj: JObject = jm.into();
                                let jc_obj: JObject = jc.into();
                                let jp_obj: JObject = jp.map(Into::into).unwrap_or(JObject::null());
                                let _ = env.call_static_method(
                                    class,
                                    "onNativePush",
                                    "(J[B[B[B)V",
                                    &[
                                        jhandle,
                                        JValue::from(&jm_obj),
                                        JValue::from(&jc_obj),
                                        JValue::from(&jp_obj),
                                    ],
                                );
                            }
                        }
                    }
                }
            });
        }
        return Ok(table.get(&handle_id).unwrap().value().clone());
    }
    Err(anyhow::anyhow!("Client not found in handle_table"))
}

/// JNI init hook to ensure JVM cached for push callbacks
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideCoreClient_onNativeInit(
    env: JNIEnv,
    _class: JClass,
) {
    if let Ok(jvm) = env.get_java_vm() {
        let _ = JVM.set(Arc::new(jvm));
    }
}

/// Get the default inflight request limit (per-client, handled by glide-core)
pub(crate) fn get_inflight_requests_limit() -> usize {
    DEFAULT_MAX_INFLIGHT_REQUESTS as usize
}

// ==================== GLIDE-CORE DEFAULTS QUERY JNI ====================

/// Get glide-core default timeout in milliseconds
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultTimeoutMs(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    // Return glide-core's default timeout (30 seconds)
    30_000
}

/// Get glide-core default maximum inflight requests limit
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultMaxInflightRequests(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    // Return glide-core's default max inflight requests
    DEFAULT_MAX_INFLIGHT_REQUESTS as jint
}

pub(crate) fn stats_handle_table_len() -> usize {
    get_handle_table().len()
}

/// Get JNI environment by attaching to current thread
///
/// SAFETY: Each thread must attach independently to avoid cross-thread JNI issues
fn get_or_attach_jni_env(jvm: &JavaVM) -> Result<JNIEnv<'_>, jni::errors::Error> {
    jvm.attach_current_thread_permanently()
}

/// Generate unique safe handle for JNI resource management
/// These handles provide opaque references that prevent direct pointer manipulation
static NEXT_HANDLE_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

fn generate_safe_handle() -> u64 {
    NEXT_HANDLE_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
}

/// Execute a cluster scan command asynchronously.
async fn execute_cluster_scan_command(
    mut client: GlideClient,
    scan_request: crate::command_parser::ClusterScanRequest,
) -> Result<ServerValue> {
    use glide_core::client::FINISHED_SCAN_CURSOR;
    use glide_core::cluster_scan_container::get_cluster_scan_cursor;
    use redis::{ClusterScanArgs, ObjectType, ScanStateRC};

    // Get or create scan state cursor
    let scan_state_cursor: ScanStateRC = match &scan_request.cursor_id {
        Some(cursor_id) if cursor_id != FINISHED_SCAN_CURSOR => {
            get_cluster_scan_cursor(cursor_id.clone())
                .map_err(|e| anyhow::anyhow!("Invalid cursor: {}", e))?
        }
        _ => {
            // Create initial scan state using proper constructor
            ScanStateRC::new()
        }
    };

    // Build ClusterScanArgs
    let mut scan_args = ClusterScanArgs::builder();

    if let Some(pattern) = scan_request.match_pattern {
        scan_args = scan_args.with_match_pattern(pattern);
    }

    if let Some(count) = scan_request.count {
        scan_args = scan_args.with_count(count);
    }

    if let Some(object_type_str) = scan_request.object_type {
        let object_type = match object_type_str.to_uppercase().as_str() {
            "STRING" => ObjectType::String,
            "LIST" => ObjectType::List,
            "SET" => ObjectType::Set,
            "ZSET" => ObjectType::ZSet,
            "HASH" => ObjectType::Hash,
            "STREAM" => ObjectType::Stream,
            _ => return Err(anyhow::anyhow!("Invalid object type: {}", object_type_str)),
        };
        scan_args = scan_args.with_object_type(object_type);
    }

    if let Some(allow) = scan_request.allow_non_covered_slots {
        scan_args = scan_args.allow_non_covered_slots(allow);
    }

    let scan_args_final = scan_args.build();

    // Execute cluster scan using glide-core client method
    // The glide_core::client::Client::cluster_scan already handles cursor storage
    // and returns Value::Array([cursor_id, keys_array])
    match client
        .cluster_scan(&scan_state_cursor, scan_args_final)
        .await
    {
        Ok(result) => Ok(result),
        Err(err) => {
            let code = glide_core::errors::error_type(&err) as i32;
            let msg = glide_core::errors::error_message(&err);
            Err(anyhow::anyhow!(format!("ERR_CODE:{}|{}", code, msg)))
        }
    }
}

/// Execute a script through glide-core's invoke_script
async fn execute_script(
    mut client: GlideClient,
    hash: String,
    keys: Vec<Vec<u8>>,
    args: Vec<Vec<u8>>,
    routing: Option<crate::command_parser::RouteInfo>,
) -> Result<ServerValue> {
    // First check if the script exists in the JNI layer refcount
    // This enforces the lifecycle semantics: once all Script objects are closed,
    // the script should not be executable even if it exists on the server
    {
        let refcount = get_script_refcount();
        let map = refcount.read();
        let current_count = map.get(&hash).copied().unwrap_or(0);
        log::info!(
            "execute_script: checking hash={}, refcount={}",
            hash,
            current_count
        );

        if !map.contains_key(&hash) || current_count == 0 {
            // Script was removed from JNI refcount (all Java references dropped)
            // Return NOSCRIPT error as expected by tests
            log::info!(
                "execute_script: returning NOSCRIPT for hash={} (refcount={})",
                hash,
                current_count
            );
            return Err(anyhow::anyhow!(
                "NOSCRIPT No matching script. Please use EVAL."
            ));
        }
        log::info!(
            "execute_script: proceeding with hash={}, refcount={}",
            hash,
            current_count
        );
    }

    // Convert keys and args to &[u8] slices
    let keys_refs: Vec<&[u8]> = keys.iter().map(|k| k.as_slice()).collect();
    let args_refs: Vec<&[u8]> = args.iter().map(|a| a.as_slice()).collect();

    // Convert routing to glide-core format
    let routing_info = routing.as_ref().map(|r| {
        use crate::command_parser::RouteInfo as R;
        match r {
            R::Random => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::Random,
            ),
            R::AllPrimaries => redis::cluster_routing::RoutingInfo::MultiNode((
                redis::cluster_routing::MultipleNodeRoutingInfo::AllMasters,
                None,
            )),
            R::AllNodes => redis::cluster_routing::RoutingInfo::MultiNode((
                redis::cluster_routing::MultipleNodeRoutingInfo::AllNodes,
                None,
            )),
            R::PrimaryForKey(key) => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(
                    redis::cluster_routing::Route::new(
                        redis::cluster_topology::get_slot(key.as_bytes()),
                        redis::cluster_routing::SlotAddr::Master,
                    ),
                ),
            ),
            R::ReplicaForKey(key) => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(
                    redis::cluster_routing::Route::new(
                        redis::cluster_topology::get_slot(key.as_bytes()),
                        redis::cluster_routing::SlotAddr::ReplicaRequired,
                    ),
                ),
            ),
            R::SlotId { id, replica } => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(
                    redis::cluster_routing::Route::new(
                        *id,
                        if *replica {
                            redis::cluster_routing::SlotAddr::ReplicaRequired
                        } else {
                            redis::cluster_routing::SlotAddr::Master
                        },
                    ),
                ),
            ),
            R::ByAddress { host, port } => redis::cluster_routing::RoutingInfo::SingleNode(
                redis::cluster_routing::SingleNodeRoutingInfo::ByAddress {
                    host: host.clone(),
                    port: *port,
                },
            ),
        }
    });

    // Use glide-core's invoke_script which handles EVALSHA with automatic fallback to EVAL
    match client
        .invoke_script(&hash, &keys_refs, &args_refs, routing_info)
        .await
    {
        Ok(v) => Ok(v),
        Err(err) => {
            let code = glide_core::errors::error_type(&err) as i32;
            let msg = glide_core::errors::error_message(&err);
            Err(anyhow::anyhow!(format!("ERR_CODE:{}|{}", code, msg)))
        }
    }
}

/// Execute a Valkey command asynchronously.
async fn execute_command(
    mut client: GlideClient,
    command_request: crate::command_parser::CommandRequest,
) -> Result<ServerValue> {
    // Build the server command
    let server_cmd = {
        // Command type already provided as first token; any additional tokens are sent as arguments from Java.
        let cmd_type = command_request.command_type.trim();
        if cmd_type.is_empty() {
            return Err(anyhow::anyhow!("Empty command type"));
        }

        // Special handling for CUSTOM command - the first argument is the actual command
        if cmd_type.eq_ignore_ascii_case("CUSTOM") {
            if command_request.arguments.is_empty() {
                return Err(anyhow::anyhow!(
                    "CUSTOM command requires at least one argument"
                ));
            }
            // First argument is the actual command, rest are command arguments
            let mut cmd = cmd(&command_request.arguments[0]);
            for arg in &command_request.arguments[1..] {
                cmd.arg(arg);
            }
            cmd
        }
        // Special handling for SCRIPT EXISTS - it needs to be sent as a compound command
        else if cmd_type.eq_ignore_ascii_case("SCRIPT")
            && !command_request.arguments.is_empty()
            && command_request.arguments[0].eq_ignore_ascii_case("EXISTS")
        {
            // Build as "SCRIPT EXISTS" command with remaining args
            let mut cmd = cmd("SCRIPT");
            cmd.arg("EXISTS");
            // Add remaining arguments (skip the first "EXISTS")
            for arg in &command_request.arguments[1..] {
                cmd.arg(arg);
            }
            cmd
        } else {
            // Normal command - just add all arguments
            let mut cmd = cmd(cmd_type);
            for arg in &command_request.arguments {
                cmd.arg(arg);
            }
            cmd
        }
    };

    // Special handling for SCRIPT EXISTS with multi-node routes
    // When SCRIPT EXISTS is executed on multiple nodes, it returns a Map of node → boolean array
    // We need to combine these into a single boolean array using AND logic
    let is_script_exists = (command_request.command_type.eq_ignore_ascii_case("SCRIPT")
        && !command_request.arguments.is_empty()
        && command_request.arguments[0].eq_ignore_ascii_case("EXISTS"))
        || command_request
            .command_type
            .eq_ignore_ascii_case("SCRIPT EXISTS");

    // Convert routing to Valkey format
    let routing_info = command_request.to_server_routing();

    // Execute the command through glide-core
    let result = match client.send_command(&server_cmd, routing_info).await {
        Ok(v) => v,
        Err(err) => {
            let code = glide_core::errors::error_type(&err) as i32;
            let msg = glide_core::errors::error_message(&err);
            return Err(anyhow::anyhow!(format!("ERR_CODE:{}|{}", code, msg)));
        }
    };

    // Now handle special case for SCRIPT EXISTS if needed

    if is_script_exists
        && matches!(
            command_request.routing,
            Some(crate::command_parser::RouteInfo::AllNodes)
                | Some(crate::command_parser::RouteInfo::AllPrimaries)
        )
        && let ServerValue::Map(node_results) = result
    {
        // Combine results from all nodes using AND logic
        // A script exists only if it exists on ALL nodes
        let mut combined_result: Option<Vec<ServerValue>> = None;

        for (_node, node_value) in node_results {
            match node_value {
                ServerValue::Array(arr) => {
                    if let Some(ref mut combined) = combined_result {
                        // AND logic: combine with existing results
                        for (i, val) in arr.iter().enumerate() {
                            if i < combined.len() {
                                // Convert both to boolean and AND them
                                let existing = matches!(&combined[i], ServerValue::Int(1));
                                let current = matches!(val, ServerValue::Int(1));
                                combined[i] =
                                    ServerValue::Int(if existing && current { 1 } else { 0 });
                            }
                        }
                    } else {
                        // First node result, initialize combined result
                        combined_result = Some(arr);
                    }
                }
                _ => {
                    // Unexpected response type from node, skip
                }
            }
        }

        // Return the combined result as an array
        return Ok(ServerValue::Array(combined_result.unwrap_or_default()));
    }

    Ok(result)
}

/// Execute a Valkey binary command asynchronously with mixed String/byte[] arguments.
async fn execute_binary_command(
    mut client: GlideClient,
    command_request: crate::command_parser::BinaryCommandRequest,
) -> Result<ServerValue> {
    let mut server_cmd = {
        let cmd_type = command_request.command_type.trim();
        if cmd_type.is_empty() {
            return Err(anyhow::anyhow!("Empty command type"));
        }
        cmd(cmd_type)
    };

    // Add all arguments, preserving binary data
    for arg in &command_request.arguments {
        match arg {
            crate::command_parser::BinaryValue::String(s) => server_cmd.arg(s),
            crate::command_parser::BinaryValue::Binary(b) => server_cmd.arg(b),
        };
    }

    // Convert routing to Valkey format
    let routing_info = command_request.to_server_routing();

    // Execute the command through glide-core
    match client.send_command(&server_cmd, routing_info).await {
        Ok(v) => Ok(v),
        Err(err) => {
            let code = glide_core::errors::error_type(&err) as i32;
            let msg = glide_core::errors::error_message(&err);
            Err(anyhow::anyhow!(format!("ERR_CODE:{}|{}", code, msg)))
        }
    }
}

/// Cache of required Java method IDs.
#[derive(Clone)]
struct MethodCache {
    async_handle_table_class: GlobalRef,
    complete_callback_method: JStaticMethodID,
    complete_error_method: JStaticMethodID,
    complete_error_with_code_method: JStaticMethodID,
}

// Use lazy_static for thread-safe initialization with Result handling
static METHOD_CACHE: std::sync::OnceLock<Mutex<Option<MethodCache>>> = std::sync::OnceLock::new();

/// Get or initialize the method cache.
fn get_method_cache(env: &mut JNIEnv) -> Result<MethodCache> {
    let cache_mutex = METHOD_CACHE.get_or_init(|| Mutex::new(None));

    {
        let cache_guard = cache_mutex.lock().unwrap();
        if let Some(cache) = cache_guard.as_ref() {
            return Ok(cache.clone());
        }
    }

    // Cache miss: initialize
    log::debug!("Initializing JNI method cache");

    // Find the AsyncRegistry class once
    let class = env
        .find_class("glide/internal/AsyncRegistry")
        .map_err(|e| {
            anyhow::anyhow!("Failed to find AsyncRegistry class: {e}")
                .context("initializing JNI method cache")
        })?;

    // Create global reference to prevent GC collection
    let global_class = env.new_global_ref(&class).map_err(|e| {
        anyhow::anyhow!("Failed to create global class reference: {e}")
            .context("caching AsyncRegistry class reference")
    })?;

    // Cache method IDs for both success and error callbacks
    let complete_callback_method = env
        .get_static_method_id(&class, "completeCallback", "(JLjava/lang/Object;)Z")
        .map_err(|e| anyhow::anyhow!("Failed to get completeCallback method ID: {e}"))?;

    let complete_error_method = env
        .get_static_method_id(
            &class,
            "completeCallbackWithError",
            "(JLjava/lang/String;)Z",
        )
        .map_err(|e| anyhow::anyhow!("Failed to get completeCallbackWithError method ID: {e}"))?;

    let complete_error_with_code_method = env
        .get_static_method_id(
            &class,
            "completeCallbackWithErrorCode",
            "(JILjava/lang/String;)Z",
        )
        .map_err(|e| {
            anyhow::anyhow!("Failed to get completeCallbackWithErrorCode method ID: {e}")
        })?;

    let method_cache = MethodCache {
        async_handle_table_class: global_class,
        complete_callback_method,
        complete_error_method,
        complete_error_with_code_method,
    };

    // Store
    {
        let mut cache_guard = cache_mutex.lock().unwrap();
        *cache_guard = Some(method_cache.clone());
    }

    log::debug!("JNI method cache initialized");
    Ok(method_cache)
}

/// Callback job type handled by dedicated callback workers
type CallbackJob = (Arc<JavaVM>, jlong, Result<ServerValue>, bool); // bool indicates binary_mode

/// Global unbounded callback queue sender
static CALLBACK_SENDER: std::sync::OnceLock<Sender<CallbackJob>> = std::sync::OnceLock::new();

fn get_callback_worker_threads() -> usize {
    if let Ok(val) = std::env::var("GLIDE_CALLBACK_WORKER_THREADS") {
        val.parse::<usize>()
            .unwrap_or(DEFAULT_CALLBACK_WORKER_THREADS)
            .max(1)
    } else {
        DEFAULT_CALLBACK_WORKER_THREADS
    }
}

fn init_callback_workers() -> &'static Sender<CallbackJob> {
    CALLBACK_SENDER.get_or_init(|| {
        let (tx, rx) = channel::<CallbackJob>();
        let rx = Arc::new(Mutex::new(rx));
        let worker_threads = get_callback_worker_threads();

        for i in 0..worker_threads {
            let rx_clone = Arc::clone(&rx);
            thread::Builder::new()
                .name(format!("glide-jni-callback-{i}"))
                .spawn(move || {
                    loop {
                        // Receive next job (blocking). One receiver guarded by a mutex ensures fair distribution.
                        let job_opt = {
                            let guard = rx_clone.lock().unwrap();
                            guard.recv().ok()
                        };
                        let Some((jvm, callback_id, result, binary_mode)) = job_opt else { break };

                        // Process the job entirely on this dedicated callback thread
        match get_or_attach_jni_env(&jvm) {
            Ok(mut env) => match result {
                Ok(server_value) => {
                    let _ = env.push_local_frame(16);
                                    let java_result = crate::large_data_handler::LargeDataHandler::convert_with_hybrid_strategy(
                            &mut env,
                            server_value,
                            binary_mode,
                        );
                    match java_result {
                        Ok(java_result) => {
                            let _ = complete_java_callback(&mut env, callback_id, java_result);
                        }
                        Err(e) => {
                            let error_msg = format!("Response conversion failed: {e}");
                                            let _ = complete_java_callback_with_error(&mut env, callback_id, &error_msg);
                                        }
                                    }
                    let _ = unsafe { env.pop_local_frame(&JObject::null()) };
                }
                Err(e) => {
                    // Try to extract structured code prefix ERR_CODE:<n>|message
                    let s = e.to_string();
                    if let Some(rest) = s.strip_prefix("ERR_CODE:") {
                        let parts: Vec<&str> = rest.splitn(2, '|').collect();
                        if parts.len() == 2
                            && let Ok(code) = parts[0].parse::<i32>() {
                                let msg = parts[1];
                                // Call AsyncRegistry.completeCallbackWithErrorCode(correlationId, code, message)
                                let method_cache = match get_method_cache(&mut env) { Ok(m) => m, Err(_) => { let _ = complete_java_callback_with_error(&mut env, callback_id, &s); continue; } };
                                let jmsg = match env.new_string(msg) { Ok(m) => m, Err(_) => { let _ = complete_java_callback_with_error(&mut env, callback_id, &s); continue; } };
                                unsafe {
                                    let _ = env.call_static_method_unchecked(
                                        &method_cache.async_handle_table_class,
                                        method_cache.complete_error_with_code_method,
                                        jni::signature::ReturnType::Primitive(jni::signature::Primitive::Boolean),
                                        &[
                                            JValue::Long(callback_id).as_jni(),
                                            JValue::Int(code).as_jni(),
                                            JValue::Object(&jmsg).as_jni(),
                                        ],
                                    );
                                }
                                continue;
                            }
                    }
                    let _ = complete_java_callback_with_error(&mut env, callback_id, &s);
                }
            },
            Err(e) => {
                log::error!("JNI environment attachment failed: {e}");
            }
        }
                    }
                })
                .expect("Failed to spawn callback worker thread");
        }

        tx
    })
}

/// Enqueue callback job to dedicated workers.
fn complete_callback(
    jvm: Arc<JavaVM>,
    callback_id: jlong,
    result: Result<ServerValue>,
    binary_mode: bool,
) {
    let sender = init_callback_workers();
    // Enforce backpressure strictly on dedicated callback workers.
    // We block here rather than falling back to the main runtime to keep runtimes isolated.
    if let Err(e) = sender.send((jvm, callback_id, result, binary_mode)) {
        log::error!("Callback queue send failed: {e}");
    }
}

/// Complete Java CompletableFuture with success result using cached method IDs.
fn complete_java_callback(env: &mut JNIEnv, callback_id: jlong, result: jobject) -> Result<()> {
    // Use cached method IDs instead of expensive lookups
    let method_cache = get_method_cache(env)?;
    // SAFETY: The result jobject is a valid Java object returned from conversion
    let result_obj = unsafe { JObject::from_raw(result) };

    // SAFETY: cached valid method IDs; parameters match expected signature
    unsafe {
        env.call_static_method_unchecked(
            &method_cache.async_handle_table_class,
            method_cache.complete_callback_method,
            jni::signature::ReturnType::Primitive(jni::signature::Primitive::Boolean),
            &[
                JValue::Long(callback_id).as_jni(),
                JValue::Object(&result_obj).as_jni(),
            ],
        )
    }?;

    Ok(())
}

/// Complete Java CompletableFuture with error using cached method IDs.
fn complete_java_callback_with_error(
    env: &mut JNIEnv,
    callback_id: jlong,
    error: &str,
) -> Result<()> {
    // Use cached method IDs instead of expensive lookups
    let method_cache = get_method_cache(env)?;
    let error_string = env.new_string(error)?;

    // SAFETY: cached valid method IDs; parameters match expected signature
    unsafe {
        env.call_static_method_unchecked(
            &method_cache.async_handle_table_class,
            method_cache.complete_error_method,
            jni::signature::ReturnType::Primitive(jni::signature::Primitive::Boolean),
            &[
                JValue::Long(callback_id).as_jni(),
                JValue::Object(&error_string).as_jni(),
            ],
        )
    }?;

    Ok(())
}

/// Extract optional string parameter from JNI.
fn get_optional_string_param_raw(env: &mut JNIEnv, param: jstring) -> Option<String> {
    if param.is_null() {
        return None;
    }
    // SAFETY: param is a valid local ref or null (checked above); from_raw consumes it
    unsafe {
        let js = jni::objects::JString::from_raw(param);
        env.get_string(&js)
            .ok()
            .map(|s| s.to_str().unwrap_or("").to_string())
    }
}

/// Get jemalloc memory statistics.
#[cfg(feature = "fast-alloc")]
pub fn get_memory_stats() -> Result<String> {
    // Simplified memory stats - jemalloc stats require specific feature flags
    // For now, return basic system memory information
    Ok("jemalloc enabled; detailed stats require 'stats' feature".to_string())
}

#[cfg(not(feature = "fast-alloc"))]
pub fn get_memory_stats() -> Result<String> {
    Ok("Memory stats not available (jemalloc not enabled)".to_string())
}

// ==================== JNI EXPORTED FUNCTIONS ====================

/// Create Valkey client and store handle.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_createClient(
    mut env: JNIEnv,
    _class: JClass,
    addresses: jni::objects::JObjectArray,
    database_id: jint,
    username: jstring,
    password: jstring,
    use_tls: jboolean,
    insecure_tls: jboolean,
    cluster_mode: jboolean,
    request_timeout_ms: jint,
    connection_timeout_ms: jint,
    max_inflight_requests: jint,
    _native_direct_memory_mb: jint, // Unused after memory control removal
    protocol: jni::objects::JString,
    sub_exact: jni::objects::JObjectArray,   // byte[][]
    sub_pattern: jni::objects::JObjectArray, // byte[][]
    sub_sharded: jni::objects::JObjectArray, // byte[][]
    read_from: jni::objects::JString,
    client_az: jni::objects::JString,
    lazy_connect: jboolean,
    client_name: jni::objects::JString,
    reconnect_num_retries: jint,
    reconnect_exponent_base: jint,
    reconnect_factor: jint,
    reconnect_jitter_percent: jint,
) -> jlong {
    // Convert Java parameters to Rust types
    let addresses_result: Result<Vec<String>> = (|| {
        let length = env.get_array_length(&addresses)? as usize;
        let mut addrs = Vec::with_capacity(length);

        for i in 0..length {
            let addr_obj = env.get_object_array_element(&addresses, i as i32)?;
            let addr_jstring = jni::objects::JString::from(addr_obj);
            let addr_str = env.get_string(&addr_jstring)?;
            addrs.push(addr_str.to_str()?.to_string());
        }
        Ok(addrs)
    })();

    let addresses = match addresses_result {
        Ok(addrs) => addrs,
        Err(e) => {
            log::error!("Failed to parse addresses: {e}");
            return 0;
        }
    };

    let username = get_optional_string_param_raw(&mut env, username);
    let password = get_optional_string_param_raw(&mut env, password);

    // Extract routing configuration parameters
    let read_from_str = if !read_from.is_null() {
        get_optional_string_param_raw(&mut env, read_from.as_raw())
            .map(|s| s.trim().to_ascii_uppercase())
            .filter(|s| !s.is_empty())
    } else {
        None
    };

    let client_az_str = if !client_az.is_null() {
        get_optional_string_param_raw(&mut env, client_az.as_raw())
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
    } else {
        None
    };

    // Create connection configuration
    let mut config = match create_valkey_connection_config(ValkeyClientConfig {
        addresses,
        database_id: database_id as u32,
        username,
        password,
        use_tls: use_tls != 0,
        insecure_tls: insecure_tls != 0,
        cluster_mode: cluster_mode != 0,
        request_timeout_ms: request_timeout_ms as u64,
        connection_timeout_ms: connection_timeout_ms as u64,
        read_from: read_from_str.clone(),
        client_az: client_az_str.clone(),
        lazy_connect: lazy_connect != 0,
        client_name: None,
    }) {
        Ok(config) => config,
        Err(e) => {
            log::error!("Failed to create connection config: {e}");
            return 0;
        }
    };

    // Apply reconnect/backoff strategy if provided (positive values indicate configured)
    if reconnect_num_retries > 0 && reconnect_exponent_base > 0 && reconnect_factor > 0 {
        config.connection_retry_strategy = Some(glide_core::client::ConnectionRetryStrategy {
            exponent_base: reconnect_exponent_base as u32,
            factor: reconnect_factor as u32,
            number_of_retries: reconnect_num_retries as u32,
            jitter_percent: if reconnect_jitter_percent >= 0 {
                Some(reconnect_jitter_percent as u32)
            } else {
                None
            },
        });
    }

    // Apply client name if provided
    if !client_name.is_null()
        && let Some(name) = get_optional_string_param_raw(&mut env, client_name.as_raw())
        && !name.trim().is_empty()
    {
        config.client_name = Some(name);
    }

    if std::env::var("GLIDE_DEBUG_CLIENT_INIT")
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false)
    {
        let provided_name = config
            .client_name
            .clone()
            .unwrap_or_else(|| "<none>".to_string());
        let auth_present = config.authentication_info.is_some();
        let address_count = config.addresses.len();
        let read_from_kind = config
            .read_from
            .as_ref()
            .map(|rf| format!("{:?}", rf))
            .unwrap_or_else(|| "<none>".to_string());
        log::info!(
            "Client init debug: cluster_mode_enabled={} lazy_connect={} client_name={} auth_present={} addresses={} db={} request_timeout={:?} connection_timeout={:?} read_from={}",
            config.cluster_mode_enabled,
            config.lazy_connect,
            provided_name,
            auth_present,
            address_count,
            config.database_id,
            config.request_timeout,
            config.connection_timeout,
            read_from_kind
        );
    }

    // Apply protocol
    if !protocol.is_null() {
        let p = env
            .get_string(&protocol)
            .ok()
            .map(|s| s.to_string_lossy().to_string())
            .unwrap_or("RESP3".to_string());
        config.protocol = Some(match p.as_str() {
            "RESP2" => ProtocolVersion::RESP2,
            _ => ProtocolVersion::RESP3,
        });
    }

    if let Some(ref rf) = read_from_str {
        log::debug!("ReadFrom strategy: {}", rf);
        if let Some(ref az) = client_az_str {
            log::debug!("Client availability zone: {}", az);
        }
    }

    // Map subscriptions
    let mut subs: redis::PubSubSubscriptionInfo = std::collections::HashMap::new();
    let mut add_list = |kind: PubSubSubscriptionKind, arr: jni::objects::JObjectArray| {
        if arr.is_null() {
            return;
        }
        let len = env.get_array_length(&arr).unwrap_or(0) as usize;
        if len == 0 {
            return;
        }
        let mut set: std::collections::HashSet<Vec<u8>> =
            std::collections::HashSet::with_capacity(len);
        for i in 0..len as i32 {
            if let Ok(elem) = env.get_object_array_element(&arr, i) {
                // Determine if element is byte[] or String
                let is_byte_array = env.is_instance_of(&elem, "[B").unwrap_or(false);
                if is_byte_array {
                    let byte_arr: jni::objects::JByteArray = jni::objects::JByteArray::from(elem);
                    if let Ok(bytes) = env.convert_byte_array(&byte_arr) {
                        set.insert(bytes);
                    }
                } else {
                    let js: jni::objects::JString = jni::objects::JString::from(elem);
                    if let Ok(s) = env.get_string(&js) {
                        set.insert(s.to_bytes().to_vec());
                    }
                }
            }
        }
        if !set.is_empty() {
            subs.insert(kind, set);
        }
    };
    add_list(PubSubSubscriptionKind::Exact, sub_exact);
    add_list(PubSubSubscriptionKind::Pattern, sub_pattern);
    add_list(PubSubSubscriptionKind::Sharded, sub_sharded);
    if !subs.is_empty() {
        config.pubsub_subscriptions = Some(subs);
    }

    // Set per-client inflight limit (handled by glide-core)
    if max_inflight_requests > 0 {
        config.inflight_requests_limit = Some(max_inflight_requests as u32);
    }

    /* MEMORY CONTROL DISABLED - Configure native direct memory cap for DirectByteBuffer safety
    if native_direct_memory_mb > 0 {
        crate::large_data_handler::LargeDataHandler::set_memory_cap_mb(
            native_direct_memory_mb as usize,
        );
    }
    */

    // Cache JVM for push callbacks
    if let Ok(jvm) = env.get_java_vm() {
        let _ = JVM.set(Arc::new(jvm));
    }

    // If lazy_connect is requested, store pending config and return handle without creating sockets
    if lazy_connect != 0 {
        let handle_id = generate_safe_handle();
        get_pending_map().insert(handle_id, config);
        if stats_enabled() {
            record_client_created(true);
        }
        log::debug!("Registered lazy client handle: {handle_id}");
        return handle_id as jlong;
    }

    // Eager creation path
    let runtime = get_runtime();
    let has_pubsub = config
        .pubsub_subscriptions
        .as_ref()
        .map(|m| !m.is_empty())
        .unwrap_or(false);
    let (tx_opt, rx_opt) = if has_pubsub {
        let (tx, rx) = tokio::sync::mpsc::unbounded_channel::<redis::PushInfo>();
        (Some(tx), Some(rx))
    } else {
        (None, None)
    };

    match runtime.block_on(async { create_glide_client(config, tx_opt).await }) {
        Ok(client) => {
            let safe_handle = generate_safe_handle();
            let handle_table = get_handle_table();

            // Store in handle table
            handle_table.insert(safe_handle, client);

            // If we created a push channel, restart a delivery task bound to this handle
            // by replacing the earlier placeholder with a real task
            if has_pubsub {
                // Retrieve client to keep it alive
                let _ = handle_table.get(&safe_handle);
                if let Some(mut rx) = rx_opt {
                    let jvm_arc = JVM.get().cloned();
                    let handle_for_java = safe_handle as jlong;
                    get_runtime().spawn(async move {
                        while let Some(push) = rx.recv().await {
                            if let Some(jvm) = jvm_arc.as_ref()
                                && let Ok(mut env) = jvm.attach_current_thread_permanently()
                            {
                                // Map push to bytes (message, channel, pattern)
                                use redis::{PushKind, Value};
                                let as_bytes = |v: &Value| -> Option<Vec<u8>> {
                                    match v {
                                        Value::BulkString(b) => Some(b.clone()),
                                        _ => None,
                                    }
                                };
                                let mapped: Option<PushMessageTuple> = match push.kind {
                                    PushKind::Message | PushKind::SMessage => {
                                        if push.data.len() >= 2 {
                                            let channel =
                                                as_bytes(&push.data[0]).unwrap_or_default();
                                            let message =
                                                as_bytes(&push.data[1]).unwrap_or_default();
                                            Some((message, channel, None))
                                        } else {
                                            None
                                        }
                                    }
                                    PushKind::PMessage => {
                                        if push.data.len() >= 3 {
                                            let pattern =
                                                as_bytes(&push.data[0]).unwrap_or_default();
                                            let channel =
                                                as_bytes(&push.data[1]).unwrap_or_default();
                                            let message =
                                                as_bytes(&push.data[2]).unwrap_or_default();
                                            Some((message, channel, Some(pattern)))
                                        } else {
                                            None
                                        }
                                    }
                                    _ => None,
                                };
                                if let Some((m, c, p)) = mapped
                                    && let Ok(class) =
                                        env.find_class("glide/internal/GlideCoreClient")
                                {
                                    let jhandle = JValue::Long(handle_for_java);
                                    let jm = env.byte_array_from_slice(&m).ok();
                                    let jc = env.byte_array_from_slice(&c).ok();
                                    let jp = p
                                        .as_ref()
                                        .and_then(|pp| env.byte_array_from_slice(pp).ok());
                                    if let (Some(jm), Some(jc)) = (jm, jc) {
                                        let jm_obj: JObject = jm.into();
                                        let jc_obj: JObject = jc.into();
                                        let jp_obj: JObject =
                                            jp.map(Into::into).unwrap_or(JObject::null());
                                        let _ = env.call_static_method(
                                            class,
                                            "onNativePush",
                                            "(J[B[B[B)V",
                                            &[
                                                jhandle,
                                                JValue::from(&jm_obj),
                                                JValue::from(&jc_obj),
                                                JValue::from(&jp_obj),
                                            ],
                                        );
                                    }
                                }
                            }
                        }
                    });
                }
            }

            if stats_enabled() {
                record_client_created(false);
            }
            log::debug!("Created client with handle: {safe_handle}");
            safe_handle as jlong
        }
        Err(e) => {
            log::error!("Failed to create client: {e}");
            // Throw a Java exception with the actual error message
            let error_msg = format!("Failed to create client: {}", e);
            if let Err(jni_err) = env.throw_new("java/lang/RuntimeException", error_msg) {
                log::error!("Failed to throw Java exception: {jni_err}");
            }
            0
        }
    }
}

/// Execute Valkey command asynchronously.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeCommandAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    request_bytes: JByteArray,
    callback_id: jlong,
) {
    let raw_bytes = match env.convert_byte_array(&request_bytes) {
        Ok(b) => b,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to read command bytes: {e}"),
            );
            return;
        }
    };
    if raw_bytes.is_empty() {
        let _ =
            complete_java_callback_with_error(&mut env, callback_id, "Empty command request bytes");
        return;
    }
    let command_request = match crate::command_parser::CommandRequest::from_bytes(&raw_bytes) {
        Ok(r) => r,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to parse command request: {e}"),
            );
            return;
        }
    };

    // Validate handle and retrieve client
    let handle_id = client_ptr as u64;

    // Get JVM for callback completion
    let jvm = match env.get_java_vm() {
        Ok(jvm) => Arc::new(jvm),
        Err(_) => {
            let _ = complete_java_callback_with_error(&mut env, callback_id, "JVM error");
            return;
        }
    };

    // Spawn async task
    let runtime = get_runtime();
    runtime.spawn(async move {
        // Ensure client exists, lazily creating if needed
        let client_result = ensure_client_for_handle(handle_id).await;
        match client_result {
            Ok(client) => {
                // Execute command
                let result = execute_command(client, command_request).await;

                // Complete callback via dedicated workers
                complete_callback(jvm, callback_id, result, false);
            }
            Err(err) => {
                let error = Err(anyhow::anyhow!("Client not found in handle_table {err}"));
                complete_callback(jvm, callback_id, error, false);
            }
        }
    });
}

/// Execute Valkey binary command asynchronously with mixed String/byte[] arguments.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeBinaryCommandAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    request_bytes: JByteArray,
    callback_id: jlong,
) {
    let raw_bytes = match env.convert_byte_array(&request_bytes) {
        Ok(b) => b,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to read binary command bytes: {e}"),
            );
            return;
        }
    };
    if raw_bytes.is_empty() {
        let _ = complete_java_callback_with_error(
            &mut env,
            callback_id,
            "Empty binary command request bytes",
        );
        return;
    }
    let binary_command_request =
        match crate::command_parser::BinaryCommandRequest::from_bytes(&raw_bytes) {
            Ok(r) => r,
            Err(e) => {
                let _ = complete_java_callback_with_error(
                    &mut env,
                    callback_id,
                    &format!("Failed to parse binary command request: {e}"),
                );
                return;
            }
        };

    // Validate handle and retrieve client
    let handle_id = client_ptr as u64;

    // Get JVM for callback completion
    let jvm = match env.get_java_vm() {
        Ok(jvm) => Arc::new(jvm),
        Err(_) => {
            let _ = complete_java_callback_with_error(&mut env, callback_id, "JVM error");
            return;
        }
    };

    // Spawn async task
    let runtime = get_runtime();
    runtime.spawn(async move {
        // Ensure client exists, lazily creating if needed
        let client_result = ensure_client_for_handle(handle_id).await;
        match client_result {
            Ok(client) => {
                // Execute binary command
                let result = execute_binary_command(client, binary_command_request).await;

                // Complete callback via dedicated workers with binary mode enabled
                complete_callback(jvm, callback_id, result, true);
            }
            Err(err) => {
                let error = Err(anyhow::anyhow!("Client not found in handle_table {err}"));
                complete_callback(jvm, callback_id, error, true);
            }
        }
    });
}

/// Execute batch (pipeline/transaction) asynchronously.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeBatchAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    batch_request_bytes: JByteArray,
    callback_id: jlong,
) {
    let raw_bytes = match env.convert_byte_array(&batch_request_bytes) {
        Ok(b) => b,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to read batch bytes: {e}"),
            );
            return;
        }
    };
    if raw_bytes.is_empty() {
        let _ = complete_java_callback_with_error(&mut env, callback_id, "Empty batch");
        return;
    }
    
    // Parse using unified BatchRequest protocol
    let batch_request = match crate::command_parser::BatchRequest::from_bytes(&raw_bytes) {
        Ok(req) => req,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to parse batch request: {e}"),
            );
            return;
        }
    };
    // Capture routing before moving batch_request
    let routing = batch_request.to_server_routing();
    
    // Convert BatchRequest to ValkeyCmd vec using clean unified protocol
    let parsed = (|| -> anyhow::Result<BatchParseResult> {
        let mut cmds: Vec<ValkeyCmd> = Vec::with_capacity(batch_request.commands.len());
        
        for batch_cmd in &batch_request.commands {
            let valkey_cmd = batch_cmd.to_valkey_cmd()?;
            
            // Debug output for batch command parsing
            if std::env::var("GLIDE_DEBUG_BATCH").is_ok() {
                eprintln!(
                    "[BATCH DEBUG] Parsed command: '{}'",
                    batch_cmd.command_type()
                );
            }
            
            cmds.push(valkey_cmd);
        }
        
        Ok((
            cmds,
            batch_request.atomic,
            if batch_request.timeout_ms > 0 { Some(batch_request.timeout_ms) } else { None },
            batch_request.raise_on_error,
            batch_request.route.clone(),  // Clone route for tuple
            batch_request.binary_output,
        ))
    })();
    let (cmds, atomic, timeout_ms_opt, raise_on_error, _route, binary_output) = match parsed {
        Ok(v) => v,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to parse batch request: {e}"),
            );
            return;
        }
    };

    // Capture handle for lazy client realization (parity with executeCommandAsync)
    let handle_id = client_ptr as u64;
    let jvm = match env.get_java_vm() {
        Ok(j) => Arc::new(j),
        Err(_) => {
            let _ = complete_java_callback_with_error(&mut env, callback_id, "JVM error");
            return;
        }
    };

    
    // Spawn async pipeline
    let runtime = get_runtime();
    runtime.spawn(async move {
        let result: Result<ServerValue> = async {
            // Ensure client exists, lazily creating if needed
            let mut client = ensure_client_for_handle(handle_id).await?;
            // Build pipeline
            let mut pipeline = ValkeyPipeline::new();
            if atomic {
                pipeline.atomic();
            }
            for c in cmds {
                pipeline.add_command(c);
            }
            // Execute
            let resp = if atomic {
                client
                    .send_transaction(&pipeline, routing, timeout_ms_opt, raise_on_error)
                    .await
            } else {
                client
                    .send_pipeline(
                        &pipeline,
                        routing,
                        raise_on_error,
                        timeout_ms_opt,
                        redis::PipelineRetryStrategy::default(),
                    )
                    .await
            };

            let value = resp.map_err(|e| anyhow::anyhow!("Batch execution failed: {e}"))?;
            // Post-execution validation & contextual error mapping
            if let ServerValue::Array(ref items) = value {
                // Validate response count when non-atomic pipeline (atomic may wrap differently depending on server behavior)
                // For both cases, if counts mismatch we surface a structured error.
                let expected = pipeline.len();
                let actual = items.len();
                if actual != expected {
                    return Err(anyhow::anyhow!(
                        "Batch response size mismatch: expected {} got {} (atomic={}, raise_on_error={})",
                        expected, actual, atomic, raise_on_error
                    ));
                }
                // Only surface errors when raise_on_error=true
                if raise_on_error {
                    if atomic {
                        // For atomic transactions with raise_on_error=true, surface first server error with index context
                        for (idx, item) in items.iter().enumerate() {
                            if let ServerValue::ServerError(err) = item {
                                return Err(anyhow::anyhow!(
                                    "Transactional command {} failed: {}",
                                    idx, err
                                ));
                            }
                        }
                    } else {
                        // For non-atomic pipelines with raise_on_error=true, surface first error similarly
                        for (idx, item) in items.iter().enumerate() {
                            if let ServerValue::ServerError(err) = item {
                                return Err(anyhow::anyhow!(
                                    "Pipeline command {} failed: {}",
                                    idx, err
                                ));
                            }
                        }
                    }
                }
                // When raise_on_error=false, return results with errors included
            }
            Ok(value)
        }
        .await;
    complete_callback(jvm, callback_id, result, binary_output);
    });
}

/// Execute binary-safe PUBLISH/SPUBLISH asynchronously.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executePublishBinaryAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    sharded: jboolean,
    channel: JByteArray,
    message: JByteArray,
    callback_id: jlong,
) {
    // Read bytes
    let channel_bytes = match env.convert_byte_array(&channel) {
        Ok(b) => b,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to read channel: {e}"),
            );
            return;
        }
    };
    let message_bytes = match env.convert_byte_array(&message) {
        Ok(b) => b,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to read message: {e}"),
            );
            return;
        }
    };

    // Capture handle id for potential lazy realization
    let handle_id = client_ptr as u64;
    let jvm = match env.get_java_vm() {
        Ok(j) => Arc::new(j),
        Err(_) => {
            let _ = complete_java_callback_with_error(&mut env, callback_id, "JVM error");
            return;
        }
    };

    // Spawn async send
    let is_sharded = sharded != 0;
    get_runtime().spawn(async move {
        let result: Result<ServerValue> = async {
            // Ensure client exists (lazily create if pending)
            let mut client = ensure_client_for_handle(handle_id).await?;
            let mut cmd = if is_sharded {
                cmd("SPUBLISH")
            } else {
                cmd("PUBLISH")
            };
            cmd.arg(channel_bytes.as_slice());
            cmd.arg(message_bytes.as_slice());
            let resp = client
                .send_command(&cmd, None)
                .await
                .map_err(|e| anyhow::anyhow!("Publish failed: {e}"))?;
            Ok(resp)
        }
        .await;
        complete_callback(jvm, callback_id, result, false);
    });
}

/// Execute a script asynchronously via JNI
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeScriptAsync(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
    callback_id: jlong,
    hash: JString,
    keys: jobject, // String[] array
    args: jobject, // String[] array
    has_route: jboolean,
    route_type: jint,
    route_param: JString,
) {
    let jvm = match env.get_java_vm() {
        Ok(jvm) => Arc::new(jvm),
        Err(_) => {
            return;
        }
    };

    // Extract hash
    let hash = match env.get_string(&hash) {
        Ok(h) => h.into(),
        Err(e) => {
            let error = Err(anyhow::anyhow!("Failed to read script hash: {e}"));
            complete_callback(jvm, callback_id, error, false);
            return;
        }
    };

    // Extract keys array
    let keys = if !keys.is_null() {
        match extract_string_array_as_bytes(&mut env, keys) {
            Ok(k) => k,
            Err(e) => {
                let error = Err(anyhow::anyhow!("Failed to read keys: {e}"));
                complete_callback(jvm, callback_id, error, false);
                return;
            }
        }
    } else {
        Vec::new()
    };

    // Extract args array
    let args = if !args.is_null() {
        match extract_string_array_as_bytes(&mut env, args) {
            Ok(a) => a,
            Err(e) => {
                let error = Err(anyhow::anyhow!("Failed to read args: {e}"));
                complete_callback(jvm, callback_id, error, false);
                return;
            }
        }
    } else {
        Vec::new()
    };

    // Extract routing info if provided
    let routing = if has_route != 0 {
        use crate::command_parser::RouteInfo as R;
        Some(match route_type {
            0 => R::AllNodes,
            1 => R::AllPrimaries,
            2 => R::Random,
            3 => {
                let key = match env.get_string(&route_param) {
                    Ok(k) => k.into(),
                    Err(_) => String::new(),
                };
                R::PrimaryForKey(key)
            }
            4 => {
                let key = match env.get_string(&route_param) {
                    Ok(k) => k.into(),
                    Err(_) => String::new(),
                };
                R::ReplicaForKey(key)
            }
            _ => R::Random,
        })
    } else {
        None
    };

    // Use the shared runtime instead of tokio::spawn
    let runtime = get_runtime();
    thread::spawn(move || {
        runtime.block_on(async move {
            let client_result = ensure_client_for_handle(handle_id as u64).await;
            match client_result {
                Ok(client) => {
                    let result = execute_script(client, hash, keys, args, routing).await;
                    complete_callback(jvm, callback_id, result, false);
                }
                Err(err) => {
                    let error = Err(anyhow::anyhow!("Client not found: {err}"));
                    complete_callback(jvm, callback_id, error, false);
                }
            }
        });
    });
}

// Helper function to extract String[] as Vec<Vec<u8>>
fn extract_string_array_as_bytes(env: &mut JNIEnv, array: jobject) -> Result<Vec<Vec<u8>>> {
    let array = unsafe { JObject::from_raw(array) };
    let array_ref = jni::objects::JObjectArray::from(array);
    let len = env.get_array_length(&array_ref)?;

    let mut result = Vec::with_capacity(len as usize);
    for i in 0..len {
        let elem = env.get_object_array_element(&array_ref, i)?;
        if elem.is_null() {
            result.push(Vec::new());
        } else {
            let jstring = JString::from(elem);
            let string = env.get_string(&jstring)?;
            result.push(string.to_string_lossy().into_owned().into_bytes());
        }
    }

    Ok(result)
}

/// Update connection password (and optionally AUTH immediately) asynchronously.
///
/// # Safety
/// This function is unsafe because it dereferences raw JNI pointers and client pointers.
/// The caller must ensure that the JNI environment, client pointer, and string parameters are valid.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_glide_internal_GlideNativeBridge_updateConnectionPassword(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    password: jstring,
    immediate_auth: jboolean,
    callback_id: jlong,
) {
    unsafe {
        // Extract password from jstring (nullable treated as None)
        let pw_opt: Option<String> = if password.is_null() {
            None
        } else {
            let js = JString::from_raw(password);
            env.get_string(&js)
                .ok()
                .map(|s| s.to_string_lossy().to_string())
        };

        // Spawn async task on runtime
        let jvm = match env.get_java_vm() {
            Ok(j) => Arc::new(j),
            Err(_) => {
                let _ = complete_java_callback_with_error(&mut env, callback_id, "JVM error");
                return;
            }
        };
        let handle_id = client_ptr as u64;
        let do_immediate = immediate_auth != 0;
        let runtime = get_runtime();
        runtime.spawn(async move {
            let result: Result<ServerValue> = async {
                let mut client = ensure_client_for_handle(handle_id).await?;
                client
                    .update_connection_password(pw_opt.clone(), do_immediate)
                    .await
                    .map_err(|e| anyhow::anyhow!("Password update failed: {e}"))?;
                Ok(ServerValue::Okay)
            }
            .await;
            complete_callback(jvm, callback_id, result, false);
        });
    }
}

/// Execute cluster scan command asynchronously.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeClusterScanAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    scan_request_bytes: JByteArray,
    callback_id: jlong,
) {
    let raw_bytes = match env.convert_byte_array(&scan_request_bytes) {
        Ok(b) => b,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to read cluster scan bytes: {e}"),
            );
            return;
        }
    };
    if raw_bytes.is_empty() {
        let _ = complete_java_callback_with_error(
            &mut env,
            callback_id,
            "Empty cluster scan request bytes",
        );
        return;
    }
    let scan_request = match crate::command_parser::ClusterScanRequest::from_bytes(&raw_bytes) {
        Ok(r) => r,
        Err(e) => {
            let _ = complete_java_callback_with_error(
                &mut env,
                callback_id,
                &format!("Failed to parse cluster scan request: {e}"),
            );
            return;
        }
    };

    let handle_id = client_ptr as u64;
    let jvm = match env.get_java_vm() {
        Ok(jvm) => jvm,
        Err(_) => {
            let _ = complete_java_callback_with_error(&mut env, callback_id, "JVM error");
            return;
        }
    };

    // Spawn async task
    let runtime = get_runtime();
    runtime.spawn(async move {
        // Get client
        let client_result = ensure_client_for_handle(handle_id).await;
        match client_result {
            Ok(client) => {
                // Execute cluster scan
                let result = execute_cluster_scan_command(client, scan_request.clone()).await;

                // Complete callback via dedicated workers - use binary mode from scan request
                let binary_mode = scan_request.binary_mode.unwrap_or(false);
                complete_callback(jvm.into(), callback_id, result, binary_mode);
            }
            Err(err) => {
                let error = Err(anyhow::anyhow!("Client not found in handle_table {err}"));
                complete_callback(jvm.into(), callback_id, error, false);
            }
        }
    });
}

/// Close client and release resources.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_closeClient(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) {
    let handle_table = get_handle_table();
    let handle_id = client_ptr as u64;

    // DashMap operations are sync and lock-free
    if let Some((_, client)) = handle_table.remove(&handle_id) {
        if stats_enabled() {
            record_client_closed();
        }
        log::debug!("Removed client with handle: {handle_id}");

        // Schedule async cleanup
        let runtime = get_runtime();
        runtime.spawn(async move {
            // Drop the client; core will close connections via Drop implementations
            drop(client);
        });
    }
}

/// Check if client handle exists.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_isConnected(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) -> jboolean {
    let handle_table = get_handle_table();
    let handle_id = client_ptr as u64;
    if handle_table.contains_key(&handle_id) {
        1
    } else {
        0
    }
}

// ============================================================================
// Logger JNI Bindings
// ============================================================================

/// Initialize the logger with the specified level and filename.
///
/// # Safety
/// This function is unsafe because it dereferences raw JNI pointers.
/// The caller must ensure that the JNI environment and filename parameter are valid.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_glide_api_logging_Logger_initLogger(
    mut _env: JNIEnv,
    _class: JClass,
    level: jint,
    filename: jstring,
) -> jint {
    unsafe {
        let _guard = scopeguard::guard((), |_| {
            // Cleanup on scope exit
        });

        let level = match level {
            0 => logger_core::Level::Error,
            1 => logger_core::Level::Warn,
            2 => logger_core::Level::Info,
            3 => logger_core::Level::Debug,
            4 => logger_core::Level::Trace,
            5 => logger_core::Level::Off,
            _ => logger_core::Level::Warn,
        };

        let filename_opt = if filename.is_null() {
            None
        } else {
            // Safely wrap the raw jstring and extract Rust String
            let jstr = JString::from_raw(filename);
            match _env.get_string(&jstr) {
                Ok(s) => Some(s.to_string_lossy().to_string()),
                Err(_) => None,
            }
        };

        let _result = logger_core::init(Some(level), filename_opt.as_deref());
        0 // Success
    }
}

/// Log a message at the specified level with identifier.
///
/// # Safety
/// This function is unsafe because it dereferences raw JNI pointers.
/// The caller must ensure that the JNI environment and string parameters are valid.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_glide_api_logging_Logger_logMessage(
    mut _env: JNIEnv,
    _class: JClass,
    level: jint,
    identifier: jstring,
    message: jstring,
) {
    unsafe {
        let _guard = scopeguard::guard((), |_| {
            // Cleanup on scope exit
        });

        let level = match level {
            0 => logger_core::Level::Error,
            1 => logger_core::Level::Warn,
            2 => logger_core::Level::Info,
            3 => logger_core::Level::Debug,
            4 => logger_core::Level::Trace,
            5 => logger_core::Level::Off,
            _ => logger_core::Level::Warn,
        };

        let identifier_str = {
            let jstr = JString::from_raw(identifier);
            match _env.get_string(&jstr) {
                Ok(s) => s.to_string_lossy().to_string(),
                Err(_) => return,
            }
        };

        let message_str = {
            let jstr = JString::from_raw(message);
            match _env.get_string(&jstr) {
                Ok(s) => s.to_string_lossy().to_string(),
                Err(_) => return,
            }
        };

        logger_core::log(level, identifier_str, message_str);
    }
}

// ==================== OPENTELEMETRY JNI FUNCTIONS ====================

use std::collections::HashMap;
use telemetrylib::{
    GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder, GlideOpenTelemetrySignalsExporter,
};

// Global storage for active spans (store real GlideSpan instances)
static ACTIVE_SPANS: std::sync::OnceLock<Mutex<HashMap<u64, telemetrylib::GlideSpan>>> =
    std::sync::OnceLock::new();
static SPAN_COUNTER: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

fn get_active_spans() -> &'static Mutex<HashMap<u64, telemetrylib::GlideSpan>> {
    ACTIVE_SPANS.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Initialize OpenTelemetry with the provided configuration.
/// Integrates with the Rust telemetry module for full OpenTelemetry support.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_initOpenTelemetryNative(
    mut env: JNIEnv,
    _class: JClass,
    traces_endpoint: jstring,
    traces_sample_percentage: jint,
    metrics_endpoint: jstring,
    flush_interval_ms: jlong,
) -> jint {
    log::debug!("OpenTelemetry initialization called");

    let result: Result<(), Box<dyn std::error::Error>> = (|| {
        let mut config_builder = GlideOpenTelemetryConfigBuilder::default();

        // Set flush interval (allow 0 to propagate and be validated by telemetrylib)
        config_builder = config_builder
            .with_flush_interval(std::time::Duration::from_millis(flush_interval_ms as u64));

        // Configure traces if endpoint provided
        if !traces_endpoint.is_null() {
            let traces_endpoint_str = get_optional_string_param_raw(&mut env, traces_endpoint)
                .ok_or("Failed to get traces endpoint")?;

            let exporter = parse_endpoint(&traces_endpoint_str)?;
            // If exporting to file, ensure the file exists to avoid race in tests reading it
            if let GlideOpenTelemetrySignalsExporter::File(ref p) = exporter {
                let _ = std::fs::OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(p);
            }
            let sample_percentage_opt = if traces_sample_percentage >= 0 {
                Some(traces_sample_percentage as u32)
            } else {
                None // Use default
            };

            config_builder = config_builder.with_trace_exporter(exporter, sample_percentage_opt);
        }

        // Configure metrics if endpoint provided
        if !metrics_endpoint.is_null() {
            let metrics_endpoint_str = get_optional_string_param_raw(&mut env, metrics_endpoint)
                .ok_or("Failed to get metrics endpoint")?;

            let exporter = parse_endpoint(&metrics_endpoint_str)?;
            if let GlideOpenTelemetrySignalsExporter::File(ref p) = exporter {
                let _ = std::fs::OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(p);
            }
            config_builder = config_builder.with_metrics_exporter(exporter);
        }

        let config = config_builder.build();
        // Ensure OpenTelemetry initialization runs inside our Tokio runtime context
        let init_result =
            crate::get_runtime().block_on(async move { GlideOpenTelemetry::initialise(config) });
        init_result?;

        log::info!("OpenTelemetry initialized successfully");
        Ok(())
    })();

    match result {
        Ok(()) => 0, // Success
        Err(e) => {
            log::error!("OpenTelemetry initialization failed: {}", e);
            // Return appropriate error codes based on error type
            if e.to_string().contains("Missing configuration") {
                1 // Missing configuration
            } else if e.to_string().contains("Invalid traces endpoint") {
                2 // Invalid traces endpoint
            } else if e.to_string().contains("Invalid metrics endpoint") {
                3 // Invalid metrics endpoint
            } else if e.to_string().contains("Runtime initialization failure") {
                4 // Runtime initialization failure
            } else {
                5 // OpenTelemetry initialization failure
            }
        }
    }
}

/// Parse endpoint string into appropriate exporter type
fn parse_endpoint(
    endpoint: &str,
) -> Result<GlideOpenTelemetrySignalsExporter, Box<dyn std::error::Error>> {
    if endpoint.starts_with("file://") {
        let path = endpoint.strip_prefix("file://").unwrap();
        Ok(GlideOpenTelemetrySignalsExporter::File(
            std::path::PathBuf::from(path),
        ))
    } else if endpoint.starts_with("grpc://") {
        Ok(GlideOpenTelemetrySignalsExporter::Grpc(
            endpoint.to_string(),
        ))
    } else if endpoint.starts_with("http://") || endpoint.starts_with("https://") {
        Ok(GlideOpenTelemetrySignalsExporter::Http(
            endpoint.to_string(),
        ))
    } else {
        Err(format!("Unsupported endpoint protocol: {}", endpoint).into())
    }
}

/// Create a new OpenTelemetry span with the given name.
/// Returns a unique pointer ID that can be used to reference the span.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_createLeakedOtelSpan(
    mut env: JNIEnv,
    _class: JClass,
    span_name: jstring,
) -> jlong {
    log::debug!("OpenTelemetry span creation called");

    let result: Result<u64, Box<dyn std::error::Error>> = (|| {
        let span_name_str =
            get_optional_string_param_raw(&mut env, span_name).ok_or("Failed to get span name")?;

        // Create span using the telemetry module
        let span = GlideOpenTelemetry::new_span(&span_name_str);

        // Generate unique ID and store the span
        let span_id = SPAN_COUNTER.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        let active_spans = get_active_spans();
        let mut spans = active_spans.lock().map_err(|_| "Failed to lock spans")?;
        spans.insert(span_id, span);

        log::debug!(
            "Created OpenTelemetry span '{}' with ID {}",
            span_name_str,
            span_id
        );
        Ok(span_id)
    })();

    match result {
        Ok(span_id) => span_id as jlong,
        Err(e) => {
            log::error!("Failed to create OpenTelemetry span: {}", e);
            0 // Return 0 to indicate failure
        }
    }
}

/// Drop an OpenTelemetry span that was created with createLeakedOtelSpan.
/// This releases the span's resources and ends the span.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_dropOtelSpan(
    mut _env: JNIEnv,
    _class: JClass,
    span_ptr: jlong,
) {
    log::debug!("OpenTelemetry span drop called for ID {}", span_ptr);

    if span_ptr == 0 {
        return; // Invalid span pointer
    }

    let span_id = span_ptr as u64;
    let active_spans = get_active_spans();

    if let Ok(mut spans) = active_spans.lock() {
        if let Some(span) = spans.remove(&span_id) {
            // End the span
            span.end();
            log::debug!("Dropped OpenTelemetry span with ID {}", span_id);
        } else {
            log::warn!("Attempted to drop non-existent span with ID {}", span_id);
        }
    } else {
        log::error!("Failed to lock spans for dropping span ID {}", span_id);
    }
}

/// Releases a ClusterScanCursor handle allocated in Rust.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_releaseNativeCursor(
    mut env: JNIEnv,
    _class: JClass,
    cursor: JString,
) {
    let cursor_str: String = match env.get_string(&cursor) {
        Ok(js) => js.to_string_lossy().to_string(),
        Err(e) => {
            log::error!(
                "ClusterScanCursorResolver.releaseNativeCursor: failed to read cursor: {e}"
            );
            return;
        }
    };
    glide_core::cluster_scan_container::remove_scan_state_cursor(cursor_str);
}

/// Returns the String representing a finished cursor handle.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_getFinishedCursorHandleConstant<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    match env.new_string(glide_core::client::FINISHED_SCAN_CURSOR) {
        Ok(s) => s,
        Err(e) => {
            log::error!(
                "ClusterScanCursorResolver.getFinishedCursorHandleConstant: failed to create jstring: {e}"
            );
            JString::default()
        }
    }
}
