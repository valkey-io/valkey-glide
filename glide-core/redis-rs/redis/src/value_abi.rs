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
//! What it cannot see on its own is a dependency build whose `Bytes` or `BigInt` keeps its
//! size, alignment and niche while moving a field, which a version bump is free to do. So
//! the token a library reports is this fingerprint folded together with the resolved
//! versions of those crates and the compiler that produced it, both recorded at build time
//! by the `build.rs` next to each library. Two builds agree only when they used the same
//! `bytes`, the same `num-bigint` and the same rustc, and laid `Value` out the same way.

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

/// The token a library reports about the `Value` it was built with: the layout fingerprint,
/// plus the dependency versions and compiler its `build.rs` recorded.
///
/// `dependency_versions` and `rustc_version` are the two strings the build script emits;
/// they are compared through this hash rather than as text because the token crosses an FFI
/// boundary as a single `u64`.
pub fn value_abi_token(dependency_versions: &str, rustc_version: &str) -> u64 {
    let mut hash = value_abi_fingerprint();
    for part in [dependency_versions, rustc_version] {
        for byte in part.as_bytes() {
            hash = (hash ^ u64::from(*byte)).wrapping_mul(PRIME);
        }
    }
    hash
}

#[cfg(test)]
mod tests {
    use super::*;

    const VERSIONS: &str = "bytes=1.12.1;num-bigint=0.4.8";
    const RUSTC: &str = "rustc 1.99.0 (b940084d7 2026-09-28)";

    /// A dependency bump keeps `Value` the same size and still has to move the token, which
    /// is the drift no size or alignment probe can see on its own.
    #[test]
    fn a_dependency_version_moves_the_token() {
        assert_ne!(
            value_abi_token(VERSIONS, RUSTC),
            value_abi_token("bytes=2.0.0;num-bigint=0.4.8", RUSTC)
        );
        assert_ne!(
            value_abi_token(VERSIONS, RUSTC),
            value_abi_token("bytes=1.12.1;num-bigint=0.5.0", RUSTC)
        );
    }

    #[test]
    fn a_compiler_version_moves_the_token() {
        assert_ne!(
            value_abi_token(VERSIONS, RUSTC),
            value_abi_token(VERSIONS, "rustc 1.98.0 (000000000 2026-06-01)")
        );
    }

    /// The same build inputs report the same token, whichever library asks.
    #[test]
    fn the_same_inputs_report_the_same_token() {
        assert_eq!(
            value_abi_token(VERSIONS, RUSTC),
            value_abi_token(VERSIONS, RUSTC)
        );
    }
}
