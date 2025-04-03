use std::ffi::{c_int, c_longlong, c_uint, c_ulonglong};
use std::ops::Add;
use std::os::raw::c_char;
use std::time::Duration;
use glide_core::client;
use glide_core::client::AuthenticationInfo;
use crate::data::{NodeAddress, Utf8OrEmptyError};
use crate::helpers;
use crate::helpers::grab_str;

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

