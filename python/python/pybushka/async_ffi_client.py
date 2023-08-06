from pybushka.async_commands.core import FFICoreCommands
from pybushka.config import ClientConfiguration
from pybushka.utils import to_url

from .pybushka import AsyncClient


class RedisAsyncFFIClient(FFICoreCommands):
    @classmethod
    async def create(cls, config: ClientConfiguration = None):
        config = config or ClientConfiguration()
        self = RedisAsyncFFIClient()
        self.config = config
        self.connection = await self._create_multiplexed_conn()
        self.rust_functions = self._initialize_functions([FFICoreCommands])
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
        return await AsyncClient.create_client(
            to_url(self.config.addresses[0].host, self.config.addresses[0].port)
        )

    async def _execute_command(self, command, *args, **kwargs):
        conn_rust_func = self.rust_functions.get(command)
        return await conn_rust_func(*args, **kwargs)

    def create_pipeline(self):
        return self.connection.create_pipeline()
