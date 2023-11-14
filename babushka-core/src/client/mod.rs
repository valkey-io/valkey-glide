use crate::connection_request::{
    AuthenticationInfo, ConnectionRequest, NodeAddress, ReadFrom, TlsMode,
};
use futures::FutureExt;
use logger_core::log_info;
use redis::cluster_async::ClusterConnection;
use redis::cluster_routing::{RoutingInfo, SingleNodeRoutingInfo};
use redis::{
    aio::{ConnectionLike, ConnectionManager, MultiplexedConnection},
    RedisResult,
};
pub use standalone_client::StandaloneClient;
use std::io;
use std::time::Duration;
mod reconnecting_connection;
mod standalone_client;

pub const HEARTBEAT_SLEEP_DURATION: Duration = Duration::from_secs(1);

pub trait BabushkaClient: ConnectionLike + Send + Clone {}

impl BabushkaClient for MultiplexedConnection {}
impl BabushkaClient for ConnectionManager {}
impl BabushkaClient for ClusterConnection {}

pub const DEFAULT_RESPONSE_TIMEOUT: Duration = Duration::from_millis(250);
pub const DEFAULT_CONNECTION_ATTEMPT_TIMEOUT: Duration = Duration::from_millis(250);
pub const INTERNAL_CONNECTION_TIMEOUT: Duration = Duration::from_millis(250);

pub(super) fn get_port(address: &NodeAddress) -> u16 {
    const DEFAULT_PORT: u16 = 6379;
    if address.port == 0 {
        DEFAULT_PORT
    } else {
        address.port as u16
    }
}

fn chars_to_string_option(chars: &::protobuf::Chars) -> Option<String> {
    if chars.is_empty() {
        None
    } else {
        Some(chars.to_string())
    }
}

pub(super) fn get_redis_connection_info(
    authentication_info: Option<Box<AuthenticationInfo>>,
    database_id: u32,
) -> redis::RedisConnectionInfo {
    match authentication_info {
        Some(info) => redis::RedisConnectionInfo {
            db: database_id as i64,
            username: chars_to_string_option(&info.username),
            password: chars_to_string_option(&info.password),
        },
        None => redis::RedisConnectionInfo {
            db: database_id as i64,
            ..Default::default()
        },
    }
}

pub(super) fn get_connection_info(
    address: &NodeAddress,
    tls_mode: TlsMode,
    redis_connection_info: redis::RedisConnectionInfo,
) -> redis::ConnectionInfo {
    let addr = if tls_mode != TlsMode::NoTls {
        redis::ConnectionAddr::TcpTls {
            host: address.host.to_string(),
            port: get_port(address),
            insecure: tls_mode == TlsMode::InsecureTls,
        }
    } else {
        redis::ConnectionAddr::Tcp(address.host.to_string(), get_port(address))
    };
    redis::ConnectionInfo {
        addr,
        redis: redis_connection_info,
    }
}

#[derive(Clone)]
pub enum ClientWrapper {
    Standalone(StandaloneClient),
    Cluster { client: ClusterConnection },
}

#[derive(Clone)]
pub struct Client {
    internal_client: ClientWrapper,
    request_timeout: Duration,
}

async fn run_with_timeout<T>(
    timeout: Duration,
    future: impl futures::Future<Output = RedisResult<T>> + Send,
) -> redis::RedisResult<T> {
    tokio::time::timeout(timeout, future)
        .await
        .map_err(|_| io::Error::from(io::ErrorKind::TimedOut).into())
        .and_then(|res| res)
}

impl Client {
    pub fn req_packed_command<'a>(
        &'a mut self,
        cmd: &'a redis::Cmd,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, redis::Value> {
        run_with_timeout(self.request_timeout, async {
            match self.internal_client {
                ClientWrapper::Standalone(ref mut client) => client.send_packed_command(cmd).await,

                ClientWrapper::Cluster { ref mut client } => {
                    let routing = routing
                        .or_else(|| RoutingInfo::for_routable(cmd))
                        .unwrap_or(RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random));
                    client.route_command(cmd, routing).await
                }
            }
        })
        .boxed()
    }

    pub fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a redis::Pipeline,
        offset: usize,
        count: usize,
        routing: Option<RoutingInfo>,
    ) -> redis::RedisFuture<'a, Vec<redis::Value>> {
        (async move {
            match self.internal_client {
                ClientWrapper::Standalone(ref mut client) => {
                    client.send_packed_commands(cmd, offset, count).await
                }

                ClientWrapper::Cluster { ref mut client } => {
                    let route = match routing {
                        Some(RoutingInfo::SingleNode(route)) => route,
                        _ => SingleNodeRoutingInfo::Random,
                    };
                    run_with_timeout(
                        self.request_timeout,
                        client.route_pipeline(cmd, offset, count, route),
                    )
                    .await
                }
            }
        })
        .boxed()
    }
}

