# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import uuid

import pytest
from glide.glide_client import GlideClient
from glide_shared.config import (
    GlideClientConfiguration,
    NodeAddress,
    NodeDiscoveryMode,
    ProtocolVersion,
)
from glide_shared.exceptions import ClosingError

from tests.utils.cluster import ValkeyCluster


@pytest.fixture(scope="class")
def discovery_cluster(request):
    tls = request.config.getoption("--tls")
    cluster = ValkeyCluster(tls=tls, cluster_mode=False, shard_count=1, replica_count=3)
    yield cluster
    del cluster


@pytest.mark.anyio
class TestStatic:
    """Tests for STATIC mode."""

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_skip_info_replication_connects(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that STATIC allows basic connection and reads."""
        standalone_cluster = pytest.standalone_cluster  # type: ignore[attr-defined]
        addr = standalone_cluster.nodes_addr[0]
        tls = request.config.getoption("--tls")

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port)],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.STATIC,
            )
        )
        try:
            result = await client.get("nonexistent")
            assert result is None
        finally:
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_skip_info_replication_allows_writes(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that STATIC allows writes."""
        standalone_cluster = pytest.standalone_cluster  # type: ignore[attr-defined]
        addr = standalone_cluster.nodes_addr[0]
        tls = request.config.getoption("--tls")
        key = f"skip_info_test_{uuid.uuid4()}"

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port)],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.STATIC,
            )
        )
        try:
            assert await client.set(key, "value") == "OK"
            assert await client.get(key) == b"value"
        finally:
            await client.delete([key])
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_rejects_discover_replicas(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that read_only + DISCOVER_ALL raises an error."""
        standalone_cluster = pytest.standalone_cluster  # type: ignore[attr-defined]
        addr = standalone_cluster.nodes_addr[0]
        tls = request.config.getoption("--tls")

        with pytest.raises((ClosingError, Exception)) as exc_info:
            await GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(addr.host, addr.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                    node_discovery_mode=NodeDiscoveryMode.DISCOVER_ALL,
                )
            )

        assert (
            "read-only mode is not compatible with DISCOVER_ALL".lower()
            in str(exc_info.value).lower()
        )


@pytest.mark.anyio
class TestDiscoverAll:
    """Tests for DISCOVER_ALL mode using a dedicated cluster with 1 primary + 3 replicas."""

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_discover_replicas_from_primary(
        self,
        request,
        discovery_cluster: ValkeyCluster,
        protocol: ProtocolVersion,
    ):
        """Test that DISCOVER_ALL from primary connects to replicas."""
        tls = request.config.getoption("--tls")
        primary = discovery_cluster.nodes_addr[0]
        replica_0 = discovery_cluster.nodes_addr[1]
        unique_name = f"discovery_t4_{uuid.uuid4()}"

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(primary.host, primary.port)],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.DISCOVER_ALL,
                client_name=unique_name,
            )
        )
        try:
            probe = await GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_0.host, replica_0.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            try:
                client_list = str(await probe.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list
            finally:
                await probe.close()
        finally:
            await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_discover_replicas_from_replica(
        self,
        request,
        discovery_cluster: ValkeyCluster,
        protocol: ProtocolVersion,
    ):
        """Test that DISCOVER_ALL from replica discovers primary and other replicas."""
        tls = request.config.getoption("--tls")
        replica_0 = discovery_cluster.nodes_addr[1]
        unique_name = f"discovery_t5_{uuid.uuid4()}"

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(replica_0.host, replica_0.port)],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.DISCOVER_ALL,
                client_name=unique_name,
            )
        )
        try:
            key = f"discovery_test_{uuid.uuid4()}"
            assert await client.set(key, "value") == "OK"

            probe = await GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_0.host, replica_0.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            try:
                client_list = str(await probe.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list
            finally:
                await probe.close()

            await client.delete([key])
        finally:
            await client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_discover_replicas_partial_addresses(
        self,
        request,
        discovery_cluster: ValkeyCluster,
        protocol: ProtocolVersion,
    ):
        """Test that DISCOVER_ALL with partial addresses discovers remaining replicas."""
        tls = request.config.getoption("--tls")
        primary = discovery_cluster.nodes_addr[0]
        replica_0 = discovery_cluster.nodes_addr[1]
        replica_1 = discovery_cluster.nodes_addr[2]
        replica_2 = discovery_cluster.nodes_addr[3]
        unique_name = f"discovery_t6_{uuid.uuid4()}"

        client = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[
                    NodeAddress(primary.host, primary.port),
                    NodeAddress(replica_0.host, replica_0.port),
                ],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.DISCOVER_ALL,
                client_name=unique_name,
            )
        )
        try:
            probe_1 = await GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_1.host, replica_1.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            probe_2 = await GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_2.host, replica_2.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            try:
                client_list_1 = str(await probe_1.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list_1

                client_list_2 = str(await probe_2.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list_2
            finally:
                await probe_1.close()
                await probe_2.close()
        finally:
            await client.close()
