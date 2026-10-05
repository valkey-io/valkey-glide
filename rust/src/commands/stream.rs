// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Stream commands. Mirrors Python's stream command surface.
#![allow(clippy::too_many_arguments, clippy::type_complexity)]

use crate::GlideError;
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
use std::collections::HashMap;

/// A single stream entry: its ID and its field/value pairs.
pub type StreamEntry = (String, Vec<(Bytes, Bytes)>);

/// A stream trimming mode.
///
/// Mirrors redis-rs's `streams::StreamTrimmingMode` type.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[non_exhaustive]
pub enum StreamTrimmingMode {
    /// Trim exactly (`=`).
    Exact,
    /// Trim approximately (`~`).
    Approx,
}

impl ToValkeyArgs for StreamTrimmingMode {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self {
            Self::Exact => b"=",
            Self::Approx => b"~",
        });
    }
}

/// A stream trim strategy.
///
/// Mirrors redis-rs's `streams::StreamTrimStrategy` type.
#[derive(Debug, Clone, PartialEq, Eq)]
#[non_exhaustive]
pub enum StreamTrimStrategy {
    /// Evict entries while the stream is longer than the threshold (`MAXLEN`).
    MaxLen(StreamTrimmingMode, usize, Option<usize>),
    /// Evict entries with IDs lower than the threshold (`MINID`).
    MinId(StreamTrimmingMode, String, Option<usize>),
}

impl StreamTrimStrategy {
    /// Trim to at most `max_entries` entries (`MAXLEN`).
    pub fn maxlen(trim: StreamTrimmingMode, max_entries: usize) -> Self {
        Self::MaxLen(trim, max_entries, None)
    }

    /// Trim entries with IDs lower than `stream_id` (`MINID`).
    pub fn minid(trim: StreamTrimmingMode, stream_id: impl Into<String>) -> Self {
        Self::MinId(trim, stream_id.into(), None)
    }

    /// Limit the number of entries evicted in a single operation (`LIMIT`).
    pub fn limit(self, limit: usize) -> Self {
        match self {
            Self::MaxLen(mode, threshold, _) => Self::MaxLen(mode, threshold, Some(limit)),
            Self::MinId(mode, threshold, _) => Self::MinId(mode, threshold, Some(limit)),
        }
    }
}

impl ToValkeyArgs for StreamTrimStrategy {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        let limit = match self {
            Self::MaxLen(mode, threshold, limit) => {
                out.write_arg(b"MAXLEN");
                mode.write_valkey_args(out);
                out.write_arg_fmt(threshold);
                limit
            }
            Self::MinId(mode, threshold, limit) => {
                out.write_arg(b"MINID");
                mode.write_valkey_args(out);
                out.write_arg(threshold.as_bytes());
                limit
            }
        };
        if let Some(limit) = limit {
            out.write_arg(b"LIMIT");
            out.write_arg_fmt(limit);
        }
    }
}

/// Options for `XADD`.
///
/// Mirrors redis-rs's `streams::StreamAddOptions` type.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct StreamAddOptions {
    nomkstream: bool,
    trim: Option<StreamTrimStrategy>,
}

impl StreamAddOptions {
    /// Do not create the stream if it does not exist (`NOMKSTREAM`).
    pub fn nomkstream(mut self) -> Self {
        self.nomkstream = true;
        self
    }

    /// Trim the stream as part of the add.
    pub fn trim(mut self, trim: StreamTrimStrategy) -> Self {
        self.trim = Some(trim);
        self
    }
}

impl ToValkeyArgs for StreamAddOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if self.nomkstream {
            out.write_arg(b"NOMKSTREAM");
        }
        self.trim.write_valkey_args(out);
    }
}

/// Options for `XREAD` (`BLOCK`/`COUNT`).
///
/// Mirrors redis-rs's `streams::StreamReadOptions` type, but does not support `group` or `noack`.
/// Use [`StreamCommands::xreadgroup`] with [`StreamReadGroupOptions`] instead.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct StreamReadOptions {
    block: Option<usize>,
    count: Option<usize>,
}

impl StreamReadOptions {
    /// Block for up to `ms` milliseconds waiting for entries (`BLOCK`).
    pub fn block(mut self, ms: usize) -> Self {
        self.block = Some(ms);
        self
    }

