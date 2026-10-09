// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Scripting & function commands. Mirrors Python's scripting surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::commands::options::{FunctionFlushOptions, FunctionRestorePolicy};
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
        let cmd = cmd("EVAL")
            .with_arg(script)
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg(args);
        self.execute_command(cmd, None).await
    }

    /// Evaluate a cached script by its SHA1 hash (`EVALSHA`).
    async fn evalsha<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        sha1: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("EVALSHA")
            .with_arg(sha1)
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg(args);
        self.execute_command(cmd, None).await
    }

    /// Load a script into the script cache, returning its SHA1 (`SCRIPT LOAD`).
    async fn script_load(&self, script: &str) -> ValkeyResult<String> {
        let cmd = cmd("SCRIPT").with_arg("LOAD").with_arg(script);
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Check whether scripts exist in the cache by SHA1 (`SCRIPT EXISTS`).
    async fn script_exists(&self, sha1s: &[&str]) -> ValkeyResult<Vec<bool>> {
        let cmd = cmd("SCRIPT").with_arg("EXISTS").with_arg(sha1s);
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
        let cmd = cmd("SCRIPT").with_arg("FLUSH");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Invoke a function registered with `FUNCTION LOAD` (`FCALL`).
    async fn fcall<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("FCALL")
            .with_arg(function)
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg(args);
        self.execute_command(cmd, None).await
    }

    /// Read-only variant of [`ScriptingCommands::fcall`] (`FCALL_RO`).
    async fn fcall_ro<K: ToValkeyArgs + Send + Sync, A: ToValkeyArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("FCALL_RO")
            .with_arg(function)
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg(args);
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
        let cmd = cmd("FCALL")
            .with_arg(function)
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg(args);
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
        let cmd = cmd("FCALL_RO")
            .with_arg(function)
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg(args);
        self.execute_command(cmd, Some(route)).await
    }

    /// Load a function library (`FUNCTION LOAD`); returns the library name.
    async fn function_load(&self, code: &str, replace: bool) -> ValkeyResult<String> {
        let cmd = cmd("FUNCTION")
            .with_arg("LOAD")
            .with_arg(replace.then_some("REPLACE"))
            .with_arg(code);
        String::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Delete a function library (`FUNCTION DELETE`).
    async fn function_delete(&self, library_name: &str) -> ValkeyResult<()> {
        let cmd = cmd("FUNCTION").with_arg("DELETE").with_arg(library_name);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Flush all function libraries (`FUNCTION FLUSH`).
    async fn function_flush(&self) -> ValkeyResult<()> {
        let cmd = cmd("FUNCTION").with_arg("FLUSH");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Flush all function libraries (`FUNCTION FLUSH SYNC|ASYNC`).
    async fn function_flush_options(&self, options: &FunctionFlushOptions) -> ValkeyResult<()> {
        let cmd = cmd("FUNCTION").with_arg("FLUSH").with_arg(options);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// List registered function libraries (`FUNCTION LIST`). Set `with_code` to
    /// include the library source. Returns the raw structured reply.
    async fn function_list(
        &self,
        library_name: Option<&str>,
        with_code: bool,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("FUNCTION")
            .with_arg("LIST")
            .with_arg(library_name.map(|n| ("LIBRARYNAME", n)))
            .with_arg(with_code.then_some("WITHCODE"));
        self.execute_command(cmd, None).await
    }

    /// Dump the serialized payload of all function libraries (`FUNCTION DUMP`).
    async fn function_dump(&self) -> ValkeyResult<Bytes> {
        let cmd = cmd("FUNCTION").with_arg("DUMP");
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Restore function libraries from a `FUNCTION DUMP` payload
    /// (`FUNCTION RESTORE`).
    async fn function_restore<P: ToValkeyArgs + Send>(
        &self,
        payload: P,
        policy: FunctionRestorePolicy,
    ) -> ValkeyResult<()> {
        let cmd = cmd("FUNCTION")
            .with_arg("RESTORE")
            .with_arg(payload)
            .with_arg(policy.as_arg());
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get information about the function engine and running function
    /// (`FUNCTION STATS`). Returns the raw structured reply.
    async fn function_stats(&self) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("FUNCTION").with_arg("STATS");
        self.execute_command(cmd, None).await
    }

    /// Kill a running function that made no write commands (`FUNCTION KILL`).
    async fn function_kill(&self) -> ValkeyResult<()> {
        let cmd = cmd("FUNCTION").with_arg("KILL");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Kill a running script that made no write commands (`SCRIPT KILL`).
    async fn script_kill(&self) -> ValkeyResult<()> {
        let cmd = cmd("SCRIPT").with_arg("KILL");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Show the source of a cached script by its SHA1 (`SCRIPT SHOW`, Valkey 8+).
    async fn script_show(&self, sha1: &str) -> ValkeyResult<Bytes> {
        let cmd = cmd("SCRIPT").with_arg("SHOW").with_arg(sha1);
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ScriptingCommands for T {}
