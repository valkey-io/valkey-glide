// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::Telemetry;
use glide_ffi::{Statistics, get_statistics};
use std::mem;

#[test]
fn statistics_layout_matches_cffi() {
    assert_eq!(mem::size_of::<Statistics>(), 80);
    let offsets = [
        mem::offset_of!(Statistics, total_connections),
        mem::offset_of!(Statistics, total_clients),
        mem::offset_of!(Statistics, total_values_compressed),
        mem::offset_of!(Statistics, total_values_decompressed),
        mem::offset_of!(Statistics, total_original_bytes),
        mem::offset_of!(Statistics, total_bytes_compressed),
        mem::offset_of!(Statistics, total_bytes_decompressed),
        mem::offset_of!(Statistics, compression_skipped_count),
        mem::offset_of!(Statistics, subscription_out_of_sync_count),
        mem::offset_of!(Statistics, subscription_last_sync_timestamp),
    ];
    assert_eq!(offsets, [0, 8, 16, 24, 32, 40, 48, 56, 64, 72]);
}

#[test]
#[cfg(target_pointer_width = "64")]
fn statistics_preserve_values_above_u32_max() {
    let value = u32::MAX as usize + 123;
    let connections = Telemetry::incr_total_connections(value);
    let clients = Telemetry::incr_total_clients(value);
    let compressed = Telemetry::incr_total_values_compressed(value);
    let decompressed = Telemetry::incr_total_values_decompressed(value);
    let original_bytes = Telemetry::incr_total_original_bytes(value);
    let compressed_bytes = Telemetry::incr_total_bytes_compressed(value);
    let decompressed_bytes = Telemetry::incr_total_bytes_decompressed(value);
    let skipped = Telemetry::incr_compression_skipped_count(value);
    let timestamp = 1_791_000_000_123;
    Telemetry::update_subscription_last_sync_timestamp(timestamp);

    let statistics = get_statistics();
    assert_eq!(statistics.total_connections, connections as u64);
    assert_eq!(statistics.total_clients, clients as u64);
    assert_eq!(statistics.total_values_compressed, compressed as u64);
    assert_eq!(statistics.total_values_decompressed, decompressed as u64);
    assert_eq!(statistics.total_original_bytes, original_bytes as u64);
    assert_eq!(statistics.total_bytes_compressed, compressed_bytes as u64);
    assert_eq!(
        statistics.total_bytes_decompressed,
        decompressed_bytes as u64
    );
    assert_eq!(statistics.compression_skipped_count, skipped as u64);
    assert_eq!(statistics.subscription_last_sync_timestamp, timestamp);
}