    /// Return at most `n` entries per stream (`COUNT`).
    pub fn count(mut self, n: usize) -> Self {
        self.count = Some(n);
        self
    }
}

impl ToValkeyArgs for StreamReadOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(block) = self.block {
            out.write_arg(b"BLOCK");
            out.write_arg_fmt(block);
        }
        if let Some(count) = self.count {
            out.write_arg(b"COUNT");
            out.write_arg_fmt(count);
        }
    }
}

/// Options for `XREADGROUP`.
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
/// Mirrors redis-rs's `streams::StreamClaimOptions` type.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct StreamClaimOptions {
    idle: Option<usize>,
    time: Option<usize>,
    retry: Option<usize>,
    force: bool,
    justid: bool,
    lastid: Option<String>,
}

impl StreamClaimOptions {
    /// Set the idle time (ms) of the claimed messages (`IDLE`).
    pub fn idle(mut self, ms: usize) -> Self {
        self.idle = Some(ms);
        self
    }

    /// Set the idle time to a specific Unix time in ms (`TIME`).
    pub fn time(mut self, ms_time: usize) -> Self {
        self.time = Some(ms_time);
        self
    }

    /// Set the retry counter (`RETRYCOUNT`).
    pub fn retry(mut self, count: usize) -> Self {
        self.retry = Some(count);
        self
    }

    /// Create the PEL entry even if the message is not already pending (`FORCE`).
    pub fn with_force(mut self) -> Self {
        self.force = true;
        self
    }

    /// Return only the claimed IDs (`JUSTID`). The reply type changes with this option.
    pub fn with_justid(mut self) -> Self {
        self.justid = true;
        self
    }

    /// Set the group's last-delivered ID (`LASTID`).
    pub fn with_lastid(mut self, lastid: impl Into<String>) -> Self {
        self.lastid = Some(lastid.into());
        self
    }
}

impl ToValkeyArgs for StreamClaimOptions {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        if let Some(idle) = self.idle {
            out.write_arg(b"IDLE");
            out.write_arg_fmt(idle);
        }
        if let Some(time) = self.time {
            out.write_arg(b"TIME");
            out.write_arg_fmt(time);
        }
        if let Some(retry) = self.retry {
            out.write_arg(b"RETRYCOUNT");
            out.write_arg_fmt(retry);
        }
        if self.force {
            out.write_arg(b"FORCE");
        }
        if self.justid {
            out.write_arg(b"JUSTID");
        }
        if let Some(lastid) = &self.lastid {
            out.write_arg(b"LASTID");
            out.write_arg(lastid.as_bytes());
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

/// A stream entry: its ID and field/value pairs.
///
/// Mirrors redis-rs's `streams::StreamId` type.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct StreamId {
    /// The entry ID.
    pub id: String,
    /// The entry's fields, with their values.
    pub map: HashMap<String, ValkeyValue>,
    /// Milliseconds since the entry was last delivered to a consumer, if reported.
    pub milliseconds_elapsed_from_delivery: Option<usize>,
    /// The number of times the entry was delivered, if reported.
    pub delivered_count: Option<usize>,
}

impl StreamId {
    /// Returns the value of `key` decoded as `T`, or `None` if it is missing or
    /// does not decode.
    pub fn get<T: FromValkeyValue>(&self, key: &str) -> Option<T> {
        self.map
            .get(key)
            .and_then(|value| T::from_owned_valkey_value(value.clone()).ok())
    }

    /// Returns `true` if the entry has the field `key`.
    pub fn contains_key(&self, key: &str) -> bool {
        self.map.contains_key(key)
    }

    /// Returns the number of fields in the entry.
    pub fn len(&self) -> usize {
        self.map.len()
    }

    /// Returns `true` if the entry has no fields.
    pub fn is_empty(&self) -> bool {
        self.map.is_empty()
    }
}

/// A stream key and its entries, as returned by `XREAD`.
///
/// Mirrors redis-rs's `streams::StreamKey` type.
#[derive(Debug, Clone, Default)]
pub struct StreamKey {
    /// The stream key.
    pub key: String,
    /// The stream's entries.
    pub ids: Vec<StreamId>,
}

/// The `XREAD` reply.
///
/// Mirrors redis-rs's `streams::StreamReadReply` type.
#[derive(Debug, Clone, Default)]
pub struct StreamReadReply {
    /// The entries of each stream read.
    pub keys: Vec<StreamKey>,
}

impl FromValkeyValue for StreamReadReply {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        let keys = map_pairs(value, "Unexpected XREAD reply.")?
            .into_iter()
            .map(|(key, entries)| {
                Ok(StreamKey {
                    key: String::from_owned_valkey_value(key)?,
                    ids: parse_stream_ids(entries)?,
                })
            })
            .collect::<ValkeyResult<_>>()?;
        Ok(Self { keys })
    }
}

