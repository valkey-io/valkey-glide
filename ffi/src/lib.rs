// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::ConnectionRequest;
use glide_core::client::Client as GlideClient;
use glide_core::cluster_scan_container::get_cluster_scan_cursor;
use glide_core::command_request::SimpleRoutes;
use glide_core::command_request::{Routes, SlotTypes};
use glide_core::connection_request;
use glide_core::errors::RequestErrorType;
use glide_core::errors::{self, error_message};
use glide_core::request_type::RequestType;
use glide_core::scripts_container;
use glide_core::{
    DEFAULT_FLUSH_SIGNAL_INTERVAL_MS, GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder,
    GlideOpenTelemetrySignalsExporter, GlideSpan,
};
use protobuf::Message;
use redis::ErrorKind;
use redis::ObjectType;
use redis::ScanStateRC;
use redis::cluster_routing::{
    MultipleNodeRoutingInfo, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
};
use redis::cluster_routing::{ResponsePolicy, Routable};
use redis::{ClusterScanArgs, RedisError};
use redis::{Cmd, Pipeline, PipelineRetryStrategy, RedisResult, Value};
use std::ffi::CStr;
use std::future::Future;
use std::mem::ManuallyDrop;
use std::slice::from_raw_parts;
use std::str;
use std::str::FromStr;
use std::sync::Arc;
use std::{
    ffi::{CString, c_void},
    mem,
    os::raw::{c_char, c_double, c_long, c_ulong},
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

#[repr(C)]
pub struct ScriptHashBuffer {
    pub ptr: *mut u8,
    pub len: usize,
    pub capacity: usize,
}

/// Store a Lua script in the script cache and return its SHA1 hash.
///
/// # Parameters
///
/// * `script_bytes`: Pointer to the script bytes.
/// * `script_len`: Length of the script in bytes.
///
/// # Returns
///
/// A C string containing the SHA1 hash of the script. The caller is responsible for freeing this memory.
/// We can free the memory using [`drop_script`].
///
/// # Safety
///
/// * `script_bytes` must point to `script_len` consecutive properly initialized bytes.
/// * The returned buffer must be freed by the caller using [`free_script_hash_buffer`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn store_script(
    script_bytes: *const u8,
    script_len: usize,
) -> *mut ScriptHashBuffer {
    let script = unsafe { std::slice::from_raw_parts(script_bytes, script_len) };
    let hash = scripts_container::add_script(script);
    let mut hash = ManuallyDrop::new(hash);
    let script_hash_buffer = ScriptHashBuffer {
        ptr: hash.as_mut_ptr(),
        len: hash.len(),
        capacity: hash.capacity(),
    };
    Box::into_raw(Box::new(script_hash_buffer))
}

/// Free a `ScriptHashBuffer` obtained from [`store_script`].
///
/// # Parameters
///
/// * `buffer`: Pointer to the `ScriptHashBuffer`.
///
/// # Safety
///
/// * `buffer` must be a pointer returned from [`store_script`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_script_hash_buffer(buffer: *mut ScriptHashBuffer) {
    let buffer = unsafe { Box::from_raw(buffer) };
    let _hash = unsafe { String::from_raw_parts(buffer.ptr, buffer.len, buffer.capacity) };
}

/// Remove a script from the script cache.
///
/// Returns a null pointer if it succeeds and a C string error message if it fails.
///
/// # Parameters
///
/// * `hash`: The SHA1 hash of the script to remove as a byte array.
/// * `len`: The length of `hash`.
///
/// # Safety
///
/// * `hash` must be a valid pointer to a UTF-8 string obtained from [`store_script`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn drop_script(hash: *mut u8, len: usize) -> *mut c_char {
    if !hash.is_null() {
        let slice = std::ptr::slice_from_raw_parts_mut(hash, len);
        let Ok(hash_str) = str::from_utf8(unsafe { &*slice }) else {
            return CString::new("Unable to convert hash to UTF-8 string.")
                .unwrap()
                .into_raw();
        };
        scripts_container::remove_script(hash_str);
        std::ptr::null_mut()
    } else {
        CString::new("Hash pointer was null.").unwrap().into_raw()
    }
}

/// Free an error message from a failed drop_script call.
///
/// # Parameters
///
/// * `error`: The error to free.
///
/// # Safety
///
/// * `error` must be an error returned by [`drop_script`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_drop_script_error(error: *mut c_char) {
    if !error.is_null() {
        _ = unsafe { CString::from_raw(error) };
    }
}

/// The struct represents the response of the command.
///
/// It will have one of the value populated depending on the return type of the command.
///
/// The struct is freed by the external caller by using `free_command_response` to avoid memory leaks.
/// TODO: Add a type enum to validate what type of response is being sent in the CommandResponse.
#[repr(C)]
#[derive(Debug, Clone)]
pub struct CommandResponse {
    pub response_type: ResponseType,
    pub int_value: i64,
    pub float_value: c_double,
    pub bool_value: bool,

    /// Below two values are related to each other.
    /// `string_value` represents the string.
    /// `string_value_len` represents the length of the string.
    pub string_value: *mut c_char,
    pub string_value_len: c_long,

    /// Below two values are related to each other.
    /// `array_value` represents the array of CommandResponse.
    /// `array_value_len` represents the length of the array.
    pub array_value: *mut CommandResponse,
    pub array_value_len: c_long,

    /// Below two values represent the Map structure inside CommandResponse.
    /// The map is transformed into an array of (map_key: CommandResponse, map_value: CommandResponse) and passed to the foreign language.
    /// These are represented as pointers as the map can be null (optionally present).
    pub map_key: *mut CommandResponse,
    pub map_value: *mut CommandResponse,

    /// Below two values are related to each other.
    /// `sets_value` represents the set of CommandResponse.
    /// `sets_value_len` represents the length of the set.
    pub sets_value: *mut CommandResponse,
    pub sets_value_len: c_long,
}

impl Default for CommandResponse {
    fn default() -> Self {
        CommandResponse {
            response_type: ResponseType::default(),
            int_value: 0,
            float_value: 0.0,
            bool_value: false,
            string_value: std::ptr::null_mut(),
            string_value_len: 0,
            array_value: std::ptr::null_mut(),
            array_value_len: 0,
            map_key: std::ptr::null_mut(),
            map_value: std::ptr::null_mut(),
            sets_value: std::ptr::null_mut(),
            sets_value_len: 0,
        }
    }
}

#[repr(C)]
#[derive(Debug, Default, Clone)]
pub enum ResponseType {
    #[default]
    Null = 0,
    Int = 1,
    Float = 2,
    Bool = 3,
    String = 4,
    Array = 5,
    Map = 6,
    Sets = 7,
    Ok = 8,
    Error = 9,
}

/// Success callback that is called when a command succeeds.
///
/// The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
/// `message` is the value returned by the command. The 'message' is managed by Rust and is freed when the callback returns control back to the caller.
///
/// # Safety
/// `message` must be a valid pointer to a `CommandResponse` and must be freed using [`free_command_response`].
pub type SuccessCallback =
    unsafe extern "C-unwind" fn(index_ptr: usize, message: *const CommandResponse) -> ();

/// Failure callback that is called when a command fails.
///
/// The failure callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// `index_ptr` is a baton-pass back to the caller language to uniquely identify the promise.
/// `error_message` is the error message returned by server for the failed command. The 'error_message' is managed by Rust and is freed when the callback returns control back to the caller.
/// `error_type` is the type of error returned by glide-core, depending on the `RedisError` returned.
///
/// # Safety
/// `error_message` must be a valid pointer to a `c_char`.
pub type FailureCallback = unsafe extern "C-unwind" fn(
    index_ptr: usize,
    error_message: *const c_char,
    error_type: RequestErrorType,
) -> ();

/// PubSub callback that is called when a push notification is received.
///
/// The PubSub callback needs to handle the push notification synchronously, since the data will be dropped by Rust once the callback returns.
/// The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// # Parameters
/// * `client_ptr`: A baton-pass back to the caller language to uniquely identify the client.
/// * `kind`: An enum variant representing the PushKind (Message, PMessage, SMessage, etc.)
/// * `message`: A pointer to the raw message bytes.
/// * `message_len`: The length of the message data in bytes.
/// * `channel`: A pointer to the raw request name bytes.
/// * `channel_len`: The length of the request name in bytes.
/// * `pattern`: A pointer to the raw pattern bytes (null if no pattern).
/// * `pattern_len`: The length of the pattern in bytes (0 if no pattern).
///
/// # Safety
/// The pointers are only valid during the callback execution and will be freed
/// automatically when the callback returns. Any data needed beyond the callback's
/// execution must be copied.
pub type PubSubCallback = unsafe extern "C-unwind" fn(
    client_ptr: usize,
    kind: PushKind,
    message: *const u8,
    message_len: i64,
    channel: *const u8,
    channel_len: i64,
    pattern: *const u8,
    pattern_len: i64,
) -> ();

/// The connection response.
///
/// It contains either a connection or an error. It is represented as a struct instead of a union for ease of use in the wrapper language.
///
/// The struct is freed by the external caller by using `free_connection_response` to avoid memory leaks.
#[repr(C)]
pub struct ConnectionResponse {
    pub conn_ptr: *const c_void,
    pub connection_error_message: *const c_char,
}

/// Represents an error returned from a command execution.
///
/// This struct is returned as part of a [`CommandResult`] when a command fails in synchronous operations.
/// It contains both the error type and a message explaining the cause.
///
/// # Fields
///
/// - `command_error_message`: A null-terminated C string describing the error.
/// - `command_error_type`: An enum identifying the type of error. See [`RequestErrorType`] for details.
///
/// # Safety
///
/// The pointer `command_error_message` must remain valid and not be freed until after
/// [`free_command_result`] is called.
///
#[repr(C)]
pub struct CommandError {
    pub command_error_message: *const c_char,
    pub command_error_type: RequestErrorType,
}

/// Represents the result of a logging operation.
///
/// This struct is used to communicate both success/failure status and relevant data
/// across the FFI boundary. For initialization operations, it contains the log level
/// that was set. For other operations, it primarily indicates success or failure.
///
/// # Fields
///
/// - `log_error`: A pointer to a null-terminated C string containing an error message.
///   This field is `null` if the operation succeeded, or points to an error description
///   if the operation failed.
/// - `level`: The log level value. For initialization operations, this contains the
///   actual level that was set by the logger. For other operations, this field may
///   be ignored when there's an error.
///
/// # Safety
///
/// The returned `LogResult` must be freed using [`free_log_result`] to avoid memory leaks.
/// This will properly deallocate both the struct itself and any error message it contains.
///
/// - The `log_error` field must either be null or point to a valid, null-terminated C string
/// - The struct must be freed exactly once using [`free_log_result`]
/// - The error string must not be accessed after the struct has been freed
/// - The `level` field is only meaningful when `log_error` is null (success case)
#[repr(C)]
pub struct LogResult {
    pub log_error: *mut c_char,
    pub level: Level,
}

/// Represents the result of executing a command, either a successful response or an error.
///
/// This is the  return type for FFI functions that execute commands synchronously (e.g. with a SyncClient).
/// It is a tagged struct containing either a valid [`CommandResponse`] or a [`CommandError`].
/// If `command_error` is non-null, then `response` is guaranteed to be null and vice versa.
///
/// # Fields
///
/// - `response`: A pointer to a [`CommandResponse`] if the command was successful. Null if there was an error.
/// - `command_error`: A pointer to a [`CommandError`] if the command failed. Null if the command succeeded.
///
/// # Ownership
///
/// The returned pointer to `CommandResult` must be freed using [`free_command_result`] to avoid memory leaks.
/// This will recursively free both the response or the error, depending on which is set.
///
/// # Safety
///
/// The caller must check which field is non-null before accessing its contents.
/// Only one of the two fields (`response` or `command_error`) will be set.
#[repr(C)]
pub struct CommandResult {
    pub response: *mut CommandResponse,
    pub command_error: *mut CommandError,
}

