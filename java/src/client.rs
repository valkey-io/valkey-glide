// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-performance JNI client implementation with per-client architecture.
//!
//! This module provides JNI bindings that directly use glide-core's Client,
//! eliminating Unix Domain Socket overhead and enabling zero-copy operations.
//! Each client instance is completely isolated with its own runtime and resources.

use glide_core::client::{Client, ConnectionRequest, NodeAddress, TlsMode, AuthenticationInfo};
use redis::cluster_routing::RoutingInfo;
use jni::objects::{JClass, JObject, JString, JObjectArray, JByteArray};
use jni::sys::{jboolean, jint, jlong, jobject, jstring, JNI_TRUE};
use jni::JNIEnv;
use redis::{cmd, Value};
use std::collections::HashMap;
use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{LazyLock, Mutex};
use std::time::Duration;

use crate::error::JniResult;
use crate::runtime::JniRuntime;
use crate::async_bridge::AsyncBridge;
use crate::{jni_result, jni_error};

// JNI-specific validation limits (to prevent JNI-level issues)
const MAX_ADDRESSES_COUNT: usize = 10000; // Generous limit for JNI array handling

/// Per-client instance containing all client-specific state
#[derive(Clone)]
pub struct JniClient {
    /// The glide-core client instance
    core_client: Client,
    /// Async bridge for handling sync/async boundary
    async_bridge: AsyncBridge,
    /// Default timeout for operations
    default_timeout: Duration,
}

impl JniClient {
    /// Create a new JniClient instance
    pub fn new(
        core_client: Client,
        default_timeout: Duration,
        client_id: u64,
    ) -> JniResult<Self> {
        let runtime = JniRuntime::new(&format!("glide-jni-{}", client_id))?;
        let async_bridge = AsyncBridge::new(runtime);
        
        Ok(Self {
            core_client,
            async_bridge,
            default_timeout,
        })
    }

    /// Execute a command asynchronously
    pub fn execute_command(&self, cmd: redis::Cmd) -> JniResult<Value> {
        self.async_bridge.execute_command_async(
            self.core_client.clone(),
            cmd,
            self.default_timeout,
        )
    }

    /// Execute a command with routing
    pub fn execute_command_with_routing(
        &self,
        cmd: redis::Cmd,
        routing: Option<RoutingInfo>,
    ) -> JniResult<Value> {
        self.async_bridge.execute_command_with_routing_async(
            self.core_client.clone(),
            cmd,
            routing,
            self.default_timeout,
        )
    }

    /// Get the number of pending callbacks
    pub fn pending_callbacks(&self) -> JniResult<usize> {
        self.async_bridge.pending_callbacks()
    }

    /// Clean up expired callbacks
    pub fn cleanup_expired_callbacks(&self) -> JniResult<usize> {
        self.async_bridge.cleanup_expired_callbacks()
    }

    /// Shutdown the client
    pub fn shutdown(&self) {
        self.async_bridge.shutdown();
    }
}

/// Client registry for managing multiple client instances
static CLIENT_REGISTRY: LazyLock<Mutex<HashMap<u64, JniClient>>> = 
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// Atomic counter for generating unique client handles
static NEXT_CLIENT_HANDLE: AtomicU64 = AtomicU64::new(1);

/// Generate a unique client handle
fn generate_client_handle() -> u64 {
    NEXT_CLIENT_HANDLE.fetch_add(1, Ordering::SeqCst)
}

/// Get a client from the registry
fn get_client(handle: u64) -> JniResult<JniClient> {
    let registry = CLIENT_REGISTRY.lock()
        .map_err(|_| jni_error!(LockPoisoned, "Client registry lock poisoned"))?;
    
    registry.get(&handle)
        .cloned()
        .ok_or_else(|| jni_error!(InvalidHandle, "Client handle {} not found", handle))
}

