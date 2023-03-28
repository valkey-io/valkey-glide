use babushka::start_socket_listener;
use logger_core::{log_error, log_info};
use redis::{aio::MultiplexedConnection, AsyncCommands, RedisResult, Value};
use std::{
    ffi::{c_void, CStr, CString},
    os::raw::c_char,
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

#[derive(Debug)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
}

// Required to support FFI async client
#[allow(dead_code)]
pub struct Connection {
    connection: MultiplexedConnection,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (), // TODO - add specific error codes
    runtime: Runtime,
}

fn create_connection_internal(
    address: *const c_char,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> RedisResult<Connection> {
    let address_cstring = unsafe { CStr::from_ptr(address as *mut c_char) };
    let address_string = address_cstring.to_str()?;
    let client = redis::Client::open(address_string)?;
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .thread_name("Babushka C# thread")
        .build()?;
    let _runtime_handle = runtime.enter();
    let connection = runtime.block_on(client.get_multiplexed_async_connection())?; // TODO - log errors
    Ok(Connection {
        connection,
        success_callback,
        failure_callback,
        runtime,
    })
}

/// Creates a new connection to the given address. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. All callbacks should be offloaded to separate threads in order not to exhaust the connection's thread pool.
#[no_mangle]
pub extern "C" fn create_connection(
    address: *const c_char,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> *const c_void {
    match create_connection_internal(address, success_callback, failure_callback) {
        Err(_) => std::ptr::null(),
        Ok(connection) => Box::into_raw(Box::new(connection)) as *const c_void,
    }
}

#[no_mangle]
pub extern "C" fn close_connection(connection_ptr: *const c_void) {
    let connection_ptr = unsafe { Box::from_raw(connection_ptr as *mut Connection) };
    let _runtime_handle = connection_ptr.runtime.enter();
    drop(connection_ptr);
}

/// Expects that key and value will be kept valid until the callback is called.
#[no_mangle]
pub extern "C" fn set(
    connection_ptr: *const c_void,
    callback_index: usize,
    key: *const c_char,
    value: *const c_char,
) {
    let connection = unsafe { Box::leak(Box::from_raw(connection_ptr as *mut Connection)) };
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let ptr_address = connection_ptr as usize;

    let key_cstring = unsafe { CStr::from_ptr(key as *mut c_char) };
    let value_cstring = unsafe { CStr::from_ptr(value as *mut c_char) };
    let mut connection_clone = connection.connection.clone();
    connection.runtime.spawn(async move {
        let key_bytes = key_cstring.to_bytes();
        let value_bytes = value_cstring.to_bytes();
        let result: RedisResult<()> = connection_clone.set(key_bytes, value_bytes).await;
        unsafe {
            let connection = Box::leak(Box::from_raw(ptr_address as *mut Connection));
            match result {
                Ok(_) => (connection.success_callback)(callback_index, std::ptr::null()),
                Err(_) => (connection.failure_callback)(callback_index), // TODO - report errors
            };
        }
    });
}

/// Expects that key will be kept valid until the callback is called. If the callback is called with a string pointer, the pointer must
/// be used synchronously, because the string will be dropped after the callback.
#[no_mangle]
pub extern "C" fn get(connection_ptr: *const c_void, callback_index: usize, key: *const c_char) {
    let connection = unsafe { Box::leak(Box::from_raw(connection_ptr as *mut Connection)) };
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let ptr_address = connection_ptr as usize;

    let key_cstring = unsafe { CStr::from_ptr(key as *mut c_char) };
    let mut connection_clone = connection.connection.clone();
    connection.runtime.spawn(async move {
        let key_bytes = key_cstring.to_bytes();
        let result: RedisResult<Option<CString>> = connection_clone.get(key_bytes).await;

        unsafe {
            let connection = Box::leak(Box::from_raw(ptr_address as *mut Connection));
            match result {
                Ok(None) => (connection.success_callback)(callback_index, std::ptr::null()),
                Ok(Some(c_str)) => (connection.success_callback)(callback_index, c_str.as_ptr()),
                Err(_) => (connection.failure_callback)(callback_index), // TODO - report errors
            };
        }
    });
}

/// Receives a callback function which should be called with a single allocated pointer and a single null pointer.
/// The first pointer is to a socket name address if startup was successful, second pointer is to an error message if the process fails.
#[no_mangle]
pub extern "C" fn start_socket_listener_wrapper(
    init_callback: unsafe extern "C" fn(*const c_char, *const c_char) -> (),
) {
    log_info("start_socket_listener_wrapper", "starting");
    start_socket_listener(move |result| {
        match result {
            Ok(socket_name) => {
                let c_str = CString::new(socket_name).unwrap();
                log_info(
                    "start_socket_listener_wrapper",
                    format!("socket: {:?}", c_str),
                );
                unsafe {
                    init_callback(c_str.as_ptr(), std::ptr::null());
                }
            }
            Err(error_message) => {
                log_error(
                    "start_socket_listener_wrapper",
                    format!("error: {:?}", error_message),
                );
                let c_str = CString::new(error_message).unwrap();
                unsafe {
                    init_callback(std::ptr::null(), c_str.as_ptr());
                }
            }
        };
    });
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

#[no_mangle]
#[allow(improper_ctypes_definitions)]
/// # Safety
/// Unsafe function because creating string from pointer
pub unsafe extern "C" fn log(
    log_level: Level,
    log_identifier: *const c_char,
    message: *const c_char,
) {
    unsafe {
        logger_core::log(
            log_level.into(),
            CStr::from_ptr(log_identifier)
                .to_str()
                .expect("Can not read log_identifier argument."),
            CStr::from_ptr(message)
                .to_str()
                .expect("Can not read message argument."),
        );
    }
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
/// # Safety
/// Unsafe function because creating string from pointer
pub unsafe extern "C" fn init(level: Option<Level>, file_name: *const c_char) -> Level {
    let file_name_as_str;
    unsafe {
        file_name_as_str = if file_name.is_null() {
            None
        } else {
            Some(
                CStr::from_ptr(file_name)
                    .to_str()
                    .expect("Can not read string argument."),
            )
        };

        let logger_level = logger_core::init(level.map(|level| level.into()), file_name_as_str);
        logger_level.into()
    }
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
pub unsafe extern "C" fn free_memory(pointer: *const Value) {
    let _value = unsafe { Box::from_raw(pointer as *mut Value) };
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
pub fn string_from_pointer(pointer: *const char) -> String {
    *unsafe { Box::from_raw(pointer as *mut String) }
}
