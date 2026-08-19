// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Stream commands. Mirrors Python's stream command surface.
#![allow(clippy::too_many_arguments, clippy::type_complexity)]

use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

/// A single stream entry: its ID and its field/value pairs.
pub type StreamEntry = (String, Vec<(Bytes, Bytes)>);

/// Trim strategy for `XADD`/`XTRIM`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StreamTrimStrategy {
    /// Trim by maximum length (`MAXLEN`).
    MaxLen,
    /// Trim by minimum ID (`MINID`).
    MinId,
}

/// Trim options for `XADD`/`XTRIM`.
///
/// Mirrors Python `StreamTrimOptions` (`TrimByMaxLen`/`TrimByMinId`).
#[derive(Debug, Clone)]
pub struct StreamTrimOptions {
    strategy: StreamTrimStrategy,
    /// Exact (`=`) vs near-exact (`~`) trimming.
    exact: bool,
    threshold: String,
    limit: Option<i64>,
}

impl StreamTrimOptions {
    /// Trim by maximum length (`MAXLEN`).
    pub fn max_len(exact: bool, threshold: i64, limit: Option<i64>) -> Self {
        Self {
            strategy: StreamTrimStrategy::MaxLen,
            exact,
            threshold: threshold.to_string(),
            limit,
        }
    }

    /// Trim by minimum ID (`MINID`).
    pub fn min_id(exact: bool, threshold: impl Into<String>, limit: Option<i64>) -> Self {
        Self {
            strategy: StreamTrimStrategy::MinId,
            exact,
            threshold: threshold.into(),
            limit,
        }
    }

    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        match self.strategy {
            StreamTrimStrategy::MaxLen => cmd.arg("MAXLEN"),
            StreamTrimStrategy::MinId => cmd.arg("MINID"),
        };
        cmd.arg(if self.exact { "=" } else { "~" });
        cmd.arg(&self.threshold);
        if let Some(l) = self.limit {
            cmd.arg("LIMIT").arg(l);
        }
    }
}

/// Options for `XADD`.
///
/// Mirrors Python `StreamAddOptions`.
#[derive(Debug, Clone, Default)]
pub struct StreamAddOptions {
    /// If `false`, do not create the stream if it does not exist (`NOMKSTREAM`).
    pub make_stream: bool,
    /// Optional trim to apply as part of the add.
    pub trim: Option<StreamTrimOptions>,
}

impl StreamAddOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if !self.make_stream {
            cmd.arg("NOMKSTREAM");
        }
        if let Some(t) = &self.trim {
            t.add_to(cmd);
        }
    }
}

/// Options for `XREAD` (`BLOCK`/`COUNT`).
///
/// Mirrors Python `StreamReadOptions`.
#[derive(Debug, Clone, Copy, Default)]
pub struct StreamReadOptions {
    /// Block for up to this many milliseconds waiting for entries (`BLOCK`).
    pub block_ms: Option<i64>,
    /// Maximum number of entries to return per stream (`COUNT`).
    pub count: Option<i64>,
}

impl StreamReadOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if let Some(b) = self.block_ms {
            cmd.arg("BLOCK").arg(b);
        }
        if let Some(c) = self.count {
            cmd.arg("COUNT").arg(c);
        }
    }
}

/// Options for `XREADGROUP` (`BLOCK`/`COUNT`/`NOACK`).
///
/// Mirrors Python `StreamReadGroupOptions`.
#[derive(Debug, Clone, Copy, Default)]
pub struct StreamReadGroupOptions {
    /// Block for up to this many milliseconds waiting for entries (`BLOCK`).
    pub block_ms: Option<i64>,
    /// Maximum number of entries to return per stream (`COUNT`).
    pub count: Option<i64>,
    /// Do not add read entries to the Pending Entries List (`NOACK`).
    pub no_ack: bool,
}

