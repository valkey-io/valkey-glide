// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Sorted-set commands. Mirrors Python's sorted-set command surface.
#![allow(clippy::type_complexity)]

use crate::commands::options::Limit;
use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use bytes::Bytes;
use redis::{Cmd, ToRedisArgs};

/// A score boundary for `ZRANGEBYSCORE`/`ZCOUNT` etc.
///
/// Mirrors Python `ScoreBoundary` + `InfBound`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ScoreBound {
    /// Negative infinity (`-inf`).
    NegativeInfinity,
    /// Positive infinity (`+inf`).
    PositiveInfinity,
    /// Inclusive bound at `value`.
    Inclusive(f64),
    /// Exclusive bound at `value` (`(value`).
    Exclusive(f64),
}

impl ScoreBound {
    fn to_arg(self) -> String {
        match self {
            ScoreBound::NegativeInfinity => "-inf".to_string(),
            ScoreBound::PositiveInfinity => "+inf".to_string(),
            ScoreBound::Inclusive(v) => v.to_string(),
            ScoreBound::Exclusive(v) => format!("({v}"),
        }
    }
}

/// A lexicographical boundary for `ZRANGEBYLEX`/`ZLEXCOUNT`.
///
/// Mirrors Python `LexBoundary` + `InfBound`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LexBound {
    /// Smallest possible value (`-`).
    NegativeInfinity,
    /// Largest possible value (`+`).
    PositiveInfinity,
    /// Inclusive bound (`[value`).
    Inclusive(Vec<u8>),
    /// Exclusive bound (`(value`).
    Exclusive(Vec<u8>),
}

impl LexBound {
    fn to_arg(&self) -> Vec<u8> {
        match self {
            LexBound::NegativeInfinity => b"-".to_vec(),
            LexBound::PositiveInfinity => b"+".to_vec(),
            LexBound::Inclusive(v) => {
                let mut out = vec![b'['];
                out.extend_from_slice(v);
                out
            }
            LexBound::Exclusive(v) => {
                let mut out = vec![b'('];
                out.extend_from_slice(v);
                out
            }
        }
    }
}

/// Aggregation mode for `ZUNIONSTORE`/`ZINTERSTORE`.
///
/// Mirrors Python `AggregationType`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AggregationType {
    /// Sum the scores.
    Sum,
    /// Take the minimum score.
    Min,
    /// Take the maximum score.
    Max,
}

impl AggregationType {
    fn as_arg(&self) -> &'static str {
        match self {
            AggregationType::Sum => "SUM",
            AggregationType::Min => "MIN",
            AggregationType::Max => "MAX",
        }
    }
}

