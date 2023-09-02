import random
from typing import AsyncGenerator, Optional, Union

import pytest
from pybushka.config import AddressInfo, AuthenticationOptions, ClientConfiguration
from pybushka.Logger import Level as logLevel
from pybushka.Logger import Logger
from pybushka.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.utils.cluster import RedisCluster

default_host = "localhost"
default_port = 6379

Logger.set_logger_config(logLevel.WARN)


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


@pytest.fixture(autouse=True, scope="session")
def call_before_all_tests(request):
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
async def redis_client(request, cluster_mode) -> AsyncGenerator[TRedisClient, None]:
    "Get async socket client for tests"
    client = await create_client(request, cluster_mode)
    yield client
    client.close()


async def create_client(
    request, cluster_mode: bool, credentials: Optional[AuthenticationOptions] = None
) -> Union[RedisClient, RedisClusterClient]:
    # Create async socket client
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    use_tls = request.config.getoption("--tls")
    client: TRedisClient
    if cluster_mode:
        assert type(pytest.redis_cluster) is RedisCluster
        seed_nodes = random.sample(pytest.redis_cluster.nodes_addr, k=3)
        config = ClientConfiguration(
            addresses=seed_nodes, use_tls=use_tls, credentials=credentials
        )
        return await RedisClusterClient.create(config)
    else:
        config = ClientConfiguration(
            [AddressInfo(host=host, port=port)],
            use_tls=use_tls,
            credentials=credentials,
        )
        return await RedisClient.create(config)
