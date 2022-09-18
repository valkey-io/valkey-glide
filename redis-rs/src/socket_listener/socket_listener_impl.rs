use super::super::{AsyncCommands, RedisResult};
use super::{headers::*, rotating_buffer::RotatingBuffer};
use crate::aio::MultiplexedConnection;
use crate::{Client, RedisError};
use byteorder::{LittleEndian, WriteBytesExt};
use lifeguard::{pool, Pool, RcRecycled, StartingSize, Supplier};
use num_traits::ToPrimitive;
use std::ops::Range;
use std::rc::Rc;
use std::str;
use std::{io, thread};
use tokio::io::Interest;
use tokio::net::{UnixListener, UnixStream};
use tokio::runtime::Builder;
use tokio::sync::Mutex;
use tokio::task;
use ClosingReason::*;
use PipeListeningResult::*;
use std::path::Path;
use chrono::{DateTime, Utc};

pub const SOCKET_PATH: &'static str = "babushka-socket";

struct SocketListener {
    read_socket: Rc<UnixStream>,
    rotating_buffer: RotatingBuffer,
    pool: Rc<Pool<Vec<u8>>>,
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


impl Drop for SocketListener {
    fn drop(&mut self) {
        close_socket(SOCKET_PATH);
    }
}

impl SocketListener {
    fn new(read_socket: Rc<UnixStream>) -> Self {
        let pool = Rc::new(
            pool()
                .with(StartingSize(2))
                .with(Supplier(move || Vec::<u8>::with_capacity(65_536)))
                .build(),
        );
        let rotating_buffer = RotatingBuffer::with_pool(pool.clone());
        SocketListener {
            read_socket,
            rotating_buffer,
            pool,
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
                .try_read_buf(self.rotating_buffer.current_buffer());
            match read_result {
                Ok(0) => {
                    println!("read result 0");
                    return ReadSocketClosed.into();
                }
                Ok(n) => {
                    let now: DateTime<Utc> = Utc::now();
                    println!("{:?} Rust: try_read read {:?} bytes", now.to_rfc3339(), n);
                    return match self.rotating_buffer.get_requests() {
                        Ok(requests) => ReceivedValues(requests),
                        Err(err) => {println!("read result UnhandledError");
                            UnhandledError(err.into()).into()
                        },
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
                    println!("read result UnhandledError2");
                    return UnhandledError(err.into()).into();
                }
            }
        }
    }
}

async fn write_to_output(output: &[u8], write_socket: &UnixStream, lock: &Mutex<()>) {
    let _ = lock.lock().await;
    //let write_socket = write_socket.lock().await;
    let mut total_written_bytes = 0;
    while total_written_bytes < output.len() {
        let ready_result = write_socket.ready(Interest::WRITABLE).await;
        if let Ok(ready) = ready_result {
            if !ready.is_writable() {
                continue;
            }
        }
        match write_socket.try_write(&output[total_written_bytes..]) {
            Ok(written_bytes) => {
                total_written_bytes += written_bytes;
            }
            Err(err) if err.kind() == io::ErrorKind::WouldBlock => {
                task::yield_now().await;
            }
            Err(err) if err.kind() == io::ErrorKind::Interrupted => {}
            Err(err) => {
                // TODO - add proper error handling.
                panic!("received unexpected error {:?}", err);
            }
        }
    }
}

fn write_response_header_to_vec(
    output_buffer: &mut Vec<u8>,
    callback_index: u32,
    response_type: ResponseType,
    length: usize,
) {
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
    lock: Rc<Mutex<()>>
) {
    let _: RedisResult<()> = connection
        .set(&buffer[key_range], &buffer[value_range])
        .await; // TODO - add proper error handling.
    let mut output_buffer = [0_u8; HEADER_END];
    write_response_header(&mut output_buffer, callback_index, ResponseType::Null);
    write_to_output(&output_buffer, &write_socket, &lock).await;
}

fn get_vec(pool: &Pool<Vec<u8>>, required_capacity: usize) -> RcRecycled<Vec<u8>> {
    let mut vec = pool.new_rc();
    vec.clear();
    let current_capacity = vec.capacity();
    if required_capacity > current_capacity {
        vec.reserve(required_capacity.next_power_of_two() - current_capacity);
    }
    vec
}

async fn send_get_request(
    vec: SharedBuffer,
    key_range: Range<usize>,
    callback_index: u32,
    mut connection: MultiplexedConnection,
    write_socket: Rc<Mutex<UnixStream>>,
    pool: &Pool<Vec<u8>>,
    lock: Rc<Mutex<()>>
) {
    let result: Option<Vec<u8>> = connection.get(&vec[key_range]).await.unwrap(); // TODO - add proper error handling.
    match result {
        Some(result_bytes) => {
            let length = HEADER_END + result_bytes.len();
            let mut output_buffer = get_vec(pool, length);
            write_response_header_to_vec(
                &mut output_buffer,
                callback_index,
                ResponseType::String,
                length,
            );
            output_buffer.extend_from_slice(&result_bytes);
            let offset = output_buffer.len() % 4;
            if offset != 0 {
                output_buffer.resize(length + 4 - offset, 0);
            }
            write_to_output(&output_buffer, &write_socket, &lock).await;
        }
        None => {
            let mut output_buffer = [0_u8; HEADER_END];
            write_response_header(&mut output_buffer, callback_index, ResponseType::Null);
            write_to_output(&output_buffer, &write_socket, &lock).await;
        }
    };
}

fn handle_request(
    request: WholeRequest,
    connection: MultiplexedConnection,
    write_socket: Rc<Mutex<UnixStream>>,
    pool: Rc<Pool<Vec<u8>>>,
    lock: Rc<Mutex<()>>
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
                    &pool,
                    lock
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
                    lock
                )
                .await;
            }
        }
    });
}

