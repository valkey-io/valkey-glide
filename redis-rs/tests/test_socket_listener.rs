#![cfg(feature = "tokio-comp")]
mod support;
use crate::support::*;
use crate::ClosingReason::UnhandledError;
use byteorder::{LittleEndian, ReadBytesExt, WriteBytesExt};
use futures::channel::oneshot::{channel, Receiver};
use ntest::timeout;
use num_traits::{FromPrimitive, ToPrimitive};
use rand::{distributions::Standard, thread_rng, Rng};
use redis::socket_listener::headers::{
    RequestType, ResponseType, CALLBACK_INDEX_END, HEADER_END, MESSAGE_LENGTH_END,
    MESSAGE_LENGTH_FIELD_LENGTH, TYPE_END,
};
use redis::socket_listener::*;
use std::io::{self, prelude::*, ErrorKind};
use std::sync::{Arc, Mutex};
use std::{
    mem,
    os::unix::net::{UnixListener, UnixStream},
    thread,
    time::Duration,
};
use tempfile::tempdir;

struct TestBasics {
    _server: RedisServer,
    read_socket: UnixStream,
    write_socket: UnixStream,
    closing_message_receiver: Option<Receiver<ClosingReason>>,
}

fn setup_test_basics() -> TestBasics {
    let (close_sender, close_receiver) = channel();
    let (start_sender, start_receiver) = channel();
    let context = TestContext::new();
    let dir = tempdir().unwrap();
    let read_socket_path = dir.path().join("read");
    let write_socket_path = dir.path().join("write");
    let read_listener = UnixListener::bind(read_socket_path.as_path()).unwrap();
    let write_listener = UnixListener::bind(write_socket_path.as_path()).unwrap();
    start_socket_listener(
        context.client,
        read_socket_path
            .as_path()
            .as_os_str()
            .to_str()
            .unwrap()
            .to_string(),
        write_socket_path
            .as_path()
            .as_os_str()
            .to_str()
            .unwrap()
            .to_string(),
        || {
            start_sender.send(()).unwrap();
        },
        |res| {
            close_sender.send(res).unwrap();
        },
    );
    let read_socket = read_listener.accept().unwrap().0;
    let write_socket = write_listener.accept().unwrap().0;
    wait_for_receiver(start_receiver);
    TestBasics {
        _server: context.server,
        read_socket,
        write_socket,
        closing_message_receiver: Some(close_receiver),
    }
}

fn get_receiver(mut test_basics: TestBasics) -> Receiver<ClosingReason> {
    mem::replace(&mut test_basics.closing_message_receiver, None).unwrap()
}

fn wait_for_receiver<T>(mut receiver: Receiver<T>) -> T {
    while let Ok(received) = receiver.try_recv() {
        if let Some(val) = received {
            return val;
        }
        thread::sleep(Duration::from_millis(1));
    }
    unreachable!()
}

fn wait_for_closing_result(receiver: Receiver<ClosingReason>, expected_reason: ClosingReason) {
    let err = wait_for_receiver(receiver);
    if mem::discriminant(&err) != mem::discriminant(&expected_reason) {
        panic!("Expected: {:?}, received: {:?}", expected_reason, err);
    }
    if let UnhandledError(received_err) = err {
        if let UnhandledError(expected_err) = expected_reason {
            assert_eq!(received_err.category(), expected_err.category());
            assert_eq!(received_err.code(), expected_err.code());
        }
    }
}

fn generate_random_bytes(length: usize) -> Vec<u8> {
    thread_rng()
        .sample_iter::<u8, Standard>(Standard)
        .take(length)
        .map(u8::from)
        .collect()
}

#[test]
fn test_socket_reports_closing() {
    let receiver = get_receiver(setup_test_basics());

    wait_for_closing_result(receiver, ClosingReason::ReadSocketClosed);
}

