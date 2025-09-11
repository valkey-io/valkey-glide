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
                Ok(JObject::from(env.new_string(data)?))
            } else {
                Ok(JObject::from(env.byte_array_from_slice(data.as_bytes())?))
            }
        }
        Value::Okay => Ok(JObject::from(env.new_string("OK")?)),
        Value::Int(num) => Ok(env.new_object("java/lang/Long", "(J)V", &[num.into()])?),
        Value::BulkString(data) => {
            if encoding_utf8 {
                let utf8_str = String::from_utf8(data)?;
                Ok(JObject::from(env.new_string(utf8_str)?))
            } else {
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
            // Convert Redis BigNumber to Java BigInteger
            // BigNumbers in Redis are represented as strings
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
            // Convert Redis Attribute to Java Map<String, Object>
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


#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    code: JByteArray,
) -> JObject<'local> {
    handle_panics(
        move || {
            fn store_script<'a>(
                env: &mut JNIEnv<'a>,
                code: JByteArray,
            ) -> Result<JObject<'a>, FFIError> {
                let code_byte_array = env.convert_byte_array(code)?;
                let hash = glide_core::scripts_container::add_script(&code_byte_array);
                Ok(JObject::from(env.new_string(hash)?))
            }
            let result = store_script(&mut env, code);
            handle_errors(&mut env, result)
        },
        "storeScript",
    )
    .unwrap_or(JObject::null())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    hash: JString,
) {
    handle_panics(
        move || {
            fn drop_script(env: &mut JNIEnv<'_>, hash: JString) -> Result<(), FFIError> {
                let hash_str: String = env.get_string(&hash)?.into();
                glide_core::scripts_container::remove_script(&hash_str);
                Ok(())
            }
            let result = drop_script(&mut env, hash);
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
) -> JObject<'local> {
    handle_panics(
        move || {
            fn init_open_telemetry<'a>(
                env: &mut JNIEnv<'a>,
                traces_endpoint: JString<'a>,
                traces_sample_percentage: jint,
                metrics_endpoint: JString<'a>,
                flush_interval_ms: jlong,
            ) -> Result<JObject<'a>, FFIError> {
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

                Ok(JObject::null())
            }
            let result = init_open_telemetry(&mut env, traces_endpoint, traces_sample_percentage, metrics_endpoint, flush_interval_ms);
            handle_errors(&mut env, result)
        },
        "initOpenTelemetry",
    )
    .unwrap_or(JObject::null())
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
    _max_inflight_requests: jint,
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

            // Create connection configuration using simplified parameters
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
                read_from: None,
                client_az: None,
                lazy_connect: false,
                client_name: None,
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
            let tx_opt: Option<tokio::sync::mpsc::UnboundedSender<redis::PushInfo>> = None;

            match runtime.block_on(async { create_glide_client(config, tx_opt).await }) {
                Ok(client) => {
                    let safe_handle = jni_client::generate_safe_handle();
                    let handle_table = get_handle_table();

                    // Store in handle table
                    handle_table.insert(safe_handle, client);

                    log::debug!("Created client with handle: {safe_handle}");
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
                                        
                                        client.send_command(&cmd, routing).await
                                            .map_err(|e| anyhow::anyhow!("Command execution failed: {e}"))
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
                                    client.send_transaction(
                                        &pipeline,
                                        routing,
                                        batch.timeout,
                                        batch.raise_on_error.unwrap_or(true)
                                    ).await.map_err(|e| anyhow::anyhow!("Transaction failed: {e}"))
                                } else {
                                    client.send_pipeline(
                                        &pipeline,
                                        routing,
                                        batch.raise_on_error.unwrap_or(true),
                                        batch.timeout,
                                        redis::PipelineRetryStrategy {
                                            retry_server_error: batch.retry_server_error.unwrap_or(false),
                                            retry_connection_error: batch.retry_connection_error.unwrap_or(false),
                                        }
                                    ).await.map_err(|e| anyhow::anyhow!("Pipeline failed: {e}"))
                                }
                            }
                            Some(command_request::Command::ScriptInvocation(script)) => {
                                // Handle script using FFI approach
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
                                
                                let keys: Vec<&[u8]> = script.keys.iter().map(|k| k.as_ref()).collect();
                                let args: Vec<&[u8]> = script.args.iter().map(|a| a.as_ref()).collect();
                                
                                client.invoke_script(&script.hash, &keys, &args, routing).await
                                    .map_err(|e| anyhow::anyhow!("Script execution failed: {e}"))
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
                log::debug!("Removed client with handle: {handle_id}");

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

            let handle_id = client_ptr as u64;
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeBatchAsync");
                    return Some(());
                }
            };

            log::debug!("Executing batch with {} commands (atomic: {})", 
                       batch.commands.len(), batch.is_atomic);

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
                            if batch_clone.is_atomic {
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
                            }
                        }.await;

                        complete_callback(jvm, callback_id, result, false); // binary_output handled elsewhere
                    }
                    Err(err) => {
                        let error = Err(anyhow::anyhow!("Client not found: {err}"));
                        complete_callback(jvm, callback_id, error, false);
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

            log::debug!("Executing binary command async with {} bytes", raw_bytes.len());

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
                            _ => Err(anyhow::anyhow!("Binary command execution only supports single commands"))
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
            log::debug!("Executing {} publish async", if is_sharded { "sharded" } else { "regular" });

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
    _has_route: jni::sys::jboolean,
    _route_type: jint,
    _route_param: JString,
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

            // Extract keys array
            let keys_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
                if keys.is_null() {
                    return Ok(Vec::new());
                }
                let length = env.get_array_length(&keys)? as usize;
                let mut keys_data = Vec::with_capacity(length);

                for i in 0..length {
                    let key_obj = env.get_object_array_element(&keys, i as i32)?;
                    let key_bytes = env.convert_byte_array(JByteArray::from(key_obj))?;
                    keys_data.push(key_bytes);
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

            // Extract args array
            let args_vec: Result<Vec<Vec<u8>>, FFIError> = (|| {
                if args.is_null() {
                    return Ok(Vec::new());
                }
                let length = env.get_array_length(&args)? as usize;
                let mut args_data = Vec::with_capacity(length);

                for i in 0..length {
                    let arg_obj = env.get_object_array_element(&args, i as i32)?;
                    let arg_bytes = env.convert_byte_array(JByteArray::from(arg_obj))?;
                    args_data.push(arg_bytes);
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

            // For script execution, route parsing will be handled differently
            // TODO: Implement proper script routing using protobuf Routes if needed
            let routing_info: Option<redis::cluster_routing::RoutingInfo> = None;

            let client_handle_id = handle_id as u64;
            log::debug!("Executing script '{}' with {} keys and {} args", hash_str, keys_data.len(), args_data.len());

            // Spawn async task for script execution using FFI-imported patterns
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(client_handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        // Execute script using FFI approach  
                        let result = client.invoke_script(
                            &hash_str,
                            &keys_data.iter().map(|k| k.as_slice()).collect::<Vec<_>>(),
                            &args_data.iter().map(|a| a.as_slice()).collect::<Vec<_>>(),
                            routing_info
                        ).await
                        .map_err(|e| anyhow::anyhow!("Script execution failed: {e}"));

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

            log::debug!("Updating connection password, immediate_auth: {}", do_immediate);

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

/// Execute cluster scan command asynchronously
#[unsafe(no_mangle)]
pub extern "system" fn Java_glide_internal_GlideNativeBridge_executeClusterScanAsync(
    env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    scan_request_bytes: JByteArray,
    callback_id: jlong,
) {
    handle_panics(
        move || {
            let raw_bytes = match env.convert_byte_array(&scan_request_bytes) {
                Ok(b) => b,
                Err(e) => {
                    log::error!("Failed to read cluster scan bytes: {e}");
                    return Some(());
                }
            };
            
            if raw_bytes.is_empty() {
                log::error!("Empty cluster scan request bytes");
                return Some(());
            }

            // Parse protobuf CommandRequest for cluster scan using existing bridge logic
            let command_request = match protobuf_bridge::parse_command_request(&raw_bytes) {
                Ok(r) => r,
                Err(e) => {
                    log::error!("Failed to parse cluster scan protobuf request: {e}");
                    return Some(());
                }
            };

            let handle_id = client_ptr as u64;
            let jvm = match env.get_java_vm() {
                Ok(jvm) => Arc::new(jvm),
                Err(_) => {
                    log::error!("JVM error in executeClusterScanAsync");
                    return Some(());
                }
            };

            log::debug!("Executing cluster scan async with {} bytes", raw_bytes.len());

            // Spawn async task with actual cluster scan execution
            let runtime = get_runtime();
            runtime.spawn(async move {
                let client_result = ensure_client_for_handle(handle_id).await;
                match client_result {
                    Ok(mut client) => {
                        // Execute cluster scan using existing FFI methodology
                        let result = match &command_request.command {
                            Some(command_request::Command::SingleCommand(command)) => {
                                match protobuf_bridge::create_redis_command(command) {
                                    Ok(cmd) => {
                                        // Execute command using FFI approach with routing
                                        let route_box = command_request.route.0;
                                        let routing = if let Some(route_box) = route_box {
                                            match protobuf_bridge::create_routing_info(*route_box, Some(&cmd)) {
                                                Ok(r) => r,
                                                Err(e) => {
                                                    log::error!("Cluster scan routing error: {e}");
                                                    None
                                                }
                                            }
                                        } else {
                                            None
                                        };
                                        
                                        client.send_command(&cmd, routing).await
                                            .map_err(|e| anyhow::anyhow!("Cluster scan execution failed: {e}"))
                                    }
                                    Err(e) => Err(anyhow::anyhow!("Failed to create cluster scan command: {e}"))
                                }
                            }
                            _ => Err(anyhow::anyhow!("Cluster scan execution only supports single commands"))
                        };

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
        "executeClusterScanAsync",
    )
    .unwrap_or(())
}
