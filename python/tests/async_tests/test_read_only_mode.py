# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide.glide_client import GlideClient
from glide_shared.config import GlideClientConfiguration, NodeAddress, ProtocolVersion, ReadFrom
from glide_shared.exceptions import RequestError

from tests.async_tests.conftest import create_client


@pytest.mark.anyio
class TestReadOnlyMode:
    """Tests for read-only mode in standalone client."""

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_mode_blocks_write_commands(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that write commands are blocked in read-only mode with correct error message."""
        client = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            request_timeout=2000,
            read_only=True,
        )
        assert type(client) is GlideClient

        try:
            # Attempt to execute a write command - should be blocked
            with pytest.raises(RequestError) as exc_info:
                await client.set("key", "value")

            # Verify the error message contains the expected text
            assert "write commands are not allowed in read-only mode" in str(
                exc_info.value
            ).lower()
        finally:
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_mode_allows_read_commands(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that read commands are allowed in read-only mode."""
        client = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            request_timeout=2000,
            read_only=True,
        )
        assert type(client) is GlideClient

        try:
            # Read commands should work without raising an error
            result = await client.get("nonexistent_key")
            # The key doesn't exist, so result should be None
            assert result is None
        finally:
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_rejects_az_affinity(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that read-only mode rejects AZAffinity strategy with correct error message."""
        with pytest.raises((RequestError, Exception)) as exc_info:
            await create_client(
                request,
                cluster_mode,
                protocol=protocol,
                request_timeout=2000,
                read_only=True,
                read_from=ReadFrom.AZ_AFFINITY,
                client_az="us-east-1a",
            )

        # Verify the error message contains the expected text
        assert "read-only mode is not compatible with azaffinity" in str(
            exc_info.value
        ).lower()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_rejects_az_affinity_replicas_and_primary(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that read-only mode rejects AZAffinityReplicasAndPrimary strategy."""
        with pytest.raises((RequestError, Exception)) as exc_info:
            await create_client(
                request,
                cluster_mode,
                protocol=protocol,
                request_timeout=2000,
                read_only=True,
                read_from=ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY,
                client_az="us-east-1a",
            )

        # Verify the error message contains the expected text
        assert "read-only mode is not compatible with azaffinity" in str(
            exc_info.value
        ).lower()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_accepts_prefer_replica(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that read-only mode accepts PreferReplica strategy."""
        client = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            request_timeout=2000,
            read_only=True,
            read_from=ReadFrom.PREFER_REPLICA,
        )
        assert type(client) is GlideClient

        try:
            # Client should be created successfully and read commands should work
            result = await client.get("nonexistent_key")
            assert result is None
        finally:
            await client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_read_only_connects_to_primary_and_replica_separately(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """
        Test that read-only mode works when connecting to either primary or replica.

        The standalone cluster is set up with a primary (nodes_addr[0]) and a replica (nodes_addr[1]).
        This test creates two read-only clients - one to each node - to verify that:
        1. Read-only mode works when connecting directly to a replica (skips INFO REPLICATION)
        2. Read-only mode works when connecting directly to a primary
        3. Write commands are blocked on both
        4. Read commands work on both
        """
        # Get the standalone cluster addresses
        standalone_cluster = pytest.standalone_cluster
        assert len(standalone_cluster.nodes_addr) >= 2, (
            "Standalone cluster should have at least 2 nodes (primary + replica)"
        )

        # Node 0 is the primary, Node 1 is the replica (based on cluster_manager.py)
        node_0_addr = standalone_cluster.nodes_addr[0]
        node_1_addr = standalone_cluster.nodes_addr[1]

        # Create read-only client connecting only to node 0 (primary)
        client_node_0 = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(node_0_addr.host, node_0_addr.port)],
                use_tls=request.config.getoption("--tls"),
                protocol=protocol,
                request_timeout=2000,
                read_only=True,
            )
        )

        # Create read-only client connecting only to node 1 (replica)
        client_node_1 = await GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(node_1_addr.host, node_1_addr.port)],
                use_tls=request.config.getoption("--tls"),
                protocol=protocol,
                request_timeout=2000,
                read_only=True,
            )
        )

        try:
            # Test read commands work on both clients
            result_0 = await client_node_0.get("test_key_read_only")
            result_1 = await client_node_1.get("test_key_read_only")
            # Both should return None (key doesn't exist) without error
            assert result_0 is None
            assert result_1 is None

            # Test write commands are blocked on client connected to node 0
            with pytest.raises(RequestError) as exc_info_0:
                await client_node_0.set("test_key", "value")
            assert "write commands are not allowed in read-only mode" in str(
                exc_info_0.value
            ).lower()

            # Test write commands are blocked on client connected to node 1
            with pytest.raises(RequestError) as exc_info_1:
                await client_node_1.set("test_key", "value")
            assert "write commands are not allowed in read-only mode" in str(
                exc_info_1.value
            ).lower()

        finally:
            await client_node_0.close()
            await client_node_1.close()
