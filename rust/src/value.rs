// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Valkey reply value types.
//!
//! [`ValkeyValue`] represents a value returned by the Valkey server,
//! which [`FromValkeyValue`] decodes it into concrete Rust types.

use crate::ValkeyResult;
use crate::error::GlideError;
use bytes::Bytes;
use num_bigint::BigInt;
use redis::{Value, VerbatimFormat};

/// A value returned by the Valkey server.
/// Covers all RESP2/RESP3 value types.
/// Decode into concrete Rust types via [`FromValkeyValue`].
///
/// Mirrors redis-rs's `Value`.
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

/// The text format of a [`ValkeyValue::VerbatimString`].
///
/// Mirrors redis-rs's `VerbatimFormat` type.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ValkeyVerbatimFormat {
    /// A format string other than the ones below.
    Unknown(String),

    /// Markdown (`mkd`).
    Markdown,

    /// Plain text (`txt`).
    Text,
}

/// An error reply.
///
/// Mirrors redis-rs's `ServerError`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ValkeyServerError {
    code: String,
    detail: Option<String>,
}

impl ValkeyServerError {
    /// The error code (e.g. `WRONGTYPE`, `MOVED`).
    pub fn err_code(&self) -> &str {
        &self.code
    }

    /// The human-readable detail.
    pub fn details(&self) -> Option<&str> {
        self.detail.as_deref()
    }
}

impl ValkeyVerbatimFormat {
    fn from_redis(format: VerbatimFormat) -> Self {
        match format {
            VerbatimFormat::Unknown(s) => ValkeyVerbatimFormat::Unknown(s),
            VerbatimFormat::Markdown => ValkeyVerbatimFormat::Markdown,
            VerbatimFormat::Text => ValkeyVerbatimFormat::Text,
        }
    }
}

