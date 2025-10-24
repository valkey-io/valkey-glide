// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#[allow(unused_imports)]
use logger_core::log_warn;
#[allow(unused_imports)]
use std::collections::HashSet;
use std::time::Duration;

#[cfg(feature = "proto")]
use crate::connection_request as protobuf;
use crate::iam::ServiceType;

#[derive(Default, Clone, Debug)]
pub struct ConnectionRequest {
    pub read_from: Option<ReadFrom>,
    pub client_name: Option<String>,
    pub lib_name: Option<String>,
    pub authentication_info: Option<AuthenticationInfo>,
    pub database_id: i64,
    pub protocol: Option<redis::ProtocolVersion>,
    pub tls_mode: Option<TlsMode>,
    pub addresses: Vec<NodeAddress>,
    pub cluster_mode_enabled: bool,
    pub request_timeout: Option<u32>,
    pub connection_timeout: Option<u32>,
    pub connection_retry_strategy: Option<ConnectionRetryStrategy>,
    pub periodic_checks: Option<PeriodicCheck>,
    pub pubsub_subscriptions: Option<redis::PubSubSubscriptionInfo>,
    pub inflight_requests_limit: Option<u32>,
    pub lazy_connect: bool,
    pub refresh_topology_from_initial_nodes: bool,
    pub root_certs: Vec<Vec<u8>>,
}

/// Authentication information for connecting to Redis/Valkey servers
///
/// Supports traditional username/password authentication and AWS IAM authentication.
/// IAM authentication takes priority when both are configured.
#[derive(PartialEq, Eq, Clone, Default, Debug)]
pub struct AuthenticationInfo {
    /// Username for authentication (required for IAM)
    pub username: Option<String>,

    /// Password for traditional authentication (fallback when IAM unavailable)
    pub password: Option<String>,

    /// IAM authentication configuration (takes precedence over password)
    pub iam_config: Option<IamAuthenticationConfig>,
}

/// AWS IAM authentication configuration for ElastiCache and MemoryDB
///
/// Handles AWS credential resolution, SigV4 token signing, and automatic token refresh.
/// Tokens are valid for 15 minutes and refreshed every 14 minutes by default.
#[derive(PartialEq, Eq, Clone, Debug)]
pub struct IamAuthenticationConfig {
    /// AWS ElastiCache or MemoryDB cluster name
    pub cluster_name: String,

    /// AWS region where the cluster is located
    pub region: String,

    /// AWS service type (ElastiCache or MemoryDB)
    pub service_type: ServiceType,

    /// Token refresh interval in seconds (1 second to 12 hours, default 14 minutes)
    pub refresh_interval_seconds: Option<u32>,
}

#[derive(Default, Clone, Copy, Debug)]
pub enum PeriodicCheck {
    #[default]
    Enabled,
    Disabled,
    ManualInterval(Duration),
}

#[derive(Clone, Debug)]
pub struct NodeAddress {
    pub host: String,
    pub port: u16,
}

impl ::std::fmt::Display for NodeAddress {
    fn fmt(&self, f: &mut ::std::fmt::Formatter<'_>) -> ::std::fmt::Result {
        write!(f, "Host: `{}`, Port: {}", self.host, self.port)
    }
}

#[derive(PartialEq, Eq, Clone, Default, Debug)]
pub enum ReadFrom {
    #[default]
    Primary,
    PreferReplica,
    AZAffinity(String),
    AZAffinityReplicasAndPrimary(String),
}

#[derive(PartialEq, Eq, Clone, Copy, Default, Debug)]
#[repr(C)]
pub enum TlsMode {
    #[default]
    NoTls,
    InsecureTls,
    SecureTls,
}

#[derive(PartialEq, Eq, Clone, Copy, Debug)]
#[repr(C)]
pub struct ConnectionRetryStrategy {
    pub exponent_base: u32,
    pub factor: u32,
    pub number_of_retries: u32,
    pub jitter_percent: Option<u32>,
}

#[cfg(feature = "proto")]
fn chars_to_string_option(chars: &::protobuf::Chars) -> Option<String> {
    if chars.is_empty() {
        None
    } else {
        Some(chars.to_string())
    }
}

#[cfg(feature = "proto")]
pub(crate) fn none_if_zero(value: u32) -> Option<u32> {
    if value == 0 { None } else { Some(value) }
}

