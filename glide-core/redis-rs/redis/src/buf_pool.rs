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

/// Maximum number of retained idle buffers. In-flight buffers are not
/// counted; this only bounds idle memory
/// (`POOL_MAX_COUNT * POOL_MAX_BUF_CAPACITY` worst case).
const POOL_MAX_COUNT: usize = 64;

static POOL: Mutex<Vec<Vec<u8>>> = Mutex::new(Vec::new());

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
        let mut pool = POOL.lock().unwrap_or_else(|e| e.into_inner());
        if pool.len() < POOL_MAX_COUNT {
            pool.push(buf);
        }
    }
}

/// Copy `data` into a recycled buffer and return it as [`Bytes`].
///
/// The backing allocation returns to the pool when the returned `Bytes` (and
/// every clone/slice of it) has dropped. Small copies (≤ [`POOL_MIN`]) use a
/// plain [`Bytes::copy_from_slice`].
pub(crate) fn pooled_bytes_from_slice(data: &[u8]) -> Bytes {
    if data.len() <= POOL_MIN {
        return Bytes::copy_from_slice(data);
    }
    let mut buf = {
        let mut pool = POOL.lock().unwrap_or_else(|e| e.into_inner());
        pool.pop().unwrap_or_default()
    };
    buf.clear();
    buf.extend_from_slice(data);
    Bytes::from_owner(PooledBuf(buf))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_and_reuse() {
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
        let payload = vec![1u8; 16];
        let b = pooled_bytes_from_slice(&payload);
        assert_eq!(&b[..], &payload[..]);
    }

    #[test]
    fn oversized_buffers_not_retained() {
        let big = vec![3u8; POOL_MAX_BUF_CAPACITY + 1];
        let b = pooled_bytes_from_slice(&big);
        assert_eq!(b.len(), big.len());
        // Exercises the oversized drop branch (buffer freed, not pooled).
        drop(b);
    }
}
