// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

/// Mock TimeoutWatchdog for miri tests.
pub struct TimeoutWatchdog;

impl TimeoutWatchdog {
    pub fn reinit_global() {
        // No-op in miri mock.
    }
}
