use criterion::{Criterion, criterion_group, criterion_main};
use glide_core::connection_request::{ConnectionRequest, NodeAddress, TlsMode};
use glide_core::request_type::RequestType;
use glide_ffi::*;
use protobuf::Message;
use std::env;
use std::ffi::{c_ulong, c_void, CStr, c_char};
use std::sync::{Arc, RwLock, atomic::{AtomicUsize, Ordering}};
use std::collections::HashMap;
use std::time::Instant;
use tokio::runtime::Builder;
use glide_core::errors::RequestErrorType;

struct AsyncMetrics {
    pub success_count: AtomicUsize,
    pub failure_count: AtomicUsize,
    pub results: HashMap<usize, Result<String, (String, RequestErrorType)>>,
}

static ASYNC_METRICS: std::sync::LazyLock<Arc<RwLock<AsyncMetrics>>> = std::sync::LazyLock::new(|| {
    Arc::new(RwLock::new(AsyncMetrics {
        success_count: AtomicUsize::new(0),
        failure_count: AtomicUsize::new(0),
        results: HashMap::new(),
    }))
});

extern "C-unwind" fn string_success_callback(index: usize, response_ptr: *const CommandResponse) {
    let mut metrics = ASYNC_METRICS.write().expect("Failed to acquire write lock");
    metrics.results.insert(index, Ok(parse_string_res(response_ptr)));
    metrics.success_count.fetch_add(1, Ordering::SeqCst);
}

extern "C-unwind" fn failure_callback(
    index: usize,
    err_msg_ptr: *const c_char,
    error_type: RequestErrorType,
) {
    let mut metrics = ASYNC_METRICS.write().expect("Failed to acquire write lock");
    metrics.results.insert(index, Err((parse_error_msg(err_msg_ptr), error_type)));
    metrics.failure_count.fetch_add(1, Ordering::SeqCst);
}

fn parse_string_res(response_ptr: *const CommandResponse) -> String {
    assert!(!response_ptr.is_null());
    let response: &CommandResponse = unsafe { &*response_ptr };
    assert!(!response.string_value.is_null());
    let bytes = unsafe {
        std::slice::from_raw_parts(
            response.string_value as *const u8,
            response.string_value_len as usize,
        )
    };
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
            Some(parse_string_res(result.response))
        } else {
            None
        }
    } else {
        None
    }
}

fn run_ffi_benchmark(iterations: usize, use_request_type: bool) -> f64 {
    let client_ptr = create_ffi_client();
    let start = Instant::now();
    
    for i in 0..iterations {
        let command_type = if use_request_type {
            RequestType::Get  // Uses GLIDE's RequestType enum (FFI protobuf path)
        } else {
            RequestType::CustomCommand  // Forces manual command construction
        };
        
        let _result = execute_ffi_command(client_ptr, i, b"benchmark_key", b"", command_type);
    }
    
    let duration = start.elapsed();
    unsafe { close_client(client_ptr) };
    
    iterations as f64 / duration.as_secs_f64()
}

fn benchmark_ffi_comparison(c: &mut Criterion) {
    let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
    let iterations = env::var("BENCH_ITERATIONS")
        .unwrap_or_else(|_| "10000".to_string())
        .parse()
        .unwrap_or(10000);

    let mut group = c.benchmark_group("ffi_protobuf_vs_direct");
    group.significance_level(0.1).sample_size(10).measurement_time(std::time::Duration::from_secs(30));

    group.bench_function("comparison", |b| {
        b.to_async(&runtime).iter(|| async {
            // FFI with RequestType (protobuf path)
            let protobuf_tps = run_ffi_benchmark(iterations, true);
            
            // FFI with CustomCommand (manual path)  
            let direct_tps = run_ffi_benchmark(iterations, false);
            
            println!("FFI Protobuf (RequestType) TPS: {:.2}", protobuf_tps);
            println!("FFI Direct (CustomCommand) TPS: {:.2}", direct_tps);
            println!("Difference: {:.2}% ({:.2} TPS)", 
                ((protobuf_tps - direct_tps) / direct_tps * 100.0),
                (protobuf_tps - direct_tps)
            );
            
            (protobuf_tps, direct_tps)
        });
    });

    group.finish();
}

criterion_group!(benches, benchmark_ffi_comparison);
criterion_main!(benches);
