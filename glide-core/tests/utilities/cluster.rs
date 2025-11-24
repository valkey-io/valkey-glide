// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use super::{ClusterMode, TestConfiguration, create_connection_request};
use futures::FutureExt;
use futures::future::{BoxFuture, join_all};
use glide_core::client::Client;
use once_cell::sync::Lazy;
use redis::{ConnectionAddr, RedisConnectionInfo};
use serde::Deserialize;
use std::process::Command;
use std::sync::Mutex;
use std::time::Duration;
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
LOG_FILE=/Users/user/glide-for-redis/utils/clusters/redis-cluster-2024-11-05T16-05-44Z-2bz4YS/cluster_manager.log
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
