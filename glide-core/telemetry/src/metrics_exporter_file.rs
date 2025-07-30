use async_trait::async_trait;
use logger_core::log_debug;
use opentelemetry_sdk::error::OTelSdkError;
use opentelemetry_sdk::metrics::Temporality;
use opentelemetry_sdk::metrics::data::ResourceMetrics;
use opentelemetry_sdk::metrics::data::{AggregatedMetrics, MetricData};
use opentelemetry_sdk::metrics::exporter::PushMetricExporter;
use serde_json::{Map, Value};
use std::any::Any;
use std::error::Error;
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
    /// Returns an OTelSdkError if:
    /// - The parent directory doesn't exist
    /// - The path points to a directory that doesn't exist
    /// - The user doesn't have write permissions for the target location
    pub fn new(path: PathBuf) -> Result<Self, Box<dyn Error + Send + Sync>> {
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

    #[allow(clippy::manual_async_fn)]
    fn export(
        &self,
        metrics: &ResourceMetrics,
    ) -> impl std::future::Future<Output = Result<(), OTelSdkError>> + Send {
        async move {
            log_debug("FileMetricExporter", "Export called");
            if self.is_shutdown.load(Ordering::SeqCst) {
                return Err(OTelSdkError::InternalFailure("Exporter is shutdown".into()));
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
                log_debug(
                    "FileMetricExporter",
                    format!("Failed to serialize metrics to JSON: {e}"),
                );
                OTelSdkError::InternalFailure(format!("Failed to serialize metrics to JSON: {e}"))
            })?;
            log_debug("FileMetricExporter", "Metrics JSON serialized successfully");
            let json_string = serde_json::to_string(&metrics_json).map_err(|e| {
                log_debug(
                    "FileMetricExporter",
                    format!("Failed to serialize JSON to string: {e}"),
                );
                OTelSdkError::InternalFailure(format!("Failed to serialize metrics to JSON: {e}"))
            })?;
            log_debug(
                "FileMetricExporter",
                format!("JSON string created: {json_string}"),
            );

            writeln!(file, "{json_string}").map_err(|e| {
                log_debug("FileMetricExporter", format!("File write error: {e}"));
                OTelSdkError::InternalFailure(format!("File write error: {e}"))
            })?;
            log_debug("FileMetricExporter", "Successfully wrote to file");

            Ok(())
        }
    }

    /// No-op implementation since metrics are written immediately in export()
    fn force_flush(&self) -> Result<(), OTelSdkError> {
        Ok(())
    }

    fn shutdown_with_timeout(&self, _timeout: Duration) -> Result<(), OTelSdkError> {
        self.is_shutdown.store(true, Ordering::SeqCst);
        Ok(())
    }
}

