use napi::bindgen_prelude::ToNapiValue;
use napi::threadsafe_function::{ErrorStrategy, ThreadsafeFunction, ThreadsafeFunctionCallMode};
use napi::{Error, JsFunction, Result, Status};
use napi_derive::napi;
use redis::aio::MultiplexedConnection;
use redis::socket_listener::headers::HEADER_END;
use redis::socket_listener::{start_socket_listener, ClosingReason};
use redis::{AsyncCommands, RedisError, RedisResult};
use std::str;

// TODO - this repetition will become unmaintainable. We need to do this in macros.
#[napi]
pub enum RequestType {
    /// Type of a get string request.
    GetString = 1,
    /// Type of a set string request.
    SetString = 2,
}

// TODO - this repetition will become unmaintainable. We need to do this in macros.
#[napi]
pub enum ResponseType {
    /// Type of a response that returns a null.
    Null = 0,
    /// Type of a response that returns a string.
    String = 1,
}

// TODO - this repetition will become unmaintainable. We need to do this in macros.
#[napi]
pub const HEADER_LENGTH_IN_BYTES: u32 = HEADER_END as u32;

#[napi]
struct AsyncClient {
    #[allow(dead_code)]
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
    #[allow(dead_code)]
    pub async fn create_connection(connection_address: String) -> Result<AsyncClient> {
        let client = to_js_result(redis::Client::open(connection_address))?;
        let connection = to_js_result(client.get_multiplexed_async_connection().await)?;
        Ok(AsyncClient { connection })
    }

    #[napi]
    #[allow(dead_code)]
    pub async fn get(&self, key: String) -> Result<Option<String>> {
        let mut connection = self.connection.clone();
        let result = connection.get(key).await;
        match result {
            Ok(val) => Ok(val),
            Err(err) => Err(to_js_error(err)),
        }
    }

    #[napi]
    #[allow(dead_code)]
    pub async fn set(&self, key: String, value: String) -> Result<()> {
        let mut connection = self.connection.clone();
        to_js_result(connection.set(key, value).await)
    }
}

#[napi(
    js_name = "StartSocketConnection",
    ts_args_type = "connectionAddress: string, 
                    readSocketName: string, 
                    writeSocketName: string, 
                    startCallback: (err: null | Error) => void, 
                    closeCallback: (err: null | Error) => void"
)]
pub fn start_socket_listener_external(
    connection_address: String,
    read_socket_name: String,
    write_socket_name: String,
    start_callback: JsFunction,
    close_callback: JsFunction,
) -> napi::Result<()> {
    let threadsafe_start_callback: ThreadsafeFunction<(), ErrorStrategy::Fatal> =
        start_callback.create_threadsafe_function(0, |_| Ok(Vec::<()>::new()))?;
    let threadsafe_close_callback: ThreadsafeFunction<(), ErrorStrategy::CalleeHandled> =
        close_callback.create_threadsafe_function(0, |_| Ok(Vec::<()>::new()))?;
    let client = to_js_result(redis::Client::open(connection_address))?;
    start_socket_listener(
        client,
        read_socket_name,
        write_socket_name,
        move || {
            threadsafe_start_callback.call((), ThreadsafeFunctionCallMode::NonBlocking);
        },
        move |result| match result {
            ClosingReason::ReadSocketClosed => {
                threadsafe_close_callback.call(Ok(()), ThreadsafeFunctionCallMode::NonBlocking);
            }
            ClosingReason::UnhandledError(err) => {
                threadsafe_close_callback.call(
                    Err(to_js_error(err)),
                    ThreadsafeFunctionCallMode::NonBlocking,
                );
            }
            ClosingReason::FailedInitialization(err) => {
                // TODO - Do we want to differentiate this from UnhandledError ?
                threadsafe_close_callback.call(
                    Err(to_js_error(err)),
                    ThreadsafeFunctionCallMode::NonBlocking,
                );
            }
        },
    );
    Ok(())
}
