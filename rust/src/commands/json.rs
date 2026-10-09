// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! JSON module commands (`JSON.*`). Mirrors Python's `glide_json` namespace.
//!
//! These require the `json` module to be loaded on the server. Paths default to
//! the JSONPath root (`$`) where the server does.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;

/// JSON module commands (`JSON.SET`, `JSON.GET`, `JSON.ARRAPPEND`, ...).
///
/// Mirrors the Python `glide_json` module functions.
#[async_trait]
pub trait JsonCommands: CommandExecutor {
    /// Set the JSON value at `path` in `key` (`JSON.SET`).
    async fn json_set<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send, V: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
        value: V,
    ) -> ValkeyResult<()> {
        let cmd = cmd("JSON.SET").with_arg(key).with_arg(path).with_arg(value);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the JSON value(s) at `paths` in `key` (`JSON.GET`).
    async fn json_get<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        paths: &[P],
    ) -> ValkeyResult<Option<Bytes>> {
        let cmd = cmd("JSON.GET").with_arg(key).with_arg(paths);
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Delete the value(s) at `path` (`JSON.DEL`); returns the number deleted.
    async fn json_del<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("JSON.DEL").with_arg(key).with_arg(path);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Delete the value(s) at `path` (`JSON.FORGET`, an alias of `JSON.DEL`).
    async fn json_forget<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("JSON.FORGET").with_arg(key).with_arg(path);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the type of the value(s) at `path` (`JSON.TYPE`).
    async fn json_type<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.TYPE").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Increment the number(s) at `path` by `value` (`JSON.NUMINCRBY`). Returns
    /// the resulting value(s) encoded as a JSON string.
    async fn json_numincrby<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
        value: f64,
    ) -> ValkeyResult<Option<Bytes>> {
        let cmd = cmd("JSON.NUMINCRBY")
            .with_arg(key)
            .with_arg(path)
            .with_arg(value);
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Multiply the number(s) at `path` by `value` (`JSON.NUMMULTBY`).
    async fn json_nummultby<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
        value: f64,
    ) -> ValkeyResult<Option<Bytes>> {
        let cmd = cmd("JSON.NUMMULTBY")
            .with_arg(key)
            .with_arg(path)
            .with_arg(value);
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Append `value` to the string(s) at `path` (`JSON.STRAPPEND`). Returns the
    /// new string length(s).
    async fn json_strappend<
        K: ToValkeyArgs + Send,
        P: ToValkeyArgs + Send,
        V: ToValkeyArgs + Send,
    >(
        &self,
        key: K,
        path: P,
        value: V,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.STRAPPEND")
            .with_arg(key)
            .with_arg(path)
            .with_arg(value);
        self.execute_command(cmd, None).await
    }

    /// Get the length of the string(s) at `path` (`JSON.STRLEN`).
    async fn json_strlen<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.STRLEN").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Append `values` to the array(s) at `path` (`JSON.ARRAPPEND`).
    async fn json_arrappend<
        K: ToValkeyArgs + Send,
        P: ToValkeyArgs + Send,
        V: ToValkeyArgs + Send + Sync,
    >(
        &self,
        key: K,
        path: P,
        values: &[V],
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.ARRAPPEND")
            .with_arg(key)
            .with_arg(path)
            .with_arg(values);
        self.execute_command(cmd, None).await
    }

    /// Insert `values` into the array(s) at `path` starting at `index`
    /// (`JSON.ARRINSERT`).
    async fn json_arrinsert<
        K: ToValkeyArgs + Send,
        P: ToValkeyArgs + Send,
        V: ToValkeyArgs + Send + Sync,
    >(
        &self,
        key: K,
        path: P,
        index: i64,
        values: &[V],
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.ARRINSERT")
            .with_arg(key)
            .with_arg(path)
            .with_arg(index)
            .with_arg(values);
        self.execute_command(cmd, None).await
    }

    /// Get the length of the array(s) at `path` (`JSON.ARRLEN`).
    async fn json_arrlen<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.ARRLEN").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Pop an element from the array(s) at `path` at `index` (`JSON.ARRPOP`).
    async fn json_arrpop<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
        index: Option<i64>,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.ARRPOP")
            .with_arg(key)
            .with_arg(path)
            .with_arg(index);
        self.execute_command(cmd, None).await
    }

    /// Trim the array(s) at `path` to the inclusive range `[start, stop]`
    /// (`JSON.ARRTRIM`).
    async fn json_arrtrim<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
        start: i64,
        stop: i64,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.ARRTRIM")
            .with_arg(key)
            .with_arg(path)
            .with_arg(start)
            .with_arg(stop);
        self.execute_command(cmd, None).await
    }

    /// Get the keys of the object(s) at `path` (`JSON.OBJKEYS`).
    async fn json_objkeys<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.OBJKEYS").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Get the number of keys in the object(s) at `path` (`JSON.OBJLEN`).
    async fn json_objlen<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.OBJLEN").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Toggle the boolean value(s) at `path` (`JSON.TOGGLE`).
    async fn json_toggle<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.TOGGLE").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Clear container value(s) at `path` (`JSON.CLEAR`); returns the number of
    /// values cleared.
    async fn json_clear<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("JSON.CLEAR").with_arg(key).with_arg(path);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Find the index of `value` in the array(s) at `path` (`JSON.ARRINDEX`).
    /// Optionally restrict the search to `[start, end)`.
    async fn json_arrindex<
        K: ToValkeyArgs + Send,
        P: ToValkeyArgs + Send,
        V: ToValkeyArgs + Send,
    >(
        &self,
        key: K,
        path: P,
        value: V,
        range: Option<(i64, i64)>,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.ARRINDEX")
            .with_arg(key)
            .with_arg(path)
            .with_arg(value)
            .with_arg(range);
        self.execute_command(cmd, None).await
    }

    /// Get the value(s) at `path` from multiple keys (`JSON.MGET`).
    async fn json_mget<K: ToValkeyArgs + Send + Sync, P: ToValkeyArgs + Send>(
        &self,
        keys: &[K],
        path: P,
    ) -> ValkeyResult<Vec<Option<Bytes>>> {
        let cmd = cmd("JSON.MGET").with_arg(keys).with_arg(path);
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Option::<Bytes>::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Ok(vec![Option::<Bytes>::from_owned_valkey_value(other)?]),
        }
    }

    /// Get the value(s) at `path` in RESP form (`JSON.RESP`).
    async fn json_resp<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.RESP").with_arg(key).with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Report the memory usage of the value(s) at `path`
    /// (`JSON.DEBUG MEMORY`).
    async fn json_debug_memory<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.DEBUG")
            .with_arg("MEMORY")
            .with_arg(key)
            .with_arg(path);
        self.execute_command(cmd, None).await
    }

    /// Report the number of fields in the value(s) at `path`
    /// (`JSON.DEBUG FIELDS`).
    async fn json_debug_fields<K: ToValkeyArgs + Send, P: ToValkeyArgs + Send>(
        &self,
        key: K,
        path: P,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("JSON.DEBUG")
            .with_arg("FIELDS")
            .with_arg(key)
            .with_arg(path);
        self.execute_command(cmd, None).await
    }
}

impl<T: CommandExecutor + ?Sized> JsonCommands for T {}
