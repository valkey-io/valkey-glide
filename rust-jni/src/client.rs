// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-performance JNI client implementation.
//!
//! This module provides a direct integration with glide-core, eliminating
//! Unix Domain Socket overhead and enabling zero-copy operations where possible.

use glide_core::client::{Client, ConnectionRequest, NodeAddress, TlsMode};
use jni::objects::{JClass, JObject, JString, JObjectArray};
use jni::sys::{jboolean, jint, jlong, jobject, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use redis::{cmd, Value};
use std::ptr;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Mutex;

use crate::error::{JniResult};
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

/// High-performance JNI client wrapping glide-core
pub struct GlideJniClient {
    inner: Arc<Mutex<Client>>,
}

impl GlideJniClient {
    /// Create a new client with the specified configuration
    pub async fn new(
        addresses: Vec<NodeAddress>,
        database_id: Option<i64>,
        username: Option<String>,
        password: Option<String>,
        tls_mode: Option<TlsMode>,
        cluster_mode: bool,
        request_timeout: Option<Duration>,
    ) -> JniResult<Self> {
        let mut connection_request = ConnectionRequest::default();
        connection_request.addresses = addresses;
        
        if let Some(db_id) = database_id {
            connection_request.database_id = db_id;
        }
        
        if username.is_some() || password.is_some() {
            connection_request.authentication_info = Some(glide_core::client::AuthenticationInfo {
                username,
                password,
            });
        }
        
        if let Some(tls) = tls_mode {
            connection_request.tls_mode = Some(tls);
        }
        
        connection_request.cluster_mode_enabled = cluster_mode;
        
        if let Some(timeout) = request_timeout {
            connection_request.request_timeout = Some(timeout.as_millis() as u32);
        }

        let client = Client::new(connection_request, None).await?;
        
        Ok(Self {
            inner: Arc::new(Mutex::new(client)),
        })
    }

    /// Execute GET command with zero-copy optimization
    pub async fn get(&self, key: &[u8]) -> JniResult<Option<Vec<u8>>> {
        let mut cmd = cmd("GET");
        cmd.arg(key);
        let mut client = self.inner.lock().await;
        
        let result = client.send_command(&cmd, None).await
            .map_err(|e| jni_error!(Command, "GET failed: {}", e))?;

        match result {
            Value::BulkString(bytes) => Ok(Some(bytes)),
            Value::Nil => Ok(None),
            other => Err(jni_error!(UnexpectedResponse, "GET returned unexpected type: {:?}", other)),
        }
    }

    /// Execute SET command with zero-copy optimization
    pub async fn set(&self, key: &[u8], value: &[u8]) -> JniResult<()> {
        let mut cmd = cmd("SET");
        cmd.arg(key).arg(value);
        let mut client = self.inner.lock().await;
        
        let result = client.send_command(&cmd, None).await
            .map_err(|e| jni_error!(Command, "SET failed: {}", e))?;

        match result {
            Value::SimpleString(response) if response == "OK" => Ok(()),
            other => Err(jni_error!(UnexpectedResponse, "SET returned unexpected result: {:?}", other)),
        }
    }

    /// Execute DEL command
    pub async fn del(&self, key: &[u8]) -> JniResult<i64> {
        let mut cmd = cmd("DEL");
        cmd.arg(key);
        let mut client = self.inner.lock().await;
        
        let result = client.send_command(&cmd, None).await
            .map_err(|e| jni_error!(Command, "DEL failed: {}", e))?;

        match result {
            Value::Int(count) => Ok(count),
            other => Err(jni_error!(UnexpectedResponse, "DEL returned unexpected type: {:?}", other)),
        }
    }

    /// Execute PING command
    pub async fn ping(&self) -> JniResult<String> {
        let mut cmd = cmd("PING");
        let mut client = self.inner.lock().await;
        
        let result = client.send_command(&cmd, None).await
            .map_err(|e| jni_error!(Command, "PING failed: {}", e))?;

        match result {
            Value::SimpleString(response) => Ok(response),
            other => Err(jni_error!(UnexpectedResponse, "PING returned unexpected type: {:?}", other)),
        }
    }
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

/// Convert Java string to Rust bytes
fn get_bytes_from_java(env: &mut JNIEnv, jstr: jstring) -> JniResult<Vec<u8>> {
    let jstring_obj = unsafe { JString::from_raw(jstr) };
    let java_str = env.get_string(&jstring_obj)?;
    Ok(java_str.to_bytes().to_vec())
}

// JNI function implementations

/// Create a new Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_createClient(
    mut env: JNIEnv,
    _class: JClass,
    addresses: jobject,
    database_id: jint,
    username: jstring,
    password: jstring,
    use_tls: jboolean,
    cluster_mode: jboolean,
    request_timeout_ms: jint,
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
        
        let timeout = if request_timeout_ms > 0 {
            Some(Duration::from_millis(request_timeout_ms as u64))
        } else {
            None
        };

        let client = get_runtime().block_on(async {
            GlideJniClient::new(
                parsed_addresses,
                db_id,
                username_opt,
                password_opt,
                tls_mode,
                cluster_mode == JNI_TRUE,
                timeout,
            ).await
        })?;

        let boxed_client = Box::new(client);
        Ok(Box::into_raw(boxed_client) as jlong)
    };

    jni_result!(&mut env, result(), 0)
}

/// Close and free a Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_closeClient(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) {
    if client_ptr != 0 {
        unsafe {
            let _ = Box::from_raw(client_ptr as *mut GlideJniClient);
        }
    }
}

/// Execute GET command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_get(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    key: jstring,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &*(client_ptr as *const GlideJniClient) };
        let key_bytes = get_bytes_from_java(&mut env, key)?;

        let response = get_runtime().block_on(async {
            client.get(&key_bytes).await
        })?;

        match response {
            Some(bytes) => {
                let java_string = env.new_string(String::from_utf8(bytes)?)?;
                Ok(java_string.into_raw())
            }
            None => Ok(ptr::null_mut()),
        }
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Execute SET command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_set(
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

        let client = unsafe { &*(client_ptr as *const GlideJniClient) };
        let key_bytes = get_bytes_from_java(&mut env, key)?;
        let value_bytes = get_bytes_from_java(&mut env, value)?;

        get_runtime().block_on(async {
            client.set(&key_bytes, &value_bytes).await
        })?;

        Ok(JNI_TRUE)
    };

    jni_result!(&mut env, result(), JNI_FALSE)
}

/// Execute DEL command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_del(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
    key: jstring,
) -> jint {
    let mut result = || -> JniResult<jint> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &*(client_ptr as *const GlideJniClient) };
        let key_bytes = get_bytes_from_java(&mut env, key)?;

        let count = get_runtime().block_on(async {
            client.del(&key_bytes).await
        })?;

        Ok(count as jint)
    };

    jni_result!(&mut env, result(), -1)
}

/// Execute PING command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_ping(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jlong,
) -> jstring {
    let mut result = || -> JniResult<jstring> {
        if client_ptr == 0 {
            return Err(jni_error!(NullPointer, "Client pointer is null"));
        }

        let client = unsafe { &*(client_ptr as *const GlideJniClient) };

        let response = get_runtime().block_on(async {
            client.ping().await
        })?;

        let java_string = env.new_string(&response)?;
        Ok(java_string.into_raw())
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}