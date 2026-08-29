// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use opentelemetry_proto::tonic::collector::metrics::v1::ExportMetricsServiceRequest;
use opentelemetry_proto::tonic::metrics::v1::{AggregationTemporality, metric};
use prost::Message;
use std::collections::HashMap;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::mpsc;
use std::thread;
use std::time::Duration;
use telemetrylib::{
    GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder, GlideOpenTelemetryMetricsTemporality,
    GlideOpenTelemetrySignalsExporter,
};

struct CapturedRequest {
    headers: String,
    body: Vec<u8>,
}

fn read_request(mut stream: TcpStream) -> CapturedRequest {
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .expect("failed to set read timeout");

    let mut request = Vec::new();
    let mut buffer = [0_u8; 4096];
    let header_end = loop {
        let count = stream.read(&mut buffer).expect("failed to read request");
        assert!(
            count > 0,
            "connection closed before request headers arrived"
        );
        request.extend_from_slice(&buffer[..count]);
        if let Some(position) = request.windows(4).position(|bytes| bytes == b"\r\n\r\n") {
            break position + 4;
        }
    };

    let headers =
        String::from_utf8(request[..header_end].to_vec()).expect("headers were not valid UTF-8");
    let content_length = headers
        .lines()
        .find_map(|line| {
            line.to_ascii_lowercase()
                .strip_prefix("content-length:")
                .map(str::trim)
                .map(str::parse::<usize>)
        })
        .expect("request did not contain Content-Length")
        .expect("Content-Length was not a number");

    while request.len() - header_end < content_length {
        let count = stream
            .read(&mut buffer)
            .expect("failed to read request body");
        assert!(count > 0, "connection closed before request body arrived");
        request.extend_from_slice(&buffer[..count]);
    }

    stream
        .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        .expect("failed to write response");

    CapturedRequest {
        headers,
        body: request[header_end..header_end + content_length].to_vec(),
    }
}

#[tokio::test(flavor = "multi_thread")]
async fn programmatic_headers_and_delta_temporality_are_exported() {
    let listener = TcpListener::bind("127.0.0.1:0").expect("failed to bind test collector");
    let address = listener
        .local_addr()
        .expect("failed to get collector address");
    let (sender, receiver) = mpsc::sync_channel(1);

    let collector = thread::spawn(move || {
        let (stream, _) = listener.accept().expect("failed to accept OTLP request");
        sender
            .send(read_request(stream))
            .expect("failed to return captured request");
    });

    let config = GlideOpenTelemetryConfigBuilder::default()
        .with_flush_interval(Duration::from_millis(20))
        .with_metrics_exporter_config(
            GlideOpenTelemetrySignalsExporter::Http(format!("http://{address}/v1/metrics")),
            HashMap::from([(
                "X-Test-Routing-Header".to_string(),
                "test%2Fvalue".to_string(),
            )]),
            Some(GlideOpenTelemetryMetricsTemporality::Delta),
        )
        .build();

    GlideOpenTelemetry::initialise(config).expect("failed to initialize OpenTelemetry");
    GlideOpenTelemetry::record_timeout_error().expect("failed to record test metric");

    let request = receiver
        .recv_timeout(Duration::from_secs(5))
        .expect("timed out waiting for OTLP metrics export");
    collector.join().expect("test collector panicked");

    assert!(
        request
            .headers
            .to_ascii_lowercase()
            .contains("x-test-routing-header: test%2fvalue\r\n"),
        "custom routing header was not exported: {}",
        request.headers
    );

    let export = ExportMetricsServiceRequest::decode(request.body.as_slice())
        .expect("failed to decode OTLP protobuf request");
    let timeout_metric = export
        .resource_metrics
        .iter()
        .flat_map(|resource| &resource.scope_metrics)
        .flat_map(|scope| &scope.metrics)
        .find(|metric| metric.name == "glide.timeout_errors")
        .expect("timeout metric was not exported");
    let sum = match timeout_metric.data.as_ref() {
        Some(metric::Data::Sum(sum)) => sum,
        data => panic!("timeout metric was not exported as a sum: {data:?}"),
    };
    assert_eq!(
        sum.aggregation_temporality,
        AggregationTemporality::Delta as i32
    );
}
