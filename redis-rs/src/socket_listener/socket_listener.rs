use super::super::{AsyncCommands, RedisResult};
use crate::aio::MultiplexedConnection;
use crate::{Client, RedisError};
use byteorder::{LittleEndian, WriteBytesExt};
use num_traits::ToPrimitive;
use std::ops::Range;
use std::rc::Rc;
use std::{io, thread};
use tokio::io::Interest;
use tokio::net::UnixStream;
use tokio::runtime::Builder;
use tokio::task;
use ClosingReason::*;
use PipeListeningResult::*;

use super::{headers::*, rotating_buffer::RotatingBuffer};

struct SocketListener {
    read_socket: UnixStream,
    rotating_buffer: RotatingBuffer,
}

enum PipeListeningResult {
    Closed(ClosingReason),
    ReceivedValues(Vec<WholeRequest>),
}

impl From<ClosingReason> for PipeListeningResult {
    fn from(result: ClosingReason) -> Self {
        Closed(result)
    }
}

impl SocketListener {
    fn new(read_socket: UnixStream) -> Self {
        let rotating_buffer = RotatingBuffer::new(2, 65_536);
        SocketListener {
            read_socket,
            rotating_buffer,
        }
    }

    pub(crate) async fn next_values(&mut self) -> PipeListeningResult {
        loop {
            let ready_result = self.read_socket.ready(Interest::READABLE).await;
            match ready_result {
                Ok(ready) => {
                    if !ready.is_readable() {
                        continue;
                    }
                }
                Err(err) => {
                    return UnhandledError(err.into()).into();
                }
            };

            let read_result = self
                .read_socket
                .try_read(self.rotating_buffer.current_buffer());
            match read_result {
                Ok(0) => {
                    return ReadSocketClosed.into();
                }
                Ok(size) => {
                    return match self.rotating_buffer.get_requests(size) {
                        Ok(requests) => ReceivedValues(requests),
                        Err(err) => UnhandledError(err.into()).into(),
                    };
                }
                Err(ref e) if e.kind() == io::ErrorKind::WouldBlock => {
                    task::yield_now().await;
                    continue;
                }
                Err(ref e) if e.kind() == io::ErrorKind::Interrupted => {
                    continue;
                }
                Err(err) => {
                    return UnhandledError(err.into()).into();
                }
            }
        }
    }
}

async fn write_to_output(output: &[u8], write_socket: &UnixStream) {
    loop {
        let ready_result = write_socket.ready(Interest::WRITABLE).await;
        if let Ok(ready) = ready_result {
            if !ready.is_writable() {
                continue;
            }
        }
        if let Err(err) = write_socket.try_write(output) {
            if err.kind() == io::ErrorKind::WouldBlock {
                task::yield_now().await;
            } else if err.kind() == io::ErrorKind::Interrupted {
                continue;
            } else {
                // TODO - add proper error handling.
                panic!("received unexpected error {:?}", err);
            }
        } else {
            return;
        }
    }
}

fn write_response_header_to_vec(
    output_buffer: &mut Vec<u8>,
    callback_index: u32,
    response_type: ResponseType,
) {
    let length = output_buffer.capacity();
    // TODO - use serde for easier serialization.
    output_buffer
        .write_u32::<LittleEndian>(length as u32)
        .unwrap();
    output_buffer
        .write_u32::<LittleEndian>(callback_index)
        .unwrap();
    output_buffer
        .write_u32::<LittleEndian>(response_type.to_u32().unwrap())
        .unwrap();
}

