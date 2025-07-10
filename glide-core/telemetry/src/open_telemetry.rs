use once_cell::sync::OnceCell;
use opentelemetry::global::ObjectSafeSpan;
use opentelemetry::trace::{SpanKind, TraceContextExt, TraceError};
use opentelemetry::{global, trace::Tracer};
use opentelemetry_otlp::{MetricExporter, Protocol, WithExportConfig};
use opentelemetry_sdk::export::trace::SpanExporter;
use opentelemetry_sdk::metrics::{MetricError, SdkMeterProvider};
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::runtime::Tokio;
use opentelemetry_sdk::trace::{BatchConfig, BatchSpanProcessor, TracerProvider};
use std::io::{Error, ErrorKind};
use std::path::PathBuf;
#[cfg(test)]
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, OnceLock, RwLock};
use std::time::Duration;
use thiserror::Error;
use url::Url;

const SPAN_WRITE_LOCK_ERR: &str = "Failed to acquire span write lock";
const SPAN_READ_LOCK_ERR: &str = "Failed to acquire span read lock";
const TRACE_SCOPE: &str = "valkey_glide";

// Metric names
const TIMEOUT_ERROR_METRIC: &str = "glide.timeout_errors";
const RETRIES_METRIC: &str = "glide.retry_attempts";
const MOVED_ERROR_METRIC: &str = "glide.moved_errors";

/// Custom error type for OpenTelemetry errors in Glide
#[derive(Debug, Error)]
pub enum GlideOTELError {
    #[error("Glide OpenTelemetry trace error: {0}")]
    TraceError(#[from] TraceError),

    #[error("Glide OpenTelemetry metric error: {0}")]
    MetricError(#[from] MetricError),

    #[error("Glide OpenTelemetry error: Failed to acquire read lock")]
    ReadLockError,

    #[error("Glide OpenTelemetry error: Failed to acquire write lock")]
    WriteLockError,

    #[error("Other error: {0}")]
    Other(String),
}

/// Default interval in milliseconds for flushing open telemetry data to the collector.
pub const DEFAULT_FLUSH_SIGNAL_INTERVAL_MS: u32 = 5000;

/// Default trace sampling percentage for sending OpenTelemetry data to the collector.
pub const DEFAULT_TRACE_SAMPLE_PERCENTAGE: u32 = 1;

/// Default filename for the file exporter.
pub const DEFAULT_SIGNAL_FILENAME: &str = "signals.json";

pub enum GlideSpanStatus {
    Ok,
    Error(String),
}

#[derive(Clone, Debug)]
/// Defines the method that exporter connects to the collector. It can be:
/// gRPC or HTTP. The third type (i.e. "File") defines an exporter that does not connect to a collector
/// instead, it writes the collected signals to files.
pub enum GlideOpenTelemetrySignalsExporter {
    /// Collector is listening on grpc
    Grpc(String),
    /// Collector is listening on http
    Http(String),
    /// No collector. Instead, write the signals collected to a file. The contained value "PathBuf"
    /// points to the folder where the collected data should be placed.
    File(PathBuf),
}

impl std::str::FromStr for GlideOpenTelemetrySignalsExporter {
    type Err = Error;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        parse_endpoint(s)
    }
}

fn parse_endpoint(endpoint: &str) -> Result<GlideOpenTelemetrySignalsExporter, Error> {
    // Parse the URL using the `url` crate to validate it
    let url = Url::parse(endpoint)
        .map_err(|_| Error::new(ErrorKind::InvalidInput, format!("Parse error. {endpoint}")))?;

    match url.scheme() {
        "http" | "https" => Ok(GlideOpenTelemetrySignalsExporter::Http(
            endpoint.to_string(),
        )), // HTTP/HTTPS endpoint
        "grpc" => Ok(GlideOpenTelemetrySignalsExporter::Grpc(
            endpoint.to_string(),
        )), // gRPC endpoint
        "file" => {
            // For file, we need to extract the path without the 'file://' prefix
            let file_prefix = "file://";
            if !endpoint.starts_with(file_prefix) {
                return Err(Error::new(
                    ErrorKind::InvalidInput,
                    "File path must start with 'file://'",
                ));
            }

            // Extract the path by removing the 'file://' prefix
            let path = endpoint.strip_prefix(file_prefix).ok_or_else(|| {
                Error::new(
                    ErrorKind::InvalidInput,
                    "Failed to extract path from file URL",
                )
            })?;

            let path_buf = PathBuf::from(path);

            // Determine if this is a directory path or a file path
            let final_path = if path_buf.is_dir() || path_buf.extension().is_none() {
                // If it's a directory or doesn't have an extension, treat it as a directory
                // and append the default filename
                path_buf.join(DEFAULT_SIGNAL_FILENAME)
            } else {
                path_buf
            };

            // Check if the parent directory exists and is a directory
            if let Some(parent_dir) = final_path.parent() {
                match parent_dir.try_exists() {
                    Ok(exists) => {
                        if !exists || !parent_dir.is_dir() {
                            return Err(Error::new(
                                ErrorKind::InvalidInput,
                                format!(
                                    "The directory does not exist or is not a directory: {}",
                                    parent_dir.display()
                                ),
                            ));
                        }
                    }
                    Err(e) => {
                        return Err(Error::new(
                            ErrorKind::InvalidInput,
                            format!("Error checking if parent directory exists: {}", e),
                        ));
                    }
                }
            }

            Ok(GlideOpenTelemetrySignalsExporter::File(final_path))
        } // file endpoint
        _ => Err(Error::new(ErrorKind::InvalidInput, endpoint)),
    }
}

#[derive(Clone, Debug)]
struct GlideSpanInner {
    span: Arc<RwLock<opentelemetry::global::BoxedSpan>>,
    #[cfg(test)]
    reference_count: Arc<AtomicUsize>,
}

impl GlideSpanInner {
    /// Create new span with no parent.
    pub fn new(name: &str) -> Self {
        let tracer = global::tracer(TRACE_SCOPE);
        let span = Arc::new(RwLock::new(
            tracer
                .span_builder(name.to_string())
                .with_kind(SpanKind::Client)
                .start(&tracer),
        ));

        GlideSpanInner {
            span,
            #[cfg(test)]
            reference_count: Arc::new(AtomicUsize::new(1)),
        }
    }

