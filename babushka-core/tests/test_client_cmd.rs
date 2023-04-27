mod utilities;

#[cfg(test)]
mod client_cmd_tests {
    use super::*;
    use babushka::connection_request::ConnectionRetryStrategy;
    use redis::Value;
    use rstest::rstest;
    use std::time::Duration;
    use utilities::*;

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_reconnect_and_complete_request_after_temporary_disconnect(
        #[values(false, true)] use_tls: bool,
    ) {
        block_on_all(async move {
            // We want a retry strategy that retries often, so that the test won't hang an wait too long for a retry.
            let mut retry_strategy = ConnectionRetryStrategy::new();
            retry_strategy.number_of_retries = 100;
            retry_strategy.factor = 1;
            retry_strategy.exponent_base = 2;
            let mut test_basics =
                setup_test_basics_and_connection_retry_strategy(use_tls, Some(retry_strategy))
                    .await;
            let server = test_basics.server;
            let address = server.get_client_addr();
            drop(server);

            let thread = std::thread::spawn(move || {
                block_on_all(async move {
                    let get_result = send_get(
                        &mut test_basics.client,
                        "test_reconnect_and_complete_request_after_temporary_disconnect",
                    )
                    .await
                    .unwrap();
                    assert_eq!(get_result, Value::Nil);
                });
            });
            let _new_server = RedisServer::new_with_addr_and_modules(address.clone(), &[]);
            wait_for_server_to_become_ready(&address).await;

            thread.join().unwrap();
        });
    }
}
