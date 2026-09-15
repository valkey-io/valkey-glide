// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Generic (key) commands. Mirrors Python's generic command surface.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::commands::options::{Limit, MigrateOptions, ObjectType, OrderBy, RestoreOptions};
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ToValkeyArgs;
use crate::value::ValkeyValue;
use async_trait::async_trait;
use bytes::Bytes;

/// Generic key-space commands (`DEL`, `EXISTS`, `EXPIRE`, `TTL`, `RENAME`, ...).
#[async_trait]
pub trait GenericCommands: CommandExecutor {
    // TODO #7082: add `expire`/`pexpire`/`expire_at`/`pexpire_at` variants that take
    // the NX/XX/GT/LT condition options (the `ExpireOptions` type already exists).

    /// Iterate the keyspace with `SCAN`, with `MATCH`/`COUNT`/`TYPE` options
    /// (cursor-style). Returns `(cursor, keys)`; a returned cursor of `"0"`
    /// indicates iteration is complete. For simple iteration prefer the
    /// unified [`crate::AsyncCommands::scan_match`] iterator.
    async fn scan_cursor(
        &self,
        cursor: &str,
        pattern: Option<&[u8]>,
        count: Option<i64>,
        type_filter: Option<ObjectType>,
    ) -> ValkeyResult<(String, Vec<Bytes>)> {
        let mut cmd = Cmd::new();
        cmd.arg("SCAN").arg(cursor);
        if let Some(p) = pattern {
            cmd.arg("MATCH").arg(p);
        }
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        if let Some(t) = type_filter {
            cmd.arg("TYPE").arg(t.to_redis().to_string().to_lowercase());
        }
        let reply = self.execute_command(cmd, None).await?;
        parse_scan_reply(reply)
    }

    /// Get the absolute expiry Unix time in seconds (`EXPIRETIME`).
    async fn expiretime<K: ToValkeyArgs + Send>(&self, key: K) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("EXPIRETIME").arg(key);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the absolute expiry Unix time in milliseconds (`PEXPIRETIME`).
    async fn pexpiretime<K: ToValkeyArgs + Send>(&self, key: K) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("PEXPIRETIME").arg(key);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Return a random key from the keyspace (`RANDOMKEY`).
    async fn randomkey(&self) -> ValkeyResult<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("RANDOMKEY");
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Serialize `key` (`DUMP`). Returns `None` if the key does not exist.
    async fn dump<K: ToValkeyArgs + Send>(&self, key: K) -> ValkeyResult<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("DUMP").arg(key);
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Touch the given keys, returning how many were touched (`TOUCH`).
    async fn touch<K: ToValkeyArgs + Send + Sync>(&self, keys: &[K]) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("TOUCH");
        for k in keys {
            cmd.arg(k);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Copy `source` to `destination` (`COPY`). Set `replace` to overwrite.
    async fn copy<S: ToValkeyArgs + Send, D: ToValkeyArgs + Send>(
        &self,
        source: S,
        destination: D,
        replace: bool,
    ) -> ValkeyResult<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("COPY").arg(source).arg(destination);
        if replace {
            cmd.arg("REPLACE");
        }
        bool::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Sort the elements at `key` (`SORT`), optionally by order and with an
    /// optional `LIMIT offset count`.
    async fn sort<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        order: Option<OrderBy>,
        limit: Option<Limit>,
        alpha: bool,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("SORT").arg(key);
        if let Some(l) = limit {
            cmd.arg("LIMIT").arg(l.offset).arg(l.count);
        }
        if let Some(o) = order {
            cmd.arg(o.as_arg());
        }
        if alpha {
            cmd.arg("ALPHA");
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Bytes::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
        }
    }

