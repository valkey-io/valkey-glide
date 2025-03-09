// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use tokio::runtime::Runtime;

use redis::VerbatimFormat;
use std::ffi::{c_double, c_int, c_long, c_void, CString, NulError};
use std::fmt::Formatter;
use std::mem::forget;
use std::os::raw::c_char;
use std::ptr::null;
use std::str::{FromStr, Utf8Error};
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
pub enum Utf8OrEmptyError {
    Utf8Error(Utf8Error),
    Empty,
}

#[repr(C)]
pub struct InitResult {
    pub success: c_int,
    pub logger_level: ELoggerLevel,
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
#[repr(C)]
pub struct BlockingCommandResult {
    pub success: c_int,
    pub error_string: *const c_char,
    pub value: Value,
}

pub type CommandCallback =
    unsafe extern "C-unwind" fn(data: *mut c_void, success: c_int, output: Value);

#[repr(C)]
pub union ValueUnion {
    pub i: c_long,
    pub f: c_double,
    pub ptr: *const c_void,
}
#[repr(C)]
#[allow(dead_code)]
pub enum ValueKind {
    /// # Summary
    /// A nil response from the server.
    ///
    /// # Implications for union
    /// Union value must be ignored.
    Nil,
    /// # Summary
    /// An integer response.  Note that there are a few situations
    /// in which redis actually returns a string for an integer which
    /// is why this library generally treats integers and strings
    /// the same for all numeric responses.
    ///
    /// # Implications for union
    /// Union value will be set as c_long.
    /// It can be safely consumed without freeing.
    Int,
    /// # Summary
    /// An arbitrary binary data, usually represents a binary-safe string.
    ///
    /// # Implications for union
    /// Union will, in ptr, contain an array of c_char (bytes).
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    BulkString,
    /// # Summary
    /// A response containing an array with more data.
    /// This is generally used by redis to express nested structures.
    ///
    /// # Implications for union
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    Array,
    /// # Summary
    /// A simple string response, without line breaks and not binary safe.
    ///
    /// # Implications for union
    /// Union will, in ptr, contain a c_str.
    /// See CommandResult.length for the length of the string, excluding the zero byte.
    /// ValueUnion.ptr MUST be freed.
    SimpleString,
    /// # Summary
    /// A status response which represents the string "OK".
    ///
    /// # Implications for union
    /// Union value must be ignored.
    Okay,
    /// # Summary
    /// Unordered key,value list from the server. Use `as_map_iter` function.
    ///
    /// # Implications for union
    /// Union will, in ptr, contain an array of CommandResult's which are supposed to be interpreted as key-value pairs.
    /// See CommandResult.length for the number of pairs (aka: elements * 2).
    /// ValueUnion.ptr MUST be freed.
    Map,
    /// Placeholder
    /// ToDo: Figure out a way to map this to C-Memory
    Attribute,
    /// # Summary
    /// Unordered set value from the server.
    ///
    /// # Implications for union
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    Set,
    /// # Summary
    /// A floating number response from the server.
    ///
    /// # Implications for union
    /// Union value will be set as c_double.
    /// It can be safely consumed without freeing.
    Double,
    /// # Summary
    /// A boolean response from the server.
    ///
    /// # Implications for union
    /// Union value will be set as c_long.
    /// It can be safely consumed without freeing.
    Boolean,
    /// # Summary
    /// First String is format and other is the string
    ///
    /// # Implications for union
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    ///
    /// ## Remarks
    /// First result will be verbatim-kind
    /// Second will be string
    VerbatimString,
    /// # Summary
    /// Very large number that out of the range of the signed 64 bit numbers
    ///
    /// # Implications for union
    /// Union will, in ptr, contain a StringPair
    /// ValueUnion.ptr MUST be freed.
    BigNumber,
    /// # Summary
    /// Push data from the server.
    ///
    /// # Implications for union
    /// Union will, in ptr, contain an array of CommandResult's.
    /// See CommandResult.length for the number of elements.
    /// ValueUnion.ptr MUST be freed.
    ///
    /// ## Remarks
    /// First result will be push-kind
    /// Second will be array of results
    Push,
}
#[repr(C)]
pub struct Value {
    pub kind: ValueKind,
    pub data: ValueUnion,
    pub length: c_long,
}

#[repr(C)]
pub struct StringPair {
    pub a_start: *mut c_char,
    pub a_end: *mut c_char,
    pub b_start: *mut c_char,
    pub b_end: *mut c_char,
}

#[derive(Clone, PartialEq, Eq, Debug)]
pub enum ValueError {
    NulError(NulError),
}
impl std::fmt::Display for ValueError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            ValueError::NulError(e) => e.fmt(f),
        }
    }
}

