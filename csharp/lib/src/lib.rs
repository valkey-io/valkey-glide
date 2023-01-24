use babushka::start_socket_listener;
use redis::aio::MultiplexedConnection;
use redis::{AsyncCommands, RedisResult};
use std::{
    ffi::{c_int, c_void, CStr, CString},
    os::raw::c_char,
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

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
        .worker_threads(1)
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
    start_socket_listener(move |result| {
        match result {
            Ok(socket_name) => {
                let c_str = CString::new(socket_name).unwrap();
                unsafe {
                    init_callback(c_str.as_ptr(), std::ptr::null());
                }
            }
            Err(error) => {
                let c_str = CString::new(error.to_string()).unwrap();
                unsafe {
                    init_callback(std::ptr::null(), c_str.as_ptr());
                }
            }
        };
    });
}

fn into_logger_level(level: i32) -> Option<logger_core::Level> {
    match level {
        0 => Some(logger_core::Level::Error),
        1 => Some(logger_core::Level::Warn),
        2 => Some(logger_core::Level::Info),
        3 => Some(logger_core::Level::Debug),
        4 => Some(logger_core::Level::Trace),
        _ => None,
    }
}

fn into_level(level: logger_core::Level) -> i32 {
    match level {
        logger_core::Level::Error => 0,
        logger_core::Level::Warn => 1,
        logger_core::Level::Info => 2,
        logger_core::Level::Debug => 3,
        logger_core::Level::Trace => 4,
    }
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
pub extern "C" fn log(log_level: c_int, log_identifier: *const c_char, message: *const c_char) {
    unsafe {
        logger_core::log(
            into_logger_level(log_level).unwrap(),
            CStr::from_ptr(log_identifier)
                .to_str()
                .expect("Can not read string argument."),
            CStr::from_ptr(message)
                .to_str()
                .expect("Can not read string argument."),
        );
    }
}

#[no_mangle]
#[allow(improper_ctypes_definitions)]
pub extern "C" fn init(level: c_int, file_name: *const c_char) -> i32 {
    let file_name_as_str;
    unsafe {
        if file_name.is_null() {
            println!(
                "{}",
                CStr::from_ptr(file_name)
                    .to_str()
                    .expect("Can not read string argument.")
            );
            file_name_as_str = None;
        } else {
            file_name_as_str = Some(
                CStr::from_ptr(file_name)
                    .to_str()
                    .expect("Can not read string argument."),
            );
        }

        let logger_level = logger_core::init(into_logger_level(level), file_name_as_str);
        let _ = tracing_subscriber::fmt::try_init();
        return into_level(logger_level);
    }
}
