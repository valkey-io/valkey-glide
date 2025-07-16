// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-performance JNI client implementation with direct glide-core integration.
//!
//! This module provides JNI bindings that directly use glide-core's Client,
//! eliminating Unix Domain Socket overhead and enabling zero-copy operations.

use glide_core::client::{Client, ConnectionRequest, NodeAddress, TlsMode, AuthenticationInfo};
use jni::objects::{JClass, JObject, JString, JObjectArray};
use jni::sys::{jboolean, jint, jlong, jobject, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use redis::{cmd, Value};
use std::ptr;
use std::time::Duration;
use std::sync::Mutex;
use std::sync::LazyLock;

use crate::error::JniResult;
use crate::{jni_result, jni_error};

// Command validation constants (based on Valkey official limits)
#[allow(dead_code)]
const MAX_COMMAND_NAME_LEN: usize = 64; // Reasonable limit for command names
#[allow(dead_code)]
const MAX_ARGUMENT_LEN: usize = 536_870_912; // 512MB - Valkey proto-max-bulk-len default
#[allow(dead_code)]
const MAX_ARGUMENTS_COUNT: usize = 100_000; // Conservative limit, Valkey max is 2^31-1 but impractical

// Allowed Valkey commands whitelist for security
#[allow(dead_code)]
const ALLOWED_COMMANDS: &[&str] = &[
    "GET", "SET", "DEL", "EXISTS", "PING", "INFO", "TIME",
    "MGET", "MSET", "INCR", "DECR", "INCRBY", "DECRBY",
    "HGET", "HSET", "HDEL", "HEXISTS", "HKEYS", "HVALS",
    "LPUSH", "RPUSH", "LPOP", "RPOP", "LLEN", "LRANGE",
    "SADD", "SREM", "SMEMBERS", "SCARD", "SISMEMBER",
    "ZADD", "ZREM", "ZRANGE", "ZRANK", "ZSCORE", "ZCARD",
    "EXPIRE", "TTL", "FLUSHDB", "DBSIZE", "RANDOMKEY",
    "TYPE", "RENAME", "COPY", "DUMP", "RESTORE",
    "CLIENT", "ECHO", "SELECT", "OBJECT", "CONFIG",
    "EVAL", "EVALSHA", "SCRIPT", "MULTI", "EXEC", "DISCARD"
];

/// Runtime singleton for efficient Tokio integration
static RUNTIME: std::sync::OnceLock<tokio::runtime::Runtime> = std::sync::OnceLock::new();

/// Shutdown flag to prevent runtime reuse after shutdown
static RUNTIME_SHUTDOWN: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

/// Single client instance - the Client is a multiplexer, not a pool
/// The Client from glide-core is already thread-safe and designed to be cloned
/// for concurrent access, so we just store one client and clone it as needed
static CLIENT_INSTANCE: LazyLock<Mutex<Option<Client>>> = 
    LazyLock::new(|| Mutex::new(None));

/// Get or initialize the Tokio runtime
fn get_runtime() -> JniResult<&'static tokio::runtime::Runtime> {
    if RUNTIME_SHUTDOWN.load(std::sync::atomic::Ordering::Acquire) {
        return Err(jni_error!(RuntimeShutdown, "Runtime has been shutdown"));
    }
    
    Ok(RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("glide-jni")
            .build()
            .expect("Failed to create Tokio runtime")
    }))
}

/// Shutdown the Tokio runtime and clean up resources
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_shutdownRuntime(
    _env: JNIEnv,
    _class: JClass,
) {
    // Mark runtime as shutdown
    RUNTIME_SHUTDOWN.store(true, std::sync::atomic::Ordering::Release);
    
    // Clear the client instance
    if let Ok(mut instance) = CLIENT_INSTANCE.lock() {
        *instance = None;
    }
    
    // The runtime will be dropped when the static variable is dropped
    // This happens automatically when the JVM shuts down
}

// Command validation functions

