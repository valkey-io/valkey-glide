# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import re
from typing import Mapping, cast

import pytest

from glide.async_commands.core import InfoSection
from glide.config import ProtocolVersion, ReadFrom
from glide.constants import OK
from glide.glide_client import GlideClusterClient
from glide.routes import AllNodes, SlotIdRoute, SlotType
from tests.conftest import create_client
from tests.utils.utils import get_first_result


@pytest.mark.anyio
# @pytest.mark.usefixtures("multiple_replicas_cluster")
class TestAZAffinity:
    async def _get_num_replicas(self, client: GlideClusterClient) -> int:
        info_replicas = get_first_result(
            await client.info([InfoSection.REPLICATION])
        ).decode()
        match = re.search(r"connected_slaves:(\d+)", info_replicas)
        if match:
            return int(match.group(1))
        else:
            raise ValueError(
                "Could not find the number of replicas in the INFO REPLICATION response"
            )

    @pytest.mark.skip_if_version_below("8.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_routing_with_az_affinity_strategy_to_1_replica(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that the client with az affinity strategy will only route to the 1 replica with the same az"""
        az = "us-east-1a"
        GET_CALLS = 3
        get_cmdstat = "cmdstat_get" + ":" + f"calls={GET_CALLS}"

        client_for_config_set = await create_client(
            request,
            cluster_mode,
            # addresses=multiple_replicas_cluster.nodes_addr,
            protocol=protocol,
            request_timeout=2000,
        )
        assert type(client_for_config_set) is GlideClusterClient
        # Reset the availability zone for all nodes
        await client_for_config_set.custom_command(
            ["CONFIG", "SET", "availability-zone", ""],
            route=AllNodes(),
        )
        assert await client_for_config_set.config_resetstat() == OK

        # 12182 is the slot of "foo"
        await client_for_config_set.custom_command(
            ["CONFIG", "SET", "availability-zone", az],
            route=SlotIdRoute(SlotType.REPLICA, 12182),
        )

        client_for_testing_az = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            read_from=ReadFrom.AZ_AFFINITY,
            request_timeout=2000,
            client_az=az,
        )
        assert type(client_for_testing_az) is GlideClusterClient
        for _ in range(GET_CALLS):
            await client_for_testing_az.get("foo")

        info_result = cast(
            Mapping[bytes, bytes],
            await client_for_testing_az.info(
                [InfoSection.SERVER, InfoSection.COMMAND_STATS], AllNodes()
            ),
        )

        # Check that only the replica with az has all the GET calls
        matching_entries_count = sum(
            1
            for value in info_result.values()
            if get_cmdstat in value.decode() and az in value.decode()
        )
        assert matching_entries_count == 1

        # Check that the other replicas have no availability zone set
        changed_az_count = sum(
            1
            for node in info_result.values()
            if ("availability_zone" + ":" + f"{az}") in node.decode()
        )
        assert changed_az_count == 1
        await client_for_testing_az.close()
        await client_for_config_set.close()

    @pytest.mark.skip_if_version_below("8.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_routing_by_slot_to_replica_with_az_affinity_strategy_to_all_replicas(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """
        Test that the client with AZ affinity strategy routes in a round-robin manner to all replicas within the
        specified AZ
        """

        az = "us-east-1a"
        client_for_config_set = await create_client(
            request,
            cluster_mode,
            # addresses=multiple_replicas_cluster.nodes_addr,
            protocol=protocol,
            request_timeout=2000,
        )
        assert type(client_for_config_set) is GlideClusterClient
        assert await client_for_config_set.config_resetstat() == OK
        await client_for_config_set.custom_command(
            ["CONFIG", "SET", "availability-zone", az], AllNodes()
        )

        client_for_testing_az = await create_client(
            request,
            cluster_mode,
            protocol=protocol,
            read_from=ReadFrom.AZ_AFFINITY,
            request_timeout=2000,
            client_az=az,
        )
        assert type(client_for_testing_az) is GlideClusterClient
        azs = cast(
            Mapping[bytes, Mapping[bytes, bytes]],
            await client_for_testing_az.config_get(["availability-zone"], AllNodes()),
        )

        # Check that all replicas have the availability zone set to the az
        assert all((node[b"availability-zone"].decode() == az) for node in azs.values())

        n_replicas = await self._get_num_replicas(client_for_testing_az)
        GET_CALLS = 4 * n_replicas
        get_cmdstat = "cmdstat_get" + ":" + f"calls={GET_CALLS // n_replicas}"

        for _ in range(GET_CALLS):
            await client_for_testing_az.get("foo")

        info_result = cast(
            Mapping[bytes, bytes],
            await client_for_testing_az.info(
                [InfoSection.COMMAND_STATS, InfoSection.SERVER], AllNodes()
            ),
        )

        # Check that all replicas have the same number of GET calls
        matching_entries_count = sum(
            1
            for value in info_result.values()
            if get_cmdstat in value.decode() and az in value.decode()
        )
        assert matching_entries_count == n_replicas

        await client_for_config_set.close()
        await client_for_testing_az.close()

    @pytest.mark.skip_if_version_below("8.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_az_affinity_non_existing_az(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        GET_CALLS = 4

        client_for_testing_az = await create_client(
            request,
            cluster_mode,
            # addresses=multiple_replicas_cluster.nodes_addr,
            protocol=protocol,
            read_from=ReadFrom.AZ_AFFINITY,
            request_timeout=2000,
            client_az="non-existing-az",
        )
        assert type(client_for_testing_az) is GlideClusterClient
        assert await client_for_testing_az.config_resetstat() == OK

        for _ in range(GET_CALLS):
            await client_for_testing_az.get("foo")

        n_replicas = await self._get_num_replicas(client_for_testing_az)
        # We expect the calls to be distributed evenly among the replicas
        get_cmdstat = "cmdstat_get" + ":" + f"calls={GET_CALLS // n_replicas}"

        info_result = cast(
            Mapping[bytes, bytes],
            await client_for_testing_az.info(
                [InfoSection.COMMAND_STATS, InfoSection.SERVER], AllNodes()
            ),
        )

        matching_entries_count = sum(
            1 for value in info_result.values() if get_cmdstat in value.decode()
        )
        assert matching_entries_count == n_replicas

        await client_for_testing_az.close()

    @pytest.mark.skip_if_version_below("8.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_az_affinity_requires_client_az(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """Test that setting read_from to AZ_AFFINITY without client_az raises an error."""
        with pytest.raises(ValueError):
            await create_client(
                request,
                cluster_mode=cluster_mode,
                protocol=protocol,
                read_from=ReadFrom.AZ_AFFINITY,
                request_timeout=2000,
            )

    @pytest.mark.skip_if_version_below("8.0.0")
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_az_affinity_replicas_and_primary_routes_to_primary(
        self,
        request,
        cluster_mode: bool,
        protocol: ProtocolVersion,
    ):
        """Test that the client with AZ_AFFINITY_REPLICAS_AND_PRIMARY routes to the primary in the same AZ"""
        az = "us-east-1a"
        other_az = "us-east-1b"
        GET_CALLS = 4

        client_for_config_set = None
        client_for_testing_az = None

        try:
            client_for_config_set = await create_client(
                request,
                cluster_mode,
                protocol=protocol,
                request_timeout=2000,
            )

            assert type(client_for_config_set) is GlideClusterClient

            # Reset stats and set all nodes to other_az
            await client_for_config_set.config_resetstat()
            await client_for_config_set.custom_command(
                ["CONFIG", "SET", "availability-zone", other_az],
                AllNodes(),
            )

            # Set primary for slot 12182 to az
            await client_for_config_set.custom_command(
                ["CONFIG", "SET", "availability-zone", az],
                route=SlotIdRoute(SlotType.PRIMARY, 12182),
            )

            # Verify primary AZ
            primary_az = cast(
                Mapping[bytes, bytes],
                await client_for_config_set.custom_command(
                    ["CONFIG", "GET", "availability-zone"],
                    route=SlotIdRoute(SlotType.PRIMARY, 12182),
                ),
            )
            assert (
                primary_az[b"availability-zone"].decode() == az
            ), f"Primary for slot 12182 is not in the expected AZ {az}"

            # Create test client AFTER configuration
            client_for_testing_az = await create_client(
                request,
                cluster_mode,
                protocol=protocol,
                read_from=ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY,
                client_az=az,
                request_timeout=2000,
            )

            # Perform GET operations
            for i in range(GET_CALLS):
                await client_for_testing_az.get("foo")

            assert type(client_for_testing_az) is GlideClusterClient
            # Collect info from all nodes
            result = await client_for_testing_az.info(
                [
                    InfoSection.SERVER,
                    InfoSection.REPLICATION,
                    InfoSection.COMMAND_STATS,
                ],
                AllNodes(),
            )
            info_result = cast(dict[bytes, bytes], result)

            matching_entries_count = 0
            total_get_calls = 0
            node_info_list = []

            for node, node_info in info_result.items():
                info_str = node_info.decode()
                is_primary = "role:master" in info_str
                az_match = re.search(r"availability_zone:(\S+)", info_str)
                node_az = az_match.group(1) if az_match else ""
                get_calls_match = re.search(r"cmdstat_get:calls=(\d+)", info_str)
                get_calls = int(get_calls_match.group(1)) if get_calls_match else 0

                total_get_calls += get_calls
                node_info_list.append((node_az, is_primary, get_calls))

                if is_primary and node_az == az and get_calls == GET_CALLS:
                    matching_entries_count += 1
                elif node_az != az and get_calls > 0:
                    pytest.fail(f"GET calls found on node not in AZ {az}")

            assert matching_entries_count == 1, (
                "Exactly one primary in AZ should handle all calls. "
                f"Matching entries: {matching_entries_count}, Total GET calls: {total_get_calls}"
            )
            assert (
                total_get_calls == GET_CALLS
            ), f"Total GET calls mismatch, expected {GET_CALLS}, got {total_get_calls}"

        finally:
            if client_for_testing_az:
                await client_for_testing_az.close()
            if client_for_config_set:
                await client_for_config_set.close()
