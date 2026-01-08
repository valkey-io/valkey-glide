// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use super::{ClusterMode, TestConfiguration, create_connection_request};
use futures::FutureExt;
use futures::future::{BoxFuture, join_all};
use glide_core::client::Client;
#[cfg(not(feature = "mock-pubsub"))]
use glide_core::client::ClientWrapper;
#[cfg(not(feature = "mock-pubsub"))]
use glide_core::pubsub::synchronizer::GlidePubSubSynchronizer;
#[cfg(not(feature = "mock-pubsub"))]
use glide_core::pubsub::{PubSubSubscriptionInfo, PubSubSynchronizer, create_pubsub_synchronizer};
use once_cell::sync::Lazy;
#[cfg(not(feature = "mock-pubsub"))]
use redis::PubSubSubscriptionKind;
use redis::{ConnectionAddr, RedisConnectionInfo};
use redis::{
    Value,
    cluster_async::ClusterConnection,
    cluster_routing::{RoutingInfo, SingleNodeRoutingInfo},
};
use serde::Deserialize;
#[cfg(not(feature = "mock-pubsub"))]
use std::collections::HashMap;
use std::collections::HashSet;
use std::process::Command;
#[cfg(not(feature = "mock-pubsub"))]
use std::sync::Arc;
use std::sync::Mutex;
#[cfg(not(feature = "mock-pubsub"))]
use std::sync::Weak;
use std::time::Duration;
#[cfg(not(feature = "mock-pubsub"))]
use tokio::sync::{RwLock as TokioRwLock, mpsc};
use which::which;
// Code copied from redis-rs

pub(crate) const SHORT_CLUSTER_TEST_TIMEOUT: Duration = Duration::from_millis(50_000);
pub(crate) const LONG_CLUSTER_TEST_TIMEOUT: Duration = Duration::from_millis(60_000);

enum ClusterType {
    Tcp,
    TcpTls,
}

#[derive(Deserialize, Clone, Debug)]
struct ValkeyServerInfo {
    host: String,
    port: u32,
    pid: u32,
    is_primary: bool,
}

impl ClusterType {
    fn build_addr(use_tls: bool, host: &str, port: u16) -> redis::ConnectionAddr {
        if use_tls {
            redis::ConnectionAddr::TcpTls {
                host: host.to_string(),
                port,
                insecure: true,
                tls_params: None,
            }
        } else {
            redis::ConnectionAddr::Tcp(host.to_string(), port)
        }
    }
}

pub struct RedisCluster {
    cluster_folder: String,
    use_tls: bool,
    password: Option<String>,
    servers: Vec<ValkeyServerInfo>,
}

impl Drop for RedisCluster {
    fn drop(&mut self) {
        let pids: Vec<String> = self
            .servers
            .iter()
            .map(|server| format!("{}", server.pid))
            .collect();
        let pids = pids.join(",");
        Self::execute_cluster_script(
            vec![
                "stop",
                "--cluster-folder",
                &self.cluster_folder,
                "--pids",
                &pids,
            ],
            self.use_tls,
            self.password.clone(),
            None,
        );
    }
}

type SharedCluster = Lazy<Mutex<Option<RedisCluster>>>;
static SHARED_CLUSTER: SharedCluster =
    Lazy::new(|| Mutex::new(Some(RedisCluster::new(false, &None, None, None))));

static SHARED_TLS_CLUSTER: SharedCluster =
    Lazy::new(|| Mutex::new(Some(RedisCluster::new(true, &None, None, None))));

static SHARED_CLUSTER_ADDRESSES: Lazy<Vec<ConnectionAddr>> = Lazy::new(|| {
    SHARED_CLUSTER
        .lock()
        .unwrap()
        .as_ref()
        .unwrap()
        .get_server_addresses()
});

static SHARED_TLS_CLUSTER_ADDRESSES: Lazy<Vec<ConnectionAddr>> = Lazy::new(|| {
    SHARED_TLS_CLUSTER
        .lock()
        .unwrap()
        .as_ref()
        .unwrap()
        .get_server_addresses()
});