impl StreamReadGroupOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if let Some(b) = self.block_ms {
            cmd.arg("BLOCK").arg(b);
        }
        if let Some(c) = self.count {
            cmd.arg("COUNT").arg(c);
        }
        if self.no_ack {
            cmd.arg("NOACK");
        }
    }
}

/// Options for `XGROUP CREATE` (`MKSTREAM`/`ENTRIESREAD`).
///
/// Mirrors Python `StreamGroupOptions`.
#[derive(Debug, Clone, Copy, Default)]
pub struct StreamGroupCreateOptions {
    /// Create the stream if it does not exist (`MKSTREAM`).
    pub make_stream: bool,
    /// Number of entries already read by the group (`ENTRIESREAD`, Valkey 7+).
    pub entries_read: Option<i64>,
}

impl StreamGroupCreateOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if self.make_stream {
            cmd.arg("MKSTREAM");
        }
        if let Some(e) = self.entries_read {
            cmd.arg("ENTRIESREAD").arg(e);
        }
    }
}

/// Options for `XCLAIM`.
///
/// Mirrors Python `StreamClaimOptions`.
#[derive(Debug, Clone, Copy, Default)]
pub struct StreamClaimOptions {
    /// Set the idle time (ms) of the claimed messages (`IDLE`).
    pub idle: Option<i64>,
    /// Set the idle time to a specific Unix time in ms (`TIME`).
    pub idle_unix_time: Option<i64>,
    /// Set the retry counter (`RETRYCOUNT`).
    pub retry_count: Option<i64>,
    /// Create the PEL entry even if the message is not already pending (`FORCE`).
    pub is_force: bool,
}

impl StreamClaimOptions {
    pub(crate) fn add_to(&self, cmd: &mut Cmd) {
        if let Some(i) = self.idle {
            cmd.arg("IDLE").arg(i);
        }
        if let Some(t) = self.idle_unix_time {
            cmd.arg("TIME").arg(t);
        }
        if let Some(r) = self.retry_count {
            cmd.arg("RETRYCOUNT").arg(r);
        }
        if self.is_force {
            cmd.arg("FORCE");
        }
    }
}

/// A pending-summary consumer entry: `(consumer_name, count)`.
pub type PendingConsumer = (Bytes, i64);

/// Summary form of the `XPENDING` reply.
#[derive(Debug, Clone, Default)]
pub struct XPendingSummary {
    /// Total number of pending messages.
    pub count: i64,
    /// Smallest pending ID (`None` if no pending messages).
    pub min_id: Option<Bytes>,
    /// Largest pending ID (`None` if no pending messages).
    pub max_id: Option<Bytes>,
    /// Per-consumer pending counts.
    pub consumers: Vec<PendingConsumer>,
}

/// A single entry from the extended (range) form of `XPENDING`.
#[derive(Debug, Clone)]
pub struct XPendingEntry {
    /// The message ID.
    pub id: Bytes,
    /// The consumer that currently owns the message.
    pub consumer: Bytes,
    /// Milliseconds since the message was last delivered.
    pub idle_ms: i64,
    /// Number of times the message was delivered.
    pub delivery_count: i64,
}

/// Stream commands (`XADD`, `XLEN`, `XRANGE`, `XREAD`, `XDEL`, groups, ...).
#[async_trait]
pub trait StreamCommands: CommandExecutor {
    /// Append an entry to the stream at `key` (`XADD`). Pass `"*"` for an
    /// auto-generated ID. Returns the generated entry ID.
    async fn xadd<K, F, V>(&self, key: K, id: &str, fields: &[(F, V)]) -> Result<Option<String>>
    where
        K: ToRedisArgs + Send + Sync,
        F: ToRedisArgs + Send + Sync,
        V: ToRedisArgs + Send + Sync,
    {
        let mut cmd = Cmd::new();
        cmd.arg("XADD").arg(key).arg(id);
        for (f, v) in fields {
            cmd.arg(f).arg(v);
        }
        value::to_opt_string(self.execute_command(cmd, None).await?)
    }

