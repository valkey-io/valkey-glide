// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Mock-executor unit tests for the server-management command family.
use super::Mock;
use glide::commands::options::FlushMode;
use glide::commands::server_management::ServerManagementCommands;
use redis::Value;

#[tokio::test]
async fn info_and_sections() {
    let m = Mock::bulk("# Server\nredis_version:7.0\n");
    let info = m.info().await.unwrap();
    m.assert_args(&["INFO"]);
    assert!(info.starts_with(b"# Server"));

    let m = Mock::bulk("# CPU\n");
    m.info_sections(&["cpu", "memory"]).await.unwrap();
    m.assert_args(&["INFO", "cpu", "memory"]);
}

#[tokio::test]
async fn dbsize_encoding() {
    let m = Mock::int(7);
    assert_eq!(m.dbsize().await.unwrap(), 7);
    m.assert_args(&["DBSIZE"]);
}

#[tokio::test]
async fn flushdb_default_and_modes() {
    let m = Mock::ok();
    m.flushdb(None).await.unwrap();
    m.assert_args(&["FLUSHDB"]);

    let m = Mock::ok();
    m.flushdb(Some(FlushMode::Async)).await.unwrap();
    m.assert_args(&["FLUSHDB", "ASYNC"]);
}

#[tokio::test]
async fn flushall_with_mode() {
    let m = Mock::ok();
    m.flushall(Some(FlushMode::Sync)).await.unwrap();
    m.assert_args(&["FLUSHALL", "SYNC"]);
}

#[tokio::test]
async fn config_get_parses_map() {
    let m = Mock::array(vec![
        Value::BulkString(b"maxmemory".to_vec()),
        Value::BulkString(b"100mb".to_vec()),
    ]);
    let cfg = m.config_get("maxmemory").await.unwrap();
    m.assert_args(&["CONFIG", "GET", "maxmemory"]);
    assert_eq!(
        cfg.get("maxmemory").map(|b| b.as_ref()),
        Some(&b"100mb"[..])
    );
}

#[tokio::test]
async fn config_set_encoding() {
    let m = Mock::ok();
    m.config_set("maxmemory", "100mb").await.unwrap();
    m.assert_args(&["CONFIG", "SET", "maxmemory", "100mb"]);
}

#[tokio::test]
async fn config_resetstat_and_rewrite() {
    let m = Mock::ok();
    m.config_resetstat().await.unwrap();
    m.assert_args(&["CONFIG", "RESETSTAT"]);

    let m = Mock::ok();
    m.config_rewrite().await.unwrap();
    m.assert_args(&["CONFIG", "REWRITE"]);
}

#[tokio::test]
async fn time_parses_pair() {
    let m = Mock::array(vec![
        Value::BulkString(b"1700000000".to_vec()),
        Value::BulkString(b"123456".to_vec()),
    ]);
    assert_eq!(m.time().await.unwrap(), (1700000000, 123456));
    m.assert_args(&["TIME"]);
}

#[tokio::test]
async fn lastsave_encoding() {
    let m = Mock::int(1700000000);
    assert_eq!(m.lastsave().await.unwrap(), 1700000000);
    m.assert_args(&["LASTSAVE"]);
}

#[tokio::test]
async fn lolwut_default_and_versioned() {
    let m = Mock::bulk("Redis ver. 7.0");
    m.lolwut(None).await.unwrap();
    m.assert_args(&["LOLWUT"]);

    let m = Mock::bulk("art");
    m.lolwut(Some(5)).await.unwrap();
    m.assert_args(&["LOLWUT", "VERSION", "5"]);
}

// ---- Admin / persistence / replication / latency commands ----

#[tokio::test]
async fn persistence_commands() {
    let m = Mock::simple("Background append only file rewriting started");
    m.bgrewriteaof().await.unwrap();
    m.assert_args(&["BGREWRITEAOF"]);

    let m = Mock::simple("Background saving started");
    m.bgsave(false).await.unwrap();
    m.assert_args(&["BGSAVE"]);

    let m = Mock::simple("Background saving started");
    m.bgsave(true).await.unwrap();
    m.assert_args(&["BGSAVE", "SCHEDULE"]);

    let m = Mock::ok();
    m.save().await.unwrap();
    m.assert_args(&["SAVE"]);
}

#[tokio::test]
async fn replication_commands() {
    let m = Mock::ok();
    m.replicaof("primary.host", 6379).await.unwrap();
    m.assert_args(&["REPLICAOF", "primary.host", "6379"]);

    let m = Mock::ok();
    m.replicaof_no_one().await.unwrap();
    m.assert_args(&["REPLICAOF", "NO", "ONE"]);
}

#[tokio::test]
async fn failover_variants() {
    let m = Mock::ok();
    m.failover::<&str>(None, false, None).await.unwrap();
    m.assert_args(&["FAILOVER"]);

    let m = Mock::ok();
    m.failover(Some(("replica.host", 7000)), true, Some(5000))
        .await
        .unwrap();
    m.assert_args(&[
        "FAILOVER",
        "TO",
        "replica.host",
        "7000",
        "FORCE",
        "TIMEOUT",
        "5000",
    ]);

    let m = Mock::ok();
    m.failover_abort().await.unwrap();
    m.assert_args(&["FAILOVER", "ABORT"]);
}

#[tokio::test]
async fn client_pause_variants() {
    let m = Mock::ok();
    m.client_pause(1000, None).await.unwrap();
    m.assert_args(&["CLIENT", "PAUSE", "1000"]);

    let m = Mock::ok();
    m.client_pause(1000, Some(glide::commands::options::ClientPauseMode::Write))
        .await
        .unwrap();
    m.assert_args(&["CLIENT", "PAUSE", "1000", "WRITE"]);

    let m = Mock::ok();
    m.client_unpause().await.unwrap();
    m.assert_args(&["CLIENT", "UNPAUSE"]);
}

#[tokio::test]
async fn latency_commands() {
    let m = Mock::array(vec![]);
    m.latency_history("command").await.unwrap();
    m.assert_args(&["LATENCY", "HISTORY", "command"]);

    let m = Mock::array(vec![]);
    m.latency_latest().await.unwrap();
    m.assert_args(&["LATENCY", "LATEST"]);

    let m = Mock::int(2);
    assert_eq!(m.latency_reset(&["command", "fork"]).await.unwrap(), 2);
    m.assert_args(&["LATENCY", "RESET", "command", "fork"]);

    let m = Mock::int(0);
    m.latency_reset(&[] as &[&str]).await.unwrap();
    m.assert_args(&["LATENCY", "RESET"]);

    let m = Mock::bulk("Dave, I have observed the system...");
    m.latency_doctor().await.unwrap();
    m.assert_args(&["LATENCY", "DOCTOR"]);

    let m = Mock::bulk("graph");
    m.latency_graph("command").await.unwrap();
    m.assert_args(&["LATENCY", "GRAPH", "command"]);
}
