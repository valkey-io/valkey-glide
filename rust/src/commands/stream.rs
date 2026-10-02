// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Stream commands. Mirrors Python's stream command surface.
#![allow(clippy::too_many_arguments, clippy::type_complexity)]

use crate::ValkeyResult;
use crate::cmd::Cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::value::to_glide_error;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyWrite;
use async_trait::async_trait;
use bytes::Bytes;

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
}

impl ToValkeyArgs for StreamTrimOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self.strategy {
            StreamTrimStrategy::MaxLen => b"MAXLEN".as_slice(),
            StreamTrimStrategy::MinId => b"MINID".as_slice(),
        });
        out.write_arg(if self.exact { b"=" } else { b"~" });
        out.write_arg(self.threshold.as_bytes());
        if let Some(limit) = self.limit {
            out.write_arg(b"LIMIT");
            out.write_arg_fmt(limit);
        }
    }
}

/// Options for `XADD`.
///
/// Mirrors Python `StreamAddOptions`.
#[derive(Debug, Clone)]
pub struct StreamAddOptions {
    /// If `false`, do not create the stream if it does not exist (`NOMKSTREAM`).
    pub make_stream: bool,
    /// Optional trim to apply as part of the add.
    pub trim: Option<StreamTrimOptions>,
}

impl Default for StreamAddOptions {
    fn default() -> Self {
        Self {
            make_stream: true,
            trim: None,
        }
    }
}

impl ToValkeyArgs for StreamAddOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if !self.make_stream {
            out.write_arg(b"NOMKSTREAM");
        }
        self.trim.write_valkey_args(out);
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