    /// Get the number of entries in the stream (`XLEN`).
    async fn xlen<K: ToRedisArgs + Send>(&self, key: K) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XLEN").arg(key);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Delete entries by ID (`XDEL`); returns the number deleted.
    async fn xdel<K: ToRedisArgs + Send>(&self, key: K, ids: &[&str]) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XDEL").arg(key);
        for id in ids {
            cmd.arg(*id);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Trim the stream to (approximately) `maxlen` entries (`XTRIM ... MAXLEN`).
    async fn xtrim_maxlen<K: ToRedisArgs + Send>(
        &self,
        key: K,
        maxlen: i64,
        approximate: bool,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XTRIM").arg(key).arg("MAXLEN");
        if approximate {
            cmd.arg("~");
        }
        cmd.arg(maxlen);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Read a range of entries (`XRANGE key start end`).
    async fn xrange<K: ToRedisArgs + Send>(
        &self,
        key: K,
        start: &str,
        end: &str,
    ) -> Result<Vec<StreamEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XRANGE").arg(key).arg(start).arg(end);
        parse_entries(self.execute_command(cmd, None).await?)
    }

    /// Read a range of entries in reverse (`XREVRANGE key end start`).
    async fn xrevrange<K: ToRedisArgs + Send>(
        &self,
        key: K,
        end: &str,
        start: &str,
    ) -> Result<Vec<StreamEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XREVRANGE").arg(key).arg(end).arg(start);
        parse_entries(self.execute_command(cmd, None).await?)
    }

    /// Create a consumer group (`XGROUP CREATE`). Set `mkstream` to create the
    /// stream if it does not exist.
    async fn xgroup_create<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        id: &str,
        mkstream: bool,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("CREATE").arg(key).arg(group).arg(id);
        if mkstream {
            cmd.arg("MKSTREAM");
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Destroy a consumer group (`XGROUP DESTROY`). Returns whether it existed.
    async fn xgroup_destroy<K: ToRedisArgs + Send>(&self, key: K, group: &str) -> Result<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("DESTROY").arg(key).arg(group);
        value::to_bool(self.execute_command(cmd, None).await?)
    }

