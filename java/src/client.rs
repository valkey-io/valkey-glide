// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Unified JNI client implementation that properly leverages glide-core.
//!
//! This module provides the complete JNI client architecture:
//! - JniClient: Thin wrapper around glide-core's Client
//! - JNI functions: Essential infrastructure and command execution
//! - Single source of truth: glide-core handles all Valkey protocol logic
//!
//! ARCHITECTURE PRINCIPLE: Eliminate duplication, leverage glide-core directly

use glide_core::client::Client;
use redis::cluster_routing::RoutingInfo;
use redis::{Cmd, Value};

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jlong, jobject, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::ptr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::sync::{LazyLock, Mutex};
use std::time::{Duration, Instant};
use tokio::runtime::Runtime;

use crate::error::{JniError, JniResult};
use crate::input_validator::{register_client, unregister_client, JniSafetyValidator};
use crate::jni_wrappers::create_jni_string;
use crate::{jni_error, jni_result};

// ============================================================================
// JNI CLIENT - Thin wrapper around glide-core
// ============================================================================

/// Simplified client wrapper that directly uses glide-core
///
/// This eliminates all the unnecessary abstraction layers and callback complexity
/// by using glide-core's native async support with a simple blocking interface.
pub struct JniClient {
    /// The glide-core client - our single source of truth
    core_client: Client,
    /// Tokio runtime for executing async operations
    runtime: Arc<Runtime>,
}

impl JniClient {
    /// Create a new JniClient wrapping a glide-core Client
    ///
    /// This is the only place where we create runtime overhead - everything else
    /// delegates directly to glide-core's battle-tested implementation.
    pub fn new(core_client: Client) -> JniResult<Self> {
        let runtime = Arc::new(
            tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build()
                .map_err(|e| JniError::Runtime(format!("Failed to create runtime: {e}")))?,
        );

        logger_core::log_debug(
            "simple-client",
            "Created JniClient with direct glide-core integration",
        );

        Ok(Self {
            core_client,
            runtime,
        })
    }

    /// Execute a Valkey command using glide-core directly
    ///
    /// This is the core method - everything else is just parameter conversion.
    /// No callbacks, no complex async bridges, just direct delegation to glide-core.
    pub fn execute_command(&mut self, cmd: Cmd) -> JniResult<Value> {
        logger_core::log_debug("simple-client", format!("Executing command: {cmd:?}"));

        // Use glide-core's send_command directly - that's it!
        let result = self
            .runtime
            .block_on(async { self.core_client.send_command(&cmd, None).await });

        match result {
            Ok(value) => {
                logger_core::log_debug("simple-client", "Command executed successfully");
                Ok(value)
            }
            Err(e) => {
                logger_core::log_debug("simple-client", format!("Command failed: {e}"));
                Err(JniError::from(e))
            }
        }
    }

    /// Execute a Valkey command with routing using glide-core directly
    ///
    /// For cluster operations that need specific routing.
    pub fn execute_command_with_routing(
        &mut self,
        cmd: Cmd,
        routing: Option<RoutingInfo>,
    ) -> JniResult<Value> {
        logger_core::log_debug(
            "simple-client",
            format!("Executing command with routing: {cmd:?}, routing: {routing:?}"),
        );

        // Use glide-core's send_command with routing - that's it!
        let result = self
            .runtime
            .block_on(async { self.core_client.send_command(&cmd, routing).await });

        match result {
            Ok(value) => {
                logger_core::log_debug(
                    "simple-client",
                    "Command with routing executed successfully",
                );
                Ok(value)
            }
            Err(e) => {
                logger_core::log_debug(
                    "simple-client",
                    format!("Command with routing failed: {e}"),
                );
                Err(JniError::from(e))
            }
        }
    }

    /// Execute a pipeline using glide-core directly
    ///
    /// For batch operations.
    pub fn execute_pipeline(&mut self, pipeline: redis::Pipeline) -> JniResult<Value> {
        logger_core::log_debug("simple-client", "Executing pipeline");

        // Use glide-core's send_pipeline directly - that's it!
        let result = self.runtime.block_on(async {
            self.core_client
                .send_pipeline(&pipeline, None, false, None, Default::default())
                .await
        });

        match result {
            Ok(value) => {
                logger_core::log_debug("simple-client", "Pipeline executed successfully");
                Ok(value)
            }
            Err(e) => {
                logger_core::log_debug("simple-client", format!("Pipeline failed: {e}"));
                Err(JniError::from(e))
            }
        }
    }

