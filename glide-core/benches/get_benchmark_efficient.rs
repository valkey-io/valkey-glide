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

#[derive(Clone)]
struct BenchmarkConfig {
    threads: usize,
    iterations: usize,
    sync_mode: bool,
    direct_mode: bool,
}

impl BenchmarkConfig {
    fn from_env() -> Self {
        Self {
            threads: env::var("BENCH_THREADS")
                .unwrap_or_else(|_| "4".to_string())
                .parse()
                .unwrap_or(4),
            iterations: env::var("BENCH_ITERATIONS")
                .unwrap_or_else(|_| "1000".to_string())
                .parse()
                .unwrap_or(1000),
            sync_mode: env::var("BENCH_SYNC_MODE")
                .unwrap_or_else(|_| "false".to_string())
                .parse()
                .unwrap_or(false),
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

async fn run_efficient_benchmark(iterations: usize, direct_mode: bool) -> f64 {
    let start = Instant::now();
    let mut client = setup_client().await;
    
    for _ in 0..iterations {
        let _result = if direct_mode {
            run_single_get_direct(&mut client).await
        } else {
            run_single_get_protobuf(&mut client).await
        };
    }
    
    let duration = start.elapsed();
    iterations as f64 / duration.as_secs_f64()
}

fn benchmark_get_commands(c: &mut Criterion) {
    let runtime = Builder::new_multi_thread().enable_all().build().unwrap();
    let config = BenchmarkConfig::from_env();
    let mode_suffix = if config.direct_mode { "_direct" } else { "_protobuf" };

    let mut group = c.benchmark_group("get_commands_efficient");
    group.significance_level(0.1).sample_size(10);

    // Single client benchmark
    group.bench_function(format!("single_client{}", mode_suffix), |b| {
        b.to_async(&runtime).iter(|| async {
            let tps = run_efficient_benchmark(config.iterations, config.direct_mode).await;
            println!("Single client{} TPS: {:.2}", mode_suffix, tps);
            tps
        });
    });

    group.finish();
}

criterion_group!(benches, benchmark_get_commands);
criterion_main!(benches);