pub fn get_shared_cluster_addresses(use_tls: bool) -> Vec<ConnectionAddr> {
    if use_tls {
        SHARED_TLS_CLUSTER_ADDRESSES.clone()
    } else {
        SHARED_CLUSTER_ADDRESSES.clone()
    }
}

#[ctor::dtor]
fn clean_shared_clusters() {
    if let Some(mutex) = SharedCluster::get(&SHARED_CLUSTER) {
        drop(mutex.lock().unwrap().take());
    }
    if let Some(mutex) = SharedCluster::get(&SHARED_TLS_CLUSTER) {
        drop(mutex.lock().unwrap().take());
    }
}

impl RedisCluster {
    pub fn new(
        use_tls: bool,
        conn_info: &Option<RedisConnectionInfo>,
        shards: Option<u16>,
        replicas: Option<u16>,
    ) -> RedisCluster {
        Self::new_with_tls_paths(use_tls, conn_info, shards, replicas, None)
    }

    pub fn new_with_tls(
        shards: u16,
        replicas: u16,
        tls_paths: Option<super::TlsFilePaths>,
    ) -> RedisCluster {
        Self::new_with_tls_paths(true, &None, Some(shards), Some(replicas), tls_paths)
    }

    fn new_with_tls_paths(
        use_tls: bool,
        conn_info: &Option<RedisConnectionInfo>,
        shards: Option<u16>,
        replicas: Option<u16>,
        tls_paths: Option<super::TlsFilePaths>,
    ) -> RedisCluster {
        let mut script_args = vec!["start", "--cluster-mode"];
        let shards_num: String;
        let replicas_num: String;
        if let Some(shards) = shards {
            shards_num = shards.to_string();
            script_args.push("-n");
            script_args.push(&shards_num);
        }
        if let Some(replicas) = replicas {
            replicas_num = replicas.to_string();
            script_args.push("-r");
            script_args.push(&replicas_num);
        }
        let (stdout, stderr) =
            Self::execute_cluster_script(script_args, use_tls, None, tls_paths.as_ref());
        let (cluster_folder, servers) = Self::parse_start_script_output(&stdout, &stderr);
        let mut password: Option<String> = None;
        if let Some(info) = conn_info {
            password.clone_from(&info.password);
        };
        RedisCluster {
            cluster_folder,
            use_tls,
            password,
            servers,
        }
    }

    fn value_after_prefix(prefix: &str, line: &str) -> Option<String> {
        if !line.starts_with(prefix) {
            return None;
        }
        Some(line[prefix.len()..].to_string())
    }

    fn parse_start_script_output(output: &str, _errors: &str) -> (String, Vec<ValkeyServerInfo>) {
        let prefixes = vec!["CLUSTER_FOLDER", "SERVERS_JSON"];
        let mut values = std::collections::HashMap::<String, String>::new();
        let lines: Vec<&str> = output.split('\n').map(|line| line.trim()).collect();
        for line in lines {
            for prefix in &prefixes {
                let prefix_with_shave = format!("{prefix}=");
                if line.starts_with(&prefix_with_shave) {
                    values.insert(
                        prefix.to_string(),
                        Self::value_after_prefix(&prefix_with_shave, line).unwrap_or_default(),
                    );
                }
            }
        }

        let cluster_folder = values.get("CLUSTER_FOLDER").unwrap();
        let cluster_nodes_json = values.get("SERVERS_JSON").unwrap();
        let servers: Vec<ValkeyServerInfo> = serde_json::from_str(cluster_nodes_json).unwrap();
        (cluster_folder.clone(), servers)
    }

