//! Error handling for the JNI bridge.
//!
//! This module defines the error types and utilities for converting between
//! Rust errors and Java exceptions.

use jni::errors::Error as JniError;
use jni::JNIEnv;
use jni::objects::JThrowable;
use thiserror::Error;

/// Result type for JNI operations
pub type Result<T> = std::result::Result<T, Error>;

/// Error type for JNI operations
#[derive(Error, Debug)]
pub enum Error {
    /// JNI error
    #[error("JNI error: {0}")]
    Jni(#[from] JniError),

    /// Connection error
    #[error("Connection error: {0}")]
    Connection(String),

    /// Redis error
    #[error("Redis error: {0}")]
    Redis(String),

    /// Command error
    #[error("Command error: {0}")]
    Command(String),

    /// Configuration error
    #[error("Configuration error: {0}")]
    Config(String),

    /// Invalid argument error
    #[error("Invalid argument: {0}")]
    InvalidArgument(String),

    /// Internal error
    #[error("Internal error: {0}")]
    Internal(String),

    /// Not implemented error
    #[error("Not implemented: {0}")]
    NotImplemented(String),
}

impl Error {
    /// Throw this error as a Java exception
    pub fn throw<'local>(&self, env: &mut JNIEnv<'local>) -> Result<JThrowable<'local>> {
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
fn throw_exception<'local>(
    env: &mut JNIEnv<'local>,
    class_name: &str,
    message: &str,
) -> Result<JThrowable<'local>> {
    let exception_class = env.find_class(class_name)?;
    let exception = env.new_object(
        exception_class,
        "(Ljava/lang/String;)V",
        &[env.new_string(message)?.into()],
    )?;

    let throwable = exception.into();
    env.throw(throwable)?;

    Ok(throwable)
}

/// RAII guard for exception checking
pub struct ExceptionGuard<'a> {
    env: &'a mut JNIEnv<'a>,
}

impl<'a> ExceptionGuard<'a> {
    /// Create a new exception guard
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
