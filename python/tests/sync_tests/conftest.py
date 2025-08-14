# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Generator, List, Optional

import pytest
from glide_shared.config import (
    BackoffStrategy,
    NodeAddress,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
)
from glide_shared.exceptions import ClosingError
from glide_sync import GlideClient as SyncGlideClient
from glide_sync import GlideClusterClient as SyncGlideClusterClient
from glide_sync import TGlideClient as TSyncGlideClient
from glide_sync.logger import Logger

from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import (
    DEFAULT_SYNC_TEST_LOG_LEVEL,
    INITIAL_PASSWORD,
    NEW_PASSWORD,
    USERNAME,
    auth_client,
    config_set_new_password,
    create_sync_client_config,
    set_new_acl_username_with_password,
)

Logger.set_logger_config(DEFAULT_SYNC_TEST_LOG_LEVEL)


@pytest.fixture(scope="function")
def glide_sync_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> Generator[TSyncGlideClient, None, None]:
    "Get sync socket client for tests"
    client = create_sync_client(
        request, cluster_mode, protocol=protocol, connection_timeout=5000
    )
    yield client
    sync_test_teardown(request, cluster_mode, protocol)
    client.close()


@pytest.fixture(scope="function")
def management_sync_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> Generator[TSyncGlideClient, None, None]:
    """Get async socket client for tests, used to manage the state when tests are on the client ability to connect"""
    client = create_sync_client(
        request, cluster_mode, protocol=protocol, lazy_connect=False
    )
    try:
        yield client
    finally:
        # Close the client first, then run teardown
        client.close()
        # Run teardown which has its own robust error handling
        sync_test_teardown(request, cluster_mode, protocol)


@pytest.fixture(scope="function")
def acl_glide_sync_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    management_sync_client: TSyncGlideClient,
) -> Generator[TSyncGlideClient, None, None]:
    """
    Client fot tests that use a server pre-configured with an ACL user.
    This function first uses the management client to register the USERNAME with INITIAL_PASSWORD,so that
    the client would be able to connect.
    It then returns a client with this USERNAME and INITIAL_PASSWORD already set as its ServerCredentials.
    """

    set_new_acl_username_with_password(
        management_sync_client, USERNAME, INITIAL_PASSWORD
    )

    client = create_sync_client(
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
        client.close()
        # Run teardown which has its own robust error handling
        sync_test_teardown(request, cluster_mode, protocol)


def create_sync_client(
    request,
    cluster_mode: bool,
    credentials: Optional[ServerCredentials] = None,
    database_id: int = 0,
    addresses: Optional[List[NodeAddress]] = None,
    client_name: Optional[str] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    request_timeout: Optional[int] = 1000,
    connection_timeout: Optional[int] = 1000,
    read_from: ReadFrom = ReadFrom.PRIMARY,
    client_az: Optional[str] = None,
    reconnect_strategy: Optional[BackoffStrategy] = None,
    valkey_cluster: Optional[ValkeyCluster] = None,
    use_tls: Optional[bool] = None,
    tls_insecure: Optional[bool] = None,
    lazy_connect: Optional[bool] = False,
) -> TSyncGlideClient:
    # Create sync client
    config = create_sync_client_config(
        request,
        cluster_mode,
        credentials,
        database_id,
        addresses,
        client_name,
        protocol,
        request_timeout,
        connection_timeout,
        read_from,
        client_az,
        reconnect_strategy,
        valkey_cluster,
        use_tls=use_tls,
        tls_insecure=tls_insecure,
        lazy_connect=lazy_connect,
    )
    if cluster_mode:
        return SyncGlideClusterClient.create(config)
    else:
        return SyncGlideClient.create(config)


@pytest.fixture(scope="function")
def glide_sync_tls_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    tls_insecure: bool,
) -> Generator[TSyncGlideClient, None, None]:
    """
    Get sync client for tests with TLS enabled.
    """
    client = create_sync_client(
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
        client.close()
        # Run teardown which has its own robust error handling
        sync_test_teardown(request, cluster_mode, protocol)


def sync_test_teardown(request, cluster_mode: bool, protocol: ProtocolVersion):
    """
    Perform teardown tasks such as flushing all data from the cluster.

    If authentication is required, attempt to connect with the known password,
    reset it back to empty, and proceed with teardown.
    """
    credentials = None
    try:
        # Try connecting without credentials
        client = create_sync_client(
            request, cluster_mode, protocol=protocol, request_timeout=2000
        )
        client.custom_command(["FLUSHALL"])
        client.close()
    except ClosingError as e:
        # Check if the error is due to authentication
        if "NOAUTH" in str(e):
            # Use the known password to authenticate
            credentials = ServerCredentials(password=NEW_PASSWORD)
            client = create_sync_client(
                request,
                cluster_mode,
                protocol=protocol,
                request_timeout=2000,
                credentials=credentials,
            )
            try:
                auth_client(client, NEW_PASSWORD)
                # Reset the server password back to empty
                config_set_new_password(client, "")
                client.update_connection_password(None)
                # Perform the teardown
                client.custom_command(["FLUSHALL"])
            finally:
                client.close()
        else:
            raise e
