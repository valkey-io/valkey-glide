// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use crate::buffering::FFIBuffer;
use crate::helpers;
use crate::helpers::grab_str;
use glide_core::client;
use glide_core::client::AuthenticationInfo;
use redis::VerbatimFormat;
use std::ffi::{
    c_double, c_int, c_long, c_longlong, c_uint, c_ulonglong, c_void, CString, NulError,
};
use std::fmt::{Display, Formatter};
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
                Some(client::ReadFrom::AZAffinityReplicasAndPrimary(value))
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
            EPeriodicCheckKind::ManualInterval => Some(client::PeriodicCheck::ManualInterval(
                Duration::from_secs(self.secs).add(Duration::from_nanos(self.nanos)),
            )),
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

pub mod routing {
    use crate::data::Utf8OrEmptyError;
    use crate::helpers;
    use redis::cluster_routing::ResponsePolicy;
    use std::ffi::{c_char, c_longlong, c_uint, c_ushort};

    #[repr(C)]
    pub struct RoutingInfo {
        pub kind: ERoutingInfo,
        pub value: RoutingInfoUnion,
    }
    impl RoutingInfo {
        pub unsafe fn to_redis(
            &self,
        ) -> Result<redis::cluster_routing::RoutingInfo, Utf8OrEmptyError> {
            Ok(match self.kind {
                ERoutingInfo::SingleRandom => redis::cluster_routing::RoutingInfo::SingleNode(
                    redis::cluster_routing::SingleNodeRoutingInfo::Random,
                ),
                ERoutingInfo::SingleRandomPrimary => {
                    redis::cluster_routing::RoutingInfo::SingleNode(
                        redis::cluster_routing::SingleNodeRoutingInfo::RandomPrimary,
                    )
                }
                ERoutingInfo::SingleSpecificNode => {
                    let route = self.value.specific_node.to_redis();
                    redis::cluster_routing::RoutingInfo::SingleNode(
                        redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(route),
                    )
                }
                ERoutingInfo::SingleByAddress => {
                    let address = self.value.by_address.to_redis()?;
                    redis::cluster_routing::RoutingInfo::SingleNode(address)
                }
                ERoutingInfo::MultiAllNodes => redis::cluster_routing::RoutingInfo::MultiNode((
                    redis::cluster_routing::MultipleNodeRoutingInfo::AllNodes,
                    self.value.multi.to_redis(),
                )),
                ERoutingInfo::MultiAllMasters => redis::cluster_routing::RoutingInfo::MultiNode((
                    redis::cluster_routing::MultipleNodeRoutingInfo::AllMasters,
                    self.value.multi.to_redis(),
                )),
                ERoutingInfo::MultiMultiSlot => {
                    let multi_slot = self.value.multi_slot.to_redis();
                    redis::cluster_routing::RoutingInfo::MultiNode(multi_slot)
                }
            })
        }
    }
    #[repr(C)]
    pub enum ERoutingInfo {
        /// Route to any node at random
        SingleRandom,
        /// Route to any *primary* node
        SingleRandomPrimary,
        /// Route to the node that matches the [Route]
        SingleSpecificNode,
        /// Route to the node with the given address.
        SingleByAddress,

        /// Route to all nodes in the clusters
        MultiAllNodes,
        /// Route to all primaries in the cluster
        MultiAllMasters,
        /// Routes the request to multiple slots.
        /// This variant contains instructions for splitting a multi-slot command (e.g., MGET, MSET) into sub-commands.
        /// Each tuple consists of a `Route` representing the target node for the subcommand,
        /// and a vector of argument indices from the original command that should be copied to each subcommand.
        /// The `MultiSlotArgPattern` specifies the pattern of the command’s arguments, indicating how they are organized
        /// (e.g., only keys, key-value pairs, etc).
        MultiMultiSlot,
    }

    #[repr(C)]
    pub struct RoutingInfoByAddress {
        /// DNS hostname of the node
        pub host: *const c_char,
        /// Length of host
        pub host_length: c_uint,
        /// port of the node
        pub port: c_ushort,
    }

