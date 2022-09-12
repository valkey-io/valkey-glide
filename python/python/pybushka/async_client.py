import logging
import socket
import socketserver
import sys

from pybushka.commands.core import CoreCommands
from pybushka.config import ClientConfiguration
from pybushka.utils import to_url

from .pybushka import AsyncClient, start_socket_listener_external

LOGGER = logging.getLogger(__name__)


class RedisAsyncFFIClient(CoreCommands):
    @classmethod
    async def create(cls, config: ClientConfiguration = None):
        config = config or ClientConfiguration.get_default_config()
        self = RedisAsyncFFIClient()
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


class RedisAsyncUDSClient(CoreCommands):
    @classmethod
    async def create(cls, config: ClientConfiguration = None):
        config = config or ClientConfiguration.get_default_uds_config()
        self = RedisAsyncUDSClient()
        self.config = config
        if "read_socket_name" not in self.config.config_args:
            raise Exception(
                "read_socket_name must be assigned to the client configuration"
            )
        self.uds_path = self.config.config_args.get("read_socket_name")
        self._sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        print(
            start_socket_listener_external(
                connection_address=to_url(**self.config.config_args),
                socket_path=self.config.config_args["read_socket_name"],
                start_callback=self.create_uds_connection,
                close_callback=self.close_socket,
            )
        )
        # self.connection = self._create_uds_connection()

    def create_uds_connection(self):
        LOGGER.info("Python: start function was called")
        try:
            print("Python connecting to UDS")
            self._sock.connect(self.uds_path)
            print("Python: Connected!")
            self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 32 * 1024 * 1024)
            self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 32 * 1024 * 1024)
        except Exception as e:
            LOGGER.error(str(e))
            self.close()
            sys.exit(1)

    def close_socket(self, err=""):
        print(f"Closing socket {err}")
        self._sock.close()

    async def execute_command(self, command, *args, **kwargs):

        pass

    async def write_to_socket(self, command, *args, **kwargs):
        pass
        # header = ""
        # cmd_msg = 