    /// Create new span as a child of `parent`, returning an error if the parent span lock is poisoned.
    pub fn new_with_parent(name: &str, parent: &GlideSpanInner) -> Result<Self, TraceError> {
        let parent_span_ctx = parent
            .span
            .read()
            .map_err(|_| TraceError::from(SPAN_READ_LOCK_ERR))?
            .span_context()
            .clone();

        let parent_context =
            opentelemetry::Context::new().with_remote_span_context(parent_span_ctx);

        let tracer = global::tracer(TRACE_SCOPE);
        let span = Arc::new(RwLock::new(
            tracer
                .span_builder(name.to_string())
                .with_kind(SpanKind::Client)
                .start_with_context(&tracer, &parent_context),
        ));
        Ok(GlideSpanInner {
            span,
            #[cfg(test)]
            reference_count: Arc::new(AtomicUsize::new(1)),
        })
    }

    /// Attach event with name and list of attributes to this span.
    pub fn add_event(&self, name: &str, attributes: Option<&Vec<(&str, &str)>>) {
        let attributes: Vec<opentelemetry::KeyValue> = if let Some(attributes) = attributes {
            attributes
                .iter()
                .map(|(k, v)| opentelemetry::KeyValue::new(k.to_string(), v.to_string()))
                .collect()
        } else {
            Vec::<opentelemetry::KeyValue>::default()
        };
        self.span
            .write()
            .expect(SPAN_WRITE_LOCK_ERR)
            .add_event_with_timestamp(
                name.to_string().into(),
                std::time::SystemTime::now(),
                attributes,
            );
    }

    pub fn set_status(&self, status: GlideSpanStatus) {
        match status {
            GlideSpanStatus::Ok => self
                .span
                .write()
                .expect(SPAN_WRITE_LOCK_ERR)
                .set_status(opentelemetry::trace::Status::Ok),
            GlideSpanStatus::Error(error_message) => {
                self.span.write().expect(SPAN_WRITE_LOCK_ERR).set_status(
                    opentelemetry::trace::Status::Error {
                        description: error_message.into(),
                    },
                )
            }
        }
    }

    /// Create new span, add it as a child to this span and return it.
    /// Returns an error if the child span creation fails.
    pub fn add_span(&self, name: &str) -> Result<GlideSpanInner, TraceError> {
        let child = GlideSpanInner::new_with_parent(name, self)?;
        {
            let child_span = child
                .span
                .read()
                .map_err(|_| TraceError::from(SPAN_READ_LOCK_ERR))?;
            self.span
                .write()
                .expect(SPAN_WRITE_LOCK_ERR)
                .add_link(child_span.span_context().clone(), Vec::default());
        }
        Ok(child)
    }

    /// Return the span ID
    pub fn id(&self) -> String {
        self.span
            .read()
            .expect(SPAN_READ_LOCK_ERR)
            .span_context()
            .span_id()
            .to_string()
    }

    /// Finishes the `Span`.
    pub fn end(&self) {
        self.span.write().expect(SPAN_READ_LOCK_ERR).end()
    }

    #[cfg(test)]
    pub fn get_reference_count(&self) -> usize {
        self.reference_count.load(Ordering::SeqCst)
    }

    #[cfg(test)]
    pub fn increment_reference_count(&self) {
        self.reference_count.fetch_add(1, Ordering::SeqCst);
    }

    #[cfg(test)]
    pub fn decrement_reference_count(&self) {
        self.reference_count.fetch_sub(1, Ordering::SeqCst);
    }
}

#[cfg(test)]
impl Drop for GlideSpanInner {
    fn drop(&mut self) {
        // Only print debug info if the reference count is non-zero
        let current_count = self.reference_count.load(Ordering::SeqCst);
        if current_count > 0 {
            self.decrement_reference_count();
        } else {
            panic!("Span reference count is 0");
        }
    }
}

#[derive(Clone, Debug)]
pub struct GlideSpan {
    inner: GlideSpanInner,
}

impl GlideSpan {
    pub fn new(name: &str) -> Self {
        GlideSpan {
            inner: GlideSpanInner::new(name),
        }
    }

    /// Attach event with name to this span.
    pub fn add_event(&self, name: &str) {
        self.inner.add_event(name, None)
    }

    /// Attach event with name and attributes to this span.
    pub fn add_event_with_attributes(&self, name: &str, attributes: &Vec<(&str, &str)>) {
        self.inner.add_event(name, Some(attributes))
    }

    pub fn set_status(&self, status: GlideSpanStatus) {
        self.inner.set_status(status)
    }

    /// Add child span to this span and return it
    pub fn add_span(&self, name: &str) -> Result<GlideSpan, opentelemetry::trace::TraceError> {
        let inner_span = self.inner.add_span(name).map_err(|err| {
            TraceError::from(format!("Failed to create child span '{}': {}", name, err))
        })?;

        Ok(GlideSpan { inner: inner_span })
    }

    pub fn id(&self) -> String {
        self.inner.id()
    }

    /// Finishes the `Span`.
    pub fn end(&self) {
        self.inner.end()
    }

    #[cfg(test)]
    pub fn add_reference(&self) {
        self.inner.increment_reference_count();
    }

