//! JNI client management infrastructure extracted from JNI-java implementation
//! This module provides direct JNI calls to glide-core while preserving protobuf serialization

use anyhow::Result;
use dashmap::DashMap;
use glide_core::client::Client as GlideClient;
use glide_core::client::{AuthenticationInfo, ConnectionRequest, NodeAddress, TlsMode};
use jni::JNIEnv;
use jni::JavaVM;
use jni::objects::{GlobalRef, JClass, JObject, JStaticMethodID, JValue};
use jni::signature;
use jni::sys::{jlong, jobject, jstring};
use redis::Value as ServerValue;
use std::sync::Arc;
use std::sync::mpsc::{Sender, channel};
use std::thread;
use tokio::runtime::Runtime;

// Type aliases for complex types
type PushMessageTuple = (Vec<u8>, Vec<u8>, Option<Vec<u8>>);

// Runtime and JVM statics
pub static JVM: std::sync::OnceLock<Arc<JavaVM>> = std::sync::OnceLock::new();
static RUNTIME: std::sync::OnceLock<Runtime> = std::sync::OnceLock::new();

// Defaults for runtime and callback workers
const DEFAULT_RUNTIME_WORKER_THREADS: usize = 1;
const DEFAULT_CALLBACK_WORKER_THREADS: usize = 2;

// =========================
// Native buffer registry
// =========================
static NATIVE_BUFFER_REGISTRY: std::sync::OnceLock<dashmap::DashMap<u64, Vec<u8>>> =
    std::sync::OnceLock::new();
static NEXT_NATIVE_BUFFER_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

fn get_native_buffer_registry() -> &'static dashmap::DashMap<u64, Vec<u8>> {
    NATIVE_BUFFER_REGISTRY.get_or_init(dashmap::DashMap::new)
}

pub fn register_native_buffer(bytes: Vec<u8>) -> (u64, *mut u8, usize) {
    let id = NEXT_NATIVE_BUFFER_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    let registry = get_native_buffer_registry();
    registry.insert(id, bytes);
    // Obtain stable pointer/len from stored Vec
    let guard = registry.get(&id).expect("buffer just inserted");
    let ptr = guard.as_ptr() as *mut u8;
    let len = guard.len();
    (id, ptr, len)
}

pub fn free_native_buffer(id: u64) -> bool {
    let registry = get_native_buffer_registry();
    registry.remove(&id).is_some()
}

