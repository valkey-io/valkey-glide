//! Clean e2e receive-path benchmark: pipelined GET / MGET against a live
//! server through MultiplexedConnection. No workload generation in the hot
//! loop — isolates client receive-path CPU.
//!
//! Usage: zc_e2e <port> <mode:get|mget> <value_size> <total_ops> <pipeline_depth>
use futures::future::join_all;
use redis::{aio::MultiplexedConnection, AsyncCommands, Client};
use std::time::Instant;

async fn get_conn(port: u16) -> MultiplexedConnection {
    let client = Client::open(format!("redis://127.0.0.1:{port}")).unwrap();
    client
        .get_multiplexed_tokio_connection(redis::GlideConnectionOptions::default())
        .await
        .unwrap()
}

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() {
    let args: Vec<String> = std::env::args().collect();
    let port: u16 = args[1].parse().unwrap();
    let mode = args[2].clone();
    let size: usize = args[3].parse().unwrap();
    let total_ops: usize = args[4].parse().unwrap();
    let depth: usize = args[5].parse().unwrap();

    let mut conn = get_conn(port).await;
    let payload = vec![b'x'; size];

    // Preload keys.
    let nkeys = 100usize;
    for i in 0..nkeys {
        let _: () = conn.set(format!("zc:{i}"), &payload).await.unwrap();
    }
    let mget_keys: Vec<String> = (0..nkeys).map(|i| format!("zc:{i}")).collect();

    let start = Instant::now();
    let mut done = 0usize;
    while done < total_ops {
        let batch = depth.min(total_ops - done);
        let futs = (0..batch).map(|j| {
            let mut c = conn.clone();
            let mode = &mode;
            let keys = &mget_keys;
            async move {
                match mode.as_str() {
                    "get" => {
                        let v: Vec<u8> = c.get(format!("zc:{}", j % 100)).await.unwrap();
                        assert_eq!(v.len(), size);
                    }
                    // Consume the raw Value (glide-core's actual pattern: it
                    // reads BulkString bytes in place, no Vec conversion).
                    "getv" => {
                        let mut cmd = redis::cmd("GET");
                        cmd.arg(format!("zc:{}", j % 100));
                        let v = c.send_packed_command(&cmd).await.unwrap();
                        match v {
                            redis::Value::BulkString(b) => assert_eq!(b.len(), size),
                            other => panic!("unexpected {other:?}"),
                        }
                    }
                    "mgetv" => {
                        let mut cmd = redis::cmd("MGET");
                        for k in keys {
                            cmd.arg(k);
                        }
                        let v = c.send_packed_command(&cmd).await.unwrap();
                        match v {
                            redis::Value::Array(items) => assert_eq!(items.len(), keys.len()),
                            other => panic!("unexpected {other:?}"),
                        }
                    }
                    "mget" => {
                        let vs: Vec<Vec<u8>> = c.mget(keys).await.unwrap();
                        assert_eq!(vs.len(), keys.len());
                    }
                    _ => panic!("bad mode"),
                }
            }
        });
        join_all(futs).await;
        done += batch;
    }
    let elapsed = start.elapsed();
    println!(
        "mode={mode} size={size} ops={total_ops} depth={depth} elapsed={:.3}s ops_per_sec={:.0}",
        elapsed.as_secs_f64(),
        total_ops as f64 / elapsed.as_secs_f64()
    );
}
