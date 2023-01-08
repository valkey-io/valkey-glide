use super::super::{AsyncCommands, RedisResult};
use super::{headers::*, rotating_buffer::RotatingBuffer};
use crate::aio::MultiplexedConnection;
use crate::{Client, RedisError};
use bytes::BufMut;
use futures::stream::StreamExt;
use num_traits::ToPrimitive;
use signal_hook::consts::signal::*;
use signal_hook_tokio::Signals;
use std::cell::Cell;
use std::ops::Range;
use std::rc::Rc;
use std::str;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::{io, thread};
use tokio::io::ErrorKind::AddrInUse;
use tokio::net::{UnixListener, UnixStream};
use tokio::runtime::Builder;
use tokio::sync::Mutex;
use tokio::sync::Notify;
use tokio::task;
use ClosingReason::*;
use PipeListeningResult::*;
use std::collections::HashMap;
use affinity::*;
use tokio::time::{sleep, Duration};


/// The socket file name
pub const SOCKET_FILE_NAME: &str = "babushka-socket";

/// struct containing all objects needed to read from a socket.
struct SocketListener {
    read_socket: Rc<UnixStream>,
    rotating_buffer: RotatingBuffer,
}

/// struct containing all objects needed to write to a socket.
struct Writer {
    socket: Rc<UnixStream>,
    lock: Mutex<()>,
    accumulated_outputs: Cell<Vec<u8>>,
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
        let rotating_buffer = RotatingBuffer::new(2, 65_536);
        SocketListener {
            read_socket,
            rotating_buffer,
        }
    }

    pub(crate) async fn next_values(&mut self) -> PipeListeningResult {
        loop {
            self.read_socket
                .readable()
                .await
                .expect("Readable check failed");

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
                Err(ref e)
                    if e.kind() == io::ErrorKind::WouldBlock
                        || e.kind() == io::ErrorKind::Interrupted =>
                {
                    continue;
                }
                Err(err) => return UnhandledError(err.into()).into(),
            }
        }
    }
}

async fn write_to_output(writer: &Rc<Writer>) {
    let Ok(_guard) = writer.lock.try_lock() else {
        return;
    };
    sleep(Duration::from_micros(100)).await; // TODO: fix it
    let mut output = writer.accumulated_outputs.take();
    loop {
        if output.is_empty() {
            return;
        }
        let mut total_written_bytes = 0;
        while total_written_bytes < output.len() {
            writer
                .socket
                .writable()
                .await
                .expect("Writable check failed");
            match writer.socket.try_write(&output[total_written_bytes..]) {
                Ok(written_bytes) => {
                    total_written_bytes += written_bytes;
                }
                Err(err)
                    if err.kind() == io::ErrorKind::WouldBlock
                        || err.kind() == io::ErrorKind::Interrupted =>
                {
                    continue;
                }
                Err(err) => {
                    // TODO - add proper error handling.
                    panic!("received unexpected error {:?}", err);
                }
            }
        }
        output.clear();
        output = writer.accumulated_outputs.replace(output);
    }
}

fn write_response_header(
    accumulated_outputs: &Cell<Vec<u8>>,
    callback_index: u32,
    response_type: ResponseType,
    length: usize,
) -> Result<(), io::Error> {
    let mut vec = accumulated_outputs.take();
    vec.put_u32_le(length as u32);
    vec.put_u32_le(callback_index);
    vec.put_u32_le(response_type.to_u32().ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            format!("Response type {:?} wasn't found", response_type),
        )
    })?);

    assert!(!vec.is_empty());
    accumulated_outputs.set(vec);
    Ok(())
}

fn write_null_response_header(
    accumulated_outputs: &Cell<Vec<u8>>,
    callback_index: u32,
) -> Result<(), io::Error> {
    write_response_header(
        accumulated_outputs,
        callback_index,
        ResponseType::Null,
        HEADER_END,
    )
}

fn write_slice_to_output(accumulated_outputs: &Cell<Vec<u8>>, bytes_to_write: &[u8]) {
    let mut vec = accumulated_outputs.take();
    vec.extend_from_slice(bytes_to_write);
    accumulated_outputs.set(vec);
}

async fn send_set_request(
    buffer: SharedBuffer,
    key_range: Range<usize>,
    value_range: Range<usize>,
    callback_index: u32,
    mut connection: MultiplexedConnection,
    writer: Rc<Writer>,
) -> RedisResult<()> {
    connection
        .set(&buffer[key_range], &buffer[value_range])
        .await?;
    write_null_response_header(&writer.accumulated_outputs, callback_index)?;
    write_to_output(&writer).await;
    Ok(())
}

