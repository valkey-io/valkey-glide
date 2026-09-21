// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's command API: [`AsyncCommands`] and [`Commands`].
//!
//! One command table (below) defines both traits via the
//! `implement_glide_commands!` macro. Entries are source-compatible with the
//! vendored redis-rs fork (v0.25.2, predating the upstream license change):
//! same method names, generic parameter order, and argument lists, so
//! migrated call sites (including turbofish annotations) compile unchanged.
//! Each entry carries the command body (mirroring the redis-rs's
//! `implement_commands!`), so the wire encoding is identical by construction;
//! signature parity is enforced by `tests/it_parity_guard.rs`.
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
//! Commands beyond this table (streams, geo, `FT.*`, `JSON.*`, …) live in
//! the per-family extension traits in [`crate::commands`].
//!
//! Maintenance: add or adjust entries in the `implement_glide_commands!`
//! invocation at the bottom of this file; the parity-guard test will flag any
//! divergence from the fork's table (see DEVELOPER.md).

use crate::ValkeyFuture;
use crate::cmd::Cmd;
use crate::commands::options::{Direction, Expiry, LposOptions, SetOptions};
use crate::pipeline::Pipeline;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyNumericBehavior;

// Only exposed by sync commands.
#[cfg(feature = "sync")]
use crate::ValkeyResult;

/// Defines the unified [`AsyncCommands`] and [`Commands`] traits from one
/// command table.
///
/// Each `fn name<G: Bound>(args) { body }` entry expands to an async method
/// (generic `RV: FromValkeyValue` return, `&self` receiver, owned-send
/// dispatch) and its blocking counterpart.
macro_rules! implement_glide_commands {
    (
        $lifetime:lifetime;
        $(
            $(#[$attr:meta])*
            fn $name:ident <$($g:ident: $b:ident),*> ($($arg:ident: $ty:ty),*) $body:block
        )*
    ) => {
        /// Command methods, one per table entry.
        ///
        /// For example, the `pttl` table entry expands to:
        ///
        /// ```ignore
        /// impl Cmd {
        ///     pub(crate) fn pttl<K: ToValkeyArgs>(key: K) -> Cmd {
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
        ///     pub fn pttl<K: ToValkeyArgs>(&mut self, key: K) -> &mut Self {
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

    // The scan iterators are a deliberate GLIDE deviation from redis-rs:
    // `&self` receivers returning GLIDE's own iterator type (same
    // `next_item()` call shape), with every page dispatched by value on the
    // owned-send path — no connection-object machinery, no per-page copies.

    /// Cursor-driven `SCAN` over the whole keyspace.
    // TODO #6872: Use `GlideClusterClient::cluster_scan` for cluster iteration.
    #[inline]
    fn scan<'s, RV: FromValkeyValue + Send + 's>(
        &'s self,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        Box::pin(crate::commands::scan::ScanIter::new(
            self,
            vec![b"SCAN".to_vec()],
            Vec::new(),
        ))
    }

    /// Cursor-driven `SCAN` over the keyspace, filtered by a `MATCH` pattern.
    // TODO #6872: Use `GlideClusterClient::cluster_scan` for cluster iteration.
    #[inline]
    fn scan_match<'s, P: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        pattern: P,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(
            self,
            vec![b"SCAN".to_vec()],
            suffix,
        ))
    }

    /// Cursor-driven `HSCAN` over a hash's fields and values.
    #[inline]
    fn hscan<'s, K: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        key: K,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, Vec::new()))
    }

    /// Cursor-driven `HSCAN`, filtered by a field-name `MATCH` pattern.
    #[inline]
    fn hscan_match<'s, K: ToValkeyArgs, P: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        key: K,
        pattern: P,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, suffix))
    }

    /// Cursor-driven `SSCAN` over a set's members.
    #[inline]
    fn sscan<'s, K: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        key: K,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, Vec::new()))
    }

    /// Cursor-driven `SSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn sscan_match<'s, K: ToValkeyArgs, P: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        key: K,
        pattern: P,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, suffix))
    }

    /// Cursor-driven `ZSCAN` over a sorted set's members and scores.
    #[inline]
    fn zscan<'s, K: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        key: K,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, Vec::new()))
    }

    /// Cursor-driven `ZSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn zscan_match<'s, K: ToValkeyArgs, P: ToValkeyArgs, RV: FromValkeyValue + Send + 's>(
        &'s self,
        key: K,
        pattern: P,
    ) -> ValkeyFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, suffix))
    }        }

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
            /// Use this method only for commands GLIDE does not implement..
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

    // See the async trait: GLIDE-owned iterators on the owned-send path.
    // `SyncScanIter` implements `Iterator`, so `for` loops work as before.

    /// Cursor-driven `SCAN` over the whole keyspace.
    // TODO #6872: Use `GlideClusterClient::cluster_scan` for cluster iteration.
    #[inline]
    fn scan<RV: FromValkeyValue>(
        &self,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        crate::commands::scan::SyncScanIter::new(self, vec![b"SCAN".to_vec()], Vec::new())
    }

    /// Cursor-driven `SCAN` over the keyspace, filtered by a `MATCH` pattern.
    // TODO #6872: Use `GlideClusterClient::cluster_scan` for cluster iteration.
    #[inline]
    fn scan_match<P: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        pattern: P,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, vec![b"SCAN".to_vec()], suffix)
    }

    /// Cursor-driven `HSCAN` over a hash's fields and values.
    #[inline]
    fn hscan<K: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        key: K,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        crate::commands::scan::SyncScanIter::new(self, prefix, Vec::new())
    }

    /// Cursor-driven `HSCAN`, filtered by a field-name `MATCH` pattern.
    #[inline]
    fn hscan_match<K: ToValkeyArgs, P: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        key: K,
        pattern: P,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, prefix, suffix)
    }

    /// Cursor-driven `SSCAN` over a set's members.
    #[inline]
    fn sscan<K: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        key: K,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        crate::commands::scan::SyncScanIter::new(self, prefix, Vec::new())
    }

    /// Cursor-driven `SSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn sscan_match<K: ToValkeyArgs, P: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        key: K,
        pattern: P,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, prefix, suffix)
    }

    /// Cursor-driven `ZSCAN` over a sorted set's members and scores.
    #[inline]
    fn zscan<K: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        key: K,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        crate::commands::scan::SyncScanIter::new(self, prefix, Vec::new())
    }

    /// Cursor-driven `ZSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn zscan_match<K: ToValkeyArgs, P: ToValkeyArgs, RV: FromValkeyValue>(
        &self,
        key: K,
        pattern: P,
    ) -> ValkeyResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_valkey_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_valkey_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, prefix, suffix)
    }        }
    };
}

