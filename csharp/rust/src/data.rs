// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::value::Value;
use std::ffi::{
    c_int, c_void,
};
use std::fmt::{Display, Formatter};
use std::os::raw::c_char;
use std::ptr::null;
use std::str::Utf8Error;
use tokio::runtime::Runtime;

pub struct FFIHandle {
    pub runtime: Runtime,
    pub handle: crate::apihandle::Handle,
}

#[repr(C)]
#[derive(PartialEq)]
pub struct CreateClientHandleResult {
    pub result: ECreateClientHandleCode,
    pub client_handle: *const c_void,
    pub error_string: *const c_char,
}

#[repr(C)]
#[derive(PartialEq, Eq, Debug, Clone, Copy, Hash)]
pub enum ECreateClientHandleCode {
    Success = 0,
    ParameterError = 1,
    ThreadCreationError = 2,
    ConnectionTimedOutError = 3,
    ConnectionToFailedError = 4,
    ConnectionToClusterFailed = 5,
    ConnectionIoError = 6,
}

#[repr(C)]
pub struct NodeAddress {
    pub host: *const c_char,
    pub port: u16,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Utf8OrEmptyError {
    Utf8Error(Utf8Error),
    Empty,
}
impl From<Utf8Error> for Utf8OrEmptyError {
    fn from(value: Utf8Error) -> Self {
        Self::Utf8Error(value)
    }
}

impl Display for Utf8OrEmptyError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            Utf8OrEmptyError::Utf8Error(e) => e.fmt(f),
            Utf8OrEmptyError::Empty => "Empty".fmt(f),
        }
    }
}

#[repr(C)]
#[allow(dead_code)]
pub enum ELoggerLevel {
    None = 0,
    Error = 1,
    Warn = 2,
    Info = 3,
    Debug = 4,
    Trace = 5,
    Off = 6,
}
#[repr(C)]
pub struct CommandResult {
    pub success: c_int,
    pub error_string: *const c_char,
}

impl CommandResult {
    pub fn new_success() -> Self {
        Self {
            success: 1,
            error_string: null(),
        }
    }

    pub fn new_error(error_message: *const c_char) -> Self {
        Self {
            success: 0,
            error_string: error_message,
        }
    }
}

pub type CommandCallback =
    unsafe extern "C-unwind" fn(data: *mut c_void, success: c_int, output: Value);



