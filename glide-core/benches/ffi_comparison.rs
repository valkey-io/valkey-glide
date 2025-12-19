use criterion::{Criterion, criterion_group, criterion_main};
use glide_core::{
    client::StandaloneClient,
    connection_request::{self, NodeAddress, ProtocolVersion, TlsMode},
    request_type::RequestType,
};
use redis::Value;
use std::env;
use std::time::Instant;
use tokio::runtime::Builder;
use tokio::sync::mpsc;

// Import FFI functions
extern "C" {
    fn create_client(
        connection_request_bytes: *const u8,
        connection_request_len: usize,
        success_callback: extern "C" fn(*const std::ffi::c_void, usize, *mut std::ffi::c_void),
        failure_callback: extern "C" fn(*const std::ffi::c_void, usize, *mut std::ffi::c_void),
    ) -> *mut std::ffi::c_void;
    
    fn command(
        client_adapter_ptr: *const std::ffi::c_void,
        request_id: usize,
        command_type: RequestType,
        arg_count: std::os::raw::c_ulong,
        args: *const usize,
        args_len: *const std::os::raw::c_ulong,
        route_bytes: *const u8,
        route_bytes_len: usize,
        span_ptr: u64,
    ) -> *mut std::ffi::c_void;
}

#[derive(Clone)]
struct BenchmarkConfig {
    iterations: usize,
    direct_mode: bool,
}

impl BenchmarkConfig {
    fn from_env() -> Self {
        Self {
            iterations: env::var("BENCH_ITERATIONS")
                .unwrap_or_else(|_| "1000".to_string())
                .parse()
                .unwrap_or(1000),
            direct_mode: env::var("BENCH_DIRECT_MODE")
                .unwrap_or_else(|_| "false".to_string())
                .parse()
                .unwrap_or(false),
        }
    }
}

async fn setup_client() -> StandaloneClient {
    let mut node_address = NodeAddress::new();
    node_address.host = "127.0.0.1".into();
    node_address.port = 6379;
    
    let mut connection_request = connection_request::ConnectionRequest::new();
    connection_request.addresses = vec![node_address];
    connection_request.tls_mode = TlsMode::NoTls.into();
    connection_request.cluster_mode_enabled = false;
    connection_request.request_timeout = 250;
    connection_request.protocol = ProtocolVersion::RESP2.into();
    connection_request.client_name = "get_benchmark".into();
    connection_request.database_id = 0;

    let (push_sender, _push_receiver) = mpsc::unbounded_channel();
    let mut client = StandaloneClient::create_client(connection_request.into(), Some(push_sender), None)
        .await
        .expect("Failed to create client");

    // Setup: SET the benchmark key
    let mut set_cmd = redis::Cmd::new();
    set_cmd.arg("SET").arg("benchmark_key").arg("test_value");
    client.send_command(&set_cmd).await.expect("Failed to set benchmark key");

    client
}

async fn run_single_get_protobuf(client: &mut StandaloneClient) -> Result<Value, redis::RedisError> {
    let mut cmd = redis::Cmd::new();
    cmd.arg("GET").arg("benchmark_key");
    client.send_command(&cmd).await
}

async fn run_single_get_direct(client: &mut StandaloneClient) -> Result<Value, redis::RedisError> {
    let mut cmd = RequestType::Get.get_command().expect("Failed to get GET command");
    cmd.arg("benchmark_key");
    client.send_command(&cmd).await
}

async fn run_benchmark_comparison(iterations: usize) -> (f64, f64) {
    // Protobuf mode (manual command construction)
    let start = Instant::now();
    let mut client = setup_client().await;
    for _ in 0..iterations {
        let _result = run_single_get_protobuf(&mut client).await;
    }
    let protobuf_tps = iterations as f64 / start.elapsed().as_secs_f64();
    
    // Direct mode (RequestType enum)
    let start = Instant::now();
    let mut client = setup_client().await;
    for _ in 0..iterations {
        let _result = run_single_get_direct(&mut client).await;
    }
    let direct_tps = iterations as f64 / start.elapsed().as_secs_f64();
    
    (protobuf_tps, direct_tps)
}

fn benchmark_comparison(c: &mut Criterion) {
    let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
    let config = BenchmarkConfig::from_env();

    let mut group = c.benchmark_group("protobuf_vs_direct_comparison");
    group.significance_level(0.1).sample_size(5);

    group.bench_function("comparison", |b| {
        b.to_async(&runtime).iter(|| async {
            let (protobuf_tps, direct_tps) = run_benchmark_comparison(config.iterations).await;
            println!("Protobuf (manual) TPS: {:.2}", protobuf_tps);
            println!("Direct (RequestType) TPS: {:.2}", direct_tps);
            println!("Difference: {:.2}% ({:.2} TPS)", 
                ((direct_tps - protobuf_tps) / protobuf_tps * 100.0),
                (direct_tps - protobuf_tps)
            );
            (protobuf_tps, direct_tps)
        });
    });

    group.finish();
}

criterion_group!(benches, benchmark_comparison);
criterion_main!(benches);
