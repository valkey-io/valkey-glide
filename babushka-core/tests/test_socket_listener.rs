use babushka::headers::{
    RequestType, ResponseType, CALLBACK_INDEX_END, HEADER_END, MESSAGE_LENGTH_END,
    MESSAGE_LENGTH_FIELD_LENGTH, TYPE_END,
};
use babushka::*;
use byteorder::{LittleEndian, ReadBytesExt, WriteBytesExt};
use num_traits::{FromPrimitive, ToPrimitive};
use rand::{distributions::Standard, thread_rng, Rng};
use rsevents::{Awaitable, EventState, ManualResetEvent};
use std::io::prelude::*;
use std::sync::{Arc, Mutex};
use std::{os::unix::net::UnixStream, thread};
mod utilities;
use utilities::*;

#[cfg(test)]
mod socket_listener {
    use super::*;
    use babushka::headers::HEADER_WITH_KEY_LENGTH_END;
    use redis::Value;
    use rstest::rstest;
    use std::{mem::size_of, time::Duration};

    struct TestBasics {
        _server: RedisServer,
        socket: UnixStream,
    }

    fn assert_value(buffer: &[u8], cursor: usize, expected: &[u8]) {
        let pointer = (&buffer[cursor + HEADER_END..cursor + HEADER_END + size_of::<usize>()])
            .read_u64::<LittleEndian>()
            .unwrap() as *mut Value;
        let received_value = unsafe { Box::from_raw(pointer) };
        assert_eq!(*received_value, Value::Data(expected.to_owned()));
    }

    fn write_header(
        buffer: &mut Vec<u8>,
        length: usize,
        callback_index: u32,
        request_type: RequestType,
    ) {
        buffer.write_u32::<LittleEndian>(length as u32).unwrap();
        buffer.write_u32::<LittleEndian>(callback_index).unwrap();
        buffer
            .write_u32::<LittleEndian>(request_type.to_u32().unwrap())
            .unwrap();
    }

    fn write_get(buffer: &mut Vec<u8>, callback_index: u32, key: &str) {
        write_header(
            buffer,
            HEADER_END + key.len(),
            callback_index,
            RequestType::GetString,
        );
        buffer.write_all(key.as_bytes()).unwrap();
    }

    fn write_set(buffer: &mut Vec<u8>, callback_index: u32, key: &str, value: &Vec<u8>) {
        write_header(
            buffer,
            HEADER_WITH_KEY_LENGTH_END + key.len() + value.len(),
            callback_index,
            RequestType::SetString,
        );
        buffer.write_u32::<LittleEndian>(key.len() as u32).unwrap();
        buffer.write_all(key.as_bytes()).unwrap();
        buffer.write_all(value).unwrap();
    }

    fn parse_header(buffer: &[u8]) -> (u32, u32, ResponseType) {
        let message_length = (&buffer[..MESSAGE_LENGTH_END])
            .read_u32::<LittleEndian>()
            .unwrap();
        let callback_index = (&buffer[MESSAGE_LENGTH_END..CALLBACK_INDEX_END])
            .read_u32::<LittleEndian>()
            .unwrap();
        let response_type = (&buffer[CALLBACK_INDEX_END..HEADER_END])
            .read_u32::<LittleEndian>()
            .unwrap();
        let response_type = ResponseType::from_u32(response_type).unwrap();
        (message_length, callback_index, response_type)
    }

    fn assert_header(
        buffer: &[u8],
        expected_callback: u32,
        expected_size: usize,
        expected_response_type: ResponseType,
    ) {
        let (message_length, callback_index, response_type) = parse_header(buffer);
        assert_eq!(message_length, expected_size as u32);
        assert_eq!(callback_index, expected_callback);
        assert_eq!(response_type, expected_response_type);
    }

    fn assert_null_header(buffer: &[u8], expected_callback: u32) {
        assert_header(buffer, expected_callback, HEADER_END, ResponseType::Null);
    }

    fn assert_received_value(buffer: &[u8], expected_callback: u32, expected_value: &[u8]) {
        assert_header(
            buffer,
            expected_callback,
            HEADER_END + size_of::<usize>(),
            ResponseType::Value,
        );
        assert_value(buffer, 0, expected_value);
    }

    fn send_address(address: String, socket: &UnixStream, use_tls: bool) {
        // Send the server address
        const CALLBACK_INDEX: u32 = 1;
        let address = if use_tls {
            format!("rediss://{address}#insecure")
        } else {
            format!("redis://{address}")
        };
        let message_length = address.len() + HEADER_END;
        let mut buffer = Vec::with_capacity(message_length);
        write_header(
            &mut buffer,
            message_length,
            CALLBACK_INDEX,
            RequestType::ServerAddress,
        );
        buffer.write_all(address.as_bytes()).unwrap();
        let mut socket = socket.try_clone().unwrap();
        socket.write_all(&buffer).unwrap();
        let size = socket.read(&mut buffer).unwrap();
        assert_eq!(size, HEADER_END);
        assert_null_header(&buffer, CALLBACK_INDEX);
    }

