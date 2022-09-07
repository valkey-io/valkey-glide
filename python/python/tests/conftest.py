import pytest
from pybushka.async_client import RedisAsyncFFIClient, RedisAsyncUDSClient
from pybushka.config import ClientConfiguration
from pybushka.connection import AsyncFFIConnection

default_host = "localhost"
default_port = 6379


def pytest_addoption(parser):
    parser.addoption(
        "--host",
        default=default_host,
        action="store",
        help="Redis host endpoint," " defaults to `%(default)s`",
    )

    parser.addoption(
        "--port",
        default=default_port,
        action="store",
        help="Redis port," " defaults to `%(default)s`",
    )


@pytest.fixture()
async def async_ffi_client(request):
    "Get async client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    config = ClientConfiguration(host=host, port=port)
    return await RedisAsyncFFIClient.create(config)


@pytest.fixture()
async def async_uds_client(request):
    "Get async client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    config = ClientConfiguration(
        host=host,
        port=port,
        read_socket_name="./uds_read_socket",
        write_socket_name="./uds_write_socket",
    )
    return await RedisAsyncUDSClient.create(config)
