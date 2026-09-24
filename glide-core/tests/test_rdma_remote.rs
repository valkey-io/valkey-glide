// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! End-to-end RDMA against a server that serves the `LO.*` commands.
//!
//! Ignored by default because this repository cannot start such a server.
//! EFA hardware is required only for `efa-direct`.
//!
//! | To run | You need |
//! |---|---|
//! | over `tcp`, one machine | libfabric and a module built for that machine |
//! | over `efa-direct` | the above, plus two EFA instances in one AZ, same VPC |
//!
//! Loopback, the more accessible setup:
//!
//! ```sh
//! valkey-server --port 6379 --loadmodule ./<rdma-module>.so <module arguments> &
//! export GLIDE_RDMA_SERVER=127.0.0.1:6379
//! export GLIDE_RDMA_PROVIDER=tcp
//! cargo test --features rdma --test test_rdma_remote -- --ignored --nocapture
//! ```
//!
//! Against EFA hardware, where the transfer is a real NIC operation:
//!
//! ```sh
//! export GLIDE_RDMA_SERVER=<server private IP>:6379
//! export GLIDE_RDMA_PROVIDER=efa-direct
//! cargo test --features rdma --test test_rdma_remote -- --ignored --nocapture
//! ```

#![cfg(feature = "rdma")]

#[cfg(test)]
mod remote_dma_tests {
    use glide_core::client::{Client, ConnectionRequest, NodeAddress, RdmaOutcome};
    use glide_core::rdma::{FabricConfig, Provider, RdmaSetting};
    use glide_rdma::RdmaBuffer;

    const VALUE_BYTES: usize = 1 << 20;

    fn server() -> (String, u16) {
        let address = std::env::var("GLIDE_RDMA_SERVER")
            .expect("set GLIDE_RDMA_SERVER=<host>:<port> to run the remote RDMA tests");
        let (host, port) = address
            .rsplit_once(':')
            .expect("GLIDE_RDMA_SERVER must be host:port");
        (
            host.to_string(),
            port.parse().expect("port must be a number"),
        )
    }

    fn provider() -> Provider {
        match std::env::var("GLIDE_RDMA_PROVIDER")
            .expect("set GLIDE_RDMA_PROVIDER=tcp or efa-direct")
            .as_str()
        {
            "tcp" => Provider::Tcp,
            "efa-direct" => Provider::EfaDirect,
            other => panic!("GLIDE_RDMA_PROVIDER must be tcp or efa-direct, got {other:?}"),
        }
    }

    async fn dma_client() -> Client {
        let (host, port) = server();
        let mut fabric = FabricConfig::new(provider());
        if let Ok(interface) = std::env::var("GLIDE_RDMA_INTERFACE") {
            fabric = fabric.with_interface(interface);
        }
        let request = ConnectionRequest {
            addresses: vec![NodeAddress { host, port }],
            rdma: RdmaSetting::Configured(fabric),
            ..Default::default()
        };
        Client::new(request, None)
            .await
            .expect("RDMA client construction opens the fabric and connects")
    }

    /// Unwrap a transfer that must succeed, and the buffer it hands back.
    fn succeeded<T>(
        (buffer, result): RdmaOutcome<T>,
        what: impl std::fmt::Display,
    ) -> (RdmaBuffer, T) {
        let value = result.unwrap_or_else(|error| panic!("{what}: {error}"));
        let buffer = buffer.expect("a finished transfer hands the buffer back");
        assert!(
            !buffer.is_revoked(),
            "{what}: a finished transfer keeps its region"
        );
        (buffer, value)
    }

    /// The round trip: stage a value, let the server read it out of registered
    /// memory, then let it write the value back into a second buffer, and
    /// compare the bytes. The comparison, and the checksum when the server sends
    /// one with the `LO.GET` reply, make a misaddressed transfer fail here rather
    /// than return plausible-looking bytes.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn a_value_round_trips_through_the_fabric() {
        let mut client = dma_client().await;
        let key = format!("glide-rdma-roundtrip-{}", std::process::id());

