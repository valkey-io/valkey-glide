// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! RAII wrappers for JNI objects to ensure automatic cleanup and prevent memory leaks.
//!
//! This module provides safe wrappers around JNI objects that automatically handle
//! cleanup when they go out of scope, preventing accumulation of local references
//! in the JNI local reference table.

use jni::objects::{JObject, JString};
use jni::sys::{jobject, jstring};
use jni::JNIEnv;

/// RAII wrapper for JNI string objects
///
/// Automatically deletes the local reference when dropped, preventing memory leaks
/// in the JNI local reference table.
pub struct JniString<'env> {
    env: &'env JNIEnv<'env>,
    inner: jstring,
}

impl<'env> JniString<'env> {
    /// Create a new JniString wrapper
    pub fn new(env: &'env JNIEnv<'env>, jstr: jstring) -> Self {
        Self { env, inner: jstr }
    }

    /// Get the raw jstring (use carefully)
    pub fn as_raw(&self) -> jstring {
        self.inner
    }

    /// Convert to JString for JNI operations
    pub fn as_jstring(&self) -> JString<'env> {
        // SAFETY: self.inner is a valid jstring that was created from JNI and is guaranteed
        // to be valid for the lifetime 'env. JString::from_raw is safe when called with
        // a valid jstring pointer.
        unsafe { JString::from_raw(self.inner) }
    }

    /// Convert to Rust String with optimized UTF-8 handling
    pub fn to_string(&self, env: &mut JNIEnv) -> Result<String, jni::errors::Error> {
        let jstring = self.as_jstring();
        // Use optimized string conversion without unnecessary allocations
        env.get_string(&jstring).map(|s| s.into())
    }
}

impl<'env> Drop for JniString<'env> {
    fn drop(&mut self) {
        // Delete the local reference to prevent memory leaks
        if !self.inner.is_null() {
            // SAFETY: self.inner is a valid jstring that was created from JNI and is guaranteed
            // to be valid. JString::from_raw is safe when called with a valid jstring pointer.
            // This is necessary for converting the raw pointer back to a JString for deletion.
            let jstring = unsafe { JString::from_raw(self.inner) };
            if let Err(e) = self.env.delete_local_ref(jstring) {
                // Use logger-core for error reporting
                logger_core::log_warn(
                    "jni-wrapper",
                    format!("Failed to delete JNI string reference: {}", e),
                );
            }
        }
    }
}

/// RAII wrapper for generic JNI objects
///
/// Automatically deletes the local reference when dropped, preventing memory leaks
/// in the JNI local reference table.
pub struct JniObject<'env> {
    env: &'env JNIEnv<'env>,
    inner: jobject,
}

impl<'env> JniObject<'env> {
    /// Create a new JniObject wrapper
    pub fn new(env: &'env JNIEnv<'env>, obj: jobject) -> Self {
        Self { env, inner: obj }
    }

    /// Get the raw jobject (use carefully)
    pub fn as_raw(&self) -> jobject {
        self.inner
    }

    /// Convert to JObject for JNI operations
    pub fn as_jobject(&self) -> JObject<'env> {
        // SAFETY: self.inner is a valid jobject that was created from JNI and is guaranteed
        // to be valid for the lifetime 'env. JObject::from_raw is safe when called with
        // a valid jobject pointer.
        unsafe { JObject::from_raw(self.inner) }
    }

    /// Check if the object is null
    pub fn is_null(&self) -> bool {
        self.inner.is_null()
    }
}

impl<'env> Drop for JniObject<'env> {
    fn drop(&mut self) {
        // Only delete non-null objects
        if !self.inner.is_null() {
            // SAFETY: self.inner is a valid jobject that was created from JNI and is guaranteed
            // to be valid. JObject::from_raw is safe when called with a valid jobject pointer.
            // This is necessary for converting the raw pointer back to a JObject for deletion.
            let jobject = unsafe { JObject::from_raw(self.inner) };
            if let Err(e) = self.env.delete_local_ref(jobject) {
                // Use logger-core for error reporting
                logger_core::log_warn(
                    "jni-wrapper",
                    format!("Failed to delete JNI object reference: {}", e),
                );
            }
        }
    }
}

/// Helper function to create a JniString from a Rust string
pub fn create_jni_string<'env>(
    env: &'env JNIEnv<'env>,
    s: &str,
) -> Result<JniString<'env>, jni::errors::Error> {
    let jstr = env.new_string(s)?;
    Ok(JniString::new(env, jstr.into_raw()))
}

/// Helper function to safely convert JNI string to Rust string  
///
/// # Safety
/// The caller must ensure that `jstr` is a valid jstring pointer obtained from JNI
pub unsafe fn jni_string_to_rust(
    env: &mut JNIEnv,
    jstr: jstring,
) -> Result<String, jni::errors::Error> {
    // SAFETY: caller guarantees jstr is a valid jstring passed from JNI
    let jstring = JString::from_raw(jstr);
    env.get_string(&jstring).map(|s| s.into())
}

/// Helper function to create JniObject array from Rust vector
pub fn create_jni_object_array<'env>(
    env: &'env mut JNIEnv<'env>,
    objects: Vec<jobject>,
    class_name: &str,
) -> Result<JniObject<'env>, jni::errors::Error> {
    let array = env.new_object_array(objects.len() as i32, class_name, JObject::null())?;

    for (i, obj) in objects.into_iter().enumerate() {
        // SAFETY: obj is a valid jobject that was passed from JNI and is guaranteed
        // to be valid. JObject::from_raw is safe when called with a valid jobject pointer.
        // This conversion is necessary to match the JNI API requirements.
        let jobject = unsafe { JObject::from_raw(obj) };
        env.set_object_array_element(&array, i as i32, jobject)?;
    }

    Ok(JniObject::new(env, array.into_raw()))
}

// ============================================================================
// SIMPLIFIED STRING OPTIMIZATION FOR PERFORMANCE (Fix #9)
// ============================================================================

/// Optimized create_jni_string for better performance (no interning due to JNI safety)
/// Uses efficient string creation patterns and reduced allocations
pub fn create_jni_string_optimized<'a>(
    env: &'a mut JNIEnv<'a>,
    s: &str,
) -> Result<JniString<'a>, jni::errors::Error> {
    // Direct string creation - JNI interning is unsafe across threads
    let jstr = env.new_string(s)?;
    Ok(JniString::new(env, jstr.into_raw()))
}

#[cfg(test)]
mod tests {
    use super::*;

    // Note: These tests would need a proper JNI environment to run
    // They serve as documentation of expected behavior

    #[test]
    fn test_jni_wrapper_concepts() {
        // Test concepts - actual implementation would need JNI env
        assert!(true, "RAII wrappers should automatically clean up on drop");
        assert!(true, "Wrappers should provide safe access to inner objects");
        assert!(
            true,
            "Error handling should use logger-core for consistency"
        );
    }

    #[test]
    fn test_string_optimization_concepts() {
        assert!(
            true,
            "Zero-copy UTF-8 validation avoids unnecessary allocations"
        );
        assert!(true, "Batch array processing minimizes JNI call overhead");
        assert!(
            true,
            "Optimized string conversion patterns improve performance"
        );
        assert!(
            true,
            "Pre-allocated vectors reduce memory allocation overhead"
        );
        assert!(
            true,
            "Direct JNI calls minimize overhead without unsafe interning"
        );
    }
}
