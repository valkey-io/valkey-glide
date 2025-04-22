use async_trait::async_trait;
use opentelemetry_sdk::metrics::data::ResourceMetrics;
use opentelemetry_sdk::metrics::exporter::PushMetricExporter;
use opentelemetry_sdk::metrics::MetricError;
use opentelemetry_sdk::metrics::MetricResult;
use opentelemetry_sdk::metrics::Temporality;
use serde_json::{Map, Value};
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic;

/// An OpenTelemetry exporter that writes Metrics to a file on export.
pub struct FileMetricExporter {
    is_shutdown: atomic::AtomicBool,
    path: PathBuf,
}

impl FileMetricExporter {
    pub fn new(mut path: PathBuf) -> std::result::Result<Self, MetricError> {
        path.push("metrics.json");
        Ok(FileMetricExporter {
            is_shutdown: atomic::AtomicBool::new(false),
            path,
        })
    }
}

// macro_rules! file_writeln {
//     ($file:expr, $content:expr) => {{
//         if let Err(e) = $file.write(format!("{}\n", $content).as_bytes()) {
//             return Err(MetricError::Other(format!("File write error: {}", e)));
//         }
//     }};
// }

#[async_trait]
impl PushMetricExporter for FileMetricExporter {
    fn temporality(&self) -> Temporality {
        Temporality::Cumulative
    }

    async fn export(&self, metrics: &mut ResourceMetrics) -> MetricResult<()> {
        if self.is_shutdown.load(atomic::Ordering::SeqCst) {
            return Ok(());
        }

        let Ok(mut data_file) = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.path)
        else {
            return Err(MetricError::Other(format!(
                "Unable to open exporter file: {} for append.",
                self.path.display()
            )));
        };

        let metrics_json = to_json(metrics);
        if let Ok(s) = serde_json::to_string(&metrics_json) {
            if let Err(e) = data_file.write(format!("{}\n", s).as_bytes()) {
                return Err(MetricError::Other(format!("File write error: {}", e)));
            }
        }

        Ok(())
    }

    async fn force_flush(&self) -> MetricResult<()> {
        Ok(())
    }

    fn shutdown(&self) -> MetricResult<()> {
        self.is_shutdown.store(true, atomic::Ordering::SeqCst);
        Ok(())
    }
}

fn to_json(metrics: &ResourceMetrics) -> Value {
    let mut root = Map::new();

    // Add resource attributes
    let mut resource_attrs = Map::new();
    for (key, value) in metrics.resource.iter() {
        resource_attrs.insert(key.to_string(), Value::String(value.to_string()));
    }
    root.insert("resource".to_string(), Value::Object(resource_attrs));

    // Add scope metrics
    let mut scope_metrics = Vec::new();
    for scope_metric in metrics.scope_metrics.iter() {
        let mut scope = Map::new();
        scope.insert(
            "name".to_string(),
            Value::String(scope_metric.scope.name().to_string()),
        );
        if let Some(version) = scope_metric.scope.version() {
            scope.insert("version".to_string(), Value::String(version.to_string()));
        }
        if let Some(schema_url) = scope_metric.scope.schema_url() {
            scope.insert(
                "schema_url".to_string(),
                Value::String(schema_url.to_string()),
            );
        }

        // Add metrics
        let mut metrics = Vec::new();
        for metric in scope_metric.metrics.iter() {
            let mut metric_obj = Map::new();
            metric_obj.insert("name".to_string(), Value::String(metric.name.to_string()));
            metric_obj.insert(
                "description".to_string(),
                Value::String(metric.description.to_string()),
            );
            metric_obj.insert("unit".to_string(), Value::String(metric.unit.to_string()));

            // Add data points
            let data_points = Vec::new();
            // match &metric.data {
            //     data::Metric::Sum(sum) => {
            //         for point in sum.data_points.iter() {
            //             let mut dp = Map::new();
            //             dp.insert("value".to_string(), Value::String(point.value.to_string()));
            //             let start_time: DateTime<Utc> = point.start_time.into();
            //             dp.insert(
            //                 "start_time".to_string(),
            //                 Value::String(start_time.timestamp_micros().to_string()),
            //             );
            //             let time: DateTime<Utc> = point.time.into();
            //             dp.insert(
            //                 "time".to_string(),
            //                 Value::String(time.timestamp_micros().to_string()),
            //             );

            //             // Add attributes
            //             let mut attributes = Map::new();
            //             for (key, value) in point.attributes.iter() {
            //                 attributes.insert(key.to_string(), Value::String(value.to_string()));
            //             }
            //             dp.insert("attributes".to_string(), Value::Object(attributes));

            //             data_points.push(Value::Object(dp));
            //         }
            //     }
            //     data::Metric::Gauge(gauge) => {
            //         for point in gauge.data_points.iter() {
            //             let mut dp = Map::new();
            //             dp.insert("value".to_string(), Value::String(point.value.to_string()));
            //             let time: DateTime<Utc> = point.time.into();
            //             dp.insert(
            //                 "time".to_string(),
            //                 Value::String(time.timestamp_micros().to_string()),
            //             );

            //             // Add attributes
            //             let mut attributes = Map::new();
            //             for (key, value) in point.attributes.iter() {
            //                 attributes.insert(key.to_string(), Value::String(value.to_string()));
            //             }
            //             dp.insert("attributes".to_string(), Value::Object(attributes));

            //             data_points.push(Value::Object(dp));
            //         }
            //     }
            //     data::Metric::Histogram(histogram) => {
            //         for point in histogram.data_points.iter() {
            //             let mut dp = Map::new();
            //             dp.insert("count".to_string(), Value::Number(point.count.into()));
            //             dp.insert("sum".to_string(), Value::String(point.sum.to_string()));

            //             // Add bucket counts
            //             let bucket_counts: Vec<Value> = point
            //                 .bucket_counts
            //                 .iter()
            //                 .map(|&count| Value::Number(count.into()))
            //                 .collect();
            //             dp.insert("bucket_counts".to_string(), Value::Array(bucket_counts));

            //             // Add explicit bounds
            //             let bounds: Vec<Value> = point
            //                 .explicit_bounds
            //                 .iter()
            //                 .map(|&bound| Value::String(bound.to_string()))
            //                 .collect();
            //             dp.insert("explicit_bounds".to_string(), Value::Array(bounds));

            //             let start_time: DateTime<Utc> = point.start_time.into();
            //             dp.insert(
            //                 "start_time".to_string(),
            //                 Value::String(start_time.timestamp_micros().to_string()),
            //             );
            //             let time: DateTime<Utc> = point.time.into();
            //             dp.insert(
            //                 "time".to_string(),
            //                 Value::String(time.timestamp_micros().to_string()),
            //             );

            //             // Add attributes
            //             let mut attributes = Map::new();
            //             for (key, value) in point.attributes.iter() {
            //                 attributes.insert(key.to_string(), Value::String(value.to_string()));
            //             }
            //             dp.insert("attributes".to_string(), Value::Object(attributes));

            //             data_points.push(Value::Object(dp));
            //         }
            //     }
            // }
            metric_obj.insert("data_points".to_string(), Value::Array(data_points));
            metrics.push(Value::Object(metric_obj));
        }
        scope.insert("metrics".to_string(), Value::Array(metrics));
        scope_metrics.push(Value::Object(scope));
    }
    root.insert("scope_metrics".to_string(), Value::Array(scope_metrics));

    Value::Object(root)
}
