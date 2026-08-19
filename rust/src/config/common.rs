// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Configuration types shared by the standalone and cluster configurations.

use glide_core::client::{
    AuthenticationInfo, ConnectionRetryStrategy, IamAuthenticationConfig,
    NodeAddress as CoreNodeAddress, PeriodicCheck, ReadFrom as CoreReadFrom, TlsMode,
};
use glide_core::iam::ServiceType as CoreServiceType;
use std::collections::{HashMap, HashSet};
use std::time::Duration;

/// The kind of a Pub/Sub channel subscription.
///
/// Mirrors Python's `PubSubChannelModes`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum PubSubChannelMode {
    /// Exact channel name (`SUBSCRIBE`).
    Exact,
    /// Glob pattern (`PSUBSCRIBE`).
    Pattern,
    /// Shard channel (`SSUBSCRIBE`, cluster only).
    Sharded,
}

impl PubSubChannelMode {
    fn to_core(self) -> redis::PubSubSubscriptionKind {
        match self {
            PubSubChannelMode::Exact => redis::PubSubSubscriptionKind::Exact,
            PubSubChannelMode::Pattern => redis::PubSubSubscriptionKind::Pattern,
            PubSubChannelMode::Sharded => redis::PubSubSubscriptionKind::Sharded,
        }
    }
}

/// Pub/Sub subscriptions to establish automatically when the client connects.
///
/// Messages received on these subscriptions are delivered via
/// [`crate::GlideClient::get_pubsub_message`] /
/// [`crate::GlideClient::try_get_pubsub_message`] (and the cluster equivalents).
///
/// Mirrors Python's `*ClientConfiguration.pubsub_subscriptions`.
#[derive(Debug, Clone, Default)]
pub struct PubSubSubscriptions {
    channels: HashMap<PubSubChannelMode, HashSet<Vec<u8>>>,
}

impl PubSubSubscriptions {
    /// Create an empty subscription set.
    pub fn new() -> Self {
        PubSubSubscriptions::default()
    }

    /// Subscribe to `channel` under the given `mode`.
    pub fn subscribe(mut self, mode: PubSubChannelMode, channel: impl Into<Vec<u8>>) -> Self {
        self.channels
            .entry(mode)
            .or_default()
            .insert(channel.into());
        self
    }

    /// Whether any subscriptions are configured.
    pub fn is_empty(&self) -> bool {
        self.channels.values().all(|s| s.is_empty())
    }

    pub(crate) fn to_core(&self) -> redis::PubSubSubscriptionInfo {
        let mut info: redis::PubSubSubscriptionInfo = HashMap::new();
        for (mode, set) in &self.channels {
            info.insert(mode.to_core(), set.clone());
        }
        info
    }
}

/// The RESP protocol version used to communicate with the server.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ProtocolVersion {
    /// RESP2 — the legacy protocol.
    RESP2,
    /// RESP3 — the default; enables richer typed replies and client-side push.
    #[default]
    RESP3,
}

impl From<ProtocolVersion> for redis::ProtocolVersion {
    fn from(v: ProtocolVersion) -> Self {
        match v {
            ProtocolVersion::RESP2 => redis::ProtocolVersion::RESP2,
            ProtocolVersion::RESP3 => redis::ProtocolVersion::RESP3,
        }
    }
}

/// Strategy for selecting which node to read from.
///
/// Mirrors Python `ReadFrom`.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub enum ReadFrom {
    /// Always read from the primary.
    #[default]
    Primary,
    /// Prefer replicas, falling back to the primary if none are available.
    PreferReplica,
    /// Read from a replica in the given availability zone when possible.
    AZAffinity(String),
    /// Read from a replica in the given AZ, else the primary in that AZ, else any.
    AZAffinityReplicasAndPrimary(String),
    /// Spread reads across all nodes.
    AllNodes,
}

impl From<ReadFrom> for CoreReadFrom {
    fn from(v: ReadFrom) -> Self {
        match v {
            ReadFrom::Primary => CoreReadFrom::Primary,
            ReadFrom::PreferReplica => CoreReadFrom::PreferReplica,
            ReadFrom::AZAffinity(az) => CoreReadFrom::AZAffinity(az),
            ReadFrom::AZAffinityReplicasAndPrimary(az) => {
                CoreReadFrom::AZAffinityReplicasAndPrimary(az)
            }
            ReadFrom::AllNodes => CoreReadFrom::AllNodes,
        }
    }
}

/// A server address (host + port).
///
/// Mirrors Python `NodeAddress`. Defaults to `localhost:6379`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NodeAddress {
    /// Hostname or IP address.
    pub host: String,
    /// TCP port.
    pub port: u16,
}