    fn setup_test_basics(use_tls: bool) -> TestBasics {
        let socket_listener_state: Arc<ManualResetEvent> =
            Arc::new(ManualResetEvent::new(EventState::Unset));
        let context = TestContext::new(ServerType::Tcp { tls: use_tls });
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
        send_address(address, &socket, use_tls);
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

    #[rstest]
    #[timeout(Duration::from_millis(5000))]
    fn test_socket_set_and_get(#[values(false, true)] use_tls: bool) {
        let mut test_basics = setup_test_basics(use_tls);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 10;
        let key = "hello";
        let value = generate_random_bytes(VALUE_LENGTH);
        // Send a set request
        let message_length = VALUE_LENGTH + key.len() + HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;
        let mut buffer = Vec::with_capacity(message_length);
        write_set(&mut buffer, CALLBACK1_INDEX, key, &value);
        test_basics.socket.write_all(&buffer).unwrap();

        let size = test_basics.socket.read(&mut buffer).unwrap();
        assert_eq!(size, HEADER_END);
        assert_null_header(&buffer, CALLBACK1_INDEX);

        buffer.clear();
        write_get(&mut buffer, CALLBACK2_INDEX, key);
        test_basics.socket.write_all(&buffer).unwrap();

        let expected_length = size_of::<usize>() + HEADER_END;
        // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        unsafe { buffer.set_len(message_length) };
        let size = test_basics.socket.read(&mut buffer).unwrap();
        assert_eq!(size, expected_length);
        assert_received_value(&buffer, CALLBACK2_INDEX, &value);
    }

    #[rstest]
    #[timeout(Duration::from_millis(5000))]
    fn test_socket_get_returns_null(#[values(false, true)] use_tls: bool) {
        const CALLBACK_INDEX: u32 = 99;
        let mut test_basics = setup_test_basics(use_tls);
        let key = "hello";
        let mut buffer = Vec::with_capacity(HEADER_END);
        write_get(&mut buffer, CALLBACK_INDEX, key);
        test_basics.socket.write_all(&buffer).unwrap();

        let size = test_basics.socket.read(&mut buffer).unwrap();
        assert_eq!(size, HEADER_END);
        assert_null_header(&buffer, CALLBACK_INDEX);
    }

    #[rstest]
    #[timeout(Duration::from_millis(5000))]
    fn test_socket_report_error(#[values(false, true)] use_tls: bool) {
        let mut test_basics = setup_test_basics(use_tls);

        const CALLBACK_INDEX: u32 = 99;
        let key = "a";
        // Send a set request
        let message_length = HEADER_END + key.len();
        let mut buffer = Vec::with_capacity(message_length);
        buffer
            .write_u32::<LittleEndian>(message_length as u32)
            .unwrap();
        buffer.write_u32::<LittleEndian>(CALLBACK_INDEX).unwrap();
        buffer.write_u32::<LittleEndian>(u32::MAX).unwrap(); // here we send an erroneous enum
        buffer.write_all(key.as_bytes()).unwrap();
        test_basics.socket.write_all(&buffer).unwrap();

        let _ = test_basics.socket.read(&mut buffer).unwrap();
        let (_, _, response_type) = parse_header(&buffer);
        assert_eq!(response_type, ResponseType::ClosingError);
    }

    #[rstest]
    #[timeout(Duration::from_millis(5000))]
    fn test_socket_handle_long_input(#[values(false, true)] use_tls: bool) {
        let mut test_basics = setup_test_basics(use_tls);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 1000000;
        let key = "hello";
        let value = generate_random_bytes(VALUE_LENGTH);
        // Send a set request
        let message_length = VALUE_LENGTH + key.len() + HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;
        let mut buffer = Vec::with_capacity(message_length);
        write_set(&mut buffer, CALLBACK1_INDEX, key, &value);
        test_basics.socket.write_all(&buffer).unwrap();

        let size = test_basics.socket.read(&mut buffer).unwrap();
        assert_eq!(size, HEADER_END);
        assert_null_header(&buffer, CALLBACK1_INDEX);

        buffer.clear();
        write_get(&mut buffer, CALLBACK2_INDEX, key);
        test_basics.socket.write_all(&buffer).unwrap();

        let expected_length = size_of::<usize>() + HEADER_END;
        // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        unsafe { buffer.set_len(message_length) };
        let mut size = 0;
        while size < expected_length {
            let next_read = test_basics.socket.read(&mut buffer[size..]).unwrap();
            assert_ne!(0, next_read);
            size += next_read;
        }
        assert_eq!(size, expected_length);
        assert_received_value(&buffer, CALLBACK2_INDEX, &value);
    }

    // This test starts multiple threads writing large inputs to a socket, and another thread that reads from the output socket and
    // verifies that the outputs match the inputs.
    #[rstest]
    #[timeout(Duration::from_millis(15000))]
    fn test_socket_handle_multiple_long_inputs(#[values(false, true)] use_tls: bool) {
        #[derive(Clone, PartialEq, Eq, Debug)]
        enum State {
            Initial,
            ReceivedNull,
            ReceivedValue,
        }
        let test_basics = setup_test_basics(use_tls);
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
                        let (length, callback_index, response_type) =
                            parse_header(&buffer[cursor..cursor + TYPE_END]);
                        let callback_index = callback_index as usize;
                        let length = length as usize;

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
                                ResponseType::Value => {
                                    assert_eq!(results[callback_index], State::ReceivedNull);

                                    let values = values_for_read.lock().unwrap();

                                    assert_value(&buffer, cursor, &values[callback_index]);

                                    results[callback_index] = State::ReceivedValue;
                                }
                                _ => unreachable!(),
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
                    let key = format!("hello{index}");
                    let value = generate_random_bytes(VALUE_LENGTH);

                    {
                        let mut values = values.lock().unwrap();
                        values[index] = value.clone();
                    }

                    // Send a set request
                    let message_length =
                        VALUE_LENGTH + key.len() + HEADER_END + MESSAGE_LENGTH_FIELD_LENGTH;
                    let mut buffer = Vec::with_capacity(message_length);
                    write_set(&mut buffer, index as u32, &key, &value);
                    {
                        let _guard = cloned_lock.lock().unwrap();
                        write_socket.write_all(&buffer).unwrap();
                    }
                    buffer.clear();

                    // Send a get request
                    write_get(&mut buffer, index as u32, &key);
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
}
