#![allow(unknown_lints, dependency_on_unit_never_type_fallback)]
#![cfg(feature = "cluster-async")]
mod support;

#[cfg(test)]
mod cluster_async {
    use std::{
        collections::HashMap,
        net::{IpAddr, SocketAddr},
        str::from_utf8,
        sync::{
            atomic::{self, AtomicBool, AtomicI32, AtomicU16, AtomicU32, Ordering},
            Arc,
        },
        time::Duration,
    };

    use futures::prelude::*;
    use futures_time::{future::FutureExt, task::sleep};
    use once_cell::sync::Lazy;
    use std::ops::Add;
    use std::path::PathBuf;
    use std::sync::OnceLock;
    use telemetrylib::*;
    use tokio::runtime::Runtime;

    use redis::{
        aio::{ConnectionLike, MultiplexedConnection},
        cluster::ClusterClient,
        cluster_async::{testing::MANAGEMENT_CONN_NAME, ClusterConnection, Connect},
        cluster_routing::{
            MultipleNodeRoutingInfo, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
        },
        cluster_topology::{get_slot, DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES},
        cmd, fenced_cmd, from_owned_redis_value, parse_redis_value, AsyncCommands, Cmd,
        ConnectionAddr, ErrorKind, FromRedisValue, GlideConnectionOptions, InfoDict,
        IntoConnectionInfo, PipelineRetryStrategy, ProtocolVersion, RedisError, RedisFuture,
        RedisResult, Value,
    };

    use crate::support::*;
    fn broken_pipe_error() -> RedisError {
        RedisError::from(std::io::Error::new(
            std::io::ErrorKind::BrokenPipe,
            "mock-io-error",
        ))
    }

    const SPANS_JSON: &str = "/tmp/spans.json";
    const METRICS_JSON: &str = "/tmp/metrics.json";
    // const SPANS_CLOUDWATCH: &str = "http://localhost:4318/v1/traces";
    // const METRICS_CLOUDWATCH: &str = "http://localhost:4318/v1/metrics";
    const PUBLISH_TIME: u64 = 2000;
    const MOVED: &str = "glide.moved_errors";
    const RETRY: &str = "glide.retry_attempts";

    async fn init_otel() -> Result<(), GlideOTELError> {
        let config = GlideOpenTelemetryConfigBuilder::default()
            .with_flush_interval(Duration::from_millis(PUBLISH_TIME))
            .with_trace_exporter(
                GlideOpenTelemetrySignalsExporter::File(PathBuf::from(SPANS_JSON)),
                Some(100),
            )
            .with_metrics_exporter(GlideOpenTelemetrySignalsExporter::File(PathBuf::from(
                METRICS_JSON,
            )))
            .build();
        if let Err(e) = GlideOpenTelemetry::initialise(config) {
            panic!("Failed to initialize OpenTelemetry: {e}");
        }
        Ok(())
    }

    fn is_in_same_node(slot1: u16, slot2: u16, nodes_and_slots: Vec<(&String, Vec<u16>)>) -> bool {
        for (_, slots) in nodes_and_slots {
            if slots[0] <= slot1 && slot1 <= slots[1] && slots[0] <= slot2 && slot2 <= slots[1] {
                return true;
            }
        }
        false
    }

    fn generate_two_keys_in_the_same_node(
        nodes_and_slots: Vec<(&String, Vec<u16>)>,
    ) -> (String, String) {
        for _ in 0..1000 {
            let key = generate_random_string(10);
            let key2 = generate_random_string(10);
            let slot = get_slot(key.as_bytes());
            let slot2 = get_slot(key2.as_bytes());

            if is_in_same_node(slot, slot2, nodes_and_slots.clone()) {
                return (key, key2);
            }
        }
        panic!("Failed to 2 keys after 1000 attempts");
    }

    pub async fn assert_error_occurred(
        connection: &mut ClusterConnection,
        error_type: &str,
        expected_count: usize,
    ) {
        // Build the INFO errorstats command.
        let mut info_errorstats_cmd = cmd("INFO");
        info_errorstats_cmd.arg("errorstats");

        // Execute the command on all nodes.
        let res = connection
            .route_command(
                &info_errorstats_cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .expect("INFO errorstats command failed");

        // Parse the INFO output.
        let info_result: HashMap<String, String> =
            redis::from_owned_redis_value(res).expect("Failed to parse INFO command result");

        // Search for the specified error type.
        let mut error_count: Option<usize> = None;
        let error_prefix = format!("errorstat_{error_type}:count=");

        for info in info_result.values() {
            for line in info.lines() {
                if line.starts_with(&error_prefix) {
                    if let Some(count_str) = line.strip_prefix(&error_prefix) {
                        let count_val = count_str
                            .split(',')
                            .next()
                            .unwrap_or("0")
                            .parse::<usize>()
                            .unwrap_or(0);
                        error_count = Some(count_val + error_count.unwrap_or(0));
                        break;
                    }
                }
            }
        }

        // Assert that the found count matches the expected count.
        match error_count {
            Some(count) => assert_eq!(
                count, expected_count,
                "Expected {error_type} count {expected_count} but found {count}"
            ),
            None => panic!("{error_type} not found in INFO errorstats output"),
        }
    }

    fn shared_runtime() -> &'static Runtime {
        static RUNTIME: OnceLock<Runtime> = OnceLock::new();
        RUNTIME.get_or_init(|| Runtime::new().expect("Failed to create runtime"))
    }

    fn read_latest_metrics_json() -> serde_json::Value {
        let file_content =
            std::fs::read_to_string(METRICS_JSON).expect("Failed to read metrics JSON file");
        let lines: Vec<&str> = file_content
            .lines()
            .filter(|l| !l.trim().is_empty())
            .collect();
        serde_json::from_str(lines.last().expect("No metrics lines found"))
            .expect("Failed to parse metrics JSON")
    }

