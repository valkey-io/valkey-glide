// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! TODO #7024: Add module description.

use crate::ValkeyResult;
use crate::error::GlideError;
use bytes::Bytes;
use num_bigint::BigInt;
use redis::{FromRedisValue, Value, VerbatimFormat};

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
    fn from_redis(format: VerbatimFormat) -> Self {
        match format {
            VerbatimFormat::Unknown(s) => ValkeyVerbatimFormat::Unknown(s),
            VerbatimFormat::Markdown => ValkeyVerbatimFormat::Markdown,
            VerbatimFormat::Text => ValkeyVerbatimFormat::Text,
        }
    }
}

impl ValkeyValue {
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
            Value::ServerError(e) => ValkeyValue::ServerError(ValkeyServerError {
                code: e.err_code().to_string(),
                detail: e.details().map(str::to_string),
            }),

            Value::Attribute { .. } => unreachable!("Attributes are not supported."),
            Value::Push { .. } => unreachable!("Commands should not return Push values."),
        }
    }
}

// ==== ToValkeyArgs =======================================================

/// Describes how a value behaves in a numeric context.
///
/// Mirrors redis-rs's `NumericBehavior` type.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ValkeyNumericBehavior {
    /// The value is not a number.
    NonNumeric,
    /// The value is an integer.
    NumberIsInteger,
    /// The value is a floating-point number.
    NumberIsFloat,
}

/// Encodes a value into command arguments.
/// Implemented for the standard argument types (integers, floats, `bool`,
/// strings, byte slices, [`Bytes`], `Option`, slices, `Vec`, tuples, and the
/// standard maps/sets). Implement it for your own types to pass them directly
/// as command arguments.
pub trait ToValkeyArgs {
    /// Append this value's argument(s) encoding to `out`.
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>);

    /// Return this value's argument encoding as a vector.
    fn to_valkey_args(&self) -> Vec<Vec<u8>> {
        let mut out = Vec::new();
        self.write_valkey_args(&mut out);
        out
    }

    /// The value's numeric behavior.
    /// Defaults to non-numeric.
    fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
        ValkeyNumericBehavior::NonNumeric
    }

    /// Whether this value encodes to exactly one argument.
    fn is_single_arg(&self) -> bool {
        true
    }

    /// Encode a slice of `Self`. Exists as a workaround for the lack of
    /// specialization: `u8` overrides it to emit a single binary argument.
    #[doc(hidden)]
    fn write_args_from_slice(items: &[Self], out: &mut Vec<Vec<u8>>)
    where
        Self: Sized,
    {
        Self::make_arg_iter_ref(items.iter(), out);
    }

    /// Encode each item of an iterator of `&Self`.
    #[doc(hidden)]
    fn make_arg_iter_ref<'a, I>(items: I, out: &mut Vec<Vec<u8>>)
    where
        I: Iterator<Item = &'a Self>,
        Self: Sized + 'a,
    {
        for item in items {
            item.write_valkey_args(out);
        }
    }

    /// Whether a slice of `Self` encodes to exactly one argument.
    #[doc(hidden)]
    fn is_single_vec_arg(items: &[Self]) -> bool
    where
        Self: Sized,
    {
        items.len() == 1 && items[0].is_single_arg()
    }
}

/// Encodes an integer as a command argument.
macro_rules! impl_to_valkey_args_int {
    ($($t:ty),* $(,)?) => {$(
        impl ToValkeyArgs for $t {
            fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
                out.push(self.to_string().into_bytes());
            }
            fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
                ValkeyNumericBehavior::NumberIsInteger
            }
        }
    )*};
}

impl_to_valkey_args_int!(i8, i16, u16, i32, u32, i64, u64, isize, usize);

/// Encodes a non-zero integer as a command argument.
macro_rules! impl_to_valkey_args_nonzero {
    ($($t:ty),* $(,)?) => {$(
        impl ToValkeyArgs for $t {
            fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
                out.push(self.get().to_string().into_bytes());
            }
            fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
                ValkeyNumericBehavior::NumberIsInteger
            }
        }
    )*};
}

