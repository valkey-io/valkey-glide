use std::ffi::{c_double, c_long, c_void, CString, NulError};
use std::fmt::Formatter;
use std::os::raw::c_char;
use std::ptr::null;
use std::str::FromStr;
use redis::VerbatimFormat;
use crate::buffering::FFIBuffer;
use crate::helpers;

#[repr(C)]
pub union ValueUnion {
    pub i: c_long,
    pub f: c_double,
    pub ptr: *const c_void,
}
#[repr(C)]
#[allow(dead_code)]
#[derive(Debug)]
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
    pub fn from_redis(value: &redis::Value, buffer: &mut FFIBuffer) -> Result<Self, ValueError> {
        Ok(match value {
            redis::Value::Nil => Self::nil(),
            redis::Value::Int(i) => Self {
                data: ValueUnion { i: *i as c_long },
                length: 0,
                kind: ValueKind::Int,
            },
            redis::Value::BulkString(d) => {
                let result = Self {
                    data: ValueUnion {
                        ptr: buffer.write_to_buffer(d.as_slice()) as *mut c_void,
                    },
                    length: d.len() as c_long,
                    kind: ValueKind::BulkString,
                };
                result
            }
            redis::Value::Array(values) => {
                // ToDo: Optimize the allocation here with buffer too
                let values = values
                    .iter()
                    .map(|d| Value::from_redis(d, buffer))
                    .collect::<Result<Vec<_>, _>>()?;
                let result = Self {
                    data: ValueUnion {
                        ptr: buffer.write_values_to_buffer(values.as_slice()) as *mut c_void,
                    },
                    length: values.len() as c_long,
                    kind: ValueKind::Set,
                };
                result
            }
            redis::Value::SimpleString(s) => return Self::simple_string(s.as_str(), Some(buffer)),
            redis::Value::Okay => Self::okay(),
            redis::Value::Map(tuples) => {
                let mut out_tuples = Vec::with_capacity(tuples.len() * 2);
                for (k, v) in tuples {
                    out_tuples.push(Value::from_redis(k, buffer)?);
                    out_tuples.push(Value::from_redis(v, buffer)?);
                }
                Self {
                    data: ValueUnion {
                        ptr: buffer.write_values_to_buffer(out_tuples.as_slice()) as *mut c_void,
                    },
                    length: tuples.len() as c_long,
                    kind: ValueKind::Map,
                }
            }
            redis::Value::Attribute { .. } => {
                todo!("Implement")
            }
            redis::Value::Set(values) => {
                let values = values
                    .iter()
                    .map(|d| Value::from_redis(d, buffer))
                    .collect::<Result<Vec<_>, _>>()?;
                let result = Self {
                    data: ValueUnion {
                        ptr: buffer.write_values_to_buffer(values.as_slice()) as *mut c_void,
                    },
                    length: values.len() as c_long,
                    kind: ValueKind::Set,
                };
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
                Self {
                    length: (size_of::<StringPair>() + format_length + text.len()) as c_long,
                    kind: ValueKind::VerbatimString,
                    data: ValueUnion {
                        ptr: buffer.write_string_pair_to_buffer(format, text) as *mut c_void,
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

impl Value {
    pub fn simple_string(s: &str, buffer: Option<&mut FFIBuffer>) -> Result<Self, ValueError> {
        if s.len() == 0 {
            Ok(Self {
                data: ValueUnion { ptr: null() },
                length: 0,
                kind: ValueKind::SimpleString,
            })
        } else {
            if let Some(buffer) = buffer {
                Ok(Self {
                    data: ValueUnion {
                        ptr: match CString::from_str(s) {
                            Ok(d) => buffer.write_string_to_buffer(&d) as *mut c_void,
                            Err(e) => return Err(ValueError::NulError(e)),
                        },
                    },
                    length: s.len() as c_long,
                    kind: ValueKind::SimpleString,
                })
            } else {
                Ok(Self {
                    data: ValueUnion {
                        ptr: match CString::from_str(s) {
                            Ok(d) => d.into_raw() as *mut c_void,
                            Err(e) => return Err(ValueError::NulError(e)),
                        },
                    },
                    length: s.len() as c_long,
                    kind: ValueKind::SimpleString,
                })
            }
        }
    }
    pub fn simple_string_with_null(s: &str) -> Self {
        let ptr = helpers::to_cstr_ptr_or_null(s) as *mut c_void;
        let len = s.len() as c_long;
        Self {
            data: ValueUnion { ptr },
            length: if !ptr.is_null() { len } else { 0 },
            kind: ValueKind::SimpleString,
        }
    }
    pub fn nil() -> Self {
        Self {
            data: ValueUnion { i: 0 },
            length: 0,
            kind: ValueKind::Nil,
        }
    }
    pub fn okay() -> Self {
        Self {
            data: ValueUnion { i: 0 },
            length: 0,
            kind: ValueKind::Okay,
        }
    }
}