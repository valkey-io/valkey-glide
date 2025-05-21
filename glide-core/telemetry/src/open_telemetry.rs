use opentelemetry::global::ObjectSafeSpan;
use opentelemetry::trace::{SpanKind, TraceContextExt, TraceError};
use opentelemetry::{global, trace::Tracer};
use opentelemetry_otlp::{Protocol, WithExportConfig};
use opentelemetry_sdk::export::trace::SpanExporter;
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::runtime::Tokio;
use opentelemetry_sdk::trace::{BatchConfig, BatchSpanProcessor, TracerProvider};
use std::io::{Error, ErrorKind};
use std::path::PathBuf;
#[cfg(test)]
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use thiserror::Error;
use url::Url;

const SPAN_WRITE_LOCK_ERR: &str = "Failed to acquire span write lock";
const SPAN_READ_LOCK_ERR: &str = "Failed to acquire span read lock";
const TRACE_SCOPE: &str = "valkey_glide";

/// Custom error type for OpenTelemetry errors in Glide
#[derive(Debug, Error)]
pub enum GlideOTELError {
    #[error("Glide OpenTelemetry trace error: {0}")]
    OpenTelemetry(#[from] TraceError),

    #[error("Failed to acquire span read lock")]
    SpanReadLockError,

    #[error("Failed to acquire span write lock")]
    SpanWriteLockError,

    #[error("Other error: {0}")]
    Other(String),
}

/// Default interval in milliseconds for flushing open telemetry data to the collector.
pub const DEFAULT_FLUSH_SIGNAL_INTERVAL_MS: u32 = 5000;

/// Default filename for the open telemetry data file.
pub const DEFAULT_SIGNAL_FILENAME: &str = "signals.json";

pub enum GlideSpanStatus {
    Ok,
    Error(String),
}

#[derive(Clone, Debug)]
/// Defines the method that exporter connects to the collector. It can be:
/// gRPC or HTTP. The third type (i.e. "File") defines an exporter that does not connect to a collector
/// instead, it writes the collected signals to files.
pub enum GlideOpenTelemetryTraceExporter {
    /// Collector is listening on grpc
    Grpc(String),
    /// Collector is listening on http
    Http(String),
    /// No collector. Instead, write the traces collected to a file. The contained value "PathBuf"
    /// points to the folder where the collected data should be placed.
    File(PathBuf),
}

impl std::str::FromStr for GlideOpenTelemetryTraceExporter {
    type Err = Error;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        parse_endpoint(s)
    }
}