    /// Acknowledge processed entries in a consumer group (`XACK`).
    async fn xack<K: ToRedisArgs + Send>(&self, key: K, group: &str, ids: &[&str]) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XACK").arg(key).arg(group);
        for id in ids {
            cmd.arg(*id);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Append an entry to the stream with options (`XADD` with `NOMKSTREAM` /
    /// trim). Returns the generated ID, or `None` if `NOMKSTREAM` was set and the
    /// stream did not exist.
    async fn xadd_options<K, F, V>(
        &self,
        key: K,
        id: &str,
        fields: &[(F, V)],
        options: &StreamAddOptions,
    ) -> Result<Option<String>>
    where
        K: ToRedisArgs + Send + Sync,
        F: ToRedisArgs + Send + Sync,
        V: ToRedisArgs + Send + Sync,
    {
        let mut cmd = Cmd::new();
        cmd.arg("XADD").arg(key);
        options.add_to(&mut cmd);
        cmd.arg(id);
        for (f, v) in fields {
            cmd.arg(f).arg(v);
        }
        value::to_opt_string(self.execute_command(cmd, None).await?)
    }

    /// Trim the stream to a minimum ID (`XTRIM ... MINID`). Returns entries removed.
    async fn xtrim_minid<K: ToRedisArgs + Send>(
        &self,
        key: K,
        minid: &str,
        approximate: bool,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XTRIM").arg(key).arg("MINID");
        if approximate {
            cmd.arg("~");
        }
        cmd.arg(minid);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Read from one or more streams (`XREAD`). `keys_ids` is a list of
    /// `(key, id)` pairs. Returns `(stream_key, entries)` per stream that
    /// produced data.
    async fn xread<K: ToRedisArgs + Send + Sync>(
        &self,
        keys_ids: &[(K, &str)],
        options: Option<StreamReadOptions>,
    ) -> Result<Vec<(Bytes, Vec<StreamEntry>)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XREAD");
        if let Some(o) = options {
            o.add_to(&mut cmd);
        }
        cmd.arg("STREAMS");
        for (k, _) in keys_ids {
            cmd.arg(k);
        }
        for (_, id) in keys_ids {
            cmd.arg(*id);
        }
        parse_stream_read(self.execute_command(cmd, None).await?)
    }

    /// Read from streams as part of a consumer group (`XREADGROUP`).
    async fn xreadgroup<K: ToRedisArgs + Send + Sync>(
        &self,
        group: &str,
        consumer: &str,
        keys_ids: &[(K, &str)],
        options: Option<StreamReadGroupOptions>,
    ) -> Result<Vec<(Bytes, Vec<StreamEntry>)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XREADGROUP").arg("GROUP").arg(group).arg(consumer);
        if let Some(o) = options {
            o.add_to(&mut cmd);
        }
        cmd.arg("STREAMS");
        for (k, _) in keys_ids {
            cmd.arg(k);
        }
        for (_, id) in keys_ids {
            cmd.arg(*id);
        }
        parse_stream_read(self.execute_command(cmd, None).await?)
    }

    /// Claim ownership of pending messages (`XCLAIM`). Returns the claimed
    /// entries with their fields.
    async fn xclaim<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        ids: &[&str],
        options: Option<StreamClaimOptions>,
    ) -> Result<Vec<StreamEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XCLAIM")
            .arg(key)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_time_ms);
        for id in ids {
            cmd.arg(*id);
        }
        if let Some(o) = options {
            o.add_to(&mut cmd);
        }
        parse_entries(self.execute_command(cmd, None).await?)
    }

    /// Claim ownership of pending messages, returning only their IDs
    /// (`XCLAIM ... JUSTID`).
    async fn xclaim_justid<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        ids: &[&str],
        options: Option<StreamClaimOptions>,
    ) -> Result<Vec<String>> {
        let mut cmd = Cmd::new();
        cmd.arg("XCLAIM")
            .arg(key)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_time_ms);
        for id in ids {
            cmd.arg(*id);
        }
        if let Some(o) = options {
            o.add_to(&mut cmd);
        }
        cmd.arg("JUSTID");
        collect_strings(self.execute_command(cmd, None).await?)
    }

    /// Automatically claim pending messages idle for at least `min_idle_time_ms`
    /// (`XAUTOCLAIM`). Returns `(next_cursor, claimed_entries, deleted_ids)`.
    async fn xautoclaim<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        start: &str,
        count: Option<i64>,
    ) -> Result<(String, Vec<StreamEntry>, Vec<String>)> {
        let mut cmd = Cmd::new();
        cmd.arg("XAUTOCLAIM")
            .arg(key)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_time_ms)
            .arg(start);
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        parse_autoclaim(self.execute_command(cmd, None).await?)
    }

    /// Automatically claim pending messages returning only their IDs
    async fn xautoclaim_justid<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        start: &str,
        count: Option<i64>,
    ) -> Result<(String, Vec<String>, Vec<String>)> {
        let mut cmd = Cmd::new();
        cmd.arg("XAUTOCLAIM")
            .arg(key)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_time_ms)
            .arg(start);
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        cmd.arg("JUSTID");
        parse_autoclaim_justid(self.execute_command(cmd, None).await?)
    }

    /// Summary form of `XPENDING` (`XPENDING key group`).
    async fn xpending<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
    ) -> Result<XPendingSummary> {
        let mut cmd = Cmd::new();
        cmd.arg("XPENDING").arg(key).arg(group);
        parse_xpending_summary(self.execute_command(cmd, None).await?)
    }

    /// Extended (range) form of `XPENDING`
    /// (`XPENDING key group [IDLE ms] start end count [consumer]`).
    async fn xpending_range<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        start: &str,
        end: &str,
        count: i64,
        min_idle_time_ms: Option<i64>,
        consumer: Option<&str>,
    ) -> Result<Vec<XPendingEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XPENDING").arg(key).arg(group);
        if let Some(idle) = min_idle_time_ms {
            cmd.arg("IDLE").arg(idle);
        }
        cmd.arg(start).arg(end).arg(count);
        if let Some(c) = consumer {
            cmd.arg(c);
        }
        parse_xpending_range(self.execute_command(cmd, None).await?)
    }

    /// Get general information about a stream (`XINFO STREAM`). Returns the raw
    /// structured reply as a list of `(field, value)` pairs.
    async fn xinfo_stream<K: ToRedisArgs + Send>(
        &self,
        key: K,
    ) -> Result<Vec<(Bytes, redis::Value)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("STREAM").arg(key);
        parse_field_value_map(self.execute_command(cmd, None).await?)
    }

    /// Get the full state of a stream including entries and PEL
    /// (`XINFO STREAM ... FULL`). Returns the raw structured reply as
    /// `(field, value)` pairs. Pass `count` to limit returned entries/PEL.
    async fn xinfo_stream_full<K: ToRedisArgs + Send>(
        &self,
        key: K,
        count: Option<i64>,
    ) -> Result<Vec<(Bytes, redis::Value)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("STREAM").arg(key).arg("FULL");
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        parse_field_value_map(self.execute_command(cmd, None).await?)
    }

    /// Get information about the consumer groups of a stream (`XINFO GROUPS`).
    /// Returns one `(field, value)` map per group.
    async fn xinfo_groups<K: ToRedisArgs + Send>(
        &self,
        key: K,
    ) -> Result<Vec<Vec<(Bytes, redis::Value)>>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("GROUPS").arg(key);
        parse_list_of_maps(self.execute_command(cmd, None).await?)
    }

    /// Get information about the consumers in a group (`XINFO CONSUMERS`).
    async fn xinfo_consumers<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
    ) -> Result<Vec<Vec<(Bytes, redis::Value)>>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("CONSUMERS").arg(key).arg(group);
        parse_list_of_maps(self.execute_command(cmd, None).await?)
    }

    /// Set the last-delivered ID of a stream (`XSETID`).
    async fn xsetid<K: ToRedisArgs + Send>(
        &self,
        key: K,
        last_id: &str,
        entries_added: Option<i64>,
        max_deleted_id: Option<&str>,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XSETID").arg(key).arg(last_id);
        if let Some(e) = entries_added {
            cmd.arg("ENTRIESADDED").arg(e);
        }
        if let Some(m) = max_deleted_id {
            cmd.arg("MAXDELETEDID").arg(m);
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Create a consumer group with options (`XGROUP CREATE` with `MKSTREAM` /
    /// `ENTRIESREAD`).
    async fn xgroup_create_options<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        id: &str,
        options: &StreamGroupCreateOptions,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("CREATE").arg(key).arg(group).arg(id);
        options.add_to(&mut cmd);
        value::to_unit(self.execute_command(cmd, None).await?)
    }

    /// Create a new consumer in a group (`XGROUP CREATECONSUMER`). Returns
    /// whether the consumer was created.
    async fn xgroup_create_consumer<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
    ) -> Result<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP")
            .arg("CREATECONSUMER")
            .arg(key)
            .arg(group)
            .arg(consumer);
        value::to_bool(self.execute_command(cmd, None).await?)
    }

    /// Delete a consumer from a group (`XGROUP DELCONSUMER`). Returns the number
    /// of pending messages the consumer had.
    async fn xgroup_del_consumer<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP")
            .arg("DELCONSUMER")
            .arg(key)
            .arg(group)
            .arg(consumer);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Set the last-delivered ID for a consumer group (`XGROUP SETID`).
    async fn xgroup_set_id<K: ToRedisArgs + Send>(
        &self,
        key: K,
        group: &str,
        id: &str,
        entries_read: Option<i64>,
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("SETID").arg(key).arg(group).arg(id);
        if let Some(e) = entries_read {
            cmd.arg("ENTRIESREAD").arg(e);
        }
        value::to_unit(self.execute_command(cmd, None).await?)
    }
}

