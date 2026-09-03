// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster harness using `cluster_manager.py`.

use glide::{GlideClusterClient, GlideClusterClientConfiguration, ProtocolVersion, Route};
use std::process::{Command, Stdio};
use std::time::Duration;

/// Extract the first unsigned integer value for `"field"` in a JSON fragment,
/// e.g. `field_u64(r#""port": 6379,"#, "\"port\"") == Some(6379)`. A tiny
/// dependency-free parser sufficient for cluster_manager.py's `SERVERS_JSON`.
fn field_u64(fragment: &str, field: &str) -> Option<u64> {
    let idx = fragment.find(field)?;
    let after = &fragment[idx + field.len()..];
    let digits: String = after
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(|c| c.is_ascii_digit())
        .collect();
    digits.parse().ok()
}

const CLUSTER_MANAGER: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../utils/cluster_manager.py");

/// A cluster created with `cluster_manager.py`.
pub struct ClusterHarness {
    pub ports: Vec<u16>,
    /// The `--cluster-folder` used to stop the cluster on drop.
    folder: String,
    /// Primary node ports (the seed is `ports[0]`, always a primary).
    pub primary_ports: Vec<u16>,
    /// Replica node ports.
    pub replica_ports: Vec<u16>,
}

impl ClusterHarness {
    /// Start a 3-primary cluster (one replica each) using `cluster_manager.py`.
    /// Panics if the cluster cannot be created.
    pub fn start() -> ClusterHarness {
        Self::start_via_cluster_manager(3, 1)
            .expect("failed to start a cluster with `cluster_manager.py`.")
    }

    /// Starts a cluster with the specified number of shards and replicas using `cluster_manager.py`.
    /// Returns `None` if the script fails.
    fn start_via_cluster_manager(shards: usize, replicas: usize) -> Option<ClusterHarness> {
        let out = Command::new("python3")
            .args([
                CLUSTER_MANAGER,
                "start",
                "--cluster-mode",
                "-n",
                &shards.to_string(),
                "-r",
                &replicas.to_string(),
            ])
            .output()
            .ok()?;
        if !out.status.success() {
            return None;
        }
        let stdout = String::from_utf8_lossy(&out.stdout);

        let mut folder: Option<String> = None;
        let mut nodes: Vec<(String, u16)> = Vec::new();
        for line in stdout.lines() {
            if let Some(rest) = line.strip_prefix("CLUSTER_FOLDER=") {
                folder = Some(rest.trim().to_string());
            } else if let Some(rest) = line.strip_prefix("CLUSTER_NODES=") {
                for addr in rest.trim().split(',') {
                    if let Some((h, p)) = addr.rsplit_once(':')
                        && let Ok(port) = p.parse::<u16>()
                    {
                        nodes.push((h.to_string(), port));
                    }
                }
            }
        }
        let folder = folder?;
        if nodes.is_empty() {
            return None;
        }

        // Parse SERVERS_JSON for the primary/replica split.
        let mut primary_ports: Vec<u16> = Vec::new();
        let mut replica_ports: Vec<u16> = Vec::new();
        if let Some(json_line) = stdout.lines().find(|l| l.starts_with("SERVERS_JSON=")) {
            let json = &json_line["SERVERS_JSON=".len()..];
            for obj in json.split('{').skip(1) {
                let port = field_u64(obj, "\"port\"").map(|v| v as u16);
                let is_primary = obj
                    .split_once("\"is_primary\"")
                    .map(|(_, r)| r.contains("true"))
                    .unwrap_or(false);
                if let Some(port) = port {
                    if is_primary {
                        primary_ports.push(port);
                    } else {
                        replica_ports.push(port);
                    }
                }
            }
        }

        // Seed on a primary if we identified one, else the first node.
        let seed = *primary_ports.first().unwrap_or(&nodes[0].1);
        let mut ports: Vec<u16> = vec![seed];
        ports.extend(nodes.iter().map(|(_, p)| *p).filter(|p| *p != seed));
        if primary_ports.is_empty() {
            primary_ports = nodes.iter().map(|(_, p)| *p).collect();
        }

        Some(ClusterHarness {
            ports,
            folder,
            primary_ports,
            replica_ports,
        })
    }