impl ToValkeyArgs for StreamReadOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(block_ms) = self.block_ms {
            out.write_arg(b"BLOCK");
            out.write_arg_fmt(block_ms);
        }
        if let Some(count) = self.count {
            out.write_arg(b"COUNT");
            out.write_arg_fmt(count);
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

impl ToValkeyArgs for StreamReadGroupOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(block_ms) = self.block_ms {
            out.write_arg(b"BLOCK");
            out.write_arg_fmt(block_ms);
        }
        if let Some(count) = self.count {
            out.write_arg(b"COUNT");
            out.write_arg_fmt(count);
        }
        if self.no_ack {
            out.write_arg(b"NOACK");
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

impl ToValkeyArgs for StreamGroupCreateOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if self.make_stream {
            out.write_arg(b"MKSTREAM");
        }
        if let Some(entries_read) = self.entries_read {
            out.write_arg(b"ENTRIESREAD");
            out.write_arg_fmt(entries_read);
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

impl ToValkeyArgs for StreamClaimOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(idle) = self.idle {
            out.write_arg(b"IDLE");
            out.write_arg_fmt(idle);
        }
        if let Some(idle_unix_time) = self.idle_unix_time {
            out.write_arg(b"TIME");
            out.write_arg_fmt(idle_unix_time);
        }
        if let Some(retry_count) = self.retry_count {
            out.write_arg(b"RETRYCOUNT");
            out.write_arg_fmt(retry_count);
        }
        if self.is_force {
            out.write_arg(b"FORCE");
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

impl FromValkeyValue for XPendingSummary {
    /// Decodes `[count, min, max, [[consumer, count], ...]]`, or nil as an empty summary.
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        match value {
            ValkeyValue::Nil => Ok(Self::default()),
            ValkeyValue::Array(items) if items.len() == 4 => {
                let mut items = items.into_iter();
                let mut next = || items.next().expect("checked length");
                Ok(Self {
                    count: i64::from_owned_valkey_value(next())?,
                    min_id: Option::<Bytes>::from_owned_valkey_value(next())?,
                    max_id: Option::<Bytes>::from_owned_valkey_value(next())?,
                    consumers: Vec::<PendingConsumer>::from_owned_valkey_value(next())?,
                })
            }
            other => Err(to_glide_error(other, "Unexpected XPENDING summary reply.")),
        }
    }
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

impl FromValkeyValue for XPendingEntry {
    /// Decodes `[id, consumer, idle_ms, delivery_count]`.
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        match value {
            ValkeyValue::Array(items) if items.len() == 4 => {
                let mut items = items.into_iter();
                let mut next = || items.next().expect("checked length");
                Ok(Self {
                    id: Bytes::from_owned_valkey_value(next())?,
                    consumer: Bytes::from_owned_valkey_value(next())?,
                    idle_ms: i64::from_owned_valkey_value(next())?,
                    delivery_count: i64::from_owned_valkey_value(next())?,
                })
            }
            other => Err(to_glide_error(other, "Unexpected XPENDING entry.")),
        }
    }
}

/// Stream commands (`XADD`, `XLEN`, `XRANGE`, `XREAD`, `XDEL`, groups, ...).
#[async_trait]
pub trait StreamCommands: CommandExecutor {
    /// Append an entry to the stream at `key` (`XADD`). Pass `"*"` for an
    /// auto-generated ID. Returns the generated entry ID.
    async fn xadd<K, F, V>(
        &self,
        key: K,
        id: &str,
        fields: &[(F, V)],
    ) -> ValkeyResult<Option<String>>
    where
        K: ToValkeyArgs + Send + Sync,
        F: ToValkeyArgs + Send + Sync,
        V: ToValkeyArgs + Send + Sync,
    {
        let mut cmd = Cmd::new();
        cmd.arg("XADD").arg(key).arg(id);
        for (f, v) in fields {
            cmd.arg(f).arg(v);
        }
        Option::<String>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the number of entries in the stream (`XLEN`).
    async fn xlen<K: ToValkeyArgs + Send>(&self, key: K) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XLEN").arg(key);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Delete entries by ID (`XDEL`); returns the number deleted.
    async fn xdel<K: ToValkeyArgs + Send>(&self, key: K, ids: &[&str]) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XDEL").arg(key);
        for id in ids {
            cmd.arg(*id);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Trim the stream to (approximately) `maxlen` entries (`XTRIM ... MAXLEN`).
    async fn xtrim_maxlen<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        maxlen: i64,
        approximate: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XTRIM").arg(key).arg("MAXLEN");
        if approximate {
            cmd.arg("~");
        }
        cmd.arg(maxlen);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Read a range of entries (`XRANGE key start end`).
    async fn xrange<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        start: &str,
        end: &str,
    ) -> ValkeyResult<Vec<StreamEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XRANGE").arg(key).arg(start).arg(end);
        parse_entries(self.execute_command(cmd, None).await?)
    }

    /// Read a range of entries in reverse (`XREVRANGE key end start`).
    async fn xrevrange<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        end: &str,
        start: &str,
    ) -> ValkeyResult<Vec<StreamEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XREVRANGE").arg(key).arg(end).arg(start);
        parse_entries(self.execute_command(cmd, None).await?)
    }

    /// Create a consumer group (`XGROUP CREATE`). Set `mkstream` to create the
    /// stream if it does not exist.
    async fn xgroup_create<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        id: &str,
        mkstream: bool,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("CREATE").arg(key).arg(group).arg(id);
        if mkstream {
            cmd.arg("MKSTREAM");
        }
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Destroy a consumer group (`XGROUP DESTROY`). Returns whether it existed.
    async fn xgroup_destroy<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
    ) -> ValkeyResult<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("DESTROY").arg(key).arg(group);
        bool::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Acknowledge processed entries in a consumer group (`XACK`).
    async fn xack<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        ids: &[&str],
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XACK").arg(key).arg(group);
        for id in ids {
            cmd.arg(*id);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
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
    ) -> ValkeyResult<Option<String>>
    where
        K: ToValkeyArgs + Send + Sync,
        F: ToValkeyArgs + Send + Sync,
        V: ToValkeyArgs + Send + Sync,
    {
        let mut cmd = Cmd::new();
        cmd.arg("XADD").arg(key).arg(options);
        cmd.arg(id);
        for (f, v) in fields {
            cmd.arg(f).arg(v);
        }
        Option::<String>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Trim the stream to a minimum ID (`XTRIM ... MINID`). Returns entries removed.
    async fn xtrim_minid<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        minid: &str,
        approximate: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XTRIM").arg(key).arg("MINID");
        if approximate {
            cmd.arg("~");
        }
        cmd.arg(minid);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Read from one or more streams (`XREAD`). `keys_ids` is a list of
    /// `(key, id)` pairs. Returns `(stream_key, entries)` per stream that
    /// produced data.
    async fn xread<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys_ids: &[(K, &str)],
        options: Option<StreamReadOptions>,
    ) -> ValkeyResult<Vec<(Bytes, Vec<StreamEntry>)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XREAD");
        cmd.arg(options);
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
    async fn xreadgroup<K: ToValkeyArgs + Send + Sync>(
        &self,
        group: &str,
        consumer: &str,
        keys_ids: &[(K, &str)],
        options: Option<StreamReadGroupOptions>,
    ) -> ValkeyResult<Vec<(Bytes, Vec<StreamEntry>)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XREADGROUP").arg("GROUP").arg(group).arg(consumer);
        cmd.arg(options);
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
    async fn xclaim<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        ids: &[&str],
        options: Option<StreamClaimOptions>,
    ) -> ValkeyResult<Vec<StreamEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XCLAIM")
            .arg(key)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_time_ms);
        for id in ids {
            cmd.arg(*id);
        }
        cmd.arg(options);
        parse_entries(self.execute_command(cmd, None).await?)
    }

    /// Claim ownership of pending messages, returning only their IDs
    /// (`XCLAIM ... JUSTID`).
    async fn xclaim_justid<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        ids: &[&str],
        options: Option<StreamClaimOptions>,
    ) -> ValkeyResult<Vec<String>> {
        let mut cmd = Cmd::new();
        cmd.arg("XCLAIM")
            .arg(key)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_time_ms);
        for id in ids {
            cmd.arg(*id);
        }
        cmd.arg(options);
        cmd.arg("JUSTID");
        collect_strings(self.execute_command(cmd, None).await?)
    }

    /// Automatically claim pending messages idle for at least `min_idle_time_ms`
    /// (`XAUTOCLAIM`). Returns `(next_cursor, claimed_entries, deleted_ids)`.
    async fn xautoclaim<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        start: &str,
        count: Option<i64>,
    ) -> ValkeyResult<(String, Vec<StreamEntry>, Vec<String>)> {
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
    async fn xautoclaim_justid<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
        min_idle_time_ms: i64,
        start: &str,
        count: Option<i64>,
    ) -> ValkeyResult<(String, Vec<String>, Vec<String>)> {
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
    async fn xpending<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
    ) -> ValkeyResult<XPendingSummary> {
        let mut cmd = Cmd::new();
        cmd.arg("XPENDING").arg(key).arg(group);
        XPendingSummary::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Extended (range) form of `XPENDING`
    /// (`XPENDING key group [IDLE ms] start end count [consumer]`).
    async fn xpending_range<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        start: &str,
        end: &str,
        count: i64,
        min_idle_time_ms: Option<i64>,
        consumer: Option<&str>,
    ) -> ValkeyResult<Vec<XPendingEntry>> {
        let mut cmd = Cmd::new();
        cmd.arg("XPENDING").arg(key).arg(group);
        if let Some(idle) = min_idle_time_ms {
            cmd.arg("IDLE").arg(idle);
        }
        cmd.arg(start).arg(end).arg(count);
        if let Some(c) = consumer {
            cmd.arg(c);
        }
        Vec::<XPendingEntry>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get general information about a stream (`XINFO STREAM`). Returns the raw
    /// structured reply as a list of `(field, value)` pairs.
    async fn xinfo_stream<K: ToValkeyArgs + Send>(
        &self,
        key: K,
    ) -> ValkeyResult<Vec<(Bytes, ValkeyValue)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("STREAM").arg(key);
        parse_field_value_map(self.execute_command(cmd, None).await?)
    }

    /// Get the full state of a stream including entries and PEL
    /// (`XINFO STREAM ... FULL`). Returns the raw structured reply as
    /// `(field, value)` pairs. Pass `count` to limit returned entries/PEL.
    async fn xinfo_stream_full<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        count: Option<i64>,
    ) -> ValkeyResult<Vec<(Bytes, ValkeyValue)>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("STREAM").arg(key).arg("FULL");
        if let Some(c) = count {
            cmd.arg("COUNT").arg(c);
        }
        parse_field_value_map(self.execute_command(cmd, None).await?)
    }

    /// Get information about the consumer groups of a stream (`XINFO GROUPS`).
    /// Returns one `(field, value)` map per group.
    async fn xinfo_groups<K: ToValkeyArgs + Send>(
        &self,
        key: K,
    ) -> ValkeyResult<Vec<Vec<(Bytes, ValkeyValue)>>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("GROUPS").arg(key);
        parse_list_of_maps(self.execute_command(cmd, None).await?)
    }

    /// Get information about the consumers in a group (`XINFO CONSUMERS`).
    async fn xinfo_consumers<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
    ) -> ValkeyResult<Vec<Vec<(Bytes, ValkeyValue)>>> {
        let mut cmd = Cmd::new();
        cmd.arg("XINFO").arg("CONSUMERS").arg(key).arg(group);
        parse_list_of_maps(self.execute_command(cmd, None).await?)
    }

    /// Set the last-delivered ID of a stream (`XSETID`).
    async fn xsetid<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        last_id: &str,
        entries_added: Option<i64>,
        max_deleted_id: Option<&str>,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XSETID").arg(key).arg(last_id);
        if let Some(e) = entries_added {
            cmd.arg("ENTRIESADDED").arg(e);
        }
        if let Some(m) = max_deleted_id {
            cmd.arg("MAXDELETEDID").arg(m);
        }
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Create a consumer group with options (`XGROUP CREATE` with `MKSTREAM` /
    /// `ENTRIESREAD`).
    async fn xgroup_create_options<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        id: &str,
        options: &StreamGroupCreateOptions,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP")
            .arg("CREATE")
            .arg(key)
            .arg(group)
            .arg(id)
            .arg(options);
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Create a new consumer in a group (`XGROUP CREATECONSUMER`). Returns
    /// whether the consumer was created.
    async fn xgroup_create_consumer<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
    ) -> ValkeyResult<bool> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP")
            .arg("CREATECONSUMER")
            .arg(key)
            .arg(group)
            .arg(consumer);
        bool::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Delete a consumer from a group (`XGROUP DELCONSUMER`). Returns the number
    /// of pending messages the consumer had.
    async fn xgroup_del_consumer<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        consumer: &str,
    ) -> ValkeyResult<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP")
            .arg("DELCONSUMER")
            .arg(key)
            .arg(group)
            .arg(consumer);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Set the last-delivered ID for a consumer group (`XGROUP SETID`).
    async fn xgroup_set_id<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        group: &str,
        id: &str,
        entries_read: Option<i64>,
    ) -> ValkeyResult<()> {
        let mut cmd = Cmd::new();
        cmd.arg("XGROUP").arg("SETID").arg(key).arg(group).arg(id);
        if let Some(e) = entries_read {
            cmd.arg("ENTRIESREAD").arg(e);
        }
        <()>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

/// Parse an `XRANGE`/`XREVRANGE` reply into `(id, [(field, value), ...])` entries,
/// handling both RESP2 (array of `[id, [f, v, ...]]`) and RESP3 (map of
/// `id -> [[f, v], ...]`).
fn parse_entries(v: ValkeyValue) -> ValkeyResult<Vec<StreamEntry>> {
    let pairs: Vec<(ValkeyValue, ValkeyValue)> = match v {
        ValkeyValue::Nil => return Ok(Vec::new()),
        ValkeyValue::Map(pairs) => pairs,
        ValkeyValue::Array(items) => {
            // RESP2: each item is [id, fields]. Normalize to (id, fields) pairs.
            let mut out = Vec::with_capacity(items.len());
            for entry in items {
                if let ValkeyValue::Array(mut parts) = entry
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
            return Err(to_glide_error(other, "Unexpected stream reply."));
        }
    };

    let mut out = Vec::with_capacity(pairs.len());
    for (id_val, fields_val) in pairs {
        let id = String::from_owned_valkey_value(id_val)?;
        let fv = parse_fields(fields_val)?;
        out.push((id, fv));
    }
    Ok(out)
}

/// Parse a field/value collection that may be flat (`[f, v, f, v]`) or nested
/// pairs (`[[f, v], [f, v]]`).
fn parse_fields(v: ValkeyValue) -> ValkeyResult<Vec<(Bytes, Bytes)>> {
    let items = match v {
        ValkeyValue::Array(items) => items,
        ValkeyValue::Nil => return Ok(Vec::new()),
        other => return Ok(vec![(Bytes::from_owned_valkey_value(other)?, Bytes::new())]),
    };
    // Nested pairs form.
    if items
        .iter()
        .all(|it| matches!(it, ValkeyValue::Array(inner) if inner.len() == 2))
    {
        let mut out = Vec::with_capacity(items.len());
        for it in items {
            if let ValkeyValue::Array(mut pair) = it {
                let val = Bytes::from_owned_valkey_value(pair.pop().unwrap())?;
                let field = Bytes::from_owned_valkey_value(pair.pop().unwrap())?;
                out.push((field, val));
            }
        }
        return Ok(out);
    }
    // Flat form.
    let mut out = Vec::with_capacity(items.len() / 2);
    let mut iter = items.into_iter();
    while let (Some(f), Some(val)) = (iter.next(), iter.next()) {
        out.push((
            Bytes::from_owned_valkey_value(f)?,
            Bytes::from_owned_valkey_value(val)?,
        ));
    }
    Ok(out)
}

impl<T: CommandExecutor + ?Sized> StreamCommands for T {}

/// Collect an array reply into a `Vec<String>` (used by `JUSTID` variants).
fn collect_strings(v: ValkeyValue) -> ValkeyResult<Vec<String>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(String::from_owned_valkey_value)
            .collect(),
        other => Ok(vec![String::from_owned_valkey_value(other)?]),
    }
}

