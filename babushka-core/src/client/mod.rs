use crate::connection_request::{
    AddressInfo, AuthenticationInfo, ConnectionRequest, ReadFromReplicaStrategy, TlsMode,
};
use futures::FutureExt;
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

pub(super) fn get_port(address: &AddressInfo) -> u16 {
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
) -> redis::RedisConnectionInfo {
    match authentication_info {
        Some(info) => redis::RedisConnectionInfo {
            db: 0,
            username: chars_to_string_option(&info.username),
            password: chars_to_string_option(&info.password),
        },
        None => redis::RedisConnectionInfo::default(),
    }
}

pub(super) fn get_connection_info(
    address: &AddressInfo,
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
    Cluster {
        client: ClusterConnection,
        read_from_replicas: bool,
    },
}

#[derive(Clone)]
pub struct Client {
    internal_client: ClientWrapper,
    response_timeout: Duration,
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
        run_with_timeout(self.response_timeout, async {
            match self.internal_client {
                ClientWrapper::Standalone(ref mut client) => client.send_packed_command(cmd).await,

                ClientWrapper::Cluster {
                    ref mut client,
                    read_from_replicas,
                } => {
                    let routing =
                        routing.or_else(|| RoutingInfo::for_routable(cmd, read_from_replicas));
                    client.send_packed_command(cmd, routing).await
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

                ClientWrapper::Cluster {
                    ref mut client,
                    read_from_replicas: _,
                } => {
                    let route = match routing {
                        Some(RoutingInfo::SingleNode(SingleNodeRoutingInfo::SpecificNode(
                            route,
                        ))) => Some(route),
                        _ => None,
                    };
                    run_with_timeout(
                        self.response_timeout,
                        client.send_packed_commands(cmd, offset, count, route),
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
) -> RedisResult<(redis::cluster_async::ClusterConnection, bool)> {
    // TODO - implement timeout for each connection attempt
    let tls_mode = request.tls_mode.enum_value_or(TlsMode::NoTls);
    let redis_connection_info = get_redis_connection_info(request.authentication_info.0);
    let initial_nodes = request
        .addresses
        .into_iter()
        .map(|address| get_connection_info(&address, tls_mode, redis_connection_info.clone()))
        .collect();
    let read_from_replica_strategy = request
        .read_from_replica_strategy
        .enum_value()
        .unwrap_or(ReadFromReplicaStrategy::AlwaysFromPrimary);
    let read_from_replicas = !matches!(
        read_from_replica_strategy,
        ReadFromReplicaStrategy::AlwaysFromPrimary,
    ); // TODO - implement different read from replica strategies.
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
    Ok((client.get_async_connection().await?, read_from_replicas))
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

impl Client {
    pub async fn new(request: ConnectionRequest) -> Result<Self, ConnectionError> {
        const DEFAULT_CLIENT_CREATION_TIMEOUT: Duration = Duration::from_millis(2500);

        let response_timeout = to_duration(request.response_timeout, DEFAULT_RESPONSE_TIMEOUT);
        let total_connection_timeout = to_duration(
            request.client_creation_timeout,
            DEFAULT_CLIENT_CREATION_TIMEOUT,
        );
        tokio::time::timeout(total_connection_timeout, async move {
            let internal_client = if request.cluster_mode_enabled {
                let (client, read_from_replicas) = create_cluster_client(request)
                    .await
                    .map_err(ConnectionError::Cluster)?;
                ClientWrapper::Cluster {
                    client,
                    read_from_replicas,
                }
            } else {
                ClientWrapper::Standalone(
                    StandaloneClient::create_client(request)
                        .await
                        .map_err(ConnectionError::Standalone)?,
                )
            };

            Ok(Self {
                internal_client,
                response_timeout,
            })
        })
        .await
        .map_err(|_| ConnectionError::Timeout)
        .and_then(|res| res)
    }
}
