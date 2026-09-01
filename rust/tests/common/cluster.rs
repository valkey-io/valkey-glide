// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Cluster harness (cluster_manager.py backend + native fallback).

use super::helpers::key;
use super::server::{free_port, server_binary, wait_for_port};
use glide::{GlideClusterClient, GlideClusterClientConfiguration, ProtocolVersion, Route};
use std::io::{Read, Write};
use std::net::TcpStream;
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

/// Extract a numeric `CLUSTER INFO` field value, e.g.
/// `cluster_info_field(info, "cluster_known_nodes") == Some(3)`.
fn cluster_info_field(info: &str, field: &str) -> Option<u64> {
    for line in info.lines() {
        if let Some(rest) = line.trim().strip_prefix(field)
            && let Some(v) = rest.strip_prefix(':')
        {
            return v.trim().parse().ok();
        }
    }
    None
}

/// Whether a native-cluster node reports FULL convergence — not merely
/// `cluster_state:ok`, which alone can precede a settled slot map / peer view
/// and let a routed command hit a `MOVED` to a not-yet-known node. We gate on
/// three `CLUSTER INFO` fields (mirroring upstream `cluster_manager.py`'s
/// slot-coverage + `cluster_state:ok` + topology-views checks):
///   * `cluster_state:ok`             — node considers the cluster usable;
///   * `cluster_slots_assigned:16384` — full slot coverage (G7);
///   * `cluster_known_nodes:<shards>` — node sees the whole topology (G2).
fn node_fully_converged(port: u16, shards: usize) -> bool {
    let Ok(mut s) = TcpStream::connect(("127.0.0.1", port)) else {
        return false;
    };
    let _ = s.set_read_timeout(Some(Duration::from_secs(2)));
    let Ok(info) = raw_cmd(&mut s, &["CLUSTER", "INFO"]) else {
        return false;
    };
    info.contains("cluster_state:ok")
        && cluster_info_field(&info, "cluster_slots_assigned") == Some(16384)
        && cluster_info_field(&info, "cluster_known_nodes") == Some(shards as u64)
}

/// Send a single RESP command over a raw socket and return the raw reply text.
/// Used only for cluster bootstrapping (slot assignment + meet + info polling),
/// which must bypass the standalone client handshake (SELECT is disabled in
/// cluster mode).
fn raw_cmd(stream: &mut TcpStream, args: &[&str]) -> std::io::Result<String> {
    let mut buf = format!("*{}\r\n", args.len()).into_bytes();
    for a in args {
        buf.extend_from_slice(format!("${}\r\n", a.len()).as_bytes());
        buf.extend_from_slice(a.as_bytes());
        buf.extend_from_slice(b"\r\n");
    }
    stream.write_all(&buf)?;
    let mut resp = [0u8; 65536];
    let n = stream.read(&mut resp)?;
    Ok(String::from_utf8_lossy(&resp[..n]).into_owned())
}

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

/// Two backends:
/// * **cluster_manager.py** (preferred, opt-in): when the `GLIDE_CLUSTER_MANAGER`
///   env var points at the canonical `valkey-glide/utils/cluster_manager.py`,
///   the cluster is created with that tool — matching the Python suite and
///   yielding **replicas** (and, in future, TLS). Requires `valkey-cli` on PATH.
/// * **native** (fallback, self-contained): builds a primaries-only cluster
///   directly from `valkey-server` via `CLUSTER ADDSLOTSRANGE` + `MEET`, so the
///   standalone repo needs no external tooling.
pub struct ClusterHarness {
    children: Vec<Child>,
    pub ports: Vec<u16>,
    dir: std::path::PathBuf,
    /// When `Some`, the cluster was created by cluster_manager.py; the value is
    /// the `--cluster-folder` used to stop it.
    managed_folder: Option<String>,
    /// Primary node ports (the seed is `ports[0]`, always a primary).
    pub primary_ports: Vec<u16>,
    /// Replica node ports (empty for the native backend).
    pub replica_ports: Vec<u16>,
}

impl ClusterHarness {
    /// Start a 3-primary cluster (no replicas) and wait for it to converge.
    /// Returns `None` (so tests SKIP) if a server binary is unavailable or the
    /// cluster does not reach `cluster_state:ok` within the timeout.
    pub fn start() -> Option<ClusterHarness> {
        Self::start_shards(3)
    }

    /// Start a cluster with `shards` primaries. Tries cluster_manager.py first
    /// (adds replicas), then the native primaries-only backend.
    pub fn start_shards(shards: usize) -> Option<ClusterHarness> {
        if let Some(h) = Self::start_via_cluster_manager(shards, 1) {
            return Some(h);
        }
        Self::start_native(shards)
    }

