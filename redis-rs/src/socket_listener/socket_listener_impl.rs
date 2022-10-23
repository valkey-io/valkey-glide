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
use std::sync::Arc;
use tokio::sync::Notify;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::{io, thread};
use tokio::io::Interest;
use tokio::net::{UnixListener, UnixStream};
use tokio::runtime::Builder;
use tokio::sync::Mutex;
use tokio::task;
use signal_hook::consts::signal::*;
use signal_hook_tokio::Signals;
use ClosingReason::*;
use PipeListeningResult::*;
use futures::stream::StreamExt;
use tokio::io::ErrorKind::{AddrInUse};
/// The socket file name 
pub const SOCKET_FILE_NAME: &'static str = "babushka-socket";

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
                        Err(err) =>  UnhandledError(err.into()).into()
                    };
                }
                Err(ref e) if e.kind() == io::ErrorKind::WouldBlock => {
                    task::yield_now().await;
                    continue;
                }
                Err(ref e) if e.kind() == io::ErrorKind::Interrupted => {
                    continue;
                }
                Err(err) => return UnhandledError(err.into()).into()
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
    write_socket: Rc<UnixStream>,
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
    //connection: Rc<RefCell<Option<MultiplexedConnection>>>,
    connection: MultiplexedConnection,
    write_socket: Rc<UnixStream>,
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
            RequestRanges::ServerAddress { address } => {}
        }
    });
}

async fn handle_requests(
    received_requests: Vec<WholeRequest>,
    connection: &MultiplexedConnection,
    write_socket: &Rc<UnixStream>,
    pool: Rc<Pool<Vec<u8>>>,
    lock: &Rc<Mutex<()>>
) {
    // TODO - can use pipeline here, if we're fine with the added latency.
    for request in received_requests {
        //let connection = connection.clone();
        let write_socket = write_socket.clone();
        let lock = lock.clone();
        handle_request(request, connection.clone(), write_socket,  pool.clone(), lock)
    }
    // Yield to ensure that the subtasks aren't starved.
    task::yield_now().await;
}

fn close_socket() {
    println!("close_socket() was called");
    let _ = match std::fs::remove_file(get_socket_path()) {
        Ok(()) => { 
            println!("Successfully deleted socket file");
        },
        Err(e) => { println!("Failed to delete socket file: {}", e);
        }
    };
}

