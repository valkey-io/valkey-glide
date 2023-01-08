use babushka::start_socket_listener;
use redis::aio::MultiplexedConnection;
use redis::{AsyncCommands, RedisResult};
use std::{
    ffi::{c_void, CStr, CString},
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
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operrations have completed.
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
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operrations have completed.
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
/// The first pointer is to a socket name address if startup was succesful, second pointer is to an error message if the process fails.
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
