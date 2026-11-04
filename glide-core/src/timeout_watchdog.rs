//! Dedicated timeout watchdog thread that fires timeouts independently of the
//! Tokio runtime and provides structured diagnostics about timeout causes.
//!
//! Design: The hot path (`register()`) pushes the deadline into a per-thread-sharded
//! inbox (an uncontended mutex) and does not wake the watchdog thread. The thread
//! drains the inboxes once per `WATCHDOG_TICK` and owns the deadline buckets
//! exclusively. (Previously: an std MPSC channel whose receiver slept to the next
//! nanosecond-resolution deadline, so it woke at roughly the registration rate.)
//!
//! The watchdog is a pure timer — it just signals "timeout fired" with `()`.
//! All diagnostic event construction moves to the consumer side (`client/mod.rs`).

use futures::task::AtomicWaker;
use std::collections::BTreeMap;
use std::pin::Pin;
use std::sync::atomic::{AtomicBool, AtomicU8, AtomicU64, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::task::{Context, Poll};
use std::time::{Duration, Instant};

// ─── Public Types ────────────────────────────────────────────────────────────

/// Published pending count: updated by the watchdog thread once per loop iteration.
/// Readers use relaxed load; only the watchdog thread writes (no contention).
static PUBLISHED_PENDING: AtomicUsize = AtomicUsize::new(0);

/// Returns the current number of pending (registered but not yet fired/cancelled)
/// watchdog entries.
pub fn pending_count() -> usize {
    PUBLISHED_PENDING.load(Ordering::Relaxed)
}

/// Resolve a command name from raw bytes to a &'static str without allocation.
/// Exhaustive coverage of all commands in the RequestType enum.
/// Falls back to "UNKNOWN" for unrecognized commands.
pub fn cmd_name_from_bytes(bytes: &[u8]) -> &'static str {
    match bytes.len() {
        3 => match_upper(
            bytes,
            &[
                (b"ACL", "ACL"),
                (b"DEL", "DEL"),
                (b"GET", "GET"),
                (b"LCS", "LCS"),
                (b"SET", "SET"),
                (b"TTL", "TTL"),
            ],
        ),
        4 => match_upper(
            bytes,
            &[
                (b"AUTH", "AUTH"),
                (b"COPY", "COPY"),
                (b"DECR", "DECR"),
                (b"DUMP", "DUMP"),
                (b"ECHO", "ECHO"),
                (b"EVAL", "EVAL"),
                (b"EXEC", "EXEC"),
                (b"HDEL", "HDEL"),
                (b"HGET", "HGET"),
                (b"HLEN", "HLEN"),
                (b"HSET", "HSET"),
                (b"HTTL", "HTTL"),
                (b"INCR", "INCR"),
                (b"INFO", "INFO"),
                (b"KEYS", "KEYS"),
                (b"LLEN", "LLEN"),
                (b"LPOP", "LPOP"),
                (b"LPOS", "LPOS"),
                (b"LREM", "LREM"),
                (b"LSET", "LSET"),
                (b"MGET", "MGET"),
                (b"MOVE", "MOVE"),
                (b"MSET", "MSET"),
                (b"PING", "PING"),
                (b"PTTL", "PTTL"),
                (b"QUIT", "QUIT"),
                (b"ROLE", "ROLE"),
                (b"RPOP", "RPOP"),
                (b"SADD", "SADD"),
                (b"SAVE", "SAVE"),
                (b"SCAN", "SCAN"),
                (b"SORT", "SORT"),
                (b"SPOP", "SPOP"),
                (b"SREM", "SREM"),
                (b"SYNC", "SYNC"),
                (b"TIME", "TIME"),
                (b"TYPE", "TYPE"),
                (b"WAIT", "WAIT"),
                (b"XACK", "XACK"),
                (b"XADD", "XADD"),
                (b"XDEL", "XDEL"),
                (b"XLEN", "XLEN"),
                (b"ZADD", "ZADD"),
                (b"ZREM", "ZREM"),
            ],
        ),
        5 => match_upper(
            bytes,
            &[
                (b"BITOP", "BITOP"),
                (b"BLPOP", "BLPOP"),
                (b"BRPOP", "BRPOP"),
                (b"FCALL", "FCALL"),
                (b"GETEX", "GETEX"),
                (b"HELLO", "HELLO"),
                (b"HKEYS", "HKEYS"),
                (b"HMGET", "HMGET"),
                (b"HMSET", "HMSET"),
                (b"HPTTL", "HPTTL"),
                (b"HSCAN", "HSCAN"),
                (b"HVALS", "HVALS"),
                (b"LMOVE", "LMOVE"),
                (b"LMPOP", "LMPOP"),
                (b"LPUSH", "LPUSH"),
                (b"LTRIM", "LTRIM"),
                (b"MULTI", "MULTI"),
                (b"PFADD", "PFADD"),
                (b"PSYNC", "PSYNC"),
                (b"RESET", "RESET"),
                (b"RPUSH", "RPUSH"),
                (b"SCARD", "SCARD"),
                (b"SDIFF", "SDIFF"),
                (b"SETNX", "SETNX"),
                (b"SMOVE", "SMOVE"),
                (b"SSCAN", "SSCAN"),
                (b"TOUCH", "TOUCH"),
                (b"WATCH", "WATCH"),
                (b"XINFO", "XINFO"),
                (b"XREAD", "XREAD"),
                (b"XTRIM", "XTRIM"),
                (b"ZCARD", "ZCARD"),
                (b"ZDIFF", "ZDIFF"),
                (b"ZMPOP", "ZMPOP"),
                (b"ZRANK", "ZRANK"),
                (b"ZSCAN", "ZSCAN"),
            ],
        ),
        6 => match_upper(
            bytes,
            &[
                (b"APPEND", "APPEND"),
                (b"ASKING", "ASKING"),
                (b"BGSAVE", "BGSAVE"),
                (b"BITPOS", "BITPOS"),
                (b"BLMOVE", "BLMOVE"),
                (b"BLMPOP", "BLMPOP"),
                (b"BZMPOP", "BZMPOP"),
                (b"CLIENT", "CLIENT"),
                (b"CONFIG", "CONFIG"),
                (b"DBSIZE", "DBSIZE"),
                (b"DECRBY", "DECRBY"),
                (b"EXISTS", "EXISTS"),
                (b"EXPIRE", "EXPIRE"),
                (b"GEOADD", "GEOADD"),
                (b"GEOPOS", "GEOPOS"),
                (b"GETBIT", "GETBIT"),
                (b"GETDEL", "GETDEL"),
                (b"GETSET", "GETSET"),
                (b"HGETEX", "HGETEX"),
                (b"HSETEX", "HSETEX"),
                (b"HSETNX", "HSETNX"),
                (b"INCRBY", "INCRBY"),
                (b"LINDEX", "LINDEX"),
                (b"LOLWUT", "LOLWUT"),
                (b"LPUSHX", "LPUSHX"),
                (b"LRANGE", "LRANGE"),
                (b"MEMORY", "MEMORY"),
                (b"MODULE", "MODULE"),
                (b"MSETNX", "MSETNX"),
                (b"OBJECT", "OBJECT"),
                (b"PSETEX", "PSETEX"),
                (b"PUBSUB", "PUBSUB"),
                (b"RENAME", "RENAME"),
                (b"RPUSHX", "RPUSHX"),
                (b"SCRIPT", "SCRIPT"),
                (b"SELECT", "SELECT"),
                (b"SETBIT", "SETBIT"),
                (b"SINTER", "SINTER"),
                (b"STRLEN", "STRLEN"),
                (b"SUBSTR", "SUBSTR"),
                (b"SUNION", "SUNION"),
                (b"SWAPDB", "SWAPDB"),
                (b"UNLINK", "UNLINK"),
                (b"XCLAIM", "XCLAIM"),
                (b"XGROUP", "XGROUP"),
                (b"XRANGE", "XRANGE"),
                (b"ZCOUNT", "ZCOUNT"),
                (b"ZINTER", "ZINTER"),
                (b"ZRANGE", "ZRANGE"),
                (b"ZSCORE", "ZSCORE"),
                (b"ZUNION", "ZUNION"),
            ],
        ),
        7 => match_upper(
            bytes,
            &[
                (b"CLUSTER", "CLUSTER"),
                (b"COMMAND", "COMMAND"),
                (b"DELIFEQ", "DELIFEQ"),
                (b"DISCARD", "DISCARD"),
                (b"EVALSHA", "EVALSHA"),
                (b"FLUSHDB", "FLUSHDB"),
                (b"GEODIST", "GEODIST"),
                (b"GEOHASH", "GEOHASH"),
                (b"HEXISTS", "HEXISTS"),
                (b"HEXPIRE", "HEXPIRE"),
                (b"HGETALL", "HGETALL"),
                (b"HGETDEL", "HGETDEL"),
                (b"HINCRBY", "HINCRBY"),
                (b"HSTRLEN", "HSTRLEN"),
                (b"LATENCY", "LATENCY"),
                (b"LINSERT", "LINSERT"),
                (b"MIGRATE", "MIGRATE"),
                (b"MONITOR", "MONITOR"),
                (b"PERSIST", "PERSIST"),
                (b"PEXPIRE", "PEXPIRE"),
                (b"PFCOUNT", "PFCOUNT"),
                (b"PFMERGE", "PFMERGE"),
                (b"PUBLISH", "PUBLISH"),
                (b"RESTORE", "RESTORE"),
                (b"SLAVEOF", "SLAVEOF"),
                (b"SLOWLOG", "SLOWLOG"),
                (b"UNWATCH", "UNWATCH"),
                (b"WAITAOF", "WAITAOF"),
                (b"ZINCRBY", "ZINCRBY"),
                (b"ZMSCORE", "ZMSCORE"),
                (b"ZPOPMAX", "ZPOPMAX"),
                (b"ZPOPMIN", "ZPOPMIN"),
            ],
        ),
        8 => match_upper(
            bytes,
            &[
                (b"BITCOUNT", "BITCOUNT"),
                (b"BITFIELD", "BITFIELD"),
                (b"BZPOPMAX", "BZPOPMAX"),
                (b"BZPOPMIN", "BZPOPMIN"),
                (b"EXPIREAT", "EXPIREAT"),
                (b"FAILOVER", "FAILOVER"),
                (b"FLUSHALL", "FLUSHALL"),
                (b"FUNCTION", "FUNCTION"),
                (b"GETRANGE", "GETRANGE"),
                (b"HPERSIST", "HPERSIST"),
                (b"HPEXPIRE", "HPEXPIRE"),
                (b"LASTSAVE", "LASTSAVE"),
                (b"READONLY", "READONLY"),
                (b"RENAMENX", "RENAMENX"),
                (b"REPLCONF", "REPLCONF"),
                (b"SETRANGE", "SETRANGE"),
                (b"SHUTDOWN", "SHUTDOWN"),
                (b"SMEMBERS", "SMEMBERS"),
                (b"SPUBLISH", "SPUBLISH"),
                (b"XPENDING", "XPENDING"),
                (b"ZREVRANK", "ZREVRANK"),
            ],
        ),
        9 => match_upper(
            bytes,
            &[
                (b"GEOSEARCH", "GEOSEARCH"),
                (b"HEXPIREAT", "HEXPIREAT"),
                (b"PEXPIREAT", "PEXPIREAT"),
                (b"RANDOMKEY", "RANDOMKEY"),
                (b"READWRITE", "READWRITE"),
                (b"REPLICAOF", "REPLICAOF"),
                (b"SISMEMBER", "SISMEMBER"),
                (b"SUBSCRIBE", "SUBSCRIBE"),
                (b"XREVRANGE", "XREVRANGE"),
                (b"ZLEXCOUNT", "ZLEXCOUNT"),
                (b"ZREVRANGE", "ZREVRANGE"),
            ],
        ),
        10 => match_upper(
            bytes,
            &[
                (b"EXPIRETIME", "EXPIRETIME"),
                (b"HPEXPIREAT", "HPEXPIREAT"),
                (b"HRANDFIELD", "HRANDFIELD"),
                (b"PSUBSCRIBE", "PSUBSCRIBE"),
                (b"SDIFFSTORE", "SDIFFSTORE"),
                (b"SINTERCARD", "SINTERCARD"),
                (b"SMISMEMBER", "SMISMEMBER"),
                (b"SSUBSCRIBE", "SSUBSCRIBE"),
                (b"XAUTOCLAIM", "XAUTOCLAIM"),
                (b"XREADGROUP", "XREADGROUP"),
                (b"ZDIFFSTORE", "ZDIFFSTORE"),
                (b"ZINTERCARD", "ZINTERCARD"),
            ],
        ),
        11 => match_upper(
            bytes,
            &[
                (b"HEXPIRETIME", "HEXPIRETIME"),
                (b"INCRBYFLOAT", "INCRBYFLOAT"),
                (b"PEXPIRETIME", "PEXPIRETIME"),
                (b"SINTERSTORE", "SINTERSTORE"),
                (b"SRANDMEMBER", "SRANDMEMBER"),
                (b"SUNIONSTORE", "SUNIONSTORE"),
                (b"UNSUBSCRIBE", "UNSUBSCRIBE"),
                (b"ZINTERSTORE", "ZINTERSTORE"),
                (b"ZRANDMEMBER", "ZRANDMEMBER"),
                (b"ZRANGEBYLEX", "ZRANGEBYLEX"),
                (b"ZRANGESTORE", "ZRANGESTORE"),
                (b"ZUNIONSTORE", "ZUNIONSTORE"),
            ],
        ),
        12 => match_upper(
            bytes,
            &[
                (b"BGREWRITEAOF", "BGREWRITEAOF"),
                (b"HINCRBYFLOAT", "HINCRBYFLOAT"),
                (b"HPEXPIRETIME", "HPEXPIRETIME"),
                (b"PUNSUBSCRIBE", "PUNSUBSCRIBE"),
                (b"SUNSUBSCRIBE", "SUNSUBSCRIBE"),
            ],
        ),
        13 => match_upper(bytes, &[(b"ZRANGEBYSCORE", "ZRANGEBYSCORE")]),
        14 => match_upper(
            bytes,
            &[
                (b"GEOSEARCHSTORE", "GEOSEARCHSTORE"),
                (b"ZREMRANGEBYLEX", "ZREMRANGEBYLEX"),
                (b"ZREVRANGEBYLEX", "ZREVRANGEBYLEX"),
            ],
        ),
        15 => match_upper(bytes, &[(b"ZREMRANGEBYRANK", "ZREMRANGEBYRANK")]),
        16 => match_upper(
            bytes,
            &[
                (b"ZREMRANGEBYSCORE", "ZREMRANGEBYSCORE"),
                (b"ZREVRANGEBYSCORE", "ZREVRANGEBYSCORE"),
            ],
        ),
        _ => None,
    }
    .unwrap_or("UNKNOWN")
}
fn match_upper(input: &[u8], table: &[(&[u8], &'static str)]) -> Option<&'static str> {
    debug_assert!(
        table.windows(2).all(|w| w[0].0 <= w[1].0),
        "cmd_name_from_bytes table must be sorted alphabetically"
    );
    // Tables are sorted alphabetically (uppercase), so use binary search.
    table
        .binary_search_by(|(pattern, _)| {
            pattern
                .iter()
                .zip(input.iter())
                .map(|(&p, &i)| p.cmp(&i.to_ascii_uppercase()))
                .find(|&ord| ord != std::cmp::Ordering::Equal)
                .unwrap_or(std::cmp::Ordering::Equal)
        })
        .ok()
        .map(|idx| table[idx].1)
}

/// The phase a command was in when the timeout fired.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CommandPhase {
    /// Command was queued but never sent to the server (client-side bottleneck).
    Queued,
    /// Command was sent to the server, awaiting response.
    Sent,
}

/// Classified root cause of the timeout.
#[derive(Debug, Clone, PartialEq)]
pub enum TimeoutCause {
    /// Command was sent but the server didn't respond in time.
    ServerUnresponsive { node: String },
    /// Command never left the client — Tokio or connection pool bottleneck.
    ClientBackpressure {
        queue_depth: usize,
        scheduling_delay: Duration,
    },
    /// Broad timeout storm across multiple nodes — likely local resource exhaustion.
    SystemOverload { pending_total: usize },
}

/// Structured timeout event returned to the caller when a deadline fires.
#[derive(Debug, Clone)]
pub struct TimeoutEvent {
    /// Classified cause of the timeout.
    pub cause: TimeoutCause,
    /// The command that timed out (e.g. "GET", "SET").
    pub command: &'static str,
    /// Target node address.
    pub node: String,
    /// What phase the command was in when the timeout fired.
    pub phase: CommandPhase,
    /// The timeout duration that was configured.
    pub configured_timeout: Duration,
    /// Actual wall-clock time elapsed since the command was submitted.
    pub actual_elapsed: Duration,
    /// Total commands pending across all nodes at fire time.
    pub pending_commands: usize,
    /// Recent p99 latency for the target node (if available).
    pub recent_p99_latency: Option<Duration>,
    /// Process RSS in bytes at fire time (Linux/macOS).
    pub rss_bytes: Option<u64>,
    /// Suggested timeout based on recent latency observations.
    pub suggested_timeout: Option<Duration>,
    /// Number of inflight requests when the command was submitted.
    pub inflight_at_register: Option<usize>,
    /// Number of inflight requests when the timeout fired.
    pub inflight_at_timeout: Option<usize>,
    /// Number of retries attempted before this timeout.
    pub retry_count: u8,
}

/// Returns the process RSS in bytes. Cached for 1 second to avoid redundant
/// syscalls during timeout storms (many timeouts fire within milliseconds).
/// - Linux: reads /proc/self/statm (single read, no alloc beyond stack buffer)
/// - macOS: mach_task_basic_info syscall (~200ns, no fork)
/// - Other: None
pub fn get_rss() -> Option<u64> {
    use std::sync::atomic::{AtomicU64, Ordering as AOrdering};

    static CACHED_RSS: AtomicU64 = AtomicU64::new(0);
    static CACHED_AT: std::sync::OnceLock<std::sync::Mutex<Instant>> = std::sync::OnceLock::new();

    let mutex =
        CACHED_AT.get_or_init(|| std::sync::Mutex::new(Instant::now() - Duration::from_secs(10)));
    // Fast path: check if cache is fresh
    if let Ok(last) = mutex.try_lock()
        && last.elapsed() < Duration::from_secs(1)
    {
        let val = CACHED_RSS.load(AOrdering::Relaxed);
        return if val == 0 { None } else { Some(val) };
    }

    let rss = get_rss_inner();
    CACHED_RSS.store(rss.unwrap_or(0), AOrdering::Relaxed);
    if let Ok(mut last) = mutex.try_lock() {
        *last = Instant::now();
    }
    rss
}

fn get_rss_inner() -> Option<u64> {
    #[cfg(target_os = "linux")]
    {
        let statm = std::fs::read_to_string("/proc/self/statm").ok()?;
        let resident_pages: u64 = statm.split_whitespace().nth(1)?.parse().ok()?;
        let page_size = unsafe { libc::sysconf(libc::_SC_PAGESIZE) } as u64;
        Some(resident_pages * page_size)
    }
    #[cfg(target_os = "macos")]
    {
        use std::mem::MaybeUninit;
        #[allow(deprecated)]
        let task = unsafe { libc::mach_task_self_ };
        let mut info = MaybeUninit::<libc::mach_task_basic_info_data_t>::uninit();
        let mut count = (std::mem::size_of::<libc::mach_task_basic_info_data_t>()
            / std::mem::size_of::<libc::natural_t>())
            as libc::mach_msg_type_number_t;
        #[allow(deprecated)]
        let kr = unsafe {
            libc::task_info(
                task,
                libc::MACH_TASK_BASIC_INFO,
                info.as_mut_ptr() as libc::task_info_t,
                &mut count,
            )
        };
        if kr == libc::KERN_SUCCESS {
            Some(unsafe { info.assume_init() }.resident_size)
        } else {
            None
        }
    }
    #[cfg(not(any(target_os = "linux", target_os = "macos")))]
    {
        None
    }
}

impl std::fmt::Display for TimeoutEvent {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "Timeout: cmd={} node={} cause={:?} phase={:?} \
             elapsed={:?} configured={:?}",
            self.command,
            self.node,
            self.cause,
            self.phase,
            self.actual_elapsed,
            self.configured_timeout,
        )?;
        write!(f, " pending={}", self.pending_commands)?;
        if let (Some(at_reg), Some(at_fire)) = (self.inflight_at_register, self.inflight_at_timeout)
        {
            let trend = if at_fire > at_reg + 10 {
                "BUILDING (backpressure increasing during timeout window)"
            } else if at_reg > at_fire + 10 {
                "DRAINING (backpressure decreasing, system recovering)"
            } else {
                "STABLE (system was already saturated at submission)"
            };
            write!(f, " inflight={}→{} {}", at_reg, at_fire, trend)?;
        }
        if let Some(p99) = self.recent_p99_latency {
            write!(f, " p99={:?}", p99)?;
        }
        if let Some(suggested) = self.suggested_timeout {
            write!(f, " suggested_timeout={:?}", suggested)?;
        }
        if self.retry_count > 0 {
            write!(f, " retries={}", self.retry_count)?;
        }
        if let Some(rss) = self.rss_bytes {
            write!(f, " rss={}MB", rss / (1024 * 1024))?;
        }
        Ok(())
    }
}

