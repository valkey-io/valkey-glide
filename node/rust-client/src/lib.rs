use babushka::start_socket_listener;
use babushka::MAX_REQUEST_ARGS_LENGTH;
use byteorder::{LittleEndian, WriteBytesExt};
use napi::bindgen_prelude::BigInt;
use napi::{Env, Error, JsObject, JsUnknown, Result, Status};
use napi_derive::napi;
use redis::aio::MultiplexedConnection;
use redis::{AsyncCommands, Value};
use std::str;
use tokio::runtime::{Builder, Runtime};

#[napi]
pub enum Level {
    Debug = 3,
    Error = 0,
    Info = 2,
    Trace = 4,
    Warn = 1,
}

#[napi]
pub const MAX_REQUEST_ARGS_LEN: u32 = MAX_REQUEST_ARGS_LENGTH as u32;

#[napi]
pub const DEFAULT_TIMEOUT_IN_MILLISECONDS: u32 =
    babushka::client::DEFAULT_RESPONSE_TIMEOUT.as_millis() as u32;

#[napi]
struct AsyncClient {
    #[allow(dead_code)]
    connection: MultiplexedConnection,
    runtime: Runtime,
}

fn to_js_error(err: impl std::error::Error) -> Error {
    napi::Error::new(Status::Unknown, err.to_string())
}

fn to_js_result<T, E: std::error::Error>(result: std::result::Result<T, E>) -> Result<T> {
    result.map_err(to_js_error)
}

#[napi]
impl AsyncClient {
    #[napi(js_name = "CreateConnection")]
    #[allow(dead_code)]
    pub fn create_connection(connection_address: String) -> Result<AsyncClient> {
        let runtime = Builder::new_multi_thread()
            .enable_all()
            .worker_threads(1)
            .thread_name("Babushka node thread")
            .build()?;
        let _runtime_handle = runtime.enter();
        let client = to_js_result(redis::Client::open(connection_address))?;
        let connection = to_js_result(runtime.block_on(client.get_multiplexed_async_connection()))?;
        Ok(AsyncClient {
            connection,
            runtime,
        })
    }

    #[napi(ts_return_type = "Promise<string | null>")]
    #[allow(dead_code)]
    pub fn get(&self, env: Env, key: String) -> Result<JsObject> {
        let (deferred, promise) = env.create_deferred()?;

        let mut connection = self.connection.clone();
        self.runtime.spawn(async move {
            let result: Result<Option<String>> = to_js_result(connection.get(key).await);
            match result {
                Ok(value) => deferred.resolve(|_| Ok(value)),
                Err(e) => deferred.reject(e),
            }
        });

        Ok(promise)
    }

    #[napi(ts_return_type = "Promise<string | \"OK\" | null>")]
    #[allow(dead_code)]
    pub fn set(&self, env: Env, key: String, value: String) -> Result<JsObject> {
        let (deferred, promise) = env.create_deferred()?;

        let mut connection = self.connection.clone();
        self.runtime.spawn(async move {
            let result: Result<()> = to_js_result(connection.set(key, value).await);
            match result {
                Ok(_) => deferred.resolve(|_| Ok("OK")),
                Err(e) => deferred.reject(e),
            }
        });

        Ok(promise)
    }
}

#[napi(js_name = "StartSocketConnection", ts_return_type = "Promise<string>")]
pub fn start_socket_listener_external(env: Env) -> Result<JsObject> {
    let (deferred, promise) = env.create_deferred()?;

    start_socket_listener(move |result| {
        match result {
            Ok(path) => deferred.resolve(|_| Ok(path)),
            Err(error_message) => deferred.reject(napi::Error::new(Status::Unknown, error_message)),
        };
    });

    Ok(promise)
}

impl From<logger_core::Level> for Level {
    fn from(level: logger_core::Level) -> Self {
        match level {
            logger_core::Level::Error => Level::Error,
            logger_core::Level::Warn => Level::Warn,
            logger_core::Level::Info => Level::Info,
            logger_core::Level::Debug => Level::Debug,
            logger_core::Level::Trace => Level::Trace,
        }
    }
}

impl From<Level> for logger_core::Level {
    fn from(level: Level) -> logger_core::Level {
        match level {
            Level::Error => logger_core::Level::Error,
            Level::Warn => logger_core::Level::Warn,
            Level::Info => logger_core::Level::Info,
            Level::Debug => logger_core::Level::Debug,
            Level::Trace => logger_core::Level::Trace,
        }
    }
}

#[napi]
pub fn log(log_level: Level, log_identifier: String, message: String) {
    logger_core::log(log_level.into(), log_identifier, message);
}

#[napi(js_name = "InitInternalLogger")]
pub fn init(level: Option<Level>, file_name: Option<&str>) -> Level {
    let logger_level = logger_core::init(level.map(|level| level.into()), file_name);
    logger_level.into()
}

fn redis_value_to_js(val: Value, js_env: Env) -> Result<JsUnknown> {
    match val {
        Value::Nil => js_env.get_null().map(|val| val.into_unknown()),
        Value::Status(str) => js_env
            .create_string_from_std(str)
            .map(|val| val.into_unknown()),
        Value::Okay => js_env.create_string("OK").map(|val| val.into_unknown()),
        Value::Int(num) => js_env.create_int64(num).map(|val| val.into_unknown()),
        Value::Data(data) => {
            let str = to_js_result(std::str::from_utf8(data.as_ref()))?;
            js_env.create_string(str).map(|val| val.into_unknown())
        }
        Value::Bulk(bulk) => {
            let mut js_array_view = js_env.create_array_with_length(bulk.len())?;
            for (index, item) in bulk.into_iter().enumerate() {
                js_array_view.set_element(index as u32, redis_value_to_js(item, js_env)?)?;
            }
            Ok(js_array_view.into_unknown())
        }
    }
}

#[napi(ts_return_type = "null | string | number | any[]")]
pub fn value_from_split_pointer(js_env: Env, high_bits: u32, low_bits: u32) -> Result<JsUnknown> {
    let mut bytes = [0_u8; 8];
    (&mut bytes[..4])
        .write_u32::<LittleEndian>(low_bits)
        .unwrap();
    (&mut bytes[4..])
        .write_u32::<LittleEndian>(high_bits)
        .unwrap();
    let pointer = u64::from_le_bytes(bytes);
    let value = unsafe { Box::from_raw(pointer as *mut Value) };
    redis_value_to_js(*value, js_env)
}

#[napi]
pub fn string_from_pointer(pointer_as_bigint: BigInt) -> String {
    *unsafe { Box::from_raw(pointer_as_bigint.get_u64().1 as *mut String) }
}

#[napi]
/// This function is for tests that require a value allocated on the heap.
/// Should NOT be used in production.
pub fn create_leaked_value(message: String) -> usize {
    let value = Value::Status(message);
    Box::leak(Box::new(value)) as *mut Value as usize
}

#[napi(ts_return_type = "[number, number]")]
pub fn create_leaked_string_vec(message: Vec<String>) -> [u32; 2] {
    let pointer = Box::leak(Box::new(message)) as *mut Vec<String> as u64;
    let bytes = u64::to_le_bytes(pointer);
    let [lower, higher] = unsafe { std::mem::transmute::<[u8; 8], [u32; 2]>(bytes) };
    [lower, higher]
}
