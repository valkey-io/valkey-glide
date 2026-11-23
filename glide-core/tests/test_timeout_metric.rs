// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! `glide.timeout_errors` counts every command that fails because its request timeout elapsed.
//!
//! A command with a request timeout races two timers of the same length: the timeout watchdog and the
//! connection's own response timeout. Whichever fires first fails the command, and the counter must not
//! depend on which one that was. On macOS the connection's timer usually wins (the watchdog thread wakes
//! later under timer coalescing), so before the fix the multiplexed test counted few or none of the
//! timeouts there. Commands on a scope connection time out through a timer of their own, and are counted
//! too.
//!
//! A file of its own because OpenTelemetry is initialised once per process. The counter is shared by the
//! tests here, so each runs serially and asserts on how far the counter moved during it.

mod constants;
mod utilities;

use glide_core::client::{AuthGeneration, Client};
use glide_telemetry::{
    GlideOpenTelemetry, GlideOpenTelemetryConfigBuilder, GlideOpenTelemetrySignalsExporter,
};
use redis::{GlideConnectionOptions, RedisConnectionInfo};
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::time::Duration;
use utilities::*;

const TIMEOUTS: u64 = 5;
const REQUEST_TIMEOUT_MS: u32 = 200;
const FLUSH_INTERVAL: Duration = Duration::from_millis(100);

/// The file the metrics are exported to, OpenTelemetry being initialised on the first call.
fn metrics_file() -> &'static Path {
    static FILE: OnceLock<PathBuf> = OnceLock::new();
    FILE.get_or_init(|| {
        let file =
            std::env::temp_dir().join(format!("glide-timeout-metric-{}.json", std::process::id()));
        let _ = std::fs::remove_file(&file);
        GlideOpenTelemetry::initialise(
            GlideOpenTelemetryConfigBuilder::default()
                .with_flush_interval(FLUSH_INTERVAL)
                .with_metrics_exporter(GlideOpenTelemetrySignalsExporter::File(file.clone()))
                .build(),
        )
        .unwrap();
        file
    })
}

/// The last value exported for `glide.timeout_errors`, a cumulative counter, or 0 if none was exported.
fn exported_timeout_errors() -> u64 {
    let content = std::fs::read_to_string(metrics_file()).unwrap_or_default();
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

/// How far `glide.timeout_errors` moved while `timeouts` ran.
async fn timeouts_counted_during(timeouts: impl std::future::Future<Output = ()>) -> u64 {
    tokio::time::sleep(FLUSH_INTERVAL * 3).await;
    let before = exported_timeout_errors();
    timeouts.await;
    tokio::time::sleep(FLUSH_INTERVAL * 5).await;
    exported_timeout_errors() - before
}

/// A standalone server, and a client to it with a short request timeout.
async fn server_and_client() -> (BackingServer, Client) {
    metrics_file();
    let server = BackingServer::Standalone(Some(RedisServer::new(ServerType::Tcp { tls: false })));
    let client = create_client(
        &server,
        TestConfiguration {
            request_timeout: Some(REQUEST_TIMEOUT_MS),
            ..Default::default()
        },
    )
    .await;
    (server, client)
}

/// The server stops answering for longer than the request timeout; the pause's own reply comes first.
async fn pause_server(client: &mut Client) {
    let mut pause = redis::cmd("CLIENT");
    pause.arg("PAUSE").arg(REQUEST_TIMEOUT_MS * 2).arg("ALL");
    client.send_command(&mut pause, None).await.unwrap();
}

/// Waits out the pause, so the next one starts from a server that is answering.
async fn wait_out_pause() {
    tokio::time::sleep(Duration::from_millis(u64::from(REQUEST_TIMEOUT_MS) * 3 / 2)).await;
}

#[test]
#[serial_test::serial]
fn every_request_timeout_is_counted_whichever_timer_fires_first() {
    block_on_all(async {
        let (_server, mut client) = server_and_client().await;

        let counted = timeouts_counted_during(async {
            for _ in 0..TIMEOUTS {
                pause_server(&mut client).await;
                let mut get = redis::cmd("GET");
                get.arg("timeout-metric");
                let err = client.send_command(&mut get, None).await.unwrap_err();
                assert!(err.is_timeout(), "{err}");
                wait_out_pause().await;
            }
        })
        .await;

        assert_eq!(counted, TIMEOUTS);
    });
}

#[test]
#[serial_test::serial]
fn a_request_timeout_on_a_scope_connection_is_counted() {
    block_on_all(async {
        let (server, mut client) = server_and_client().await;
        let BackingServer::Standalone(Some(redis_server)) = &server else {
            unreachable!("server_and_client starts a standalone server of its own")
        };
        // A raw connection standing in for a scoped one, which also has no response timeout of its own,
        // so the request timeout `send_command_on_connection` applies is what fails the command.
        let mut scope_connection = redis::Client::open(redis::ConnectionInfo {
            addr: redis_server.get_client_addr(),
            redis: RedisConnectionInfo::default(),
        })
        .unwrap()
        .get_multiplexed_async_connection(GlideConnectionOptions::default())
        .await
        .unwrap();
        let mut auth_generation = AuthGeneration::NEVER;

        let counted = timeouts_counted_during(async {
            for _ in 0..TIMEOUTS {
                pause_server(&mut client).await;
                let mut get = redis::cmd("GET");
                get.arg("timeout-metric");
                let err = client
                    .send_command_on_connection(
                        &get,
                        &mut scope_connection,
                        &mut auth_generation,
                        false,
                    )
                    .await
                    .unwrap_err();
                assert!(err.is_timeout(), "{err}");
                wait_out_pause().await;
            }
        })
        .await;

        assert_eq!(counted, TIMEOUTS);
    });
}