impl NodeAddress {
    /// Create a new address.
    pub fn new(host: impl Into<String>, port: u16) -> Self {
        NodeAddress {
            host: host.into(),
            port,
        }
    }
}

impl Default for NodeAddress {
    fn default() -> Self {
        NodeAddress::new("localhost", 6379)
    }
}

impl From<NodeAddress> for CoreNodeAddress {
    fn from(a: NodeAddress) -> Self {
        CoreNodeAddress {
            host: a.host,
            port: a.port,
        }
    }
}

/// Username/password credentials.
///
/// Mirrors Python `ServerCredentials`.
#[derive(Clone, Default, PartialEq, Eq)]
pub struct ServerCredentials {
    /// Optional username (ACL). If omitted, the default user is used. Required
    /// when [`Self::iam_config`] is set.
    pub username: Option<String>,
    /// Password for traditional authentication. Ignored when IAM is configured
    /// and available (IAM acts as the password source); may still be set as a
    /// fallback.
    pub password: Option<String>,
    /// AWS IAM authentication configuration. When set, IAM takes precedence over
    /// [`Self::password`].
    pub iam_config: Option<IamAuthConfig>,
}

impl ServerCredentials {
    /// Password-only credentials (default user).
    pub fn password(password: impl Into<String>) -> Self {
        ServerCredentials {
            username: None,
            password: Some(password.into()),
            iam_config: None,
        }
    }

    /// Username + password credentials.
    pub fn new(username: impl Into<String>, password: impl Into<String>) -> Self {
        ServerCredentials {
            username: Some(username.into()),
            password: Some(password.into()),
            iam_config: None,
        }
    }

    /// AWS IAM credentials for ElastiCache/MemoryDB. `username` is the IAM user
    /// and is required; the token is signed and refreshed automatically by the
    /// core. Mirrors Python's IAM `ServerCredentials`.
    pub fn iam(username: impl Into<String>, iam_config: IamAuthConfig) -> Self {
        ServerCredentials {
            username: Some(username.into()),
            password: None,
            iam_config: Some(iam_config),
        }
    }

    /// Set a fallback password (used when IAM is unavailable). Builder form.
    #[must_use]
    pub fn with_password(mut self, password: impl Into<String>) -> Self {
        self.password = Some(password.into());
        self
    }

    pub(crate) fn to_core(&self) -> AuthenticationInfo {
        AuthenticationInfo {
            username: self.username.clone(),
            password: self.password.clone(),
            iam_config: self.iam_config.as_ref().map(IamAuthConfig::to_core),
        }
    }
}

impl std::fmt::Debug for ServerCredentials {
    /// Redacts the password so it never leaks through `{:?}` (including via the
    /// containing configuration's derived `Debug`).
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ServerCredentials")
            .field("username", &self.username)
            .field("password", &self.password.as_ref().map(|_| "<redacted>"))
            .field("iam_config", &self.iam_config)
            .finish()
    }
}

/// Client certificate + private key (both PEM) for **mutual TLS**.
///
/// Fields are private so a half-set identity (cert without key or vice versa)
/// is unrepresentable. Build with [`Self::new`] or the configs'
/// `client_identity(cert, key)` builder methods.
#[derive(Clone)]
pub struct ClientIdentity {
    cert_pem: Vec<u8>,
    key_pem: Vec<u8>,
}

impl ClientIdentity {
    /// Create a client identity from certificate and private-key PEM bytes.
    pub fn new(cert_pem: impl Into<Vec<u8>>, key_pem: impl Into<Vec<u8>>) -> Self {
        ClientIdentity {
            cert_pem: cert_pem.into(),
            key_pem: key_pem.into(),
        }
    }

    /// The client certificate (PEM bytes).
    pub fn cert_pem(&self) -> &[u8] {
        &self.cert_pem
    }

    pub(crate) fn key_pem(&self) -> &[u8] {
        &self.key_pem
    }
}

impl std::fmt::Debug for ClientIdentity {
    /// Redacts the private key so it never leaks through `{:?}` (including via
    /// the containing configuration's derived `Debug`).
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ClientIdentity")
            .field("cert_pem", &format_args!("[{} bytes]", self.cert_pem.len()))
            .field("key_pem", &"<redacted>")
            .finish()
    }
}

/// AWS service backing IAM authentication.
///
/// Mirrors Python's IAM `ServiceType`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ServiceType {
    /// Amazon ElastiCache.
    ElastiCache,
    /// Amazon MemoryDB.
    MemoryDB,
}