/// Parse an `XRANGE`/`XREVRANGE` reply into `(id, [(field, value), ...])` entries,
/// handling both RESP2 (array of `[id, [f, v, ...]]`) and RESP3 (map of
/// `id -> [[f, v], ...]`).
fn parse_entries(v: redis::Value) -> Result<Vec<StreamEntry>> {
    let pairs: Vec<(redis::Value, redis::Value)> = match v {
        redis::Value::Nil => return Ok(Vec::new()),
        redis::Value::Map(pairs) => pairs,
        redis::Value::Array(items) => {
            // RESP2: each item is [id, fields]. Normalize to (id, fields) pairs.
            let mut out = Vec::with_capacity(items.len());
            for entry in items {
                if let redis::Value::Array(mut parts) = entry
                    && parts.len() == 2
                {
                    let fields = parts.pop().unwrap();
                    let id = parts.pop().unwrap();
                    out.push((id, fields));
                }
            }
            out
        }
        other => {
            return Err(crate::error::GlideError::Request(format!(
                "unexpected stream reply: {other:?}"
            )));
        }
    };

    let mut out = Vec::with_capacity(pairs.len());
    for (id_val, fields_val) in pairs {
        let id = value::to_string(id_val)?;
        let fv = parse_fields(fields_val)?;
        out.push((id, fv));
    }
    Ok(out)
}

