// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Bitmap commands. Mirrors Python's bitmap command surface.

use crate::ValkeyResult;
use crate::cmd::cmd;
use crate::executor::CommandExecutor;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::write::ToValkeyArgs;
use crate::write::ValkeyWrite;
use async_trait::async_trait;

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

impl ToValkeyArgs for BitEncoding {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            BitEncoding::Signed(n) => out.write_arg_fmt(format_args!("i{n}")),
            BitEncoding::Unsigned(n) => out.write_arg_fmt(format_args!("u{n}")),
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

impl ToValkeyArgs for BitFieldOffset {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            BitFieldOffset::Bit(n) => out.write_arg_fmt(n),
            BitFieldOffset::Multiplier(n) => out.write_arg_fmt(format_args!("#{n}")),
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

impl ToValkeyArgs for BitOverflow {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(match self {
            BitOverflow::Wrap => b"WRAP".as_slice(),
            BitOverflow::Sat => b"SAT".as_slice(),
            BitOverflow::Fail => b"FAIL".as_slice(),
        });
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

impl ToValkeyArgs for BitFieldSubcommand {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        match self {
            BitFieldSubcommand::Get { encoding, offset } => {
                out.write_arg(b"GET");
                encoding.write_valkey_args(out);
                offset.write_valkey_args(out);
            }
            BitFieldSubcommand::Set {
                encoding,
                offset,
                value,
            } => {
                out.write_arg(b"SET");
                encoding.write_valkey_args(out);
                offset.write_valkey_args(out);
                value.write_valkey_args(out);
            }
            BitFieldSubcommand::IncrBy {
                encoding,
                offset,
                increment,
            } => {
                out.write_arg(b"INCRBY");
                encoding.write_valkey_args(out);
                offset.write_valkey_args(out);
                increment.write_valkey_args(out);
            }
            BitFieldSubcommand::Overflow(overflow) => {
                out.write_arg(b"OVERFLOW");
                overflow.write_valkey_args(out);
            }
        }
    }
}

/// Bitmap commands (`SETBIT`, `GETBIT`, `BITCOUNT`, `BITPOS`, `BITOP`).
#[async_trait]
pub trait BitmapCommands: CommandExecutor {
    // TODO #7082: add a `bitcount` variant that takes the `BYTE`/`BIT` index unit
    // (the `BitmapIndexType` type already exists), to match the other GLIDE clients.

    /// Find the position of the first bit set to `bit` (`BITPOS`).
    async fn bitpos<K: ToValkeyArgs + Send>(&self, key: K, bit: u8) -> ValkeyResult<i64> {
        let cmd = cmd("BITPOS").with_arg(key).with_arg(bit);
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Find the position of the first bit set to `bit` within a range
    /// (`BITPOS key bit start end [BYTE|BIT]`).
    async fn bitpos_range<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        bit: u8,
        start: i64,
        end: i64,
        index_type: Option<BitmapIndexType>,
    ) -> ValkeyResult<i64> {
        let cmd = cmd("BITPOS")
            .with_arg(key)
            .with_arg(bit)
            .with_arg(start)
            .with_arg(end)
            .with_arg(index_type.map(|it| it.as_arg()));
        i64::from_owned_valkey_value(self.execute_command(cmd, None).await?)
    }

    /// Perform arbitrary bit-field operations (`BITFIELD`). Returns one result
    /// per non-`OVERFLOW` subcommand; a `None` indicates a `FAIL` overflow.
    async fn bitfield<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        subcommands: &[BitFieldSubcommand],
    ) -> ValkeyResult<Vec<Option<i64>>> {
        let cmd = cmd("BITFIELD").with_arg(key).with_arg(subcommands);
        parse_bitfield(self.execute_command(cmd, None).await?)
    }

    /// Read-only bit-field operations (`BITFIELD_RO`). Only `GET` subcommands are
    /// permitted by the server.
    async fn bitfield_readonly<K: ToValkeyArgs + Send>(
        &self,
        key: K,
        subcommands: &[BitFieldSubcommand],
    ) -> ValkeyResult<Vec<Option<i64>>> {
        let cmd = cmd("BITFIELD_RO").with_arg(key).with_arg(subcommands);
        parse_bitfield(self.execute_command(cmd, None).await?)
    }
}

impl<T: CommandExecutor + ?Sized> BitmapCommands for T {}

/// Parse a `BITFIELD` reply (array of ints, with `Nil` for `FAIL` overflow).
fn parse_bitfield(v: ValkeyValue) -> ValkeyResult<Vec<Option<i64>>> {
    match v {
        ValkeyValue::Nil => Ok(Vec::new()),
        ValkeyValue::Array(items) => items
            .into_iter()
            .map(|it| match it {
                ValkeyValue::Nil => Ok(None),
                other => Ok(Some(i64::from_owned_valkey_value(other)?)),
            })
            .collect(),
        other => Ok(vec![Some(i64::from_owned_valkey_value(other)?)]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_utils::assert_args;

    #[test]
    fn encoding_and_offset_args() {
        assert_args(BitEncoding::Signed(8), &["i8"]);
        assert_args(BitEncoding::Unsigned(16), &["u16"]);
        assert_args(BitFieldOffset::Bit(5), &["5"]);
        assert_args(BitFieldOffset::Multiplier(3), &["#3"]);
    }

    #[test]
    fn overflow_args() {
        assert_args(BitOverflow::Wrap, &["WRAP"]);
        assert_args(BitOverflow::Sat, &["SAT"]);
        assert_args(BitOverflow::Fail, &["FAIL"]);
    }

    #[test]
    fn bitfield_subcommand_args() {
        assert_args(
            BitFieldSubcommand::Get {
                encoding: BitEncoding::Unsigned(8),
                offset: BitFieldOffset::Bit(0),
            },
            &["GET", "u8", "0"],
        );
        assert_args(
            BitFieldSubcommand::Set {
                encoding: BitEncoding::Signed(5),
                offset: BitFieldOffset::Multiplier(1),
                value: 12,
            },
            &["SET", "i5", "#1", "12"],
        );
        assert_args(
            BitFieldSubcommand::IncrBy {
                encoding: BitEncoding::Unsigned(4),
                offset: BitFieldOffset::Bit(2),
                increment: -3,
            },
            &["INCRBY", "u4", "2", "-3"],
        );
        assert_args(
            BitFieldSubcommand::Overflow(BitOverflow::Sat),
            &["OVERFLOW", "SAT"],
        );
    }

    #[test]
    fn parse_bitfield_handles_nil() {
        let v = ValkeyValue::Array(vec![ValkeyValue::Int(1), ValkeyValue::Nil]);
        assert_eq!(parse_bitfield(v).unwrap(), vec![Some(1), None]);
    }
}
