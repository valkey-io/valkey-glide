// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use jni::{JNIEnv, errors::Error as JNIError};
use log::error;
use std::string::FromUtf8Error;

pub enum FFIError {
    Jni(JNIError),
    Utf8(FromUtf8Error),
    Logger(String),
    OpenTelemetry(String),
}

impl From<jni::errors::Error> for FFIError {
    fn from(value: jni::errors::Error) -> Self {
        FFIError::Jni(value)
    }
}

impl From<FromUtf8Error> for FFIError {
    fn from(value: FromUtf8Error) -> Self {
        FFIError::Utf8(value)
    }
}

impl std::fmt::Display for FFIError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FFIError::Jni(err) => write!(f, "{err}"),
            FFIError::Utf8(err) => write!(f, "{err}"),
            FFIError::Logger(err) => write!(f, "{err}"),
            FFIError::OpenTelemetry(err) => write!(f, "{err}"),
        }
    }
}

#[derive(Copy, Clone)]
pub enum ExceptionType {
    Exception,
    RuntimeException,
}

impl std::fmt::Display for ExceptionType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ExceptionType::Exception => write!(f, "java/lang/Exception"),
            ExceptionType::RuntimeException => write!(f, "java/lang/RuntimeException"),
        }
    }
}

// This handles `FFIError`s by converting them to Java exceptions and throwing them.
pub fn handle_errors<T>(env: &mut JNIEnv, result: Result<T, FFIError>) -> Option<T> {
    match result {
        Ok(value) => Some(value),
        Err(err) => {
            match err {
                FFIError::Utf8(utf8_error) => throw_java_exception(
                    env,
                    ExceptionType::RuntimeException,
                    &utf8_error.to_string(),
                ),
                error => throw_java_exception(env, ExceptionType::Exception, &error.to_string()),
            };
            // Return `None` because we need to still return a value after throwing.
            // This signals to the caller that we need to return the default value.
            None
        }
    }
}

/// Execute a JNI entry point body. Provides a closure scope so callers can use
/// early `return Some(...)` for error paths.
#[inline(always)]
pub fn run_ffi<T>(func: impl FnOnce() -> Option<T>) -> Option<T> {
    func()
}

pub fn throw_java_exception(env: &mut JNIEnv, exception_type: ExceptionType, message: &str) {
    match env.exception_check() {
        Ok(true) => (),
        Ok(false) => {
            env.throw_new(exception_type.to_string(), message)
                .unwrap_or_else(|err| {
                    error!("Failed to create exception with string {message}: {err}");
                });
        }
        Err(err) => {
            error!("Failed to check if an exception is currently being thrown: {err}");
        }
    }
}
