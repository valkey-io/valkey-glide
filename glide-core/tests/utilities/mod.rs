// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#![allow(dead_code)]
use futures::Future;
use glide_core::{
    client::{Client, StandaloneClient},
    connection_request::{self, AuthenticationInfo, NodeAddress, ProtocolVersion},
};
use once_cell::sync::Lazy;
use rand::{Rng, distributions::Alphanumeric};
use redis::{
    ConnectionAddr, GlideConnectionOptions, PushInfo, RedisConnectionInfo, RedisResult, Value,
    cluster_routing::{MultipleNodeRoutingInfo, RoutingInfo},
};
use socket2::{Domain, Socket, Type};
use std::{
    env, fs, io, net::SocketAddr, net::TcpListener, ops::Deref, path::PathBuf, process,
    sync::Mutex, time::Duration,
};
use tempfile::TempDir;
use tokio::sync::mpsc;
use versions::Versioning;

pub mod cluster;
pub mod mocks;

pub(crate) const SHORT_STANDALONE_TEST_TIMEOUT: Duration = Duration::from_millis(20_000);
pub(crate) const LONG_STANDALONE_TEST_TIMEOUT: Duration = Duration::from_millis(40_000);

// Code copied from redis-rs

#[derive(PartialEq, Eq)]
pub enum ServerType {
    Tcp { tls: bool },
    Unix,
}

type SharedServer = Lazy<Mutex<Option<RedisServer>>>;
static SHARED_SERVER: SharedServer =
    Lazy::new(|| Mutex::new(Some(RedisServer::new(ServerType::Tcp { tls: false }))));

static SHARED_TLS_SERVER: SharedServer =
    Lazy::new(|| Mutex::new(Some(RedisServer::new(ServerType::Tcp { tls: true }))));

static SHARED_SERVER_ADDRESS: Lazy<ConnectionAddr> = Lazy::new(|| {
    SHARED_SERVER
        .lock()
        .unwrap()
        .as_ref()
        .unwrap()
        .get_client_addr()
});

static SHARED_TLS_SERVER_ADDRESS: Lazy<ConnectionAddr> = Lazy::new(|| {
    SHARED_TLS_SERVER
        .lock()
        .unwrap()
        .as_ref()
        .unwrap()
        .get_client_addr()
});

pub fn get_shared_server_address(use_tls: bool) -> ConnectionAddr {
    if use_tls {
        SHARED_TLS_SERVER_ADDRESS.clone()
    } else {
        SHARED_SERVER_ADDRESS.clone()
    }
}

#[ctor::dtor]
fn clean_shared_clusters() {
    if let Some(mutex) = SharedServer::get(&SHARED_SERVER) {
        drop(mutex.lock().unwrap().take());
    }
    if let Some(mutex) = SharedServer::get(&SHARED_TLS_SERVER) {
        drop(mutex.lock().unwrap().take());
    }
}

pub struct RedisServer {
    pub process: process::Child,
    tempdir: Option<tempfile::TempDir>,
    addr: redis::ConnectionAddr,
}

pub enum Module {
    Json,
}

pub fn get_listener_on_available_port() -> TcpListener {
    let addr = &"127.0.0.1:0".parse::<SocketAddr>().unwrap().into();
    let socket = Socket::new(Domain::IPV4, Type::STREAM, None).unwrap();
    socket.set_reuse_address(true).unwrap();
    socket.bind(addr).unwrap();
    socket.listen(1).unwrap();
    TcpListener::from(socket)
}

pub fn get_available_port() -> u16 {
    // this is technically a race but we can't do better with
    // the tools that redis gives us :(
    let listener = get_listener_on_available_port();
    listener.local_addr().unwrap().port()
}

impl RedisServer {
    pub fn new(server_type: ServerType) -> RedisServer {
        RedisServer::with_modules(server_type, &[])
    }

