// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! TODO #7024: Add module description.

use crate::ValkeyResult;
use crate::error::GlideError;
use bytes::Bytes;
use num_bigint::BigInt;
use redis::{ErrorKind, FromRedisValue, RedisError, ToRedisArgs, Value, VerbatimFormat};

/// Convert a raw [`Value`] into any type implementing [`FromRedisValue`].
// TODO #7024: do not expose.
pub fn from_value<T: FromRedisValue>(value: Value) -> ValkeyResult<T> {
    redis::from_owned_redis_value(value).map_err(GlideError::from_redis_error)
}

/// Convert a [`Value`] into `Option<Bytes>` (Nil → `None`).
// TODO #7024: do not expose.
pub fn to_opt_bytes(value: Value) -> ValkeyResult<Option<Bytes>> {
    match value {
        Value::Nil => Ok(None),
        other => Ok(Some(bytes_from_value(other)?)),
    }
}

/// Convert a [`Value`] into `Bytes`, accepting the various string-shaped RESP2/RESP3
/// replies (bulk, simple, verbatim, OK) as well as numbers.
// TODO #7024: do not expose.
pub fn to_bytes(value: Value) -> ValkeyResult<Bytes> {
    bytes_from_value(value)
}

// TODO #7024: do not expose.
fn bytes_from_value(value: Value) -> ValkeyResult<Bytes> {
    match value {
        Value::BulkString(b) => Ok(b),
        Value::SimpleString(s) => Ok(Bytes::from(s.into_bytes())),
        Value::VerbatimString { text, .. } => Ok(Bytes::from(text.into_bytes())),
        Value::Okay => Ok(Bytes::from_static(b"OK")),
        Value::Int(i) => Ok(Bytes::from(i.to_string().into_bytes())),
        Value::Double(f) => Ok(Bytes::from(f.to_string().into_bytes())),
        Value::Boolean(b) => Ok(Bytes::from(if b { "1" } else { "0" })),
        other => {
            let v: Vec<u8> = from_value(other)?;
            Ok(Bytes::from(v))
        }
    }
}

/// Convert a [`Value`] into a UTF-8 `String`.
// TODO #7024: do not expose.
pub fn to_string(value: Value) -> ValkeyResult<String> {
    match value {
        Value::SimpleString(s) => Ok(s),
        Value::VerbatimString { text, .. } => Ok(text),
        Value::Okay => Ok("OK".to_string()),
        Value::Int(i) => Ok(i.to_string()),
        Value::Double(f) => Ok(f.to_string()),
        other => from_value(other),
    }
}

/// Convert a [`Value`] into an `Option<String>` (Nil → `None`).
// TODO #7024: do not expose.
pub fn to_opt_string(value: Value) -> ValkeyResult<Option<String>> {
    match value {
        Value::Nil => Ok(None),
        other => Ok(Some(to_string(other)?)),
    }
}

/// Convert a [`Value`] into an `i64`.
// TODO #7024: do not expose.
pub fn to_i64(value: Value) -> ValkeyResult<i64> {
    from_value(value)
}

/// Convert a [`Value`] into an `f64`.
// TODO #7024: do not expose.
pub fn to_f64(value: Value) -> ValkeyResult<f64> {
    from_value(value)
}

/// Convert a [`Value`] into an `Option<f64>` (Nil → `None`).
// TODO #7024: do not expose.
pub fn to_opt_f64(value: Value) -> ValkeyResult<Option<f64>> {
    match value {
        Value::Nil => Ok(None),
        other => Ok(Some(from_value(other)?)),
    }
}

/// Convert an integer reply into `bool` (`1` → true, `0` → false). Also accepts
/// RESP3 boolean replies.
// TODO #7024: do not expose.
pub fn to_bool(value: Value) -> ValkeyResult<bool> {
    match value {
        Value::Boolean(b) => Ok(b),
        Value::Int(i) => Ok(i != 0),
        other => {
            let i: i64 = from_value(other)?;
            Ok(i != 0)
        }
    }
}

/// Convert an "OK"/simple-string reply into `()`.
// TODO #7024: do not expose.
pub fn to_unit(_value: Value) -> ValkeyResult<()> {
    Ok(())
}

/// A value returned by the server.
///
/// Mirrors redis-rs's `Value` type.
///
/// TODO #7024: Revisit this documentation.
/// Mirrors every RESP2/RESP3 reply shape. Decode into concrete Rust types with
/// the `to_*`/`from_*` helpers in this module or via [`FromValkeyValue`].
#[derive(Clone, Debug, PartialEq)]
pub enum ValkeyValue {
    /// A nil reply.
    Nil,

