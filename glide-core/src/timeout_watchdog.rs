//! Dedicated timeout watchdog thread that fires timeouts independently of the
//! Tokio runtime. Under memory pressure or Tokio starvation, `tokio::time::sleep`
//! may not fire on time. This watchdog uses a separate OS thread to guarantee
//! timeout delivery.

use std::collections::BTreeMap;
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::oneshot;

/// Handle to the watchdog thread. Register deadlines and receive timeout signals.
#[derive(Clone)]
pub struct TimeoutWatchdog {
    state: Arc<WatchdogState>,
}

struct WatchdogState {
    deadlines: Mutex<BTreeMap<Instant, Vec<oneshot::Sender<()>>>>,
    condvar: Condvar,
}

impl TimeoutWatchdog {
    /// Start the watchdog background thread.
    pub fn start() -> Self {
        let state = Arc::new(WatchdogState {
            deadlines: Mutex::new(BTreeMap::new()),
            condvar: Condvar::new(),
        });

        let state_weak = Arc::downgrade(&state);
        std::thread::Builder::new()
            .name("glide-timeout-watchdog".into())
            .spawn(move || Self::run(state_weak))
            .expect("Failed to spawn timeout watchdog thread");

        Self { state }
    }

    /// Register a timeout. Returns a receiver that fires when the deadline passes,
    /// delivered from the watchdog thread independent of Tokio.
    pub fn register(&self, timeout: Duration) -> oneshot::Receiver<()> {
        let (tx, rx) = oneshot::channel();
        let deadline = Instant::now() + timeout;

        {
            let mut deadlines = self.state.deadlines.lock().unwrap();
            deadlines.entry(deadline).or_default().push(tx);
        }

        // Wake watchdog in case this deadline is sooner than current sleep
        self.state.condvar.notify_one();
        rx
    }

    fn run(state_weak: std::sync::Weak<WatchdogState>) {
        loop {
            let state = match state_weak.upgrade() {
                Some(s) => s,
                None => return, // All clients dropped, exit thread
            };

            let sleep_duration = {
                let mut deadlines = state.deadlines.lock().unwrap();
                let now = Instant::now();

                // Fire all expired deadlines
                let expired_keys: Vec<_> = deadlines.range(..=now).map(|(k, _)| *k).collect();
                for key in expired_keys {
                    if let Some(senders) = deadlines.remove(&key) {
                        for sender in senders {
                            let _ = sender.send(());
                        }
                    }
                }

                // Sleep until next deadline or 1s (idle poll)
                deadlines
                    .keys()
                    .next()
                    .map(|next| next.saturating_duration_since(now))
                    .unwrap_or(Duration::from_secs(1))
            };

            // Wait, waking early if a new deadline is registered
            let guard = state.deadlines.lock().unwrap();
            let _ = state.condvar.wait_timeout(guard, sleep_duration);
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
        // tokio::select! will observe it on the next yield from the blocker.
        let result = tokio::time::timeout(Duration::from_secs(1), rx).await;

        assert!(
            result.is_ok(),
            "Watchdog should fire despite Tokio starvation"
        );
        blocker.abort();
    }
}
