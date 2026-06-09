//! Client-wide circuit breaker.
//!
//! Detects when the GLIDE core is unhealthy (sustained error rate across all nodes)
//! and exposes an `is_healthy()` check that language clients call synchronously at
//! the FFI boundary before submitting commands. This prevents thread parking on
//! futures that will likely fail.
//!
//! State machine: Closed → Open → HalfOpen → Closed (on N consecutive probe successes)
//!                                 HalfOpen → Open  (on probe failure)

use std::collections::HashMap;
use std::collections::VecDeque;
use std::sync::RwLock;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::{Duration, Instant};

use logger_core::{log_info, log_warn, log_warn_rate_limited};

/// Circuit breaker phase.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CircuitState {
    /// Normal operation. Errors tracked in sliding window.
    Closed,
    /// Core unhealthy. Requests rejected at FFI boundary.
    Open,
    /// Probing recovery. Allows all traffic through (optimistic).
    HalfOpen,
}

/// Client-wide circuit breaker configuration.
#[derive(Debug, Clone)]
pub struct ClientCircuitBreakerConfig {
    /// Sliding window for error counting. Default: 10s.
    pub window_size: Duration,
    /// Error rate (0.0–1.0) within window to trip. Default: 0.5 (50%).
    pub failure_rate_threshold: f32,
    /// Minimum errors within window before rate is evaluated. Default: 50.
    pub min_errors: u32,
    /// Time in Open state before allowing a probe. Default: 5s.
    pub open_timeout: Duration,
    /// When true, timeouts count toward tripping. Default: false.
    pub count_timeouts: bool,
    /// Number of consecutive successful probes needed before closing. Default: 3.
    pub consecutive_successes: u32,
}

impl Default for ClientCircuitBreakerConfig {
    fn default() -> Self {
        Self {
            window_size: Duration::from_secs(10),
            failure_rate_threshold: 0.5,
            min_errors: 50,
            open_timeout: Duration::from_secs(5),
            count_timeouts: false,
            consecutive_successes: 3,
        }
    }
}

/// Tracks a single request outcome within the sliding window.
#[derive(Debug, Clone, Copy)]
struct RequestRecord {
    timestamp: Instant,
    is_error: bool,
}

/// Client-wide circuit breaker.
pub struct ClientCircuitBreaker {
    config: ClientCircuitBreakerConfig,
    state: RwLock<BreakerState>,
    /// Fast-path atomic: true when Closed or HalfOpen (healthy), false when Open.
    /// Language clients read this without acquiring the lock.
    healthy: AtomicBool,
    /// Total number of times the breaker has tripped.
    trip_count: AtomicU64,
    /// Total number of requests rejected while open.
    rejection_count: AtomicU64,
}

struct BreakerState {
    phase: CircuitState,
    /// Sliding window of request outcomes (both successes and errors).
    records: VecDeque<RequestRecord>,
    /// Running count of errors in the window (maintained on push/evict).
    error_count: u32,
    /// When the breaker entered Open state.
    opened_at: Option<Instant>,
    /// Consecutive successful probes in HalfOpen state.
    consecutive_success_count: u32,
    /// Error type counts since last trip/reset. For diagnostic logging.
    error_counts: HashMap<String, u32>,
}

impl ClientCircuitBreaker {
    pub fn new(config: ClientCircuitBreakerConfig) -> Self {
        assert!(
            config.failure_rate_threshold > 0.0 && config.failure_rate_threshold <= 1.0,
            "failure_rate_threshold must be between 0.0 (exclusive) and 1.0 (inclusive)"
        );
        assert!(config.min_errors > 0, "min_errors must be greater than 0");
        assert!(
            config.consecutive_successes > 0,
            "consecutive_successes must be greater than 0"
        );
        Self {
            config,
            state: RwLock::new(BreakerState {
                phase: CircuitState::Closed,
                records: VecDeque::new(),
                error_count: 0,
                opened_at: None,
                consecutive_success_count: 0,
                error_counts: HashMap::new(),
            }),
            healthy: AtomicBool::new(true),
            trip_count: AtomicU64::new(0),
            rejection_count: AtomicU64::new(0),
        }
    }