/// Sorted-set commands (`ZADD`, `ZRANGE`, `ZSCORE`, ...).
#[async_trait]
pub trait SortedSetCommands: CommandExecutor {
    /// Increment the score of `member` by `increment` (`ZADD ... INCR`).
    ///
    /// This is the plain (unconditional) `INCR` form, so the result is always
    /// `Some(new_score)`. The `Option` return is kept for the conditional
    /// forms of the command: combined with `NX`/`XX`/`GT`/`LT` (reachable via
    /// [`crate::CustomCommand::custom_command`]) the server replies nil when
    /// the condition suppresses the update.
    async fn zadd_incr<K: ToRedisArgs + Send, M: ToRedisArgs + Send>(
        &self,
        key: K,
        member: M,
        increment: f64,
    ) -> Result<Option<f64>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZADD")
            .arg(key)
            .arg("INCR")
            .arg(increment)
            .arg(member);
        value::to_opt_f64(self.execute_command(cmd, None).await?)
    }

    /// Get the rank of `member` with its score, low to high (`ZRANK ... WITHSCORE`).
    async fn zrank_withscore<K: ToRedisArgs + Send, M: ToRedisArgs + Send>(
        &self,
        key: K,
        member: M,
    ) -> Result<Option<(i64, f64)>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZRANK").arg(key).arg(member).arg("WITHSCORE");
        parse_rank_withscore(self.execute_command(cmd, None).await?)
    }

    /// Get the rank of `member` with its score, high to low
    /// (`ZREVRANK ... WITHSCORE`).
    async fn zrevrank_withscore<K: ToRedisArgs + Send, M: ToRedisArgs + Send>(
        &self,
        key: K,
        member: M,
    ) -> Result<Option<(i64, f64)>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZREVRANK").arg(key).arg(member).arg("WITHSCORE");
        parse_rank_withscore(self.execute_command(cmd, None).await?)
    }

    #[doc(hidden)]
    async fn bzpop<K: ToRedisArgs + Send + Sync>(
        &self,
        op: &'static str,
        keys: &[K],
        timeout: f64,
    ) -> Result<Option<(Bytes, Bytes, f64)>> {
        let mut cmd = Cmd::new();
        cmd.arg(op);
        for k in keys {
            cmd.arg(k);
        }
        cmd.arg(timeout);
        match self.execute_command(cmd, None).await? {
            redis::Value::Nil => Ok(None),
            redis::Value::Array(mut items) if items.len() == 3 => {
                let score = value::to_f64(items.pop().unwrap())?;
                let member = value::to_bytes(items.pop().unwrap())?;
                let key = value::to_bytes(items.pop().unwrap())?;
                Ok(Some((key, member, score)))
            }
            other => Err(crate::error::GlideError::Request(format!(
                "unexpected blocking zpop reply: {other:?}"
            ))),
        }
    }

    /// Store a range of a sorted set into `destination` (`ZRANGESTORE` by index).
    /// Returns the number of elements stored.
    async fn zrangestore_by_index<D: ToRedisArgs + Send, S: ToRedisArgs + Send>(
        &self,
        destination: D,
        source: S,
        start: i64,
        stop: i64,
        rev: bool,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("ZRANGESTORE")
            .arg(destination)
            .arg(source)
            .arg(start)
            .arg(stop);
        if rev {
            cmd.arg("REV");
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Store into `destination` the members of the sorted set `source` whose
    /// scores are within `[min, max]` (`ZRANGESTORE ... BYSCORE`), returning the
    /// number of elements stored.
    ///
    /// When `rev` is `true` the range is interpreted in reverse (highest scores
    /// first); the bounds are emitted in the order the server requires for `REV`.
    /// `limit` applies an optional `LIMIT offset count`.
    async fn zrangestore_by_score<D: ToRedisArgs + Send, S: ToRedisArgs + Send>(
        &self,
        destination: D,
        source: S,
        min: ScoreBound,
        max: ScoreBound,
        rev: bool,
        limit: Option<Limit>,
    ) -> Result<i64> {
        // For a reverse range the server expects the high bound first.
        let (first, second) = if rev { (max, min) } else { (min, max) };
        let mut cmd = Cmd::new();
        cmd.arg("ZRANGESTORE")
            .arg(destination)
            .arg(source)
            .arg(first.to_arg())
            .arg(second.to_arg())
            .arg("BYSCORE");
        if rev {
            cmd.arg("REV");
        }
        if let Some(limit) = limit {
            cmd.arg("LIMIT").arg(limit.offset).arg(limit.count);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Store into `destination` the members of the sorted set `source` whose
    /// lexicographical values are within `[min, max]`
    /// (`ZRANGESTORE ... BYLEX`), returning the number of elements stored.
    ///
    /// When `rev` is `true` the range is interpreted in reverse; the bounds are
    /// emitted in the order the server requires for `REV`. `limit` applies an
    /// optional `LIMIT offset count`.
    async fn zrangestore_by_lex<D: ToRedisArgs + Send, S: ToRedisArgs + Send>(
        &self,
        destination: D,
        source: S,
        min: &LexBound,
        max: &LexBound,
        rev: bool,
        limit: Option<Limit>,
    ) -> Result<i64> {
        let (first, second) = if rev { (max, min) } else { (min, max) };
        let mut cmd = Cmd::new();
        cmd.arg("ZRANGESTORE")
            .arg(destination)
            .arg(source)
            .arg(first.to_arg())
            .arg(second.to_arg())
            .arg("BYLEX");
        if rev {
            cmd.arg("REV");
        }
        if let Some(limit) = limit {
            cmd.arg("LIMIT").arg(limit.offset).arg(limit.count);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Compute the difference of the given sorted sets (`ZDIFF`).
    async fn zdiff<K: ToRedisArgs + Send + Sync>(&self, keys: &[K]) -> Result<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZDIFF").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Compute the difference of the given sorted sets with scores
    /// (`ZDIFF ... WITHSCORES`).
    async fn zdiff_withscores<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
    ) -> Result<Vec<(Bytes, f64)>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZDIFF").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        cmd.arg("WITHSCORES");
        collect_member_scores(self.execute_command(cmd, None).await?)
    }

    /// Store the difference of the given sorted sets into `destination`
    /// (`ZDIFFSTORE`).
    async fn zdiffstore<D: ToRedisArgs + Send, K: ToRedisArgs + Send + Sync>(
        &self,
        destination: D,
        keys: &[K],
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("ZDIFFSTORE").arg(destination).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Compute the union of the given sorted sets (`ZUNION`).
    async fn zunion<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> Result<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZUNION").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Compute the union of the given sorted sets with scores
    /// (`ZUNION ... WITHSCORES`).
    async fn zunion_withscores<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> Result<Vec<(Bytes, f64)>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZUNION").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        cmd.arg("WITHSCORES");
        collect_member_scores(self.execute_command(cmd, None).await?)
    }

    /// Compute the intersection of the given sorted sets (`ZINTER`).
    async fn zinter<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> Result<Vec<Bytes>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZINTER").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Compute the intersection of the given sorted sets with scores
    /// (`ZINTER ... WITHSCORES`).
    async fn zinter_withscores<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> Result<Vec<(Bytes, f64)>> {
        let mut cmd = Cmd::new();
        cmd.arg("ZINTER").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        cmd.arg("WITHSCORES");
        collect_member_scores(self.execute_command(cmd, None).await?)
    }

    /// Cardinality of the intersection of the given sorted sets (`ZINTERCARD`),
    /// with an optional `LIMIT`.
    async fn zintercard<K: ToRedisArgs + Send + Sync>(
        &self,
        keys: &[K],
        limit: Option<i64>,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("ZINTERCARD").arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        if let Some(l) = limit {
            cmd.arg("LIMIT").arg(l);
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    #[doc(hidden)]
    async fn zsetop_store<D: ToRedisArgs + Send, K: ToRedisArgs + Send + Sync>(
        &self,
        op: &'static str,
        destination: D,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg(op).arg(destination).arg(keys.len());
        for k in keys {
            cmd.arg(k);
        }
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }
}

fn collect_bytes(v: redis::Value) -> Result<Vec<Bytes>> {
    match v {
        redis::Value::Array(items) => items.into_iter().map(value::to_bytes).collect(),
        redis::Value::Nil => Ok(Vec::new()),
        other => Ok(vec![value::to_bytes(other)?]),
    }
}

/// Parse a `WITHSCORES`/`ZPOPMIN`-style reply into `(member, score)` pairs,
/// handling both RESP2 flat arrays and RESP3 nested pairs.
fn collect_member_scores(v: redis::Value) -> Result<Vec<(Bytes, f64)>> {
    match v {
        redis::Value::Nil => Ok(Vec::new()),
        // RESP3 returns a map of member -> score.
        redis::Value::Map(pairs) => pairs
            .into_iter()
            .map(|(m, s)| Ok((value::to_bytes(m)?, value::to_f64(s)?)))
            .collect(),
        redis::Value::Array(items) => {
            // RESP3: array of [member, score] pairs.
            if items
                .iter()
                .all(|it| matches!(it, redis::Value::Array(inner) if inner.len() == 2))
            {
                let mut out = Vec::with_capacity(items.len());
                for it in items {
                    if let redis::Value::Array(mut pair) = it {
                        let score = value::to_f64(pair.pop().unwrap())?;
                        let member = value::to_bytes(pair.pop().unwrap())?;
                        out.push((member, score));
                    }
                }
                Ok(out)
            } else {
                // RESP2: flat [member, score, member, score, ...].
                let mut out = Vec::with_capacity(items.len() / 2);
                let mut iter = items.into_iter();
                while let (Some(m), Some(s)) = (iter.next(), iter.next()) {
                    out.push((value::to_bytes(m)?, value::to_f64(s)?));
                }
                Ok(out)
            }
        }
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected sorted-set reply: {other:?}"
        ))),
    }
}

impl<T: CommandExecutor + ?Sized> SortedSetCommands for T {}

/// Parse a `ZRANK ... WITHSCORE` reply (`[rank, score]` or nil).
fn parse_rank_withscore(v: redis::Value) -> Result<Option<(i64, f64)>> {
    match v {
        redis::Value::Nil => Ok(None),
        redis::Value::Array(mut items) if items.len() == 2 => {
            let score = value::to_f64(items.pop().unwrap())?;
            let rank = value::to_i64(items.pop().unwrap())?;
            Ok(Some((rank, score)))
        }
        other => Err(crate::error::GlideError::Request(format!(
            "unexpected ZRANK WITHSCORE reply: {other:?}"
        ))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn score_bound_formatting() {
        assert_eq!(ScoreBound::NegativeInfinity.to_arg(), "-inf");
        assert_eq!(ScoreBound::PositiveInfinity.to_arg(), "+inf");
        assert_eq!(ScoreBound::Exclusive(1.0).to_arg(), "(1");
    }
}