/// Initialize or return the shared Tokio runtime.
pub(crate) fn get_runtime() -> &'static Runtime {
    RUNTIME.get_or_init(|| {
        let worker_threads = if let Ok(threads_str) = std::env::var("GLIDE_TOKIO_WORKER_THREADS") {
            threads_str
                .parse::<usize>()
                .unwrap_or(DEFAULT_RUNTIME_WORKER_THREADS)
        } else {
            DEFAULT_RUNTIME_WORKER_THREADS
        };

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

/// Generate unique safe handle for JNI resource management
static NEXT_HANDLE_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

pub fn generate_safe_handle() -> u64 {
    NEXT_HANDLE_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
}

/// Configuration struct to avoid too many function parameters
#[derive(Debug)]
pub struct ValkeyClientConfig {
    pub addresses: Vec<String>,
    pub database_id: u32,
    pub username: Option<String>,
    pub password: Option<String>,
    pub use_tls: bool,
    pub insecure_tls: bool,
    pub cluster_mode: bool,
    pub request_timeout_ms: u64,
    pub connection_timeout_ms: u64,
    pub read_from: Option<String>,
    pub client_az: Option<String>,
    pub lazy_connect: bool,
    pub client_name: Option<String>,
    pub protocol: Option<String>,
    pub max_inflight_requests: Option<u32>,
    pub pubsub_subscriptions: Option<redis::PubSubSubscriptionInfo>,
    pub connection_retry_strategy: Option<glide_core::client::ConnectionRetryStrategy>,
}

/// Helper function to create Valkey connection configuration
pub fn create_valkey_connection_config(config: ValkeyClientConfig) -> Result<ConnectionRequest> {
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
        protocol,
        max_inflight_requests,
        pubsub_subscriptions,
        connection_retry_strategy,
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
        tls_mode: Some(if insecure_tls {
            TlsMode::InsecureTls
        } else if use_tls {
            TlsMode::SecureTls
        } else {
            TlsMode::NoTls
        }),
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
        protocol: protocol.and_then(|proto| match proto.as_str() {
            "RESP2" => Some(redis::ProtocolVersion::RESP2),
            "RESP3" => Some(redis::ProtocolVersion::RESP3),
            other => {
                log::warn!("Unknown protocol {}, defaulting to RESP3", other);
                None
            }
        }),
        connection_retry_strategy,
        periodic_checks: None,
        pubsub_subscriptions,
        inflight_requests_limit: max_inflight_requests,
        lazy_connect,
    };

    Ok(connection_request)
}

/// Create actual glide-core Valkey client with specified configuration
pub async fn create_glide_client(
    connection_request: ConnectionRequest,
    push_tx: Option<tokio::sync::mpsc::UnboundedSender<redis::PushInfo>>,
) -> Result<GlideClient> {
    let client = GlideClient::new(connection_request, push_tx)
        .await
        .map_err(|e| {
            log::error!("Failed to create glide-core client: {e}");
            anyhow::anyhow!("Failed to create glide-core client: {e}")
        })?;
    Ok(client)
}

pub async fn ensure_client_for_handle(handle_id: u64) -> Result<GlideClient> {
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

        // Handle push notifications if needed
        if let Some(mut rx) = rx_opt {
            let jvm_arc = JVM.get().cloned();
            let handle_for_java = handle_id as jlong;
            get_runtime().spawn(async move {
                while let Some(push) = rx.recv().await {
                    if let Some(jvm) = jvm_arc.as_ref()
                        && let Ok(mut env) = jvm.attach_current_thread_permanently()
                    {
                        // Handle push notification callback to Java
                        handle_push_notification(&mut env, handle_for_java, push);
                    }
                }
            });
        }

        return Ok(table.get(&handle_id).unwrap().value().clone());
    }

    Err(anyhow::anyhow!("Client not found in handle_table"))
}

pub(crate) fn handle_push_notification(env: &mut JNIEnv, handle_id: jlong, push: redis::PushInfo) {
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

    if let Some((m, c, p)) = mapped {
        let _ = env.push_local_frame(16);
        let jm = env.byte_array_from_slice(&m).ok();
        let jc = env.byte_array_from_slice(&c).ok();
        let jp = p.as_ref().and_then(|pp| env.byte_array_from_slice(pp).ok());

        if let (Some(jm), Some(jc)) = (jm, jc) {
            let jm_obj: JObject = jm.into();
            let jc_obj: JObject = jc.into();
            let jp_obj: JObject = jp.map(Into::into).unwrap_or(JObject::null());

            if let Ok(cache) = get_glide_core_client_cache(env) {
                unsafe {
                    let _ = env.call_static_method_unchecked(
                        &cache.class,
                        cache.on_native_push,
                        signature::ReturnType::Primitive(signature::Primitive::Void),
                        &[
                            JValue::Long(handle_id).as_jni(),
                            JValue::Object(&jm_obj).as_jni(),
                            JValue::Object(&jc_obj).as_jni(),
                            JValue::Object(&jp_obj).as_jni(),
                        ],
                    );
                }
            }
        }

        let _ = unsafe { env.pop_local_frame(&JObject::null()) };
    }
}

/// Cache of required Java method IDs.
#[derive(Clone)]
pub(crate) struct MethodCache {
    async_handle_table_class: GlobalRef,
    complete_callback_method: JStaticMethodID,
    complete_error_method: JStaticMethodID,
}

static METHOD_CACHE: std::sync::OnceLock<parking_lot::Mutex<Option<MethodCache>>> =
    std::sync::OnceLock::new();