impl ValkeyValue {
    pub(crate) fn from_redis(value: Value) -> ValkeyResult<Self> {
        let pairs = |ps: Vec<(Value, Value)>| -> ValkeyResult<Vec<(ValkeyValue, ValkeyValue)>> {
            ps.into_iter()
                .map(|(k, v)| Ok((ValkeyValue::from_redis(k)?, ValkeyValue::from_redis(v)?)))
                .collect()
        };
        Ok(match value {
            Value::Nil => ValkeyValue::Nil,
            Value::Int(i) => ValkeyValue::Int(i),
            Value::BulkString(b) => ValkeyValue::BulkString(b),
            Value::Array(items) => ValkeyValue::Array(
                items
                    .into_iter()
                    .map(ValkeyValue::from_redis)
                    .collect::<ValkeyResult<_>>()?,
            ),
            Value::SimpleString(s) => ValkeyValue::SimpleString(s),
            Value::Okay => ValkeyValue::Okay,
            Value::Map(ps) => ValkeyValue::Map(pairs(ps)?),
            Value::Set(items) => ValkeyValue::Set(
                items
                    .into_iter()
                    .map(ValkeyValue::from_redis)
                    .collect::<ValkeyResult<_>>()?,
            ),
            Value::Double(d) => ValkeyValue::Double(d),
            Value::Boolean(b) => ValkeyValue::Boolean(b),
            Value::VerbatimString { format, text } => ValkeyValue::VerbatimString {
                format: ValkeyVerbatimFormat::from_redis(format),
                text,
            },
            Value::BigNumber(n) => ValkeyValue::BigNumber(n),
            Value::ServerError(e) => ValkeyValue::ServerError(ValkeyServerError {
                code: e.err_code().to_string(),
                detail: e.details().map(str::to_string),
            }),

            // There a currently no known cases where a response would include
            // an attribute value, so we match redis-rs's behavior: return the
            // attribute data and drop the corresponding metadata.
            Value::Attribute { data, .. } => ValkeyValue::from_redis(*data)?,

            // Push values should never occur in command replies (they should be handled
            // by glide-core), so raise an error if we encounter one.
            Value::Push { .. } => {
                return Err(GlideError::Request(
                    "unexpected Push value in command reply".into(),
                ));
            }
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use redis::{ErrorKind, RedisError};

    // ---- ValkeyValue::from_redis ----------------------------------------

    #[test]
    fn from_redis_maps_variants() {
        let rv = Value::Array(vec![
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
                data: Box::new(Value::Int(42)),
                attributes: vec![(Value::SimpleString("k".into()), Value::Int(1))],
            },
        ]);
        let vv = ValkeyValue::from_redis(rv).unwrap();
        assert_eq!(
            vv,
            ValkeyValue::Array(vec![
                ValkeyValue::Nil,
                ValkeyValue::Int(-5),
                ValkeyValue::BulkString(Bytes::from_static(b"hi")),
                ValkeyValue::SimpleString("s".into()),
                ValkeyValue::Okay,
                ValkeyValue::Double(1.5),
                ValkeyValue::Boolean(true),
                ValkeyValue::BigNumber(BigInt::from(i64::MAX) + 1),
                ValkeyValue::VerbatimString {
                    format: ValkeyVerbatimFormat::Markdown,
                    text: "md".into(),
                },
                ValkeyValue::Map(vec![(ValkeyValue::Int(1), ValkeyValue::Int(2))]),
                ValkeyValue::Set(vec![ValkeyValue::Int(9)]),
                ValkeyValue::Int(42),
            ])
        );
    }

    #[test]
    fn from_redis_reads_server_error_code_and_detail() {
        let error = RedisError::from((ErrorKind::ResponseError, "boom", "detail".to_string()));
        let v = Value::ServerError(error.into());
        match ValkeyValue::from_redis(v).unwrap() {
            ValkeyValue::ServerError(e) => {
                assert_eq!(e.err_code(), "ERR");
                assert_eq!(e.details(), Some("boom detail"));
            }
            other => panic!("expected ServerError, got {other:?}"),
        }
    }

    #[test]
    fn from_redis_push_value_error() {
        let v = Value::Push {
            kind: redis::PushKind::Message,
            data: vec![Value::Int(1)],
        };
        assert!(ValkeyValue::from_redis(v).is_err());
    }
}

// ==== FromValkeyValue ====================================================

/// Converts a [`ValkeyValue`] reply into a Rust type.
pub trait FromValkeyValue: Sized {
    /// Converts an owned [`ValkeyValue`] into `Self`.
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self>;

    /// Converts a borrowed [`ValkeyValue`] into `Self`.
    fn from_valkey_value(value: &ValkeyValue) -> ValkeyResult<Self> {
        Self::from_owned_valkey_value(value.clone())
    }

    /// Converts a `Vec<ValkeyValue>` into a `Vec<Self>`.
    #[doc(hidden)]
    fn from_owned_valkey_values(items: Vec<ValkeyValue>) -> ValkeyResult<Vec<Self>> {
        items
            .into_iter()
            .map(Self::from_owned_valkey_value)
            .collect()
    }

    /// Converts a `Vec<u8>` into a `Vec<Self>`.
    #[doc(hidden)]
    fn from_owned_byte_vec(bytes: Vec<u8>) -> ValkeyResult<Vec<Self>> {
        Self::from_owned_valkey_value(ValkeyValue::BulkString(Bytes::from(bytes))).map(|v| vec![v])
    }
}

/// Build a error for the given `ValkeyValue` and message.
fn to_glide_error(value: ValkeyValue, msg: &str) -> GlideError {
    GlideError::Request(format!("{msg} (response was {value:?})"))
}

/// Decodes a `ValkeyValue` into a map.
/// Returns an error with the given target type on failure.
fn into_map<K, V, M>(value: ValkeyValue, target_type: &str) -> ValkeyResult<M>
where
    K: FromValkeyValue,
    V: FromValkeyValue,
    M: FromIterator<(K, V)>,
{
    // Normalize the reply into key/value pairs.
    let pairs: Vec<(ValkeyValue, ValkeyValue)> = match value {
        ValkeyValue::Nil => Vec::new(),
        ValkeyValue::Map(pairs) => pairs,
        ValkeyValue::Array(items) if items.len() % 2 == 0 => {
            let mut it = items.into_iter();
            let mut pairs = Vec::with_capacity(it.len() / 2);
            while let (Some(k), Some(v)) = (it.next(), it.next()) {
                pairs.push((k, v));
            }
            pairs
        }
        other => return Err(to_glide_error(other, target_type)),
    };

    pairs
        .into_iter()
        .map(|(k, v)| {
            Ok((
                K::from_owned_valkey_value(k)?,
                V::from_owned_valkey_value(v)?,
            ))
        })
        .collect()
}

/// Decodes a `ValkeyValue` into a set.
/// Returns an error with the given target type on failure.
fn into_set<T, C>(value: ValkeyValue, target_type: &str) -> ValkeyResult<C>
where
    T: FromValkeyValue,
    C: FromIterator<T>,
{
    let items = match value {
        ValkeyValue::Array(items) => items,
        ValkeyValue::Set(items) => items,
        ValkeyValue::Nil => Vec::new(),
        other => return Err(to_glide_error(other, target_type)),
    };

    items.into_iter().map(T::from_owned_valkey_value).collect()
}

/// Converts a `ValkeyValue` to a number.
macro_rules! impl_from_valkey_num {
    ($($t:ty),* $(,)?) => {$(
        impl FromValkeyValue for $t {
            fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<$t> {
                match value {
                    ValkeyValue::Int(v) => Ok(v as $t),
                    ValkeyValue::Double(v) => Ok(v as $t),
                    ValkeyValue::SimpleString(s) => s
                        .parse::<$t>()
                        .map_err(|_| GlideError::Request("Could not convert from string.".into())),
                    ValkeyValue::BulkString(bytes) => std::str::from_utf8(&bytes)
                        .ok()
                        .and_then(|s| s.parse::<$t>().ok())
                        .ok_or_else(|| GlideError::Request("Could not convert from string.".into())),
                    other => Err(to_glide_error(other, "Response type not convertible to numeric.")),
                }
            }
        }
    )*};
}

impl_from_valkey_num!(
    i8, i16, i32, i64, i128, u16, u32, u64, u128, f32, f64, isize, usize
);

impl FromValkeyValue for u8 {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<u8> {
        match value {
            ValkeyValue::Int(v) => Ok(v as u8),
            ValkeyValue::Double(v) => Ok(v as u8),
            ValkeyValue::SimpleString(s) => s
                .parse::<u8>()
                .map_err(|_| GlideError::Request("Could not convert from string.".into())),
            ValkeyValue::BulkString(bytes) => std::str::from_utf8(&bytes)
                .ok()
                .and_then(|s| s.parse::<u8>().ok())
                .ok_or_else(|| GlideError::Request("Could not convert from string.".into())),
            other => Err(to_glide_error(
                other,
                "Response type not convertible to numeric.",
            )),
        }
    }

    // Specialization that makes `Vec<u8>` consume raw bulk-string bytes directly.
    fn from_owned_byte_vec(bytes: Vec<u8>) -> ValkeyResult<Vec<u8>> {
        Ok(bytes)
    }
}

/// Converts a `ValkeyValue` to a boolean.
impl FromValkeyValue for bool {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<bool> {
        match value {
            ValkeyValue::Nil => Ok(false),
            ValkeyValue::Int(v) => Ok(v != 0),
            ValkeyValue::Boolean(b) => Ok(b),
            ValkeyValue::Okay => Ok(true),
            ValkeyValue::SimpleString(ref s) if s == "1" => Ok(true),
            ValkeyValue::SimpleString(ref s) if s == "0" => Ok(false),
            ValkeyValue::BulkString(ref b) if b.as_ref() == b"1" => Ok(true),
            ValkeyValue::BulkString(ref b) if b.as_ref() == b"0" => Ok(false),
            other => Err(to_glide_error(other, "Response type not bool compatible.")),
        }
    }
}

/// Converts a `ValkeyValue` to a string.
impl FromValkeyValue for String {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<String> {
        match value {
            ValkeyValue::BulkString(bytes) => String::from_utf8(bytes.to_vec())
                .map_err(|_| GlideError::Request("Response was not valid UTF-8.".into())),
            ValkeyValue::Okay => Ok("OK".to_string()),
            ValkeyValue::SimpleString(s) => Ok(s),
            ValkeyValue::VerbatimString { text, .. } => Ok(text),
            ValkeyValue::Double(v) => Ok(v.to_string()),
            ValkeyValue::Int(v) => Ok(v.to_string()),
            other => Err(to_glide_error(
                other,
                "Response type not string compatible.",
            )),
        }
    }
}

/// Converts a `ValkeyValue` into bytes.
impl FromValkeyValue for Bytes {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Bytes> {
        match value {
            ValkeyValue::BulkString(bytes) => Ok(bytes),
            ValkeyValue::SimpleString(s) => Ok(Bytes::from(s.into_bytes())),
            ValkeyValue::VerbatimString { text, .. } => Ok(Bytes::from(text.into_bytes())),
            other => Err(to_glide_error(other, "Response type not byte compatible.")),
        }
    }
}

impl FromValkeyValue for () {
    fn from_owned_valkey_value(_value: ValkeyValue) -> ValkeyResult<()> {
        Ok(())
    }
}

/// Converts a `ValkeyValue` to an `Option<T>` value.
impl<T: FromValkeyValue> FromValkeyValue for Option<T> {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Option<T>> {
        match value {
            ValkeyValue::Nil => Ok(None),
            other => Ok(Some(T::from_owned_valkey_value(other)?)),
        }
    }
}

/// Converts a `ValkeyValue` to an `Vec<T>` value.
impl<T: FromValkeyValue> FromValkeyValue for Vec<T> {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Vec<T>> {
        match value {
            ValkeyValue::BulkString(bytes) => T::from_owned_byte_vec(bytes.to_vec()),
            ValkeyValue::Array(items) => T::from_owned_valkey_values(items),
            ValkeyValue::Set(items) => T::from_owned_valkey_values(items),
            ValkeyValue::Map(pairs) => {
                // Each pair decodes as one element (used when `T` is a tuple).
                let mut out = Vec::with_capacity(pairs.len());
                for (k, v) in pairs {
                    out.push(T::from_owned_valkey_value(ValkeyValue::Map(vec![(k, v)]))?);
                }
                Ok(out)
            }
            ValkeyValue::Nil => Ok(Vec::new()),
            other => Err(to_glide_error(
                other,
                "Response type not vector compatible.",
            )),
        }
    }
}

/// Converts a `ValkeyValue` to a `HashMap<K, V, S>` value.
impl<K, V, S> FromValkeyValue for std::collections::HashMap<K, V, S>
where
    K: FromValkeyValue + std::cmp::Eq + std::hash::Hash,
    V: FromValkeyValue,
    S: std::hash::BuildHasher + Default,
{
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        into_map(value, "Response type not hashmap compatible")
    }
}

/// Converts a `ValkeyValue` to a `HashSet<T, S>` value.
impl<T, S> FromValkeyValue for std::collections::HashSet<T, S>
where
    T: FromValkeyValue + std::cmp::Eq + std::hash::Hash,
    S: std::hash::BuildHasher + Default,
{
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        into_set(value, "Response type not hashset compatible")
    }
}

