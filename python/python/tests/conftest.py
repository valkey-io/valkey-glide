import random
from typing import AsyncGenerator, Optional, Union

import pytest
from pybushka.config import (
    AddressInfo,
    AuthenticationOptions,
    ClientConfiguration,
    StandaloneClientConfiguration,
)
from pybushka.logger import Level as logLevel
from pybushka.logger import Logger
from pybushka.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.utils.cluster import RedisCluster

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 6379
DEFAULT_TEST_LOG_LEVEL = logLevel.WARN

Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)


def pytest_addoption(parser):
    parser.addoption(
        "--host",
        default=DEFAULT_HOST,
        action="store",
        help="Redis host endpoint, defaults to `%(default)s`",
    )

    parser.addoption(
        "--port",
        default=DEFAULT_PORT,
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


@pytest.fixture(autouse=True, scope="session")
def call_before_all_pytests(request):
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
    try:
        del pytest.redis_cluster
    except AttributeError:
        # redis_cluster was not set, skip deletion
        pass


@pytest.fixture()
async def redis_client(
    request,
    cluster_mode: bool,
) -> AsyncGenerator[TRedisClient, None]:
    "Get async socket client for tests"
    client = await create_client(request, cluster_mode)
    yield client
    client.close()


async def create_client(
    request,
    cluster_mode: bool,
    credentials: Optional[AuthenticationOptions] = None,
    database_id: int = 0,
) -> Union[RedisClient, RedisClusterClient]:
    # Create async socket client
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    use_tls = request.config.getoption("--tls")
    if cluster_mode:
        assert type(pytest.redis_cluster) is RedisCluster
        assert database_id == 0
        seed_nodes = random.sample(pytest.redis_cluster.nodes_addr, k=3)
        config = ClientConfiguration(
            addresses=seed_nodes, use_tls=use_tls, credentials=credentials
        )
        return await RedisClusterClient.create(config)
    else:
        config = StandaloneClientConfiguration(
            addresses=[AddressInfo(host=host, port=port)],
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,
        )
        return await RedisClient.create(config)
