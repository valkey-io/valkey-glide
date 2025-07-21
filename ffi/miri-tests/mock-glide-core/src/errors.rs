// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::RedisError;

#[repr(C)]
pub enum RequestErrorType {
    Unspecified = 0,
    ExecAbort = 1,
    Timeout = 2,
    Disconnect = 3,
}

pub fn error_type(_error: &RedisError) -> RequestErrorType {
    RequestErrorType::Unspecified
}

pub fn error_message(_error: &RedisError) -> String {
    "".to_string()
}