/// Converts a `ValkeyValue` to a `BTreeMap<K, V>` value.
impl<K, V> FromValkeyValue for std::collections::BTreeMap<K, V>
where
    K: FromValkeyValue + std::cmp::Ord,
    V: FromValkeyValue,
{
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        into_map(value, "Response type not btreemap compatible")
    }
}

/// Converts a `ValkeyValue` to a `BTreeSet<T>` value.
impl<T> FromValkeyValue for std::collections::BTreeSet<T>
where
    T: FromValkeyValue + std::cmp::Ord,
{
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        into_set(value, "Response type not btreeset compatible")
    }
}

/// Converts a `ValkeyValue` to a fixed-size `[T; N]` array.
impl<T: FromValkeyValue, const N: usize> FromValkeyValue for [T; N] {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<[T; N]> {
        let items = Vec::<T>::from_owned_valkey_value(value)?;
        let len = items.len();
        items.try_into().map_err(|_| {
            GlideError::Request(format!(
                "Array response of wrong dimension (expected {N}, got {len})"
            ))
        })
    }
}

/// Converts a `ValkeyValue` to a boxed slice `Box<[T]>`.
impl<T: FromValkeyValue> FromValkeyValue for Box<[T]> {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Box<[T]>> {
        Ok(Vec::<T>::from_owned_valkey_value(value)?.into_boxed_slice())
    }
}

