// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use futures_intrusive::sync::ManualResetEvent;
use redis::{Cmd, ConnectionAddr, Value};
use std::collections::HashMap;
use std::io;
use std::io::Read;
use std::io::Write;
use std::net::TcpListener;
use std::net::TcpStream as StdTcpStream;
use std::str::from_utf8;
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicU16, Ordering},
};
use tokio::sync::mpsc::UnboundedSender;

pub struct MockedRequest {
    pub expected_message: String,
    pub response: String,
}

pub struct ServerMock {
    request_sender: UnboundedSender<MockedRequest>,
    address: ConnectionAddr,
    received_commands: Arc<AtomicU16>,
    /// Total PINGs seen on the socket, whether answered from the
    /// constant-response table or dropped by the blackhole.
    ping_count: Arc<AtomicU16>,
    runtime: Option<tokio::runtime::Runtime>, // option so that we can take the runtime on drop.
    closing_signal: Arc<ManualResetEvent>,
    closing_completed_signal: Arc<ManualResetEvent>,
    /// When set, PING requests received on the socket are read but not
    /// answered. Reproduces a silent half-open flow where the transport
    /// still accepts writes but the read half never delivers a
    /// response. Used by the idle_timeout integration test.
    ping_blackhole: Arc<AtomicBool>,
}

fn read_from_socket(
    buffer: &mut [u8],
    socket: &mut StdTcpStream,
    closing_signal: &Arc<ManualResetEvent>,
) -> Option<usize> {
    while !closing_signal.is_set() {
        let read_res = socket.read(buffer); // read() is using timeout
        match read_res {
            Ok(0) => {
                return None;
            }
            Ok(size) => {
                return Some(size);
            }
            Err(ref e)
                if e.kind() == io::ErrorKind::WouldBlock
                    || e.kind() == io::ErrorKind::Interrupted =>
            {
                continue;
            }
            Err(_) => {
                return None;
            }
        }
    }
    // If we reached here, it means we got a signal to terminate
    None
}

/// Escape and print a RESP message
fn log_resp_message(msg: &str) {
    logger_core::log_info(
        "Test",
        format!(
            "{:?} {}",
            std::thread::current().id(),
            msg.replace('\r', "\\r").replace('\n', "\\n")
        ),
    );
}

fn receive_and_respond_to_next_message(
    receiver: &mut tokio::sync::mpsc::UnboundedReceiver<MockedRequest>,
    socket: &mut StdTcpStream,
    received_commands: &Arc<AtomicU16>,
    ping_count: &Arc<AtomicU16>,
    constant_responses: &HashMap<String, Value>,
    closing_signal: &Arc<ManualResetEvent>,
    ping_blackhole: &Arc<AtomicBool>,
) -> bool {
    let mut buffer = vec![0; 1024];
    let size = match read_from_socket(&mut buffer, socket, closing_signal) {
        Some(size) => size,
        None => {
            return false;
        }
    };
    let message = from_utf8(&buffer[..size]).unwrap().to_string();
    log_resp_message(&message);

    if message.contains("PING") {
        ping_count.fetch_add(1, Ordering::AcqRel);
    }

    if ping_blackhole.swap(false, Ordering::AcqRel) && message.contains("PING") {
        // Read and drop the PING once, then clear the blackhole and
        // shut down this socket. The client's pre-command PING will
        // trip its bounded deadline, close the dead flow, and start a
        // fresh reconnect. Subsequent PINGs (on the fresh socket) go
        // through normally so the retried command sees a live
        // transport.
        let _ = socket.shutdown(std::net::Shutdown::Both);
        return false;
    }

    let setinfo_count = message.matches("SETINFO").count();
    if setinfo_count > 0 {
        let mut buffer = Vec::new();
        for _ in 0..setinfo_count {
            super::encode_value(&Value::Okay, &mut buffer).unwrap();
        }
        socket.write_all(&buffer).unwrap();
        return true;
    }

    if message.contains("HELLO") {
        let mut buffer = Vec::new();
        let response = Value::Map(vec![
            (Value::BulkString(b"proto".to_vec().into()), Value::Int(3)),
            (
                Value::BulkString(b"role".to_vec().into()),
                Value::BulkString(b"master".to_vec().into()),
            ),
        ]);
        super::encode_value(&response, &mut buffer).unwrap();
        socket.write_all(&buffer).unwrap();
        return true;
    }

    if let Some(response) = constant_responses.get(&message) {
        let mut buffer = Vec::new();
        super::encode_value(response, &mut buffer).unwrap();
        socket.write_all(&buffer).unwrap();
        return true;
    }
    let Ok(request) = receiver.try_recv() else {
        panic!("Received unexpected message: {message}");
    };
    received_commands.fetch_add(1, Ordering::AcqRel);
    assert_eq!(message, request.expected_message);
    socket.write_all(request.response.as_bytes()).unwrap();
    true
}

