mod utilities;

#[cfg(test)]
mod client_cmd_tests {
    use super::*;
    use redis::Value;
    use rstest::rstest;
    use std::time::Duration;
    use utilities::*;

    #[rstest]
    #[timeout(SHORT_CMD_TEST_TIMEOUT)]
    fn test_report_disconnect_and_reconnect_after_temporary_disconnect(
        #[values(false, true)] use_tls: bool,
    ) {
        let server_available_event =
            std::sync::Arc::new(futures_intrusive::sync::ManualResetEvent::new(false));
        let server_available_event_clone = server_available_event.clone();
        block_on_all(async move {
            let mut test_basics = setup_test_basics(use_tls).await;
            let server = test_basics.server;
            let address = server.get_client_addr();
            drop(server);

            let thread = std::thread::spawn(move || {
                block_on_all(async move {
                    let mut get_command = redis::Cmd::new();
                    get_command
                        .arg("GET")
                        .arg("test_report_disconnect_and_reconnect_after_temporary_disconnect");
                    let error = test_basics.client.send_packed_command(&get_command).await;
                    assert!(error.is_err(), "{error:?}",);

                    server_available_event.wait().await;
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
            server_available_event_clone.set();

            thread.join().unwrap();
        });
    }
}
