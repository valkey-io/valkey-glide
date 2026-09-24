// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Covers what a `MonitorClient` does when the server sends the first monitor line in
//! the same write as the `MONITOR` `+OK` reply, which a busy server routinely does.
//!
//! A scripted server is used instead of a real one because the packing has to be exact
//! for these cases to be distinguishable, and a real server only produces it under load.

#[cfg(test)]
mod test_monitor_handshake_buffering {
    use glide_core::client::{
        MonitorClient, MonitorLine, MonitorLineCallback, NodeAddress, TlsMode,
    };
    use redis::RedisConnectionInfo;
    use std::sync::{Arc, Mutex};
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::TcpListener;
    use tokio::sync::oneshot;

    /// Short enough to survive the connection decoder's read window intact, which is
    /// what makes this the whole-frame case.
    const SHORT_PACKED_LINE: &str = "+1.5 [0 1.2.3.4:1] \"GET\" \"shortkey\"\r\n";
    /// A realistically sized line, longer than that read window, so the handshake read
    /// stops in the middle of it.
    const LONG_PACKED_LINE: &str =
        "+1720000000.000000 [0 127.0.0.1:55501] \"GET\" \"other_key\"\r\n";
    /// The line every scenario waits for, standing in for the command under test.
    const TARGET_LINE: &str =
        "+1720000000.500000 [0 127.0.0.1:55502] \"SET\" \"monitor_test_key\" \"monitor_test_val\"\r\n";
    const CANARY_LINE: &str =
        "+1720000000.100000 [0 127.0.0.1:55503] \"PING\" \"monitor_canary\"\r\n";

    const DEADLINE: std::time::Duration = std::time::Duration::from_secs(5);

    fn monitor_conn_info() -> RedisConnectionInfo {
        RedisConnectionInfo {
            db: 0,
            username: None,
            password: None,
            protocol: redis::ProtocolVersion::RESP2,
            client_name: None,
            lib_name: None,
            lib_ver: None,
            cache: None,
            server_assisted_cache: false,
        }
    }

    fn make_collector() -> (MonitorLineCallback, Arc<Mutex<Vec<MonitorLine>>>) {
        let lines: Arc<Mutex<Vec<MonitorLine>>> = Arc::new(Mutex::new(Vec::new()));
        let lines_clone = lines.clone();
        let cb: MonitorLineCallback = Arc::new(move |line| {
            lines_clone.lock().unwrap().push(line);
        });
        (cb, lines)
    }

    fn is_target(line: &MonitorLine) -> bool {
        line.command == "SET" && line.args.first().map(|s| s.as_str()) == Some("monitor_test_key")
    }

    fn is_short_packed(line: &MonitorLine) -> bool {
        line.command == "GET" && line.args.first().map(|s| s.as_str()) == Some("shortkey")
    }

    fn is_canary(line: &MonitorLine) -> bool {
        line.command == "PING" && line.args.first().map(|s| s.as_str()) == Some("monitor_canary")
    }

    /// Polls for a matching line to a bounded deadline. Returns whether one arrived.
    async fn wait_for(
        lines: &Arc<Mutex<Vec<MonitorLine>>>,
        predicate: impl Fn(&MonitorLine) -> bool,
    ) -> bool {
        let deadline = std::time::Instant::now() + DEADLINE;
        loop {
            if lines.lock().unwrap().iter().any(&predicate) {
                return true;
            }
            if std::time::Instant::now() >= deadline {
                return false;
            }
            tokio::time::sleep(std::time::Duration::from_millis(5)).await;
        }
    }

    /// Reads one RESP command, returning its name uppercased, or `None` at end of input.
    async fn read_command(
        socket: &mut tokio::net::TcpStream,
        pending: &mut Vec<u8>,
    ) -> Option<String> {
        loop {
            if let Some((name, consumed)) = parse_command(pending) {
                pending.drain(..consumed);
                return Some(name);
            }
            let mut chunk = [0u8; 1024];
            let read = socket.read(&mut chunk).await.ok()?;
            if read == 0 {
                return None;
            }
            pending.extend_from_slice(&chunk[..read]);
        }
    }

