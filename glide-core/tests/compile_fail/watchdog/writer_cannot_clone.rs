// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#[path = "../../../src/timeout_watchdog/pending_snapshot.rs"]
mod pending_snapshot;

fn main() {
    let (_, writer) = pending_snapshot::new();
    let _ = writer.clone();
}
