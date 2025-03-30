use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::errors::RequestErrorType;
use glide_core::request_type::RequestType;
use glide_ffi::*;
use lazy_static::lazy_static;
use protobuf::Message;
use rstest::rstest;
use std::collections::HashMap;
use std::ffi::{c_char, c_ulong, c_void, CStr};
use std::net::TcpListener;
use std::process::{Child, Command};
use std::sync::{
    atomic::{AtomicUsize, Ordering},
    Arc, RwLock,
};
use tokio::runtime::Runtime;
use tokio::time::{sleep, Duration};

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
extern "C" fn string_success_callback(index: usize, response_ptr: *const CommandResponse) {
    let mut metrics = ASYNC_METRICS.write().expect(ASYNC_WRITE_LOCK_ERR);
    metrics
        .results
        .insert(index, Ok(parse_string_res(response_ptr)));
    metrics.success_count.fetch_add(1, Ordering::SeqCst);
}

/// Failure callback function for the async client
extern "C" fn failure_callback(
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
                eprintln!(
                    "Failed to start valkey-server: {}. Trying redis-server...",
                    e
                );
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
        let response_ptr =
            create_client(connection_request_ptr, connection_request_len, client_type);

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