    /// Copy `source` to `destination`, optionally into a different logical
    /// database (`COPY ... DB destination_db`). Set `replace` to overwrite.
    async fn copy_with_options<S: ToValkeyArgs + Send, D: ToValkeyArgs + Send>(
        &self,
        source: S,
        destination: D,
        destination_db: Option<i64>,
        replace: bool,
    ) -> ValkeyResult<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("COPY").arg(source).arg(destination);
        if let Some(db) = destination_db {
            cmd.arg("DB").arg(db);
        }
        if replace {
            cmd.arg("REPLACE");
        }
        bool::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Create a key from a serialized payload produced by `DUMP` (`RESTORE`).
    async fn restore<K: ToValkeyArgs + Send, V: ToValkeyArgs + Send>(
        &self,
        key: K,
        ttl_ms: i64,
        serialized: V,
        options: RestoreOptions,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("RESTORE").arg(key).arg(ttl_ms).arg(serialized);
        options.add_to(&mut cmd);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Block until `numreplicas` replicas acknowledge previous writes, or until
    /// `timeout_ms` elapses (`WAIT`). Returns the number of replicas reached.
    async fn wait(&self, numreplicas: i64, timeout_ms: i64) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("WAIT").arg(numreplicas).arg(timeout_ms);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Sort the elements at `key` and store the result into `destination`
    /// (`SORT ... STORE destination`). Returns the number of elements stored.
    async fn sort_store<K: ToValkeyArgs + Send, D: ToValkeyArgs + Send>(
        &self,
        key: K,
        destination: D,
        order: Option<OrderBy>,
        limit: Option<Limit>,
        alpha: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("SORT").arg(key);
        if let Some(l) = limit {
            cmd.arg("LIMIT").arg(l.offset).arg(l.count);
        }
        if let Some(o) = order {
            cmd.arg(o.as_arg());
        }
        if alpha {
            cmd.arg("ALPHA");
        }
        cmd.arg("STORE").arg(destination);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Read-only variant of `SORT` (`SORT_RO`); returns the sorted elements.
    async fn sort_ro<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        order: Option<OrderBy>,
        limit: Option<Limit>,
        alpha: bool,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("SORT_RO").arg(key);
        if let Some(l) = limit {
            cmd.arg("LIMIT").arg(l.offset).arg(l.count);
        }
        if let Some(o) = order {
            cmd.arg(o.as_arg());
        }
        if alpha {
            cmd.arg("ALPHA");
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Bytes::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
        }
    }

    /// Move `key` to another logical database (`MOVE`). Returns whether the key
    /// was moved.
    async fn move_key<K: ToValkeyArgs + Send>(&self, key: K, db: i64) -> ValkeyResult<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("MOVE").arg(key).arg(db);
        bool::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Atomically transfer a key to another instance (`MIGRATE`).
    async fn migrate<H: ToValkeyArgs + Send, K: ToValkeyArgs + Send>(
        &self,
        host: H,
        port: i64,
        key: K,
        destination_db: i64,
        timeout_ms: i64,
        options: MigrateOptions,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("MIGRATE")
            .arg(host)
            .arg(port)
            .arg(key)
            .arg(destination_db)
            .arg(timeout_ms);
        options.add_to(&mut cmd);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Watch the given keys for changes before a transaction (`WATCH`).
    ///
    /// Note: `WATCH` is connection-scoped, but GLIDE multiplexes commands over
    /// shared connections. The optimistic lock is therefore only reliable while
    /// the caller has exclusive use of the client through `EXEC`/`UNWATCH`.
    /// Dedicated scoped connections are tracked in [#6917].
    ///
    /// [#6917]: https://github.com/valkey-io/valkey-glide/issues/6917
    async fn watch<K: ToValkeyArgs + Send + Sync>(&self, keys: &[K]) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("WATCH");
        for k in keys {
            cmd.arg(k);
        }
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Forget all watched keys (`UNWATCH`).
    ///
    /// See [`watch`](Self::watch) for the connection-scoping caveat that
    /// applies to `WATCH`/`UNWATCH` on GLIDE's multiplexed client.
    async fn unwatch(&self) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("UNWATCH");
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

pub(crate) fn parse_scan_reply(reply: ValkeyValue) -> ValkeyResult<(String, Vec<Bytes>)> {
    match reply {
        ValkeyValue::Array(mut items) if items.len() == 2 => {
            let keys_val = items.pop().unwrap();
            let cursor_val = items.pop().unwrap();
            let cursor = String::from_owned_valkey_value(cursor_val)?;
            let keys = match keys_val {
                ValkeyValue::Array(elems) => elems
                    .into_iter()
                    .map(Bytes::from_owned_valkey_value)
                    .collect::<ValkeyResult<Vec<_>>>()?,
                ValkeyValue::Nil => Vec::new(),
                other => {
                    return Err(crate::error::GlideError::Request(format!(
                        "unexpected SCAN keys shape: {other:?}"
                    )));
                }
            };
            Ok((cursor, keys))
        }
        _ => Err(crate::error::GlideError::Request(
            "unexpected SCAN reply shape".into(),
        )),
    }
}

impl<T: CommandExecutor + ?Sized> GenericCommands for T {}

#[cfg(test)]
mod scan_arg_probe {
    #[test]
    fn scan_type_arg_encoding() {
        // strum's Display renders the variant name ("ZSet"); the wire arg
        // must be the lowercase type name the server expects.
        let s = crate::commands::options::ObjectType::ZSet
            .to_redis()
            .to_string()
            .to_lowercase();
        assert_eq!(s, "zset");
    }
}