/// Get or initialize the method cache.
pub(crate) fn get_method_cache(env: &mut JNIEnv) -> Result<MethodCache> {
    let cache_mutex = METHOD_CACHE.get_or_init(|| parking_lot::Mutex::new(None));

    {
        let cache_guard = cache_mutex.lock();
        if let Some(cache) = cache_guard.as_ref() {
            return Ok(cache.clone());
        }
    }

    let class = env
        .find_class("glide/internal/AsyncRegistry")
        .map_err(|e| anyhow::anyhow!("Failed to find AsyncRegistry class: {e}"))?;

    let global_class = env
        .new_global_ref(&class)
        .map_err(|e| anyhow::anyhow!("Failed to create global class reference: {e}"))?;

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

    let method_cache = MethodCache {
        async_handle_table_class: global_class,
        complete_callback_method,
        complete_error_method,
    };

    // Store in cache
    {
        let mut cache_guard = cache_mutex.lock();
        *cache_guard = Some(method_cache.clone());
    }

    Ok(method_cache)
}

/// Callback job type handled by dedicated callback workers
type CallbackJob = (Arc<JavaVM>, jlong, Result<ServerValue>, bool);

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

pub fn init_callback_workers() -> &'static Sender<CallbackJob> {
    CALLBACK_SENDER.get_or_init(|| {
        let (tx, rx) = channel::<CallbackJob>();
        let rx = Arc::new(std::sync::Mutex::new(rx));
        let worker_threads = get_callback_worker_threads();

        for i in 0..worker_threads {
            let rx_clone = Arc::clone(&rx);
            thread::Builder::new()
                .name(format!("glide-jni-callback-{i}"))
                .spawn(move || {
                    loop {
                        let job_opt = {
                            let guard = rx_clone.lock().unwrap();
                            guard.recv().ok()
                        };
                        let Some((jvm, callback_id, result, binary_mode)) = job_opt else {
                            break;
                        };

                        // Process callback on this dedicated thread
                        process_callback_job(jvm, callback_id, result, binary_mode);
                    }
                })
                .expect("Failed to spawn callback worker thread");
        }

        tx
    })
}

fn process_callback_job(
    jvm: Arc<JavaVM>,
    callback_id: jlong,
    result: Result<ServerValue>,
    binary_mode: bool,
) {
    match jvm.attach_current_thread_permanently() {
        Ok(mut env) => match result {
            Ok(server_value) => {
                let _ = env.push_local_frame(16);

                // Direct conversion with size-based routing
                let java_result = if should_use_direct_buffer(&server_value) {
                    // For large data (>16KB): Use DirectByteBuffer
                    create_direct_byte_buffer(&mut env, server_value, !binary_mode)
                } else {
                    // For small data (<16KB): Regular JNI objects
                    crate::resp_value_to_java(&mut env, server_value, !binary_mode)
                };

                match java_result {
                    Ok(java_result) => {
                        let _ = complete_java_callback(&mut env, callback_id, java_result.as_raw());
                    }
                    Err(e) => {
                        let error_msg = format!("Response conversion failed: {e}");
                        let _ =
                            complete_java_callback_with_error(&mut env, callback_id, &error_msg);
                    }
                }
                let _ = unsafe { env.pop_local_frame(&JObject::null()) };
            }
            Err(e) => {
                let _ = complete_java_callback_with_error(&mut env, callback_id, &e.to_string());
            }
        },
        Err(e) => {
            log::error!("JNI environment attachment failed: {e}");
        }
    }
}

/// Enqueue callback job to dedicated workers.
pub fn complete_callback(
    jvm: Arc<JavaVM>,
    callback_id: jlong,
    result: Result<ServerValue>,
    binary_mode: bool,
) {
    let sender = init_callback_workers();
    if let Err(e) = sender.send((jvm, callback_id, result, binary_mode)) {
        log::error!("Callback queue send failed: {e}");
    }
}