// ─── Latency Tracker ─────────────────────────────────────────────────────────

/// Per-node latency ring buffer. Lock-free writes via atomic index.
/// Shared between the command completion path and the watchdog fire path.
#[derive(Debug)]
pub struct LatencyTracker {
    /// Ring buffer of latency samples in microseconds.
    samples: Box<[AtomicU64]>,
    /// Write index (wraps around).
    write_idx: AtomicUsize,
    /// Number of samples written (saturates at capacity).
    count: AtomicUsize,
    /// Bitmask for wrapping (capacity - 1). Capacity must be a power of 2.
    mask: usize,
    capacity: usize,
}

/// Sentinel value indicating an unwritten slot.
const LATENCY_UNWRITTEN: u64 = u64::MAX;

impl LatencyTracker {
    pub fn new(capacity: usize) -> Self {
        assert!(
            capacity.is_power_of_two(),
            "LatencyTracker capacity must be a power of 2, got {capacity}"
        );
        let samples: Vec<AtomicU64> = (0..capacity)
            .map(|_| AtomicU64::new(LATENCY_UNWRITTEN))
            .collect();
        Self {
            samples: samples.into_boxed_slice(),
            write_idx: AtomicUsize::new(0),
            count: AtomicUsize::new(0),
            mask: capacity - 1,
            capacity,
        }
    }

