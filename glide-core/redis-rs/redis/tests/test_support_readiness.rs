//! Incidental port collisions cannot reliably exercise startup failures in CI.
#![cfg(unix)]

mod support;

use std::{
    process::{Child, Command},
    sync::{
        atomic::{AtomicUsize, Ordering},
        Arc,
    },
    thread,
    time::{Duration, Instant},
};

use redis::{Client, ProtocolVersion};
use support::readiness::wait_for_server;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    sync::oneshot,
};

struct TestChild(Child);

impl TestChild {
    fn new() -> Self {
        Self(Command::new("sleep").arg("30").spawn().unwrap())
    }
}

impl Drop for TestChild {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}

#[derive(Clone, Copy)]
enum Reply {
    Ready,
    LoadingOnce,
    LoadingAlways,
    StallInfoOnce,
    DisconnectOnce,
    WrongPid,
    MissingPid,
    StallSetup,
    StallInfo,
}

struct Peer {
    port: u16,
    infos: Arc<AtomicUsize>,
    flushes: Arc<AtomicUsize>,
    stop: Option<oneshot::Sender<()>>,
    thread: Option<thread::JoinHandle<()>>,
}

impl Peer {
    fn new(pid: u32, reply: Reply) -> Self {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        listener.set_nonblocking(true).unwrap();
        let infos = Arc::new(AtomicUsize::new(0));
        let flushes = Arc::new(AtomicUsize::new(0));
        let info_count = infos.clone();
        let flush_count = flushes.clone();
        let (stop, stopped) = oneshot::channel();
        let thread = thread::spawn(move || {
            support::current_thread_runtime().block_on(async move {
                let listener = tokio::net::TcpListener::from_std(listener).unwrap();
                let serve = async {
                    loop {
                        let (mut stream, _) = listener.accept().await.unwrap();
                        let mut decoder = combine::stream::Decoder::new();
                        loop {
                            if matches!(reply, Reply::StallSetup) {
                                std::future::pending::<()>().await;
                            }
                            let Ok(value) =
                                redis::parse_redis_value_async(&mut decoder, &mut stream).await
                            else {
                                break;
                            };
                            let args: Vec<String> = redis::from_redis_value(&value).unwrap();
                            let response = match args[0].as_str() {
                                "INFO" => {
                                    let attempt = info_count.fetch_add(1, Ordering::SeqCst);
                                    match reply {
                                        Reply::StallInfo => std::future::pending::<String>().await,
                                        Reply::LoadingOnce if attempt == 0 => {
                                            "-LOADING still starting\r\n".into()
                                        }
                                        Reply::LoadingAlways => {
                                            "-LOADING still starting\r\n".into()
                                        }
                                        Reply::StallInfoOnce if attempt == 0 => {
                                            // A retry can succeed while the previous driver still
                                            // holds its socket; EOF is evidence of cleanup.
                                            let mut byte = [0];
                                            assert_eq!(stream.read(&mut byte).await.unwrap(), 0);
                                            break;
                                        }
                                        Reply::DisconnectOnce if attempt == 0 => break,
                                        _ => {
                                            let info = match reply {
                                                Reply::WrongPid => {
                                                    format!("process_id:{}\r\n", pid + 1)
                                                }
                                                Reply::MissingPid => {
                                                    "redis_version:9.0.2\r\n".into()
                                                }
                                                _ => format!("process_id:{pid}\r\n"),
                                            };
                                            format!("${}\r\n{info}\r\n", info.len())
                                        }
                                    }
                                }
                                "FLUSHDB" => {
                                    flush_count.fetch_add(1, Ordering::SeqCst);
                                    "+OK\r\n".into()
                                }
                                // Successful setup keeps these failures specific to INFO.
                                "CLIENT" => "+OK\r\n".into(),
                                other => panic!("unexpected probe command: {other}"),
                            };
                            if stream.write_all(response.as_bytes()).await.is_err() {
                                break;
                            }
                        }
                    }
                };
                tokio::select! {
                    _ = stopped => {},
                    _ = serve => {},
                }
            });
        });
        Self {
            port,
            infos,
            flushes,
            stop: Some(stop),
            thread: Some(thread),
        }
    }

    fn client(&self) -> Client {
        Client::open(format!("redis://127.0.0.1:{}/", self.port)).unwrap()
    }
}

impl Drop for Peer {
    fn drop(&mut self) {
        let _ = self.stop.take().unwrap().send(());
        self.thread.take().unwrap().join().unwrap();
    }
}

