// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use super::get_valkey_connection_info;
use super::reconnecting_connection::{ReconnectReason, ReconnectingConnection};
use super::{ConnectionRequest, NodeAddress, NodeDiscoveryMode, TlsMode};
use crate::client::types::ReadFrom as ClientReadFrom;
use futures::{StreamExt, future, stream};
use glide_logger::log_debug;
use glide_logger::log_info;
use glide_logger::log_warn;
use glide_telemetry::Telemetry;
use redis::aio::ConnectionLike;
use redis::cluster_routing::{
    self, ResponsePolicy, Routable, RoutingInfo, SingleNodeRoutingInfo, SlotAddr, is_readonly_cmd,
};
use redis::{AddressResolver, PushInfo, RedisError, RedisResult, RetryStrategy, Value};
use std::sync::atomic::{AtomicBool, AtomicI64, AtomicU64, AtomicUsize, Ordering};
use std::sync::{Arc, Weak};
use std::time::Duration;
use tokio::sync::{Notify, mpsc};
use tokio::task;

/// Build a [`crate::tls_reload::CertReloadManager`] when path-based mTLS is
/// configured, starting its background reload task if reload is enabled. Returns
/// `Ok(None)` when no cert paths are configured. The manager performs the initial
/// parse + validation, so a bad initial cert/key surfaces here as an error.
/// `root_cert` is the already-combined-and-validated root material (see
/// [`super::combine_root_certs`]).
async fn build_cert_material_manager(
    connection_request: &ConnectionRequest,
    root_cert: Option<Vec<u8>>,
) -> Result<Option<Arc<crate::tls_reload::CertReloadManager>>, String> {
    let (Some(cert_path), Some(key_path)) = (
        connection_request.client_cert_path.as_ref(),
        connection_request.client_key_path.as_ref(),
    ) else {
        return Ok(None);
    };

    let interval_seconds = connection_request
        .cert_reload
        .as_ref()
        .filter(|cfg| cfg.enabled)
        .map(|cfg| cfg.interval_seconds);

    let mut manager = crate::tls_reload::CertReloadManager::new(
        cert_path.into(),
        key_path.into(),
        root_cert,
        interval_seconds.flatten(),
    )
    .await
    .map_err(|err| err.to_string())?;

    // Only spin up the background re-read task when reload is explicitly enabled;
    // otherwise the path-based config behaves like static (load-once) mTLS.
    if interval_seconds.is_some() {
        manager.start_reload_task();
    }

    Ok(Some(Arc::new(manager)))
}

#[derive(Debug)]
enum ReadFrom {
    Primary,
    PreferReplica {
        latest_read_replica_index: Arc<AtomicUsize>,
    },
    AllNodes {
        latest_read_node_index: Arc<AtomicUsize>,
    },
    AZAffinity {
        client_az: String,
        last_read_replica_index: Arc<AtomicUsize>,
    },
    AZAffinityReplicasAndPrimary {
        client_az: String,
        latest_read_node_index: Arc<AtomicUsize>,
    },
    AZAffinityAllNodes {
        client_az: String,
        latest_read_node_index: Arc<AtomicUsize>,
    },
}

/// Shortest and longest wait before retrying replicas that missed a database change.
const REPLICA_SYNC_MIN_RETRY_DELAY: Duration = Duration::from_secs(1);
const REPLICA_SYNC_MAX_RETRY_DELAY: Duration = Duration::from_secs(30);

/// Tracks which replicas are on the client's current database.
///
/// A `SELECT` inside a batch runs on the primary only. A background task then runs
/// it on each replica, and reads skip a replica until it has, so reads sent after
/// the batch returns do not see the previous database.
#[derive(Debug)]
struct DatabaseSync {
    /// Incremented on every database change.
    generation: AtomicU64,
    /// The database replicas should be on. A batch `SELECT` writes it before
    /// incrementing `generation`, so the sync task, which reads `generation` first,
    /// sees that database or a later one. A `SELECT` sent to every node writes it
    /// after marking every replica applied, so the task has nothing to send for it.
    database_id: AtomicI64,
    /// The last generation each node has applied, indexed like `nodes`.
    /// The primary's entry is unused: every database change reaches it.
    node_generations: Vec<AtomicU64>,
    /// Held while sending `SELECT` to replicas, so the sync task and a `SELECT`
    /// sent to every node cannot interleave on one replica.
    replica_select_lock: tokio::sync::Mutex<()>,
    /// Whether the sync task is running. At most one runs per client.
    sync_task_running: AtomicBool,
    /// Wakes the sync task from its retry delay when the database changes.
    database_changed: Arc<Notify>,
}

impl DatabaseSync {
    fn new(node_count: usize) -> Self {
        Self {
            generation: AtomicU64::new(0),
            database_id: AtomicI64::new(0),
            node_generations: (0..node_count).map(|_| AtomicU64::new(0)).collect(),
            replica_select_lock: tokio::sync::Mutex::new(()),
            sync_task_running: AtomicBool::new(false),
            database_changed: Arc::new(Notify::new()),
        }
    }

    /// Starts a database change; replicas are stale until they apply it.
    fn next_generation(&self) -> u64 {
        self.generation.fetch_add(1, Ordering::AcqRel) + 1
    }

    fn is_latest(&self, generation: u64) -> bool {
        self.generation.load(Ordering::Acquire) == generation
    }

    fn node_has_applied(&self, index: usize, generation: u64) -> bool {
        self.node_generations[index].load(Ordering::Acquire) >= generation
    }

    fn node_is_current(&self, index: usize) -> bool {
        self.node_has_applied(index, self.generation.load(Ordering::Acquire))
    }

    fn mark_node_applied(&self, index: usize, generation: u64) {
        self.node_generations[index].fetch_max(generation, Ordering::AcqRel);
    }

    fn mark_all_applied(&self, generation: u64) {
        for index in 0..self.node_generations.len() {
            self.mark_node_applied(index, generation);
        }
    }
}

#[derive(Debug)]
struct DropWrapper {
    /// Connection to the primary node in the client.
    primary_index: usize,
    nodes: Vec<ReconnectingConnection>,
    read_from: ReadFrom,
    database_sync: DatabaseSync,
    /// When true, write commands are blocked and INFO REPLICATION is skipped during connection.
    read_only: bool,
    /// Owns the background mTLS certificate reload task, when path-based reload is
    /// configured. Held here so the task lives for the client's lifetime and is
    /// shut down when the client is dropped.
    _cert_material_manager: Option<Arc<crate::tls_reload::CertReloadManager>>,
    /// The TLS certificate material this client connected with, so scoped
    /// connections can inherit it (see [`super::InheritedCertMaterial`]).
    cert_material: super::InheritedCertMaterial,
}

impl Drop for DropWrapper {
    fn drop(&mut self) {
        for node in self.nodes.iter() {
            node.mark_as_dropped();
        }
    }
}

#[derive(Clone, Debug)]
pub struct StandaloneClient {
    inner: Arc<DropWrapper>,
}

impl Drop for StandaloneClient {
    fn drop(&mut self) {
        // Client was dropped, reduce the number of clients
        Telemetry::decr_total_clients(1);
    }
}

pub enum StandaloneClientConnectionError {
    NoAddressesProvided,
    FailedConnection(Vec<(Option<String>, RedisError)>),
    PrimaryConflictFound(String),
}

impl std::fmt::Debug for StandaloneClientConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            StandaloneClientConnectionError::NoAddressesProvided => {
                write!(f, "No addresses provided")
            }
            StandaloneClientConnectionError::FailedConnection(errs) => {
                match errs.len() {
                    0 => {
                        writeln!(f, "Failed without explicit error")?;
                    }
                    1 => {
                        let (ref address, ref error) = errs[0];
                        match address {
                            Some(address) => {
                                writeln!(f, "Received error for address `{address}`: {error}")?
                            }
                            None => writeln!(f, "Received error: {error}")?,
                        }
                    }
                    _ => {
                        writeln!(f, "Received errors:")?;
                        for (address, error) in errs {
                            match address {
                                Some(address) => writeln!(f, "{address}: {error}")?,
                                None => writeln!(f, "{error}")?,
                            }
                        }
                    }
                };
                Ok(())
            }
            StandaloneClientConnectionError::PrimaryConflictFound(found_primaries) => {
                writeln!(
                    f,
                    "Primary conflict. More than one primary found in a Standalone setup: {found_primaries}"
                )
            }
        }
    }
}

