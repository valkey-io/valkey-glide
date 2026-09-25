use chrono::{DateTime, Utc};
use opentelemetry_sdk::error::{OTelSdkError, OTelSdkResult};
use opentelemetry_sdk::metrics::Temporality;
use opentelemetry_sdk::metrics::data::{
    AggregatedMetrics, Gauge, Histogram, Metric, MetricData, ResourceMetrics, Sum,
};
use opentelemetry_sdk::metrics::exporter::PushMetricExporter;
use serde_json::{Map, Value};
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::result::Result;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

/// An OpenTelemetry exporter that writes Metrics to a file on export.
pub struct FileMetricExporter {
    is_shutdown: AtomicBool,
    path: PathBuf,
}

impl FileMetricExporter {
    /// Creates a new FileMetricExporter that writes metrics to the specified path.
    ///
    /// # Arguments
    /// * `path` - The path where metrics will be written. This can be either a file or directory path.
    ///
    /// # Behavior
    /// - If the path points to a directory:
    ///   - The directory must exist
    ///   - Metrics will be written to a file named "signals.json" within that directory
    /// - If the path points to a file:
    ///   - If the file exists, new metrics will be appended to it (existing data is preserved)
    ///   - If the file doesn't exist, it will be created
    ///   - The parent directory must exist
    ///
    /// # Errors
    /// Returns an error if:
    /// - The parent directory doesn't exist
    /// - The path points to a directory that doesn't exist
    /// - The user doesn't have write permissions for the target location
    pub fn new(path: PathBuf) -> Result<Self, OTelSdkError> {
        // TODO: Check if the file exists and has write permissions - https://github.com/valkey-io/valkey-glide/issues/3720
        Ok(Self {
            is_shutdown: AtomicBool::new(false),
            path,
        })
    }
}

impl PushMetricExporter for FileMetricExporter {
    fn temporality(&self) -> Temporality {
        Temporality::Cumulative
    }

    async fn export(&self, metrics: &ResourceMetrics) -> OTelSdkResult {
        if self.is_shutdown.load(Ordering::SeqCst) {
            return Err(OTelSdkError::AlreadyShutdown);
        }

        // TODO: Move the writes to Tokio task - https://github.com/valkey-io/valkey-glide/issues/3720
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.path)
            .map_err(|err| {
                OTelSdkError::InternalFailure(format!("Unable to open exporter file: {err}"))
            })?;

        let metrics_json = to_json(metrics).map_err(|e| {
            OTelSdkError::InternalFailure(format!("Failed to serialize metrics to JSON: {e}"))
        })?;
        let json_string = serde_json::to_string(&metrics_json).map_err(|e| {
            OTelSdkError::InternalFailure(format!("Failed to serialize metrics to JSON: {e}"))
        })?;

        writeln!(file, "{}", json_string)
            .map_err(|e| OTelSdkError::InternalFailure(format!("File write error: {e}")))?;

        Ok(())
    }

    /// No-op implementation since metrics are written immediately in export()
    fn force_flush(&self) -> OTelSdkResult {
        Ok(())
    }

    fn shutdown_with_timeout(&self, _timeout: Duration) -> OTelSdkResult {
        self.is_shutdown.store(true, Ordering::SeqCst);
        Ok(())
    }
}

fn to_json(metrics: &ResourceMetrics) -> Result<Value, OTelSdkError> {
    let mut root = Map::new();

    // Add resource attributes
    let mut resource_attrs = Map::new();
    for (key, value) in metrics.resource().iter() {
        resource_attrs.insert(key.to_string(), Value::String(value.to_string()));
    }
    root.insert("resource".to_owned(), Value::Object(resource_attrs));

    // Add scope metrics
    let mut scope_metrics = Vec::new();
    for scope_metric in metrics.scope_metrics() {
        let mut scope = Map::new();
        let mut scope_info = Map::new();
        scope_info.insert(
            "name".to_owned(),
            Value::String(scope_metric.scope().name().to_string()),
        );
        if let Some(version) = scope_metric.scope().version() {
            scope_info.insert("version".to_owned(), Value::String(version.to_string()));
        }
        if let Some(schema_url) = scope_metric.scope().schema_url() {
            scope_info.insert(
                "schema_url".to_owned(),
                Value::String(schema_url.to_string()),
            );
        }
        scope.insert("scope".to_owned(), Value::Object(scope_info));

        // Add metrics
        let mut metrics_array = Vec::new();
        for metric in scope_metric.metrics() {
            let mut metric_obj = Map::new();
            metric_obj.insert("name".to_owned(), Value::String(metric.name().to_string()));
            metric_obj.insert(
                "description".to_owned(),
                Value::String(metric.description().to_string()),
            );
            metric_obj.insert("unit".to_owned(), Value::String(metric.unit().to_string()));

            // Serialize data points using helper functions
            let data_points = serialize_metric_data(metric)?;
            metric_obj.insert("data_points".to_owned(), Value::Array(data_points));
            metrics_array.push(Value::Object(metric_obj));
        }
        scope.insert("metrics".to_owned(), Value::Array(metrics_array));
        scope_metrics.push(Value::Object(scope));
    }
    root.insert("scope_metrics".to_owned(), Value::Array(scope_metrics));

    Ok(Value::Object(root))
}

