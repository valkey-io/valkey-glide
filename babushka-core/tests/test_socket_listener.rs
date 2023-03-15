use babushka::*;
use rand::Rng;
use rsevents::{Awaitable, EventState, ManualResetEvent};
use std::io::prelude::*;
use std::sync::{Arc, Mutex};
use std::{os::unix::net::UnixStream, thread};
mod utilities;
use integer_encoding::VarInt;
use utilities::*;

/// Response header length approximation, including the length of the message and the callback index
const APPROX_RESP_HEADER_LEN: usize = 3;

#[cfg(test)]
mod socket_listener {
    use super::*;
    use babushka::connection_request::{AddressInfo, ConnectionRequest};
    use babushka::redis_request::redis_request::{Args, ArgsArray};
    use babushka::response::{response, ConstantResponse, Response};
    use protobuf::{EnumOrUnknown, Message};
    use rand::distributions::Alphanumeric;
    use redis::{ConnectionAddr, Value};
    use redis_request::{RedisRequest, RequestType};
    use rstest::rstest;
    use std::{mem::size_of, time::Duration};
    use tokio::{net::UnixListener, runtime::Builder};

    /// An enum representing the values of the request type field for testing purposes
    #[derive(PartialEq, Eq, Debug)]
    pub enum ResponseType {
        /// Type of a response that returns a null.
        Null = 0,
        /// Type of a response that returns a redis value, and not an error.
        Value = 1,
        /// Type of response containing an error that impacts a single request.
        RequestError = 2,
        /// Type of response containing an error causes the connection to close.
        ClosingError = 3,
    }

    struct TestBasics {
        _server: Arc<RedisServer>,
        socket: UnixStream,
    }

    fn assert_value(pointer: u64, expected_value: Option<Value>) {
        let pointer = pointer as *mut Value;
        let received_value = unsafe { Box::from_raw(pointer) };
        assert!(expected_value.is_some());
        assert_eq!(*received_value, expected_value.unwrap());
    }

    fn decode_response(buffer: &[u8], cursor: usize, message_length: usize) -> Response {
        let header_end = cursor;
        match Response::parse_from_bytes(&buffer[header_end..header_end + message_length]) {
            Ok(res) => res,
            Err(err) => {
                panic!(
                    "Error decoding protocol message\r\n|── Protobuf error was: {:?}",
                    err.to_string()
                );
            }
        }
    }

    fn assert_null_response(buffer: &[u8], expected_callback: u32) {
        assert_response(buffer, 0, expected_callback, None, ResponseType::Null);
    }

    fn assert_ok_response(buffer: &[u8], expected_callback: u32) {
        assert_response(
            buffer,
            0,
            expected_callback,
            Some(Value::Okay),
            ResponseType::Value,
        );
    }

    fn assert_error_response(
        buffer: &[u8],
        expected_callback: u32,
        error_type: ResponseType,
    ) -> Response {
        assert_response(buffer, 0, expected_callback, None, error_type)
    }

    fn assert_response(
        buffer: &[u8],
        cursor: usize,
        expected_callback: u32,
        expected_value: Option<Value>,
        response_type: ResponseType,
    ) -> Response {
        let (message_length, header_bytes) = parse_header(buffer);
        let response = decode_response(buffer, cursor + header_bytes, message_length as usize);
        assert_eq!(response.callback_idx, expected_callback);
        match response.value {
            Some(response::Value::RespPointer(pointer)) => {
                assert_value(pointer, expected_value);
            }
            Some(response::Value::ClosingError(ref _err)) => {
                assert_eq!(response_type, ResponseType::ClosingError);
            }
            Some(response::Value::RequestError(ref _err)) => {
                assert_eq!(response_type, ResponseType::RequestError);
            }
            Some(response::Value::ConstantResponse(enum_value)) => {
                let enum_value = enum_value.unwrap();
                if enum_value == ConstantResponse::OK {
                    assert_eq!(expected_value.unwrap(), Value::Okay);
                } else {
                    unreachable!()
                }
            }
            Some(_) => unreachable!(),
            None => {
                assert!(expected_value.is_none());
            }
        };
        response
    }