    fn find_metric<'a>(
        metrics_json: &'a serde_json::Value,
        metric_name: &str,
    ) -> &'a serde_json::Value {
        metrics_json["scope_metrics"][0]["metrics"]
            .as_array()
            .expect("Expected 'metrics' to be an array")
            .iter()
            .find(|m| m["name"] == metric_name)
            .unwrap_or_else(|| panic!("Metric '{metric_name}' not found"))
    }

    fn get_start_value(metric_name: &str) -> u64 {
        let file_content = match std::fs::read_to_string(METRICS_JSON) {
            Ok(content) => content,
            Err(_) => return 0, // File not found or unreadable
        };

        let lines: Vec<&str> = file_content
            .split('\n')
            .filter(|l| !l.trim().is_empty())
            .collect();

        if lines.is_empty() {
            return 0;
        }

        let metric_json: serde_json::Value = match serde_json::from_str(lines.last().unwrap()) {
            Ok(json) => json,
            Err(_) => return 0, // Invalid JSON
        };

        let metric = match metric_json["scope_metrics"][0]["metrics"]
            .as_array()
            .and_then(|metrics| metrics.iter().find(|m| m["name"] == metric_name))
        {
            Some(m) => m,
            None => return 0,
        };

        metric["data_points"][0]["value"].as_u64().unwrap_or(0)
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_basic_cmd() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            cmd("SET")
                .arg("test")
                .arg("test_data")
                .query_async::<_, ()>(&mut connection)
                .await?;
            let res: String = cmd("GET")
                .arg("test")
                .clone()
                .query_async(&mut connection)
                .await?;
            assert_eq!(res, "test_data");
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_open_telemetry_moved_command() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel().await.unwrap();

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;
            let start_moved_value = get_start_value(MOVED);
            let start_retry_value = get_start_value(RETRY);

            // Create a test cluster with 3 masters and no replicas.
            let cluster = TestClusterContext::new(3, 0);
            let mut connection = cluster.async_connection(None).await;

            // Get the current slot distribution.
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let nodes_and_slots = slot_distribution
                .iter()
                .map(|(node_id, _, _, v)| (node_id, v[0].clone()))
                .collect::<Vec<_>>();

            let (moved_key, key2) = generate_two_keys_in_the_same_node(nodes_and_slots);
            let key_slot = get_slot(moved_key.as_bytes());

            cluster
                .move_specific_slot(key_slot, slot_distribution)
                .await;

            let mut cmd: Cmd = redis::cmd("SET");
            cmd.arg(&moved_key).arg("value");

            let result = connection
                .route_command(
                    &cmd,
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                        key_slot,
                        SlotAddr::Master,
                    ))),
                )
                .await
                .expect("Failed to route command");

            let expected = Value::Okay;

            assert_eq!(
                result, expected,
                "Command result did not match expected output.\n\
                     Keys chosen: ('{moved_key}', '{key2}')\n\
                     key_slot: {key_slot}\n\
                     Expected result: {expected:?}\n\
                     Actual result: {result:?}"
            );

            assert_error_occurred(&mut connection, "MOVED", 1).await;
            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;

            let metric_json = read_latest_metrics_json();
            let moved_errors = find_metric(&metric_json, MOVED);
            let retry_attempts = find_metric(&metric_json, RETRY);

            assert_eq!(
                moved_errors["data_points"][0]["value"],
                1 + start_moved_value
            );

            assert_eq!(
                retry_attempts["data_points"][0]["value"],
                1 + start_retry_value
            );
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_async_open_telemetry_moved_pipeline_atomic() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel().await.unwrap();

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;
            let start_moved_value = get_start_value(MOVED);
            let start_retry_value = get_start_value(RETRY);

            // Create a test cluster with 3 masters and no replicas.
            let cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(2),
                false,
            );
            let mut connection = cluster.async_connection(None).await;

            // Get the current slot distribution.
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let nodes_and_slots = slot_distribution
                .iter()
                .map(|(node_id, _, _, v)| (node_id, v[0].clone()))
                .collect::<Vec<_>>();

            let (moved_key, key2) = generate_two_keys_in_the_same_node(nodes_and_slots);
            let key_slot = get_slot(moved_key.as_bytes());

            cluster
                .move_specific_slot(key_slot, slot_distribution)
                .await;

            // Create a pipeline with several commands.
            let mut pipeline = redis::pipe();
            pipeline.atomic().incr(&moved_key, 5).get(&moved_key);

            let route = SingleNodeRoutingInfo::SpecificNode(Route::new(key_slot, SlotAddr::Master));
            // connection.route_command(cmd, routing)
            // Execute the pipeline.
            let result = connection
                .route_pipeline(
                    &pipeline,
                    3,
                    1,
                    Some(route),
                    Some(PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    }),
                )
                .await;

            let expected = vec![Value::Array(vec![
                Value::Int(5),
                Value::BulkString(b"5".to_vec()),
            ])];
            let result = result.expect("Pipeline execution failed");
            assert_eq!(
                result, expected,
                "Pipeline result did not match expected output.\n\
                     Keys chosen: ('{moved_key}', '{key2}')\n\
                     key_slot: {key_slot}\n\
                     Actual result: {result:?}"
            );

            assert_error_occurred(&mut connection, "MOVED", 2).await;
            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;

            let metric_json = read_latest_metrics_json();
            let moved_errors = find_metric(&metric_json, "glide.moved_errors");
            let retry_attempts = find_metric(&metric_json, "glide.retry_attempts");

            assert_eq!(
                moved_errors["data_points"][0]["value"],
                2 + start_moved_value
            );

            assert_eq!(
                retry_attempts["data_points"][0]["value"],
                1 + start_retry_value
            );
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_async_open_telemetry_moved_pipeline_non_atomic() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel().await.unwrap();

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;
            let start_moved_value = get_start_value(MOVED);
            let start_retry_value = get_start_value(RETRY);

            // Create a test cluster with 3 masters and no replicas.
            let cluster = TestClusterContext::new(3, 0);
            let mut connection = cluster.async_connection(None).await;

            // Get the current slot distribution.
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let nodes_and_slots = slot_distribution
                .iter()
                .map(|(node_id, _, _, v)| (node_id, v[0].clone()))
                .collect::<Vec<_>>();

            let (moved_key, key2) = generate_two_keys_in_the_same_node(nodes_and_slots);
            let key_slot = get_slot(moved_key.as_bytes());

            cluster
                .move_specific_slot(key_slot, slot_distribution)
                .await;

            // Create a pipeline with several commands.
            let mut pipeline = redis::pipe();
            pipeline
                .incr(&key2, 5)
                .set(&moved_key, "value")
                .get(&moved_key);
            // connection.route_command(cmd, routing)
            // Execute the pipeline.
            let result = connection
                .route_pipeline(
                    &pipeline,
                    0,
                    3,
                    None,
                    Some(PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    }),
                )
                .await
                .expect("Pipeline execution failed");

            let expected = vec![
                Value::Int(5),
                Value::Okay,
                Value::BulkString(b"value".to_vec()),
            ];
            assert_eq!(
                result, expected,
                "Pipeline result did not match expected output.\n\
                     Keys chosen: ('{moved_key}', '{key2}')\n\
                     key_slot: {key_slot}\n\
                     Actual result: {result:?}"
            );

            assert_error_occurred(&mut connection, "MOVED", 2).await;
            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;

            let metric_json = read_latest_metrics_json();
            let moved_errors = find_metric(&metric_json, "glide.moved_errors");
            let retry_attempts = find_metric(&metric_json, "glide.retry_attempts");

            assert_eq!(
                moved_errors["data_points"][0]["value"],
                2 + start_moved_value
            );

            assert_eq!(
                retry_attempts["data_points"][0]["value"],
                2 + start_retry_value
            );
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_async_open_telemetry_retry_pipeline_atomic() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel().await.unwrap();

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;
            let start_retry_value = get_start_value(RETRY);

            // Create a test cluster with 3 masters and no replicas.
            let retry = true;
            let cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(10),
                false,
            );
            let mut connection = cluster.async_connection(None).await;
            let mut stable_conn = cluster.async_connection(None).await;

            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let migrated_key = generate_random_string(10);
            let key_slot = get_slot(migrated_key.as_bytes());
            let key = format!("{{{}}}:{}", migrated_key, generate_random_string(5));
            let des_node = cluster.migrate_slot(key_slot, slot_distribution).await;

            let stable_future = async {
                // This future completes the slot migration, which resolves the `TryAgain` error.
                tokio::time::sleep(std::time::Duration::from_millis(500)).await;
                let mut cluster_setslot_cmd = redis::cmd("CLUSTER");
                cluster_setslot_cmd
                    .arg("SETSLOT")
                    .arg(key_slot)
                    .arg("NODE")
                    .arg(des_node);
                let all_nodes = RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None));
                stable_conn
                    .route_command(&cluster_setslot_cmd, all_nodes)
                    .await
                    .expect("Failed to move slot completely");
            };

            let future = async {
                let mut pipeline = redis::pipe();
                pipeline
                    .atomic()
                    .get(&migrated_key)
                    .set(&migrated_key, "value");
                let route =
                    SingleNodeRoutingInfo::SpecificNode(Route::new(key_slot, SlotAddr::Master));

                // Execute the pipeline.
                let result = connection
                    .route_pipeline(
                        &pipeline,
                        3,
                        1,
                        Some(route),
                        Some(PipelineRetryStrategy {
                            retry_server_error: retry,
                            retry_connection_error: false,
                        }),
                    )
                    .await
                    .expect("Pipeline execution failed");
                result
            };

            let ((), result) = tokio::join!(stable_future, future);

            let expected = vec![Value::Array(vec![Value::Nil, Value::Okay])];

            assert_eq!(
                result[0..1],
                expected,
                "Pipeline result did not match expected output.\n\
                 Keys chosen: ('{migrated_key}', '{key}')\n\
                 key_slot: {key_slot}\n\
                 Actual result: {result:?}"
            );

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;

            let metric_json = read_latest_metrics_json();
            let retry_attempts = find_metric(&metric_json, "glide.retry_attempts");

            assert_eq!(
                retry_attempts["data_points"][0]["value"],
                1 + start_retry_value
            );
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_async_open_telemetry_retry_pipeline_non_atomic() {
        let rt = shared_runtime();
        rt.block_on(async {
            let _ = std::fs::remove_file(METRICS_JSON);
            init_otel().await.unwrap();

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;
            let start_retry_value = get_start_value(RETRY);

            // Create a test cluster with 3 masters and no replicas.
            let retry = true;
            let cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(10),
                false,
            );
            let mut connection = cluster.async_connection(None).await;
            let mut stable_conn = cluster.async_connection(None).await;

            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let migrated_key = generate_random_string(10);
            let key_slot = get_slot(migrated_key.as_bytes());
            let key = format!("{{{}}}:{}", migrated_key, generate_random_string(5));
            let des_node = cluster.migrate_slot(key_slot, slot_distribution).await;

            let stable_future = async {
                // This future completes the slot migration, which resolves the `TryAgain` error.
                tokio::time::sleep(std::time::Duration::from_millis(500)).await;
                let mut cluster_setslot_cmd = redis::cmd("CLUSTER");
                cluster_setslot_cmd
                    .arg("SETSLOT")
                    .arg(key_slot)
                    .arg("NODE")
                    .arg(des_node);
                let all_nodes = RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None));
                stable_conn
                    .route_command(&cluster_setslot_cmd, all_nodes)
                    .await
                    .expect("Failed to move slot completely");
            };

            let future = async {
                let mut pipeline = redis::pipe();
                pipeline
                    .get(&migrated_key)
                    .set(&migrated_key, "value")
                    .mget(&[&migrated_key, &key]);

                // Execute the pipeline.
                let result = connection
                    .route_pipeline(
                        &pipeline,
                        0,
                        3,
                        None,
                        Some(PipelineRetryStrategy {
                            retry_server_error: retry,
                            retry_connection_error: false,
                        }),
                    )
                    .await
                    .expect("Pipeline execution failed");
                result
            };

            let ((), result) = tokio::join!(stable_future, future);

            let expected = vec![Value::Nil, Value::Okay];

            assert_eq!(
                result[0..2],
                expected,
                "Pipeline result did not match expected output.\n\
                 Keys chosen: ('{migrated_key}', '{key}')\n\
                 key_slot: {key_slot}\n\
                 Actual result: {result:?}"
            );

            sleep(Duration::from_millis(PUBLISH_TIME + 100).into()).await;

            let metric_json = read_latest_metrics_json();
            let retry_attempts = find_metric(&metric_json, "glide.retry_attempts");

            assert_eq!(
                retry_attempts["data_points"][0]["value"],
                4 + start_retry_value
            );
        });
    }

    #[tokio::test]
    async fn test_routing_by_slot_to_replica_with_az_affinity_strategy_to_half_replicas() {
        test_az_affinity_helper(StrategyVariant::AZAffinity).await;
    }

    #[tokio::test]
    async fn test_routing_by_slot_to_replica_with_az_affinity_replicas_and_primary_strategy_to_half_replicas(
    ) {
        test_az_affinity_helper(StrategyVariant::AZAffinityReplicasAndPrimary).await;
    }
    enum StrategyVariant {
        AZAffinity,
        AZAffinityReplicasAndPrimary,
    }

    async fn test_az_affinity_helper(strategy_variant: StrategyVariant) {
        // Skip test if version is less then Valkey 8.0
        if engine_version_less_than("8.0").await {
            return;
        }

        let replica_num: u16 = 4;
        let primaries_num: u16 = 3;
        let replicas_num_in_client_az = replica_num / 2;
        let cluster =
            TestClusterContext::new((replica_num * primaries_num) + primaries_num, replica_num);
        let az: String = "us-east-1a".to_string();

        let mut connection = cluster.async_connection(None).await;
        let cluster_addresses: Vec<_> = cluster
            .cluster
            .servers
            .iter()
            .map(|server| server.connection_info())
            .collect();

        let mut cmd = redis::cmd("CONFIG");
        cmd.arg(&["SET", "availability-zone", &az.clone()]);

        for _ in 0..replicas_num_in_client_az {
            connection
                .route_command(
                    &cmd,
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                        12182, // foo key is mapping to 12182 slot
                        SlotAddr::ReplicaRequired,
                    ))),
                )
                .await
                .unwrap();
        }
        let strategy = match strategy_variant {
            StrategyVariant::AZAffinity => {
                redis::cluster_slotmap::ReadFromReplicaStrategy::AZAffinity(az.clone())
            }
            StrategyVariant::AZAffinityReplicasAndPrimary => {
                redis::cluster_slotmap::ReadFromReplicaStrategy::AZAffinityReplicasAndPrimary(
                    az.clone(),
                )
            }
        };
        let mut client = ClusterClient::builder(cluster_addresses.clone())
            .read_from(strategy)
            .build()
            .unwrap()
            .get_async_connection(None, None)
            .await
            .unwrap();

        // Each replica in the client az will return the value of foo n times
        let n = 4;
        for _ in 0..n * replicas_num_in_client_az {
            let mut cmd = redis::cmd("GET");
            cmd.arg("foo");
            let _res: RedisResult<Value> = cmd.query_async(&mut client).await;
        }

        let mut cmd = redis::cmd("INFO");
        cmd.arg("ALL");
        let info = connection
            .route_command(
                &cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .unwrap();

        let info_result = redis::from_owned_redis_value::<HashMap<String, String>>(info).unwrap();
        let get_cmdstat = "cmdstat_get:calls=".to_string();
        let n_get_cmdstat = format!("cmdstat_get:calls={n}");
        let client_az = format!("availability_zone:{az}");

        let mut matching_entries_count: usize = 0;

        for value in info_result.values() {
            if value.contains(&get_cmdstat) {
                if value.contains(&client_az) && value.contains(&n_get_cmdstat) {
                    matching_entries_count += 1;
                } else {
                    panic!(
                        "Invalid entry found: {value}. Expected cmdstat_get:calls={n} and availability_zone={az}");
                }
            }
        }

        assert_eq!(
            (matching_entries_count.try_into() as Result<u16, _>).unwrap(),
            replicas_num_in_client_az,
            "Test failed: expected exactly '{replicas_num_in_client_az}' entries with '{get_cmdstat}' and '{client_az}', found {matching_entries_count}"
        );
    }

    #[tokio::test]
    async fn test_az_affinity_strategy_to_all_replicas() {
        test_all_replicas_helper(StrategyVariant::AZAffinity).await;
    }

    #[tokio::test]
    async fn test_az_affinity_replicas_and_primary_to_all_replicas() {
        test_all_replicas_helper(StrategyVariant::AZAffinityReplicasAndPrimary).await;
    }

    async fn test_all_replicas_helper(strategy_variant: StrategyVariant) {
        // Skip test if version is less then Valkey 8.0
        if engine_version_less_than("8.0").await {
            return;
        }

        let replica_num: u16 = 4;
        let primaries_num: u16 = 3;
        let cluster =
            TestClusterContext::new((replica_num * primaries_num) + primaries_num, replica_num);
        let az: String = "us-east-1a".to_string();

        let mut connection = cluster.async_connection(None).await;
        let cluster_addresses: Vec<_> = cluster
            .cluster
            .servers
            .iter()
            .map(|server| server.connection_info())
            .collect();

        let mut cmd = redis::cmd("CONFIG");
        cmd.arg(&["SET", "availability-zone", &az.clone()]);

        connection
            .route_command(
                &cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .unwrap();

        // Strategy-specific client configuration
        let strategy = match strategy_variant {
            StrategyVariant::AZAffinity => {
                redis::cluster_slotmap::ReadFromReplicaStrategy::AZAffinity(az.clone())
            }
            StrategyVariant::AZAffinityReplicasAndPrimary => {
                redis::cluster_slotmap::ReadFromReplicaStrategy::AZAffinityReplicasAndPrimary(
                    az.clone(),
                )
            }
        };
        let mut client = ClusterClient::builder(cluster_addresses.clone())
            .read_from(strategy)
            .build()
            .unwrap()
            .get_async_connection(None, None)
            .await
            .unwrap();

        // Each replica will return the value of foo n times
        let n = 4;
        for _ in 0..(n * replica_num) {
            let mut cmd = redis::cmd("GET");
            cmd.arg("foo");
            let _res: RedisResult<Value> = cmd.query_async(&mut client).await;
        }

        let mut cmd = redis::cmd("INFO");
        cmd.arg("ALL");
        let info = connection
            .route_command(
                &cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .unwrap();

        let info_result = redis::from_owned_redis_value::<HashMap<String, String>>(info).unwrap();
        let get_cmdstat = "cmdstat_get:calls=".to_string();
        let n_get_cmdstat = format!("cmdstat_get:calls={n}");
        let client_az = format!("availability_zone:{az}");

        let mut matching_entries_count: usize = 0;

        for value in info_result.values() {
            if value.contains(&get_cmdstat) {
                if value.contains(&client_az) && value.contains(&n_get_cmdstat) {
                    matching_entries_count += 1;
                } else {
                    panic!(
                        "Invalid entry found: {value}. Expected cmdstat_get:calls={n} and availability_zone={az}");
                }
            }
        }

        assert_eq!(
            (matching_entries_count.try_into() as Result<u16, _>).unwrap(),
            replica_num,
            "Test failed: expected exactly '{replica_num}' entries with '{get_cmdstat}' and '{client_az}', found {matching_entries_count}"
        );
    }

    #[tokio::test]
    async fn test_az_affinity_replicas_and_primary_prefers_local_primary() {
        // Skip test if version is less than Valkey 8.0
        if engine_version_less_than("8.0").await {
            return;
        }

        let replica_num: u16 = 4;
        let primaries_num: u16 = 3;
        let primary_in_same_az: u16 = 1;

        let cluster =
            TestClusterContext::new((replica_num * primaries_num) + primaries_num, replica_num);
        let client_az = "us-east-1a".to_string();
        let other_az = "us-east-1b".to_string();

        let mut connection = cluster.async_connection(None).await;
        let cluster_addresses: Vec<_> = cluster
            .cluster
            .servers
            .iter()
            .map(|server| server.connection_info())
            .collect();

        // Set AZ for all nodes to a different AZ initially
        let mut cmd = redis::cmd("CONFIG");
        cmd.arg(&["SET", "availability-zone", &other_az.clone()]);

        connection
            .route_command(
                &cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .unwrap();

        // Set the client's AZ for one primary (the last one)
        let mut cmd = redis::cmd("CONFIG");
        cmd.arg(&["SET", "availability-zone", &client_az]);
        connection
            .route_command(
                &cmd,
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                    12182, // This should target the third primary
                    SlotAddr::Master,
                ))),
            )
            .await
            .unwrap();

        let mut client = ClusterClient::builder(cluster_addresses.clone())
            .read_from(
                redis::cluster_slotmap::ReadFromReplicaStrategy::AZAffinityReplicasAndPrimary(
                    client_az.clone(),
                ),
            )
            .build()
            .unwrap()
            .get_async_connection(None, None)
            .await
            .unwrap();

        // Perform read operations
        let n = 100;
        for _ in 0..n {
            let mut cmd = redis::cmd("GET");
            cmd.arg("foo"); // This key should hash to the third primary's slot
            let _res: RedisResult<Value> = cmd.query_async(&mut client).await;
        }

        // Gather INFO
        let mut cmd = redis::cmd("INFO");
        cmd.arg("ALL");
        let info = connection
            .route_command(
                &cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .unwrap();

        let info_result: HashMap<String, String> =
            redis::from_owned_redis_value::<HashMap<String, String>>(info).unwrap();
        let get_cmdstat = "cmdstat_get:calls=".to_string();
        let n_get_cmdstat = format!("cmdstat_get:calls={n}");
        let client_az2 = format!("availability-zone:{client_az}");
        let mut matching_entries_count: usize = 0;

        for value in info_result.values() {
            if value.contains(&get_cmdstat) {
                if value.contains(&client_az) && value.contains(&n_get_cmdstat) {
                    matching_entries_count += 1;
                } else {
                    panic!(
                        "Invalid entry found: {value}. Expected cmdstat_get:calls={n} and availability_zone={client_az2}");
                }
            }
        }

        assert_eq!(
            (matching_entries_count.try_into() as Result<u16, _>).unwrap(),
            primary_in_same_az,
            "Test failed: expected exactly '{primary_in_same_az}' entries with '{get_cmdstat}' and '{client_az}', found {matching_entries_count}"
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_basic_eval() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let res: String = cmd("EVAL")
                .arg(r#"redis.call("SET", KEYS[1], ARGV[1]); return redis.call("GET", KEYS[1])"#)
                .arg(1)
                .arg("key")
                .arg("test")
                .query_async(&mut connection)
                .await?;
            assert_eq!(res, "test");
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_route_flush_to_specific_node() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let _: () = connection.set("foo", "bar").await.unwrap();
            let _: () = connection.set("bar", "foo").await.unwrap();

            let res: String = connection.get("foo").await.unwrap();
            assert_eq!(res, "bar".to_string());
            let res2: Option<String> = connection.get("bar").await.unwrap();
            assert_eq!(res2, Some("foo".to_string()));

            let route =
                redis::cluster_routing::Route::new(1, redis::cluster_routing::SlotAddr::Master);
            let single_node_route =
                redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(route);
            let routing = RoutingInfo::SingleNode(single_node_route);
            assert_eq!(
                connection
                    .route_command(&redis::cmd("FLUSHALL"), routing)
                    .await
                    .unwrap(),
                Value::Okay
            );
            let res: String = connection.get("foo").await.unwrap();
            assert_eq!(res, "bar".to_string());
            let res2: Option<String> = connection.get("bar").await.unwrap();
            assert_eq!(res2, None);
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_route_flush_to_node_by_address() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let mut cmd = redis::cmd("INFO");
            // The other sections change with time.
            // TODO - after we remove support of redis 6, we can add more than a single section - .arg("Persistence").arg("Memory").arg("Replication")
            cmd.arg("Clients");
            let value = connection
                .route_command(
                    &cmd,
                    RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
                )
                .await
                .unwrap();

            let info_by_address = from_owned_redis_value::<HashMap<String, String>>(value).unwrap();
            // find the info of the first returned node
            let (address, info) = info_by_address.into_iter().next().unwrap();
            let mut split_address = address.split(':');
            let host = split_address.next().unwrap().to_string();
            let port = split_address.next().unwrap().parse().unwrap();

            let value = connection
                .route_command(
                    &cmd,
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress { host, port }),
                )
                .await
                .unwrap();
            let new_info = from_owned_redis_value::<String>(value).unwrap();

            assert_eq!(new_info, info);
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_route_info_to_nodes() {
        let cluster = TestClusterContext::new(12, 1);

        let split_to_addresses_and_info = |res| -> (Vec<String>, Vec<String>) {
            if let Value::Map(values) = res {
                let mut pairs: Vec<_> = values
                    .into_iter()
                    .map(|(key, value)| {
                        (
                            redis::from_redis_value::<String>(&key).unwrap(),
                            redis::from_redis_value::<String>(&value).unwrap(),
                        )
                    })
                    .collect();
                pairs.sort_by(|(address1, _), (address2, _)| address1.cmp(address2));
                pairs.into_iter().unzip()
            } else {
                unreachable!("{:?}", res);
            }
        };

        block_on_all(async move {
            let cluster_addresses: Vec<_> = cluster
                .cluster
                .servers
                .iter()
                .map(|server| server.connection_info())
                .collect();
            let client = ClusterClient::builder(cluster_addresses.clone())
                .read_from_replicas()
                .build()?;
            let mut connection = client.get_async_connection(None, None).await?;

            let route_to_all_nodes = redis::cluster_routing::MultipleNodeRoutingInfo::AllNodes;
            let routing = RoutingInfo::MultiNode((route_to_all_nodes, None));
            let res = connection
                .route_command(&redis::cmd("INFO"), routing)
                .await
                .unwrap();
            let (addresses, infos) = split_to_addresses_and_info(res);

            let mut cluster_addresses: Vec<_> = cluster_addresses
                .into_iter()
                .map(|info| info.addr.to_string())
                .collect();
            cluster_addresses.sort();

            assert_eq!(addresses.len(), 12);
            assert_eq!(addresses, cluster_addresses);
            assert_eq!(infos.len(), 12);
            for i in 0..12 {
                let split: Vec<_> = addresses[i].split(':').collect();
                assert!(infos[i].contains(&format!("tcp_port:{}", split[1])));
            }

            let route_to_all_primaries =
                redis::cluster_routing::MultipleNodeRoutingInfo::AllMasters;
            let routing = RoutingInfo::MultiNode((route_to_all_primaries, None));
            let res = connection
                .route_command(&redis::cmd("INFO"), routing)
                .await
                .unwrap();
            let (addresses, infos) = split_to_addresses_and_info(res);
            assert_eq!(addresses.len(), 6);
            assert_eq!(infos.len(), 6);
            // verify that all primaries have the correct port & host, and are marked as primaries.
            for i in 0..6 {
                assert!(cluster_addresses.contains(&addresses[i]));
                let split: Vec<_> = addresses[i].split(':').collect();
                assert!(infos[i].contains(&format!("tcp_port:{}", split[1])));
                assert!(infos[i].contains("role:primary") || infos[i].contains("role:master"));
            }

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_resp3() {
        if use_protocol() == ProtocolVersion::RESP2 {
            return;
        }
        block_on_all(async move {
            let cluster = TestClusterContext::new(3, 0);

            let mut connection = cluster.async_connection(None).await;

            let hello: HashMap<String, Value> = redis::cmd("HELLO")
                .query_async(&mut connection)
                .await
                .unwrap();
            assert_eq!(hello.get("proto").unwrap(), &Value::Int(3));

            let _: () = connection.hset("hash", "foo", "baz").await.unwrap();
            let _: () = connection.hset("hash", "bar", "foobar").await.unwrap();
            let result: Value = connection.hgetall("hash").await.unwrap();

            assert_eq!(
                result,
                Value::Map(vec![
                    (
                        Value::BulkString("foo".as_bytes().to_vec()),
                        Value::BulkString("baz".as_bytes().to_vec())
                    ),
                    (
                        Value::BulkString("bar".as_bytes().to_vec()),
                        Value::BulkString("foobar".as_bytes().to_vec())
                    )
                ])
            );

            Ok(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_basic_pipe() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let mut pipe = redis::pipe();
            pipe.add_command(cmd("SET").arg("test").arg("test_data").clone());
            pipe.add_command(cmd("SET").arg("{test}3").arg("test_data3").clone());
            pipe.query_async::<_, ()>(&mut connection).await?;
            let res: String = connection.get("test").await?;
            assert_eq!(res, "test_data");
            let res: String = connection.get("{test}3").await?;
            assert_eq!(res, "test_data3");
            Ok::<_, RedisError>(())
        })
        .unwrap()
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_multi_shard_commands() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            let res: String = connection
                .mset(&[("foo", "bar"), ("bar", "foo"), ("baz", "bazz")])
                .await?;
            assert_eq!(res, "OK");
            let res: Vec<String> = connection.mget(&["baz", "foo", "bar"]).await?;
            assert_eq!(res, vec!["bazz", "bar", "foo"]);
            Ok::<_, RedisError>(())
        })
        .unwrap()
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_basic_failover() {
        block_on_all(async move {
            test_failover(&TestClusterContext::new(6, 1), 10, 123, false).await;
            Ok::<_, RedisError>(())
        })
        .unwrap()
    }

    async fn do_failover(
        redis: &mut redis::aio::MultiplexedConnection,
    ) -> Result<(), anyhow::Error> {
        cmd("CLUSTER")
            .arg("FAILOVER")
            .query_async::<_, ()>(redis)
            .await?;
        Ok(())
    }

    // parameter `_mtls_enabled` can only be used if `feature = tls-rustls` is active
    #[allow(dead_code)]
    async fn test_failover(
        env: &TestClusterContext,
        requests: i32,
        value: i32,
        _mtls_enabled: bool,
    ) {
        let completed = Arc::new(AtomicI32::new(0));

        let connection = env.async_connection(None).await;
        let mut node_conns: Vec<MultiplexedConnection> = Vec::new();

        'outer: loop {
            node_conns.clear();
            let cleared_nodes = async {
                for server in env.cluster.iter_servers() {
                    let addr = server.client_addr();

                    let client = build_single_client(
                        server.connection_info(),
                        &server.tls_paths,
                        _mtls_enabled,
                    )
                    .unwrap_or_else(|e| panic!("Failed to connect to '{addr}': {e}"));

                    let mut conn = client
                        .get_multiplexed_async_connection(GlideConnectionOptions::default())
                        .await
                        .unwrap_or_else(|e| panic!("Failed to get connection: {e}"));

                    let info: InfoDict = redis::Cmd::new()
                        .arg("INFO")
                        .query_async(&mut conn)
                        .await
                        .expect("INFO");
                    let role: String = info.get("role").expect("cluster role");

                    if role == "master" {
                        tokio::time::timeout(std::time::Duration::from_secs(3), async {
                            Ok(redis::Cmd::new()
                                .arg("FLUSHALL")
                                .query_async::<_, ()>(&mut conn)
                                .await?)
                        })
                        .await
                        .unwrap_or_else(|err| Err(anyhow::Error::from(err)))?;
                    }

                    node_conns.push(conn);
                }
                Ok::<_, anyhow::Error>(())
            }
            .await;
            match cleared_nodes {
                Ok(()) => break 'outer,
                Err(err) => {
                    // Failed to clear the databases, retry
                    tracing::warn!("{}", err);
                }
            }
        }

        (0..requests + 1)
            .map(|i| {
                let mut connection = connection.clone();
                let mut node_conns = node_conns.clone();
                let completed = completed.clone();
                async move {
                    if i == requests / 2 {
                        // Failover all the nodes, error only if all the failover requests error
                        let mut results = future::join_all(
                            node_conns
                                .iter_mut()
                                .map(|conn| Box::pin(do_failover(conn))),
                        )
                        .await;
                        if results.iter().all(|res| res.is_err()) {
                            results.pop().unwrap()
                        } else {
                            Ok::<_, anyhow::Error>(())
                        }
                    } else {
                        let key = format!("test-{value}-{i}");
                        cmd("SET")
                            .arg(&key)
                            .arg(i)
                            .clone()
                            .query_async::<_, ()>(&mut connection)
                            .await?;
                        let res: i32 = cmd("GET")
                            .arg(key)
                            .clone()
                            .query_async(&mut connection)
                            .await?;
                        assert_eq!(res, i);
                        completed.fetch_add(1, Ordering::SeqCst);
                        Ok::<_, anyhow::Error>(())
                    }
                }
            })
            .collect::<stream::FuturesUnordered<_>>()
            .try_collect::<()>()
            .await
            .unwrap_or_else(|e| panic!("{e}"));

        assert_eq!(
            completed.load(Ordering::SeqCst),
            requests,
            "Some requests never completed!"
        );
    }

    static ERROR: Lazy<AtomicBool> = Lazy::new(Default::default);

    #[derive(Clone)]
    struct ErrorConnection {
        inner: MultiplexedConnection,
    }

    impl Connect for ErrorConnection {
        fn connect<'a, T>(
            info: T,
            response_timeout: std::time::Duration,
            connection_timeout: std::time::Duration,
            socket_addr: Option<SocketAddr>,
            glide_connection_options: GlideConnectionOptions,
        ) -> RedisFuture<'a, (Self, Option<IpAddr>)>
        where
            T: IntoConnectionInfo + Send + 'a,
        {
            Box::pin(async move {
                let (inner, _ip) = MultiplexedConnection::connect(
                    info,
                    response_timeout,
                    connection_timeout,
                    socket_addr,
                    glide_connection_options,
                )
                .await?;
                Ok((ErrorConnection { inner }, None))
            })
        }
    }

    impl ConnectionLike for ErrorConnection {
        fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
            if ERROR.load(Ordering::SeqCst) {
                Box::pin(async move { Err(RedisError::from((redis::ErrorKind::Moved, "ERROR"))) })
            } else {
                self.inner.req_packed_command(cmd)
            }
        }

        fn req_packed_commands<'a>(
            &'a mut self,
            pipeline: &'a redis::Pipeline,
            offset: usize,
            count: usize,
            pipeline_retry_strategy: Option<PipelineRetryStrategy>,
        ) -> RedisFuture<'a, Vec<Value>> {
            self.inner
                .req_packed_commands(pipeline, offset, count, pipeline_retry_strategy)
        }

        fn get_db(&self) -> i64 {
            self.inner.get_db()
        }

        fn is_closed(&self) -> bool {
            true
        }
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_error_in_inner_connection() {
        let cluster = TestClusterContext::new(3, 0);

        block_on_all(async move {
            let mut con = cluster.async_generic_connection::<ErrorConnection>().await;

            ERROR.store(false, Ordering::SeqCst);
            let r: Option<i32> = con.get("test").await?;
            assert_eq!(r, None::<i32>);

            ERROR.store(true, Ordering::SeqCst);

            let result: RedisResult<()> = con.get("test").await;
            assert_eq!(
                result,
                Err(RedisError::from((redis::ErrorKind::Moved, "ERROR")))
            );

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_can_connect_to_server_that_sends_cluster_slots_without_host_name() {
        let name =
            "test_async_cluster_can_connect_to_server_that_sends_cluster_slots_without_host_name";

        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::new(name, move |cmd: &[u8], _| {
            if contains_slice(cmd, b"PING") {
                Err(Ok(Value::SimpleString("OK".into())))
            } else if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                Err(Ok(Value::Array(vec![Value::Array(vec![
                    Value::Int(0),
                    Value::Int(16383),
                    Value::Array(vec![
                        Value::BulkString("".as_bytes().to_vec()),
                        Value::Int(6379),
                    ]),
                ])])))
            } else {
                Err(Ok(Value::Nil))
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Value>(&mut connection),
        );

        assert_eq!(value, Ok(Value::Nil));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_can_connect_to_server_that_sends_cluster_slots_with_null_host_name() {
        let name =
            "test_async_cluster_can_connect_to_server_that_sends_cluster_slots_with_null_host_name";

        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::new(name, move |cmd: &[u8], _| {
            if contains_slice(cmd, b"PING") {
                Err(Ok(Value::SimpleString("OK".into())))
            } else if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                Err(Ok(Value::Array(vec![Value::Array(vec![
                    Value::Int(0),
                    Value::Int(16383),
                    Value::Array(vec![Value::Nil, Value::Int(6379)]),
                ])])))
            } else {
                Err(Ok(Value::Nil))
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Value>(&mut connection),
        );

        assert_eq!(value, Ok(Value::Nil));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_cannot_connect_to_server_with_unknown_host_name() {
        let name = "test_async_cluster_cannot_connect_to_server_with_unknown_host_name";
        let handler = move |cmd: &[u8], _| {
            if contains_slice(cmd, b"PING") {
                Err(Ok(Value::SimpleString("OK".into())))
            } else if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                Err(Ok(Value::Array(vec![Value::Array(vec![
                    Value::Int(0),
                    Value::Int(16383),
                    Value::Array(vec![
                        Value::BulkString("?".as_bytes().to_vec()),
                        Value::Int(6379),
                    ]),
                ])])))
            } else {
                Err(Ok(Value::Nil))
            }
        };
        let client_builder = ClusterClient::builder(vec![&*format!("redis://{name}")]);
        let client: ClusterClient = client_builder.build().unwrap();
        let _handler = MockConnectionBehavior::register_new(name, Arc::new(handler));
        let connection = client.get_generic_connection::<MockConnection>(None);
        assert!(connection.is_err());
        let err = connection.err().unwrap();
        assert!(err
            .to_string()
            .contains("Error parsing slots: No healthy node found"))
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_can_connect_to_server_that_sends_cluster_slots_with_partial_nodes_with_unknown_host_name(
    ) {
        let name = "test_async_cluster_can_connect_to_server_that_sends_cluster_slots_with_partial_nodes_with_unknown_host_name";

        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::new(name, move |cmd: &[u8], _| {
            if contains_slice(cmd, b"PING") {
                Err(Ok(Value::SimpleString("OK".into())))
            } else if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                Err(Ok(Value::Array(vec![
                    Value::Array(vec![
                        Value::Int(0),
                        Value::Int(7000),
                        Value::Array(vec![
                            Value::BulkString(name.as_bytes().to_vec()),
                            Value::Int(6379),
                        ]),
                    ]),
                    Value::Array(vec![
                        Value::Int(7001),
                        Value::Int(16383),
                        Value::Array(vec![
                            Value::BulkString("?".as_bytes().to_vec()),
                            Value::Int(6380),
                        ]),
                    ]),
                ])))
            } else {
                Err(Ok(Value::Nil))
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Value>(&mut connection),
        );

        assert_eq!(value, Ok(Value::Nil));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_retries() {
        let name = "tryagain";

        let requests = atomic::AtomicUsize::new(0);
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(5),
            name,
            move |cmd: &[u8], _| {
                respond_startup(name, cmd)?;

                match requests.fetch_add(1, atomic::Ordering::SeqCst) {
                    0..=4 => Err(parse_redis_value(b"-TRYAGAIN mock\r\n")),
                    _ => Err(Ok(Value::BulkString(b"123".to_vec()))),
                }
            },
        );

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_tryagain_exhaust_retries() {
        let name = "tryagain_exhaust_retries";

        let requests = Arc::new(atomic::AtomicUsize::new(0));

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(2),
            name,
            {
                let requests = requests.clone();
                move |cmd: &[u8], _| {
                    respond_startup(name, cmd)?;
                    requests.fetch_add(1, atomic::Ordering::SeqCst);
                    Err(parse_redis_value(b"-TRYAGAIN mock\r\n"))
                }
            },
        );

        let result = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        match result {
            Ok(_) => panic!("result should be an error"),
            Err(e) => match e.kind() {
                ErrorKind::TryAgain => {}
                _ => panic!("Expected TryAgain but got {:?}", e.kind()),
            },
        }
        assert_eq!(requests.load(atomic::Ordering::SeqCst), 3);
    }

    // Obtain the view index associated with the node with [called_port] port
    fn get_node_view_index(num_of_views: usize, ports: &Vec<u16>, called_port: u16) -> usize {
        let port_index = ports
            .iter()
            .position(|&p| p == called_port)
            .unwrap_or_else(|| {
                panic!(
                    "CLUSTER SLOTS was called with unknown port: {called_port}; Known ports: {ports:?}"
                )
            });
        // If we have less views than nodes, use the last view
        if port_index < num_of_views {
            port_index
        } else {
            num_of_views - 1
        }
    }
    #[test]
    #[serial_test::serial]
    fn test_async_cluster_move_error_when_new_node_is_added() {
        let name = "rebuild_with_extra_nodes";

        let requests = atomic::AtomicUsize::new(0);
        let started = atomic::AtomicBool::new(false);
        let refreshed_map = HashMap::from([
            (6379, atomic::AtomicBool::new(false)),
            (6380, atomic::AtomicBool::new(false)),
        ]);

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::new(name, move |cmd: &[u8], port| {
            if !started.load(atomic::Ordering::SeqCst) {
                respond_startup(name, cmd)?;
            }
            started.store(true, atomic::Ordering::SeqCst);

            if contains_slice(cmd, b"PING") || contains_slice(cmd, b"SETNAME") {
                return Err(Ok(Value::SimpleString("OK".into())));
            }

            let i = requests.fetch_add(1, atomic::Ordering::SeqCst);

            let is_get_cmd = contains_slice(cmd, b"GET");
            let get_response = Err(Ok(Value::BulkString(b"123".to_vec())));
            match i {
                // Respond that the key exists on a node that does not yet have a connection:
                0 => Err(parse_redis_value(
                    format!("-MOVED 123 {name}:6380\r\n").as_bytes(),
                )),
                _ => {
                    if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                        // Should not attempt to refresh slots more than once,
                        // so we expect a single CLUSTER NODES request for each node
                        assert!(!refreshed_map
                            .get(&port)
                            .unwrap()
                            .swap(true, Ordering::SeqCst));
                        Err(Ok(Value::Array(vec![
                            Value::Array(vec![
                                Value::Int(0),
                                Value::Int(1),
                                Value::Array(vec![
                                    Value::BulkString(name.as_bytes().to_vec()),
                                    Value::Int(6379),
                                ]),
                            ]),
                            Value::Array(vec![
                                Value::Int(2),
                                Value::Int(16383),
                                Value::Array(vec![
                                    Value::BulkString(name.as_bytes().to_vec()),
                                    Value::Int(6380),
                                ]),
                            ]),
                        ])))
                    } else {
                        assert_eq!(port, 6380);
                        assert!(is_get_cmd, "{:?}", std::str::from_utf8(cmd));
                        get_response
                    }
                }
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    fn test_async_cluster_refresh_topology_after_moved_assert_get_succeed_and_expected_retries(
        slots_config_vec: Vec<Vec<MockSlotRange>>,
        ports: Vec<u16>,
        has_a_majority: bool,
    ) {
        assert!(!ports.is_empty() && !slots_config_vec.is_empty());
        let name = "refresh_topology_moved";
        let num_of_nodes = ports.len();
        let requests = atomic::AtomicUsize::new(0);
        let started = atomic::AtomicBool::new(false);
        let refresh_calls = Arc::new(atomic::AtomicUsize::new(0));
        let refresh_calls_cloned = refresh_calls.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                // Disable the rate limiter to refresh slots immediately on all MOVED errors.
                .slots_refresh_rate_limit(Duration::from_secs(0), 0),
            name,
            move |cmd: &[u8], port| {
                if !started.load(atomic::Ordering::SeqCst) {
                    respond_startup_with_replica_using_config(
                        name,
                        cmd,
                        Some(slots_config_vec[0].clone()),
                    )?;
                }
                started.store(true, atomic::Ordering::SeqCst);

                if contains_slice(cmd, b"PING") || contains_slice(cmd, b"SETNAME") {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                let i = requests.fetch_add(1, atomic::Ordering::SeqCst);
                let is_get_cmd = contains_slice(cmd, b"GET");
                let get_response = Err(Ok(Value::BulkString(b"123".to_vec())));
                let moved_node = ports[0];
                match i {
                    // Respond that the key exists on a node that does not yet have a connection:
                    0 => Err(parse_redis_value(
                        format!("-MOVED 123 {name}:{moved_node}\r\n").as_bytes(),
                    )),
                    _ => {
                        if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                            refresh_calls_cloned.fetch_add(1, atomic::Ordering::SeqCst);
                            let view_index =
                                get_node_view_index(slots_config_vec.len(), &ports, port);
                            Err(Ok(create_topology_from_config(
                                name,
                                slots_config_vec[view_index].clone(),
                            )))
                        } else {
                            assert_eq!(port, moved_node);
                            assert!(is_get_cmd, "{:?}", std::str::from_utf8(cmd));
                            get_response
                        }
                    }
                }
            },
        );
        runtime.block_on(async move {
        let res = cmd("GET")
            .arg("test")
            .query_async::<_, Option<i32>>(&mut connection)
            .await;
        assert_eq!(res, Ok(Some(123)));
        // If there is a majority in the topology views, or if it's a 2-nodes cluster, we shall be able to calculate the topology on the first try,
        // so each node will be queried only once with CLUSTER SLOTS.
        // Otherwise, if we don't have a majority, we expect to see the refresh_slots function being called with the maximum retry number.
        let expected_calls = if has_a_majority || num_of_nodes == 2 {num_of_nodes} else {DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES * num_of_nodes};
        let mut refreshed_calls = 0;
        for _ in 0..100 {
            refreshed_calls = refresh_calls.load(atomic::Ordering::Relaxed);
            if refreshed_calls == expected_calls {
                return;
            } else {
                let sleep_duration = core::time::Duration::from_millis(100);
                #[cfg(feature = "tokio-comp")]
                tokio::time::sleep(sleep_duration).await;
            }
        }
        panic!("Failed to reach to the expected topology refresh retries. Found={refreshed_calls}, Expected={expected_calls}")
    });
    }

    fn test_async_cluster_refresh_slots_rate_limiter_helper(
        slots_config_vec: Vec<Vec<MockSlotRange>>,
        ports: Vec<u16>,
        should_skip: bool,
    ) {
        // This test queries GET, which returns a MOVED error. If `should_skip` is true,
        // it indicates that we should skip refreshing slots because the specified time
        // duration since the last refresh slots call has not yet passed. In this case,
        // we expect CLUSTER SLOTS not to be called on the nodes after receiving the
        // MOVED error.

        // If `should_skip` is false, we verify that if the MOVED error occurs after the
        // time duration of the rate limiter has passed, the refresh slots operation
        // should not be skipped. We assert this by expecting calls to CLUSTER SLOTS on
        // all nodes.
        let test_name = format!(
            "test_async_cluster_refresh_slots_rate_limiter_helper_{}",
            if should_skip {
                "should_skip"
            } else {
                "not_skipping_waiting_time_passed"
            }
        );

        let requests = atomic::AtomicUsize::new(0);
        let started = atomic::AtomicBool::new(false);
        let refresh_calls = Arc::new(atomic::AtomicUsize::new(0));
        let refresh_calls_cloned = Arc::clone(&refresh_calls);
        let wait_duration = Duration::from_millis(10);
        let num_of_nodes = ports.len();

        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{test_name}")])
                .slots_refresh_rate_limit(wait_duration, 0),
            test_name.clone().as_str(),
            move |cmd: &[u8], port| {
                if !started.load(atomic::Ordering::SeqCst) {
                    respond_startup_with_replica_using_config(
                        test_name.as_str(),
                        cmd,
                        Some(slots_config_vec[0].clone()),
                    )?;
                    started.store(true, atomic::Ordering::SeqCst);
                }

                if contains_slice(cmd, b"PING") {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                let i = requests.fetch_add(1, atomic::Ordering::SeqCst);
                let is_get_cmd = contains_slice(cmd, b"GET");
                let get_response = Err(Ok(Value::BulkString(b"123".to_vec())));
                let moved_node = ports[0];
                match i {
                    // The first request calls are the starting calls for each GET command where we want to respond with MOVED error
                    0 => {
                        if !should_skip {
                            // Wait for the wait duration to pass
                            std::thread::sleep(wait_duration.add(Duration::from_millis(10)));
                        }
                        Err(parse_redis_value(
                            format!("-MOVED 123 {test_name}:{moved_node}\r\n").as_bytes(),
                        ))
                    }
                    _ => {
                        if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                            refresh_calls_cloned.fetch_add(1, atomic::Ordering::SeqCst);
                            let view_index =
                                get_node_view_index(slots_config_vec.len(), &ports, port);
                            Err(Ok(create_topology_from_config(
                                test_name.as_str(),
                                slots_config_vec[view_index].clone(),
                            )))
                        } else {
                            // Even if the slots weren't refreshed we still expect the command to be
                            // routed by the redirect host and port it received in the moved error
                            assert_eq!(port, moved_node);
                            assert!(is_get_cmd, "{:?}", std::str::from_utf8(cmd));
                            get_response
                        }
                    }
                }
            },
        );

        runtime.block_on(async move {
            // First GET request should raise MOVED error and then refresh slots
            let res = cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection)
                .await;
            assert_eq!(res, Ok(Some(123)));

            // We should skip is false, we should call CLUSTER SLOTS once per node
            let expected_calls = if should_skip {
                0
            } else {
                num_of_nodes
            };
            for _ in 0..4 {
                if refresh_calls.load(atomic::Ordering::Relaxed) == expected_calls {
                    return Ok::<_, RedisError>(());
                }
                let _ = sleep(Duration::from_millis(50).into()).await;
            }
            panic!("Refresh slots wasn't called as expected!\nExpected CLUSTER SLOTS calls: {}, actual calls: {:?}", expected_calls, refresh_calls.load(atomic::Ordering::Relaxed));
        }).unwrap()
    }

    fn test_async_cluster_refresh_topology_in_client_init_get_succeed(
        slots_config_vec: Vec<Vec<MockSlotRange>>,
        ports: Vec<u16>,
    ) {
        assert!(!ports.is_empty() && !slots_config_vec.is_empty());
        let name = "refresh_topology_client_init";
        let started = atomic::AtomicBool::new(false);
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder::<String>(
                ports
                    .iter()
                    .map(|port| format!("redis://{name}:{port}"))
                    .collect::<Vec<_>>(),
            ),
            name,
            move |cmd: &[u8], port| {
                let is_started = started.load(atomic::Ordering::SeqCst);
                if !is_started {
                    if contains_slice(cmd, b"PING") || contains_slice(cmd, b"SETNAME") {
                        return Err(Ok(Value::SimpleString("OK".into())));
                    } else if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                        let view_index = get_node_view_index(slots_config_vec.len(), &ports, port);
                        return Err(Ok(create_topology_from_config(
                            name,
                            slots_config_vec[view_index].clone(),
                        )));
                    } else if contains_slice(cmd, b"READONLY") {
                        return Err(Ok(Value::SimpleString("OK".into())));
                    }
                }
                started.store(true, atomic::Ordering::SeqCst);
                if contains_slice(cmd, b"PING") {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                let is_get_cmd = contains_slice(cmd, b"GET");
                let get_response = Err(Ok(Value::BulkString(b"123".to_vec())));
                {
                    assert!(is_get_cmd, "{:?}", std::str::from_utf8(cmd));
                    get_response
                }
            },
        );
        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    fn generate_topology_view(
        ports: &[u16],
        interval: usize,
        full_slot_coverage: bool,
    ) -> Vec<MockSlotRange> {
        let mut slots_res = vec![];
        let mut start_pos: usize = 0;
        for (idx, port) in ports.iter().enumerate() {
            let end_pos: usize = if idx == ports.len() - 1 && full_slot_coverage {
                16383
            } else {
                start_pos + interval
            };
            let mock_slot = MockSlotRange {
                primary_port: *port,
                replica_ports: vec![],
                slot_range: (start_pos as u16..end_pos as u16),
            };
            slots_res.push(mock_slot);
            start_pos = end_pos + 1;
        }
        slots_res
    }

    fn get_ports(num_of_nodes: usize) -> Vec<u16> {
        (6379_u16..6379 + num_of_nodes as u16).collect()
    }

    fn get_no_majority_topology_view(ports: &[u16]) -> Vec<Vec<MockSlotRange>> {
        let mut result = vec![];
        let mut full_coverage = true;
        for i in 0..ports.len() {
            result.push(generate_topology_view(ports, i + 1, full_coverage));
            full_coverage = !full_coverage;
        }
        result
    }

    fn get_topology_with_majority(ports: &[u16]) -> Vec<Vec<MockSlotRange>> {
        let view: Vec<MockSlotRange> = generate_topology_view(ports, 10, true);
        let result: Vec<_> = ports.iter().map(|_| view.clone()).collect();
        result
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_topology_after_moved_error_all_nodes_agree_get_succeed() {
        let ports = get_ports(3);
        test_async_cluster_refresh_topology_after_moved_assert_get_succeed_and_expected_retries(
            get_topology_with_majority(&ports),
            ports,
            true,
        );
    }

    #[test]
    #[serial_test::serial]
    /// This test verifies the behavior of refreshing topology from initial nodes.
    ///
    /// This test simulates a network partition in a 3-node cluster to verify how
    /// `refresh_topology_from_initial_nodes` affects cluster topology discovery:
    ///
    /// Test flow:
    /// 1. Creates a 3-node cluster and connects via node_0
    /// 2. Verifies initial connectivity to all nodes
    /// 3. Creates a network partition:
    ///    - Makes nodes 1 & 2 forget node_0
    ///    - Makes node_0 forget nodes 1 & 2
    /// 4. Triggers topology refresh via MOVED error
    /// 5. Verifies final cluster view based on refresh mode
    ///
    /// Expected outcomes:
    /// - When refresh_from_initial = true:
    ///   * Only sees node_0 (initial node)
    ///   * PING returns 1 response
    ///   * Reason: refreshes topology solely from initial node, which only knows itself.
    /// - When refresh_from_initial = false:
    ///   * Sees nodes 1 & 2 (majority)
    ///   * PING returns 2 responses
    ///   * Reason: refreshes topology from internal cluster view (all 3 nodes), which reflects majority decision.
    ///
    /// This test ensures the client correctly follows either:
    /// - initial node's view (when refresh_from_initial = true)
    /// - Internal cluster view (when refresh_from_initial = false)
    fn test_refresh_topology_from_initial_nodes() {
        for refresh_topology_from_initial_nodes in [false, true] {
            let cluster = TestClusterContext::new(3, 0);

            let _ = block_on_all(async move {
                // Extract node_0 address for later
                let (node_0_host, node_0_port) = match cluster.nodes[0].addr {
                    ConnectionAddr::Tcp(ref host, port) => (host.clone(), port),
                    ConnectionAddr::TcpTls { ref host, port, .. } => (host.clone(), port),
                    _ => panic!("Unsupported connection address"),
                };

                // Discover topology
                let cluster_nodes = cluster.get_cluster_nodes().await;
                let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

                // Partition cluster into node_0 and the rest
                let (node_0_info, rest): (Vec<_>, Vec<_>) = slot_distribution
                    .into_iter()
                    .partition(|(_, addr, port, _)| {
                        addr == &node_0_host && port == &node_0_port.to_string()
                    });

                let node_0_id = node_0_info[0].0.clone();
                let rest_ids: Vec<_> = rest.iter().map(|(id, _, _, _)| id.clone()).collect();

                // Connect through node0 (use node0 as initial node)
                let client = redis::cluster::ClusterClientBuilder::new(vec![cluster.nodes[0].clone()])
                        .use_protocol(use_protocol())
                        // Force slots refresh to be immediate after MOVED error
                        .slots_refresh_rate_limit(Duration::from_secs(0), 0)
                        .refresh_topology_from_initial_nodes(refresh_topology_from_initial_nodes)
                        .build()
                        .unwrap();

                let mut conn = client.get_async_connection(None, None).await.unwrap();

                // Disable full coverage requirement
                let _ = conn
                    .route_command(
                        &cmd("CONFIG")
                            .arg("SET")
                            .arg("cluster-require-full-coverage")
                            .arg("no"),
                        RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
                    )
                    .await
                    .expect("Failed to disable full coverage requirement");

                // Check that all nodes are reachable
                let ping = conn
                    .route_command(
                        &cmd("PING"),
                        RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
                    )
                    .await
                    .expect("Failed to PING all nodes");

                let res = match ping {
                    Value::Map(map) => map,
                    _ => panic!("Unexpected PING response: {:?}", ping),
                };

                assert_eq!(res.len(), 3, "Expected exactly 3 PING responses");

                // Make all nodes forget node_0
                let mut forget_node_0 = cmd("CLUSTER");
                forget_node_0.arg("FORGET").arg(&node_0_id);

                let _ = conn
                    .route_command(
                        &forget_node_0,
                        RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
                    )
                    .await;

                // Instruct node_0 to forget all other nodes.
                // This fully partitions the cluster, ensuring node_0 has no knowledge of the remaining nodes.
                // This step is necessary so that after a topology refresh, node_0's view excludes the rest,
                // preventing any residual cluster state due to gossip propagation delays.
                for rest_id in &rest_ids {
                    let mut forget_rest = cmd("CLUSTER");
                    forget_rest.arg("FORGET").arg(rest_id);

                    let _ = conn
                        .route_command(
                            &forget_rest,
                            RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                                host: node_0_host.clone(),
                                port: node_0_port,
                            }),
                        )
                        .await
                        .expect("Failed to make node 0 forget");
                }

                // Force a MOVED error by routing key1 through the wrong slot, this should trigger refresh slots immediately
                // key1 -> 9189 (node 1)
                // key2 -> 12539 (node 2)
                let _ = conn
                    .route_command(
                        &cmd("GET").arg("key1"),
                        RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                            get_slot("key".as_bytes()),
                            SlotAddr::Master,
                        ))),
                    )
                    .await;

                // Allow some time for the topology to refresh
                sleep(Duration::from_secs(1).into()).await;

                let ping = conn
                    .route_command(
                        &cmd("PING"),
                        RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
                    )
                    .await
                    .unwrap();

                let res = match ping {
                    Value::Map(map) => map,
                    _ => panic!("Unexpected PING response: {:?}", ping),
                };

                let res_size = if refresh_topology_from_initial_nodes {
                    1
                } else {
                    2 // since both node 1 and node 2 forgot about node 0, and we do follow majority decisions
                };

                assert!(
                    res.len() == res_size,
                    "Expected exactly {} PING responses",
                    res_size
                );

                if refresh_topology_from_initial_nodes {
                    assert!(
                        res.iter().any(|(k, _)| k
                            == &Value::BulkString(
                                format!("{}:{}", node_0_host, node_0_port).into_bytes()
                            )),
                        "Expected to see node 0 only"
                    );
                }

                Ok(())
            });
        }
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_topology_in_client_init_all_nodes_agree_get_succeed() {
        let ports = get_ports(3);
        test_async_cluster_refresh_topology_in_client_init_get_succeed(
            get_topology_with_majority(&ports),
            ports,
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_topology_after_moved_error_with_no_majority_get_succeed() {
        for num_of_nodes in 2..4 {
            let ports = get_ports(num_of_nodes);
            test_async_cluster_refresh_topology_after_moved_assert_get_succeed_and_expected_retries(
                get_no_majority_topology_view(&ports),
                ports,
                false,
            );
        }
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_topology_in_client_init_with_no_majority_get_succeed() {
        for num_of_nodes in 2..4 {
            let ports = get_ports(num_of_nodes);
            test_async_cluster_refresh_topology_in_client_init_get_succeed(
                get_no_majority_topology_view(&ports),
                ports,
            );
        }
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_topology_is_not_blocking() {
        // Test: Non-Head-of-Line Blocking During Slot Refresh
        //
        // This test verifies that during cluster topology refresh operations triggered by
        // MOVED errors, the implementation does not exhibit head-of-line blocking behavior.
        // When a client receives a MOVED error (indicating topology changes), it refreshes
        // its slot mapping in the background, allowing other commands to proceed concurrently.
        //
        // The test employs the following strategy to verify the non-blocking behavior:
        //
        // 1. Trigger Slot Refresh: Send a blocking BLPOP command that will receive a MOVED error when
        //    slot 0 is migrated, initiating a topology refresh operation.
        //
        // 2. Atomicly migrate slot and pause clients: Use SET SLOT and CLIENT PAUSE to artificially delay the node's
        //    response during the refresh operation.
        //
        // 3. Verify Non-Blocking Behavior: While the refresh is in progress, send a GET command
        //    to a different node in the cluster. Unlike the blocking implementation, this command
        //    should complete successfully without timing out.
        //
        // This test intentionally demonstrates how topology refresh operations is no longer blocking
        // subsequent commands.

        // Create a cluster with 3 nodes
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder
                    .response_timeout(Duration::from_millis(2000))
                    .slots_refresh_rate_limit(Duration::from_secs(0), 0)
                    .retries(0)
            },
            false,
        );

        block_on_all(async move {
            // STEP 1: Create two separate connections to the same cluster
            let mut client1 = cluster.async_connection(None).await;
            let mut client2 = cluster.async_connection(None).await;

            // STEP 2: Prepare keys that hash to different shards
            let slot0_key = "06S"; // This key should hash to slot 0
            assert_eq!(get_slot(slot0_key.as_bytes()), 0);

            let other_shard_key = "foo"; // This key hashes to slot 12182
            assert_eq!(get_slot(other_shard_key.as_bytes()), 12182);

            // Initialize the keys with values
            let _: () = client1.set(other_shard_key, "value2").await.unwrap();

            // Get node IDs for migration
            let shards_info = client1
                .route_command(
                    cmd("CLUSTER").arg("SHARDS"),
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                        0,
                        SlotAddr::Master,
                    ))),
                )
                .await
                .unwrap();

            let (_, target_node_id) = extract_node_ids_from_shards(shards_info);

            // STEP 3: Launch a background task that will handle the blocking BLPOP and GET commands
            let client1_handle = tokio::spawn(async move {
                // Send BLPOP which will trigger a MOVED error and topology refresh
                // Note, this command will timeout after 2000ms due to response_timeout config
                let blpop_result = client1.blpop::<_, String>(slot0_key, 0.0).await;
                assert!(blpop_result.is_err());

                // Now immediately try to access another key on a different shard

                // Apply timeout from when the call is made, not when it starts executing
                // This GET should time out as it's blocked by the topology refresh
                let get_result = tokio::time::timeout(
                    Duration::from_millis(1000),
                    client1.get::<_, String>(other_shard_key),
                )
                .await;

                // Assert that the GET succeeded (no timeout or error)
                assert!(get_result.is_ok());
                let result = get_result.unwrap().unwrap();
                assert_eq!(result, "value2");

                true
            });

            // STEP 4: Give the BLPOP time to start blocking - it will have 1500 ms to wait for MOVED
            sleep(futures_time::time::Duration::from_millis(500)).await;

            // STEP 5: Trigger migration to cause MOVED error and delay the response
            let mut pipe = redis::pipe();
            pipe.atomic()
                .cmd("CLUSTER")
                .arg("SETSLOT")
                .arg("0")
                .arg("NODE")
                .arg(&target_node_id)
                .ignore()
                .cmd("CLIENT")
                .arg("PAUSE")
                .arg(2000)
                .ignore();

            // Execute migration pipeline on client2
            let _ = client2
                .route_pipeline(
                    &pipe,
                    0,
                    2,
                    Some(SingleNodeRoutingInfo::SpecificNode(Route::new(
                        0,
                        SlotAddr::Master,
                    ))),
                    None,
                )
                .await;

            // STEP 6: Wait for client1 handle to complete
            let result = client1_handle.await.unwrap();

            assert!(
                result,
                "The test should pass, demonstrating non blocking behavior"
            );

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    /// Helper function to extract node IDs from CLUSTER SHARDS response
    fn extract_node_ids_from_shards(shards_info: Value) -> (String, String) {
        let mut node_id_for_slot0 = None;
        let mut other_node_id = None;

        match &shards_info {
            Value::Array(shards) => {
                for shard in shards {
                    if let Value::Array(shard_data) = shard {
                        let mut slots = None;
                        let mut nodes = None;

                        // Extract slots and nodes arrays
                        for i in (0..shard_data.len()).step_by(2) {
                            if i + 1 >= shard_data.len() {
                                continue; // No value for this key
                            }

                            if let Value::BulkString(key_bytes) = &shard_data[i] {
                                let key = String::from_utf8_lossy(key_bytes);
                                if key.contains("slots") {
                                    slots = shard_data.get(i + 1);
                                } else if key.contains("nodes") {
                                    nodes = shard_data.get(i + 1);
                                }
                            }
                        }

                        // Check if this shard has slot 0
                        let has_slot0 = if let Some(Value::Array(slot_ranges)) = slots {
                            if slot_ranges.len() >= 2 {
                                if let (Value::Int(start), Value::Int(end)) =
                                    (&slot_ranges[0], &slot_ranges[1])
                                {
                                    *start <= 0 && *end >= 0
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        } else {
                            false
                        };

                        // Extract node ID if this is the slot 0 shard or we need another node
                        if let Some(Value::Array(nodes_array)) = nodes {
                            if !nodes_array.is_empty() {
                                if let Value::Array(node_data) = &nodes_array[0] {
                                    let mut node_id = None;

                                    // Find the node ID in the node data
                                    for i in (0..node_data.len()).step_by(2) {
                                        if i + 1 >= node_data.len() {
                                            continue;
                                        }

                                        if let Value::BulkString(key_bytes) = &node_data[i] {
                                            let key = String::from_utf8_lossy(key_bytes);
                                            if key.contains("id") {
                                                if let Value::BulkString(id_bytes) =
                                                    &node_data[i + 1]
                                                {
                                                    node_id = Some(
                                                        String::from_utf8_lossy(id_bytes)
                                                            .to_string(),
                                                    );
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                    if let Some(id) = node_id {
                                        if has_slot0 {
                                            node_id_for_slot0 = Some(id);
                                        } else if other_node_id.is_none() {
                                            other_node_id = Some(id);
                                        }
                                    }
                                }
                            }
                        }

                        // Break early if we found both IDs
                        if node_id_for_slot0.is_some() && other_node_id.is_some() {
                            break;
                        }
                    }
                }
            }
            _ => panic!("Unexpected CLUSTER SHARDS response type: {shards_info:?}"),
        }

        if node_id_for_slot0.is_none() {
            panic!("Could not find master node for slot 0 in CLUSTER SHARDS output");
        }

        if other_node_id.is_none() {
            panic!("Could not find another master node in CLUSTER SHARDS output");
        }

        (node_id_for_slot0.unwrap(), other_node_id.unwrap())
    }

    #[test]
    fn test_async_cluster_update_slots_based_on_moved_error_indicates_slot_migration() {
        // This test simulates the scenario where the client receives a MOVED error indicating that a key is now
        // stored on the primary node of another shard.
        // It ensures that the new slot now owned by the primary and its associated replicas.
        let name = "test_async_cluster_update_slots_based_on_moved_error_indicates_slot_migration";
        let slots_config = vec![
            MockSlotRange {
                primary_port: 6379,
                replica_ports: vec![7000],
                slot_range: (0..8000),
            },
            MockSlotRange {
                primary_port: 6380,
                replica_ports: vec![7001],
                slot_range: (8001..16380),
            },
        ];

        let moved_from_port = 6379;
        let moved_to_port = 6380;
        let new_shard_replica_port = 7001;

        // Tracking moved and replica requests for validation
        let moved_requests = Arc::new(atomic::AtomicUsize::new(0));
        let cloned_moved_requests = moved_requests.clone();
        let replica_requests = Arc::new(atomic::AtomicUsize::new(0));
        let cloned_replica_requests = moved_requests.clone();

        // Test key and slot
        let key = "test";
        let key_slot = 6918;

        // Mock environment setup
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                    .slots_refresh_rate_limit(Duration::from_secs(1000000), 0) // Rate limiter to disable slot refresh
                    .read_from_replicas(), // Allow reads from replicas
            name,
            move |cmd: &[u8], port| {
                if contains_slice(cmd, b"PING")
                    || contains_slice(cmd, b"SETNAME")
                    || contains_slice(cmd, b"READONLY")
                {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                    let slots = create_topology_from_config(name, slots_config.clone());
                    return Err(Ok(slots));
                }

                if contains_slice(cmd, b"SET") {
                    if port == moved_to_port {
                        // Simulate primary OK response
                        Err(Ok(Value::SimpleString("OK".into())))
                    } else if port == moved_from_port {
                        // Simulate MOVED error for other port
                        moved_requests.fetch_add(1, Ordering::Relaxed);
                        Err(parse_redis_value(
                            format!("-MOVED {key_slot} {name}:{moved_to_port}\r\n").as_bytes(),
                        ))
                    } else {
                        panic!("unexpected port for SET command: {port:?}.\n
                            Expected one of: moved_to_port={moved_to_port}, moved_from_port={moved_from_port}");
                    }
                } else if contains_slice(cmd, "GET".as_bytes()) {
                    if new_shard_replica_port == port {
                        // Simulate replica response for GET after slot migration
                        replica_requests.fetch_add(1, Ordering::Relaxed);
                        Err(Ok(Value::BulkString(b"123".to_vec())))
                    } else {
                        panic!("unexpected port for GET command: {port:?}, Expected: {new_shard_replica_port:?}");
                    }
                } else {
                    panic!("unexpected command {cmd:?}")
                }
            },
        );

        // First request: Trigger MOVED error and reroute
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Second request: Should be routed directly to the new primary node if the slots map is updated
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Handle slot migration scenario: Ensure the new shard's replicas are accessible
        let value = runtime.block_on(
            cmd("GET")
                .arg(key)
                .query_async::<_, Option<i32>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(123)));
        assert_eq!(cloned_replica_requests.load(Ordering::Relaxed), 1);

        // Assert there was only a single MOVED error
        assert_eq!(cloned_moved_requests.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn test_async_cluster_update_slots_based_on_moved_error_indicates_failover() {
        // This test simulates a failover scenario, where the client receives a MOVED error and the replica becomes the new primary.
        // The test verifies that the client updates the slot mapping to promote the replica to the primary and routes future requests
        // to the new primary, ensuring other slots in the shard are also handled by the new primary.
        let name = "test_async_cluster_update_slots_based_on_moved_error_indicates_failover";
        let slots_config = vec![
            MockSlotRange {
                primary_port: 6379,
                replica_ports: vec![7001],
                slot_range: (0..8000),
            },
            MockSlotRange {
                primary_port: 6380,
                replica_ports: vec![7002],
                slot_range: (8001..16380),
            },
        ];

        let moved_from_port = 6379;
        let moved_to_port = 7001;

        // Tracking moved for validation
        let moved_requests = Arc::new(atomic::AtomicUsize::new(0));
        let cloned_moved_requests = moved_requests.clone();

        // Test key and slot
        let key = "test";
        let key_slot = 6918;

        // Mock environment setup
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .slots_refresh_rate_limit(Duration::from_secs(1000000), 0), // Rate limiter to disable slot refresh
            name,
            move |cmd: &[u8], port| {
                if contains_slice(cmd, b"PING")
                    || contains_slice(cmd, b"SETNAME")
                    || contains_slice(cmd, b"READONLY")
                {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                    let slots = create_topology_from_config(name, slots_config.clone());
                    return Err(Ok(slots));
                }

                if contains_slice(cmd, b"SET") {
                    if port == moved_to_port {
                        // Simulate primary OK response
                        Err(Ok(Value::SimpleString("OK".into())))
                    } else if port == moved_from_port {
                        // Simulate MOVED error for other port
                        moved_requests.fetch_add(1, Ordering::Relaxed);
                        Err(parse_redis_value(
                            format!("-MOVED {key_slot} {name}:{moved_to_port}\r\n").as_bytes(),
                        ))
                    } else {
                        panic!("unexpected port for SET command: {port:?}.\n
                            Expected one of: moved_to_port={moved_to_port}, moved_from_port={moved_from_port}");
                    }
                } else {
                    panic!("unexpected command {cmd:?}")
                }
            },
        );

        // First request: Trigger MOVED error and reroute
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Second request: Should be routed directly to the new primary node if the slots map is updated
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Handle failover scenario: Ensure other slots in the same shard are updated to the new primary
        let key_slot_1044 = "foo2";
        let value = runtime.block_on(
            cmd("SET")
                .arg(key_slot_1044)
                .arg("bar2")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Assert there was only a single MOVED error
        assert_eq!(cloned_moved_requests.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn test_async_cluster_update_slots_based_on_moved_error_indicates_new_primary() {
        // This test simulates the scenario where the client receives a MOVED error indicating that the key now belongs to
        // an entirely new primary node that wasn't previously known. The test verifies that the client correctly adds the new
        // primary node to its slot map and routes future requests to the new node.
        let name = "test_async_cluster_update_slots_based_on_moved_error_indicates_new_primary";
        let slots_config = vec![
            MockSlotRange {
                primary_port: 6379,
                replica_ports: vec![],
                slot_range: (0..8000),
            },
            MockSlotRange {
                primary_port: 6380,
                replica_ports: vec![],
                slot_range: (8001..16380),
            },
        ];

        let moved_from_port = 6379;
        let moved_to_port = 6381;

        // Tracking moved for validation
        let moved_requests = Arc::new(atomic::AtomicUsize::new(0));
        let cloned_moved_requests = moved_requests.clone();

        // Test key and slot
        let key = "test";
        let key_slot = 6918;

        // Mock environment setup
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
            .slots_refresh_rate_limit(Duration::from_secs(1000000), 0) // Rate limiter to disable slot refresh
            .read_from_replicas(), // Allow reads from replicas
            name,
            move |cmd: &[u8], port| {
                if contains_slice(cmd, b"PING")
                    || contains_slice(cmd, b"SETNAME")
                    || contains_slice(cmd, b"READONLY")
                {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                    let slots = create_topology_from_config(name, slots_config.clone());
                    return Err(Ok(slots));
                }

                if contains_slice(cmd, b"SET") {
                    if port == moved_to_port {
                        // Simulate primary OK response
                        Err(Ok(Value::SimpleString("OK".into())))
                    } else if port == moved_from_port {
                        // Simulate MOVED error for other port
                        moved_requests.fetch_add(1, Ordering::Relaxed);
                        Err(parse_redis_value(
                            format!("-MOVED {key_slot} {name}:{moved_to_port}\r\n").as_bytes(),
                        ))
                    } else {
                        panic!("unexpected port for SET command: {port:?}.\n
                    Expected one of: moved_to_port={moved_to_port}, moved_from_port={moved_from_port}");
                    }
                } else if contains_slice(cmd, b"GET") {
                    if moved_to_port == port {
                        // Simulate primary response for GET
                        Err(Ok(Value::BulkString(b"123".to_vec())))
                    } else {
                        panic!(
                            "unexpected port for GET command: {port:?}, Expected: {moved_to_port}"
                        );
                    }
                } else {
                    panic!("unexpected command {cmd:?}")
                }
            },
        );

        // First request: Trigger MOVED error and reroute
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Second request: Should be routed directly to the new primary node if the slots map is updated
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Third request: The new primary should have no replicas so it should be directed to it
        let value = runtime.block_on(
            cmd("GET")
                .arg(key)
                .query_async::<_, Option<i32>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(123)));

        // Assert there was only a single MOVED error
        assert_eq!(cloned_moved_requests.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn test_async_cluster_update_slots_based_on_moved_error_indicates_replica_of_different_shard() {
        // This test simulates a scenario where the client receives a MOVED error indicating that a key
        // has been moved to a replica in a different shard. The replica is then promoted to primary and
        // no longer exists in the shard’s replica set.
        // The test validates that the key gets correctly routed to the new primary and ensures that the
        // shard updates its mapping accordingly, with only one MOVED error encountered during the process.

        let name = "test_async_cluster_update_slots_based_on_moved_error_indicates_replica_of_different_shard";
        let slots_config = vec![
            MockSlotRange {
                primary_port: 6379,
                replica_ports: vec![7000],
                slot_range: (0..8000),
            },
            MockSlotRange {
                primary_port: 6380,
                replica_ports: vec![7001],
                slot_range: (8001..16380),
            },
        ];

        let moved_from_port = 6379;
        let moved_to_port = 7001;
        let primary_shard2 = 6380;

        // Tracking moved for validation
        let moved_requests = Arc::new(atomic::AtomicUsize::new(0));
        let cloned_moved_requests = moved_requests.clone();

        // Test key and slot of the first shard
        let key = "test";
        let key_slot = 6918;

        // Test key of the second shard
        let key_shard2 = "foo"; // slot 12182

        // Mock environment setup
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                    .slots_refresh_rate_limit(Duration::from_secs(1000000), 0) // Rate limiter to disable slot refresh
                    .read_from_replicas(), // Allow reads from replicas
            name,
            move |cmd: &[u8], port| {
                if contains_slice(cmd, b"PING")
                    || contains_slice(cmd, b"SETNAME")
                    || contains_slice(cmd, b"READONLY")
                {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                    let slots = create_topology_from_config(name, slots_config.clone());
                    return Err(Ok(slots));
                }

                if contains_slice(cmd, b"SET") {
                    if port == moved_to_port {
                        // Simulate primary OK response
                        Err(Ok(Value::SimpleString("OK".into())))
                    } else if port == moved_from_port {
                        // Simulate MOVED error for other port
                        moved_requests.fetch_add(1, Ordering::Relaxed);
                        Err(parse_redis_value(
                            format!("-MOVED {key_slot} {name}:{moved_to_port}\r\n").as_bytes(),
                        ))
                    } else {
                        panic!("unexpected port for SET command: {port:?}.\n
                            Expected one of: moved_to_port={moved_to_port}, moved_from_port={moved_from_port}");
                    }
                } else if contains_slice(cmd, b"GET") {
                    if port == primary_shard2 {
                        // Simulate second shard primary response for GET
                        Err(Ok(Value::BulkString(b"123".to_vec())))
                    } else {
                        panic!("unexpected port for GET command: {port:?}, Expected: {primary_shard2:?}");
                    }
                } else {
                    panic!("unexpected command {cmd:?}")
                }
            },
        );

        // First request: Trigger MOVED error and reroute
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Second request: Should be routed directly to the new primary node if the slots map is updated
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Third request: Verify that the promoted replica is no longer part of the second shard replicas by
        // ensuring the response is received from the shard's primary
        let value = runtime.block_on(
            cmd("GET")
                .arg(key_shard2)
                .query_async::<_, Option<i32>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(123)));

        // Assert there was only a single MOVED error
        assert_eq!(cloned_moved_requests.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn test_async_cluster_update_slots_based_on_moved_error_no_change() {
        // This test simulates a scenario where the client receives a MOVED error, but the new primary is the
        // same as the old primary (no actual change). It ensures that no additional slot map
        // updates are required and that the subsequent requests are still routed to the same primary node, with
        // only one MOVED error encountered.
        let name = "test_async_cluster_update_slots_based_on_moved_error_no_change";
        let slots_config = vec![
            MockSlotRange {
                primary_port: 6379,
                replica_ports: vec![7000],
                slot_range: (0..8000),
            },
            MockSlotRange {
                primary_port: 6380,
                replica_ports: vec![7001],
                slot_range: (8001..16380),
            },
        ];

        let moved_from_port = 6379;
        let moved_to_port = 6379;

        // Tracking moved for validation
        let moved_requests = Arc::new(atomic::AtomicUsize::new(0));
        let cloned_moved_requests = moved_requests.clone();

        // Test key and slot of the first shard
        let key = "test";
        let key_slot = 6918;

        // Mock environment setup
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .slots_refresh_rate_limit(Duration::from_secs(1000000), 0), // Rate limiter to disable slot refresh
            name,
            move |cmd: &[u8], port| {
                if contains_slice(cmd, b"PING")
                    || contains_slice(cmd, b"SETNAME")
                    || contains_slice(cmd, b"READONLY")
                {
                    return Err(Ok(Value::SimpleString("OK".into())));
                }

                if contains_slice(cmd, b"CLUSTER") && contains_slice(cmd, b"SLOTS") {
                    let slots = create_topology_from_config(name, slots_config.clone());
                    return Err(Ok(slots));
                }

                if contains_slice(cmd, b"SET") {
                    if port == moved_to_port {
                        if moved_requests.load(Ordering::Relaxed) == 0 {
                            moved_requests.fetch_add(1, Ordering::Relaxed);
                            Err(parse_redis_value(
                                format!("-MOVED {key_slot} {name}:{moved_to_port}\r\n").as_bytes(),
                            ))
                        } else {
                            Err(Ok(Value::SimpleString("OK".into())))
                        }
                    } else {
                        panic!("unexpected port for SET command: {port:?}.\n
                            Expected one of: moved_to_port={moved_to_port}, moved_from_port={moved_from_port}");
                    }
                } else {
                    panic!("unexpected command {cmd:?}")
                }
            },
        );

        // First request: Trigger MOVED error and reroute
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Second request: Should be still routed to the same primary node
        let value = runtime.block_on(
            cmd("SET")
                .arg(key)
                .arg("bar")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));

        // Assert there was only a single MOVED error
        assert_eq!(cloned_moved_requests.load(Ordering::Relaxed), 1);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_reconnect_even_with_zero_retries() {
        let name = "test_async_cluster_reconnect_even_with_zero_retries";

        let should_reconnect = atomic::AtomicBool::new(true);
        let connection_count = Arc::new(atomic::AtomicU16::new(0));
        let connection_count_clone = connection_count.clone();

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(0),
            name,
            move |cmd: &[u8], port| {
                match respond_startup(name, cmd) {
                    Ok(_) => {}
                    Err(err) => {
                        connection_count.fetch_add(1, Ordering::Relaxed);
                        return Err(err);
                    }
                }

                if contains_slice(cmd, b"ECHO") && port == 6379 {
                    // Should not attempt to refresh slots more than once:
                    if should_reconnect.swap(false, Ordering::SeqCst) {
                        Err(Err(broken_pipe_error()))
                    } else {
                        Err(Ok(Value::BulkString(b"PONG".to_vec())))
                    }
                } else {
                    panic!("unexpected command {cmd:?}")
                }
            },
        );

        // We expect 6 calls in total. MockEnv creates both synchronous and asynchronous connections, which make the following calls:
        // - 1 call by the sync connection to `CLUSTER SLOTS` for initializing the client's topology map.
        // - 3 calls by the async connection to `PING`: one for the user connection when creating the node from initial addresses,
        //     and two more for checking the user and management connections during client initialization in `refresh_slots`.
        // - 1 call by the async connection to `CLIENT SETNAME` for setting up the management connection name.
        // - 1 call by the async connection to `CLUSTER SLOTS` for initializing the client's topology map.
        // Note: If additional nodes or setup calls are added, this number should increase.
        let expected_init_calls = 6;
        assert_eq!(
            connection_count_clone.load(Ordering::Relaxed),
            expected_init_calls
        );

        let value = runtime.block_on(connection.route_command(
            &cmd("ECHO"),
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                host: name.to_string(),
                port: 6379,
            }),
        ));

        // The user should receive an initial error, because there are no retries and the first request failed.
        assert_eq!(
            value.unwrap_err().to_string(),
            broken_pipe_error().to_string()
        );

        let value = runtime.block_on(connection.route_command(
            &cmd("ECHO"),
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                host: name.to_string(),
                port: 6379,
            }),
        ));

        assert_eq!(value, Ok(Value::BulkString(b"PONG".to_vec())));
        // `expected_init_calls` plus another PING for a new user connection created from refresh_connections
        assert_eq!(
            connection_count_clone.load(Ordering::Relaxed),
            expected_init_calls + 1
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_slots_rate_limiter_skips_refresh() {
        let ports = get_ports(3);
        test_async_cluster_refresh_slots_rate_limiter_helper(
            get_topology_with_majority(&ports),
            ports,
            true,
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_refresh_slots_rate_limiter_does_refresh_when_wait_duration_passed() {
        let ports = get_ports(3);
        test_async_cluster_refresh_slots_rate_limiter_helper(
            get_topology_with_majority(&ports),
            ports,
            false,
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_ask_redirect() {
        let name = "node";
        let completed = Arc::new(AtomicI32::new(0));
        let MockEnv {
            async_connection: mut connection,
            handler: _handler,
            runtime,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]),
            name,
            {
                move |cmd: &[u8], port| {
                    respond_startup_two_nodes(name, cmd)?;
                    // Error twice with io-error, ensure connection is reestablished w/out calling
                    // other node (i.e., not doing a full slot rebuild)
                    let count = completed.fetch_add(1, Ordering::SeqCst);
                    match port {
                        6379 => match count {
                            0 => Err(parse_redis_value(b"-ASK 14000 node:6380\r\n")),
                            _ => panic!("Node should not be called now"),
                        },
                        6380 => match count {
                            1 => {
                                assert!(contains_slice(cmd, b"ASKING"));
                                Err(Ok(Value::Okay))
                            }
                            2 => {
                                assert!(contains_slice(cmd, b"GET"));
                                Err(Ok(Value::BulkString(b"123".to_vec())))
                            }
                            _ => panic!("Node should not be called now"),
                        },
                        _ => panic!("Wrong node"),
                    }
                }
            },
        );

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_ask_save_new_connection() {
        let name = "node";
        let ping_attempts = Arc::new(AtomicI32::new(0));
        let ping_attempts_clone = ping_attempts.clone();
        let MockEnv {
            async_connection: mut connection,
            handler: _handler,
            runtime,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]),
            name,
            {
                move |cmd: &[u8], port| {
                    if port != 6391 {
                        respond_startup_two_nodes(name, cmd)?;
                        return Err(parse_redis_value(b"-ASK 14000 node:6391\r\n"));
                    }

                    if contains_slice(cmd, b"PING") {
                        ping_attempts_clone.fetch_add(1, Ordering::Relaxed);
                    }
                    respond_startup_two_nodes(name, cmd)?;
                    Err(Ok(Value::Okay))
                }
            },
        );

        for _ in 0..4 {
            runtime
                .block_on(
                    cmd("GET")
                        .arg("test")
                        .query_async::<_, Value>(&mut connection),
                )
                .unwrap();
        }

        assert_eq!(ping_attempts.load(Ordering::Relaxed), 1);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_reset_routing_if_redirect_fails() {
        let name = "test_async_cluster_reset_routing_if_redirect_fails";
        let completed = Arc::new(AtomicI32::new(0));
        let MockEnv {
            async_connection: mut connection,
            handler: _handler,
            runtime,
            ..
        } = MockEnv::new(name, move |cmd: &[u8], port| {
            if port != 6379 && port != 6380 {
                return Err(Err(broken_pipe_error()));
            }
            respond_startup_two_nodes(name, cmd)?;
            let count = completed.fetch_add(1, Ordering::SeqCst);
            match (port, count) {
                // redirect once to non-existing node
                (6379, 0) => Err(parse_redis_value(
                    format!("-ASK 14000 {name}:9999\r\n").as_bytes(),
                )),
                // accept the next request
                (6379, 1) => {
                    assert!(contains_slice(cmd, b"GET"));
                    Err(Ok(Value::BulkString(b"123".to_vec())))
                }
                _ => panic!("Wrong node. port: {port}, received count: {count}"),
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_ask_redirect_even_if_original_call_had_no_route() {
        let name = "node";
        let completed = Arc::new(AtomicI32::new(0));
        let MockEnv {
            async_connection: mut connection,
            handler: _handler,
            runtime,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]),
            name,
            {
                move |cmd: &[u8], port| {
                    respond_startup_two_nodes(name, cmd)?;
                    // Error twice with io-error, ensure connection is reestablished w/out calling
                    // other node (i.e., not doing a full slot rebuild)
                    let count = completed.fetch_add(1, Ordering::SeqCst);
                    if count == 0 {
                        return Err(parse_redis_value(b"-ASK 14000 node:6380\r\n"));
                    }
                    match port {
                        6380 => match count {
                            1 => {
                                assert!(
                                    contains_slice(cmd, b"ASKING"),
                                    "{:?}",
                                    std::str::from_utf8(cmd)
                                );
                                Err(Ok(Value::Okay))
                            }
                            2 => {
                                assert!(contains_slice(cmd, b"EVAL"));
                                Err(Ok(Value::Okay))
                            }
                            _ => panic!("Node should not be called now"),
                        },
                        _ => panic!("Wrong node"),
                    }
                }
            },
        );

        let value = runtime.block_on(
            cmd("EVAL") // Eval command has no directed, and so is redirected randomly
                .query_async::<_, Value>(&mut connection),
        );

        assert_eq!(value, Ok(Value::Okay));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_ask_error_when_new_node_is_added() {
        let name = "ask_with_extra_nodes";

        let requests = atomic::AtomicUsize::new(0);
        let started = atomic::AtomicBool::new(false);

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::new(name, move |cmd: &[u8], port| {
            if !started.load(atomic::Ordering::SeqCst) {
                respond_startup(name, cmd)?;
            }
            started.store(true, atomic::Ordering::SeqCst);

            if contains_slice(cmd, b"PING") || contains_slice(cmd, b"SETNAME") {
                return Err(Ok(Value::SimpleString("OK".into())));
            }

            let i = requests.fetch_add(1, atomic::Ordering::SeqCst);

            match i {
                // Respond that the key exists on a node that does not yet have a connection:
                0 => Err(parse_redis_value(
                    format!("-ASK 123 {name}:6380\r\n").as_bytes(),
                )),
                1 => {
                    assert_eq!(port, 6380);
                    assert!(contains_slice(cmd, b"ASKING"));
                    Err(Ok(Value::Okay))
                }
                2 => {
                    assert_eq!(port, 6380);
                    assert!(contains_slice(cmd, b"GET"));
                    Err(Ok(Value::BulkString(b"123".to_vec())))
                }
                _ => {
                    panic!("Unexpected request: {cmd:?}");
                }
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_replica_read() {
        let name = "node";

        // requests should route to replica
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |cmd: &[u8], port| {
                respond_startup_with_replica(name, cmd)?;
                match port {
                    6380 => Err(Ok(Value::BulkString(b"123".to_vec()))),
                    _ => panic!("Wrong node"),
                }
            },
        );

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(123)));

        // requests should route to primary
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |cmd: &[u8], port| {
                respond_startup_with_replica(name, cmd)?;
                match port {
                    6379 => Err(Ok(Value::SimpleString("OK".into()))),
                    _ => panic!("Wrong node"),
                }
            },
        );

        let value = runtime.block_on(
            cmd("SET")
                .arg("test")
                .arg("123")
                .query_async::<_, Option<Value>>(&mut connection),
        );
        assert_eq!(value, Ok(Some(Value::SimpleString("OK".to_owned()))));
    }

    fn test_async_cluster_fan_out(
        command: &'static str,
        expected_ports: Vec<u16>,
        slots_config: Option<Vec<MockSlotRange>>,
    ) {
        let name = "node";
        let found_ports = Arc::new(std::sync::Mutex::new(Vec::new()));
        let ports_clone = found_ports.clone();
        let mut cmd = Cmd::new();
        for arg in command.split_whitespace() {
            cmd.arg(arg);
        }
        let packed_cmd = cmd.get_packed_command();
        // requests should route to replica
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(
                    name,
                    received_cmd,
                    slots_config.clone(),
                )?;
                if received_cmd == packed_cmd {
                    ports_clone.lock().unwrap().push(port);
                    return Err(Ok(Value::SimpleString("OK".into())));
                }
                Ok(())
            },
        );

        let _ = runtime.block_on(cmd.query_async::<_, Option<()>>(&mut connection));
        found_ports.lock().unwrap().sort();
        // MockEnv creates 2 mock connections.
        assert_eq!(*found_ports.lock().unwrap(), expected_ports);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_to_all_primaries() {
        test_async_cluster_fan_out("FLUSHALL", vec![6379, 6381], None);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_to_all_nodes() {
        test_async_cluster_fan_out("CONFIG SET", vec![6379, 6380, 6381, 6382], None);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_once_to_each_primary_when_no_replicas_are_available() {
        test_async_cluster_fan_out(
            "CONFIG SET",
            vec![6379, 6381],
            Some(vec![
                MockSlotRange {
                    primary_port: 6379,
                    replica_ports: Vec::new(),
                    slot_range: (0..8191),
                },
                MockSlotRange {
                    primary_port: 6381,
                    replica_ports: Vec::new(),
                    slot_range: (8192..16383),
                },
            ]),
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_once_even_if_primary_has_multiple_slot_ranges() {
        test_async_cluster_fan_out(
            "CONFIG SET",
            vec![6379, 6380, 6381, 6382],
            Some(vec![
                MockSlotRange {
                    primary_port: 6379,
                    replica_ports: vec![6380],
                    slot_range: (0..4000),
                },
                MockSlotRange {
                    primary_port: 6381,
                    replica_ports: vec![6382],
                    slot_range: (4001..8191),
                },
                MockSlotRange {
                    primary_port: 6379,
                    replica_ports: vec![6380],
                    slot_range: (8192..8200),
                },
                MockSlotRange {
                    primary_port: 6381,
                    replica_ports: vec![6382],
                    slot_range: (8201..16383),
                },
            ]),
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_route_according_to_passed_argument() {
        let name = "test_async_cluster_route_according_to_passed_argument";

        let touched_ports = Arc::new(std::sync::Mutex::new(Vec::new()));
        let cloned_ports = touched_ports.clone();

        // requests should route to replica
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |cmd: &[u8], port| {
                respond_startup_with_replica(name, cmd)?;
                cloned_ports.lock().unwrap().push(port);
                Err(Ok(Value::Nil))
            },
        );

        let mut cmd = cmd("GET");
        cmd.arg("test");
        let _ = runtime.block_on(connection.route_command(
            &cmd,
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, None)),
        ));
        {
            let mut touched_ports = touched_ports.lock().unwrap();
            touched_ports.sort();
            assert_eq!(*touched_ports, vec![6379, 6381]);
            touched_ports.clear();
        }

        let _ = runtime.block_on(connection.route_command(
            &cmd,
            RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
        ));
        {
            let mut touched_ports = touched_ports.lock().unwrap();
            touched_ports.sort();
            assert_eq!(*touched_ports, vec![6379, 6380, 6381, 6382]);
            touched_ports.clear();
        }

        let _ = runtime.block_on(connection.route_command(
            &cmd,
            RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                host: name.to_string(),
                port: 6382,
            }),
        ));
        {
            let mut touched_ports = touched_ports.lock().unwrap();
            touched_ports.sort();
            assert_eq!(*touched_ports, vec![6382]);
            touched_ports.clear();
        }
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_aggregate_numeric_response_with_min() {
        let name = "test_async_cluster_fan_out_and_aggregate_numeric_response";
        let mut cmd = Cmd::new();
        cmd.arg("SLOWLOG").arg("LEN");

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;

                let res = 6383 - port as i64;
                Err(Ok(Value::Int(res))) // this results in 1,2,3,4
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, i64>(&mut connection))
            .unwrap();
        assert_eq!(result, 10, "{result}");
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_aggregate_logical_array_response() {
        let name = "test_async_cluster_fan_out_and_aggregate_logical_array_response";
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT")
            .arg("EXISTS")
            .arg("foo")
            .arg("bar")
            .arg("baz")
            .arg("barvaz");

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;

                if port == 6381 {
                    return Err(Ok(Value::Array(vec![
                        Value::Int(0),
                        Value::Int(0),
                        Value::Int(1),
                        Value::Int(1),
                    ])));
                } else if port == 6379 {
                    return Err(Ok(Value::Array(vec![
                        Value::Int(0),
                        Value::Int(1),
                        Value::Int(0),
                        Value::Int(1),
                    ])));
                }

                panic!("unexpected port {port}");
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Vec<i64>>(&mut connection))
            .unwrap();
        assert_eq!(result, vec![0, 0, 0, 1], "{result:?}");
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_return_one_succeeded_response() {
        let name = "test_async_cluster_fan_out_and_return_one_succeeded_response";
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("KILL");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                if port == 6381 {
                    return Err(Ok(Value::Okay));
                }
                Err(Err((
                    ErrorKind::NotBusy,
                    "No scripts in execution right now",
                )
                    .into()))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Value>(&mut connection))
            .unwrap();
        assert_eq!(result, Value::Okay, "{result:?}");
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_fail_one_succeeded_if_there_are_no_successes() {
        let name = "test_async_cluster_fan_out_and_fail_one_succeeded_if_there_are_no_successes";
        let mut cmd = Cmd::new();
        cmd.arg("SCRIPT").arg("KILL");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], _port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;

                Err(Err((
                    ErrorKind::NotBusy,
                    "No scripts in execution right now",
                )
                    .into()))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Value>(&mut connection))
            .unwrap_err();
        assert_eq!(result.kind(), ErrorKind::NotBusy, "{:?}", result.kind());
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_return_all_succeeded_response() {
        let name = "test_async_cluster_fan_out_and_return_all_succeeded_response";
        let cmd = cmd("FLUSHALL");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], _port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                Err(Ok(Value::Okay))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Value>(&mut connection))
            .unwrap();
        assert_eq!(result, Value::Okay, "{result:?}");
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_fail_all_succeeded_if_there_is_a_single_failure() {
        let name = "test_async_cluster_fan_out_and_fail_all_succeeded_if_there_is_a_single_failure";
        let cmd = cmd("FLUSHALL");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                if port == 6381 {
                    return Err(Err((
                        ErrorKind::NotBusy,
                        "No scripts in execution right now",
                    )
                        .into()));
                }
                Err(Ok(Value::Okay))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Value>(&mut connection))
            .unwrap_err();
        assert_eq!(result.kind(), ErrorKind::NotBusy, "{:?}", result.kind());
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_first_succeeded_non_empty_or_all_empty_return_value_ignoring_nil_and_err_resps(
    ) {
        let name =
            "test_async_cluster_first_succeeded_non_empty_or_all_empty_return_value_ignoring_nil_and_err_resps";
        let cmd = cmd("RANDOMKEY");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                let ports = vec![6379, 6380, 6381];
                let slots_config_vec = generate_topology_view(&ports, 1000, true);
                respond_startup_with_config(name, received_cmd, Some(slots_config_vec), false)?;
                if port == 6380 {
                    return Err(Ok(Value::BulkString("foo".as_bytes().to_vec())));
                } else if port == 6381 {
                    return Err(Err(RedisError::from((
                        redis::ErrorKind::ResponseError,
                        "ERROR",
                    ))));
                }
                Err(Ok(Value::Nil))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, String>(&mut connection))
            .unwrap();
        assert_eq!(result, "foo", "{result:?}");
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_first_succeeded_non_empty_or_all_empty_return_err_if_all_resps_are_nil_and_errors(
    ) {
        let name =
            "test_async_cluster_first_succeeded_non_empty_or_all_empty_return_err_if_all_resps_are_nil_and_errors";
        let cmd = cmd("RANDOMKEY");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_config(name, received_cmd, None, false)?;
                if port == 6380 {
                    return Err(Ok(Value::Nil));
                }
                Err(Err(RedisError::from((
                    redis::ErrorKind::ResponseError,
                    "ERROR",
                ))))
            },
        );
        let result = runtime
            .block_on(cmd.query_async::<_, Value>(&mut connection))
            .unwrap_err();
        assert_eq!(result.kind(), ErrorKind::ResponseError);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_first_succeeded_non_empty_or_all_empty_return_nil_if_all_resp_nil() {
        let name =
            "test_async_cluster_first_succeeded_non_empty_or_all_empty_return_nil_if_all_resp_nil";
        let cmd = cmd("RANDOMKEY");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], _port| {
                respond_startup_with_config(name, received_cmd, None, false)?;
                Err(Ok(Value::Nil))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Value>(&mut connection))
            .unwrap();
        assert_eq!(result, Value::Nil, "{result:?}");
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_return_map_of_results_for_special_response_policy() {
        let name = "foo";
        let mut cmd = Cmd::new();
        cmd.arg("LATENCY").arg("LATEST");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                Err(Ok(Value::BulkString(
                    format!("latency: {port}").into_bytes(),
                )))
            },
        );

        // TODO once RESP3 is in, return this as a map
        let mut result = runtime
            .block_on(cmd.query_async::<_, Vec<(String, String)>>(&mut connection))
            .unwrap();
        result.sort();
        assert_eq!(
            result,
            vec![
                (format!("{name}:6379"), "latency: 6379".to_string()),
                (format!("{name}:6380"), "latency: 6380".to_string()),
                (format!("{name}:6381"), "latency: 6381".to_string()),
                (format!("{name}:6382"), "latency: 6382".to_string())
            ],
            "{result:?}"
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fan_out_and_combine_arrays_of_values() {
        let name = "foo";
        let cmd = cmd("KEYS");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                Err(Ok(Value::Array(vec![Value::BulkString(
                    format!("key:{port}").into_bytes(),
                )])))
            },
        );

        let mut result = runtime
            .block_on(cmd.query_async::<_, Vec<String>>(&mut connection))
            .unwrap();
        result.sort();
        assert_eq!(
            result,
            vec!["key:6379".to_string(), "key:6381".to_string(),],
            "{result:?}"
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_split_multi_shard_command_and_combine_arrays_of_values() {
        let name = "test_async_cluster_split_multi_shard_command_and_combine_arrays_of_values";
        let mut cmd = cmd("MGET");
        cmd.arg("foo").arg("bar").arg("baz");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                let cmd_str = std::str::from_utf8(received_cmd).unwrap();
                let results = ["foo", "bar", "baz"]
                    .iter()
                    .filter_map(|expected_key| {
                        if cmd_str.contains(expected_key) {
                            Some(Value::BulkString(
                                format!("{expected_key}-{port}").into_bytes(),
                            ))
                        } else {
                            None
                        }
                    })
                    .collect();
                Err(Ok(Value::Array(results)))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Vec<String>>(&mut connection))
            .unwrap();
        assert_eq!(result, vec!["foo-6382", "bar-6380", "baz-6380"]);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_handle_asking_error_in_split_multi_shard_command() {
        let name = "test_async_cluster_handle_asking_error_in_split_multi_shard_command";
        let mut cmd = cmd("MGET");
        cmd.arg("foo").arg("bar").arg("baz");
        let asking_called = Arc::new(AtomicU16::new(0));
        let asking_called_cloned = asking_called.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(name, received_cmd, None)?;
                let cmd_str = std::str::from_utf8(received_cmd).unwrap();
                if cmd_str.contains("ASKING") && port == 6382 {
                    asking_called_cloned.fetch_add(1, Ordering::Relaxed);
                }
                if port == 6380 && cmd_str.contains("baz") {
                    return Err(parse_redis_value(
                        format!("-ASK 14000 {name}:6382\r\n").as_bytes(),
                    ));
                }
                let results = ["foo", "bar", "baz"]
                    .iter()
                    .filter_map(|expected_key| {
                        if cmd_str.contains(expected_key) {
                            Some(Value::BulkString(
                                format!("{expected_key}-{port}").into_bytes(),
                            ))
                        } else {
                            None
                        }
                    })
                    .collect();
                Err(Ok(Value::Array(results)))
            },
        );

        let result = runtime
            .block_on(cmd.query_async::<_, Vec<String>>(&mut connection))
            .unwrap();
        assert_eq!(result, vec!["foo-6382", "bar-6380", "baz-6382"]);
        assert_eq!(asking_called.load(Ordering::Relaxed), 1);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_pass_errors_from_split_multi_shard_command() {
        let name = "test_async_cluster_pass_errors_from_split_multi_shard_command";
        let mut cmd = cmd("MGET");
        cmd.arg("foo").arg("bar").arg("baz");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::new(name, move |received_cmd: &[u8], port| {
            respond_startup_with_replica_using_config(name, received_cmd, None)?;
            let cmd_str = std::str::from_utf8(received_cmd).unwrap();
            if cmd_str.contains("foo") || cmd_str.contains("baz") {
                Err(Err((ErrorKind::IoError, "error").into()))
            } else {
                Err(Ok(Value::Array(vec![Value::BulkString(
                    format!("{port}").into_bytes(),
                )])))
            }
        });

        let result = runtime
            .block_on(cmd.query_async::<_, Vec<String>>(&mut connection))
            .unwrap_err();
        assert_eq!(result.kind(), ErrorKind::IoError);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_handle_missing_slots_in_split_multi_shard_command() {
        let name = "test_async_cluster_handle_missing_slots_in_split_multi_shard_command";
        let mut cmd = cmd("MGET");
        cmd.arg("foo").arg("bar").arg("baz");
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::new(name, move |received_cmd: &[u8], port| {
            respond_startup_with_replica_using_config(
                name,
                received_cmd,
                Some(vec![MockSlotRange {
                    primary_port: 6381,
                    replica_ports: vec![6382],
                    slot_range: (8192..16383),
                }]),
            )?;
            Err(Ok(Value::Array(vec![Value::BulkString(
                format!("{port}").into_bytes(),
            )])))
        });

        let result = runtime
            .block_on(cmd.query_async::<_, Vec<String>>(&mut connection))
            .unwrap_err();
        assert!(
            matches!(result.kind(), ErrorKind::ConnectionNotFoundForRoute)
                || result.is_connection_dropped()
        );
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_with_username_and_password() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder
                    .username(RedisCluster::username().to_string())
                    .password(RedisCluster::password().to_string())
            },
            false,
        );
        cluster.disable_default_user();

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            cmd("SET")
                .arg("test")
                .arg("test_data")
                .query_async::<_, ()>(&mut connection)
                .await?;
            let res: String = cmd("GET")
                .arg("test")
                .clone()
                .query_async(&mut connection)
                .await?;
            assert_eq!(res, "test_data");
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_io_error() {
        let name = "node";
        let completed = Arc::new(AtomicI32::new(0));
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(2),
            name,
            move |cmd: &[u8], port| {
                respond_startup_two_nodes(name, cmd)?;
                // Error twice with io-error, ensure connection is reestablished w/out calling
                // other node (i.e., not doing a full slot rebuild)
                match port {
                    6380 => panic!("Node should not be called"),
                    _ => match completed.fetch_add(1, Ordering::SeqCst) {
                        0..=1 => Err(Err(RedisError::from((
                            ErrorKind::FatalSendError,
                            "mock-io-error",
                        )))),
                        _ => Err(Ok(Value::BulkString(b"123".to_vec()))),
                    },
                }
            },
        );

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        assert_eq!(value, Ok(Some(123)));
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_non_retryable_error_should_not_retry() {
        let name = "node";
        let completed = Arc::new(AtomicI32::new(0));
        let MockEnv {
            async_connection: mut connection,
            handler: _handler,
            runtime,
            ..
        } = MockEnv::new(name, {
            let completed = completed.clone();
            move |cmd: &[u8], _| {
                respond_startup_two_nodes(name, cmd)?;
                // Error twice with io-error, ensure connection is reestablished w/out calling
                // other node (i.e., not doing a full slot rebuild)
                completed.fetch_add(1, Ordering::SeqCst);
                Err(Err((ErrorKind::ReadOnly, "").into()))
            }
        });

        let value = runtime.block_on(
            cmd("GET")
                .arg("test")
                .query_async::<_, Option<i32>>(&mut connection),
        );

        match value {
            Ok(_) => panic!("result should be an error"),
            Err(e) => match e.kind() {
                ErrorKind::ReadOnly => {}
                _ => panic!("Expected ReadOnly but got {:?}", e.kind()),
            },
        }
        assert_eq!(completed.load(Ordering::SeqCst), 1);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_non_retryable_io_error_should_not_retry() {
        let name = "test_async_cluster_non_retryable_io_error_should_not_retry";
        let requests = atomic::AtomicUsize::new(0);
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(3),
            name,
            move |cmd: &[u8], _port| {
                respond_startup_two_nodes(name, cmd)?;
                let i = requests.fetch_add(1, atomic::Ordering::SeqCst);
                match i {
                    0 => Err(Err(RedisError::from((ErrorKind::IoError, "io-error")))),
                    _ => {
                        panic!("Expected not to be retried!")
                    }
                }
            },
        );
        runtime
            .block_on(async move {
                let res = cmd("INCR")
                    .arg("foo")
                    .query_async::<_, Option<i32>>(&mut connection)
                    .await;
                assert!(res.is_err());
                let err = res.unwrap_err();
                assert!(err.is_io_error());
                Ok::<_, RedisError>(())
            })
            .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_retry_safe_io_error_should_be_retried() {
        let name = "test_async_cluster_retry_safe_io_error_should_be_retried";
        let requests = atomic::AtomicUsize::new(0);
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(3),
            name,
            move |cmd: &[u8], _port| {
                respond_startup_two_nodes(name, cmd)?;
                let i = requests.fetch_add(1, atomic::Ordering::SeqCst);
                match i {
                    0 => Err(Err(RedisError::from((
                        ErrorKind::FatalSendError,
                        "server didn't receive the request, safe to retry",
                    )))),
                    _ => Err(Ok(Value::Int(1))),
                }
            },
        );
        runtime
            .block_on(async move {
                let res = cmd("INCR")
                    .arg("foo")
                    .query_async::<_, i32>(&mut connection)
                    .await;
                assert!(res.is_ok());
                let value = res.unwrap();
                assert_eq!(value, 1);
                Ok::<_, RedisError>(())
            })
            .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_read_from_primary() {
        let name = "node";
        let found_ports = Arc::new(std::sync::Mutex::new(Vec::new()));
        let ports_clone = found_ports.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::new(name, move |received_cmd: &[u8], port| {
            respond_startup_with_replica_using_config(
                name,
                received_cmd,
                Some(vec![
                    MockSlotRange {
                        primary_port: 6379,
                        replica_ports: vec![6380, 6381],
                        slot_range: (0..8191),
                    },
                    MockSlotRange {
                        primary_port: 6382,
                        replica_ports: vec![6383, 6384],
                        slot_range: (8192..16383),
                    },
                ]),
            )?;
            ports_clone.lock().unwrap().push(port);
            Err(Ok(Value::Nil))
        });

        runtime.block_on(async {
            cmd("GET")
                .arg("foo")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
            cmd("GET")
                .arg("bar")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
            cmd("GET")
                .arg("foo")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
            cmd("GET")
                .arg("bar")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
        });

        found_ports.lock().unwrap().sort();
        assert_eq!(*found_ports.lock().unwrap(), vec![6379, 6379, 6382, 6382]);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_round_robin_read_from_replica() {
        let name = "node";
        let found_ports = Arc::new(std::sync::Mutex::new(Vec::new()));
        let ports_clone = found_ports.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).read_from_replicas(),
            name,
            move |received_cmd: &[u8], port| {
                respond_startup_with_replica_using_config(
                    name,
                    received_cmd,
                    Some(vec![
                        MockSlotRange {
                            primary_port: 6379,
                            replica_ports: vec![6380, 6381],
                            slot_range: (0..8191),
                        },
                        MockSlotRange {
                            primary_port: 6382,
                            replica_ports: vec![6383, 6384],
                            slot_range: (8192..16383),
                        },
                    ]),
                )?;
                ports_clone.lock().unwrap().push(port);
                Err(Ok(Value::Nil))
            },
        );

        runtime.block_on(async {
            cmd("GET")
                .arg("foo")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
            cmd("GET")
                .arg("bar")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
            cmd("GET")
                .arg("foo")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
            cmd("GET")
                .arg("bar")
                .query_async::<_, ()>(&mut connection)
                .await
                .unwrap();
        });

        found_ports.lock().unwrap().sort();
        assert_eq!(*found_ports.lock().unwrap(), vec![6380, 6381, 6383, 6384]);
    }

    fn get_queried_node_id_if_master(cluster_nodes_output: Value) -> Option<String> {
        // Returns the node ID of the connection that was queried for CLUSTER NODES (using the 'myself' flag), if it's a master.
        // Otherwise, returns None.
        let get_node_id = |str: &str| {
            let parts: Vec<&str> = str.split('\n').collect();
            for node_entry in parts {
                if node_entry.contains("myself") && node_entry.contains("master") {
                    let node_entry_parts: Vec<&str> = node_entry.split(' ').collect();
                    let node_id = node_entry_parts[0];
                    return Some(node_id.to_string());
                }
            }
            None
        };

        match cluster_nodes_output {
            Value::BulkString(val) => match from_utf8(&val) {
                Ok(str_res) => get_node_id(str_res),
                Err(e) => panic!("failed to decode INFO response: {e:?}"),
            },
            Value::VerbatimString { format: _, text } => get_node_id(&text),
            _ => panic!("Recieved unexpected response: {cluster_nodes_output:?}"),
        }
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_handle_complete_server_disconnect_without_panicking() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(2),
            false,
        );
        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            drop(cluster);
            for _ in 0..5 {
                let cmd = cmd("PING");
                let result = connection
                    .route_command(&cmd, RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random))
                    .await;
                // TODO - this should be a NoConnectionError, but ATM we get the errors from the failing
                assert!(result.is_err());
                // This will route to all nodes - different path through the code.
                let result = connection.req_packed_command(&cmd).await;
                // TODO - this should be a NoConnectionError, but ATM we get the errors from the failing
                assert!(result.is_err());
            }
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_test_fast_reconnect() {
        // Note the 3 seconds connection check to differentiate between notifications and periodic
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder
                    .retries(0)
                    .periodic_connections_checks(Some(Duration::from_secs(3)))
            },
            false,
        );

        // For tokio-comp, do 3 consequtive disconnects and ensure reconnects succeeds in less than 100ms,
        // which is more than enough for local connections even with TLS.
        // More than 1 run is done to ensure it is the fast reconnect notification that trigger the reconnect
        // and not the periodic interval.
        // For other async implementation, only periodic connection check is available, hence,
        // do 1 run sleeping for periodic connection check interval, allowing it to reestablish connections
        block_on_all(async move {
            let mut disconnecting_con = cluster.async_connection(None).await;
            let mut monitoring_con = cluster.async_connection(None).await;

            #[cfg(feature = "tokio-comp")]
            let tries = 0..3;
            #[cfg(not(feature = "tokio-comp"))]
            let tries = 0..1;

            for _ in tries {
                // get connection id
                let mut cmd = redis::cmd("CLIENT");
                cmd.arg("ID");
                let res = disconnecting_con
                    .route_command(
                        &cmd,
                        RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                            0,
                            SlotAddr::Master,
                        ))),
                    )
                    .await;
                assert!(res.is_ok());
                let res = res.unwrap();
                let id = {
                    match res {
                        Value::Int(id) => id,
                        _ => {
                            panic!("Wrong return value for CLIENT ID command: {res:?}");
                        }
                    }
                };

                // ask server to kill the connection
                let mut cmd = redis::cmd("CLIENT");
                cmd.arg("KILL").arg("ID").arg(id).arg("SKIPME").arg("NO");
                let res = disconnecting_con
                    .route_command(
                        &cmd,
                        RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                            0,
                            SlotAddr::Master,
                        ))),
                    )
                    .await;
                // assert server has closed connection
                assert_eq!(res, Ok(Value::Int(1)));

                #[cfg(feature = "tokio-comp")]
                // ensure reconnect happened in less than 100ms
                sleep(futures_time::time::Duration::from_millis(100)).await;

                #[cfg(not(feature = "tokio-comp"))]
                // no fast notification is available, wait for 1 periodic check + overhead
                sleep(futures_time::time::Duration::from_secs(3 + 1)).await;

                let mut cmd = redis::cmd("CLIENT");
                cmd.arg("LIST").arg("TYPE").arg("NORMAL");
                let res = monitoring_con
                    .route_command(
                        &cmd,
                        RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                            0,
                            SlotAddr::Master,
                        ))),
                    )
                    .await;
                assert!(res.is_ok());
                let res = res.unwrap();
                let client_list: String = {
                    match res {
                        // RESP2
                        Value::BulkString(client_info) => {
                            // ensure 4 connections - 2 for each client, its save to unwrap here
                            String::from_utf8(client_info).unwrap()
                        }
                        // RESP3
                        Value::VerbatimString { format: _, text } => text,
                        _ => {
                            panic!("Wrong return type for CLIENT LIST command: {res:?}");
                        }
                    }
                };
                assert_eq!(client_list.chars().filter(|&x| x == '\n').count(), 4);
            }
            Ok(())
        })
        .unwrap();
    }

    // This test verifies that if a client's connection to a node is killed and its reconnection is blocked,
    // commands from the same client to healthy nodes are not delayed.
    //
    // The test uses a cluster with 3 shards and disables periodic connection checks, also We set 3 retries, so on the
    // request to the blocked node - (1) Identify disconnection, (2) Route to Random node, (3) ConnectionNotFoundForRoute.
    // It uses two clients:
    //  - A helper client (con_block) that executes a pipeline against shard 0. This pipeline kills
    //    the main client's (con_tx) connection on shard 0 and blocks its reconnection using a DEBUG SLEEP command to block the engine.
    //  - The main client connection (con_tx) which is used to send commands.
    //
    // The test workflow is as follows:
    //  1. The helper client (con_block) kills con_tx's connection to shard 0 and blocks reconnection by blocking the engine.
    //  2. The main client (con_tx) sends a request to shard 0, which should fail and trigger reconnection in the background.
    //  3. The main client (con_tx) then sends a request wrapped by timeout to a healthy node (routed to slot 12182 - cluster keyslot foo), which should succeed.
    //  4. After the blocking sleep expires and reconnection completes, the main client sends another request to shard 0,
    //     which is expected to return "OK".
    //
    // This demonstrates that a reconnection process to one node does not prevent the same client from
    // successfully communicating with healthy nodes.
    #[test]
    #[serial_test::serial]
    fn test_async_cluster_non_blocking_reconnection_with_transaction() {
        use std::time::Duration;

        // Create a cluster with 3 shards, no periodic checks, and a 250ms response timeout.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3, // 3 shards
            0,
            |builder| {
                builder
                    .retries(2)
                    .periodic_connections_checks(None)
                    .response_timeout(Duration::from_millis(250)) // aligns with the default timeout in glide core
            },
            false,
        );

        block_on_all(async move {
            // con_block will run the blocking transaction on shard 0.
            let mut con_block = cluster.async_connection(None).await;
            // con_tx will be used to trigger reconnection and send commands.
            let mut con_tx = cluster.async_connection(None).await;
            let keyslot_bar = 5061;

            // STEP 1: Get the client ID for shard holding 'bar' key using con_tx.
            let mut cmd_id = redis::cmd("CLIENT");
            cmd_id.arg("ID");
            let res = con_tx
                .route_command(
                    &cmd_id,
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
                        Route::new(keyslot_bar, SlotAddr::Master),
                    )),
                )
                .await;
            let client_id = match res.unwrap() {
                Value::Int(id) => id,
                _ => panic!("Unexpected CLIENT ID response on shard 0"),
            };

            // STEP 2: Create an atomic transaction (pipeline) that:
            //   - Kills the connection on shard 0 using the obtained client_id, and
            //   - Blocks reconnection by executing a DEBUG SLEEP (3 seconds).
            let mut pipe = redis::pipe();
            pipe.atomic()
                .cmd("CLIENT")
                .arg("KILL")
                .arg("ID")
                .arg(client_id)
                .arg("SKIPME")
                .arg("NO")
                .ignore()
                .cmd("DEBUG")
                .arg("SLEEP")
                .arg(3)
                .ignore();

            // STEP 3: Spawn the transaction on shard 0 via con_block.
            // Running it concurrently ensures the blocking (DEBUG SLEEP) does not stall the rest of the test.
            tokio::spawn(async move {
                _ = con_block
                    .route_pipeline(
                        &pipe,
                        0,
                        2,
                        Some(SingleNodeRoutingInfo::SpecificNode(Route::new(keyslot_bar, SlotAddr::Master))),
                        None,
                    )
                    .await;
            });

            // STEP 4: Give a short delay to help ensure the pipeline has started.
            tokio::time::sleep(Duration::from_secs(1)).await;

            // STEP 5: Use con_tx to send a SET request to the blocked shard (shard 0)
            // to trigger reconnection logic.
            {
                let mut trigger_set_cmd = redis::cmd("SET");
                trigger_set_cmd.arg("bar").arg("123");
                let trigger_res =
                    trigger_set_cmd.query_async::<_, String>(&mut con_tx).await;
                match trigger_res {
                    Ok(_) => panic!("Unexpected success on SET to blocked shard; expected ConnectionNotFoundForRoute error"),
                    Err(e) => {
                        if !e.to_string().contains("ConnectionNotFoundForRoute") {
                            panic!("Unexpected error on SET to blocked shard: {e:?}");
                        }
                    }
                }
            }

            // STEP 6: While reconnection is active (Due to the BEBUG SLEEP), send a SET request via con_tx to a healthy
            // shard (routed to slot 12182 - cluster keyslot foo) to show that healthy requests are processed right away
            {
                let mut healthy_set_cmd = redis::cmd("SET");
                healthy_set_cmd.arg("foo").arg("123");
                let healthy_res =
                    healthy_set_cmd.query_async::<_, String>(&mut con_tx).await;
                match healthy_res {
                    Ok(result) => {
                        assert_eq!(
                            result,
                            "OK",
                            "Healthy shard (slot 12182) did not return OK as expected"
                        );
                    }
                    Err(e) => panic!("Route command to healthy shard returned error: {e:?}"),
                }
            }

            // STEP 7: Ensure the pipeline task has completed.
            tokio::time::sleep(Duration::from_secs(3)).await;

            // STEP 8: Send another SET to shard 0 via con_tx and expect it to return "OK".
            {
                let mut final_set_cmd = redis::cmd("SET");
                final_set_cmd.arg("bar").arg("123");
                let final_res = final_set_cmd.query_async::<_, String>(&mut con_tx).await;
                assert_eq!(
                    final_res.unwrap(),
                    "OK",
                    "Final check: shard 0 did return OK as expected"
                );
            }
            Ok(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_periodic_checks_update_topology_after_failover() {
        // This test aims to validate the functionality of periodic topology checks by detecting and updating topology changes.
        // We will repeatedly execute CLUSTER NODES commands against the primary node responsible for slot 0, recording its node ID.
        // Once we've successfully completed commands with the current primary, we will initiate a failover within the same shard.
        // Since we are not executing key-based commands, we won't encounter MOVED errors that trigger a slot refresh.
        // Consequently, we anticipate that only the periodic topology check will detect this change and trigger topology refresh.
        // If successful, the node to which we route the CLUSTER NODES command should be the newly promoted node with a different node ID.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            6,
            1,
            |builder| {
                builder
                    .periodic_topology_checks(Duration::from_millis(10))
                    // Disable the rate limiter to refresh slots immediately on all MOVED errors
                    .slots_refresh_rate_limit(Duration::from_secs(0), 0)
            },
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let mut prev_master_id = "".to_string();
            let max_requests = 5000;
            let mut i = 0;
            loop {
                if i == 10 {
                    let mut cmd = redis::cmd("CLUSTER");
                    cmd.arg("FAILOVER");
                    cmd.arg("TAKEOVER");
                    let res = connection
                        .route_command(
                            &cmd,
                            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
                                Route::new(0, SlotAddr::ReplicaRequired),
                            )),
                        )
                        .await;
                    assert!(res.is_ok());
                } else if i == max_requests {
                    break;
                } else {
                    let mut cmd = redis::cmd("CLUSTER");
                    cmd.arg("NODES");
                    let res = connection
                        .route_command(
                            &cmd,
                            RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
                                Route::new(0, SlotAddr::Master),
                            )),
                        )
                        .await
                        .expect("Failed executing CLUSTER NODES");
                    let node_id = get_queried_node_id_if_master(res);
                    if let Some(current_master_id) = node_id {
                        if prev_master_id.is_empty() {
                            prev_master_id = current_master_id;
                        } else if prev_master_id != current_master_id {
                            return Ok::<_, RedisError>(());
                        }
                    }
                }
                i += 1;
                let _ = sleep(futures_time::time::Duration::from_millis(10)).await;
            }
            panic!("Topology change wasn't found!");
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_recover_disconnected_management_connections() {
        // This test aims to verify that the management connections used for periodic checks are reconnected, in case that they get killed.
        // In order to test this, we choose a single node, kill all connections to it which aren't user connections, and then wait until new
        // connections are created.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder.periodic_topology_checks(Duration::from_millis(10))
                            // Disable the rate limiter to refresh slots immediately
                            .slots_refresh_rate_limit(Duration::from_secs(0), 0)
            },
            false,
        );

        block_on_all(async move {
            let routing = RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                1,
                SlotAddr::Master,
            )));

            let mut connection = cluster.async_connection(None).await;
            let max_requests = 5000;

            let connections =
                get_clients_names_to_ids(&mut connection, routing.clone().into()).await;
            assert!(connections.contains_key(MANAGEMENT_CONN_NAME));
            let management_conn_id = connections.get(MANAGEMENT_CONN_NAME).unwrap();

            // Get the connection ID of the management connection
            kill_connection(&mut connection, management_conn_id).await;

            let connections =
                get_clients_names_to_ids(&mut connection, routing.clone().into()).await;
            assert!(!connections.contains_key(MANAGEMENT_CONN_NAME));

            for _ in 0..max_requests {
                let _ = sleep(futures_time::time::Duration::from_millis(10)).await;

                let connections =
                    get_clients_names_to_ids(&mut connection, routing.clone().into()).await;
                if connections.contains_key(MANAGEMENT_CONN_NAME) {
                    return Ok(());
                }
            }

            panic!("Topology connection didn't reconnect!");
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_with_client_name() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.client_name(RedisCluster::client_name().to_string()),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let client_info: String = cmd("CLIENT")
                .arg("INFO")
                .query_async(&mut connection)
                .await
                .unwrap();

            let client_attrs = parse_client_info(&client_info);

            assert!(
                client_attrs.contains_key("name"),
                "Could not detect the 'name' attribute in CLIENT INFO output"
            );

            assert_eq!(
                client_attrs["name"],
                RedisCluster::client_name(),
                "Incorrect client name, expecting: {}, got {}",
                RedisCluster::client_name(),
                client_attrs["name"]
            );
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_reroute_from_replica_if_in_loading_state() {
        /* Test replica in loading state. The expected behaviour is that the request will be directed to a different replica or the primary.
        depends on the read from replica policy. */
        let name = "test_async_cluster_reroute_from_replica_if_in_loading_state";

        let load_errors: Arc<_> = Arc::new(std::sync::Mutex::new(vec![]));
        let load_errors_clone = load_errors.clone();

        // requests should route to replica
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).read_from_replicas(),
            name,
            move |cmd: &[u8], port| {
                respond_startup_with_replica_using_config(
                    name,
                    cmd,
                    Some(vec![MockSlotRange {
                        primary_port: 6379,
                        replica_ports: vec![6380, 6381],
                        slot_range: (0..16383),
                    }]),
                )?;
                match port {
                    6380 | 6381 => {
                        load_errors_clone.lock().unwrap().push(port);
                        Err(parse_redis_value(b"-LOADING\r\n"))
                    }
                    6379 => Err(Ok(Value::BulkString(b"123".to_vec()))),
                    _ => panic!("Wrong node"),
                }
            },
        );
        for _n in 0..3 {
            let value = runtime.block_on(
                cmd("GET")
                    .arg("test")
                    .query_async::<_, Option<i32>>(&mut connection),
            );
            assert_eq!(value, Ok(Some(123)));
        }

        let mut load_errors_guard = load_errors.lock().unwrap();
        load_errors_guard.sort();

        // We expected to get only 2 loading error since the 2 replicas are in loading state.
        // The third iteration will be directed to the primary since the connections of the replicas were removed.
        assert_eq!(*load_errors_guard, vec![6380, 6381]);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_read_from_primary_when_primary_loading() {
        // Test primary in loading state. The expected behaviour is that the request will be retried until the primary is no longer in loading state.
        let name = "test_async_cluster_read_from_primary_when_primary_loading";

        const RETRIES: u32 = 3;
        const ITERATIONS: u32 = 2;
        let load_errors = Arc::new(AtomicU32::new(0));
        let load_errors_clone = load_errors.clone();

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]),
            name,
            move |cmd: &[u8], port| {
                respond_startup_with_replica_using_config(
                    name,
                    cmd,
                    Some(vec![MockSlotRange {
                        primary_port: 6379,
                        replica_ports: vec![6380, 6381],
                        slot_range: (0..16383),
                    }]),
                )?;
                match port {
                    6379 => {
                        let attempts = load_errors_clone.fetch_add(1, Ordering::Relaxed) + 1;
                        if attempts % RETRIES == 0 {
                            Err(Ok(Value::BulkString(b"123".to_vec())))
                        } else {
                            Err(parse_redis_value(b"-LOADING\r\n"))
                        }
                    }
                    _ => panic!("Wrong node"),
                }
            },
        );
        for _n in 0..ITERATIONS {
            runtime
                .block_on(
                    cmd("GET")
                        .arg("test")
                        .query_async::<_, Value>(&mut connection),
                )
                .unwrap();
        }

        assert_eq!(load_errors.load(Ordering::Relaxed), ITERATIONS * RETRIES);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_can_be_created_with_partial_slot_coverage() {
        let name = "test_async_cluster_can_be_created_with_partial_slot_coverage";
        let slots_config = Some(vec![
            MockSlotRange {
                primary_port: 6379,
                replica_ports: vec![],
                slot_range: (0..8000),
            },
            MockSlotRange {
                primary_port: 6381,
                replica_ports: vec![],
                slot_range: (8201..16380),
            },
        ]);

        let MockEnv {
            async_connection: mut connection,
            handler: _handler,
            runtime,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(0)
                .read_from_replicas(),
            name,
            move |received_cmd: &[u8], _| {
                respond_startup_with_replica_using_config(
                    name,
                    received_cmd,
                    slots_config.clone(),
                )?;
                Err(Ok(Value::SimpleString("PONG".into())))
            },
        );

        let res = runtime.block_on(connection.req_packed_command(&redis::cmd("PING")));
        assert!(res.is_ok());
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_reconnect_after_complete_server_disconnect() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder.retries(2)
                // Disable the rate limiter to refresh slots immediately
                .slots_refresh_rate_limit(Duration::from_secs(0), 0)
            },
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            drop(cluster);
            let cmd = cmd("PING");

            let result = connection
                .route_command(&cmd, RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random))
                .await;
            // TODO - this should be a NoConnectionError, but ATM we get the errors from the failing
            assert!(result.is_err());

            // This will route to all nodes - different path through the code.
            let result = connection.req_packed_command(&cmd).await;
            // TODO - this should be a NoConnectionError, but ATM we get the errors from the failing
            assert!(result.is_err());

            let _cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(2),
                false,
            );

            let max_requests = 5;
            let mut i = 0;
            let mut last_err = None;
            loop {
                if i == max_requests {
                    break;
                }
                i += 1;
                match connection.req_packed_command(&cmd).await {
                    Ok(result) => {
                        assert_eq!(result, Value::SimpleString("PONG".to_string()));
                        return Ok::<_, RedisError>(());
                    }
                    Err(err) => {
                        last_err = Some(err);
                        let _ = sleep(futures_time::time::Duration::from_secs(1)).await;
                    }
                }
            }
            panic!("Failed to recover after all nodes went down. Last error: {last_err:?}");
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_reconnect_after_complete_server_disconnect_route_to_many() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(3),
            false,
        );
        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            drop(cluster);

            // recreate cluster
            let _cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(2),
                false,
            );

            let cmd = cmd("PING");

            let max_requests = 5;
            let mut i = 0;
            let mut last_err = None;
            loop {
                if i == max_requests {
                    break;
                }
                i += 1;
                // explicitly route to all primaries and request all succeeded
                match connection
                    .route_command(
                        &cmd,
                        RoutingInfo::MultiNode((
                            MultipleNodeRoutingInfo::AllMasters,
                            Some(redis::cluster_routing::ResponsePolicy::AllSucceeded),
                        )),
                    )
                    .await
                {
                    Ok(result) => {
                        assert_eq!(result, Value::SimpleString("PONG".to_string()));
                        return Ok::<_, RedisError>(());
                    }
                    Err(err) => {
                        last_err = Some(err);
                        let _ = sleep(futures_time::time::Duration::from_secs(1)).await;
                    }
                }
            }
            panic!("Failed to recover after all nodes went down. Last error: {last_err:?}");
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_blocking_command_when_cluster_drops() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(3),
            false,
        );
        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            futures::future::join(
                async {
                    let res = connection.blpop::<&str, f64>("foo", 0.0).await;
                    assert!(res.is_err());
                    println!("blpop returned error {:?}", res.map_err(|e| e.to_string()));
                },
                async {
                    let _ = sleep(futures_time::time::Duration::from_secs(3)).await;
                    drop(cluster);
                },
            )
            .await;
            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_saves_reconnected_connection() {
        let name = "test_async_cluster_saves_reconnected_connection";
        let ping_attempts = Arc::new(AtomicI32::new(0));
        let ping_attempts_clone = ping_attempts.clone();
        let get_attempts = AtomicI32::new(0);

        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(1),
            name,
            move |cmd: &[u8], port| {
                if port == 6380 {
                    respond_startup_two_nodes(name, cmd)?;
                    return Err(parse_redis_value(
                        format!("-MOVED 123 {name}:6379\r\n").as_bytes(),
                    ));
                }

                if contains_slice(cmd, b"PING") {
                    let connect_attempt = ping_attempts_clone.fetch_add(1, Ordering::Relaxed);
                    let past_get_attempts = get_attempts.load(Ordering::Relaxed);
                    // We want connection checks to fail after the first GET attempt, until it retries. Hence, we wait for 5 PINGs -
                    // 1. initial connection,
                    // 2. refresh slots on client creation,
                    // 3. refresh_connections `check_connection` after first GET failed,
                    // 4. refresh_connections `connect_and_check` after first GET failed,
                    // 5. reconnect on 2nd GET attempt.
                    // more than 5 attempts mean that the server reconnects more than once, which is the behavior we're testing against.
                    if past_get_attempts != 1 || connect_attempt > 3 {
                        respond_startup_two_nodes(name, cmd)?;
                    }
                    if connect_attempt > 5 {
                        panic!("Too many pings!");
                    }
                    Err(Err(RedisError::from((
                        ErrorKind::FatalSendError,
                        "mock-io-error",
                    ))))
                } else {
                    respond_startup_two_nodes(name, cmd)?;
                    let past_get_attempts = get_attempts.fetch_add(1, Ordering::Relaxed);
                    // we fail the initial GET request, and after that we'll fail the first reconnect attempt, in the `refresh_connections` attempt.
                    if past_get_attempts == 0 {
                        // Error once with io-error, ensure connection is reestablished w/out calling
                        // other node (i.e., not doing a full slot rebuild)
                        Err(Err(RedisError::from((
                            ErrorKind::FatalSendError,
                            "mock-io-error",
                        ))))
                    } else {
                        Err(Ok(Value::BulkString(b"123".to_vec())))
                    }
                }
            },
        );

        for _ in 0..4 {
            let value = runtime.block_on(
                cmd("GET")
                    .arg("test")
                    .query_async::<_, Option<i32>>(&mut connection),
            );

            assert_eq!(value, Ok(Some(123)));
        }
        // If you need to change the number here due to a change in the cluster, you probably also need to adjust the test.
        // See the PING counts above to explain why 5 is the target number.
        assert_eq!(ping_attempts.load(Ordering::Acquire), 5);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_periodic_checks_use_management_connection() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder.periodic_topology_checks(Duration::from_millis(10))
                // Disable the rate limiter to refresh slots immediately on the periodic checks
                .slots_refresh_rate_limit(Duration::from_secs(0), 0)
            },
            false,
        );

        block_on_all(async move {
        let mut connection = cluster.async_connection(None).await;
        let mut client_list = "".to_string();
        let max_requests = 1000;
        let mut i = 0;
        loop {
            if i == max_requests {
                break;
            } else {
                client_list = cmd("CLIENT")
                    .arg("LIST")
                    .query_async::<_, String>(&mut connection)
                    .await
                    .expect("Failed executing CLIENT LIST");
                let mut client_list_parts = client_list.split('\n');
                if client_list_parts
                .any(|line| line.contains(MANAGEMENT_CONN_NAME) && line.contains("cmd=cluster")) 
                && client_list.matches(MANAGEMENT_CONN_NAME).count() == 1 {
                    return Ok::<_, RedisError>(());
                }
            }
            i += 1;
            let _ = sleep(futures_time::time::Duration::from_millis(10)).await;
        }
        panic!("Couldn't find a management connection or the connection wasn't used to execute CLUSTER SLOTS {client_list:?}");
    })
    .unwrap();
    }

    async fn get_clients_names_to_ids(
        connection: &mut ClusterConnection,
        routing: Option<RoutingInfo>,
    ) -> HashMap<String, String> {
        let mut client_list_cmd = redis::cmd("CLIENT");
        client_list_cmd.arg("LIST");
        let value = match routing {
            Some(routing) => connection.route_command(&client_list_cmd, routing).await,
            None => connection.req_packed_command(&client_list_cmd).await,
        }
        .unwrap();
        let string = String::from_owned_redis_value(value).unwrap();
        string
            .split('\n')
            .filter_map(|line| {
                if line.is_empty() {
                    return None;
                }
                let key_values = line
                    .split(' ')
                    .filter_map(|value| {
                        let mut split = value.split('=');
                        match (split.next(), split.next()) {
                            (Some(key), Some(val)) => Some((key, val)),
                            _ => None,
                        }
                    })
                    .collect::<HashMap<_, _>>();
                match (key_values.get("name"), key_values.get("id")) {
                    (Some(key), Some(val)) if !val.is_empty() => {
                        Some((key.to_string(), val.to_string()))
                    }
                    _ => None,
                }
            })
            .collect()
    }

    async fn kill_connection(killer_connection: &mut ClusterConnection, connection_to_kill: &str) {
        let default_routing = RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
            Route::new(0, SlotAddr::Master),
        ));
        kill_connection_with_routing(killer_connection, connection_to_kill, default_routing).await;
    }

    async fn kill_connection_with_routing(
        killer_connection: &mut ClusterConnection,
        connection_to_kill: &str,
        routing: RoutingInfo,
    ) {
        let mut cmd = redis::cmd("CLIENT");
        cmd.arg("KILL");
        cmd.arg("ID");
        cmd.arg(connection_to_kill);
        // Kill the management connection for the routing node
        assert!(killer_connection.route_command(&cmd, routing).await.is_ok());
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_only_management_connection_is_reconnected_after_connection_failure() {
        // This test will check two aspects:
        // 1. Ensuring that after a disconnection in the management connection, a new management connection is established.
        // 2. Confirming that a failure in the management connection does not impact the user connection, which should remain intact.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.periodic_topology_checks(Duration::from_millis(10)),
            false,
        );
        block_on_all(async move {
        let mut connection = cluster.async_connection(None).await;
        let _client_list = "".to_string();
        let max_requests = 500;
        let mut i = 0;
        // Set the name of the client connection to 'user-connection', so we'll be able to identify it later on
        assert!(cmd("CLIENT")
            .arg("SETNAME")
            .arg("user-connection")
            .query_async::<_, Value>(&mut connection)
            .await
            .is_ok());
        // Get the client list
        let names_to_ids = get_clients_names_to_ids(&mut connection, Some(RoutingInfo::SingleNode(
            SingleNodeRoutingInfo::SpecificNode(Route::new(0, SlotAddr::Master))))).await;

        // Get the connection ID of 'user-connection'
        let user_conn_id = names_to_ids.get("user-connection").unwrap();
        // Get the connection ID of the management connection
        let management_conn_id = names_to_ids.get(MANAGEMENT_CONN_NAME).unwrap();
        // Get another connection that will be used to kill the management connection
        let mut killer_connection = cluster.async_connection(None).await;
        kill_connection(&mut killer_connection, management_conn_id).await;
        loop {
            // In this loop we'll wait for the new management connection to be established
            if i == max_requests {
                break;
            } else {
                let names_to_ids = get_clients_names_to_ids(&mut connection, Some(RoutingInfo::SingleNode(
                    SingleNodeRoutingInfo::SpecificNode(Route::new(0, SlotAddr::Master))))).await;
                if names_to_ids.contains_key(MANAGEMENT_CONN_NAME) {
                    // A management connection is found
                    let curr_management_conn_id =
                    names_to_ids.get(MANAGEMENT_CONN_NAME).unwrap();
                    let curr_user_conn_id =
                    names_to_ids.get("user-connection").unwrap();
                    // Confirm that the management connection has a new connection ID, and verify that the user connection remains unaffected.
                    if (curr_management_conn_id != management_conn_id)
                        && (curr_user_conn_id == user_conn_id)
                    {
                        return Ok::<_, RedisError>(());
                    }
                } else {
                    i += 1;
                    let _ = sleep(futures_time::time::Duration::from_millis(50)).await;
                    continue;
                }
            }
        }
        panic!(
            "No reconnection of the management connection found, or there was an unwantedly reconnection of the user connections.
            \nprev_management_conn_id={management_conn_id:?},prev_user_conn_id={user_conn_id:?}\nclient list={names_to_ids:?}"
        );
    })
    .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_dont_route_to_a_random_on_non_key_based_cmd() {
        // This test verifies that non-key-based commands do not get routed to a random node
        // when no connection is found for the given route. Instead, the appropriate error
        // should be raised.
        let name = "test_async_cluster_dont_route_to_a_random_on_non_key_based_cmd";
        let request_counter = Arc::new(AtomicU32::new(0));
        let cloned_req_counter = request_counter.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]).retries(1),
            name,
            move |received_cmd: &[u8], _| {
                let slots_config_vec = vec![
                    MockSlotRange {
                        primary_port: 6379,
                        replica_ports: vec![],
                        slot_range: (0_u16..8000_u16),
                    },
                    MockSlotRange {
                        primary_port: 6380,
                        replica_ports: vec![],
                        // Don't cover all slots
                        slot_range: (8001_u16..12000_u16),
                    },
                ];
                respond_startup_with_config(name, received_cmd, Some(slots_config_vec), false)?;
                // If requests are sent to random nodes, they will be caught and counted here.
                request_counter.fetch_add(1, Ordering::Relaxed);
                Err(Ok(Value::Nil))
            },
        );

        runtime
            .block_on(async move {
                let uncovered_slot = 16000;
                let route = redis::cluster_routing::Route::new(
                    uncovered_slot,
                    redis::cluster_routing::SlotAddr::Master,
                );
                let single_node_route =
                    redis::cluster_routing::SingleNodeRoutingInfo::SpecificNode(route);
                let routing = RoutingInfo::SingleNode(single_node_route);
                let res = connection
                    .route_command(&redis::cmd("FLUSHALL"), routing)
                    .await;
                assert!(res.is_err());
                let res_err = res.unwrap_err();
                assert_eq!(
                    res_err.kind(),
                    ErrorKind::ConnectionNotFoundForRoute,
                    "{res_err:?}"
                );
                assert_eq!(cloned_req_counter.load(Ordering::Relaxed), 0);
                Ok::<_, RedisError>(())
            })
            .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_route_to_random_on_key_based_cmd() {
        // This test verifies that key-based commands get routed to a random node
        // when no connection is found for the given route. The command should
        // then be redirected correctly by the server's MOVED error.
        let name = "test_async_cluster_route_to_random_on_key_based_cmd";
        let request_counter = Arc::new(AtomicU32::new(0));
        let cloned_req_counter = request_counter.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            handler: _handler,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")]),
            name,
            move |received_cmd: &[u8], _| {
                let slots_config_vec = vec![
                    MockSlotRange {
                        primary_port: 6379,
                        replica_ports: vec![],
                        slot_range: (0_u16..8000_u16),
                    },
                    MockSlotRange {
                        primary_port: 6380,
                        replica_ports: vec![],
                        // Don't cover all slots
                        slot_range: (8001_u16..12000_u16),
                    },
                ];
                respond_startup_with_config(name, received_cmd, Some(slots_config_vec), false)?;
                if contains_slice(received_cmd, b"GET") {
                    if request_counter.fetch_add(1, Ordering::Relaxed) == 0 {
                        return Err(parse_redis_value(
                            format!("-MOVED 12182 {name}:6380\r\n").as_bytes(),
                        ));
                    } else {
                        return Err(Ok(Value::SimpleString("bar".into())));
                    }
                }
                panic!("unexpected command {received_cmd:?}");
            },
        );

        runtime
            .block_on(async move {
                // The keyslot of "foo" is 12182 and it isn't covered by any node, so we expect the
                // request to be routed to a random node and then to be redirected to the MOVED node (2 requests in total)
                let res: String = connection.get("foo").await.unwrap();
                assert_eq!(res, "bar".to_string());
                assert_eq!(cloned_req_counter.load(Ordering::Relaxed), 2);
                Ok::<_, RedisError>(())
            })
            .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_do_not_retry_when_receiver_was_dropped() {
        let name = "test_async_cluster_do_not_retry_when_receiver_was_dropped";
        let cmd = cmd("FAKE_COMMAND");
        let packed_cmd = cmd.get_packed_command();
        let request_counter = Arc::new(AtomicU32::new(0));
        let cloned_req_counter = request_counter.clone();
        let MockEnv {
            runtime,
            async_connection: mut connection,
            ..
        } = MockEnv::with_client_builder(
            ClusterClient::builder(vec![&*format!("redis://{name}")])
                .retries(5)
                .max_retry_wait(2)
                .min_retry_wait(2),
            name,
            move |received_cmd: &[u8], _| {
                respond_startup(name, received_cmd)?;

                if received_cmd == packed_cmd {
                    cloned_req_counter.fetch_add(1, Ordering::Relaxed);
                    return Err(Err((ErrorKind::TryAgain, "seriously, try again").into()));
                }

                Err(Ok(Value::Okay))
            },
        );

        runtime.block_on(async move {
            let err = cmd
                .query_async::<_, Value>(&mut connection)
                .timeout(futures_time::time::Duration::from_millis(1))
                .await
                .unwrap_err();
            assert_eq!(err.kind(), std::io::ErrorKind::TimedOut);

            // we sleep here, to allow the cluster connection time to retry. We expect it won't, but without this
            // sleep the test will complete before the the runtime gave the connection time to retry, which would've made the
            // test pass regardless of whether the connection tries retrying or not.
            sleep(Duration::from_millis(10).into()).await;
        });

        assert_eq!(request_counter.load(Ordering::Relaxed), 1);
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fenced_command_with_successful_response() {
        // Test fenced command returns correct value (command result, then PONG).

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            let _: () = connection.set("test_key", "test_value").await.unwrap();

            let mut fenced_cmd = fenced_cmd("GET");
            fenced_cmd.arg("test_key");

            let result: String = fenced_cmd.query_async(&mut connection).await.unwrap();
            assert_eq!(result, "test_value");

            // Verify ordering is correct after fenced command
            let result: String = cmd("GET")
                .arg("test_key")
                .query_async(&mut connection)
                .await
                .unwrap();
            assert_eq!(result, "test_value");

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fenced_command_with_server_error() {
        // Test fenced command correctly returns server error (error, then PONG).

        let key = "type_error_test_key";

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            // Set key to string value
            let _: () = connection.set(key, "hello").await.unwrap();

            // Try LPUSH to string key (will fail with WRONGTYPE)
            let mut lpush_cmd = fenced_cmd("LPUSH");
            lpush_cmd.arg(key).arg("1");

            let result = lpush_cmd.query_async::<_, Value>(&mut connection).await;

            match result {
                Err(e) if e.kind() == ErrorKind::ExtensionError => {
                    assert_eq!(e.code(), Some("WRONGTYPE"));
                }
                Err(e) => panic!("Expected WRONGTYPE error but got {:?}: {}", e.kind(), e),
                Ok(val) => panic!("Expected Err(WRONGTYPE) but got Ok: {:?}", val),
            }

            // Verify connection still works
            let ping_result: String = cmd("PING").query_async(&mut connection).await.unwrap();
            assert_eq!(ping_result, "PONG");

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fenced_command_with_connection_error() {
        // Test fenced command correctly handles connection errors.

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            let mut fenced_cmd = fenced_cmd("GET");
            fenced_cmd.arg("some_key");

            // Drop cluster to kill all connections
            drop(cluster);

            let result = connection.req_packed_command(&fenced_cmd).await;

            assert!(
                result
                    .as_ref()
                    .is_err_and(|e| e.kind() == ErrorKind::FatalSendError
                        || e.kind() == ErrorKind::FatalReceiveError),
                "Expected connection error, but got: {:?}",
                result
            );

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fenced_sunsubscribe_with_moved_error() {
        // Test fenced SUNSUBSCRIBE receives MOVED error after slot migration,
        // verifying the fenced command logic handles it correctly.
        // This scenario is similar to the one described in - https://github.com/valkey-io/valkey/issues/1066

        let channel = "fenced_sunsubscribe_test_channel";
        let channel_slot = get_slot(channel.as_bytes());

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            let _: () = cmd("SSUBSCRIBE")
                .arg(channel)
                .query_async(&mut connection)
                .await
                .unwrap();

            let old_node_route = RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
                Route::new(channel_slot, SlotAddr::Master),
            ));

            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);
            cluster
                .move_specific_slot(channel_slot, slot_distribution)
                .await;

            let mut unsubscribe_cmd = fenced_cmd("SUNSUBSCRIBE");
            unsubscribe_cmd.arg(channel);

            let result = connection
                .route_command(&unsubscribe_cmd, old_node_route)
                .await;

            match result {
                Err(e) if e.kind() == ErrorKind::Moved => {}
                Err(e) => panic!(
                    "Expected Err(MOVED) but got different error: kind={:?}, detail={:?}",
                    e.kind(),
                    e.detail()
                ),
                Ok(val) => panic!("Expected Err(MOVED) but got Ok: {:?}", val),
            }

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fenced_sunsubscribe_with_slot_deletion_error() {
        // Test fenced SUNSUBSCRIBE receives error after slot deletion,
        // verifying the fenced command logic handles it correctly.
        // This scenario is similar to the one described in - https://github.com/valkey-io/valkey/issues/1066

        let channel = "fenced_sunsubscribe_deletion_test_channel";
        let channel_slot = get_slot(channel.as_bytes());

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            let _: () = cmd("SSUBSCRIBE")
                .arg(channel)
                .query_async(&mut connection)
                .await
                .unwrap();

            let old_node_route = RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
                Route::new(channel_slot, SlotAddr::Master),
            ));

            cluster.delete_specific_slot(channel_slot, None).await;

            let mut unsubscribe_cmd = fenced_cmd("SUNSUBSCRIBE");
            unsubscribe_cmd.arg(channel);

            let result = connection
                .route_command(&unsubscribe_cmd, old_node_route)
                .await;

            // Verify we got a server error (slot no longer owned by this node)
            match result {
                Err(e) if e.kind() == ErrorKind::ClusterDown => {}
                Err(e) => panic!("Expected CLUSTERDOWN error but got {:?}: {}", e.kind(), e),
                Ok(val) => panic!("Expected Err(CLUSTERDOWN) but got Ok: {:?}", val),
            }

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_fenced_sunsubscribe_successful() {
        // Test fenced SUNSUBSCRIBE correctly handles PONG as the response of the command,
        // indicating the fenced command completed successfully.

        let channel = "successful_sunsubscribe_test_channel";

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            let _: () = cmd("SSUBSCRIBE")
                .arg(channel)
                .query_async(&mut connection)
                .await
                .unwrap();

            let mut unsubscribe_cmd = fenced_cmd("SUNSUBSCRIBE");
            unsubscribe_cmd.arg(channel);

            let result = unsubscribe_cmd
                .query_async::<_, Value>(&mut connection)
                .await;

            assert!(
                matches!(result, Ok(Value::Nil)),
                "Expected Nil but got: {:?}",
                result
            );

            // Verify ordering is still correct
            let ping_result: String = cmd("PING").query_async(&mut connection).await.unwrap();
            assert_eq!(ping_result, "PONG");

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_multiple_fenced_commands_sequential() {
        // Test multiple fenced commands sent sequentially each receive correct responses.

        let channels = vec![
            "fenced_seq_channel_1",
            "fenced_seq_channel_2",
            "fenced_seq_channel_3",
            "fenced_seq_channel_4",
        ];

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0).use_protocol(ProtocolVersion::RESP3),
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;

            // Subscribe to all channels
            for channel in &channels {
                let _: () = cmd("SSUBSCRIBE")
                    .arg(channel)
                    .query_async(&mut connection)
                    .await
                    .unwrap();
            }

            // Send fenced SUNSUBSCRIBE for each channel
            for channel in &channels {
                let mut unsubscribe_cmd = fenced_cmd("SUNSUBSCRIBE");
                unsubscribe_cmd.arg(channel);

                let result = unsubscribe_cmd
                    .query_async::<_, Value>(&mut connection)
                    .await;

                assert!(
                    matches!(result, Ok(Value::Nil)),
                    "Expected Nil for channel '{}' but got: {:?}",
                    channel,
                    result
                );
            }

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_async_cluster_response_ordering_during_slot_migration() {
        // Verifies that if there's an unrelated InFlight request while receiving an unprompted
        // SUNSUBSCRIBE push notification, it does not interfere with response ordering

        let channel = "migration_test_channel";

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder
                    .retries(3)
                    .use_protocol(ProtocolVersion::RESP3)
                    .slots_refresh_rate_limit(Duration::from_secs(0), 0)
            },
            false,
        );

        block_on_all(async move {
            let mut connection = cluster.async_connection(None).await;
            let mut push_connection = cluster.async_connection(None).await;

            let _: () = cmd("SSUBSCRIBE")
                .arg(channel)
                .query_async(&mut connection)
                .await
                .unwrap();

            let channel_slot = get_slot(channel.as_bytes());

            // Start blocking BLPOP
            let blpop_key = "blocking_list_key";
            let expected_value = "test_value";
            let mut connection_clone = connection.clone();
            let blpop_handle = tokio::spawn(async move {
                connection_clone
                    .blpop::<_, Option<(String, String)>>(blpop_key, 10.0)
                    .await
            });

            // Move slot to trigger unprompted SUNSUBSCRIBE
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);
            cluster
                .move_specific_slot(channel_slot, slot_distribution)
                .await;

            // Push value to unblock BLPOP
            let _: () = push_connection
                .rpush(blpop_key, expected_value)
                .await
                .unwrap();

            // Verify BLPOP received correct value
            let blpop_result = blpop_handle.await.unwrap();
            match blpop_result {
                Ok(Some((key, value))) => {
                    assert_eq!(key, blpop_key);
                    assert_eq!(value, expected_value);
                }
                Ok(None) => panic!("BLPOP timed out"),
                Err(e) => panic!("BLPOP failed: {:?}", e),
            }

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    #[test]
    #[serial_test::serial]
    fn test_protocol_desync_when_fenced_command_fails() {
        let test_user = "test_desync_user";
        let test_password = "test_password";

        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| {
                builder
                    .retries(0)
                    .use_protocol(ProtocolVersion::RESP3)
                    .slots_refresh_rate_limit(Duration::from_secs(0), 0)
            },
            false,
        );

        block_on_all(async {
            // Use admin connection for ACL commands (default user)
            let mut admin_connection = cluster.async_connection(None).await;

            // Set up test user with PING initially to allow connection setup
            let _: () = redis::cmd("ACL")
                .arg("SETUSER")
                .arg(test_user)
                .arg("on")
                .arg(&format!(">{}", test_password))
                .arg("+subscribe")
                .arg("+ssubscribe")
                .arg("+sunsubscribe")
                .arg("+unsubscribe")
                .arg("+ping")      // Allow PING initially
                .arg("+cluster")   // Allow CLUSTER commands for topology discovery
                .arg("allkeys")
                .query_async(&mut admin_connection)
                .await
                .unwrap();

            // Create a separate client for test user
            let test_user_client = ClusterClient::builder(cluster.nodes.clone())
                .use_protocol(ProtocolVersion::RESP3)
                .username(test_user.to_string())
                .password(test_password.to_string())
                .build()
                .unwrap();

            let mut connection = test_user_client
                .get_async_connection(None, None)
                .await
                .unwrap();

            // Now revoke PING permission
            let _: () = redis::cmd("ACL")
                .arg("SETUSER")
                .arg(test_user)
                .arg("-ping")  // Revoke PING
                .query_async(&mut admin_connection)
                .await
                .unwrap();

            // Try fenced command (will fail because PING is denied)
            let mut cmd = fenced_cmd("SET");
            cmd.arg("test_key");
            cmd.arg("test_value");

            let result = cmd.query_async::<_, Value>(&mut connection).await;

            // Verify - should fail with ProtocolDesync
            match result {
                Err(e) if e.kind() == ErrorKind::ProtocolDesync => {}
                Err(e) => panic!(
                    "Expected ProtocolDesync error but got {:?}: {}",
                    e.kind(),
                    e
                ),
                Ok(val) => panic!("Expected Err(ProtocolDesync) but got Ok: {:?}", val),
            }

            // Verify: Subsequent command also fails with ProtocolDesync
            let subsequent_result = cmd.query_async::<_, String>(&mut connection).await;
            assert!(subsequent_result.is_err(), "Subsequent PING should fail");
            assert_eq!(
                subsequent_result.unwrap_err().kind(),
                ErrorKind::ProtocolDesync
            );

            // Cleanup: Delete test user
            let _: () = redis::cmd("ACL")
                .arg("DELUSER")
                .arg(test_user)
                .query_async(&mut admin_connection)
                .await
                .unwrap();

            Ok::<_, RedisError>(())
        })
        .unwrap();
    }

    mod mtls_test {
        use crate::support::mtls_test::create_cluster_client_from_cluster;
        use redis::ConnectionInfo;

        use super::*;

        #[test]
        #[serial_test::serial]
        fn test_async_cluster_basic_cmd_with_mtls() {
            let cluster = TestClusterContext::new_with_mtls(3, 0);
            block_on_all(async move {
                let client = create_cluster_client_from_cluster(&cluster, true).unwrap();
                let mut connection = client.get_async_connection(None, None).await.unwrap();
                cmd("SET")
                    .arg("test")
                    .arg("test_data")
                    .query_async::<_, ()>(&mut connection)
                    .await?;
                let res: String = cmd("GET")
                    .arg("test")
                    .clone()
                    .query_async(&mut connection)
                    .await?;
                assert_eq!(res, "test_data");
                Ok::<_, RedisError>(())
            })
            .unwrap();
        }

        #[test]
        #[serial_test::serial]
        fn test_async_cluster_should_not_connect_without_mtls_enabled() {
            let cluster = TestClusterContext::new_with_mtls(3, 0);
            block_on_all(async move {
            let client = create_cluster_client_from_cluster(&cluster, false).unwrap();
            let connection = client.get_async_connection(None, None).await;
            match cluster.cluster.servers.first().unwrap().connection_info() {
                ConnectionInfo {
                    addr: redis::ConnectionAddr::TcpTls { .. },
                    ..
            } => {
                if connection.is_ok() {
                    panic!("Must NOT be able to connect without client credentials if server accepts TLS");
                }
            }
            _ => {
                if let Err(e) = connection {
                    panic!("Must be able to connect without client credentials if server does NOT accept TLS: {e:?}");
                }
            }
            }
            Ok::<_, RedisError>(())
        }).unwrap();
        }
    }
}
