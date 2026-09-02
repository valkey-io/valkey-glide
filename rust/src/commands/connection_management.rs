// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Connection-management commands. Mirrors Python's connection command surface.

use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

/// Connection-management commands (`PING`, `ECHO`, `SELECT`, `CLIENT ...`).
#[async_trait]
pub trait ConnectionManagementCommands: CommandExecutor {
    /// Ping the server (`PING`). Returns `"PONG"`.
    async fn ping(&self) -> Result<String> {
        let mut cmd = Cmd::new();
        cmd.arg("PING");
        value::to_string(self.execute_command(cmd, None).await?)
    }

    /// Ping the server with a message (`PING message`). Echoes the message back.
    async fn ping_message<M: ToRedisArgs + Send>(&self, message: M) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("PING").arg(message);
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Echo a message (`ECHO`).
    async fn echo<M: ToRedisArgs + Send>(&self, message: M) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("ECHO").arg(message);
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Select the logical database with the given index (`SELECT`).
    async fn select(&self, index: i64) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SELECT").arg(index);
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Get the current connection id (`CLIENT ID`).
    async fn client_id(&self) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("ID");
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Get the current connection name (`CLIENT GETNAME`).
    async fn client_getname(&self) -> Result<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("GETNAME");
        value::to_opt_bytes(self.execute_command(cmd, None).await?)
    }

    /// Set the current connection name (`CLIENT SETNAME`).
    async fn client_setname<N: ToRedisArgs + Send>(&self, name: N) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("SETNAME").arg(name);
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Enable or disable eviction for the current connection (`CLIENT NO-EVICT`).
    async fn client_no_evict(&self, on: bool) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT")
            .arg("NO-EVICT")
            .arg(if on { "ON" } else { "OFF" });
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Enable or disable access-time updates for the current connection
    /// (`CLIENT NO-TOUCH`).
    async fn client_no_touch(&self, on: bool) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT")
            .arg("NO-TOUCH")
            .arg(if on { "ON" } else { "OFF" });
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Reset the connection to its initial state (`RESET`).
    async fn reset(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("RESET");
        value::to_unit(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ConnectionManagementCommands for T {}
