// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Error handling for the JNI bridge.
//!
//! This module provides comprehensive error handling between Rust and Java,
//! optimized for performance and proper exception mapping.

use jni::JNIEnv;
use thiserror::Error;

/// Result type for JNI operations
pub type JniResult<T> = std::result::Result<T, JniError>;

/// Comprehensive error type for JNI operations
#[derive(Error, Debug)]
pub enum JniError {
    /// JNI framework error
    #[error("JNI error: {0}")]
    Jni(String),

    /// Connection establishment failed
    #[error("Connection failed: {0}")]
    Connection(String),

    /// Command execution failed
    #[error("Command failed: {0}")]
    Command(String),

    /// Invalid configuration provided
    #[error("Configuration error: {0}")]
    Configuration(String),

    /// Type conversion error
    #[error("Conversion error: {0}")]
    ConversionError(String),

    /// Invalid input parameters
    #[error("Invalid input: {0}")]
    InvalidInput(String),

    /// Tokio runtime error
    #[error("Runtime error: {0}")]
    Runtime(String),

    /// Null pointer encountered
    #[error("Null pointer: {0}")]
    NullPointer(String),

    /// UTF-8 conversion failed
    #[error("UTF-8 conversion error: {0}")]
    Utf8(String),

    /// Unexpected response type
    #[error("Unexpected response: {0}")]
    UnexpectedResponse(String),

    /// Timeout occurred
    #[error("Operation timeout: {0}")]
    Timeout(String),

    /// Redis/Valkey error
    #[error("Redis error: {0}")]
    Redis(#[from] redis::RedisError),

    /// Lock poisoned error
    #[error("Lock poisoned: {0}")]
    LockPoisoned(String),

    /// Invalid handle error
    #[error("Invalid handle: {0}")]
    InvalidHandle(String),

    /// Runtime shutdown error
    #[error("Runtime shutdown: {0}")]
    RuntimeShutdown(String),
}

impl From<jni::errors::Error> for JniError {
    fn from(err: jni::errors::Error) -> Self {
        JniError::Jni(err.to_string())
    }
}

impl From<glide_core::client::ConnectionError> for JniError {
    fn from(err: glide_core::client::ConnectionError) -> Self {
        JniError::Connection(err.to_string())
    }
}

impl From<std::string::FromUtf8Error> for JniError {
    fn from(err: std::string::FromUtf8Error) -> Self {
        JniError::Utf8(err.to_string())
    }
}

/// Throw appropriate Java exception based on error type
pub fn throw_java_exception(env: &mut JNIEnv, error: &JniError) {
    let (class_name, message) = match error {
        JniError::Jni(msg) => ("java/lang/RuntimeException", format!("JNI error: {}", msg)),
        JniError::Connection(msg) => ("java/net/ConnectException", msg.clone()),
        JniError::Command(msg) => ("java/lang/IllegalStateException", format!("Command failed: {}", msg)),
        JniError::Configuration(msg) => ("java/lang/IllegalArgumentException", format!("Configuration error: {}", msg)),
        JniError::InvalidInput(msg) => ("java/lang/IllegalArgumentException", msg.clone()),
        JniError::Runtime(msg) => ("java/lang/RuntimeException", format!("Runtime error: {}", msg)),
        JniError::NullPointer(msg) => ("java/lang/NullPointerException", msg.clone()),
        JniError::Utf8(msg) => ("java/lang/IllegalArgumentException", format!("UTF-8 error: {}", msg)),
        JniError::UnexpectedResponse(msg) => ("java/lang/IllegalStateException", format!("Unexpected response: {}", msg)),
        JniError::Timeout(msg) => ("java/util/concurrent/TimeoutException", msg.clone()),
        JniError::Redis(err) => ("java/lang/RuntimeException", format!("Redis error: {}", err)),
        JniError::ConversionError(msg) => ("java/lang/IllegalArgumentException", format!("Conversion error: {}", msg)),
        JniError::LockPoisoned(msg) => ("java/lang/IllegalStateException", format!("Lock poisoned: {}", msg)),
        JniError::InvalidHandle(msg) => ("java/lang/IllegalArgumentException", format!("Invalid handle: {}", msg)),
        JniError::RuntimeShutdown(msg) => ("java/lang/IllegalStateException", format!("Runtime shutdown: {}", msg)),
    };

    // Attempt to throw the exception, ignoring errors since we're already in error handling
    let _ = env.throw_new(class_name, message);
}

/// Macro for converting Rust results to JNI return values with exception handling
#[macro_export]
macro_rules! jni_result {
    ($env:expr, $result:expr, $default:expr) => {
        match $result {
            Ok(value) => value,
            Err(error) => {
                $crate::error::throw_java_exception($env, &error);
                return $default;
            }
        }
    };
}

/// Macro for creating JNI error from format string
#[macro_export]
macro_rules! jni_error {
    ($variant:ident, $($arg:tt)*) => {
        $crate::error::JniError::$variant(format!($($arg)*))
    };
}