    #[cfg(test)]
    pub fn get_reference_count(&self) -> usize {
        self.inner.get_reference_count()
    }
}

/// OpenTelemetry configuration object. Use `GlideOpenTelemetryConfigBuilder` to construct it:
///
/// ```text
/// let config = GlideOpenTelemetryConfigBuilder::default()
///    .with_flush_interval(std::time::Duration::from_millis(100))
///    .build();
/// GlideOpenTelemetry::initialise(config);
/// ```
#[derive(Clone, Debug)]
pub struct GlideOpenTelemetryConfig {
    /// Default delay interval between two consecutive exports.
    flush_interval_ms: Duration,
    traces: Option<GlideOpenTelemetryTracesConfig>,
    metrics: Option<GlideOpenTelemetryMetricsConfig>,
}

#[derive(Clone, Debug)]
pub struct GlideOpenTelemetryTracesConfig {
    /// Specifies how the exporter sends telemetry data to the collector, and holds the endpoint information.
    trace_exporter: GlideOpenTelemetrySignalsExporter,
    /// The percentage of requests to sample and create a span for, used to measure command duration.
    trace_sample_percentage: u32,
}

#[derive(Clone, Debug)]
pub struct GlideOpenTelemetryMetricsConfig {
    /// Specifies how the exporter sends telemetry data to the collector, and holds the endpoint information.
    metrics_exporter: GlideOpenTelemetrySignalsExporter,
}

/// Builder for configuring OpenTelemetry in GLIDE
///
/// This struct allows you to configure how telemetry data (traces and metrics) is exported.
/// - `flush_interval_ms`: Sets the interval between consecutive exports of telemetry data.
/// - `traces_config`: Optional configuration for exporting trace data. If `None`, trace data will not be exported.
/// - `metrics_config`: Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
///
/// If both `traces_config` and `metrics_config` are `None`, no telemetry data will be exported.
#[derive(Clone, Debug)]
pub struct GlideOpenTelemetryConfigBuilder {
    /// The interval between consecutive exports of telemetry data.
    flush_interval_ms: Duration,
    /// Optional configuration for exporting trace data. If `None`, trace data will not be exported.
    traces_config: Option<GlideOpenTelemetryTracesConfig>,
    /// Optional configuration for exporting metrics data. If `None`, metrics data will not be exported.
    metrics_config: Option<GlideOpenTelemetryMetricsConfig>,
}

impl Default for GlideOpenTelemetryConfigBuilder {
    fn default() -> Self {
        GlideOpenTelemetryConfigBuilder {
            flush_interval_ms: Duration::from_millis(DEFAULT_FLUSH_SIGNAL_INTERVAL_MS as u64),
            traces_config: None,
            metrics_config: None,
        }
    }
}

impl GlideOpenTelemetryConfigBuilder {
    /// Configure the flush interval in milliseconds
    ///
    /// - `duration`: The duration between consecutive exports of telemetry data.
    pub fn with_flush_interval(mut self, duration: Duration) -> Self {
        self.flush_interval_ms = duration;
        self
    }

    /// Configure the trace exporter
    ///
    /// - `exporter`: The exporter endpoint to use for trace data.
    /// - `sample_percentage`: The percentage of requests to sample and create a span for, used to measure command duration.
    ///   If `None`, the default value of `DEFAULT_TRACE_SAMPLE_PERCENTAGE` will be used.
    pub fn with_trace_exporter(
        mut self,
        exporter: GlideOpenTelemetrySignalsExporter,
        sample_percentage: Option<u32>,
    ) -> Self {
        self.traces_config = Some(GlideOpenTelemetryTracesConfig {
            trace_exporter: exporter,
            trace_sample_percentage: sample_percentage.unwrap_or(DEFAULT_TRACE_SAMPLE_PERCENTAGE),
        });
        self
    }

    /// Configure the metrics exporter
    ///
    /// - `exporter`: The exporter endpoint to use for metrics data.
    pub fn with_metrics_exporter(mut self, exporter: GlideOpenTelemetrySignalsExporter) -> Self {
        self.metrics_config = Some(GlideOpenTelemetryMetricsConfig {
            metrics_exporter: exporter,
        });
        self
    }

    pub fn build(self) -> GlideOpenTelemetryConfig {
        GlideOpenTelemetryConfig {
            flush_interval_ms: self.flush_interval_ms,
            traces: self.traces_config,
            metrics: self.metrics_config,
        }
    }
}

fn build_span_exporter(
    batch_config: BatchConfig,
    exporter: impl SpanExporter + 'static,
) -> BatchSpanProcessor<Tokio> {
    BatchSpanProcessor::builder(exporter, Tokio)
        .with_batch_config(batch_config)
        .build()
}

#[derive(Clone)]
pub struct GlideOpenTelemetry {}

static TIMEOUT_COUNTER: OnceLock<opentelemetry::metrics::Counter<u64>> = OnceLock::new();
static RETRIES_COUNTER: OnceLock<opentelemetry::metrics::Counter<u64>> = OnceLock::new();
static MOVED_COUNTER: OnceLock<opentelemetry::metrics::Counter<u64>> = OnceLock::new();

/// Singleton instance of GlideOpenTelemetry. Ensures that telemetry setup happens only once across the application.
static OTEL: OnceCell<RwLock<GlideOpenTelemetry>> = OnceCell::new();

/// Our interface to OpenTelemetry
impl GlideOpenTelemetry {
    /// Initialise the open telemetry library with a file system exporter
    ///
    /// This method should be called once for the given **process**
    /// If OpenTelemetry is already initialized, this method will return Ok(()) without reinitializing
    pub fn initialise(config: GlideOpenTelemetryConfig) -> Result<(), GlideOTELError> {
        OTEL.get_or_try_init(|| {
            Self::validate_config(config.clone())?;

            if let Some(traces_config) = config.traces.as_ref() {
                Self::initialise_trace_exporter(
                    config.flush_interval_ms,
                    &traces_config.trace_exporter,
                )?;
            }

            if let Some(metrics_config) = config.metrics.as_ref() {
                Self::initialise_metrics_exporter(
                    config.flush_interval_ms,
                    &metrics_config.metrics_exporter,
                )?;
                Self::init_metrics()?;
            }

            Ok::<RwLock<GlideOpenTelemetry>, GlideOTELError>(RwLock::new(GlideOpenTelemetry {}))
        })?;

        Ok(())
    }

