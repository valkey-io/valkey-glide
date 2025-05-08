// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::{
    ffi::{CStr, c_char},
    slice::from_raw_parts,
};

use glide_core::{
    client::{
        AuthenticationInfo, ConnectionRequest, ConnectionRetryStrategy, NodeAddress,
        ReadFrom as coreReadFrom, TlsMode,
    },
    request_type::RequestType,
};
use redis::{
    Cmd, Value,
    cluster_routing::{
        MultipleNodeRoutingInfo, ResponsePolicy, Routable, Route, RoutingInfo,
        SingleNodeRoutingInfo, SlotAddr,
    },
};

/// Convert raw C string to a rust string.
///
/// # Safety
///
/// * `ptr` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
unsafe fn ptr_to_str(ptr: *const c_char) -> String {
    if !ptr.is_null() {
        unsafe { CStr::from_ptr(ptr) }.to_str().unwrap().into()
    } else {
        "".into()
    }
}

/// Convert raw C string to a rust string wrapped by [Option].
///
/// # Safety
///
/// * `ptr` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
unsafe fn ptr_to_opt_str(ptr: *const c_char) -> Option<String> {
    if !ptr.is_null() {
        Some(unsafe { ptr_to_str(ptr) })
    } else {
        None
    }
}

/// A mirror of [`ConnectionRequest`] adopted for FFI.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct ConnectionConfig {
    pub address_count: usize,
    /// Pointer to an array.
    pub addresses: *const *const Address,
    pub has_tls: bool,
    pub tls_mode: TlsMode,
    pub cluster_mode: bool,
    pub has_request_timeout: bool,
    pub request_timeout: u32,
    pub has_connection_timeout: bool,
    pub connection_timeout: u32,
    pub has_read_from: bool,
    pub read_from: ReadFrom,
    pub has_connection_retry_strategy: bool,
    pub connection_retry_strategy: ConnectionRetryStrategy,
    pub has_authentication_info: bool,
    pub authentication_info: Credentials,
    pub database_id: u32,
    pub has_protocol: bool,
    pub protocol: redis::ProtocolVersion,
    /// zero pointer is valid, means no client name is given (`None`)
    pub client_name: *const c_char,
    /*
    TODO below
    pub periodic_checks: Option<PeriodicCheck>,
    pub pubsub_subscriptions: Option<redis::PubSubSubscriptionInfo>,
    pub inflight_requests_limit: Option<u32>,
    pub otel_endpoint: Option<String>,
    pub otel_span_flush_interval_ms: Option<u64>,
    */
}

/// Convert connection configuration to a corresponding object.
///
/// # Safety
///
/// * `config` must not be `null`.
/// * `config` must be a valid pointer to a [`ConnectionConfig`] struct.
/// * Dereferenced [`ConnectionConfig`] struct and all nested structs must contain valid pointers.
///   See the safety documentation of [`convert_node_addresses`], [`ptr_to_str`] and [`ptr_to_opt_str`].
pub(crate) unsafe fn create_connection_request(
    config_ptr: *const ConnectionConfig,
) -> ConnectionRequest {
    let config = unsafe { *config_ptr };
    ConnectionRequest {
        read_from: if config.has_read_from {
            Some(match config.read_from.strategy {
                ReadFromStrategy::Primary => coreReadFrom::Primary,
                ReadFromStrategy::PreferReplica => coreReadFrom::PreferReplica,
                ReadFromStrategy::AZAffinity => {
                    coreReadFrom::AZAffinity(unsafe { ptr_to_str(config.read_from.az) })
                }
                ReadFromStrategy::AZAffinityReplicasAndPrimary => {
                    coreReadFrom::AZAffinityReplicasAndPrimary(unsafe {
                        ptr_to_str(config.read_from.az)
                    })
                }
            })
        } else {
            None
        },
        client_name: unsafe { ptr_to_opt_str(config.client_name) },
        authentication_info: if config.has_authentication_info {
            Some(AuthenticationInfo {
                username: unsafe { ptr_to_opt_str(config.authentication_info.username) },
                password: unsafe { ptr_to_opt_str(config.authentication_info.password) },
            })
        } else {
            None
        },
        database_id: config.database_id.into(),
        protocol: if config.has_protocol {
            Some(config.protocol)
        } else {
            None
        },
        tls_mode: if config.has_tls {
            Some(config.tls_mode)
        } else {
            None
        },
        addresses: unsafe { convert_node_addresses(config.addresses, config.address_count) },
        cluster_mode_enabled: config.cluster_mode,
        request_timeout: if config.has_request_timeout {
            Some(config.request_timeout)
        } else {
            None
        },
        connection_timeout: if config.has_connection_timeout {
            Some(config.connection_timeout)
        } else {
            None
        },
        connection_retry_strategy: if config.has_connection_retry_strategy {
            Some(config.connection_retry_strategy)
        } else {
            None
        },
        // TODO below
        periodic_checks: None,
        pubsub_subscriptions: None,
        inflight_requests_limit: None,
        otel_endpoint: None,
        otel_span_flush_interval_ms: None,
    }
}

