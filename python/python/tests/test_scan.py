from typing import AsyncGenerator, List, cast

import anyio
import pytest

from glide import ByAddressRoute
from glide.async_commands.command_args import ObjectType
from glide.config import ProtocolVersion
from glide.exceptions import RequestError
from glide.glide import ClusterScanCursor
from glide.glide_client import GlideClient, GlideClusterClient
from tests.conftest import create_client
from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import get_random_string


# Helper function to get a number of nodes, and ask the cluster till we get the number of nodes
async def is_cluster_ready(client: GlideClusterClient, count: int) -> bool:
    # we allow max 20 seconds to get the nodes
    timeout = 20
    start_time = anyio.current_time()

    while True:
        if anyio.current_time() - start_time > timeout:
            return False

        cluster_info = await client.custom_command(["CLUSTER", "INFO"])
        cluster_info_map = {}

        if cluster_info:
            info_str = (
                cluster_info
                if isinstance(cluster_info, str)
                else (
                    cluster_info.decode()
                    if isinstance(cluster_info, bytes)
                    else str(cluster_info)
                )
            )
            cluster_info_lines = info_str.split("\n")
            cluster_info_lines = [line for line in cluster_info_lines if line]

            for line in cluster_info_lines:
                key, value = line.split(":")
                cluster_info_map[key.strip()] = value.strip()

            if (
                cluster_info_map.get("cluster_state") == "ok"
                and int(cluster_info_map.get("cluster_known_nodes", "0")) == count
            ):
                break

        await anyio.sleep(2)

    return True


# The slots not covered testing is messing with the cluster by removing a node, and then scanning the cluster
# When a node is forgot its getting into a banned state for one minutes, so in order to bring back the cluster to normal state
# we need to wait for the node to be unbanned, and then we can continue with the tests
# In order to avoid the time wasting and the chance that the restoration will not happen, we will run the test on separate
# cluster
@pytest.fixture(scope="function")
async def function_scoped_cluster():
    """
    Function-scoped fixture to create a new cluster for each test invocation.
    """
    cluster = ValkeyCluster(
        tls=False, cluster_mode=True, shard_count=3, replica_count=0
    )
    yield cluster
    del cluster


# Since the cluster for slots covered is created separately, we need to create a client for the specific cluster
# The client is created with 100 timeout so looping over the keys with scan will return the error before we finish the loop
# otherwise the test will be flaky
@pytest.fixture(scope="function")
async def glide_client_scoped(
    request, function_scoped_cluster: ValkeyCluster, protocol: ProtocolVersion
) -> AsyncGenerator[GlideClusterClient, None]:
    """
    Get client for tests, adjusted to use the function-scoped cluster.
    """
    client = await create_client(
        request,
        True,
        valkey_cluster=function_scoped_cluster,
        protocol=protocol,
    )
    assert isinstance(client, GlideClusterClient)
    yield client
    await client.close()


