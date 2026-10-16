// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Tests the client flow for setting up a transfer: register, stage, lend, build a
//! command, and get the buffer back. Does not require a server. Does require the
//! `libfabric` feature and libfabric installed.

#![cfg(feature = "libfabric")]

use glide_rdma::{FabricConfig, Provider, RdmaFabric, SetChecksum, encode_hex};

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
        .lend_for_set(b"chunk:abc", 0, payload.len(), SetChecksum::Compute)
        .expect("the window fits");

    assert_eq!(command.name(), "BLOB.RDMA_SET");
    let arguments = command.arguments();
    assert_eq!(
        arguments.len(),
        7,
        "key, length, CRC, checksum, rkey, remote address, window length"
    );
    assert_eq!(arguments[0], b"chunk:abc");
    assert_eq!(arguments[1], payload.len().to_string().into_bytes());
    assert_eq!(arguments[2], b"CRC");
    assert_eq!(
        arguments[3],
        glide_rdma::checksum(payload).to_string().into_bytes()
    );
    for number in &arguments[4..] {
        let text = std::str::from_utf8(number).expect("decimal ASCII");
        text.parse::<u64>().expect("an unsigned integer");
    }
    assert_eq!(arguments[6], payload.len().to_string().into_bytes());

    let hello = glide_rdma::hello(fabric.local_address());
    assert_eq!(hello.name(), "BLOB.RDMA_HELLO");
    assert_eq!(
        hello.arguments(),
        [encode_hex(fabric.local_address()).into_bytes()]
    );

    // The client address is handshake-only: a transfer never repeats it.
    assert!(!command.arguments().contains(&hello.arguments()[0]));

    // SAFETY: the command was never sent.
    let buffer = unsafe { loan.cancel_unsent() };
    assert_eq!(&buffer.as_host()[..payload.len()], payload);
}

/// Memory that records when it is dropped, standing in for memory the caller
/// owns and may only reuse once the fabric lets go of it.
struct Tracked {
    bytes: Vec<u8>,
    dropped: std::sync::Arc<std::sync::atomic::AtomicBool>,
}

impl AsMut<[u8]> for Tracked {
    fn as_mut(&mut self) -> &mut [u8] {
        &mut self.bytes
    }
}

impl Drop for Tracked {
    fn drop(&mut self) {
        self.dropped
            .store(true, std::sync::atomic::Ordering::SeqCst);
    }
}

/// Dropping a buffer closes its region later on the fabric's closing thread.
/// Waiting for closes is how a caller knows the memory has been let go of.
#[test]
fn waiting_for_closes_returns_once_a_dropped_buffer_has_let_go_of_its_memory() {
    let fabric = RdmaFabric::open(&FabricConfig::new(Provider::Tcp))
        .expect("the tcp provider should open on any host with libfabric");
    let dropped = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
    let buffer = fabric
        .register(Tracked {
            bytes: vec![0u8; 4096],
            dropped: dropped.clone(),
        })
        .expect("registration failed");

    drop(buffer);
    fabric.wait_for_closes();

    assert!(
        dropped.load(std::sync::atomic::Ordering::SeqCst),
        "an idle buffer's memory is dropped by the time the wait returns"
    );
    assert_eq!(fabric.parked_bytes(), 0);
    assert_eq!(fabric.leaked_bytes(), 0);
}

/// A caller holding only the buffer, such as a binding handed nothing else, can
/// still wait for its region to close through the fabric the buffer names.
#[test]
fn a_buffer_names_its_fabric_so_a_caller_holding_only_the_buffer_can_wait() {
    let dropped = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
    let buffer = {
        let fabric = RdmaFabric::open(&FabricConfig::new(Provider::Tcp))
            .expect("the tcp provider should open on any host with libfabric");
        let buffer = fabric
            .register(Tracked {
                bytes: vec![0u8; 4096],
                dropped: dropped.clone(),
            })
            .expect("registration failed");
        assert!(buffer.is_registered_on(&fabric));
        assert!(buffer.is_registered_on(buffer.fabric()));
        buffer
    };

    let fabric = buffer.fabric().clone();
    drop(buffer);
    fabric.wait_for_closes();

    assert!(
        dropped.load(std::sync::atomic::Ordering::SeqCst),
        "an idle buffer's memory is dropped by the time the wait returns"
    );
}
