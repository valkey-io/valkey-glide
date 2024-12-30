use opentelemetry::global::ObjectSafeSpan;
use opentelemetry::trace::SpanKind;
use opentelemetry::{global, trace::Tracer};
use opentelemetry_sdk::propagation::TraceContextPropagator;
use opentelemetry_sdk::trace::TracerProvider;
use std::sync::{Arc, RwLock};

const SPAN_WRITE_LOCK_ERR: &str = "Failed to get span write lock";

pub type GlideSpan = Arc<RwLock<opentelemetry::global::BoxedSpan>>;

pub struct GlideOpenTelemetry {}

/// Our interface to OpenTelemetry
impl GlideOpenTelemetry {
    /// Initialise the open telemetry library.
    ///
    /// This method should be called once for the given **process**
    pub fn initialise() {
        global::set_text_map_propagator(TraceContextPropagator::new());
        // TODO: we should replace the opentelemetry_stdout exporter with otlp exporter
        let exporter = opentelemetry_stdout::SpanExporter::default();
        let provider = TracerProvider::builder()
            .with_batch_exporter(exporter, opentelemetry_sdk::runtime::Tokio)
            .build();
        global::set_tracer_provider(provider);
    }

    /// Create new span
    pub fn new_span(name: String) -> GlideSpan {
        let tracer = global::tracer("valkey_glide");
        let span = tracer
            .span_builder(name)
            .with_kind(SpanKind::Client)
            .start(&tracer);
        Arc::new(RwLock::new(span))
    }

    /// Attach event with name and list of attributes to the span `span`.
    pub fn add_event(span: GlideSpan, name: &str, attributes: Option<&Vec<(&str, &str)>>) {
        let attributes: Vec<opentelemetry::KeyValue> = if let Some(attributes) = attributes {
            attributes
                .iter()
                .map(|(k, v)| opentelemetry::KeyValue::new(k.to_string(), v.to_string()))
                .collect()
        } else {
            Vec::<opentelemetry::KeyValue>::default()
        };
        span.write()
            .expect(SPAN_WRITE_LOCK_ERR)
            .add_event_with_timestamp(
                name.to_string().into(),
                std::time::SystemTime::now(),
                attributes,
            );
    }
}
