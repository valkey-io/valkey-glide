// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::client::FINISHED_SCAN_CURSOR;
use glide_core::errors::error_message;

// Protocol constants for Java (defined directly since we don't use socket layer)
const TYPE_HASH: &str = "hash";
const TYPE_LIST: &str = "list";
const TYPE_SET: &str = "set";
const TYPE_STREAM: &str = "stream";
const TYPE_STRING: &str = "string";
const TYPE_ZSET: &str = "zset";
const MAX_REQUEST_ARGS_LENGTH_IN_BYTES: usize = 2_i32.pow(12) as usize; // 4096 bytes

// Telemetry required for getStatistics
use glide_core::Telemetry;

use jni::JNIEnv;
use jni::errors::Error as JniError;
use jni::objects::{JByteArray, JClass, JObject, JObjectArray, JString};
use jni::sys::{jint, jlong};
use redis::Value;
use std::str::FromStr;
use std::sync::Arc;

mod errors;
mod linked_hashmap;
mod jni_client;
mod protobuf_bridge;

use errors::{FFIError, handle_errors, handle_panics};
use jni_client::*;
use protobuf_bridge::*;


/// Configuration for OpenTelemetry integration in the Java client.
///
/// This struct allows you to configure how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
/// - `traces`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
/// - `metrics`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
/// - `flush_interval_ms`: Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, a default value will be used.
///
/// At least one of traces or metrics must be provided.
#[derive(Clone)]
pub struct OpenTelemetryConfig {
    /// Optional configuration for exporting trace data. If `None`, trace data will not be exported.
    pub traces: Option<OpenTelemetryTracesConfig>,
    /// Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
    pub metrics: Option<OpenTelemetryMetricsConfig>,
    /// Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, the default `DEFAULT_FLUSH_SIGNAL_INTERVAL_MS` will be used.
    pub flush_interval_ms: Option<i64>,
}

/// Configuration for exporting OpenTelemetry traces.
///
/// - `endpoint`: The endpoint to which trace data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
/// - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration. If `None`, a default value DEFAULT_TRACE_SAMPLE_PERCENTAGE will be used.
///   Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
///   It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
#[derive(Clone)]
pub struct OpenTelemetryTracesConfig {
    /// The endpoint to which trace data will be exported.
    pub endpoint: String,
    /// The percentage of requests to sample and create a span for, used to measure command duration. If `None`, a default value DEFAULT_TRACE_SAMPLE_PERCENTAGE will be used.
    pub sample_percentage: Option<u32>,
}

/// Configuration for exporting OpenTelemetry metrics.
///
/// - `endpoint`: The endpoint to which metrics data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
#[derive(Clone)]
pub struct OpenTelemetryMetricsConfig {
    /// The endpoint to which metrics data will be exported.
    pub endpoint: String,
}
struct Level(i32);

fn resp_value_to_java<'local>(
    env: &mut JNIEnv<'local>,
    val: Value,
    encoding_utf8: bool,
) -> Result<JObject<'local>, FFIError> {
    match val {
        Value::Nil => Ok(JObject::null()),
        Value::SimpleString(data) => {
            if encoding_utf8 {
                // Normalize "ok" to "OK" for compatibility with Java constants
                let normalized = if data.eq_ignore_ascii_case("ok") {
                    "OK".to_string()
                } else {
                    data
                };
                Ok(JObject::from(env.new_string(normalized)?))
            } else {
                // Return raw byte array - Java will convert to GlideString
                Ok(JObject::from(env.byte_array_from_slice(data.as_bytes())?))
            }
        }
        Value::Okay => Ok(JObject::from(env.new_string("OK")?)),
        Value::Int(num) => Ok(env.new_object("java/lang/Long", "(J)V", &[num.into()])?),
        Value::BulkString(data) => {
            if encoding_utf8 {
                // Try UTF-8 conversion for string mode
                match String::from_utf8(data.clone()) {
                    Ok(utf8_str) => Ok(JObject::from(env.new_string(utf8_str)?)),
                    Err(_) => {
                        // Binary data - return as byte array
                        Ok(JObject::from(env.byte_array_from_slice(&data)?))
                    }
                }
            } else {
                // Binary mode - always return byte array
                Ok(JObject::from(env.byte_array_from_slice(&data)?))
            }
        }
        Value::Array(array) => array_to_java_array(env, array, encoding_utf8),
        Value::Map(map) => {
            let linked_hash_map = env.new_object("java/util/LinkedHashMap", "()V", &[])?;

            for (key, value) in map {
                let java_key = resp_value_to_java(env, key, encoding_utf8)?;
                let java_value = resp_value_to_java(env, value, encoding_utf8)?;
                env.call_method(
                    &linked_hash_map,
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    &[(&java_key).into(), (&java_value).into()],
                )?;
            }

            Ok(linked_hash_map)
        }
        Value::Double(float) => Ok(env.new_object("java/lang/Double", "(D)V", &[float.into()])?),
        Value::Boolean(bool) => Ok(env.new_object("java/lang/Boolean", "(Z)V", &[bool.into()])?),
        Value::VerbatimString { format: _, text } => {
            if encoding_utf8 {
                Ok(JObject::from(env.new_string(text)?))
            } else {
                Ok(JObject::from(env.byte_array_from_slice(text.as_bytes())?))
            }
        }
        Value::BigNumber(num) => {
            // Convert Valkey BigNumber to Java BigInteger
            // BigNumbers in Valkey are represented as strings
            let big_int_str = num.to_string();
            let java_string = env.new_string(big_int_str)?;
            Ok(env.new_object(
                "java/math/BigInteger",
                "(Ljava/lang/String;)V",
                &[(&java_string).into()],
            )?)
        }
        Value::Set(array) => {
            let set = env.new_object("java/util/HashSet", "()V", &[])?;

            for elem in array {
                let java_value = resp_value_to_java(env, elem, encoding_utf8)?;
                env.call_method(
                    &set,
                    "add",
                    "(Ljava/lang/Object;)Z",
                    &[(&java_value).into()],
                )?;
            }

            Ok(set)
        }
        Value::Attribute { data, attributes } => {
            // Convert Valkey Attribute to Java Map<String, Object>
            // Create a HashMap with both data and attributes
            let hash_map = env.new_object("java/util/HashMap", "()V", &[])?;
            
            // Add the main data under "data" key
            let data_key = env.new_string("data")?;
            let java_data = resp_value_to_java(env, *data, encoding_utf8)?;
            env.call_method(
                &hash_map,
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                &[(&data_key).into(), (&java_data).into()],
            )?;
            
            // Add the attributes under "attributes" key
            let attributes_key = env.new_string("attributes")?;
            let java_attributes = resp_value_to_java(env, Value::Map(attributes), encoding_utf8)?;
            env.call_method(
                &hash_map,
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                &[(&attributes_key).into(), (&java_attributes).into()],
            )?;
            
            Ok(hash_map)
        }
        // Create a java `Map<String, Object>` with two keys:
        //   - "kind" which corresponds to the push type, stored as a `String`
        //   - "values" which corresponds to the array of values received, stored as `Object[]`
        // Only string messages are supported now by Valkey and `redis-rs`.
        Value::Push { kind, data } => {
            let hash_map = env.new_object("java/util/HashMap", "()V", &[])?;

            let kind_str = env.new_string("kind")?;
            let kind_value_str = env.new_string(format!("{kind:?}"))?;

            let _ = env.call_method(
                &hash_map,
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                &[(&kind_str).into(), (&kind_value_str).into()],
            )?;

            let values_str = env.new_string("values")?;
            let values = array_to_java_array(env, data, encoding_utf8)?;

            let _ = env.call_method(
                &hash_map,
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                &[(&values_str).into(), (&values).into()],
            )?;

            Ok(hash_map)
        }
        Value::ServerError(server_error) => {
            let err_msg = error_message(&server_error.into());
            let java_exception = env.new_object(
                "glide/api/models/exceptions/RequestException",
                "(Ljava/lang/String;)V",
                &[(&env.new_string(err_msg)?).into()],
            )?;
            Ok(java_exception)
        }
    }
}