/// Insert a client into the registry
fn insert_client(handle: u64, client: JniClient) -> JniResult<()> {
    let mut registry = CLIENT_REGISTRY.lock()
        .map_err(|_| jni_error!(LockPoisoned, "Client registry lock poisoned"))?;
    
    registry.insert(handle, client);
    Ok(())
}

/// Remove a client from the registry
fn remove_client(handle: u64) -> JniResult<Option<JniClient>> {
    let mut registry = CLIENT_REGISTRY.lock()
        .map_err(|_| jni_error!(LockPoisoned, "Client registry lock poisoned"))?;
    
    Ok(registry.remove(&handle))
}

// Command validation functions

// Removed unused validation functions that duplicate glide-core validation

/// Validate and parse address for JNI-specific issues (null bytes, basic format)
fn validate_and_parse_address(addr_str: &str) -> JniResult<NodeAddress> {
    if addr_str.is_empty() {
        return Err(jni_error!(InvalidInput, "Address cannot be empty"));
    }
    
    if addr_str.contains('\0') {
        return Err(jni_error!(InvalidInput, "Address contains null bytes"));
    }
    
    let parts: Vec<&str> = addr_str.split(':').collect();
    if parts.len() != 2 {
        return Err(jni_error!(InvalidInput, "Address must be in format 'host:port'"));
    }
    
    let host = parts[0];
    let port_str = parts[1];
    
    if host.is_empty() {
        return Err(jni_error!(InvalidInput, "Host cannot be empty"));
    }
    
    let port = port_str.parse::<u16>()
        .map_err(|_| jni_error!(InvalidInput, "Invalid port number: {}", port_str))?;
    
    Ok(NodeAddress {
        host: host.to_string(),
        port,
    })
}

/// Parse Java string array to vector of NodeAddress with basic validation
fn parse_addresses(env: &mut JNIEnv, addresses_array: &JObjectArray) -> JniResult<Vec<NodeAddress>> {
    let length = env.get_array_length(addresses_array)?;
    let mut parsed_addresses = Vec::with_capacity(length as usize);

    // Basic sanity check for JNI array size
    if length > MAX_ADDRESSES_COUNT as i32 {
        return Err(jni_error!(InvalidInput, "Too many addresses: {}", length));
    }

    for i in 0..length {
        let addr_obj = env.get_object_array_element(addresses_array, i)?;
        let addr_str: String = env.get_string(&JString::from(addr_obj))?.into();
        let node_address = validate_and_parse_address(&addr_str)?;
        parsed_addresses.push(node_address);
    }

    Ok(parsed_addresses)
}

/// Validate credential string for JNI-specific issues (null bytes only)
fn validate_credential(credential: &str, field_name: &str) -> JniResult<()> {
    if credential.contains('\0') {
        return Err(jni_error!(InvalidInput, "{} contains null bytes", field_name));
    }
    
    Ok(())
}

/// Parse optional Java string to Rust Option<String> with validation
fn parse_optional_string(env: &mut JNIEnv, jstring: jstring) -> JniResult<Option<String>> {
    if jstring.is_null() {
        Ok(None)
    } else {
        let jstr = unsafe { JString::from_raw(jstring) };
        let s: String = env.get_string(&jstr)?.into();
        Ok(Some(s))
    }
}

/// Ensure proper cleanup of JNI local references
fn ensure_local_ref_cleanup(env: &mut JNIEnv) {
    let _ = env.ensure_local_capacity(10);
}

/// Create a scoped local reference frame for complex operations
fn with_local_frame<F, R>(env: &mut JNIEnv, capacity: i32, f: F) -> JniResult<R>
where
    F: FnOnce(&mut JNIEnv) -> JniResult<R>,
{
    env.push_local_frame(capacity)?;
    
    let result = f(env);
    
    match result {
        Ok(value) => {
            unsafe { env.pop_local_frame(&JObject::null())? };
            Ok(value)
        }
        Err(e) => {
            unsafe { let _ = env.pop_local_frame(&JObject::null()); }
            Err(e)
        }
    }
}

// JNI function implementations

