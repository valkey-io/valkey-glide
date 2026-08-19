// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Helpers for converting `redis::Value` replies into typed Rust values.
//!
//! `glide-core` already normalizes many replies (RESP3 maps/doubles/booleans,
//! Nil handling, etc.) inside `send_command`, so these helpers are thin wrappers
//! over the `redis` fork's `FromRedisValue` machinery with Glide-friendly signatures.

use crate::error::{GlideError, Result};
use bytes::Bytes;
use redis::{FromRedisValue, Value};

/// Convert a raw [`Value`] into any type implementing [`FromRedisValue`].
pub fn from_value<T: FromRedisValue>(value: Value) -> Result<T> {
    redis::from_owned_redis_value(value).map_err(GlideError::from)
}

/// Convert a [`Value`] into `Option<Bytes>` (Nil → `None`).
pub fn to_opt_bytes(value: Value) -> Result<Option<Bytes>> {
    match value {
        Value::Nil => Ok(None),
        other => Ok(Some(bytes_from_value(other)?)),
    }
}

/// Convert a [`Value`] into `Bytes`, accepting the various string-shaped RESP2/RESP3
/// replies (bulk, simple, verbatim, OK) as well as numbers.
pub fn to_bytes(value: Value) -> Result<Bytes> {
    bytes_from_value(value)
}

fn bytes_from_value(value: Value) -> Result<Bytes> {
    match value {
        Value::BulkString(b) => Ok(Bytes::from(b)),
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
pub fn to_string(value: Value) -> Result<String> {
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
pub fn to_opt_string(value: Value) -> Result<Option<String>> {
    match value {
        Value::Nil => Ok(None),
        other => Ok(Some(to_string(other)?)),
    }
}

/// Convert a [`Value`] into an `i64`.
pub fn to_i64(value: Value) -> Result<i64> {
    from_value(value)
}

/// Convert a [`Value`] into an `f64`.
pub fn to_f64(value: Value) -> Result<f64> {
    from_value(value)
}

/// Convert a [`Value`] into an `Option<f64>` (Nil → `None`).
pub fn to_opt_f64(value: Value) -> Result<Option<f64>> {
    match value {
        Value::Nil => Ok(None),
        other => Ok(Some(from_value(other)?)),
    }
}

/// Convert an integer reply into `bool` (`1` → true, `0` → false). Also accepts
/// RESP3 boolean replies.
pub fn to_bool(value: Value) -> Result<bool> {
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
pub fn to_unit(_value: Value) -> Result<()> {
    Ok(())
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
        let v = Value::BulkString(b"hello".to_vec());
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
}