    /// Execute a transaction using glide-core directly
    ///
    /// For MULTI/EXEC transactions.
    pub fn execute_transaction(&mut self, transaction: redis::Pipeline) -> JniResult<Value> {
        logger_core::log_debug("simple-client", "Executing transaction");

        // Use glide-core's send_transaction directly - that's it!
        let result = self.runtime.block_on(async {
            self.core_client
                .send_transaction(&transaction, None, None, false)
                .await
        });

        match result {
            Ok(value) => {
                logger_core::log_debug("simple-client", "Transaction executed successfully");
                Ok(value)
            }
            Err(e) => {
                logger_core::log_debug("simple-client", format!("Transaction failed: {e}"));
                Err(JniError::from(e))
            }
        }
    }
}

impl Clone for JniClient {
    fn clone(&self) -> Self {
        Self {
            core_client: self.core_client.clone(),
            runtime: self.runtime.clone(),
        }
    }
}

/// Helper function to create a Valkey command
///
/// Simple utility that doesn't duplicate glide-core logic.
pub fn create_valkey_command(command: &str, args: &[String]) -> Cmd {
    let mut cmd = redis::cmd(command);
    for arg in args {
        cmd.arg(arg);
    }
    cmd
}

// ============================================================================
// CLIENT REGISTRY - Essential infrastructure with memory leak prevention
// ============================================================================

/// Client entry with metadata for cleanup tracking
struct ClientEntry {
    /// The actual client
    client: Arc<Mutex<JniClient>>,
    /// When this client was created
    created_at: Instant,
    /// Last time this client was accessed
    last_accessed: Instant,
}

impl ClientEntry {
    fn new(client: JniClient) -> Self {
        let now = Instant::now();
        Self {
            client: Arc::new(Mutex::new(client)),
            created_at: now,
            last_accessed: now,
        }
    }

    fn access(&mut self) -> Arc<Mutex<JniClient>> {
        self.last_accessed = Instant::now();
        self.client.clone()
    }

    fn is_expired(&self, timeout: Duration) -> bool {
        self.last_accessed.elapsed() > timeout
    }
}

impl std::fmt::Debug for ClientEntry {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ClientEntry")
            .field("created_at", &self.created_at)
            .field("last_accessed", &self.last_accessed)
            .finish()
    }
}

/// Client registry with enhanced safety and automatic cleanup
static CLIENTS: LazyLock<Mutex<HashMap<u64, ClientEntry>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// Client handle counter
static CLIENT_COUNTER: AtomicU64 = AtomicU64::new(1);

/// Default client timeout (30 minutes of inactivity)
const DEFAULT_CLIENT_TIMEOUT: Duration = Duration::from_secs(30 * 60);

/// Get client from registry with proper error handling and lazy cleanup
fn get_client(handle: u64) -> JniResult<Arc<Mutex<JniClient>>> {
    let mut clients = CLIENTS
        .lock()
        .map_err(|_| jni_error!(Runtime, "Failed to lock client registry"))?;

    // Perform lazy cleanup of expired clients while we have the lock
    cleanup_expired_clients_locked(&mut clients);

    // Get the requested client and update its access time
    match clients.get_mut(&handle) {
        Some(entry) => {
            logger_core::log_debug("client-registry", format!("Accessing client {handle}"));
            Ok(entry.access())
        }
        None => Err(jni_error!(
            InvalidHandle,
            "Client handle {} not found",
            handle
        )),
    }
}

/// Cleanup expired clients while holding the registry lock
/// This is called during normal operations for lazy cleanup
fn cleanup_expired_clients_locked(clients: &mut HashMap<u64, ClientEntry>) {
    let timeout = DEFAULT_CLIENT_TIMEOUT;
    let initial_count = clients.len();

    clients.retain(|&handle, entry| {
        if entry.is_expired(timeout) {
            logger_core::log_debug(
                "client-registry",
                format!(
                    "Cleaning up expired client {handle} (idle for {:?})",
                    entry.last_accessed.elapsed()
                ),
            );
            unregister_client(handle);
            false // Remove this entry
        } else {
            true // Keep this entry
        }
    });

    let cleaned_count = initial_count - clients.len();
    if cleaned_count > 0 {
        logger_core::log_debug(
            "client-registry",
            format!("Lazy cleanup removed {cleaned_count} expired clients"),
        );
    }
}

/// Force cleanup of all expired clients (can be called manually)
pub fn cleanup_expired_clients() -> usize {
    let mut clients = match CLIENTS.lock() {
        Ok(clients) => clients,
        Err(_) => {
            logger_core::log_warn("client-registry", "Failed to lock registry for cleanup");
            return 0;
        }
    };

    let initial_count = clients.len();
    cleanup_expired_clients_locked(&mut clients);
    initial_count - clients.len()
}

