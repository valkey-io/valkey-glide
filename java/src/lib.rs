// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! High-Performance JNI Bridge for Valkey GLIDE
//!
//! This library provides a production-ready, high-performance Java Native Interface (JNI)
//! bridge for Valkey GLIDE. The architecture is designed to achieve exceptional throughput
//! and low latency through advanced optimization techniques.
//!
//! ## Key Features
//!
//! - **Lock-free Command Queuing**: Utilizes crossbeam channels for maximum concurrency
//! - **Dynamic Worker Scaling**: Automatically scales workers based on system capabilities
//! - **Batch Processing**: Intelligent command batching with adaptive sizing
//! - **Zero-Copy Optimizations**: Minimizes memory allocations and copies
//! - **Robust Resource Management**: Comprehensive cleanup and error handling
//! - **Performance Monitoring**: Real-time metrics and adaptive optimization
//!
//! ## Performance Target
//!
//! Designed to achieve high throughput with low latency on modern hardware configurations.
//!
//! ## Architecture
//!
//! The system consists of three core components:
//! 1. **Command Queue**: High-performance command queuing and batching
//! 2. **Batch Dispatcher**: Parallel pipeline execution with worker pools
//! 3. **JNI Interface**: Clean Java integration layer

// Core high-performance modules
mod command_queue;
mod batch_dispatcher;
mod jni_interface;

// Essential utility modules
mod error;
mod input_validator;
mod jni_wrappers;

// Legacy modules removed - architecture now clean and minimal

// Export high-performance interface
pub use command_queue::*;
pub use batch_dispatcher::*;
pub use jni_interface::*;

// Export utilities
pub use error::*;
pub use input_validator::*;
pub use jni_wrappers::*;

// Clean architecture - no legacy exports needed
