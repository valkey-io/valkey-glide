// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Shared option types used by multiple command families.
//!
//! Mirrors the Python `glide_shared.commands.core_options` and
//! `command_args` modules.

use crate::write::ToSingleValkeyArg;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyWrite;

/// Condition for the hash-field expire commands.
///
/// Mirrors redis-rs's `ExpireOption` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpireOption {
    /// Set the expiry regardless of the field's current expiry (no argument).
    NONE,
    /// Set the expiry only when the field has no expiry (`NX`).
    NX,
    /// Set the expiry only when the field has an existing expiry (`XX`).
    XX,
    /// Set the expiry only when the new expiry is greater than the current one (`GT`).
    GT,
    /// Set the expiry only when the new expiry is less than the current one (`LT`).
    LT,
}

impl ToValkeyArgs for ExpireOption {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            ExpireOption::NONE => {}
            ExpireOption::NX => out.write_arg(b"NX"),
            ExpireOption::XX => out.write_arg(b"XX"),
            ExpireOption::GT => out.write_arg(b"GT"),
            ExpireOption::LT => out.write_arg(b"LT"),
        }
    }
}

/// Expiry to apply when setting a value.
///
/// Mirrors redis-rs's `SetExpiry` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SetExpiry {
    /// Expire after the given number of seconds (`EX`).
    EX(u64),
    /// Expire after the given number of milliseconds (`PX`).
    PX(u64),
    /// Expire at the given Unix time in seconds (`EXAT`).
    EXAT(u64),
    /// Expire at the given Unix time in milliseconds (`PXAT`).
    PXAT(u64),
    /// Retain the key's existing TTL (`KEEPTTL`).
    KEEPTTL,
}

impl ToValkeyArgs for SetExpiry {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        let mut kw = |k: &[u8], v: u64| {
            out.write_arg(k);
            out.write_arg_fmt(v);
        };
        match self {
            SetExpiry::EX(secs) => kw(b"EX", *secs),
            SetExpiry::PX(millis) => kw(b"PX", *millis),
            SetExpiry::EXAT(ts) => kw(b"EXAT", *ts),
            SetExpiry::PXAT(ts) => kw(b"PXAT", *ts),
            SetExpiry::KEEPTTL => out.write_arg(b"KEEPTTL"),
        }
    }
}

/// Existence check for `SET` and `GEOADD`.
///
/// Mirrors redis-rs's `ExistenceCheck` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExistenceCheck {
    /// Only set the key if it does not already exist (`NX`).
    NX,
    /// Only set the key if it already exists (`XX`).
    XX,
}

impl ToValkeyArgs for ExistenceCheck {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self {
            ExistenceCheck::NX => b"NX".as_slice(),
            ExistenceCheck::XX => b"XX".as_slice(),
        });
    }
}

/// Field existence check for `HSETEX`.
///
/// Mirrors redis-rs's `FieldExistenceCheck` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FieldExistenceCheck {
    /// Only set the fields if none of them already exist (`FNX`).
    FNX,
    /// Only set the fields if all of them already exist (`FXX`).
    FXX,
}

impl ToValkeyArgs for FieldExistenceCheck {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self {
            FieldExistenceCheck::FNX => b"FNX".as_slice(),
            FieldExistenceCheck::FXX => b"FXX".as_slice(),
        });
    }
}

/// Options for the `HSETEX` command.
///
/// Mirrors redis-rs's `HashFieldExpirationOptions` type.
#[derive(Debug, Clone, Copy, Default)]
pub struct HashFieldExpirationOptions {
    existence_check: Option<FieldExistenceCheck>,
    expiration: Option<SetExpiry>,
}

impl HashFieldExpirationOptions {
    /// Set the field existence check (`FNX`/`FXX`).
    pub fn set_existence_check(mut self, field_existence_check: FieldExistenceCheck) -> Self {
        self.existence_check = Some(field_existence_check);
        self
    }

    /// Set the fields' expiry.
    pub fn set_expiration(mut self, expiration: SetExpiry) -> Self {
        self.expiration = Some(expiration);
        self
    }
}

