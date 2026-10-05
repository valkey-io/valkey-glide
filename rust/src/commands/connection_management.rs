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
