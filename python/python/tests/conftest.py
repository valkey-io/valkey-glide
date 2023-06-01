import pytest
from pybushka.async_ffi_client import RedisAsyncFFIClient
from pybushka.config import AddressInfo, ClientConfiguration
from pybushka.Logger import Level as logLevel
from pybushka.Logger import set_logger_config
from tests.utils.cluster import RedisCluster

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


@pytest.fixture(autouse=True, scope="session")
def pytest_sessionstart(request):
    """
    Called after the Session object has been created and
    before performing collection and entering the run test loop.
    """
    tls = request.config.getoption("--tls")
    pytest.redis_cluster = RedisCluster(tls)


def pytest_sessionfinish(session, exitstatus):
    """
    Called after whole test run finished, right before
    returning the exit status to the system.
    """
    del pytest.redis_cluster
