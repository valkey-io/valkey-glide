// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod utilities;

#[macro_export]
/// Compare `$expected` with `$actual`. This macro, will exit the test process
/// if the assertion fails. Unlike `assert_eq!` - this also works in tasks
macro_rules! async_assert_eq {
    ($expected:expr, $actual:expr) => {{
        if $actual != $expected {
            println!(
                "{}:{}: Expected: {:?} != Actual: {:?}",
                file!(),
                line!(),
                $actual,
                $expected
            );
            std::process::exit(1);
        }
    }};
}

#[cfg(test)]
pub(crate) mod shared_client_tests {
    use glide_core::Telemetry;
    use redis::{cluster_topology::get_slot, cmd};
    use std::collections::HashMap;

    use super::*;
    use glide_core::client::{Client, DEFAULT_RESPONSE_TIMEOUT};
    use glide_core::connection_request::ProtocolVersion;
    use redis::cluster_routing::{SingleNodeRoutingInfo, SlotAddr};
    use redis::{
        FromRedisValue, InfoDict, Pipeline, PipelineRetryStrategy, RedisConnectionInfo, Value,
        cluster_routing::{MultipleNodeRoutingInfo, Route, RoutingInfo},
    };
    use rstest::rstest;
    use utilities::BackingServer;
    use utilities::cluster::*;
    use utilities::*;

    #[cfg(feature = "iam_tests")]
    use glide_core::connection_request::{
        AuthenticationInfo, IamCredentials, ServiceType, TlsMode,
    };

    #[cfg(feature = "iam_tests")]
    const ELASTICACHE_CLUSTER_IAM_ENDPOINT: &str = "elasticache-cluster-iam.endpoint"; // Replace with your cluster endpoint
    #[cfg(feature = "iam_tests")]
    const ELASTICACHE_STANDALONE_IAM_ENDPOINT: &str = "elasticache-standalone-iam.endpoint"; // Replace with your standalone endpoint
    #[cfg(feature = "iam_tests")]
    const MEMORYDB_CLUSTER_IAM_ENDPOINT: &str = "memorydb-cluster-iam.endpoint"; // Replace with your cluster endpoint

    struct TestBasics {
        server: BackingServer,
        client: Client,
    }

    async fn create_client(server: &BackingServer, configuration: TestConfiguration) -> Client {
        match server {
            BackingServer::Standalone(server) => {
                let connection_addr = server
                    .as_ref()
                    .map(|server| server.get_client_addr())
                    .unwrap_or(get_shared_server_address(configuration.use_tls));

                // TODO - this is a patch, handling the situation where the new server
                // still isn't available to connection. This should be fixed in [RedisServer].
                repeat_try_create(|| async {
                    Client::new(
                        create_connection_request(
                            std::slice::from_ref(&connection_addr),
                            &configuration,
                        )
                        .into(),
                        None,
                    )
                    .await
                    .ok()
                })
                .await
            }
            BackingServer::Cluster(cluster) => {
                create_cluster_client(cluster.as_ref(), configuration).await
            }
        }
    }

