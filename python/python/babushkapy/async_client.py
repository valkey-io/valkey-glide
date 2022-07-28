from babushkapy.commands.core import CoreCommands
from babushkapy.config import ClientConfiguration
from babushkapy.utils import to_url

from .babushkapy import AsyncClient


class RedisAsyncClient(CoreCommands):
    @classmethod
    async def create(
        cls, config: ClientConfiguration = ClientConfiguration.get_default_config()
    ):
        self = RedisAsyncClient()
        self.config = config
        self.connection = await self._create_multiplexed_conn()
        self.rust_functions = self._initialize_functions([CoreCommands])

        return self

    def _initialize_functions(self, classes):
        funcs = dict()
        for cls in classes:
            for method in dir(cls):
                if not method.startswith("__"):
                    try:
                        func = getattr(self.connection, method)
                        funcs[method] = func
                    except AttributeError:
                        # The connection doesn't have this method
                        pass
        return funcs

    async def _create_multiplexed_conn(self):
        return await AsyncClient.create_client(to_url(**self.config.config_args))

    async def execute_command(self, command, *args, **kwargs):
        conn_rust_func = self.rust_functions.get(command)
        return await conn_rust_func(*args, **kwargs)

    def create_pipeline(self):
        return self.connection.create_pipeline()
