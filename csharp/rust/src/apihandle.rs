use glide_core::client::{Client, ConnectionError};
use glide_core::ConnectionRequest;
use redis::{Cmd, RedisError, Value};
use redis::cluster_routing::RoutingInfo;

#[derive(Clone)]
pub(crate) struct Handle {
    client: Client,
}

impl Handle {
    pub async fn create(request: ConnectionRequest) -> Result<Self, ConnectionError> {
        let client = Client::new(request, None).await?;
        Ok(Self {
            client
        })
    }

    pub async fn command(&self, mut cmd: Cmd, args: Vec<String>, routing: Option<RoutingInfo>) -> Result<Value, RedisError> {
        let mut clone = self.client.clone();
        for arg in args {
            cmd.arg(arg.into_bytes());
        }
        logger_core::log_trace("csharp_ffi::Handle", format!("Sending command {:?}", cmd));
        let result = match clone.send_command(&cmd, routing).await {
            Ok(d) => d,
            Err(e) => return Err(e),
        };
        Ok(result)
    }
}