// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! OpenTelemetry integration.
//!
//! Enables exporting **traces** (per-command spans, sampled) and **metrics**
//! (connection/retry/timeout counters, …) from the underlying `glide-core` to an
//! OpenTelemetry collector or a local file. Mirrors the OpenTelemetry surface of
//! the other GLIDE wrappers (Python/Node/Go/Java).
//!
//! Telemetry is a **process-global, one-shot** setting: call [`init`] exactly
//! once, early in your program, before creating clients. Subsequent calls are
//! ignored (the first configuration wins).
//!
//! ```rust,no_run
//! use std::time::Duration;
//! use glide::telemetry::{self, OpenTelemetryConfig, TelemetryExporter};
//!
//! # #[tokio::main]
//! # async fn main() -> glide::Result<()> {
//! // Export traces to a collector over gRPC, sampling 5% of commands.
//! let config = OpenTelemetryConfig::builder()
//!     .with_flush_interval(Duration::from_millis(1000))
//!     .with_trace_exporter(
//!         TelemetryExporter::grpc("http://localhost:4317"),
//!         Some(5),
//!     )
//!     .build();
//! telemetry::init(config)?;
//! assert!(telemetry::is_initialized());
//! # Ok(())
//! # }
//! ```
//!
//! # Runtime requirement
//! [`init`] installs background batch exporters on the Tokio runtime, so it must
//! be called from **within a Tokio runtime context** (e.g. inside
//! `#[tokio::main]` or a `Runtime::block_on`).

use crate::error::{GlideError, Result};
use glide_core::{
    DEFAULT_TRACE_SAMPLE_PERCENTAGE, GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder,
    GlideOpenTelemetrySignalsExporter,
};
use std::path::PathBuf;
use std::time::Duration;

/// The default trace sampling percentage used when none is supplied
/// (see [`OpenTelemetryConfig::builder`]).
pub const DEFAULT_TRACE_SAMPLE_PERCENT: u32 = DEFAULT_TRACE_SAMPLE_PERCENTAGE;

/// Where a telemetry signal (traces or metrics) is exported to.
///
/// Mirrors `GlideOpenTelemetrySignalsExporter`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TelemetryExporter {
    /// Send to a collector over OTLP/gRPC at the given endpoint
    /// (e.g. `http://localhost:4317`).
    Grpc(String),
    /// Send to a collector over OTLP/HTTP at the given endpoint
    /// (e.g. `http://localhost:4318`).
    Http(String),
    /// Write signals to a file/folder at the given path (no collector needed).
    File(PathBuf),
}

impl TelemetryExporter {
    /// Convenience constructor for a gRPC exporter endpoint.
    pub fn grpc(endpoint: impl Into<String>) -> Self {
        TelemetryExporter::Grpc(endpoint.into())
    }

    /// Convenience constructor for an HTTP exporter endpoint.
    pub fn http(endpoint: impl Into<String>) -> Self {
        TelemetryExporter::Http(endpoint.into())
    }

    /// Convenience constructor for a file exporter path.
    pub fn file(path: impl Into<PathBuf>) -> Self {
        TelemetryExporter::File(path.into())
    }
}

impl From<TelemetryExporter> for GlideOpenTelemetrySignalsExporter {
    fn from(e: TelemetryExporter) -> Self {
        match e {
            TelemetryExporter::Grpc(s) => GlideOpenTelemetrySignalsExporter::Grpc(s),
            TelemetryExporter::Http(s) => GlideOpenTelemetrySignalsExporter::Http(s),
            TelemetryExporter::File(p) => GlideOpenTelemetrySignalsExporter::File(p),
        }
    }
}

/// A fully-built OpenTelemetry configuration, ready to pass to [`init`].
///
/// Construct one with [`OpenTelemetryConfig::builder`]. If neither a trace nor a
/// metrics exporter is configured, initialisation is a no-op.
#[derive(Clone)]
pub struct OpenTelemetryConfig {
    inner: GlideOpenTelemetryConfigBuilder,
}

impl OpenTelemetryConfig {
    /// Start building an OpenTelemetry configuration.
    pub fn builder() -> OpenTelemetryConfigBuilder {
        OpenTelemetryConfigBuilder {
            inner: GlideOpenTelemetryConfigBuilder::default(),
        }
    }
}

/// Builder for [`OpenTelemetryConfig`]. Mirrors `GlideOpenTelemetryConfigBuilder`.
#[derive(Clone)]
pub struct OpenTelemetryConfigBuilder {
    inner: GlideOpenTelemetryConfigBuilder,
}

