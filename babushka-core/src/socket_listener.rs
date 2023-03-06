use super::{headers::*, rotating_buffer::RotatingBuffer};
use dispose::{Disposable, Dispose};
use futures::stream::StreamExt;
use logger_core::{log_error, log_info, log_trace};
use num_traits::FromPrimitive;
use pb_message::{Request, Response};
use protobuf::Message;
use redis::aio::MultiplexedConnection;
use redis::{AsyncCommands, ErrorKind, RedisResult, Value};
use redis::{Client, RedisError};
use signal_hook::consts::signal::*;
use signal_hook_tokio::Signals;
use std::cell::Cell;
use std::rc::Rc;
use std::{fmt, str};
use std::{io, thread};
use tokio::io::ErrorKind::AddrInUse;
use tokio::net::{UnixListener, UnixStream};
use tokio::runtime::Builder;
use tokio::sync::mpsc::{channel, Sender};
use tokio::sync::Mutex;
use tokio::task;
use tokio_retry::strategy::{jitter, ExponentialBackoff, FixedInterval};
use tokio_retry::Retry;
use tokio_util::task::LocalPoolHandle;
use ClosingReason::*;
use PipeListeningResult::*;

/// The socket file name
const SOCKET_FILE_NAME: &str = "babushka-socket";

/// Result type for responses
type ResponseResult = Result<Value, Box<dyn std::error::Error>>;

/// struct containing all objects needed to bind to a socket and clean it.
struct SocketListener {
    socket_path: String,
    cleanup_socket: bool,
}

impl Dispose for SocketListener {
    fn dispose(self) {
        if self.cleanup_socket {
            close_socket(&self.socket_path);
        }
    }
}

/// struct containing all objects needed to read from a unix stream.
struct UnixStreamListener {
    read_socket: Rc<UnixStream>,
    rotating_buffer: RotatingBuffer,
}

/// struct containing all objects needed to write to a socket.
struct Writer {
    socket: Rc<UnixStream>,
    lock: Mutex<()>,
    accumulated_outputs: Cell<Vec<u8>>,
    closing_sender: Sender<ClosingReason>,
}

enum PipeListeningResult {
    Closed(ClosingReason),
    ReceivedValues(Vec<Request>),
}

impl From<ClosingReason> for PipeListeningResult {
    fn from(result: ClosingReason) -> Self {
        Closed(result)
    }
}

impl UnixStreamListener {
    fn new(read_socket: Rc<UnixStream>) -> Self {
        // if the logger has been initialized by the user (external or internal) on info level this log will be shown
        log_info("connection", "new socket listener initiated");
        let rotating_buffer = RotatingBuffer::new(2, 65_536);
        Self {
            read_socket,
            rotating_buffer,
        }
    }

    pub(crate) async fn next_values(&mut self) -> PipeListeningResult {
        loop {
            if let Err(err) = self.read_socket.readable().await {
                return ClosingReason::UnhandledError(err.into()).into();
            }

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

    let mut output = writer.accumulated_outputs.take();
    loop {
        if output.is_empty() {
            return;
        }
        let mut total_written_bytes = 0;
        while total_written_bytes < output.len() {
            if let Err(err) = writer.socket.writable().await {
                let _res = writer.closing_sender.send(err.into()).await; // we ignore the error, because it means that the reader was dropped, which is ok.
                return;
            }
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
                    let _res = writer.closing_sender.send(err.into()).await; // we ignore the error, because it means that the reader was dropped, which is ok.
                }
            }
        }
        output.clear();
        output = writer.accumulated_outputs.replace(output);
    }
}

