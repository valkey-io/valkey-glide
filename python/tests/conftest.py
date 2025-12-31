# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import sys
from typing import List

import pytest

from tests.sync_tests.conftest import create_sync_client
from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import sync_check_if_server_version_lt

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 6379


def pytest_addoption(parser):
    parser.addoption(
        "--host",
        default=DEFAULT_HOST,
        action="store",
        help="Server host endpoint, defaults to `%(default)s`",
    )

    parser.addoption(
        "--port",
        default=DEFAULT_PORT,
        action="store",
        help="Server port, defaults to `%(default)s`",
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

    parser.addoption(
        "--compression",
        default=False,
        action="store_true",
        help="Enable compression for all tests, defaults to `%(default)s`",
    )

    parser.addoption(
        "--load-module",
        action="append",
        help="""Load additional Valkey modules (provide full path for the module's shared library).
            Use multiple times for multiple modules.
            Example:
            pytest -v --load-module=/path/to/module1.so --load-module=/path/to/module2.so""",
        default=[],
    )

    parser.addoption(
        "--cluster-endpoints",
        action="store",
        help="""Comma-separated list of cluster endpoints for standalone cluster in the format host1:port1,host2:port2,...
            Note: The cluster will be flashed between tests.
            Example:
                pytest -v --cluster-endpoints=127.0.0.1:6379
                pytest -v --cluster-endpoints=127.0.0.1:6379,127.0.0.1:6380
            """,
        default=None,
    )

    parser.addoption(
        "--standalone-endpoints",
        action="store",
        help="""Comma-separated list of cluster endpoints for cluster mode cluster in the format host1:port1,host2:port2,...
            Note: The cluster will be flashed between tests.
            Example:
                pytest -v --standalone-endpoints=127.0.0.1:6379
                pytest -v --standalone-endpoints=127.0.0.1:6379,127.0.0.1:6380
            """,
        default=None,
    )

    parser.addoption(
        "--async-backend",
        action="append",
        choices=("asyncio", "uvloop", "trio"),
        help="""Async framework with which the tests will be run. By default, runs on asyncio and trio.
            Example:
                pytest -v --async-backend=trio
                pytest -v --async-backend=uvloop --async-backend=trio
            """,
        default=None,
    )

    parser.addoption(
        "--mock-pubsub",
        default=False,
        action="store_true",
        help="Running with mock pubsub (skips tests that require real server connections)",
    )


def parse_endpoints(endpoints_str: str) -> List[List[str]]:
    """
    Parse the endpoints string into a list of lists containing host and port.
    """
    try:
        endpoints = [endpoint.split(":") for endpoint in endpoints_str.split(",")]
        for endpoint in endpoints:
            if len(endpoint) != 2:
                raise ValueError(
                    "Each endpoint should be in the format 'host:port'.\nEndpoints should be separated by commas."
                )
            host, port = endpoint
            if not host or not port.isdigit():
                raise ValueError(
                    "Both host and port should be specified and port should be a valid integer."
                )
        return endpoints
    except ValueError as e:
        raise ValueError("Invalid endpoints format: " + str(e))


def create_clusters(tls, load_module, cluster_endpoints, standalone_endpoints):
    """
    Create Valkey clusters based on the provided options.
    """
    if cluster_endpoints or standalone_endpoints:
        # Endpoints were passed by the caller, not creating clusters internally
        if cluster_endpoints:
            cluster_endpoints = parse_endpoints(cluster_endpoints)
            pytest.valkey_cluster = ValkeyCluster(tls=tls, addresses=cluster_endpoints)
        if standalone_endpoints:
            standalone_endpoints = parse_endpoints(standalone_endpoints)
            pytest.standalone_cluster = ValkeyCluster(
                tls=tls, addresses=standalone_endpoints
            )
    else:
        # No endpoints were provided, create new clusters
        pytest.valkey_cluster = ValkeyCluster(
            tls=tls,
            cluster_mode=True,
            load_module=load_module,
            addresses=cluster_endpoints,
            replica_count=2,
        )
        pytest.standalone_cluster = ValkeyCluster(
            tls=tls,
            cluster_mode=False,
            shard_count=1,
            replica_count=1,
            load_module=load_module,
            addresses=standalone_endpoints,
        )

    pytest.valkey_tls_cluster = ValkeyCluster(
        tls=True,
        cluster_mode=True,
        load_module=load_module,
        replica_count=2,
    )
    pytest.standalone_tls_cluster = ValkeyCluster(
        tls=True,
        cluster_mode=False,
        shard_count=1,
        replica_count=1,
        load_module=load_module,
    )


@pytest.fixture(autouse=True, scope="session")
def call_before_all_pytests(request):
    """
    Called after the Session object has been created and
    before performing collection and entering the run test loop.
    """
    tls = request.config.getoption("--tls")
    load_module = request.config.getoption("--load-module")
    cluster_endpoints = request.config.getoption("--cluster-endpoints")
    standalone_endpoints = request.config.getoption("--standalone-endpoints")

    # only run asyncio by default. trio is run in CI nightly
    request.config.async_backends = request.config.getoption("--async-backend") or (
        "asyncio",
    )

    create_clusters(tls, load_module, cluster_endpoints, standalone_endpoints)


def pytest_sessionfinish(session, exitstatus):
    """
    Called after whole test run finished, right before
    returning the exit status to the system.
    """
    try:
        del pytest.valkey_cluster
    except AttributeError:
        # valkey_cluster was not set, skip deletion
        pass

    try:
        del pytest.standalone_cluster
    except AttributeError:
        # standalone_cluster was not set, skip deletion
        pass

    try:
        del pytest.valkey_tls_cluster
    except AttributeError:
        # valkey_tls_cluster was not set, skip deletion
        pass

    try:
        del pytest.standalone_tls_cluster
    except AttributeError:
        # standalone_tls_cluster was not set, skip deletion
        pass


def pytest_collection_modifyitems(config, items):
    """
    Modify collected test items.

    This function checks if cluster or standalone endpoints are provided. If so, it checks if the test requires
    cluster mode and skips it accordingly.
    """
    for item in items:
        if config.getoption("--cluster-endpoints") or config.getoption(
            "--standalone-endpoints"
        ):
            if "cluster_mode" in item.fixturenames:
                cluster_mode_value = item.callspec.params.get("cluster_mode", None)
                if cluster_mode_value is True and not config.getoption(
                    "--cluster-endpoints"
                ):
                    item.add_marker(
                        pytest.mark.skip(
                            reason="Test skipped because cluster_mode=True and cluster endpoints weren't provided"
                        )
                    )
                elif cluster_mode_value is False and not config.getoption(
                    "--standalone-endpoints"
                ):
                    item.add_marker(
                        pytest.mark.skip(
                            reason="Test skipped because cluster_mode=False and standalone endpoints weren't provided"
                        )
                    )


@pytest.fixture(
    params=[
        pytest.param(
            (("asyncio", {"use_uvloop": False}), "asyncio"),
            id="asyncio",
        ),
        pytest.param(
            (("asyncio", {"use_uvloop": True}), "uvloop"),
            marks=[
                pytest.mark.skipif(
                    sys.platform == "win32",
                    reason="uvloop is unavailabe on windows",
                )
            ],
            id="uvloop",
        ),
        pytest.param(
            (("trio", {"restrict_keyboard_interrupt_to_checkpoints": True}), "trio"),
            id="trio",
        ),
    ]
)
def anyio_backend(request):
    if request.param[1] not in request.config.async_backends:
        pytest.skip(
            reason=f"{request.param[1]} is excluded",
            allow_module_level=True,
        )

    return request.param[0]


@pytest.fixture(autouse=True)
def skip_if_version_below(request):
    """
    Skip test(s) if server version is below than given parameter. Can skip a complete test suite.

    Example:
        @pytest.mark.skip_if_version_below('7.0.0')
        async def test_meow_meow(...):
            ...
    """
    if request.node.get_closest_marker("skip_if_version_below"):
        min_version = request.node.get_closest_marker("skip_if_version_below").args[0]
        client = create_sync_client(request, False)
        try:
            if sync_check_if_server_version_lt(client, min_version):
                pytest.skip(
                    reason=f"This feature added in version {min_version}",
                    allow_module_level=True,
                )
        finally:
            client.close()


@pytest.fixture
def tls_insecure(request) -> bool:
    # If the test has param'd tls_insecure, use it
    # Otherwise default to False
    return getattr(request, "param", False)


@pytest.fixture(autouse=True)
def skip_if_mock_pubsub(request):
    """
    Skip test(s) if running with mock pubsub and test is marked with skip_if_mock_pubsub.

    Example:
        @pytest.mark.skip_if_mock_pubsub
        async def test_resubscribe_after_connection_kill(...):
            ...
    """
    if request.node.get_closest_marker("skip_if_mock_pubsub"):
        if request.config.getoption("--mock-pubsub"):
            pytest.skip(
                reason="Test skipped because it requires real server connections (running with --mock-pubsub)",
                allow_module_level=True,
            )