async fn send_get_request(
    vec: SharedBuffer,
    key_range: Range<usize>,
    callback_index: u32,
    mut connection: MultiplexedConnection,
    writer: Rc<Writer>,
) -> RedisResult<()> {
    let result: Option<Vec<u8>> = connection.get(&vec[key_range]).await?;
    match result {
        Some(result_bytes) => {
            let length = HEADER_END + result_bytes.len();
            write_response_header(
                &writer.accumulated_outputs,
                callback_index,
                ResponseType::String,
                length,
            )?;
            write_slice_to_output(&writer.accumulated_outputs, &result_bytes);
        }
        None => {
            write_null_response_header(&writer.accumulated_outputs, callback_index)?;
        }
    };
    write_to_output(&writer).await;
    Ok(())
}

fn handle_request(request: WholeRequest, connection: MultiplexedConnection, writer: Rc<Writer>) {
    task::spawn_local(async move {
        let result = match request.request_type {
            RequestRanges::Get { key: key_range } => {
                send_get_request(
                    request.buffer,
                    key_range,
                    request.callback_index,
                    connection,
                    writer.clone(),
                )
                .await
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
                    writer.clone(),
                )
                .await
            }
            RequestRanges::ServerAddress { address: _ } => {
                unreachable!("Server address can only be sent once")
            }
        };
        if let Err(err) = result {
            write_error(
                err,
                request.callback_index,
                writer,
                ResponseType::RequestError,
            )
            .await;
        }
    });
}

async fn write_error(
    err: RedisError,
    callback_index: u32,
    writer: Rc<Writer>,
    response_type: ResponseType,
) {
    let err_str = err.to_string();
    let error_bytes = err_str.as_bytes();
    let length = HEADER_END + error_bytes.len();
    write_response_header(
        &writer.accumulated_outputs,
        callback_index,
        response_type,
        length,
    )
    .expect("Failed writing error to vec");
    write_slice_to_output(&writer.accumulated_outputs, error_bytes);
    write_to_output(&writer).await;
}

async fn handle_requests(
    received_requests: Vec<WholeRequest>,
    connection: &MultiplexedConnection,
    writer: &Rc<Writer>,
) {
    // TODO - can use pipeline here, if we're fine with the added latency.
    for request in received_requests {
        handle_request(request, connection.clone(), writer.clone())
    }
    // Yield to ensure that the subtasks aren't starved.
    task::yield_now().await;
}

fn close_socket() {
    std::fs::remove_file(get_socket_path()).expect("Failed to delete socket file");
}

fn to_babushka_result<T, E: std::fmt::Display>(
    result: Result<T, E>,
    err_msg: Option<&str>,
) -> Result<T, BabushkaError> {
    result.map_err(|err: E| {
        BabushkaError::BaseError(match err_msg {
            Some(msg) => format!("{}: {}", msg, err),
            None => format!("{}", err),
        })
    })
}

async fn parse_address_create_conn(
    writer: &Rc<Writer>,
    request: &WholeRequest,
    address_range: Range<usize>,
) -> Result<MultiplexedConnection, BabushkaError> {
    let address = &request.buffer[address_range];
    let address = to_babushka_result(
        std::str::from_utf8(address),
        Some("Failed to parse address"),
    )?;
    let client = to_babushka_result(
        Client::open(address),
        Some("Failed to open redis-rs client"),
    )?;
    let connection = to_babushka_result(
        client.get_multiplexed_async_connection().await,
        Some("Failed to create a multiplexed connection"),
    )?;

    // Send response
    write_null_response_header(&writer.accumulated_outputs, request.callback_index)
        .expect("Failed writing address response.");
    write_to_output(writer).await;
    Ok(connection)
}