    /// The seed `host:port` used to connect a cluster client.
    pub fn seed_port(&self) -> u16 {
        self.ports[0]
    }

    /// Connect a cluster client to this cluster with the given protocol.
    pub async fn client_with_protocol(
        &self,
        protocol: ProtocolVersion,
    ) -> Option<GlideClusterClient> {
        let config = GlideClusterClientConfiguration::with_address("127.0.0.1", self.ports[0])
            .protocol(protocol)
            .request_timeout(Duration::from_secs(5));
        // Bounded connect-retry: under load a freshly-formed cluster can briefly
        // refuse or time out the initial connection; a single attempt shouldn't
        // fail the whole test.
        let mut client = None;
        for attempt in 0..10u32 {
            match GlideClusterClient::connect(config.clone()).await {
                Ok(c) => {
                    client = Some(c);
                    break;
                }
                Err(_) => {
                    tokio::time::sleep(Duration::from_millis(100 * (attempt + 1) as u64)).await;
                }
            }
        }
        let client = client?;
        // Converge the connection map before handing the client to a test: right
        // after a cluster forms, the client's topology snapshot can lag, so the
        // first routed op may hit a `MOVED` to a not-yet-connected node
        // (`ConnectionNotFoundForRoute`). A retried broadcast PING forces the
        // client to connect to every primary, eliminating that startup race.
        warm_up_cluster(&client).await;
        Some(client)
    }

    /// Connect a cluster client with the default protocol (RESP3).
    pub async fn client(&self) -> Option<GlideClusterClient> {
        self.client_with_protocol(ProtocolVersion::RESP3).await
    }
}

impl Drop for ClusterHarness {
    fn drop(&mut self) {
        // Stop the cluster using `cluster_manager.py`.
        let _ = Command::new("python3")
            .args([CLUSTER_MANAGER, "stop", "--cluster-folder", &self.folder])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
}

/// Whether an error is a *transient* cluster-topology error — the kind that can
/// occur while a freshly-formed cluster client's slot→node connection map is
/// still converging. Right after a cluster forms, a command can hit a `MOVED`
/// redirect to a node not yet in the connection map, surfacing as
/// `ConnectionNotFoundForRoute`; a topology refresh is triggered but the
/// in-flight op fails. `TRYAGAIN` (multi-key op spanning a slot mid-migration)
/// and `LOADING` (a node still reading its dataset into memory) are likewise
/// transient right after startup. These are all safe to retry.
pub fn is_transient_cluster_error(e: &glide::GlideError) -> bool {
    let m = e.to_string();
    m.contains("ConnectionNotFoundForRoute")
        || m.contains("Requested connection not found")
        || m.contains("connection map")
        || m.contains("MOVED")
        || m.contains("Moved")
        || m.contains("TRYAGAIN")
        || m.contains("TryAgain")
        || m.contains("LOADING")
        || m.contains("Loading")
        || m.contains("loading the dataset")
}

/// Force a freshly-connected cluster client to connect to every primary so its
/// slot→node connection map is fully populated before a test issues routed or
/// scan commands. Best-effort: retries a broadcast `PING` on transient topology
/// errors, and returns once it succeeds (or after a bounded number of attempts /
/// on a non-transient error, leaving the test to surface any real problem).
async fn warm_up_cluster(client: &GlideClusterClient) {
    for attempt in 0..20u32 {
        let mut ping = redis::Cmd::new();
        ping.arg("PING");
        match client.route_command(ping, Route::AllPrimaries).await {
            Ok(_) => return,
            Err(e) if is_transient_cluster_error(&e) => {
                tokio::time::sleep(Duration::from_millis(50 * (attempt + 1) as u64)).await;
            }
            Err(_) => return,
        }
    }
}