// Deallocates a `CommandResult`.
///
/// This function frees both the `CommandResult` itself and its internal components if preset.
///
/// # Behavior
///
/// - If the provided `command_result_ptr` is null, the function returns immediately.
/// - If either `response` or `command_error` is non-null, they are deallocated accordingly.
///
/// # Safety
///
/// * `free_command_result` must only be called **once** for any given `CommandResult`.
///   Calling it multiple times is undefined behavior and may lead to double-free errors.
/// * The `command_result_ptr` must be a valid pointer returned by a function that creates a `CommandResult`.
/// * The memory behind `command_result_ptr` must remain valid until this function is called.
/// * If `command_error.command_error_message` is non-null, it must be a valid pointer obtained from Rust
///   and must outlive the `CommandError` itself.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_command_result(command_result_ptr: *mut CommandResult) {
    if command_result_ptr.is_null() {
        return;
    }
    unsafe {
        let command_result = Box::from_raw(command_result_ptr);
        if !command_result.response.is_null() {
            free_command_response(command_result.response);
        }
        if !command_result.command_error.is_null() {
            let command_error = Box::from_raw(command_result.command_error);
            if !command_error.command_error_message.is_null() {
                _ = CString::from_raw(command_error.command_error_message as *mut c_char);
            }
        }
    }
}

/// Specifies the type of client used to execute commands.
///
/// This enum distinguishes between synchronous and asynchronous client modes.
/// It is passed from the calling language (e.g. Go or Python) to determine how
/// command execution should be handled.
///
/// # Variants
///
/// - `AsyncClient`: Executes commands asynchronously. Includes callbacks for success and failure
///   that will be invoked once the command completes.
/// - `SyncClient`: Executes commands synchronously and returns a result directly.
#[repr(C)]
#[derive(Clone)]
pub enum ClientType {
    AsyncClient {
        success_callback: SuccessCallback,
        failure_callback: FailureCallback,
    },
    SyncClient,
}

/// A `GlideClient` adapter.
pub struct ClientAdapter {
    runtime: Runtime,
    core: Arc<CommandExecutionCore>,
}

struct CommandExecutionCore {
    client: GlideClient,
    client_type: ClientType,
}

impl ClientAdapter {
    /// Executes a command and routes the result based on client type.
    ///
    /// For async clients, spawns the future and returns null immediately.
    /// For sync clients, blocks on the future and returns a `CommandResult`.
    #[must_use]
    fn execute_request<Fut>(&self, request_id: usize, request_future: Fut) -> *mut CommandResult
    where
        Fut: Future<Output = RedisResult<Value>> + Send + 'static,
    {
        match self.core.client_type {
            ClientType::AsyncClient {
                success_callback,
                failure_callback,
            } => {
                // Spawn the request for async client
                self.runtime.spawn(async move {
                    let result = request_future.await;
                    let _ = Self::handle_result(
                        result,
                        Some(success_callback),
                        Some(failure_callback),
                        request_id,
                    );
                });
                std::ptr::null_mut()
            }
            ClientType::SyncClient => {
                // Block on the request for sync client
                let result = self.runtime.block_on(request_future);
                Self::handle_result(result, None, None, request_id)
            }
        }
    }

    /// Handles the result of a command and returns a `CommandResult`.
    ///
    /// For async clients, invokes the appropriate callback and returns null.
    /// For sync clients, returns a `CommandResult`.
    // TODO SAFETY
    #[must_use]
    fn handle_result(
        result: RedisResult<Value>,
        success_callback: Option<SuccessCallback>,
        failure_callback: Option<FailureCallback>,
        request_id: usize,
    ) -> *mut CommandResult {
        match result {
            Ok(value) => match valkey_value_to_command_response(value) {
                Ok(command_response) => {
                    if let Some(success_callback) = success_callback {
                        unsafe {
                            (success_callback)(
                                request_id,
                                Box::into_raw(Box::new(command_response)),
                            );
                        }
                    } else {
                        return Box::into_raw(Box::new(CommandResult {
                            response: Box::into_raw(Box::new(command_response)),
                            command_error: std::ptr::null_mut(),
                        }));
                    }
                }
                Err(err) => {
                    if let Some(failure_callback) = failure_callback {
                        unsafe { Self::send_async_redis_error(failure_callback, err, request_id) };
                    } else {
                        eprintln!("Error converting value to CommandResponse: {err:?}");
                        return create_error_result_with_redis_error(err);
                    }
                }
            },
            Err(err) => {
                if let Some(failure_callback) = failure_callback {
                    unsafe { Self::send_async_redis_error(failure_callback, err, request_id) };
                } else {
                    eprintln!("Error executing command: {err:?}");
                    return create_error_result_with_redis_error(err);
                }
            }
        };
        std::ptr::null_mut()
    }

    /// Handles a Redis error by either invoking the failure callback (for async clients)
    /// or returning a heap-allocated `CommandResult` (for sync clients).
    ///
    /// This method ensures consistent error handling logic across both client types.
    ///
    /// # Parameters
    /// - `err`: The `RedisError` to handle.
    /// - `request_id`: The unique ID associated with the request.
    ///
    /// # Returns
    /// - For async clients: Returns a null pointer after invoking the failure callback.
    /// - For sync clients: Returns a pointer to a `CommandResult` containing the error.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`Self::handle_custom_error`].
    #[must_use]
    unsafe fn handle_redis_error(&self, err: RedisError, request_id: usize) -> *mut CommandResult {
        let error_string = errors::error_message(&err);
        let error_type = errors::error_type(&err);
        unsafe { Self::handle_custom_error(self, error_string, error_type, request_id) }
    }

    /// Handles a Redis error by either invoking the failure callback (for async clients)
    /// or returning a heap-allocated `CommandResult` (for sync clients).
    ///
    /// This method ensures consistent error handling logic across both client types.
    ///
    /// # Parameters
    /// - `error_string`: The error to handle.
    /// - `error_type`: The error type.
    /// - `request_id`: The unique ID associated with the request.
    ///
    /// # Returns
    /// - For async clients: Returns a null pointer after invoking the failure callback.
    /// - For sync clients: Returns a pointer to a `CommandResult` containing the error.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`Self::send_async_custom_error`].
    unsafe fn handle_custom_error(
        &self,
        error_string: String,
        error_type: RequestErrorType,
        request_id: usize,
    ) -> *mut CommandResult {
        //logger_core::log(logger_core::Level::Error, "ffi", &error_string);
        match self.core.client_type {
            ClientType::AsyncClient {
                success_callback: _,
                failure_callback,
            } => {
                unsafe {
                    Self::send_async_custom_error(
                        failure_callback,
                        error_string,
                        error_type,
                        request_id,
                    )
                };
                std::ptr::null_mut()
            }
            ClientType::SyncClient => {
                create_error_result_with_custom_error(error_string, error_type)
            }
        }
    }

    /// Invokes the asynchronous failure callback with an error.
    ///
    /// This function is used in async client flows to report command execution failures
    /// back to the calling language (e.g. Go) via a registered failure callback.
    ///
    /// # Parameters
    /// - `failure_callback`: The callback to invoke with the error.
    /// - `error_string`: The error message to report.
    /// - `error_type`: The error type to report.
    /// - `request_id`: An identifier used to correlate the error to the original request.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`FailureCallback`].
    unsafe fn send_async_custom_error(
        failure_callback: FailureCallback,
        error_string: String,
        error_type: RequestErrorType,
        request_id: usize,
    ) {
        let err_ptr = CString::into_raw(
            CString::new(error_string).expect("Couldn't convert error message to CString"),
        );
        unsafe { (failure_callback)(request_id, err_ptr, error_type) };
        _ = unsafe { CString::from_raw(err_ptr as *mut c_char) };
    }

    /// Invokes the asynchronous failure callback with an error.
    ///
    /// This function is used in async client flows to report command execution failures
    /// back to the calling language (e.g. Go) via a registered failure callback.
    ///
    /// # Parameters
    /// - `failure_callback`: The callback to invoke with the error.
    /// - `err`: The `RedisError` to report.
    /// - `request_id`: An identifier used to correlate the error to the original request.
    ///
    /// # Safety
    /// Unsafe, because calls to an FFI function. See the safety documentation of [`FailureCallback`].
    unsafe fn send_async_redis_error(
        failure_callback: FailureCallback,
        err: RedisError,
        request_id: usize,
    ) {
        let (c_err_str, error_type) = to_c_error(err);
        unsafe { (failure_callback)(request_id, c_err_str, error_type) };
        _ = unsafe { CString::from_raw(c_err_str as *mut c_char) };
    }
}

#[repr(C)]
#[derive(Debug, Clone)]
pub enum PushKind {
    PushDisconnection,
    PushOther,
    PushInvalidate,
    PushMessage,
    PushPMessage,
    PushSMessage,
    PushUnsubscribe,
    PushPUnsubscribe,
    PushSUnsubscribe,
    PushSubscribe,
    PushPSubscribe,
    PushSSubscribe,
}

impl From<redis::PushKind> for PushKind {
    fn from(value: redis::PushKind) -> Self {
        match value {
            redis::PushKind::Disconnection => PushKind::PushDisconnection,
            redis::PushKind::Other(_) => PushKind::PushOther,
            redis::PushKind::Invalidate => PushKind::PushInvalidate,
            redis::PushKind::Message => PushKind::PushMessage,
            redis::PushKind::PMessage => PushKind::PushPMessage,
            redis::PushKind::SMessage => PushKind::PushSMessage,
            redis::PushKind::Unsubscribe => PushKind::PushUnsubscribe,
            redis::PushKind::PUnsubscribe => PushKind::PushPUnsubscribe,
            redis::PushKind::SUnsubscribe => PushKind::PushSUnsubscribe,
            redis::PushKind::Subscribe => PushKind::PushSubscribe,
            redis::PushKind::PSubscribe => PushKind::PushPSubscribe,
            redis::PushKind::SSubscribe => PushKind::PushSSubscribe,
        }
    }
}

/// Processes a push notification message and calls the provided callback function.
///
/// This function converts a PushInfo message to a CommandResponse, determines the
/// notification type, and invokes the callback with the appropriate parameters.
///
/// # Parameters
/// - `push_msg`: The push notification message to process.
/// - `pubsub_callback`: The callback function to invoke with the processed notification.
/// - `client_adapter_ptr`: A pointer to the client adapter to pass to the callback.
///
/// # Returns
/// - `true` if the message was successfully processed and the callback was called.
/// - `false` if there was an error processing the message (e.g., conversion failed).
///
/// # Safety
/// This function is unsafe because it:
/// - Dereferences raw pointers
/// - Calls an FFI function (`pubsub_callback`) that may have undefined behavior
/// - Creates and destroys vectors via `Vec::from_raw_parts`
/// - Assumes push_msg.data contains valid BulkString values
///
/// The caller must ensure:
/// - `pubsub_callback` is a valid function pointer to a properly implemented callback
/// - `client_adapter_ptr` is a valid usize representing a client adapter pointer
/// - Memory allocated during conversion is properly freed after the callback completes
unsafe fn process_push_notification(
    push_msg: redis::PushInfo,
    pubsub_callback: PubSubCallback,
    client_adapter_ptr: usize,
) {
    let strings: Vec<(*mut u8, i64)> = push_msg
        .data
        .iter()
        .map(|v| {
            let Value::BulkString(str) = v else {
                unreachable!()
            };
            let (ptr, len) = convert_vec_to_pointer(str.clone());
            (ptr, len)
        })
        .collect();

    let ((pattern_ptr, pattern_len), (channel, channel_len), (message_ptr, message_len)) = {
        if strings.len() == 3 {
            (strings[0], strings[1], strings[2])
        } else {
            ((std::ptr::null_mut::<u8>(), 0), strings[0], strings[1])
        }
    };

    // Call the pubsub callback with the push notification data
    unsafe {
        pubsub_callback(
            client_adapter_ptr,
            push_msg.kind.into(),
            message_ptr,
            message_len,
            channel,
            channel_len,
            pattern_ptr,
            pattern_len,
        );
        // Free memory
        let _ = Vec::from_raw_parts(message_ptr, message_len as usize, message_len as usize);
        let _ = Vec::from_raw_parts(channel, channel_len as usize, channel_len as usize);
        if !pattern_ptr.is_null() {
            let _ = Vec::from_raw_parts(pattern_ptr, pattern_len as usize, pattern_len as usize);
        }
    }
}

