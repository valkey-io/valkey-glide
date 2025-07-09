//! JNI client implementation for realistic performance benchmarking.
//!
//! This module provides a client that mirrors the complexity of the UDS implementation
//! while using JNI instead of sockets. The goal is fair performance comparison by
//! maintaining similar async patterns and processing overhead.

use std::sync::Arc;
use jni::JNIEnv;
use jni::objects::JByteArray;
use jni::sys::jlong;
use tokio::sync::RwLock;

use crate::error::{Result, Error};
use crate::metadata::{CommandMetadata, command_type};

// Import glide-core types
use glide_core::client::{Client as GlideClient, ConnectionRequest, NodeAddress, TlsMode};
use redis::{Cmd, RedisResult, Value};

/// JNI client for realistic performance benchmarking.
///
/// This client provides similar operational complexity to the UDS client
/// but uses direct glide-core integration instead of socket communication + protobuf.
pub struct JniClient {
    /// Host and port for debugging/logging
    host: String,
    port: u32,

    /// Actual glide-core client for Redis operations
    glide_client: Arc<RwLock<GlideClient>>,

    /// Command execution tracking for realistic overhead
    request_counter: std::sync::atomic::AtomicU32,
}

impl JniClient {
    /// Create a new JNI client with actual glide-core integration.
    pub async fn new(host: String, port: u32) -> Result<Self> {
        // Create connection request
        let mut connection_request = ConnectionRequest::default();

        // Set up address
        let address = NodeAddress {
            host: host.clone(),
            port: port as u16,
        };
        connection_request.addresses = vec![address];

        // Configure connection settings
        connection_request.tls_mode = Some(TlsMode::NoTls); // POC uses non-TLS connections
        connection_request.cluster_mode_enabled = false; // POC uses standalone mode
        connection_request.request_timeout = Some(5000); // 5 second timeout
        connection_request.database_id = 0; // Default database

        // Create the actual glide-core client
        let glide_client = GlideClient::new(connection_request, None)
            .await
            .map_err(|e| Error::Connection(format!("Failed to connect to Valkey: {:?}", e)))?;

        Ok(Self {
            host,
            port,
            glide_client: Arc::new(RwLock::new(glide_client)),
            request_counter: std::sync::atomic::AtomicU32::new(0),
        })
    }

    /// Execute a command with realistic processing overhead.
    ///
    /// This method performs actual Redis operations through glide-core while
    /// maintaining similar processing steps as the UDS implementation:
    /// 1. Command metadata validation and processing
    /// 2. Payload parsing and validation
    /// 3. Command execution through glide-core
    /// 4. Response serialization and return
    pub async fn execute_command(&self, command_type: u32, payload: &[u8]) -> Result<Vec<u8>> {
        // Increment request counter for realistic overhead
        self.request_counter.fetch_add(1, std::sync::atomic::Ordering::Relaxed);

        // Create metadata with realistic processing
        let _metadata = CommandMetadata::new(command_type, payload.len() as u32);

        // Validate command type
        match command_type {
            command_type::GET | command_type::SET | command_type::PING => {
                // Valid command types
            }
            _ => return Err(Error::InvalidArgument(format!("Unknown command type: {}", command_type))),
        }

        // Execute the command based on type with actual Redis operations
        match command_type {
            command_type::GET => self.execute_get(payload).await,
            command_type::SET => self.execute_set(payload).await,
            command_type::PING => self.execute_ping().await,
            _ => unreachable!(), // Already validated above
        }
    }

    /// Execute GET command with actual Redis operation.
    async fn execute_get(&self, payload: &[u8]) -> Result<Vec<u8>> {
        // Parse key from payload with validation
        let key = String::from_utf8(payload.to_vec())
            .map_err(|_| Error::InvalidArgument("Invalid UTF-8 in key".to_string()))?;

        if key.is_empty() {
            return Err(Error::InvalidArgument("Key cannot be empty".to_string()));
        }

        // Execute GET command through glide-core
        let mut client = self.glide_client.write().await;
        let mut cmd = Cmd::new();
        cmd.arg("GET").arg(&key);

        let result: RedisResult<Value> = client.send_command(&cmd, None).await;

        match result {
            Ok(Value::BulkString(bytes)) => Ok(bytes),
            Ok(Value::Nil) => Ok(Vec::new()), // Null response for missing key
            Ok(value) => {
                // Convert other value types to string representation
                let string_value = format!("{:?}", value);
                Ok(string_value.into_bytes())
            }
            Err(e) => Err(Error::Redis(format!("GET failed: {}", e))),
        }
    }

