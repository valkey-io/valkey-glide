use napi::{Error, Result, Status};
use napi_derive::napi;
use redis::aio::MultiplexedConnection;
use redis::{AsyncCommands, RedisError, RedisResult};
use std::str;

#[napi]
struct AsyncClient {
    connection: MultiplexedConnection,
}

fn to_js_error(err: RedisError) -> Error {
    napi::Error::new(Status::Unknown, err.to_string())
}

fn to_js_result<T>(result: RedisResult<T>) -> Result<T> {
    match result {
        Ok(val) => Ok(val),
        Err(err) => Err(to_js_error(err)),
    }
}

#[napi]
impl AsyncClient {
    #[napi(js_name = "CreateConnection")]
    pub async fn create_connection(connection_address: String) -> Result<AsyncClient> {
        let client = to_js_result(redis::Client::open(connection_address))?;
        let connection = to_js_result(client.get_multiplexed_async_connection().await)?;
        Ok(AsyncClient { connection })
    }

    #[napi]
    pub async fn get(&self, key: String) -> Result<Option<String>> {
        let mut connection = self.connection.clone();
        let result = connection.get(key).await;
        match result {
            Ok(val) => Ok(val),
            Err(err) => Err(to_js_error(err)),
        }
    }

    #[napi]
    pub async fn set(&self, key: String, value: String) -> Result<()> {
        let mut connection = self.connection.clone();
        to_js_result(connection.set(key, value).await)
    }
}