/// Serialize metric data points based on the aggregation type.
///
/// In opentelemetry_sdk 0.32 the metric data model is an explicit
/// `AggregatedMetrics` enum keyed on the numeric type (`u64`, `i64`, `f64`),
/// each wrapping a `MetricData<T>` describing the aggregation kind. This
/// replaces the previous `Box<dyn Any>` downcasting approach while preserving
/// the same JSON output shape.
fn serialize_metric_data(metric: &Metric) -> Result<Vec<Value>, OTelSdkError> {
    match metric.data() {
        AggregatedMetrics::U64(data) => {
            serialize_metric_data_typed(metric, data, |v: u64| Value::Number(v.into()))
        }
        AggregatedMetrics::I64(data) => {
            serialize_metric_data_typed(metric, data, |v: i64| Value::Number(v.into()))
        }
        AggregatedMetrics::F64(data) => {
            serialize_metric_data_typed(metric, data, |v: f64| Value::String(v.to_string()))
        }
    }
}

/// Serialize the aggregation for a single numeric type `T`.
fn serialize_metric_data_typed<T>(
    metric: &Metric,
    data: &MetricData<T>,
    value_serializer: impl Fn(T) -> Value,
) -> Result<Vec<Value>, OTelSdkError>
where
    T: Copy,
{
    match data {
        MetricData::Sum(sum) => serialize_sum_data_points(sum, value_serializer),
        MetricData::Gauge(gauge) => serialize_gauge_data_points(gauge, value_serializer),
        MetricData::Histogram(histogram) => {
            serialize_histogram_data_points(histogram, value_serializer)
        }
        MetricData::ExponentialHistogram(_) => Err(OTelSdkError::InternalFailure(format!(
            "Unsupported metric type (ExponentialHistogram) for metric '{}'",
            metric.name()
        ))),
    }
}

/// Generic helper to serialize Sum data points
fn serialize_sum_data_points<T>(
    sum: &Sum<T>,
    value_serializer: impl Fn(T) -> Value,
) -> Result<Vec<Value>, OTelSdkError>
where
    T: Copy,
{
    let start_time = sum.start_time();
    let time = sum.time();
    let mut data_points = Vec::new();
    for point in sum.data_points() {
        let mut dp = Map::new();
        dp.insert("value".to_owned(), value_serializer(point.value()));

        let start_time: DateTime<Utc> = start_time.into();
        dp.insert(
            "start_time".to_owned(),
            Value::String(start_time.timestamp_micros().to_string()),
        );

        let time: DateTime<Utc> = time.into();
        dp.insert(
            "time".to_owned(),
            Value::String(time.timestamp_micros().to_string()),
        );

        dp.insert(
            "attributes".to_owned(),
            attributes_to_json(point.attributes()),
        );
        data_points.push(Value::Object(dp));
    }
    Ok(data_points)
}

/// Generic helper to serialize Gauge data points
fn serialize_gauge_data_points<T>(
    gauge: &Gauge<T>,
    value_serializer: impl Fn(T) -> Value,
) -> Result<Vec<Value>, OTelSdkError>
where
    T: Copy,
{
    let time = gauge.time();
    let mut data_points = Vec::new();
    for point in gauge.data_points() {
        let mut dp = Map::new();
        dp.insert("value".to_owned(), value_serializer(point.value()));

        let time: DateTime<Utc> = time.into();
        dp.insert(
            "time".to_owned(),
            Value::String(time.timestamp_micros().to_string()),
        );

        dp.insert(
            "attributes".to_owned(),
            attributes_to_json(point.attributes()),
        );
        data_points.push(Value::Object(dp));
    }
    Ok(data_points)
}

/// Generic helper to serialize Histogram data points
fn serialize_histogram_data_points<T>(
    histogram: &Histogram<T>,
    value_serializer: impl Fn(T) -> Value,
) -> Result<Vec<Value>, OTelSdkError>
where
    T: Copy,
{
    let start_time = histogram.start_time();
    let time = histogram.time();
    let mut data_points = Vec::new();
    for point in histogram.data_points() {
        let mut dp = Map::new();
        dp.insert("count".to_owned(), Value::Number(point.count().into()));
        dp.insert("sum".to_owned(), value_serializer(point.sum()));

        // Bucket counts are always u64 in the OTel SDK, independent of T
        let bucket_counts: Vec<Value> = point
            .bucket_counts()
            .map(|count| Value::Number(count.into()))
            .collect();
        dp.insert("bucket_counts".to_owned(), Value::Array(bucket_counts));

        // Bounds are always f64 in the OTel SDK, independent of T — serialized as strings for precision
        let bounds: Vec<Value> = point
            .bounds()
            .map(|bound| Value::String(bound.to_string()))
            .collect();
        dp.insert("bounds".to_owned(), Value::Array(bounds));

        let start_time: DateTime<Utc> = start_time.into();
        dp.insert(
            "start_time".to_owned(),
            Value::String(start_time.timestamp_micros().to_string()),
        );
        let time: DateTime<Utc> = time.into();
        dp.insert(
            "time".to_owned(),
            Value::String(time.timestamp_micros().to_string()),
        );

        dp.insert(
            "attributes".to_owned(),
            attributes_to_json(point.attributes()),
        );
        data_points.push(Value::Object(dp));
    }
    Ok(data_points)
}

// Helper function to convert attributes to JSON
fn attributes_to_json<'a>(attributes: impl Iterator<Item = &'a opentelemetry::KeyValue>) -> Value {
    let mut json_attributes = Map::new();
    for kv in attributes {
        json_attributes.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
    }
    Value::Object(json_attributes)
}
