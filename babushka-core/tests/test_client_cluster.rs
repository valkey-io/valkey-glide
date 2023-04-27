mod utilities;

#[cfg(test)]
mod cluster_client {
    use super::*;
    use crate::utilities::cluster::RedisCluster;
    use babushka::client::Client;
    use babushka::connection_request::{self, ConnectionRequest};
    use protobuf::MessageField;
    use rstest::rstest;
    use utilities::cluster::*;
    use utilities::*;

    struct TestBasics {
        cluster: RedisCluster,
        client: Client,
    }

    async fn setup_test_basics(use_tls: bool) -> TestBasics {
        let cluster = RedisCluster::new(3, 0, use_tls).await;
        let mut connection_request = ConnectionRequest::new();
        connection_request.addresses = cluster
            .iter_servers()
            .map(|server| get_address_info(&server.get_client_addr()))
            .collect();
        connection_request.tls_mode = if use_tls {
            connection_request::TlsMode::InsecureTls
        } else {
            connection_request::TlsMode::NoTls
        }
        .into();
        connection_request.connection_retry_strategy = MessageField::from_option(None);
        connection_request.cluster_mode_enabled = true;
        let client = Client::new(connection_request).await.unwrap();
        TestBasics { cluster, client }
    }

    #[rstest]
    #[timeout(SHORT_CLUSTER_TEST_TIMEOUT)]
    fn test_send_set_and_get(#[values(false, true)] use_tls: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(use_tls).await;
            let key = "hello";
            send_set_and_get(test_basics.client.clone(), key.to_string()).await;
        });
    }

    #[rstest]
    #[timeout(LONG_CLUSTER_TEST_TIMEOUT)]
    fn test_client_handle_concurrent_workload(#[values(false, true)] use_tls: bool) {
        block_on_all(async {
            let test_basics = setup_test_basics(use_tls).await;
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
    fn test_report_closing_when_server_closes() {
        block_on_all(async {
            let mut test_basics = setup_test_basics(false).await;
            let cluster = test_basics.cluster;
            drop(cluster);

            let get_result = send_get(&mut test_basics.client, "foobar")
                .await
                .unwrap_err();
            assert!(get_result.is_connection_dropped());
        });
    }
}
