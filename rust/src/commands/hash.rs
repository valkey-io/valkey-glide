// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Hash commands. Mirrors Python's hash command surface.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::commands::options::{ExpireOptions, Expiry, HashFieldConditionalChange, SetExpiry};
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ToValkeyArgs;
use crate::value::ValkeyValue;
use async_trait::async_trait;
use bytes::Bytes;

/// Hash commands (`HSET`, `HGET`, `HGETALL`, `HDEL`, ...).
#[async_trait]
pub trait HashCommands: CommandExecutor {
    // TODO #7082: add a multi-field `hset` that returns the count of newly-added
    // fields, to match the other GLIDE clients.

    /// Get the values of multiple fields (`HMGET`).
    async fn hmget<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
    ) -> ValkeyResult<Vec<Option<Bytes>>> {
        let mut cmd = Cmd::new();
        cmd.arg("HMGET").arg(key);
        for f in fields {
            cmd.arg(f);
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Option::<Bytes>::from_owned_valkey_value)
                .collect(),
            other => Ok(vec![Option::<Bytes>::from_owned_valkey_value(other)?]),
        }
    }

    /// Get the string length of a field's value (`HSTRLEN`).
    async fn hstrlen<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send>(
        &self,
        key: K,
        field: F,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("HSTRLEN").arg(key).arg(field);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get a random field from the hash (`HRANDFIELD`).
    async fn hrandfield<K: ToValkeyArgs + Send>(&self, key: K) -> ValkeyResult<Option<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("HRANDFIELD").arg(key);
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get `count` random fields from the hash (`HRANDFIELD key count`).
    async fn hrandfield_count<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        count: i64,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("HRANDFIELD").arg(key).arg(count);
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Get `count` random fields with their values
    /// (`HRANDFIELD key count WITHVALUES`).
    async fn hrandfield_withvalues<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        count: i64,
    ) -> ValkeyResult<Vec<(Bytes, Bytes)>> {
        let mut cmd = Cmd::new();
        cmd.arg("HRANDFIELD").arg(key).arg(count).arg("WITHVALUES");
        collect_pairs(self.execute_command(cmd, None).await?)
    }

    /// Incrementally iterate a hash returning only field names
    /// (`HSCAN ... NOVALUES`). Returns `(cursor, fields)`.
    async fn hscan_novalues<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        cursor: &str,
        pattern: Option<&[u8]>,
        count: Option<i64>,
    ) -> ValkeyResult<(String, Vec<Bytes>)> {
        let mut cmd = Cmd::new();
        cmd.arg("HSCAN").arg(key).arg(cursor);
        if let Some(p) = pattern {
            cmd.arg("MATCH").arg(p);
        }
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        cmd.arg("NOVALUES");
        crate::commands::generic::parse_scan_reply(self.execute_command(cmd, None).await?)
    }

    /// Set an expiry in seconds on one or more hash fields (`HEXPIRE`). Returns a
    /// per-field status code.
    async fn hexpire<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        seconds: i64,
        fields: &[F],
        option: Option<ExpireOptions>,
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire("HEXPIRE", key, Some(seconds), fields, option)
            .await
    }

    /// Set an expiry at an absolute Unix time (seconds) on hash fields
    /// (`HEXPIREAT`).
    async fn hexpireat<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        unix_seconds: i64,
        fields: &[F],
        option: Option<ExpireOptions>,
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire("HEXPIREAT", key, Some(unix_seconds), fields, option)
            .await
    }

    /// Get the absolute expiry Unix time (seconds) of hash fields
    /// (`HEXPIRETIME`).
    async fn hexpiretime<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire::<K, F>("HEXPIRETIME", key, None, fields, None)
            .await
    }

    /// Set an expiry in milliseconds on hash fields (`HPEXPIRE`).
    async fn hpexpire<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        milliseconds: i64,
        fields: &[F],
        option: Option<ExpireOptions>,
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire("HPEXPIRE", key, Some(milliseconds), fields, option)
            .await
    }

    /// Set an expiry at an absolute Unix time (milliseconds) on hash fields
    /// (`HPEXPIREAT`).
    async fn hpexpireat<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        unix_milliseconds: i64,
        fields: &[F],
        option: Option<ExpireOptions>,
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire("HPEXPIREAT", key, Some(unix_milliseconds), fields, option)
            .await
    }

    /// Get the absolute expiry Unix time (milliseconds) of hash fields
    /// (`HPEXPIRETIME`).
    async fn hpexpiretime<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire::<K, F>("HPEXPIRETIME", key, None, fields, None)
            .await
    }

    /// Get the remaining TTL in seconds of hash fields (`HTTL`).
    async fn httl<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire::<K, F>("HTTL", key, None, fields, None)
            .await
    }

    /// Get the remaining TTL in milliseconds of hash fields (`HPTTL`).
    async fn hpttl<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire::<K, F>("HPTTL", key, None, fields, None)
            .await
    }

    /// Remove the expiry from hash fields (`HPERSIST`). Returns a per-field
    /// status code.
    async fn hpersist<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
    ) -> ValkeyResult<Vec<i64>> {
        self.hfield_expire::<K, F>("HPERSIST", key, None, fields, None)
            .await
    }

    #[doc(hidden)]
    async fn hfield_expire<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        op: &'static str,
        key: K,
        value: Option<i64>,
        fields: &[F],
        option: Option<ExpireOptions>,
    ) -> ValkeyResult<Vec<i64>> {
        let mut cmd = Cmd::new();
        cmd.arg(op).arg(key);
        if let Some(v) = value {
            cmd.arg(v);
        }
        if let Some(o) = option {
            o.add_to(&mut cmd);
        }
        cmd.arg("FIELDS").arg(fields.len());
        for f in fields {
            cmd.arg(f);
        }
        collect_i64(self.execute_command(cmd, None).await?)
    }

    /// Get the values of hash fields, optionally changing their expiry
    /// (`HGETEX`).
    async fn hgetex<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send + Sync>(
        &self,
        key: K,
        fields: &[F],
        expiry: Option<Expiry>,
    ) -> ValkeyResult<Vec<Option<Bytes>>> {
        let mut cmd = Cmd::new();
        cmd.arg("HGETEX").arg(key);
        if let Some(e) = expiry {
            cmd.arg(e);
        }
        cmd.arg("FIELDS").arg(fields.len());
        for f in fields {
            cmd.arg(f);
        }
        match self.execute_command(cmd, None).await? {
            ValkeyValue::Array(items) => items
                .into_iter()
                .map(Option::<Bytes>::from_owned_valkey_value)
                .collect(),
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Ok(vec![Option::<Bytes>::from_owned_valkey_value(other)?]),
        }
    }

    /// Set hash field values with an optional field condition and expiry
    /// (`HSETEX`). Returns `1` if all fields were set, `0` otherwise.
    // TODO #6904: investigate whether the latest redis-rs version defines HSETEX and
    // its option types, to mirror redis-rs instead of the Python-shaped signature.
    async fn hsetex<K, F, V>(
        &self,
        key: K,
        field_values: &[(F, V)],
        condition: Option<HashFieldConditionalChange>,
        expiry: Option<SetExpiry>,
    ) -> ValkeyResult<i64>
    where
        K: ToValkeyArgs + Send + Sync,
        F: ToValkeyArgs + Send + Sync,
        V: ToValkeyArgs + Send + Sync,
    {
        let mut cmd = Cmd::new();
        cmd.arg("HSETEX").arg(key);
        if let Some(c) = condition {
            cmd.arg(c.as_arg());
        }
        if let Some(e) = expiry {
            cmd.arg(e);
        }
        cmd.arg("FIELDS").arg(field_values.len());
        for (f, v) in field_values {
            cmd.arg(f).arg(v);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

/// Collect an array reply into `Vec<i64>`.
fn collect_i64(v: ValkeyValue) -> ValkeyResult<Vec<i64>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(i64::from_owned_valkey_value)
            .collect(),
        other => Ok(vec![i64::from_owned_valkey_value(other)?]),
    }
}

