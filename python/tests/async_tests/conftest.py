# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import AsyncGenerator, List, Optional, Union

import pytest

from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.logger import Logger
from glide.shared.config import (
    BackoffStrategy,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
)
from glide.shared.exceptions import ClosingError
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


@pytest.fixture(scope="function")
async def glide_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> AsyncGenerator[TGlideClient, None]:
    "Get async socket client for tests"
    client = await create_client(
        request, cluster_mode, protocol=protocol, request_timeout=5000
    )
    yield client
    await test_teardown(request, cluster_mode, protocol)
    await client.close()


@pytest.fixture(scope="function")
async def management_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> AsyncGenerator[TGlideClient, None]:
    "Get async socket client for tests, used to manage the state when tests are on the client ability to connect"
    client = await create_client(request, cluster_mode, protocol=protocol)
    yield client
    await test_teardown(request, cluster_mode, protocol)
    await client.close()


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
    )
    yield client
    await test_teardown(request, cluster_mode, protocol)
    await client.close()


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
    """
    credentials = None
    try:
        # Try connecting without credentials
        client = await create_client(
            request, cluster_mode, protocol=protocol, request_timeout=2000
        )
        await client.custom_command(["FLUSHALL"])
        await client.close()
    except ClosingError as e:
        # Check if the error is due to authentication
        if "NOAUTH" in str(e):
            # Use the known password to authenticate
            credentials = ServerCredentials(password=NEW_PASSWORD)
            client = await create_client(
                request,
                cluster_mode,
                protocol=protocol,
                request_timeout=2000,
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
        else:
            raise e
