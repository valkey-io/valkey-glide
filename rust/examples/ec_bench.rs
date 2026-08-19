// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! End-to-end ElastiCache (cluster-mode Valkey) benchmark for the native Rust
//! GLIDE client. Connects to a real ElastiCache configuration endpoint via
//! `GlideClusterClient` and measures SET/GET p50/p99 latency and throughput
//! (ops/sec) across several concurrency levels.
//!
//! Configured entirely via environment variables so it can be driven remotely
//! over SSM without editing code:
//!   GLIDE_HOST   cluster configuration endpoint host (required)
//!   GLIDE_PORT   port (default 6379)
//!   GLIDE_TLS    one of: off | secure | insecure  (default: off)
//!   GLIDE_OPS    total ops per (op,concurrency) measurement (default 40000)
//!   GLIDE_CONC   comma-separated concurrency levels (default 1,8,32,128,512)
//!
//! Output is human-readable plus machine-parseable `RESULT` lines:
//!   `RESULT <op> tls=<mode> conc=<n> ops=<n> secs=<f> throughput_ops=<f> p50_us=<f> p99_us=<f> avg_us=<f>`
//!
//! Run with:
//!   GLIDE_HOST=<endpoint> cargo run --release --example ec_bench

use glide::{AsyncCommands, GlideClusterClient, GlideClusterClientConfiguration, TlsConfig};
use std::env;
use std::sync::Arc;
use std::time::Instant;

fn percentile(sorted_us: &[u64], p: f64) -> f64 {
    if sorted_us.is_empty() {
        return 0.0;
    }
    let rank = (p / 100.0 * (sorted_us.len() as f64 - 1.0)).round() as usize;
    sorted_us[rank.min(sorted_us.len() - 1)] as f64
}

/// Run `total` ops of `op` at the given `concurrency`, returning
/// (throughput_ops_per_sec, p50_us, p99_us, avg_us).
async fn measure(
    client: Arc<GlideClusterClient>,
    op: &str,
    total: usize,
    concurrency: usize,
) -> (f64, f64, f64, f64) {
    let per_task = (total / concurrency).max(1);
    let start = Instant::now();
    let mut handles = Vec::with_capacity(concurrency);
    for t in 0..concurrency {
        let c = client.clone();
        let op = op.to_string();
        handles.push(tokio::spawn(async move {
            let mut lats = Vec::with_capacity(per_task);
            for i in 0..per_task {
                // Vary keys so load spreads across all shards.
                let key = format!("bench:{{{}}}:{}", (t * 131 + i) % 4096, t);
                let t0 = Instant::now();
                match op.as_str() {
                    "SET" => {
                        c.set::<_, _, ()>(&key, "value-payload-0123456789")
                            .await
                            .unwrap();
                    }
                    "GET" => {
                        let _: Option<String> = c.get(&key).await.unwrap();
                    }
                    _ => unreachable!(),
                }
                lats.push(t0.elapsed().as_micros() as u64);
            }
            lats
        }));
    }
    let mut all: Vec<u64> = Vec::with_capacity(per_task * concurrency);
    for h in handles {
        all.extend(h.await.unwrap());
    }
    let elapsed = start.elapsed().as_secs_f64();
    let done = all.len();
    all.sort_unstable();
    let avg = all.iter().map(|&x| x as f64).sum::<f64>() / done.max(1) as f64;
    (
        done as f64 / elapsed,
        percentile(&all, 50.0),
        percentile(&all, 99.0),
        avg,
    )
}

#[tokio::main(flavor = "multi_thread")]
async fn main() {
    let host = env::var("GLIDE_HOST").expect("GLIDE_HOST is required");
    let port: u16 = env::var("GLIDE_PORT")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(6379);
    let tls_s = env::var("GLIDE_TLS").unwrap_or_else(|_| "off".into());
    let total: usize = env::var("GLIDE_OPS")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(40_000);
    let concs: Vec<usize> = env::var("GLIDE_CONC")
        .unwrap_or_else(|_| "1,8,32,128,512".into())
        .split(',')
        .filter_map(|s| s.trim().parse().ok())
        .collect();

    let tls = match tls_s.as_str() {
        "off" | "none" => TlsConfig::NoTls,
        "secure" => TlsConfig::SecureTls,
        "insecure" => TlsConfig::InsecureTls,
        other => panic!("bad GLIDE_TLS: {other}"),
    };

    eprintln!("connecting: host={host} port={port} tls={tls_s}");
    let config = GlideClusterClientConfiguration::with_address(host.clone(), port).tls(tls);
    let client = GlideClusterClient::connect(config)
        .await
        .expect("connect to ElastiCache cluster");
    let client = Arc::new(client);
    eprintln!("connected.");

    // Warm up + seed keys for GET.
    for op in ["SET", "GET"] {
        for &conc in &concs {
            // small warmup at this level
            let _ = measure(client.clone(), op, (conc * 200).max(2000), conc).await;
            let (thr, p50, p99, avg) = measure(client.clone(), op, total, conc).await;
            println!(
                "RESULT {op} tls={tls_s} conc={conc} ops={total} throughput_ops={thr:.0} p50_us={p50:.1} p99_us={p99:.1} avg_us={avg:.1}"
            );
        }
    }
    eprintln!("done.");
}