    /// Returns the command name and the byte count it occupied, or `None` if `buf` does
    /// not yet hold a whole command.
    fn parse_command(buf: &[u8]) -> Option<(String, usize)> {
        let mut pos = 0;
        let line_end = find_crlf(buf, pos)?;
        if buf[pos] != b'*' {
            return None;
        }
        let argc: usize = std::str::from_utf8(&buf[pos + 1..line_end])
            .ok()?
            .parse()
            .ok()?;
        pos = line_end + 2;
        let mut name = String::new();
        for index in 0..argc {
            let header_end = find_crlf(buf, pos)?;
            if buf[pos] != b'$' {
                return None;
            }
            let len: usize = std::str::from_utf8(&buf[pos + 1..header_end])
                .ok()?
                .parse()
                .ok()?;
            let arg_start = header_end + 2;
            let arg_end = arg_start + len;
            if buf.len() < arg_end + 2 {
                return None;
            }
            if index == 0 {
                name = String::from_utf8_lossy(&buf[arg_start..arg_end]).to_uppercase();
            }
            pos = arg_end + 2;
        }
        Some((name, pos))
    }

    fn find_crlf(buf: &[u8], from: usize) -> Option<usize> {
        (from..buf.len().saturating_sub(1)).find(|&i| buf[i] == b'\r' && buf[i + 1] == b'\n')
    }

    /// Handles to drive a scripted server: its address, a sender that releases the
    /// remaining output, and a receiver that reports the `MONITOR` reply was sent.
    struct Scripted {
        addr: std::net::SocketAddr,
        release: oneshot::Sender<()>,
        answered: oneshot::Receiver<()>,
    }