/// Converts a `ValkeyValue` to a reference-counted slice `Arc<[T]>`.
impl<T: FromValkeyValue> FromValkeyValue for std::sync::Arc<[T]> {
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<std::sync::Arc<[T]>> {
        Ok(Vec::<T>::from_owned_valkey_value(value)?.into())
    }
}

/// Converts a `ValkeyValue` to a tuple.
macro_rules! impl_from_valkey_tuple {
    ($( ($($name:ident),+) ),+ $(,)?) => {$(
        #[doc(hidden)]
        impl<$($name: FromValkeyValue),*> FromValkeyValue for ($($name,)*) {
            #[allow(non_snake_case, unused_variables)]
            fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<($($name,)*)> {
                match value {
                    ValkeyValue::Array(mut items) => {
                        let mut n = 0;
                        $(let $name = (); n += 1;)*
                        if items.len() != n {
                            return Err(GlideError::Request("Array response of wrong dimension".into()));
                        }
                        let mut i = 0;
                        Ok(($({ let $name = (); $name::from_owned_valkey_value(
                            std::mem::replace(&mut items[{ i += 1; i - 1 }], ValkeyValue::Nil))? },)*))
                    }
                    ValkeyValue::Map(items) => {
                        let mut n = 0;
                        $(let $name = (); n += 1;)*

                        // Only support decoding a map with one key-value pair as a 2-element tuple.
                        if n != 2 || items.len() != 1 {
                            return Err(GlideError::Request("Map response of wrong dimension".into()));
                        }

                        let (k, v) = items.into_iter().next().expect("exactly one entry checked above");
                        let mut flat = [k, v];
                        let mut i = 0;
                        Ok(($({ let $name = (); $name::from_owned_valkey_value(
                            std::mem::replace(&mut flat[{ i += 1; i - 1 }], ValkeyValue::Nil))? },)*))
                    }
                    other => Err(to_glide_error(other, "Not an Array response")),
                }
            }
            #[allow(non_snake_case, unused_variables)]
            fn from_owned_valkey_values(items: Vec<ValkeyValue>) -> ValkeyResult<Vec<($($name,)*)>> {
                let mut n = 0;
                $(let $name = (); n += 1;)*

                if items.is_empty() {
                    return Ok(Vec::new());
                }

                let mut items = items;

                // First try array-of-arrays: each element is itself an N-array.
                if items
                    .iter()
                    .all(|it| matches!(it, ValkeyValue::Array(ch) if ch.len() == n))
                {
                    let mut rv = Vec::with_capacity(items.len());
                    for item in &mut items {
                        if let ValkeyValue::Array(ch) = item {
                            if let [$($name),*] = &mut ch[..] {
                                rv.push(($($name::from_owned_valkey_value(
                                    std::mem::replace($name, ValkeyValue::Nil))?,)*));
                            }
                        }
                    }

                    return Ok(rv);
                }

                // Otherwise treat the flat sequence as chunks of N.
                if items.len() % n != 0 {
                    return Err(GlideError::Request("Array response of wrong dimension".into()));
                }

                let mut rv = Vec::with_capacity(items.len() / n);
                for chunk in items.chunks_mut(n) {
                    if let [$($name),*] = chunk {
                        rv.push(($($name::from_owned_valkey_value(
                            std::mem::replace($name, ValkeyValue::Nil))?,)*));
                    }
                }

                Ok(rv)
            }
        }
    )+};
}