/// A mirror of [`NodeAddress`] adopted for FFI.
#[repr(C)]
pub struct Address {
    pub host: *const c_char,
    pub port: u16,
}

impl From<&Address> for NodeAddress {
    fn from(addr: &Address) -> Self {
        NodeAddress {
            host: unsafe { ptr_to_str(addr.host) },
            port: addr.port,
        }
    }
}

/// Convert raw array pointer of [`Address`]es to a vector of [`NodeAddress`]es.
///
/// # Safety
///
/// * `len` must not be greater than [`isize::MAX`]. See the safety documentation of [`std::slice::from_raw_parts`].
/// * `data` must not be `null`.
/// * `data` must point to `len` consecutive properly initialized [`Address`] structs.
/// * Each [`Address`] dereferenced by `data` must contain a valid string pointer. See the safety documentation of [`ptr_to_str`].
unsafe fn convert_node_addresses(data: *const *const Address, len: usize) -> Vec<NodeAddress> {
    unsafe { std::slice::from_raw_parts(data as *mut Address, len) }
        .iter()
        .map(NodeAddress::from)
        .collect()
}

/// A mirror of [`coreReadFrom`] adopted for FFI.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct ReadFrom {
    pub strategy: ReadFromStrategy,
    pub az: *const c_char,
}

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum ReadFromStrategy {
    Primary,
    PreferReplica,
    AZAffinity,
    AZAffinityReplicasAndPrimary,
}

/// A mirror of [`AuthenticationInfo`] adopted for FFI.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct Credentials {
    /// zero pointer is valid, means no username is given (`None`)
    pub username: *const c_char,
    /// zero pointer is valid, means no password is given (`None`)
    pub password: *const c_char,
}

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum RouteType {
    Random,
    AllNodes,
    AllPrimaries,
    SlotId,
    SlotKey,
    ByAddress,
}

/// A mirror of [`SlotAddr`]
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub enum SlotType {
    Primary,
    Replica,
}

impl From<&SlotType> for SlotAddr {
    fn from(val: &SlotType) -> Self {
        match val {
            SlotType::Primary => SlotAddr::Master,
            SlotType::Replica => SlotAddr::ReplicaRequired,
        }
    }
}

/// A structure which represents a route. To avoid extra pointer mandgling, it has fields for all route types.
/// Depending on [`RouteType`], the struct stores:
/// * Only `route_type` is filled, if route is a simple route;
/// * `route_type`, `slot_id` and `slot_type`, if route is a Slot ID route;
/// * `route_type`, `slot_key` and `slot_type`, if route is a Slot key route;
/// * `route_type`, `hostname` and `port`, if route is a Address route;
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct RouteInfo {
    pub route_type: RouteType,
    pub slot_id: i32,
    /// zero pointer is valid, means no slot key is given (`None`)
    pub slot_key: *const c_char,
    pub slot_type: SlotType,
    /// zero pointer is valid, means no hostname is given (`None`)
    pub hostname: *const c_char,
    pub port: i32,
}

/// Convert route configuration to a corresponding object.
///
/// # Safety
///
/// * `route_info` could be `null`, but if it is not `null`, it must be a valid pointer to a [`RouteInfo`] struct.
/// * `slot_key` and `hostname` in dereferenced [`RouteInfo`] struct must contain valid string pointers when corresponding `route_type` is set.
///   See description of [`RouteInfo`] and the safety documentation of [`ptr_to_str`].
pub(crate) unsafe fn create_route(
    route_ptr: *const RouteInfo,
    cmd: Option<&Cmd>,
) -> Option<RoutingInfo> {
    if route_ptr.is_null() {
        return None;
    }
    let route = unsafe { *route_ptr };
    match route.route_type {
        RouteType::Random => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
        RouteType::AllNodes => Some(RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllNodes,
            cmd.and_then(|c| ResponsePolicy::for_command(&c.command().unwrap())),
        ))),
        RouteType::AllPrimaries => Some(RoutingInfo::MultiNode((
            MultipleNodeRoutingInfo::AllMasters,
            cmd.and_then(|c| ResponsePolicy::for_command(&c.command().unwrap())),
        ))),
        RouteType::SlotId => Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                route.slot_id as u16,
                (&route.slot_type).into(),
            )),
        )),
        RouteType::SlotKey => Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(
                redis::cluster_topology::get_slot(unsafe { ptr_to_str(route.slot_key) }.as_bytes()),
                (&route.slot_type).into(),
            )),
        )),
        RouteType::ByAddress => Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
            host: unsafe { ptr_to_str(route.hostname) },
            port: route.port as u16,
        })),
    }
}