    async fn setup_test_basics(use_cluster: bool, configuration: TestConfiguration) -> TestBasics {
        if use_cluster {
            let cluster_basics = cluster::setup_test_basics_internal(configuration).await;
            TestBasics {
                server: BackingServer::Cluster(cluster_basics.cluster),
                client: cluster_basics.client,
            }
        } else {
            let test_basics = utilities::setup_test_basics_internal(&configuration).await;
            let server = BackingServer::Standalone(test_basics.server);
            let client = create_client(&server, configuration).await;
            TestBasics { server, client }
        }
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_set_and_get(
        #[values(false, true)] use_tls: bool,
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls,
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_transaction_is_not_routed() {
        // This test checks that a transaction without user routing isn't routed to a random node before reaching its target.
        // This is tested by checking how many requests each node has received - one of the 6 nodes should have more requests than the others.
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                true,
                TestConfiguration {
                    use_tls: false,
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // reset stats on all connections
            let mut cmd = redis::cmd("CONFIG");
            cmd.arg("RESETSTAT");
            let _ = test_basics
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

            // Send a keyed transaction
            let key = generate_random_string(6);
            let mut pipe = redis::pipe();
            pipe.cmd("GET").arg(key);
            pipe.atomic();

            for _ in 0..4 {
                let _ = test_basics
                    .client
                    .send_transaction(&pipe, None, None, false)
                    .await
                    .unwrap();
            }

            // Gather info from each server
            let mut cmd = redis::cmd("INFO");
            cmd.arg("commandstats");
            let values = test_basics
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

            let values: Vec<_> = match values {
                Value::Map(map) => map.into_iter().filter_map(|(_, value)| {
                    let map = InfoDict::from_owned_redis_value(value).unwrap();
                    map.get::<String>("cmdstat_get")?
                        .split_once(',')?
                        .0
                        .split_at(6) // split after `calls=``
                        .1
                        .parse::<u32>()
                        .ok()
                }),

                _ => panic!("Expected map, got `{values:?}`"),
            }
            .collect();

            // Check that only one node received all of the GET calls.
            assert_eq!(values.len(), 1);
            assert_eq!(values[0], 4);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_resp_support(#[values(false, true)] use_cluster: bool, #[values(2, 3)] protocol: i64) {
        let protocol_enum = match protocol {
            2 => redis::ProtocolVersion::RESP2,
            3 => redis::ProtocolVersion::RESP3,
            _ => panic!(),
        };
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    connection_info: Some(RedisConnectionInfo {
                        protocol: protocol_enum,
                        ..Default::default()
                    }),
                    protocol: match protocol {
                        2 => ProtocolVersion::RESP2,
                        3 => ProtocolVersion::RESP3,
                        _ => panic!(),
                    },
                    ..Default::default()
                },
            )
            .await;
            let hello: std::collections::HashMap<String, Value> = redis::from_owned_redis_value(
                test_basics
                    .client
                    .send_command(&redis::cmd("HELLO"), None)
                    .await
                    .unwrap(),
            )
            .unwrap();
            assert_eq!(hello.get("proto").unwrap(), &Value::Int(protocol));

            let mut cmd = redis::cmd("HSET");
            cmd.arg("hash").arg("foo").arg("baz");
            test_basics.client.send_command(&cmd, None).await.unwrap();
            let mut cmd = redis::cmd("HSET");
            cmd.arg("hash").arg("bar").arg("foobar");
            test_basics.client.send_command(&cmd, None).await.unwrap();

            let mut cmd = redis::cmd("HGETALL");
            cmd.arg("hash");
            let result = test_basics.client.send_command(&cmd, None).await.unwrap();

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
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_client_handle_concurrent_workload_without_dropping_or_changing_values(
        #[values(false, true)] use_tls: bool,
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls,
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            const NUMBER_OF_CONCURRENT_OPERATIONS: usize = 1000;

            let mut actions = Vec::with_capacity(NUMBER_OF_CONCURRENT_OPERATIONS);
            for index in 0..NUMBER_OF_CONCURRENT_OPERATIONS {
                actions.push(send_set_and_get(
                    test_basics.client.clone(),
                    format!("key{index}"),
                ));
            }
            futures::future::join_all(actions).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_report_closing_when_server_closes(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(10000000),
                    ..Default::default()
                },
            )
            .await;
            let server = test_basics.server;
            drop(server);

            let get_result = send_get(&mut test_basics.client, "foobar")
                .await
                .unwrap_err();
            assert!(
                get_result.is_connection_dropped()
                    || get_result.kind() == redis::ErrorKind::ConnectionNotFoundForRoute
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_authenticate_with_password(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_authenticate_with_password_and_username(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    use_tls: true,
                    connection_info: Some(redis::RedisConnectionInfo {
                        password: Some("ReallySecurePassword".to_string()),
                        username: Some("AuthorizedUsername".to_string()),
                        ..Default::default()
                    }),
                    ..Default::default()
                },
            )
            .await;
            let key = generate_random_string(6);
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[cfg(feature = "iam_tests")]
    fn remove_test_credentials() {
        // Clear any existing AWS credentials
        unsafe {
            std::env::remove_var("AWS_ACCESS_KEY_ID");
            std::env::remove_var("AWS_SECRET_ACCESS_KEY");
            std::env::remove_var("AWS_SESSION_TOKEN");
            std::env::remove_var("AWS_PROFILE");
            std::env::remove_var("AWS_SHARED_CREDENTIALS_FILE");
            std::env::remove_var("AWS_CONFIG_FILE");
        }
    }

    #[cfg(feature = "iam_tests")]
    /// Helper function to create connection request with IAM authentication
    fn create_iam_connection_request(
        addresses: &[redis::ConnectionAddr],
        cluster_name: &str,
        username: &str,
        region: &str,
        refresh_interval_seconds: Option<u32>,
        cluster_mode: bool,
        service_type: ServiceType,
    ) -> glide_core::connection_request::ConnectionRequest {
        let addresses_info = addresses.iter().map(get_address_info).collect();

        let iam_credentials = IamCredentials {
            cluster_name: cluster_name.into(),
            region: region.into(),
            service_type: service_type.into(),
            refresh_interval_seconds,
            ..Default::default()
        };

        let auth_info = AuthenticationInfo {
            password: String::new().into(), // Empty password when using IAM
            username: username.into(),
            iam_credentials: protobuf::MessageField::some(iam_credentials),
            ..Default::default()
        };

        glide_core::connection_request::ConnectionRequest {
            addresses: addresses_info,
            tls_mode: TlsMode::SecureTls.into(),
            cluster_mode_enabled: cluster_mode,
            request_timeout: 10000, // 10 seconds
            authentication_info: protobuf::MessageField::some(auth_info),
            ..Default::default()
        }
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_authentication_elasticache_cluster() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint: &'static str = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // If the client is successfully created, try sending a command
                    let result = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(result.is_ok(), "PING command should succeed: {result:?}");
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_authentication_elasticache_standalone() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-standalone"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = ELASTICACHE_STANDALONE_IAM_ENDPOINT; // Replace with your standalone endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None,  // Use default refresh interval
                false, // standalone mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // If the client is successfully created, try sending a command
                    let result = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(result.is_ok(), "PING command should succeed: {result:?}");
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_authentication_memorydb_cluster() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth-test"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = MEMORYDB_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::MEMORYDB,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // If the client is successfully created, try sending a command
                    let result = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(result.is_ok(), "PING command should succeed: {result:?}");
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_lazy_connection_establishes_on_first_command_with_cluster() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request with lazy connection enabled
            let mut connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Enable lazy connection
            connection_request.lazy_connect = true;

            // Create client with lazy connection
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // At this point, the client should be created but not yet connected
                    // The connection should be established on the first command

                    // Send the first command - this should trigger the connection establishment
                    let result = client.send_command(&redis::cmd("PING"), None).await;

                    match result {
                        Ok(value) => {
                            // Verify that PING returned the expected response
                            assert_eq!(value, Value::SimpleString("PONG".to_string()));

                            // Send another command to verify the connection is established and working
                            let key = generate_random_string(6);
                            let set_result = client
                                .send_command(redis::cmd("SET").arg(&key).arg("test_value"), None)
                                .await;
                            assert!(
                                set_result.is_ok(),
                                "SET command should succeed: {set_result:?}"
                            );

                            let get_result =
                                client.send_command(redis::cmd("GET").arg(&key), None).await;
                            assert_eq!(
                                get_result.unwrap(),
                                Value::BulkString("test_value".as_bytes().to_vec())
                            );
                        }
                        Err(err) => {
                            let error_msg = err.to_string();
                            // If DNS lookup failed, provide a clearer message
                            if error_msg.contains("failed to lookup address")
                                || error_msg.contains("Name or service not known")
                            {
                                // Uncomment this when you have a real AWS environment
                                panic!(
                                    "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                                    endpoint, error_msg
                                );
                            }

                            // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                            // Uncomment this when you have a real AWS environment
                            panic!(
                                "Failed to execute first command with IAM authentication on lazy cluster connection: {}",
                                error_msg
                            );
                        }
                    }
                }
                Err(err) => {
                    // Client creation should succeed even with lazy connection
                    panic!(
                        "Failed to create lazy cluster client with IAM authentication: {}",
                        err
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_lazy_connection_establishes_on_first_command_with_standalone() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-standalone"; // Replace with your standalone cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = ELASTICACHE_STANDALONE_IAM_ENDPOINT; // Replace with your standalone endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request with lazy connection enabled
            let mut connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None,  // Use default refresh interval
                false, // standalone mode
                ServiceType::ELASTICACHE,
            );

            // Enable lazy connection
            connection_request.lazy_connect = true;

            // Create client with lazy connection
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // At this point, the client should be created but not yet connected
                    // The connection should be established on the first command

                    // Send the first command - this should trigger the connection establishment
                    let result = client.send_command(&redis::cmd("PING"), None).await;

                    match result {
                        Ok(value) => {
                            // Verify that PING returned the expected response
                            assert_eq!(value, Value::SimpleString("PONG".to_string()));

                            // Send another command to verify the connection is established and working
                            let key = generate_random_string(6);
                            let set_result = client
                                .send_command(redis::cmd("SET").arg(&key).arg("test_value"), None)
                                .await;
                            assert!(
                                set_result.is_ok(),
                                "SET command should succeed: {set_result:?}"
                            );

                            let get_result =
                                client.send_command(redis::cmd("GET").arg(&key), None).await;
                            assert_eq!(
                                get_result.unwrap(),
                                Value::BulkString("test_value".as_bytes().to_vec())
                            );
                        }
                        Err(err) => {
                            let error_msg = err.to_string();
                            // If DNS lookup failed, provide a clearer message
                            if error_msg.contains("failed to lookup address")
                                || error_msg.contains("Name or service not known")
                            {
                                // Uncomment this when you have a real AWS environment
                                panic!(
                                    "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                                    endpoint, error_msg
                                );
                            }

                            // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                            // Uncomment this when you have a real AWS environment
                            panic!(
                                "Failed to execute first command with IAM authentication on lazy standalone connection: {}",
                                error_msg
                            );
                        }
                    }
                }
                Err(err) => {
                    // Client creation should succeed even with lazy connection
                    panic!(
                        "Failed to create lazy standalone client with IAM authentication: {}",
                        err
                    );
                }
            }
        });
    }

