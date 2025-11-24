# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import sys
import threading
from typing import (
    TYPE_CHECKING,
    Any,
    Awaitable,
    Dict,
    List,
    Optional,
    Set,
    Tuple,
    Union,
    cast,
)

import anyio
import sniffio
from anyio import to_thread
from glide.glide import (
    DEFAULT_TIMEOUT_IN_MILLISECONDS,
    MAX_REQUEST_ARGS_LEN,
    ClusterScanCursor,
    create_leaked_bytes_vec,
    create_otel_span,
    drop_otel_span,
    get_statistics,
    start_socket_listener_external,
    value_from_pointer,
)
from glide_shared.commands.command_args import ObjectType
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import BaseClientConfiguration, ServerCredentials
from glide_shared.constants import (
    DEFAULT_READ_BYTES_SIZE,
    OK,
    TEncodable,
    TRequest,
    TResult,
)
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    ConnectionError,
    get_request_error_class,
)
from glide_shared.protobuf.command_request_pb2 import (
    Command,
    CommandRequest,
    RefreshIamToken,
    RequestType,
)
from glide_shared.protobuf.connection_request_pb2 import ConnectionRequest
from glide_shared.protobuf.response_pb2 import Response
from glide_shared.protobuf_codec import PartialMessageException, ProtobufCodec
from glide_shared.routes import Route, set_protobuf_route

from .async_commands.cluster_commands import ClusterCommands
from .async_commands.core import CoreCommands
from .async_commands.standalone_commands import StandaloneCommands
from .logger import Level as LogLevel
from .logger import Logger as ClientLogger
from .opentelemetry import OpenTelemetry

if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self

if TYPE_CHECKING:
    import asyncio

    import trio

    TTask = Union[asyncio.Task[None], trio.lowlevel.Task]
    TFuture = Union[asyncio.Future[Any], "_CompatFuture"]


class _CompatFuture:
    """anyio shim for asyncio.Future-like functionality"""

    def __init__(self) -> None:
        self._is_done = anyio.Event()
        self._result: Any = None
        self._exception: Optional[Exception] = None

    def set_result(self, result: Any) -> None:
        self._result = result
        self._is_done.set()

    def set_exception(self, exception: Exception) -> None:
        self._exception = exception
        self._is_done.set()

    def done(self) -> bool:
        return self._is_done.is_set()

    def __await__(self):
        return self._is_done.wait().__await__()

    def result(self) -> Any:
        if self._exception:
            raise self._exception

        return self._result


def _get_new_future_instance() -> "TFuture":
    if sniffio.current_async_library() == "asyncio":
        import asyncio

        return asyncio.get_running_loop().create_future()

    # _CompatFuture is also compatible with asyncio, but is not as closely integrated
    # into the asyncio event loop and thus introduces a noticeable performance
    # degradation. so we only use it for trio
    return _CompatFuture()


