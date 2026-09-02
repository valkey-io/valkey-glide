// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! JSON module commands (`JSON.*`). Mirrors Python's `glide_json` namespace.
//!
//! These require the `json` module to be loaded on the server. Paths default to
//! the JSONPath root (`$`) where the server does.

use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

/// JSON module commands (`JSON.SET`, `JSON.GET`, `JSON.ARRAPPEND`, ...).
///
/// Mirrors the Python `glide_json` module functions.
#[async_trait]
pub trait JsonCommands: CommandExecutor {
    /// Set the JSON value at `path` in `key` (`JSON.SET`).
    async fn json_set<K: ToRedisArgs + Send, P: ToRedisArgs + Send, V: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        value: V,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.SET").arg(key).arg(path).arg(value);
        crate::value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Get the JSON value(s) at `paths` in `key` (`JSON.GET`).
    async fn json_get<K: ToRedisArgs + Send, P: ToRedisArgs + Send + Sync>(
        &self,
        key: K,
        paths: &[P],
    ) -> Result<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.GET").arg(key);
        for p in paths {
            cmd.arg(p);
        }
        value::to_opt_bytes(self.execute_command(cmd, None).await?)
    }

    /// Delete the value(s) at `path` (`JSON.DEL`); returns the number deleted.
    async fn json_del<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.DEL").arg(key).arg(path);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Delete the value(s) at `path` (`JSON.FORGET`, an alias of `JSON.DEL`).
    async fn json_forget<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.FORGET").arg(key).arg(path);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Get the type of the value(s) at `path` (`JSON.TYPE`).
    async fn json_type<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.TYPE").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Increment the number(s) at `path` by `value` (`JSON.NUMINCRBY`). Returns
    /// the resulting value(s) encoded as a JSON string.
    async fn json_numincrby<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        value: f64,
    ) -> Result<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.NUMINCRBY").arg(key).arg(path).arg(value);
        crate::value::to_opt_bytes(self.execute_command(cmd, None).await?)
    }

    /// Multiply the number(s) at `path` by `value` (`JSON.NUMMULTBY`).
    async fn json_nummultby<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        value: f64,
    ) -> Result<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.NUMMULTBY").arg(key).arg(path).arg(value);
        crate::value::to_opt_bytes(self.execute_command(cmd, None).await?)
    }

    /// Append `value` to the string(s) at `path` (`JSON.STRAPPEND`). Returns the
    /// new string length(s).
    async fn json_strappend<K: ToRedisArgs + Send, P: ToRedisArgs + Send, V: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        value: V,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.STRAPPEND").arg(key).arg(path).arg(value);
        self.execute_command(cmd, None).await
    }

    /// Get the length of the string(s) at `path` (`JSON.STRLEN`).
    async fn json_strlen<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.STRLEN").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Append `values` to the array(s) at `path` (`JSON.ARRAPPEND`).
    async fn json_arrappend<
        K: ToRedisArgs + Send,
        P: ToRedisArgs + Send,
        V: ToRedisArgs + Send + Sync,
    >(
        &self,
        key: K,
        path: P,
        values: &[V],
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.ARRAPPEND").arg(key).arg(path);
        for v in values {
            cmd.arg(v);
        }
        self.execute_command(cmd, None).await
    }

    /// Insert `values` into the array(s) at `path` starting at `index`
    /// (`JSON.ARRINSERT`).
    async fn json_arrinsert<
        K: ToRedisArgs + Send,
        P: ToRedisArgs + Send,
        V: ToRedisArgs + Send + Sync,
    >(
        &self,
        key: K,
        path: P,
        index: i64,
        values: &[V],
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.ARRINSERT").arg(key).arg(path).arg(index);
        for v in values {
            cmd.arg(v);
        }
        self.execute_command(cmd, None).await
    }

    /// Get the length of the array(s) at `path` (`JSON.ARRLEN`).
    async fn json_arrlen<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.ARRLEN").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Pop an element from the array(s) at `path` at `index` (`JSON.ARRPOP`).
    async fn json_arrpop<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        index: Option<i64>,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.ARRPOP").arg(key).arg(path);
        if let Some(i) = index {
            cmd.arg(i);
        }
        self.execute_command(cmd, None).await
    }

    /// Trim the array(s) at `path` to the inclusive range `[start, stop]`
    /// (`JSON.ARRTRIM`).
    async fn json_arrtrim<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        start: i64,
        stop: i64,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.ARRTRIM")
            .arg(key)
            .arg(path)
            .arg(start)
            .arg(stop);
        self.execute_command(cmd, None).await
    }

    /// Get the keys of the object(s) at `path` (`JSON.OBJKEYS`).
    async fn json_objkeys<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.OBJKEYS").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Get the number of keys in the object(s) at `path` (`JSON.OBJLEN`).
    async fn json_objlen<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.OBJLEN").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Toggle the boolean value(s) at `path` (`JSON.TOGGLE`).
    async fn json_toggle<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.TOGGLE").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Clear container value(s) at `path` (`JSON.CLEAR`); returns the number of
    /// values cleared.
    async fn json_clear<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.CLEAR").arg(key).arg(path);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Find the index of `value` in the array(s) at `path` (`JSON.ARRINDEX`).
    /// Optionally restrict the search to `[start, end)`.
    async fn json_arrindex<K: ToRedisArgs + Send, P: ToRedisArgs + Send, V: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
        value: V,
        range: Option<(i64, i64)>,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.ARRINDEX").arg(key).arg(path).arg(value);
        if let Some((start, end)) = range {
            cmd.arg(start).arg(end);
        }
        self.execute_command(cmd, None).await
    }

    /// Get the value(s) at `path` from multiple keys (`JSON.MGET`).
    async fn json_mget<K: ToRedisArgs + Send + Sync, P: ToRedisArgs + Send>(
        &self,
        keys: &[K],
        path: P,
    ) -> Result<Vec<Option<Bytes>>> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.MGET");
        for k in keys {
            cmd.arg(k);
        }
        cmd.arg(path);
        match self.execute_command(cmd, None).await? {
            redis::Value::Array(items) => items.into_iter().map(value::to_opt_bytes).collect(),
            redis::Value::Nil => Ok(Vec::new()),
            other => Ok(vec![value::to_opt_bytes(other)?]),
        }
    }

    /// Get the value(s) at `path` in RESP form (`JSON.RESP`).
    async fn json_resp<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.RESP").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Report the memory usage of the value(s) at `path`
    /// (`JSON.DEBUG MEMORY`).
    async fn json_debug_memory<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.DEBUG").arg("MEMORY").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }

    /// Report the number of fields in the value(s) at `path`
    /// (`JSON.DEBUG FIELDS`).
    async fn json_debug_fields<K: ToRedisArgs + Send, P: ToRedisArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("JSON.DEBUG").arg("FIELDS").arg(key).arg(path);
        self.execute_command(cmd, None).await
    }
}

impl<T: CommandExecutor + ?Sized> JsonCommands for T {}
