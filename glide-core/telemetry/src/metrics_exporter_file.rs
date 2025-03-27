// use chrono::{DateTime, Utc};
// use core::fmt;
// use futures_util::future::BoxFuture;
// use opentelemetry::metrics::MetricsError;
// use opentelemetry_sdk::export::{metrics::ExportResult, metrics::MetricsData};
// use serde_json::{Map, Value};
// use std::fs::OpenOptions;
// use std::io::Write;
// use std::path::PathBuf;
// use std::sync::atomic;

// use opentelemetry_sdk::resource::Resource;

// /// An OpenTelemetry exporter that writes Metrics to a file on export.
// pub struct MetricsExporterFile {
//     resource: Resource,
//     is_shutdown: atomic::AtomicBool,
//     path: PathBuf,
// }

// impl fmt::Debug for MetricsExporterFile {
//     fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
//         f.write_str("MetricsExporterFile")
//     }
// }

// impl MetricsExporterFile {
//     pub fn new(mut path: PathBuf) -> Self {
//         path.push("metrics.json");
//         MetricsExporterFile {
//             resource: Resource::default(),
//             is_shutdown: atomic::AtomicBool::new(false),
//             path,
//         }
//     }
// }

// macro_rules! file_writeln {
//     ($file:expr, $content:expr) => {{
//         if let Err(e) = $file.write(format!("{}\n", $content).as_bytes()) {
//             return Box::pin(std::future::ready(Err(MetricsError::from(format!(
//                 "File write error. {e}",
//             )))));
//         }
//     }};
// }

// impl opentelemetry_sdk::export::metrics::MetricsExporter for MetricsExporterFile {
//     /// Write Metrics to JSON file
//     fn export(&mut self, batch: Vec<MetricsData>) -> BoxFuture<'static, ExportResult> {
//         let Ok(mut data_file) = OpenOptions::new()
//             .create(true)
//             .append(true)
//             .open(&self.path)
//         else {
//             return Box::pin(std::future::ready(Err(MetricsError::from(format!(
//                 "Unable to open exporter file: {} for append.",
//                 self.path.display()
//             )))));
//         };

//         let metrics = to_jsons(batch);
//         for metric in &metrics {
//             if let Ok(s) = serde_json::to_string(&metric) {
//                 file_writeln!(data_file, s);
//             }
//         }
//         Box::pin(std::future::ready(Ok(())))
//     }

//     fn shutdown(&mut self) {
//         self.is_shutdown.store(true, atomic::Ordering::SeqCst);
//     }

//     fn set_resource(&mut self, res: &opentelemetry_sdk::Resource) {
//         self.resource = res.clone();
//     }
// }

// fn to_jsons(batch: Vec<MetricsData>) -> Vec<Value> {
//     let mut metrics = Vec::<Value>::new();
//     for metric in &batch {
//         let mut map = Map::new();
//         map.insert(
//             "scope".to_string(),
//             Value::String(metric.instrumentation_scope.name().to_string()),
//         );
//         if let Some(version) = &metric.instrumentation_scope.version() {
//             map.insert("version".to_string(), Value::String(version.to_string()));
//         }
//         if let Some(schema_url) = &metric.instrumentation_scope.schema_url() {
//             map.insert(
//                 "schema_url".to_string(),
//                 Value::String(schema_url.to_string()),
//             );
//         }

//         let mut scope_attributes = Vec::<Value>::new();
//         for kv in metric.instrumentation_scope.attributes() {
//             let mut attr = Map::new();
//             attr.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
//             scope_attributes.push(Value::Object(attr));
//         }
//         map.insert(
//             "scope_attributes".to_string(),
//             Value::Array(scope_attributes),
//         );

//         // Add metric data
//         map.insert("name".to_string(), Value::String(metric.name.to_string()));
//         map.insert(
//             "description".to_string(),
//             Value::String(metric.description.to_string()),
//         );
//         map.insert("unit".to_string(), Value::String(metric.unit.to_string()));

//         let datetime: DateTime<Utc> = metric.start_time.into();
//         map.insert(
//             "start_time".to_string(),
//             Value::String(datetime.timestamp_micros().to_string()),
//         );

//         let datetime: DateTime<Utc> = metric.end_time.into();
//         map.insert(
//             "end_time".to_string(),
//             Value::String(datetime.timestamp_micros().to_string()),
//         );

//         // Add metric attributes
//         let mut metric_attributes = Vec::<Value>::new();
//         for kv in metric.attributes.iter() {
//             let mut attr = Map::new();
//             attr.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
//             metric_attributes.push(Value::Object(attr));
//         }
//         map.insert(
//             "metric_attributes".to_string(),
//             Value::Array(metric_attributes),
//         );

//         metrics.push(Value::Object(map));
//     }
//     metrics
// }
