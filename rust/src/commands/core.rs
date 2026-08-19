// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's command API: [`AsyncCommands`] and [`Commands`].
//!
//! One command table (below) defines both traits via the
//! `implement_glide_commands!` macro. Entries are source-compatible with the
//! vendored redis-rs fork (v0.25.2, predating the upstream license change):
//! same method names, generic parameter order, and argument lists, so
//! migrated call sites (including turbofish annotations) compile unchanged.
//! Every table method delegates to the fork's `Cmd::<name>()` constructor, so
//! the wire encoding is identical by construction; signature parity is
//! enforced by `tests/it_parity_guard.rs`.
//!
//! The built command is handed to glide-core **by value** through
//! [`AsyncCommands::glide_send_owned`] — the same zero-extra-copy path as the
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

use redis::{
    Cmd, Direction, Expiry, FromRedisValue, LposOptions, RedisFuture, SetOptions, ToRedisArgs,
    Value, from_owned_redis_value,
};
// Only the blocking (`Commands`) flavor names `RedisResult` directly.
#[cfg(feature = "sync")]
use redis::RedisResult;

/// Defines the unified [`AsyncCommands`] and [`Commands`] traits from one
/// command table.
///
/// Each `fn name<G: Bound>(args);` entry expands to an async method (generic
/// `RV: FromRedisValue` return, `&self` receiver, owned-send dispatch) and its
/// blocking counterpart. The method body is always
/// `Cmd::name(args) -> glide_send_owned`, delegating argument encoding to the
/// fork's generated constructors.
macro_rules! implement_glide_commands {
    (
        $lifetime:lifetime;
        $(
            $(#[$attr:meta])*
            fn $name:ident $(<$($g:ident: $b:ident),+>)? ($($arg:ident: $ty:ty),*);
        )*
    ) => {
        /// **GLIDE's async command API.**
        ///
        /// Implemented by [`crate::GlideClient`] and
        /// [`crate::GlideClusterClient`] — see the [module docs](self).
        ///
        /// Deliberately **not** tied to the `redis` crate's connection-object
        /// traits: every method dispatches through [`Self::glide_send_owned`],
        /// GLIDE's zero-extra-copy path.
        pub trait AsyncCommands: Send + Sync + Sized {
            /// Send an already-built command **by value** (no clone). This is
            /// the single required method; every typed command delegates to
            /// it. Also useful directly as a zero-extra-copy escape hatch for
            /// custom commands with large payloads.
            fn glide_send_owned<'a>(&'a self, cmd: Cmd) -> RedisFuture<'a, Value>;

            /// Typed escape hatch: send an already-built [`Cmd`] by value and
            /// decode the reply into `RV`. This replaces
            /// `cmd(...).query_async(&mut con)` call sites — same decode, no
            /// connection-object machinery, no payload copy.
            #[inline]
            fn glide_send<'a, RV: FromRedisValue>(&'a self, cmd: Cmd) -> RedisFuture<'a, RV> {
                Box::pin(async move { from_owned_redis_value(self.glide_send_owned(cmd).await?) })
            }

            $(
                $(#[$attr])*
                #[inline]
                #[allow(deprecated)]
                #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
                fn $name<$lifetime, $($($g: $b + Send + Sync + $lifetime,)+)? RV>(
                    &$lifetime self $(, $arg: $ty)*
                ) -> RedisFuture<$lifetime, RV>
                where
                    RV: FromRedisValue,
                {
                    let cmd = Cmd::$name($($arg),*);
                    Box::pin(async move { from_owned_redis_value(self.glide_send_owned(cmd).await?) })
                }
            )*

    // The scan iterators are a deliberate GLIDE deviation from redis-rs:
    // `&self` receivers returning GLIDE's own iterator type (same
    // `next_item()` call shape), with every page dispatched by value on the
    // owned-send path — no connection-object machinery, no per-page copies.

    /// Cursor-driven `SCAN` over the whole keyspace.
    #[inline]
    fn scan<'s, RV: FromRedisValue + Send + 's>(
        &'s self,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        Box::pin(crate::commands::scan::ScanIter::new(
            self,
            vec![b"SCAN".to_vec()],
            Vec::new(),
        ))
    }

    /// Cursor-driven `SCAN` over the keyspace, filtered by a `MATCH` pattern.
    #[inline]
    fn scan_match<'s, P: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        pattern: P,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(
            self,
            vec![b"SCAN".to_vec()],
            suffix,
        ))
    }

    /// Cursor-driven `HSCAN` over a hash's fields and values.
    #[inline]
    fn hscan<'s, K: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        key: K,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, Vec::new()))
    }

    /// Cursor-driven `HSCAN`, filtered by a field-name `MATCH` pattern.
    #[inline]
    fn hscan_match<'s, K: ToRedisArgs, P: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        key: K,
        pattern: P,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, suffix))
    }

    /// Cursor-driven `SSCAN` over a set's members.
    #[inline]
    fn sscan<'s, K: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        key: K,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, Vec::new()))
    }

    /// Cursor-driven `SSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn sscan_match<'s, K: ToRedisArgs, P: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        key: K,
        pattern: P,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, suffix))
    }

    /// Cursor-driven `ZSCAN` over a sorted set's members and scores.
    #[inline]
    fn zscan<'s, K: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        key: K,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        Box::pin(crate::commands::scan::ScanIter::new(self, prefix, Vec::new()))
    }

    /// Cursor-driven `ZSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn zscan_match<'s, K: ToRedisArgs, P: ToRedisArgs, RV: FromRedisValue + Send + 's>(
        &'s self,
        key: K,
        pattern: P,
    ) -> RedisFuture<'s, crate::commands::scan::ScanIter<'s, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
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
            fn glide_send_owned_sync(&self, cmd: Cmd) -> RedisResult<Value>;

            /// Typed escape hatch (blocking counterpart of the async
            /// `glide_send`): send an already-built [`Cmd`] by value and
            /// decode the reply into `RV`.
            #[inline]
            fn glide_send_sync<RV: FromRedisValue>(&self, cmd: Cmd) -> RedisResult<RV> {
                from_owned_redis_value(self.glide_send_owned_sync(cmd)?)
            }

            $(
                $(#[$attr])*
                #[inline]
                #[allow(deprecated)]
                #[allow(clippy::extra_unused_lifetimes, clippy::needless_lifetimes)]
                fn $name<$lifetime, $($($g: $b,)+)? RV: FromRedisValue>(
                    &self $(, $arg: $ty)*
                ) -> RedisResult<RV> {
                    from_owned_redis_value(self.glide_send_owned_sync(Cmd::$name($($arg),*))?)
                }
            )*

    // See the async trait: GLIDE-owned iterators on the owned-send path.
    // `SyncScanIter` implements `Iterator`, so `for` loops work as before.

    /// Cursor-driven `SCAN` over the whole keyspace.
    #[inline]
    fn scan<RV: FromRedisValue>(
        &self,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        crate::commands::scan::SyncScanIter::new(self, vec![b"SCAN".to_vec()], Vec::new())
    }

    /// Cursor-driven `SCAN` over the keyspace, filtered by a `MATCH` pattern.
    #[inline]
    fn scan_match<P: ToRedisArgs, RV: FromRedisValue>(
        &self,
        pattern: P,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, vec![b"SCAN".to_vec()], suffix)
    }

    /// Cursor-driven `HSCAN` over a hash's fields and values.
    #[inline]
    fn hscan<K: ToRedisArgs, RV: FromRedisValue>(
        &self,
        key: K,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        crate::commands::scan::SyncScanIter::new(self, prefix, Vec::new())
    }

    /// Cursor-driven `HSCAN`, filtered by a field-name `MATCH` pattern.
    #[inline]
    fn hscan_match<K: ToRedisArgs, P: ToRedisArgs, RV: FromRedisValue>(
        &self,
        key: K,
        pattern: P,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"HSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, prefix, suffix)
    }

    /// Cursor-driven `SSCAN` over a set's members.
    #[inline]
    fn sscan<K: ToRedisArgs, RV: FromRedisValue>(
        &self,
        key: K,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        crate::commands::scan::SyncScanIter::new(self, prefix, Vec::new())
    }

    /// Cursor-driven `SSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn sscan_match<K: ToRedisArgs, P: ToRedisArgs, RV: FromRedisValue>(
        &self,
        key: K,
        pattern: P,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"SSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, prefix, suffix)
    }

    /// Cursor-driven `ZSCAN` over a sorted set's members and scores.
    #[inline]
    fn zscan<K: ToRedisArgs, RV: FromRedisValue>(
        &self,
        key: K,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        crate::commands::scan::SyncScanIter::new(self, prefix, Vec::new())
    }

    /// Cursor-driven `ZSCAN`, filtered by a `MATCH` pattern.
    #[inline]
    fn zscan_match<K: ToRedisArgs, P: ToRedisArgs, RV: FromRedisValue>(
        &self,
        key: K,
        pattern: P,
    ) -> RedisResult<crate::commands::scan::SyncScanIter<'_, Self, RV>> {
        let mut prefix = vec![b"ZSCAN".to_vec()];
        key.write_redis_args(&mut prefix);
        let mut suffix = vec![b"MATCH".to_vec()];
        pattern.write_redis_args(&mut suffix);
        crate::commands::scan::SyncScanIter::new(self, prefix, suffix)
    }        }
    };
}