    /// Record a completed command latency. Called on the success path.
    pub fn record(&self, latency: Duration) {
        let micros = latency.as_micros() as u64;
        let idx = self.write_idx.fetch_add(1, Ordering::Relaxed) & self.mask;
        self.samples[idx].store(micros, Ordering::Release);
        // Only update count while the ring is filling. Once full, count == capacity
        // and never changes again — skip the atomic RMW entirely.
        if self.count.load(Ordering::Relaxed) < self.capacity {
            //TODO: (#7175) `fetch_update` is deprecated for `try_update` since Rust 1.99, but `try_update`
            // needs 1.95 and the MSRV is 1.94.1.
            #[allow(deprecated)]
            let _ = self
                .count
                .fetch_update(Ordering::Release, Ordering::Relaxed, |c| {
                    if c < self.capacity { Some(c + 1) } else { None }
                });
        }
    }

    /// Compute p99 from the ring buffer. Called only at fire time (rare).
    pub fn p99(&self) -> Option<Duration> {
        let n = self.count.load(Ordering::Acquire).min(self.capacity);
        if n < 10 {
            return None; // Not enough data
        }
        let mut buf: Vec<u64> = (0..n)
            .map(|i| self.samples[i].load(Ordering::Acquire))
            .filter(|&v| v != LATENCY_UNWRITTEN)
            .collect();
        if buf.len() < 10 {
            return None;
        }
        buf.sort_unstable();
        let idx = (buf.len() as f64 * 0.99) as usize;
        Some(Duration::from_micros(buf[idx.min(buf.len() - 1)]))
    }
}

