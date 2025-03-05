// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use tokio::runtime::Runtime;

struct FFIHandle {
    runtime: Runtime,
    handle: crate::apihandle::Handle,
}

mod helpers {
    use std::ffi::c_char;
    use std::str::Utf8Error;

    pub fn grab_str(input: *const c_char) -> Result<Option<String>, Utf8Error> {
        if input.is_null() {
            return Ok(None);
        }
        unsafe {
            let c_str = std::ffi::CStr::from_ptr(input);
            match c_str.to_str() {
                Ok(d) => Ok(Some(d.to_string())),
                Err(e) => Err(e),
            }
        }
    }

    pub fn grab_vec<TIn, TOut, TErr>(
        input: *const TIn,
        len: usize,
        f: impl Fn(&TIn) -> Result<TOut, TErr>,
    ) -> Result<Vec<TOut>, TErr> {
        let mut result = Vec::with_capacity(len);
        unsafe {
            for i in 0..len {
                let it: *const TIn = input.offset((size_of::<TIn>() * i) as isize);
                result.push(f(&*it)?);
            }
            Ok(result)
        }
    }
}

mod calls {
    use crate::apihandle::Handle;
    use crate::exports::{helpers, FFIHandle};
    use glide_core::client;
    use glide_core::client::ConnectionError;
    use glide_core::request_type::RequestType;
    use redis::FromRedisValue;
    use std::ffi::{c_int, c_void, CString};
    use std::os::raw::c_char;
    use std::ptr::null;
    use std::str::{FromStr, Utf8Error};
    use tokio::runtime::Builder;

    /// # Summary
    /// Special method to free the returned strings.
    /// MUST be used instead of calling c-free!
    #[no_mangle]
    pub unsafe extern "C" fn csharp_free_string(input: *const c_char) {
        logger_core::log_info("csharp_ffi", "Entered csharp_free_string");
        let str = CString::from_raw(input as *mut c_char);
        drop(str);
    }

    #[repr(C)]
    pub struct InitResult {
        pub success: c_int,
        pub logger_level: ELoggerLevel,
    }

    #[repr(C)]
    pub enum ELoggerLevel {
        None = 0,
        Error = 1,
        Warn = 2,
        Info = 3,
        Debug = 4,
        Trace = 5,
        Off = 6,
    }

    /// # Summary
    /// Initializes essential parts of the system.
    /// Supposed to be called once only.
    ///
    /// # Parameters
    /// - ***in_minimal_level*** The minimum file log level
    /// - ***in_file_name*** The file name to log to
    ///
    /// # Input Safety (in_...)
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    ///
    /// # Output Safety (out_... / return ...)
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    ///
    /// # Reference Safety (ref_...)
    /// Any reference data is considered "caller owned".
    ///
    /// # Freeing data allocated by the API
    /// To free data returned by the API, use the corresponding `free_...` methods of the API.
    /// It is **not optional** to call them to free data allocated by the API!
    #[no_mangle]
    pub extern "C" fn csharp_system_init(
        in_minimal_level: ELoggerLevel,
        in_file_name: *const c_char,
    ) -> InitResult {
        logger_core::log_info("csharp_ffi", "Entered csharp_system_init");
        // ToDo: Rebuild into having a log-callback so that we can manage logging at the dotnet side
        let file_name = match helpers::grab_str(in_file_name) {
            Ok(d) => d,
            Err(_) => {
                return InitResult {
                    logger_level: ELoggerLevel::Off,
                    success: false as c_int,
                }
            }
        };
        let logger_level = match file_name {
            None => logger_core::init(
                match in_minimal_level {
                    ELoggerLevel::Error => Some(logger_core::Level::Error),
                    ELoggerLevel::Warn => Some(logger_core::Level::Warn),
                    ELoggerLevel::Info => Some(logger_core::Level::Info),
                    ELoggerLevel::Debug => Some(logger_core::Level::Debug),
                    ELoggerLevel::Trace => Some(logger_core::Level::Trace),
                    ELoggerLevel::Off => Some(logger_core::Level::Off),
                    ELoggerLevel::None => None,
                },
                None,
            ),
            Some(file_name) => logger_core::init(
                match in_minimal_level {
                    ELoggerLevel::Error => Some(logger_core::Level::Error),
                    ELoggerLevel::Warn => Some(logger_core::Level::Warn),
                    ELoggerLevel::Info => Some(logger_core::Level::Info),
                    ELoggerLevel::Debug => Some(logger_core::Level::Debug),
                    ELoggerLevel::Trace => Some(logger_core::Level::Trace),
                    ELoggerLevel::Off => Some(logger_core::Level::Off),
                    ELoggerLevel::None => None,
                },
                Some(file_name.as_str()),
            ),
        };

        InitResult {
            success: true as c_int,
            logger_level: match logger_level {
                logger_core::Level::Error => ELoggerLevel::Error,
                logger_core::Level::Warn => ELoggerLevel::Warn,
                logger_core::Level::Info => ELoggerLevel::Info,
                logger_core::Level::Debug => ELoggerLevel::Debug,
                logger_core::Level::Trace => ELoggerLevel::Trace,
                logger_core::Level::Off => ELoggerLevel::Off,
            },
        }
    }