implement_glide_commands! {
    'a;

    // ==== Strings =======================================================
    /// `GET` (`MGET` when `key` is a slice).
    fn get<K: ToRedisArgs>(key: K);
    /// `MGET`.
    fn mget<K: ToRedisArgs>(key: K);
    /// `SET`.
    fn set<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `SET`.
    fn set_options<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, options: SetOptions);
    /// `MSET`.
    #[allow(deprecated)]
    #[deprecated(since = "0.2.0", note = "use mset() (same command)")]
    fn set_multiple<K: ToRedisArgs, V: ToRedisArgs>(items: &'a [(K, V)]);
    /// `MSET`.
    fn mset<K: ToRedisArgs, V: ToRedisArgs>(items: &'a [(K, V)]);
    /// `SETEX`.
    fn set_ex<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, seconds: u64);
    /// `PSETEX`.
    fn pset_ex<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, milliseconds: u64);
    /// `SETNX`.
    fn set_nx<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `MSETNX`.
    fn mset_nx<K: ToRedisArgs, V: ToRedisArgs>(items: &'a [(K, V)]);
    /// `GETSET`.
    fn getset<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `GETRANGE`.
    fn getrange<K: ToRedisArgs>(key: K, from: isize, to: isize);
    /// `SETRANGE`.
    fn setrange<K: ToRedisArgs, V: ToRedisArgs>(key: K, offset: isize, value: V);
    /// `GETEX`.
    fn get_ex<K: ToRedisArgs>(key: K, expire_at: Expiry);
    /// `GETDEL`.
    fn get_del<K: ToRedisArgs>(key: K);
    /// `APPEND`.
    fn append<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `INCRBY` (`INCRBYFLOAT` for float deltas).
    fn incr<K: ToRedisArgs, V: ToRedisArgs>(key: K, delta: V);
    /// `DECRBY`.
    fn decr<K: ToRedisArgs, V: ToRedisArgs>(key: K, delta: V);
    /// `STRLEN`.
    fn strlen<K: ToRedisArgs>(key: K);

    // ==== Keys & expiry =================================================
    /// `KEYS`.
    fn keys<K: ToRedisArgs>(key: K);
    /// `DEL`.
    fn del<K: ToRedisArgs>(key: K);
    /// `EXISTS`.
    fn exists<K: ToRedisArgs>(key: K);
    /// `TYPE`.
    fn key_type<K: ToRedisArgs>(key: K);
    /// `EXPIRE`.
    fn expire<K: ToRedisArgs>(key: K, seconds: i64);
    /// `EXPIREAT`.
    fn expire_at<K: ToRedisArgs>(key: K, ts: i64);
    /// `PEXPIRE`.
    fn pexpire<K: ToRedisArgs>(key: K, ms: i64);
    /// `PEXPIREAT`.
    fn pexpire_at<K: ToRedisArgs>(key: K, ts: i64);
    /// `PERSIST`.
    fn persist<K: ToRedisArgs>(key: K);
    /// `TTL`.
    fn ttl<K: ToRedisArgs>(key: K);
    /// `PTTL`.
    fn pttl<K: ToRedisArgs>(key: K);
    /// `RENAME`.
    fn rename<K: ToRedisArgs, N: ToRedisArgs>(key: K, new_key: N);
    /// `RENAMENX`.
    fn rename_nx<K: ToRedisArgs, N: ToRedisArgs>(key: K, new_key: N);
    /// `UNLINK`.
    fn unlink<K: ToRedisArgs>(key: K);
    /// `OBJECT ENCODING`.
    fn object_encoding<K: ToRedisArgs>(key: K);
    /// `OBJECT IDLETIME`.
    fn object_idletime<K: ToRedisArgs>(key: K);
    /// `OBJECT FREQ`.
    fn object_freq<K: ToRedisArgs>(key: K);
    /// `OBJECT REFCOUNT`.
    fn object_refcount<K: ToRedisArgs>(key: K);

    // ==== Lists =========================================================
    /// `BLMOVE`.
    fn blmove<S: ToRedisArgs, D: ToRedisArgs>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction, timeout: f64);
    /// `BLMPOP`.
    fn blmpop<K: ToRedisArgs>(timeout: f64, numkeys: usize, key: K, dir: Direction, count: usize);
    /// `BLPOP`.
    fn blpop<K: ToRedisArgs>(key: K, timeout: f64);
    /// `BRPOP`.
    fn brpop<K: ToRedisArgs>(key: K, timeout: f64);
    /// `BRPOPLPUSH`.
    fn brpoplpush<S: ToRedisArgs, D: ToRedisArgs>(srckey: S, dstkey: D, timeout: f64);
    /// `LINDEX`.
    fn lindex<K: ToRedisArgs>(key: K, index: isize);
    /// `LINSERT`.
    fn linsert_before<K: ToRedisArgs, P: ToRedisArgs, V: ToRedisArgs>(key: K, pivot: P, value: V);
    /// `LINSERT`.
    fn linsert_after<K: ToRedisArgs, P: ToRedisArgs, V: ToRedisArgs>(key: K, pivot: P, value: V);
    /// `LLEN`.
    fn llen<K: ToRedisArgs>(key: K);
    /// `LMOVE`.
    fn lmove<S: ToRedisArgs, D: ToRedisArgs>(srckey: S, dstkey: D, src_dir: Direction, dst_dir: Direction);
    /// `LMPOP`.
    fn lmpop<K: ToRedisArgs>(numkeys: usize, key: K, dir: Direction, count: usize);
    /// `LPOP`.
    fn lpop<K: ToRedisArgs>(key: K, count: Option<core::num::NonZeroUsize>);
    /// `LPOS`.
    fn lpos<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V, options: LposOptions);
    /// `LPUSH`.
    fn lpush<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `LPUSHX`.
    fn lpush_exists<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `LRANGE`.
    fn lrange<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `LREM`.
    fn lrem<K: ToRedisArgs, V: ToRedisArgs>(key: K, count: isize, value: V);
    /// `LTRIM`.
    fn ltrim<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `LSET`.
    fn lset<K: ToRedisArgs, V: ToRedisArgs>(key: K, index: isize, value: V);
    /// `RPOP`.
    fn rpop<K: ToRedisArgs>(key: K, count: Option<core::num::NonZeroUsize>);
    /// `RPOPLPUSH`.
    fn rpoplpush<K: ToRedisArgs, D: ToRedisArgs>(key: K, dstkey: D);
    /// `RPUSH`.
    fn rpush<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);
    /// `RPUSHX`.
    fn rpush_exists<K: ToRedisArgs, V: ToRedisArgs>(key: K, value: V);

    // ==== Hashes ========================================================
    /// `HGET` (`HMGET` when `field` is a slice).
    fn hget<K: ToRedisArgs, F: ToRedisArgs>(key: K, field: F);
    /// `HDEL`.
    fn hdel<K: ToRedisArgs, F: ToRedisArgs>(key: K, field: F);
    /// `HSET`.
    fn hset<K: ToRedisArgs, F: ToRedisArgs, V: ToRedisArgs>(key: K, field: F, value: V);
    /// `HSETNX`.
    fn hset_nx<K: ToRedisArgs, F: ToRedisArgs, V: ToRedisArgs>(key: K, field: F, value: V);
    /// `HMSET`.
    fn hset_multiple<K: ToRedisArgs, F: ToRedisArgs, V: ToRedisArgs>(key: K, items: &'a [(F, V)]);
    /// `HINCRBY` (`HINCRBYFLOAT` for float deltas).
    fn hincr<K: ToRedisArgs, F: ToRedisArgs, D: ToRedisArgs>(key: K, field: F, delta: D);
    /// `HEXISTS`.
    fn hexists<K: ToRedisArgs, F: ToRedisArgs>(key: K, field: F);
    /// `HKEYS`.
    fn hkeys<K: ToRedisArgs>(key: K);
    /// `HVALS`.
    fn hvals<K: ToRedisArgs>(key: K);
    /// `HGETALL`.
    fn hgetall<K: ToRedisArgs>(key: K);
    /// `HLEN`.
    fn hlen<K: ToRedisArgs>(key: K);

    // ==== Sets ==========================================================
    /// `SADD`.
    fn sadd<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M);
    /// `SCARD`.
    fn scard<K: ToRedisArgs>(key: K);
    /// `SDIFF`.
    fn sdiff<K: ToRedisArgs>(keys: K);
    /// `SDIFFSTORE`.
    fn sdiffstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: K);
    /// `SINTER`.
    fn sinter<K: ToRedisArgs>(keys: K);
    /// `SINTERSTORE`.
    fn sinterstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: K);
    /// `SISMEMBER`.
    fn sismember<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M);
    /// `SMISMEMBER`.
    fn smismember<K: ToRedisArgs, M: ToRedisArgs>(key: K, members: M);
    /// `SMEMBERS`.
    fn smembers<K: ToRedisArgs>(key: K);
    /// `SMOVE`.
    fn smove<S: ToRedisArgs, D: ToRedisArgs, M: ToRedisArgs>(srckey: S, dstkey: D, member: M);
    /// `SPOP`.
    fn spop<K: ToRedisArgs>(key: K);
    /// `SRANDMEMBER`.
    fn srandmember<K: ToRedisArgs>(key: K);
    /// `SRANDMEMBER`.
    fn srandmember_multiple<K: ToRedisArgs>(key: K, count: usize);
    /// `SREM`.
    fn srem<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M);
    /// `SUNION`.
    fn sunion<K: ToRedisArgs>(keys: K);
    /// `SUNIONSTORE`.
    fn sunionstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: K);

    // ==== Sorted sets ===================================================
    /// `ZADD`.
    fn zadd<K: ToRedisArgs, S: ToRedisArgs, M: ToRedisArgs>(key: K, member: M, score: S);
    /// `ZADD`.
    fn zadd_multiple<K: ToRedisArgs, S: ToRedisArgs, M: ToRedisArgs>(key: K, items: &'a [(S, M)]);
    /// `ZCARD`.
    fn zcard<K: ToRedisArgs>(key: K);
    /// `ZCOUNT`.
    fn zcount<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `ZINCRBY`.
    fn zincr<K: ToRedisArgs, M: ToRedisArgs, D: ToRedisArgs>(key: K, member: M, delta: D);
    /// `ZINTERSTORE`.
    fn zinterstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]);
    /// `ZINTERSTORE`.
    fn zinterstore_min<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]);
    /// `ZINTERSTORE`.
    fn zinterstore_max<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]);
    /// `ZINTERSTORE`.
    fn zinterstore_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]);
    /// `ZINTERSTORE`.
    fn zinterstore_min_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]);
    /// `ZINTERSTORE`.
    fn zinterstore_max_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]);
    /// `ZLEXCOUNT`.
    fn zlexcount<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `BZPOPMAX`.
    fn bzpopmax<K: ToRedisArgs>(key: K, timeout: f64);
    /// `ZPOPMAX`.
    fn zpopmax<K: ToRedisArgs>(key: K, count: isize);
    /// `BZPOPMIN`.
    fn bzpopmin<K: ToRedisArgs>(key: K, timeout: f64);
    /// `ZPOPMIN`.
    fn zpopmin<K: ToRedisArgs>(key: K, count: isize);
    /// `BZMPOP`.
    fn bzmpop_max<K: ToRedisArgs>(timeout: f64, keys: &'a [K], count: isize);
    /// `ZMPOP`.
    fn zmpop_max<K: ToRedisArgs>(keys: &'a [K], count: isize);
    /// `BZMPOP`.
    fn bzmpop_min<K: ToRedisArgs>(timeout: f64, keys: &'a [K], count: isize);
    /// `ZMPOP`.
    fn zmpop_min<K: ToRedisArgs>(keys: &'a [K], count: isize);
    /// `ZRANDMEMBER`.
    fn zrandmember<K: ToRedisArgs>(key: K, count: Option<isize>);
    /// `ZRANDMEMBER`.
    fn zrandmember_withscores<K: ToRedisArgs>(key: K, count: isize);
    /// `ZRANGE`.
    fn zrange<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `ZRANGE`.
    fn zrange_withscores<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `ZRANGEBYLEX`.
    fn zrangebylex<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `ZRANGEBYLEX`.
    fn zrangebylex_limit<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM, offset: isize, count: isize);
    /// `ZREVRANGEBYLEX`.
    fn zrevrangebylex<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M);
    /// `ZREVRANGEBYLEX`.
    fn zrevrangebylex_limit<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M, offset: isize, count: isize);
    /// `ZRANGEBYSCORE`.
    fn zrangebyscore<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `ZRANGEBYSCORE`.
    fn zrangebyscore_withscores<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `ZRANGEBYSCORE`.
    fn zrangebyscore_limit<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM, offset: isize, count: isize);
    /// `ZRANGEBYSCORE`.
    fn zrangebyscore_limit_withscores<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM, offset: isize, count: isize);
    /// `ZRANK`.
    fn zrank<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M);
    /// `ZREM`.
    fn zrem<K: ToRedisArgs, M: ToRedisArgs>(key: K, members: M);
    /// `ZREMRANGEBYLEX`.
    fn zrembylex<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `ZREMRANGEBYRANK`.
    fn zremrangebyrank<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `ZREMRANGEBYSCORE`.
    fn zrembyscore<K: ToRedisArgs, M: ToRedisArgs, MM: ToRedisArgs>(key: K, min: M, max: MM);
    /// `ZREVRANGE`.
    fn zrevrange<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `ZREVRANGE`.
    fn zrevrange_withscores<K: ToRedisArgs>(key: K, start: isize, stop: isize);
    /// `ZREVRANGEBYSCORE`.
    fn zrevrangebyscore<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M);
    /// `ZREVRANGEBYSCORE`.
    fn zrevrangebyscore_withscores<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M);
    /// `ZREVRANGEBYSCORE`.
    fn zrevrangebyscore_limit<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M, offset: isize, count: isize);
    /// `ZREVRANGEBYSCORE`.
    fn zrevrangebyscore_limit_withscores<K: ToRedisArgs, MM: ToRedisArgs, M: ToRedisArgs>(key: K, max: MM, min: M, offset: isize, count: isize);
    /// `ZREVRANK`.
    fn zrevrank<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M);
    /// `ZSCORE`.
    fn zscore<K: ToRedisArgs, M: ToRedisArgs>(key: K, member: M);
    /// `ZMSCORE`.
    fn zscore_multiple<K: ToRedisArgs, M: ToRedisArgs>(key: K, members: &'a [M]);
    /// `ZUNIONSTORE`.
    fn zunionstore<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]);
    /// `ZUNIONSTORE`.
    fn zunionstore_min<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]);
    /// `ZUNIONSTORE`.
    fn zunionstore_max<D: ToRedisArgs, K: ToRedisArgs>(dstkey: D, keys: &'a [K]);
    /// `ZUNIONSTORE`.
    fn zunionstore_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]);
    /// `ZUNIONSTORE`.
    fn zunionstore_min_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]);
    /// `ZUNIONSTORE`.
    fn zunionstore_max_weights<D: ToRedisArgs, K: ToRedisArgs, W: ToRedisArgs>(dstkey: D, keys: &'a [(K, W)]);

    // ==== HyperLogLog ===================================================
    /// `PFADD`.
    fn pfadd<K: ToRedisArgs, E: ToRedisArgs>(key: K, element: E);
    /// `PFCOUNT`.
    fn pfcount<K: ToRedisArgs>(key: K);
    /// `PFMERGE`.
    fn pfmerge<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S);

    // ==== Bitmaps =======================================================
    /// `SETBIT`.
    fn setbit<K: ToRedisArgs>(key: K, offset: usize, value: bool);
    /// `GETBIT`.
    fn getbit<K: ToRedisArgs>(key: K, offset: usize);
    /// `BITCOUNT`.
    fn bitcount<K: ToRedisArgs>(key: K);
    /// `BITCOUNT`.
    fn bitcount_range<K: ToRedisArgs>(key: K, start: usize, end: usize);
    /// `BITOP`.
    fn bit_and<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S);
    /// `BITOP`.
    fn bit_or<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S);
    /// `BITOP`.
    fn bit_xor<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckeys: S);
    /// `BITOP`.
    fn bit_not<D: ToRedisArgs, S: ToRedisArgs>(dstkey: D, srckey: S);

    // ==== Pub/Sub =======================================================
    /// `PUBLISH`.
    fn publish<K: ToRedisArgs, E: ToRedisArgs>(channel: K, message: E);
}
