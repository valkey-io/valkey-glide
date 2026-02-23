// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests for Bug Fix: Gauge<u64>, Sum, and Histogram Types Not Handled by File Exporter
//!
//! This test file verifies the fix for the bug where FileMetricExporter::to_json()
//! did not handle Gauge<u64> metrics. The SUBSCRIPTION_LAST_SYNC_TIMESTAMP metric
//! uses u64_gauge(), which returns Gauge<u64>, causing export failures.
//!
//! Fix: Added Gauge<u64>, Sum<u64>, Sum<i64>, Sum<f64>, Histogram<u64>, Histogram<i64>,
//! and Histogram<f64> branches in to_json() method to serialize all metric types.

use opentelemetry::InstrumentationScope;
use opentelemetry::KeyValue;
use opentelemetry_sdk::Resource;
use opentelemetry_sdk::metrics::Temporality;
use opentelemetry_sdk::metrics::data::{
    DataPoint, Gauge, Histogram, Metric, ResourceMetrics, ScopeMetrics, Sum,
};
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

// ============================================================================
// Sum<u64> Tests
// ============================================================================

/// Helper function to create a test ResourceMetrics with a Sum<u64> metric
fn create_sum_u64_metrics(metric_name: &str, value: u64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let sum = Sum {
        data_points: vec![DataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: Some(SystemTime::now()),
            time: Some(SystemTime::now()),
            value,
            exemplars: vec![],
        }],
        temporality: Temporality::Cumulative,
        is_monotonic: true,
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test u64 sum metric".into(),
        unit: "1".into(),
        data: Box::new(sum),
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
async fn test_sum_u64_export_success() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_sum_u64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_sum_u64_metrics("test.sum.u64", 54321);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Sum<u64>: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let metrics_array = json["scope_metrics"][0]["metrics"]
        .as_array()
        .expect("Should have metrics array");
    assert_eq!(metrics_array.len(), 1, "Should have one metric");
    assert_eq!(metrics_array[0]["name"].as_str().unwrap(), "test.sum.u64");

    let data_points = metrics_array[0]["data_points"]
        .as_array()
        .expect("Should have data_points array");
    assert_eq!(data_points.len(), 1, "Should have one data point");
    assert_eq!(
        data_points[0]["value"].as_u64().unwrap(),
        54321,
        "Value should be 54321"
    );
}

#[tokio::test]
async fn test_sum_u64_large_values() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_sum_u64_large.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let large_value = u64::MAX - 1000;
    let mut metrics = create_sum_u64_metrics("test.sum.u64.large", large_value);

    let result = exporter.export(&mut metrics).await;
    assert!(result.is_ok(), "Export should succeed for large u64 sum");

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_u64()
        .expect("Value should be u64");
    assert_eq!(
        value, large_value,
        "Large u64 sum value should be preserved"
    );
}

// ============================================================================
// Sum<i64> Tests
// ============================================================================

/// Helper function to create a test ResourceMetrics with a Sum<i64> metric
fn create_sum_i64_metrics(metric_name: &str, value: i64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let sum = Sum {
        data_points: vec![DataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: Some(SystemTime::now()),
            time: Some(SystemTime::now()),
            value,
            exemplars: vec![],
        }],
        temporality: Temporality::Cumulative,
        is_monotonic: false,
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test i64 sum metric".into(),
        unit: "1".into(),
        data: Box::new(sum),
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
async fn test_sum_i64_export_success() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_sum_i64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_sum_i64_metrics("test.sum.i64", -12345);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Sum<i64>: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_i64()
        .expect("Value should be i64");
    assert_eq!(value, -12345, "Negative i64 value should be preserved");
}

#[tokio::test]
async fn test_sum_i64_positive_and_negative() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_sum_i64_mixed.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    // Test with positive value
    let mut metrics = create_sum_i64_metrics("test.sum.i64.positive", 99999);
    let result = exporter.export(&mut metrics).await;
    assert!(result.is_ok(), "Export should succeed for positive i64");

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");
    let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_i64()
        .expect("Value should be i64");
    assert_eq!(value, 99999, "Positive i64 value should be preserved");
}

