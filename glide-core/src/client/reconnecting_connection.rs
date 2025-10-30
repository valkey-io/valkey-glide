// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use super::{NodeAddress, TlsMode};
use async_trait::async_trait;
use futures_intrusive::sync::ManualResetEvent;
use logger_core::{log_debug, log_error, log_trace, log_warn};
use redis::aio::{DisconnectNotifier, MultiplexedConnection};
use redis::{
    GlideConnectionOptions, PushInfo, RedisConnectionInfo, RedisError, RedisResult, RetryStrategy,
};
use std::fmt;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{RwLock, RwLockReadGuard};
use std::time::Duration;
use telemetrylib::Telemetry;
use tokio::sync::{Notify, mpsc};
use tokio::task;
use tokio::time::timeout;
use tokio_retry2::{Retry, RetryError};

use super::{DEFAULT_CONNECTION_TIMEOUT, run_with_timeout};

const WRITE_LOCK_ERR: &str = "Failed to acquire the write lock";
const READ_LOCK_ERR: &str = "Failed to acquire the read lock";

/// The reason behind the call to `reconnect()`
#[derive(PartialEq, Eq, Debug, Clone)]
pub enum ReconnectReason {
    /// A connection was dropped (for any reason)
    ConnectionDropped,
    /// Connection creation error
    CreateError,
}

/// The object that is used in order to recreate a connection after a disconnect.
struct ConnectionBackend {
    /// This signal is reset when a connection disconnects, and set when a new `ConnectionState` has been set with a `Connected` state.
    connection_available_signal: ManualResetEvent,
    /// Information needed in order to create a new connection.
    connection_info: RwLock<redis::Client>,
    /// Once this flag is set, the internal connection needs no longer try to reconnect to the server, because all the outer clients were dropped.
    client_dropped_flagged: AtomicBool,
}

/// State of the current connection. Allows the user to use a connection only when a reconnect isn't in progress or has failed.
enum ConnectionState {
    /// A connection has been established.
    Connected(MultiplexedConnection),
    /// There's a reconnection effort on the way, no need to try reconnecting again.
    Reconnecting,
    /// Initial state of connection when no connection was created during initialization.
    InitializedDisconnected,
}

struct InnerReconnectingConnection {
    state: Mutex<ConnectionState>,
    backend: ConnectionBackend,
}

#[derive(Clone)]
pub(super) struct ReconnectingConnection {
    inner: Arc<InnerReconnectingConnection>,
    connection_options: GlideConnectionOptions,
}

impl fmt::Debug for ReconnectingConnection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.node_address())
    }
}

async fn get_multiplexed_connection(
    client: &redis::Client,
    connection_options: &GlideConnectionOptions,
) -> RedisResult<MultiplexedConnection> {
    run_with_timeout(
        Some(
            connection_options
                .connection_timeout
                .unwrap_or(DEFAULT_CONNECTION_TIMEOUT),
        ),
        client.get_multiplexed_async_connection(connection_options.clone()),
    )
    .await
}

#[derive(Clone)]
struct TokioDisconnectNotifier {
    disconnect_notifier: Arc<Notify>,
}

#[async_trait]
impl DisconnectNotifier for TokioDisconnectNotifier {
    fn notify_disconnect(&mut self) {
        self.disconnect_notifier.notify_one();
    }

    async fn wait_for_disconnect_with_timeout(&self, max_wait: &Duration) {
        let _ = timeout(*max_wait, async {
            self.disconnect_notifier.notified().await;
        })
        .await;
    }

    fn clone_box(&self) -> Box<dyn DisconnectNotifier> {
        Box::new(self.clone())
    }
}

impl TokioDisconnectNotifier {
    fn new() -> TokioDisconnectNotifier {
        TokioDisconnectNotifier {
            disconnect_notifier: Arc::new(Notify::new()),
        }
    }
}

