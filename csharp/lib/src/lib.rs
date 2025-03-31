// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod ffi;
use ffi::{create_connection_request, create_route, ConnectionConfig, RouteInfo};
use glide_core::{client::Client as GlideClient, request_type::RequestType};
use redis::{RedisResult, Value};
use std::{
    ffi::{c_char, c_void, CStr},
    slice::from_raw_parts,
    sync::Arc,
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

// TODO define `SuccessCallback` and `FailureCallback` types

pub struct Client {
    runtime: Runtime,
    core: Arc<CommandExecutionCore>,
}

struct CommandExecutionCore {
    client: GlideClient,
    success_callback: unsafe extern "C" fn(usize, i32, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (), // TODO - add specific error codes
}

/// # Safety
///
/// * `config` must be a valid [`ConnectionConfig`] pointer. See the safety documentation of [`create_connection_request`].
unsafe fn create_client_internal(
    config: *const ConnectionConfig,
    success_callback: unsafe extern "C" fn(usize, i32, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> RedisResult<Client> {
    let request = unsafe { create_connection_request(config) };
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .thread_name("GLIDE C# thread")
        .build()?;
    let _runtime_handle = runtime.enter();
    let client = runtime.block_on(GlideClient::new(request, None)).unwrap(); // TODO - handle errors.
    let core = Arc::new(CommandExecutionCore {
        success_callback,
        failure_callback,
        client,
    });
    Ok(Client { runtime, core })
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
    success_callback: unsafe extern "C" fn(usize, i32, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> *const c_void {
    match unsafe { create_client_internal(config, success_callback, failure_callback) } {
        Err(_) => std::ptr::null(), // TODO - log errors
        Ok(client) => Arc::into_raw(Arc::new(client)) as *const c_void,
    }
}

/// Closes the given client, deallocating it from the heap.
/// This function should only be called once per pointer created by [`create_client`].
/// After calling this function the `client_ptr` is not in a valid state.
///
/// # Safety
///
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Box<Client>`] via [`Box::from_raw`]. See the safety documentation of [`std::boxed::Box::from_raw`].
#[no_mangle]
pub extern "C" fn close_client(client_ptr: *const c_void) {
    assert!(!client_ptr.is_null());
    // This will bring the strong count down to 0 once all client requests are done.
    unsafe { Arc::decrement_strong_count(client_ptr as *const Client) };
}

/// Converts a double pointer to a vec.
///
/// # Safety
///
/// * `data` and `data_len` must not be `null`.
/// * `data` must point to `len` consecutive string pointers.
/// * `data_len` must point to `len` consecutive string lengths.
/// * `data`, `data_len` and also each pointer stored in `data` must be able to be safely casted to a valid to a slice of the corresponding type via [`from_raw_parts`].
///   See the safety documentation of [`from_raw_parts`].
/// * The caller is responsible of freeing the allocated memory.
unsafe fn convert_double_pointer_to_vec<'a>(
    data: *const *const c_void,
    len: u32,
    data_len: *const u32,
) -> Vec<&'a [u8]> {
    let string_ptrs = unsafe { from_raw_parts(data, len as usize) };
    let string_lengths = unsafe { from_raw_parts(data_len, len as usize) };
    let mut result = Vec::<&[u8]>::with_capacity(string_ptrs.len());
    for (i, &str_ptr) in string_ptrs.iter().enumerate() {
        let slice = unsafe { from_raw_parts(str_ptr as *const u8, string_lengths[i] as usize) };
        result.push(slice);
    }
    result
}

fn convert_vec_to_pointer<T>(mut vec: Vec<T>) -> (*const T, usize) {
    vec.shrink_to_fit();
    let vec_ptr = vec.as_ptr();
    let len = vec.len();
    // TODO use `Box::into_raw`
    std::mem::forget(vec);
    (vec_ptr, len)
}

/// Execute a command.
/// Expects that arguments will be kept valid until the callback is called.
///
/// # Safety
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Box<Client>`] via [`Box::from_raw`]. See the safety documentation of [`std::boxed::Box::from_raw`].
/// * This function should only be called should with a pointer created by [`create_client`], before [`close_client`] was called with the pointer.
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
    args_len: *const u32,
    route_info: *const RouteInfo,
) {
    let client = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_ptr);
        Arc::from_raw(client_ptr as *mut Client)
    };
    let core = client.core.clone();

    let arg_vec =
        unsafe { convert_double_pointer_to_vec(args as *const *const c_void, arg_count, args_len) };

    // Create the command outside of the task to ensure that the command arguments passed are still valid
    let Some(mut cmd) = request_type.get_command() else {
        unsafe {
            (core.failure_callback)(callback_index); // TODO - report errors
            return;
        }
    };
    for command_arg in arg_vec {
        cmd.arg(command_arg);
    }

    let route = create_route(route_info, &cmd);

    client.runtime.spawn(async move {
        let result = core.client.clone().send_command(&cmd, route).await;
        unsafe {
            match result {
                Ok(Value::SimpleString(text)) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(text.into_bytes());
                    (core.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::BulkString(text)) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(text);
                    (core.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::VerbatimString { format: _, text }) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(text.into_bytes());
                    (core.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::Okay) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(String::from("OK").into_bytes());
                    (core.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::Nil) => (core.success_callback)(callback_index, 0, std::ptr::null()),
                Err(err) => {
                    dbg!(err); // TODO - report errors
                    (core.failure_callback)(callback_index)
                }
                Ok(value) => {
                    dbg!(value); // TODO - handle other response types
                    (core.failure_callback)(callback_index)
                }
            };
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
