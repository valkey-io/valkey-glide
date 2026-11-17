// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_telemetry::{
    GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder, GlideOpenTelemetrySignalsExporter,
};
use std::fs;
use std::time::Duration;
use tempfile::tempdir;

#[test]
fn test_shutdown_flushes_metrics() {
    let temp_dir = tempdir().expect("Failed to create tempdir");
    let metrics_path = temp_dir.path().join("shutdown_metrics.json");

    // Configure OpenTelemetry with a 60-second flush interval
    // so normal periodic flushes do not happen automatically.
    let config = GlideOpenTelemetryConfigBuilder::default()
        .with_flush_interval(Duration::from_secs(60))
        .with_metrics_exporter(GlideOpenTelemetrySignalsExporter::File(
            metrics_path.clone(),
        ))
        .build();

    GlideOpenTelemetry::initialise(config).expect("Failed to initialize OpenTelemetry");

    // Record a metric
    GlideOpenTelemetry::record_retry_attempt().expect("Failed to record retry attempt");

    // Prior to shutdown, the file should either not exist or be empty
    let content_before = fs::read_to_string(&metrics_path).unwrap_or_default();
    assert!(
        content_before.trim().is_empty(),
        "Metrics should not be flushed before shutdown when flush_interval is 60s"
    );

    // Call shutdown which invokes SdkMeterProvider::shutdown and flushes metrics
    GlideOpenTelemetry::shutdown();

    // After shutdown, metrics must be flushed to the file
    let content_after =
        fs::read_to_string(&metrics_path).expect("Metrics file should exist after shutdown");
    assert!(
        !content_after.trim().is_empty(),
        "Metrics file should contain exported metrics after shutdown"
    );
    assert!(
        content_after.contains("glide.retry_attempts"),
        "Exported metrics should contain glide.retry_attempts metric"
    );
}
