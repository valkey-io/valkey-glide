import asyncio
from typing import Awaitable, Optional, Type

import async_timeout
from pybushka.commands.core import CoreCommands
from pybushka.config import ClientConfiguration
from pybushka.Logger import Level as LogLevel
from pybushka.Logger import Logger
from pybushka.utils import to_url

from .pybushka import (
    HEADER_LENGTH_IN_BYTES,
    PyRequestType,
    PyResponseType,
    start_socket_listener_external,
)


class RedisAsyncSocketClient(CoreCommands):
    @classmethod
    async def create(cls, config: ClientConfiguration = None):
        config = config or ClientConfiguration.get_default_config()
        self = RedisAsyncSocketClient()
        self.config: Type[ClientConfiguration] = config
        self.socket_connect_timeout = config.config_args.get("connection_timeout")
        self.write_buffer: bytearray = bytearray(1024)
        self._availableFutures: dict[int, Awaitable[Optional[str]]] = {}
        self._availableCallbackIndexes: set[int] = set()
        self._lock = asyncio.Lock()
        init_future = asyncio.Future()
        loop = asyncio.get_event_loop()

        def init_callback(socket_path: Optional[str], err: Optional[str]):
            if err is not None:
                raise (f"Failed to initialize the socket connection: {str(err)}")
            elif socket_path is None:
                raise ("Received None as the socket_path")
            else:
                # Received socket path
                self.socket_path = socket_path
                loop.call_soon_threadsafe(init_future.set_result, True)

        start_socket_listener_external(init_callback=init_callback)

        # will log if the logger was created (wrapper or costumer) on info level or higher
        Logger.log(LogLevel.INFO, "connection info", "new connection established")

        # Wait for the socket listener to complete its initialization
        await init_future
        # Create UDS connection
        await self._create_uds_connection()
        # Start the reader loop as a background task
        self._reader_task = asyncio.create_task(self._reader_loop())
        server_url = to_url(**self.config.config_args)
        # Set the server address
        await self.execute_command(int(PyRequestType.ServerAddress), server_url)

        return self

    async def _wait_for_init_complete(self):
        while not self._done_init:
            await asyncio.sleep(0.1)

    async def _create_uds_connection(self):
        try:
            # Open an UDS connection
            async with async_timeout.timeout(self.socket_connect_timeout):
                reader, writer = await asyncio.open_unix_connection(
                    path=self.socket_path
                )
            self._reader = reader
            self._writer = writer
        except Exception as e:
            self.close(f"Failed to create UDS connection: {e}")
            raise

    def __del__(self):
        try:
            if self._reader_task:
                self._reader_task.cancel()
            pass
        except RuntimeError as e:
            if "no running event loop" in str(e):
                # event loop already closed
                pass

    def close(self, err=""):
        for response_future in self._availableFutures.values():
            response_future.set_exception(err)
        self.__del__()

    def _get_header_length(self, num_of_args: int) -> int:
        return HEADER_LENGTH_IN_BYTES + 4 * (num_of_args - 1)

    async def execute_command(self, command, *args, **kwargs):
        response_future = await self._write_to_socket(command, *args, **kwargs)
        await response_future
        return response_future.result()

    async def set(self, key, value):
        return await self.execute_command(int(PyRequestType.SetString), key, value)

    async def get(self, key):
        return await self.execute_command(int(PyRequestType.GetString), key)

    def _write_int_to_buffer(self, int_arg, bytes_offset, length=4, byteorder="little"):
        bytes_end = bytes_offset + length
        self.write_buffer[bytes_offset:bytes_end] = (int_arg).to_bytes(
            length=length, byteorder=byteorder
        )

    def _write_header(
        self, callback_index, message_length, operation_type, args_len_array
    ):
        self._write_int_to_buffer(message_length, 0)
        self._write_int_to_buffer(callback_index, 4)
        self._write_int_to_buffer(operation_type, 8)
        # Except for the last argument, which can be calculated from the message length
        # minus all other arguments + header, write the length of all additional arguments
        num_of_args = len(args_len_array)
        if num_of_args > 1:
            bytes_offset = HEADER_LENGTH_IN_BYTES
            for i in range(num_of_args - 1):
                self._write_int_to_buffer(args_len_array[i], bytes_offset)
                bytes_offset += 4

    def _get_callback_index(self):
        if not self._availableCallbackIndexes:
            # Set is empty
            return len(self._availableFutures)
        return self._availableCallbackIndexes.pop()

    async def _write_to_socket(self, operation_type, *args, **kwargs):
        async with self._lock:
            callback_index = self._get_callback_index()
            header_len = self._get_header_length(len(args))
            args_len_array = []
            bytes_offset = header_len
            for arg in args:
                arg_in_bytes = arg.encode("UTF-8")
                arg_len = len(arg_in_bytes)
                # Adds the argument length to the array so we can add it to the header later
                args_len_array.append(arg_len)
                end_pos = bytes_offset + arg_len
                # Write the argument to the buffer
                self.write_buffer[bytes_offset:end_pos] = arg_in_bytes
                bytes_offset = end_pos

            message_length = header_len + sum(len for len in args_len_array)
            # Write the header to the buffer
            self._write_header(
                callback_index,
                message_length,
                operation_type,
                args_len_array,
            )
            # Create a response future for this request and add it to the available futures map
            response_future = asyncio.Future()
            self._writer.write(self.write_buffer[0:message_length])
            await self._writer.drain()
            self._availableFutures.update({callback_index: response_future})
            return response_future

    async def _reader_loop(self):
        # Socket reader loop
        while True:
            try:
                data = await self._reader.readexactly(HEADER_LENGTH_IN_BYTES)
                # Parse the received header and wait for the rest of the message
                await self._handle_read_data(data)
            except asyncio.IncompleteReadError:
                self.close("The server closed the connection")

    def _parse_header(self, data):
        msg_length = int.from_bytes(data[0:4], "little")
        callback_idx = int.from_bytes(data[4:8], "little")
        request_type = int.from_bytes(data[8:12], "little")
        return msg_length, callback_idx, request_type

    async def _handle_read_data(self, data):
        length, callback_idx, type = self._parse_header(data)
        if type == int(PyResponseType.ClosingError):
            msg_length = length - HEADER_LENGTH_IN_BYTES
            # Wait for the rest of the message
            message = await self._reader.readexactly(msg_length)
            response = message[:msg_length].decode("UTF-8")
            self.close(f"The server closed the connection with error: {response}")
            return

        res_future = self._availableFutures.get(callback_idx)
        if not res_future:
            raise Exception(f"found invalid callback index: {callback_idx}")
        if type == int(PyResponseType.Null):
            res_future.set_result(None)
        else:
            msg_length = length - HEADER_LENGTH_IN_BYTES
            if msg_length > 0:
                # Wait for the rest of the message
                message = await self._reader.readexactly(msg_length)
                response = message[:msg_length].decode("UTF-8")
            else:
                response = ""
            if type == int(PyResponseType.String):
                res_future.set_result(response)
            elif type == int(PyResponseType.RequestError):
                res_future.set_exception(response)
            elif type == int(PyResponseType.ClosingError):
                self.close(f"The server closed the connection with error: {response}")
            else:
                self.close(f"Received invalid response type: {type}")
        self._availableCallbackIndexes.add(callback_idx)
