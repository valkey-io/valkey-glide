// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's command API: [`AsyncCommands`] and [`Commands`], and their typed
//! counterparts [`AsyncTypedCommands`] and [`TypedCommands`].
//!
//! One command table (below) defines all four traits via the
//! `implement_commands!` macro. Entries are source-compatible with
//! redis-rs's command table: same method names, generic parameter
//! order, bounds, and argument lists, so migrated call sites (including
//! turbofish annotations) compile unchanged.
//!
//! Each entry carries the command body (mirroring redis-rs's
//! `implement_commands!`), so the wire encoding is identical by construction;
//! signature parity is enforced by the `parity_tests` module (`src/parity_tests/`).
//!
//! The built command is handed to glide-core **by value** through
//! [`AsyncCommands::glide_send_command`] — the same zero-extra-copy path as the
//! rest of the client. Methods take `&self` (the clients are cheaply
//! cloneable handles); migrated `&mut` call sites still compile via
//! auto-borrow.
//!
//! Two deliberate deviations trade redis-rs parity for performance and
//! clarity (parity here is a command-surface contract, not a
//! connection-plumbing one):
//! * the traits are **not** coupled to the `redis` crate's connection-object
//!   (`ConnectionLike`) machinery — dispatch is GLIDE's owned-send path only;
//! * the `scan*` methods take `&self` and return GLIDE's own iterators
//!   ([`crate::commands::scan`]) with the familiar `next_item()` /
//!   `Iterator` shape, each page dispatched by value (no per-page copies).
//!
//! Commands beyond this table (the remaining stream commands, geo search,
//! `JSON.*`, …) live in the per-family extension traits in [`crate::commands`].
//!
//! Maintenance: add or adjust entries in the `implement_commands!`
//! invocation at the bottom of this file; the parity test will flag any
//! divergence from redis-rs's command table (see DEVELOPER.md).

use crate::ValkeyFuture;
use crate::cmd::Cmd;
use crate::commands::geo::GeoCoord;
use crate::commands::geo::GeoUnit;
use crate::commands::options::CopyOptions;
use crate::commands::options::Direction;
use crate::commands::options::ExpireOption;
use crate::commands::options::Expiry;
use crate::commands::options::FlushAllOptions;
use crate::commands::options::FlushDbOptions;
use crate::commands::options::HashFieldExpirationOptions;
use crate::commands::options::LposOptions;
use crate::commands::options::SetOptions;
use crate::commands::stream::StreamAddOptions;
use crate::commands::stream::StreamClaimReply;
use crate::commands::stream::StreamInfoConsumersReply;
use crate::commands::stream::StreamInfoGroupsReply;
use crate::commands::stream::StreamInfoStreamReply;
use crate::commands::stream::StreamPendingReply;
use crate::commands::stream::StreamRangeReply;
use crate::commands::stream::StreamReadReply;
use crate::pipeline::Pipeline;
use crate::types::IntegerReplyOrNoOp;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToSingleValkeyArg;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyNumericBehavior;
use std::collections::HashSet;

// Only exposed by sync commands.
#[cfg(feature = "sync")]
use crate::ValkeyResult;

/// Expands to the scan iterators, shared by every command trait:
/// - `$iter` builds the iterator from the client and the command's prefix and suffix
///   arguments (e.g. `SyncScanIter::new`).
/// - `$ret` is the iterator's return type (e.g. `ValkeyResult<SyncScanIter<'s, Self, RV>>`),
///   which may name each method's `'s` lifetime and `RV` result type.
///
/// These scan iterators mirror redis-rs's (same names and arguments, and the same generics
/// apart from the `'s` lifetime) but deliberately deviate: `&self` receivers returning
/// GLIDE's own iterator types (same `next_item()` call shape), with every page dispatched
/// by value on the owned-send path.
///
/// Follows the structure of redis-rs's `implement_iterators!`.
macro_rules! implement_iterators {
    ($iter:expr, $ret:ty) => {
        /// Cursor-driven `SCAN` over the whole keyspace.
        // TODO #6872: Use `GlideClusterClient::cluster_scan` for cluster iteration.
        #[inline]
        fn scan<'s, RV: FromValkeyValue + 's>(&'s self) -> $ret {
            ($iter)(self, vec![b"SCAN".to_vec()], Vec::new())
        }

        /// Cursor-driven `SCAN` over the keyspace, filtered by a `MATCH` pattern.
        // TODO #6872: Use `GlideClusterClient::cluster_scan` for cluster iteration.
        #[inline]
        fn scan_match<'s, P: ToSingleValkeyArg, RV: FromValkeyValue + 's>(
            &'s self,
            pattern: P,
        ) -> $ret {
            let mut suffix = vec![b"MATCH".to_vec()];
            pattern.write_valkey_args(&mut suffix);
            ($iter)(self, vec![b"SCAN".to_vec()], suffix)
        }

        /// Cursor-driven `HSCAN` over a hash's fields and values.
        #[inline]
        fn hscan<'s, K: ToSingleValkeyArg, RV: FromValkeyValue + 's>(&'s self, key: K) -> $ret {
            let mut prefix = vec![b"HSCAN".to_vec()];
            key.write_valkey_args(&mut prefix);
            ($iter)(self, prefix, Vec::new())
        }

        /// Cursor-driven `HSCAN`, filtered by a field-name `MATCH` pattern.
        #[inline]
        fn hscan_match<'s, K: ToSingleValkeyArg, P: ToSingleValkeyArg, RV: FromValkeyValue + 's>(
            &'s self,
            key: K,
            pattern: P,
        ) -> $ret {
            let mut prefix = vec![b"HSCAN".to_vec()];
            key.write_valkey_args(&mut prefix);
            let mut suffix = vec![b"MATCH".to_vec()];
            pattern.write_valkey_args(&mut suffix);
            ($iter)(self, prefix, suffix)
        }

        /// Cursor-driven `SSCAN` over a set's members.
        #[inline]
        fn sscan<'s, K: ToSingleValkeyArg, RV: FromValkeyValue + 's>(&'s self, key: K) -> $ret {
            let mut prefix = vec![b"SSCAN".to_vec()];
            key.write_valkey_args(&mut prefix);
            ($iter)(self, prefix, Vec::new())
        }

        /// Cursor-driven `SSCAN`, filtered by a `MATCH` pattern.
        #[inline]
        fn sscan_match<'s, K: ToSingleValkeyArg, P: ToSingleValkeyArg, RV: FromValkeyValue + 's>(
            &'s self,
            key: K,
            pattern: P,
        ) -> $ret {
            let mut prefix = vec![b"SSCAN".to_vec()];
            key.write_valkey_args(&mut prefix);
            let mut suffix = vec![b"MATCH".to_vec()];
            pattern.write_valkey_args(&mut suffix);
            ($iter)(self, prefix, suffix)
        }

        /// Cursor-driven `ZSCAN` over a sorted set's members and scores.
        #[inline]
        fn zscan<'s, K: ToSingleValkeyArg, RV: FromValkeyValue + 's>(&'s self, key: K) -> $ret {
            let mut prefix = vec![b"ZSCAN".to_vec()];
            key.write_valkey_args(&mut prefix);
            ($iter)(self, prefix, Vec::new())
        }

        /// Cursor-driven `ZSCAN`, filtered by a `MATCH` pattern.
        #[inline]
        fn zscan_match<'s, K: ToSingleValkeyArg, P: ToSingleValkeyArg, RV: FromValkeyValue + 's>(
            &'s self,
            key: K,
            pattern: P,
        ) -> $ret {
            let mut prefix = vec![b"ZSCAN".to_vec()];
            key.write_valkey_args(&mut prefix);
            let mut suffix = vec![b"MATCH".to_vec()];
            pattern.write_valkey_args(&mut suffix);
            ($iter)(self, prefix, suffix)
        }
    };
}

