// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Connection-management commands. Mirrors Python's connection command surface.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;

/// Connection-management commands (`PING`, `ECHO`, `SELECT`, `CLIENT ...`).
#[async_trait]
pub trait ConnectionManagementCommands: CommandExecutor {
    /// Ping the server (`PING`). Returns `"PONG"`.
    async fn ping(&self) -> ValkeyResult<String> {
        let mut cmd = Cmd::new();
        cmd.arg("PING");
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Ping the server with a message (`PING message`). Echoes the message back.
    async fn ping_message<M: ToValkeyArgs + Send>(&self, message: M) -> ValkeyResult<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("PING").arg(message);
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Echo a message (`ECHO`).
    async fn echo<M: ToValkeyArgs + Send>(&self, message: M) -> ValkeyResult<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("ECHO").arg(message);
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Select the logical database with the given index (`SELECT`).
    async fn select(&self, index: i64) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SELECT").arg(index);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the current connection id (`CLIENT ID`).
    async fn client_id(&self) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("ID");
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the current connection name (`CLIENT GETNAME`).
    async fn client_getname(&self) -> ValkeyResult<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("GETNAME");
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Set the current connection name (`CLIENT SETNAME`).
    async fn client_setname<N: ToValkeyArgs + Send>(&self, name: N) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT").arg("SETNAME").arg(name);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Enable or disable eviction for the current connection (`CLIENT NO-EVICT`).
    async fn client_no_evict(&self, on: bool) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT")
            .arg("NO-EVICT")
            .arg(if on { "ON" } else { "OFF" });
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Enable or disable access-time updates for the current connection
    /// (`CLIENT NO-TOUCH`).
    async fn client_no_touch(&self, on: bool) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("CLIENT")
            .arg("NO-TOUCH")
            .arg(if on { "ON" } else { "OFF" });
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Reset the connection to its initial state (`RESET`).
    async fn reset(&self) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("RESET");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ConnectionManagementCommands for T {}
