// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use criterion::{Criterion, criterion_group, criterion_main, BenchmarkId};
use futures::future::join_all;
use glide_core::{
    client::StandaloneClient,
    connection_request::{self, NodeAddress, ProtocolVersion, TlsMode},
    request_type::RequestType,
};
use redis::Value;
use std::env;
use std::time::Instant;
use tokio::runtime::{Builder, Runtime};
use tokio::sync::mpsc;

#[derive(Clone)]
struct BenchmarkConfig {
    threads: usize,
    iterations: usize,
    sync_mode: bool, // true for sync, false for async
    direct_mode: bool, // true for direct redis calls, false for protobuf commands
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

async fn run_get_batch_sync(iterations: usize, direct_mode: bool) -> (Vec<Result<Value, redis::RedisError>>, f64) {
    let start = Instant::now();
    let mut results = Vec::with_capacity(iterations);
    
    let mut client = setup_client().await;
    for _ in 0..iterations {
        let result = if direct_mode {
            run_single_get_direct(&mut client).await
        } else {
            run_single_get_protobuf(&mut client).await
        };
        results.push(result);
    }
    
    let duration = start.elapsed();
    let tps = iterations as f64 / duration.as_secs_f64();
    (results, tps)
}

async fn run_get_batch_async(iterations: usize, direct_mode: bool) -> (Vec<Result<Value, redis::RedisError>>, f64) {
    let start = Instant::now();
    
    let futures: Vec<_> = (0..iterations)
        .map(|_| async {
            let mut client = setup_client().await;
            if direct_mode {
                run_single_get_direct(&mut client).await
            } else {
                run_single_get_protobuf(&mut client).await
            }
        })
        .collect();
    let results = join_all(futures).await;
    
    let duration = start.elapsed();
    let tps = iterations as f64 / duration.as_secs_f64();
    (results, tps)
}

fn benchmark_get_commands(c: &mut Criterion, runtime: &Runtime) {
    let config = BenchmarkConfig::from_env();

    let mut group = c.benchmark_group("get_commands");
    group.significance_level(0.1).sample_size(50);

    let mode_suffix = if config.direct_mode { "_direct" } else { "_protobuf" };

    // Single threaded benchmarks
    if config.sync_mode {
        group.bench_function(format!("single_thread_sync{}", mode_suffix), |b| {
            b.to_async(runtime).iter(|| async {
                let (_results, tps) = run_get_batch_sync(config.iterations, config.direct_mode).await;
                println!("Single thread sync{} TPS: {:.2}", mode_suffix, tps);
                tps
            });
        });
    } else {
        group.bench_function(format!("single_thread_async{}", mode_suffix), |b| {
            b.to_async(runtime).iter(|| async {
                let (_results, tps) = run_get_batch_async(config.iterations, config.direct_mode).await;
                println!("Single thread async{} TPS: {:.2}", mode_suffix, tps);
                tps
            });
        });
    }

    // Multi-threaded benchmarks
    for thread_count in [1, 2, 4, 8].iter().filter(|&&t| t <= config.threads) {
        let iterations_per_thread = config.iterations / thread_count;
        
        if config.sync_mode {
            group.bench_with_input(
                BenchmarkId::new(format!("multi_thread_sync{}", mode_suffix), thread_count),
                thread_count,
                |b, &thread_count| {
                    b.to_async(runtime).iter(|| async {
                        let start = Instant::now();
                        let handles: Vec<_> = (0..thread_count)
                            .map(|_| {
                                let direct_mode = config.direct_mode;
                                tokio::spawn(async move {
                                    run_get_batch_sync(iterations_per_thread, direct_mode).await
                                })
                            })
                            .collect();
                        
                        let _results = join_all(handles).await;
                        let total_duration = start.elapsed();
                        let total_ops = config.iterations;
                        let total_tps = total_ops as f64 / total_duration.as_secs_f64();
                        
                        println!("Multi thread ({}) sync{} TPS: {:.2}", thread_count, mode_suffix, total_tps);
                        total_tps
                    });
                },
            );
        } else {
            group.bench_with_input(
                BenchmarkId::new(format!("multi_thread_async{}", mode_suffix), thread_count),
                thread_count,
                |b, &thread_count| {
                    b.to_async(runtime).iter(|| async {
                        let start = Instant::now();
                        let handles: Vec<_> = (0..thread_count)
                            .map(|_| {
                                let direct_mode = config.direct_mode;
                                tokio::spawn(async move {
                                    run_get_batch_async(iterations_per_thread, direct_mode).await
                                })
                            })
                            .collect();
                        
                        let _results = join_all(handles).await;
                        let total_duration = start.elapsed();
                        let total_ops = config.iterations;
                        let total_tps = total_ops as f64 / total_duration.as_secs_f64();
                        
                        println!("Multi thread ({}) async{} TPS: {:.2}", thread_count, mode_suffix, total_tps);
                        total_tps
                    });
                },
            );
        }
    }

    group.finish();
}

fn get_benchmark(c: &mut Criterion) {
    let runtime = Builder::new_multi_thread()
        .worker_threads(8)
        .enable_all()
        .build()
        .expect("Failed to create Tokio runtime");

    benchmark_get_commands(c, &runtime);
}

criterion_group!(benches, get_benchmark);
criterion_main!(benches);
