from cffi import FFI
from glide.protobuf.command_request_pb2 import Command, CommandRequest, RequestType
from typing import List, Union, Optional
from glide.sync_commands.core import CoreCommands
from glide.constants import DEFAULT_READ_BYTES_SIZE, OK, TEncodable, TRequest, TResult
from glide.routes import Route


class GlideSync(CoreCommands):        
    def __init__(self):
        self._init_ffi()
        # Call the `create_client` function
        client_response_ptr = self.lib.create_client()
        # Handle the connection response
        if client_response_ptr != self.ffi.NULL:
            client_response = self.ffi.cast("ConnectionResponse*", client_response_ptr)
            if client_response.conn_ptr != self.ffi.NULL:
                print("Client created successfully.")
                self.core_client = client_response.conn_ptr
            else:
                error_message = self.ffi.string(client_response.connection_error_message).decode('utf-8') if client_response.connection_error_message != self.ffi.NULL else "Unknown error"
                print(f"Failed to create client. Error: {error_message}")

            # Free the connection response to avoid memory leaks
            self.lib.free_connection_response(client_response_ptr)
        else:
            print("Failed to create client, response pointer is NULL.")

    def _init_ffi(self):
        self.ffi = FFI()

        # Define the CommandResponse struct and related types
        self.ffi.cdef("""
        typedef struct CommandResponse {
            int response_type;
            long int_value;
            double float_value;
            bool bool_value;
            char* string_value;
            long string_value_len;
            struct CommandResponse* array_value;
            long array_value_len;
            struct CommandResponse* map_key;
            struct CommandResponse* map_value;
            struct CommandResponse* sets_value;
            long sets_value_len;
        } CommandResponse;

        typedef struct ConnectionResponse {
            const void* conn_ptr;
            const char* connection_error_message;
        } ConnectionResponse;

        const ConnectionResponse* create_client();
        void free_command_response(CommandResponse* response);
        void free_connection_response(ConnectionResponse* response);

        CommandResponse* command(
            const void *client_adapter_ptr,
            size_t channel,
            int command_type,
            unsigned long arg_count,
            const size_t *args,
            const unsigned long *args_len
        );

        """)

        # Load the shared library (adjust the path to your compiled Rust library)
        self.lib = self.ffi.dlopen("/home/ubuntu/glide-for-redis/go/target/release/libglide_rs.so")
        
    def _handle_response(self, message):
        if message == self.ffi.NULL:
            print("Received NULL message.")
            return None

        # Identify the type of the message
        message_type = self.ffi.typeof(message).cname

        # If message is a pointer to CommandResponse, dereference it
        if message_type == "CommandResponse *":
            message = message[0]  # Dereference the pointer
            message_type = self.ffi.typeof(message).cname
        # Check if message is now a CommandResponse
        if message_type == "CommandResponse":
            msg = message
            if msg.response_type == 0:  # Null
                return None
            elif msg.response_type == 1:  # Int
                return msg.int_value
            elif msg.response_type == 2:  # Float
                return msg.float_value
            elif msg.response_type == 3:  # Bool
                return bool(msg.bool_value)
            elif msg.response_type == 4:  # String
                try:
                    string_value = self.ffi.buffer(msg.string_value, msg.string_value_len)[:]
                    return string_value
                except Exception as e:
                    print(f"Error decoding string value: {e}")
            elif msg.response_type == 5:  # Array
                array = []
                for i in range(msg.array_value_len):
                    element = self.ffi.cast("struct CommandResponse*", msg.array_value + i)
                    array.append(self._handle_response(element))
                return array
            elif msg.response_type == 6:  # Map
                map_dict = {}
                for i in range(msg.array_value_len):
                    key = self.ffi.cast("struct CommandResponse*", msg.map_key + i)
                    value = self.ffi.cast("struct CommandResponse*", msg.map_value + i)
                    map_dict[self._handle_response(key)] = self._handle_response(value)
                return map_dict
            elif msg.response_type == 7:  # Sets
                result_set = set()
                sets_array = self.ffi.cast(f"struct CommandResponse[{msg.sets_value_len}]", msg.sets_value)
                for i in range(msg.sets_value_len):
                    element = sets_array[i]  # Already a struct
                    result_set.add(self._handle_response(element))
                return result_set
            else:
                print(f"Unhandled response type = {msg.response_type}")
                return None
        else:
            print(f"Unexpected message type: {message_type}")
            return None    

    def _to_c_strings(self, args):
        """Convert Python arguments to C-compatible pointers and lengths."""
        c_strings = []
        string_lengths = []
        buffers = []  # Keep a reference to prevent premature garbage collection

        for arg in args:
            if isinstance(arg, str):
                # Convert string to UTF-8 bytes
                arg_bytes = arg.encode('utf-8')
            elif isinstance(arg, (int, float)):
                # Convert numeric values to strings and then to bytes
                arg_bytes = str(arg).encode('utf-8')
            else:
                raise ValueError(f"Unsupported argument type: {type(arg)}")

            # Use ffi.from_buffer for zero-copy conversion
            buffers.append(arg_bytes)  # Keep the byte buffer alive
            c_strings.append(self.ffi.cast("size_t", self.ffi.from_buffer(arg_bytes)))
            string_lengths.append(len(arg_bytes))

            # Debugging
            print(f"arg={arg}, arg_bytes={list(arg_bytes)}, len={len(arg_bytes)}, c_str={c_strings[-1]}")

        # Return C-compatible arrays and keep buffers alive
        return (
            self.ffi.new("size_t[]", c_strings),
            self.ffi.new("unsigned long[]", string_lengths),
            buffers,  # Ensure buffers stay alive
        )
    
    def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[TEncodable],
        route: Optional[Route] = None,
    ) -> TResult:
        client_adapter_ptr = self.core_client
        if client_adapter_ptr == self.ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Convert the arguments to C-compatible pointers
        c_args, c_lengths, buffers = self._to_c_strings(args)
        # Call the command function
        return self._handle_response(self.lib.command(
            client_adapter_ptr,  # Client pointer
            1,  # Example channel (adjust as needed)
            request_type,  # Request type (e.g., GET or SET)
            len(args),  # Number of arguments
            c_args,  # Array of argument pointers
            c_lengths  # Array of argument lengths
        ))
