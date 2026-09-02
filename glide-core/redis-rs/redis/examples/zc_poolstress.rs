//! Pure pool-contention microbenchmark — no I/O, no server.
//!
//! Each thread builds and drops a `Cmd` with one payload argument. When the
//! payload exceeds SHARED_ARG_INLINE_MAX (4 KiB), `write_arg` routes it
//! through the recycled buffer pool: one pool pop + memcpy on build, one
//! pool push on drop. This isolates exactly the pool lock under thread
//! parallelism.
//!
//! Usage: zc_poolstress <threads> <payload_size> <iters_per_thread>
//! Output: threads,size,iters,total_ops,secs,ops_per_sec
use std::time::Instant;

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let threads: usize = args[1].parse().unwrap();
    let size: usize = args[2].parse().unwrap();
    let iters: usize = args[3].parse().unwrap();

    let payload = std::sync::Arc::new(vec![b'x'; size]);
    let start = Instant::now();
    let handles: Vec<_> = (0..threads)
        .map(|t| {
            let payload = payload.clone();
            std::thread::spawn(move || {
                let mut sink = 0usize;
                for i in 0..iters {
                    let mut cmd = redis::cmd("SET");
                    cmd.arg("k").arg(&payload[..]);
                    // Touch the packed form so the build can't be elided.
                    sink = sink.wrapping_add(cmd.get_packed_segments().len() + t + i);
                    // Cmd drops here -> pooled Bytes drops -> pool push.
                }
                sink
            })
        })
        .collect();
    let mut sink = 0usize;
    for h in handles {
        sink = sink.wrapping_add(h.join().unwrap());
    }
    let secs = start.elapsed().as_secs_f64();
    let total = threads * iters;
    println!(
        "{threads},{size},{iters},{total},{secs:.3},{:.0},sink={sink}",
        total as f64 / secs
    );
}