async fn wait_for_server_address_create_conn(
    client_listener: &mut SocketListener,
    writer: &Rc<Writer>,
) -> Result<MultiplexedConnection, BabushkaError> {
    // Wait for the server's address
    match client_listener.next_values().await {
        Closed(reason) => {
            return Err(BabushkaError::CloseError(reason));
        }
        ReceivedValues(received_requests) => {
            if let Some(index) = (0..received_requests.len()).next() {
                let request = received_requests
                    .get(index)
                    .ok_or_else(|| BabushkaError::BaseError("No received requests".to_string()))?;
                match request.request_type.clone() {
                    RequestRanges::ServerAddress {
                        address: address_range,
                    } => return parse_address_create_conn(writer, request, address_range).await,
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
    if connected_clients.fetch_sub(1, Ordering::Relaxed) == 1 {
        // No more clients connected, close the socket
        close_notifier.notify_one();
    }
}

async fn listen_on_client_stream(
    socket: UnixStream,
    notify_close: Arc<Notify>,
    connected_clients: Arc<AtomicUsize>,
) {
    let socket = Rc::new(socket);
    // Spawn a new task to listen on this client's stream
    let write_lock = Mutex::new(());
    let mut client_listener = SocketListener::new(socket.clone());
    let accumulated_outputs = Cell::new(Vec::new());
    let writer = Rc::new(Writer {
        socket,
        lock: write_lock,
        accumulated_outputs,
    });
    let connection = match wait_for_server_address_create_conn(&mut client_listener, &writer).await
    {
        Ok(conn) => conn,
        Err(BabushkaError::CloseError(_reason)) => {
            update_notify_connected_clients(connected_clients, notify_close);
            return; // TODO: implement error protocol, handle closing reasons different from ReadSocketClosed
        }
        Err(BabushkaError::BaseError(err)) => {
            println!("Recieved error: {:?}", err); // TODO: implement error protocol
            return;
        }
    };
    loop {
        match client_listener.next_values().await {
            Closed(reason) => {
                if let ClosingReason::UnhandledError(err) = reason {
                    write_error(err, u32::MAX, writer.clone(), ResponseType::ClosingError).await;
                };
                update_notify_connected_clients(connected_clients, notify_close);
                return; // TODO: implement error protocol, handle error closing reasons
            }
            ReceivedValues(received_requests) => {
                handle_requests(received_requests, &connection, &writer).await;
            }
        }
    }
}

async fn listen_on_socket<InitCallback>(init_callback: InitCallback)
where
    InitCallback: FnOnce(Result<String, RedisError>) + Send + 'static,
{
    // Bind to socket
    let listener = match UnixListener::bind(get_socket_path()) {
        Ok(listener) => listener,
        Err(err) if err.kind() == AddrInUse => {
            init_callback(Ok(get_socket_path()));
            return;
        }
        Err(err) => {
            init_callback(Err(err.into()));
            return;
        }
    };
    let local = task::LocalSet::new();
    let connected_clients = Arc::new(AtomicUsize::new(0));
    let notify_close = Arc::new(Notify::new());
    init_callback(Ok(get_socket_path()));
    local.run_until(async move {
        loop {
            tokio::select! {
                listen_v = listener.accept() => {
                    if let Ok((stream, _addr)) = listen_v {
                        // New client
                        let cloned_close_notifier = notify_close.clone();
                        let cloned_connected_clients = connected_clients.clone();
                        cloned_connected_clients.fetch_add(1, Ordering::Relaxed);
                        task::spawn_local(listen_on_client_stream(stream, cloned_close_notifier.clone(), cloned_connected_clients));
                    } else if listen_v.is_err() {
                        close_socket();
                        return;
                    }
                },
                // `notify_one` was called to indicate no more clients are connected,
                // close the socket
                _ = notify_close.notified() => {close_socket(); return;},
                // Interrupt was received, close the socket
                _ = handle_signals() => {close_socket(); return;}
            }
        };
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
fn get_socket_path() -> String {
    let socket_name = format!("{}-{}", SOCKET_FILE_NAME, std::process::id());
    std::env::temp_dir()
        .join(socket_name)
        .into_os_string()
        .into_string()
        .expect("Couldn't create socket path")
}

async fn handle_signals() {
    // Handle Unix signals
    let mut signals =
        Signals::new([SIGTERM, SIGQUIT, SIGINT, SIGHUP]).expect("Failed creating signals");
    while let Some(signal) = signals.next().await {
        match signal {
            SIGTERM | SIGQUIT | SIGINT | SIGHUP => {
                // Close the socket
                close_socket();
            }
            sig => unreachable!("Received an unregistered signal `{}`", sig),
        }
    }
}

/// Creates a new thread with a main loop task listening on the socket for new connections.
/// Every new connection will be assigned with a client-listener task to handle their requests.
///
/// # Arguments
/// * `init_callback` - called when the socket listener fails to initialize, with the reason for the failure.
pub fn start_socket_listener<InitCallback>(init_callback: InitCallback)
where
    InitCallback: FnOnce(Result<String, RedisError>) + Send + 'static,
{
    thread::Builder::new()
        .name("socket_listener_thread".to_string())
        .spawn(move || {
            set_thread_affinity(&[3]).unwrap();
            let runtime = Builder::new_current_thread()
                .enable_all()
                .thread_name("socket_listener_thread")
                .build();
            match runtime {
                Ok(runtime) => {
                    runtime.block_on(listen_on_socket(init_callback));
                }
                Err(err) => {
                    close_socket();
                    init_callback(Err(err.into()))
                }
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}
