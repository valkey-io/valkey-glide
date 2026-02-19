// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests for Bug Fix: Gauge<u64> Not Handled by File Exporter
//!
//! This test file verifies the fix for the bug where FileMetricExporter::to_json()
//! did not handle Gauge<u64> metrics. The SUBSCRIPTION_LAST_SYNC_TIMESTAMP metric
//! uses u64_gauge(), which returns Gauge<u64>, causing export failures.
//!
//! Fix: Added Gauge<u64> branch in to_json() method to serialize u64 gauge metrics.

use opentelemetry::InstrumentationScope;
use opentelemetry::KeyValue;
use opentelemetry_sdk::Resource;
use opentelemetry_sdk::metrics::Temporality;
use opentelemetry_sdk::metrics::data::{DataPoint, Gauge, Metric, ResourceMetrics, ScopeMetrics};
use opentelemetry_sdk::metrics::exporter::PushMetricExporter;
use std::fs;
use std::time::SystemTime;
use telemetrylib::FileMetricExporter;
use tempfile::TempDir;

/// Helper function to create a test ResourceMetrics with a Gauge<u64> metric
fn create_gauge_u64_metrics(metric_name: &str, value: u64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let gauge = Gauge {
        data_points: vec![DataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: None,
            time: Some(SystemTime::now()),
            value,
            exemplars: vec![],
        }],
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test u64 gauge metric".into(),
        unit: "1".into(),
        data: Box::new(gauge),
    };

    let scope = InstrumentationScope::builder("test_scope")
        .with_version("1.0.0")
        .build();

    let scope_metrics = ScopeMetrics {
        scope,
        metrics: vec![metric],
    };

    ResourceMetrics {
        resource,
        scope_metrics: vec![scope_metrics],
    }
}

#[tokio::test]
async fn test_gauge_u64_export_success() {
    // Create a temporary directory for test output
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_u64.json");

    // Create the file exporter
    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    // Create metrics with Gauge<u64>
    let mut metrics = create_gauge_u64_metrics("test.gauge.u64", 12345);

    // Export the metrics - this should succeed with the fix
    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Gauge<u64>: {:?}",
        result.err()
    );

    // Verify the file was created and contains valid JSON
    assert!(file_path.exists(), "Metrics file should be created");

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    assert!(!content.is_empty(), "Metrics file should not be empty");

    // Parse the JSON to verify it's valid
    let json: serde_json::Value =
        serde_json::from_str(&content).expect("Metrics file should contain valid JSON");

    // Verify the metric name is present
    let metrics_array = json["scope_metrics"][0]["metrics"]
        .as_array()
        .expect("Should have metrics array");
    assert_eq!(metrics_array.len(), 1, "Should have one metric");
    assert_eq!(metrics_array[0]["name"].as_str().unwrap(), "test.gauge.u64");

    // Verify the value is present and correct
    let data_points = metrics_array[0]["data_points"]
        .as_array()
        .expect("Should have data_points array");
    assert_eq!(data_points.len(), 1, "Should have one data point");
    assert_eq!(
        data_points[0]["value"].as_u64().unwrap(),
        12345,
        "Value should be 12345"
    );
}

#[tokio::test]
async fn test_gauge_u64_large_values() {
    // Test with large u64 values (simulating timestamps)
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_large_u64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    // Use a large timestamp value similar to SUBSCRIPTION_LAST_SYNC_TIMESTAMP
    let timestamp_value = 1708531200000u64; // Example timestamp in milliseconds
    let mut metrics = create_gauge_u64_metrics("subscription.last_sync_timestamp", timestamp_value);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for large u64 values: {:?}",
        result.err()
    );

    // Verify the value is correctly serialized
    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_u64()
        .expect("Value should be u64");
    assert_eq!(
        value, timestamp_value,
        "Large u64 value should be preserved"
    );
}

