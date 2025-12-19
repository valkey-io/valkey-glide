use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::env;
use std::ffi::{c_ulong, c_void, CStr, c_char};
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
        
        assert!(!response_ptr.is_null());
        let response: &ConnectionResponse = &*response_ptr;
        assert!(!response.conn_ptr.is_null() && response.connection_error_message.is_null());
        
        let client_ptr = response.conn_ptr;
        
        // Setup: SET benchmark_key test_value
        execute_ffi_command(client_ptr, 0, b"benchmark_key", b"test_value", RequestType::Set);
        
        client_ptr
    }
}

fn execute_ffi_command(
    client_ptr: *const c_void,
    index: usize,
    key: &[u8],
    value: &[u8],
    command_type: RequestType,
) -> Option<String> {
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
    
    if !command_res_ptr.is_null() {
        let result = unsafe { Box::from_raw(command_res_ptr) };
        if !result.response.is_null() {
            let response: &CommandResponse = unsafe { &*result.response };
            if !response.string_value.is_null() {
                let bytes = unsafe {
                    std::slice::from_raw_parts(
                        response.string_value as *const u8,
                        response.string_value_len as usize,
                    )
                };
                return Some(std::str::from_utf8(bytes).unwrap().to_string());
            }
        }
    }
    None
}

fn run_ffi_benchmark(iterations: usize, command_type: RequestType) -> f64 {
    let client_ptr = create_ffi_client();
    let start = Instant::now();
    
    for i in 0..iterations {
        let _result = execute_ffi_command(client_ptr, i, b"benchmark_key", b"", command_type);
    }
    
    let duration = start.elapsed();
    unsafe { close_client(client_ptr) };
    
    iterations as f64 / duration.as_secs_f64()
}

fn main() {
    let iterations = env::var("BENCH_ITERATIONS")
        .unwrap_or_else(|_| "10000".to_string())
        .parse()
        .unwrap_or(10000);

    println!("=== FFI Protobuf vs Direct Benchmark ===");
    println!("Iterations: {}", iterations);
    println!();

    // FFI with RequestType::Get (uses protobuf routing internally)
    let protobuf_tps = run_ffi_benchmark(iterations, RequestType::Get);
    
    // FFI with RequestType::CustomCommand (manual command path)
    let custom_tps = run_ffi_benchmark(iterations, RequestType::CustomCommand);
    
    println!("Results:");
    println!("  FFI RequestType::Get TPS:        {:.2}", protobuf_tps);
    println!("  FFI RequestType::CustomCommand:  {:.2}", custom_tps);
    println!("  Difference:                      {:.2}% ({:.2} TPS)", 
        ((protobuf_tps - custom_tps) / custom_tps * 100.0),
        (protobuf_tps - custom_tps)
    );
    
    if protobuf_tps > custom_tps {
        println!("  → RequestType::Get is {:.1}x faster", protobuf_tps / custom_tps);
    } else {
        println!("  → CustomCommand is {:.1}x faster", custom_tps / protobuf_tps);
    }
}