/// Parse an `XREAD`/`XREADGROUP` reply (map or array of `[key, entries]`) into
/// `(stream_key, entries)` pairs.
fn parse_stream_read(v: ValkeyValue) -> ValkeyResult<Vec<(Bytes, Vec<StreamEntry>)>> {
    let pairs: Vec<(ValkeyValue, ValkeyValue)> = match v {
        ValkeyValue::Nil => return Ok(Vec::new()),
        ValkeyValue::Map(pairs) => pairs,
        ValkeyValue::Array(items) => {
            let mut out = Vec::with_capacity(items.len());
            for entry in items {
                if let ValkeyValue::Array(mut parts) = entry
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
            return Err(to_glide_error(other, "Unexpected XREAD reply."));
        }
    };
    let mut out = Vec::with_capacity(pairs.len());
    for (key_val, entries_val) in pairs {
        let key = Bytes::from_owned_valkey_value(key_val)?;
        let entries = parse_entries(entries_val)?;
        out.push((key, entries));
    }
    Ok(out)
}

/// Parse an `XAUTOCLAIM` reply `[cursor, entries, deleted]`.
fn parse_autoclaim(v: ValkeyValue) -> ValkeyResult<(String, Vec<StreamEntry>, Vec<String>)> {
    match v {
        ValkeyValue::Array(mut items) if items.len() == 2 || items.len() == 3 => {
            let deleted = if items.len() == 3 {
                collect_strings(items.pop().unwrap())?
            } else {
                Vec::new()
            };
            let entries = parse_entries(items.pop().unwrap())?;
            let cursor = String::from_owned_valkey_value(items.pop().unwrap())?;
            Ok((cursor, entries, deleted))
        }
        other => Err(to_glide_error(other, "Unexpected XAUTOCLAIM reply.")),
    }
}

/// Parse an `XAUTOCLAIM ... JUSTID` reply `[cursor, ids, deleted]`.
fn parse_autoclaim_justid(v: ValkeyValue) -> ValkeyResult<(String, Vec<String>, Vec<String>)> {
    match v {
        ValkeyValue::Array(mut items) if items.len() == 2 || items.len() == 3 => {
            let deleted = if items.len() == 3 {
                collect_strings(items.pop().unwrap())?
            } else {
                Vec::new()
            };
            let ids = collect_strings(items.pop().unwrap())?;
            let cursor = String::from_owned_valkey_value(items.pop().unwrap())?;
            Ok((cursor, ids, deleted))
        }
        other => Err(to_glide_error(other, "Unexpected XAUTOCLAIM JUSTID reply.")),
    }
}

/// Parse a structured reply (RESP3 map or RESP2 flat array of alternating
/// field/value) into `(field, value)` pairs.
fn parse_field_value_map(v: ValkeyValue) -> ValkeyResult<Vec<(Bytes, ValkeyValue)>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Map(pairs) => pairs
            .into_iter()
            .map(|(k, val)| Ok((Bytes::from_owned_valkey_value(k)?, val)))
            .collect(),
        ValkeyValue::Array(items) => {
            let mut out = Vec::with_capacity(items.len() / 2);
            let mut iter = items.into_iter();
            while let (Some(k), Some(val)) = (iter.next(), iter.next()) {
                out.push((Bytes::from_owned_valkey_value(k)?, val));
            }
            Ok(out)
        }
        other => Err(to_glide_error(other, "Unexpected XINFO reply.")),
    }
}

