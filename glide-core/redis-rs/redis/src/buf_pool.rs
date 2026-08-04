//! Recycled buffer pool for large transient payload copies.
//!
//! Two hot paths briefly need an owned buffer of tens-to-hundreds of KB whose
//! lifetime is tied to a refcounted [`Bytes`]:
//!
//! - the RESP decoder copies each complete frame out of the read buffer
//!   (`ValueCodec::decode_stream`), and
//! - command building copies large borrowed args into shared payload segments
//!   (`Cmd`'s `write_arg` auto-share).
//!
//! Allocating these buffers fresh per operation causes page-fault churn under
//! pipelined load: with N requests in flight, N large buffers are alive
//! concurrently, the allocator cannot recycle freed ones fast enough, and
//! every new allocation's pages must be faulted in on first touch. Profiling
//! against a real network peer showed this fault handling dominating client
//! CPU at 64 KB payloads (30% of cycles, ~37 faults/op at pipeline depth 16).
//!
//! The pool recycles the underlying allocations so pages stay resident: a
//! buffer returns here when the last `Bytes` referencing it drops, and the
//! next operation reuses it (already-faulted pages) instead of allocating.

use std::sync::Mutex;

use bytes::Bytes;

/// Copies at or below this size bypass the pool: the allocator's small size
/// classes recycle them well and they touch few fresh pages.
pub(crate) const POOL_MIN: usize = 4 * 1024;

/// Buffers larger than this are not retained, so a burst of huge values
/// cannot leave large allocations parked in the pool.
const POOL_MAX_BUF_CAPACITY: usize = 1024 * 1024;

/// Number of independent pool shards. Threads are assigned a shard
/// round-robin, so concurrent connections on different runtime threads don't
/// serialize on a single process-wide lock for every large payload.
const SHARD_COUNT: usize = 8;

/// Maximum number of retained idle buffers **per shard**. In-flight buffers
/// are not counted; this only bounds idle memory
/// (`SHARD_COUNT * SHARD_MAX_COUNT * POOL_MAX_BUF_CAPACITY` = 64 MiB worst
/// case process-wide, unchanged from the previous single-pool bound).
const SHARD_MAX_COUNT: usize = 8;

static POOL: [Mutex<Vec<Vec<u8>>>; SHARD_COUNT] = [
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
    Mutex::new(Vec::new()),
];

/// The shard this thread checks first. Buffers may be popped from one shard
/// and returned to another (a `Bytes` can be dropped on any thread); the
/// pool is a best-effort cache, so that only shifts where buffers idle.
fn shard() -> &'static Mutex<Vec<Vec<u8>>> {
    use std::sync::atomic::{AtomicUsize, Ordering};
    static NEXT: AtomicUsize = AtomicUsize::new(0);
    thread_local! {
        static SHARD_IDX: usize = NEXT.fetch_add(1, Ordering::Relaxed) % SHARD_COUNT;
    }
    // TLS can be unavailable while a thread is being torn down (a `Bytes`
    // drop can run that late); fall back to shard 0 rather than panicking.
    let idx = SHARD_IDX.try_with(|i| *i).unwrap_or(0);
    &POOL[idx]
}

/// Owner type handed to [`Bytes::from_owner`]; returns its allocation to the
/// pool when the last referencing `Bytes` drops.
struct PooledBuf(Vec<u8>);