#[tokio::test]
async fn test_gauge_u64_zero_value() {
    // Test with zero value
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_zero_u64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_gauge_u64_metrics("test.gauge.zero", 0);

    let result = exporter.export(&mut metrics).await;
    assert!(result.is_ok(), "Export should succeed for zero value");

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_u64()
        .expect("Value should be u64");
    assert_eq!(value, 0, "Zero value should be preserved");
}

#[tokio::test]
async fn test_gauge_u64_max_value() {
    // Test with maximum u64 value
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_max_u64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_gauge_u64_metrics("test.gauge.max", u64::MAX);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for max u64 value: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_u64()
        .expect("Value should be u64");
    assert_eq!(value, u64::MAX, "Max u64 value should be preserved");
}

#[tokio::test]
async fn test_gauge_u64_with_multiple_data_points() {
    // Test Gauge<u64> with multiple data points
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_multi_points.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let gauge = Gauge {
        data_points: vec![
            DataPoint {
                attributes: vec![KeyValue::new("instance", "1")],
                start_time: None,
                time: Some(SystemTime::now()),
                value: 100u64,
                exemplars: vec![],
            },
            DataPoint {
                attributes: vec![KeyValue::new("instance", "2")],
                start_time: None,
                time: Some(SystemTime::now()),
                value: 200u64,
                exemplars: vec![],
            },
            DataPoint {
                attributes: vec![KeyValue::new("instance", "3")],
                start_time: None,
                time: Some(SystemTime::now()),
                value: 300u64,
                exemplars: vec![],
            },
        ],
    };

    let metric = Metric {
        name: "test.gauge.multi".to_string().into(),
        description: "Test multi-point gauge".into(),
        unit: "1".into(),
        data: Box::new(gauge),
    };

    let scope = InstrumentationScope::builder("test_scope")
        .with_version("1.0.0")
        .build();

    let scope_metrics = ScopeMetrics {
        scope,
        metrics: vec![metric],
    };

    let mut metrics = ResourceMetrics {
        resource,
        scope_metrics: vec![scope_metrics],
    };

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for multiple data points"
    );

    // Verify all data points were exported
    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let data_points = json["scope_metrics"][0]["metrics"][0]["data_points"]
        .as_array()
        .expect("Should have data_points array");
    assert_eq!(data_points.len(), 3, "Should have 3 data points");

    // Verify values
    assert_eq!(data_points[0]["value"].as_u64().unwrap(), 100);
    assert_eq!(data_points[1]["value"].as_u64().unwrap(), 200);
    assert_eq!(data_points[2]["value"].as_u64().unwrap(), 300);
}

#[tokio::test]
async fn test_gauge_u64_temporality() {
    // Verify that the exporter reports correct temporality
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_temporality.json");

    let exporter = FileMetricExporter::new(file_path).expect("Failed to create exporter");

    // Verify temporality is Cumulative
    assert_eq!(
        exporter.temporality(),
        Temporality::Cumulative,
        "Exporter should use Cumulative temporality"
    );
}

#[tokio::test]
async fn test_regression_subscription_last_sync_timestamp() {
    // Regression test for the actual bug: SUBSCRIPTION_LAST_SYNC_TIMESTAMP metric
    // This metric uses u64_gauge() which returns Gauge<u64>
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("subscription_metrics.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    // Simulate the SUBSCRIPTION_LAST_SYNC_TIMESTAMP metric
    let mut metrics = create_gauge_u64_metrics(
        "glide.pubsub.subscription.last_sync_timestamp",
        SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_millis() as u64,
    );

    // This should succeed with the fix (previously would fail)
    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for SUBSCRIPTION_LAST_SYNC_TIMESTAMP metric: {:?}",
        result.err()
    );

    // Verify the metric was written correctly
    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let metric_name = json["scope_metrics"][0]["metrics"][0]["name"]
        .as_str()
        .expect("Should have metric name");
    assert_eq!(
        metric_name, "glide.pubsub.subscription.last_sync_timestamp",
        "Metric name should match"
    );

    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_u64()
        .expect("Value should be u64");
    assert!(value > 0, "Timestamp value should be positive");
}