impl_to_valkey_args_nonzero!(
    core::num::NonZeroU8,
    core::num::NonZeroI8,
    core::num::NonZeroU16,
    core::num::NonZeroI16,
    core::num::NonZeroU32,
    core::num::NonZeroI32,
    core::num::NonZeroU64,
    core::num::NonZeroI64,
    core::num::NonZeroUsize,
    core::num::NonZeroIsize,
);

/// Encodes a float as a command argument.
macro_rules! impl_to_valkey_args_float {
    ($($t:ty),* $(,)?) => {$(
        impl ToValkeyArgs for $t {
            fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
                let mut buf = ryu::Buffer::new();
                out.push(buf.format(*self).as_bytes().to_vec());
            }
            fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
                ValkeyNumericBehavior::NumberIsFloat
            }
        }
    )*};
}

impl_to_valkey_args_float!(f32, f64);

/// Encodes a `u8` as a command argument.
impl ToValkeyArgs for u8 {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.push(self.to_string().into_bytes());
    }

    // A byte slice encodes as a single binary argument, so `Vec<u8>`/`&[u8]`
    // pass through unsplit.
    fn write_args_from_slice(items: &[u8], out: &mut Vec<Vec<u8>>) {
        out.push(items.to_vec());
    }

    fn is_single_vec_arg(_items: &[u8]) -> bool {
        true
    }
}

/// Encodes a `bool` as a command argument.
impl ToValkeyArgs for bool {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.push(if *self { b"1".to_vec() } else { b"0".to_vec() });
    }
}

/// Encodes a `String` as a command argument.
impl ToValkeyArgs for String {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.push(self.as_bytes().to_vec());
    }
}

/// Encodes a `&str` as a command argument.
impl ToValkeyArgs for &str {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.push(self.as_bytes().to_vec());
    }
}

/// Encodes a `Bytes` as a command argument.
impl ToValkeyArgs for Bytes {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        out.push(self.to_vec());
    }
}

/// Encodes a `Vec` as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for Vec<T> {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        T::write_args_from_slice(self, out);
    }

    fn is_single_arg(&self) -> bool {
        T::is_single_vec_arg(&self[..])
    }
}

/// Encodes a slice as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for &[T] {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        T::write_args_from_slice(self, out);
    }

    fn is_single_arg(&self) -> bool {
        T::is_single_vec_arg(self)
    }
}

/// Encodes a fixed-size array as command arguments.
impl<T: ToValkeyArgs, const N: usize> ToValkeyArgs for &[T; N] {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        T::write_args_from_slice(self.as_slice(), out);
    }

    fn is_single_arg(&self) -> bool {
        T::is_single_vec_arg(self.as_slice())
    }
}

/// Encodes an `Option` as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for Option<T> {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        if let Some(x) = self {
            x.write_valkey_args(out);
        }
    }

    fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
        match self {
            Some(x) => x.describe_numeric_behavior(),
            None => ValkeyNumericBehavior::NonNumeric,
        }
    }

    fn is_single_arg(&self) -> bool {
        match self {
            Some(x) => x.is_single_arg(),
            None => false,
        }
    }
}

/// Encodes a reference as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for &T {
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        (*self).write_valkey_args(out);
    }

    fn is_single_arg(&self) -> bool {
        (*self).is_single_arg()
    }
}

/// Encodes a `HashSet`'s members as command arguments.
impl<T: ToValkeyArgs + std::cmp::Eq + std::hash::Hash, S: std::hash::BuildHasher> ToValkeyArgs
    for std::collections::HashSet<T, S>
{
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        T::make_arg_iter_ref(self.iter(), out);
    }

    fn is_single_arg(&self) -> bool {
        self.len() <= 1
    }
}

