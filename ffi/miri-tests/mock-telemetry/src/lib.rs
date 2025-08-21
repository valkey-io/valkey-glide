// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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

impl fmt::Display for TraceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "trace error")
    }
}

impl GlideSpan {
    pub fn add_span(&self, _name: &str) -> Result<GlideSpan, TraceError> {
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
    fn from_str(_s: &str) -> Result<Self, Self::Err> {
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

    pub fn with_flush_interval(self, _duration: Duration) -> Self {
        self
    }

    pub fn with_metrics_exporter(self, _exporter: GlideOpenTelemetrySignalsExporter) -> Self {
        self
    }

    pub fn with_trace_exporter(
        self,
        _exporter: GlideOpenTelemetrySignalsExporter,
        _sample_percentage: Option<u32>,
    ) -> Self {
        self
    }
}

pub struct GlideOpenTelemetry;

impl GlideOpenTelemetry {
    pub fn initialise(_config: GlideOpenTelemetryConfig) -> Result<(), GlideOTELError> {
        Ok(())
    }

    pub fn new_span(_name: &str) -> GlideSpan {
        GlideSpan
    }

    pub unsafe fn span_from_pointer(_ptr: u64) -> Result<GlideSpan, TraceError> {
        Ok(GlideSpan)
    }
}

pub struct GlideOTELError;

impl fmt::Display for GlideOTELError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "error")
    }
}