async fn send_set_request(
    request: &Request,
    mut connection: MultiplexedConnection,
    writer: Rc<Writer>,
) -> RedisResult<()> {
    assert_eq!(request.request_type, RequestType::SetString as u32);
    let args = &request.args;
    assert_eq!(args.len(), 2); // TODO: delete it in the chunks implementation
    let result: RedisResult<Value> = connection.set(&args[0], &args[1]).await;
    if result.is_ok() {
        write_response(Ok(Value::Nil), request.callback_idx, &writer).await?; // TODO: remove this after we change SET response to OK instead of null
    } else {
        write_response(to_response_result(result), request.callback_idx, &writer).await?;
    }
    Ok(())
}

/// Create response and write it to the writer
async fn write_response(
    resp_result: ResponseResult,
    callback_index: u32,
    writer: &Rc<Writer>,
) -> Result<(), io::Error> {
    let mut response = Response::new();
    response.callback_idx = callback_index;
    match resp_result {
        Ok(value) => {
            if value != Value::Nil {
                // Since null values don't require any additional data, they can be sent without any extra effort.
                // Move the value to the heap and leak it. The wrapper should use `Box::from_raw` to recreate the box, use the value, and drop the allocation.
                let pointer = Box::leak(Box::new(value));
                let raw_pointer = pointer as *mut redis::Value;
                response.value = Some(pb_message::response::Value::RespPointer(raw_pointer as u64))
            }
        }
        Err(err) => {
            log_error("response error", err.to_string());
            if err.is::<ClosingError>() {
                response.value = Some(pb_message::response::Value::ClosingError(err.to_string()))
            } else {
                response.value = Some(pb_message::response::Value::RequestError(err.to_string()))
            }
        }
    }

    let mut vec = writer.accumulated_outputs.take();
    let encode_result = response.write_length_delimited_to_vec(&mut vec);

    // Write the response' length to the buffer
    match encode_result {
        Ok(_) => {
            writer.accumulated_outputs.set(vec);
            write_to_output(writer).await;
            Ok(())
        }
        Err(err) => {
            let err_message = format!("failed to encode response: {err}");
            log_error("response error", err_message.clone());
            Err(std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                err_message,
            ))
        }
    }
}

async fn send_get_request(
    request: &Request,
    mut connection: MultiplexedConnection,
    writer: Rc<Writer>,
) -> RedisResult<()> {
    assert_eq!(request.request_type, RequestType::GetString as u32);
    assert_eq!(request.args.len(), 1); // TODO: delete it in the chunks implementation
    let result: RedisResult<Value> = connection.get(request.args.first().unwrap()).await;
    write_response(to_response_result(result), request.callback_idx, &writer).await?;
    Ok(())
}

fn handle_request(request: Request, connection: MultiplexedConnection, writer: Rc<Writer>) {
    task::spawn_local(async move {
        let request_type =
            FromPrimitive::from_u32(request.request_type).unwrap_or(RequestType::InvalidRequest);
        let result = match request_type {
            RequestType::GetString => send_get_request(&request, connection, writer.clone()).await,
            RequestType::SetString => send_set_request(&request, connection, writer.clone()).await,
            RequestType::ServerAddress => Err(RedisError::from((
                ErrorKind::ClientError,
                "Server address can only be sent once",
            ))),
            _ => {
                let err_message =
                    format!("Recieved invalid request type: {}", request.request_type);
                let _res = write_response(
                    Err(ClosingError { err: err_message }.into()),
                    request.callback_idx,
                    &writer,
                )
                .await;
                return;
            }
        };
        if let Err(err) = result {
            let _res = write_response(Err(err.into()), request.callback_idx, &writer).await;
        }
    });
}