/// The `XRANGE` / `XREVRANGE` reply.
///
/// Mirrors redis-rs's `streams::StreamRangeReply` type.
#[derive(Debug, Clone, Default)]
pub struct StreamRangeReply {
    /// The entries in the range.
    pub ids: Vec<StreamId>,
}

impl FromValkeyValue for StreamRangeReply {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        Ok(Self {
            ids: parse_stream_ids(value)?,
        })
    }
}

/// The `XCLAIM` reply.
///
/// Mirrors redis-rs's `streams::StreamClaimReply` type.
#[derive(Debug, Clone, Default)]
pub struct StreamClaimReply {
    /// The claimed entries.
    pub ids: Vec<StreamId>,
}

impl FromValkeyValue for StreamClaimReply {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        Ok(Self {
            ids: parse_stream_ids(value)?,
        })
    }
}

/// The summary `XPENDING` reply.
///
/// Mirrors redis-rs's `streams::StreamPendingReply` type.
#[derive(Debug, Clone, Default)]
#[non_exhaustive]
pub enum StreamPendingReply {
    /// No entries are pending.
    #[default]
    Empty,
    /// Some entries are pending.
    Data(StreamPendingData),
}

impl StreamPendingReply {
    /// Returns the number of pending entries.
    pub fn count(&self) -> usize {
        match self {
            Self::Empty => 0,
            Self::Data(data) => data.count,
        }
    }
}

impl FromValkeyValue for StreamPendingReply {
    /// Decodes `[count, start_id, end_id, [[consumer, count], ...]]`.
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        type Summary = (
            usize,
            Option<String>,
            Option<String>,
            Option<Vec<(String, usize)>>,
        );
        let (count, start_id, end_id, consumers) = Summary::from_owned_valkey_value(value)?;
        if count == 0 {
            return Ok(Self::Empty);
        }
        let (Some(start_id), Some(end_id)) = (start_id, end_id) else {
            return Err(GlideError::Request(
                "Non-empty XPENDING reply without start and end IDs.".into(),
            ));
        };
        let consumers = consumers
            .unwrap_or_default()
            .into_iter()
            .map(|(name, pending)| StreamInfoConsumer {
                name,
                pending,
                idle: 0,
            })
            .collect();
        Ok(Self::Data(StreamPendingData {
            count,
            start_id,
            end_id,
            consumers,
        }))
    }
}

/// The details of a non-empty [`StreamPendingReply`].
///
/// Mirrors redis-rs's `streams::StreamPendingData` type.
#[derive(Debug, Clone, Default)]
pub struct StreamPendingData {
    /// The number of pending entries.
    pub count: usize,
    /// The smallest pending entry ID.
    pub start_id: String,
    /// The largest pending entry ID.
    pub end_id: String,
    /// Every consumer with pending entries, and how many it has (`idle` is not reported).
    pub consumers: Vec<StreamInfoConsumer>,
}

/// The `XINFO STREAM` reply.
///
/// Mirrors redis-rs's `streams::StreamInfoStreamReply` type.
#[derive(Debug, Clone, Default)]
pub struct StreamInfoStreamReply {
    /// The last generated ID, which may differ from the last entry's ID.
    pub last_generated_id: String,
    /// The number of radix tree nodes (`radix-tree-nodes`).
    pub radix_tree_keys: usize,
    /// The number of consumer groups.
    pub groups: usize,
    /// The number of entries.
    pub length: usize,
    /// The first entry (default if the stream is empty).
    pub first_entry: StreamId,
    /// The last entry (default if the stream is empty).
    pub last_entry: StreamId,
}

