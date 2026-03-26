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

use super::{run_with_timeout, types::DEFAULT_CONNECTION_TIMEOUT};

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

/// Token handle to the IAM token cache for use during reconnection.
///
/// Holds shared references to the cached token, its creation timestamp, and the
/// IAM configuration needed to generate a fresh token on demand. On every
/// reconnection attempt the handle returns the best available token — refreshing
/// it via SigV4 signing when the current one has expired — so the AUTH command
/// always uses valid credentials without requiring a reference back to the full
/// `IAMTokenManager`.
#[derive(Clone)]
pub struct IAMTokenHandle {
    /// Shared cached IAM token (same `Arc` owned by `IAMTokenManager`).
    pub(crate) cached_token: Arc<tokio::sync::RwLock<String>>,
    /// When the cached token was last generated or refreshed.
    pub(crate) token_created_at: Arc<tokio::sync::RwLock<tokio::time::Instant>>,
    /// IAM configuration (cluster name, region, etc.) for on-demand token generation.
    pub(crate) iam_token_state: crate::iam::IamTokenState,
}

impl IAMTokenHandle {
    /// Returns the best available token, refreshing it first if expired.
    ///
    /// If the token has expired, attempts to generate a fresh one via SigV4.
    /// On refresh failure, falls back to the existing cached token so that
    /// the password is always updated on every reconnection attempt.
    /// Returns `None` only if the cache is completely empty.
    pub(crate) async fn get_valid_token_inner(&self) -> Option<String> {
        use crate::iam::TOKEN_TTL_SECONDS;

        let is_expired = {
            let ts = self.token_created_at.read().await;
            ts.elapsed() >= std::time::Duration::from_secs(TOKEN_TTL_SECONDS)
        };

        if is_expired {
            logger_core::log_info(
                "IAM reconnect",
                "Token expired, generating a fresh token before reconnection",
            );
            match crate::iam::IAMTokenManager::generate_token_with_backoff(&self.iam_token_state)
                .await
            {
                Ok(new_token) => {
                    {
                        let mut guard = self.cached_token.write().await;
                        *guard = new_token.clone();
                    }
                    {
                        let mut ts = self.token_created_at.write().await;
                        *ts = tokio::time::Instant::now();
                    }
                    return Some(new_token);
                }
                Err(err) => {
                    logger_core::log_error(
                        "IAM reconnect",
                        format!("Failed to generate fresh IAM token, using cached token: {err}"),
                    );
                    // Fall through to return the cached (possibly expired) token
                }
            }
        }

        let guard = self.cached_token.read().await;
        let token = guard.clone();
        if token.is_empty() { None } else { Some(token) }
    }
}

#[async_trait::async_trait]
impl redis::IAMTokenProvider for IAMTokenHandle {
    async fn get_valid_token(&self) -> Option<String> {
        self.get_valid_token_inner().await
    }
}

