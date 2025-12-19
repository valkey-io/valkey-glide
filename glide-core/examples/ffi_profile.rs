use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::ffi::{c_ulong, c_void};
use std::time::Instant;

fn create_connection_request() -> Vec<u8> {
    let mut request = ConnectionRequest::new();
    request.tls_mode = TlsMode::NoTls.into();
    let mut address_info = NodeAddress::new();
    address_info.host = "127.0.0.1".into();
    address_info.port = 6379;
    request.addresses.push(address_info);
    request.write_to_bytes().expect("Failed to serialize")
}

fn create_ffi_client() -> *const c_void {
    let connection_request_bytes = create_connection_request();
    let connection_request_len = connection_request_bytes.len();
    let connection_request_ptr = connection_request_bytes.as_ptr();
    
    let client_type = Box::into_raw(Box::new(ClientType::SyncClient));
    
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
        
        let response: &ConnectionResponse = &*response_ptr;
        let client_ptr = response.conn_ptr;
        
        // Setup key
        execute_ffi_command(client_ptr, 0, b"profile_key", b"profile_value", RequestType::Set);
        
        client_ptr
    }
}

fn execute_ffi_command(
    client_ptr: *const c_void,
    index: usize,
    key: &[u8],
    value: &[u8],
    command_type: RequestType,
) {
    let args = match command_type {
        RequestType::Get => vec![key],
        _ => vec![key, value],
    };
    
    let arg_count = args.len() as u64;
    let command_ptrs: Vec<*const u8> = args.iter().map(|arg| arg.as_ptr()).collect();
    let command_ptr = command_ptrs.as_ptr() as *const usize;
    
    let args_lens: Vec<c_ulong> = args.iter().map(|arg| arg.len() as c_ulong).collect();
    let args_len_ptr = args_lens.as_ptr();

    let empty_data: Vec<u8> = vec![];
    let route_bytes: *const u8 = empty_data.as_ptr();
    let route_len = 0;
    
    let _command_res_ptr = unsafe {
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
}

fn main() {
    let iterations = 1000;
    println!("=== FFI PROTOBUF PATH PROFILE ===");
    
    let client_ptr = create_ffi_client();
    let start = Instant::now();
    
    for i in 0..iterations {
        // FFI protobuf path: RequestType through FFI
        execute_ffi_command(client_ptr, i, b"profile_key", b"", RequestType::Get);
    }
    
    let duration = start.elapsed();
    let tps = iterations as f64 / duration.as_secs_f64();
    
    unsafe { close_client(client_ptr) };
    
    println!("FFI protobuf path completed: {} iterations in {:?}", iterations, duration);
    println!("TPS: {:.2}", tps);
}
