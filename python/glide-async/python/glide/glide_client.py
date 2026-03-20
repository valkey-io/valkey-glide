# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import os
import sys
import threading
from typing import (
    Any,
    Dict,
    List,
    Optional,
    Set,
    Tuple,
    Union,
    cast,
)

<<<<<<< Updated upstream
from glide_sync._glide_ffi import _GlideFFI
from glide_sync.sync_commands.cluster_scan_cursor import ClusterScanCursor
=======
from glide_shared._fast_response import parse_response as _c_parse_response
from glide.async_commands.core import RequestType
from glide_shared._glide_ffi import _GlideFFI
from glide_shared.cluster_scan_cursor import ClusterScanCursor
>>>>>>> Stashed changes
from glide_shared.commands.command_args import ObjectType
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    BaseClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ServerCredentials,
)
from glide_shared.constants import (
    OK,
    TEncodable,
    TResult,
)
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    RequestError,
    get_request_error_class,
)
from glide.async_commands.core import RequestType
from glide_shared.routes import Route, build_protobuf_route

from .async_commands.cluster_commands import ClusterCommands
from .async_commands.core import CoreCommands
from .async_commands.standalone_commands import StandaloneCommands
from .logger import Level as LogLevel
from .logger import Logger as ClientLogger
from .opentelemetry import OpenTelemetry

from _fast_response import parse_response as _c_parse_response

if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self

ENCODING = "utf-8"


class FFIClientTypeEnum:
    Async = 0
    Sync = 1



