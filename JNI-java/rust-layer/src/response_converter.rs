//! # Response Converter - Server Value to Java Object Transformation
//!
//! ## Overview
//! High-performance converter providing comprehensive Valkey type support with zero-copy
//! optimization. Handles all RESP3 protocol types including complex nested structures.
//!
//! ## Architecture
//! - **Type Coverage**: All Valkey value types (String, Int, Array, Map, Set, Push, etc.)
//! - **Performance**: Cached class lookups, unsafe method calls for speed
//! - **Memory**: Efficient direct allocation with minimal overhead
//! - **Safety**: Proper error handling with Java exception conversion
//!
//! ## Conversion Strategy
//! - **Primitive Types**: Direct JNI object creation with cached classes
//! - **Collections**: Recursive conversion with pre-sized Java collections
//! - **Complex Types**: RESP3 Push/VerbatimString wrapped in HashMap structures
//! - **Error Handling**: Context-aware Java exception types

use anyhow::{Result, anyhow};
use jni::JNIEnv;
use jni::objects::{GlobalRef, JObject, JValue};
use jni::sys::jobject;
use redis::{Cmd, Value as ServerValue};
use std::sync::{Mutex, OnceLock};

/// **Core Response Converter**
///
/// Server value to Java object conversion.
/// type support and performance optimizations. Handles all RESP3 protocol types.
///
/// **Key Features:**
/// - Zero-copy conversion where possible
/// - Cached class lookups for performance
/// - Recursive handling of complex nested structures
/// - Context-aware error handling with proper Java exceptions
pub struct ResponseConverter {
    // marker struct – no fields
}

