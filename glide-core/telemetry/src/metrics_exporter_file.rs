use async_trait::async_trait;
use chrono::{DateTime, Utc};
use opentelemetry_sdk::metrics::MetricError;
use opentelemetry_sdk::metrics::MetricResult;
use opentelemetry_sdk::metrics::Temporality;
use opentelemetry_sdk::metrics::data::ResourceMetrics;
use opentelemetry_sdk::metrics::data::{Gauge, Histogram, Sum};
use opentelemetry_sdk::metrics::exporter::PushMetricExporter;
use serde_json::{Map, Value};
use std::any::Any;
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::result::Result;
use std::sync::atomic::{AtomicBool, Ordering};

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
    /// Returns a MetricError if:
    /// - The parent directory doesn't exist
    /// - The path points to a directory that doesn't exist
    /// - The user doesn't have write permissions for the target location
    pub fn new(path: PathBuf) -> Result<Self, MetricError> {
        // TODO: Check if the file exists and has write permissions - https://github.com/valkey-io/valkey-glide/issues/3720
        Ok(Self {
            is_shutdown: AtomicBool::new(false),
            path,
        })
    }
}

#[async_trait]
impl PushMetricExporter for FileMetricExporter {
    fn temporality(&self) -> Temporality {
        Temporality::Cumulative
    }

    async fn export(&self, metrics: &mut ResourceMetrics) -> MetricResult<()> {
        if self.is_shutdown.load(Ordering::SeqCst) {
            return Err(MetricError::Other("Exporter is shutdown".to_string()));
        }

        // TODO: Move the writes to Tokio task - https://github.com/valkey-io/valkey-glide/issues/3720
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.path)
            .map_err(|err| MetricError::Other(format!("Unable to open exporter file: {err}")))?;

        let metrics_json = to_json(metrics)
            .map_err(|e| MetricError::Other(format!("Failed to serialize metrics to JSON: {e}")))?;
        let json_string = serde_json::to_string(&metrics_json)
            .map_err(|e| MetricError::Other(format!("Failed to serialize metrics to JSON: {e}")))?;

        writeln!(file, "{}", json_string)
            .map_err(|e| MetricError::Other(format!("File write error: {e}")))?;

        Ok(())
    }

    /// No-op implementation since metrics are written immediately in export()
    async fn force_flush(&self) -> MetricResult<()> {
        Ok(())
    }

    fn shutdown(&self) -> MetricResult<()> {
        self.is_shutdown.store(true, Ordering::SeqCst);
        Ok(())
    }
}

