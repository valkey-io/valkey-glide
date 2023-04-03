#![allow(dead_code)]
use std::{
    env, fs, io, net::SocketAddr, net::TcpListener, path::PathBuf, process, thread::sleep,
    time::Duration,
};

use babushka::connection_request::AddressInfo;
use futures::Future;
use rand::{distributions::Alphanumeric, Rng};
use redis::{ConnectionAddr, RedisResult, Value};
use socket2::{Domain, Socket, Type};
use tempfile::TempDir;

// Code copied from redis-rs

#[derive(PartialEq, Eq)]
pub enum ServerType {
    Tcp { tls: bool },
    Unix,
}

pub struct RedisServer {
    pub process: process::Child,
    tempdir: Option<tempfile::TempDir>,
    addr: redis::ConnectionAddr,
}

pub enum Module {
    Json,
}

impl RedisServer {
    pub fn new(server_type: ServerType) -> RedisServer {
        RedisServer::with_modules(server_type, &[])
    }

    pub fn with_modules(server_type: ServerType, modules: &[Module]) -> RedisServer {
        let addr = match server_type {
            ServerType::Tcp { tls } => {
                // this is technically a race but we can't do better with
                // the tools that redis gives us :(
                let addr = &"127.0.0.1:0".parse::<SocketAddr>().unwrap().into();
                let socket = Socket::new(Domain::IPV4, Type::STREAM, None).unwrap();
                socket.set_reuse_address(true).unwrap();
                socket.bind(addr).unwrap();
                socket.listen(1).unwrap();
                let listener = TcpListener::from(socket);
                let redis_port = listener.local_addr().unwrap().port();
                if tls {
                    redis::ConnectionAddr::TcpTls {
                        host: "127.0.0.1".to_string(),
                        port: redis_port,
                        insecure: true,
                    }
                } else {
                    redis::ConnectionAddr::Tcp("127.0.0.1".to_string(), redis_port)
                }
            }
            ServerType::Unix => {
                let (a, b) = rand::random::<(u64, u64)>();
                let path = format!("/tmp/redis-rs-test-{a}-{b}.sock");
                redis::ConnectionAddr::Unix(PathBuf::from(&path))
            }
        };
        RedisServer::new_with_addr_and_modules(addr, modules)
    }

    pub fn new_with_addr_and_modules(
        addr: redis::ConnectionAddr,
        modules: &[Module],
    ) -> RedisServer {
        RedisServer::new_with_addr_tls_modules_and_spawner(addr, None, modules, |cmd| {
            cmd.spawn()
                .unwrap_or_else(|err| panic!("Failed to run {cmd:?}: {err}"))
        })
    }

    pub fn new_with_addr_tls_modules_and_spawner<
        F: FnOnce(&mut process::Command) -> process::Child,
    >(
        addr: redis::ConnectionAddr,
        tls_paths: Option<TlsFilePaths>,
        modules: &[Module],
        spawner: F,
    ) -> RedisServer {
        let mut redis_cmd = process::Command::new("redis-server");

        for module in modules {
            match module {
                Module::Json => {
                    redis_cmd
                        .arg("--loadmodule")
                        .arg(env::var("REDIS_RS_REDIS_JSON_PATH").expect(
                        "Unable to find path to RedisJSON at REDIS_RS_REDIS_JSON_PATH, is it set?",
                    ));
                }
            };
        }

        redis_cmd
            .stdout(process::Stdio::null())
            .stderr(process::Stdio::null());
        let tempdir = tempfile::Builder::new()
            .prefix("redis")
            .tempdir()
            .expect("failed to create tempdir");
        match addr {
            redis::ConnectionAddr::Tcp(ref bind, server_port) => {
                redis_cmd
                    .arg("--port")
                    .arg(server_port.to_string())
                    .arg("--bind")
                    .arg(bind);

                RedisServer {
                    process: spawner(&mut redis_cmd),
                    tempdir: None,
                    addr,
                }
            }
            redis::ConnectionAddr::TcpTls { ref host, port, .. } => {
                let tls_paths = tls_paths.unwrap_or_else(|| build_keys_and_certs_for_tls(&tempdir));

                // prepare redis with TLS
                redis_cmd
                    .arg("--tls-port")
                    .arg(&port.to_string())
                    .arg("--port")
                    .arg("0")
                    .arg("--tls-cert-file")
                    .arg(&tls_paths.redis_crt)
                    .arg("--tls-key-file")
                    .arg(&tls_paths.redis_key)
                    .arg("--tls-ca-cert-file")
                    .arg(&tls_paths.ca_crt)
                    .arg("--tls-auth-clients") // Make it so client doesn't have to send cert
                    .arg("no")
                    .arg("--bind")
                    .arg(host);

                let addr = redis::ConnectionAddr::TcpTls {
                    host: host.clone(),
                    port,
                    insecure: true,
                };

                RedisServer {
                    process: spawner(&mut redis_cmd),
                    tempdir: Some(tempdir),
                    addr,
                }
            }
            redis::ConnectionAddr::Unix(ref path) => {
                redis_cmd
                    .arg("--port")
                    .arg("0")
                    .arg("--unixsocket")
                    .arg(path);
                RedisServer {
                    process: spawner(&mut redis_cmd),
                    tempdir: Some(tempdir),
                    addr,
                }
            }
        }
    }