class BaseClient(CoreCommands):
    def __init__(self, config: BaseClientConfiguration):
        """To create a new client, use the `create` classmethod"""
        _glide_ffi = _GlideFFI()
        self._ffi = _glide_ffi.ffi
        self._lib = _glide_ffi.lib
        self._config: BaseClientConfiguration = config
        self._is_closed: bool = False
        self._core_client = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._pending_futures: Dict[int, asyncio.Future] = {}
        self._callback_counter = 0
        self._lock = threading.Lock()
        self._success_callback_ref = None
        self._failure_callback_ref = None
        self._pubsub_callback_ref = None
        self._pubsub_futures: List[asyncio.Future] = []
        self._pubsub_lock = threading.Lock()
        self._pending_push_notifications: List[PubSubMsg] = []

    @classmethod
    async def create(cls, config: BaseClientConfiguration) -> Self:
        """Creates a Glide client.

        Args:
            config (ClientConfiguration): The configuration options for the client.

        Returns:
            Self: A promise that resolves to a connected client instance.
        """
        self = cls(config)
        self._loop = asyncio.get_running_loop()

        # Create CFFI callbacks
        @self._ffi.callback("SuccessCallback")
        def _success_cb(index_ptr, message):
            self._on_success(index_ptr, message)

        @self._ffi.callback("FailureCallback")
        def _failure_cb(index_ptr, error_message, error_type):
            self._on_failure(index_ptr, error_message, error_type)

        self._success_callback_ref = _success_cb
        self._failure_callback_ref = _failure_cb

        # Build connection request
        conn_req = config._create_a_protobuf_conn_request(
            cluster_mode=isinstance(config, GlideClusterClientConfiguration)
        )
        conn_req_bytes = conn_req.SerializeToString()

        # Create AsyncClient type
        client_type = self._ffi.new(
            "ClientType*",
            {
                "_type": self._ffi.cast("ClientTypeEnum", FFIClientTypeEnum.Async),
                "async_client": {
                    "success_callback": _success_cb,
                    "failure_callback": _failure_cb,
                },
            },
        )

        # Create pubsub callback
        python_callback = self._create_push_handle_callback()
        pubsub_callback = self._ffi.callback("PubSubCallback", python_callback)
        self._pubsub_callback_ref = pubsub_callback

        client_response_ptr = self._lib.create_client(
            conn_req_bytes,
            len(conn_req_bytes),
            client_type,
            pubsub_callback,
        )

        ClientLogger.log(LogLevel.INFO, "connection info", "new connection established")

        if client_response_ptr == self._ffi.NULL:
            raise ClosingError("Failed to create client, response pointer is NULL.")

        client_response = self._ffi.cast("ConnectionResponse*", client_response_ptr)
        if client_response.conn_ptr != self._ffi.NULL:
            self._core_client = client_response.conn_ptr
        else:
            error_msg = (
                self._ffi.string(client_response.connection_error_message).decode(ENCODING)
                if client_response.connection_error_message != self._ffi.NULL
                else "Unknown error"
            )
            self._lib.free_connection_response(client_response_ptr)
            raise ClosingError(error_msg)

        self._lib.free_connection_response(client_response_ptr)
        return self


    # ==================== Callback Handling ====================

    def _get_callback_id(self) -> int:
        self._callback_counter += 1
        return self._callback_counter

    def _on_success(self, index_ptr: int, message) -> None:
        """Called from Rust thread on command success."""
        if message == self._ffi.NULL:
            result = None
        else:
            addr = int(self._ffi.cast("uintptr_t", message))
            try:
                result, arena_ptr = _c_parse_response(addr)
                if arena_ptr:
                    self._lib.free_response_arena(self._ffi.cast("void*", arena_ptr))
            except Exception as e:
                result = e

        fut = self._pending_futures.pop(index_ptr, None)
        if fut is not None and not fut.done():
            if isinstance(result, Exception):
                self._loop.call_soon_threadsafe(fut.set_exception, result)
            else:
                self._loop.call_soon_threadsafe(fut.set_result, result)

    def _on_failure(self, index_ptr: int, error_message, error_type: int) -> None:
        """Called from Rust thread on command failure."""
        try:
            msg = self._ffi.string(error_message).decode(ENCODING)
        except Exception:
            msg = "Unknown error"

        exc = get_request_error_class(error_type)(msg)

        fut = self._pending_futures.pop(index_ptr, None)
        if fut is not None and not fut.done():
            self._loop.call_soon_threadsafe(fut.set_exception, exc)

    # ==================== PubSub ====================

    def _create_push_handle_callback(self):
        """Create the FFI pubsub callback function."""
        push_kind_map = {
            0: "Disconnection", 1: "Other", 2: "Invalidate",
            3: "Message", 4: "PMessage", 5: "SMessage",
            6: "Unsubscribe", 7: "PUnsubscribe", 8: "SUnsubscribe",
            9: "Subscribe", 10: "PSubscribe", 11: "SSubscribe",
        }

        def _pubsub_callback(
            client_ptr, kind, message_ptr, message_len,
            channel_ptr, channel_len, pattern_ptr, pattern_len,
        ):
            try:
                message = self._ffi.buffer(message_ptr, message_len)[:]
                channel = self._ffi.buffer(channel_ptr, channel_len)[:]
                pattern = (
                    self._ffi.buffer(pattern_ptr, pattern_len)[:]
                    if pattern_ptr != self._ffi.NULL
                    else None
                )
                message_kind = push_kind_map.get(kind)

                if message_kind == "Disconnection":
                    ClientLogger.log(
                        LogLevel.WARN, "disconnect notification",
                        "Transport disconnected, messages might be lost",
                    )
                elif message_kind in ("Message", "PMessage", "SMessage"):
                    pubsub_msg = PubSubMsg(
                        message=message, channel=channel, pattern=pattern
                    )
                    with self._pubsub_lock:
                        user_callback, context = (
                            self._config._get_pubsub_callback_and_context()
                        )
                        if user_callback:
                            user_callback(pubsub_msg, context)
                        else:
                            self._pending_push_notifications.append(pubsub_msg)
                            self._complete_pubsub_futures_safe()
            except Exception as e:
                ClientLogger.log(
                    LogLevel.ERROR, "pubsub_callback",
                    f"Error in pubsub callback: {e}",
                )

        return _pubsub_callback

    def _complete_pubsub_futures_safe(self):
        """Complete pending pubsub futures with available messages. Must hold _pubsub_lock."""
        loop = self._loop
        while self._pending_push_notifications and self._pubsub_futures:
            msg = self._pending_push_notifications.pop(0)
            fut = self._pubsub_futures.pop(0)
            if not fut.done() and loop and not loop.is_closed():
                loop.call_soon_threadsafe(fut.set_result, msg)

    async def get_pubsub_message(self) -> PubSubMsg:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )
        fut: asyncio.Future = self._loop.create_future()
        with self._pubsub_lock:
            self._pubsub_futures.append(fut)
            self._complete_pubsub_futures_safe()
        return await fut

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )
        with self._pubsub_lock:
            if self._pending_push_notifications:
                return self._pending_push_notifications.pop(0)
            return None


    # ==================== Response Parsing ====================

    def _handle_response(self, message):
        if message == self._ffi.NULL:
            return None
        addr = int(self._ffi.cast("uintptr_t", message))
        result, arena_ptr = _c_parse_response(addr)
        if arena_ptr:
            self._lib.free_response_arena(self._ffi.cast("void*", arena_ptr))
        return result

    def _parse_response_cffi(self, addr):
        """Fallback CFFI-based response parser when C extension is unavailable."""
        msg = self._ffi.cast("struct CommandResponse*", addr)[0]
        return self._handle_command_response(msg)

    def _handle_command_response(self, msg):
        rt = msg.response_type
        if rt == 0:  # Null
            return None
        elif rt == 1:  # Int
            return msg.int_value
        elif rt == 2:  # Float
            return msg.float_value
        elif rt == 3:  # Bool
            return bool(msg.bool_value)
        elif rt == 4:  # String
            return self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
        elif rt == 5:  # Array
            return [
                self._handle_response(
                    self._ffi.cast("struct CommandResponse*", msg.array_value + i)
                )
                for i in range(msg.array_value_len)
            ]
        elif rt == 6:  # Map
            result = {}
            for i in range(msg.array_value_len):
                elem = self._ffi.cast("struct CommandResponse*", msg.array_value + i)
                key = self._handle_response(
                    self._ffi.cast("struct CommandResponse*", elem.map_key)
                )
                val = self._handle_response(
                    self._ffi.cast("struct CommandResponse*", elem.map_value)
                )
                result[key] = val
            return result
        elif rt == 7:  # Sets
            result = set()
            sets_array = self._ffi.cast(
                f"struct CommandResponse[{msg.sets_value_len}]", msg.sets_value
            )
            for i in range(msg.sets_value_len):
                result.add(self._handle_response(sets_array[i]))
            return result
        elif rt == 8:  # Ok
            return OK
        elif rt == 9:  # Error
            error_msg = self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
            raise RequestError(str(error_msg))
        else:
            raise RequestError(f"Unknown response type: {rt}")

    # ==================== FFI Helpers ====================

    @staticmethod
    def _encode_arg(arg):
        return arg.encode(ENCODING) if isinstance(arg, str) else bytes(arg) if isinstance(arg, (bytearray, memoryview)) else arg

    def _to_c_strings(self, args):
        ffi = self._ffi
        encode = self._encode_arg
        buffers = [encode(a) for a in args]
        c_strings = ffi.new("size_t[]", [ffi.cast("size_t", ffi.from_buffer(b)) for b in buffers])
        c_lengths = ffi.new("unsigned long[]", [len(b) for b in buffers])
        return c_strings, c_lengths, buffers

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


    # ==================== Command Execution ====================

    async def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[TEncodable],
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        callback_id = self._get_callback_id()
        fut = self._loop.create_future()

        self._pending_futures[callback_id] = fut

        c_args, c_lengths, buffers = self._to_c_strings(args)

        # OTel span creation only when initialized (rare)
        span = 0
        if OpenTelemetry._instance is not None and OpenTelemetry.should_sample():
            span_name_cstr = self._ffi.new("char[]", RequestType.Name(request_type).encode())
            span = self._lib.create_named_otel_span(span_name_cstr)

        if route is None:
            self._lib.command(
                self._core_client, callback_id, request_type,
                len(args), c_args, c_lengths, self._ffi.NULL, 0, span,
            )
        else:
            route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)
            self._lib.command(
                self._core_client, callback_id, request_type,
                len(args), c_args, c_lengths, route_ptr, route_len, span,
            )

        try:
            return await fut
        finally:
            if span:
                self._lib.drop_otel_span(span)

    async def _execute_batch(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        is_atomic: bool,
        raise_on_error: bool = False,
        retry_server_error: bool = False,
        retry_connection_error: bool = False,
        route: Optional[Route] = None,
        timeout: Optional[int] = None,
    ) -> List[TResult]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        callback_id = self._get_callback_id()
        fut = self._loop.create_future()

        self._pending_futures[callback_id] = fut

        span = 0
        if OpenTelemetry.should_sample():
            span = self._lib.create_batch_otel_span()

        # Build C BatchInfo
        all_refs = []
        cmd_infos = []
        for request_type, args in commands:
            arg_buffers = []
            arg_ptrs = []
            arg_lengths = []
            for arg in args:
                if isinstance(arg, str):
                    arg_bytes = arg.encode(ENCODING)
                elif isinstance(arg, (bytes, bytearray, memoryview)):
                    arg_bytes = bytes(arg)
                else:
                    raise TypeError(f"Unsupported argument type: {type(arg)}")
                arg_buffers.append(arg_bytes)
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
            all_refs.extend(arg_buffers + [c_arg_array, c_lengths])

        cmd_info_array = self._ffi.new("const CmdInfo*[]", cmd_infos)
        all_refs.extend(cmd_infos + [cmd_info_array])

        batch_info = self._ffi.new(
            "BatchInfo*",
            {
                "cmd_count": len(commands),
                "cmds": cmd_info_array,
                "is_atomic": is_atomic,
            },
        )

        # Build batch options
        batch_options = self._ffi.new(
            "BatchOptionsInfo*",
            {
                "retry_server_error": retry_server_error,
                "retry_connection_error": retry_connection_error,
                "has_timeout": timeout is not None,
                "timeout": timeout or 0,
                "route_info": self._ffi.NULL,
            },
        )

        self._lib.batch(
            self._core_client,
            callback_id,
            batch_info,
            raise_on_error,
            batch_options,
            span,
        )

        try:
            return await fut
        finally:
            if span != 0:
                self._lib.drop_otel_span(span)


    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        callback_id = self._get_callback_id()
        fut = self._loop.create_future()

        self._pending_futures[callback_id] = fut

        if keys is None:
            keys = []
        if args is None:
            args = []

        keys_c_args, keys_c_lengths, keys_buffers = self._to_c_strings(keys)
        args_c_args, args_c_lengths, args_buffers = self._to_c_strings(args)

        hash_bytes = hash.encode(ENCODING) + b"\0"
        hash_buffer = self._ffi.from_buffer(hash_bytes)

        route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

        self._lib.invoke_script(
            self._core_client,
            callback_id,
            hash_buffer,
            len(keys),
            keys_c_args,
            keys_c_lengths,
            len(args),
            args_c_args,
            args_c_lengths,
            route_ptr,
            route_len,
        )

        return await fut

    # ==================== Connection Management ====================

    async def _update_connection_password(
        self, password: Optional[str], immediate_auth: bool
    ) -> TResult:
        if self._is_closed:
            raise ClosingError("Client is closed.")

        callback_id = self._get_callback_id()
        fut = self._loop.create_future()

        self._pending_futures[callback_id] = fut

        c_password = (
            self._ffi.new("char[]", password.encode(ENCODING))
            if password is not None
            else self._ffi.new("char[]", b"")
        )

        self._lib.update_connection_password(
            self._core_client,
            callback_id,
            c_password,
            immediate_auth,
        )

        result = await fut
        if result is OK:
            if self._config.credentials is None:
                self._config.credentials = ServerCredentials(password=password or "")
            self._config.credentials.password = password or ""
        return result

    async def _refresh_iam_token(self) -> TResult:
        if self._is_closed:
            raise ClosingError("Client is closed.")

        callback_id = self._get_callback_id()
        fut = self._loop.create_future()

        self._pending_futures[callback_id] = fut

        self._lib.refresh_iam_token(
            self._core_client,
            callback_id,
        )

        return await fut

    async def get_statistics(self) -> dict:
        stats = self._lib.get_statistics()
        return {
            "total_connections": stats.total_connections,
            "total_clients": stats.total_clients,
            "total_values_compressed": stats.total_values_compressed,
            "total_values_decompressed": stats.total_values_decompressed,
            "total_original_bytes": stats.total_original_bytes,
            "total_bytes_compressed": stats.total_bytes_compressed,
            "total_bytes_decompressed": stats.total_bytes_decompressed,
            "compression_skipped_count": stats.compression_skipped_count,
            "subscription_out_of_sync_count": stats.subscription_out_of_sync_count,
            "subscription_last_sync_timestamp": stats.subscription_last_sync_timestamp,
        }

    def _parse_pubsub_state(self, result: TResult, is_cluster: bool):
        if not isinstance(result, list) or len(result) != 4:
            raise RequestError("Invalid response format from GetSubscriptions")

        desired_dict = result[1]
        actual_dict = result[3]

        if is_cluster:
            PubSubChannelModes = GlideClusterClientConfiguration.PubSubChannelModes
            StateClass = GlideClusterClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
                "Sharded": PubSubChannelModes.Sharded,
            }
        else:
            PubSubChannelModes = GlideClientConfiguration.PubSubChannelModes
            StateClass = GlideClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
            }

        desired_subscriptions = {}
        actual_subscriptions = {}

        for key_bytes, value_list in desired_dict.items():
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                desired_subscriptions[mode_map[key]] = values

        for key_bytes, value_list in actual_dict.items():
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                actual_subscriptions[mode_map[key]] = values

        return StateClass(
            desired_subscriptions=desired_subscriptions,
            actual_subscriptions=actual_subscriptions,
        )

    async def close(self, err_message: Optional[str] = None) -> None:
        if not self._is_closed:
            self._is_closed = True
            err_message = "" if err_message is None else err_message

            with self._lock:
                for fut in self._pending_futures.values():
                    if not fut.done():
                        fut.set_exception(ClosingError(err_message))
                self._pending_futures.clear()

            with self._pubsub_lock:
                for fut in self._pubsub_futures:
                    if not fut.done():
                        fut.set_exception(ClosingError(err_message))
                self._pubsub_futures.clear()

            if self._core_client is not None:
                self._lib.close_client(self._core_client)
                self._core_client = None



