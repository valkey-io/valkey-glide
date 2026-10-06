#![cfg(feature = "cluster")]

mod support;

use std::{
    net::TcpListener,
    panic::catch_unwind,
    thread,
    time::{Duration, Instant},
};

use support::{
    get_random_available_port,
    ports::{available_port, is_reserved, PortReservation},
    readiness::{wait_for_server, STARTUP_TIMEOUT},
    NodePorts, PortPolicy, RedisCluster, RedisServer, TestClusterContext,
};

fn listener() -> TcpListener {
    TcpListener::bind("127.0.0.1:0").unwrap()
}

#[test]
fn restart_reservation_excludes_client_and_bus_ports_until_released() {
    let client = listener();
    let bus = listener();
    let ports = [
        client.local_addr().unwrap().port(),
        bus.local_addr().unwrap().port(),
    ];
    let reservation = PortReservation::new(ports);
    drop((client, bus));

    // A forced candidate list makes accidental reuse observable on every run.
    let result = thread::spawn(move || available_port(ports)).join().unwrap();
    assert_eq!(
        result.unwrap_err().kind(),
        std::io::ErrorKind::AddrNotAvailable
    );
    drop(reservation);
    assert!(!is_reserved(ports[0]));
    assert!(!is_reserved(ports[1]));
}

#[test]
fn overlapping_reservations_survive_drop_and_unwind() {
    let held = listener();
    let port = held.local_addr().unwrap().port();
    let reservation = PortReservation::new([port]);
    drop(held);
    let panic = catch_unwind(|| {
        let _overlap = PortReservation::new([port]);
        panic!("simulate interrupted restart");
    });
    assert!(panic.is_err());
    assert!(available_port([port]).is_err());
    drop(reservation);
    assert!(!is_reserved(port));
}

#[test]
fn occupied_client_and_bus_ports_retry_on_fresh_ports() {
    for occupy_client in [true, false] {
        let held = listener();
        let occupied = held.local_addr().unwrap().port();
        let free = get_random_available_port();
        let requested = if occupy_client {
            NodePorts {
                client: occupied,
                bus: free,
            }
        } else {
            NodePorts {
                client: free,
                bus: occupied,
            }
        };
        let (mut servers, _folders, actual) = RedisCluster::start_nodes(
            &[requested],
            0,
            &[],
            false,
            &None,
            false,
            PortPolicy::Fresh,
            2,
        );
        assert_ne!(actual[0], requested);
        assert_ne!(actual[0].client, occupied);
        assert_ne!(actual[0].bus, occupied);
        assert!(servers[0].process.try_wait().unwrap().is_none());
        assert_eq!(held.local_addr().unwrap().port(), occupied);
    }
}

#[test]
fn occupied_client_and_bus_ports_exhaust_fixed_retries() {
    for occupy_client in [true, false] {
        let held = listener();
        let occupied = held.local_addr().unwrap().port();
        let free = get_random_available_port();
        let requested = if occupy_client {
            NodePorts {
                client: occupied,
                bus: free,
            }
        } else {
            NodePorts {
                client: free,
                bus: occupied,
            }
        };
        let start = Instant::now();
        let panic = catch_unwind(|| {
            RedisCluster::start_nodes(
                &[requested],
                0,
                &[],
                false,
                &None,
                false,
                PortPolicy::Fixed,
                2,
            );
        })
        .expect_err("a fixed-port restart cannot move to a different address");
        let message = panic.downcast_ref::<String>().unwrap();
        assert!(message.contains("after 3 attempts"), "{message}");
        assert!(message.contains(&occupied.to_string()), "{message}");
        assert!(message.contains("creation failed with status"), "{message}");
        assert!(start.elapsed() < Duration::from_secs(5));
        assert_eq!(held.local_addr().unwrap().port(), occupied);
    }
}

#[test]
fn original_client_reaches_replacement_while_another_fixture_is_running() {
    let cluster = TestClusterContext::new(3, 0);
    let client = cluster.client.clone();
    let old_ports = cluster.cluster.ports();
    let restart_ports = cluster.cluster.reserve_ports_for_restart();
    drop(cluster);

    let candidates: Vec<_> = old_ports.iter().flat_map(|p| [p.client, p.bus]).collect();
    let mut competitor = thread::spawn(move || {
        assert!(available_port(candidates.clone()).is_err());
        let fallback = get_random_available_port();
        let port = available_port(candidates.into_iter().chain([fallback])).unwrap();
        assert_eq!(port, fallback);
        RedisServer::new_with_addr_and_modules(
            redis::ConnectionAddr::Tcp("127.0.0.1".into(), port),
            &[],
            false,
        )
    })
    .join()
    .unwrap();

    let competitor_client = redis::Client::open(competitor.connection_info()).unwrap();
    wait_for_server(
        &mut competitor.process,
        &competitor_client,
        false,
        STARTUP_TIMEOUT,
    )
    .expect("competing fixture must be ready before the cluster restarts");

    let replacement =
        TestClusterContext::restart_on_ports(restart_ports, 0, |builder| builder, false);
    assert_eq!(replacement.cluster.ports(), old_ports);
    let mut connection = client.get_connection(None).unwrap();
    redis::cmd("SET")
        .arg("restart-test")
        .arg("value")
        .query::<()>(&mut connection)
        .unwrap();
    let value: String = redis::cmd("GET")
        .arg("restart-test")
        .query(&mut connection)
        .unwrap();
    assert_eq!(value, "value");
    assert!(
        competitor.process.try_wait().unwrap().is_none(),
        "competing fixture exited before the replacement-cluster check completed"
    );
    drop(competitor);
}