// ─── Deadline Entry ──────────────────────────────────────────────────────────

/// Coarse tick of the watchdog's deadline buckets. A timeout fires at the first tick boundary
/// at or after its deadline — never early, and at most one tick (plus OS wake-up jitter) late.
/// Request timeouts are in the hundreds of milliseconds and up, so 1 ms is well inside them.
pub const WATCHDOG_TICK: Duration = Duration::from_millis(1);

/// Inbox shards. Registrations from different worker threads land in different shards, so
/// the per-registration lock is almost always uncontended.
const INBOX_SHARDS: usize = 16;

const SLOT_PENDING: u8 = 0;
const SLOT_FIRED: u8 = 1;
const SLOT_CANCELLED: u8 = 2;
/// The watchdog thread is gone (handle dropped or thread died): the receiver resolves to
/// `Err(RecvError)`, exactly like the old `oneshot` whose sender was dropped.
const SLOT_DEAD: u8 = 3;

/// Shared state of one registration: written by the watchdog (fire/dead) and by the receiver
/// (cancel on drop). Replaces the per-command `tokio::sync::oneshot` channel.
struct TimerSlot {
    state: AtomicU8,
    waker: AtomicWaker,
}

impl TimerSlot {
    /// Watchdog side: PENDING -> `to` (FIRED or DEAD), then wake the receiver. A slot the
    /// receiver already cancelled is left alone.
    fn resolve(&self, to: u8) {
        if self
            .state
            .compare_exchange(SLOT_PENDING, to, Ordering::AcqRel, Ordering::Acquire)
            .is_ok()
        {
            self.waker.wake();
        }
    }

    fn is_cancelled(&self) -> bool {
        self.state.load(Ordering::Relaxed) == SLOT_CANCELLED
    }
}

/// Internal entry handed from callers to the watchdog thread.
/// Minimal struct: the watchdog is a pure timer.
struct DeadlineEntry {
    deadline: Instant,
    slot: Arc<TimerSlot>,
}

/// Error returned by a [`TimeoutReceiver`] when the watchdog thread is gone before the deadline
/// fired (same meaning as `tokio::sync::oneshot::error::RecvError` in the previous design).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct RecvError;

impl std::fmt::Display for RecvError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("timeout watchdog is gone")
    }
}
impl std::error::Error for RecvError {}

/// Error returned by [`TimeoutReceiver::try_recv`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TryRecvError {
    /// The deadline has not fired yet.
    Empty,
    /// The watchdog thread is gone.
    Closed,
}

/// Resolves to `Ok(())` when the deadline fires, or `Err(RecvError)` if the watchdog thread is
/// gone. Dropping it cancels the registration (the watchdog then discards the entry without
/// firing it), exactly like dropping the old `oneshot::Receiver`.
pub struct TimeoutReceiver {
    slot: Arc<TimerSlot>,
}

impl TimeoutReceiver {
    /// Non-blocking check, mirroring `oneshot::Receiver::try_recv`.
    pub fn try_recv(&mut self) -> Result<(), TryRecvError> {
        match self.slot.state.load(Ordering::Acquire) {
            SLOT_FIRED => Ok(()),
            SLOT_DEAD => Err(TryRecvError::Closed),
            _ => Err(TryRecvError::Empty),
        }
    }
}

impl std::future::Future for TimeoutReceiver {
    type Output = Result<(), RecvError>;

    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output> {
        let check = |s: u8| match s {
            SLOT_FIRED => Some(Ok(())),
            SLOT_DEAD => Some(Err(RecvError)),
            _ => None,
        };
        if let Some(r) = check(self.slot.state.load(Ordering::Acquire)) {
            return Poll::Ready(r);
        }
        self.slot.waker.register(cx.waker());
        // Re-check after registering, so a fire racing the registration is not lost.
        match check(self.slot.state.load(Ordering::Acquire)) {
            Some(r) => Poll::Ready(r),
            None => Poll::Pending,
        }
    }
}

impl Drop for TimeoutReceiver {
    fn drop(&mut self) {
        // PENDING -> CANCELLED; a fired/dead slot stays as it is.
        let _ = self.slot.state.compare_exchange(
            SLOT_PENDING,
            SLOT_CANCELLED,
            Ordering::AcqRel,
            Ordering::Relaxed,
        );
        // Drop the registered task waker now rather than when the watchdog drops its ref.
        drop(self.slot.waker.take());
    }
}

// ─── Timeout Watchdog ────────────────────────────────────────────────────────

/// Cache-line padded inbox shard.
#[repr(align(128))]
struct InboxShard(Mutex<Vec<DeadlineEntry>>);

struct WatchdogShared {
    inbox: [InboxShard; INBOX_SHARDS],
    /// Set by the watchdog thread when it is about to park with no deadlines outstanding;
    /// a registration that sees it set unparks the thread. While deadlines are outstanding the
    /// thread wakes on its own every tick, so registrations never wake it.
    sleeping: AtomicBool,
    /// Set when the last handle is dropped, or by the thread itself on exit.
    shutdown: AtomicBool,
    thread: OnceLock<std::thread::Thread>,
    publish_count: bool,
}

impl WatchdogShared {
    fn unpark(&self) {
        if let Some(t) = self.thread.get() {
            t.unpark();
        }
    }
}

/// Dropped when the last [`TimeoutWatchdog`] clone goes: tells the thread to resolve every
/// outstanding receiver to `Err(RecvError)` and exit (the old design's mpsc-disconnect path).
struct HandleToken(Arc<WatchdogShared>);

impl Drop for HandleToken {
    fn drop(&mut self) {
        self.0.shutdown.store(true, Ordering::SeqCst);
        self.0.unpark();
    }
}

/// Handle to the watchdog thread. Register deadlines and receive a signal
/// when the timeout fires.
///
/// Design (perf/proto-hotpath): `register()` allocates one small shared slot, pushes it into a
/// per-thread-sharded inbox under an uncontended mutex, and returns. It does not signal the
/// watchdog thread except when that thread is parked idle. The thread drains the inboxes once
/// per [`WATCHDOG_TICK`], drops entries whose receiver already went away without ever indexing
/// them, and keeps the rest in coarse per-tick buckets — so a command that completes within a
/// tick costs the thread nothing beyond the drain.
#[derive(Clone)]
pub struct TimeoutWatchdog {
    shared: Arc<WatchdogShared>,
    _token: Arc<HandleToken>,
    /// [`fork_generation`] when this instance (and its thread) was started.
    fork_gen: u64,
}

/// Global singleton watchdog instance. Uses an atomic pointer so the read path
/// is lock-free and the state can be replaced after fork().
static GLOBAL_WATCHDOG: std::sync::atomic::AtomicPtr<TimeoutWatchdog> =
    std::sync::atomic::AtomicPtr::new(std::ptr::null_mut());

/// Bumped in every forked child by a `pthread_atfork` handler (installed when the
/// first global watchdog starts). A global instance started under an older
/// generation was inherited across fork(): its thread did not survive, so
/// [`TimeoutWatchdog::global`] replaces it. This covers children whose binding
/// never calls [`TimeoutWatchdog::reinit_global`] (e.g. the sync Python client).
#[cfg(unix)]
static FORK_GENERATION: AtomicU64 = AtomicU64::new(0);

#[cfg(unix)]
extern "C" fn bump_fork_generation() {
    // Runs in the child right after fork(): only an atomic store.
    FORK_GENERATION.fetch_add(1, Ordering::Relaxed);
}