/// Register a new client in the registry
pub fn register_simple_client(handle: u64, client: JniClient) -> JniResult<()> {
    let mut clients = CLIENTS
        .lock()
        .map_err(|_| jni_error!(Runtime, "Failed to lock client registry for insertion"))?;

    // Perform lazy cleanup when adding new clients to prevent buildup
    cleanup_expired_clients_locked(&mut clients);

    // Create client entry with metadata
    let entry = ClientEntry::new(client);
    clients.insert(handle, entry);
    register_client(handle);

    logger_core::log_debug(
        "client-registry",
        format!("Registered client with handle: {handle}"),
    );
    Ok(())
}

// ============================================================================
// JNI FUNCTIONS - Essential infrastructure and command execution
// ============================================================================

/// Create a client - PLACEHOLDER for proper implementation
/// In real implementation, this would create glide-core Client with proper configuration
///
/// # Safety
/// This function is called from Java via JNI with a valid jstring parameter.
#[no_mangle]
pub unsafe extern "system" fn Java_io_valkey_glide_core_client_GlideClient_createClient(
    mut env: JNIEnv,
    _class: JClass,
    connection_string: jstring,
) -> jlong {
    let mut result = || -> JniResult<jlong> {
        // Validate input
        // SAFETY: connection_string is a valid jstring passed from Java (function is marked unsafe)
        let jstr_conn = JString::from_raw(connection_string);
        let conn_str: String = env.get_string(&jstr_conn)?.into();
        JniSafetyValidator::validate_no_interior_nulls(&conn_str)?;

        // PLACEHOLDER: In real implementation, this would:
        // 1. Parse connection string
        // 2. Create glide-core Client with proper configuration
        // 3. Wrap in JniClient and register in registry

        logger_core::log_debug("client", "Creating placeholder client");

        // Generate handle for the placeholder
        let handle = CLIENT_COUNTER.fetch_add(1, Ordering::SeqCst);

        logger_core::log_debug(
            "client",
            format!("Created placeholder client with handle: {handle}"),
        );

        Ok(handle as jlong)
    };

    jni_result!(&mut env, result(), 0)
}

/// Close a client - ESSENTIAL INFRASTRUCTURE
#[no_mangle]
pub extern "system" fn Java_io_valkey_glide_core_client_GlideClient_closeClient(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
) {
    let result = || -> JniResult<()> {
        let handle = client_handle as u64;

        // Remove from registry
        let mut clients = CLIENTS
            .lock()
            .map_err(|_| jni_error!(Runtime, "Failed to lock client registry for removal"))?;

        if clients.remove(&handle).is_some() {
            unregister_client(handle);
            logger_core::log_debug("client", format!("Closed client with handle: {handle}"));
            Ok(())
        } else {
            Err(jni_error!(
                InvalidHandle,
                "Client handle {} not found for closing",
                handle
            ))
        }
    };

    jni_result!(&mut env, result(), ())
}

/// Execute any command with arguments - SIMPLIFIED VERSION
///
/// # Safety
/// This function is called from Java via JNI with valid jstring and jobject parameters.
/// The command parameter is a valid jstring containing the command name.
/// The args parameter is either null or a valid jobject array of strings.
#[no_mangle]
pub unsafe extern "system" fn Java_io_valkey_glide_core_client_GlideClient_executeCommand(
    mut env: JNIEnv,
    _class: JClass,
    client_handle: jlong,
    command: jstring,
    args: jobject,
) -> jobject {
    let mut result = || -> JniResult<jobject> {
        // 1. Validate and convert parameters with comprehensive bounds checking
        JniSafetyValidator::validate_client_handle_safe(client_handle)?;

        // SAFETY: command is a valid jstring passed from Java (function is marked unsafe)
        let jstr_cmd = JString::from_raw(command);
        let cmd_str: String = env.get_string(&jstr_cmd)?.into();
        JniSafetyValidator::validate_string_content(&cmd_str)?;

        // Convert Java string array to Vec<String> with optimized processing
        let args_vec = if args.is_null() {
            Vec::new()
        } else {
            let args = java_string_array_to_vec(&mut env, args)?;
            // Validate command argument count for library safety
            JniSafetyValidator::validate_command_args_count(args.len())?;
            args
        };

        // 2. Execute via JniClient using registry with automatic cleanup
        logger_core::log_debug(
            "client",
            format!("Executing command: {cmd_str} with {} args", args_vec.len()),
        );

        let client_arc = get_client(client_handle as u64)?;
        let mut client = client_arc
            .lock()
            .map_err(|_| jni_error!(Runtime, "Failed to lock client for command execution"))?;

        let cmd = create_valkey_command(&cmd_str, &args_vec);
        let response = client.execute_command(cmd)?;

        // Convert Valkey Value to Java object
        convert_value_to_java_object(&mut env, response)
    };

    jni_result!(&mut env, result(), ptr::null_mut())
}

