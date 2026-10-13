// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! String commands. Mirrors Python's string command surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;
use bytes::Bytes;

/// String commands (`GET`, `SET`, `APPEND`, `INCR`, ...).
#[async_trait]
pub trait StringCommands: CommandExecutor {
    /// Longest common subsequence length between two keys (`LCS ... LEN`).
    async fn lcs_len<K1: ToValkeyArgs + Send, K2: ToValkeyArgs + Send>(
        &self,
        key1: K1,
        key2: K2,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("LCS").with_arg(key1).with_arg(key2).with_arg("LEN");
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the longest common subsequence of two keys (`LCS`).
    async fn lcs<K1: ToValkeyArgs + Send, K2: ToValkeyArgs + Send>(
        &self,
        key1: K1,
        key2: K2,
    ) -> ValkeyResult<Bytes> {
        let cmd = cmd("LCS").with_arg(key1).with_arg(key2);
        Bytes::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the LCS match positions between two keys (`LCS ... IDX`). Returns the
    /// raw structured reply (a map of `matches`/`len`). Pass `min_match_len` to
    /// filter short matches, and `with_match_len` to include per-match lengths.
    async fn lcs_idx<K1: ToValkeyArgs + Send, K2: ToValkeyArgs + Send>(
        &self,
        key1: K1,
        key2: K2,
        min_match_len: Option<i64>,
        with_match_len: bool,
    ) -> ValkeyResult<ValkeyValue> {
        let cmd = cmd("LCS")
            .with_arg(key1)
            .with_arg(key2)
            .with_arg("IDX")
            .with_arg(min_match_len.map(|m| ("MINMATCHLEN", m)))
            .with_arg(with_match_len.then_some("WITHMATCHLEN"));
        self.execute_command(cmd, None).await
    }
}

impl<T: CommandExecutor + ?Sized> StringCommands for T {}
