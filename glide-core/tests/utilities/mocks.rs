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
    atomic::{AtomicU16, Ordering},
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
    runtime: Option<tokio::runtime::Runtime>, // option so that we can take the runtime on drop.
    closing_signal: Arc<ManualResetEvent>,
    closing_completed_signal: Arc<ManualResetEvent>,
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
    constant_responses: &HashMap<String, Value>,
    closing_signal: &Arc<ManualResetEvent>,
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
            (Value::BulkString(b"proto".to_vec()), Value::Int(3)),
            (
                Value::BulkString(b"role".to_vec()),
                Value::BulkString(b"master".to_vec()),
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
        let address = ConnectionAddr::Tcp(
            "localhost".to_string(),
            listener.local_addr().unwrap().port(),
        );
        let closing_signal = Arc::new(ManualResetEvent::new(false));
        let closing_signal_clone = closing_signal.clone();
        let closing_completed_signal = Arc::new(ManualResetEvent::new(false));
        let closing_completed_signal_clone = closing_completed_signal.clone();
        let address_clone = address.clone();
        std::thread::spawn(move || {
            logger_core::log_info("Test", format!("ServerMock started on: {address_clone}"));
            let mut socket: StdTcpStream = listener.accept().unwrap().0;
            let _ = socket.set_read_timeout(Some(std::time::Duration::from_millis(10)));

            while receive_and_respond_to_next_message(
                &mut receiver,
                &mut socket,
                &received_commands_clone,
                &constant_responses,
                &closing_signal_clone,
            ) {}

            // Terminate the connection
            let _ = socket.shutdown(std::net::Shutdown::Both);

            // Now notify exit completed
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
            runtime: None,
            closing_signal,
            closing_completed_signal,
        }
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