#[test]
fn readiness_retries_info_errors_before_flushing() {
    for reply in [
        Reply::LoadingOnce,
        Reply::DisconnectOnce,
        Reply::StallInfoOnce,
    ] {
        let mut child = TestChild::new();
        let peer = Peer::new(child.0.id(), reply);
        wait_for_server(&mut child.0, &peer.client(), true, Duration::from_secs(2)).unwrap();
        assert_eq!(peer.infos.load(Ordering::SeqCst), 2);
        assert_eq!(peer.flushes.load(Ordering::SeqCst), 1);
    }
}

#[test]
fn readiness_rejects_wrong_or_missing_pid_without_flushing() {
    for (reply, message) in [
        (Reply::WrongPid, "address is served by pid"),
        (Reply::MissingPid, "INFO server has no valid process_id"),
    ] {
        let mut child = TestChild::new();
        let peer = Peer::new(child.0.id(), reply);
        let err = wait_for_server(&mut child.0, &peer.client(), true, Duration::from_secs(2))
            .unwrap_err()
            .to_string();
        assert!(err.contains(message), "{err}");
        assert_eq!(peer.infos.load(Ordering::SeqCst), 1);
        assert_eq!(peer.flushes.load(Ordering::SeqCst), 0);
    }
}

#[test]
fn readiness_bounds_resp2_resp3_and_tls_setup() {
    for (protocol, tls) in [
        (ProtocolVersion::RESP2, false),
        (ProtocolVersion::RESP3, false),
        (ProtocolVersion::RESP2, true),
    ] {
        let mut child = TestChild::new();
        let peer = Peer::new(child.0.id(), Reply::StallSetup);
        let mut info = peer.client().get_connection_info().clone();
        info.redis.protocol = protocol;
        if tls {
            info.addr = redis::ConnectionAddr::TcpTls {
                host: "127.0.0.1".into(),
                port: peer.port,
                insecure: true,
                tls_params: None,
            };
        }
        let client = Client::open(info).unwrap();
        let start = Instant::now();
        let err = wait_for_server(&mut child.0, &client, false, Duration::from_millis(100))
            .unwrap_err()
            .to_string();
        assert!(err.contains("was not ready within"), "{err}");
        assert!(start.elapsed() < Duration::from_secs(2));
        assert_eq!(peer.flushes.load(Ordering::SeqCst), 0);
    }
}

#[test]
fn readiness_bounds_info() {
    let mut child = TestChild::new();
    let peer = Peer::new(child.0.id(), Reply::StallInfo);
    let start = Instant::now();
    let err = wait_for_server(
        &mut child.0,
        &peer.client(),
        true,
        Duration::from_millis(100),
    )
    .unwrap_err()
    .to_string();
    assert!(err.contains("was not ready within"), "{err}");
    assert!(start.elapsed() < Duration::from_secs(2));
    assert_eq!(peer.infos.load(Ordering::SeqCst), 1);
    assert_eq!(peer.flushes.load(Ordering::SeqCst), 0);
}

#[test]
fn readiness_reports_child_exit_before_connecting() {
    let mut child = TestChild::new();
    let peer = Peer::new(child.0.id(), Reply::Ready);
    child.0.kill().unwrap();
    child.0.wait().unwrap();
    let err = wait_for_server(&mut child.0, &peer.client(), false, Duration::from_secs(2))
        .unwrap_err()
        .to_string();
    assert!(err.contains("exited with"), "{err}");
    assert_eq!(peer.infos.load(Ordering::SeqCst), 0);
}

#[tokio::test]
async fn readiness_can_run_inside_tokio_without_flushing() {
    let mut child = TestChild::new();
    let peer = Peer::new(child.0.id(), Reply::Ready);
    wait_for_server(&mut child.0, &peer.client(), false, Duration::from_secs(2)).unwrap();
    assert_eq!(peer.infos.load(Ordering::SeqCst), 1);
    assert_eq!(peer.flushes.load(Ordering::SeqCst), 0);
}

#[test]
fn readiness_retains_last_info_error_at_deadline() {
    let mut child = TestChild::new();
    let peer = Peer::new(child.0.id(), Reply::LoadingAlways);
    let err = wait_for_server(
        &mut child.0,
        &peer.client(),
        false,
        Duration::from_millis(200),
    )
    .unwrap_err()
    .to_string();
    assert!(err.contains("was not ready within"), "{err}");
    assert!(err.contains("still starting"), "{err}");
    assert!(peer.infos.load(Ordering::SeqCst) > 1);
    assert_eq!(peer.flushes.load(Ordering::SeqCst), 0);
}
