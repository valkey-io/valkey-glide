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
    pass
