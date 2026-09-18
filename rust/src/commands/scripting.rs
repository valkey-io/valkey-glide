// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Scripting & function commands. Mirrors Python's scripting surface.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::commands::options::{FlushMode, FunctionRestorePolicy};
use crate::executor::CommandExecutor;
use crate::routes::Route;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;

/// Scripting and function commands (`EVAL`, `EVALSHA`, `SCRIPT ...`, `FCALL`, ...).
#[async_trait]
pub trait ScriptingCommands: CommandExecutor {
    /// Evaluate a Lua `script` (`EVAL`). Returns the raw reply.
    async fn eval<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        script: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("EVAL").arg(script).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Evaluate a cached script by its SHA1 hash (`EVALSHA`).
    async fn evalsha<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        sha1: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("EVALSHA").arg(sha1).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Load a script into the script cache, returning its SHA1 (`SCRIPT LOAD`).
    async fn script_load(&self, script: &str) -> ValkeyResult<String> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("LOAD").arg(script);
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Check whether scripts exist in the cache by SHA1 (`SCRIPT EXISTS`).
    async fn script_exists(&self, sha1s: &[&str]) -> ValkeyResult<Vec<bool>> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("EXISTS");
        for s in sha1s {
            cmd.arg(*s);
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(bool::from_owned_valkey_value)
                .collect(),
            other => Ok(vec![bool::from_owned_valkey_value(other)?]),
        }
    }

    /// Flush the script cache (`SCRIPT FLUSH`).
    async fn script_flush(&self) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("FLUSH");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Invoke a function registered with `FUNCTION LOAD` (`FCALL`).
    async fn fcall<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FCALL").arg(function).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// Read-only variant of [`ScriptingCommands::fcall`] (`FCALL_RO`).
    async fn fcall_ro<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FCALL_RO").arg(function).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, None).await
    }

    /// `FCALL` routed to specific cluster node(s) (`route`). Keyless function
    /// calls are commonly invoked with an explicit route (e.g. `AllPrimaries`);
    /// on a standalone client the route is ignored.
    async fn fcall_route<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
        route: Route,
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FCALL").arg(function).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, Some(route)).await
    }

    /// Read-only `FCALL_RO` routed to specific cluster node(s) (`route`). On a
    /// standalone client the route is ignored.
    async fn fcall_ro_route<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
        route: Route,
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FCALL_RO").arg(function).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        self.execute_command(cmd, Some(route)).await
    }

    /// Load a function library (`FUNCTION LOAD`); returns the library name.
    async fn function_load(&self, code: &str, replace: bool) -> ValkeyResult<String> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("LOAD");
        if replace {
            cmd.arg("REPLACE");
        }
        cmd.arg(code);
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Delete a function library (`FUNCTION DELETE`).
    async fn function_delete(&self, library_name: &str) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("DELETE").arg(library_name);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Flush all function libraries (`FUNCTION FLUSH`).
    async fn function_flush(&self) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("FLUSH");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Flush all function libraries with a flush mode (`FUNCTION FLUSH SYNC|ASYNC`).
    async fn function_flush_mode(&self, mode: FlushMode) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("FLUSH").arg(mode.as_arg());
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// List registered function libraries (`FUNCTION LIST`). Set `with_code` to
    /// include the library source. Returns the raw structured reply.
    async fn function_list(
        &self,
        library_name: Option<&str>,
        with_code: bool,
    ) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("LIST");
        if let Some(n) = library_name {
            cmd.arg("LIBRARYNAME").arg(n);
        }
        if with_code {
            cmd.arg("WITHCODE");
        }
        self.execute_command(cmd, None).await
    }

    /// Dump the serialized payload of all function libraries (`FUNCTION DUMP`).
    async fn function_dump(&self) -> ValkeyResult<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("DUMP");
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Restore function libraries from a `FUNCTION DUMP` payload
    /// (`FUNCTION RESTORE`).
    async fn function_restore<P: ToValkeyArgs + Send>(
        &self,
        payload: P,
        policy: FunctionRestorePolicy,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION")
            .arg("RESTORE")
            .arg(payload)
            .arg(policy.as_arg());
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get information about the function engine and running function
    /// (`FUNCTION STATS`). Returns the raw structured reply.
    async fn function_stats(&self) -> ValkeyResult<ValkeyValue> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("STATS");
        self.execute_command(cmd, None).await
    }

    /// Kill a running function that made no write commands (`FUNCTION KILL`).
    async fn function_kill(&self) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("KILL");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Kill a running script that made no write commands (`SCRIPT KILL`).
    async fn script_kill(&self) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("KILL");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Show the source of a cached script by its SHA1 (`SCRIPT SHOW`, Valkey 8+).
    async fn script_show(&self, sha1: &str) -> ValkeyResult<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("SHOW").arg(sha1);
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ScriptingCommands for T {}
