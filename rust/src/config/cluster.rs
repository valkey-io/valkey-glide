// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Configuration for the **cluster** client.

use super::common::{
    BackoffStrategy, ClientIdentity, NodeAddress, PeriodicChecks, ProtocolVersion,
    PubSubSubscriptions, ReadFrom, ServerCredentials, TlsConfig, credentials_from_info,
    duration_as_millis_u32, from_redis_protocol, impl_common_config_builders,
    split_connection_addr,
};
use glide_core::client::ConnectionRequest;
use std::time::Duration;

/// Configuration for a **cluster** GLIDE client.
///
/// Mirrors Python `GlideClusterClientConfiguration`.
#[derive(Debug, Clone)]
pub struct GlideClusterClientConfiguration {
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
    /// Client name reported to the server.
    pub client_name: Option<String>,
    /// RESP protocol version.
    pub protocol: ProtocolVersion,
    /// Periodic topology checks.
    pub periodic_checks: PeriodicChecks,
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

impl_common_config_builders!(GlideClusterClientConfiguration);

impl GlideClusterClientConfiguration {
    /// Create a cluster configuration for the given addresses.
    pub fn new(addresses: Vec<NodeAddress>) -> Self {
        GlideClusterClientConfiguration {
            addresses,
            tls: TlsConfig::NoTls,
            credentials: None,
            read_from: ReadFrom::Primary,
            request_timeout: None,
            connection_timeout: None,
            reconnect_strategy: None,
            client_name: None,
            protocol: ProtocolVersion::default(),
            periodic_checks: PeriodicChecks::Enabled,
            inflight_requests_limit: None,
            lazy_connect: false,
            pubsub_subscriptions: None,
            force_pubsub_channel: false,
            root_certs: Vec::new(),
            client_identity: None,
        }
    }

    /// Build a cluster configuration from one or more Redis connection URLs
    /// (seed nodes), using the fork's exact URL semantics
    /// (`ClusterClient::new(initial_nodes)` accepts the same URLs):
    ///
    /// ```
    /// use glide::GlideClusterClientConfiguration;
    /// let cfg = GlideClusterClientConfiguration::from_urls([
    ///     "redis://n1:7000",
    ///     "redis://n2:7001",
    /// ]).unwrap();
    /// assert_eq!(cfg.addresses.len(), 2);
    /// ```
    ///
    /// Credentials / client-name / database / TLS mode must be identical
    /// across all URLs — conflicting settings are rejected with a
    /// configuration error (matching the fork's `ClusterClient`). A URL selecting
    /// a non-zero database is rejected — clusters only support database 0.
    /// The RESP `protocol` is taken from the **first** URL and not
    /// cross-validated (matching the fork, which overwrites per-node protocol
    /// from builder params without validating it).
    pub fn from_urls<T: redis::IntoConnectionInfo>(
        urls: impl IntoIterator<Item = T>,
    ) -> crate::error::Result<Self> {
        let mut addresses = Vec::new();
        let mut first: Option<(TlsConfig, redis::RedisConnectionInfo)> = None;
        for url in urls {
            let info = url
                .into_connection_info()
                .map_err(|e| crate::error::GlideError::Configuration(e.to_string()))?;
            let (address, tls) = split_connection_addr(info.addr)?;
            addresses.push(address);
            // Reject conflicting per-URL settings, matching the fork's
            // `ClusterClient` validation — silently ignoring the settings of
            // URLs 2..N would misconfigure the client.
            match &first {
                None => first = Some((tls, info.redis)),
                Some((first_tls, first_redis)) => {
                    if info.redis.password != first_redis.password {
                        return Err(crate::error::GlideError::Configuration(
                            "Cannot use different password among initial nodes.".into(),
                        ));
                    }
                    if info.redis.username != first_redis.username {
                        return Err(crate::error::GlideError::Configuration(
                            "Cannot use different username among initial nodes.".into(),
                        ));
                    }
                    if info.redis.client_name != first_redis.client_name {
                        return Err(crate::error::GlideError::Configuration(
                            "Cannot use different client_name among initial nodes.".into(),
                        ));
                    }
                    if info.redis.db != first_redis.db {
                        return Err(crate::error::GlideError::Configuration(
                            "Cannot use different database among initial nodes.".into(),
                        ));
                    }
                    if tls != *first_tls {
                        return Err(crate::error::GlideError::Configuration(
                            "Cannot use different TLS modes among initial nodes.".into(),
                        ));
                    }
                }
            }
        }
        let Some((tls, redis_info)) = first else {
            return Err(crate::error::GlideError::Configuration(
                "at least one node URL is required".into(),
            ));
        };
        if redis_info.db != 0 {
            return Err(crate::error::GlideError::Configuration(
                "cluster deployments only support database 0".into(),
            ));
        }
        let mut cfg = Self::new(addresses).tls(tls);
        cfg.protocol = from_redis_protocol(redis_info.protocol);
        cfg.client_name = redis_info.client_name;
        cfg.credentials = credentials_from_info(redis_info.username, redis_info.password);
        Ok(cfg)
    }

    /// Set periodic checks.
    pub fn periodic_checks(mut self, checks: PeriodicChecks) -> Self {
        self.periodic_checks = checks;
        self
    }

    pub(crate) fn to_request(&self) -> ConnectionRequest {
        let mut req = self.common_request();
        req.cluster_mode_enabled = true;
        req.periodic_checks = Some(self.periodic_checks.into());
        req
    }
}
