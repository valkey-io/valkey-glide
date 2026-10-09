// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Server-management commands. Mirrors Python's server-management surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::commands::options::ClientPauseMode;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::value::to_glide_error;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;
use std::collections::HashMap;

/// Server-management commands (`INFO`, `DBSIZE`, `FLUSHALL`, `CONFIG ...`, `TIME`).
#[async_trait]
pub trait ServerManagementCommands: CommandExecutor {
    /// Get server information and statistics (`INFO`).
    async fn info(&self) -> ValkeyResult<String> {
        String::from_owned_valkey_value(self.execute_command(cmd("INFO"), None).await?)
    }

    /// Get server information for specific sections (`INFO section...`).
    async fn info_sections<S: ToValkeyArgs + Send + Sync>(
        &self,
        sections: &[S],
    ) -> ValkeyResult<String> {
        let cmd = cmd("INFO").with_arg(sections);
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the number of keys in the current database (`DBSIZE`).
    async fn dbsize(&self) -> ValkeyResult<i64> {
        i64::from_owned_valkey_value(self.execute_command(cmd("DBSIZE"), None).await?)
    }

    /// Get configuration parameters matching `parameter` (`CONFIG GET`).
    async fn config_get<P: ToValkeyArgs + Send>(
        &self,
        parameter: P,
    ) -> ValkeyResult<HashMap<String, Bytes>> {
        let cmd = cmd("CONFIG").with_arg("GET").with_arg(parameter);
        let map: HashMap<String, Vec<u8>> =
            FromValkeyValue::from_owned_valkey_value(self.execute_command(cmd, None).await?)?;
        Ok(map.into_iter().map(|(k, v)| (k, Bytes::from(v))).collect())
    }

    /// Set a configuration parameter (`CONFIG SET`).
    async fn config_set<P: ToValkeyArgs + Send, V: ToValkeyArgs + Send>(
        &self,
        parameter: P,
        value: V,
    ) -> ValkeyResult<()> {
        let cmd = cmd("CONFIG")
            .with_arg("SET")
            .with_arg(parameter)
            .with_arg(value);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Reset configuration statistics (`CONFIG RESETSTAT`).
    async fn config_resetstat(&self) -> ValkeyResult<()> {
        let cmd = cmd("CONFIG").with_arg("RESETSTAT");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the server time as `(unix_seconds, microseconds)` (`TIME`).
    async fn time(&self) -> ValkeyResult<(i64, i64)> {
        match self.execute_command(cmd("TIME"), None).await? {
            ValkeyValue::Array(mut parts) if parts.len() == 2 => {
                let micros = String::from_owned_valkey_value(parts.pop().unwrap())?;
                let secs = String::from_owned_valkey_value(parts.pop().unwrap())?;
                Ok((
                    secs.parse().unwrap_or_default(),
                    micros.parse().unwrap_or_default(),
                ))
            }
            other => Err(to_glide_error(other, "Unexpected TIME reply.")),
        }
    }

    /// Get the Unix time of the last successful save to disk (`LASTSAVE`).
    async fn lastsave(&self) -> ValkeyResult<i64> {
        i64::from_owned_valkey_value(self.execute_command(cmd("LASTSAVE"), None).await?)
    }

    /// Rewrite the configuration file with the in-memory configuration
    /// (`CONFIG REWRITE`).
    async fn config_rewrite(&self) -> ValkeyResult<()> {
        let cmd = cmd("CONFIG").with_arg("REWRITE");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Display a piece of generative art and the server version (`LOLWUT`),
    /// optionally selecting a rendering `version`.
    async fn lolwut(&self, version: Option<i64>) -> ValkeyResult<String> {
        let cmd = cmd("LOLWUT").with_arg(version.map(|v| ("VERSION", v)));
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Asynchronously rewrite the append-only file (`BGREWRITEAOF`). Returns the
    /// server's status message.
    async fn bgrewriteaof(&self) -> ValkeyResult<String> {
        String::from_owned_valkey_value(self.execute_command(cmd("BGREWRITEAOF"), None).await?)
    }

    /// Asynchronously save the dataset to disk (`BGSAVE`). Set `schedule` to defer
    /// until no other save is running. Returns the server's status message.
    async fn bgsave(&self, schedule: bool) -> ValkeyResult<String> {
        let cmd = cmd("BGSAVE").with_arg(schedule.then_some("SCHEDULE"));
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Synchronously save the dataset to disk (`SAVE`).
    async fn save(&self) -> ValkeyResult<()> {
        <()>::from_owned_valkey_value(self.execute_command(cmd("SAVE"), None).await?)
    }

    /// Make the server a replica of another instance (`REPLICAOF host port`).
    async fn replicaof<H: ToValkeyArgs + Send>(&self, host: H, port: i64) -> ValkeyResult<()> {
        let cmd = cmd("REPLICAOF").with_arg(host).with_arg(port);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Promote the server to a primary (`REPLICAOF NO ONE`).
    async fn replicaof_no_one(&self) -> ValkeyResult<()> {
        let cmd = cmd("REPLICAOF").with_arg("NO").with_arg("ONE");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Start a coordinated failover between the primary and a replica
    /// (`FAILOVER`).
    async fn failover<H: ToValkeyArgs + Send>(
        &self,
        to: Option<(H, i64)>,
        force: bool,
        timeout_ms: Option<i64>,
    ) -> ValkeyResult<()> {
        let cmd = cmd("FAILOVER")
            .with_arg(to.map(|(host, port)| ("TO", host, port, force.then_some("FORCE"))))
            .with_arg(timeout_ms.map(|t| ("TIMEOUT", t)));
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Abort an in-progress coordinated failover (`FAILOVER ABORT`).
    async fn failover_abort(&self) -> ValkeyResult<()> {
        let cmd = cmd("FAILOVER").with_arg("ABORT");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Suspend client commands for up to `timeout_ms` (`CLIENT PAUSE`).
    async fn client_pause(
        &self,
        timeout_ms: i64,
        mode: Option<ClientPauseMode>,
    ) -> ValkeyResult<()> {
        let cmd = cmd("CLIENT")
            .with_arg("PAUSE")
            .with_arg(timeout_ms)
            .with_arg(mode.map(|m| m.as_arg()));
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Resume paused clients (`CLIENT UNPAUSE`).
    async fn client_unpause(&self) -> ValkeyResult<()> {
        let cmd = cmd("CLIENT").with_arg("UNPAUSE");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get latency time series for an event (`LATENCY HISTORY`).
    async fn latency_history<E: ToValkeyArgs + Send>(&self, event: E) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("LATENCY").with_arg("HISTORY").with_arg(event);
        self.execute_command(cmd, None).await
    }

    /// Get the latest latency samples for all events (`LATENCY LATEST`).
    async fn latency_latest(&self) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("LATENCY").with_arg("LATEST");
        self.execute_command(cmd, None).await
    }

    /// Reset latency data, returning the number of event time series reset
    /// (`LATENCY RESET`).
    async fn latency_reset<E: ToValkeyArgs + Send + Sync>(
        &self,
        events: &[E],
    ) -> ValkeyResult<i64> {
        let cmd = cmd("LATENCY").with_arg("RESET").with_arg(events);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get a human-readable latency diagnosis report (`LATENCY DOCTOR`).
    async fn latency_doctor(&self) -> ValkeyResult<String> {
        let cmd = cmd("LATENCY").with_arg("DOCTOR");
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get a latency graph for an event (`LATENCY GRAPH`).
    async fn latency_graph<E: ToValkeyArgs + Send>(&self, event: E) -> ValkeyResult<String> {
        let cmd = cmd("LATENCY").with_arg("GRAPH").with_arg(event);
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ServerManagementCommands for T {}
