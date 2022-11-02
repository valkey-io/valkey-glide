#![cfg(feature = "tokio-comp")]
mod support;
use crate::support::*;
use byteorder::{LittleEndian, ReadBytesExt, WriteBytesExt};
use ntest::timeout;
use num_traits::{FromPrimitive, ToPrimitive};
use rand::{distributions::Standard, thread_rng, Rng};
use redis::socket_listener::headers::{
    RequestType, ResponseType, CALLBACK_INDEX_END, HEADER_END, MESSAGE_LENGTH_END,
    MESSAGE_LENGTH_FIELD_LENGTH, TYPE_END,
};
use redis::socket_listener::*;
use rsevents::{Awaitable, EventState, ManualResetEvent};
use std::io::prelude::*;
use std::sync::{Arc, Mutex};
use std::{os::unix::net::UnixStream, thread};

struct TestBasics {
    _server: RedisServer,
    socket: UnixStream,
}

fn send_address(address: String, socket: &UnixStream) {
    // Send the server address
    const CALLBACK_INDEX: u32 = 1;
    let address = format!("redis://{}", address);
    let message_length = address.len() + HEADER_END;
    let mut buffer = Vec::with_capacity(message_length);
    buffer
        .write_u32::<LittleEndian>(message_length as u32)
        .unwrap();
    buffer
        .write_u32::<LittleEndian>(CALLBACK_INDEX as u32)
        .unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::ServerAddress.to_u32().unwrap())
        .unwrap();
    buffer.write_all(address.as_bytes()).unwrap();
    let mut socket = socket.try_clone().unwrap();
    socket.write_all(&buffer).unwrap();
    let size = socket.read(&mut buffer).unwrap();
    assert_eq!(size, HEADER_END);
    assert_eq!(
        (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        HEADER_END as u32
    );
    assert_eq!(
        (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        CALLBACK_INDEX
    );
    assert_eq!(
        (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        ResponseType::Null.to_u32().unwrap()
    );
}

fn setup_test_basics() -> TestBasics {
    let socket_listener_state: Arc<ManualResetEvent> =
        Arc::new(ManualResetEvent::new(EventState::Unset));
    let context = TestContext::new();
    let cloned_state = socket_listener_state.clone();
    let path_arc = Arc::new(std::sync::Mutex::new(None));
    let path_arc_clone = Arc::clone(&path_arc);
    start_socket_listener(move |res| {
        let path: String = res.expect("Failed to initialize the socket listener");
        let mut path_arc_clone = path_arc_clone.lock().unwrap();
        *path_arc_clone = Some(path);
        cloned_state.set();
    });
    socket_listener_state.wait();
    let path = path_arc.lock().unwrap();
    let path = path.as_ref().expect("Didn't get any socket path");
    let socket = std::os::unix::net::UnixStream::connect(path).unwrap();
    let address = context.server.get_client_addr().to_string();
    send_address(address, &socket);
    TestBasics {
        _server: context.server,
        socket,
    }
}

fn generate_random_bytes(length: usize) -> Vec<u8> {
    thread_rng()
        .sample_iter::<u8, Standard>(Standard)
        .take(length)
        .map(u8::from)
        .collect()
}

/// Check if the passed server type is TLS or UNIX
pub fn is_tls_or_unix() -> bool {
    match std::env::var("REDISRS_SERVER_TYPE") {
        Ok(env) => env.eq_ignore_ascii_case("tcp+tls") || env.eq_ignore_ascii_case("unix"),
        Err(_) => false,
    }
}

#[test]
fn test_socket_set_and_get() {
    if is_tls_or_unix() {
        // TODO: delete after we'll support passing configurations to socket
        return;
    }
    let mut test_basics = setup_test_basics();

    const CALLBACK1_INDEX: u32 = 100;
    const CALLBACK2_INDEX: u32 = 101;
    const VALUE_LENGTH: usize = 10;
    let key = "hello";
    let value = generate_random_bytes(VALUE_LENGTH);
    // Send a set request
    let message_length = VALUE_LENGTH + key.len() + HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;
    let mut buffer = Vec::with_capacity(message_length);
    buffer
        .write_u32::<LittleEndian>(message_length as u32)
        .unwrap();
    buffer.write_u32::<LittleEndian>(CALLBACK1_INDEX).unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::SetString.to_u32().unwrap())
        .unwrap();
    buffer.write_u32::<LittleEndian>(5).unwrap();
    buffer.write_all(key.as_bytes()).unwrap();
    buffer.write_all(&value).unwrap();
    test_basics.socket.write_all(&buffer).unwrap();

    let size = test_basics.socket.read(&mut buffer).unwrap();
    assert_eq!(size, HEADER_END);
    assert_eq!(
        (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        HEADER_END as u32
    );
    assert_eq!(
        (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        CALLBACK1_INDEX
    );
    assert_eq!(
        (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        ResponseType::Null.to_u32().unwrap()
    );

    buffer.clear();
    buffer
        .write_u32::<LittleEndian>((HEADER_END + key.len()) as u32)
        .unwrap();
    buffer.write_u32::<LittleEndian>(CALLBACK2_INDEX).unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::GetString.to_u32().unwrap())
        .unwrap();
    buffer.write_all(key.as_bytes()).unwrap();
    test_basics.socket.write_all(&buffer).unwrap();

    let expected_length = VALUE_LENGTH + HEADER_END;
    let expected_aligned_length = expected_length + (4 - expected_length % 4);
    // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
    unsafe { buffer.set_len(message_length) };
    let size = test_basics.socket.read(&mut buffer).unwrap();
    assert_eq!(size, expected_aligned_length);
    assert_eq!(
        (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        (expected_length) as u32
    );
    assert_eq!(
        (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        CALLBACK2_INDEX
    );
    assert_eq!(
        (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        ResponseType::String.to_u32().unwrap()
    );
    assert_eq!(&buffer[HEADER_END..VALUE_LENGTH + HEADER_END], value);
}

#[test]
fn test_socket_get_returns_null() {
    if is_tls_or_unix() {
        // TODO: delete after we'll support passing configurations to socket
        return;
    }
    const CALLBACK_INDEX: u32 = 99;
    let mut test_basics = setup_test_basics();
    let key = "hello";
    let mut buffer = Vec::with_capacity(HEADER_END);
    buffer.write_u32::<LittleEndian>(17_u32).unwrap();
    buffer.write_u32::<LittleEndian>(CALLBACK_INDEX).unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::GetString.to_u32().unwrap())
        .unwrap();
    buffer.write_all(key.as_bytes()).unwrap();
    test_basics.socket.write_all(&buffer).unwrap();

    let size = test_basics.socket.read(&mut buffer).unwrap();
    assert_eq!(size, HEADER_END);
    assert_eq!(
        (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        HEADER_END as u32
    );
    assert_eq!(
        (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        CALLBACK_INDEX
    );
    assert_eq!(
        (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        ResponseType::Null.to_u32().unwrap()
    );
}

#[test]
fn test_socket_report_error() {
    if is_tls_or_unix() {
        // TODO: delete after we'll support passing configurations to socket
        return;
    }
    let mut test_basics = setup_test_basics();

    let key = "a";
    let value = generate_random_bytes(1);
    // Send a set request
    let message_length = 1 + key.len() + 16;
    let mut buffer = Vec::with_capacity(message_length);
    buffer
        .write_u32::<LittleEndian>(message_length as u32)
        .unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::GetString.to_u32().unwrap())
        .unwrap();
    buffer.write_u32::<LittleEndian>(u32::MAX).unwrap(); // here we send an erroneous enum
    buffer.write_u32::<LittleEndian>(5).unwrap();
    buffer.write_all(key.as_bytes()).unwrap();
    buffer.write_all(&value).unwrap();
    test_basics.socket.write_all(&buffer).unwrap();
}

#[test]
fn test_socket_handle_long_input() {
    if is_tls_or_unix() {
        // TODO: delete after we'll support passing configurations to socket
        return;
    }
    let mut test_basics = setup_test_basics();

    const CALLBACK1_INDEX: u32 = 100;
    const CALLBACK2_INDEX: u32 = 101;
    const VALUE_LENGTH: usize = 1000000;
    let key = "hello";
    let value = generate_random_bytes(VALUE_LENGTH);
    // Send a set request
    let message_length = VALUE_LENGTH + key.len() + HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;
    let mut buffer = Vec::with_capacity(message_length);
    buffer
        .write_u32::<LittleEndian>(message_length as u32)
        .unwrap();
    buffer.write_u32::<LittleEndian>(CALLBACK1_INDEX).unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::SetString.to_u32().unwrap())
        .unwrap();
    buffer.write_u32::<LittleEndian>(5).unwrap();
    buffer.write_all(key.as_bytes()).unwrap();
    buffer.write_all(&value).unwrap();
    test_basics.socket.write_all(&buffer).unwrap();

    let size = test_basics.socket.read(&mut buffer).unwrap();
    assert_eq!(size, HEADER_END);
    assert_eq!(
        (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        HEADER_END as u32
    );
    assert_eq!(
        (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        CALLBACK1_INDEX
    );
    assert_eq!(
        (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        ResponseType::Null.to_u32().unwrap()
    );

    buffer.clear();
    buffer
        .write_u32::<LittleEndian>((HEADER_END + key.len()) as u32)
        .unwrap();
    buffer.write_u32::<LittleEndian>(CALLBACK2_INDEX).unwrap();
    buffer
        .write_u32::<LittleEndian>(RequestType::GetString.to_u32().unwrap())
        .unwrap();
    buffer.write_all(key.as_bytes()).unwrap();
    test_basics.socket.write_all(&buffer).unwrap();

    let expected_length = VALUE_LENGTH + HEADER_END;
    // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
    unsafe { buffer.set_len(message_length) };
    let mut size = 0;
    while size < expected_length {
        let next_read = test_basics.socket.read(&mut buffer[size..]).unwrap();
        assert_ne!(0, next_read);
        size += next_read;
    }
    assert_eq!(size, expected_length);
    assert_eq!(
        (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        (expected_length) as u32
    );
    assert_eq!(
        (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        CALLBACK2_INDEX
    );
    assert_eq!(
        (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap(),
        ResponseType::String.to_u32().unwrap()
    );
    assert_eq!(&buffer[HEADER_END..VALUE_LENGTH + HEADER_END], value);
}

// This test starts multiple threads writing large inputs to a socket, and another thread that reads from the output socket and
// verifies that the outputs match the inputs.
#[test]
#[timeout(10000)]
fn test_socket_handle_multiple_long_inputs() {
    if is_tls_or_unix() {
        // TODO: delete after we'll support passing configurations to socket
        return;
    }
    #[derive(Clone, PartialEq, Eq, Debug)]
    enum State {
        Initial,
        ReceivedNull,
        ReceivedValue,
    }
    let test_basics = setup_test_basics();
    const VALUE_LENGTH: usize = 1000000;
    const NUMBER_OF_THREADS: usize = 10;
    let values = Arc::new(Mutex::new(vec![Vec::<u8>::new(); NUMBER_OF_THREADS]));
    let results = Arc::new(Mutex::new(vec![State::Initial; NUMBER_OF_THREADS]));
    let lock = Arc::new(Mutex::new(()));
    thread::scope(|scope| {
        let values_for_read = values.clone();
        let results_for_read = results.clone();
        // read thread
        let mut read_socket = test_basics.socket.try_clone().unwrap();
        scope.spawn(move || {
            let mut received_callbacks = 0;
            let mut buffer = vec![0_u8; 2 * (VALUE_LENGTH + 2 * HEADER_END)];
            let mut next_start = 0;
            while received_callbacks < NUMBER_OF_THREADS * 2 {
                let size = read_socket.read(&mut buffer[next_start..]).unwrap();
                let mut cursor = 0;
                while cursor < size {
                    let length = (&buffer[cursor..cursor + MESSAGE_LENGTH_END])
                        .read_u32::<LittleEndian>()
                        .unwrap() as usize;
                    let callback_index = (&buffer
                        [cursor + MESSAGE_LENGTH_END..cursor + CALLBACK_INDEX_END])
                        .read_u32::<LittleEndian>()
                        .unwrap() as usize;
                    let response_type = ResponseType::from_u32(
                        (&buffer[cursor + CALLBACK_INDEX_END..cursor + TYPE_END])
                            .read_u32::<LittleEndian>()
                            .unwrap(),
                    )
                    .unwrap();

                    if cursor + length > size + next_start {
                        break;
                    }

                    {
                        let mut results = results_for_read.lock().unwrap();
                        match response_type {
                            ResponseType::Null => {
                                assert_eq!(results[callback_index], State::Initial);
                                results[callback_index] = State::ReceivedNull;
                            }
                            ResponseType::String => {
                                assert_eq!(results[callback_index], State::ReceivedNull);

                                let values = values_for_read.lock().unwrap();

                                assert_eq!(
                                    &buffer[cursor + HEADER_END..cursor + length],
                                    values[callback_index]
                                );

                                results[callback_index] = State::ReceivedValue;
                            }
                        };
                    }

                    cursor += length;
                    received_callbacks += 1;
                }

                let save_size = next_start + size - cursor;
                next_start = save_size;
                if next_start > 0 {
                    let mut new_buffer = vec![0_u8; 2 * VALUE_LENGTH + 4 * HEADER_END];
                    let slice = &buffer[cursor..cursor + save_size];
                    let iter = slice.iter().copied();
                    new_buffer.splice(..save_size, iter);
                    buffer = new_buffer;
                }
            }
        });

        for i in 0..NUMBER_OF_THREADS {
            let mut write_socket = test_basics.socket.try_clone().unwrap();
            let values = values.clone();
            let index = i;
            let cloned_lock = lock.clone();
            scope.spawn(move || {
                let key = format!("hello{}", index);
                let value = generate_random_bytes(VALUE_LENGTH);

                {
                    let mut values = values.lock().unwrap();
                    values[index] = value.clone();
                }

                // Send a set request
                let message_length =
                    VALUE_LENGTH + key.len() + HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;
                let mut buffer = Vec::with_capacity(message_length);
                buffer
                    .write_u32::<LittleEndian>(message_length as u32)
                    .unwrap();
                buffer.write_u32::<LittleEndian>(index as u32).unwrap();
                buffer
                    .write_u32::<LittleEndian>(RequestType::SetString.to_u32().unwrap())
                    .unwrap();
                buffer.write_u32::<LittleEndian>(key.len() as u32).unwrap();
                buffer.write_all(key.as_bytes()).unwrap();
                buffer.write_all(&value).unwrap();
                {
                    let _guard = cloned_lock.lock().unwrap();
                    write_socket.write_all(&buffer).unwrap();
                }
                buffer.clear();

                // Send a get request
                let message_length = key.len() + HEADER_END;
                buffer
                    .write_u32::<LittleEndian>(message_length as u32)
                    .unwrap();
                buffer.write_u32::<LittleEndian>(index as u32).unwrap();
                buffer
                    .write_u32::<LittleEndian>(RequestType::GetString.to_u32().unwrap())
                    .unwrap();
                buffer.write_all(key.as_bytes()).unwrap();
                {
                    let _guard = cloned_lock.lock().unwrap();
                    write_socket.write_all(&buffer).unwrap();
                }
            });
        }
    });

    let results = results.lock().unwrap();
    for i in 0..NUMBER_OF_THREADS {
        assert_eq!(State::ReceivedValue, results[i]);
    }

    thread::sleep(std::time::Duration::from_secs(1)); // TODO: delete this, find a better way to gracefully close the server thread
}
