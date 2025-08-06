use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::errors::RequestErrorType;
use glide_core::request_type::RequestType;
use glide_ffi::*;
use lazy_static::lazy_static;
use protobuf::Message;
use rstest::rstest;
use std::collections::HashMap;
use std::ffi::{CStr, c_char, c_ulong, c_void};
use std::net::TcpListener;
use std::process::{Child, Command};
use std::sync::{
    Arc, RwLock,
    atomic::{AtomicUsize, Ordering},
};
use tokio::runtime::Runtime;
use tokio::time::{Duration, sleep};

pub(crate) struct AsyncMetrics {
    pub success_count: AtomicUsize,
    pub failure_count: AtomicUsize,
    pub results: HashMap<usize, Result<String, (String, RequestErrorType)>>,
}

lazy_static! {
    static ref ASYNC_METRICS: Arc<RwLock<AsyncMetrics>> = Arc::new(RwLock::new(AsyncMetrics {
        success_count: AtomicUsize::new(0),
        failure_count: AtomicUsize::new(0),
        results: HashMap::new(),
    }));
}

const ASYNC_WRITE_LOCK_ERR: &str = "Failed to aquire ASYNC_METRICS the write lock";
const ASYNC_READ_LOCK_ERR: &str = "Failed to aquire ASYNC_METRICS the write lock";

/// Success callback function for String responses for the async client
extern "C-unwind" fn string_success_callback(index: usize, response_ptr: *const CommandResponse) {
    let mut metrics = ASYNC_METRICS.write().expect(ASYNC_WRITE_LOCK_ERR);
    metrics
        .results
        .insert(index, Ok(parse_string_res(response_ptr)));
    metrics.success_count.fetch_add(1, Ordering::SeqCst);
}

/// Failure callback function for the async client
extern "C-unwind" fn failure_callback(
    index: usize,
    err_msg_ptr: *const c_char,
    error_type: RequestErrorType,
) {
    let mut metrics = ASYNC_METRICS.write().expect(ASYNC_WRITE_LOCK_ERR);
    metrics
        .results
        .insert(index, Err((parse_error_msg(err_msg_ptr), error_type)));
    metrics.failure_count.fetch_add(1, Ordering::SeqCst);
}

fn parse_string_res(response_ptr: *const CommandResponse) -> String {
    assert!(!response_ptr.is_null());
    let response: &CommandResponse = unsafe { &*response_ptr };
    assert!(!response.string_value.is_null());
    // Create a byte slice from the raw pointer
    let bytes = unsafe {
        std::slice::from_raw_parts(
            response.string_value as *const u8,
            response.string_value_len as usize,
        )
    };
    // Convert bytes to a Rust string, handling UTF-8 safely
    std::str::from_utf8(bytes)
        .map(|s| s.to_owned())
        .expect("Failed to parse string value")
}

fn parse_error_msg(err_msg_ptr: *const c_char) -> String {
    assert!(!err_msg_ptr.is_null());
    let err_msg: &'static str = unsafe {
        CStr::from_ptr(err_msg_ptr)
            .to_str()
            .expect("Failed to parse err_msg")
    };
    err_msg.to_string()
}

// TODO: Move RedisServer implementation from glide-core tests to a reusable library and replace this Server implementation.
struct Server {
    process: Child,
    pub(crate) port: u16,
}

impl Server {
    fn new() -> Self {
        let port = Self::get_available_port();
        let process = Self::start_server(port);
        Self { process, port }
    }

    fn get_available_port() -> u16 {
        TcpListener::bind("127.0.0.1:0") // Bind to port 0 (OS assigns a free port)
            .ok()
            .and_then(|listener| listener.local_addr().ok())
            .map(|addr| addr.port())
            .expect("Can't find available port")
    }

    fn start_server(port: u16) -> Child {
        let run_server = |engine_type: &str| {
            Command::new(engine_type)
                .arg("--port")
                .arg(port.to_string())
                .spawn()
        };

        let child = match run_server("valkey-server") {
            Ok(child) => child,
            Err(e) => {
                eprintln!("Failed to start valkey-server: {e}. Trying redis-server...");
                run_server("redis-server")
                    .expect("Failed to start both valkey-server and redis-server")
            }
        };

        // Give the server some time to start
        std::thread::sleep(Duration::from_secs(1));
        child
    }
}

impl Drop for Server {
    fn drop(&mut self) {
        self.process
            .kill()
            .expect("Failed to kill the Valkey server");
    }
}

fn create_connection_request(port: u16) -> Vec<u8> {
    let host = "localhost";
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = NodeAddress::new();
    address_info.host = host.into();
    address_info.port = port as u32;
    request.addresses.push(address_info);
    request.write_to_bytes().expect("Failed to serialize")
}

