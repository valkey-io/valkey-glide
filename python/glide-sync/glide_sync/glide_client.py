# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import sys
import threading
from typing import Any, List, Optional, Tuple, Union

from glide_shared.commands.command_args import ObjectType
from glide_shared.commands.core_options import PubSubMsg
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
from .sync_commands.cluster_scan_cursor import ClusterScanCursor
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
        _glide_ffi = _GlideFFI()
        self._ffi = _glide_ffi.ffi
        self._lib = _glide_ffi.lib
        self._config: BaseClientConfiguration = config
        self._pubsub_queue: List[PubSubMsg] = []
        self._pubsub_lock = threading.Lock()
        self._pubsub_condition = threading.Condition(self._pubsub_lock)
        self._pubsub_callback_ref = None  # Keep callback alive

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
        # This check is needed in case a fork happens after the client already closed
        # In that case the registered fork function will kick in even if the
        # client already closed, and recreate it anyway.
        if self._is_closed:
            return
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

        if self._config._is_pubsub_configured():
            # If in subscribed mode, create a callback that will be called by the FFI layer
            # for handling push notifications. This callback would either call the user callback (if provided),
            # or append the messaged to the the `_pubsub_queue`
            python_callback = self._create_push_handle_callback()
            pubsub_callback = self._ffi.callback("PubSubCallback", python_callback)
            # Store reference to prevent garbage collection
            self._pubsub_callback_ref = pubsub_callback
        else:
            pubsub_callback = self._ffi.cast("PubSubCallback", 0)

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

    def _create_push_handle_callback(self):
        """Create the FFI pubsub callback function"""

        def _pubsub_callback(
            client_ptr,
            kind,
            message_ptr,
            message_len,
            channel_ptr,
            channel_len,
            pattern_ptr,
            pattern_len,
        ):
            try:
                # Convert C pointers to Python bytes using ffi.buffer
                message = self._ffi.buffer(message_ptr, message_len)[:]
                channel = self._ffi.buffer(channel_ptr, channel_len)[:]
                pattern = (
                    self._ffi.buffer(pattern_ptr, pattern_len)[:]
                    if pattern_ptr != self._ffi.NULL
                    else None
                )

                push_kind_map = {
                    0: "Disconnection",
                    1: "Other",
                    2: "Invalidate",
                    3: "Message",
                    4: "PMessage",
                    5: "SMessage",
                    6: "Unsubscribe",
                    7: "PUnsubscribe",
                    8: "SUnsubscribe",
                    9: "Subscribe",
                    10: "PSubscribe",
                    11: "SSubscribe",
                }

                message_kind = push_kind_map.get(kind)

                if message_kind == "Disconnection":
                    Logger.log(
                        Level.WARN,
                        "disconnect notification",
                        "Transport disconnected, messages might be lost",
                    )
                elif message_kind in ["Message", "PMessage", "SMessage"]:
                    pubsub_msg = PubSubMsg(
                        message=message, channel=channel, pattern=pattern
                    )

                    # This aquires the underlying `_pubsub_lock` and allows for calling `notify()` on the variable
                    # If a callback is registered, call it with the message and the provided context
                    # Otherwise, append the message to the queue and notify threads that are waiting for a message.
                    with self._pubsub_condition:
                        user_callback, context = (
                            self._config._get_pubsub_callback_and_context()
                        )
                        if user_callback:
                            user_callback(pubsub_msg, context)
                        else:
                            self._pubsub_queue.append(pubsub_msg)
                            self._pubsub_condition.notify()
                elif message_kind in [
                    "PSubscribe",
                    "Subscribe",
                    "SSubscribe",
                    "Unsubscribe",
                    "PUnsubscribe",
                    "SUnsubscribe",
                ]:
                    pass  # Ignore subscription confirmations
                else:
                    Logger.log(
                        Level.WARN,
                        "unknown notification",
                        f"Unknown notification message: '{message_kind}'",
                    )

            except Exception as e:
                Logger.log(
                    Level.ERROR, "pubsub_callback", f"Error in pubsub callback: {e}"
                )

        return _pubsub_callback

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
                arg_bytes = arg.encode(ENCODING)
            elif isinstance(arg, bytes):
                arg_bytes = arg
            else:
                raise TypeError(f"Unsupported argument type: {type(arg)}")

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

    # `route_bytes` must remain alive for the duration of the FFI call that consumes `route_ptr`
    def _to_c_route_ptr_and_len(self, route: Optional[Route]):
        proto_route = build_protobuf_route(route)
        if proto_route:
            route_bytes = proto_route.SerializeToString()
            route_ptr = self._ffi.from_buffer(route_bytes)
            route_len = len(route_bytes)
        else:
            route_bytes = None
            route_ptr = self._ffi.NULL
            route_len = 0

        return route_ptr, route_len, route_bytes

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

        # Route bytes should be kept alive in the scope of the FFI call
        route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

        result = self._lib.command(
            client_adapter_ptr,  # Pointer to the ClientAdapter from create_client()
            0,  # Request ID - placeholder for sync clients (used for async callbacks)
            request_type,  # Request type (e.g., GET or SET)
            len(args),  # Number of arguments
            c_args,  # Array of argument pointers
            c_lengths,  # Array of argument lengths
            route_ptr,  # Pointer to protobuf-encoded routing information (NULL if no routing)
            route_len,  # Length of the routing data in bytes (0 if no routing)
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

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Note: batch_refs and option_refs must remain in scope
        # throughout this entire function call to prevent garbage collection of Python objects
        # that have C pointers pointing to them via ffi.from_buffer().

        # Convert commands + atomic flag to C BatchInfo
        batch_info, batch_refs = self._convert_commands_to_c_batch_info(
            commands, is_atomic
        )

        # Create batch options from extracted parameters
        batch_options, option_refs = self._create_c_batch_options_from_params(
            retry_server_error, retry_connection_error, route, timeout
        )

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
        """
        Convert commands directly to C BatchInfo (no intermediate _to_c_strings).
        Returns a tuple of (batch_info, refs) where refs contains all Python objects
        that must be kept alive to prevent garbage collection while C code uses pointers to them.
        """
        # all_refs keeps Python objects alive while C pointers reference their memory.
        # ffi.from_buffer() creates C pointers to Python object memory, and ffi.new() creates
        # FFI-managed memory with a Python reference controlling its lifetime. In both cases,
        # if Python references are garbage collected, the underlying memory may be freed,
        # creating dangling C pointers.

        all_refs = []
        cmd_infos = []

        for request_type, args in commands:
            args_buffers = []
            arg_ptrs = []
            arg_lengths = []

            for arg in args:
                if isinstance(arg, str):
                    arg_bytes = arg.encode(ENCODING)
                elif isinstance(arg, bytes):
                    arg_bytes = arg
                else:
                    raise TypeError(f"Unsupported argument type: {type(arg)}")

                args_buffers.append(arg_bytes)
                arg_ptrs.append(self._ffi.from_buffer(arg_bytes))
                arg_lengths.append(len(arg_bytes))

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
            all_refs.extend(args_buffers + [c_arg_array, c_lengths])

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
        """
        Create BatchOptionsInfo from params, with refs.
        Returns a tuple of (batch_options, refs) where refs contains all Python objects
        that must be kept alive while C code accesses pointers to them.
        """

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
        """
        Convert a Route object to C RouteInfo format.

        Returns a tuple of (route_info, refs) where refs contains all Python objects
        that must be kept alive while C code uses pointers to them.
        """
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
            # Null termination needed for safety instructions of the FFI layer's `ptr_to_str` call.
            slot_key_bytes = route.slot_key.encode(ENCODING) + b"\0"
            refs.append(slot_key_bytes)
            slot_key_ptr = self._ffi.from_buffer(slot_key_bytes)
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, ByAddressRoute):
            route_type = 5
            # Null termination needed for safety instructions of the FFI layer's `ptr_to_str` call.
            hostname_bytes = route.host.encode(ENCODING) + b"\0"
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

    def _execute_script(
        self,
        script_hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult:

        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Default to empty lists if None provided
        if keys is None:
            keys = []
        if args is None:
            args = []

        # Convert keys to C-compatible format
        keys_c_args, keys_c_lengths, keys_buffers = self._to_c_strings(keys)

        # Convert args to C-compatible format
        args_c_args, args_c_lengths, args_buffers = self._to_c_strings(args)

        # Convert script hash to C string
        hash_bytes = script_hash.encode(ENCODING) + b"\0"
        hash_buffer = self._ffi.from_buffer(hash_bytes)

        # Route bytes should be kept alive in the scope of the FFI call
        route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

        result = self._lib.invoke_script(
            client_adapter_ptr,  # Pointer to the ClientAdapter from create_client()
            0,  # Request ID - placeholder for sync clients (used for async callbacks)
            hash_buffer,  # Pointer to the script's SHA1 hash string
            len(keys),  # num of keys
            keys_c_args,  # keys (array of pointers)
            keys_c_lengths,  # keys_len (array of lengths)
            len(args),  # args_count
            args_c_args,  # args (array of pointers)
            args_c_lengths,  # args_len (array of lengths)
            route_ptr,  # Pointer to protobuf-encoded routing information (NULL if no routing)
            route_len,  # Length of the routing data in bytes (0 if no routing)
        )
        return self._handle_cmd_result(result)

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        """Try to get a pubsub message without blocking"""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if not self._config._is_pubsub_configured():
            raise ConfigurationError(
                "The operation will never succeed since there was no pubsbub subscriptions applied to the client."
            )

        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never succeed since messages will be passed to the configured callback."
            )

        with self._pubsub_condition:
            if self._pubsub_queue:
                return self._pubsub_queue.pop(0)
            else:
                return None

    def get_pubsub_message(self) -> PubSubMsg:
        """Get a pubsub message, blocking until one is available"""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if not self._config._is_pubsub_configured():
            raise ConfigurationError("No pubsub subscriptions configured")

        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )

        with self._pubsub_condition:
            while not self._pubsub_queue:
                if self._is_closed:
                    raise ClosingError("Client was closed while waiting for message")

                # Block indefinitely until notify() is called
                self._pubsub_condition.wait()

            return self._pubsub_queue.pop(0)

    def close(self):
        if not self._is_closed:
            self._is_closed = True
            with self._pubsub_condition:
                self._pubsub_condition.notify_all()
            self._lib.close_client(self._core_client)
            self._core_client = self._ffi.NULL
            self._pubsub_callback_ref = None


