use std::fmt;
use std::time::Duration;

pub struct Error;

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}

#[derive(Clone)]
pub struct GlideSpan;

#[derive(Debug)]
pub struct TraceError;

impl GlideSpan {
    pub fn add_span(&self, name: &str) -> Result<GlideSpan, TraceError> {
        Ok(GlideSpan)
    }

    pub fn end(&self) {}
}

pub struct GlideOpenTelemetrySignalsExporter;

impl GlideOpenTelemetrySignalsExporter {
    pub fn new() -> Self {
        GlideOpenTelemetrySignalsExporter
    }
}

impl std::str::FromStr for GlideOpenTelemetrySignalsExporter {
    type Err = Error;
    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(GlideOpenTelemetrySignalsExporter)
    }
}

pub struct GlideOpenTelemetryConfigBuilder;

pub struct GlideOpenTelemetryConfig;

impl Default for GlideOpenTelemetryConfigBuilder {
    fn default() -> Self {
        GlideOpenTelemetryConfigBuilder
    }
}

impl GlideOpenTelemetryConfigBuilder {
    pub fn build(self) -> GlideOpenTelemetryConfig {
        GlideOpenTelemetryConfig
    }

    pub fn with_flush_interval(mut self, duration: Duration) -> Self {
        self
    }

    pub fn with_metrics_exporter(mut self, exporter: GlideOpenTelemetrySignalsExporter) -> Self {
        self
    }

    pub fn with_trace_exporter(
        mut self, 
        exporter: GlideOpenTelemetrySignalsExporter,
        sample_percentage: Option<u32>,
    ) -> Self {
        self
    }
}

pub struct GlideOpenTelemetry;

impl GlideOpenTelemetry {
    pub fn initialise(config: GlideOpenTelemetryConfig) -> Result<(), GlideOTELError> {
        todo!()
    }

    pub fn new_span(name: &str) -> GlideSpan {
        todo!()
    }
}

pub struct GlideOTELError;

impl fmt::Display for GlideOTELError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}


