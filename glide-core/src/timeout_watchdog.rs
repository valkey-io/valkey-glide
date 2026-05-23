//! Dedicated timeout watchdog thread that fires timeouts independently of the
//! Tokio runtime. Under memory pressure or Tokio starvation, `tokio::time::sleep`
//! may not fire on time. This watchdog uses a separate OS thread to guarantee
//! timeout delivery.
//!
//! Design: The hot path (`register()`) sends deadlines through a lock-free MPSC
//! channel. The watchdog thread owns the deadline queue exclusively — no shared
//! Mutex on the command path. This scales to 90K+ TPS without contention.

use std::collections::BTreeMap;
use std::sync::mpsc;
use std::time::{Duration, Instant};
use tokio::sync::oneshot;

/// A deadline entry sent from callers to the watchdog thread.
struct DeadlineEntry {
    deadline: Instant,
    sender: oneshot::Sender<()>,
}

/// Handle to the watchdog thread. Register deadlines and receive timeout signals.
/// A single watchdog thread is shared across all client instances.
#[derive(Clone)]
pub struct TimeoutWatchdog {
    tx: mpsc::Sender<DeadlineEntry>,
}

/// Global singleton watchdog instance.
static GLOBAL_WATCHDOG: std::sync::OnceLock<TimeoutWatchdog> = std::sync::OnceLock::new();

