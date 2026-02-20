// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod utilities;

#[cfg(test)]
mod cluster_client_tests {
    use std::collections::HashMap;
    use std::sync::Arc;
    use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
    use std::time::Duration;

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
            let info = test_basics
                .client
                .send_command(&mut cmd, None)
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
                    &mut cmd,
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
                    &mut cmd,
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
                    &mut cmd,
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
                    &mut cmd,
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
                    &mut cmd,
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
            let mut cmd = redis::cmd("INFO");
            let info = test_basics
                .client
                .send_command(
                    &mut cmd,
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

        match client.send_command(&mut cmd, Some(routing_info)).await {
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
    /// Test that verifies the client maintains the correct database ID after an automatic reconnection.
    /// This test:
    /// 1. Creates a client connected to database 4
    /// 2. Verifies the initial connection is to the correct database
    /// 3. Simulates a connection drop by killing the connection
    /// 4. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection to db=4
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still on db=4
    /// This ensures that database selection persists across reconnections.
    fn test_set_database_id_after_reconnection() {
        let mut client_info_cmd = redis::cmd("CLIENT");
        client_info_cmd.arg("INFO");
        block_on_all(async {
            // First create a basic client to check server version
            let mut version_check_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                ..Default::default()
            })
            .await;

            // Skip test if server version is less than 9.0 (database isolation not supported)
            if !utilities::version_greater_or_equal(&mut version_check_basics.client, "9.0.0").await
            {
                return;
            }
            let mut test_basics = setup_test_basics_internal(TestConfiguration {
                cluster_mode: ClusterMode::Enabled,
                shared_server: true,
                database_id: 4, // Set a specific database ID
                ..Default::default()
            })
            .await;

            let client_info = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await
                .unwrap();
            let client_info_str = match client_info {
                redis::Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                redis::Value::VerbatimString { text, .. } => text,
                _ => panic!("Unexpected CLIENT INFO response type: {:?}", client_info),
            };
            assert!(client_info_str.contains("db=4"));

            // Extract initial client ID
            let initial_client_id =
                extract_client_id(&client_info_str).expect("Failed to extract initial client ID");

            kill_connection(&mut test_basics.client).await;

            let res = test_basics
                .client
                .send_command(&mut client_info_cmd, None)
                .await;
            match res {
                Err(err) => {
                    // Connection was dropped as expected, or reconnection is in progress
                    assert!(
                        err.is_connection_dropped()
                            || err.is_timeout()
                            || err.kind() == redis::ErrorKind::AllConnectionsUnavailable,
                        "Expected connection dropped, timeout, or connection unavailable error, got: {err:?}",
                    );
                    let client_info = repeat_try_create(|| async {
                        let mut client = test_basics.client.clone();
                        let mut cmd = client_info_cmd.clone();
                        let response = client.send_command(&mut cmd, None).await.ok()?;
                        match response {
                            redis::Value::BulkString(bytes) => {
                                Some(String::from_utf8_lossy(&bytes).to_string())
                            }
                            redis::Value::VerbatimString { text, .. } => Some(text),
                            _ => None,
                        }
                    })
                    .await;
                    assert!(client_info.contains("db=4"));
                }
                Ok(response) => {
                    // Command succeeded, extract new client ID and compare
                    let new_client_info = match response {
                        redis::Value::BulkString(bytes) => {
                            String::from_utf8_lossy(&bytes).to_string()
                        }
                        redis::Value::VerbatimString { text, .. } => text,
                        _ => panic!("Unexpected CLIENT INFO response type: {:?}", response),
                    };
                    let new_client_id = extract_client_id(&new_client_info)
                        .expect("Failed to extract new client ID");
                    assert_ne!(
                        initial_client_id, new_client_id,
                        "Client ID should change after reconnection if command succeeds"
                    );
                    // Check that the database ID is still 4
                    assert!(new_client_info.contains("db=4"));
                }
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
                .send_command(&mut redis::cmd("PING"), None)
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

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cluster_tls_connection_with_custom_root_cert() {
        block_on_all(async move {
            // Create a dedicated TLS cluster with custom certificates
            let tempdir = tempfile::Builder::new()
                .prefix("tls_cluster_test")
                .tempdir()
                .expect("Failed to create temp dir");
            let tls_paths = build_keys_and_certs_for_tls(&tempdir);
            let ca_cert_bytes = tls_paths.read_ca_cert_as_bytes();

            let cluster = utilities::cluster::RedisCluster::new_with_tls(3, 0, Some(tls_paths));
            let cluster_addresses = cluster.get_server_addresses();

            // Create connection request with custom root certificate
            let mut connection_request = create_connection_request(
                &cluster_addresses,
                &TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    cluster_mode: ClusterMode::Enabled,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::SecureTls.into();
            connection_request.root_certs = vec![ca_cert_bytes.into()];

            // Test that connection works with custom root cert
            let mut client = Client::new(connection_request.into(), None)
                .await
                .expect("Failed to create cluster client with custom root cert");

            // Verify connection works by sending a command
            let ping_result = client.send_command(&mut redis::cmd("PING"), None).await;
            assert_eq!(
                ping_result.unwrap(),
                Value::SimpleString("PONG".to_string())
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_cluster_tls_connection_fails_with_wrong_root_cert() {
        block_on_all(async move {
            // Create a TLS cluster with one set of certificates
            let tempdir1 = tempfile::Builder::new()
                .prefix("tls_cluster_server")
                .tempdir()
                .expect("Failed to create temp dir");
            let server_tls_paths = build_keys_and_certs_for_tls(&tempdir1);

            // Create different CA certificate for client
            let tempdir2 = tempfile::Builder::new()
                .prefix("tls_cluster_client")
                .tempdir()
                .expect("Failed to create temp dir");
            let client_tls_paths = build_keys_and_certs_for_tls(&tempdir2);
            let wrong_ca_cert_bytes = client_tls_paths.read_ca_cert_as_bytes();

            let cluster =
                utilities::cluster::RedisCluster::new_with_tls(3, 0, Some(server_tls_paths));
            let cluster_addresses = cluster.get_server_addresses();

            // Try to connect with wrong root certificate
            let mut connection_request = create_connection_request(
                &cluster_addresses,
                &TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    cluster_mode: ClusterMode::Enabled,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::SecureTls.into();
            connection_request.root_certs = vec![wrong_ca_cert_bytes.into()];

            // Connection should fail due to certificate mismatch
            let client_result = Client::new(connection_request.into(), None).await;
            assert!(
                client_result.is_err(),
                "Expected cluster connection to fail with wrong root certificate"
            );
        });
    }

    /// Test for #4990: Failover causes near-zero throughput
    /// See: https://github.com/valkey-io/valkey-glide/issues/4990
    #[rstest]
    #[timeout(LONG_CLUSTER_TEST_TIMEOUT*2)]
    fn test_failover_doesnt_block_healthy_shards() {
        block_on_all(async {
            const NUM_KEYS: usize = 1000;
            const NUM_PRIMARIES: u16 = 10;
            const NUM_REPLICAS: u16 = 1;
            const MEASUREMENT_DURATION_SECS: u64 = 5;

            // Start cluster with replicas
            let mut test_basics = setup_cluster_with_replicas(
                TestConfiguration {
                    cluster_mode: ClusterMode::Enabled,
                    shared_server: false,
                    ..Default::default()
                },
                NUM_REPLICAS,
                NUM_PRIMARIES,
            )
            .await;

            // Get cluster info
            let cluster = test_basics.cluster.unwrap();
            let addresses = cluster.get_server_addresses();

            // Set keys on all shards to establish connections
            for i in 0..NUM_KEYS {
                let key = format!("key_{}", i);
                let mut cmd = redis::cmd("SET");
                cmd.arg(&key).arg("value");
                test_basics
                    .client
                    .send_command(&mut cmd, None)
                    .await
                    .unwrap();
            }

            // Measure baseline throughput across all shards
            let baseline_count = Arc::new(AtomicU64::new(0));
            let stop_flag = Arc::new(AtomicBool::new(false));
            let mut tasks = Vec::new();
            for i in 0..NUM_KEYS {
                let mut client = test_basics.client.clone();
                let count = baseline_count.clone();
                let stop = stop_flag.clone();
                let key_id = i;

                tasks.push(tokio::spawn(async move {
                    while !stop.load(Ordering::Relaxed) {
                        let mut cmd = redis::cmd("GET");
                        cmd.arg(format!("key_{}", key_id));
                        if client.send_command(&mut cmd, None).await.is_ok() {
                            count.fetch_add(1, Ordering::Relaxed);
                        }
                    }
                }));
            }
            tokio::time::sleep(Duration::from_secs(MEASUREMENT_DURATION_SECS)).await;
            stop_flag.store(true, Ordering::Relaxed);
            futures::future::join_all(tasks).await;
            let baseline_qps = baseline_count.load(Ordering::Relaxed) / MEASUREMENT_DURATION_SECS;

            // Choose failover method:
            // 1. CLUSTER FAILOVER (graceful, no connection loss, no errors expected)
            // 2. SHUTDOWN NOSAVE (ungraceful, connection loss, triggers reconnection)
            // 3. DEBUG SEGFAULT (crash, connection loss, triggers reconnection)
            let failover_method = "SEGFAULT"; // FAILOVER | SHUTDOWN | SEGFAULT
            match failover_method {
                "FAILOVER" => {
                    // Graceful failover via replica
                    let replica_addr = &addresses[1];
                    let (replica_host, replica_port) = match replica_addr {
                        redis::ConnectionAddr::Tcp(h, p) => (h.clone(), *p),
                        redis::ConnectionAddr::TcpTls { host: h, port: p, .. } => (h.clone(), *p),
                        _ => panic!("Unexpected connection type"),
                    };
                    let mut cmd = redis::cmd("CLUSTER");
                    cmd.arg("FAILOVER");
                    let routing = RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                        host: replica_host,
                        port: replica_port,
                    });
                    let _ = test_basics.client.send_command(&mut cmd, Some(routing)).await;
                }
                "SHUTDOWN" | "SEGFAULT" => {
                    // Ungraceful failover via primary crash
                    let primary_addr = &addresses[0];
                    let (host, port) = match primary_addr {
                        redis::ConnectionAddr::Tcp(h, p) => (h.clone(), *p),
                        redis::ConnectionAddr::TcpTls { host: h, port: p, .. } => (h.clone(), *p),
                        _ => panic!("Unexpected connection type"),
                    };
                    let mut cmd = redis::cmd(if failover_method == "SHUTDOWN" { "SHUTDOWN" } else { "DEBUG" });
                    if failover_method == "SHUTDOWN" {
                        cmd.arg("NOSAVE");
                    } else {
                        cmd.arg("SEGFAULT");
                    }
                    let routing = RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress { host, port });
                    let _ = test_basics.client.send_command(&mut cmd, Some(routing)).await;
                }
                _ => panic!("Unknown failover method"),
            }

            // Measure throughput across shards during failover
            let failover_count = Arc::new(AtomicU64::new(0));
            let error_count = Arc::new(AtomicU64::new(0));
            let stop_flag = Arc::new(AtomicBool::new(false));
            let mut tasks = Vec::new();
            for i in 0..NUM_KEYS {
                let mut client = test_basics.client.clone();
                let success_count = failover_count.clone();
                let err_count = error_count.clone();
                let stop = stop_flag.clone();
                let key_id = i;

                tasks.push(tokio::spawn(async move {
                    while !stop.load(Ordering::Relaxed) {
                        let mut cmd = redis::cmd("GET");
                        cmd.arg(format!("key_{}", key_id));
                        match client.send_command(&mut cmd, None).await {
                            Ok(_) => success_count.fetch_add(1, Ordering::Relaxed),
                            Err(_) => err_count.fetch_add(1, Ordering::Relaxed),
                        };
                    }
                }));
            }
            tokio::time::sleep(Duration::from_secs(MEASUREMENT_DURATION_SECS)).await;
            stop_flag.store(true, Ordering::Relaxed);
            futures::future::join_all(tasks).await;
            let failover_qps = failover_count.load(Ordering::Relaxed) / MEASUREMENT_DURATION_SECS;
            let errors = error_count.load(Ordering::Relaxed);

            // Verify throughput recovers after full cluster failover
            let expected_min_throughput = baseline_qps * 50 / 100;
            println!(
                "Baseline QPS: {}, Failover QPS: {}, Errors: {}",
                baseline_qps, failover_qps, errors
            );
            assert!(
                failover_qps > expected_min_throughput,
                "Throughput dropped too much during full cluster failover: {} vs baseline {} (expected >{})",
                failover_qps,
                baseline_qps,
                expected_min_throughput
            );
        });
    }
}