impl From<ServiceType> for CoreServiceType {
    fn from(s: ServiceType) -> Self {
        match s {
            ServiceType::ElastiCache => CoreServiceType::ElastiCache,
            ServiceType::MemoryDB => CoreServiceType::MemoryDB,
        }
    }
}

/// AWS IAM authentication configuration for ElastiCache/MemoryDB.
///
/// The core resolves AWS credentials, signs a SigV4 auth token, and refreshes it
/// automatically (default every 14 minutes). Mirrors Python's IAM config.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct IamAuthConfig {
    /// AWS ElastiCache or MemoryDB cluster name.
    pub cluster_name: String,
    /// AWS region of the cluster (e.g. `us-east-1`).
    pub region: String,
    /// Which AWS service backs the cluster.
    pub service_type: ServiceType,
    /// Token refresh interval in seconds (1s–12h). `None` uses the core default
    /// (14 minutes).
    pub refresh_interval_seconds: Option<u32>,
}

impl IamAuthConfig {
    /// Create an IAM config for the given cluster, region, and service, using the
    /// default refresh interval.
    pub fn new(
        cluster_name: impl Into<String>,
        region: impl Into<String>,
        service_type: ServiceType,
    ) -> Self {
        IamAuthConfig {
            cluster_name: cluster_name.into(),
            region: region.into(),
            service_type,
            refresh_interval_seconds: None,
        }
    }

    /// Override the token refresh interval (seconds). Builder form.
    #[must_use]
    pub fn with_refresh_interval_seconds(mut self, seconds: u32) -> Self {
        self.refresh_interval_seconds = Some(seconds);
        self
    }

    fn to_core(&self) -> IamAuthenticationConfig {
        IamAuthenticationConfig {
            cluster_name: self.cluster_name.clone(),
            region: self.region.clone(),
            service_type: self.service_type.into(),
            refresh_interval_seconds: self.refresh_interval_seconds,
        }
    }
}

/// Reconnection backoff strategy.
///
/// Mirrors Python `BackoffStrategy`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BackoffStrategy {
    /// Number of retry attempts before giving up on a reconnection burst.
    pub num_of_retries: u32,
    /// The exponent base used for the exponential backoff.
    pub factor: u32,
    /// The multiplier that will be applied to the waiting time between retries.
    pub exponent_base: u32,
    /// Optional jitter percentage applied to the computed delay.
    pub jitter_percent: Option<u32>,
}

impl From<BackoffStrategy> for ConnectionRetryStrategy {
    fn from(b: BackoffStrategy) -> Self {
        ConnectionRetryStrategy {
            exponent_base: b.exponent_base,
            factor: b.factor,
            number_of_retries: b.num_of_retries,
            jitter_percent: b.jitter_percent,
        }
    }
}

/// Periodic cluster topology check configuration (cluster only).
///
/// Mirrors Python `PeriodicChecksStatus` / `PeriodicChecksManualInterval`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum PeriodicChecks {
    /// Enabled with the default interval.
    #[default]
    Enabled,
    /// Disabled entirely.
    Disabled,
    /// Enabled with a manual interval (seconds).
    ManualInterval(u64),
}

impl From<PeriodicChecks> for PeriodicCheck {
    fn from(p: PeriodicChecks) -> Self {
        match p {
            PeriodicChecks::Enabled => PeriodicCheck::Enabled,
            PeriodicChecks::Disabled => PeriodicCheck::Disabled,
            PeriodicChecks::ManualInterval(secs) => {
                PeriodicCheck::ManualInterval(Duration::from_secs(secs))
            }
        }
    }
}

/// TLS mode.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum TlsConfig {
    /// No TLS (plaintext).
    #[default]
    NoTls,
    /// TLS with full certificate verification.
    SecureTls,
    /// TLS without certificate verification (testing only).
    InsecureTls,
}

impl From<TlsConfig> for TlsMode {
    fn from(t: TlsConfig) -> Self {
        match t {
            TlsConfig::NoTls => TlsMode::NoTls,
            TlsConfig::SecureTls => TlsMode::SecureTls,
            TlsConfig::InsecureTls => TlsMode::InsecureTls,
        }
    }
}

// ---- shared request-lowering helpers -------------------------------------------

