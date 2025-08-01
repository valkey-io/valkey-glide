# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import random
import sys
from typing import AsyncGenerator, List, Optional, Union

import anyio
import pytest

from glide.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
    TlsAdvancedConfiguration,
)
from glide.exceptions import ClosingError
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.logger import Level as logLevel
from glide.logger import Logger
from glide.routes import AllNodes
from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import (
    check_if_server_version_lt,
    set_new_acl_username_with_password,
)

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 6379
DEFAULT_TEST_LOG_LEVEL = logLevel.OFF

# Test teardown retry configuration
TEST_TEARDOWN_MAX_RETRIES = 3
TEST_TEARDOWN_BASE_DELAY = 1  # seconds
MAX_BACKOFF_TIME = 8  # seconds

Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)


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


@pytest.fixture(autouse=True)
async def skip_if_version_below(request, anyio_backend):
    """
    Skip test(s) if server version is below than given parameter. Can skip a complete test suite.

    Example:
        @pytest.mark.skip_if_version_below('7.0.0')
        async def test_meow_meow(...):
            ...
    """
    if request.node.get_closest_marker("skip_if_version_below"):
        min_version = request.node.get_closest_marker("skip_if_version_below").args[0]
        client = await create_client(request, False)
        try:
            if await check_if_server_version_lt(client, min_version):
                pytest.skip(
                    reason=f"This feature added in version {min_version}",
                    allow_module_level=True,
                )
        finally:
            await client.close()


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


@pytest.fixture(scope="function")
async def glide_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> AsyncGenerator[TGlideClient, None]:
    """Get async socket client for tests"""
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        request_timeout=5000,
        lazy_connect=False,  # Explicitly false for general test client
    )
    try:
        yield client
    finally:
        # Close the client first, then run teardown
        await client.close()
        # Run teardown which has its own robust error handling
        await test_teardown(request, cluster_mode, protocol)


@pytest.fixture(scope="function")
async def glide_tls_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    tls_insecure: bool,
) -> AsyncGenerator[TGlideClient, None]:
    """
    Get async socket client for tests with TLS enabled.
    """
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        use_tls=True,
        tls_insecure=tls_insecure,
        valkey_cluster=pytest.valkey_tls_cluster if cluster_mode else pytest.standalone_tls_cluster,  # type: ignore
    )
    try:
        yield client
    finally:
        # Close the client first, then run teardown
        await client.close()
        # Run teardown which has its own robust error handling
        await test_teardown(request, cluster_mode, protocol)


@pytest.fixture
def tls_insecure(request) -> bool:
    # If the test has param'd tls_insecure, use it
    # Otherwise default to False
    return getattr(request, "param", False)


@pytest.fixture(scope="function")
async def management_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> AsyncGenerator[TGlideClient, None]:
    """Get async socket client for tests, used to manage the state when tests are on the client ability to connect"""
    client = await create_client(
        request, cluster_mode, protocol=protocol, lazy_connect=False
    )
    try:
        yield client
    finally:
        # Close the client first, then run teardown
        await client.close()
        # Run teardown which has its own robust error handling
        await test_teardown(request, cluster_mode, protocol)


@pytest.fixture(scope="function")
async def acl_glide_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    management_client: TGlideClient,
) -> AsyncGenerator[TGlideClient, None]:
    """
    Client fot tests that use a server pre-configured with an ACL user.
    This function first uses the management client to register the USERNAME with INITIAL_PASSWORD,so that
    the client would be ablt to connect.
    It then returns a client with this USERNAME and INITIAL_PASSWORD already set as its ServerCredentials.
    """

    await set_new_acl_username_with_password(
        management_client, USERNAME, INITIAL_PASSWORD
    )

    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        credentials=ServerCredentials(username=USERNAME, password=INITIAL_PASSWORD),
        request_timeout=2000,
        lazy_connect=False,
    )
    try:
        yield client
    finally:
        # Close the client first, then run teardown
        await client.close()
        # Run teardown which has its own robust error handling
        await test_teardown(request, cluster_mode, protocol)


async def create_client(
    request,
    cluster_mode: bool,
    credentials: Optional[ServerCredentials] = None,
    database_id: int = 0,
    addresses: Optional[List[NodeAddress]] = None,
    client_name: Optional[str] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    request_timeout: Optional[int] = 1000,
    connection_timeout: Optional[int] = 1000,
    cluster_mode_pubsub: Optional[
        GlideClusterClientConfiguration.PubSubSubscriptions
    ] = None,
    standalone_mode_pubsub: Optional[
        GlideClientConfiguration.PubSubSubscriptions
    ] = None,
    inflight_requests_limit: Optional[int] = None,
    read_from: ReadFrom = ReadFrom.PRIMARY,
    client_az: Optional[str] = None,
    reconnect_strategy: Optional[BackoffStrategy] = None,
    valkey_cluster: Optional[ValkeyCluster] = None,
    use_tls: Optional[bool] = None,
    tls_insecure: Optional[bool] = None,
    lazy_connect: Optional[bool] = False,
) -> Union[GlideClient, GlideClusterClient]:
    # Create async socket client
    if use_tls is not None:
        use_tls = use_tls
    else:
        use_tls = request.config.getoption("--tls")
    tls_adv_conf = TlsAdvancedConfiguration(use_insecure_tls=tls_insecure)
    if cluster_mode:
        valkey_cluster = valkey_cluster or pytest.valkey_cluster  # type: ignore
        assert type(valkey_cluster) is ValkeyCluster
        assert database_id == 0
        k = min(3, len(valkey_cluster.nodes_addr))
        seed_nodes = random.sample(valkey_cluster.nodes_addr, k=k)
        cluster_config = GlideClusterClientConfiguration(
            addresses=seed_nodes if addresses is None else addresses,
            use_tls=use_tls,
            credentials=credentials,
            client_name=client_name,
            protocol=protocol,
            request_timeout=request_timeout,
            pubsub_subscriptions=cluster_mode_pubsub,
            inflight_requests_limit=inflight_requests_limit,
            read_from=read_from,
            client_az=client_az,
            advanced_config=AdvancedGlideClusterClientConfiguration(
                connection_timeout, tls_config=tls_adv_conf
            ),
            lazy_connect=lazy_connect,
        )
        return await GlideClusterClient.create(cluster_config)
    else:
        valkey_cluster = valkey_cluster or pytest.standalone_cluster  # type: ignore
        assert type(valkey_cluster) is ValkeyCluster
        config = GlideClientConfiguration(
            addresses=(valkey_cluster.nodes_addr if addresses is None else addresses),
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
            request_timeout=request_timeout,
            pubsub_subscriptions=standalone_mode_pubsub,
            inflight_requests_limit=inflight_requests_limit,
            read_from=read_from,
            client_az=client_az,
            advanced_config=AdvancedGlideClientConfiguration(
                connection_timeout, tls_config=tls_adv_conf
            ),
            reconnect_strategy=reconnect_strategy,
            lazy_connect=lazy_connect,
        )
        return await GlideClient.create(config)