/// Parse a flat `[a, b, a, b, ...]` reply into `(a, b)` pairs.
fn collect_pairs(v: ValkeyValue) -> ValkeyResult<Vec<(Bytes, Bytes)>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Map(pairs) => pairs
            .into_iter()
            .map(|(a, b)| {
                Ok((
                    Bytes::from_owned_valkey_value(a)?,
                    Bytes::from_owned_valkey_value(b)?,
                ))
            })
            .collect(),
        ValkeyValue::Array(items) => {
            if items
                .iter()
                .all(|it| matches!(it, ValkeyValue::Array(inner) if inner.len() == 2))
            {
                let mut out = Vec::with_capacity(items.len());
                for it in items {
                    if let ValkeyValue::Array(mut pair) = it {
                        let b = Bytes::from_owned_valkey_value(pair.pop().unwrap())?;
                        let a = Bytes::from_owned_valkey_value(pair.pop().unwrap())?;
                        out.push((a, b));
                    }
                }
                Ok(out)
            } else {
                let mut out = Vec::with_capacity(items.len() / 2);
                let mut iter = items.into_iter();
                while let (Some(a), Some(b)) = (iter.next(), iter.next()) {
                    out.push((
                        Bytes::from_owned_valkey_value(a)?,
                        Bytes::from_owned_valkey_value(b)?,
                    ));
                }
                Ok(out)
            }
        }
        other => Ok(vec![(Bytes::from_owned_valkey_value(other)?, Bytes::new())]),
    }
}

fn collect_bytes(v: ValkeyValue) -> ValkeyResult<Vec<Bytes>> {
    match v {
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(Bytes::from_owned_valkey_value)
            .collect(),
        ValkeyValue::Nil => Ok(Vec::new()),
        other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
    }
}

impl<T: CommandExecutor + ?Sized> HashCommands for T {}