impl OpenTelemetryConfigBuilder {
    /// Set the interval between consecutive exports of telemetry data.
    ///
    /// Must be non-zero; a zero interval is rejected by [`init`].
    #[must_use]
    pub fn with_flush_interval(mut self, interval: Duration) -> Self {
        self.inner = self.inner.with_flush_interval(interval);
        self
    }

    /// Enable trace export to `exporter`, sampling `sample_percentage` percent of
    /// commands (0–100). `None` uses [`DEFAULT_TRACE_SAMPLE_PERCENT`]. A
    /// percentage above 100 is rejected by [`init`].
    #[must_use]
    pub fn with_trace_exporter(
        mut self,
        exporter: TelemetryExporter,
        sample_percentage: Option<u32>,
    ) -> Self {
        self.inner = self
            .inner
            .with_trace_exporter(exporter.into(), sample_percentage);
        self
    }

    /// Enable metrics export to `exporter`.
    #[must_use]
    pub fn with_metrics_exporter(mut self, exporter: TelemetryExporter) -> Self {
        self.inner = self.inner.with_metrics_exporter(exporter.into());
        self
    }

    /// Finish building the configuration.
    pub fn build(self) -> OpenTelemetryConfig {
        OpenTelemetryConfig { inner: self.inner }
    }
}

/// Initialise process-global OpenTelemetry with the given configuration.
///
/// Idempotent: the first successful call wins and later calls are no-ops
/// (returning `Ok`). Must be called from within a Tokio runtime (see the
/// [module docs](self)).
///
/// # Errors
/// Returns [`GlideError::Configuration`] if the configuration is invalid (zero
/// flush interval, trace sample percentage > 100) or an exporter fails to
/// initialise.
pub fn init(config: OpenTelemetryConfig) -> Result<()> {
    GlideOpenTelemetry::initialise(config.inner.build())
        .map_err(|e| GlideError::Configuration(format!("OpenTelemetry init failed: {e}")))
}

/// Whether OpenTelemetry has been successfully initialised in this process.
pub fn is_initialized() -> bool {
    GlideOpenTelemetry::is_initialized()
}

/// Shut down OpenTelemetry, flushing any pending signals. Safe to call even if
/// telemetry was never initialised.
pub fn shutdown() {
    GlideOpenTelemetry::shutdown();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exporter_constructors_and_mapping() {
        assert_eq!(
            TelemetryExporter::grpc("http://h:4317"),
            TelemetryExporter::Grpc("http://h:4317".into())
        );
        assert_eq!(
            TelemetryExporter::http("http://h:4318"),
            TelemetryExporter::Http("http://h:4318".into())
        );
        assert_eq!(
            TelemetryExporter::file("/tmp/sig"),
            TelemetryExporter::File(PathBuf::from("/tmp/sig"))
        );
        // Lowering to the core type preserves the variant + payload.
        let core: GlideOpenTelemetrySignalsExporter = TelemetryExporter::grpc("g").into();
        assert!(matches!(core, GlideOpenTelemetrySignalsExporter::Grpc(s) if s == "g"));
    }

    #[test]
    fn default_sample_percent_matches_core() {
        assert_eq!(
            DEFAULT_TRACE_SAMPLE_PERCENT,
            DEFAULT_TRACE_SAMPLE_PERCENTAGE
        );
    }

    #[tokio::test]
    async fn init_rejects_zero_flush_interval() {
        let config = OpenTelemetryConfig::builder()
            .with_flush_interval(Duration::from_millis(0))
            .with_trace_exporter(TelemetryExporter::file("/tmp/otel-zero"), Some(1))
            .build();
        let err = init(config).expect_err("zero flush interval must be rejected");
        assert!(matches!(err, GlideError::Configuration(_)));
    }

    #[tokio::test]
    async fn init_rejects_sample_percentage_over_100() {
        let config = OpenTelemetryConfig::builder()
            .with_flush_interval(Duration::from_millis(100))
            .with_trace_exporter(TelemetryExporter::file("/tmp/otel-bad-sample"), Some(101))
            .build();
        let err = init(config).expect_err("sample percentage > 100 must be rejected");
        assert!(matches!(err, GlideError::Configuration(_)));
    }

    // NOTE: a successful `init` is process-global and one-shot, so it cannot be
    // combined with the rejection tests above in the same process without making
    // them order-dependent. The happy path (file exporter) + idempotency +
    // is_initialized are covered by the dedicated integration test
    // `tests/it_telemetry.rs`, which runs in its own process.
}
