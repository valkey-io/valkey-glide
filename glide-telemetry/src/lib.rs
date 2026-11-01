use serde::Serialize;
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
mod metrics_exporter_file;
mod open_telemetry;
mod span_exporter_file;

pub use metrics_exporter_file::FileMetricExporter;
pub use open_telemetry::*;
pub use span_exporter_file::SpanExporterFile;

#[derive(Default, Serialize)]
pub struct Telemetry {
    /// Total number of connections opened to Valkey
    total_connections: AtomicUsize,
    /// Total number of GLIDE clients
    total_clients: AtomicUsize,
    /// Total number of values compressed
    total_values_compressed: AtomicUsize,
    /// Total number of values decompressed
    total_values_decompressed: AtomicUsize,
    /// Total original bytes before compression
    total_original_bytes: AtomicUsize,
    /// Total bytes after compression
    total_bytes_compressed: AtomicUsize,
    /// Total bytes after decompression
    total_bytes_decompressed: AtomicUsize,
    /// Number of times compression was skipped
    compression_skipped_count: AtomicUsize,
    /// Number of times subscriptions were detected as out of sync
    subscription_out_of_sync_count: AtomicUsize,
    /// Unix timestamp (in milliseconds) of the last time subscriptions were in sync
    subscription_last_sync_timestamp: AtomicU64,
}

static TELEMETRY: Telemetry = Telemetry::new();