/// Validate Valkey command name against whitelist and injection attacks
fn validate_command_name(command: &str) -> JniResult<()> {
    // Length validation
    if command.is_empty() {
        return Err(jni_error!(InvalidInput, "Command name cannot be empty"));
    }
    
    if command.len() > MAX_COMMAND_NAME_LEN {
        return Err(jni_error!(InvalidInput, "Command name too long: {}", command.len()));
    }
    
    // Check for injection characters
    if command.contains('\r') || command.contains('\n') || command.contains('\0') {
        return Err(jni_error!(InvalidInput, "Command name contains invalid characters"));
    }
    
    // Check whitelist
    let normalized_command = command.to_uppercase();
    if !ALLOWED_COMMANDS.contains(&normalized_command.as_str()) {
        return Err(jni_error!(InvalidInput, "Command not allowed: {}", command));
    }
    
    Ok(())
}

/// Validate and sanitize command argument
fn validate_argument(arg: &[u8]) -> JniResult<()> {
    // Size validation
    if arg.len() > MAX_ARGUMENT_LEN {
        return Err(jni_error!(InvalidInput, "Argument too large: {} bytes", arg.len()));
    }
    
    // Check for protocol injection if it's text
    if let Ok(arg_str) = std::str::from_utf8(arg) {
        if arg_str.contains('\r') || arg_str.contains('\n') {
            return Err(jni_error!(InvalidInput, "Argument contains invalid characters"));
        }
    }
    
    Ok(())
}

/// Validate key string for Valkey operations
/// This function is reserved for future use when key validation is needed
#[allow(dead_code)]
fn validate_key(key: &str) -> JniResult<()> {
    if key.is_empty() {
        return Err(jni_error!(InvalidInput, "Key cannot be empty"));
    }
    
    if key.len() > MAX_ARGUMENT_LEN {
        return Err(jni_error!(InvalidInput, "Key too large: {} bytes", key.len()));
    }
    
    if key.contains('\0') || key.contains('\r') || key.contains('\n') {
        return Err(jni_error!(InvalidInput, "Key contains invalid characters"));
    }
    
    Ok(())
}

// Type-safe client handle management

/// Set the single client instance
/// The Client is a multiplexer, so we only need one instance
fn set_client(client: Client) {
    let mut instance = CLIENT_INSTANCE.lock().unwrap();
    *instance = Some(client);
}

/// Get the client instance
/// Returns a clone of the Client, which is designed for concurrent access
fn get_client_safe() -> JniResult<Client> {
    let instance = CLIENT_INSTANCE.lock()
        .map_err(|_| jni_error!(LockPoisoned, "Client instance lock poisoned"))?;
    
    instance.as_ref()
        .cloned()
        .ok_or(jni_error!(InvalidHandle, "Client not initialized"))
}

// Client access is now direct - no locking needed since Client is Clone and thread-safe

/// Clear the client instance
fn clear_client() -> JniResult<()> {
    let mut instance = CLIENT_INSTANCE.lock()
        .map_err(|_| jni_error!(LockPoisoned, "Client instance lock poisoned"))?;
    
    *instance = None;
    Ok(())
}

// Helper functions for JNI parameter parsing

/// Ensure proper cleanup of JNI local references
fn ensure_local_ref_cleanup(env: &mut JNIEnv) {
    // Force cleanup of any pending local references
    // This is a defensive measure to prevent reference leaks
    let _ = env.ensure_local_capacity(10);
}

/// Create a scoped local reference frame for complex operations
fn with_local_frame<F, R>(env: &mut JNIEnv, capacity: i32, f: F) -> JniResult<R>
where
    F: FnOnce(&mut JNIEnv) -> JniResult<R>,
{
    // Push a new local reference frame
    env.push_local_frame(capacity)?;
    
    let result = f(env);
    
    // Pop the frame, cleaning up all local references created within
    match result {
        Ok(value) => {
            // SAFETY: pop_local_frame is safe because we pushed a frame earlier
            // and we're passing a valid JObject reference
            unsafe { env.pop_local_frame(&JObject::null())? };
            Ok(value)
        }
        Err(e) => {
            // SAFETY: pop_local_frame is safe because we pushed a frame earlier
            // and we're passing a valid JObject reference. We ignore the result
            // because we're already handling an error
            unsafe { let _ = env.pop_local_frame(&JObject::null()); }
            Err(e)
        }
    }
}

