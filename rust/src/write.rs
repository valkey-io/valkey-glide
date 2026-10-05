// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Valkey command argument encoding.
//!
//! [`ToValkeyArgs`] encodes Rust values into command arguments,
//! using [`ValkeyWrite`] to avoid any intermediate allocation.

use bytes::Bytes;

// --- ValkeyWrite --------------------------------------------------------------------------------

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

// --- ToValkeyArgs -------------------------------------------------------------------------------

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
///
/// Mirrors redis-rs's `ToRedisArgs`.
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

    /// The number of command arguments this value encodes to.
    fn num_of_args(&self) -> usize {
        let mut counter = ArgCounter(0);
        self.write_valkey_args(&mut counter);
        counter.0
    }

    /// The number of command arguments a slice of `Self` encodes to.
    #[doc(hidden)]
    fn num_of_args_from_slice(items: &[Self]) -> usize
    where
        Self: Sized,
    {
        items.iter().map(Self::num_of_args).sum()
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

    fn num_of_args_from_slice(_items: &[u8]) -> usize {
        1
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

    fn num_of_args(&self) -> usize {
        T::num_of_args_from_slice(self)
    }
}

/// Encodes a slice as command arguments.
impl<T: ToValkeyArgs> ToValkeyArgs for &[T] {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_slice(self, out);
    }

    fn num_of_args(&self) -> usize {
        T::num_of_args_from_slice(self)
    }
}

/// Encodes a fixed-size array as command arguments.
impl<T: ToValkeyArgs, const N: usize> ToValkeyArgs for &[T; N] {
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_slice(self.as_slice(), out);
    }

    fn num_of_args(&self) -> usize {
        T::num_of_args_from_slice(self.as_slice())
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

    fn num_of_args(&self) -> usize {
        self.as_ref().map_or(0, ToValkeyArgs::num_of_args)
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

    fn num_of_args(&self) -> usize {
        (*self).num_of_args()
    }
}

/// Encodes a `HashSet`'s members as command arguments.
impl<T: ToValkeyArgs + std::cmp::Eq + std::hash::Hash, S: std::hash::BuildHasher> ToValkeyArgs
    for std::collections::HashSet<T, S>
{
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_iter(self.iter(), out);
    }
}

/// Encodes a `BTreeSet`'s members as command arguments.
impl<T: ToValkeyArgs + std::cmp::Eq + std::hash::Hash + Ord> ToValkeyArgs
    for std::collections::BTreeSet<T>
{
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        T::write_valkey_args_from_iter(self.iter(), out);
    }
}

/// Encodes a `BTreeMap` as command arguments.
/// Panics if a key or value does not encode as exactly one argument.
impl<K: ToValkeyArgs, V: ToValkeyArgs> ToValkeyArgs for std::collections::BTreeMap<K, V>
where
    K: std::cmp::Eq + std::hash::Hash + Ord,
{
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        for (key, value) in self {
            assert!(key.num_of_args() == 1 && value.num_of_args() == 1);
            key.write_valkey_args(out);
            value.write_valkey_args(out);
        }
    }
}

/// Encodes a `HashMap` as command arguments.
/// Panics if a key or value does not encode as exactly one argument.
impl<K: ToValkeyArgs, V: ToValkeyArgs, S: std::hash::BuildHasher> ToValkeyArgs
    for std::collections::HashMap<K, V, S>