/// Convert an array of values into java array of corresponding values.
///
/// Recursively calls to [`resp_value_to_java`] for every element.
///
/// Returns an arbitrary java `Object[]`.
fn array_to_java_array<'local>(
    env: &mut JNIEnv<'local>,
    values: Vec<Value>,
    encoding_utf8: bool,
) -> Result<JObject<'local>, FFIError> {
    let items: JObjectArray =
        env.new_object_array(values.len() as i32, "java/lang/Object", JObject::null())?;

    for (i, item) in values.into_iter().enumerate() {
        let java_value = resp_value_to_java(env, item, encoding_utf8)?;
        env.set_object_array_element(&items, i as i32, java_value)?;
    }

    Ok(items.into())
}


/// Returns the maximum total length in bytes of request arguments.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `_env`    - The JNI environment. Not used.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_getMaxRequestArgsLengthInBytes<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    MAX_REQUEST_ARGS_LENGTH_IN_BYTES as jlong
}

/// Convert a Valkey Value pointer to a Java object with UTF-8 string encoding.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`     - The JNI environment.
/// * `_class`  - The class object. Not used.
/// * `pointer` - A pointer to a Valkey Value object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_valueFromPointer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn value_from_pointer<'a>(
                env: &mut JNIEnv<'a>,
                pointer: jlong,
            ) -> Result<JObject<'a>, FFIError> {
                if pointer == 0 {
                    return Ok(JObject::null());
                }
                
                // The pointer is now an ID from JniResponseRegistry, not a Rust pointer
                // We need to retrieve the Java object from the registry
                let registry_class = env.find_class("glide/managers/JniResponseRegistry")?;
                let retrieve_method = env.get_static_method_id(
                    &registry_class,
                    "retrieveAndRemove",
                    "(J)Ljava/lang/Object;"
                )?;
                
                let result = unsafe {
                    env.call_static_method_unchecked(
                        &registry_class,
                        retrieve_method,
                        jni::signature::ReturnType::Object,
                        &[jni::sys::jvalue { j: pointer }]
                    )
                }?;
                
                match result {
                    jni::objects::JValueGen::Object(obj) => Ok(obj),
                    _ => Ok(JObject::null()),
                }
            }
            let result = value_from_pointer(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointer",
    )
    .unwrap_or(JObject::null())
}

/// Convert a Redis Value pointer to a Java object with binary (byte[]) encoding.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`     - The JNI environment.
/// * `_class`  - The class object. Not used.
/// * `pointer` - A pointer to a Valkey Value object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_valueFromPointerBinary<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pointer: jlong,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn value_from_pointer_binary<'a>(
                env: &mut JNIEnv<'a>,
                pointer: jlong,
            ) -> Result<JObject<'a>, FFIError> {
                if pointer == 0 {
                    return Ok(JObject::null());
                }
                
                // Same as valueFromPointer - retrieve from registry
                // The object is already converted with the correct encoding
                let registry_class = env.find_class("glide/managers/JniResponseRegistry")?;
                let retrieve_method = env.get_static_method_id(
                    &registry_class,
                    "retrieveAndRemove",
                    "(J)Ljava/lang/Object;"
                )?;
                
                let result = unsafe {
                    env.call_static_method_unchecked(
                        &registry_class,
                        retrieve_method,
                        jni::signature::ReturnType::Object,
                        &[jni::sys::jvalue { j: pointer }]
                    )
                }?;
                
                match result {
                    jni::objects::JValueGen::Object(obj) => Ok(obj),
                    _ => Ok(JObject::null()),
                }
            }
            let result = value_from_pointer_binary(&mut env, pointer);
            handle_errors(&mut env, result)
        },
        "valueFromPointerBinary",
    )
    .unwrap_or(JObject::null())
}

/// Create a leaked byte vector from Java byte array arguments.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`     - The JNI environment.
/// * `_class`  - The class object. Not used.
/// * `args`    - A Java array of byte arrays.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_GlideValueResolver_createLeakedBytesVec<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    args: JObjectArray<'local>,
) -> jlong {
    handle_panics(
        move || {
            fn create_leaked_bytes_vec<'a>(
                env: &mut JNIEnv<'a>,
                args: JObjectArray<'a>,
            ) -> Result<jlong, FFIError> {
                let length = env.get_array_length(&args)? as usize;
                let mut byte_arrays = Vec::with_capacity(length);

                for i in 0..length {
                    let byte_array_obj = env.get_object_array_element(&args, i as i32)?;
                    let byte_array = JByteArray::from(byte_array_obj);
                    let bytes = env.convert_byte_array(&byte_array)?;
                    byte_arrays.push(bytes);
                }

                // Create a Box and leak it to get a stable pointer
                let boxed_vec = Box::new(byte_arrays);
                let leaked_ptr = Box::into_raw(boxed_vec);
                Ok(leaked_ptr as jlong)
            }
            let result = create_leaked_bytes_vec(&mut env, args);
            handle_errors(&mut env, result)
        },
        "createLeakedBytesVec",
    )
    .unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JByteArray<'local>,
) -> JString<'local> {
    handle_panics(
        move || {
            fn store_script<'a>(
                env: &mut JNIEnv<'a>,
                code: JByteArray<'a>,
            ) -> Result<JString<'a>, FFIError> {
                let bytes = env.convert_byte_array(&code)?;
                let hash = glide_core::scripts_container::add_script(&bytes);
                Ok(env.new_string(hash)?)
            }
            let result = store_script(&mut env, code);
            handle_errors(&mut env, result)
        },
        "storeScript",
    )
    .unwrap_or(JString::<'_>::default())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    sha1: JString<'local>,
) {
    handle_panics(
        move || {
            fn drop_script(env: &mut JNIEnv<'_>, sha1: JString<'_>) -> Result<(), FFIError> {
                let sha: String = env.get_string(&sha1)?.into();
                glide_core::scripts_container::remove_script(&sha);
                Ok(())
            }
            let result = drop_script(&mut env, sha1);
            handle_errors(&mut env, result)
        },
        "dropScript",
    )
    .unwrap_or(())
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level(0),
            logger_core::Level::Warn => Level(1),
            logger_core::Level::Info => Level(2),
            logger_core::Level::Debug => Level(3),
            logger_core::Level::Trace => Level(4),
            logger_core::Level::Off => Level(5),
        }
    }
}