    /**
     * Ensures the client **reconnects** to an ElastiCache cluster with **IAM auth** after its
     * connection is dropped.
     *
     * Force a **failure** (negative case):
     * - Set `refresh_interval_seconds` **> 900**.
     * - Sleep **> 900s and < refresh_interval_seconds** **before** killing the connection.
     *   The token will be expired and reconnect should fail.
     */
    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_iam_cluster_reconnection_after_connection_kill() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                true, // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection with PING
                    let initial_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        initial_ping.is_ok(),
                        "Initial PING should succeed: {initial_ping:?}"
                    );

                    // Set a test key-value pair to verify functionality
                    let test_key = generate_random_string(10);
                    let test_value = generate_random_string(10);

                    let set_result = client
                        .send_command(redis::cmd("SET").arg(&test_key).arg(&test_value), None)
                        .await;
                    assert!(
                        set_result.is_ok(),
                        "SET command should succeed: {set_result:?}"
                    );

                    // Verify the value was set correctly
                    let get_result = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert_eq!(
                        get_result.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec()),
                        "GET should return the set value"
                    );

                    // Wait a moment for the connection validation task to detect and reconnect
                    tokio::time::sleep(std::time::Duration::from_secs(2)).await;

                    // Kill all connections to simulate network interruption
                    kill_connection(&mut client).await;

                    // Test that the client can reconnect and function properly after connection kill
                    let reconnect_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        reconnect_ping.is_ok(),
                        "PING after reconnection should succeed: {reconnect_ping:?}"
                    );

                    // Verify that we can still retrieve the previously set value after reconnection
                    let get_after_reconnect = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert_eq!(
                        get_after_reconnect.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec()),
                        "GET after reconnection should return the same value"
                    );
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    fn test_iam_refresh_token(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = if use_cluster {
                "iam-auth-test"
            } else {
                "iam-auth-standalone"
            };
            let username = "iam-auth";
            let region = "us-east-1";
            let endpoint = if use_cluster {
                ELASTICACHE_CLUSTER_IAM_ENDPOINT
            } else {
                ELASTICACHE_STANDALONE_IAM_ENDPOINT
            };

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                None, // Use default refresh interval
                use_cluster,
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection with PING
                    let initial_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        initial_ping.is_ok(),
                        "Initial PING should succeed: {initial_ping:?}"
                    );

                    // Test manual IAM token refresh
                    let refresh_result = client.refresh_iam_token().await;
                    assert!(
                        refresh_result.is_ok(),
                        "IAM token refresh should succeed: {refresh_result:?}"
                    );

                    // Verify that the client still works after token refresh
                    let post_refresh_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        post_refresh_ping.is_ok(),
                        "PING after token refresh should succeed: {post_refresh_ping:?}"
                    );

                    // Test multiple consecutive refreshes
                    for i in 1..=3 {
                        let refresh_result = client.refresh_iam_token().await;
                        assert!(
                            refresh_result.is_ok(),
                            "IAM token refresh #{i} should succeed: {refresh_result:?}"
                        );

                        // Verify client still works after each refresh
                        let ping_result = client.send_command(&redis::cmd("PING"), None).await;
                        assert!(
                            ping_result.is_ok(),
                            "PING after refresh #{i} should succeed: {ping_result:?}"
                        );
                    }
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    fn test_iam_cluster_refresh_token_after_connection_kill_and_token_expired() {
        // Change the sleep duration inside the test to be > 900 if you want the token to be expired before reconnect
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                // None,
                Some(3600), // Use default refresh interval
                true,       // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection with PING
                    let initial_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        initial_ping.is_ok(),
                        "Initial PING should succeed: {initial_ping:?}"
                    );

                    // Set a test key-value pair to verify functionality
                    let test_key = generate_random_string(10);
                    let test_value = generate_random_string(10);

                    let set_result = client
                        .send_command(redis::cmd("SET").arg(&test_key).arg(&test_value), None)
                        .await;
                    assert!(
                        set_result.is_ok(),
                        "SET command should succeed: {set_result:?}"
                    );

                    // Verify the value was set correctly
                    let get_result = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert_eq!(
                        get_result.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec()),
                        "GET should return the set value"
                    );

                    // Change to 910 if you want the token to be expired before reconnect
                    tokio::time::sleep(std::time::Duration::from_secs(2)).await;

                    // generate a new token before reconnect
                    client.refresh_iam_token().await.unwrap();

                    // Kill all connections to simulate network interruption
                    kill_connection(&mut client).await;

                    // Test that the client can reconnect and function properly after connection kill
                    let reconnect_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        reconnect_ping.is_ok(),
                        "PING after reconnection should succeed: {reconnect_ping:?}"
                    );

                    // Verify that we can still retrieve the previously set value after reconnection
                    let get_after_reconnect = client
                        .send_command(redis::cmd("GET").arg(&test_key), None)
                        .await;
                    assert_eq!(
                        get_after_reconnect.unwrap(),
                        Value::BulkString(test_value.as_bytes().to_vec()),
                        "GET after reconnection should return the same value"
                    );
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    /**
     * Ensures the client **reconnects** to an ElastiCache cluster with **IAM auth** after a
     * **node failover**.
     * Install aws sdk for running the test.
     * Follow the steps inside the test to trigger a node failover, while keeping the token expired.
     */
    #[cfg(feature = "iam_tests")]
    #[rstest]
    #[serial_test::serial]
    fn test_iam_cluster_reconnection_after_node_kill() {
        block_on_all(async {
            remove_test_credentials();

            let cluster_name = "iam-auth-test"; // Replace with your ElastiCache cluster name
            let username = "iam-auth"; // Replace with your IAM username
            let region = "us-east-1";
            let endpoint = ELASTICACHE_CLUSTER_IAM_ENDPOINT; // Replace with your cluster endpoint

            // Use the provided endpoint and port
            let address = redis::ConnectionAddr::Tcp(endpoint.to_string(), 6379);

            // Create IAM connection request
            let connection_request = create_iam_connection_request(
                &[address],
                cluster_name,
                username,
                region,
                Some(2400), // 40 min
                true,       // cluster mode
                ServiceType::ELASTICACHE,
            );

            // Attempt to create client with IAM authentication
            let client_result = Client::new(connection_request.into(), None).await;

            match client_result {
                Ok(mut client) => {
                    // Test initial connection with PING

                    use logger_core::log_info;
                    let initial_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        initial_ping.is_ok(),
                        "Initial PING should succeed: {initial_ping:?}"
                    );

                    // Change to 900
                    // wait enough for the token to be expired
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;
                    log_info("Initial token is expired", "");

                    // run this script in the terminal to trigger a node failover after 900 seconds
                    //  aws elasticache test-failover \
                    //   --replication-group-id iam-auth-test \
                    //   --node-group-id 0001 \
                    //   --region us-east-1

                    // Change to 400
                    // Wait for the cluster and the client to stabilize
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;

                    // Test manual IAM token refresh
                    let refresh_result = client.refresh_iam_token().await;
                    assert!(
                        refresh_result.is_ok(),
                        "IAM token refresh should succeed: {refresh_result:?}"
                    );

                    // Verify that the client still works after token refresh
                    let post_refresh_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        post_refresh_ping.is_ok(),
                        "PING after token refresh should succeed: {post_refresh_ping:?}"
                    );

                    // Change to 900
                    // wait enough again for the token to be expired
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;
                    log_info("Refresh token is expired", "");

                    // run this script in the terminal to trigger a node failover after 900 seconds
                    //  aws elasticache test-failover \
                    //   --replication-group-id iam-auth-cluster \
                    //   --node-group-id 0001 \
                    //   --region us-east-1

                    // Change to 400
                    // When failovering the client doesn't have a valid token, but
                    // when when time interval hit the 2400 seconds, it will generate a new token and reconnect
                    // Follow the logs to verify that the client reconnected successfully.
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;

                    // Verify that the client still works after token refresh
                    let post_refresh_ping = client.send_command(&redis::cmd("PING"), None).await;
                    assert!(
                        post_refresh_ping.is_ok(),
                        "PING after token refresh should succeed: {post_refresh_ping:?}"
                    );
                }
                Err(err) => {
                    // In case of failure, print error and assert that it is not a non-connection/auth error
                    let error_msg = err.to_string();
                    // If DNS lookup failed, provide a clearer message
                    if error_msg.contains("failed to lookup address")
                        || error_msg.contains("Name or service not known")
                    {
                        // Uncomment this when you have a real AWS environment
                        panic!(
                            "DNS lookup failed: Unable to resolve the address `{}`. Please verify that the endpoint is correct and accessible from your environment.\nError: {}",
                            endpoint, error_msg
                        );
                    }

                    // Other errors will fall here, indicating problems with IAM token generation or connection/auth
                    // Uncomment this when you have a real AWS environment
                    panic!(
                        "Failed to create client with IAM authentication: {}",
                        error_msg
                    );
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1), // milliseconds
                    shared_server: false,
                    ..Default::default()
                },
            )
            .await;
            let mut cmd = redis::Cmd::new();
            // Create a long running command to ensure we get into timeout
            cmd.arg("EVAL")
                .arg(
                    r#"
                    while (true)
                    do
                    redis.call('ping')
                    end
                "#,
                )
                .arg("0");
            let result = test_basics.client.send_command(&cmd, None).await;
            assert!(result.is_err());
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "{err}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_doesnt_raise_timeout_error(#[values(false, true)] use_cluster: bool) {
        // We test that the request timeout is based on the value specified in the blocking command argument,
        // and not on the one set in the client configuration. To achieve this, we execute a command designed to
        // be blocked until it reaches the specified command timeout. We set the client's request timeout to
        // a shorter duration than the blocking command's timeout. Subsequently, we confirm that we receive
        // a response from the server instead of encountering a timeout error.
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1), // milliseconds
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut cmd = redis::Cmd::new();
            cmd.arg("BLPOP").arg(generate_random_string(10)).arg(0.3); // server should return null after 300 millisecond
            let result = test_basics.client.send_command(&cmd, None).await;
            assert!(result.is_ok());
            assert_eq!(result.unwrap(), Value::Nil);
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_with_negative_timeout_returns_error(
        #[values(false, true)] use_cluster: bool,
    ) {
        // We test that when blocking command is passed with a negative timeout the command will return with an error
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1), // milliseconds
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;
            let mut cmd = redis::Cmd::new();
            cmd.arg("BLPOP").arg(generate_random_string(10)).arg(-1);
            let result = test_basics.client.send_command(&cmd, None).await;
            assert!(result.is_err());
            let err = result.unwrap_err();
            assert_eq!(err.kind(), redis::ErrorKind::ResponseError);
            assert!(err.to_string().contains("negative"));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_with_zero_timeout_blocks_indefinitely(
        #[values(false, true)] use_cluster: bool,
    ) {
        // We test that when a blocking command is passed with a timeout duration of 0, it will block the client indefinitely
        block_on_all(async {
            let config = TestConfiguration {
                request_timeout: Some(1), // millisecond
                shared_server: true,
                ..Default::default()
            };
            let mut test_basics = setup_test_basics(use_cluster, config).await;
            let key = generate_random_string(10);
            let future = async move {
                let mut cmd = redis::Cmd::new();
                cmd.arg("BLPOP").arg(key).arg(0); // `0` should block indefinitely
                test_basics.client.send_command(&cmd, None).await
            };
            // We execute the command with Tokio's timeout wrapper to prevent the test from hanging indefinitely.
            let tokio_timeout_result =
                tokio::time::timeout(DEFAULT_RESPONSE_TIMEOUT * 2, future).await;
            assert!(tokio_timeout_result.is_err());
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_transaction_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    request_timeout: Some(1),
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // BLPOP doesn't block in transactions, so we'll pause the client instead.
            let mut cmd = redis::cmd("CLIENT");
            cmd.arg("PAUSE").arg(100);
            let _ = test_basics
                .client
                .send_command(
                    &cmd,
                    Some(RoutingInfo::MultiNode((
                        MultipleNodeRoutingInfo::AllNodes,
                        None,
                    ))),
                )
                .await;

            let mut pipeline = redis::pipe();
            pipeline.atomic();
            pipeline.cmd("GET").arg("foo");
            let result = test_basics
                .client
                .send_transaction(&pipeline, None, None, false)
                .await;
            assert!(result.is_err(), "Received {result:?}");
            let err = result.unwrap_err();
            assert!(err.is_timeout(), "{err}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_client_name_after_reconnection(#[values(false, true)] use_cluster: bool) {
        const CLIENT_NAME: &str = "TEST_CLIENT_NAME";
        let mut client_info_cmd = redis::Cmd::new();
        client_info_cmd.arg("CLIENT").arg("INFO");
        block_on_all(async move {
            let test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    client_name: Some(CLIENT_NAME.to_string()),
                    ..Default::default()
                },
            )
            .await;

            for i in 0..2 {
                // ensure all connections have CLIENT_NAME set
                let mut client = test_basics.client.clone();
                let client_infos: HashMap<String, String> = {
                    let variant_res = client
                        .send_command(
                            &client_info_cmd,
                            Some(RoutingInfo::MultiNode((
                                MultipleNodeRoutingInfo::AllNodes,
                                None,
                            ))),
                        )
                        .await
                        .unwrap();

                    if use_cluster {
                        redis::from_owned_redis_value(variant_res).unwrap()
                    } else {
                        [(
                            "DONT_CARE".to_string(),
                            redis::from_owned_redis_value(variant_res).unwrap(),
                        )]
                        .into()
                    }
                };

                for client_info in client_infos.values() {
                    assert!(client_info.contains(&format!("name={CLIENT_NAME}")));
                }

                if i == 0 {
                    // first pass - kill the connections
                    kill_connection(&mut client).await;
                    // short sleep to allow the connection validation task to reconnect - 1s is enough since the detection should happen immediately
                    tokio::time::sleep(std::time::Duration::from_secs(1)).await;
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_resp_support(
        #[values(false, true)] use_cluster: bool,
        #[values(2, 3)] protocol: i64,
    ) {
        let protocol_enum = match protocol {
            2 => redis::ProtocolVersion::RESP2,
            3 => redis::ProtocolVersion::RESP3,
            _ => panic!(),
        };
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    connection_info: Some(RedisConnectionInfo {
                        protocol: protocol_enum,
                        ..Default::default()
                    }),
                    protocol: match protocol {
                        2 => ProtocolVersion::RESP2,
                        3 => ProtocolVersion::RESP3,
                        _ => panic!(),
                    },
                    ..Default::default()
                },
            )
            .await;
            let hello: std::collections::HashMap<String, Value> = redis::from_owned_redis_value(
                test_basics
                    .client
                    .send_command(&redis::cmd("HELLO"), None)
                    .await
                    .expect("HELLO failed"),
            )
            .unwrap();
            assert_eq!(hello.get("proto").unwrap(), &Value::Int(protocol));

            let (key, field, value, field2, value2) = (
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
            );
            let mut pipeline = Pipeline::new();
            pipeline.hset(&key, &field, &value);
            pipeline.hset(&key, &field2, &value2);
            pipeline.hgetall(&key);

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");
            assert_eq!(
                result,
                Value::Array(vec![
                    Value::Int(1),
                    Value::Int(1),
                    Value::Map(vec![
                        (
                            Value::BulkString(field.as_bytes().to_vec()),
                            Value::BulkString(value.as_bytes().to_vec())
                        ),
                        (
                            Value::BulkString(field2.as_bytes().to_vec()),
                            Value::BulkString(value2.as_bytes().to_vec())
                        )
                    ])
                ]),
                "Pipeline result: {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_return_error(
        #[values(false, true)] use_cluster: bool,
        #[values(false, true)] raise_error: bool,
    ) {
        use redis::ErrorKind;

        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, value, key2) = (
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
            );

            let mut pipeline = Pipeline::new();
            pipeline.set(&key, &value).get(&key).llen(&key).get(&key2);

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    raise_error,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await;

            match raise_error {
                false => {
                    let res = match res {
                        Ok(Value::Array(arr)) => arr,
                        _ => panic!("Expected an array response, got: {res:?}"),
                    };
                    assert_eq!(
                        &res[..2],
                        &[Value::Okay, Value::BulkString(value.as_bytes().to_vec()),],
                        "Pipeline result: {res:?}"
                    );

                    assert!(
                        matches!(res[2], Value::ServerError(ref err) if err.err_code().contains("WRONGTYPE"))
                    );
                }
                true => {
                    assert!(res.is_err(), "Pipeline should fail with wrong type error");
                    let err = res.unwrap_err();
                    assert_eq!(
                        err.kind(),
                        ErrorKind::ExtensionError,
                        "Pipeline should fail with response error"
                    );
                    assert!(err.to_string().contains("WRONGTYPE"), "{err:?}");
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_with_blocking_command_returns_null(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(3000), // allow enough time for the BLPOP to timeout
                    ..Default::default()
                },
            )
            .await;

            // Generate two random keys.
            let key = generate_random_string(10);
            let key2 = generate_random_string(10);

            // Build a pipeline:
            // 1. SET key to "value1"
            // 2. GET key (should return "value1")
            // 3. BLPOP key2 with a 2-second timeout (should return null since key2 is empty)
            // 4. GET key2 (should return null)
            let mut pipeline = Pipeline::new();
            pipeline.set(&key, "value1");
            pipeline.get(&key);
            pipeline.blpop(&key2, 1.0);
            pipeline.get(&key2);

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");

            // Expected results:
            // - SET returns OK
            // - GET returns "value1"
            // - BLPOP returns null (because of timeout)
            // - GET returns null (key2 was never set)
            assert_eq!(
                result,
                Value::Array(vec![
                    Value::Okay,
                    Value::BulkString(b"value1".to_vec()),
                    Value::Nil,
                    Value::Nil,
                ]),
                "Pipeline with blocking command should return null for BLPOP and GET on a non-existent key {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_blocking_command_inside_pipeline_raises_timeout_error(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(1000),
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, key2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, "value1")
                .get(&key)
                .blpop(&key2, 2.0)
                .get(&key2);

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await;
            assert!(
                res.is_err(),
                "Pipeline should fail with blocking command taking too long"
            );
            let err = res.unwrap_err();
            assert!(err.is_timeout(), "Pipeline should fail with timeout error");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(1000),
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, key2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, "value1")
                .get(&key)
                .blpop(&key2, 2.0)
                .get(&key2);

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    Some(3000),
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");

            assert_eq!(
                res,
                Value::Array(vec![
                    Value::Okay,
                    Value::BulkString(b"value1".to_vec()),
                    Value::Nil,
                    Value::Nil,
                ]),
                "Pipeline result: {res:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_transaction_timeout(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    request_timeout: Some(1000),
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let key = generate_random_string(10);

            let mut transaction = redis::pipe();
            transaction.atomic();
            transaction.blpop(&key, 2.0).get(&key);

            let res = test_basics
                .client
                .send_transaction(&transaction, None, Some(3000), true)
                .await
                .expect("Transaction failed");

            assert_eq!(
                res,
                Value::Array(vec![Value::Nil, Value::Nil,]),
                "Transaction result: {res:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_can_reconnect(#[values(false, true)] use_cluster: bool) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let (key, value) = (generate_random_string(10), generate_random_string(10));
            let (key2, value2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, &value)
                .get(&key)
                .set(&key2, &value2)
                .get(&key2);

            // kill the connection for `key2`
            kill_connection_for_route(
                &mut test_basics.client,
                RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                    get_slot(key2.as_bytes()),
                    SlotAddr::Master,
                ))),
            )
            .await;

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");
            assert_eq!(
                res,
                Value::Array(vec![
                    Value::Okay,
                    Value::BulkString(value.as_bytes().to_vec()),
                    Value::Okay,
                    Value::BulkString(value2.as_bytes().to_vec()),
                ]),
                "Pipeline result: {res:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_parallel_pipeline_and_kill_connection(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let key = generate_random_string(10);
            let configuration = TestConfiguration {
                shared_server: true,
                request_timeout: Some(3000),
                ..Default::default()
            };
            let mut test_basics = setup_test_basics(use_cluster, configuration.clone()).await;

            let mut client_for_kill = match test_basics.server {
                BackingServer::Cluster(cluster) => {
                    create_cluster_client(cluster.as_ref(), configuration).await
                }
                BackingServer::Standalone(standalone) => {
                    create_client(&BackingServer::Standalone(standalone), configuration).await
                }
            };

            let pipeline_future = async {
                let mut pipeline = Pipeline::new();
                pipeline.blpop(&key, 2.0);
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                pipeline.lpush(&key, "value");
                test_basics
                    .client
                    .send_pipeline(
                        &pipeline,
                        None,
                        false,
                        None,
                        PipelineRetryStrategy {
                            retry_server_error: true,
                            retry_connection_error: false,
                        },
                    )
                    .await
            };

            let kill_future = async {
                tokio::time::sleep(std::time::Duration::from_millis(50)).await;

                kill_connection_for_route(
                    &mut client_for_kill,
                    RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(Route::new(
                        get_slot(key.as_bytes()),
                        SlotAddr::Master,
                    ))),
                )
                .await;
            };

            // Run both tasks concurrently.
            let (pipeline_result, _) = tokio::join!(pipeline_future, kill_future);

            match use_cluster {
                true => {
                    assert!(
                        pipeline_result.is_ok(),
                        "Pipeline failed: {pipeline_result:?}"
                    );
                    let result = match pipeline_result.unwrap() {
                        Value::Array(res) => res,
                        _ => panic!("Expected an array of values"),
                    };

                    assert!(
                        result.iter().all(|r| matches!(r, Value::ServerError(e) if e.err_code().contains("BrokenPipe"))
                            ),
                        "Not all responses are ServerError: {result:?}"
                    );
                }
                false => {
                    assert!(
                        pipeline_result.is_err(),
                        "Pipeline should have failed: {pipeline_result:?}"
                    );
                    assert!(
                        pipeline_result
                            .unwrap_err()
                            .to_string()
                            .contains("FatalReceiveError"),
                    );
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_reconnect_after_kill_all_connections(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            // Generate random keys.
            let (key, key2) = (generate_random_string(10), generate_random_string(10));

            let mut pipeline = Pipeline::new();
            pipeline
                .set(&key, "value1")
                .get(&key)
                .set(&key2, "value2")
                .get(&key2);

            // Kill all connections.
            kill_connection(&mut test_basics.client).await;

            let res = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed after killing all connections");

            assert_eq!(
                res,
                Value::Array(vec![
                    Value::Okay,
                    Value::BulkString("value1".as_bytes().to_vec()),
                    Value::Okay,
                    Value::BulkString("value2".as_bytes().to_vec()),
                ]),
                "Pipeline result: {res:?}"
            );
        });
    }
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_empty_pipeline(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let pipeline = Pipeline::new();
            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await;

            assert!(result.is_err(), "Pipeline should fail with empty pipeline");
        });
    }
    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_multi_slot_routing(#[values(true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let keys = vec![
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
                generate_random_string(10),
            ];

            let items = keys.iter().map(|key| (key, key)).collect::<Vec<_>>();
            let expected = keys
                .iter()
                .map(|key| Value::BulkString(key.as_bytes().to_vec()))
                .collect::<Vec<_>>();
            let mut pipeline = Pipeline::new();
            pipeline.mset(&items);
            pipeline.mget(&keys);

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");
            assert_eq!(
                result,
                Value::Array(vec![Value::Okay, Value::Array(expected)]),
                "Pipeline result: {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_all_nodes_routing(#[values(true, false)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut config_set_cmd = cmd("CONFIG");
            config_set_cmd.arg("SET").arg("appendonly").arg("no");
            let mut config_get_cmd = cmd("CONFIG");
            config_get_cmd.arg("GET").arg("appendonly");
            let mut pipeline = Pipeline::new();
            pipeline.add_command(config_set_cmd); // AllNodes cmd
            pipeline.add_command(config_get_cmd); // RandomNode cmd

            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline failed");

            assert_eq!(
                result,
                Value::Array(vec![
                    Value::Okay,
                    Value::Map(vec![(
                        Value::BulkString(b"appendonly".to_vec()),
                        Value::BulkString(b"no".to_vec()),
                    )])
                ]),
                "Pipeline result: {result:?}"
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_all_primary_routing(#[values(true, false)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut pipeline = Pipeline::new();
            pipeline.add_command(cmd("PING"));
            pipeline.set(generate_random_string(10), "value");
            pipeline.add_command(cmd("FLUSHALL"));
            pipeline.add_command(cmd("DBSIZE")); // AllPrimary cmd + SUM aggregation

            // Execute the pipeline.
            let result = test_basics
                .client
                .send_pipeline(
                    &pipeline,
                    None,
                    false,
                    None,
                    PipelineRetryStrategy {
                        retry_server_error: true,
                        retry_connection_error: false,
                    },
                )
                .await
                .expect("Pipeline execution failed");

            let expected = Value::Array(vec![
                Value::SimpleString("PONG".to_string()),
                Value::Okay,
                Value::Okay,
                Value::Int(0),
            ]);

            assert_eq!(result, expected, "Pipeline result: {result:?}");
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_pipeline_failed_commands_skip_conversion(
        #[values(false, true)] use_cluster: bool,
        #[values(false, true)] atomic: bool,
    ) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut pipeline = Pipeline::new();
            if atomic {
                pipeline.atomic();
            }
            pipeline.set("key", "value");
            pipeline.hgetall("key");

            let result = if atomic {
                test_basics
                    .client
                    .send_transaction(&pipeline, None, None, false)
                    .await
                    .expect("Transaction failed")
            } else {
                test_basics
                    .client
                    .send_pipeline(
                        &pipeline,
                        None,
                        false,
                        None,
                        PipelineRetryStrategy {
                            retry_server_error: false,
                            retry_connection_error: false,
                        },
                    )
                    .await
                    .expect("Pipeline failed")
            };

            let arr = match result {
                Value::Array(ref arr) => arr,
                _ => panic!("Expected array result, got: {result:?}"),
            };

            assert_eq!(arr[0], Value::Okay, "Pipeline result: {arr:?}");
            assert!(
                matches!(&arr[1], Value::ServerError(err) if err.err_code().contains("WRONGTYPE")),
                "Pipeline result: {arr:?}"
            );
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_client_telemetry_standalone() {
        Telemetry::reset();
        block_on_all(async move {
            // create a server with 2 clients
            let server_config = TestConfiguration {
                use_tls: false,
                ..Default::default()
            };

            let test_basics = utilities::setup_test_basics_internal(&server_config).await;
            let server = BackingServer::Standalone(test_basics.server);

            // setup_test_basics_internal internally, starts a single client connection
            assert_eq!(Telemetry::total_connections(), 1);
            assert_eq!(Telemetry::total_clients(), 1);

            {
                // Create 2 more clients, confirm that they are tracked
                let _client1 = create_client(&server, server_config.clone()).await;
                let _client2 = create_client(&server, server_config).await;

                // Each client maintains a single connection
                assert_eq!(Telemetry::total_connections(), 3);
                assert_eq!(Telemetry::total_clients(), 3);

                // Connections are dropped here
            }

            // Confirm 1 connection & client remain
            assert_eq!(Telemetry::total_connections(), 1);
            assert_eq!(Telemetry::total_clients(), 1);
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_client_telemetry_cluster() {
        Telemetry::reset();
        block_on_all(async {
            let local_set = tokio::task::LocalSet::default();
            let (tx, mut rx) = tokio::sync::mpsc::channel(1);
            // We use 2 tasks to let "dispose" be called. In addition, the task that checks for the cleanup
            // does not start until the cluster is up and running. We use a channel to communicate this between
            // the tasks
            local_set.spawn_local(async move {
                let cluster = cluster::setup_default_cluster().await;
                async_assert_eq!(Telemetry::total_connections(), 0);
                async_assert_eq!(Telemetry::total_clients(), 0);

                // Each client opens 12 connections
                println!("Creating 1st cluster client...");
                let _c1 = cluster::setup_default_client(&cluster).await;
                async_assert_eq!(Telemetry::total_connections(), 12);
                async_assert_eq!(Telemetry::total_clients(), 1);

                println!("Creating 2nd cluster client...");
                let _c2 = cluster::setup_default_client(&cluster).await;
                async_assert_eq!(Telemetry::total_connections(), 24);
                async_assert_eq!(Telemetry::total_clients(), 2);

                let _ = tx.send(1).await;
                // client is dropped and eventually disposed here
            });

            local_set.spawn_local(async move {
                let _ = rx.recv().await;
                println!("Cluster terminated. Wait for the telemetry to clear");
                tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
                assert_eq!(Telemetry::total_connections(), 0);
                assert_eq!(Telemetry::total_clients(), 0);
            });
            local_set.await;
        });
    }

    #[test]
    #[serial_test::serial]
    fn test_multi_key_no_args_in_cluster() {
        block_on_all(async {
            let cluster = cluster::setup_default_cluster().await;
            println!("Creating 1st cluster client...");
            let mut c1 = cluster::setup_default_client(&cluster).await;
            let result = c1.send_command(&redis::cmd("MSET"), None).await;
            assert!(result.is_err());
            let e = result.unwrap_err();
            assert!(e.kind().clone().eq(&redis::ErrorKind::ResponseError));
            assert!(e.to_string().contains("wrong number of arguments"));
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_request_transaction_and_convert_all_values(#[values(false, true)] use_cluster: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    shared_server: true,
                    protocol: glide_core::connection_request::ProtocolVersion::RESP2,
                    ..Default::default()
                },
            )
            .await;

            let key = generate_random_string(10);
            let mut pipeline = redis::pipe();
            pipeline.atomic();
            pipeline.hset(&key, "bar", "vaz");
            pipeline.hgetall(&key);
            pipeline.hexists(&key, "bar");
            pipeline.del(&key);
            pipeline.set(&key, "0");
            pipeline.cmd("INCRBYFLOAT").arg(&key).arg("0.5");
            pipeline.del(&key);

            let result = test_basics
                .client
                .send_transaction(&pipeline, None, None, false)
                .await;
            assert_eq!(
                result,
                Ok(Value::Array(vec![
                    Value::Int(1),
                    Value::Map(vec![(
                        Value::BulkString(b"bar".to_vec()),
                        Value::BulkString(b"vaz".to_vec()),
                    )]),
                    Value::Boolean(true),
                    Value::Int(1),
                    Value::Okay,
                    Value::Double(0.5),
                    Value::Int(1),
                ]),)
            );
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies the client maintains the correct database ID after an automatic reconnection
    /// when the database was changed using the SELECT command.
    /// This test:
    /// 1. Creates a client connected to database 0 (default)
    /// 2. Uses SELECT command to change to database 5
    /// 3. Verifies the connection is now on database 5
    /// 4. Simulates a connection drop by killing the connection
    /// 5. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection to db=5
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still on db=5
    /// This ensures that database selection via SELECT command persists across reconnections.
    fn test_select_command_database_persistence_after_reconnection(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    database_id: 0, // Start with default database
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            if use_cluster {
                // Skip test if server version is less than 9.0 (database isolation not supported in cluster)
                if !utilities::version_greater_or_equal(&mut test_basics.client, "9.0.0").await {
                    return;
                }
            }

            let mut client_info_cmd = redis::Cmd::new();
            client_info_cmd.arg("CLIENT").arg("INFO");

            let mut select_cmd = redis::Cmd::new();
            select_cmd.arg("SELECT").arg("5");

            // Verify initial connection is to database 0
            let initial_client_info_response = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await
                .unwrap();

            let initial_client_info = match initial_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    initial_client_info_response
                ),
            };
            assert!(initial_client_info.contains("db=0"));

            // Extract initial client ID
            let initial_client_id = utilities::extract_client_id(&initial_client_info)
                .expect("Failed to extract initial client ID");

            // Execute SELECT command to change to database 5
            let select_result = test_basics
                .client
                .send_command(&select_cmd, None)
                .await
                .unwrap();
            assert_eq!(select_result, Value::Okay);

            // Verify we're now on database 5
            let post_select_client_info_response = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await
                .unwrap();

            let post_select_client_info = match post_select_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    post_select_client_info_response
                ),
            };
            assert!(post_select_client_info.contains("db=5"));

            // Kill the connection to simulate a network drop
            kill_connection(&mut test_basics.client).await;

            // Try to send another command - this should trigger reconnection
            let res = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await;
            match res {
                Err(err) => {
                    // Connection was dropped as expected
                    assert!(
                        err.is_connection_dropped() || err.is_timeout(),
                        "Expected connection dropped or timeout error, got: {err:?}",
                    );
                    // Retry and verify we're still on database 5 after reconnection
                    let client_info = repeat_try_create(|| async {
                        let mut client = test_basics.client.clone();
                        let response = client.send_command(&client_info_cmd, None).await.ok()?;
                        match response {
                            Value::BulkString(bytes) => {
                                Some(String::from_utf8_lossy(&bytes).to_string())
                            }
                            Value::VerbatimString { text, .. } => Some(text),
                            _ => None,
                        }
                    })
                    .await;
                    assert!(client_info.contains("db=5"));
                }
                Ok(response) => {
                    // Command succeeded, extract new client ID and compare
                    let new_client_info = match response {
                        Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                        Value::VerbatimString { text, .. } => text,
                        _ => panic!("Unexpected CLIENT INFO response type: {:?}", response),
                    };
                    let new_client_id = utilities::extract_client_id(&new_client_info)
                        .expect("Failed to extract new client ID");
                    assert_ne!(
                        initial_client_id, new_client_id,
                        "Client ID should change after reconnection if command succeeds"
                    );
                    // Check that the database ID is still 5 (from SELECT command)
                    assert!(new_client_info.contains("db=5"));
                }
            }
        });
    }

    #[rstest]
    #[serial_test::serial]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    /// Test that verifies the client maintains the correct client name after an automatic reconnection
    /// when the client name was changed using the CLIENT SETNAME command.
    /// This test:
    /// 1. Creates a client with a name "1stName"
    /// 2. Uses CLIENT SETNAME command to change to "2ndName"
    /// 3. Verifies the connection is now with the "2ndName"
    /// 4. Simulates a connection drop by killing the connection
    /// 5. Sends another command which either:
    ///    - Fails due to the dropped connection, then retries and verifies reconnection has the name "2ndName" set
    ///    - Succeeds with a new client ID (indicating reconnection) and verifies still has the name "2ndName" set
    /// This ensures that client name via CLIENT SETNAME command persists across reconnections.
    fn test_client_set_name_command_persistence_after_reconnection(
        #[values(false, true)] use_cluster: bool,
    ) {
        block_on_all(async move {
            let mut test_basics = setup_test_basics(
                use_cluster,
                TestConfiguration {
                    client_name: Some("1stName".to_string()),
                    shared_server: true,
                    ..Default::default()
                },
            )
            .await;

            let mut client_info_cmd = redis::Cmd::new();
            client_info_cmd.arg("CLIENT").arg("INFO");

            let mut client_setname_cmd = redis::Cmd::new();
            client_setname_cmd
                .arg("Client")
                .arg("SETNAME")
                .arg("2ndName");

            // Verify initial connection client name
            let initial_client_info_response = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await
                .unwrap();

            let initial_client_info = match initial_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    initial_client_info_response
                ),
            };
            assert!(initial_client_info.contains("name=1stName"));

            // Extract initial client ID
            let initial_client_id = utilities::extract_client_id(&initial_client_info)
                .expect("Failed to extract initial client ID");

            // Execute CLIENT SETNAME command to change to 2ndName
            let client_setname_result = test_basics
                .client
                .send_command(&client_setname_cmd, None)
                .await
                .unwrap();
            assert_eq!(client_setname_result, Value::Okay);

            // Verify we're now on 2ndName
            let post_client_setname_client_info_response = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await
                .unwrap();

            let post_client_setname_client_info = match post_client_setname_client_info_response {
                Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                Value::VerbatimString { text, .. } => text,
                _ => panic!(
                    "Unexpected CLIENT INFO response type: {:?}",
                    post_client_setname_client_info_response
                ),
            };
            assert!(post_client_setname_client_info.contains("name=2ndName"));

            // Kill the connection to simulate a network drop
            kill_connection(&mut test_basics.client).await;

            // Try to send another command - this should trigger reconnection
            let res = test_basics
                .client
                .send_command(&client_info_cmd, None)
                .await;
            match res {
                Err(err) => {
                    // Connection was dropped as expected
                    assert!(
                        err.is_connection_dropped() || err.is_timeout(),
                        "Expected connection dropped or timeout error, got: {err:?}",
                    );
                    // Retry and verify we're still on name 2ndName after reconnection
                    let client_info = repeat_try_create(|| async {
                        let mut client = test_basics.client.clone();
                        let response = client.send_command(&client_info_cmd, None).await.ok()?;
                        match response {
                            Value::BulkString(bytes) => {
                                Some(String::from_utf8_lossy(&bytes).to_string())
                            }
                            Value::VerbatimString { text, .. } => Some(text),
                            _ => None,
                        }
                    })
                    .await;
                    assert!(client_info.contains("name=2ndName"));
                }
                Ok(response) => {
                    // Command succeeded, extract new client ID and compare
                    let new_client_info = match response {
                        Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
                        Value::VerbatimString { text, .. } => text,
                        _ => panic!("Unexpected CLIENT INFO response type: {:?}", response),
                    };
                    let new_client_id = utilities::extract_client_id(&new_client_info)
                        .expect("Failed to extract new client ID");
                    assert_ne!(
                        initial_client_id, new_client_id,
                        "Client ID should change after reconnection if command succeeds"
                    );
                    // Check that the client name is still 2ndName (from CLIENT SETNAME command)
                    println!("{}", new_client_info);
                    assert!(new_client_info.contains("name=2ndName"));
                }
            }
        });
    }
}