impl_from_valkey_tuple! {
    (T1),
    (T1, T2),
    (T1, T2, T3),
    (T1, T2, T3, T4),
    (T1, T2, T3, T4, T5),
    (T1, T2, T3, T4, T5, T6),
    (T1, T2, T3, T4, T5, T6, T7),
    (T1, T2, T3, T4, T5, T6, T7, T8),
    (T1, T2, T3, T4, T5, T6, T7, T8, T9),
    (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10),
    (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11),
    (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12),
}

#[cfg(test)]
mod from_valkey_value_tests {
    use super::*;
    use std::collections::{HashMap, HashSet};

    // `ValkeyValue`s constants for testing.
    const NIL: ValkeyValue = ValkeyValue::Nil;
    const INT: ValkeyValue = ValkeyValue::Int(7);
    const OKAY: ValkeyValue = ValkeyValue::Okay;
    const DOUBLE: ValkeyValue = ValkeyValue::Double(1.5);
    const BOOLEAN: ValkeyValue = ValkeyValue::Boolean(true);
    const BULK: ValkeyValue = ValkeyValue::BulkString(Bytes::from_static(b"hi"));
    const BYTES_A: ValkeyValue = ValkeyValue::BulkString(Bytes::from_static(b"a"));
    const BYTES_B: ValkeyValue = ValkeyValue::BulkString(Bytes::from_static(b"b"));
    const BYTES_1: ValkeyValue = ValkeyValue::BulkString(Bytes::from_static(b"1"));
    const BYTES_2: ValkeyValue = ValkeyValue::BulkString(Bytes::from_static(b"2"));