fn to_duration(time_in_millis: u32, default: Duration) -> Duration {
    if time_in_millis > 0 {
        Duration::from_millis(time_in_millis as u64)
    } else {
        default
    }
}

async fn create_cluster_client(
    request: ConnectionRequest,
) -> RedisResult<redis::cluster_async::ClusterConnection> {
    // TODO - implement timeout for each connection attempt
    let tls_mode = request.tls_mode.enum_value_or_default();
    let redis_connection_info = get_redis_connection_info(request.authentication_info.0, 0);
    let initial_nodes = request
        .addresses
        .into_iter()
        .map(|address| get_connection_info(&address, tls_mode, redis_connection_info.clone()))
        .collect();
    let read_from = request.read_from.enum_value().unwrap_or(ReadFrom::Primary);
    let read_from_replicas = !matches!(read_from, ReadFrom::Primary,); // TODO - implement different read from replica strategies.
    let mut builder = redis::cluster::ClusterClientBuilder::new(initial_nodes)
        .connection_timeout(INTERNAL_CONNECTION_TIMEOUT);
    if read_from_replicas {
        builder = builder.read_from_replicas();
    }
    if tls_mode != TlsMode::NoTls {
        let tls = if tls_mode == TlsMode::SecureTls {
            redis::cluster::TlsMode::Secure
        } else {
            redis::cluster::TlsMode::Insecure
        };
        builder = builder.tls(tls);
    }
    let client = builder.build()?;
    client.get_async_connection().await
}

#[derive(thiserror::Error)]
pub enum ConnectionError {
    Standalone(standalone_client::StandaloneClientConnectionError),
    Cluster(redis::RedisError),
    Timeout,
}

impl std::fmt::Debug for ConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Standalone(arg0) => f.debug_tuple("Standalone").field(arg0).finish(),
            Self::Cluster(arg0) => f.debug_tuple("Cluster").field(arg0).finish(),
            Self::Timeout => write!(f, "Timeout"),
        }
    }
}

impl std::fmt::Display for ConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ConnectionError::Standalone(err) => write!(f, "{err:?}"),
            ConnectionError::Cluster(err) => write!(f, "{err}"),
            ConnectionError::Timeout => f.write_str("connection attempt timed out"),
        }
    }
}

fn format_non_zero_value(name: &'static str, value: u32) -> String {
    if value > 0 {
        format!("\n{name}: {value}")
    } else {
        String::new()
    }
}

fn sanitized_request_string(request: &ConnectionRequest) -> String {
    let addresses = request
        .addresses
        .iter()
        .map(|address| format!("{}:{}", address.host, address.port))
        .collect::<Vec<_>>()
        .join(", ");
    let tls_mode = request.tls_mode.enum_value_or_default();
    let cluster_mode = request.cluster_mode_enabled;
    let request_timeout = format_non_zero_value("response timeout", request.request_timeout);
    let database_id = format_non_zero_value("database ID", request.database_id);
    let rfr_strategy = request.read_from.enum_value_or_default();
    let connection_retry_strategy = match &request.connection_retry_strategy.0 {
        Some(strategy) => {
            format!("\nreconnect backoff strategy: number of increasing duration retries: {}, base: {}, factor: {}",
        strategy.number_of_retries, strategy.exponent_base, strategy.factor)
        }
        None => String::new(),
    };

    format!(
        "\naddresses: {addresses}\nTLS mode: {tls_mode:?}\ncluster mode: {cluster_mode}{request_timeout}\nRead from replica strategy: {rfr_strategy:?}{database_id}{connection_retry_strategy}",
    )
}

impl Client {
    pub async fn new(request: ConnectionRequest) -> Result<Self, ConnectionError> {
        const DEFAULT_CLIENT_CREATION_TIMEOUT: Duration = Duration::from_secs(10);

        log_info(
            "Connection configuration",
            sanitized_request_string(&request),
        );
        let request_timeout = to_duration(request.request_timeout, DEFAULT_RESPONSE_TIMEOUT);
        tokio::time::timeout(DEFAULT_CLIENT_CREATION_TIMEOUT, async move {
            let internal_client = if request.cluster_mode_enabled {
                let client = create_cluster_client(request)
                    .await
                    .map_err(ConnectionError::Cluster)?;
                ClientWrapper::Cluster { client }
            } else {
                ClientWrapper::Standalone(
                    StandaloneClient::create_client(request)
                        .await
                        .map_err(ConnectionError::Standalone)?,
                )
            };

            Ok(Self {
                internal_client,
                request_timeout,
            })
        })
        .await
        .map_err(|_| ConnectionError::Timeout)
        .and_then(|res| res)
    }
}