    pub fn with_modules(server_type: ServerType, modules: &[Module]) -> RedisServer {
        let addr = match server_type {
            ServerType::Tcp { tls } => {
                let redis_port = get_available_port();
                if tls {
                    redis::ConnectionAddr::TcpTls {
                        host: "127.0.0.1".to_string(),
                        port: redis_port,
                        insecure: true,
                        tls_params: None,
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
                    .arg(port.to_string())
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
                    tls_params: None,
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

    pub fn get_client_addr(&self) -> redis::ConnectionAddr {
        self.addr.clone()
    }

    pub fn connection_info(&self) -> redis::ConnectionInfo {
        redis::ConnectionInfo {
            addr: self.get_client_addr(),
            redis: Default::default(),
        }
    }

    pub fn stop(&mut self) {
        let _ = self.process.kill();
        let _ = self.process.wait();
        if let redis::ConnectionAddr::Unix(ref path) = self.get_client_addr() {
            fs::remove_file(path).ok();
        }
    }
}

impl Drop for RedisServer {
    fn drop(&mut self) {
        self.stop()
    }
}

fn encode_iter<W>(values: &[Value], writer: &mut W, prefix: &str) -> io::Result<()>
where
    W: io::Write,
{
    write!(writer, "{}{}\r\n", prefix, values.len())?;
    for val in values.iter() {
        encode_value(val, writer)?;
    }
    Ok(())
}

fn encode_map<W>(values: &[(Value, Value)], writer: &mut W, prefix: &str) -> io::Result<()>
where
    W: io::Write,
{
    write!(writer, "{}{}\r\n", prefix, values.len())?;
    for (k, v) in values.iter() {
        encode_value(k, writer)?;
        encode_value(v, writer)?;
    }
    Ok(())
}

pub fn encode_value<W>(value: &Value, writer: &mut W) -> io::Result<()>
where
    W: io::Write,
{
    #![allow(clippy::write_with_newline)]
    match *value {
        Value::Nil => write!(writer, "$-1\r\n"),
        Value::Int(val) => write!(writer, ":{val}\r\n"),
        Value::BulkString(ref val) => {
            write!(writer, "${}\r\n", val.len())?;
            writer.write_all(val)?;
            writer.write_all(b"\r\n")
        }
        Value::Array(ref values) => encode_iter(values, writer, "*"),
        Value::Okay => write!(writer, "+OK\r\n"),
        Value::SimpleString(ref s) => write!(writer, "+{s}\r\n"),
        Value::Map(ref values) => encode_map(values, writer, "%"),
        Value::Attribute {
            ref data,
            ref attributes,
        } => {
            encode_map(attributes, writer, "|")?;
            encode_value(data, writer)?;
            Ok(())
        }
        Value::Set(ref values) => encode_iter(values, writer, "~"),
        Value::Double(val) => write!(writer, ",{val}\r\n"),
        Value::Boolean(v) => {
            if v {
                write!(writer, "#t\r\n")
            } else {
                write!(writer, "#f\r\n")
            }
        }
        Value::VerbatimString {
            ref format,
            ref text,
        } => {
            // format is always 3 bytes
            write!(writer, "={}\r\n{}:{}\r\n", 3 + text.len(), format, text)
        }
        Value::BigNumber(ref val) => write!(writer, "({val}\r\n"),
        Value::Push { ref kind, ref data } => {
            write!(writer, ">{}\r\n+{kind}\r\n", data.len() + 1)?;
            for val in data.iter() {
                encode_value(val, writer)?;
            }
            Ok(())
        }
        Value::ServerError(ref err) => write!(writer, "server-error({err})\r\n"),
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
    let ext_file = tempdir.path().join("openssl.cnf");

    fn make_key<S: AsRef<std::ffi::OsStr>>(name: S, size: usize) {
        process::Command::new("openssl")
            .arg("genrsa")
            .arg("-out")
            .arg(name)
            .arg(format!("{size}"))
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

    // Build x509v3 extensions file with SAN for 127.0.0.1
    fs::write(&ext_file, b"keyUsage = digitalSignature, keyEncipherment\nsubjectAltName = IP:127.0.0.1,DNS:localhost")
        .expect("failed to create x509v3 extensions file");

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
        .arg("-extfile")
        .arg(&ext_file)
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

impl TlsFilePaths {
    pub fn read_ca_cert_as_bytes(&self) -> Vec<u8> {
        fs::read(&self.ca_crt).expect("Failed to read CA certificate file")
    }
}

pub async fn wait_for_server_to_become_ready(server_address: &ConnectionAddr) {
    let millisecond = Duration::from_millis(1);
    let mut retries = 0;
    let client = redis::Client::open(redis::ConnectionInfo {
        addr: server_address.clone(),
        redis: RedisConnectionInfo::default(),
    })
    .unwrap();
    loop {
        match client
            .get_multiplexed_async_connection(GlideConnectionOptions::default())
            .await
        {
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
                while con.send_packed_command(&redis::cmd("PING")).await.is_err() {
                    tokio::time::sleep(Duration::from_millis(10)).await;
                }
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

pub fn get_address_info(address: &ConnectionAddr) -> NodeAddress {
    let mut address_info = NodeAddress::new();
    match address {
        ConnectionAddr::Tcp(host, port) => {
            address_info.host = host.to_string().into();
            address_info.port = *port as u32;
        }
        ConnectionAddr::TcpTls {
            host,
            port,
            insecure: _,
            tls_params: _,
        } => {
            address_info.host = host.to_string().into();
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

pub async fn send_get(client: &mut Client, key: &str) -> RedisResult<Value> {
    let mut get_command = redis::Cmd::new();
    get_command.arg("GET").arg(key);
    client.send_command(&get_command, None).await
}

pub async fn send_set_and_get(mut client: Client, key: String) {
    const VALUE_LENGTH: usize = 10;
    let value = generate_random_string(VALUE_LENGTH);

    let mut set_command = redis::Cmd::new();
    set_command.arg("SET").arg(key.as_str()).arg(value.clone());
    let set_result = client.send_command(&set_command, None).await.unwrap();
    let mut get_command = redis::Cmd::new();
    get_command.arg("GET").arg(key);
    let get_result = client.send_command(&get_command, None).await.unwrap();

    assert_eq!(set_result, Value::Okay);
    assert_eq!(get_result, Value::BulkString(value.into_bytes()));
}

pub struct TestBasics {
    pub server: Option<RedisServer>,
    pub client: StandaloneClient,
    pub push_receiver: mpsc::UnboundedReceiver<PushInfo>,
}

fn convert_to_protobuf_protocol(
    protocol: redis::ProtocolVersion,
) -> connection_request::ProtocolVersion {
    match protocol {
        redis::ProtocolVersion::RESP2 => connection_request::ProtocolVersion::RESP2,
        redis::ProtocolVersion::RESP3 => connection_request::ProtocolVersion::RESP3,
    }
}

fn set_connection_info_to_connection_request(
    connection_info: RedisConnectionInfo,
    connection_request: &mut connection_request::ConnectionRequest,
) {
    connection_request.protocol = convert_to_protobuf_protocol(connection_info.protocol).into();
    if connection_info.password.is_some() {
        connection_request.authentication_info =
            protobuf::MessageField(Some(Box::new(AuthenticationInfo {
                password: connection_info.password.unwrap().into(),
                username: connection_info.username.unwrap_or_default().into(),
                iam_credentials: protobuf::MessageField::none(),
                ..Default::default()
            })));
    }
}

pub async fn repeat_try_create<T, Fut>(f: impl Fn() -> Fut) -> T
where
    Fut: Future<Output = Option<T>>,
{
    for _ in 0..500 {
        if let Some(value) = f().await {
            return value;
        }
        tokio::time::sleep(std::time::Duration::from_millis(5)).await;
    }
    panic!("Couldn't create object");
}

pub async fn setup_acl(addr: &ConnectionAddr, connection_info: &RedisConnectionInfo) {
    let client = redis::Client::open(redis::ConnectionInfo {
        addr: addr.clone(),
        redis: RedisConnectionInfo::default(),
    })
    .unwrap();
    let mut connection = repeat_try_create(|| async {
        client
            .get_multiplexed_async_connection(GlideConnectionOptions::default())
            .await
            .ok()
    })
    .await;

    let password = connection_info.password.clone().unwrap();
    let username = connection_info
        .username
        .clone()
        .unwrap_or_else(|| "default".to_string());
    let mut cmd = redis::cmd("ACL");
    cmd.arg("SETUSER")
        .arg(username)
        .arg("on")
        .arg("allkeys")
        .arg("+@all")
        .arg(format!(">{password}"));
    connection.send_packed_command(&cmd).await.unwrap();
}

#[derive(Eq, PartialEq, Default, Clone, Debug)]
pub enum ClusterMode {
    #[default]
    Disabled,
    Enabled,
}

pub fn create_connection_request(
    addresses: &[ConnectionAddr],
    configuration: &TestConfiguration,
) -> connection_request::ConnectionRequest {
    let addresses_info = addresses.iter().map(get_address_info).collect();
    let mut connection_request = connection_request::ConnectionRequest::new();
    connection_request.addresses = addresses_info;
    connection_request.database_id = configuration.database_id;
    connection_request.tls_mode = if configuration.use_tls {
        connection_request::TlsMode::InsecureTls
    } else {
        connection_request::TlsMode::NoTls
    }
    .into();
    connection_request.cluster_mode_enabled = ClusterMode::Enabled == configuration.cluster_mode;
    if let Some(request_timeout) = configuration.request_timeout {
        connection_request.request_timeout = request_timeout;
    }
    if let Some(strategy) = configuration.read_from {
        connection_request.read_from = strategy.into()
    }

    connection_request.connection_retry_strategy =
        protobuf::MessageField::from_option(configuration.connection_retry_strategy.clone());
    set_connection_info_to_connection_request(
        configuration.connection_info.clone().unwrap_or_default(),
        &mut connection_request,
    );

    if let Some(client_name) = &configuration.client_name {
        connection_request.client_name = client_name.deref().into();
    }

    if let Some(client_az) = &configuration.client_az {
        connection_request.client_az = client_az.deref().into();
    }
    connection_request.lazy_connect = configuration.lazy_connect;
    connection_request.protocol = configuration.protocol.into();
    connection_request
}

#[derive(Default, Clone, Debug)]
pub struct TestConfiguration {
    pub use_tls: bool,
    pub connection_retry_strategy: Option<connection_request::ConnectionRetryStrategy>,
    pub connection_info: Option<RedisConnectionInfo>,
    pub cluster_mode: ClusterMode,
    pub request_timeout: Option<u32>,
    pub shared_server: bool,
    pub read_from: Option<connection_request::ReadFrom>,
    pub database_id: u32,
    pub client_name: Option<String>,
    pub client_az: Option<String>,
    pub protocol: ProtocolVersion,
    pub lazy_connect: bool,
}

pub(crate) async fn setup_test_basics_internal(configuration: &TestConfiguration) -> TestBasics {
    let server = if !configuration.shared_server {
        Some(RedisServer::new(ServerType::Tcp {
            tls: configuration.use_tls,
        }))
    } else {
        None
    };
    let connection_addr = if !configuration.shared_server {
        server.as_ref().unwrap().get_client_addr()
    } else {
        get_shared_server_address(configuration.use_tls)
    };

    if let Some(redis_connection_info) = &configuration.connection_info
        && redis_connection_info.password.is_some()
    {
        assert!(!configuration.shared_server);
        setup_acl(&connection_addr, redis_connection_info).await;
    }
    let mut connection_request = create_connection_request(&[connection_addr], configuration);
    connection_request.cluster_mode_enabled = false;
    connection_request.protocol = configuration.protocol.into();
    let (push_sender, push_receiver) = tokio::sync::mpsc::unbounded_channel();
    let client =
        StandaloneClient::create_client(connection_request.into(), Some(push_sender), None)
            .await
            .unwrap();

    TestBasics {
        server,
        client,
        push_receiver,
    }
}

pub async fn setup_test_basics(use_tls: bool) -> TestBasics {
    setup_test_basics_internal(&TestConfiguration {
        use_tls,
        ..Default::default()
    })
    .await
}

#[cfg(test)]
#[ctor::ctor]
fn init() {
    logger_core::init(Some(logger_core::Level::Debug), None);

    // This needs to be done before any TLS connections are made
    let _ = rustls::crypto::CryptoProvider::install_default(
        rustls::crypto::aws_lc_rs::default_provider(),
    );
}

pub async fn kill_connection(client: &mut impl glide_core::client::GlideClientForTests) {
    let mut client_kill_cmd = redis::cmd("CLIENT");
    client_kill_cmd.arg("KILL").arg("SKIPME").arg("NO");

    let _ = client
        .send_command(
            &client_kill_cmd,
            Some(RoutingInfo::MultiNode((
                MultipleNodeRoutingInfo::AllNodes,
                Some(redis::cluster_routing::ResponsePolicy::AllSucceeded),
            ))),
        )
        .await
        .unwrap();
}

pub async fn kill_connection_for_route(
    client: &mut impl glide_core::client::GlideClientForTests,
    route: RoutingInfo,
) {
    let mut client_kill_cmd = redis::cmd("CLIENT");
    client_kill_cmd.arg("KILL").arg("SKIPME").arg("NO");

    let _ = client
        .send_command(&client_kill_cmd, Some(route))
        .await
        .unwrap();
}

pub enum BackingServer {
    Standalone(Option<RedisServer>),
    Cluster(Option<cluster::RedisCluster>),
}

/// Get the server version from a client connection
pub async fn get_server_version(
    client: &mut impl glide_core::client::GlideClientForTests,
) -> (u16, u16, u16) {
    let mut info_cmd = redis::cmd("INFO");
    info_cmd.arg("SERVER");

    let info_result = client.send_command(&info_cmd, None).await.unwrap();
    let info_string = match info_result {
        Value::BulkString(bytes) => String::from_utf8_lossy(&bytes).to_string(),
        Value::VerbatimString { text, .. } => text,
        Value::Map(node_results) => {
            // In cluster mode, INFO returns a map of node -> info string
            // We just need to get the version from any node (they should all be the same)
            if let Some((_, node_info)) = node_results.first() {
                match node_info {
                    Value::VerbatimString { text, .. } => text.clone(),
                    Value::BulkString(bytes) => String::from_utf8_lossy(bytes).to_string(),
                    _ => panic!(
                        "Unexpected node info type in cluster INFO response: {:?}",
                        node_info
                    ),
                }
            } else {
                panic!("Empty cluster INFO response");
            }
        }
        _ => panic!("Unexpected INFO response type: {:?}", info_result),
    };

    // Parse the INFO response to extract version
    // First try to find valkey_version, then fall back to redis_version
    for line in info_string.lines() {
        if let Some(version_str) = line.strip_prefix("valkey_version:") {
            return parse_version_string(version_str);
        }
    }

    // If no valkey_version found, look for redis_version
    for line in info_string.lines() {
        if let Some(version_str) = line.strip_prefix("redis_version:") {
            return parse_version_string(version_str);
        }
    }

    panic!("Could not find version information in INFO response");
}

/// Parse a version string like "8.1.3" into (8, 1, 3)
fn parse_version_string(version_str: &str) -> (u16, u16, u16) {
    let parts: Vec<&str> = version_str.split('.').collect();
    if parts.len() >= 3 {
        let major = parts[0].parse().unwrap_or(0);
        let minor = parts[1].parse().unwrap_or(0);
        let patch = parts[2].parse().unwrap_or(0);
        (major, minor, patch)
    } else {
        panic!("Invalid version format: {}", version_str);
    }
}

/// Check if the server version is greater than or equal to the specified version
pub async fn version_greater_or_equal(
    client: &mut impl glide_core::client::GlideClientForTests,
    version: &str,
) -> bool {
    let (major, minor, patch) = get_server_version(client).await;
    let server_version = Versioning::new(format!("{major}.{minor}.{patch}")).unwrap();
    let compared_version = Versioning::new(version).unwrap();
    server_version >= compared_version
}

/// Extract client ID from CLIENT INFO response string
pub fn extract_client_id(client_info: &str) -> Option<String> {
    client_info
        .split_whitespace()
        .find(|part| part.starts_with("id="))
        .and_then(|id_part| id_part.strip_prefix("id="))
        .map(|id| id.to_string())
}
