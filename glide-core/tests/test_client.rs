/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
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
    use std::collections::HashMap;

    use super::*;
    use glide_core::client::{Client, DEFAULT_RESPONSE_TIMEOUT};
    use redis::{
        cluster_routing::{MultipleNodeRoutingInfo, RoutingInfo},
        FromRedisValue, InfoDict, RedisConnectionInfo, Value,
    };
    use rstest::rstest;
    use utilities::cluster::*;
    use utilities::BackingServer;
    use utilities::*;

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
                        create_connection_request(&[connection_addr.clone()], &configuration)
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
    fn test_pipeline_is_not_routed() {
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
                    .send_transaction(&pipe, None)
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
            let result = test_basics.client.send_transaction(&pipeline, None).await;
            assert!(result.is_err(), "Received {:?}", result);
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

            let result = test_basics.client.send_transaction(&pipeline, None).await;
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
}
