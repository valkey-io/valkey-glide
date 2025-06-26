#![cfg(feature = "cluster-async")]
mod support;

// TODO: add connection kill with retry
mod test_cluster_pipeline {

    use redis::{cluster_async::ClusterConnection, cluster_topology::get_slot, ErrorKind};
    use std::collections::HashMap;

    use redis::{
        cluster::ClusterClient,
        cluster_routing::{
            MultipleNodeRoutingInfo, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
        },
        cmd, Pipeline, PipelineRetryStrategy, Value,
    };

    use crate::support::*;

    fn is_slot_in_ranges(slot: u16, ranges: Vec<Vec<u16>>) -> bool {
        for range in ranges {
            if slot >= range[0] && slot <= range[1] {
                return true;
            }
        }
        false
    }

    fn is_in_same_node(slot1: u16, slot2: u16, nodes_and_slots: Vec<(&String, Vec<u16>)>) -> bool {
        for (_, slots) in nodes_and_slots {
            if slots[0] <= slot1 && slot1 <= slots[1] && slots[0] <= slot2 && slot2 <= slots[1] {
                return true;
            }
        }
        false
    }

    fn generate_2_keys_in_the_same_node(
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

    fn generate_2_keys_in_different_node(
        nodes_and_slots: Vec<(&String, Vec<u16>)>,
    ) -> (String, String) {
        for _ in 0..1000 {
            let key = generate_random_string(10);
            let key2 = generate_random_string(10);
            let slot = get_slot(key.as_bytes());
            let slot2 = get_slot(key2.as_bytes());

            if !is_in_same_node(slot, slot2, nodes_and_slots.clone()) {
                return (key, key2);
            }
        }
        panic!("Failed to 2 keys after 1000 attempts");
    }

    fn generate_2_keys_in_different_slots() -> (String, String) {
        for _ in 0..1000 {
            let key = generate_random_string(10);
            let key2 = generate_random_string(10);
            let slot = get_slot(key.as_bytes());
            let slot2 = get_slot(key2.as_bytes());

            if slot != slot2 {
                return (key, key2);
            }
        }
        panic!("Failed to find 2 keys in different slots after 1000 attempts");
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

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_with_killed_node() {
        // Setup a cluster with 3 nodes, no replicas.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(1),
            false,
        );
        let mut connection = cluster.async_connection(None).await;

        // Set cluster-require-full-coverage to no
        let mut config_cmd = cmd("CONFIG");
        config_cmd
            .arg("SET")
            .arg("cluster-require-full-coverage")
            .arg("no");

        connection
            .route_command(
                &config_cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .expect("Failed to set cluster-require-full-coverage to no");

        // Get the initial slot distribution.
        let cluster_nodes = cluster.get_cluster_nodes().await;
        let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

        // Pick a random "bad" key and compute its slot.
        let bad_key = generate_random_string(10);
        let bad_slot = get_slot(bad_key.as_bytes());

        // Kill the node that handles the bad_key's slot.
        let killed_node_routing = cluster
            .kill_one_node(slot_distribution.clone(), Some(bad_slot))
            .await;

        // Wait for cluster failover / recovery.
        cluster
            .wait_for_fail_to_finish(&killed_node_routing)
            .await
            .expect("Failover did not finish");

        // Get the new slot distribution.
        let new_cluster_nodes = cluster.get_cluster_nodes().await;
        let new_slot_distribution = cluster.get_slots_ranges_distribution(&new_cluster_nodes);

        assert_ne!(slot_distribution, new_slot_distribution, "Slot distribution did not change after killing a node with routing: {killed_node_routing:?}, before: {slot_distribution:?}, after: {new_slot_distribution:?}");

        // Extract the slots from the new slot distribution.
        let slots = new_slot_distribution
            .iter()
            .map(|(_, _, _, v)| v[0].clone())
            .collect::<Vec<_>>();

        // Pick a "good" key whose slot is NOT in the killed node's ranges.
        let good_key_finder = || -> String {
            for _ in 0..1000 {
                let candidate = generate_random_string(10);
                let candidate_slot = get_slot(candidate.as_bytes());
                if is_slot_in_ranges(candidate_slot, slots.clone()) {
                    return candidate;
                }
            }
            panic!("Failed to find a good key after 1000 attempts");
        };

        let good_key = good_key_finder();
        let good_key2 = good_key_finder();

        let mut pipeline = Pipeline::new();
        pipeline.set(&bad_key, "value");
        pipeline.get(&bad_key);
        pipeline.cmd("PING");
        pipeline.set(&good_key, "value");
        pipeline.get(&good_key);
        pipeline.set(&good_key2, "value2");
        pipeline.get(&good_key2);

        // Execute the pipeline.
        let res = connection
            .route_pipeline(
                &pipeline,
                0,
                pipeline.len(),
                None,
                Some(PipelineRetryStrategy {
                    retry_server_error: true,
                    retry_connection_error: false,
                }),
            )
            .await
            .expect("Failed to execute pipeline");

        // Assert that the pipeline with the good key succeeds.
        let expected = vec![
            Value::Okay,
            Value::BulkString(b"value".to_vec()),
            Value::Okay,
            Value::BulkString(b"value2".to_vec()),
        ];
        assert_eq!(
            &res[3..],
            expected,
            "Pipeline returned unexpected result. \
            Good keys: '{}' '{}', result: {:?}",
            good_key,
            good_key2,
            &res[2..]
        );

        let bad_results = &res[0..3];
        assert!(
            bad_results
                .iter()
                .all(|r| matches!(r, Value::ServerError(err) if err.err_code().contains("ConnectionNotFound"))),
            "Expected server error responses for the bad key operations, but got: {bad_results:?}"
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_with_moved_error() {
        for retries in [1, 0] {
            // Create a test cluster with 3 masters and no replicas.
            let cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(retries),
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

            let (moved_key, key2) = generate_2_keys_in_the_same_node(nodes_and_slots);
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

            if retries > 0 {
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
            } else {
                assert!(
                    matches!(result[0], Value::Int(5)),
                    "Expected a successful INCR result, but got {:?}",
                    result[0]
                );

                assert!(
                    matches!(result[1], Value::ServerError(ref err) if err.kind() == ErrorKind::Moved),
                    "Expected a server error response for the SET command, but got {:?}",
                    result[1]
                );

                assert!(
                    matches!(result[2], Value::ServerError(ref err) if err.kind() == ErrorKind::Moved),
                    "Expected a server error response for the GET command, but got {:?}",
                    result[2]
                );
            }

            assert_error_occurred(&mut connection, "MOVED", 2).await;
        }
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_transaction_with_moved_error_with_retries() {
        for retries in [1, 0] {
            // Create a test cluster with 3 masters and no replicas.
            let cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(retries),
                false,
            );
            let mut connection = cluster.async_connection(None).await;

            // Get the current slot distribution.
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let moved_key = generate_random_string(10);
            let key_slot = get_slot(moved_key.as_bytes());
            let route = SingleNodeRoutingInfo::SpecificNode(Route::new(key_slot, SlotAddr::Master));

            cluster
                .move_specific_slot(key_slot, slot_distribution)
                .await;

            // Create a pipeline with several commands.
            let mut pipeline = redis::pipe();
            pipeline.atomic().set(&moved_key, "value").get(&moved_key);

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

            if retries == 1 {
                let expected = vec![Value::Array(vec![
                    Value::Okay,
                    Value::BulkString(b"value".to_vec()),
                ])];
                assert!(result.is_ok());
                let response = result.unwrap();
                assert_eq!(
                    response, expected,
                    "Pipeline result did not match expected output.\n\
                        Keys chosen: ('{moved_key}')\n\
                        key_slot: {key_slot}\n\
                        Actual result: {response:?}"
                );
            } else {
                assert!(result.is_err());
                assert!(result.unwrap_err().kind() == ErrorKind::Moved);
            }
            assert_error_occurred(&mut connection, "MOVED", 2).await;
        }
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_with_specific_route() {
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

        // Pick a random key and compute its slot.
        let (key, key2) = generate_2_keys_in_the_same_node(nodes_and_slots);
        let key_slot = get_slot(key.as_bytes());

        // Define specific routing for the key's master node.
        let route = SingleNodeRoutingInfo::SpecificNode(Route::new(key_slot, SlotAddr::Master));

        // Create a pipeline that sets and then retrieves the key.
        let mut pipeline = redis::pipe();
        pipeline.set(&key, "pipeline_value");
        pipeline.get(&key);
        pipeline.set(&key2, "pipeline_value2");
        pipeline.get(&key2);

        // Execute the pipeline using the specified route.
        let result = connection
            .route_pipeline(&pipeline, 0, pipeline.len(), Some(route), None)
            .await
            .expect("Pipeline execution failed");

        // Verify the pipeline result.
        let expected = vec![
            Value::Okay,
            Value::BulkString(b"pipeline_value".to_vec()),
            Value::Okay,
            Value::BulkString(b"pipeline_value2".to_vec()),
        ];

        assert_eq!(
            result, expected,
            "Pipeline result did not match expected output"
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_with_wrong_route() {
        // Create a test cluster with 3 masters and no replicas.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(1),
            false,
        );
        for atomic in [true, false] {
            let mut connection = cluster.async_connection(None).await;
            // Get the current slot distribution.
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let nodes_and_slots = slot_distribution
                .iter()
                .map(|(node_id, _, _, v)| (node_id, v[0].clone()))
                .collect::<Vec<_>>();

            let (key, mut key2) = generate_2_keys_in_different_node(nodes_and_slots);
            let different_node_slot = get_slot(key2.as_bytes());
            if atomic {
                key2 = format!("{{{}}}:{}", key, generate_random_string(5));
            }
            let key_slot = get_slot(key.as_bytes());

            // Define a routing that intentionally targets the wrong node.
            let wrong_slot = if atomic {
                different_node_slot
            } else {
                key_slot
            };
            let route =
                SingleNodeRoutingInfo::SpecificNode(Route::new(wrong_slot, SlotAddr::Master));

            // Create a pipeline that sets and then retrieves the key.
            let mut pipeline = redis::pipe();
            if atomic {
                pipeline.atomic();
            }
            pipeline.set(&key, "pipeline_value");
            pipeline.get(&key);
            pipeline.set(&key2, "pipeline_value2");
            pipeline.get(&key2);

            let (offset, count) = match atomic {
                false => (0, 4),
                true => (5, 1),
            };

            // Execute the pipeline using the wrong route.
            let result = connection
                .route_pipeline(&pipeline, offset, count, Some(route), None)
                .await
                .expect("Pipeline execution failed");

            let inner = vec![
                Value::Okay,
                Value::BulkString(b"pipeline_value".to_vec()),
                Value::Okay,
                Value::BulkString(b"pipeline_value2".to_vec()),
            ];

            let expected = if atomic {
                vec![Value::Array(inner)]
            } else {
                inner
            };

            assert_eq!(
                result, expected,
                "Pipeline result did not match expected output {result:?}"
            );

            let moved_count = match atomic {
                true => 4,
                false => 2,
            };

            assert_error_occurred(&mut connection, "MOVED", moved_count).await;

            // Assert that the pipeline handled redirection.
            let mut reset_cmd = cmd("CONFIG");
            reset_cmd.arg("RESETSTAT");
            let reset = connection
                .route_command(
                    &reset_cmd,
                    RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
                )
                .await
                .expect("Failed to reset stats");
            let map_res = match reset {
                Value::Map(map) => map.into_iter().map(|x| x.1).collect::<Vec<_>>(),
                _ => unreachable!(),
            };

            assert!(map_res.iter().all(|res| *res == Value::Okay));
        }
    }

    #[tokio::test]
    #[serial_test::serial]

    async fn test_mset_mget_with_moved_error() {
        for &retries in &[0, 1] {
            // Create a test cluster with 3 masters and no replicas.
            let cluster = TestClusterContext::new_with_cluster_client_builder(
                3,
                0,
                |builder| builder.retries(retries),
                false,
            );
            let mut connection = cluster.async_connection(None).await;

            // Get the current slot distribution.
            let cluster_nodes = cluster.get_cluster_nodes().await;
            let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

            let (moved_key, key2) = generate_2_keys_in_different_slots();
            let moved_slot = get_slot(moved_key.as_bytes());

            // Move the slot for the first key to simulate a MOVED error.
            cluster
                .move_specific_slot(moved_slot, slot_distribution.clone())
                .await;

            // Execute MSET for both keys.
            let mset_pairs = vec![(moved_key.clone(), "value1"), (key2.clone(), "2")];
            let mut pipeline = redis::pipe();
            pipeline
                .mset(&mset_pairs)
                .incr(&key2, 1) // Increment key2 to ensure modification
                .del(&moved_key) // Delete moved_key before re-setting it
                .set(&moved_key, "value1") // Re-set moved_key with a different value
                .mget(&[moved_key.clone(), key2.clone()]);

            // Execute the pipeline.
            let result = connection
                .route_pipeline(
                    &pipeline,
                    0,
                    pipeline.len(),
                    None,
                    Some(PipelineRetryStrategy {
                        retry_server_error: retries > 0,
                        retry_connection_error: false,
                    }),
                )
                .await
                .expect("Pipeline execution failed");

            if retries == 1 {
                assert_eq!(
                    result,
                    vec![
                        Value::Okay,
                        Value::Int(3),
                        Value::Int(1),
                        Value::Okay,
                        Value::Array(vec![
                            Value::BulkString(b"value1".to_vec()),
                            Value::BulkString(b"3".to_vec())
                        ])
                    ],
                    "Expected MSET to succeed and MGET to return the values, but got: {result:?} key: {moved_key} key2: {key2}"
                );
            } else {
                assert!(
                    matches!(result[0], Value::ServerError(ref err) if err.kind() == ErrorKind::Moved),
                    "Expected a server error response for the MSET command, but got {:?}",
                    result[0]
                );
                assert_eq!(
                    result[1],
                    Value::Int(3),
                    "Expected the INCR command to succeed, but got {:?}",
                    result[1]
                );
                assert!(
                    matches!(result[2], Value::ServerError(ref err) if err.kind() == ErrorKind::Moved),
                    "Expected a server error response for the DEL command, but got {:?}",
                    result[2]
                );
                assert!(
                    matches!(result[3], Value::ServerError(ref err) if err.kind() == ErrorKind::Moved),
                    "Expected a server error response for the SET command, but got {:?}",
                    result[3]
                );

                assert!(
                    matches!(result[4], Value::ServerError(ref err) if err.kind() == ErrorKind::Moved),
                    "Expected a server error response for the MGET command, but got {:?}",
                    result[4]
                );
            }

            assert_error_occurred(&mut connection, "MOVED", 4).await;
        }
    }

    /// Tests pipeline execution behavior in a  Cluster when encountering ASK and TRYAGAIN errors due to slot migration.
    ///
    /// - **ASK errors** are redirection errors and should always be rerouted to the correct node, regardless of the retry configuration.
    /// - **TRYAGAIN errors** occur when a slot is in the process of being migrated, and the client attempts to execute multi-key commands with `ASKING`.
    ///   - These errors are considered retryable and should be retried based on the retry configuration.
    ///
    /// The test verifies whether the pipeline correctly handles these errors under different retry settings.
    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_with_ask_and_try_again_errors() {
        // Create a test cluster with 3 masters and no replicas.
        for retry in [false, true] {
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

                connection
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
                    .expect("Pipeline execution failed")
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

            match retry {
                true => {
                    // When retry is enabled, TRYAGAIN errors should be retried, and `MGET` should return expected values.
                    assert_eq!(
                        result[2],
                        Value::Array(vec![Value::BulkString(b"value".to_vec()), Value::Nil]),
                        "Pipeline result did not match expected output.\n\
                         Keys chosen: ('{migrated_key}', '{key}')\n\
                         key_slot: {key_slot}\n\
                         Actual result: {result:?}"
                    );
                }
                false => {
                    // When retry is disabled, TRYAGAIN errors should not be retried, and the error should be present in the response.
                    assert!(
                        matches!(result[2], Value::ServerError(ref err) if err.kind() == ErrorKind::TryAgain),
                        "Pipeline result did not match expected output.\n\
                     Keys chosen: ('{migrated_key}', '{key}')\n\
                     key_slot: {key_slot}\n\
                     Actual result: {result:?}"
                    );
                }
            }

            assert_error_occurred(&mut connection, "ASK", 3).await;
            assert_error_occurred(&mut connection, "TRYAGAIN", 1).await;
        }
    }

    /// Tests pipeline retry behavior when encountering connection errors.
    ///
    /// This test is executed twice—once with `retry_connection_error = false` and once with `retry_connection_error = true`.
    ///
    /// **Execution flow:**
    /// 1. Spawn `kill_conn_future` to sleep 500 ms then drop all connections on `connection`, simulating a `RecvError`.
    /// 2. Build and dispatch a Redis pipeline:
    ///    ```rust
    ///    pipeline.incr(&key, 1)
    ///           .blpop(&key2, 2.0)
    ///           .set(&key3, "value");
    ///    ```
    /// 3. Call `route_pipeline(...)` with
    ///    `PipelineRetryStrategy { retry_server_error: false, retry_connection_error: <toggle> }`.
    /// 4. Join the kill task and the pipeline future, then assert based on the retry setting.
    ///
    /// - **Without retry** (`retry_connection_error = false`):
    ///   - The pipeline is **not** retried.
    ///   - `INCR` and `BLPOP` both hit a connection error and return `ServerError(RecvError)`.
    ///   - `SET` on `key3` succeeds (`Value::Okay`) because it targets a different node and completes before the kill.
    ///   - **Expected:**  
    ///     `[ServerError(RecvError), ServerError(RecvError), Value::Okay]`
    ///
    /// - **With retry** (`retry_connection_error = true`):
    ///   - After the connection drops, the entire pipeline is retried on a fresh connection.
    ///   - `INCR` runs twice (once before drop, once on retry), so its final value is `2`.
    ///   - `BLPOP` returns `Nil` on retry.
    ///   - `SET` still succeeds (`Value::Okay`).
    ///   - **Expected:**  
    ///     `[Value::Int(2), Value::Nil, Value::Okay]`
    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_kill_all_connections() {
        for retry in [false, true] {
            // Create a test cluster with 3 masters and no replicas.
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
            let nodes_and_slots = slot_distribution
                .iter()
                .map(|(node_id, _, _, v)| (node_id, v[0].clone()))
                .collect::<Vec<_>>();
            let (key, key3) = generate_2_keys_in_different_node(nodes_and_slots);
            let key2 = format!("{{{}}}:{}", key, generate_random_string(5));

            let kill_conn_future = async {
                // Wait for 500 ms before killing the connections.
                tokio::time::sleep(std::time::Duration::from_millis(500)).await;
                kill_all_connections(&mut stable_conn).await;
            };

            let future = async {
                let mut pipeline = redis::pipe();
                pipeline.incr(&key, 1).blpop(&key2, 2.0).set(key3, "value");

                // Execute the pipeline.

                connection
                    .route_pipeline(
                        &pipeline,
                        0,
                        3,
                        None,
                        Some(PipelineRetryStrategy {
                            retry_server_error: false,
                            retry_connection_error: retry,
                        }),
                    )
                    .await
                    .expect("Pipeline execution failed")
            };

            let ((), result) = tokio::join!(kill_conn_future, future);

            match retry {
                true => {
                    // When retry is enabled, the pipeline should be retried, and the INCR command should run twice.
                    assert_eq!(
                        result,
                        [Value::Int(2), Value::Nil, Value::Okay],
                        "Pipeline result did not match expected output.\n\
                         Keys chosen: ('{key}', '{key2}')\n\
                         Actual result: {result:?}"
                    );
                }
                false => {
                    // When retry is disabled, the pipeline should not be retried, and the INCR command should run once.
                    assert!(
                        result.iter().enumerate().all(|(i, r)| {
                            if i == 2 {
                                // since SET is in another node, it should have succeeded regardless of the connection kill
                                // As it is not blocked by the BLPOP, the connection will be killed after the SET command succeeded
                                matches!(r, Value::Okay)
                            } else {
                                matches!(r, Value::ServerError(ref err) if err.details().unwrap().contains("RecvError"))
                            }
                        }),
                        "Pipeline result did not match expected output.\n\
                     Keys chosen: ('{key}', '{key2}')\n\
                     Actual result: {result:?}"
                    );
                }
            }
        }
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_read_from_replicas() {
        // 3 masters, 3 replicas (1 replica per master)
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            6,
            1,
            |builder| builder.read_from_replicas(),
            false,
        );

        // Create an asynchronous connection.
        let mut connection = cluster.async_connection(None).await;

        // Define pipeline with GET commands.
        let keys = (1..=15).map(|i| format!("key{i}"));
        let mut pipeline = redis::pipe();
        for key in keys {
            pipeline.get(key);
        }

        // Route the pipeline explicitly to replicas.
        let result = connection
            .route_pipeline(
                &pipeline,
                0,
                pipeline.len(),
                None,
                Some(PipelineRetryStrategy {
                    retry_server_error: true,
                    retry_connection_error: false,
                }),
            )
            .await
            .expect("Pipeline execution failed");

        // Ensure all responses are `Value::Nil`.
        assert_eq!(
            result,
            vec![Value::Nil; pipeline.len()],
            "Expected all GET responses to be Nil, got: {result:?}"
        );

        let mut info_cmd = cmd("INFO");
        info_cmd.arg("replication").arg("commandstats");

        let res = connection
            .route_command(
                &info_cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .expect("INFO command failed");

        // Parse the INFO output.
        let info_result: HashMap<String, String> =
            redis::from_owned_redis_value(res).expect("Failed to parse INFO command result");

        // Aggregate GET command call counts by node role.
        // Aggregate GET command call counts by node role.
        let (mut master_get_calls, mut replica_get_calls) = (0, 0);

        for info in info_result.values() {
            let mut node_role = String::new();
            let mut node_get_calls = 0;

            for line in info.lines() {
                if let Some(role) = line.strip_prefix("role:") {
                    node_role = role.trim().to_string();
                } else if let Some(calls_part) = line.strip_prefix("cmdstat_get:calls=") {
                    node_get_calls = calls_part
                        .split(',')
                        .next()
                        .unwrap_or("0")
                        .parse()
                        .unwrap_or(0);
                }
            }

            if node_role.contains("master") {
                master_get_calls += node_get_calls;
            } else if node_role.contains("slave") || node_role.contains("replica") {
                replica_get_calls += node_get_calls;
            }
        }

        // Assert that no GET commands were processed on masters
        // and that replicas did process GET commands.
        assert_eq!(
            master_get_calls, 0,
            "Expected no GET calls on masters, but got {master_get_calls}"
        );
        assert!(
            replica_get_calls == pipeline.len() as i64,
            "Expected {} GET calls on replicas, but got {replica_get_calls}",
            pipeline.len()
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_round_robin_read_from_replicas() {
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            12, // 3 masters, 3 replicas per shard
            3,
            |builder| builder.read_from_replicas(),
            false,
        );

        let mut connection = cluster.async_connection(None).await;

        // Use a fixed hash tag to force all keys into the same Redis slot
        let slot_key = "{test123}";
        let keys: Vec<String> = (1..=15).map(|i| format!("{slot_key}key{i}")).collect();

        let mut pipeline = redis::pipe();
        for key in &keys {
            pipeline.get(key);
        }

        // Route the pipeline to replicas using round-robin
        let result = connection
            .route_pipeline(
                &pipeline,
                0,
                pipeline.len(),
                None,
                Some(PipelineRetryStrategy {
                    retry_server_error: true,
                    retry_connection_error: false,
                }),
            )
            .await
            .expect("Pipeline execution failed");

        // Ensure all responses are `Value::Nil`.
        assert_eq!(
            result,
            vec![Value::Nil; pipeline.len()],
            "Expected all GET responses to be Nil, got: {result:?}"
        );

        let mut info_cmd = cmd("INFO");
        info_cmd.arg("replication").arg("commandstats");

        let res = connection
            .route_command(
                &info_cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .expect("INFO command failed");

        // Parse the INFO output.
        let info_result: HashMap<String, String> =
            redis::from_owned_redis_value(res).expect("Failed to parse INFO command result");

        let mut replica_counts = vec![];

        for info in info_result.values() {
            let mut node_role = String::new();
            let mut node_get_calls = 0;

            for line in info.lines() {
                if let Some(role) = line.strip_prefix("role:") {
                    node_role = role.trim().to_string();
                } else if let Some(calls_part) = line.strip_prefix("cmdstat_get:calls=") {
                    node_get_calls = calls_part
                        .split(',')
                        .next()
                        .unwrap_or("0")
                        .parse()
                        .unwrap_or(0);
                }
            }

            if (node_role.contains("replica") || node_role.contains("slave")) & (node_get_calls > 0)
            {
                replica_counts.push(node_get_calls);
            }
        }

        assert_eq!(
            replica_counts.len(),
            3,
            "Expected 3 replicas to process GET calls, but found {}. INFO output: {:#?}",
            replica_counts.len(),
            info_result
        );

        // Assert that each replica processed exactly 5 GET calls.
        assert!(
            replica_counts.iter().all(|&count| count == 5),
            "Expected each replica to process exactly 5 GET calls, but got: {replica_counts:?}. INFO output: {info_result:#?}"
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_read_from_az_affinity() {
        // Skip test if version is less then Valkey 8.0
        if engine_version_less_than("8.0").await {
            return;
        }
        // Create a cluster with 3 shards, 2 replicas per shard.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            9,
            2,
            |builder| builder.read_from_replicas(),
            false,
        );

        // Create an asynchronous connection.
        let mut connection = cluster.async_connection(None).await;

        let key = generate_random_string(10);
        let key_slot = get_slot(key.as_bytes());

        let az: String = "us-east-1a".to_string();
        let mut config_cmd = cmd("CONFIG");
        config_cmd.arg(&["SET", "availability-zone", &az.clone()]);

        connection
            .route_command(
                &config_cmd,
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                    key_slot,
                    SlotAddr::ReplicaRequired,
                ))),
            )
            .await
            .unwrap();

        let cluster_addresses: Vec<_> = cluster
            .cluster
            .servers
            .iter()
            .map(|server| server.connection_info())
            .collect();

        let mut client = ClusterClient::builder(cluster_addresses.clone())
            .read_from(redis::cluster_slotmap::ReadFromReplicaStrategy::AZAffinity(
                az.clone(),
            ))
            .build()
            .unwrap()
            .get_async_connection(None)
            .await
            .unwrap();

        // Define pipeline with GET commands.
        let mut pipeline = redis::pipe();
        let keys = (1..=15).map(|i| format!("{{{key}}}:{i}"));
        for key in keys {
            pipeline.get(key);
        }

        // Route the pipeline explicitly to replicas.
        let result = client
            .route_pipeline(
                &pipeline,
                0,
                pipeline.len(),
                None,
                Some(PipelineRetryStrategy {
                    retry_server_error: true,
                    retry_connection_error: false,
                }),
            )
            .await
            .expect("Pipeline execution failed");

        // Ensure all responses are `Value::Nil`.
        assert_eq!(result, vec![Value::Nil; pipeline.len()]);

        let mut info_cmd = cmd("INFO");
        info_cmd
            .arg("server")
            .arg("replication")
            .arg("commandstats");

        let res = connection
            .route_command(
                &info_cmd,
                RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllNodes, None)),
            )
            .await
            .expect("INFO command failed");

        let info_result = redis::from_owned_redis_value::<HashMap<String, String>>(res).unwrap();
        let get_cmdstat = "cmdstat_get:calls=".to_string();
        let n_get_cmdstat = format!("cmdstat_get:calls={}", pipeline.len());
        let client_az = format!("availability_zone:{az}");

        let mut matching_entries_count: usize = 0;

        for value in info_result.values() {
            if value.contains(&get_cmdstat) {
                if value.contains(&client_az) && value.contains(&n_get_cmdstat) {
                    matching_entries_count += 1;
                } else {
                    panic!(
                        "Invalid entry found: {}. Expected cmdstat_get:calls={} and availability_zone={}",
                        value, pipeline.len(), az);
                }
            }
        }

        assert_eq!(
            matching_entries_count, 1,
            "Test failed: expected exactly '{}' entries with '{}' and '{}', found {}",
            1, get_cmdstat, client_az, matching_entries_count
        );
    }
}