    pub fn get_client_addr(&self) -> &redis::ConnectionAddr {
        &self.addr
    }

    pub fn stop(&mut self) {
        let _ = self.process.kill();
        let _ = self.process.wait();
        if let redis::ConnectionAddr::Unix(ref path) = *self.get_client_addr() {
            fs::remove_file(path).ok();
        }
    }
}

impl Drop for RedisServer {
    fn drop(&mut self) {
        self.stop()
    }
}

pub struct TestContext {
    pub server: RedisServer,
    pub client: redis::Client,
}

impl TestContext {
    pub fn new(server_type: ServerType) -> TestContext {
        TestContext::with_modules(server_type, &[])
    }

    pub fn with_modules(server_type: ServerType, modules: &[Module]) -> TestContext {
        let server = RedisServer::with_modules(server_type, modules);

        let client = redis::Client::open(redis::ConnectionInfo {
            addr: server.get_client_addr().clone(),
            redis: Default::default(),
        })
        .unwrap();
        let mut con;

        let millisecond = Duration::from_millis(1);
        let mut retries = 0;
        loop {
            match client.get_connection() {
                Err(err) => {
                    if err.is_connection_refusal() {
                        sleep(millisecond);
                        retries += 1;
                        if retries > 100000 {
                            panic!("Tried to connect too many times, last error: {err}");
                        }
                    } else {
                        panic!("Could not connect: {err}");
                    }
                }
                Ok(x) => {
                    con = x;
                    break;
                }
            }
        }
        redis::cmd("FLUSHDB").execute(&mut con);

        TestContext { server, client }
    }

    pub fn connection(&self) -> redis::Connection {
        self.client.get_connection().unwrap()
    }

    pub fn stop_server(&mut self) {
        self.server.stop();
    }
}

pub fn encode_value<W>(value: &Value, writer: &mut W) -> io::Result<()>
where
    W: io::Write,
{
    #![allow(clippy::write_with_newline)]
    match *value {
        Value::Nil => write!(writer, "$-1\r\n"),
        Value::Int(val) => write!(writer, ":{val}\r\n"),
        Value::Data(ref val) => {
            write!(writer, "${}\r\n", val.len())?;
            writer.write_all(val)?;
            writer.write_all(b"\r\n")
        }
        Value::Bulk(ref values) => {
            write!(writer, "*{}\r\n", values.len())?;
            for val in values.iter() {
                encode_value(val, writer)?;
            }
            Ok(())
        }
        Value::Okay => write!(writer, "+OK\r\n"),
        Value::Status(ref s) => write!(writer, "+{s}\r\n"),
    }
}

#[derive(Clone)]
pub struct TlsFilePaths {
    redis_crt: PathBuf,
    redis_key: PathBuf,
    ca_crt: PathBuf,
}