impl FromValkeyValue for StreamInfoStreamReply {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        let mut reply = Self::default();
        for (field, value) in map_pairs(value, "Unexpected XINFO STREAM reply.")? {
            match String::from_owned_valkey_value(field)?.as_str() {
                "last-generated-id" => {
                    reply.last_generated_id = FromValkeyValue::from_owned_valkey_value(value)?
                }
                "radix-tree-nodes" => {
                    reply.radix_tree_keys = FromValkeyValue::from_owned_valkey_value(value)?
                }
                "groups" => reply.groups = FromValkeyValue::from_owned_valkey_value(value)?,
                "length" => reply.length = FromValkeyValue::from_owned_valkey_value(value)?,
                "first-entry" => reply.first_entry = parse_optional_stream_id(value)?,
                "last-entry" => reply.last_entry = parse_optional_stream_id(value)?,
                _ => {}
            }
        }
        Ok(reply)
    }
}

/// The `XINFO CONSUMERS` reply.
///
/// Mirrors redis-rs's `streams::StreamInfoConsumersReply` type.
#[derive(Debug, Clone, Default)]
pub struct StreamInfoConsumersReply {
    /// Every consumer in the group.
    pub consumers: Vec<StreamInfoConsumer>,
}

impl FromValkeyValue for StreamInfoConsumersReply {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        let consumers = list_of_map_pairs(value, "Unexpected XINFO CONSUMERS reply.")?
            .into_iter()
            .map(|fields| {
                let mut consumer = StreamInfoConsumer::default();
                for (field, value) in fields {
                    match String::from_owned_valkey_value(field)?.as_str() {
                        "name" => consumer.name = FromValkeyValue::from_owned_valkey_value(value)?,
                        "pending" => {
                            consumer.pending = FromValkeyValue::from_owned_valkey_value(value)?
                        }
                        "idle" => consumer.idle = FromValkeyValue::from_owned_valkey_value(value)?,
                        _ => {}
                    }
                }
                Ok(consumer)
            })
            .collect::<ValkeyResult<_>>()?;
        Ok(Self { consumers })
    }
}

/// The `XINFO GROUPS` reply.
///
/// Mirrors redis-rs's `streams::StreamInfoGroupsReply` type.
#[derive(Debug, Clone, Default)]
pub struct StreamInfoGroupsReply {
    /// Every consumer group of the stream.
    pub groups: Vec<StreamInfoGroup>,
}

impl FromValkeyValue for StreamInfoGroupsReply {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        let groups = list_of_map_pairs(value, "Unexpected XINFO GROUPS reply.")?
            .into_iter()
            .map(|fields| {
                let mut group = StreamInfoGroup::default();
                for (field, value) in fields {
                    match String::from_owned_valkey_value(field)?.as_str() {
                        "name" => group.name = FromValkeyValue::from_owned_valkey_value(value)?,
                        "consumers" => {
                            group.consumers = FromValkeyValue::from_owned_valkey_value(value)?
                        }
                        "pending" => {
                            group.pending = FromValkeyValue::from_owned_valkey_value(value)?
                        }
                        "last-delivered-id" => {
                            group.last_delivered_id =
                                FromValkeyValue::from_owned_valkey_value(value)?
                        }
                        "entries-read" => {
                            group.entries_read = FromValkeyValue::from_owned_valkey_value(value)?
                        }
                        "lag" => group.lag = FromValkeyValue::from_owned_valkey_value(value)?,
                        _ => {}
                    }
                }
                Ok(group)
            })
            .collect::<ValkeyResult<_>>()?;
        Ok(Self { groups })
    }
}

/// A consumer, as reported by `XINFO CONSUMERS` or `XPENDING`.
///
/// Mirrors redis-rs's `streams::StreamInfoConsumer` type.
#[derive(Debug, Clone, Default)]
pub struct StreamInfoConsumer {
    /// The consumer name.
    pub name: String,
    /// The number of entries pending for the consumer.
    pub pending: usize,
    /// The consumer's idle time in milliseconds.
    pub idle: usize,
}

/// A consumer group, as reported by `XINFO GROUPS`.
///
/// Mirrors redis-rs's `streams::StreamInfoGroup` type.
#[derive(Debug, Clone, Default)]
pub struct StreamInfoGroup {
    /// The group name.
    pub name: String,
    /// The number of consumers in the group.
    pub consumers: usize,
    /// The number of entries delivered to the group but not yet acknowledged.
    pub pending: usize,
    /// The ID of the last entry delivered to the group.
    pub last_delivered_id: String,
    /// The logical read counter of the last entry delivered, if reported.
    pub entries_read: Option<usize>,
    /// The number of entries not yet delivered to the group, if known.
    pub lag: Option<usize>,
}

