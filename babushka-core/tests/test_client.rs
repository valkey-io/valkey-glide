mod utilities;

#[cfg(test)]
mod shared_client_tests {
    use super::*;
    use babushka::client::Client;
    use rstest::rstest;
    use utilities::cluster::*;
    use utilities::*;

    enum BackingServer {
        Cmd(RedisServer),
        Cme(RedisCluster),
    }

    struct TestBasics {
        server: BackingServer,
        client: Client,
    }

    async fn setup_test_basics(use_tls: bool, use_cme: bool) -> TestBasics {
        if use_cme {
            let cluster_basics = cluster::setup_test_basics(use_tls).await;
            TestBasics {
                server: BackingServer::Cme(cluster_basics.cluster),
                client: cluster_basics.client,
            }
        } else {
            let cmd_basics = utilities::setup_test_basics(use_tls).await;
            TestBasics {
                server: BackingServer::Cmd(cmd_basics.server),
                client: Client::CMD(cmd_basics.client),
            }
        }
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_set_and_get(
        #[values(false, true)] use_tls: bool,
        #[values(false, true)] use_cme: bool,
    ) {
        block_on_all(async {
            let test_basics = setup_test_basics(use_tls, use_cme).await;
            let key = "hello";
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_client_handle_concurrent_workload(
        #[values(false, true)] use_tls: bool,
        #[values(false, true)] use_cme: bool,
    ) {
        block_on_all(async {
            let test_basics = setup_test_basics(use_tls, use_cme).await;
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
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_report_closing_when_server_closes(#[values(false, true)] use_cme: bool) {
        block_on_all(async {
            let mut test_basics = setup_test_basics(false, use_cme).await;
            let server = test_basics.server;
            drop(server);

            let get_result = send_get(&mut test_basics.client, "foobar")
                .await
                .unwrap_err();
            assert!(get_result.is_connection_dropped());
        });
    }
}
