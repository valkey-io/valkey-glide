// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Sorted-set commands. Mirrors Python's sorted-set command surface.
#![allow(clippy::type_complexity)]

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::commands::options::Limit;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::value::to_glide_error;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyWrite;
use async_trait::async_trait;
use bytes::Bytes;

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

impl ToValkeyArgs for ScoreBound {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            ScoreBound::NegativeInfinity => out.write_arg(b"-inf"),
            ScoreBound::PositiveInfinity => out.write_arg(b"+inf"),
            ScoreBound::Inclusive(v) => out.write_arg_fmt(v),
            ScoreBound::Exclusive(v) => out.write_arg_fmt(format_args!("({v}")),
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

impl ToValkeyArgs for LexBound {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        // A bounded value is prefixed with `[`/`(` within the same argument.
        let prefixed = |prefix: u8, value: &[u8]| {
            let mut arg = Vec::with_capacity(1 + value.len());
            arg.push(prefix);
            arg.extend_from_slice(value);
            arg
        };
        match self {
            LexBound::NegativeInfinity => out.write_arg(b"-"),
            LexBound::PositiveInfinity => out.write_arg(b"+"),
            LexBound::Inclusive(v) => out.write_arg(&prefixed(b'[', v)),
            LexBound::Exclusive(v) => out.write_arg(&prefixed(b'(', v)),
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
    async fn zadd_incr<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
        increment: f64,
    ) -> ValkeyResult<Option<f64>> {
        let mut cmd = cmd("ZADD");
        cmd.arg(key).arg("INCR").arg(increment).arg(member);
        Option::<f64>::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Get the rank of `member` with its score, low to high (`ZRANK ... WITHSCORE`).
    async fn zrank_withscore<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
    ) -> ValkeyResult<Option<(i64, f64)>> {
        let mut cmd = cmd("ZRANK");
        cmd.arg(key).arg(member).arg("WITHSCORE");
        parse_rank_withscore(self.execute_command(cmd, None).await?)
    }

    /// Get the rank of `member` with its score, high to low
    /// (`ZREVRANK ... WITHSCORE`).
    async fn zrevrank_withscore<K: ToValkeyArgs + Send, M: ToValkeyArgs + Send>(
        &self,
        key: K,
        member: M,
    ) -> ValkeyResult<Option<(i64, f64)>> {
        let mut cmd = cmd("ZREVRANK");
        cmd.arg(key).arg(member).arg("WITHSCORE");
        parse_rank_withscore(self.execute_command(cmd, None).await?)
    }