/// Returns the pairs of a map reply, or none for nil.
fn map_pairs(value: ValkeyValue, error: &str) -> ValkeyResult<Vec<(ValkeyValue, ValkeyValue)>> {
    match value {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Map(pairs) => Ok(pairs),
        other => Err(to_glide_error(other, error)),
    }
}

/// Returns the pairs of each map in an array of maps (e.g. `XINFO GROUPS`).
fn list_of_map_pairs(
    value: ValkeyValue,
    error: &str,
) -> ValkeyResult<Vec<Vec<(ValkeyValue, ValkeyValue)>>> {
    match value {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(|item| map_pairs(item, error))
            .collect(),
        other => Err(to_glide_error(other, error)),
    }
}

/// Decodes stream entries from a map of entry ID to `[[field, value], ...]`.
fn parse_stream_ids(value: ValkeyValue) -> ValkeyResult<Vec<StreamId>> {
    map_pairs(value, "Unexpected stream entries reply.")?
        .into_iter()
        .map(|(id, fields)| {
            let fields = match fields {
                ValkeyValue::Array(fields) => fields
                    .into_iter()
                    .map(|pair| match pair {
                        ValkeyValue::Array(pair) if pair.len() == 2 => {
                            let [field, value]: [ValkeyValue; 2] =
                                pair.try_into().expect("checked length");
                            Ok((field, value))
                        }
                        other => Err(to_glide_error(other, "Unexpected stream entry field.")),
                    })
                    .collect::<ValkeyResult<_>>()?,
                other => return Err(to_glide_error(other, "Unexpected stream entry fields.")),
            };
            stream_id(id, fields)
        })
        .collect()
}

/// Decodes an `XINFO STREAM` entry, `[id, [field, value, ...]]`, or nil as the default entry.
fn parse_optional_stream_id(value: ValkeyValue) -> ValkeyResult<StreamId> {
    match value {
        ValkeyValue::Nil => Ok(StreamId::default()),
        ValkeyValue::Array(items) if items.len() == 2 => {
            let [id, fields]: [ValkeyValue; 2] = items.try_into().expect("checked length");
            let fields = match fields {
                ValkeyValue::Array(fields) if fields.len() % 2 == 0 => fields,
                other => return Err(to_glide_error(other, "Unexpected stream entry fields.")),
            };
            let mut fields = fields.into_iter();
            let mut pairs = Vec::with_capacity(fields.len() / 2);
            while let (Some(field), Some(value)) = (fields.next(), fields.next()) {
                pairs.push((field, value));
            }
            stream_id(id, pairs)
        }
        other => Err(to_glide_error(other, "Unexpected stream entry reply.")),
    }
}

/// Builds a stream entry from its ID and field/value pairs.
fn stream_id(id: ValkeyValue, fields: Vec<(ValkeyValue, ValkeyValue)>) -> ValkeyResult<StreamId> {
    let map = fields
        .into_iter()
        .map(|(field, value)| Ok((String::from_owned_valkey_value(field)?, value)))
        .collect::<ValkeyResult<_>>()?;
    Ok(StreamId {
        id: String::from_owned_valkey_value(id)?,
        map,
        milliseconds_elapsed_from_delivery: None,
        delivered_count: None,
    })
}

/// Stream commands beyond the command table (trimming, consumer groups, claiming, ...).
#[async_trait]
pub trait StreamCommands: CommandExecutor {
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

impl<T: CommandExecutor + ?Sized> StreamCommands for T {}

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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::assert_args;
    use crate::test_utils::assert_args_empty;

    #[test]
    fn trim_strategy_args() {
        assert_args(
            StreamTrimStrategy::maxlen(StreamTrimmingMode::Exact, 100),
            &["MAXLEN", "=", "100"],
        );
        assert_args(
            StreamTrimStrategy::maxlen(StreamTrimmingMode::Approx, 100).limit(10),
            &["MAXLEN", "~", "100", "LIMIT", "10"],
        );
        assert_args(
            StreamTrimStrategy::minid(StreamTrimmingMode::Approx, "1526985054069-0"),
            &["MINID", "~", "1526985054069-0"],
        );
    }