impl AsRef<[u8]> for PooledBuf {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

impl Drop for PooledBuf {
    fn drop(&mut self) {
        let buf = std::mem::take(&mut self.0);
        if buf.capacity() == 0 || buf.capacity() > POOL_MAX_BUF_CAPACITY {
            return;
        }
        // A poisoned lock only means another thread panicked mid push/pop;
        // the Vec is still structurally valid, so keep recycling.
        let mut pool = shard().lock().unwrap_or_else(|e| e.into_inner());
        if pool.len() < SHARD_MAX_COUNT {
            pool.push(buf);
        }
    }
}

/// Copy `data` into a recycled buffer and return it as [`Bytes`].
///
/// The backing allocation returns to the pool when the returned `Bytes` (and
/// every clone/slice of it) has dropped. Small copies (≤ [`POOL_MIN`]) use a
/// plain [`Bytes::copy_from_slice`]. Oversized copies
/// (> [`POOL_MAX_BUF_CAPACITY`]) bypass the pool entirely: no pooled buffer
/// can satisfy them without an immediate realloc, so popping one would only
/// drain a warm buffer from mid-size traffic and then discard it (the drop
/// hook never retains capacities above the cap).
pub(crate) fn pooled_bytes_from_slice(data: &[u8]) -> Bytes {
    if data.len() <= POOL_MIN {
        return Bytes::copy_from_slice(data);
    }
    if data.len() > POOL_MAX_BUF_CAPACITY {
        return Bytes::copy_from_slice(data);
    }
    let mut buf = {
        let mut pool = shard().lock().unwrap_or_else(|e| e.into_inner());
        pool.pop().unwrap_or_default()
    };
    buf.clear();
    buf.extend_from_slice(data);
    Bytes::from_owner(PooledBuf(buf))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Pool tests assert on shared shard state; serialize them so one test's
    /// pops/pushes can't race another's assertions (test threads round-robin
    /// onto the same 8 shards).
    ///
    /// NOTE: this guard only covers tests in this module. Any other lib test
    /// that routes a >[`POOL_MIN`] payload through
    /// [`pooled_bytes_from_slice`] (e.g. decoding a large frame through the
    /// codec) touches the same shards without taking `SERIAL` and would race
    /// the shard-state assertions below. Keep such tests out of the lib test
    /// binary or extend this guard if that ever happens.
    static SERIAL: Mutex<()> = Mutex::new(());

    #[test]
    fn roundtrip_and_reuse() {
        let _g = SERIAL.lock().unwrap_or_else(|e| e.into_inner());
        let payload = vec![7u8; POOL_MIN + 1];
        let b = pooled_bytes_from_slice(&payload);
        assert_eq!(&b[..], &payload[..]);
        // Slices keep the buffer alive; dropping all of them recycles it.
        let slice = b.slice(1..100);
        drop(b);
        assert_eq!(&slice[..], &payload[1..100]);
        drop(slice);

        // The next request should be able to reuse a pooled allocation and
        // must return the right contents regardless.
        let payload2 = vec![9u8; POOL_MIN + 512];
        let b2 = pooled_bytes_from_slice(&payload2);
        assert_eq!(&b2[..], &payload2[..]);
    }

    #[test]
    fn small_copies_bypass_pool() {
        let _g = SERIAL.lock().unwrap_or_else(|e| e.into_inner());
        let payload = vec![1u8; 16];
        let b = pooled_bytes_from_slice(&payload);
        assert_eq!(&b[..], &payload[..]);
    }

    #[test]
    fn oversized_buffers_not_retained() {
        let _g = SERIAL.lock().unwrap_or_else(|e| e.into_inner());
        let big = vec![3u8; POOL_MAX_BUF_CAPACITY + 1];
        let b = pooled_bytes_from_slice(&big);
        assert_eq!(b.len(), big.len());
        assert_eq!(&b[..4], &[3u8; 4]);
        // Oversized requests take the bypass (plain copy, nothing pooled).
        drop(b);
    }

    /// Regression: an oversized request must not pop (and then destroy) a
    /// warm pooled buffer that mid-size traffic could have reused.
    #[test]
    fn oversized_requests_do_not_drain_the_pool() {
        let _g = SERIAL.lock().unwrap_or_else(|e| e.into_inner());
        // A fresh thread gets a round-robin shard index — possibly one
        // shared with a previous thread, so the assertions below are
        // self-contained (fill to cap, then compare against the cap) rather
        // than assuming an empty shard. SERIAL keeps other pool tests from
        // touching the shards concurrently.
        std::thread::spawn(|| {
            // Fill this thread's shard to its retention cap: hold
            // SHARD_MAX_COUNT pooled buffers alive at once, then drop them
            // all so each parks.
            let held: Vec<_> = (0..SHARD_MAX_COUNT)
                .map(|_| pooled_bytes_from_slice(&vec![1u8; POOL_MIN + 1]))
                .collect();
            drop(held);
            let parked = shard().lock().unwrap_or_else(|e| e.into_inner()).len();
            assert_eq!(parked, SHARD_MAX_COUNT, "shard should be full");

            // Oversized request: must bypass the pool entirely.
            let big = pooled_bytes_from_slice(&vec![2u8; POOL_MAX_BUF_CAPACITY + 1]);
            assert_eq!(big.len(), POOL_MAX_BUF_CAPACITY + 1);
            drop(big);

            let after = shard().lock().unwrap_or_else(|e| e.into_inner()).len();
            assert_eq!(
                after, SHARD_MAX_COUNT,
                "oversized request consumed a pooled buffer"
            );
        })
        .join()
        .unwrap();
    }

    /// Concurrent pop/recycle across threads: every returned `Bytes` must
    /// contain exactly the caller's data (no cross-thread buffer mixups),
    /// including slices that outlive the parent handle.
    #[test]
    fn concurrent_reuse_is_correct() {
        let _g = SERIAL.lock().unwrap_or_else(|e| e.into_inner());
        let threads: Vec<_> = (0..8)
            .map(|t| {
                std::thread::spawn(move || {
                    for i in 0..200usize {
                        let fill = (t * 31 + i) as u8;
                        let len = POOL_MIN + 1 + (i % 3) * 1024;
                        let payload = vec![fill; len];
                        let b = pooled_bytes_from_slice(&payload);
                        let slice = b.slice(len / 2..len);
                        drop(b);
                        assert!(slice.iter().all(|&x| x == fill), "corrupted slice");
                        drop(slice); // recycles the buffer for other threads
                    }
                })
            })
            .collect();
        for t in threads {
            t.join().unwrap();
        }
    }
}