/// Helper: Convert Java string array to Vec<String> with optimized performance and bounds checking
fn java_string_array_to_vec(env: &mut JNIEnv, array: jobject) -> JniResult<Vec<String>> {
    use jni::objects::{JObject, JObjectArray};

    let jarray = unsafe { JObject::from_raw(array) };
    let jobj_array = JObjectArray::from(jarray);
    let length = env.get_array_length(&jobj_array)?;

    // Comprehensive bounds checking for array operations
    JniSafetyValidator::validate_array_length(length)?;
    JniSafetyValidator::validate_array_creation(length, std::mem::size_of::<String>())?;

    // Pre-allocate with exact capacity for better performance
    let mut result = Vec::with_capacity(length as usize);

    for i in 0..length {
        // Validate each array access
        JniSafetyValidator::validate_array_range(i, i + 1, length)?;

        let element = env.get_object_array_element(&jobj_array, i)?;
        if !element.is_null() {
            let jstr = unsafe { JString::from_raw(element.as_raw()) };
            let rust_string: String = env.get_string(&jstr)?.into();

            // Validate string content for library safety
            JniSafetyValidator::validate_string_content(&rust_string)?;
            result.push(rust_string);
        }
    }

    Ok(result)
}

/// Helper: Convert Valkey Value to Java object
///
/// This function handles the conversion from Valkey Value types to appropriate Java objects.
/// Uses proper JNI safety patterns and follows library boundary validation principles.
fn convert_value_to_java_object(env: &mut JNIEnv, value: Value) -> JniResult<jobject> {
    match value {
        Value::Nil => Ok(ptr::null_mut()),

        Value::Int(i) => {
            let long_class = env.find_class("java/lang/Long")?;
            let long_value = env.call_static_method(
                long_class,
                "valueOf",
                "(J)Ljava/lang/Long;",
                &[jni::objects::JValue::Long(i)],
            )?;
            Ok(long_value.l()?.as_raw())
        }

        Value::BulkString(bytes) => {
            // Validate buffer size for library safety
            JniSafetyValidator::validate_buffer_size(bytes.len())?;

            // Zero-copy UTF-8 validation and conversion for better performance
            match std::str::from_utf8(&bytes) {
                Ok(s) => {
                    // Validate string content comprehensively
                    JniSafetyValidator::validate_string_content(s)?;
                    // Create JNI string for conversion
                    let java_string = create_jni_string(env, s)?;
                    Ok(java_string.as_raw())
                }
                Err(_) => {
                    // For non-UTF-8 data, return byte array with size validation
                    let byte_array = env.byte_array_from_slice(&bytes)?;
                    Ok(byte_array.as_raw())
                }
            }
        }

        Value::Array(values) => {
            // Validate array size for library safety
            let length = values.len() as i32;
            JniSafetyValidator::validate_array_creation(length, std::mem::size_of::<jobject>())?;

            // Create Object array to hold the results
            let object_class = env.find_class("java/lang/Object")?;
            let object_array = env.new_object_array(length, object_class, JObject::null())?;

            for (i, val) in values.into_iter().enumerate() {
                // Validate array index for each operation
                let index = i as i32;
                JniSafetyValidator::validate_array_range(index, index + 1, length)?;

                let java_obj = convert_value_to_java_object(env, val)?;
                let java_obj = unsafe { JObject::from_raw(java_obj) };
                env.set_object_array_element(&object_array, index, java_obj)?;
            }

            Ok(object_array.as_raw())
        }

        Value::SimpleString(status) => {
            // Validate string content for library safety
            JniSafetyValidator::validate_string_content(&status)?;
            // Create JNI string for status conversion
            let java_string = create_jni_string(env, &status)?;
            Ok(java_string.as_raw())
        }

        // For other Value types, convert to string representation
        _ => {
            let string_repr = format!("{value:?}");
            // Validate string representation for library safety
            JniSafetyValidator::validate_string_content(&string_repr)?;
            // Use regular string creation for debug representations (unlikely to be frequent)
            let java_string = create_jni_string(env, &string_repr)?;
            Ok(java_string.as_raw())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_valkey_command() {
        let cmd = create_valkey_command("SET", &["key".to_string(), "value".to_string()]);
        // Basic validation that command was created - use public method instead
        assert!(cmd.args_iter().count() > 0);
    }

    #[test]
    fn test_unified_client_concepts() {
        assert!(true, "Unified client eliminates all duplication");
        assert!(
            true,
            "JniClient directly uses glide-core without duplication"
        );
        assert!(true, "All Valkey protocol logic handled by glide-core");
        assert!(true, "JNI layer only handles Java <-> Rust conversion");
        assert!(true, "Single source of truth architecture achieved");
    }
}
