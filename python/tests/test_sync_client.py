# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
# mypy: disable_error_code="arg-type"

from __future__ import annotations

import pytest
from glide.config import ProtocolVersion
from glide.constants import OK, TResult
from glide.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
)
from glide.sync import GlideClusterClient, TGlideClient

from tests.utils.utils import get_first_result, get_random_string


class TestGlideClients:
    @pytest.mark.skip_if_version_below("6.2.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_set_return_old_value(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        value = get_random_string(10)
        res = glide_sync_client.set(key, value)
        assert res == OK
        assert glide_sync_client.get(key) == value.encode()
        new_value = get_random_string(10)
        res = glide_sync_client.set(key, new_value, return_old_value=True)
        assert res == value.encode()
        assert glide_sync_client.get(key) == new_value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_echo(self, glide_sync_client: TGlideClient):
        message = get_random_string(5)
        assert glide_sync_client.echo(message) == message.encode()
        if isinstance(glide_sync_client, GlideClusterClient):
            echo_dict = glide_sync_client.echo(message, AllNodes())
            assert isinstance(echo_dict, dict)
            for value in echo_dict.values():
                assert value == message.encode()


class TestClusterRoutes:
    def cluster_route_custom_command_multi_nodes(
        self,
        glide_sync_client: GlideClusterClient,
        route: Route,
    ):
        cluster_nodes = glide_sync_client.custom_command(["CLUSTER", "NODES"])
        assert isinstance(cluster_nodes, bytes)
        cluster_nodes = cluster_nodes.decode()
        assert isinstance(cluster_nodes, (str, list))
        cluster_nodes = get_first_result(cluster_nodes)
        num_of_nodes = len(cluster_nodes.splitlines())
        assert isinstance(cluster_nodes, (str, list))
        expected_num_of_results = (
            num_of_nodes
            if isinstance(route, AllNodes)
            else num_of_nodes - cluster_nodes.count("slave")
        )
        expected_primary_count = cluster_nodes.count("master")
        expected_replica_count = (
            cluster_nodes.count("slave") if isinstance(route, AllNodes) else 0
        )

        all_results = glide_sync_client.custom_command(["INFO", "REPLICATION"], route)
        assert isinstance(all_results, dict)
        assert len(all_results) == expected_num_of_results
        primary_count = 0
        replica_count = 0
        for _, info_res in all_results.items():
            assert isinstance(info_res, bytes)
            info_res = info_res.decode()
            assert "role:master" in info_res or "role:slave" in info_res
            if "role:master" in info_res:
                primary_count += 1
            else:
                replica_count += 1
        assert primary_count == expected_primary_count
        assert replica_count == expected_replica_count

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_route_custom_command_all_nodes(
        self, glide_sync_client: GlideClusterClient
    ):
        self.cluster_route_custom_command_multi_nodes(glide_sync_client, AllNodes())

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_route_custom_command_all_primaries(
        self, glide_sync_client: GlideClusterClient
    ):
        self.cluster_route_custom_command_multi_nodes(glide_sync_client, AllPrimaries())

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_route_custom_command_random_node(
        self, glide_sync_client: GlideClusterClient
    ):
        info_res = glide_sync_client.custom_command(
            ["INFO", "REPLICATION"], RandomNode()
        )
        assert isinstance(info_res, bytes)
        info_res = info_res.decode()
        assert type(info_res) is str
        assert "role:master" in info_res or "role:slave" in info_res

    def cluster_route_custom_command_slot_route(
        self, glide_sync_client: GlideClusterClient, is_slot_key: bool
    ):
        route_class = SlotKeyRoute if is_slot_key else SlotIdRoute
        route_second_arg = "foo" if is_slot_key else 4000
        primary_res = glide_sync_client.custom_command(
            ["CLUSTER", "NODES"],
            route_class(SlotType.PRIMARY, route_second_arg),  # type: ignore
        )
        assert isinstance(primary_res, bytes)
        primary_res = primary_res.decode()

        assert type(primary_res) is str
        assert "myself,master" in primary_res
        expected_primary_node_id = ""
        for node_line in primary_res.splitlines():
            if "myself" in node_line:
                expected_primary_node_id = node_line.split(" ")[0]

        replica_res = glide_sync_client.custom_command(
            ["CLUSTER", "NODES"],
            route_class(SlotType.REPLICA, route_second_arg),  # type: ignore
        )
        assert isinstance(replica_res, bytes)
        replica_res = replica_res.decode()

        assert isinstance(replica_res, str)
        assert "myself,slave" in replica_res
        for node_line in replica_res:
            if "myself" in node_line:
                primary_node_id = node_line.split(" ")[3]
                assert primary_node_id == expected_primary_node_id

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_route_custom_command_slot_key_route(
        self, glide_sync_client: GlideClusterClient
    ):
        self.cluster_route_custom_command_slot_route(glide_sync_client, True)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_route_custom_command_slot_id_route(
        self, glide_sync_client: GlideClusterClient
    ):
        self.cluster_route_custom_command_slot_route(glide_sync_client, False)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_route_by_address_reaches_correct_node(
        self, glide_sync_client: GlideClusterClient
    ):
        # returns the line that contains the word "myself", up to that point. This is done because the values after it might
        # change with time.
        def clean_result(value: TResult):
            assert type(value) is str
            for line in value.splitlines():
                if "myself" in line:
                    return line.split("myself")[0]
            raise Exception(
                f"Couldn't find 'myself' in the cluster nodes output: {value}"
            )

        cluster_nodes = glide_sync_client.custom_command(
            ["cluster", "nodes"], RandomNode()
        )
        assert isinstance(cluster_nodes, bytes)
        cluster_nodes = clean_result(cluster_nodes.decode())

        assert isinstance(cluster_nodes, str)
        host = cluster_nodes.split(" ")[1].split("@")[0]

        second_result = glide_sync_client.custom_command(
            ["cluster", "nodes"], ByAddressRoute(host)
        )
        assert isinstance(second_result, bytes)
        second_result = clean_result(second_result.decode())

        assert cluster_nodes == second_result

        host, port = host.split(":")
        port_as_int = int(port)

        third_result = glide_sync_client.custom_command(
            ["cluster", "nodes"], ByAddressRoute(host, port_as_int)
        )
        assert isinstance(third_result, bytes)
        third_result = clean_result(third_result.decode())

        assert cluster_nodes == third_result
