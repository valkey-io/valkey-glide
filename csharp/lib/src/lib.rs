// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::client;
use glide_core::client::Client as GlideClient;
use glide_core::request_type::RequestType;
use redis::{FromRedisValue, RedisResult};
use std::{
    ffi::{c_void, CStr, CString},
    os::raw::c_char,
    sync::Arc,
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

pub struct Client {
    runtime: Runtime,
    core: Arc<CommandExecutionCore>,
}

struct CommandExecutionCore {
    client: GlideClient,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (), // TODO - add specific error codes
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
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
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
    let core = Arc::new(CommandExecutionCore {
        success_callback,
        failure_callback,
        client,
    });
    Ok(Client { runtime, core })
}

/// Creates a new client to the given address. The success callback needs to copy the given string synchronously, since it will be dropped by Rust once the callback returns.
/// All callbacks should be offloaded to separate threads in order not to exhaust the client's thread pool.
#[no_mangle]
pub extern "C" fn create_client(
    host: *const c_char,
    port: u32,
    use_tls: bool,
    success_callback: unsafe extern "C" fn(usize, *const c_char) -> (),
    failure_callback: unsafe extern "C" fn(usize) -> (),
) -> *const c_void {
    match create_client_internal(host, port, use_tls, success_callback, failure_callback) {
        Err(_) => std::ptr::null(), // TODO - log errors
        Ok(client) => Arc::into_raw(Arc::new(client)) as *const c_void,
    }
}

/// # Safety
///
/// This function should only be called once per pointer created by [create_client]. After calling this function
/// the `client_ptr` is not in a valid state.
#[no_mangle]
pub extern "C" fn close_client(client_adapter_ptr: *const c_void) {
    assert!(!client_adapter_ptr.is_null());
    // This will bring the strong count down to 0 once all client requests are done.
    unsafe { Arc::decrement_strong_count(client_adapter_ptr as *const Client) };
}

/// Expects that key and value will be kept valid until the callback is called.
///
/// # Safety
///
/// This function should only be called should with a pointer created by [create_client], before [close_client] was called with the pointer.
#[no_mangle]
pub unsafe extern "C" fn command(
    client_ptr: *const c_void,
    callback_index: usize,
    request_type: RequestType,
    args: *const *mut c_char,
    arg_count: u32,
) {
    let client = unsafe {
        // we increment the strong count to ensure that the client is not dropped just because we turned it into an Arc.
        Arc::increment_strong_count(client_ptr);
        Arc::from_raw(client_ptr as *mut Client)
    };

    // The safety of these needs to be ensured by the calling code. Cannot dispose of the pointer before all operations have completed.
    let args_address = args as usize;

    let core = client.core.clone();
    client.runtime.spawn(async move {
        let Some(mut cmd) = request_type.get_command() else {
            unsafe {
                (core.failure_callback)(callback_index); // TODO - report errors
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

        let result = core
            .client
            .clone()
            .send_command(&cmd, None)
            .await
            .and_then(Option::<CString>::from_owned_redis_value);
        unsafe {
            match result {
                Ok(None) => (core.success_callback)(callback_index, std::ptr::null()),
                Ok(Some(c_str)) => (core.success_callback)(callback_index, c_str.as_ptr()),
                Err(_) => (core.failure_callback)(callback_index), // TODO - report errors
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
