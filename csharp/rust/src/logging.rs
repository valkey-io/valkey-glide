use std::ffi::c_int;

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