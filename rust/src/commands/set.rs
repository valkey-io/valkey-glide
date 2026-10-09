// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Set commands. Mirrors Python's set command surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::write::ToValkeyArgs;
use async_trait::async_trait;

/// Set commands (`SADD`, `SREM`, `SMEMBERS`, `SINTER`, ...).
#[async_trait]
pub trait SetCommands: CommandExecutor {
    // TODO #7082: add a `spop` variant that takes a `count` (cf. `srandmember_multiple`),
    // to match the other GLIDE clients.

    /// Cardinality of the intersection of the given sets (`SINTERCARD`).
    async fn sintercard<K: ToValkeyArgs + Send + Sync>(&self, keys: &[K]) -> ValkeyResult<i64> {
        let cmd = cmd("SINTERCARD")
            .with_arg(keys.num_of_args())
            .with_arg(keys);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Cardinality of the intersection of the given sets with a `LIMIT`
    /// (`SINTERCARD numkeys key [key ...] LIMIT limit`). A `limit` of `0` means
    /// no limit.
    async fn sintercard_limit<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        limit: i64,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("SINTERCARD")
            .with_arg(keys.num_of_args())
            .with_arg(keys)
            .with_arg("LIMIT")
            .with_arg(limit);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> SetCommands for T {}
