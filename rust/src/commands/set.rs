// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Set commands. Mirrors Python's set command surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::value::to_glide_error;
use crate::write::ToSingleValkeyArg;
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

    /// Returns if `member` is a member of the set stored at `key`, or
    /// [`SIsMemberStatusCode::KeyNotFound`] if `key` does not exist.
    ///
    /// Since Valkey 9.2.0.
    async fn sismember_if_exists<K: ToSingleValkeyArg + Send, M: ToSingleValkeyArg + Send>(
        &self,
        key: K,
        member: M,
    ) -> ValkeyResult<SIsMemberStatusCode> {
        let cmd = cmd("SISMEMBER")
            .with_arg(key)
            .with_arg(member)
            .with_arg("XX");
        SIsMemberStatusCode::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> SetCommands for T {}

/// The `SISMEMBER ... XX` reply.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum SIsMemberStatusCode {
    /// The key does not exist (`-1`).
    KeyNotFound = -1,
    /// The key exists but the member is not in the set (`0`).
    NotMember = 0,
    /// The member is in the set (`1`).
    Member = 1,
}

impl FromValkeyValue for SIsMemberStatusCode {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        match value {
            ValkeyValue::Int(-1) => Ok(Self::KeyNotFound),
            ValkeyValue::Int(0) => Ok(Self::NotMember),
            ValkeyValue::Int(1) => Ok(Self::Member),
            other => Err(to_glide_error(other, "Value should be -1, 0, or 1.")),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sismember_status_code_decoding() {
        assert_eq!(decode(-1).unwrap(), SIsMemberStatusCode::KeyNotFound);
        assert_eq!(decode(0).unwrap(), SIsMemberStatusCode::NotMember);
        assert_eq!(decode(1).unwrap(), SIsMemberStatusCode::Member);

        assert!(decode(-2).is_err());
        assert!(decode(2).is_err());
        assert!(SIsMemberStatusCode::from_owned_valkey_value(ValkeyValue::Nil).is_err());
    }

    fn decode(i: i64) -> ValkeyResult<SIsMemberStatusCode> {
        SIsMemberStatusCode::from_owned_valkey_value(ValkeyValue::Int(i))
    }
}
