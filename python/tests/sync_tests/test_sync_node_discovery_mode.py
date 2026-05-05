# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import uuid

import pytest
from glide_shared.config import (
    GlideClientConfiguration,
    NodeAddress,
    NodeDiscoveryMode,
    ProtocolVersion,
)
from glide_shared.exceptions import ClosingError
from glide_sync import GlideClient

from tests.utils.cluster import ValkeyCluster


@pytest.fixture(scope="class")
def discovery_cluster(request):
    tls = request.config.getoption("--tls")
    cluster = ValkeyCluster(tls=tls, cluster_mode=False, shard_count=1, replica_count=3)
    yield cluster
    del cluster


class TestSyncStatic:
    """Tests for STATIC mode (sync version)."""

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_skip_info_replication_connects(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        standalone_cluster = pytest.standalone_cluster  # type: ignore[attr-defined]
        addr = standalone_cluster.nodes_addr[0]
        tls = request.config.getoption("--tls")

        client = GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port)],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.STATIC,
            )
        )
        try:
            result = client.get("nonexistent")
            assert result is None
        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_skip_info_replication_allows_writes(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        standalone_cluster = pytest.standalone_cluster  # type: ignore[attr-defined]
        addr = standalone_cluster.nodes_addr[0]
        tls = request.config.getoption("--tls")
        key = f"skip_info_test_{uuid.uuid4()}"

        client = GlideClient.create(
            GlideClientConfiguration(
                addresses=[NodeAddress(addr.host, addr.port)],
                use_tls=tls,
                protocol=protocol,
                request_timeout=2000,
                node_discovery_mode=NodeDiscoveryMode.STATIC,
            )
        )
        try:
            assert client.set(key, "value") == "OK"
            assert client.get(key) == b"value"
        finally:
            client.delete([key])
            client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_read_only_rejects_discover_replicas(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        standalone_cluster = pytest.standalone_cluster  # type: ignore[attr-defined]
        addr = standalone_cluster.nodes_addr[0]
        tls = request.config.getoption("--tls")

        with pytest.raises((ClosingError, Exception)) as exc_info:
            GlideClient.create(
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


class TestSyncDiscoverAll:
    """Tests for DISCOVER_ALL mode (sync version)."""

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_discover_replicas_from_primary(
        self,
        request,
        discovery_cluster: ValkeyCluster,
        protocol: ProtocolVersion,
    ):
        tls = request.config.getoption("--tls")
        primary = discovery_cluster.nodes_addr[0]
        replica_0 = discovery_cluster.nodes_addr[1]
        unique_name = f"discovery_t4_{uuid.uuid4()}"

        client = GlideClient.create(
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
            probe = GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_0.host, replica_0.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            try:
                client_list = str(probe.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list
            finally:
                probe.close()
        finally:
            client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_discover_replicas_from_replica(
        self,
        request,
        discovery_cluster: ValkeyCluster,
        protocol: ProtocolVersion,
    ):
        tls = request.config.getoption("--tls")
        replica_0 = discovery_cluster.nodes_addr[1]
        unique_name = f"discovery_t5_{uuid.uuid4()}"

        client = GlideClient.create(
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
            assert client.set(key, "value") == "OK"

            probe = GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_0.host, replica_0.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            try:
                client_list = str(probe.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list
            finally:
                probe.close()

            client.delete([key])
        finally:
            client.close()

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_discover_replicas_partial_addresses(
        self,
        request,
        discovery_cluster: ValkeyCluster,
        protocol: ProtocolVersion,
    ):
        tls = request.config.getoption("--tls")
        primary = discovery_cluster.nodes_addr[0]
        replica_0 = discovery_cluster.nodes_addr[1]
        replica_1 = discovery_cluster.nodes_addr[2]
        replica_2 = discovery_cluster.nodes_addr[3]
        unique_name = f"discovery_t6_{uuid.uuid4()}"

        client = GlideClient.create(
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
            probe_1 = GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_1.host, replica_1.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            probe_2 = GlideClient.create(
                GlideClientConfiguration(
                    addresses=[NodeAddress(replica_2.host, replica_2.port)],
                    use_tls=tls,
                    protocol=protocol,
                    request_timeout=2000,
                    read_only=True,
                )
            )
            try:
                client_list_1 = str(probe_1.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list_1

                client_list_2 = str(probe_2.custom_command(["CLIENT", "LIST"]))
                assert unique_name in client_list_2
            finally:
                probe_1.close()
                probe_2.close()
        finally:
            client.close()