impl Value {
    // ToDo: Create a new "blob" creating method that first counts the bytes needed,
    //       allocates one big blob and secondly fills in the bytes in that blob, returning
    //       just that as ValueBlob to allow better large-scale result operations.
    pub fn from_redis(value: &redis::Value) -> Result<Self, ValueError> {
        unsafe {
            Ok(match value {
                redis::Value::Nil => Self {
                    data: ValueUnion { ptr: null() },
                    length: 0,
                    kind: ValueKind::Nil,
                },
                redis::Value::Int(i) => Self {
                    data: ValueUnion { i: *i as c_long },
                    length: 0,
                    kind: ValueKind::Int,
                },
                redis::Value::BulkString(d) => {
                    let mut d = d.clone();
                    d.shrink_to_fit();
                    assert_eq!(d.len(), d.capacity());
                    Self {
                        data: ValueUnion {
                            ptr: d.as_mut_ptr() as *mut c_void,
                        },
                        length: d.len() as c_long,
                        kind: ValueKind::SimpleString,
                    }
                }
                redis::Value::Array(values) => {
                    let mut values = values
                        .iter()
                        .map(|d| Value::from_redis(d))
                        .collect::<Result<Vec<_>, _>>()?;
                    values.shrink_to_fit();
                    assert_eq!(values.len(), values.capacity());
                    let result = Self {
                        data: ValueUnion {
                            ptr: values.as_mut_ptr() as *mut c_void,
                        },
                        length: values.len() as c_long,
                        kind: ValueKind::Set,
                    };
                    forget(values);
                    result
                }
                redis::Value::SimpleString(s) => Self {
                    data: ValueUnion {
                        ptr: match CString::from_str(s.as_str()) {
                            Ok(d) => d.into_raw() as *mut c_void,
                            Err(e) => return Err(ValueError::NulError(e)),
                        },
                    },
                    length: s.len() as c_long,
                    kind: ValueKind::SimpleString,
                },
                redis::Value::Okay => Self {
                    data: ValueUnion { ptr: null() },
                    length: 0,
                    kind: ValueKind::Okay,
                },
                redis::Value::Map(tuples) => {
                    let mut out_tuples = Vec::with_capacity(tuples.len() * 2);
                    for (k, v) in tuples {
                        out_tuples.push(Value::from_redis(k));
                        out_tuples.push(Value::from_redis(v));
                    }
                    out_tuples.shrink_to_fit();
                    Self {
                        data: ValueUnion {
                            ptr: out_tuples.as_mut_ptr() as *mut c_void,
                        },
                        length: tuples.len() as c_long,
                        kind: ValueKind::Map,
                    }
                }
                redis::Value::Attribute { .. } => {
                    todo!("Implement")
                }
                redis::Value::Set(values) => {
                    let mut values = values
                        .iter()
                        .map(|d| Value::from_redis(d))
                        .collect::<Result<Vec<_>, _>>()?;
                    values.shrink_to_fit();
                    assert_eq!(values.len(), values.capacity());
                    let result = Self {
                        data: ValueUnion {
                            ptr: values.as_mut_ptr() as *mut c_void,
                        },
                        length: values.len() as c_long,
                        kind: ValueKind::Set,
                    };
                    forget(values);
                    result
                }
                redis::Value::Double(d) => Self {
                    data: ValueUnion { f: *d },
                    length: 0,
                    kind: ValueKind::Double,
                },
                redis::Value::Boolean(b) => Self {
                    data: ValueUnion { i: *b as c_long },
                    length: 0,
                    kind: ValueKind::Boolean,
                },
                redis::Value::VerbatimString { format, text } => {
                    let format_length = match format {
                        VerbatimFormat::Unknown(unknown) => unknown.len(),
                        VerbatimFormat::Markdown => "markdown".len(),
                        VerbatimFormat::Text => "text".len(),
                    };
                    let format = match format {
                        VerbatimFormat::Unknown(unknown) => unknown,
                        VerbatimFormat::Markdown => &"markdown".to_string(),
                        VerbatimFormat::Text => &"text".to_string(),
                    };
                    let mut vec = Vec::<u8>::with_capacity(
                        size_of::<StringPair>() + format_length + text.len(),
                    );
                    let out_vec = vec.as_mut_ptr(); // we leak here
                    let output = StringPair {
                        a_start: out_vec.add(size_of::<StringPair>()) as *mut c_char,
                        a_end: out_vec.add(size_of::<StringPair>() + format_length) as *mut c_char,
                        b_start: out_vec.add(size_of::<StringPair>() + format_length)
                            as *mut c_char,
                        b_end: out_vec.add(size_of::<StringPair>() + format_length + text.len())
                            as *mut c_char,
                    };
                    for i in 0..format_length {
                        *output.a_start.wrapping_add(i) = format.as_ptr().wrapping_add(i) as c_char
                    }
                    for i in 0..text.len() {
                        *output.b_start.wrapping_add(i) = text.as_ptr().wrapping_add(i) as c_char
                    }
                    Self {
                        length: vec.len() as c_long,
                        kind: ValueKind::VerbatimString,
                        data: ValueUnion {
                            ptr: out_vec as *mut c_void,
                        },
                    }
                }
                redis::Value::BigNumber(_) => {
                    todo!("Implement")
                }
                redis::Value::Push { .. } => {
                    todo!("Implement")
                }
            })
        }
    }
}

