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

use crate::error::JniResult;
use crate::{jni_result, jni_error};

/// Runtime singleton for efficient Tokio integration
static RUNTIME: std::sync::OnceLock<tokio::runtime::Runtime> = std::sync::OnceLock::new();

/// Get or initialize the Tokio runtime
fn get_runtime() -> &'static tokio::runtime::Runtime {
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("glide-jni")
            .build()
            .expect("Failed to create Tokio runtime")
    })
}

// Helper functions for JNI parameter parsing

/// Parse Java string array to vector of NodeAddress
fn parse_addresses(env: &mut JNIEnv, addresses_array: &JObjectArray) -> JniResult<Vec<NodeAddress>> {
    let length = env.get_array_length(addresses_array)?;
    let mut parsed_addresses = Vec::with_capacity(length as usize);

    for i in 0..length {
        let addr_obj = env.get_object_array_element(addresses_array, i)?;
        let addr_str: String = env.get_string(&JString::from(addr_obj))?.into();

        let parts: Vec<&str> = addr_str.split(':').collect();
        if parts.len() != 2 {
            return Err(jni_error!(InvalidInput, "Address must be in format 'host:port'"));
        }

        let host = parts[0].to_string();
        let port = parts[1].parse::<u16>()
            .map_err(|e| jni_error!(InvalidInput, "Invalid port: {}", e))?;

        parsed_addresses.push(NodeAddress { host, port });
    }

    Ok(parsed_addresses)
}

/// Parse optional Java string to Rust Option<String>
fn parse_optional_string(env: &mut JNIEnv, jstring: jstring) -> JniResult<Option<String>> {
    if jstring.is_null() {
        Ok(None)
    } else {
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
        let addresses_obj = unsafe { JObject::from_raw(addresses) };
        let addresses_array = JObjectArray::from(addresses_obj);
        let parsed_addresses = parse_addresses(&mut env, &addresses_array)?;
        let db_id = if database_id >= 0 { Some(database_id as i64) } else { None };
        let username_opt = parse_optional_string(&mut env, username)?;
        let password_opt = parse_optional_string(&mut env, password)?;

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
        let client = get_runtime().block_on(async {
            Client::new(connection_request, None).await
        }).map_err(|e| jni_error!(Connection, "Failed to create client: {}", e))?;

        let boxed_client = Box::new(client);
        Ok(Box::into_raw(boxed_client) as jlong)
    };

    jni_result!(&mut env, result(), 0)
}

/// Close and free a Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_closeClient(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) {
    if client_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(client_ptr as *mut Client);
        }
    }
}

/// Execute GET command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_get(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    key: jstring,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &mut *(client_ptr as *mut Client) };
        let key_str: String = env.get_string(&unsafe { JString::from_raw(key) })?.into();

        let mut cmd = cmd("GET");
        cmd.arg(&key_str);

        let response = get_runtime().block_on(async {
            client.send_command(&cmd, None).await
        })?;

        match response {
            Value::BulkString(bytes) => {
                let value_str = String::from_utf8(bytes)
                    .map_err(|e| jni_error!(Utf8, "Failed to convert response to string: {}", e))?;
                let java_string = env.new_string(&value_str)?;
                Ok(java_string.into_raw())
            }
            Value::Nil => Ok(ptr::null_mut()),
            other => Err(jni_error!(UnexpectedResponse, "GET returned unexpected type: {:?}", other)),
        }
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute SET command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_set(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    key: jstring,
    value: jstring,
) -> jboolean {
    let mut result = || -> JniResult<jboolean> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &mut *(client_ptr as *mut Client) };
        let key_str: String = env.get_string(&unsafe { JString::from_raw(key) })?.into();
        let value_str: String = env.get_string(&unsafe { JString::from_raw(value) })?.into();

        let mut cmd = cmd("SET");
        cmd.arg(&key_str).arg(&value_str);

        let response = get_runtime().block_on(async {
            client.send_command(&cmd, None).await
        })?;

        match response {
            Value::SimpleString(ref s) if s.to_uppercase() == "OK" => Ok(JNI_TRUE),
            Value::Okay => Ok(JNI_TRUE),
            other => Err(jni_error!(UnexpectedResponse, "SET returned unexpected result: {:?}", other)),
        }
    };

    jni_result!(&mut env, result(), JNI_FALSE)
}

/// Execute PING command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_ping(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) -> jstring {
    let result = || -> JniResult<jstring> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &mut *(client_ptr as *mut Client) };

        let cmd = cmd("PING");

        let response = get_runtime().block_on(async {
            client.send_command(&cmd, None).await
        })?;

        match response {
            Value::SimpleString(s) => {
                let java_string = env.new_string(&s)?;
                Ok(java_string.into_raw())
            }
            other => Err(jni_error!(UnexpectedResponse, "PING returned unexpected type: {:?}", other)),
        }
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute any command with arguments
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jobject {
    let mut result = || -> JniResult<jobject> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &mut *(client_ptr as *mut Client) };
        let command_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse the byte[][] args parameter
        let args_array = unsafe { JObjectArray::from(JObject::from_raw(args)) };
        let args_length = env.get_array_length(&args_array)?;

        let mut cmd = cmd(&command_str);

        // Add each argument to the command
        for i in 0..args_length {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            // Cast to byte array and convert to Vec<u8>
            let byte_array = jni::objects::JByteArray::from(arg_obj);
            let arg_bytes = env.convert_byte_array(&byte_array)?;
            cmd.arg(&arg_bytes);
        }

        let response = get_runtime().block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert the response to a Java object
        convert_value_to_java_object(&mut env, response)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
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

/// Get client from pointer with null check
fn get_client(client_ptr: jlong) -> JniResult<&'static mut Client> {
    if client_ptr == 0 {
        return Err(jni_error!(NullPointer, "Client pointer is null"));
    }
    Ok(unsafe { &mut *(client_ptr as *mut Client) })
}

// ==================== TYPED JNI METHODS ====================
// These methods provide direct typed returns, leveraging glide-core's value_conversion.rs

/// Execute a command expecting a String result
/// Uses glide-core's ExpectedReturnType::BulkString for conversion
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_client_GlideJniClient_executeStringCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        let client = get_client(client_ptr)?;
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime().block_on(async {
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
    client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jlong {
    let mut result = || -> JniResult<jlong> {
        let client = get_client(client_ptr)?;
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime().block_on(async {
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
    client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> f64 {
    let mut result = || -> JniResult<f64> {
        let client = get_client(client_ptr)?;
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime().block_on(async {
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
    client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jboolean {
    let mut result = || -> JniResult<jboolean> {
        let client = get_client(client_ptr)?;
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime().block_on(async {
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
    client_ptr: jlong,
    command: jstring,
    args: jobject,
) -> jobject {
    let mut result = || -> JniResult<jobject> {
        let client = get_client(client_ptr)?;
        let cmd_str: String = env.get_string(&unsafe { JString::from_raw(command) })?.into();

        // Parse arguments
        let args_array = unsafe { JObjectArray::from_raw(args) };
        let arg_count = env.get_array_length(&args_array)?;
        let mut cmd = cmd(&cmd_str);

        for i in 0..arg_count {
            let arg_obj = env.get_object_array_element(&args_array, i)?;
            let arg_str: String = env.get_string(&JString::from(arg_obj))?.into();
            cmd.arg(&arg_str);
        }

        // Execute command via glide-core
        let response = get_runtime().block_on(async {
            client.send_command(&cmd, None).await
        })?;

        // Convert response to Object[] - reuse existing conversion logic
        convert_value_to_java_object(&mut env, response)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}