async fn handle_requests(
    received_requests: Vec<Request>,
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

pub fn close_socket(socket_path: &String) {
    log_info("close_socket", format!("closing socket at {socket_path}"));
    let _ = std::fs::remove_file(socket_path);
}

fn to_babushka_result<T, E: std::fmt::Display>(
    result: Result<T, E>,
    err_msg: Option<&str>,
) -> Result<T, ClientCreationError> {
    result.map_err(|err: E| {
        ClientCreationError::UnhandledError(match err_msg {
            Some(msg) => format!("{msg}: {err}"),
            None => format!("{err}"),
        })
    })
}

// TODO: refactor the response code with more careful use of the type system
fn to_response_result<E>(res: Result<Value, E>) -> ResponseResult
where
    E: std::error::Error + 'static,
{
    res.map_err(|err: E| -> Box<_> { err.into() })
}

async fn parse_address_create_conn(
    writer: &Rc<Writer>,
    request: &Request,
) -> Result<MultiplexedConnection, ClientCreationError> {
    let address = request.args.first().unwrap();
    // TODO - should be a configuration sent over the socket.
    const BASE: u64 = 10;
    const FACTOR: u64 = 5;
    const NUMBER_OF_RETRIES: usize = 3;
    let retry_strategy = ExponentialBackoff::from_millis(BASE)
        .factor(FACTOR)
        .map(jitter) // tokio-retry doesn't support additive jitter.
        .take(NUMBER_OF_RETRIES);

    let action = || async move {
        let client = to_babushka_result(
            Client::open(address.as_str()),
            Some("Failed to open redis-rs client"),
        )?;
        to_babushka_result(
            client.get_multiplexed_async_connection().await,
            Some("Failed to create a multiplexed connection"),
        )
    };

    let connection = Retry::spawn(retry_strategy, action).await?;

    // Send response
    write_response(Ok(Value::Nil), request.callback_idx, writer)
        .await
        .map_err(|err| ClientCreationError::UnhandledError(err.to_string()))?;
    log_trace(
        "client creation",
        format!("Connection to {address} created"),
    );
    Ok(connection)
}

async fn wait_for_server_address_create_conn(
    client_listener: &mut UnixStreamListener,
    writer: &Rc<Writer>,
) -> Result<MultiplexedConnection, ClientCreationError> {
    // Wait for the server's address
    match client_listener.next_values().await {
        Closed(reason) => Err(ClientCreationError::SocketListenerClosed(reason)),
        ReceivedValues(received_requests) => {
            if let Some(request) = received_requests.first() {
                match FromPrimitive::from_u32(request.request_type).unwrap() {
                    RequestType::ServerAddress => parse_address_create_conn(writer, request).await,
                    _ => Err(ClientCreationError::UnhandledError(
                        "Received another request before receiving server address".to_string(),
                    )),
                }
            } else {
                Err(ClientCreationError::UnhandledError(
                    "No received requests".to_string(),
                ))
            }
        }
    }
}

async fn read_values_loop(
    mut client_listener: UnixStreamListener,
    connection: MultiplexedConnection,
    writer: Rc<Writer>,
) -> ClosingReason {
    loop {
        match client_listener.next_values().await {
            Closed(reason) => {
                return reason;
            }
            ReceivedValues(received_requests) => {
                handle_requests(received_requests, &connection, &writer).await;
            }
        }
    }
}

async fn listen_on_client_stream(socket: UnixStream) {
    let socket = Rc::new(socket);
    // Spawn a new task to listen on this client's stream
    let write_lock = Mutex::new(());
    let mut client_listener = UnixStreamListener::new(socket.clone());
    let accumulated_outputs = Cell::new(Vec::new());
    let (sender, mut receiver) = channel(1);
    let writer = Rc::new(Writer {
        socket,
        lock: write_lock,
        accumulated_outputs,
        closing_sender: sender,
    });
    let connection = match wait_for_server_address_create_conn(&mut client_listener, &writer).await
    {
        Ok(conn) => conn,
        Err(ClientCreationError::SocketListenerClosed(ClosingReason::ReadSocketClosed)) => {
            // This isn't an error - it can happen when a new wrapper-client creates a connection in order to check whether something already listens on the socket.
            log_trace(
                "client creation",
                "read socket closed before client was created.",
            );
            return;
        }
        Err(ClientCreationError::SocketListenerClosed(reason)) => {
            let error_message = format!("Socket listener closed due to {reason:?}");
            let _res = write_response(
                Err(ClosingError {
                    err: error_message.clone(),
                }
                .into()),
                u32::MAX,
                &writer,
            )
            .await;
            return;
        }
        Err(ClientCreationError::UnhandledError(err)) => {
            let _res = write_response(
                Err(ClosingError { err: err.clone() }.into()),
                u32::MAX,
                &writer,
            )
            .await;
            return;
        }
    };
    tokio::select! {
            reader_closing = read_values_loop(client_listener, connection, writer.clone()) => {
                if let ClosingReason::UnhandledError(err) = reader_closing {
                    let err = ClosingError{err: err.to_string()}.into();
                    let _res = write_response(Err(err), u32::MAX, &writer).await;
                };
                log_trace("client closing", "reader closed");
            },
            writer_closing = receiver.recv() => {
                if let Some(ClosingReason::UnhandledError(err)) = writer_closing {
                    log_error("client closing", format!("Writer closed with error: {err}"));
                } else {
                    log_trace("client closing", "writer closed");
                }
            }
    }
    log_trace("client closing", "closing connection");
}

enum SocketCreationResult {
    // Socket creation was successful, returned a socket listener.
    Created(UnixListener),
    // There's an existing a socket listener.
    PreExisting,
    // Socket creation failed with an error.
    Err(io::Error),
}

impl SocketListener {
    fn new(socket_path: String) -> Self {
        SocketListener {
            socket_path,
            // Don't cleanup the socket resources unless we know that the socket is in use, and owned by this listener.
            cleanup_socket: false,
        }
    }

    /// Return true if it's possible to connect to socket.
    async fn socket_is_available(&self) -> bool {
        if UnixStream::connect(&self.socket_path).await.is_ok() {
            return true;
        }
        const NUMBER_OF_RETRIES: usize = 3;
        const SLEEP_DURATION_IN_MILLISECONDS: u64 = 10;
        let retry_strategy = FixedInterval::from_millis(SLEEP_DURATION_IN_MILLISECONDS)
            .map(jitter) // tokio-retry doesn't support additive jitter.
            .take(NUMBER_OF_RETRIES);

        let action = || async {
            UnixStream::connect(&self.socket_path)
                .await
                .map(|_| ())
                .map_err(|_| ())
        };
        let result = Retry::spawn(retry_strategy, action).await;
        result.is_ok()
    }

    async fn get_socket_listener(&self) -> SocketCreationResult {
        const RETRY_COUNT: u8 = 3;
        let mut retries = RETRY_COUNT;
        while retries > 0 {
            match UnixListener::bind(self.socket_path.clone()) {
                Ok(listener) => {
                    return SocketCreationResult::Created(listener);
                }
                Err(err) if err.kind() == AddrInUse => {
                    if self.socket_is_available().await {
                        return SocketCreationResult::PreExisting;
                    } else {
                        // socket file might still exist, even if nothing is listening on it.
                        close_socket(&self.socket_path);
                        retries -= 1;
                        continue;
                    }
                }
                Err(err) => {
                    return SocketCreationResult::Err(err);
                }
            }
        }
        SocketCreationResult::Err(io::Error::new(
            io::ErrorKind::Other,
            "Failed to connect to socket",
        ))
    }

    pub(crate) async fn listen_on_socket<InitCallback>(&mut self, init_callback: InitCallback)
    where
        InitCallback: FnOnce(Result<String, String>) + Send + 'static,
    {
        // Bind to socket
        let listener = match self.get_socket_listener().await {
            SocketCreationResult::Created(listener) => listener,
            SocketCreationResult::Err(err) => {
                log_info("listen_on_socket", format!("failed with error: {err}"));
                init_callback(Err(err.to_string()));
                return;
            }
            SocketCreationResult::PreExisting => {
                init_callback(Ok(self.socket_path.clone()));
                return;
            }
        };

        self.cleanup_socket = true;
        init_callback(Ok(self.socket_path.clone()));
        let local_set_pool = LocalPoolHandle::new(num_cpus::get());
        loop {
            tokio::select! {
                listen_v = listener.accept() => {
                    if let Ok((stream, _addr)) = listen_v {
                        // New client
                        local_set_pool.spawn_pinned(move || {
                            listen_on_client_stream(stream)
                        });
                    } else if listen_v.is_err() {
                        return
                    }
                },
                // Interrupt was received, close the socket
                _ = handle_signals() => return
            }
        }
    }
}

#[derive(Debug)]
/// Enum describing the reason that a socket listener stopped listening on a socket.
pub enum ClosingReason {
    /// The socket was closed. This is the expected way that the listener should be closed.
    ReadSocketClosed,
    /// The listener encounter an error it couldn't handle.
    UnhandledError(RedisError),
}

impl From<io::Error> for ClosingReason {
    fn from(error: io::Error) -> Self {
        UnhandledError(error.into())
    }
}

/// Enum describing errors received during client creation.
pub enum ClientCreationError {
    /// An error was returned during the client creation process.
    UnhandledError(String),
    /// Socket listener was closed before receiving the server address.
    SocketListenerClosed(ClosingReason),
}

/// Defines errors caused the connection to close.
#[derive(Debug, Clone)]
struct ClosingError {
    /// A string describing the closing reason
    err: String,
}

impl fmt::Display for ClosingError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}", self.err)
    }
}

