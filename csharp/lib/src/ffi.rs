// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::ffi::{c_char, CStr};

use glide_core::client::{
    AuthenticationInfo, ConnectionRequest, ConnectionRetryStrategy, NodeAddress,
    ReadFrom as coreReadFrom, TlsMode,
};

/// Convert raw C string to a rust string.
///
/// # Safety
///
/// * `ptr` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
unsafe fn ptr_to_str(ptr: *const c_char) -> String {
    if ptr as u64 != 0 {
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
    if ptr as u64 != 0 {
        Some(unsafe { ptr_to_str(ptr) })
    } else {
        None
    }
}

/// A mirror of [`ConnectionRequest`] adopted for FFI.
#[repr(C)]
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
    config: *const ConnectionConfig,
) -> ConnectionRequest {
    ConnectionRequest {
        read_from: if (*config).has_read_from {
            Some(match (*config).read_from.strategy {
                ReadFromStrategy::Primary => coreReadFrom::Primary,
                ReadFromStrategy::PreferReplica => coreReadFrom::PreferReplica,
                ReadFromStrategy::AZAffinity => {
                    coreReadFrom::AZAffinity(unsafe { ptr_to_str((*config).read_from.az) })
                }
                ReadFromStrategy::AZAffinityReplicasAndPrimary => {
                    coreReadFrom::AZAffinityReplicasAndPrimary(unsafe {
                        ptr_to_str((*config).read_from.az)
                    })
                }
            })
        } else {
            None
        },
        client_name: unsafe { ptr_to_opt_str((*config).client_name) },
        authentication_info: if (*config).has_authentication_info {
            Some(AuthenticationInfo {
                username: unsafe { ptr_to_opt_str((*config).authentication_info.username) },
                password: unsafe { ptr_to_opt_str((*config).authentication_info.password) },
            })
        } else {
            None
        },
        database_id: (*config).database_id.into(),
        protocol: if (*config).has_protocol {
            Some((*config).protocol)
        } else {
            None
        },
        tls_mode: if (*config).has_tls {
            Some((*config).tls_mode)
        } else {
            None
        },
        addresses: unsafe { convert_node_addresses((*config).addresses, (*config).address_count) },
        cluster_mode_enabled: (*config).cluster_mode,
        request_timeout: if (*config).has_request_timeout {
            Some((*config).request_timeout)
        } else {
            None
        },
        connection_timeout: if (*config).has_connection_timeout {
            Some((*config).connection_timeout)
        } else {
            None
        },
        connection_retry_strategy: if (*config).has_connection_retry_strategy {
            Some((*config).connection_retry_strategy)
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
pub struct ReadFrom {
    pub strategy: ReadFromStrategy,
    pub az: *const c_char,
}

#[repr(C)]
pub enum ReadFromStrategy {
    Primary,
    PreferReplica,
    AZAffinity,
    AZAffinityReplicasAndPrimary,
}

/// A mirror of [`AuthenticationInfo`] adopted for FFI.
#[repr(C)]
pub struct Credentials {
    /// zero pointer is valid, means no username is given (`None`)
    pub username: *const c_char,
    /// zero pointer is valid, means no password is given (`None`)
    pub password: *const c_char,
}
