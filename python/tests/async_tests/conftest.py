# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import AsyncGenerator, List, Optional, Union

import anyio
import pytest
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.logger import Level as logLevel
from glide.logger import Logger
from glide_shared.config import (
    BackoffStrategy,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
)
from glide_shared.exceptions import ClosingError

from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import (
    DEFAULT_TEST_LOG_LEVEL,
    INITIAL_PASSWORD,
    NEW_PASSWORD,
    USERNAME,
    auth_client,
    config_set_new_password,
    create_client_config,
    set_new_acl_username_with_password,
)

Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)

# Test teardown retry configuration
TEST_TEARDOWN_MAX_RETRIES = 3
TEST_TEARDOWN_BASE_DELAY = 1  # seconds
MAX_BACKOFF_TIME = 8  # seconds


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
    the client would be able to connect.
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
    enable_compression: Optional[bool] = None,
    reconciliation_interval_ms: Optional[int] = None,
) -> Union[GlideClient, GlideClusterClient]:
    config = create_client_config(
        request,
        cluster_mode,
        credentials,
        database_id,
        addresses,
        client_name,
        protocol,
        request_timeout,
        connection_timeout,
        cluster_mode_pubsub,
        standalone_mode_pubsub,
        inflight_requests_limit,
        read_from,
        client_az,
        reconnect_strategy,
        valkey_cluster,
        use_tls,
        tls_insecure,
        lazy_connect,
        enable_compression,
        reconciliation_interval_ms,
    )
    if cluster_mode:
        return await GlideClusterClient.create(config)
    else:
        return await GlideClient.create(config)


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
