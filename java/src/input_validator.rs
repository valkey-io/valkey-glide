// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Input validation for JNI boundary safety.
//!
//! This module provides validation functions that prevent library crashes and undefined
//! behavior at the JNI boundary. It does NOT perform application-level validation like
//! command whitelisting or business logic checks - those are the responsibility of the
//! application and Valkey server.

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
    /// Integer overflow in size calculation
    IntegerOverflow {
        operation: &'static str,
        values: String,
    },
    /// Buffer size exceeds library safety limits
    BufferSizeExceedsLimit { size: usize, limit: usize },
    /// Invalid UTF-8 sequence in string
    InvalidUtf8,
    /// Array length would cause memory overflow
    ArraySizeTooLarge { length: i32, element_size: usize },
    /// JNI operation failed
    JniError(jni::errors::Error),
}

impl std::fmt::Display for ValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ValidationError::RequiredPointerNull(name) => {
                write!(f, "Required {name} pointer is null")
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
                write!(f, "Array index {index} out of bounds (length: {length})")
            }
            ValidationError::NegativeArrayLength(length) => {
                write!(f, "Array length cannot be negative: {length}")
            }
            ValidationError::IntegerOverflow { operation, values } => {
                write!(f, "Integer overflow in {operation}: {values}")
            }
            ValidationError::BufferSizeExceedsLimit { size, limit } => {
                write!(f, "Buffer size {size} exceeds safety limit {limit}")
            }
            ValidationError::InvalidUtf8 => {
                write!(f, "Invalid UTF-8 sequence in string")
            }
            ValidationError::ArraySizeTooLarge {
                length,
                element_size,
            } => {
                write!(
                    f,
                    "Array size too large: {length} elements of {element_size} bytes each"
                )
            }
            ValidationError::JniError(e) => {
                write!(f, "JNI error: {e}")
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

/// Library safety limits for preventing resource exhaustion
pub mod safety_limits {
    /// Maximum buffer size for individual operations (128MB)
    /// Prevents memory exhaustion while allowing large operations
    pub const MAX_BUFFER_SIZE: usize = 128 * 1024 * 1024;

    /// Maximum array length for safety checks
    /// Based on JVM array size limits and practical memory constraints
    pub const MAX_ARRAY_LENGTH: i32 = i32::MAX - 8;

    /// Maximum string length for validation (64MB)
    /// Prevents extremely large string allocations
    pub const MAX_STRING_LENGTH: usize = 64 * 1024 * 1024;

    /// Maximum number of command arguments
    /// Prevents command explosion attacks at library level
    pub const MAX_COMMAND_ARGS: usize = 10_000;
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

    // ============================================================================
    // COMPREHENSIVE BOUNDS CHECKING FOR LIBRARY SAFETY (Fix #8)
    // ============================================================================

    /// Validate integer multiplication for size calculations with overflow protection
    pub fn validate_size_calculation(
        count: i32,
        element_size: usize,
    ) -> Result<usize, ValidationError> {
        // Check for negative count
        if count < 0 {
            return Err(ValidationError::NegativeArrayLength(count));
        }

        // Check for array size limits
        if count > safety_limits::MAX_ARRAY_LENGTH {
            return Err(ValidationError::ArraySizeTooLarge {
                length: count,
                element_size,
            });
        }

        // Safe conversion to usize and overflow check
        let count_usize = count as usize;
        match count_usize.checked_mul(element_size) {
            Some(total_size) => {
                if total_size > safety_limits::MAX_BUFFER_SIZE {
                    Err(ValidationError::BufferSizeExceedsLimit {
                        size: total_size,
                        limit: safety_limits::MAX_BUFFER_SIZE,
                    })
                } else {
                    Ok(total_size)
                }
            }
            None => Err(ValidationError::IntegerOverflow {
                operation: "array size calculation",
                values: format!("{count} * {element_size}"),
            }),
        }
    }

    /// Validate buffer size is within library safety limits
    pub fn validate_buffer_size(size: usize) -> Result<(), ValidationError> {
        if size > safety_limits::MAX_BUFFER_SIZE {
            Err(ValidationError::BufferSizeExceedsLimit {
                size,
                limit: safety_limits::MAX_BUFFER_SIZE,
            })
        } else {
            Ok(())
        }
    }

    /// Validate string length and UTF-8 encoding
    pub fn validate_string_content(s: &str) -> Result<(), ValidationError> {
        // Check string length limits
        if s.len() > safety_limits::MAX_STRING_LENGTH {
            return Err(ValidationError::BufferSizeExceedsLimit {
                size: s.len(),
                limit: safety_limits::MAX_STRING_LENGTH,
            });
        }

        // Validate UTF-8 encoding (this also catches interior nulls)
        if !s.is_ascii() && std::str::from_utf8(s.as_bytes()).is_err() {
            return Err(ValidationError::InvalidUtf8);
        }

        // Check for interior null bytes specifically for JNI safety
        Self::validate_no_interior_nulls(s)?;

        Ok(())
    }

    /// Validate array creation parameters with comprehensive bounds checking
    pub fn validate_array_creation(
        length: i32,
        element_size: usize,
    ) -> Result<(), ValidationError> {
        // Validate the size calculation includes all the safety checks
        Self::validate_size_calculation(length, element_size)?;
        Ok(())
    }

    /// Validate command argument count to prevent resource exhaustion
    pub fn validate_command_args_count(count: usize) -> Result<(), ValidationError> {
        if count > safety_limits::MAX_COMMAND_ARGS {
            Err(ValidationError::BufferSizeExceedsLimit {
                size: count,
                limit: safety_limits::MAX_COMMAND_ARGS,
            })
        } else {
            Ok(())
        }
    }

    /// Validate integer addition with overflow protection
    pub fn validate_addition(
        a: i32,
        b: i32,
        operation: &'static str,
    ) -> Result<i32, ValidationError> {
        match a.checked_add(b) {
            Some(result) => Ok(result),
            None => Err(ValidationError::IntegerOverflow {
                operation,
                values: format!("{a} + {b}"),
            }),
        }
    }

    /// Validate array index range with comprehensive bounds checking
    pub fn validate_array_range(
        start_index: i32,
        end_index: i32,
        array_length: i32,
    ) -> Result<(), ValidationError> {
        // Validate individual indices
        if start_index < 0 {
            return Err(ValidationError::ArrayIndexOutOfBounds {
                index: start_index,
                length: array_length,
            });
        }

        if end_index > array_length {
            return Err(ValidationError::ArrayIndexOutOfBounds {
                index: end_index,
                length: array_length,
            });
        }

        // Validate range ordering
        if start_index > end_index {
            return Err(ValidationError::IntegerOverflow {
                operation: "array range validation",
                values: format!("start {start_index} > end {end_index}"),
            });
        }

        Ok(())
    }

    /// Enhanced client handle validation with overflow protection
    pub fn validate_client_handle_safe(handle: jlong) -> Result<u64, ValidationError> {
        // Check for negative handles (invalid)
        if handle < 0 {
            return Err(ValidationError::InvalidClientHandle);
        }

        // Check for zero handle (reserved)
        if handle == 0 {
            return Err(ValidationError::InvalidClientHandle);
        }

        // Safe conversion to u64
        let handle_u64 = handle as u64;

        // Check if client exists in registry
        if !client_exists(handle_u64) {
            return Err(ValidationError::ClientNotFound);
        }

        Ok(handle_u64)
    }
}

/// Convert validation error to JNI error for consistent error handling
impl From<ValidationError> for JniError {
    fn from(e: ValidationError) -> Self {
        match e {
            ValidationError::RequiredPointerNull(name) => {
                JniError::InvalidInput(format!("Required {name} is null"))
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
                format!("Array index {index} out of bounds (length: {length})"),
            ),
            ValidationError::NegativeArrayLength(length) => {
                JniError::InvalidInput(format!("Array length cannot be negative: {length}"))
            }
            ValidationError::IntegerOverflow { operation, values } => {
                JniError::InvalidInput(format!("Integer overflow in {operation}: {values}"))
            }
            ValidationError::BufferSizeExceedsLimit { size, limit } => {
                JniError::InvalidInput(format!("Buffer size {size} exceeds safety limit {limit}"))
            }
            ValidationError::InvalidUtf8 => {
                JniError::InvalidInput("Invalid UTF-8 sequence in string".to_string())
            }
            ValidationError::ArraySizeTooLarge {
                length,
                element_size,
            } => JniError::InvalidInput(format!(
                "Array size too large: {length} elements of {element_size} bytes each"
            )),
            ValidationError::JniError(e) => JniError::Runtime(format!("JNI error: {e}")),
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
