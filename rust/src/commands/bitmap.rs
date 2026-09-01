// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Bitmap commands. Mirrors Python's bitmap command surface.

use crate::error::Result;
use crate::executor::CommandExecutor;
use crate::value;
use async_trait::async_trait;
use redis::{Cmd, ToRedisArgs};

/// Index unit for `BITCOUNT`/`BITPOS` range queries.
///
/// Mirrors Python `BitmapIndexType`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BitmapIndexType {
    /// Interpret the range as byte offsets (`BYTE`).
    Byte,
    /// Interpret the range as bit offsets (`BIT`).
    Bit,
}

impl BitmapIndexType {
    fn as_arg(&self) -> &'static str {
        match self {
            BitmapIndexType::Byte => "BYTE",
            BitmapIndexType::Bit => "BIT",
        }
    }
}

/// A signed or unsigned bit encoding for `BITFIELD`/`BITFIELD_RO`.
///
/// Mirrors Python `SignedEncoding`/`UnsignedEncoding`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BitEncoding {
    /// Signed encoding of the given bit width (`i<width>`, `< 65` bits).
    Signed(u32),
    /// Unsigned encoding of the given bit width (`u<width>`, `< 64` bits).
    Unsigned(u32),
}

impl BitEncoding {
    fn to_arg(self) -> String {
        match self {
            BitEncoding::Signed(n) => format!("i{n}"),
            BitEncoding::Unsigned(n) => format!("u{n}"),
        }
    }
}

/// A bit offset for `BITFIELD`/`BITFIELD_RO`.
///
/// Mirrors Python `BitOffset`/`BitOffsetMultiplier`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BitFieldOffset {
    /// A raw bit index offset.
    Bit(u64),
    /// An offset multiplied by the encoding width (`#<offset>`).
    Multiplier(u64),
}

impl BitFieldOffset {
    fn to_arg(self) -> String {
        match self {
            BitFieldOffset::Bit(n) => n.to_string(),
            BitFieldOffset::Multiplier(n) => format!("#{n}"),
        }
    }
}

/// Overflow behavior for `BITFIELD` `SET`/`INCRBY` subcommands.
///
/// Mirrors Python `BitOverflowControl`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BitOverflow {
    /// Wrap around on overflow (`WRAP`).
    Wrap,
    /// Saturate at the min/max value on overflow (`SAT`).
    Sat,
    /// Return `None` on overflow (`FAIL`).
    Fail,
}

impl BitOverflow {
    fn as_arg(&self) -> &'static str {
        match self {
            BitOverflow::Wrap => "WRAP",
            BitOverflow::Sat => "SAT",
            BitOverflow::Fail => "FAIL",
        }
    }
}

/// A subcommand for `BITFIELD`/`BITFIELD_RO`.
///
/// Mirrors Python `BitFieldGet`/`BitFieldSet`/`BitFieldIncrBy`/`BitFieldOverflow`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BitFieldSubcommand {
    /// Read a value (`GET encoding offset`).
    Get {
        /// The bit encoding.
        encoding: BitEncoding,
        /// The bit offset.
        offset: BitFieldOffset,
    },
    /// Write a value (`SET encoding offset value`).
    Set {
        /// The bit encoding.
        encoding: BitEncoding,
        /// The bit offset.
        offset: BitFieldOffset,
        /// The value to set.
        value: i64,
    },
    /// Increment a value (`INCRBY encoding offset increment`).
    IncrBy {
        /// The bit encoding.
        encoding: BitEncoding,
        /// The bit offset.
        offset: BitFieldOffset,
        /// The amount to increment by.
        increment: i64,
    },
    /// Set the overflow behavior for subsequent subcommands (`OVERFLOW`).
    Overflow(BitOverflow),
}

impl BitFieldSubcommand {
    fn add_to(&self, cmd: &mut Cmd) {
        match self {
            BitFieldSubcommand::Get { encoding, offset } => {
                cmd.arg("GET").arg(encoding.to_arg()).arg(offset.to_arg());
            }
            BitFieldSubcommand::Set {
                encoding,
                offset,
                value,
            } => {
                cmd.arg("SET")
                    .arg(encoding.to_arg())
                    .arg(offset.to_arg())
                    .arg(value);
            }
            BitFieldSubcommand::IncrBy {
                encoding,
                offset,
                increment,
            } => {
                cmd.arg("INCRBY")
                    .arg(encoding.to_arg())
                    .arg(offset.to_arg())
                    .arg(increment);
            }
            BitFieldSubcommand::Overflow(o) => {
                cmd.arg("OVERFLOW").arg(o.as_arg());
            }
        }
    }
}