    fn execute_cluster_script(
        args: Vec<&str>,
        use_tls: bool,
        password: Option<String>,
        tls_paths: Option<&super::TlsFilePaths>,
    ) -> (String, String) {
        let python_binary = which("python3").unwrap();
        let mut script_path = std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        script_path.push("../utils/cluster_manager.py");
        assert!(script_path.exists());

        // Helper to quote shell arguments
        fn shell_quote(s: &str) -> String {
            if s.contains(' ')
                || s.contains('\t')
                || s.contains('\n')
                || s.contains('"')
                || s.contains('$')
            {
                // Use single quotes and escape any single quotes in the string
                let escaped = s.replace("'", "'\"'\"'");
                format!("'{}'", escaped)
            } else {
                s.to_string()
            }
        }

        let mut cmd_parts = vec![
            shell_quote(&python_binary.to_string_lossy()),
            shell_quote(&script_path.to_string_lossy()),
        ];

        if use_tls {
            cmd_parts.push("--tls".to_string());
        }

        if let Some(pass) = password {
            cmd_parts.push("--auth".to_string());
            cmd_parts.push(shell_quote(&pass));
        }

        for arg in args {
            cmd_parts.push(arg.to_string());
        }

        if let Some(paths) = tls_paths {
            cmd_parts.push("--tls-cert-file".to_string());
            cmd_parts.push(shell_quote(&paths.redis_crt.to_string_lossy()));
            cmd_parts.push("--tls-key-file".to_string());
            cmd_parts.push(shell_quote(&paths.redis_key.to_string_lossy()));
            cmd_parts.push("--tls-ca-cert-file".to_string());
            cmd_parts.push(shell_quote(&paths.ca_crt.to_string_lossy()));
        }

        let cmd = cmd_parts.join(" ");

        let output = if cfg!(target_os = "windows") {
            Command::new("cmd")
                .args(["/C", &cmd])
                .output()
                .expect("failed to execute process")
        } else {
            Command::new("sh")
                .arg("-c")
                .arg(&cmd)
                .output()
                .expect("failed to execute process")
        };
        let parsed_stdout = std::str::from_utf8(&output.stdout)
            .unwrap()
            .trim()
            .to_string();
        let parsed_stderr = std::str::from_utf8(&output.stderr)
            .unwrap()
            .trim()
            .to_string();
        (parsed_stdout, parsed_stderr)
    }

    pub fn get_server_addresses(&self) -> Vec<ConnectionAddr> {
        self.servers
            .iter()
            .map(|server| ClusterType::build_addr(self.use_tls, &server.host, server.port as u16))
            .collect()
    }
}

pub struct ClusterTestBasics {
    pub cluster: Option<RedisCluster>,
    pub client: Client,
}

async fn setup_acl_for_cluster(
    addresses: &[ConnectionAddr],
    connection_info: &RedisConnectionInfo,
) {
    let ops: Vec<BoxFuture<()>> = addresses
        .iter()
        .map(|addr| (async move { super::setup_acl(addr, connection_info).await }).boxed())
        .collect();
    join_all(ops).await;
}

pub async fn create_cluster_client(
    cluster: Option<&RedisCluster>,
    mut configuration: TestConfiguration,
) -> Client {
    let addresses = if !configuration.shared_server {
        cluster.unwrap().get_server_addresses()
    } else {
        get_shared_cluster_addresses(configuration.use_tls)
    };

    if let Some(redis_connection_info) = &configuration.connection_info
        && redis_connection_info.password.is_some()
    {
        assert!(!configuration.shared_server);
        setup_acl_for_cluster(&addresses, redis_connection_info).await;
    }
    configuration.cluster_mode = ClusterMode::Enabled;
    configuration.request_timeout = configuration.request_timeout.or(Some(10000));
    let connection_request = create_connection_request(&addresses, &configuration);

    Client::new(connection_request.into(), None).await.unwrap()
}

pub async fn setup_test_basics_internal(configuration: TestConfiguration) -> ClusterTestBasics {
    let cluster = if !configuration.shared_server {
        Some(RedisCluster::new(
            configuration.use_tls,
            &configuration.connection_info,
            None,
            None,
        ))
    } else {
        None
    };
    let client = create_cluster_client(cluster.as_ref(), configuration).await;
    ClusterTestBasics { cluster, client }
}

pub async fn setup_default_cluster() -> RedisCluster {
    let test_config = TestConfiguration::default();
    RedisCluster::new(false, &test_config.connection_info, None, None)
}

pub async fn setup_default_client(cluster: &RedisCluster) -> Client {
    let test_config = TestConfiguration::default();
    create_cluster_client(Some(cluster), test_config).await
}