class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    For full documentation, see
    https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper#cluster
    """

    def _build_cluster_scan_args(self, match, count, type, allow_non_covered_slots):
        args = []
        if match is not None:
            # Inline _encode_arg logic
            if isinstance(match, str):
                encoded_match = match.encode(ENCODING)
            else:
                encoded_match = match
            args.extend([b"MATCH", encoded_match])

        if count is not None:
            args.extend([b"COUNT", str(count).encode(ENCODING)])
        if type is not None:
            args.extend([b"TYPE", type.value.encode(ENCODING)])
        if allow_non_covered_slots:
            args.extend([b"ALLOW_NON_COVERED_SLOTS"])

        return args

    def _cluster_scan(
        self,
        cursor: ClusterScanCursor,
        match: Optional[TEncodable] = None,
        count: Optional[int] = None,
        type: Optional[ObjectType] = None,
        allow_non_covered_slots: bool = False,
    ) -> List[Union[ClusterScanCursor, List[bytes]]]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Use helper method to build args
        args = self._build_cluster_scan_args(
            match, count, type, allow_non_covered_slots
        )
        # Convert cursor to C string
        cursor_string = cursor.get_cursor()
        cursor_bytes = cursor_string.encode(ENCODING) + b"\0"  # Null terminate for C

        # Keep references to prevent GC
        temp_buffers: List[Any] = [cursor_bytes]
        cursor_buffer = self._ffi.from_buffer(cursor_bytes)

        # Prepare FFI arguments
        if args:
            args_array, args_len_array, arg_buffers = self._to_c_strings(args)
            temp_buffers.extend(arg_buffers)  # Keep references alive
            arg_count = len(args)
        else:
            args_array = self._ffi.NULL
            args_len_array = self._ffi.NULL
            arg_count = 0

        result_ptr = self._lib.request_cluster_scan(
            client_adapter_ptr,
            0,
            cursor_buffer,
            arg_count,
            args_array,
            args_len_array,
        )

        response_data = self._handle_cmd_result(result_ptr)

        if not isinstance(response_data, list) or len(response_data) != 2:
            raise RequestError("Unexpected cluster scan response format")

        new_cursor = response_data[0]
        if isinstance(new_cursor, bytes):
            new_cursor = new_cursor.decode(ENCODING)

        keys_list = response_data[1] if response_data[1] is not None else []

        return [ClusterScanCursor(new_cursor), keys_list]


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    For full documentation, see
    https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper#standalone
    """


TGlideClient = Union[GlideClient, GlideClusterClient]
