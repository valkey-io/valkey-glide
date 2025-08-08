// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::Cmd;

#[derive(Debug, Clone, Copy)]
#[repr(C)]
pub enum RequestType {
    /// Invalid request type
    InvalidRequest = 0,

    // Basic string commands for testing
    Get = 1504,
    Set = 1517,
    Del = 402,
    Exists = 404,

    // Hash commands for testing
    HGet = 603,
    HSet = 613,
    HDel = 601,

    // List commands for testing
    LPush = 801,
    RPush = 820,
    LPop = 809,
    RPop = 819,

    // Other common commands
    Expire = 405,
    TTL = 428,
}

impl RequestType {
    pub fn get_command(&self) -> Option<Cmd> {
        match self {
            RequestType::InvalidRequest => None,
            _ => Some(Cmd::default()),
        }
    }
}