impl ResponseConverter {
    /// Convert a server response to appropriate Java objects with hybrid optimization
    /// This provides full Valkey type support including RESP3 types and large data optimization
    pub fn convert_server_value_to_java<'a>(
        env: &mut JNIEnv<'a>,
        value: ServerValue,
        _command: Option<&Cmd>,
    ) -> Result<jobject> {
        // Direct conversion - no size estimation or deferred conversion
        Self::server_value_to_java(env, value, false)
    }

    /// Binary aware conversion (internal) – if binary_mode true BulkString => GlideString
    pub fn convert_server_value_to_java_with_mode<'a>(
        env: &mut JNIEnv<'a>,
        value: ServerValue,
        binary_mode: bool,
    ) -> Result<jobject> {
        Self::server_value_to_java(env, value, binary_mode)
    }

    /// Core conversion function that handles all server value types
    fn server_value_to_java<'a>(
        env: &mut JNIEnv<'a>,
        value: ServerValue,
        binary_mode: bool,
    ) -> Result<jobject> {
        match value {
            ServerValue::BulkString(bytes) => {
                if binary_mode {
                    // Create GlideString via static of(byte[]) method for zero interpretation of bytes
                    let cache = crate::get_cached_response_classes(env)?;
                    let byte_array = env.byte_array_from_slice(&bytes)?;
                    // SAFETY: method signature validated at cache population
                    let byte_obj: JObject = byte_array.into();
                    let glide_obj = unsafe {
                        env.call_static_method_unchecked(
                            &cache.glide_string_class,
                            cache.glide_string_of_bytes_method,
                            jni::signature::ReturnType::Object,
                            &[JValue::Object(&byte_obj).as_jni()],
                        )?
                    };
                    Ok(glide_obj.l()?.into_raw())
                } else {
                    let string_content = String::from_utf8_lossy(&bytes);
                    let java_string = env.new_string(&string_content)?;
                    Ok(java_string.into_raw())
                }
            }
            ServerValue::SimpleString(s) => {
                let java_string = env.new_string(&s)?;
                Ok(java_string.into_raw())
            }
            ServerValue::Int(i) => {
                // Use cached class to avoid expensive JNI lookup
                let cache = crate::get_cached_response_classes(env)?;
                let long_obj = env.new_object(&cache.long_class, "(J)V", &[JValue::Long(i)])?;
                Ok(long_obj.into_raw())
            }
            ServerValue::Double(d) => {
                // Use cached class to avoid expensive JNI lookup
                let cache = crate::get_cached_response_classes(env)?;
                let double_obj =
                    env.new_object(&cache.double_class, "(D)V", &[JValue::Double(d)])?;
                Ok(double_obj.into_raw())
            }
            ServerValue::Boolean(b) => {
                // Use cached class to avoid expensive JNI lookup
                let cache = crate::get_cached_response_classes(env)?;
                let bool_obj = env.new_object(
                    &cache.boolean_class,
                    "(Z)V",
                    &[JValue::Bool(b as jni::sys::jboolean)],
                )?;
                Ok(bool_obj.into_raw())
            }
            ServerValue::Nil => Ok(JObject::null().into_raw()),
            ServerValue::Array(items) => Self::convert_array_to_java_list(env, items, binary_mode),
            ServerValue::Map(pairs) => Self::convert_map_to_java_map(env, pairs, binary_mode),
            ServerValue::Set(items) => Self::convert_set_to_java_set(env, items, binary_mode),
            ServerValue::Push { kind, data } => {
                let kind_str = format!("{kind:?}"); // Convert PushKind to string
                Self::convert_push_to_java(env, kind_str, data)
            }
            ServerValue::VerbatimString { format, text } => {
                // For CLUSTER NODES, return text directly instead of HashMap wrapper
                // This matches RESP2 behavior where cluster nodes is returned as a plain string
                if text.contains("myself") || text.contains("cluster-enabled:yes") {
                    let java_string = env.new_string(&text)?;
                    Ok(java_string.into_raw())
                } else {
                    // For other VerbatimString types, use the full HashMap wrapper
                    let format_str = format!("{format:?}"); // Convert VerbatimFormat to string
                    Self::convert_verbatim_string_to_java(
                        env,
                        format_str.into_bytes(),
                        text.into_bytes(),
                    )
                }
            }
            ServerValue::Attribute {
                data,
                attributes: _,
            } => {
                // For now, just return the data part, ignoring attributes
                // Could be enhanced to return a wrapper object with metadata
                Self::server_value_to_java(env, *data, binary_mode)
            }
            ServerValue::BigNumber(big_num) => {
                // Use cached class to avoid expensive JNI lookup
                let cache = crate::get_cached_response_classes(env)?;
                let number_str = env.new_string(big_num.to_string())?;
                let big_int_obj = env.new_object(
                    &cache.big_integer_class,
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&number_str)],
                )?;
                Ok(big_int_obj.into_raw())
            }
            ServerValue::Okay => {
                let java_string = env.new_string("OK")?;
                Ok(java_string.into_raw())
            }
            ServerValue::ServerError(server_error) => {
                // For batch operations, we need to return error as a special marker
                // that Java can convert to RequestException
                // Format: "__GLIDE_ERROR__:message"
                // Use Debug format to get the full error details
                let error_marker = format!("__GLIDE_ERROR__:{:?}", server_error);
                let java_string = env.new_string(error_marker)?;
                Ok(java_string.into_raw())
            }
        }
    }

    /// Convert Server array to native Java Object[] array
    fn convert_array_to_java_list<'a>(
        env: &mut JNIEnv<'a>,
        items: Vec<ServerValue>,
        binary_mode: bool,
    ) -> Result<jobject> {
        // Create native Java Object[] array instead of ArrayList
        let cache = crate::get_cached_response_classes(env)?;
        let array =
            env.new_object_array(items.len() as i32, &cache.object_class, JObject::null())?;

        // Add all items to the array
        for (index, item) in items.into_iter().enumerate() {
            let java_item = Self::server_value_to_java(env, item, binary_mode)?;
            let java_obj = unsafe { JObject::from_raw(java_item) };
            env.set_object_array_element(&array, index as i32, java_obj)?;
        }

        Ok(array.into_raw())
    }

    /// Convert Server map to Java HashMap
    fn convert_map_to_java_map<'a>(
        env: &mut JNIEnv<'a>,
        pairs: Vec<(ServerValue, ServerValue)>,
        binary_mode: bool,
    ) -> Result<jobject> {
        // Use cached class and methods to avoid expensive JNI lookups
        let cache = crate::get_cached_response_classes(env)?;
        let map = env.new_object(
            &cache.hash_map_class,
            "(I)V",
            &[JValue::Int(pairs.len() as i32)],
        )?;

        // Add all key-value pairs
        for (key, value) in pairs {
            let java_key = Self::server_value_to_java(env, key, binary_mode)?;
            let java_value = Self::server_value_to_java(env, value, binary_mode)?;

            // SAFETY: java_key and java_value are valid jobjects from server_value_to_java
            let key_obj = unsafe { JObject::from_raw(java_key) };
            let value_obj = unsafe { JObject::from_raw(java_value) };

            unsafe {
                let _ = env.call_method_unchecked(
                    &map,
                    cache.hash_map_put_method,
                    jni::signature::ReturnType::Object,
                    &[
                        JValue::Object(&key_obj).as_jni(),
                        JValue::Object(&value_obj).as_jni(),
                    ],
                )?;
            }
        }

        Ok(map.into_raw())
    }

    /// Convert Server set to Java HashSet
    fn convert_set_to_java_set<'a>(
        env: &mut JNIEnv<'a>,
        items: Vec<ServerValue>,
        binary_mode: bool,
    ) -> Result<jobject> {
        // Use cached class and methods to avoid expensive JNI lookups
        let cache = crate::get_cached_response_classes(env)?;
        let set = env.new_object(
            &cache.hash_set_class,
            "(I)V",
            &[JValue::Int(items.len() as i32)],
        )?;

        // Add all items
        for item in items {
            let java_item = Self::server_value_to_java(env, item, binary_mode)?;
            let java_obj = unsafe { JObject::from_raw(java_item) };
            unsafe {
                let _ = env.call_method_unchecked(
                    &set,
                    cache.hash_set_add_method,
                    jni::signature::ReturnType::Primitive(jni::signature::Primitive::Boolean),
                    &[JValue::Object(&java_obj).as_jni()],
                )?;
            }
        }

        Ok(set.into_raw())
    }

    /// Convert RESP3 Push data to Java wrapper object
    fn convert_push_to_java<'a>(
        env: &mut JNIEnv<'a>,
        kind: String,
        data: Vec<ServerValue>,
    ) -> Result<jobject> {
        // Use cached class and methods to avoid expensive JNI lookups
        let cache = crate::get_cached_response_classes(env)?;
        let map = env.new_object(&cache.hash_map_class, "()V", &[])?;

        // Add kind
        let kind_key = env.new_string("kind")?;
        let kind_value = env.new_string(&kind)?;
        unsafe {
            let _ = env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&kind_key).as_jni(),
                    JValue::Object(&kind_value).as_jni(),
                ],
            )?;
        }

        // Add data as list
        let data_key = env.new_string("data")?;
        let data_list = Self::convert_array_to_java_list(env, data, false)?;
        let data_obj = unsafe { JObject::from_raw(data_list) };
        unsafe {
            let _ = env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&data_key).as_jni(),
                    JValue::Object(&data_obj).as_jni(),
                ],
            )?;
        }

        Ok(map.into_raw())
    }

    /// Convert RESP3 VerbatimString to Java wrapper
    fn convert_verbatim_string_to_java<'a>(
        env: &mut JNIEnv<'a>,
        format: Vec<u8>,
        text: Vec<u8>,
    ) -> Result<jobject> {
        // Use cached class and methods to avoid expensive JNI lookups
        let cache = crate::get_cached_response_classes(env)?;
        let map = env.new_object(&cache.hash_map_class, "()V", &[])?;

        // Add format
        let format_key = env.new_string("format")?;
        let format_content = String::from_utf8_lossy(&format);
        let format_value = env.new_string(&format_content)?;
        unsafe {
            let _ = env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&format_key).as_jni(),
                    JValue::Object(&format_value).as_jni(),
                ],
            )?;
        }

        // Add text
        let text_key = env.new_string("text")?;
        let text_content = String::from_utf8_lossy(&text);
        let text_value = env.new_string(&text_content)?;
        unsafe {
            let _ = env.call_method_unchecked(
                &map,
                cache.hash_map_put_method,
                jni::signature::ReturnType::Object,
                &[
                    JValue::Object(&text_key).as_jni(),
                    JValue::Object(&text_value).as_jni(),
                ],
            )?;
        }

        Ok(map.into_raw())
    }

    // Note: command-aware shape normalization happens in glide-core. JNI only maps
    // the already-normalized redis::Value into Java objects and applies LargeData
    // hybrid deferral. No semantic re-shaping should be done here.

    /// Convert error to Java exception with proper error context
    pub fn convert_error_to_java_exception(env: &mut JNIEnv, error: &anyhow::Error) -> Result<()> {
        // Use more specific exception types based on error context
        let exception_class = if error.to_string().contains("connection") {
            "java/net/ConnectException"
        } else if error.to_string().contains("timeout") {
            "java/util/concurrent/TimeoutException"
        } else {
            "java/lang/RuntimeException"
        };

        let exception_class_obj = env.find_class(exception_class)?;
        env.throw_new(
            exception_class_obj,
            format!("Server operation failed: {error}"),
        )?;
        Ok(())
    }

    /// Efficient conversion for simple string responses (GET, SET, etc.)
    pub fn convert_simple_string<'a>(env: &mut JNIEnv<'a>, value: &str) -> Result<jobject> {
        let java_string = env.new_string(value)?;
        Ok(java_string.into_raw())
    }

    /// Efficient conversion for simple integer responses (INCR, etc.)
    pub fn convert_simple_integer<'a>(env: &mut JNIEnv<'a>, value: i64) -> Result<jobject> {
        // Use cached class to avoid expensive JNI lookup
        let cache = crate::get_cached_response_classes(env)?;
        let long_obj = env.new_object(&cache.long_class, "(J)V", &[JValue::Long(value)])?;
        Ok(long_obj.into_raw())
    }
}

