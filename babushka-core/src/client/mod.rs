use crate::connection_request::{AddressInfo, AuthenticationInfo, ConnectionRequest, TlsMode};
pub use client_cmd::ClientCMD;
use futures::FutureExt;
use redis::cluster_async::ClusterConnection;
use redis::{
    aio::{ConnectionLike, ConnectionManager, MultiplexedConnection},
    RedisResult,
};
use std::io;
use std::time::Duration;
mod client_cmd;
mod reconnecting_connection;

pub trait BabushkaClient: ConnectionLike + Send + Clone {}

impl BabushkaClient for MultiplexedConnection {}
impl BabushkaClient for ConnectionManager {}
impl BabushkaClient for ClusterConnection {}
impl BabushkaClient for Client {}

pub const DEFAULT_RESPONSE_TIMEOUT: Duration = Duration::from_millis(250);
pub const DEFAULT_CONNECTION_ATTEMPT_TIMEOUT: Duration = Duration::from_millis(250);

pub(super) fn get_port(address: &AddressInfo) -> u16 {
    const DEFAULT_PORT: u16 = 6379;
    if address.port == 0 {
        DEFAULT_PORT
    } else {
        address.port as u16
    }
}

pub(super) fn string_to_option(str: String) -> Option<String> {
    if str.is_empty() {
        None
    } else {
        Some(str)
    }
}

pub(super) fn get_redis_connection_info(
    authentication_info: Option<Box<AuthenticationInfo>>,
) -> redis::RedisConnectionInfo {
    match authentication_info {
        Some(info) => redis::RedisConnectionInfo {
            db: 0,
            username: string_to_option(info.username),
            password: string_to_option(info.password),
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
            host: address.host.clone(),
            port: get_port(address),
            insecure: tls_mode == TlsMode::InsecureTls,
        }
    } else {
        redis::ConnectionAddr::Tcp(address.host.clone(), get_port(address))
    };
    redis::ConnectionInfo {
        addr,
        redis: redis_connection_info,
    }
}

#[derive(Clone)]
pub enum ClientWrapper {
    CMD(ClientCMD),
    CME(ClusterConnection),
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

impl ConnectionLike for Client {
    fn req_packed_command<'a>(
        &'a mut self,
        cmd: &'a redis::Cmd,
    ) -> redis::RedisFuture<'a, redis::Value> {
        run_with_timeout(self.response_timeout, async {
            match self.internal_client {
                ClientWrapper::CMD(ref mut client) => client.send_packed_command(cmd).await,

                ClientWrapper::CME(ref mut client) => client.req_packed_command(cmd).await,
            }
        })
        .boxed()
    }

    fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> redis::RedisFuture<'a, Vec<redis::Value>> {
        (async move {
            match self.internal_client {
                ClientWrapper::CMD(ref mut client) => {
                    client.send_packed_commands(cmd, offset, count).await
                }

                ClientWrapper::CME(ref mut client) => {
                    run_with_timeout(
                        self.response_timeout,
                        client.req_packed_commands(cmd, offset, count),
                    )
                    .await
                }
            }
        })
        .boxed()
    }

    fn get_db(&self) -> i64 {
        match self.internal_client {
            ClientWrapper::CMD(ref client) => client.get_db(),
            ClientWrapper::CME(ref client) => client.get_db(),
        }
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
    let tls_mode = request.tls_mode.enum_value_or(TlsMode::NoTls);
    let redis_connection_info = get_redis_connection_info(request.authentication_info.0);
    let initial_nodes = request
        .addresses
        .into_iter()
        .map(|address| get_connection_info(&address, tls_mode, redis_connection_info.clone()))
        .collect();
    let mut builder = redis::cluster::ClusterClientBuilder::new(initial_nodes);
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

impl Client {
    pub async fn new(request: ConnectionRequest) -> RedisResult<Self> {
        const DEFAULT_CLIENT_CREATION_TIMEOUT: Duration = Duration::from_millis(2500);

        let response_timeout = to_duration(request.response_timeout, DEFAULT_RESPONSE_TIMEOUT);
        let total_connection_timeout = to_duration(
            request.client_creation_timeout,
            DEFAULT_CLIENT_CREATION_TIMEOUT,
        );
        run_with_timeout(total_connection_timeout, async move {
            let internal_client = if request.cluster_mode_enabled {
                ClientWrapper::CME(create_cluster_client(request).await?)
            } else {
                ClientWrapper::CMD(ClientCMD::create_client(request).await?)
            };

            Ok(Self {
                internal_client,
                response_timeout,
            })
        })
        .await
    }
}
