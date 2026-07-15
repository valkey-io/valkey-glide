// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests for the file metric exporter across all supported instrument/value types.
//!
//! These tests verify that `FileMetricExporter::to_json()` serializes every
//! metric shape GLIDE relies on (Counter/UpDownCounter -> Sum, Gauge, Histogram)
//! for the `u64`, `i64`, and `f64` value types, including the
//! `SUBSCRIPTION_LAST_SYNC_TIMESTAMP` regression that used `u64_gauge()`.
//!
//! In opentelemetry_sdk 0.32 the metric data model (`ResourceMetrics`, `Sum`,
//! `Gauge`, `Histogram`, and their data points) has private fields and no public
//! constructors, so tests can no longer hand-build these structs. Instead we
//! drive real SDK instruments through an `SdkMeterProvider` whose reader feeds an
//! `InMemoryMetricExporter`. After `force_flush()` we retrieve the aggregated
//! `ResourceMetrics` and export it through the `FileMetricExporter` under test.
//! This exercises the real aggregation pipeline and keeps the JSON-output
//! contract assertions identical.

use opentelemetry::KeyValue;
use opentelemetry::metrics::MeterProvider;
use opentelemetry_sdk::Resource;
use opentelemetry_sdk::metrics::data::ResourceMetrics;
use opentelemetry_sdk::metrics::exporter::PushMetricExporter;
use opentelemetry_sdk::metrics::{
    InMemoryMetricExporter, PeriodicReader, SdkMeterProvider, Temporality,
};
use std::fs;
use telemetrylib::FileMetricExporter;
use tempfile::TempDir;

/// Build a meter provider backed by an in-memory exporter, returning both so the
/// caller can record measurements and then retrieve the aggregated metrics.
fn provider_with_exporter() -> (SdkMeterProvider, InMemoryMetricExporter) {
    let exporter = InMemoryMetricExporter::default();
    let provider = SdkMeterProvider::builder()
        .with_resource(
            Resource::builder_empty()
                .with_attribute(KeyValue::new("service.name", "test_service"))
                .build(),
        )
        .with_reader(PeriodicReader::builder(exporter.clone()).build())
        .build();
    (provider, exporter)
}

/// Force-flush the provider and return the single collected `ResourceMetrics`.
fn collect_metrics(
    provider: &SdkMeterProvider,
    exporter: &InMemoryMetricExporter,
) -> ResourceMetrics {
    provider.force_flush().expect("force_flush should succeed");
    let mut finished = exporter
        .get_finished_metrics()
        .expect("get_finished_metrics should succeed");
    assert!(
        !finished.is_empty(),
        "expected at least one ResourceMetrics batch"
    );
    finished.remove(0)
}

/// Export a collected `ResourceMetrics` through `FileMetricExporter` and return
/// the parsed JSON, after asserting the file/metric structure.
async fn export_and_read(
    test_name: &str,
    metric_name: &str,
    metrics: ResourceMetrics,
) -> serde_json::Value {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join(format!("{}.json", test_name));

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");
    let result = exporter.export(&metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for {}: {:?}",
        test_name,
        result.err()
    );

    assert!(file_path.exists(), "Metrics file should be created");
    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    assert!(!content.is_empty(), "Metrics file should not be empty");
    let json: serde_json::Value =
        serde_json::from_str(&content).expect("Metrics file should contain valid JSON");

    let metrics_array = json["scope_metrics"][0]["metrics"]
        .as_array()
        .expect("Should have metrics array");
    assert_eq!(metrics_array.len(), 1, "Should have one metric");
    assert_eq!(
        metrics_array[0]["name"].as_str().unwrap(),
        metric_name,
        "Metric name should match"
    );

    json
}

// ============================================================================
// Value extraction helpers
// ============================================================================

fn first_value(json: &serde_json::Value) -> &serde_json::Value {
    &json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
}

fn first_data_point(json: &serde_json::Value) -> &serde_json::Value {
    &json["scope_metrics"][0]["metrics"][0]["data_points"][0]
}

// ============================================================================
// Gauge tests (u64 / i64 / f64)
// ============================================================================

#[tokio::test]
async fn test_gauge_u64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.u64_gauge("test.gauge.u64").build();
    gauge.record(12345u64, &[KeyValue::new("test_attr", "test_value")]);

    let json = export_and_read(
        "gauge_u64",
        "test.gauge.u64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_u64().unwrap(), 12345);
}