    /// Store a range of a sorted set into `destination` (`ZRANGESTORE` by index).
    /// Returns the number of elements stored.
    async fn zrangestore_by_index<D: ToValkeyArgs + Send, S: ToValkeyArgs + Send>(
        &self,
        destination: D,
        source: S,
        start: i64,
        stop: i64,
        rev: bool,
    ) -> ValkeyResult<i64> {
        let mut cmd = cmd("ZRANGESTORE");
        cmd.arg(destination).arg(source).arg(start).arg(stop);
        if rev {
            cmd.arg("REV");
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Store into `destination` the members of the sorted set `source` whose
    /// scores are within `[min, max]` (`ZRANGESTORE ... BYSCORE`), returning the
    /// number of elements stored.
    ///
    /// When `rev` is `true` the range is interpreted in reverse (highest scores
    /// first); the bounds are emitted in the order the server requires for `REV`.
    /// `limit` applies an optional `LIMIT offset count`.
    async fn zrangestore_by_score<D: ToValkeyArgs + Send, S: ToValkeyArgs + Send>(
        &self,
        destination: D,
        source: S,
        min: ScoreBound,
        max: ScoreBound,
        rev: bool,
        limit: Option<Limit>,
    ) -> ValkeyResult<i64> {
        // For a reverse range the server expects the high bound first.
        let (first, second) = if rev { (max, min) } else { (min, max) };
        let mut cmd = cmd("ZRANGESTORE");
        cmd.arg(destination)
            .arg(source)
            .arg(first)
            .arg(second)
            .arg("BYSCORE");
        if rev {
            cmd.arg("REV");
        }
        if let Some(limit) = limit {
            cmd.arg("LIMIT").arg(limit.offset).arg(limit.count);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Store into `destination` the members of the sorted set `source` whose
    /// lexicographical values are within `[min, max]`
    /// (`ZRANGESTORE ... BYLEX`), returning the number of elements stored.
    ///
    /// When `rev` is `true` the range is interpreted in reverse; the bounds are
    /// emitted in the order the server requires for `REV`. `limit` applies an
    /// optional `LIMIT offset count`.
    async fn zrangestore_by_lex<D: ToValkeyArgs + Send, S: ToValkeyArgs + Send>(
        &self,
        destination: D,
        source: S,
        min: &LexBound,
        max: &LexBound,
        rev: bool,
        limit: Option<Limit>,
    ) -> ValkeyResult<i64> {
        let (first, second) = if rev { (max, min) } else { (min, max) };
        let mut cmd = cmd("ZRANGESTORE");
        cmd.arg(destination)
            .arg(source)
            .arg(first)
            .arg(second)
            .arg("BYLEX");
        if rev {
            cmd.arg("REV");
        }
        if let Some(limit) = limit {
            cmd.arg("LIMIT").arg(limit.offset).arg(limit.count);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Compute the difference of the given sorted sets (`ZDIFF`).
    async fn zdiff<K: ToValkeyArgs + Send + Sync>(&self, keys: &[K]) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = cmd("ZDIFF");
        cmd.arg(keys.num_of_args()).arg(keys);
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Compute the difference of the given sorted sets with scores
    /// (`ZDIFF ... WITHSCORES`).
    async fn zdiff_withscores<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
    ) -> ValkeyResult<Vec<(Bytes, f64)>> {
        let mut cmd = cmd("ZDIFF");
        cmd.arg(keys.num_of_args()).arg(keys).arg("WITHSCORES");
        collect_member_scores(self.execute_command(cmd, None).await?)
    }

    /// Store the difference of the given sorted sets into `destination`
    /// (`ZDIFFSTORE`).
    async fn zdiffstore<D: ToValkeyArgs + Send, K: ToValkeyArgs + Send + Sync>(
        &self,
        destination: D,
        keys: &[K],
    ) -> ValkeyResult<i64> {
        let mut cmd = cmd("ZDIFFSTORE");
        cmd.arg(destination).arg(keys.num_of_args()).arg(keys);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Compute the union of the given sorted sets (`ZUNION`).
    async fn zunion<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = cmd("ZUNION");
        cmd.arg(keys.num_of_args()).arg(keys);
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Compute the union of the given sorted sets with scores
    /// (`ZUNION ... WITHSCORES`).
    async fn zunion_withscores<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> ValkeyResult<Vec<(Bytes, f64)>> {
        let mut cmd = cmd("ZUNION");
        cmd.arg(keys.num_of_args()).arg(keys);
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        cmd.arg("WITHSCORES");
        collect_member_scores(self.execute_command(cmd, None).await?)
    }

    /// Compute the intersection of the given sorted sets (`ZINTER`).
    async fn zinter<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> ValkeyResult<Vec<Bytes>> {
        let mut cmd = cmd("ZINTER");
        cmd.arg(keys.num_of_args()).arg(keys);
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        collect_bytes(self.execute_command(cmd, None).await?)
    }

    /// Compute the intersection of the given sorted sets with scores
    /// (`ZINTER ... WITHSCORES`).
    async fn zinter_withscores<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        aggregate: Option<AggregationType>,
    ) -> ValkeyResult<Vec<(Bytes, f64)>> {
        let mut cmd = cmd("ZINTER");
        cmd.arg(keys.num_of_args()).arg(keys);
        if let Some(agg) = aggregate {
            cmd.arg("AGGREGATE").arg(agg.as_arg());
        }
        cmd.arg("WITHSCORES");
        collect_member_scores(self.execute_command(cmd, None).await?)
    }

    /// Cardinality of the intersection of the given sorted sets (`ZINTERCARD`),
    /// with an optional `LIMIT`.
    async fn zintercard<K: ToValkeyArgs + Send + Sync>(
        &self,
        keys: &[K],
        limit: Option<i64>,
    ) -> ValkeyResult<i64> {
        let mut cmd = cmd("ZINTERCARD");
        cmd.arg(keys.num_of_args()).arg(keys);
        if let Some(l) = limit {
            cmd.arg("LIMIT").arg(l);
        }
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }
}

fn collect_bytes(v: ValkeyValue) -> ValkeyResult<Vec<Bytes>> {
    match v {
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(Bytes::from_owned_valkey_value)
            .collect(),
        ValkeyValue::Nil => Ok(Vec::new()),
        other => Ok(vec![Bytes::from_owned_valkey_value(other)?]),
    }
}

/// Parse a `WITHSCORES`/`ZPOPMIN`-style reply into `(member, score)` pairs,
/// handling both RESP2 flat arrays and RESP3 nested pairs.
fn collect_member_scores(v: ValkeyValue) -> ValkeyResult<Vec<(Bytes, f64)>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        // RESP3 returns a map of member -> score.
        ValkeyValue::Map(pairs) => pairs
            .into_iter()
            .map(|(m, s)| {
                Ok((
                    Bytes::from_owned_valkey_value(m)?,
                    f64::from_owned_valkey_value(s)?,
                ))
            })
            .collect(),
        ValkeyValue::Array(items) => {
            // RESP3: array of [member, score] pairs.
            if items
                .iter()
                .all(|it| matches!(it, ValkeyValue::Array(inner) if inner.len() == 2))
            {
                let mut out = Vec::with_capacity(items.len());
                for it in items {
                    if let ValkeyValue::Array(mut pair) = it {
                        let score = f64::from_owned_valkey_value(pair.pop().unwrap())?;
                        let member = Bytes::from_owned_valkey_value(pair.pop().unwrap())?;
                        out.push((member, score));
                    }
                }
                Ok(out)
            } else {
                // RESP2: flat [member, score, member, score, ...].
                let mut out = Vec::with_capacity(items.len() / 2);
                let mut iter = items.into_iter();
                while let (Some(m), Some(s)) = (iter.next(), iter.next()) {
                    out.push((
                        Bytes::from_owned_valkey_value(m)?,
                        f64::from_owned_valkey_value(s)?,
                    ));
                }
                Ok(out)
            }
        }
        other => Err(to_glide_error(other, "Unexpected sorted-set reply.")),
    }
}

impl<T: CommandExecutor + ?Sized> SortedSetCommands for T {}

/// Parse a `ZRANK ... WITHSCORE` reply (`[rank, score]` or nil).
fn parse_rank_withscore(v: ValkeyValue) -> ValkeyResult<Option<(i64, f64)>> {
    match v {
        ValkeyValue::Nil => Ok(None),
        ValkeyValue::Array(mut items) if items.len() == 2 => {
            let score = f64::from_owned_valkey_value(items.pop().unwrap())?;
            let rank = i64::from_owned_valkey_value(items.pop().unwrap())?;
            Ok(Some((rank, score)))
        }
        other => Err(to_glide_error(other, "Unexpected ZRANK WITHSCORE reply.")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::assert_args;

    #[test]
    fn score_bound_args() {
        assert_args(ScoreBound::NegativeInfinity, &["-inf"]);
        assert_args(ScoreBound::PositiveInfinity, &["+inf"]);
        assert_args(ScoreBound::Inclusive(1.5), &["1.5"]);
        assert_args(ScoreBound::Exclusive(1.0), &["(1"]);
    }

    #[test]
    fn lex_bound_args() {
        assert_args(LexBound::NegativeInfinity, &["-"]);
        assert_args(LexBound::PositiveInfinity, &["+"]);
        assert_args(LexBound::Inclusive(b"a".to_vec()), &["[a"]);
        assert_args(LexBound::Exclusive(b"b".to_vec()), &["(b"]);
        // Binary values are kept byte-for-byte after the prefix.
        assert_args(
            LexBound::Inclusive(vec![0xFF, 0x00]),
            &[[b'[', 0xFF, 0x00].as_slice()],
        );
    }
}
