/*
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

#[derive(Default)]
pub struct ConnectionRequest {
    pub read_from: Option<ReadFrom>,
    pub client_name: Option<String>,
    pub authentication_info: Option<AuthenticationInfo>,
    pub database_id: i64,
    pub protocol: Option<redis::ProtocolVersion>,
    pub tls_mode: Option<TlsMode>,
    pub addresses: Vec<NodeAddress>,
    pub cluster_mode_enabled: bool,
    pub request_timeout: Option<u32>,
    pub connection_retry_strategy: Option<ConnectionRetryStrategy>,
}

pub struct AuthenticationInfo {
    pub username: Option<String>,
    pub password: Option<String>,
}

#[derive(Debug)]
pub struct NodeAddress {
    pub host: String,
    pub port: u16,
}

impl ::std::fmt::Display for NodeAddress {
    fn fmt(&self, f: &mut ::std::fmt::Formatter<'_>) -> ::std::fmt::Result {
        write!(f, "Host: `{}`, Port: {}", self.host, self.port)
    }
}

#[derive(PartialEq, Eq, Clone, Copy, Default)]
pub enum ReadFrom {
    #[default]
    Primary,
    PreferReplica,
}

#[derive(PartialEq, Eq, Clone, Copy, Default)]
pub enum TlsMode {
    #[default]
    NoTls,
    InsecureTls,
    SecureTls,
}

pub struct ConnectionRetryStrategy {
    pub exponent_base: u32,
    pub factor: u32,
    pub number_of_retries: u32,
}

#[cfg(feature = "socket-layer")]
fn chars_to_string_option(chars: &::protobuf::Chars) -> Option<String> {
    if chars.is_empty() {
        None
    } else {
        Some(chars.to_string())
    }
}

#[cfg(feature = "socket-layer")]
fn none_if_zero(value: u32) -> Option<u32> {
    if value == 0 {
        None
    } else {
        Some(value)
    }
}

#[cfg(feature = "socket-layer")]
impl From<crate::connection_request::ConnectionRequest> for ConnectionRequest {
    fn from(value: crate::connection_request::ConnectionRequest) -> Self {
        let read_from = value.read_from.enum_value().ok().map(|val| match val {
            crate::connection_request::ReadFrom::Primary => ReadFrom::Primary,
            crate::connection_request::ReadFrom::PreferReplica => ReadFrom::PreferReplica,
            crate::connection_request::ReadFrom::LowestLatency => todo!(),
            crate::connection_request::ReadFrom::AZAffinity => todo!(),
        });

        let client_name = chars_to_string_option(&value.client_name);
        let authentication_info = value.authentication_info.0.and_then(|authentication_info| {
            let password = chars_to_string_option(&authentication_info.password);
            let username = chars_to_string_option(&authentication_info.username);
            if password.is_none() && username.is_none() {
                return None;
            }

            Some(AuthenticationInfo { password, username })
        });

        let database_id = value.database_id as i64;
        let protocol = value.protocol.enum_value().ok().map(|val| match val {
            crate::connection_request::ProtocolVersion::RESP3 => redis::ProtocolVersion::RESP3,
            crate::connection_request::ProtocolVersion::RESP2 => redis::ProtocolVersion::RESP2,
        });

        let tls_mode = value.tls_mode.enum_value().ok().map(|val| match val {
            crate::connection_request::TlsMode::NoTls => TlsMode::NoTls,
            crate::connection_request::TlsMode::SecureTls => TlsMode::SecureTls,
            crate::connection_request::TlsMode::InsecureTls => TlsMode::InsecureTls,
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
        let connection_retry_strategy =
            value
                .connection_retry_strategy
                .0
                .map(|strategy| ConnectionRetryStrategy {
                    exponent_base: strategy.exponent_base,
                    factor: strategy.factor,
                    number_of_retries: strategy.number_of_retries,
                });

        ConnectionRequest {
            read_from,
            client_name,
            authentication_info,
            database_id,
            protocol,
            tls_mode,
            addresses,
            cluster_mode_enabled,
            request_timeout,
            connection_retry_strategy,
        }
    }
}
