# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import sys
from typing import Any, List, Optional, Tuple, Union

from glide_shared.config import BaseClientConfiguration
from glide_shared.constants import OK, TEncodable, TResult
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    RequestError,
    get_request_error_class,
)
from glide_shared.protobuf.command_request_pb2 import RequestType
from glide_shared.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
    build_protobuf_route,
)

from ._glide_ffi import _GlideFFI
from .config import GlideClientConfiguration, GlideClusterClientConfiguration
from .logger import Level, Logger
from .sync_commands.cluster_commands import ClusterCommands
from .sync_commands.core import CoreCommands
from .sync_commands.standalone_commands import StandaloneCommands

if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self

ENCODING = "utf-8"


# Enum values must match the Rust definition
class FFIClientTypeEnum:
    Async = 0
    Sync = 1


class BaseClient(CoreCommands):

    def __init__(self, config: BaseClientConfiguration):
        """
        To create a new client, use the `create` classmethod
        """
        self._glide_ffi = _GlideFFI()
        self._ffi = self._glide_ffi.ffi
        self._lib = self._glide_ffi.lib
        self._config: BaseClientConfiguration = config
        self._is_closed: bool = False

    @classmethod
    def create(cls, config: BaseClientConfiguration) -> Self:
        if not isinstance(
            config, (GlideClientConfiguration, GlideClusterClientConfiguration)
        ):
            raise ConfigurationError(
                "Configuration must be an instance of the sync version of GlideClientConfiguration or GlideClusterClientConfiguration, imported from glide_sync.config."
            )
        self = cls(config)
        self._config = config
        self._is_closed = False

        os.register_at_fork(after_in_child=self._create_core_client)

        self._create_core_client()

        return self

    def _create_core_client(self):
        conn_req = self._config._create_a_protobuf_conn_request(
            cluster_mode=type(self._config) is GlideClusterClientConfiguration
        )
        conn_req_bytes = conn_req.SerializeToString()
        client_type = self._ffi.new(
            "ClientType*",
            {
                "_type": self._ffi.cast("ClientTypeEnum", FFIClientTypeEnum.Sync),
            },
        )
        pubsub_callback = self._ffi.cast(
            "PubSubCallback", 0
        )  # PubSub not yet implementet for Sync Python
        client_response_ptr = self._lib.create_client(
            conn_req_bytes,
            len(conn_req_bytes),
            client_type,
            pubsub_callback,
        )

        Logger.log(Level.INFO, "connection info", "new connection established")

        # Handle the connection response
        if client_response_ptr != self._ffi.NULL:
            client_response = self._try_ffi_cast(
                "ConnectionResponse*", client_response_ptr
            )
            if client_response.conn_ptr != self._ffi.NULL:
                self._core_client = client_response.conn_ptr
            else:
                error_message = (
                    self._ffi.string(client_response.connection_error_message).decode(
                        ENCODING
                    )
                    if client_response.connection_error_message != self._ffi.NULL
                    else "Unknown error"
                )
                raise ClosingError(error_message)

            # Free the connection response to avoid memory leaks
            self._lib.free_connection_response(client_response_ptr)
        else:
            raise ClosingError("Failed to create client, response pointer is NULL.")

    def _handle_response(self, message):
        if message == self._ffi.NULL:
            raise RequestError("Received NULL message.")

        message_type = self._ffi.typeof(message).cname
        if message_type == "CommandResponse *":
            message = message[0]
            message_type = self._ffi.typeof(message).cname

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
            9: self._handle_error_response,
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
            return self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
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

    def _handle_error_response(self, msg):
        try:
            error_msg = self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
            return RequestError(f"{error_msg}")
        except Exception as e:
            raise RequestError(f"Error decoding error message: {e}")

    def _try_ffi_cast(self, type, source):
        try:
            return self._ffi.cast(type, source)
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
                self._try_ffi_cast("size_t", self._ffi.from_buffer(arg_bytes))
            )
            string_lengths.append(len(arg_bytes))
        # Return C-compatible arrays and keep buffers alive
        return (
            self._ffi.new("size_t[]", c_strings),
            self._ffi.new("unsigned long[]", string_lengths),
            buffers,  # Ensure buffers stay alive
        )

    def _handle_cmd_result(self, command_result):
        try:
            if command_result == self._ffi.NULL:
                raise ClosingError("Internal error: Received NULL as a command result")
            if command_result.command_error != self._ffi.NULL:
                # Handle the error case
                error = self._try_ffi_cast(
                    "CommandError*", command_result.command_error
                )
                error_message = self._ffi.string(error.command_error_message).decode(
                    ENCODING
                )
                error_class = get_request_error_class(error.command_error_type)
                # Free the error message to avoid memory leaks
                raise error_class(error_message)
            else:
                return self._handle_response(command_result.response)
                # Free the error message to avoid memory leaks
        finally:
            self._lib.free_command_result(command_result)

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
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Convert the arguments to C-compatible pointers
        c_args, c_lengths, buffers = self._to_c_strings(args)

        proto_route = build_protobuf_route(route)
        if proto_route:
            route_bytes = proto_route.SerializeToString()
            route_ptr = self._ffi.from_buffer(route_bytes)
        else:
            route_bytes = b""
            route_ptr = self._ffi.NULL

        result = self._lib.command(
            client_adapter_ptr,  # Client pointer
            1,  # Example channel (adjust as needed)
            request_type,  # Request type (e.g., GET or SET)
            len(args),  # Number of arguments
            c_args,  # Array of argument pointers
            c_lengths,  # Array of argument lengths
            route_ptr,
            len(route_bytes),
            0,  # Span pointer (0 for no tracing)
        )
        return self._handle_cmd_result(result)

    def _update_connection_password(
        self,
        password: Optional[str],
        immediate_auth: bool = False,
    ) -> TResult:
        """
        Update the current connection password with a new password.

        Note:
            This method updates the client's internal password configuration and does
            not perform password rotation on the server side.

        This method is useful in scenarios where the server password has changed or when
        utilizing short-lived passwords for enhanced security. It allows the client to
        update its password to reconnect upon disconnection without the need to recreate
        the client instance. This ensures that the internal reconnection mechanism can
        handle reconnection seamlessly, preventing the loss of in-flight commands.

        Args:
            password (`Optional[str]`): The new password to use for the connection,
                if `None` the password will be removed.
            immediate_auth (`bool`):
                `True`: The client will authenticate immediately with the new password against all connections, Using `AUTH`
                command. If password supplied is an empty string, auth will not be performed and warning will be returned.
                The default is `False`.

        Returns:
            TOK: A simple OK response. If `immediate_auth=True` returns OK if the reauthenticate succeed.

        Example:
            >>> client.update_connection_password("new_password", immediate_auth=True)
            'OK'
        """
        if self._is_closed:
            raise ClosingError("Client is closed.")
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Prepare C string for password
        c_password = (
            self._ffi.new("char[]", password.encode(ENCODING))
            if password is not None
            else self._ffi.new("char[]", b"")
        )

        result = self._lib.update_connection_password(
            client_adapter_ptr,
            0,  # Request ID (0 for sync use)
            c_password,
            immediate_auth,
        )
        return self._handle_cmd_result(result)

    def _execute_batch(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        is_atomic: bool,
        raise_on_error: bool,
        retry_server_error: bool = False,
        retry_connection_error: bool = False,
        route: Optional[Route] = None,
        timeout: Optional[int] = None,
    ) -> List[TResult]:
        """
        Execute a batch of commands synchronously using the FFI batch function.
        Accepts pre-extracted parameters from exec().
        """
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if not commands:
            raise ValueError("Batch cannot be empty")

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Convert commands + atomic flag to C BatchInfo
        batch_info, batch_refs = self._convert_commands_to_c_batch_info(
            commands, is_atomic
        )

        # Create batch options from extracted parameters
        batch_options, option_refs = self._create_c_batch_options_from_params(
            retry_server_error, retry_connection_error, route, timeout
        )

        # Keep references alive during call
        all_refs = batch_refs + option_refs  # noqa: F841

        result = self._lib.batch(
            client_adapter_ptr,
            0,  # callback_index (0 for sync)
            batch_info,
            raise_on_error,
            batch_options,
            0,  # span_ptr (not yet implemented in sync)
        )
        return self._handle_cmd_result(result)

    def _convert_commands_to_c_batch_info(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        is_atomic: bool,
    ) -> Tuple[Any, List[Any]]:
        """Convert commands directly to C BatchInfo (no intermediate _to_c_strings)."""
        if not commands:
            raise ValueError("Batch cannot be empty")

        all_refs = []
        cmd_infos = []

        for request_type, args in commands:
            buffers = []
            arg_ptrs = []
            arg_lengths = []

            for arg in args:
                if isinstance(arg, str):
                    arg_bytes = arg.encode("utf-8")
                elif isinstance(arg, (int, float)):
                    arg_bytes = str(arg).encode("utf-8")
                elif isinstance(arg, bytes):
                    arg_bytes = arg
                else:
                    raise ValueError(f"Unsupported argument type: {type(arg)}")

                buffers.append(arg_bytes)
                arg_ptrs.append(self._ffi.from_buffer(arg_bytes))
                arg_lengths.append(len(arg_bytes))

            # Build the two arrays directly
            c_arg_array = self._ffi.new("const uint8_t*[]", arg_ptrs)
            c_lengths = self._ffi.new("size_t[]", arg_lengths)

            cmd_info = self._ffi.new(
                "CmdInfo*",
                {
                    "request_type": request_type,
                    "args": c_arg_array,
                    "arg_count": len(args),
                    "args_len": c_lengths,
                },
            )

            cmd_infos.append(cmd_info)
            all_refs.extend(buffers + [c_arg_array, c_lengths])

        cmd_info_array = self._ffi.new("const CmdInfo*[]", cmd_infos)
        all_refs.append(cmd_info_array)
        all_refs.extend(cmd_infos)

        batch_info = self._ffi.new(
            "BatchInfo*",
            {
                "cmd_count": len(commands),
                "cmds": cmd_info_array,
                "is_atomic": is_atomic,
            },
        )

        return batch_info, all_refs + [batch_info]

    def _create_c_batch_options_from_params(
        self,
        retry_server_error: bool,
        retry_connection_error: bool,
        route: Optional[Route],
        timeout: Optional[int],
    ) -> Tuple[Any, List[Any]]:
        """Create BatchOptionsInfo from params, with refs."""
        route_info, route_refs = self._convert_route_to_c_format(route)

        batch_options = self._ffi.new(
            "BatchOptionsInfo*",
            {
                "retry_server_error": retry_server_error,
                "retry_connection_error": retry_connection_error,
                "has_timeout": timeout is not None,
                "timeout": timeout or 0,
                "route_info": route_info,
            },
        )

        return batch_options, route_refs + [batch_options]

    def _convert_route_to_c_format(
        self, route: Optional[Route]
    ) -> Tuple[Any, List[Any]]:
        if route is None:
            return self._ffi.NULL, []

        refs = []

        slot_key_ptr = self._ffi.NULL
        hostname_ptr = self._ffi.NULL
        route_type = 2  # Default to Random
        slot_id = 0
        slot_type = 0  # Primary by default
        port = 0

        if isinstance(route, AllNodes):
            route_type = 0
        elif isinstance(route, AllPrimaries):
            route_type = 1
        elif isinstance(route, RandomNode):
            route_type = 2
        elif isinstance(route, SlotIdRoute):
            route_type = 3
            slot_id = route.slot_id
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, SlotKeyRoute):
            route_type = 4
            slot_key_bytes = route.slot_key.encode("utf-8") + b"\0"
            refs.append(slot_key_bytes)
            slot_key_ptr = self._ffi.from_buffer(slot_key_bytes)
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, ByAddressRoute):
            route_type = 5
            hostname_bytes = route.host.encode("utf-8") + b"\0"
            refs.append(hostname_bytes)
            hostname_ptr = self._ffi.from_buffer(hostname_bytes)
            port = route.port if route.port is not None else 0
        else:
            raise RequestError(f"Invalid route type: {type(route)}")

        route_info = self._ffi.new(
            "RouteInfo*",
            {
                "route_type": route_type,
                "slot_id": slot_id,
                "slot_key": slot_key_ptr,
                "slot_type": slot_type,
                "hostname": hostname_ptr,
                "port": port,
            },
        )

        return route_info, refs + [route_info]

    def close(self):
        if not self._is_closed:
            self._lib.close_client(self._core_client)
            self._core_client = self._ffi.NULL
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