fn execute_command(
    client_ptr: *const c_void,
    index: usize,
    command_bytes: &[u8],
    arg_count: u64,
    command_type: RequestType,
) -> Option<Box<CommandResult>> {
    let command_len = command_bytes.len();
    // Convert command arguments into a vector of pointers
    let command_vec = [command_bytes.as_ptr()];
    let command_ptr = command_vec.as_ptr() as *const usize;
    let args_len = command_len as c_ulong;
    let args_len_vec = [args_len];
    let args_len_ptr = args_len_vec.as_ptr();

    let empty_data: Vec<u8> = vec![];
    let route_bytes: *const u8 = empty_data.as_ptr();
    let route_len = 0;
    let command_res_ptr = unsafe {
        command(
            client_ptr,
            index,
            command_type,
            arg_count,
            command_ptr,
            args_len_ptr,
            route_bytes,
            route_len,
            0,
        )
    };
    if command_res_ptr.is_null() {
        // If the returned CommandResult pointer is a null it means that the Async client is being used.
        // We shall let the async callback to be called.
        let rt = Runtime::new().unwrap();
        rt.block_on(async {
            sleep(Duration::from_millis(1)).await;
        });
        None
    } else {
        // Sync client, command result returns immediately
        unsafe { Some(Box::from_raw(command_res_ptr)) }
    }
}

fn get_sync_response(cmd_resp: *mut CommandResponse) -> String {
    parse_string_res(cmd_resp)
}

fn get_async_response(index: usize) -> String {
    let metrics = ASYNC_METRICS.read().expect(ASYNC_READ_LOCK_ERR);
    let result = metrics
        .results
        .get(&index)
        .expect("Couldn't find the relevant idx in the map");
    assert!(result.is_ok());
    result.as_ref().expect("Can't unwrap result").to_string()
}

fn get_sync_error(command_error: *mut CommandError) -> (String, RequestErrorType) {
    assert!(!command_error.is_null());
    let command_err: Box<CommandError> = unsafe { Box::from_raw(command_error) };
    (
        parse_error_msg(command_err.command_error_message),
        command_err.command_error_type,
    )
}

fn get_async_error(index: usize) -> (String, RequestErrorType) {
    let metrics = ASYNC_METRICS.read().expect(ASYNC_READ_LOCK_ERR);
    let result = metrics
        .results
        .get(&index)
        .expect("Couldn't find the relevant idx in the map");
    assert!(result.is_err());
    let (err_msg, err_type) = result.as_ref().unwrap_err();
    (err_msg.to_string(), err_type.clone())
}

#[rstest]
fn test_ffi_client_command_execution(#[values(false, true)] async_client: bool) {
    let server = Server::new();
    let connection_request_bytes = create_connection_request(server.port);
    let connection_request_len = connection_request_bytes.len();
    let connection_request_ptr = connection_request_bytes.as_ptr();
    let client_type = Box::into_raw(Box::new(if async_client {
        ClientType::AsyncClient {
            success_callback: string_success_callback,
            failure_callback,
        }
    } else {
        ClientType::SyncClient
    }));
    unsafe {
        let response_ptr = create_client(
            connection_request_ptr,
            connection_request_len,
            client_type,
            std::mem::transmute::<
                *mut c_void,
                unsafe extern "C-unwind" fn(
                    client_ptr: usize,
                    kind: PushKind,
                    message: *const u8,
                    message_len: i64,
                    channel: *const u8,
                    channel_len: i64,
                    pattern: *const u8,
                    pattern_len: i64,
                ),
            >(std::ptr::null_mut()),
        );

        assert!(!response_ptr.is_null(), "Failed to create client");
        let response = &*response_ptr;
        assert!(
            !response.conn_ptr.is_null() && response.connection_error_message.is_null(),
            "Connection response should be valid"
        );

        let client_ptr = response.conn_ptr;
        // Good command: PING IS_WORKING
        let good_cmd_idx = 0;
        let ping_value = b"IS_WORKING";
        let good_res = execute_command(
            client_ptr,
            good_cmd_idx,
            ping_value,
            1_u64,
            RequestType::Ping,
        );
        let ping_res = if async_client {
            assert!(good_res.is_none()); // result should be returned through callback
            let metrics = ASYNC_METRICS.read().expect(ASYNC_READ_LOCK_ERR);
            assert_eq!(metrics.success_count.load(Ordering::SeqCst), 1);
            get_async_response(good_cmd_idx)
        } else {
            assert!(good_res.is_some());
            get_sync_response(good_res.unwrap().response)
        };
        assert_eq!(ping_res, String::from_utf8_lossy(ping_value));
        // Bad command: Non existing command args, the server should return with an error: SADD NOTAREALCMD
        let bad_cmd_idx = 1;
        let bad_res = execute_command(
            client_ptr,
            bad_cmd_idx,
            b"NOTAREALCMD",
            1_u64,
            RequestType::SAdd,
        );
        let (err_msg, err_type) = if async_client {
            assert!(bad_res.is_none()); // result should be returned through callback
            let metrics = ASYNC_METRICS.read().expect(ASYNC_READ_LOCK_ERR);
            assert_eq!(metrics.failure_count.load(Ordering::SeqCst), 1);
            get_async_error(bad_cmd_idx)
        } else {
            assert!(bad_res.is_some());
            get_sync_error(bad_res.unwrap().command_error)
        };
        assert!(err_msg.contains("wrong number of arguments for 'sadd' command"));
        assert_eq!(err_type, RequestErrorType::Unspecified);
        free_connection_response(response_ptr as *mut ConnectionResponse);
        close_client(client_ptr);
    }
}
#[test]
fn test_create_otel_span_with_parent() {
    // Test creating a parent span
    let parent_span_ptr = create_otel_span(RequestType::Set);
    assert_ne!(parent_span_ptr, 0, "Parent span creation should succeed");

    // Test creating a child span with valid parent
    let child_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, parent_span_ptr) };
    assert_ne!(
        child_span_ptr, 0,
        "Child span creation with valid parent should succeed"
    );

    // Test creating a child span with invalid parent (0)
    let child_span_ptr_invalid = unsafe { create_otel_span_with_parent(RequestType::Get, 0) };
    assert_ne!(
        child_span_ptr_invalid, 0,
        "Child span creation with invalid parent should fallback to independent span"
    );

    // Test creating a child span with invalid parent (garbage pointer)
    let child_span_ptr_garbage =
        unsafe { create_otel_span_with_parent(RequestType::Get, 0xDEADBEEF) };
    assert_ne!(
        child_span_ptr_garbage, 0,
        "Child span creation with garbage parent should fallback to independent span"
    );

    // Test with invalid request type
    let child_span_ptr_invalid_req =
        unsafe { create_otel_span_with_parent(RequestType::InvalidRequest, parent_span_ptr) };
    assert_eq!(
        child_span_ptr_invalid_req, 0,
        "Child span creation with invalid request type should return 0"
    );

    // Clean up spans
    unsafe {
        drop_otel_span(parent_span_ptr);
        drop_otel_span(child_span_ptr);
        drop_otel_span(child_span_ptr_invalid);
        drop_otel_span(child_span_ptr_garbage);
    }
}