fn create_client_internal(
    connection_request_bytes: &[u8],
    client_type: ClientType,
    pubsub_callback: PubSubCallback,
) -> Result<*const ClientAdapter, String> {
    let request = connection_request::ConnectionRequest::parse_from_bytes(connection_request_bytes)
        .map_err(|err| err.to_string())?;
    // TODO: optimize this using multiple threads instead of a single worker thread (e.g. by pinning each go thread to a rust thread)
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .worker_threads(1)
        .thread_name("Valkey-GLIDE thread")
        .build()
        .map_err(|err| {
            let redis_error = err.into();
            errors::error_message(&redis_error)
        })?;

    let is_subscriber = request.pubsub_subscriptions.is_some() && pubsub_callback as usize != 0;
    let (push_tx, mut push_rx) = tokio::sync::mpsc::unbounded_channel();
    let tx = match is_subscriber {
        true => Some(push_tx),
        false => None,
    };

    let client = runtime
        .block_on(GlideClient::new(ConnectionRequest::from(request), tx))
        .map_err(|err| err.to_string())?;

    // Create the client adapter that will be returned and used as conn_ptr
    let core = Arc::new(CommandExecutionCore {
        client,
        client_type,
    });
    let client_adapter = Arc::new(ClientAdapter { runtime, core });
    // Clone client_adapter before moving it into the async block
    let client_adapter_ptr = Arc::as_ptr(&client_adapter).addr();

    // If pubsub_callback is provided (not null), spawn a task to handle push notifications
    if is_subscriber {
        client_adapter.runtime.spawn(async move {
            while let Some(push_msg) = push_rx.recv().await {
                if push_msg.kind == redis::PushKind::Message
                    || push_msg.kind == redis::PushKind::PMessage
                    || push_msg.kind == redis::PushKind::SMessage
                {
                    unsafe {
                        process_push_notification(push_msg, pubsub_callback, client_adapter_ptr);
                    }
                }
            }
        });
    }

    Ok(Arc::into_raw(client_adapter))
}

/// Creates a new `ClientAdapter` with a new `GlideClient` configured using a Protobuf `ConnectionRequest`.
///
/// The returned `ConnectionResponse` will only be freed by calling [`free_connection_response`].
///
/// `connection_request_bytes` is an array of bytes that will be parsed into a Protobuf `ConnectionRequest` object.
/// `connection_request_len` is the number of bytes in `connection_request_bytes`.
/// `success_callback` is the callback that will be called when a command succeeds.
/// `failure_callback` is the callback that will be called when a command fails.
///
/// # Safety
///
/// * `connection_request_bytes` must point to `connection_request_len` consecutive properly initialized bytes. It must be a well-formed Protobuf `ConnectionRequest` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `connection_request_len` must not be greater than the length of the connection request bytes array. It must also not be greater than the max value of a signed pointer-sized integer.
/// * The `conn_ptr` pointer in the returned `ConnectionResponse` must live while the client is open/active and must be explicitly freed by calling [`close_client``].
/// * The `connection_error_message` pointer in the returned `ConnectionResponse` must live until the returned `ConnectionResponse` pointer is passed to [`free_connection_response``].
/// * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
// TODO: Consider making this async
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn create_client(
    connection_request_bytes: *const u8,
    connection_request_len: usize,
    client_type: *const ClientType,
    pubsub_callback: PubSubCallback,
) -> *const ConnectionResponse {
    assert!(!connection_request_bytes.is_null());
    let request_bytes =
        unsafe { std::slice::from_raw_parts(connection_request_bytes, connection_request_len) };
    let client_type = unsafe { &*client_type };
    let response = match create_client_internal(request_bytes, client_type.clone(), pubsub_callback)
    {
        Err(err) => ConnectionResponse {
            conn_ptr: std::ptr::null(),
            connection_error_message: CString::into_raw(
                CString::new(err).expect("Couldn't convert error message to CString"),
            ),
        },
        Ok(client) => ConnectionResponse {
            conn_ptr: client as *const c_void,
            connection_error_message: std::ptr::null(),
        },
    };
    Box::into_raw(Box::new(response))
}

/// Closes the given `GlideClient`, freeing it from the heap.
///
/// `client_adapter_ptr` is a pointer to a valid `GlideClient` returned in the `ConnectionResponse` from [`create_client`].
///
/// # Panics
///
/// This function panics when called with a null `client_adapter_ptr`.
///
/// # Safety
///
/// * `close_client` can only be called once per client. Calling it twice is undefined behavior, since the address will be freed twice.
/// * `close_client` must be called after `free_connection_response` has been called to avoid creating a dangling pointer in the `ConnectionResponse`.
/// * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be valid until `close_client` is called.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn close_client(client_adapter_ptr: *const c_void) {
    assert!(!client_adapter_ptr.is_null());
    // This will bring the strong count down to 0 once all client requests are done.
    unsafe { Arc::decrement_strong_count(client_adapter_ptr as *const ClientAdapter) };
}

/// Deallocates a `ConnectionResponse`.
///
/// This function also frees the contained error. If the contained error is a null pointer, the function returns and only the `ConnectionResponse` is freed.
///
/// # Panics
///
/// This function panics when called with a null `ConnectionResponse` pointer.
///
/// # Safety
///
/// * `free_connection_response` can only be called once per `ConnectionResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
/// * `connection_response_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `connection_response_ptr` must be valid until `free_connection_response` is called.
/// * The contained `connection_error_message` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * The contained `connection_error_message` must be valid until `free_connection_response` is called and it must outlive the `ConnectionResponse` that contains it.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_connection_response(
    connection_response_ptr: *mut ConnectionResponse,
) {
    assert!(!connection_response_ptr.is_null());
    let connection_response = unsafe { Box::from_raw(connection_response_ptr) };
    let connection_error_message = connection_response.connection_error_message;
    drop(connection_response);
    if !connection_error_message.is_null() {
        drop(unsafe { CString::from_raw(connection_error_message as *mut c_char) });
    }
}

/// Provides the string mapping for the ResponseType enum.
///
/// Important: the returned pointer is a pointer to a constant string and should not be freed.
#[unsafe(no_mangle)]
pub extern "C" fn get_response_type_string(response_type: ResponseType) -> *const c_char {
    let c_str = match response_type {
        ResponseType::Null => c"Null",
        ResponseType::Int => c"Int",
        ResponseType::Float => c"Float",
        ResponseType::Bool => c"Bool",
        ResponseType::String => c"String",
        ResponseType::Array => c"Array",
        ResponseType::Map => c"Map",
        ResponseType::Sets => c"Sets",
        ResponseType::Ok => c"Ok",
        ResponseType::Error => c"Error",
    };
    c_str.as_ptr()
}

/// Deallocates a `CommandResponse`.
///
/// This function also frees the contained string_value and array_value. If the string_value and array_value are null pointers, the function returns and only the `CommandResponse` is freed.
///
/// # Safety
///
/// * `free_command_response` can only be called once per `CommandResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
/// * `command_response_ptr` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * `command_response_ptr` must be valid until `free_command_response` is called.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_command_response(command_response_ptr: *mut CommandResponse) {
    if !command_response_ptr.is_null() {
        let command_response = unsafe { Box::from_raw(command_response_ptr) };
        unsafe { free_command_response_elements(*command_response) };
    }
}

/// Frees the nested elements of `CommandResponse`.
/// TODO: Add a test case to check for memory leak.
///
/// # Safety
///
/// * `free_command_response_elements` can only be called once per `CommandResponse`. Calling it twice is undefined behavior, since the address will be freed twice.
/// * The contained `string_value` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `string_value` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
/// * The contained `array_value` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `array_value` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
/// * The contained `map_key` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `map_key` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
/// * The contained `map_value` must be obtained from the `CommandResponse` returned in [`SuccessCallback`] from [`command`].
/// * The contained `map_value` must be valid until `free_command_response` is called and it must outlive the `CommandResponse` that contains it.
unsafe fn free_command_response_elements(command_response: CommandResponse) {
    let string_value = command_response.string_value;
    let string_value_len = command_response.string_value_len;
    let array_value = command_response.array_value;
    let array_value_len = command_response.array_value_len;
    let map_key = command_response.map_key;
    let map_value = command_response.map_value;
    let sets_value = command_response.sets_value;
    let sets_value_len = command_response.sets_value_len;
    if !string_value.is_null() {
        let len = string_value_len as usize;
        unsafe { Vec::from_raw_parts(string_value, len, len) };
    }
    if !array_value.is_null() {
        let len = array_value_len as usize;
        let vec = unsafe { Vec::from_raw_parts(array_value, len, len) };
        for element in vec.into_iter() {
            unsafe { free_command_response_elements(element) };
        }
    }
    if !map_key.is_null() {
        unsafe { free_command_response(map_key) };
    }
    if !map_value.is_null() {
        unsafe { free_command_response(map_value) };
    }
    if !sets_value.is_null() {
        let len = sets_value_len as usize;
        let vec = unsafe { Vec::from_raw_parts(sets_value, len, len) };
        for element in vec.into_iter() {
            unsafe { free_command_response_elements(element) };
        }
    }
}

/// Converts a double pointer to a vec.
///
/// # Safety
///
/// `convert_double_pointer_to_vec` returns a `Vec` of u8 slice which holds pointers of `go`
/// strings. The returned `Vec<&'a [u8]>` is meant to be copied into Rust code. Storing them
/// for later use will cause the program to crash as the pointers will be freed by go's gc
unsafe fn convert_double_pointer_to_vec<'a>(
    data: *const *const c_void,
    len: c_ulong,
    data_len: *const c_ulong,
) -> Vec<&'a [u8]> {
    let string_ptrs = unsafe { from_raw_parts(data, len as usize) };
    let string_lengths = unsafe { from_raw_parts(data_len, len as usize) };
    let mut result = Vec::<&[u8]>::with_capacity(string_ptrs.len());
    for (i, &str_ptr) in string_ptrs.iter().enumerate() {
        let slice = unsafe { from_raw_parts(str_ptr as *const u8, string_lengths[i] as usize) };
        result.push(slice);
    }
    result
}

fn convert_vec_to_pointer<T>(mut vec: Vec<T>) -> (*mut T, c_long) {
    vec.shrink_to_fit();
    let vec_ptr = vec.as_mut_ptr();
    let len = vec.len() as c_long;
    mem::forget(vec);
    (vec_ptr, len)
}