/// Encodes a `BTreeSet`'s members as command arguments.
impl<T: ToValkeyArgs + std::cmp::Eq + std::hash::Hash + Ord> ToValkeyArgs
    for std::collections::BTreeSet<T>
{
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        T::make_arg_iter_ref(self.iter(), out);
    }

    fn is_single_arg(&self) -> bool {
        self.len() <= 1
    }
}

/// Encodes a `BTreeMap` as command arguments.
impl<K: ToValkeyArgs, V: ToValkeyArgs> ToValkeyArgs for std::collections::BTreeMap<K, V>
where
    K: std::cmp::Eq + std::hash::Hash + Ord,
{
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        for (key, value) in self {
            assert!(key.is_single_arg() && value.is_single_arg());
            key.write_valkey_args(out);
            value.write_valkey_args(out);
        }
    }

    fn is_single_arg(&self) -> bool {
        self.len() <= 1
    }
}

/// Encodes a `HashMap` as command arguments.
impl<K: ToValkeyArgs, V: ToValkeyArgs, S: std::hash::BuildHasher> ToValkeyArgs
    for std::collections::HashMap<K, V, S>
where
    K: std::cmp::Eq + std::hash::Hash,
{
    fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
        for (key, value) in self {
            assert!(key.is_single_arg() && value.is_single_arg());
            key.write_valkey_args(out);
            value.write_valkey_args(out);
        }
    }

    fn is_single_arg(&self) -> bool {
        self.len() <= 1
    }
}

/// Encodes a tuple as command arguments.
macro_rules! impl_to_valkey_args_tuple {
    ($( ($($name:ident),+) ),+ $(,)?) => {$(
        #[doc(hidden)]
        impl<$($name: ToValkeyArgs),*> ToValkeyArgs for ($($name,)*) {
            #[allow(non_snake_case, unused_variables)]
            fn write_valkey_args(&self, out: &mut Vec<Vec<u8>>) {
                let ($(ref $name,)*) = *self;
                $($name.write_valkey_args(out);)*
            }
            #[allow(non_snake_case, unused_variables)]
            fn is_single_arg(&self) -> bool {
                let mut n = 0u32;
                $(let $name = (); n += 1;)*
                n == 1
            }
        }
    )+};
}

