import asyncio
import random
import string
from datetime import datetime, timedelta
from typing import Dict, List, Type

import pytest
from packaging import version
from pybushka.async_commands.core import (
    ConditionalSet,
    ExpirySet,
    ExpiryType,
    Transaction,
)
from pybushka.async_ffi_client import RedisAsyncFFIClient
from pybushka.config import AddressInfo, ClientConfiguration
from pybushka.constants import OK
from pybushka.Logger import Level as logLevel
from pybushka.Logger import set_logger_config
from pybushka.redis_client import BaseRedisClient, RedisClient, RedisClusterClient
from pybushka.routes import (
    AllNodes,
    AllPrimaries,
    RandomNode,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
)

set_logger_config(logLevel.INFO)


@pytest.fixture()
async def redis_client(request, cluster_mode) -> BaseRedisClient:
    "Get async socket client for tests"
    host = request.config.getoption("--host")
    port = request.config.getoption("--port")
    use_tls = request.config.getoption("--tls")
    if cluster_mode:
        seed_nodes = random.sample(pytest.redis_cluster.nodes_addr, k=3)
        config = ClientConfiguration(seed_nodes, use_tls=use_tls)
        client = await RedisClusterClient.create(config)
    else:
        config = ClientConfiguration(
            [AddressInfo(host=host, port=port)], use_tls=use_tls
        )
        client = await RedisClient.create(config)
    yield client
    client.close()


def get_first_result(res: str | List[str] | List[List[str]]) -> str:
    while isinstance(res, list):
        res = (
            res[1]
            if not isinstance(res[0], list) and res[0].startswith("127.0.0.1")
            else res[0]
        )

    return res


def parse_info_response(res: str | List[str] | List[List[str]]) -> Dict[str, str]:
    res = get_first_result(res)
    info_lines = [
        line for line in res.splitlines() if line and not line.startswith("#")
    ]
    info_dict = {}
    for line in info_lines:
        key, value = line.split(":")
        info_dict[key] = value
    return info_dict


def get_random_string(length):
    result_str = "".join(random.choice(string.ascii_letters) for i in range(length))
    return result_str


async def check_if_server_version_lt(client: BaseRedisClient, min_version: str) -> bool:
    # TODO: change it to pytest fixture after we'll implement a sync client
    info_str = await client.custom_command(["INFO", "server"])
    redis_version = parse_info_response(info_str).get("redis_version")
    return version.parse(redis_version) < version.parse(min_version)


