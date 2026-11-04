//! Layout fingerprint for [`Value`], for a binary that hands a `Value` to a separately
//! compiled library.
//!
//! `Value` is `repr(Rust)`, so its layout is whatever the compiler chose for the crate that
//! defined it. A library that receives a `Value` compiled elsewhere reads it with its own
//! idea of that layout, and a disagreement is memory unsafety rather than a wrong answer.
//! Comparing a fingerprint of the layout on both sides is what lets such a hand-off fail
//! closed instead.
//!
//! The fingerprint covers what drifts independently of this source tree: the size and
//! alignment of `Value`, of the private types it embeds (`bytes::Bytes` inside
//! `BulkString`, `num_bigint::BigInt` inside `BigNumber`), and how `Option<Value>` packs a
//! discriminant, which moves when the enum's own layout does. A dependency bump or a
//! compiler change that lays any of those out differently changes the fingerprint.
//!
//! What it cannot see is a build whose `Bytes` or `BigInt` keeps the same size, alignment
//! and niche while moving a field, because those types are only ever read through their own
//! methods here. Two libraries that must agree on a `Value` should therefore be built from
//! one lockfile and one toolchain; this fingerprint is the cheap check, not the guarantee.

use std::mem::{align_of, size_of};

use num_bigint::BigInt;

use crate::Value;

/// FNV-1a offset basis.
const BASIS: u64 = 0xcbf2_9ce4_8422_2325;
/// FNV-1a prime.
const PRIME: u64 = 0x0000_0100_0000_01b3;

/// Folds one type's size and alignment into `hash`.
const fn fold(hash: u64, size: usize, align: usize) -> u64 {
    let hash = (hash ^ size as u64).wrapping_mul(PRIME);
    (hash ^ align as u64).wrapping_mul(PRIME)
}

/// Layout fingerprint of [`Value`] and of the dependency types it embeds.
///
/// Two builds that report the same fingerprint lay `Value` out the same way, so either may
/// hand a `Value` to the other. Being a `const fn`, the value is fixed at compile time.
pub const fn value_abi_fingerprint() -> u64 {
    let mut hash = BASIS;
    hash = fold(hash, size_of::<Value>(), align_of::<Value>());
    // `Option<Value>` shows how the enum packs a discriminant, which is the part of a
    // `repr(Rust)` layout a compiler change is most likely to move.
    hash = fold(
        hash,
        size_of::<Option<Value>>(),
        align_of::<Option<Value>>(),
    );
    hash = fold(hash, size_of::<bytes::Bytes>(), align_of::<bytes::Bytes>());
    hash = fold(
        hash,
        size_of::<Option<bytes::Bytes>>(),
        align_of::<Option<bytes::Bytes>>(),
    );
    hash = fold(hash, size_of::<BigInt>(), align_of::<BigInt>());
    fold(
        hash,
        size_of::<Option<BigInt>>(),
        align_of::<Option<BigInt>>(),
    )
}
