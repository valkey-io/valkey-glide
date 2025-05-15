// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
#![macro_use]

macro_rules! fail {
    ($expr:expr) => {
        return Err(::std::convert::From::from($expr))
    };
}