/// Split `&[(key, weight)]` into separate key and weight slices.
fn unzip_weights<K, W>(items: &[(K, W)]) -> (Vec<&K>, Vec<&W>) {
    items.iter().map(|(key, weight)| (key, weight)).unzip()
}

/// Build an owned [`Cmd`]: `build_cmd!(NAME, arg1, arg2, …)`.
macro_rules! build_cmd {
    ($name:expr $(, $arg:expr)* $(,)?) => {{
        let mut command = $crate::cmd::cmd($name);
        $( command.arg($arg); )*
        command
    }};
}

implement_glide_commands! {
    'a;

    // ==== Strings =======================================================

    /// `GET`.
    fn get<K: ToValkeyArgs>(key: K) {
        build_cmd!(if key.is_single_arg() { "GET" } else { "MGET" }, key)
    }

    /// `MGET`.
    fn mget<K: ToValkeyArgs>(key: K) {
        build_cmd!("MGET", key)
    }

    /// `SET`.
    fn set<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("SET", key, value)
    }

    /// `SET`.
    fn set_options<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V, options: SetOptions) {
        build_cmd!("SET", key, value, options)
    }

    // TODO #7042: Remove deprecated.
    /// `MSET`.
    #[allow(deprecated)]
    #[deprecated(since = "0.2.0", note = "use mset() (same command)")]
    fn set_multiple<K: ToValkeyArgs, V: ToValkeyArgs>(items: &'a [(K, V)]) {
        build_cmd!("MSET", items)
    }

    /// `MSET`.
    fn mset<K: ToValkeyArgs, V: ToValkeyArgs>(items: &'a [(K, V)]) {
        build_cmd!("MSET", items)
    }

    /// `SETEX`.
    fn set_ex<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V, seconds: u64) {
        build_cmd!("SETEX", key, seconds, value)
    }

    /// `PSETEX`.
    fn pset_ex<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V, milliseconds: u64) {
        build_cmd!("PSETEX", key, milliseconds, value)
    }

    /// `SETNX`.
    fn set_nx<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("SETNX", key, value)
    }

    /// `MSETNX`.
    fn mset_nx<K: ToValkeyArgs, V: ToValkeyArgs>(items: &'a [(K, V)]) {
        build_cmd!("MSETNX", items)
    }

    /// `GETSET`.
    fn getset<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("GETSET", key, value)
    }

    /// `GETRANGE`.
    fn getrange<K: ToValkeyArgs>(key: K, from: isize, to: isize) {
        build_cmd!("GETRANGE", key, from, to)
    }

    /// `SETRANGE`.
    fn setrange<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, offset: isize, value: V) {
        build_cmd!("SETRANGE", key, offset, value)
    }

    /// `GETEX`.
    fn get_ex<K: ToValkeyArgs>(key: K, expire_at: Expiry) {
        build_cmd!("GETEX", key, expire_at)
    }

    /// `GETDEL`.
    fn get_del<K: ToValkeyArgs>(key: K) {
        build_cmd!("GETDEL", key)
    }

    /// `APPEND`.
    fn append<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("APPEND", key, value)
    }

    /// `INCRBY`/`INCRBYFLOAT`
    fn incr<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, delta: V) {
        build_cmd!(if delta.describe_numeric_behavior() == ValkeyNumericBehavior::NumberIsFloat {
            "INCRBYFLOAT"
        } else {
            "INCRBY"
        }, key, delta)
    }

    /// `DECRBY`.
    fn decr<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, delta: V) {
        build_cmd!("DECRBY", key, delta)
    }

    /// `STRLEN`.
    fn strlen<K: ToValkeyArgs>(key: K) {
        build_cmd!("STRLEN", key)
    }

    // ==== Keys & expiry =================================================

    /// `KEYS`.
    fn keys<K: ToValkeyArgs>(key: K) {
        build_cmd!("KEYS", key)
    }

    /// `DEL`.
    fn del<K: ToValkeyArgs>(key: K) {
        build_cmd!("DEL", key)
    }

    /// `EXISTS`.
    fn exists<K: ToValkeyArgs>(key: K) {
        build_cmd!("EXISTS", key)
    }

    /// `TYPE`.
    fn key_type<K: ToValkeyArgs>(key: K) {
        build_cmd!("TYPE", key)
    }

    /// `EXPIRE`.
    fn expire<K: ToValkeyArgs>(key: K, seconds: i64) {
        build_cmd!("EXPIRE", key, seconds)
    }

    /// `EXPIREAT`.
    fn expire_at<K: ToValkeyArgs>(key: K, ts: i64) {
        build_cmd!("EXPIREAT", key, ts)
    }

    /// `PEXPIRE`.
    fn pexpire<K: ToValkeyArgs>(key: K, ms: i64) {
        build_cmd!("PEXPIRE", key, ms)
    }

    /// `PEXPIREAT`.
    fn pexpire_at<K: ToValkeyArgs>(key: K, ts: i64) {
        build_cmd!("PEXPIREAT", key, ts)
    }

    /// `PERSIST`.
    fn persist<K: ToValkeyArgs>(key: K) {
        build_cmd!("PERSIST", key)
    }

    /// `TTL`.
    fn ttl<K: ToValkeyArgs>(key: K) {
        build_cmd!("TTL", key)
    }

    /// `PTTL`.
    fn pttl<K: ToValkeyArgs>(key: K) {
        build_cmd!("PTTL", key)
    }

    /// `RENAME`.
    fn rename<K: ToValkeyArgs, N: ToValkeyArgs>(key: K, new_key: N) {
        build_cmd!("RENAME", key, new_key)
    }

    /// `RENAMENX`.
    fn rename_nx<K: ToValkeyArgs, N: ToValkeyArgs>(key: K, new_key: N) {
        build_cmd!("RENAMENX", key, new_key)
    }

    /// `UNLINK`.
    fn unlink<K: ToValkeyArgs>(key: K) {
        build_cmd!("UNLINK", key)
    }

    /// `OBJECT ENCODING`.
    fn object_encoding<K: ToValkeyArgs>(key: K) {
        build_cmd!("OBJECT", "ENCODING", key)
    }

    /// `OBJECT IDLETIME`.
    fn object_idletime<K: ToValkeyArgs>(key: K) {
        build_cmd!("OBJECT", "IDLETIME", key)
    }

    /// `OBJECT FREQ`.
    fn object_freq<K: ToValkeyArgs>(key: K) {
        build_cmd!("OBJECT", "FREQ", key)
    }

    /// `OBJECT REFCOUNT`.
    fn object_refcount<K: ToValkeyArgs>(key: K) {
        build_cmd!("OBJECT", "REFCOUNT", key)
    }

    // ==== Lists =========================================================

    /// `BLMOVE`.
    fn blmove<S: ToValkeyArgs, D: ToValkeyArgs>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction, timeout: f64) {
        build_cmd!("BLMOVE", srckey, dstkey, src_dir, dst_dir, timeout)
    }

    /// `BLMPOP`.
    fn blmpop<K: ToValkeyArgs>(timeout: f64, numkeys: usize, key: K, dir: Direction, count: usize) {
        build_cmd!("BLMPOP", timeout, numkeys, key, dir, "COUNT", count)
    }

    /// `BLPOP`.
    fn blpop<K: ToValkeyArgs>(key: K, timeout: f64) {
        build_cmd!("BLPOP", key, timeout)
    }

    /// `BRPOP`.
    fn brpop<K: ToValkeyArgs>(key: K, timeout: f64) {
        build_cmd!("BRPOP", key, timeout)
    }

    /// `BRPOPLPUSH`.
    fn brpoplpush<S: ToValkeyArgs, D: ToValkeyArgs>(srckey: S, dstkey: D, timeout: f64) {
        build_cmd!("BRPOPLPUSH", srckey, dstkey, timeout)
    }

    /// `LINDEX`.
    fn lindex<K: ToValkeyArgs>(key: K, index: isize) {
        build_cmd!("LINDEX", key, index)
    }

    /// `LINSERT`.
    fn linsert_before<K: ToValkeyArgs, P: ToValkeyArgs, V: ToValkeyArgs>(key: K, pivot: P, value: V) {
        build_cmd!("LINSERT", key, "BEFORE", pivot, value)
    }

    /// `LINSERT`.
    fn linsert_after<K: ToValkeyArgs, P: ToValkeyArgs, V: ToValkeyArgs>(key: K, pivot: P, value: V) {
        build_cmd!("LINSERT", key, "AFTER", pivot, value)
    }

    /// `LLEN`.
    fn llen<K: ToValkeyArgs>(key: K) {
        build_cmd!("LLEN", key)
    }

    /// `LMOVE`.
    fn lmove<S: ToValkeyArgs, D: ToValkeyArgs>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction) {
        build_cmd!("LMOVE", srckey, dstkey, src_dir, dst_dir)
    }

    /// `LMPOP`.
    fn lmpop<K: ToValkeyArgs>(numkeys: usize, key: K, dir: Direction, count: usize) {
        build_cmd!("LMPOP", numkeys, key, dir, "COUNT", count)
    }

    /// `LPOP`.
    fn lpop<K: ToValkeyArgs>(key: K, count: Option<core::num::NonZeroUsize>) {
        build_cmd!("LPOP", key, count)
    }

    /// `LPOS`.
    fn lpos<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V, options: LposOptions) {
        build_cmd!("LPOS", key, value, options)
    }

    /// `LPUSH`.
    fn lpush<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("LPUSH", key, value)
    }

    /// `LPUSHX`.
    fn lpush_exists<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("LPUSHX", key, value)
    }

    /// `LRANGE`.
    fn lrange<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("LRANGE", key, start, stop)
    }

    /// `LREM`.
    fn lrem<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, count: isize, value: V) {
        build_cmd!("LREM", key, count, value)
    }

    /// `LTRIM`.
    fn ltrim<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("LTRIM", key, start, stop)
    }

    /// `LSET`.
    fn lset<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, index: isize, value: V) {
        build_cmd!("LSET", key, index, value)
    }

    /// `RPOP`.
    fn rpop<K: ToValkeyArgs>(key: K, count: Option<core::num::NonZeroUsize>) {
        build_cmd!("RPOP", key, count)
    }

    /// `RPOPLPUSH`.
    fn rpoplpush<K: ToValkeyArgs, D: ToValkeyArgs>(key: K, dstkey: D) {
        build_cmd!("RPOPLPUSH", key, dstkey)
    }

    /// `RPUSH`.
    fn rpush<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("RPUSH", key, value)
    }

    /// `RPUSHX`.
    fn rpush_exists<K: ToValkeyArgs, V: ToValkeyArgs>(key: K, value: V) {
        build_cmd!("RPUSHX", key, value)
    }

    // ==== Hashes ========================================================

    /// `HGET`/`HMGET`.
    fn hget<K: ToValkeyArgs, F: ToValkeyArgs>(key: K, field: F) {
        build_cmd!(if field.is_single_arg() { "HGET" } else { "HMGET" }, key, field)
    }

    /// `HDEL`.
    fn hdel<K: ToValkeyArgs, F: ToValkeyArgs>(key: K, field: F) {
        build_cmd!("HDEL", key, field)
    }

    /// `HSET`.
    fn hset<K: ToValkeyArgs, F: ToValkeyArgs, V: ToValkeyArgs>(key: K, field: F, value: V) {
        build_cmd!("HSET", key, field, value)
    }

    /// `HSETNX`.
    fn hset_nx<K: ToValkeyArgs, F: ToValkeyArgs, V: ToValkeyArgs>(key: K, field: F, value: V) {
        build_cmd!("HSETNX", key, field, value)
    }

    /// `HMSET`.
    fn hset_multiple<K: ToValkeyArgs, F: ToValkeyArgs, V: ToValkeyArgs>(key: K, items: &'a [(F, V)]) {
        build_cmd!("HMSET", key, items)
    }

    /// `HINCRBY`/`HINCRBYFLOAT`.
    fn hincr<K: ToValkeyArgs, F: ToValkeyArgs, D: ToValkeyArgs>(key: K, field: F, delta: D) {
        build_cmd!(if delta.describe_numeric_behavior() == ValkeyNumericBehavior::NumberIsFloat {
            "HINCRBYFLOAT"
        } else {
            "HINCRBY"
        }, key, field, delta)
    }

    /// `HEXISTS`.
    fn hexists<K: ToValkeyArgs, F: ToValkeyArgs>(key: K, field: F) {
        build_cmd!("HEXISTS", key, field)
    }

    /// `HKEYS`.
    fn hkeys<K: ToValkeyArgs>(key: K) {
        build_cmd!("HKEYS", key)
    }

    /// `HVALS`.
    fn hvals<K: ToValkeyArgs>(key: K) {
        build_cmd!("HVALS", key)
    }

    /// `HGETALL`.
    fn hgetall<K: ToValkeyArgs>(key: K) {
        build_cmd!("HGETALL", key)
    }

    /// `HLEN`.
    fn hlen<K: ToValkeyArgs>(key: K) {
        build_cmd!("HLEN", key)
    }

    // ==== Sets ==========================================================

    /// `SADD`.
    fn sadd<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M) {
        build_cmd!("SADD", key, member)
    }

    /// `SCARD`.
    fn scard<K: ToValkeyArgs>(key: K) {
        build_cmd!("SCARD", key)
    }

    /// `SDIFF`.
    fn sdiff<K: ToValkeyArgs>(keys: K) {
        build_cmd!("SDIFF", keys)
    }

    /// `SDIFFSTORE`.
    fn sdiffstore<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: K) {
        build_cmd!("SDIFFSTORE", dstkey, keys)
    }

    /// `SINTER`.
    fn sinter<K: ToValkeyArgs>(keys: K) {
        build_cmd!("SINTER", keys)
    }

    /// `SINTERSTORE`.
    fn sinterstore<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: K) {
        build_cmd!("SINTERSTORE", dstkey, keys)
    }

    /// `SISMEMBER`.
    fn sismember<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M) {
        build_cmd!("SISMEMBER", key, member)
    }

    /// `SMISMEMBER`.
    fn smismember<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, members: M) {
        build_cmd!("SMISMEMBER", key, members)
    }

    /// `SMEMBERS`.
    fn smembers<K: ToValkeyArgs>(key: K) {
        build_cmd!("SMEMBERS", key)
    }

    /// `SMOVE`.
    fn smove<S: ToValkeyArgs, D: ToValkeyArgs, M: ToValkeyArgs>(srckey: S, dstkey: D, member: M) {
        build_cmd!("SMOVE", srckey, dstkey, member)
    }

    /// `SPOP`.
    fn spop<K: ToValkeyArgs>(key: K) {
        build_cmd!("SPOP", key)
    }

    /// `SRANDMEMBER`.
    fn srandmember<K: ToValkeyArgs>(key: K) {
        build_cmd!("SRANDMEMBER", key)
    }

    /// `SRANDMEMBER`.
    fn srandmember_multiple<K: ToValkeyArgs>(key: K, count: usize) {
        build_cmd!("SRANDMEMBER", key, count)
    }

    /// `SREM`.
    fn srem<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M) {
        build_cmd!("SREM", key, member)
    }

    /// `SUNION`.
    fn sunion<K: ToValkeyArgs>(keys: K) {
        build_cmd!("SUNION", keys)
    }

    /// `SUNIONSTORE`.
    fn sunionstore<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: K) {
        build_cmd!("SUNIONSTORE", dstkey, keys)
    }

    // ==== Sorted sets ===================================================

    /// `ZADD`.
    fn zadd<K: ToValkeyArgs, S: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M, score: S) {
        build_cmd!("ZADD", key, score, member)
    }

    /// `ZADD`.
    fn zadd_multiple<K: ToValkeyArgs, S: ToValkeyArgs, M: ToValkeyArgs>(key: K, items: &'a [(S, M)]) {
        build_cmd!("ZADD", key, items)
    }

    /// `ZCARD`.
    fn zcard<K: ToValkeyArgs>(key: K) {
        build_cmd!("ZCARD", key)
    }

    /// `ZCOUNT`.
    fn zcount<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZCOUNT", key, min, max)
    }

    /// `ZINCRBY`.
    fn zincr<K: ToValkeyArgs, M: ToValkeyArgs, D: ToValkeyArgs>(key: K, member: M, delta: D) {
        build_cmd!("ZINCRBY", key, delta, member)
    }

    /// `ZINTERSTORE`.
    fn zinterstore<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: &'a [K]) {
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys)
    }

    /// `ZINTERSTORE`.
    fn zinterstore_min<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: &'a [K]) {
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MIN")
    }

    /// `ZINTERSTORE`.
    fn zinterstore_max<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: &'a [K]) {
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MAX")
    }

    /// `ZINTERSTORE`.
    fn zinterstore_weights<D: ToValkeyArgs, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "WEIGHTS", weights)
    }

    /// `ZINTERSTORE`.
    fn zinterstore_min_weights<D: ToValkeyArgs, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MIN", "WEIGHTS", weights)
    }

    /// `ZINTERSTORE`.
    fn zinterstore_max_weights<D: ToValkeyArgs, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZINTERSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MAX", "WEIGHTS", weights)
    }

    /// `ZLEXCOUNT`.
    fn zlexcount<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZLEXCOUNT", key, min, max)
    }

    /// `BZPOPMAX`.
    fn bzpopmax<K: ToValkeyArgs>(key: K, timeout: f64) {
        build_cmd!("BZPOPMAX", key, timeout)
    }

    /// `ZPOPMAX`.
    fn zpopmax<K: ToValkeyArgs>(key: K, count: isize) {
        build_cmd!("ZPOPMAX", key, count)
    }

    /// `BZPOPMIN`.
    fn bzpopmin<K: ToValkeyArgs>(key: K, timeout: f64) {
        build_cmd!("BZPOPMIN", key, timeout)
    }

    /// `ZPOPMIN`.
    fn zpopmin<K: ToValkeyArgs>(key: K, count: isize) {
        build_cmd!("ZPOPMIN", key, count)
    }

    /// `BZMPOP`.
    fn bzmpop_max<K: ToValkeyArgs>(timeout: f64, keys: &'a [K], count: isize) {
        build_cmd!("BZMPOP", timeout, keys.len(), keys, "MAX", "COUNT", count)
    }

    /// `ZMPOP`.
    fn zmpop_max<K: ToValkeyArgs>(keys: &'a [K], count: isize) {
        build_cmd!("ZMPOP", keys.len(), keys, "MAX", "COUNT", count)
    }

    /// `BZMPOP`.
    fn bzmpop_min<K: ToValkeyArgs>(timeout: f64, keys: &'a [K], count: isize) {
        build_cmd!("BZMPOP", timeout, keys.len(), keys, "MIN", "COUNT", count)
    }

    /// `ZMPOP`.
    fn zmpop_min<K: ToValkeyArgs>(keys: &'a [K], count: isize) {
        build_cmd!("ZMPOP", keys.len(), keys, "MIN", "COUNT", count)
    }

    /// `ZRANDMEMBER`.
    fn zrandmember<K: ToValkeyArgs>(key: K, count: Option<isize>) {
        build_cmd!("ZRANDMEMBER", key, count)
    }

    /// `ZRANDMEMBER WITHSCORES`.
    fn zrandmember_withscores<K: ToValkeyArgs>(key: K, count: isize) {
        build_cmd!("ZRANDMEMBER", key, count, "WITHSCORES")
    }

    /// `ZRANGE`.
    fn zrange<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("ZRANGE", key, start, stop)
    }

    /// `ZRANGE WITHSCORES`.
    fn zrange_withscores<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("ZRANGE", key, start, stop, "WITHSCORES")
    }

    /// `ZRANGEBYLEX`.
    fn zrangebylex<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZRANGEBYLEX", key, min, max)
    }

    /// `ZRANGEBYLEX LIMIT`.
    fn zrangebylex_limit<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM, offset: isize, count: isize) {
        build_cmd!("ZRANGEBYLEX", key, min, max, "LIMIT", offset, count)
    }

    /// `ZREVRANGEBYLEX`.
    fn zrevrangebylex<K: ToValkeyArgs, MM: ToValkeyArgs, M: ToValkeyArgs>(key: K, max: MM, min: M) {
        build_cmd!("ZREVRANGEBYLEX", key, max, min)
    }

    /// `ZREVRANGEBYLEX LIMIT`.
    fn zrevrangebylex_limit<K: ToValkeyArgs, MM: ToValkeyArgs, M: ToValkeyArgs>(key: K, max: MM, min: M, offset: isize, count: isize) {
        build_cmd!("ZREVRANGEBYLEX", key, max, min, "LIMIT", offset, count)
    }

    /// `ZRANGEBYSCORE`.
    fn zrangebyscore<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZRANGEBYSCORE", key, min, max)
    }

    /// `ZRANGEBYSCORE WITHSCORES`.
    fn zrangebyscore_withscores<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZRANGEBYSCORE", key, min, max, "WITHSCORES")
    }

    /// `ZRANGEBYSCORE LIMIT`.
    fn zrangebyscore_limit<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM, offset: isize, count: isize) {
        build_cmd!("ZRANGEBYSCORE", key, min, max, "LIMIT", offset, count)
    }

    /// `ZRANGEBYSCORE WITHSCORES LIMIT`.
    fn zrangebyscore_limit_withscores<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM, offset: isize, count: isize) {
        build_cmd!("ZRANGEBYSCORE", key, min, max, "WITHSCORES", "LIMIT", offset, count)
    }

    /// `ZRANK`.
    fn zrank<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M) {
        build_cmd!("ZRANK", key, member)
    }

    /// `ZREM`.
    fn zrem<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, members: M) {
        build_cmd!("ZREM", key, members)
    }

    /// `ZREMRANGEBYLEX`.
    fn zrembylex<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZREMRANGEBYLEX", key, min, max)
    }

    /// `ZREMRANGEBYRANK`.
    fn zremrangebyrank<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("ZREMRANGEBYRANK", key, start, stop)
    }

    /// `ZREMRANGEBYSCORE`.
    fn zrembyscore<K: ToValkeyArgs, M: ToValkeyArgs, MM: ToValkeyArgs>(key: K, min: M, max: MM) {
        build_cmd!("ZREMRANGEBYSCORE", key, min, max)
    }

    /// `ZREVRANGE`.
    fn zrevrange<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("ZREVRANGE", key, start, stop)
    }

    /// `ZREVRANGE WITHSCORES`.
    fn zrevrange_withscores<K: ToValkeyArgs>(key: K, start: isize, stop: isize) {
        build_cmd!("ZREVRANGE", key, start, stop, "WITHSCORES")
    }

    /// `ZREVRANGEBYSCORE`.
    fn zrevrangebyscore<K: ToValkeyArgs, MM: ToValkeyArgs, M: ToValkeyArgs>(key: K, max: MM, min: M) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min)
    }

    /// `ZREVRANGEBYSCORE WITHSCORES`.
    fn zrevrangebyscore_withscores<K: ToValkeyArgs, MM: ToValkeyArgs, M: ToValkeyArgs>(key: K, max: MM, min: M) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min, "WITHSCORES")
    }

    /// `ZREVRANGEBYSCORE LIMIT`.
    fn zrevrangebyscore_limit<K: ToValkeyArgs, MM: ToValkeyArgs, M: ToValkeyArgs>(key: K, max: MM, min: M, offset: isize, count: isize) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min, "LIMIT", offset, count)
    }

    /// `ZREVRANGEBYSCORE WITHSCORES LIMIT`.
    fn zrevrangebyscore_limit_withscores<K: ToValkeyArgs, MM: ToValkeyArgs, M: ToValkeyArgs>(key: K, max: MM, min: M, offset: isize, count: isize) {
        build_cmd!("ZREVRANGEBYSCORE", key, max, min, "WITHSCORES", "LIMIT", offset, count)
    }

    /// `ZREVRANK`.
    fn zrevrank<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M) {
        build_cmd!("ZREVRANK", key, member)
    }

    /// `ZSCORE`.
    fn zscore<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, member: M) {
        build_cmd!("ZSCORE", key, member)
    }

    /// `ZMSCORE`.
    fn zscore_multiple<K: ToValkeyArgs, M: ToValkeyArgs>(key: K, members: &'a [M]) {
        build_cmd!("ZMSCORE", key, members)
    }

    /// `ZUNIONSTORE`.
    fn zunionstore<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: &'a [K]) {
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys)
    }

    /// `ZUNIONSTORE AGGREGATE MIN`.
    fn zunionstore_min<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: &'a [K]) {
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MIN")
    }

    /// `ZUNIONSTORE AGGREGATE MAX`.
    fn zunionstore_max<D: ToValkeyArgs, K: ToValkeyArgs>(dstkey: D, keys: &'a [K]) {
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MAX")
    }

    /// `ZUNIONSTORE WEIGHTS`.
    fn zunionstore_weights<D: ToValkeyArgs, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "WEIGHTS", weights)
    }

    /// `ZUNIONSTORE AGGREGATE MIN WEIGHTS`.
    fn zunionstore_min_weights<D: ToValkeyArgs, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MIN", "WEIGHTS", weights)
    }

    /// `ZUNIONSTORE AGGREGATE MAX WEIGHTS`.
    fn zunionstore_max_weights<D: ToValkeyArgs, K: ToValkeyArgs, W: ToValkeyArgs>(dstkey: D, keys: &'a [(K, W)]) {
        let (keys, weights) = unzip_weights(keys);
        build_cmd!("ZUNIONSTORE", dstkey, keys.len(), keys, "AGGREGATE", "MAX", "WEIGHTS", weights)
    }

    // ==== HyperLogLog ===================================================

    /// `PFADD`.
    fn pfadd<K: ToValkeyArgs, E: ToValkeyArgs>(key: K, element: E) {
        build_cmd!("PFADD", key, element)
    }

    /// `PFCOUNT`.
    fn pfcount<K: ToValkeyArgs>(key: K) {
        build_cmd!("PFCOUNT", key)
    }

    /// `PFMERGE`.
    fn pfmerge<D: ToValkeyArgs, S: ToValkeyArgs>(dstkey: D, srckeys: S) {
        build_cmd!("PFMERGE", dstkey, srckeys)
    }

    // ==== Bitmaps =======================================================

    /// `SETBIT`.
    fn setbit<K: ToValkeyArgs>(key: K, offset: usize, value: bool) {
        build_cmd!("SETBIT", key, offset, i32::from(value))
    }

    /// `GETBIT`.
    fn getbit<K: ToValkeyArgs>(key: K, offset: usize) {
        build_cmd!("GETBIT", key, offset)
    }

    /// `BITCOUNT`.
    fn bitcount<K: ToValkeyArgs>(key: K) {
        build_cmd!("BITCOUNT", key)
    }

    /// `BITCOUNT`.
    fn bitcount_range<K: ToValkeyArgs>(key: K, start: usize, end: usize) {
        build_cmd!("BITCOUNT", key, start, end)
    }

    /// `BITOP AND`.
    fn bit_and<D: ToValkeyArgs, S: ToValkeyArgs>(dstkey: D, srckeys: S) {
        build_cmd!("BITOP", "AND", dstkey, srckeys)
    }

    /// `BITOP OR`.
    fn bit_or<D: ToValkeyArgs, S: ToValkeyArgs>(dstkey: D, srckeys: S) {
        build_cmd!("BITOP", "OR", dstkey, srckeys)
    }

    /// `BITOP XOR`.
    fn bit_xor<D: ToValkeyArgs, S: ToValkeyArgs>(dstkey: D, srckeys: S) {
        build_cmd!("BITOP", "XOR", dstkey, srckeys)
    }

    /// `BITOP NOT`.
    fn bit_not<D: ToValkeyArgs, S: ToValkeyArgs>(dstkey: D, srckey: S) {
        build_cmd!("BITOP", "NOT", dstkey, srckey)
    }

    // ==== Pub/Sub =======================================================

    /// `PUBLISH`.
    fn publish<K: ToValkeyArgs, E: ToValkeyArgs>(channel: K, message: E) {
        build_cmd!("PUBLISH", channel, message)
    }
}
