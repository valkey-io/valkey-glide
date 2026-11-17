// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

#[path = "../../../src/timeout_watchdog/pending_snapshot.rs"]
mod pending_snapshot;

fn main() {
    let (reader, mut writer) = pending_snapshot::new();
    let cloned_reader = reader.clone();
    std::thread::spawn(move || writer.publish(2)).join().unwrap();
    assert_eq!(reader.load(), 2);
    assert_eq!(cloned_reader.load(), 2);
}
