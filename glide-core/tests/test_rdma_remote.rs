// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! End-to-end RDMA against a server that serves the `BLOB.*` commands.
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
    use redis::Value;

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
    /// compare the bytes. The comparison, and the checksum the server sends with
    /// the `BLOB.RDMA_GET` reply, make a misaddressed transfer fail here rather
    /// than return plausible-looking bytes.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
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
                .rdma_set(
                    key.as_bytes(),
                    source,
                    0,
                    VALUE_BYTES,
                    glide_rdma::SetChecksum::Skip,
                )
                .await,
            "BLOB.RDMA_SET",
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
            "BLOB.RDMA_GET",
        );
        let receipt = receipt.expect("the key was just written, so it must exist");
        assert_eq!(receipt.bytes_written, VALUE_BYTES);
        assert_eq!(receipt.checksum, glide_rdma::checksum(&value));
        receipt
            .verify(destination.as_host())
            .expect("the bytes that landed match the server's checksum");

        assert_eq!(
            &destination.as_host()[..VALUE_BYTES],
            &value[..],
            "the bytes the server transferred must match what was stored"
        );
    }

    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
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
                        glide_rdma::SetChecksum::Skip,
                    )
                    .await,
                format!("BLOB.RDMA_SET of slot {slot}"),
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
                format!("BLOB.RDMA_GET of slot {slot}"),
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
    #[ignore = "needs a server serving the BLOB.* commands"]
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
                client
                    .rdma_set(
                        key.as_bytes(),
                        source,
                        0,
                        BYTES,
                        glide_rdma::SetChecksum::Skip,
                    )
                    .await,
                format!("BLOB.RDMA_SET in round {round}"),
            )
            .0;

            destination.as_host_mut().fill(0);
            let (buffer, receipt) = succeeded(
                client.rdma_get(key.as_bytes(), destination, 0, BYTES).await,
                format!("BLOB.RDMA_GET in round {round}"),
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
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn a_missing_key_transfers_nothing() {
        let mut client = dma_client().await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering");

        let absent = format!("glide-rdma-absent-{}", std::process::id());
        let (_, receipt) = succeeded(
            client.rdma_get(absent.as_bytes(), buffer, 0, 4096).await,
            "BLOB.RDMA_GET against a missing key is not an error",
        );
        assert!(receipt.is_none(), "a missing key must transfer nothing");
    }

    /// The server refuses a value larger than the window with an error reply,
    /// before it writes anything. A reply means the server is done with the
    /// memory, so the buffer comes back unrevoked and untouched, and it works for
    /// the next transfer. Checked with the window both inside a larger region and
    /// filling its region.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
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
            client
                .rdma_set(
                    key.as_bytes(),
                    source,
                    0,
                    VALUE,
                    glide_rdma::SetChecksum::Skip,
                )
                .await,
            "BLOB.RDMA_SET",
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
            let error = result.expect_err("the value does not fit the window");
            assert!(error.is_server_reply(), "{label}: {error}");
            let buffer =
                buffer.unwrap_or_else(|| panic!("{label}: an error reply hands the buffer back"));
            assert!(
                !buffer.is_revoked(),
                "{label}: an error reply keeps the region"
            );
            assert!(
                buffer.as_host().iter().all(|&byte| byte == 0),
                "{label}: the server refused before writing"
            );

            let absent = format!("{key}-absent");
            succeeded(
                client.rdma_get(absent.as_bytes(), buffer, 0, WINDOW).await,
                format!("{label}: reusing the buffer after {error}"),
            );
        }
        run(&mut client, &["DEL", &key]).await;
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
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn closing_the_client_cancels_a_transfer_the_server_never_answers() {
        let key = format!("glide-rdma-paused-{}", std::process::id());
        let mut pauser = plain_client().await;
        let client = dma_client().await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering");
        // Open the session first, so the transfer below is held at BLOB.RDMA_GET itself.
        let mut warming = client.clone();
        let (buffer, _) = succeeded(
            warming.rdma_get(key.as_bytes(), buffer, 0, 4096).await,
            "BLOB.RDMA_GET",
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
        assert!(
            buffer.is_none(),
            "a transfer that ends without a reply hands back no buffer"
        );
    }

    /// The hole lending closes: a caller that stops waiting, here with a timeout,
    /// while the server holds the command. The server runs the held `BLOB.RDMA_GET` once
    /// the pause lapses, after the caller has moved on. Dropping the transfer
    /// must already have closed the region, so the server's write fails at the
    /// fabric instead of landing in memory the client has reused or freed. The
    /// connection must stay usable afterwards.
    #[tokio::test(flavor = "multi_thread")]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn abandoning_a_transfer_closes_its_region_before_the_server_writes() {
        let key = format!("glide-rdma-abandoned-{}", std::process::id());
        let mut pauser = plain_client().await;
        let mut writer = dma_client().await;
        let mut source = writer
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering the source region");
        source.as_host_mut().fill(0x5A);
        succeeded(
            writer
                .rdma_set(
                    key.as_bytes(),
                    source,
                    0,
                    4096,
                    glide_rdma::SetChecksum::Skip,
                )
                .await,
            "BLOB.RDMA_SET",
        );

        let mut client = dma_client().await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering");
        // Open the session first, so the transfer below is held at BLOB.RDMA_GET itself.
        let (buffer, _) = succeeded(
            client.rdma_get(key.as_bytes(), buffer, 0, 4096).await,
            "BLOB.RDMA_GET",
        );
        let revoker = buffer.revoker();
        let closed = buffer.revoked();

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
        // Dropping the transfer queued the close on the fabric's closing thread;
        // it must finish long before the pause lapses and the server writes.
        tokio::time::timeout(std::time::Duration::from_secs(1), closed)
            .await
            .expect("dropping the transfer closed the region before the server could write");
        assert!(revoker.is_released());
        assert!(
            paused_at.elapsed() < pause,
            "closed while the server was still paused"
        );

        // Let the pause lapse and the server run the held BLOB.RDMA_GET against the
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

    /// `BLOB.TCP_GET` reply bytes, or `None` for a missing key.
    async fn tcp_get(client: &mut Client, key: &[u8]) -> Option<Vec<u8>> {
        let mut command = redis::cmd("BLOB.TCP_GET");
        command.arg(key);
        match client
            .send_command(&mut command, None)
            .await
            .expect("BLOB.TCP_GET")
        {
            Value::BulkString(bytes) => Some(bytes.to_vec()),
            Value::Nil => None,
            other => panic!("unexpected BLOB.TCP_GET reply {other:?}"),
        }
    }

    async fn tcp_set(client: &mut Client, key: &[u8], value: &[u8]) {
        let mut command = redis::cmd("BLOB.TCP_SET");
        command.arg(key).arg(value);
        let reply = client
            .send_command(&mut command, None)
            .await
            .expect("BLOB.TCP_SET");
        assert_eq!(reply, Value::Okay, "BLOB.TCP_SET");
    }

    /// The TCP commands carry the value over RESP, so a client with no RDMA
    /// configuration, and so no fabric, can use them as ordinary commands.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn tcp_commands_need_no_rdma() {
        let mut client = plain_client().await;
        let key = format!("glide-rdma-tcp-{}", std::process::id());
        let value: Vec<u8> = (0..VALUE_BYTES).map(|index| (index * 7) as u8).collect();

        assert_eq!(tcp_get(&mut client, key.as_bytes()).await, None);
        tcp_set(&mut client, key.as_bytes(), &value).await;
        assert_eq!(tcp_get(&mut client, key.as_bytes()).await, Some(value));
        run(&mut client, &["DEL", &key]).await;
    }

    /// Both transports store and read the same objects: a value set over one comes
    /// back over the other, on one RDMA client, and an RDMA client may send the
    /// TCP commands directly.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn a_value_crosses_between_the_tcp_and_rdma_commands() {
        let mut client = dma_client().await;
        let key = format!("glide-rdma-cross-{}", std::process::id());
        let value: Vec<u8> = (0..VALUE_BYTES).map(|index| (index * 13) as u8).collect();

        // TCP in, RDMA out. The checksum in the reply is checked by `rdma_get`.
        tcp_set(&mut client, key.as_bytes(), &value).await;
        let destination = client
            .register_rdma_region(vec![0xAAu8; VALUE_BYTES])
            .expect("registering the destination region");
        let (destination, receipt) = succeeded(
            client
                .rdma_get(key.as_bytes(), destination, 0, VALUE_BYTES)
                .await,
            "BLOB.RDMA_GET of a value set over TCP",
        );
        assert_eq!(
            receipt.map(|receipt| receipt.bytes_written),
            Some(VALUE_BYTES)
        );
        assert_eq!(&destination.as_host()[..VALUE_BYTES], &value[..]);

        // RDMA in, TCP out.
        let changed: Vec<u8> = value.iter().map(|byte| byte ^ 0xFF).collect();
        let mut source = client
            .register_rdma_region(vec![0u8; VALUE_BYTES])
            .expect("registering the source region");
        source.copy_from(&changed).expect("staging the value");
        succeeded(
            client
                .rdma_set(
                    key.as_bytes(),
                    source,
                    0,
                    VALUE_BYTES,
                    glide_rdma::SetChecksum::Skip,
                )
                .await,
            "BLOB.RDMA_SET",
        );
        assert_eq!(tcp_get(&mut client, key.as_bytes()).await, Some(changed));
        run(&mut client, &["DEL", &key]).await;
    }

    // ─── Checksums on BLOB.RDMA_SET ───────────────────────────────────────────

    use glide_rdma::SetChecksum;

    const CRC_BYTES: usize = 1 << 20;

    /// A value that does not repeat on any power-of-two period.
    fn crc_value(seed: u8) -> Vec<u8> {
        (0..CRC_BYTES)
            .map(|index| ((index % 251) as u8).wrapping_add(seed))
            .collect()
    }

    /// Stage `value` at `offset` of a fresh region and store it with `checksum`.
    async fn set_with(
        client: &mut Client,
        key: &str,
        value: &[u8],
        offset: usize,
        checksum: SetChecksum,
    ) -> (Option<RdmaBuffer>, redis::RedisResult<()>) {
        let mut memory = vec![0x5Au8; offset + value.len() + 4096];
        memory[offset..offset + value.len()].copy_from_slice(value);
        let source = client
            .register_rdma_region(memory)
            .expect("registering the source region");
        client
            .rdma_set(key.as_bytes(), source, offset, value.len(), checksum)
            .await
    }

    /// Read `key` back over RDMA, verified against the server's checksum.
    async fn read_back(client: &mut Client, key: &str) -> Option<Vec<u8>> {
        let destination = client
            .register_rdma_region(vec![0xAAu8; CRC_BYTES])
            .expect("registering the destination region");
        let (destination, receipt) = succeeded(
            client
                .rdma_get(key.as_bytes(), destination, 0, CRC_BYTES)
                .await,
            "BLOB.RDMA_GET",
        );
        let receipt = receipt?;
        receipt
            .verify(destination.as_host())
            .expect("the bytes that landed match the server's checksum");
        Some(destination.as_host()[..receipt.bytes_written].to_vec())
    }

    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn crc_every_choice_stores_the_value() {
        let mut client = dma_client().await;
        let value = crc_value(0);
        for (name, checksum) in [
            ("skip", SetChecksum::Skip),
            ("compute", SetChecksum::Compute),
            ("known", SetChecksum::Known(glide_rdma::checksum(&value))),
        ] {
            let key = format!("glide-rdma-crc-{name}-{}", std::process::id());
            succeeded(
                set_with(&mut client, &key, &value, 0, checksum).await,
                format!("BLOB.RDMA_SET with {name}"),
            );
            assert_eq!(
                read_back(&mut client, &key).await.as_deref(),
                Some(&value[..]),
                "{name}"
            );
            run(&mut client, &["DEL", &key]).await;
        }
    }

    /// The checksum covers only the lent window, not the bytes around it.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn crc_computed_over_an_offset_window_matches_the_server() {
        let mut client = dma_client().await;
        let key = format!("glide-rdma-crc-window-{}", std::process::id());
        let value = crc_value(7);
        succeeded(
            set_with(&mut client, &key, &value, 12_345, SetChecksum::Compute).await,
            "BLOB.RDMA_SET of an offset window with a computed CRC",
        );
        assert_eq!(
            read_back(&mut client, &key).await.as_deref(),
            Some(&value[..])
        );
        run(&mut client, &["DEL", &key]).await;
    }

    /// A wrong checksum is refused: the error reaches the caller, the buffer comes
    /// back usable, and no value is stored.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn crc_mismatch_is_refused_and_stores_nothing() {
        let mut client = dma_client().await;
        let key = format!("glide-rdma-crc-mismatch-{}", std::process::id());
        let value = crc_value(1);
        let wrong = glide_rdma::checksum(&value) ^ 1;

        let (buffer, result) =
            set_with(&mut client, &key, &value, 0, SetChecksum::Known(wrong)).await;
        let error = result.expect_err("a wrong CRC must be refused");
        assert!(
            error.to_string().contains("CRC mismatch after transfer"),
            "{error}"
        );
        let buffer = buffer.expect("an error reply hands the buffer back");
        assert!(!buffer.is_revoked(), "an error reply keeps the region");
        assert_eq!(read_back(&mut client, &key).await, None, "nothing stored");

        // The returned buffer still works: retry with the right CRC.
        let (_, result) = client
            .rdma_set(
                key.as_bytes(),
                buffer,
                0,
                value.len(),
                SetChecksum::Known(glide_rdma::checksum(&value)),
            )
            .await;
        result.expect("the retry with the right CRC succeeds");
        assert_eq!(
            read_back(&mut client, &key).await.as_deref(),
            Some(&value[..])
        );
        run(&mut client, &["DEL", &key]).await;
    }

    /// A refused overwrite leaves the old value in place.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn crc_mismatch_on_overwrite_keeps_the_old_value() {
        let mut client = dma_client().await;
        let key = format!("glide-rdma-crc-overwrite-{}", std::process::id());
        let old = crc_value(2);
        let new = crc_value(3);
        succeeded(
            set_with(&mut client, &key, &old, 0, SetChecksum::Compute).await,
            "storing the original value",
        );
        let (_, result) = set_with(
            &mut client,
            &key,
            &new,
            0,
            SetChecksum::Known(glide_rdma::checksum(&new) ^ 0x8000_0000),
        )
        .await;
        result.expect_err("a wrong CRC must be refused");
        assert_eq!(
            read_back(&mut client, &key).await.as_deref(),
            Some(&old[..])
        );
        run(&mut client, &["DEL", &key]).await;
    }

    // ─── Regions closed off the dropping thread ──────────────────────────────

    /// The common case and its opposite on one client: transfer through regions
    /// registered and dropped over and over, some transfers abandoned by a
    /// timeout, then check nothing leaked and transfers still work.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn regions_registered_and_dropped_over_and_over_keep_working() {
        const CYCLES: usize = 100;
        let mut client = dma_client().await;
        let key = format!("glide-rdma-churn-{}", std::process::id());
        let value: Vec<u8> = (0..VALUE_BYTES).map(|index| (index % 251) as u8).collect();
        let mut source = client
            .register_rdma_region(vec![0u8; VALUE_BYTES])
            .expect("registering the source region");
        source.copy_from(&value).unwrap();
        let (source, ()) = succeeded(
            client
                .rdma_set(
                    key.as_bytes(),
                    source,
                    0,
                    VALUE_BYTES,
                    glide_rdma::SetChecksum::Skip,
                )
                .await,
            "BLOB.RDMA_SET",
        );
        drop(source);

        let mut abandoned = 0;
        let started = std::time::Instant::now();
        for cycle in 0..CYCLES {
            let size = VALUE_BYTES * (1 + cycle % 4);
            let buffer = client
                .register_rdma_region(vec![0u8; size])
                .unwrap_or_else(|error| panic!("cycle {cycle}: registering {size} bytes: {error}"));
            if cycle % 5 == 4 {
                // Gives up almost at once, usually before the reply.
                match tokio::time::timeout(
                    std::time::Duration::from_micros(50),
                    client.rdma_get(key.as_bytes(), buffer, 0, size),
                )
                .await
                {
                    Err(_) => abandoned += 1,
                    Ok((buffer, result)) => {
                        result.unwrap_or_else(|error| panic!("cycle {cycle}: {error}"));
                        drop(buffer);
                    }
                }
                continue;
            }
            let (buffer, receipt) = succeeded(
                client.rdma_get(key.as_bytes(), buffer, 0, size).await,
                format!("cycle {cycle}: BLOB.RDMA_GET"),
            );
            let receipt = receipt.expect("the key exists");
            receipt.verify(buffer.as_host()).expect("checksum matches");
            assert_eq!(
                &buffer.as_host()[..VALUE_BYTES],
                &value[..],
                "cycle {cycle}"
            );
            drop(buffer);
        }
        println!(
            "churn: {CYCLES} cycles, {abandoned} abandoned, in {:?}",
            started.elapsed()
        );

        // A last round trip after all the churn.
        let buffer = client
            .register_rdma_region(vec![0u8; VALUE_BYTES])
            .expect("registering after the churn");
        let (buffer, receipt) = succeeded(
            client
                .rdma_get(key.as_bytes(), buffer, 0, VALUE_BYTES)
                .await,
            "BLOB.RDMA_GET after the churn",
        );
        assert_eq!(
            receipt.map(|receipt| receipt.bytes_written),
            Some(VALUE_BYTES)
        );
        assert_eq!(&buffer.as_host()[..VALUE_BYTES], &value[..]);

        assert_eq!(client.rdma_leaked_bytes().unwrap(), 0, "nothing leaked");
        let parked = client.rdma_parked_bytes().unwrap();
        if matches!(provider(), Provider::EfaDirect) {
            assert_eq!(parked, 0, "efa-direct frees once the region closes");
        }
        run(&mut client, &["DEL", &key]).await;
    }

    /// When a `BLOB.RDMA_GET` times out, its region is closed, and on efa a large
    /// region's close takes milliseconds. Closed on the dropping thread, that
    /// would stall every other task on it. `#[tokio::test]` runs on one thread,
    /// like the bindings' default runtime, so a ticker task beside the transfers
    /// sees any stall as a gap between its ticks.
    ///
    /// A `CLIENT PAUSE` holds each transfer at the server so it is sure to time
    /// out. Four time out together on one client, the worst case: closed on the
    /// dropping thread, their closes would also wait on each other for the
    /// fabric's endpoint lock.
    #[tokio::test]
    #[serial_test::serial]
    #[ignore = "needs a server serving the BLOB.* commands"]
    async fn a_timed_out_get_does_not_stall_the_client() {
        const REGION: usize = 1 << 30;
        let key = format!("glide-rdma-stall-{}", std::process::id());
        let mut pauser = plain_client().await;
        let mut client = dma_client().await;

        let mut source = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registering the source region");
        source.as_host_mut().fill(0x5A);
        let (source, ()) = succeeded(
            client
                .rdma_set(
                    key.as_bytes(),
                    source,
                    0,
                    4096,
                    glide_rdma::SetChecksum::Skip,
                )
                .await,
            "BLOB.RDMA_SET",
        );
        drop(source);

        for together in [1usize, 4] {
            let mut regions = Vec::new();
            for _ in 0..together {
                let buffer = client
                    .register_rdma_region(vec![0u8; REGION])
                    .expect("registering a 1 GiB region");
                regions.push(buffer);
            }
            // Open the session first, so the transfers below wait at the server.
            let first = regions.pop().unwrap();
            let (first, _) = succeeded(
                client.rdma_get(key.as_bytes(), first, 0, 4096).await,
                "BLOB.RDMA_GET",
            );
            regions.push(first);
            let closed: Vec<_> = regions.iter().map(|buffer| buffer.revoked()).collect();

            let ticking = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(true));
            let ticker = tokio::spawn({
                let ticking = ticking.clone();
                async move {
                    let mut interval = tokio::time::interval(std::time::Duration::from_millis(1));
                    interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
                    interval.tick().await;
                    let mut last = std::time::Instant::now();
                    let mut longest = std::time::Duration::ZERO;
                    while ticking.load(std::sync::atomic::Ordering::Relaxed) {
                        interval.tick().await;
                        let now = std::time::Instant::now();
                        longest = longest.max(now - last);
                        last = now;
                    }
                    longest
                }
            });
            // Let the ticker settle in.
            tokio::time::sleep(std::time::Duration::from_millis(50)).await;

            let pause = std::time::Duration::from_secs(1);
            let paused_at = std::time::Instant::now();
            run(&mut pauser, &["CLIENT", "PAUSE", "1000", "ALL"]).await;
            let transfers = regions.into_iter().map(|buffer| {
                let mut client = client.clone();
                let key = key.clone();
                async move {
                    tokio::time::timeout(
                        std::time::Duration::from_millis(20),
                        client.rdma_get(key.as_bytes(), buffer, 0, 4096),
                    )
                    .await
                    .is_err()
                }
            });
            let timed_out = futures::future::join_all(transfers).await;
            assert!(
                timed_out.iter().all(|timed_out| *timed_out),
                "the server is paused, so every transfer times out"
            );
            for closed in closed {
                tokio::time::timeout(std::time::Duration::from_secs(1), closed)
                    .await
                    .expect("each abandoned region closes");
            }
            let closed_after = paused_at.elapsed();
            // Keep ticking a little past the closes.
            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
            ticking.store(false, std::sync::atomic::Ordering::Relaxed);
            let longest = ticker.await.unwrap();
            println!(
                "{together} x 1 GiB timed out: longest ticker gap {longest:?}, all closed {closed_after:?} after the pause"
            );

            // Let the pause lapse before the next round or test.
            tokio::time::sleep(
                pause.saturating_sub(paused_at.elapsed()) + std::time::Duration::from_millis(300),
            )
            .await;
            assert!(
                longest < std::time::Duration::from_millis(5),
                "{together} timed-out transfer(s) stalled the runtime for {longest:?}"
            );
        }

        assert_eq!(client.rdma_leaked_bytes().unwrap(), 0);
        run(&mut client, &["DEL", &key]).await;
    }
}