async fn handle_requests(
    received_requests: Vec<WholeRequest>,
    connection: &MultiplexedConnection,
    write_socket: &Rc<Mutex<UnixStream>>,
    pool: Rc<Pool<Vec<u8>>>,
    lock: &Rc<Mutex<()>>
) {
    // TODO - can use pipeline here, if we're fine with the added latency.
    for request in received_requests {
        let connection = connection.clone();
        let write_socket = write_socket.clone();
        let lock = lock.clone();
        handle_request(request, connection, write_socket,  pool.clone(), lock)
    }
    // Yield to ensure that the subtasks aren't starved.
    task::yield_now().await;
}

fn close_socket(socket_name: &str) {
    let path = Path::new(socket_name);
    let _ = match std::fs::remove_file(path) {
        Ok(()) => { 
            println!("Successfully deleted socket file");
        },
        Err(e) => { println!("Failed to delete socket file: {}", e);
        }
    };
}

async fn listen_on_socket<StartCallback, CloseCallback>(
    client: Client,
    read_socket_name: &str,
    write_socket_name: &str,
    start_callback: StartCallback,
    close_callback: CloseCallback,
) where
    StartCallback: Fn() + Send + 'static,
    CloseCallback: Fn(ClosingReason) + Send + 'static,
{
    // Bind to socket
    let listener = match UnixListener::bind(SOCKET_PATH) {
        Ok(listener) => listener,
        Err(err) => {
            println!("{}",err);
            close_callback(FailedInitialization(err.into()));
            return;
        }
    };
    let local = task::LocalSet::new();
    let client_rc = Rc::new(client);
    let close_callback_rc = Rc::new(close_callback);
    start_callback();
    local
        .run_until(async move {
            loop {
                println!("Rust: entering loop");
                let cloned_close_callback = close_callback_rc.clone();
                match listener.accept().await {
                    Ok((stream, _addr)) => {
                        let cloned_client = client_rc.clone();
                        println!("Rust: new client!");
                        task::spawn_local(async move {
                            let connection =
                                match cloned_client.get_multiplexed_async_connection().await {
                                    Ok(socket) => socket,
                                    Err(err) => {
                                        cloned_close_callback(FailedInitialization(err));
                                        return;
                                    }
                                };
                            let rc_stream = Rc::new(stream);
                            let lock = Rc::new(Mutex::new(()));
                            let mut client_listener = SocketListener::new(rc_stream.clone());
                            loop {
                                let listening_result = client_listener.next_values().await;
                                match listening_result {
                                    Closed(reason) => {
                                        println!("Rust: Closing! {:?}", reason);
                                        cloned_close_callback(reason);
                                        return;
                                    }
                                    ReceivedValues(received_requests) => {
                                        handle_requests(received_requests, &connection, &rc_stream, &lock.clone())
                                            .await;
                                    }
                                }
                        }});
                    }
                    Err(err) => {
                        cloned_close_callback(FailedInitialization(err.into()));
                    }
                }
            }
        })
        .await;
    println!("Rust: Bye!");
    close_socket(SOCKET_PATH);
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
    StartCallback: Fn() + Send + 'static,
    CloseCallback: Fn(ClosingReason) + Send + 'static,
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
                Err(err) => {
                    close_callback(FailedInitialization(err.into()))
                }
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}