// ============================================================================
// Sum<f64> Tests
// ============================================================================

/// Helper function to create a test ResourceMetrics with a Sum<f64> metric
fn create_sum_f64_metrics(metric_name: &str, value: f64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let sum = Sum {
        data_points: vec![DataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: Some(SystemTime::now()),
            time: Some(SystemTime::now()),
            value,
            exemplars: vec![],
        }],
        temporality: Temporality::Cumulative,
        is_monotonic: true,
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test f64 sum metric".into(),
        unit: "1".into(),
        data: Box::new(sum),
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
async fn test_sum_f64_export_success() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_sum_f64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_sum_f64_metrics("test.sum.f64", 123.456);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Sum<f64>: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let value_str = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_str()
        .expect("Value should be string for f64");
    let value: f64 = value_str.parse().expect("Should parse as f64");
    assert!(
        (value - 123.456).abs() < 0.001,
        "f64 value should be preserved"
    );
}

#[tokio::test]
async fn test_sum_f64_special_values() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_sum_f64_special.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    // Test with very small decimal
    let mut metrics = create_sum_f64_metrics("test.sum.f64.small", 0.000001);
    let result = exporter.export(&mut metrics).await;
    assert!(result.is_ok(), "Export should succeed for small f64");

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");
    let value_str = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
        .as_str()
        .expect("Value should be string");
    let value: f64 = value_str.parse().expect("Should parse as f64");
    assert!(
        (value - 0.000001).abs() < 0.0000001,
        "Small f64 value should be preserved"
    );
}

// ============================================================================
// Histogram<f64> Tests
// ============================================================================

/// Helper function to create a test ResourceMetrics with a Histogram<f64> metric
fn create_histogram_f64_metrics(metric_name: &str, sum: f64, count: u64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let histogram = Histogram {
        data_points: vec![opentelemetry_sdk::metrics::data::HistogramDataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: SystemTime::now(),
            time: SystemTime::now(),
            count,
            bounds: vec![0.0, 5.0, 10.0, 25.0, 50.0, 75.0, 100.0],
            bucket_counts: vec![1, 2, 3, 4, 5, 6, 7, 8],
            min: Some(0.5),
            max: Some(99.9),
            sum,
            exemplars: vec![],
        }],
        temporality: Temporality::Cumulative,
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test f64 histogram metric".into(),
        unit: "ms".into(),
        data: Box::new(histogram),
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
async fn test_histogram_f64_export_success() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_histogram_f64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_histogram_f64_metrics("test.histogram.f64", 1234.56, 100);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Histogram<f64>: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let data_points = json["scope_metrics"][0]["metrics"][0]["data_points"]
        .as_array()
        .expect("Should have data_points array");
    assert_eq!(data_points.len(), 1, "Should have one data point");

    let count = data_points[0]["count"]
        .as_u64()
        .expect("Count should be u64");
    assert_eq!(count, 100, "Count should be 100");

    let sum_str = data_points[0]["sum"]
        .as_str()
        .expect("Sum should be string for f64");
    let sum: f64 = sum_str.parse().expect("Should parse as f64");
    assert!((sum - 1234.56).abs() < 0.01, "Sum should be preserved");

    let bucket_counts = data_points[0]["bucket_counts"]
        .as_array()
        .expect("Should have bucket_counts");
    assert_eq!(bucket_counts.len(), 8, "Should have 8 bucket counts");
}

#[tokio::test]
async fn test_histogram_f64_bounds_and_buckets() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_histogram_f64_bounds.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_histogram_f64_metrics("test.histogram.bounds", 500.0, 50);

    let result = exporter.export(&mut metrics).await;
    assert!(result.is_ok(), "Export should succeed");

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];

    let bounds = data_point["bounds"].as_array().expect("Should have bounds");
    assert_eq!(bounds.len(), 7, "Should have 7 bounds");

    // Verify bounds are serialized as strings
    assert_eq!(bounds[0].as_str().unwrap(), "0");
    assert_eq!(bounds[1].as_str().unwrap(), "5");
    assert_eq!(bounds[6].as_str().unwrap(), "100");
}

