// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Valkey command argument encoding.
//!
//! [`ToValkeyArgs`] encodes Rust values into command arguments,
//! using [`ValkeyWrite`] to avoid any intermediate allocation.

use bytes::Bytes;

// ---- ValkeyWrite ----

/// A writer that command arguments are written into.
///
/// Mirrors redis-rs's `RedisWrite`.
pub trait ValkeyWrite {
    /// Append a single argument's bytes.
    fn write_arg(&mut self, arg: &[u8]);

    /// Append a single argument formatted via [`std::fmt::Display`].
    fn write_arg_fmt(&mut self, arg: impl std::fmt::Display) {
        self.write_arg(arg.to_string().as_bytes())
    }
}

impl ValkeyWrite for Vec<Vec<u8>> {
    fn write_arg(&mut self, arg: &[u8]) {
        self.push(arg.to_owned());
    }

    fn write_arg_fmt(&mut self, arg: impl std::fmt::Display) {
        self.push(arg.to_string().into_bytes())
    }
}

impl ValkeyWrite for crate::cmd::Cmd {
    fn write_arg(&mut self, arg: &[u8]) {
        redis::RedisWrite::write_arg(self.as_redis_mut(), arg);
    }

    fn write_arg_fmt(&mut self, arg: impl std::fmt::Display) {
        redis::RedisWrite::write_arg_fmt(self.as_redis_mut(), arg);
    }
}

// ---- ToValkeyArgs ----

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
///
/// Implemented for the standard argument types:
/// - integers
/// - non-zero integers
/// - floats
/// - byte slices
/// - booleans
/// - strings
/// - bytes
/// - vectors
/// - slices
/// - arrays
/// - options
/// - references
/// - standard maps and sets
/// - tuples
///
/// Implement it for your own types to pass them directly as command arguments.
pub trait ToValkeyArgs {
    /// Writes this value's argument encoding to `out`.
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W);

    /// Returns this value's argument encoding as a vector.
    fn to_valkey_args(&self) -> Vec<Vec<u8>> {
        let mut out = Vec::new();
        self.write_valkey_args(&mut out);
        out
    }

    /// The value's numeric behavior.
    /// Defaults to `NonNumeric`.
    fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
        ValkeyNumericBehavior::NonNumeric
    }

    /// Whether this value encodes to exactly one argument.
    /// Defaults to `true`.
    fn is_single_arg(&self) -> bool {
        true
    }

    /// Writes this slice's argument encoding to `out`.
    #[doc(hidden)]
    fn write_valkey_args_from_slice<W: ?Sized + ValkeyWrite>(items: &[Self], out: &mut W)
    where
        Self: Sized,
    {
        Self::write_valkey_args_from_iter(items.iter(), out);
    }

    /// Writes this iterator's argument encoding to `out`.
    #[doc(hidden)]
    fn write_valkey_args_from_iter<'a, I, W: ?Sized + ValkeyWrite>(items: I, out: &mut W)
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
    fn is_single_slice_arg(items: &[Self]) -> bool
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
            fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
                out.write_arg_fmt(self);
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
            fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
                out.write_arg_fmt(self);
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
            fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
                let mut buf = ryu::Buffer::new();
                out.write_arg(buf.format(*self).as_bytes());
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
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg_fmt(self);
    }

    // A byte slice encodes as a single binary argument, so `Vec<u8>`/`&[u8]`
    // pass through unsplit.
    fn write_valkey_args_from_slice<W: ?Sized + ValkeyWrite>(items: &[u8], out: &mut W) {
        out.write_arg(items);
    }

    fn is_single_slice_arg(_items: &[u8]) -> bool {
        true
    }
}

/// Encodes a `bool` as a command argument.
impl ToValkeyArgs for bool {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(if *self { b"1" } else { b"0" });
    }
}

/// Encodes a `String` as a command argument.
impl ToValkeyArgs for String {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(self.as_bytes());
    }
}

