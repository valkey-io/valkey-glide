// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Configuration for the **standalone** (non-cluster) client.

use super::common::{
    BackoffStrategy, ClientIdentity, NodeAddress, ProtocolVersion, PubSubSubscriptions, ReadFrom,
    ServerCredentials, TlsConfig, credentials_from_info, duration_as_millis_u32,
    from_redis_protocol, impl_common_config_builders, split_connection_addr,
};
use glide_core::client::ConnectionRequest;
use std::time::Duration;

/// Configuration for a **standalone** (non-cluster) GLIDE client.
///
/// Mirrors Python `GlideClientConfiguration`. Build with [`Self::new`] then chain
/// the `with_*` setters.
#[derive(Debug, Clone)]
pub struct GlideClientConfiguration {
    /// Seed node addresses.
    pub addresses: Vec<NodeAddress>,
    /// TLS mode.
    pub tls: TlsConfig,
    /// Optional credentials.
    pub credentials: Option<ServerCredentials>,
    /// Read strategy.
    pub read_from: ReadFrom,
    /// Overall request timeout.
    pub request_timeout: Option<Duration>,
    /// Connection establishment timeout.
    pub connection_timeout: Option<Duration>,
    /// Reconnection backoff strategy.
    pub reconnect_strategy: Option<BackoffStrategy>,
    /// Logical database index to SELECT on connect.
    pub database_id: i64,
    /// Client name reported to the server (`CLIENT SETNAME`).
    pub client_name: Option<String>,
    /// RESP protocol version.
    pub protocol: ProtocolVersion,
    /// Maximum number of concurrent in-flight requests.
    pub inflight_requests_limit: Option<u32>,
    /// Defer connecting until the first command is issued.
    pub lazy_connect: bool,
    /// Pub/Sub subscriptions to establish on connect.
    pub pubsub_subscriptions: Option<PubSubSubscriptions>,
    /// Force creation of the Pub/Sub push channel even when no subscriptions are
    /// configured, so runtime `subscribe`/`unsubscribe` can receive messages.
    /// See [`crate::commands::pubsub::PubSubCommands`].
    pub force_pubsub_channel: bool,
    /// Custom CA certificate(s) (PEM bytes) to trust when verifying the server
    /// under [`TlsConfig::SecureTls`]. Lowered into
    /// `ConnectionRequest::root_certs`. Empty = use the system trust store.
    pub root_certs: Vec<Vec<u8>>,
    /// Client certificate + private key for **mutual TLS**. Set via
    /// [`Self::client_identity`]. Only meaningful together with
    /// [`TlsConfig::SecureTls`].
    pub client_identity: Option<ClientIdentity>,
}

impl_common_config_builders!(GlideClientConfiguration);

impl GlideClientConfiguration {
    /// Create a standalone configuration for the given addresses.
    pub fn new(addresses: Vec<NodeAddress>) -> Self {
        GlideClientConfiguration {
            addresses,
            tls: TlsConfig::NoTls,
            credentials: None,
            read_from: ReadFrom::Primary,
            request_timeout: None,
            connection_timeout: None,
            reconnect_strategy: None,
            database_id: 0,
            client_name: None,
            protocol: ProtocolVersion::default(),
            inflight_requests_limit: None,
            lazy_connect: false,
            pubsub_subscriptions: None,
            force_pubsub_channel: false,
            root_certs: Vec::new(),
            client_identity: None,
        }
    }

    /// Build a configuration from a Redis connection URL, using the exact URL
    /// semantics of the vendored fork (`redis://` and `rediss://`, with
    /// `[user][:password@]host[:port][/db]`):
    ///
    /// ```
    /// use glide::GlideClientConfiguration;
    /// let cfg = GlideClientConfiguration::from_url("redis://user:pass@localhost:6379/2").unwrap();
    /// assert_eq!(cfg.database_id, 2);
    /// ```
    ///
    /// `rediss://` enables TLS with full verification;
    /// `rediss://…/#insecure` disables certificate verification, as in
    /// the fork. Unix-socket URLs are not supported by glide-core and return
    /// a configuration error.
    pub fn from_url(url: &str) -> crate::error::Result<Self> {
        Self::from_connection_info(url)
    }

    /// Build a configuration from anything implementing
    /// [`redis::IntoConnectionInfo`] (a URL string, or a prebuilt
    /// [`redis::ConnectionInfo`]).
    pub fn from_connection_info<T: redis::IntoConnectionInfo>(
        info: T,
    ) -> crate::error::Result<Self> {
        let info = info
            .into_connection_info()
            .map_err(|e| crate::error::GlideError::Configuration(e.to_string()))?;
        let (address, tls) = split_connection_addr(info.addr)?;
        let mut cfg = Self::new(vec![address]).tls(tls);
        cfg.database_id = info.redis.db;
        cfg.protocol = from_redis_protocol(info.redis.protocol);
        cfg.client_name = info.redis.client_name;
        cfg.credentials = credentials_from_info(info.redis.username, info.redis.password);
        Ok(cfg)
    }

    /// Set database id.
    pub fn database_id(mut self, db: i64) -> Self {
        self.database_id = db;
        self
    }

    pub(crate) fn to_request(&self) -> ConnectionRequest {
        let mut req = self.common_request();
        req.cluster_mode_enabled = false;
        req.database_id = self.database_id;
        req
    }
}
