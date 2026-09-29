// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! OpenTelemetry integration test (server-free).
//!
//! Runs in its own test binary/process because a successful `init` is
//! process-global and one-shot: this lets us assert the happy path, idempotency,
//! and `is_initialized()` without perturbing the in-crate unit tests (which only
//! exercise the validation/rejection paths).

use std::time::Duration;

use glide::telemetry::{self, OpenTelemetryConfig, TelemetryExporter};

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn init_file_exporter_is_idempotent_and_reports_initialized() {
    // Not initialised until we ask for it.
    assert!(
        !telemetry::is_initialized(),
        "telemetry must start uninitialised in a fresh process"
    );

    let dir = std::env::temp_dir().join(format!("glide-otel-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();

    let config = OpenTelemetryConfig::builder()
        .with_flush_interval(Duration::from_millis(100))
        .with_trace_exporter(TelemetryExporter::file(&dir), Some(100))
        .with_metrics_exporter(TelemetryExporter::file(&dir))
        .build();

    telemetry::init(config).expect("file-exporter init should succeed");
    assert!(telemetry::is_initialized());

    // Idempotent: a second init (even with a different config) is a no-op Ok.
    let again = OpenTelemetryConfig::builder()
        .with_flush_interval(Duration::from_millis(500))
        .with_trace_exporter(TelemetryExporter::file(&dir), Some(1))
        .build();
    telemetry::init(again).expect("second init must be a no-op Ok");
    assert!(telemetry::is_initialized());

    // Shutdown is safe to call.
    telemetry::shutdown();

    let _ = std::fs::remove_dir_all(&dir);
}