/// Complete Java CompletableFuture with success result using cached method IDs.
pub fn complete_java_callback(env: &mut JNIEnv, callback_id: jlong, result: jobject) -> Result<()> {
    let method_cache = get_method_cache(env)?;
    let result_obj = unsafe { JObject::from_raw(result) };

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
pub fn complete_java_callback_with_error(
    env: &mut JNIEnv,
    callback_id: jlong,
    error: &str,
) -> Result<()> {
    let method_cache = get_method_cache(env)?;
    let _ = env.push_local_frame(4);
    let error_string = env.new_string(error)?;
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
    let _ = unsafe { env.pop_local_frame(&JObject::null()) };
    Ok(())
}

/// Check if response should use DirectByteBuffer based on size threshold (16KB)
fn should_use_direct_buffer(value: &ServerValue) -> bool {
    const THRESHOLD: usize = 16 * 1024; // 16KB threshold

    match value {
        redis::Value::BulkString(data) => data.len() > THRESHOLD,
        redis::Value::Array(arr) => {
            // Only offload arrays composed of simple scalar types. Nested arrays/maps lose fidelity
            if arr.iter().any(|elem| !is_simple_scalar(elem)) {
                return false;
            }

            // Calculate total estimated size of array elements
            let total_size: usize = arr.iter().map(estimate_value_size).sum();
            total_size > THRESHOLD
        }
        redis::Value::Map(map) => {
            // Direct buffers are only safe when both keys and values are bulk strings; complex
            // structures (arrays, integers, maps) need full decoding to preserve types.
            if map.iter().any(|(k, v)| {
                !matches!(k, redis::Value::BulkString(_))
                    || !matches!(v, redis::Value::BulkString(_))
            }) {
                return false;
            }

            // Calculate total size of map (keys + values)
            let total_size: usize = map
                .iter()
                .map(|(k, v)| estimate_value_size(k) + estimate_value_size(v))
                .sum();
            total_size > THRESHOLD
        }
        redis::Value::Set(set) => {
            // Sets must also contain only scalar elements to be safely serialized.
            if set.iter().any(|elem| !is_simple_scalar(elem)) {
                return false;
            }

            // Calculate total size of set elements
            let total_size: usize = set.iter().map(estimate_value_size).sum();
            total_size > THRESHOLD
        }
        _ => false, // Other types (Int, Double, Boolean, etc.) are typically small
    }
}

fn is_simple_scalar(value: &ServerValue) -> bool {
    matches!(
        value,
        redis::Value::BulkString(_)
            | redis::Value::SimpleString(_)
            | redis::Value::Int(_)
            | redis::Value::Boolean(_)
            | redis::Value::Double(_)
            | redis::Value::Nil
            | redis::Value::Okay
            | redis::Value::BigNumber(_)
    )
}

/// Estimate the memory size of a ServerValue for threshold calculations
fn estimate_value_size(value: &ServerValue) -> usize {
    match value {
        redis::Value::Nil => 0,
        redis::Value::SimpleString(s) => s.len(),
        redis::Value::BulkString(data) => data.len(),
        redis::Value::Int(_) => 8,    // 64-bit int
        redis::Value::Double(_) => 8, // 64-bit double
        redis::Value::Boolean(_) => 1,
        redis::Value::Array(arr) => {
            arr.iter().map(estimate_value_size).sum::<usize>() + (arr.len() * 8) // overhead
        }
        redis::Value::Map(map) => {
            map.iter()
                .map(|(k, v)| estimate_value_size(k) + estimate_value_size(v))
                .sum::<usize>()
                + (map.len() * 16) // overhead for key-value pairs
        }
        redis::Value::Set(set) => {
            set.iter().map(estimate_value_size).sum::<usize>() + (set.len() * 8) // overhead
        }
        redis::Value::VerbatimString { text, .. } => text.len(),
        redis::Value::BigNumber(num) => num.to_string().len(), // Estimate size as string representation
        redis::Value::Push { data, .. } => data.iter().map(estimate_value_size).sum::<usize>(),
        redis::Value::ServerError(_) => 128, // Estimate for error messages
        redis::Value::Okay => 2,             // "OK"
        redis::Value::Attribute { data, .. } => estimate_value_size(data.as_ref()),
    }
}

/// Create DirectByteBuffer for large responses (>16KB) with zero-copy optimization
fn create_direct_byte_buffer<'local>(
    env: &mut JNIEnv<'local>,
    value: ServerValue,
    encoding_utf8: bool,
) -> Result<JObject<'local>, crate::errors::FFIError> {
    match value {
        redis::Value::BulkString(data) => {
            let (id, ptr, len) = register_native_buffer(data);
            let bb = unsafe { env.new_direct_byte_buffer(ptr.cast(), len)? };
            // Register Java-side cleaner to free native buffer when GC'd
            let obj: JObject = bb.into();
            let out = env.new_local_ref(&obj)?;
            register_buffer_cleaner(env, &out, id)?;
            Ok(out)
        }
        redis::Value::Array(arr) => {
            let serialized = serialize_array_to_bytes(arr, encoding_utf8)?;
            let (id, ptr, len) = register_native_buffer(serialized);
            let bb = unsafe { env.new_direct_byte_buffer(ptr.cast(), len)? };
            let obj: JObject = bb.into();
            let out = env.new_local_ref(&obj)?;
            register_buffer_cleaner(env, &out, id)?;
            Ok(out)
        }
        redis::Value::Map(map) => {
            let serialized = serialize_map_vec_to_bytes(map, encoding_utf8)?;
            let (id, ptr, len) = register_native_buffer(serialized);
            let bb = unsafe { env.new_direct_byte_buffer(ptr.cast(), len)? };
            let obj: JObject = bb.into();
            let out = env.new_local_ref(&obj)?;
            register_buffer_cleaner(env, &out, id)?;
            Ok(out)
        }
        _ => {
            // Fall back to regular conversion for other large types
            crate::resp_value_to_java(env, value, encoding_utf8)
        }
    }
}