/// Expands one command table entry into an [`AsyncTypedCommands`] method.
///
/// Follows the structure of redis-rs's `implement_command_async!`.
macro_rules! implement_typed_command_async {
    (
        $lifetime:lifetime
        $(#[$attr:meta])*
        fn $name:ident <$($g:ident: $b:ident),*> ($($arg:ident: $ty:ty),*) Generic
    ) => {
        $(#[$attr])*
        #[inline]
        #[allow(deprecated)]
        #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
        fn $name<$lifetime, RV: FromValkeyValue, $($g: $b + Send + Sync + $lifetime,)*>(
            &$lifetime self $(, $arg: $ty)*
        ) -> ValkeyFuture<$lifetime, RV> {
            let cmd = Cmd::$name($($arg),*);
            Box::pin(async move { RV::from_owned_valkey_value(self.glide_send_command(cmd).await?) })
        }
    };
    (
        $lifetime:lifetime
        $(#[$attr:meta])*
        fn $name:ident <$($g:ident: $b:ident),*> ($($arg:ident: $ty:ty),*) $ret:ty
    ) => {
        $(#[$attr])*
        #[inline]
        #[allow(deprecated)]
        // Table return types are parenthesized, as in redis-rs (e.g. `-> (usize)`).
        #[allow(unused_parens)]
        #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
        fn $name<$lifetime, $($g: $b + Send + Sync + $lifetime,)*>(
            &$lifetime self $(, $arg: $ty)*
        ) -> ValkeyFuture<$lifetime, $ret> {
            let cmd = Cmd::$name($($arg),*);
            Box::pin(async move {
                <$ret as FromValkeyValue>::from_owned_valkey_value(self.glide_send_command(cmd).await?)
            })
        }
    };
}

/// Expands one command table entry into a [`TypedCommands`] method.
///
/// Blocking counterpart of `implement_typed_command_async!`.
#[cfg(feature = "sync")]
macro_rules! implement_typed_command_sync {
    (
        $lifetime:lifetime
        $(#[$attr:meta])*
        fn $name:ident <$($g:ident: $b:ident),*> ($($arg:ident: $ty:ty),*) Generic
    ) => {
        $(#[$attr])*
        #[inline]
        #[allow(deprecated)]
        #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
        fn $name<$lifetime, RV: FromValkeyValue, $($g: $b,)*>(
            &self $(, $arg: $ty)*
        ) -> ValkeyResult<RV> {
            RV::from_owned_valkey_value(self.glide_send_command(Cmd::$name($($arg),*))?)
        }
    };
    (
        $lifetime:lifetime
        $(#[$attr:meta])*
        fn $name:ident <$($g:ident: $b:ident),*> ($($arg:ident: $ty:ty),*) $ret:ty
    ) => {
        $(#[$attr])*
        #[inline]
        #[allow(deprecated)]
        // Table return types are parenthesized, as in redis-rs (e.g. `-> (usize)`).
        #[allow(unused_parens)]
        #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
        fn $name<$lifetime, $($g: $b,)*>(
            &self $(, $arg: $ty)*
        ) -> ValkeyResult<$ret> {
            <$ret as FromValkeyValue>::from_owned_valkey_value(
                self.glide_send_command(Cmd::$name($($arg),*))?,
            )
        }
    };
}

/// Defines the [`AsyncCommands`], [`Commands`], [`AsyncTypedCommands`], and
/// [`TypedCommands`] traits from one command table.
///
/// Each `fn name<G: Bound>(args) -> (T) { body }` entry expands to an async
/// method, its blocking counterpart, and the typed async and blocking
/// methods returning `T`.
macro_rules! implement_commands {
    (
        $lifetime:lifetime;
        $(
            $(#[$attr:meta])*
            fn $name:ident <$($g:ident: $b:ident),*> ($($arg:ident: $ty:ty),*) -> $ret:tt $body:block
        )*
    ) => {
        /// Command methods, one per table entry.
        ///
        /// For example, the `pttl` table entry expands to:
        ///
        /// ```ignore
        /// impl Cmd {
        ///     pub(crate) fn pttl<K: ToSingleValkeyArg>(key: K) -> Cmd {
        ///         build_cmd!("PTTL", key)
        ///     }
        /// }
        /// ```
        impl Cmd {
            $(
                $(#[$attr])*
                #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
                pub(crate) fn $name<$lifetime, $($g: $b),*>($($arg: $ty),*) -> Self $body
            )*
        }

        /// Pipeline builder methods, one per table entry.
        ///
        /// For example, the `pttl` table entry expands to:
        ///
        /// ```ignore
        /// impl Pipeline {
        ///     pub fn pttl<K: ToSingleValkeyArg>(&mut self, key: K) -> &mut Self {
        ///         self.add_command(Cmd::pttl(key));
        ///         self
        ///     }
        /// }
        /// ```
        impl Pipeline {
            $(
                $(#[$attr])*
                #[inline]
                #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
                pub fn $name<$lifetime, $($g: $b),*>(&mut self $(, $arg: $ty)*) -> &mut Self {
                    self.add_command(Cmd::$name($($arg),*));
                    self
                }
            )*
        }

        /// **GLIDE's async command API.**
        ///
        /// Implemented by [`crate::GlideClient`] and
        /// [`crate::GlideClusterClient`] — see the [module docs](self).
        ///
        /// Deliberately **not** tied to the `redis` crate's connection-object
        /// traits: every method dispatches through [`Self::glide_send_command`],
        /// GLIDE's zero-extra-copy path.
        pub trait AsyncCommands: Send + Sync + Sized {
            /// Send an already-built command **by value** (no clone). This is
            /// the single required method; every typed command delegates to
            /// it. Also useful directly as a zero-extra-copy escape hatch for
            /// custom commands with large payloads.
            ///
            /// Prefer the typed commands (e.g. [`get`](Self::get)).
            /// Use this method only for commands GLIDE does not implement.
            fn glide_send_command<'a>(&'a self, cmd: Cmd) -> ValkeyFuture<'a, ValkeyValue>;

            /// Typed escape hatch: send an already-built [`Cmd`] by value and
            /// decode the reply into `RV`. An alternative to
            /// `cmd(...).query_async(con)` ([`Cmd::query_async`] delegates here)
            /// — same decode, no connection-object machinery, no payload copy.
            ///
            /// Prefer the typed commands (e.g. [`get`](Self::get)).
            /// Use this method only for commands GLIDE does not implement.
            #[inline]
            fn glide_send_command_as<'a, RV: FromValkeyValue>(&'a self, cmd: Cmd) -> ValkeyFuture<'a, RV> {
                Box::pin(async move { RV::from_owned_valkey_value(self.glide_send_command(cmd).await?) })
            }

            $(
                $(#[$attr])*
                #[inline]
                #[allow(deprecated)]
                #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
                fn $name<$lifetime, $($g: $b + Send + Sync + $lifetime,)* RV>(
                    &$lifetime self $(, $arg: $ty)*
                ) -> ValkeyFuture<$lifetime, RV>
                where
                    RV: FromValkeyValue,
                {
                    let cmd = Cmd::$name($($arg),*);
                    Box::pin(async move { RV::from_owned_valkey_value(self.glide_send_command(cmd).await?) })
                }
            )*

            implement_iterators!(
                |con, prefix, suffix| Box::pin(crate::commands::scan::ScanIter::new(con, prefix, suffix)),
                ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>>
            );
        }

        /// **GLIDE's blocking command API.**
        ///
        /// Blocking counterpart of [`AsyncCommands`] — see there and the
        /// [module docs](self). Implemented by
        /// [`crate::sync::SyncGlideClient`] and
        /// [`crate::sync::SyncGlideClusterClient`].
        ///
        /// These methods block on the internal runtime and **must not be
        /// called from within an async context** (doing so panics with
        /// tokio's "cannot block the current thread from within a runtime");
        /// use [`AsyncCommands`] on the async clients there instead.
        #[cfg(feature = "sync")]
        pub trait Commands: Sized {
            /// Send an already-built command **by value** (no clone). This is
            /// the single required method; every typed command delegates to it.
            ///
            /// Prefer the typed commands (e.g. [`get`](Self::get)).
            /// Use this method only for commands GLIDE does not implement.
            fn glide_send_command(&self, cmd: Cmd) -> ValkeyResult<ValkeyValue>;

            /// Typed escape hatch (blocking counterpart of the async
            /// `glide_send_command_as`): send an already-built [`Cmd`] by value and
            /// decode the reply into `RV`.
            ///
            /// Prefer the typed commands (e.g. [`get`](Self::get)).
            /// Use this method only for commands GLIDE does not implement.
            #[inline]
            fn glide_send_command_as<RV: FromValkeyValue>(&self, cmd: Cmd) -> ValkeyResult<RV> {
                RV::from_owned_valkey_value(self.glide_send_command(cmd)?)
            }

            $(
                $(#[$attr])*
                #[inline]
                #[allow(deprecated)]
                #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
                fn $name<$lifetime, $($g: $b,)* RV: FromValkeyValue>(
                    &self $(, $arg: $ty)*
                ) -> ValkeyResult<RV> {
                    RV::from_owned_valkey_value(self.glide_send_command(Cmd::$name($($arg),*))?)
                }
            )*

            implement_iterators!(
                crate::commands::scan::SyncScanIter::new,
                ValkeyResult<crate::commands::scan::SyncScanIter<'s, Self, RV>>
            );
        }

        /// **GLIDE's typed async command API.**
        ///
        /// Like [`AsyncCommands`], but each method returns a concrete type
        /// (e.g. [`get`](Self::get) returns `Option<String>`), so no type
        /// annotation is needed. To choose the return type, use
        /// [`AsyncCommands`] instead.
        ///
        /// Implemented for every [`AsyncCommands`] type. Import only one of the
        /// two traits: their methods share names, so calls are ambiguous when
        /// both are in scope (as with redis-rs's `AsyncCommands` and
        /// `AsyncTypedCommands`).
        pub trait AsyncTypedCommands: AsyncCommands {
            $(
                implement_typed_command_async! {
                    $lifetime
                    $(#[$attr])*
                    fn $name<$($g: $b),*>($($arg: $ty),*) $ret
                }
            )*

            implement_iterators!(
                |con, prefix, suffix| Box::pin(crate::commands::scan::ScanIter::new(con, prefix, suffix)),
                ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>>
            );
        }

        impl<T: AsyncCommands> AsyncTypedCommands for T {}

        /// **GLIDE's typed blocking command API.**
        ///
        /// Blocking counterpart of [`AsyncTypedCommands`]; implemented for
        /// every [`Commands`] type. Import only one of [`Commands`] and
        /// [`TypedCommands`] (see [`AsyncTypedCommands`]).
        #[cfg(feature = "sync")]
        pub trait TypedCommands: Commands {
            $(
                implement_typed_command_sync! {
                    $lifetime
                    $(#[$attr])*
                    fn $name<$($g: $b),*>($($arg: $ty),*) $ret
                }
            )*

            implement_iterators!(
                crate::commands::scan::SyncScanIter::new,
                ValkeyResult<crate::commands::scan::SyncScanIter<'s, Self, RV>>
            );
        }

        #[cfg(feature = "sync")]
        impl<T: Commands> TypedCommands for T {}
    };
}

/// Split `&[(key, weight)]` into separate key and weight slices.
fn unzip_weights<K, W>(items: &[(K, W)]) -> (Vec<&K>, Vec<&W>) {
    items.iter().map(|(key, weight)| (key, weight)).unzip()
}

/// Build an owned [`Cmd`]: `build_cmd!(NAME, arg1, arg2, …)`.
macro_rules! build_cmd {
    ($name:expr $(,)?) => {
        $crate::cmd::cmd($name)
    };
    ($name:expr $(, $arg:expr)* $(,)?) => {{
        let mut command = $crate::cmd::cmd($name);
        $( command.arg($arg); )*
        command
    }};
}

implement_commands! {
    'a;

    // ==== Strings =======================================================

    /// `GET`.
    fn get<K: ToSingleValkeyArg>(key: K) -> (Option<String>) {
        build_cmd!("GET", key)
    }

    /// `MGET`.
    fn mget<K: ToValkeyArgs>(key: K) -> (Vec<Option<String>>) {
        build_cmd!("MGET", key)
    }

    /// `SET`.
    fn set<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V) -> (()) {
        build_cmd!("SET", key, value)
    }

    /// `SET`.
    fn set_options<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V, options: SetOptions) -> (Option<String>) {
        build_cmd!("SET", key, value, options)
    }

    /// `MSET`.
    fn mset<K: ToValkeyArgs, V: ToValkeyArgs>(items: &'a [(K, V)]) -> (()) {
        build_cmd!("MSET", items)
    }

    /// `SETEX`.
    fn set_ex<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V, seconds: u64) -> (()) {
        build_cmd!("SETEX", key, seconds, value)
    }

    /// `PSETEX`.
    fn pset_ex<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V, milliseconds: u64) -> (()) {
        build_cmd!("PSETEX", key, milliseconds, value)
    }

    /// `SETNX`.
    fn set_nx<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V) -> (bool) {
        build_cmd!("SETNX", key, value)
    }

    /// `MSETNX`.
    fn mset_nx<K: ToValkeyArgs, V: ToValkeyArgs>(items: &'a [(K, V)]) -> (bool) {
        build_cmd!("MSETNX", items)
    }

    /// `GETSET`.
    fn getset<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V) -> (Option<String>) {
        build_cmd!("GETSET", key, value)
    }

    /// `GETRANGE`.
    fn getrange<K: ToSingleValkeyArg>(key: K, from: isize, to: isize) -> (String) {
        build_cmd!("GETRANGE", key, from, to)
    }

    /// `SETRANGE`.
    fn setrange<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, offset: isize, value: V) -> (usize) {
        build_cmd!("SETRANGE", key, offset, value)
    }

    /// `GETEX`.
    fn get_ex<K: ToSingleValkeyArg>(key: K, expire_at: Expiry) -> (Option<String>) {
        build_cmd!("GETEX", key, expire_at)
    }

    /// `GETDEL`.
    fn get_del<K: ToSingleValkeyArg>(key: K) -> (Option<String>) {
        build_cmd!("GETDEL", key)
    }

    /// `APPEND`.
    fn append<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V) -> (usize) {
        build_cmd!("APPEND", key, value)
    }

    /// `INCRBY`/`INCRBYFLOAT`
    // TODO #7262: redis-rs's typed `isize` return cannot represent the
    // `INCRBYFLOAT` (float `delta`) reply.
    fn incr<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, delta: V) -> (isize) {
        build_cmd!(if delta.describe_numeric_behavior() == ValkeyNumericBehavior::NumberIsFloat {
            "INCRBYFLOAT"
        } else {
            "INCRBY"
        }, key, delta)
    }

    /// `DECRBY`.
    fn decr<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, delta: V) -> (isize) {
        build_cmd!("DECRBY", key, delta)
    }

    /// `STRLEN`.
    fn strlen<K: ToSingleValkeyArg>(key: K) -> (usize) {
        build_cmd!("STRLEN", key)
    }

    // ==== Keys & expiry =================================================

    /// `KEYS`.
    fn keys<K: ToSingleValkeyArg>(key: K) -> (Vec<String>) {
        build_cmd!("KEYS", key)
    }

    /// `DEL`.
    fn del<K: ToValkeyArgs>(key: K) -> (usize) {
        build_cmd!("DEL", key)
    }

    /// `EXISTS`.
    fn exists<K: ToValkeyArgs>(key: K) -> (bool) {
        build_cmd!("EXISTS", key)
    }

    /// `TYPE`.
    fn key_type<K: ToSingleValkeyArg>(key: K) -> (crate::types::ValueType) {
        build_cmd!("TYPE", key)
    }

    /// `EXPIRE`.
    fn expire<K: ToSingleValkeyArg>(key: K, seconds: i64) -> (bool) {
        build_cmd!("EXPIRE", key, seconds)
    }

    /// `EXPIREAT`.
    fn expire_at<K: ToSingleValkeyArg>(key: K, ts: i64) -> (bool) {
        build_cmd!("EXPIREAT", key, ts)
    }

    /// `PEXPIRE`.
    fn pexpire<K: ToSingleValkeyArg>(key: K, ms: i64) -> (bool) {
        build_cmd!("PEXPIRE", key, ms)
    }

    /// `PEXPIREAT`.
    fn pexpire_at<K: ToSingleValkeyArg>(key: K, ts: i64) -> (bool) {
        build_cmd!("PEXPIREAT", key, ts)
    }

    /// `PERSIST`.
    fn persist<K: ToSingleValkeyArg>(key: K) -> (bool) {
        build_cmd!("PERSIST", key)
    }

    /// `TTL`.
    fn ttl<K: ToSingleValkeyArg>(key: K) -> (IntegerReplyOrNoOp) {
        build_cmd!("TTL", key)
    }

    /// `PTTL`.
    fn pttl<K: ToSingleValkeyArg>(key: K) -> (IntegerReplyOrNoOp) {
        build_cmd!("PTTL", key)
    }

    /// `RENAME`.
    fn rename<K: ToSingleValkeyArg, N: ToSingleValkeyArg>(key: K, new_key: N) -> (()) {
        build_cmd!("RENAME", key, new_key)
    }

    /// `RENAMENX`.
    fn rename_nx<K: ToSingleValkeyArg, N: ToSingleValkeyArg>(key: K, new_key: N) -> (bool) {
        build_cmd!("RENAMENX", key, new_key)
    }

    /// `UNLINK`.
    fn unlink<K: ToValkeyArgs>(key: K) -> (usize) {
        build_cmd!("UNLINK", key)
    }

    /// `OBJECT ENCODING`.
    fn object_encoding<K: ToSingleValkeyArg>(key: K) -> (Option<String>) {
        build_cmd!("OBJECT", "ENCODING", key)
    }

    /// `OBJECT IDLETIME`.
    fn object_idletime<K: ToSingleValkeyArg>(key: K) -> (Option<usize>) {
        build_cmd!("OBJECT", "IDLETIME", key)
    }

    /// `OBJECT FREQ`.
    fn object_freq<K: ToSingleValkeyArg>(key: K) -> (Option<usize>) {
        build_cmd!("OBJECT", "FREQ", key)
    }

    /// `OBJECT REFCOUNT`.
    fn object_refcount<K: ToSingleValkeyArg>(key: K) -> (Option<usize>) {
        build_cmd!("OBJECT", "REFCOUNT", key)
    }

    /// `COPY`.
    fn copy<KSrc: ToSingleValkeyArg, KDst: ToSingleValkeyArg, Db: ToString>(source: KSrc, destination: KDst, options: CopyOptions<Db>) -> (bool) {
        build_cmd!("COPY", source, destination, options)
    }

    // ==== Lists =========================================================

    /// `BLMOVE`.
    fn blmove<S: ToSingleValkeyArg, D: ToSingleValkeyArg>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction, timeout: f64) -> (Option<String>) {
        build_cmd!("BLMOVE", srckey, dstkey, src_dir, dst_dir, timeout)
    }

    /// `BLMPOP`.
    fn blmpop<K: ToValkeyArgs>(timeout: f64, numkeys: usize, key: K, dir: Direction, count: usize) -> (Option<(String, Vec<String>)>) {
        build_cmd!("BLMPOP", timeout, numkeys, key, dir, "COUNT", count)
    }

    /// `BLPOP`.
    fn blpop<K: ToValkeyArgs>(key: K, timeout: f64) -> (Option<[String; 2]>) {
        build_cmd!("BLPOP", key, timeout)
    }

    /// `BRPOP`.
    fn brpop<K: ToValkeyArgs>(key: K, timeout: f64) -> (Option<[String; 2]>) {
        build_cmd!("BRPOP", key, timeout)
    }

    /// `BRPOPLPUSH`.
    fn brpoplpush<S: ToSingleValkeyArg, D: ToSingleValkeyArg>(srckey: S, dstkey: D, timeout: f64) -> (Option<String>) {
        build_cmd!("BRPOPLPUSH", srckey, dstkey, timeout)
    }

    /// `LINDEX`.
    fn lindex<K: ToSingleValkeyArg>(key: K, index: isize) -> (Option<String>) {
        build_cmd!("LINDEX", key, index)
    }

    /// `LINSERT`.
    fn linsert_before<K: ToSingleValkeyArg, P: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, pivot: P, value: V) -> (isize) {
        build_cmd!("LINSERT", key, "BEFORE", pivot, value)
    }

    /// `LINSERT`.
    fn linsert_after<K: ToSingleValkeyArg, P: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, pivot: P, value: V) -> (isize) {
        build_cmd!("LINSERT", key, "AFTER", pivot, value)
    }

    /// `LLEN`.
    fn llen<K: ToSingleValkeyArg>(key: K) -> (usize) {
        build_cmd!("LLEN", key)
    }

    /// `LMOVE`.
    fn lmove<S: ToSingleValkeyArg, D: ToSingleValkeyArg>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction) -> (String) {
        build_cmd!("LMOVE", srckey, dstkey, src_dir, dst_dir)
    }

    /// `LMPOP`.
    fn lmpop<K: ToValkeyArgs>(numkeys: usize, key: K, dir: Direction, count: usize) -> (Option<(String, Vec<String>)>) {
        build_cmd!("LMPOP", numkeys, key, dir, "COUNT", count)
    }

    /// `LPOP`.
    fn lpop<K: ToSingleValkeyArg>(key: K, count: Option<core::num::NonZeroUsize>) -> Generic {
        build_cmd!("LPOP", key, count)
    }

    /// `LPOS`.
    fn lpos<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, value: V, options: LposOptions) -> Generic {
        build_cmd!("LPOS", key, value, options)
    }

    /// `LPUSH`.
    fn lpush<K: ToSingleValkeyArg, V: ToValkeyArgs>(key: K, value: V) -> (usize) {
        build_cmd!("LPUSH", key, value)
    }

    /// `LPUSHX`.
    fn lpush_exists<K: ToSingleValkeyArg, V: ToValkeyArgs>(key: K, value: V) -> (usize) {
        build_cmd!("LPUSHX", key, value)
    }

    /// `LRANGE`.
    fn lrange<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (Vec<String>) {
        build_cmd!("LRANGE", key, start, stop)
    }

    /// `LREM`.
    fn lrem<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, count: isize, value: V) -> (usize) {
        build_cmd!("LREM", key, count, value)
    }

    /// `LTRIM`.
    fn ltrim<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (()) {
        build_cmd!("LTRIM", key, start, stop)
    }

    /// `LSET`.
    fn lset<K: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, index: isize, value: V) -> (()) {
        build_cmd!("LSET", key, index, value)
    }

    /// `RPOP`.
    fn rpop<K: ToSingleValkeyArg>(key: K, count: Option<core::num::NonZeroUsize>) -> Generic {
        build_cmd!("RPOP", key, count)
    }

    /// `RPOPLPUSH`.
    fn rpoplpush<K: ToSingleValkeyArg, D: ToSingleValkeyArg>(key: K, dstkey: D) -> (Option<String>) {
        build_cmd!("RPOPLPUSH", key, dstkey)
    }

    /// `RPUSH`.
    fn rpush<K: ToSingleValkeyArg, V: ToValkeyArgs>(key: K, value: V) -> (usize) {
        build_cmd!("RPUSH", key, value)
    }

    /// `RPUSHX`.
    fn rpush_exists<K: ToSingleValkeyArg, V: ToValkeyArgs>(key: K, value: V) -> (usize) {
        build_cmd!("RPUSHX", key, value)
    }

    // ==== Hashes ========================================================

    /// `HGET`.
    fn hget<K: ToSingleValkeyArg, F: ToSingleValkeyArg>(key: K, field: F) -> (Option<String>) {
        build_cmd!("HGET", key, field)
    }

    /// `HMGET`.
    fn hmget<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F) -> (Vec<String>) {
        build_cmd!("HMGET", key, fields)
    }

    /// `HDEL`.
    fn hdel<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, field: F) -> (usize) {
        build_cmd!("HDEL", key, field)
    }

    /// `HSET`.
    fn hset<K: ToSingleValkeyArg, F: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, field: F, value: V) -> (usize) {
        build_cmd!("HSET", key, field, value)
    }

    /// `HSET`.
    fn hset_multiple<K: ToSingleValkeyArg, F: ToValkeyArgs, V: ToValkeyArgs>(key: K, items: &'a [(F, V)]) -> (usize) {
        build_cmd!("HSET", key, items)
    }

    /// `HSETNX`.
    fn hset_nx<K: ToSingleValkeyArg, F: ToSingleValkeyArg, V: ToSingleValkeyArg>(key: K, field: F, value: V) -> (bool) {
        build_cmd!("HSETNX", key, field, value)
    }

    /// `HINCRBY`/`HINCRBYFLOAT`.
    fn hincr<K: ToSingleValkeyArg, F: ToSingleValkeyArg, D: ToSingleValkeyArg>(key: K, field: F, delta: D) -> (f64) {
        build_cmd!(if delta.describe_numeric_behavior() == ValkeyNumericBehavior::NumberIsFloat {
            "HINCRBYFLOAT"
        } else {
            "HINCRBY"
        }, key, field, delta)
    }

    /// `HEXISTS`.
    fn hexists<K: ToSingleValkeyArg, F: ToSingleValkeyArg>(key: K, field: F) -> (bool) {
        build_cmd!("HEXISTS", key, field)
    }

    /// `HKEYS`.
    fn hkeys<K: ToSingleValkeyArg>(key: K) -> (Vec<String>) {
        build_cmd!("HKEYS", key)
    }

    /// `HVALS`.
    fn hvals<K: ToSingleValkeyArg>(key: K) -> (Vec<String>) {
        build_cmd!("HVALS", key)
    }

    /// `HGETALL`.
    fn hgetall<K: ToSingleValkeyArg>(key: K) -> (std::collections::HashMap<String, String>) {
        build_cmd!("HGETALL", key)
    }

    /// `HLEN`.
    fn hlen<K: ToSingleValkeyArg>(key: K) -> (usize) {
        build_cmd!("HLEN", key)
    }

    /// `HGETEX`.
    fn hget_ex<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F, expire_at: Expiry) -> (Vec<String>) {
        build_cmd!("HGETEX", key, expire_at, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HSETEX`.
    fn hset_ex<K: ToSingleValkeyArg, F: ToValkeyArgs, V: ToValkeyArgs>(key: K, hash_field_expiration_options: &'a HashFieldExpirationOptions, fields_values: &'a [(F, V)]) -> (bool) {
        build_cmd!("HSETEX", key, hash_field_expiration_options, "FIELDS", fields_values.len(), fields_values)
    }

    /// `HTTL`.
    fn httl<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HTTL", key, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HPTTL`.
    fn hpttl<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HPTTL", key, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HEXPIRE`.
    fn hexpire<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, seconds: i64, opt: ExpireOption, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HEXPIRE", key, seconds, opt, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HEXPIREAT`.
    fn hexpire_at<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, ts: i64, opt: ExpireOption, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HEXPIREAT", key, ts, opt, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HEXPIRETIME`.
    fn hexpire_time<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HEXPIRETIME", key, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HPERSIST`.
    fn hpersist<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HPERSIST", key, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HPEXPIRE`.
    fn hpexpire<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, milliseconds: i64, opt: ExpireOption, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HPEXPIRE", key, milliseconds, opt, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HPEXPIREAT`.
    fn hpexpire_at<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, ts: i64, opt: ExpireOption, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HPEXPIREAT", key, ts, opt, "FIELDS", fields.num_of_args(), fields)
    }

    /// `HPEXPIRETIME`.
    fn hpexpire_time<K: ToSingleValkeyArg, F: ToValkeyArgs>(key: K, fields: F) -> (Vec<IntegerReplyOrNoOp>) {
        build_cmd!("HPEXPIRETIME", key, "FIELDS", fields.num_of_args(), fields)
    }

    // ==== Sets ==========================================================

    /// `SADD`.
    fn sadd<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, member: M) -> (usize) {
        build_cmd!("SADD", key, member)
    }

    /// `SCARD`.
    fn scard<K: ToSingleValkeyArg>(key: K) -> (usize) {
        build_cmd!("SCARD", key)
    }

    /// `SDIFF`.
    fn sdiff<K: ToValkeyArgs>(keys: K) -> (HashSet<String>) {
        build_cmd!("SDIFF", keys)
    }

    /// `SDIFFSTORE`.
    fn sdiffstore<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("SDIFFSTORE", dstkey, keys)
    }

    /// `SINTER`.
    fn sinter<K: ToValkeyArgs>(keys: K) -> (HashSet<String>) {
        build_cmd!("SINTER", keys)
    }

    /// `SINTERSTORE`.
    fn sinterstore<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("SINTERSTORE", dstkey, keys)
    }

    /// `SISMEMBER`.
    fn sismember<K: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, member: M) -> (bool) {
        build_cmd!("SISMEMBER", key, member)
    }

    /// `SMISMEMBER`.
    fn smismember<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, members: M) -> (Vec<bool>) {
        build_cmd!("SMISMEMBER", key, members)
    }

    /// `SMEMBERS`.
    fn smembers<K: ToSingleValkeyArg>(key: K) -> (HashSet<String>) {
        build_cmd!("SMEMBERS", key)
    }

    /// `SMOVE`.
    fn smove<S: ToSingleValkeyArg, D: ToSingleValkeyArg, M: ToSingleValkeyArg>(srckey: S, dstkey: D, member: M) -> (bool) {
        build_cmd!("SMOVE", srckey, dstkey, member)
    }

    /// `SPOP`.
    fn spop<K: ToSingleValkeyArg>(key: K) -> Generic {
        build_cmd!("SPOP", key)
    }

    /// `SRANDMEMBER`.
    fn srandmember<K: ToSingleValkeyArg>(key: K) -> (Option<String>) {
        build_cmd!("SRANDMEMBER", key)
    }

    /// `SRANDMEMBER`.
    fn srandmember_multiple<K: ToSingleValkeyArg>(key: K, count: isize) -> (Vec<String>) {
        build_cmd!("SRANDMEMBER", key, count)
    }

    /// `SREM`.
    fn srem<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, member: M) -> (usize) {
        build_cmd!("SREM", key, member)
    }

    /// `SUNION`.
    fn sunion<K: ToValkeyArgs>(keys: K) -> (HashSet<String>) {
        build_cmd!("SUNION", keys)
    }

    /// `SUNIONSTORE`.
    fn sunionstore<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("SUNIONSTORE", dstkey, keys)
    }

    // ==== Sorted sets ===================================================

    /// `ZADD`.
    fn zadd<K: ToSingleValkeyArg, S: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, member: M, score: S) -> usize {
        build_cmd!("ZADD", key, score, member)
    }

    /// `ZADD`.
    fn zadd_multiple<K: ToSingleValkeyArg, S: ToValkeyArgs, M: ToValkeyArgs>(key: K, items: &'a [(S, M)]) -> (usize) {
        build_cmd!("ZADD", key, items)
    }

    /// `ZCARD`.
    fn zcard<K: ToSingleValkeyArg>(key: K) -> (usize) {
        build_cmd!("ZCARD", key)
    }

    /// `ZCOUNT`.
    fn zcount<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (usize) {
        build_cmd!("ZCOUNT", key, min, max)
    }

    /// `ZINCRBY`.
    fn zincr<K: ToSingleValkeyArg, M: ToSingleValkeyArg, D: ToSingleValkeyArg>(key: K, member: M, delta: D) -> (f64) {
        build_cmd!("ZINCRBY", key, delta, member)
    }

    /// `ZINTERSTORE`.
    fn zinterstore<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("ZINTERSTORE", dstkey, keys.num_of_args(), keys)
    }

    /// `ZINTERSTORE`.
    fn zinterstore_min<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("ZINTERSTORE", dstkey, keys.num_of_args(), keys, "AGGREGATE", "MIN")
    }

    /// `ZINTERSTORE`.
    fn zinterstore_max<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("ZINTERSTORE", dstkey, keys.num_of_args(), keys, "AGGREGATE", "MAX")
    }

    /// `ZINTERSTORE`.
    fn zinterstore_weights<D: ToSingleValkeyArg, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) -> (usize) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "WEIGHTS", weights)
    }

    /// `ZINTERSTORE`.
    fn zinterstore_min_weights<D: ToSingleValkeyArg, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) -> (usize) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MIN", "WEIGHTS", weights)
    }

    /// `ZINTERSTORE`.
    fn zinterstore_max_weights<D: ToSingleValkeyArg, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) -> (usize) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MAX", "WEIGHTS", weights)
    }

    /// `ZLEXCOUNT`.
    fn zlexcount<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (usize) {
        build_cmd!("ZLEXCOUNT", key, min, max)
    }

    /// `BZPOPMAX`.
    fn bzpopmax<K: ToValkeyArgs>(key: K, timeout: f64) -> (Option<(String, String, f64)>) {
        build_cmd!("BZPOPMAX", key, timeout)
    }

    /// `ZPOPMAX`.
    fn zpopmax<K: ToSingleValkeyArg>(key: K, count: isize) -> (Vec<(String, f64)>) {
        build_cmd!("ZPOPMAX", key, count)
    }

    /// `BZPOPMIN`.
    fn bzpopmin<K: ToValkeyArgs>(key: K, timeout: f64) -> (Option<(String, String, f64)>) {
        build_cmd!("BZPOPMIN", key, timeout)
    }

    /// `ZPOPMIN`.
    fn zpopmin<K: ToSingleValkeyArg>(key: K, count: isize) -> (Vec<(String, f64)>) {
        build_cmd!("ZPOPMIN", key, count)
    }

    /// `BZMPOP`.
    fn bzmpop_max<K: ToValkeyArgs>(timeout: f64, keys: K, count: isize) -> (Option<(String, Vec<(String, f64)>)>) {
        build_cmd!("BZMPOP", timeout, keys.num_of_args(), keys, "MAX", "COUNT", count)
    }

    /// `ZMPOP`.
    fn zmpop_max<K: ToValkeyArgs>(keys: K, count: isize) -> (Option<(String, Vec<(String, f64)>)>) {
        build_cmd!("ZMPOP", keys.num_of_args(), keys, "MAX", "COUNT", count)
    }

    /// `BZMPOP`.
    fn bzmpop_min<K: ToValkeyArgs>(timeout: f64, keys: K, count: isize) -> (Option<(String, Vec<(String, f64)>)>) {
        build_cmd!("BZMPOP", timeout, keys.num_of_args(), keys, "MIN", "COUNT", count)
    }

    /// `ZMPOP`.
    fn zmpop_min<K: ToValkeyArgs>(keys: K, count: isize) -> (Option<(String, Vec<(String, f64)>)>) {
        build_cmd!("ZMPOP", keys.num_of_args(), keys, "MIN", "COUNT", count)
    }

    /// `ZRANDMEMBER`.
    fn zrandmember<K: ToSingleValkeyArg>(key: K, count: Option<isize>) -> Generic {
        build_cmd!("ZRANDMEMBER", key, count)
    }

    /// `ZRANDMEMBER WITHSCORES`.
    fn zrandmember_withscores<K: ToSingleValkeyArg>(key: K, count: isize) -> Generic {
        build_cmd!("ZRANDMEMBER", key, count, "WITHSCORES")
    }

    /// `ZRANGE`.
    fn zrange<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (Vec<String>) {
        build_cmd!("ZRANGE", key, start, stop)
    }

    /// `ZRANGE WITHSCORES`.
    fn zrange_withscores<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (Vec<(String, f64)>) {
        build_cmd!("ZRANGE", key, start, stop, "WITHSCORES")
    }

    /// `ZRANGEBYLEX`.
    fn zrangebylex<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (Vec<String>) {
        build_cmd!("ZRANGEBYLEX", key, min, max)
    }

    /// `ZRANGEBYLEX LIMIT`.
    fn zrangebylex_limit<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM, offset: isize, count: isize) -> (Vec<String>) {
        build_cmd!("ZRANGEBYLEX", key, min, max, "LIMIT", offset, count)
    }

    /// `ZREVRANGEBYLEX`.
    fn zrevrangebylex<K: ToSingleValkeyArg, MM: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, max: MM, min: M) -> (Vec<String>) {
        build_cmd!("ZREVRANGEBYLEX", key, max, min)
    }

    /// `ZREVRANGEBYLEX LIMIT`.
    fn zrevrangebylex_limit<K: ToSingleValkeyArg, MM: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, max: MM, min: M, offset: isize, count: isize) -> (Vec<String>) {
        build_cmd!("ZREVRANGEBYLEX", key, max, min, "LIMIT", offset, count)
    }

    /// `ZRANGEBYSCORE`.
    fn zrangebyscore<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (Vec<String>) {
        build_cmd!("ZRANGEBYSCORE", key, min, max)
    }

    /// `ZRANGEBYSCORE WITHSCORES`.
    fn zrangebyscore_withscores<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (Vec<(String, usize)>) {
        build_cmd!("ZRANGEBYSCORE", key, min, max, "WITHSCORES")
    }

    /// `ZRANGEBYSCORE LIMIT`.
    fn zrangebyscore_limit<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM, offset: isize, count: isize) -> (Vec<String>) {
        build_cmd!("ZRANGEBYSCORE", key, min, max, "LIMIT", offset, count)
    }

    /// `ZRANGEBYSCORE WITHSCORES LIMIT`.
    fn zrangebyscore_limit_withscores<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM, offset: isize, count: isize) -> (Vec<(String, usize)>) {
        build_cmd!("ZRANGEBYSCORE", key, min, max, "WITHSCORES", "LIMIT", offset, count)
    }

    /// `ZRANK`.
    fn zrank<K: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, member: M) -> (Option<usize>) {
        build_cmd!("ZRANK", key, member)
    }

    /// `ZREM`.
    fn zrem<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, members: M) -> (usize) {
        build_cmd!("ZREM", key, members)
    }

    /// `ZREMRANGEBYLEX`.
    fn zrembylex<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (usize) {
        build_cmd!("ZREMRANGEBYLEX", key, min, max)
    }

    /// `ZREMRANGEBYRANK`.
    fn zremrangebyrank<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (usize) {
        build_cmd!("ZREMRANGEBYRANK", key, start, stop)
    }

    /// `ZREMRANGEBYSCORE`.
    fn zrembyscore<K: ToSingleValkeyArg, M: ToSingleValkeyArg, MM: ToSingleValkeyArg>(key: K, min: M, max: MM) -> (usize) {
        build_cmd!("ZREMRANGEBYSCORE", key, min, max)
    }

    /// `ZREVRANGE`.
    fn zrevrange<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (Vec<String>) {
        build_cmd!("ZREVRANGE", key, start, stop)
    }

    /// `ZREVRANGE WITHSCORES`.
    fn zrevrange_withscores<K: ToSingleValkeyArg>(key: K, start: isize, stop: isize) -> (Vec<String>) {
        build_cmd!("ZREVRANGE", key, start, stop, "WITHSCORES")
    }

    /// `ZREVRANGEBYSCORE`.
    fn zrevrangebyscore<K: ToSingleValkeyArg, MM: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, max: MM, min: M) -> (Vec<String>) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min)
    }

    /// `ZREVRANGEBYSCORE WITHSCORES`.
    fn zrevrangebyscore_withscores<K: ToSingleValkeyArg, MM: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, max: MM, min: M) -> (Vec<String>) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min, "WITHSCORES")
    }

    /// `ZREVRANGEBYSCORE LIMIT`.
    fn zrevrangebyscore_limit<K: ToSingleValkeyArg, MM: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, max: MM, min: M, offset: isize, count: isize) -> (Vec<String>) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min, "LIMIT", offset, count)
    }

    /// `ZREVRANGEBYSCORE WITHSCORES LIMIT`.
    fn zrevrangebyscore_limit_withscores<K: ToSingleValkeyArg, MM: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, max: MM, min: M, offset: isize, count: isize) -> (Vec<String>) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min, "WITHSCORES", "LIMIT", offset, count)
    }

    /// `ZREVRANK`.
    fn zrevrank<K: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, member: M) -> (Option<usize>) {
        build_cmd!("ZREVRANK", key, member)
    }

    /// `ZSCORE`.
    fn zscore<K: ToSingleValkeyArg, M: ToSingleValkeyArg>(key: K, member: M) -> (Option<f64>) {
        build_cmd!("ZSCORE", key, member)
    }

    /// `ZMSCORE`.
    fn zscore_multiple<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, members: &'a [M]) -> (Option<Vec<f64>>) {
        build_cmd!("ZMSCORE", key, members)
    }

    /// `ZUNIONSTORE`.
    fn zunionstore<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("ZUNIONSTORE", dstkey, keys.num_of_args(), keys)
    }

    /// `ZUNIONSTORE AGGREGATE MIN`.
    fn zunionstore_min<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("ZUNIONSTORE", dstkey, keys.num_of_args(), keys, "AGGREGATE", "MIN")
    }

    /// `ZUNIONSTORE AGGREGATE MAX`.
    fn zunionstore_max<D: ToSingleValkeyArg, K: ToValkeyArgs>(dstkey: D, keys: K) -> (usize) {
        build_cmd!("ZUNIONSTORE", dstkey, keys.num_of_args(), keys, "AGGREGATE", "MAX")
    }

    /// `ZUNIONSTORE WEIGHTS`.
    fn zunionstore_weights<D: ToSingleValkeyArg, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) -> (usize) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "WEIGHTS", weights)
    }

    /// `ZUNIONSTORE AGGREGATE MIN WEIGHTS`.
    fn zunionstore_min_weights<D: ToSingleValkeyArg, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) -> (usize) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MIN", "WEIGHTS", weights)
    }

    /// `ZUNIONSTORE AGGREGATE MAX WEIGHTS`.
    fn zunionstore_max_weights<D: ToSingleValkeyArg, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) -> (usize) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MAX", "WEIGHTS", weights)
    }

    // ==== HyperLogLog ===================================================

    /// `PFADD`.
    fn pfadd<K: ToSingleValkeyArg, E: ToValkeyArgs>(key: K, element: E) -> (bool) {
        build_cmd!("PFADD", key, element)
    }

    /// `PFCOUNT`.
    fn pfcount<K: ToValkeyArgs>(key: K) -> (usize) {
        build_cmd!("PFCOUNT", key)
    }

    /// `PFMERGE`.
    fn pfmerge<D: ToSingleValkeyArg, S: ToValkeyArgs>(dstkey: D, srckeys: S) -> (()) {
        build_cmd!("PFMERGE", dstkey, srckeys)
    }

    // ==== Bitmaps =======================================================

    /// `SETBIT`.
    fn setbit<K: ToSingleValkeyArg>(key: K, offset: usize, value: bool) -> (bool) {
        build_cmd!("SETBIT", key, offset, i32::from(value))
    }

    /// `GETBIT`.
    fn getbit<K: ToSingleValkeyArg>(key: K, offset: usize) -> (bool) {
        build_cmd!("GETBIT", key, offset)
    }

    /// `BITCOUNT`.
    fn bitcount<K: ToSingleValkeyArg>(key: K) -> (usize) {
        build_cmd!("BITCOUNT", key)
    }

    /// `BITCOUNT`.
    fn bitcount_range<K: ToSingleValkeyArg>(key: K, start: usize, end: usize) -> (usize) {
        build_cmd!("BITCOUNT", key, start, end)
    }

    /// `BITOP AND`.
    fn bit_and<D: ToSingleValkeyArg, S: ToValkeyArgs>(dstkey: D, srckeys: S) -> (usize) {
        build_cmd!("BITOP", "AND", dstkey, srckeys)
    }

    /// `BITOP OR`.
    fn bit_or<D: ToSingleValkeyArg, S: ToValkeyArgs>(dstkey: D, srckeys: S) -> (usize) {
        build_cmd!("BITOP", "OR", dstkey, srckeys)
    }

    /// `BITOP XOR`.
    fn bit_xor<D: ToSingleValkeyArg, S: ToValkeyArgs>(dstkey: D, srckeys: S) -> (usize) {
        build_cmd!("BITOP", "XOR", dstkey, srckeys)
    }

    /// `BITOP NOT`.
    fn bit_not<D: ToSingleValkeyArg, S: ToSingleValkeyArg>(dstkey: D, srckey: S) -> (usize) {
        build_cmd!("BITOP", "NOT", dstkey, srckey)
    }

    // ==== Geospatial ====================================================

    /// `GEOADD`.
    fn geo_add<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, members: M) -> (usize) {
        build_cmd!("GEOADD", key, members)
    }

    /// `GEODIST`.
    fn geo_dist<K: ToSingleValkeyArg, M1: ToSingleValkeyArg, M2: ToSingleValkeyArg>(key: K, member1: M1, member2: M2, unit: GeoUnit) -> (Option<f64>) {
        build_cmd!("GEODIST", key, member1, member2, unit)
    }

    /// `GEOHASH`.
    fn geo_hash<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, members: M) -> (Vec<String>) {
        build_cmd!("GEOHASH", key, members)
    }

    /// `GEOPOS`.
    fn geo_pos<K: ToSingleValkeyArg, M: ToValkeyArgs>(key: K, members: M) -> (Vec<Option<GeoCoord<f64>>>) {
        build_cmd!("GEOPOS", key, members)
    }

    // ==== Streams =======================================================

    /// `XACK`.
    fn xack<K: ToValkeyArgs, G: ToValkeyArgs, I: ToValkeyArgs>(key: K, group: G, ids: &'a [I]) -> (usize) {
        build_cmd!("XACK", key, group, ids)
    }

    /// `XADD`.
    fn xadd<K: ToValkeyArgs, ID: ToValkeyArgs, F: ToValkeyArgs, V: ToValkeyArgs>(key: K, id: ID, items: &'a [(F, V)]) -> (Option<String>) {
        build_cmd!("XADD", key, id, items)
    }

    /// `XADD` with options.
    fn xadd_options<K: ToValkeyArgs, ID: ToValkeyArgs, I: ToValkeyArgs>(key: K, id: ID, items: I, options: &'a StreamAddOptions) -> (Option<String>) {
        build_cmd!("XADD", key, options, id, items)
    }

    /// `XCLAIM`.
    fn xclaim<K: ToSingleValkeyArg, G: ToValkeyArgs, C: ToValkeyArgs, MIT: ToValkeyArgs, ID: ToValkeyArgs>(key: K, group: G, consumer: C, min_idle_time: MIT, ids: &'a [ID]) -> (StreamClaimReply) {
        build_cmd!("XCLAIM", key, group, consumer, min_idle_time, ids)
    }

    /// `XDEL`.
    fn xdel<K: ToSingleValkeyArg, ID: ToValkeyArgs>(key: K, ids: &'a [ID]) -> (usize) {
        build_cmd!("XDEL", key, ids)
    }

    /// `XGROUP CREATE`.
    fn xgroup_create<K: ToValkeyArgs, G: ToValkeyArgs, ID: ToValkeyArgs>(key: K, group: G, id: ID) -> () {
        build_cmd!("XGROUP", "CREATE", key, group, id)
    }

    /// `XGROUP DESTROY`.
    fn xgroup_destroy<K: ToValkeyArgs, G: ToValkeyArgs>(key: K, group: G) -> bool {
        build_cmd!("XGROUP", "DESTROY", key, group)
    }

    /// `XINFO CONSUMERS`.
    fn xinfo_consumers<K: ToValkeyArgs, G: ToValkeyArgs>(key: K, group: G) -> (StreamInfoConsumersReply) {
        build_cmd!("XINFO", "CONSUMERS", key, group)
    }

    /// `XINFO GROUPS`.
    fn xinfo_groups<K: ToValkeyArgs>(key: K) -> (StreamInfoGroupsReply) {
        build_cmd!("XINFO", "GROUPS", key)
    }

    /// `XINFO STREAM`.
    fn xinfo_stream<K: ToValkeyArgs>(key: K) -> (StreamInfoStreamReply) {
        build_cmd!("XINFO", "STREAM", key)
    }

    /// `XLEN`.
    fn xlen<K: ToValkeyArgs>(key: K) -> usize {
        build_cmd!("XLEN", key)
    }

    /// `XPENDING`.
    fn xpending<K: ToValkeyArgs, G: ToValkeyArgs>(key: K, group: G) -> (StreamPendingReply) {
        build_cmd!("XPENDING", key, group)
    }

    /// `XRANGE`.
    fn xrange<K: ToValkeyArgs, S: ToValkeyArgs, E: ToValkeyArgs>(key: K, start: S, end: E) -> (StreamRangeReply) {
        build_cmd!("XRANGE", key, start, end)
    }

    /// `XREAD`.
    fn xread<K: ToValkeyArgs, ID: ToValkeyArgs>(keys: &'a [K], ids: &'a [ID]) -> (Option<StreamReadReply>) {
        build_cmd!("XREAD", "STREAMS", keys, ids)
    }

    /// `XREVRANGE`.
    fn xrevrange<K: ToValkeyArgs, E: ToValkeyArgs, S: ToValkeyArgs>(key: K, end: E, start: S) -> (StreamRangeReply) {
        build_cmd!("XREVRANGE", key, end, start)
    }

    // ==== Connection ====================================================

    /// `PING`.
    fn ping<>() -> (String) {
        build_cmd!("PING")
    }

    /// `PING` with a message.
    fn ping_message<K: ToSingleValkeyArg>(message: K) -> (String) {
        build_cmd!("PING", message)
    }

    /// `CLIENT GETNAME`.
    fn client_getname<>() -> (Option<String>) {
        build_cmd!("CLIENT", "GETNAME")
    }

    /// `CLIENT ID`.
    fn client_id<>() -> (isize) {
        build_cmd!("CLIENT", "ID")
    }

    /// `CLIENT SETNAME`.
    fn client_setname<K: ToSingleValkeyArg>(connection_name: K) -> (()) {
        build_cmd!("CLIENT", "SETNAME", connection_name)
    }

    // ==== Server ========================================================

    /// `FLUSHALL`.
    fn flushall<>() -> () {
        build_cmd!("FLUSHALL")
    }

    /// `FLUSHALL`.
    fn flushall_options<>(options: &'a FlushAllOptions) -> () {
        build_cmd!("FLUSHALL", options)
    }

    /// `FLUSHDB`.
    fn flushdb<>() -> () {
        build_cmd!("FLUSHDB")
    }

    /// `FLUSHDB`.
    fn flushdb_options<>(options: &'a FlushDbOptions) -> () {
        build_cmd!("FLUSHDB", options)
    }

    // ==== Pub/Sub =======================================================

    /// `PUBLISH`.
    fn publish<K: ToSingleValkeyArg, E: ToSingleValkeyArg>(channel: K, message: E) -> (usize) {
        build_cmd!("PUBLISH", channel, message)
    }

    /// `SPUBLISH`.
    fn spublish<K: ToSingleValkeyArg, E: ToSingleValkeyArg>(channel: K, message: E) -> (usize) {
        build_cmd!("SPUBLISH", channel, message)
    }
}
