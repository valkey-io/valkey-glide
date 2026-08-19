// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! String commands. Mirrors Python's string command surface.

use crate::error::Result;
use crate::executor::CommandExecutor;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

/// String commands (`GET`, `SET`, `APPEND`, `INCR`, ...).
#[async_trait]
pub trait StringCommands: CommandExecutor {
    /// Longest common subsequence length between two keys (`LCS ... LEN`).
    async fn lcs_len<K1: ToRedisArgs + Send, K2: ToRedisArgs + Send>(
        &self,
        key1: K1,
        key2: K2,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("LCS").arg(key1).arg(key2).arg("LEN");
        crate::value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Get the longest common subsequence of two keys (`LCS`).
    async fn lcs<K1: ToRedisArgs + Send, K2: ToRedisArgs + Send>(
        &self,
        key1: K1,
        key2: K2,
    ) -> Result<Bytes> {
        let mut cmd = Cmd::new();
        cmd.arg("LCS").arg(key1).arg(key2);
        crate::value::to_bytes(self.execute_command(cmd, None).await?)
    }

    /// Get the LCS match positions between two keys (`LCS ... IDX`). Returns the
    /// raw structured reply (a map of `matches`/`len`). Pass `min_match_len` to
    /// filter short matches, and `with_match_len` to include per-match lengths.
    async fn lcs_idx<K1: ToRedisArgs + Send, K2: ToRedisArgs + Send>(
        &self,
        key1: K1,
        key2: K2,
        min_match_len: Option<i64>,
        with_match_len: bool,
    ) -> Result<redis::Value> {
        let mut cmd = Cmd::new();
        cmd.arg("LCS").arg(key1).arg(key2).arg("IDX");
        if let Some(m) = min_match_len {
            cmd.arg("MINMATCHLEN").arg(m);
        }
        if with_match_len {
            cmd.arg("WITHMATCHLEN");
        }
        self.execute_command(cmd, None).await
    }
}

impl<T: CommandExecutor + ?Sized> StringCommands for T {}