        let value: Vec<u8> = (0..VALUE_BYTES).map(|index| index as u8).collect();

        let mut source = client
            .register_rdma_region(vec![0u8; VALUE_BYTES])
            .expect("registering the source region");
        source
            .copy_from(&value)
            .expect("staging the value into registered memory");

        succeeded(
            client
                .rdma_set(key.as_bytes(), source, 0, VALUE_BYTES)
                .await,
            "LO.SET",
        );

        // A separate region, scrubbed, so a "successful" read of untouched
        // memory cannot be mistaken for a transfer.
        let destination = client
            .register_rdma_region(vec![0xAAu8; VALUE_BYTES])
            .expect("registering the destination region");

        let (destination, receipt) = succeeded(
            client
                .rdma_get(key.as_bytes(), destination, 0, VALUE_BYTES)
                .await,
            "LO.GET",
        );
        let receipt = receipt.expect("the key was just written, so it must exist");
        assert_eq!(receipt.bytes_written, VALUE_BYTES);
        if let Some(checksum) = receipt.checksum {
            assert_eq!(checksum, glide_rdma::checksum(&value));
        }

        assert_eq!(
            &destination.as_host()[..VALUE_BYTES],
            &value[..],
            "the bytes the server transferred must match what was stored"
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn windows_of_one_region_round_trip_independently() {
        const SLOT_BYTES: usize = 4096;
        const SLOTS: usize = 4;

        let mut client = dma_client().await;
        let prefix = format!("glide-rdma-window-{}", std::process::id());

        // One registration, filled so each slot holds only its own byte value.
        let mut source = client
            .register_rdma_region(vec![0u8; SLOT_BYTES * SLOTS])
            .expect("registering the source region");
        for slot in 0..SLOTS {
            let offset = slot * SLOT_BYTES;
            source.as_host_mut()[offset..offset + SLOT_BYTES].fill(slot as u8 + 1);
        }

        for slot in 0..SLOTS {
            let offset = slot * SLOT_BYTES;
            source = succeeded(
                client
                    .rdma_set(
                        format!("{prefix}-{slot}").as_bytes(),
                        source,
                        offset,
                        SLOT_BYTES,
                    )
                    .await,
                format!("LO.SET of slot {slot}"),
            )
            .0;
        }

        // Read back into a second region, each value into a different slot than
        // it was written from, so a transfer that ignored either offset lands in
        // the wrong place and fails the comparison.
        let mut destination = client
            .register_rdma_region(vec![0xAAu8; SLOT_BYTES * SLOTS])
            .expect("registering the destination region");
        for slot in 0..SLOTS {
            let destination_slot = SLOTS - 1 - slot;
            let (buffer, receipt) = succeeded(
                client
                    .rdma_get(
                        format!("{prefix}-{slot}").as_bytes(),
                        destination,
                        destination_slot * SLOT_BYTES,
                        SLOT_BYTES,
                    )
                    .await,
                format!("LO.GET of slot {slot}"),
            );
            destination = buffer;
            let receipt = receipt.expect("the key was just written, so it must exist");
            assert_eq!(receipt.bytes_written, SLOT_BYTES);
        }

        let landed = destination.as_host();
        for slot in 0..SLOTS {
            let destination_slot = SLOTS - 1 - slot;
            let offset = destination_slot * SLOT_BYTES;
            assert_eq!(
                &landed[offset..offset + SLOT_BYTES],
                &vec![slot as u8 + 1; SLOT_BYTES][..],
                "slot {slot} must land at offset {offset} and nowhere else"
            );
        }
    }

    /// The point of lending rather than registering per transfer: one buffer is
    /// lent, handed back, rewritten and lent again, many times, and every
    /// transfer sees exactly the bytes of its own turn.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn one_buffer_serves_many_transfers_in_turn() {
        const BYTES: usize = 64 * 1024;
        const ROUNDS: u8 = 50;

        let mut client = dma_client().await;
        let key = format!("glide-rdma-reuse-{}", std::process::id());
        let mut source = client
            .register_rdma_region(vec![0u8; BYTES])
            .expect("registering the source region");
        let mut destination = client
            .register_rdma_region(vec![0u8; BYTES])
            .expect("registering the destination region");

        for round in 1..=ROUNDS {
            source.as_host_mut().fill(round);
            source = succeeded(
                client.rdma_set(key.as_bytes(), source, 0, BYTES).await,
                format!("LO.SET in round {round}"),
            )
            .0;

            destination.as_host_mut().fill(0);
            let (buffer, receipt) = succeeded(
                client.rdma_get(key.as_bytes(), destination, 0, BYTES).await,
                format!("LO.GET in round {round}"),
            );
            destination = buffer;
            assert_eq!(receipt.map(|receipt| receipt.bytes_written), Some(BYTES));
            assert!(
                destination.as_host().iter().all(|byte| *byte == round),
                "round {round} must read back exactly what it wrote"
            );
        }
    }

    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn a_missing_key_transfers_nothing() {
        let mut client = dma_client().await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering");

        let absent = format!("glide-rdma-absent-{}", std::process::id());
        let (_, receipt) = succeeded(
            client.rdma_get(absent.as_bytes(), buffer, 0, 4096).await,
            "LO.GET against a missing key is not an error",
        );
        assert!(receipt.is_none(), "a missing key must transfer nothing");
    }