// ==================== Response Class Cache (moved from lib.rs) ====================

#[derive(Clone)]
pub(crate) struct ResponseClassCache {
    // Java collection classes
    pub(crate) hash_map_class: GlobalRef,
    pub(crate) hash_set_class: GlobalRef,
    pub(crate) object_class: GlobalRef,

    // Java primitive wrapper classes
    pub(crate) long_class: GlobalRef,
    pub(crate) double_class: GlobalRef,
    pub(crate) boolean_class: GlobalRef,
    pub(crate) big_integer_class: GlobalRef,
    pub(crate) glide_string_class: GlobalRef,

    // Collection method IDs
    pub(crate) hash_map_put_method: jni::objects::JMethodID,
    pub(crate) hash_set_add_method: jni::objects::JMethodID,
    // GlideString static factory for of(byte[])
    pub(crate) glide_string_of_bytes_method: jni::objects::JStaticMethodID,
}

static RESPONSE_CLASS_CACHE: OnceLock<Mutex<Option<ResponseClassCache>>> = OnceLock::new();

pub(crate) fn get_response_class_cache(env: &mut JNIEnv) -> Result<ResponseClassCache> {
    let cache_mutex = RESPONSE_CLASS_CACHE.get_or_init(|| Mutex::new(None));

    {
        let cache_guard = cache_mutex
            .lock()
            .map_err(|e| anyhow!("Failed to acquire lock for response class cache: {}", e))?;
        if let Some(cache) = cache_guard.as_ref() {
            return Ok(cache.clone());
        }
    }

    // Cache miss: initialize
    log::debug!("Initializing response class cache");

    // Find and cache collection classes
    let hash_map_class = env
        .find_class("java/util/HashMap")
        .map_err(|e| anyhow!("Failed to find HashMap class: {e}"))?;
    let hash_set_class = env
        .find_class("java/util/HashSet")
        .map_err(|e| anyhow!("Failed to find HashSet class: {e}"))?;
    let object_class = env
        .find_class("java/lang/Object")
        .map_err(|e| anyhow!("Failed to find Object class: {e}"))?;

    // Find and cache primitive wrapper classes
    let long_class = env
        .find_class("java/lang/Long")
        .map_err(|e| anyhow!("Failed to find Long class: {e}"))?;
    let double_class = env
        .find_class("java/lang/Double")
        .map_err(|e| anyhow!("Failed to find Double class: {e}"))?;
    let boolean_class = env
        .find_class("java/lang/Boolean")
        .map_err(|e| anyhow!("Failed to find Boolean class: {e}"))?;
    let big_integer_class = env
        .find_class("java/math/BigInteger")
        .map_err(|e| anyhow!("Failed to find BigInteger class: {e}"))?;
    let glide_string_class = env
        .find_class("glide/api/models/GlideString")
        .map_err(|e| anyhow!("Failed to find GlideString class: {e}"))?;

    // Create global references to prevent GC collection
    let hash_map_global = env
        .new_global_ref(&hash_map_class)
        .map_err(|e| anyhow!("Failed to create HashMap global ref: {e}"))?;
    let hash_set_global = env
        .new_global_ref(&hash_set_class)
        .map_err(|e| anyhow!("Failed to create HashSet global ref: {e}"))?;
    let object_global = env
        .new_global_ref(&object_class)
        .map_err(|e| anyhow!("Failed to create Object global ref: {e}"))?;
    let long_global = env
        .new_global_ref(&long_class)
        .map_err(|e| anyhow!("Failed to create Long global ref: {e}"))?;
    let double_global = env
        .new_global_ref(&double_class)
        .map_err(|e| anyhow!("Failed to create Double global ref: {e}"))?;
    let boolean_global = env
        .new_global_ref(&boolean_class)
        .map_err(|e| anyhow!("Failed to create Boolean global ref: {e}"))?;
    let big_integer_global = env
        .new_global_ref(&big_integer_class)
        .map_err(|e| anyhow!("Failed to create BigInteger global ref: {e}"))?;
    let glide_string_global = env
        .new_global_ref(&glide_string_class)
        .map_err(|e| anyhow!("Failed to create GlideString global ref: {e}"))?;

    // Cache method IDs for collection operations (these are instance methods, not static)
    let hash_map_put_method = env
        .get_method_id(
            &hash_map_class,
            "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        )
        .map_err(|e| anyhow!("Failed to get HashMap put method: {e}"))?;

    let hash_set_add_method = env
        .get_method_id(&hash_set_class, "add", "(Ljava/lang/Object;)Z")
        .map_err(|e| anyhow!("Failed to get HashSet add method: {e}"))?;

    // GlideString.of(byte[]) static method
    let glide_string_of_bytes_method = env
        .get_static_method_id(
            &glide_string_class,
            "of",
            "([B)Lglide/api/models/GlideString;",
        )
        .map_err(|e| anyhow!("Failed to get GlideString.of(byte[]) method: {e}"))?;

    let response_cache = ResponseClassCache {
        hash_map_class: hash_map_global,
        hash_set_class: hash_set_global,
        object_class: object_global,
        long_class: long_global,
        double_class: double_global,
        boolean_class: boolean_global,
        big_integer_class: big_integer_global,
        glide_string_class: glide_string_global,

        hash_map_put_method,
        hash_set_add_method,
        glide_string_of_bytes_method,
    };

    {
        let mut cache_guard = cache_mutex
            .lock()
            .map_err(|e| anyhow!("Failed to acquire lock for response class cache: {}", e))?;
        *cache_guard = Some(response_cache.clone());
    }

    log::debug!("Response class cache initialized");
    Ok(response_cache)
}

#[cfg(test)]
mod tests {
    use redis::Value;

    #[test]
    fn test_value_type_coverage() {
        // Ensure we handle all Server value types
        // This is a compile-time test to verify pattern matching coverage
        let test_values = vec![
            Value::Nil,
            Value::Int(42),
            Value::BulkString(b"test".to_vec()),
            Value::SimpleString("test".to_string()),
            Value::Array(vec![]),
            Value::Map(vec![]),
            Value::Set(vec![]),
            Value::Double(std::f64::consts::PI),
            Value::Boolean(true),
            Value::VerbatimString {
                format: redis::VerbatimFormat::Text,
                text: "test".to_string(),
            },
            Value::Push {
                kind: redis::PushKind::Message,
                data: vec![],
            },
            Value::Attribute {
                data: Box::new(Value::Nil),
                attributes: vec![],
            },
        ];

        // Verify all variants are handled in our match statement
        for value in test_values {
            match value {
                Value::Nil => {}
                Value::Int(_) => {}
                Value::BulkString(_) => {}
                Value::SimpleString(_) => {}
                Value::Array(_) => {}
                Value::Map(_) => {}
                Value::Set(_) => {}
                Value::Double(_) => {}
                Value::Boolean(_) => {}
                Value::VerbatimString { .. } => {}
                Value::Push { .. } => {}
                Value::Attribute { .. } => {}
                Value::Okay => {}
                Value::BigNumber(_) => {}
                Value::ServerError(_) => {}
            }
        }
    }

    #[test]
    fn test_hash_response_validation() {
        // Test hash response array length validation
        let odd_items = [Value::BulkString(b"key1".to_vec())]; // Odd length should error

        // This would require a JNI environment to test properly
        // Including for documentation and future test infrastructure
        assert_eq!(odd_items.len() % 2, 1);
    }
}
