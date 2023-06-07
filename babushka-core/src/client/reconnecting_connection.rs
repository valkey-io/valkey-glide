use crate::connection_request::{AddressInfo, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures_intrusive::sync::ManualResetEvent;
use logger_core::{log_debug, log_trace};
use redis::aio::{ConnectionLike, MultiplexedConnection};
use redis::{RedisConnectionInfo, RedisError, RedisResult};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Mutex;
use tokio::task;
use tokio_retry::Retry;

use super::{get_connection_info, run_with_timeout, DEFAULT_CONNECTION_ATTEMPT_TIMEOUT};

/// The object that is used in order to recreate a connection after a disconnect.
struct ConnectionBackend {
    /// This signal is reset when a connection disconnects, and set when a new `ConnectionState` has been set with a `Connected` state.
    connection_available_signal: ManualResetEvent,
    /// Information needed in order to create a new connection.
    connection_info: redis::Client,
    /// Once this flag is set, the internal connection needs no longer try to reconnect to the server, because all the outer clients were dropped.
    client_dropped_flagged: AtomicBool,
}

/// State of the current connection. Allows the user to use a connection only when a reconnect isn't in progress or has failed.
enum ConnectionState {
    /// A connection has been established.
    Connected(MultiplexedConnection),
    /// There's a reconnection effort on the way, no need to try reconnecting again.
    Reconnecting,
}

struct InnerReconnectingConnection {
    state: Mutex<ConnectionState>,
    backend: ConnectionBackend,
}

/// The separation between an inner and outer connection is because the outer connection is clonable, and the inner connection needs to be dropped when no outer connection exists.
struct DropWrapper(Arc<InnerReconnectingConnection>);

impl Drop for DropWrapper {
    fn drop(&mut self) {
        self.0
            .backend
            .client_dropped_flagged
            .store(true, Ordering::Relaxed);
    }
}

#[derive(Clone)]
pub(super) struct ReconnectingConnection {
    /// All of the connection's clones point to the same internal wrapper, which will be dropped only once,
    /// when all of the clones have been dropped.
    inner: Arc<DropWrapper>,
}

async fn get_multiplexed_connection(client: &redis::Client) -> RedisResult<MultiplexedConnection> {
    run_with_timeout(
        DEFAULT_CONNECTION_ATTEMPT_TIMEOUT,
        client.get_multiplexed_async_connection(),
    )
    .await
}

async fn try_create_connection(
    connection_backend: ConnectionBackend,
    retry_strategy: RetryStrategy,
) -> RedisResult<ReconnectingConnection> {
    let client = &connection_backend.connection_info;
    let action = || {
        log_debug("connection creation", "Creating multiplexed connection");
        get_multiplexed_connection(client)
    };

    let connection = Retry::spawn(retry_strategy.get_iterator(), action).await?;
    Ok(ReconnectingConnection {
        inner: Arc::new(DropWrapper(Arc::new(InnerReconnectingConnection {
            state: Mutex::new(ConnectionState::Connected(connection)),
            backend: connection_backend,
        }))),
    })
}

fn get_client(
    address: &AddressInfo,
    tls_mode: TlsMode,
    redis_connection_info: redis::RedisConnectionInfo,
) -> RedisResult<redis::Client> {
    redis::Client::open(get_connection_info(
        address,
        tls_mode,
        redis_connection_info,
    ))
}

/// This iterator isn't exposed to users, and can't be configured.
fn internal_retry_iterator() -> impl Iterator<Item = Duration> {
    const MAX_DURATION: Duration = Duration::from_secs(5);
    crate::retry_strategies::get_exponential_backoff(
        crate::retry_strategies::EXPONENT_BASE,
        crate::retry_strategies::FACTOR,
        crate::retry_strategies::NUMBER_OF_RETRIES,
    )
    .get_iterator()
    .chain(std::iter::repeat(MAX_DURATION))
}

impl ReconnectingConnection {
    pub(super) async fn new(
        address: &AddressInfo,
        connection_retry_strategy: RetryStrategy,
        redis_connection_info: RedisConnectionInfo,
        tls_mode: TlsMode,
    ) -> RedisResult<Self> {
        log_debug(
            "connection creation",
            format!("Attempting connection to {address}"),
        );

        let client = ConnectionBackend {
            connection_info: get_client(address, tls_mode, redis_connection_info)?,
            connection_available_signal: ManualResetEvent::new(true),
            client_dropped_flagged: AtomicBool::new(false),
        };
        let connection = try_create_connection(client, connection_retry_strategy).await?;
        log_debug(
            "connection creation",
            format!("Connection to {address} created"),
        );
        Ok(connection)
    }

    async fn get_connection(&self) -> Result<MultiplexedConnection, RedisError> {
        loop {
            self.inner
                .0
                .backend
                .connection_available_signal
                .wait()
                .await;
            {
                let guard = self.inner.0.state.lock().await;
                if let ConnectionState::Connected(connection) = &*guard {
                    return Ok(connection.clone());
                }
            };
        }
    }

    async fn reconnect(&self) {
        {
            let mut guard = self.inner.0.state.lock().await;
            match &*guard {
                ConnectionState::Connected(_) => {
                    self.inner.0.backend.connection_available_signal.reset();
                }
                _ => {
                    log_trace("reconnect", "already started");
                    // exit early - if reconnection already started or failed, there's nothing else to do.
                    return;
                }
            };
            *guard = ConnectionState::Reconnecting;
        };
        log_debug("reconnect", "starting");
        let inner_connection_clone = self.inner.0.clone();
        // The reconnect task is spawned instead of awaited here, so that the reconnect attempt will continue in the
        // background, regardless of whether the calling task is dropped or not.
        task::spawn(async move {
            let client = &inner_connection_clone.backend.connection_info;
            for sleep_duration in internal_retry_iterator() {
                if inner_connection_clone
                    .backend
                    .client_dropped_flagged
                    .load(Ordering::Relaxed)
                {
                    log_trace(
                        "ReconnectingConnection",
                        "reconnect stopped after client was dropped",
                    );
                    // Client was dropped, reconnection attempts can stop
                    return;
                }
                log_debug("connection creation", "Creating multiplexed connection");
                match get_multiplexed_connection(client).await {
                    Ok(connection) => {
                        let mut guard = inner_connection_clone.state.lock().await;
                        log_debug("reconnect", "completed succesfully");
                        inner_connection_clone
                            .backend
                            .connection_available_signal
                            .set();
                        *guard = ConnectionState::Connected(connection);
                        return;
                    }
                    Err(_) => tokio::time::sleep(sleep_duration).await,
                }
            }
        });
    }

    pub(super) async fn send_packed_command(
        &mut self,
        cmd: &redis::Cmd,
    ) -> redis::RedisResult<redis::Value> {
        log_trace("ReconnectingConnection", "sending command");
        let mut connection = self.get_connection().await?;
        let result = connection.send_packed_command(cmd).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
                self.reconnect().await;
                Err(err)
            }
            _ => result,
        }
    }

    pub(super) async fn send_packed_commands(
        &mut self,
        cmd: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> redis::RedisResult<Vec<redis::Value>> {
        let mut connection = self.get_connection().await?;
        let result = connection.send_packed_commands(cmd, offset, count).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
                self.reconnect().await;
                Err(err)
            }
            _ => result,
        }
    }

    pub(super) fn get_db(&self) -> i64 {
        let guard = self.inner.0.state.blocking_lock();
        match &*guard {
            ConnectionState::Connected(connection) => connection.get_db(),
            _ => -1,
        }
    }
}