    // `ValkeyValue`s functions for testing.
    // (`SimpleString`/`VerbatimString` carry a `String`, so they can't be `const`.)
    fn simple() -> ValkeyValue {
        ValkeyValue::SimpleString("s".into())
    }
    fn verbatim() -> ValkeyValue {
        ValkeyValue::VerbatimString {
            format: ValkeyVerbatimFormat::Text,
            text: "vt".into(),
        }
    }

    fn decode<T: FromValkeyValue>(v: ValkeyValue) -> T {
        T::from_owned_valkey_value(v).unwrap()
    }

    #[test]
    fn from_owned_valkey_value_numeric() {
        let bulk_string_numeric = ValkeyValue::BulkString(Bytes::from_static(b"42"));
        let simple_string_numeric = ValkeyValue::SimpleString("100".into());

        assert_eq!(decode::<i64>(INT), 7);
        assert_eq!(decode::<i64>(DOUBLE), 1); // 1.5 truncates to 1
        assert_eq!(decode::<i64>(bulk_string_numeric), 42);
        assert_eq!(decode::<i64>(simple_string_numeric), 100);
        assert_eq!(decode::<u64>(INT), 7);

        // Non-numeric input is a decode error.
        assert!(i64::from_owned_valkey_value(OKAY).is_err());
        assert!(i64::from_owned_valkey_value(BULK).is_err());
    }

    #[test]
    fn from_owned_valkey_value_bool() {
        let bulk_string_bool = ValkeyValue::BulkString(Bytes::from_static(b"0"));

        assert!(decode::<bool>(INT)); // any non-zero int is true
        assert!(!decode::<bool>(ValkeyValue::Int(0)));
        assert!(decode::<bool>(BOOLEAN));
        assert!(decode::<bool>(OKAY));
        assert!(!decode::<bool>(NIL));
        assert!(decode::<bool>(ValkeyValue::SimpleString("1".into())));
        assert!(!decode::<bool>(bulk_string_bool));
    }