impl StandaloneClient {
    pub async fn create_client(
        connection_request: ConnectionRequest,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
        iam_token_manager: Option<&Arc<crate::iam::IAMTokenManager>>,
        pubsub_synchronizer: Option<Arc<dyn crate::pubsub::PubSubSynchronizer>>,
    ) -> Result<Self, StandaloneClientConnectionError> {
        Self::create_client_with_rdma(
            connection_request,
            push_sender,
            iam_token_manager,
            pubsub_synchronizer,
            None,
        )
        .await
    }

    /// As [`Self::create_client`], with every node connection able to open an RDMA
    /// session over `rdma_fabric`. `None` behaves exactly like `create_client`.
    pub(crate) async fn create_client_with_rdma(
        connection_request: ConnectionRequest,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
        iam_token_manager: Option<&Arc<crate::iam::IAMTokenManager>>,
        pubsub_synchronizer: Option<Arc<dyn crate::pubsub::PubSubSynchronizer>>,
        rdma_fabric: Option<crate::rdma::Fabric>,
    ) -> Result<Self, StandaloneClientConnectionError> {
        if connection_request.addresses.is_empty() {
            return Err(StandaloneClientConnectionError::NoAddressesProvided);
        }

        super::validate_unix_socket_addresses(&connection_request).map_err(|message| {
            StandaloneClientConnectionError::FailedConnection(vec![(
                None,
                RedisError::from((
                    redis::ErrorKind::InvalidClientConfig,
                    "Invalid Unix domain socket address",
                    message,
                )),
            )])
        })?;

        // Validate read_only mode is not combined with AZAffinity strategies
        if connection_request.read_only
            && matches!(
                connection_request.read_from,
                Some(ClientReadFrom::AZAffinity(_))
                    | Some(ClientReadFrom::AZAffinityReplicasAndPrimary(_))
                    | Some(ClientReadFrom::AZAffinityAllNodes(_))
            )
        {
            return Err(StandaloneClientConnectionError::FailedConnection(vec![(
                None,
                RedisError::from((
                    redis::ErrorKind::InvalidClientConfig,
                    "read-only mode is not compatible with AZAffinity strategies",
                )),
            )]));
        }

        // Validate read_only mode is not combined with DISCOVER_ALL
        if connection_request.read_only
            && connection_request.node_discovery_mode == NodeDiscoveryMode::DiscoverAll
        {
            return Err(StandaloneClientConnectionError::FailedConnection(vec![(
                None,
                RedisError::from((
                    redis::ErrorKind::InvalidClientConfig,
                    "read-only mode is not compatible with DISCOVER_ALL node discovery mode",
                )),
            )]));
        }

        let valkey_connection_info =
            get_valkey_connection_info(&connection_request, iam_token_manager).await;
        let retry_strategy = match connection_request.connection_retry_strategy {
            Some(strategy) => RetryStrategy::try_new(
                strategy.exponent_base,
                strategy.factor,
                strategy.number_of_retries,
                strategy.jitter_percent,
            )
            .map_err(|err| StandaloneClientConnectionError::FailedConnection(vec![(None, err)]))?,
            None => RetryStrategy::default(),
        };

        let tls_mode = connection_request.tls_mode;
        let node_count = connection_request.addresses.len();
        let discover_az = matches!(
            connection_request.read_from,
            Some(ClientReadFrom::AZAffinity(_))
                | Some(ClientReadFrom::AZAffinityReplicasAndPrimary(_))
                | Some(ClientReadFrom::AZAffinityAllNodes(_))
        );

        let connection_timeout = connection_request.get_connection_timeout();

        let tcp_nodelay = connection_request.tcp_nodelay;

        let has_root_certs = !connection_request.root_certs.is_empty();
        let has_client_cert = !connection_request.client_cert.is_empty();
        let has_client_key = !connection_request.client_key.is_empty();
        let has_cert_path = connection_request.client_cert_path.is_some();
        let has_key_path = connection_request.client_key_path.is_some();
        super::validate_client_cert_config(
            has_client_cert,
            has_client_key,
            has_cert_path,
            has_key_path,
        )
        .map_err(|err| StandaloneClientConnectionError::FailedConnection(vec![(None, err)]))?;

        // Combine + validate the root certs first (fail fast on an empty entry) so the
        // cert-reload manager, built next, never sees unvalidated root material.
        let root_cert_bytes = super::combine_root_certs(&connection_request.root_certs)
            .map_err(|err| StandaloneClientConnectionError::FailedConnection(vec![(None, err)]))?;

        // Build the certificate reload manager when path-based mTLS is configured.
        // It performs the initial parse + validation and, if reload is enabled,
        // drives the background re-read task. The resulting handle is shared with
        // every node's reconnection loop.
        let cert_material_manager =
            build_cert_material_manager(&connection_request, root_cert_bytes.clone())
                .await
                .map_err(|err| {
                    StandaloneClientConnectionError::FailedConnection(vec![(
                        None,
                        RedisError::from((
                            redis::ErrorKind::InvalidClientConfig,
                            "TLS certificate reload configuration error",
                            err,
                        )),
                    )])
                })?;
        let cert_material_handle = cert_material_manager.as_ref().map(|m| m.get_handle());

        let tls_params = if let Some(manager) = &cert_material_manager {
            // Path-based mTLS: seed the initial params from the (validated) manager.
            if tls_mode.unwrap_or(TlsMode::NoTls) == TlsMode::NoTls {
                return Err(StandaloneClientConnectionError::FailedConnection(vec![(
                    None,
                    RedisError::from((
                        redis::ErrorKind::InvalidClientConfig,
                        "TLS certificates provided but TLS is disabled",
                    )),
                )]));
            }
            Some(manager.get_params().await)
        } else if has_root_certs || has_client_cert || has_client_key {
            if tls_mode.unwrap_or(TlsMode::NoTls) == TlsMode::NoTls {
                return Err(StandaloneClientConnectionError::FailedConnection(vec![(
                    None,
                    RedisError::from((
                        redis::ErrorKind::InvalidClientConfig,
                        "TLS certificates provided but TLS is disabled",
                    )),
                )]));
            }

            let client_tls = if has_client_cert && has_client_key {
                Some(redis::ClientTlsConfig {
                    client_cert: connection_request.client_cert.clone(),
                    client_key: connection_request.client_key.clone(),
                })
            } else {
                None
            };

            let tls_certificates = redis::TlsCertificates {
                client_tls,
                root_cert: root_cert_bytes,
            };
            Some(
                redis::retrieve_tls_certificates(tls_certificates).map_err(|err| {
                    StandaloneClientConnectionError::FailedConnection(vec![(None, err)])
                })?,
            )
        } else {
            None
        };

        // Snapshot both halves before the locals are handed to the nodes; scoped
        // connections inherit them from the parent `Client`.
        let cert_material = super::InheritedCertMaterial {
            tls_params: tls_params.clone(),
            reload_handle: cert_material_handle.clone(),
        };

        let read_only = connection_request.read_only;
        let node_discovery_mode = connection_request.node_discovery_mode;
        let addresses = connection_request.addresses.clone();
        let read_from_option = connection_request.read_from.clone();

        let iam_token_handle = iam_token_manager.map(|m| m.get_token_handle());

        // Clone values needed for post-stream discovery connections
        let discovery_conn_info = valkey_connection_info.clone();
        let discovery_push_sender = push_sender.clone();
        let discovery_tls_params = tls_params.clone();
        let discovery_pubsub_sync = pubsub_synchronizer.clone();
        let discovery_iam_handle = iam_token_handle.clone();
        let discovery_cert_handle = cert_material_handle.clone();
        let discovery_resolver = connection_request.address_resolver.clone();
        let discovery_rdma_fabric = rdma_fabric.clone();
        let first_pass_rdma_fabric = discovery_rdma_fabric.clone();

        let mut stream = stream::iter(addresses)
            .map(move |address| {
                let info = valkey_connection_info.clone();
                let retry = retry_strategy;
                let sender = push_sender.clone();
                let tls = tls_mode.unwrap_or(TlsMode::NoTls);
                let discover = discover_az;
                let timeout = connection_timeout;
                let params = tls_params.clone();
                let nodelay = tcp_nodelay;
                let sync = pubsub_synchronizer.clone();
                let skip_replication =
                    read_only || node_discovery_mode == NodeDiscoveryMode::Static;
                let resolver = connection_request.address_resolver.clone();
                let iam_handle = iam_token_handle.clone();
                let cert_handle = cert_material_handle.clone();
                let rdma_fabric = first_pass_rdma_fabric.clone();
                async move {
                    get_connection_and_replication_info(
                        &address,
                        &retry,
                        &info,
                        tls,
                        &sender,
                        discover,
                        timeout,
                        params,
                        nodelay,
                        &sync,
                        skip_replication,
                        resolver.as_ref(),
                        iam_handle,
                        cert_handle,
                        rdma_fabric.clone(),
                    )
                    .await
                    .map_err(|err| (address.endpoint(), err))
                }
            })
            .buffer_unordered(node_count);

        let mut nodes = Vec::with_capacity(node_count);
        let mut addresses_and_errors = Vec::with_capacity(node_count);
        let mut primary_index = if read_only || node_discovery_mode == NodeDiscoveryMode::Static {
            Some(0)
        } else {
            None
        };
        let mut replication_infos: Vec<Option<String>> = Vec::with_capacity(node_count);

        while let Some(result) = stream.next().await {
            match result {
                Ok((connection, replication_status)) => {
                    nodes.push(connection);
                    // Parse replication info string and store for potential discovery.
                    // None if STATIC mode or connection error; empty string is handled
                    // gracefully by parsing functions (no matches → no discovery).
                    let info_str = replication_status
                        .and_then(|status| redis::from_owned_redis_value::<String>(status).ok());
                    let is_primary = info_str
                        .as_ref()
                        .is_some_and(|val| val.contains("role:master"));
                    replication_infos.push(info_str);

                    if is_primary {
                        if let Some(existing_primary) = primary_index {
                            // More than one primary found
                            return Err(StandaloneClientConnectionError::PrimaryConflictFound(
                                format!(
                                    "Primary nodes: {:?}, {:?}",
                                    nodes.pop(),
                                    nodes.get(existing_primary)
                                ),
                            ));
                        }
                        primary_index = Some(nodes.len().saturating_sub(1));
                    }
                }
                Err((address, boxed_err)) => {
                    let (connection, err) = *boxed_err;
                    nodes.push(connection);
                    replication_infos.push(None);
                    addresses_and_errors.push((Some(address), err));
                }
            }
        }

        // Topology discovery: connect to nodes found in INFO REPLICATION responses.
        // Each discovered connection uses the same connection_timeout as initial connections.
        // Unreachable discovered nodes are logged and skipped (not fatal).
        // Discovery is bounded: at most 2 levels deep (replica → primary → primary's replicas).
        if node_discovery_mode == NodeDiscoveryMode::DiscoverAll {
            let mut discovered: Vec<NodeAddress> = Vec::new();
            let existing: Vec<String> = connection_request
                .addresses
                .iter()
                .map(NodeAddress::endpoint)
                .collect();

            // Phase 1: Parse initial INFO REPLICATION responses.
            // If replication_infos is empty (all connections failed), this loop is
            // skipped and the "Validate we have required connections" block handles the error.
            for info_str in replication_infos.iter().flatten() {
                if is_primary_role(info_str) {
                    let replicas = parse_replica_addresses(info_str);
                    log_info(
                        "topology discovery",
                        format!("Discovered {} replica(s) from primary", replicas.len()),
                    );
                    for r in replicas {
                        if !address_is_known(&r, &existing, &discovered) {
                            discovered.push(r);
                        }
                    }
                } else if let Some(primary_addr) = parse_primary_address(info_str) {
                    log_info(
                        "topology discovery",
                        format!(
                            "Discovered primary at {}:{}",
                            primary_addr.host, primary_addr.port
                        ),
                    );
                    if !address_is_known(&primary_addr, &existing, &discovered) {
                        discovered.push(primary_addr);
                    }
                }
            }

            // Phase 2: Connect to discovered nodes in parallel
            let tls = tls_mode.unwrap_or(TlsMode::NoTls);
            let discovered_count = discovered.len();

            let mut phase2_stream = stream::iter(discovered.clone())
                .map(|address| {
                    let conn_info = discovery_conn_info.clone();
                    let sender = discovery_push_sender.clone();
                    let params = discovery_tls_params.clone();
                    let sync = discovery_pubsub_sync.clone();
                    let iam_handle = discovery_iam_handle.clone();
                    let cert_handle = discovery_cert_handle.clone();
                    let resolver = discovery_resolver.clone();
                    let rdma_fabric = discovery_rdma_fabric.clone();
                    async move {
                        let result = get_connection_and_replication_info(
                            &address,
                            &retry_strategy,
                            &conn_info,
                            tls,
                            &sender,
                            discover_az,
                            connection_timeout,
                            params,
                            tcp_nodelay,
                            &sync,
                            false,
                            resolver.as_ref(),
                            iam_handle,
                            cert_handle,
                            rdma_fabric.clone(),
                        )
                        .await;
                        (address, result)
                    }
                })
                .buffer_unordered(discovered_count);

            let mut phase3_addresses: Vec<NodeAddress> = Vec::new();
            while let Some((addr, result)) = phase2_stream.next().await {
                match result {
                    Ok((connection, replication_status)) => {
                        let info_str = replication_status
                            .and_then(|s| redis::from_owned_redis_value::<String>(s).ok());
                        let is_primary =
                            info_str.as_ref().is_some_and(|v| v.contains("role:master"));

                        if is_primary && primary_index.is_none() {
                            primary_index = Some(nodes.len());
                        }
                        nodes.push(connection);

                        // Collect Phase 3 addresses from the primary's replica list
                        if let Some(info) = info_str.as_deref().filter(|_| is_primary) {
                            for r in parse_replica_addresses(info) {
                                if !address_is_known(&r, &existing, &discovered)
                                    && !address_is_known(&r, &existing, &phase3_addresses)
                                {
                                    phase3_addresses.push(r);
                                }
                            }
                        }
                    }
                    Err(boxed_err) => {
                        let (_connection, err) = *boxed_err;
                        log_warn(
                            "topology discovery",
                            format!(
                                "Failed to connect to discovered node {}:{}: {}",
                                addr.host, addr.port, err
                            ),
                        );
                    }
                }
            }

            // Phase 3: Connect to replicas discovered from the primary, in parallel
            if !phase3_addresses.is_empty() {
                let phase3_count = phase3_addresses.len();
                let mut phase3_stream = stream::iter(phase3_addresses)
                    .map(|address| {
                        let conn_info = discovery_conn_info.clone();
                        let sender = discovery_push_sender.clone();
                        let params = discovery_tls_params.clone();
                        let sync = discovery_pubsub_sync.clone();
                        let iam_handle = discovery_iam_handle.clone();
                        let cert_handle = discovery_cert_handle.clone();
                        let rdma_fabric = discovery_rdma_fabric.clone();
                        let resolver = discovery_resolver.clone();
                        async move {
                            let result = get_connection_and_replication_info(
                                &address,
                                &retry_strategy,
                                &conn_info,
                                tls,
                                &sender,
                                discover_az,
                                connection_timeout,
                                params,
                                tcp_nodelay,
                                &sync,
                                false,
                                resolver.as_ref(),
                                iam_handle,
                                cert_handle,
                                rdma_fabric.clone(),
                            )
                            .await;
                            (address, result)
                        }
                    })
                    .buffer_unordered(phase3_count);

                while let Some((addr, result)) = phase3_stream.next().await {
                    match result {
                        Ok((conn, _)) => nodes.push(conn),
                        Err(boxed_err) => {
                            let (_conn, err) = *boxed_err;
                            log_warn(
                                "topology discovery",
                                format!(
                                    "Failed to connect to discovered replica {}:{}: {}",
                                    addr.host, addr.port, err
                                ),
                            );
                        }
                    }
                }
            }

            if !discovered.is_empty() {
                log_info(
                    "topology discovery",
                    format!("Full topology: {} node(s) connected", nodes.len()),
                );
            }
        }

        // Validate we have required connections
        let primary_index = if read_only {
            // In read-only mode, we need at least one successful connection
            if nodes.is_empty() && !addresses_and_errors.is_empty() {
                return Err(StandaloneClientConnectionError::FailedConnection(
                    addresses_and_errors,
                ));
            }
            0 // primary_index won't be used for writes in read-only mode
        } else {
            // Normal mode requires a primary
            match primary_index {
                Some(idx) => idx,
                None => {
                    let mut errors = addresses_and_errors;
                    if errors.is_empty() {
                        errors.insert(
                            0,
                            (
                                None,
                                RedisError::from((
                                    redis::ErrorKind::ClientError,
                                    "No primary node found",
                                )),
                            ),
                        )
                    };
                    return Err(StandaloneClientConnectionError::FailedConnection(errors));
                }
            }
        };

        if !addresses_and_errors.is_empty() {
            log_warn(
                "client creation",
                format!(
                    "Failed to connect to {addresses_and_errors:?}, will attempt to reconnect."
                ),
            );
        }
        let read_from = if read_only && read_from_option.is_none() {
            // Default to PreferReplica when read_only=true and no ReadFrom specified
            ReadFrom::PreferReplica {
                latest_read_replica_index: Default::default(),
            }
        } else {
            get_read_from(read_from_option)
        };

        #[cfg(feature = "standalone_heartbeat")]
        for node in nodes.iter() {
            Self::start_heartbeat(node.clone());
        }

        for node in nodes.iter() {
            Self::start_periodic_connection_check(node.clone());
        }

        // Successfully created new client. Update the telemetry
        Telemetry::incr_total_clients(1);

        let database_sync = DatabaseSync::new(nodes.len());
        Ok(Self {
            inner: Arc::new(DropWrapper {
                primary_index,
                nodes,
                read_from,
                database_sync,
                read_only,
                _cert_material_manager: cert_material_manager,
                cert_material,
            }),
        })
    }

