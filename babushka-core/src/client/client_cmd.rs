use super::get_redis_connection_info;
use super::reconnecting_connection::ReconnectingConnection;
use crate::connection_request::{AddressInfo, ConnectionRequest, TlsMode};
use crate::retry_strategies::RetryStrategy;
use futures::{stream, StreamExt};
#[cfg(cmd_heartbeat)]
use logger_core::log_debug;
use logger_core::{log_trace, log_warn};
use protobuf::EnumOrUnknown;
use redis::{RedisError, RedisResult, Value};
use std::sync::Arc;
#[cfg(cmd_heartbeat)]
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
pub struct ClientCMD {
    inner: Arc<DropWrapper>,
}

pub enum ClientCMDConnectionError {
    NoAddressesProvided,
    FailedConnection(Vec<(String, RedisError)>),
}

impl std::fmt::Debug for ClientCMDConnectionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ClientCMDConnectionError::NoAddressesProvided => {
                write!(f, "No addresses provided")
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
            return Err(ClientCMDConnectionError::FailedConnection(addresses_and_errors));
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

        #[cfg(cmd_heartbeat)]
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
        if !is_readonly_cmd(cmd) || self.inner.nodes.len() == 1 {
            return self.get_primary_connection();
        }
        match &self.inner.read_from_replica_strategy {
            ReadFromReplicaStrategy::AlwaysFromPrimary => self.get_primary_connection(),
            ReadFromReplicaStrategy::RoundRobin {
                latest_read_replica_index,
            } => {
                let initial_index = latest_read_replica_index
                    .load(std::sync::atomic::Ordering::Relaxed)
                    % self.inner.nodes.len();
                loop {
                    let index = (latest_read_replica_index
                        .fetch_add(1, std::sync::atomic::Ordering::Relaxed)
                        + 1)
                        % self.inner.nodes.len();

                    // Looped through all replicas, no connected replica was found.
                    if index == initial_index {
                        return self.get_primary_connection();
                    }
                    if index == self.inner.primary_index {
                        continue;
                    }
                    let Some(connection) = self
                        .inner
                        .nodes
                        .get(index) else {
                        continue;
                    };
                    if connection.is_connected() {
                        return connection;
                    }
                }
            }
        }
    }

    pub async fn send_packed_command(&mut self, cmd: &redis::Cmd) -> RedisResult<Value> {
        log_trace("ClientCMD", "sending command");
        let reconnecting_connection = self.get_connection(cmd);
        let mut connection = reconnecting_connection.get_connection().await?;
        let result = connection.send_packed_command(cmd).await;
        match result {
            Err(err) if err.is_connection_dropped() => {
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
                reconnecting_connection.reconnect();
                Err(err)
            }
            _ => result,
        }
    }

    #[cfg(cmd_heartbeat)]
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