impl TryFrom<Level> for logger_core::Level {
    type Error = FFIError;
    fn try_from(level: Level) -> Result<Self, <logger_core::Level as TryFrom<Level>>::Error> {
        match level.0 {
            0 => Ok(logger_core::Level::Error),
            1 => Ok(logger_core::Level::Warn),
            2 => Ok(logger_core::Level::Info),
            3 => Ok(logger_core::Level::Debug),
            4 => Ok(logger_core::Level::Trace),
            5 => Ok(logger_core::Level::Off),
            _ => Err(FFIError::Logger(format!(
                "Invalid log level: {:?}",
                level.0
            ))),
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_LoggerResolver_logInternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
    log_identifier: JString<'local>,
    message: JString<'local>,
) {
    handle_panics(
        move || {
            fn log_internal(
                env: &mut JNIEnv<'_>,
                level: jint,
                log_identifier: JString<'_>,
                message: JString<'_>,
            ) -> Result<(), FFIError> {
                let level = Level(level);

                let log_identifier: String = env.get_string(&log_identifier)?.into();

                let message: String = env.get_string(&message)?.into();

                logger_core::log(level.try_into()?, log_identifier, message);
                Ok(())
            }
            let result = log_internal(&mut env, level, log_identifier, message);
            handle_errors(&mut env, result)
        },
        "logInternal",
    )
    .unwrap_or(())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_LoggerResolver_initInternal<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level: jint,
    file_name: JString<'local>,
) -> jint {
    handle_panics(
        move || {
            fn init_internal(
                env: &mut JNIEnv<'_>,
                level: jint,
                file_name: JString<'_>,
            ) -> Result<jint, FFIError> {
                let level = if level >= 0 { Some(level) } else { None };
                let file_name: Option<String> = match env.get_string(&file_name) {
                    Ok(file_name) => Some(file_name.into()),
                    Err(JniError::NullPtr(_)) => None,
                    Err(err) => return Err(err.into()),
                };
                let level = match level {
                    Some(lvl) => Some(Level(lvl).try_into()?),
                    None => None,
                };
                let logger_level = logger_core::init(level, file_name.as_deref());
                Ok(Level::from(logger_level).0)
            }
            let result = init_internal(&mut env, level, file_name);
            handle_errors(&mut env, result)
        },
        "initInternal",
    )
    .unwrap_or(0)
}

/// Releases a ClusterScanCursor handle allocated in Rust.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `_env`    - The JNI environment. Not used.
/// * `_class`  - The class object. Not used.
/// * cursor      - The cursor handle to release.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_releaseNativeCursor<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    cursor: JString<'local>,
) {
    handle_panics(
        move || {
            fn release_native_cursor(
                env: &mut JNIEnv<'_>,
                cursor: JString<'_>,
            ) -> Result<(), FFIError> {
                let cursor_str: String = env.get_string(&cursor)?.into();
                glide_core::cluster_scan_container::remove_scan_state_cursor(cursor_str);
                Ok(())
            }
            let result = release_native_cursor(&mut env, cursor);
            handle_errors(&mut env, result)
        },
        "releaseNativeCursor",
    )
    .unwrap_or(())
}

/// Returns the String representing a finished cursor handle.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_getFinishedCursorHandleConstant<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, FINISHED_SCAN_CURSOR, "getFinishedCursorHandleConstant")
}

/// Returns the String representing the name of the ObjectType String.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeStringConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_STRING, "getTypeStringConstant")
}

/// Returns the String representing the name of the ObjectType List.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeListConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_LIST, "getTypeListConstant")
}

/// Returns the String representing the name of the ObjectType Set.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeSetConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_SET, "getTypeSetConstant")
}

/// Returns the String representing the name of the ObjectType ZSet.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeZSetConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_ZSET, "getTypeZSetConstant")
}

/// Returns the String representing the name of the ObjectType Hash.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeHashConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_HASH, "getTypeHashConstant")
}

/// Returns the String representing the name of the ObjectType Set.
///
/// This function is meant to be invoked by Java using JNI. This is used to ensure
/// that this constant is consistent with the Rust client.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ObjectTypeResolver_getTypeStreamConstant<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    safe_create_jstring(env, TYPE_STREAM, "getTypeStreamConstant")
}

/// Returns a Java's `HashMap` representing the statistics collected for this process.
///
/// This function is meant to be invoked by Java using JNI.
///
/// * `env`    - The JNI environment.
/// * `_class`  - The class object. Not used.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_StatisticsResolver_getStatistics<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    let Some(mut map) = linked_hashmap::new_linked_hashmap(&mut env) else {
        return JObject::null();
    };

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_connections",
        &format!("{}", Telemetry::total_connections()),
    );

    linked_hashmap::put_strings(
        &mut env,
        &mut map,
        "total_clients",
        &format!("{}", Telemetry::total_clients()),
    );

    map
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_initOpenTelemetry<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    traces_endpoint: JString<'local>,
    traces_sample_percentage: jint,
    metrics_endpoint: JString<'local>,
    flush_interval_ms: jlong,
) -> jint {
    handle_panics(
        move || {
            fn init_open_telemetry<'a>(
                env: &mut JNIEnv<'a>,
                traces_endpoint: JString<'a>,
                traces_sample_percentage: jint,
                metrics_endpoint: JString<'a>,
                flush_interval_ms: jlong,
            ) -> Result<jint, FFIError> {
                // Convert JString to Rust String or None if null
                let traces_endpoint: Option<String> = match env.get_string(&traces_endpoint) {
                    Ok(endpoint) => Some(endpoint.into()),
                    Err(JniError::NullPtr(_)) => None,
                    Err(err) => return Err(err.into()),
                };

                let metrics_endpoint: Option<String> = match env.get_string(&metrics_endpoint) {
                    Ok(endpoint) => Some(endpoint.into()),
                    Err(JniError::NullPtr(_)) => None,
                    Err(err) => return Err(err.into()),
                };

                // Validate that at least one endpoint is provided
                if traces_endpoint.is_none() && metrics_endpoint.is_none() {
                    return Err(FFIError::OpenTelemetry(
                        "At least one of traces or metrics must be provided for OpenTelemetry configuration.".to_string(),
                    ));
                }
                // Validate flush interval
                if flush_interval_ms <= 0 {
                    return Err(FFIError::OpenTelemetry(format!(
                        "InvalidInput: flushIntervalMs must be a positive integer (got: {flush_interval_ms})"
                    )));
                }

                let mut config = glide_core::GlideOpenTelemetryConfigBuilder::default();

                // Initialize traces exporter if endpoint is provided
                if let Some(endpoint) = traces_endpoint {
                    config = config.with_trace_exporter(
                        glide_core::GlideOpenTelemetrySignalsExporter::from_str(&endpoint)
                            .map_err(|e| FFIError::OpenTelemetry(format!("{e}")))?,
                        if traces_sample_percentage >= 0 {
                            Some(traces_sample_percentage as u32)
                        } else {
                            return Err(FFIError::OpenTelemetry(format!(
                                "InvalidInput: traces_sample_percentage must be a positive integer (got: {traces_sample_percentage})"
                                ))
                            );
                        },
                    );
                }

                // Initialize metrics exporter if endpoint is provided
                if let Some(endpoint) = metrics_endpoint {
                    config = config.with_metrics_exporter(
                        glide_core::GlideOpenTelemetrySignalsExporter::from_str(&endpoint)
                            .map_err(|e| FFIError::OpenTelemetry(format!("{e}")))?,
                    );
                }

                // Set flush interval
                config = config.with_flush_interval(std::time::Duration::from_millis(flush_interval_ms as u64));

                // Initialize OpenTelemetry
                let glide_rt = match glide_core::client::get_or_init_runtime() {
                    Ok(handle) => handle,
                    Err(err) => {
                        return Err(FFIError::OpenTelemetry(format!(
                            "Failed to get or init runtime: {err}"
                        )))
                    }
                };

                glide_rt.runtime.block_on(async {
                    if let Err(e) = glide_core::GlideOpenTelemetry::initialise(config.build()) {
                        logger_core::log(
                            logger_core::Level::Error,
                            "OpenTelemetry",
                            format!("Failed to initialize OpenTelemetry: {e}"),
                        );
                        return Err(FFIError::OpenTelemetry(format!(
                            "Failed to initialize OpenTelemetry: {e}"
                        )));
                    }
                    Ok(())
                })?;

                Ok(0 as jint)
            }
            let result = init_open_telemetry(&mut env, traces_endpoint, traces_sample_percentage, metrics_endpoint, flush_interval_ms);
            handle_errors(&mut env, result)
        },
        "initOpenTelemetry",
    )
    .unwrap_or(0 as jint)
}

