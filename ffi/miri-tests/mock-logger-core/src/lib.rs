// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Mock logger_core implementation for miri tests
// These functions are no-ops to avoid any complex logging infrastructure

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Level {
    Error = 0,
    Warn = 1,
    Info = 2,
    Debug = 3,
    Trace = 4,
    Off = 5,
}

pub fn log_error(_module: &str, _message: impl std::fmt::Display) {
    // No-op for miri tests
}

pub fn log_warn(_module: &str, _message: impl std::fmt::Display) {
    // No-op for miri tests
}

pub fn log_debug(_module: &str, _message: impl std::fmt::Display) {
    // No-op for miri tests
}

pub fn log_info(_module: &str, _message: impl std::fmt::Display) {
    // No-op for miri tests
}

pub fn init(_minimal_level: Option<Level>, _file_name: Option<&str>) -> Level {
    Level::Warn
}

pub fn log<Message: AsRef<str>, Identifier: AsRef<str>>(
    _log_level: Level,
    _log_identifier: Identifier,
    _message: Message,
) {
    // No-op for Miri tests
}
