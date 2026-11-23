// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! `glide.timeout_errors` counts every command that fails because its request timeout elapsed.
//!
//! A command with a request timeout races two timers of the same length: the timeout watchdog and the
//! connection's own response timeout. Whichever fires first fails the command, and the counter must not
//! depend on which one that was. On macOS the connection's timer usually wins (the watchdog thread wakes
//! later under timer coalescing), so before the fix this test counted few or none of the timeouts there.
//!
//! A file of its own because OpenTelemetry is initialised once per process.

mod constants;
mod utilities;

use glide_telemetry::{
    GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder, GlideOpenTelemetrySignalsExporter,
};
use std::path::Path;
use std::time::Duration;
use utilities::*;

const TIMEOUTS: u64 = 5;
const FLUSH_INTERVAL: Duration = Duration::from_millis(100);

/// The last value exported for `glide.timeout_errors`, a cumulative counter, or 0 if none was exported.
fn exported_timeout_errors(metrics_file: &Path) -> u64 {
    let content = std::fs::read_to_string(metrics_file).unwrap_or_default();
    content
        .lines()
        .filter(|line| !line.trim().is_empty())
        .filter_map(|line| serde_json::from_str::<serde_json::Value>(line).ok())
        .flat_map(|json| {
            json["scope_metrics"]
                .as_array()
                .into_iter()
                .flatten()
                .flat_map(|scope| scope["metrics"].as_array().into_iter().flatten().cloned())
                .collect::<Vec<_>>()
        })
        .filter(|metric| metric["name"] == "glide.timeout_errors")
        .filter_map(|metric| metric["data_points"][0]["value"].as_u64())
        .next_back()
        .unwrap_or(0)
}

#[test]
fn every_request_timeout_is_counted_whichever_timer_fires_first() {
    let metrics_file =
        std::env::temp_dir().join(format!("glide-timeout-metric-{}.json", std::process::id()));
    let _ = std::fs::remove_file(&metrics_file);
    GlideOpenTelemetry::initialise(
        GlideOpenTelemetryConfigBuilder::default()
            .with_flush_interval(FLUSH_INTERVAL)
            .with_metrics_exporter(GlideOpenTelemetrySignalsExporter::File(
                metrics_file.clone(),
            ))
            .build(),
    )
    .unwrap();

    block_on_all(async {
        let server =
            BackingServer::Standalone(Some(RedisServer::new(ServerType::Tcp { tls: false })));
        let mut client = create_client(
            &server,
            TestConfiguration {
                request_timeout: Some(200), // milliseconds
                ..Default::default()
            },
        )
        .await;

        for _ in 0..TIMEOUTS {
            // The server stops answering for longer than the request timeout; the pause's own reply comes first.
            let mut pause = redis::cmd("CLIENT");
            pause.arg("PAUSE").arg(400).arg("ALL");
            client.send_command(&mut pause, None).await.unwrap();

            let mut get = redis::cmd("GET");
            get.arg("timeout-metric");
            let err = client.send_command(&mut get, None).await.unwrap_err();
            assert!(err.is_timeout(), "{err}");

            tokio::time::sleep(Duration::from_millis(300)).await;
        }

        tokio::time::sleep(FLUSH_INTERVAL * 5).await;
        assert_eq!(exported_timeout_errors(&metrics_file), TIMEOUTS);
    });
}