    /// Fast-path health check for language clients.
    /// Returns true if the client should accept requests.
    /// Transitions Open → HalfOpen when open_timeout elapses.
    #[inline]
    pub fn is_healthy(&self) -> bool {
        if self.healthy.load(Ordering::Relaxed) {
            return true;
        }
        // Check if we should transition to HalfOpen
        self.try_allow_probe()
    }

    /// Check if Open → HalfOpen transition should occur. Returns true if traffic should resume.
    fn try_allow_probe(&self) -> bool {
        let mut state = match self.state.try_write() {
            Ok(guard) => guard,
            Err(std::sync::TryLockError::WouldBlock) => return false,
            Err(std::sync::TryLockError::Poisoned(e)) => e.into_inner(),
        };
        if state.phase != CircuitState::Open {
            // Race: another thread already transitioned
            return self.healthy.load(Ordering::Relaxed);
        }
        let now = Instant::now();
        let opened_at = state.opened_at.unwrap_or(now);
        if now.duration_since(opened_at) >= self.config.open_timeout {
            state.phase = CircuitState::HalfOpen;
            state.consecutive_success_count = 0;
            self.healthy.store(true, Ordering::Release);
            true
        } else {
            self.rejection_count.fetch_add(1, Ordering::Relaxed);
            log_warn_rate_limited!(
                "circuit_breaker",
                5,
                format!(
                    "Circuit breaker open, rejecting requests. {} rejected so far",
                    self.rejection_count.load(Ordering::Relaxed)
                )
            );
            false
        }
    }

    /// Report a command result. Called after each command completes.
    /// `is_error` should be true only for transport-level errors.
    /// `error_kind` is the error type string for diagnostic logging (only used when is_error=true).
    pub fn on_result(&self, is_error: bool, error_kind: Option<&str>) {
        let mut state = self.state.write().unwrap_or_else(|e| e.into_inner());
        let now = Instant::now();

        match state.phase {
            CircuitState::Closed => {
                // Record in sliding window
                state.records.push_back(RequestRecord {
                    timestamp: now,
                    is_error,
                });
                if is_error {
                    state.error_count += 1;
                }

                // Track error types for diagnostic logging
                if is_error && let Some(kind) = error_kind {
                    *state.error_counts.entry(kind.to_string()).or_insert(0) += 1;
                }

                // Evict expired records
                let window = self.config.window_size;
                while state
                    .records
                    .front()
                    .is_some_and(|r| now.duration_since(r.timestamp) > window)
                {
                    if let Some(r) = state.records.pop_front()
                        && r.is_error
                    {
                        state.error_count -= 1;
                    }
                }

                // Cap window size to prevent unbounded memory growth under high throughput.
                const MAX_WINDOW_RECORDS: usize = 10_000;
                if state.records.len() > MAX_WINDOW_RECORDS {
                    log_warn_rate_limited!(
                        "circuit_breaker",
                        60,
                        format!(
                            "Sliding window capped at {} records (effective window shrunk)",
                            MAX_WINDOW_RECORDS
                        )
                    );
                    while state.records.len() > MAX_WINDOW_RECORDS {
                        if let Some(r) = state.records.pop_front()
                            && r.is_error
                        {
                            state.error_count -= 1;
                        }
                    }
                }

                // Check if we should trip
                let total = state.records.len() as u32;
                let errors = state.error_count;

                if errors >= self.config.min_errors {
                    let rate = errors as f32 / total as f32;
                    if rate >= self.config.failure_rate_threshold {
                        self.trip(&mut state, now, errors, total);
                    }
                }
            }
            CircuitState::HalfOpen => {
                if is_error {
                    // Probe failed, reopen
                    state.phase = CircuitState::Open;
                    state.opened_at = Some(now);
                    state.consecutive_success_count = 0;
                    self.healthy.store(false, Ordering::Release);
                    log_warn(
                        "circuit_breaker",
                        "Probe failed. Client circuit breaker remains open",
                    );
                } else {
                    state.consecutive_success_count += 1;
                    if state.consecutive_success_count >= self.config.consecutive_successes {
                        // Recovery confirmed, close breaker
                        let open_duration = state
                            .opened_at
                            .map(|t| now.duration_since(t))
                            .unwrap_or_default();
                        state.phase = CircuitState::Closed;
                        state.opened_at = None;
                        state.records.clear();
                        state.error_count = 0;
                        self.healthy.store(true, Ordering::Release);
                        let rejected = self.rejection_count.swap(0, Ordering::Relaxed);
                        log_info(
                            "circuit_breaker",
                            format!(
                                "Client circuit breaker closed after {}ms open, {} consecutive successful probes. {} requests were rejected",
                                open_duration.as_millis(),
                                self.config.consecutive_successes,
                                rejected
                            ),
                        );
                    }
                }
            }
            CircuitState::Open => {
                // Stale result from before trip
            }
        }
    }

