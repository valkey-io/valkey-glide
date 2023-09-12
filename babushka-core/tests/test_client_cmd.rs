mod utilities;

#[cfg(test)]
mod client_cmd_tests {
    use crate::utilities::mocks::{Mock, ServerMock};

    use super::*;
    use babushka::{client::ClientCMD, connection_request::ReadFromReplicaStrategy};
    use redis::Value;
    use rstest::rstest;
    use std::time::Duration;
    use utilities::*;

    #[rstest]
    #[timeout(LONG_CMD_TEST_TIMEOUT)]
    fn test_report_disconnect_and_reconnect_after_temporary_disconnect(
        #[values(false, true)] use_tls: bool,
    ) {
        let server_available_event =
            std::sync::Arc::new(futures_intrusive::sync::ManualResetEvent::new(false));
        let server_available_event_clone = server_available_event.clone();
        block_on_all(async move {
            let test_basics = setup_test_basics(use_tls).await;
            let server = test_basics.server.unwrap();
            let mut client = test_basics.client;
            let address = server.get_client_addr();
            drop(server);

            // we use another thread, so that the creation of the server won't block the client work.
            let thread = std::thread::spawn(move || {
                block_on_all(async move {
                    let mut get_command = redis::Cmd::new();
                    get_command
                        .arg("GET")
                        .arg("test_report_disconnect_and_reconnect_after_temporary_disconnect");
                    let error = client.send_packed_command(&get_command).await;
                    assert!(error.is_err(), "{error:?}",);

                    server_available_event.wait().await;
                    let get_result = repeat_try_create(|| async {
                        let mut client = client.clone();
                        client.send_packed_command(&get_command).await.ok()
                    })
                    .await;
                    assert_eq!(get_result, Value::Nil);
                });
            });

            // If we don't sleep here, TLS connections will start reconnecting too soon, and then will timeout
            // before the server is ready.
            tokio::time::sleep(Duration::from_millis(10)).await;

            let _new_server = RedisServer::new_with_addr_and_modules(address.clone(), &[]);
            wait_for_server_to_become_ready(&address).await;
            server_available_event_clone.set();

            thread.join().unwrap();
        });
    }

    #[rstest]
    #[timeout(LONG_CMD_TEST_TIMEOUT)]
    #[cfg(cmd_heartbeat)]
    fn test_detect_disconnect_and_reconnect_using_heartbeat(#[values(false, true)] use_tls: bool) {
        let (sender, receiver) = tokio::sync::oneshot::channel();
        block_on_all(async move {
            let mut test_basics = setup_test_basics(use_tls).await;
            let server = test_basics.server;
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
            tokio::time::sleep(babushka::client::HEARTBEAT_SLEEP_DURATION + Duration::from_secs(1))
                .await;

            let mut get_command = redis::Cmd::new();
            get_command
                .arg("GET")
                .arg("test_detect_disconnect_and_reconnect_using_heartbeat");
            let get_result = test_basics
                .client
                .send_packed_command(&get_command)
                .await
                .unwrap();
            assert_eq!(get_result, Value::Nil);
        });
    }

    fn create_primary_mock_with_replicas(replica_count: usize) -> Vec<ServerMock> {
        let mut listeners: Vec<std::net::TcpListener> = (0..replica_count + 1)
            .map(|_| get_listener_on_available_port())
            .collect();
        let mut primary_responses = std::collections::HashMap::new();
        primary_responses.insert(
            "*1\r\n$4\r\nPING\r\n".to_string(),
            Value::Data(b"PONG".to_vec()),
        );
        primary_responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::Data(b"role:master\r\nconnected_slaves:3\r\n".to_vec()),
        );
        let primary = ServerMock::new_with_listener(primary_responses, listeners.pop().unwrap());
        let mut mocks = vec![primary];
        let mut replica_responses = std::collections::HashMap::new();
        replica_responses.insert(
            "*1\r\n$4\r\nPING\r\n".to_string(),
            Value::Data(b"PONG".to_vec()),
        );
        replica_responses.insert(
            "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
            Value::Data(b"role:slave\r\n".to_vec()),
        );
        mocks.extend(
            listeners
                .into_iter()
                .map(|listener| ServerMock::new_with_listener(replica_responses.clone(), listener)),
        );
        mocks
    }

    struct ReadFromReplicaTestConfig {
        read_from_replica_strategy: ReadFromReplicaStrategy,
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
                read_from_replica_strategy: ReadFromReplicaStrategy::AlwaysFromPrimary,
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
        let mut mocks = create_primary_mock_with_replicas(
            config.number_of_initial_replicas - config.number_of_missing_replicas,
        );
        let mut cmd = redis::cmd("GET");
        cmd.arg("foo");

        for mock in mocks.iter() {
            for _ in 0..3 {
                mock.add_response(&cmd, "$-1\r\n".to_string());
            }
        }

        let mut addresses: Vec<redis::ConnectionAddr> =
            mocks.iter().flat_map(|mock| mock.get_addresses()).collect();

        for i in 4 - config.number_of_missing_replicas..4 {
            addresses.push(redis::ConnectionAddr::Tcp(
                "foo".to_string(),
                6379 + i as u16,
            ));
        }
        let mut connection_request =
            create_connection_request(addresses.as_slice(), &Default::default());
        connection_request.read_from_replica_strategy = config.read_from_replica_strategy.into();

        block_on_all(async {
            let mut client = ClientCMD::create_client(connection_request).await.unwrap();
            for mock in mocks.drain(1..config.number_of_replicas_dropped_after_connection + 1) {
                mock.close().await;
            }
            for _ in 0..config.number_of_requests_sent {
                let _ = client.send_packed_command(&cmd).await;
            }
        });

        assert_eq!(
            mocks[0].get_number_of_received_commands(),
            config.expected_primary_reads
        );
        let mut replica_reads: Vec<_> = mocks
            .iter()
            .skip(1)
            .map(|mock| mock.get_number_of_received_commands())
            .collect();
        replica_reads.sort();
        assert_eq!(config.expected_replica_reads, replica_reads);
    }

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_read_from_replica_always_read_from_primary() {
        test_read_from_replica(ReadFromReplicaTestConfig::default());
    }

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from_replica_strategy: ReadFromReplicaStrategy::RoundRobin,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 1, 1],
            ..Default::default()
        });
    }

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_skip_disconnected_replicas() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from_replica_strategy: ReadFromReplicaStrategy::RoundRobin,
            expected_primary_reads: 0,
            expected_replica_reads: vec![1, 2],
            number_of_missing_replicas: 1,
            ..Default::default()
        });
    }

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_read_from_primary_if_no_replica_is_connected() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from_replica_strategy: ReadFromReplicaStrategy::RoundRobin,
            expected_primary_reads: 3,
            expected_replica_reads: vec![],
            number_of_missing_replicas: 3,
            ..Default::default()
        });
    }

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_do_not_read_from_disconnected_replica() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from_replica_strategy: ReadFromReplicaStrategy::RoundRobin,
            expected_primary_reads: 0,
            expected_replica_reads: vec![2, 3],
            number_of_replicas_dropped_after_connection: 1,
            number_of_requests_sent: 6,
            ..Default::default()
        });
    }

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_read_from_replica_round_robin_with_single_replica() {
        test_read_from_replica(ReadFromReplicaTestConfig {
            read_from_replica_strategy: ReadFromReplicaStrategy::RoundRobin,
            expected_primary_reads: 0,
            expected_replica_reads: vec![3],
            number_of_initial_replicas: 1,
            number_of_requests_sent: 3,
            ..Default::default()
        });
    }
}