class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    Use :func:`~BaseClient.create` to request a client.
    For full documentation, see
    [Valkey GLIDE Documentation](https://glide.valkey.io/how-to/client-initialization/#cluster)
    """

    async def _cluster_scan(
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

        callback_id = self._get_callback_id()
        fut = self._loop.create_future()

        self._pending_futures[callback_id] = fut

        # Build scan args
        args = []
        if match is not None:
            encoded_match = match.encode(ENCODING) if isinstance(match, str) else match
            args.extend([b"MATCH", encoded_match])
        if count is not None:
            args.extend([b"COUNT", str(count).encode(ENCODING)])
        if type is not None:
            args.extend([b"TYPE", type.value.encode(ENCODING)])
        if allow_non_covered_slots:
            args.extend([b"ALLOW_NON_COVERED_SLOTS"])

        cursor_string = cursor.get_cursor()
        cursor_bytes = cursor_string.encode(ENCODING) + b"\0"
        cursor_buffer = self._ffi.from_buffer(cursor_bytes)

        if args:
            args_array, args_len_array, arg_buffers = self._to_c_strings(args)
            arg_count = len(args)
        else:
            args_array = self._ffi.NULL
            args_len_array = self._ffi.NULL
            arg_count = 0

        self._lib.request_cluster_scan(
            self._core_client,
            callback_id,
            cursor_buffer,
            arg_count,
            args_array,
            args_len_array,
        )

        response_data = await fut

        if not isinstance(response_data, list) or len(response_data) != 2:
            raise RequestError("Unexpected cluster scan response format")

        new_cursor = response_data[0]
        if isinstance(new_cursor, bytes):
            new_cursor = new_cursor.decode(ENCODING)

        keys_list = response_data[1] if response_data[1] is not None else []
        return [ClusterScanCursor(new_cursor), keys_list]

    async def get_subscriptions(
        self,
    ) -> GlideClusterClientConfiguration.PubSubState:
        result = await self._execute_command(RequestType.GetSubscriptions, [])
        return cast(
            GlideClusterClientConfiguration.PubSubState,
            self._parse_pubsub_state(result, is_cluster=True),
        )


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    Use :func:`~BaseClient.create` to request a client.
    For full documentation, see
    [Valkey GLIDE Documentation](https://glide.valkey.io/how-to/client-initialization/#standalone)
    """

    async def get_subscriptions(
        self,
    ) -> GlideClientConfiguration.PubSubState:
        result = await self._execute_command(RequestType.GetSubscriptions, [])
        return cast(
            GlideClientConfiguration.PubSubState,
            self._parse_pubsub_state(result, is_cluster=False),
        )


TGlideClient = Union[GlideClient, GlideClusterClient]