fn write_response_header(
    output_buffer: &mut [u8],
    callback_index: u32,
    response_type: ResponseType,
) {
    let length = output_buffer.len();
    // TODO - use serde for easier serialization.
    (&mut output_buffer[..MESSAGE_LENGTH_END])
        .write_u32::<LittleEndian>(length as u32)
        .unwrap();
    (&mut output_buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
        .write_u32::<LittleEndian>(callback_index)
        .unwrap();
    (&mut output_buffer[CALLBACK_INDEX_END..HEADER_END])
        .write_u32::<LittleEndian>(response_type.to_u32().unwrap())
        .unwrap();
}

async fn send_set_request(
    buffer: SharedBuffer,
    key_range: Range<usize>,
    value_range: Range<usize>,
    callback_index: u32,
    mut connection: MultiplexedConnection,
    write_socket: Rc<UnixStream>,
) {
    let _: RedisResult<()> = connection
        .set(&buffer[key_range], &buffer[value_range])
        .await; // TODO - add proper error handling.
    let mut output_buffer = [0_u8; HEADER_END];
    write_response_header(&mut output_buffer, callback_index, ResponseType::Null);
    write_to_output(&output_buffer, &write_socket).await;
}

async fn send_get_request(
    vec: SharedBuffer,
    key_range: Range<usize>,
    callback_index: u32,
    mut connection: MultiplexedConnection,
    write_socket: Rc<UnixStream>,
) {
    let result: Option<Vec<u8>> = connection.get(&vec[key_range]).await.unwrap(); // TODO - add proper error handling.
    match result {
        Some(result_bytes) => {
            let length = HEADER_END + result_bytes.len();
            let mut output_buffer = Vec::with_capacity(length);
            write_response_header_to_vec(&mut output_buffer, callback_index, ResponseType::String);
            output_buffer.extend_from_slice(&result_bytes);
            write_to_output(&mut output_buffer, &write_socket).await;
        }
        None => {
            let mut output_buffer = [0 as u8; HEADER_END];
            write_response_header(&mut output_buffer, callback_index, ResponseType::Null);
            write_to_output(&mut output_buffer, &write_socket).await;
        }
    };
}

fn handle_request(
    request: WholeRequest,
    connection: MultiplexedConnection,
    write_socket: Rc<UnixStream>,
) {
    task::spawn_local(async move {
        match request.request_type {
            RequestRanges::Get { key: key_range } => {
                send_get_request(
                    request.buffer,
                    key_range,
                    request.callback_index,
                    connection,
                    write_socket,
                )
                .await;
            }
            RequestRanges::Set {
                key: key_range,
                value: value_range,
            } => {
                send_set_request(
                    request.buffer,
                    key_range,
                    value_range,
                    request.callback_index,
                    connection,
                    write_socket,
                )
                .await;
            }
        }
    });
}

async fn handle_requests(
    received_requests: Vec<WholeRequest>,
    connection: &MultiplexedConnection,
    write_socket: &Rc<UnixStream>,
) {
    // TODO - can use pipeline here, if we're fine with the added latency.
    for request in received_requests {
        let connection = connection.clone();
        let write_socket = write_socket.clone();
        handle_request(request, connection, write_socket)
    }
    // Yield to ensure that the subtasks aren't starved.
    task::yield_now().await;
}

async fn listen_on_socket<StartCallback, CloseCallback>(
    client: Client,
    read_socket_name: &str,
    write_socket_name: &str,
    start_callback: StartCallback,
    close_callback: CloseCallback,
) where
    StartCallback: FnOnce() + Send + 'static,
    CloseCallback: FnOnce(ClosingReason) + Send + 'static,
{
    let read_socket = match UnixStream::connect(read_socket_name).await {
        Ok(socket) => socket,
        Err(err) => {
            close_callback(FailedInitialization(err.into()));
            return;
        }
    };
    let write_socket = match UnixStream::connect(write_socket_name).await {
        Ok(socket) => socket,
        Err(err) => {
            close_callback(FailedInitialization(err.into()));
            return;
        }
    };
    let write_socket = Rc::new(write_socket);
    let connection = match client.get_multiplexed_async_connection().await {
        Ok(socket) => socket,
        Err(err) => {
            close_callback(FailedInitialization(err));
            return;
        }
    };
    let mut listener = SocketListener::new(read_socket);
    let local = task::LocalSet::new();
    start_callback();
    local
        .run_until(async move {
            loop {
                let listening_result = listener.next_values().await;
                match listening_result {
                    Closed(reason) => {
                        close_callback(reason);
                        return;
                    }
                    ReceivedValues(received_requests) => {
                        handle_requests(received_requests, &connection, &write_socket).await;
                    }
                };
            }
        })
        .await;
}

#[derive(Debug)]
/// Enum describing the reason that a socket listener stopped listening on a socket.
pub enum ClosingReason {
    /// The socket was closed. This is usually the required way to close the listener.
    ReadSocketClosed,
    /// The listener encounter an error it couldn't handle.
    UnhandledError(RedisError),
    /// The listener couldn't start due to Redis connection error.
    FailedInitialization(RedisError),
}

/// Start a thread  
///
/// # Arguments
///
/// * `client` - the client from which to create a connection.
///
/// * `read_socket_name` - name of the socket from which the listener will receive requests.
///
/// * `write_socket_name` - name of the socket to which the listener will send results.
///
/// * `start_callback` - called when the thread started listening on the socket. This is used to prevent races.
///
/// * `close_callback` - called when the listener stopped listening, with the reason for stopping.
pub fn start_socket_listener<StartCallback, CloseCallback>(
    client: Client,
    read_socket_name: String,
    write_socket_name: String,
    start_callback: StartCallback,
    close_callback: CloseCallback,
) where
    StartCallback: FnOnce() + Send + 'static,
    CloseCallback: FnOnce(ClosingReason) + Send + 'static,
{
    thread::Builder::new()
        .name("socket_listener_thread".to_string())
        .spawn(move || {
            let runtime = Builder::new_current_thread()
                .enable_all()
                .thread_name("socket_listener_thread")
                .build();
            match runtime {
                Ok(runtime) => {
                    runtime.block_on(listen_on_socket(
                        client,
                        read_socket_name.as_str(),
                        write_socket_name.as_str(),
                        start_callback,
                        close_callback,
                    ));
                }
                Err(err) => close_callback(FailedInitialization(err.into())),
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}
