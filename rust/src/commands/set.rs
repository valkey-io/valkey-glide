// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Set commands. Mirrors Python's set command surface.

use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};
use std::collections::HashSet;

fn collect_bytes(v: redis::Value) -> Result<Vec<Bytes>> {
    match v {
        redis::Value::Array(items) => items.into_iter().map(value::to_bytes).collect(),
        redis::Value::Set(items) => items.into_iter().map(value::to_bytes).collect(),
        redis::Value::Nil => Ok(Vec::new()),
        other => Ok(vec![value::to_bytes(other)?]),
    }
}

/// Set commands (`SADD`, `SREM`, `SMEMBERS`, `SINTER`, ...).
#[async_trait]
pub trait SetCommands: CommandExecutor {
    /// Cardinality of the intersection of the given sets (`SINTERCARD`).
    async fn sintercard<K: ToRedisArgs + Send + Sync>(&self, keys: &[K]) -> Result<i64> {
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
    async fn sintercard_limit<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
        limit: i64,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("SINTERCARD").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        cmd.arg("LIMIT").arg(limit);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    #[doc(hidden)]
    async fn set_op<K: ToRedisArgs + Send + Sync>(
        &self,
        op: &'static str,
        keys: &[K],
    ) -> Result<HashSet<Bytes>> {
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
    async fn set_op_store<D: ToRedisArgs + Send, K: ToRedisArgs + Send + Sync>(
        &self,
        op: &'static str,
        destination: D,
        keys: &[K],
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg(op).arg(destination);
        for k in keys {
            cmd.arg(k);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> SetCommands for T {}
