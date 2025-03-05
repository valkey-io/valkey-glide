#![cfg(feature = "cluster-async")]
mod support;

mod test_pipeline {

    use redis::{cluster_async::ClusterConnection, cluster_topology::get_slot, ErrorKind};
    use std::collections::HashMap;

    use redis::{
        cluster::ClusterClient,
        cluster_routing::{
            MultipleNodeRoutingInfo, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
        },
        cmd, Pipeline, Value,
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
            let slot = get_slot(key.as_str().as_bytes());
            let slot2 = get_slot(key2.as_str().as_bytes());

            if is_in_same_node(slot, slot2, nodes_and_slots.clone()) {
                return (key, key2);
            }
        }
        panic!("Failed to find a good key after 1000 attempts");
    }

    pub async fn assert_moved_err_received(
        connection: &mut ClusterConnection,
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

        println!("INFO errorstats output: {:?}", res);

        // Parse the output as a String.
        // Parse the INFO output.
        let info_result: HashMap<String, String> =
            redis::from_owned_redis_value(res).expect("Failed to parse INFO command result");

        // Search for the "errorstat_MOVED" line.
        let mut moved_count: Option<usize> = None;
        for info in info_result.values() {
            for line in info.lines() {
                if line.starts_with("errorstat_MOVED:") {
                    if let Some(count_str) = line.strip_prefix("errorstat_MOVED:count=") {
                        let count_val = count_str
                            .split(',')
                            .next()
                            .unwrap_or("0")
                            .parse::<usize>()
                            .unwrap_or(0);
                        println!("Found errorstat_MOVED count: {}", count_val);
                        moved_count = Some(count_val + moved_count.unwrap_or(0));
                        break;
                    }
                }
            }
        }

        // Assert that the found count matches the expected count.
        match moved_count {
            Some(count) => {
                assert_eq!(
                    count, expected_count,
                    "Expected errorstat_MOVED count {} but found {}",
                    expected_count, count
                );
            }
            None => panic!("errorstat_MOVED not found in INFO errorstats output"),
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
        let bad_slot = get_slot(&bad_key.as_str().as_bytes());

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
                let candidate_slot = get_slot(candidate.as_str().as_bytes());
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
        pipeline.set(&good_key, "value");
        pipeline.get(&good_key);
        pipeline.set(&good_key2, "value2");
        pipeline.get(&good_key2);

        // Execute the pipeline.
        let res = connection
            .route_pipeline(&pipeline, 0, pipeline.len(), SingleNodeRoutingInfo::Random)
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
            &res[2..],
            expected,
            "Pipeline returned unexpected result. \
            Good keys: '{}' '{}', result: {:?}",
            good_key,
            good_key2,
            &res[2..]
        );

        assert!(
            matches!(res[0], Value::ServerError(_)) && matches!(res[1], Value::ServerError(_)),
            "Expected server error responses for the bad key operations, but got: {:?}",
            &res[0..2]
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_with_moved_error_with_retries() {
        // Create a test cluster with 3 masters and no replicas.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(1),
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

        let (key, key2) = generate_2_keys_in_the_same_node(nodes_and_slots);
        let key_slot = get_slot(key.as_str().as_bytes());

        cluster
            .move_specific_slot(key_slot, slot_distribution)
            .await;

        // Create a pipeline with several commands.
        let mut pipeline = redis::pipe();
        pipeline.incr(&key2, 5).set(&key, "value").get(&key);

        // Execute the pipeline.
        let result = connection
            .route_pipeline(&pipeline, 0, 3, SingleNodeRoutingInfo::Random)
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
             Keys chosen: ('{}', '{}')\n\
             key_slot: {}\n\
             Actual result: {:?}",
            key, key2, key_slot, result
        );

        assert_moved_err_received(&mut connection, 2).await;
    }

    #[tokio::test]
    #[serial_test::serial]
    async fn test_pipeline_return_moved_error_with_zero_retries() {
        // Create a test cluster with 3 masters and no replicas.
        let cluster = TestClusterContext::new_with_cluster_client_builder(
            3,
            0,
            |builder| builder.retries(0),
            false,
        );
        let mut connection = cluster.async_connection(None).await;

        // Get the current slot distribution.
        let cluster_nodes = cluster.get_cluster_nodes().await;
        let slot_distribution = cluster.get_slots_ranges_distribution(&cluster_nodes);

        let key = generate_random_string(10);
        let key2 = generate_random_string(10);
        let slot_to_move = get_slot(key.as_str().as_bytes());

        // Move the slot of `key` to a different node.
        cluster
            .move_specific_slot(slot_to_move, slot_distribution)
            .await;

        // Create a pipeline with several commands.
        let mut pipeline = redis::pipe();
        pipeline.set(&key, "value").get(&key).set(&key2, "value2");

        // Execute the pipeline.
        let result = connection
            .route_pipeline(&pipeline, 0, pipeline.len(), SingleNodeRoutingInfo::Random)
            .await
            .expect("Pipeline execution failed");

        assert!(
            result[2] == Value::Okay,
            "Expected the SET command to succeed, but got {:?}",
            result[2]
        );

        assert!(
            if let Value::ServerError(ref err) = result[0] {
                err.kind() == ErrorKind::Moved
            } else {
                false
            },
            "Expected a server error response for the SET command, but got {:?}",
            result[0]
        );

        assert!(
            if let Value::ServerError(ref err) = result[1] {
                err.kind() == ErrorKind::Moved
            } else {
                false
            },
            "Expected a server error response for the GET command, but got {:?}",
            result[1]
        );
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
        let keys = (1..=15).map(|i| format!("key{}", i));
        let mut pipeline = redis::pipe();
        for key in keys {
            pipeline.get(key);
        }

        // Route the pipeline explicitly to replicas.
        let result = connection
            .route_pipeline(&pipeline, 0, pipeline.len(), SingleNodeRoutingInfo::Random)
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
        let keys: Vec<String> = (1..=15).map(|i| format!("{}key{}", slot_key, i)).collect();

        let mut pipeline = redis::pipe();
        for key in &keys {
            pipeline.get(key);
        }

        // Route the pipeline to replicas using round-robin
        let result = connection
            .route_pipeline(&pipeline, 0, pipeline.len(), SingleNodeRoutingInfo::Random)
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
            "Expected each replica to process exactly 5 GET calls, but got: {replica_counts:?}. INFO output: {:#?}",
            info_result
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
        let key_slot = get_slot(key.as_str().as_bytes());

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
        let keys = (1..=15).map(|i| format!("{{{}}}:{}", key, i));
        for key in keys {
            pipeline.get(key);
        }

        // Route the pipeline explicitly to replicas.
        let result = client
            .route_pipeline(&pipeline, 0, pipeline.len(), SingleNodeRoutingInfo::Random)
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
        let client_az = format!("availability_zone:{}", az);

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