#[test]
fn test_create_named_otel_span() {
    use std::ffi::CString;

    // Test creating a named span with valid name
    let span_name = CString::new("test_user_operation").expect("CString::new failed");
    let span_ptr = unsafe { create_named_otel_span(span_name.as_ptr()) };
    assert_ne!(
        span_ptr, 0,
        "Named span creation with valid name should succeed"
    );

    // Test creating a named span with empty name
    let empty_name = CString::new("").expect("CString::new failed");
    let empty_span_ptr = unsafe { create_named_otel_span(empty_name.as_ptr()) };
    assert_ne!(
        empty_span_ptr, 0,
        "Named span creation with empty name should succeed"
    );

    // Test creating a named span with null pointer
    let null_span_ptr = unsafe { create_named_otel_span(std::ptr::null()) };
    assert_eq!(
        null_span_ptr, 0,
        "Named span creation with null pointer should return 0"
    );

    // Test creating a named span with very long name (should fail)
    let long_name = "a".repeat(300); // Exceeds 256 character limit
    let long_name_cstring = CString::new(long_name).expect("CString::new failed");
    let long_span_ptr = unsafe { create_named_otel_span(long_name_cstring.as_ptr()) };
    assert_eq!(
        long_span_ptr, 0,
        "Named span creation with overly long name should return 0"
    );

    // Test creating a named span with normal length name
    let normal_name = CString::new("normal_operation_name").expect("CString::new failed");
    let normal_span_ptr = unsafe { create_named_otel_span(normal_name.as_ptr()) };
    assert_ne!(
        normal_span_ptr, 0,
        "Named span creation with normal name should succeed"
    );

    // Test that the created span can be used as a parent
    let child_span_ptr = unsafe { create_otel_span_with_parent(RequestType::Get, span_ptr) };
    assert_ne!(
        child_span_ptr, 0,
        "Child span creation with named parent should succeed"
    );

    // Test creating a named span with special characters
    let special_name = CString::new("user-operation:123").expect("CString::new failed");
    let special_span_ptr = unsafe { create_named_otel_span(special_name.as_ptr()) };
    assert_ne!(
        special_span_ptr, 0,
        "Named span creation with special characters should succeed"
    );

    // Clean up spans
    unsafe {
        drop_otel_span(span_ptr);
        drop_otel_span(empty_span_ptr);
        drop_otel_span(normal_span_ptr);
        drop_otel_span(special_span_ptr);
        drop_otel_span(child_span_ptr);
    }
}
