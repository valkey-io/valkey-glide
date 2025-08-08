// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::{
    ConnectionRequest,
    connection_request::{NodeAddress, TlsMode},
};
use miri_tests::{
    ClientType, ConnectionResponse, PushKind, close_client, create_client, free_connection_response,
};
use miri_tests::{Level, LogResult, free_log_result, init, log};
use protobuf::Message;
use std::ffi::{CStr, CString, c_char};
use std::ptr;

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

unsafe extern "C-unwind" fn pubsub_callback(
    _client_ptr: usize,
    _kind: PushKind,
    _message: *const u8,
    _message_len: i64,
    _channel: *const u8,
    _channel_len: i64,
    _pattern: *const u8,
    _pattern_len: i64,
) {
}

fn get_logger_error_message(log_result: &LogResult) -> Option<String> {
    if log_result.log_error.is_null() {
        None
    } else {
        unsafe {
            CStr::from_ptr(log_result.log_error)
                .to_str()
                .ok()
                .map(|s| s.to_string())
        }
    }
}

/// Create_client

#[test]
fn create_client_test() {
    let connection_request_bytes = create_connection_request(6378);
    let connection_request_len = connection_request_bytes.len();
    let connection_request_ptr = connection_request_bytes.as_ptr();
    let client_type = Box::new(ClientType::SyncClient);
    let client_type_ptr = Box::into_raw(client_type);

    unsafe {
        let connection_response_ptr = create_client(
            connection_request_ptr,
            connection_request_len,
            client_type_ptr,
            pubsub_callback,
        );
        let conn_ptr = (*connection_response_ptr).conn_ptr;
        close_client(conn_ptr);
        free_connection_response(connection_response_ptr as *mut ConnectionResponse);
        let _ = Box::from_raw(client_type_ptr);
    }
}

/// Logger tests
#[test]
fn test_init_logger_with_valid_level() {
    unsafe {
        let level = Level::INFO;
        let log_result_ptr = init(&level, ptr::null());
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(log_result.log_error.is_null());

        free_log_result(log_result_ptr);
    }
}

#[test]
fn test_init_with_invalid_utf8_filename() {
    unsafe {
        let level = Level::INFO;
        let invalid_utf8 = vec![0xFF, 0xFE, 0xFD, 0x00];

        let log_result_ptr = init(&level, invalid_utf8.as_ptr() as *const c_char);
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(!log_result.log_error.is_null());

        // Check that the error message contains the expected text
        let error_message = get_logger_error_message(log_result);
        assert!(error_message.is_some());
        let error_text = error_message.unwrap();
        assert!(error_text.contains("File name contains invalid UTF-8"));

        free_log_result(log_result_ptr);
    }
}

#[test]
fn test_log_with_valid_inputs() {
    unsafe {
        let level = Level::INFO;
        let init_result_ptr = init(&level, ptr::null());
        free_log_result(init_result_ptr);

        let identifier = CString::new("test_identifier").unwrap();
        let message = CString::new("This is a test log message").unwrap();

        let log_result_ptr = log(Level::INFO, identifier.as_ptr(), message.as_ptr());
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(log_result.log_error.is_null());

        free_log_result(log_result_ptr);
    }
}

#[test]
fn test_log_with_invalid_utf8_message() {
    unsafe {
        let level = Level::INFO;
        let init_result_ptr = init(&level, ptr::null());
        free_log_result(init_result_ptr);

        let identifier = CString::new("valid_identifier").unwrap();
        let invalid_utf8 = vec![0xFF, 0xFE, 0xFD, 0x00];

        let log_result_ptr = log(
            Level::ERROR,
            identifier.as_ptr(),
            invalid_utf8.as_ptr() as *const c_char,
        );
        assert!(!log_result_ptr.is_null());

        let log_result = &*log_result_ptr;
        assert!(!log_result.log_error.is_null());

        let error_message = get_logger_error_message(log_result);
        assert!(error_message.is_some());
        let error_text = error_message.unwrap();
        assert!(error_text.contains("Log message contains invalid UTF-8"));

        free_log_result(log_result_ptr);
    }
}