fn valkey_value_to_command_response(value: Value) -> RedisResult<CommandResponse> {
    let mut command_response = CommandResponse::default();
    let result: RedisResult<CommandResponse> = match value {
        Value::Nil => Ok(command_response),
        Value::SimpleString(text) => {
            let vec: Vec<u8> = text.into_bytes();
            let (vec_ptr, len) = convert_vec_to_pointer(vec);
            command_response.string_value = vec_ptr as *mut c_char;
            command_response.string_value_len = len;
            command_response.response_type = ResponseType::String;
            Ok(command_response)
        }
        Value::BulkString(text) => {
            let (vec_ptr, len) = convert_vec_to_pointer(text);
            command_response.string_value = vec_ptr as *mut c_char;
            command_response.string_value_len = len;
            command_response.response_type = ResponseType::String;
            Ok(command_response)
        }
        Value::VerbatimString { format: _, text } => {
            let vec: Vec<u8> = text.into_bytes();
            let (vec_ptr, len) = convert_vec_to_pointer(vec);
            command_response.string_value = vec_ptr as *mut c_char;
            command_response.string_value_len = len;
            command_response.response_type = ResponseType::String;
            Ok(command_response)
        }
        Value::Okay => {
            command_response.response_type = ResponseType::Ok;
            Ok(command_response)
        }
        Value::Int(num) => {
            command_response.int_value = num;
            command_response.response_type = ResponseType::Int;
            Ok(command_response)
        }
        Value::Double(num) => {
            command_response.float_value = num;
            command_response.response_type = ResponseType::Float;
            Ok(command_response)
        }
        Value::Boolean(boolean) => {
            command_response.bool_value = boolean;
            command_response.response_type = ResponseType::Bool;
            Ok(command_response)
        }
        Value::Array(array) => {
            let vec: Result<Vec<CommandResponse>, RedisError> = array
                .into_iter()
                .map(valkey_value_to_command_response)
                .collect();
            let (vec_ptr, len) = convert_vec_to_pointer(vec?);
            command_response.array_value = vec_ptr;
            command_response.array_value_len = len;
            command_response.response_type = ResponseType::Array;
            Ok(command_response)
        }
        Value::Map(map) => {
            let result: Result<Vec<CommandResponse>, RedisError> = map
                .into_iter()
                .map(|(key, val)| {
                    let mut map_response = CommandResponse::default();

                    let map_key = match valkey_value_to_command_response(key) {
                        Ok(map_key) => map_key,
                        Err(err) => return Err(err),
                    };
                    map_response.map_key = Box::into_raw(Box::new(map_key));

                    let map_val = match valkey_value_to_command_response(val) {
                        Ok(map_val) => map_val,
                        Err(err) => return Err(err),
                    };
                    map_response.map_value = Box::into_raw(Box::new(map_val));

                    Ok(map_response)
                })
                .collect::<Result<Vec<CommandResponse>, RedisError>>();

            let (vec_ptr, len) = convert_vec_to_pointer(result?);
            command_response.array_value = vec_ptr;
            command_response.array_value_len = len;
            command_response.response_type = ResponseType::Map;
            Ok(command_response)
        }
        Value::Set(array) => {
            let vec: Result<Vec<CommandResponse>, RedisError> = array
                .into_iter()
                .map(valkey_value_to_command_response)
                .collect();
            let (vec_ptr, len) = convert_vec_to_pointer(vec?);
            command_response.sets_value = vec_ptr;
            command_response.sets_value_len = len;
            command_response.response_type = ResponseType::Sets;
            Ok(command_response)
        }
        Value::ServerError(server_error) => {
            let error_message: String = error_message(&server_error.into());
            // Convert the formatted string to bytes
            let bytes = error_message.into_bytes();
            // Process the bytes as before
            let (vec_ptr, len) = convert_vec_to_pointer(bytes);
            command_response.string_value = vec_ptr as *mut c_char;
            command_response.string_value_len = len;
            command_response.response_type = ResponseType::Error;

            // Return as Ok to continue transaction processing
            Ok(command_response)
        }
        Value::Push { kind, data } => {
            // Create kind entry
            let mut kind_entry = CommandResponse::default();
            let map_key =
                valkey_value_to_command_response(Value::SimpleString("kind".to_string()))?;
            kind_entry.map_key = Box::into_raw(Box::new(map_key));
            let map_val =
                valkey_value_to_command_response(Value::SimpleString(format!("{:?}", kind)))?;
            kind_entry.map_value = Box::into_raw(Box::new(map_val));

            // Create values entry
            let mut values_entry = CommandResponse::default();
            let map_key =
                valkey_value_to_command_response(Value::SimpleString("values".to_string()))?;
            values_entry.map_key = Box::into_raw(Box::new(map_key));
            let map_val = valkey_value_to_command_response(Value::Array(data))?;
            values_entry.map_value = Box::into_raw(Box::new(map_val));

            let (map_ptr, map_len) = convert_vec_to_pointer(vec![kind_entry, values_entry]);
            command_response.array_value = map_ptr;
            command_response.array_value_len = map_len;
            command_response.response_type = ResponseType::Map;

            Ok(command_response)
        }
        // TODO: Add support for other return types.
        _ => todo!(),
    };
    result
}

/// Executes a command.
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`std::sync::Arc::from_raw`].
/// * `request_id` must be a request ID from the foreign language and must be valid until either `success_callback` or `failure_callback` is finished.
/// * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `arg_count` the number of elements in `args` and `args_len`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `arg_count` must be 0 if `args` and `args_len` are null.
/// * `args` and `args_len` must either be both null or be both not null.
/// * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `route_bytes_len` must be 0 if `route_bytes` is null.
/// * `span_ptr` is a valid pointer to [`Arc<GlideSpan>`], a span created by [`create_otel_span`] or `0`. The span must be valid until the command is finished.
/// * This function should only be called should with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn command(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    command_type: RequestType,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
    span_ptr: u64,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    let arg_vec: Vec<&[u8]> = if !args.is_null() && !args_len.is_null() {
        unsafe { convert_double_pointer_to_vec(args as *const *const c_void, arg_count, args_len) }
    } else {
        Vec::new()
    };

    // Create the command outside of the task to ensure that the command arguments passed
    // from the foreign code are still valid
    let mut cmd = match command_type.get_command() {
        Some(cmd) => cmd,
        None => {
            let err = RedisError::from((ErrorKind::ClientError, "Couldn't fetch command type"));
            return unsafe { client_adapter.handle_redis_error(err, request_id) };
        }
    };
    for command_arg in arg_vec {
        cmd.arg(command_arg);
    }
    if span_ptr != 0 {
        cmd.set_span(unsafe { get_unsafe_span_from_ptr(Some(span_ptr)) });
    }

    let route = if !route_bytes.is_null() {
        let r_bytes = unsafe { std::slice::from_raw_parts(route_bytes, route_bytes_len) };
        match Routes::parse_from_bytes(r_bytes) {
            Ok(route) => route,
            Err(err) => {
                let err = RedisError::from((
                    ErrorKind::ClientError,
                    "Decoding route failed",
                    err.to_string(),
                ));
                return unsafe { client_adapter.handle_redis_error(err, request_id) };
            }
        }
    } else {
        Routes::default()
    };

    let child_span = create_child_span(cmd.span().as_ref(), "send_command");
    let mut client = client_adapter.core.client.clone();
    let result = client_adapter.execute_request(request_id, async move {
        let routing_info = get_route(route, Some(&cmd))?;
        client.send_command(&cmd, routing_info).await
    });
    if let Ok(span) = child_span {
        span.end();
    }
    result
}

/// Creates a heap-allocated `CommandResult` containing a `CommandError`.
///
/// This function is used to construct an error response when a Valkey command fails,
/// intended to be returned through FFI to the calling language.
///
/// The resulting `CommandResult` contains:
/// - A null `response` pointer.
/// - A valid `command_error` pointer with error details (message and type).
///
/// # Parameters
/// - `err`: The `RedisError` to be converted into a `CommandError`.
///
/// # Returns
/// A raw pointer to a `CommandResult`. This must be freed using [`free_command_result`]
/// to avoid memory leaks.
///
/// # Safety
/// The returned pointer must be passed back to Rust for cleanup. Failing to call
/// [`free_command_result`] will result in a memory leak.
fn create_error_result_with_redis_error(err: RedisError) -> *mut CommandResult {
    let (c_err_str, error_type) = to_c_error(err);
    Box::into_raw(Box::new(CommandResult {
        response: std::ptr::null_mut(),
        command_error: Box::into_raw(Box::new(CommandError {
            command_error_message: c_err_str,
            command_error_type: error_type,
        })),
    }))
}

/// Creates a heap-allocated `CommandResult` containing a `CommandError`.
///
/// This function is used to construct an error response when a Valkey command fails,
/// intended to be returned through FFI to the calling language.
///
/// The resulting `CommandResult` contains:
/// - A null `response` pointer.
/// - A valid `command_error` pointer with error details (message and type).
///
/// # Parameters
/// - `error_string`: The error message to be converted into a `CommandError`.
/// - `error_type`: The error type.
///
/// # Returns
/// A raw pointer to a `CommandResult`. This must be freed using [`free_command_result`]
/// to avoid memory leaks.
///
/// # Safety
/// The returned pointer must be passed back to Rust for cleanup. Failing to call
/// [`free_command_result`] will result in a memory leak.
fn create_error_result_with_custom_error(
    error_string: String,
    error_type: RequestErrorType,
) -> *mut CommandResult {
    let c_err_str = CString::into_raw(
        CString::new(error_string).expect("Couldn't convert error message to CString"),
    );
    Box::into_raw(Box::new(CommandResult {
        response: std::ptr::null_mut(),
        command_error: Box::into_raw(Box::new(CommandError {
            command_error_message: c_err_str,
            command_error_type: error_type,
        })),
    }))
}

/// Converts a `RedisError` into a C-compatible error representation.
///
/// This helper function extracts the error message and error type,
/// and returns a raw C string pointer (`*const c_char`) along with
/// the corresponding [`RequestErrorType`].
///
/// # Parameters
/// - `err`: The `RedisError` to convert.
///
/// # Returns
/// A tuple containing:
/// - A raw C string (`*const c_char`) containing the error message.
/// - A `RequestErrorType` representing the kind of error.
///
/// # Panics
/// This function will panic if the error message cannot be converted into a `CString`.
fn to_c_error(err: RedisError) -> (*const c_char, RequestErrorType) {
    let message = errors::error_message(&err);
    let error_type = errors::error_type(&err);

    let c_err_str = CString::into_raw(
        CString::new(message).expect("Couldn't convert error message to CString"),
    );
    (c_err_str, error_type)
}

fn get_route(route: Routes, cmd: Option<&Cmd>) -> RedisResult<Option<RoutingInfo>> {
    use glide_core::command_request::routes::Value;
    let route = match route.value {
        Some(route) => route,
        None => return Ok(None),
    };
    let get_response_policy = |cmd: Option<&Cmd>| {
        cmd.and_then(|cmd| {
            cmd.command()
                .and_then(|cmd| ResponsePolicy::for_command(&cmd))
        })
    };
    match route {
        Value::SimpleRoutes(simple_route) => {
            let simple_route = match simple_route.enum_value() {
                Ok(simple_route) => simple_route,
                Err(value) => {
                    return Err(RedisError::from((
                        ErrorKind::ClientError,
                        "simple_route was not a valid enum variant",
                        format!("Value: {value}"),
                    )));
                }
            };
            match simple_route {
                SimpleRoutes::AllNodes => Ok(Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllNodes,
                    get_response_policy(cmd),
                )))),
                SimpleRoutes::AllPrimaries => Ok(Some(RoutingInfo::MultiNode((
                    MultipleNodeRoutingInfo::AllMasters,
                    get_response_policy(cmd),
                )))),
                SimpleRoutes::Random => {
                    Ok(Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)))
                }
            }
        }
        Value::SlotKeyRoute(slot_key_route) => Ok(Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                redis::cluster_topology::get_slot(slot_key_route.slot_key.as_bytes()),
                get_slot_addr(&slot_key_route.slot_type)?,
            )),
        ))),
        Value::SlotIdRoute(slot_id_route) => Ok(Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                slot_id_route.slot_id as u16,
                get_slot_addr(&slot_id_route.slot_type)?,
            )),
        ))),
        Value::ByAddressRoute(by_address_route) => match u16::try_from(by_address_route.port) {
            Ok(port) => Ok(Some(RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::ByAddress {
                    host: by_address_route.host.to_string(),
                    port,
                },
            ))),
            Err(_) => Err(RedisError::from((
                ErrorKind::ClientError,
                "by_address_route port could not be converted to u16.",
                format!("Value: {}", by_address_route.port),
            ))),
        },
        _ => Err(RedisError::from((
            ErrorKind::ClientError,
            "Unknown route type.",
            format!("Value: {route:?}"),
        ))),
    }
}

