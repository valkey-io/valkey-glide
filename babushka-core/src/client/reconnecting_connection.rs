use crate::connection_request::{AddressInfo, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures_intrusive::sync::ManualResetEvent;
use logger_core::{log_debug, log_trace};
use redis::aio::MultiplexedConnection;
use redis::{RedisConnectionInfo, RedisError, RedisResult};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Duration;
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

#[derive(Clone)]
pub(super) struct ReconnectingConnection {
    inner: Arc<InnerReconnectingConnection>,
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
        inner: Arc::new(InnerReconnectingConnection {
            state: Mutex::new(ConnectionState::Connected(connection)),
            backend: connection_backend,
        }),
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
    ) -> RedisResult<ReconnectingConnection> {
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

    pub(super) fn is_dropped(&self) -> bool {
        self.inner
            .backend
            .client_dropped_flagged
            .load(Ordering::Relaxed)
    }

    pub(super) fn mark_as_dropped(&self) {
        self.inner
            .backend
            .client_dropped_flagged
            .store(true, Ordering::Relaxed)
    }

    pub(super) async fn try_get_connection(&self) -> Option<MultiplexedConnection> {
        let guard = self.inner.state.lock().unwrap();
        if let ConnectionState::Connected(connection) = &*guard {
            Some(connection.clone())
        } else {
            None
        }
    }

    pub(super) async fn get_connection(&self) -> Result<MultiplexedConnection, RedisError> {
        loop {
            self.inner.backend.connection_available_signal.wait().await;
            if let Some(connection) = self.try_get_connection().await {
                return Ok(connection);
            }
        }
    }

    pub(super) fn reconnect(&self) {
        {
            let mut guard = self.inner.state.lock().unwrap();
            match &*guard {
                ConnectionState::Connected(_) => {
                    self.inner.backend.connection_available_signal.reset();
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
        let connection_clone = self.clone();
        // The reconnect task is spawned instead of awaited here, so that the reconnect attempt will continue in the
        // background, regardless of whether the calling task is dropped or not.
        task::spawn(async move {
            let client = &connection_clone.inner.backend.connection_info;
            for sleep_duration in internal_retry_iterator() {
                if connection_clone.is_dropped() {
                    log_debug(
                        "ReconnectingConnection",
                        "reconnect stopped after client was dropped",
                    );
                    // Client was dropped, reconnection attempts can stop
                    return;
                }
                log_debug("connection creation", "Creating multiplexed connection");
                match get_multiplexed_connection(client).await {
                    Ok(connection) => {
                        {
                            let mut guard = connection_clone.inner.state.lock().unwrap();
                            log_debug("reconnect", "completed succesfully");
                            connection_clone
                                .inner
                                .backend
                                .connection_available_signal
                                .set();
                            *guard = ConnectionState::Connected(connection);
                        }
                        return;
                    }
                    Err(_) => tokio::time::sleep(sleep_duration).await,
                }
            }
        });
    }
}