    /// An integer reply.
    Int(i64),

    /// Binary-safe string data.
    BulkString(Bytes),

    /// An array of nested values.
    Array(Vec<ValkeyValue>),

    /// A simple string, not binary-safe.
    SimpleString(String),

    /// The status reply `OK`.
    Okay,

    /// An unordered key/value map (RESP3).
    Map(Vec<(ValkeyValue, ValkeyValue)>),

    /// A value carrying RESP3 attribute metadata.
    Attribute {
        /// The value the attributes annotate.
        data: Box<ValkeyValue>,
        /// The attached attribute key/value pairs.
        attributes: Vec<(ValkeyValue, ValkeyValue)>,
    },

    /// An unordered set (RESP3).
    Set(Vec<ValkeyValue>),

    /// A double-precision float reply (RESP3).
    Double(f64),

    /// A boolean reply (RESP3).
    Boolean(bool),

    /// A verbatim string carrying a format hint (RESP3).
    VerbatimString {
        /// The declared text format.
        format: ValkeyVerbatimFormat,
        /// The string contents.
        text: String,
    },

    /// A number outside the range of a signed 64-bit integer (RESP3).
    BigNumber(BigInt),

    /// An error reply from the server (e.g. a failed element within a reply).
    ServerError(ValkeyServerError),
}

/// The declared text format of a [`ValkeyValue::VerbatimString`].
///
/// Mirrors redis-rs's `VerbatimFormat` type.
/// TODO #7024: Revisit this documentation.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ValkeyVerbatimFormat {
    /// A format string other than the ones below.
    Unknown(String),

    /// Markdown (`mkd`).
    Markdown,

    /// Plain text (`txt`).
    Text,
}

// TODO #7024: revisit — flat struct vs mirroring redis's ServerError variants
// (ExtensionError/KnownError + ServerErrorKind, unnameable from this crate today).
/// An error reply carried in-band as a [`ValkeyValue::ServerError`].
///
/// Mirrors redis-rs's `ServerError` type.
/// TODO #7024: Revisit this documentation.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ValkeyServerError {
    /// The error code (the first word of the reply, e.g. `WRONGTYPE`, `MOVED`).
    pub code: String,

    /// The human-readable detail, if any.
    pub detail: Option<String>,
}

impl ValkeyVerbatimFormat {
    // TODO #7024: drop this allow; from_redis is used once glide_send_owned returns ValkeyValue (Phase 3).
    #[allow(dead_code)]
    fn from_redis(format: VerbatimFormat) -> Self {
        match format {
            VerbatimFormat::Unknown(s) => ValkeyVerbatimFormat::Unknown(s),
            VerbatimFormat::Markdown => ValkeyVerbatimFormat::Markdown,
            VerbatimFormat::Text => ValkeyVerbatimFormat::Text,
        }
    }

    fn into_redis(self) -> VerbatimFormat {
        match self {
            ValkeyVerbatimFormat::Unknown(s) => VerbatimFormat::Unknown(s),
            ValkeyVerbatimFormat::Markdown => VerbatimFormat::Markdown,
            ValkeyVerbatimFormat::Text => VerbatimFormat::Text,
        }
    }
}

impl ValkeyValue {
    // TODO #7024: drop this allow; from_redis is used once glide_send_owned returns ValkeyValue (Phase 3).
    #[allow(dead_code)]
    pub(crate) fn from_redis(value: Value) -> Self {
        let pairs = |ps: Vec<(Value, Value)>| {
            ps.into_iter()
                .map(|(k, v)| (ValkeyValue::from_redis(k), ValkeyValue::from_redis(v)))
                .collect()
        };
        match value {
            Value::Nil => ValkeyValue::Nil,
            Value::Int(i) => ValkeyValue::Int(i),
            Value::BulkString(b) => ValkeyValue::BulkString(b),
            Value::Array(items) => {
                ValkeyValue::Array(items.into_iter().map(ValkeyValue::from_redis).collect())
            }
            Value::SimpleString(s) => ValkeyValue::SimpleString(s),
            Value::Okay => ValkeyValue::Okay,
            Value::Map(ps) => ValkeyValue::Map(pairs(ps)),
            Value::Attribute { data, attributes } => ValkeyValue::Attribute {
                data: Box::new(ValkeyValue::from_redis(*data)),
                attributes: pairs(attributes),
            },
            Value::Set(items) => {
                ValkeyValue::Set(items.into_iter().map(ValkeyValue::from_redis).collect())
            }
            Value::Double(d) => ValkeyValue::Double(d),
            Value::Boolean(b) => ValkeyValue::Boolean(b),
            Value::VerbatimString { format, text } => ValkeyValue::VerbatimString {
                format: ValkeyVerbatimFormat::from_redis(format),
                text,
            },
            Value::BigNumber(n) => ValkeyValue::BigNumber(n),
            // Push frames are delivered via the pub/sub channel, never as a
            // command reply; map defensively to the payload array.
            Value::Push { data, .. } => {
                ValkeyValue::Array(data.into_iter().map(ValkeyValue::from_redis).collect())
            }
            Value::ServerError(e) => ValkeyValue::ServerError(ValkeyServerError {
                code: e.err_code().to_string(),
                detail: e.details().map(str::to_string),
            }),
        }
    }