/// Parse a field/value collection that may be flat (`[f, v, f, v]`) or nested
/// pairs (`[[f, v], [f, v]]`).
fn parse_fields(v: redis::Value) -> Result<Vec<(Bytes, Bytes)>> {
    let items = match v {
        redis::Value::Array(items) => items,
        redis::Value::Nil => return Ok(Vec::new()),
        other => return Ok(vec![(value::to_bytes(other)?, Bytes::new())]),
    };
    // Nested pairs form.
    if items
        .iter()
        .all(|it| matches!(it, redis::Value::Array(inner) if inner.len() == 2))
    {
        let mut out = Vec::with_capacity(items.len());
        for it in items {
            if let redis::Value::Array(mut pair) = it {
                let val = value::to_bytes(pair.pop().unwrap())?;
                let field = value::to_bytes(pair.pop().unwrap())?;
                out.push((field, val));
            }
        }
        return Ok(out);
    }
    // Flat form.
    let mut out = Vec::with_capacity(items.len() / 2);
    let mut iter = items.into_iter();
    while let (Some(f), Some(val)) = (iter.next(), iter.next()) {
        out.push((value::to_bytes(f)?, value::to_bytes(val)?));
    }
    Ok(out)
}

impl<T: CommandExecutor + ?Sized> StreamCommands for T {}

/// Collect an array reply into a `Vec<String>` (used by `JUSTID` variants).
fn collect_strings(v: redis::Value) -> Result<Vec<String>> {
    match v {
        redis::Value::Nil => Ok(Vec::new()),
        redis::Value::Array(items) => items.into_iter().map(value::to_string).collect(),
        other => Ok(vec![value::to_string(other)?]),
    }
}

