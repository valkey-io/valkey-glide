// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! RDMA integration tests that need a live server but no fabric hardware.
//!
//! Requires the `rdma` feature. Needs libfabric since these tests open a fabric,
//! but over the `tcp` provider rather than a real card.

#![cfg(feature = "rdma")]

mod constants;
mod utilities;

#[cfg(test)]
mod dma_tests {
    use crate::utilities::mocks::{Mock, ServerMock};
    use crate::utilities::*;
    use glide_core::client::{Client, NodeAddress};
    use glide_core::rdma::{FabricConfig, Provider, RdmaSetting};
    use redis::FromRedisValue;
    use redis::{ConnectionAddr, Value};
    use std::collections::HashMap;

    /// Unwrap a transfer that must succeed, and the buffer it hands back.
    fn succeeded<T>(
        (buffer, result): glide_core::client::RdmaOutcome<T>,
        what: impl std::fmt::Display,
    ) -> (glide_rdma::RdmaBuffer, T) {
        let value = result.unwrap_or_else(|error| panic!("{what}: {error}"));
        let buffer = buffer.expect("a finished transfer hands the buffer back");
        assert!(
            !buffer.is_revoked(),
            "{what}: a finished transfer keeps its region"
        );
        (buffer, value)
    }

    fn extract_port(addr: &redis::ConnectionAddr) -> u16 {
        match addr {
            redis::ConnectionAddr::Tcp(_, port) => *port,
            redis::ConnectionAddr::TcpTls { port, .. } => *port,
            other => panic!("unexpected address type: {other:?}"),
        }
    }