async fn create_connection(
    connection_backend: ConnectionBackend,
    retry_strategy: RetryStrategy,
    push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    discover_az: bool,
    connection_timeout: Duration,
) -> Result<ReconnectingConnection, (ReconnectingConnection, RedisError)> {
    let client = {
        let guard = connection_backend
            .connection_info
            .read()
            .expect(READ_LOCK_ERR);
        guard.clone()
    };

    let connection_options = GlideConnectionOptions {
        push_sender,
        disconnect_notifier: Some::<Box<dyn DisconnectNotifier>>(Box::new(
            TokioDisconnectNotifier::new(),
        )),
        discover_az,
        connection_timeout: Some(connection_timeout),
        connection_retry_strategy: Some(retry_strategy),
    };

    let action = || async {
        get_multiplexed_connection(&client, &connection_options)
            .await
            .map_err(RetryError::transient)
    };

    match Retry::spawn(retry_strategy.get_bounded_backoff_dur_iterator(), action).await {
        Ok(connection) => {
            log_debug(
                "connection creation",
                format!(
                    "Connection to {} created",
                    connection_backend
                        .get_backend_client()
                        .get_connection_info()
                        .addr
                ),
            );
            Telemetry::incr_total_connections(1);
            Ok(ReconnectingConnection {
                inner: Arc::new(InnerReconnectingConnection {
                    state: Mutex::new(ConnectionState::Connected(connection)),
                    backend: connection_backend,
                }),
                connection_options,
            })
        }
        Err(err) => {
            log_warn(
                "connection creation",
                format!(
                    "Failed connecting to {}, due to {err}",
                    connection_backend
                        .get_backend_client()
                        .get_connection_info()
                        .addr
                ),
            );
            let connection = ReconnectingConnection {
                inner: Arc::new(InnerReconnectingConnection {
                    state: Mutex::new(ConnectionState::InitializedDisconnected),
                    backend: connection_backend,
                }),
                connection_options,
            };
            connection.reconnect(ReconnectReason::CreateError);
            Err((connection, err))
        }
    }
}

// tls_params should be only set if tls_mode is SecureTls
// this should be validated before calling this function
fn get_client(
    address: &NodeAddress,
    tls_mode: TlsMode,
    redis_connection_info: redis::RedisConnectionInfo,
    tls_params: Option<redis::TlsConnParams>,
) -> redis::Client {
    let connection_info =
        super::get_connection_info(address, tls_mode, redis_connection_info, tls_params);
    redis::Client::open(connection_info).unwrap() // can unwrap, because [open] fails only on trying to convert input to ConnectionInfo, and we pass ConnectionInfo.
}

impl ConnectionBackend {
    /// Returns a read-only reference to the client's connection information
    fn get_backend_client(&self) -> RwLockReadGuard<'_, redis::Client> {
        self.connection_info.read().expect(READ_LOCK_ERR)
    }
}

impl ReconnectingConnection {
    #[allow(clippy::too_many_arguments)]
    pub(super) async fn new(
        address: &NodeAddress,
        connection_retry_strategy: RetryStrategy,
        redis_connection_info: RedisConnectionInfo,
        tls_mode: TlsMode,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
        discover_az: bool,
        connection_timeout: Duration,
        tls_params: Option<redis::TlsConnParams>,
    ) -> Result<ReconnectingConnection, (ReconnectingConnection, RedisError)> {
        log_debug(
            "connection creation",
            format!("Attempting connection to {address}"),
        );

        let connection_info = get_client(address, tls_mode, redis_connection_info, tls_params);
        let backend = ConnectionBackend {
            connection_info: RwLock::new(connection_info),
            connection_available_signal: ManualResetEvent::new(true),
            client_dropped_flagged: AtomicBool::new(false),
        };
        create_connection(
            backend,
            connection_retry_strategy,
            push_sender,
            discover_az,
            connection_timeout,
        )
        .await
    }

    pub(crate) fn node_address(&self) -> String {
        self.inner
            .backend
            .get_backend_client()
            .get_connection_info()
            .addr
            .to_string()
    }

    pub(super) fn is_dropped(&self) -> bool {
        self.inner
            .backend
            .client_dropped_flagged
            .load(Ordering::Relaxed)
    }

