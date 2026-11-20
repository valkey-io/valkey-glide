// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! A failed `Client::new` must not leave an orphaned connect task redialling in the
//! background (#7809). Runs on its own runtime kept alive across the failed creation:
//! on the shared `block_on_all` runtime the spawned task would be killed by the
//! runtime drop, hiding the leak.

#![cfg(unix)]

mod constants;
mod utilities;

#[cfg(test)]
mod orphaned_connect_task_tests {
    use super::utilities::*;
    use glide_core::client::Client;
    use redis::ConnectionAddr;
    use std::io::ErrorKind;
    use std::net::TcpListener;
    use std::time::{Duration, Instant};

    #[test]
    fn failed_client_creation_leaves_no_orphaned_connect_task() {
        let runtime = current_thread_runtime();

        let probe = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = probe.local_addr().unwrap().port();
        drop(probe);

        runtime.block_on(async move {
            let mut request = create_connection_request(
                &[ConnectionAddr::Tcp("127.0.0.1".to_string(), port)],
                &TestConfiguration {
                    // Tiny backoff, so an unfixed reconnect loop would redial
                    // continuously rather than in rare bursts.
                    connection_retry_strategy: Some(
                        glide_core::connection_request::ConnectionRetryStrategy {
                            number_of_retries: 1,
                            factor: 1,
                            exponent_base: 1,
                            ..Default::default()
                        },
                    ),
                    ..Default::default()
                },
            );
            request.connection_timeout = 300;

            let result = Client::new(request.into(), None).await;
            assert!(
                result.is_err(),
                "creation against a refused port should fail"
            );

            // All owners of the failed node's connection are gone by now: an
            // orphaned reconnect task would connect to this listener on every
            // retry, a fixed one exits at its next ownership check.
            let listener = TcpListener::bind(("127.0.0.1", port)).unwrap();
            listener
                .set_nonblocking(true)
                .expect("listener should go non-blocking");

            let start = Instant::now();
            let grace = Duration::from_secs(1);
            let total = Duration::from_secs(3);
            let mut accepted_after_grace = 0usize;
            while start.elapsed() < total {
                match listener.accept() {
                    Ok((stream, _)) => {
                        if start.elapsed() >= grace {
                            accepted_after_grace += 1;
                        }
                        drop(stream); // close it, so a redialling loop reconnects
                    }
                    Err(err) if err.kind() == ErrorKind::WouldBlock => {
                        tokio::time::sleep(Duration::from_millis(10)).await;
                    }
                    Err(err) => panic!("accept failed: {err}"),
                }
            }

            assert_eq!(
                accepted_after_grace, 0,
                "an orphaned connect task is still redialling after the failed \
                 creation dropped its connection"
            );
        });
    }
}
