import asyncio
from typing import AsyncGenerator

import pytest
from glide.config import ProtocolVersion
from glide.glide_client import TGlideClient
from tests.conftest import create_client
from tests.utils.cluster import ValkeyCluster


@pytest.fixture(scope="module")
def event_loop():
    """A module-scoped event loop for async tests in this file only."""
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="module", autouse=True)
async def tls_clusters():
    tls_valkey_cluster = ValkeyCluster(
        tls=True, cluster_mode=True, shard_count=3, replica_count=0
    )
    tls_valkey_standalone = ValkeyCluster(
        tls=True, cluster_mode=False, shard_count=1, replica_count=0
    )

    yield (tls_valkey_cluster, tls_valkey_standalone)

    del tls_valkey_cluster
    del tls_valkey_standalone


@pytest.fixture
def tls_insecure(request) -> bool:
    # If the test has param'd tls_insecure, use it
    # Otherwise default to False
    return getattr(request, "param", False)


@pytest.fixture(scope="function")
async def glide_tls_client(
    request,
    tls_clusters,  # we get (cluster_mode=True, cluster_mode=False) ValkeyClusters as a tuple
    cluster_mode: bool,  # this is coming from @pytest.mark.parametrize
    protocol: ProtocolVersion,
    tls_insecure: bool,
) -> "AsyncGenerator[TGlideClient, None]":
    """
    Return a GlideClusterClient that connects to either the cluster or standalone,
    depending on the cluster_mode param.
    """
    (tls_valkey_cluster, tls_valkey_standalone) = tls_clusters

    if cluster_mode:
        chosen_cluster = tls_valkey_cluster
    else:
        chosen_cluster = tls_valkey_standalone

    client = await create_client(
        request,
        cluster_mode=cluster_mode,
        valkey_cluster=chosen_cluster,
        protocol=protocol,
        use_tls=True,
        tls_insecure=tls_insecure,
    )
    yield client
    await client.close()


@pytest.mark.asyncio
class TestTls:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("tls_insecure", [True], indirect=True)
    async def test_tls_insecure(self, glide_tls_client: TGlideClient):
        """
        This test verifies that the Glide client can connect to a TLS-enabled Valkey instance while skipping
        certificate validation. By parametrizing tls_insecure=True, we confirm that the client can successfully
        ping the cluster without strict cert checks.
        """
        result = await glide_tls_client.ping()

        assert result == b"PONG"