pub async fn setup_cluster_with_replicas(
    configuration: TestConfiguration,
    replicas_num: u16,
    primaries_num: u16,
) -> ClusterTestBasics {
    let cluster = if !configuration.shared_server {
        Some(RedisCluster::new(
            configuration.use_tls,
            &configuration.connection_info,
            Some(primaries_num),
            Some(replicas_num),
        ))
    } else {
        None
    };
    let client = create_cluster_client(cluster.as_ref(), configuration).await;
    ClusterTestBasics { cluster, client }
}

pub async fn setup_test_basics(use_tls: bool) -> ClusterTestBasics {
    setup_test_basics_internal(TestConfiguration {
        use_tls,
        ..Default::default()
    })
    .await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_start_script_output() {
        let script_output = r#"
INFO:root:## Executing cluster_manager.py with the following args:
  Namespace(host='127.0.0.1', tls=False, auth=None, log='info', logfile=None, action='start', cluster_mode=True, folder_path='/Users/user/glide-for-redis/utils/clusters', ports=None, shard_count=3, replica_count=2, prefix='redis-cluster', load_module=None)
INFO:root:2024-11-05 16:05:44.024796+00:00 Starting script for cluster /Users/user/glide-for-redis/utils/clusters/redis-cluster-2024-11-05T16-05-44Z-2bz4YS
LOG_FILE=/Users/user/glide-for-redis/utils/cluspters/redis-cluster-2024-11-05T16-05-44Z-2bz4YS/cluster_manager.log
SERVERS_JSON=[{"host": "127.0.0.1", "port": 39163, "pid": 59428, "is_primary": true}, {"host": "127.0.0.1", "port": 23178, "pid": 59436, "is_primary": true}, {"host": "127.0.0.1", "port": 25186, "pid": 59453, "is_primary": true}, {"host": "127.0.0.1", "port": 52500, "pid": 59432, "is_primary": false}, {"host": "127.0.0.1", "port": 48252, "pid": 59461, "is_primary": false}, {"host": "127.0.0.1", "port": 19544, "pid": 59444, "is_primary": false}, {"host": "127.0.0.1", "port": 37455, "pid": 59440, "is_primary": false}, {"host": "127.0.0.1", "port": 9282, "pid": 59449, "is_primary": false}, {"host": "127.0.0.1", "port": 19843, "pid": 59457, "is_primary": false}]
INFO:root:Created Cluster Redis in 24.8926 seconds
CLUSTER_FOLDER=/Users/user/glide-for-redis/utils/clusters/redis-cluster-2024-11-05T16-05-44Z-2bz4YS
CLUSTER_NODES=127.0.0.1:39163,127.0.0.1:23178,127.0.0.1:25186,127.0.0.1:52500,127.0.0.1:48252,127.0.0.1:19544,127.0.0.1:37455,127.0.0.1:9282,127.0.0.1:19843
        "#;
        let (folder, servers) = RedisCluster::parse_start_script_output(script_output, "");
        assert_eq!(servers.len(), 9);
        assert_eq!(
            folder,
            "/Users/user/glide-for-redis/utils/clusters/redis-cluster-2024-11-05T16-05-44Z-2bz4YS"
        );

        let server_0 = servers.first().unwrap();
        assert_eq!(server_0.pid, 59428);
        assert_eq!(server_0.port, 39163);
        assert_eq!(server_0.host, "127.0.0.1");
        assert!(server_0.is_primary);
    }
}

/// Holds all components needed for pubsub topology test setup.
/// The `_client_holder` keeps the Arc alive so the weak reference in the synchronizer remains valid.
#[cfg(not(feature = "mock-pubsub"))]
pub struct PubSubTestSetup {
    pub connection: ClusterConnection,
    pub synchronizer: Arc<dyn PubSubSynchronizer>,
    pub _client_holder: Arc<TokioRwLock<ClientWrapper>>,
}

#[cfg(not(feature = "mock-pubsub"))]
impl PubSubTestSetup {
    /// Creates a test setup with a ClusterConnection configured for fast slot refresh
    /// and a properly initialized synchronizer.
    pub async fn new(addresses: &[ConnectionAddr]) -> Self {
        Self::new_with_interval(addresses, None).await
    }

