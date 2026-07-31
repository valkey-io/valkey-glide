//! Multi-connection pool-contention benchmark (harness v3).
//!
//! Measures the recycled buffer pool (`buf_pool`) under multi-connection
//! load: C independent MultiplexedConnections on a multi-threaded runtime,
//! each with `depth` persistent pipelined workers. Every >4KiB payload
//! (GET receive frames, SET send args) takes the pool lock on alloc and
//! on drop, so lock contention scales with connections × runtime threads.
//!
//! Usage: zc_multiconn <host:port> <mode:get|set> <value_size> <duration_secs> <depth> <conns> <threads>
//! Output: one CSV line: mode,size,depth,conns,threads,ops,secs,ops_per_sec,p50us,p95us,p99us
use redis::{aio::MultiplexedConnection, Client};
use std::time::Instant;

async fn get_conn(hostport: &str) -> MultiplexedConnection {
    let client = Client::open(format!("redis://{hostport}")).unwrap();
    let opts = redis::GlideConnectionOptions {
        tcp_nodelay: true,
        ..Default::default()
    };
    client.get_multiplexed_tokio_connection(opts).await.unwrap()
}

async fn one(mode: &str, c: &mut MultiplexedConnection, j: usize, size: usize, payload: &[u8]) {
    match mode {
        "get" => {
            let mut cmd = redis::cmd("GET");
            cmd.arg(format!("zc:{}", j % 100));
            match c.send_packed_command(&cmd).await.unwrap() {
                redis::Value::BulkString(b) => assert_eq!(b.len(), size),
                o => panic!("{o:?}"),
            }
        }
        "set" => {
            let mut cmd = redis::cmd("SET");
            cmd.arg(format!("zc:{}", j % 100)).arg(payload);
            assert_eq!(
                c.send_packed_command(&cmd).await.unwrap(),
                redis::Value::Okay
            );
        }
        _ => panic!("bad mode"),
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let hostport = args[1].clone();
    let mode = args[2].clone();
    let size: usize = args[3].parse().unwrap();
    let dur: f64 = args[4].parse().unwrap();
    let depth: usize = args[5].parse().unwrap();
    let conns: usize = args[6].parse().unwrap();
    let threads: usize = args[7].parse().unwrap();

    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(threads)
        .enable_all()
        .build()
        .unwrap();

    rt.block_on(async move {
        let payload = std::sync::Arc::new(vec![b'x'; size]);

        // Preload keys once.
        let mut c0 = get_conn(&hostport).await;
        for i in 0..100usize {
            let mut cmd = redis::cmd("SET");
            cmd.arg(format!("zc:{i}")).arg(&payload[..]);
            assert_eq!(
                c0.send_packed_command(&cmd).await.unwrap(),
                redis::Value::Okay
            );
        }

        let mut connections = Vec::with_capacity(conns);
        for _ in 0..conns {
            connections.push(get_conn(&hostport).await);
        }

        // Persistent pipelined workers per connection until deadline
        // (same worker model as harness v2 — no per-batch futures Vec).
        let run_window = |secs: f64| {
            let connections = connections.clone();
            let mode = mode.clone();
            let payload = payload.clone();
            async move {
                let start = Instant::now();
                let mut tasks = Vec::with_capacity(conns * depth);
                for (ci, conn) in connections.into_iter().enumerate() {
                    for w in 0..depth {
                        let mut c = conn.clone();
                        let mode = mode.clone();
                        let payload = payload.clone();
                        tasks.push(tokio::spawn(async move {
                            let mut n = 0usize;
                            let mut j = ci * depth + w;
                            let mut lats: Vec<u64> = Vec::with_capacity(8192);
                            while start.elapsed().as_secs_f64() < secs {
                                let t0 = Instant::now();
                                one(&mode, &mut c, j, payload.len(), &payload).await;
                                lats.push(t0.elapsed().as_micros() as u64);
                                n += 1;
                                j += depth;
                            }
                            (n, lats)
                        }));
                    }
                }
                let mut done = 0usize;
                let mut all: Vec<u64> = Vec::new();
                for t in tasks {
                    let (n, mut l) = t.await.unwrap();
                    done += n;
                    all.append(&mut l);
                }
                all.sort_unstable();
                let pct = |p: f64| -> u64 {
                    if all.is_empty() {
                        0
                    } else {
                        all[((all.len() - 1) as f64 * p) as usize]
                    }
                };
                (
                    done,
                    start.elapsed().as_secs_f64(),
                    pct(0.50),
                    pct(0.95),
                    pct(0.99),
                )
            }
        };

        // Warmup (uncounted).
        let warm = (dur * 0.2).min(1.0);
        if warm > 0.0 {
            let _ = run_window(warm).await;
        }
        let (ops, secs, p50, p95, p99) = run_window(dur).await;
        println!(
            "{mode},{size},{depth},{conns},{threads},{ops},{secs:.3},{:.0},{p50},{p95},{p99}",
            ops as f64 / secs
        );
    });
}