/// Creates an open telemetry span with the given name and returns a pointer to the span
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_createLeakedOtelSpan<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    name: JString<'local>,
) -> jlong {
    handle_panics(
        move || {
            fn create_leaked_otel_span<'a>(
                env: &mut JNIEnv<'a>,
                name: JString<'a>,
            ) -> Result<jlong, FFIError> {
                let name_str: String = env.get_string(&name)?.into();
                let span = glide_core::GlideOpenTelemetry::new_span(&name_str);
                let s = Arc::into_raw(Arc::new(span)) as *mut glide_core::GlideSpan;
                Ok(s as jlong)
            }
            let result = create_leaked_otel_span(&mut env, name);
            handle_errors(&mut env, result)
        },
        "createLeakedOtelSpan",
    )
    .unwrap_or(0)
}

/// Drops an OpenTelemetry span given its pointer
/// # Safety
/// * `span_ptr` must not be `null`.
/// * `span_ptr` must be able to be safely casted to a valid [`Arc<glide_core::GlideSpan>`] via [`Arc::from_raw`]. See the safety documentation of [`Arc::from_raw`].
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_glide_ffi_resolvers_OpenTelemetryResolver_dropOtelSpan<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    span_ptr: jlong,
) {
    handle_panics(
        move || {
            fn drop_otel_span(span_ptr: jlong) -> Result<(), FFIError> {
                if span_ptr <= 0 {
                    return Err(FFIError::OpenTelemetry(
                        "Received an invalid pointer value.".to_string(),
                    ));
                }
                unsafe {
                    Arc::from_raw(span_ptr as *const glide_core::GlideSpan);
                }
                Ok(())
            }
            let result = drop_otel_span(span_ptr);
            handle_errors(&mut env, result)
        },
        "dropOtelSpan",
    )
    .unwrap_or(())
}

/// Convert a Rust string to a Java String and handle errors.
///
/// * `env`             - The JNI environment.
/// * `_class`          - The class object. Not used.
/// * `input`           - The String to convert.
/// * `functionName`    - The name of the calling function.
fn safe_create_jstring<'local>(
    mut env: JNIEnv<'local>,
    input: &str,
    function_name: &str,
) -> JString<'local> {
    handle_panics(
        move || {
            fn create_jstring<'a>(
                env: &mut JNIEnv<'a>,
                input: &str,
            ) -> Result<JString<'a>, FFIError> {
                Ok(env.new_string(input)?)
            }
            let result = create_jstring(&mut env, input);
            handle_errors(&mut env, result)
        },
        function_name,
    )
    .unwrap_or(JString::<'_>::default())
}

// ==================== JNI CLIENT MANAGEMENT FUNCTIONS ====================

/// Create Valkey client and store handle.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_createClient(
    mut env: JNIEnv,
    _class: JClass,
    addresses: JObjectArray,
    database_id: jint,
    username: jni::sys::jstring,
    password: jni::sys::jstring,
    use_tls: jni::sys::jboolean,
    insecure_tls: jni::sys::jboolean,
    cluster_mode: jni::sys::jboolean,
    request_timeout_ms: jint,
    connection_timeout_ms: jint,
    max_inflight_requests: jint,
    read_from: jni::sys::jstring,
    client_az: jni::sys::jstring,
    lazy_connect: jni::sys::jboolean,
    client_name: jni::sys::jstring,
    sub_exact: JObjectArray,
    sub_pattern: JObjectArray,
    sub_sharded: JObjectArray,
) -> jlong {
    handle_panics(
        move || {
            // Convert Java parameters to Rust types
            let addresses_result: Result<Vec<String>, FFIError> = (|| {
                let length = env.get_array_length(&addresses)? as usize;
                let mut addrs = Vec::with_capacity(length);

                for i in 0..length {
                    let addr_obj = env.get_object_array_element(&addresses, i as i32)?;
                    let addr_jstring = JString::from(addr_obj);
                    let addr_str = env.get_string(&addr_jstring)?;
                    addrs.push(addr_str.to_str().map_err(|e| FFIError::Logger(e.to_string()))?.to_string());
                }
                Ok(addrs)
            })();

            let addresses = match addresses_result {
                Ok(addrs) => addrs,
                Err(e) => {
                    log::error!("Failed to parse addresses: {e}");
                    return Some(0);
                }
            };

            let username = get_optional_string_param_raw(&mut env, username);
            let password = get_optional_string_param_raw(&mut env, password);
            let read_from_str = get_optional_string_param_raw(&mut env, read_from);
            let client_az_opt = get_optional_string_param_raw(&mut env, client_az);
            let client_name_opt = get_optional_string_param_raw(&mut env, client_name);

            // Build PubSubSubscriptionInfo from three arrays if any present
            let subscriptions_opt: Option<redis::PubSubSubscriptionInfo> = (|| {
                let mut any = false;
                let mut info = std::collections::HashMap::new();

                // helper to convert array of byte[] to HashSet<PubSubChannelOrPattern>
                fn array_to_set(env: &mut JNIEnv, arr: JObjectArray) -> std::collections::HashSet<redis::PubSubChannelOrPattern> {
                    let mut set = std::collections::HashSet::new();
                    if arr.is_null() { return set; }
                    if let Ok(len) = env.get_array_length(&arr) {
                        for i in 0..len {
                            if let Ok(obj) = env.get_object_array_element(&arr, i) {
                                if let Ok(bytes) = env.convert_byte_array(JByteArray::from(obj)) {
                                    set.insert(redis::PubSubChannelOrPattern::from(bytes));
                                }
                            }
                        }
                    }
                    set
                }

                let exact_set = array_to_set(&mut env, sub_exact);
                if !exact_set.is_empty() {
                    any = true;
                    info.insert(redis::PubSubSubscriptionKind::Exact, exact_set);
                }
                let pattern_set = array_to_set(&mut env, sub_pattern);
                if !pattern_set.is_empty() {
                    any = true;
                    info.insert(redis::PubSubSubscriptionKind::Pattern, pattern_set);
                }
                let sharded_set = array_to_set(&mut env, sub_sharded);
                if !sharded_set.is_empty() {
                    any = true;
                    info.insert(redis::PubSubSubscriptionKind::Sharded, sharded_set);
                }

                if any { Some(info) } else { None }
            })();

            // Create connection configuration using all parameters
            let config = match create_valkey_connection_config(ValkeyClientConfig {
                addresses,
                database_id: database_id as u32,
                username,
                password,
                use_tls: use_tls != 0,
                insecure_tls: insecure_tls != 0,
                cluster_mode: cluster_mode != 0,
                request_timeout_ms: request_timeout_ms as u64,
                connection_timeout_ms: connection_timeout_ms as u64,
                read_from: read_from_str,
                client_az: client_az_opt,
                lazy_connect: lazy_connect != 0,
                client_name: client_name_opt,
                max_inflight_requests: if max_inflight_requests > 0 { Some(max_inflight_requests as u32) } else { None },
                pubsub_subscriptions: subscriptions_opt,
            }) {
                Ok(config) => config,
                Err(e) => {
                    log::error!("Failed to create connection config: {e}");
                    return Some(0);
                }
            };

            // Cache JVM for push callbacks
            if let Ok(jvm) = env.get_java_vm() {
                let _ = jni_client::JVM.set(Arc::new(jvm));
            }

            // Direct client creation (no lazy loading for simplified implementation)
            let runtime = get_runtime();
            // Enable push channel if pubsub subscriptions are present
            let mut rx_opt: Option<tokio::sync::mpsc::UnboundedReceiver<redis::PushInfo>> = None;
            let tx_opt: Option<tokio::sync::mpsc::UnboundedSender<redis::PushInfo>> = if config.pubsub_subscriptions.is_some() {
                let (tx, rx) = tokio::sync::mpsc::unbounded_channel::<redis::PushInfo>();
                rx_opt = Some(rx);
                Some(tx)
            } else { None };

            match runtime.block_on(async { create_glide_client(config, tx_opt).await }) {
                Ok(client) => {
                    let safe_handle = jni_client::generate_safe_handle();
                    let handle_table = get_handle_table();

                    // Store in handle table
                    handle_table.insert(safe_handle, client);

                    // If we created a push channel, spawn a forwarder to deliver pushes to Java
                    if let Some(mut rx) = rx_opt {
                        let jvm_arc = jni_client::JVM.get().cloned();
                        let handle_for_java = safe_handle as jlong;
                        get_runtime().spawn(async move {
                            while let Some(push) = rx.recv().await {
                                if let Some(jvm) = jvm_arc.as_ref()
                                    && let Ok(mut env) = jvm.attach_current_thread_permanently()
                                {
                                    handle_push_notification(&mut env, handle_for_java, push);
                                }
                            }
                        });
                    }

                    Some(safe_handle as jlong)
                }
                Err(e) => {
                    log::error!("Failed to create client: {e}");
                    Some(0)
                }
            }
        },
        "createClient",
    )
    .unwrap_or(0)
}

