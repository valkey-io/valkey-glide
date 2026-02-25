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
use std::path::PathBuf;
use std::time::SystemTime;
use telemetrylib::FileMetricExporter;
use tempfile::TempDir;

// ============================================================================
// Generic Test Framework
// ============================================================================

/// Generic test runner for metric export tests
async fn run_metric_export_test<F>(
    test_name: &str,
    metric_name: &str,
    metrics: ResourceMetrics,
    validator: F,
) where
    F: FnOnce(&serde_json::Value),
{
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join(format!("{}.json", test_name));
    let mut metrics = metrics;

    let exporter = FileMetricExporter::new(file_path.clone()).expect("Failed to create exporter");
    let result = exporter.export(&mut metrics).await;
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

    // Verify metric structure
    let metrics_array = json["scope_metrics"][0]["metrics"]
        .as_array()
        .expect("Should have metrics array");
    assert_eq!(metrics_array.len(), 1, "Should have one metric");
    assert_eq!(
        metrics_array[0]["name"].as_str().unwrap(),
        metric_name,
        "Metric name should match"
    );

    validator(&json);
}

/// Validator for u64 values
fn validate_u64(expected: u64) -> impl FnOnce(&serde_json::Value) {
    move |json: &serde_json::Value| {
        let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
            .as_u64()
            .expect("Value should be u64");
        assert_eq!(value, expected, "u64 value should be {}", expected);
    }
}

/// Validator for i64 values
fn validate_i64(expected: i64) -> impl FnOnce(&serde_json::Value) {
    move |json: &serde_json::Value| {
        let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
            .as_i64()
            .expect("Value should be i64");
        assert_eq!(value, expected, "i64 value should be {}", expected);
    }
}

/// Validator for f64 values
fn validate_f64(expected: f64, tolerance: f64) -> impl FnOnce(&serde_json::Value) {
    move |json: &serde_json::Value| {
        let value_str = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
            .as_str()
            .expect("Value should be string for f64");
        let value: f64 = value_str.parse().expect("Should parse as f64");
        assert!(
            (value - expected).abs() < tolerance,
            "f64 value {} should be within {} of {}",
            value,
            tolerance,
            expected
        );
    }
}

/// Validator for histogram with u64 sum
fn validate_histogram_u64(
    expected_sum: u64,
    expected_count: u64,
) -> impl FnOnce(&serde_json::Value) {
    move |json: &serde_json::Value| {
        let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];
        let count = data_point["count"].as_u64().expect("Count should be u64");
        let sum = data_point["sum"].as_u64().expect("Sum should be u64");
        assert_eq!(count, expected_count, "Count should be {}", expected_count);
        assert_eq!(sum, expected_sum, "Sum should be {}", expected_sum);
    }
}

/// Validator for histogram with i64 sum
fn validate_histogram_i64(
    expected_sum: i64,
    expected_count: u64,
) -> impl FnOnce(&serde_json::Value) {
    move |json: &serde_json::Value| {
        let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];
        let count = data_point["count"].as_u64().expect("Count should be u64");
        let sum = data_point["sum"].as_i64().expect("Sum should be i64");
        assert_eq!(count, expected_count, "Count should be {}", expected_count);
        assert_eq!(sum, expected_sum, "Sum should be {}", expected_sum);
    }
}

/// Validator for histogram with f64 sum
fn validate_histogram_f64(
    expected_sum: f64,
    expected_count: u64,
    tolerance: f64,
) -> impl FnOnce(&serde_json::Value) {
    move |json: &serde_json::Value| {
        let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];
        let count = data_point["count"].as_u64().expect("Count should be u64");
        let sum_str = data_point["sum"]
            .as_str()
            .expect("Sum should be string for f64");
        let sum: f64 = sum_str.parse().expect("Should parse as f64");
        assert_eq!(count, expected_count, "Count should be {}", expected_count);
        assert!(
            (sum - expected_sum).abs() < tolerance,
            "Sum {} should be within {} of {}",
            sum,
            tolerance,
            expected_sum
        );
    }
}

// ============================================================================
// Metric Creator Functions
// ============================================================================

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

fn create_gauge_i64_metrics(metric_name: &str, value: i64) -> ResourceMetrics {
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
        description: "Test i64 gauge metric".into(),
        unit: "1".into(),
        data: Box::new(gauge),
    };
    let scope = InstrumentationScope::builder("test_scope")
        .with_version("1.0.0")
        .build();
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

fn create_gauge_f64_metrics(metric_name: &str, value: f64) -> ResourceMetrics {
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
        description: "Test f64 gauge metric".into(),
        unit: "1".into(),
        data: Box::new(gauge),
    };
    let scope = InstrumentationScope::builder("test_scope")
        .with_version("1.0.0")
        .build();
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

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
    ResourceMetrics {
        resource,
        scope_metrics: vec![ScopeMetrics {
            scope,
            metrics: vec![metric],
        }],
    }
}

