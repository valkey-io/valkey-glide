use super::{
    build_keys_and_certs_for_tls, create_connection_request, get_available_port,
    wait_for_server_to_become_ready, ClusterMode, Module, RedisServer, TestConfiguration,
};
use babushka::client::Client;
use futures::future::{join_all, BoxFuture};
use futures::FutureExt;
use redis::{ConnectionAddr, RedisConnectionInfo};
use std::process;
use std::thread::sleep;
use std::time::Duration;
use tempfile::TempDir;

// Code copied from redis-rs

const LOCALHOST: &str = "127.0.0.1";
pub(crate) const SHORT_CLUSTER_TEST_TIMEOUT: Duration = Duration::from_millis(30_000);
pub(crate) const LONG_CLUSTER_TEST_TIMEOUT: Duration = Duration::from_millis(40_000);

enum ClusterType {
    Tcp,
    TcpTls,
}

impl ClusterType {
    fn build_addr(use_tls: bool, port: u16) -> redis::ConnectionAddr {
        if use_tls {
            redis::ConnectionAddr::TcpTls {
                host: "127.0.0.1".into(),
                port,
                insecure: true,
            }
        } else {
            redis::ConnectionAddr::Tcp("127.0.0.1".into(), port)
        }
    }
}

pub struct RedisCluster {
    pub servers: Vec<RedisServer>,
    pub cluster_info: Vec<ConnectionAddr>,
    pub replica_count: u16,
    pub folders: Vec<TempDir>,
}

fn get_port(address: &ConnectionAddr) -> u16 {
    match address {
        ConnectionAddr::Tcp(_, port) => *port,
        ConnectionAddr::TcpTls {
            host: _,
            port,
            insecure: _,
        } => *port,
        ConnectionAddr::Unix(_) => unreachable!(),
    }
}

impl RedisCluster {
    pub async fn new(nodes: u16, replicas: u16, use_tls: bool) -> Self {
        let initial_port = get_available_port();

        let cluster_info = (0..nodes)
            .map(|port| ClusterType::build_addr(use_tls, port + initial_port))
            .collect();

        Self::new_with_cluster_info(cluster_info, use_tls, replicas, &[]).await
    }

    pub async fn new_with_cluster_info(
        cluster_info: Vec<ConnectionAddr>,
        use_tls: bool,
        replicas: u16,
        modules: &[Module],
    ) -> Self {
        let mut servers = vec![];
        let mut folders = vec![];
        let mut addrs = vec![];
        let mut tls_paths = None;

        let mut is_tls = false;

        if use_tls {
            // Create a shared set of keys in cluster mode
            let tempdir = tempfile::Builder::new()
                .prefix("redis")
                .tempdir()
                .expect("failed to create tempdir");
            let files = build_keys_and_certs_for_tls(&tempdir);
            folders.push(tempdir);
            tls_paths = Some(files);
            is_tls = true;
        }

        for server_info in cluster_info.iter() {
            let port = get_port(server_info);
            servers.push(RedisServer::new_with_addr_tls_modules_and_spawner(
                server_info.clone(),
                tls_paths.clone(),
                modules,
                |cmd| {
                    let tempdir = tempfile::Builder::new()
                        .prefix("redis")
                        .tempdir()
                        .expect("failed to create tempdir");
                    cmd.arg("--cluster-enabled")
                        .arg("yes")
                        .arg("--cluster-config-file")
                        .arg(&tempdir.path().join("nodes.conf"))
                        .arg("--cluster-node-timeout")
                        .arg("5000")
                        .arg("--appendonly")
                        .arg("yes")
                        .arg("--logfile")
                        .arg(format!("{port}.log"));
                    if is_tls {
                        cmd.arg("--tls-cluster").arg("yes");
                        if replicas > 0 {
                            cmd.arg("--tls-replication").arg("yes");
                        }
                    }

                    cmd.current_dir(tempdir.path());
                    folders.push(tempdir);
                    addrs.push(format!("127.0.0.1:{port}"));
                    cmd.spawn().unwrap()
                },
            ));
        }

        let cluster = RedisCluster {
            servers,
            folders,
            cluster_info,
            replica_count: replicas,
        };

        wait_for_cluster_to_become_ready(&cluster.get_server_addresses()).await;

        let mut cmd = process::Command::new("redis-cli");
        cmd.stdout(process::Stdio::null())
            .arg("--cluster")
            .arg("create")
            .args(&addrs);
        if replicas > 0 {
            cmd.arg("--cluster-replicas").arg(replicas.to_string());
        }
        cmd.arg("--cluster-yes");
        if is_tls {
            cmd.arg("--tls").arg("--insecure");
        }
        let status = cmd.status().unwrap();
        assert!(status.success(), "{}", status);

        cluster.wait_for_nodes_to_sync();
        cluster.wait_for_slots_to_fill();
        cluster.wait_for_cluster_ok();
        cluster
    }

