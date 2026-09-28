// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Runs comparisons between the Valkey GLIDE Rust client and redis-rs
//! to ensure parity between their commands public surface.

mod parity;

/// The redis-rs release GLIDE targets for parity.
// TODO #7058: bump to "1.7.0" and retarget the guard to *upstream* redis-rs
// (fetch `redis/src/commands/mod.rs` at the `redis-1.7.0` tag from GitHub).
const REDIS_RS_VERSION: &str = "0.25.2";

/// Compares the methods defined by the Valkey GLIDE and redis-rs command tables
/// (see by the `implement_commands` macro) and fails if they do not match.
#[test]
fn redis_parity_check() {
    match parity::run_parity_check(REDIS_RS_VERSION) {
        Ok(summary) => println!("{summary}"),
        Err(problems) => panic!(
            "command table diverges from redis-rs — PARITY VIOLATIONS ({}):\n - {}",
            problems.len(),
            problems.join("\n - ")
        ),
    }
}