/// Execute Valkey command asynchronously using protobuf with FFI-imported routing.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeCommandAsync(
    env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    request_bytes: JByteArray,
    callback_id: jlong,
) {
    handle_panics(
        move || {
            let raw_bytes = match env.convert_byte_array(&request_bytes) {
                Ok(b) => b,
                Err(e) => {
                    log::error!("Failed to read command bytes: {e}");
                    return Some(());
                }
            };
            
            if raw_bytes.is_empty() {
                log::error!("Empty command request bytes");
                return Some(());
            }
            
            // Parse actual protobuf CommandRequest using existing glide-core logic
            let command_request = match protobuf_bridge::parse_command_request(&raw_bytes) {
                Ok(r) => r,
                Err(e) => {
                    log::error!("Failed to parse protobuf command request: {e}");
                    return Some(());
                }
            };

            let handle_id = client_ptr as u64;

            // Get JVM for callback completion
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeCommandAsync");
                    return Some(());
                }
            };

            // Spawn async task using existing glide-core command processing logic
            let runtime = get_runtime();
            runtime.spawn(async move {
                // Ensure client exists
                let client_result = ensure_client_for_handle(handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        let root_span_ptr_opt = command_request.root_span_ptr;
                        // Process command using FFI methodology (surgical reuse)
                        let result = match &command_request.command {
                            Some(command_request::Command::SingleCommand(command)) => {
                                // Create command using existing FFI approach
                                match protobuf_bridge::create_redis_command(command) {
                                    Ok(cmd) => {
                                        // Use FFI get_route function directly
                                        let route_box = command_request.route.0;
                                        let routing = if let Some(route_box) = route_box {
                                            match protobuf_bridge::create_routing_info(*route_box, Some(&cmd)) {
                                                Ok(r) => r,
                                                Err(e) => {
                                                    log::error!("Routing error: {e}");
                                                    None
                                                }
                                            }
                                        } else {
                                            None
                                        };
                                        
                                        {
                                            let exec = client
                                                .send_command(&cmd, routing)
                                                .await
                                                .map_err(|e| anyhow::anyhow!("Command execution failed: {e}"));
                                            if let Some(root_span_ptr) = root_span_ptr_opt {
                                                if root_span_ptr != 0 {
                                                    if let Ok(span) = unsafe { glide_core::GlideOpenTelemetry::span_from_pointer(root_span_ptr) } {
                                                        span.end();
                                                    }
                                                    unsafe { std::sync::Arc::from_raw(root_span_ptr as *const glide_core::GlideSpan); }
                                                }
                                            }
                                            exec
                                        }
                                    }
                                    Err(e) => Err(anyhow::anyhow!("Failed to create command: {e}"))
                                }
                            }
                            Some(command_request::Command::Batch(batch)) => {
                                // Handle batch using existing FFI patterns
                                let mut pipeline = redis::Pipeline::with_capacity(batch.commands.len());
                                if batch.is_atomic {
                                    pipeline.atomic();
                                }
                                
                                // Add commands to pipeline using FFI command creation logic
                                for cmd in &batch.commands {
                                    match protobuf_bridge::create_redis_command(cmd) {
                                        Ok(redis_cmd) => pipeline.add_command(redis_cmd),
                                        Err(e) => {
                                            let error = Err(anyhow::anyhow!("Failed to create batch command: {e}"));
                                            complete_callback(jvm, callback_id, error, false);
                                            return;
                                        }
                                    };
                                }
                                
                                // Get routing using FFI approach
                                let route_box = command_request.route.0;
                                let routing = if let Some(route_box) = route_box {
                                    match protobuf_bridge::create_routing_info(*route_box, None) {
                                        Ok(r) => r,
                                        Err(e) => {
                                            log::error!("Routing error: {e}");
                                            None
                                        }
                                    }
                                } else {
                                    None
                                };
                                
                                // Execute using existing client methods
                                if batch.is_atomic {
                                    let exec = client.send_transaction(
                                        &pipeline,
                                        routing,
                                        batch.timeout,
                                        batch.raise_on_error.unwrap_or(true)
                                    ).await.map_err(|e| anyhow::anyhow!("Transaction failed: {e}"));
                                    if let Some(root_span_ptr) = root_span_ptr_opt {
                                        if root_span_ptr != 0 {
                                            if let Ok(span) = unsafe { glide_core::GlideOpenTelemetry::span_from_pointer(root_span_ptr) } {
                                                span.end();
                                            }
                                            unsafe { std::sync::Arc::from_raw(root_span_ptr as *const glide_core::GlideSpan); }
                                        }
                                    }
                                    exec
                                } else {
                                    let exec = client.send_pipeline(
                                        &pipeline,
                                        routing,
                                        batch.raise_on_error.unwrap_or(true),
                                        batch.timeout,
                                        redis::PipelineRetryStrategy {
                                            retry_server_error: batch.retry_server_error.unwrap_or(false),
                                            retry_connection_error: batch.retry_connection_error.unwrap_or(false),
                                        }
                                    ).await.map_err(|e| anyhow::anyhow!("Pipeline failed: {e}"));
                                    if let Some(root_span_ptr) = root_span_ptr_opt {
                                        if root_span_ptr != 0 {
                                            if let Ok(span) = unsafe { glide_core::GlideOpenTelemetry::span_from_pointer(root_span_ptr) } {
                                                span.end();
                                            }
                                            unsafe { std::sync::Arc::from_raw(root_span_ptr as *const glide_core::GlideSpan); }
                                        }
                                    }
                                    exec
                                }
                            }
                            
                            _ => Err(anyhow::anyhow!("Unsupported command type"))
                        };

                        // Complete callback
                        complete_callback(jvm, callback_id, result, false);
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        complete_callback(jvm, callback_id, error, false);
                    }
                }
            });

            Some(())
        },
        "executeCommandAsync",
    )
    .unwrap_or(())
}