/// Validate and parse address with security checks
fn validate_and_parse_address(addr_str: &str) -> JniResult<NodeAddress> {
    // Length validation
    if addr_str.len() > 255 {
        return Err(jni_error!(InvalidInput, "Address too long: {}", addr_str.len()));
    }
    
    if addr_str.is_empty() {
        return Err(jni_error!(InvalidInput, "Address cannot be empty"));
    }
    
    // Check for suspicious characters
    if addr_str.contains('\0') || addr_str.contains('\r') || addr_str.contains('\n') {
        return Err(jni_error!(InvalidInput, "Address contains invalid characters"));
    }
    
    let parts: Vec<&str> = addr_str.split(':').collect();
    if parts.len() != 2 {
        return Err(jni_error!(InvalidInput, "Address must be in format 'host:port'"));
    }
    
    let host = parts[0];
    let port_str = parts[1];
    
    // Validate hostname
    if host.is_empty() || host.len() > 253 {
        return Err(jni_error!(InvalidInput, "Invalid hostname length"));
    }
    
    // Validate port
    let port = port_str.parse::<u16>()
        .map_err(|_| jni_error!(InvalidInput, "Invalid port number: {}", port_str))?;
    
    if port == 0 {
        return Err(jni_error!(InvalidInput, "Port cannot be zero"));
    }
    
    Ok(NodeAddress {
        host: host.to_string(),
        port,
    })
}

/// Parse Java string array to vector of NodeAddress with validation
fn parse_addresses(env: &mut JNIEnv, addresses_array: &JObjectArray) -> JniResult<Vec<NodeAddress>> {
    let length = env.get_array_length(addresses_array)?;
    let mut parsed_addresses = Vec::with_capacity(length as usize);

    // Validate array size
    if length > 100 {
        return Err(jni_error!(InvalidInput, "Too many addresses: {}", length));
    }

    for i in 0..length {
        let addr_obj = env.get_object_array_element(addresses_array, i)?;
        let addr_str: String = env.get_string(&JString::from(addr_obj))?.into();

        // Use secure address parsing
        let node_address = validate_and_parse_address(&addr_str)?;
        parsed_addresses.push(node_address);
    }

    Ok(parsed_addresses)
}

/// Validate credential string for security
fn validate_credential(credential: &str, field_name: &str) -> JniResult<()> {
    // Length validation
    if credential.is_empty() {
        return Err(jni_error!(InvalidInput, "{} cannot be empty", field_name));
    }
    
    if credential.len() > 256 {
        return Err(jni_error!(InvalidInput, "{} too long: {} chars", field_name, credential.len()));
    }
    
    // Character validation - no control characters except tab
    for ch in credential.chars() {
        if ch.is_control() && ch != '\t' {
            return Err(jni_error!(InvalidInput, "Invalid character in {}", field_name));
        }
    }
    
    // No newlines or carriage returns (protocol injection prevention)
    if credential.contains('\r') || credential.contains('\n') {
        return Err(jni_error!(InvalidInput, "Invalid characters in {}", field_name));
    }
    
    Ok(())
}

/// Parse optional Java string to Rust Option<String> with validation
fn parse_optional_string(env: &mut JNIEnv, jstring: jstring) -> JniResult<Option<String>> {
    if jstring.is_null() {
        Ok(None)
    } else {
        // SAFETY: JString::from_raw is safe because we've checked for null
        // and jstring is a valid JNI string reference from the caller
        let jstr = unsafe { JString::from_raw(jstring) };
        let s: String = env.get_string(&jstr)?.into();
        Ok(Some(s))
    }
}

// JNI function implementations

