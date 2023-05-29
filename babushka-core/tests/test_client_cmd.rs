mod utilities;

#[cfg(test)]
mod client_cmd_tests {
    use std::time::Duration;

    use super::*;
    use babushka::connection_request::ConnectionRetryStrategy;
    use redis::Value;
    use rstest::rstest;
    use utilities::*;

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_reconnect_and_complete_request_after_temporary_disconnect(
        #[values(false, true)] use_tls: bool,
    ) {
        block_on_all(async move {
            // We want a retry strategy that retries often, so that the test won't hang an wait too long for a retry.
            let mut retry_strategy = ConnectionRetryStrategy::new();
            retry_strategy.number_of_retries = 100;
            retry_strategy.factor = 1;
            retry_strategy.exponent_base = 2;
            let mut test_basics = setup_test_basics_internal(&TestConfiguration {
                use_tls,
                connection_retry_strategy: Some(retry_strategy),
                ..Default::default()
            })
            .await;
            let server = test_basics.server;
            let address = server.get_client_addr();
            drop(server);

            let thread = std::thread::spawn(move || {
                block_on_all(async move {
                    let mut get_command = redis::Cmd::new();
                    get_command
                        .arg("GET")
                        .arg("test_reconnect_and_complete_request_after_temporary_disconnect");
                    let get_result = test_basics
                        .client
                        .send_packed_command(&get_command)
                        .await
                        .unwrap();
                    assert_eq!(get_result, Value::Nil);
                });
            });

            // If we don't sleep here, TLS connections will start reconnecting too soon, and then will timeout
            // before the server is ready.
            tokio::time::sleep(Duration::from_millis(10)).await;

            let _new_server = RedisServer::new_with_addr_and_modules(address.clone(), &[]);
            wait_for_server_to_become_ready(&address).await;

            thread.join().unwrap();
        });
    }
}
