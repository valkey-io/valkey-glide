//! # Large Data Handler - Deferred Conversion for JNI
//!
//! Implements deferred conversion strategy for large Valkey values (>16KB)
//! to eliminate expensive JNI data copying overhead. Uses a pointer registry
//! system that stores large values in native memory and returns lightweight
//! pointer IDs to Java.

use anyhow::{Result, anyhow};
use dashmap::DashMap;
use jni::JNIEnv;
use jni::objects::{JClass, JValue};
use jni::sys::{jbyteArray, jlong, jobject};
use redis::Value as ServerValue;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, LazyLock};

/// Global pointer registry for storing large server values
static POINTER_REGISTRY: LazyLock<Arc<DashMap<u64, StoredServerValue>>> =
    LazyLock::new(|| Arc::new(DashMap::new()));

/// Atomic counter for generating unique pointer IDs
static NEXT_POINTER_ID: AtomicU64 = AtomicU64::new(1);

/// Size threshold for triggering deferred conversion (16KB)
const LARGE_DATA_THRESHOLD: usize = 16 * 1024;

/// Performance statistics
static DEFERRED_COUNT: AtomicU64 = AtomicU64::new(0);
static IMMEDIATE_COUNT: AtomicU64 = AtomicU64::new(0);
static BYTES_SAVED: AtomicU64 = AtomicU64::new(0);

/// Stored server value with metadata
#[derive(Debug)]
pub struct StoredServerValue {
    /// The actual server value
    pub value: ServerValue,
    /// Size in bytes (for statistics)
    pub size_bytes: usize,
    /// Whether original request expected binary output semantics
    pub binary_mode: bool,
}

/// Response wrapper for deferred conversion
#[derive(Debug)]
pub enum HybridResponse {
    /// Immediate conversion - data included
    Immediate(jobject),
    /// Deferred conversion - pointer ID returned
    Deferred {
        pointer_id: u64,
        size_bytes: usize,
        data_type: String,
    },
}

/// Main handler for large data optimization
pub struct LargeDataHandler;

impl LargeDataHandler {
    /// Check if a server value should use deferred conversion
    pub fn should_defer_conversion(value: &ServerValue) -> bool {
        match value {
            ServerValue::BulkString(bytes) => bytes.len() >= LARGE_DATA_THRESHOLD,
            ServerValue::Array(items) => {
                let estimated_size = items
                    .iter()
                    .map(Self::estimate_server_value_size)
                    .sum::<usize>();
                estimated_size >= LARGE_DATA_THRESHOLD
            }
            ServerValue::Map(map) => {
                let estimated_size = map
                    .iter()
                    .map(|(k, v)| {
                        Self::estimate_server_value_size(k) + Self::estimate_server_value_size(v)
                    })
                    .sum::<usize>();
                estimated_size >= LARGE_DATA_THRESHOLD
            }
            _ => false, // Small values always use immediate conversion
        }
    }

    /// Store a large value and return pointer ID
    pub fn store_large_value(value: ServerValue, binary_mode: bool) -> u64 {
        let size_bytes = Self::estimate_server_value_size(&value);
        let pointer_id = NEXT_POINTER_ID.fetch_add(1, Ordering::Relaxed);

        let stored_value = StoredServerValue {
            value,
            size_bytes,
            binary_mode,
        };

        POINTER_REGISTRY.insert(pointer_id, stored_value);

        // Update statistics
        DEFERRED_COUNT.fetch_add(1, Ordering::Relaxed);
        BYTES_SAVED.fetch_add(size_bytes as u64, Ordering::Relaxed);

        pointer_id
    }

    /// Retrieve a value by pointer ID
    pub fn retrieve_value(pointer_id: u64) -> Result<ServerValue> {
        match POINTER_REGISTRY.get(&pointer_id) {
            Some(stored) => Ok(stored.value.clone()),
            None => Err(anyhow!("Invalid pointer ID: {}", pointer_id)),
        }
    }

