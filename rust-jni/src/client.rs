// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::client::{Client, ConnectionRequest, NodeAddress, TlsMode};
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::{jboolean, jint, jobject, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::ptr;
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::error::{GlideError, GlideResult};

/// JNI-managed Glide client that wraps the glide-core client
pub struct GlideJniClient {
    client: Arc<Mutex<Client>>,
}

impl GlideJniClient {
    /// Create a new JNI client with the given connection configuration
    pub async fn new(
        addresses: Vec<NodeAddress>,
        database_id: Option<i64>,
        username: Option<String>,
        password: Option<String>,
        tls_mode: Option<TlsMode>,
        cluster_mode: bool,
        request_timeout: Option<u32>,
    ) -> GlideResult<Self> {
        let mut connection_request = ConnectionRequest::new();
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
            connection_request.request_timeout = Some(timeout);
        }

        let client = Client::new(connection_request, None)
            .await
            .map_err(|e| GlideError::ConnectionFailed(format!("{:?}", e)))?;

        Ok(Self {
            client: Arc::new(Mutex::new(client)),
        })
    }

    /// Execute a GET command
    pub async fn get(&self, key: &str) -> GlideResult<Option<String>> {
        let mut cmd = Cmd::new();
        cmd.arg("GET").arg(key);

        let mut client = self.client.lock().await;
        let result = client.send_command(&cmd, None).await
            .map_err(|e| GlideError::CommandFailed(format!("GET failed: {}", e)))?;

        match result {
            Value::Data(bytes) => {
                Ok(Some(String::from_utf8(bytes)
                    .map_err(|e| GlideError::InvalidUtf8(e.to_string()))?))
            }
            Value::Nil => Ok(None),
            _ => Err(GlideError::UnexpectedResponse(format!("GET returned unexpected type: {:?}", result))),
        }
    }

    /// Execute a SET command
    pub async fn set(&self, key: &str, value: &str) -> GlideResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SET").arg(key).arg(value);

        let mut client = self.client.lock().await;
        let result = client.send_command(&cmd, None).await
            .map_err(|e| GlideError::CommandFailed(format!("SET failed: {}", e)))?;

        match result {
            Value::SimpleString(response) if response == "OK" => Ok(()),
            _ => Err(GlideError::UnexpectedResponse(format!("SET returned unexpected result: {:?}", result))),
        }
    }

    /// Execute a DEL command
    pub async fn del(&self, key: &str) -> GlideResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("DEL").arg(key);

        let mut client = self.client.lock().await;
        let result = client.send_command(&cmd, None).await
            .map_err(|e| GlideError::CommandFailed(format!("DEL failed: {}", e)))?;

        match result {
            Value::Int(count) => Ok(count),
            _ => Err(GlideError::UnexpectedResponse(format!("DEL returned unexpected type: {:?}", result))),
        }
    }

    /// Execute a PING command
    pub async fn ping(&self) -> GlideResult<String> {
        let mut cmd = Cmd::new();
        cmd.arg("PING");

        let mut client = self.client.lock().await;
        let result = client.send_command(&cmd, None).await
            .map_err(|e| GlideError::CommandFailed(format!("PING failed: {}", e)))?;

        match result {
            Value::SimpleString(response) => Ok(response),
            _ => Err(GlideError::UnexpectedResponse(format!("PING returned unexpected type: {:?}", result))),
        }
    }
}

// JNI function implementations