/// Parse a list of structured maps (e.g. `XINFO GROUPS`/`CONSUMERS`).
fn parse_list_of_maps(v: ValkeyValue) -> ValkeyResult<Vec<Vec<(Bytes, ValkeyValue)>>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Array(items) => items.into_iter().map(parse_field_value_map).collect(),
        other => Err(to_glide_error(other, "Unexpected XINFO list reply.")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::assert_args;
    use crate::test_utils::assert_args_empty;

    #[test]
    fn trim_options_maxlen_args() {
        assert_args(
            StreamTrimOptions::max_len(true, 100, None),
            &["MAXLEN", "=", "100"],
        );
        assert_args(
            StreamTrimOptions::max_len(false, 100, Some(10)),
            &["MAXLEN", "~", "100", "LIMIT", "10"],
        );
    }

    #[test]
    fn trim_options_minid_args() {
        assert_args(
            StreamTrimOptions::min_id(false, "1526985054069-0", None),
            &["MINID", "~", "1526985054069-0"],
        );
    }

    #[test]
    fn add_options_default() {
        let opts = StreamAddOptions::default();
        assert!(opts.make_stream);
        assert!(opts.trim.is_none());
        assert_args_empty(opts);
    }

    #[test]
    fn add_options_args() {
        assert_args(
            StreamAddOptions {
                make_stream: false,
                trim: Some(StreamTrimOptions::max_len(true, 5, None)),
            },
            &["NOMKSTREAM", "MAXLEN", "=", "5"],
        );
    }

    #[test]
    fn read_options_args() {
        assert_args_empty(StreamReadOptions::default());
        assert_args(
            StreamReadOptions {
                block_ms: Some(500),
                count: Some(10),
            },
            &["BLOCK", "500", "COUNT", "10"],
        );
    }

    #[test]
    fn read_group_options_args() {
        assert_args_empty(StreamReadGroupOptions::default());
        assert_args(
            StreamReadGroupOptions {
                block_ms: Some(500),
                count: Some(10),
                no_ack: true,
            },
            &["BLOCK", "500", "COUNT", "10", "NOACK"],
        );
    }

    #[test]
    fn group_create_options_args() {
        assert_args_empty(StreamGroupCreateOptions::default());
        assert_args(
            StreamGroupCreateOptions {
                make_stream: true,
                entries_read: Some(7),
            },
            &["MKSTREAM", "ENTRIESREAD", "7"],
        );
    }

    #[test]
    fn claim_options_args() {
        assert_args_empty(StreamClaimOptions::default());
        assert_args(
            StreamClaimOptions {
                idle: Some(100),
                idle_unix_time: None,
                retry_count: Some(3),
                is_force: true,
            },
            &["IDLE", "100", "RETRYCOUNT", "3", "FORCE"],
        );
        assert_args(
            StreamClaimOptions {
                idle_unix_time: Some(1_700_000_000_000),
                ..Default::default()
            },
            &["TIME", "1700000000000"],
        );
    }
}
