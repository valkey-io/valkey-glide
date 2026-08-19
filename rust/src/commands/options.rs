// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Shared option types used by multiple command families.
//!
//! Mirrors the Python `glide_shared.commands.core_options` and
//! `command_args` modules.

use redis::Cmd;

/// Condition under which a `SET` (or similar) should be applied.
///
/// Mirrors Python `ConditionalChange`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConditionalChange {
    /// Only set if the key already exists (`XX`).
    OnlyIfExists,
    /// Only set if the key does not exist (`NX`).
    OnlyIfDoesNotExist,
}

impl ConditionalChange {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        match self {
            ConditionalChange::OnlyIfExists => cmd.arg("XX"),
            ConditionalChange::OnlyIfDoesNotExist => cmd.arg("NX"),
        };
    }
}

/// Expiry for `SET` / `GETEX`.
///
/// Mirrors Python `ExpirySet` / `ExpiryType`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpirySet {
    /// Set expiry, in seconds (`EX`).
    Seconds(u64),
    /// Set expiry, in milliseconds (`PX`).
    Milliseconds(u64),
    /// Set expiry at a Unix timestamp, in seconds (`EXAT`).
    UnixSeconds(u64),
    /// Set expiry at a Unix timestamp, in milliseconds (`PXAT`).
    UnixMilliseconds(u64),
    /// Retain the existing TTL (`KEEPTTL`).
    KeepExisting,
    /// Remove any TTL (`PERSIST`, used by `GETEX`).
    Persist,
}

impl ExpirySet {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        match self {
            ExpirySet::Seconds(v) => {
                cmd.arg("EX").arg(v);
            }
            ExpirySet::Milliseconds(v) => {
                cmd.arg("PX").arg(v);
            }
            ExpirySet::UnixSeconds(v) => {
                cmd.arg("EXAT").arg(v);
            }
            ExpirySet::UnixMilliseconds(v) => {
                cmd.arg("PXAT").arg(v);
            }
            ExpirySet::KeepExisting => {
                cmd.arg("KEEPTTL");
            }
            ExpirySet::Persist => {
                cmd.arg("PERSIST");
            }
        }
    }
}

/// Conditions for `EXPIRE`/`PEXPIRE`/`EXPIREAT`/`PEXPIREAT`.
///
/// Mirrors Python `ExpireOptions`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpireOptions {
    /// Set expiry only when the key has no existing expiry (`NX`).
    HasNoExpiry,
    /// Set expiry only when the key has an existing expiry (`XX`).
    HasExistingExpiry,
    /// Set expiry only when the new expiry is greater than the current one (`GT`).
    NewExpiryGreaterThanCurrent,
    /// Set expiry only when the new expiry is less than the current one (`LT`).
    NewExpiryLessThanCurrent,
}

impl ExpireOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        match self {
            ExpireOptions::HasNoExpiry => cmd.arg("NX"),
            ExpireOptions::HasExistingExpiry => cmd.arg("XX"),
            ExpireOptions::NewExpiryGreaterThanCurrent => cmd.arg("GT"),
            ExpireOptions::NewExpiryLessThanCurrent => cmd.arg("LT"),
        };
    }
}

/// Policy for the `FUNCTION RESTORE` command.
///
/// Mirrors Python `FunctionRestorePolicy`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum FunctionRestorePolicy {
    /// Append to existing libraries, aborting on name collision (`APPEND`).
    #[default]
    Append,
    /// Delete all existing libraries before restoring (`FLUSH`).
    Flush,
    /// Append, replacing existing libraries on name collision (`REPLACE`).
    Replace,
}

impl FunctionRestorePolicy {
    pub(crate) fn as_arg(&self) -> &'static str {
        match self {
            FunctionRestorePolicy::Append => "APPEND",
            FunctionRestorePolicy::Flush => "FLUSH",
            FunctionRestorePolicy::Replace => "REPLACE",
        }
    }
}

/// Mode for the `CLIENT PAUSE` command.
///
/// Mirrors Python `ClientPauseMode`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClientPauseMode {
    /// Pause all client commands (`ALL`).
    All,
    /// Pause only client write commands (`WRITE`).
    Write,
}

impl ClientPauseMode {
    pub(crate) fn as_arg(&self) -> &'static str {
        match self {
            ClientPauseMode::All => "ALL",
            ClientPauseMode::Write => "WRITE",
        }
    }
}