    impl RoutingInfoByAddress {
        pub(crate) fn to_redis(
            &self,
        ) -> Result<redis::cluster_routing::SingleNodeRoutingInfo, Utf8OrEmptyError>
        {
            Ok(redis::cluster_routing::SingleNodeRoutingInfo::ByAddress {
                host: helpers::grab_str_not_null(self.host)?,
                port: self.port,
            })
        }
    }

    #[repr(C)]
    pub enum RouteSlotAddress {
        /// The request must be routed to primary node
        Master,
        /// The request may be routed to a replica node.
        /// For example, a GET command can be routed either to replica or primary.
        ReplicaOptional,
        /// The request must be routed to replica node, if one exists.
        /// For example, by user requested routing.
        ReplicaRequired,
    }
    #[repr(C)]
    pub struct Route {
        /// DNS hostname of the node
        pub slot: c_ushort,
        /// port of the node
        pub slot_addr: RouteSlotAddress,
    }

    impl Route {
        pub(crate) fn to_redis(&self) -> redis::cluster_routing::Route {
            redis::cluster_routing::Route::new(
                self.slot,
                match self.slot_addr {
                    RouteSlotAddress::Master => redis::cluster_routing::SlotAddr::Master,
                    RouteSlotAddress::ReplicaOptional => {
                        redis::cluster_routing::SlotAddr::ReplicaOptional
                    }
                    RouteSlotAddress::ReplicaRequired => {
                        redis::cluster_routing::SlotAddr::ReplicaRequired
                    }
                },
            )
        }
    }

    #[repr(C)]
    pub struct RoutingInfoMultiSlot {
        pub response_policy: ERoutingInfoMultiResponsePolicy,
        pub arg_pattern: ERoutingInfoMultiSlotArgPattern,
        pub routes: *const RoutingInfoMultiSlotPair,
        pub routes_length: c_uint,
    }

    impl RoutingInfoMultiSlot {
        pub(crate) fn to_redis(
            &self,
        ) -> (
            redis::cluster_routing::MultipleNodeRoutingInfo,
            Option<ResponsePolicy>,
        ) {
            let response_policy = self.response_policy.to_redis();
            let arg_pattern = self.arg_pattern.to_redis();
            let value = helpers::grab_vec(self.routes, self.routes_length as usize, |pair| {
                let route = pair.route.to_redis();
                let something = helpers::grab_vec(
                    pair.something,
                    pair.something_length as usize,
                    |something| Ok::<usize, ()>(*something as usize),
                )
                .unwrap();
                Ok::<(redis::cluster_routing::Route, Vec<usize>), ()>((route, something))
            })
            .unwrap();
            (
                redis::cluster_routing::MultipleNodeRoutingInfo::MultiSlot((value, arg_pattern)),
                response_policy,
            )
        }
    }

    #[repr(C)]
    pub struct RoutingInfoMultiSlotPair {
        pub route: Route,
        pub something: *const c_longlong,
        pub something_length: c_uint,
    }
    #[repr(C)]
    pub enum ERoutingInfoMultiSlotArgPattern {
        /// Pattern where only keys are provided in the command.
        /// For example: `MGET key1 key2`
        KeysOnly,

        /// Pattern where each key is followed by a corresponding value.
        /// For example: `MSET key1 value1 key2 value2`
        KeyValuePairs,

        /// Pattern where a list of keys is followed by a shared parameter.
        /// For example: `JSON.MGET key1 key2 key3 path`
        KeysAndLastArg,

        /// Pattern where each key is followed by two associated arguments, forming key-argument-argument triples.
        /// For example: `JSON.MSET key1 path1 value1 key2 path2 value2`
        KeyWithTwoArgTriples,
    }

    impl ERoutingInfoMultiSlotArgPattern {
        pub(crate) fn to_redis(&self) -> redis::cluster_routing::MultiSlotArgPattern {
            match self {
                ERoutingInfoMultiSlotArgPattern::KeysOnly => {
                    redis::cluster_routing::MultiSlotArgPattern::KeysOnly
                }
                ERoutingInfoMultiSlotArgPattern::KeyValuePairs => {
                    redis::cluster_routing::MultiSlotArgPattern::KeyValuePairs
                }
                ERoutingInfoMultiSlotArgPattern::KeysAndLastArg => {
                    redis::cluster_routing::MultiSlotArgPattern::KeysAndLastArg
                }
                ERoutingInfoMultiSlotArgPattern::KeyWithTwoArgTriples => {
                    redis::cluster_routing::MultiSlotArgPattern::KeyWithTwoArgTriples
                }
            }
        }
    }

