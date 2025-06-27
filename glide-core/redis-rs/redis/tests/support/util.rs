use rand::Rng;
use redis::{
    cluster_async::ClusterConnection,
    cluster_routing::{MultipleNodeRoutingInfo, RoutingInfo},
};
use std::collections::HashMap;
use versions::Versioning;

use super::TestContext;

use std::cell::Cell;
use tokio::sync::Mutex;

use lazy_static::lazy_static;
lazy_static! {
    static ref CLUSTER_VERSION: Mutex<Cell<usize>> = Mutex::<Cell<usize>>::default();
}
#[macro_export]
macro_rules! assert_args {
    ($value:expr, $($args:expr),+) => {
        let args = $value.to_redis_args();
        let strings: Vec<_> = args.iter()
                                .map(|a| std::str::from_utf8(a.as_ref()).unwrap())
                                .collect();
        assert_eq!(strings, vec![$($args),+]);
    }
}

pub fn parse_client_info(client_info: &str) -> HashMap<String, String> {
    let mut res = HashMap::new();

    for line in client_info.split(' ') {
        let this_attr: Vec<&str> = line.split('=').collect();
        res.insert(this_attr[0].to_string(), this_attr[1].to_string());
    }

    res
}

pub fn version_greater_or_equal(ctx: &TestContext, version: &str) -> bool {
    // Get the server version
    let (major, minor, patch) = ctx.get_version();
    let server_version = Versioning::new(format!("{major}.{minor}.{patch}")).unwrap();
    let compared_version = Versioning::new(version).unwrap();
    // Compare server version with the specified version
    server_version >= compared_version
}

pub fn generate_random_string(length: usize) -> String {
    rand::rng()
        .sample_iter(rand::distr::Alphanumeric)
        .take(length)
        .map(char::from)
        .collect()
}

fn parse_version_from_info(info: String) -> Option<usize> {
    // check for valkey_version
    if let Some(version) = info
        .lines()
        .find_map(|line| line.strip_prefix("valkey_version:"))
    {
        return version_to_usize(version);
    }

    // check for redis_version if no valkey_version was found
    if let Some(version) = info
        .lines()
        .find_map(|line| line.strip_prefix("redis_version:"))
    {
        return version_to_usize(version);
    }
    None
}

/// Takes a version string (e.g., 8.2.1) and converts it to a usize (e.g., 80201)
/// version 12.10.0 will became 121000
fn version_to_usize(version: &str) -> Option<usize> {
    version
        .split('.')
        .enumerate()
        .map(|(index, part)| {
            part.parse::<usize>()
                .ok()
                .map(|num| num * 10_usize.pow(2 * (2 - index) as u32))
        })
        .sum()
}
/// Static function to get the engine version. When version looks like 8.0.0 -> 80000 and 12.0.1 -> 120001.
pub async fn get_cluster_version() -> usize {
    let cluster_version = CLUSTER_VERSION.lock().await;
    if cluster_version.get() == 0 {
        let cluster = crate::support::TestClusterContext::new(3, 0);

        let mut connection = cluster.async_connection(None).await;

        let cmd = redis::cmd("INFO");
        let info = connection
            .route_command(
                &cmd,
                redis::cluster_routing::RoutingInfo::SingleNode(
                    redis::cluster_routing::SingleNodeRoutingInfo::Random,
                ),
            )
            .await
            .unwrap();

        let info_result = redis::from_owned_redis_value::<String>(info).unwrap();

        cluster_version.set(
            parse_version_from_info(info_result.clone())
                .unwrap_or_else(|| panic!("Invalid version string in INFO : {info_result}")),
        );
    }
    cluster_version.get()
}

/// Check if the current cluster version is less than `min_version`.
/// At first, the func check for the Valkey version and if none exists, then the Redis version is checked.
pub async fn engine_version_less_than(min_version: &str) -> bool {
    let test_version = get_cluster_version().await;
    let min_version_usize = version_to_usize(min_version).unwrap();
    if test_version < min_version_usize {
        println!("The engine version is {test_version:?}, which is lower than {min_version:?}");
        return true;
    }
    false
}

/// Terminates all client connections to all nodes in the cluster.
pub async fn kill_all_connections(client: &mut ClusterConnection) {
    let mut client_kill_cmd = redis::cmd("CLIENT");
    client_kill_cmd.arg("KILL").arg("SKIPME").arg("NO");

    let _ = client
        .route_command(
            &client_kill_cmd,
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
        )
        .await
        .unwrap();
}
