// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! OpenTelemetry integration.
//!
//! Enables exporting **traces** (per-command spans, sampled) and **metrics**
//! (connection/retry/timeout counters, …) from the underlying `glide-core` to an
//! OpenTelemetry collector or a local file. Mirrors the OpenTelemetry surface of
//! the other GLIDE wrappers (Python/Node/Go/Java).
//!
//! Telemetry is a **process-global, one-shot** setting: call [`init`] exactly
//! once, early in your program, before creating clients. Calls after a
//! successful [`init`] are ignored. A failed [`init`] can be retried.
//!
//! ```rust,no_run
//! use std::time::Duration;
//! use glide::telemetry::{self, OpenTelemetryConfig, TelemetryExporter};
//!
//! # #[tokio::main]
//! # async fn main() -> glide::ValkeyResult<()> {
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
//! # Runtime
//! [`init`] runs the HTTP and gRPC exporters' background batch workers on
//! GLIDE's own Tokio runtime, so it can be called from synchronous code or from
//! within any Tokio runtime, and the exporters keep running if the caller's
//! runtime shuts down.

use crate::ValkeyResult;
use crate::error::GlideError;
use glide_core::{
    DEFAULT_TRACE_SAMPLE_PERCENTAGE, GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder,
    GlideOpenTelemetrySignalsExporter,
};
use std::path::PathBuf;
use std::time::Duration;

/// The default trace sampling percentage used when none is supplied
/// (see [`OpenTelemetryConfig::builder`]).
pub const DEFAULT_TRACE_SAMPLE_PERCENT: u32 = DEFAULT_TRACE_SAMPLE_PERCENTAGE;

// TODO #7701: Remove `TelemetryExporter` in favor of endpoint strings on `TracesConfig` /
// `MetricsConfig`, matching the other GLIDE clients.
/// Where a telemetry signal (traces or metrics) is exported to.
///
/// Mirrors `GlideOpenTelemetrySignalsExporter`.
#[derive(Debug, Clone, PartialEq, Eq)]
#[non_exhaustive]
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

    pub(crate) fn to_core(&self) -> GlideOpenTelemetrySignalsExporter {
        match self {
            TelemetryExporter::Grpc(s) => GlideOpenTelemetrySignalsExporter::Grpc(s.clone()),
            TelemetryExporter::Http(s) => GlideOpenTelemetrySignalsExporter::Http(s.clone()),
            TelemetryExporter::File(p) => GlideOpenTelemetrySignalsExporter::File(p.clone()),
        }
    }
}

/// An OpenTelemetry configuration.
///
/// At least one of a trace or a metrics exporter must be configured.
#[derive(Clone)]
pub struct OpenTelemetryConfig {
    inner: GlideOpenTelemetryConfigBuilder,
    // TODO #7706: Remove once glide-telemetry's `validate_config` rejects a config
    // with no exporter.
    has_exporter: bool,
}

impl OpenTelemetryConfig {
    /// Start building an OpenTelemetry configuration.
    pub fn builder() -> OpenTelemetryConfigBuilder {
        OpenTelemetryConfigBuilder {
            inner: GlideOpenTelemetryConfigBuilder::default(),
            has_exporter: false,
        }
    }
}

/// Builder for [`OpenTelemetryConfig`]. Mirrors `GlideOpenTelemetryConfigBuilder`.
#[derive(Clone)]
pub struct OpenTelemetryConfigBuilder {
    inner: GlideOpenTelemetryConfigBuilder,
    // TODO #7706: Remove once glide-telemetry's `validate_config` rejects a config
    // with no exporter.
    has_exporter: bool,
}

impl OpenTelemetryConfigBuilder {
    /// Sets the interval for flushing telemetry data to the collector.
    ///
    /// `interval` must be positive – [`init`] rejects a zero interval.
    #[must_use]
    pub fn with_flush_interval(mut self, interval: Duration) -> Self {
        self.inner = self.inner.with_flush_interval(interval);
        self
    }

    /// Sets the traces exporter and sample percentage.
    ///
    /// `sample_percentage` must be a valid percentage – [`init`] rejects a
    /// percentage greater than 100.
    #[must_use]
    // TODO #7701: Replace with `with_traces(TracesConfig)`.
    pub fn with_trace_exporter(
        mut self,
        exporter: TelemetryExporter,
        sample_percentage: Option<u32>,
    ) -> Self {
        self.inner = self
            .inner
            .with_trace_exporter(exporter.to_core(), sample_percentage);
        self.has_exporter = true;
        self
    }

    /// Sets the metrics exporter.
    #[must_use]
    // TODO #7701: Replace with `with_metrics(MetricsConfig)`.
    pub fn with_metrics_exporter(mut self, exporter: TelemetryExporter) -> Self {
        self.inner = self.inner.with_metrics_exporter(exporter.to_core());
        self.has_exporter = true;
        self
    }

    /// Finish building the configuration.
    pub fn build(self) -> OpenTelemetryConfig {
        OpenTelemetryConfig {
            inner: self.inner,
            has_exporter: self.has_exporter,
        }
    }
}

/// Initialise process-global OpenTelemetry with the given configuration.
///
/// Idempotent: the first successful call wins and later calls are no-ops
/// (returning `Ok`).
///
/// # Errors
/// Returns [`GlideError::Configuration`] if the configuration is invalid
/// or an exporter fails to initialise.
pub fn init(config: OpenTelemetryConfig) -> ValkeyResult<()> {
    // TODO #7706: Drop once glide-telemetry's `validate_config` rejects a config with no exporter.
    if !config.has_exporter {
        return Err(GlideError::Configuration(
            "At least one of traces or metrics must be provided for OpenTelemetry configuration"
                .to_string(),
        ));
    }

    // The HTTP and gRPC exporters spawn their batch workers with `tokio::spawn`. Run them on
    // GLIDE's runtime, as the FFI layer does, so they outlive the caller's runtime.
    let glide_runtime = glide_runtime()?;
    let _runtime_guard = glide_runtime.runtime.enter();

    initialise(config)
}

/// Returns GLIDE's runtime, initializing it if needed.
///
/// `get_or_init_runtime` blocks on a channel the first time it runs, which panics inside a
/// Tokio runtime, so it's called on a separate thread.
fn glide_runtime() -> ValkeyResult<&'static glide_core::client::GlideRt> {
    std::thread::spawn(glide_core::client::get_or_init_runtime)
        .join()
        .map_err(|_| GlideError::Configuration("Failed to get or init runtime".to_string()))?
        .map_err(|e| GlideError::Configuration(format!("Failed to get or init runtime: {e}")))
}

/// Hands the configuration to glide-core, with a Tokio runtime already in scope.
fn initialise(config: OpenTelemetryConfig) -> ValkeyResult<()> {
    GlideOpenTelemetry::initialise(config.inner.build())
        .map_err(|e| GlideError::Configuration(format!("OpenTelemetry init failed: {e}")))
}

/// Whether OpenTelemetry has been successfully initialised in this process.
pub fn is_initialized() -> bool {
    GlideOpenTelemetry::is_initialized()
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
        let core: GlideOpenTelemetrySignalsExporter = TelemetryExporter::grpc("g").to_core();
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
    async fn init_rejects_config_without_exporter() {
        let config = OpenTelemetryConfig::builder()
            .with_flush_interval(Duration::from_millis(100))
            .build();
        let err = init(config).expect_err("a config with no exporter must be rejected");
        assert!(matches!(err, GlideError::Configuration(_)));
        assert!(!is_initialized());
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