/// Create a new Glide client with full configuration
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_createClient(
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
        let addresses_obj = unsafe { JObject::from_raw(addresses) };
        let addresses_array = JObjectArray::from(addresses_obj);
        let parsed_addresses = parse_addresses(&mut env, &addresses_array)?;
        
        let db_id = if database_id >= 0 { 
            Some(database_id as i64) 
        } else { 
            None 
        };
        
        let username_opt = parse_optional_string(&mut env, username)?;
        let password_opt = parse_optional_string(&mut env, password)?;
        
        if let Some(ref user) = username_opt {
            validate_credential(user, "username")?;
        }
        
        if let Some(ref pass) = password_opt {
            validate_credential(pass, "password")?;
        }
        
        // Convert timeout values (let glide-core handle validation)

        let tls_mode = if use_tls == JNI_TRUE {
            Some(TlsMode::SecureTls)
        } else {
            Some(TlsMode::NoTls)
        };

        let request_timeout = if request_timeout_ms > 0 {
            Duration::from_millis(request_timeout_ms as u64)
        } else {
            Duration::from_millis(5000) // Default 5 seconds
        };

        let connection_timeout = if connection_timeout_ms > 0 {
            Some(Duration::from_millis(connection_timeout_ms as u64))
        } else {
            None
        };

        // Create ConnectionRequest
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

        if let Some(timeout) = connection_timeout {
            connection_request.connection_timeout = Some(timeout.as_millis() as u32);
        }

        connection_request.request_timeout = Some(request_timeout.as_millis() as u32);

        // Generate unique client handle
        let client_handle = generate_client_handle();

        // Create the permanent runtime first
        let runtime = JniRuntime::new(&format!("glide-jni-{}", client_handle))?;
        
        // Create the async bridge first 
        let async_bridge = AsyncBridge::new(runtime);
        
        // Create the glide-core client using the async bridge's runtime
        let core_client = async_bridge.runtime().block_on(async {
            Client::new(connection_request, None).await
        })?.map_err(|e| jni_error!(Connection, "Failed to create client: {}", e))?;

        // Create the JniClient instance
        let jni_client = JniClient {
            core_client,
            async_bridge,
            default_timeout: request_timeout,
        };

        // Insert into registry
        insert_client(client_handle, jni_client)?;

        Ok(client_handle as jlong)
    };

    jni_result!(&mut env, result(), 0)
}

/// Close and free a Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_closeClient(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
) {
    let result = || -> JniResult<()> {
        let handle = client_handle as u64;
        
        if let Some(client) = remove_client(handle)? {
            client.shutdown();
        }
        
        Ok(())
    };

    jni_result!(&mut env, result(), ())
}

/// Execute any command with arguments
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_executeCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    command: jstring,
    args: jobject,
) -> jobject {
    let result = with_local_frame(&mut env, 50, |env| -> JniResult<jobject> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        let command_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();
        
        // Only basic JNI safety checks
        if command_str.contains('\0') {
            return Err(jni_error!(InvalidInput, "Command contains null bytes"));
        }

        let args_array = unsafe { JObjectArray::from(JObject::from_raw(args)) };
        let args_length = env.get_array_length(&args_array)?;
        
        let mut cmd = cmd(&command_str);

        for i in 0..args_length {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let byte_array = JByteArray::from(arg_obj);
            let arg_bytes = env.convert_byte_array(&byte_array)?;
            
            cmd.arg(&arg_bytes);
        }

        let response = client.execute_command(cmd)?;
        convert_value_to_java_object(env, response)
    });

    ensure_local_ref_cleanup(&mut env);
    jni_result!(&mut env, result, ptr::null_mut())
}