#[cfg(feature = "proto")]
impl From<protobuf::ConnectionRequest> for ConnectionRequest {
    fn from(value: protobuf::ConnectionRequest) -> Self {
        let read_from = value.read_from.enum_value().ok().map(|val| match val {
            protobuf::ReadFrom::Primary => ReadFrom::Primary,
            protobuf::ReadFrom::PreferReplica => ReadFrom::PreferReplica,
            protobuf::ReadFrom::LowestLatency => todo!(),
            protobuf::ReadFrom::AZAffinity => {
                if let Some(client_az) = chars_to_string_option(&value.client_az) {
                    ReadFrom::AZAffinity(client_az)
                } else {
                    log_warn(
                        "types",
                        format!(
                            "Failed to convert availability zone string: '{:?}'. Falling back to `ReadFrom::PreferReplica`",
                            value.client_az
                        ),
                    );
                    ReadFrom::PreferReplica
                }
            }
            protobuf::ReadFrom::AZAffinityReplicasAndPrimary => {
                if let Some(client_az) = chars_to_string_option(&value.client_az) {
                    ReadFrom::AZAffinityReplicasAndPrimary(client_az)
                } else {
                    log_warn(
                        "types",
                        format!(
                            "Failed to convert availability zone string: '{:?}'. Falling back to `ReadFrom::PreferReplica`",
                            value.client_az
                        ),
                    );
                    ReadFrom::PreferReplica
                }
            },
        });

        let client_name = chars_to_string_option(&value.client_name);
        let lib_name = chars_to_string_option(&value.lib_name);
        let authentication_info = value.authentication_info.0.map(|authentication_info| {
            let password = chars_to_string_option(&authentication_info.password);
            let username = chars_to_string_option(&authentication_info.username);
            let iam_config = authentication_info.iam_credentials.0.map(|iam_creds| {
                let cluster_name =
                    chars_to_string_option(&iam_creds.cluster_name).unwrap_or_default();
                let region = chars_to_string_option(&iam_creds.region).unwrap_or_default();
                let service_type = match iam_creds.service_type.enum_value() {
                    Ok(protobuf::ServiceType::MEMORYDB) => ServiceType::MemoryDB,
                    _ => ServiceType::ElastiCache,
                };
                let refresh_interval_seconds = iam_creds.refresh_interval_seconds;

                IamAuthenticationConfig {
                    cluster_name,
                    region,
                    service_type,
                    refresh_interval_seconds,
                }
            });

            AuthenticationInfo {
                password,
                username,
                iam_config,
            }
        });

        let database_id = value.database_id as i64;
        let protocol = value.protocol.enum_value().ok().map(|val| match val {
            protobuf::ProtocolVersion::RESP3 => redis::ProtocolVersion::RESP3,
            protobuf::ProtocolVersion::RESP2 => redis::ProtocolVersion::RESP2,
        });

        let tls_mode = value.tls_mode.enum_value().ok().map(|val| match val {
            protobuf::TlsMode::NoTls => TlsMode::NoTls,
            protobuf::TlsMode::SecureTls => TlsMode::SecureTls,
            protobuf::TlsMode::InsecureTls => TlsMode::InsecureTls,
        });

        let addresses = value
            .addresses
            .into_iter()
            .map(|addr| NodeAddress {
                host: addr.host.to_string(),
                port: addr.port as u16,
            })
            .collect();
        let cluster_mode_enabled = value.cluster_mode_enabled;
        let request_timeout = none_if_zero(value.request_timeout);
        let connection_timeout = none_if_zero(value.connection_timeout);
        let connection_retry_strategy =
            value
                .connection_retry_strategy
                .0
                .map(|strategy| ConnectionRetryStrategy {
                    exponent_base: strategy.exponent_base,
                    factor: strategy.factor,
                    number_of_retries: strategy.number_of_retries,
                    jitter_percent: strategy.jitter_percent,
                });
        let periodic_checks = value
            .periodic_checks
            .map(|periodic_check| match periodic_check {
                protobuf::connection_request::Periodic_checks::PeriodicChecksManualInterval(
                    interval,
                ) => PeriodicCheck::ManualInterval(Duration::from_secs(
                    interval.duration_in_sec.into(),
                )),
                protobuf::connection_request::Periodic_checks::PeriodicChecksDisabled(_) => {
                    PeriodicCheck::Disabled
                }
            });
        let mut pubsub_subscriptions: Option<redis::PubSubSubscriptionInfo> = None;
        if let Some(protobuf_pubsub) = value.pubsub_subscriptions.0 {
            let mut redis_pubsub = redis::PubSubSubscriptionInfo::new();
            for (pubsub_type, channels_patterns) in
                protobuf_pubsub.channels_or_patterns_by_type.iter()
            {
                let kind = match *pubsub_type {
                    0 => redis::PubSubSubscriptionKind::Exact,
                    1 => redis::PubSubSubscriptionKind::Pattern,
                    2 => redis::PubSubSubscriptionKind::Sharded,
                    3_u32..=u32::MAX => {
                        log_warn(
                            "client creation",
                            format!(
                                "Omitting pubsub subscription on an unknown type: {:?}",
                                *pubsub_type
                            ),
                        );
                        continue;
                    }
                };

                for channel_pattern in channels_patterns.channels_or_patterns.iter() {
                    redis_pubsub
                        .entry(kind)
                        .and_modify(|channels_patterns| {
                            channels_patterns.insert(channel_pattern.to_vec());
                        })
                        .or_insert(HashSet::from([channel_pattern.to_vec()]));
                }
            }
            pubsub_subscriptions = Some(redis_pubsub);
        }

        let inflight_requests_limit = none_if_zero(value.inflight_requests_limit);
        let lazy_connect = value.lazy_connect;
        let refresh_topology_from_initial_nodes = value.refresh_topology_from_initial_nodes;
        let root_certs = value
            .root_certs
            .into_iter()
            .map(|cert| cert.to_vec())
            .collect();

        ConnectionRequest {
            read_from,
            client_name,
            lib_name,
            authentication_info,
            database_id,
            protocol,
            tls_mode,
            addresses,
            cluster_mode_enabled,
            request_timeout,
            connection_timeout,
            connection_retry_strategy,
            periodic_checks,
            pubsub_subscriptions,
            inflight_requests_limit,
            lazy_connect,
            refresh_topology_from_initial_nodes,
            root_certs,
        }
    }
}