impl std::error::Error for ClosingError {}

pub fn get_socket_path_from_name(socket_name: String) -> String {
    std::env::temp_dir()
        .join(socket_name)
        .into_os_string()
        .into_string()
        .expect("Couldn't create socket path")
}

/// Get the socket path as a string
pub fn get_socket_path() -> String {
    let socket_name = format!("{}-{}", SOCKET_FILE_NAME, std::process::id());
    get_socket_path_from_name(socket_name)
}

async fn handle_signals() {
    // Handle Unix signals
    let mut signals =
        Signals::new([SIGTERM, SIGQUIT, SIGINT, SIGHUP]).expect("Failed creating signals");
    loop {
        if let Some(signal) = signals.next().await {
            match signal {
                SIGTERM | SIGQUIT | SIGINT | SIGHUP => {
                    log_info("connection", format!("Signal {signal:?} received"));
                    return;
                }
                _ => continue,
            }
        }
    }
}

/// This function is exposed only for the sake of testing with a nonstandard `socket_path`.
/// Avoid using this function, unless you explicitly want to test the behavior of the listener
/// without using the sockets used by other tests.
pub fn start_socket_listener_internal<InitCallback>(
    init_callback: InitCallback,
    socket_path: Option<String>,
) where
    InitCallback: FnOnce(Result<String, String>) + Send + 'static,
{
    thread::Builder::new()
        .name("socket_listener_thread".to_string())
        .spawn(move || {
            let runtime = Builder::new_current_thread().enable_all().build();
            match runtime {
                Ok(runtime) => {
                    let mut listener = Disposable::new(SocketListener::new(
                        socket_path.unwrap_or_else(get_socket_path),
                    ));
                    runtime.block_on(listener.listen_on_socket(init_callback));
                }
                Err(err) => init_callback(Err(err.to_string())),
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}

/// Creates a new thread with a main loop task listening on the socket for new connections.
/// Every new connection will be assigned with a client-listener task to handle their requests.
///
/// # Arguments
/// * `init_callback` - called when the socket listener fails to initialize, with the reason for the failure.
pub fn start_socket_listener<InitCallback>(init_callback: InitCallback)
where
    InitCallback: FnOnce(Result<String, String>) + Send + 'static,
{
    start_socket_listener_internal(init_callback, None);
}