pub fn build_keys_and_certs_for_tls(tempdir: &TempDir) -> TlsFilePaths {
    // Based on shell script in redis's server tests
    // https://github.com/redis/redis/blob/8c291b97b95f2e011977b522acf77ead23e26f55/utils/gen-test-certs.sh
    let ca_crt = tempdir.path().join("ca.crt");
    let ca_key = tempdir.path().join("ca.key");
    let ca_serial = tempdir.path().join("ca.txt");
    let redis_crt = tempdir.path().join("redis.crt");
    let redis_key = tempdir.path().join("redis.key");

    fn make_key<S: AsRef<std::ffi::OsStr>>(name: S, size: usize) {
        process::Command::new("openssl")
            .arg("genrsa")
            .arg("-out")
            .arg(name)
            .arg(&format!("{size}"))
            .stdout(process::Stdio::null())
            .stderr(process::Stdio::null())
            .spawn()
            .expect("failed to spawn openssl")
            .wait()
            .expect("failed to create key");
    }

    // Build CA Key
    make_key(&ca_key, 4096);

    // Build redis key
    make_key(&redis_key, 2048);

    // Build CA Cert
    process::Command::new("openssl")
        .arg("req")
        .arg("-x509")
        .arg("-new")
        .arg("-nodes")
        .arg("-sha256")
        .arg("-key")
        .arg(&ca_key)
        .arg("-days")
        .arg("3650")
        .arg("-subj")
        .arg("/O=Redis Test/CN=Certificate Authority")
        .arg("-out")
        .arg(&ca_crt)
        .stdout(process::Stdio::null())
        .stderr(process::Stdio::null())
        .spawn()
        .expect("failed to spawn openssl")
        .wait()
        .expect("failed to create CA cert");

    // Read redis key
    let mut key_cmd = process::Command::new("openssl")
        .arg("req")
        .arg("-new")
        .arg("-sha256")
        .arg("-subj")
        .arg("/O=Redis Test/CN=Generic-cert")
        .arg("-key")
        .arg(&redis_key)
        .stdout(process::Stdio::piped())
        .stderr(process::Stdio::null())
        .spawn()
        .expect("failed to spawn openssl");

    // build redis cert
    process::Command::new("openssl")
        .arg("x509")
        .arg("-req")
        .arg("-sha256")
        .arg("-CA")
        .arg(&ca_crt)
        .arg("-CAkey")
        .arg(&ca_key)
        .arg("-CAserial")
        .arg(&ca_serial)
        .arg("-CAcreateserial")
        .arg("-days")
        .arg("365")
        .arg("-out")
        .arg(&redis_crt)
        .stdin(key_cmd.stdout.take().expect("should have stdout"))
        .stdout(process::Stdio::null())
        .stderr(process::Stdio::null())
        .spawn()
        .expect("failed to spawn openssl")
        .wait()
        .expect("failed to create redis cert");

    key_cmd.wait().expect("failed to create redis key");

    TlsFilePaths {
        redis_crt,
        redis_key,
        ca_crt,
    }
}

pub async fn wait_for_server_to_become_ready(server_address: &ConnectionAddr) {
    let millisecond = Duration::from_millis(1);
    let mut retries = 0;
    let client = redis::Client::open(redis::ConnectionInfo {
        addr: server_address.clone(),
        redis: redis::RedisConnectionInfo::default(),
    })
    .unwrap();
    loop {
        match client.get_multiplexed_async_connection().await {
            Err(err) => {
                if err.is_connection_refusal() {
                    tokio::time::sleep(millisecond).await;
                    retries += 1;
                    if retries > 100000 {
                        panic!("Tried to connect too many times, last error: {err}");
                    }
                } else {
                    panic!("Could not connect: {err}");
                }
            }
            Ok(mut con) => {
                let _: RedisResult<()> = redis::cmd("FLUSHDB").query_async(&mut con).await;
                break;
            }
        }
    }
}

pub fn current_thread_runtime() -> tokio::runtime::Runtime {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap()
}

pub fn block_on_all<F>(f: F) -> F::Output
where
    F: Future,
{
    current_thread_runtime().block_on(f)
}

pub fn get_address_info(address: &ConnectionAddr) -> AddressInfo {
    let mut address_info = AddressInfo::new();
    match address {
        ConnectionAddr::Tcp(host, port) => {
            address_info.host = host.clone();
            address_info.port = *port as u32;
        }
        ConnectionAddr::TcpTls {
            host,
            port,
            insecure: _,
        } => {
            address_info.host = host.clone();
            address_info.port = *port as u32;
        }
        ConnectionAddr::Unix(_) => unreachable!("Unix connection not tested"),
    }
    address_info
}

pub fn generate_random_string(length: usize) -> String {
    rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(length)
        .map(char::from)
        .collect()
}