    // TODO #7024: remove this; decode ValkeyValue natively in FromValkeyValue
    // (erroring on ServerError nodes directly) instead of round-tripping through
    // redis::Value, so no ServerError→redis reconstruction is needed (Phase 3).
    pub(crate) fn into_redis(self) -> Value {
        let pairs = |ps: Vec<(ValkeyValue, ValkeyValue)>| {
            ps.into_iter()
                .map(|(k, v)| (k.into_redis(), v.into_redis()))
                .collect()
        };
        match self {
            ValkeyValue::Nil => Value::Nil,
            ValkeyValue::Int(i) => Value::Int(i),
            ValkeyValue::BulkString(b) => Value::BulkString(b),
            ValkeyValue::Array(items) => {
                Value::Array(items.into_iter().map(ValkeyValue::into_redis).collect())
            }
            ValkeyValue::SimpleString(s) => Value::SimpleString(s),
            ValkeyValue::Okay => Value::Okay,
            ValkeyValue::Map(ps) => Value::Map(pairs(ps)),
            ValkeyValue::Attribute { data, attributes } => Value::Attribute {
                data: Box::new(data.into_redis()),
                attributes: pairs(attributes),
            },
            ValkeyValue::Set(items) => {
                Value::Set(items.into_iter().map(ValkeyValue::into_redis).collect())
            }
            ValkeyValue::Double(d) => Value::Double(d),
            ValkeyValue::Boolean(b) => Value::Boolean(b),
            ValkeyValue::VerbatimString { format, text } => Value::VerbatimString {
                format: format.into_redis(),
                text,
            },
            ValkeyValue::BigNumber(n) => Value::BigNumber(n),
            ValkeyValue::ServerError(e) => {
                let redis_err = match e.detail {
                    Some(detail) => RedisError::from((ErrorKind::ResponseError, "", detail)),
                    None => RedisError::from((ErrorKind::ResponseError, "server error")),
                };
                Value::ServerError(redis_err.into())
            }
        }
    }
}

/// Encodes a value into command arguments: the Valkey-branded replacement for
/// redis-rs's `ToRedisArgs`.
///
/// Implemented for every type that implements the redis fork's `ToRedisArgs`, so
/// argument types migrated from redis-rs work unchanged. Implement it for your
/// own types to pass them directly as command arguments.
pub trait ToValkeyArgs {
    /// Append this value's argument encoding to `out`.
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>);

    /// Return this value's argument encoding as a fresh vector.
    fn to_valkey_args(&self) -> Vec<Vec<u8>> {
        let mut out = Vec::new();
        self.write_valkey_args(&mut out);
        out
    }
}

// TODO #7024: revisit blanket impl vs explicit standard-type impls (blanket
// blocks downstream user impls) when this becomes a command bound (Phase 3).
impl<T: ToRedisArgs> ToValkeyArgs for T {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.extend(self.to_redis_args());
    }
}

/// Decodes a [`ValkeyValue`] reply into a Rust type: the Valkey-branded
/// replacement for redis-rs's `FromRedisValue`.
///
/// Implemented for every type that implements the redis fork's `FromRedisValue`,
/// so return types migrated from redis-rs work unchanged. Implement it for your
/// own types to decode replies into them directly.
pub trait FromValkeyValue: Sized {
    /// Decode an owned [`ValkeyValue`] into `Self`.
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self>;

    /// Decode a borrowed [`ValkeyValue`] into `Self`.
    fn from_valkey_value(value: &ValkeyValue) -> ValkeyResult<Self> {
        Self::from_owned_valkey_value(value.clone())
    }
}