fn register_buffer_cleaner<'local>(
    env: &mut JNIEnv<'local>,
    buffer: &JObject<'local>,
    id: u64,
) -> Result<(), crate::errors::FFIError> {
    let cache = get_glide_core_client_cache(env).map_err(|_e| {
        // Map to a representative JNI error variant
        jni::errors::Error::JNIEnvMethodNotFound("GlideCoreClient cache")
    })?;
    unsafe {
        env.call_static_method_unchecked(
            &cache.class,
            cache.register_native_buffer_cleaner,
            signature::ReturnType::Primitive(signature::Primitive::Void),
            &[
                JValue::Object(buffer).as_jni(),
                JValue::Long(id as jlong).as_jni(),
            ],
        )?
    };

    Ok(())
}

/// Serialize array to bytes for DirectByteBuffer (simplified binary format)
fn serialize_array_to_bytes(
    arr: Vec<ServerValue>,
    _encoding_utf8: bool,
) -> Result<Vec<u8>, crate::errors::FFIError> {
    let mut bytes = Vec::new();

    // Write array marker and length
    bytes.push(b'*'); // Redis array prefix
    bytes.extend_from_slice(&(arr.len() as u32).to_be_bytes());

    for value in arr {
        match value {
            redis::Value::BulkString(data) => {
                bytes.push(b'$'); // Bulk string marker
                bytes.extend_from_slice(&(data.len() as u32).to_be_bytes());
                bytes.extend_from_slice(&data);
            }
            redis::Value::SimpleString(s) => {
                // Normalize "ok" to "OK" while avoiding unnecessary allocations
                if s == "OK" {
                    let data = s.into_bytes();
                    bytes.push(b'+'); // Simple string marker
                    bytes.extend_from_slice(&(data.len() as u32).to_be_bytes());
                    bytes.extend_from_slice(&data);
                } else if s.eq_ignore_ascii_case("ok") {
                    bytes.push(b'+');
                    bytes.extend_from_slice(&2u32.to_be_bytes());
                    bytes.extend_from_slice(b"OK");
                } else {
                    let data = s.into_bytes();
                    bytes.push(b'+');
                    bytes.extend_from_slice(&(data.len() as u32).to_be_bytes());
                    bytes.extend_from_slice(&data);
                }
            }
            redis::Value::Okay => {
                let data = b"OK";
                bytes.push(b'+');
                bytes.extend_from_slice(&(data.len() as u32).to_be_bytes());
                bytes.extend_from_slice(data);
            }
            redis::Value::Int(n) => {
                bytes.push(b':'); // Integer marker
                bytes.extend_from_slice(&n.to_be_bytes());
            }
            _ => {
                // For complex nested types, store as serialized string representation
                let repr = format!("{:?}", value);
                let data = repr.into_bytes();
                bytes.push(b'#'); // Complex type marker
                bytes.extend_from_slice(&(data.len() as u32).to_be_bytes());
                bytes.extend_from_slice(&data);
            }
        }
    }

    Ok(bytes)
}

