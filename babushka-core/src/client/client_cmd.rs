use super::get_redis_connection_info;
use super::reconnecting_connection::ReconnectingConnection;
use crate::connection_request::{ConnectionRequest, TlsMode};
use crate::retry_strategies::RetryStrategy;
use logger_core::{log_debug, log_trace};
use redis::RedisResult;
use std::sync::Arc;
use tokio::task;

struct DropWrapper {
    /// Connection to the primary node in the client.
    primary: ReconnectingConnection,
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

impl ClientCMD {
    pub async fn create_client(connection_request: ConnectionRequest) -> RedisResult<Self> {
        let primary_address = connection_request.addresses.first().unwrap();

        let retry_strategy = RetryStrategy::new(&connection_request.connection_retry_strategy.0);
        let redis_connection_info =
            get_redis_connection_info(connection_request.authentication_info.0);

        let tls_mode = connection_request.tls_mode.enum_value_or(TlsMode::NoTls);
        let primary = ReconnectingConnection::new(
            primary_address,
            retry_strategy.clone(),
            redis_connection_info,
            tls_mode,
        )
        .await?;
        Self::start_heartbeat(primary.clone());
        Ok(Self {
            inner: Arc::new(DropWrapper { primary }),
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