fn fork_generation() -> u64 {
    #[cfg(unix)]
    {
        static ATFORK: std::sync::Once = std::sync::Once::new();
        ATFORK.call_once(|| {
            // SAFETY: registers a child-side handler that only does an atomic store.
            // If registration fails, fork detection falls back to explicit
            // `reinit_global()` calls, as before.
            unsafe {
                libc::pthread_atfork(None, None, Some(bump_fork_generation));
            }
        });
        FORK_GENERATION.load(Ordering::Relaxed)
    }
    #[cfg(not(unix))]
    {
        0
    }
}

thread_local! {
    /// Inbox shard of the calling thread, assigned round-robin on first use.
    static SHARD: usize = {
        static NEXT: AtomicUsize = AtomicUsize::new(0);
        NEXT.fetch_add(1, Ordering::Relaxed) % INBOX_SHARDS
    };
}

impl TimeoutWatchdog {
    /// Get or initialize the global shared watchdog instance.
    pub fn global() -> &'static Self {
        loop {
            let ptr = GLOBAL_WATCHDOG.load(std::sync::atomic::Ordering::Acquire);
            if ptr.is_null() {
                return Self::init_global();
            }
            // Safety: once installed as non-null, the pointer is never freed
            // until process exit (reinit_global and the fork replacement below
            // intentionally leak the prior instance rather than dropping it).
            let current = unsafe { &*ptr };
            if current.fork_gen == fork_generation() {
                return current;
            }
            // Inherited across fork(): replace it, so registrations in this child
            // are served by a live thread. The CAS makes concurrent first callers
            // agree on one replacement.
            let fresh = Box::into_raw(Box::new(Self::start_global()));
            match GLOBAL_WATCHDOG.compare_exchange(
                ptr,
                fresh,
                std::sync::atomic::Ordering::AcqRel,
                std::sync::atomic::Ordering::Acquire,
            ) {
                Ok(_) => {
                    // The stale instance is leaked; its entries are gone.
                    PUBLISHED_PENDING.store(0, Ordering::Relaxed);
                    return unsafe { &*fresh };
                }
                // Lost the race: drop ours and re-check the winner.
                Err(_) => unsafe { drop(Box::from_raw(fresh)) },
            }
        }
    }

    /// Initialize the global watchdog (first call or after reinit).
    /// Uses compare_exchange so only one caller wins the race.
    fn init_global() -> &'static Self {
        let ptr = Box::into_raw(Box::new(Self::start_global()));
        match GLOBAL_WATCHDOG.compare_exchange(
            std::ptr::null_mut(),
            ptr,
            std::sync::atomic::Ordering::AcqRel,
            std::sync::atomic::Ordering::Acquire,
        ) {
            Ok(_) => unsafe { &*ptr },
            Err(existing) => {
                // Another thread won the race — drop ours (dropping its last
                // handle sets `shutdown` and unparks the spawned thread, which exits).
                unsafe {
                    drop(Box::from_raw(ptr));
                    &*existing
                }
            }
        }
    }

    /// Reinitialize the global watchdog after fork().
    ///
    /// After fork(), the watchdog thread is dead but the AtomicPtr still points
    /// to the old instance. This replaces it with a fresh one (new inbox shards,
    /// new thread). The old instance is intentionally leaked, never dropped or
    /// touched: its inbox mutexes may have been held by a parent thread at fork
    /// time, so they are in undefined state in the child.
    ///
    /// Must only be called in a forked child process where the previous
    /// watchdog thread is no longer running.
    pub fn reinit_global() {
        let w = Box::new(Self::start_global());
        let w_ref: &'static Self = Box::leak(w);
        let ptr = w_ref as *const Self as *mut Self;
        GLOBAL_WATCHDOG.store(ptr, std::sync::atomic::Ordering::Release);
        // Reset the published pending count — the old watchdog's entries are gone.
        PUBLISHED_PENDING.store(0, Ordering::Relaxed);
    }

    /// Start the global watchdog instance (publishes pending count).
    fn start_global() -> Self {
        Self::spawn(true)
    }

    /// Start a watchdog instance. Spawns a dedicated OS thread.
    /// Does NOT publish to the global pending count (only the global instance does).
    pub fn start() -> Self {
        Self::spawn(false)
    }

    fn spawn(publish_count: bool) -> Self {
        let shared = Arc::new(WatchdogShared {
            inbox: std::array::from_fn(|_| InboxShard(Mutex::new(Vec::new()))),
            sleeping: AtomicBool::new(false),
            shutdown: AtomicBool::new(false),
            thread: OnceLock::new(),
            publish_count,
        });
        let thread_shared = shared.clone();
        let handle = std::thread::Builder::new()
            .name("glide-timeout-watchdog".into())
            .spawn(move || {
                let shared = thread_shared;
                // Whatever ends the loop (shutdown or a panic), every outstanding receiver must
                // resolve to Err(RecvError) so callers fall back instead of hanging.
                let mut wheel = Wheel::default();
                let res = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    Self::run(&shared, &mut wheel)
                }));
                shared.shutdown.store(true, Ordering::SeqCst);
                std::sync::atomic::fence(Ordering::SeqCst);
                wheel.kill_all();
                for shard in shared.inbox.iter() {
                    let drained = std::mem::take(&mut *lock(&shard.0));
                    for e in drained {
                        e.slot.resolve(SLOT_DEAD);
                    }
                }
                if let Err(p) = res {
                    std::panic::resume_unwind(p);
                }
            })
            .expect("Failed to spawn timeout watchdog thread");
        let _ = shared.thread.set(handle.thread().clone());
        Self {
            _token: Arc::new(HandleToken(shared.clone())),
            shared,
            fork_gen: fork_generation(),
        }
    }

    /// Register a timeout. Returns a [`TimeoutReceiver`] that resolves
    /// when the deadline fires.
    ///
    /// `submitted_at` should be the caller's `Instant::now()` — shared with
    /// the latency tracking timer to avoid a redundant clock read.
    #[inline]
    pub fn register(&self, timeout: Duration, submitted_at: Instant) -> TimeoutReceiver {
        let slot = Arc::new(TimerSlot {
            state: AtomicU8::new(SLOT_PENDING),
            waker: AtomicWaker::new(),
        });
        let rx = TimeoutReceiver { slot: slot.clone() };
        let deadline = submitted_at + timeout;
        let shard = SHARD.with(|s| *s);
        {
            let mut inbox = lock(&self.shared.inbox[shard].0);
            // Checked under the shard lock: the exiting thread sets `shutdown` before it takes
            // each shard lock for its final drain, so an entry pushed here (shutdown unset) is
            // always drained and resolved, and none is pushed once that drain may have passed.
            if self.shared.shutdown.load(Ordering::Acquire) {
                drop(inbox);
                // The watchdog is gone: resolve now so the caller takes its fallback.
                rx.slot.resolve(SLOT_DEAD);
                return rx;
            }
            inbox.push(DeadlineEntry { deadline, slot });
        }
        // Pairs with the thread's fence between `sleeping = true` and its final inbox scan:
        // either the thread sees this entry, or we see `sleeping` and unpark it.
        std::sync::atomic::fence(Ordering::SeqCst);
        if self.shared.sleeping.load(Ordering::Relaxed)
            && self.shared.sleeping.swap(false, Ordering::AcqRel)
        {
            self.shared.unpark();
        }
        rx
    }

    /// Watchdog thread main loop. Returns on shutdown.
    fn run(shared: &WatchdogShared, wheel: &mut Wheel) {
        let mut last_cleanup = Instant::now();
        let mut spare: Vec<DeadlineEntry> = Vec::new();

        loop {
            if shared.shutdown.load(Ordering::SeqCst) {
                return;
            }

            // Fire what is already due before draining, so a large registration burst does not
            // delay due timeouts by the time the drain takes.
            wheel.fire_due(Instant::now());

            // Drain new registrations; entries whose command already completed are dropped
            // here without being indexed or counted.
            for shard in shared.inbox.iter() {
                {
                    let mut guard = lock(&shard.0);
                    if guard.is_empty() {
                        continue;
                    }
                    std::mem::swap(&mut *guard, &mut spare);
                }
                for e in spare.drain(..) {
                    if !e.slot.is_cancelled() {
                        wheel.insert(e);
                    }
                }
            }

            // Fire all expired deadlines (including newly drained, already-due ones).
            let now = Instant::now();
            wheel.fire_due(now);

            // Periodic cleanup of entries whose receivers were dropped (command completed).
            // Run at most once per second to amortize cost.
            if now.duration_since(last_cleanup) > Duration::from_secs(1) {
                wheel.sweep_cancelled();
                last_cleanup = now;
            }

            // Publish the local count once per iteration (no contention on write side)
            if shared.publish_count {
                PUBLISHED_PENDING.store(wheel.count, Ordering::Relaxed);
            }

            match wheel.time_to_next(Instant::now()) {
                // Deadlines outstanding: sleep to the next bucket, capped at one tick so new
                // registrations (which do not wake us) are drained within a tick.
                Some(until_next) => {
                    let sleep = until_next.min(WATCHDOG_TICK);
                    if !sleep.is_zero() {
                        std::thread::park_timeout(sleep);
                    }
                }
                // Nothing outstanding: park until a registration (or shutdown) unparks us.
                None => {
                    shared.sleeping.store(true, Ordering::SeqCst);
                    std::sync::atomic::fence(Ordering::SeqCst);
                    let inbox_empty = shared.inbox.iter().all(|s| lock(&s.0).is_empty());
                    if inbox_empty && !shared.shutdown.load(Ordering::SeqCst) {
                        std::thread::park();
                    }
                    shared.sleeping.store(false, Ordering::SeqCst);
                }
            }
        }
    }
}

fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|p| p.into_inner())
}

/// Coarse deadline buckets: one `Vec` per tick that has deadlines, keyed by tick number since
/// `epoch`. A deadline goes into the bucket of the first tick boundary at or after it, so firing a
/// bucket at its boundary never fires a deadline early.
struct Wheel {
    epoch: Instant,
    buckets: BTreeMap<u64, Vec<DeadlineEntry>>,
    /// Entries resident in `buckets` (same semantics as the old `local_count`: registered and
    /// not yet fired or swept).
    count: usize,
}

impl Default for Wheel {
    fn default() -> Self {
        Self {
            epoch: Instant::now(),
            buckets: BTreeMap::new(),
            count: 0,
        }
    }
}

impl Wheel {
    /// Bucket of `deadline`: the first tick boundary at or after it (rounds up, so never early).
    /// A deadline at or before `epoch` has already passed and goes into tick 0.
    fn tick_of(&self, deadline: Instant) -> u64 {
        let nanos = deadline.saturating_duration_since(self.epoch).as_nanos();
        nanos
            .div_ceil(WATCHDOG_TICK.as_nanos())
            .min(u64::MAX as u128) as u64
    }

    /// Offset of `tick`'s boundary from `epoch`. Kept as a `Duration` (not an `Instant`) so even
    /// the longest blocking-command timeouts (up to `u32::MAX` seconds) neither overflow nor get
    /// clamped to an earlier boundary.
    fn tick_offset(tick: u64) -> Duration {
        let tick_nanos = WATCHDOG_TICK.as_nanos() as u64;
        let nanos = u128::from(tick) * u128::from(tick_nanos);
        Duration::new(
            (nanos / 1_000_000_000).min(u128::from(u64::MAX)) as u64,
            (nanos % 1_000_000_000) as u32,
        )
    }

    fn insert(&mut self, e: DeadlineEntry) {
        let tick = self.tick_of(e.deadline);
        self.buckets.entry(tick).or_default().push(e);
        self.count += 1;
    }

    fn fire_due(&mut self, now: Instant) {
        let elapsed = now.saturating_duration_since(self.epoch);
        while let Some(first) = self.buckets.keys().next().copied() {
            if Self::tick_offset(first) > elapsed {
                break;
            }
            let entries = self.buckets.remove(&first).unwrap_or_default();
            self.count -= entries.len();
            for e in entries {
                // Cancelled slots are skipped inside `resolve`.
                e.slot.resolve(SLOT_FIRED);
            }
        }
    }

    fn sweep_cancelled(&mut self) {
        let count = &mut self.count;
        self.buckets.retain(|_, entries| {
            let before = entries.len();
            entries.retain(|e| !e.slot.is_cancelled());
            *count -= before - entries.len();
            !entries.is_empty()
        });
    }

    /// Time from `now` until the earliest non-empty bucket is due (zero if already due), or
    /// `None` when no deadline is outstanding.
    fn time_to_next(&self, now: Instant) -> Option<Duration> {
        let elapsed = now.saturating_duration_since(self.epoch);
        self.buckets
            .keys()
            .next()
            .map(|t| Self::tick_offset(*t).saturating_sub(elapsed))
    }

    fn kill_all(&mut self) {
        for (_, entries) in std::mem::take(&mut self.buckets) {
            for e in entries {
                e.slot.resolve(SLOT_DEAD);
            }
        }
        self.count = 0;
    }
}