    fn write_header(buffer: &mut Vec<u8>, length: u32) {
        let required_space = u32::required_space(length);
        let new_len = buffer.len() + required_space;
        buffer.resize(new_len, 0_u8);
        u32::encode_var(length, &mut buffer[new_len - required_space..]);
    }

    fn write_message(buffer: &mut Vec<u8>, request: impl Message) -> u32 {
        let message_length = request.compute_size() as u32;

        write_header(buffer, message_length);
        let _res = buffer.write_all(&request.write_to_bytes().unwrap());
        message_length
    }

    fn write_request(
        buffer: &mut Vec<u8>,
        callback_index: u32,
        args: Vec<String>,
        request_type: EnumOrUnknown<RequestType>,
        args_pointer: bool,
    ) -> u32 {
        let mut request = RedisRequest::new();
        request.callback_idx = callback_index;
        request.request_type = request_type;
        if args_pointer {
            request.args = Some(Args::ArgsVecPointer(
                Box::leak(Box::new(args)) as *mut Vec<String> as u64,
            ));
        } else {
            let mut args_array = ArgsArray::new();
            args_array.args = args;
            request.args = Some(Args::ArgsArray(args_array));
        }

        write_message(buffer, request)
    }

    fn write_get(buffer: &mut Vec<u8>, callback_index: u32, key: &str, args_pointer: bool) -> u32 {
        write_request(
            buffer,
            callback_index,
            vec![key.to_string()],
            RequestType::GetString.into(),
            args_pointer,
        )
    }

    fn write_set(
        buffer: &mut Vec<u8>,
        callback_index: u32,
        key: &str,
        value: String,
        args_pointer: bool,
    ) -> u32 {
        write_request(
            buffer,
            callback_index,
            vec![key.to_string(), value],
            RequestType::SetString.into(),
            args_pointer,
        )
    }

    fn parse_header(buffer: &[u8]) -> (u32, usize) {
        u32::decode_var(buffer).unwrap()
    }

    fn get_address_info(address: &ConnectionAddr) -> AddressInfo {
        let mut address_info = AddressInfo::new();
        match address {
            ConnectionAddr::Tcp(host, port) => {
                address_info.host = host.clone();
                address_info.port = *port as u32;
            }
            ConnectionAddr::TcpTls {
                host,
                port,
                insecure,
            } => {
                address_info.host = host.clone();
                address_info.port = *port as u32;
                address_info.insecure = *insecure;
            }
            ConnectionAddr::Unix(_) => unreachable!("Unix connection not tested"),
        }
        address_info
    }

    fn send_address(address: &ConnectionAddr, socket: &UnixStream, use_tls: bool) {
        // Send the server address
        const CALLBACK_INDEX: u32 = 0;

        let address_info = get_address_info(address);
        let approx_message_length = address_info.compute_size() as usize + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        let mut connection_request = ConnectionRequest::new();
        connection_request.addresses = vec![address_info];
        connection_request.use_tls = use_tls;
        write_message(&mut buffer, connection_request);
        let mut socket = socket.try_clone().unwrap();
        socket.write_all(&buffer).unwrap();
        let _size = socket.read(&mut buffer).unwrap();
        assert_null_response(&buffer, CALLBACK_INDEX);
    }

