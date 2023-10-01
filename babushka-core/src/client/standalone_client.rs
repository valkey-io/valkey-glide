use super::get_redis_connection_info;
use super::reconnecting_connection::ReconnectingConnection;
use crate::connection_request::{AddressInfo, ConnectionRequest, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures::{stream, StreamExt};
#[cfg(standalone_heartbeat)]
use logger_core::log_debug;
use logger_core::{log_trace, log_warn};
use protobuf::EnumOrUnknown;
use redis::cluster_routing::is_readonly;
use redis::{RedisError, RedisResult, Value};
use std::sync::Arc;
#[cfg(standalone_heartbeat)]
use tokio::task;

enum ReadFromReplicaStrategy {
    AlwaysFromPrimary,
    RoundRobin {
        latest_read_replica_index: Arc<std::sync::atomic::AtomicUsize>,
    },
}

struct DropWrapper {
    /// Connection to the primary node in the client.
    primary_index: usize,
    nodes: Vec<ReconnectingConnection>,
    read_from_replica_strategy: ReadFromReplicaStrategy,
}

impl Drop for DropWrapper {
    fn drop(&mut self) {
        for node in self.nodes.iter() {
            node.mark_as_dropped();
        }
    }
}

#[derive(Clone)]
pub struct StandaloneClient {
    inner: Arc<DropWrapper>,
}

pub enum StandaloneClientConnectionError {
    NoAddressesProvided,
    FailedConnection(Vec<(String, RedisError)>),
}

impl std::fmt::Debug for StandaloneClientConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            StandaloneClientConnectionError::NoAddressesProvided => {
                write!(f, "No addresses provided")
            }
            StandaloneClientConnectionError::FailedConnection(errs) => {
                writeln!(f, "Received errors:")?;
                for (address, error) in errs {
                    writeln!(f, "{address}: {error}")?;
                }
                Ok(())
            }
        }
    }
}

impl StandaloneClient {
    pub async fn create_client(
        connection_request: ConnectionRequest,
    ) -> Result<Self, StandaloneClientConnectionError> {
        if connection_request.addresses.is_empty() {
            return Err(StandaloneClientConnectionError::NoAddressesProvided);
        }
        let retry_strategy = RetryStrategy::new(&connection_request.connection_retry_strategy.0);
        let redis_connection_info = get_redis_connection_info(
            connection_request.authentication_info.0,
            connection_request.database_id,
        );

        let tls_mode = connection_request.tls_mode.enum_value_or_default();
        let node_count = connection_request.addresses.len();
        let mut stream = stream::iter(connection_request.addresses.iter())
            .map(|address| async {
                get_connection_and_replication_info(
                    address,
                    &retry_strategy,
                    &redis_connection_info,
                    tls_mode,
                )
                .await
                .map_err(|err| (format!("{}:{}", address.host, address.port), err))
            })
            .buffer_unordered(node_count);

        let mut nodes = Vec::with_capacity(node_count);
        let mut addresses_and_errors = Vec::with_capacity(node_count);
        let mut primary_index = None;
        while let Some(result) = stream.next().await {
            match result {
                Ok((connection, replication_status)) => {
                    nodes.push(connection);
                    if primary_index.is_none()
                        && redis::from_redis_value::<String>(&replication_status)
                            .is_ok_and(|val| val.contains("role:master"))
                    {
                        primary_index = Some(nodes.len() - 1);
                    }
                }
                Err((address, (connection, err))) => {
                    nodes.push(connection);
                    addresses_and_errors.push((address, err));
                }
            }
        }

        let Some(primary_index) = primary_index else {
            return Err(StandaloneClientConnectionError::FailedConnection(
                addresses_and_errors,
            ));
        };
        if !addresses_and_errors.is_empty() {
            log_warn(
                "client creation",
                format!(
                    "Failed to connect to {addresses_and_errors:?}, will attempt to reconnect."
                ),
            );
        }
        let read_from_replica_strategy =
            get_read_from_replica_strategy(&connection_request.read_from_replica_strategy);

        #[cfg(standalone_heartbeat)]
        for node in nodes.iter() {
            Self::start_heartbeat(node.clone());
        }

        Ok(Self {
            inner: Arc::new(DropWrapper {
                primary_index,
                nodes,
                read_from_replica_strategy,
            }),
        })
    }

    fn get_primary_connection(&self) -> &ReconnectingConnection {
        self.inner.nodes.get(self.inner.primary_index).unwrap()
    }