    #[test]
    fn from_owned_valkey_value_string() {
        assert_eq!(decode::<String>(BULK), "hi");
        assert_eq!(decode::<String>(simple()), "s");
        assert_eq!(decode::<String>(OKAY), "OK");
        assert_eq!(decode::<String>(INT), "7");
        assert_eq!(decode::<String>(DOUBLE), "1.5");
        assert_eq!(decode::<String>(verbatim()), "vt");
    }

    #[test]
    fn from_owned_valkey_value_bytes() {
        assert_eq!(decode::<Bytes>(BULK), Bytes::from_static(b"hi"));
        assert_eq!(decode::<Bytes>(simple()), Bytes::from_static(b"s"));
        assert_eq!(decode::<Bytes>(verbatim()), Bytes::from_static(b"vt"));
    }

    #[test]
    fn from_owned_valkey_value_u8() {
        assert_eq!(decode::<u8>(INT), 7);

        // `Vec<u8>` from a bulk string consumes the raw bytes directly.
        assert_eq!(decode::<Vec<u8>>(BULK), b"hi".to_vec());
        assert_eq!(
            decode::<Vec<u8>>(ValkeyValue::BulkString(Bytes::from_static(b""))),
            Vec::<u8>::new()
        );

        // `Vec<u8>` from an array decodes element-wise.
        assert_eq!(
            decode::<Vec<u8>>(ValkeyValue::Array(vec![INT, INT])),
            vec![7u8, 7u8]
        );
    }

    #[test]
    fn from_owned_valkey_value_vec() {
        let v = ValkeyValue::Array(vec![BYTES_A, BYTES_B]);
        assert_eq!(
            decode::<Vec<String>>(v),
            vec!["a".to_string(), "b".to_string()]
        );

        assert_eq!(decode::<Vec<String>>(BULK), vec!["hi".to_string()]);
        assert_eq!(decode::<Vec<String>>(NIL), Vec::<String>::new());
    }

    #[test]
    fn from_owned_valkey_value_array() {
        let v = ValkeyValue::Array(vec![BYTES_A, BYTES_B]);
        let a: [String; 2] = decode(v.clone());
        assert_eq!(a, ["a".to_string(), "b".to_string()]);

        // A length mismatch is a decode error.
        assert!(<[String; 3]>::from_owned_valkey_value(v).is_err());
    }

    #[test]
    fn from_owned_valkey_value_boxed_slice() {
        let v = ValkeyValue::Array(vec![BYTES_A, BYTES_B]);
        let b: Box<[String]> = decode(v);
        assert_eq!(&b[..], ["a".to_string(), "b".to_string()]);
    }

    #[test]
    fn from_owned_valkey_value_arc_slice() {
        let v = ValkeyValue::Array(vec![BYTES_A, BYTES_B]);
        let a: std::sync::Arc<[String]> = decode(v);
        assert_eq!(&a[..], ["a".to_string(), "b".to_string()]);
    }

    #[test]
    fn from_owned_valkey_value_option() {
        let none: Option<String> = decode(NIL);
        assert_eq!(none, None);

        let some: Option<String> = decode(BULK);
        assert_eq!(some.as_deref(), Some("hi"));
    }

    #[test]
    fn from_owned_valkey_value_unit() {
        let _: () = decode(OKAY);
        let _: () = decode(NIL);
    }

    #[test]
    fn from_owned_valkey_value_hashmap() {
        let from_map = ValkeyValue::Map(vec![(BULK, BULK)]);
        let m: HashMap<String, String> = decode(from_map);
        assert_eq!(m.get("hi").map(String::as_str), Some("hi"));

        let from_flat = ValkeyValue::Array(vec![BYTES_A, BYTES_1, BYTES_B, BYTES_2]);
        let m: HashMap<String, i64> = decode(from_flat);
        assert_eq!(m.get("a"), Some(&1));
        assert_eq!(m.get("b"), Some(&2));

        let empty: HashMap<String, String> = decode(NIL);
        assert!(empty.is_empty());
    }

