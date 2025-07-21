// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::Cmd;

#[derive(Debug, Clone, Copy)]
#[repr(C)]
pub struct RequestType {
    _data: ()
}

impl RequestType {
    pub fn get_command(&self) -> Option<Cmd> {
        Some(Cmd)
    }
}
