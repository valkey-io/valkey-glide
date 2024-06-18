# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import threading
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple, Type, Union, cast

import async_timeout
from glide.async_commands.cluster_commands import ClusterCommands
from glide.async_commands.core import CoreCommands
from glide.async_commands.standalone_commands import StandaloneCommands
from glide.config import BaseClientConfiguration
from glide.constants import DEFAULT_READ_BYTES_SIZE, OK, TRequest, TResult
from glide.exceptions import (
    ClosingError,
    ConnectionError,
    ExecAbortError,
    RequestError,
    TimeoutError,
)
from glide.logger import Level as LogLevel
from glide.logger import Logger as ClientLogger
from glide.protobuf.connection_request_pb2 import ConnectionRequest
from glide.protobuf.redis_request_pb2 import Command, RedisRequest, RequestType
from glide.protobuf.response_pb2 import RequestErrorType, Response
from glide.protobuf_codec import PartialMessageException, ProtobufCodec
from glide.routes import Route, set_protobuf_route
from typing_extensions import Self

from .glide import (
    DEFAULT_TIMEOUT_IN_MILLISECONDS,
    start_socket_listener_external,
    value_from_pointer,
)


def get_request_error_class(
    error_type: Optional[RequestErrorType.ValueType],
) -> Type[RequestError]:
    if error_type == RequestErrorType.Disconnect:
        return ConnectionError
    if error_type == RequestErrorType.ExecAbort:
        return ExecAbortError
    if error_type == RequestErrorType.Timeout:
        return TimeoutError
    if error_type == RequestErrorType.Unspecified:
        return RequestError
    return RequestError