fn to_json(metrics: &ResourceMetrics) -> Result<Value, MetricError> {
    let mut root = Map::new();

    // Add resource attributes
    let mut resource_attrs = Map::new();
    for (key, value) in metrics.resource.iter() {
        resource_attrs.insert(key.to_string(), Value::String(value.to_string()));
    }
    root.insert("resource".to_owned(), Value::Object(resource_attrs));

    // Add scope metrics
    let mut scope_metrics = Vec::new();
    for scope_metric in metrics.scope_metrics.iter() {
        let mut scope = Map::new();
        let mut scope_info = Map::new();
        scope_info.insert(
            "name".to_owned(),
            Value::String(scope_metric.scope.name().to_string()),
        );
        if let Some(version) = scope_metric.scope.version() {
            scope_info.insert("version".to_owned(), Value::String(version.to_string()));
        }
        if let Some(schema_url) = scope_metric.scope.schema_url() {
            scope_info.insert(
                "schema_url".to_owned(),
                Value::String(schema_url.to_string()),
            );
        }
        scope.insert("scope".to_owned(), Value::Object(scope_info));

        // Add metrics
        let mut metrics = Vec::new();
        for metric in scope_metric.metrics.iter() {
            let mut metric_obj = Map::new();
            metric_obj.insert("name".to_owned(), Value::String(metric.name.to_string()));
            metric_obj.insert(
                "description".to_owned(),
                Value::String(metric.description.to_string()),
            );
            metric_obj.insert("unit".to_owned(), Value::String(metric.unit.to_string()));

            // Add data points
            let mut data_points = Vec::new();

            let aggregation = metric.data.as_ref() as &dyn Any;
            if let Some(sum) = aggregation.downcast_ref::<Sum<u64>>() {
                for point in sum.data_points.iter() {
                    let mut dp = Map::new();
                    dp.insert("value".to_owned(), Value::Number(point.value.into()));
                    let start_time = point
                        .start_time
                        .ok_or_else(|| MetricError::Other("Missing start time".to_string()))?;
                    let start_time: DateTime<Utc> = start_time.into();
                    dp.insert(
                        "start_time".to_owned(),
                        Value::String(start_time.timestamp_micros().to_string()),
                    );

                    let time = point
                        .time
                        .ok_or_else(|| MetricError::Other("Missing time".to_string()))?;
                    let time: DateTime<Utc> = time.into();
                    dp.insert(
                        "time".to_owned(),
                        Value::String(time.timestamp_micros().to_string()),
                    );

                    dp.insert(
                        "attributes".to_owned(),
                        attributes_to_json(&point.attributes),
                    );
                    data_points.push(Value::Object(dp));
                }
            } else if let Some(gauge) = aggregation.downcast_ref::<Gauge<f64>>() {
                for point in gauge.data_points.iter() {
                    let mut dp = Map::new();
                    dp.insert("value".to_owned(), Value::String(point.value.to_string()));
                    let time = point
                        .time
                        .ok_or_else(|| MetricError::Other("Missing time".to_string()))?;
                    let time: DateTime<Utc> = time.into();
                    dp.insert(
                        "time".to_owned(),
                        Value::String(time.timestamp_micros().to_string()),
                    );

                    dp.insert(
                        "attributes".to_owned(),
                        attributes_to_json(&point.attributes),
                    );
                    data_points.push(Value::Object(dp));
                }
            } else if let Some(histogram) = aggregation.downcast_ref::<Histogram<f64>>() {
                for point in histogram.data_points.iter() {
                    let mut dp = Map::new();
                    dp.insert("count".to_owned(), Value::Number(point.count.into()));
                    dp.insert("sum".to_owned(), Value::String(point.sum.to_string()));

                    // Add bucket counts
                    let bucket_counts: Vec<Value> = point
                        .bucket_counts
                        .iter()
                        .map(|&count| Value::Number(count.into()))
                        .collect();
                    dp.insert("bucket_counts".to_owned(), Value::Array(bucket_counts));

                    // Add bounds
                    let bounds: Vec<Value> = point
                        .bounds
                        .iter()
                        .map(|&bound| Value::String(bound.to_string()))
                        .collect();
                    dp.insert("bounds".to_owned(), Value::Array(bounds));

                    let start_time: DateTime<Utc> = point.start_time.into();
                    dp.insert(
                        "start_time".to_owned(),
                        Value::String(start_time.timestamp_micros().to_string()),
                    );
                    let time: DateTime<Utc> = point.time.into();
                    dp.insert(
                        "time".to_owned(),
                        Value::String(time.timestamp_micros().to_string()),
                    );

                    dp.insert(
                        "attributes".to_owned(),
                        attributes_to_json(&point.attributes),
                    );
                    data_points.push(Value::Object(dp));
                }
            } else {
                return Err(MetricError::Other(format!(
                    "Unsupported metric type: {:?}",
                    metric.data.as_ref().type_id()
                )));
            }
            metric_obj.insert("data_points".to_owned(), Value::Array(data_points));
            metrics.push(Value::Object(metric_obj));
        }
        scope.insert("metrics".to_owned(), Value::Array(metrics));
        scope_metrics.push(Value::Object(scope));
    }
    root.insert("scope_metrics".to_owned(), Value::Array(scope_metrics));

    Ok(Value::Object(root))
}

// Helper function to convert attributes to JSON
fn attributes_to_json(attributes: &[opentelemetry::KeyValue]) -> Value {
    let mut json_attributes = Map::new();
    for kv in attributes.iter() {
        json_attributes.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
    }
    Value::Object(json_attributes)
}
