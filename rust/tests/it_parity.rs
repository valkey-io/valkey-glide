// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Runs comparisons between the Valkey GLIDE Rust client and redis-rs
//! to ensure parity between their commands public surface.

mod parity;

/// Compares the methods defined by the Valkey GLIDE and redis-rs command tables
/// (see by the `implement_commands` macro) and fails if they do not match.
#[test]
fn redis_parity_check() {
    match parity::run_parity_check() {
        Ok(summary) => println!("{summary}"),
        Err(problems) => panic!(
            "command table diverges from redis-rs — PARITY VIOLATIONS ({}):\n - {}",
            problems.len(),
            problems.join("\n - ")
        ),
    }
}