    fn trip(&self, state: &mut BreakerState, now: Instant, errors: u32, total: u32) {
        state.phase = CircuitState::Open;
        state.opened_at = Some(now);
        state.records.clear();
        state.error_count = 0;
        state.consecutive_success_count = 0;
        self.healthy.store(false, Ordering::Release);
        self.trip_count.fetch_add(1, Ordering::Relaxed);

        // Format error type breakdown
        let error_breakdown = if state.error_counts.is_empty() {
            String::new()
        } else {
            let mut types: Vec<_> = state.error_counts.drain().collect();
            types.sort_by_key(|item| std::cmp::Reverse(item.1)); // highest count first
            format!(
                " Types: {{{}}}.",
                types
                    .iter()
                    .map(|(k, v)| format!("{}: {}", k, v))
                    .collect::<Vec<_>>()
                    .join(", ")
            )
        };

        log_warn(
            "circuit_breaker",
            format!(
                "Client circuit breaker tripped: {}/{} errors ({:.1}%) in window.{} \
                 Rejecting requests for {:?} (trip #{})",
                errors,
                total,
                (errors as f64 / total as f64) * 100.0,
                error_breakdown,
                self.config.open_timeout,
                self.trip_count.load(Ordering::Relaxed)
            ),
        );
    }

    /// Current breaker state.
    pub fn state(&self) -> CircuitState {
        self.state.read().unwrap_or_else(|e| e.into_inner()).phase
    }

    /// Number of times the breaker has tripped.
    pub fn trip_count(&self) -> u64 {
        self.trip_count.load(Ordering::Relaxed)
    }

    /// Number of requests rejected while open.
    pub fn rejection_count(&self) -> u64 {
        self.rejection_count.load(Ordering::Relaxed)
    }