fn get_slot_addr(slot_type: &protobuf::EnumOrUnknown<SlotTypes>) -> RedisResult<SlotAddr> {
    let slot_addr_result = slot_type.enum_value().map(|slot_type| match slot_type {
        SlotTypes::Primary => SlotAddr::Master,
        SlotTypes::Replica => SlotAddr::ReplicaRequired,
    });
    slot_addr_result.map_err(|_| {
        RedisError::from((
            ErrorKind::ClientError,
            "Received unexpected slot id type",
            format!("Value: {slot_type:?}"),
        ))
    })
}

/// Allows the client to request a cluster scan command to be executed.
///
/// `client_adapter_ptr` is a pointer to a valid `GlideClusterClient` returned in the `ConnectionResponse` from [`create_client`].
/// `request_id` is a unique identifier for a valid payload buffer which is created in the client.
/// `cursor` is a cursor string.
/// `arg_count` keeps track of how many option arguments are passed in the client.
/// `args` is a pointer to C string representation of the string args.
/// `args_len` is a pointer to the lengths of the C string representation of the string args.
/// `success_callback` is the callback that will be called when a command succeeds.
/// `failure_callback` is the callback that will be called when a command fails.
///
/// # Safety
///
/// * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be valid until `close_client` is called.
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * `cursor` must not be null. It must point to a valid C string ([`CStr`]). See the safety documentation of [`CStr::from_ptr`].
/// * `cursor` must remain valid until the end of this call. The caller is responsible for freeing the memory allocated for this string.
/// * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn request_cluster_scan(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    cursor: *const c_char,
    arg_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };
    let cursor_id = unsafe { CStr::from_ptr(cursor) }
        .to_str()
        .unwrap_or("0")
        .to_owned();

    let cluster_scan_args: ClusterScanArgs = if arg_count > 0 {
        let arg_vec = unsafe {
            convert_double_pointer_to_vec(args as *const *const c_void, arg_count, args_len)
        };

        let mut pattern: &[u8] = &[];
        let mut object_type: &[u8] = &[];
        let mut count: &[u8] = &[];
        let mut allow_non_covered_slots: bool = false;

        let mut iter = arg_vec.iter().peekable();
        while let Some(arg) = iter.next() {
            match *arg {
                b"MATCH" => match iter.next() {
                    Some(pat) => pattern = pat,
                    None => {
                        let err = RedisError::from((
                            ErrorKind::ClientError,
                            "No argument following MATCH.",
                        ));
                        return unsafe { client_adapter.handle_redis_error(err, request_id) };
                    }
                },
                b"TYPE" => match iter.next() {
                    Some(obj_type) => object_type = obj_type,
                    None => {
                        let err = RedisError::from((
                            ErrorKind::ClientError,
                            "No argument following TYPE.",
                        ));
                        return unsafe { client_adapter.handle_redis_error(err, request_id) };
                    }
                },
                b"COUNT" => match iter.next() {
                    Some(c) => count = c,
                    None => {
                        let err = RedisError::from((
                            ErrorKind::ClientError,
                            "No argument following COUNT.",
                        ));
                        return unsafe { client_adapter.handle_redis_error(err, request_id) };
                    }
                },
                b"ALLOW_NON_COVERED_SLOTS" => {
                    allow_non_covered_slots = true;
                }
                _ => {
                    // Unknown or unsupported arg — safely skip or log
                    continue;
                }
            }
        }

        // Convert back to proper types
        let converted_count = match str::from_utf8(count) {
            Ok(v) => {
                if !count.is_empty() {
                    match str::parse::<u32>(v) {
                        Ok(v) => v,
                        Err(e) => {
                            return unsafe {
                                client_adapter.handle_redis_error(RedisError::from(e), request_id)
                            };
                        }
                    }
                } else {
                    10 // default count value
                }
            }
            Err(e) => {
                return unsafe {
                    client_adapter.handle_redis_error(RedisError::from(e), request_id)
                };
            }
        };

        let converted_type = match str::from_utf8(object_type) {
            Ok(v) => ObjectType::from(v.to_string()),
            Err(e) => {
                return unsafe {
                    client_adapter.handle_redis_error(RedisError::from(e), request_id)
                };
            }
        };

        let mut cluster_scan_args_builder = ClusterScanArgs::builder();
        if !count.is_empty() {
            cluster_scan_args_builder = cluster_scan_args_builder.with_count(converted_count);
        }
        if !pattern.is_empty() {
            cluster_scan_args_builder = cluster_scan_args_builder.with_match_pattern(pattern);
        }
        if !object_type.is_empty() {
            cluster_scan_args_builder = cluster_scan_args_builder.with_object_type(converted_type);
        }
        cluster_scan_args_builder =
            cluster_scan_args_builder.allow_non_covered_slots(allow_non_covered_slots);
        cluster_scan_args_builder.build()
    } else {
        ClusterScanArgs::builder().build()
    };

    let scan_state_cursor = if cursor_id.is_empty() || cursor_id == "0" {
        ScanStateRC::new()
    } else {
        match get_cluster_scan_cursor(cursor_id) {
            Ok(existing_cursor) => existing_cursor,
            Err(err) => {
                return unsafe { client_adapter.handle_redis_error(err, request_id) };
            }
        }
    };
    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        client
            .cluster_scan(&scan_state_cursor, cluster_scan_args)
            .await
    })
}

/// Remove a cluster scan cursor from the container.
///
/// `cursor_id` is the cursor ID returned by a previous cluster scan operation.
///
/// # Safety
/// * `cursor_id` must point to a valid C string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn remove_cluster_scan_cursor(cursor_id: *const c_char) {
    if cursor_id.is_null() {
        return;
    }

    if let Ok(cursor_str) = unsafe { CStr::from_ptr(cursor_id).to_str() } {
        glide_core::cluster_scan_container::remove_scan_state_cursor(cursor_str.to_string());
    }
}

/// Allows the client to request an update to the connection password.
///
/// `client_adapter_ptr` is a pointer to a valid `GlideClusterClient` returned in the `ConnectionResponse` from [`create_client`].
/// `request_id` is a unique identifier for a valid payload buffer which is created in the client.
/// `password` is a pointer to C string representation of the password.
/// `immediate_auth` is a boolean flag to indicate if the password should be updated immediately.
/// `success_callback` is the callback that will be called when a command succeeds.
/// `failure_callback` is the callback that will be called when a command fails.
///
/// # Safety
///
/// * `client_adapter_ptr` must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be valid until `close_client` is called.
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * Both the `success_callback` and `failure_callback` function pointers need to live while the client is open/active. The caller is responsible for freeing both callbacks.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn update_connection_password(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    password: *const c_char,
    immediate_auth: bool,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    // argument conversion to be used in the async block
    let password = match unsafe { CStr::from_ptr(password).to_str() } {
        Ok(password) => password,
        Err(e) => {
            return unsafe { client_adapter.handle_redis_error(RedisError::from(e), request_id) };
        }
    };
    let password_option = if password.is_empty() {
        None
    } else {
        Some(password.to_string())
    };
    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        client
            .update_connection_password(password_option, immediate_auth)
            .await
    })
}

/// Manually refresh the IAM authentication token.
///
/// This function triggers an immediate refresh of the IAM token and updates the connection.
/// It is only available if the client was created with IAM authentication.
///
/// # Parameters
///
/// * `client_adapter_ptr`: Pointer to a valid client returned from [`create_client`].
/// * `request_id`: Unique identifier for a valid payload buffer created in the calling language.
///
/// # Returns
///
/// * A pointer to a [`CommandResult`] containing "OK" on success, or an error if:
///   - The client is not using IAM authentication
///   - Token generation fails
///   - Authentication with the new token fails
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`].
/// * `request_id` must be valid until it is passed in a call to [`free_command_response`].
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn refresh_iam_token(
    client_adapter_ptr: *const c_void,
    request_id: usize,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        client.refresh_iam_token().await.map(|_| Value::Okay)
    })
}

/// Executes a Lua script.
///
/// # Parameters
///
/// * `client_adapter_ptr`: Pointer to a valid `GlideClusterClient` returned from [`create_client`].
/// * `request_id`: Unique identifier for a valid payload buffer created in the calling language.
/// * `hash`: SHA1 hash of the script for script caching.
/// * `keys_count`: Number of keys in the keys array.
/// * `keys`: Array of keys used by the script.
/// * `keys_len`: Array of lengths for each key.
/// * `args_count`: Number of arguments in the args array.
/// * `args`: Array of arguments to pass to the script.
/// * `args_len`: Array of lengths for each argument.
/// * `route_bytes`: Optional array of bytes for routing information.
/// * `route_bytes_len`: Length of the route_bytes array.
///
/// # Safety
///
/// * `client_adapter_ptr` must not be `null` and must be obtained from the `ConnectionResponse` returned from [`create_client`].
/// * `client_adapter_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`].
/// * `request_id` must be valid until either `success_callback` or `failure_callback` is finished.
/// * `hash` must be a valid null-terminated C string.
/// * `keys` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `keys_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `keys_count` must be 0 if `keys` and `keys_len` are null.
/// * `keys` and `keys_len` must either be both null or be both not null.
/// * `args` is an optional bytes pointers array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_len` is an optional bytes length array. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `args_count` must be 0 if `args` and `args_len` are null.
/// * `args` and `args_len` must either be both null or be both not null.
/// * `route_bytes` is an optional array of bytes that will be parsed into a Protobuf `Routes` object. The array must be allocated by the caller and subsequently freed by the caller after this function returns.
/// * `route_bytes_len` is the number of bytes in `route_bytes`. It must also not be greater than the max value of a signed pointer-sized integer.
/// * `route_bytes_len` must be 0 if `route_bytes` is null.
/// * This function should only be called with a `client_adapter_ptr` created by [`create_client`], before [`close_client`] was called with the pointer.
#[unsafe(no_mangle)]
pub unsafe extern "C-unwind" fn invoke_script(
    client_adapter_ptr: *const c_void,
    request_id: usize,
    hash: *const c_char,
    keys_count: c_ulong,
    keys: *const usize,
    keys_len: *const c_ulong,
    args_count: c_ulong,
    args: *const usize,
    args_len: *const c_ulong,
    route_bytes: *const u8,
    route_bytes_len: usize,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_adapter_ptr);
        Arc::from_raw(client_adapter_ptr as *mut ClientAdapter)
    };

    // Convert hash to Rust string
    let hash_str = match unsafe { CStr::from_ptr(hash).to_str() } {
        Ok(hash) => hash,
        Err(e) => {
            return unsafe { client_adapter.handle_redis_error(RedisError::from(e), request_id) };
        }
    };

    // Convert keys to Vec<&[u8]>
    let keys_vec: Vec<&[u8]> = if !keys.is_null() && !keys_len.is_null() && keys_count > 0 {
        unsafe { convert_double_pointer_to_vec(keys as *const *const c_void, keys_count, keys_len) }
    } else {
        Vec::new()
    };

    // Convert args to Vec<&[u8]>
    let args_vec: Vec<&[u8]> = if !args.is_null() && !args_len.is_null() && args_count > 0 {
        unsafe { convert_double_pointer_to_vec(args as *const *const c_void, args_count, args_len) }
    } else {
        Vec::new()
    };

    // Parse routing information if provided
    let route = if !route_bytes.is_null() {
        let r_bytes = unsafe { std::slice::from_raw_parts(route_bytes, route_bytes_len) };
        match Routes::parse_from_bytes(r_bytes) {
            Ok(route) => route,
            Err(err) => {
                let err = RedisError::from((
                    ErrorKind::ClientError,
                    "Decoding route failed",
                    err.to_string(),
                ));
                return unsafe { client_adapter.handle_redis_error(err, request_id) };
            }
        }
    } else {
        Routes::default()
    };

    let mut client = client_adapter.core.client.clone();
    client_adapter.execute_request(request_id, async move {
        let routing_info = get_route(route, None)?;
        client
            .invoke_script(hash_str, &keys_vec, &args_vec, routing_info)
            .await
    })
}

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum RouteType {
    AllNodes = 0,
    AllPrimaries,
    Random,
    SlotId,
    SlotKey,
    ByAddress,
}