    /// The TLS certificate material this client connected with. Read when a
    /// scoped connection inherits the parent's mTLS identity and trust roots.
    pub(super) fn cert_material(&self) -> &super::InheritedCertMaterial {
        &self.inner.cert_material
    }

    fn get_primary_connection(&self) -> &ReconnectingConnection {
        self.inner.nodes.get(self.inner.primary_index).unwrap()
    }

    /// Whether reads can go to a replica.
    fn reads_from_replicas(&self) -> bool {
        self.inner.nodes.len() > 1 && !matches!(self.inner.read_from, ReadFrom::Primary)
    }

    /// Whether the node at `index` is on the client's current database. See [`DatabaseSync`].
    fn is_on_current_database(&self, index: usize) -> bool {
        index == self.inner.primary_index || self.inner.database_sync.node_is_current(index)
    }

    /// Round-robins through replicas (skipping the primary) and returns the first connected one
    /// that is on the current database. Falls back to the primary if there is none.
    fn round_robin_read_from_replica(
        &self,
        latest_read_replica_index: &Arc<AtomicUsize>,
    ) -> &ReconnectingConnection {
        let initial_index = latest_read_replica_index.load(Ordering::Relaxed);
        let mut check_count = 0;
        loop {
            check_count += 1;

            // Looped through all replicas, no connected replica was found.
            if check_count > self.inner.nodes.len() {
                return self.get_primary_connection();
            }
            let index = (initial_index + check_count) % self.inner.nodes.len();
            if index == self.inner.primary_index {
                continue;
            }
            let Some(connection) = self.inner.nodes.get(index) else {
                continue;
            };
            if self.is_on_current_database(index) && connection.is_connected() {
                let _ = latest_read_replica_index.compare_exchange_weak(
                    initial_index,
                    index,
                    Ordering::Relaxed,
                    Ordering::Relaxed,
                );
                return connection;
            }
        }
    }