impl Telemetry {
    /// Increment the total number of connections by `incr_by`
    /// Return the number of total connections after the increment
    pub fn incr_total_connections(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_connections, incr_by)
    }

    /// Decrease the total number of connections by `decr_by`
    /// Return the number of total connections after the decrease
    pub fn decr_total_connections(decr_by: usize) -> usize {
        saturating_sub(&TELEMETRY.total_connections, decr_by)
    }

    /// Increment the total number of clients by `incr_by`
    /// Return the number of total clients after the increment
    pub fn incr_total_clients(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_clients, incr_by)
    }

    /// Decrease the total number of clients by `decr_by`
    /// Return the number of total clients after the decrease
    pub fn decr_total_clients(decr_by: usize) -> usize {
        saturating_sub(&TELEMETRY.total_clients, decr_by)
    }

    /// Return the number of active connections
    pub fn total_connections() -> usize {
        TELEMETRY.total_connections.load(Ordering::Relaxed)
    }

    /// Return the number of active clients
    pub fn total_clients() -> usize {
        TELEMETRY.total_clients.load(Ordering::Relaxed)
    }

    /// Increment the total number of values compressed
    pub fn incr_total_values_compressed(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_values_compressed, incr_by)
    }

    /// Return the total number of values compressed
    pub fn total_values_compressed() -> usize {
        TELEMETRY.total_values_compressed.load(Ordering::Relaxed)
    }

    /// Increment the total number of values decompressed
    pub fn incr_total_values_decompressed(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_values_decompressed, incr_by)
    }

    /// Return the total number of values decompressed
    pub fn total_values_decompressed() -> usize {
        TELEMETRY.total_values_decompressed.load(Ordering::Relaxed)
    }

    /// Increment the total original bytes before compression
    pub fn incr_total_original_bytes(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_original_bytes, incr_by)
    }

    /// Return the total original bytes before compression
    pub fn total_original_bytes() -> usize {
        TELEMETRY.total_original_bytes.load(Ordering::Relaxed)
    }

    /// Increment the total bytes after compression
    pub fn incr_total_bytes_compressed(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_bytes_compressed, incr_by)
    }

    /// Return the total bytes after compression
    pub fn total_bytes_compressed() -> usize {
        TELEMETRY.total_bytes_compressed.load(Ordering::Relaxed)
    }

    /// Increment the total bytes after decompression
    pub fn incr_total_bytes_decompressed(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.total_bytes_decompressed, incr_by)
    }

    /// Return the total bytes after decompression
    pub fn total_bytes_decompressed() -> usize {
        TELEMETRY.total_bytes_decompressed.load(Ordering::Relaxed)
    }

    /// Increment the compression skipped count
    pub fn incr_compression_skipped_count(incr_by: usize) -> usize {
        saturating_add(&TELEMETRY.compression_skipped_count, incr_by)
    }

    /// Return the compression skipped count
    pub fn compression_skipped_count() -> usize {
        TELEMETRY.compression_skipped_count.load(Ordering::Relaxed)
    }

    /// Increment the subscription out of sync count
    /// Return the new count after increment
    pub fn incr_subscription_out_of_sync() -> usize {
        saturating_add(&TELEMETRY.subscription_out_of_sync_count, 1)
    }

    /// Get the current subscription out of sync count
    pub fn subscription_out_of_sync_count() -> usize {
        TELEMETRY
            .subscription_out_of_sync_count
            .load(Ordering::Relaxed)
    }

    /// Update the subscription last sync timestamp
    /// Return the new timestamp
    pub fn update_subscription_last_sync_timestamp(timestamp: u64) -> u64 {
        TELEMETRY
            .subscription_last_sync_timestamp
            .store(timestamp, Ordering::Relaxed);
        timestamp
    }

    /// Get the subscription last sync timestamp
    pub fn subscription_last_sync_timestamp() -> u64 {
        TELEMETRY
            .subscription_last_sync_timestamp
            .load(Ordering::Relaxed)
    }

    /// Reset the telemetry collected thus far.
    /// Fields are reset individually, not as one atomic snapshot.
    pub fn reset() {
        TELEMETRY.reset_all();
    }

    const fn new() -> Self {
        Self {
            total_connections: AtomicUsize::new(0),
            total_clients: AtomicUsize::new(0),
            total_values_compressed: AtomicUsize::new(0),
            total_values_decompressed: AtomicUsize::new(0),
            total_original_bytes: AtomicUsize::new(0),
            total_bytes_compressed: AtomicUsize::new(0),
            total_bytes_decompressed: AtomicUsize::new(0),
            compression_skipped_count: AtomicUsize::new(0),
            subscription_out_of_sync_count: AtomicUsize::new(0),
            subscription_last_sync_timestamp: AtomicU64::new(0),
        }
    }

    fn reset_all(&self) {
        self.total_connections.store(0, Ordering::Relaxed);
        self.total_clients.store(0, Ordering::Relaxed);
        self.total_values_compressed.store(0, Ordering::Relaxed);
        self.total_values_decompressed.store(0, Ordering::Relaxed);
        self.total_original_bytes.store(0, Ordering::Relaxed);
        self.total_bytes_compressed.store(0, Ordering::Relaxed);
        self.total_bytes_decompressed.store(0, Ordering::Relaxed);
        self.compression_skipped_count.store(0, Ordering::Relaxed);
        self.subscription_out_of_sync_count
            .store(0, Ordering::Relaxed);
        self.subscription_last_sync_timestamp
            .store(0, Ordering::Relaxed);
    }
}

/// Saturating-add `n` into `counter`, returning the post-operation value.
fn saturating_add(counter: &AtomicUsize, n: usize) -> usize {
    update(counter, |v| v.saturating_add(n))
}

/// Saturating-subtract `n` from `counter`, returning the post-operation value.
fn saturating_sub(counter: &AtomicUsize, n: usize) -> usize {
    update(counter, |v| v.saturating_sub(n))
}