/// Close client and release resources.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_closeClient(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) {
    handle_panics(
        move || {
            let handle_table = get_handle_table();
            let handle_id = client_ptr as u64;

            // DashMap operations are sync and lock-free
            if let Some((_, client)) = handle_table.remove(&handle_id) {
                // Schedule async cleanup
                let runtime = get_runtime();
                runtime.spawn(async move {
                    // Drop the client; core will close connections via Drop implementations
                    drop(client);
                });
            }
            Some(())
        },
        "closeClient",
    )
    .unwrap_or(())
}

/// Check if client handle exists.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_isConnected(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) -> jni::sys::jboolean {
    handle_panics(
        move || {
            let handle_table = get_handle_table();
            let handle_id = client_ptr as u64;
            if handle_table.contains_key(&handle_id) {
                Some(1)
            } else {
                Some(0)
            }
        },
        "isConnected",
    )
    .unwrap_or(0)
}

/// Get client information from native layer.
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getClientInfo<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    client_ptr: jlong,
) -> JString<'local> {
    handle_panics(
        move || {
            fn get_client_info<'a>(
                env: &mut JNIEnv<'a>,
                client_ptr: jlong,
            ) -> Result<JString<'a>, FFIError> {
                let handle_id = client_ptr as u64;
                let handle_table = get_handle_table();
                
                if handle_table.contains_key(&handle_id) {
                    // Return basic client information
                    let info = format!("Client handle: {}, Status: Connected", handle_id);
                    Ok(env.new_string(info)?)
                } else {
                    let info = format!("Client handle: {}, Status: Not found", handle_id);
                    Ok(env.new_string(info)?)
                }
            }
            let result = get_client_info(&mut env, client_ptr);
            handle_errors(&mut env, result)
        },
        "getClientInfo",
    )
    .unwrap_or(JString::default())
}

/// Get glide-core default timeout in milliseconds
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultTimeoutMs(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    // Return glide-core's default timeout in milliseconds
    glide_core::client::DEFAULT_RESPONSE_TIMEOUT.as_millis() as jlong
}

/// Get glide-core default maximum inflight requests limit
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_getGlideCoreDefaultMaxInflightRequests(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    // Return glide-core's default max inflight requests
    glide_core::client::DEFAULT_MAX_INFLIGHT_REQUESTS as jint
}

/// Execute a batch (pipeline/transaction) asynchronously using FFI-imported logic
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeBatchAsync(
    env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    batch_request_bytes: JByteArray,
    expect_utf8: jni::sys::jboolean,
    callback_id: jlong,
) {
    handle_panics(
        move || {
            let raw_bytes = match env.convert_byte_array(&batch_request_bytes) {
                Ok(b) => b,
                Err(e) => {
                    log::error!("Failed to read batch bytes: {e}");
                    return Some(());
                }
            };
            
            if raw_bytes.is_empty() {
                log::error!("Empty batch request bytes");
                return Some(());
            }
            
            // Parse actual batch protobuf using existing protobuf logic
            let command_request = match protobuf_bridge::parse_command_request(&raw_bytes) {
                Ok(r) => r,
                Err(e) => {
                    log::error!("Failed to parse batch protobuf request: {e}");
                    return Some(());
                }
            };
            
            // Extract the batch from the command request
            let batch = match &command_request.command {
                Some(command_request::Command::Batch(batch)) => batch,
                _ => {
                    log::error!("Expected batch command in request");
                    return Some(());
                }
            };
            // Extract optional root span pointer from the request (if provided by Java)
            let root_span_ptr_opt = command_request.root_span_ptr;

            let handle_id = client_ptr as u64;
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeBatchAsync");
                    return Some(());
                }
            };
            
            // Spawn async task for batch execution using existing glide-core patterns
            let batch_clone = batch.clone();
            let route_clone = command_request.route.0.map(|r| *r).unwrap_or_default();
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        // Execute batch using existing FFI methodology
                        let result = async {
                            // If we have a root span, create a child span named "send_batch" to match expectations
                            let mut send_batch_span: Option<glide_core::GlideSpan> = None;
                            if let Some(root_span_ptr) = root_span_ptr_opt {
                                if root_span_ptr != 0 {
                                    if let Ok(root_span) = unsafe { glide_core::GlideOpenTelemetry::span_from_pointer(root_span_ptr) } {
                                        if let Ok(child) = root_span.add_span("send_batch") {
                                            send_batch_span = Some(child);
                                        }
                                    }
                                }
                            }
                            // Create pipeline using existing FFI approach
                            let mut pipeline = redis::Pipeline::with_capacity(batch_clone.commands.len());
                            if batch_clone.is_atomic {
                                pipeline.atomic();
                            }
                            
                            // Add commands to pipeline using existing bridge logic
                            for cmd in &batch_clone.commands {
                                match protobuf_bridge::create_redis_command(cmd) {
                                    Ok(redis_cmd) => pipeline.add_command(redis_cmd),
                                    Err(e) => return Err(anyhow::anyhow!("Failed to create batch command: {e}"))
                                };
                            }
                            
                            // Get routing using FFI approach
                            let routing = protobuf_bridge::create_routing_info(route_clone, None)
                                .map_err(|e| anyhow::anyhow!("Routing error: {e}"))?;
                            
                            // Execute using existing client methods
                            let exec_res = if batch_clone.is_atomic {
                                client.send_transaction(
                                    &pipeline,
                                    routing,
                                    batch_clone.timeout,
                                    batch_clone.raise_on_error.unwrap_or(true)
                                ).await.map_err(|e| anyhow::anyhow!("Transaction failed: {e}"))
                            } else {
                                client.send_pipeline(
                                    &pipeline,
                                    routing,
                                    batch_clone.raise_on_error.unwrap_or(true),
                                    batch_clone.timeout,
                                    redis::PipelineRetryStrategy {
                                        retry_server_error: batch_clone.retry_server_error.unwrap_or(false),
                                        retry_connection_error: batch_clone.retry_connection_error.unwrap_or(false),
                                    }
                                ).await.map_err(|e| anyhow::anyhow!("Pipeline failed: {e}"))
                            };

                            // End child span if created
                            if let Some(child) = send_batch_span.as_ref() {
                                child.end();
                            }
                            // End and drop the root span if provided
                            if let Some(root_span_ptr) = root_span_ptr_opt {
                                if root_span_ptr != 0 {
                                    if let Ok(root_span) = unsafe { glide_core::GlideOpenTelemetry::span_from_pointer(root_span_ptr) } {
                                        root_span.end();
                                    }
                                    unsafe { std::sync::Arc::from_raw(root_span_ptr as *const glide_core::GlideSpan); }
                                }
                            }
                            exec_res
                        }.await;

                        let binary_mode = expect_utf8 == 0;
                        complete_callback(jvm, callback_id, result, binary_mode);
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        let binary_mode = expect_utf8 == 0;
                        complete_callback(jvm, callback_id, error, binary_mode);
                    }
                }
            });
            
            Some(())
        },
        "executeBatchAsync",
    )
    .unwrap_or(())
}

