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
        let port = Self::get_available_port();
        let process = Self::start_server(port);
        Self { process, port }
    }

    fn get_available_port() -> u16 {
        TcpListener::bind("127.0.0.1:0")
            .ok()
            .and_then(|listener| listener.local_addr().ok())
            .map(|addr| addr.port())
            .expect("Failed to find an available port")
    }

    fn start_server(port: u16) -> Child {
        let run_server = |engine_type: &str| {
            Command::new(engine_type)
                .arg("--port")
                .arg(port.to_string())
                .arg("--save")
                .arg("")
                .arg("--appendonly")
                .arg("no")
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