/// A mirror of [`SlotAddr`]
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum SlotType {
    Primary = 0,
    Replica,
}

impl From<&SlotType> for SlotAddr {
    fn from(val: &SlotType) -> Self {
        match val {
            SlotType::Primary => SlotAddr::Master,
            SlotType::Replica => SlotAddr::ReplicaRequired,
        }
    }
}

/// A structure which represents a route. To avoid extra pointer mandgling, it has fields for all route types.
/// Depending on [`RouteType`], the struct stores:
/// * Only `route_type` is filled, if route is a simple route;
/// * `route_type`, `slot_id` and `slot_type`, if route is a Slot ID route;
/// * `route_type`, `slot_key` and `slot_type`, if route is a Slot key route;
/// * `route_type`, `hostname` and `port`, if route is a Address route;
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct RouteInfo {
    pub route_type: RouteType,
    pub slot_id: i32,
    /// zero pointer is valid, means no slot key is given (`None`)
    pub slot_key: *const c_char,
    pub slot_type: SlotType,
    /// zero pointer is valid, means no hostname is given (`None`)
    pub hostname: *const c_char,
    pub port: i32,
}

#[repr(C)]
#[derive(Clone, Debug, Copy)]
pub struct CmdInfo {
    pub request_type: RequestType,
    pub args: *const *const u8,
    pub arg_count: usize,
    pub args_len: *const usize,
}

#[repr(C)]
#[derive(Clone, Debug, Copy)]
pub struct BatchInfo {
    pub cmd_count: usize,
    pub cmds: *const *const CmdInfo,
    pub is_atomic: bool,
}

#[repr(C)]
#[derive(Clone, Debug, Copy)]
pub struct BatchOptionsInfo {
    // two params from PipelineRetryStrategy
    pub retry_server_error: bool,
    pub retry_connection_error: bool,
    pub has_timeout: bool,
    pub timeout: u32,
    pub route_info: *const RouteInfo,
}