/// Convert a server response value to a Java Object
fn convert_value_to_java_object(env: &mut JNIEnv, value: Value) -> JniResult<jobject> {
    match value {
        Value::Nil => Ok(ptr::null_mut()),
        Value::SimpleString(s) => {
            let java_string = env.new_string(&s)?;
            Ok(java_string.into_raw())
        }
        Value::BulkString(bytes) => {
            match String::from_utf8(bytes.clone()) {
                Ok(string) => {
                    let java_string = env.new_string(&string)?;
                    Ok(java_string.into_raw())
                }
                Err(_) => {
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
            let object_class = env.find_class("java/lang/Object")?;
            let java_array = env.new_object_array(arr.len() as i32, object_class, JObject::null())?;

            for (i, item) in arr.into_iter().enumerate() {
                let java_item = convert_value_to_java_object(env, item)?;
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

/// Execute a command expecting a String result
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_executeStringCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    command: jstring,
    args: jobject,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();
        
        // Only basic JNI safety checks
        if cmd_str.contains('\0') {
            return Err(jni_error!(InvalidInput, "Command contains null bytes"));
        }

        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        let response = client.execute_command(cmd)?;

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
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_executeLongCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    command: jstring,
    args: jobject,
) -> jlong {
    let mut result = || -> JniResult<jlong> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();
        
        // Only basic JNI safety checks
        if cmd_str.contains('\0') {
            return Err(jni_error!(InvalidInput, "Command contains null bytes"));
        }

        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        let response = client.execute_command(cmd)?;

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

/// Get client statistics
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_getClientStats(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
) -> jstring {
    let result = || -> JniResult<jstring> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        let pending_callbacks = client.pending_callbacks()?;
        let stats = format!("{{\"pending_callbacks\": {}, \"client_handle\": {}}}", pending_callbacks, handle);
        
        let java_string = env.new_string(&stats)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Clean up expired callbacks for a client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_cleanupExpiredCallbacks(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
) -> jint {
    let result = || -> JniResult<jint> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        let cleaned_count = client.cleanup_expired_callbacks()?;
        Ok(cleaned_count as jint)
    };

    jni_result!(&mut env, result(), 0)
}

// ============================================================================
// Script Management Functions
// ============================================================================

/// Store a script in the native script container and return its SHA1 hash
#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_storeScript(
    mut env: JNIEnv,
    _class: JClass,
    code: JByteArray,
) -> jstring {
    let result = || -> JniResult<jstring> {
        // Convert Java byte array to Rust bytes
        let code_bytes = env.convert_byte_array(&code)?;
        
        // Store script using glide_core scripts container
        let hash = glide_core::scripts_container::add_script(&code_bytes);
        
        // Return SHA1 hash as JString
        let java_string = env.new_string(&hash)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Remove a script from the native script container by its SHA1 hash
#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ScriptResolver_dropScript(
    mut env: JNIEnv,
    _class: JClass,
    hash: JString,
) {
    let mut result = || -> JniResult<()> {
        // Convert JString hash to Rust String
        let hash_str: String = env.get_string(&hash)?.into();
        
        // Remove script from glide_core scripts container
        glide_core::scripts_container::remove_script(&hash_str);
        
        Ok(())
    };

    if let Err(e) = result() {
        eprintln!("Error in dropScript: {:?}", e);
    }
}

/// Execute SCRIPT SHOW command to retrieve script source by SHA1 hash
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_scriptShow(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    hash: JString,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Convert JString hash to Rust String
        let hash_str: String = env.get_string(&hash)?.into();
        
        // Execute SCRIPT SHOW command
        let mut cmd = redis::cmd("SCRIPT");
        cmd.arg("SHOW").arg(&hash_str);
        let response = client.execute_command(cmd)?;
        
        // Convert response to String
        let script_source = match response {
            redis::Value::BulkString(bytes) => {
                String::from_utf8(bytes)
                    .map_err(|_| jni_error!(ConversionError, "Failed to convert script to UTF-8"))?
            },
            redis::Value::Nil => {
                return Err(jni_error!(ConversionError, "Script not found"));
            },
            _ => {
                return Err(jni_error!(ConversionError, "Unexpected response type from SCRIPT SHOW"));
            }
        };
        
        // Return script source as JString
        let java_string = env.new_string(&script_source)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

// ============================================================================
// Function Commands
// ============================================================================

/// Execute Valkey FCALL command to call a function
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_fcall(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    function_name: JString,
    keys: JObjectArray,
    args: JObjectArray,
) -> jobject {
    let mut result = || -> JniResult<jobject> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Convert function name
        let function_str: String = env.get_string(&function_name)?.into();
        
        // Convert keys array
        let keys_vec = if keys.is_null() {
            Vec::new()
        } else {
            let keys_len = env.get_array_length(&keys)?;
            let mut keys_vec = Vec::with_capacity(keys_len as usize);
            for i in 0..keys_len {
                let key_obj = env.get_object_array_element(&keys, i)?;
                let key_jstring = JString::from(key_obj);
                let key_str = env.get_string(&key_jstring)?;
                keys_vec.push(String::from(key_str));
            }
            keys_vec
        };
        
        // Convert args array
        let args_vec = if args.is_null() {
            Vec::new()
        } else {
            let args_len = env.get_array_length(&args)?;
            let mut args_vec = Vec::with_capacity(args_len as usize);
            for i in 0..args_len {
                let arg_obj = env.get_object_array_element(&args, i)?;
                let arg_jstring = JString::from(arg_obj);
                let arg_str = env.get_string(&arg_jstring)?;
                args_vec.push(String::from(arg_str));
            }
            args_vec
        };
        
        // Build Valkey FCALL command
        let mut cmd = redis::cmd("FCALL");
        cmd.arg(&function_str);
        cmd.arg(keys_vec.len());
        for key in &keys_vec {
            cmd.arg(key);
        }
        for arg in &args_vec {
            cmd.arg(arg);
        }
        
        // Execute command
        let response = client.execute_command(cmd)?;
        
        // Convert response to Java object (simplified - could be improved based on expected return types)
        let java_result = match response {
            redis::Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                let java_string = env.new_string(&string_val)?;
                java_string.into_raw()
            },
            redis::Value::Okay => {
                let java_string = env.new_string("OK")?;
                java_string.into_raw()
            },
            redis::Value::Nil => ptr::null_mut(),
            redis::Value::Int(num) => {
                let java_long = env.new_object("java/lang/Long", "(J)V", &[num.into()])?;
                java_long.into_raw()
            },
            _ => {
                let java_string = env.new_string(&format!("{:?}", response))?;
                java_string.into_raw()
            }
        };
        
        Ok(java_result)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute FUNCTION LIST command to list functions
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_functionList(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    library_name: JString,
) -> jobject {
    let mut result = || -> JniResult<jobject> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Build FUNCTION LIST command
        let mut cmd = redis::cmd("FUNCTION");
        cmd.arg("LIST");
        
        // Add library name filter if provided
        if !library_name.is_null() {
            let lib_str: String = env.get_string(&library_name)?.into();
            cmd.arg("LIBRARYNAME").arg(&lib_str);
        }
        
        // Execute command
        let response = client.execute_command(cmd)?;
        
        // Convert response to Java object (array of function info)
        let java_result = match response {
            redis::Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                let java_string = env.new_string(&string_val)?;
                java_string.into_raw()
            },
            redis::Value::Nil => ptr::null_mut(),
            _ => {
                let java_string = env.new_string(&format!("{:?}", response))?;
                java_string.into_raw()
            }
        };
        
        Ok(java_result)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute FUNCTION LOAD command to load a function library
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_functionLoad(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    library_code: JString,
    replace: jboolean,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Convert library code
        let code_str: String = env.get_string(&library_code)?.into();
        
        // Build FUNCTION LOAD command
        let mut cmd = redis::cmd("FUNCTION");
        cmd.arg("LOAD");
        
        if replace == JNI_TRUE {
            cmd.arg("REPLACE");
        }
        
        cmd.arg(&code_str);
        
        // Execute command
        let response = client.execute_command(cmd)?;
        
        // Convert response to String (library name)
        let library_name = match response {
            redis::Value::BulkString(bytes) => {
                String::from_utf8_lossy(&bytes).to_string()
            },
            redis::Value::Okay => "OK".to_string(),
            _ => format!("{:?}", response),
        };
        
        // Return library name as JString
        let java_string = env.new_string(&library_name)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute FUNCTION DELETE command to delete a function library
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_functionDelete(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    library_name: JString,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Convert library name
        let lib_str: String = env.get_string(&library_name)?.into();
        
        // Build FUNCTION DELETE command
        let mut cmd = redis::cmd("FUNCTION");
        cmd.arg("DELETE").arg(&lib_str);
        
        // Execute command
        let response = client.execute_command(cmd)?;
        
        // Convert response to String
        let result_str = match response {
            redis::Value::Okay => "OK".to_string(),
            redis::Value::BulkString(bytes) => {
                String::from_utf8_lossy(&bytes).to_string()
            },
            _ => format!("{:?}", response),
        };
        
        // Return result as JString
        let java_string = env.new_string(&result_str)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute FUNCTION FLUSH command to flush all functions
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_functionFlush(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    flush_mode: JString,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Build FUNCTION FLUSH command
        let mut cmd = redis::cmd("FUNCTION");
        cmd.arg("FLUSH");
        
        // Add flush mode if provided (ASYNC or SYNC)
        if !flush_mode.is_null() {
            let mode_str: String = env.get_string(&flush_mode)?.into();
            cmd.arg(&mode_str);
        }
        
        // Execute command
        let response = client.execute_command(cmd)?;
        
        // Convert response to String
        let result_str = match response {
            redis::Value::Okay => "OK".to_string(),
            redis::Value::BulkString(bytes) => {
                String::from_utf8_lossy(&bytes).to_string()
            },
            _ => format!("{:?}", response),
        };
        
        // Return result as JString
        let java_string = env.new_string(&result_str)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute FUNCTION STATS command to get function statistics
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_functionStats(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
) -> jobject {
    let result = || -> JniResult<jobject> {
        let handle = client_handle as u64;
        let client = get_client(handle)?;
        
        // Build FUNCTION STATS command
        let mut cmd = redis::cmd("FUNCTION");
        cmd.arg("STATS");
        
        // Execute command
        let response = client.execute_command(cmd)?;
        
        // Convert response to Java object
        let java_result = match response {
            redis::Value::BulkString(bytes) => {
                let string_val = String::from_utf8_lossy(&bytes);
                let java_string = env.new_string(&string_val)?;
                java_string.into_raw()
            },
            redis::Value::Nil => ptr::null_mut(),
            _ => {
                let java_string = env.new_string(&format!("{:?}", response))?;
                java_string.into_raw()
            }
        };
        
        Ok(java_result)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

// ============================================================================
// Cluster Scan Cursor Management
// ============================================================================

/// Release a native cluster scan cursor by its ID
#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_releaseNativeCursor(
    mut env: JNIEnv,
    _class: JClass,
    cursor: JString,
) {
    let mut result = || -> JniResult<()> {
        // Convert JString cursor to Rust String
        let cursor_str: String = env.get_string(&cursor)?.into();
        
        // Remove cursor from glide_core cluster scan container
        glide_core::cluster_scan_container::remove_scan_state_cursor(cursor_str);
        
        Ok(())
    };

    if let Err(e) = result() {
        eprintln!("Error in releaseNativeCursor: {:?}", e);
    }
}

/// Get the finished cursor handle constant
#[no_mangle]
pub extern "system" fn Java_glide_ffi_resolvers_ClusterScanCursorResolver_getFinishedCursorHandleConstant(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let result = || -> JniResult<jstring> {
        // Return the finished scan cursor constant from glide_core
        let finished_cursor = glide_core::client::FINISHED_SCAN_CURSOR;
        let java_string = env.new_string(finished_cursor)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}
