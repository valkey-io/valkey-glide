// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Scripting & function commands. Mirrors Python's scripting surface.

use crate::commands::options::{FlushMode, FunctionRestorePolicy};
use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::routes::Route;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs, Value};

/// Scripting and function commands (`EVAL`, `EVALSHA`, `SCRIPT ...`, `FCALL`, ...).
#[async_trait]
pub trait ScriptingCommands: CommandExecutor {
    /// Evaluate a Lua `script` (`EVAL`). Returns the raw reply.
    async fn eval<K: ToRedisArgs + Send + Sync, A: ToRedisArgs + Send + Sync>(
        &self,
        script: &str,
        keys: &[K],
        args: &[A],
    ) -> Result<Value> {
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
    async fn evalsha<K: ToRedisArgs + Send + Sync, A: ToRedisArgs + Send + Sync>(
        &self,
        sha1: &str,
        keys: &[K],
        args: &[A],
    ) -> Result<Value> {
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
    async fn script_load(&self, script: &str) -> Result<String> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("LOAD").arg(script);
        value::to_string(self.execute_command(cmd, None).await?)
    }

    /// Check whether scripts exist in the cache by SHA1 (`SCRIPT EXISTS`).
    async fn script_exists(&self, sha1s: &[&str]) -> Result<Vec<bool>> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("EXISTS");
        for s in sha1s {
            cmd.arg(*s);
        }
        match self.execute_command(cmd, None).await? {
            Value::Array(items) => items.into_iter().map(value::to_bool).collect(),
            other => Ok(vec![value::to_bool(other)?]),
        }
    }

    /// Flush the script cache (`SCRIPT FLUSH`).
    async fn script_flush(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("FLUSH");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Invoke a function registered with `FUNCTION LOAD` (`FCALL`).
    async fn fcall<K: ToRedisArgs + Send + Sync, A: ToRedisArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
    ) -> Result<Value> {
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
    async fn fcall_ro<K: ToRedisArgs + Send + Sync, A: ToRedisArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
    ) -> Result<Value> {
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
    async fn fcall_route<K: ToRedisArgs + Send + Sync, A: ToRedisArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
        route: Route,
    ) -> Result<Value> {
        let mut cmd = Cmd::new();
        cmd.arg("FCALL").arg(function).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        let routing = route.to_routing_info(Some(&cmd));
        self.execute_command(cmd, Some(routing)).await
    }

    /// Read-only `FCALL_RO` routed to specific cluster node(s) (`route`). On a
    /// standalone client the route is ignored.
    async fn fcall_ro_route<K: ToRedisArgs + Send + Sync, A: ToRedisArgs + Send + Sync>(
        &self,
        function: &str,
        keys: &[K],
        args: &[A],
        route: Route,
    ) -> Result<Value> {
        let mut cmd = Cmd::new();
        cmd.arg("FCALL_RO").arg(function).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        for a in args {
            cmd.arg(a);
        }
        let routing = route.to_routing_info(Some(&cmd));
        self.execute_command(cmd, Some(routing)).await
    }

    /// Load a function library (`FUNCTION LOAD`); returns the library name.
    async fn function_load(&self, code: &str, replace: bool) -> Result<String> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("LOAD");
        if replace {
            cmd.arg("REPLACE");
        }
        cmd.arg(code);
        value::to_string(self.execute_command(cmd, None).await?)
    }

    /// Delete a function library (`FUNCTION DELETE`).
    async fn function_delete(&self, library_name: &str) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("DELETE").arg(library_name);
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Flush all function libraries (`FUNCTION FLUSH`).
    async fn function_flush(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("FLUSH");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Flush all function libraries with a flush mode (`FUNCTION FLUSH SYNC|ASYNC`).
    async fn function_flush_mode(&self, mode: FlushMode) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("FLUSH").arg(mode.as_arg());
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// List registered function libraries (`FUNCTION LIST`). Set `with_code` to
    /// include the library source. Returns the raw structured reply.
    async fn function_list(&self, library_name: Option<&str>, with_code: bool) -> Result<Value> {
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
    async fn function_dump(&self) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("DUMP");
        value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Restore function libraries from a `FUNCTION DUMP` payload
    /// (`FUNCTION RESTORE`).
    async fn function_restore<P: ToRedisArgs + Send>(
        &self,
        payload: P,
        policy: FunctionRestorePolicy,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION")
            .arg("RESTORE")
            .arg(payload)
            .arg(policy.as_arg());
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Get information about the function engine and running function
    /// (`FUNCTION STATS`). Returns the raw structured reply.
    async fn function_stats(&self) -> Result<Value> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("STATS");
        self.execute_command(cmd, None).await
    }

    /// Kill a running function that made no write commands (`FUNCTION KILL`).
    async fn function_kill(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("FUNCTION").arg("KILL");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Kill a running script that made no write commands (`SCRIPT KILL`).
    async fn script_kill(&self) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("KILL");
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Show the source of a cached script by its SHA1 (`SCRIPT SHOW`, Valkey 8+).
    async fn script_show(&self, sha1: &str) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("SHOW").arg(sha1);
        value::to_bytes(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> ScriptingCommands for T {}
