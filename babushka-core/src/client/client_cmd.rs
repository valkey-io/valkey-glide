use super::get_redis_connection_info;
use super::reconnecting_connection::ReconnectingConnection;
use crate::connection_request::{ConnectionRequest, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures::{stream, StreamExt};
use logger_core::{log_debug, log_trace};
use redis::RedisError;
use std::sync::Arc;
use tokio::task;

struct DropWrapper {
    /// Connection to the primary node in the client.
    primary: ReconnectingConnection,
    replicas: Vec<ReconnectingConnection>,
}

impl Drop for DropWrapper {
    fn drop(&mut self) {
        self.primary.mark_as_dropped();
    }
}

#[derive(Clone)]
pub struct ClientCMD {
    inner: Arc<DropWrapper>,
}

pub enum ClientCMDConnectionError {
    NoAddressesProvided,
    NoPrimaryFound,
    FailedConnection(Vec<(String, RedisError)>),
}

impl std::fmt::Debug for ClientCMDConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ClientCMDConnectionError::NoAddressesProvided => {
                write!(f, "No addresses provided")
            }
            ClientCMDConnectionError::NoPrimaryFound => {
                write!(f, "No primary node found")
            }
            ClientCMDConnectionError::FailedConnection(errs) => {
                writeln!(f, "Received errors:")?;
                for (address, error) in errs {
                    writeln!(f, "{address}: {error}")?;
                }
                Ok(())
            }
        }
    }
}

impl ClientCMD {
    pub async fn create_client(
        connection_request: ConnectionRequest,
    ) -> Result<Self, ClientCMDConnectionError> {
        if connection_request.addresses.is_empty() {
            return Err(ClientCMDConnectionError::NoAddressesProvided);
        }
        let retry_strategy = RetryStrategy::new(&connection_request.connection_retry_strategy.0);
        let redis_connection_info =
            get_redis_connection_info(connection_request.authentication_info.0);

        let tls_mode = connection_request.tls_mode.enum_value_or(TlsMode::NoTls);
        let connections = stream::iter(connection_request.addresses.iter())
            .map(|address| async {
                (
                    format!("{}:{}", address.host, address.port),
                    async {
                        let reconnecting_connection = ReconnectingConnection::new(
                            address,
                            retry_strategy.clone(),
                            redis_connection_info.clone(),
                            tls_mode,
                        )
                        .await?;
                        let mut multiplexed_connection =
                            reconnecting_connection.get_connection().await?;
                        let replication_status = multiplexed_connection
                            .send_packed_command(redis::cmd("INFO").arg("REPLICATION"))
                            .await?;
                        Ok((reconnecting_connection, replication_status))
                    }
                    .await,
                )
            })
            .buffer_unordered(connection_request.addresses.len())
            .collect::<Vec<_>>()
            .await;

        if connections.iter().any(|(_, result)| result.is_err()) {
            let addresses_and_errors: Vec<(String, RedisError)> = connections
                .into_iter()
                .filter_map(|(address, result)| result.err().map(|err| (address, err)))
                .collect();
            return Err(ClientCMDConnectionError::FailedConnection(
                addresses_and_errors,
            ));
        }
        let results: Vec<(ReconnectingConnection, redis::Value)> = connections
            .into_iter()
            .map(|(_, result)| result.unwrap())
            .collect();
        let Some(primary_index) = results.iter().position(|(_, replication_status)| 
            redis::from_redis_value::<String>(replication_status).ok().and_then(|val|if val.contains("role:master") { Some(())} else {None}).is_some()
        ) else {
            return Err(ClientCMDConnectionError::NoPrimaryFound);
        };
        let mut connections: Vec<ReconnectingConnection> = results
            .into_iter()
            .map(|(connection, _)| connection)
            .collect();
        let Some(primary) = connections
            .drain(primary_index..primary_index+1)
            .next() else {
                return Err(ClientCMDConnectionError::NoPrimaryFound);
            };

        Self::start_heartbeat(primary.clone());
        for connection in connections.iter() {
            Self::start_heartbeat(connection.clone());
        }
        Ok(Self {
            inner: Arc::new(DropWrapper {
                primary,
                replicas: connections,
            }),
        })
    }

    pub async fn send_packed_command(
        &mut self,
        cmd: &redis::Cmd,
    ) -> redis::RedisResult<redis::Value> {
        log_trace("ClientCMD", "sending command");
        let mut connection = self.inner.primary.get_connection().await?;
        let result = connection.send_packed_command(cmd).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
                self.inner.primary.reconnect();
                Err(err)
            }
            _ => result,
        }
    }

    pub async fn send_packed_commands(
        &mut self,
        cmd: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> redis::RedisResult<Vec<redis::Value>> {
        let mut connection = self.inner.primary.get_connection().await?;
        let result = connection.send_packed_commands(cmd, offset, count).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
                self.inner.primary.reconnect();
                Err(err)
            }
            _ => result,
        }
    }

    fn start_heartbeat(reconnecting_connection: ReconnectingConnection) {
        task::spawn(async move {
            loop {
                tokio::time::sleep(super::HEARTBEAT_SLEEP_DURATION).await;
                if reconnecting_connection.is_dropped() {
                    log_debug(
                        "ClientCMD",
                        "heartbeat stopped after connection was dropped",
                    );
                    // Client was dropped, heartbeat can stop.
                    return;
                }

                let Some(mut connection) = reconnecting_connection.try_get_connection().await else {
                    log_debug(
                        "ClientCMD",
                        "heartbeat stopped while connection is reconnecting",
                    );
                    // Client is reconnecting..
                    continue;
                };
                log_debug("ClientCMD", "performing heartbeat");
                if connection
                    .send_packed_command(&redis::cmd("PING"))
                    .await
                    .is_err_and(|err| err.is_connection_dropped() || err.is_connection_refusal())
                {
                    log_debug("ClientCMD", "heartbeat triggered reconnect");
                    reconnecting_connection.reconnect();
                }
            }
        });
    }
}