// ─── Unit Tests ──────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use serial_test::serial;
    use std::sync::Arc;

    // ── Basic Firing Behavior ────────────────────────────────────────────

    #[tokio::test]
    async fn fires_on_deadline() {
        let watchdog = TimeoutWatchdog::start();
        let rx = watchdog.register(Duration::from_millis(50), Instant::now());
        let result = rx.await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn does_not_fire_before_deadline() {
        let watchdog = TimeoutWatchdog::start();
        let mut rx = watchdog.register(Duration::from_millis(200), Instant::now());
        tokio::time::sleep(Duration::from_millis(50)).await;
        assert!(rx.try_recv().is_err());
    }

    #[tokio::test]
    async fn multiple_deadlines_fire_in_order() {
        let watchdog = TimeoutWatchdog::start();
        let rx1 = watchdog.register(Duration::from_millis(30), Instant::now());
        let rx2 = watchdog.register(Duration::from_millis(60), Instant::now());

        rx1.await.unwrap();
        let mid = Instant::now();
        rx2.await.unwrap();
        let end = Instant::now();

        assert!(end.duration_since(mid) >= Duration::from_millis(10));
    }

    #[tokio::test]
    async fn cancelled_before_deadline_does_not_fire() {
        let watchdog = TimeoutWatchdog::start();
        let rx = watchdog.register(Duration::from_millis(200), Instant::now());
        drop(rx);
        tokio::time::sleep(Duration::from_millis(250)).await;
    }

    // ── perf/proto-hotpath: sharded-inbox + tick-bucket design ──────────

    #[tokio::test]
    async fn never_fires_early_and_within_tick_bound() {
        let watchdog = TimeoutWatchdog::start();
        for ms in [1u64, 3, 7, 20] {
            let start = Instant::now();
            let rx = watchdog.register(Duration::from_millis(ms), start);
            rx.await.unwrap();
            let el = start.elapsed();
            assert!(
                el >= Duration::from_millis(ms),
                "fired early: {el:?} < {ms}ms"
            );
            // One tick of rounding plus generous scheduling slack for CI.
            assert!(
                el < Duration::from_millis(ms) + WATCHDOG_TICK + Duration::from_millis(50),
                "fired too late: {el:?} for {ms}ms"
            );
        }
    }

    #[tokio::test]
    async fn idle_thread_is_woken_by_registration() {
        let watchdog = TimeoutWatchdog::start();
        // Let the thread go fully idle (no deadlines -> parked without timeout).
        tokio::time::sleep(Duration::from_millis(50)).await;
        let start = Instant::now();
        let rx = watchdog.register(Duration::from_millis(10), start);
        let r = tokio::time::timeout(Duration::from_millis(500), rx).await;
        assert!(r.is_ok(), "parked watchdog must be unparked by register()");
        assert!(start.elapsed() >= Duration::from_millis(10));
    }

    #[tokio::test]
    async fn register_after_thread_exit_resolves_err_without_queueing() {
        // Panic/exit path: once the thread has done its final drain, a registration must resolve
        // to Err immediately and must not leave an entry nobody will ever drain.
        let watchdog = TimeoutWatchdog::start();
        watchdog.shared.shutdown.store(true, Ordering::SeqCst);
        watchdog.shared.unpark();
        tokio::time::sleep(Duration::from_millis(50)).await;
        for _ in 0..100 {
            let rx = watchdog.register(Duration::from_secs(60), Instant::now());
            assert_eq!(rx.await, Err(RecvError));
        }
        assert!(
            watchdog.shared.inbox.iter().all(|s| lock(&s.0).is_empty()),
            "registrations after exit must not accumulate"
        );
    }

    #[tokio::test]
    async fn dropping_last_handle_resolves_pending_to_err() {
        // Old design: dropping the last mpsc sender disconnected the thread, which dropped
        // every oneshot sender -> receivers got Err(RecvError). Keep that contract.
        let watchdog = TimeoutWatchdog::start();
        let rx = watchdog.register(Duration::from_secs(60), Instant::now());
        drop(watchdog);
        let r = tokio::time::timeout(Duration::from_millis(500), rx).await;
        assert_eq!(r.expect("must resolve promptly"), Err(RecvError));
    }

    #[tokio::test]
    async fn try_recv_reports_fired() {
        let watchdog = TimeoutWatchdog::start();
        let mut rx = watchdog.register(Duration::from_millis(5), Instant::now());
        assert_eq!(rx.try_recv(), Err(TryRecvError::Empty));
        tokio::time::sleep(Duration::from_millis(60)).await;
        assert_eq!(rx.try_recv(), Ok(()));
    }

    #[test]
    fn bucket_boundary_is_never_early_and_less_than_one_tick_late() {
        // Deterministic check of the rounding the timing tests above can only sample:
        // boundary(tick_of(d)) is in [d, d + WATCHDOG_TICK).
        let wheel = Wheel::default();
        let tick = WATCHDOG_TICK.as_nanos() as u64;
        for offset_ns in [
            0,
            1,
            tick - 1,
            tick,
            tick + 1,
            7 * tick + tick / 2,
            250_000_000,
            250_000_001,
        ] {
            let offset = Duration::from_nanos(offset_ns);
            let boundary = Wheel::tick_offset(wheel.tick_of(wheel.epoch + offset));
            assert!(boundary >= offset, "early: {boundary:?} < {offset:?}");
            assert!(
                boundary < offset + WATCHDOG_TICK,
                "a full tick late or more: {boundary:?} for {offset:?}"
            );
        }
    }

    #[test]
    fn long_blocking_timeouts_are_not_clamped_early() {
        // Blocking commands may carry timeouts up to u32::MAX seconds (#6978). Their bucket
        // boundary must not be clamped below the deadline, which would fire them early.
        let mut wheel = Wheel::default();
        let far = Duration::from_secs(u32::MAX as u64);
        let boundary = Wheel::tick_offset(wheel.tick_of(wheel.epoch + far));
        assert!(boundary >= far, "clamped early: {boundary:?} < {far:?}");
        assert!(boundary < far + WATCHDOG_TICK);

        let slot = Arc::new(TimerSlot {
            state: AtomicU8::new(SLOT_PENDING),
            waker: AtomicWaker::new(),
        });
        wheel.insert(DeadlineEntry {
            deadline: wheel.epoch + far,
            slot: slot.clone(),
        });
        // Days later (well past any u32-millisecond clamp of ~49.7 days) it is still pending.
        wheel.fire_due(wheel.epoch + Duration::from_secs(60 * 24 * 3600));
        assert_eq!(slot.state.load(Ordering::Acquire), SLOT_PENDING);
        assert!(wheel.time_to_next(wheel.epoch).is_some_and(|d| d >= far));
        wheel.fire_due(wheel.epoch + far + WATCHDOG_TICK);
        assert_eq!(slot.state.load(Ordering::Acquire), SLOT_FIRED);
    }

    /// Real fork(): the child inherits the parent's global watchdog with its thread gone and its
    /// inbox mutexes possibly held. After `reinit_global()` (what the FFI layer's post-fork hook
    /// calls), registrations in the child must fire, without touching the stale instance.
    #[cfg(unix)]
    #[test]
    #[serial]
    fn fork_child_reinit_global_fires_timeouts() {
        let old = TimeoutWatchdog::global();
        // Held across fork(): in the child these locks are never released, exactly like a
        // mutex some other parent thread held at fork time.
        let guards: Vec<_> = old.shared.inbox.iter().map(|s| lock(&s.0)).collect();
        // SAFETY: the child only spawns a thread, allocates and calls `_exit`, matching what the
        // production post-fork path (`reinit_async_pipe`) does.
        let pid = unsafe { libc::fork() };
        assert!(pid >= 0, "fork failed");
        if pid == 0 {
            // A child stuck on a stale mutex is killed by SIGALRM, failing the test instead of
            // hanging it.
            // SAFETY: async-signal-safe libc call.
            unsafe { libc::alarm(10) };
            TimeoutWatchdog::reinit_global();
            let start = Instant::now();
            let mut rx = TimeoutWatchdog::global().register(Duration::from_millis(20), start);
            let code = loop {
                match rx.try_recv() {
                    Ok(()) if start.elapsed() >= Duration::from_millis(20) => break 0,
                    Ok(()) => break 2, // fired early
                    Err(TryRecvError::Closed) => break 3,
                    Err(TryRecvError::Empty) if start.elapsed() > Duration::from_secs(5) => {
                        break 4;
                    }
                    Err(TryRecvError::Empty) => std::thread::sleep(Duration::from_millis(1)),
                }
            };
            // SAFETY: terminate the forked child without running the parent's test harness.
            unsafe { libc::_exit(code) };
        }
        drop(guards);
        let mut status = 0;
        // SAFETY: `pid` is our child.
        let waited = unsafe { libc::waitpid(pid, &mut status, 0) };
        assert_eq!(waited, pid);
        assert!(
            libc::WIFEXITED(status),
            "child did not exit cleanly: {status}"
        );
        assert_eq!(
            libc::WEXITSTATUS(status),
            0,
            "child watchdog failed (2=early, 3=closed, 4=never fired)"
        );
    }

    // ── Pending Count ────────────────────────────────────────────────────

    #[tokio::test]
    #[serial]
    async fn pending_count_increments_and_decrements() {
        // Use the global watchdog since PUBLISHED_PENDING is a single global.
        // #[serial] prevents concurrent tests from perturbing the global counter
        // while we poll for our 2 registered entries.
        let watchdog = TimeoutWatchdog::global();

        let rx1 = watchdog.register(Duration::from_millis(500), Instant::now());
        let rx2 = watchdog.register(Duration::from_millis(500), Instant::now());

        // Poll until the watchdog thread drains the channel and publishes our
        // two entries, or until a deadline expires. A fixed sleep is not
        // reliable under concurrent test load.
        let deadline = tokio::time::Instant::now() + Duration::from_secs(2);
        loop {
            let count = pending_count();
            if count >= 2 {
                break;
            }
            if tokio::time::Instant::now() >= deadline {
                panic!("pending count must reflect registered entries within 2s: got {count}");
            }
            tokio::time::sleep(Duration::from_millis(10)).await;
        }

        // Wait for both to fire
        let _ = rx1.await;
        let _ = rx2.await;
    }

    #[tokio::test]
    async fn pending_count_decrements_on_cancel() {
        // Verify that cancelled entries (dropped receivers) are cleaned up and
        // don't prevent subsequent timeouts from firing.
        let watchdog = TimeoutWatchdog::start();

        // Register 1000 entries and immediately cancel them
        for _ in 0..1000 {
            let rx = watchdog.register(Duration::from_secs(60), Instant::now());
            drop(rx);
        }

        // A subsequent entry must still fire promptly — proves the watchdog
        // thread isn't stuck processing cancelled entries indefinitely.
        let rx = watchdog.register(Duration::from_millis(50), Instant::now());
        let result = tokio::time::timeout(Duration::from_millis(500), rx).await;
        assert!(
            result.is_ok(),
            "watchdog must still fire after mass cancellation"
        );
    }

    // ── Latency Tracker ──────────────────────────────────────────────────

    #[tokio::test]
    async fn latency_tracker_reports_p99() {
        let tracker = Arc::new(LatencyTracker::new(128));
        for i in 1..=100 {
            tracker.record(Duration::from_millis(i));
        }
        let p99 = tracker.p99().unwrap();
        assert!(p99 >= Duration::from_millis(95));
        assert!(p99 <= Duration::from_millis(100));
    }

    #[tokio::test]
    async fn latency_tracker_returns_none_with_few_samples() {
        let tracker = Arc::new(LatencyTracker::new(128));
        for i in 1..=5 {
            tracker.record(Duration::from_millis(i));
        }
        assert!(tracker.p99().is_none());
    }

    #[tokio::test]
    async fn latency_tracker_wraps_around() {
        let tracker = Arc::new(LatencyTracker::new(16));
        for i in 1..=20 {
            tracker.record(Duration::from_millis(i));
        }
        let p99 = tracker.p99().unwrap();
        assert!(p99 >= Duration::from_millis(19));
        assert!(p99 <= Duration::from_millis(20));
    }

    // ── Concurrency & Throughput ─────────────────────────────────────────

    #[tokio::test]
    async fn high_throughput_register() {
        let watchdog = TimeoutWatchdog::start();
        let start = Instant::now();
        let mut receivers = Vec::with_capacity(10_000);
        for _ in 0..10_000 {
            let rx = watchdog.register(Duration::from_secs(60), Instant::now());
            receivers.push(rx);
        }
        let elapsed = start.elapsed();
        assert!(
            elapsed < Duration::from_millis(500),
            "10K registrations took {:?} — possible contention",
            elapsed
        );
        drop(receivers);
    }

    #[tokio::test]
    async fn completed_commands_dont_accumulate() {
        let watchdog = TimeoutWatchdog::start();
        for _ in 0..1000 {
            let _rx = watchdog.register(Duration::from_secs(1), Instant::now());
            // rx is immediately dropped — simulates command completing
        }

        tokio::time::sleep(Duration::from_millis(50)).await;

        let rx = watchdog.register(Duration::from_millis(30), Instant::now());
        let result = tokio::time::timeout(Duration::from_millis(200), rx).await;
        assert!(
            result.is_ok(),
            "Watchdog should still function after cleanup"
        );
    }

    #[tokio::test]
    async fn concurrent_register_from_multiple_tasks() {
        let watchdog = TimeoutWatchdog::start();
        let mut handles = Vec::new();
        for _ in 0..10 {
            let w = watchdog.clone();
            handles.push(tokio::spawn(async move {
                let mut rxs = Vec::new();
                for _ in 0..100 {
                    let rx = w.register(Duration::from_millis(50), Instant::now());
                    rxs.push(rx);
                }
                for rx in rxs {
                    let _ = rx.await;
                }
            }));
        }
        for h in handles {
            h.await.unwrap();
        }
    }

    // ── Tokio Starvation ─────────────────────────────────────────────────

    #[tokio::test(flavor = "current_thread")]
    async fn watchdog_fires_under_tokio_starvation() {
        let watchdog = TimeoutWatchdog::start();
        let rx = watchdog.register(Duration::from_millis(100), Instant::now());

        let blocker = tokio::spawn(async {
            let start = Instant::now();
            while start.elapsed() < Duration::from_secs(2) {
                tokio::task::yield_now().await;
                std::thread::sleep(Duration::from_millis(50));
            }
        });

        let result = tokio::time::timeout(Duration::from_secs(1), rx).await;
        assert!(
            result.is_ok(),
            "Watchdog should fire despite Tokio starvation"
        );
        blocker.abort();
    }

    // ── cmd_name_from_bytes ──────────────────────────────────────────────

    #[tokio::test]
    async fn cmd_name_from_bytes_resolves_common_commands() {
        assert_eq!(cmd_name_from_bytes(b"GET"), "GET");
        assert_eq!(cmd_name_from_bytes(b"get"), "GET");
        assert_eq!(cmd_name_from_bytes(b"Set"), "SET");
        assert_eq!(cmd_name_from_bytes(b"HGETALL"), "HGETALL");
        assert_eq!(cmd_name_from_bytes(b"ping"), "PING");
        assert_eq!(cmd_name_from_bytes(b"ZADD"), "ZADD");
        assert_eq!(cmd_name_from_bytes(b"LPUSH"), "LPUSH");
        assert_eq!(cmd_name_from_bytes(b"EXPIRE"), "EXPIRE");
        assert_eq!(cmd_name_from_bytes(b"CLUSTER"), "CLUSTER");
        assert_eq!(cmd_name_from_bytes(b"SUBSCRIBE"), "SUBSCRIBE");
        assert_eq!(cmd_name_from_bytes(b"PTTL"), "PTTL");
        assert_eq!(cmd_name_from_bytes(b"HMGET"), "HMGET");
        assert_eq!(cmd_name_from_bytes(b"HMSET"), "HMSET");
        assert_eq!(cmd_name_from_bytes(b"BITCOUNT"), "BITCOUNT");
        assert_eq!(cmd_name_from_bytes(b"EXPIREAT"), "EXPIREAT");
        assert_eq!(cmd_name_from_bytes(b"PEXPIREAT"), "PEXPIREAT");
        assert_eq!(cmd_name_from_bytes(b"INCRBYFLOAT"), "INCRBYFLOAT");
        assert_eq!(cmd_name_from_bytes(b"unknowncmd"), "UNKNOWN");
    }

    // ── Fork Safety (reinit) ─────────────────────────────────────────────

    #[tokio::test]
    #[serial]
    async fn reinit_global_spawns_new_working_watchdog() {
        // Ensure the global watchdog is initialized.
        let _pre = TimeoutWatchdog::global();

        // Simulate post-fork reinit: replace the global with a fresh instance.
        TimeoutWatchdog::reinit_global();

        // The new watchdog must be functional — register and await a timeout.
        let watchdog = TimeoutWatchdog::global();
        let rx = watchdog.register(Duration::from_millis(50), Instant::now());
        let result = tokio::time::timeout(Duration::from_millis(500), rx).await;
        assert!(
            result.is_ok(),
            "watchdog must fire after reinit_global (simulating post-fork)"
        );
        assert!(result.unwrap().is_ok());
    }

    /// Real fork() with NO explicit `reinit_global()` (the sync Python client's child path):
    /// `global()` must notice the inherited instance and replace it, so a registration in the
    /// child still fires. Standalone requests rely on this: they arm no other timer.
    #[cfg(unix)]
    #[test]
    #[serial]
    fn fork_child_global_replaces_inherited_watchdog() {
        let old = TimeoutWatchdog::global();
        // Held across fork(): the parent's watchdog thread takes these locks every tick, so a
        // fork can land while one is held. The child must not need them.
        let guards: Vec<_> = old.shared.inbox.iter().map(|s| lock(&s.0)).collect();
        // SAFETY: the child only spawns a thread, allocates and calls `_exit`.
        let pid = unsafe { libc::fork() };
        assert!(pid >= 0, "fork failed");
        if pid == 0 {
            // A child that hangs is killed by SIGALRM, failing the test instead of hanging it.
            // SAFETY: async-signal-safe libc call.
            unsafe { libc::alarm(10) };
            let start = Instant::now();
            let mut rx = TimeoutWatchdog::global().register(Duration::from_millis(20), start);
            let code = loop {
                match rx.try_recv() {
                    Ok(()) if start.elapsed() >= Duration::from_millis(20) => break 0,
                    Ok(()) => break 2, // fired early
                    Err(_) if start.elapsed() > Duration::from_secs(5) => break 4,
                    Err(_) => std::thread::sleep(Duration::from_millis(1)),
                }
            };
            // SAFETY: terminate the forked child without running the parent's test harness.
            unsafe { libc::_exit(code) };
        }
        drop(guards);
        let mut status = 0;
        // SAFETY: `pid` is our child.
        let waited = unsafe { libc::waitpid(pid, &mut status, 0) };
        assert_eq!(waited, pid);
        assert!(
            libc::WIFEXITED(status),
            "child did not exit cleanly: {status}"
        );
        assert_eq!(
            libc::WEXITSTATUS(status),
            0,
            "child watchdog failed (2=early, 4=never fired)"
        );
    }
}
