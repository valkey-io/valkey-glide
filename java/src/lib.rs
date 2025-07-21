// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-Performance JNI Bridge for Valkey GLIDE
//!
//! Professional architecture achieving exceptional performance through:
//! - Lock-free parallel command queuing with crossbeam channels
//! - Zero-copy JNI optimizations with direct ByteBuffer support
//! - High-throughput batch processing with adaptive sizing
//! - Multi-threaded pipeline execution for maximum concurrency
//! - Memory-efficient resource management
//! - Comprehensive performance monitoring
//! 
//! TARGET: 100K+ TPS with ultra-low latency

// High-performance core modules
mod command_queue;
mod batch_dispatcher;
mod jni_interface;

// Core utility modules
mod error;
mod input_validator;
mod jni_wrappers;

// Legacy modules (compatibility)
mod async_bridge;
mod callback;
mod client;
mod runtime;

// Export high-performance interface
pub use command_queue::*;
pub use batch_dispatcher::*;
pub use jni_interface::*;

// Export utilities
pub use error::*;
pub use input_validator::*;
pub use jni_wrappers::*;

// Legacy exports (will be phased out)
pub use async_bridge::*;
pub use callback::*;
pub use client::*;
pub use runtime::*;
