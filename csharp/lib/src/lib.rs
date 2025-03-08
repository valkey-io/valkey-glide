// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::client;
use glide_core::client::Client as GlideClient;
use glide_core::request_type::RequestType;
use redis::{RedisResult, Value};
use std::{
    ffi::{c_char, c_void, CStr},
    slice::from_raw_parts,
};
use tokio::runtime::Builder;
use tokio::runtime::Runtime;

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
    client: GlideClient,
    success_callback: unsafe extern "C" fn(usize, i32, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (), // TODO - add specific error codes
    runtime: Runtime,
}

fn create_connection_request(host: String, port: u32, use_tls: bool) -> client::ConnectionRequest {
    let address_info = client::NodeAddress {
        host,
        port: port as u16,
    };
    let addresses = vec![address_info];
    client::ConnectionRequest {
        addresses,
        tls_mode: if use_tls {
            Some(client::TlsMode::SecureTls)
        } else {
            Some(client::TlsMode::NoTls)
        },
        ..Default::default()
    }
}

fn create_client_internal(
    host: *const c_char,
    port: u32,
    use_tls: bool,
    success_callback: unsafe extern "C" fn(usize, i32, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> RedisResult<Client> {
    let host_cstring = unsafe { CStr::from_ptr(host) };
    let host_string = host_cstring.to_str()?.to_string();
    let request = create_connection_request(host_string, port, use_tls);
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

/// Creates a new client to the given address. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns. All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
#[no_mangle]
pub extern "C" fn create_client(
    host: *const c_char,
    port: u32,
    use_tls: bool,
    success_callback: unsafe extern "C" fn(usize, i32, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> *const c_void {
    match create_client_internal(host, port, use_tls, success_callback, failure_callback) {
        Err(_) => std::ptr::null(), // TODO - log errors
        Ok(client) => Box::into_raw(Box::new(client)) as *const c_void,
    }
}

#[no_mangle]
pub extern "C" fn close_client(client_ptr: *const c_void) {
    let client_ptr = unsafe { Box::from_raw(client_ptr as *mut Client) };
    let _runtime_handle = client_ptr.runtime.enter();
    drop(client_ptr);
}

// TODO safety
/// Converts a double pointer to a vec.
///
/// # Safety
///
/// `convert_double_pointer_to_vec` returns a `Vec` of u8 slice which holds pointers of `go`
/// strings. The returned `Vec<&'a [u8]>` is meant to be copied into Rust code. Storing them
/// for later use will cause the program to crash as the pointers will be freed by go's gc
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

/// Expects that key and value will be kept valid until the callback is called.
///
/// # Safety
/// TODO merge with [#3321](https://github.com/valkey-io/valkey-glide/pull/3321)
#[no_mangle]
pub unsafe extern "C" fn command(
    client_ptr: *const c_void,
    callback_index: usize,
    request_type: RequestType,
    args: *const *mut c_char,
    arg_count: u32,
    args_len: *const u32,
) {
    let client = unsafe { Box::leak(Box::from_raw(client_ptr as *mut Client)) };

    // The safety of these needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let ptr_address = client_ptr as usize;

    let mut client_clone = client.client.clone();

    let arg_vec =
        unsafe { convert_double_pointer_to_vec(args as *const *const c_void, arg_count, args_len) };

    // Create the command outside of the task to ensure that the command arguments passed are still valid
    let Some(mut cmd) = request_type.get_command() else {
        unsafe {
            (client.failure_callback)(callback_index); // TODO - report errors
            return;
        }
    };
    for command_arg in arg_vec {
        cmd.arg(command_arg);
    }

    client.runtime.spawn(async move {
        let result = client_clone.send_command(&cmd, None).await;
        unsafe {
            let client = Box::leak(Box::from_raw(ptr_address as *mut Client));
            match result {
                Ok(Value::SimpleString(text)) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(text.into_bytes());
                    (client.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::BulkString(text)) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(text);
                    (client.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::VerbatimString { format: _, text }) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(text.into_bytes());
                    (client.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::Okay) => {
                    let (vec_ptr, len) = convert_vec_to_pointer(String::from("OK").into_bytes());
                    (client.success_callback)(callback_index, len as i32, vec_ptr as *const c_char)
                }
                Ok(Value::Nil) => (client.success_callback)(callback_index, 0, std::ptr::null()),
                Err(err) => {
                    dbg!(err); // TODO - report errors
                    (client.failure_callback)(callback_index)
                }
                Ok(value) => {
                    dbg!(value); // TODO - handle other response types
                    (client.failure_callback)(callback_index)
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