/// Execute a batch.
///
/// # Safety
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Arc<ClientAdapter>`] via [`Arc::from_raw`]. See the safety documentation of [`Box::from_raw`].
/// * This function should only be called should with a pointer created by [`create_client`], before [`close_client`] was called with the pointer.
/// * `batch_ptr` must not be `null`.
/// * `batch_ptr` must be able to be safely casted to a valid [`BatchInfo`]. See the safety documentation of [`create_pipeline`].
/// * `options_ptr` could be `null`, but if it is not `null`, it must be a valid [`BatchOptionsInfo`] pointer. See the safety documentation of [`get_pipeline_options`].
#[allow(rustdoc::private_intra_doc_links)]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn batch(
    client_ptr: *const c_void,
    callback_index: usize,
    batch_ptr: *const BatchInfo,
    raise_on_error: bool,
    options_ptr: *const BatchOptionsInfo,
    span_ptr: u64,
) -> *mut CommandResult {
    let client_adapter = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_ptr);
        Arc::from_raw(client_ptr as *mut ClientAdapter)
    };
    let mut client = client_adapter.core.client.clone();

    // TODO handle panics
    let mut pipeline = match unsafe { create_pipeline(batch_ptr) } {
        Ok(pipeline) => pipeline,
        Err(err) => {
            return unsafe {
                client_adapter.handle_custom_error(
                    err,
                    RequestErrorType::Unspecified,
                    callback_index,
                )
            };
        }
    };
    if span_ptr != 0 {
        pipeline.set_pipeline_span(unsafe { get_unsafe_span_from_ptr(Some(span_ptr)) });
    }
    let child_span = create_child_span(pipeline.span().as_ref(), "send_batch");
    let (routing, timeout, pipeline_retry_strategy) = unsafe { get_pipeline_options(options_ptr) };

    let result = client_adapter.execute_request(callback_index, async move {
        if pipeline.is_atomic() {
            client
                .send_transaction(&pipeline, routing, timeout, raise_on_error)
                .await
        } else {
            client
                .send_pipeline(
                    &pipeline,
                    routing,
                    raise_on_error,
                    timeout,
                    pipeline_retry_strategy,
                )
                .await
        }
    });

    if let Ok(span) = child_span {
        span.end();
    }
    result
}

/// Convert raw C string to a rust string.
///
/// # Safety
///
/// * `ptr` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
unsafe fn ptr_to_str(ptr: *const c_char) -> String {
    if !ptr.is_null() {
        unsafe { CStr::from_ptr(ptr) }.to_str().unwrap().into()
    } else {
        "".into()
    }
}

/// Convert route configuration to a corresponding object.
///
/// # Safety
/// * `route_ptr` could be `null`, but if it is not `null`, it must be a valid pointer to a [`RouteInfo`] struct.
/// * `slot_key` and `hostname` in dereferenced [`RouteInfo`] struct must contain valid string pointers when corresponding `route_type` is set.
///   See description of [`RouteInfo`] and the safety documentation of [`ptr_to_str`].
pub(crate) unsafe fn create_route(
    route_ptr: *const RouteInfo,
    cmd: Option<&Cmd>,
) -> Option<RoutingInfo> {
    if route_ptr.is_null() {
        return None;
    }
    let route = unsafe { *route_ptr };
    match route.route_type {
        RouteType::Random => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
        RouteType::AllNodes => Some(RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllNodes,
            cmd.and_then(|c| ResponsePolicy::for_command(&c.command().unwrap())),
        ))),
        RouteType::AllPrimaries => Some(RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllMasters,
            cmd.and_then(|c| ResponsePolicy::for_command(&c.command().unwrap())),
        ))),
        RouteType::SlotId => Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                route.slot_id as u16,
                (&route.slot_type).into(),
            )),
        )),
        RouteType::SlotKey => Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                redis::cluster_topology::get_slot(unsafe { ptr_to_str(route.slot_key) }.as_bytes()),
                (&route.slot_type).into(),
            )),
        )),
        RouteType::ByAddress => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
            host: unsafe { ptr_to_str(route.hostname) },
            port: route.port as u16,
        })),
    }
}

/// Convert [`CmdInfo`] to a [`Cmd`].
///
/// # Safety
/// * `cmd_ptr` must be able to be safely casted to a valid [`CmdInfo`]
/// * `args` and `args_len` in a referred [`CmdInfo`] structure must not be `null`.
/// * `data` in a referred [`CmdInfo`] structure must point to `arg_count` consecutive string pointers.
/// * `args_len` in a referred [`CmdInfo`] structure must point to `arg_count` consecutive string lengths. See the safety documentation of [`convert_double_pointer_to_vec`].
pub(crate) unsafe fn create_cmd(ptr: *const CmdInfo) -> Result<Cmd, String> {
    let info = unsafe { *ptr };
    let arg_vec = unsafe {
        convert_double_pointer_to_vec(
            info.args as *const *const c_void,
            info.arg_count as c_ulong,
            info.args_len as *const c_ulong,
        )
    };

    let Some(mut cmd) = info.request_type.get_command() else {
        return Err("Couldn't fetch command type".into());
    };
    for command_arg in arg_vec {
        cmd.arg(command_arg);
    }
    Ok(cmd)
}

/// Convert [`BatchInfo`] to a [`Pipeline`].
///
/// # Safety
/// * `ptr` must be able to be safely casted to a valid [`BatchInfo`].
/// * `cmds` in a referred [`BatchInfo`] structure must not be `null`.
/// * `cmds` in a referred [`BatchInfo`] structure must point to `cmd_count` consecutive [`CmdInfo`] pointers.
///   They must be able to be safely casted to a valid to a slice of the corresponding type via [`from_raw_parts`]. See the safety documentation of [`from_raw_parts`].
/// * Every pointer stored in `cmds` must not be `null` and must point to a valid [`CmdInfo`] structure.
/// * All data in referred [`CmdInfo`] structure(s) should be valid. See the safety documentation of [`create_cmd`].
pub(crate) unsafe fn create_pipeline(ptr: *const BatchInfo) -> Result<Pipeline, String> {
    let info = unsafe { *ptr };
    let cmd_pointers = unsafe { from_raw_parts(info.cmds, info.cmd_count) };
    let mut pipeline = Pipeline::with_capacity(info.cmd_count);
    for (i, cmd_ptr) in cmd_pointers.iter().enumerate() {
        match unsafe { create_cmd(*cmd_ptr) } {
            Ok(cmd) => pipeline.add_command(cmd),
            Err(err) => return Err(format!("Coudln't create {i:?}'th command: {err:?}")),
        };
    }
    if info.is_atomic {
        pipeline.atomic();
    }

    Ok(pipeline)
}

/// Convert [`BatchOptionsInfo`] to a tuple of corresponding values.
///
/// # Safety
/// * `ptr` could be `null`, but if it is not `null`, it must be a valid pointer to a [`BatchOptionsInfo`] struct.
/// * `route_info` in dereferenced [`BatchOptionsInfo`] struct must contain a [`RouteInfo`] pointer.
///   See description of [`RouteInfo`] and the safety documentation of [`create_route`].
pub(crate) unsafe fn get_pipeline_options(
    ptr: *const BatchOptionsInfo,
) -> (Option<RoutingInfo>, Option<u32>, PipelineRetryStrategy) {
    if ptr.is_null() {
        return (None, None, PipelineRetryStrategy::new(false, false));
    }
    let info = unsafe { *ptr };
    let timeout = if info.has_timeout {
        Some(info.timeout)
    } else {
        None
    };
    let route = unsafe { create_route(info.route_info, None) };

    (
        route,
        timeout,
        PipelineRetryStrategy::new(info.retry_server_error, info.retry_connection_error),
    )
}

/// Creates an OpenTelemetry span with the given name and returns a pointer to the span as u64.
///
#[unsafe(no_mangle)]
pub extern "C" fn create_otel_span(request_type: RequestType) -> u64 {
    // Validate request type and extract command
    let cmd = match request_type.get_command() {
        Some(cmd) => cmd,
        None => {
            logger_core::log_error(
                "ffi_otel",
                "create_otel_span: RequestType has no command available",
            );
            return 0;
        }
    };

    // Validate command bytes
    let cmd_bytes = match cmd.command() {
        Some(bytes) => bytes,
        None => {
            logger_core::log_error(
                "ffi_otel",
                "create_otel_span: Command has no bytes available",
            );
            return 0;
        }
    };

    // Validate UTF-8 encoding
    let command_name = match std::str::from_utf8(cmd_bytes.as_slice()) {
        Ok(name) => name,
        Err(e) => {
            logger_core::log_error(
                "ffi_otel",
                format!("create_otel_span: Command bytes are not valid UTF-8: {e}"),
            );
            return 0;
        }
    };

    // Validate command name length (reasonable limit to prevent abuse)
    if command_name.len() > 256 {
        logger_core::log_error(
            "ffi_otel",
            format!(
                "create_otel_span: Command name too long ({} chars), max 256",
                command_name.len()
            ),
        );
        return 0;
    }

    // Create span and convert to pointer
    let span = GlideOpenTelemetry::new_span(command_name);
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_otel_span: Successfully created span '{command_name}' with pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with a fixed name "batch" and returns a pointer to the span as u64.
///
#[unsafe(no_mangle)]
pub extern "C" fn create_batch_otel_span() -> u64 {
    let command_name = "Batch";

    // Create span and convert to pointer
    let span = GlideOpenTelemetry::new_span(command_name);
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_batch_otel_span: Successfully created batch span with pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry batch span with a parent span and returns a pointer to the span as u64.
/// This function creates a child span with the name "Batch" under the provided parent span.
/// Returns 0 on failure.
///
/// # Parameters
/// * `parent_span_ptr`: A u64 pointer to the parent span created by create_otel_span, create_named_otel_span, or create_batch_otel_span
///
/// # Returns
/// * A u64 pointer to the created child batch span, or 0 if creation fails
///
/// # Safety
/// * `parent_span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`], [`create_named_otel_span`], or [`create_batch_otel_span`], or 0.
/// * If `parent_span_ptr` is 0 or invalid, the function will create an independent batch span as fallback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_batch_otel_span_with_parent(parent_span_ptr: u64) -> u64 {
    let command_name = "Batch";

    // Handle parent span pointer validation with graceful fallback
    if parent_span_ptr == 0 {
        logger_core::log_warn(
            "ffi_otel",
            "create_batch_otel_span_with_parent: parent_span_ptr is null (0), creating independent batch span as fallback",
        );
        // Graceful fallback: create independent batch span
        let span = GlideOpenTelemetry::new_span(command_name);
        let arc = Arc::new(span);
        let ptr = Arc::into_raw(arc);
        let span_ptr = ptr as u64;
        logger_core::log_debug(
            "ffi_otel",
            format!(
                "create_batch_otel_span_with_parent: Created independent fallback batch span with pointer 0x{span_ptr:x}",
            ),
        );
        return span_ptr;
    }

    // Convert parent pointer to GlideSpan and use existing add_span method
    let span = match unsafe { GlideOpenTelemetry::span_from_pointer(parent_span_ptr) } {
        Ok(parent_span) => {
            // Use existing add_span method to create child batch span
            match parent_span.add_span(command_name) {
                Ok(child_span) => child_span,
                Err(e) => {
                    logger_core::log_warn(
                        "ffi_otel",
                        format!(
                            "create_batch_otel_span_with_parent: Failed to create child batch span with parent 0x{parent_span_ptr:x}: {e}. Creating independent batch span as fallback.",
                        ),
                    );
                    // Graceful fallback: create independent batch span
                    GlideOpenTelemetry::new_span(command_name)
                }
            }
        }
        Err(e) => {
            logger_core::log_warn(
                "ffi_otel",
                format!(
                    "create_batch_otel_span_with_parent: Invalid parent span pointer 0x{parent_span_ptr:x}: {e}. Creating independent batch span as fallback.",
                ),
            );
            // Graceful fallback: create independent batch span
            GlideOpenTelemetry::new_span(command_name)
        }
    };

    // Convert span to pointer and return
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_batch_otel_span_with_parent: Successfully created batch span with parent 0x{parent_span_ptr:x}, child pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with a custom name and returns a pointer to the span as u64.
/// This function is intended for creating parent spans that can be used with create_otel_span_with_parent.
/// Returns 0 on failure.
///
/// # Parameters
/// * `span_name`: A null-terminated C string containing the name for the span
///
/// # Returns
/// * A u64 pointer to the created span, or 0 if creation fails
///
/// # Safety
/// * `span_name` must be a valid pointer to a null-terminated C string
/// * The string must be valid UTF-8
/// * The caller is responsible for eventually calling drop_otel_span with the returned pointer
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_named_otel_span(span_name: *const c_char) -> u64 {
    // Validate input pointer
    if span_name.is_null() {
        logger_core::log_error(
            "ffi_otel",
            "create_named_otel_span: span_name pointer is null",
        );
        return 0;
    }

    // Convert C string to Rust string with safe error handling
    let c_str = unsafe { CStr::from_ptr(span_name) };

    let name_str = match c_str.to_str() {
        Ok(s) => s,
        Err(e) => {
            logger_core::log_error(
                "ffi_otel",
                format!("create_named_otel_span: span_name is not valid UTF-8: {e}",),
            );
            return 0;
        }
    };

    // Validate string length (reasonable limit to prevent abuse)
    // Note: Empty names are allowed as per test expectations
    if name_str.len() > 256 {
        logger_core::log_error(
            "ffi_otel",
            format!(
                "create_named_otel_span: span_name too long ({} chars), max 256",
                name_str.len()
            ),
        );
        return 0;
    }

    // Validate string content (basic sanity check for control characters)
    if name_str
        .chars()
        .any(|c| c.is_control() && c != '\t' && c != '\n' && c != '\r')
    {
        logger_core::log_error(
            "ffi_otel",
            "create_named_otel_span: span_name contains invalid control characters",
        );
        return 0;
    }

    // Create the named span using existing new_span method
    let span = GlideOpenTelemetry::new_span(name_str);
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_named_otel_span: Successfully created named span '{name_str}' with pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Creates an OpenTelemetry span with the given request type as a child of the provided parent span.
/// Returns a pointer to the child span as u64, or 0 on failure.
///
/// # Parameters
/// * `request_type`: The type of request to create a span for
/// * `parent_span_ptr`: A pointer to the parent span (created by create_otel_span or create_named_otel_span)
///
/// # Returns
/// * A u64 pointer to the created child span, or 0 if creation fails
///
/// # Safety
/// * `parent_span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`], [`create_named_otel_span`], or [`create_batch_otel_span`], or 0.
/// * If `parent_span_ptr` is 0 or invalid, the function will create an independent span as fallback.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn create_otel_span_with_parent(
    request_type: RequestType,
    parent_span_ptr: u64,
) -> u64 {
    // Validate request type and extract command first (this should fail hard)
    let cmd = match request_type.get_command() {
        Some(cmd) => cmd,
        None => {
            logger_core::log_error(
                "ffi_otel",
                "create_otel_span_with_parent: RequestType has no command available",
            );
            return 0;
        }
    };

    // Validate command bytes
    let cmd_bytes = match cmd.command() {
        Some(bytes) => bytes,
        None => {
            logger_core::log_error(
                "ffi_otel",
                "create_otel_span_with_parent: Command has no bytes available",
            );
            return 0;
        }
    };

    // Validate UTF-8 encoding
    let command_name = match std::str::from_utf8(cmd_bytes.as_slice()) {
        Ok(name) => name,
        Err(e) => {
            logger_core::log_error(
                "ffi_otel",
                format!("create_otel_span_with_parent: Command bytes are not valid UTF-8: {e}",),
            );
            return 0;
        }
    };

    // Validate command name length (reasonable limit to prevent abuse)
    if command_name.len() > 256 {
        logger_core::log_error(
            "ffi_otel",
            format!(
                "create_otel_span_with_parent: Command name too long ({} chars), max 256",
                command_name.len()
            ),
        );
        return 0;
    }

    // Handle parent span pointer validation with graceful fallback
    if parent_span_ptr == 0 {
        logger_core::log_warn(
            "ffi_otel",
            "create_otel_span_with_parent: parent_span_ptr is null (0), creating independent span as fallback",
        );
        // Graceful fallback: create independent span
        let span = GlideOpenTelemetry::new_span(command_name);
        let arc = Arc::new(span);
        let ptr = Arc::into_raw(arc);
        let span_ptr = ptr as u64;
        logger_core::log_debug(
            "ffi_otel",
            format!(
                "create_otel_span_with_parent: Created independent fallback span '{command_name}' with pointer 0x{span_ptr:x}",
            ),
        );
        return span_ptr;
    }

    // Convert parent pointer to GlideSpan and use existing add_span method
    let span = match unsafe { GlideOpenTelemetry::span_from_pointer(parent_span_ptr) } {
        Ok(parent_span) => {
            // Use existing add_span method to create child span
            match parent_span.add_span(command_name) {
                Ok(child_span) => child_span,
                Err(e) => {
                    logger_core::log_warn(
                        "ffi_otel",
                        format!(
                            "create_otel_span_with_parent: Failed to create child span '{command_name}' with parent 0x{parent_span_ptr:x}: {e}. Creating independent span as fallback.",
                        ),
                    );
                    // Graceful fallback: create independent span
                    GlideOpenTelemetry::new_span(command_name)
                }
            }
        }
        Err(e) => {
            logger_core::log_warn(
                "ffi_otel",
                format!(
                    "create_otel_span_with_parent: Invalid parent span pointer 0x{parent_span_ptr:x}: {e}. Creating independent span as fallback.",
                ),
            );
            // Graceful fallback: create independent span
            GlideOpenTelemetry::new_span(command_name)
        }
    };

    // Convert span to pointer and return
    let arc = Arc::new(span);
    let ptr = Arc::into_raw(arc);
    let span_ptr = ptr as u64;

    logger_core::log_debug(
        "ffi_otel",
        format!(
            "create_otel_span_with_parent: Successfully created span '{command_name}' with parent 0x{parent_span_ptr:x}, child pointer 0x{span_ptr:x}",
        ),
    );
    span_ptr
}

/// Drops an OpenTelemetry span given its pointer as u64.
///
/// # Safety
/// * `span_ptr` must be a valid pointer to a [`Arc<GlideSpan>`] span created by [`create_otel_span`] or `0`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn drop_otel_span(span_ptr: u64) {
    // Validate span pointer
    if span_ptr == 0 {
        logger_core::log_debug("ffi_otel", "drop_otel_span: Ignoring null span pointer (0)");
        return;
    }

    // Validate pointer alignment and bounds (basic safety checks)
    if !span_ptr.is_multiple_of(8) {
        logger_core::log_error(
            "ffi_otel",
            format!("drop_otel_span: Invalid span pointer - misaligned: 0x{span_ptr:x}",),
        );
        return;
    }

    // Check for obviously invalid pointer values
    const MIN_VALID_ADDRESS: u64 = 0x1000; // 4KB, below this is likely invalid
    const MAX_VALID_ADDRESS: u64 = 0x7FFF_FFFF_FFFF_FFF8; // Max user space on most 64-bit systems

    if span_ptr < MIN_VALID_ADDRESS {
        logger_core::log_error(
            "ffi_otel",
            format!("drop_otel_span: Invalid span pointer - address too low: 0x{span_ptr:x}",),
        );
        return;
    }

    if span_ptr > MAX_VALID_ADDRESS {
        logger_core::log_error(
            "ffi_otel",
            format!("drop_otel_span: Invalid span pointer - address too high: 0x{span_ptr:x}",),
        );
        return;
    }

    // Attempt to safely drop the span
    unsafe {
        // Use std::panic::catch_unwind to handle potential panics during Arc::from_raw
        let result = std::panic::catch_unwind(|| {
            Arc::from_raw(span_ptr as *const GlideSpan);
        });

        match result {
            Ok(_) => {
                logger_core::log_debug(
                    "ffi_otel",
                    format!(
                        "drop_otel_span: Successfully dropped span with pointer 0x{span_ptr:x}",
                    ),
                );
            }
            Err(_) => {
                logger_core::log_error(
                    "ffi_otel",
                    format!(
                        "drop_otel_span: Panic occurred while dropping span pointer 0x{span_ptr:x} - likely invalid pointer",
                    ),
                );
            }
        }
    }
}

/// Configuration for OpenTelemetry integration in the Node.js client.
///
/// This struct allows you to configure how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
/// - `traces`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
/// - `metrics`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
/// - `flush_interval_ms`: Optional interval in milliseconds between consecutive exports of telemetry data. If `None`, a default value will be used.
///
/// At least one of traces or metrics must be provided.
#[repr(C)]
#[derive(Clone, Debug)]
pub struct OpenTelemetryConfig {
    /// Configuration for exporting trace data. Only valid if has_traces is true.
    pub traces: *const OpenTelemetryTracesConfig,
    /// Configuration for exporting metrics data. Only valid if has_metrics is true.
    pub metrics: *const OpenTelemetryMetricsConfig,
    /// Whether flush interval is specified
    pub has_flush_interval_ms: bool,
    /// Interval in milliseconds between consecutive exports of telemetry data. Only valid if has_flush_interval_ms is true.
    pub flush_interval_ms: i64,
}

/// Configuration for exporting OpenTelemetry traces.
///
/// - `endpoint`: The endpoint to which trace data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
/// - `has_sample_percentage`: Whether sample percentage is specified
/// - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration. Only valid if has_sample_percentage is true.
#[repr(C)]
#[derive(Clone, Debug)]
pub struct OpenTelemetryTracesConfig {
    /// The endpoint to which trace data will be exported, `null` if not specified.
    pub endpoint: *const c_char,
    /// Whether sample percentage is specified
    pub has_sample_percentage: bool,
    /// The percentage of requests to sample and create a span for, used to measure command duration. Only valid if has_sample_percentage is true.
    pub sample_percentage: u32,
}

/// Configuration for exporting OpenTelemetry metrics.
///
/// - `endpoint`: The endpoint to which metrics data will be exported. Expected format:
///   - For gRPC: `grpc://host:port`
///   - For HTTP: `http://host:port` or `https://host:port`
///   - For file exporter: `file:///absolute/path/to/folder/file.json`
#[repr(C)]
#[derive(Clone, Debug)]
pub struct OpenTelemetryMetricsConfig {
    /// The endpoint to which metrics data will be exported, `null` if not specified.
    pub endpoint: *const c_char,
}