    /// Convert with hybrid strategy (immediate for small, deferred for large)
    pub fn convert_with_hybrid_strategy<'a>(
        env: &mut JNIEnv<'a>,
        value: ServerValue,
        binary_mode: bool,
    ) -> Result<jobject> {
        // Fast path for obviously small data - bypass all checks
        match &value {
            ServerValue::Nil | ServerValue::Okay | ServerValue::Int(_) => {
                // These are always small, skip all overhead
                return crate::response_converter::ResponseConverter::convert_server_value_to_java_with_mode(
                    env, value, binary_mode,
                );
            }
            ServerValue::SimpleString(s) if s.len() < 1024 => {
                // Small simple strings, skip all overhead
                return crate::response_converter::ResponseConverter::convert_server_value_to_java_with_mode(
                    env, value, binary_mode,
                );
            }
            ServerValue::BulkString(bytes) if bytes.len() < 1024 => {
                // Small bulk strings, skip all overhead
                return crate::response_converter::ResponseConverter::convert_server_value_to_java_with_mode(
                    env, value, binary_mode,
                );
            }
            _ => {} // Continue with full check
        }

        if Self::should_defer_conversion(&value) {
            // Deferred conversion for large data
            let data_type = match &value {
                ServerValue::BulkString(_) => "BulkString",
                ServerValue::Array(_) => "Array",
                ServerValue::Map(_) => "Map",
                _ => "Unknown",
            };

            let size_bytes = Self::estimate_server_value_size(&value);
            let pointer_id = Self::store_large_value(value, binary_mode);

            // Return a special large data response object
            Self::create_deferred_response(env, pointer_id, size_bytes, data_type)
        } else {
            // Immediate conversion for smaller data
            IMMEDIATE_COUNT.fetch_add(1, Ordering::Relaxed);
            crate::response_converter::ResponseConverter::convert_server_value_to_java_with_mode(
                env,
                value,
                binary_mode,
            )
        }
    }

    /// Create deferred response object
    pub fn create_deferred_response<'a>(
        env: &mut JNIEnv<'a>,
        pointer_id: u64,
        size_bytes: usize,
        data_type: &str,
    ) -> Result<jobject> {
        // Return a HashMap with the legacy shape expected by Java LargeDataHandler:
        // {
        //   "__deferred__": "true",
        //   "pointerId": Long,
        //   "sizeBytes": Long,
        //   "dataType": String
        // }
        let cache = crate::get_cached_response_classes(env)?;

        // Create HashMap with initial capacity 4
        let map = env.new_object(
            &cache.hash_map_class,
            "(I)V",
            &[JValue::Int(4)],
        )?;

        // Keys
        let k_deferred = env.new_string("__deferred__")?;
        let k_pointer = env.new_string("pointerId")?;
        let k_size = env.new_string("sizeBytes")?;
        let k_type = env.new_string("dataType")?;

        // Values
        let v_true = env.new_string("true")?;
        let v_pointer = env.new_object(&cache.long_class, "(J)V", &[JValue::Long(pointer_id as jlong)])?;
        let v_size = env.new_object(&cache.long_class, "(J)V", &[JValue::Long(size_bytes as jlong)])?;
        let v_data_type = env.new_string(data_type)?;

        unsafe {
            // map.put("__deferred__", "true")
            env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&k_deferred).as_jni(),
                    JValue::Object(&v_true).as_jni(),
                ],
            )?;

            // map.put("pointerId", Long(pointer_id))
            env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&k_pointer).as_jni(),
                    JValue::Object(&v_pointer).as_jni(),
                ],
            )?;

            // map.put("sizeBytes", Long(size_bytes))
            env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&k_size).as_jni(),
                    JValue::Object(&v_size).as_jni(),
                ],
            )?;

            // map.put("dataType", dataType)
            env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&k_type).as_jni(),
                    JValue::Object(&v_data_type).as_jni(),
                ],
            )?;
        }

        Ok(map.into_raw())
    }

    /// Estimate size of server value in bytes
    fn estimate_server_value_size(value: &ServerValue) -> usize {
        match value {
            ServerValue::Nil => 0,
            ServerValue::Okay => 2,
            ServerValue::Int(_) => 8,
            ServerValue::SimpleString(s) => s.len(),
            ServerValue::BulkString(bytes) => bytes.len(),
            ServerValue::Array(items) => {
                items
                    .iter()
                    .map(Self::estimate_server_value_size)
                    .sum::<usize>()
                    + (items.len() * 8)
            }
            ServerValue::Map(map) => {
                map.iter()
                    .map(|(k, v)| {
                        Self::estimate_server_value_size(k) + Self::estimate_server_value_size(v)
                    })
                    .sum::<usize>()
                    + (map.len() * 16)
            }
            ServerValue::Set(items) => {
                items
                    .iter()
                    .map(Self::estimate_server_value_size)
                    .sum::<usize>()
                    + (items.len() * 8)
            }
            ServerValue::Double(_) => 8,
            ServerValue::Boolean(_) => 1,
            ServerValue::VerbatimString { format: _, text } => format!("{}:{}", "", text).len(),
            ServerValue::BigNumber(bn) => bn.to_string().len(),
            ServerValue::Attribute {
                data,
                attributes: _,
            } => Self::estimate_server_value_size(data),
            ServerValue::Push { kind: _, data } => {
                data.iter()
                    .map(Self::estimate_server_value_size)
                    .sum::<usize>()
                    + (data.len() * 8)
            }
            ServerValue::ServerError(err) => err.to_string().len(),
        }
    }
}

// JNI Functions for Java integration

/// Retrieve large data as Java object
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_retrieveLargeData(
    mut env: JNIEnv,
    _class: JClass,
    pointer_id: jlong,
) -> jobject {
    match LargeDataHandler::retrieve_value(pointer_id as u64) {
        Ok(value) => {
            let binary_mode = POINTER_REGISTRY
                .get(&(pointer_id as u64))
                .map(|stored| stored.binary_mode)
                .unwrap_or(false);

            match crate::response_converter::ResponseConverter::convert_server_value_to_java_with_mode(
                &mut env, value, binary_mode,
            ) {
                Ok(obj) => obj,
                Err(e) => {
                    let _ = env.throw_new("java/lang/RuntimeException", format!("Failed to convert large data: {}", e));
                    std::ptr::null_mut()
                }
            }
        }
        Err(e) => {
            let _ = env.throw_new(
                "java/lang/IllegalArgumentException",
                format!("Invalid pointer ID: {}", e),
            );
            std::ptr::null_mut()
        }
    }
}

/// Delete large data pointer
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_deleteLargeDataPointer(
    _env: JNIEnv,
    _class: JClass,
    pointer_id: jlong,
) -> jlong {
    match POINTER_REGISTRY.remove(&(pointer_id as u64)) {
        Some((_, stored_value)) => stored_value.size_bytes as jlong,
        None => -1,
    }
}

/// Get large data handler statistics
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getLargeDataStatistics(
    env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    let stats = serde_json::json!({
        "activePointers": POINTER_REGISTRY.len(),
        "deferredCount": DEFERRED_COUNT.load(Ordering::Relaxed),
        "immediateCount": IMMEDIATE_COUNT.load(Ordering::Relaxed),
        "bytesSaved": BYTES_SAVED.load(Ordering::Relaxed),
    });

    let stats_str = stats.to_string();
    match env.byte_array_from_slice(stats_str.as_bytes()) {
        Ok(array) => array.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
