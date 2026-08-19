// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Compression suite (stub).
//!
//! The Rust wrapper does not yet expose a compression configuration knob
//! (glide-core supports request/response compression, but
//! `GlideClientConfiguration` has no field to enable it). These stubs document
//! the intended coverage and SKIP gracefully so the gap is tracked without a
//! spurious failure. When compression is wired into the config, replace these
//! with round-trip tests that:
//!   * set a large value with compression enabled and read it back intact;
//!   * confirm interop between a compressed writer and a plain reader;
//!   * verify the compression threshold is honoured.

mod common;

#[tokio::test]
async fn compression_config_not_yet_exposed() {
    eprintln!("SKIP: compression config not exposed by GlideClientConfiguration yet");
}

#[tokio::test]
async fn compressed_roundtrip_placeholder() {
    eprintln!("SKIP: compressed value round-trip pending compression config support");
}

#[tokio::test]
async fn compression_interop_placeholder() {
    eprintln!("SKIP: compressed/plain interop pending compression config support");
}