    #[test]
    fn add_options_args() {
        assert_args_empty(StreamAddOptions::default());
        assert_args(
            StreamAddOptions::default()
                .nomkstream()
                .trim(StreamTrimStrategy::maxlen(StreamTrimmingMode::Exact, 5)),
            &["NOMKSTREAM", "MAXLEN", "=", "5"],
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
            StreamClaimOptions::default()
                .idle(100)
                .retry(3)
                .with_force()
                .with_justid()
                .with_lastid("5-0"),
            &[
                "IDLE",
                "100",
                "RETRYCOUNT",
                "3",
                "FORCE",
                "JUSTID",
                "LASTID",
                "5-0",
            ],
        );
        assert_args(
            StreamClaimOptions::default().time(1_700_000_000_000),
            &["TIME", "1700000000000"],
        );
    }

    #[test]
    fn read_options_args() {
        assert_args_empty(StreamReadOptions::default());
        assert_args(
            StreamReadOptions::default().count(10).block(1000),
            &["BLOCK", "1000", "COUNT", "10"],
        );
    }

    fn bulk(s: &str) -> ValkeyValue {
        ValkeyValue::BulkString(Bytes::from(s.to_string()))
    }

    fn array(items: Vec<ValkeyValue>) -> ValkeyValue {
        ValkeyValue::Array(items)
    }

    fn entry(id: &str, field: &str, value: &str) -> StreamId {
        StreamId {
            id: id.to_string(),
            map: HashMap::from([(field.to_string(), bulk(value))]),
            ..Default::default()
        }
    }

    #[test]
    fn range_reply_decoding() {
        // glide-core: a map of entry ID to `[[field, value], ...]`.
        let range = ValkeyValue::Map(vec![
            (bulk("1-0"), array(vec![array(vec![bulk("f"), bulk("a")])])),
            (bulk("2-0"), array(vec![array(vec![bulk("f"), bulk("b")])])),
        ]);
        let reply = StreamRangeReply::from_owned_valkey_value(range).unwrap();
        assert_eq!(
            reply.ids,
            vec![entry("1-0", "f", "a"), entry("2-0", "f", "b")]
        );
        assert!(
            StreamRangeReply::from_owned_valkey_value(ValkeyValue::Nil)
                .unwrap()
                .ids
                .is_empty()
        );

        // The raw RESP2 shape is not accepted: glide-core always converts it.
        let raw = array(vec![array(vec![
            bulk("1-0"),
            array(vec![bulk("f"), bulk("a")]),
        ])]);
        assert!(StreamRangeReply::from_owned_valkey_value(raw).is_err());

        // A field that is not a `[field, value]` pair.
        let unpaired = ValkeyValue::Map(vec![(bulk("1-0"), array(vec![bulk("f")]))]);
        assert!(StreamRangeReply::from_owned_valkey_value(unpaired).is_err());

        let claim = StreamClaimReply::from_owned_valkey_value(ValkeyValue::Map(vec![(
            bulk("1-0"),
            array(vec![array(vec![bulk("f"), bulk("a")])]),
        )]))
        .unwrap();
        assert_eq!(claim.ids, vec![entry("1-0", "f", "a")]);

        let id = entry("1-0", "f", "7");
        assert_eq!(id.get::<i64>("f"), Some(7));
        assert_eq!(id.get::<i64>("missing"), None);
        assert!(id.contains_key("f"));
        assert_eq!((id.len(), id.is_empty()), (1, false));
    }

    #[test]
    fn read_reply_decoding() {
        // glide-core: a map of stream key to a map of entry ID to `[[field, value], ...]`.
        let entries = ValkeyValue::Map(vec![(
            bulk("1-0"),
            array(vec![array(vec![bulk("f"), bulk("a")])]),
        )]);
        let reply =
            StreamReadReply::from_owned_valkey_value(ValkeyValue::Map(vec![(bulk("s"), entries)]))
                .unwrap();
        assert_eq!(reply.keys.len(), 1);
        assert_eq!(reply.keys[0].key, "s");
        assert_eq!(reply.keys[0].ids, vec![entry("1-0", "f", "a")]);

        assert!(
            Option::<StreamReadReply>::from_owned_valkey_value(ValkeyValue::Nil)
                .unwrap()
                .is_none()
        );
    }

