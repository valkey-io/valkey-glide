use crate::connection_request::{AddressInfo, ConnectionRequest, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures::FutureExt;
use futures_intrusive::sync::ManualResetEvent;
use logger_core::log_trace;
use redis::RedisError;
use redis::{
    aio::{ConnectionLike, ConnectionManager, MultiplexedConnection},
    RedisResult,
};
use std::io;
use std::sync::Arc;
use tokio::sync::Mutex;
use tokio_retry::Retry;

pub trait BabushkaClient: ConnectionLike + Send + Clone {}

impl BabushkaClient for MultiplexedConnection {}
impl BabushkaClient for ConnectionManager {}
impl BabushkaClient for ClientCMD {}

/// The object that is used in order to recreate a connection after a disconnect.
struct ConnectionBackend {
    /// This signal is reset when a connection disconnects, and set when a new `ConnectionState` has been set with either a `Connected` or a `Disconnected` state.
    /// Clone of the connection who experience the disconnect can wait on the signal in order to be notified when the new connection state is established.
    connection_available_signal: ManualResetEvent,
    /// Information needed in order to create a new connection.
    connection_info: redis::Client,
}

/// State of the current connection. Allows the user to use a connection only when a reconnect isn't in progress or has failed.
enum ConnectionState {
    /// A connection has been made, and hasn't disconnected yet.
    Connected(MultiplexedConnection, Arc<ConnectionBackend>),
    /// There's a reconnection effort on the way, no need to try reconnecting again.
    Reconnecting(Arc<ConnectionBackend>),
    /// The connection has been disconnected, and reconnection efforts have exhausted all available retries.
    Disconnected,
}

/// This allows us to safely share and replace the connection state between clones of the client.
type ConnectionWrapper = Arc<Mutex<ConnectionState>>;

#[derive(Clone)]
pub struct ClientCMD {
    /// Connection to the primary node in the client.
    primary: ConnectionWrapper,
    connection_retry_strategy: RetryStrategy,
}

fn get_port(address: &AddressInfo) -> u16 {
    const DEFAULT_PORT: u16 = 6379;
    if address.port == 0 {
        DEFAULT_PORT
    } else {
        address.port as u16
    }
}

fn get_connection_info(address: &AddressInfo, tls_mode: TlsMode) -> redis::ConnectionInfo {
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
        redis: redis::RedisConnectionInfo {
            db: 0,
            username: None,
            password: None,
        },
    }
}

fn get_client(address: &AddressInfo, tls_mode: TlsMode) -> RedisResult<redis::Client> {
    redis::Client::open(get_connection_info(address, tls_mode))
}

async fn try_create_multiplexed_connection(
    connection_backend: Arc<ConnectionBackend>,
    retry_strategy: RetryStrategy,
) -> RedisResult<MultiplexedConnection> {
    let client = &connection_backend.connection_info;
    let action = || client.get_multiplexed_async_connection();

    Retry::spawn(retry_strategy.get_iterator(), action).await
}

async fn try_create_connection(
    connection_backend: Arc<ConnectionBackend>,
    retry_strategy: RetryStrategy,
) -> RedisResult<ConnectionWrapper> {
    let connection =
        try_create_multiplexed_connection(connection_backend.clone(), retry_strategy).await?;
    Ok(Arc::new(Mutex::new(ConnectionState::Connected(
        connection,
        connection_backend,
    ))))
}

impl ClientCMD {
    pub async fn create_client(connection_request: ConnectionRequest) -> RedisResult<Self> {
        let address = connection_request.addresses.first().unwrap();
        log_trace(
            "client creation",
            format!("Connection to {address} created"),
        );

        let retry_strategy = RetryStrategy::new(&connection_request.connection_retry_strategy.0);
        let client = Arc::new(ConnectionBackend {
            connection_info: get_client(
                address,
                connection_request.tls_mode.enum_value_or(TlsMode::NoTls),
            )?,
            connection_available_signal: ManualResetEvent::new(true),
        });
        let primary = try_create_connection(client, retry_strategy.clone()).await?;
        log_trace(
            "client creation",
            format!("Connection to {address} created"),
        );
        Ok(Self {
            primary,
            // _replicas: Vec::new(),
            connection_retry_strategy: retry_strategy,
        })
    }

    fn get_disconnected_error<T>() -> Result<T, RedisError> {
        let io_error: io::Error = io::ErrorKind::BrokenPipe.into();
        Err(io_error.into())
    }

    async fn get_connection(&self) -> Result<MultiplexedConnection, RedisError> {
        loop {
            // Using a limited scope in order to release the mutex lock before waiting for notifications.
            let backend = {
                let mut guard = self.primary.lock().await;
                match &mut *guard {
                    ConnectionState::Reconnecting(backend) => backend.clone(),
                    ConnectionState::Connected(connection, _) => {
                        return Ok(connection.clone());
                    }
                    ConnectionState::Disconnected => {
                        return Self::get_disconnected_error();
                    }
                }
            };
            backend.connection_available_signal.wait().await;
        }
    }

    async fn reconnect(&self) -> Result<MultiplexedConnection, RedisError> {
        let backend = {
            let mut guard = self.primary.lock().await;
            let backend = match &*guard {
                ConnectionState::Connected(_, backend) => {
                    backend.connection_available_signal.reset();
                    backend.clone()
                }
                _ => {
                    // exit early - if reconnection already started or failed, there's nothing else to do.
                    return self.get_connection().await;
                }
            };
            *guard = ConnectionState::Reconnecting(backend.clone());
            backend
        };

        let connection_result = try_create_multiplexed_connection(
            backend.clone(),
            self.connection_retry_strategy.clone(),
        )
        .await;
        let mut guard = self.primary.lock().await;
        backend.connection_available_signal.set();
        if let Ok(connection) = connection_result {
            *guard = ConnectionState::Connected(connection.clone(), backend.clone());
            Ok(connection)
        } else {
            *guard = ConnectionState::Disconnected;
            Self::get_disconnected_error()
        }
    }
}

impl ConnectionLike for ClientCMD {
    fn req_packed_command<'a>(
        &'a mut self,
        cmd: &'a redis::Cmd,
    ) -> redis::RedisFuture<'a, redis::Value> {
        (async move {
            let mut connection = self.get_connection().await?;
            let result = connection.send_packed_command(cmd).await;
            match result {
                Ok(val) => Ok(val),
                Err(err) if err.is_connection_dropped() => {
                    let mut connection = self.reconnect().await?;
                    connection.send_packed_command(cmd).await
                }
                Err(err) => Err(err),
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
            let mut connection = self.get_connection().await?;
            let result = connection.send_packed_commands(cmd, offset, count).await;
            match result {
                Ok(val) => Ok(val),
                Err(err) if err.is_connection_dropped() => {
                    let mut connection = self.reconnect().await?;
                    connection.send_packed_commands(cmd, offset, count).await
                }
                Err(err) => Err(err),
            }
        })
        .boxed()
    }

    fn get_db(&self) -> i64 {
        let guard = self.primary.blocking_lock();
        match &*guard {
            ConnectionState::Connected(connection, _) => connection.get_db(),
            _ => -1,
        }
    }
}