// ============================================================================
// Gauge<u64> Tests
// ============================================================================

#[tokio::test]
async fn test_gauge_u64_export_success() {
    run_metric_export_test(
        "gauge_u64",
        "test.gauge.u64",
        create_gauge_u64_metrics("test.gauge.u64", 12345),
        validate_u64(12345),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_u64_large_values() {
    let timestamp = 1708531200000u64;
    run_metric_export_test(
        "gauge_u64_large",
        "subscription.last_sync_timestamp",
        create_gauge_u64_metrics("subscription.last_sync_timestamp", timestamp),
        validate_u64(timestamp),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_u64_zero_value() {
    run_metric_export_test(
        "gauge_u64_zero",
        "test.gauge.zero",
        create_gauge_u64_metrics("test.gauge.zero", 0),
        validate_u64(0),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_u64_max_value() {
    run_metric_export_test(
        "gauge_u64_max",
        "test.gauge.max",
        create_gauge_u64_metrics("test.gauge.max", u64::MAX),
        validate_u64(u64::MAX),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_u64_temporality() {
    let temp_dir = TempDir::new().expect("Failed to create temp dir");
    let file_path = temp_dir.path().join("temporality.json");
    let exporter = FileMetricExporter::new(file_path).expect("Failed to create exporter");
    assert_eq!(
        exporter.temporality(),
        Temporality::Cumulative,
        "Exporter should use Cumulative temporality"
    );
}

// ============================================================================
// Gauge<i64> Tests
// ============================================================================

#[tokio::test]
async fn test_gauge_i64_export_success() {
    run_metric_export_test(
        "gauge_i64",
        "test.gauge.i64",
        create_gauge_i64_metrics("test.gauge.i64", -12345),
        validate_i64(-12345),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_i64_positive_value() {
    run_metric_export_test(
        "gauge_i64_positive",
        "test.gauge.i64.positive",
        create_gauge_i64_metrics("test.gauge.i64.positive", 54321),
        validate_i64(54321),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_i64_zero_value() {
    run_metric_export_test(
        "gauge_i64_zero",
        "test.gauge.i64.zero",
        create_gauge_i64_metrics("test.gauge.i64.zero", 0),
        validate_i64(0),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_i64_min_max_values() {
    // Test min value
    run_metric_export_test(
        "gauge_i64_min",
        "test.gauge.i64.min",
        create_gauge_i64_metrics("test.gauge.i64.min", i64::MIN),
        validate_i64(i64::MIN),
    )
    .await;

    // Test max value
    run_metric_export_test(
        "gauge_i64_max",
        "test.gauge.i64.max",
        create_gauge_i64_metrics("test.gauge.i64.max", i64::MAX),
        validate_i64(i64::MAX),
    )
    .await;
}

// ============================================================================
// Gauge<f64> Tests
// ============================================================================

#[tokio::test]
async fn test_gauge_f64_export_success() {
    run_metric_export_test(
        "gauge_f64",
        "test.gauge.f64",
        create_gauge_f64_metrics("test.gauge.f64", 123.456),
        validate_f64(123.456, 0.001),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_f64_negative_value() {
    run_metric_export_test(
        "gauge_f64_negative",
        "test.gauge.f64.negative",
        create_gauge_f64_metrics("test.gauge.f64.negative", -987.654),
        validate_f64(-987.654, 0.001),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_f64_zero_value() {
    run_metric_export_test(
        "gauge_f64_zero",
        "test.gauge.f64.zero",
        create_gauge_f64_metrics("test.gauge.f64.zero", 0.0),
        validate_f64(0.0, 0.0001),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_f64_small_value() {
    run_metric_export_test(
        "gauge_f64_small",
        "test.gauge.f64.small",
        create_gauge_f64_metrics("test.gauge.f64.small", 0.000001),
        validate_f64(0.000001, 0.0000001),
    )
    .await;
}

#[tokio::test]
async fn test_gauge_f64_large_value() {
    run_metric_export_test(
        "gauge_f64_large",
        "test.gauge.f64.large",
        create_gauge_f64_metrics("test.gauge.f64.large", 1234567890.123456),
        validate_f64(1234567890.123456, 0.001),
    )
    .await;
}

// ============================================================================
// Sum<u64> Tests
// ============================================================================

#[tokio::test]
async fn test_sum_u64_export_success() {
    run_metric_export_test(
        "sum_u64",
        "test.sum.u64",
        create_sum_u64_metrics("test.sum.u64", 54321),
        validate_u64(54321),
    )
    .await;
}

#[tokio::test]
async fn test_sum_u64_large_values() {
    let large_value = u64::MAX - 1000;
    run_metric_export_test(
        "sum_u64_large",
        "test.sum.u64.large",
        create_sum_u64_metrics("test.sum.u64.large", large_value),
        validate_u64(large_value),
    )
    .await;
}

// ============================================================================
// Sum<i64> Tests
// ============================================================================

#[tokio::test]
async fn test_sum_i64_export_success() {
    run_metric_export_test(
        "sum_i64",
        "test.sum.i64",
        create_sum_i64_metrics("test.sum.i64", -12345),
        validate_i64(-12345),
    )
    .await;
}

#[tokio::test]
async fn test_sum_i64_positive_value() {
    run_metric_export_test(
        "sum_i64_positive",
        "test.sum.i64.positive",
        create_sum_i64_metrics("test.sum.i64.positive", 99999),
        validate_i64(99999),
    )
    .await;
}

// ============================================================================
// Sum<f64> Tests
// ============================================================================

#[tokio::test]
async fn test_sum_f64_export_success() {
    run_metric_export_test(
        "sum_f64",
        "test.sum.f64",
        create_sum_f64_metrics("test.sum.f64", 123.456),
        validate_f64(123.456, 0.001),
    )
    .await;
}

#[tokio::test]
async fn test_sum_f64_small_value() {
    run_metric_export_test(
        "sum_f64_small",
        "test.sum.f64.small",
        create_sum_f64_metrics("test.sum.f64.small", 0.000001),
        validate_f64(0.000001, 0.0000001),
    )
    .await;
}

// ============================================================================
// Histogram<f64> Tests
// ============================================================================

#[tokio::test]
async fn test_histogram_f64_export_success() {
    run_metric_export_test(
        "histogram_f64",
        "test.histogram.f64",
        create_histogram_f64_metrics("test.histogram.f64", 1234.56, 100),
        validate_histogram_f64(1234.56, 100, 0.01),
    )
    .await;
}

#[tokio::test]
async fn test_histogram_f64_bounds() {
    run_metric_export_test(
        "histogram_f64_bounds",
        "test.histogram.bounds",
        create_histogram_f64_metrics("test.histogram.bounds", 500.0, 50),
        |json| {
            let data_point = &json["scope_metrics"][0]["metrics"][0]["data_points"][0];
            let bounds = data_point["bounds"].as_array().expect("Should have bounds");
            assert_eq!(bounds.len(), 7, "Should have 7 bounds");
            assert_eq!(bounds[0].as_str().unwrap(), "0");
            assert_eq!(bounds[6].as_str().unwrap(), "100");
        },
    )
    .await;
}

// ============================================================================
// Histogram<u64> Tests
// ============================================================================

#[tokio::test]
async fn test_histogram_u64_export_success() {
    run_metric_export_test(
        "histogram_u64",
        "test.histogram.u64",
        create_histogram_u64_metrics("test.histogram.u64", 50000, 200),
        validate_histogram_u64(50000, 200),
    )
    .await;
}

#[tokio::test]
async fn test_histogram_u64_large_values() {
    let large_sum = u64::MAX / 2;
    run_metric_export_test(
        "histogram_u64_large",
        "test.histogram.u64.large",
        create_histogram_u64_metrics("test.histogram.u64.large", large_sum, 1000),
        validate_histogram_u64(large_sum, 1000),
    )
    .await;
}

// ============================================================================
// Histogram<i64> Tests
// ============================================================================

#[tokio::test]
async fn test_histogram_i64_export_success() {
    run_metric_export_test(
        "histogram_i64",
        "test.histogram.i64",
        create_histogram_i64_metrics("test.histogram.i64", -5000, 75),
        validate_histogram_i64(-5000, 75),
    )
    .await;
}

#[tokio::test]
async fn test_histogram_i64_positive_sum() {
    run_metric_export_test(
        "histogram_i64_positive",
        "test.histogram.i64.positive",
        create_histogram_i64_metrics("test.histogram.i64.positive", 8888, 150),
        validate_histogram_i64(8888, 150),
    )
    .await;
}

// ============================================================================
// Regression Test
// ============================================================================

#[tokio::test]
async fn test_regression_subscription_last_sync_timestamp() {
    let timestamp = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64;

    run_metric_export_test(
        "subscription_timestamp",
        "glide.pubsub.subscription.last_sync_timestamp",
        create_gauge_u64_metrics("glide.pubsub.subscription.last_sync_timestamp", timestamp),
        |json| {
            let value = json["scope_metrics"][0]["metrics"][0]["data_points"][0]["value"]
                .as_u64()
                .expect("Value should be u64");
            assert!(value > 0, "Timestamp should be positive");
        },
    )
    .await;
}