/// Parse an `XREAD`/`XREADGROUP` reply (map or array of `[key, entries]`) into
/// `(stream_key, entries)` pairs.
fn parse_stream_read(v: redis::Value) -> Result<Vec<(Bytes, Vec<StreamEntry>)>> {
    let pairs: Vec<(redis::Value, redis::Value)> = match v {
        redis::Value::Nil => return Ok(Vec::new()),
        redis::Value::Map(pairs) => pairs,
        redis::Value::Array(items) => {
            let mut out = Vec::with_capacity(items.len());
            for entry in items {
                if let redis::Value::Array(mut parts) = entry
                    && parts.len() == 2
                {
                    let entries = parts.pop().unwrap();
                    let key = parts.pop().unwrap();
                    out.push((key, entries));
                }
            }
            out
        }
        other => {
            return Err(crate::error::GlideError::Request(format!(
                "unexpected XREAD reply: {other:?}"
            )));
        }
    };
    let mut out = Vec::with_capacity(pairs.len());
    for (key_val, entries_val) in pairs {
        let key = value::to_bytes(key_val)?;
        let entries = parse_entries(entries_val)?;
        out.push((key, entries));
    }
    Ok(out)
}

/// Parse an `XAUTOCLAIM` reply `[cursor, entries, deleted]`.
fn parse_autoclaim(v: redis::Value) -> Result<(String, Vec<StreamEntry>, Vec<String>)> {
    match v {
        redis::Value::Array(mut items) if items.len() == 2 || items.len() == 3 => {
            let deleted = if items.len() == 3 {
                collect_strings(items.pop().unwrap())?
            } else {
                Vec::new()
            };
            let entries = parse_entries(items.pop().unwrap())?;
            let cursor = value::to_string(items.pop().unwrap())?;
            Ok((cursor, entries, deleted))
        }
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected XAUTOCLAIM reply: {other:?}"
        ))),
    }
}

/// Parse an `XAUTOCLAIM ... JUSTID` reply `[cursor, ids, deleted]`.
fn parse_autoclaim_justid(v: redis::Value) -> Result<(String, Vec<String>, Vec<String>)> {
    match v {
        redis::Value::Array(mut items) if items.len() == 2 || items.len() == 3 => {
            let deleted = if items.len() == 3 {
                collect_strings(items.pop().unwrap())?
            } else {
                Vec::new()
            };
            let ids = collect_strings(items.pop().unwrap())?;
            let cursor = value::to_string(items.pop().unwrap())?;
            Ok((cursor, ids, deleted))
        }
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected XAUTOCLAIM JUSTID reply: {other:?}"
        ))),
    }
}

/// Parse the summary form of `XPENDING`: `[count, min, max, [[consumer, count], ...]]`.
fn parse_xpending_summary(v: redis::Value) -> Result<XPendingSummary> {
    let mut items = match v {
        redis::Value::Array(items) if items.len() == 4 => items,
        redis::Value::Nil => return Ok(XPendingSummary::default()),
        other => {
            return Err(crate::error::GlideError::Request(format!(
                "unexpected XPENDING summary reply: {other:?}"
            )));
        }
    };
    let consumers_val = items.pop().unwrap();
    let max_val = items.pop().unwrap();
    let min_val = items.pop().unwrap();
    let count = value::to_i64(items.pop().unwrap())?;
    let consumers = match consumers_val {
        redis::Value::Nil => Vec::new(),
        redis::Value::Array(list) => {
            let mut out = Vec::with_capacity(list.len());
            for it in list {
                if let redis::Value::Array(mut pair) = it
                    && pair.len() == 2
                {
                    let cnt = value::to_i64(pair.pop().unwrap())?;
                    let name = value::to_bytes(pair.pop().unwrap())?;
                    out.push((name, cnt));
                }
            }
            out
        }
        _ => Vec::new(),
    };
    Ok(XPendingSummary {
        count,
        min_id: value::to_opt_bytes(min_val)?,
        max_id: value::to_opt_bytes(max_val)?,
        consumers,
    })
}

