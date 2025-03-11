// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::helpers;
use crate::helpers::grab_str;
use glide_core::client;
use glide_core::client::AuthenticationInfo;
use redis::{RedisWrite, VerbatimFormat};
use std::ffi::{
    c_double, c_int, c_long, c_longlong, c_uint, c_ulonglong, c_void, CString,
    NulError,
};
use std::fmt::Formatter;
use std::mem::forget;
use std::ops::Add;
use std::os::raw::c_char;
use std::ptr::null;
use std::str::{FromStr, Utf8Error};
use std::time::Duration;
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
impl From<Utf8Error> for Utf8OrEmptyError {
    fn from(value: Utf8Error) -> Self {
        Self::Utf8Error(value)
    }
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


#[repr(C)]
pub enum EReadFromKind {
    None = 0,
    Primary = 1,
    PreferReplica = 2,

    // Define using ReadFrom.value
    AZAffinity = 3,
    // Define using ReadFrom.value
    AZAffinityReplicasAndPrimary = 4,
}

#[repr(C)]
pub struct ReadFrom {
    pub kind: EReadFromKind,
    pub value: *const c_char,
}

impl ReadFrom {
    pub fn to_redis(self) -> Result<Option<client::ReadFrom>, Utf8OrEmptyError> {
        Ok(match self.kind {
            EReadFromKind::None => None,
            EReadFromKind::Primary => Some(client::ReadFrom::Primary),
            EReadFromKind::PreferReplica => Some(client::ReadFrom::PreferReplica),
            EReadFromKind::AZAffinity => {
                let value = helpers::grab_str_not_null(self.value)?;
                Some(client::ReadFrom::AZAffinity(value))
            }
            EReadFromKind::AZAffinityReplicasAndPrimary => {
                let value = helpers::grab_str_not_null(self.value)?;
                Some(client::ReadFrom::AZAffinityReplicasAndPrimary(
                    value,
                ))
            }
        })
    }
}
#[repr(C)]
pub enum EProtocolVersion {
    None,
    /// <https://github.com/redis/redis-specifications/blob/master/protocol/RESP2.md>
    RESP2,
    /// <https://github.com/redis/redis-specifications/blob/master/protocol/RESP3.md>
    RESP3,
}

impl EProtocolVersion {
    pub fn to_redis(self) -> Option<redis::ProtocolVersion> {
        match self {
            EProtocolVersion::None => None,
            EProtocolVersion::RESP2 => Some(redis::ProtocolVersion::RESP2),
            EProtocolVersion::RESP3 => Some(redis::ProtocolVersion::RESP3),
        }
    }
}

#[repr(C)]
pub enum ETlsMode {
    None = 0,
    NoTls = 1,
    InsecureTls = 2,
    SecureTls = 3,
}

impl ETlsMode {
    pub fn to_redis(self) -> Option<client::TlsMode> {
        match self {
            ETlsMode::None => None,
            ETlsMode::NoTls => Some(client::TlsMode::NoTls),
            ETlsMode::InsecureTls => Some(client::TlsMode::InsecureTls),
            ETlsMode::SecureTls => Some(client::TlsMode::SecureTls),
        }
    }
}

#[repr(C)]
pub struct ConnectionRetryStrategy {
    pub ignore: c_int,
    pub exponent_base: c_uint,
    pub factor: c_uint,
    pub number_of_retries: c_uint,
}

impl ConnectionRetryStrategy {
    pub fn to_redis(self) -> Option<client::ConnectionRetryStrategy> {
        if self.ignore == 0 {
            return Some(client::ConnectionRetryStrategy {
                exponent_base: self.exponent_base,
                factor: self.factor,
                number_of_retries: self.number_of_retries,
            });
        } else {
            None
        }
    }
}

#[repr(C)]
pub struct PeriodicCheck {
    pub kind: EPeriodicCheckKind,
    pub secs: c_ulonglong,
    pub nanos: c_ulonglong,
}
#[repr(C)]
pub enum EPeriodicCheckKind {
    None = 0,
    Enabled = 1,
    Disabled = 2,