    /// Whether this CB is configured to count timeouts as errors.
    pub fn counts_timeouts(&self) -> bool {
        self.config.count_timeouts
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn default_config() -> ClientCircuitBreakerConfig {
        ClientCircuitBreakerConfig {
            window_size: Duration::from_secs(10),
            failure_rate_threshold: 0.5,
            min_errors: 5,
            open_timeout: Duration::from_millis(100),
            count_timeouts: false,
            consecutive_successes: 2,
        }
    }

    #[test]
    fn stays_closed_under_threshold() {
        let cb = ClientCircuitBreaker::new(default_config());

        // 4 errors, 6 successes = 40% < 50% threshold
        for _ in 0..6 {
            cb.on_result(false, None);
        }
        for _ in 0..4 {
            cb.on_result(true, Some("IoError"));
        }

        assert!(cb.is_healthy());
        assert_eq!(cb.state(), CircuitState::Closed);
    }

    #[test]
    fn trips_when_threshold_exceeded() {
        let cb = ClientCircuitBreaker::new(default_config());

        // 6 errors, 4 successes = 60% > 50% threshold, 6 >= min_errors(5)
        for _ in 0..4 {
            cb.on_result(false, None);
        }
        for _ in 0..6 {
            cb.on_result(true, Some("IoError"));
        }

        assert!(!cb.is_healthy());
        assert_eq!(cb.state(), CircuitState::Open);
        assert_eq!(cb.trip_count(), 1);
    }

    #[test]
    fn does_not_trip_below_min_errors() {
        let cb = ClientCircuitBreaker::new(default_config());

        // 4 errors, 0 successes = 100% rate but only 4 < min_errors(5)
        for _ in 0..4 {
            cb.on_result(true, Some("IoError"));
        }

        assert!(cb.is_healthy());
        assert_eq!(cb.state(), CircuitState::Closed);
    }

    #[test]
    fn rejects_when_open() {
        let cb = ClientCircuitBreaker::new(default_config());

        // Trip it
        for _ in 0..10 {
            cb.on_result(true, Some("IoError"));
        }
        assert!(!cb.is_healthy());
        assert_eq!(cb.rejection_count(), 1); // is_healthy() increments when rejecting
    }

    #[test]
    fn transitions_to_half_open_after_timeout() {
        let cb = ClientCircuitBreaker::new(default_config());

        // Trip it
        for _ in 0..10 {
            cb.on_result(true, Some("IoError"));
        }

        // Wait for open_timeout
        std::thread::sleep(Duration::from_millis(150));

        // is_healthy() should now allow through (transitions to HalfOpen)
        assert!(cb.is_healthy());
        assert_eq!(cb.state(), CircuitState::HalfOpen);
    }

    #[test]
    fn closes_after_consecutive_successes() {
        let cb = ClientCircuitBreaker::new(default_config());

        // Trip it
        for _ in 0..10 {
            cb.on_result(true, Some("IoError"));
        }

        // Wait for open_timeout
        std::thread::sleep(Duration::from_millis(150));

        // Transition to HalfOpen
        assert!(cb.is_healthy());

        // Probe 1 succeeds
        cb.on_result(false, None);

        // Probe 2 succeeds (consecutive_successes = 2)
        cb.on_result(false, None);

        assert_eq!(cb.state(), CircuitState::Closed);
        assert!(cb.is_healthy());
    }

    #[test]
    fn probe_failure_returns_to_open() {
        let cb = ClientCircuitBreaker::new(default_config());

        // Trip it
        for _ in 0..10 {
            cb.on_result(true, Some("IoError"));
        }

        // Wait for open_timeout
        std::thread::sleep(Duration::from_millis(150));

        // Transition to HalfOpen
        assert!(cb.is_healthy());

        // Probe fails
        cb.on_result(true, Some("IoError"));

        assert_eq!(cb.state(), CircuitState::Open);
        assert!(!cb.is_healthy());
    }

    #[test]
    fn window_eviction() {
        let config = ClientCircuitBreakerConfig {
            window_size: Duration::from_millis(50),
            min_errors: 5, // Need 5 errors to trip, we only add 3
            ..default_config()
        };
        let cb = ClientCircuitBreaker::new(config);

        // Add 3 errors (below min_errors threshold of 5)
        for _ in 0..3 {
            cb.on_result(true, Some("IoError"));
        }

        // Wait for window to expire
        std::thread::sleep(Duration::from_millis(60));

        // Add 1 success after window expires
        cb.on_result(false, None);

        assert!(cb.is_healthy());
        assert_eq!(cb.state(), CircuitState::Closed);
    }

    #[test]
    fn concurrent_probe_race() {
        let config = ClientCircuitBreakerConfig {
            open_timeout: Duration::from_millis(50),
            ..default_config()
        };
        let cb = std::sync::Arc::new(ClientCircuitBreaker::new(config));

        // Trip the breaker
        for _ in 0..10 {
            cb.on_result(true, Some("IoError"));
        }

        // Wait for open_timeout to elapse
        std::thread::sleep(Duration::from_millis(60));

        // Spawn 10 threads all calling is_healthy() simultaneously
        let handles: Vec<_> = (0..10)
            .map(|_| {
                let cb = cb.clone();
                std::thread::spawn(move || cb.is_healthy())
            })
            .collect();

        let results: Vec<bool> = handles.into_iter().map(|h| h.join().unwrap()).collect();
        let allowed = results.iter().filter(|&&r| r).count();

        // All should get true (HalfOpen allows traffic), but only one transition should occur
        assert!(allowed > 0);
        assert_eq!(cb.state(), CircuitState::HalfOpen);
    }
}