    /// `LO.GET` does not yet say how big its window is, so a value larger than the
    /// window is the server's to handle. Whatever it does, the client must end up
    /// in one of two safe states: the buffer back and usable after a reply, or the
    /// buffer revoked. This test records which one this server produces.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn a_value_larger_than_the_window_leaves_the_buffer_safe() {
        const VALUE: usize = 8192;
        const WINDOW: usize = 4096;

        let mut client = dma_client().await;
        let key = format!("glide-rdma-too-large-{}", std::process::id());
        let mut source = client
            .register_rdma_region(vec![7u8; VALUE])
            .expect("registering the source region");
        source.as_host_mut().fill(7);
        succeeded(
            client.rdma_set(key.as_bytes(), source, 0, VALUE).await,
            "LO.SET",
        );

        for (label, registered) in [
            ("inside a larger region", VALUE),
            ("filling its region", WINDOW),
        ] {
            let destination = client
                .register_rdma_region(vec![0u8; registered])
                .expect("registering the destination region");
            let (buffer, result) = client
                .rdma_get(key.as_bytes(), destination, 0, WINDOW)
                .await;
            let buffer = buffer.expect("the buffer comes back either way");
            println!(
                "window {label}: result {result:?}, buffer revoked: {}",
                buffer.is_revoked()
            );
            let error = result.expect_err("the value does not fit the window");
            if !buffer.is_revoked() {
                // Handed back after a reply, so it must work for the next transfer.
                let absent = format!("{key}-absent");
                succeeded(
                    client.rdma_get(absent.as_bytes(), buffer, 0, WINDOW).await,
                    format!("reusing the buffer after {error}"),
                );
            }
        }
    }

    /// A client without RDMA, for arranging the server around a transfer.
    async fn plain_client() -> Client {
        let (host, port) = server();
        Client::new(
            ConnectionRequest {
                addresses: vec![NodeAddress { host, port }],
                ..Default::default()
            },
            None,
        )
        .await
        .expect("plain client")
    }

    async fn run(client: &mut Client, args: &[&str]) {
        let mut command = redis::cmd(args[0]);
        for arg in &args[1..] {
            command.arg(*arg);
        }
        client
            .send_command(&mut command, None)
            .await
            .unwrap_or_else(|err| panic!("{args:?}: {err}"));
    }

    fn is_cancellation(error: &redis::RedisError) -> bool {
        error.to_string().contains("RDMA transfer cancelled")
    }

    /// The hung-server case. `CLIENT PAUSE` holds every command without answering
    /// it, so the transfer can only end with a cancellation because the client was
    /// closed: had the pause ended it, the server's reply would have come back
    /// instead. The pause is left to lapse rather than lifted, because the server
    /// holds `CLIENT UNPAUSE` too.
    #[tokio::test(flavor = "multi_thread")]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn closing_the_client_cancels_a_transfer_the_server_never_answers() {
        let key = format!("glide-rdma-paused-{}", std::process::id());
        let mut pauser = plain_client().await;
        let client = dma_client().await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering");
        // Open the session first, so the transfer below is held at LO.GET itself.
        let mut warming = client.clone();
        let (buffer, _) = succeeded(
            warming.rdma_get(key.as_bytes(), buffer, 0, 4096).await,
            "LO.GET",
        );

        let pause = std::time::Duration::from_secs(5);
        let paused_at = std::time::Instant::now();
        run(&mut pauser, &["CLIENT", "PAUSE", "5000", "ALL"]).await;
        let mut transferring = client.clone();
        let transfer =
            tokio::spawn(
                async move { transferring.rdma_get(key.as_bytes(), buffer, 0, 4096).await },
            );
        // Give the transfer time to reach the server, so it is the held command
        // that is cancelled. A close that came first would still cancel it.
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;

        client.close_rdma().expect("every region closes");
        let finished = tokio::time::timeout(std::time::Duration::from_secs(30), transfer).await;
        // Let the pause lapse, so the tests after this one can connect.
        tokio::time::sleep(
            pause.saturating_sub(paused_at.elapsed()) + std::time::Duration::from_millis(200),
        )
        .await;

        let (buffer, outcome) = finished.expect("closing the client ends the wait").unwrap();
        let error = outcome.expect_err("a cancelled transfer is an error");
        assert!(is_cancellation(&error), "{error}");
        assert!(buffer.expect("the buffer comes back").is_revoked());
    }

    /// The hole lending closes: a caller that stops waiting, here with a timeout,
    /// while the server holds the command. The server runs the held `LO.GET` once
    /// the pause lapses, after the caller has moved on. Dropping the transfer
    /// must already have closed the region, so the server's write fails at the
    /// fabric instead of landing in memory the client has reused or freed. The
    /// connection must stay usable afterwards.
    #[tokio::test(flavor = "multi_thread")]
    #[serial_test::serial]
    #[ignore = "needs a server serving the LO.* commands"]
    async fn abandoning_a_transfer_closes_its_region_before_the_server_writes() {
        let key = format!("glide-rdma-abandoned-{}", std::process::id());
        let mut pauser = plain_client().await;
        let mut writer = dma_client().await;
        let mut source = writer
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering the source region");
        source.as_host_mut().fill(0x5A);
        succeeded(
            writer.rdma_set(key.as_bytes(), source, 0, 4096).await,
            "LO.SET",
        );

        let mut client = dma_client().await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering");
        // Open the session first, so the transfer below is held at LO.GET itself.
        let (buffer, _) = succeeded(
            client.rdma_get(key.as_bytes(), buffer, 0, 4096).await,
            "LO.GET",
        );
        let revoker = buffer.revoker();

        let pause = std::time::Duration::from_secs(3);
        let paused_at = std::time::Instant::now();
        run(&mut pauser, &["CLIENT", "PAUSE", "3000", "ALL"]).await;
        let abandoned = tokio::time::timeout(
            std::time::Duration::from_millis(500),
            client.rdma_get(key.as_bytes(), buffer, 0, 4096),
        )
        .await;
        assert!(
            abandoned.is_err(),
            "the server is paused, so the wait times out"
        );
        assert!(
            revoker.is_released(),
            "dropping the transfer closed the region before the server could write"
        );

        // Let the pause lapse and the server run the held LO.GET against the
        // closed region.
        tokio::time::sleep(
            pause.saturating_sub(paused_at.elapsed()) + std::time::Duration::from_millis(500),
        )
        .await;

        let fresh = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering again");
        let (fresh, receipt) = succeeded(
            client.rdma_get(key.as_bytes(), fresh, 0, 4096).await,
            "a new transfer on the same client after the abandoned one",
        );
        assert_eq!(receipt.map(|receipt| receipt.bytes_written), Some(4096));
        assert!(fresh.as_host().iter().all(|byte| *byte == 0x5A));
    }
}