    /// Preferred backend: shell out to the canonical `cluster_manager.py`
    /// (pointed to by `GLIDE_CLUSTER_MANAGER`). Returns `None` if the env var is
    /// unset/invalid or the tool fails, so the caller falls back to native.
    fn start_via_cluster_manager(shards: usize, replicas: usize) -> Option<ClusterHarness> {
        let script = std::env::var("GLIDE_CLUSTER_MANAGER").ok()?;
        if !std::path::Path::new(&script).exists() {
            return None;
        }
        let out = Command::new("python3")
            .args([
                &script,
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
            children: Vec::new(),
            ports,
            dir: std::path::PathBuf::from(&folder),
            managed_folder: Some(folder),
            primary_ports,
            replica_ports,
        })
    }

    /// Native fallback: build a `shards`-primary cluster directly from
    /// `valkey-server`.
    fn start_native(shards: usize) -> Option<ClusterHarness> {
        let bin = server_binary()?;
        let dir = std::env::temp_dir().join(key("vgr_cluster"));
        std::fs::create_dir_all(&dir).ok()?;

        let ports: Vec<u16> = (0..shards).map(|_| free_port()).collect();
        let mut children = Vec::with_capacity(shards);
        for &port in &ports {
            let node_dir = dir.join(port.to_string());
            if std::fs::create_dir_all(&node_dir).is_err() {
                break;
            }
            let conf = format!("nodes-{port}.conf");
            let mut command = Command::new(&bin);
            command
                .args([
                    "--port",
                    &port.to_string(),
                    "--bind",
                    "127.0.0.1",
                    "--cluster-enabled",
                    "yes",
                    "--cluster-config-file",
                    &conf,
                    "--cluster-node-timeout",
                    "5000",
                    "--dir",
                    node_dir.to_str().unwrap(),
                    "--save",
                    "",
                    "--appendonly",
                    "no",
                ])
                .stdout(Stdio::null())
                .stderr(Stdio::null());
            match command.spawn() {
                Ok(c) => {
                    children.push(c);
                }
                Err(_) => break,
            }
        }

        let harness = ClusterHarness {
            children,
            ports: ports.clone(),
            dir,
            managed_folder: None,
            primary_ports: ports.clone(),
            replica_ports: Vec::new(),
        };

        // All nodes must be up.
        if harness.children.len() != shards {
            return None;
        }
        for &port in &ports {
            if !wait_for_port(port, Duration::from_secs(10)) {
                return None;
            }
        }

        // Assign slot ranges evenly across primaries, then MEET into node 0.
        let total_slots = 16384u32;
        let per = total_slots / shards as u32;
        for (i, &port) in ports.iter().enumerate() {
            let lo = i as u32 * per;
            let hi = if i == shards - 1 {
                total_slots - 1
            } else {
                (i as u32 + 1) * per - 1
            };
            let mut s = TcpStream::connect(("127.0.0.1", port)).ok()?;
            s.set_read_timeout(Some(Duration::from_secs(3))).ok()?;
            let reply = raw_cmd(
                &mut s,
                &["CLUSTER", "ADDSLOTSRANGE", &lo.to_string(), &hi.to_string()],
            )
            .ok()?;
            if !reply.contains("OK") {
                return None;
            }
        }
        for &port in &ports[1..] {
            let mut s = TcpStream::connect(("127.0.0.1", ports[0])).ok()?;
            s.set_read_timeout(Some(Duration::from_secs(3))).ok()?;
            let reply =
                raw_cmd(&mut s, &["CLUSTER", "MEET", "127.0.0.1", &port.to_string()]).ok()?;
            if !reply.contains("OK") {
                return None;
            }
        }

        // Wait for FULL convergence before returning the cluster. A node
        // reporting `cluster_state:ok` alone is insufficient: right after
        // formation its slot map / peer view can still be settling, so the first
        // routed op may hit a `MOVED` to a not-yet-known node. We therefore gate
        // on every node reaching full slot coverage AND full topology awareness
        // (see [`node_fully_converged`]) — the source-level fix for the
        // cluster-scan startup race that `warm_up_cluster`/`retry_transient!`
        // otherwise paper over.
        let deadline = Instant::now() + Duration::from_secs(20);
        let mut all_ok = false;
        while Instant::now() < deadline {
            let converged = ports
                .iter()
                .filter(|&&port| node_fully_converged(port, shards))
                .count();
            if converged == ports.len() {
                all_ok = true;
                break;
            }
            std::thread::sleep(Duration::from_millis(200));
        }
        if !all_ok {
            return None;
        }
        Some(harness)
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
        if let Some(folder) = &self.managed_folder {
            // Managed backend: stop via cluster_manager.py (best effort).
            if let Ok(script) = std::env::var("GLIDE_CLUSTER_MANAGER") {
                let _ = Command::new("python3")
                    .args([&script, "stop", "--cluster-folder", folder])
                    .stdout(Stdio::null())
                    .stderr(Stdio::null())
                    .status();
            }
            return;
        }
        for child in &mut self.children {
            let _ = child.kill();
            let _ = child.wait();
        }
        let _ = std::fs::remove_dir_all(&self.dir);
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
