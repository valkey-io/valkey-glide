use crate::connection_request::{ConnectionRequest, TlsMode};
use crate::retry_strategies::RetryStrategy;
use redis::RedisResult;

use super::get_redis_connection_info;
use super::reconnecting_connection::ReconnectingConnection;

#[derive(Clone)]
pub struct ClientCMD {
    /// Connection to the primary node in the client.
    primary: ReconnectingConnection,
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

        Ok(Self { primary })
    }

    pub async fn send_packed_command(
        &mut self,
        cmd: &redis::Cmd,
    ) -> redis::RedisResult<redis::Value> {
        self.primary.send_packed_command(cmd).await
    }

    pub(super) async fn send_packed_commands(
        &mut self,
        cmd: &redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> redis::RedisResult<Vec<redis::Value>> {
        self.primary.send_packed_commands(cmd, offset, count).await
    }

    pub(super) fn get_db(&self) -> i64 {
        self.primary.get_db()
    }
}