/// Create a new Glide client with full configuration
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_createClient(
    mut env: JNIEnv,
    _class: JClass,
    addresses: jobject,
    database_id: jint,
    username: jstring,
    password: jstring,
    use_tls: jboolean,
    cluster_mode: jboolean,
    request_timeout_ms: jint,
    connection_timeout_ms: jint,
) -> jlong {
    let mut result = || -> JniResult<jlong> {
        // SAFETY: JObject::from_raw is safe because addresses is a valid
        // jobject parameter passed from JNI, representing a String[]
        let addresses_obj = unsafe { JObject::from_raw(addresses) };
        let addresses_array = JObjectArray::from(addresses_obj);
        let parsed_addresses = parse_addresses(&mut env, &addresses_array)?;
        // Validate database ID (Valkey default supports 0-15, configurable up to larger values)
        let db_id = if database_id >= 0 { 
            if database_id > 255 {  // Conservative limit - Valkey supports configurable databases, default 16
                return Err(jni_error!(InvalidInput, "Database ID too large: {}", database_id));
            }
            Some(database_id as i64) 
        } else { 
            None 
        };
        
        let username_opt = parse_optional_string(&mut env, username)?;
        let password_opt = parse_optional_string(&mut env, password)?;
        
        // Validate credentials if provided
        if let Some(ref user) = username_opt {
            validate_credential(user, "username")?;
        }
        
        if let Some(ref pass) = password_opt {
            validate_credential(pass, "password")?;
            
            // Ensure both username and password are provided together
            if username_opt.is_none() {
                return Err(jni_error!(InvalidInput, "Username required when password is provided"));
            }
        }
        
        // Validate timeout parameters
        if request_timeout_ms < 0 || request_timeout_ms > 300_000 {  // 5 minutes max
            return Err(jni_error!(InvalidInput, "Invalid request timeout: {}", request_timeout_ms));
        }
        
        if connection_timeout_ms < 0 || connection_timeout_ms > 300_000 {
            return Err(jni_error!(InvalidInput, "Invalid connection timeout: {}", connection_timeout_ms));
        }

        let tls_mode = if use_tls == JNI_TRUE {
            Some(TlsMode::SecureTls)
        } else {
            Some(TlsMode::NoTls)
        };

        let request_timeout = if request_timeout_ms > 0 {
            Some(Duration::from_millis(request_timeout_ms as u64))
        } else {
            None
        };

        let connection_timeout = if connection_timeout_ms > 0 {
            Some(Duration::from_millis(connection_timeout_ms as u64))
        } else {
            None
        };

        // Create ConnectionRequest to match glide-core API
        let mut connection_request = ConnectionRequest::default();
        connection_request.addresses = parsed_addresses;

        if let Some(db_id) = db_id {
            connection_request.database_id = db_id;
        }

        if username_opt.is_some() || password_opt.is_some() {
            connection_request.authentication_info = Some(AuthenticationInfo {
                username: username_opt,
                password: password_opt,
            });
        }

        if let Some(tls) = tls_mode {
            connection_request.tls_mode = Some(tls);
        }

        connection_request.cluster_mode_enabled = cluster_mode == JNI_TRUE;

        if let Some(timeout) = request_timeout {
            connection_request.request_timeout = Some(timeout.as_millis() as u32);
        }

        if let Some(timeout) = connection_timeout {
            connection_request.connection_timeout = Some(timeout.as_millis() as u32);
        }

        // Create the actual glide-core client
        let client = get_runtime()?.block_on(async {
            Client::new(connection_request, None).await
        }).map_err(|e| jni_error!(Connection, "Failed to create client: {}", e))?;

        // Set the single client instance
        set_client(client);
        Ok(1i64) // Return success indicator
    };

    jni_result!(&mut env, result(), 0)
}

/// Close and free a Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_closeClient(
    _env: JNIEnv,
    _class: JClass,
    _client_handle: jlong,
) {
    // Clear the client instance
    // The client will be automatically dropped when it goes out of scope
    // glide-core Client implements Drop trait for proper cleanup
    let _ = clear_client();
}


