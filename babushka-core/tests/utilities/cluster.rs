use super::{
    build_keys_and_certs_for_tls, get_available_port, wait_for_server_to_become_ready, Module,
    RedisServer,
};
use futures::future::join_all;
use redis::ConnectionAddr;
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
    pub fn username() -> &'static str {
        "hello"
    }

    pub fn password() -> &'static str {
        "world"
    }

    pub fn new(nodes: u16, replicas: u16, use_tls: bool) -> Self {
        let initial_port = get_available_port();

        let cluster_info = (0..nodes)
            .map(|port| ClusterType::build_addr(use_tls, port + initial_port))
            .collect();

        Self::new_with_cluster_info(cluster_info, use_tls, replicas, &[])
    }

    pub fn new_with_cluster_info(
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
                    let acl_path = tempdir.path().join("users.acl");
                    let acl_content = format!(
                        "user {} on allcommands allkeys >{}",
                        Self::username(),
                        Self::password()
                    );
                    std::fs::write(&acl_path, acl_content).expect("failed to write acl file");
                    cmd.arg("--cluster-enabled")
                        .arg("yes")
                        .arg("--cluster-config-file")
                        .arg(&tempdir.path().join("nodes.conf"))
                        .arg("--cluster-node-timeout")
                        .arg("5000")
                        .arg("--appendonly")
                        .arg("yes")
                        .arg("--aclfile")
                        .arg(&acl_path);
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

        sleep(Duration::from_millis(100));

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
        assert!(cmd.status().unwrap().success());

        let cluster = RedisCluster {
            servers,
            folders,
            cluster_info,
            replica_count: replicas,
        };
        if replicas > 0 {
            cluster.wait_for_replicas(replicas);
        }
        cluster
    }

    fn wait_for_replicas(&self, replicas: u16) {
        'server: for server in &self.servers {
            let conn_info = server.connection_info();
            eprintln!(
                "waiting until {:?} knows required number of replicas",
                conn_info.addr
            );
            let client = redis::Client::open(conn_info).unwrap();
            let mut con = client.get_connection().unwrap();

            // retry 500 times
            for _ in 1..500 {
                let value = redis::cmd("CLUSTER").arg("SLOTS").query(&mut con).unwrap();
                let slots: Vec<Vec<redis::Value>> = redis::from_redis_value(&value).unwrap();

                // all slots should have following items:
                // [start slot range, end slot range, master's IP, replica1's IP, replica2's IP,... ]
                if slots.iter().all(|slot| slot.len() >= 3 + replicas as usize) {
                    continue 'server;
                }

                sleep(Duration::from_millis(100));
            }

            panic!("failed to create enough replicas");
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
    // let vec = map.collect();
    join_all(map).await;
}