// Copied and djusted from redis-rs
fn is_readonly_cmd(cmd: &redis::Cmd) -> bool {
    matches!(
        cmd.args_iter().next(),
        // @admin
        Some(redis::Arg::Simple(b"LASTSAVE")) |
        // @bitmap
        Some(redis::Arg::Simple(b"BITCOUNT")) | Some(redis::Arg::Simple(b"BITFIELD_RO")) | Some(redis::Arg::Simple(b"BITPOS")) | Some(redis::Arg::Simple(b"GETBIT")) |
        // @connection
        Some(redis::Arg::Simple(b"CLIENT")) | Some(redis::Arg::Simple(b"ECHO")) |
        // @geo
        Some(redis::Arg::Simple(b"GEODIST")) | Some(redis::Arg::Simple(b"GEOHASH")) | Some(redis::Arg::Simple(b"GEOPOS")) | Some(redis::Arg::Simple(b"GEORADIUSBYMEMBER_RO")) | Some(redis::Arg::Simple(b"GEORADIUS_RO")) | Some(redis::Arg::Simple(b"GEOSEARCH")) |
        // @hash
        Some(redis::Arg::Simple(b"HEXISTS")) | Some(redis::Arg::Simple(b"HGET")) | Some(redis::Arg::Simple(b"HGETALL")) | Some(redis::Arg::Simple(b"HKEYS")) | Some(redis::Arg::Simple(b"HLEN")) | Some(redis::Arg::Simple(b"HMGET")) | Some(redis::Arg::Simple(b"HRANDFIELD")) | Some(redis::Arg::Simple(b"HSCAN")) | Some(redis::Arg::Simple(b"HSTRLEN")) | Some(redis::Arg::Simple(b"HVALS")) |
        // @hyperloglog
        Some(redis::Arg::Simple(b"PFCOUNT")) |
        // @keyspace
        Some(redis::Arg::Simple(b"DBSIZE")) | Some(redis::Arg::Simple(b"DUMP")) | Some(redis::Arg::Simple(b"EXISTS")) | Some(redis::Arg::Simple(b"EXPIRETIME")) | Some(redis::Arg::Simple(b"KEYS")) | Some(redis::Arg::Simple(b"OBJECT")) | Some(redis::Arg::Simple(b"PEXPIRETIME")) | Some(redis::Arg::Simple(b"PTTL")) | Some(redis::Arg::Simple(b"RANDOMKEY")) | Some(redis::Arg::Simple(b"SCAN")) | Some(redis::Arg::Simple(b"TOUCH")) | Some(redis::Arg::Simple(b"TTL")) | Some(redis::Arg::Simple(b"TYPE")) |
        // @list
        Some(redis::Arg::Simple(b"LINDEX")) | Some(redis::Arg::Simple(b"LLEN")) | Some(redis::Arg::Simple(b"LPOS")) | Some(redis::Arg::Simple(b"LRANGE")) | Some(redis::Arg::Simple(b"SORT_RO")) |
        // @scripting
        Some(redis::Arg::Simple(b"EVALSHA_RO")) | Some(redis::Arg::Simple(b"EVAL_RO")) | Some(redis::Arg::Simple(b"FCALL_RO")) |
        // @set
        Some(redis::Arg::Simple(b"SCARD")) | Some(redis::Arg::Simple(b"SDIFF")) | Some(redis::Arg::Simple(b"SINTER")) | Some(redis::Arg::Simple(b"SINTERCARD")) | Some(redis::Arg::Simple(b"SISMEMBER")) | Some(redis::Arg::Simple(b"SMEMBERS")) | Some(redis::Arg::Simple(b"SMISMEMBER")) | Some(redis::Arg::Simple(b"SRANDMEMBER")) | Some(redis::Arg::Simple(b"SSCAN")) | Some(redis::Arg::Simple(b"SUNION")) |
        // @sortedset
        Some(redis::Arg::Simple(b"ZCARD")) | Some(redis::Arg::Simple(b"ZCOUNT")) | Some(redis::Arg::Simple(b"ZDIFF")) | Some(redis::Arg::Simple(b"ZINTER")) | Some(redis::Arg::Simple(b"ZINTERCARD")) | Some(redis::Arg::Simple(b"ZLEXCOUNT")) | Some(redis::Arg::Simple(b"ZMSCORE")) | Some(redis::Arg::Simple(b"ZRANDMEMBER")) | Some(redis::Arg::Simple(b"ZRANGE")) | Some(redis::Arg::Simple(b"ZRANGEBYLEX")) | Some(redis::Arg::Simple(b"ZRANGEBYSCORE")) | Some(redis::Arg::Simple(b"ZRANK")) | Some(redis::Arg::Simple(b"ZREVRANGE")) | Some(redis::Arg::Simple(b"ZREVRANGEBYLEX")) | Some(redis::Arg::Simple(b"ZREVRANGEBYSCORE")) | Some(redis::Arg::Simple(b"ZREVRANK")) | Some(redis::Arg::Simple(b"ZSCAN")) | Some(redis::Arg::Simple(b"ZSCORE")) | Some(redis::Arg::Simple(b"ZUNION")) |
        // @stream
        Some(redis::Arg::Simple(b"XINFO")) | Some(redis::Arg::Simple(b"XLEN")) | Some(redis::Arg::Simple(b"XPENDING")) | Some(redis::Arg::Simple(b"XRANGE")) | Some(redis::Arg::Simple(b"XREAD")) | Some(redis::Arg::Simple(b"XREVRANGE")) |
        // @string
        Some(redis::Arg::Simple(b"GET")) | Some(redis::Arg::Simple(b"GETRANGE")) | Some(redis::Arg::Simple(b"LCS")) | Some(redis::Arg::Simple(b"MGET")) | Some(redis::Arg::Simple(b"STRALGO")) | Some(redis::Arg::Simple(b"STRLEN")) | Some(redis::Arg::Simple(b"SUBSTR"))
    )
}

fn get_read_from_replica_strategy(
    read_from_replica_strategy: &EnumOrUnknown<crate::connection_request::ReadFromReplicaStrategy>,
) -> ReadFromReplicaStrategy {
    match read_from_replica_strategy
        .enum_value_or(crate::connection_request::ReadFromReplicaStrategy::AlwaysFromPrimary)
    {
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