impl_to_valkey_args_tuple! {
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
mod to_valkey_args_tests {
    use super::*;
    use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
    use std::sync::LazyLock;

    static HASH_SET_0: LazyLock<HashSet<&str>> = LazyLock::new(HashSet::new);
    static HASH_SET_1: LazyLock<HashSet<&str>> = LazyLock::new(|| HashSet::from(["x"]));
    static HASH_SET_2: LazyLock<HashSet<&str>> = LazyLock::new(|| HashSet::from(["a", "b", "c"]));

    static BTREE_SET_0: LazyLock<BTreeSet<&str>> = LazyLock::new(BTreeSet::new);
    static BTREE_SET_1: LazyLock<BTreeSet<&str>> = LazyLock::new(|| BTreeSet::from(["x"]));
    static BTREE_SET_2: LazyLock<BTreeSet<&str>> =
        LazyLock::new(|| BTreeSet::from(["a", "b", "c"]));

    static HASH_MAP_0: LazyLock<HashMap<&str, i64>> = LazyLock::new(HashMap::new);
    static HASH_MAP_1: LazyLock<HashMap<&str, i64>> = LazyLock::new(|| HashMap::from([("k", 1)]));
    static HASH_MAP_2: LazyLock<HashMap<&str, i64>> =
        LazyLock::new(|| HashMap::from([("f1", 1), ("f2", 2)]));

    static BTREE_MAP_0: LazyLock<BTreeMap<&str, i64>> = LazyLock::new(BTreeMap::new);
    static BTREE_MAP_1: LazyLock<BTreeMap<&str, i64>> =
        LazyLock::new(|| BTreeMap::from([("k", 1)]));
    static BTREE_MAP_2: LazyLock<BTreeMap<&str, i64>> =
        LazyLock::new(|| BTreeMap::from([("f1", 1), ("f2", 2)]));

    #[test]
    fn to_valkey_args_matches_redis_encoding() {
        use redis::ToRedisArgs;
        macro_rules! same {
            ($v:expr) => {
                assert_eq!(
                    $v.to_valkey_args(),
                    $v.to_redis_args(),
                    "encoding of {:?}",
                    $v
                );
            };
        }

        // Integers
        same!(1i8);
        same!(2i16);
        same!(3i32);
        same!(4i64);
        same!(5isize);
        same!(0u8);
        same!(255u8);
        same!(6u16);
        same!(7u32);
        same!(8u64);
        same!(9usize);
        same!(i64::MAX);
        same!(i64::MIN);
        same!(u64::MAX);

        // Non-zero integers
        same!(core::num::NonZeroI8::new(-1).unwrap());
        same!(core::num::NonZeroI16::new(-2).unwrap());
        same!(core::num::NonZeroI32::new(-3).unwrap());
        same!(core::num::NonZeroI64::new(-4).unwrap());
        same!(core::num::NonZeroIsize::new(-5).unwrap());
        same!(core::num::NonZeroU8::new(1).unwrap());
        same!(core::num::NonZeroU16::new(2).unwrap());
        same!(core::num::NonZeroU32::new(3).unwrap());
        same!(core::num::NonZeroU64::new(4).unwrap());
        same!(core::num::NonZeroUsize::new(5).unwrap());

        // Floats
        same!(1.5f32);
        same!(0.0f64);
        same!(1.5f64);
        same!(-2.25f64);
        same!(0.1f64);
        same!(1e20f64);
        same!(-1e-20f64);
        same!(std::f64::consts::PI);

        // Strings.
        same!("hello");
        same!(String::from("world"));

        // Bytes
        same!(b"raw".to_vec());
        same!(&b"raw"[..]);

        // Bools
        same!(true);
        same!(false);

        // Sequences
        same!(vec!["a", "b", "c"]);
        same!(&["a", "b"][..]);
        same!(&["a", "b"]);
        same!(&[("f1", 1i64), ("f2", 2i64)][..]);

        // Maps and sets
        same!(&*HASH_SET_0);
        same!(&*BTREE_SET_0);
        same!(&*HASH_MAP_0);
        same!(&*BTREE_MAP_0);

        same!(&*HASH_SET_1);
        same!(&*BTREE_SET_1);
        same!(&*HASH_MAP_1);
        same!(&*BTREE_MAP_1);

        same!(&*HASH_SET_2);
        same!(&*BTREE_SET_2);
        same!(&*HASH_MAP_2);
        same!(&*BTREE_MAP_2);

        // References
        same!(&5i64);
        same!(&"x");

        // Option.
        same!(Some(5i64));
        same!(Option::<i64>::None);

        // Tuples
        same!((1,));
        same!((1, 2));
        same!((1, 2, 3));
        same!((1, 2, 3, 4));
        same!((1, 2, 3, 4, 5));
        same!((1, 2, 3, 4, 5, 6));
        same!((1, 2, 3, 4, 5, 6, 7));
        same!((1, 2, 3, 4, 5, 6, 7, 8));
        same!((1, 2, 3, 4, 5, 6, 7, 8, 9));
        same!((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        same!((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
        same!((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
    }

    /// `Bytes` is a GLIDE addition, so it has no `ToRedisArgs` equivalent.
    #[test]
    fn to_valkey_args_bytes() {
        assert_eq!(
            Bytes::from_static(b"\x00\xff data").to_valkey_args(),
            vec![b"\x00\xff data".to_vec()]
        );
    }

    #[test]
    fn is_single_arg() {
        // Scalars
        assert!(1i8.is_single_arg());
        assert!(1i64.is_single_arg());
        assert!(1usize.is_single_arg());
        assert!(core::num::NonZeroU8::new(1).unwrap().is_single_arg());
        assert!(1.5f64.is_single_arg());
        assert!(true.is_single_arg());
        assert!("k".is_single_arg());
        assert!(String::from("k").is_single_arg());
        assert!(Bytes::from_static(b"b").is_single_arg());

        // Byte sequences
        assert!(b"bytes".to_vec().is_single_arg());
        assert!(Vec::<u8>::new().is_single_arg());
        assert!((&b"bytes"[..]).is_single_arg());

        // Non-byte sequences
        assert!(vec!["one"].is_single_arg());
        assert!(!vec!["a", "b"].is_single_arg());
        assert!(!Vec::<&str>::new().is_single_arg());
        assert!((&["one"][..]).is_single_arg());
        assert!(!(&["a", "b"][..]).is_single_arg());
        assert!((&["one"]).is_single_arg());
        assert!(!(&["a", "b"]).is_single_arg());

        // Option
        assert!(!Option::<i64>::None.is_single_arg());
        assert!(Some(1i64).is_single_arg());

        // References
        assert!(1i64.is_single_arg());
        assert!(!vec!["a", "b"].is_single_arg());

        // Maps and sets
        assert!((*HASH_SET_0).is_single_arg());
        assert!((*BTREE_SET_0).is_single_arg());
        assert!((*HASH_MAP_0).is_single_arg());
        assert!((*BTREE_MAP_0).is_single_arg());

        assert!((*HASH_SET_1).is_single_arg());
        assert!((*BTREE_SET_1).is_single_arg());
        assert!((*HASH_MAP_1).is_single_arg());
        assert!((*BTREE_MAP_1).is_single_arg());

        assert!(!(*HASH_SET_2).is_single_arg());
        assert!(!(*BTREE_SET_2).is_single_arg());
        assert!(!(*HASH_MAP_2).is_single_arg());
        assert!(!(*BTREE_MAP_2).is_single_arg());

        // Tuples
        assert!((1i64,).is_single_arg());
        assert!(!(1i64, 2i64).is_single_arg());
        assert!(!(1i64, 2i64, 3i64).is_single_arg());
    }

    #[test]
    fn describe_numeric_behavior() {
        assert_eq!(
            5i64.describe_numeric_behavior(),
            ValkeyNumericBehavior::NumberIsInteger
        );
        assert_eq!(
            1.5f64.describe_numeric_behavior(),
            ValkeyNumericBehavior::NumberIsFloat
        );
        assert_eq!(
            "x".describe_numeric_behavior(),
            ValkeyNumericBehavior::NonNumeric
        );
        assert_eq!(
            Some(1.5f64).describe_numeric_behavior(),
            ValkeyNumericBehavior::NumberIsFloat
        );
        assert_eq!(
            Option::<f64>::None.describe_numeric_behavior(),
            ValkeyNumericBehavior::NonNumeric
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use redis::{ErrorKind, RedisError};

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
        ]);
        let vv = ValkeyValue::from_redis(rv);
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
            ])
        );
    }

    #[test]
    fn from_redis_reads_server_error_code_and_detail() {
        let error = RedisError::from((ErrorKind::ResponseError, "boom", "detail".to_string()));
        let v = Value::ServerError(error.into());
        match ValkeyValue::from_redis(v) {
            ValkeyValue::ServerError(e) => {
                assert_eq!(e.code, "ERR");
                assert_eq!(e.detail.as_deref(), Some("boom detail"));
            }
            other => panic!("expected ServerError, got {other:?}"),
        }
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

/// Converts a `ValkeyValue` to a `Vec<ValkeyValue>`.
fn into_sequence(value: ValkeyValue) -> Result<Vec<ValkeyValue>, ValkeyValue> {
    match value {
        ValkeyValue::Array(items) => Ok(items),
        ValkeyValue::Set(items) => Ok(items),
        ValkeyValue::Nil => Ok(Vec::new()),
        other => Err(other),
    }
}

/// Converts a `ValkeyValue` to a `Vec<(ValkeyValue, ValkeyValue)`.
fn into_pairs(value: ValkeyValue) -> Result<Vec<(ValkeyValue, ValkeyValue)>, ValkeyValue> {
    match value {
        ValkeyValue::Map(pairs) => Ok(pairs),
        ValkeyValue::Array(items) if items.len() % 2 == 0 => {
            let mut it = items.into_iter();
            let mut pairs = Vec::with_capacity(it.len() / 2);
            while let (Some(k), Some(v)) = (it.next(), it.next()) {
                pairs.push((k, v));
            }
            Ok(pairs)
        }
        other => Err(other),
    }
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
            other => Err(to_glide_error(other, "Not a bulk string")),
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
        if value == ValkeyValue::Nil {
            return Ok(Self::default());
        }
        let pairs = into_pairs(value)
            .map_err(|v| to_glide_error(v, "Response type not hashmap compatible"))?;
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
}

/// Converts a `ValkeyValue` to a `HashSet<T, S>` value.
impl<T, S> FromValkeyValue for std::collections::HashSet<T, S>
where
    T: FromValkeyValue + std::cmp::Eq + std::hash::Hash,
    S: std::hash::BuildHasher + Default,
{
    fn from_owned_valkey_value(value: ValkeyValue) -> ValkeyResult<Self> {
        let items = into_sequence(value)
            .map_err(|v| to_glide_error(v, "Response type not hashset compatible"))?;
        items.into_iter().map(T::from_owned_valkey_value).collect()
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
                        if n != 2 {
                            return Err(GlideError::Request("Map response of wrong dimension".into()));
                        }
                        let mut flat = Vec::with_capacity(items.len() * 2);
                        for (k, v) in items {
                            flat.push(k);
                            flat.push(v);
                        }
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
                // First try array-of-arrays: each element is itself an N-array.
                let mut rv = Vec::with_capacity(items.len());
                for item in &items {
                    if let ValkeyValue::Array(ch) = item {
                        if let [$($name),*] = &ch[..] {
                            rv.push(($($name::from_valkey_value($name)?,)*));
                        }
                    }
                }
                if !rv.is_empty() {
                    return Ok(rv);
                }
                // Otherwise treat the flat sequence as chunks of N.
                let mut items = items;
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
    fn from_owned_valkey_value_hashset() {
        let v = ValkeyValue::Set(vec![BYTES_A, BYTES_B]);
        let s: HashSet<String> = decode(v);
        assert_eq!(s, HashSet::from(["a".to_string(), "b".to_string()]));
    }

    #[test]
    fn from_owned_valkey_value_tuple() {
        let t: (String, i64) = decode(ValkeyValue::Array(vec![BULK, INT]));
        assert_eq!(t, ("hi".to_string(), 7));

        let t: (String, i64, f64) = decode(ValkeyValue::Array(vec![BULK, INT, DOUBLE]));
        assert_eq!(t, ("hi".to_string(), 7, 1.5));

        assert!(<(String, i64)>::from_owned_valkey_value(ValkeyValue::Array(vec![INT])).is_err());
    }

    #[test]
    fn from_owned_valkey_value_vec_of_pairs() {
        // Array of 2-element arrays (normalized shape).
        let nested = ValkeyValue::Array(vec![
            ValkeyValue::Array(vec![BULK, DOUBLE]),
            ValkeyValue::Array(vec![BULK, DOUBLE]),
        ]);
        let pairs: Vec<(String, f64)> = decode(nested);
        assert_eq!(
            pairs,
            vec![("hi".to_string(), 1.5), ("hi".to_string(), 1.5)]
        );

        // Flat sequence chunked into pairs (RESP2 shape).
        let flat = ValkeyValue::Array(vec![BULK, DOUBLE, BULK, DOUBLE]);
        let pairs: Vec<(String, f64)> = decode(flat);
        assert_eq!(
            pairs,
            vec![("hi".to_string(), 1.5), ("hi".to_string(), 1.5)]
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
