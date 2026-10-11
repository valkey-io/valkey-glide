use glide_ffi::*;
use rstest::rstest;
use std::ffi::{CStr, CString, c_char};
use std::net::TcpListener;
use std::process::{Child, Command};
use std::ptr;
use std::time::Duration;

// TODO: Move RedisServer implementation from glide-core tests to a reusable library and replace this Server implementation.
struct Server {
    process: Child,
    pub(crate) port: u16,
}

impl Server {
    fn new() -> Self {
        Self::with_requirepass(None)
    }

    fn with_requirepass(password: Option<&str>) -> Self {
        let port = Self::get_available_port();
        let process = Self::start_server(port, password);
        Self { process, port }
    }

    fn get_available_port() -> u16 {
        TcpListener::bind("127.0.0.1:0")
            .ok()
            .and_then(|listener| listener.local_addr().ok())
            .map(|addr| addr.port())
            .expect("Failed to find an available port")
    }

    fn start_server(port: u16, requirepass: Option<&str>) -> Child {
        let run_server = |engine_type: &str| {
            let mut cmd = Command::new(engine_type);
            cmd.arg("--port")
                .arg(port.to_string())
                .arg("--save")
                .arg("")
                .arg("--appendonly")
                .arg("no");
            if let Some(pw) = requirepass {
                cmd.arg("--requirepass").arg(pw);
            }
            cmd.spawn()
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
        std::thread::sleep(Duration::from_millis(500));
        child
    }
}

impl Drop for Server {
    fn drop(&mut self) {
        self.process.kill().ok();
        self.process.wait().ok();
    }
}

/// A server that listens only on a Unix domain socket (`--port 0`), so a client
/// that connects must have gone through the socket.
#[cfg(unix)]
struct UnixSocketServer {
    process: Child,
    dir: std::path::PathBuf,
    socket_path: std::path::PathBuf,
}

#[cfg(unix)]
impl UnixSocketServer {
    fn with_requirepass(password: &str) -> Self {
        static NEXT_ID: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
        let id = NEXT_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        // Socket paths are limited to ~104-108 bytes, so stay in /tmp.
        let dir =
            std::path::PathBuf::from(format!("/tmp/glide-ffi-uri-{}-{id}", std::process::id()));
        std::fs::create_dir_all(&dir).expect("Failed to create socket directory");
        let socket_path = dir.join("valkey.sock");
        let run_server = |engine_type: &str| {
            Command::new(engine_type)
                .args(["--port", "0", "--save", "", "--appendonly", "no"])
                .arg("--unixsocket")
                .arg(&socket_path)
                .arg("--dir")
                .arg(&dir)
                .args(["--requirepass", password])
                .spawn()
        };
        let process = run_server("valkey-server")
            .or_else(|_| run_server("redis-server"))
            .expect("Failed to start both valkey-server and redis-server");
        // The server creates the socket file once it listens.
        let deadline = std::time::Instant::now() + Duration::from_secs(10);
        while !socket_path.exists() {
            assert!(
                std::time::Instant::now() < deadline,
                "server did not listen on {socket_path:?}"
            );
            std::thread::sleep(Duration::from_millis(20));
        }
        Self {
            process,
            dir,
            socket_path,
        }
    }
}

#[cfg(unix)]
impl Drop for UnixSocketServer {
    fn drop(&mut self) {
        self.process.kill().ok();
        self.process.wait().ok();
        std::fs::remove_dir_all(&self.dir).ok();
    }
}

fn parse_error_msg(err_msg_ptr: *const c_char) -> String {
    if err_msg_ptr.is_null() {
        return String::new();
    }
    unsafe {
        CStr::from_ptr(err_msg_ptr)
            .to_str()
            .unwrap_or("Failed to parse error message")
            .to_string()
    }
}

// Helper to get null PubSubCallback
fn null_pubsub_callback() -> PubSubCallback {
    unsafe { std::mem::transmute::<*mut std::ffi::c_void, PubSubCallback>(std::ptr::null_mut()) }
}

#[test]
fn test_create_client_from_uri_simple() {
    let server = Server::new();
    let uri = CString::new(format!("valkey://127.0.0.1:{}", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(), // No extra options
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        // Success - client created
        assert!(!conn_response.conn_ptr.is_null());

        // Cleanup
        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        // Connection failed - print error for debugging
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_redis_scheme_compat() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_refresh_topology_from_initial_nodes() {
    let server = Server::new();
    let uri = CString::new(format!("valkey://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"refresh_topology_from_initial_nodes": true}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_read_only() {
    let server = Server::new();
    let uri = CString::new(format!("valkey://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"read_only": true}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_password() {
    let server = Server::new();
    // Note: This test will fail connection because server doesn't have auth,
    // but it tests URI parsing
    let uri = CString::new(format!("redis://:mypassword@127.0.0.1:{}", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail auth, but that's expected - we just want to test parsing
    // The important thing is it doesn't crash

    unsafe {
        if !conn_response.conn_ptr.is_null() {
            close_client(conn_response.conn_ptr);
        }
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_with_username_and_password() {
    let server = Server::new();
    let uri = CString::new(format!("redis://user:pass@127.0.0.1:{}", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());

    unsafe {
        let conn_response = &*response;
        if !conn_response.conn_ptr.is_null() {
            close_client(conn_response.conn_ptr);
        }
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_with_database() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}/5", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_json_options() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "request_timeout": 5000,
        "connection_timeout": 3000,
        "client_name": "test_client"
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_protocol() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"protocol": "RESP3"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_read_from() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"read_from": "Primary"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_retry_strategy() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "connection_retry_strategy": {
            "number_of_retries": 5,
            "factor": 2,
            "exponent_base": 2,
            "jitter_percent": 10
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_multiple_options() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "request_timeout": 5000,
        "client_name": "myapp",
        "protocol": "RESP2",
        "read_from": "Primary",
        "tcp_nodelay": true,
        "lazy_connect": false
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_invalid_uri() {
    let uri = CString::new("not-a-valid-uri").unwrap();
    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with error message
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Invalid connection URI") || error.contains("URI"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_invalid_json() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{invalid json}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with JSON error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Invalid JSON") || error.contains("JSON"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_unknown_json_keys() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"requst_timeout": 5000, "clint_name": "myapp"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("Unknown key(s) in connection options JSON"),
        "expected unknown-key error, got: {error}"
    );
    assert!(error.contains("requst_timeout"));
    assert!(error.contains("clint_name"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_invalid_protocol() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"protocol": "RESP99"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with unknown protocol error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Unknown protocol version") || error.contains("protocol"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_invalid_read_from() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"read_from": "InvalidValue"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with unknown read_from error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Unknown read_from value") || error.contains("read_from"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_invalid_database_id() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}/notanumber", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with invalid database ID error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Invalid database ID") || error.contains("database"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_wrong_type_in_json() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();

    // request_timeout should be a number, not a string
    let options = CString::new(r#"{"request_timeout": "not_a_number"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with type error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("must be a positive integer") || error.contains("timeout"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[rstest]
#[case("valkey://127.0.0.1")]
#[case("valkey://localhost:6379")]
#[case("valkeys://example.com:6380")]
#[case("redis://127.0.0.1")]
#[case("redis://localhost")]
#[case("redis://example.com:6380")]
#[case("redis://:password@localhost:6379")]
#[case("redis://user:pass@example.com:6380/0")]
fn test_create_client_from_uri_valid_formats(#[case] uri_format: &str) {
    let uri = CString::new(uri_format).unwrap();
    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());

    unsafe {
        let conn_response = &*response;
        // May or may not connect (server might not exist), but should parse without crash
        if !conn_response.conn_ptr.is_null() {
            close_client(conn_response.conn_ptr);
        }
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[rstest]
#[case("PreferReplica")]
// #[case("LowestLatency")] // TODO: Not yet implemented in glide-core
#[case("AZAffinity")]
#[case("AZAffinityReplicasAndPrimary")]
#[case("AllNodes")]
#[case("AZAffinityAllNodes")]
fn test_create_client_from_uri_all_read_from_values(#[case] read_from: &str) {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(format!(r#"{{"read_from": "{}"}}"#, read_from)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with read_from={}: {}",
            read_from, error
        );
    }
}

#[test]
fn test_create_client_from_uri_with_compression_config() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "compression_config": {
            "enabled": true,
            "backend": "ZSTD",
            "compression_level": 3,
            "min_compression_size": 1024
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client with compression_config: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_periodic_checks_manual() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "periodic_checks": {
            "manual_interval": {
                "duration_in_sec": 30
            }
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with periodic_checks manual: {}",
            error
        );
    }
}

#[test]
fn test_create_client_from_uri_with_periodic_checks_disabled() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "periodic_checks": {
            "disabled": true
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with periodic_checks disabled: {}",
            error
        );
    }
}

#[test]
fn test_create_client_from_uri_with_iam_credentials() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "iam_credentials": {
            "cluster_name": "my-cluster",
            "region": "us-east-1",
            "service_type": "ELASTICACHE",
            "refresh_interval_seconds": 900
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // IAM credentials require actual AWS setup, so connection may fail
    // We're just testing that the parsing works without crashing
    unsafe {
        if !conn_response.conn_ptr.is_null() {
            close_client(conn_response.conn_ptr);
        }
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_with_username_only() {
    let server = Server::new();
    let uri = CString::new(format!("redis://unknown-user@127.0.0.1:{}", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Failed to create client with username only: {}", error);
    }
}

#[test]
fn test_create_client_from_uri_with_pubsub_subscriptions() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "pubsub_subscriptions": {
            "0": ["news", "updates"],
            "1": ["events:*"],
            "2": ["shard-channel"]
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with pubsub_subscriptions: {}",
            error
        );
    }
}

#[test]
fn test_create_client_from_uri_invalid_compression_backend() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "compression_config": {
            "enabled": true,
            "backend": "INVALID"
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with unknown backend error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Unknown compression backend") || error.contains("INVALID"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_invalid_service_type() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "iam_credentials": {
            "cluster_name": "my-cluster",
            "region": "us-east-1",
            "service_type": "INVALID_SERVICE"
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    // Should fail with unknown service type error
    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(error.contains("Unknown service type") || error.contains("INVALID_SERVICE"));

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_with_client_side_cache_all_fields() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "max_cache_kb": 2048,
            "entry_ttl_ms": 60000,
            "eviction_policy": "LRU",
            "enable_metrics": true
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with client_side_cache (all fields): {}",
            error
        );
    }
}

#[test]
fn test_create_client_from_uri_with_client_side_cache_required_fields_only() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "max_cache_kb": 1024,
            "entry_ttl_ms": 0
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with client_side_cache (required fields only): {}",
            error
        );
    }
}

#[test]
fn test_create_client_from_uri_with_client_side_cache_lfu_policy() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "max_cache_kb": 512,
            "entry_ttl_ms": 30000,
            "eviction_policy": "LFU"
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "Failed to create client with client_side_cache (LFU policy): {}",
            error
        );
    }
}

#[test]
fn test_create_client_from_uri_invalid_eviction_policy() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "max_cache_kb": 1024,
            "entry_ttl_ms": 60000,
            "eviction_policy": "INVALID"
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("Unknown eviction_policy") || error.contains("INVALID"),
        "expected eviction_policy error, got: {error}"
    );

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_client_side_cache_missing_max_cache_kb() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "entry_ttl_ms": 60000
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("max_cache_kb is required"),
        "expected missing max_cache_kb error, got: {error}"
    );

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_client_side_cache_missing_entry_ttl_ms() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "max_cache_kb": 1024
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("entry_ttl_ms is required"),
        "expected missing entry_ttl_ms error, got: {error}"
    );

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_client_side_cache_invalid_max_cache_kb_type() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{
        "client_side_cache": {
            "max_cache_kb": "not_a_number",
            "entry_ttl_ms": 60000
        }
    }"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("max_cache_kb must be a positive integer"),
        "expected type error for max_cache_kb, got: {error}"
    );

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_client_side_cache_not_an_object() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(r#"{"client_side_cache": "not_an_object"}"#).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("client_side_cache must be an object"),
        "expected object type error, got: {error}"
    );

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[rstest]
#[case("lru")]
#[case("lfu")]
#[case("LrU")]
#[case("lFu")]
fn test_create_client_from_uri_client_side_cache_case_insensitive_eviction_policy(
    #[case] eviction_policy: &str,
) {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(format!(
        r#"{{"client_side_cache": {{"max_cache_kb": 256, "entry_ttl_ms": 5000, "eviction_policy": "{}"}}}}"#,
        eviction_policy
    ))
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if conn_response.connection_error_message.is_null() {
        assert!(!conn_response.conn_ptr.is_null());

        unsafe {
            close_client(conn_response.conn_ptr);
            free_connection_response(response as *mut ConnectionResponse);
            drop(Box::from_raw(client_type));
        }
    } else {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("Expected success for eviction_policy={eviction_policy:?}, got: {error}");
    }
}

#[test]
fn test_create_client_from_uri_client_side_cache_rejects_cache_id() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options = CString::new(
        r#"{"client_side_cache": {"max_cache_kb": 256, "entry_ttl_ms": 5000, "cache_id": "user-supplied-id"}}"#,
    )
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    assert!(!conn_response.connection_error_message.is_null());
    assert!(conn_response.conn_ptr.is_null());

    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("cache_id"),
        "Error should mention cache_id, got: {error}"
    );

    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

#[test]
fn test_create_client_from_uri_client_side_cache_zero_max_cache_kb() {
    let server = Server::new();
    let uri = CString::new(format!("redis://127.0.0.1:{}", server.port)).unwrap();
    let options =
        CString::new(r#"{"client_side_cache": {"max_cache_kb": 0, "entry_ttl_ms": 1000}}"#)
            .unwrap();
    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));
    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            options.as_ptr(),
            client_type,
            null_pubsub_callback(),
        )
    };
    assert!(!response.is_null());
    let conn_response = unsafe { &*response };
    assert!(
        !conn_response.connection_error_message.is_null(),
        "Expected error for max_cache_kb=0"
    );
    assert!(conn_response.conn_ptr.is_null());
    let error = parse_error_msg(conn_response.connection_error_message);
    assert!(
        error.contains("max_cache_kb"),
        "Expected max_cache_kb error, got: {error}"
    );
    unsafe {
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

// Live-server end-to-end regression test for issue #6659: a URI with a
// percent-encoded reserved character in the password must decode before
// AUTH is issued, otherwise the server rejects the connection.
//
// Prior to the fix, `redis://:p%40ss@host` sent `AUTH p%40ss` on the wire.
// With `requirepass "p@ss"` set on the server side, connection failed.
// The fix percent-decodes userinfo, so the server now receives `AUTH p@ss`
// and the connection succeeds.
#[test]
fn test_create_client_from_uri_with_reserved_char_password_authenticates() {
    let password = "p@ss";
    let server = Server::with_requirepass(Some(password));
    // "@" is percent-encoded as "%40" in the URI. Without decoding on the FFI
    // side, the server would see "p%40ss" and reject the AUTH command.
    let uri = CString::new(format!("redis://:p%40ss@127.0.0.1:{}", server.port)).unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if !conn_response.connection_error_message.is_null() {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!(
            "expected successful AUTH with percent-encoded password `p%40ss` \
             (server requirepass = `{password}`), got: {error}"
        );
    }
    assert!(
        !conn_response.conn_ptr.is_null(),
        "expected a live connection after successful AUTH"
    );

    unsafe {
        close_client(conn_response.conn_ptr);
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}

// The server listens only on the socket and requires a password, so a
// successful connection shows the URI's socket path and `pass` query
// parameter both reached glide-core.
#[cfg(unix)]
#[rstest]
#[case("valkey+unix")]
#[case("redis+unix")]
#[case("unix")]
fn test_create_client_from_uri_unix_socket_authenticates(#[case] scheme: &str) {
    let password = "p@ss";
    let server = UnixSocketServer::with_requirepass(password);
    let uri = CString::new(format!(
        "{scheme}://{}?db=5&pass=p%40ss",
        server.socket_path.display()
    ))
    .unwrap();

    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));

    let response = unsafe {
        create_client_from_uri(
            uri.as_ptr(),
            ptr::null(),
            client_type,
            null_pubsub_callback(),
        )
    };

    assert!(!response.is_null());
    let conn_response = unsafe { &*response };

    if !conn_response.connection_error_message.is_null() {
        let error = parse_error_msg(conn_response.connection_error_message);
        panic!("expected a connection over {scheme}:// with AUTH, got: {error}");
    }
    assert!(
        !conn_response.conn_ptr.is_null(),
        "expected a live connection over the socket"
    );

    unsafe {
        close_client(conn_response.conn_ptr);
        free_connection_response(response as *mut ConnectionResponse);
        drop(Box::from_raw(client_type));
    }
}