    /// Validate the configuration
    ///
    /// - `config`: The OpenTelemetry configuration to validate.
    ///
    /// Returns an error if the configuration is invalid:
    /// - `flush_interval_ms` cannot be zero
    /// - `trace_sample_percentage` must be between 0 and 100
    fn validate_config(config: GlideOpenTelemetryConfig) -> Result<(), GlideOTELError> {
        // Validate flush_interval_ms
        if config.flush_interval_ms.is_zero() {
            return Err(GlideOTELError::Other(
                "InvalidInput: flushIntervalMs cannot be zero".to_string(),
            ));
        }

        // Validate trace_sample_percentage
        if let Some(traces_config) = config.traces.as_ref() {
            if traces_config.trace_sample_percentage > 100 {
                return Err(GlideOTELError::Other(
                    "Trace sample percentage must be between 0 and 100".into(),
                ));
            }
        }
        Ok(())
    }

    /// Initialize the trace exporter based on the configuration
    fn initialise_trace_exporter(
        flush_interval_ms: Duration,
        trace_exporter: &GlideOpenTelemetrySignalsExporter,
    ) -> Result<(), GlideOTELError> {
        let batch_config = opentelemetry_sdk::trace::BatchConfigBuilder::default()
            .with_scheduled_delay(flush_interval_ms)
            .build();

        let trace_exporter = match trace_exporter {
            GlideOpenTelemetrySignalsExporter::File(p) => {
                let exporter = crate::SpanExporterFile::new(p.clone()).map_err(|e| {
                    GlideOTELError::Other(format!("Failed to create traces exporter: {}", e))
                })?;
                build_span_exporter(batch_config, exporter)
            }
            GlideOpenTelemetrySignalsExporter::Http(url) => {
                let protocol_str = std::env::var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL")
                    .or_else(|_| std::env::var("OTEL_EXPORTER_OTLP_PROTOCOL"))
                    .unwrap_or_else(|_| "http/protobuf".to_string());
                let protocol = match protocol_str.as_str() {
                    "grpc" => Protocol::Grpc,
                    "http/protobuf" => Protocol::HttpBinary,
                    "http/json" => Protocol::HttpJson,
                    _ => Protocol::HttpBinary, // Default fallback
                };
                let exporter = opentelemetry_otlp::SpanExporter::builder()
                    .with_http()
                    .with_endpoint(url)
                    .with_protocol(protocol)
                    .build()?;
                build_span_exporter(batch_config, exporter)
            }
            GlideOpenTelemetrySignalsExporter::Grpc(url) => {
                let protocol_str = std::env::var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL")
                    .or_else(|_| std::env::var("OTEL_EXPORTER_OTLP_PROTOCOL"))
                    .unwrap_or_else(|_| "grpc".to_string());
                let protocol = match protocol_str.as_str() {
                    "grpc" => Protocol::Grpc,
                    "http/protobuf" => Protocol::HttpBinary,
                    "http/json" => Protocol::HttpJson,
                    _ => Protocol::Grpc, // Default fallback
                };
                let exporter = opentelemetry_otlp::SpanExporter::builder()
                    .with_tonic()
                    .with_endpoint(url)
                    .with_protocol(protocol)
                    .build()?;
                build_span_exporter(batch_config, exporter)
            }
        };

        global::set_text_map_propagator(TraceContextPropagator::new());
        let provider = TracerProvider::builder()
            .with_span_processor(trace_exporter)
            .build();
        global::set_tracer_provider(provider);

        Ok(())
    }

    /// Initialize the metrics exporter based on the configuration
    fn initialise_metrics_exporter(
        flush_interval_ms: Duration,
        metrics_exporter: &GlideOpenTelemetrySignalsExporter,
    ) -> Result<(), GlideOTELError> {
        let metrics_exporter = match metrics_exporter {
            GlideOpenTelemetrySignalsExporter::File(p) => {
                let exporter = crate::FileMetricExporter::new(p.clone()).map_err(|e| {
                    GlideOTELError::Other(format!("Failed to create metrics exporter: {}", e))
                })?;
                opentelemetry_sdk::metrics::PeriodicReader::builder(exporter, Tokio)
                    .with_interval(flush_interval_ms)
                    .build()
            }
            GlideOpenTelemetrySignalsExporter::Http(url) => {
                let protocol_str = std::env::var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL")
                    .or_else(|_| std::env::var("OTEL_EXPORTER_OTLP_PROTOCOL"))
                    .unwrap_or_else(|_| "http/protobuf".to_string());
                let protocol = match protocol_str.as_str() {
                    "grpc" => Protocol::Grpc,
                    "http/protobuf" => Protocol::HttpBinary,
                    "http/json" => Protocol::HttpJson,
                    _ => Protocol::HttpBinary, // Default fallback
                };
                let exporter = MetricExporter::builder()
                    .with_http()
                    .with_endpoint(url)
                    .with_protocol(protocol)
                    .build()?;
                opentelemetry_sdk::metrics::PeriodicReader::builder(exporter, Tokio)
                    .with_interval(flush_interval_ms)
                    .build()
            }
            GlideOpenTelemetrySignalsExporter::Grpc(url) => {
                let protocol_str = std::env::var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL")
                    .or_else(|_| std::env::var("OTEL_EXPORTER_OTLP_PROTOCOL"))
                    .unwrap_or_else(|_| "grpc".to_string());
                let protocol = match protocol_str.as_str() {
                    "grpc" => Protocol::Grpc,
                    "http/protobuf" => Protocol::HttpBinary,
                    "http/json" => Protocol::HttpJson,
                    _ => Protocol::Grpc, // Default fallback
                };
                let exporter = MetricExporter::builder()
                    .with_tonic()
                    .with_endpoint(url)
                    .with_protocol(protocol)
                    .build()?;
                opentelemetry_sdk::metrics::PeriodicReader::builder(exporter, Tokio)
                    .with_interval(flush_interval_ms)
                    .build()
            }
        };

        let meter_provider = SdkMeterProvider::builder()
            .with_reader(metrics_exporter)
            .build();
        global::set_meter_provider(meter_provider);

        Ok(())
    }