    pub(super) fn mark_as_dropped(&self) {
        // Update the telemetry for each connection that is dropped. A dropped connection
        // will not be re-connected, so update the telemetry here
        Telemetry::decr_total_connections(1);
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

    /// Attempt to re-connect the connection.
    ///
    /// This function spawns a task to perform the reconnection in the background
    pub(super) fn reconnect(&self, reason: ReconnectReason) {
        {
            let mut guard = self.inner.state.lock().unwrap();
            if matches!(*guard, ConnectionState::Reconnecting) {
                log_trace("reconnect", "already started");
                // exit early - if reconnection already started or failed, there's nothing else to do.
                return;
            }
            self.inner.backend.connection_available_signal.reset();
            *guard = ConnectionState::Reconnecting;
        };
        log_debug("reconnect", "starting");

        let connection_clone = self.clone();

        if reason.eq(&ReconnectReason::ConnectionDropped) {
            // Attempting to reconnect a connection that was dropped (for any reason) - update the telemetry by reducing
            // the number of opened connections by 1, it will be incremented by 1 after a successful re-connect
            Telemetry::decr_total_connections(1);
        }

        // The reconnect task is spawned instead of awaited here, so that the reconnect attempt will continue in the
        // background, regardless of whether the calling task is dropped or not.
        task::spawn(async move {
            // Get a clone of the client with the current connection info
            // updates made via update_connection_database(). This ensures reconnection uses the
            // correct database as selected by previous SELECT commands.
            let client = {
                let guard = connection_clone.inner.backend.get_backend_client();
                guard.clone()
            };

            let infinite_backoff_dur_iterator = connection_clone
                .connection_options
                .connection_retry_strategy
                .unwrap()
                .get_infinite_backoff_dur_iterator();
            for sleep_duration in infinite_backoff_dur_iterator {
                if connection_clone.is_dropped() {
                    log_debug(
                        "ReconnectingConnection",
                        "reconnect stopped after client was dropped",
                    );
                    // Client was dropped, reconnection attempts can stop
                    return;
                }
                match get_multiplexed_connection(&client, &connection_clone.connection_options)
                    .await
                {
                    Ok(mut connection) => {
                        if connection
                            .send_packed_command(&redis::cmd("PING"))
                            .await
                            .is_err()
                        {
                            tokio::time::sleep(sleep_duration).await;
                            continue;
                        }
                        {
                            let mut guard = connection_clone.inner.state.lock().unwrap();
                            log_debug("reconnect", "completed successfully");
                            connection_clone
                                .inner
                                .backend
                                .connection_available_signal
                                .set();
                            *guard = ConnectionState::Connected(connection);
                        }
                        Telemetry::incr_total_connections(1);
                        return;
                    }
                    Err(_) => tokio::time::sleep(sleep_duration).await,
                }
            }
        });
    }

    pub fn is_connected(&self) -> bool {
        !matches!(
            *self.inner.state.lock().unwrap(),
            ConnectionState::Reconnecting
        )
    }

    pub async fn wait_for_disconnect_with_timeout(&self, max_wait: &Duration) {
        // disconnect_notifier should always exists
        if let Some(disconnect_notifier) = &self.connection_options.disconnect_notifier {
            disconnect_notifier
                .wait_for_disconnect_with_timeout(max_wait)
                .await;
        } else {
            log_error("disconnect notifier", "BUG! Disconnect notifier is not set");
        }
    }

    /// Updates the password that's saved inside connection_info, that will be used in case of disconnection from the server.
    pub(crate) fn update_connection_password(&self, new_password: Option<String>) {
        let mut client = self
            .inner
            .backend
            .connection_info
            .write()
            .expect(WRITE_LOCK_ERR);
        client.update_password(new_password);
    }

    /// Updates the database ID that's saved inside connection_info, that will be used in case of disconnection from the server.
    ///
    /// This method is called when a SELECT command is successfully executed to track the current database.
    /// During reconnection, the stored database ID will be automatically used to re-select the correct
    /// database via a SELECT command during connection establishment.
    ///
    /// # Arguments
    /// * `new_database_id` - The database ID to store for future reconnections
    ///
    pub(crate) fn update_connection_database(&self, new_database_id: i64) {
        let mut client = self
            .inner
            .backend
            .connection_info
            .write()
            .expect(WRITE_LOCK_ERR);
        client.update_database(new_database_id);
    }

    /// Updates the client name that's saved inside connection_info, that will be used in case of disconnection from the server.
    pub(crate) fn update_connection_client_name(&self, new_client_name: Option<String>) {
        let mut client = self
            .inner
            .backend
            .connection_info
            .write()
            .expect(WRITE_LOCK_ERR);
        client.update_client_name(new_client_name);
    }

    /// Returns the username if one was configured during client creation. Otherwise, returns None.
    pub(crate) fn get_username(&self) -> Option<String> {
        let client = self.inner.backend.get_backend_client();
        client.get_connection_info().redis.username.clone()
    }
}