async fn wait_for_server_address(
    client_listener: &mut SocketListener, 
    connected_clients: Arc<AtomicUsize>, 
    notifier: Arc<Notify>,
    socket: &Rc<UnixStream>,
    lock: &Rc<Mutex<()>>) -> Result<(MultiplexedConnection, Vec<WholeRequest>), ClosingReason>
    {
    // wait for address
    let listening_result = client_listener.next_values().await;
    match listening_result {
        Closed(reason) => {
            println!("Rust: Closing! {:?}", reason);
            connected_clients.fetch_sub(1, Ordering::SeqCst);
            if connected_clients.load(Ordering::SeqCst) == 0 {
                notifier.notify_one();
            }
            return Err(reason);
        }
        ReceivedValues(received_requests) => {
            for index in 0..received_requests.len() {
                let request = received_requests.get(index).unwrap();
                match request.request_type.clone() {
                    RequestRanges::ServerAddress { address: address_range } => {
                        let address = &request.buffer[address_range];
                        let address = std::str::from_utf8(address).expect("Found invalid UTF-8");
                        println!("Got address: {}", address);
                        let client = Client::open(address).unwrap(); // TODO: better error handling
                        let connection = match client.get_multiplexed_async_connection().await {
                            Ok(socket) => socket,
                            Err(err) => {
                                return Err(FailedInitialization(err));
                            }
                        };
                        let mut output_buffer = [0_u8; HEADER_END];
                        write_response_header(&mut output_buffer, request.callback_index, ResponseType::Null);
                        write_to_output(&output_buffer, &socket, &lock).await;
                        return Ok((connection, received_requests))
                    }
                    _ => {panic!("Got other request before server address");}
                }
            };
        }
    }
    return Err(ClosingReason::AllConnectionsClosed)
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
                println!("Address already in use, exiting this thread to let bind to the exiting connection");
                start_callback();
            } else {
                println!("Got error trying to bind {}",err);
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
                let cloned_close_callback = close_callback.clone();
                tokio::select! {
                    listen_v = listener.accept() => {
                    if let Ok((stream, _addr)) = listen_v {
                        let cloned_close_notifier = notify_close.clone();
                        let cloned_connected_clients = connected_clients.clone();
                        cloned_connected_clients.fetch_add(1, Ordering::SeqCst);
                        println!("Rust: new client!");
                        task::spawn_local(async move {
                            let rc_stream = Rc::new(stream);
                            let lock = Rc::new(Mutex::new(()));
                            let mut client_listener = SocketListener::new(rc_stream.clone());
                            let (connection, _already_received_requests) = 
                                match wait_for_server_address(
                                    &mut client_listener, 
                                    cloned_connected_clients.clone(), 
                                    cloned_close_notifier.clone(),
                                    &rc_stream,
                                    &lock.clone()
                                ).await {
                                    Ok((conn, requests)) => (conn, requests),
                                    Err(err) => {cloned_close_callback(err); return;}
                            };
                            loop {
                                let listening_result = client_listener.next_values().await;
                                match listening_result {
                                    Closed(reason) => {
                                        println!("Rust: Closing! {:?}", reason);
                                        cloned_connected_clients.fetch_sub(1, Ordering::SeqCst);
                                        if cloned_connected_clients.load(Ordering::SeqCst) == 0 {
                                            // No more clients connected, close the socket
                                            cloned_close_notifier.notify_one();
                                            println!("notified");
                                        } else {
                                            cloned_close_callback(reason);
                                        }
                                        return;
                                    }
                                    ReceivedValues(received_requests) => {
                                        handle_requests(received_requests, &connection, &rc_stream, client_listener.pool.clone(), &lock.clone())
                                            .await;
                                    }
                                }
                        }}
                    );
                    } else if let Err(err) = listen_v {
                        println!("closing due to error");
                        cloned_close_callback(FailedInitialization(err.into()));
                    }
                }
                _ = notify_close.notified() => {
                    println!("closing the server as 'closed' message recieved");
                    // `notify_one` was called to indicate no more clients are connected,
                    // close the socket
                    close_socket();
                }
                _ = handle_signals() => {
                    // Interrupt was received, close the socket 
                    println!("closing the server as SIGINT recieved");
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
    AllConnectionsClosed
}

/// Get the socket path as a string
pub fn get_socket_path() -> String {
    return std::env::temp_dir().join(SOCKET_FILE_NAME).into_os_string().into_string().unwrap();
}

async fn handle_signals() {
    let mut signals = Signals::new(&[
        SIGTERM,
        SIGINT,
        SIGQUIT,
    ]).unwrap();
    while let Some(signal) = signals.next().await {
        match signal {
            SIGTERM | SIGINT | SIGQUIT => {
                // Shutdown the system;
                println!("Exiting due to signal interrupt");
                close_socket();
            },
            _ => unreachable!(),
        }
    }
}

/// Start a thread  
///
/// # Arguments
///
/// * `client` - the client from which to create a connection.
///
/// * `start_callback` - called when the thread started listening on the socket. This is used to prevent races.
///
/// * `close_callback` - called when the listener stopped listening, with the reason for stopping.
pub fn start_socket_listener<StartCallback, CloseCallback>(
    client: Client,
    start_callback: StartCallback,
    close_callback: CloseCallback,
) where
    StartCallback: Fn() + Send + 'static,
    CloseCallback: Fn(ClosingReason) + Send + 'static,
{
    // if is_server_up() {
    //     // no need to start the server if it's already running
    //     println!("server is already up");
    //     start_callback();
    //     return
    // }
    
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
                    runtime.block_on(listen_on_socket(
                        start_callback,
                        close_callback_rc.clone(),
                    ));
                }
                
                Err(err) => {
                    close_socket();
                    close_callback(FailedInitialization(err.into()))
                }
            };
        })
        .expect("Thread spawn failed. Cannot report error because callback was moved.");
}
