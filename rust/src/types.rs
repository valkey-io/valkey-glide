// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Typed reply types returned by [`crate::AsyncTypedCommands`] and [`crate::TypedCommands`].

use crate::ValkeyResult;
use crate::value::FromValkeyValue;
use crate::value::ValkeyValue;
use crate::value::to_glide_error;

// --- ValueType ----------------------------------------------------------------------------------

/// The type of the value stored at a key.
///
/// Mirrors redis-rs's `ValueType`.
#[derive(Debug, Clone, PartialEq)]
#[non_exhaustive]
pub enum ValueType {
    /// The key does not exist (`none`).
    None,
    /// A string (`string`).
    String,
    /// A list (`list`).
    List,
    /// A set (`set`).
    Set,
    /// A sorted set (`zset`).
    ZSet,
    /// A hash (`hash`).
    Hash,
    /// A stream (`stream`).
    Stream,
    /// Any other type name.
    Unknown(String),
}

impl<T: AsRef<str>> From<T> for ValueType {
    fn from(s: T) -> Self {
        match s.as_ref() {
            "none" => Self::None,
            "string" => Self::String,
            "list" => Self::List,
            "set" => Self::Set,
            "zset" => Self::ZSet,
            "hash" => Self::Hash,
            "stream" => Self::Stream,
            s => Self::Unknown(s.to_string()),
        }
    }
}

impl From<ValueType> for String {
    fn from(v: ValueType) -> Self {
        match v {
            ValueType::None => "none".to_string(),
            ValueType::String => "string".to_string(),
            ValueType::List => "list".to_string(),
            ValueType::Set => "set".to_string(),
            ValueType::ZSet => "zset".to_string(),
            ValueType::Hash => "hash".to_string(),
            ValueType::Stream => "stream".to_string(),
            ValueType::Unknown(s) => s,
        }
    }
}

impl FromValkeyValue for ValueType {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        match value {
            ValkeyValue::SimpleString(s) => Ok(s.into()),
            other => Err(to_glide_error(
                other,
                "Value type should be a simple string.",
            )),
        }
    }
}

#[cfg(test)]
mod value_type_tests {
    use super::*;

    #[test]
    fn value_type_round_trips_known_names() {
        for name in ["none", "string", "list", "set", "zset", "hash", "stream"] {
            let value_type = ValueType::from(name);
            assert!(!matches!(value_type, ValueType::Unknown(_)), "{name}");
            assert_eq!(String::from(value_type), name);
        }
    }

    #[test]
    fn value_type_keeps_unknown_names() {
        assert_eq!(
            ValueType::from("vectorset"),
            ValueType::Unknown("vectorset".to_string())
        );
        assert_eq!(String::from(ValueType::from("vectorset")), "vectorset");
    }

    #[test]
    fn value_type_decodes_simple_string_only() {
        let decoded =
            ValueType::from_owned_valkey_value(ValkeyValue::SimpleString("zset".into())).unwrap();
        assert_eq!(decoded, ValueType::ZSet);
        assert!(
            ValueType::from_owned_valkey_value(ValkeyValue::BulkString("zset".into())).is_err()
        );
    }
}

// --- IntegerReplyOrNoOp -------------------------------------------------------------------------

/// A reply that is either a non-negative integer,
/// or a negative sentinel indicating a no-op.
///
/// Mirrors redis-rs's `IntegerReplyOrNoOp`.
#[derive(Debug, PartialEq, Clone)]
#[non_exhaustive]
pub enum IntegerReplyOrNoOp {
    /// A non-negative integer reply.
    IntegerReply(usize),
    /// The key or field exists but the operation does not apply to it (`-1`).
    ExistsButNotRelevant,
    /// The key or field does not exist (`-2`).
    NotExists,
}

impl IntegerReplyOrNoOp {
    /// Returns the raw integer reply.
    pub fn raw(&self) -> isize {
        match self {
            Self::IntegerReply(s) => *s as isize,
            Self::ExistsButNotRelevant => -1,
            Self::NotExists => -2,
        }
    }
}

impl FromValkeyValue for IntegerReplyOrNoOp {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        match value {
            ValkeyValue::Int(-2) => Ok(Self::NotExists),
            ValkeyValue::Int(-1) => Ok(Self::ExistsButNotRelevant),
            ValkeyValue::Int(s) => Ok(Self::IntegerReply(s as usize)),
            other => Err(to_glide_error(other, "Value should be an integer.")),
        }
    }
}

impl PartialEq<isize> for IntegerReplyOrNoOp {
    fn eq(&self, other: &isize) -> bool {
        self.raw() == *other
    }
}

impl PartialEq<usize> for IntegerReplyOrNoOp {
    fn eq(&self, other: &usize) -> bool {
        matches!(self, Self::IntegerReply(s) if s == other)
    }
}

impl PartialEq<i32> for IntegerReplyOrNoOp {
    fn eq(&self, other: &i32) -> bool {
        self.raw() as i32 == *other
    }
}

impl PartialEq<u32> for IntegerReplyOrNoOp {
    fn eq(&self, other: &u32) -> bool {
        matches!(self, Self::IntegerReply(s) if *s as u32 == *other)
    }
}

#[cfg(test)]
mod integer_reply_or_noop_tests {
    use super::*;

    #[test]
    fn integer_reply_or_no_op_decodes_sentinels() {
        assert_eq!(decode(-2), IntegerReplyOrNoOp::NotExists);
        assert_eq!(decode(-1), IntegerReplyOrNoOp::ExistsButNotRelevant);
        assert_eq!(decode(0), IntegerReplyOrNoOp::IntegerReply(0));
        assert_eq!(decode(42), IntegerReplyOrNoOp::IntegerReply(42));
        assert!(IntegerReplyOrNoOp::from_owned_valkey_value(ValkeyValue::Nil).is_err());
    }

    #[test]
    fn integer_reply_or_no_op_raw_and_comparisons() {
        assert_eq!(IntegerReplyOrNoOp::NotExists.raw(), -2);
        assert_eq!(IntegerReplyOrNoOp::ExistsButNotRelevant.raw(), -1);
        assert_eq!(IntegerReplyOrNoOp::IntegerReply(7).raw(), 7);
        assert_eq!(IntegerReplyOrNoOp::NotExists, -2isize);
        assert_eq!(IntegerReplyOrNoOp::ExistsButNotRelevant, -1i32);
        assert_eq!(IntegerReplyOrNoOp::IntegerReply(7), 7usize);
        assert_eq!(IntegerReplyOrNoOp::IntegerReply(7), 7u32);
        assert_ne!(IntegerReplyOrNoOp::NotExists, 2usize);
    }

    fn decode(i: i64) -> IntegerReplyOrNoOp {
        IntegerReplyOrNoOp::from_owned_valkey_value(ValkeyValue::Int(i)).unwrap()
    }
}