    /// Answers connection setup, then answers `MONITOR` with `+OK` and `packed` in a
    /// single write. Sends `remainder` only once the test releases it, so the bytes the
    /// handshake read can see are exactly `packed` and nothing more.
    async fn scripted_server(packed: String, remainder: String) -> Scripted {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let (release, release_rx) = oneshot::channel::<()>();
        let (answered_tx, answered) = oneshot::channel::<()>();

        tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            socket.set_nodelay(true).unwrap();
            let mut pending = Vec::new();
            while let Some(name) = read_command(&mut socket, &mut pending).await {
                if name == "MONITOR" {
                    let mut packet = String::from("+OK\r\n");
                    packet.push_str(&packed);
                    socket.write_all(packet.as_bytes()).await.unwrap();
                    socket.flush().await.unwrap();
                    let _ = answered_tx.send(());
                    break;
                }
                socket.write_all(b"+OK\r\n").await.unwrap();
                socket.flush().await.unwrap();
            }
            let _ = release_rx.await;
            socket.write_all(remainder.as_bytes()).await.unwrap();
            socket.flush().await.unwrap();
            // Stay open so a client that lost the buffered bytes waits on the socket
            // rather than seeing end of input, which keeps a timeout meaningful.
            let mut discard = [0u8; 64];
            while matches!(socket.read(&mut discard).await, Ok(n) if n > 0) {}
        });

        Scripted {
            addr,
            release,
            answered,
        }
    }

    struct Outcome {
        target: bool,
        packed_seen: bool,
        canary_seen: bool,
        diagnostics: String,
    }

    /// Runs one scenario end to end and reports which lines arrived.
    async fn run(packed: String, remainder: String, want_canary: bool) -> Outcome {
        let server = scripted_server(packed, remainder).await;
        let node_addr = NodeAddress {
            host: server.addr.ip().to_string(),
            port: server.addr.port(),
        };
        let (on_line, lines) = make_collector();
        let monitor = MonitorClient::new(&node_addr, monitor_conn_info(), TlsMode::NoTls, on_line)
            .await
            .expect("MonitorClient::new failed");

        // The constructor has returned, so the handshake read is done and the stream
        // exists. Releasing the rest only now keeps each scenario's packing exact.
        let _ = server.answered.await;
        let _ = server.release.send(());

        let canary_seen = want_canary && wait_for(&lines, is_canary).await;
        let target = wait_for(&lines, is_target).await;
        let packed_seen = lines.lock().unwrap().iter().any(is_short_packed);
        let diagnostics = format!("{:?}", monitor.diagnostics());
        monitor.stop_async().await;
        Outcome {
            target,
            packed_seen,
            canary_seen,
            diagnostics,
        }
    }

    /// Nothing is packed with `+OK`, so nothing can be lost. Guards the scripted server
    /// itself, so a failure elsewhere in this file points at the client.
    #[tokio::test]
    async fn unpacked_handshake_delivers_the_line() {
        let outcome = run(String::new(), TARGET_LINE.to_string(), false).await;
        assert!(
            outcome.target,
            "scripted server never delivered a line; diagnostics: {}",
            outcome.diagnostics
        );
    }

    /// The whole first line shares the handshake write. Later lines arriving is not
    /// enough on its own, so this checks the packed line itself reaches the caller.
    #[tokio::test]
    async fn whole_packed_line_is_delivered() {
        let outcome = run(
            SHORT_PACKED_LINE.to_string(),
            TARGET_LINE.to_string(),
            false,
        )
        .await;
        assert!(
            outcome.target,
            "timed out waiting for the later line; diagnostics: {}",
            outcome.diagnostics
        );
        assert!(
            outcome.packed_seen,
            "the line packed with the handshake reply was dropped; diagnostics: {}",
            outcome.diagnostics
        );
    }

    /// The handshake write is cut mid-line. Resuming at the wrong offset fails to parse,
    /// which ends the stream and costs every later line too. This is the case that made
    /// the monitor deliver nothing at all.
    #[tokio::test]
    async fn partially_packed_line_does_not_end_the_stream() {
        let outcome = run(LONG_PACKED_LINE.to_string(), TARGET_LINE.to_string(), false).await;
        assert!(
            outcome.target,
            "timed out waiting for the later line; diagnostics: {}",
            outcome.diagnostics
        );
    }

    /// Same cut line, with an earlier line to wait for first. A dead stream loses both,
    /// which is how one cut line costs a monitor everything that comes after it.
    #[tokio::test]
    async fn partially_packed_line_does_not_cost_following_lines() {
        let outcome = run(
            LONG_PACKED_LINE.to_string(),
            format!("{CANARY_LINE}{TARGET_LINE}"),
            true,
        )
        .await;
        assert!(
            outcome.canary_seen,
            "timed out waiting for the first line after the handshake; diagnostics: {}",
            outcome.diagnostics
        );
        assert!(
            outcome.target,
            "timed out waiting for the later line; diagnostics: {}",
            outcome.diagnostics
        );
    }

    /// A line written while the reader task cannot run has to survive until it does.
    /// A slow machine and the buffering bug look alike from the outside, and this test
    /// separates them, so the next person here does not blame scheduling.
    #[tokio::test]
    async fn line_written_while_the_task_cannot_run_is_not_lost() {
        let server = scripted_server(String::new(), TARGET_LINE.to_string()).await;
        let node_addr = NodeAddress {
            host: server.addr.ip().to_string(),
            port: server.addr.port(),
        };
        let (on_line, lines) = make_collector();
        let monitor = MonitorClient::new(&node_addr, monitor_conn_info(), TlsMode::NoTls, on_line)
            .await
            .expect("MonitorClient::new failed");
        let _ = server.answered.await;
        let _ = server.release.send(());

        // Blocking the runtime thread is the stall being reproduced: it starves the
        // reader task for as long as the line takes to arrive.
        std::thread::sleep(std::time::Duration::from_millis(500));
        assert!(
            lines.lock().unwrap().is_empty(),
            "the reader task ran while its thread was blocked"
        );

        let target = wait_for(&lines, is_target).await;
        let diagnostics = format!("{:?}", monitor.diagnostics());
        monitor.stop_async().await;
        assert!(
            target,
            "a starved reader task lost the line; diagnostics: {diagnostics}"
        );
    }
}
