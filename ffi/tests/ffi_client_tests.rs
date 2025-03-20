use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::errors::RequestErrorType;
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::collections::HashMap;
use std::ffi::{c_char, c_ulong, c_void, CStr};
use std::net::TcpListener;
use std::process::{Child, Command};
use std::sync::{
    atomic::{AtomicUsize, Ordering},
    Arc,
};
use std::sync::{LazyLock, Mutex};
use tokio::runtime::Runtime;
use tokio::time::{sleep, Duration};

static SUCCESS_COUNTER: LazyLock<Arc<AtomicUsize>> =
    LazyLock::new(|| Arc::new(AtomicUsize::new(0)));
static FAILURE_COUNTER: LazyLock<Arc<AtomicUsize>> =
    LazyLock::new(|| Arc::new(AtomicUsize::new(0)));
type StringResultMap =
    LazyLock<Mutex<HashMap<usize, Result<String, (&'static str, RequestErrorType)>>>>;
static RESULTS_MAP: StringResultMap = LazyLock::new(|| Mutex::new(HashMap::new()));

/// Success callback function for String responses
extern "C" fn string_success_callback(index: usize, response_ptr: *const CommandResponse) {
    assert!(!response_ptr.is_null());
    let response: &CommandResponse = unsafe { &*response_ptr };
    assert!(!response.string_value.is_null());
    let mut map = RESULTS_MAP
        .lock()
        .expect("Failed to aquire the results' lock");
    // Create a byte slice from the raw pointer
    let bytes = unsafe {
        std::slice::from_raw_parts(
            response.string_value as *const u8,
            response.string_value_len as usize,
        )
    };
    // Convert bytes to a Rust string, handling UTF-8 safely
    let str_response = std::str::from_utf8(bytes)
        .map(|s| s.to_owned())
        .expect("Failed to parse string value");
    map.insert(index, Ok(str_response));

    SUCCESS_COUNTER.fetch_add(1, Ordering::SeqCst);
}

/// Failure callback function
extern "C" fn failure_callback(
    index: usize,
    err_msg_ptr: *const c_char,
    error_type: RequestErrorType,
) {
    assert!(!err_msg_ptr.is_null());
    let err_msg: &'static str = unsafe {
        CStr::from_ptr(err_msg_ptr)
            .to_str()
            .expect("Failed to parse err_msg")
    };
    let mut map = RESULTS_MAP
        .lock()
        .expect("Failed to aquire the results' lock");
    map.insert(index, Err((err_msg, error_type)));
    FAILURE_COUNTER.fetch_add(1, Ordering::SeqCst);
}

// TODO: Move RedisServer implementation from glide-core tests to a reusable library and replace this Server implementation.
struct Server {
    process: Child,
    pub(crate) port: u32,
}

impl Server {
    fn new() -> Self {
        let port = Self::get_available_port() as u32;
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

    fn start_server(port: u32) -> Child {
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

fn create_connection_request(port: u32) -> Vec<u8> {
    let host = "localhost";
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = NodeAddress::new();
    address_info.host = host.into();
    address_info.port = port;
    request.addresses.push(address_info);
    request.write_to_bytes().expect("Failed to serialize")
}

fn execute_command(
    client_ptr: *const c_void,
    index: usize,
    command_bytes: &[u8],
    arg_count: u64,
    command_type: RequestType,
) {
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

    unsafe {
        command(
            client_ptr,
            index,
            command_type,
            arg_count,
            command_ptr,
            args_len_ptr,
            route_bytes,
            route_len,
        );
    }
    let rt = Runtime::new().unwrap();
    rt.block_on(async {
        // Let the async callback to be called
        sleep(Duration::from_millis(1)).await;
    });
}

fn get_response(index: usize) -> String {
    let map = RESULTS_MAP.lock().unwrap();
    let result = map
        .get(&index)
        .expect("Couldn't find the relevant idx in the map");
    assert!(result.is_ok());
    result.as_ref().expect("Can't unwrap result").to_string()
}

fn get_error(index: usize) -> (String, RequestErrorType) {
    let map = RESULTS_MAP.lock().unwrap();
    let result = map
        .get(&index)
        .expect("Couldn't find the relevant idx in the map");
    assert!(result.is_err());
    let (err_msg, err_type) = result.as_ref().unwrap_err();
    (err_msg.to_string(), err_type.clone())
}

#[test]
fn test_command_execution() {
    let server = Server::new();
    let connection_request_bytes = create_connection_request(server.port);
    let connection_request_len = connection_request_bytes.len();
    let connection_request_ptr = connection_request_bytes.as_ptr();
    unsafe {
        let response_ptr = create_client(
            connection_request_ptr,
            connection_request_len,
            string_success_callback,
            failure_callback,
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
        execute_command(
            client_ptr,
            good_cmd_idx,
            ping_value,
            1_u64,
            RequestType::Ping,
        );
        assert_eq!(SUCCESS_COUNTER.load(Ordering::SeqCst), 1);
        assert_eq!(
            get_response(good_cmd_idx),
            String::from_utf8_lossy(ping_value)
        );
        // Bad command: Non existing command args, the server should return with an error: SADD NOTAREALCMD
        let bad_cmd_idx = 1;
        execute_command(
            client_ptr,
            bad_cmd_idx,
            b"NOTAREALCMD",
            1_u64,
            RequestType::SAdd,
        );
        assert_eq!(FAILURE_COUNTER.load(Ordering::SeqCst), 1);
        let (err_msg, err_type) = get_error(bad_cmd_idx);
        assert!(err_msg.contains("wrong number of arguments for 'sadd' command"));
        assert_eq!(err_type, RequestErrorType::Unspecified);
        free_connection_response(response_ptr as *mut ConnectionResponse);
        close_client(client_ptr);
    }
}