    #[tokio::test]
    async fn a_server_without_the_module_still_serves_ordinary_commands() {
        let server = RedisServer::new(ServerType::Tcp { tls: false });
        let port = extract_port(&server.get_client_addr());
        wait_for_server_to_become_ready(&server.get_client_addr()).await;

        let request = glide_core::client::ConnectionRequest {
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port,
            }],
            // tcp is the development provider: it opens a real endpoint
            // without EFA hardware, which is all this test needs.
            rdma: RdmaSetting::Configured(FabricConfig::new(Provider::Tcp)),
            ..Default::default()
        };

        // Asking for RDMA is asking for a faster path to some keys, not for a
        // different client. A server that cannot serve it is still a server.
        let mut client = Client::new(request, None)
            .await
            .expect("a server without the module still yields a working client");

        let mut set = redis::cmd("SET");
        set.arg("plain-key").arg("plain-value");
        client
            .send_command(&mut set, None)
            .await
            .expect("SET works on a client that asked for RDMA");

        let mut get = redis::cmd("GET");
        get.arg("plain-key");
        let reply = client
            .send_command(&mut get, None)
            .await
            .expect("GET works");
        assert_eq!(
            String::from_owned_redis_value(reply).expect("GET returns text"),
            "plain-value"
        );

        // Only a transfer handshakes, so a transfer is where the missing module
        // is reported.
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("the client has a fabric, so it registers");
        let (_, result) = client.rdma_get(b"plain-key", buffer, 0, 4096).await;
        let error = result.expect_err("a node that refused the handshake cannot serve a transfer");
        let message = error.to_string();
        assert!(
            message.contains("no RDMA module loaded"),
            "the transfer error must name what the server is missing: {error}"
        );
        assert!(
            message.contains(&port.to_string()),
            "and which node is missing it: {error}"
        );
    }

    #[tokio::test]
    async fn a_cluster_without_the_module_still_serves_ordinary_commands() {
        const PRIMARIES: u16 = 3;

        let cluster = cluster::RedisCluster::new(false, &None, Some(PRIMARIES), Some(0));
        let ports: Vec<u16> = cluster
            .get_server_addresses()
            .iter()
            .map(extract_port)
            .collect();

        let request = glide_core::client::ConnectionRequest {
            addresses: ports
                .iter()
                .map(|port| NodeAddress {
                    host: "127.0.0.1".to_string(),
                    port: *port,
                })
                .collect(),
            cluster_mode_enabled: true,
            request_timeout: Some(10_000),
            rdma: RdmaSetting::Configured(FabricConfig::new(Provider::Tcp)),
            ..Default::default()
        };

        let mut client = Client::new(request, None)
            .await
            .expect("a cluster without the module still yields a working client");

        // Building the connections sends no handshake, so the cluster is as usable
        // as it would be with RDMA switched off.
        let mut set = redis::cmd("SET");
        set.arg("plain-key").arg("plain-value");
        client
            .send_command(&mut set, None)
            .await
            .expect("SET works across a cluster that asked for RDMA");

        let mut get = redis::cmd("GET");
        get.arg("plain-key");
        let reply = client
            .send_command(&mut get, None)
            .await
            .expect("GET works");
        assert_eq!(
            String::from_owned_redis_value(reply).expect("GET returns text"),
            "plain-value"
        );
    }

    /// A client built without RDMA says what is missing, whether it is asked to
    /// register memory or to transfer.
    #[tokio::test]
    async fn a_client_without_a_fabric_names_the_cause() {
        let server = RedisServer::new(ServerType::Tcp { tls: false });
        let port = extract_port(&server.get_client_addr());
        wait_for_server_to_become_ready(&server.get_client_addr()).await;

        let request = glide_core::client::ConnectionRequest {
            addresses: vec![NodeAddress {
                host: "127.0.0.1".to_string(),
                port,
            }],
            ..Default::default()
        };
        let mut client = Client::new(request, None)
            .await
            .expect("plain client connects");

        let error = client
            .register_rdma_region(vec![0u8; 4096])
            .expect_err("a client without a fabric cannot register");
        assert!(error.to_string().contains("RdmaConfiguration"), "{error}");
        for error in [
            client
                .rdma_parked_bytes()
                .expect_err("no fabric, so nothing to report"),
            client
                .rdma_leaked_bytes()
                .expect_err("no fabric, so nothing to report"),
        ] {
            assert!(error.to_string().contains("RdmaConfiguration"), "{error}");
        }

        // A buffer cannot be made without a fabric, so borrow one from a
        // standalone tcp endpoint purely to have something to pass.
        let fabric =
            glide_rdma::RdmaFabric::open(&glide_rdma::FabricConfig::new(glide_rdma::Provider::Tcp))
                .expect("tcp fabric opens");
        let buffer = fabric.register(vec![0u8; 4096]).expect("registers");
        let (buffer, result) = client.rdma_get(b"key", buffer, 0, 4096).await;
        let error = result.expect_err("a client without a fabric cannot transfer");
        assert!(
            buffer.is_some(),
            "a transfer that never started hands the buffer back"
        );
        assert!(error.to_string().contains("RdmaConfiguration"), "{error}");
    }

    // ---- against a mock server ----------------------------------------------
    //
    // A mock can answer `BLOB.*` commands without a server module, including with
    // errors and with no reply at all, which a live server cannot be made to do.

    /// What a standalone client asks a node before it will use it.
    fn primary_responses() -> HashMap<String, Value> {
        HashMap::from([
            (
                "*1\r\n$4\r\nPING\r\n".to_string(),
                Value::BulkString(b"PONG".to_vec().into()),
            ),
            (
                "*2\r\n$4\r\nINFO\r\n$11\r\nREPLICATION\r\n".to_string(),
                Value::BulkString(b"role:master\r\n".to_vec().into()),
            ),
        ])
    }

    /// A `BLOB.RDMA_HELLO` reply naming one peer and the endpoint it belongs to.
    ///
    /// The address comes from a real tcp endpoint rather than a handwritten
    /// sockaddr, because what `fi_av_insert` accepts is platform-specific -- a
    /// BSD `sockaddr_in` leads with `sin_len`, a Linux one does not. Nothing is
    /// ever sent to it; the insert is a local operation. The endpoint is returned
    /// so the caller can hold it open and keep the address its own.
    fn hello_reply() -> (glide_rdma::RdmaFabric, String) {
        let peer =
            glide_rdma::RdmaFabric::open(&glide_rdma::FabricConfig::new(glide_rdma::Provider::Tcp))
                .expect("tcp fabric opens");
        let hex = glide_rdma::encode_hex(peer.local_address());
        let reply = format!("*1\r\n${}\r\n{hex}\r\n", hex.len());
        (peer, reply)
    }

    fn mock_address(mock: &ServerMock) -> NodeAddress {
        match mock
            .get_addresses()
            .first()
            .expect("the mock has an address")
        {
            ConnectionAddr::Tcp(host, port) => NodeAddress {
                host: host.clone(),
                port: *port,
            },
            other => panic!("unexpected mock address: {other:?}"),
        }
    }

    async fn rdma_client_against(mock: &ServerMock) -> Client {
        let request = glide_core::client::ConnectionRequest {
            addresses: vec![mock_address(mock)],
            rdma: RdmaSetting::Configured(FabricConfig::new(Provider::Tcp)),
            ..Default::default()
        };
        Client::new(request, None).await.expect(
            "building a client sends no BLOB.RDMA_HELLO, so the mock needs no script for it",
        )
    }

    /// A transfer that fails before its command is sent never gave the server the
    /// memory, so the buffer must come back usable, not revoked. Hitting the in-flight
    /// limit is the everyday case: it is back-pressure under load.
    #[tokio::test]
    async fn a_transfer_refused_by_the_inflight_limit_hands_back_a_usable_buffer() {
        let (_peer, mock) = server_that_never_answers("BLOB.RDMA_GET");
        let request = glide_core::client::ConnectionRequest {
            addresses: vec![mock_address(&mock)],
            rdma: RdmaSetting::Configured(FabricConfig::new(Provider::Tcp)),
            inflight_requests_limit: Some(1),
            ..Default::default()
        };
        let client = Client::new(request, None).await.expect("connects");

        // Take the one in-flight slot with a transfer the server never answers.
        let mut holding = client.clone();
        let first = client.register_rdma_region(vec![0u8; 4096]).unwrap();
        let _held = tokio::spawn(async move { holding.rdma_get(b"key", first, 0, 4096).await });
        tokio::time::sleep(STUCK).await;

        let mut refused = client.clone();
        let second = client.register_rdma_region(vec![0u8; 4096]).unwrap();
        let (buffer, result) = refused.rdma_get(b"key", second, 0, 4096).await;

        let error = result.expect_err("no in-flight slot is free");
        assert!(error.to_string().contains("inflight"), "{error}");
        let buffer = buffer.expect("nothing was sent, so the buffer comes back");
        assert!(!buffer.is_revoked(), "and it can be lent again");
        assert_eq!(client.rdma_parked_bytes().unwrap(), 0);
    }

    /// A byte count and its checksum, which is what a read reply carries. The
    /// mock writes nothing, so the checksum is that of the eight zero bytes every
    /// test's region starts with.
    const READ_REPLY: &str = "*2\r\n:8\r\n:2351477386\r\n";

    /// A refusal from a node that will not take writes. Earlier module builds
    /// registered the handshake as a write command, so every replica answered it
    /// this way; the current one accepts it, but a node can still refuse.
    const READONLY: &str = "-READONLY You can't write against a read only replica.\r\n";

    #[tokio::test]
    async fn the_session_is_opened_by_the_first_transfer_and_only_once() {
        let (_peer, hello) = hello_reply();
        let scripts = HashMap::from([
            // A second handshake would take this reply and fail the transfer
            // that sent it, so the test cannot pass if one is ever sent.
            (
                "BLOB.RDMA_HELLO".to_string(),
                vec![hello, "-ERR a second BLOB.RDMA_HELLO\r\n".to_string()],
            ),
            ("BLOB.RDMA_GET".to_string(), vec![READ_REPLY.to_string()]),
        ]);
        let mock = ServerMock::new_with_command_scripts(primary_responses(), scripts);

        let mut client = rdma_client_against(&mock).await;

        assert_eq!(
            mock.get_number_of_received_commands(),
            0,
            "building a connection must not handshake: a client that never \
             transfers should cost exactly what it costs without RDMA"
        );

        let mut buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");
        for attempt in 1..=2 {
            buffer = succeeded(client.rdma_get(b"key", buffer, 0, 4096).await, attempt).0;
        }

        assert_eq!(
            mock.get_number_of_received_commands(),
            3,
            "one BLOB.RDMA_HELLO for the two BLOB.GETs, not one each"
        );
    }

    /// A reply the mock never sends: an empty script entry writes nothing.
    const NO_REPLY: &str = "";

    /// A mock whose handshake succeeds and whose `command` is never answered.
    fn server_that_never_answers(command: &str) -> (glide_rdma::RdmaFabric, ServerMock) {
        let (peer, hello) = hello_reply();
        let scripts = HashMap::from([
            ("BLOB.RDMA_HELLO".to_string(), vec![hello]),
            (command.to_string(), vec![NO_REPLY.to_string()]),
        ]);
        (
            peer,
            ServerMock::new_with_command_scripts(primary_responses(), scripts),
        )
    }

    /// How long a transfer is given to show it is really stuck before it is
    /// cancelled, and how long the cancellation is given to take effect.
    const STUCK: std::time::Duration = std::time::Duration::from_millis(200);
    const PROMPTLY: std::time::Duration = std::time::Duration::from_secs(5);

    /// Memory for a region that records when it is freed.
    struct Tracked {
        bytes: Vec<u8>,
        freed: std::sync::Arc<std::sync::atomic::AtomicBool>,
    }

    impl AsMut<[u8]> for Tracked {
        fn as_mut(&mut self) -> &mut [u8] {
            &mut self.bytes
        }
    }

    impl Drop for Tracked {
        fn drop(&mut self) {
            self.freed.store(true, std::sync::atomic::Ordering::SeqCst);
        }
    }

    fn tracked() -> (Tracked, std::sync::Arc<std::sync::atomic::AtomicBool>) {
        let freed = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let memory = Tracked {
            bytes: vec![0u8; 4096],
            freed: freed.clone(),
        };
        (memory, freed)
    }

    /// Wait up to [`PROMPTLY`] for `freed` to be set.
    async fn freed_promptly(freed: &std::sync::atomic::AtomicBool) -> bool {
        let deadline = tokio::time::Instant::now() + PROMPTLY;
        while !freed.load(std::sync::atomic::Ordering::SeqCst) {
            if tokio::time::Instant::now() > deadline {
                return false;
            }
            tokio::time::sleep(std::time::Duration::from_millis(10)).await;
        }
        true
    }

    /// The test fabric is tcp, where closing a region does not stop a write already
    /// accepted, so a cancelled transfer's memory is parked until the fabric closes.
    /// The caller can see how much, and a new client is the way to get it back.
    #[tokio::test]
    async fn a_cancelled_read_parks_its_memory_until_the_client_is_dropped() {
        let (_peer, mock) = server_that_never_answers("BLOB.RDMA_GET");
        let client = rdma_client_against(&mock).await;
        let (memory, freed) = tracked();
        let buffer = client.register_rdma_region(memory).expect("registers");
        let revoker = buffer.revoker();

        let mut transferring = client.clone();
        let transfer =
            tokio::spawn(async move { transferring.rdma_get(b"key", buffer, 0, 4096).await });
        tokio::time::sleep(STUCK).await;
        revoker.revoke().expect("the region closes");
        let _ = tokio::time::timeout(PROMPTLY, transfer)
            .await
            .expect("the revoke ends the wait");

        mock.close().await;
        tokio::time::sleep(STUCK).await;
        assert!(
            !freed.load(std::sync::atomic::Ordering::SeqCst),
            "without a reply, tcp memory stays parked"
        );
        assert_eq!(
            client.rdma_parked_bytes().unwrap(),
            4096,
            "and the caller can see how much"
        );
        assert_eq!(client.rdma_leaked_bytes().unwrap(), 0, "parked, not leaked");

        // A new client is the way to get it back: once this one and the buffers
        // registered through it are gone, its fabric closes and frees the memory.
        drop(client);
        assert!(freed_promptly(&freed).await);
    }

    /// The hung-server case: no reply is coming, so only closing the client can
    /// end the wait. It must also leave the region revoked, since that is what
    /// makes returning early safe.
    #[tokio::test]
    async fn closing_the_client_cancels_a_read_the_server_never_answers() {
        let (_peer, mock) = server_that_never_answers("BLOB.RDMA_GET");
        let client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");

        let mut transferring = client.clone();
        let transfer =
            tokio::spawn(async move { transferring.rdma_get(b"key", buffer, 0, 4096).await });
        tokio::time::sleep(STUCK).await;
        assert!(!transfer.is_finished(), "the server never replies");

        client.close_rdma().expect("the region closes");

        let (buffer, outcome) = tokio::time::timeout(PROMPTLY, transfer)
            .await
            .expect("closing the client ends the wait")
            .unwrap();
        assert!(
            buffer.is_none(),
            "a recalled loan's memory is parked until the fabric closes"
        );
        let cancelled = outcome.expect_err("a cancelled read is an error");
        assert_eq!(cancelled.kind(), redis::ErrorKind::ClientError);
        assert!(
            cancelled.to_string().contains("RDMA transfer cancelled"),
            "{cancelled}"
        );
    }

    #[tokio::test]
    async fn closing_the_client_cancels_a_write_the_server_never_answers() {
        let (_peer, mock) = server_that_never_answers("BLOB.RDMA_SET");
        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![1u8; 4096])
            .expect("registers");

        let closer = client.clone();
        let closing = tokio::spawn(async move {
            tokio::time::sleep(STUCK).await;
            closer.close_rdma()
        });

        let (buffer, outcome) =
            tokio::time::timeout(STUCK + PROMPTLY, client.rdma_set(b"key", buffer, 0, 4096))
                .await
                .expect("closing the client ends the wait");
        assert!(
            buffer.is_none(),
            "a recalled loan's memory is parked until the fabric closes"
        );
        let cancelled = outcome.expect_err("a cancelled write is an error");
        closing.await.unwrap().expect("the region closes");
        assert!(
            cancelled.to_string().contains("RDMA transfer cancelled"),
            "{cancelled}"
        );
    }

    /// Revoking one region is the same cancellation without closing the client,
    /// and the error should say which of the two happened.
    #[tokio::test]
    async fn revoking_a_region_cancels_the_transfer_using_it() {
        let (_peer, mock) = server_that_never_answers("BLOB.RDMA_GET");
        let client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");
        let revoker = buffer.revoker();

        let mut transferring = client.clone();
        let transfer =
            tokio::spawn(async move { transferring.rdma_get(b"key", buffer, 0, 4096).await });
        tokio::time::sleep(STUCK).await;

        revoker.revoke().expect("the region closes");

        let (buffer, outcome) = tokio::time::timeout(PROMPTLY, transfer)
            .await
            .expect("revoking the region ends the wait")
            .unwrap();
        assert!(
            buffer.is_none(),
            "a recalled loan's memory is parked until the fabric closes"
        );
        let cancelled = outcome.expect_err("a cancelled read is an error");
        assert!(
            cancelled.to_string().contains("region was revoked"),
            "{cancelled}"
        );
    }

    /// `BLOB.*` sent any other way than `rdma_get` and `rdma_set` would skip their
    /// window checks, progress and cancellation, so the client refuses it before
    /// anything reaches the server.
    #[tokio::test]
    async fn rdma_commands_sent_directly_are_refused() {
        let (_peer, hello) = hello_reply();
        let scripts = HashMap::from([
            ("BLOB.RDMA_HELLO".to_string(), vec![hello]),
            ("BLOB.RDMA_GET".to_string(), vec![READ_REPLY.to_string()]),
            ("BLOB.RDMA_SET".to_string(), vec!["+OK\r\n".to_string()]),
        ]);
        let mock = ServerMock::new_with_command_scripts(primary_responses(), scripts);
        let mut client = rdma_client_against(&mock).await;

        for name in ["BLOB.RDMA_HELLO", "BLOB.RDMA_GET", "blob.rdma_set"] {
            let mut command = redis::cmd(name);
            command.arg("key").arg("7").arg("4096");
            let refused = client
                .send_command(&mut command, None)
                .await
                .expect_err("a direct RDMA command is refused");
            assert_eq!(refused.kind(), redis::ErrorKind::ClientError, "{refused}");
        }

        let mut batch = redis::pipe();
        batch.cmd("SET").arg("key").arg("value");
        batch
            .cmd("BLOB.RDMA_SET")
            .arg("key")
            .arg("8")
            .arg("7")
            .arg("4096");
        client
            .send_pipeline(
                &batch,
                None,
                false,
                None,
                redis::PipelineRetryStrategy {
                    retry_server_error: false,
                    retry_connection_error: false,
                },
            )
            .await
            .expect_err("a batch holding an RDMA command is refused");
        client
            .send_transaction(&batch, None, None, false)
            .await
            .expect_err("so is a transaction");

        assert_eq!(
            mock.get_number_of_received_commands(),
            0,
            "refused before anything was sent, the batch's SET included"
        );

        // The approved path still works on the same client.
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");
        succeeded(client.rdma_get(b"key", buffer, 0, 4096).await, "rdma_get");
    }

    /// Nothing RDMA can start after a close, but the client is otherwise intact.
    #[tokio::test]
    async fn a_closed_client_refuses_rdma_and_still_serves_other_commands() {
        let (_peer, hello) = hello_reply();
        let scripts = HashMap::from([("BLOB.RDMA_HELLO".to_string(), vec![hello])]);
        let mock = ServerMock::new_with_command_scripts(primary_responses(), scripts);
        let mut client = rdma_client_against(&mock).await;
        let registered_before = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");

        client.close_rdma().expect("closes");
        client.close_rdma().expect("closing again is harmless");

        let refused = client
            .register_rdma_region(vec![0u8; 4096])
            .expect_err("no registration after close");
        assert!(refused.to_string().contains("closed"), "{refused}");
        let (_, result) = client.rdma_get(b"key", registered_before, 0, 4096).await;
        let refused = result.expect_err("no transfer after close");
        assert!(refused.to_string().contains("closed"), "{refused}");
        assert_eq!(
            mock.get_number_of_received_commands(),
            0,
            "a refused transfer sends nothing, not even the handshake"
        );

        let pong = client
            .send_command(&mut redis::cmd("PING"), None)
            .await
            .expect("other commands are unaffected");
        assert_eq!(pong, Value::BulkString(b"PONG".to_vec().into()));
    }

    /// The promotion case, which is why a refusal cannot be remembered.
    ///
    /// A node refuses the handshake, as a replica did on module builds that
    /// treated it as a write. The connection built against it stays perfectly
    /// usable, and once the node will take it, for example after a promotion, the
    /// next transfer has to ask again on the same connection rather than having
    /// written the node off.
    #[tokio::test]
    async fn a_refused_handshake_is_tried_again_by_the_next_transfer() {
        let (_peer, hello) = hello_reply();
        let scripts = HashMap::from([
            (
                "BLOB.RDMA_HELLO".to_string(),
                vec![READONLY.to_string(), hello],
            ),
            ("BLOB.RDMA_GET".to_string(), vec![READ_REPLY.to_string()]),
        ]);
        let mock = ServerMock::new_with_command_scripts(primary_responses(), scripts);

        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");

        let (buffer, result) = client.rdma_get(b"key", buffer, 0, 4096).await;
        let refused = result.expect_err("a node that refuses the handshake cannot transfer");
        // The transfer command was never sent, so the server never touched the
        // memory and the buffer is still usable.
        let buffer = buffer.expect("the buffer comes back");
        assert!(!buffer.is_revoked());
        assert!(
            refused.to_string().contains("refused the RDMA handshake"),
            "the error must say the handshake was refused: {refused}"
        );
        assert_eq!(
            refused.kind(),
            redis::ErrorKind::ReadOnly,
            "a replica's refusal stays READONLY so a cluster client retries it"
        );
        assert_eq!(
            mock.get_number_of_received_commands(),
            1,
            "a refused handshake sends no transfer"
        );

        // Same client, same connection, same buffer -- only the node's answer changed.
        succeeded(
            client.rdma_get(b"key", buffer, 0, 4096).await,
            "the next transfer asks again, and this time it is answered",
        );

        assert_eq!(
            mock.get_number_of_received_commands(),
            3,
            "the retry handshook again and then transferred"
        );
    }

    /// A mock that handshakes and then answers `BLOB.RDMA_GET` with each reply in turn.
    fn server_answering_reads(replies: &[&str]) -> (glide_rdma::RdmaFabric, ServerMock) {
        let (peer, hello) = hello_reply();
        let scripts = HashMap::from([
            ("BLOB.RDMA_HELLO".to_string(), vec![hello]),
            (
                "BLOB.RDMA_GET".to_string(),
                replies.iter().map(|reply| reply.to_string()).collect(),
            ),
        ]);
        (
            peer,
            ServerMock::new_with_command_scripts(primary_responses(), scripts),
        )
    }

    /// An error reply means the server is done with the memory, so the buffer
    /// comes back usable rather than revoked.
    #[tokio::test]
    async fn an_error_reply_hands_back_a_buffer_that_can_be_lent_again() {
        let (_peer, mock) = server_answering_reads(&["-ERR no room\r\n", READ_REPLY]);
        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");

        let (buffer, result) = client.rdma_get(b"key", buffer, 0, 4096).await;
        let error = result.expect_err("the server said no");
        assert!(error.to_string().contains("no room"), "{error}");
        let buffer = buffer.expect("the buffer comes back");
        assert!(!buffer.is_revoked(), "the server replied, so it is done");

        let (_, receipt) = succeeded(client.rdma_get(b"key", buffer, 0, 4096).await, "retry");
        assert_eq!(receipt.map(|receipt| receipt.bytes_written), Some(8));
    }

    /// A reply is a reply even when it cannot be read.
    #[tokio::test]
    async fn a_malformed_reply_hands_back_a_usable_buffer() {
        let (_peer, mock) = server_answering_reads(&["+OK\r\n"]);
        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");

        let (buffer, result) = client.rdma_get(b"key", buffer, 0, 4096).await;

        result.expect_err("OK is not a read reply");
        assert!(!buffer.expect("the buffer comes back").is_revoked());
    }

    #[tokio::test]
    async fn a_reply_larger_than_the_window_is_an_error_and_keeps_the_buffer() {
        let (_peer, mock) = server_answering_reads(&["*2\r\n:4097\r\n:0\r\n"]);
        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 8192])
            .expect("registers");

        let (buffer, result) = client.rdma_get(b"key", buffer, 0, 4096).await;

        let error = result.expect_err("more than the window held");
        assert_eq!(
            error.kind(),
            redis::ErrorKind::UserOperationError,
            "{error}"
        );
        assert!(!buffer.expect("the buffer comes back").is_revoked());
    }

    /// The caller gave up on the reply, for example with a timeout. The server may
    /// still write into the memory, so the region must close before anything else
    /// can touch it.
    #[tokio::test]
    async fn abandoning_a_transfer_revokes_its_region() {
        let (_peer, mock) = server_that_never_answers("BLOB.RDMA_GET");
        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 4096])
            .expect("registers");
        let revoker = buffer.revoker();
        let closed = buffer.revoked();

        tokio::time::timeout(STUCK, client.rdma_get(b"key", buffer, 0, 4096))
            .await
            .expect_err("the server never replies, so the wait times out");

        // Dropping the transfer dropped the loan, which queued the close on the
        // fabric's closing thread rather than closing the region in the drop.
        tokio::time::timeout(PROMPTLY, closed)
            .await
            .expect("dropping the transfer closed the region");
        assert!(revoker.is_released());
    }

    /// A window that runs past the end is refused before anything is sent, and the
    /// buffer is untouched.
    #[tokio::test]
    async fn a_window_past_the_end_is_refused_without_a_transfer() {
        let (_peer, mock) = server_answering_reads(&[READ_REPLY]);
        let mut client = rdma_client_against(&mock).await;
        let buffer = client
            .register_rdma_region(vec![0u8; 1024])
            .expect("registers");

        let (buffer, result) = client.rdma_get(b"key", buffer, 768, 512).await;

        let error = result.expect_err("the window runs past the end");
        assert!(error.to_string().contains("1024"), "{error}");
        assert!(!buffer.expect("the buffer comes back").is_revoked());
        assert_eq!(
            mock.get_number_of_received_commands(),
            0,
            "nothing was sent"
        );
    }
}
