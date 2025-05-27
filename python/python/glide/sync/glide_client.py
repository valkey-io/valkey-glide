# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import sys
from pathlib import Path
from typing import List, Optional, Union

from cffi import FFI

from glide.commands.sync_commands.cluster_commands import ClusterCommands
from glide.commands.sync_commands.core import CoreCommands
from glide.commands.sync_commands.standalone_commands import StandaloneCommands
from glide.config import BaseClientConfiguration, GlideClusterClientConfiguration
from glide.constants import OK, TEncodable, TResult
from glide.exceptions import ClosingError, RequestError
from glide.glide_client import get_request_error_class
from glide.protobuf.command_request_pb2 import RequestType
from glide.routes import Route, build_protobuf_route

if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self

ENCODING = "utf-8"
CURR_DIR = Path(__file__).resolve().parent
ROOT_DIR = CURR_DIR.parent.parent.parent.parent
FFI_DIR = ROOT_DIR / "ffi"
LIB_FILE = FFI_DIR / "target" / "debug" / "libglide_ffi.so"


# Enum values must match the Rust definition
class FFIClientTypeEnum:
    Async = 0
    Sync = 1


class BaseClient(CoreCommands):

    def __init__(self, config: BaseClientConfiguration):
        """
        To create a new client, use the `create` classmethod
        """
        self.config: BaseClientConfiguration = config
        self._is_closed: bool = False

    @classmethod
    def create(cls, config: BaseClientConfiguration) -> Self:
        self = cls(config)
        self._init_ffi()
        self.config = config
        self._is_closed = False

        os.register_at_fork(after_in_child=self._create_core_client)

        self._create_core_client()

        return self

    def _create_core_client(self):
        conn_req = self.config._create_a_protobuf_conn_request(
            cluster_mode=type(self.config) is GlideClusterClientConfiguration
        )
        conn_req_bytes = conn_req.SerializeToString()
        client_type = self.ffi.new(
            "ClientType*",
            {
                "_type": self.ffi.cast("ClientTypeEnum", FFIClientTypeEnum.Sync),
            },
        )
        client_response_ptr = self.lib.create_client(
            conn_req_bytes, len(conn_req_bytes), client_type
        )
        # Handle the connection response
        if client_response_ptr != self.ffi.NULL:
            client_response = self._try_ffi_cast(
                "ConnectionResponse*", client_response_ptr
            )
            if client_response.conn_ptr != self.ffi.NULL:
                self.core_client = client_response.conn_ptr
            else:
                error_message = (
                    self.ffi.string(client_response.connection_error_message).decode(
                        ENCODING
                    )
                    if client_response.connection_error_message != self.ffi.NULL
                    else "Unknown error"
                )
                raise ClosingError(error_message)

            # Free the connection response to avoid memory leaks
            self.lib.free_connection_response(client_response_ptr)
        else:
            raise ClosingError("Failed to create client, response pointer is NULL.")

    def _init_ffi(self):
        self.ffi = FFI()

        # Define the CommandResponse struct and related types
        self.ffi.cdef(
            """
            struct CommandResponse {
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
            };

            typedef struct CommandResponse CommandResponse;

            typedef enum {
                Null = 0,
                Int = 1,
                Float = 2,
                Bool = 3,
                String = 4,
                Array = 5,
                Map = 6,
                Sets = 7
            } ResponseType;

            typedef void (*SuccessCallback)(uintptr_t index_ptr, const CommandResponse* message);
            typedef void (*FailureCallback)(uintptr_t index_ptr, const char* error_message, int error_type);

            typedef struct {
                const void* conn_ptr;
                const char* connection_error_message;
            } ConnectionResponse;

            typedef struct {
                const char* command_error_message;
                int command_error_type;
            } CommandError;

            typedef struct {
                CommandResponse* response;
                CommandError* command_error;
            } CommandResult;

            typedef enum {
                Async = 0,
                Sync = 1
            } ClientTypeEnum;

            typedef struct {
                SuccessCallback success_callback;
                FailureCallback failure_callback;
            } AsyncClient;

            typedef struct {
                int _type;  // Enum to differentiate between Async and Sync
                union {
                    struct {
                        void (*success_callback)(uintptr_t, const void*);
                        void (*failure_callback)(uintptr_t, const char*, int);
                    } async_client;
                };
            } ClientType;

            // Function declarations
            const ConnectionResponse* create_client(
                const uint8_t* connection_request_bytes,
                size_t connection_request_len,
                const ClientType* client_type  // Pass by pointer
            );
            void close_client(const void* client_adapter_ptr);
            void free_connection_response(ConnectionResponse* connection_response_ptr);
            char* get_response_type_string(int response_type);
            void free_response_type_string(char* response_string);
            void free_command_response(CommandResponse* command_response_ptr);
            void free_error_message(char* error_message);
            void free_command_result(CommandResult* command_result_ptr);
            CommandResult* command(
                const void* client_adapter_ptr, uintptr_t channel, int command_type,
                unsigned long arg_count, const size_t *args, const unsigned long* args_len,
                const unsigned char* route_bytes, size_t route_bytes_len
            );

        """
        )

        # Load the shared library (adjust the path to your compiled Rust library)
        self.lib = self.ffi.dlopen(str(LIB_FILE.resolve()))

    def _handle_response(self, message):
        if message == self.ffi.NULL:
            raise RequestError("Received NULL message.")

        message_type = self.ffi.typeof(message).cname
        if message_type == "CommandResponse *":
            message = message[0]
            message_type = self.ffi.typeof(message).cname

        if message_type != "CommandResponse":
            raise RequestError(f"Unexpected message type = {message_type}")

        return self._handle_command_response(message)

    def _handle_command_response(self, msg):
        """Handle a CommandResponse message based on its response type."""
        handlers = {
            0: self._handle_null_response,
            1: self._handle_int_response,
            2: self._handle_float_response,
            3: self._handle_bool_response,
            4: self._handle_string_response,
            5: self._handle_array_response,
            6: self._handle_map_response,
            7: self._handle_set_response,
            8: self._handle_ok_response,
        }

        handler = handlers.get(msg.response_type)
        if handler is None:
            raise RequestError(f"Unhandled response type = {msg.response_type}")

        return handler(msg)

    def _handle_null_response(self, msg):
        return None

    def _handle_int_response(self, msg):
        return msg.int_value

    def _handle_float_response(self, msg):
        return msg.float_value

    def _handle_bool_response(self, msg):
        return bool(msg.bool_value)

    def _handle_string_response(self, msg):
        try:
            return self.ffi.buffer(msg.string_value, msg.string_value_len)[:]
        except Exception as e:
            raise RequestError(f"Error decoding string value: {e}")

    def _handle_array_response(self, msg):
        array = []
        for i in range(msg.array_value_len):
            element = self._try_ffi_cast("struct CommandResponse*", msg.array_value + i)
            array.append(self._handle_response(element))
        return array

    def _handle_map_response(self, msg):
        map_dict = {}
        for i in range(msg.array_value_len):
            element = self._try_ffi_cast("struct CommandResponse*", msg.array_value + i)
            key = self._try_ffi_cast("struct CommandResponse*", element.map_key)
            value = self._try_ffi_cast("struct CommandResponse*", element.map_value)
            map_dict[self._handle_response(key)] = self._handle_response(value)
        return map_dict

    def _handle_set_response(self, msg):
        result_set = set()
        sets_array = self._try_ffi_cast(
            f"struct CommandResponse[{msg.sets_value_len}]", msg.sets_value
        )
        for i in range(msg.sets_value_len):
            element = sets_array[i]
            result_set.add(self._handle_response(element))
        return result_set

    def _handle_ok_response(self, msg):
        return OK

    def _try_ffi_cast(self, type, source):
        try:
            return self.ffi.cast(type, source)
        except Exception as e:
            raise ClosingError(f"FFI casting failed: {e}")

    def _to_c_strings(self, args):
        """Convert Python arguments to C-compatible pointers and lengths."""
        c_strings = []
        string_lengths = []
        buffers = []  # Keep a reference to prevent premature garbage collection

        for arg in args:
            if isinstance(arg, str):
                # Convert string to bytes
                arg_bytes = arg.encode(ENCODING)
            elif isinstance(arg, (int, float)):
                # Convert numeric values to strings and then to bytes
                arg_bytes = str(arg).encode(ENCODING)
            elif isinstance(arg, bytes):
                arg_bytes = arg
            else:
                raise ValueError(f"Unsupported argument type: {type(arg)}")

            # Use ffi.from_buffer for zero-copy conversion
            buffers.append(arg_bytes)  # Keep the byte buffer alive
            c_strings.append(
                self._try_ffi_cast("size_t", self.ffi.from_buffer(arg_bytes))
            )
            string_lengths.append(len(arg_bytes))
        # Return C-compatible arrays and keep buffers alive
        return (
            self.ffi.new("size_t[]", c_strings),
            self.ffi.new("unsigned long[]", string_lengths),
            buffers,  # Ensure buffers stay alive
        )

    def _handle_cmd_result(self, command_result):
        try:
            if command_result == self.ffi.NULL:
                raise ClosingError("Internal error: Received NULL as a command result")
            if command_result.command_error != self.ffi.NULL:
                # Handle the error case
                error = self._try_ffi_cast(
                    "CommandError*", command_result.command_error
                )
                error_message = self.ffi.string(error.command_error_message).decode(
                    ENCODING
                )
                error_class = get_request_error_class(error.command_error_type)
                # Free the error message to avoid memory leaks
                raise error_class(error_message)
            else:
                return self._handle_response(command_result.response)
                # Free the error message to avoid memory leaks
        finally:
            self.lib.free_command_result(command_result)

    def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[TEncodable],
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        client_adapter_ptr = self.core_client
        if client_adapter_ptr == self.ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Convert the arguments to C-compatible pointers
        c_args, c_lengths, buffers = self._to_c_strings(args)

        proto_route = build_protobuf_route(route)
        if proto_route:
            route_bytes = proto_route.SerializeToString()
            route_ptr = self.ffi.from_buffer(route_bytes)
        else:
            route_bytes = b""
            route_ptr = self.ffi.NULL

        result = self.lib.command(
            client_adapter_ptr,  # Client pointer
            1,  # Example channel (adjust as needed)
            request_type,  # Request type (e.g., GET or SET)
            len(args),  # Number of arguments
            c_args,  # Array of argument pointers
            c_lengths,  # Array of argument lengths
            route_ptr,
            len(route_bytes),
        )
        return self._handle_cmd_result(result)

    def close(self):
        if not self._is_closed:
            self.lib.close_client(self.core_client)
            self.core_client = self.ffi.NULL
            self._is_closed = True


class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    For full documentation, see
    https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper#cluster
    """


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    For full documentation, see
    https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper#standalone
    """


TGlideClient = Union[GlideClient, GlideClusterClient]