@pytest.mark.anyio
class TestScan:
    # Cluster scan tests
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_simple(self, glide_client: GlideClusterClient):
        key = get_random_string(10)
        expected_keys = [f"{key}{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        expected_keys_encoded = map(lambda k: k.encode(), expected_keys)
        cursor = ClusterScanCursor()
        keys: List[str] = []
        while not cursor.is_finished():
            result = await glide_client.scan(cursor)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = cast(List[str], result[1])
            keys.extend(result_keys)

        assert set(expected_keys_encoded) == set(keys)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_with_object_type_and_pattern(
        self, glide_client: GlideClusterClient
    ):
        key = get_random_string(10)
        expected_keys = [f"key-{key}-{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        unexpected_type_keys = [f"{key}-{i}" for i in range(100, 200)]
        for key in unexpected_type_keys:
            await glide_client.sadd(key, ["value"])
        encoded_unexpected_type_keys = map(lambda k: k.encode(), unexpected_type_keys)
        unexpected_pattern_keys = [f"{i}" for i in range(200, 300)]
        await glide_client.mset({k: "value" for k in unexpected_pattern_keys})
        encoded_unexpected_pattern_keys = map(
            lambda k: k.encode(), unexpected_pattern_keys
        )
        keys: List[str] = []
        cursor = ClusterScanCursor()
        while not cursor.is_finished():
            result = await glide_client.scan(
                cursor, match=b"key-*", type=ObjectType.STRING
            )
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = cast(List[str], result[1])
            keys.extend(result_keys)

        assert set(encoded_expected_keys) == set(keys)
        assert not set(encoded_unexpected_type_keys).intersection(set(keys))
        assert not set(encoded_unexpected_pattern_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_with_count(self, glide_client: GlideClusterClient):
        key = get_random_string(10)
        expected_keys = [f"{key}{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        cursor = ClusterScanCursor()
        keys: List[str] = []
        successful_compared_scans = 0
        while not cursor.is_finished():
            result_of_1 = await glide_client.scan(cursor, count=1)
            cursor = cast(ClusterScanCursor, result_of_1[0])
            result_keys_of_1 = cast(List[str], result_of_1[1])
            keys.extend(result_keys_of_1)
            if cursor.is_finished():
                break
            result_of_100 = await glide_client.scan(cursor, count=100)
            cursor = cast(ClusterScanCursor, result_of_100[0])
            result_keys_of_100 = cast(List[str], result_of_100[1])
            keys.extend(result_keys_of_100)
            if len(result_keys_of_100) > len(result_keys_of_1):
                successful_compared_scans += 1

        assert set(encoded_expected_keys) == set(keys)
        assert successful_compared_scans > 0

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_with_match(self, glide_client: GlideClusterClient):
        unexpected_keys = [f"{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in unexpected_keys})
        encoded_unexpected_keys = map(lambda k: k.encode(), unexpected_keys)
        key = get_random_string(10)
        expected_keys = [f"key-{key}-{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        cursor = ClusterScanCursor()
        keys: List[str] = []
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, match="key-*")
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = cast(List[str], result[1])
            keys.extend(result_keys)
        assert set(encoded_expected_keys) == set(keys)
        assert not set(encoded_unexpected_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    # We test whether the cursor is cleaned up after it is deleted, which we expect to happen when th GC is called
    async def test_cluster_scan_cleaning_cursor(self, glide_client: GlideClusterClient):
        key = get_random_string(10)
        await glide_client.mset({k: "value" for k in [f"{key}{i}" for i in range(100)]})
        cursor = cast(
            ClusterScanCursor, (await glide_client.scan(ClusterScanCursor()))[0]
        )
        cursor_string = cursor.get_cursor()
        del cursor
        new_cursor_with_same_id = ClusterScanCursor(cursor_string)
        with pytest.raises(RequestError) as e_info:
            await glide_client.scan(new_cursor_with_same_id)
        assert "Invalid scan_state_cursor id" in str(e_info.value)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_all_types(self, glide_client: GlideClusterClient):
        # We test that the scan command work for all types of keys
        key = get_random_string(10)
        string_keys = [f"{key}{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in string_keys})
        encoded_string_keys = list(map(lambda k: k.encode(), string_keys))

        set_key = get_random_string(10)
        set_keys = [f"{set_key}{i}" for i in range(100, 200)]
        for key in set_keys:
            await glide_client.sadd(key, ["value"])
        encoded_set_keys = list(map(lambda k: k.encode(), set_keys))

        hash_key = get_random_string(10)
        hash_keys = [f"{hash_key}{i}" for i in range(200, 300)]
        for key in hash_keys:
            await glide_client.hset(key, {"field": "value"})
        encoded_hash_keys = list(map(lambda k: k.encode(), hash_keys))

        list_key = get_random_string(10)
        list_keys = [f"{list_key}{i}" for i in range(300, 400)]
        for key in list_keys:
            await glide_client.lpush(key, ["value"])
        encoded_list_keys = list(map(lambda k: k.encode(), list_keys))

        zset_key = get_random_string(10)
        zset_keys = [f"{zset_key}{i}" for i in range(400, 500)]
        for key in zset_keys:
            await glide_client.zadd(key, {"value": 1})
        encoded_zset_keys = list(map(lambda k: k.encode(), zset_keys))

        stream_key = get_random_string(10)
        stream_keys = [f"{stream_key}{i}" for i in range(500, 600)]
        for key in stream_keys:
            await glide_client.xadd(key, [("field", "value")])
        encoded_stream_keys = list(map(lambda k: k.encode(), stream_keys))

        cursor = ClusterScanCursor()
        keys: List[bytes] = []
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, type=ObjectType.STRING)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(List[bytes], result_keys))
        assert set(encoded_string_keys) == set(keys)
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, type=ObjectType.SET)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(List[bytes], result_keys))
        assert set(encoded_set_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, type=ObjectType.HASH)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(List[bytes], result_keys))
        assert set(encoded_hash_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, type=ObjectType.LIST)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(List[bytes], result_keys))
        assert set(encoded_list_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, type=ObjectType.ZSET)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(List[bytes], result_keys))
        assert set(encoded_zset_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))

        cursor = ClusterScanCursor()
        keys.clear()
        while not cursor.is_finished():
            result = await glide_client.scan(cursor, type=ObjectType.STREAM)
            cursor = cast(ClusterScanCursor, result[0])
            result_keys = result[1]
            keys.extend(cast(List[bytes], result_keys))
        assert set(encoded_stream_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))

    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_scan_non_covered_slots(
        self,
        protocol: ProtocolVersion,
        function_scoped_cluster: ValkeyCluster,
        glide_client_scoped: GlideClusterClient,
    ):
        key = get_random_string(10)
        for i in range(1000):
            await glide_client_scoped.set(f"{key}{i}", "value")
        cursor = ClusterScanCursor()
        result = await glide_client_scoped.scan(cursor)
        cursor = cast(ClusterScanCursor, result[0])
        assert not cursor.is_finished()
        await glide_client_scoped.config_set({"cluster-require-full-coverage": "no"})
        # forget one server
        address_to_forget = glide_client_scoped.config.addresses[0]
        all_other_addresses = glide_client_scoped.config.addresses[1:]
        id_to_forget = await glide_client_scoped.custom_command(
            ["CLUSTER", "MYID"],
            ByAddressRoute(address_to_forget.host, address_to_forget.port),
        )
        for address in all_other_addresses:
            await glide_client_scoped.custom_command(
                ["CLUSTER", "FORGET", cast(str, id_to_forget)],
                ByAddressRoute(address.host, address.port),
            )
        # now we let it few seconds gossip to get the new cluster configuration
        await is_cluster_ready(glide_client_scoped, len(all_other_addresses))
        # Iterate scan until error is returned, as it might take time for the inner core to forget the missing node
        cursor = ClusterScanCursor()
        while True:
            try:
                while not cursor.is_finished():
                    result = await glide_client_scoped.scan(cursor)
                    cursor = cast(ClusterScanCursor, result[0])
                # Reset cursor for next iteration
                cursor = ClusterScanCursor()
            except RequestError as e_info:
                assert (
                    "Could not find an address covering a slot, SCAN operation cannot continue"
                    in str(e_info)
                )
                break
        # Scan with allow_non_covered_slots=True
        while not cursor.is_finished():
            result = await glide_client_scoped.scan(
                cursor, allow_non_covered_slots=True
            )
            cursor = cast(ClusterScanCursor, result[0])
        assert cursor.is_finished()
        # check the keys we can get with keys command, and scan from the beginning
        keys = []
        for address in all_other_addresses:
            result_keys = cast(
                List[bytes],
                await glide_client_scoped.custom_command(
                    ["KEYS", "*"], ByAddressRoute(address.host, address.port)
                ),
            )
            keys.extend(cast(List[bytes], result_keys))

        cursor = ClusterScanCursor()
        results = []
        while not cursor.is_finished():
            result = await glide_client_scoped.scan(
                cursor, allow_non_covered_slots=True
            )
            results.extend(cast(List[bytes], result[1]))
            cursor = cast(ClusterScanCursor, result[0])
        assert set(keys) == set(results)

    # Standalone scan tests
    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_simple(self, glide_client: GlideClient):
        key = get_random_string(10)
        expected_keys = [f"{key}{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        keys: List[str] = []
        cursor = b"0"
        while True:
            result = await glide_client.scan(cursor)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes
            new_keys = cast(List[str], result[1])
            keys.extend(new_keys)
            if cursor == b"0":
                break
        assert set(encoded_expected_keys) == set(keys)

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_with_object_type_and_pattern(
        self, glide_client: GlideClient
    ):
        key = get_random_string(10)
        expected_keys = [f"key-{key}-{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        unexpected_type_keys = [f"key-{i}" for i in range(100, 200)]
        for key in unexpected_type_keys:
            await glide_client.sadd(key, ["value"])
        unexpected_pattern_keys = [f"{i}" for i in range(200, 300)]
        for key in unexpected_pattern_keys:
            await glide_client.set(key, "value")
        keys: List[str] = []
        cursor = b"0"
        while True:
            result = await glide_client.scan(
                cursor, match=b"key-*", type=ObjectType.STRING
            )
            cursor = cast(bytes, result[0])
            keys.extend(list(map(lambda k: k.decode(), cast(List[bytes], result[1]))))
            if cursor == b"0":
                break
        assert set(expected_keys) == set(keys)
        assert not set(unexpected_type_keys).intersection(set(keys))
        assert not set(unexpected_pattern_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_with_count(self, glide_client: GlideClient):
        key = get_random_string(10)
        expected_keys = [f"{key}{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        cursor = "0"
        keys: List[str] = []
        successful_compared_scans = 0
        while True:
            result_of_1 = await glide_client.scan(cursor, count=1)
            cursor_bytes = cast(bytes, result_of_1[0])
            cursor = cursor_bytes.decode()
            keys_of_1 = cast(List[str], result_of_1[1])
            keys.extend(keys_of_1)
            result_of_100 = await glide_client.scan(cursor, count=100)
            cursor_bytes = cast(bytes, result_of_100[0])
            cursor = cursor_bytes.decode()
            keys_of_100 = cast(List[str], result_of_100[1])
            keys.extend(keys_of_100)
            if len(keys_of_100) > len(keys_of_1):
                successful_compared_scans += 1
            if cursor == "0":
                break
        assert set(encoded_expected_keys) == set(keys)
        assert successful_compared_scans > 0

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_with_match(self, glide_client: GlideClient):
        key = get_random_string(10)
        expected_keys = [f"key-{key}-{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in expected_keys})
        encoded_expected_keys = map(lambda k: k.encode(), expected_keys)
        unexpected_keys = [f"{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in [f"{i}" for i in range(100)]})
        encoded_unexpected_keys = map(lambda k: k.encode(), unexpected_keys)
        cursor = "0"
        keys: List[str] = []
        while True:
            result = await glide_client.scan(cursor, match="key-*")
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = cast(List[str], result[1])
            keys.extend(new_keys)
            if cursor == "0":
                break
        assert set(encoded_expected_keys) == set(keys)
        assert not set(encoded_unexpected_keys).intersection(set(keys))

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_scan_all_types(self, glide_client: GlideClient):
        # We test that the scan command work for all types of keys
        (
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
        ) = await self.create_keys(glide_client)

        cursor = "0"
        keys: List[bytes] = []
        cursor = await self.scan_string(
            glide_client,
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
            cursor,
            keys,
        )

        keys.clear()
        cursor = await self.scan_set(
            glide_client,
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
            cursor,
            keys,
        )

        keys.clear()
        cursor = await self.scan_hash(
            glide_client,
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
            cursor,
            keys,
        )

        keys.clear()
        cursor = await self.scan_list(
            glide_client,
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
            cursor,
            keys,
        )

        keys.clear()
        cursor = await self.scan_zset(
            glide_client,
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
            cursor,
            keys,
        )

        keys.clear()
        await self.scan_stream(
            glide_client,
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
            cursor,
            keys,
        )

    async def scan_stream(
        self,
        glide_client,
        encoded_string_keys,
        encoded_set_keys,
        encoded_hash_keys,
        encoded_list_keys,
        encoded_zset_keys,
        encoded_stream_keys,
        cursor,
        keys,
    ):
        while True:
            result = await glide_client.scan(cursor, type=ObjectType.STREAM)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(List[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_stream_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))

    async def scan_zset(
        self,
        glide_client,
        encoded_string_keys,
        encoded_set_keys,
        encoded_hash_keys,
        encoded_list_keys,
        encoded_zset_keys,
        encoded_stream_keys,
        cursor,
        keys,
    ):
        while True:
            result = await glide_client.scan(cursor, type=ObjectType.ZSET)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(List[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_zset_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))
        return cursor

    async def scan_list(
        self,
        glide_client,
        encoded_string_keys,
        encoded_set_keys,
        encoded_hash_keys,
        encoded_list_keys,
        encoded_zset_keys,
        encoded_stream_keys,
        cursor,
        keys,
    ):
        while True:
            result = await glide_client.scan(cursor, type=ObjectType.LIST)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(List[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_list_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))
        return cursor

    async def scan_hash(
        self,
        glide_client,
        encoded_string_keys,
        encoded_set_keys,
        encoded_hash_keys,
        encoded_list_keys,
        encoded_zset_keys,
        encoded_stream_keys,
        cursor,
        keys,
    ):
        while True:
            result = await glide_client.scan(cursor, type=ObjectType.HASH)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(List[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_hash_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))
        return cursor

    async def scan_set(
        self,
        glide_client,
        encoded_string_keys,
        encoded_set_keys,
        encoded_hash_keys,
        encoded_list_keys,
        encoded_zset_keys,
        encoded_stream_keys,
        cursor,
        keys,
    ):
        while True:
            result = await glide_client.scan(cursor, type=ObjectType.SET)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(List[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_set_keys) == set(keys)
        assert not set(encoded_string_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))
        return cursor

    async def scan_string(
        self,
        glide_client,
        encoded_string_keys,
        encoded_set_keys,
        encoded_hash_keys,
        encoded_list_keys,
        encoded_zset_keys,
        encoded_stream_keys,
        cursor,
        keys,
    ):
        while True:
            result = await glide_client.scan(cursor, type=ObjectType.STRING)
            cursor_bytes = cast(bytes, result[0])
            cursor = cursor_bytes.decode()
            new_keys = result[1]
            keys.extend(cast(List[bytes], new_keys))
            if cursor == "0":
                break
        assert set(encoded_string_keys) == set(keys)
        assert not set(encoded_set_keys).intersection(set(keys))
        assert not set(encoded_hash_keys).intersection(set(keys))
        assert not set(encoded_list_keys).intersection(set(keys))
        assert not set(encoded_zset_keys).intersection(set(keys))
        assert not set(encoded_stream_keys).intersection(set(keys))
        return cursor

    async def create_keys(self, glide_client):
        key = get_random_string(10)
        string_keys = [f"{key}{i}" for i in range(100)]
        await glide_client.mset({k: "value" for k in string_keys})
        encoded_string_keys = list(map(lambda k: k.encode(), string_keys))

        set_keys = [f"{key}{i}" for i in range(100, 200)]
        for key in set_keys:
            await glide_client.sadd(key, ["value"])
        encoded_set_keys = list(map(lambda k: k.encode(), set_keys))

        hash_keys = [f"{key}{i}" for i in range(200, 300)]
        for key in hash_keys:
            await glide_client.hset(key, {"field": "value"})
        encoded_hash_keys = list(map(lambda k: k.encode(), hash_keys))

        list_keys = [f"{key}{i}" for i in range(300, 400)]
        for key in list_keys:
            await glide_client.lpush(key, ["value"])
        encoded_list_keys = list(map(lambda k: k.encode(), list_keys))

        zset_keys = [f"{key}{i}" for i in range(400, 500)]
        for key in zset_keys:
            await glide_client.zadd(key, {"value": 1})
        encoded_zset_keys = list(map(lambda k: k.encode(), zset_keys))

        stream_keys = [f"{key}{i}" for i in range(500, 600)]
        for key in stream_keys:
            await glide_client.xadd(key, [("field", "value")])
        encoded_stream_keys = list(map(lambda k: k.encode(), stream_keys))

        return (
            encoded_string_keys,
            encoded_set_keys,
            encoded_hash_keys,
            encoded_list_keys,
            encoded_zset_keys,
            encoded_stream_keys,
        )