/// Serialize map Vec<(K,V)> to bytes for DirectByteBuffer (simplified binary format)
fn serialize_map_vec_to_bytes(
    map: Vec<(ServerValue, ServerValue)>,
    _encoding_utf8: bool,
) -> Result<Vec<u8>, crate::errors::FFIError> {
    let mut bytes = Vec::new();

    // Write map marker and length
    bytes.push(b'%'); // Map prefix
    bytes.extend_from_slice(&(map.len() as u32).to_be_bytes());

    for (key, value) in map {
        // Serialize key
        if let redis::Value::BulkString(key_data) = key {
            bytes.extend_from_slice(&(key_data.len() as u32).to_be_bytes());
            bytes.extend_from_slice(&key_data);
        } else {
            let key_repr = format!("{:?}", key).into_bytes();
            bytes.extend_from_slice(&(key_repr.len() as u32).to_be_bytes());
            bytes.extend_from_slice(&key_repr);
        }

        // Serialize value
        if let redis::Value::BulkString(value_data) = value {
            bytes.extend_from_slice(&(value_data.len() as u32).to_be_bytes());
            bytes.extend_from_slice(&value_data);
        } else {
            let value_repr = format!("{:?}", value).into_bytes();
            bytes.extend_from_slice(&(value_repr.len() as u32).to_be_bytes());
            bytes.extend_from_slice(&value_repr);
        }
    }

    Ok(bytes)
}

/// Extract optional string parameter from JNI.
pub fn get_optional_string_param_raw(env: &mut JNIEnv, param: jstring) -> Option<String> {
    if param.is_null() {
        return None;
    }
    unsafe {
        let js = jni::objects::JString::from_raw(param);
        env.get_string(&js)
            .ok()
            .map(|s| s.to_str().unwrap_or("").to_string())
    }
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

/// Native free for DirectByteBuffer-backed native memory (called by Java Cleaner)
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideCoreClient_freeNativeBuffer(
    _env: JNIEnv,
    _class: JClass,
    id: jlong,
) {
    let id = id as u64;
    let _ = free_native_buffer(id);
}

#[derive(Clone)]
struct GlideCoreClientCache {
    class: GlobalRef,
    on_native_push: JStaticMethodID,
    register_native_buffer_cleaner: JStaticMethodID,
}

static GLIDE_CORE_CLIENT_CACHE: std::sync::OnceLock<
    parking_lot::Mutex<Option<GlideCoreClientCache>>,
> = std::sync::OnceLock::new();

fn get_glide_core_client_cache(env: &mut JNIEnv) -> Result<GlideCoreClientCache> {
    let cache_mutex = GLIDE_CORE_CLIENT_CACHE.get_or_init(|| parking_lot::Mutex::new(None));
    {
        let guard = cache_mutex.lock();
        if let Some(c) = guard.as_ref() {
            return Ok(c.clone());
        }
    }
    let class = env.find_class("glide/internal/GlideCoreClient")?;
    let global = env.new_global_ref(&class)?;
    let on_native_push = env.get_static_method_id(&class, "onNativePush", "(J[B[B[B)V")?;
    let register_native_buffer_cleaner = env.get_static_method_id(
        &class,
        "registerNativeBufferCleaner",
        "(Ljava/nio/ByteBuffer;J)V",
    )?;
    let cache = GlideCoreClientCache {
        class: global,
        on_native_push,
        register_native_buffer_cleaner,
    };
    {
        let mut guard = cache_mutex.lock();
        *guard = Some(cache.clone());
    }
    Ok(cache)
}