/// Field-conditional change option for `HSETEX`.
///
/// Mirrors Python `HashFieldConditionalChange`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HashFieldConditionalChange {
    /// Only set the fields if all of them already exist (`FXX`).
    OnlyIfAllExist,
    /// Only set the fields if none of them already exist (`FNX`).
    OnlyIfNoneExist,
}

impl HashFieldConditionalChange {
    pub(crate) fn as_arg(&self) -> &'static str {
        match self {
            HashFieldConditionalChange::OnlyIfAllExist => "FXX",
            HashFieldConditionalChange::OnlyIfNoneExist => "FNX",
        }
    }
}

/// Options for the `MIGRATE` command.
///
/// Mirrors Python `MigrateOptions`.
#[derive(Debug, Clone, Default)]
pub struct MigrateOptions {
    /// Do not remove the key from the source instance (`COPY`).
    pub copy: bool,
    /// Replace an existing key on the destination (`REPLACE`).
    pub replace: bool,
    /// Password for `AUTH`, or with `username` for `AUTH2`.
    pub password: Option<String>,
    /// Username for `AUTH2` (requires `password`).
    pub username: Option<String>,
}

impl MigrateOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if self.copy {
            cmd.arg("COPY");
        }
        if self.replace {
            cmd.arg("REPLACE");
        }
        match (&self.username, &self.password) {
            (Some(u), Some(p)) => {
                cmd.arg("AUTH2").arg(u).arg(p);
            }
            (None, Some(p)) => {
                cmd.arg("AUTH").arg(p);
            }
            _ => {}
        }
    }
}

/// Options for the `RESTORE` command.
///
/// Mirrors the option surface of Python's `restore(...)`.
#[derive(Debug, Clone, Copy, Default)]
pub struct RestoreOptions {
    /// Replace the key if it already exists (`REPLACE`).
    pub replace: bool,
    /// Treat `ttl` as an absolute Unix timestamp in milliseconds (`ABSTTL`).
    pub absttl: bool,
    /// Set the key's idle time, in seconds (`IDLETIME`).
    pub idletime: Option<i64>,
    /// Set the key's access frequency (`FREQ`), for `LFU` eviction policies.
    pub frequency: Option<i64>,
}

impl RestoreOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if self.replace {
            cmd.arg("REPLACE");
        }
        if self.absttl {
            cmd.arg("ABSTTL");
        }
        if let Some(i) = self.idletime {
            cmd.arg("IDLETIME").arg(i);
        }
        if let Some(f) = self.frequency {
            cmd.arg("FREQ").arg(f);
        }
    }
}

/// Sort/scan ordering.
///
/// Mirrors Python `OrderBy`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OrderBy {
    /// Ascending order (`ASC`).
    Asc,
    /// Descending order (`DESC`).
    Desc,
}

impl OrderBy {
    pub(crate) fn as_arg(&self) -> &'static str {
        match self {
            OrderBy::Asc => "ASC",
            OrderBy::Desc => "DESC",
        }
    }
}

/// A `LIMIT offset count` clause.
///
/// Mirrors Python `Limit`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Limit {
    /// The offset from the start of the result set.
    pub offset: i64,
    /// The maximum number of elements to include.
    pub count: i64,
}

/// The type of a key, used by `OBJECT`/`TYPE`/`SCAN`.
///
/// Mirrors Python `ObjectType`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ObjectType {
    /// String.
    String,
    /// List.
    List,
    /// Set.
    Set,
    /// Sorted set.
    ZSet,
    /// Hash.
    Hash,
    /// Stream.
    Stream,
}

impl ObjectType {
    /// Map to the vendored `redis` crate's `ObjectType` (used by cluster scan).
    pub(crate) fn to_redis(self) -> redis::ObjectType {
        match self {
            ObjectType::String => redis::ObjectType::String,
            ObjectType::List => redis::ObjectType::List,
            ObjectType::Set => redis::ObjectType::Set,
            ObjectType::ZSet => redis::ObjectType::ZSet,
            ObjectType::Hash => redis::ObjectType::Hash,
            ObjectType::Stream => redis::ObjectType::Stream,
        }
    }
}

