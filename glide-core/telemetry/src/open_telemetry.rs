use logger_core::log_warn;
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

/// Signal types supported when reading protocol configuration from the
/// environment.
#[derive(Clone, Copy)]
enum OtelSignal {
    Traces,
    Metrics,
}

/// Parse the OTLP protocol environment variables for the given signal. Returns
/// `Some(Protocol)` when the environment specifies a supported protocol,
/// otherwise `None`.
fn protocol_from_env(signal: OtelSignal) -> Option<Protocol> {
    fn parse(value: &str) -> Option<Protocol> {
        match value.to_ascii_lowercase().as_str() {
            "grpc" => Some(Protocol::Grpc),
            "http/protobuf" | "http/binary" | "http" | "http_proto" => Some(Protocol::HttpBinary),
            "http/json" => Some(Protocol::HttpJson),
            _ => None,
        }
    }

    let specific = match signal {
        OtelSignal::Traces => "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL",
        OtelSignal::Metrics => "OTEL_EXPORTER_OTLP_METRICS_PROTOCOL",
    };

    if let Ok(val) = std::env::var(specific) {
        parse(&val)
    } else if let Ok(val) = std::env::var("OTEL_EXPORTER_OTLP_PROTOCOL") {
        parse(&val)
    } else {
        None
    }
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
    /// Validate if a span pointer is valid
    ///
    /// # Arguments
    /// * `span_ptr` - The u64 span pointer to validate
    ///
    /// # Returns
    /// * `true` - If the span pointer is valid (non-zero and within reasonable bounds)
    /// * `false` - If the span pointer is invalid (zero, out of bounds, or potentially corrupted)
    ///
    /// # Safety
    /// This function performs basic validation but cannot guarantee the pointer points to valid memory.
    /// It only checks for obvious invalid values like null pointers and unreasonable addresses.
    pub unsafe fn is_span_pointer_valid(span_ptr: u64) -> bool {
        // Check for null pointer
        if span_ptr == 0 {
            logger_core::log_warn("OpenTelemetry", "Invalid span pointer - null pointer (0)");
            return false;
        }

        // Check for obviously invalid pointer values
        // Pointers should be aligned to at least 8 bytes on 64-bit systems
        if span_ptr % 8 != 0 {
            logger_core::log_warn(
                "OpenTelemetry",
                &format!(
                    "Invalid span pointer - misaligned pointer: 0x{:x}",
                    span_ptr
                ),
            );
            return false;
        }

        // Check for unreasonably small addresses (likely corrupted)
        // Valid heap addresses are typically much higher than this
        const MIN_VALID_ADDRESS: u64 = 0x1000; // 4KB, below this is likely invalid
        if span_ptr < MIN_VALID_ADDRESS {
            logger_core::log_warn(
                "OpenTelemetry",
                &format!("Invalid span pointer - address too low: 0x{:x}", span_ptr),
            );
            return false;
        }

        // Check for unreasonably high addresses (on 64-bit systems, user space is limited)
        // This is a conservative check for obviously corrupted pointers
        // On most 64-bit systems, user space is limited to the lower half of the address space
        const MAX_VALID_ADDRESS: u64 = 0x7FFF_FFFF_FFFF_FFF8; // Max user space on most 64-bit systems
        if span_ptr > MAX_VALID_ADDRESS {
            logger_core::log_warn(
                "OpenTelemetry",
                &format!("Invalid span pointer - address too high: 0x{:x}", span_ptr),
            );
            return false;
        }

        true
    }

    /// Convert a span pointer to a GlideSpan with validation
    ///
    /// # Arguments
    /// * `span_ptr` - The u64 span pointer to convert
    ///
    /// # Returns
    /// * `Ok(GlideSpan)` - If the pointer is valid and conversion succeeds
    /// * `Err(TraceError)` - If the pointer is invalid or conversion fails
    ///
    /// # Safety
    /// This function validates the pointer before attempting conversion, but still uses unsafe code
    pub unsafe fn span_from_pointer(span_ptr: u64) -> Result<GlideSpan, TraceError> {
        // First validate the pointer
        if !unsafe { Self::is_span_pointer_valid(span_ptr) } {
            return Err(TraceError::from(format!(
                "Invalid span pointer: 0x{:x} failed validation checks",
                span_ptr
            )));
        }

        // Attempt to convert the pointer safely
        // This follows the same pattern as get_unsafe_span_from_ptr in FFI layer
        let span = unsafe {
            // Increment strong count to prevent premature deallocation
            std::sync::Arc::increment_strong_count(span_ptr as *const GlideSpan);

            // Convert pointer back to Arc and clone the span
            (*std::sync::Arc::from_raw(span_ptr as *const GlideSpan)).clone()
        };

        Ok(span)
    }

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

        let env_protocol = protocol_from_env(OtelSignal::Traces);
        let trace_exporter = match trace_exporter {
            GlideOpenTelemetrySignalsExporter::File(p) => {
                let exporter = crate::SpanExporterFile::new(p.clone()).map_err(|e| {
                    GlideOTELError::Other(format!("Failed to create traces exporter: {}", e))
                })?;
                build_span_exporter(batch_config, exporter)
            }
            GlideOpenTelemetrySignalsExporter::Http(url) => {
                match env_protocol.unwrap_or(Protocol::HttpBinary) {
                    Protocol::Grpc => {
                        let exporter = opentelemetry_otlp::SpanExporter::builder()
                            .with_tonic()
                            .with_endpoint(url)
                            .with_protocol(Protocol::Grpc)
                            .build()?;
                        build_span_exporter(batch_config, exporter)
                    }
                    protocol => {
                        let exporter = opentelemetry_otlp::SpanExporter::builder()
                            .with_http()
                            .with_endpoint(url)
                            .with_protocol(protocol)
                            .build()?;
                        build_span_exporter(batch_config, exporter)
                    }
                }
            }
            GlideOpenTelemetrySignalsExporter::Grpc(url) => {
                let protocol = env_protocol.unwrap_or(Protocol::Grpc);
                if protocol != Protocol::Grpc {
                    log_warn(
                        "opentelemetry",
                        format!(
                            "Inconsistent configuration: The endpoint URL '{url}' suggests gRPC, but the protocol is set to '{protocol:?}' via environment variables. The environment variable setting will be used."
                        ),
                    );
                }
                match protocol {
                    Protocol::Grpc => {
                        let exporter = opentelemetry_otlp::SpanExporter::builder()
                            .with_tonic()
                            .with_endpoint(url)
                            .with_protocol(Protocol::Grpc)
                            .build()?;
                        build_span_exporter(batch_config, exporter)
                    }
                    protocol => {
                        let exporter = opentelemetry_otlp::SpanExporter::builder()
                            .with_http()
                            .with_endpoint(url)
                            .with_protocol(protocol)
                            .build()?;
                        build_span_exporter(batch_config, exporter)
                    }
                }
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
        let env_protocol = protocol_from_env(OtelSignal::Metrics);
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
                let protocol = env_protocol.unwrap_or(Protocol::HttpBinary);
                let exporter = match protocol {
                    Protocol::Grpc => MetricExporter::builder()
                        .with_tonic()
                        .with_endpoint(url)
                        .with_protocol(Protocol::Grpc)
                        .build()?,
                    p => MetricExporter::builder()
                        .with_http()
                        .with_endpoint(url)
                        .with_protocol(p)
                        .build()?,
                };
                opentelemetry_sdk::metrics::PeriodicReader::builder(exporter, Tokio)
                    .with_interval(flush_interval_ms)
                    .build()
            }
            GlideOpenTelemetrySignalsExporter::Grpc(url) => {
                let protocol = env_protocol.unwrap_or(Protocol::Grpc);
                if protocol != Protocol::Grpc {
                    log_warn(
                        "opentelemetry",
                        format!(
                            "Inconsistent configuration: The endpoint URL '{url}' suggests gRPC, but the protocol is set to '{protocol:?}' via environment variables. The environment variable setting will be used."
                        ),
                    );
                }
                let exporter = match protocol {
                    Protocol::Grpc => MetricExporter::builder()
                        .with_tonic()
                        .with_endpoint(url)
                        .with_protocol(Protocol::Grpc)
                        .build()?,
                    p => MetricExporter::builder()
                        .with_http()
                        .with_endpoint(url)
                        .with_protocol(p)
                        .build()?,
                };
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

    async fn init_otel() -> Result<(), GlideOTELError> {
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
            panic!("Failed to initialize OpenTelemetry: {}", e);
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

            init_otel().await.unwrap();
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
            init_otel().await.unwrap();
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
            init_otel().await.unwrap();
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
            init_otel().await.unwrap();
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
            init_otel().await.unwrap();
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

            init_otel().await.unwrap();
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

    #[test]
    fn test_protocol_from_env() {
        unsafe {
            std::env::remove_var("OTEL_EXPORTER_OTLP_PROTOCOL");
            std::env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
            std::env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
        }

        // default: None
        assert!(protocol_from_env(OtelSignal::Traces).is_none());

        unsafe { std::env::set_var("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc") };
        assert_eq!(protocol_from_env(OtelSignal::Traces), Some(Protocol::Grpc));

        unsafe { std::env::set_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL", "http/protobuf") };
        assert_eq!(
            protocol_from_env(OtelSignal::Metrics),
            Some(Protocol::HttpBinary)
        );

        unsafe {
            std::env::remove_var("OTEL_EXPORTER_OTLP_PROTOCOL");
            std::env::set_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", "http/json");
        }
        assert_eq!(
            protocol_from_env(OtelSignal::Traces),
            Some(Protocol::HttpJson)
        );
        unsafe {
            std::env::remove_var("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL");
            std::env::remove_var("OTEL_EXPORTER_OTLP_METRICS_PROTOCOL");
        }
    }

    #[test]
    fn test_span_pointer_validation() {
        // Test null pointer validation
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0) });

        // Test misaligned pointer validation
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0x1001) }); // Not 8-byte aligned
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0x1002) }); // Not 8-byte aligned
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0x1007) }); // Not 8-byte aligned

        // Test address too low validation
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0x800) }); // Below MIN_VALID_ADDRESS
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0x100) }); // Way too low

        // Test address too high validation
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0x8000_0000_0000_0000) }); // Above MAX_VALID_ADDRESS
        assert!(unsafe { !GlideOpenTelemetry::is_span_pointer_valid(0xFFFF_FFFF_FFFF_FFFF) }); // Maximum u64

        // Test valid pointer ranges
        assert!(unsafe { GlideOpenTelemetry::is_span_pointer_valid(0x1000) }); // Minimum valid
        assert!(unsafe { GlideOpenTelemetry::is_span_pointer_valid(0x10000) }); // Reasonable heap address
        assert!(unsafe { GlideOpenTelemetry::is_span_pointer_valid(0x7FFF_FFFF_FFFF_FFF8) }); // Near maximum valid
    }

    #[test]
    fn test_span_from_pointer_validation() {
        let rt = shared_runtime();
        rt.block_on(async {
            init_otel().await.unwrap();

            // Test with null pointer
            let result = unsafe { GlideOpenTelemetry::span_from_pointer(0) };
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Invalid span pointer")
            );

            // Test with misaligned pointer
            let result = unsafe { GlideOpenTelemetry::span_from_pointer(0x1001) };
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("failed validation checks")
            );

            // Test with address too low
            let result = unsafe { GlideOpenTelemetry::span_from_pointer(0x800) };
            assert!(result.is_err());
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("failed validation checks")
            );

            // Note: We don't test with very high addresses that would cause segfaults
            // The validation function will catch these, but we can't safely test Arc conversion
        });
    }

    #[test]
    fn test_new_with_parent_creates_child_span() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(SPANS_JSON);
            init_otel().await.unwrap();

            // Create parent span
            let parent_span = GlideOpenTelemetry::new_span("parent_span");

            // Create child span using new_with_parent
            let child_span_result =
                GlideSpanInner::new_with_parent("child_span", &parent_span.inner);
            assert!(
                child_span_result.is_ok(),
                "Failed to create child span with parent"
            );

            let child_span = child_span_result.unwrap();

            // Verify child span has different ID from parent
            let parent_id = parent_span.id();
            let child_id = child_span.id();
            assert_ne!(
                parent_id, child_id,
                "Child span should have different ID from parent"
            );

            // End spans to trigger export
            child_span.end();
            parent_span.end();

            // Wait for export
            sleep(Duration::from_millis(2100)).await;

            // Verify spans were exported
            let file_content = std::fs::read_to_string(SPANS_JSON).unwrap();
            let lines: Vec<&str> = file_content
                .split('\n')
                .filter(|l| !l.trim().is_empty())
                .collect();

            assert!(lines.len() >= 2, "Expected at least 2 spans to be exported");

            // Find child and parent spans in export
            let mut child_found = false;
            let mut parent_found = false;

            for line in lines {
                let span_json: serde_json::Value = serde_json::from_str(line).unwrap();
                let span_name = span_json["name"].as_str().unwrap();

                if span_name == "child_span" {
                    child_found = true;
                    // Verify child span has parent context
                    assert!(
                        span_json["parent_span_id"].is_string(),
                        "Child span should have parent_span_id"
                    );
                } else if span_name == "parent_span" {
                    parent_found = true;
                }
            }

            assert!(child_found, "Child span should be found in export");
            assert!(parent_found, "Parent span should be found in export");
        });
    }

    #[test]
    fn test_new_with_parent_error_handling() {
        let rt = shared_runtime();
        rt.block_on(async {
            init_otel().await.unwrap();

            // Create a parent span
            let parent_span = GlideOpenTelemetry::new_span("error_test_parent");

            // Test creating child with empty name (should still work)
            let child_result = GlideSpanInner::new_with_parent("", &parent_span.inner);
            assert!(
                child_result.is_ok(),
                "Should be able to create child span with empty name"
            );

            // Test creating child with very long name (should still work)
            let long_name = "a".repeat(1000);
            let child_result = GlideSpanInner::new_with_parent(&long_name, &parent_span.inner);
            assert!(
                child_result.is_ok(),
                "Should be able to create child span with long name"
            );

            // Clean up
            if let Ok(child) = child_result {
                child.end();
            }
            parent_span.end();
        });
    }

    #[test]
    fn test_span_from_pointer_error_messages() {
        let rt = shared_runtime();
        rt.block_on(async {
            init_otel().await.unwrap();

            // Test null pointer error message
            let result = unsafe { GlideOpenTelemetry::span_from_pointer(0) };
            assert!(result.is_err());
            let error_msg = result.unwrap_err().to_string();
            assert!(
                error_msg.contains("Invalid span pointer"),
                "Error message should mention invalid span pointer"
            );
            assert!(
                error_msg.contains("0x0"),
                "Error message should include the pointer value"
            );

            // Test misaligned pointer error message
            let result = unsafe { GlideOpenTelemetry::span_from_pointer(0x1001) };
            assert!(result.is_err());
            let error_msg = result.unwrap_err().to_string();
            assert!(
                error_msg.contains("failed validation checks"),
                "Error message should mention validation failure"
            );
            assert!(
                error_msg.contains("0x1001"),
                "Error message should include the pointer value"
            );

            // Test address too low error message
            let result = unsafe { GlideOpenTelemetry::span_from_pointer(0x800) };
            assert!(result.is_err());
            let error_msg = result.unwrap_err().to_string();
            assert!(
                error_msg.contains("failed validation checks"),
                "Error message should mention validation failure"
            );
        });
    }

    #[test]
    fn test_validation_functions_fallback_behavior() {
        let rt = shared_runtime();
        rt.block_on(async {
            init_otel().await.unwrap();

            // Test that validation functions provide proper fallback behavior
            // when given invalid inputs

            let invalid_pointers = vec![
                0,                     // null
                0x1001,                // misaligned
                0x800,                 // too low
                0x8000_0000_0000_0000, // too high
            ];

            for &invalid_ptr in &invalid_pointers {
                // Validation should return false
                assert!(
                    unsafe { !GlideOpenTelemetry::is_span_pointer_valid(invalid_ptr) },
                    "Pointer 0x{:x} should be invalid",
                    invalid_ptr
                );

                // Safe conversion should return error
                let result = unsafe { GlideOpenTelemetry::span_from_pointer(invalid_ptr) };
                assert!(
                    result.is_err(),
                    "Conversion of invalid pointer 0x{:x} should fail",
                    invalid_ptr
                );

                // Error should contain meaningful information
                let error_msg = result.unwrap_err().to_string();
                assert!(
                    error_msg.contains("Invalid span pointer")
                        || error_msg.contains("failed validation"),
                    "Error message should be meaningful for pointer 0x{:x}: {}",
                    invalid_ptr,
                    error_msg
                );
            }
        });
    }
}