/// Create a new Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_client_GlideClient_createClient(
    mut env: JNIEnv,
    _class: JClass,
    addresses: jobject,
    database_id: jint,
    username: jstring,
    password: jstring,
    use_tls: jboolean,
    cluster_mode: jboolean,
    request_timeout: jint,
) -> jobject {
    // Get Tokio runtime handle
    let rt = match glide_core::client::get_or_init_runtime() {
        Ok(rt) => rt,
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::RuntimeError(e));
            return ptr::null_mut();
        }
    };

    let result = rt.runtime.block_on(async move {
        // Parse addresses array
        let addresses_array = unsafe {
            env.get_list(&JObject::from_raw(addresses))
                .map_err(|e| GlideError::JniError(format!("Failed to get addresses list: {}", e)))?
        };

        let mut parsed_addresses = Vec::new();
        let mut iter = addresses_array.iter(&mut env)
            .map_err(|e| GlideError::JniError(format!("Failed to iterate addresses: {}", e)))?;

        while let Some(addr_obj) = iter.next(&mut env)
            .map_err(|e| GlideError::JniError(format!("Failed to get next address: {}", e)))? {

            let addr_str: String = env.get_string(&JString::from(addr_obj))
                .map_err(|e| GlideError::JniError(format!("Failed to get address string: {}", e)))?
                .into();

            let parts: Vec<&str> = addr_str.split(':').collect();
            if parts.len() != 2 {
                return Err(GlideError::InvalidInput("Address must be in format 'host:port'".to_string()));
            }

            let host = parts[0].to_string();
            let port = parts[1].parse::<u16>()
                .map_err(|e| GlideError::InvalidInput(format!("Invalid port: {}", e)))?;

            parsed_addresses.push(NodeAddress { host, port });
        }

        // Parse optional parameters
        let db_id = if database_id >= 0 { Some(database_id as i64) } else { None };

        let username_opt = if username.is_null() {
            None
        } else {
            Some(env.get_string(&unsafe { JString::from_raw(username) })
                .map_err(|e| GlideError::JniError(format!("Failed to get username: {}", e)))?
                .into())
        };

        let password_opt = if password.is_null() {
            None
        } else {
            Some(env.get_string(&unsafe { JString::from_raw(password) })
                .map_err(|e| GlideError::JniError(format!("Failed to get password: {}", e)))?
                .into())
        };

        let tls_mode = if use_tls == JNI_TRUE {
            Some(TlsMode::SecureTls)
        } else {
            Some(TlsMode::NoTls)
        };

        let timeout = if request_timeout > 0 { Some(request_timeout as u32) } else { None };

        // Create the client
        let client = GlideJniClient::new(
            parsed_addresses,
            db_id,
            username_opt,
            password_opt,
            tls_mode,
            cluster_mode == JNI_TRUE,
            timeout,
        ).await?;

        // Box the client and return as raw pointer
        let boxed_client = Box::new(client);
        Ok(Box::into_raw(boxed_client) as jobject)
    });

    match result {
        Ok(client_ptr) => client_ptr,
        Err(error) => {
            crate::error::throw_glide_exception(&mut env, &error);
            ptr::null_mut()
        }
    }
}

/// Close and free a Glide client
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_client_GlideClient_closeClient(
    _env: JNIEnv,
    _class: JClass,
    client_ptr: jobject,
) {
    if !client_ptr.is_null() {
        unsafe {
            let _ = Box::from_raw(client_ptr as *mut GlideJniClient);
        }
    }
}

/// Execute GET command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_client_GlideClient_get(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jobject,
    key: jstring,
) -> jstring {
    if client_ptr.is_null() {
        crate::error::throw_glide_exception(&mut env, &GlideError::NullPointer("Client pointer is null".to_string()));
        return ptr::null_mut();
    }

    let client = unsafe { &*(client_ptr as *const GlideJniClient) };
    let key_str: String = match env.get_string(&unsafe { JString::from_raw(key) }) {
        Ok(s) => s.into(),
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::JniError(format!("Failed to get key string: {}", e)));
            return ptr::null_mut();
        }
    };

    // Get Tokio runtime handle and execute
    let rt = match glide_core::client::get_or_init_runtime() {
        Ok(rt) => rt,
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::RuntimeError(e));
            return ptr::null_mut();
        }
    };

    let result = rt.runtime.block_on(async {
        client.get(&key_str).await
    });

    match result {
        Ok(Some(value)) => {
            match env.new_string(&value) {
                Ok(java_string) => java_string.into_raw(),
                Err(e) => {
                    crate::error::throw_glide_exception(&mut env, &GlideError::JniError(format!("Failed to create Java string: {}", e)));
                    ptr::null_mut()
                }
            }
        }
        Ok(None) => ptr::null_mut(),
        Err(error) => {
            crate::error::throw_glide_exception(&mut env, &error);
            ptr::null_mut()
        }
    }
}

