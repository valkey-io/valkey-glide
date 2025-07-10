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