impl ToValkeyArgs for HashFieldExpirationOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(ref existence_check) = self.existence_check {
            existence_check.write_valkey_args(out);
        }
        if let Some(ref expiration) = self.expiration {
            expiration.write_valkey_args(out);
        }
    }
}

/// Options for the `COPY` command.
///
/// Mirrors redis-rs's `CopyOptions` type.
#[derive(Debug, Clone, Copy)]
pub struct CopyOptions<Db: ToString> {
    db: Option<Db>,
    replace: bool,
}

impl Default for CopyOptions<&'static str> {
    fn default() -> Self {
        CopyOptions {
            db: None,
            replace: false,
        }
    }
}

impl<Db: ToString> CopyOptions<Db> {
    /// Copy into the given logical database (`DB`).
    pub fn db<Db2: ToString>(self, db: Db2) -> CopyOptions<Db2> {
        CopyOptions {
            db: Some(db),
            replace: self.replace,
        }
    }

    /// Overwrite the destination key if it exists (`REPLACE`).
    pub fn replace(mut self, replace: bool) -> Self {
        self.replace = replace;
        self
    }
}

impl<Db: ToString> ToValkeyArgs for CopyOptions<Db> {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(ref db) = self.db {
            out.write_arg(b"DB");
            out.write_arg(db.to_string().as_bytes());
        }
        if self.replace {
            out.write_arg(b"REPLACE");
        }
    }
}

/// Options for the `FLUSHALL` command.
///
/// Mirrors redis-rs's `FlushAllOptions` type.
#[derive(Debug, Clone, Copy, Default)]
pub struct FlushAllOptions {
    /// Flush synchronously (`SYNC`) if `true`, asynchronously (`ASYNC`) otherwise.
    pub blocking: bool,
}

impl FlushAllOptions {
    /// Set whether to flush synchronously (`SYNC`) or asynchronously (`ASYNC`).
    pub fn blocking(mut self, blocking: bool) -> Self {
        self.blocking = blocking;
        self
    }
}

impl ToValkeyArgs for FlushAllOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(if self.blocking { b"SYNC" } else { b"ASYNC" });
    }
}

/// Options for the `FLUSHDB` command.
///
/// Mirrors redis-rs's `FlushDbOptions` type.
pub type FlushDbOptions = FlushAllOptions;

/// Options for the `FUNCTION FLUSH` command.
pub type FunctionFlushOptions = FlushAllOptions;

/// Value comparison for `SET` — whether the key's current value must equal
/// the given one.
///
/// Mirrors redis-rs's `ValueComparison` type.
/// Only `IFEQ` is supported.
//
// GLIDE's `ValueComparison` diverges from redis-rs by holding a `Vec<u8>`
// instead of a `String`: redis-rs builds that `String` lossily, silently
// breaking comparisons of binary (non-UTF-8) values. See #5046 for a related
// binary `IFEQ` issue in the Java client.
//
// TODO #7237: Add `IFNE` (and `ValueComparison::ifne`) for Valkey 9.2.
// TODO #7238: Use for `del_ex` (`DELEX`, Valkey 9.2).
#[derive(Debug, Clone, PartialEq, Eq)]
#[non_exhaustive]
pub enum ValueComparison {
    /// Only set if the current value equals the given value (`IFEQ`).
    IFEQ(Vec<u8>),
}

impl ValueComparison {
    /// Compare for equality with the given value (`IFEQ`).
    pub fn ifeq(value: impl ToSingleValkeyArg) -> Self {
        Self::IFEQ(Self::arg_to_bytes(value))
    }

    fn arg_to_bytes(value: impl ToSingleValkeyArg) -> Vec<u8> {
        value.to_valkey_args().swap_remove(0)
    }
}

impl ToValkeyArgs for ValueComparison {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            ValueComparison::IFEQ(value) => {
                out.write_arg(b"IFEQ");
                out.write_arg(value);
            }
        }
    }
}