/// Execute SET command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_client_GlideClient_set(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jobject,
    key: jstring,
    value: jstring,
) -> jboolean {
    if client_ptr.is_null() {
        crate::error::throw_glide_exception(&mut env, &GlideError::NullPointer("Client pointer is null".to_string()));
        return JNI_FALSE;
    }

    let client = unsafe { &*(client_ptr as *const GlideJniClient) };
    let key_str: String = match env.get_string(&unsafe { JString::from_raw(key) }) {
        Ok(s) => s.into(),
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::JniError(format!("Failed to get key string: {}", e)));
            return JNI_FALSE;
        }
    };
    let value_str: String = match env.get_string(&unsafe { JString::from_raw(value) }) {
        Ok(s) => s.into(),
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::JniError(format!("Failed to get value string: {}", e)));
            return JNI_FALSE;
        }
    };

    // Get Tokio runtime handle and execute
    let rt = match glide_core::client::get_or_init_runtime() {
        Ok(rt) => rt,
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::RuntimeError(e));
            return JNI_FALSE;
        }
    };

    let result = rt.runtime.block_on(async {
        client.set(&key_str, &value_str).await
    });

    match result {
        Ok(()) => JNI_TRUE,
        Err(error) => {
            crate::error::throw_glide_exception(&mut env, &error);
            JNI_FALSE
        }
    }
}

/// Execute DEL command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_client_GlideClient_del(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jobject,
    key: jstring,
) -> jint {
    if client_ptr.is_null() {
        crate::error::throw_glide_exception(&mut env, &GlideError::NullPointer("Client pointer is null".to_string()));
        return -1;
    }

    let client = unsafe { &*(client_ptr as *const GlideJniClient) };
    let key_str: String = match env.get_string(&unsafe { JString::from_raw(key) }) {
        Ok(s) => s.into(),
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::JniError(format!("Failed to get key string: {}", e)));
            return -1;
        }
    };

    // Get Tokio runtime handle and execute
    let rt = match glide_core::client::get_or_init_runtime() {
        Ok(rt) => rt,
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::RuntimeError(e));
            return -1;
        }
    };

    let result = rt.runtime.block_on(async {
        client.del(&key_str).await
    });

    match result {
        Ok(count) => count as jint,
        Err(error) => {
            crate::error::throw_glide_exception(&mut env, &error);
            -1
        }
    }
}

/// Execute PING command
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_client_GlideClient_ping(
    mut env: JNIEnv,
    _class: JClass,
    client_ptr: jobject,
) -> jstring {
    if client_ptr.is_null() {
        crate::error::throw_glide_exception(&mut env, &GlideError::NullPointer("Client pointer is null".to_string()));
        return ptr::null_mut();
    }

    let client = unsafe { &*(client_ptr as *const GlideJniClient) };

    // Get Tokio runtime handle and execute
    let rt = match glide_core::client::get_or_init_runtime() {
        Ok(rt) => rt,
        Err(e) => {
            crate::error::throw_glide_exception(&mut env, &GlideError::RuntimeError(e));
            return ptr::null_mut();
        }
    };

    let result = rt.runtime.block_on(async {
        client.ping().await
    });

    match result {
        Ok(response) => {
            match env.new_string(&response) {
                Ok(java_string) => java_string.into_raw(),
                Err(e) => {
                    crate::error::throw_glide_exception(&mut env, &GlideError::JniError(format!("Failed to create Java string: {}", e)));
                    ptr::null_mut()
                }
            }
        }
        Err(error) => {
            crate::error::throw_glide_exception(&mut env, &error);
            ptr::null_mut()
        }
    }
}
