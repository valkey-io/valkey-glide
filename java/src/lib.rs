// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-performance JNI bindings for Valkey GLIDE.
//!
//! This library provides direct integration between Java and the Rust-based
//! glide-core client, eliminating Unix Domain Socket overhead and enabling
//! zero-copy operations for maximum performance.

mod async_bridge;
mod callback;
mod client;
mod error;
mod input_validator;
mod jni_wrappers;
mod runtime;

pub use async_bridge::*;
pub use callback::*;
pub use client::*;
pub use error::*;
pub use input_validator::*;
pub use jni_wrappers::*;
pub use runtime::*;