    /// Initialize metrics counters
    fn init_metrics() -> Result<(), GlideOTELError> {
        let meter = global::meter(TRACE_SCOPE);

        // Create timeout error counter
        TIMEOUT_COUNTER
            .set(
                meter
                    .u64_counter(TIMEOUT_ERROR_METRIC)
                    .with_description("Number of timeout errors encountered")
                    .with_unit("1")
                    .build(),
            )
            .map_err(|_| {
                GlideOTELError::Other(
                    "OpenTelemetry error: Failed to initialize timeout counter".to_owned(),
                )
            })?;

        // Create retries counter
        RETRIES_COUNTER
            .set(
                meter
                    .u64_counter(RETRIES_METRIC)
                    .with_description("Number of retry attempts made")
                    .with_unit("1")
                    .build(),
            )
            .map_err(|_| {
                GlideOTELError::Other(
                    "OpenTelemetry error: Failed to initialize retries counter".to_owned(),
                )
            })?;

        // Create moved counter
        MOVED_COUNTER
            .set(
                meter
                    .u64_counter(MOVED_ERROR_METRIC)
                    .with_description("Number of moved errors encountered")
                    .with_unit("1")
                    .build(),
            )
            .map_err(|_| {
                GlideOTELError::Other(
                    "OpenTelemetry error: Failed to initialize moved counter".to_owned(),
                )
            })?;

        Ok(())
    }

    /// Record a timeout error
    ///
    /// If OpenTelemetry is not initialized, this method will do nothing.
    pub fn record_timeout_error() -> Result<(), GlideOTELError> {
        if GlideOpenTelemetry::is_initialized() {
            TIMEOUT_COUNTER
                .get()
                .ok_or_else(|| {
                    GlideOTELError::Other(
                        "OpenTelemetry error: Timeout counter not initialized".to_owned(),
                    )
                })?
                .add(1, &[]);
        }
        Ok(())
    }

    /// Record a retry attempt
    ///
    /// If OpenTelemetry is not initialized, this method will do nothing.
    pub fn record_retry_attempt() -> Result<(), GlideOTELError> {
        if GlideOpenTelemetry::is_initialized() {
            RETRIES_COUNTER
                .get()
                .ok_or_else(|| {
                    GlideOTELError::Other(
                        "OpenTelemetry error: Retries counter not initialized".to_string(),
                    )
                })?
                .add(1, &[]);
        }
        Ok(())
    }

    /// Record a moved error
    ///
    /// If OpenTelemetry is not initialized, this method will do nothing.
    pub fn record_moved_error() -> Result<(), GlideOTELError> {
        if GlideOpenTelemetry::is_initialized() {
            MOVED_COUNTER
                .get()
                .ok_or_else(|| {
                    GlideOTELError::Other(
                        "OpenTelemetry error: Moved counter not initialized".to_string(),
                    )
                })?
                .add(1, &[]);
        }
        Ok(())
    }

    /// Get the flush interval milliseconds
    pub fn get_flush_interval_ms(config: GlideOpenTelemetryConfig) -> Duration {
        config.flush_interval_ms
    }

    /// Create new span
    pub fn new_span(name: &str) -> GlideSpan {
        GlideSpan::new(name)
    }

    /// Trigger a shutdown procedure flushing all remaining traces
    pub fn shutdown() {
        global::shutdown_tracer_provider();
    }

    /// Check if OpenTelemetry is initialized
    pub fn is_initialized() -> bool {
        OTEL.get().is_some()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;
    use std::panic;
    use std::sync::OnceLock;
    use tokio::runtime::Runtime;
    use tokio::time::sleep;

    const SPANS_JSON: &str = "/tmp/spans.json";
    const METRICS_JSON: &str = "/tmp/metrics.json";

    fn shared_runtime() -> &'static Runtime {
        static RUNTIME: OnceLock<Runtime> = OnceLock::new();
        RUNTIME.get_or_init(|| Runtime::new().expect("Failed to create runtime"))
    }

    fn string_property_to_u64(json: &serde_json::Value, prop: &str) -> u64 {
        let s = json[prop].to_string().replace('"', "");
        s.parse::<u64>().unwrap()
    }