/// Execute a binary command asynchronously
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeBinaryCommandAsync(
    env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    request_bytes: JByteArray,
    callback_id: jlong,
) {
    handle_panics(
        move || {
            let raw_bytes = match env.convert_byte_array(&request_bytes) {
                Ok(b) => b,
                Err(e) => {
                    log::error!("Failed to read binary command bytes: {e}");
                    return Some(());
                }
            };
            
            if raw_bytes.is_empty() {
                log::error!("Empty binary command request bytes");
                return Some(());
            }
            
            // Parse protobuf CommandRequest using existing bridge logic
            let command_request = match protobuf_bridge::parse_command_request(&raw_bytes) {
                Ok(r) => r,
                Err(e) => {
                    log::error!("Failed to parse binary protobuf command request: {e}");
                    return Some(());
                }
            };

            let handle_id = client_ptr as u64;
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeBinaryCommandAsync");
                    return Some(());
                }
            };

            // Spawn async task with actual command execution
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        // Execute binary command using existing FFI methodology
                        let result = match &command_request.command {
                            Some(command_request::Command::SingleCommand(command)) => {
                                match protobuf_bridge::create_redis_command(command) {
                                    Ok(cmd) => {
                                        // Get routing using FFI approach
                                        let route_box = command_request.route.0;
                                        let routing = if let Some(route_box) = route_box {
                                            match protobuf_bridge::create_routing_info(*route_box, Some(&cmd)) {
                                                Ok(r) => r,
                                                Err(e) => {
                                                    log::error!("Binary command routing error: {e}");
                                                    None
                                                }
                                            }
                                        } else {
                                            None
                                        };
                                        
                                        client.send_command(&cmd, routing).await
                                            .map_err(|e| anyhow::anyhow!("Binary command execution failed: {e}"))
                                    }
                                    Err(e) => Err(anyhow::anyhow!("Failed to create binary command: {e}"))
                                }
                            }
                            
                            _ => Err(anyhow::anyhow!("Binary command execution only supports single commands and script invocations"))
                        };

                        complete_callback(jvm, callback_id, result, true); // binary_mode = true
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        complete_callback(jvm, callback_id, error, true);
                    }
                }
            });
            
            Some(())
        },
        "executeBinaryCommandAsync",
    )
    .unwrap_or(())
}

/// Execute binary-safe PUBLISH/SPUBLISH asynchronously
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executePublishBinaryAsync(
    env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    sharded: jni::sys::jboolean,
    channel: JByteArray,
    message: JByteArray,
    callback_id: jlong,
) {
    handle_panics(
        move || {
            let channel_bytes = match env.convert_byte_array(&channel) {
                Ok(b) => b,
                Err(e) => {
                    log::error!("Failed to read channel bytes: {e}");
                    return Some(());
                }
            };
            
            let message_bytes = match env.convert_byte_array(&message) {
                Ok(b) => b,
                Err(e) => {
                    log::error!("Failed to read message bytes: {e}");
                    return Some(());
                }
            };

            let handle_id = client_ptr as u64;
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executePublishBinaryAsync");
                    return Some(());
                }
            };

            let is_sharded = sharded != 0;
            // Spawn async task
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        let result = async {
                            let mut cmd = if is_sharded {
                                redis::cmd("SPUBLISH")
                            } else {
                                redis::cmd("PUBLISH")
                            };
                            cmd.arg(channel_bytes.as_slice());
                            cmd.arg(message_bytes.as_slice());
                            
                            client.send_command(&cmd, None).await
                                .map_err(|e| anyhow::anyhow!("Publish failed: {e}"))
                        }.await;
                        
                        complete_callback(jvm, callback_id, result, false);
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        complete_callback(jvm, callback_id, error, false);
                    }
                }
            });
            
            Some(())
        },
        "executePublishBinaryAsync",
    )
    .unwrap_or(())
}

/// Execute a script asynchronously using FFI-imported logic
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeScriptAsync(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
    callback_id: jlong,
    hash: JString,
    keys: jni::objects::JObjectArray,
    args: jni::objects::JObjectArray,
    has_route: jni::sys::jboolean,
    route_type: jint,
    route_param: JString,
    expect_utf8: jni::sys::jboolean,
) {
    handle_panics(
        move || {
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeScriptAsync");
                    return Some(());
                }
            };

            // Extract script hash
            let hash_str = match env.get_string(&hash) {
                Ok(h) => h.to_string_lossy().to_string(),
                Err(e) => {
                    log::error!("Failed to read script hash: {e}");
                    complete_callback(jvm, callback_id, Err(anyhow::anyhow!("Failed to read hash: {e}")), false);
                    return Some(());
                }
            };

            // Extract keys array (supports String[] or byte[][])
            let keys_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
                if keys.is_null() {
                    return Ok(Vec::new());
                }
                let length = env.get_array_length(&keys)? as usize;
                let mut keys_data = Vec::with_capacity(length);

                for i in 0..length {
                    let key_obj = env.get_object_array_element(&keys, i as i32)?;
                    if env.is_instance_of(&key_obj, "[B")? {
                        let key_bytes = env.convert_byte_array(JByteArray::from(key_obj))?;
                        keys_data.push(key_bytes);
                    } else {
                        let jstr = JString::from(key_obj);
                        let s: String = env.get_string(&jstr)?.into();
                        keys_data.push(s.into_bytes());
                    }
                }
                Ok(keys_data)
            })();

            let keys_data = match keys_vec {
                Ok(k) => k,
                Err(e) => {
                    log::error!("Failed to extract script keys: {e}");
                    complete_callback(jvm, callback_id, Err(anyhow::anyhow!("Failed to extract keys: {e}")), false);
                    return Some(());
                }
            };

            // Extract args array (supports String[] or byte[][])
            let args_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
                if args.is_null() {
                    return Ok(Vec::new());
                }
                let length = env.get_array_length(&args)? as usize;
                let mut args_data = Vec::with_capacity(length);

                for i in 0..length {
                    let arg_obj = env.get_object_array_element(&args, i as i32)?;
                    if env.is_instance_of(&arg_obj, "[B")? {
                        let arg_bytes = env.convert_byte_array(JByteArray::from(arg_obj))?;
                        args_data.push(arg_bytes);
                    } else {
                        let jstr = JString::from(arg_obj);
                        let s: String = env.get_string(&jstr)?.into();
                        args_data.push(s.into_bytes());
                    }
                }
                Ok(args_data)
            })();

            let args_data = match args_vec {
                Ok(a) => a,
                Err(e) => {
                    log::error!("Failed to extract script args: {e}");
                    complete_callback(jvm, callback_id, Err(anyhow::anyhow!("Failed to extract args: {e}")), false);
                    return Some(());
                }
            };

            let client_handle_id = handle_id as u64;

            // Extract route parameters on the current thread (avoid JNI env escaping into async)
            let has_route_bool = has_route != 0;
            let route_type_val: i32 = route_type as i32;
            let route_param_str: Option<String> = if !route_param.is_null() {
                match env.get_string(&route_param) {
                    Ok(s) => Some(s.into()),
                    Err(_) => None,
                }
            } else {
                None
            };

            // Spawn async task for script execution using FFI-imported patterns
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(client_handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        // Determine routing: explicit route if provided, otherwise infer from keys via EVALSHA-shaped command
                        let routing_info = if has_route_bool {
                            use glide_core::command_request::{Routes, SimpleRoutes, SlotTypes, SlotIdRoute, SlotKeyRoute, ByAddressRoute};
                            use protobuf::EnumOrUnknown;
                            let mut routes = Routes::default();
                            // Build route based on route_type/route_param
                            // SimpleRoutes
                            if route_type_val >= 0 && route_param_str.is_none() {
                                let simple = match route_type_val {
                                    0 => SimpleRoutes::AllNodes,
                                    1 => SimpleRoutes::AllPrimaries,
                                    _ => SimpleRoutes::Random,
                                };
                                routes.set_simple_routes(simple);
                            } else if route_type_val >= 0 && route_param_str.is_some() {
                                // Slot routes with slot type
                                let slot_type = match route_type_val {
                                    1 => SlotTypes::Replica,
                                    _ => SlotTypes::Primary,
                                };
                                // Try to parse param as integer slot id; if fails, treat as slot key
                                let param_str = route_param_str.unwrap_or_default();
                                if let Ok(slot_id) = param_str.parse::<i32>() {
                                    let mut s = SlotIdRoute::default();
                                    s.slot_type = EnumOrUnknown::new(slot_type);
                                    s.slot_id = slot_id;
                                    routes.set_slot_id_route(s);
                                } else if !param_str.is_empty() {
                                    let mut s = SlotKeyRoute::default();
                                    s.slot_type = EnumOrUnknown::new(slot_type);
                                    s.slot_key = param_str.into();
                                    routes.set_slot_key_route(s);
                                }
                            } else if route_type_val < 0 && route_param_str.is_some() {
                                // ByAddressRoute encoded with route_type = -1 and host:port in route_param
                                let param_str = route_param_str.unwrap_or_default();
                                if let Some((host, port_str)) = param_str.split_once(':') {
                                    if let Ok(port) = port_str.parse::<i32>() {
                                        let mut s = ByAddressRoute::default();
                                        s.host = host.to_string().into();
                                        s.port = port;
                                        routes.set_by_address_route(s);
                                    }
                                }
                            }

                            match protobuf_bridge::create_routing_info(routes, None) {
                                Ok(r) => r,
                                Err(e) => {
                                    complete_callback(jvm, callback_id, Err(anyhow::anyhow!(format!("Routing error: {e}"))), false);
                                    return;
                                }
                            }
                        } else {
                            // Auto route by constructing EVALSHA-shaped command
                            let mut route_cmd = redis::cmd("EVALSHA");
                            route_cmd.arg(hash_str.as_bytes());
                            route_cmd.arg(keys_data.len());
                            for k in &keys_data { route_cmd.arg(k.as_slice()); }
                            for a in &args_data { route_cmd.arg(a.as_slice()); }
                            match protobuf_bridge::create_routing_info(Default::default(), Some(&route_cmd)) {
                                Ok(r) => r,
                                Err(e) => {
                                    complete_callback(jvm, callback_id, Err(anyhow::anyhow!(format!("Routing error: {e}"))), false);
                                    return;
                                }
                            }
                        };

                        let result = client.invoke_script(
                            &hash_str,
                            &keys_data.iter().map(|k| k.as_slice()).collect::<Vec<_>>(),
                            &args_data.iter().map(|a| a.as_slice()).collect::<Vec<_>>(),
                            routing_info
                        ).await
                        .map_err(|e| anyhow::anyhow!("Script execution failed: {e}"));

                        let binary_mode = expect_utf8 == 0;
                        complete_callback(jvm, callback_id, result, binary_mode);
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        let binary_mode = expect_utf8 == 0;
                        complete_callback(jvm, callback_id, error, binary_mode);
                    }
                }
            });
            
            Some(())
        },
        "executeScriptAsync",
    )
    .unwrap_or(())
}