/// Parse the extended (range) form of `XPENDING`: array of
/// `[id, consumer, idle, delivery_count]`.
fn parse_xpending_range(v: redis::Value) -> Result<Vec<XPendingEntry>> {
    let items = match v {
        redis::Value::Nil => return Ok(Vec::new()),
        redis::Value::Array(items) => items,
        other => {
            return Err(crate::error::GlideError::Request(format!(
                "unexpected XPENDING range reply: {other:?}"
            )));
        }
    };
    let mut out = Vec::with_capacity(items.len());
    for it in items {
        if let redis::Value::Array(mut parts) = it
            && parts.len() == 4
        {
            let delivery_count = value::to_i64(parts.pop().unwrap())?;
            let idle_ms = value::to_i64(parts.pop().unwrap())?;
            let consumer = value::to_bytes(parts.pop().unwrap())?;
            let id = value::to_bytes(parts.pop().unwrap())?;
            out.push(XPendingEntry {
                id,
                consumer,
                idle_ms,
                delivery_count,
            });
        }
    }
    Ok(out)
}

/// Parse a structured reply (RESP3 map or RESP2 flat array of alternating
/// field/value) into `(field, value)` pairs.
fn parse_field_value_map(v: redis::Value) -> Result<Vec<(Bytes, redis::Value)>> {
    match v {
        redis::Value::Nil => Ok(Vec::new()),
        redis::Value::Map(pairs) => pairs
            .into_iter()
            .map(|(k, val)| Ok((value::to_bytes(k)?, val)))
            .collect(),
        redis::Value::Array(items) => {
            let mut out = Vec::with_capacity(items.len() / 2);
            let mut iter = items.into_iter();
            while let (Some(k), Some(val)) = (iter.next(), iter.next()) {
                out.push((value::to_bytes(k)?, val));
            }
            Ok(out)
        }
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected XINFO reply: {other:?}"
        ))),
    }
}

/// Parse a list of structured maps (e.g. `XINFO GROUPS`/`CONSUMERS`).
fn parse_list_of_maps(v: redis::Value) -> Result<Vec<Vec<(Bytes, redis::Value)>>> {
    match v {
        redis::Value::Nil => Ok(Vec::new()),
        redis::Value::Array(items) => items.into_iter().map(parse_field_value_map).collect(),
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected XINFO list reply: {other:?}"
        ))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn args_of(cmd: &Cmd) -> Vec<String> {
        cmd.args_iter()
            .filter_map(|a| match a {
                redis::Arg::Simple(bytes) => Some(String::from_utf8_lossy(bytes).into_owned()),
                redis::Arg::Cursor => None,
            })
            .collect()
    }

    #[test]
    fn trim_options_maxlen_args() {
        let mut cmd = Cmd::new();
        StreamTrimOptions::max_len(true, 100, None).add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["MAXLEN", "=", "100"]);

        let mut cmd = Cmd::new();
        StreamTrimOptions::max_len(false, 100, Some(10)).add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["MAXLEN", "~", "100", "LIMIT", "10"]);
    }

    #[test]
    fn trim_options_minid_args() {
        let mut cmd = Cmd::new();
        StreamTrimOptions::min_id(false, "1526985054069-0", None).add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["MINID", "~", "1526985054069-0"]);
    }

    #[test]
    fn add_options_args() {
        let opts = StreamAddOptions {
            make_stream: false,
            trim: Some(StreamTrimOptions::max_len(true, 5, None)),
        };
        let mut cmd = Cmd::new();
        opts.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["NOMKSTREAM", "MAXLEN", "=", "5"]);
    }

    #[test]
    fn read_group_options_args() {
        let opts = StreamReadGroupOptions {
            block_ms: Some(500),
            count: Some(10),
            no_ack: true,
        };
        let mut cmd = Cmd::new();
        opts.add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["BLOCK", "500", "COUNT", "10", "NOACK"]);
    }

    #[test]
    fn claim_options_args() {
        let opts = StreamClaimOptions {
            idle: Some(100),
            idle_unix_time: None,
            retry_count: Some(3),
            is_force: true,
        };
        let mut cmd = Cmd::new();
        opts.add_to(&mut cmd);
        assert_eq!(
            args_of(&cmd),
            vec!["IDLE", "100", "RETRYCOUNT", "3", "FORCE"]
        );
    }
}