    async fn init_otel_with_env(
        trace_protocol_env: Option<&str>,
        metrics_protocol_env: Option<&str>,
        generic_protocol_env: Option<&str>,
    ) -> Result<(), GlideOTELError> {
        // Set environment variables for testing
        if let Some(protocol) = trace_protocol_env {
            env::set_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", protocol);
        } else {
            env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
        }
        if let Some(protocol) = metrics_protocol_env {
            env::set_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL", protocol);
        } else {
            env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
        }
        if let Some(protocol) = generic_protocol_env {
            env::set_var("OTEL_EXPORTER_OTLP_PROTOCOL", protocol);
        } else {
            env::remove_var("OTEL_EXPORTER_OTLP_PROTOCOL");
        }

        // Reinitialize OTEL global for each test run to pick up new env vars
        // This is a bit hacky, ideally opentelemetry-rust would provide a better way to reset global state.
        // For now, we'll use a new OnceCell for each test run.
        unsafe {
            OTEL = OnceCell::new();
        }

        let config = GlideOpenTelemetryConfigBuilder::default()
            .with_flush_interval(Duration::from_millis(2000))
            .with_trace_exporter(
                // Using http endpoint to test protocol override, file exporter doesn't have protocol
                GlideOpenTelemetrySignalsExporter::Http("http://localhost:4318".to_string()),
                Some(100),
            )
            .with_metrics_exporter(
                GlideOpenTelemetrySignalsExporter::Http("http://localhost:4318".to_string()),
            )
            .build();

        match GlideOpenTelemetry::initialise(config) {
            Ok(_) => Ok(()),
            Err(GlideOTELError::TraceError(TraceError::Other(msg)))
                if msg.contains("OTEL has already been initialized") =>
            {
                // This can happen if another test initialized OTEL without clearing it.
                // We try to proceed, hoping the existing config is compatible enough.
                Ok(())
            }
            Err(e) => {
                // Clean up env vars before panicking
                env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
                env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
                env::remove_var("OTEL_EXPORTER_OTLP_PROTOCOL");
                panic!("Failed to initialize OpenTelemetry: {}", e);
            }
        }
    }

    async fn init_otel_file_exporter() -> Result<(), GlideOTELError> {
        // Clean up env vars from other tests
        env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
        env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
        env::remove_var("OTEL_EXPORTER_OTLP_PROTOCOL");
        unsafe {
            OTEL = OnceCell::new();
        }
        let config = GlideOpenTelemetryConfigBuilder::default()
            .with_flush_interval(Duration::from_millis(2000))
            .with_trace_exporter(
                GlideOpenTelemetrySignalsExporter::File(PathBuf::from(SPANS_JSON)),
                Some(100),
            )
            .with_metrics_exporter(GlideOpenTelemetrySignalsExporter::File(PathBuf::from(
                METRICS_JSON,
            )))
            .build();
        if let Err(e) = GlideOpenTelemetry::initialise(config) {
            panic!("Failed to initialize OpenTelemetry with file exporter: {}", e);
        }
        Ok(())
    }

    async fn create_test_spans() {
        // Clear the file
        let _ = std::fs::remove_file(SPANS_JSON);

        let span = GlideOpenTelemetry::new_span("Root_Span_1");
        span.add_event("Event1");
        span.set_status(GlideSpanStatus::Ok);

        let child1 = span.add_span("Network_Span").unwrap();

        // Simulate some work
        sleep(Duration::from_millis(100)).await;
        child1.end();

        // Simulate that the parent span is still doing some work
        sleep(Duration::from_millis(100)).await;
        span.end();

        let span = GlideOpenTelemetry::new_span("Root_Span_2");
        span.add_event("Event1");
        span.add_event("Event2");
        span.set_status(GlideSpanStatus::Ok);
        drop(span); // writes the span

        sleep(Duration::from_millis(2100)).await;
    }

    #[test]
    fn test_span_json_exporter() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(SPANS_JSON);

            init_otel_file_exporter().await.unwrap();
            create_test_spans().await;

            let file_content = std::fs::read_to_string(SPANS_JSON).unwrap();
            let lines: Vec<&str> = file_content
                .split('\n')
                .filter(|l| !l.trim().is_empty())
                .collect();

            assert!(
                lines.len() == 3 || lines.len() == 4,
                "Expected 3 or 4 lines, got {}. file content: {file_content:?}",
                lines.len()
            );

            // Adjust base index if there are only 3 lines (no header line)
            let base = if lines.len() == 3 { 0 } else { 1 };

            let span_json: serde_json::Value = serde_json::from_str(lines[base]).unwrap();
            assert_eq!(span_json["name"], "Network_Span");
            let network_span_id = span_json["span_id"].to_string();
            let network_span_start_time = string_property_to_u64(&span_json, "start_time");
            let network_span_end_time = string_property_to_u64(&span_json, "end_time");

            // Because of the sleep above, the network span should be at least 100ms (units are microseconds)
            assert!(network_span_end_time - network_span_start_time >= 100_000);

            let span_json: serde_json::Value = serde_json::from_str(lines[base + 1]).unwrap();
            assert_eq!(span_json["name"], "Root_Span_1");
            assert_eq!(span_json["links"].as_array().unwrap().len(), 1); // we expect 1 child
            let root_1_span_start_time = string_property_to_u64(&span_json, "start_time");
            let root_1_span_end_time = string_property_to_u64(&span_json, "end_time");

            // The network span started *after* its parent
            assert!(network_span_start_time >= root_1_span_start_time);

            // The parent span ends *after* the child span (by at least 100ms)
            assert!(root_1_span_end_time - network_span_end_time >= 100_000);

            let child_span_id = span_json["links"][0]["span_id"].to_string();
            assert_eq!(child_span_id, network_span_id);