    #[test]
    fn from_owned_valkey_value_btreemap() {
        use std::collections::BTreeMap;

        let from_map = ValkeyValue::Map(vec![(BYTES_A, BYTES_1), (BYTES_B, BYTES_2)]);
        let m: BTreeMap<String, i64> = decode(from_map);
        assert_eq!(m.get("a"), Some(&1));
        assert_eq!(m.get("b"), Some(&2));

        let from_array = ValkeyValue::Array(vec![BYTES_A, BYTES_1, BYTES_B, BYTES_2]);
        let m: BTreeMap<String, i64> = decode(from_array);
        assert_eq!(m.get("a"), Some(&1));
        assert_eq!(m.get("b"), Some(&2));

        let empty: BTreeMap<String, String> = decode(NIL);
        assert!(empty.is_empty());
    }

    #[test]
    fn from_owned_valkey_value_hashset() {
        let v = ValkeyValue::Set(vec![BYTES_A, BYTES_B]);
        let s: HashSet<String> = decode(v);
        assert_eq!(s, HashSet::from(["a".to_string(), "b".to_string()]));
    }

    #[test]
    fn from_owned_valkey_value_btreeset() {
        use std::collections::BTreeSet;

        let v = ValkeyValue::Set(vec![BYTES_A, BYTES_B]);
        let s: BTreeSet<String> = decode(v);
        assert_eq!(s, BTreeSet::from(["a".to_string(), "b".to_string()]));
    }

    #[test]
    fn from_owned_valkey_value_tuple() {
        let t: (String, i64) = decode(ValkeyValue::Array(vec![BULK, INT]));
        assert_eq!(t, ("hi".to_string(), 7));

        let t: (String, i64, f64) = decode(ValkeyValue::Array(vec![BULK, INT, DOUBLE]));
        assert_eq!(t, ("hi".to_string(), 7, 1.5));

        // Conversion from array with unexpected number of entries fails.
        assert!(<(String, i64)>::from_owned_valkey_value(ValkeyValue::Array(vec![INT])).is_err());

        let t: (String, i64) = decode(ValkeyValue::Map(vec![(BULK, INT)]));
        assert_eq!(t, ("hi".to_string(), 7));

        // Conversion from map with unexpected number of entries fails.
        let map_2 = ValkeyValue::Map(vec![(BULK, INT), (BYTES_A, INT)]);
        assert!(<(String, i64)>::from_owned_valkey_value(map_2).is_err());
    }

    #[test]
    fn from_owned_valkey_value_vec_of_pairs() {
        // Nested array
        let nested = ValkeyValue::Array(vec![
            ValkeyValue::Array(vec![BULK, DOUBLE]),
            ValkeyValue::Array(vec![BULK, DOUBLE]),
        ]);
        let pairs: Vec<(String, f64)> = decode(nested);
        assert_eq!(
            pairs,
            vec![("hi".to_string(), 1.5), ("hi".to_string(), 1.5)]
        );

        // Flat array
        let flat = ValkeyValue::Array(vec![BULK, DOUBLE, BULK, DOUBLE]);
        let pairs: Vec<(String, f64)> = decode(flat);
        assert_eq!(
            pairs,
            vec![("hi".to_string(), 1.5), ("hi".to_string(), 1.5)]
        );

        // Conversion from flat array with odd number of entries fails.
        assert!(
            <Vec<(String, f64)>>::from_owned_valkey_value(ValkeyValue::Array(vec![
                BULK, DOUBLE, BULK
            ]))
            .is_err()
        );
    }

    #[test]
    fn from_owned_valkey_value_server_error() {
        let server_err = ValkeyServerError {
            code: "WRONGTYPE".into(),
            detail: Some("nope".into()),
        };
        let value = ValkeyValue::ServerError(server_err);
        let request_err = i64::from_owned_valkey_value(value).unwrap_err();

        assert_eq!(request_err.class_name(), "RequestError");
        assert!(request_err.message().contains("WRONGTYPE"));
    }
}