where
    K: std::cmp::Eq + std::hash::Hash,
{
    fn write_valkey_args<W: ?Sized + ValkeyWrite>(&self, out: &mut W) {
        for (key, value) in self {
            assert!(key.num_of_args() == 1 && value.num_of_args() == 1);
            key.write_valkey_args(out);
            value.write_valkey_args(out);
        }
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

// ---- ToSingleValkeyArg -------------------------------------------------------------------------

/// Encodes a value into exactly one command argument.
///
/// Implemented for these standard argument types:
/// - integers
/// - non-zero integers
/// - floats
/// - byte slices
/// - booleans
/// - strings
/// - bytes
/// - references
///
/// Implement it for your own types to pass them directly as command arguments.
///
/// Mirrors redis-rs's `ToSingleRedisArg`.
pub trait ToSingleValkeyArg: ToValkeyArgs {}

macro_rules! impl_to_single_valkey_arg {
    ($($t:ty),* $(,)?) => {$(
        impl ToSingleValkeyArg for $t {}
    )*};
}

impl_to_single_valkey_arg!(
    i8,
    i16,
    u16,
    i32,
    u32,
    i64,
    u64,
    isize,
    usize,
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
    f32,
    f64,
    u8,
    bool,
    String,
    &str,
    Bytes,
    Vec<u8>,
    &[u8],
);

impl<const N: usize> ToSingleValkeyArg for &[u8; N] {}

impl<T: ToSingleValkeyArg> ToSingleValkeyArg for &T {}

// --- ArgCounter ---------------------------------------------------------------------------------

/// A [`ValkeyWrite`] that counts arguments without storing them.
struct ArgCounter(usize);

impl ValkeyWrite for ArgCounter {
    fn write_arg(&mut self, _arg: &[u8]) {
        self.0 += 1;
    }

    fn write_arg_fmt(&mut self, _arg: impl std::fmt::Display) {
        self.0 += 1;
    }
}

// --- Tests --------------------------------------------------------------------------------------

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
    use crate::test_utils::assert_args;
    use crate::test_utils::assert_args_empty;
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
    fn num_of_args() {
        // Integers
        assert_args(1i8, &["1"]);
        assert_args(-1i64, &["-1"]);
        assert_args(1usize, &["1"]);

        // Non-zero integers
        assert_args(core::num::NonZeroU8::new(1).unwrap(), &["1"]);

        // Floats
        assert_args(1.5f64, &["1.5"]);

        // Byte slices (one binary argument, even when empty)
        assert_args(b"bytes".to_vec(), &["bytes"]);
        assert_args(Vec::<u8>::new(), &[""]);
        assert_args(&b"bytes"[..], &["bytes"]);
        assert_args(b"bytes", &["bytes"]);
        assert_args(vec![0xFFu8, 0x00], &[[0xFFu8, 0x00].as_slice()]);

        // Booleans
        assert_args(true, &["1"]);

        // Strings
        assert_args("k", &["k"]);
        assert_args(String::from("k"), &["k"]);

        // Bytes
        assert_args(Bytes::from_static(b"b"), &["b"]);

        // Sequences
        assert_args(vec!["one"], &["one"]);
        assert_args(vec!["a", "b", "c"], &["a", "b", "c"]);
        assert_args_empty(Vec::<&str>::new());
        assert_args(&["a", "b"][..], &["a", "b"]);
        assert_args(&["a", "b"], &["a", "b"]);
        assert_args(vec![vec!["a", "b"], vec!["c"]], &["a", "b", "c"]);
        assert_args(&[("f1", 1i64), ("f2", 2i64)][..], &["f1", "1", "f2", "2"]);

        // Options
        assert_args_empty(Option::<i64>::None);
        assert_args(Some(1i64), &["1"]);
        assert_args(Some(vec!["a", "b"]), &["a", "b"]);

        // References. The explicit `&` exercises the `ToValkeyArgs for &T` impl,
        // so the borrow is deliberate, not `needless_borrows_for_generic_args`.
        #[allow(clippy::needless_borrows_for_generic_args)]
        {
            assert_args(&1i64, &["1"]);
            assert_args(&&vec!["a", "b"], &["a", "b"]);
        }

        // Maps and sets
        assert_args_empty(&*HASH_SET_0);
        assert_args_empty(&*BTREE_SET_0);
        assert_args_empty(&*HASH_MAP_0);
        assert_args_empty(&*BTREE_MAP_0);

        assert_args(&*HASH_SET_1, &["x"]);
        assert_args(&*BTREE_SET_1, &["x"]);
        assert_args(&*HASH_MAP_1, &["k", "1"]);
        assert_args(&*BTREE_MAP_1, &["k", "1"]);

        assert_args(&*BTREE_SET_2, &["a", "b", "c"]);
        assert_args(&*BTREE_MAP_2, &["f1", "1", "f2", "2"]);

        // Hash-based collections have no fixed order, so only the count is checked.
        assert_eq!(HASH_SET_2.to_valkey_args().len(), 3);
        assert_eq!(HASH_SET_2.num_of_args(), 3);
        assert_eq!(HASH_MAP_2.to_valkey_args().len(), 4);
        assert_eq!(HASH_MAP_2.num_of_args(), 4);

        // Tuples
        assert_args((1i64,), &["1"]);
        assert_args((1i64, 2i64), &["1", "2"]);
        assert_args(("a", 1, 2.5), &["a", "1", "2.5"]);
        assert_args((vec!["a", "b"], "c"), &["a", "b", "c"]);
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

#[cfg(test)]
mod to_single_arg {
    use super::*;

    /// Compiles only if `T` implements [`ToSingleValkeyArg`].
    fn assert_single<T: ToSingleValkeyArg>(value: T) {
        assert_eq!(value.num_of_args(), 1);
    }

    #[test]
    fn to_single_valkey_arg_impls_encode_one_arg() {
        // Integers
        assert_single(1i8);
        assert_single(1i64);
        assert_single(1usize);
        assert_single(255u8);

        // Non-zero integers
        assert_single(core::num::NonZeroU64::new(1).unwrap());

        // Floats
        assert_single(1.5f32);
        assert_single(1.5f64);

        // Booleans
        assert_single(true);

        // Strings
        assert_single("k");
        assert_single(String::from("k"));

        // Bytes
        assert_single(Bytes::from_static(b"b"));

        // Byte slices, vectors, and arrays
        assert_single(b"raw".to_vec());
        assert_single(&b"raw"[..]);
        assert_single(b"raw");
        assert_single(Vec::<u8>::new());

        // References. The explicit `&` exercises the `ToSingleValkeyArg for &T` impl,
        // so the borrow is deliberate, not `needless_borrows_for_generic_args`.
        #[allow(clippy::needless_borrows_for_generic_args)]
        {
            assert_single(&"k");
            assert_single(&&5i64);
        }
    }
}
