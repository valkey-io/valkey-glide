use core::fmt;
use opentelemetry_sdk::error::{OTelSdkError, OTelSdkResult};
use opentelemetry_sdk::trace::SpanData;
use serde_json::{Map, Value};
use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic;
use std::time::{SystemTime, UNIX_EPOCH};
use time::OffsetDateTime;
use time::format_description::BorrowedFormatItem;
use time::macros::format_description;

use opentelemetry_sdk::Resource;

/// An OpenTelemetry exporter that writes Spans to a file on export.
pub struct SpanExporterFile {
    resource: Resource,
    is_shutdown: atomic::AtomicBool,
    path: PathBuf,
}

impl fmt::Debug for SpanExporterFile {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str("SpanExporterFile")
    }
}

impl SpanExporterFile {
    /// Creates a new SpanExporterFile that writes Spans to a file on export.
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
            resource: Resource::builder_empty().build(),
            is_shutdown: atomic::AtomicBool::new(false),
            path,
        })
    }
}

impl opentelemetry_sdk::trace::SpanExporter for SpanExporterFile {
    /// Write Spans to JSON file
    async fn export(&self, batch: Vec<SpanData>) -> OTelSdkResult {
        if self.is_shutdown.load(atomic::Ordering::SeqCst) {
            return Err(OTelSdkError::AlreadyShutdown);
        }

        // TODO: Move the writes to Tokio task - https://github.com/valkey-io/valkey-glide/issues/3720
        let Ok(mut file) = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.path)
        else {
            return Err(OTelSdkError::InternalFailure(format!(
                "Unable to open exporter file: {} for append.",
                self.path.display()
            )));
        };

        let spans = to_jsons(batch);

        for span in &spans {
            if let Ok(s) = serde_json::to_string(&span)
                && let Err(e) = writeln!(file, "{}", s)
            {
                return Err(OTelSdkError::InternalFailure(format!(
                    "File write error. {e}",
                )));
            }
        }
        Ok(())
    }

    fn shutdown(&self) -> OTelSdkResult {
        self.is_shutdown.store(true, atomic::Ordering::SeqCst);
        Ok(())
    }

    fn set_resource(&mut self, res: &Resource) {
        self.resource = res.clone();
    }
}

fn to_jsons(batch: Vec<SpanData>) -> Vec<Value> {
    let mut spans = Vec::<Value>::new();
    for span in &batch {
        let mut map = Map::new();
        map.insert(
            "scope".to_owned(),
            Value::String(span.instrumentation_scope.name().to_string()),
        );
        if let Some(version) = &span.instrumentation_scope.version() {
            map.insert("version".to_owned(), Value::String(version.to_string()));
        }
        if let Some(schema_url) = &span.instrumentation_scope.schema_url() {
            map.insert(
                "schema_url".to_owned(),
                Value::String(schema_url.to_string()),
            );
        }

        let mut scope_attributes = Vec::<Value>::new();
        for kv in span.instrumentation_scope.attributes() {
            let mut attr = Map::new();
            attr.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
            scope_attributes.push(Value::Object(attr));
        }
        map.insert(
            "scope_attributes".to_owned(),
            Value::Array(scope_attributes),
        );
        map.insert("name".to_owned(), Value::String(span.name.to_string()));
        map.insert(
            "span_id".to_owned(),
            Value::String(span.span_context.span_id().to_string()),
        );
        map.insert(
            "parent_span_id".to_owned(),
            Value::String(span.parent_span_id.to_string()),
        );
        map.insert(
            "trace_id".to_owned(),
            Value::String(span.span_context.trace_id().to_string()),
        );
        map.insert(
            "kind".to_owned(),
            Value::String(format!("{:?}", span.span_kind)),
        );

        map.insert(
            "start_time".to_owned(),
            Value::String(
                span.start_time
                    .duration_since(UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_micros()
                    .to_string(),
            ),
        );

        map.insert(
            "end_time".to_owned(),
            Value::String(
                span.end_time
                    .duration_since(UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_micros()
                    .to_string(),
            ),
        );

        map.insert(
            "status".to_owned(),
            Value::String(format!("{:?}", span.status)),
        );

        // Add the span attributes
        let mut span_attributes = Vec::<Value>::new();
        for kv in span.attributes.iter() {
            let mut attr = Map::new();
            attr.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
            span_attributes.push(Value::Object(attr));
        }
        map.insert("span_attributes".to_owned(), Value::Array(span_attributes));

        // Add span events
        let mut events = Vec::<Value>::new();
        for event in span.events.iter() {
            let mut evt = Map::new();
            evt.insert("name".to_owned(), Value::String(event.name.to_string()));
            evt.insert(
                "timestamp".to_owned(),
                Value::String(format_event_timestamp(event.timestamp)),
            );

            let mut event_attributes = Vec::<Value>::new();
            for kv in event.attributes.iter() {
                let mut attr = Map::new();
                attr.insert(kv.key.to_string(), Value::String(kv.value.to_string()));
                event_attributes.push(Value::Object(attr));
            }
            evt.insert(
                "event_attributes".to_owned(),
                Value::Array(event_attributes),
            );
            events.push(Value::Object(evt));
        }
        map.insert("events".to_owned(), Value::Array(events));

        let mut links = Vec::<Value>::new();
        for link in span.links.iter() {
            let mut lk = Map::new();
            lk.insert(
                "trace_id".to_owned(),
                Value::String(link.span_context.trace_id().to_string()),
            );
            lk.insert(
                "span_id".to_owned(),
                Value::String(link.span_context.span_id().to_string()),
            );
            links.push(Value::Object(lk));
        }
        map.insert("links".to_owned(), Value::Array(links));
        spans.push(Value::Object(map));
    }
    spans
}

/// Format of span event timestamps: `YYYY-MM-DD HH:MM:SS.ffffff` in UTC.
const EVENT_TIMESTAMP_FORMAT: &[BorrowedFormatItem<'static>] =
    format_description!("[year]-[month]-[day] [hour]:[minute]:[second].[subsecond digits:6]");

fn format_event_timestamp(timestamp: SystemTime) -> String {
    OffsetDateTime::from(timestamp)
        .format(EVENT_TIMESTAMP_FORMAT)
        .expect("event timestamp format is valid")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    #[test]
    fn test_format_event_timestamp() {
        let timestamp = UNIX_EPOCH + Duration::new(1_708_531_200, 123_456_789);
        assert_eq!(
            format_event_timestamp(timestamp),
            "2024-02-21 16:00:00.123456"
        );
    }
}