/// Server data-flush mode.
///
/// Mirrors Python `FlushMode`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum FlushMode {
    /// Flush synchronously (`SYNC`).
    #[default]
    Sync,
    /// Flush asynchronously (`ASYNC`).
    Async,
}

impl FlushMode {
    pub(crate) fn as_arg(&self) -> &'static str {
        match self {
            FlushMode::Sync => "SYNC",
            FlushMode::Async => "ASYNC",
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Collect a command's arguments as UTF-8 strings for assertions.
    fn args_of(cmd: &Cmd) -> Vec<String> {
        cmd.args_iter()
            .filter_map(|a| match a {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).into_owned()),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    #[test]
    fn conditional_change_args() {
        let mut cmd = Cmd::new();
        ConditionalChange::OnlyIfExists.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["XX"]);

        let mut cmd = Cmd::new();
        ConditionalChange::OnlyIfDoesNotExist.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["NX"]);
    }

    #[test]
    fn expiry_set_args() {
        let mut cmd = Cmd::new();
        ExpirySet::Seconds(60).add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["EX", "60"]);

        let mut cmd = Cmd::new();
        ExpirySet::Milliseconds(1500).add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["PX", "1500"]);

        let mut cmd = Cmd::new();
        ExpirySet::KeepExisting.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["KEEPTTL"]);

        let mut cmd = Cmd::new();
        ExpirySet::Persist.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["PERSIST"]);
    }

    #[test]
    fn expire_options_args() {
        let cases = [
            (ExpireOptions::HasNoExpiry, "NX"),
            (ExpireOptions::HasExistingExpiry, "XX"),
            (ExpireOptions::NewExpiryGreaterThanCurrent, "GT"),
            (ExpireOptions::NewExpiryLessThanCurrent, "LT"),
        ];
        for (opt, expected) in cases {
            let mut cmd = Cmd::new();
            opt.add_to(&mut cmd);
            assert_eq!(args_of(&cmd), vec![expected]);
        }
    }

    #[test]
    fn simple_enum_args() {
        assert_eq!(OrderBy::Asc.as_arg(), "ASC");
        assert_eq!(OrderBy::Desc.as_arg(), "DESC");
        assert_eq!(FlushMode::Sync.as_arg(), "SYNC");
        assert_eq!(FlushMode::Async.as_arg(), "ASYNC");
    }

    #[test]
    fn restore_options_args() {
        let mut cmd = Cmd::new();
        RestoreOptions::default().add_to(&mut cmd);
        assert!(args_of(&cmd).is_empty());

        let opts = RestoreOptions {
            replace: true,
            absttl: true,
            idletime: Some(100),
            frequency: Some(5),
        };
        let mut cmd = Cmd::new();
        opts.add_to(&mut cmd);
        assert_eq!(
            args_of(&cmd),
            vec!["REPLACE", "ABSTTL", "IDLETIME", "100", "FREQ", "5"]
        );
    }

    #[test]
    fn migrate_options_args() {
        let mut cmd = Cmd::new();
        MigrateOptions::default().add_to(&mut cmd);
        assert!(args_of(&cmd).is_empty());

        let opts = MigrateOptions {
            copy: true,
            replace: true,
            password: Some("pw".into()),
            username: None,
        };
        let mut cmd = Cmd::new();
        opts.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["COPY", "REPLACE", "AUTH", "pw"]);

        let opts = MigrateOptions {
            copy: false,
            replace: false,
            password: Some("pw".into()),
            username: Some("user".into()),
        };
        let mut cmd = Cmd::new();
        opts.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["AUTH2", "user", "pw"]);
    }

    #[test]
    fn misc_option_args() {
        assert_eq!(ClientPauseMode::All.as_arg(), "ALL");
        assert_eq!(ClientPauseMode::Write.as_arg(), "WRITE");
        assert_eq!(FunctionRestorePolicy::Append.as_arg(), "APPEND");
        assert_eq!(FunctionRestorePolicy::Flush.as_arg(), "FLUSH");
        assert_eq!(FunctionRestorePolicy::Replace.as_arg(), "REPLACE");
        assert_eq!(HashFieldConditionalChange::OnlyIfAllExist.as_arg(), "FXX");
        assert_eq!(HashFieldConditionalChange::OnlyIfNoneExist.as_arg(), "FNX");
    }
}
