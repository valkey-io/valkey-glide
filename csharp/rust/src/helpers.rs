use crate::data::Utf8OrEmptyError;
use crate::helpers;
use std::ffi::{c_char, CString};
use std::ptr::null;
use std::slice::from_raw_parts;
use std::str::{FromStr, Utf8Error};

/// # Summary
/// Convert raw C string to an optional rust string.
///
/// # Safety
/// * Dereferenced [input] ptr must contain a valid ptr or null.
pub fn grab_str(input: *const c_char) -> Result<Option<String>, Utf8Error> {
    if input.is_null() {
        return Ok(None);
    }
    unsafe {
        let c_str = std::ffi::CStr::from_ptr(input);
        match c_str.to_str() {
            Ok(d) => Ok(Some(d.to_string())),
            Err(e) => Err(e),
        }
    }
}
/// # Summary
/// Convert raw C string to a rust string.
///
/// # Safety
/// * Dereferenced [input] ptr must contain a valid ptr or null.
pub fn grab_str_not_null(input: *const c_char) -> Result<String, Utf8OrEmptyError> {
    match grab_str(input) {
        Ok(d) => match d {
            None => Err(Utf8OrEmptyError::Empty),
            Some(s) => Ok(s),
        },
        Err(e) => Err(Utf8OrEmptyError::Utf8Error(e)),
    }
}

/// # Summary
/// Helper method to transfer a raw array ptr of [TIn] to a [Vec<TOut>], using a transform method
///
/// # Safety
/// * Dereferenced [input] ptr and elements must contain a valid ptr or null.
pub fn grab_vec<TIn, TOut, TErr>(
    input: *const TIn,
    len: usize,
    f: impl Fn(&TIn) -> Result<TOut, TErr>,
) -> Result<Vec<TOut>, TErr> {
    let mut result = Vec::with_capacity(len);
    unsafe {
        for i in 0..len {
            let it: *const TIn = input.offset(i as isize);
            result.push(f(&*it)?);
        }
        Ok(result)
    }
}

/// # Summary
/// Helper method to transfer a raw array ptr of [c_char] to a [Vec<String>], using a transform method
///
/// # Safety
/// * Dereferenced [input] ptr and elements must contain a valid ptr or null.
pub fn grab_vec_str(
    input: *const *const c_char,
    len: usize,
) -> Result<Vec<String>, Utf8OrEmptyError> {
    grab_vec(
        input,
        len as usize,
        |it| -> Result<String, Utf8OrEmptyError> {
            match helpers::grab_str(*it) {
                Ok(d) => match d {
                    None => Err(Utf8OrEmptyError::Empty),
                    Some(d) => Ok(d),
                },
                Err(e) => Err(Utf8OrEmptyError::Utf8Error(e)),
            }
        },
    )
}

/// # Summary
/// Helper method transforming a rust [&str] to a raw [c_char] ptr
///
/// # Safety
/// This method will leak memory! Always make sure to capture the [c_char] ptr again
/// using [CString::from_raw]
pub fn to_cstr_ptr_or_null(input: &str) -> *const c_char {
    match CString::from_str(input) {
        Ok(d) => d.into_raw() as *const std::os::raw::c_char,
        Err(_) => null(),
    }
}

/// # Summary
/// Helper method to transform any given input to a slice of bytes.
///
/// # Safety
/// See [from_raw_parts] for safety information.
pub unsafe fn any_as_u8_slice<T: Sized>(p: &T) -> &[u8] {
    from_raw_parts((p as *const T) as *const u8, size_of::<T>())
}
