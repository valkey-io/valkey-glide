# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import asyncio
import copy
import math
import time
from collections.abc import Mapping
from datetime import date, datetime, timedelta, timezone
from typing import Any, Dict, List, Union, cast

import pytest
from glide import ClosingError, RequestError, Script
from glide.async_commands.bitmap import (
    BitFieldGet,
    BitFieldIncrBy,
    BitFieldOverflow,
    BitFieldSet,
    BitmapIndexType,
    BitOffset,
    BitOffsetMultiplier,
    BitOverflowControl,
    BitwiseOperation,
    OffsetOptions,
    SignedEncoding,
    UnsignedEncoding,
)
from glide.async_commands.command_args import Limit, ListDirection, OrderBy
from glide.async_commands.core import (
    ConditionalChange,
    ExpireOptions,
    ExpiryGetEx,
    ExpirySet,
    ExpiryType,
    ExpiryTypeGetEx,
    FlushMode,
    InfBound,
    InfoSection,
    InsertPosition,
    UpdateOptions,
)
from glide.async_commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeoSearchCount,
    GeospatialData,
    GeoUnit,
    InfBound,
    LexBoundary,
    RangeByIndex,
    RangeByLex,
    RangeByScore,
    ScoreBoundary,
    ScoreFilter,
)
from glide.async_commands.stream import (
    ExclusiveIdBound,
    IdBound,
    MaxId,
    MinId,
    StreamAddOptions,
    StreamReadOptions,
    TrimByMaxLen,
    TrimByMinId,
)
from glide.config import (
    ClusterClientConfiguration,
    ProtocolVersion,
    RedisClientConfiguration,
    RedisCredentials,
)
from glide.constants import OK, TResult
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
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
from tests.conftest import create_client
from tests.utils.utils import (
    check_if_server_version_lt,
    compare_maps,
    get_first_result,
    get_random_string,
    is_single_response,
    parse_info_response,
)


@pytest.mark.asyncio
class TestRedisClients:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_register_client_name_and_version(self, redis_client: TRedisClient):
        min_version = "7.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")
        info = await redis_client.custom_command(["CLIENT", "INFO"])
        assert type(info) is str
        assert "lib-name=GlidePy" in info
        assert "lib-ver=unknown" in info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_send_and_receive_large_values(self, redis_client: TRedisClient):
        length = 2**16
        key = get_random_string(length)
        value = get_random_string(length)
        assert len(key) == length
        assert len(value) == length
        await redis_client.set(key, value)
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_send_and_receive_non_ascii_unicode(self, redis_client: TRedisClient):
        key = "foo"
        value = "שלום hello 汉字"
        assert value == "שלום hello 汉字"
        await redis_client.set(key, value)
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("value_size", [100, 2**16])
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_handle_concurrent_workload_without_dropping_or_changing_values(
        self, redis_client: TRedisClient, value_size
    ):
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
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_connect_with_auth_requirepass(
        self, redis_client: TRedisClient, request
    ):
        is_cluster = isinstance(redis_client, RedisClusterClient)
        password = "TEST_AUTH"
        credentials = RedisCredentials(password)
        try:
            await redis_client.custom_command(
                ["CONFIG", "SET", "requirepass", password]
            )

            with pytest.raises(ClosingError, match="NOAUTH"):
                # Creation of a new client without password should fail
                await create_client(
                    request,
                    is_cluster,
                    addresses=redis_client.config.addresses,
                )

            auth_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=redis_client.config.addresses,
            )
            key = get_random_string(10)
            assert await auth_client.set(key, key) == OK
            assert await auth_client.get(key) == key
        finally:
            # Reset the password
            auth_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=redis_client.config.addresses,
            )
            await auth_client.custom_command(["CONFIG", "SET", "requirepass", ""])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_connect_with_auth_acl(
        self, redis_client: Union[RedisClient, RedisClusterClient], request
    ):
        is_cluster = isinstance(redis_client, RedisClusterClient)
        username = "testuser"
        password = "TEST_AUTH"
        try:
            assert (
                await redis_client.custom_command(
                    [
                        "ACL",
                        "SETUSER",
                        username,
                        "on",
                        "allkeys",
                        "+get",
                        "+cluster",
                        "+ping",
                        "+info",
                        "+client",
                        f">{password}",
                    ]
                )
                == OK
            )
            key = get_random_string(10)
            assert await redis_client.set(key, key) == OK
            credentials = RedisCredentials(password, username)

            testuser_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=redis_client.config.addresses,
            )
            assert await testuser_client.get(key) == key
            with pytest.raises(RequestError) as e:
                # This client isn't authorized to perform SET
                await testuser_client.set("foo", "bar")
            assert "NOPERM" in str(e)
        finally:
            # Delete this user
            await redis_client.custom_command(["ACL", "DELUSER", username])

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_select_standalone_database_id(self, request, cluster_mode):
        redis_client = await create_client(
            request, cluster_mode=cluster_mode, database_id=4
        )
        client_info = await redis_client.custom_command(["CLIENT", "INFO"])
        assert "db=4" in client_info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_name(self, request, cluster_mode, protocol):
        redis_client = await create_client(
            request,
            cluster_mode=cluster_mode,
            client_name="TEST_CLIENT_NAME",
            protocol=protocol,
        )
        client_info = await redis_client.custom_command(["CLIENT", "INFO"])
        assert "name=TEST_CLIENT_NAME" in client_info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_closed_client_raises_error(self, redis_client: TRedisClient):
        await redis_client.close()
        with pytest.raises(ClosingError) as e:
            await redis_client.set("foo", "bar")
        assert "the client is closed" in str(e)