    /// secs and nanos on PeriodicCheck must be set
    ManualInterval = 3,
}

impl PeriodicCheck {
    pub fn to_redis(self) -> Option<client::PeriodicCheck> {
        match self.kind {
            EPeriodicCheckKind::None => None,
            EPeriodicCheckKind::Enabled => Some(client::PeriodicCheck::Enabled),
            EPeriodicCheckKind::Disabled => Some(client::PeriodicCheck::Disabled),
            EPeriodicCheckKind::ManualInterval => {
                Some(client::PeriodicCheck::ManualInterval(
                    Duration::from_secs(self.secs).add(Duration::from_nanos(self.nanos)),
                ))
            }
        }
    }
}

#[repr(C)]
pub struct OptionalU32 {
    pub ignore: c_int,
    pub value: c_uint,
}
impl Into<Option<u32>> for OptionalU32 {
    fn into(self) -> Option<u32> {
        if self.ignore == 0 {
            Some(self.value)
        } else {
            None
        }
    }
}
#[repr(C)]
pub struct OptionalU64 {
    pub ignore: c_int,
    pub value: c_ulonglong,
}

impl Into<Option<u64>> for OptionalU64 {
    fn into(self) -> Option<u64> {
        if self.ignore == 0 {
            Some(self.value)
        } else {
            None
        }
    }
}

#[repr(C)]
pub struct ConnectionRequest {
    pub read_from: ReadFrom,
    pub client_name: *const c_char,
    pub auth_username: *const c_char,
    pub auth_password: *const c_char,
    pub database_id: c_longlong,
    pub protocol: EProtocolVersion,
    pub tls_mode: ETlsMode,
    pub addresses: *const NodeAddress,
    pub addresses_length: c_uint,
    pub cluster_mode_enabled: c_int,
    pub request_timeout: OptionalU32,
    pub connection_timeout: OptionalU32,
    pub connection_retry_strategy: ConnectionRetryStrategy,
    pub periodic_checks: PeriodicCheck,
    pub inflight_requests_limit: OptionalU32,
    pub otel_endpoint: *const c_char,
    pub otel_span_flush_interval_ms: OptionalU64,
    // ToDo: Enable pubsub_subscriptions
    // pub pubsub_subscriptions: Option<redis::PubSubSubscriptionInfo>,
}

impl ConnectionRequest {
    pub fn to_redis(self) -> Result<client::ConnectionRequest, Utf8OrEmptyError> {
        let addresses = helpers::grab_vec(
            self.addresses,
            self.addresses_length as usize,
            |it| -> Result<client::NodeAddress, Utf8OrEmptyError> {
                let host = match grab_str(it.host) {
                    Ok(d) => d,
                    Err(e) => return Err(Utf8OrEmptyError::Utf8Error(e)),
                };
                let host = match host {
                    Some(host) => host,
                    None => return Err(Utf8OrEmptyError::Empty),
                };
                let port = it.port;
                Ok(client::NodeAddress { host, port })
            },
        )?;

        Ok(client::ConnectionRequest {
            read_from: self.read_from.to_redis()?,
            client_name: grab_str(self.client_name)?,
            authentication_info: if !self.auth_username.is_null() && !self.auth_password.is_null() {
                Some(AuthenticationInfo {
                    username: grab_str(self.auth_username)?,
                    password: grab_str(self.auth_password)?,
                })
            } else {
                None
            },
            database_id: self.database_id,
            protocol: self.protocol.to_redis(),
            tls_mode: self.tls_mode.to_redis(),
            addresses,
            cluster_mode_enabled: self.cluster_mode_enabled != 0,
            request_timeout: self.request_timeout.into(),
            connection_timeout: self.connection_timeout.into(),
            connection_retry_strategy: self.connection_retry_strategy.to_redis(),
            periodic_checks: self.periodic_checks.to_redis(),
            inflight_requests_limit: self.inflight_requests_limit.into(),
            otel_endpoint: grab_str(self.otel_endpoint)?,
            otel_span_flush_interval_ms: self.otel_span_flush_interval_ms.into(),

            pubsub_subscriptions: None,
        })
    }
}
