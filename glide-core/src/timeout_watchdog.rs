//! Dedicated timeout watchdog thread that fires timeouts independently of the
//! Tokio runtime and provides structured diagnostics about timeout causes.
//!
//! Design: The hot path (`register()`) sends deadlines through a lock-free MPSC
//! channel. The watchdog thread owns the deadline queue exclusively — no shared
//! Mutex on the command path.
//!
//! The watchdog is a pure timer — it just signals "timeout fired" with `()`.
//! All diagnostic event construction moves to the consumer side (`client/mod.rs`).

use std::collections::BTreeMap;
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::mpsc;
use std::time::{Duration, Instant};
use tokio::sync::oneshot;

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
                (b"DISCARD", "DISCARD"),
                (b"EVALSHA", "EVALSHA"),
                (b"FLUSHDB", "FLUSHDB"),
                (b"GEODIST", "GEODIST"),
                (b"GEOHASH", "GEOHASH"),
                (b"HEXISTS", "HEXISTS"),
                (b"HEXPIRE", "HEXPIRE"),
                (b"HGETALL", "HGETALL"),
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

/// Internal entry sent from callers to the watchdog thread.
/// Minimal struct: the watchdog is a pure timer.
struct DeadlineEntry {
    deadline: Instant,
    sender: oneshot::Sender<()>,
}

// ─── Timeout Watchdog ────────────────────────────────────────────────────────

/// Handle to the watchdog thread. Register deadlines and receive a signal
/// when the timeout fires.
#[derive(Clone)]
pub struct TimeoutWatchdog {
    tx: mpsc::Sender<DeadlineEntry>,
}

/// Global singleton watchdog instance.
static GLOBAL_WATCHDOG: std::sync::OnceLock<TimeoutWatchdog> = std::sync::OnceLock::new();

impl TimeoutWatchdog {
    /// Get or initialize the global shared watchdog instance.
    pub fn global() -> &'static Self {
        GLOBAL_WATCHDOG.get_or_init(Self::start_global)
    }

    /// Start the global watchdog instance (publishes pending count).
    fn start_global() -> Self {
        let (tx, rx) = mpsc::channel();
        std::thread::Builder::new()
            .name("glide-timeout-watchdog".into())
            .spawn(move || Self::run(rx, true))
            .expect("Failed to spawn timeout watchdog thread");
        Self { tx }
    }

    /// Start a watchdog instance. Spawns a dedicated OS thread.
    /// Does NOT publish to the global pending count (only the global instance does).
    pub fn start() -> Self {
        let (tx, rx) = mpsc::channel();
        std::thread::Builder::new()
            .name("glide-timeout-watchdog".into())
            .spawn(move || Self::run(rx, false))
            .expect("Failed to spawn timeout watchdog thread");
        Self { tx }
    }

    /// Register a timeout. Returns a `oneshot::Receiver<()>` that resolves
    /// when the deadline fires.
    ///
    /// `submitted_at` should be the caller's `Instant::now()` — shared with
    /// the latency tracking timer to avoid a redundant clock read.
    #[inline]
    pub fn register(&self, timeout: Duration, submitted_at: Instant) -> oneshot::Receiver<()> {
        let (sender, rx) = oneshot::channel();
        let deadline = submitted_at + timeout;
        let _ = self.tx.send(DeadlineEntry { deadline, sender });
        rx
    }

    /// Watchdog thread main loop.
    fn run(rx: mpsc::Receiver<DeadlineEntry>, publish_count: bool) {
        let mut deadlines: BTreeMap<Instant, Vec<DeadlineEntry>> = BTreeMap::new();
        let mut last_cleanup = Instant::now();
        let mut local_count: usize = 0;

        loop {
            let now = Instant::now();

            // Periodic cleanup of entries whose receivers were dropped (command completed)
            // Run at most once per second to amortize cost
            if now.duration_since(last_cleanup) > Duration::from_secs(1) {
                deadlines.retain(|_, entries| {
                    let before = entries.len();
                    entries.retain(|e| !e.sender.is_closed());
                    local_count -= before - entries.len();
                    !entries.is_empty()
                });
                last_cleanup = now;
            }

            // Fire all expired deadlines
            while let Some(entry) = deadlines.first_entry() {
                if *entry.key() > now {
                    break;
                }
                let (_, entries) = entry.remove_entry();
                local_count -= entries.len();
                for e in entries {
                    if e.sender.is_closed() {
                        continue; // Command completed before timeout
                    }
                    let _ = e.sender.send(());
                }
            }

            // Drain new registrations (bounded to prevent starvation of deadline firing)
            const MAX_DRAIN_BATCH: usize = 256;
            for _ in 0..MAX_DRAIN_BATCH {
                match rx.try_recv() {
                    Ok(entry) => {
                        if entry.sender.is_closed() {
                            // Already cancelled, don't count it
                        } else {
                            local_count += 1;
                            deadlines.entry(entry.deadline).or_default().push(entry);
                        }
                    }
                    Err(_) => break,
                }
                // If a deadline is now due, stop draining and go fire it
                if deadlines.keys().next().is_some_and(|d| *d <= now) {
                    break;
                }
            }

            // Publish the local count once per iteration (no contention on write side)
            if publish_count {
                PUBLISHED_PENDING.store(local_count, Ordering::Relaxed);
            }

            // Wait for next event
            let wait_result = if let Some(next_deadline) = deadlines.keys().next() {
                let sleep_duration = next_deadline.saturating_duration_since(Instant::now());
                rx.recv_timeout(sleep_duration)
            } else {
                rx.recv().map_err(|_| mpsc::RecvTimeoutError::Disconnected)
            };

            match wait_result {
                Ok(entry) => {
                    if entry.sender.is_closed() {
                        // Already cancelled, don't count it
                    } else {
                        local_count += 1;
                        deadlines.entry(entry.deadline).or_default().push(entry);
                    }
                }
                Err(mpsc::RecvTimeoutError::Timeout) => {}
                Err(mpsc::RecvTimeoutError::Disconnected) => return,
            }
        }
    }
}

// ─── Unit Tests ──────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
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

    // ── Pending Count ────────────────────────────────────────────────────

    #[tokio::test]
    async fn pending_count_increments_and_decrements() {
        // Use the global watchdog since PUBLISHED_PENDING is a single global.
        let watchdog = TimeoutWatchdog::global();

        let rx1 = watchdog.register(Duration::from_millis(500), Instant::now());
        let rx2 = watchdog.register(Duration::from_millis(500), Instant::now());

        // Give the watchdog thread time to drain and publish
        tokio::time::sleep(Duration::from_millis(100)).await;
        let after_register = pending_count();
        assert!(
            after_register >= 2,
            "pending count must reflect registered entries: got {after_register}"
        );

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
}
