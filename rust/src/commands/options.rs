// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Shared option types used by multiple command families.
//!
//! Mirrors the Python `glide_shared.commands.core_options` and
//! `command_args` modules.

// TODO #6904: investigate whether the latest redis-rs version defines equivalents
// for these Python-mirrored option types, to mirror redis-rs instead. (`SetExpiry`
// already mirrors redis-rs and is exempt.)
use crate::cmd::Cmd;
use crate::value::ToValkeyArgs;

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

/// Expiry to apply when setting a value.
///
/// Mirrors redis-rs's `SetExpiry` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SetExpiry {
    /// Expire after the given number of seconds (`EX`).
    EX(usize),

    /// Expire after the given number of milliseconds (`PX`).
    PX(usize),

    /// Expire at the given Unix time in seconds (`EXAT`).
    EXAT(usize),

    /// Expire at the given Unix time in milliseconds (`PXAT`).
    PXAT(usize),

    /// Retain the key's existing TTL (`KEEPTTL`).
    KEEPTTL,
}

impl ToValkeyArgs for SetExpiry {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        let mut kw = |k: &[u8], v: usize| {
            out.push(k.to_vec());
            out.push(v.to_string().into_bytes());
        };
        match self {
            SetExpiry::EX(secs) => kw(b"EX", *secs),
            SetExpiry::PX(millis) => kw(b"PX", *millis),
            SetExpiry::EXAT(ts) => kw(b"EXAT", *ts),
            SetExpiry::PXAT(ts) => kw(b"PXAT", *ts),
            SetExpiry::KEEPTTL => out.push(b"KEEPTTL".to_vec()),
        }
    }

    fn is_single_arg(&self) -> bool {
        matches!(self, SetExpiry::KEEPTTL)
    }
}

/// Existence check for `SET` — whether the key must (not) already exist.
///
/// Mirrors redis-rs's `ExistenceCheck` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExistenceCheck {
    /// Only set the key if it does not already exist (`NX`).
    NX,
    /// Only set the key if it already exists (`XX`).
    XX,
}

/// Options for the `SET` command (`set_options`).
///
/// Mirrors redis-rs's `SetOptions` type.
#[derive(Clone, Copy, Default)]
pub struct SetOptions {
    conditional_set: Option<ExistenceCheck>,
    get: bool,
    expiration: Option<SetExpiry>,
}

impl SetOptions {
    /// Set the existence check (`NX`/`XX`).
    pub fn conditional_set(mut self, existence_check: ExistenceCheck) -> Self {
        self.conditional_set = Some(existence_check);
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
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        if let Some(ref existence_check) = self.conditional_set {
            match existence_check {
                ExistenceCheck::NX => out.push(b"NX".to_vec()),
                ExistenceCheck::XX => out.push(b"XX".to_vec()),
            }
        }
        if self.get {
            out.push(b"GET".to_vec());
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
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.push(
            match self {
                Direction::Left => b"LEFT".as_slice(),
                Direction::Right => b"RIGHT".as_slice(),
            }
            .to_vec(),
        );
    }
}

/// Expiry argument for `GETEX`/`HGETEX`.
///
/// Mirrors redis-rs's `Expiry` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Expiry {
    /// Set expiry, in seconds (`EX`).
    EX(usize),
    /// Set expiry, in milliseconds (`PX`).
    PX(usize),
    /// Set expiry at a Unix time, in seconds (`EXAT`).
    EXAT(usize),
    /// Set expiry at a Unix time, in milliseconds (`PXAT`).
    PXAT(usize),
    /// Remove the time to live (`PERSIST`).
    PERSIST,
}

impl ToValkeyArgs for Expiry {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        let mut kw = |k: &[u8], v: usize| {
            out.push(k.to_vec());
            out.push(v.to_string().into_bytes());
        };
        match self {
            Expiry::EX(sec) => kw(b"EX", *sec),
            Expiry::PX(ms) => kw(b"PX", *ms),
            Expiry::EXAT(ts) => kw(b"EXAT", *ts),
            Expiry::PXAT(ts) => kw(b"PXAT", *ts),
            Expiry::PERSIST => out.push(b"PERSIST".to_vec()),
        }
    }

    fn is_single_arg(&self) -> bool {
        matches!(self, Expiry::PERSIST)
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
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        if let Some(n) = self.count {
            out.push(b"COUNT".to_vec());
            out.push(n.to_string().into_bytes());
        }
        if let Some(n) = self.rank {
            out.push(b"RANK".to_vec());
            out.push(n.to_string().into_bytes());
        }
        if let Some(n) = self.maxlen {
            out.push(b"MAXLEN".to_vec());
            out.push(n.to_string().into_bytes());
        }
    }

    fn is_single_arg(&self) -> bool {
        false
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
        cmd.as_redis()
            .args_iter()
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
    fn set_expiry_args() {
        let cases: [(SetExpiry, Vec<&str>); 5] = [
            (SetExpiry::EX(60), vec!["EX", "60"]),
            (SetExpiry::PX(1500), vec!["PX", "1500"]),
            (SetExpiry::EXAT(100), vec!["EXAT", "100"]),
            (SetExpiry::PXAT(200), vec!["PXAT", "200"]),
            (SetExpiry::KEEPTTL, vec!["KEEPTTL"]),
        ];
        for (opt, expected) in cases {
            let mut cmd = Cmd::new();
            cmd.arg(opt);
            assert_eq!(args_of(&cmd), expected);
        }
    }

    #[test]
    fn direction_args() {
        let mut cmd = Cmd::new();
        cmd.arg(Direction::Left).arg(Direction::Right);
        assert_eq!(args_of(&cmd), vec!["LEFT", "RIGHT"]);
    }

    #[test]
    fn expiry_args() {
        let cases: [(Expiry, Vec<&str>); 5] = [
            (Expiry::EX(60), vec!["EX", "60"]),
            (Expiry::PX(1500), vec!["PX", "1500"]),
            (Expiry::EXAT(100), vec!["EXAT", "100"]),
            (Expiry::PXAT(200), vec!["PXAT", "200"]),
            (Expiry::PERSIST, vec!["PERSIST"]),
        ];
        for (opt, expected) in cases {
            let mut cmd = Cmd::new();
            cmd.arg(opt);
            assert_eq!(args_of(&cmd), expected);
        }
        assert!(!Expiry::EX(1).is_single_arg());
        assert!(Expiry::PERSIST.is_single_arg());
    }

    #[test]
    fn lpos_options_args() {
        let mut cmd = Cmd::new();
        cmd.arg(LposOptions::default());
        assert!(args_of(&cmd).is_empty());

        let mut cmd = Cmd::new();
        cmd.arg(LposOptions::default().count(2).rank(-1).maxlen(100));
        assert_eq!(
            args_of(&cmd),
            vec!["COUNT", "2", "RANK", "-1", "MAXLEN", "100"]
        );
        assert!(!LposOptions::default().is_single_arg());
    }

    #[test]
    fn set_options_args() {
        let mut cmd = Cmd::new();
        cmd.arg(SetOptions::default());
        assert!(args_of(&cmd).is_empty());

        let mut cmd = Cmd::new();
        cmd.arg(
            SetOptions::default()
                .conditional_set(ExistenceCheck::NX)
                .get(true)
                .with_expiration(SetExpiry::EX(60)),
        );
        assert_eq!(args_of(&cmd), vec!["NX", "GET", "EX", "60"]);
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