pub trait Mock {
    fn get_addresses(&self) -> Vec<ConnectionAddr>;

    fn add_response(&self, request: &Cmd, response: String);

    fn get_number_of_received_commands(&self) -> u16;
}

impl ServerMock {
    pub fn new(constant_responses: HashMap<String, Value>) -> Self {
        let listener = super::get_listener_on_available_port();
        Self::new_with_listener(constant_responses, listener)
    }

    pub fn new_with_listener(
        constant_responses: HashMap<String, Value>,
        listener: TcpListener,
    ) -> Self {
        let (request_sender, mut receiver) = tokio::sync::mpsc::unbounded_channel();
        let received_commands = Arc::new(AtomicU16::new(0));
        let received_commands_clone = received_commands.clone();
        let ping_count = Arc::new(AtomicU16::new(0));
        let ping_count_clone = ping_count.clone();
        let address = ConnectionAddr::Tcp(
            "localhost".to_string(),
            listener.local_addr().unwrap().port(),
        );
        let closing_signal = Arc::new(ManualResetEvent::new(false));
        let closing_signal_clone = closing_signal.clone();
        let closing_completed_signal = Arc::new(ManualResetEvent::new(false));
        let closing_completed_signal_clone = closing_completed_signal.clone();
        let ping_blackhole = Arc::new(AtomicBool::new(false));
        let ping_blackhole_clone = ping_blackhole.clone();
        let address_clone = address.clone();
        std::thread::spawn(move || {
            logger_core::log_info("Test", format!("ServerMock started on: {address_clone}"));

            // Poll accept in a loop so a client-side reconnect can drop
            // its current socket and immediately establish a replacement
            // against the same listener. `accept()` on the underlying
            // socket blocks by default, but the outer loop watches the
            // closing signal so the thread exits cleanly on drop.
            listener
                .set_nonblocking(true)
                .expect("set listener non-blocking");

            'accept: loop {
                let mut socket = loop {
                    if closing_signal_clone.is_set() {
                        break 'accept;
                    }
                    match listener.accept() {
                        Ok((sock, _)) => break sock,
                        Err(ref e) if e.kind() == io::ErrorKind::WouldBlock => {
                            std::thread::sleep(std::time::Duration::from_millis(10));
                            continue;
                        }
                        Err(_) => break 'accept,
                    }
                };
                let _ = socket.set_read_timeout(Some(std::time::Duration::from_millis(10)));

                while receive_and_respond_to_next_message(
                    &mut receiver,
                    &mut socket,
                    &received_commands_clone,
                    &ping_count_clone,
                    &constant_responses,
                    &closing_signal_clone,
                    &ping_blackhole_clone,
                ) {}

                // Terminate the current connection and go accept the
                // next one. A well-behaved client that noticed the
                // half-open flow reconnects to the same port and picks
                // up the fresh handshake through the constant-response
                // table.
                let _ = socket.shutdown(std::net::Shutdown::Both);
            }

            closing_completed_signal_clone.set();

            logger_core::log_info(
                "Test",
                format!("{:?} ServerMock exited", std::thread::current().id()),
            );
        });

        Self {
            request_sender,
            address,
            received_commands,
            ping_count,
            runtime: None,
            closing_signal,
            closing_completed_signal,
            ping_blackhole,
        }
    }

    /// Enable or disable the PING blackhole. When enabled, any PING
    /// received on the socket is read but silently dropped so the
    /// client's pre-command PING times out on its bounded deadline.
    pub fn set_ping_blackhole(&self, blackhole: bool) {
        self.ping_blackhole.store(blackhole, Ordering::Release);
    }

    /// Total number of PING messages seen on the socket, including any
    /// that were dropped by the blackhole. Useful for asserting the
    /// pre-command validation fired.
    pub fn get_ping_count(&self) -> u16 {
        self.ping_count.load(Ordering::Acquire)
    }

    pub async fn close(self) {
        self.closing_signal.set();
        self.closing_completed_signal.wait().await;
    }
}

impl Mock for ServerMock {
    fn get_addresses(&self) -> Vec<ConnectionAddr> {
        vec![self.address.clone()]
    }

    fn add_response(&self, request: &Cmd, response: String) {
        let expected_message = String::from_utf8(request.get_packed_command()).unwrap();
        let _ = self.request_sender.send(MockedRequest {
            expected_message,
            response,
        });
    }

    fn get_number_of_received_commands(&self) -> u16 {
        self.received_commands.load(Ordering::Acquire)
    }
}

impl Drop for ServerMock {
    fn drop(&mut self) {
        self.closing_signal.set();
    }
}