    #[test]
    fn pending_reply_decoding() {
        let empty = array(vec![
            ValkeyValue::Int(0),
            ValkeyValue::Nil,
            ValkeyValue::Nil,
            ValkeyValue::Nil,
        ]);
        let reply = StreamPendingReply::from_owned_valkey_value(empty).unwrap();
        assert!(matches!(reply, StreamPendingReply::Empty));
        assert_eq!(reply.count(), 0);

        let data = array(vec![
            ValkeyValue::Int(3),
            bulk("1-0"),
            bulk("3-0"),
            array(vec![array(vec![bulk("c1"), bulk("3")])]),
        ]);
        let reply = StreamPendingReply::from_owned_valkey_value(data).unwrap();
        assert_eq!(reply.count(), 3);
        let StreamPendingReply::Data(data) = reply else {
            panic!("expected pending data");
        };
        assert_eq!(
            (data.start_id.as_str(), data.end_id.as_str()),
            ("1-0", "3-0")
        );
        assert_eq!(data.consumers.len(), 1);
        assert_eq!(
            (data.consumers[0].name.as_str(), data.consumers[0].pending),
            ("c1", 3)
        );

        let missing_ids = array(vec![
            ValkeyValue::Int(1),
            ValkeyValue::Nil,
            ValkeyValue::Nil,
            ValkeyValue::Nil,
        ]);
        assert!(StreamPendingReply::from_owned_valkey_value(missing_ids).is_err());
    }

    #[test]
    fn info_stream_reply_decoding() {
        // glide-core: a map, whose entries keep the flat `[id, [field, value, ...]]` form.
        let info = ValkeyValue::Map(vec![
            (bulk("length"), ValkeyValue::Int(2)),
            (bulk("radix-tree-nodes"), ValkeyValue::Int(1)),
            (bulk("groups"), ValkeyValue::Int(1)),
            (bulk("last-generated-id"), bulk("2-0")),
            (
                bulk("first-entry"),
                array(vec![bulk("1-0"), array(vec![bulk("f"), bulk("a")])]),
            ),
            (bulk("last-entry"), ValkeyValue::Nil),
            (bulk("entries-added"), ValkeyValue::Int(2)),
        ]);
        let reply = StreamInfoStreamReply::from_owned_valkey_value(info).unwrap();
        assert_eq!(
            (reply.length, reply.radix_tree_keys, reply.groups),
            (2, 1, 1)
        );
        assert_eq!(reply.last_generated_id, "2-0");
        assert_eq!(reply.first_entry, entry("1-0", "f", "a"));
        assert_eq!(reply.last_entry, StreamId::default());

        // An entry with an odd number of field/value elements.
        let odd = ValkeyValue::Map(vec![(
            bulk("first-entry"),
            array(vec![
                bulk("1-0"),
                array(vec![bulk("f"), bulk("a"), bulk("g")]),
            ]),
        )]);
        assert!(StreamInfoStreamReply::from_owned_valkey_value(odd).is_err());
    }

    #[test]
    fn info_groups_and_consumers_reply_decoding() {
        // glide-core: an array of maps.
        let groups = array(vec![ValkeyValue::Map(vec![
            (bulk("name"), bulk("g")),
            (bulk("consumers"), ValkeyValue::Int(2)),
            (bulk("pending"), ValkeyValue::Int(3)),
            (bulk("last-delivered-id"), bulk("3-0")),
            (bulk("entries-read"), ValkeyValue::Int(3)),
            (bulk("lag"), ValkeyValue::Nil),
        ])]);
        let reply = StreamInfoGroupsReply::from_owned_valkey_value(groups).unwrap();
        let group = &reply.groups[0];
        assert_eq!(
            (group.name.as_str(), group.consumers, group.pending),
            ("g", 2, 3)
        );
        assert_eq!(group.last_delivered_id, "3-0");
        assert_eq!((group.entries_read, group.lag), (Some(3), None));

        let consumers = array(vec![ValkeyValue::Map(vec![
            (bulk("name"), bulk("c1")),
            (bulk("pending"), ValkeyValue::Int(1)),
            (bulk("idle"), ValkeyValue::Int(42)),
            (bulk("inactive"), ValkeyValue::Int(42)),
        ])]);
        let reply = StreamInfoConsumersReply::from_owned_valkey_value(consumers).unwrap();
        let consumer = &reply.consumers[0];
        assert_eq!(
            (consumer.name.as_str(), consumer.pending, consumer.idle),
            ("c1", 1, 42)
        );
    }
}
