from babushka import Client

from src.commands.core import CoreCommands
from src.config import ClientConfiguration
from src.utils import to_url


class RedisAsyncClient(CoreCommands):
    def __init__(
        self,
        config: ClientConfiguration = ClientConfiguration.get_default_config(),
    ):
        self.config = config
        self.connection = None

    async def create_multiplexed_conn(self):
        self.connection = await Client.new(to_url(**self.config.config_args))
        return self.connection

    async def execute_command(self, command, *args, **kwargs):
        conn_rust_func = getattr(self.connection, command)
        return await conn_rust_func(*args, **kwargs)