    fn round_robin_read_from_all_nodes(
        &self,
        latest_read_node_index: &Arc<AtomicUsize>,
    ) -> &ReconnectingConnection {
        let initial_index = latest_read_node_index.load(Ordering::Relaxed);
        let mut check_count = 0;
        loop {
            check_count += 1;

            // Looped through all nodes, no connected node was found.
            if check_count > self.inner.nodes.len() {
                return self.get_primary_connection();
            }
            let index = (initial_index + check_count) % self.inner.nodes.len();
            let Some(connection) = self.inner.nodes.get(index) else {
                continue;
            };
            if self.is_on_current_database(index) && connection.is_connected() {
                let _ = latest_read_node_index.compare_exchange_weak(
                    initial_index,
                    index,
                    Ordering::Relaxed,
                    Ordering::Relaxed,
                );
                return connection;
            }
        }
    }

    /// Round-robins through nodes and returns the first one whose availability zone
    /// matches `client_az`. Skips the primary unless `include_primary` is set.
    /// Nodes that are not currently connected, or not yet on the current database,
    /// are skipped rather than awaited. Returns `None` if no match is found.
    async fn get_next_local_node(
        &self,
        latest_read_node_index: &Arc<AtomicUsize>,
        client_az: &str,
        include_primary: bool,
    ) -> Option<&ReconnectingConnection> {
        let initial_index = latest_read_node_index.load(Ordering::Relaxed);
        let mut retries = 0usize;

        loop {
            retries = retries.saturating_add(1);
            // Looped through all nodes; no connected node found in the same AZ.
            if retries > self.inner.nodes.len() {
                return None;
            }

            // Calculate index based on initial index and check count.
            let index = (initial_index + retries) % self.inner.nodes.len();
            if !include_primary && index == self.inner.primary_index {
                continue;
            }
            if !self.is_on_current_database(index) {
                continue;
            }
            let node = &self.inner.nodes[index];

            // Skip nodes that are not currently connected instead of awaiting their recovery.
            if let Some(connection) = node.try_get_connection().await
                && let Some(node_az) = connection.get_az().as_deref()
                && node_az == client_az
            {
                // Update `latest_read_node_index` with the index of this node.
                let _ = latest_read_node_index.compare_exchange_weak(
                    initial_index,
                    index,
                    Ordering::Relaxed,
                    Ordering::Relaxed,
                );
                return Some(node);
            }
        }
    }

    /// AZAffinity strategy: same-AZ replica → any replica (round-robin) → primary (last resort).
    async fn round_robin_read_from_replica_az_awareness(
        &self,
        latest_read_replica_index: &Arc<AtomicUsize>,
        client_az: &str,
    ) -> &ReconnectingConnection {
        if let Some(replica) = self
            .get_next_local_node(latest_read_replica_index, client_az, false)
            .await
        {
            return replica;
        }
        self.round_robin_read_from_replica(latest_read_replica_index)
    }

    /// AZAffinityReplicasAndPrimary strategy: same-AZ replica → same-AZ primary → any node (round-robin).
    async fn round_robin_read_from_replica_az_awareness_replicas_and_primary(
        &self,
        latest_read_node_index: &Arc<AtomicUsize>,
        client_az: &str,
    ) -> &ReconnectingConnection {
        if let Some(replica) = self
            .get_next_local_node(latest_read_node_index, client_az, false)
            .await
        {
            return replica;
        }

        // Step 2: Check if primary is in the same AZ, skipping it if not currently connected.
        let primary = self.get_primary_connection();
        if let Some(connection) = primary.try_get_connection().await
            && let Some(primary_az) = connection.get_az().as_deref()
            && primary_az == client_az
        {
            return primary;
        }

        // Step 3: Fall back to any available node using round-robin
        self.round_robin_read_from_all_nodes(latest_read_node_index)
    }

    /// AZAffinityAllNodes strategy: same-AZ node (primary or replica, equal round-robin)
    /// → any node (round-robin).
    async fn round_robin_read_from_all_nodes_az_awareness(
        &self,
        latest_read_node_index: &Arc<AtomicUsize>,
        client_az: &str,
    ) -> &ReconnectingConnection {
        if let Some(node) = self
            .get_next_local_node(latest_read_node_index, client_az, true)
            .await
        {
            return node;
        }

        // Fall back to any available node using round-robin
        self.round_robin_read_from_all_nodes(latest_read_node_index)
    }

