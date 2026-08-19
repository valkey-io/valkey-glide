// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Pub/Sub subscription management and introspection commands.
//!
//! This covers runtime (un)subscription (`SUBSCRIBE`/`PSUBSCRIBE`/`SSUBSCRIBE`
//! and counterparts), shard publishing (`SPUBLISH`), and the `PUBSUB`
//! introspection subcommands. Plain `PUBLISH` lives in the unified command API
//! ([`crate::AsyncCommands::publish`]). Receiving messages via a subscription
//! is delivered through the client (`get_pubsub_message`), not through these
//! commands.

use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

/// Pub/Sub commands (`SUBSCRIBE`/`UNSUBSCRIBE` at runtime, `SPUBLISH`,
/// `PUBSUB ...`).
#[async_trait]
pub trait PubSubCommands: CommandExecutor {
    /// Subscribe to one or more exact channels at runtime (`SUBSCRIBE`).
    ///
    /// Received messages are delivered via `get_pubsub_message` /
    /// `try_get_pubsub_message`. The client must have the Pub/Sub push channel
    /// enabled — either by configuring connect-time `subscriptions` or by calling
    /// `enable_pubsub()` on the configuration — otherwise messages are not
    /// captured.
    ///
    /// Note: runtime subscriptions are session-scoped and are not automatically
    /// restored after a reconnect (connect-time subscriptions are).
    async fn subscribe<C: ToRedisArgs + Send + Sync>(&self, channels: &[C]) -> Result<()> {
        self.pubsub_subscribe_impl("SUBSCRIBE", channels).await
    }

    /// Unsubscribe from exact channels (`UNSUBSCRIBE`). An empty slice
    /// unsubscribes from all exact channels.
    async fn unsubscribe<C: ToRedisArgs + Send + Sync>(&self, channels: &[C]) -> Result<()> {
        self.pubsub_subscribe_impl("UNSUBSCRIBE", channels).await
    }

    /// Subscribe to one or more glob patterns at runtime (`PSUBSCRIBE`). See
    /// [`Self::subscribe`] for delivery requirements.
    async fn psubscribe<C: ToRedisArgs + Send + Sync>(&self, patterns: &[C]) -> Result<()> {
        self.pubsub_subscribe_impl("PSUBSCRIBE", patterns).await
    }

    /// Unsubscribe from patterns (`PUNSUBSCRIBE`). An empty slice unsubscribes
    /// from all patterns.
    async fn punsubscribe<C: ToRedisArgs + Send + Sync>(&self, patterns: &[C]) -> Result<()> {
        self.pubsub_subscribe_impl("PUNSUBSCRIBE", patterns).await
    }

    /// Subscribe to one or more shard channels at runtime (`SSUBSCRIBE`, cluster
    /// only). See [`Self::subscribe`] for delivery requirements.
    async fn ssubscribe<C: ToRedisArgs + Send + Sync>(&self, channels: &[C]) -> Result<()> {
        self.pubsub_subscribe_impl("SSUBSCRIBE", channels).await
    }

    /// Unsubscribe from shard channels (`SUNSUBSCRIBE`). An empty slice
    /// unsubscribes from all shard channels.
    async fn sunsubscribe<C: ToRedisArgs + Send + Sync>(&self, channels: &[C]) -> Result<()> {
        self.pubsub_subscribe_impl("SUNSUBSCRIBE", channels).await
    }

    #[doc(hidden)]
    async fn pubsub_subscribe_impl<C: ToRedisArgs + Send + Sync>(
        &self,
        keyword: &'static str,
        channels: &[C],
    ) -> Result<()> {
        let mut cmd = Cmd::new();
        cmd.arg(keyword);
        for c in channels {
            cmd.arg(c);
        }
        self.execute_command(cmd, None).await?;
        Ok(())
    }