    #[repr(C)]
    #[derive(PartialEq)]
    pub struct CreateClientHandleResult {
        pub result: ECreateClientHandleCode,
        pub client_handle: *const c_void,
        pub error_string: *const c_char,
    }

    #[repr(C)]
    #[derive(PartialEq, Eq, Debug, Clone, Copy, Hash)]
    pub enum ECreateClientHandleCode {
        Success = 0,
        ParameterError = 1,
        ThreadCreationError = 2,
        ConnectionTimedOutError = 3,
        ConnectionToFailedError = 4,
        ConnectionToClusterFailed = 5,
        ConnectionIoError = 6,
    }

    #[repr(C)]
    pub struct NodeAddress {
        pub host: *const c_char,
        pub port: u16,
    }
    pub enum Utf8OrEmptyError {
        Utf8Error(Utf8Error),
        Empty,
    }

    /// # Summary
    /// Creates a new client to the given address.
    ///
    /// # Input Safety (in_...)
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    ///
    /// # Output Safety (out_... / return ...)
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    ///
    /// # Reference Safety (ref_...)
    /// Any reference data is considered "caller owned".
    ///
    /// # Freeing data allocated by the API
    /// To free data returned by the API, use the corresponding `free_...` methods of the API.
    /// It is **not optional** to call them to free data allocated by the API!
    #[no_mangle]
    pub extern "C" fn csharp_create_client_handle(
        in_host: *const NodeAddress,
        in_host_count: u16,
        in_use_tls: c_int,
    ) -> CreateClientHandleResult {
        let addresses = match helpers::grab_vec(
            in_host,
            in_host_count as usize,
            |it| -> Result<client::NodeAddress, Utf8OrEmptyError> {
                let host = match helpers::grab_str(it.host) {
                    Ok(d) => d,
                    Err(e) => return Err(Utf8OrEmptyError::Utf8Error(e)),
                };
                let host = match host {
                    Some(host) => host,
                    None => return Err(Utf8OrEmptyError::Empty),
                };
                let port = it.port;
                Ok(client::NodeAddress { host, port })
            },
        ) {
            Ok(d) => d,
            Err(e) => match e {
                Utf8OrEmptyError::Utf8Error(e) => {
                    return CreateClientHandleResult {
                        result: ECreateClientHandleCode::ParameterError,
                        client_handle: null(),
                        error_string: match CString::from_str(e.to_string().as_str()) {
                            Ok(d) => d.into_raw(),
                            Err(_) => null(),
                        },
                    }
                }
                Utf8OrEmptyError::Empty => {
                    return CreateClientHandleResult {
                        result: ECreateClientHandleCode::ParameterError,
                        client_handle: null(),
                        error_string: match CString::from_str("Null value passed for host") {
                            Ok(d) => d.into_raw(),
                            Err(_) => null(),
                        },
                    }
                }
            },
        };

        let request = client::ConnectionRequest {
            addresses,
            tls_mode: match in_use_tls != 0 {
                true => Some(client::TlsMode::SecureTls),
                false => Some(client::TlsMode::NoTls),
            },
            ..Default::default()
        };

        let runtime = match Builder::new_multi_thread()
            .enable_all()
            .thread_name("GLIDE C# thread")
            .build()
        {
            Ok(d) => d,
            Err(e) => {
                return CreateClientHandleResult {
                    result: ECreateClientHandleCode::ThreadCreationError,
                    client_handle: null(),
                    error_string: match CString::from_str(e.to_string().as_str()) {
                        Ok(d) => d.into_raw(),
                        Err(_) => null(),
                    },
                }
            }
        };
        let handle: Handle;
        {
            let _runtime_handle = runtime.enter();
             handle = match runtime.block_on(Handle::create(request)) {
                Ok(d) => d,
                Err(e) => {
                    let str = e.to_string();
                    return CreateClientHandleResult {
                        result: match e {
                            // ToDo: Improve error return codes even further to get more fine control at dotnet side
                            ConnectionError::Standalone(_) => {
                                ECreateClientHandleCode::ConnectionToFailedError
                            }
                            ConnectionError::Cluster(_) => {
                                ECreateClientHandleCode::ConnectionToClusterFailed
                            }
                            ConnectionError::Timeout => {
                                ECreateClientHandleCode::ConnectionTimedOutError
                            }
                            ConnectionError::IoError(_) => ECreateClientHandleCode::ConnectionIoError,
                        },
                        client_handle: null(),
                        error_string: match CString::from_str(str.as_str()) {
                            Ok(d) => d.into_raw(),
                            Err(_) => null(),
                        },
                    };
                }
            };
        }
        CreateClientHandleResult {
            result: ECreateClientHandleCode::Success,
            client_handle: Box::into_raw(Box::new(FFIHandle { runtime, handle })) as *const c_void,
            error_string: null(),
        }
    }

