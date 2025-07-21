// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use redis::{ScanStateRC, RedisResult};

pub fn get_cluster_scan_cursor(_id: String) -> RedisResult<ScanStateRC> {
    Ok(ScanStateRC)
}

pub fn remove_scan_state_cursor(_id: String) {
}