// TODO #7024: revisit blanket impl vs explicit standard-type impls (blanket
// blocks downstream user impls) when this becomes a command bound (Phase 3).
impl<T: FromRedisValue> FromValkeyValue for T {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        redis::from_owned_redis_value(value.into_redis()).map_err(GlideError::from_redis_error)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn opt_bytes_nil_is_none() {
        assert_eq!(to_opt_bytes(Value::Nil).unwrap(), None);
    }

    #[test]
    fn opt_bytes_bulkstring() {
        let v = Value::BulkString(b"hello".to_vec().into());
        assert_eq!(to_opt_bytes(v).unwrap(), Some(Bytes::from_static(b"hello")));
    }

    #[test]
    fn int_and_bool() {
        assert_eq!(to_i64(Value::Int(42)).unwrap(), 42);
        assert!(to_bool(Value::Int(1)).unwrap());
        assert!(!to_bool(Value::Int(0)).unwrap());
        assert!(to_bool(Value::Boolean(true)).unwrap());
    }

    #[test]
    fn opt_f64_nil_and_value() {
        assert_eq!(to_opt_f64(Value::Nil).unwrap(), None);
        assert_eq!(to_opt_f64(Value::Double(1.5)).unwrap(), Some(1.5));
    }

    #[test]
    fn string_from_simple() {
        assert_eq!(
            to_string(Value::SimpleString("PONG".into())).unwrap(),
            "PONG"
        );
    }

    // ---- ValkeyValue conversions ----------------------------------------

    #[test]
    fn valkey_value_round_trip() {
        let original = Value::Array(vec![
            Value::Nil,
            Value::Int(-5),
            Value::BulkString(Bytes::from_static(b"hi")),
            Value::SimpleString("s".into()),
            Value::Okay,
            Value::Double(1.5),
            Value::Boolean(true),
            Value::BigNumber(BigInt::from(i64::MAX) + 1),
            Value::VerbatimString {
                format: VerbatimFormat::Markdown,
                text: "md".into(),
            },
            Value::Map(vec![(Value::Int(1), Value::Int(2))]),
            Value::Set(vec![Value::Int(9)]),
            Value::Attribute {
                data: Box::new(Value::Int(1)),
                attributes: vec![(Value::SimpleString("k".into()), Value::Int(2))],
            },
        ]);
        let round = ValkeyValue::from_redis(original.clone()).into_redis();
        assert_eq!(round, original);
    }

    // TODO #7024: Reevaluate whether needed.
    #[test]
    fn server_error_reads_code_and_detail() {
        let v = Value::ServerError(
            RedisError::from((ErrorKind::ResponseError, "boom", "detail".to_string())).into(),
        );
        match ValkeyValue::from_redis(v) {
            ValkeyValue::ServerError(e) => {
                assert_eq!(e.code, "ERR");
                assert_eq!(e.detail.as_deref(), Some("boom detail"));
            }
            other => panic!("expected ServerError, got {other:?}"),
        }
    }

    // TODO #7024: remove with ValkeyValue::into_redis when FromValkeyValue decodes natively (Phase 3).
    #[test]
    fn server_error_into_redis_is_a_server_error() {
        let v = ValkeyValue::ServerError(ValkeyServerError {
            code: "ERR".into(),
            detail: Some("nope".into()),
        });
        assert!(matches!(v.into_redis(), Value::ServerError(_)));
    }

    // ---- ToValkeyArgs / FromValkeyValue ---------------------------------

    // TODO #7024: one representative type suffices for the type-agnostic blanket impl;
    // broaden to a case per standard type if the impl becomes explicit per-type (Phase 3).
    #[test]
    fn to_valkey_args_matches_redis_encoding() {
        assert_eq!(42i64.to_valkey_args(), 42i64.to_redis_args());
        let mut out = Vec::new();
        "hello".write_valkey_args(&mut out);
        assert_eq!(out, vec![b"hello".to_vec()]);
    }

    // TODO #7024: one representative type suffices for the type-agnostic blanket impl;
    // broaden to a case per standard type if the impl becomes explicit per-type (Phase 3).
    #[test]
    fn from_valkey_value_decodes_standard_types() {
        assert_eq!(
            i64::from_owned_valkey_value(ValkeyValue::Int(7)).unwrap(),
            7
        );
        let s: String = FromValkeyValue::from_owned_valkey_value(ValkeyValue::BulkString(
            Bytes::from_static(b"hi"),
        ))
        .unwrap();
        assert_eq!(s, "hi");
        let none: Option<String> = FromValkeyValue::from_valkey_value(&ValkeyValue::Nil).unwrap();
        assert_eq!(none, None);
    }
}
