import random

import pytest
from pybushka.config import AddressInfo, ClientConfiguration
from pybushka.Logger import Level as logLevel
from pybushka.Logger import set_logger_config
from pybushka.redis_client import BaseRedisClient, RedisClient, RedisClusterClient
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
async def redis_client(request, cluster_mode) -> BaseRedisClient:
    "Get async socket client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    use_tls = request.config.getoption("--tls")
    if cluster_mode:
        seed_nodes = random.sample(pytest.redis_cluster.nodes_addr, k=3)
        config = ClientConfiguration(seed_nodes, use_tls=use_tls)
        client = await RedisClusterClient.create(config)
    else:
        config = ClientConfiguration(
            [AddressInfo(host=host, port=port)], use_tls=use_tls
        )
        client = await RedisClient.create(config)
    yield client
    client.close()