// ============================================================================
// Histogram<u64> Tests
// ============================================================================

/// Helper function to create a test ResourceMetrics with a Histogram<u64> metric
fn create_histogram_u64_metrics(metric_name: &str, sum: u64, count: u64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let histogram = Histogram {
        data_points: vec![opentelemetry_sdk::metrics::data::HistogramDataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: SystemTime::now(),
            time: SystemTime::now(),
            count,
            bounds: vec![0.0, 10.0, 50.0, 100.0, 500.0],
            bucket_counts: vec![5, 10, 15, 20, 25, 30],
            min: Some(1),
            max: Some(999),
            sum,
            exemplars: vec![],
        }],
        temporality: Temporality::Cumulative,
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test u64 histogram metric".into(),
        unit: "bytes".into(),
        data: Box::new(histogram),
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
async fn test_histogram_u64_export_success() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_histogram_u64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_histogram_u64_metrics("test.histogram.u64", 50000, 200);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Histogram<u64>: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];

    let count = data_point["count"].as_u64().expect("Count should be u64");
    assert_eq!(count, 200, "Count should be 200");

    let sum = data_point["sum"].as_u64().expect("Sum should be u64");
    assert_eq!(sum, 50000, "Sum should be 50000");
}

#[tokio::test]
async fn test_histogram_u64_large_values() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_histogram_u64_large.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let large_sum = u64::MAX / 2;
    let mut metrics = create_histogram_u64_metrics("test.histogram.u64.large", large_sum, 1000);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for large u64 histogram"
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let sum = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["sum"]
        .as_u64()
        .expect("Sum should be u64");
    assert_eq!(sum, large_sum, "Large u64 sum should be preserved");
}

// ============================================================================
// Histogram<i64> Tests
// ============================================================================

/// Helper function to create a test ResourceMetrics with a Histogram<i64> metric
fn create_histogram_i64_metrics(metric_name: &str, sum: i64, count: u64) -> ResourceMetrics {
    let resource = Resource::new(vec![KeyValue::new("service.name", "test_service")]);

    let histogram = Histogram {
        data_points: vec![opentelemetry_sdk::metrics::data::HistogramDataPoint {
            attributes: vec![KeyValue::new("test_attr", "test_value")],
            start_time: SystemTime::now(),
            time: SystemTime::now(),
            count,
            bounds: vec![0.0, 10.0, 50.0, 100.0],
            bucket_counts: vec![3, 7, 12, 18, 25],
            min: Some(-50),
            max: Some(150),
            sum,
            exemplars: vec![],
        }],
        temporality: Temporality::Cumulative,
    };

    let metric = Metric {
        name: metric_name.to_string().into(),
        description: "Test i64 histogram metric".into(),
        unit: "delta".into(),
        data: Box::new(histogram),
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
async fn test_histogram_i64_export_success() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_histogram_i64.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_histogram_i64_metrics("test.histogram.i64", -5000, 75);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for Histogram<i64>: {:?}",
        result.err()
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];

    let count = data_point["count"].as_u64().expect("Count should be u64");
    assert_eq!(count, 75, "Count should be 75");

    let sum = data_point["sum"].as_i64().expect("Sum should be i64");
    assert_eq!(sum, -5000, "Negative i64 sum should be preserved");
}

#[tokio::test]
async fn test_histogram_i64_positive_sum() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("metrics_histogram_i64_positive.json");

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");

    let mut metrics = create_histogram_i64_metrics("test.histogram.i64.positive", 8888, 150);

    let result = exporter.export(&mut metrics).await;
    assert!(
        result.is_ok(),
        "Export should succeed for positive i64 histogram"
    );

    let content = fs::read_to_string(&file_path).expect("Failed to read metrics file");
    let json: serde_json::Value = serde_json::from_str(&content).expect("Should be valid JSON");

    let sum = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["sum"]
        .as_i64()
        .expect("Sum should be i64");
    assert_eq!(sum, 8888, "Positive i64 sum should be preserved");
}
