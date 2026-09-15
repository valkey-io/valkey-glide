// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Set commands. Mirrors Python's set command surface.

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::executor::CommandExecutor;
use crate::value;
use crate::value::ToValkeyArgs;
use crate::value::ValkeyValue;
use async_trait::async_trait;
use bytes::Bytes;
use std::collections::HashSet;

fn collect_bytes(v: ValkeyValue) -> ValkeyResult<Vec<Bytes>> {
    match v {
        ValkeyValue::Array(items) => items.into_iter().map(value::to_bytes).collect(),
        ValkeyValue::Set(items) => items.into_iter().map(value::to_bytes).collect(),
        ValkeyValue::Nil => Ok(Vec::new()),
        other => Ok(vec![value::to_bytes(other)?]),
    }
}

/// Set commands (`SADD`, `SREM`, `SMEMBERS`, `SINTER`, ...).
#[async_trait]
pub trait SetCommands: CommandExecutor {
    // TODO #7082: add a `spop` variant that takes a `count` (cf. `srandmember_multiple`),
    // to match the other GLIDE clients.

    /// Cardinality of the intersection of the given sets (`SINTERCARD`).
    async fn sintercard<K: ToValkeyArgs + Send + Sync>(&self, keys: &[K]) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("SINTERCARD").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Cardinality of the intersection of the given sets with a `LIMIT`
    /// (`SINTERCARD numkeys key [key ...] LIMIT limit`). A `limit` of `0` means
    /// no limit.
    async fn sintercard_limit<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        limit: i64,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("SINTERCARD").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        cmd.arg("LIMIT").arg(limit);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    #[doc(hidden)]
    async fn set_op<K: ToValkeyArgs + Send + Sync>(
        &self,
        op: &'static str,
        keys: &[K],
    ) -> ValkeyResult<HashSet<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg(op);
        for k in keys {
            cmd.arg(k);
        }
        Ok(collect_bytes(self.execute_command(cmd, None).await?)?
            .into_iter()
            .collect())
    }

    #[doc(hidden)]
    async fn set_op_store<D: ToValkeyArgs + Send, K: ToValkeyArgs + Send + Sync>(
        &self,
        op: &'static str,
        destination: D,
        keys: &[K],
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg(op).arg(destination);
        for k in keys {
            cmd.arg(k);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> SetCommands for T {}
