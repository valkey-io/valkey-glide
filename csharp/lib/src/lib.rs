// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod ffi;
use ffi::{create_connection_request, create_route, ConnectionConfig, RouteInfo};
use glide_core::{client::Client as GlideClient, request_type::RequestType};
use redis::{FromRedisValue, RedisResult};
use std::{
    ffi::{c_void, CStr, CString},
    os::raw::c_char,
};
use tokio::runtime::{Builder, Runtime};

#[repr(C)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
    Off = 5,
}

pub struct Client {
    client: GlideClient,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (), // TODO - add specific error codes
    runtime: Runtime,
}

/// # Safety
///
/// * `config` must be a valid [`ConnectionConfig`] pointer. See the safety documentation of [`create_connection_request`].
unsafe fn create_client_internal(
    config: *const ConnectionConfig,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> RedisResult<Client> {
    let request = unsafe { create_connection_request(config) };
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .thread_name("GLIDE C# thread")
        .build()?;
    let _runtime_handle = runtime.enter();
    let client = runtime.block_on(GlideClient::new(request, None)).unwrap(); // TODO - handle errors.
    Ok(Client {
        client,
        success_callback,
        failure_callback,
        runtime,
    })
}

/// Creates a new client with the given configuration.
/// The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns.
/// All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
///
/// # Safety
///
/// * `config` must be a valid [`ConnectionConfig`] pointer. See the safety documentation of [`create_client_internal`].
#[allow(rustdoc::private_intra_doc_links)]
#[no_mangle]
pub unsafe extern "C" fn create_client(
    config: *const ConnectionConfig,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> *const c_void {
    match unsafe { create_client_internal(config, success_callback, failure_callback) } {
        Err(_) => std::ptr::null(), // TODO - log errors
        Ok(client) => Box::into_raw(Box::new(client)) as *const c_void,
    }
}

/// Closes the given client, deallocating it from the heap.
///
/// # Safety
///
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Box<Client>`] via [`Box::from_raw`]. See the safety documentation of [`std::boxed::Box::from_raw`].
#[no_mangle]
pub unsafe extern "C" fn close_client(client_ptr: *const c_void) {
    let client_ptr = unsafe { Box::from_raw(client_ptr as *mut Client) };
    let _runtime_handle = client_ptr.runtime.enter();
    drop(client_ptr);
}

/// Execute a command.
/// Expects that arguments will be kept valid until the callback is called.
///
/// # Safety
///
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Box<Client>`] via [`Box::from_raw`]. See the safety documentation of [`std::boxed::Box::from_raw`].
/// * `key` and `value` must not be `null`.
/// * `key` and `value` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
/// * `key` and `value` must be kept valid until the callback is called.
/// * `route_info` could be `null`, but if it is not `null`, it must be a valid [`RouteInfo`] pointer. See the safety documentation of [`create_route`].
#[allow(rustdoc::private_intra_doc_links)]
#[no_mangle]
pub unsafe extern "C" fn command(
    client_ptr: *const c_void,
    callback_index: usize,
    request_type: RequestType,
    args: *const *mut c_char,
    arg_count: u32,
    route_info: *const RouteInfo,
) {
    let client = unsafe { Box::leak(Box::from_raw(client_ptr as *mut Client)) };

    // The safety of these needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let ptr_address = client_ptr as usize;
    let args_address = args as usize;

    let Some(mut cmd) = request_type.get_command() else {
        unsafe {
            let client = Box::leak(Box::from_raw(ptr_address as *mut Client));
            (client.failure_callback)(callback_index); // TODO - report errors
            return;
        }
    };

    let args_slice = unsafe {
        std::slice::from_raw_parts(args_address as *const *mut c_char, arg_count as usize)
    };
    for arg in args_slice {
        let c_str = unsafe { CStr::from_ptr(*arg as *mut c_char) };
        cmd.arg(c_str.to_bytes());
    }

    let route = create_route(route_info, &cmd);

    let mut client_clone = client.client.clone();
    client.runtime.spawn(async move {
        let result = client_clone
            .send_command(&cmd, route)
            .await
            .and_then(Option::<CString>::from_owned_redis_value);
        unsafe {
            let client = Box::leak(Box::from_raw(ptr_address as *mut Client));
            match result {
                Ok(None) => (client.success_callback)(callback_index, std::ptr::null()),
                Ok(Some(c_str)) => (client.success_callback)(callback_index, c_str.as_ptr()),
                Err(err) => {
                    dbg!(err); // TODO - report errors
                    (client.failure_callback)(callback_index);
                }
            };
        }
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
            logger_core::Level::Off => Level::Off,
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
            Level::Off => logger_core::Level::Off,
        }
    }
}

/// Unsafe function because creating string from pointer.
///
/// # Safety
///
/// * `message` and `log_identifier` must not be `null`.
/// * `message` and `log_identifier` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
#[no_mangle]
#[allow(improper_ctypes_definitions)]
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

/// Unsafe function because creating string from pointer.
///
/// # Safety
///
/// * `file_name` must not be `null`.
/// * `file_name` must be able to be safely casted to a valid [`CStr`] via [`CStr::from_ptr`]. See the safety documentation of [`std::ffi::CStr::from_ptr`].
#[no_mangle]
#[allow(improper_ctypes_definitions)]
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