class BaseRedisClient(CoreCommands):
    def __init__(self, config: BaseClientConfiguration):
        """
        To create a new client, use the `create` classmethod
        """
        self.config: BaseClientConfiguration = config
        self._available_futures: dict[int, asyncio.Future] = {}
        self._available_callback_indexes: List[int] = list()
        self._buffered_requests: List[TRequest] = list()
        self._writer_lock = threading.Lock()
        self.socket_path: Optional[str] = None
        self._reader_task: Optional[asyncio.Task] = None
        self._is_closed: bool = False
        self._pubsub_futures: List[asyncio.Future] = []
        self._pubsub_lock = threading.Lock()
        self._pending_push_notifications: List[Response] = list()

    @classmethod
    async def create(cls, config: BaseClientConfiguration) -> Self:
        """Creates a Redis client.

        Args:
            config (ClientConfiguration): The client configurations.
                If no configuration is provided, a default client to "localhost":6379 will be created.

        Returns:
            Self: a Redis Client instance.
        """
        config = config
        self = cls(config)
        init_future: asyncio.Future = asyncio.Future()
        loop = asyncio.get_event_loop()

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
                loop.call_soon_threadsafe(init_future.set_result, True)

        start_socket_listener_external(init_callback=init_callback)

        # will log if the logger was created (wrapper or costumer) on info
        # level or higher
        ClientLogger.log(LogLevel.INFO, "connection info", "new connection established")
        # Wait for the socket listener to complete its initialization
        await init_future
        # Create UDS connection
        await self._create_uds_connection()
        # Start the reader loop as a background task
        self._reader_task = asyncio.create_task(self._reader_loop())
        # Set the client configurations
        await self._set_connection_configurations()
        return self

    async def _create_uds_connection(self) -> None:
        try:
            # Open an UDS connection
            async with async_timeout.timeout(DEFAULT_TIMEOUT_IN_MILLISECONDS):
                reader, writer = await asyncio.open_unix_connection(
                    path=self.socket_path
                )
            self._reader = reader
            self._writer = writer
        except Exception as e:
            await self.close(f"Failed to create UDS connection: {e}")
            raise

    def __del__(self) -> None:
        try:
            if self._reader_task:
                self._reader_task.cancel()
        except RuntimeError as e:
            if "no running event loop" in str(e):
                # event loop already closed
                pass

    async def close(self, err_message: Optional[str] = None) -> None:
        """
        Terminate the client by closing all associated resources, including the socket and any active futures.
        All open futures will be closed with an exception.

        Args:
            err_message (Optional[str]): If not None, this error message will be passed along with the exceptions when closing all open futures.
            Defaults to None.
        """
        self._is_closed = True
        for response_future in self._available_futures.values():
            if not response_future.done():
                err_message = "" if err_message is None else err_message
                response_future.set_exception(ClosingError(err_message))
        try:
            self._pubsub_lock.acquire()
            for pubsub_future in self._pubsub_futures:
                if not response_future.done() and not pubsub_future.cancelled():
                    pubsub_future.set_exception(ClosingError(""))
        finally:
            self._pubsub_lock.release()

        self._writer.close()
        await self._writer.wait_closed()
        self.__del__()

    def _get_future(self, callback_idx: int) -> asyncio.Future:
        response_future: asyncio.Future = asyncio.Future()
        self._available_futures.update({callback_idx: response_future})
        return response_future

    def _get_protobuf_conn_request(self) -> ConnectionRequest:
        return self.config._create_a_protobuf_conn_request()

    async def _set_connection_configurations(self) -> None:
        conn_request = self._get_protobuf_conn_request()
        response_future: asyncio.Future = self._get_future(0)
        await self._write_or_buffer_request(conn_request)
        await response_future
        if response_future.result() is not OK:
            raise ClosingError(response_future.result())

    def _create_write_task(self, request: TRequest):
        asyncio.create_task(self._write_or_buffer_request(request))

    async def _write_or_buffer_request(self, request: TRequest):
        self._buffered_requests.append(request)
        if self._writer_lock.acquire(False):
            try:
                while len(self._buffered_requests) > 0:
                    await self._write_buffered_requests_to_socket()

            finally:
                self._writer_lock.release()

    async def _write_buffered_requests_to_socket(self) -> None:
        requests = self._buffered_requests
        self._buffered_requests = list()
        b_arr = bytearray()
        for request in requests:
            ProtobufCodec.encode_delimited(b_arr, request)
        self._writer.write(b_arr)
        await self._writer.drain()

    async def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[str],
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        request = RedisRequest()
        request.callback_idx = self._get_callback_index()
        request.single_command.request_type = request_type
        request.single_command.args_array.args[:] = [
            bytes(elem, encoding="utf8") for elem in args
        ]  # TODO - use arg pointer
        set_protobuf_route(request, route)
        return await self._write_request_await_response(request)

    async def _execute_transaction(
        self,
        commands: List[Tuple[RequestType.ValueType, List[str]]],
        route: Optional[Route] = None,
    ) -> List[TResult]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        request = RedisRequest()
        request.callback_idx = self._get_callback_index()
        transaction_commands = []
        for requst_type, args in commands:
            command = Command()
            command.request_type = requst_type
            # For now, we allow the user to pass the command as array of strings
            # we convert them here into bytearray (the datatype that our rust core expects)
            command.args_array.args[:] = [bytes(elem, encoding="utf8") for elem in args]
            transaction_commands.append(command)
        request.transaction.commands.extend(transaction_commands)
        set_protobuf_route(request, route)
        return await self._write_request_await_response(request)

    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[str]] = None,
        args: Optional[List[str]] = None,
        route: Optional[Route] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        request = RedisRequest()
        request.callback_idx = self._get_callback_index()
        request.script_invocation.hash = hash
        request.script_invocation.args[:] = args if args is not None else []
        request.script_invocation.keys[:] = keys if keys is not None else []
        set_protobuf_route(request, route)
        return await self._write_request_await_response(request)

    @dataclass
    class PubSubMsg:
        message: str
        channel: str
        pattern: Optional[str]

    async def get_pubsub_message(self) -> PubSubMsg:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        # locking might not be required
        response_future: asyncio.Future = asyncio.Future()
        try:
            self._pubsub_lock.acquire()
            self._pubsub_futures.append(response_future)
            self._push_pubsub_messages_safe()
        finally:
            self._pubsub_lock.release()
        return await response_future

    def _cancel_pubsub_futures_with_exception_safe(self, exception: ConnectionError):
        while len(self._pubsub_futures):
            next_future = self._pubsub_futures.pop(0)
            if not next_future.cancelled():
                next_future.set_exception(exception)

    def _push_pubsub_messages_safe(self):
        while len(self._pending_push_notifications) and len(self._pubsub_futures):
            next_push_notification = self._pending_push_notifications.pop(0)
            next_push_notification: Dict = value_from_pointer(
                next_push_notification.resp_pointer
            )
            message_kind = next_push_notification["kind"]
            if message_kind == "Disconnect":
                # cancel all futures since we dont know how many (if any) messages wont arrive
                # TODO: consider cancelling a single future
                self._cancel_pubsub_futures_with_exception_safe(
                    ConnectionError(
                        "Warning, transport disconnect occured, messages might be lost"
                    )
                )
            elif (
                message_kind == "Message"
                or message_kind == "PMessage"
                or message_kind == "SMessage"
            ):
                next_future = self._pubsub_futures.pop(0)
                values: List = next_push_notification["values"]
                if message_kind == "PMessage":
                    msg = BaseRedisClient.PubSubMsg(
                        message=values[2], channel=values[1], pattern=values[0]
                    )
                else:
                    msg = BaseRedisClient.PubSubMsg(
                        message=values[1], channel=values[0], pattern=None
                    )
                next_future.set_result(msg)
            elif (
                message_kind == "PSubscribe"
                or message_kind == "Subscribe"
                or message_kind == "SSubscribe"
                or message_kind == "Unsubscribe"
            ):
                pass
            else:
                err_msg = f"Unsupported push message: '{message_kind}'"
                ClientLogger.log(LogLevel.ERROR, "pubsub message", err_msg)
                # cancel all futures since its a serious
                # TODO: consider cancelling a single future
                self._cancel_pubsub_futures_with_exception_safe(
                    ConnectionError(err_msg)
                )

    async def _write_request_await_response(self, request: RedisRequest):
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
            if res_future is not None:
                res_future.set_exception(ClosingError(err_msg))
            await self.close(err_msg)
            raise ClosingError(err_msg)
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

    async def _process_push(self, response: Response) -> None:
        if response.HasField("closing_error") or not response.HasField("resp_pointer"):
            err_msg = (
                response.closing_error
                if response.HasField("closing_error")
                else "Client Error - push notification without resp_pointer"
            )
            await self.close(err_msg)
            raise ClosingError(err_msg)

        try:
            self._pubsub_lock.acquire()
            self._pending_push_notifications.append(response)
            self._push_pubsub_messages_safe()
        finally:
            self._pubsub_lock.release()

    async def _reader_loop(self) -> None:
        # Socket reader loop
        remaining_read_bytes = bytearray()
        while True:
            read_bytes = await self._reader.read(DEFAULT_READ_BYTES_SIZE)
            if len(read_bytes) == 0:
                err_msg = "The communication layer was unexpectedly closed."
                await self.close(err_msg)
                raise ClosingError(err_msg)
            read_bytes = remaining_read_bytes + bytearray(read_bytes)
            read_bytes_view = memoryview(read_bytes)
            offset = 0
            while offset <= len(read_bytes):
                try:
                    response, offset = ProtobufCodec.decode_delimited(
                        read_bytes, read_bytes_view, offset, Response
                    )
                except PartialMessageException:
                    # Recieved only partial response, break the inner loop
                    remaining_read_bytes = read_bytes[offset:]
                    break
                response = cast(Response, response)
                if response.is_push:
                    await self._process_push(response=response)
                else:
                    await self._process_response(response=response)


class RedisClusterClient(BaseRedisClient, ClusterCommands):
    """
    Client used for connection to cluster Redis servers.
    For full documentation, see
    https://github.com/aws/babushka/wiki/Python-wrapper#redis-cluster
    """

    def _get_protobuf_conn_request(self) -> ConnectionRequest:
        return self.config._create_a_protobuf_conn_request(cluster_mode=True)


class RedisClient(BaseRedisClient, StandaloneCommands):
    """
    Client used for connection to standalone Redis servers.
    For full documentation, see
    https://github.com/aws/babushka/wiki/Python-wrapper#redis-standalone
    """

    pass


TRedisClient = Union[RedisClient, RedisClusterClient]