class BaseClient(CoreCommands):
    def __init__(self, config: BaseClientConfiguration):
        """
        To create a new client, use the `create` classmethod
        """
        self.config: BaseClientConfiguration = config
        self._available_futures: Dict[int, "TFuture"] = {}
        self._available_callback_indexes: List[int] = list()
        self._buffered_requests: List[TRequest] = list()
        self._writer_lock = threading.Lock()
        self.socket_path: Optional[str] = None
        self._reader_task: Optional["TTask"] = None
        self._is_closed: bool = False
        self._pubsub_futures: List["TFuture"] = []
        self._pubsub_lock = threading.Lock()
        self._pending_push_notifications: List[Response] = list()

        self._pending_tasks: Optional[Set[Awaitable[None]]] = None
        """asyncio-only to avoid gc on pending write tasks"""

    def _create_task(self, task, *args, **kwargs):
        """framework agnostic free-floating task shim"""
        framework = sniffio.current_async_library()
        if framework == "trio":
            from functools import partial

            import trio

            return trio.lowlevel.spawn_system_task(partial(task, **kwargs), *args)
        elif framework == "asyncio":
            import asyncio

            # the asyncio event loop holds weak refs to tasks, so it's recommended to
            # hold strong refs to them during their lifetime to prevent garbage
            # collection
            t = asyncio.create_task(task(*args, **kwargs))

            if self._pending_tasks is None:
                self._pending_tasks = set()

            self._pending_tasks.add(t)
            t.add_done_callback(self._pending_tasks.discard)

            return t

        raise RuntimeError(f"Unsupported async framework {framework}")

    @classmethod
    async def create(cls, config: BaseClientConfiguration) -> Self:
        """Creates a Glide client.

        Args:
            config (ClientConfiguration): The configuration options for the client, including cluster addresses,
            authentication credentials, TLS settings, periodic checks, and Pub/Sub subscriptions.

        Returns:
            Self: A promise that resolves to a connected client instance.

        Examples:
            # Connecting to a Standalone Server
            >>> from glide import GlideClientConfiguration, NodeAddress, GlideClient, ServerCredentials, BackoffStrategy
            >>> config = GlideClientConfiguration(
            ...     [
            ...         NodeAddress('primary.example.com', 6379),
            ...         NodeAddress('replica1.example.com', 6379),
            ...     ],
            ...     use_tls = True,
            ...     database_id = 1,
            ...     credentials = ServerCredentials(username = 'user1', password = 'passwordA'),
            ...     reconnect_strategy = BackoffStrategy(num_of_retries = 5, factor = 1000, exponent_base = 2),
            ...     pubsub_subscriptions = GlideClientConfiguration.PubSubSubscriptions(
            ...         channels_and_patterns = {GlideClientConfiguration.PubSubChannelModes.Exact: {'updates'}},
            ...         callback = lambda message,context : print(message),
            ...     ),
            ... )
            >>> client = await GlideClient.create(config)

            # Connecting to a Cluster
            >>> from glide import GlideClusterClientConfiguration, NodeAddress, GlideClusterClient,
            ... PeriodicChecksManualInterval
            >>> config = GlideClusterClientConfiguration(
            ...     [
            ...         NodeAddress('address1.example.com', 6379),
            ...         NodeAddress('address2.example.com', 6379),
            ...     ],
            ...     use_tls = True,
            ...     periodic_checks = PeriodicChecksManualInterval(duration_in_sec = 30),
            ...     credentials = ServerCredentials(username = 'user1', password = 'passwordA'),
            ...     reconnect_strategy = BackoffStrategy(num_of_retries = 5, factor = 1000, exponent_base = 2),
            ...     pubsub_subscriptions = GlideClusterClientConfiguration.PubSubSubscriptions(
            ...         channels_and_patterns = {
            ...             GlideClusterClientConfiguration.PubSubChannelModes.Exact: {'updates'},
            ...             GlideClusterClientConfiguration.PubSubChannelModes.Sharded: {'sharded_channel'},
            ...         },
            ...         callback = lambda message,context : print(message),
            ...     ),
            ... )
            >>> client = await GlideClusterClient.create(config)

        Remarks:
            Use this static method to create and connect a client to a Valkey server.
            The client will automatically handle connection establishment, including cluster topology discovery and
            handling of authentication and TLS configurations.

                - **Cluster Topology Discovery**: The client will automatically discover the cluster topology based
                  on the seed addresses provided.
                - **Authentication**: If `ServerCredentials` are provided, the client will attempt to authenticate
                  using the specified username and password.
                - **TLS**: If `use_tls` is set to `true`, the client will establish secure connections using TLS.
                - **Periodic Checks**: The `periodic_checks` setting allows you to configure how often the client
                  checks for cluster topology changes.
                - **Reconnection Strategy**: The `BackoffStrategy` settings define how the client will attempt to
                  reconnect in case of disconnections.
                - **Pub/Sub Subscriptions**: Any channels or patterns specified in `PubSubSubscriptions` will be
                  subscribed to upon connection.

        """
        config = config
        self = cls(config)

        init_event: threading.Event = threading.Event()

        def init_callback(socket_path: Optional[str], err: Optional[str]):
            if err is not None:
                raise ClosingError(err)
            elif socket_path is None:
                raise ClosingError(
                    "Socket initialization error: Missing valid socket path."
                )
            else:
                # Received socket path
                self.socket_path = socket_path
                init_event.set()

        start_socket_listener_external(init_callback=init_callback)

        # will log if the logger was created (wrapper or costumer) on info
        # level or higher
        ClientLogger.log(LogLevel.INFO, "connection info", "new connection established")
        # Wait for the socket listener to complete its initialization
        await to_thread.run_sync(init_event.wait)
        # Create UDS connection
        await self._create_uds_connection()

        # Start the reader loop as a background task
        self._reader_task = self._create_task(self._reader_loop)

        # Set the client configurations
        await self._set_connection_configurations()

        return self

    async def _create_uds_connection(self) -> None:
        try:
            # Open an UDS connection
            with anyio.fail_after(DEFAULT_TIMEOUT_IN_MILLISECONDS):
                self._stream = await anyio.connect_unix(
                    path=cast(str, self.socket_path)
                )
        except Exception as e:
            raise ClosingError("Failed to create UDS connection") from e

    async def close(self, err_message: Optional[str] = None) -> None:
        """
        Terminate the client by closing all associated resources, including the socket and any active futures.
        All open futures will be closed with an exception.

        Args:
            err_message (Optional[str]): If not None, this error message will be passed along with the exceptions when
            closing all open futures.
            Defaults to None.
        """
        if not self._is_closed:
            self._is_closed = True
            err_message = "" if err_message is None else err_message
            for response_future in self._available_futures.values():
                if not response_future.done():
                    response_future.set_exception(ClosingError(err_message))
            try:
                self._pubsub_lock.acquire()
                for pubsub_future in self._pubsub_futures:
                    if not pubsub_future.done():
                        pubsub_future.set_exception(ClosingError(err_message))
            finally:
                self._pubsub_lock.release()

            await self._stream.aclose()

    def _get_future(self, callback_idx: int) -> "TFuture":
        response_future: "TFuture" = _get_new_future_instance()
        self._available_futures.update({callback_idx: response_future})
        return response_future

    def _get_protobuf_conn_request(self) -> ConnectionRequest:
        return self.config._create_a_protobuf_conn_request()

    async def _set_connection_configurations(self) -> None:
        conn_request = self._get_protobuf_conn_request()
        response_future: "TFuture" = self._get_future(0)
        self._create_write_task(conn_request)
        await response_future
        res = response_future.result()
        if res is not OK:
            raise ClosingError(res)

    def _create_write_task(self, request: TRequest):
        self._create_task(self._write_or_buffer_request, request)

    async def _write_or_buffer_request(self, request: TRequest):
        self._buffered_requests.append(request)
        if self._writer_lock.acquire(False):
            try:
                while len(self._buffered_requests) > 0:
                    await self._write_buffered_requests_to_socket()
            except Exception as e:
                # trio system tasks cannot raise exceptions, so gracefully propagate
                # any error to the pending future instead
                callback_idx = (
                    request.callback_idx if isinstance(request, CommandRequest) else 0
                )
                res_future = self._available_futures.pop(callback_idx, None)
                if res_future and not res_future.done():
                    res_future.set_exception(e)
                else:
                    ClientLogger.log(
                        LogLevel.WARN,
                        "unhandled response error",
                        f"Unhandled response error for unknown request: {callback_idx}",
                    )
            finally:
                self._writer_lock.release()

    async def _write_buffered_requests_to_socket(self) -> None:
        requests = self._buffered_requests
        self._buffered_requests = list()
        b_arr = bytearray()
        for request in requests:
            ProtobufCodec.encode_delimited(b_arr, request)
        try:
            await self._stream.send(b_arr)
        except (anyio.ClosedResourceError, anyio.EndOfStream):
            raise ClosingError("The communication layer was unexpectedly closed.")

    def _encode_arg(self, arg: TEncodable) -> bytes:
        """
        Converts a string argument to bytes.

        Args:
            arg (str): An encodable argument.

        Returns:
            bytes: The encoded argument as bytes.
        """
        if isinstance(arg, str):
            # TODO: Allow passing different encoding options
            return bytes(arg, encoding="utf8")
        return arg

    def _encode_and_sum_size(
        self,
        args_list: Optional[List[TEncodable]],
    ) -> Tuple[List[bytes], int]:
        """
        Encodes the list and calculates the total memory size.

        Args:
            args_list (Optional[List[TEncodable]]): A list of strings to be converted to bytes.
                                                           If None or empty, returns ([], 0).

        Returns:
            int: The total memory size of the encoded arguments in bytes.
        """
        args_size = 0
        encoded_args_list: List[bytes] = []
        if not args_list:
            return (encoded_args_list, args_size)
        for arg in args_list:
            encoded_arg = self._encode_arg(arg) if isinstance(arg, str) else arg
            encoded_args_list.append(encoded_arg)
            args_size += len(encoded_arg)
        return (encoded_args_list, args_size)

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

        # Create span if OpenTelemetry is configured and sampling indicates we should trace
        span = None
        if OpenTelemetry.should_sample():
            command_name = RequestType.Name(request_type)
            span = create_otel_span(command_name)

        request = CommandRequest()
        request.callback_idx = self._get_callback_index()
        request.single_command.request_type = request_type
        request.single_command.args_array.args[:] = [
            bytes(elem, encoding="utf8") if isinstance(elem, str) else elem
            for elem in args
        ]
        (encoded_args, args_size) = self._encode_and_sum_size(args)
        if args_size < MAX_REQUEST_ARGS_LEN:
            request.single_command.args_array.args[:] = encoded_args
        else:
            request.single_command.args_vec_pointer = create_leaked_bytes_vec(
                encoded_args
            )

        # Add span pointer to request if span was created
        if span:
            request.root_span_ptr = span

        set_protobuf_route(request, route)
        return await self._write_request_await_response(request)

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

        # Create span if OpenTelemetry is configured and sampling indicates we should trace
        span = None

        if OpenTelemetry.should_sample():
            # Use "Batch" as span name for batches
            span = create_otel_span("Batch")

        request = CommandRequest()
        request.callback_idx = self._get_callback_index()
        batch_commands = []
        for requst_type, args in commands:
            command = Command()
            command.request_type = requst_type
            # For now, we allow the user to pass the command as array of strings
            # we convert them here into bytes (the datatype that our rust core expects)
            (encoded_args, args_size) = self._encode_and_sum_size(args)
            if args_size < MAX_REQUEST_ARGS_LEN:
                command.args_array.args[:] = encoded_args
            else:
                command.args_vec_pointer = create_leaked_bytes_vec(encoded_args)
            batch_commands.append(command)
        request.batch.commands.extend(batch_commands)
        request.batch.is_atomic = is_atomic
        request.batch.raise_on_error = raise_on_error
        if timeout is not None:
            request.batch.timeout = timeout
        request.batch.retry_server_error = retry_server_error
        request.batch.retry_connection_error = retry_connection_error

        # Add span pointer to request if span was created
        if span:
            request.root_span_ptr = span

        set_protobuf_route(request, route)
        return await self._write_request_await_response(request)

    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[Union[str, bytes]]] = None,
        args: Optional[List[Union[str, bytes]]] = None,
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        request = CommandRequest()
        request.callback_idx = self._get_callback_index()
        (encoded_keys, keys_size) = self._encode_and_sum_size(keys)
        (encoded_args, args_size) = self._encode_and_sum_size(args)
        if (keys_size + args_size) < MAX_REQUEST_ARGS_LEN:
            request.script_invocation.hash = hash
            request.script_invocation.keys[:] = encoded_keys
            request.script_invocation.args[:] = encoded_args

        else:
            request.script_invocation_pointers.hash = hash
            request.script_invocation_pointers.keys_pointer = create_leaked_bytes_vec(
                encoded_keys
            )
            request.script_invocation_pointers.args_pointer = create_leaked_bytes_vec(
                encoded_args
            )
        set_protobuf_route(request, route)
        return await self._write_request_await_response(request)

    async def get_pubsub_message(self) -> PubSubMsg:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if not self.config._is_pubsub_configured():
            raise ConfigurationError(
                "The operation will never complete since there was no pubsub subscriptions applied to the client."
            )

        if self.config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )

        # locking might not be required
        response_future: "TFuture" = _get_new_future_instance()
        try:
            self._pubsub_lock.acquire()
            self._pubsub_futures.append(response_future)
            self._complete_pubsub_futures_safe()
        finally:
            self._pubsub_lock.release()
        await response_future
        return response_future.result()

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if not self.config._is_pubsub_configured():
            raise ConfigurationError(
                "The operation will never succeed since there was no pubsbub subscriptions applied to the client."
            )

        if self.config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never succeed since messages will be passed to the configured callback."
            )

        # locking might not be required
        msg: Optional[PubSubMsg] = None
        try:
            self._pubsub_lock.acquire()
            self._complete_pubsub_futures_safe()
            while len(self._pending_push_notifications) and not msg:
                push_notification = self._pending_push_notifications.pop(0)
                msg = self._notification_to_pubsub_message_safe(push_notification)
        finally:
            self._pubsub_lock.release()
        return msg

    def _cancel_pubsub_futures_with_exception_safe(self, exception: ConnectionError):
        while len(self._pubsub_futures):
            next_future = self._pubsub_futures.pop(0)
            next_future.set_exception(exception)

    def _notification_to_pubsub_message_safe(
        self, response: Response
    ) -> Optional[PubSubMsg]:
        pubsub_message = None
        push_notification = cast(
            Dict[str, Any], value_from_pointer(response.resp_pointer)
        )
        message_kind = push_notification["kind"]
        if message_kind == "Disconnection":
            ClientLogger.log(
                LogLevel.WARN,
                "disconnect notification",
                "Transport disconnected, messages might be lost",
            )
        elif (
            message_kind == "Message"
            or message_kind == "PMessage"
            or message_kind == "SMessage"
        ):
            values: List = push_notification["values"]
            if message_kind == "PMessage":
                pubsub_message = PubSubMsg(
                    message=values[2], channel=values[1], pattern=values[0]
                )
            else:
                pubsub_message = PubSubMsg(
                    message=values[1], channel=values[0], pattern=None
                )
        elif (
            message_kind == "PSubscribe"
            or message_kind == "Subscribe"
            or message_kind == "SSubscribe"
            or message_kind == "Unsubscribe"
            or message_kind == "PUnsubscribe"
            or message_kind == "SUnsubscribe"
        ):
            pass
        else:
            ClientLogger.log(
                LogLevel.WARN,
                "unknown notification",
                f"Unknown notification message: '{message_kind}'",
            )

        return pubsub_message

    def _complete_pubsub_futures_safe(self):
        while len(self._pending_push_notifications) and len(self._pubsub_futures):
            next_push_notification = self._pending_push_notifications.pop(0)
            pubsub_message = self._notification_to_pubsub_message_safe(
                next_push_notification
            )
            if pubsub_message:
                self._pubsub_futures.pop(0).set_result(pubsub_message)

    async def _write_request_await_response(self, request: CommandRequest):
        # Create a response future for this request and add it to the available
        # futures map
        response_future = self._get_future(request.callback_idx)
        self._create_write_task(request)
        await response_future
        return response_future.result()

    def _get_callback_index(self) -> int:
        try:
            return self._available_callback_indexes.pop()
        except IndexError:
            # The list is empty
            return len(self._available_futures)

    async def _process_response(self, response: Response) -> None:
        res_future = self._available_futures.pop(response.callback_idx, None)
        if not res_future or response.HasField("closing_error"):
            err_msg = (
                response.closing_error
                if response.HasField("closing_error")
                else f"Client Error - closing due to unknown error. callback index:  {response.callback_idx}"
            )
            exc = ClosingError(err_msg)
            if res_future is not None:
                res_future.set_exception(exc)
            else:
                ClientLogger.log(
                    LogLevel.WARN,
                    "unhandled response error",
                    f"Unhandled response error for unknown request: {response.callback_idx}",
                )
            raise exc
        else:
            self._available_callback_indexes.append(response.callback_idx)
            if response.HasField("request_error"):
                error_type = get_request_error_class(response.request_error.type)
                res_future.set_exception(error_type(response.request_error.message))
            elif response.HasField("resp_pointer"):
                res_future.set_result(value_from_pointer(response.resp_pointer))
            elif response.HasField("constant_response"):
                res_future.set_result(OK)
            else:
                res_future.set_result(None)

        # Clean up span if it was created
        if response.HasField("root_span_ptr"):
            drop_otel_span(response.root_span_ptr)

    async def _process_push(self, response: Response) -> None:
        if response.HasField("closing_error") or not response.HasField("resp_pointer"):
            err_msg = (
                response.closing_error
                if response.HasField("closing_error")
                else "Client Error - push notification without resp_pointer"
            )
            raise ClosingError(err_msg)
        try:
            self._pubsub_lock.acquire()
            callback, context = self.config._get_pubsub_callback_and_context()
            if callback:
                pubsub_message = self._notification_to_pubsub_message_safe(response)
                if pubsub_message:
                    callback(pubsub_message, context)
            else:
                self._pending_push_notifications.append(response)
                self._complete_pubsub_futures_safe()
        finally:
            self._pubsub_lock.release()

    async def _reader_loop(self) -> None:
        # Socket reader loop
        try:
            remaining_read_bytes = bytearray()
            while True:
                try:
                    read_bytes = await self._stream.receive(DEFAULT_READ_BYTES_SIZE)
                except (anyio.ClosedResourceError, anyio.EndOfStream):
                    raise ClosingError(
                        "The communication layer was unexpectedly closed."
                    )
                read_bytes = remaining_read_bytes + bytearray(read_bytes)
                read_bytes_view = memoryview(read_bytes)
                offset = 0
                while offset <= len(read_bytes):
                    try:
                        response, offset = ProtobufCodec.decode_delimited(
                            read_bytes, read_bytes_view, offset, Response
                        )
                    except PartialMessageException:
                        # Received only partial response, break the inner loop
                        remaining_read_bytes = read_bytes[offset:]
                        break
                    response = cast(Response, response)
                    if response.is_push:
                        await self._process_push(response=response)
                    else:
                        await self._process_response(response=response)
        except Exception as e:
            # close and stop reading at terminal exceptions from incoming responses or
            # stream closures
            await self.close(str(e))

    async def get_statistics(self) -> dict:
        return get_statistics()

    async def _update_connection_password(
        self, password: Optional[str], immediate_auth: bool
    ) -> TResult:
        request = CommandRequest()
        request.callback_idx = self._get_callback_index()
        if password is not None:
            request.update_connection_password.password = password
        request.update_connection_password.immediate_auth = immediate_auth
        response = await self._write_request_await_response(request)
        # Update the client binding side password if managed to change core configuration password
        if response is OK:
            if self.config.credentials is None:
                self.config.credentials = ServerCredentials(password=password or "")
                self.config.credentials.password = password or ""
        return response

    async def _refresh_iam_token(self) -> TResult:
        request = CommandRequest()
        request.callback_idx = self._get_callback_index()
        request.refresh_iam_token.CopyFrom(
            RefreshIamToken()
        )  # Empty message, just triggers the refresh
        response = await self._write_request_await_response(request)
        return response


class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    Use :func:`~BaseClient.create` to request a client.
    For full documentation, see
    [Valkey GLIDE Wiki](https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper#cluster)
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
        request = CommandRequest()
        request.callback_idx = self._get_callback_index()
        # Take out the id string from the wrapping object
        cursor_string = cursor.get_cursor()
        request.cluster_scan.cursor = cursor_string
        request.cluster_scan.allow_non_covered_slots = allow_non_covered_slots
        if match is not None:
            request.cluster_scan.match_pattern = (
                self._encode_arg(match) if isinstance(match, str) else match
            )
        if count is not None:
            request.cluster_scan.count = count
        if type is not None:
            request.cluster_scan.object_type = type.value
        response = await self._write_request_await_response(request)
        return [ClusterScanCursor(bytes(response[0]).decode()), response[1]]

    def _get_protobuf_conn_request(self) -> ConnectionRequest:
        return self.config._create_a_protobuf_conn_request(cluster_mode=True)


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    Use :func:`~BaseClient.create` to request a client.
    For full documentation, see
    [Valkey GLIDE Wiki](https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper#standalone)
    """


TGlideClient = Union[GlideClient, GlideClusterClient]