    // NOTE: no `publish` here — `PUBLISH` lives in the unified command table
    // (`crate::AsyncCommands::publish`). Duplicating it in this trait would
    // make `.publish(...)` ambiguous (E0034) whenever both traits are in
    // scope, breaking `use glide::*`.

    /// Publish `message` to a shard `channel` (`SPUBLISH`, cluster). Returns the
    /// number of clients that received the message.
    async fn spublish<C: ToRedisArgs + Send, M: ToRedisArgs + Send>(
        &self,
        channel: C,
        message: M,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("SPUBLISH").arg(channel).arg(message);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// List active channels, optionally matching `pattern` (`PUBSUB CHANNELS`).
    async fn pubsub_channels(&self, pattern: Option<&[u8]>) -> Result<Vec<Bytes>> {
        self.pubsub_channels_impl("CHANNELS", pattern).await
    }

    /// List active shard channels, optionally matching `pattern`
    /// (`PUBSUB SHARDCHANNELS`).
    async fn pubsub_shardchannels(&self, pattern: Option<&[u8]>) -> Result<Vec<Bytes>> {
        self.pubsub_channels_impl("SHARDCHANNELS", pattern).await
    }

    /// Get the number of subscriptions to patterns (`PUBSUB NUMPAT`).
    async fn pubsub_numpat(&self) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("PUBSUB").arg("NUMPAT");
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Get the number of subscribers per channel (`PUBSUB NUMSUB`).
    async fn pubsub_numsub<C: ToRedisArgs + Send + Sync>(
        &self,
        channels: &[C],
    ) -> Result<Vec<(Bytes, i64)>> {
        self.pubsub_numsub_impl("NUMSUB", channels).await
    }

    /// Get the number of subscribers per shard channel (`PUBSUB SHARDNUMSUB`).
    async fn pubsub_shardnumsub<C: ToRedisArgs + Send + Sync>(
        &self,
        channels: &[C],
    ) -> Result<Vec<(Bytes, i64)>> {
        self.pubsub_numsub_impl("SHARDNUMSUB", channels).await
    }

    #[doc(hidden)]
    async fn pubsub_channels_impl(
        &self,
        sub: &'static str,
        pattern: Option<&[u8]>,
    ) -> Result<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("PUBSUB").arg(sub);
        if let Some(p) = pattern {
            cmd.arg(p);
        }
        match self.execute_command(cmd, None).await? {
            redis::Value::Array(items) => items.into_iter().map(value::to_bytes).collect(),
            redis::Value::Nil => Ok(Vec::new()),
            other => Ok(vec![value::to_bytes(other)?]),
        }
    }

    #[doc(hidden)]
    async fn pubsub_numsub_impl<C: ToRedisArgs + Send + Sync>(
        &self,
        sub: &'static str,
        channels: &[C],
    ) -> Result<Vec<(Bytes, i64)>> {
        let mut cmd = Cmd::new();
        cmd.arg("PUBSUB").arg(sub);
        for c in channels {
            cmd.arg(c);
        }
        parse_numsub(self.execute_command(cmd, None).await?)
    }
}

/// Parse a `PUBSUB NUMSUB` reply (flat `[channel, count, ...]` or RESP3 map).
fn parse_numsub(v: redis::Value) -> Result<Vec<(Bytes, i64)>> {
    match v {
        redis::Value::Nil => Ok(Vec::new()),
        redis::Value::Map(pairs) => pairs
            .into_iter()
            .map(|(c, n)| Ok((value::to_bytes(c)?, value::to_i64(n)?)))
            .collect(),
        redis::Value::Array(items) => {
            let mut out = Vec::with_capacity(items.len() / 2);
            let mut iter = items.into_iter();
            while let (Some(c), Some(n)) = (iter.next(), iter.next()) {
                out.push((value::to_bytes(c)?, value::to_i64(n)?));
            }
            Ok(out)
        }
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected PUBSUB NUMSUB reply: {other:?}"
        ))),
    }
}

impl<T: CommandExecutor + ?Sized> PubSubCommands for T {}