    fn setup_test_basics_with_server_and_socket_path(
        use_tls: bool,
        socket_path: Option<String>,
        redis_server: Arc<RedisServer>,
    ) -> TestBasics {
        let socket_listener_state: Arc<ManualResetEvent> =
            Arc::new(ManualResetEvent::new(EventState::Unset));
        let cloned_state = socket_listener_state.clone();
        let path_arc = Arc::new(std::sync::Mutex::new(None));
        let path_arc_clone = Arc::clone(&path_arc);
        socket_listener::start_socket_listener_internal(
            move |res| {
                let path: String = res.expect("Failed to initialize the socket listener");
                let mut path_arc_clone = path_arc_clone.lock().unwrap();
                *path_arc_clone = Some(path);
                cloned_state.set();
            },
            socket_path,
        );
        socket_listener_state.wait();
        let path = path_arc.lock().unwrap();
        let path = path.as_ref().expect("Didn't get any socket path");
        let socket = std::os::unix::net::UnixStream::connect(path).unwrap();
        let address = redis_server.get_client_addr();
        send_address(address, &socket, use_tls);
        TestBasics {
            _server: redis_server,
            socket,
        }
    }

    fn setup_test_basics_with_socket_path(
        use_tls: bool,
        socket_path: Option<String>,
    ) -> TestBasics {
        let context = TestContext::new(ServerType::Tcp { tls: use_tls });
        setup_test_basics_with_server_and_socket_path(
            use_tls,
            socket_path,
            Arc::new(context.server),
        )
    }

