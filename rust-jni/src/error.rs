// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Error handling for the JNI bridge.
//!
//! This module defines the error types and utilities for converting between
//! Rust errors and Java exceptions.

use jni::errors::Error as JniError;
use jni::JNIEnv;
use jni::objects::JThrowable;
use thiserror::Error;

/// Result type for JNI operations
pub type GlideResult<T> = std::result::Result<T, GlideError>;

/// Error type for JNI operations
#[derive(Error, Debug)]
pub enum GlideError {
    /// JNI error
    #[error("JNI error: {0}")]
    JniError(String),

    /// Connection error
    #[error("Connection failed: {0}")]
    ConnectionFailed(String),

    /// Command error
    #[error("Command failed: {0}")]
    CommandFailed(String),

    /// Configuration error
    #[error("Configuration error: {0}")]
    ConfigError(String),

    /// Invalid input error
    #[error("Invalid input: {0}")]
    InvalidInput(String),

    /// Runtime error
    #[error("Runtime error: {0}")]
    RuntimeError(String),

    /// Null pointer error
    #[error("Null pointer: {0}")]
    NullPointer(String),

    /// UTF-8 conversion error
    #[error("Invalid UTF-8: {0}")]
    InvalidUtf8(String),

    /// Unexpected response error
    #[error("Unexpected response: {0}")]
    UnexpectedResponse(String),
}

/// Throw a Glide exception to Java
pub fn throw_glide_exception(env: &mut JNIEnv, error: &GlideError) {
    let (class_name, message) = match error {
        GlideError::JniError(msg) => ("java/lang/RuntimeException", format!("JNI error: {}", msg)),
        GlideError::ConnectionFailed(msg) => ("io/valkey/glide/jni/exceptions/ConnectionException", msg.clone()),
        GlideError::CommandFailed(msg) => ("io/valkey/glide/jni/exceptions/CommandException", msg.clone()),
        GlideError::ConfigError(msg) => ("io/valkey/glide/jni/exceptions/ConfigurationException", msg.clone()),
        GlideError::InvalidInput(msg) => ("java/lang/IllegalArgumentException", msg.clone()),
        GlideError::RuntimeError(msg) => ("java/lang/RuntimeException", msg.clone()),
        GlideError::NullPointer(msg) => ("java/lang/NullPointerException", msg.clone()),
        GlideError::InvalidUtf8(msg) => ("java/lang/IllegalArgumentException", format!("UTF-8 error: {}", msg)),
        GlideError::UnexpectedResponse(msg) => ("io/valkey/glide/jni/exceptions/RedisException", msg.clone()),
    };

    // Attempt to throw the exception, ignore errors as we're already in error handling
    let _ = env.throw_new(class_name, &message);
}

// Legacy error type for compatibility - remove this eventually
#[derive(Error, Debug)]
pub enum Error {
    /// JNI error
    #[error("JNI error: {0}")]
    Jni(#[from] JniError),

    /// Connection error
    #[error("Connection error: {0}")]
    #[allow(dead_code)]
    Connection(String),

    /// Redis error
    #[error("Redis error: {0}")]
    #[allow(dead_code)]
    Redis(String),

    /// Command error
    #[error("Command error: {0}")]
    #[allow(dead_code)]
    Command(String),

    /// Configuration error
    #[error("Configuration error: {0}")]
    #[allow(dead_code)]
    Config(String),

    /// Invalid argument error
    #[error("Invalid argument: {0}")]
    InvalidArgument(String),

    /// Internal error
    #[error("Internal error: {0}")]
    #[allow(dead_code)]
    Internal(String),

    /// Not implemented error
    #[error("Not implemented: {0}")]
    #[allow(dead_code)]
    NotImplemented(String),
}

/// Legacy result type for compatibility
pub type Result<T> = std::result::Result<T, Error>;

impl Error {
    /// Optimized error throwing with direct exception creation
    #[allow(dead_code)]
    pub fn throw<'local>(&self, mut env: JNIEnv<'local>) -> Result<()> {
        let (class_name, message) = match self {
            Error::Jni(err) => ("java/lang/RuntimeException", format!("JNI error: {}", err)),
            Error::Connection(msg) => ("io/valkey/glide/jni/exceptions/ConnectionException", msg.clone()),
            Error::Redis(msg) => ("io/valkey/glide/jni/exceptions/RedisException", msg.clone()),
            Error::Command(msg) => ("io/valkey/glide/jni/exceptions/CommandException", msg.clone()),
            Error::Config(msg) => ("io/valkey/glide/jni/exceptions/ConfigurationException", msg.clone()),
            Error::InvalidArgument(msg) => ("java/lang/IllegalArgumentException", msg.clone()),
            Error::Internal(msg) => ("io/valkey/glide/jni/exceptions/InternalException", msg.clone()),
            Error::NotImplemented(msg) => ("java/lang/UnsupportedOperationException", msg.clone()),
        };

        // Direct exception throwing without intermediate object creation
        env.throw_new(class_name, &message)?;
        Ok(())
    }

    /// Legacy method for backward compatibility - will be removed in production
    #[allow(dead_code)]
    pub fn throw_legacy<'local>(&self, env: JNIEnv<'local>) -> Result<JThrowable<'local>> {
        match self {
            Error::Jni(err) => {
                let msg = format!("JNI error: {}", err);
                throw_exception(env, "java/lang/RuntimeException", &msg)
            }
            Error::Connection(msg) => {
                throw_exception(env, "io/valkey/glide/jni/exceptions/ConnectionException", msg)
            }
            Error::Redis(msg) => {
                throw_exception(env, "io/valkey/glide/jni/exceptions/RedisException", msg)
            }
            Error::Command(msg) => {
                throw_exception(env, "io/valkey/glide/jni/exceptions/CommandException", msg)
            }
            Error::Config(msg) => {
                throw_exception(env, "io/valkey/glide/jni/exceptions/ConfigurationException", msg)
            }
            Error::InvalidArgument(msg) => {
                throw_exception(env, "java/lang/IllegalArgumentException", msg)
            }
            Error::Internal(msg) => {
                throw_exception(env, "io/valkey/glide/jni/exceptions/InternalException", msg)
            }
            Error::NotImplemented(msg) => {
                throw_exception(env, "java/lang/UnsupportedOperationException", msg)
            }
        }
    }
}

/// Throw a Java exception with the given class name and message
#[allow(dead_code)]
fn throw_exception<'local>(
    mut env: JNIEnv<'local>,
    class_name: &str,
    message: &str,
) -> Result<JThrowable<'local>> {
    let exception_class = env.find_class(class_name)?;
    let exception = env.new_object(
        exception_class,
        "(Ljava/lang/String;)V",
        &[(&env.new_string(message)?).into()],
    )?;

    let throwable = exception.into();
    env.throw(&throwable)?;

    Ok(throwable)
}

/// RAII guard for exception checking
#[allow(dead_code)]
pub struct ExceptionGuard<'a> {
    env: &'a mut JNIEnv<'a>,
}

impl<'a> ExceptionGuard<'a> {
    /// Create a new exception guard
    #[allow(dead_code)]
    pub fn new(env: &'a mut JNIEnv<'a>) -> Self {
        Self { env }
    }
}

impl<'a> Drop for ExceptionGuard<'a> {
    fn drop(&mut self) {
        // Check for pending exceptions and describe them
        if let Ok(true) = self.env.exception_check() {
            let _ = self.env.exception_describe();
            let _ = self.env.exception_clear();
        }
    }
}