/// Apply `f` to `counter` with a CAS loop, returning the new value.
fn update(counter: &AtomicUsize, f: impl Fn(usize) -> usize) -> usize {
    let mut current = counter.load(Ordering::Relaxed);
    loop {
        let new = f(current);
        match counter.compare_exchange_weak(current, new, Ordering::Relaxed, Ordering::Relaxed) {
            Ok(_) => return new,
            Err(actual) => current = actual,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn incr_returns_post_operation_value() {
        let t = Telemetry::new();
        assert_eq!(saturating_add(&t.total_connections, 5), 5);
        assert_eq!(saturating_add(&t.total_connections, 3), 8);
    }

    #[test]
    fn decr_returns_post_operation_value() {
        let t = Telemetry::new();
        saturating_add(&t.total_connections, 10);
        assert_eq!(saturating_sub(&t.total_connections, 4), 6);
    }

    #[test]
    fn decr_clamps_at_zero() {
        let t = Telemetry::new();
        assert_eq!(saturating_sub(&t.total_connections, 5), 0);
    }

    #[test]
    fn incr_clamps_at_usize_max() {
        let t = Telemetry::new();
        t.total_connections
            .store(usize::MAX - 1, Ordering::Relaxed);
        assert_eq!(saturating_add(&t.total_connections, 5), usize::MAX);
    }

    #[test]
    fn reset_all_zeroes_every_field() {
        let t = Telemetry::new();
        saturating_add(&t.total_connections, 1);
        saturating_add(&t.total_clients, 1);
        saturating_add(&t.total_values_compressed, 1);
        saturating_add(&t.total_values_decompressed, 1);
        saturating_add(&t.total_original_bytes, 1);
        saturating_add(&t.total_bytes_compressed, 1);
        saturating_add(&t.total_bytes_decompressed, 1);
        saturating_add(&t.compression_skipped_count, 1);
        saturating_add(&t.subscription_out_of_sync_count, 1);
        t.subscription_last_sync_timestamp
            .store(42, Ordering::Relaxed);

        t.reset_all();

        assert_eq!(t.total_connections.load(Ordering::Relaxed), 0);
        assert_eq!(t.total_clients.load(Ordering::Relaxed), 0);
        assert_eq!(t.total_values_compressed.load(Ordering::Relaxed), 0);
        assert_eq!(t.total_values_decompressed.load(Ordering::Relaxed), 0);
        assert_eq!(t.total_original_bytes.load(Ordering::Relaxed), 0);
        assert_eq!(t.total_bytes_compressed.load(Ordering::Relaxed), 0);
        assert_eq!(t.total_bytes_decompressed.load(Ordering::Relaxed), 0);
        assert_eq!(t.compression_skipped_count.load(Ordering::Relaxed), 0);
        assert_eq!(t.subscription_out_of_sync_count.load(Ordering::Relaxed), 0);
        assert_eq!(t.subscription_last_sync_timestamp.load(Ordering::Relaxed), 0);
    }

    #[test]
    fn concurrent_increments_are_never_lost() {
        let t = Telemetry::new();
        std::thread::scope(|scope| {
            for _ in 0..8 {
                scope.spawn(|| {
                    for _ in 0..10_000 {
                        saturating_add(&t.total_connections, 1);
                    }
                });
            }
        });
        assert_eq!(t.total_connections.load(Ordering::Relaxed), 80_000);
    }

    #[test]
    fn concurrent_incr_decr_interleaving_returns_to_base() {
        let t = Telemetry::new();
        const BASE: usize = 100_000;
        t.total_connections.store(BASE, Ordering::Relaxed);

        std::thread::scope(|scope| {
            for _ in 0..4 {
                scope.spawn(|| {
                    for _ in 0..10_000 {
                        saturating_add(&t.total_connections, 1);
                    }
                });
            }
            for _ in 0..4 {
                scope.spawn(|| {
                    for _ in 0..10_000 {
                        saturating_sub(&t.total_connections, 1);
                    }
                });
            }
        });
        assert_eq!(t.total_connections.load(Ordering::Relaxed), BASE);
    }

    #[test]
    fn serialize_yields_all_zero_fields_with_expected_keys() {
        let value = serde_json::to_value(Telemetry::new()).unwrap();
        let expected = serde_json::json!({
            "total_connections": 0,
            "total_clients": 0,
            "total_values_compressed": 0,
            "total_values_decompressed": 0,
            "total_original_bytes": 0,
            "total_bytes_compressed": 0,
            "total_bytes_decompressed": 0,
            "compression_skipped_count": 0,
            "subscription_out_of_sync_count": 0,
            "subscription_last_sync_timestamp": 0,
        });
        assert_eq!(value, expected);
    }
}