/// Converts a double pointer to a vec.
///
/// # Safety
///
/// * `data` and `data_len` must not be `null`.
/// * `data` must point to `len` consecutive string pointers.
/// * `data_len` must point to `len` consecutive string lengths.
/// * `data`, `data_len` and also each pointer stored in `data` must be able to be safely casted to a valid to a slice of the corresponding type via [`from_raw_parts`].
///   See the safety documentation of [`from_raw_parts`].
/// * The caller is responsible of freeing the allocated memory.
pub(crate) unsafe fn convert_double_pointer_to_vec<'a>(
    data: *const *const u8,
    len: usize,
    data_len: *const usize,
) -> Vec<&'a [u8]> {
    let string_ptrs = unsafe { from_raw_parts(data, len) };
    let string_lengths = unsafe { from_raw_parts(data_len, len) };
    let mut result = Vec::with_capacity(string_ptrs.len());
    for (i, &str_ptr) in string_ptrs.iter().enumerate() {
        let slice = unsafe { from_raw_parts(str_ptr, string_lengths[i]) };
        result.push(slice);
    }
    result
}

pub(crate) fn convert_vec_to_pointer<T>(mut vec: Vec<T>) -> (*const T, usize) {
    vec.shrink_to_fit();
    let vec_ptr = vec.as_ptr();
    let len = vec.len();
    std::mem::forget(vec);
    (vec_ptr, len)
}

#[repr(C)]
#[derive(Default, Debug, Clone)]
pub enum ValueType {
    #[default]
    Null = 0,
    Int = 1,
    Float = 2,
    Bool = 3,
    String = 4,
    Array = 5,
    Map = 6,
    Set = 7,
    BulkString = 8,
    OK = 9,
    Error = 10,
}

/// Represents FFI-safe variant of [`Value`].
/// * For [`Value::Nil`] and [`Value::Okay`], only [`ResponseValue::typ`] is stored.
/// * Simple values such as [`Value::Int`], [`Value::Double`], and [`Value::Boolean`] are stored in [`ResponseValue::val`],
///   while corresponding [`ResponseValue::typ`] is set.
/// * For complex values, such as [`Value::BulkString`], [`Value::VerbatimString`], [`Value::SimpleString`], only a pointer
///   is stored in [`ResponseValue::val`], while corresponding [`ResponseValue::typ`] and [`ResponseValue::size`] are set.
/// * Way more complex types are stored by reference. For [`Value::Array`], [`Value::Set`] and [`Value::Map`], in
///   [`ResponseValue::val`] a pointer to an array of another [`ResponseValue`] is stored and [`ResponseValue::size`] contains
///   the array length (for a map - it is 2x map size).
#[repr(C)]
#[derive(Default, Debug, Clone)]
pub struct ResponseValue {
    pub typ: ValueType,
    pub val: i64,
    /// For [`Value::BulkString`], [`Value::VerbatimString`], [`Value::SimpleString`] - size in bytes.
    /// For Maps, sets and arrays - amount of values [`ResponseValue::val`] points to.
    pub size: u32,
}

