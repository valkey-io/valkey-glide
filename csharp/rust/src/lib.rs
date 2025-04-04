// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

mod ffi;
use ffi::{
    convert_double_pointer_to_vec, create_connection_request, create_route, ConnectionConfig,
    ResponseValue, RouteInfo,
};
use glide_core::{
    client::Client as GlideClient,
    errors::{error_message, error_type, RequestErrorType},
    request_type::RequestType,
};
use std::{
    ffi::{c_char, c_void, CStr, CString},
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

pub struct Client {
    runtime: Runtime,
    core: Arc<CommandExecutionCore>,
}

// TODO safety
// TODO: merge with #3395 https://github.com/valkey-io/valkey-glide/pull/3395

/// Success callback that is called when a command succeeds.
///
/// The success callback needs to copy the given data synchronously, since it will be dropped by Rust once the callback returns.
/// The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// `index` is a baton-pass back to the caller language to uniquely identify the promise.
/// `message` is the value returned by the command. The 'message' is managed by Rust and is freed when the callback returns control back to the caller.
pub type SuccessCallback = unsafe extern "C" fn(usize, *const ResponseValue) -> ();

/// Failure callback that is called when a command fails.
///
/// The failure callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns.
/// The callback should be offloaded to a separate thread in order not to exhaust the client's thread pool.
///
/// * `index` is a baton-pass back to the caller language to uniquely identify the promise.
/// * `error_message` is an UTF-8 string storing the error message returned by server for the failed command.
///   The `error_message` is managed by Rust and is freed when the callback returns control back to the caller.
/// * `error_type` is the type of error returned by glide-core, depending on the [`RedisError`](redis::RedisError) returned.
pub type FailureCallback = unsafe extern "C" fn(
    index: usize,
    error_message: *const c_char,
    error_type: RequestErrorType,
) -> ();

struct CommandExecutionCore {
    client: GlideClient,
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
}

// TODO make this async if possible
/// Creates a new client with the given configuration.
/// The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns.
/// All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
///
/// # Safety
///
/// * `config` must be a valid [`ConnectionConfig`] pointer. See the safety documentation of [`create_connection_request`].
#[allow(rustdoc::private_intra_doc_links)]
#[no_mangle]
pub unsafe extern "C" fn create_client(
    config: *const ConnectionConfig,
    success_callback: SuccessCallback,
    failure_callback: FailureCallback,
) {
    let request = unsafe { create_connection_request(config) };
    let runtime = Builder::new_multi_thread()
        .enable_all()
        .thread_name("GLIDE C# thread")
        .build()
        .unwrap();

    let _runtime_handle = runtime.enter();
    let res = runtime.block_on(GlideClient::new(request, None));
    match res {
        Ok(client) => {
            let core = Arc::new(CommandExecutionCore {
                success_callback,
                failure_callback,
                client,
            });

            let client_ptr = Arc::into_raw(Arc::new(Client { runtime, core }));
            unsafe { success_callback(0, client_ptr as *const ResponseValue) };
        }
        Err(err) => {
            let err_ptr = CString::into_raw(
                CString::new(err.to_string()).expect("Couldn't convert error message to CString"),
            );
            unsafe { failure_callback(0, err_ptr, RequestErrorType::Disconnect) };
            _ = CString::from_raw(err_ptr);
        }
    }
}

/// Closes the given client, deallocating it from the heap.
/// This function should only be called once per pointer created by [`create_client`].
/// After calling this function the `client_ptr` is not in a valid state.
///
/// # Safety
///
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Box<Client>`] via [`Box::from_raw`]. See the safety documentation of [`Box::from_raw`].
#[no_mangle]
pub extern "C" fn close_client(client_ptr: *const c_void) {
    assert!(!client_ptr.is_null());
    // This will bring the strong count down to 0 once all client requests are done.
    unsafe { Arc::decrement_strong_count(client_ptr as *const Client) };
}

// TODO handle panic if possible
/// Execute a command.
/// Expects that arguments will be kept valid until the callback is called.
///
/// # Safety
/// * `client_ptr` must not be `null`.
/// * `client_ptr` must be able to be safely casted to a valid [`Box<Client>`] via [`Box::from_raw`]. See the safety documentation of [`Box::from_raw`].
/// * This function should only be called should with a pointer created by [`create_client`], before [`close_client`] was called with the pointer.
/// * `args` and `args_len` must not be `null`.
/// * `data` must point to `arg_count` consecutive string pointers.
/// * `args_len` must point to `arg_count` consecutive string lengths. See the safety documentation of [`convert_double_pointer_to_vec`].
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
        let err_str = "Couldn't fetch command type";
        let err_ptr = CString::into_raw(
            CString::new(err_str).expect("Couldn't convert error message to CString"),
        );
        unsafe { (core.failure_callback)(callback_index, err_ptr, RequestErrorType::ExecAbort) };
        _ = CString::from_raw(err_ptr);
        return;
    };
    for command_arg in arg_vec {
        cmd.arg(command_arg);
    }

    let route = create_route(route_info, &cmd);

    client.runtime.spawn(async move {
        let result = core.client.clone().send_command(&cmd, route).await;
        unsafe {
            match result {
                Ok(value) => {
                    let ptr = Box::into_raw(Box::new(ResponseValue::from_value(value)));
                    (core.success_callback)(callback_index, ptr);
                }
                Err(err) => {
                    let err_str = error_message(&err);
                    let err_ptr = CString::into_raw(
                        CString::new(err_str).expect("Couldn't convert error message to CString"),
                    );
                    (core.failure_callback)(callback_index, err_ptr, error_type(&err));
                    _ = CString::from_raw(err_ptr);
                }
            };
        };
    });
}

/// Free the memory allocated for a [`ResponseValue`] and nested structure.
///
/// # Safety
/// * `ptr` must not be `null`.
/// * `ptr` must be able to be safely casted to a valid [`Box<ResponseValue>`] via [`Box::from_raw`]. See the safety documentation of [`Box::from_raw`].
#[allow(rustdoc::private_intra_doc_links)]
#[no_mangle]
pub unsafe extern "C" fn free_respose(ptr: *mut ResponseValue) {
    unsafe {
        let val = Box::leak(Box::from_raw(ptr));
        val.free_memory();
    }
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