fn parse_endpoint(endpoint: &str) -> Result<GlideOpenTelemetryTraceExporter, Error> {
    // Parse the URL using the `url` crate to validate it
    let url = Url::parse(endpoint)
        .map_err(|_| Error::new(ErrorKind::InvalidInput, format!("Parse error. {endpoint}")))?;

    match url.scheme() {
        "http" | "https" => Ok(GlideOpenTelemetryTraceExporter::Http(endpoint.to_string())), // HTTP/HTTPS endpoint
        "grpc" => Ok(GlideOpenTelemetryTraceExporter::Grpc(endpoint.to_string())), // gRPC endpoint
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

            Ok(GlideOpenTelemetryTraceExporter::File(final_path))
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
            GlideSpanStatus::Error(what) => {
                self.span.write().expect(SPAN_WRITE_LOCK_ERR).set_status(
                    opentelemetry::trace::Status::Error {
                        description: what.into(),
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
    span_flush_interval: std::time::Duration,
    /// Determines the protocol between the collector and GLIDE
    trace_exporter: GlideOpenTelemetryTraceExporter,
}

#[derive(Clone, Debug)]
#[allow(dead_code)]
pub struct GlideOpenTelemetryConfigBuilder {
    span_flush_interval: std::time::Duration,
    trace_exporter: GlideOpenTelemetryTraceExporter,
}

impl Default for GlideOpenTelemetryConfigBuilder {
    fn default() -> Self {
        GlideOpenTelemetryConfigBuilder {
            span_flush_interval: std::time::Duration::from_millis(
                DEFAULT_FLUSH_SIGNAL_INTERVAL_MS as u64,
            ),
            trace_exporter: GlideOpenTelemetryTraceExporter::File(std::env::temp_dir()),
        }
    }
}

impl GlideOpenTelemetryConfigBuilder {
    pub fn with_flush_interval(mut self, duration: std::time::Duration) -> Self {
        self.span_flush_interval = duration;
        self
    }

    pub fn with_trace_exporter(mut self, protocol: GlideOpenTelemetryTraceExporter) -> Self {
        self.trace_exporter = protocol;
        self
    }

    pub fn build(self) -> GlideOpenTelemetryConfig {
        GlideOpenTelemetryConfig {
            span_flush_interval: self.span_flush_interval,
            trace_exporter: self.trace_exporter,
        }
    }
}

fn build_exporter(
    batch_config: BatchConfig,
    exporter: impl SpanExporter + 'static,
) -> BatchSpanProcessor<Tokio> {
    BatchSpanProcessor::builder(exporter, Tokio)
        .with_batch_config(batch_config)
        .build()
}

#[derive(Clone)]
pub struct GlideOpenTelemetry {}

/// Static mutex to track initialization state
static OTEL_INITIALIZED: Mutex<bool> = Mutex::new(false);

/// Our interface to OpenTelemetry
impl GlideOpenTelemetry {
    /// Initialise the open telemetry library with a file system exporter
    ///
    /// This method should be called once for the given **process**
    /// If OpenTelemetry is already initialized, this method will return Ok(()) without reinitializing
    pub fn initialise(config: GlideOpenTelemetryConfig) -> Result<(), GlideOTELError> {
        // Check if already initialized
        let mut initialized = OTEL_INITIALIZED.lock().map_err(|_| {
            GlideOTELError::Other("Failed to acquire initialization lock".to_string())
        })?;

        if *initialized {
            return Ok(());
        }

        let batch_config = opentelemetry_sdk::trace::BatchConfigBuilder::default()
            .with_scheduled_delay(config.span_flush_interval)
            .build();

        let trace_exporter = match config.trace_exporter {
            GlideOpenTelemetryTraceExporter::File(p) => {
                let exporter = crate::SpanExporterFile::new(p);
                build_exporter(batch_config, exporter)
            }
            GlideOpenTelemetryTraceExporter::Http(url) => {
                let exporter = opentelemetry_otlp::SpanExporter::builder()
                    .with_http()
                    .with_endpoint(url)
                    .with_protocol(Protocol::HttpBinary)
                    .build()?;
                build_exporter(batch_config, exporter)
            }
            GlideOpenTelemetryTraceExporter::Grpc(url) => {
                let exporter = opentelemetry_otlp::SpanExporter::builder()
                    .with_tonic()
                    .with_endpoint(url)
                    .with_protocol(Protocol::Grpc)
                    .build()?;

                build_exporter(batch_config, exporter)
            }
        };

        global::set_text_map_propagator(TraceContextPropagator::new());
        let provider = TracerProvider::builder()
            .with_span_processor(trace_exporter)
            .build();
        global::set_tracer_provider(provider);

        // Mark as initialized
        *initialized = true;

        Ok(())
    }

    pub fn get_span_interval(config: GlideOpenTelemetryConfig) -> u64 {
        config.span_flush_interval.as_millis() as u64
    }

    /// Create new span
    pub fn new_span(name: &str) -> GlideSpan {
        GlideSpan::new(name)
    }

    /// Trigger a shutdown procedure flushing all remaining traces
    pub fn shutdown() {
        global::shutdown_tracer_provider();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    const SPANS_JSON: &str = "/tmp/spans.json";

    fn string_property_to_u64(json: &serde_json::Value, prop: &str) -> u64 {
        let s = json[prop].to_string().replace('"', "");
        s.parse::<u64>().unwrap()
    }

    async fn create_test_spans() {
        let span = GlideOpenTelemetry::new_span("Root_Span_1");
        span.add_event("Event1");
        span.set_status(GlideSpanStatus::Ok);

        let child1 = span.add_span("Network_Span").unwrap();

        // Simulate some work
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        child1.end();

        // Simulate that the parent span is still doing some work
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        span.end();

        let span = GlideOpenTelemetry::new_span("Root_Span_2");
        span.add_event("Event1");
        span.add_event("Event2");
        span.set_status(GlideSpanStatus::Ok);
        drop(span); // writes the span

        tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
    }

    #[test]
    fn test_span_json_exporter() {
        let _ = std::fs::remove_file(SPANS_JSON);
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        runtime.block_on(async {
            let config = GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(std::time::Duration::from_millis(100))
                .with_trace_exporter(GlideOpenTelemetryTraceExporter::File(PathBuf::from("/tmp")))
                .build();
            let _ = GlideOpenTelemetry::initialise(config);
            create_test_spans().await;

            // Read the file content
            let file_content = std::fs::read_to_string(SPANS_JSON).unwrap();
            let lines: Vec<&str> = file_content.split('\n').collect();
            assert_eq!(lines.len(), 4);

            let span_json: serde_json::Value = serde_json::from_str(lines[0]).unwrap();
            assert_eq!(span_json["name"], "Network_Span");
            let network_span_id = span_json["span_id"].to_string();
            let network_span_start_time = string_property_to_u64(&span_json, "start_time");
            let network_span_end_time = string_property_to_u64(&span_json, "end_time");

            // Because of the sleep above, the network span should be at least 100ms (units are microseconds)
            assert!(network_span_end_time - network_span_start_time >= 100_000);

            let span_json: serde_json::Value = serde_json::from_str(lines[1]).unwrap();
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

            let span_json: serde_json::Value = serde_json::from_str(lines[2]).unwrap();
            assert_eq!(span_json["name"], "Root_Span_2");
        });
    }

    #[test]
    fn test_span_http_exporter() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        runtime.block_on(async {
            let config = GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(std::time::Duration::from_millis(100))
                .with_trace_exporter(GlideOpenTelemetryTraceExporter::Http(
                    "http://test.com".to_string(),
                ))
                .build();
            let _ = GlideOpenTelemetry::initialise(config);
            create_test_spans().await;
        });
    }

    #[test]
    fn test_span_grpc_exporter() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        runtime.block_on(async {
            let config = GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(std::time::Duration::from_millis(100))
                .with_trace_exporter(GlideOpenTelemetryTraceExporter::Grpc(
                    "grpc://test.com".to_string(),
                ))
                .build();
            let _ = GlideOpenTelemetry::initialise(config);
            create_test_spans().await;
        });
    }

    #[test]
    fn test_span_reference_count() {
        let span = GlideOpenTelemetry::new_span("Root_Span_1");
        span.add_reference();
        assert_eq!(span.get_reference_count(), 2);
        drop(span);
    }

    #[test]
    fn test_span_transaction() {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap();
        runtime.block_on(async {
            let config = GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(std::time::Duration::from_millis(100))
                .with_trace_exporter(GlideOpenTelemetryTraceExporter::File(PathBuf::from("/tmp")))
                .build();
            let _ = GlideOpenTelemetry::initialise(config);
            create_test_spans().await;
        });
    }
}
