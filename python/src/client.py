from babushka import Client

from src.commands.core import CoreCommands
from src.utils import to_url


class RedisClient(CoreCommands):
    def __init__(
        self, host="localhost", port=6379, tls_enabled=False, user="", password=""
    ):
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.tls_enabled = tls_enabled
        self.connection = None

    def create_multiplexed_conn(self):
        raise NotImplementedError

    def execute_command(self, command, *args, **kwargs):
        conn_rust_func = getattr(self.connection, command.lower())
        conn_rust_func(*args, **kwargs)


class RedisAsyncClient(RedisClient):
    def __init__(
        self, host="localhost", port=6379, tls_enabled=False, user="", password=""
    ):
        super(RedisAsyncClient, self).__init__(host, port, tls_enabled, user, password)

    async def create_multiplexed_conn(self):
        self.connection = await Client.new(
            to_url(self.host, self.port, self.user, self.password, self.tls_enabled)
        )
        return self.connection

    async def execute_command(self, command, *args, **kwargs):
        conn_rust_func = getattr(self.connection, command.lower())
        return await conn_rust_func(*args, **kwargs)
