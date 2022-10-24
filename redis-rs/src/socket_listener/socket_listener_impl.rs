use super::super::{AsyncCommands, RedisResult};
use super::{headers::*, rotating_buffer::RotatingBuffer};
use crate::aio::MultiplexedConnection;
use crate::{Client, RedisError};
use byteorder::{LittleEndian, WriteBytesExt};
use futures::stream::StreamExt;
use lifeguard::{pool, Pool, RcRecycled, StartingSize, Supplier};
use num_traits::ToPrimitive;
use signal_hook::consts::signal::*;
use signal_hook_tokio::Signals;
use std::ops::Range;
use std::rc::Rc;
use std::str;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::{io, thread};
use tokio::io::ErrorKind::AddrInUse;
use tokio::io::Interest;
use tokio::net::{UnixListener, UnixStream};
use tokio::runtime::Builder;
use tokio::sync::Mutex;
use tokio::sync::Notify;
use tokio::task;
use ClosingReason::*;
use PipeListeningResult::*;
/// The socket file name
pub const SOCKET_FILE_NAME: &str = "babushka-socket";

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
                    return ReadSocketClosed.into();
                }
                Ok(_) => {
                    return match self.rotating_buffer.get_requests() {
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
                Err(err) => return UnhandledError(err.into()).into(),
            }
        }
    }
}

async fn write_to_output(output: &[u8], write_socket: &UnixStream, write_lock: &Mutex<()>) {
    let _ = write_lock.lock().await;
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
    write_lock: Rc<Mutex<()>>,
) {
    let _: RedisResult<()> = connection
        .set(&buffer[key_range], &buffer[value_range])
        .await; // TODO - add proper error handling.
    let mut output_buffer = [0_u8; HEADER_END];
    write_response_header(&mut output_buffer, callback_index, ResponseType::Null);
    write_to_output(&output_buffer, &write_socket, &write_lock).await;
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
    write_socket: Rc<UnixStream>,
    pool: &Pool<Vec<u8>>,
    write_lock: Rc<Mutex<()>>,
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
            write_to_output(&output_buffer, &write_socket, &write_lock).await;
        }
        None => {
            let mut output_buffer = [0_u8; HEADER_END];
            write_response_header(&mut output_buffer, callback_index, ResponseType::Null);
            write_to_output(&output_buffer, &write_socket, &write_lock).await;
        }
    };
}

fn handle_request(
    request: WholeRequest,
    connection: MultiplexedConnection,
    write_socket: Rc<UnixStream>,
    pool: Rc<Pool<Vec<u8>>>,
    write_lock: Rc<Mutex<()>>,
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
                    write_lock,
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
                    write_lock,
                )
                .await;
            }
            RequestRanges::ServerAddress { address: _ } => {}
        }
    });
}

async fn handle_requests(
    received_requests: Vec<WholeRequest>,
    connection: &MultiplexedConnection,
    write_socket: &Rc<UnixStream>,
    pool: Rc<Pool<Vec<u8>>>,
    write_lock: &Rc<Mutex<()>>,
) {
    // TODO - can use pipeline here, if we're fine with the added latency.
    for request in received_requests {
        //let connection = connection.clone();
        let write_socket = write_socket.clone();
        let lock = write_lock.clone();
        handle_request(
            request,
            connection.clone(),
            write_socket,
            pool.clone(),
            lock,
        )
    }
    // Yield to ensure that the subtasks aren't starved.
    task::yield_now().await;
}

fn close_socket() {
    match std::fs::remove_file(get_socket_path()) {
        Ok(()) => {}
        Err(e) => {
            panic!("Failed to delete socket file: {}", e); // TODO: better error handling
        }
    };
}

async fn wait_for_server_address_create_conn(
    client_listener: &mut SocketListener,
    socket: &Rc<UnixStream>,
    write_lock: &Rc<Mutex<()>>,
) -> Result<MultiplexedConnection, BabushkaError> {
    // Wait for the server's address
    match client_listener.next_values().await {
        Closed(reason) => {
            return Err(BabushkaError::CloseError(reason));
        }
        ReceivedValues(received_requests) => {
            if let Some(index) = (0..received_requests.len()).next() {
                let request = received_requests.get(index).unwrap();
                match request.request_type.clone() {
                    RequestRanges::ServerAddress {
                        address: address_range,
                    } => {
                        let address = &request.buffer[address_range];
                        let address = std::str::from_utf8(address).expect("Found invalid UTF-8");
                        let client = Client::open(address).unwrap(); // TODO: better error handling
                        let connection = match client.get_multiplexed_async_connection().await {
                            Ok(conn) => conn,
                            Err(err) => {
                                return Err(BabushkaError::BaseError(format!(
                                    "Failed to create a multiplexed connection: {:?}",
                                    err
                                )))
                            }
                        };
                        let mut output_buffer = [0_u8; HEADER_END];
                        write_response_header(
                            &mut output_buffer,
                            request.callback_index,
                            ResponseType::Null,
                        );
                        write_to_output(&output_buffer, socket, write_lock).await;
                        return Ok(connection);
                    }
                    _ => {
                        return Err(BabushkaError::BaseError(
                            "Received another request before receiving server address".to_string(),
                        ))
                    }
                }
            }
        }
    }
    Err(BabushkaError::BaseError(
        "Failed to get the server's address".to_string(),
    ))
}