/// Options for the `SET` command.
///
/// Mirrors redis-rs's `SetOptions` type.
#[derive(Clone, Default)]
pub struct SetOptions {
    conditional_set: Option<ExistenceCheck>,
    value_comparison: Option<ValueComparison>,
    get: bool,
    expiration: Option<SetExpiry>,
}

impl SetOptions {
    /// Set the existence check (`NX`/`XX`).
    pub fn conditional_set(mut self, existence_check: ExistenceCheck) -> Self {
        self.conditional_set = Some(existence_check);
        self
    }

    /// Set the value comparison (`IFEQ`).
    // TODO #7237: Document `IFNE` once it is supported.
    pub fn value_comparison(mut self, value_comparison: ValueComparison) -> Self {
        self.value_comparison = Some(value_comparison);
        self
    }

    /// Return the key's old value (`GET`).
    pub fn get(mut self, get: bool) -> Self {
        self.get = get;
        self
    }

    /// Set the expiry.
    pub fn with_expiration(mut self, expiration: SetExpiry) -> Self {
        self.expiration = Some(expiration);
        self
    }
}

impl ToValkeyArgs for SetOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(ref existence_check) = self.conditional_set {
            existence_check.write_valkey_args(out);
        }
        if let Some(ref value_comparison) = self.value_comparison {
            value_comparison.write_valkey_args(out);
        }
        if self.get {
            out.write_arg(b"GET");
        }
        if let Some(ref expiration) = self.expiration {
            expiration.write_valkey_args(out);
        }
    }
}

/// The `LEFT`/`RIGHT` argument used by list commands (`LMOVE`, `LMPOP`, ...).
///
/// Mirrors redis-rs's `Direction` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Direction {
    /// The head of the list (`LEFT`).
    Left,
    /// The tail of the list (`RIGHT`).
    Right,
}

impl ToValkeyArgs for Direction {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self {
            Direction::Left => b"LEFT".as_slice(),
            Direction::Right => b"RIGHT".as_slice(),
        });
    }
}

/// Expiry argument for `GETEX`/`HGETEX`.
///
/// Mirrors redis-rs's `Expiry` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Expiry {
    /// Set expiry, in seconds (`EX`).
    EX(u64),
    /// Set expiry, in milliseconds (`PX`).
    PX(u64),
    /// Set expiry at a Unix time, in seconds (`EXAT`).
    EXAT(u64),
    /// Set expiry at a Unix time, in milliseconds (`PXAT`).
    PXAT(u64),
    /// Remove the time to live (`PERSIST`).
    PERSIST,
}

impl ToValkeyArgs for Expiry {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        let mut kw = |k: &[u8], v: u64| {
            out.write_arg(k);
            out.write_arg_fmt(v);
        };
        match self {
            Expiry::EX(sec) => kw(b"EX", *sec),
            Expiry::PX(ms) => kw(b"PX", *ms),
            Expiry::EXAT(ts) => kw(b"EXAT", *ts),
            Expiry::PXAT(ts) => kw(b"PXAT", *ts),
            Expiry::PERSIST => out.write_arg(b"PERSIST"),
        }
    }
}

/// Options for the `LPOS` command.
///
/// Mirrors redis-rs's `LposOptions` type.
#[derive(Debug, Clone, Copy, Default)]
pub struct LposOptions {
    count: Option<usize>,
    maxlen: Option<usize>,
    rank: Option<isize>,
}

impl LposOptions {
    /// Limit the results to the first `n` matches (`COUNT`).
    pub fn count(mut self, n: usize) -> Self {
        self.count = Some(n);
        self
    }

    /// Return the `n`-th match (`RANK`).
    pub fn rank(mut self, n: isize) -> Self {
        self.rank = Some(n);
        self
    }

    /// Limit the search to the first `n` list entries (`MAXLEN`).
    pub fn maxlen(mut self, n: usize) -> Self {
        self.maxlen = Some(n);
        self
    }
}