    fn get_connection(&self, cmd: &redis::Cmd) -> &ReconnectingConnection {
        if !is_readonly(cmd) || self.inner.nodes.len() == 1 {
            return self.get_primary_connection();
        }
        match &self.inner.read_from_replica_strategy {
            ReadFromReplicaStrategy::AlwaysFromPrimary => self.get_primary_connection(),
            ReadFromReplicaStrategy::RoundRobin {
                latest_read_replica_index,
            } => {
                let initial_index =
                    latest_read_replica_index.load(std::sync::atomic::Ordering::Relaxed);
                let mut check_count = 0;
                loop {
                    check_count += 1;

                    // Looped through all replicas, no connected replica was found.
                    if check_count > self.inner.nodes.len() {
                        return self.get_primary_connection();
                    }
                    let index = (initial_index + check_count) % self.inner.nodes.len();
                    if index == self.inner.primary_index {
                        continue;
                    }
                    let Some(connection) = self.inner.nodes.get(index) else {
                        continue;
                    };
                    if connection.is_connected() {
                        let _ = latest_read_replica_index.compare_exchange_weak(
                            initial_index,
                            index,
                            std::sync::atomic::Ordering::Relaxed,
                            std::sync::atomic::Ordering::Relaxed,
                        );
                        return connection;
                    }
                }
            }
        }
    }

    pub async fn send_packed_command(&mut self, cmd: &redis::Cmd) -> RedisResult<Value> {
        log_trace("StandaloneClient", "sending command");
        let reconnecting_connection = self.get_connection(cmd);
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection.send_packed_command(cmd).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
                log_warn(
                    "single request",
                    format!("received disconnect error `{err}`"),
                );
                reconnecting_connection.reconnect();
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
    ) -> RedisResult<Vec<Value>> {
        let reconnecting_connection = self.get_primary_connection();
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection.send_packed_commands(cmd, offset, count).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
                log_warn(
                    "pipeline request",
                    format!("received disconnect error `{err}`"),
                );
                reconnecting_connection.reconnect();
                Err(err)
            }
            _ => result,
        }
    }

    #[cfg(standalone_heartbeat)]
    fn start_heartbeat(reconnecting_connection: ReconnectingConnection) {
        task::spawn(async move {
            loop {
                tokio::time::sleep(super::HEARTBEAT_SLEEP_DURATION).await;
                if reconnecting_connection.is_dropped() {
                    log_debug(
                        "StandaloneClient",
                        "heartbeat stopped after connection was dropped",
                    );
                    // Client was dropped, heartbeat can stop.
                    return;
                }

                let Some(mut connection) = reconnecting_connection.try_get_connection().await
                else {
                    log_debug(
                        "StandaloneClient",
                        "heartbeat stopped while connection is reconnecting",
                    );
                    // Client is reconnecting..
                    continue;
                };
                log_debug("StandaloneClient", "performing heartbeat");
                if connection
                    .send_packed_command(&redis::cmd("PING"))
                    .await
                    .is_err_and(|err| err.is_connection_dropped() || err.is_connection_refusal())
                {
                    log_debug("StandaloneClient", "heartbeat triggered reconnect");
                    reconnecting_connection.reconnect();
                }
            }
        });
    }
}

async fn get_connection_and_replication_info(
    address: &AddressInfo,
    retry_strategy: &RetryStrategy,
    connection_info: &redis::RedisConnectionInfo,
    tls_mode: TlsMode,
) -> Result<(ReconnectingConnection, Value), (ReconnectingConnection, RedisError)> {
    let result = ReconnectingConnection::new(
        address,
        retry_strategy.clone(),
        connection_info.clone(),
        tls_mode,
    )
    .await;
    let reconnecting_connection = match result {
        Ok(reconnecting_connection) => reconnecting_connection,
        Err(tuple) => return Err(tuple),
    };

    let mut multiplexed_connection = match reconnecting_connection.get_connection().await {
        Ok(multiplexed_connection) => multiplexed_connection,
        Err(err) => {
            reconnecting_connection.reconnect();
            return Err((reconnecting_connection, err));
        }
    };

    match multiplexed_connection
        .send_packed_command(redis::cmd("INFO").arg("REPLICATION"))
        .await
    {
        Ok(replication_status) => Ok((reconnecting_connection, replication_status)),
        Err(err) => Err((reconnecting_connection, err)),
    }
}

fn get_read_from_replica_strategy(
    read_from_replica_strategy: &EnumOrUnknown<crate::connection_request::ReadFromReplicaStrategy>,
) -> ReadFromReplicaStrategy {
    match read_from_replica_strategy.enum_value_or_default() {
        crate::connection_request::ReadFromReplicaStrategy::AlwaysFromPrimary => {
            ReadFromReplicaStrategy::AlwaysFromPrimary
        }
        crate::connection_request::ReadFromReplicaStrategy::RoundRobin => {
            ReadFromReplicaStrategy::RoundRobin {
                latest_read_replica_index: Default::default(),
            }
        }
        crate::connection_request::ReadFromReplicaStrategy::LowestLatency => todo!(),
        crate::connection_request::ReadFromReplicaStrategy::AZAffinity => todo!(),
    }
}
