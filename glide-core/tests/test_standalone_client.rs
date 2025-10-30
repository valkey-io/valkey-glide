// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod utilities;

#[cfg(test)]
mod standalone_client_tests {
    use crate::utilities::mocks::{Mock, ServerMock};
    use std::collections::HashMap;

    use super::*;
    use glide_core::{
        client::{Client as GlideClient, ConnectionError, StandaloneClient},
        connection_request::{ProtocolVersion, ReadFrom},
    };
    use redis::{FromRedisValue, Value};
    use rstest::rstest;
    use utilities::*;

    async fn get_connected_clients(client: &mut StandaloneClient) -> usize {
        let mut cmd = redis::Cmd::new();
        cmd.arg("CLIENT").arg("LIST");
        let result: Value = client.send_command(&cmd).await.expect("CLIENT LIST failed");
        match result {
            Value::BulkString(bytes) => {
                // Handles RESP2
                let s = String::from_utf8_lossy(&bytes);
                s.lines().count()
            }
            Value::VerbatimString { format: _, text } => {
                // Handles RESP3
                text.lines().count()
            }
            _ => {
                panic!("CLIENT LIST did not return a BulkString or VerbatimString, got: {result:?}")
            }
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(LONG_STANDALONE_TEST_TIMEOUT)]
    #[cfg(feature = "standalone_heartbeat")]
    fn test_detect_disconnect_and_reconnect_using_heartbeat(#[values(false, true)] use_tls: bool) {
        let (sender, receiver) = tokio::sync::oneshot::channel();
        block_on_all(async move {
            let mut test_basics = setup_test_basics(use_tls).await;
            let server = test_basics.server.expect("Server shouldn't be None");
            let address = server.get_client_addr();
            drop(server);

            // we use another thread, so that the creation of the server won't block the client work.
            std::thread::spawn(move || {
                block_on_all(async move {
                    let new_server = RedisServer::new_with_addr_and_modules(address.clone(), &[]);
                    wait_for_server_to_become_ready(&address).await;
                    let _ = sender.send(new_server);
                })
            });

            let _new_server = receiver.await;
            tokio::time::sleep(
                glide_core::client::HEARTBEAT_SLEEP_DURATION + std::time::Duration::from_secs(1),
            )
            .await;

            let mut get_command = redis::Cmd::new();
            get_command
                .arg("GET")
                .arg("test_detect_disconnect_and_reconnect_using_heartbeat");
            let get_result = test_basics.client.send_command(&get_command).await.unwrap();
            assert_eq!(get_result, Value::Nil);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_automatic_reconnect(#[values(false, true)] use_tls: bool) {
        block_on_all(async move {
            let shared_config = TestConfiguration {
                use_tls,
                cluster_mode: ClusterMode::Disabled,
                shared_server: true,
                ..Default::default()
            };

            let mut validation_client = setup_test_basics_internal(&shared_config).await;

            let mut monitoring_client = setup_test_basics_internal(&shared_config).await;

            let mut info_clients_cmd = redis::Cmd::new();
            info_clients_cmd.arg("INFO").arg("CLIENTS");

            // validate 2 connected clients
            let info_clients: String = redis::from_owned_redis_value(
                monitoring_client
                    .client
                    .send_command(&info_clients_cmd)
                    .await
                    .unwrap(),
            )
            .unwrap();

            assert!(info_clients.contains("connected_clients:2"));

            kill_connection(&mut validation_client.client).await;

            // short sleep to allow the connections checker task to reconnect - 1s is enough since the detection should happen immediately
            tokio::time::sleep(std::time::Duration::from_secs(1)).await;

            // validate 2 connected clients
            let info_clients: String = redis::from_owned_redis_value(
                monitoring_client
                    .client
                    .send_command(&info_clients_cmd)
                    .await
                    .unwrap(),
            )
            .unwrap();

            assert!(info_clients.contains("connected_clients:2"));

            // validate connection works
            let ping_result = validation_client
                .client
                .send_command(&redis::cmd("PING"))
                .await
                .ok();
            assert_eq!(ping_result, Some(Value::SimpleString("PONG".to_string())));
        });
    }

    fn get_mock_addresses(mocks: &[ServerMock]) -> Vec<redis::ConnectionAddr> {
        mocks.iter().flat_map(|mock| mock.get_addresses()).collect()
    }

    fn create_primary_responses() -> HashMap<String, Value> {
        let mut primary_responses = std::collections::HashMap::new();
        primary_responses.insert(
            "*1\r\n$4\r\nPING\r\n".to_string(),
            Value::BulkString(b"PONG".to_vec()),
        );
        primary_responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::BulkString(b"role:master\r\nconnected_slaves:3\r\n".to_vec()),
        );
        primary_responses.insert(
            "*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".to_string(),
            Value::Map(vec![
                (Value::BulkString(b"proto".to_vec()), Value::Int(3)),
                (
                    Value::BulkString(b"role".to_vec()),
                    Value::BulkString(b"master".to_vec()),
                ),
            ]),
        );
        primary_responses
    }

    fn create_replica_response() -> HashMap<String, Value> {
        let mut replica_responses = std::collections::HashMap::new();
        replica_responses.insert(
            "*1\r\n$4\r\nPING\r\n".to_string(),
            Value::BulkString(b"PONG".to_vec()),
        );
        replica_responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::BulkString(b"role:slave\r\n".to_vec()),
        );
        replica_responses.insert(
            "*2\r\n$5\r\nHELLO\r\n$1\r\n3\r\n".to_string(),
            Value::Map(vec![
                (Value::BulkString(b"proto".to_vec()), Value::Int(3)),
                (
                    Value::BulkString(b"role".to_vec()),
                    Value::BulkString(b"replica".to_vec()),
                ),
            ]),
        );
        replica_responses
    }
    fn create_primary_conflict_mock_two_primaries_one_replica() -> Vec<ServerMock> {
        let mut listeners: Vec<std::net::TcpListener> =
            (0..3).map(|_| get_listener_on_available_port()).collect();
        let primary_1 =
            ServerMock::new_with_listener(create_primary_responses(), listeners.pop().unwrap());
        let primary_2 =
            ServerMock::new_with_listener(create_primary_responses(), listeners.pop().unwrap());
        let replica =
            ServerMock::new_with_listener(create_replica_response(), listeners.pop().unwrap());
        vec![primary_1, primary_2, replica]
    }

    fn create_primary_mock_with_replicas(replica_count: usize) -> Vec<ServerMock> {
        let mut listeners: Vec<std::net::TcpListener> = (0..replica_count + 1)
            .map(|_| get_listener_on_available_port())
            .collect();
        let primary =
            ServerMock::new_with_listener(create_primary_responses(), listeners.pop().unwrap());
        let mut mocks = vec![primary];

        mocks.extend(
            listeners
                .into_iter()
                .map(|listener| ServerMock::new_with_listener(create_replica_response(), listener)),
        );
        mocks
    }

    struct ReadFromReplicaTestConfig {
        read_from: ReadFrom,
        expected_primary_reads: u16,
        expected_replica_reads: Vec<u16>,
        number_of_initial_replicas: usize,
        number_of_missing_replicas: usize,
        number_of_replicas_dropped_after_connection: usize,
        number_of_requests_sent: usize,
    }

    impl Default for ReadFromReplicaTestConfig {
        fn default() -> Self {
            Self {
                read_from: ReadFrom::Primary,
                expected_primary_reads: 3,
                expected_replica_reads: vec![0, 0, 0],
                number_of_initial_replicas: 3,
                number_of_missing_replicas: 0,
                number_of_replicas_dropped_after_connection: 0,
                number_of_requests_sent: 3,
            }
        }
    }

    fn test_read_from_replica(config: ReadFromReplicaTestConfig) {
        let mut servers = create_primary_mock_with_replicas(
            config.number_of_initial_replicas - config.number_of_missing_replicas,
        );
        let mut cmd = redis::cmd("GET");
        cmd.arg("foo");

        for server in servers.iter() {
            for _ in 0..3 {
                server.add_response(&cmd, "$-1\r\n".to_string());
            }
        }

        let mut addresses = get_mock_addresses(&servers);
        for i in 4 - config.number_of_missing_replicas..4 {
            addresses.push(redis::ConnectionAddr::Tcp(
                "192.0.2.1".to_string(), // Use non-routable IP for fast connection failure
                6379 + i as u16,
            ));
        }
        let mut connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());
        connection_request.read_from = config.read_from.into();

        block_on_all(async {
            let mut client = StandaloneClient::create_client(connection_request.into(), None, None)
                .await
                .unwrap();
            logger_core::log_info(
                "Test",
                format!(
                    "Closing {} servers after connection established",
                    config.number_of_replicas_dropped_after_connection
                ),
            );
            for server in servers.drain(1..config.number_of_replicas_dropped_after_connection + 1) {
                server.close().await;
            }
            logger_core::log_info(
                "Test",
                format!("sending {} messages", config.number_of_requests_sent),
            );

            tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
            for _ in 0..config.number_of_requests_sent {
                let _ = client.send_command(&cmd).await;
            }
        });

        assert_eq!(
            servers[0].get_number_of_received_commands(),
            config.expected_primary_reads
        );
        let mut replica_reads: Vec<_> = servers
            .iter()
            .skip(1)
            .map(|mock| mock.get_number_of_received_commands())
            .collect();
        replica_reads.sort();
        assert!(config.expected_replica_reads <= replica_reads);
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_always_read_from_primary() {
        test_read_from_replica(ReadFromReplicaTestConfig::default());
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 1, 1],
            ..Default::default()
        });
    }

    // TODO - Current test falls back to PreferReplica when run, need to integrate the az here also
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_az_affinity() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::AZAffinity,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 1, 1],
            ..Default::default()
        });
    }
    // TODO - Needs changes in the struct and the create_primary_mock
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_az_affinity_replicas_and_primary() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::AZAffinityReplicasAndPrimary,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 1, 1],
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_skip_disconnected_replicas() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 2],
            number_of_missing_replicas: 1,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_read_from_primary_if_no_replica_is_connected() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 3,
            expected_replica_reads: vec![],
            number_of_missing_replicas: 3,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_do_not_read_from_disconnected_replica() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            // Since we drop 1 replica after connection establishment
            // we expect all reads to be handled by the remaining replicas
            expected_replica_reads: vec![3, 3],
            number_of_replicas_dropped_after_connection: 1,
            number_of_requests_sent: 6,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_with_single_replica() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from: ReadFrom::PreferReplica,
            expected_primary_reads: 0,
            expected_replica_reads: vec![3],
            number_of_initial_replicas: 1,
            number_of_requests_sent: 3,
            ..Default::default()
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_primary_conflict_raises_error() {
        let mocks = create_primary_conflict_mock_two_primaries_one_replica();
        let addresses = get_mock_addresses(&mocks);
        let connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());
        block_on_all(async {
            let client_res = StandaloneClient::create_client(connection_request.into(), None, None)
                .await
                .map_err(ConnectionError::Standalone);
            assert!(client_res.is_err());
            let error = client_res.unwrap_err();
            assert!(matches!(error, ConnectionError::Standalone(_),));
            let primary_1_addr = addresses.first().unwrap().to_string();
            let primary_2_addr = addresses.get(1).unwrap().to_string();
            let replica_addr = addresses.get(2).unwrap().to_string();
            let err_msg = error.to_string().to_ascii_lowercase();
            assert!(
                err_msg.contains("conflict")
                    && err_msg.contains(&primary_1_addr)
                    && err_msg.contains(&primary_2_addr)
                    && !err_msg.contains(&replica_addr)
            );
        });
    }

    #[rstest]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_send_acl_request_to_all_nodes() {
        let mocks = create_primary_mock_with_replicas(2);
        let mut cmd = redis::cmd("ACL");
        cmd.arg("SETUSER").arg("foo");

        for mock in mocks.iter() {
            for _ in 0..3 {
                mock.add_response(&cmd, "+OK\r\n".to_string());
            }
        }

        let addresses: Vec<redis::ConnectionAddr> =
            mocks.iter().flat_map(|mock| mock.get_addresses()).collect();

        let connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());

        block_on_all(async {
            let mut client = StandaloneClient::create_client(connection_request.into(), None, None)
                .await
                .unwrap();

            let result = client.send_command(&cmd).await;
            assert_eq!(result, Ok(Value::Okay));
        });

        for mock in mocks {
            assert_eq!(mock.get_number_of_received_commands(), 1);
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
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
        let mut client_info_cmd = redis::Cmd::new();
        client_info_cmd.arg("CLIENT").arg("INFO");
        block_on_all(async move {
            let test_basics = setup_test_basics_internal(&TestConfiguration {
                database_id: 4,
                shared_server: true,
                ..Default::default()
            })
            .await;
            let mut client = test_basics.client;

            let client_info: String = String::from_owned_redis_value(
                client.send_command(&client_info_cmd).await.unwrap(),
            )
            .unwrap();
            assert!(client_info.contains("db=4"));

            // Extract initial client ID
            let initial_client_id =
                extract_client_id(&client_info).expect("Failed to extract initial client ID");

            kill_connection(&mut client).await;

            let res = client.send_command(&client_info_cmd).await;
            match res {
                Err(err) => {
                    // Connection was dropped as expected
                    assert!(
                        err.is_connection_dropped() || err.is_timeout(),
                        "Expected connection dropped or timeout error, got: {err:?}",
                    );
                    let client_info = repeat_try_create(|| async {
                        let mut client = client.clone();
                        String::from_owned_redis_value(
                            client.send_command(&client_info_cmd).await.unwrap(),
                        )
                        .ok()
                    })
                    .await;
                    assert!(client_info.contains("db=4"));
                }
                Ok(response) => {
                    // Command succeeded, extract new client ID and compare
                    let new_client_info: String = String::from_owned_redis_value(response).unwrap();
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
    #[timeout(LONG_STANDALONE_TEST_TIMEOUT)]
    fn test_lazy_connection_establishes_on_first_command(
        #[values(ProtocolVersion::RESP2, ProtocolVersion::RESP3)] protocol: ProtocolVersion,
    ) {
        block_on_all(async move {
            const USE_TLS: bool = false;

            // 1. Base configuration for creating a DEDICATED standalone server
            let base_config_for_dedicated_server = utilities::TestConfiguration {
                use_tls: USE_TLS,
                protocol,
                shared_server: false, // request a dedicated server
                cluster_mode: ClusterMode::Disabled,
                lazy_connect: false, // Monitoring client connects eagerly
                ..Default::default()
            };

            // 2. Setup the dedicated standalone server and the monitoring client.
            let mut monitoring_test_basics =
                utilities::setup_test_basics_internal(&base_config_for_dedicated_server).await;
            // monitoring_client is already a StandaloneClient
            let monitoring_client = &mut monitoring_test_basics.client;

            // Extract the address of the DEDICATED standalone server
            let dedicated_server_address = match &monitoring_test_basics.server {
                // Corrected field access
                Some(server) => {
                    // Directly use `server`
                    server.get_client_addr().clone()
                }
                None => panic!(
                    "Expected a dedicated standalone server to be created by setup_test_basics_internal"
                ),
            };

            // 3. Get initial client count on the DEDICATED server.
            let clients_before_lazy_init = get_connected_clients(monitoring_client).await;
            logger_core::log_info(
                "TestStandaloneLazy",
                format!(
                    "Clients before lazy client init (protocol={protocol:?} on dedicated server): {clients_before_lazy_init}"
                ),
            );

            // 4. Configuration for the lazy client, targeting the SAME dedicated server.
            let mut lazy_client_config = base_config_for_dedicated_server.clone();
            lazy_client_config.lazy_connect = true;

            let mut lazy_client_connection_request_pb = utilities::create_connection_request(
                std::slice::from_ref(&dedicated_server_address),
                &lazy_client_config,
            );
            lazy_client_connection_request_pb.cluster_mode_enabled = false;

            // 5. Create the "lazy" client.
            // For standalone lazy client, we'd expect to create a glide_core::client::Client
            // that internally holds a LazyClient configured for standalone.
            let core_connection_request: glide_core::connection_request::ConnectionRequest =
                lazy_client_connection_request_pb;

            // We need to use the generic Client::new for lazy loading behavior
            let mut lazy_glide_client_enum = GlideClient::new(core_connection_request.into(), None)
                .await
                .expect("Failed to create lazy GlideClient for dedicated server");

            // 6. Assert that no new connection was made yet by the lazy client
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;
            let clients_after_lazy_init = get_connected_clients(monitoring_client).await; // Pass &mut StandaloneClient
            logger_core::log_info(
                "TestStandaloneLazy",
                format!(
                    "Clients after lazy client init (protocol={protocol:?} on dedicated server): {clients_after_lazy_init}"
                ),
            );
            assert_eq!(
                clients_after_lazy_init, clients_before_lazy_init,
                "Lazy client (on dedicated server) should not connect before the first command. Before: {clients_before_lazy_init}, After: {clients_after_lazy_init}. protocol={protocol:?}"
            );

            // 7. Send the first command using the lazy client (which is a GlideClient)
            logger_core::log_info(
                "TestStandaloneLazy",
                format!(
                    "Sending first command to lazy client (PING) (protocol={protocol:?} on dedicated server)"
                ),
            );
            let ping_response = lazy_glide_client_enum
                .send_command(&redis::cmd("PING"), None)
                .await;
            assert!(
                ping_response.is_ok(),
                "PING command failed (on dedicated server): {:?}. protocol={:?}",
                ping_response.as_ref().err(),
                protocol
            );
            assert_eq!(
                ping_response.unwrap(),
                redis::Value::SimpleString("PONG".to_string())
            );

            // 8. Assert that a new connection was made by the lazy client on the dedicated server
            let clients_after_first_command = get_connected_clients(monitoring_client).await; // Pass &mut StandaloneClient
            logger_core::log_info(
                "TestStandaloneLazy",
                format!(
                    "Clients after first command (protocol={protocol:?} on dedicated server): {clients_after_first_command}"
                ),
            );
            assert_eq!(
                clients_after_first_command,
                clients_before_lazy_init + 1,
                "Lazy client (on dedicated server) should connect after the first command. Before: {clients_before_lazy_init}, After: {clients_after_first_command}. protocol={protocol:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_tls_connection_with_custom_root_cert() {
        block_on_all(async move {
            // Create a dedicated TLS server with custom certificates
            let tempdir = tempfile::Builder::new()
                .prefix("tls_test")
                .tempdir()
                .expect("Failed to create temp dir");
            let tls_paths = build_keys_and_certs_for_tls(&tempdir);
            let ca_cert_bytes = tls_paths.read_ca_cert_as_bytes();

            let server = RedisServer::new_with_addr_tls_modules_and_spawner(
                redis::ConnectionAddr::TcpTls {
                    host: "127.0.0.1".to_string(),
                    port: get_available_port(),
                    insecure: false,
                    tls_params: None,
                },
                Some(tls_paths),
                &[],
                |cmd| cmd.spawn().expect("Failed to spawn server"),
            );

            let server_addr = server.get_client_addr();
            // Skip wait_for_server_to_become_ready since it uses default OS verifier
            tokio::time::sleep(std::time::Duration::from_millis(200)).await; // Give server time to start

            // Create connection request with custom root certificate
            let mut connection_request = create_connection_request(
                &[server_addr],
                &TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::SecureTls.into();
            connection_request.root_certs = vec![ca_cert_bytes.into()];

            // Test that connection works with custom root cert
            let mut client = StandaloneClient::create_client(connection_request.into(), None, None)
                .await
                .expect("Failed to create client with custom root cert");

            // Verify connection works by sending a command
            let ping_result = client.send_command(&redis::cmd("PING")).await;
            assert_eq!(
                ping_result.unwrap(),
                Value::SimpleString("PONG".to_string())
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_tls_connection_fails_with_wrong_root_cert() {
        block_on_all(async move {
            // Create a TLS server with one set of certificates
            let tempdir1 = tempfile::Builder::new()
                .prefix("tls_test_server")
                .tempdir()
                .expect("Failed to create temp dir");
            let server_tls_paths = build_keys_and_certs_for_tls(&tempdir1);

            // Create different CA certificate for client
            let tempdir2 = tempfile::Builder::new()
                .prefix("tls_test_client")
                .tempdir()
                .expect("Failed to create temp dir");
            let client_tls_paths = build_keys_and_certs_for_tls(&tempdir2);
            let wrong_ca_cert_bytes = client_tls_paths.read_ca_cert_as_bytes();

            let server = RedisServer::new_with_addr_tls_modules_and_spawner(
                redis::ConnectionAddr::TcpTls {
                    host: "127.0.0.1".to_string(),
                    port: get_available_port(),
                    insecure: false,
                    tls_params: None,
                },
                Some(server_tls_paths),
                &[],
                |cmd| cmd.spawn().expect("Failed to spawn server"),
            );

            let server_addr = server.get_client_addr();
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;

            // Try to connect with wrong root certificate
            let mut connection_request = create_connection_request(
                &[server_addr],
                &TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::SecureTls.into();
            connection_request.root_certs = vec![wrong_ca_cert_bytes.into()];
            // Use minimal retries to fail fast
            connection_request.connection_retry_strategy =
                Some(glide_core::connection_request::ConnectionRetryStrategy {
                    number_of_retries: 1,
                    factor: 1,
                    exponent_base: 1,
                    ..Default::default()
                })
                .into();

            // Connection should fail due to certificate mismatch
            let client_result =
                StandaloneClient::create_client(connection_request.into(), None, None).await;
            assert!(
                client_result.is_err(),
                "Expected connection to fail with wrong root certificate"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_tls_connection_fails_with_invalid_cert_bytes() {
        block_on_all(async move {
            let server_addr = redis::ConnectionAddr::TcpTls {
                host: "127.0.0.1".to_string(),
                port: get_available_port(),
                insecure: false,
                tls_params: None,
            };

            let mut connection_request = create_connection_request(
                &[server_addr],
                &TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::SecureTls.into();
            // Provide invalid certificate bytes that will fail PEM parsing
            // Using a PEM-like structure but with invalid base64 content
            connection_request.root_certs = vec![
                b"-----BEGIN CERTIFICATE-----\n!!!invalid base64!!!\n-----END CERTIFICATE-----"
                    .to_vec()
                    .into(),
            ];

            // Client creation should fail during certificate parsing
            let client_result =
                StandaloneClient::create_client(connection_request.into(), None, None).await;
            assert!(
                client_result.is_err(),
                "Expected client creation to fail with invalid certificate bytes"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_tls_connection_fails_with_custom_certs_and_no_tls() {
        block_on_all(async move {
            let server_addr =
                redis::ConnectionAddr::Tcp("127.0.0.1".to_string(), get_available_port());

            let mut connection_request = create_connection_request(
                &[server_addr],
                &TestConfiguration {
                    use_tls: false,
                    shared_server: false,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::NoTls.into();
            // Provide custom root certs but with NoTls mode
            connection_request.root_certs = vec![b"some certificate".to_vec().into()];

            // Client creation should fail due to invalid configuration
            let client_result =
                StandaloneClient::create_client(connection_request.into(), None, None).await;
            assert!(
                client_result.is_err(),
                "Expected client creation to fail when custom certs provided with NoTls mode"
            );
            let err = client_result.unwrap_err();
            let err_msg = format!("{:?}", err).to_lowercase();
            assert!(
                err_msg.contains("tls") && err_msg.contains("disabled"),
                "Error message should mention TLS being disabled, got: {}",
                err_msg
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_STANDALONE_TEST_TIMEOUT)]
    fn test_tls_connection_with_multiple_root_certs_first_invalid() {
        block_on_all(async move {
            // Create server with valid certificates
            let tempdir_server = tempfile::Builder::new()
                .prefix("tls_test_server")
                .tempdir()
                .expect("Failed to create temp dir");
            let server_tls_paths = build_keys_and_certs_for_tls(&tempdir_server);
            let valid_ca_cert_bytes = server_tls_paths.read_ca_cert_as_bytes();

            // Create invalid CA certificate
            let tempdir_invalid = tempfile::Builder::new()
                .prefix("tls_test_invalid")
                .tempdir()
                .expect("Failed to create temp dir");
            let invalid_tls_paths = build_keys_and_certs_for_tls(&tempdir_invalid);
            let invalid_ca_cert_bytes = invalid_tls_paths.read_ca_cert_as_bytes();

            let server = RedisServer::new_with_addr_tls_modules_and_spawner(
                redis::ConnectionAddr::TcpTls {
                    host: "127.0.0.1".to_string(),
                    port: get_available_port(),
                    insecure: false,
                    tls_params: None,
                },
                Some(server_tls_paths),
                &[],
                |cmd| cmd.spawn().expect("Failed to spawn server"),
            );

            let server_addr = server.get_client_addr();
            tokio::time::sleep(std::time::Duration::from_millis(200)).await;

            // Provide two root certs: first invalid, second valid
            let mut connection_request = create_connection_request(
                &[server_addr],
                &TestConfiguration {
                    use_tls: true,
                    shared_server: false,
                    ..Default::default()
                },
            );
            connection_request.tls_mode = glide_core::connection_request::TlsMode::SecureTls.into();
            connection_request.root_certs =
                vec![invalid_ca_cert_bytes.into(), valid_ca_cert_bytes.into()];

            // Connection should succeed using the second (valid) certificate
            let mut client = StandaloneClient::create_client(connection_request.into(), None, None)
                .await
                .expect("Failed to create client with multiple root certs");

            let ping_result = client.send_command(&redis::cmd("PING")).await;
            assert_eq!(
                ping_result.unwrap(),
                Value::SimpleString("PONG".to_string())
            );
        });
    }
}
