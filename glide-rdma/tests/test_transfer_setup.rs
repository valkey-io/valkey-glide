// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests the client flow for setting up a transfer: register, stage, slice, build a command.
//! Does not require a server. Does require the `libfabric` feature and libfabric installed.

#![cfg(feature = "libfabric")]

use glide_rdma::{FabricConfig, Provider, RdmaFabric, encode_hex};

/// The whole client-side sequence short of the transfer itself.
#[test]
fn a_registered_buffer_produces_a_sendable_set_command() {
    let fabric = RdmaFabric::open(&FabricConfig::new(Provider::Tcp))
        .expect("the tcp provider should open on any host with libfabric");

    let mut buffer = fabric
        .register(vec![0u8; 64 * 1024])
        .expect("registration failed");

    let payload = b"the quick brown fox";
    assert_eq!(buffer.copy_from(payload), Some(payload.len()));

    let window = buffer.slice(0, payload.len()).expect("window should fit");
    let command = glide_rdma::set(b"chunk:abc", window.length(), window.region_ref());

    assert_eq!(command.name(), "LO.SET");
    assert_eq!(
        command.arguments(),
        [
            b"chunk:abc".to_vec(),
            payload.len().to_string().into_bytes(),
            window.region_ref().remote_key.to_string().into_bytes(),
            window.region_ref().remote_address.to_string().into_bytes(),
        ]
    );

    let hello = glide_rdma::hello(fabric.local_address());
    assert_eq!(hello.name(), "LO.HELLO");
    assert_eq!(
        hello.arguments(),
        [encode_hex(fabric.local_address()).into_bytes()]
    );

    // The client address is handshake-only: a transfer never repeats it.
    assert!(!command.arguments().contains(&hello.arguments()[0]));
}