    fn setup_test_basics(use_tls: bool) -> TestBasics {
        setup_test_basics_with_socket_path(use_tls, None)
    }
    fn generate_random_string(length: usize) -> String {
        rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(length)
            .map(char::from)
            .collect()
    }

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_working_after_socket_listener_was_dropped() {
        let socket_path =
            get_socket_path_from_name("test_working_after_socket_listener_was_dropped".to_string());
        close_socket(&socket_path);
        // create a socket listener and drop it, to simulate a panic in a previous iteration.
        Builder::new_current_thread()
            .enable_all()
            .build()
            .unwrap()
            .block_on(async {
                let _ = UnixListener::bind(socket_path.clone()).unwrap();
            });

        const CALLBACK_INDEX: u32 = 99;
        let mut test_basics = setup_test_basics_with_socket_path(false, Some(socket_path.clone()));
        let key = "hello";
        let approx_message_length = key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_get(&mut buffer, CALLBACK_INDEX, key, false);
        test_basics.socket.write_all(&buffer).unwrap();

        let _size = test_basics.socket.read(&mut buffer).unwrap();
        assert_null_response(&buffer, CALLBACK_INDEX);
        close_socket(&socket_path);
    }

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_multiple_listeners_competing_for_the_socket() {
        let socket_path = get_socket_path_from_name(
            "test_multiple_listeners_competing_for_the_socket".to_string(),
        );
        close_socket(&socket_path);
        let server = Arc::new(TestContext::new(ServerType::Tcp { tls: false }).server);

        thread::scope(|scope| {
            for i in 0..20 {
                thread::Builder::new()
                    .name(format!("test-{i}"))
                    .spawn_scoped(scope, || {
                        const CALLBACK_INDEX: u32 = 99;
                        let mut test_basics = setup_test_basics_with_server_and_socket_path(
                            false,
                            Some(socket_path.clone()),
                            server.clone(),
                        );
                        let key = "hello";
                        let approx_message_length = key.len() + APPROX_RESP_HEADER_LEN;
                        let mut buffer = Vec::with_capacity(approx_message_length);
                        write_get(&mut buffer, CALLBACK_INDEX, key, false);
                        test_basics.socket.write_all(&buffer).unwrap();

                        let _size = test_basics.socket.read(&mut buffer).unwrap();
                        assert_null_response(&buffer, CALLBACK_INDEX);
                    })
                    .unwrap();
            }
        });
        close_socket(&socket_path);
    }

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_socket_set_and_get(
        #[values((false, false), (true, false), (false,true))] use_arg_pointer_and_tls: (
            bool,
            bool,
        ),
    ) {
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
        let mut test_basics = setup_test_basics(use_tls);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 10;
        let key = "hello";
        let value = generate_random_string(VALUE_LENGTH);
        // Send a set request
        let approx_message_length = VALUE_LENGTH + key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_set(
            &mut buffer,
            CALLBACK1_INDEX,
            key,
            value.clone(),
            args_pointer,
        );
        test_basics.socket.write_all(&buffer).unwrap();

        let _size = test_basics.socket.read(&mut buffer).unwrap();
        assert_ok_response(&buffer, CALLBACK1_INDEX);

        buffer.clear();
        write_get(&mut buffer, CALLBACK2_INDEX, key, args_pointer);
        test_basics.socket.write_all(&buffer).unwrap();
        // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        buffer.resize(approx_message_length, 0);
        let _size = test_basics.socket.read(&mut buffer).unwrap();
        assert_response(
            &buffer,
            0,
            CALLBACK2_INDEX,
            Some(Value::Data(value.into_bytes())),
            ResponseType::Value,
        );
    }

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_socket_get_returns_null(
        #[values((false, false), (true, false), (false,true))] use_arg_pointer_and_tls: (
            bool,
            bool,
        ),
    ) {
        const CALLBACK_INDEX: u32 = 99;
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
        let mut test_basics = setup_test_basics(use_tls);
        let key = "hello";
        let mut buffer = Vec::with_capacity(key.len() * 2);
        write_get(&mut buffer, CALLBACK_INDEX, key, args_pointer);
        test_basics.socket.write_all(&buffer).unwrap();

        let _size = test_basics.socket.read(&mut buffer).unwrap();
        assert_null_response(&buffer, CALLBACK_INDEX);
    }

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_socket_report_error(#[values(false, true)] use_tls: bool) {
        let mut test_basics = setup_test_basics(use_tls);

        const CALLBACK_INDEX: u32 = 99;
        let key = "a";
        let request_type = i32::MAX; // here we send an erroneous enum
                                     // Send a set request
        let approx_message_length = key.len() + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_request(
            &mut buffer,
            CALLBACK_INDEX,
            vec![key.to_string()],
            EnumOrUnknown::from_i32(request_type),
            false,
        );
        test_basics.socket.write_all(&buffer).unwrap();
        let mut buffer = [0; 50];
        let _size = test_basics.socket.read(&mut buffer).unwrap();
        let response = assert_error_response(&buffer, CALLBACK_INDEX, ResponseType::ClosingError);
        assert_eq!(
            response.closing_error(),
            format!("Received invalid request type: {request_type}")
        );
    }

    #[rstest]
    #[timeout(Duration::from_millis(10000))]
    fn test_socket_handle_long_input(
        #[values((false, false), (true, false), (false,true))] use_arg_pointer_and_tls: (
            bool,
            bool,
        ),
    ) {
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
        let mut test_basics = setup_test_basics(use_tls);

        const CALLBACK1_INDEX: u32 = 100;
        const CALLBACK2_INDEX: u32 = 101;
        const VALUE_LENGTH: usize = 1000000;
        let key = "hello";
        let value = generate_random_string(VALUE_LENGTH);
        // Send a set request
        let approx_message_length = VALUE_LENGTH
            + key.len()
            + u32::required_space(VALUE_LENGTH as u32)
            + APPROX_RESP_HEADER_LEN;
        let mut buffer = Vec::with_capacity(approx_message_length);
        write_set(
            &mut buffer,
            CALLBACK1_INDEX,
            key,
            value.clone(),
            args_pointer,
        );
        test_basics.socket.write_all(&buffer).unwrap();

        let _size = test_basics.socket.read(&mut buffer).unwrap();
        assert_ok_response(&buffer, CALLBACK1_INDEX);

        buffer.clear();
        write_get(&mut buffer, CALLBACK2_INDEX, key, args_pointer);
        test_basics.socket.write_all(&buffer).unwrap();

        let response_header_length = u32::required_space(size_of::<usize>() as u32);
        let expected_length = size_of::<usize>() + response_header_length + 2; // 2 bytes for callbackIdx and value type
                                                                               // we set the length to a longer value, just in case we'll get more data - which is a failure for the test.
        buffer.resize(approx_message_length, 0);
        let mut size = 0;
        while size < expected_length {
            let next_read = test_basics.socket.read(&mut buffer[size..]).unwrap();
            assert_ne!(0, next_read);
            size += next_read;
        }
        assert_response(
            &buffer,
            0,
            CALLBACK2_INDEX,
            Some(Value::Data(value.into_bytes())),
            ResponseType::Value,
        );
    }

    // This test starts multiple threads writing large inputs to a socket, and another thread that reads from the output socket and
    // verifies that the outputs match the inputs.
    #[rstest]
    #[timeout(Duration::from_millis(15000))]
    fn test_socket_handle_multiple_long_inputs(
        #[values((false, false), (true, false), (false,true))] use_arg_pointer_and_tls: (
            bool,
            bool,
        ),
    ) {
        #[derive(Clone, PartialEq, Eq, Debug)]
        enum State {
            Initial,
            ReceivedNull,
            ReceivedValue,
        }
        let args_pointer = use_arg_pointer_and_tls.0;
        let use_tls = use_arg_pointer_and_tls.1;
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
                let mut buffer = vec![0_u8; 2 * (VALUE_LENGTH + 2 * APPROX_RESP_HEADER_LEN)];
                let mut next_start = 0;
                while received_callbacks < NUMBER_OF_THREADS * 2 {
                    let size = read_socket.read(&mut buffer[next_start..]).unwrap();
                    let mut cursor = 0;
                    while cursor < size {
                        let (request_len, header_bytes) =
                            parse_header(&buffer[cursor..cursor + APPROX_RESP_HEADER_LEN]);
                        let length = request_len as usize;

                        if cursor + header_bytes + length > size + next_start {
                            break;
                        }

                        {
                            let response = decode_response(&buffer, cursor + header_bytes, length);
                            let callback_index = response.callback_idx as usize;
                            let mut results = results_for_read.lock().unwrap();
                            match response.value {
                                Some(response::Value::ConstantResponse(constant)) => {
                                    assert_eq!(constant, ConstantResponse::OK.into());
                                    assert_eq!(results[callback_index], State::Initial);
                                    results[callback_index] = State::ReceivedNull;
                                }
                                Some(response::Value::RespPointer(pointer)) => {
                                    assert_eq!(results[callback_index], State::ReceivedNull);

                                    let values = values_for_read.lock().unwrap();

                                    assert_value(
                                        pointer,
                                        Some(Value::Data(values[callback_index].clone())),
                                    );
                                    results[callback_index] = State::ReceivedValue;
                                }
                                _ => unreachable!(),
                            };
                        }

                        cursor += length + header_bytes;
                        received_callbacks += 1;
                    }

                    let save_size = next_start + size - cursor;
                    next_start = save_size;
                    if next_start > 0 {
                        let mut new_buffer =
                            vec![0_u8; 2 * VALUE_LENGTH + 4 * APPROX_RESP_HEADER_LEN];
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
                    let value = generate_random_string(VALUE_LENGTH);

                    {
                        let mut values = values.lock().unwrap();
                        values[index] = value.clone().into();
                    }

                    // Send a set request
                    let approx_message_length = VALUE_LENGTH + key.len() + APPROX_RESP_HEADER_LEN;
                    let mut buffer = Vec::with_capacity(approx_message_length);
                    write_set(&mut buffer, index as u32, &key, value, args_pointer);
                    {
                        let _guard = cloned_lock.lock().unwrap();
                        write_socket.write_all(&buffer).unwrap();
                    }
                    buffer.clear();

                    // Send a get request
                    write_get(&mut buffer, index as u32, &key, args_pointer);
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
    }
}