/// Map a [`redis::ConnectionAddr`] to our address + TLS mode.
pub(crate) fn split_connection_addr(
    addr: redis::ConnectionAddr,
) -> crate::error::Result<(NodeAddress, TlsConfig)> {
    match addr {
        redis::ConnectionAddr::Tcp(host, port) => {
            Ok((NodeAddress::new(host, port), TlsConfig::NoTls))
        }
        redis::ConnectionAddr::TcpTls {
            host,
            port,
            insecure,
            tls_params,
        } => {
            // The fork's `TlsConnParams` fields (root cert store, client
            // identity) are `pub(crate)` — we cannot read them to map onto
            // `root_certs` / `client_identity`. Silently dropping them would
            // yield a mysteriously misconfigured connection (wrong trust
            // roots, mTLS not attempted), so fail loudly instead.
            if tls_params.is_some() {
                return Err(crate::error::GlideError::Configuration(
                    "ConnectionInfo carries TLS certificate parameters (TlsCertificates) that \
                     cannot be mapped; configure them via the config's `root_certs` field and \
                     `client_identity(cert, key)` instead"
                        .into(),
                ));
            }
            let tls = if insecure {
                TlsConfig::InsecureTls
            } else {
                TlsConfig::SecureTls
            };
            Ok((NodeAddress::new(host, port), tls))
        }
        redis::ConnectionAddr::Unix(_) => Err(crate::error::GlideError::Configuration(
            "unix-socket connections are not supported by glide-core".into(),
        )),
    }
}

/// Map the fork's protocol enum to ours.
pub(crate) fn from_redis_protocol(p: redis::ProtocolVersion) -> ProtocolVersion {
    match p {
        redis::ProtocolVersion::RESP2 => ProtocolVersion::RESP2,
        redis::ProtocolVersion::RESP3 => ProtocolVersion::RESP3,
    }
}

/// Build [`ServerCredentials`] from URL-provided username/password (either may
/// be absent; `redis://:pass@host` yields password-only credentials).
pub(crate) fn credentials_from_info(
    username: Option<String>,
    password: Option<String>,
) -> Option<ServerCredentials> {
    match (username, password) {
        (None, None) => None,
        (username, password) => Some(ServerCredentials {
            username,
            password,
            iam_config: None,
        }),
    }
}

/// Convert a [`Duration`] to whole milliseconds as `u32`, saturating at
/// `u32::MAX` rather than silently truncating (a `Duration` above ~49.7 days
/// would overflow `u32` ms). The core treats the timeout as a `u32` millisecond
/// count, so saturation is the safe, lossless-within-range behavior.
pub(crate) fn duration_as_millis_u32(d: Duration) -> u32 {
    u32::try_from(d.as_millis()).unwrap_or(u32::MAX)
}

// ---- shared builder setters + request lowering ----------------------------------

