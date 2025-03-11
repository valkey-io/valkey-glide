use glide_core::client::{Client, ConnectionError};
use glide_core::ConnectionRequest;
use redis::{Cmd, RedisError, Value};

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

    pub async fn command(&self, mut cmd: Cmd, args: Vec<String>) -> Result<Value, RedisError> {
        let mut clone = self.client.clone();
        for arg in args {
            cmd.arg(arg.into_bytes());
        }
        let result = match clone.send_command(&cmd, None).await {
            Ok(d) => d,
            Err(e) => return Err(e),
        };
        Ok(result)
    }
}