    /// Creates a test setup with a custom reconciliation interval.
    pub async fn new_with_interval(
        addresses: &[ConnectionAddr],
        reconciliation_interval: Option<Duration>,
    ) -> Self {
        let (push_tx, _push_rx) = mpsc::unbounded_channel();

        // Create synchronizer with empty weak pointer initially
        let synchronizer = create_pubsub_synchronizer(
            Some(push_tx),
            None,
            true,
            Weak::new(),
            reconciliation_interval,
            Duration::from_millis(1000),
        )
        .await;

        let initial_nodes: Vec<redis::ConnectionInfo> = addresses
            .iter()
            .map(|addr| redis::ConnectionInfo {
                addr: addr.clone(),
                redis: RedisConnectionInfo::default(),
            })
            .collect();

        let client = redis::cluster::ClusterClientBuilder::new(initial_nodes)
            .slots_refresh_rate_limit(Duration::from_millis(0), 0)
            .periodic_topology_checks(Duration::from_millis(500))
            .build()
            .expect("Failed to build cluster client for topology test");

        let connection = client
            .get_async_connection(None, Some(synchronizer.clone()))
            .await
            .expect("Failed to get async connection for topology test");

        // Create the real client wrapper
        let client_wrapper = ClientWrapper::Cluster {
            client: connection.clone(),
        };
        let client_arc = Arc::new(TokioRwLock::new(client_wrapper));

        // Now set the real client on the synchronizer
        synchronizer
            .as_any()
            .downcast_ref::<GlidePubSubSynchronizer>()
            .expect("Expected GlidePubSubSynchronizer")
            .set_internal_client(Arc::downgrade(&client_arc));

        Self {
            connection,
            synchronizer,
            _client_holder: client_arc,
        }
    }

    /// Get current subscriptions organized by address.
    /// Downcasts to concrete type to access internal state.
    pub fn get_subscriptions_by_address(&self) -> HashMap<String, PubSubSubscriptionInfo> {
        use glide_core::pubsub::synchronizer::GlidePubSubSynchronizer;

        self.synchronizer
            .as_any()
            .downcast_ref::<GlidePubSubSynchronizer>()
            .expect("Expected GlidePubSubSynchronizer")
            .get_current_subscriptions_by_address()
    }

    /// Check if server version is >= specified version
    pub async fn version_gte(&mut self, version: &str) -> bool {
        super::version_greater_or_equal(&mut self.connection, version).await
    }
}

/// Macro to skip test if server version is below minimum.
/// Returns early from the async block if version requirement not met.
#[macro_export]
macro_rules! skip_if_version_below {
    ($setup:expr, $version:expr) => {
        if !$setup.version_gte($version).await {
            logger_core::log_info(
                "test_pubsub",
                format!("Skipping test: requires server version >= {}", $version),
            );
            return;
        }
    };
}

/// Holds cluster topology information for tests.
pub struct ClusterTopology {
    pub nodes: Vec<ClusterNodeInfo>,
    pub primary_nodes: Vec<ClusterNodeInfo>,
    pub all_node_addresses: Vec<(String, u16)>,
}

/// Information about a single cluster node.
#[derive(Clone)]
pub struct ClusterNodeInfo {
    pub node_id: String,
    pub host: String,
    pub port: u16,
    pub is_primary: bool,
    pub primary_id: Option<String>,
    pub slot_ranges: Vec<(u16, u16)>,
}

impl ClusterTopology {
    /// Get cluster topology from a connection.
    pub async fn from_connection(connection: &mut ClusterConnection) -> Self {
        let nodes_output = connection
            .route_command(
                redis::cmd("CLUSTER").arg("NODES"),
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random),
            )
            .await
            .expect("Failed to get CLUSTER NODES");

        let nodes_str = match nodes_output {
            Value::BulkString(b) => String::from_utf8_lossy(&b).to_string(),
            Value::VerbatimString { text, .. } => text,
            _ => panic!("Unexpected CLUSTER NODES response type"),
        };