/// Generates the builder setters and the `ConnectionRequest` lowering shared by
/// [`crate::config::GlideClientConfiguration`] and
/// [`crate::config::GlideClusterClientConfiguration`].
///
/// Both structs carry the same common public fields (same names, same types), so
/// the generated methods access them directly. Mode-specific fields/setters
/// (`database_id`, `periodic_checks`, `from_url*`) stay in each struct's own
/// `impl` block, as does `to_request()`, which starts from the generated
/// generated `common_request` and layers the mode-specific fields
/// on top.
macro_rules! impl_common_config_builders {
    ($ty:ty) => {
        impl $ty {
            /// Configure for a single `host:port`.
            pub fn with_address(host: impl Into<String>, port: u16) -> Self {
                Self::new(vec![NodeAddress::new(host, port)])
            }

            /// Set TLS mode.
            pub fn tls(mut self, tls: TlsConfig) -> Self {
                self.tls = tls;
                self
            }

            /// Trust a custom CA certificate (PEM bytes) when verifying the server with
            /// [`TlsConfig::SecureTls`]. Enables secure TLS against a server presenting a
            /// certificate signed by a private/self-signed CA. May be called more than
            /// once to trust multiple CAs.
            pub fn tls_ca_cert(mut self, pem: impl Into<Vec<u8>>) -> Self {
                self.root_certs.push(pem.into());
                self
            }

            /// Set a client certificate + private key (both PEM) for **mutual TLS**.
            /// The server must be configured to require/verify client certificates.
            /// Only meaningful together with [`TlsConfig::SecureTls`].
            #[must_use]
            pub fn client_identity(
                mut self,
                cert_pem: impl Into<Vec<u8>>,
                key_pem: impl Into<Vec<u8>>,
            ) -> Self {
                self.client_identity = Some(ClientIdentity::new(cert_pem, key_pem));
                self
            }

            /// Set credentials.
            pub fn credentials(mut self, creds: ServerCredentials) -> Self {
                self.credentials = Some(creds);
                self
            }

            /// Set read strategy.
            pub fn read_from(mut self, read_from: ReadFrom) -> Self {
                self.read_from = read_from;
                self
            }

            /// Set request timeout.
            pub fn request_timeout(mut self, timeout: Duration) -> Self {
                self.request_timeout = Some(timeout);
                self
            }

            /// Set the connection-establishment timeout.
            pub fn connection_timeout(mut self, timeout: Duration) -> Self {
                self.connection_timeout = Some(timeout);
                self
            }

            /// Set the maximum number of concurrent in-flight requests.
            pub fn inflight_requests_limit(mut self, limit: u32) -> Self {
                self.inflight_requests_limit = Some(limit);
                self
            }

            /// Set client name.
            pub fn client_name(mut self, name: impl Into<String>) -> Self {
                self.client_name = Some(name.into());
                self
            }

            /// Set protocol version.
            pub fn protocol(mut self, protocol: ProtocolVersion) -> Self {
                self.protocol = protocol;
                self
            }

            /// Enable lazy connection.
            pub fn lazy_connect(mut self, lazy: bool) -> Self {
                self.lazy_connect = lazy;
                self
            }

            /// Set reconnection strategy.
            pub fn reconnect_strategy(mut self, strategy: BackoffStrategy) -> Self {
                self.reconnect_strategy = Some(strategy);
                self
            }

            /// Set Pub/Sub subscriptions to establish on connect.
            pub fn subscriptions(mut self, subscriptions: PubSubSubscriptions) -> Self {
                self.pubsub_subscriptions = Some(subscriptions);
                self
            }

            /// Enable the Pub/Sub push channel without configuring any connect-time
            /// subscriptions, so the client can use runtime `subscribe`/`unsubscribe`
            /// and receive messages via `get_pubsub_message`. Connect-time
            /// `subscriptions` enable this implicitly.
            ///
            /// Note: runtime subscriptions are session-scoped — unlike connect-time
            /// subscriptions they are not automatically restored after a reconnect.
            #[must_use]
            pub fn enable_pubsub(mut self) -> Self {
                self.force_pubsub_channel = true;
                self
            }

            /// Lower the fields shared by both configurations into a
            /// `ConnectionRequest`. Mode-specific fields (`cluster_mode_enabled`,
            /// `database_id`, `periodic_checks`) are layered on by `to_request()`.
            pub(crate) fn common_request(&self) -> glide_core::client::ConnectionRequest {
                use glide_core::client::ConnectionRequest;
                let mut req = ConnectionRequest {
                    addresses: self.addresses.iter().cloned().map(Into::into).collect(),
                    tls_mode: Some(self.tls.into()),
                    read_from: Some(self.read_from.clone().into()),
                    protocol: Some(self.protocol.into()),
                    client_name: self.client_name.clone(),
                    // Identify this client library to the server (CLIENT INFO /
                    // lib-name), mirroring the other GLIDE wrappers (GlidePy,
                    // GlideJava, ...).
                    lib_name: Some("GlideRust".to_string()),
                    lazy_connect: self.lazy_connect,
                    inflight_requests_limit: self.inflight_requests_limit,
                    // Disable Nagle's algorithm. We build `ConnectionRequest`
                    // directly, so we do NOT inherit glide-core's protobuf-path
                    // default of `tcp_nodelay = true` (the bare struct `Default` is
                    // `false`). Leaving Nagle on interacts with delayed-ACK to add
                    // multi-ms tail latency under high concurrency, so we
                    // explicitly enable TCP_NODELAY to match the intended core
                    // default.
                    tcp_nodelay: true,
                    // All other fields (periodic_checks, database_id,
                    // pubsub_subscriptions, cluster_mode_enabled, tls certs, otel,
                    // IAM, ...) are intentionally left at their core defaults here
                    // and set by the caller's `to_request()`. If glide-core adds a
                    // field this default absorbs it silently — revisit when bumping
                    // the glide-core dependency.
                    ..ConnectionRequest::default()
                };

                if !self.root_certs.is_empty() {
                    req.root_certs = self.root_certs.clone();
                }
                if let Some(identity) = &self.client_identity {
                    req.client_cert = identity.cert_pem().to_vec();
                    req.client_key = identity.key_pem().to_vec();
                }
                if let Some(subs) = &self.pubsub_subscriptions {
                    req.pubsub_subscriptions = Some(subs.to_core());
                }
                if let Some(creds) = &self.credentials {
                    req.authentication_info = Some(creds.to_core());
                }
                if let Some(t) = self.request_timeout {
                    req.request_timeout = Some(duration_as_millis_u32(t));
                }
                if let Some(t) = self.connection_timeout {
                    req.connection_timeout = Some(duration_as_millis_u32(t));
                }
                if let Some(strategy) = self.reconnect_strategy {
                    req.connection_retry_strategy = Some(strategy.into());
                }
                req
            }
        }
    };
}

pub(crate) use impl_common_config_builders;
