// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::fmt;
use std::time::Duration;

pub struct Error;

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}

#[derive(Clone)]
pub struct GlideSpan;

#[derive(Debug)]
pub struct TraceError;

impl fmt::Display for TraceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "trace error")
    }
}

impl GlideSpan {
    pub fn new_with_remote_context(
        _name: &str,
        _trace_id_hex: &str,
        _span_id_hex: &str,
        _trace_flags: u8,
        _trace_state: Option<&str>,
    ) -> Result<GlideSpan, TraceError> {
        Ok(GlideSpan)
    }

    pub fn add_span(&self, _name: &str) -> Result<GlideSpan, TraceError> {
        Ok(GlideSpan)
    }

    pub fn end(&self) {}
}

pub struct GlideOpenTelemetrySignalsExporter;

impl GlideOpenTelemetrySignalsExporter {
    pub fn new() -> Self {
        GlideOpenTelemetrySignalsExporter
    }
}

impl std::str::FromStr for GlideOpenTelemetrySignalsExporter {
    type Err = Error;
    fn from_str(_s: &str) -> Result<Self, Self::Err> {
        Ok(GlideOpenTelemetrySignalsExporter)
    }
}

pub struct GlideOpenTelemetryConfigBuilder;

pub struct GlideOpenTelemetryConfig;

impl Default for GlideOpenTelemetryConfigBuilder {
    fn default() -> Self {
        GlideOpenTelemetryConfigBuilder
    }
}

impl GlideOpenTelemetryConfigBuilder {
    pub fn build(self) -> GlideOpenTelemetryConfig {
        GlideOpenTelemetryConfig
    }

    pub fn with_flush_interval(self, _duration: Duration) -> Self {
        self
    }

    pub fn with_metrics_exporter(self, _exporter: GlideOpenTelemetrySignalsExporter) -> Self {
        self
    }

    pub fn with_trace_exporter(
        self,
        _exporter: GlideOpenTelemetrySignalsExporter,
        _sample_percentage: Option<u32>,
    ) -> Self {
        self
    }
}

pub struct GlideOpenTelemetry;

impl GlideOpenTelemetry {
    pub fn initialise(_config: GlideOpenTelemetryConfig) -> Result<(), GlideOTELError> {
        Ok(())
    }

    pub fn new_span(_name: &str) -> GlideSpan {
        GlideSpan
    }

    pub unsafe fn span_from_pointer(_ptr: u64) -> Result<GlideSpan, TraceError> {
        Ok(GlideSpan)
    }

    /// Mock of the shared create path: leak a span into a raw `u64` pointer via `Arc::into_raw`
    /// so the round-trip with `drop_span_ptr` stays sound under Miri.
    pub fn leak_span(span: GlideSpan) -> u64 {
        std::sync::Arc::into_raw(std::sync::Arc::new(span)) as u64
    }

    /// Mock of the shared drop path: reclaim a pointer produced by `leak_span` (`0` is a no-op).
    /// Mirrors the real helper's validation so obviously invalid pointers are rejected *before*
    /// any `Arc::from_raw`, matching the behaviour the miri tests exercise (e.g. `0xDEADBEEF`).
    pub unsafe fn drop_span_ptr(span_ptr: u64) -> Result<(), TraceError> {
        if span_ptr == 0 {
            return Ok(());
        }
        // Same bounds/alignment checks as the real is_span_pointer_valid.
        const MIN_VALID_ADDRESS: u64 = 0x1000;
        const MAX_VALID_ADDRESS: u64 = 0x7FFF_FFFF_FFFF_FFF8;
        if span_ptr % 8 != 0 || span_ptr < MIN_VALID_ADDRESS || span_ptr > MAX_VALID_ADDRESS {
            return Err(TraceError);
        }
        unsafe { drop(std::sync::Arc::from_raw(span_ptr as *const GlideSpan)) };
        Ok(())
    }
}

pub struct GlideOTELError;

impl fmt::Display for GlideOTELError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}

/// Mock Telemetry struct for Miri tests
pub struct Telemetry;

impl Telemetry {
    pub fn incr_total_connections(_incr_by: usize) -> usize { 0 }
    pub fn decr_total_connections(_decr_by: usize) -> usize { 0 }
    pub fn incr_total_clients(_incr_by: usize) -> usize { 0 }
    pub fn decr_total_clients(_decr_by: usize) -> usize { 0 }
    pub fn total_connections() -> usize { 0 }
    pub fn total_clients() -> usize { 0 }
    pub fn incr_total_values_compressed(_incr_by: usize) -> usize { 0 }
    pub fn total_values_compressed() -> usize { 0 }
    pub fn incr_total_values_decompressed(_incr_by: usize) -> usize { 0 }
    pub fn total_values_decompressed() -> usize { 0 }
    pub fn incr_total_original_bytes(_incr_by: usize) -> usize { 0 }
    pub fn total_original_bytes() -> usize { 0 }
    pub fn incr_total_bytes_compressed(_incr_by: usize) -> usize { 0 }
    pub fn total_bytes_compressed() -> usize { 0 }
    pub fn incr_total_bytes_decompressed(_incr_by: usize) -> usize { 0 }
    pub fn total_bytes_decompressed() -> usize { 0 }
    pub fn incr_compression_skipped_count(_incr_by: usize) -> usize { 0 }
    pub fn compression_skipped_count() -> usize { 0 }
    pub fn incr_subscription_out_of_sync() -> usize { 0 }
    pub fn subscription_out_of_sync_count() -> usize { 0 }
    pub fn update_subscription_last_sync_timestamp(_timestamp: u64) -> u64 { 0 }
    pub fn subscription_last_sync_timestamp() -> u64 { 0 }
    pub fn reset() {}
}

pub const DEFAULT_FLUSH_SIGNAL_INTERVAL_MS: u32 = 0;