        let nodes = Self::parse_cluster_nodes(&nodes_str);
        let primary_nodes: Vec<_> = nodes.iter().filter(|n| n.is_primary).cloned().collect();
        let all_node_addresses: Vec<(String, u16)> =
            nodes.iter().map(|n| (n.host.clone(), n.port)).collect();

        Self {
            nodes,
            primary_nodes,
            all_node_addresses,
        }
    }

    /// Parse CLUSTER NODES output to extract node information.
    fn parse_cluster_nodes(output: &str) -> Vec<ClusterNodeInfo> {
        output
            .lines()
            .filter_map(|line| {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() < 8 {
                    return None;
                }

                let node_id = parts[0].to_string();
                let addr_part = parts[1].split('@').next()?;
                let (host, port_str) = addr_part.rsplit_once(':')?;
                let port: u16 = port_str.parse().ok()?;
                let is_primary = parts[2].contains("master");

                let primary_id = if parts[3] != "-" {
                    Some(parts[3].to_string())
                } else {
                    None
                };

                let slot_ranges: Vec<(u16, u16)> = parts[8..]
                    .iter()
                    .filter_map(|s| {
                        if s.starts_with('[') {
                            return None;
                        }
                        if s.contains('-') {
                            let mut range = s.split('-');
                            let start: u16 = range.next()?.parse().ok()?;
                            let end: u16 = range.next()?.parse().ok()?;
                            Some((start, end))
                        } else {
                            let slot: u16 = s.parse().ok()?;
                            Some((slot, slot))
                        }
                    })
                    .collect();

                Some(ClusterNodeInfo {
                    node_id,
                    host: host.to_string(),
                    port,
                    is_primary,
                    primary_id,
                    slot_ranges,
                })
            })
            .collect()
    }

    /// Find which node owns a specific slot.
    pub fn find_slot_owner(&self, slot: u16) -> Option<&ClusterNodeInfo> {
        self.nodes.iter().find(|node| {
            node.is_primary
                && node
                    .slot_ranges
                    .iter()
                    .any(|(start, end)| slot >= *start && slot <= *end)
        })
    }

    /// Find a different primary node than the one with the given node_id.
    pub fn find_different_primary(&self, owner_node_id: &str) -> Option<&ClusterNodeInfo> {
        self.primary_nodes
            .iter()
            .find(|node| node.node_id != owner_node_id)
    }

    /// Find replicas of a given primary node.
    pub fn find_replicas_of(&self, primary_node_id: &str) -> Vec<&ClusterNodeInfo> {
        self.nodes
            .iter()
            .filter(|n| !n.is_primary && n.primary_id.as_deref() == Some(primary_node_id))
            .collect()
    }

    /// Find a primary node that has at least one replica.
    pub fn find_primary_with_replica(&self) -> Option<&ClusterNodeInfo> {
        self.primary_nodes
            .iter()
            .find(|primary| !self.find_replicas_of(&primary.node_id).is_empty())
    }
}

/// Migrate a slot from one node to another using CLUSTER SETSLOT commands.
pub async fn migrate_slot(
    connection: &mut ClusterConnection,
    slot: u16,
    to_node_id: &str,
    all_node_addresses: &[(String, u16)],
) {
    for (host, port) in all_node_addresses {
        let mut cmd = redis::cmd("CLUSTER");
        cmd.arg("SETSLOT").arg(slot).arg("NODE").arg(to_node_id);

        let routing = RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
            host: host.clone(),
            port: *port,
        });

        match connection.route_command(&cmd, routing).await {
            Ok(_) => {
                logger_core::log_debug(
                    "migrate_slot",
                    format!(
                        "CLUSTER SETSLOT {} NODE {} on {}:{} succeeded",
                        slot, to_node_id, host, port
                    ),
                );
            }
            Err(e) => {
                logger_core::log_warn(
                    "migrate_slot",
                    format!("CLUSTER SETSLOT on {}:{} failed: {:?}", host, port, e),
                );
            }
        }
    }
}

