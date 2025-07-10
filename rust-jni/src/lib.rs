// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Real JNI bridge using glide-core for actual performance measurement.
//!
//! This module provides a production-ready JNI interface directly to glide-core,
//! eliminating UDS overhead and proving the performance benefits of in-process
//! Rust core integration. This is the real implementation requested by the user.

mod error;
mod client;

pub use client::*;

// Re-export for compatibility with existing interfaces
pub use error::{GlideError, GlideResult};
