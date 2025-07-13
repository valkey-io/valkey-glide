// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use miri_tests::{PushKind, ClientType, ConnectionResponse, create_client, close_client, free_connection_response};
use glide_core::{ConnectionRequest, connection_request::{TlsMode, NodeAddress}};
use protobuf::Message;

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

#[test]
fn create_client_test() {
    let connection_request_bytes = create_connection_request(6378);
    let connection_request_len = connection_request_bytes.len();
    let connection_request_ptr = connection_request_bytes.as_ptr();
    let client_type = Box::new(ClientType::SyncClient);
    let client_type_ptr = Box::into_raw(client_type);

    unsafe {
        let connection_response_ptr = 
            create_client(
                connection_request_ptr,
                connection_request_len,
                client_type_ptr,
                pubsub_callback
            );
        let conn_ptr = (*connection_response_ptr).conn_ptr;
        close_client(conn_ptr);
        free_connection_response(connection_response_ptr as *mut ConnectionResponse);
        let _ = Box::from_raw(client_type_ptr);
    }
}