USERNAME = "username"
INITIAL_PASSWORD = "initial_password"
NEW_PASSWORD = "new_secure_password"
WRONG_PASSWORD = "wrong_password"


async def auth_client(client: TGlideClient, password: str, username: str = "default"):
    """
    Authenticates the given TGlideClient server connected. If no username is provided, uses the 'default' user.
    """
    if isinstance(client, GlideClient):
        return await client.custom_command(["AUTH", username, password])
    elif isinstance(client, GlideClusterClient):
        return await client.custom_command(
            ["AUTH", username, password], route=AllNodes()
        )


async def config_set_new_password(client: TGlideClient, password: str):
    """
    Sets a new password for the given TGlideClient server connected.
    This function updates the server to require a new password.
    """
    if isinstance(client, GlideClient):
        await client.config_set({"requirepass": password})
    elif isinstance(client, GlideClusterClient):
        await client.config_set({"requirepass": password}, route=AllNodes())


async def kill_connections(client: TGlideClient):
    """
    Kills all connections to the given TGlideClient server connected.
    """
    if isinstance(client, GlideClient):
        await client.custom_command(["CLIENT", "KILL", "TYPE", "normal"])
    elif isinstance(client, GlideClusterClient):
        await client.custom_command(
            ["CLIENT", "KILL", "TYPE", "normal"], route=AllNodes()
        )


async def test_teardown(request, cluster_mode: bool, protocol: ProtocolVersion):
    """
    Perform teardown tasks such as flushing all data from the cluster.

    If authentication is required, attempt to connect with the known password,
    reset it back to empty, and proceed with teardown.

    This function is made robust to handle connection timeouts and other transient
    errors that can occur after password changes and connection kills.
    """
    # Add a small delay to allow server to stabilize after password/connection changes
    await anyio.sleep(0.5)

    # Retry connection attempts with exponential backoff
    max_retries = TEST_TEARDOWN_MAX_RETRIES
    base_delay = TEST_TEARDOWN_BASE_DELAY

    for attempt in range(max_retries):
        try:
            await _attempt_teardown(request, cluster_mode, protocol)
            return  # Success, exit the function
        except (ClosingError, TimeoutError) as e:
            if attempt == max_retries - 1:
                # Last attempt failed, log the error but don't fail the test
                Logger.log(
                    logLevel.WARN,
                    "test_teardown",
                    f"Test teardown failed after {max_retries} attempts: {e}",
                )
                return
            else:
                # Wait before retrying with exponential backoff
                delay = min(base_delay * (2**attempt), MAX_BACKOFF_TIME)
                Logger.log(
                    logLevel.WARN,
                    "test_teardown",
                    f"Teardown attempt {attempt + 1} failed, retrying in {delay}s: {e}",
                )
                await anyio.sleep(delay)


async def _attempt_teardown(request, cluster_mode: bool, protocol: ProtocolVersion):
    """
    Single attempt at teardown operations. This function may raise exceptions
    which will be handled by the retry logic in test_teardown.
    """
    credentials = None
    try:
        # Try connecting without credentials with increased timeouts
        client = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            request_timeout=5000,  # Increased from 2000ms
            connection_timeout=5000,  # Increased from default 1000ms
        )
        await client.custom_command(["FLUSHALL"])
        await client.close()
    except ClosingError as e:
        # Check if the error is due to authentication or connection issues
        if "NOAUTH" in str(e):
            # Use the known password to authenticate
            credentials = ServerCredentials(password=NEW_PASSWORD)
            client = await create_client(
                request,
                cluster_mode,
                protocol=protocol,
                request_timeout=5000,  # Increased timeout
                connection_timeout=5000,  # Increased timeout
                credentials=credentials,
            )
            try:
                await auth_client(client, NEW_PASSWORD)
                # Reset the server password back to empty
                await config_set_new_password(client, "")
                await client.update_connection_password(None)
                # Perform the teardown
                await client.custom_command(["FLUSHALL"])
            finally:
                await client.close()
        elif "timed out" in str(e) or "Failed to create initial connections" in str(e):
            # Handle connection timeout errors more gracefully
            # These are often transient after password changes and connection kills
            raise TimeoutError(f"Connection timeout during teardown: {e}")
        else:
            raise e