    /// Execute SET command with actual Redis operation.
    async fn execute_set(&self, payload: &[u8]) -> Result<Vec<u8>> {
        if payload.len() < 4 {
            return Err(Error::InvalidArgument("SET payload too short".to_string()));
        }

        // Parse key length from first 4 bytes (big-endian)
        let key_length = u32::from_be_bytes([
            payload[0], payload[1], payload[2], payload[3]
        ]) as usize;

        if payload.len() < 4 + key_length {
            return Err(Error::InvalidArgument("SET payload missing key data".to_string()));
        }

        // Extract key and value with validation
        let key_bytes = &payload[4..4 + key_length];
        let value_bytes = &payload[4 + key_length..];

        let key = String::from_utf8(key_bytes.to_vec())
            .map_err(|_| Error::InvalidArgument("Invalid UTF-8 in key".to_string()))?;
        let value = String::from_utf8(value_bytes.to_vec())
            .map_err(|_| Error::InvalidArgument("Invalid UTF-8 in value".to_string()))?;

        if key.is_empty() {
            return Err(Error::InvalidArgument("Key cannot be empty".to_string()));
        }

        // Execute SET command through glide-core
        let mut client = self.glide_client.write().await;
        let mut cmd = Cmd::new();
        cmd.arg("SET").arg(&key).arg(&value);

        let result: RedisResult<Value> = client.send_command(&cmd, None).await;

        match result {
            Ok(Value::SimpleString(response)) => Ok(response.into_bytes()),
            Ok(Value::BulkString(bytes)) => Ok(bytes),
            Ok(_) => Ok(b"OK".to_vec()), // Default success response
            Err(e) => Err(Error::Redis(format!("SET failed: {}", e))),
        }
    }

    /// Execute PING command with actual Redis operation.
    async fn execute_ping(&self) -> Result<Vec<u8>> {
        // Execute PING command through glide-core
        let mut client = self.glide_client.write().await;
        let mut cmd = Cmd::new();
        cmd.arg("PING");

        let result: RedisResult<Value> = client.send_command(&cmd, None).await;

        match result {
            Ok(Value::SimpleString(response)) => Ok(response.into_bytes()),
            Ok(Value::BulkString(bytes)) => Ok(bytes),
            Ok(_) => Ok(b"PONG".to_vec()), // Default PING response
            Err(e) => Err(Error::Redis(format!("PING failed: {}", e))),
        }
    }

    /// Check if the client is connected.
    pub fn is_connected(&self) -> bool {
        // For simplicity in POC, assume we're always connected once created
        // In production, this would check the actual connection state
        true
    }

    /// Close the client connection.
    pub async fn close(&self) {
        // glide-core client will be dropped automatically when the RwLock is dropped
        // The Drop trait on GlideClient handles cleanup
    }

    /// Get connection info for debugging.
    pub fn connection_info(&self) -> String {
        format!("{}:{}", self.host, self.port)
    }

    /// Get count of requests processed (for monitoring).
    pub fn request_count(&self) -> u32 {
        self.request_counter.load(std::sync::atomic::Ordering::Relaxed)
    }
}

// JNI interface functions

/// Create a new client with the given host and port.
pub fn create_client<'local>(
    env: &mut JNIEnv<'local>,
    host: jni::objects::JString<'local>,
    port: jni::sys::jint,
) -> Result<jlong> {
    // Get host string from Java
    let host_binding = host.into();
    let host_str = env.get_string(&host_binding)?;
    let host_str: String = host_str.into();

    // Validate port
    if port <= 0 || port > 65535 {
        return Err(Error::InvalidArgument(format!("Invalid port: {}", port)));
    }

    // Create async runtime for client creation
    let rt = tokio::runtime::Runtime::new()
        .map_err(|e| Error::Connection(format!("Failed to create async runtime: {}", e)))?;

    // Create client with actual glide-core integration
    let client = rt.block_on(async {
        JniClient::new(host_str, port as u32).await
    })?;

    // Store in Box and return pointer
    let client_ptr = Box::into_raw(Box::new((client, rt))) as jlong;
    Ok(client_ptr)
}

/// Close a client and release its resources.
pub fn close_client<'local>(
    _env: &JNIEnv<'local>,
    client_ptr: jlong,
) -> Result<()> {
    if client_ptr == 0 {
        return Ok(());
    }

    // Convert pointer back to Box for automatic cleanup
    unsafe {
        let (client, rt) = *Box::from_raw(client_ptr as *mut (JniClient, tokio::runtime::Runtime));

        // Close the client asynchronously
        rt.block_on(async {
            client.close().await;
        });

        // Both client and runtime are dropped automatically
    }

    Ok(())
}

/// Execute a command with actual Redis operations through glide-core.
/// This is the core function for POC benchmarking against UDS implementation.
pub fn execute_command<'local>(
    env: &mut JNIEnv<'local>,
    client_ptr: jlong,
    command_type: u32,
    payload: &[u8],
) -> Result<JByteArray<'local>> {
    if client_ptr == 0 {
        return Err(Error::InvalidArgument("Client pointer is null".to_string()));
    }

    // Get client and runtime references
    let (client, rt) = unsafe { &*(client_ptr as *const (JniClient, tokio::runtime::Runtime)) };

    // Execute command asynchronously through glide-core
    let response_data = rt.block_on(async {
        client.execute_command(command_type, payload).await
    })?;

    // Convert response to Java byte array
    let response_array = env.byte_array_from_slice(&response_data)?;
    Ok(response_array)
}
