use std::ffi::c_char;
use std::str::Utf8Error;
use crate::data::Utf8OrEmptyError;

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
pub fn grab_str_not_null(input: *const c_char) -> Result<String, Utf8OrEmptyError> {
    match grab_str(input) {
        Ok(d) => match d {
            None => Err(Utf8OrEmptyError::Empty),
            Some(s) => Ok(s),
        }
        Err(e) => Err(Utf8OrEmptyError::Utf8Error(e)),
    }
}

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
