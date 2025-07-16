// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-performance JNI bindings for Valkey GLIDE.
//!
//! This library provides direct integration between Java and the Rust-based
//! glide-core client, eliminating Unix Domain Socket overhead and enabling
//! zero-copy operations for maximum performance.

mod error;
mod client;
mod callback;
mod runtime;
mod async_bridge;

pub use error::*;
pub use client::*;
pub use callback::*;
pub use runtime::*;
pub use async_bridge::*;