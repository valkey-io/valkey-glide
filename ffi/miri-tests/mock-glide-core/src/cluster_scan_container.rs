// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#![allow(unused)]

use redis::{ScanStateRC, RedisResult};

pub fn get_cluster_scan_cursor(id: String) -> RedisResult<ScanStateRC> {
    Ok(ScanStateRC)
}

pub fn remove_scan_state_cursor(id: String) {
}