    #[repr(C)]
    pub enum ERoutingInfoMultiResponsePolicy {
        /// Unspecified response policy
        None = 0,
        /// Wait for one request to succeed and return its results. Return error if all requests fail.
        OneSucceeded = 1,
        /// Returns the first succeeded non-empty result; if all results are empty, returns `Nil`; otherwise, returns the last received error.
        FirstSucceededNonEmptyOrAllEmpty = 3,
        /// Waits for all requests to succeed, and the returns one of the successes. Returns the error on the first received error.
        AllSucceeded = 4,
        /// Aggregate array responses into a single array. Return error on any failed request or on a response that isn't an array.
        CombineArrays = 5,
        /// Handling is not defined by the Redis standard. Will receive a special case
        Special = 6,
        /// Combines multiple map responses into a single map.
        CombineMaps = 7,

        /// Aggregate success results according to a logical bitwise operator. Return error on any failed request or on a response that doesn't conform to 0 or 1.
        AggregateLogicalWithAnd = 50,

        /// Aggregate success results according to a numeric operator. Return error on any failed request or on a response that isn't an integer.
        /// Choose minimal value
        AggregateWithMin = 70,
        /// Aggregate success results according to a numeric operator. Return error on any failed request or on a response that isn't an integer.
        /// Sum all values
        AggregateWithSum = 71,
    }

    impl ERoutingInfoMultiResponsePolicy {
        pub(crate) fn to_redis(&self) -> Option<redis::cluster_routing::ResponsePolicy> {
            match self {
                ERoutingInfoMultiResponsePolicy::None => None,
                ERoutingInfoMultiResponsePolicy::OneSucceeded => {
                    Some(redis::cluster_routing::ResponsePolicy::OneSucceeded)
                }
                ERoutingInfoMultiResponsePolicy::FirstSucceededNonEmptyOrAllEmpty => {
                    Some(redis::cluster_routing::ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty)
                }
                ERoutingInfoMultiResponsePolicy::AllSucceeded => {
                    Some(redis::cluster_routing::ResponsePolicy::AllSucceeded)
                }
                ERoutingInfoMultiResponsePolicy::CombineArrays => {
                    Some(redis::cluster_routing::ResponsePolicy::CombineArrays)
                }
                ERoutingInfoMultiResponsePolicy::Special => {
                    Some(redis::cluster_routing::ResponsePolicy::Special)
                }
                ERoutingInfoMultiResponsePolicy::CombineMaps => {
                    Some(redis::cluster_routing::ResponsePolicy::CombineMaps)
                }
                ERoutingInfoMultiResponsePolicy::AggregateLogicalWithAnd => {
                    Some(redis::cluster_routing::ResponsePolicy::AggregateLogical(
                        redis::cluster_routing::LogicalAggregateOp::And,
                    ))
                }
                ERoutingInfoMultiResponsePolicy::AggregateWithMin => {
                    Some(redis::cluster_routing::ResponsePolicy::Aggregate(
                        redis::cluster_routing::AggregateOp::Min,
                    ))
                }
                ERoutingInfoMultiResponsePolicy::AggregateWithSum => {
                    Some(redis::cluster_routing::ResponsePolicy::Aggregate(
                        redis::cluster_routing::AggregateOp::Sum,
                    ))
                }
            }
        }
    }

    #[repr(C)]
    pub union RoutingInfoUnion {
        /// Set if ERoutingInfo is ByAddress
        pub by_address: std::mem::ManuallyDrop<RoutingInfoByAddress>,

        /// Set if ERoutingInfo is SpecificNode
        pub specific_node: std::mem::ManuallyDrop<Route>,

        /// Set if ERoutingInfo is MultiAllNodes or MultiAllMasters
        pub multi: std::mem::ManuallyDrop<ERoutingInfoMultiResponsePolicy>,

        /// Set if ERoutingInfo is MultiMultiSlot
        pub multi_slot: std::mem::ManuallyDrop<RoutingInfoMultiSlot>,
    }
}