/// The object that is used in order to recreate a connection after a disconnect.
struct ConnectionBackend {
    /// This signal is reset when a connection disconnects, and set when a new `ConnectionState` has been set with a `Connected` state.
    connection_available_signal: ManualResetEvent,
    /// Information needed in order to create a new connection.
    connection_info: RwLock<redis::Client>,
    /// Once this flag is set, the internal connection needs no longer try to reconnect to the server, because all the outer clients were dropped.
    client_dropped_flagged: AtomicBool,
    /// Optional handle to the IAM token cache for refreshing the password before reconnection.
    iam_token_handle: Option<IAMTokenHandle>,
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
    tcp_nodelay: bool,
    pubsub_synchronizer: Option<Arc<dyn crate::pubsub::PubSubSynchronizer>>,
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
        tcp_nodelay,
        pubsub_synchronizer,
        iam_token_provider: None,
    };

    // Wrap retry loop in timeout so total time respects connection_timeout
    let action = || async {
        client
            .get_multiplexed_async_connection(connection_options.clone())
            .await
            .map_err(|e| {
                // Don't retry errors that won't resolve with retries
                let is_permanent = matches!(
                    e.kind(),
                    redis::ErrorKind::AuthenticationFailed
                        | redis::ErrorKind::InvalidClientConfig
                        | redis::ErrorKind::RESP3NotSupported
                ) || e.to_string().contains("NOAUTH")
                    || e.to_string().contains("WRONGPASS");
                if is_permanent {
                    RetryError::permanent(e)
                } else {
                    RetryError::transient(e)
                }
            })
    };
    let retry_future = Retry::spawn(retry_strategy.get_bounded_backoff_dur_iterator(), action);
    let result = timeout(connection_timeout, retry_future).await;

    match result {
        Ok(Ok(connection)) => {
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
        err => {
            let err: RedisError = match err {
                Ok(Err(e)) => e,
                _ => std::io::Error::from(std::io::ErrorKind::TimedOut).into(),
            };
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
        tcp_nodelay: bool,
        pubsub_synchronizer: Option<Arc<dyn crate::pubsub::PubSubSynchronizer>>,
        iam_token_handle: Option<IAMTokenHandle>,
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
            iam_token_handle,
        };
        create_connection(
            backend,
            connection_retry_strategy,
            push_sender,
            discover_az,
            connection_timeout,
            tcp_nodelay,
            pubsub_synchronizer,
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
            let has_iam = connection_clone.inner.backend.iam_token_handle.is_some();

            // For non-IAM connections, clone the client once before the loop to preserve
            // the original reconnection behavior (password is fixed at reconnect start).
            // For IAM connections, the client is cloned inside the loop so each retry
            // picks up the freshest token written by the IAM handle.
            let static_client = if !has_iam {
                Some({
                    let guard = connection_clone.inner.backend.get_backend_client();
                    guard.clone()
                })
            } else {
                None
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

                // If IAM authentication is configured, ensure the connection uses a
                // valid token before attempting to reconnect.  If the cached token has
                // expired, a fresh one is generated on demand via SigV4 signing.
                if let Some(handle) = &connection_clone.inner.backend.iam_token_handle
                    && let Some(valid_token) = handle.get_valid_token_inner().await
                {
                    let mut client = connection_clone
                        .inner
                        .backend
                        .connection_info
                        .write()
                        .expect(WRITE_LOCK_ERR);
                    client.update_password(Some(valid_token));
                    log_debug(
                        "reconnect",
                        "Updated connection password with valid IAM token before reconnection attempt",
                    );
                }

                let client = if let Some(ref c) = static_client {
                    c.clone()
                } else {
                    // IAM path: re-read from backend to pick up the token update above
                    let guard = connection_clone.inner.backend.get_backend_client();
                    guard.clone()
                };

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

    /// Updates the username that's saved inside connection_info, that will be used in case of disconnection from the server.
    ///
    /// This method is called when an AUTH command is successfully executed with a username to track the current user.
    /// During reconnection, the stored username will be automatically used for authentication.
    ///
    /// # Arguments
    /// * `new_username` - The username to store for future reconnections (None to clear)
    ///
    pub(crate) fn update_connection_username(&self, new_username: Option<String>) {
        let mut client = self
            .inner
            .backend
            .connection_info
            .write()
            .expect(WRITE_LOCK_ERR);
        client.update_username(new_username);
    }

    /// Updates the protocol version that's saved inside connection_info, that will be used in case of disconnection from the server.
    ///
    /// This method is called when a HELLO command is successfully executed to track the current protocol version.
    /// During reconnection, the stored protocol version will be automatically used for connection establishment.
    ///
    /// # Arguments
    /// * `new_protocol` - The protocol version to store for future reconnections
    ///
    pub(crate) fn update_connection_protocol(&self, new_protocol: redis::ProtocolVersion) {
        let mut client = self
            .inner
            .backend
            .connection_info
            .write()
            .expect(WRITE_LOCK_ERR);
        client.update_protocol(new_protocol);
    }

    /// Returns the username if one was configured during client creation. Otherwise, returns None.
    pub(crate) fn get_username(&self) -> Option<String> {
        let client = self.inner.backend.get_backend_client();
        client.get_connection_info().redis.username.clone()
    }
}