    async fn get_connection(&self, readonly: bool) -> &ReconnectingConnection {
        if self.inner.nodes.len() == 1 || !readonly {
            return self.get_primary_connection();
        }

        match &self.inner.read_from {
            ReadFrom::Primary => self.get_primary_connection(),
            ReadFrom::PreferReplica {
                latest_read_replica_index,
            } => self.round_robin_read_from_replica(latest_read_replica_index),
            ReadFrom::AllNodes {
                latest_read_node_index,
            } => self.round_robin_read_from_all_nodes(latest_read_node_index),
            ReadFrom::AZAffinity {
                client_az,
                last_read_replica_index,
            } => {
                self.round_robin_read_from_replica_az_awareness(last_read_replica_index, client_az)
                    .await
            }
            ReadFrom::AZAffinityReplicasAndPrimary {
                client_az,
                latest_read_node_index,
            } => {
                self.round_robin_read_from_replica_az_awareness_replicas_and_primary(
                    latest_read_node_index,
                    client_az,
                )
                .await
            }
            ReadFrom::AZAffinityAllNodes {
                client_az,
                latest_read_node_index,
            } => {
                self.round_robin_read_from_all_nodes_az_awareness(latest_read_node_index, client_az)
                    .await
            }
        }
    }

    async fn send_request(
        cmd: &redis::Cmd,
        reconnecting_connection: &ReconnectingConnection,
    ) -> RedisResult<Value> {
        // Mark command as sent for watchdog diagnostics
        cmd.watchdog_phase
            .store(redis::PHASE_SENT, std::sync::atomic::Ordering::Release);
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection.send_packed_command(cmd).await;
        match result {
            Err(err) if err.is_unrecoverable_error() => {
                log_warn("send request", format!("received disconnect error `{err}`"));
                reconnecting_connection.reconnect(ReconnectReason::ConnectionDropped);
                Err(err)
            }
            _ => result,
        }
    }

    pub(crate) async fn send_request_to_all_nodes(
        &mut self,
        cmd: &redis::Cmd,
        response_policy: Option<ResponsePolicy>,
    ) -> RedisResult<Value> {
        let requests = self
            .inner
            .nodes
            .iter()
            .map(|node| Self::send_request(cmd, node));

        // TODO - once Value::Error will be merged, these will need to be updated to handle this new value.
        match response_policy {
            Some(ResponsePolicy::AllSucceeded) => {
                future::try_join_all(requests)
                    .await
                    .map(|mut results| results.pop().unwrap()) // unwrap is safe, since at least one function succeeded
            }
            Some(ResponsePolicy::OneSucceeded) => future::select_ok(requests.map(Box::pin))
                .await
                .map(|(result, _)| result),
            Some(ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty) => {
                future::select_ok(requests.map(|request| {
                    Box::pin(async move {
                        let result = request.await?;
                        match result {
                            Value::Nil => {
                                Err((redis::ErrorKind::ResponseError, "no value found").into())
                            }
                            _ => Ok(result),
                        }
                    })
                }))
                .await
                .map(|(result, _)| result)
            }
            Some(ResponsePolicy::Aggregate(op)) => future::try_join_all(requests)
                .await
                .and_then(|results| cluster_routing::aggregate(results, op)),
            Some(ResponsePolicy::AggregateArray(op)) => future::try_join_all(requests)
                .await
                .and_then(|results| cluster_routing::aggregate_array(results, op)),
            Some(ResponsePolicy::AggregateLogical(op)) => future::try_join_all(requests)
                .await
                .and_then(|results| cluster_routing::logical_aggregate(results, op)),
            Some(ResponsePolicy::CombineArrays) => future::try_join_all(requests)
                .await
                .and_then(cluster_routing::combine_array_results),
            Some(ResponsePolicy::CombineMaps) => future::try_join_all(requests)
                .await
                .and_then(cluster_routing::combine_map_results),
            Some(ResponsePolicy::Special) => {
                // Await all futures and collect results
                let results = future::try_join_all(requests).await?;
                // Create key-value pairs where the key is the node address and the value is the corresponding result
                let node_result_pairs = self
                    .inner
                    .nodes
                    .iter()
                    .zip(results)
                    .map(|(node, result)| (Value::BulkString(node.node_address().into()), result))
                    .collect();

                Ok(Value::Map(node_result_pairs))
            }

            None => {
                // This is our assumption - if there's no coherent way to aggregate the responses, we just collect them in an array, and pass it to the user.
                // TODO - once Value::Error is merged, we can use join_all and report separate errors and also pass successes.
                future::try_join_all(requests).await.map(Value::Array)
            }
        }
    }

    async fn send_request_to_single_node(
        &mut self,
        cmd: &redis::Cmd,
        readonly: bool,
    ) -> RedisResult<Value> {
        let reconnecting_connection = self.get_connection(readonly).await;
        Self::send_request(cmd, reconnecting_connection).await
    }

    /// Sends `SELECT` or `RESET`, which change the database, to every node.
    ///
    /// Holding the replica `SELECT` lock keeps the replica sync task from sending an
    /// earlier batch's `SELECT` to a replica after this one. The new generation, set
    /// before the lock is released, then ends that task's work.
    async fn send_database_change_to_all_nodes(
        &mut self,
        cmd: &redis::Cmd,
        response_policy: Option<ResponsePolicy>,
    ) -> RedisResult<Value> {
        let inner = self.inner.clone();
        let sync = &inner.database_sync;
        let _guard = sync.replica_select_lock.lock().await;
        let result = self.send_request_to_all_nodes(cmd, response_policy).await;
        if result.is_ok() {
            sync.mark_all_applied(sync.next_generation());
        }
        result
    }

    pub async fn send_command(&mut self, cmd: &redis::Cmd) -> RedisResult<Value> {
        let Some(cmd_bytes) = Routable::command(cmd) else {
            return self.send_request_to_single_node(cmd, false).await;
        };

        // Block write commands in read-only mode
        if self.inner.read_only && !is_readonly_cmd(cmd_bytes.as_slice()) {
            return Err(RedisError::from((
                redis::ErrorKind::ReadOnly,
                "write commands are not allowed in read-only mode",
            )));
        }

        if RoutingInfo::is_all_nodes(cmd_bytes.as_slice()) {
            let response_policy = ResponsePolicy::for_command(cmd_bytes.as_slice());
            if matches!(cmd_bytes.as_slice(), b"SELECT" | b"RESET") {
                return self
                    .send_database_change_to_all_nodes(cmd, response_policy)
                    .await;
            }
            return self.send_request_to_all_nodes(cmd, response_policy).await;
        }
        self.send_request_to_single_node(cmd, is_readonly_cmd(cmd_bytes.as_slice()))
            .await
    }

