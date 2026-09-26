// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests the client flow for setting up a transfer: register, stage, lend, build a
//! command, and get the buffer back. Does not require a server. Does require the
//! `libfabric` feature and libfabric installed.

#![cfg(feature = "libfabric")]

use glide_rdma::{FabricConfig, Provider, RdmaFabric, TransferReply, encode_hex};

/// The whole client-side sequence short of the transfer itself.
#[test]
fn a_registered_buffer_produces_a_sendable_set_command() {
    let fabric = RdmaFabric::open(&FabricConfig::new(Provider::Tcp))
        .expect("the tcp provider should open on any host with libfabric");

    let mut buffer = fabric
        .register(vec![0u8; 64 * 1024])
        .expect("registration failed");
    assert!(buffer.is_registered_on(&fabric));

    let payload = b"the quick brown fox";
    assert_eq!(buffer.copy_from(payload), Some(payload.len()));

    let (command, loan) = buffer
        .lend_for_set(b"chunk:abc", 0, payload.len())
        .expect("the window fits");

    assert_eq!(command.name(), "LO.SET");
    let arguments = command.arguments();
    assert_eq!(arguments.len(), 4, "key, length, rkey, remote address");
    assert_eq!(arguments[0], b"chunk:abc");
    assert_eq!(arguments[1], payload.len().to_string().into_bytes());
    for number in &arguments[2..] {
        let text = std::str::from_utf8(number).expect("decimal ASCII");
        text.parse::<u64>().expect("an unsigned integer");
    }

    let hello = glide_rdma::hello(fabric.local_address());
    assert_eq!(hello.name(), "LO.HELLO");
    assert_eq!(
        hello.arguments(),
        [encode_hex(fabric.local_address()).into_bytes()]
    );

    // The client address is handshake-only: a transfer never repeats it.
    assert!(!command.arguments().contains(&hello.arguments()[0]));

    // The server replied, so the buffer comes back unchanged and can be lent again.
    let (buffer, receipt) = loan.reclaim(TransferReply::Stored).expect("reclaims");
    assert_eq!(receipt, None);
    assert_eq!(&buffer.as_host()[..payload.len()], payload);
}