fn to_json(metrics: &ResourceMetrics) -> Result<Value, Box<dyn Error + Send + Sync>> {
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
        let mut metrics = Vec::new();
        for metric in scope_metric.metrics() {
            let mut metric_obj = Map::new();
            metric_obj.insert("name".to_owned(), Value::String(metric.name().to_string()));
            metric_obj.insert(
                "description".to_owned(),
                Value::String(metric.description().to_string()),
            );
            metric_obj.insert("unit".to_owned(), Value::String(metric.unit().to_string()));

            // Add data points
            let mut data_points = Vec::new();

            let aggregation = metric.data() as &dyn Any;
            if let Some(aggregated_metric) = aggregation.downcast_ref::<AggregatedMetrics>() {
                match aggregated_metric {
                    AggregatedMetrics::U64(metric_data) => {
                        match metric_data {
                            MetricData::Sum(sum) => {
                                for point in sum.data_points() {
                                    let mut dp = Map::new();
                                    dp.insert(
                                        "value".to_owned(),
                                        Value::Number(point.value().into()),
                                    );
                                    dp.insert(
                                        "attributes".to_owned(),
                                        attributes_to_json(point.attributes()),
                                    );
                                    data_points.push(Value::Object(dp));
                                }
                            }
                            MetricData::Gauge(gauge) => {
                                for point in gauge.data_points() {
                                    let mut dp = Map::new();
                                    dp.insert(
                                        "value".to_owned(),
                                        Value::Number(point.value().into()),
                                    );
                                    dp.insert(
                                        "attributes".to_owned(),
                                        attributes_to_json(point.attributes()),
                                    );
                                    data_points.push(Value::Object(dp));
                                }
                            }
                            MetricData::Histogram(histogram) => {
                                for point in histogram.data_points() {
                                    let mut dp = Map::new();
                                    dp.insert(
                                        "count".to_owned(),
                                        Value::Number(point.count().into()),
                                    );
                                    dp.insert("sum".to_owned(), Value::Number(point.sum().into()));

                                    // Add bucket counts
                                    let bucket_counts: Vec<Value> = point
                                        .bucket_counts()
                                        .map(|count| Value::Number(count.into()))
                                        .collect();
                                    dp.insert(
                                        "bucket_counts".to_owned(),
                                        Value::Array(bucket_counts),
                                    );

                                    // Add bounds
                                    let bounds: Vec<Value> = point
                                        .bounds()
                                        .map(|bound| Value::String(bound.to_string()))
                                        .collect();
                                    dp.insert("bounds".to_owned(), Value::Array(bounds));

                                    dp.insert(
                                        "attributes".to_owned(),
                                        attributes_to_json(point.attributes()),
                                    );
                                    data_points.push(Value::Object(dp));
                                }
                            }
                            _ => {
                                return Err("Unsupported U64 metric data type".to_string().into());
                            }
                        }
                    }
                    AggregatedMetrics::F64(metric_data) => {
                        match metric_data {
                            MetricData::Gauge(gauge) => {
                                for point in gauge.data_points() {
                                    let mut dp = Map::new();
                                    dp.insert(
                                        "value".to_owned(),
                                        Value::String(point.value().to_string()),
                                    );
                                    dp.insert(
                                        "attributes".to_owned(),
                                        attributes_to_json(point.attributes()),
                                    );
                                    data_points.push(Value::Object(dp));
                                }
                            }
                            MetricData::Histogram(histogram) => {
                                for point in histogram.data_points() {
                                    let mut dp = Map::new();
                                    dp.insert(
                                        "count".to_owned(),
                                        Value::Number(point.count().into()),
                                    );
                                    dp.insert(
                                        "sum".to_owned(),
                                        Value::String(point.sum().to_string()),
                                    );

                                    // Add bucket counts
                                    let bucket_counts: Vec<Value> = point
                                        .bucket_counts()
                                        .map(|count| Value::Number(count.into()))
                                        .collect();
                                    dp.insert(
                                        "bucket_counts".to_owned(),
                                        Value::Array(bucket_counts),
                                    );

                                    // Add bounds
                                    let bounds: Vec<Value> = point
                                        .bounds()
                                        .map(|bound| Value::String(bound.to_string()))
                                        .collect();
                                    dp.insert("bounds".to_owned(), Value::Array(bounds));

                                    dp.insert(
                                        "attributes".to_owned(),
                                        attributes_to_json(point.attributes()),
                                    );
                                    data_points.push(Value::Object(dp));
                                }
                            }
                            _ => {
                                return Err("Unsupported F64 metric data type".to_string().into());
                            }
                        }
                    }
                    AggregatedMetrics::I64(metric_data) => match metric_data {
                        MetricData::Sum(sum) => {
                            for point in sum.data_points() {
                                let mut dp = Map::new();
                                dp.insert("value".to_owned(), Value::Number(point.value().into()));
                                dp.insert(
                                    "attributes".to_owned(),
                                    attributes_to_json(point.attributes()),
                                );
                                data_points.push(Value::Object(dp));
                            }
                        }
                        MetricData::Gauge(gauge) => {
                            for point in gauge.data_points() {
                                let mut dp = Map::new();
                                dp.insert("value".to_owned(), Value::Number(point.value().into()));
                                dp.insert(
                                    "attributes".to_owned(),
                                    attributes_to_json(point.attributes()),
                                );
                                data_points.push(Value::Object(dp));
                            }
                        }
                        _ => {
                            return Err("Unsupported I64 metric data type".to_string().into());
                        }
                    },
                }
            } else {
                return Err(
                    format!("Unsupported metric type: {:?}", metric.data().type_id()).into(),
                );
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
fn attributes_to_json<'a>(attributes: impl Iterator<Item = &'a opentelemetry::KeyValue>) -> Value {
    let mut json_attributes = Map::new();
    for kv in attributes {
        json_attributes.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
    }
    Value::Object(json_attributes)
}