/// Encodes a `&str` as a command argument.
impl ToValkeyArgs for &str {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(self.as_bytes());
    }
}

/// Encodes a `Bytes` as a command argument.
impl ToValkeyArgs for Bytes {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        out.write_arg(self.as_ref());
    }
}

/// Encodes a `Vec` as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for Vec<T> {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_slice(self, out);
    }

    fn is_single_arg(&self) -> bool {
        T::is_single_slice_arg(&self[..])
    }
}

/// Encodes a slice as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for &[T] {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_slice(self, out);
    }

    fn is_single_arg(&self) -> bool {
        T::is_single_slice_arg(self)
    }
}

/// Encodes a fixed-size array as command arguments.
impl<T: ToValkeyArgs, const N: usize> ToValkeyArgs for &[T; N] {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_slice(self.as_slice(), out);
    }

    fn is_single_arg(&self) -> bool {
        T::is_single_slice_arg(self.as_slice())
    }
}

/// Encodes an `Option` as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for Option<T> {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
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
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        (*self).write_valkey_args(out);
    }

    fn describe_numeric_behavior(&self) -> ValkeyNumericBehavior {
        (*self).describe_numeric_behavior()
    }

    fn is_single_arg(&self) -> bool {
        (*self).is_single_arg()
    }
}

/// Encodes a `HashSet`'s members as command arguments.
impl<T: ToValkeyArgs + std::cmp::Eq + std::hash::Hash, S: std::hash::BuildHasher> ToValkeyArgs
    for std::collections::HashSet<T, S>
{
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_iter(self.iter(), out);
    }

    fn is_single_arg(&self) -> bool {
        self.len() <= 1
    }
}