/// Migrate a channel's slot to a different node than its current owner.
/// Returns Some(target_node_id) if migration was performed, None if not possible.
pub async fn migrate_channel_to_different_node(
    connection: &mut ClusterConnection,
    topology: &ClusterTopology,
    slot: u16,
) -> Option<String> {
    let Some(owner) = topology.find_slot_owner(slot) else {
        logger_core::log_warn(
            "migrate_channel",
            format!("No owner found for slot {}", slot),
        );
        return None;
    };

    let Some(target) = topology.find_different_primary(&owner.node_id) else {
        logger_core::log_warn(
            "migrate_channel",
            format!(
                "No different primary found for slot {} (owner: {})",
                slot, owner.node_id
            ),
        );
        return None;
    };

    logger_core::log_info(
        "migrate_channel",
        format!(
            "Migrating slot {} from {} to {}",
            slot, owner.node_id, target.node_id
        ),
    );

    migrate_slot(
        connection,
        slot,
        &target.node_id,
        &topology.all_node_addresses,
    )
    .await;

    Some(target.node_id.clone())
}

/// Migrate multiple channels to different nodes than their current owners.
/// Returns count of successful migrations.
pub async fn migrate_channels_to_different_nodes(
    connection: &mut ClusterConnection,
    topology: &ClusterTopology,
    channels_with_slots: &[(Vec<u8>, u16)],
    delay_between_migrations: Duration,
) -> usize {
    let mut migrated_count = 0;

    for (channel, slot) in channels_with_slots {
        match migrate_channel_to_different_node(connection, topology, *slot).await {
            Some(_) => {
                migrated_count += 1;
                logger_core::log_debug(
                    "migrate_channels",
                    format!(
                        "Successfully migrated slot {} for channel {:?}",
                        slot,
                        String::from_utf8_lossy(channel)
                    ),
                );
            }
            None => {
                logger_core::log_debug(
                    "migrate_channels",
                    format!("Skipped migration for slot {}", slot),
                );
            }
        }

        if delay_between_migrations > Duration::ZERO {
            tokio::time::sleep(delay_between_migrations).await;
        }
    }

    migrated_count
}

/// Trigger a failover on a replica node.
/// Returns true if failover was initiated successfully.
pub async fn trigger_failover(
    connection: &mut ClusterConnection,
    replica: &ClusterNodeInfo,
) -> bool {
    let cmd = redis::cmd("CLUSTER").arg("FAILOVER").to_owned();

    let routing = RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
        host: replica.host.clone(),
        port: replica.port,
    });

    match connection.route_command(&cmd, routing).await {
        Ok(_) => {
            logger_core::log_info(
                "trigger_failover",
                format!(
                    "CLUSTER FAILOVER initiated on {}:{}",
                    replica.host, replica.port
                ),
            );
            true
        }
        Err(e) => {
            logger_core::log_warn(
                "trigger_failover",
                format!(
                    "CLUSTER FAILOVER on {}:{} failed: {:?}",
                    replica.host, replica.port, e
                ),
            );
            false
        }
    }
}

/// Wait for a node to become primary.
pub async fn wait_for_node_to_become_primary(
    connection: &mut ClusterConnection,
    node_id: &str,
    timeout: Duration,
) -> bool {
    let start = std::time::Instant::now();

    while start.elapsed() < timeout {
        let topology = ClusterTopology::from_connection(connection).await;

        if let Some(node) = topology.nodes.iter().find(|n| n.node_id == node_id)
            && node.is_primary
        {
            return true;
        }

        tokio::time::sleep(Duration::from_millis(200)).await;
    }

    false
}

/// Wait for subscription state to match expected channels.
#[cfg(not(feature = "mock-pubsub"))]
pub async fn wait_for_pubsub_state(
    synchronizer: &Arc<dyn PubSubSynchronizer>,
    kind: PubSubSubscriptionKind,
    expected_channels: &HashSet<Vec<u8>>,
    check_actual: bool,
    timeout: Duration,
) -> bool {
    let start = std::time::Instant::now();
    while start.elapsed() < timeout {
        let (desired, actual) = synchronizer.get_subscription_state();
        let state_to_check = if check_actual { &actual } else { &desired };

        if let Some(channels) = state_to_check.get(&kind) {
            if channels == expected_channels {
                return true;
            }
        } else if expected_channels.is_empty() {
            return true;
        }

        tokio::time::sleep(Duration::from_millis(100)).await;
    }
    false
}