impl Value {
    pub fn nil() -> Self {
        Self {
            data: ValueUnion { i: 0 },
            length: 0,
            kind: ValueKind::Nil,
        }
    }
    pub unsafe fn free_data(&mut self) {
        match self.kind {
            ValueKind::Nil => { /* empty */ }
            ValueKind::Int => { /* empty */ }
            ValueKind::BulkString => drop(Vec::from_raw_parts(
                self.data.ptr as *mut u8,
                self.length as usize,
                self.length as usize,
            )),
            ValueKind::Array => {
                let mut values = Vec::from_raw_parts(
                    self.data.ptr as *mut Value,
                    self.length as usize,
                    self.length as usize,
                );
                for value in values.iter_mut() {
                    value.free_data()
                }
                drop(values);
            }
            ValueKind::SimpleString => drop(CString::from_raw(self.data.ptr as *mut c_char)),
            ValueKind::Okay => { /* empty */ }
            ValueKind::Map => {
                let mut values = Vec::from_raw_parts(
                    self.data.ptr as *mut Value,
                    self.length as usize * 2,
                    self.length as usize * 2,
                );
                for value in values.iter_mut() {
                    value.free_data()
                }
                drop(values);
            }
            ValueKind::Attribute => {
                todo!("Implement")
            }
            ValueKind::Set => {
                let mut values = Vec::from_raw_parts(
                    self.data.ptr as *mut Value,
                    self.length as usize,
                    self.length as usize,
                );
                for value in values.iter_mut() {
                    value.free_data()
                }
                drop(values);
            }
            ValueKind::Double => { /* empty */ }
            ValueKind::Boolean => { /* empty */ }
            ValueKind::VerbatimString => {
                let vec = Vec::from_raw_parts(
                    self.data.ptr as *mut u8,
                    self.length as usize,
                    self.length as usize,
                );
                drop(vec);
            }
            ValueKind::BigNumber => {
                todo!("Implement")
            }
            ValueKind::Push => {
                todo!("Implement")
            }
        }
    }
}
