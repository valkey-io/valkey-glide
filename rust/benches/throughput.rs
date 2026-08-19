// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Latency and throughput benchmarks for the GLIDE Rust client.
//!
//! Boots an ephemeral `valkey-server`, then measures:
//!   * single-op latency for `SET`, `GET`, `INCR` (via Criterion), and
//!   * end-to-end throughput at several concurrency levels (printed to stdout).
//!
//! Run with: `cargo bench`. If no server binary is available the bench skips.

use criterion::{BenchmarkId, Criterion, Throughput};
use glide::{AsyncCommands, GlideClient, GlideClientConfiguration};
use std::net::TcpListener;
use std::process::{Child, Command, Stdio};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::runtime::Runtime;

struct Server {
    child: Child,
    port: u16,
}

impl Drop for Server {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

fn server_binary() -> Option<String> {
    if let Ok(p) = std::env::var("VALKEY_SERVER_PATH")
        && std::path::Path::new(&p).exists()
    {
        return Some(p);
    }
    for name in ["valkey-server", "redis-server"] {
        if let Ok(o) = Command::new("which").arg(name).output()
            && o.status.success()
        {
            let p = String::from_utf8_lossy(&o.stdout).trim().to_string();
            if !p.is_empty() {
                return Some(p);
            }
        }
    }
    None
}

fn start_server() -> Option<Server> {
    let bin = server_binary()?;
    let port = TcpListener::bind("127.0.0.1:0")
        .ok()?
        .local_addr()
        .ok()?
        .port();
    let child = Command::new(&bin)
        .args([
            "--port",
            &port.to_string(),
            "--save",
            "",
            "--appendonly",
            "no",
        ])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;
    let deadline = Instant::now() + Duration::from_secs(10);
    while Instant::now() < deadline {
        if std::net::TcpStream::connect(("127.0.0.1", port)).is_ok() {
            std::thread::sleep(Duration::from_millis(100));
            return Some(Server { child, port });
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    None
}

/// Manual throughput probe: fire `total` operations at a given concurrency and
/// report ops/sec.
async fn throughput(client: Arc<GlideClient>, total: usize, concurrency: usize) -> f64 {
    let start = Instant::now();
    let per_task = total / concurrency;
    let mut handles = Vec::with_capacity(concurrency);
    for t in 0..concurrency {
        let c = client.clone();
        handles.push(tokio::spawn(async move {
            for i in 0..per_task {
                let key = format!("thr:{t}:{}", i % 1000);
                c.set::<_, _, ()>(&key, "v").await.unwrap();
            }
        }));
    }
    for h in handles {
        h.await.unwrap();
    }
    let elapsed = start.elapsed().as_secs_f64();
    (per_task * concurrency) as f64 / elapsed
}

fn main() {
    let server = match start_server() {
        Some(s) => s,
        None => {
            eprintln!("SKIP benchmarks: no valkey-server binary available");
            return;
        }
    };
    let rt = Runtime::new().unwrap();
    let client = rt.block_on(async {
        GlideClient::connect(GlideClientConfiguration::with_address(
            "127.0.0.1",
            server.port,
        ))
        .await
        .expect("connect")
    });
    let client = Arc::new(client);

    // Seed a key for GET.
    rt.block_on(async { client.set::<_, _, ()>("bench:get", "value").await.unwrap() });

    // ---- Manual throughput probe (concrete numbers, printed) ----
    println!("\n=== GLIDE Rust throughput (SET, pipelined via concurrency) ===");
    for &conc in &[1usize, 8, 32, 128] {
        let ops = throughput(client.clone(), 20_000, conc).await_or(&rt);
        println!("  concurrency={conc:>4}  ->  {ops:>12.0} ops/sec");
    }

    // ---- Criterion latency benchmarks ----
    let mut crit = Criterion::default()
        .sample_size(50)
        .measurement_time(Duration::from_secs(3))
        .warm_up_time(Duration::from_millis(500))
        .configure_from_args();

    let mut group = crit.benchmark_group("latency");
    group.throughput(Throughput::Elements(1));

    group.bench_function("set", |b| {
        b.to_async(&rt).iter(|| {
            let c = client.clone();
            async move {
                c.set::<_, _, ()>("bench:set", "value").await.unwrap();
            }
        })
    });

    group.bench_function("get", |b| {
        b.to_async(&rt).iter(|| {
            let c = client.clone();
            async move {
                let _: Option<String> = c.get("bench:get").await.unwrap();
            }
        })
    });

    group.bench_function("incr", |b| {
        b.to_async(&rt).iter(|| {
            let c = client.clone();
            async move {
                let _: i64 = c.incr("bench:incr", 1).await.unwrap();
            }
        })
    });

    for &conc in &[8usize, 32] {
        group.bench_with_input(
            BenchmarkId::new("set_concurrent", conc),
            &conc,
            |b, &conc| {
                b.to_async(&rt).iter(|| {
                    let c = client.clone();
                    async move {
                        let mut hs = Vec::with_capacity(conc);
                        for i in 0..conc {
                            let c = c.clone();
                            hs.push(tokio::spawn(async move {
                                c.set::<_, _, ()>(format!("bc:{i}"), "v").await.unwrap();
                            }));
                        }
                        for h in hs {
                            h.await.unwrap();
                        }
                    }
                })
            },
        );
    }

    group.finish();
    crit.final_summary();
}

/// Small helper to block on a future from sync `main` using an existing runtime.
trait AwaitOr {
    fn await_or(self, rt: &Runtime) -> f64;
}
impl<F: std::future::Future<Output = f64>> AwaitOr for F {
    fn await_or(self, rt: &Runtime) -> f64 {
        rt.block_on(self)
    }
}
