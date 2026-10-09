// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Hash commands. Mirrors Python's hash command surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;

/// Hash commands (`HSET`, `HGET`, `HGETALL`, `HDEL`, ...).
#[async_trait]
pub trait HashCommands: CommandExecutor {
    // TODO #7082: add a multi-field `hset` that returns the count of newly-added
    // fields, to match the other GLIDE clients.

    /// Get the string length of a field's value (`HSTRLEN`).
    async fn hstrlen<K: ToValkeyArgs + Send, F: ToValkeyArgs + Send>(
        &self,
        key: K,
        field: F,
    ) -> ValkeyResult<i64> {
        let mut cmd = cmd("HSTRLEN");
        cmd.arg(key).arg(field);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get a random field from the hash (`HRANDFIELD`).
    async fn hrandfield<K: ToValkeyArgs + Send>(&self, key: K) -> ValkeyResult<Option<Bytes>> {
        let mut cmd = cmd("HRANDFIELD");
        cmd.arg(key);
        Option::<Bytes>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get `count` random fields from the hash (`HRANDFIELD key count`).
    async fn hrandfield_count<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        count: i64,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = cmd("HRANDFIELD");
        cmd.arg(key).arg(count);
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Get `count` random fields with their values
    /// (`HRANDFIELD key count WITHVALUES`).
    async fn hrandfield_withvalues<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        count: i64,
    ) -> ValkeyResult<Vec<(Bytes, Bytes)>> {
        let mut cmd = cmd("HRANDFIELD");
        cmd.arg(key).arg(count).arg("WITHVALUES");
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
        let mut cmd = cmd("HSCAN");
        cmd.arg(key).arg(cursor);
        if let Some(p) = pattern {
            cmd.arg("MATCH").arg(p);
        }
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        cmd.arg("NOVALUES");
        crate::commands::generic::parse_scan_reply(self.execute_command(cmd, None).await?)
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