impl ToValkeyArgs for LposOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(n) = self.count {
            out.write_arg(b"COUNT");
            out.write_arg_fmt(n);
        }
        if let Some(n) = self.rank {
            out.write_arg(b"RANK");
            out.write_arg_fmt(n);
        }
        if let Some(n) = self.maxlen {
            out.write_arg(b"MAXLEN");
            out.write_arg_fmt(n);
        }
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

/// Options for the `MIGRATE` command.
///
/// Mirrors Python `MigrateOptions`.
#[derive(Clone, Default)]
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

impl std::fmt::Debug for MigrateOptions {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("MigrateOptions")
            .field("copy", &self.copy)
            .field("replace", &self.replace)
            .field("password", &self.password.as_ref().map(|_| "<redacted>"))
            .field("username", &self.username)
            .finish()
    }
}

impl ToValkeyArgs for MigrateOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if self.copy {
            out.write_arg(b"COPY");
        }
        if self.replace {
            out.write_arg(b"REPLACE");
        }
        match (&self.username, &self.password) {
            (Some(username), Some(password)) => {
                out.write_arg(b"AUTH2");
                out.write_arg(username.as_bytes());
                out.write_arg(password.as_bytes());
            }
            (None, Some(password)) => {
                out.write_arg(b"AUTH");
                out.write_arg(password.as_bytes());
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

impl ToValkeyArgs for RestoreOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if self.replace {
            out.write_arg(b"REPLACE");
        }
        if self.absttl {
            out.write_arg(b"ABSTTL");
        }
        if let Some(idletime) = self.idletime {
            out.write_arg(b"IDLETIME");
            out.write_arg_fmt(idletime);
        }
        if let Some(frequency) = self.frequency {
            out.write_arg(b"FREQ");
            out.write_arg_fmt(frequency);
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
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[non_exhaustive]
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::assert_args;
    use crate::test_utils::assert_args_empty;

    #[test]
    fn existence_check_args() {
        assert_args(ExistenceCheck::NX, &["NX"]);
        assert_args(ExistenceCheck::XX, &["XX"]);
    }

    #[test]
    fn set_expiry_args() {
        assert_args(SetExpiry::EX(60), &["EX", "60"]);
        assert_args(SetExpiry::PX(1500), &["PX", "1500"]);
        assert_args(SetExpiry::EXAT(100), &["EXAT", "100"]);
        assert_args(SetExpiry::PXAT(200), &["PXAT", "200"]);
        assert_args(SetExpiry::KEEPTTL, &["KEEPTTL"]);
        assert_args(SetExpiry::PXAT(u64::MAX), &["PXAT", "18446744073709551615"]);
    }

    #[test]
    fn direction_args() {
        assert_args(Direction::Left, &["LEFT"]);
        assert_args(Direction::Right, &["RIGHT"]);
    }

    #[test]
    fn expiry_args() {
        assert_args(Expiry::EX(60), &["EX", "60"]);
        assert_args(Expiry::PX(1500), &["PX", "1500"]);
        assert_args(Expiry::EXAT(100), &["EXAT", "100"]);
        assert_args(Expiry::PXAT(200), &["PXAT", "200"]);
        assert_args(Expiry::PERSIST, &["PERSIST"]);
        assert_args(Expiry::EXAT(u64::MAX), &["EXAT", "18446744073709551615"]);
    }

    #[test]
    fn lpos_options_args() {
        assert_args_empty(LposOptions::default());
        assert_args(
            LposOptions::default().count(2).rank(-1).maxlen(100),
            &["COUNT", "2", "RANK", "-1", "MAXLEN", "100"],
        );
    }

    #[test]
    fn set_options_args() {
        assert_args_empty(SetOptions::default());
        assert_args(
            SetOptions::default()
                .conditional_set(ExistenceCheck::NX)
                .get(true)
                .with_expiration(SetExpiry::EX(60)),
            &["NX", "GET", "EX", "60"],
        );
        assert_args(
            SetOptions::default()
                .conditional_set(ExistenceCheck::XX)
                .value_comparison(ValueComparison::ifeq("old"))
                .get(true)
                .with_expiration(SetExpiry::PX(1500)),
            &["XX", "IFEQ", "old", "GET", "PX", "1500"],
        );
    }

    #[test]
    fn value_comparison_args() {
        assert_args(ValueComparison::ifeq("v"), &["IFEQ", "v"]);
        assert_args(
            ValueComparison::IFEQ([0u8].to_vec()),
            &[b"IFEQ".as_slice(), &[0u8]],
        );

        // TODO #7237: Add `IFNE` tests.
    }

    #[test]
    fn expire_option_args() {
        assert_args_empty(ExpireOption::NONE);
        assert_args(ExpireOption::NX, &["NX"]);
        assert_args(ExpireOption::XX, &["XX"]);
        assert_args(ExpireOption::GT, &["GT"]);
        assert_args(ExpireOption::LT, &["LT"]);
    }

    #[test]
    fn hash_field_expiration_options_args() {
        assert_args_empty(HashFieldExpirationOptions::default());
        assert_args(
            HashFieldExpirationOptions::default()
                .set_existence_check(FieldExistenceCheck::FNX)
                .set_expiration(SetExpiry::PX(1500)),
            &["FNX", "PX", "1500"],
        );
        assert_args(
            HashFieldExpirationOptions::default().set_existence_check(FieldExistenceCheck::FXX),
            &["FXX"],
        );
    }

    #[test]
    fn copy_options_args() {
        assert_args_empty(CopyOptions::default());
        assert_args(CopyOptions::default().replace(true), &["REPLACE"]);
        assert_args(
            CopyOptions::default().db(2).replace(true),
            &["DB", "2", "REPLACE"],
        );
    }

    #[test]
    fn flush_all_options_args() {
        assert_args(FlushAllOptions::default(), &["ASYNC"]);
        assert_args(FlushDbOptions::default().blocking(true), &["SYNC"]);
        assert_args(FunctionFlushOptions::default().blocking(true), &["SYNC"]);
    }

    #[test]
    fn order_by_args() {
        assert_eq!(OrderBy::Asc.as_arg(), "ASC");
        assert_eq!(OrderBy::Desc.as_arg(), "DESC");
    }

    #[test]
    fn restore_options_args() {
        assert_args_empty(RestoreOptions::default());
        assert_args(
            RestoreOptions {
                replace: true,
                absttl: true,
                idletime: Some(100),
                frequency: Some(5),
            },
            &["REPLACE", "ABSTTL", "IDLETIME", "100", "FREQ", "5"],
        );
    }

    #[test]
    fn migrate_options_args() {
        assert_args_empty(MigrateOptions::default());
        assert_args(
            MigrateOptions {
                copy: true,
                replace: true,
                password: Some("pw".into()),
                username: None,
            },
            &["COPY", "REPLACE", "AUTH", "pw"],
        );
        assert_args(
            MigrateOptions {
                copy: false,
                replace: false,
                password: Some("pw".into()),
                username: Some("user".into()),
            },
            &["AUTH2", "user", "pw"],
        );
    }

    #[test]
    fn migrate_options_debug_redacts_password() {
        let opts = MigrateOptions {
            copy: true,
            replace: false,
            password: Some("super-secret".into()),
            username: Some("alice".into()),
        };

        let redacted_str = format!("{opts:?}");
        assert!(!redacted_str.contains("super-secret"));
        assert!(redacted_str.contains("<redacted>"));

        let unredacted_str = format!("{:?}", MigrateOptions::default());
        assert!(!unredacted_str.contains("<redacted>"));
    }

    #[test]
    fn client_pause_mode_args() {
        assert_eq!(ClientPauseMode::All.as_arg(), "ALL");
        assert_eq!(ClientPauseMode::Write.as_arg(), "WRITE");
    }

    #[test]
    fn function_restore_policy_args() {
        assert_eq!(FunctionRestorePolicy::Append.as_arg(), "APPEND");
        assert_eq!(FunctionRestorePolicy::Flush.as_arg(), "FLUSH");
        assert_eq!(FunctionRestorePolicy::Replace.as_arg(), "REPLACE");
    }
}
