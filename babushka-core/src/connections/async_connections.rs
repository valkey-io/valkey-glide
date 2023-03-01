
use core::fmt::{self, Debug};
use std::sync::Arc;
use tokio::io::AsyncWriteExt;
use tokio::sync::Mutex;

// use futures::{FutureExt, future};
use redis::aio::ConnectionLike;
use redis::{RedisResult, Value, RedisFuture, Cmd};
use redis::aio::Connection;

pub struct SimpleAsyncConnection
{
    connection_info: redis::ConnectionInfo,
    redis_con: Arc<Mutex<Connection>>,
}

impl Debug for SimpleAsyncConnection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("SimpleConnection")
            .field("connection_info", &self.connection_info)
            .finish()
    }
}

unsafe impl core::marker::Send for SimpleAsyncConnection {}
unsafe impl Sync for SimpleAsyncConnection {}

impl Clone for SimpleAsyncConnection {
    fn clone(&self) -> SimpleAsyncConnection {
        SimpleAsyncConnection { connection_info: self.connection_info.clone(), redis_con: self.redis_con.clone() }
    }
}

impl SimpleAsyncConnection {
    /// Constructs a new `SimpleAsyncConnection`
    pub fn new(
        connection_info: redis::ConnectionInfo,
        con: Connection,
    ) -> RedisResult<Self>
    {
        logger_core::log(
            logger_core::Level::Info,
            "connection",
            "new SimpleAsyncConnection initiated",
        );
        let connection = SimpleAsyncConnection {
            connection_info: connection_info,
            redis_con: Arc::new(Mutex::new(con)),
        };
        Ok(connection)
    }

    pub async fn open<T: redis::IntoConnectionInfo>(params: T) -> RedisResult<SimpleAsyncConnection> {
        let new_client = redis::Client::open(params).unwrap();
        let con = new_client.get_async_connection().await?;
        SimpleAsyncConnection::new(
            new_client.get_connection_info().clone(), 
            con,
        )
    }

    pub fn get_connection_info(&self) -> &redis::ConnectionInfo {
        &self.connection_info
    }
}

impl ConnectionLike for SimpleAsyncConnection {
    fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
        let temp_con = self.redis_con.clone();
        let read_fn = async move  {
            let packed_cmd = cmd.get_packed_command();
            {
                let _result = temp_con.lock().await.con.write_all (packed_cmd.as_slice()).await?;
            }
            let response = temp_con.lock().await.read_response().await;
            response
        };

        // future::ready(read_fn).boxed()
        Box::pin(read_fn)        
    }

    fn req_packed_commands<'a>(
        &'a mut self,
        cmd: &'a redis::Pipeline,
        offset: usize,
        count: usize,
    ) -> redis::RedisFuture<'a, Vec<redis::Value>> {
        let temp_con = self.redis_con.clone();

        let read_fn = async move {
            let packed_cmd = cmd.get_packed_pipeline();
           
            {
                temp_con.lock().await.con.write_all(packed_cmd.as_slice()).await?;
            }

            let mut results = Vec::<redis::Value>::new();
            for _ in offset..count {
                results.push(match temp_con.lock().await.read_response().await {
                    Ok(it) => it,
                    Err(err) => return Err(err),
                });
            };
            Ok(results)
        };
        Box::pin(read_fn)
    }

    fn get_db(&self) -> i64 {
        self.connection_info.redis.db
    }
}
