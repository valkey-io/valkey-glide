import pytest

from src.client import RedisAsyncClient

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
async def async_client(request):
    "Get async client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    client = RedisAsyncClient(host, port)
    return await client.create_multiplexed_conn()