/// Encodes a `BTreeSet`'s members as command arguments.
impl<T: ToValkeyArgs + std::cmp::Eq + std::hash::Hash + Ord> ToValkeyArgs
    for std::collections::BTreeSet<T>
{
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_iter(self.iter(), out);
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
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
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
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
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
            fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
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
mod tests {
    use super::*;

    // Values to encode.
    const TEXT: &[u8] = b"key";
    const BINARY: &[u8] = &[0u8, 1, 2, 255, 0, 42];
    const NUMBER: i64 = -7;

    #[test]
    fn vec_writer() {
        let mut out: Vec<Vec<u8>> = Vec::new();
        out.write_arg(TEXT);
        out.write_arg(BINARY);
        out.write_arg_fmt(NUMBER);

        assert_eq!(out, vec![TEXT.to_vec(), BINARY.to_vec(), b"-7".to_vec()]);
    }

    #[test]
    fn cmd_writer() {
        let mut out = crate::cmd::Cmd::new();
        out.write_arg(TEXT);
        out.write_arg(BINARY);
        out.write_arg_fmt(NUMBER);

        assert_eq!(
            out.as_redis().get_packed_command(),
            crate::cmd::Cmd::new()
                .arg(TEXT)
                .arg(BINARY)
                .arg(NUMBER)
                .as_redis()
                .get_packed_command()
        );
    }
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

        // Byte slices
        same!(b"raw".to_vec());
        same!(&b"raw"[..]);

        // Booleans
        same!(true);
        same!(false);

        // Strings
        same!("hello");
        same!(String::from("world"));

        // Sequences
        same!(vec!["a", "b", "c"]);
        same!(&["a", "b"][..]);
        same!(&["a", "b"]);
        same!(&[("f1", 1i64), ("f2", 2i64)][..]);

        // Options
        same!(Some(5i64));
        same!(Option::<i64>::None);

        // References
        same!(&5i64);
        same!(&"x");

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
        // Integers
        assert!(1i8.is_single_arg());
        assert!(1i64.is_single_arg());
        assert!(1usize.is_single_arg());

        // Non-zero integers
        assert!(core::num::NonZeroU8::new(1).unwrap().is_single_arg());

        // Floats
        assert!(1.5f64.is_single_arg());

        // Byte slices
        assert!(b"bytes".to_vec().is_single_arg());
        assert!(Vec::<u8>::new().is_single_arg());
        assert!((&b"bytes"[..]).is_single_arg());

        // Booleans
        assert!(true.is_single_arg());

        // Strings
        assert!("k".is_single_arg());
        assert!(String::from("k").is_single_arg());

        // Bytes
        assert!(Bytes::from_static(b"b").is_single_arg());

        // Sequences
        assert!(vec!["one"].is_single_arg());
        assert!(!vec!["a", "b"].is_single_arg());
        assert!(!Vec::<&str>::new().is_single_arg());
        assert!((&["one"][..]).is_single_arg());
        assert!(!(&["a", "b"][..]).is_single_arg());
        assert!((&["one"]).is_single_arg());
        assert!(!(&["a", "b"]).is_single_arg());

        // Options
        assert!(!Option::<i64>::None.is_single_arg());
        assert!(Some(1i64).is_single_arg());

        // References
        assert!((&1i64).is_single_arg());
        assert!(!(&vec!["a", "b"]).is_single_arg());

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
        use ValkeyNumericBehavior::{NonNumeric, NumberIsFloat, NumberIsInteger};
        macro_rules! num {
            ($v:expr, $b:expr) => {
                assert_eq!(
                    $v.describe_numeric_behavior(),
                    $b,
                    "numeric behavior of {:?}",
                    $v
                );
            };
        }

        // Integers
        num!(1i8, NumberIsInteger);
        num!(2i16, NumberIsInteger);
        num!(3i32, NumberIsInteger);
        num!(4i64, NumberIsInteger);
        num!(5isize, NumberIsInteger);
        num!(6u16, NumberIsInteger);
        num!(7u32, NumberIsInteger);
        num!(8u64, NumberIsInteger);
        num!(9usize, NumberIsInteger);

        // Non-zero integers
        num!(core::num::NonZeroI8::new(-1).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroI16::new(-2).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroI32::new(-3).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroI64::new(-4).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroIsize::new(-5).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroU8::new(1).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroU16::new(2).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroU32::new(3).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroU64::new(4).unwrap(), NumberIsInteger);
        num!(core::num::NonZeroUsize::new(5).unwrap(), NumberIsInteger);

        // Floats
        num!(1.5f32, NumberIsFloat);
        num!(1.5f64, NumberIsFloat);

        // Byte slices. `u8` is a byte, not an integer (matching redis-rs), so
        // it is non-numeric.
        num!(0u8, NonNumeric);
        num!(b"raw".to_vec(), NonNumeric);
        num!(&b"raw"[..], NonNumeric);

        // Booleans
        num!(true, NonNumeric);

        // Strings
        num!("hello", NonNumeric);
        num!(String::from("world"), NonNumeric);

        // Bytes
        num!(Bytes::from_static(b"b"), NonNumeric);

        // Sequences
        num!(vec!["a", "b"], NonNumeric);
        num!(&["a", "b"][..], NonNumeric);
        num!(&["a", "b"], NonNumeric);

        // Options
        num!(Some(5i64), NumberIsInteger);
        num!(Some(1.5f64), NumberIsFloat);
        num!(Some("x"), NonNumeric);
        num!(Option::<f64>::None, NonNumeric);

        // References
        num!(&5i64, NumberIsInteger);
        num!(&1.5f64, NumberIsFloat);
        num!(&&5i64, NumberIsInteger);
        num!(&"x", NonNumeric);

        // Maps and sets
        num!(&*HASH_SET_2, NonNumeric);
        num!(&*BTREE_SET_2, NonNumeric);
        num!(&*HASH_MAP_2, NonNumeric);
        num!(&*BTREE_MAP_2, NonNumeric);

        // Tuples
        num!((1,), NonNumeric);
        num!((1, 2), NonNumeric);
        num!((1, 2, 3), NonNumeric);
    }
}
