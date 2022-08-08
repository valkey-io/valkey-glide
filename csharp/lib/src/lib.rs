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
        .build()
        .unwrap();
    let _runtime_handle = runtime.enter();
    let connection = runtime
        .block_on(client.get_multiplexed_async_connection())
        .unwrap();
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

fn set_internal<F>(
    connection_ptr: *mut Connection,
    key: *const c_char,
    value: *const c_char,
    callback: F,
) where
    F: FnOnce(RedisResult<()>) + Send + 'static,
{
    let key_cstring = unsafe { CStr::from_ptr(key as *mut c_char) };
    let value_cstring = unsafe { CStr::from_ptr(value as *mut c_char) };
    unsafe {
        let mut connection_clone = (*connection_ptr).connection.clone();
        (*connection_ptr).runtime.spawn(async move {
            let key_bytes = key_cstring.to_bytes();
            let value_bytes = value_cstring.to_bytes();
            callback(connection_clone.set(key_bytes, value_bytes).await);
        });
    }
}

#[no_mangle]
pub extern "C" fn set(
    connection_ptr: *const c_void,
    callback_index: usize,
    key: *const c_char,
    value: *const c_char,
) {
    let connection = connection_ptr as *mut Connection;
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operrations have completed.
    let ptr_as_usize = connection_ptr as usize;
    unsafe {
        set_internal(connection, key, value, move |result| {
            let connection = ptr_as_usize as *mut Connection;
            match result {
                Ok(_) => ((*connection).success_callback)(callback_index, std::ptr::null()),
                Err(_) => ((*connection).failure_callback)(callback_index),
            };
        });
    }
}

fn get_internal<F>(connection_ptr: *mut Connection, key: *const c_char, callback: F)
where
    F: FnOnce(RedisResult<Option<CString>>) + Send + 'static,
{
    let key_cstring = unsafe { CStr::from_ptr(key as *mut c_char) };
    unsafe {
        let mut connection_clone = (*connection_ptr).connection.clone();
        (*connection_ptr).runtime.spawn(async move {
            let key_bytes = key_cstring.to_bytes();
            callback(connection_clone.get(key_bytes).await);
        });
    }
}

#[no_mangle]
pub extern "C" fn get(connection_ptr: *const c_void, callback_index: usize, key: *const c_char) {
    let connection_ptr = connection_ptr as *mut Connection;
    // The safety of this needs to be ensured by the calling code. Cannot dispose of the pointer before all operrations have completed.
    let ptr_as_usize = connection_ptr as usize;
    get_internal(connection_ptr, key, move |result| unsafe {
        let connection = ptr_as_usize as *mut Connection;
        match result {
            Ok(None) => ((*connection).success_callback)(callback_index, std::ptr::null()),
            Ok(Some(c_str)) => ((*connection).success_callback)(callback_index, c_str.as_ptr()),
            Err(_) => ((*connection).failure_callback)(callback_index),
        };
    });
}
