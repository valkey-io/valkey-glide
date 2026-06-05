// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod constants;
mod utilities;

#[cfg(test)]
mod test_monitor {
    use super::utilities;
    use super::utilities::get_shared_server_address;
    use glide_core::client::{
        MonitorClient, MonitorLine, MonitorLineCallback, NodeAddress, TlsMode,
    };
    use redis::{ConnectionInfo, GlideConnectionOptions, RedisConnectionInfo};
    use std::sync::{Arc, Mutex};

    fn monitor_conn_info() -> RedisConnectionInfo {
        RedisConnectionInfo {
            db: 0,
            username: None,
            password: None,
            protocol: redis::ProtocolVersion::RESP2,
            client_name: None,
            lib_name: None,
            cache: None,
            server_assisted_cache: false,
        }
    }

    fn make_collector() -> (MonitorLineCallback, Arc<Mutex<Vec<MonitorLine>>>) {
        let lines: Arc<Mutex<Vec<MonitorLine>>> = Arc::new(Mutex::new(Vec::new()));
        let lines_clone = lines.clone();
        let cb: MonitorLineCallback = Arc::new(move |line| {
            lines_clone.lock().unwrap().push(line);
        });
        (cb, lines)
    }

    fn node_addr(server_addr: &redis::ConnectionAddr) -> NodeAddress {
        match server_addr {
            redis::ConnectionAddr::Tcp(host, port) => NodeAddress {
                host: host.clone(),
                port: *port,
            },
            _ => panic!("Expected TCP address"),
        }
    }

    #[tokio::test]
    async fn test_monitor_start_and_receive_line() {
        let server_addr = get_shared_server_address(false);
        utilities::wait_for_server_to_become_ready(&server_addr).await;
        let node_addr = node_addr(&server_addr);

        let (on_line, lines) = make_collector();
        let monitor = MonitorClient::new(&node_addr, monitor_conn_info(), TlsMode::NoTls, on_line)
            .await
            .expect("MonitorClient::new failed");

        let client = redis::Client::open(ConnectionInfo {
            addr: server_addr.clone(),
            redis: monitor_conn_info(),
        })
        .unwrap();
        let mut conn = client
            .get_multiplexed_async_connection(GlideConnectionOptions::default())
            .await
            .unwrap();
        let _: () = redis::cmd("SET")
            .arg("monitor_test_key")
            .arg("monitor_test_val")
            .query_async(&mut conn)
            .await
            .unwrap();

        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
        loop {
            if lines.lock().unwrap().iter().any(|l| {
                l.command == "SET" && l.args.first().map(|s| s.as_str()) == Some("monitor_test_key")
            }) {
                break;
            }
            assert!(
                std::time::Instant::now() < deadline,
                "timed out waiting for SET line"
            );
            tokio::time::sleep(std::time::Duration::from_millis(10)).await;
        }

        let (args, db, client_addr, timestamp) = {
            let found = lines.lock().unwrap();
            let set_line = found
                .iter()
                .find(|l| {
                    l.command == "SET"
                        && l.args.first().map(|s| s.as_str()) == Some("monitor_test_key")
                })
                .unwrap();
            (
                set_line.args.clone(),
                set_line.db,
                set_line.client_addr.clone(),
                set_line.timestamp,
            )
        };
        assert_eq!(args, vec!["monitor_test_key", "monitor_test_val"]);
        assert!(db >= 0);
        assert!(!client_addr.is_empty());
        assert!(timestamp > 0.0);

        monitor.stop_async().await;
    }

    #[tokio::test]
    async fn test_monitor_stop_no_lines_after_stop() {
        let server_addr = get_shared_server_address(false);
        utilities::wait_for_server_to_become_ready(&server_addr).await;
        let node_addr = node_addr(&server_addr);

        let (on_line, lines) = make_collector();
        let monitor = MonitorClient::new(&node_addr, monitor_conn_info(), TlsMode::NoTls, on_line)
            .await
            .expect("MonitorClient::new failed");

        monitor.stop_async().await;
        let count_after_stop = lines.lock().unwrap().len();

        // Issue a command after stop — should NOT appear in lines
        let client = redis::Client::open(ConnectionInfo {
            addr: server_addr.clone(),
            redis: monitor_conn_info(),
        })
        .unwrap();
        let mut conn = client
            .get_multiplexed_async_connection(GlideConnectionOptions::default())
            .await
            .unwrap();
        let _: () = redis::cmd("SET")
            .arg("monitor_after_stop_key")
            .arg("val")
            .query_async(&mut conn)
            .await
            .unwrap();

        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        let count_final = lines.lock().unwrap().len();
        assert_eq!(count_after_stop, count_final, "lines received after stop");
    }

    #[tokio::test]
    async fn test_monitor_stop_idempotent() {
        let server_addr = get_shared_server_address(false);
        utilities::wait_for_server_to_become_ready(&server_addr).await;
        let node_addr = node_addr(&server_addr);

        let (on_line, _lines) = make_collector();
        let mut monitor =
            MonitorClient::new(&node_addr, monitor_conn_info(), TlsMode::NoTls, on_line)
                .await
                .expect("MonitorClient::new failed");

        monitor.stop();
        monitor.stop(); // must not panic
    }
}