@pytest.mark.asyncio
class TestCommands:
    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_socket_set_get(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
        assert await redis_client.set(key, value) == OK
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
    async def test_use_resp3_protocol(self, redis_client: TRedisClient):
        result = cast(Dict[str, str], await redis_client.custom_command(["HELLO"]))

        assert int(result["proto"]) == 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2])
    async def test_allow_opt_in_to_resp2_protocol(self, redis_client: TRedisClient):
        result = cast(Dict[str, str], await redis_client.custom_command(["HELLO"]))

        assert int(result["proto"]) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_conditional_set(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        res = await redis_client.set(
            key, value, conditional_set=ConditionalChange.ONLY_IF_EXISTS
        )
        assert res is None
        res = await redis_client.set(
            key, value, conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST
        )
        assert res == OK
        assert await redis_client.get(key) == value
        res = await redis_client.set(
            key, "foobar", conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST
        )
        assert res is None
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_set_return_old_value(self, redis_client: TRedisClient):
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
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_single_arg(self, redis_client: TRedisClient):
        # Test single arg command
        res = await redis_client.custom_command(["PING"])
        assert res == "PONG"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_multi_arg(self, redis_client: TRedisClient):
        # Test multi args command
        client_list = await redis_client.custom_command(
            ["CLIENT", "LIST", "TYPE", "NORMAL"]
        )
        assert isinstance(client_list, (str, list))
        res: str = get_first_result(client_list)
        assert res is not None
        assert "id" in res
        assert "cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_lower_and_upper_case(
        self, redis_client: TRedisClient
    ):
        # Test multi args command
        client_list = await redis_client.custom_command(
            ["CLIENT", "LIST", "TYPE", "NORMAL"]
        )
        assert isinstance(client_list, (str, list))
        res: str = get_first_result(client_list)
        assert res is not None
        assert "id" in res
        assert "cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_request_error_raises_exception(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        await redis_client.set(key, value)
        with pytest.raises(RequestError) as e:
            await redis_client.custom_command(["HSET", key, "1", "bar"])
        assert "WRONGTYPE" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_server_replication(self, redis_client: TRedisClient):
        info = get_first_result(await redis_client.info([InfoSection.SERVER]))
        assert "# Server" in info
        cluster_mode = parse_info_response(info)["redis_mode"]
        expected_cluster_mode = isinstance(redis_client, RedisClusterClient)
        assert cluster_mode == "cluster" if expected_cluster_mode else "standalone"
        info = get_first_result(await redis_client.info([InfoSection.REPLICATION]))
        assert "# Replication" in info
        assert "# Errorstats" not in info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_default(self, redis_client: TRedisClient):
        cluster_mode = isinstance(redis_client, RedisClusterClient)
        info_result = await redis_client.info()
        if cluster_mode:
            cluster_nodes = await redis_client.custom_command(["CLUSTER", "NODES"])
            assert isinstance(cluster_nodes, (str, list))
            cluster_nodes = get_first_result(cluster_nodes)
            expected_num_of_results = cluster_nodes.count("master")
            assert len(info_result) == expected_num_of_results
        info_result = get_first_result(info_result)
        assert "# Memory" in info_result

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_select(self, redis_client: RedisClient):
        assert await redis_client.select(0) == OK
        key = get_random_string(10)
        value = get_random_string(10)
        assert await redis_client.set(key, value) == OK
        assert await redis_client.get(key) == value
        assert await redis_client.select(1) == OK
        assert await redis_client.get(key) is None
        assert await redis_client.select(0) == OK
        assert await redis_client.get(key) == value

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_move(self, redis_client: RedisClient):
        key = get_random_string(10)
        value = get_random_string(10)

        assert await redis_client.select(0) == OK
        assert await redis_client.move(key, 1) is False

        assert await redis_client.set(key, value) == OK
        assert await redis_client.get(key) == value

        assert await redis_client.move(key, 1) is True
        assert await redis_client.get(key) is None
        assert await redis_client.select(1) == OK
        assert await redis_client.get(key) == value

        with pytest.raises(RequestError) as e:
            await redis_client.move(key, -1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_delete(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10), get_random_string(10)]
        value = get_random_string(10)
        [await redis_client.set(key, value) for key in keys]
        assert await redis_client.get(keys[0]) == value
        assert await redis_client.get(keys[1]) == value
        assert await redis_client.get(keys[2]) == value
        delete_keys = keys + [get_random_string(10)]
        assert await redis_client.delete(delete_keys) == 3
        assert await redis_client.delete(keys) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getdel(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value = get_random_string(10)
        non_existing_key = get_random_string(10)
        list_key = get_random_string(10)
        assert await redis_client.set(key, value) == "OK"

        # Retrieve and delete existing key
        assert await redis_client.getdel(key) == value
        assert await redis_client.get(key) is None

        # Try to get and delete a non-existing key
        assert await redis_client.getdel(non_existing_key) is None

        assert await redis_client.lpush(list_key, [value]) == 1
        with pytest.raises(RequestError) as e:
            await redis_client.getdel(list_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getrange(self, redis_client: TRedisClient):
        key = get_random_string(16)
        value = get_random_string(10)
        non_string_key = get_random_string(10)

        assert await redis_client.set(key, value) == OK
        assert await redis_client.getrange(key, 0, 3) == value[:4]
        assert await redis_client.getrange(key, -3, -1) == value[-3:]
        assert await redis_client.getrange(key, 0, -1) == value

        # out of range
        assert await redis_client.getrange(key, 10, 100) == value[10:]
        assert await redis_client.getrange(key, -200, -3) == value[-200:-2]
        assert await redis_client.getrange(key, 100, 200) == ""

        # incorrect range
        assert await redis_client.getrange(key, -1, -3) == ""

        # a redis bug, fixed in version 8: https://github.com/redis/redis/issues/13207
        if await check_if_server_version_lt(redis_client, "8.0.0"):
            assert await redis_client.getrange(key, -200, -100) == value[0]
        else:
            assert await redis_client.getrange(key, -200, -100) == ""

        if await check_if_server_version_lt(redis_client, "8.0.0"):
            assert await redis_client.getrange(non_string_key, 0, -1) == ""

        # non-string key
        assert await redis_client.lpush(non_string_key, ["_"]) == 1
        with pytest.raises(RequestError):
            await redis_client.getrange(non_string_key, 0, -1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_reset_stat(self, redis_client: TRedisClient):
        # we execute set and info so the commandstats will show `cmdstat_set::calls` greater than 1
        # after the configResetStat call we initiate an info command and the the commandstats won't contain `cmdstat_set`.
        await redis_client.set("foo", "bar")
        info_stats = str(await redis_client.info([InfoSection.COMMAND_STATS]))

        assert "cmdstat_set" in info_stats

        assert await redis_client.config_resetstat() == OK
        info_stats = str(await redis_client.info([InfoSection.COMMAND_STATS]))

        # 1 stands for the second info command
        assert "cmdstat_set" not in info_stats

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_rewrite(self, redis_client: TRedisClient):
        info_server = parse_info_response(
            get_first_result(await redis_client.info([InfoSection.SERVER]))
        )
        if len(info_server["config_file"]) > 0:
            assert await redis_client.config_rewrite() == OK
        else:
            # We expect Redis to return an error since the test cluster doesn't use redis.conf file
            with pytest.raises(RequestError) as e:
                await redis_client.config_rewrite()
            assert "The server is running without a config file" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_id(self, redis_client: TRedisClient):
        client_id = await redis_client.client_id()
        assert type(client_id) is int
        assert client_id > 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "10") == OK
        assert await redis_client.incr(key) == 11
        assert await redis_client.get(key) == "11"
        assert await redis_client.incrby(key, 4) == 15
        assert await redis_client.get(key) == "15"
        assert await redis_client.incrbyfloat(key, 5.5) == 20.5
        assert await redis_client.get(key) == "20.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_non_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(10)
        key3 = get_random_string(10)

        assert await redis_client.get(key) is None
        assert await redis_client.incr(key) == 1
        assert await redis_client.get(key) == "1"

        assert await redis_client.get(key2) is None
        assert await redis_client.incrby(key2, 3) == 3
        assert await redis_client.get(key2) == "3"

        assert await redis_client.get(key3) is None
        assert await redis_client.incrbyfloat(key3, 0.5) == 0.5
        assert await redis_client.get(key3) == "0.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_with_str_value(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.incr(key)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.incrby(key, 3)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.incrbyfloat(key, 3.5)
        assert "value is not a valid float" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_getname(self, redis_client: TRedisClient):
        assert await redis_client.client_getname() is None
        assert (
            await redis_client.custom_command(["CLIENT", "SETNAME", "GlideConnection"])
            == OK
        )
        assert await redis_client.client_getname() == "GlideConnection"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_mset_mget(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10), get_random_string(10)]
        non_existing_key = get_random_string(10)
        key_value_pairs = {key: value for key, value in zip(keys, keys)}

        assert await redis_client.mset(key_value_pairs) == OK

        # Add the non-existing key
        keys.append(non_existing_key)
        mget_res = await redis_client.mget(keys)
        keys[-1] = None
        assert mget_res == keys

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_touch(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10)]
        key_value_pairs = {key: value for key, value in zip(keys, keys)}

        assert await redis_client.mset(key_value_pairs) == OK
        assert await redis_client.touch(keys) == 2

        # 2 existing keys, one non-existing
        assert await redis_client.touch([*keys, get_random_string(3)]) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_msetnx(self, redis_client: TRedisClient):
        key1 = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        key3 = f"{{key}}-3{get_random_string(5)}"
        non_existing = get_random_string(5)
        value = get_random_string(5)
        key_value_map1 = {key1: value, key2: value}
        key_value_map2 = {key2: get_random_string(5), key3: value}

        assert await redis_client.msetnx(key_value_map1) is True
        mget_res = await redis_client.mget([key1, key2, non_existing])
        assert mget_res == [value, value, None]

        assert await redis_client.msetnx(key_value_map2) is False
        assert await redis_client.get(key3) is None
        assert await redis_client.get(key2) == value

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ping(self, redis_client: TRedisClient):
        assert await redis_client.ping() == "PONG"
        assert await redis_client.ping("HELLO") == "HELLO"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_get_set(self, redis_client: TRedisClient):
        previous_timeout = await redis_client.config_get(["timeout"])
        assert await redis_client.config_set({"timeout": "1000"}) == OK
        assert await redis_client.config_get(["timeout"]) == {"timeout": "1000"}
        # revert changes to previous timeout
        assert isinstance(previous_timeout, dict)
        assert isinstance(previous_timeout["timeout"], str)
        assert (
            await redis_client.config_set({"timeout": previous_timeout["timeout"]})
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_decrby_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "10") == OK
        assert await redis_client.decr(key) == 9
        assert await redis_client.get(key) == "9"
        assert await redis_client.decrby(key, 4) == 5
        assert await redis_client.get(key) == "5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_decrby_non_existing_key(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(10)

        assert await redis_client.get(key) is None
        assert await redis_client.decr(key) == -1
        assert await redis_client.get(key) == "-1"

        assert await redis_client.get(key2) is None
        assert await redis_client.decrby(key2, 3) == -3
        assert await redis_client.get(key2) == "-3"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_with_str_value(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.decr(key)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.decrby(key, 3)

        assert "value is not an integer" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_setrange(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        # test new key and existing key
        assert await redis_client.setrange(key1, 0, "Hello World") == 11
        assert await redis_client.setrange(key1, 6, "GLIDE") == 11

        # offset > len
        assert await redis_client.setrange(key1, 15, "GLIDE") == 20

        # negative offset
        with pytest.raises(RequestError):
            assert await redis_client.setrange(key1, -1, "GLIDE")

        # non-string key throws RequestError
        assert await redis_client.lpush(key2, ["_"]) == 1
        with pytest.raises(RequestError):
            assert await redis_client.setrange(key2, 0, "_")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hset_hget_hgetall(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hget(key, field) == "value"
        assert await redis_client.hget(key, field2) == "value2"
        assert await redis_client.hget(key, "non_existing_field") is None

        hgetall_map = await redis_client.hgetall(key)
        expected_map = {field: "value", field2: "value2"}
        assert compare_maps(hgetall_map, expected_map) is True
        assert await redis_client.hgetall("non_existing_field") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hdel(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2", field3: "value3"}

        assert await redis_client.hset(key, field_value_map) == 3
        assert await redis_client.hdel(key, [field, field2]) == 2
        assert await redis_client.hdel(key, ["nonExistingField"]) == 0
        assert await redis_client.hdel("nonExistingKey", [field3]) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hsetnx(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)

        assert await redis_client.hsetnx(key, field, "value") == True
        assert await redis_client.hsetnx(key, field, "new value") == False
        assert await redis_client.hget(key, field) == "value"
        key = get_random_string(5)
        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hsetnx(key, field, "value")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hmget(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hmget(key, [field, "nonExistingField", field2]) == [
            "value",
            None,
            "value2",
        ]
        assert await redis_client.hmget("nonExistingKey", [field, field2]) == [
            None,
            None,
        ]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hset_without_data(self, redis_client: TRedisClient):
        with pytest.raises(RequestError) as e:
            await redis_client.hset("key", {})

        assert "wrong number of arguments" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_hincrbyfloat(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "10"}

        assert await redis_client.hset(key, field_value_map) == 1
        assert await redis_client.hincrby(key, field, 1) == 11
        assert await redis_client.hincrby(key, field, 4) == 15
        assert await redis_client.hincrbyfloat(key, field, 1.5) == 16.5

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_non_existing_key_field(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "10"}

        assert await redis_client.hincrby("nonExistingKey", field, 1) == 1
        assert await redis_client.hset(key, field_value_map) == 1
        assert await redis_client.hincrby(key, "nonExistingField", 2) == 2
        assert await redis_client.hset(key2, field_value_map) == 1
        assert await redis_client.hincrbyfloat(key2, "nonExistingField", -0.5) == -0.5

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_invalid_value(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "value"}

        assert await redis_client.hset(key, field_value_map) == 1

        with pytest.raises(RequestError) as e:
            await redis_client.hincrby(key, field, 2)
        assert "hash value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.hincrbyfloat(key, field, 1.5)
        assert "hash value is not a float" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hexist(self, redis_client: TRedisClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hexists(key, field)
        assert not await redis_client.hexists(key, "nonExistingField")
        assert not await redis_client.hexists("nonExistingKey", field2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hlen(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hlen(key) == 2
        assert await redis_client.hdel(key, [field]) == 1
        assert await redis_client.hlen(key) == 1
        assert await redis_client.hlen("non_existing_hash") == 0

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hlen(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hvals(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hvals(key) == ["value", "value2"]
        assert await redis_client.hdel(key, [field]) == 1
        assert await redis_client.hvals(key) == ["value2"]
        assert await redis_client.hvals("non_existing_key") == []

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hvals(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hkeys(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hkeys(key) == [field, field2]
        assert await redis_client.hdel(key, [field]) == 1
        assert await redis_client.hkeys(key) == [field2]
        assert await redis_client.hkeys("non_existing_key") == []

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hkeys(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hrandfield(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        assert await redis_client.hrandfield(key) in [field, field2]
        assert await redis_client.hrandfield("non_existing_key") is None

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hrandfield(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hrandfield_count(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        # Unique values are expected as count is positive
        rand_fields = await redis_client.hrandfield_count(key, 4)
        assert len(rand_fields) == 2
        assert set(rand_fields) == {field, field2}

        # Duplicate values are expected as count is negative
        rand_fields = await redis_client.hrandfield_count(key, -4)
        assert len(rand_fields) == 4
        for rand_field in rand_fields:
            assert rand_field in [field, field2]

        assert await redis_client.hrandfield_count(key, 0) == []
        assert await redis_client.hrandfield_count("non_existing_key", 4) == []

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hrandfield_count(key2, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hrandfield_withvalues(self, redis_client: TRedisClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await redis_client.hset(key, field_value_map) == 2
        # Unique values are expected as count is positive
        rand_fields_with_values = await redis_client.hrandfield_withvalues(key, 4)
        assert len(rand_fields_with_values) == 2
        for field_with_value in rand_fields_with_values:
            assert field_with_value in [[field, "value"], [field2, "value2"]]

        # Duplicate values are expected as count is negative
        rand_fields_with_values = await redis_client.hrandfield_withvalues(key, -4)
        assert len(rand_fields_with_values) == 4
        for field_with_value in rand_fields_with_values:
            assert field_with_value in [[field, "value"], [field2, "value2"]]

        assert await redis_client.hrandfield_withvalues(key, 0) == []
        assert await redis_client.hrandfield_withvalues("non_existing_key", 4) == []

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.hrandfield_withvalues(key2, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hstrlen(self, redis_client: TRedisClient):
        key = get_random_string(10)

        assert await redis_client.hstrlen(key, "field") == 0
        assert await redis_client.hset(key, {"field": "value"}) == 1
        assert await redis_client.hstrlen(key, "field") == 5

        assert await redis_client.hstrlen(key, "field2") == 0

        await redis_client.set(key, "value")
        with pytest.raises(RequestError):
            await redis_client.hstrlen(key, "field")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpush_lpop_lrange(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.lpush(key, value_list) == 4
        assert await redis_client.lpop(key) == value_list[-1]
        assert await redis_client.lrange(key, 0, -1) == value_list[-2::-1]
        assert await redis_client.lpop_count(key, 2) == value_list[-2:0:-1]
        assert await redis_client.lrange("non_existing_key", 0, -1) == []
        assert await redis_client.lpop("non_existing_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpush_lpop_lrange_wrong_type_raise_error(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await redis_client.lpush(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.lpop(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.lrange(key, 0, -1)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpushx(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        # new key
        assert await redis_client.lpushx(key1, ["1"]) == 0
        assert await redis_client.lrange(key1, 0, -1) == []
        # existing key
        assert await redis_client.lpush(key1, ["0"]) == 1
        assert await redis_client.lpushx(key1, ["1", "2", "3"]) == 4
        assert await redis_client.lrange(key1, 0, -1) == ["3", "2", "1", "0"]
        # key exists, but not a list
        assert await redis_client.set(key2, "bar") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.lpushx(key2, ["_"])
        # incorrect arguments
        with pytest.raises(RequestError):
            await redis_client.lpushx(key1, [])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_blpop(self, redis_client: TRedisClient):
        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        value1 = "value1"
        value2 = "value2"
        value_list = [value1, value2]

        assert await redis_client.lpush(key1, value_list) == 2
        assert await redis_client.blpop([key1, key2], 0.5) == [key1, value2]
        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert await redis_client.blpop(["non_existent_key"], 0.5) is None

        # key exists, but not a list
        assert await redis_client.set("foo", "bar")
        with pytest.raises(RequestError):
            await redis_client.blpop(["foo"], 0.001)

        async def endless_blpop_call():
            await redis_client.blpop(["non_existent_key"], 0)

        # blpop is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_blpop_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lmpop(self, redis_client: TRedisClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        key3 = f"{{test}}-3-f{get_random_string(10)}"

        # Initialize the lists
        assert await redis_client.lpush(key1, ["3", "2", "1"]) == 3
        assert await redis_client.lpush(key2, ["6", "5", "4"]) == 3

        # Pop from LEFT
        result = await redis_client.lmpop([key1, key2], ListDirection.LEFT, 2)
        expected_result = {key1: ["1", "2"]}
        assert compare_maps(result, expected_result) is True

        # Pop from RIGHT
        result = await redis_client.lmpop([key2, key1], ListDirection.RIGHT, 2)
        expected_result = {key2: ["6", "5"]}
        assert compare_maps(result, expected_result) is True

        # Pop without count (default is 1)
        result = await redis_client.lmpop([key1, key2], ListDirection.LEFT)
        expected_result = {key1: ["3"]}
        assert compare_maps(result, expected_result) is True

        # Non-existing key
        result = await redis_client.lmpop([key3], ListDirection.LEFT, 1)
        assert result is None

        # Non-list key
        assert await redis_client.set(key3, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.lmpop([key3], ListDirection.LEFT, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_blmpop(self, redis_client: TRedisClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        key3 = f"{{test}}-3-f{get_random_string(10)}"
        key4 = f"{{test}}-4-f{get_random_string(10)}"

        # Initialize the lists
        assert await redis_client.lpush(key1, ["3", "2", "1"]) == 3
        assert await redis_client.lpush(key2, ["6", "5", "4"]) == 3

        # Pop from LEFT with blocking
        result = await redis_client.blmpop([key1, key2], ListDirection.LEFT, 0.1, 2)
        expected_result = {key1: ["1", "2"]}
        assert compare_maps(result, expected_result) is True

        # Pop from RIGHT with blocking
        result = await redis_client.blmpop([key2, key1], ListDirection.RIGHT, 0.1, 2)
        expected_result = {key2: ["6", "5"]}
        assert compare_maps(result, expected_result) is True

        # Pop without count (default is 1)
        result = await redis_client.blmpop([key1, key2], ListDirection.LEFT, 0.1)
        expected_result = {key1: ["3"]}
        assert compare_maps(result, expected_result) is True

        # Non-existing key with blocking
        result = await redis_client.blmpop([key3], ListDirection.LEFT, 0.1, 1)
        assert result is None

        # Non-list key with blocking
        assert await redis_client.set(key4, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.blmpop([key4], ListDirection.LEFT, 0.1, 1)

        # BLMPOP is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        async def endless_blmpop_call():
            await redis_client.blmpop([key3], ListDirection.LEFT, 0, 1)

        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_blmpop_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lindex(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = [get_random_string(5), get_random_string(5)]
        assert await redis_client.lpush(key, value_list) == 2
        assert await redis_client.lindex(key, 0) == value_list[1]
        assert await redis_client.lindex(key, 1) == value_list[0]
        assert await redis_client.lindex(key, 3) is None
        assert await redis_client.lindex("non_existing_key", 0) is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpush_rpop(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.rpush(key, value_list) == 4
        assert await redis_client.rpop(key) == value_list[-1]

        assert await redis_client.rpop_count(key, 2) == value_list[-2:0:-1]
        assert await redis_client.rpop("non_existing_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpush_rpop_wrong_type_raise_error(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await redis_client.rpush(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.rpop(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpushx(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        # new key
        assert await redis_client.rpushx(key1, ["1"]) == 0
        assert await redis_client.lrange(key1, 0, -1) == []
        # existing key
        assert await redis_client.rpush(key1, ["0"]) == 1
        assert await redis_client.rpushx(key1, ["1", "2", "3"]) == 4
        assert await redis_client.lrange(key1, 0, -1) == ["0", "1", "2", "3"]
        # key existing, but it is not a list
        assert await redis_client.set(key2, "bar") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.rpushx(key2, ["_"])
        # incorrect arguments
        with pytest.raises(RequestError):
            await redis_client.rpushx(key2, [])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_brpop(self, redis_client: TRedisClient):
        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        value1 = "value1"
        value2 = "value2"
        value_list = [value1, value2]

        assert await redis_client.lpush(key1, value_list) == 2
        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert await redis_client.brpop([key1, key2], 0.5) == [key1, value1]

        assert await redis_client.brpop(["non_existent_key"], 0.5) is None

        # key exists, but not a list
        assert await redis_client.set("foo", "bar")
        with pytest.raises(RequestError):
            await redis_client.brpop(["foo"], 0.001)

        async def endless_brpop_call():
            await redis_client.brpop(["non_existent_key"], 0)

        # brpop is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_brpop_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_linsert(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        assert await redis_client.lpush(key1, ["4", "3", "2", "1"]) == 4
        assert await redis_client.linsert(key1, InsertPosition.BEFORE, "2", "1.5") == 5
        assert await redis_client.linsert(key1, InsertPosition.AFTER, "3", "3.5") == 6
        assert await redis_client.lrange(key1, 0, -1) == [
            "1",
            "1.5",
            "2",
            "3",
            "3.5",
            "4",
        ]

        assert (
            await redis_client.linsert(
                "non_existing_key", InsertPosition.BEFORE, "pivot", "elem"
            )
            == 0
        )
        assert await redis_client.linsert(key1, InsertPosition.AFTER, "5", "6") == -1

        # key exists, but it is not a list
        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.linsert(key2, InsertPosition.AFTER, "p", "e")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lmove(self, redis_client: TRedisClient):
        key1 = "{SameSlot}" + get_random_string(10)
        key2 = "{SameSlot}" + get_random_string(10)

        # Initialize the lists
        assert await redis_client.lpush(key1, ["2", "1"]) == 2
        assert await redis_client.lpush(key2, ["4", "3"]) == 2

        # Move from LEFT to LEFT
        assert (
            await redis_client.lmove(key1, key2, ListDirection.LEFT, ListDirection.LEFT)
            == "1"
        )
        assert await redis_client.lrange(key1, 0, -1) == ["2"]
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3", "4"]

        # Move from LEFT to RIGHT
        assert (
            await redis_client.lmove(
                key1, key2, ListDirection.LEFT, ListDirection.RIGHT
            )
            == "2"
        )
        assert await redis_client.lrange(key1, 0, -1) == []
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3", "4", "2"]

        # Move from RIGHT to LEFT - non-existing destination key
        assert (
            await redis_client.lmove(
                key2, key1, ListDirection.RIGHT, ListDirection.LEFT
            )
            == "2"
        )
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3", "4"]
        assert await redis_client.lrange(key1, 0, -1) == ["2"]

        # Move from RIGHT to RIGHT
        assert (
            await redis_client.lmove(
                key2, key1, ListDirection.RIGHT, ListDirection.RIGHT
            )
            == "4"
        )
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3"]
        assert await redis_client.lrange(key1, 0, -1) == ["2", "4"]

        # Non-existing source key
        assert (
            await redis_client.lmove(
                "{SameSlot}non_existing_key",
                key1,
                ListDirection.LEFT,
                ListDirection.LEFT,
            )
            is None
        )

        # Non-list source key
        key3 = get_random_string(10)
        assert await redis_client.set(key3, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.lmove(key3, key1, ListDirection.LEFT, ListDirection.LEFT)

        # Non-list destination key
        with pytest.raises(RequestError):
            await redis_client.lmove(key1, key3, ListDirection.LEFT, ListDirection.LEFT)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_blmove(self, redis_client: TRedisClient):
        key1 = "{SameSlot}" + get_random_string(10)
        key2 = "{SameSlot}" + get_random_string(10)

        # Initialize the lists
        assert await redis_client.lpush(key1, ["2", "1"]) == 2
        assert await redis_client.lpush(key2, ["4", "3"]) == 2

        # Move from LEFT to LEFT with blocking
        assert (
            await redis_client.blmove(
                key1, key2, ListDirection.LEFT, ListDirection.LEFT, 0.1
            )
            == "1"
        )
        assert await redis_client.lrange(key1, 0, -1) == ["2"]
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3", "4"]

        # Move from LEFT to RIGHT with blocking
        assert (
            await redis_client.blmove(
                key1, key2, ListDirection.LEFT, ListDirection.RIGHT, 0.1
            )
            == "2"
        )
        assert await redis_client.lrange(key1, 0, -1) == []
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3", "4", "2"]

        # Move from RIGHT to LEFT non-existing destination with blocking
        assert (
            await redis_client.blmove(
                key2, key1, ListDirection.RIGHT, ListDirection.LEFT, 0.1
            )
            == "2"
        )
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3", "4"]
        assert await redis_client.lrange(key1, 0, -1) == ["2"]

        # Move from RIGHT to RIGHT with blocking
        assert (
            await redis_client.blmove(
                key2, key1, ListDirection.RIGHT, ListDirection.RIGHT, 0.1
            )
            == "4"
        )
        assert await redis_client.lrange(key2, 0, -1) == ["1", "3"]
        assert await redis_client.lrange(key1, 0, -1) == ["2", "4"]

        # Non-existing source key with blocking
        assert (
            await redis_client.blmove(
                "{SameSlot}non_existing_key",
                key1,
                ListDirection.LEFT,
                ListDirection.LEFT,
                0.1,
            )
            is None
        )

        # Non-list source key with blocking
        key3 = get_random_string(10)
        assert await redis_client.set(key3, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.blmove(
                key3, key1, ListDirection.LEFT, ListDirection.LEFT, 0.1
            )

        # Non-list destination key with blocking
        with pytest.raises(RequestError):
            await redis_client.blmove(
                key1, key3, ListDirection.LEFT, ListDirection.LEFT, 0.1
            )

        # BLMOVE is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        async def endless_blmove_call():
            await redis_client.blmove(
                "{SameSlot}non_existing_key",
                key2,
                ListDirection.LEFT,
                ListDirection.RIGHT,
                0,
            )

        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_blmove_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lset(self, redis_client: TRedisClient):
        key = get_random_string(10)
        element = get_random_string(5)
        values = [get_random_string(5) for _ in range(4)]

        # key does not exist
        with pytest.raises(RequestError):
            await redis_client.lset("non_existing_key", 0, element)

        # pushing elements to list
        await redis_client.lpush(key, values) == 4

        # index out of range
        with pytest.raises(RequestError):
            await redis_client.lset(key, 10, element)

        # assert lset result
        assert await redis_client.lset(key, 0, element) == OK

        values = [element] + values[:-1][::-1]
        assert await redis_client.lrange(key, 0, -1) == values

        # assert lset with a negative index for the last element in the list
        assert await redis_client.lset(key, -1, element) == OK

        values[-1] = element
        assert await redis_client.lrange(key, 0, -1) == values

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["member1", "member2", "member3", "member4"]

        assert await redis_client.sadd(key, value_list) == 4
        assert await redis_client.srem(key, ["member4", "nonExistingMember"]) == 1

        assert set(await redis_client.smembers(key)) == set(value_list[:3])

        assert await redis_client.srem(key, ["member1"]) == 1
        assert await redis_client.scard(key) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard_non_existing_key(
        self, redis_client: TRedisClient
    ):
        non_existing_key = get_random_string(10)
        assert await redis_client.srem(non_existing_key, ["member"]) == 0
        assert await redis_client.scard(non_existing_key) == 0
        assert await redis_client.smembers(non_existing_key) == set()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard_wrong_type_raise_error(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await redis_client.sadd(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.srem(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.scard(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.smembers(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sismember(self, redis_client: TRedisClient):
        key = get_random_string(10)
        member = get_random_string(5)
        assert await redis_client.sadd(key, [member]) == 1
        assert await redis_client.sismember(key, member)
        assert not await redis_client.sismember(key, get_random_string(5))
        assert not await redis_client.sismember("non_existing_key", member)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_spop(self, redis_client: TRedisClient):
        key = get_random_string(10)
        member = get_random_string(5)
        assert await redis_client.sadd(key, [member]) == 1
        assert await redis_client.spop(key) == member

        member2 = get_random_string(5)
        member3 = get_random_string(5)
        assert await redis_client.sadd(key, [member, member2, member3]) == 3
        assert await redis_client.spop_count(key, 4) == {member, member2, member3}

        assert await redis_client.scard(key) == 0

        assert await redis_client.spop("non_existing_key") == None
        assert await redis_client.spop_count("non_existing_key", 3) == set()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_smove(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await redis_client.sadd(key1, ["1", "2", "3"]) == 3
        assert await redis_client.sadd(key2, ["2", "3"]) == 2

        # move an element
        assert await redis_client.smove(key1, key2, "1") is True
        assert await redis_client.smembers(key1) == {"2", "3"}
        assert await redis_client.smembers(key2) == {"1", "2", "3"}

        # moved element already exists in the destination set
        assert await redis_client.smove(key2, key1, "2") is True
        assert await redis_client.smembers(key1) == {"2", "3"}
        assert await redis_client.smembers(key2) == {"1", "3"}

        # attempt to move from a non-existing key
        assert await redis_client.smove(non_existing_key, key1, "4") is False
        assert await redis_client.smembers(key1) == {"2", "3"}

        # move to a new set
        assert await redis_client.smove(key1, key3, "2")
        assert await redis_client.smembers(key1) == {"3"}
        assert await redis_client.smembers(key3) == {"2"}

        # attempt to move a missing element
        assert await redis_client.smove(key1, key3, "42") is False
        assert await redis_client.smembers(key1) == {"3"}
        assert await redis_client.smembers(key3) == {"2"}

        # move missing element to missing key
        assert await redis_client.smove(key1, non_existing_key, "42") is False
        assert await redis_client.smembers(key1) == {"3"}
        assert await redis_client.type(non_existing_key) == "none"

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.smove(string_key, key1, "_")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sunion(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list = ["a", "b", "c"]
        member2_list = ["b", "c", "d", "e"]

        assert await redis_client.sadd(key1, member1_list) == 3
        assert await redis_client.sadd(key2, member2_list) == 4
        assert await redis_client.sunion([key1, key2]) == {"a", "b", "c", "d", "e"}

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.sunion([])

        # non-existing key returns the set of existing keys
        assert await redis_client.sunion([key1, non_existing_key]) == set(member1_list)

        # non-set key
        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.sunion([key2])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sunionstore(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        key4 = f"{{testKey}}:4-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await redis_client.sadd(key1, ["a", "b", "c"]) == 3
        assert await redis_client.sadd(key2, ["c", "d", "e"]) == 3
        assert await redis_client.sadd(key3, ["e", "f", "g"]) == 3

        # store union in new key
        assert await redis_client.sunionstore(key4, [key1, key2]) == 5
        assert await redis_client.smembers(key4) == {"a", "b", "c", "d", "e"}

        # overwrite existing set
        assert await redis_client.sunionstore(key1, [key4, key2]) == 5
        assert await redis_client.smembers(key1) == {"a", "b", "c", "d", "e"}

        # overwrite one of the source keys
        assert await redis_client.sunionstore(key2, [key4, key2]) == 5
        assert await redis_client.smembers(key1) == {"a", "b", "c", "d", "e"}

        # union with a non existing key
        assert await redis_client.sunionstore(key2, [non_existing_key]) == 0
        assert await redis_client.smembers(key2) == set()

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.sunionstore(key4, [string_key, key1])

        # overwrite destination when destination is not a set
        assert await redis_client.sunionstore(string_key, [key1, key3]) == 7
        assert await redis_client.smembers(string_key) == {
            "a",
            "b",
            "c",
            "d",
            "e",
            "f",
            "g",
        }

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sinter(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list = ["a", "b", "c"]
        member2_list = ["c", "d", "e"]

        # positive test case
        assert await redis_client.sadd(key1, member1_list) == 3
        assert await redis_client.sadd(key2, member2_list) == 3
        assert await redis_client.sinter([key1, key2]) == set(["c"])

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.sinter([])

        # non-existing key returns empty set
        assert await redis_client.sinter([key1, non_existing_key]) == set()

        # non-set key
        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.sinter([key2])
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sinterstore(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        key3 = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list = ["a", "b", "c"]
        member2_list = ["c", "d", "e"]

        assert await redis_client.sadd(key1, member1_list) == 3
        assert await redis_client.sadd(key2, member2_list) == 3

        # store in new key
        assert await redis_client.sinterstore(key3, [key1, key2]) == 1
        assert await redis_client.smembers(key3) == {"c"}

        # overwrite existing set, which is also a source set
        assert await redis_client.sinterstore(key2, [key2, key3]) == 1
        assert await redis_client.smembers(key2) == {"c"}

        # source set is the same as the existing set
        assert await redis_client.sinterstore(key2, [key2]) == 1
        assert await redis_client.smembers(key2) == {"c"}

        # intersection with non-existing key
        assert await redis_client.sinterstore(key1, [key2, non_existing_key]) == 0
        assert await redis_client.smembers(key1) == set()

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.sinterstore(key3, [])

        # non-set key
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.sinterstore(key3, [string_key])

        # overwrite non-set key
        assert await redis_client.sinterstore(string_key, [key2]) == 1
        assert await redis_client.smembers(string_key) == {"c"}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sintercard(self, redis_client: TRedisClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        key3 = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list = ["a", "b", "c"]
        member2_list = ["b", "c", "d", "e"]
        member3_list = ["b", "c", "f", "g"]

        assert await redis_client.sadd(key1, member1_list) == 3
        assert await redis_client.sadd(key2, member2_list) == 4
        assert await redis_client.sadd(key3, member3_list) == 4

        # Basic intersection
        assert (
            await redis_client.sintercard([key1, key2]) == 2
        )  # Intersection of key1 and key2 is {"b", "c"}

        # Intersection with non-existing key
        assert (
            await redis_client.sintercard([key1, non_existing_key]) == 0
        )  # No common elements

        # Intersection with a single key
        assert await redis_client.sintercard([key1]) == 3  # All elements in key1

        # Intersection with limit
        assert (
            await redis_client.sintercard([key1, key2, key3], limit=1) == 1
        )  # Stops early at limit

        # Invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.sintercard([])

        # Non-set key
        assert await redis_client.set(string_key, "value") == "OK"
        with pytest.raises(RequestError):
            await redis_client.sintercard([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sdiff(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await redis_client.sadd(key1, ["a", "b", "c"]) == 3
        assert await redis_client.sadd(key2, ["c", "d", "e"]) == 3

        assert await redis_client.sdiff([key1, key2]) == {"a", "b"}
        assert await redis_client.sdiff([key2, key1]) == {"d", "e"}

        assert await redis_client.sdiff([key1, non_existing_key]) == {"a", "b", "c"}
        assert await redis_client.sdiff([non_existing_key, key1]) == set()

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.sdiff([])

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.sdiff([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sdiffstore(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await redis_client.sadd(key1, ["a", "b", "c"]) == 3
        assert await redis_client.sadd(key2, ["c", "d", "e"]) == 3

        # Store diff in new key
        assert await redis_client.sdiffstore(key3, [key1, key2]) == 2
        assert await redis_client.smembers(key3) == {"a", "b"}

        # Overwrite existing set
        assert await redis_client.sdiffstore(key3, [key2, key1]) == 2
        assert await redis_client.smembers(key3) == {"d", "e"}

        # Overwrite one of the source sets
        assert await redis_client.sdiffstore(key3, [key2, key3]) == 1
        assert await redis_client.smembers(key3) == {"c"}

        # Diff between non-empty set and empty set
        assert await redis_client.sdiffstore(key3, [key1, non_existing_key]) == 3
        assert await redis_client.smembers(key3) == {"a", "b", "c"}

        # Diff between empty set and non-empty set
        assert await redis_client.sdiffstore(key3, [non_existing_key, key1]) == 0
        assert await redis_client.smembers(key3) == set()

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.sdiffstore(key3, [])

        # source key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.sdiffstore(key3, [string_key])

        # Overwrite a key holding a non-set value
        assert await redis_client.sdiffstore(string_key, [key1, key2]) == 2
        assert await redis_client.smembers(string_key) == {"a", "b"}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_smismember(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await redis_client.sadd(key1, ["one", "two"]) == 2
        assert await redis_client.smismember(key1, ["two", "three"]) == [True, False]

        assert await redis_client.smismember(non_existing_key, ["two"]) == [False]

        # invalid argument - member list must not be empty
        with pytest.raises(RequestError):
            await redis_client.smismember(key1, [])

        # source key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.smismember(string_key, ["two"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ltrim(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.lpush(key, value_list) == 4
        assert await redis_client.ltrim(key, 0, 1) == OK
        assert await redis_client.lrange(key, 0, -1) == ["value1", "value2"]

        assert await redis_client.ltrim(key, 4, 2) == OK
        assert await redis_client.lrange(key, 0, -1) == []

        assert await redis_client.ltrim("non_existing_key", 0, 1) == OK

        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.ltrim(key, 0, 1)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lrem(self, redis_client: TRedisClient):
        key = get_random_string(10)
        value_list = ["value1", "value2", "value1", "value1", "value2"]

        assert await redis_client.lpush(key, value_list) == 5

        assert await redis_client.lrem(key, 2, "value1") == 2
        assert await redis_client.lrange(key, 0, -1) == ["value2", "value2", "value1"]

        assert await redis_client.lrem(key, -1, "value2") == 1
        assert await redis_client.lrange(key, 0, -1) == ["value2", "value1"]

        assert await redis_client.lrem(key, 0, "value2") == 1
        assert await redis_client.lrange(key, 0, -1) == ["value1"]

        assert await redis_client.lrem("non_existing_key", 2, "value") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_llen(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.lpush(key1, value_list) == 4
        assert await redis_client.llen(key1) == 4

        assert await redis_client.llen("non_existing_key") == 0

        assert await redis_client.set(key2, "foo") == OK
        with pytest.raises(RequestError) as e:
            await redis_client.llen(key2)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_strlen(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        value_list = ["value4", "value3", "value2", "value1"]

        assert await redis_client.set(key1, "foo") == OK
        assert await redis_client.strlen(key1) == 3
        assert await redis_client.strlen("non_existing_key") == 0

        assert await redis_client.lpush(key2, value_list) == 4
        with pytest.raises(RequestError):
            assert await redis_client.strlen(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rename(self, redis_client: TRedisClient):
        key1 = "{" + get_random_string(10) + "}"
        assert await redis_client.set(key1, "foo") == OK
        assert await redis_client.rename(key1, key1 + "_rename") == OK
        assert await redis_client.exists([key1 + "_rename"]) == 1

        with pytest.raises(RequestError):
            assert await redis_client.rename(
                "{same_slot}" + "non_existing_key", "{same_slot}" + "_rename"
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_renamenx(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        # Verify that attempting to rename a non-existing key throws an error
        with pytest.raises(RequestError):
            assert await redis_client.renamenx(non_existing_key, key1)

        # Test RENAMENX with string values
        assert await redis_client.set(key1, "key1") == OK
        assert await redis_client.set(key3, "key3") == OK
        # Test that RENAMENX can rename key1 to key2 (where key2 does not yet exist)
        assert await redis_client.renamenx(key1, key2) is True
        # Verify that key2 now holds the value that was in key1
        assert await redis_client.get(key2) == "key1"
        # Verify that RENAMENX doesn't rename key2 to key3, since key3 already exists
        assert await redis_client.renamenx(key2, key3) is False
        # Verify that key3 remains unchanged
        assert await redis_client.get(key3) == "key3"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_exists(self, redis_client: TRedisClient):
        keys = [get_random_string(10), get_random_string(10)]

        assert await redis_client.set(keys[0], "value") == OK
        assert await redis_client.exists(keys) == 1

        assert await redis_client.set(keys[1], "value") == OK
        assert await redis_client.exists(keys) == 2
        keys.append("non_existing_key")
        assert await redis_client.exists(keys) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_unlink(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        key3 = get_random_string(10)

        assert await redis_client.set(key1, "value") == OK
        assert await redis_client.set(key2, "value") == OK
        assert await redis_client.set(key3, "value") == OK
        assert await redis_client.unlink([key1, key2, "non_existing_key", key3]) == 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_ttl_expiretime_pexpiretime_with_positive_timeout(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.ttl(key) == -1

        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -1
            assert await redis_client.pexpiretime(key) == -1

        assert await redis_client.expire(key, 10) == 1
        assert await redis_client.ttl(key) in range(11)

        # set command clears the timeout.
        assert await redis_client.set(key, "bar") == OK
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.pexpire(key, 10000)
        else:
            assert await redis_client.pexpire(key, 10000, ExpireOptions.HasNoExpiry)
        assert await redis_client.ttl(key) in range(11)

        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expire(key, 15)
        else:
            assert await redis_client.expire(key, 15, ExpireOptions.HasExistingExpiry)
            assert await redis_client.expiretime(key) > int(time.time())
            assert await redis_client.pexpiretime(key) > (int(time.time()) * 1000)
        assert await redis_client.ttl(key) in range(16)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expireat_pexpireat_ttl_with_positive_timeout(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        current_time = int(time.time())

        assert await redis_client.expireat(key, current_time + 10) == 1
        assert await redis_client.ttl(key) in range(11)
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expireat(key, current_time + 50) == 1
        else:
            assert (
                await redis_client.expireat(
                    key, current_time + 50, ExpireOptions.NewExpiryGreaterThanCurrent
                )
                == 1
            )
        assert await redis_client.ttl(key) in range(51)

        # set command clears the timeout.
        assert await redis_client.set(key, "bar") == OK
        current_time_ms = int(time.time() * 1000)
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert not await redis_client.pexpireat(
                key, current_time_ms + 50000, ExpireOptions.HasExistingExpiry
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_expireat_pexpireat_expiretime_pexpiretime_past_or_negative_timeout(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.ttl(key) == -1

        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -1
            assert await redis_client.pexpiretime(key) == -1

        assert await redis_client.expire(key, -10) is True
        assert await redis_client.ttl(key) == -2
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -2
            assert await redis_client.pexpiretime(key) == -2

        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.pexpire(key, -10000)
        assert await redis_client.ttl(key) == -2
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -2
            assert await redis_client.pexpiretime(key) == -2

        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.expireat(key, int(time.time()) - 50) == 1
        assert await redis_client.ttl(key) == -2
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -2
            assert await redis_client.pexpiretime(key) == -2

        assert await redis_client.set(key, "foo") == OK
        assert await redis_client.pexpireat(key, int(time.time() * 1000) - 50000)
        assert await redis_client.ttl(key) == -2
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -2
            assert await redis_client.pexpiretime(key) == -2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_expireAt_pexpireAt_ttl_expiretime_pexpiretime_non_existing_key(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)

        assert await redis_client.expire(key, 10) == 0
        assert not await redis_client.pexpire(key, 10000)
        assert await redis_client.expireat(key, int(time.time()) + 50) == 0
        assert not await redis_client.pexpireat(key, int(time.time() * 1000) + 50000)
        assert await redis_client.ttl(key) == -2
        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.expiretime(key) == -2
            assert await redis_client.pexpiretime(key) == -2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pttl(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.pttl(key) == -2
        current_time = int(time.time())

        assert await redis_client.set(key, "value") == OK
        assert await redis_client.pttl(key) == -1

        assert await redis_client.expire(key, 10)
        assert 0 < await redis_client.pttl(key) <= 10000

        assert await redis_client.expireat(key, current_time + 20)
        assert 0 < await redis_client.pttl(key) <= 20000

        assert await redis_client.pexpireat(key, current_time * 1000 + 30000)
        assert 0 < await redis_client.pttl(key) <= 30000

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_persist(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "value") == OK
        assert not await redis_client.persist(key)

        assert await redis_client.expire(key, 10)
        assert await redis_client.persist(key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geoadd(self, redis_client: TRedisClient):
        key, key2 = get_random_string(10), get_random_string(10)
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await redis_client.geoadd(key, members_coordinates) == 2
        members_coordinates["Catania"].latitude = 39
        assert (
            await redis_client.geoadd(
                key,
                members_coordinates,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == 0
        )
        assert (
            await redis_client.geoadd(
                key,
                members_coordinates,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 0
        )
        members_coordinates["Catania"].latitude = 40
        members_coordinates.update({"Tel-Aviv": GeospatialData(32.0853, 34.7818)})
        assert (
            await redis_client.geoadd(
                key,
                members_coordinates,
                changed=True,
            )
            == 2
        )

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.geoadd(key2, members_coordinates)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geoadd_invalid_args(self, redis_client: TRedisClient):
        key = get_random_string(10)

        with pytest.raises(RequestError):
            await redis_client.geoadd(key, {})

        with pytest.raises(RequestError):
            await redis_client.geoadd(key, {"Place": GeospatialData(-181, 0)})

        with pytest.raises(RequestError):
            await redis_client.geoadd(key, {"Place": GeospatialData(181, 0)})

        with pytest.raises(RequestError):
            await redis_client.geoadd(key, {"Place": GeospatialData(0, 86)})

        with pytest.raises(RequestError):
            await redis_client.geoadd(key, {"Place": GeospatialData(0, -86)})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearch_by_box(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members = ["Catania", "Palermo", "edge2", "edge1"]
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        result = [
            [
                "Catania",
                [56.4413, 3479447370796909, [15.087267458438873, 37.50266842333162]],
            ],
            [
                "Palermo",
                [190.4424, 3479099956230698, [13.361389338970184, 38.1155563954963]],
            ],
            [
                "edge2",
                [279.7403, 3481342659049484, [17.241510450839996, 38.78813451624225]],
            ],
            [
                "edge1",
                [279.7405, 3479273021651468, [12.75848776102066, 38.78813451624225]],
            ],
        ]
        assert await redis_client.geoadd(key, members_coordinates) == 4

        # Test search by box, unit: kilometers, from a geospatial data
        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                OrderBy.ASC,
            )
            == members
        )

        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                OrderBy.DESC,
                with_coord=True,
                with_dist=True,
                with_hash=True,
            )
            == result[::-1]
        )

        assert await redis_client.geosearch(
            key,
            GeospatialData(15, 37),
            GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
            OrderBy.ASC,
            count=GeoSearchCount(1),
            with_dist=True,
            with_hash=True,
        ) == [["Catania", [56.4413, 3479447370796909]]]

        # Test search by box, unit: meters, from a member, with distance
        meters = 400 * 1000
        assert await redis_client.geosearch(
            key,
            "Catania",
            GeoSearchByBox(meters, meters, GeoUnit.METERS),
            OrderBy.DESC,
            with_dist=True,
        ) == [["edge2", [236529.1799]], ["Palermo", [166274.1516]], ["Catania", [0.0]]]

        # Test search by box, unit: feet, from a member, with limited count to 2, with hash
        feet = 400 * 3280.8399
        assert await redis_client.geosearch(
            key,
            "Palermo",
            GeoSearchByBox(feet, feet, GeoUnit.FEET),
            OrderBy.ASC,
            count=GeoSearchCount(2),
            with_hash=True,
        ) == [["Palermo", [3479099956230698]], ["edge1", [3479273021651468]]]

        # Test search by box, unit: miles, from a geospatial data, with limited ANY count to 1
        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(250, 250, GeoUnit.MILES),
                OrderBy.ASC,
                count=GeoSearchCount(1, True),
            )
        )[0] in members

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearch_by_radius(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        result = [
            [
                "Catania",
                [56.4413, 3479447370796909, [15.087267458438873, 37.50266842333162]],
            ],
            [
                "Palermo",
                [190.4424, 3479099956230698, [13.361389338970184, 38.1155563954963]],
            ],
        ]
        members = ["Catania", "Palermo", "edge2", "edge1"]
        assert await redis_client.geoadd(key, members_coordinates) == 4

        # Test search by radius, units: feet, from a member
        feet = 200 * 3280.8399
        assert (
            await redis_client.geosearch(
                key,
                "Catania",
                GeoSearchByRadius(feet, GeoUnit.FEET),
                OrderBy.ASC,
            )
            == members[:2]
        )

        # Test search by radius, units: meters, from a member
        meters = 200 * 1000
        assert (
            await redis_client.geosearch(
                key,
                "Catania",
                GeoSearchByRadius(meters, GeoUnit.METERS),
                OrderBy.DESC,
            )
            == members[:2][::-1]
        )

        # Test search by radius, unit: miles, from a geospatial data
        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(175, GeoUnit.MILES),
                OrderBy.DESC,
            )
            == members[::-1]
        )

        # Test search by radius, unit: kilometers, from a geospatial data, with limited count to 2
        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(200, GeoUnit.KILOMETERS),
                OrderBy.ASC,
                count=GeoSearchCount(2),
                with_coord=True,
                with_dist=True,
                with_hash=True,
            )
            == result
        )

        # Test search by radius, unit: kilometers, from a geospatial data, with limited ANY count to 1
        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(200, GeoUnit.KILOMETERS),
                OrderBy.ASC,
                count=GeoSearchCount(1, True),
                with_coord=True,
                with_dist=True,
                with_hash=True,
            )
        )[0] in result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearch_no_result(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        assert await redis_client.geoadd(key, members_coordinates) == 4

        # No membes within the aea
        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(50, 50, GeoUnit.METERS),
                OrderBy.ASC,
            )
            == []
        )

        assert (
            await redis_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(10, GeoUnit.METERS),
                OrderBy.ASC,
            )
            == []
        )

        # No members in the area (apart from the member we seach fom itself)
        assert await redis_client.geosearch(
            key,
            "Catania",
            GeoSearchByBox(10, 10, GeoUnit.KILOMETERS),
        ) == ["Catania"]

        assert await redis_client.geosearch(
            key,
            "Catania",
            GeoSearchByRadius(10, GeoUnit.METERS),
        ) == ["Catania"]

        # Search from non exiting memeber
        with pytest.raises(RequestError):
            await redis_client.geosearch(
                key,
                "non_existing_member",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError):
            await redis_client.geosearch(
                key,
                "Catania",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearchstore_by_box(self, redis_client: TRedisClient):
        key = f"{{testKey}}:{get_random_string(10)}"
        destination_key = f"{{testKey}}:{get_random_string(8)}"
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        result = {
            "Catania": [56.4412578701582, 3479447370796909.0],
            "Palermo": [190.44242984775784, 3479099956230698.0],
            "edge2": [279.7403417843143, 3481342659049484.0],
            "edge1": [279.7404521356343, 3479273021651468.0],
        }
        assert await redis_client.geoadd(key, members_coordinates) == 4

        # Test storing results of a box search, unit: kilometes, from a geospatial data
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
            )
        ) == 4  # Number of elements stored

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[1] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test storing results of a box search, unit: kilometes, from a geospatial data, with distance
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                store_dist=True,
            )
        ) == 4  # Number of elements stored

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[0] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test storing results of a box search, unit: kilometes, from a geospatial data, with count
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                count=GeoSearchCount(1),
            )
        ) == 1  # Number of elements stored

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_map, {"Catania": 3479447370796909.0}) is True

        # Test storing results of a box search, unit: meters, from a member, with distance
        meters = 400 * 1000
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByBox(meters, meters, GeoUnit.METERS),
                store_dist=True,
            )
        ) == 3  # Number of elements stored

        # Verify the stored results with distances
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_distances = {
            "Catania": 0.0,
            "Palermo": 166274.15156960033,
            "edge2": 236529.17986494553,
        }
        assert compare_maps(zrange_map, expected_distances) is True

        # Test search by box, unit: feet, from a member, with limited ANY count to 2, with hash
        feet = 400 * 3280.8399
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Palermo",
                GeoSearchByBox(feet, feet, GeoUnit.FEET),
                count=GeoSearchCount(2),
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        for member in zrange_map:
            assert member in result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearchstore_by_radius(self, redis_client: TRedisClient):
        key = f"{{testKey}}:{get_random_string(10)}"
        destination_key = f"{{testKey}}:{get_random_string(8)}"
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        result = {
            "Catania": [56.4412578701582, 3479447370796909.0],
            "Palermo": [190.44242984775784, 3479099956230698.0],
        }
        assert await redis_client.geoadd(key, members_coordinates) == 4

        # Test storing results of a radius search, unit: feet, from a member
        feet = 200 * 3280.8399
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByRadius(feet, GeoUnit.FEET),
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[1] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test search by radius, units: meters, from a member
        meters = 200 * 1000
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByRadius(meters, GeoUnit.METERS),
                store_dist=True,
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_distances = {
            "Catania": 0.0,
            "Palermo": 166274.15156960033,
        }
        assert compare_maps(zrange_map, expected_distances) is True

        # Test search by radius, unit: miles, from a geospatial data
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(175, GeoUnit.MILES),
            )
            == 4
        )

        # Test storing results of a radius search, unit: kilometers, from a geospatial data, with limited count to 2
        kilometers = 200
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(kilometers, GeoUnit.KILOMETERS),
                count=GeoSearchCount(2),
                store_dist=True,
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[0] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test storing results of a radius search, unit: kilometers, from a geospatial data, with limited ANY count to 1
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(kilometers, GeoUnit.KILOMETERS),
                count=GeoSearchCount(1, True),
            )
            == 1
        )

        # Verify the stored results
        zrange_map = await redis_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )

        for member in zrange_map:
            assert member in result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearchstore_no_result(self, redis_client: TRedisClient):
        key = f"{{testKey}}:{get_random_string(10)}"
        destination_key = f"{{testKey}}:{get_random_string(8)}"
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        assert await redis_client.geoadd(key, members_coordinates) == 4

        # No members within the area
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(50, 50, GeoUnit.METERS),
            )
            == 0
        )

        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(10, GeoUnit.METERS),
            )
            == 0
        )

        # No members in the area (apart from the member we search from itself)
        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByBox(10, 10, GeoUnit.KILOMETERS),
            )
            == 1
        )

        assert (
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByRadius(10, GeoUnit.METERS),
            )
            == 1
        )

        # Search from non-existing member
        with pytest.raises(RequestError):
            await redis_client.geosearchstore(
                destination_key,
                key,
                "non_existing_member",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

        assert await redis_client.set(key, "foo") == OK
        with pytest.raises(RequestError):
            await redis_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geohash(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await redis_client.geoadd(key, members_coordinates) == 2
        assert await redis_client.geohash(key, ["Palermo", "Catania", "Place"]) == [
            "sqc8b49rny0",
            "sqdtr74hyu0",
            None,
        ]

        assert (
            await redis_client.geohash(
                "non_existing_key", ["Palermo", "Catania", "Place"]
            )
            == [None] * 3
        )

        # Neccessary to check since we are enforcing the user to pass a list of members while redis don't
        # But when running the command with key only (and no members) the returned value will always be an empty list
        # So in case of any changes, this test will fail and inform us that we should allow not passing any members.
        assert await redis_client.geohash(key, []) == []

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.geohash(key, ["Palermo", "Catania"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geodist(self, redis_client: TRedisClient):
        key, key2 = get_random_string(10), get_random_string(10)
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await redis_client.geoadd(key, members_coordinates) == 2

        assert await redis_client.geodist(key, "Palermo", "Catania") == 166274.1516
        assert (
            await redis_client.geodist(key, "Palermo", "Catania", GeoUnit.KILOMETERS)
            == 166.2742
        )
        assert await redis_client.geodist(key, "Palermo", "Palermo", GeoUnit.MILES) == 0
        assert (
            await redis_client.geodist(
                key, "Palermo", "non-existing-member", GeoUnit.FEET
            )
            == None
        )

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.geodist(key2, "Palmero", "Catania")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geopos(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_coordinates = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await redis_client.geoadd(key, members_coordinates) == 2

        # The comparison allows for a small tolerance level due to potential precision errors in floating-point calculations
        # No worries, Python can handle it, therefore, this shouldn't fail
        positions = await redis_client.geopos(key, ["Palermo", "Catania", "Place"])
        expected_positions = [
            [13.36138933897018433, 38.11555639549629859],
            [15.08726745843887329, 37.50266842333162032],
        ]
        assert len(positions) == 3 and positions[2] is None

        assert all(
            all(
                math.isclose(actual_coord, expected_coord)
                for actual_coord, expected_coord in zip(actual_pos, expected_pos)
            )
            for actual_pos, expected_pos in zip(positions, expected_positions)
            if actual_pos is not None
        )

        assert (
            await redis_client.geopos(
                "non_existing_key", ["Palermo", "Catania", "Place"]
            )
            == [None] * 3
        )

        # Neccessary to check since we are enforcing the user to pass a list of members while redis don't
        # But when running the command with key only (and no members) the returned value will always be an empty list
        # So in case of any changes, this test will fail and inform us that we should allow not passing any members.
        assert await redis_client.geohash(key, []) == []

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.geopos(key, ["Palermo", "Catania"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_zaddincr(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zadd_incr(key, member="one", increment=2) == 3.0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_nx_xx(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 0
        )
        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == 3
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=5.0,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            is None
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=5.0,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 6.0
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_gt_lt(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": -3, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        members_scores["one"] = 10
        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                update_condition=UpdateOptions.GREATER_THAN,
                changed=True,
            )
            == 1
        )

        assert (
            await redis_client.zadd(
                key,
                members_scores=members_scores,
                update_condition=UpdateOptions.LESS_THAN,
                changed=True,
            )
            == 0
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=-3.0,
                update_condition=UpdateOptions.LESS_THAN,
            )
            == 7.0
        )

        assert (
            await redis_client.zadd_incr(
                key,
                member="one",
                increment=-3.0,
                update_condition=UpdateOptions.GREATER_THAN,
            )
            is None
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zincrby(self, redis_client: TRedisClient):
        key, member, member2 = (
            get_random_string(10),
            get_random_string(5),
            get_random_string(5),
        )

        # key does not exist
        assert await redis_client.zincrby(key, 2.5, member) == 2.5
        assert await redis_client.zscore(key, member) == 2.5

        # key exists, but value doesn't
        assert await redis_client.zincrby(key, -3.3, member2) == -3.3
        assert await redis_client.zscore(key, member2) == -3.3

        # updating existing value in existing key
        assert await redis_client.zincrby(key, 1.0, member) == 3.5
        assert await redis_client.zscore(key, member) == 3.5

        # Key exists, but it is not a sorted set
        assert await redis_client.set(key, "_") == OK
        with pytest.raises(RequestError):
            await redis_client.zincrby(key, 0.5, "_")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrem(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrem(key, ["one"]) == 1
        assert await redis_client.zrem(key, ["one", "two", "three"]) == 2

        assert await redis_client.zrem("non_existing_set", ["member"]) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zremrangebyscore(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores) == 3

        assert (
            await redis_client.zremrangebyscore(
                key, ScoreBoundary(1, False), ScoreBoundary(2)
            )
            == 1
        )
        assert (
            await redis_client.zremrangebyscore(key, ScoreBoundary(1), InfBound.NEG_INF)
            == 0
        )
        assert (
            await redis_client.zremrangebyscore(
                "non_existing_set", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zremrangebyscore(key, InfBound.NEG_INF, InfBound.POS_INF)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zremrangebylex(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores = {"a": 1, "b": 2, "c": 3, "d": 4}
        assert await redis_client.zadd(key1, members_scores) == 4

        assert (
            await redis_client.zremrangebylex(
                key1, LexBoundary("a", False), LexBoundary("c")
            )
            == 2
        )
        zremrangebylex_res = await redis_client.zrange_withscores(key1, range)
        assert compare_maps(zremrangebylex_res, {"a": 1.0, "d": 4.0}) is True

        assert (
            await redis_client.zremrangebylex(key1, LexBoundary("d"), InfBound.POS_INF)
            == 1
        )
        assert await redis_client.zrange_withscores(key1, range) == {"a": 1.0}

        # min_lex > max_lex
        assert (
            await redis_client.zremrangebylex(key1, LexBoundary("a"), InfBound.NEG_INF)
            == 0
        )
        assert await redis_client.zrange_withscores(key1, range) == {"a": 1.0}

        assert (
            await redis_client.zremrangebylex(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

        # key exists, but it is not a sorted set
        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zremrangebylex(
                key2, LexBoundary("a", False), LexBoundary("c")
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zremrangebyrank(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores = {"a": 1, "b": 2, "c": 3, "d": 4, "e": 5}
        assert await redis_client.zadd(key1, members_scores) == 5

        # Test start exceeding end
        assert await redis_client.zremrangebyrank(key1, 2, 1) == 0

        # Test removing elements by rank
        assert await redis_client.zremrangebyrank(key1, 0, 2) == 3
        zremrangebyrank_res = await redis_client.zrange_withscores(key1, range)
        assert compare_maps(zremrangebyrank_res, {"d": 4.0, "e": 5.0}) is True

        # Test removing elements beyond the existing range
        assert await redis_client.zremrangebyrank(key1, 0, 10) == 2
        assert await redis_client.zrange_withscores(key1, range) == {}

        # Test with non-existing key
        assert await redis_client.zremrangebyrank("non_existing_key", 0, 1) == 0

        # Key exists, but it is not a sorted set
        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zremrangebyrank(key2, 0, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zlexcount(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        members_scores = {"a": 1.0, "b": 2.0, "c": 3.0}

        assert await redis_client.zadd(key1, members_scores) == 3
        assert (
            await redis_client.zlexcount(key1, InfBound.NEG_INF, InfBound.POS_INF) == 3
        )
        assert (
            await redis_client.zlexcount(
                key1,
                LexBoundary("a", is_inclusive=False),
                LexBoundary("c", is_inclusive=True),
            )
            == 2
        )
        assert (
            await redis_client.zlexcount(
                key1, InfBound.NEG_INF, LexBoundary("c", is_inclusive=True)
            )
            == 3
        )
        # Incorrect range; start > end
        assert (
            await redis_client.zlexcount(
                key1, InfBound.POS_INF, LexBoundary("c", is_inclusive=True)
            )
            == 0
        )
        assert (
            await redis_client.zlexcount(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

        # key exists, but it is not a sorted set
        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zlexcount(key2, InfBound.NEG_INF, InfBound.POS_INF)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zcard(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zcard(key) == 3

        assert await redis_client.zrem(key, ["one"]) == 1
        assert await redis_client.zcard(key) == 2
        assert await redis_client.zcard("non_existing_key") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zcount(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zcount(key, InfBound.NEG_INF, InfBound.POS_INF) == 3
        assert (
            await redis_client.zcount(
                key,
                ScoreBoundary(1, is_inclusive=False),
                ScoreBoundary(3, is_inclusive=False),
            )
            == 1
        )
        assert (
            await redis_client.zcount(
                key,
                ScoreBoundary(1, is_inclusive=False),
                ScoreBoundary(3, is_inclusive=True),
            )
            == 2
        )
        assert (
            await redis_client.zcount(
                key, InfBound.NEG_INF, ScoreBoundary(3, is_inclusive=True)
            )
            == 3
        )
        assert (
            await redis_client.zcount(
                key, InfBound.POS_INF, ScoreBoundary(3, is_inclusive=True)
            )
            == 0
        )
        assert (
            await redis_client.zcount(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zscore(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zscore(key, "one") == 1.0

        assert await redis_client.zscore(key, "non_existing_member") is None
        assert (
            await redis_client.zscore("non_existing_key", "non_existing_member") is None
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zmscore(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}

        assert await redis_client.zadd(key1, members_scores=members_scores) == 3
        assert await redis_client.zmscore(key1, ["one", "two", "three"]) == [
            1.0,
            2.0,
            3.0,
        ]
        assert await redis_client.zmscore(
            key1, ["one", "non_existing_member", "non_existing_member", "three"]
        ) == [1.0, None, None, 3.0]
        assert await redis_client.zmscore("non_existing_key", ["one"]) == [None]

        assert await redis_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zmscore(key2, ["one"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zinter_commands(self, redis_client: TRedisClient):
        key1 = "{testKey}:1-" + get_random_string(10)
        key2 = "{testKey}:2-" + get_random_string(10)
        key3 = "{testKey}:3-" + get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores1 = {"one": 1.0, "two": 2.0}
        members_scores2 = {"one": 1.5, "two": 2.5, "three": 3.5}

        assert await redis_client.zadd(key1, members_scores1) == 2
        assert await redis_client.zadd(key2, members_scores2) == 3

        # zinter tests
        zinter_map = await redis_client.zinter([key1, key2])
        expected_zinter_map = ["one", "two"]
        assert zinter_map == expected_zinter_map

        # zinterstore tests
        assert await redis_client.zinterstore(key3, [key1, key2]) == 2
        zinterstore_map = await redis_client.zrange_withscores(key3, range)
        expected_zinter_map_withscores = {
            "one": 2.5,
            "two": 4.5,
        }
        assert compare_maps(zinterstore_map, expected_zinter_map_withscores) is True

        # zinter_withscores tests
        zinter_withscores_map = await redis_client.zinter_withscores([key1, key2])
        assert (
            compare_maps(zinter_withscores_map, expected_zinter_map_withscores) is True
        )

        # MAX aggregation tests
        assert (
            await redis_client.zinterstore(key3, [key1, key2], AggregationType.MAX) == 2
        )
        zinterstore_map_max = await redis_client.zrange_withscores(key3, range)
        expected_zinter_map_max = {
            "one": 1.5,
            "two": 2.5,
        }
        assert compare_maps(zinterstore_map_max, expected_zinter_map_max) is True

        zinter_withscores_map_max = await redis_client.zinter_withscores(
            [key1, key2], AggregationType.MAX
        )
        assert compare_maps(zinter_withscores_map_max, expected_zinter_map_max) is True

        # MIN aggregation tests
        assert (
            await redis_client.zinterstore(key3, [key1, key2], AggregationType.MIN) == 2
        )
        zinterstore_map_min = await redis_client.zrange_withscores(key3, range)
        expected_zinter_map_min = {
            "one": 1.0,
            "two": 2.0,
        }
        assert compare_maps(zinterstore_map_min, expected_zinter_map_min) is True

        zinter_withscores_map_min = await redis_client.zinter_withscores(
            [key1, key2], AggregationType.MIN
        )
        assert compare_maps(zinter_withscores_map_min, expected_zinter_map_min) is True

        # SUM aggregation tests
        assert (
            await redis_client.zinterstore(key3, [key1, key2], AggregationType.SUM) == 2
        )
        zinterstore_map_sum = await redis_client.zrange_withscores(key3, range)
        assert compare_maps(zinterstore_map_sum, expected_zinter_map_withscores) is True

        zinter_withscores_map_sum = await redis_client.zinter_withscores(
            [key1, key2], AggregationType.SUM
        )
        assert (
            compare_maps(zinter_withscores_map_sum, expected_zinter_map_withscores)
            is True
        )

        # Multiplying scores during aggregation tests
        assert (
            await redis_client.zinterstore(
                key3, [(key1, 2.0), (key2, 2.0)], AggregationType.SUM
            )
            == 2
        )
        zinterstore_map_multiplied = await redis_client.zrange_withscores(key3, range)
        expected_zinter_map_multiplied = {
            "one": 5.0,
            "two": 9.0,
        }
        assert (
            compare_maps(zinterstore_map_multiplied, expected_zinter_map_multiplied)
            is True
        )

        zinter_withscores_map_multiplied = await redis_client.zinter_withscores(
            [(key1, 2.0), (key2, 2.0)], AggregationType.SUM
        )
        assert (
            compare_maps(
                zinter_withscores_map_multiplied, expected_zinter_map_multiplied
            )
            is True
        )

        # Non-existing key test
        assert (
            await redis_client.zinterstore(key3, [key1, "{testKey}-non_existing_key"])
            == 0
        )
        zinter_withscores_non_existing = await redis_client.zinter_withscores(
            [key1, "{testKey}-non_existing_key"]
        )
        assert zinter_withscores_non_existing == {}

        # Empty list check
        with pytest.raises(RequestError) as e:
            await redis_client.zinterstore("{xyz}", [])
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.zinter([])
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.zinter_withscores([])
        assert "at least 1 input key is needed" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zunion_commands(self, redis_client: TRedisClient):
        key1 = "{testKey}:1-" + get_random_string(10)
        key2 = "{testKey}:2-" + get_random_string(10)
        key3 = "{testKey}:3-" + get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores1 = {"one": 1.0, "two": 2.0}
        members_scores2 = {"one": 1.5, "two": 2.5, "three": 3.5}

        assert await redis_client.zadd(key1, members_scores1) == 2
        assert await redis_client.zadd(key2, members_scores2) == 3

        # zunion tests
        zunion_map = await redis_client.zunion([key1, key2])
        expected_zunion_map = ["one", "three", "two"]
        assert zunion_map == expected_zunion_map

        # zunionstore tests
        assert await redis_client.zunionstore(key3, [key1, key2]) == 3
        zunionstore_map = await redis_client.zrange_withscores(key3, range)
        expected_zunion_map_withscores = {
            "one": 2.5,
            "three": 3.5,
            "two": 4.5,
        }
        assert compare_maps(zunionstore_map, expected_zunion_map_withscores) is True

        # zunion_withscores tests
        zunion_withscores_map = await redis_client.zunion_withscores([key1, key2])
        assert (
            compare_maps(zunion_withscores_map, expected_zunion_map_withscores) is True
        )

        # MAX aggregation tests
        assert (
            await redis_client.zunionstore(key3, [key1, key2], AggregationType.MAX) == 3
        )
        zunionstore_map_max = await redis_client.zrange_withscores(key3, range)
        expected_zunion_map_max = {
            "one": 1.5,
            "two": 2.5,
            "three": 3.5,
        }
        assert compare_maps(zunionstore_map_max, expected_zunion_map_max) is True

        zunion_withscores_map_max = await redis_client.zunion_withscores(
            [key1, key2], AggregationType.MAX
        )
        assert compare_maps(zunion_withscores_map_max, expected_zunion_map_max) is True

        # MIN aggregation tests
        assert (
            await redis_client.zunionstore(key3, [key1, key2], AggregationType.MIN) == 3
        )
        zunionstore_map_min = await redis_client.zrange_withscores(key3, range)
        expected_zunion_map_min = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.5,
        }
        assert compare_maps(zunionstore_map_min, expected_zunion_map_min) is True

        zunion_withscores_map_min = await redis_client.zunion_withscores(
            [key1, key2], AggregationType.MIN
        )
        assert compare_maps(zunion_withscores_map_min, expected_zunion_map_min) is True

        # SUM aggregation tests
        assert (
            await redis_client.zunionstore(key3, [key1, key2], AggregationType.SUM) == 3
        )
        zunionstore_map_sum = await redis_client.zrange_withscores(key3, range)
        assert compare_maps(zunionstore_map_sum, expected_zunion_map_withscores) is True

        zunion_withscores_map_sum = await redis_client.zunion_withscores(
            [key1, key2], AggregationType.SUM
        )
        assert (
            compare_maps(zunion_withscores_map_sum, expected_zunion_map_withscores)
            is True
        )

        # Multiplying scores during aggregation tests
        assert (
            await redis_client.zunionstore(
                key3, [(key1, 2.0), (key2, 2.0)], AggregationType.SUM
            )
            == 3
        )
        zunionstore_map_multiplied = await redis_client.zrange_withscores(key3, range)
        expected_zunion_map_multiplied = {
            "one": 5.0,
            "three": 7.0,
            "two": 9.0,
        }
        assert (
            compare_maps(zunionstore_map_multiplied, expected_zunion_map_multiplied)
            is True
        )

        zunion_withscores_map_multiplied = await redis_client.zunion_withscores(
            [(key1, 2.0), (key2, 2.0)], AggregationType.SUM
        )
        assert (
            compare_maps(
                zunion_withscores_map_multiplied, expected_zunion_map_multiplied
            )
            is True
        )

        # Non-existing key test
        assert (
            await redis_client.zunionstore(key3, [key1, "{testKey}-non_existing_key"])
            == 2
        )
        zunionstore_map_nonexistingkey = await redis_client.zrange_withscores(
            key3, range
        )
        expected_zunion_map_nonexistingkey = {
            "one": 1.0,
            "two": 2.0,
        }
        assert (
            compare_maps(
                zunionstore_map_nonexistingkey, expected_zunion_map_nonexistingkey
            )
            is True
        )

        zunion_withscores_non_existing = await redis_client.zunion_withscores(
            [key1, "{testKey}-non_existing_key"]
        )
        assert (
            compare_maps(
                zunion_withscores_non_existing, expected_zunion_map_nonexistingkey
            )
            is True
        )

        # Empty list check
        with pytest.raises(RequestError) as e:
            await redis_client.zunionstore("{xyz}", [])
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.zunion([])
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await redis_client.zunion_withscores([])
        assert "at least 1 input key is needed" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zpopmin(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3
        assert await redis_client.zpopmin(key) == {"a": 1.0}

        zpopmin_map = await redis_client.zpopmin(key, 3)
        expected_map = {"b": 2.0, "c": 3.0}
        assert compare_maps(zpopmin_map, expected_map) is True

        assert await redis_client.zpopmin(key) == {}
        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zpopmin(key)

        assert await redis_client.zpopmin("non_exisitng_key") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bzpopmin(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"

        assert await redis_client.zadd(key1, {"a": 1.0, "b": 1.5}) == 2
        assert await redis_client.zadd(key2, {"c": 2.0}) == 1
        assert await redis_client.bzpopmin([key1, key2], 0.5) == [key1, "a", 1.0]
        assert await redis_client.bzpopmin([non_existing_key, key2], 0.5) == [
            key2,
            "c",
            2.0,
        ]
        assert await redis_client.bzpopmin(["non_existing_key"], 0.5) is None

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.bzpopmin([], 0.5)

        # key exists, but it is not a sorted set
        assert await redis_client.set("foo", "value") == OK
        with pytest.raises(RequestError):
            await redis_client.bzpopmin(["foo"], 0.5)

        async def endless_bzpopmin_call():
            await redis_client.bzpopmin(["non_existent_key"], 0)

        # bzpopmin is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_bzpopmin_call(), timeout=0.5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zpopmax(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await redis_client.zadd(key, members_scores) == 3
        assert await redis_client.zpopmax(key) == {"c": 3.0}

        zpopmax_map = await redis_client.zpopmax(key, 3)
        expected_map = {"b": 2.0, "a": 1.0}
        assert compare_maps(zpopmax_map, expected_map) is True

        assert await redis_client.zpopmax(key) == {}
        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zpopmax(key)

        assert await redis_client.zpopmax("non_exisitng_key") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bzpopmax(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"

        assert await redis_client.zadd(key1, {"a": 1.0, "b": 1.5}) == 2
        assert await redis_client.zadd(key2, {"c": 2.0}) == 1
        assert await redis_client.bzpopmax([key1, key2], 0.5) == [key1, "b", 1.5]
        assert await redis_client.bzpopmax([non_existing_key, key2], 0.5) == [
            key2,
            "c",
            2.0,
        ]
        assert await redis_client.bzpopmax(["non_existing_key"], 0.5) is None

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.bzpopmax([], 0.5)

        # key exists, but it is not a sorted set
        assert await redis_client.set("foo", "value") == OK
        with pytest.raises(RequestError):
            await redis_client.bzpopmax(["foo"], 0.5)

        async def endless_bzpopmax_call():
            await redis_client.bzpopmax(["non_existent_key"], 0)

        # bzpopmax is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_bzpopmax_call(), timeout=0.5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_by_index(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrange(key, RangeByIndex(start=0, stop=1)) == [
            "one",
            "two",
        ]

        zrange_map = await redis_client.zrange_withscores(
            key, RangeByIndex(start=0, stop=-1)
        )
        expected_map = {"one": 1.0, "two": 2.0, "three": 3.0}
        assert compare_maps(zrange_map, expected_map) is True

        assert await redis_client.zrange(
            key, RangeByIndex(start=0, stop=1), reverse=True
        ) == [
            "three",
            "two",
        ]

        assert await redis_client.zrange(key, RangeByIndex(start=3, stop=1)) == []
        assert (
            await redis_client.zrange_withscores(key, RangeByIndex(start=3, stop=1))
            == {}
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_byscore(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrange(
            key,
            RangeByScore(
                start=InfBound.NEG_INF, stop=ScoreBoundary(3, is_inclusive=False)
            ),
        ) == ["one", "two"]

        zrange_map = await redis_client.zrange_withscores(
            key,
            RangeByScore(start=InfBound.NEG_INF, stop=InfBound.POS_INF),
        )
        expected_map = {"one": 1.0, "two": 2.0, "three": 3.0}
        assert compare_maps(zrange_map, expected_map) is True

        assert await redis_client.zrange(
            key,
            RangeByScore(
                start=ScoreBoundary(3, is_inclusive=False), stop=InfBound.NEG_INF
            ),
            reverse=True,
        ) == ["two", "one"]

        assert (
            await redis_client.zrange(
                key,
                RangeByScore(
                    start=InfBound.NEG_INF,
                    stop=InfBound.POS_INF,
                    limit=Limit(offset=1, count=2),
                ),
            )
        ) == ["two", "three"]

        assert (
            await redis_client.zrange(
                key,
                RangeByScore(
                    start=InfBound.NEG_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
                reverse=True,
            )
            == []
        )  # stop is greater than start with reverse set to True

        assert (
            await redis_client.zrange(
                key,
                RangeByScore(
                    start=InfBound.POS_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
            )
            == []
        )  # start is greater than stop

        assert (
            await redis_client.zrange_withscores(
                key,
                RangeByScore(
                    start=InfBound.POS_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
            )
            == {}
        )  # start is greater than stop

        assert (
            await redis_client.zrange_withscores(
                key,
                RangeByScore(
                    start=InfBound.NEG_INF, stop=ScoreBoundary(3, is_inclusive=False)
                ),
                reverse=True,
            )
            == {}
        )  # stop is greater than start with reverse set to True

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_bylex(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"a": 1, "b": 2, "c": 3}
        assert await redis_client.zadd(key, members_scores=members_scores) == 3

        assert await redis_client.zrange(
            key,
            RangeByLex(
                start=InfBound.NEG_INF, stop=LexBoundary("c", is_inclusive=False)
            ),
        ) == ["a", "b"]

        assert (
            await redis_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.NEG_INF,
                    stop=InfBound.POS_INF,
                    limit=Limit(offset=1, count=2),
                ),
            )
        ) == ["b", "c"]

        assert await redis_client.zrange(
            key,
            RangeByLex(
                start=LexBoundary("c", is_inclusive=False), stop=InfBound.NEG_INF
            ),
            reverse=True,
        ) == ["b", "a"]

        assert (
            await redis_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.NEG_INF, stop=LexBoundary("c", is_inclusive=False)
                ),
                reverse=True,
            )
            == []
        )  # stop is greater than start with reverse set to True

        assert (
            await redis_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.POS_INF, stop=LexBoundary("c", is_inclusive=False)
                ),
            )
            == []
        )  # start is greater than stop

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_different_types_of_keys(self, redis_client: TRedisClient):
        key = get_random_string(10)

        assert (
            await redis_client.zrange("non_existing_key", RangeByIndex(start=0, stop=1))
            == []
        )

        assert (
            await redis_client.zrange_withscores(
                "non_existing_key", RangeByIndex(start=0, stop=-1)
            )
        ) == {}

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrange(key, RangeByIndex(start=0, stop=1))

        with pytest.raises(RequestError):
            await redis_client.zrange_withscores(key, RangeByIndex(start=0, stop=1))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrangestore_by_index(self, redis_client: TRedisClient):
        destination = f"{{testKey}}:{get_random_string(10)}"
        source = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"

        member_scores = {"one": 1.0, "two": 2.0, "three": 3.0}
        assert await redis_client.zadd(source, member_scores) == 3

        # full range
        assert (
            await redis_client.zrangestore(destination, source, RangeByIndex(0, -1))
            == 3
        )
        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0, "three": 3.0}) is True

        # range from rank 0 to 1, from highest to lowest score
        assert (
            await redis_client.zrangestore(
                destination, source, RangeByIndex(0, 1), True
            )
            == 2
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"two": 2.0, "three": 3.0}) is True

        # incorrect range, as start > stop
        assert (
            await redis_client.zrangestore(destination, source, RangeByIndex(3, 1)) == 0
        )
        assert (
            await redis_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # non-existing source
        assert (
            await redis_client.zrangestore(
                destination, non_existing_key, RangeByIndex(0, -1)
            )
            == 0
        )
        assert (
            await redis_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrangestore(destination, string_key, RangeByIndex(0, -1))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrangestore_by_score(self, redis_client: TRedisClient):
        destination = f"{{testKey}}:{get_random_string(10)}"
        source = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"

        member_scores = {"one": 1.0, "two": 2.0, "three": 3.0}
        assert await redis_client.zadd(source, member_scores) == 3

        # range from negative infinity to 3 (exclusive)
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, False)),
            )
            == 2
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0}) is True

        # range from 1 (inclusive) to positive infinity
        assert (
            await redis_client.zrangestore(
                destination, source, RangeByScore(ScoreBoundary(1), InfBound.POS_INF)
            )
            == 3
        )
        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0, "three": 3.0}) is True

        # range from negative to positive infinity, limited to ranks 1 to 2
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByScore(InfBound.NEG_INF, InfBound.POS_INF, Limit(1, 2)),
            )
            == 2
        )
        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"two": 2.0, "three": 3.0}) is True

        # range from positive to negative infinity reversed, limited to ranks 1 to 2
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByScore(InfBound.POS_INF, InfBound.NEG_INF, Limit(1, 2)),
                True,
            )
            == 2
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0}) is True

        # incorrect range as start > stop
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByScore(ScoreBoundary(3, False), InfBound.NEG_INF),
            )
            == 0
        )
        assert (
            await redis_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # non-existing source
        assert (
            await redis_client.zrangestore(
                destination,
                non_existing_key,
                RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, False)),
            )
            == 0
        )
        assert (
            await redis_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrangestore(
                destination,
                string_key,
                RangeByScore(ScoreBoundary(0), ScoreBoundary(3)),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrangestore_by_lex(self, redis_client: TRedisClient):
        destination = f"{{testKey}}:{get_random_string(10)}"
        source = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await redis_client.zadd(source, member_scores) == 3

        # range from negative infinity to "c" (exclusive)
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByLex(InfBound.NEG_INF, LexBoundary("c", False)),
            )
            == 2
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"a": 1.0, "b": 2.0}) is True

        # range from "a" (inclusive) to positive infinity
        assert (
            await redis_client.zrangestore(
                destination, source, RangeByLex(LexBoundary("a"), InfBound.POS_INF)
            )
            == 3
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"a": 1.0, "b": 2.0, "c": 3.0}) is True

        # range from negative to positive infinity, limited to ranks 1 to 2
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByLex(InfBound.NEG_INF, InfBound.POS_INF, Limit(1, 2)),
            )
            == 2
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"b": 2.0, "c": 3.0}) is True

        # range from positive to negative infinity reversed, limited to ranks 1 to 2
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByLex(InfBound.POS_INF, InfBound.NEG_INF, Limit(1, 2)),
                True,
            )
            == 2
        )

        zrange_res = await redis_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"a": 1.0, "b": 2.0}) is True

        # incorrect range as start > stop
        assert (
            await redis_client.zrangestore(
                destination,
                source,
                RangeByLex(LexBoundary("c", False), InfBound.NEG_INF),
            )
            == 0
        )
        assert (
            await redis_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # non-existing source
        assert (
            await redis_client.zrangestore(
                destination,
                non_existing_key,
                RangeByLex(InfBound.NEG_INF, InfBound.POS_INF),
            )
            == 0
        )
        assert (
            await redis_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrangestore(
                destination, string_key, RangeByLex(InfBound.NEG_INF, InfBound.POS_INF)
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrank(self, redis_client: TRedisClient):
        key = get_random_string(10)
        members_scores = {"one": 1.5, "two": 2, "three": 3}
        assert await redis_client.zadd(key, members_scores) == 3
        assert await redis_client.zrank(key, "one") == 0
        if not await check_if_server_version_lt(redis_client, "7.2.0"):
            assert await redis_client.zrank_withscore(key, "one") == [0, 1.5]
            assert await redis_client.zrank_withscore(key, "non_existing_field") is None
            assert (
                await redis_client.zrank_withscore("non_existing_key", "field") is None
            )

        assert await redis_client.zrank(key, "non_existing_field") is None
        assert await redis_client.zrank("non_existing_key", "field") is None

        assert await redis_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrank(key, "one")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrevrank(self, redis_client: TRedisClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        member_scores = {"one": 1.0, "two": 2.0, "three": 3.0}

        assert await redis_client.zadd(key, member_scores) == 3
        assert await redis_client.zrevrank(key, "three") == 0
        assert await redis_client.zrevrank(key, "non_existing_member") is None
        assert (
            await redis_client.zrevrank(non_existing_key, "non_existing_member") is None
        )

        if not check_if_server_version_lt(redis_client, "7.2.0"):
            assert await redis_client.zrevrank_withscore(key, "one") == [2, 1.0]
            assert (
                await redis_client.zrevrank_withscore(key, "non_existing_member")
                is None
            )
            assert (
                await redis_client.zrevrank_withscore(
                    non_existing_key, "non_existing_member"
                )
                is None
            )

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await redis_client.zrevrank(string_key, "member")
        with pytest.raises(RequestError):
            await redis_client.zrevrank_withscore(string_key, "member")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zdiff(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores1 = {"one": 1.0, "two": 2.0, "three": 3.0}
        member_scores2 = {"two": 2.0}
        member_scores3 = {"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0}

        assert await redis_client.zadd(key1, member_scores1) == 3
        assert await redis_client.zadd(key2, member_scores2) == 1
        assert await redis_client.zadd(key3, member_scores3) == 4

        assert await redis_client.zdiff([key1, key2]) == ["one", "three"]
        assert await redis_client.zdiff([key1, key3]) == []
        assert await redis_client.zdiff([non_existing_key, key3]) == []

        zdiff_map = await redis_client.zdiff_withscores([key1, key2])
        expected_map = {
            "one": 1.0,
            "three": 3.0,
        }
        assert compare_maps(zdiff_map, expected_map) is True
        assert (
            compare_maps(await redis_client.zdiff_withscores([key1, key3]), {}) is True
        )
        assert (
            compare_maps(
                await redis_client.zdiff_withscores([non_existing_key, key3]), {}
            )
            is True
        )

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.zdiff([])

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.zdiff_withscores([])

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await redis_client.zdiff([string_key, key2])

        assert await redis_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await redis_client.zdiff_withscores([string_key, key2])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zdiffstore(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        key4 = f"{{testKey}}:4-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores1 = {"one": 1.0, "two": 2.0, "three": 3.0}
        member_scores2 = {"two": 2.0}
        member_scores3 = {"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0}

        assert await redis_client.zadd(key1, member_scores1) == 3
        assert await redis_client.zadd(key2, member_scores2) == 1
        assert await redis_client.zadd(key3, member_scores3) == 4

        assert await redis_client.zdiffstore(key4, [key1, key2]) == 2

        zrange_res = await redis_client.zrange_withscores(key4, RangeByIndex(0, -1))
        assert compare_maps(zrange_res, {"one": 1.0, "three": 3.0}) is True

        assert await redis_client.zdiffstore(key4, [key3, key2, key1]) == 1
        assert await redis_client.zrange_withscores(key4, RangeByIndex(0, -1)) == {
            "four": 4.0
        }

        assert await redis_client.zdiffstore(key4, [key1, key3]) == 0
        assert await redis_client.zrange_withscores(key4, RangeByIndex(0, -1)) == {}

        assert await redis_client.zdiffstore(key4, [non_existing_key, key1]) == 0
        assert await redis_client.zrange_withscores(key4, RangeByIndex(0, -1)) == {}

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zdiffstore(key4, [string_key, key1])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bzmpop(self, redis_client: TRedisClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        non_existing_key = f"{{test}}-non_existing_key"
        string_key = f"{{test}}-3-f{get_random_string(10)}"

        assert await redis_client.zadd(key1, {"a1": 1, "b1": 2}) == 2
        assert await redis_client.zadd(key2, {"a2": 0.1, "b2": 0.2}) == 2

        assert await redis_client.bzmpop([key1, key2], ScoreFilter.MAX, 0.1) == [
            key1,
            {"b1": 2},
        ]
        assert await redis_client.bzmpop([key2, key1], ScoreFilter.MAX, 0.1, 10) == [
            key2,
            {"b2": 0.2, "a2": 0.1},
        ]

        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert (
            await redis_client.bzmpop([non_existing_key], ScoreFilter.MIN, 0.5) is None
        )
        assert (
            await redis_client.bzmpop([non_existing_key], ScoreFilter.MIN, 0.55, 1)
            is None
        )

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.bzmpop([string_key], ScoreFilter.MAX, 0.1)
        with pytest.raises(RequestError):
            await redis_client.bzmpop([string_key], ScoreFilter.MAX, 0.1, 1)

        # incorrect argument: key list should not be empty
        with pytest.raises(RequestError):
            assert await redis_client.bzmpop([], ScoreFilter.MAX, 0.1, 1)

        # incorrect argument: count should be greater than 0
        with pytest.raises(RequestError):
            assert await redis_client.bzmpop([key1], ScoreFilter.MAX, 0.1, 0)

        # check that order of entries in the response is preserved
        entries = {}
        for i in range(0, 10):
            entries.update({f"a{i}": float(i)})

        assert await redis_client.zadd(key2, entries) == 10
        result = await redis_client.bzmpop([key2], ScoreFilter.MIN, 0.1, 10)
        assert result is not None
        result_map = cast(Mapping[str, float], result[1])
        assert compare_maps(entries, result_map) is True

        async def endless_bzmpop_call():
            await redis_client.bzmpop(["non_existent_key"], ScoreFilter.MAX, 0)

        # bzmpop is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_bzmpop_call(), timeout=0.5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrandmember(self, redis_client: TRedisClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        scores = {"one": 1, "two": 2}
        assert await redis_client.zadd(key, scores) == 2

        member = await redis_client.zrandmember(key)
        assert member in scores
        assert await redis_client.zrandmember("non_existing_key") is None

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrandmember(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrandmember_count(self, redis_client: TRedisClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        scores = {"one": 1, "two": 2}
        assert await redis_client.zadd(key, scores) == 2

        # unique values are expected as count is positive
        members = await redis_client.zrandmember_count(key, 4)
        assert len(members) == 2
        assert set(members) == {"one", "two"}

        # duplicate values are expected as count is negative
        members = await redis_client.zrandmember_count(key, -4)
        assert len(members) == 4
        for member in members:
            assert member in scores

        assert await redis_client.zrandmember_count(key, 0) == []
        assert await redis_client.zrandmember_count("non_existing_key", 0) == []

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrandmember_count(string_key, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrandmember_withscores(self, redis_client: TRedisClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        scores = {"one": 1, "two": 2}
        assert await redis_client.zadd(key, scores) == 2

        # unique values are expected as count is positive
        elements = await redis_client.zrandmember_withscores(key, 4)
        assert len(elements) == 2

        for member, score in elements:
            assert scores[str(member)] == score

        # duplicate values are expected as count is negative
        elements = await redis_client.zrandmember_withscores(key, -4)
        assert len(elements) == 4
        for member, score in elements:
            assert scores[str(member)] == score

        assert await redis_client.zrandmember_withscores(key, 0) == []
        assert await redis_client.zrandmember_withscores("non_existing_key", 0) == []

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zrandmember_withscores(string_key, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zintercard(self, redis_client: TRedisClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores1 = {"one": 1.0, "two": 2.0, "three": 3.0}
        member_scores2 = {"two": 2.0, "three": 3.0, "four": 4.0}

        assert await redis_client.zadd(key1, member_scores1) == 3
        assert await redis_client.zadd(key2, member_scores2) == 3

        assert await redis_client.zintercard([key1, key2]) == 2
        assert await redis_client.zintercard([key1, non_existing_key]) == 0

        assert await redis_client.zintercard([key1, key2], 0) == 2
        assert await redis_client.zintercard([key1, key2], 1) == 1
        assert await redis_client.zintercard([key1, key2], 3) == 2

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await redis_client.zintercard([])

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zintercard([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zmpop(self, redis_client: TRedisClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        non_existing_key = f"{{test}}-non_existing_key"
        string_key = f"{{test}}-3-f{get_random_string(10)}"

        assert await redis_client.zadd(key1, {"a1": 1, "b1": 2}) == 2
        assert await redis_client.zadd(key2, {"a2": 0.1, "b2": 0.2}) == 2

        assert await redis_client.zmpop([key1, key2], ScoreFilter.MAX) == [
            key1,
            {"b1": 2},
        ]
        assert await redis_client.zmpop([key2, key1], ScoreFilter.MAX, 10) == [
            key2,
            {"b2": 0.2, "a2": 0.1},
        ]

        assert await redis_client.zmpop([non_existing_key], ScoreFilter.MIN) is None
        assert await redis_client.zmpop([non_existing_key], ScoreFilter.MIN, 1) is None

        # key exists, but it is not a sorted set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.zmpop([string_key], ScoreFilter.MAX)
        with pytest.raises(RequestError):
            await redis_client.zmpop([string_key], ScoreFilter.MAX, 1)

        # incorrect argument: key list should not be empty
        with pytest.raises(RequestError):
            assert await redis_client.zmpop([], ScoreFilter.MAX, 1)

        # incorrect argument: count should be greater than 0
        with pytest.raises(RequestError):
            assert await redis_client.zmpop([key1], ScoreFilter.MAX, 0)

        # check that order of entries in the response is preserved
        entries = {}
        for i in range(0, 10):
            entries.update({f"a{i}": float(i)})

        assert await redis_client.zadd(key2, entries) == 10
        result = await redis_client.zmpop([key2], ScoreFilter.MIN, 10)
        assert result is not None
        result_map = cast(Mapping[str, float], result[1])
        assert compare_maps(entries, result_map) is True

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_type(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.set(key, "value") == OK
        assert (await redis_client.type(key)).lower() == "string"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.lpush(key, ["value"]) == 1
        assert (await redis_client.type(key)).lower() == "list"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.sadd(key, ["value"]) == 1
        assert (await redis_client.type(key)).lower() == "set"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.zadd(key, {"member": 1.0}) == 1
        assert (await redis_client.type(key)).lower() == "zset"
        assert await redis_client.delete([key]) == 1

        assert await redis_client.hset(key, {"field": "value"}) == 1
        assert (await redis_client.type(key)).lower() == "hash"
        assert await redis_client.delete([key]) == 1

        await redis_client.xadd(key, [("field", "value")])
        assert await redis_client.type(key) == "stream"
        assert await redis_client.delete([key]) == 1

        assert (await redis_client.type(key)).lower() == "none"

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sort_and_sort_store_with_get_or_by_args(
        self, redis_client: RedisClient
    ):
        key = "{SameSlotKey}" + get_random_string(10)
        store = "{SameSlotKey}" + get_random_string(10)
        user_key1, user_key2, user_key3, user_key4, user_key5 = (
            "user:1",
            "user:2",
            "user:3",
            "user:4",
            "user:5",
        )

        # Prepare some data
        assert await redis_client.hset(user_key1, {"name": "Alice", "age": "30"}) == 2
        assert await redis_client.hset(user_key2, {"name": "Bob", "age": "25"}) == 2
        assert await redis_client.hset(user_key3, {"name": "Charlie", "age": "35"}) == 2
        assert await redis_client.hset(user_key4, {"name": "Dave", "age": "20"}) == 2
        assert await redis_client.hset(user_key5, {"name": "Eve", "age": "40"}) == 2
        assert await redis_client.lpush("user_ids", ["5", "4", "3", "2", "1"]) == 5

        # Test sort with all arguments
        assert await redis_client.lpush(key, ["3", "1", "2"]) == 3
        result = await redis_client.sort(
            key,
            limit=Limit(0, 2),
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        assert result == ["Alice", "Bob"]

        # Test sort_store with all arguments
        sort_store_result = await redis_client.sort_store(
            key,
            store,
            limit=Limit(0, 2),
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        assert sort_store_result == 2
        sorted_list = await redis_client.lrange(store, 0, -1)
        assert sorted_list == ["Alice", "Bob"]

        # Test sort with `by` argument
        result = await redis_client.sort(
            "user_ids",
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            alpha=True,
        )
        assert result == ["Dave", "Bob", "Alice", "Charlie", "Eve"]

        # Test sort with `by` argument with missing keys to sort by
        assert await redis_client.lpush("user_ids", ["a"]) == 6
        result = await redis_client.sort(
            "user_ids",
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            alpha=True,
        )
        assert result == [None, "Dave", "Bob", "Alice", "Charlie", "Eve"]

        # Test sort with `by` argument with missing keys to sort by
        result = await redis_client.sort(
            "user_ids",
            by_pattern="user:*->name",
            get_patterns=["user:*->age"],
            alpha=True,
        )
        assert result == [None, "30", "25", "35", "20", "40"]

        # Test Limit with count 0
        result = await redis_client.sort(
            "user_ids",
            limit=Limit(0, 0),
            alpha=True,
        )
        assert result == []

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sort_and_sort_store_without_get_or_by_args(
        self, redis_client: TRedisClient
    ):
        key = "{SameSlotKey}" + get_random_string(10)
        store = "{SameSlotKey}" + get_random_string(10)

        # Test sort with non-existing key
        result = await redis_client.sort("non_existing_key")
        assert result == []

        # Test sort_store with non-existing key
        sort_store_result = await redis_client.sort_store(
            "{SameSlotKey}:non_existing_key", store
        )
        assert sort_store_result == 0

        # Test each argument separately
        assert await redis_client.lpush(key, ["5", "2", "4", "1", "3"]) == 5

        # Test w/o flags
        result = await redis_client.sort(key)
        assert result == ["1", "2", "3", "4", "5"]

        # limit argument
        result = await redis_client.sort(key, limit=Limit(1, 3))
        assert result == ["2", "3", "4"]

        # order argument
        result = await redis_client.sort(key, order=OrderBy.DESC)
        assert result == ["5", "4", "3", "2", "1"]

        assert await redis_client.lpush(key, ["a"]) == 6

        with pytest.raises(RequestError) as e:
            await redis_client.sort(key)
        assert "can't be converted into double" in str(e).lower()

        # alpha argument
        result = await redis_client.sort(key, alpha=True)
        assert result == ["1", "2", "3", "4", "5", "a"]

        # Combining multiple arguments
        result = await redis_client.sort(
            key, limit=Limit(1, 3), order=OrderBy.DESC, alpha=True
        )
        assert result == ["5", "4", "3"]

        # Test sort_store with combined arguments
        sort_store_result = await redis_client.sort_store(
            key, store, limit=Limit(1, 3), order=OrderBy.DESC, alpha=True
        )
        assert sort_store_result == 3
        sorted_list = await redis_client.lrange(store, 0, -1)
        assert sorted_list == ["5", "4", "3"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_echo(self, redis_client: TRedisClient):
        message = get_random_string(5)
        assert await redis_client.echo(message) == message
        if isinstance(redis_client, RedisClusterClient):
            echo_dict = await redis_client.echo(message, AllNodes())
            assert isinstance(echo_dict, dict)
            for value in echo_dict.values():
                assert value == message

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_dbsize(self, redis_client: TRedisClient):
        assert await redis_client.custom_command(["FLUSHALL"]) == OK

        assert await redis_client.dbsize() == 0
        key_value_pairs = [(get_random_string(10), "foo") for _ in range(10)]

        for key, value in key_value_pairs:
            assert await redis_client.set(key, value) == OK
        assert await redis_client.dbsize() == 10

        if isinstance(redis_client, RedisClusterClient):
            assert await redis_client.custom_command(["FLUSHALL"]) == OK
            key = get_random_string(5)
            assert await redis_client.set(key, value) == OK
            assert await redis_client.dbsize(SlotKeyRoute(SlotType.PRIMARY, key)) == 1
        else:
            assert await redis_client.select(1) == OK
            assert await redis_client.dbsize() == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_time(self, redis_client: TRedisClient):
        current_time = int(time.time()) - 1
        result = await redis_client.time()
        assert len(result) == 2
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert isinstance(result[1], str)
        assert int(result[0]) > current_time
        assert 0 < int(result[1]) < 1000000

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lastsave(self, redis_client: TRedisClient):
        yesterday = date.today() - timedelta(1)
        yesterday_unix_time = time.mktime(yesterday.timetuple())

        result = await redis_client.lastsave()
        assert isinstance(result, int)
        assert result > yesterday_unix_time

        if isinstance(redis_client, RedisClusterClient):
            # test with single-node route
            result = await redis_client.lastsave(RandomNode())
            assert isinstance(result, int)
            assert result > yesterday_unix_time

            # test with multi-node route
            result = await redis_client.lastsave(AllNodes())
            assert isinstance(result, dict)
            for lastsave_time in result.values():
                assert lastsave_time > yesterday_unix_time

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_append(self, redis_client: TRedisClient):
        key, value = get_random_string(10), get_random_string(5)
        assert await redis_client.append(key, value) == 5

        assert await redis_client.append(key, value) == 10
        assert await redis_client.get(key) == value * 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xadd_xtrim_xlen(self, redis_client: TRedisClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        field, field2 = get_random_string(10), get_random_string(10)

        assert (
            await redis_client.xadd(
                key,
                [(field, "foo"), (field2, "bar")],
                StreamAddOptions(make_stream=False),
            )
            is None
        )

        assert (
            await redis_client.xadd(
                key, [(field, "foo1"), (field2, "bar1")], StreamAddOptions(id="0-1")
            )
            == "0-1"
        )

        assert (
            await redis_client.xadd(key, [(field, "foo2"), (field2, "bar2")])
        ) is not None
        assert await redis_client.xlen(key) == 2

        # This will trim the first entry.
        id = await redis_client.xadd(
            key,
            [(field, "foo3"), (field2, "bar3")],
            StreamAddOptions(trim=TrimByMaxLen(exact=True, threshold=2)),
        )

        assert id is not None
        assert await redis_client.xlen(key) == 2

        # This will trim the 2nd entry.
        assert (
            await redis_client.xadd(
                key,
                [(field, "foo4"), (field2, "bar4")],
                StreamAddOptions(trim=TrimByMinId(exact=True, threshold=str(id))),
            )
            is not None
        )
        assert await redis_client.xlen(key) == 2

        assert await redis_client.xtrim(key, TrimByMaxLen(threshold=1, exact=True)) == 1
        assert await redis_client.xlen(key) == 1

        assert await redis_client.xtrim(key, TrimByMaxLen(threshold=0, exact=True)) == 1
        # Unlike other Redis collection types, stream keys still exist even after removing all entries
        assert await redis_client.exists([key]) == 1
        assert await redis_client.xlen(key) == 0

        assert (
            await redis_client.xtrim(
                non_existing_key, TrimByMaxLen(threshold=1, exact=True)
            )
            == 0
        )
        assert await redis_client.xlen(non_existing_key) == 0

        # key exists, but it is not a stream
        assert await redis_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await redis_client.xtrim(string_key, TrimByMaxLen(threshold=1, exact=True))
        with pytest.raises(RequestError):
            await redis_client.xlen(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xdel(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        stream_id1 = "0-1"
        stream_id2 = "0-2"
        stream_id3 = "0-3"

        assert (
            await redis_client.xadd(
                key1, [("f1", "foo1"), ("f2", "foo2")], StreamAddOptions(stream_id1)
            )
            == stream_id1
        )
        assert (
            await redis_client.xadd(
                key1, [("f1", "foo1"), ("f2", "foo2")], StreamAddOptions(stream_id2)
            )
            == stream_id2
        )
        assert await redis_client.xlen(key1) == 2

        # deletes one stream id, and ignores anything invalid
        assert await redis_client.xdel(key1, [stream_id1, stream_id3]) == 1
        assert await redis_client.xdel(non_existing_key, [stream_id3]) == 0

        # invalid argument - id list should not be empty
        with pytest.raises(RequestError):
            await redis_client.xdel(key1, [])

        # key exists, but it is not a stream
        assert await redis_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await redis_client.xdel(string_key, [stream_id3])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xrange_and_xrevrange(self, redis_client: TRedisClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        stream_id1 = "0-1"
        stream_id2 = "0-2"
        stream_id3 = "0-3"

        assert (
            await redis_client.xadd(
                key, [("f1", "v1")], StreamAddOptions(id=stream_id1)
            )
            == stream_id1
        )
        assert (
            await redis_client.xadd(
                key, [("f2", "v2")], StreamAddOptions(id=stream_id2)
            )
            == stream_id2
        )
        assert await redis_client.xlen(key) == 2

        # get everything from the stream
        assert await redis_client.xrange(key, MinId(), MaxId()) == {
            stream_id1: [["f1", "v1"]],
            stream_id2: [["f2", "v2"]],
        }
        assert await redis_client.xrevrange(key, MaxId(), MinId()) == {
            stream_id2: [["f2", "v2"]],
            stream_id1: [["f1", "v1"]],
        }

        # returns empty mapping if + before -
        assert await redis_client.xrange(key, MaxId(), MinId()) == {}
        # rev search returns empty mapping if - before +
        assert await redis_client.xrevrange(key, MinId(), MaxId()) == {}

        assert (
            await redis_client.xadd(
                key, [("f3", "v3")], StreamAddOptions(id=stream_id3)
            )
            == stream_id3
        )

        # get the newest entry
        assert await redis_client.xrange(
            key, ExclusiveIdBound(stream_id2), ExclusiveIdBound.from_timestamp(5), 1
        ) == {stream_id3: [["f3", "v3"]]}
        assert await redis_client.xrevrange(
            key, ExclusiveIdBound.from_timestamp(5), ExclusiveIdBound(stream_id2), 1
        ) == {stream_id3: [["f3", "v3"]]}

        # xrange/xrevrange against an emptied stream
        assert await redis_client.xdel(key, [stream_id1, stream_id2, stream_id3]) == 3
        assert await redis_client.xrange(key, MinId(), MaxId(), 10) == {}
        assert await redis_client.xrevrange(key, MaxId(), MinId(), 10) == {}

        assert await redis_client.xrange(non_existing_key, MinId(), MaxId()) == {}
        assert await redis_client.xrevrange(non_existing_key, MaxId(), MinId()) == {}

        # count value < 1 returns None
        assert await redis_client.xrange(key, MinId(), MaxId(), 0) is None
        assert await redis_client.xrange(key, MinId(), MaxId(), -1) is None
        assert await redis_client.xrevrange(key, MaxId(), MinId(), 0) is None
        assert await redis_client.xrevrange(key, MaxId(), MinId(), -1) is None

        # key exists, but it is not a stream
        assert await redis_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await redis_client.xrange(string_key, MinId(), MaxId())
        with pytest.raises(RequestError):
            await redis_client.xrevrange(string_key, MaxId(), MinId())

        # invalid start bound
        with pytest.raises(RequestError):
            await redis_client.xrange(key, IdBound("not_a_stream_id"), MaxId())
        with pytest.raises(RequestError):
            await redis_client.xrevrange(key, MaxId(), IdBound("not_a_stream_id"))

        # invalid end bound
        with pytest.raises(RequestError):
            await redis_client.xrange(key, MinId(), IdBound("not_a_stream_id"))
        with pytest.raises(RequestError):
            await redis_client.xrevrange(key, IdBound("not_a_stream_id"), MinId())

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xread(
        self, redis_client: TRedisClient, cluster_mode, protocol, request
    ):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:3-{get_random_string(10)}"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"
        stream_id1_3 = "1-3"
        stream_id2_1 = "2-1"
        stream_id2_2 = "2-2"
        stream_id2_3 = "2-3"
        non_existing_id = "99-99"

        # setup first entries in streams key1 and key2
        assert (
            await redis_client.xadd(
                key1, [("f1_1", "v1_1")], StreamAddOptions(id=stream_id1_1)
            )
            == stream_id1_1
        )
        assert (
            await redis_client.xadd(
                key2, [("f2_1", "v2_1")], StreamAddOptions(id=stream_id2_1)
            )
            == stream_id2_1
        )

        # setup second entries in streams key1 and key2
        assert (
            await redis_client.xadd(
                key1, [("f1_2", "v1_2")], StreamAddOptions(id=stream_id1_2)
            )
            == stream_id1_2
        )
        assert (
            await redis_client.xadd(
                key2, [("f2_2", "v2_2")], StreamAddOptions(id=stream_id2_2)
            )
            == stream_id2_2
        )

        # setup third entries in streams key1 and key2
        assert (
            await redis_client.xadd(
                key1, [("f1_3", "v1_3")], StreamAddOptions(id=stream_id1_3)
            )
            == stream_id1_3
        )
        assert (
            await redis_client.xadd(
                key2, [("f2_3", "v2_3")], StreamAddOptions(id=stream_id2_3)
            )
            == stream_id2_3
        )

        assert await redis_client.xread({key1: stream_id1_1, key2: stream_id2_1}) == {
            key1: {
                stream_id1_2: [["f1_2", "v1_2"]],
                stream_id1_3: [["f1_3", "v1_3"]],
            },
            key2: {
                stream_id2_2: [["f2_2", "v2_2"]],
                stream_id2_3: [["f2_3", "v2_3"]],
            },
        }

        assert await redis_client.xread({non_existing_key: stream_id1_1}) is None
        assert await redis_client.xread({key1: non_existing_id}) is None

        # passing an empty read options argument has no effect
        assert await redis_client.xread({key1: stream_id1_1}, StreamReadOptions()) == {
            key1: {
                stream_id1_2: [["f1_2", "v1_2"]],
                stream_id1_3: [["f1_3", "v1_3"]],
            },
        }

        assert await redis_client.xread(
            {key1: stream_id1_1}, StreamReadOptions(count=1)
        ) == {
            key1: {
                stream_id1_2: [["f1_2", "v1_2"]],
            },
        }
        assert await redis_client.xread(
            {key1: stream_id1_1}, StreamReadOptions(count=1, block_ms=1000)
        ) == {
            key1: {
                stream_id1_2: [["f1_2", "v1_2"]],
            },
        }

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xread_edge_cases_and_failures(
        self, redis_client: TRedisClient, cluster_mode, protocol, request
    ):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        string_key = f"{{testKey}}:2-{get_random_string(10)}"
        stream_id0 = "0-0"
        stream_id1 = "1-1"
        stream_id2 = "1-2"

        assert (
            await redis_client.xadd(
                key1, [("f1", "v1")], StreamAddOptions(id=stream_id1)
            )
            == stream_id1
        )
        assert (
            await redis_client.xadd(
                key1, [("f2", "v2")], StreamAddOptions(id=stream_id2)
            )
            == stream_id2
        )

        test_client = await create_client(
            request=request, protocol=protocol, cluster_mode=cluster_mode, timeout=900
        )
        # ensure command doesn't time out even if timeout > request timeout
        assert (
            await test_client.xread(
                {key1: stream_id2}, StreamReadOptions(block_ms=1000)
            )
            is None
        )

        async def endless_xread_call():
            await test_client.xread({key1: stream_id2}, StreamReadOptions(block_ms=0))

        # when xread is called with a block timeout of 0, it should never timeout, but we wrap the test with a timeout
        # to avoid the test getting stuck forever.
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_xread_call(), timeout=3)

        # if count is non-positive, it is ignored
        assert await redis_client.xread(
            {key1: stream_id0}, StreamReadOptions(count=0)
        ) == {
            key1: {
                stream_id1: [["f1", "v1"]],
                stream_id2: [["f2", "v2"]],
            },
        }
        assert await redis_client.xread(
            {key1: stream_id0}, StreamReadOptions(count=-1)
        ) == {
            key1: {
                stream_id1: [["f1", "v1"]],
                stream_id2: [["f2", "v2"]],
            },
        }

        # invalid stream ID
        with pytest.raises(RequestError):
            await redis_client.xread({key1: "invalid_stream_id"})

        # invalid argument - block cannot be negative
        with pytest.raises(RequestError):
            await redis_client.xread({key1: stream_id1}, StreamReadOptions(block_ms=-1))

        # invalid argument - keys_and_ids must not be empty
        with pytest.raises(RequestError):
            await redis_client.xread({})

        # key exists, but it is not a stream
        assert await redis_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await redis_client.xread({string_key: stream_id1, key1: stream_id1})
        with pytest.raises(RequestError):
            await redis_client.xread({key1: stream_id1, string_key: stream_id1})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pfadd(self, redis_client: TRedisClient):
        key = get_random_string(10)
        assert await redis_client.pfadd(key, []) == 1
        assert await redis_client.pfadd(key, ["one", "two"]) == 1
        assert await redis_client.pfadd(key, ["two"]) == 0
        assert await redis_client.pfadd(key, []) == 0

        assert await redis_client.set("foo", "value") == OK
        with pytest.raises(RequestError):
            await redis_client.pfadd("foo", [])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pfcount(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await redis_client.pfadd(key1, ["a", "b", "c"]) == 1
        assert await redis_client.pfadd(key2, ["b", "c", "d"]) == 1
        assert await redis_client.pfcount([key1]) == 3
        assert await redis_client.pfcount([key2]) == 3
        assert await redis_client.pfcount([key1, key2]) == 4
        assert await redis_client.pfcount([key1, key2, non_existing_key]) == 4
        # empty HyperLogLog data set
        assert await redis_client.pfadd(key3, []) == 1
        assert await redis_client.pfcount([key3]) == 0

        # incorrect argument - key list cannot be empty
        with pytest.raises(RequestError):
            await redis_client.pfcount([])

        # key exists, but it is not a HyperLogLog
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.pfcount([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pfmerge(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await redis_client.pfadd(key1, ["a", "b", "c"]) == 1
        assert await redis_client.pfadd(key2, ["b", "c", "d"]) == 1

        # merge into new HyperLogLog data set
        assert await redis_client.pfmerge(key3, [key1, key2]) == OK
        assert await redis_client.pfcount([key3]) == 4

        # merge into existing HyperLogLog data set
        assert await redis_client.pfmerge(key1, [key2]) == OK
        assert await redis_client.pfcount([key1]) == 4

        # non-existing source key
        assert await redis_client.pfmerge(key2, [key1, non_existing_key]) == OK
        assert await redis_client.pfcount([key2]) == 4

        # empty source key list
        assert await redis_client.pfmerge(key1, []) == OK
        assert await redis_client.pfcount([key1]) == 4

        # source key exists, but it is not a HyperLogLog
        assert await redis_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            assert await redis_client.pfmerge(key3, [string_key])

        # destination key exists, but it is not a HyperLogLog
        with pytest.raises(RequestError):
            assert await redis_client.pfmerge(string_key, [key3])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitcount(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        set_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        value = "foobar"

        assert await redis_client.set(key1, value) == OK
        assert await redis_client.bitcount(key1) == 26
        assert await redis_client.bitcount(key1, OffsetOptions(1, 1)) == 6
        assert await redis_client.bitcount(key1, OffsetOptions(0, -5)) == 10
        assert await redis_client.bitcount(non_existing_key, OffsetOptions(5, 30)) == 0
        assert await redis_client.bitcount(non_existing_key) == 0

        # key exists, but it is not a string
        assert await redis_client.sadd(set_key, [value]) == 1
        with pytest.raises(RequestError):
            await redis_client.bitcount(set_key)
        with pytest.raises(RequestError):
            await redis_client.bitcount(set_key, OffsetOptions(1, 1))

        if await check_if_server_version_lt(redis_client, "7.0.0"):
            # exception thrown because BIT and BYTE options were implemented after 7.0.0
            with pytest.raises(RequestError):
                await redis_client.bitcount(
                    key1, OffsetOptions(2, 5, BitmapIndexType.BYTE)
                )
            with pytest.raises(RequestError):
                await redis_client.bitcount(
                    key1, OffsetOptions(2, 5, BitmapIndexType.BIT)
                )
        else:
            assert (
                await redis_client.bitcount(
                    key1, OffsetOptions(2, 5, BitmapIndexType.BYTE)
                )
                == 16
            )
            assert (
                await redis_client.bitcount(
                    key1, OffsetOptions(5, 30, BitmapIndexType.BIT)
                )
                == 17
            )
            assert (
                await redis_client.bitcount(
                    key1, OffsetOptions(5, -5, BitmapIndexType.BIT)
                )
                == 23
            )
            assert (
                await redis_client.bitcount(
                    non_existing_key, OffsetOptions(5, 30, BitmapIndexType.BIT)
                )
                == 0
            )

            # key exists but it is not a string
            with pytest.raises(RequestError):
                await redis_client.bitcount(
                    set_key, OffsetOptions(1, 1, BitmapIndexType.BIT)
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_setbit(self, redis_client: TRedisClient):
        key = get_random_string(10)
        set_key = get_random_string(10)

        assert await redis_client.setbit(key, 0, 1) == 0
        assert await redis_client.setbit(key, 0, 0) == 1

        # invalid argument - offset can't be negative
        with pytest.raises(RequestError):
            assert await redis_client.setbit(key, -1, 0) == 1

        # key exists, but it is not a string
        assert await redis_client.sadd(set_key, ["foo"]) == 1
        with pytest.raises(RequestError):
            await redis_client.setbit(set_key, 0, 0)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getbit(self, redis_client: TRedisClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        value = "foobar"

        assert await redis_client.set(key, value) == OK
        assert await redis_client.getbit(key, 1) == 1
        # When offset is beyond the string length, the string is assumed to be a contiguous space with 0 bits.
        assert await redis_client.getbit(key, 1000) == 0
        # When key does not exist it is assumed to be an empty string, so offset is always out of range and the value is
        # also assumed to be a contiguous space with 0 bits.
        assert await redis_client.getbit(non_existing_key, 1) == 0

        # invalid argument - offset can't be negative
        with pytest.raises(RequestError):
            assert await redis_client.getbit(key, -1) == 1

        # key exists, but it is not a string
        assert await redis_client.sadd(set_key, ["foo"]) == 1
        with pytest.raises(RequestError):
            await redis_client.getbit(set_key, 0)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitpos_and_bitpos_interval(self, redis_client: TRedisClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        value = (
            "?f0obar"  # 00111111 01100110 00110000 01101111 01100010 01100001 01110010
        )

        assert await redis_client.set(key, value) == OK
        assert await redis_client.bitpos(key, 0) == 0
        assert await redis_client.bitpos(key, 1) == 2
        assert await redis_client.bitpos(key, 1, 1) == 9
        assert await redis_client.bitpos_interval(key, 0, 3, 5) == 24

        # `BITPOS` returns -1 for non-existing strings
        assert await redis_client.bitpos(non_existing_key, 1) == -1
        assert await redis_client.bitpos_interval(non_existing_key, 1, 3, 5) == -1

        # invalid argument - bit value must be 0 or 1
        with pytest.raises(RequestError):
            await redis_client.bitpos(key, 2)
        with pytest.raises(RequestError):
            await redis_client.bitpos_interval(key, 2, 3, 5)

        # key exists, but it is not a string
        assert await redis_client.sadd(set_key, [value]) == 1
        with pytest.raises(RequestError):
            await redis_client.bitpos(set_key, 1)
        with pytest.raises(RequestError):
            await redis_client.bitpos_interval(set_key, 1, 1, -1)

        if await check_if_server_version_lt(redis_client, "7.0.0"):
            # error thrown because BIT and BYTE options were implemented after 7.0.0
            with pytest.raises(RequestError):
                await redis_client.bitpos_interval(key, 1, 1, -1, BitmapIndexType.BYTE)
            with pytest.raises(RequestError):
                await redis_client.bitpos_interval(key, 1, 1, -1, BitmapIndexType.BIT)
        else:
            assert (
                await redis_client.bitpos_interval(key, 0, 3, 5, BitmapIndexType.BYTE)
                == 24
            )
            assert (
                await redis_client.bitpos_interval(key, 1, 43, -2, BitmapIndexType.BIT)
                == 47
            )
            assert (
                await redis_client.bitpos_interval(
                    non_existing_key, 1, 3, 5, BitmapIndexType.BYTE
                )
                == -1
            )
            assert (
                await redis_client.bitpos_interval(
                    non_existing_key, 1, 3, 5, BitmapIndexType.BIT
                )
                == -1
            )

            # key exists, but it is not a string
            with pytest.raises(RequestError):
                await redis_client.bitpos_interval(
                    set_key, 1, 1, -1, BitmapIndexType.BIT
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitop(self, redis_client: TRedisClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        keys = [key1, key2]
        destination = f"{{testKey}}:3-{get_random_string(10)}"
        non_existing_key1 = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key2 = f"{{testKey}}:5-{get_random_string(10)}"
        non_existing_keys = [non_existing_key1, non_existing_key2]
        set_key = f"{{testKey}}:6-{get_random_string(10)}"
        value1 = "foobar"
        value2 = "abcdef"

        assert await redis_client.set(key1, value1) == OK
        assert await redis_client.set(key2, value2) == OK
        assert await redis_client.bitop(BitwiseOperation.AND, destination, keys) == 6
        assert await redis_client.get(destination) == "`bc`ab"
        assert await redis_client.bitop(BitwiseOperation.OR, destination, keys) == 6
        assert await redis_client.get(destination) == "goofev"

        # reset values for simplicity of results in XOR
        assert await redis_client.set(key1, "a") == OK
        assert await redis_client.set(key2, "b") == OK
        assert await redis_client.bitop(BitwiseOperation.XOR, destination, keys) == 1
        assert await redis_client.get(destination) == "\u0003"

        # test single source key
        assert await redis_client.bitop(BitwiseOperation.AND, destination, [key1]) == 1
        assert await redis_client.get(destination) == "a"
        assert await redis_client.bitop(BitwiseOperation.OR, destination, [key1]) == 1
        assert await redis_client.get(destination) == "a"
        assert await redis_client.bitop(BitwiseOperation.XOR, destination, [key1]) == 1
        assert await redis_client.get(destination) == "a"
        assert await redis_client.bitop(BitwiseOperation.NOT, destination, [key1]) == 1
        # currently, attempting to get the value from destination after the above NOT incorrectly raises an error
        # TODO: update with a GET call once fix is implemented for https://github.com/aws/glide-for-redis/issues/1447

        assert await redis_client.setbit(key1, 0, 1) == 0
        assert await redis_client.bitop(BitwiseOperation.NOT, destination, [key1]) == 1
        assert await redis_client.get(destination) == "\u001e"

        # stores None when all keys hold empty strings
        assert (
            await redis_client.bitop(
                BitwiseOperation.AND, destination, non_existing_keys
            )
            == 0
        )
        assert await redis_client.get(destination) is None
        assert (
            await redis_client.bitop(
                BitwiseOperation.OR, destination, non_existing_keys
            )
            == 0
        )
        assert await redis_client.get(destination) is None
        assert (
            await redis_client.bitop(
                BitwiseOperation.XOR, destination, non_existing_keys
            )
            == 0
        )
        assert await redis_client.get(destination) is None
        assert (
            await redis_client.bitop(
                BitwiseOperation.NOT, destination, [non_existing_key1]
            )
            == 0
        )
        assert await redis_client.get(destination) is None

        # invalid argument - source key list cannot be empty
        with pytest.raises(RequestError):
            await redis_client.bitop(BitwiseOperation.OR, destination, [])

        # invalid arguments - NOT cannot be passed more than 1 key
        with pytest.raises(RequestError):
            await redis_client.bitop(BitwiseOperation.NOT, destination, [key1, key2])

        assert await redis_client.sadd(set_key, [value1]) == 1
        # invalid argument - source key has the wrong type
        with pytest.raises(RequestError):
            await redis_client.bitop(BitwiseOperation.AND, destination, [set_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitfield(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        foobar = "foobar"
        u2 = UnsignedEncoding(2)
        u7 = UnsignedEncoding(7)
        i3 = SignedEncoding(3)
        i8 = SignedEncoding(8)
        offset1 = BitOffset(1)
        offset5 = BitOffset(5)
        offset_multiplier4 = BitOffsetMultiplier(4)
        offset_multiplier8 = BitOffsetMultiplier(8)
        overflow_set = BitFieldSet(u2, offset1, -10)
        overflow_get = BitFieldGet(u2, offset1)

        # binary value: 01100110 01101111 01101111 01100010 01100001 01110010
        assert await redis_client.set(key1, foobar) == OK

        # SET tests
        assert await redis_client.bitfield(
            key1,
            [
                # binary value becomes: 0(10)00110 01101111 01101111 01100010 01100001 01110010
                BitFieldSet(u2, offset1, 2),
                # binary value becomes: 01000(011) 01101111 01101111 01100010 01100001 01110010
                BitFieldSet(i3, offset5, 3),
                # binary value becomes: 01000011 01101111 01101111 0110(0010 010)00001 01110010
                BitFieldSet(u7, offset_multiplier4, 18),
                # addressing with SET or INCRBY bits outside the current string length will enlarge the string,
                # zero-padding it, as needed, for the minimal length needed, according to the most far bit touched.
                #
                # binary value becomes:
                # 01000011 01101111 01101111 01100010 01000001 01110010 00000000 00000000 (00010100)
                BitFieldSet(i8, offset_multiplier8, 20),
                BitFieldGet(u2, offset1),
                BitFieldGet(i3, offset5),
                BitFieldGet(u7, offset_multiplier4),
                BitFieldGet(i8, offset_multiplier8),
            ],
        ) == [3, -2, 19, 0, 2, 3, 18, 20]

        # INCRBY tests
        assert await redis_client.bitfield(
            key1,
            [
                # binary value becomes:
                # 0(11)00011 01101111 01101111 01100010 01000001 01110010 00000000 00000000 00010100
                BitFieldIncrBy(u2, offset1, 1),
                # binary value becomes:
                # 01100(101) 01101111 01101111 01100010 01000001 01110010 00000000 00000000 00010100
                BitFieldIncrBy(i3, offset5, 2),
                # binary value becomes:
                # 01100101 01101111 01101111 0110(0001 111)00001 01110010 00000000 00000000 00010100
                BitFieldIncrBy(u7, offset_multiplier4, -3),
                # binary value becomes:
                # 01100101 01101111 01101111 01100001 11100001 01110010 00000000 00000000 (00011110)
                BitFieldIncrBy(i8, offset_multiplier8, 10),
            ],
        ) == [3, -3, 15, 30]

        # OVERFLOW WRAP is used by default if no OVERFLOW is specified
        assert await redis_client.bitfield(
            key2,
            [
                overflow_set,
                BitFieldOverflow(BitOverflowControl.WRAP),
                overflow_set,
                overflow_get,
            ],
        ) == [0, 2, 2]

        # OVERFLOW affects only SET or INCRBY after OVERFLOW subcommand
        assert await redis_client.bitfield(
            key2,
            [
                overflow_set,
                BitFieldOverflow(BitOverflowControl.SAT),
                overflow_set,
                overflow_get,
                BitFieldOverflow(BitOverflowControl.FAIL),
                overflow_set,
            ],
        ) == [2, 2, 3, None]

        # if the key doesn't exist, the operation is performed as though the missing value was a string with all bits
        # set to 0.
        assert await redis_client.bitfield(
            non_existing_key, [BitFieldSet(UnsignedEncoding(2), BitOffset(3), 2)]
        ) == [0]

        # empty subcommands argument returns an empty list
        assert await redis_client.bitfield(key1, []) == []

        # invalid argument - offset must be >= 0
        with pytest.raises(RequestError):
            await redis_client.bitfield(
                key1, [BitFieldSet(UnsignedEncoding(5), BitOffset(-1), 1)]
            )

        # invalid argument - encoding size must be > 0
        with pytest.raises(RequestError):
            await redis_client.bitfield(
                key1, [BitFieldSet(UnsignedEncoding(0), BitOffset(1), 1)]
            )

        # invalid argument - unsigned encoding size must be < 64
        with pytest.raises(RequestError):
            await redis_client.bitfield(
                key1, [BitFieldSet(UnsignedEncoding(64), BitOffset(1), 1)]
            )

        # invalid argument - signed encoding size must be < 65
        with pytest.raises(RequestError):
            await redis_client.bitfield(
                key1, [BitFieldSet(SignedEncoding(65), BitOffset(1), 1)]
            )

        # key exists, but it is not a string
        assert await redis_client.sadd(set_key, [foobar]) == 1
        with pytest.raises(RequestError):
            await redis_client.bitfield(
                set_key, [BitFieldSet(SignedEncoding(3), BitOffset(1), 2)]
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitfield_read_only(self, redis_client: TRedisClient):
        min_version = "6.0.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        foobar = "foobar"
        unsigned_offset_get = BitFieldGet(UnsignedEncoding(2), BitOffset(1))

        # binary value: 01100110 01101111 01101111 01100010 01100001 01110010
        assert await redis_client.set(key, foobar) == OK
        assert await redis_client.bitfield_read_only(
            key,
            [
                # Get value in: 0(11)00110 01101111 01101111 01100010 01100001 01110010 00010100
                unsigned_offset_get,
                # Get value in: 01100(110) 01101111 01101111 01100010 01100001 01110010 00010100
                BitFieldGet(SignedEncoding(3), BitOffset(5)),
                # Get value in: 01100110 01101111 01101(111 0110)0010 01100001 01110010 00010100
                BitFieldGet(UnsignedEncoding(7), BitOffsetMultiplier(3)),
                # Get value in: 01100110 01101111 (01101111) 01100010 01100001 01110010 00010100
                BitFieldGet(SignedEncoding(8), BitOffsetMultiplier(2)),
            ],
        ) == [3, -2, 118, 111]
        # offset is greater than current length of string: the operation is performed like the missing part all consists
        # of bits set to 0.
        assert await redis_client.bitfield_read_only(
            key, [BitFieldGet(UnsignedEncoding(3), BitOffset(100))]
        ) == [0]
        # similarly, if the key doesn't exist, the operation is performed as though the missing value was a string with
        # all bits set to 0.
        assert await redis_client.bitfield_read_only(
            non_existing_key, [unsigned_offset_get]
        ) == [0]

        # empty subcommands argument returns an empty list
        assert await redis_client.bitfield_read_only(key, []) == []

        # invalid argument - offset must be >= 0
        with pytest.raises(RequestError):
            await redis_client.bitfield_read_only(
                key, [BitFieldGet(UnsignedEncoding(5), BitOffset(-1))]
            )

        # invalid argument - encoding size must be > 0
        with pytest.raises(RequestError):
            await redis_client.bitfield_read_only(
                key, [BitFieldGet(UnsignedEncoding(0), BitOffset(1))]
            )

        # invalid argument - unsigned encoding size must be < 64
        with pytest.raises(RequestError):
            await redis_client.bitfield_read_only(
                key, [BitFieldGet(UnsignedEncoding(64), BitOffset(1))]
            )

        # invalid argument - signed encoding size must be < 65
        with pytest.raises(RequestError):
            await redis_client.bitfield_read_only(
                key, [BitFieldGet(SignedEncoding(65), BitOffset(1))]
            )

        # key exists, but it is not a string
        assert await redis_client.sadd(set_key, [foobar]) == 1
        with pytest.raises(RequestError):
            await redis_client.bitfield_read_only(set_key, [unsigned_offset_get])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_encoding(self, redis_client: TRedisClient):
        string_key = get_random_string(10)
        list_key = get_random_string(10)
        hashtable_key = get_random_string(10)
        intset_key = get_random_string(10)
        set_listpack_key = get_random_string(10)
        hash_hashtable_key = get_random_string(10)
        hash_listpack_key = get_random_string(10)
        skiplist_key = get_random_string(10)
        zset_listpack_key = get_random_string(10)
        stream_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await redis_client.object_encoding(non_existing_key) is None

        assert await redis_client.set(
            string_key, "a really loooooooooooooooooooooooooooooooooooooooong value"
        )
        assert await redis_client.object_encoding(string_key) == "raw"

        assert await redis_client.set(string_key, "2") == OK
        assert await redis_client.object_encoding(string_key) == "int"

        assert await redis_client.set(string_key, "value") == OK
        assert await redis_client.object_encoding(string_key) == "embstr"

        assert await redis_client.lpush(list_key, ["1"]) == 1
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.object_encoding(list_key) == "quicklist"
        else:
            assert await redis_client.object_encoding(list_key) == "listpack"

        # The default value of set-max-intset-entries is 512
        for i in range(0, 513):
            assert await redis_client.sadd(hashtable_key, [str(i)]) == 1
        assert await redis_client.object_encoding(hashtable_key) == "hashtable"

        assert await redis_client.sadd(intset_key, ["1"]) == 1
        assert await redis_client.object_encoding(intset_key) == "intset"

        assert await redis_client.sadd(set_listpack_key, ["foo"]) == 1
        if await check_if_server_version_lt(redis_client, "7.2.0"):
            assert await redis_client.object_encoding(set_listpack_key) == "hashtable"
        else:
            assert await redis_client.object_encoding(set_listpack_key) == "listpack"

        # The default value of hash-max-listpack-entries is 512
        for i in range(0, 513):
            assert await redis_client.hset(hash_hashtable_key, {str(i): "2"}) == 1
        assert await redis_client.object_encoding(hash_hashtable_key) == "hashtable"

        assert await redis_client.hset(hash_listpack_key, {"1": "2"}) == 1
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.object_encoding(hash_listpack_key) == "ziplist"
        else:
            assert await redis_client.object_encoding(hash_listpack_key) == "listpack"

        # The default value of zset-max-listpack-entries is 128
        for i in range(0, 129):
            assert await redis_client.zadd(skiplist_key, {str(i): 2.0}) == 1
        assert await redis_client.object_encoding(skiplist_key) == "skiplist"

        assert await redis_client.zadd(zset_listpack_key, {"1": 2.0}) == 1
        if await check_if_server_version_lt(redis_client, "7.0.0"):
            assert await redis_client.object_encoding(zset_listpack_key) == "ziplist"
        else:
            assert await redis_client.object_encoding(zset_listpack_key) == "listpack"

        assert await redis_client.xadd(stream_key, [("field", "value")]) is not None
        assert await redis_client.object_encoding(stream_key) == "stream"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_freq(self, redis_client: TRedisClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = await redis_client.config_get([maxmemory_policy_key])
        maxmemory_policy = cast(str, config.get(maxmemory_policy_key))

        try:
            assert (
                await redis_client.config_set({maxmemory_policy_key: "allkeys-lfu"})
                == OK
            )
            assert await redis_client.object_freq(non_existing_key) is None
            assert await redis_client.set(key, "") == OK
            freq = await redis_client.object_freq(key)
            assert freq is not None and freq >= 0
        finally:
            await redis_client.config_set({maxmemory_policy_key: maxmemory_policy})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_idletime(self, redis_client: TRedisClient):
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await redis_client.object_idletime(non_existing_key) is None
        assert await redis_client.set(string_key, "foo") == OK
        time.sleep(2)
        idletime = await redis_client.object_idletime(string_key)
        assert idletime is not None and idletime > 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_refcount(self, redis_client: TRedisClient):
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await redis_client.object_refcount(non_existing_key) is None
        assert await redis_client.set(string_key, "foo") == OK
        refcount = await redis_client.object_refcount(string_key)
        assert refcount is not None and refcount >= 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_srandmember(self, redis_client: TRedisClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        elements = ["one", "two"]
        assert await redis_client.sadd(key, elements) == 2

        member = await redis_client.srandmember(key)
        assert member in elements
        assert await redis_client.srandmember("non_existing_key") is None

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.srandmember(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_srandmember_count(self, redis_client: TRedisClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        elements = ["one", "two"]
        assert await redis_client.sadd(key, elements) == 2

        # unique values are expected as count is positive
        members = await redis_client.srandmember_count(key, 4)
        assert len(members) == 2
        assert set(members) == {"one", "two"}

        # duplicate values are expected as count is negative
        members = await redis_client.srandmember_count(key, -4)
        assert len(members) == 4
        for member in members:
            assert member in elements

        # empty return values for non-existing or empty keys
        assert await redis_client.srandmember_count(key, 0) == []
        assert await redis_client.srandmember_count("non_existing_key", 0) == []

        # key exists, but it is not a set
        assert await redis_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await redis_client.srandmember_count(string_key, 8)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_flushall(self, redis_client: TRedisClient):
        min_version = "6.2.0"
        key = f"{{key}}-1{get_random_string(5)}"
        value = get_random_string(5)

        await redis_client.set(key, value)
        assert await redis_client.dbsize() > 0
        assert await redis_client.flushall() is OK
        assert await redis_client.flushall(FlushMode.ASYNC) is OK
        if not await check_if_server_version_lt(redis_client, min_version):
            assert await redis_client.flushall(FlushMode.SYNC) is OK
        assert await redis_client.dbsize() == 0

        if isinstance(redis_client, RedisClusterClient):
            await redis_client.set(key, value)
            assert await redis_client.flushall(route=AllPrimaries()) is OK
            assert await redis_client.flushall(FlushMode.ASYNC, AllPrimaries()) is OK
            if not await check_if_server_version_lt(redis_client, min_version):
                assert await redis_client.flushall(FlushMode.SYNC, AllPrimaries()) is OK
            assert await redis_client.dbsize() == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getex(self, redis_client: TRedisClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        key1 = get_random_string(10)
        non_existing_key = get_random_string(10)
        value = get_random_string(10)

        assert await redis_client.set(key1, value) == OK
        assert await redis_client.getex(non_existing_key) is None
        assert await redis_client.getex(key1) == value
        assert await redis_client.ttl(key1) == -1

        # setting expiration timer
        assert (
            await redis_client.getex(key1, ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 50))
            == value
        )
        assert await redis_client.ttl(key1) != -1

        # setting and clearing expiration timer
        assert await redis_client.set(key1, value) == OK
        assert (
            await redis_client.getex(key1, ExpiryGetEx(ExpiryTypeGetEx.SEC, 10))
            == value
        )
        assert (
            await redis_client.getex(key1, ExpiryGetEx(ExpiryTypeGetEx.PERSIST, None))
            == value
        )
        assert await redis_client.ttl(key1) == -1

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_copy_no_database(self, redis_client: TRedisClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        source = f"{{testKey}}:1-{get_random_string(10)}"
        destination = f"{{testKey}}:2-{get_random_string(10)}"
        value1 = get_random_string(5)
        value2 = get_random_string(5)

        # neither key exists
        assert await redis_client.copy(source, destination, replace=False) is False
        assert await redis_client.copy(source, destination) is False

        # source exists, destination does not
        await redis_client.set(source, value1)
        assert await redis_client.copy(source, destination, replace=False) is True
        assert await redis_client.get(destination) == value1

        # new value for source key
        await redis_client.set(source, value2)

        # both exists, no REPLACE
        assert await redis_client.copy(source, destination) is False
        assert await redis_client.copy(source, destination, replace=False) is False
        assert await redis_client.get(destination) == value1

        # both exists, with REPLACE
        assert await redis_client.copy(source, destination, replace=True) is True
        assert await redis_client.get(destination) == value2

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_copy_database(self, redis_client: RedisClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(redis_client, min_version):
            return pytest.mark.skip(reason=f"Redis version required >= {min_version}")

        source = get_random_string(10)
        destination = get_random_string(10)
        value1 = get_random_string(5)
        value2 = get_random_string(5)
        index0 = 0
        index1 = 1
        index2 = 2

        try:
            assert await redis_client.select(index0) == OK

            # neither key exists
            assert (
                await redis_client.copy(source, destination, index1, replace=False)
                is False
            )

            # source exists, destination does not
            await redis_client.set(source, value1)
            assert (
                await redis_client.copy(source, destination, index1, replace=False)
                is True
            )
            assert await redis_client.select(index1) == OK
            assert await redis_client.get(destination) == value1

            # new value for source key
            assert await redis_client.select(index0) == OK
            await redis_client.set(source, value2)

            # no REPLACE, copying to existing key on DB 0 & 1, non-existing key on DB 2
            assert (
                await redis_client.copy(source, destination, index1, replace=False)
                is False
            )
            assert (
                await redis_client.copy(source, destination, index2, replace=False)
                is True
            )

            # new value only gets copied to DB 2
            assert await redis_client.select(index1) == OK
            assert await redis_client.get(destination) == value1
            assert await redis_client.select(index2) == OK
            assert await redis_client.get(destination) == value2

            # both exists, with REPLACE, when value isn't the same, source always get copied to destination
            assert await redis_client.select(index0) == OK
            assert (
                await redis_client.copy(source, destination, index1, replace=True)
                is True
            )
            assert await redis_client.select(index1) == OK
            assert await redis_client.get(destination) == value2

            # invalid DB index
            with pytest.raises(RequestError):
                await redis_client.copy(source, destination, -1, replace=True)
        finally:
            assert await redis_client.select(0) == OK


class TestMultiKeyCommandCrossSlot:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_multi_key_command_returns_cross_slot_error(
        self, redis_client: RedisClusterClient
    ):
        promises: list[Any] = [
            redis_client.blpop(["abc", "zxy", "lkn"], 0.1),
            redis_client.brpop(["abc", "zxy", "lkn"], 0.1),
            redis_client.rename("abc", "zxy"),
            redis_client.zdiffstore("abc", ["zxy", "lkn"]),
            redis_client.zdiff(["abc", "zxy", "lkn"]),
            redis_client.zdiff_withscores(["abc", "zxy", "lkn"]),
            redis_client.zrangestore("abc", "zxy", RangeByIndex(0, -1)),
            redis_client.zinterstore("{xyz}", ["{abc}", "{def}"]),
            redis_client.zunionstore("{xyz}", ["{abc}", "{def}"]),
            redis_client.bzpopmin(["abc", "zxy", "lkn"], 0.5),
            redis_client.bzpopmax(["abc", "zxy", "lkn"], 0.5),
            redis_client.smove("abc", "def", "_"),
            redis_client.sunionstore("abc", ["zxy", "lkn"]),
            redis_client.sinter(["abc", "zxy", "lkn"]),
            redis_client.sinterstore("abc", ["zxy", "lkn"]),
            redis_client.sdiff(["abc", "zxy", "lkn"]),
            redis_client.sdiffstore("abc", ["def", "ghi"]),
            redis_client.renamenx("abc", "def"),
            redis_client.pfcount(["def", "ghi"]),
            redis_client.pfmerge("abc", ["def", "ghi"]),
            redis_client.zinter(["def", "ghi"]),
            redis_client.zinter_withscores(["def", "ghi"]),
            redis_client.zunion(["def", "ghi"]),
            redis_client.zunion_withscores(["def", "ghi"]),
            redis_client.sort_store("abc", "zxy"),
            redis_client.lmove("abc", "zxy", ListDirection.LEFT, ListDirection.LEFT),
            redis_client.blmove(
                "abc", "zxy", ListDirection.LEFT, ListDirection.LEFT, 1
            ),
            redis_client.msetnx({"abc": "abc", "zxy": "zyx"}),
            redis_client.sunion(["def", "ghi"]),
            redis_client.bitop(BitwiseOperation.OR, "abc", ["zxy", "lkn"]),
            redis_client.xread({"abc": "0-0", "zxy": "0-0"}),
        ]

        if not await check_if_server_version_lt(redis_client, "6.2.0"):
            promises.extend(
                [
                    redis_client.geosearchstore(
                        "abc",
                        "zxy",
                        GeospatialData(15, 37),
                        GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                    ),
                    redis_client.copy("abc", "zxy", replace=True),
                ]
            )

        if not await check_if_server_version_lt(redis_client, "7.0.0"):
            promises.extend(
                [
                    redis_client.bzmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX, 0.1),
                    redis_client.zintercard(["abc", "def"]),
                    redis_client.zmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX),
                    redis_client.sintercard(["def", "ghi"]),
                    redis_client.lmpop(["def", "ghi"], ListDirection.LEFT),
                    redis_client.blmpop(["def", "ghi"], ListDirection.LEFT, 1),
                ]
            )

        for promise in promises:
            with pytest.raises(RequestError) as e:
                await promise
            assert "crossslot" in str(e).lower()

        # TODO bz*, zunion, sdiff and others - all rest multi-key commands except ones tested below
        pass

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_multi_key_command_routed_to_multiple_nodes(
        self, redis_client: RedisClusterClient
    ):
        await redis_client.exists(["abc", "zxy", "lkn"])
        await redis_client.unlink(["abc", "zxy", "lkn"])
        await redis_client.delete(["abc", "zxy", "lkn"])
        await redis_client.mget(["abc", "zxy", "lkn"])
        await redis_client.mset({"abc": "1", "zxy": "2", "lkn": "3"})
        await redis_client.touch(["abc", "zxy", "lkn"])


class TestCommandsUnitTests:
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
            ExpiryType.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpirySet(ExpiryType.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpirySet(
            ExpiryType.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

    def test_get_expiry_cmd_args(self):
        exp_sec = ExpiryGetEx(ExpiryTypeGetEx.SEC, 5)
        assert exp_sec.get_cmd_args() == ["EX", "5"]

        exp_sec_timedelta = ExpiryGetEx(ExpiryTypeGetEx.SEC, timedelta(seconds=5))
        assert exp_sec_timedelta.get_cmd_args() == ["EX", "5"]

        exp_millsec = ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 5)
        assert exp_millsec.get_cmd_args() == ["PX", "5"]

        exp_millsec_timedelta = ExpiryGetEx(
            ExpiryTypeGetEx.MILLSEC, timedelta(seconds=5)
        )
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_millsec_timedelta = ExpiryGetEx(
            ExpiryTypeGetEx.MILLSEC, timedelta(seconds=5)
        )
        assert exp_millsec_timedelta.get_cmd_args() == ["PX", "5000"]

        exp_unix_sec = ExpiryGetEx(ExpiryTypeGetEx.UNIX_SEC, 1682575739)
        assert exp_unix_sec.get_cmd_args() == ["EXAT", "1682575739"]

        exp_unix_sec_datetime = ExpiryGetEx(
            ExpiryTypeGetEx.UNIX_SEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_sec_datetime.get_cmd_args() == ["EXAT", "1682639759"]

        exp_unix_millisec = ExpiryGetEx(ExpiryTypeGetEx.UNIX_MILLSEC, 1682586559964)
        assert exp_unix_millisec.get_cmd_args() == ["PXAT", "1682586559964"]

        exp_unix_millisec_datetime = ExpiryGetEx(
            ExpiryTypeGetEx.UNIX_MILLSEC,
            datetime(2023, 4, 27, 23, 55, 59, 342380, timezone.utc),
        )
        assert exp_unix_millisec_datetime.get_cmd_args() == ["PXAT", "1682639759342"]

        exp_persist = ExpiryGetEx(
            ExpiryTypeGetEx.PERSIST,
            None,
        )
        assert exp_persist.get_cmd_args() == ["PERSIST"]

    def test_expiry_raises_on_value_error(self):
        with pytest.raises(ValueError):
            ExpirySet(ExpiryType.SEC, 5.5)

    def test_is_single_response(self):
        assert is_single_response("This is a string value", "")
        assert is_single_response(["value", "value"], [""])
        assert not is_single_response(
            [["value", ["value"]], ["value", ["valued"]]], [""]
        )
        assert is_single_response(None, None)


@pytest.mark.asyncio
class TestClusterRoutes:
    async def cluster_route_custom_command_multi_nodes(
        self,
        redis_client: RedisClusterClient,
        route: Route,
    ):
        cluster_nodes = await redis_client.custom_command(["CLUSTER", "NODES"])
        assert isinstance(cluster_nodes, (str, list))
        cluster_nodes = get_first_result(cluster_nodes)
        num_of_nodes = len(cluster_nodes.splitlines())
        expected_num_of_results = (
            num_of_nodes
            if isinstance(route, AllNodes)
            else num_of_nodes - cluster_nodes.count("slave")
        )
        expected_primary_count = cluster_nodes.count("master")
        expected_replica_count = (
            cluster_nodes.count("slave") if isinstance(route, AllNodes) else 0
        )

        all_results = await redis_client.custom_command(["INFO", "REPLICATION"], route)
        assert isinstance(all_results, dict)
        assert len(all_results) == expected_num_of_results
        primary_count = 0
        replica_count = 0
        for _, info_res in all_results.items():
            assert "role:master" in info_res or "role:slave" in info_res
            if "role:master" in info_res:
                primary_count += 1
            else:
                replica_count += 1
        assert primary_count == expected_primary_count
        assert replica_count == expected_replica_count

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_all_nodes(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_multi_nodes(redis_client, AllNodes())

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_all_primaries(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_multi_nodes(
            redis_client, AllPrimaries()
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_random_node(
        self, redis_client: RedisClusterClient
    ):
        info_res = await redis_client.custom_command(
            ["INFO", "REPLICATION"], RandomNode()
        )
        assert type(info_res) is str
        assert "role:master" in info_res or "role:slave" in info_res

    async def cluster_route_custom_command_slot_route(
        self, redis_client: RedisClusterClient, is_slot_key: bool
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
        assert isinstance(replica_res, str)
        assert "myself,slave" in replica_res
        for node_line in replica_res:
            if "myself" in node_line:
                primary_node_id = node_line.split(" ")[3]
                assert primary_node_id == expected_primary_node_id

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_slot_key_route(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_slot_route(redis_client, True)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_slot_id_route(
        self, redis_client: RedisClusterClient
    ):
        await self.cluster_route_custom_command_slot_route(redis_client, False)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_random_route(self, redis_client: RedisClusterClient):
        info = await redis_client.info([InfoSection.SERVER], RandomNode())
        assert isinstance(info, str)
        assert "# Server" in info

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_by_address_reaches_correct_node(
        self, redis_client: RedisClusterClient
    ):
        # returns the line that contains the word "myself", up to that point. This is done because the values after it might change with time.
        def clean_result(value: TResult):
            assert type(value) is str
            for line in value.splitlines():
                if "myself" in line:
                    return line.split("myself")[0]
            raise Exception(
                f"Couldn't find 'myself' in the cluster nodes output: {value}"
            )

        cluster_nodes = clean_result(
            await redis_client.custom_command(["cluster", "nodes"], RandomNode())
        )
        assert isinstance(cluster_nodes, str)
        host = cluster_nodes.split(" ")[1].split("@")[0]

        second_result = clean_result(
            await redis_client.custom_command(
                ["cluster", "nodes"], ByAddressRoute(host)
            )
        )

        assert cluster_nodes == second_result

        host, port = host.split(":")
        port_as_int = int(port)

        third_result = clean_result(
            await redis_client.custom_command(
                ["cluster", "nodes"], ByAddressRoute(host, port_as_int)
            )
        )

        assert cluster_nodes == third_result

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_fail_routing_by_address_if_no_port_is_provided(
        self, redis_client: RedisClusterClient
    ):
        with pytest.raises(RequestError):
            await redis_client.info(route=ByAddressRoute("foo"))


@pytest.mark.asyncio
class TestScripts:
    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script(self, redis_client: TRedisClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        script = Script("return 'Hello'")
        assert await redis_client.invoke_script(script) == "Hello"

        script = Script("return redis.call('SET', KEYS[1], ARGV[1])")
        assert (
            await redis_client.invoke_script(script, keys=[key1], args=["value1"])
            == "OK"
        )
        # Reuse the same script with different parameters.
        assert (
            await redis_client.invoke_script(script, keys=[key2], args=["value2"])
            == "OK"
        )
        script = Script("return redis.call('GET', KEYS[1])")
        assert await redis_client.invoke_script(script, keys=[key1]) == "value1"
        assert await redis_client.invoke_script(script, keys=[key2]) == "value2"


@pytest.mark.asyncio
class TestPubSub:

    async def test_pubsub_basic_standalone(self, request):
        CHANNEL_NAME = "test-channel"
        MESSAGE = "test-message"
        PATTERN = "*"

        publishing_client: RedisClient = await create_client(
            request, cluster_mode=False
        )

        standalone_mode_pubsub = RedisClientConfiguration.PubSubSubscriptions(
            channels_and_patterns={
                RedisClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME},
                RedisClientConfiguration.PubSubChannelModes.Pattern: {PATTERN},
            },
            callback=None,
            context=None,
        )

        # will be used with get_pubsub_message
        listening_client_async = await create_client(
            request,
            cluster_mode=False,
            standalone_mode_pubsub=copy.deepcopy(standalone_mode_pubsub),
        )

        listening_client_try = await create_client(
            request,
            cluster_mode=False,
            standalone_mode_pubsub=copy.deepcopy(standalone_mode_pubsub),
        )

        async_messages: List[RedisClient.PubSubMsg] = []
        try_messages: List[RedisClient.PubSubMsg] = []
        callback_messages: List[RedisClient.PubSubMsg] = []

        def new_message(msg: RedisClient.PubSubMsg, context: Any):
            received_messages: List[RedisClient.PubSubMsg] = context
            received_messages.append(msg)

        # create callback client
        standalone_mode_pubsub_with_callback = copy.deepcopy(standalone_mode_pubsub)
        standalone_mode_pubsub_with_callback.callback = new_message
        standalone_mode_pubsub_with_callback.context = callback_messages
        _ = await create_client(
            request,
            cluster_mode=False,
            standalone_mode_pubsub=standalone_mode_pubsub_with_callback,
        )

        await publishing_client.publish(MESSAGE, CHANNEL_NAME)
        # allow the message to propagate
        await asyncio.sleep(3)

        # get messages explicitly
        for i in range(2):
            async_messages.append(await listening_client_async.get_pubsub_message())
            try_messages.append(listening_client_try.try_get_pubsub_message())

        # assert no more messages by try_get_pubsub_message
        assert listening_client_try.try_get_pubsub_message() is None

        # assert 2 messages are received
        assert len(async_messages) == 2
        assert len(try_messages) == 2
        assert len(callback_messages) == 2

        # assert all api flavors produced the the same messages
        assert async_messages == try_messages
        assert async_messages == callback_messages

        pattern_cnt = 0
        pattern = None
        for i in range(2):
            pubsub_msg = async_messages[i]
            assert pubsub_msg.channel == CHANNEL_NAME
            assert pubsub_msg.message == MESSAGE
            if pubsub_msg.pattern:
                pattern_cnt += 1
                pattern = pubsub_msg.pattern

        assert pattern == PATTERN
        assert pattern_cnt == 1

    async def test_pubsub_basic_clustermode(self, request):
        CHANNEL_NAME = "test-channel"
        SHARDED_CHANNEL_NAME = "test-channel-sharded"
        MESSAGE = "test-message"

        publishing_client: RedisClusterClient = await create_client(
            request, cluster_mode=True
        )
        test_sharded = not await check_if_server_version_lt(publishing_client, "7.0.0")

        cluster_mode_pubsub = ClusterClientConfiguration.PubSubSubscriptions(
            channels_and_patterns={
                ClusterClientConfiguration.PubSubChannelModes.Exact: {CHANNEL_NAME}
            },
            callback=None,
            context=None,
        )
        if test_sharded:
            cluster_mode_pubsub.channels_and_patterns[
                ClusterClientConfiguration.PubSubChannelModes.Sharded
            ] = {SHARDED_CHANNEL_NAME}

        listening_client = await create_client(
            request, cluster_mode=True, cluster_mode_pubsub=cluster_mode_pubsub
        )

        await publishing_client.publish(MESSAGE, CHANNEL_NAME)
        # allow the message to propagate
        await asyncio.sleep(1)

        pubsub_msg = await listening_client.get_pubsub_message()
        assert pubsub_msg.channel == CHANNEL_NAME
        assert pubsub_msg.message == MESSAGE
        assert pubsub_msg.pattern is None

        if test_sharded:
            await publishing_client.publish(MESSAGE, SHARDED_CHANNEL_NAME, sharded=True)
            # allow the message to propagate
            await asyncio.sleep(1)

            pubsub_msg = await listening_client.get_pubsub_message()
            assert pubsub_msg.channel == SHARDED_CHANNEL_NAME
            assert pubsub_msg.message == MESSAGE
            assert pubsub_msg.pattern is None
