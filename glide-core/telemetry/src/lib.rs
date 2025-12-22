use lazy_static::lazy_static;
use serde::Serialize;
use std::sync::RwLock as StdRwLock;
mod metrics_exporter_file;
mod open_telemetry;
mod span_exporter_file;

pub use metrics_exporter_file::FileMetricExporter;
pub use open_telemetry::*;
pub use span_exporter_file::SpanExporterFile;

#[derive(Default, Serialize)]
#[allow(dead_code)]
pub struct Telemetry {
    /// Total number of connections opened to Valkey
    total_connections: usize,
    /// Total number of GLIDE clients
    total_clients: usize,
    /// Total number of values compressed
    total_values_compressed: usize,
    /// Total number of values decompressed
    total_values_decompressed: usize,
    /// Total original bytes before compression
    total_original_bytes: usize,
    /// Total bytes after compression
    total_bytes_compressed: usize,
    /// Total bytes after decompression
    total_bytes_decompressed: usize,
    /// Number of times compression was skipped
    compression_skipped_count: usize,
    /// Number of times subscriptions were detected as out of sync
    subscription_out_of_sync_count: usize,
    /// Unix timestamp (in milliseconds) of the last time subscriptions were in sync
    subscription_last_sync_timestamp: u64,
}

lazy_static! {
    static ref TELEMETRY: StdRwLock<Telemetry> = StdRwLock::<Telemetry>::default();
}

const MUTEX_WRITE_ERR: &str = "Failed to obtain write lock for mutex. Poisoned mutex";
const MUTEX_READ_ERR: &str = "Failed to obtain read lock for mutex. Poisoned mutex";

impl Telemetry {
    /// Increment the total number of connections by `incr_by`
    /// Return the number of total connections after the increment
    pub fn incr_total_connections(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_connections = t.total_connections.saturating_add(incr_by);
        t.total_connections
    }

    /// Decrease the total number of connections by `decr_by`
    /// Return the number of total connections after the decrease
    pub fn decr_total_connections(decr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_connections = t.total_connections.saturating_sub(decr_by);
        t.total_connections
    }

    /// Increment the total number of clients by `incr_by`
    /// Return the number of total clients after the increment
    pub fn incr_total_clients(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_clients = t.total_clients.saturating_add(incr_by);
        t.total_clients
    }

    /// Decrease the total number of clients by `decr_by`
    /// Return the number of total clients after the decrease
    pub fn decr_total_clients(decr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_clients = t.total_clients.saturating_sub(decr_by);
        t.total_clients
    }

    /// Return the number of active connections
    pub fn total_connections() -> usize {
        TELEMETRY.read().expect(MUTEX_READ_ERR).total_connections
    }

    /// Return the number of active clients
    pub fn total_clients() -> usize {
        TELEMETRY.read().expect(MUTEX_READ_ERR).total_clients
    }

    /// Increment the total number of values compressed
    pub fn incr_total_values_compressed(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_values_compressed = t.total_values_compressed.saturating_add(incr_by);
        t.total_values_compressed
    }

    /// Return the total number of values compressed
    pub fn total_values_compressed() -> usize {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .total_values_compressed
    }

    /// Increment the total number of values decompressed
    pub fn incr_total_values_decompressed(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_values_decompressed = t.total_values_decompressed.saturating_add(incr_by);
        t.total_values_decompressed
    }

    /// Return the total number of values decompressed
    pub fn total_values_decompressed() -> usize {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .total_values_decompressed
    }

    /// Increment the total original bytes before compression
    pub fn incr_total_original_bytes(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_original_bytes = t.total_original_bytes.saturating_add(incr_by);
        t.total_original_bytes
    }

    /// Return the total original bytes before compression
    pub fn total_original_bytes() -> usize {
        TELEMETRY.read().expect(MUTEX_READ_ERR).total_original_bytes
    }

    /// Increment the total bytes after compression
    pub fn incr_total_bytes_compressed(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_bytes_compressed = t.total_bytes_compressed.saturating_add(incr_by);
        t.total_bytes_compressed
    }

    /// Return the total bytes after compression
    pub fn total_bytes_compressed() -> usize {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .total_bytes_compressed
    }

    /// Increment the total bytes after decompression
    pub fn incr_total_bytes_decompressed(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.total_bytes_decompressed = t.total_bytes_decompressed.saturating_add(incr_by);
        t.total_bytes_decompressed
    }

    /// Return the total bytes after decompression
    pub fn total_bytes_decompressed() -> usize {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .total_bytes_decompressed
    }

    /// Increment the compression skipped count
    pub fn incr_compression_skipped_count(incr_by: usize) -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.compression_skipped_count = t.compression_skipped_count.saturating_add(incr_by);
        t.compression_skipped_count
    }

    /// Return the compression skipped count
    pub fn compression_skipped_count() -> usize {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .compression_skipped_count
    }
    /// Increment the subscription out of sync count
    /// Return the new count after increment
    pub fn incr_subscription_out_of_sync() -> usize {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.subscription_out_of_sync_count = t.subscription_out_of_sync_count.saturating_add(1);
        t.subscription_out_of_sync_count
    }

    /// Get the current subscription out of sync count
    pub fn subscription_out_of_sync_count() -> usize {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .subscription_out_of_sync_count
    }

    /// Update the subscription last sync timestamp
    /// Return the new timestamp
    pub fn update_subscription_last_sync_timestamp(timestamp: u64) -> u64 {
        let mut t = TELEMETRY.write().expect(MUTEX_WRITE_ERR);
        t.subscription_last_sync_timestamp = timestamp;
        t.subscription_last_sync_timestamp
    }

    /// Get the subscription last sync timestamp
    pub fn subscription_last_sync_timestamp() -> u64 {
        TELEMETRY
            .read()
            .expect(MUTEX_READ_ERR)
            .subscription_last_sync_timestamp
    }

    /// Reset the telemetry collected thus far
    pub fn reset() {
        *TELEMETRY.write().expect(MUTEX_WRITE_ERR) = Telemetry::default();
    }
}