#[tokio::test]
async fn test_gauge_u64_large_values() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let timestamp = 1708531200000u64;
    let gauge = meter.u64_gauge("subscription.last_sync_timestamp").build();
    gauge.record(timestamp, &[]);

    let json = export_and_read(
        "gauge_u64_large",
        "subscription.last_sync_timestamp",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_u64().unwrap(), timestamp);
}

#[tokio::test]
async fn test_gauge_u64_zero_value() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.u64_gauge("test.gauge.zero").build();
    gauge.record(0u64, &[]);

    let json = export_and_read(
        "gauge_u64_zero",
        "test.gauge.zero",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_u64().unwrap(), 0);
}

#[tokio::test]
async fn test_gauge_u64_max_value() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.u64_gauge("test.gauge.max").build();
    gauge.record(u64::MAX, &[]);

    let json = export_and_read(
        "gauge_u64_max",
        "test.gauge.max",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_u64().unwrap(), u64::MAX);
}

#[tokio::test]
async fn test_exporter_temporality() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("temporality.json");
    let exporter = FileMetricExporter::new(file_path).expect("Failed to create exporter");
    assert_eq!(
        exporter.temporality(),
        Temporality::Cumulative,
        "Exporter should use Cumulative temporality"
    );
}

#[tokio::test]
async fn test_gauge_i64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.i64_gauge("test.gauge.i64").build();
    gauge.record(-12345i64, &[]);

    let json = export_and_read(
        "gauge_i64",
        "test.gauge.i64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_i64().unwrap(), -12345);
}

#[tokio::test]
async fn test_gauge_i64_positive_value() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.i64_gauge("test.gauge.i64.positive").build();
    gauge.record(54321i64, &[]);

    let json = export_and_read(
        "gauge_i64_positive",
        "test.gauge.i64.positive",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_i64().unwrap(), 54321);
}

#[tokio::test]
async fn test_gauge_i64_min_max_values() {
    // min
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.i64_gauge("test.gauge.i64.min").build();
    gauge.record(i64::MIN, &[]);
    let json = export_and_read(
        "gauge_i64_min",
        "test.gauge.i64.min",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_i64().unwrap(), i64::MIN);

    // max
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.i64_gauge("test.gauge.i64.max").build();
    gauge.record(i64::MAX, &[]);
    let json = export_and_read(
        "gauge_i64_max",
        "test.gauge.i64.max",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_i64().unwrap(), i64::MAX);
}

#[tokio::test]
async fn test_gauge_f64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.f64_gauge("test.gauge.f64").build();
    gauge.record(123.456f64, &[]);

    let json = export_and_read(
        "gauge_f64",
        "test.gauge.f64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    let value: f64 = first_value(&json).as_str().unwrap().parse().unwrap();
    assert!((value - 123.456).abs() < 0.001);
}

#[tokio::test]
async fn test_gauge_f64_negative_value() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter.f64_gauge("test.gauge.f64.negative").build();
    gauge.record(-987.654f64, &[]);

    let json = export_and_read(
        "gauge_f64_negative",
        "test.gauge.f64.negative",
        collect_metrics(&provider, &exporter),
    )
    .await;
    let value: f64 = first_value(&json).as_str().unwrap().parse().unwrap();
    assert!((value - -987.654).abs() < 0.001);
}

// ============================================================================
// Sum tests (u64 / i64 / f64) via counters
// ============================================================================

#[tokio::test]
async fn test_sum_u64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let counter = meter.u64_counter("test.sum.u64").build();
    counter.add(54321u64, &[KeyValue::new("test_attr", "test_value")]);

    let json = export_and_read(
        "sum_u64",
        "test.sum.u64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_u64().unwrap(), 54321);
}

#[tokio::test]
async fn test_sum_u64_accumulates() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let counter = meter.u64_counter("test.sum.u64.acc").build();
    counter.add(1000u64, &[]);
    counter.add(2000u64, &[]);
    counter.add(3000u64, &[]);

    let json = export_and_read(
        "sum_u64_acc",
        "test.sum.u64.acc",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_u64().unwrap(), 6000);
}

#[tokio::test]
async fn test_sum_i64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let counter = meter.i64_up_down_counter("test.sum.i64").build();
    counter.add(-12345i64, &[]);

    let json = export_and_read(
        "sum_i64",
        "test.sum.i64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_i64().unwrap(), -12345);
}

#[tokio::test]
async fn test_sum_i64_positive_value() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let counter = meter.i64_up_down_counter("test.sum.i64.positive").build();
    counter.add(99999i64, &[]);

    let json = export_and_read(
        "sum_i64_positive",
        "test.sum.i64.positive",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert_eq!(first_value(&json).as_i64().unwrap(), 99999);
}

