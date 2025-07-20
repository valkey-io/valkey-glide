// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Input validation for JNI boundary safety.
//!
//! This module provides validation functions that prevent library crashes and undefined
//! behavior at the JNI boundary. It does NOT perform application-level validation like
//! command whitelisting or business logic checks - those are the responsibility of the
//! application and Redis server.

use jni::objects::JObjectArray;
use jni::sys::{jlong, jobjectArray, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::{Arc, RwLock};

use crate::error::JniError;

/// Client registry for validating client handles
static CLIENT_REGISTRY: std::sync::LazyLock<Arc<RwLock<HashMap<u64, bool>>>> =
    std::sync::LazyLock::new(|| Arc::new(RwLock::new(HashMap::new())));

/// Register a client handle as valid
pub fn register_client(handle: u64) {
    let mut registry = CLIENT_REGISTRY.write().unwrap();
    registry.insert(handle, true);
}

/// Unregister a client handle
pub fn unregister_client(handle: u64) {
    let mut registry = CLIENT_REGISTRY.write().unwrap();
    registry.remove(&handle);
}

/// Check if a client handle exists
pub fn client_exists(handle: u64) -> bool {
    let registry = CLIENT_REGISTRY.read().unwrap();
    registry.contains_key(&handle)
}

/// Validation errors that can occur at the JNI boundary
#[derive(Debug)]
pub enum ValidationError {
    /// Required JNI pointer is null
    RequiredPointerNull(&'static str),
    /// Client handle is invalid or not found
    InvalidClientHandle,
    /// Client not found in registry
    ClientNotFound,
    /// String contains interior null bytes (breaks JNI)
    InteriorNullByte,
    /// Array index is out of bounds
    ArrayIndexOutOfBounds { index: i32, length: i32 },
    /// Array length is negative (JNI violation)
    NegativeArrayLength(i32),
    /// JNI operation failed
    JniError(jni::errors::Error),
}

impl std::fmt::Display for ValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ValidationError::RequiredPointerNull(name) => {
                write!(f, "Required {} pointer is null", name)
            }
            ValidationError::InvalidClientHandle => {
                write!(f, "Invalid client handle")
            }
            ValidationError::ClientNotFound => {
                write!(f, "Client not found")
            }
            ValidationError::InteriorNullByte => {
                write!(f, "String contains null bytes")
            }
            ValidationError::ArrayIndexOutOfBounds { index, length } => {
                write!(
                    f,
                    "Array index {} out of bounds (length: {})",
                    index, length
                )
            }
            ValidationError::NegativeArrayLength(length) => {
                write!(f, "Array length cannot be negative: {}", length)
            }
            ValidationError::JniError(e) => {
                write!(f, "JNI error: {}", e)
            }
        }
    }
}

impl std::error::Error for ValidationError {}

impl From<jni::errors::Error> for ValidationError {
    fn from(e: jni::errors::Error) -> Self {
        ValidationError::JniError(e)
    }
}

/// JNI boundary safety validator
pub struct JniSafetyValidator;

impl JniSafetyValidator {
    /// Validate JNI pointer is not null when required
    pub fn validate_required_pointer<T>(
        ptr: *mut T,
        name: &'static str,
    ) -> Result<(), ValidationError> {
        if ptr.is_null() {
            return Err(ValidationError::RequiredPointerNull(name));
        }
        Ok(())
    }

    /// Validate client handle represents a valid client
    pub fn validate_client_handle(handle: jlong) -> Result<(), ValidationError> {
        if handle == 0 {
            return Err(ValidationError::InvalidClientHandle);
        }

        // Check if client actually exists in our registry
        if !client_exists(handle as u64) {
            return Err(ValidationError::ClientNotFound);
        }
        Ok(())
    }

    /// Validate string doesn't contain interior null bytes that break JNI
    pub fn validate_no_interior_nulls(s: &str) -> Result<(), ValidationError> {
        if s.contains('\0') {
            return Err(ValidationError::InteriorNullByte);
        }
        Ok(())
    }

