use redis::{Cmd, ConnectionAddr, Value};
use std::collections::HashMap;
use std::str::from_utf8;
use std::{
    io::{Read, Write},
    sync::{
        atomic::{AtomicU16, Ordering},
        mpsc::Sender,
        Arc,
    },
    thread,
};

pub struct MockedRequest {
    pub expected_message: String,
    pub response: String,
}

pub struct ServerMock {
    request_sender: Sender<MockedRequest>,
    address: ConnectionAddr,
    received_commands: Arc<AtomicU16>,
    thread_handle: Option<thread::JoinHandle<()>>, // option so that we can take the handle and join on it later.
}

fn receive_and_respond_to_next_message(
    receiver: &std::sync::mpsc::Receiver<MockedRequest>,
    socket: &mut std::net::TcpStream,
    received_commands: &Arc<AtomicU16>,
    constant_responses: &HashMap<String, Value>,
) -> bool {
    let mut buffer = vec![0_u8; 1024];
    let size = socket.read(&mut buffer).unwrap();
    let message = from_utf8(&buffer[..size]).unwrap().to_string();
    if size == 0 {
        return false;
    }

    if let Some(response) = constant_responses.get(&message) {
        let mut buffer = Vec::new();
        super::encode_value(response, &mut buffer).unwrap();
        socket.write_all(&buffer).unwrap();
        return true;
    }
    let Ok(request) = receiver.try_recv() else {
        panic!("Received unexpected message: {}", message);
    };
    received_commands.fetch_add(1, Ordering::Relaxed);
    assert_eq!(message, request.expected_message,);
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
        let (request_sender, receiver) = std::sync::mpsc::channel();
        let received_commands = Arc::new(AtomicU16::new(0));
        let address = ConnectionAddr::Tcp(
            "localhost".to_string(),
            listener.local_addr().unwrap().port(),
        );
        let thread_handle = Some({
            let received_commands = received_commands.clone();
            thread::Builder::new()
                .name(format!("ServerMock - {address}"))
                .spawn(move || {
                    let mut socket = listener.accept().unwrap().0;

                    while receive_and_respond_to_next_message(
                        &receiver,
                        &mut socket,
                        &received_commands,
                        &constant_responses,
                    ) {}
                })
                .unwrap()
        });
        Self {
            request_sender,
            address,
            received_commands,
            thread_handle,
        }
    }
}

impl Mock for ServerMock {
    fn get_addresses(&self) -> Vec<ConnectionAddr> {
        vec![self.address.clone()]
    }

    fn add_response(&self, request: &Cmd, response: String) {
        let expected_message = String::from_utf8(request.get_packed_command()).unwrap();
        self.request_sender
            .send(MockedRequest {
                expected_message,
                response,
            })
            .unwrap();
    }

    fn get_number_of_received_commands(&self) -> u16 {
        self.received_commands.load(Ordering::Relaxed)
    }
}

impl Drop for ServerMock {
    fn drop(&mut self) {
        self.thread_handle.take().unwrap().join().unwrap();
    }
}