/// Update connection password
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_updateConnectionPassword(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    password: jni::sys::jstring,
    immediate_auth: jni::sys::jboolean,
    callback_id: jlong,
) {
    handle_panics(
        move || {
        let password_opt = get_optional_string_param_raw(&mut env, password);
        let handle_id = _client_ptr as u64;
        let do_immediate = immediate_auth != 0;

            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in updateConnectionPassword");
                    return Some(());
                }
            };

            // Spawn async task
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        let result = client.update_connection_password(password_opt, do_immediate).await
                            .map(|_| redis::Value::Okay)
                            .map_err(|e| anyhow::anyhow!("Password update failed: {e}"));
                        
                        complete_callback(jvm, callback_id, result, false);
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        complete_callback(jvm, callback_id, error, false);
                    }
                }
            });
            
            Some(())
        },
        "updateConnectionPassword",
    )
    .unwrap_or(())
}

/// JNI bridge for cluster scan that properly manages cursor lifecycle
/// This reuses the existing cluster scan logic from glide-core
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeClusterScanAsync(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    cursor_id: JString,
    match_pattern: JString,
    count: jlong,
    object_type: JString,
    expect_utf8: jni::sys::jboolean,
    callback_id: jlong,
) {
    handle_panics(
        move || {
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeClusterScanAsync");
                    return Some(());
                }
            };

            // Extract cursor ID (null-safe: null means initial cursor)
            let cursor_str = if cursor_id.is_null() {
                String::new()
            } else {
                match env.get_string(&cursor_id) {
                    Ok(s) => s.to_string_lossy().to_string(),
                    Err(e) => {
                        log::error!("Failed to read cursor ID: {e}");
                        complete_callback(
                            jvm,
                            callback_id,
                            Err(anyhow::anyhow!("Failed to read cursor ID: {e}")),
                            false,
                        );
                        return Some(());
                    }
                }
            };

            // Extract optional match pattern
            let pattern = if match_pattern.is_null() {
                None
            } else {
                match env.get_string(&match_pattern) {
                    Ok(s) => Some(s.to_string_lossy().to_string()),
                    Err(e) => {
                        log::error!("Failed to read match pattern: {e}");
                        complete_callback(jvm, callback_id, Err(anyhow::anyhow!("Failed to read match pattern: {e}")), false);
                        return Some(());
                    }
                }
            };

            // Extract optional object type
            let obj_type = if object_type.is_null() {
                None  
            } else {
                match env.get_string(&object_type) {
                    Ok(s) => Some(s.to_string_lossy().to_string()),
                    Err(e) => {
                        log::error!("Failed to read object type: {e}");
                        complete_callback(jvm, callback_id, Err(anyhow::anyhow!("Failed to read object type: {e}")), false);
                        return Some(());
                    }
                }
            };

            let client_handle_id = client_ptr as u64;
            let count_value = if count > 0 { Some(count as u32) } else { None };

            // Spawn async task for cluster scan execution
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(client_handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        // Get or create scan state cursor - using redis compatible types for now
                        let scan_state_cursor = if cursor_str.is_empty() || cursor_str == "0" {
                            // Create new initial cursor
                            redis::ScanStateRC::new()
                        } else {
                            // Get existing cursor from container
                            match glide_core::cluster_scan_container::get_cluster_scan_cursor(cursor_str) {
                                Ok(cursor) => cursor,
                                Err(e) => {
                                    complete_callback(jvm, callback_id, Err(anyhow::anyhow!("Invalid cursor: {e}")), false);
                                    return;
                                }
                            }
                        };

                        // Build cluster scan args
                        let mut scan_args_builder = redis::ClusterScanArgs::builder();
                        if let Some(pattern) = pattern {
                            scan_args_builder = scan_args_builder.with_match_pattern::<bytes::Bytes>(pattern.into());
                        }
                        if let Some(count) = count_value {
                            scan_args_builder = scan_args_builder.with_count(count);
                        }
                        if let Some(obj_type) = obj_type {
                            scan_args_builder = scan_args_builder.with_object_type(obj_type.into());
                        }
                        let scan_args = scan_args_builder.build();

                        // Execute cluster scan
                        let result = client.cluster_scan(&scan_state_cursor, scan_args).await
                            .map_err(|e| anyhow::anyhow!("Cluster scan execution failed: {e}"));

                        // binary_mode = !expect_utf8
                        let binary_mode = expect_utf8 == 0;
                        complete_callback(jvm, callback_id, result, binary_mode);
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        let binary_mode = expect_utf8 == 0;
                        complete_callback(jvm, callback_id, error, binary_mode);
                    }
                }
            });
            
            Some(())
        },
        "executeClusterScanAsync",
    )
    .unwrap_or(())
}