    /// Validate array index is within bounds (prevent segfaults)
    ///
    /// # Safety
    ///
    /// This function dereferences the raw `array` pointer by converting it to a JObjectArray.
    /// The caller must ensure that `array` is a valid jobjectArray pointer obtained from JNI.
    pub unsafe fn validate_array_access(
        env: &JNIEnv,
        array: jobjectArray,
        index: i32,
    ) -> Result<(), ValidationError> {
        // SAFETY: Caller guarantees that `array` is a valid jobjectArray pointer from JNI.
        // We're only checking array length, not accessing individual elements.
        let jarray = JObjectArray::from_raw(array);
        let length = env.get_array_length(&jarray)?;

        if index < 0 || index >= length {
            return Err(ValidationError::ArrayIndexOutOfBounds { index, length });
        }
        Ok(())
    }

    /// Validate array length is non-negative (JNI requirement)
    pub fn validate_array_length(length: i32) -> Result<(), ValidationError> {
        if length < 0 {
            return Err(ValidationError::NegativeArrayLength(length));
        }
        Ok(())
    }

    /// Comprehensive validation for JNI function entry points
    pub fn validate_execute_command_params(
        client_handle: jlong,
        command: jstring,
        args: jobjectArray,
    ) -> Result<(), ValidationError> {
        Self::validate_client_handle(client_handle)?;
        Self::validate_required_pointer(command, "command")?;
        Self::validate_required_pointer(args, "args")?;
        Ok(())
    }
}

/// Convert validation error to JNI error for consistent error handling
impl From<ValidationError> for JniError {
    fn from(e: ValidationError) -> Self {
        match e {
            ValidationError::RequiredPointerNull(name) => {
                JniError::InvalidInput(format!("Required {} is null", name))
            }
            ValidationError::InvalidClientHandle => {
                JniError::InvalidHandle("Invalid client handle".to_string())
            }
            ValidationError::ClientNotFound => {
                JniError::InvalidHandle("Client not found".to_string())
            }
            ValidationError::InteriorNullByte => {
                JniError::InvalidInput("String contains null bytes".to_string())
            }
            ValidationError::ArrayIndexOutOfBounds { index, length } => JniError::InvalidInput(
                format!("Array index {} out of bounds (length: {})", index, length),
            ),
            ValidationError::NegativeArrayLength(length) => {
                JniError::InvalidInput(format!("Array length cannot be negative: {}", length))
            }
            ValidationError::JniError(e) => JniError::Runtime(format!("JNI error: {}", e)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_null_pointer_validation() {
        let result =
            JniSafetyValidator::validate_required_pointer(std::ptr::null_mut::<i32>(), "test");
        assert!(matches!(
            result,
            Err(ValidationError::RequiredPointerNull("test"))
        ));

        let valid_ptr = &mut 42i32 as *mut i32;
        let result = JniSafetyValidator::validate_required_pointer(valid_ptr, "test");
        assert!(result.is_ok());
    }

    #[test]
    fn test_client_handle_validation() {
        // Invalid handle (0)
        let result = JniSafetyValidator::validate_client_handle(0);
        assert!(matches!(result, Err(ValidationError::InvalidClientHandle)));

        // Valid handle but not registered
        let result = JniSafetyValidator::validate_client_handle(123);
        assert!(matches!(result, Err(ValidationError::ClientNotFound)));

        // Register and test valid handle
        register_client(123);
        let result = JniSafetyValidator::validate_client_handle(123);
        assert!(result.is_ok());

        // Cleanup
        unregister_client(123);
    }

    #[test]
    fn test_interior_nulls_validation() {
        let result = JniSafetyValidator::validate_no_interior_nulls("hello\0world");
        assert!(matches!(result, Err(ValidationError::InteriorNullByte)));

        let result = JniSafetyValidator::validate_no_interior_nulls("hello world");
        assert!(result.is_ok());
    }

    #[test]
    fn test_array_length_validation() {
        let result = JniSafetyValidator::validate_array_length(-1);
        assert!(matches!(
            result,
            Err(ValidationError::NegativeArrayLength(-1))
        ));

        let result = JniSafetyValidator::validate_array_length(0);
        assert!(result.is_ok());

        let result = JniSafetyValidator::validate_array_length(100);
        assert!(result.is_ok());
    }
}