#[test]
fn test_socket_set_and_get() {
    let receiver = {
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
        test_basics.read_socket.write_all(&buffer).unwrap();

        let size = test_basics.write_socket.read(&mut buffer).unwrap();
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
        test_basics.read_socket.write_all(&buffer).unwrap();

        let expected_length = VALUE_LENGTH + HEADER_END;
        let expected_aligned_length = expected_length + (4 - expected_length % 4);
        // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        unsafe { buffer.set_len(message_length) };
        let size = test_basics.write_socket.read(&mut buffer).unwrap();
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

        get_receiver(test_basics)
    };

    wait_for_closing_result(receiver, ClosingReason::ReadSocketClosed);
}

#[test]
fn test_socket_get_returns_null() {
    let receiver = {
        let mut test_basics = setup_test_basics();

        const CALLBACK_INDEX: u32 = 99;
        let key = "hello";
        let mut buffer = Vec::with_capacity(HEADER_END);
        buffer.write_u32::<LittleEndian>(17_u32).unwrap();
        buffer.write_u32::<LittleEndian>(CALLBACK_INDEX).unwrap();
        buffer
            .write_u32::<LittleEndian>(RequestType::GetString.to_u32().unwrap())
            .unwrap();
        buffer.write_all(key.as_bytes()).unwrap();
        test_basics.read_socket.write_all(&buffer).unwrap();

        let size = test_basics.write_socket.read(&mut buffer).unwrap();
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

        get_receiver(test_basics)
    };

    wait_for_closing_result(receiver, ClosingReason::ReadSocketClosed);
}

#[test]
fn test_socket_report_error() {
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
    test_basics.read_socket.write_all(&buffer).unwrap();

    let receiver = get_receiver(test_basics);

    wait_for_closing_result(
        receiver,
        ClosingReason::UnhandledError(io::Error::new(ErrorKind::InvalidInput, "").into()),
    );
}

#[test]
fn test_socket_handle_long_input() {
    let receiver = {
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
        test_basics.read_socket.write_all(&buffer).unwrap();

        let size = test_basics.write_socket.read(&mut buffer).unwrap();
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
        test_basics.read_socket.write_all(&buffer).unwrap();

        let expected_length = VALUE_LENGTH + HEADER_END;
        // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        unsafe { buffer.set_len(message_length) };
        let mut size = 0;
        while size < expected_length {
            let next_read = test_basics.write_socket.read(&mut buffer[size..]).unwrap();
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

        get_receiver(test_basics)
    };

    wait_for_closing_result(receiver, ClosingReason::ReadSocketClosed);
}

// This test starts multiple threads writing large inputs to a socket, and another thread that reads from the output socket and
// verifies that the outputs match the inputs.
#[test]
#[timeout(10000)]
fn test_socket_handle_multiple_long_inputs() {
    #[derive(Clone, PartialEq, Eq, Debug)]
    enum State {
        Initial,
        ReceivedNull,
        ReceivedValue,
    }

    let receiver = {
        let mut test_basics = setup_test_basics();
        let receiver = mem::replace(&mut test_basics.closing_message_receiver, None).unwrap();
        let write_socket = Arc::new(Mutex::new(test_basics.read_socket));
        let mut read_socket = test_basics.write_socket.try_clone().unwrap();

        const VALUE_LENGTH: usize = 1000000;
        const NUMBER_OF_THREADS: usize = 10;
        let values = Arc::new(Mutex::new(vec![Vec::<u8>::new(); NUMBER_OF_THREADS]));
        let results = Arc::new(Mutex::new(vec![State::Initial; NUMBER_OF_THREADS]));

        thread::scope(|scope| {
            let values_for_read = values.clone();
            let results_for_read = results.clone();
            // read thread
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
                let write_socket = write_socket.clone();
                let values = values.clone();
                let index = i;
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
                        let mut write_socket = write_socket.lock().unwrap();
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
                        let mut write_socket = write_socket.lock().unwrap();
                        write_socket.write_all(&buffer).unwrap();
                    }
                });
            }
        });

        let results = results.lock().unwrap();
        for i in 0..NUMBER_OF_THREADS {
            assert_eq!(State::ReceivedValue, results[i]);
        }

        receiver
    };

    wait_for_closing_result(receiver, ClosingReason::ReadSocketClosed);
}