/// Generate test channel/pattern names with guaranteed different slots.
/// Returns Vec of (bytes, slot) tuples.
/// If `is_pattern` is true, generates patterns ending with `*`.
/// Panics if unable to generate enough unique slots.
pub fn generate_test_subscriptions_different_slots(
    prefix: &str,
    count: usize,
    is_pattern: bool,
) -> Vec<(Vec<u8>, u16)> {
    let mut result = Vec::with_capacity(count);
    let mut used_slots = HashSet::with_capacity(count);
    let mut i = 0u64;

    while result.len() < count {
        let name = if is_pattern {
            format!("{{{}-{}}}*", prefix, i)
        } else {
            format!("{{{}-{}}}-channel", prefix, i)
        };
        let bytes = name.into_bytes();
        let slot = redis::cluster_topology::get_slot(&bytes);

        if !used_slots.contains(&slot) {
            used_slots.insert(slot);
            result.push((bytes, slot));
        }

        i += 1;

        if i > count as u64 * 1000 {
            panic!(
                "Unable to generate {} {} with unique slots after {} attempts",
                count,
                if is_pattern { "patterns" } else { "channels" },
                i
            );
        }
    }

    result
}

/// Subscribe to multiple channels and wait for subscriptions to be established.
#[cfg(not(feature = "mock-pubsub"))]
pub async fn subscribe_and_wait(
    synchronizer: &Arc<dyn PubSubSynchronizer>,
    channels: &[Vec<u8>],
    kind: PubSubSubscriptionKind,
    timeout: Duration,
) -> bool {
    let channels_set: HashSet<Vec<u8>> = channels.iter().cloned().collect();
    synchronizer.add_desired_subscriptions(channels_set.clone(), kind);
    wait_for_pubsub_state(synchronizer, kind, &channels_set, true, timeout).await
}

/// Find which address a channel is subscribed on.
/// Returns None if channel is not found in current subscriptions.
/// Panics if channel is found on multiple addresses (indicates a bug).
#[cfg(not(feature = "mock-pubsub"))]
pub fn find_subscription_address(
    subs_by_address: &HashMap<String, PubSubSubscriptionInfo>,
    channel: &[u8],
    kind: PubSubSubscriptionKind,
) -> Option<String> {
    let mut found_address: Option<String> = None;

    for (address, subs) in subs_by_address {
        if let Some(channels) = subs.get(&kind)
            && channels.contains(channel)
        {
            if let Some(ref existing) = found_address {
                panic!(
                    "Channel {:?} found on multiple addresses: {} and {}",
                    String::from_utf8_lossy(channel),
                    existing,
                    address
                );
            }
            found_address = Some(address.clone());
        }
    }
    found_address
}

/// Verify that subscriptions moved to different addresses after migration.
/// Returns (changed_count, unchanged_count, not_found_count).
#[cfg(not(feature = "mock-pubsub"))]
pub fn verify_subscription_addresses_changed(
    subs_before: &HashMap<String, PubSubSubscriptionInfo>,
    subs_after: &HashMap<String, PubSubSubscriptionInfo>,
    channels: &[Vec<u8>],
    kind: PubSubSubscriptionKind,
) -> (usize, usize, usize) {
    let mut changed = 0;
    let mut unchanged = 0;
    let mut not_found = 0;

    for channel in channels {
        let addr_before = find_subscription_address(subs_before, channel, kind);
        let addr_after = find_subscription_address(subs_after, channel, kind);

        match (addr_after, addr_before) {
            (Some(current), Some(previous)) => {
                if current != previous {
                    changed += 1;
                } else {
                    unchanged += 1;
                }
            }
            (None, _) => {
                not_found += 1;
            }
            (Some(_), None) => {
                changed += 1;
            }
        }
    }

    (changed, unchanged, not_found)
}