    fn wait_for_nodes_to_sync(&self) {
        'server: for server in &self.servers {
            let conn_info = server.connection_info();
            let client = redis::Client::open(conn_info).unwrap();
            let mut con = client.get_connection().unwrap();

            for _ in 1..5000 {
                let value = redis::cmd("CLUSTER").arg("NODES").query(&mut con).unwrap();
                let string_response: String = redis::from_redis_value(&value).unwrap();
                let nodes = string_response.as_str().lines().count();

                if nodes == self.servers.len() {
                    continue 'server;
                }

                sleep(Duration::from_millis(10));
            }

            panic!("failed to sync nodes");
        }
    }

    fn wait_for_slots_to_fill(&self) {
        'server: for server in &self.servers {
            let conn_info = server.connection_info();
            let client = redis::Client::open(conn_info).unwrap();
            let mut con = client.get_connection().unwrap();

            for _ in 1..5000 {
                let value = redis::cmd("CLUSTER").arg("SHARDS").query(&mut con).unwrap();
                //"slots", list of slot-couples, "nodes", more data.
                let shard_response: Vec<redis::Value> = redis::from_redis_value(&value).unwrap();
                let mut tuples: Vec<(u16, u16)> = shard_response
                    .into_iter()
                    .flat_map(|value| {
                        let node: ((), Vec<(u16, u16)>, (), ()) =
                            redis::from_redis_value(&value).unwrap();
                        node.1
                    })
                    .collect();
                tuples.sort_by(|a, b| a.0.cmp(&b.0));
                tuples.dedup();
                let consecutive = tuples.iter().fold((true, None), |acc, val| {
                    (
                        acc.0 && acc.1.map(|val| val + 1).unwrap_or(0) == val.0,
                        Some(val.1),
                    )
                });
                if consecutive.0 && consecutive.1.unwrap_or_default() == 16_383 {
                    continue 'server;
                }

                sleep(Duration::from_millis(10));
            }

            panic!("failed to get slot coverage");
        }
    }

    fn wait_for_cluster_ok(&self) {
        'server: for server in &self.servers {
            let conn_info = server.connection_info();
            let client = redis::Client::open(conn_info).unwrap();
            let mut con = client.get_connection().unwrap();

            for _ in 1..5000 {
                let value = redis::cmd("CLUSTER").arg("INFO").query(&mut con).unwrap();
                //"slots", list of slot-couples, "nodes", more data.
                let string_response: String = redis::from_redis_value(&value).unwrap();

                if string_response.contains("cluster_state:ok")
                    && string_response.contains("cluster_slots_assigned:16384")
                    && string_response.contains("cluster_slots_ok:16384")
                {
                    continue 'server;
                }

                sleep(Duration::from_millis(10));
            }

            panic!("failed to get slot coverage");
        }
    }

    pub fn stop(&mut self) {
        for server in &mut self.servers {
            server.stop();
        }
    }

    pub fn iter_servers(&self) -> impl Iterator<Item = &RedisServer> {
        self.servers.iter()
    }

    pub fn get_server_addresses(&self) -> Vec<ConnectionAddr> {
        self.servers
            .iter()
            .map(|server| server.get_client_addr())
            .collect()
    }
}

impl Drop for RedisCluster {
    fn drop(&mut self) {
        self.stop()
    }
}

pub async fn wait_for_cluster_to_become_ready(server_addresses: &[ConnectionAddr]) {
    let iter = server_addresses.iter();
    let map = iter.map(wait_for_server_to_become_ready);
    join_all(map).await;
}

pub struct ClusterTestBasics {
    pub cluster: RedisCluster,
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

pub async fn setup_test_basics_internal(mut configuration: TestConfiguration) -> ClusterTestBasics {
    let cluster = RedisCluster::new(3, 0, configuration.use_tls).await;
    if let Some(redis_connection_info) = &configuration.connection_info {
        setup_acl_for_cluster(&cluster.get_server_addresses(), redis_connection_info).await;
    }
    configuration.cluster_mode = ClusterMode::Enabled;
    let connection_request =
        create_connection_request(&cluster.get_server_addresses(), &configuration);

    let client = Client::new(connection_request).await.unwrap();
    ClusterTestBasics { cluster, client }
}

pub async fn setup_test_basics_with_connection_info(
    use_tls: bool,
    connection_info: Option<RedisConnectionInfo>,
) -> ClusterTestBasics {
    setup_test_basics_internal(TestConfiguration {
        use_tls,
        connection_info,
        ..Default::default()
    })
    .await
}

pub async fn setup_test_basics(use_tls: bool) -> ClusterTestBasics {
    setup_test_basics_internal(TestConfiguration {
        use_tls,
        ..Default::default()
    })
    .await
}
