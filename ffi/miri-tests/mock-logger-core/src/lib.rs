// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Mock logger_core implementation for miri tests
// These functions are no-ops to avoid any complex logging infrastructure

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