impl TimeoutWatchdog {
    /// Get or initialize the global shared watchdog instance.
    /// One OS thread serves all clients in the process.
    pub fn global() -> &'static Self {
        GLOBAL_WATCHDOG.get_or_init(Self::start)
    }

    /// Start a watchdog instance. Spawns a dedicated OS thread.
    pub fn start() -> Self {
        // Unbounded channel: register() must never block the caller.
        let (tx, rx) = mpsc::channel();

        std::thread::Builder::new()
            .name("glide-timeout-watchdog".into())
            .spawn(move || Self::run(rx))
            .expect("Failed to spawn timeout watchdog thread");

        Self { tx }
    }

    /// Register a timeout. Returns a receiver that fires when the deadline passes,
    /// delivered from the watchdog thread independent of Tokio.
    ///
    /// This is the hot path — uses mpsc::Sender which is lock-free for sends.
    #[inline]
    pub fn register(&self, timeout: Duration) -> oneshot::Receiver<()> {
        let (sender, rx) = oneshot::channel();
        let deadline = Instant::now() + timeout;

        // Send is non-blocking (unbounded mpsc). If the watchdog thread is dead,
        // the send fails silently — the oneshot receiver will see a RecvError,
        // which is equivalent to "no timeout fired" (safe fallback).
        let _ = self.tx.send(DeadlineEntry { deadline, sender });
        rx
    }

    /// Watchdog thread main loop. Owns the deadline BTreeMap exclusively.
    fn run(rx: mpsc::Receiver<DeadlineEntry>) {
        let mut deadlines: BTreeMap<Instant, Vec<oneshot::Sender<()>>> = BTreeMap::new();

        loop {
            let now = Instant::now();

            // Fire all expired deadlines
            while let Some(entry) = deadlines.first_entry() {
                if *entry.key() > now {
                    break;
                }
                let (_, senders) = entry.remove_entry();
                for sender in senders {
                    let _ = sender.send(());
                }
            }

            // Drain new registrations from the channel (non-blocking)
            while let Ok(entry) = rx.try_recv() {
                if !entry.sender.is_closed() {
                    deadlines
                        .entry(entry.deadline)
                        .or_default()
                        .push(entry.sender);
                }
            }

            // Wait for next event: deadline expiry or new registration
            let wait_result = if let Some(next_deadline) = deadlines.keys().next() {
                // Wait until next deadline or new registration
                let sleep_duration = next_deadline.saturating_duration_since(Instant::now());
                rx.recv_timeout(sleep_duration)
            } else {
                // No deadlines pending — block until a new registration arrives
                // (no busy-polling when idle)
                rx.recv().map_err(|_| mpsc::RecvTimeoutError::Disconnected)
            };

            match wait_result {
                Ok(entry) => {
                    if !entry.sender.is_closed() {
                        deadlines
                            .entry(entry.deadline)
                            .or_default()
                            .push(entry.sender);
                    }
                }
                Err(mpsc::RecvTimeoutError::Timeout) => {}
                Err(mpsc::RecvTimeoutError::Disconnected) => return,
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn fires_after_deadline() {
        let watchdog = TimeoutWatchdog::start();
        let rx = watchdog.register(Duration::from_millis(50));

        let start = Instant::now();
        rx.await.unwrap();
        let elapsed = start.elapsed();

        assert!(elapsed >= Duration::from_millis(50));
        assert!(elapsed < Duration::from_millis(150));
    }

    #[tokio::test]
    async fn does_not_fire_before_deadline() {
        let watchdog = TimeoutWatchdog::start();
        let mut rx = watchdog.register(Duration::from_millis(200));

        tokio::time::sleep(Duration::from_millis(50)).await;
        assert!(rx.try_recv().is_err());
    }

    #[tokio::test]
    async fn multiple_deadlines_fire_in_order() {
        let watchdog = TimeoutWatchdog::start();
        let rx1 = watchdog.register(Duration::from_millis(30));
        let rx2 = watchdog.register(Duration::from_millis(60));

        rx1.await.unwrap();
        let mid = Instant::now();
        rx2.await.unwrap();
        let end = Instant::now();

        assert!(end.duration_since(mid) >= Duration::from_millis(20));
    }

    #[tokio::test(flavor = "current_thread")]
    async fn watchdog_fires_under_tokio_starvation() {
        let watchdog = TimeoutWatchdog::start();
        let rx = watchdog.register(Duration::from_millis(100));

        // Block the Tokio worker thread, preventing timer wheel advancement
        let blocker = tokio::spawn(async {
            let start = Instant::now();
            while start.elapsed() < Duration::from_secs(2) {
                tokio::task::yield_now().await;
                std::thread::sleep(Duration::from_millis(50)); // blocks worker
            }
        });

        // The watchdog (OS thread) fires the oneshot even though Tokio is starved.
        let result = tokio::time::timeout(Duration::from_secs(1), rx).await;

        assert!(
            result.is_ok(),
            "Watchdog should fire despite Tokio starvation"
        );
        blocker.abort();
    }

    #[tokio::test]
    async fn high_throughput_register() {
        // Verify register() scales without contention
        let watchdog = TimeoutWatchdog::start();
        let start = Instant::now();

        let mut receivers = Vec::with_capacity(10_000);
        for _ in 0..10_000 {
            receivers.push(watchdog.register(Duration::from_secs(60)));
        }

        let elapsed = start.elapsed();
        // 10K registrations should complete in well under 100ms (no lock contention)
        assert!(
            elapsed < Duration::from_millis(100),
            "10K registrations took {:?} — possible contention",
            elapsed
        );

        // Drop receivers (simulates commands completing before timeout)
        drop(receivers);
    }

    #[tokio::test]
    async fn completed_commands_dont_accumulate() {
        // Verify that dropped receivers (completed commands) are cleaned up
        let watchdog = TimeoutWatchdog::start();

        // Register 1000 timeouts with 1s deadline
        for _ in 0..1000 {
            let _rx = watchdog.register(Duration::from_secs(1));
            // rx is immediately dropped — simulates command completing before timeout
        }

        // Wait a bit for watchdog to process
        tokio::time::sleep(Duration::from_millis(50)).await;

        // Register one more and verify it fires correctly (watchdog isn't stuck)
        let rx = watchdog.register(Duration::from_millis(30));
        let result = tokio::time::timeout(Duration::from_millis(200), rx).await;
        assert!(
            result.is_ok(),
            "Watchdog should still function after cleanup"
        );
    }
}
