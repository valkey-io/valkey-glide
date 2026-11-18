#![cfg(unix)]

mod support;

use std::{
    io::Write,
    net::TcpListener,
    process::{Command, Stdio},
};

use redis::ConnectionAddr;
use support::{
    get_random_available_port,
    readiness::{ReadinessError, ADDRESS_IN_USE},
    RedisServer, TestContext, STARTUP_ATTEMPTS,
};

fn spawn_on(addr: ConnectionAddr) -> RedisServer {
    RedisServer::new_with_addr_and_modules(addr, &[], false)
}

fn tcp(port: u16) -> ConnectionAddr {
    ConnectionAddr::Tcp("127.0.0.1".into(), port)
}

fn logged_bind_failure(server: &RedisServer) -> bool {
    std::fs::read_to_string(RedisServer::log_file(&server.tempdir))
        .map(|log| log.contains(ADDRESS_IN_USE))
        .unwrap_or(false)
}

fn client(server: &RedisServer) -> redis::Client {
    redis::Client::open(server.connection_info()).unwrap()
}

fn ping(context: &TestContext) {
    let pong: String = redis::cmd("PING").query(&mut context.connection()).unwrap();
    assert_eq!(pong, "PONG");
}

#[test]
fn bind_collision_restarts_on_a_fresh_port() {
    let taken = TcpListener::bind("127.0.0.1:0").unwrap();
    let taken_port = taken.local_addr().unwrap().port();
    let mut spawns = 0;
    let context = TestContext::start(
        || {
            spawns += 1;
            let port = if spawns == 1 {
                taken_port
            } else {
                get_random_available_port()
            };
            spawn_on(tcp(port))
        },
        client,
    )
    .unwrap();
    assert_eq!(spawns, 2);
    assert_ne!(context.server.connection_info().addr, tcp(taken_port));
    ping(&context);
}

#[test]
fn foreign_owner_restarts_on_a_fresh_port() {
    // TCP regardless of the server type: dropping the impostor below runs
    // `RedisServer::stop`, which would unlink a shared Unix socket path.
    let owner = TestContext::start(|| spawn_on(tcp(get_random_available_port())), client).unwrap();
    let owner_addr = owner.server.connection_info().addr;
    let mut spawns = 0;
    let context = TestContext::start(
        || {
            spawns += 1;
            if spawns == 1 {
                RedisServer {
                    process: Command::new("sleep")
                        .arg("30")
                        .stdout(Stdio::null())
                        .spawn()
                        .unwrap(),
                    tempdir: tempfile::tempdir().unwrap(),
                    addr: owner_addr.clone(),
                    tls_paths: None,
                }
            } else {
                spawn_on(tcp(get_random_available_port()))
            }
        },
        client,
    )
    .unwrap();
    assert_eq!(spawns, 2);
    assert_ne!(context.server.connection_info().addr, owner_addr);
    ping(&context);
    ping(&owner);
}

#[test]
fn configuration_failure_is_not_retried() {
    let dir = tempfile::tempdir().unwrap();
    let config = dir.path().join("bad.conf");
    std::fs::File::create(&config)
        .unwrap()
        .write_all(b"no-such-directive yes\n")
        .unwrap();
    let mut spawns = 0;
    let err = TestContext::start(
        || {
            spawns += 1;
            RedisServer::new_with_addr_tls_modules_and_spawner(
                tcp(get_random_available_port()),
                Some(&config),
                None,
                false,
                &[],
                |cmd| cmd.spawn().unwrap(),
            )
        },
        client,
    )
    .err()
    .expect("a misconfigured server must not start");
    assert_eq!(spawns, 1);
    assert!(matches!(err, ReadinessError::Exited { .. }), "{err}");
}

#[test]
fn persistent_collision_is_bounded() {
    let taken = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = tcp(taken.local_addr().unwrap().port());
    let mut spawns = 0;
    let err = TestContext::start(
        || {
            spawns += 1;
            spawn_on(addr.clone())
        },
        client,
    )
    .err()
    .expect("a held port must not start a server");
    assert_eq!(spawns, STARTUP_ATTEMPTS);
    assert!(
        matches!(
            err,
            ReadinessError::Exited { .. } | ReadinessError::Timeout { .. }
        ),
        "{err}"
    );
}

#[test]
fn bind_failure_is_what_the_classifier_reads() {
    let taken = TcpListener::bind("127.0.0.1:0").unwrap();
    let mut server = spawn_on(tcp(taken.local_addr().unwrap().port()));
    server.process.wait().unwrap();
    assert!(logged_bind_failure(&server));
    let healthy =
        TestContext::start(|| spawn_on(tcp(get_random_available_port())), client).unwrap();
    assert!(!logged_bind_failure(&healthy.server));
}
