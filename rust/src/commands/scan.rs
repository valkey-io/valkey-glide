// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! GLIDE's cursor-driven scan iterators, returned by the `scan*` methods of
//! [`crate::AsyncCommands`] / `glide::Commands`.
//!
//! These ride the unified API's owned-send dispatch (each page is one command
//! built fresh and handed to glide-core **by value**), so scanning never
//! touches the `redis` connection-object machinery. Call-site shape matches
//! the familiar redis-rs iterators:
//!
//! ```rust,no_run
//! # use glide::AsyncCommands;
//! # async fn demo(client: glide::GlideClient) -> glide::RedisResult<()> {
//! let mut iter = client.scan_match::<_, String>("prefix:*").await?;
//! while let Some(key) = iter.next_item().await {
//!     println!("{key}");
//! }
//! # Ok(()) }
//! ```
//!
//! Iteration semantics follow the redis-rs iterators: the first page is
//! fetched eagerly (errors surface at the `scan*` call), and a failure while
//! fetching a later page ends the iteration.

use crate::commands::core::AsyncCommands;
use redis::{Cmd, FromRedisValue, RedisResult, from_owned_redis_value};

/// Argument layout of one scan page: `prefix… <cursor> suffix…`
/// (e.g. `HSCAN key <cursor> MATCH pattern`).
#[derive(Debug)]
struct PageSpec {
    prefix: Vec<Vec<u8>>,
    suffix: Vec<Vec<u8>>,
}

impl PageSpec {
    fn to_cmd(&self, cursor: u64) -> Cmd {
        let mut cmd = Cmd::new();
        for a in &self.prefix {
            cmd.arg(&a[..]);
        }
        cmd.arg(cursor);
        for a in &self.suffix {
            cmd.arg(&a[..]);
        }
        cmd
    }
}

/// An in-progress async `SCAN`/`HSCAN`/`SSCAN`/`ZSCAN` iteration.
///
/// Created by the `scan*` methods on [`AsyncCommands`]; see the
/// [module docs](self).
pub struct ScanIter<'a, C: ?Sized, RV> {
    con: &'a C,
    spec: PageSpec,
    cursor: u64,
    batch: std::vec::IntoIter<RV>,
}

impl<'a, C: AsyncCommands, RV: FromRedisValue> ScanIter<'a, C, RV> {
    /// Start an iteration: fetch the first page eagerly so errors surface at
    /// the `scan*` call (matching redis-rs iterator behaviour).
    pub(crate) async fn new(
        con: &'a C,
        prefix: Vec<Vec<u8>>,
        suffix: Vec<Vec<u8>>,
    ) -> RedisResult<ScanIter<'a, C, RV>> {
        let spec = PageSpec { prefix, suffix };
        let (cursor, batch): (u64, Vec<RV>) =
            from_owned_redis_value(con.glide_send_owned(spec.to_cmd(0)).await?)?;
        Ok(ScanIter {
            con,
            spec,
            cursor,
            batch: batch.into_iter(),
        })
    }

    /// The next scanned element, or `None` when the iteration is complete.
    /// An error while fetching a subsequent page also ends the iteration
    /// (matching redis-rs iterator behaviour).
    pub async fn next_item(&mut self) -> Option<RV> {
        // Loop: a whole page may be empty (server-side MATCH filtering), so
        // keep fetching until an item is produced or the cursor wraps to 0.
        loop {
            if let Some(v) = self.batch.next() {
                return Some(v);
            }
            if self.cursor == 0 {
                return None;
            }
            let reply = self
                .con
                .glide_send_owned(self.spec.to_cmd(self.cursor))
                .await
                .ok()?;
            let (cursor, batch): (u64, Vec<RV>) = from_owned_redis_value(reply).ok()?;
            self.cursor = cursor;
            self.batch = batch.into_iter();
        }
    }
}

/// An in-progress blocking `SCAN`/`HSCAN`/`SSCAN`/`ZSCAN` iteration.
///
/// Created by the `scan*` methods on `glide::Commands`; implements
/// [`Iterator`], so it works with `for` loops and iterator adapters.
#[cfg(feature = "sync")]
pub struct SyncScanIter<'a, C: ?Sized, RV> {
    con: &'a C,
    spec: PageSpec,
    cursor: u64,
    batch: std::vec::IntoIter<RV>,
}

#[cfg(feature = "sync")]
impl<'a, C: crate::commands::core::Commands, RV: FromRedisValue> SyncScanIter<'a, C, RV> {
    /// Start an iteration (first page fetched eagerly; see [`ScanIter::new`]).
    pub(crate) fn new(
        con: &'a C,
        prefix: Vec<Vec<u8>>,
        suffix: Vec<Vec<u8>>,
    ) -> RedisResult<SyncScanIter<'a, C, RV>> {
        let spec = PageSpec { prefix, suffix };
        let (cursor, batch): (u64, Vec<RV>) =
            from_owned_redis_value(con.glide_send_owned_sync(spec.to_cmd(0))?)?;
        Ok(SyncScanIter {
            con,
            spec,
            cursor,
            batch: batch.into_iter(),
        })
    }
}

#[cfg(feature = "sync")]
impl<C: crate::commands::core::Commands, RV: FromRedisValue> Iterator for SyncScanIter<'_, C, RV> {
    type Item = RV;

    fn next(&mut self) -> Option<RV> {
        loop {
            if let Some(v) = self.batch.next() {
                return Some(v);
            }
            if self.cursor == 0 {
                return None;
            }
            let reply = self
                .con
                .glide_send_owned_sync(self.spec.to_cmd(self.cursor))
                .ok()?;
            let (cursor, batch): (u64, Vec<RV>) = from_owned_redis_value(reply).ok()?;
            self.cursor = cursor;
            self.batch = batch.into_iter();
        }
    }
}