@pytest.mark.asyncio
class TestSocketClient:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_socket_set_get(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        value = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        assert await redis_client.set(key, value) == OK
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_large_values(self, redis_client: BaseRedisClient):
        length = 2**16
        key = get_random_string(length)
        value = get_random_string(length)
        assert len(key) == length
        assert len(value) == length
        await redis_client.set(key, value)
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_non_ascii_unicode(self, redis_client: BaseRedisClient):
        key = "foo"
        value = "שלום hello 汉字"
        assert value == "שלום hello 汉字"
        await redis_client.set(key, value)
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("value_size", [100, 2**16])
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_concurrent_tasks(self, redis_client: BaseRedisClient, value_size):
        num_of_concurrent_tasks = 100
        running_tasks = set()

        async def exec_command(i):
            range_end = 1 if value_size > 100 else 100
            for _ in range(range_end):
                value = get_random_string(value_size)
                assert await redis_client.set(str(i), value) == OK
                assert await redis_client.get(str(i)) == value

        for i in range(num_of_concurrent_tasks):
            task = asyncio.create_task(exec_command(i))
            running_tasks.add(task)
            task.add_done_callback(running_tasks.discard)
        await asyncio.gather(*(list(running_tasks)))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_conditional_set(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        res = await redis_client.set(
            key, value, conditional_set=ConditionalSet.ONLY_IF_EXISTS
        )
        assert res is None
        res = await redis_client.set(
            key, value, conditional_set=ConditionalSet.ONLY_IF_DOES_NOT_EXIST
        )
        assert res == OK
        assert await redis_client.get(key) == value
        res = await redis_client.set(
            key, "foobar", conditional_set=ConditionalSet.ONLY_IF_DOES_NOT_EXIST
        )
        assert res is None
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_set_return_old_value(self, redis_client: BaseRedisClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")
        key = get_random_string(10)
        value = get_random_string(10)
        res = await redis_client.set(key, value)
        assert res == OK
        assert await redis_client.get(key) == value
        new_value = get_random_string(10)
        res = await redis_client.set(key, new_value, return_old_value=True)
        assert res == value
        assert await redis_client.get(key) == new_value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_custom_command_single_arg(self, redis_client: BaseRedisClient):
        # Test single arg command
        res: str = await redis_client.custom_command(["INFO"])
        info_dict = parse_info_response(res)
        assert info_dict.get("redis_version") is not None
        assert (
            info_dict.get("connected_clients").isdigit()
            and int(info_dict.get("connected_clients")) >= 1
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_custom_command_multi_arg(self, redis_client: BaseRedisClient):
        # Test multi args command
        res: str = get_first_result(
            await redis_client.custom_command(["CLIENT", "LIST", "TYPE", "NORMAL"])
        )
        assert res is not None
        assert "id" in res
        assert "cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_custom_command_lower_and_upper_case(
        self, redis_client: BaseRedisClient
    ):
        # Test multi args command
        res: str = get_first_result(
            await redis_client.custom_command(["client", "LIST", "type", "NORMAL"])
        )
        assert res is not None
        assert "id" in res
        assert "cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_request_error_raises_exception(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        await redis_client.set(key, value)
        with pytest.raises(Exception) as e:
            await redis_client.custom_command(["HSET", key, "1", "bar"])
        assert "WRONGTYPE" in str(e)


@pytest.mark.asyncio
class TestTransaction:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_set_get(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        value = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        transaction = Transaction()
        transaction.set(key, value)
        transaction.get(key)
        result = await redis_client.exec(transaction)
        assert result == [OK, value]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_multiple_commands(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        key2 = "{{{}}}:{}".format(key, get_random_string(3))  # to get the same slot
        value = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        transaction = Transaction()
        transaction.set(key, value)
        transaction.get(key)
        transaction.set(key2, value)
        transaction.get(key2)
        result = await redis_client.exec(transaction)
        assert result == [OK, value, OK, value]

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_transaction_with_different_slots(
        self, redis_client: BaseRedisClient
    ):
        transaction = Transaction()
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "Moved" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_custom_command(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        transaction = Transaction()
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = await redis_client.exec(transaction)
        assert result == [1, "bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_custom_unsupported_command(
        self, redis_client: BaseRedisClient
    ):
        key = get_random_string(10)
        transaction = Transaction()
        transaction.custom_command(["WATCH", key])
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "WATCH inside MULTI is not allowed" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_discard_command(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        await redis_client.set(key, "1")
        transaction = Transaction()
        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await redis_client.get(key)
        assert value == "1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_exec_abort(self, redis_client: BaseRedisClient):
        key = get_random_string(10)
        transaction = Transaction()
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "wrong number of arguments" in str(
            e
        )  # TODO : add an assert on EXEC ABORT


class CommandsUnitTests:
    def test_expiry_cmd_args(self):
        exp_sec = ExpirySet(ExpiryType.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpirySet(ExpiryType.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpirySet(ExpiryType.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpirySet(ExpiryType.MILLSEC, timedelta(seconds=5))
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpirySet(ExpiryType.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpirySet(
            ExpiryType.UNIX_SEC, datetime(2023, 4, 27, 23, 55, 59, 342380)
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpirySet(ExpiryType.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpirySet(
            ExpiryType.UNIX_MILLSEC, datetime(2023, 4, 27, 23, 55, 59, 342380)
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

    def test_expiry_raises_on_value_error(self):
        with pytest.raises(ValueError):
            ExpirySet(ExpiryType.SEC, 5.5)


@pytest.mark.asyncio
class TestClusterRoutes:
    async def cluster_route_custom_command_multi_nodes(
        self, redis_client: BaseRedisClient, route: Type["RandomNode"]
    ):
        cluster_nodes = await redis_client.custom_command(["CLUSTER", "NODES"])
        num_of_nodes = len(cluster_nodes.splitlines())
        expected_num_of_results = (
            num_of_nodes
            if type(route) == AllNodes
            else num_of_nodes - cluster_nodes.count("slave")
        )
        expected_primary_count = cluster_nodes.count("master")
        expected_replica_count = (
            cluster_nodes.count("slave") if type(route) == AllNodes else 0
        )

        all_results = await redis_client.custom_command(["INFO", "REPLICATION"], route)
        assert len(all_results) == expected_num_of_results
        primary_count = 0
        replica_count = 0
        for info_res in all_results:
            info_res = info_res[1]
            assert "role:master" in info_res or "role:slave" in info_res
            if "role:master" in info_res:
                primary_count += 1
            else:
                replica_count += 1
        assert primary_count == expected_primary_count
        assert replica_count == expected_replica_count

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_cluster_route_custom_command_all_nodes(
        self, redis_client: BaseRedisClient
    ):
        await self.cluster_route_custom_command_multi_nodes(redis_client, AllNodes())

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_cluster_route_custom_command_all_primaries(
        self, redis_client: BaseRedisClient
    ):
        await self.cluster_route_custom_command_multi_nodes(
            redis_client, AllPrimaries()
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_cluster_route_custom_command_random_node(
        self, redis_client: BaseRedisClient
    ):
        info_res = await redis_client.custom_command(
            ["INFO", "REPLICATION"], RandomNode()
        )
        assert type(info_res) is str
        assert "role:master" in info_res or "role:slave" in info_res

    async def cluster_route_custom_command_slot_route(
        self, redis_client: BaseRedisClient, is_slot_key: bool
    ):
        route_class = SlotKeyRoute if is_slot_key else SlotIdRoute
        route_second_arg = "foo" if is_slot_key else 4000
        primary_res = await redis_client.custom_command(
            ["CLUSTER", "NODES"], route_class(SlotType.PRIMARY, route_second_arg)
        )
        assert type(primary_res) is str
        assert "myself,master" in primary_res
        expected_primary_node_id = ""
        for node_line in primary_res.splitlines():
            if "myself" in node_line:
                expected_primary_node_id = node_line.split(" ")[0]

        replica_res = await redis_client.custom_command(
            ["CLUSTER", "NODES"], route_class(SlotType.REPLICA, route_second_arg)
        )
        assert "myself,slave" in replica_res
        for node_line in replica_res:
            if "myself" in node_line:
                primary_node_id = node_line.split(" ")[3]
                assert primary_node_id == expected_primary_node_id

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_cluster_route_custom_command_slot_key_route(
        self, redis_client: BaseRedisClient
    ):
        await self.cluster_route_custom_command_slot_route(redis_client, True)

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_cluster_route_custom_command_slot_id_route(
        self, redis_client: BaseRedisClient
    ):
        await self.cluster_route_custom_command_slot_route(redis_client, False)


@pytest.mark.asyncio
class TestFFICoreCommands:
    async def test_ffi_set_get(self, async_ffi_client: RedisAsyncFFIClient):
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        await async_ffi_client.set("key", time_str)
        result = await async_ffi_client.get("key")
        assert result == time_str


@pytest.mark.asyncio
class TestPipeline:
    async def test_set_get_pipeline(self, async_ffi_client: RedisAsyncFFIClient):
        pipeline = async_ffi_client.create_pipeline()
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        pipeline.set("pipeline_key", time_str)
        pipeline.get("pipeline_key")
        result = await pipeline.execute()
        assert result == ["OK", time_str]

    async def test_set_get_pipeline_chained_requests(
        self, async_ffi_client: RedisAsyncFFIClient
    ):
        pipeline = async_ffi_client.create_pipeline()
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        result = (
            await pipeline.set("pipeline_key", time_str).get("pipeline_key").execute()
        )
        assert result == ["OK", time_str]

    async def test_set_with_ignored_result(self, async_ffi_client: RedisAsyncFFIClient):
        pipeline = async_ffi_client.create_pipeline()
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        result = (
            await pipeline.set("pipeline_key", time_str, True)
            .get("pipeline_key")
            .execute()
        )
        assert result == [time_str]
