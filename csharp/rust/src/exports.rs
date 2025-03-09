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
    use redis::VerbatimFormat;
    use std::ffi::{c_double, c_int, c_long, c_void, CString, NulError};
    use std::fmt::{Formatter};
    use std::mem::forget;
    use std::os::raw::c_char;
    use std::panic::catch_unwind;
    use std::ptr::null;
    use std::str::{FromStr, Utf8Error};
    use tokio::runtime::Builder;

    /// # Summary
    /// Special method to free the returned values.
    /// MUST be used!
    #[no_mangle]
    pub unsafe extern "C-unwind" fn csharp_free_value(mut input: Value) {
        logger_core::log_debug("csharp_ffi", "Entered csharp_free_value");
        input.free_data();
    }
    /// # Summary
    /// Special method to free the returned strings.
    /// MUST be used instead of calling c-free!
    #[no_mangle]
    pub unsafe extern "C-unwind" fn csharp_free_string(input: *const c_char) {
        logger_core::log_debug("csharp_ffi", "Entered csharp_free_string");
        let str = CString::from_raw(input as *mut c_char);
        drop(str);
    }

    #[repr(C)]
    pub struct InitResult {
        pub success: c_int,
        pub logger_level: ELoggerLevel,
    }

    #[repr(C)]
    #[allow(dead_code)]
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
    pub extern "C-unwind" fn csharp_system_init(
        in_minimal_level: ELoggerLevel,
        in_file_name: *const c_char,
    ) -> InitResult {
        logger_core::log_debug("csharp_ffi", "Entered csharp_system_init");
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
    pub extern "C-unwind" fn csharp_create_client_handle(
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
                            ConnectionError::IoError(_) => {
                                ECreateClientHandleCode::ConnectionIoError
                            }
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
    pub extern "C-unwind" fn csharp_free_client_handle(in_client_ptr: *const c_void) {
        logger_core::log_debug("csharp_ffi", "Entered csharp_free_client_handle");
        let client_ptr = unsafe { Box::from_raw(in_client_ptr as *mut FFIHandle) };
        let _runtime_handle = client_ptr.runtime.enter();
        drop(client_ptr);
    }

    #[repr(C)]
    pub struct CommandResult {
        success: c_int,
        error_string: *const c_char,
    }

    pub(crate) type CommandCallback =
        unsafe extern "C-unwind" fn(data: *mut c_void, success: c_int, output: Value);

    #[repr(C)]
    pub union ValueUnion {
        i: c_long,
        f: c_double,
        ptr: *const c_void,
    }
    #[repr(C)]
    #[allow(dead_code)]
    pub enum ValueKind {
        /// # Summary
        /// A nil response from the server.
        ///
        /// # Implications for union
        /// Union value must be ignored.
        Nil,
        /// # Summary
        /// An integer response.  Note that there are a few situations
        /// in which redis actually returns a string for an integer which
        /// is why this library generally treats integers and strings
        /// the same for all numeric responses.
        ///
        /// # Implications for union
        /// Union value will be set as c_long.
        /// It can be safely consumed without freeing.
        Int,
        /// # Summary
        /// An arbitrary binary data, usually represents a binary-safe string.
        ///
        /// # Implications for union
        /// Union will, in ptr, contain an array of c_char (bytes).
        /// See CommandResult.length for the number of elements.
        /// ValueUnion.ptr MUST be freed.
        BulkString,
        /// # Summary
        /// A response containing an array with more data.
        /// This is generally used by redis to express nested structures.
        ///
        /// # Implications for union
        /// Union will, in ptr, contain an array of CommandResult's.
        /// See CommandResult.length for the number of elements.
        /// ValueUnion.ptr MUST be freed.
        Array,
        /// # Summary
        /// A simple string response, without line breaks and not binary safe.
        ///
        /// # Implications for union
        /// Union will, in ptr, contain a c_str.
        /// See CommandResult.length for the length of the string, excluding the zero byte.
        /// ValueUnion.ptr MUST be freed.
        SimpleString,
        /// # Summary
        /// A status response which represents the string "OK".
        ///
        /// # Implications for union
        /// Union value must be ignored.
        Okay,
        /// # Summary
        /// Unordered key,value list from the server. Use `as_map_iter` function.
        ///
        /// # Implications for union
        /// Union will, in ptr, contain an array of CommandResult's which are supposed to be interpreted as key-value pairs.
        /// See CommandResult.length for the number of pairs (aka: elements * 2).
        /// ValueUnion.ptr MUST be freed.
        Map,
        /// Placeholder
        /// ToDo: Figure out a way to map this to C-Memory
        Attribute,
        /// # Summary
        /// Unordered set value from the server.
        ///
        /// # Implications for union
        /// Union will, in ptr, contain an array of CommandResult's.
        /// See CommandResult.length for the number of elements.
        /// ValueUnion.ptr MUST be freed.
        Set,
        /// # Summary
        /// A floating number response from the server.
        ///
        /// # Implications for union
        /// Union value will be set as c_double.
        /// It can be safely consumed without freeing.
        Double,
        /// # Summary
        /// A boolean response from the server.
        ///
        /// # Implications for union
        /// Union value will be set as c_long.
        /// It can be safely consumed without freeing.
        Boolean,
        /// # Summary
        /// First String is format and other is the string
        ///
        /// # Implications for union
        /// Union will, in ptr, contain an array of CommandResult's.
        /// See CommandResult.length for the number of elements.
        /// ValueUnion.ptr MUST be freed.
        ///
        /// ## Remarks
        /// First result will be verbatim-kind
        /// Second will be string
        VerbatimString,
        /// # Summary
        /// Very large number that out of the range of the signed 64 bit numbers
        ///
        /// # Implications for union
        /// Union will, in ptr, contain a StringPair
        /// ValueUnion.ptr MUST be freed.
        BigNumber,
        /// # Summary
        /// Push data from the server.
        ///
        /// # Implications for union
        /// Union will, in ptr, contain an array of CommandResult's.
        /// See CommandResult.length for the number of elements.
        /// ValueUnion.ptr MUST be freed.
        ///
        /// ## Remarks
        /// First result will be push-kind
        /// Second will be array of results
        Push,
    }
    #[repr(C)]
    pub struct Value {
        pub kind: ValueKind,
        pub data: ValueUnion,
        pub length: c_long,
    }

    #[repr(C)]
    pub struct StringPair {
        pub a_start: *mut c_char,
        pub a_end: *mut c_char,
        pub b_start: *mut c_char,
        pub b_end: *mut c_char,
    }

    #[derive(Clone, PartialEq, Eq, Debug)]
    pub enum ValueError {
        NulError(NulError),
    }
    impl std::fmt::Display for ValueError {
        fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
            match self {
                ValueError::NulError(e) => e.fmt(f),
            }
        }
    }

    impl Value {
        // ToDo: Create a new "blob" creating method that first counts the bytes needed,
        //       allocates one big blob and secondly fills in the bytes in that blob, returning
        //       just that as ValueBlob to allow better large-scale result operations.
        unsafe fn from(value: &redis::Value) -> Result<Self, ValueError> {
            Ok(match value {
                redis::Value::Nil => Self {
                    data: ValueUnion { ptr: null() },
                    length: 0,
                    kind: ValueKind::Nil,
                },
                redis::Value::Int(i) => Self {
                    data: ValueUnion { i: *i as c_long },
                    length: 0,
                    kind: ValueKind::Int,
                },
                redis::Value::BulkString(d) => {
                    let mut d = d.clone();
                    d.shrink_to_fit();
                    assert_eq!(d.len(), d.capacity());
                    Self {
                        data: ValueUnion {
                            ptr: d.as_mut_ptr() as *mut c_void,
                        },
                        length: d.len() as c_long,
                        kind: ValueKind::SimpleString,
                    }
                }
                redis::Value::Array(values) => {
                    let mut values = values
                        .iter()
                        .map(|d| Value::from(d))
                        .collect::<Result<Vec<_>, _>>()?;
                    values.shrink_to_fit();
                    assert_eq!(values.len(), values.capacity());
                    let result = Self {
                        data: ValueUnion {
                            ptr: values.as_mut_ptr() as *mut c_void,
                        },
                        length: values.len() as c_long,
                        kind: ValueKind::Set,
                    };
                    forget(values);
                    result
                }
                redis::Value::SimpleString(s) => Self {
                    data: ValueUnion {
                        ptr: match CString::from_str(s.as_str()) {
                            Ok(d) => d.into_raw() as *mut c_void,
                            Err(e) => return Err(ValueError::NulError(e)),}
                    },
                    length: s.len() as c_long,
                    kind: ValueKind::SimpleString,
                },
                redis::Value::Okay => Self {
                    data: ValueUnion { ptr: null() },
                    length: 0,
                    kind: ValueKind::Okay,
                },
                redis::Value::Map(tuples) => {
                    let mut out_tuples = Vec::with_capacity(tuples.len() * 2);
                    for (k, v) in tuples {
                        out_tuples.push(Value::from(k));
                        out_tuples.push(Value::from(v));
                    }
                    out_tuples.shrink_to_fit();
                    Self {
                        data: ValueUnion {
                            ptr: out_tuples.as_mut_ptr() as *mut c_void,
                        },
                        length: tuples.len() as c_long,
                        kind: ValueKind::Map,
                    }
                }
                redis::Value::Attribute { .. } => {
                    todo!("Implement")
                }
                redis::Value::Set(values) => {
                    let mut values = values
                        .iter()
                        .map(|d| Value::from(d))
                        .collect::<Result<Vec<_>, _>>()?;
                    values.shrink_to_fit();
                    assert_eq!(values.len(), values.capacity());
                    let result = Self {
                        data: ValueUnion {
                            ptr: values.as_mut_ptr() as *mut c_void,
                        },
                        length: values.len() as c_long,
                        kind: ValueKind::Set,
                    };
                    forget(values);
                    result
                }
                redis::Value::Double(d) => Self {
                    data: ValueUnion { f: *d },
                    length: 0,
                    kind: ValueKind::Double,
                },
                redis::Value::Boolean(b) => Self {
                    data: ValueUnion { i: *b as c_long },
                    length: 0,
                    kind: ValueKind::Boolean,
                },
                redis::Value::VerbatimString { format, text } => {
                    let format_length = match format {
                        VerbatimFormat::Unknown(unknown) => unknown.len(),
                        VerbatimFormat::Markdown => "markdown".len(),
                        VerbatimFormat::Text => "text".len(),
                    };
                    let format = match format {
                        VerbatimFormat::Unknown(unknown) => unknown,
                        VerbatimFormat::Markdown => &"markdown".to_string(),
                        VerbatimFormat::Text => &"text".to_string(),
                    };
                    let mut vec = Vec::<u8>::with_capacity(
                        size_of::<StringPair>() + format_length + text.len(),
                    );
                    let out_vec = vec.as_mut_ptr(); // we leak here
                    let output = StringPair {
                        a_start: out_vec.add(size_of::<StringPair>()) as *mut c_char,
                        a_end: out_vec.add(size_of::<StringPair>() + format_length) as *mut c_char,
                        b_start: out_vec.add(size_of::<StringPair>() + format_length)
                            as *mut c_char,
                        b_end: out_vec.add(size_of::<StringPair>() + format_length + text.len())
                            as *mut c_char,
                    };
                    for i in 0..format_length {
                        *output.a_start.wrapping_add(i) = format.as_ptr().wrapping_add(i) as c_char
                    }
                    for i in 0..text.len() {
                        *output.b_start.wrapping_add(i) = text.as_ptr().wrapping_add(i) as c_char
                    }
                    Self {
                        length: vec.len() as c_long,
                        kind: ValueKind::VerbatimString,
                        data: ValueUnion {
                            ptr: out_vec as *mut c_void,
                        },
                    }
                }
                redis::Value::BigNumber(_) => {
                    todo!("Implement")
                }
                redis::Value::Push { .. } => {
                    todo!("Implement")
                }
            })
        }
    }

    impl Value {
        unsafe fn free_data(&mut self) {
            match self.kind {
                ValueKind::Nil => { /* empty */ }
                ValueKind::Int => { /* empty */ }
                ValueKind::BulkString => drop(Vec::from_raw_parts(
                    self.data.ptr as *mut u8,
                    self.length as usize,
                    self.length as usize,
                )),
                ValueKind::Array => {
                    let mut values = Vec::from_raw_parts(
                        self.data.ptr as *mut Value,
                        self.length as usize,
                        self.length as usize,
                    );
                    for value in values.iter_mut() {
                        value.free_data()
                    }
                    drop(values);
                }
                ValueKind::SimpleString => drop(CString::from_raw(self.data.ptr as *mut c_char)),
                ValueKind::Okay => { /* empty */ }
                ValueKind::Map => {
                    let mut values = Vec::from_raw_parts(
                        self.data.ptr as *mut Value,
                        self.length as usize * 2,
                        self.length as usize * 2,
                    );
                    for value in values.iter_mut() {
                        value.free_data()
                    }
                    drop(values);
                }
                ValueKind::Attribute => {
                    todo!("Implement")
                }
                ValueKind::Set => {
                    let mut values = Vec::from_raw_parts(
                        self.data.ptr as *mut Value,
                        self.length as usize,
                        self.length as usize,
                    );
                    for value in values.iter_mut() {
                        value.free_data()
                    }
                    drop(values);
                }
                ValueKind::Double => { /* empty */ }
                ValueKind::Boolean => { /* empty */ }
                ValueKind::VerbatimString => {
                    let vec = Vec::from_raw_parts(
                        self.data.ptr as *mut u8,
                        self.length as usize,
                        self.length as usize,
                    );
                    drop(vec);
                }
                ValueKind::BigNumber => {
                    todo!("Implement")
                }
                ValueKind::Push => {
                    todo!("Implement")
                }
            }
        }
    }

    /// # Summary
    /// Method to invoke a command.
    ///
    /// # Params
    /// ***in_client_ptr*** An active client handle
    /// ***in_callback*** A callback method with the signature:
    ///                   `void Callback(void * in_data, int out_success, const Value value)`.
    ///                   The first arg contains the data of the parameter *in_callback_data*;
    ///                   the second arg indicates whether the third parameter contains the error or result;
    ///                   the third arg contains either the result and MUST be freed by the callback.
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
    pub extern "C-unwind" fn csharp_command(
        in_client_ptr: *const c_void,
        in_callback: CommandCallback,
        in_callback_data: *mut c_void,
        in_request_type: RequestType,
        in_args: *const *const c_char,
        in_args_count: c_int,
    ) -> CommandResult {
        logger_core::log_debug("csharp_ffi", "Entered csharp_command");
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
            logger_core::log_debug("csharp_ffi", "Entered command task");
            let data = match handle.command(cmd, args).await {
                Ok(d) => d,
                Err(e) => {
                    let message = match CString::from_str(e.to_string().as_str()) {
                        Ok(d) => d.into_raw() as *const c_char,
                        Err(_) => null(),
                    };
                    logger_core::log_debug("csharp_ffi", "Error in command task");
                    match catch_unwind(|| unsafe {
                        callback(
                            callback_data as *mut c_void,
                            false as c_int,
                            Value {
                                data: ValueUnion {
                                    ptr: message as *mut c_void,
                                },
                                kind: ValueKind::SimpleString,
                                length: 0,
                            },
                        )
                    }) {
                        Err(e) => logger_core::log_error(
                            "csharp_ffi",
                            format!("Exception in C# callback: {:?}", e),
                        ),
                        _ => {}
                    };
                    return;
                }
            };
            unsafe {
                match Value::from(&data) {
                    Ok(data) => {
                        match catch_unwind(|| {
                            callback(callback_data as *mut c_void, true as c_int, data)
                        }) {
                            Err(e) => logger_core::log_error(
                                "csharp_ffi",
                                format!("Exception in C# callback: {:?}", e),
                            ),
                            _ => {}
                        }
                    }
                    Err(e) => {
                        let message = match CString::from_str(e.to_string().as_str()) {
                            Ok(d) => d.into_raw() as *const c_char,
                            Err(_) => null(),
                        };
                        logger_core::log_debug("csharp_ffi", "Error in command task");
                        match catch_unwind(|| {
                            callback(
                                callback_data as *mut c_void,
                                false as c_int,
                                Value {
                                    data: ValueUnion {
                                        ptr: message as *mut c_void,
                                    },
                                    kind: ValueKind::SimpleString,
                                    length: 0,
                                },
                            )
                        }) {
                            Err(e) => logger_core::log_error(
                                "csharp_ffi",
                                format!("Exception in C# callback: {:?}", e),
                            ),
                            _ => {}
                        };
                    }
                }
            }

            logger_core::log_debug("csharp_ffi", "Exiting tokio spawn from csharp_command");
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
    use crate::exports::calls::{ELoggerLevel, NodeAddress, Value};
    use glide_core::request_type::RequestType;
    use std::ffi::{c_int, c_void, CString};
    use std::ptr::null;
    use std::rc::Rc;
    use std::str::FromStr;
    use std::thread;
    use std::time::Duration;

    const HOST: &str = "localhost";
    const PORT: u16 = 49493;

    #[test]
    fn test_system_init() {
        assert_ne!(
            0,
            calls::csharp_system_init(ELoggerLevel::None, null()).success
        );
    }
    #[test]
    fn test_create_client_handle() {
        assert_ne!(
            0,
            calls::csharp_system_init(ELoggerLevel::None, null()).success
        );
        let address = CString::from_str(HOST).unwrap();
        let addresses = vec![NodeAddress {
            host: address.as_ptr(),
            port: PORT,
        }];
        let client_result = calls::csharp_create_client_handle(addresses.as_ptr(), 1, 0);
        assert_eq!(
            calls::ECreateClientHandleCode::Success,
            client_result.result
        );
        assert_ne!(null(), client_result.client_handle);
        calls::csharp_free_client_handle(client_result.client_handle);
    }
    #[test]
    fn test_call_command() {
        assert_ne!(
            0,
            calls::csharp_system_init(ELoggerLevel::None, null()).success
        );
        let address = CString::from_str(HOST).unwrap();
        let addresses = vec![NodeAddress {
            host: address.as_ptr(),
            port: PORT,
        }];
        let client_result = calls::csharp_create_client_handle(addresses.as_ptr(), 1, 0);
        assert_eq!(
            calls::ECreateClientHandleCode::Success,
            client_result.result
        );
        assert_ne!(null(), client_result.client_handle);

        let str = CString::from_str("test").unwrap();
        let ptr = str.as_ptr();
        let d = &[ptr];
        let flag = Rc::new(false);
        unsafe extern "C-unwind" fn test(
            in_data: *mut c_void,
            _out_success: c_int,
            _ref_output: Value,
        ) {
            let mut flag = Rc::from_raw(in_data as *mut bool);
            *flag = true;
        }
        let command_result = calls::csharp_command(
            client_result.client_handle,
            test as calls::CommandCallback,
            Rc::into_raw(flag.clone()) as *mut c_void,
            RequestType::Get,
            d.as_ptr(),
            1,
        );

        for _ in 0..200
        /* 20s */
        {
            if *flag {
                break;
            }
            thread::sleep(Duration::from_millis(100));
        }
        assert!(*flag);

        calls::csharp_free_client_handle(client_result.client_handle);
    }
}
