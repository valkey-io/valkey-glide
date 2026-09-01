// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Server-management commands. Mirrors Python's server-management surface.

use crate::commands::options::{ClientPauseMode, FlushMode};
use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};
use std::collections::HashMap;

/// Server-management commands (`INFO`, `DBSIZE`, `FLUSHALL`, `CONFIG ...`, `TIME`).
#[async_trait]
pub trait ServerManagementCommands: CommandExecutor {
    /// Get server information and statistics (`INFO`).
    async fn info(&self) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("INFO");
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Get server information for specific sections (`INFO section...`).
    async fn info_sections<S: ToRedisArgs + Send + Sync>(&self, sections: &[S]) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("INFO");
        for s in sections {
            cmd.arg(s);
        }
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Get the number of keys in the current database (`DBSIZE`).
    async fn dbsize(&self) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("DBSIZE");
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Remove all keys from the current database (`FLUSHDB`).
    async fn flushdb(&self, mode: Option<FlushMode>) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FLUSHDB");
        if let Some(m) = mode {
            cmd.arg(m.as_arg());
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Remove all keys from all databases (`FLUSHALL`).
    async fn flushall(&self, mode: Option<FlushMode>) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FLUSHALL");
        if let Some(m) = mode {
            cmd.arg(m.as_arg());
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Get configuration parameters matching `parameter` (`CONFIG GET`).
    async fn config_get<P: ToRedisArgs + Send>(
        &self,
        parameter: P,
    ) -> Result<HashMap<String, Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("CONFIG").arg("GET").arg(parameter);
        let map: HashMap<String, Vec<u8>> =
            value::from_value(self.execute_command(cmd, None).await?)?;
        Ok(map.into_iter().map(|(k, v)| (k, Bytes::from(v))).collect())
    }

    /// Set a configuration parameter (`CONFIG SET`).
    async fn config_set<P: ToRedisArgs + Send, V: ToRedisArgs + Send>(
        &self,
        parameter: P,
        value: V,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CONFIG").arg("SET").arg(parameter).arg(value);
        crate::value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Reset configuration statistics (`CONFIG RESETSTAT`).
    async fn config_resetstat(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CONFIG").arg("RESETSTAT");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Get the server time as `(unix_seconds, microseconds)` (`TIME`).
    async fn time(&self) -> Result<(i64, i64)> {
        let mut cmd = Cmd::new();
        cmd.arg("TIME");
        match self.execute_command(cmd, None).await? {
            redis::Value::Array(mut parts) if parts.len() == 2 => {
                let micros = value::to_string(parts.pop().unwrap())?;
                let secs = value::to_string(parts.pop().unwrap())?;
                Ok((
                    secs.parse().unwrap_or_default(),
                    micros.parse().unwrap_or_default(),
                ))
            }
            other => Err(crate::error::GlideError::Request(format!(
                "unexpected TIME reply: {other:?}"
            ))),
        }
    }

    /// Get the Unix time of the last successful save to disk (`LASTSAVE`).
    async fn lastsave(&self) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("LASTSAVE");
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Rewrite the configuration file with the in-memory configuration
    /// (`CONFIG REWRITE`).
    async fn config_rewrite(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CONFIG").arg("REWRITE");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Display a piece of generative art and the server version (`LOLWUT`),
    /// optionally selecting a rendering `version`.
    async fn lolwut(&self, version: Option<i64>) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("LOLWUT");
        if let Some(v) = version {
            cmd.arg("VERSION").arg(v);
        }
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Asynchronously rewrite the append-only file (`BGREWRITEAOF`).
    async fn bgrewriteaof(&self) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("BGREWRITEAOF");
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Asynchronously save the dataset to disk (`BGSAVE`). Set `schedule` to defer
    /// until no other save is running.
    async fn bgsave(&self, schedule: bool) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("BGSAVE");
        if schedule {
            cmd.arg("SCHEDULE");
        }
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Synchronously save the dataset to disk (`SAVE`).
    async fn save(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SAVE");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Make the server a replica of another instance (`REPLICAOF host port`).
    async fn replicaof<H: ToRedisArgs + Send>(&self, host: H, port: i64) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("REPLICAOF").arg(host).arg(port);
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Promote the server to a primary (`REPLICAOF NO ONE`).
    async fn replicaof_no_one(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("REPLICAOF").arg("NO").arg("ONE");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Start a coordinated failover between the primary and a replica
    /// (`FAILOVER`).
    async fn failover<H: ToRedisArgs + Send>(
        &self,
        to: Option<(H, i64)>,
        force: bool,
        timeout_ms: Option<i64>,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FAILOVER");
        if let Some((host, port)) = to {
            cmd.arg("TO").arg(host).arg(port);
            if force {
                cmd.arg("FORCE");
            }
        }
        if let Some(t) = timeout_ms {
            cmd.arg("TIMEOUT").arg(t);
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Abort an in-progress coordinated failover (`FAILOVER ABORT`).
    async fn failover_abort(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FAILOVER").arg("ABORT");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Suspend client commands for up to `timeout_ms` (`CLIENT PAUSE`).
    async fn client_pause(&self, timeout_ms: i64, mode: Option<ClientPauseMode>) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("PAUSE").arg(timeout_ms);
        if let Some(m) = mode {
            cmd.arg(m.as_arg());
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Resume paused clients (`CLIENT UNPAUSE`).
    async fn client_unpause(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("UNPAUSE");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Get latency time series for an event (`LATENCY HISTORY`).
    async fn latency_history<E: ToRedisArgs + Send>(&self, event: E) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("LATENCY").arg("HISTORY").arg(event);
        self.execute_command(cmd, None).await
    }

    /// Get the latest latency samples for all events (`LATENCY LATEST`).
    async fn latency_latest(&self) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("LATENCY").arg("LATEST");
        self.execute_command(cmd, None).await
    }

    /// Reset latency data, returning the number of event time series reset
    /// (`LATENCY RESET`).
    async fn latency_reset<E: ToRedisArgs + Send + Sync>(&self, events: &[E]) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("LATENCY").arg("RESET");
        for e in events {
            cmd.arg(e);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Get a human-readable latency diagnosis report (`LATENCY DOCTOR`).
    async fn latency_doctor(&self) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("LATENCY").arg("DOCTOR");
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Get a latency graph for an event (`LATENCY GRAPH`).
    async fn latency_graph<E: ToRedisArgs + Send>(&self, event: E) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("LATENCY").arg("GRAPH").arg(event);
        value::to_bytes(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ServerManagementCommands for T {}