fn update_notify_connected_clients(
    connected_clients: Arc<AtomicUsize>,
    close_notifier: Arc<Notify>,
) {
    // Check if the entire socket listener should be closed before
    // closing the client's connection task
    connected_clients.fetch_sub(1, Ordering::SeqCst);
    if connected_clients.load(Ordering::SeqCst) == 0 {
        // No more clients connected, close the socket
        close_notifier.notify_one();
    }
}

async fn listen_on_socket<StartCallback, CloseCallback>(
    start_callback: StartCallback,
    close_callback: Rc<CloseCallback>,
) where
    StartCallback: Fn() + Send + 'static,
    CloseCallback: Fn(ClosingReason) + Send + 'static,
{
    // Bind to socket
    let listener = match UnixListener::bind(get_socket_path()) {
        Ok(listener) => listener,
        Err(err) => {
            if err.kind() == AddrInUse {
                // Address already in use, exit this thread and try to connect to the existing listener
                start_callback();
            } else {
                // Got another error while trying to bind
                close_callback(FailedInitialization(err.into()));
            }
            return;
        }
    };
    let local = task::LocalSet::new();
    let connected_clients = Arc::new(AtomicUsize::new(0));
    let notify_close = Arc::new(Notify::new());
    start_callback();
    local
        .run_until(async move {
            loop {
                tokio::select! {
                    listen_v = listener.accept() => {
                    if let Ok((stream, _addr)) = listen_v {
                        // New client
                        let cloned_close_notifier = notify_close.clone();
                        let cloned_connected_clients = connected_clients.clone();
                        cloned_connected_clients.fetch_add(1, Ordering::SeqCst);
                        task::spawn_local(async move {
                            // Spawn a new task to listen on this client's stream
                            let rc_stream = Rc::new(stream);
                            let write_lock = Rc::new(Mutex::new(()));
                            let mut client_listener = SocketListener::new(rc_stream.clone());
                            let connection = match wait_for_server_address_create_conn(&mut client_listener,
                                    &rc_stream,
                                    &write_lock.clone()
                                ).await {
                                    Ok(conn) => conn,
                                    Err(BabushkaError::CloseError(_reason)) => {
                                        update_notify_connected_clients(cloned_connected_clients, cloned_close_notifier);
                                        return; // TODO: implement error protocol, handle closing reasons different from ReadSocketClosed
                                    }
                                    Err(BabushkaError::BaseError(err)) => {
                                        panic!("{:?}", err); // TODO: implement error protocol
                                    }
                            };
                            loop {
                                let listening_result = client_listener.next_values().await;
                                match listening_result {
                                    Closed(_reason) => {
                                        update_notify_connected_clients(cloned_connected_clients, cloned_close_notifier);
                                        return; // TODO: implement error protocol, handle error closing reasons
                                    }
                                    ReceivedValues(received_requests) => {
                                        handle_requests(received_requests, &connection, &rc_stream,
                                            client_listener.pool.clone(), &write_lock.clone())
                                            .await;
                                    }
                                }
                        }}
                    );
                    } else if let Err(err) = listen_v {
                        close_callback(FailedInitialization(err.into()));
                    }
                }
                _ = notify_close.notified() => {
                    // `notify_one` was called to indicate no more clients are connected,
                    // close the socket
                    close_socket();
                }
                _ = handle_signals() => {
                    // Interrupt was received, close the socket 
                    close_socket();
                }

            }
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
    /// No clients left to handle, close the connection
    AllConnectionsClosed,
}

/// Enum describing babushka errors
pub enum BabushkaError {
    /// Base error
    BaseError(String),
    /// Close error
    CloseError(ClosingReason),
}
/// Get the socket path as a string
pub fn get_socket_path() -> String {
    std::env::temp_dir()
        .join(SOCKET_FILE_NAME)
        .into_os_string()
        .into_string()
        .unwrap()
}

async fn handle_signals() {
    // Handle Unix signals
    let mut signals = Signals::new(&[SIGTERM, SIGQUIT, SIGINT, SIGHUP]).unwrap();
    while let Some(signal) = signals.next().await {
        match signal {
            SIGTERM | SIGQUIT | SIGINT | SIGHUP => {
                // Close the socket
                close_socket();
            }
            _ => unreachable!(),
        }
    }
}

/// Start a thread  
///
/// # Arguments
///
/// * `start_callback` - called when the thread started listening on the socket. This is used to prevent races.
///
/// * `close_callback` - called when the socket listener fails to initialize, with the reason for the failure.
pub fn start_socket_listener<StartCallback, CloseCallback>(
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
                    let close_callback_rc = Rc::new(close_callback);
                    runtime.block_on(listen_on_socket(start_callback, close_callback_rc));
                }

                Err(err) => {
                    close_socket();
                    close_callback(FailedInitialization(err.into()))
                }
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}
