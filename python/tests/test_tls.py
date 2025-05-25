from typing import AsyncGenerator, Dict

import pytest

from glide.config import ProtocolVersion
from glide.glide_client import TGlideClient
from tests.conftest import create_client
from tests.utils.cluster import ValkeyCluster


@pytest.fixture
def tls_insecure(request) -> bool:
    # If the test has param'd tls_insecure, use it
    # Otherwise default to False
    return getattr(request, "param", False)


@pytest.fixture(scope="module")
async def tls_clusters() -> AsyncGenerator[Dict[bool, ValkeyCluster], None]:
    """Create TLS clusters once per module."""
    cluster = ValkeyCluster(tls=True, cluster_mode=True, shard_count=3, replica_count=0)
    standalone = ValkeyCluster(
        tls=True, cluster_mode=False, shard_count=1, replica_count=0
    )

    yield {
        True: cluster,
        False: standalone,
    }

    del cluster
    del standalone


@pytest.fixture(scope="function")
async def glide_tls_client(
    request,
    tls_clusters,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    tls_insecure: bool,
) -> AsyncGenerator[TGlideClient, None]:
    chosen_cluster = tls_clusters[cluster_mode]

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


@pytest.mark.anyio
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