    /// Sends a batch to one node. A non-atomic batch of only read-only commands
    /// (see [`is_readonly_nonatomic_pipeline`]) uses the client's read-from strategy;
    /// any other batch goes to the primary.
    pub async fn send_pipeline(
        &mut self,
        pipeline: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> RedisResult<Vec<Value>> {
        let readonly = self.reads_from_replicas() && is_readonly_nonatomic_pipeline(pipeline);
        let reconnecting_connection = self.get_connection(readonly).await;
        Self::send_pipeline_to(reconnecting_connection, pipeline, offset, count).await
    }

    /// Sends a batch to the primary, whatever its commands. Used for transactions,
    /// and for batches sent while a raw `MULTI` is open, which the primary must queue.
    pub async fn send_pipeline_to_primary(
        &mut self,
        pipeline: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> RedisResult<Vec<Value>> {
        Self::send_pipeline_to(self.get_primary_connection(), pipeline, offset, count).await
    }

    async fn send_pipeline_to(
        reconnecting_connection: &ReconnectingConnection,
        pipeline: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> RedisResult<Vec<Value>> {
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection
            .send_packed_commands(pipeline, offset, count)
            .await;
        match result {
            Err(err) if err.is_unrecoverable_error() => {
                log_warn(
                    "pipeline request",
                    format!("received disconnect error `{err}`"),
                );
                reconnecting_connection.reconnect(ReconnectReason::ConnectionDropped);
                Err(err)
            }
            _ => result,
        }
    }

    #[cfg(feature = "standalone_heartbeat")]
    fn start_heartbeat(reconnecting_connection: ReconnectingConnection) {
        task::spawn(async move {
            loop {
                tokio::time::sleep(super::HEARTBEAT_SLEEP_DURATION).await;
                if reconnecting_connection.is_dropped() {
                    log_debug(
                        "StandaloneClient",
                        "heartbeat stopped after connection was dropped",
                    );
                    // Client was dropped, heartbeat can stop.
                    return;
                }

                let Some(mut connection) = reconnecting_connection.try_get_connection().await
                else {
                    log_debug(
                        "StandaloneClient",
                        "heartbeat stopped while connection is reconnecting",
                    );
                    // Client is reconnecting..
                    continue;
                };
                log_debug("StandaloneClient", "performing heartbeat");
                if connection
                    .send_packed_command(&redis::cmd("PING"))
                    .await
                    .is_err_and(|err| err.is_connection_dropped() || err.is_connection_refusal())
                {
                    log_debug("StandaloneClient", "heartbeat triggered reconnect");
                    reconnecting_connection.reconnect(ReconnectReason::ConnectionDropped);
                }
            }
        });
    }

    // Monitors passive connection status and reconnects if necessary.
    // This function is cheaper alternative to start_heartbeat(),
    // as it avoids sending PING commands to the server, checking only the connection state.
    fn start_periodic_connection_check(reconnecting_connection: ReconnectingConnection) {
        task::spawn(async move {
            loop {
                reconnecting_connection
                    .wait_for_disconnect_with_timeout(&super::CONNECTION_CHECKS_INTERVAL)
                    .await;
                // check connection is valid
                if reconnecting_connection.is_dropped() {
                    log_debug(
                        "StandaloneClient",
                        "connection checker stopped after connection was dropped",
                    );

                    // Client was dropped, checker can stop.
                    return;
                }

                let Some(connection) = reconnecting_connection.try_get_connection().await else {
                    log_debug(
                        "StandaloneClient",
                        "connection checker is skipping a connections since its reconnecting",
                    );
                    // Client is reconnecting..
                    continue;
                };

                if connection.is_closed() {
                    log_debug(
                        "StandaloneClient",
                        "connection checker has triggered reconnect",
                    );
                    reconnecting_connection.reconnect(ReconnectReason::ConnectionDropped);
                }
            }
        });
    }

    /// Update the password used to authenticate with the servers.
    /// If the password is `None`, the password will be removed.
    pub async fn update_connection_password(
        &self,
        new_password: Option<String>,
    ) -> RedisResult<Value> {
        for node in self.inner.nodes.iter() {
            node.update_connection_password(new_password.clone());
        }

        Ok(Value::Okay)
    }

    /// Update the database id used to establish connection with the servers. Called
    /// after a `SELECT` or `RESET` that reached every node, which
    /// [`send_command`](Self::send_command) already marked on every replica.
    pub async fn update_connection_database(&self, database_id: i64) -> RedisResult<Value> {
        self.store_connection_database(database_id);
        Ok(Value::Okay)
    }

    fn store_connection_database(&self, database_id: i64) {
        for node in self.inner.nodes.iter() {
            node.update_connection_database(database_id);
        }
        self.inner
            .database_sync
            .database_id
            .store(database_id, Ordering::Release);
    }

    /// Records a database that a batch `SELECT` changed on the primary only. Reads
    /// skip every replica until the background sync task has run the same `SELECT`
    /// on it, each attempt bounded by `timeout`. A client that never reads from
    /// replicas only records the database for reconnects.
    pub(super) fn update_database_after_batch_select(&self, database_id: i64, timeout: Duration) {
        self.store_connection_database(database_id);
        self.inner.database_sync.next_generation();
        if self.reads_from_replicas() {
            start_replica_sync(&self.inner, timeout);
        }
    }

    /// Update the client_name used to create the connection.
    pub async fn update_connection_client_name(
        &self,
        new_client_name: Option<String>,
    ) -> RedisResult<Value> {
        for node in self.inner.nodes.iter() {
            node.update_connection_client_name(new_client_name.clone());
        }

        Ok(Value::Okay)
    }

    /// Update the username used to authenticate with the servers.
    ///
    /// This method updates the username for all connections and stores it for future reconnections.
    /// Typically called after a successful AUTH command with a username parameter.
    ///
    /// # Arguments
    ///
    /// * `new_username` - The username to use for authentication (None to clear)
    ///
    pub async fn update_connection_username(
        &self,
        new_username: Option<String>,
    ) -> RedisResult<Value> {
        for node in self.inner.nodes.iter() {
            node.update_connection_username(new_username.clone());
        }

        Ok(Value::Okay)
    }

    /// Update the protocol version used for connections.
    ///
    /// This method updates the protocol version for all connections and stores it for future reconnections.
    /// Typically called after a successful HELLO command that changes the protocol version.
    ///
    /// # Arguments
    ///
    /// * `new_protocol` - The protocol version to use (RESP2 or RESP3)
    ///
    pub async fn update_connection_protocol(
        &self,
        new_protocol: redis::ProtocolVersion,
    ) -> RedisResult<Value> {
        for node in self.inner.nodes.iter() {
            node.update_connection_protocol(new_protocol);
        }

        Ok(Value::Okay)
    }

    /// Retrieve the username used to authenticate with the server.
    pub fn get_username(&self) -> Option<String> {
        // All nodes in the client should have the same username configured, thus any connection would work here.
        self.get_primary_connection().get_username()
    }
}

// Passes through the large Err from ReconnectingConnection::new. Boxing it would change
// the error type at every call site.
// TODO: Box the Err payload and drop this allow - https://github.com/valkey-io/valkey-glide/issues/6819
#[allow(clippy::result_large_err)]
#[allow(clippy::too_many_arguments)]
async fn get_connection_and_replication_info(
    address: &NodeAddress,
    retry_strategy: &RetryStrategy,
    connection_info: &redis::RedisConnectionInfo,
    tls_mode: TlsMode,
    push_sender: &Option<mpsc::UnboundedSender<PushInfo>>,
    discover_az: bool,
    connection_timeout: Duration,
    tls_params: Option<redis::TlsConnParams>,
    tcp_nodelay: bool,
    pubsub_synchronizer: &Option<Arc<dyn crate::pubsub::PubSubSynchronizer>>,
    skip_replication_check: bool,
    address_resolver: Option<&Arc<dyn AddressResolver>>,
    iam_token_handle: Option<super::IAMTokenHandle>,
    cert_material_handle: Option<crate::tls_reload::CertReloadHandle>,
    rdma_fabric: Option<crate::rdma::Fabric>,
) -> Result<(ReconnectingConnection, Option<Value>), Box<(ReconnectingConnection, RedisError)>> {
    let reconnecting_connection = ReconnectingConnection::new(
        address,
        *retry_strategy,
        connection_info.clone(),
        tls_mode,
        push_sender.clone(),
        discover_az,
        connection_timeout,
        tls_params,
        tcp_nodelay,
        pubsub_synchronizer.clone(),
        address_resolver,
        iam_token_handle,
        cert_material_handle,
        rdma_fabric,
    )
    .await?;

    let mut multiplexed_connection = match reconnecting_connection.get_connection().await {
        Ok(multiplexed_connection) => multiplexed_connection,
        Err(err) => {
            reconnecting_connection.reconnect(ReconnectReason::ConnectionDropped);
            return Err(Box::new((reconnecting_connection, err)));
        }
    };

    // Skip INFO REPLICATION in read-only mode
    if skip_replication_check {
        return Ok((reconnecting_connection, None));
    }

    match multiplexed_connection
        .send_packed_command(redis::cmd("INFO").arg("REPLICATION"))
        .await
    {
        Ok(replication_status) => Ok((reconnecting_connection, Some(replication_status))),
        Err(err) => Err(Box::new((reconnecting_connection, err))),
    }
}

fn get_read_from(read_from: Option<super::ReadFrom>) -> ReadFrom {
    match read_from {
        Some(super::ReadFrom::Primary) => ReadFrom::Primary,
        Some(super::ReadFrom::PreferReplica) => ReadFrom::PreferReplica {
            latest_read_replica_index: Default::default(),
        },
        Some(super::ReadFrom::AllNodes) => ReadFrom::AllNodes {
            latest_read_node_index: Default::default(),
        },
        Some(super::ReadFrom::AZAffinity(az)) => ReadFrom::AZAffinity {
            client_az: az,
            last_read_replica_index: Default::default(),
        },
        Some(super::ReadFrom::AZAffinityReplicasAndPrimary(az)) => {
            ReadFrom::AZAffinityReplicasAndPrimary {
                client_az: az,
                latest_read_node_index: Default::default(),
            }
        }
        Some(super::ReadFrom::AZAffinityAllNodes(az)) => ReadFrom::AZAffinityAllNodes {
            client_az: az,
            latest_read_node_index: Default::default(),
        },
        None => ReadFrom::Primary,
    }
}

/// Runs `SELECT database_id` on each replica that has not applied `generation`, and
/// marks each replica that succeeds. Fails if any replica fails, or if `timeout`
/// elapses first (including the wait for the lock). Does nothing once a later
/// database change has started.
async fn select_database_on_replicas(
    inner: &DropWrapper,
    generation: u64,
    database_id: i64,
    timeout: Duration,
) -> RedisResult<()> {
    let sync = &inner.database_sync;
    let attempt = async {
        let _guard = sync.replica_select_lock.lock().await;
        if !sync.is_latest(generation) {
            return Ok(());
        }
        let mut select = redis::cmd("SELECT");
        select.arg(database_id);
        let select = &select;
        let stale_replicas = inner.nodes.iter().enumerate().filter(|(index, _)| {
            *index != inner.primary_index && !sync.node_has_applied(*index, generation)
        });
        future::join_all(stale_replicas.map(|(index, node)| async move {
            let Some(mut connection) = node.try_get_connection().await else {
                return Err(RedisError::from((
                    redis::ErrorKind::AllConnectionsUnavailable,
                    "replica is reconnecting",
                )));
            };
            match connection.send_packed_command(select).await {
                Ok(_) => {
                    sync.mark_node_applied(index, generation);
                    Ok(())
                }
                Err(err) => {
                    if err.is_unrecoverable_error() {
                        node.reconnect(ReconnectReason::ConnectionDropped);
                    }
                    Err(err)
                }
            }
        }))
        .await
        .into_iter()
        .collect::<RedisResult<()>>()
    };
    tokio::time::timeout(timeout, attempt)
        .await
        .unwrap_or_else(|_| Err(std::io::Error::from(std::io::ErrorKind::TimedOut).into()))
}

/// Starts the replica sync task, or wakes it if it is already running.
fn start_replica_sync(inner: &Arc<DropWrapper>, timeout: Duration) {
    let sync = &inner.database_sync;
    if sync.sync_task_running.swap(true, Ordering::AcqRel) {
        sync.database_changed.notify_one();
        return;
    }
    task::spawn(sync_replica_databases(
        Arc::downgrade(inner),
        sync.database_changed.clone(),
        timeout,
    ));
}

/// Brings every replica to the latest database, then exits. Failed attempts are
/// retried with exponential backoff, or at once when the database changes again.
/// Holds only a weak reference while waiting, and stops once the client is dropped.
async fn sync_replica_databases(
    inner: Weak<DropWrapper>,
    database_changed: Arc<Notify>,
    timeout: Duration,
) {
    let mut retry_delay = REPLICA_SYNC_MIN_RETRY_DELAY;
    loop {
        let Some(client) = inner.upgrade() else {
            return;
        };
        let sync = &client.database_sync;
        let generation = sync.generation.load(Ordering::Acquire);
        let database_id = sync.database_id.load(Ordering::Acquire);
        match select_database_on_replicas(&client, generation, database_id, timeout).await {
            Ok(()) if sync.is_latest(generation) => {
                sync.sync_task_running.store(false, Ordering::Release);
                // A change made before the store above saw this task running and
                // only woke it, so keep going unless another task has taken over.
                if sync.is_latest(generation) || sync.sync_task_running.swap(true, Ordering::AcqRel)
                {
                    return;
                }
                retry_delay = REPLICA_SYNC_MIN_RETRY_DELAY;
                continue;
            }
            // A later change started during the attempt; sync it now.
            Ok(()) => {
                retry_delay = REPLICA_SYNC_MIN_RETRY_DELAY;
                continue;
            }
            Err(err) => log_debug(
                "replica database sync",
                format!(
                    "selecting database {database_id} on replicas failed, retrying in {retry_delay:?}: {err}"
                ),
            ),
        }
        drop(client);
        tokio::select! {
            () = tokio::time::sleep(retry_delay) => {
                retry_delay = (retry_delay * 2).min(REPLICA_SYNC_MAX_RETRY_DELAY);
            }
            () = database_changed.notified() => retry_delay = REPLICA_SYNC_MIN_RETRY_DELAY,
        }
    }
}

fn is_readonly_nonatomic_pipeline(pipeline: &redis::Pipeline) -> bool {
    !pipeline.is_atomic()
        && pipeline.cmd_iter().all(|command| {
            let Some(command_name) = Routable::command(command.as_ref()) else {
                return false;
            };
            is_readonly_cmd(command_name.as_slice())
                && !requires_primary_pipeline_routing(command.as_ref(), command_name.as_slice())
        })
}

/// Whether a command that `is_readonly_cmd` accepts must still keep its pipeline
/// on the primary. `is_readonly_cmd` lists every command a replica accepts, including
/// administrative commands and commands that change connection state.
fn requires_primary_pipeline_routing(command: &redis::Cmd, command_name: &[u8]) -> bool {
    // A single-command request sends these to every node (`SELECT`, `AUTH`,
    // `CLIENT SETNAME`, `RESET`, `CONFIG SET`, ...); a pipeline reaches one node only.
    RoutingInfo::is_all_nodes(command_name)
        // Other commands that change connection state.
        || matches!(
            command_name,
            b"CLIENT CACHING"
                | b"CLIENT CAPA"
                | b"CLIENT NO-EVICT"
                | b"CLIENT NO-TOUCH"
                | b"CLIENT REPLY"
                | b"CLIENT TRACKING"
                | b"HELLO"
                | b"PSUBSCRIBE"
                | b"PUNSUBSCRIBE"
                | b"SCRIPT DEBUG"
                | b"SSUBSCRIBE"
                | b"SUBSCRIBE"
                | b"SUNSUBSCRIBE"
                | b"UNSUBSCRIBE"
        )
        // Commands that change server state.
        || matches!(
            command_name,
            b"ACL LOAD"
                | b"BGREWRITEAOF"
                | b"BGSAVE"
                | b"CLIENT PAUSE"
                | b"CLIENT UNBLOCK"
                | b"CLIENT UNPAUSE"
                | b"CLUSTER FAILOVER"
                | b"CLUSTER REPLICATE"
                | b"CLUSTER SAVECONFIG"
                | b"LATENCY RESET"
                | b"MEMORY PURGE"
                | b"MODULE LOAD"
                | b"MODULE LOADEX"
                | b"MODULE UNLOAD"
                | b"PUBLISH"
                | b"REPLICAOF"
                | b"SAVE"
                | b"SHUTDOWN"
                | b"SPUBLISH"
        )
        // Cursor commands: the next call must reach the same node, and a
        // round-robin strategy may pick another replica.
        || matches!(command_name, b"SCAN" | b"HSCAN" | b"SSCAN" | b"ZSCAN")
        // `FT.AGGREGATE ... WITHCURSOR`, which cluster routing sends to a primary.
        || (command_name == b"FT.AGGREGATE"
            && matches!(
                RoutingInfo::for_routable(command),
                Some(RoutingInfo::SingleNode(
                    SingleNodeRoutingInfo::SpecificNode(route)
                )) if route.slot_addr() == SlotAddr::Master
            ))
}

/// Parse replica addresses from a primary's INFO REPLICATION response.
/// Format: slave0:ip=10.1.35.66,port=6379,state=online,offset=144849,lag=0
fn parse_replica_addresses(replication_info: &str) -> Vec<NodeAddress> {
    let mut replicas = Vec::new();
    for line in replication_info.lines() {
        let line = line.trim();
        if !line.starts_with("slave") || !line.contains(":ip=") {
            continue;
        }
        let after_colon = match line.split_once(':') {
            Some((_, rest)) => rest,
            None => continue,
        };
        let mut host = None;
        let mut port = None;
        for part in after_colon.split(',') {
            if let Some(val) = part.strip_prefix("ip=") {
                host = Some(val.to_string());
            } else if let Some(val) = part.strip_prefix("port=") {
                port = val.parse::<u16>().ok();
            }
        }
        if let (Some(h), Some(p)) = (host, port) {
            replicas.push(NodeAddress {
                host: h,
                port: p,
                unix_socket_path: None,
            });
        }
    }
    replicas
}

/// Parse primary address from a replica's INFO REPLICATION response.
fn parse_primary_address(replication_info: &str) -> Option<NodeAddress> {
    let mut host = None;
    let mut port = None;
    for line in replication_info.lines() {
        let line = line.trim();
        if let Some(val) = line.strip_prefix("master_host:") {
            host = Some(val.to_string());
        } else if let Some(val) = line.strip_prefix("master_port:") {
            port = val.parse::<u16>().ok();
        }
    }
    match (host, port) {
        (Some(h), Some(p)) => Some(NodeAddress {
            host: h,
            port: p,
            unix_socket_path: None,
        }),
        _ => None,
    }
}

/// Check if replication info indicates a primary node.
fn is_primary_role(replication_info: &str) -> bool {
    replication_info.lines().any(|l| l.trim() == "role:master")
}

/// Check if an address is already in a list (by endpoint string comparison).
fn address_is_known(addr: &NodeAddress, existing: &[String], discovered: &[NodeAddress]) -> bool {
    let key = addr.endpoint();
    existing.contains(&key) || discovered.iter().any(|a| a.endpoint() == key)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn readonly_pipeline_requires_all_commands_to_be_readonly_and_non_atomic() {
        let mut readonly = redis::Pipeline::new();
        readonly.get("key-1").get("key-2");
        assert!(is_readonly_nonatomic_pipeline(&readonly));

        let mut mixed = redis::Pipeline::new();
        mixed.get("key").set("key", "value");
        assert!(!is_readonly_nonatomic_pipeline(&mixed));

        // Each command passes `is_readonly_cmd`, so only the primary-routing check
        // keeps it off replicas. Covers the explicit lists and a sample of the
        // all-nodes commands.
        for command in [
            // All-nodes commands.
            "ACL SETUSER",
            "AUTH",
            "CLIENT SETINFO",
            "CLIENT SETNAME",
            "CONFIG SET",
            "RESET",
            "SCRIPT LOAD",
            "SELECT",
            "SLOWLOG RESET",
            // Connection state.
            "CLIENT CACHING",
            "CLIENT CAPA",
            "CLIENT NO-EVICT",
            "CLIENT NO-TOUCH",
            "CLIENT REPLY",
            "CLIENT TRACKING",
            "HELLO",
            "PSUBSCRIBE",
            "PUNSUBSCRIBE",
            "SCRIPT DEBUG",
            "SSUBSCRIBE",
            "SUBSCRIBE",
            "SUNSUBSCRIBE",
            "UNSUBSCRIBE",
            // Server state.
            "ACL LOAD",
            "BGREWRITEAOF",
            "BGSAVE",
            "CLIENT PAUSE",
            "CLIENT UNBLOCK",
            "CLIENT UNPAUSE",
            "CLUSTER FAILOVER",
            "CLUSTER REPLICATE",
            "CLUSTER SAVECONFIG",
            "LATENCY RESET",
            "MEMORY PURGE",
            "MODULE LOAD",
            "MODULE LOADEX",
            "MODULE UNLOAD",
            "PUBLISH",
            "REPLICAOF",
            "SAVE",
            "SHUTDOWN",
            "SPUBLISH",
            // Cursors.
            "HSCAN",
            "SCAN",
            "SSCAN",
            "ZSCAN",
        ] {
            assert!(is_readonly_cmd(command.as_bytes()), "{command}");
            let mut words = command.split(' ');
            let mut primary_only = redis::Pipeline::new();
            primary_only
                .cmd(words.next().unwrap())
                .arg(words.collect::<Vec<_>>());
            primary_only.get("key");
            assert!(!is_readonly_nonatomic_pipeline(&primary_only), "{command}");
        }

        let mut aggregate = redis::Pipeline::new();
        aggregate.cmd("FT.AGGREGATE").arg("index").arg("*");
        assert!(is_readonly_nonatomic_pipeline(&aggregate));
        aggregate.arg("WITHCURSOR");
        assert!(!is_readonly_nonatomic_pipeline(&aggregate));

        readonly.atomic();
        assert!(!is_readonly_nonatomic_pipeline(&readonly));
    }

    #[test]
    fn database_sync_tracks_replicas_per_generation() {
        let sync = DatabaseSync::new(3);
        assert!((0..3).all(|index| sync.node_is_current(index)));

        let first = sync.next_generation();
        assert!(sync.is_latest(first));
        assert!((0..3).all(|index| !sync.node_is_current(index)));

        sync.mark_node_applied(1, first);
        assert!(sync.node_is_current(1));
        assert!(!sync.node_is_current(2));

        // A later change makes every replica stale again, including one that
        // applied the earlier change.
        let second = sync.next_generation();
        assert!(!sync.is_latest(first));
        assert!(!sync.node_is_current(1));
        assert!(sync.node_has_applied(1, first));

        // A late success for the earlier change cannot move a node backwards.
        sync.mark_node_applied(2, second);
        sync.mark_node_applied(2, first);
        assert!(sync.node_is_current(2));

        sync.mark_all_applied(sync.next_generation());
        assert!((0..3).all(|index| sync.node_is_current(index)));
    }

    #[test]
    fn test_parse_replica_addresses_basic() {
        let info = "role:master\nconnected_slaves:2\nslave0:ip=10.0.0.1,port=6379,state=online,offset=100,lag=0\nslave1:ip=10.0.0.2,port=6380,state=online,offset=100,lag=1\n";
        let replicas = parse_replica_addresses(info);
        assert_eq!(replicas.len(), 2);
        assert_eq!(replicas[0].host, "10.0.0.1");
        assert_eq!(replicas[0].port, 6379);
        assert_eq!(replicas[1].host, "10.0.0.2");
        assert_eq!(replicas[1].port, 6380);
    }

    #[test]
    fn test_parse_replica_addresses_with_type_field() {
        let info = "slave0:ip=10.0.0.1,port=6379,state=online,offset=100,lag=0,type=replica\n";
        let replicas = parse_replica_addresses(info);
        assert_eq!(replicas.len(), 1);
        assert_eq!(replicas[0].host, "10.0.0.1");
        assert_eq!(replicas[0].port, 6379);
    }

    #[test]
    fn test_parse_replica_addresses_empty() {
        let info = "role:master\nconnected_slaves:0\n";
        let replicas = parse_replica_addresses(info);
        assert!(replicas.is_empty());
    }

    #[test]
    fn test_parse_primary_address() {
        let info = "role:slave\nmaster_host:10.0.0.1\nmaster_port:6379\nmaster_link_status:up\n";
        let primary = parse_primary_address(info);
        assert!(primary.is_some());
        let addr = primary.unwrap();
        assert_eq!(addr.host, "10.0.0.1");
        assert_eq!(addr.port, 6379);
    }

    #[test]
    fn test_parse_primary_address_missing() {
        let info = "role:master\nconnected_slaves:0\n";
        assert!(parse_primary_address(info).is_none());
    }

    #[test]
    fn test_is_primary_role() {
        assert!(is_primary_role("role:master\nconnected_slaves:0\n"));
        assert!(!is_primary_role("role:slave\nmaster_host:10.0.0.1\n"));
    }

    #[test]
    fn test_parse_replica_addresses_real_world() {
        let info = "# Replication\nrole:master\nconnected_slaves:2\nslave0:ip=YYY.YYY.YYY.YYY,port=6379,state=online,offset=1156932007140,lag=0,type=replica\nslave1:ip=ZZZ.ZZZ.ZZZ.ZZZ,port=6379,state=online,offset=1156932007140,lag=1,type=replica\nmaster_replid:070023374a903a57e473b41ff2fbcc2fcd06a01a\n";
        let replicas = parse_replica_addresses(info);
        assert_eq!(replicas.len(), 2);
        assert_eq!(replicas[0].host, "YYY.YYY.YYY.YYY");
        assert_eq!(replicas[1].host, "ZZZ.ZZZ.ZZZ.ZZZ");
    }

    #[test]
    fn test_parse_primary_address_real_world() {
        let info = "# Replication\nrole:slave\nmaster_host:XXX.XXX.XXX.XXX\nmaster_port:6379\nmaster_link_status:up\nmaster_last_io_seconds_ago:0\n";
        let primary = parse_primary_address(info).unwrap();
        assert_eq!(primary.host, "XXX.XXX.XXX.XXX");
        assert_eq!(primary.port, 6379);
    }
}
