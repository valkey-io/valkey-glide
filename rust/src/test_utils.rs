// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Shared helpers for the crate's unit tests.

use crate::write::ToValkeyArgs;
use bytes::Bytes;

/// Asserts that `value` encodes to no arguments
/// and that `num_of_args()` returns zero.
#[track_caller]
pub(crate) fn assert_args_empty(value: impl ToValkeyArgs) {
    assert_args(value, &[] as &[&str]);
}

/// Asserts that `value` encodes to the expected arguments
/// and that `num_of_args()` returns the correct number of arguments.
#[track_caller]
pub(crate) fn assert_args<A: AsRef<[u8]>>(value: impl ToValkeyArgs, expected: &[A]) {
    let actual: Vec<Bytes> = value
        .to_valkey_args()
        .into_iter()
        .map(Bytes::from)
        .collect();
    let expected: Vec<Bytes> = expected
        .iter()
        .map(|arg| Bytes::copy_from_slice(arg.as_ref()))
        .collect();
    assert_eq!(actual, expected);
    assert_eq!(value.num_of_args(), expected.len());
}