impl ResponseValue {
    /// Build [`ResponseValue`] from a [`Value`].
    pub(crate) fn from_value(value: Value) -> Self {
        match value {
            Value::Nil => ResponseValue {
                typ: ValueType::Null,
                ..Default::default()
            },
            Value::Int(int) => ResponseValue {
                typ: ValueType::Int,
                val: int,
                size: 0,
            },
            Value::BulkString(text) => {
                let (vec_ptr, len) = convert_vec_to_pointer(text);
                ResponseValue {
                    typ: ValueType::BulkString,
                    val: vec_ptr as i64,
                    size: len as u32,
                }
            }
            Value::Array(values) => {
                let vec: Vec<ResponseValue> =
                    values.into_iter().map(ResponseValue::from_value).collect();
                let (vec_ptr, len) = convert_vec_to_pointer(vec);
                ResponseValue {
                    typ: ValueType::Array,
                    val: vec_ptr as i64,
                    size: len as u32,
                }
            }
            Value::Set(values) => {
                let vec: Vec<ResponseValue> =
                    values.into_iter().map(ResponseValue::from_value).collect();
                let (vec_ptr, len) = convert_vec_to_pointer(vec);
                ResponseValue {
                    typ: ValueType::Set,
                    val: vec_ptr as i64,
                    size: len as u32,
                }
            }
            Value::Okay => ResponseValue {
                typ: ValueType::OK,
                ..Default::default()
            },
            Value::Map(items) => {
                let vec: Vec<ResponseValue> = items
                    .into_iter()
                    .flat_map(|(k, v)| {
                        vec![ResponseValue::from_value(k), ResponseValue::from_value(v)]
                    })
                    .collect();
                let (vec_ptr, len) = convert_vec_to_pointer(vec);
                ResponseValue {
                    typ: ValueType::Map,
                    val: vec_ptr as i64,
                    size: len as u32,
                }
            }
            Value::Double(num) => ResponseValue {
                typ: ValueType::Float,
                val: num.to_bits() as i64,
                size: 0,
            },
            Value::Boolean(boolean) => ResponseValue {
                typ: ValueType::Bool,
                val: if boolean { 1 } else { 0 },
                size: 0,
            },
            Value::VerbatimString { format: _, text } | Value::SimpleString(text) => {
                let (vec_ptr, len) = convert_vec_to_pointer(text.into_bytes());
                ResponseValue {
                    typ: ValueType::String,
                    val: vec_ptr as i64,
                    size: len as u32,
                }
            }
            Value::ServerError(err) => {
                let (vec_ptr, len) =
                    convert_vec_to_pointer(err.details().unwrap().as_bytes().to_vec());
                ResponseValue {
                    typ: ValueType::Error,
                    val: vec_ptr as i64,
                    size: len as u32,
                }
            }
            _ => todo!(), // push, bigint, attribute
        }
    }

    /// Restore ownership and free all memory allocated by the current [`ResponseValue`] and referenced [`ResponseValue`] recursively.
    ///
    /// # Safety
    /// * [`ResponseValue::val`] must not be `null` if [`ResponseValue::typ`] is [`ValueType::Array`] or [`ValueType::Set`] or [`ValueType::Map`] or [`ValueType::String`] or [`ValueType::BulkString`].
    /// * [`ResponseValue::val`] must be able to be safely casted to a valid [`Vec<u8>`] (when [`ResponseValue::typ`] is [`ValueType::String`] or [`ValueType::BulkString`])
    ///   or [`Vec<ResponseValue>`] in other cases via [`Vec::from_raw_parts`]. See the safety documentation of [`Vec::from_raw_parts`].
    pub(crate) unsafe fn free_memory(&self) {
        match self.typ {
            ValueType::Array | ValueType::Set | ValueType::Map => {
                let vec = unsafe {
                    Vec::from_raw_parts(
                        self.val as *mut ResponseValue,
                        self.size as usize,
                        self.size as usize,
                    )
                };
                for val in vec {
                    unsafe { val.free_memory() };
                }
            }
            ValueType::String | ValueType::BulkString | ValueType::Error => {
                let _ = unsafe {
                    Vec::from_raw_parts(self.val as *mut u8, self.size as usize, self.size as usize)
                };
            }
            _ => (),
        }
    }
}

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct CmdInfo {
    pub request_type: RequestType,
    pub args: *const *const u8,
    pub arg_count: usize,
    pub args_len: *const usize,
}

/// Convert [`CmdInfo`] to a [`Cmd`].
///
/// # Safety
/// * `cmd_ptr` must be able to be safely casted to a valid [`CmdInfo`]
/// * `args` and `args_len` in a referred [`CmdInfo`] structure must not be `null`.
/// * `data` in a referred [`CmdInfo`] structure must point to `arg_count` consecutive string pointers.
/// * `args_len` in a referred [`CmdInfo`] structure must point to `arg_count` consecutive string lengths. See the safety documentation of [`convert_double_pointer_to_vec`].
pub(crate) unsafe fn create_cmd(ptr: *const CmdInfo) -> Result<Cmd, String> {
    let info = unsafe { *ptr };
    let arg_vec =
        unsafe { convert_double_pointer_to_vec(info.args, info.arg_count, info.args_len) };

    let Some(mut cmd) = info.request_type.get_command() else {
        return Err("Couldn't fetch command type".into());
    };
    for command_arg in arg_vec {
        cmd.arg(command_arg);
    }
    Ok(cmd)
}