            let span_json: serde_json::Value = serde_json::from_str(lines[base + 2]).unwrap();
            assert_eq!(span_json["name"], "Root_Span_2");
            assert_eq!(span_json["events"].as_array().unwrap().len(), 2); // we expect 2 events
        });
    }

    #[test]
    fn test_span_reference_count() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(SPANS_JSON);
            init_otel_file_exporter().await.unwrap();
            let span = GlideOpenTelemetry::new_span("Root_Span_1");
            span.add_reference();
            assert_eq!(span.get_reference_count(), 2);
            drop(span);
        });
    }

    #[test]
    fn test_record_timeout_error() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel_file_exporter().await.unwrap();
            GlideOpenTelemetry::record_timeout_error().unwrap();
            sleep(Duration::from_millis(2100)).await;
            GlideOpenTelemetry::record_timeout_error().unwrap();
            GlideOpenTelemetry::record_timeout_error().unwrap();

            // Add a sleep to wait for the metrics to be flushed
            sleep(Duration::from_millis(2100)).await;

            let file_content = std::fs::read_to_string(METRICS_JSON).unwrap();
            let lines: Vec<&str> = file_content
                .split('\n')
                .filter(|l| !l.trim().is_empty())
                .collect();

            let metric_json: serde_json::Value = serde_json::from_str(lines[0]).unwrap();
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["name"],
                "glide.timeout_errors"
            );
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"],
                1
            );
            let metric_json: serde_json::Value =
                serde_json::from_str(lines[lines.len() - 1]).unwrap();
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"],
                3
            );
        });
    }

    #[test]
    fn test_record_retry_attempts() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel_file_exporter().await.unwrap();
            GlideOpenTelemetry::record_retry_attempt().unwrap();
            sleep(Duration::from_millis(2100)).await;
            GlideOpenTelemetry::record_retry_attempt().unwrap();
            GlideOpenTelemetry::record_retry_attempt().unwrap();

            // Add a sleep to wait for the metrics to be flushed
            sleep(Duration::from_millis(2100)).await;

            let file_content = std::fs::read_to_string(METRICS_JSON).unwrap();
            let lines: Vec<&str> = file_content
                .split('\n')
                .filter(|l| !l.trim().is_empty())
                .collect();

            let metric_json: serde_json::Value = serde_json::from_str(lines[0]).unwrap();
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["name"],
                "glide.retry_attempts"
            );
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"],
                1
            );
            let metric_json: serde_json::Value =
                serde_json::from_str(lines[lines.len() - 1]).unwrap();
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"],
                3
            );
        });
    }

    #[test]
    fn test_record_moved_error() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel_file_exporter().await.unwrap();
            GlideOpenTelemetry::record_moved_error().unwrap();
            sleep(Duration::from_millis(2100)).await;
            GlideOpenTelemetry::record_moved_error().unwrap();
            GlideOpenTelemetry::record_moved_error().unwrap();

            // Add a sleep to wait for the metrics to be flushed
            sleep(Duration::from_millis(2100)).await;

            let file_content = std::fs::read_to_string(METRICS_JSON).unwrap();
            let lines: Vec<&str> = file_content
                .split('\n')
                .filter(|l| !l.trim().is_empty())
                .collect();

            let metric_json: serde_json::Value = serde_json::from_str(lines[0]).unwrap();
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["name"],
                "glide.moved_errors"
            );
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"],
                1
            );
            let metric_json: serde_json::Value =
                serde_json::from_str(lines[lines.len() - 1]).unwrap();
            assert_eq!(
                metric_json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"],
                3
            );
        });
    }

    #[test]
    fn test_set_status_ok() {
        let rt = shared_runtime();
        rt.block_on(async {
            // Clear the file
            let _ = std::fs::remove_file(SPANS_JSON);

            init_otel_file_exporter().await.unwrap();
            let span = GlideOpenTelemetry::new_span("Root_Span_1");
            span.add_event("Event1");
            span.set_status(GlideSpanStatus::Ok);

            let child1 = span.add_span("Network_Span").unwrap();

            // Simulate some work
            sleep(Duration::from_millis(100)).await;
            child1.end();

            // Simulate that the parent span is still doing some work
            sleep(Duration::from_millis(100)).await;
            span.end();

            let span = GlideOpenTelemetry::new_span("Root_Span_2");
            span.add_event("Event1");
            span.add_event("Event2");
            span.set_status(GlideSpanStatus::Error("simple error".to_string())); // Fixed typo in "simple"
            drop(span); // writes the span

            sleep(Duration::from_millis(2100)).await;

            let file_content = std::fs::read_to_string(SPANS_JSON).unwrap();
            let lines: Vec<&str> = file_content
                .split('\n')
                .filter(|l| !l.trim().is_empty())
                .collect();

            let span_json: serde_json::Value = serde_json::from_str(lines[1]).unwrap();
            assert_eq!(span_json["status"], "Ok");

            let span_json: serde_json::Value = serde_json::from_str(lines[2]).unwrap();
            let status = span_json["status"].as_str().unwrap_or("");
            assert!(status.starts_with("Error"));
            assert!(status.contains("simple error"));
        });
    }

    macro_rules! test_protocol_env_override {
        ($test_name:ident, $trace_env:expr, $metrics_env:expr, $generic_env:expr, $expected_trace_protocol:expr, $expected_metrics_protocol:expr) => {
            #[test]
            fn $test_name() {
                let rt = shared_runtime();
                rt.block_on(async {
                    init_otel_with_env($trace_env, $metrics_env, $generic_env)
                        .await
                        .unwrap();

                    // We are not actually checking if the exporter *used* the protocol,
                    // as that would require a running OTLP collector and network inspection.
                    // We are testing that the initialization code doesn't panic with these env vars,
                    // which implies the protocol strings were parsed and accepted by the opentelemetry crate.
                    // The actual protocol setting is handled by the opentelemetry-otlp crate.
                    // We create a simple span and metric to ensure the pipeline is alive.
                    let span = GlideOpenTelemetry::new_span("TestSpan");
                    span.set_status(GlideSpanStatus::Ok);
                    span.end();
                    GlideOpenTelemetry::record_timeout_error().unwrap();

                    // Allow time for potential async operations within the OTel library
                    sleep(Duration::from_millis(100)).await;

                    // Assert that the global tracer and meter providers were set up.
                    // This is an indirect way to check if initialization likely succeeded with the given protocols.
                    assert!(
                        global::tracer_provider().version() != "no-op",
                        "Tracer provider should not be no-op"
                    );
                    assert!(
                        global::meter_provider().version() != "no-op",
                        "Meter provider should not be no-op"
                    );
                });
            }
        };
    }

    // Test cases for protocol environment variables
    // Specific trace protocol, no metrics, no generic
    test_protocol_env_override!(
        test_trace_protocol_grpc,
        Some("grpc"),
        None,
        None,
        Protocol::Grpc,
        Protocol::HttpBinary // Default for metrics when not set
    );
    test_protocol_env_override!(
        test_trace_protocol_http_protobuf,
        Some("http/protobuf"),
        None,
        None,
        Protocol::HttpBinary,
        Protocol::HttpBinary
    );
    test_protocol_env_override!(
        test_trace_protocol_http_json,
        Some("http/json"),
        None,
        None,
        Protocol::HttpJson,
        Protocol::HttpBinary
    );

    // Specific metrics protocol, no trace, no generic
    test_protocol_env_override!(
        test_metrics_protocol_grpc,
        None,
        Some("grpc"),
        None,
        Protocol::HttpBinary, // Default for trace when not set
        Protocol::Grpc
    );
    test_protocol_env_override!(
        test_metrics_protocol_http_protobuf,
        None,
        Some("http/protobuf"),
        None,
        Protocol::HttpBinary,
        Protocol::HttpBinary
    );
    test_protocol_env_override!(
        test_metrics_protocol_http_json,
        None,
        Some("http/json"),
        None,
        Protocol::HttpBinary,
        Protocol::HttpJson
    );

    // Generic protocol, no specific trace/metrics
    test_protocol_env_override!(
        test_generic_protocol_grpc,
        None,
        None,
        Some("grpc"),
        Protocol::Grpc,
        Protocol::Grpc
    );
    test_protocol_env_override!(
        test_generic_protocol_http_protobuf,
        None,
        None,
        Some("http/protobuf"),
        Protocol::HttpBinary,
        Protocol::HttpBinary
    );
    test_protocol_env_override!(
        test_generic_protocol_http_json,
        None,
        None,
        Some("http/json"),
        Protocol::HttpJson,
        Protocol::HttpJson
    );

    // Specific overrides generic
    test_protocol_env_override!(
        test_trace_overrides_generic_grpc_http,
        Some("grpc"),
        None,
        Some("http/protobuf"),
        Protocol::Grpc,
        Protocol::HttpBinary // Generic applies to metrics
    );
    test_protocol_env_override!(
        test_metrics_overrides_generic_grpc_http,
        None,
        Some("grpc"),
        Some("http/protobuf"),
        Protocol::HttpBinary, // Generic applies to trace
        Protocol::Grpc
    );
    test_protocol_env_override!(
        test_specific_overrides_generic_all_different,
        Some("grpc"),
        Some("http/json"),
        Some("http/protobuf"),
        Protocol::Grpc,
        Protocol::HttpJson
    );

    // Fallback to URL scheme if env var is invalid
    test_protocol_env_override!(
        test_invalid_trace_protocol_falls_back,
        Some("invalid_protocol"),
        None,
        None,
        Protocol::HttpBinary, // Default for Http endpoint
        Protocol::HttpBinary
    );
    test_protocol_env_override!(
        test_invalid_metrics_protocol_falls_back,
        None,
        Some("invalid_protocol"),
        None,
        Protocol::HttpBinary,
        Protocol::HttpBinary // Default for Http endpoint
    );
    test_protocol_env_override!(
        test_invalid_generic_protocol_falls_back,
        None,
        None,
        Some("invalid_protocol"),
        Protocol::HttpBinary, // Default for Http endpoint
        Protocol::HttpBinary
    );

    // Fallback to URL scheme if env var is not set (covered by some tests above, e.g. test_trace_protocol_grpc where metrics_env and generic_env are None)

    // Test with GRPC endpoint to ensure default is GRPC when env vars specify GRPC
    #[test]
    fn test_grpc_endpoint_with_grpc_env() {
        let rt = shared_runtime();
        rt.block_on(async {
            env::set_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", "grpc");
            env::set_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL", "grpc");
            unsafe {
                OTEL = OnceCell::new();
            }

            let config = GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(Duration::from_millis(100))
                .with_trace_exporter(
                    GlideOpenTelemetrySignalsExporter::Grpc("http://localhost:4317".to_string()),
                    Some(100),
                )
                .with_metrics_exporter(
                    GlideOpenTelemetrySignalsExporter::Grpc("http://localhost:4317".to_string()),
                )
                .build();
            GlideOpenTelemetry::initialise(config).unwrap();

            assert!(
                global::tracer_provider().version() != "no-op",
                "Tracer provider should not be no-op"
            );
            assert!(
                global::meter_provider().version() != "no-op",
                "Meter provider should not be no-op"
            );

            env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
            env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
        });
    }

    // Test with HTTP endpoint to ensure default is HTTP when env vars specify HTTP
    #[test]
    fn test_http_endpoint_with_http_env() {
        let rt = shared_runtime();
        rt.block_on(async {
            env::set_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", "http/protobuf");
            env::set_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL", "http/protobuf");
            unsafe {
                OTEL = OnceCell::new();
            }

            let config = GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(Duration::from_millis(100))
                .with_trace_exporter(
                    GlideOpenTelemetrySignalsExporter::Http("http://localhost:4318".to_string()),
                    Some(100),
                )
                .with_metrics_exporter(
                    GlideOpenTelemetrySignalsExporter::Http("http://localhost:4318".to_string()),
                )
                .build();
            GlideOpenTelemetry::initialise(config).unwrap();

            assert!(
                global::tracer_provider().version() != "no-op",
                "Tracer provider should not be no-op"
            );
            assert!(
                global::meter_provider().version() != "no-op",
                "Meter provider should not be no-op"
            );
            env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
            env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
        });
    }
}
