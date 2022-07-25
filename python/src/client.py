from babushka import Client

from src.commands.core import CoreCommands
from src.config import ClientConfiguration
from src.utils import to_url


class RedisClient(CoreCommands):
    def __init__(
        self, config: ClientConfiguration = ClientConfiguration.get_default_config()
    ):
        self.config = config

    def create_multiplexed_conn(self):
        raise NotImplementedError

    def execute_command(self, command, *args, **kwargs):
        conn_rust_func = getattr(self.connection, command.lower())
        conn_rust_func(*args, **kwargs)
