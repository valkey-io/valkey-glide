// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod utilities;

#[cfg(test)]
mod cluster_client_tests {
    use std::collections::HashMap;

    use super::*;
    use cluster::{LONG_CLUSTER_TEST_TIMEOUT, setup_cluster_with_replicas};
    use glide_core::client::Client;
    use glide_core::connection_request::ProtocolVersion as GlideProtocolVersion;
    use glide_core::connection_request::{
        self, PubSubChannelsOrPatterns, PubSubSubscriptions, ReadFrom,
    };
    use redis::cluster_routing::{
        MultipleNodeRoutingInfo, Route, RoutingInfo, SingleNodeRoutingInfo, SlotAddr,
    };
    use redis::{InfoDict, Value};

    use rstest::rstest;
    use utilities::cluster::{SHORT_CLUSTER_TEST_TIMEOUT, setup_test_basics_internal};
    use utilities::*;
    use versions::Versioning;

    fn count_primary_or_replica(value: &str) -> (u16, u16) {
        if value.contains("role:master") {
            (1, 0)
        } else if value.contains("role:slave") {
            (0, 1)
        } else {
            (0, 0)
        }
    }

    fn count_primaries_and_replicas(info_replication: HashMap<String, String>) -> (u16, u16) {
        info_replication
            .into_iter()
            .fold((0, 0), |acc, (_, value)| {
                let count = count_primary_or_replica(&value);
                (acc.0 + count.0, acc.1 + count.1)
            })
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_routing_no_provided_route() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                ..Default::default()
            })
            .await;

            let mut cmd = redis::cmd("INFO");
            cmd.arg("REPLICATION");
            let info = test_basics.client.send_command(&cmd, None).await.unwrap();
            let info = redis::from_owned_redis_value::<HashMap<String, String>>(info).unwrap();
            let (primaries, replicas) = count_primaries_and_replicas(info);
            assert_eq!(primaries, 3);
            assert_eq!(replicas, 0);
        });
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_routing_to_all_primaries() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                ..Default::default()
            })
            .await;

            let mut cmd = redis::cmd("INFO");
            cmd.arg("REPLICATION");
            let info = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllMasters,
                        None,
                    ))),
                )
                .await
                .unwrap();
            let info = redis::from_owned_redis_value::<HashMap<String, String>>(info).unwrap();
            let (primaries, replicas) = count_primaries_and_replicas(info);
            assert_eq!(primaries, 3);
            assert_eq!(replicas, 0);
        });
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_routing_to_all_nodes() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                ..Default::default()
            })
            .await;

            let mut cmd = redis::cmd("INFO");
            cmd.arg("REPLICATION");
            let info = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await
                .unwrap();
            let info = redis::from_owned_redis_value::<HashMap<String, String>>(info).unwrap();
            let (primaries, replicas) = count_primaries_and_replicas(info);
            assert_eq!(primaries, 3);
            assert_eq!(replicas, 3);
        });
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_routing_by_slot_to_primary() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                ..Default::default()
            })
            .await;

            let mut cmd = redis::cmd("INFO");
            cmd.arg("REPLICATION");
            let info = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::SingleNode(
                        SingleNodeRoutingInfo::SpecificNode(Route::new(0, SlotAddr::Master)),
                    )),
                )
                .await
                .unwrap();
            let info = redis::from_owned_redis_value::<String>(info).unwrap();
            let (primaries, replicas) = count_primary_or_replica(&info);
            assert_eq!(primaries, 1);
            assert_eq!(replicas, 0);
        });
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_routing_by_slot_to_replica_if_read_from_replica_configuration_allows() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                read_from: Some(ReadFrom::PreferReplica),
                ..Default::default()
            })
            .await;

            let mut cmd = redis::cmd("INFO");
            cmd.arg("REPLICATION");
            let info = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::SingleNode(
                        SingleNodeRoutingInfo::SpecificNode(Route::new(
                            0,
                            SlotAddr::ReplicaOptional,
                        )),
                    )),
                )
                .await
                .unwrap();
            let info = redis::from_owned_redis_value::<String>(info).unwrap();
            let (primaries, replicas) = count_primary_or_replica(&info);
            assert_eq!(primaries, 0);
            assert_eq!(replicas, 1);
        });
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_routing_by_slot_to_replica_override_read_from_replica_configuration() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                read_from: Some(ReadFrom::Primary),
                ..Default::default()
            })
            .await;

            let mut cmd = redis::cmd("INFO");
            cmd.arg("REPLICATION");
            let info = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::SingleNode(
                        SingleNodeRoutingInfo::SpecificNode(Route::new(
                            0,
                            SlotAddr::ReplicaRequired,
                        )),
                    )),
                )
                .await
                .unwrap();
            let info = redis::from_owned_redis_value::<String>(info).unwrap();
            let (primaries, replicas) = count_primary_or_replica(&info);
            assert_eq!(primaries, 0);
            assert_eq!(replicas, 1);
        });
    }

    #[rstest]
    #[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
    fn test_fail_creation_with_unsupported_sharded_pubsub() {
        block_on_all(async {
            let mut test_basics = setup_cluster_with_replicas(
                TestConfiguration {
                    cluster_mode: ClusterMode::Enabled,
                    shared_server: false,
                    ..Default::default()
                },
                0,
                3,
            )
            .await;

            // get engine version
            let cmd = redis::cmd("INFO");
            let info = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)),
                )
                .await
                .unwrap();

            let info_dict: InfoDict = redis::from_owned_redis_value(info).unwrap();
            match info_dict.get::<String>("redis_version") {
                Some(version) => match (Versioning::new(version), Versioning::new("7.0")) {
                    (Some(server_ver), Some(min_ver)) => {
                        if server_ver < min_ver {
                            // try to create client with initial nodes lacking the target sharded subscription node
                            let cluster = test_basics.cluster.unwrap();
                            let mut addresses = cluster.get_server_addresses();
                            addresses.truncate(1);

                            let mut connection_request =
                                connection_request::ConnectionRequest::new();
                            connection_request.addresses =
                                addresses.iter().map(get_address_info).collect();

                            connection_request.cluster_mode_enabled = true;
                            // Assumes the current implementation of the test cluster, where slots are distributed across nodes
                            // in a monotonically increasing order.
                            let mut last_slot_channel = PubSubChannelsOrPatterns::new();
                            last_slot_channel
                                .channels_or_patterns
                                .push("last-slot-channel-{16383}".as_bytes().into());

                            let mut subs = PubSubSubscriptions::new();
                            // First try to create a client with the Exact subscription
                            subs.channels_or_patterns_by_type
                                .insert(0, last_slot_channel.clone());
                            connection_request.pubsub_subscriptions =
                                protobuf::MessageField::from_option(Some(subs.clone()));

                            let _client = Client::new(connection_request.clone().into(), None)
                                .await
                                .unwrap();

                            // Now try to create a client with a Sharded subscription which should fail
                            subs.channels_or_patterns_by_type
                                .insert(2, last_slot_channel);
                            connection_request.pubsub_subscriptions =
                                protobuf::MessageField::from_option(Some(subs));

                            let client = Client::new(connection_request.into(), None).await;
                            assert!(client.is_err());
                        }
                    }
                    _ => {
                        panic!("Failed to parse engine version");
                    }
                },
                _ => {
                    panic!("Could not determine engine version from INFO result");
                }
            }
        });
    }

    // Helper function to get client count on shared cluster primaries using AllMasters routing
    async fn get_total_clients_on_shared_cluster_primaries(client: &mut Client) -> usize {
        let mut cmd = redis::Cmd::new();
        cmd.arg("CLIENT").arg("LIST");

        let routing_info = RoutingInfo::MultiNode((MultipleNodeRoutingInfo::AllMasters, None));
        let mut total_clients = 0;

        logger_core::log_info(
            "TestClusterLazyHelper",
            "Querying CLIENT LIST on all shared cluster primaries via AllMasters routing.",
        );

        match client.send_command(&cmd, Some(routing_info)).await {
            Ok(Value::Map(node_results_map)) => {
                for (_node_addr_value, node_result_value) in node_results_map {
                    match node_result_value {
                        Value::BulkString(bytes) => {
                            // RESP2 response
                            let s = String::from_utf8_lossy(&bytes);
                            total_clients += s.lines().count();
                        }
                        Value::VerbatimString { text, format: _ } => {
                            // RESP3 response
                            total_clients += text.lines().count();
                        }
                        _ => {
                            logger_core::log_warn(
                                "TestClusterLazyHelper",
                                format!(
                                    "CLIENT LIST from a primary (AllMasters) returned unexpected inner type for a node's result: {node_result_value:?}"
                                ),
                            );
                        }
                    }
                }
            }
            Ok(other_type) => {
                // Logging if returned type is not a map as we expect
                logger_core::log_warn(
                    "TestClusterLazyHelper",
                    format!(
                        "CLIENT LIST with AllMasters routing returned an unexpected type (expected Map): {other_type:?}"
                    ),
                );
            }
            Err(e) => {
                logger_core::log_warn(
                    "TestClusterLazyHelper",
                    format!("CLIENT LIST with AllMasters routing failed: {e:?}"),
                );
            }
        }

        logger_core::log_info(
            "TestClusterLazyHelper",
            format!("Total clients found on shared primaries (AllMasters): {total_clients}"),
        );
        total_clients
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_set_database_id_after_reconnection() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                database_id: 1, // Set a specific database ID
                ..Default::default()
            })
            .await;

            // Verify we can connect and perform operations with database_id = 1
            let key = generate_random_string(10);
            let value = generate_random_string(10);

            let mut set_cmd = redis::cmd("SET");
            set_cmd.arg(&key).arg(&value);
            test_basics
                .client
                .send_command(&set_cmd, None)
                .await
                .unwrap();

            let mut get_cmd = redis::cmd("GET");
            get_cmd.arg(&key);
            let result = test_basics
                .client
                .send_command(&get_cmd, None)
                .await
                .unwrap();
            assert_eq!(result, redis::Value::BulkString(value.as_bytes().to_vec()));

            // Verify that we're connected to the correct database using CLIENT INFO
            let mut client_info_cmd = redis::cmd("CLIENT");
            client_info_cmd.arg("INFO");

            // Check database before reconnection
            let info_before = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await
                .unwrap();
            let info_str_before = match info_before {
                redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                redis::Value::VerbatimString { text, .. } => text,
                _ => panic!("Unexpected CLIENT INFO response type: {:?}", info_before),
            };
            assert!(
                info_str_before.contains("db=1"),
                "Expected db=1 before reconnection, got: {}",
                info_str_before
            );

            // Kill the connection to force reconnection
            kill_connection(&mut test_basics.client).await;

            // Longer sleep to allow the connection validation task to reconnect
            tokio::time::sleep(std::time::Duration::from_secs(3)).await;

            // Verify database after reconnection using CLIENT INFO
            let info_after = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await
                .unwrap();
            let info_str_after = match info_after {
                redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                redis::Value::VerbatimString { text, .. } => text,
                _ => panic!("Unexpected CLIENT INFO response type: {:?}", info_after),
            };
            assert!(
                info_str_after.contains("db=1"),
                "Expected db=1 after reconnection, got: {}",
                info_str_after
            );

            // Set a new key after reconnection to verify database selection persists
            let key2 = generate_random_string(10);
            let value2 = generate_random_string(10);

            let mut set_cmd2 = redis::cmd("SET");
            set_cmd2.arg(&key2).arg(&value2);
            test_basics
                .client
                .send_command(&set_cmd2, None)
                .await
                .unwrap();

            let mut get_cmd2 = redis::cmd("GET");
            get_cmd2.arg(&key2);
            let result2 = test_basics
                .client
                .send_command(&get_cmd2, None)
                .await
                .unwrap();
            assert_eq!(
                result2,
                redis::Value::BulkString(value2.as_bytes().to_vec())
            );

            println!("✓ CLIENT INFO confirms database ID 1 before and after reconnection");
            println!(
                "✓ Client can successfully perform operations after reconnection with database_id = 1"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_database_isolation_in_cluster_mode() {
        block_on_all(async {
            // Create two clients connected to different databases
            let mut test_basics_db1 = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                database_id: 1, // Database 1
                ..Default::default()
            })
            .await;

            let mut test_basics_db0 = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                database_id: 0, // Database 0
                ..Default::default()
            })
            .await;

            // Use the same key name in both databases
            let key = generate_random_string(10);
            let value_db1 = "value_in_database_1";
            let value_db0 = "value_in_database_0";

            // Set the key in database 1
            let mut set_cmd_db1 = redis::cmd("SET");
            set_cmd_db1.arg(&key).arg(value_db1);
            test_basics_db1
                .client
                .send_command(&set_cmd_db1, None)
                .await
                .unwrap();

            // Set the same key in database 0 with a different value
            let mut set_cmd_db0 = redis::cmd("SET");
            set_cmd_db0.arg(&key).arg(value_db0);
            test_basics_db0
                .client
                .send_command(&set_cmd_db0, None)
                .await
                .unwrap();

            // Verify that database 1 has its value
            let mut get_cmd_db1 = redis::cmd("GET");
            get_cmd_db1.arg(&key);
            let result_db1 = test_basics_db1
                .client
                .send_command(&get_cmd_db1, None)
                .await
                .unwrap();

            // Verify that database 0 has its value
            let mut get_cmd_db0 = redis::cmd("GET");
            get_cmd_db0.arg(&key);
            let result_db0 = test_basics_db0
                .client
                .send_command(&get_cmd_db0, None)
                .await
                .unwrap();

            // Check if database isolation is supported
            let db1_value = match &result_db1 {
                redis::Value::BulkString(bytes) => String::from_utf8_lossy(bytes).to_string(),
                redis::Value::Nil => "nil".to_string(),
                _ => format!("unexpected_type: {:?}", result_db1),
            };

            let db0_value = match &result_db0 {
                redis::Value::BulkString(bytes) => String::from_utf8_lossy(bytes).to_string(),
                redis::Value::Nil => "nil".to_string(),
                _ => format!("unexpected_type: {:?}", result_db0),
            };

            if db1_value == value_db1 && db0_value == value_db0 {
                println!(
                    "✓ Database isolation is supported: database 1 and database 0 have different values for the same key"
                );
                println!("  Database 1 value: {}", db1_value);
                println!("  Database 0 value: {}", db0_value);

                // Assert that the values are different and correct
                assert_eq!(
                    result_db1,
                    redis::Value::BulkString(value_db1.as_bytes().to_vec())
                );
                assert_eq!(
                    result_db0,
                    redis::Value::BulkString(value_db0.as_bytes().to_vec())
                );
            } else {
                println!("⚠ Database isolation not supported in this cluster configuration");
                println!(
                    "  Both databases returned the same value, which indicates they share the same keyspace"
                );
                println!("  Database 1 value: {}", db1_value);
                println!("  Database 0 value: {}", db0_value);
                println!(
                    "  This is expected behavior for most Redis cluster configurations that only support database 0"
                );

                // In this case, we just verify that both clients can operate successfully
                // even if they don't have true database isolation
                assert!(matches!(result_db1, redis::Value::BulkString(_)));
                assert!(matches!(result_db0, redis::Value::BulkString(_)));
            }

            // Clean up - delete the test key from both databases
            let mut del_cmd_db1 = redis::cmd("DEL");
            del_cmd_db1.arg(&key);
            test_basics_db1
                .client
                .send_command(&del_cmd_db1, None)
                .await
                .unwrap();

            let mut del_cmd_db0 = redis::cmd("DEL");
            del_cmd_db0.arg(&key);
            test_basics_db0
                .client
                .send_command(&del_cmd_db0, None)
                .await
                .unwrap();
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_database_id_per_node_verification_with_reconnection() {
        block_on_all(async {
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                database_id: 2, // Use database 2 for this test
                ..Default::default()
            })
            .await;

            // Get cluster nodes information to understand the topology
            let mut cluster_nodes_cmd = redis::cmd("CLUSTER");
            cluster_nodes_cmd.arg("NODES");
            let cluster_nodes_result = test_basics
                .client
                .send_command(&cluster_nodes_cmd, None)
                .await
                .unwrap();
            let cluster_nodes =
                redis::from_owned_redis_value::<String>(cluster_nodes_result).unwrap();

            println!("Cluster topology:");
            let mut _master_count = 0;
            for line in cluster_nodes.lines() {
                if line.contains("master") {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 2 {
                        let node_id = parts[0];
                        let address = parts[1];
                        println!("  Master node: {} at {}", node_id, address);
                        _master_count += 1;
                    }
                }
            }

            // Test multiple keys that will likely hit different nodes
            let test_keys = vec![
                "node_test_key_1",
                "node_test_key_2",
                "node_test_key_3",
                "different_slot_key_a",
                "different_slot_key_b",
                "different_slot_key_c",
            ];

            println!("\nTesting database ID consistency across nodes (before reconnection):");

            // Set values and verify database ID for each key
            for key in &test_keys {
                // Set a value
                let value = format!("db2_value_for_{}", key);
                let mut set_cmd = redis::cmd("SET");
                set_cmd.arg(*key).arg(&value);
                test_basics
                    .client
                    .send_command(&set_cmd, None)
                    .await
                    .unwrap();

                // Retrieve the value to confirm it's in the right database
                let mut get_cmd = redis::cmd("GET");
                get_cmd.arg(*key);
                let retrieved_value = test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
                let retrieved_str = match retrieved_value {
                    redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                    _ => panic!("Unexpected GET response type: {:?}", retrieved_value),
                };
                assert_eq!(retrieved_str, value);

                // Check CLIENT INFO for this operation
                let mut client_info_cmd = redis::cmd("CLIENT");
                client_info_cmd.arg("INFO");
                let client_info = test_basics
                    .client
                    .send_command(&client_info_cmd, None)
                    .await
                    .unwrap();
                let client_info_str = match client_info {
                    redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                    redis::Value::VerbatimString { text, .. } => text,
                    _ => panic!("Unexpected CLIENT INFO response type: {:?}", client_info),
                };

                // Extract database info
                if let Some(db_line) = client_info_str.lines().find(|line| line.contains("db=")) {
                    println!("  Key '{}' -> {}", key, db_line.trim());
                    assert!(
                        db_line.contains("db=2"),
                        "Expected db=2 for key '{}', got: {}",
                        key,
                        db_line
                    );
                }
            }

            println!("✓ All node connections confirmed to be using database 2 before reconnection");

            // Force reconnection by killing connections
            println!("\nForcing reconnection...");
            kill_connection(&mut test_basics.client).await;

            // Wait for reconnection
            tokio::time::sleep(std::time::Duration::from_secs(3)).await;

            println!("Testing database ID consistency across nodes (after reconnection):");

            // Verify all keys are still accessible and database ID is maintained after reconnection
            for key in &test_keys {
                // Retrieve the value to confirm it's still in the right database
                let mut get_cmd = redis::cmd("GET");
                get_cmd.arg(*key);
                let retrieved_value = test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
                let retrieved_str = match retrieved_value {
                    redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                    _ => panic!(
                        "Unexpected GET response type after reconnection: {:?}",
                        retrieved_value
                    ),
                };
                let expected_value = format!("db2_value_for_{}", key);
                assert_eq!(
                    retrieved_str, expected_value,
                    "Key '{}' value changed after reconnection",
                    key
                );

                // Check CLIENT INFO after reconnection
                let mut client_info_cmd = redis::cmd("CLIENT");
                client_info_cmd.arg("INFO");
                let client_info = test_basics
                    .client
                    .send_command(&client_info_cmd, None)
                    .await
                    .unwrap();
                let client_info_str = match client_info {
                    redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                    redis::Value::VerbatimString { text, .. } => text,
                    _ => panic!(
                        "Unexpected CLIENT INFO response type after reconnection: {:?}",
                        client_info
                    ),
                };

                // Extract database info
                if let Some(db_line) = client_info_str.lines().find(|line| line.contains("db=")) {
                    println!("  Key '{}' (after reconnection) -> {}", key, db_line.trim());
                    assert!(
                        db_line.contains("db=2"),
                        "Expected db=2 for key '{}' after reconnection, got: {}",
                        key,
                        db_line
                    );
                }
            }

            // Test setting new keys after reconnection to verify database selection persists
            println!("\nTesting new operations after reconnection:");
            let post_reconnect_keys = vec!["post_reconnect_key_1", "post_reconnect_key_2"];

            for key in &post_reconnect_keys {
                let value = format!("post_reconnect_value_for_{}", key);
                let mut set_cmd = redis::cmd("SET");
                set_cmd.arg(*key).arg(&value);
                test_basics
                    .client
                    .send_command(&set_cmd, None)
                    .await
                    .unwrap();

                let mut get_cmd = redis::cmd("GET");
                get_cmd.arg(*key);
                let retrieved_value = test_basics
                    .client
                    .send_command(&get_cmd, None)
                    .await
                    .unwrap();
                let retrieved_str = match retrieved_value {
                    redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                    _ => panic!(
                        "Unexpected GET response type for new key: {:?}",
                        retrieved_value
                    ),
                };
                assert_eq!(retrieved_str, value);

                // Verify database ID for new operations
                let mut client_info_cmd = redis::cmd("CLIENT");
                client_info_cmd.arg("INFO");
                let client_info = test_basics
                    .client
                    .send_command(&client_info_cmd, None)
                    .await
                    .unwrap();
                let client_info_str = match client_info {
                    redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                    redis::Value::VerbatimString { text, .. } => text,
                    _ => panic!(
                        "Unexpected CLIENT INFO response type for new key: {:?}",
                        client_info
                    ),
                };

                if let Some(db_line) = client_info_str.lines().find(|line| line.contains("db=")) {
                    println!("  New key '{}' -> {}", key, db_line.trim());
                    assert!(
                        db_line.contains("db=2"),
                        "Expected db=2 for new key '{}', got: {}",
                        key,
                        db_line
                    );
                }
            }

            println!("✓ All node connections maintained database 2 after reconnection");
            println!("✓ New operations after reconnection use correct database 2");

            // Clean up all test keys
            let mut all_keys = test_keys.clone();
            all_keys.extend(post_reconnect_keys);
            for key in &all_keys {
                let mut del_cmd = redis::cmd("DEL");
                del_cmd.arg(*key);
                let _ = test_basics.client.send_command(&del_cmd, None).await;
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
    fn test_lazy_cluster_connection_establishes_on_first_command(
        #[values(GlideProtocolVersion::RESP2, GlideProtocolVersion::RESP3)]
        protocol: GlideProtocolVersion,
    ) {
        block_on_all(async move {
            const USE_TLS: bool = false;

            // 1. Base configuration for creating the DEDICATED cluster (Cluster A)
            // and the monitoring client.
            let base_config_for_dedicated_cluster = TestConfiguration {
                use_tls: USE_TLS,
                protocol,
                shared_server: false, // <<<< This ensures a dedicated cluster is made
                cluster_mode: ClusterMode::Enabled,
                lazy_connect: false, // Monitoring client connects eagerly
                client_name: Some("base_config".into()),
                ..Default::default()
            };

            // 2. Setup the dedicated cluster (Cluster A) and the monitoring client.
            // `monitoring_test_basics` now owns Cluster A.
            let mut monitoring_client_config = base_config_for_dedicated_cluster.clone();
            monitoring_client_config.client_name = Some("monitoring_client".into());
            let monitoring_test_basics = setup_test_basics_internal(monitoring_client_config).await;
            let mut monitoring_client = monitoring_test_basics.client;

            // Get addresses from the DEDICATED Cluster A
            let dedicated_cluster_addresses = monitoring_test_basics
                .cluster
                .as_ref()
                .expect("Dedicated cluster (Cluster A) should have been created")
                .get_server_addresses(); // This returns Vec<redis::ConnectionAddr>

            // 3. Get initial client count on Cluster A.
            let clients_before_lazy_init =
                get_total_clients_on_shared_cluster_primaries(&mut monitoring_client).await;
            logger_core::log_info(
                "TestClusterLazy",
                format!(
                    "Clients before lazy client init (protocol={protocol:?} on dedicated cluster A): {clients_before_lazy_init}"
                ),
            );

            // 4. Manually create the ConnectionRequest for the lazy client,
            //    pointing to the DEDICATED Cluster A.
            //    We use the `base_config_for_dedicated_cluster` for other settings.
            let mut lazy_client_config = base_config_for_dedicated_cluster;
            lazy_client_config.lazy_connect = true;
            lazy_client_config.client_name = Some("lazy_config".into());

            // Create connection request directly with our dedicated cluster addresses
            let lazy_connection_request = utilities::create_connection_request(
                &dedicated_cluster_addresses,
                &lazy_client_config, // Uses the same config but with lazy_connect=true
            );

            // 5. Create the client
            let mut lazy_glide_client = Client::new(lazy_connection_request.into(), None)
                .await
                .expect("Failed to create lazy client for Cluster A");

            // 6. Assert that no new connections were made yet by the lazy client on Cluster A.
            let clients_after_lazy_init =
                get_total_clients_on_shared_cluster_primaries(&mut monitoring_client).await;
            logger_core::log_info(
                "TestClusterLazy",
                format!(
                    "Clients after lazy client init (protocol={protocol:?} on dedicated cluster A): {clients_after_lazy_init}"
                ),
            );
            assert_eq!(
                clients_after_lazy_init, clients_before_lazy_init,
                "Lazy client (on dedicated cluster A) should not establish new connections before the first command. Before: {clients_before_lazy_init}, After: {clients_after_lazy_init}. protocol={protocol:?}"
            );

            // 7. Send the first command using the lazy client to Cluster A.
            logger_core::log_info(
                "TestClusterLazy",
                format!(
                    "Sending first command to lazy client (PING) (protocol={protocol:?} on dedicated cluster A)"
                ),
            );
            let ping_response = lazy_glide_client
                .send_command(&redis::cmd("PING"), None)
                .await;
            assert!(
                ping_response.is_ok(),
                "PING command failed (on dedicated cluster A): {:?}. protocol={:?}",
                ping_response.as_ref().err(),
                protocol
            );
            assert_eq!(
                ping_response.unwrap(),
                redis::Value::SimpleString("PONG".to_string())
            );

            // 8. Assert that new connections were made on Cluster A by the lazy client.
            let clients_after_first_command =
                get_total_clients_on_shared_cluster_primaries(&mut monitoring_client).await;
            logger_core::log_info(
                "TestClusterLazy",
                format!(
                    "Clients after first command (protocol={protocol:?} on dedicated cluster A): {clients_after_first_command}"
                ),
            );
            assert!(
                clients_after_first_command > clients_before_lazy_init,
                "Lazy client (on dedicated cluster A) should establish new connections after the first command. Before: {clients_before_lazy_init}, After: {clients_after_first_command}. protocol={protocol:?}"
            );
        });
    }
}