/// Initializes OpenTelemetry with the given configuration.
///
/// # Safety
/// * `open_telemetry_config` and its underlying traces and metrics pointers must be valid until the function returns.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn init_open_telemetry(
    open_telemetry_config: *const OpenTelemetryConfig,
) -> *const c_char {
    // At least one of traces or metrics must be provided
    if unsafe { (*open_telemetry_config).traces.is_null() }
        && unsafe { (*open_telemetry_config).metrics.is_null() }
    {
        let error_msg =
            "At least one of traces or metrics must be provided for OpenTelemetry configuration";
        return CString::new(error_msg)
            .unwrap_or_else(|_| CString::new("Couldn't convert error message to C string").unwrap())
            .into_raw();
    }

    let mut config = GlideOpenTelemetryConfigBuilder::default();

    // Initialize open telemetry traces exporter
    if !unsafe { (*open_telemetry_config).traces.is_null() } {
        let endpoint = unsafe { CStr::from_ptr((*(*open_telemetry_config).traces).endpoint) }
            .to_string_lossy()
            .to_string();
        match GlideOpenTelemetrySignalsExporter::from_str(&endpoint) {
            Ok(exporter) => {
                let sample_percentage =
                    if unsafe { (*(*open_telemetry_config).traces).has_sample_percentage } {
                        Some(unsafe { (*(*open_telemetry_config).traces).sample_percentage })
                    } else {
                        None
                    };
                config = config.with_trace_exporter(exporter, sample_percentage);
            }
            Err(e) => {
                let error_msg = format!("Invalid traces exporter configuration: {e}");
                return CString::new(error_msg)
                    .unwrap_or_else(|_| {
                        CString::new("Couldn't convert error message to C string").unwrap()
                    })
                    .into_raw();
            }
        }
    }

    // Initialize open telemetry metrics exporter
    if !unsafe { (*open_telemetry_config).metrics.is_null() } {
        let endpoint = unsafe { CStr::from_ptr((*(*open_telemetry_config).metrics).endpoint) }
            .to_string_lossy()
            .to_string();
        match GlideOpenTelemetrySignalsExporter::from_str(&endpoint) {
            Ok(exporter) => {
                config = config.with_metrics_exporter(exporter);
            }
            Err(e) => {
                let error_msg = format!("Invalid metrics exporter configuration: {e}");
                return CString::new(error_msg)
                    .unwrap_or_else(|_| {
                        CString::new("Couldn't convert error message to C string").unwrap()
                    })
                    .into_raw();
            }
        }
    }

    let flush_interval_ms = if unsafe { (*open_telemetry_config).has_flush_interval_ms } {
        unsafe { (*open_telemetry_config).flush_interval_ms }
    } else {
        DEFAULT_FLUSH_SIGNAL_INTERVAL_MS as i64
    };

    if flush_interval_ms <= 0 {
        let error_msg = format!(
            "InvalidInput: flushIntervalMs must be a positive integer (got: {flush_interval_ms})"
        );
        return CString::new(error_msg)
            .unwrap_or_else(|_| CString::new("Couldn't convert error message to C string").unwrap())
            .into_raw();
    }

    config = config.with_flush_interval(std::time::Duration::from_millis(flush_interval_ms as u64));

    // Initialize OpenTelemetry synchronously
    match glide_core::client::get_or_init_runtime() {
        Ok(glide_runtime) => {
            match glide_runtime
                .runtime
                .block_on(async { GlideOpenTelemetry::initialise(config.build()) })
            {
                Ok(_) => std::ptr::null(), // Success
                Err(e) => {
                    let error_msg = format!("Failed to initialize OpenTelemetry: {e}");
                    CString::new(error_msg)
                        .unwrap_or_else(|_| {
                            CString::new("Couldn't convert error message to C string").unwrap()
                        })
                        .into_raw()
                }
            }
        }
        Err(e) => CString::new(e)
            .unwrap_or_else(|_| CString::new("Couldn't convert error message to C string").unwrap())
            .into_raw(),
    }
}

/// Frees a C string.
///
/// # Safety
/// * `s` must be a valid pointer to a C string or `null`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_c_string(s: *mut c_char) {
    unsafe {
        if s.is_null() {
            return;
        }
        drop(CString::from_raw(s));
    };
}

/// This function converts a raw pointer to a GlideSpan into a safe Rust reference.
/// It handles the unsafe pointer operations internally, incrementing the reference count
/// to ensure the span remains valid while in use.
///
/// # Safety
///
/// This function is marked as unsafe because it dereferences a raw pointer. The caller
/// must ensure that:
/// * The pointer is valid and points to a properly allocated GlideSpan
/// * The pointer is properly aligned
/// * The data pointed to is not modified while the returned reference is in use
/// * The pointer is not used after the referenced data is dropped
///
/// # Arguments
///
/// * `command_span` - An optional raw pointer (as u64) to a GlideSpan
///
/// # Returns
///
/// * `Some(GlideSpan)` - A cloned GlideSpan if the pointer is valid
/// * `None` - If the pointer is None
unsafe fn get_unsafe_span_from_ptr(command_span: Option<u64>) -> Option<GlideSpan> {
    command_span.map(|command_span| unsafe {
        Arc::increment_strong_count(command_span as *const GlideSpan);
        (*Arc::from_raw(command_span as *const GlideSpan)).clone()
    })
}

/// Creates a child span for telemetry if telemetry is enabled
fn create_child_span(span: Option<&GlideSpan>, name: &str) -> Result<GlideSpan, String> {
    // Early return if no parent span is provided
    let parent_span = span.ok_or_else(|| "No parent span provided".to_string())?;

    match parent_span.add_span(name) {
        Ok(child_span) => Ok(child_span),
        Err(error_msg) => Err(format!(
            "Opentelemetry failed to create child span with name `{name}`. Error: {error_msg:?}"
        )),
    }
}

#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    ERROR = 0,
    WARN = 1,
    INFO = 2,
    DEBUG = 3,
    TRACE = 4,
    OFF = 5,
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level::ERROR,
            logger_core::Level::Warn => Level::WARN,
            logger_core::Level::Info => Level::INFO,
            logger_core::Level::Debug => Level::DEBUG,
            logger_core::Level::Trace => Level::TRACE,
            logger_core::Level::Off => Level::OFF,
        }
    }
}

impl From<Level> for logger_core::Level {
    fn from(level: Level) -> Self {
        match level {
            Level::ERROR => logger_core::Level::Error,
            Level::WARN => logger_core::Level::Warn,
            Level::INFO => logger_core::Level::Info,
            Level::DEBUG => logger_core::Level::Debug,
            Level::TRACE => logger_core::Level::Trace,
            Level::OFF => logger_core::Level::Off,
        }
    }
}

/// Logs a message using the logger backend.
///
/// # Parameters
///
/// * `level` - The severity level of the current message (e.g., Error, Warn, Info).
/// * `identifier` - A pointer to a null-terminated C string identifying the source of the log message.
/// * `message` - A pointer to a null-terminated C string containing the actual log message.
///
/// # Safety
///
///  The returned pointer must be freed using [`free_log_result`].
///
/// * `identifier` must be a valid, non-null pointer to a null-terminated UTF-8 encoded C string.
/// * `message` must be a valid, non-null pointer to a null-terminated UTF-8 encoded C string.
///
/// # Note
///
/// The caller (Python Sync wrapper, Go wrapper, etc.) is responsible for filtering log messages according to the logger's current log level.
/// This function will log any message it receives.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn glide_log(
    level: Level,
    identifier: *const c_char,
    message: *const c_char,
) -> *mut LogResult {
    let id_str = match unsafe { CStr::from_ptr(identifier).to_str() } {
        Ok(s) => s,
        Err(err) => {
            let c_err = CString::new(format!("Log identifier contains invalid UTF-8: {err}"))
                .unwrap_or_default()
                .into_raw();
            return Box::into_raw(Box::new(LogResult {
                log_error: c_err,
                level: Level::OFF, // Default value, should be ignored when there's an error
            }));
        }
    };

    let msg_str = match unsafe { CStr::from_ptr(message).to_str() } {
        Ok(s) => s,
        Err(err) => {
            let c_err = CString::new(format!("Log message contains invalid UTF-8: {err}"))
                .unwrap_or_default()
                .into_raw();
            return Box::into_raw(Box::new(LogResult {
                log_error: c_err,
                level: Level::OFF, // Default value, should be ignored when there's an error
            }));
        }
    };

    logger_core::log(level.into(), id_str, msg_str);

    Box::into_raw(Box::new(LogResult {
        log_error: std::ptr::null_mut(),
        level, // Level is not meaningful for log operations, but set a default
    }))
}

/// Initializes the logger with the provided log level and optional log file path.
///
/// Success is indicated by a `LogResult` with a null `log_error` field and the actual
/// log level set in the `level` field. Failure is indicated by a `LogResult` with a non-null
/// `log_error` field containing an error message, and the `level` field should be ignored.
///
/// # Parameters
///
/// * `level` - A pointer to a `Level` enum value that sets the maximum log level. If null, a WARN level will be used.
/// * `file_name` - A pointer to a null-terminated C string representing the desired log file path.
///
/// # Returns
///
/// A pointer to a `LogResult` struct containing either:
/// - Success: `log_error` is null, `level` contains the actual log level that was set
/// - Error: `log_error` contains the error message, `level` should be ignored
///
///
/// # Safety
///
/// The returned pointer must be freed using [`free_log_result`].
///
/// * `level` may be null. If not null, it must point to a valid instance of the `Level` enum.
/// * `file_name` may be null. If not null, it must point to a valid, null-terminated C string.
///   If the string contains invalid UTF-8, an error will be returned instead of panicking.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn init(level: *const Level, file_name: *const c_char) -> *mut LogResult {
    let level_option = if level.is_null() {
        None
    } else {
        Some(unsafe { *level }.into())
    };

    let file_name_option = if file_name.is_null() {
        None
    } else {
        match unsafe { CStr::from_ptr(file_name).to_str() } {
            Ok(file_str) => Some(file_str),
            Err(err) => {
                let c_err = CString::new(format!("File name contains invalid UTF-8: {err}"))
                    .unwrap_or_default()
                    .into_raw();
                return Box::into_raw(Box::new(LogResult {
                    log_error: c_err,
                    level: Level::OFF, // Default value, should be ignored when there's an error
                }));
            }
        }
    };

    let logger_level = logger_core::init(level_option, file_name_option);

    Box::into_raw(Box::new(LogResult {
        log_error: std::ptr::null_mut(),
        level: logger_level.into(),
    }))
}

/// Frees a log result.
///
/// This function deallocates a `LogResult` struct and any error message it contains.
///
/// # Parameters
///
/// * `result_ptr` - A pointer to the `LogResult` to free, or null.
///
/// # Safety
///
/// * `result_ptr` must be a valid pointer to a `LogResult` returned by [`glide_log`] or [`init`], or null.
/// * This function must be called exactly once for each `LogResult`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn free_log_result(result_ptr: *mut LogResult) {
    if result_ptr.is_null() {
        return;
    }
    unsafe {
        let result = Box::from_raw(result_ptr);
        if !result.log_error.is_null() {
            free_c_string(result.log_error);
        }
    }
}