    /// # Summary
    /// Frees the previously created client_handle, making it unusable.
    ///
    /// # Input Safety (in_...)
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    ///
    /// # Output Safety (out_... / return ...)
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    ///
    /// # Reference Safety (ref_...)
    /// Any reference data is considered "caller owned".
    ///
    /// # Freeing data allocated by the API
    /// To free data returned by the API, use the corresponding `free_...` methods of the API.
    /// It is **not optional** to call them to free data allocated by the API!
    #[no_mangle]
    pub extern "C" fn csharp_free_client_handle(in_client_ptr: *const c_void) {
        logger_core::log_info("csharp_ffi", "Entered csharp_free_client_handle");
        let client_ptr = unsafe { Box::from_raw(in_client_ptr as *mut FFIHandle) };
        let _runtime_handle = client_ptr.runtime.enter();
        drop(client_ptr);
    }

    #[repr(C)]
    pub struct CommandResult {
        success: c_int,
        error_string: *const c_char,
    }

    pub(crate) type CommandCallback = unsafe extern "C" fn(
        in_data: *const c_void,
        out_success: c_int,
        ref_output: *const c_char,
    );

    /// # Summary
    /// Method to invoke a command.
    ///
    /// # Params
    /// ***in_client_ptr*** An active client handle
    /// ***in_callback*** A callback method with the signature:
    ///                   `void Callback(void * in_data, int out_success, const char * ref_output)`.
    ///                   The first arg contains the data of the parameter *in_callback_data*;
    ///                   the second arg indicates whether the third parameter contains the error or result;
    ///                   the third arg contains either the result, the error or null and is freed by the API,
    ///                   not the calling code.
    /// ***in_callback_data*** The data to be passed in to *in_callback*
    /// ***in_request_type*** The type of command to issue
    /// ***in_args*** A C-String array of arguments to be passed, with the size of `in_args_count` and zero terminated.
    /// ***in_args_count*** The number of arguments in *in_args*
    ///
    /// # Input Safety (in_...)
    /// The data passed in is considered "caller responsibility".
    /// Any pointers hence will be left unreleased after leaving.
    ///
    /// # Output Safety (out_... / return ...)
    /// The data returned is considered "caller responsibility".
    /// The caller must release any non-null pointers.
    ///
    /// # Reference Safety (ref_...)
    /// Any reference data is considered "caller owned".
    ///
    /// # Freeing data allocated by the API
    /// To free data returned by the API, use the corresponding `free_...` methods of the API.
    /// It is **not optional** to call them to free data allocated by the API!
    #[no_mangle]
    pub extern "C" fn csharp_command(
        in_client_ptr: *const c_void,
        in_callback: CommandCallback,
        in_callback_data: *const c_void,
        in_request_type: RequestType,
        in_args: *const *const c_char,
        in_args_count: c_int,
    ) -> CommandResult {
        logger_core::log_info("csharp_ffi", "Entered csharp_command");
        if in_client_ptr.is_null() {
            return CommandResult {
                success: false as c_int,
                error_string: match CString::from_str("Null handle passed") {
                    Ok(d) => d.into_raw(),
                    Err(_) => null(),
                },
            };
        }
        let args = match helpers::grab_vec(
            in_args,
            in_args_count as usize,
            |it| -> Result<String, Utf8OrEmptyError> {
                match helpers::grab_str(*it) {
                    Ok(d) => match d {
                        None => Err(Utf8OrEmptyError::Empty),
                        Some(d) => Ok(d),
                    },
                    Err(e) => Err(Utf8OrEmptyError::Utf8Error(e)),
                }
            },
        ) {
            Ok(d) => d,
            Err(e) => match e {
                Utf8OrEmptyError::Utf8Error(e) => {
                    return CommandResult {
                        success: false as c_int,
                        error_string: match CString::from_str(e.to_string().as_str()) {
                            Ok(d) => d.into_raw(),
                            Err(_) => null(),
                        },
                    }
                }
                Utf8OrEmptyError::Empty => {
                    return CommandResult {
                        success: false as c_int,
                        error_string: match CString::from_str("Null value passed for host") {
                            Ok(d) => d.into_raw(),
                            Err(_) => null(),
                        },
                    }
                }
            },
        };
        let cmd = match in_request_type.get_command() {
            None => {
                return CommandResult {
                    success: false as c_int,
                    error_string: match CString::from_str("Unknown request type") {
                        Ok(d) => d.into_raw(),
                        Err(_) => null(),
                    },
                }
            }
            Some(d) => d,
        };
        let callback = in_callback;
        let callback_data = in_callback_data as usize;

        let ffi_handle = unsafe { Box::leak(Box::from_raw(in_client_ptr as *mut FFIHandle)) };
        let handle = ffi_handle.handle.clone();
        ffi_handle.runtime.spawn(async move {
            logger_core::log_info("csharp_ffi", "Entered command task");
            let data = match handle.command(cmd, args).await {
                Ok(d) => d,
                Err(e) => {
                    let message = match CString::from_str(e.to_string().as_str()) {
                        Ok(d) => d.into_raw() as *const c_char,
                        Err(_) => null(),
                    };
                    logger_core::log_info("csharp_ffi", "Error in command task");
                    unsafe { callback(callback_data as *const c_void, false as c_int, message) };
                    return;
                }
            };
            let data = Option::<CString>::from_owned_redis_value(data);
            match data {
                Ok(None) => {
                    logger_core::log_info("csharp_ffi", "No data returned from command task");
                    unsafe {
                        callback(callback_data as *const c_void, true as c_int, null())
                    }
                },
                Ok(Some(data)) => {
                    logger_core::log_info("csharp_ffi", "Data returned from command task");
                    unsafe {
                        callback(
                            callback_data as *const c_void,
                            true as c_int,
                            data.into_raw() as *const c_char,
                        )
                    }
                },
                Err(e) => {
                    let message = match CString::from_str(e.to_string().as_str()) {
                        Ok(d) => d.into_raw() as *const c_char,
                        Err(_) => null(),
                    };
                    logger_core::log_info("csharp_ffi", "Error in command task");
                    unsafe { callback(callback_data as *const c_void, false as c_int, message) };
                }
            };
        });

        CommandResult {
            success: true as c_int,
            error_string: null(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::exports::calls::{ELoggerLevel, NodeAddress};
    use glide_core::request_type::RequestType;
    use std::ffi::{c_int, c_void, CString};
    use std::os::raw::c_char;
    use std::ptr::null;
    use std::rc::Rc;
    use std::str::FromStr;
    use std::thread;
    use std::time::Duration;

    const HOST: &str = "localhost";
    const PORT: u16 = 49493;

    #[test]
    fn test_system_init() {
        assert_ne!(0, calls::csharp_system_init(ELoggerLevel::None, null()).success);
    }
    #[test]
    fn test_create_client_handle() {
        assert_ne!(0, calls::csharp_system_init(ELoggerLevel::None, null()).success);
        let address = CString::from_str(HOST).unwrap();
        let addresses = vec![NodeAddress {
            host: address.as_ptr(),
            port: PORT,
        }];
        let client_result = calls::csharp_create_client_handle(
            addresses.as_ptr(),
            1,
            0,
        );
        assert_eq!(
            calls::ECreateClientHandleCode::Success,
            client_result.result
        );
        assert_ne!(null(), client_result.client_handle);
        calls::csharp_free_client_handle(client_result.client_handle);
    }
    #[test]
    fn test_call_command() {
        assert_ne!(0, calls::csharp_system_init(ELoggerLevel::None, null()).success);
        let address = CString::from_str(HOST).unwrap();
        let addresses = vec![NodeAddress {
            host: address.as_ptr(),
            port: PORT,
        }];
        let client_result = calls::csharp_create_client_handle(
            addresses.as_ptr(),
            1,
            0,
        );
        assert_eq!(
            calls::ECreateClientHandleCode::Success,
            client_result.result
        );
        assert_ne!(null(), client_result.client_handle);

        let str = CString::from_str("test").unwrap();
        let ptr = str.as_ptr();
        let d = &[ptr];
        let flag = Rc::new(false);
        unsafe extern "C" fn test(
            in_data: *const c_void,
            _out_success: c_int,
            _ref_output: *const c_char,
        ) {
            let mut flag = Rc::from_raw(in_data as *mut bool);
            *flag = true;
        }
        let command_result = calls::csharp_command(
            client_result.client_handle,
            test as calls::CommandCallback,
            Rc::into_raw(flag.clone()) as *const c_void,
            RequestType::Get,
            d.as_ptr(),
            1,
        );

        for _ in 0..200 /* 20s */ {
            if *flag {
                break;
            }
            thread::sleep(Duration::from_millis(100));
        }
        assert!(*flag);

        calls::csharp_free_client_handle(client_result.client_handle);
    }
}