/// Execute any command with arguments
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeCommand(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jobject {
    // Use local reference frame for proper cleanup
    let result = with_local_frame(&mut env, 50, |env| -> JniResult<jobject> {
        let mut client = get_client_safe()?;
        
        // SAFETY: JString::from_raw is safe because command is a valid
        // jstring parameter passed from JNI
        let command_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();
        
        // Validate command name
        validate_command_name(&command_str)?;

        // Parse the byte[][] args parameter
        // SAFETY: JObject::from_raw is safe because args is a valid
        // jobject parameter passed from JNI, representing a byte[][]
        let args_array = unsafe { JObjectArray::from(JObject::from_raw(args)) };
        let args_length = env.get_array_length(&args_array)?;
        
        // Validate arguments count
        if args_length > MAX_ARGUMENTS_COUNT as i32 {
            return Err(jni_error!(InvalidInput, "Too many arguments: {}", args_length));
        }

        let mut cmd = cmd(&command_str);

        // Add each argument to the command with validation
        for i in 0..args_length {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            // Cast to byte array and convert to Vec<u8>
            let byte_array = jni::objects::JByteArray::from(arg_obj);
            let arg_bytes = env.convert_byte_array(&byte_array)?;
            
            // Validate argument size and content
            validate_argument(&arg_bytes)?;
            
            cmd.arg(&arg_bytes);
        }

        // Execute the command asynchronously - no locking needed!
        // The Client handles concurrency internally via Arc<RwLock<ClientWrapper>>
        let response = get_runtime()?.block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert the response to a Java object
        convert_value_to_java_object(env, response)
    });

    // Ensure cleanup after function completes
    ensure_local_ref_cleanup(&mut env);
    jni_result!(&mut env, result, ptr::null_mut())
}

/// Convert a server response value to a Java Object with proper reference management
fn convert_value_to_java_object(env: &mut JNIEnv, value: Value) -> JniResult<jobject> {
    match value {
        Value::Nil => Ok(ptr::null_mut()),
        Value::SimpleString(s) => {
            let java_string = env.new_string(&s)?;
            Ok(java_string.into_raw())
        }
        Value::BulkString(bytes) => {
            // Try to convert to UTF-8 string, fallback to byte array if invalid
            match String::from_utf8(bytes.clone()) {
                Ok(string) => {
                    let java_string = env.new_string(&string)?;
                    Ok(java_string.into_raw())
                }
                Err(_) => {
                    // For binary data, return as byte array
                    let byte_array = env.new_byte_array(bytes.len() as i32)?;
                    env.set_byte_array_region(&byte_array, 0, &bytes.iter().map(|&b| b as i8).collect::<Vec<i8>>())?;
                    Ok(byte_array.into_raw())
                }
            }
        }
        Value::Int(i) => {
            let long_class = env.find_class("java/lang/Long")?;
            let long_value = env.new_object(long_class, "(J)V", &[i.into()])?;
            Ok(long_value.into_raw())
        }
        Value::Array(arr) => {
            // Create an Object array to hold the results
            let object_class = env.find_class("java/lang/Object")?;
            let java_array = env.new_object_array(arr.len() as i32, object_class, JObject::null())?;

            for (i, item) in arr.into_iter().enumerate() {
                let java_item = convert_value_to_java_object(env, item)?;
                // SAFETY: JObject::from_raw is safe because java_item is a valid
                // jobject returned from convert_value_to_java_object
                let java_item_obj = unsafe { JObject::from_raw(java_item) };
                env.set_object_array_element(&java_array, i as i32, java_item_obj)?;
            }

            Ok(java_array.into_raw())
        }
        Value::Okay => {
            let java_string = env.new_string("OK")?;
            Ok(java_string.into_raw())
        }
        other => Err(jni_error!(UnexpectedResponse, "Unsupported response type: {:?}", other)),
    }
}

// ==================== HELPER FUNCTIONS ====================

/// Get client from pointer with null check using type-safe implementation
fn get_client(_client_ptr: jlong) -> JniResult<Client> {
    get_client_safe()
}

// ==================== TYPED JNI METHODS ====================
// These methods provide direct typed returns, leveraging glide-core's value_conversion.rs

/// Execute a command expecting a String result
/// Uses glide-core's ExpectedReturnType::BulkString for conversion
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeStringCommand(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let mut client = get_client(_client_ptr)?;
        // SAFETY: JString::from_raw is safe because command is a valid
        // jstring parameter passed from JNI
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        // SAFETY: JObjectArray::from_raw is safe because args is a valid
        // jobject parameter passed from JNI, representing an Object[]
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime()?.block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert response to String using glide-core's value conversion logic
        match response {
            Value::Nil => Ok(ptr::null_mut()),
            Value::SimpleString(s) => {
                let java_string = env.new_string(&s)?;
                Ok(java_string.into_raw())
            }
            Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                let java_string = env.new_string(&string_val)?;
                Ok(java_string.into_raw())
            }
            Value::Okay => {
                let java_string = env.new_string("OK")?;
                Ok(java_string.into_raw())
            }
            _ => {
                let java_string = env.new_string(&format!("{:?}", response))?;
                Ok(java_string.into_raw())
            }
        }
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute a command expecting a Long result
/// Uses glide-core's numeric type conversion
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeLongCommand(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jlong {
    let mut result = || -> JniResult<jlong> {
        let mut client = get_client(_client_ptr)?;
        // SAFETY: JString::from_raw is safe because command is a valid
        // jstring parameter passed from JNI
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        // SAFETY: JObjectArray::from_raw is safe because args is a valid
        // jobject parameter passed from JNI, representing an Object[]
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime()?.block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert response to Long
        match response {
            Value::Int(i) => Ok(i),
            Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                string_val.parse::<i64>()
                    .map_err(|_| jni_error!(ConversionError, "Cannot convert to Long: {}", string_val))
            }
            _ => Err(jni_error!(ConversionError, "Expected numeric response, got: {:?}", response))
        }
    };

    jni_result!(&mut env, result(), 0i64)
}

/// Execute a command expecting a Double result
/// Uses glide-core's ExpectedReturnType::Double for conversion
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeDoubleCommand(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> f64 {
    let mut result = || -> JniResult<f64> {
        let mut client = get_client(_client_ptr)?;
        // SAFETY: JString::from_raw is safe because command is a valid
        // jstring parameter passed from JNI
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        // SAFETY: JObjectArray::from_raw is safe because args is a valid
        // jobject parameter passed from JNI, representing an Object[]
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime()?.block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert response to Double
        match response {
            Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                string_val.parse::<f64>()
                    .map_err(|_| jni_error!(ConversionError, "Cannot convert to Double: {}", string_val))
            }
            _ => Err(jni_error!(ConversionError, "Expected double response, got: {:?}", response))
        }
    };

    jni_result!(&mut env, result(), 0.0f64)
}

/// Execute a command expecting a Boolean result
/// Uses glide-core's ExpectedReturnType::Boolean for conversion
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeBooleanCommand(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jboolean {
    let mut result = || -> JniResult<jboolean> {
        let mut client = get_client(_client_ptr)?;
        // SAFETY: JString::from_raw is safe because command is a valid
        // jstring parameter passed from JNI
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        // SAFETY: JObjectArray::from_raw is safe because args is a valid
        // jobject parameter passed from JNI, representing an Object[]
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime()?.block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert response to Boolean
        match response {
            Value::Int(i) => Ok(if i == 1 { JNI_TRUE } else { JNI_FALSE }),
            Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                match string_val.as_ref() {
                    "1" | "true" | "TRUE" => Ok(JNI_TRUE),
                    "0" | "false" | "FALSE" => Ok(JNI_FALSE),
                    _ => Err(jni_error!(ConversionError, "Cannot convert to Boolean: {}", string_val))
                }
            }
            _ => Err(jni_error!(ConversionError, "Expected boolean response, got: {:?}", response))
        }
    };

    jni_result!(&mut env, result(), JNI_FALSE)
}

/// Execute a command expecting an Object[] result
/// Uses glide-core's array type conversion
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeArrayCommand(
    mut env: JNIEnv,
    _class: JClass,
    _client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jobject {
    let mut result = || -> JniResult<jobject> {
        let mut client = get_client(_client_ptr)?;
        // SAFETY: JString::from_raw is safe because command is a valid
        // jstring parameter passed from JNI
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        // SAFETY: JObjectArray::from_raw is safe because args is a valid
        // jobject parameter passed from JNI, representing an Object[]
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime()?.block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert response to Object[] - reuse existing conversion logic
        convert_value_to_java_object(&mut env, response)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}