/// Bitmap commands (`SETBIT`, `GETBIT`, `BITCOUNT`, `BITPOS`, `BITOP`).
#[async_trait]
pub trait BitmapCommands: CommandExecutor {
    /// Find the position of the first bit set to `bit` (`BITPOS`).
    async fn bitpos<K: ToRedisArgs + Send>(&self, key: K, bit: u8) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("BITPOS").arg(key).arg(bit);
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Find the position of the first bit set to `bit` within a range
    /// (`BITPOS key bit start end [BYTE|BIT]`).
    async fn bitpos_range<K: ToRedisArgs + Send>(
        &self,
        key: K,
        bit: u8,
        start: i64,
        end: i64,
        index_type: Option<BitmapIndexType>,
    ) -> Result<i64> {
        let mut cmd = Cmd::new();
        cmd.arg("BITPOS").arg(key).arg(bit).arg(start).arg(end);
        if let Some(it) = index_type {
            cmd.arg(it.as_arg());
        }
        value::to_i64(self.execute_command(cmd, None).await?)
    }

    /// Perform arbitrary bit-field operations (`BITFIELD`). Returns one result
    /// per non-`OVERFLOW` subcommand; a `None` indicates a `FAIL` overflow.
    async fn bitfield<K: ToRedisArgs + Send>(
        &self,
        key: K,
        subcommands: &[BitFieldSubcommand],
    ) -> Result<Vec<Option<i64>>> {
        let mut cmd = Cmd::new();
        cmd.arg("BITFIELD").arg(key);
        for sub in subcommands {
            sub.add_to(&mut cmd);
        }
        parse_bitfield(self.execute_command(cmd, None).await?)
    }

    /// Read-only bit-field operations (`BITFIELD_RO`). Only `GET` subcommands are
    /// permitted by the server.
    async fn bitfield_readonly<K: ToRedisArgs + Send>(
        &self,
        key: K,
        subcommands: &[BitFieldSubcommand],
    ) -> Result<Vec<Option<i64>>> {
        let mut cmd = Cmd::new();
        cmd.arg("BITFIELD_RO").arg(key);
        for sub in subcommands {
            sub.add_to(&mut cmd);
        }
        parse_bitfield(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> BitmapCommands for T {}

/// Parse a `BITFIELD` reply (array of ints, with `Nil` for `FAIL` overflow).
fn parse_bitfield(v: redis::Value) -> Result<Vec<Option<i64>>> {
    match v {
        redis::Value::Nil => Ok(Vec::new()),
        redis::Value::Array(items) => items
            .into_iter()
            .map(|it| match it {
                redis::Value::Nil => Ok(None),
                other => Ok(Some(value::to_i64(other)?)),
            })
            .collect(),
        other => Ok(vec![Some(value::to_i64(other)?)]),
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
    fn encoding_and_offset_args() {
        assert_eq!(BitEncoding::Signed(8).to_arg(), "i8");
        assert_eq!(BitEncoding::Unsigned(16).to_arg(), "u16");
        assert_eq!(BitFieldOffset::Bit(5).to_arg(), "5");
        assert_eq!(BitFieldOffset::Multiplier(3).to_arg(), "#3");
    }

    #[test]
    fn bitfield_subcommand_args() {
        let mut cmd = Cmd::new();
        BitFieldSubcommand::Get {
            encoding: BitEncoding::Unsigned(8),
            offset: BitFieldOffset::Bit(0),
        }
        .add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["GET", "u8", "0"]);

        let mut cmd = Cmd::new();
        BitFieldSubcommand::Set {
            encoding: BitEncoding::Signed(5),
            offset: BitFieldOffset::Multiplier(1),
            value: 12,
        }
        .add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["SET", "i5", "#1", "12"]);

        let mut cmd = Cmd::new();
        BitFieldSubcommand::IncrBy {
            encoding: BitEncoding::Unsigned(4),
            offset: BitFieldOffset::Bit(2),
            increment: -3,
        }
        .add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["INCRBY", "u4", "2", "-3"]);

        let mut cmd = Cmd::new();
        BitFieldSubcommand::Overflow(BitOverflow::Sat).add_to(&mut cmd);
        assert_eq!(args_of(&cmd), vec!["OVERFLOW", "SAT"]);
    }

    #[test]
    fn parse_bitfield_handles_nil() {
        let v = redis::Value::Array(vec![redis::Value::Int(1), redis::Value::Nil]);
        assert_eq!(parse_bitfield(v).unwrap(), vec![Some(1), None]);
    }
}
