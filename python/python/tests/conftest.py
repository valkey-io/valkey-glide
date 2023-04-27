import pytest
from pybushka.async_ffi_client import RedisAsyncFFIClient
from pybushka.async_socket_client import RedisAsyncSocketClient
from pybushka.config import AddressInfo, ClientConfiguration
from pybushka.Logger import Level as logLevel
from pybushka.Logger import set_logger_config

default_host = "localhost"
default_port = 6379

set_logger_config(logLevel.INFO)


def pytest_addoption(parser):
    parser.addoption(
        "--host",
        default=default_host,
        action="store",
        help="Redis host endpoint, defaults to `%(default)s`",
    )

    parser.addoption(
        "--port",
        default=default_port,
        action="store",
        help="Redis port, defaults to `%(default)s`",
    )

    parser.addoption(
        "--tls",
        default=False,
        action="store_true",
        help="TLS enabled, defaults to `%(default)s`",
    )

    parser.addoption(
        "--no-tls",
        dest="tls",
        action="store_false",
        help="TLS disabled, defaults to `%(default)s`",
    )


@pytest.fixture()
async def async_ffi_client(request) -> RedisAsyncFFIClient:
    "Get async FFI client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    config = ClientConfiguration([AddressInfo(host=host, port=port)])
    return await RedisAsyncFFIClient.create(config)


@pytest.fixture()
async def async_socket_client(request) -> RedisAsyncSocketClient:
    "Get async socket client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    use_tls = request.config.getoption("--tls")
    config = ClientConfiguration([AddressInfo(host=host, port=port)], use_tls=use_tls)
    return await RedisAsyncSocketClient.create(config)
