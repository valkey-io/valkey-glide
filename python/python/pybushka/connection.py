import socket
import sys
from abc import ABC, abstractmethod

from pybushka.utils import to_url

from .pybushka import AsyncClient


class AsyncConnection(ABC):
    def __init__(self, conn_args) -> None:
        self.conn_args = conn_args
        self._connection = None

    def get_connection(self):
        return self._connection

    @abstractmethod
    async def connect(self):
        ...


class AsyncFFIConnection(AsyncConnection):
    def __init__(self, conn_args) -> None:
        super().__init__(conn_args)

    async def connect(self):
        self._connection = await AsyncClient.create_client(to_url(**self.conn_args))


class AsyncSocketConnection(AsyncConnection):
    DEFAULT_SERVER_ADDRESS = "./uds_socket"

    def __init__(self, conn_args, server_address=DEFAULT_SERVER_ADDRESS) -> None:
        super().__init__(conn_args)
        self._sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.server_address = server_address

    # define method
    async def connect(self):
        try:
            print("Python connecting")
            self._sock.connect(self.server_address)
            self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 32 * 1024 * 1024)
            self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 32 * 1024 * 1024)
        except socket.error as e:
            print(str(e))
            self.close()
            sys.exit(1)

    def close(self):
        print("Closing socket")
        self._sock.close()
