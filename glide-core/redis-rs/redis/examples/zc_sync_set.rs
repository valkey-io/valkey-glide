//! Sync (blocking) connection SET throughput/CPU benchmark.
//!
//! Isolates the sync `Connection::req_command` send path: single blocking
//! connection, SET of a fixed-size payload in a tight loop. No pipelining —
//! each SET is one request/response round trip, so this measures the
//! per-command send cost (where the vectored/zero-copy send lands).
//!
//! Usage: zc_sync_set <port> <mode:arg|shared> <value_size> <total_ops>
use redis::ConnectionLike;
use std::time::Instant;

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let port: u16 = args[1].parse().unwrap();
    let mode = args[2].clone();
    let size: usize = args[3].parse().unwrap();
    let total_ops: usize = args[4].parse().unwrap();

    let client = redis::Client::open(format!("redis://127.0.0.1:{port}")).unwrap();
    let mut con = client.get_connection(None).unwrap();

    let payload = vec![b'x'; size];
    let shared = bytes::Bytes::from(payload.clone());

    let start = Instant::now();
    for i in 0..total_ops {
        let mut cmd = redis::cmd("SET");
        cmd.arg(format!("zc:{}", i % 100));
        match mode.as_str() {
            // Plain arg() — large payloads auto-share inside write_arg.
            "arg" => {
                cmd.arg(&payload[..]);
            }
            // Explicit arg_shared — zero-copy from the caller's Bytes.
            "shared" => {
                cmd.arg_shared(shared.clone());
            }
            _ => panic!("bad mode"),
        }
        let v = con.req_command(&cmd).unwrap();
        assert_eq!(v, redis::Value::Okay);
    }
    let elapsed = start.elapsed();
    println!(
        "sync-set mode={mode} size={size} ops={total_ops} elapsed={:.3}s ops_per_sec={:.0}",
        elapsed.as_secs_f64(),
        total_ops as f64 / elapsed.as_secs_f64()
    );
}