#[tokio::test]
async fn test_sum_f64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let counter = meter.f64_counter("test.sum.f64").build();
    counter.add(123.456f64, &[]);

    let json = export_and_read(
        "sum_f64",
        "test.sum.f64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    let value: f64 = first_value(&json).as_str().unwrap().parse().unwrap();
    assert!((value - 123.456).abs() < 0.001);
}

// ============================================================================
// Histogram tests (f64 / u64)
// ============================================================================

#[tokio::test]
async fn test_histogram_f64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let histogram = meter
        .f64_histogram("test.histogram.f64")
        .with_unit("ms")
        .with_boundaries(vec![0.0, 5.0, 10.0, 25.0, 50.0, 75.0, 100.0])
        .build();
    // Sum = 60.0, count = 3
    histogram.record(10.0f64, &[]);
    histogram.record(20.0f64, &[]);
    histogram.record(30.0f64, &[]);

    let json = export_and_read(
        "histogram_f64",
        "test.histogram.f64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    let dp = first_data_point(&json);
    assert_eq!(dp["count"].as_u64().unwrap(), 3);
    let sum: f64 = dp["sum"].as_str().unwrap().parse().unwrap();
    assert!((sum - 60.0).abs() < 0.01);
}

#[tokio::test]
async fn test_histogram_f64_bounds() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let histogram = meter
        .f64_histogram("test.histogram.bounds")
        .with_unit("ms")
        .with_boundaries(vec![0.0, 5.0, 10.0, 25.0, 50.0, 75.0, 100.0])
        .build();
    histogram.record(1.0f64, &[]);

    let json = export_and_read(
        "histogram_f64_bounds",
        "test.histogram.bounds",
        collect_metrics(&provider, &exporter),
    )
    .await;
    let dp = first_data_point(&json);
    let bounds = dp["bounds"].as_array().expect("Should have bounds");
    assert_eq!(bounds.len(), 7, "Should have 7 bounds");
    assert_eq!(bounds[0].as_str().unwrap(), "0");
    assert_eq!(bounds[6].as_str().unwrap(), "100");
}

#[tokio::test]
async fn test_histogram_u64_export_success() {
    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let histogram = meter
        .u64_histogram("test.histogram.u64")
        .with_unit("bytes")
        .with_boundaries(vec![0.0, 10.0, 50.0, 100.0, 500.0])
        .build();
    // Sum = 600, count = 3
    histogram.record(100u64, &[]);
    histogram.record(200u64, &[]);
    histogram.record(300u64, &[]);

    let json = export_and_read(
        "histogram_u64",
        "test.histogram.u64",
        collect_metrics(&provider, &exporter),
    )
    .await;
    let dp = first_data_point(&json);
    assert_eq!(dp["count"].as_u64().unwrap(), 3);
    assert_eq!(dp["sum"].as_u64().unwrap(), 600);

    // Verify bucket_counts are serialized (one entry per bound plus the implicit
    // +inf bucket) and that all three recorded values are accounted for.
    let bucket_counts = dp["bucket_counts"]
        .as_array()
        .expect("Should have bucket_counts");
    assert_eq!(
        bucket_counts.len(),
        6,
        "5 boundaries produce 6 buckets (including +inf)"
    );
    let total: u64 = bucket_counts.iter().map(|c| c.as_u64().unwrap()).sum();
    assert_eq!(total, 3, "All recorded values should land in some bucket");
}

// Note: an i64 histogram is intentionally not tested. The opentelemetry 0.32
// metrics API only exposes `u64_histogram` and `f64_histogram` (histograms are
// for non-negative measurements), so an i64 histogram cannot be produced through
// a real SDK instrument. The generic `AggregatedMetrics::I64` serialization path
// is still covered by the i64 gauge and i64 sum tests above.

// ============================================================================
// Regression test: u64 gauge used by SUBSCRIPTION_LAST_SYNC_TIMESTAMP
// ============================================================================

#[tokio::test]
async fn test_regression_subscription_last_sync_timestamp() {
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::SystemTime::UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64;

    let (provider, exporter) = provider_with_exporter();
    let meter = provider.meter("test_scope");
    let gauge = meter
        .u64_gauge("glide.pubsub.subscription.last_sync_timestamp")
        .build();
    gauge.record(timestamp, &[]);

    let json = export_and_read(
        "subscription_timestamp",
        "glide.pubsub.subscription.last_sync_timestamp",
        collect_metrics(&provider, &exporter),
    )
    .await;
    assert!(
        first_value(&json).as_u64().unwrap() > 0,
        "Timestamp should be positive"
    );
}
