# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import asyncio
import copy
import math
import time
from datetime import date, datetime, timedelta, timezone
from typing import Any, Dict, List, Mapping, Optional, Tuple, Union, cast

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
    FunctionRestorePolicy,
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
    StreamClaimOptions,
    StreamGroupOptions,
    StreamPendingOptions,
    StreamReadGroupOptions,
    StreamReadOptions,
    TrimByMaxLen,
    TrimByMinId,
)
from glide.async_commands.transaction import ClusterTransaction, Transaction
from glide.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    ProtocolVersion,
    ServerCredentials,
)
from glide.constants import OK, TEncodable, TFunctionStatsSingleNodeResponse, TResult
from glide.exceptions import TimeoutError as GlideTimeoutError
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
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
    check_function_list_response,
    check_function_stats_response,
    check_if_server_version_lt,
    compare_maps,
    convert_bytes_to_string_object,
    convert_string_to_bytes_object,
    create_long_running_lua_script,
    create_lua_lib_with_long_running_function,
    generate_lua_lib_code,
    get_first_result,
    get_random_string,
    is_single_response,
    parse_info_response,
)


@pytest.mark.asyncio
class TestGlideClients:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_register_client_name_and_version(self, glide_client: TGlideClient):
        min_version = "7.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")
        info = await glide_client.custom_command(["CLIENT", "INFO"])
        assert isinstance(info, bytes)
        info_str = info.decode()
        assert "lib-name=GlidePy" in info_str
        assert "lib-ver=unknown" in info_str

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_send_and_receive_large_values(self, request, cluster_mode, protocol):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=5000
        )
        length = 2**25  # 33mb
        key = "0" * length
        value = "0" * length
        assert len(key) == length
        assert len(value) == length
        await glide_client.set(key, value)
        assert await glide_client.get(key) == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_send_and_receive_non_ascii_unicode(self, glide_client: TGlideClient):
        key = "foo"
        value = "שלום hello 汉字"
        assert value == "שלום hello 汉字"
        await glide_client.set(key, value)
        assert await glide_client.get(key) == value.encode()
        # check set and get in bytes
        await glide_client.set(key.encode(), value.encode())
        assert await glide_client.get(key.encode()) == value.encode()

    @pytest.mark.parametrize("value_size", [100, 2**16])
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_handle_concurrent_workload_without_dropping_or_changing_values(
        self, glide_client: TGlideClient, value_size
    ):
        num_of_concurrent_tasks = 100
        running_tasks = set()

        async def exec_command(i):
            range_end = 1 if value_size > 100 else 100
            for _ in range(range_end):
                value = get_random_string(value_size)
                assert await glide_client.set(str(i), value) == OK
                assert await glide_client.get(str(i)) == value.encode()

        for i in range(num_of_concurrent_tasks):
            task = asyncio.create_task(exec_command(i))
            running_tasks.add(task)
            task.add_done_callback(running_tasks.discard)
        await asyncio.gather(*(list(running_tasks)))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_connect_with_auth_requirepass(
        self, glide_client: TGlideClient, request
    ):
        is_cluster = isinstance(glide_client, GlideClusterClient)
        password = "TEST_AUTH"
        credentials = ServerCredentials(password)
        try:
            await glide_client.custom_command(
                ["CONFIG", "SET", "requirepass", password]
            )

            with pytest.raises(ClosingError, match="NOAUTH"):
                # Creation of a new client without password should fail
                await create_client(
                    request,
                    is_cluster,
                    addresses=glide_client.config.addresses,
                )

            auth_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=glide_client.config.addresses,
            )
            key = get_random_string(10)
            assert await auth_client.set(key, key) == OK
            assert await auth_client.get(key) == key.encode()
        finally:
            # Reset the password
            auth_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=glide_client.config.addresses,
            )
            await auth_client.custom_command(["CONFIG", "SET", "requirepass", ""])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_connect_with_auth_acl(
        self, glide_client: Union[GlideClient, GlideClusterClient], request
    ):
        is_cluster = isinstance(glide_client, GlideClusterClient)
        username = "testuser"
        password = "TEST_AUTH"
        try:
            assert (
                await glide_client.custom_command(
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
            assert await glide_client.set(key, key) == OK
            credentials = ServerCredentials(password, username)

            testuser_client = await create_client(
                request,
                is_cluster,
                credentials,
                addresses=glide_client.config.addresses,
            )
            assert await testuser_client.get(key) == key.encode()
            with pytest.raises(RequestError) as e:
                # This client isn't authorized to perform SET
                await testuser_client.set("foo", "bar")
            assert "NOPERM" in str(e)
        finally:
            # Delete this user
            await glide_client.custom_command(["ACL", "DELUSER", username])

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_select_standalone_database_id(self, request, cluster_mode):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, database_id=4
        )
        client_info = await glide_client.custom_command(["CLIENT", "INFO"])
        assert b"db=4" in client_info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_name(self, request, cluster_mode, protocol):
        glide_client = await create_client(
            request,
            cluster_mode=cluster_mode,
            client_name="TEST_CLIENT_NAME",
            protocol=protocol,
        )
        client_info = await glide_client.custom_command(["CLIENT", "INFO"])
        assert b"name=TEST_CLIENT_NAME" in client_info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_closed_client_raises_error(self, glide_client: TGlideClient):
        await glide_client.close()
        with pytest.raises(ClosingError) as e:
            await glide_client.set("foo", "bar")
        assert "the client is closed" in str(e)


@pytest.mark.asyncio
class TestCommands:
    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_socket_set_get(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
        assert await glide_client.set(key, value) == OK
        assert await glide_client.get(key) == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP3])
    async def test_use_resp3_protocol(self, glide_client: TGlideClient):
        result = cast(Dict[bytes, bytes], await glide_client.custom_command(["HELLO"]))

        assert int(result[b"proto"]) == 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2])
    async def test_allow_opt_in_to_resp2_protocol(self, glide_client: TGlideClient):
        result = cast(Dict[bytes, bytes], await glide_client.custom_command(["HELLO"]))

        assert int(result[b"proto"]) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_conditional_set(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value = get_random_string(10)
        res = await glide_client.set(
            key, value, conditional_set=ConditionalChange.ONLY_IF_EXISTS
        )
        assert res is None
        res = await glide_client.set(
            key, value, conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST
        )
        assert res == OK
        assert await glide_client.get(key) == value.encode()
        res = await glide_client.set(
            key, "foobar", conditional_set=ConditionalChange.ONLY_IF_DOES_NOT_EXIST
        )
        assert res is None
        assert await glide_client.get(key) == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_set_return_old_value(self, glide_client: TGlideClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")
        key = get_random_string(10)
        value = get_random_string(10)
        res = await glide_client.set(key, value)
        assert res == OK
        assert await glide_client.get(key) == value.encode()
        new_value = get_random_string(10)
        res = await glide_client.set(key, new_value, return_old_value=True)
        assert res == value.encode()
        assert await glide_client.get(key) == new_value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_single_arg(self, glide_client: TGlideClient):
        # Test single arg command
        res = await glide_client.custom_command(["PING"])
        assert res == b"PONG"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_multi_arg(self, glide_client: TGlideClient):
        # Test multi args command
        client_list = await glide_client.custom_command(
            ["CLIENT", "LIST", "TYPE", "NORMAL"]
        )
        assert isinstance(client_list, (bytes, list))
        res = get_first_result(client_list)
        assert res is not None
        assert b"id" in res
        assert b"cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_multi_arg_in_TEncodable(
        self, glide_client: TGlideClient
    ):
        # Test multi args command
        client_list = await glide_client.custom_command(
            ["CLIENT", b"LIST", "TYPE", b"NORMAL"]
        )
        assert isinstance(client_list, (bytes, list))
        res = get_first_result(client_list)
        assert res is not None
        assert b"id" in res
        assert b"cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_custom_command_lower_and_upper_case(
        self, glide_client: TGlideClient
    ):
        # Test multi args command
        client_list = await glide_client.custom_command(
            ["CLIENT", "LIST", "TYPE", "NORMAL"]
        )
        assert isinstance(client_list, (bytes, list))
        res = get_first_result(client_list)
        assert res is not None
        assert b"id" in res
        assert b"cmd=client" in res

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_request_error_raises_exception(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value = get_random_string(10)
        await glide_client.set(key, value)
        with pytest.raises(RequestError) as e:
            await glide_client.custom_command(["HSET", key, "1", "bar"])
        assert "WRONGTYPE" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_server_replication(self, glide_client: TGlideClient):
        info_res = get_first_result(await glide_client.info([InfoSection.SERVER]))
        info = info_res.decode()
        assert "# Server" in info
        info = get_first_result(
            await glide_client.info([InfoSection.REPLICATION])
        ).decode()
        assert "# Replication" in info
        assert "# Errorstats" not in info

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_default(self, glide_client: TGlideClient):
        cluster_mode = isinstance(glide_client, GlideClusterClient)
        info_result = await glide_client.info()
        if cluster_mode:
            cluster_nodes = await glide_client.custom_command(["CLUSTER", "NODES"])
            assert isinstance(cluster_nodes, (bytes, list))
            cluster_nodes = get_first_result(cluster_nodes)
            expected_num_of_results = cluster_nodes.count(b"master")
            assert len(info_result) == expected_num_of_results
        info_result = get_first_result(info_result)
        assert b"# Memory" in info_result

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_select(self, glide_client: GlideClient):
        assert await glide_client.select(0) == OK
        key = get_random_string(10)
        value = get_random_string(10)
        assert await glide_client.set(key, value) == OK
        assert await glide_client.get(key) == value.encode()
        assert await glide_client.select(1) == OK
        assert await glide_client.get(key) is None
        assert await glide_client.select(0) == OK
        assert await glide_client.get(key) == value.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_move(self, glide_client: GlideClient):
        key = get_random_string(10)
        value = get_random_string(10)

        assert await glide_client.select(0) == OK
        assert await glide_client.move(key, 1) is False

        assert await glide_client.set(key, value) == OK
        assert await glide_client.get(key) == value.encode()

        assert await glide_client.move(key, 1) is True
        assert await glide_client.get(key) is None
        assert await glide_client.select(1) == OK
        assert await glide_client.get(key) == value.encode()

        with pytest.raises(RequestError) as e:
            await glide_client.move(key, -1)

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_move_with_bytes(self, glide_client: GlideClient):
        key = get_random_string(10)
        value = get_random_string(10)

        assert await glide_client.select(0) == OK

        assert await glide_client.set(key, value) == OK
        assert await glide_client.get(key.encode()) == value.encode()

        assert await glide_client.move(key.encode(), 1) is True
        assert await glide_client.get(key) is None
        assert await glide_client.get(key.encode()) is None
        assert await glide_client.select(1) == OK
        assert await glide_client.get(key) == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_delete(self, glide_client: TGlideClient):
        keys = [get_random_string(10), get_random_string(10), get_random_string(10)]
        value = get_random_string(10)
        value_encoded = value.encode()
        [await glide_client.set(key, value) for key in keys]
        assert await glide_client.get(keys[0]) == value_encoded
        assert await glide_client.get(keys[1]) == value_encoded
        assert await glide_client.get(keys[2]) == value_encoded
        delete_keys = keys + [get_random_string(10)]
        assert await glide_client.delete(delete_keys) == 3
        assert await glide_client.delete(keys) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getdel(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value = get_random_string(10)
        non_existing_key = get_random_string(10)
        list_key = get_random_string(10)
        assert await glide_client.set(key, value) == "OK"

        # Retrieve and delete existing key
        assert await glide_client.getdel(key) == value.encode()
        assert await glide_client.get(key) is None

        # Try to get and delete a non-existing key
        assert await glide_client.getdel(non_existing_key) is None

        assert await glide_client.lpush(list_key, [value]) == 1
        with pytest.raises(RequestError) as e:
            await glide_client.getdel(list_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getrange(self, glide_client: TGlideClient):
        key = get_random_string(16)
        value = get_random_string(10)
        value_encoded = value.encode()
        non_string_key = get_random_string(10)

        assert await glide_client.set(key, value) == OK
        assert await glide_client.getrange(key, 0, 3) == value_encoded[:4]
        assert await glide_client.getrange(key, -3, -1) == value_encoded[-3:]
        assert await glide_client.getrange(key.encode(), -3, -1) == value_encoded[-3:]
        assert await glide_client.getrange(key, 0, -1) == value_encoded

        # out of range
        assert await glide_client.getrange(key, 10, 100) == value_encoded[10:]
        assert await glide_client.getrange(key, -200, -3) == value_encoded[-200:-2]
        assert await glide_client.getrange(key, 100, 200) == b""

        # incorrect range
        assert await glide_client.getrange(key, -1, -3) == b""

        # a redis bug, fixed in version 8: https://github.com/redis/redis/issues/13207
        if await check_if_server_version_lt(glide_client, "8.0.0"):
            assert await glide_client.getrange(key, -200, -100) == value[0].encode()
        else:
            assert await glide_client.getrange(key, -200, -100) == b""

        if await check_if_server_version_lt(glide_client, "8.0.0"):
            assert await glide_client.getrange(non_string_key, 0, -1) == b""

        # non-string key
        assert await glide_client.lpush(non_string_key, ["_"]) == 1
        with pytest.raises(RequestError):
            await glide_client.getrange(non_string_key, 0, -1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_reset_stat(self, glide_client: TGlideClient):
        # we execute set and info so the commandstats will show `cmdstat_set::calls` greater than 1
        # after the configResetStat call we initiate an info command and the the commandstats won't contain `cmdstat_set`.
        await glide_client.set("foo", "bar")
        info_stats = str(await glide_client.info([InfoSection.COMMAND_STATS]))

        assert "cmdstat_set" in info_stats

        assert await glide_client.config_resetstat() == OK
        info_stats = str(await glide_client.info([InfoSection.COMMAND_STATS]))

        # 1 stands for the second info command
        assert "cmdstat_set" not in info_stats

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_rewrite(self, glide_client: TGlideClient):
        info_server = parse_info_response(
            get_first_result(await glide_client.info([InfoSection.SERVER]))
        )
        if len(info_server["config_file"]) > 0:
            assert await glide_client.config_rewrite() == OK
        else:
            # We expect Valkey to return an error since the test cluster doesn't use valkey.conf file
            with pytest.raises(RequestError) as e:
                await glide_client.config_rewrite()
            assert "The server is running without a config file" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_id(self, glide_client: TGlideClient):
        client_id = await glide_client.client_id()
        assert type(client_id) is int
        assert client_id > 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_existing_key(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "10") == OK
        assert await glide_client.incr(key) == 11
        assert await glide_client.get(key) == b"11"
        assert await glide_client.incrby(key, 4) == 15
        assert await glide_client.get(key) == b"15"
        assert await glide_client.incrbyfloat(key, 5.5) == 20.5
        assert await glide_client.get(key) == b"20.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_non_existing_key(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(10)
        key3 = get_random_string(10)

        assert await glide_client.get(key) is None
        assert await glide_client.incr(key) == 1
        assert await glide_client.get(key) == b"1"

        assert await glide_client.get(key2) is None
        assert await glide_client.incrby(key2, 3) == 3
        assert await glide_client.get(key2) == b"3"

        assert await glide_client.get(key3) is None
        assert await glide_client.incrbyfloat(key3, 0.5) == 0.5
        assert await glide_client.get(key3) == b"0.5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_incr_commands_with_str_value(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.incr(key)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.incrby(key, 3)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.incrbyfloat(key, 3.5)
        assert "value is not a valid float" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_client_getname(self, glide_client: TGlideClient):
        assert await glide_client.client_getname() is None
        assert (
            await glide_client.custom_command(["CLIENT", "SETNAME", "GlideConnection"])
            == OK
        )
        assert await glide_client.client_getname() == b"GlideConnection"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_mset_mget(self, glide_client: TGlideClient):
        keys = [get_random_string(10), get_random_string(10), get_random_string(10)]
        non_existing_key = get_random_string(10)
        key_value_pairs = {key: value for key, value in zip(keys, keys)}

        assert await glide_client.mset(key_value_pairs) == OK

        # Add the non-existing key
        keys.append(non_existing_key)
        mget_res = await glide_client.mget(keys)
        keys[-1] = None
        assert mget_res == [key.encode() if key is not None else key for key in keys]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_touch(self, glide_client: TGlideClient):
        keys = [get_random_string(10), get_random_string(10)]
        key_value_pairs = {key: value for key, value in zip(keys, keys)}

        assert await glide_client.mset(key_value_pairs) == OK
        assert await glide_client.touch(keys) == 2

        # 2 existing keys, one non-existing
        assert await glide_client.touch([*keys, get_random_string(3)]) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_msetnx(self, glide_client: TGlideClient):
        key1 = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        key3 = f"{{key}}-3{get_random_string(5)}"
        non_existing = get_random_string(5)
        value = get_random_string(5)
        value_encoded = value.encode()
        key_value_map1: Mapping[TEncodable, TEncodable] = {key1: value, key2: value}
        key_value_map2: Mapping[TEncodable, TEncodable] = {
            key2: get_random_string(5),
            key3: value,
        }

        assert await glide_client.msetnx(key_value_map1) is True
        mget_res = await glide_client.mget([key1, key2, non_existing])
        assert mget_res == [value_encoded, value_encoded, None]

        assert await glide_client.msetnx(key_value_map2) is False
        assert await glide_client.get(key3) is None
        assert await glide_client.get(key2) == value_encoded

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ping(self, glide_client: TGlideClient):
        assert await glide_client.ping() == b"PONG"
        assert await glide_client.ping("HELLO") == b"HELLO"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_config_get_set(self, glide_client: TGlideClient):
        previous_timeout = await glide_client.config_get(["timeout"])
        assert await glide_client.config_set({"timeout": "1000"}) == OK
        assert await glide_client.config_get(["timeout"]) == {b"timeout": b"1000"}
        # revert changes to previous timeout
        previous_timeout_decoded = convert_bytes_to_string_object(previous_timeout)
        assert isinstance(previous_timeout_decoded, dict)
        assert isinstance(previous_timeout_decoded["timeout"], str)
        assert (
            await glide_client.config_set(
                {"timeout": previous_timeout_decoded["timeout"]}
            )
            == OK
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_decrby_existing_key(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "10") == OK
        assert await glide_client.decr(key) == 9
        assert await glide_client.get(key) == b"9"
        assert await glide_client.decrby(key, 4) == 5
        assert await glide_client.get(key) == b"5"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_decrby_non_existing_key(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(10)

        assert await glide_client.get(key) is None
        assert await glide_client.decr(key) == -1
        assert await glide_client.get(key) == b"-1"

        assert await glide_client.get(key2) is None
        assert await glide_client.decrby(key2, 3) == -3
        assert await glide_client.get(key2) == b"-3"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_decr_with_str_value(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.decr(key)

        assert "value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.decrby(key, 3)

        assert "value is not an integer" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_setrange(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        # test new key and existing key
        assert await glide_client.setrange(key1, 0, "Hello World") == 11
        assert await glide_client.setrange(key1, 6, "GLIDE") == 11

        # offset > len
        assert await glide_client.setrange(key1, 15, "GLIDE") == 20

        # negative offset
        with pytest.raises(RequestError):
            assert await glide_client.setrange(key1, -1, "GLIDE")

        # non-string key throws RequestError
        assert await glide_client.lpush(key2, ["_"]) == 1
        with pytest.raises(RequestError):
            assert await glide_client.setrange(key2, 0, "_")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hset_hget_hgetall(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hget(key, field) == b"value"
        assert await glide_client.hget(key, field2) == b"value2"
        assert await glide_client.hget(key, "non_existing_field") is None

        hgetall_map = await glide_client.hgetall(key)
        expected_map = {
            field.encode(): b"value",
            field2.encode(): b"value2",
        }
        assert compare_maps(hgetall_map, expected_map) is True
        assert await glide_client.hgetall("non_existing_field") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hdel(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2", field3: "value3"}

        assert await glide_client.hset(key, field_value_map) == 3
        assert await glide_client.hdel(key, [field, field2]) == 2
        assert await glide_client.hdel(key, ["nonExistingField"]) == 0
        assert await glide_client.hdel("nonExistingKey", [field3]) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hsetnx(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)

        assert await glide_client.hsetnx(key, field, "value") == True
        assert await glide_client.hsetnx(key, field, "new value") == False
        assert await glide_client.hget(key, field) == b"value"
        key = get_random_string(5)
        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hsetnx(key, field, "value")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hmget(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hmget(key, [field, "nonExistingField", field2]) == [
            b"value",
            None,
            b"value2",
        ]
        assert await glide_client.hmget("nonExistingKey", [field, field2]) == [
            None,
            None,
        ]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hset_without_data(self, glide_client: TGlideClient):
        with pytest.raises(RequestError) as e:
            await glide_client.hset("key", {})

        assert "wrong number of arguments" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_hincrbyfloat(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "10"}

        assert await glide_client.hset(key, field_value_map) == 1
        assert await glide_client.hincrby(key, field, 1) == 11
        assert await glide_client.hincrby(key, field, 4) == 15
        assert await glide_client.hincrbyfloat(key, field, 1.5) == 16.5

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_non_existing_key_field(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "10"}

        assert await glide_client.hincrby("nonExistingKey", field, 1) == 1
        assert await glide_client.hset(key, field_value_map) == 1
        assert await glide_client.hincrby(key, "nonExistingField", 2) == 2
        assert await glide_client.hset(key2, field_value_map) == 1
        assert await glide_client.hincrbyfloat(key2, "nonExistingField", -0.5) == -0.5

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hincrby_invalid_value(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field_value_map = {field: "value"}

        assert await glide_client.hset(key, field_value_map) == 1

        with pytest.raises(RequestError) as e:
            await glide_client.hincrby(key, field, 2)
        assert "hash value is not an integer" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.hincrbyfloat(key, field, 1.5)
        assert "hash value is not a float" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hexist(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hexists(key, field)
        assert not await glide_client.hexists(key, "nonExistingField")
        assert not await glide_client.hexists("nonExistingKey", field2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hlen(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hlen(key) == 2
        assert await glide_client.hdel(key, [field]) == 1
        assert await glide_client.hlen(key) == 1
        assert await glide_client.hlen("non_existing_hash") == 0

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hlen(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hvals(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hvals(key) == [b"value", b"value2"]
        assert await glide_client.hdel(key, [field]) == 1
        assert await glide_client.hvals(key) == [b"value2"]
        assert await glide_client.hvals("non_existing_key") == []

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hvals(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hkeys(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hkeys(key) == [
            field.encode(),
            field2.encode(),
        ]
        assert await glide_client.hdel(key, [field]) == 1
        assert await glide_client.hkeys(key) == [field2.encode()]
        assert await glide_client.hkeys("non_existing_key") == []

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hkeys(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hrandfield(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        assert await glide_client.hrandfield(key) in [
            field.encode(),
            field2.encode(),
        ]
        assert await glide_client.hrandfield("non_existing_key") is None

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hrandfield(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hrandfield_count(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        # Unique values are expected as count is positive
        rand_fields = await glide_client.hrandfield_count(key, 4)
        assert len(rand_fields) == 2
        assert set(rand_fields) == {field.encode(), field2.encode()}

        # Duplicate values are expected as count is negative
        rand_fields = await glide_client.hrandfield_count(key, -4)
        assert len(rand_fields) == 4
        for rand_field in rand_fields:
            assert rand_field in [field.encode(), field2.encode()]

        assert await glide_client.hrandfield_count(key, 0) == []
        assert await glide_client.hrandfield_count("non_existing_key", 4) == []

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hrandfield_count(key2, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hrandfield_withvalues(self, glide_client: TGlideClient):
        key = get_random_string(10)
        key2 = get_random_string(5)
        field = get_random_string(5)
        field2 = get_random_string(5)
        field_value_map = {field: "value", field2: "value2"}

        assert await glide_client.hset(key, field_value_map) == 2
        # Unique values are expected as count is positive
        rand_fields_with_values = await glide_client.hrandfield_withvalues(key, 4)
        assert len(rand_fields_with_values) == 2
        for field_with_value in rand_fields_with_values:
            assert field_with_value in [
                [field.encode(), b"value"],
                [field2.encode(), b"value2"],
            ]

        # Duplicate values are expected as count is negative
        rand_fields_with_values = await glide_client.hrandfield_withvalues(key, -4)
        assert len(rand_fields_with_values) == 4
        for field_with_value in rand_fields_with_values:
            assert field_with_value in [
                [field.encode(), b"value"],
                [field2.encode(), b"value2"],
            ]

        assert await glide_client.hrandfield_withvalues(key, 0) == []
        assert await glide_client.hrandfield_withvalues("non_existing_key", 4) == []

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.hrandfield_withvalues(key2, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hstrlen(self, glide_client: TGlideClient):
        key = get_random_string(10)

        assert await glide_client.hstrlen(key, "field") == 0
        assert await glide_client.hset(key, {"field": "value"}) == 1
        assert await glide_client.hstrlen(key, "field") == 5

        assert await glide_client.hstrlen(key, "field2") == 0

        await glide_client.set(key, "value")
        with pytest.raises(RequestError):
            await glide_client.hstrlen(key, "field")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpush_lpop_lrange(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value_list: List[TEncodable] = ["value4", "value3", "value2", "value1"]

        assert await glide_client.lpush(key, value_list) == 4
        assert await glide_client.lpop(key) == cast(str, value_list[-1]).encode()
        assert await glide_client.lrange(key, 0, -1) == convert_string_to_bytes_object(
            value_list[-2::-1]
        )
        assert await glide_client.lpop_count(key, 2) == convert_string_to_bytes_object(
            value_list[-2:0:-1]
        )
        assert await glide_client.lrange("non_existing_key", 0, -1) == []
        assert await glide_client.lpop("non_existing_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpush_lpop_lrange_wrong_type_raise_error(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await glide_client.lpush(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.lpop(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.lrange(key, 0, -1)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpushx(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        # new key
        assert await glide_client.lpushx(key1, ["1"]) == 0
        assert await glide_client.lrange(key1, 0, -1) == []
        # existing key
        assert await glide_client.lpush(key1, ["0"]) == 1
        assert await glide_client.lpushx(key1, ["1", "2", "3"]) == 4
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["3", "2", "1", "0"]
        )
        # key exists, but not a list
        assert await glide_client.set(key2, "bar") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.lpushx(key2, ["_"])
        # incorrect arguments
        with pytest.raises(RequestError):
            await glide_client.lpushx(key1, [])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_blpop(self, glide_client: TGlideClient):
        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        value1 = "value1"
        value2 = "value2"
        value_list: List[TEncodable] = [value1, value2]

        assert await glide_client.lpush(key1, value_list) == 2
        assert await glide_client.blpop(
            [key1, key2], 0.5
        ) == convert_string_to_bytes_object([key1, value2])
        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert await glide_client.blpop(["non_existent_key"], 0.5) is None

        # key exists, but not a list
        assert await glide_client.set("foo", "bar")
        with pytest.raises(RequestError):
            await glide_client.blpop(["foo"], 0.001)

        async def endless_blpop_call():
            await glide_client.blpop(["non_existent_key"], 0)

        # blpop is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_blpop_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lmpop(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        key3 = f"{{test}}-3-f{get_random_string(10)}"

        # Initialize the lists
        assert await glide_client.lpush(key1, ["3", "2", "1"]) == 3
        assert await glide_client.lpush(key2, ["6", "5", "4"]) == 3

        # Pop from LEFT
        result = await glide_client.lmpop([key1, key2], ListDirection.LEFT, 2)
        expected_result = {key1: ["1", "2"]}
        assert compare_maps(result, expected_result) is True

        # Pop from RIGHT
        result = await glide_client.lmpop([key2, key1], ListDirection.RIGHT, 2)
        expected_result = {key2: ["6", "5"]}
        assert compare_maps(result, expected_result) is True

        # Pop without count (default is 1)
        result = await glide_client.lmpop([key1, key2], ListDirection.LEFT)
        expected_result = {key1: ["3"]}
        assert compare_maps(result, expected_result) is True

        # Non-existing key
        result = await glide_client.lmpop([key3], ListDirection.LEFT, 1)
        assert result is None

        # Non-list key
        assert await glide_client.set(key3, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.lmpop([key3], ListDirection.LEFT, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_blmpop(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        key3 = f"{{test}}-3-f{get_random_string(10)}"
        key4 = f"{{test}}-4-f{get_random_string(10)}"

        # Initialize the lists
        assert await glide_client.lpush(key1, ["3", "2", "1"]) == 3
        assert await glide_client.lpush(key2, ["6", "5", "4"]) == 3

        # Pop from LEFT with blocking
        result = await glide_client.blmpop([key1, key2], ListDirection.LEFT, 0.1, 2)
        expected_result = {key1: ["1", "2"]}
        assert compare_maps(result, expected_result) is True

        # Pop from RIGHT with blocking
        result = await glide_client.blmpop([key2, key1], ListDirection.RIGHT, 0.1, 2)
        expected_result = {key2: ["6", "5"]}
        assert compare_maps(result, expected_result) is True

        # Pop without count (default is 1)
        result = await glide_client.blmpop([key1, key2], ListDirection.LEFT, 0.1)
        expected_result = {key1: ["3"]}
        assert compare_maps(result, expected_result) is True

        # Non-existing key with blocking
        result = await glide_client.blmpop([key3], ListDirection.LEFT, 0.1, 1)
        assert result is None

        # Non-list key with blocking
        assert await glide_client.set(key4, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.blmpop([key4], ListDirection.LEFT, 0.1, 1)

        # BLMPOP is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        async def endless_blmpop_call():
            await glide_client.blmpop([key3], ListDirection.LEFT, 0, 1)

        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_blmpop_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lindex(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value_list = [get_random_string(5), get_random_string(5)]
        assert await glide_client.lpush(key, value_list) == 2
        assert await glide_client.lindex(key, 0) == value_list[1].encode()
        assert await glide_client.lindex(key, 1) == value_list[0].encode()
        assert await glide_client.lindex(key, 3) is None
        assert await glide_client.lindex("non_existing_key", 0) is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpush_rpop(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value_list: List[TEncodable] = ["value4", "value3", "value2", "value1"]

        assert await glide_client.rpush(key, value_list) == 4
        assert await glide_client.rpop(key) == cast(str, value_list[-1]).encode()

        assert await glide_client.rpop_count(key, 2) == convert_string_to_bytes_object(
            value_list[-2:0:-1]
        )
        assert await glide_client.rpop("non_existing_key") is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpush_rpop_wrong_type_raise_error(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await glide_client.rpush(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.rpop(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rpushx(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        # new key
        assert await glide_client.rpushx(key1, ["1"]) == 0
        assert await glide_client.lrange(key1, 0, -1) == []
        # existing key
        assert await glide_client.rpush(key1, ["0"]) == 1
        assert await glide_client.rpushx(key1, ["1", "2", "3"]) == 4
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["0", "1", "2", "3"]
        )
        # key existing, but it is not a list
        assert await glide_client.set(key2, "bar") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.rpushx(key2, ["_"])
        # incorrect arguments
        with pytest.raises(RequestError):
            await glide_client.rpushx(key2, [])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_brpop(self, glide_client: TGlideClient):
        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        value1 = "value1"
        value2 = "value2"
        value_list: List[TEncodable] = [value1, value2]

        assert await glide_client.lpush(key1, value_list) == 2
        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert await glide_client.brpop(
            [key1, key2], 0.5
        ) == convert_string_to_bytes_object([key1, value1])

        assert await glide_client.brpop(["non_existent_key"], 0.5) is None

        # key exists, but not a list
        assert await glide_client.set("foo", "bar")
        with pytest.raises(RequestError):
            await glide_client.brpop(["foo"], 0.001)

        async def endless_brpop_call():
            await glide_client.brpop(["non_existent_key"], 0)

        # brpop is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_brpop_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_linsert(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)

        assert await glide_client.lpush(key1, ["4", "3", "2", "1"]) == 4
        assert await glide_client.linsert(key1, InsertPosition.BEFORE, "2", "1.5") == 5
        assert await glide_client.linsert(key1, InsertPosition.AFTER, "3", "3.5") == 6
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            [
                "1",
                "1.5",
                "2",
                "3",
                "3.5",
                "4",
            ]
        )

        assert (
            await glide_client.linsert(
                "non_existing_key", InsertPosition.BEFORE, "pivot", "elem"
            )
            == 0
        )
        assert await glide_client.linsert(key1, InsertPosition.AFTER, "5", "6") == -1

        # key exists, but it is not a list
        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.linsert(key2, InsertPosition.AFTER, "p", "e")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lmove(self, glide_client: TGlideClient):
        key1 = "{SameSlot}" + get_random_string(10)
        key2 = "{SameSlot}" + get_random_string(10)

        # Initialize the lists
        assert await glide_client.lpush(key1, ["2", "1"]) == 2
        assert await glide_client.lpush(key2, ["4", "3"]) == 2

        # Move from LEFT to LEFT
        assert (
            await glide_client.lmove(key1, key2, ListDirection.LEFT, ListDirection.LEFT)
            == b"1"
        )
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["2"]
        )
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3", "4"]
        )

        # Move from LEFT to RIGHT
        assert (
            await glide_client.lmove(
                key1, key2, ListDirection.LEFT, ListDirection.RIGHT
            )
            == b"2"
        )
        assert await glide_client.lrange(key1, 0, -1) == []
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3", "4", "2"]
        )

        # Move from RIGHT to LEFT - non-existing destination key
        assert (
            await glide_client.lmove(
                key2, key1, ListDirection.RIGHT, ListDirection.LEFT
            )
            == b"2"
        )
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3", "4"]
        )
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["2"]
        )

        # Move from RIGHT to RIGHT
        assert (
            await glide_client.lmove(
                key2, key1, ListDirection.RIGHT, ListDirection.RIGHT
            )
            == b"4"
        )
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3"]
        )
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["2", "4"]
        )

        # Non-existing source key
        assert (
            await glide_client.lmove(
                "{SameSlot}non_existing_key",
                key1,
                ListDirection.LEFT,
                ListDirection.LEFT,
            )
            is None
        )

        # Non-list source key
        key3 = get_random_string(10)
        assert await glide_client.set(key3, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.lmove(key3, key1, ListDirection.LEFT, ListDirection.LEFT)

        # Non-list destination key
        with pytest.raises(RequestError):
            await glide_client.lmove(key1, key3, ListDirection.LEFT, ListDirection.LEFT)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_blmove(self, glide_client: TGlideClient):
        key1 = "{SameSlot}" + get_random_string(10)
        key2 = "{SameSlot}" + get_random_string(10)

        # Initialize the lists
        assert await glide_client.lpush(key1, ["2", "1"]) == 2
        assert await glide_client.lpush(key2, ["4", "3"]) == 2

        # Move from LEFT to LEFT with blocking
        assert (
            await glide_client.blmove(
                key1, key2, ListDirection.LEFT, ListDirection.LEFT, 0.1
            )
            == b"1"
        )
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["2"]
        )
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3", "4"]
        )

        # Move from LEFT to RIGHT with blocking
        assert (
            await glide_client.blmove(
                key1, key2, ListDirection.LEFT, ListDirection.RIGHT, 0.1
            )
            == b"2"
        )
        assert await glide_client.lrange(key1, 0, -1) == []
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3", "4", "2"]
        )

        # Move from RIGHT to LEFT non-existing destination with blocking
        assert (
            await glide_client.blmove(
                key2, key1, ListDirection.RIGHT, ListDirection.LEFT, 0.1
            )
            == b"2"
        )
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3", "4"]
        )
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["2"]
        )

        # Move from RIGHT to RIGHT with blocking
        assert (
            await glide_client.blmove(
                key2, key1, ListDirection.RIGHT, ListDirection.RIGHT, 0.1
            )
            == b"4"
        )
        assert await glide_client.lrange(key2, 0, -1) == convert_string_to_bytes_object(
            ["1", "3"]
        )
        assert await glide_client.lrange(key1, 0, -1) == convert_string_to_bytes_object(
            ["2", "4"]
        )

        # Non-existing source key with blocking
        assert (
            await glide_client.blmove(
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
        assert await glide_client.set(key3, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.blmove(
                key3, key1, ListDirection.LEFT, ListDirection.LEFT, 0.1
            )

        # Non-list destination key with blocking
        with pytest.raises(RequestError):
            await glide_client.blmove(
                key1, key3, ListDirection.LEFT, ListDirection.LEFT, 0.1
            )

        # BLMOVE is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        async def endless_blmove_call():
            await glide_client.blmove(
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
    async def test_lset(self, glide_client: TGlideClient):
        key = get_random_string(10)
        element = get_random_string(5)
        values = [get_random_string(5) for _ in range(4)]

        # key does not exist
        with pytest.raises(RequestError):
            await glide_client.lset("non_existing_key", 0, element)

        # pushing elements to list
        await glide_client.lpush(key, values) == 4

        # index out of range
        with pytest.raises(RequestError):
            await glide_client.lset(key, 10, element)

        # assert lset result
        assert await glide_client.lset(key, 0, element) == OK

        values = [element] + values[:-1][::-1]
        assert await glide_client.lrange(key, 0, -1) == convert_string_to_bytes_object(
            values
        )

        # assert lset with a negative index for the last element in the list
        assert await glide_client.lset(key, -1, element) == OK

        values[-1] = element
        assert await glide_client.lrange(key, 0, -1) == convert_string_to_bytes_object(
            values
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value_list: List[TEncodable] = ["member1", "member2", "member3", "member4"]

        assert await glide_client.sadd(key, value_list) == 4
        assert await glide_client.srem(key, ["member4", "nonExistingMember"]) == 1

        assert set(await glide_client.smembers(key)) == set(
            cast(list, convert_string_to_bytes_object(value_list[:3]))
        )

        assert await glide_client.srem(key, ["member1"]) == 1
        assert await glide_client.scard(key) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard_non_existing_key(
        self, glide_client: TGlideClient
    ):
        non_existing_key = get_random_string(10)
        assert await glide_client.srem(non_existing_key, ["member"]) == 0
        assert await glide_client.scard(non_existing_key) == 0
        assert await glide_client.smembers(non_existing_key) == set()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sadd_srem_smembers_scard_wrong_type_raise_error(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK

        with pytest.raises(RequestError) as e:
            await glide_client.sadd(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.srem(key, ["bar"])
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.scard(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.smembers(key)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sismember(self, glide_client: TGlideClient):
        key = get_random_string(10)
        member = get_random_string(5)
        assert await glide_client.sadd(key, [member]) == 1
        assert await glide_client.sismember(key, member)
        assert not await glide_client.sismember(key, get_random_string(5))
        assert not await glide_client.sismember("non_existing_key", member)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_spop(self, glide_client: TGlideClient):
        key = get_random_string(10)
        member = get_random_string(5)
        assert await glide_client.sadd(key, [member]) == 1
        assert await glide_client.spop(key) == member.encode()

        member2 = get_random_string(5)
        member3 = get_random_string(5)
        assert await glide_client.sadd(key, [member, member2, member3]) == 3
        assert await glide_client.spop_count(key, 4) == convert_string_to_bytes_object(
            {member, member2, member3}
        )

        assert await glide_client.scard(key) == 0

        assert await glide_client.spop("non_existing_key") == None
        assert await glide_client.spop_count("non_existing_key", 3) == set()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_smove(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await glide_client.sadd(key1, ["1", "2", "3"]) == 3
        assert await glide_client.sadd(key2, ["2", "3"]) == 2

        # move an element
        assert await glide_client.smove(key1, key2, "1") is True
        assert await glide_client.smembers(key1) == convert_string_to_bytes_object(
            {"2", "3"}
        )
        assert await glide_client.smembers(key2) == convert_string_to_bytes_object(
            {"1", "2", "3"}
        )

        # moved element already exists in the destination set
        assert await glide_client.smove(key2, key1, "2") is True
        assert await glide_client.smembers(key1) == convert_string_to_bytes_object(
            {"2", "3"}
        )
        assert await glide_client.smembers(key2) == convert_string_to_bytes_object(
            {"1", "3"}
        )

        # attempt to move from a non-existing key
        assert await glide_client.smove(non_existing_key, key1, "4") is False
        assert await glide_client.smembers(key1) == convert_string_to_bytes_object(
            {"2", "3"}
        )

        # move to a new set
        assert await glide_client.smove(key1, key3, "2")
        assert await glide_client.smembers(key1) == {b"3"}
        assert await glide_client.smembers(key3) == {b"2"}

        # attempt to move a missing element
        assert await glide_client.smove(key1, key3, "42") is False
        assert await glide_client.smembers(key1) == {b"3"}
        assert await glide_client.smembers(key3) == {b"2"}

        # move missing element to missing key
        assert await glide_client.smove(key1, non_existing_key, "42") is False
        assert await glide_client.smembers(key1) == {b"3"}
        assert await glide_client.type(non_existing_key) == b"none"

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.smove(string_key, key1, "_")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sunion(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list: List[TEncodable] = ["a", "b", "c"]
        member2_list: List[TEncodable] = ["b", "c", "d", "e"]

        assert await glide_client.sadd(key1, member1_list) == 3
        assert await glide_client.sadd(key2, member2_list) == 4
        assert await glide_client.sunion([key1, key2]) == {b"a", b"b", b"c", b"d", b"e"}

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.sunion([])

        # non-existing key returns the set of existing keys
        assert await glide_client.sunion(
            [key1, non_existing_key]
        ) == convert_string_to_bytes_object(set(cast(List[str], member1_list)))

        # non-set key
        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.sunion([key2])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sunionstore(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        key4 = f"{{testKey}}:4-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await glide_client.sadd(key1, ["a", "b", "c"]) == 3
        assert await glide_client.sadd(key2, ["c", "d", "e"]) == 3
        assert await glide_client.sadd(key3, ["e", "f", "g"]) == 3

        # store union in new key
        assert await glide_client.sunionstore(key4, [key1, key2]) == 5
        assert await glide_client.smembers(key4) == convert_string_to_bytes_object(
            {"a", "b", "c", "d", "e"}
        )

        # overwrite existing set
        assert await glide_client.sunionstore(key1, [key4, key2]) == 5
        assert await glide_client.smembers(key1) == convert_string_to_bytes_object(
            {"a", "b", "c", "d", "e"}
        )

        # overwrite one of the source keys
        assert await glide_client.sunionstore(key2, [key4, key2]) == 5
        assert await glide_client.smembers(key1) == convert_string_to_bytes_object(
            {"a", "b", "c", "d", "e"}
        )

        # union with a non existing key
        assert await glide_client.sunionstore(key2, [non_existing_key]) == 0
        assert await glide_client.smembers(key2) == set()

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.sunionstore(key4, [string_key, key1])

        # overwrite destination when destination is not a set
        assert await glide_client.sunionstore(string_key, [key1, key3]) == 7
        assert await glide_client.smembers(
            string_key
        ) == convert_string_to_bytes_object(
            {
                "a",
                "b",
                "c",
                "d",
                "e",
                "f",
                "g",
            }
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sinter(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list: List[TEncodable] = ["a", "b", "c"]
        member2_list: List[TEncodable] = ["c", "d", "e"]

        # positive test case
        assert await glide_client.sadd(key1, member1_list) == 3
        assert await glide_client.sadd(key2, member2_list) == 3
        assert await glide_client.sinter([key1, key2]) == {b"c"}

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.sinter([])

        # non-existing key returns empty set
        assert await glide_client.sinter([key1, non_existing_key]) == set()

        # non-set key
        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.sinter([key2])
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sinterstore(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        key3 = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list: List[TEncodable] = ["a", "b", "c"]
        member2_list: List[TEncodable] = ["c", "d", "e"]

        assert await glide_client.sadd(key1, member1_list) == 3
        assert await glide_client.sadd(key2, member2_list) == 3

        # store in new key
        assert await glide_client.sinterstore(key3, [key1, key2]) == 1
        assert await glide_client.smembers(key3) == {b"c"}

        # overwrite existing set, which is also a source set
        assert await glide_client.sinterstore(key2, [key2, key3]) == 1
        assert await glide_client.smembers(key2) == {b"c"}

        # source set is the same as the existing set
        assert await glide_client.sinterstore(key2, [key2]) == 1
        assert await glide_client.smembers(key2) == {b"c"}

        # intersection with non-existing key
        assert await glide_client.sinterstore(key1, [key2, non_existing_key]) == 0
        assert await glide_client.smembers(key1) == set()

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.sinterstore(key3, [])

        # non-set key
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.sinterstore(key3, [string_key])

        # overwrite non-set key
        assert await glide_client.sinterstore(string_key, [key2]) == 1
        assert await glide_client.smembers(string_key) == {b"c"}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sintercard(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        key3 = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"
        member1_list: List[TEncodable] = ["a", "b", "c"]
        member2_list: List[TEncodable] = ["b", "c", "d", "e"]
        member3_list: List[TEncodable] = ["b", "c", "f", "g"]

        assert await glide_client.sadd(key1, member1_list) == 3
        assert await glide_client.sadd(key2, member2_list) == 4
        assert await glide_client.sadd(key3, member3_list) == 4

        # Basic intersection
        assert (
            await glide_client.sintercard([key1, key2]) == 2
        )  # Intersection of key1 and key2 is {"b", "c"}

        # Intersection with non-existing key
        assert (
            await glide_client.sintercard([key1, non_existing_key]) == 0
        )  # No common elements

        # Intersection with a single key
        assert await glide_client.sintercard([key1]) == 3  # All elements in key1

        # Intersection with limit
        assert (
            await glide_client.sintercard([key1, key2, key3], limit=1) == 1
        )  # Stops early at limit

        # Invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.sintercard([])

        # Non-set key
        assert await glide_client.set(string_key, "value") == "OK"
        with pytest.raises(RequestError):
            await glide_client.sintercard([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sdiff(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await glide_client.sadd(key1, ["a", "b", "c"]) == 3
        assert await glide_client.sadd(key2, ["c", "d", "e"]) == 3

        assert await glide_client.sdiff([key1, key2]) == convert_string_to_bytes_object(
            {"a", "b"}
        )
        assert await glide_client.sdiff([key2, key1]) == convert_string_to_bytes_object(
            {"d", "e"}
        )

        assert await glide_client.sdiff(
            [key1, non_existing_key]
        ) == convert_string_to_bytes_object({"a", "b", "c"})
        assert await glide_client.sdiff([non_existing_key, key1]) == set()

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.sdiff([])

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.sdiff([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sdiffstore(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await glide_client.sadd(key1, ["a", "b", "c"]) == 3
        assert await glide_client.sadd(key2, ["c", "d", "e"]) == 3

        # Store diff in new key
        assert await glide_client.sdiffstore(key3, [key1, key2]) == 2
        assert await glide_client.smembers(key3) == convert_string_to_bytes_object(
            {"a", "b"}
        )

        # Overwrite existing set
        assert await glide_client.sdiffstore(key3, [key2, key1]) == 2
        assert await glide_client.smembers(key3) == convert_string_to_bytes_object(
            {"d", "e"}
        )

        # Overwrite one of the source sets
        assert await glide_client.sdiffstore(key3, [key2, key3]) == 1
        assert await glide_client.smembers(key3) == {b"c"}

        # Diff between non-empty set and empty set
        assert await glide_client.sdiffstore(key3, [key1, non_existing_key]) == 3
        assert await glide_client.smembers(key3) == convert_string_to_bytes_object(
            {"a", "b", "c"}
        )

        # Diff between empty set and non-empty set
        assert await glide_client.sdiffstore(key3, [non_existing_key, key1]) == 0
        assert await glide_client.smembers(key3) == set()

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.sdiffstore(key3, [])

        # source key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.sdiffstore(key3, [string_key])

        # Overwrite a key holding a non-set value
        assert await glide_client.sdiffstore(string_key, [key1, key2]) == 2
        assert await glide_client.smembers(
            string_key
        ) == convert_string_to_bytes_object({"a", "b"})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_smismember(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await glide_client.sadd(key1, ["one", "two"]) == 2
        assert await glide_client.smismember(key1, ["two", "three"]) == [True, False]

        assert await glide_client.smismember(non_existing_key, ["two"]) == [False]

        # invalid argument - member list must not be empty
        with pytest.raises(RequestError):
            await glide_client.smismember(key1, [])

        # source key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.smismember(string_key, ["two"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_ltrim(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value_list: List[TEncodable] = ["value4", "value3", "value2", "value1"]

        assert await glide_client.lpush(key, value_list) == 4
        assert await glide_client.ltrim(key, 0, 1) == OK
        assert await glide_client.lrange(key, 0, -1) == convert_string_to_bytes_object(
            ["value1", "value2"]
        )

        assert await glide_client.ltrim(key, 4, 2) == OK
        assert await glide_client.lrange(key, 0, -1) == []

        assert await glide_client.ltrim("non_existing_key", 0, 1) == OK

        assert await glide_client.set(key, "foo") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.ltrim(key, 0, 1)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lrem(self, glide_client: TGlideClient):
        key = get_random_string(10)
        value_list: List[TEncodable] = [
            "value1",
            "value2",
            "value1",
            "value1",
            "value2",
        ]

        assert await glide_client.lpush(key, value_list) == 5

        assert await glide_client.lrem(key, 2, "value1") == 2
        assert await glide_client.lrange(key, 0, -1) == convert_string_to_bytes_object(
            ["value2", "value2", "value1"]
        )

        assert await glide_client.lrem(key, -1, "value2") == 1
        assert await glide_client.lrange(key, 0, -1) == convert_string_to_bytes_object(
            ["value2", "value1"]
        )

        assert await glide_client.lrem(key, 0, "value2") == 1
        assert await glide_client.lrange(key, 0, -1) == [b"value1"]

        assert await glide_client.lrem("non_existing_key", 2, "value") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_llen(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        value_list: List[TEncodable] = ["value4", "value3", "value2", "value1"]

        assert await glide_client.lpush(key1, value_list) == 4
        assert await glide_client.llen(key1) == 4

        assert await glide_client.llen("non_existing_key") == 0

        assert await glide_client.set(key2, "foo") == OK
        with pytest.raises(RequestError) as e:
            await glide_client.llen(key2)
        assert "Operation against a key holding the wrong kind of value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_strlen(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        value_list: List[TEncodable] = ["value4", "value3", "value2", "value1"]

        assert await glide_client.set(key1, "foo") == OK
        assert await glide_client.strlen(key1) == 3
        assert await glide_client.strlen("non_existing_key") == 0

        assert await glide_client.lpush(key2, value_list) == 4
        with pytest.raises(RequestError):
            assert await glide_client.strlen(key2)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_rename(self, glide_client: TGlideClient):
        key1 = "{" + get_random_string(10) + "}"
        assert await glide_client.set(key1, "foo") == OK
        assert await glide_client.rename(key1, key1 + "_rename") == OK
        assert await glide_client.exists([key1 + "_rename"]) == 1

        with pytest.raises(RequestError):
            assert await glide_client.rename(
                "{same_slot}" + "non_existing_key", "{same_slot}" + "_rename"
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_renamenx(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        # Verify that attempting to rename a non-existing key throws an error
        with pytest.raises(RequestError):
            assert await glide_client.renamenx(non_existing_key, key1)

        # Test RENAMENX with string values
        assert await glide_client.set(key1, "key1") == OK
        assert await glide_client.set(key3, "key3") == OK
        # Test that RENAMENX can rename key1 to key2 (where key2 does not yet exist)
        assert await glide_client.renamenx(key1, key2) is True
        # Verify that key2 now holds the value that was in key1
        assert await glide_client.get(key2) == b"key1"
        # Verify that RENAMENX doesn't rename key2 to key3, since key3 already exists
        assert await glide_client.renamenx(key2, key3) is False
        # Verify that key3 remains unchanged
        assert await glide_client.get(key3) == b"key3"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_exists(self, glide_client: TGlideClient):
        keys = [get_random_string(10), get_random_string(10)]

        assert await glide_client.set(keys[0], "value") == OK
        assert await glide_client.exists(keys) == 1

        assert await glide_client.set(keys[1], "value") == OK
        assert await glide_client.exists(keys) == 2
        keys.append("non_existing_key")
        assert await glide_client.exists(keys) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_unlink(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        key3 = get_random_string(10)

        assert await glide_client.set(key1, "value") == OK
        assert await glide_client.set(key2, "value") == OK
        assert await glide_client.set(key3, "value") == OK
        assert await glide_client.unlink([key1, key2, "non_existing_key", key3]) == 3

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_ttl_expiretime_pexpiretime_with_positive_timeout(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK
        assert await glide_client.ttl(key) == -1

        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -1
            assert await glide_client.pexpiretime(key) == -1

        assert await glide_client.expire(key, 10) == 1
        assert await glide_client.ttl(key) in range(11)

        # set command clears the timeout.
        assert await glide_client.set(key, "bar") == OK
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.pexpire(key, 10000)
        else:
            assert await glide_client.pexpire(key, 10000, ExpireOptions.HasNoExpiry)
        assert await glide_client.ttl(key) in range(11)

        if await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expire(key, 15)
        else:
            assert await glide_client.expire(key, 15, ExpireOptions.HasExistingExpiry)
            assert await glide_client.expiretime(key) > int(time.time())
            assert await glide_client.pexpiretime(key) > (int(time.time()) * 1000)
        assert await glide_client.ttl(key) in range(16)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expireat_pexpireat_ttl_with_positive_timeout(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK
        current_time = int(time.time())

        assert await glide_client.expireat(key, current_time + 10) == 1
        assert await glide_client.ttl(key) in range(11)
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expireat(key, current_time + 50) == 1
        else:
            assert (
                await glide_client.expireat(
                    key, current_time + 50, ExpireOptions.NewExpiryGreaterThanCurrent
                )
                == 1
            )
        assert await glide_client.ttl(key) in range(51)

        # set command clears the timeout.
        assert await glide_client.set(key, "bar") == OK
        current_time_ms = int(time.time() * 1000)
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert not await glide_client.pexpireat(
                key, current_time_ms + 50000, ExpireOptions.HasExistingExpiry
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_expireat_pexpireat_expiretime_pexpiretime_past_or_negative_timeout(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        assert await glide_client.set(key, "foo") == OK
        assert await glide_client.ttl(key) == -1

        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -1
            assert await glide_client.pexpiretime(key) == -1

        assert await glide_client.expire(key, -10) is True
        assert await glide_client.ttl(key) == -2
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -2
            assert await glide_client.pexpiretime(key) == -2

        assert await glide_client.set(key, "foo") == OK
        assert await glide_client.pexpire(key, -10000)
        assert await glide_client.ttl(key) == -2
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -2
            assert await glide_client.pexpiretime(key) == -2

        assert await glide_client.set(key, "foo") == OK
        assert await glide_client.expireat(key, int(time.time()) - 50) == 1
        assert await glide_client.ttl(key) == -2
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -2
            assert await glide_client.pexpiretime(key) == -2

        assert await glide_client.set(key, "foo") == OK
        assert await glide_client.pexpireat(key, int(time.time() * 1000) - 50000)
        assert await glide_client.ttl(key) == -2
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -2
            assert await glide_client.pexpiretime(key) == -2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_expire_pexpire_expireAt_pexpireAt_ttl_expiretime_pexpiretime_non_existing_key(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)

        assert await glide_client.expire(key, 10) == 0
        assert not await glide_client.pexpire(key, 10000)
        assert await glide_client.expireat(key, int(time.time()) + 50) == 0
        assert not await glide_client.pexpireat(key, int(time.time() * 1000) + 50000)
        assert await glide_client.ttl(key) == -2
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.expiretime(key) == -2
            assert await glide_client.pexpiretime(key) == -2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pttl(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.pttl(key) == -2
        current_time = int(time.time())

        assert await glide_client.set(key, "value") == OK
        assert await glide_client.pttl(key) == -1

        assert await glide_client.expire(key, 10)
        assert 0 < await glide_client.pttl(key) <= 10000

        assert await glide_client.expireat(key, current_time + 20)
        assert 0 < await glide_client.pttl(key) <= 20000

        assert await glide_client.pexpireat(key, current_time * 1000 + 30000)
        assert 0 < await glide_client.pttl(key) <= 30000

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_persist(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "value") == OK
        assert not await glide_client.persist(key)

        assert await glide_client.expire(key, 10)
        assert await glide_client.persist(key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geoadd(self, glide_client: TGlideClient):
        key, key2 = get_random_string(10), get_random_string(10)
        members_coordinates: Dict[str | bytes, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await glide_client.geoadd(key, members_coordinates) == 2
        members_coordinates["Catania"].latitude = 39
        assert (
            await glide_client.geoadd(
                key,
                members_coordinates,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == 0
        )
        assert (
            await glide_client.geoadd(
                key,
                members_coordinates,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 0
        )
        members_coordinates["Catania"].latitude = 40
        members_coordinates.update({"Tel-Aviv": GeospatialData(32.0853, 34.7818)})
        assert (
            await glide_client.geoadd(
                key,
                members_coordinates,
                changed=True,
            )
            == 2
        )

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.geoadd(key2, members_coordinates)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geoadd_invalid_args(self, glide_client: TGlideClient):
        key = get_random_string(10)

        with pytest.raises(RequestError):
            await glide_client.geoadd(key, {})

        with pytest.raises(RequestError):
            await glide_client.geoadd(key, {"Place": GeospatialData(-181, 0)})

        with pytest.raises(RequestError):
            await glide_client.geoadd(key, {"Place": GeospatialData(181, 0)})

        with pytest.raises(RequestError):
            await glide_client.geoadd(key, {"Place": GeospatialData(0, 86)})

        with pytest.raises(RequestError):
            await glide_client.geoadd(key, {"Place": GeospatialData(0, -86)})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearch_by_box(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members = ["Catania", "Palermo", "edge2", "edge1"]
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
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
        assert await glide_client.geoadd(key, members_coordinates) == 4

        # Test search by box, unit: kilometers, from a geospatial data
        assert await glide_client.geosearch(
            key,
            GeospatialData(15, 37),
            GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
            OrderBy.ASC,
        ) == convert_string_to_bytes_object(members)

        assert await glide_client.geosearch(
            key,
            GeospatialData(15, 37),
            GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
            OrderBy.DESC,
            with_coord=True,
            with_dist=True,
            with_hash=True,
        ) == convert_string_to_bytes_object(result[::-1])

        assert await glide_client.geosearch(
            key,
            GeospatialData(15, 37),
            GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
            OrderBy.ASC,
            count=GeoSearchCount(1),
            with_dist=True,
            with_hash=True,
        ) == [[b"Catania", [56.4413, 3479447370796909]]]

        # Test search by box, unit: meters, from a member, with distance
        meters = 400 * 1000
        assert await glide_client.geosearch(
            key,
            "Catania",
            GeoSearchByBox(meters, meters, GeoUnit.METERS),
            OrderBy.DESC,
            with_dist=True,
        ) == convert_string_to_bytes_object(
            [["edge2", [236529.1799]], ["Palermo", [166274.1516]], ["Catania", [0.0]]]
        )

        # Test search by box, unit: feet, from a member, with limited count to 2, with hash
        feet = 400 * 3280.8399
        assert await glide_client.geosearch(
            key,
            "Palermo",
            GeoSearchByBox(feet, feet, GeoUnit.FEET),
            OrderBy.ASC,
            count=GeoSearchCount(2),
            with_hash=True,
        ) == [[b"Palermo", [3479099956230698]], [b"edge1", [3479273021651468]]]

        # Test search by box, unit: miles, from a geospatial data, with limited ANY count to 1
        assert (
            await glide_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(250, 250, GeoUnit.MILES),
                OrderBy.ASC,
                count=GeoSearchCount(1, True),
            )
        )[0] in cast(list, convert_string_to_bytes_object(members))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearch_by_radius(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
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
        assert await glide_client.geoadd(key, members_coordinates) == 4

        # Test search by radius, units: feet, from a member
        feet = 200 * 3280.8399
        assert await glide_client.geosearch(
            key,
            "Catania",
            GeoSearchByRadius(feet, GeoUnit.FEET),
            OrderBy.ASC,
        ) == convert_string_to_bytes_object(members[:2])

        # Test search by radius, units: meters, from a member
        meters = 200 * 1000
        assert await glide_client.geosearch(
            key,
            "Catania",
            GeoSearchByRadius(meters, GeoUnit.METERS),
            OrderBy.DESC,
        ) == convert_string_to_bytes_object(members[:2][::-1])

        # Test search by radius, unit: miles, from a geospatial data
        assert await glide_client.geosearch(
            key,
            GeospatialData(15, 37),
            GeoSearchByRadius(175, GeoUnit.MILES),
            OrderBy.DESC,
        ) == convert_string_to_bytes_object(members[::-1])

        # Test search by radius, unit: kilometers, from a geospatial data, with limited count to 2
        assert await glide_client.geosearch(
            key,
            GeospatialData(15, 37),
            GeoSearchByRadius(200, GeoUnit.KILOMETERS),
            OrderBy.ASC,
            count=GeoSearchCount(2),
            with_coord=True,
            with_dist=True,
            with_hash=True,
        ) == convert_string_to_bytes_object(result)

        # Test search by radius, unit: kilometers, from a geospatial data, with limited ANY count to 1
        assert (
            await glide_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(200, GeoUnit.KILOMETERS),
                OrderBy.ASC,
                count=GeoSearchCount(1, True),
                with_coord=True,
                with_dist=True,
                with_hash=True,
            )
        )[0] in cast(list, convert_string_to_bytes_object(result))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearch_no_result(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        assert await glide_client.geoadd(key, members_coordinates) == 4

        # No membes within the aea
        assert (
            await glide_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(50, 50, GeoUnit.METERS),
                OrderBy.ASC,
            )
            == []
        )

        assert (
            await glide_client.geosearch(
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(10, GeoUnit.METERS),
                OrderBy.ASC,
            )
            == []
        )

        # No members in the area (apart from the member we seach fom itself)
        assert await glide_client.geosearch(
            key,
            "Catania",
            GeoSearchByBox(10, 10, GeoUnit.KILOMETERS),
        ) == [b"Catania"]

        assert await glide_client.geosearch(
            key,
            "Catania",
            GeoSearchByRadius(10, GeoUnit.METERS),
        ) == [b"Catania"]

        # Search from non exiting memeber
        with pytest.raises(RequestError):
            await glide_client.geosearch(
                key,
                "non_existing_member",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

        assert await glide_client.set(key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.geosearch(
                key,
                "Catania",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearchstore_by_box(self, glide_client: TGlideClient):
        key = f"{{testKey}}:{get_random_string(10)}"
        destination_key = f"{{testKey}}:{get_random_string(8)}"
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        result = {
            b"Catania": [56.4412578701582, 3479447370796909.0],
            b"Palermo": [190.44242984775784, 3479099956230698.0],
            b"edge2": [279.7403417843143, 3481342659049484.0],
            b"edge1": [279.7404521356343, 3479273021651468.0],
        }
        assert await glide_client.geoadd(key, members_coordinates) == 4

        # Test storing results of a box search, unit: kilometes, from a geospatial data
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
            )
        ) == 4  # Number of elements stored

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[1] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test storing results of a box search, unit: kilometes, from a geospatial data, with distance
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                store_dist=True,
            )
        ) == 4  # Number of elements stored

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[0] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test storing results of a box search, unit: kilometes, from a geospatial data, with count
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                count=GeoSearchCount(1),
            )
        ) == 1  # Number of elements stored

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_map, {b"Catania": 3479447370796909.0}) is True

        # Test storing results of a box search, unit: meters, from a member, with distance
        meters = 400 * 1000
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByBox(meters, meters, GeoUnit.METERS),
                store_dist=True,
            )
        ) == 3  # Number of elements stored

        # Verify the stored results with distances
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_distances = {
            b"Catania": 0.0,
            b"Palermo": 166274.15156960033,
            b"edge2": 236529.17986494553,
        }
        assert compare_maps(zrange_map, expected_distances) is True

        # Test search by box, unit: feet, from a member, with limited ANY count to 2, with hash
        feet = 400 * 3280.8399
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Palermo",
                GeoSearchByBox(feet, feet, GeoUnit.FEET),
                count=GeoSearchCount(2),
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        for member in zrange_map:
            assert member in result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearchstore_by_radius(self, glide_client: TGlideClient):
        key = f"{{testKey}}:{get_random_string(10)}"
        destination_key = f"{{testKey}}:{get_random_string(8)}"
        # Checking when parts of the value contain bytes
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            b"Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            b"edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        result = {
            b"Catania": [56.4412578701582, 3479447370796909.0],
            b"Palermo": [190.44242984775784, 3479099956230698.0],
        }
        assert await glide_client.geoadd(key, members_coordinates) == 4

        # Test storing results of a radius search, unit: feet, from a member
        feet = 200 * 3280.8399
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByRadius(feet, GeoUnit.FEET),
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[1] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test search by radius, units: meters, from a member
        meters = 200 * 1000
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByRadius(meters, GeoUnit.METERS),
                store_dist=True,
            )
            == 2
        )

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_distances = {
            b"Catania": 0.0,
            b"Palermo": 166274.15156960033,
        }
        assert compare_maps(zrange_map, expected_distances) is True

        # Test search by radius, unit: miles, from a geospatial data
        assert (
            await glide_client.geosearchstore(
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
            await glide_client.geosearchstore(
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
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )
        expected_map = {member: value[0] for member, value in result.items()}
        sorted_expected_map = dict(sorted(expected_map.items(), key=lambda x: x[1]))
        assert compare_maps(zrange_map, sorted_expected_map) is True

        # Test storing results of a radius search, unit: kilometers, from a geospatial data, with limited ANY count to 1
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(kilometers, GeoUnit.KILOMETERS),
                count=GeoSearchCount(1, True),
            )
            == 1
        )

        # Verify the stored results
        zrange_map = await glide_client.zrange_withscores(
            destination_key, RangeByIndex(0, -1)
        )

        for member in zrange_map:
            assert member in result

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geosearchstore_no_result(self, glide_client: TGlideClient):
        key = f"{{testKey}}:{get_random_string(10)}"
        destination_key = f"{{testKey}}:{get_random_string(8)}"
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
            "edge1": GeospatialData(12.758489, 38.788135),
            "edge2": GeospatialData(17.241510, 38.788135),
        }
        assert await glide_client.geoadd(key, members_coordinates) == 4

        # No members within the area
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByBox(50, 50, GeoUnit.METERS),
            )
            == 0
        )

        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                GeospatialData(15, 37),
                GeoSearchByRadius(10, GeoUnit.METERS),
            )
            == 0
        )

        # No members in the area (apart from the member we search from itself)
        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByBox(10, 10, GeoUnit.KILOMETERS),
            )
            == 1
        )

        assert (
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByRadius(10, GeoUnit.METERS),
            )
            == 1
        )

        # Search from non-existing member
        with pytest.raises(RequestError):
            await glide_client.geosearchstore(
                destination_key,
                key,
                "non_existing_member",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

        assert await glide_client.set(key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.geosearchstore(
                destination_key,
                key,
                "Catania",
                GeoSearchByBox(10, 10, GeoUnit.MILES),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geohash(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await glide_client.geoadd(key, members_coordinates) == 2
        assert await glide_client.geohash(
            key, ["Palermo", "Catania", "Place"]
        ) == convert_string_to_bytes_object(
            [
                "sqc8b49rny0",
                "sqdtr74hyu0",
                None,
            ]
        )

        assert (
            await glide_client.geohash(
                "non_existing_key", ["Palermo", "Catania", "Place"]
            )
            == [None] * 3
        )

        # Neccessary to check since we are enforcing the user to pass a list of members while valkey don't
        # But when running the command with key only (and no members) the returned value will always be an empty list
        # So in case of any changes, this test will fail and inform us that we should allow not passing any members.
        assert await glide_client.geohash(key, []) == []

        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.geohash(key, ["Palermo", "Catania"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geodist(self, glide_client: TGlideClient):
        key, key2 = get_random_string(10), get_random_string(10)
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await glide_client.geoadd(key, members_coordinates) == 2

        assert await glide_client.geodist(key, "Palermo", "Catania") == 166274.1516
        assert (
            await glide_client.geodist(key, "Palermo", "Catania", GeoUnit.KILOMETERS)
            == 166.2742
        )
        assert await glide_client.geodist(key, "Palermo", "Palermo", GeoUnit.MILES) == 0
        assert (
            await glide_client.geodist(
                key, "Palermo", "non-existing-member", GeoUnit.FEET
            )
            == None
        )

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.geodist(key2, "Palmero", "Catania")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_geopos(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_coordinates: Mapping[TEncodable, GeospatialData] = {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        }
        assert await glide_client.geoadd(key, members_coordinates) == 2

        # The comparison allows for a small tolerance level due to potential precision errors in floating-point calculations
        # No worries, Python can handle it, therefore, this shouldn't fail
        positions = await glide_client.geopos(key, ["Palermo", "Catania", "Place"])
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
            await glide_client.geopos(
                "non_existing_key", ["Palermo", "Catania", "Place"]
            )
            == [None] * 3
        )

        # Neccessary to check since we are enforcing the user to pass a list of members while valkey don't
        # But when running the command with key only (and no members) the returned value will always be an empty list
        # So in case of any changes, this test will fail and inform us that we should allow not passing any members.
        assert await glide_client.geohash(key, []) == []

        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.geopos(key, ["Palermo", "Catania"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_zaddincr(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3
        assert await glide_client.zadd_incr(key, member="one", increment=2) == 3.0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_nx_xx(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert (
            await glide_client.zadd(
                key,
                members_scores=members_scores,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 0
        )
        assert (
            await glide_client.zadd(
                key,
                members_scores=members_scores,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            == 3
        )

        assert (
            await glide_client.zadd_incr(
                key,
                member="one",
                increment=5.0,
                existing_options=ConditionalChange.ONLY_IF_DOES_NOT_EXIST,
            )
            is None
        )

        assert (
            await glide_client.zadd_incr(
                key,
                member="one",
                increment=5.0,
                existing_options=ConditionalChange.ONLY_IF_EXISTS,
            )
            == 6.0
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zadd_gt_lt(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Dict[TEncodable, float] = {"one": -3, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3
        members_scores["one"] = 10
        assert (
            await glide_client.zadd(
                key,
                members_scores=members_scores,
                update_condition=UpdateOptions.GREATER_THAN,
                changed=True,
            )
            == 1
        )

        assert (
            await glide_client.zadd(
                key,
                members_scores=members_scores,
                update_condition=UpdateOptions.LESS_THAN,
                changed=True,
            )
            == 0
        )

        assert (
            await glide_client.zadd_incr(
                key,
                member="one",
                increment=-3.0,
                update_condition=UpdateOptions.LESS_THAN,
            )
            == 7.0
        )

        assert (
            await glide_client.zadd_incr(
                key,
                member="one",
                increment=-3.0,
                update_condition=UpdateOptions.GREATER_THAN,
            )
            is None
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zincrby(self, glide_client: TGlideClient):
        key, member, member2 = (
            get_random_string(10),
            get_random_string(5),
            get_random_string(5),
        )

        # key does not exist
        assert await glide_client.zincrby(key, 2.5, member) == 2.5
        assert await glide_client.zscore(key, member) == 2.5

        # key exists, but value doesn't
        assert await glide_client.zincrby(key, -3.3, member2) == -3.3
        assert await glide_client.zscore(key, member2) == -3.3

        # updating existing value in existing key
        assert await glide_client.zincrby(key, 1.0, member) == 3.5
        assert await glide_client.zscore(key, member) == 3.5

        # Key exists, but it is not a sorted set
        assert await glide_client.set(key, "_") == OK
        with pytest.raises(RequestError):
            await glide_client.zincrby(key, 0.5, "_")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrem(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3

        assert await glide_client.zrem(key, ["one"]) == 1
        assert await glide_client.zrem(key, ["one", "two", "three"]) == 2

        assert await glide_client.zrem("non_existing_set", ["member"]) == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zremrangebyscore(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores) == 3

        assert (
            await glide_client.zremrangebyscore(
                key, ScoreBoundary(1, False), ScoreBoundary(2)
            )
            == 1
        )
        assert (
            await glide_client.zremrangebyscore(key, ScoreBoundary(1), InfBound.NEG_INF)
            == 0
        )
        assert (
            await glide_client.zremrangebyscore(
                "non_existing_set", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zremrangebyscore(key, InfBound.NEG_INF, InfBound.POS_INF)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zremrangebylex(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores: Mapping[TEncodable, float] = {"a": 1, "b": 2, "c": 3, "d": 4}
        assert await glide_client.zadd(key1, members_scores) == 4

        assert (
            await glide_client.zremrangebylex(
                key1, LexBoundary("a", False), LexBoundary("c")
            )
            == 2
        )
        zremrangebylex_res = await glide_client.zrange_withscores(key1, range)
        assert compare_maps(zremrangebylex_res, {"a": 1.0, "d": 4.0}) is True

        assert (
            await glide_client.zremrangebylex(key1, LexBoundary("d"), InfBound.POS_INF)
            == 1
        )
        assert await glide_client.zrange_withscores(key1, range) == {b"a": 1.0}

        # min_lex > max_lex
        assert (
            await glide_client.zremrangebylex(key1, LexBoundary("a"), InfBound.NEG_INF)
            == 0
        )
        assert await glide_client.zrange_withscores(key1, range) == {b"a": 1.0}

        assert (
            await glide_client.zremrangebylex(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

        # key exists, but it is not a sorted set
        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zremrangebylex(
                key2, LexBoundary("a", False), LexBoundary("c")
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zremrangebyrank(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores: Mapping[TEncodable, float] = {
            "a": 1,
            "b": 2,
            "c": 3,
            "d": 4,
            "e": 5,
        }
        assert await glide_client.zadd(key1, members_scores) == 5

        # Test start exceeding end
        assert await glide_client.zremrangebyrank(key1, 2, 1) == 0

        # Test removing elements by rank
        assert await glide_client.zremrangebyrank(key1, 0, 2) == 3
        zremrangebyrank_res = await glide_client.zrange_withscores(key1, range)
        assert compare_maps(zremrangebyrank_res, {"d": 4.0, "e": 5.0}) is True

        # Test removing elements beyond the existing range
        assert await glide_client.zremrangebyrank(key1, 0, 10) == 2
        assert await glide_client.zrange_withscores(key1, range) == {}

        # Test with non-existing key
        assert await glide_client.zremrangebyrank("non_existing_key", 0, 1) == 0

        # Key exists, but it is not a sorted set
        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zremrangebyrank(key2, 0, 1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zlexcount(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"a": 1.0, "b": 2.0, "c": 3.0}

        assert await glide_client.zadd(key1, members_scores) == 3
        assert (
            await glide_client.zlexcount(key1, InfBound.NEG_INF, InfBound.POS_INF) == 3
        )
        assert (
            await glide_client.zlexcount(
                key1,
                LexBoundary("a", is_inclusive=False),
                LexBoundary("c", is_inclusive=True),
            )
            == 2
        )
        assert (
            await glide_client.zlexcount(
                key1, InfBound.NEG_INF, LexBoundary("c", is_inclusive=True)
            )
            == 3
        )
        # Incorrect range; start > end
        assert (
            await glide_client.zlexcount(
                key1, InfBound.POS_INF, LexBoundary("c", is_inclusive=True)
            )
            == 0
        )
        assert (
            await glide_client.zlexcount(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

        # key exists, but it is not a sorted set
        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zlexcount(key2, InfBound.NEG_INF, InfBound.POS_INF)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zcard(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3
        assert await glide_client.zcard(key) == 3

        assert await glide_client.zrem(key, ["one"]) == 1
        assert await glide_client.zcard(key) == 2
        assert await glide_client.zcard("non_existing_key") == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zcount(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3

        assert await glide_client.zcount(key, InfBound.NEG_INF, InfBound.POS_INF) == 3
        assert (
            await glide_client.zcount(
                key,
                ScoreBoundary(1, is_inclusive=False),
                ScoreBoundary(3, is_inclusive=False),
            )
            == 1
        )
        assert (
            await glide_client.zcount(
                key,
                ScoreBoundary(1, is_inclusive=False),
                ScoreBoundary(3, is_inclusive=True),
            )
            == 2
        )
        assert (
            await glide_client.zcount(
                key, InfBound.NEG_INF, ScoreBoundary(3, is_inclusive=True)
            )
            == 3
        )
        assert (
            await glide_client.zcount(
                key, InfBound.POS_INF, ScoreBoundary(3, is_inclusive=True)
            )
            == 0
        )
        assert (
            await glide_client.zcount(
                "non_existing_key", InfBound.NEG_INF, InfBound.POS_INF
            )
            == 0
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zscore(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3
        assert await glide_client.zscore(key, "one") == 1.0

        assert await glide_client.zscore(key, "non_existing_member") is None
        assert (
            await glide_client.zscore("non_existing_key", "non_existing_member") is None
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zmscore(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}

        assert await glide_client.zadd(key1, members_scores=members_scores) == 3
        assert await glide_client.zmscore(key1, ["one", "two", "three"]) == [
            1.0,
            2.0,
            3.0,
        ]
        assert await glide_client.zmscore(
            key1, ["one", "non_existing_member", "non_existing_member", "three"]
        ) == [1.0, None, None, 3.0]
        assert await glide_client.zmscore("non_existing_key", ["one"]) == [None]

        assert await glide_client.set(key2, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zmscore(key2, ["one"])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zinter_commands(self, glide_client: TGlideClient):
        key1 = "{testKey}:1-" + get_random_string(10)
        key2 = "{testKey}:2-" + get_random_string(10)
        key3 = "{testKey}:3-" + get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores1: Mapping[TEncodable, float] = {"one": 1.0, "two": 2.0}
        members_scores2: Mapping[TEncodable, float] = {
            "one": 1.5,
            "two": 2.5,
            "three": 3.5,
        }

        assert await glide_client.zadd(key1, members_scores1) == 2
        assert await glide_client.zadd(key2, members_scores2) == 3

        # zinter tests
        zinter_map = await glide_client.zinter([key1, key2])
        expected_zinter_map = [b"one", b"two"]
        assert zinter_map == expected_zinter_map

        # zinterstore tests
        assert await glide_client.zinterstore(key3, [key1, key2]) == 2
        zinterstore_map = await glide_client.zrange_withscores(key3, range)
        expected_zinter_map_withscores = {
            b"one": 2.5,
            b"two": 4.5,
        }
        assert compare_maps(zinterstore_map, expected_zinter_map_withscores) is True

        # zinter_withscores tests
        zinter_withscores_map = await glide_client.zinter_withscores([key1, key2])
        assert (
            compare_maps(zinter_withscores_map, expected_zinter_map_withscores) is True
        )

        # MAX aggregation tests
        assert (
            await glide_client.zinterstore(key3, [key1, key2], AggregationType.MAX) == 2
        )
        zinterstore_map_max = await glide_client.zrange_withscores(key3, range)
        expected_zinter_map_max = {
            b"one": 1.5,
            b"two": 2.5,
        }
        assert compare_maps(zinterstore_map_max, expected_zinter_map_max) is True

        zinter_withscores_map_max = await glide_client.zinter_withscores(
            [key1, key2], AggregationType.MAX
        )
        assert compare_maps(zinter_withscores_map_max, expected_zinter_map_max) is True

        # MIN aggregation tests
        assert (
            await glide_client.zinterstore(key3, [key1, key2], AggregationType.MIN) == 2
        )
        zinterstore_map_min = await glide_client.zrange_withscores(key3, range)
        expected_zinter_map_min = {
            b"one": 1.0,
            b"two": 2.0,
        }
        assert compare_maps(zinterstore_map_min, expected_zinter_map_min) is True

        zinter_withscores_map_min = await glide_client.zinter_withscores(
            [key1, key2], AggregationType.MIN
        )
        assert compare_maps(zinter_withscores_map_min, expected_zinter_map_min) is True

        # SUM aggregation tests
        assert (
            await glide_client.zinterstore(key3, [key1, key2], AggregationType.SUM) == 2
        )
        zinterstore_map_sum = await glide_client.zrange_withscores(key3, range)
        assert compare_maps(zinterstore_map_sum, expected_zinter_map_withscores) is True

        zinter_withscores_map_sum = await glide_client.zinter_withscores(
            [key1, key2], AggregationType.SUM
        )
        assert (
            compare_maps(zinter_withscores_map_sum, expected_zinter_map_withscores)
            is True
        )

        # Multiplying scores during aggregation tests
        assert (
            await glide_client.zinterstore(
                key3, [(key1, 2.0), (key2, 2.0)], AggregationType.SUM
            )
            == 2
        )
        zinterstore_map_multiplied = await glide_client.zrange_withscores(key3, range)
        expected_zinter_map_multiplied = {
            b"one": 5.0,
            b"two": 9.0,
        }
        assert (
            compare_maps(zinterstore_map_multiplied, expected_zinter_map_multiplied)
            is True
        )

        zinter_withscores_map_multiplied = await glide_client.zinter_withscores(
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
            await glide_client.zinterstore(key3, [key1, "{testKey}-non_existing_key"])
            == 0
        )
        zinter_withscores_non_existing = await glide_client.zinter_withscores(
            [key1, "{testKey}-non_existing_key"]
        )
        assert zinter_withscores_non_existing == {}

        # Empty list check
        with pytest.raises(RequestError) as e:
            await glide_client.zinterstore(
                "{xyz}", cast(List[TEncodable], cast(List[TEncodable], []))
            )
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.zinter([])
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.zinter_withscores(cast(List[TEncodable], []))
        assert "at least 1 input key is needed" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zunion_commands(self, glide_client: TGlideClient):
        key1 = "{testKey}:1-" + get_random_string(10)
        key2 = "{testKey}:2-" + get_random_string(10)
        key3 = "{testKey}:3-" + get_random_string(10)
        range = RangeByIndex(0, -1)
        members_scores1: Mapping[TEncodable, float] = {"one": 1.0, "two": 2.0}
        members_scores2: Mapping[TEncodable, float] = {
            "one": 1.5,
            "two": 2.5,
            "three": 3.5,
        }

        assert await glide_client.zadd(key1, members_scores1) == 2
        assert await glide_client.zadd(key2, members_scores2) == 3

        # zunion tests
        zunion_map = await glide_client.zunion([key1, key2])
        expected_zunion_map = [b"one", b"three", b"two"]
        assert zunion_map == expected_zunion_map

        # zunionstore tests
        assert await glide_client.zunionstore(key3, [key1, key2]) == 3
        zunionstore_map = await glide_client.zrange_withscores(key3, range)
        expected_zunion_map_withscores = {
            b"one": 2.5,
            b"three": 3.5,
            b"two": 4.5,
        }
        assert compare_maps(zunionstore_map, expected_zunion_map_withscores) is True

        # zunion_withscores tests
        zunion_withscores_map = await glide_client.zunion_withscores([key1, key2])
        assert (
            compare_maps(zunion_withscores_map, expected_zunion_map_withscores) is True
        )

        # MAX aggregation tests
        assert (
            await glide_client.zunionstore(key3, [key1, key2], AggregationType.MAX) == 3
        )
        zunionstore_map_max = await glide_client.zrange_withscores(key3, range)
        expected_zunion_map_max = {
            b"one": 1.5,
            b"two": 2.5,
            b"three": 3.5,
        }
        assert compare_maps(zunionstore_map_max, expected_zunion_map_max) is True

        zunion_withscores_map_max = await glide_client.zunion_withscores(
            [key1, key2], AggregationType.MAX
        )
        assert compare_maps(zunion_withscores_map_max, expected_zunion_map_max) is True

        # MIN aggregation tests
        assert (
            await glide_client.zunionstore(key3, [key1, key2], AggregationType.MIN) == 3
        )
        zunionstore_map_min = await glide_client.zrange_withscores(key3, range)
        expected_zunion_map_min = {
            b"one": 1.0,
            b"two": 2.0,
            b"three": 3.5,
        }
        assert compare_maps(zunionstore_map_min, expected_zunion_map_min) is True

        zunion_withscores_map_min = await glide_client.zunion_withscores(
            [key1, key2], AggregationType.MIN
        )
        assert compare_maps(zunion_withscores_map_min, expected_zunion_map_min) is True

        # SUM aggregation tests
        assert (
            await glide_client.zunionstore(key3, [key1, key2], AggregationType.SUM) == 3
        )
        zunionstore_map_sum = await glide_client.zrange_withscores(key3, range)
        assert compare_maps(zunionstore_map_sum, expected_zunion_map_withscores) is True

        zunion_withscores_map_sum = await glide_client.zunion_withscores(
            [key1, key2], AggregationType.SUM
        )
        assert (
            compare_maps(zunion_withscores_map_sum, expected_zunion_map_withscores)
            is True
        )

        # Multiplying scores during aggregation tests
        assert (
            await glide_client.zunionstore(
                key3, [(key1, 2.0), (key2, 2.0)], AggregationType.SUM
            )
            == 3
        )
        zunionstore_map_multiplied = await glide_client.zrange_withscores(key3, range)
        expected_zunion_map_multiplied = {
            b"one": 5.0,
            b"three": 7.0,
            b"two": 9.0,
        }
        assert (
            compare_maps(zunionstore_map_multiplied, expected_zunion_map_multiplied)
            is True
        )

        zunion_withscores_map_multiplied = await glide_client.zunion_withscores(
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
            await glide_client.zunionstore(key3, [key1, "{testKey}-non_existing_key"])
            == 2
        )
        zunionstore_map_nonexistingkey = await glide_client.zrange_withscores(
            key3, range
        )
        expected_zunion_map_nonexistingkey = {
            b"one": 1.0,
            b"two": 2.0,
        }
        assert (
            compare_maps(
                zunionstore_map_nonexistingkey, expected_zunion_map_nonexistingkey
            )
            is True
        )

        zunion_withscores_non_existing = await glide_client.zunion_withscores(
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
            await glide_client.zunionstore("{xyz}", cast(List[TEncodable], []))
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.zunion([])
        assert "wrong number of arguments" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.zunion_withscores(cast(List[TEncodable], []))
        assert "at least 1 input key is needed" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zpopmin(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3
        assert await glide_client.zpopmin(key) == {b"a": 1.0}

        zpopmin_map = await glide_client.zpopmin(key, 3)
        expected_map = {b"b": 2.0, b"c": 3.0}
        assert compare_maps(zpopmin_map, expected_map) is True

        assert await glide_client.zpopmin(key) == {}
        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zpopmin(key)

        assert await glide_client.zpopmin("non_exisitng_key") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bzpopmin(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"

        assert await glide_client.zadd(key1, {"a": 1.0, "b": 1.5}) == 2
        assert await glide_client.zadd(key2, {"c": 2.0}) == 1
        assert await glide_client.bzpopmin(
            [key1, key2], 0.5
        ) == convert_string_to_bytes_object([key1, "a", 1.0])
        assert await glide_client.bzpopmin(
            [non_existing_key, key2], 0.5
        ) == convert_string_to_bytes_object(
            [
                key2,
                "c",
                2.0,
            ]
        )
        assert await glide_client.bzpopmin(["non_existing_key"], 0.5) is None

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.bzpopmin([], 0.5)

        # key exists, but it is not a sorted set
        assert await glide_client.set("foo", "value") == OK
        with pytest.raises(RequestError):
            await glide_client.bzpopmin(["foo"], 0.5)

        async def endless_bzpopmin_call():
            await glide_client.bzpopmin(["non_existent_key"], 0)

        # bzpopmin is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_bzpopmin_call(), timeout=0.5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zpopmax(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await glide_client.zadd(key, members_scores) == 3
        assert await glide_client.zpopmax(key) == {b"c": 3.0}

        zpopmax_map = await glide_client.zpopmax(key, 3)
        expected_map = {"b": 2.0, "a": 1.0}
        assert compare_maps(zpopmax_map, expected_map) is True

        assert await glide_client.zpopmax(key) == {}
        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zpopmax(key)

        assert await glide_client.zpopmax("non_exisitng_key") == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bzpopmax(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:{get_random_string(10)}"
        key2 = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:non_existing_key"

        assert await glide_client.zadd(key1, {"a": 1.0, "b": 1.5}) == 2
        assert await glide_client.zadd(key2, {"c": 2.0}) == 1
        assert await glide_client.bzpopmax(
            [key1, key2], 0.5
        ) == convert_string_to_bytes_object([key1, "b", 1.5])
        assert await glide_client.bzpopmax(
            [non_existing_key, key2], 0.5
        ) == convert_string_to_bytes_object(
            [
                key2,
                "c",
                2.0,
            ]
        )
        assert await glide_client.bzpopmax(["non_existing_key"], 0.5) is None

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.bzpopmax([], 0.5)

        # key exists, but it is not a sorted set
        assert await glide_client.set("foo", "value") == OK
        with pytest.raises(RequestError):
            await glide_client.bzpopmax(["foo"], 0.5)

        async def endless_bzpopmax_call():
            await glide_client.bzpopmax(["non_existent_key"], 0)

        # bzpopmax is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_bzpopmax_call(), timeout=0.5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_by_index(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3

        assert await glide_client.zrange(key, RangeByIndex(0, 1)) == [
            b"one",
            b"two",
        ]

        zrange_map = await glide_client.zrange_withscores(key, RangeByIndex(0, -1))
        expected_map = {b"one": 1.0, b"two": 2.0, b"three": 3.0}
        assert compare_maps(zrange_map, expected_map) is True

        assert await glide_client.zrange(key, RangeByIndex(0, 1), reverse=True) == [
            b"three",
            b"two",
        ]

        assert await glide_client.zrange(key, RangeByIndex(3, 1)) == []
        assert await glide_client.zrange_withscores(key, RangeByIndex(3, 1)) == {}

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_byscore(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3

        assert await glide_client.zrange(
            key,
            RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, is_inclusive=False)),
        ) == [b"one", b"two"]

        zrange_map = await glide_client.zrange_withscores(
            key,
            RangeByScore(InfBound.NEG_INF, InfBound.POS_INF),
        )
        expected_map = {b"one": 1.0, b"two": 2.0, b"three": 3.0}
        assert compare_maps(zrange_map, expected_map) is True

        assert await glide_client.zrange(
            key,
            RangeByScore(ScoreBoundary(3, is_inclusive=False), InfBound.NEG_INF),
            reverse=True,
        ) == [b"two", b"one"]

        assert (
            await glide_client.zrange(
                key,
                RangeByScore(
                    InfBound.NEG_INF,
                    InfBound.POS_INF,
                    Limit(offset=1, count=2),
                ),
            )
        ) == [b"two", b"three"]

        assert (
            await glide_client.zrange(
                key,
                RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, is_inclusive=False)),
                reverse=True,
            )
            == []
        )  # end is greater than start with reverse set to True

        assert (
            await glide_client.zrange(
                key,
                RangeByScore(InfBound.POS_INF, ScoreBoundary(3, is_inclusive=False)),
            )
            == []
        )  # start is greater than end

        assert (
            await glide_client.zrange_withscores(
                key,
                RangeByScore(InfBound.POS_INF, ScoreBoundary(3, is_inclusive=False)),
            )
            == {}
        )  # start is greater than end

        assert (
            await glide_client.zrange_withscores(
                key,
                RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, is_inclusive=False)),
                reverse=True,
            )
            == {}
        )  # end is greater than start with reverse set to True

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_bylex(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"a": 1, "b": 2, "c": 3}
        assert await glide_client.zadd(key, members_scores=members_scores) == 3

        assert await glide_client.zrange(
            key,
            RangeByLex(
                start=InfBound.NEG_INF, end=LexBoundary("c", is_inclusive=False)
            ),
        ) == [b"a", b"b"]

        assert (
            await glide_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.NEG_INF,
                    end=InfBound.POS_INF,
                    limit=Limit(offset=1, count=2),
                ),
            )
        ) == [b"b", b"c"]

        assert await glide_client.zrange(
            key,
            RangeByLex(
                start=LexBoundary("c", is_inclusive=False), end=InfBound.NEG_INF
            ),
            reverse=True,
        ) == [b"b", b"a"]

        assert (
            await glide_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.NEG_INF, end=LexBoundary("c", is_inclusive=False)
                ),
                reverse=True,
            )
            == []
        )  # end is greater than start with reverse set to True

        assert (
            await glide_client.zrange(
                key,
                RangeByLex(
                    start=InfBound.POS_INF, end=LexBoundary("c", is_inclusive=False)
                ),
            )
            == []
        )  # start is greater than end

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrange_different_types_of_keys(self, glide_client: TGlideClient):
        key = get_random_string(10)

        assert await glide_client.zrange("non_existing_key", RangeByIndex(0, 1)) == []

        assert (
            await glide_client.zrange_withscores(
                "non_existing_key", RangeByIndex(0, -1)
            )
        ) == {}

        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrange(key, RangeByIndex(0, 1))

        with pytest.raises(RequestError):
            await glide_client.zrange_withscores(key, RangeByIndex(0, 1))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrangestore_by_index(self, glide_client: TGlideClient):
        destination = f"{{testKey}}:{get_random_string(10)}"
        source = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"

        member_scores: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
        }
        assert await glide_client.zadd(source, member_scores) == 3

        # full range
        assert (
            await glide_client.zrangestore(destination, source, RangeByIndex(0, -1))
            == 3
        )
        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0, "three": 3.0}) is True

        # range from rank 0 to 1, from highest to lowest score
        assert (
            await glide_client.zrangestore(
                destination, source, RangeByIndex(0, 1), True
            )
            == 2
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"two": 2.0, "three": 3.0}) is True

        # incorrect range, as start > end
        assert (
            await glide_client.zrangestore(destination, source, RangeByIndex(3, 1)) == 0
        )
        assert (
            await glide_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # non-existing source
        assert (
            await glide_client.zrangestore(
                destination, non_existing_key, RangeByIndex(0, -1)
            )
            == 0
        )
        assert (
            await glide_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrangestore(destination, string_key, RangeByIndex(0, -1))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrangestore_by_score(self, glide_client: TGlideClient):
        destination = f"{{testKey}}:{get_random_string(10)}"
        source = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"

        member_scores: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
        }
        assert await glide_client.zadd(source, member_scores) == 3

        # range from negative infinity to 3 (exclusive)
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, False)),
            )
            == 2
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0}) is True

        # range from 1 (inclusive) to positive infinity
        assert (
            await glide_client.zrangestore(
                destination, source, RangeByScore(ScoreBoundary(1), InfBound.POS_INF)
            )
            == 3
        )
        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0, "three": 3.0}) is True

        # range from negative to positive infinity, limited to ranks 1 to 2
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByScore(InfBound.NEG_INF, InfBound.POS_INF, Limit(1, 2)),
            )
            == 2
        )
        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"two": 2.0, "three": 3.0}) is True

        # range from positive to negative infinity reversed, limited to ranks 1 to 2
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByScore(InfBound.POS_INF, InfBound.NEG_INF, Limit(1, 2)),
                True,
            )
            == 2
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"one": 1.0, "two": 2.0}) is True

        # incorrect range as start > end
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByScore(ScoreBoundary(3, False), InfBound.NEG_INF),
            )
            == 0
        )
        assert (
            await glide_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # non-existing source
        assert (
            await glide_client.zrangestore(
                destination,
                non_existing_key,
                RangeByScore(InfBound.NEG_INF, ScoreBoundary(3, False)),
            )
            == 0
        )
        assert (
            await glide_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrangestore(
                destination,
                string_key,
                RangeByScore(ScoreBoundary(0), ScoreBoundary(3)),
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrangestore_by_lex(self, glide_client: TGlideClient):
        destination = f"{{testKey}}:{get_random_string(10)}"
        source = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores: Mapping[TEncodable, float] = {"a": 1.0, "b": 2.0, "c": 3.0}
        assert await glide_client.zadd(source, member_scores) == 3

        # range from negative infinity to "c" (exclusive)
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByLex(InfBound.NEG_INF, LexBoundary("c", False)),
            )
            == 2
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"a": 1.0, "b": 2.0}) is True

        # range from "a" (inclusive) to positive infinity
        assert (
            await glide_client.zrangestore(
                destination, source, RangeByLex(LexBoundary("a"), InfBound.POS_INF)
            )
            == 3
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"a": 1.0, "b": 2.0, "c": 3.0}) is True

        # range from negative to positive infinity, limited to ranks 1 to 2
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByLex(InfBound.NEG_INF, InfBound.POS_INF, Limit(1, 2)),
            )
            == 2
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"b": 2.0, "c": 3.0}) is True

        # range from positive to negative infinity reversed, limited to ranks 1 to 2
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByLex(InfBound.POS_INF, InfBound.NEG_INF, Limit(1, 2)),
                True,
            )
            == 2
        )

        zrange_res = await glide_client.zrange_withscores(
            destination, RangeByIndex(0, -1)
        )
        assert compare_maps(zrange_res, {"a": 1.0, "b": 2.0}) is True

        # incorrect range as start > end
        assert (
            await glide_client.zrangestore(
                destination,
                source,
                RangeByLex(LexBoundary("c", False), InfBound.NEG_INF),
            )
            == 0
        )
        assert (
            await glide_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # non-existing source
        assert (
            await glide_client.zrangestore(
                destination,
                non_existing_key,
                RangeByLex(InfBound.NEG_INF, InfBound.POS_INF),
            )
            == 0
        )
        assert (
            await glide_client.zrange_withscores(destination, RangeByIndex(0, -1)) == {}
        )

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrangestore(
                destination, string_key, RangeByLex(InfBound.NEG_INF, InfBound.POS_INF)
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrank(self, glide_client: TGlideClient):
        key = get_random_string(10)
        members_scores: Mapping[TEncodable, float] = {"one": 1.5, "two": 2, "three": 3}
        assert await glide_client.zadd(key, members_scores) == 3
        assert await glide_client.zrank(key, "one") == 0
        if not await check_if_server_version_lt(glide_client, "7.2.0"):
            assert await glide_client.zrank_withscore(key, "one") == [0, 1.5]
            assert await glide_client.zrank_withscore(key, "non_existing_field") is None
            assert (
                await glide_client.zrank_withscore("non_existing_key", "field") is None
            )

        assert await glide_client.zrank(key, "non_existing_field") is None
        assert await glide_client.zrank("non_existing_key", "field") is None

        assert await glide_client.set(key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrank(key, "one")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrevrank(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        member_scores: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
        }

        assert await glide_client.zadd(key, member_scores) == 3
        assert await glide_client.zrevrank(key, "three") == 0
        assert await glide_client.zrevrank(key, "non_existing_member") is None
        assert (
            await glide_client.zrevrank(non_existing_key, "non_existing_member") is None
        )

        if not await check_if_server_version_lt(glide_client, "7.2.0"):
            assert await glide_client.zrevrank_withscore(key, "one") == [2, 1.0]
            assert (
                await glide_client.zrevrank_withscore(key, "non_existing_member")
                is None
            )
            assert (
                await glide_client.zrevrank_withscore(
                    non_existing_key, "non_existing_member"
                )
                is None
            )

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.zrevrank(string_key, "member")
        with pytest.raises(RequestError):
            await glide_client.zrevrank_withscore(string_key, "member")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zdiff(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores1: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
        }
        member_scores2: Mapping[TEncodable, float] = {"two": 2.0}
        member_scores3: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
            "four": 4.0,
        }

        assert await glide_client.zadd(key1, member_scores1) == 3
        assert await glide_client.zadd(key2, member_scores2) == 1
        assert await glide_client.zadd(key3, member_scores3) == 4

        assert await glide_client.zdiff([key1, key2]) == [b"one", b"three"]
        assert await glide_client.zdiff([key1, key3]) == []
        assert await glide_client.zdiff([non_existing_key, key3]) == []

        zdiff_map = await glide_client.zdiff_withscores([key1, key2])
        expected_map = {
            b"one": 1.0,
            b"three": 3.0,
        }
        assert compare_maps(zdiff_map, expected_map) is True
        assert (
            compare_maps(await glide_client.zdiff_withscores([key1, key3]), {}) is True  # type: ignore
        )
        non_exist_res = await glide_client.zdiff_withscores([non_existing_key, key3])
        assert non_exist_res == {}

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.zdiff([])

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.zdiff_withscores([])

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.zdiff([string_key, key2])

        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.zdiff_withscores([string_key, key2])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zdiffstore(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        key4 = f"{{testKey}}:4-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores1: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
        }
        member_scores2: Mapping[TEncodable, float] = {"two": 2.0}
        member_scores3: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
            "four": 4.0,
        }

        assert await glide_client.zadd(key1, member_scores1) == 3
        assert await glide_client.zadd(key2, member_scores2) == 1
        assert await glide_client.zadd(key3, member_scores3) == 4

        assert await glide_client.zdiffstore(key4, [key1, key2]) == 2

        zrange_res = await glide_client.zrange_withscores(key4, RangeByIndex(0, -1))
        assert compare_maps(zrange_res, {"one": 1.0, "three": 3.0}) is True

        assert await glide_client.zdiffstore(key4, [key3, key2, key1]) == 1
        assert await glide_client.zrange_withscores(key4, RangeByIndex(0, -1)) == {
            b"four": 4.0
        }

        assert await glide_client.zdiffstore(key4, [key1, key3]) == 0
        assert await glide_client.zrange_withscores(key4, RangeByIndex(0, -1)) == {}

        assert await glide_client.zdiffstore(key4, [non_existing_key, key1]) == 0
        assert await glide_client.zrange_withscores(key4, RangeByIndex(0, -1)) == {}

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zdiffstore(key4, [string_key, key1])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bzmpop(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        non_existing_key = f"{{test}}-non_existing_key"
        string_key = f"{{test}}-3-f{get_random_string(10)}"

        assert (
            await glide_client.zadd(
                key1, cast(Mapping[TEncodable, float], {"a1": 1, "b1": 2})
            )
            == 2
        )
        assert (
            await glide_client.zadd(
                key2, cast(Mapping[TEncodable, float], {"a2": 0.1, "b2": 0.2})
            )
            == 2
        )

        assert await glide_client.bzmpop([key1, key2], ScoreFilter.MAX, 0.1) == [
            key1.encode(),
            {b"b1": 2},
        ]
        assert await glide_client.bzmpop([key2, key1], ScoreFilter.MAX, 0.1, 10) == [
            key2.encode(),
            {b"b2": 0.2, b"a2": 0.1},
        ]

        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert (
            await glide_client.bzmpop([non_existing_key], ScoreFilter.MIN, 0.5) is None
        )
        assert (
            await glide_client.bzmpop([non_existing_key], ScoreFilter.MIN, 0.55, 1)
            is None
        )

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.bzmpop([string_key], ScoreFilter.MAX, 0.1)
        with pytest.raises(RequestError):
            await glide_client.bzmpop([string_key], ScoreFilter.MAX, 0.1, 1)

        # incorrect argument: key list should not be empty
        with pytest.raises(RequestError):
            assert await glide_client.bzmpop([], ScoreFilter.MAX, 0.1, 1)

        # incorrect argument: count should be greater than 0
        with pytest.raises(RequestError):
            assert await glide_client.bzmpop([key1], ScoreFilter.MAX, 0.1, 0)

        # check that order of entries in the response is preserved
        entries: Dict[TEncodable, float] = {}
        for i in range(0, 10):
            entries.update({f"a{i}": float(i)})

        assert await glide_client.zadd(key2, entries) == 10
        result = await glide_client.bzmpop([key2], ScoreFilter.MIN, 0.1, 10)
        assert result is not None
        result_map = cast(Mapping[bytes, float], result[1])
        assert compare_maps(entries, result_map) is True  # type: ignore

        async def endless_bzmpop_call():
            await glide_client.bzmpop(["non_existent_key"], ScoreFilter.MAX, 0)

        # bzmpop is called against a non-existing key with no timeout, but we wrap the call in an asyncio timeout to
        # avoid having the test block forever
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_bzmpop_call(), timeout=0.5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrandmember(self, glide_client: TGlideClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        scores: Mapping[TEncodable, float] = {"one": 1, "two": 2}
        assert await glide_client.zadd(key, scores) == 2

        member = await glide_client.zrandmember(key)
        # TODO: remove when functions API is fixed
        assert isinstance(member, bytes)
        assert member.decode() in scores
        assert await glide_client.zrandmember("non_existing_key") is None

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrandmember(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrandmember_count(self, glide_client: TGlideClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        scores: Mapping[TEncodable, float] = {"one": 1, "two": 2}
        assert await glide_client.zadd(key, scores) == 2

        # unique values are expected as count is positive
        members = await glide_client.zrandmember_count(key, 4)
        assert len(members) == 2
        assert set(members) == {b"one", b"two"}

        # duplicate values are expected as count is negative
        members = await glide_client.zrandmember_count(key, -4)
        assert len(members) == 4
        for member in members:
            # TODO: remove when functions API is fixed
            assert isinstance(member, bytes)
            assert member.decode() in scores

        assert await glide_client.zrandmember_count(key, 0) == []
        assert await glide_client.zrandmember_count("non_existing_key", 0) == []

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrandmember_count(string_key, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zrandmember_withscores(self, glide_client: TGlideClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        scores: Mapping[TEncodable, float] = {"one": 1, "two": 2}
        assert await glide_client.zadd(key, scores) == 2

        # unique values are expected as count is positive
        elements = await glide_client.zrandmember_withscores(key, 4)
        assert len(elements) == 2

        for member, score in elements:
            # TODO: remove when functions API is fixed
            assert isinstance(member, bytes)
            assert scores[(member).decode()] == score

        # duplicate values are expected as count is negative
        elements = await glide_client.zrandmember_withscores(key, -4)
        assert len(elements) == 4
        for member, score in elements:
            # TODO: remove when functions API is fixed
            assert isinstance(member, bytes)
            assert scores[(member).decode()] == score

        assert await glide_client.zrandmember_withscores(key, 0) == []
        assert await glide_client.zrandmember_withscores("non_existing_key", 0) == []

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zrandmember_withscores(string_key, 5)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zintercard(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        member_scores1: Mapping[TEncodable, float] = {
            "one": 1.0,
            "two": 2.0,
            "three": 3.0,
        }
        member_scores2: Mapping[TEncodable, float] = {
            "two": 2.0,
            "three": 3.0,
            "four": 4.0,
        }

        assert await glide_client.zadd(key1, member_scores1) == 3
        assert await glide_client.zadd(key2, member_scores2) == 3

        assert await glide_client.zintercard([key1, key2]) == 2
        assert await glide_client.zintercard([key1, non_existing_key]) == 0

        assert await glide_client.zintercard([key1, key2], 0) == 2
        assert await glide_client.zintercard([key1, key2], 1) == 1
        assert await glide_client.zintercard([key1, key2], 3) == 2

        # invalid argument - key list must not be empty
        with pytest.raises(RequestError):
            await glide_client.zintercard([])

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zintercard([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zmpop(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{test}}-1-f{get_random_string(10)}"
        key2 = f"{{test}}-2-f{get_random_string(10)}"
        non_existing_key = f"{{test}}-non_existing_key"
        string_key = f"{{test}}-3-f{get_random_string(10)}"

        assert await glide_client.zadd(key1, {"a1": 1, "b1": 2}) == 2
        assert await glide_client.zadd(key2, {"a2": 0.1, "b2": 0.2}) == 2

        assert await glide_client.zmpop([key1, key2], ScoreFilter.MAX) == [
            key1.encode(),
            {b"b1": 2},
        ]
        assert await glide_client.zmpop([key2, key1], ScoreFilter.MAX, 10) == [
            key2.encode(),
            {b"b2": 0.2, b"a2": 0.1},
        ]

        assert await glide_client.zmpop([non_existing_key], ScoreFilter.MIN) is None
        assert await glide_client.zmpop([non_existing_key], ScoreFilter.MIN, 1) is None

        # key exists, but it is not a sorted set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.zmpop([string_key], ScoreFilter.MAX)
        with pytest.raises(RequestError):
            await glide_client.zmpop([string_key], ScoreFilter.MAX, 1)

        # incorrect argument: key list should not be empty
        with pytest.raises(RequestError):
            assert await glide_client.zmpop([], ScoreFilter.MAX, 1)

        # incorrect argument: count should be greater than 0
        with pytest.raises(RequestError):
            assert await glide_client.zmpop([key1], ScoreFilter.MAX, 0)

        # check that order of entries in the response is preserved
        entries: Dict[TEncodable, float] = {}
        for i in range(0, 10):
            entries[f"a{i}"] = float(i)

        assert await glide_client.zadd(key2, entries) == 10
        result = await glide_client.zmpop([key2], ScoreFilter.MIN, 10)
        assert result is not None
        result_map = cast(Mapping[bytes, float], result[1])
        assert compare_maps(entries, result_map) is True  # type: ignore

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_type(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.set(key, "value") == OK
        assert (await glide_client.type(key)).lower() == b"string"
        assert await glide_client.delete([key]) == 1

        assert await glide_client.set(key.encode(), "value") == OK
        assert (await glide_client.type(key.encode())).lower() == b"string"
        assert await glide_client.delete([key.encode()]) == 1

        assert await glide_client.lpush(key, ["value"]) == 1
        assert (await glide_client.type(key)).lower() == b"list"
        assert await glide_client.delete([key]) == 1

        assert await glide_client.sadd(key, ["value"]) == 1
        assert (await glide_client.type(key)).lower() == b"set"
        assert await glide_client.delete([key]) == 1

        assert await glide_client.zadd(key, {"member": 1.0}) == 1
        assert (await glide_client.type(key)).lower() == b"zset"
        assert await glide_client.delete([key]) == 1

        assert await glide_client.hset(key, {"field": "value"}) == 1
        assert (await glide_client.type(key)).lower() == b"hash"
        assert await glide_client.delete([key]) == 1

        await glide_client.xadd(key, [("field", "value")])
        assert await glide_client.type(key) == b"stream"
        assert await glide_client.delete([key]) == 1

        assert (await glide_client.type(key)).lower() == b"none"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sort_and_sort_store_with_get_or_by_args(
        self, glide_client: TGlideClient
    ):
        if isinstance(
            glide_client, GlideClusterClient
        ) and await check_if_server_version_lt(glide_client, "7.9.0"):
            return pytest.mark.skip(
                reason=f"Valkey version required in cluster mode>= 8.0.0"
            )
        key = "{user}" + get_random_string(10)
        store = "{user}" + get_random_string(10)
        user_key1, user_key2, user_key3, user_key4, user_key5 = (
            "{user}:1",
            "{user}:2",
            "{user}:3",
            "{user}:4",
            "{user}:5",
        )

        # Prepare some data. Some keys and values randomaly encoded
        assert await glide_client.hset(user_key1, {"name": "Alice", "age": "30"}) == 2
        assert (
            await glide_client.hset(user_key2.encode(), {"name": "Bob", "age": "25"})
            == 2
        )
        assert await glide_client.hset(user_key3, {"name": "Charlie", "age": "35"}) == 2
        assert (
            await glide_client.hset(user_key4, {"name": "Dave", "age".encode(): "20"})
            == 2
        )
        assert (
            await glide_client.hset(user_key5, {"name": "Eve", "age": "40".encode()})
            == 2
        )
        assert await glide_client.lpush("{user}_ids", ["5", "4", "3", "2", "1"]) == 5

        # SORT_RO Available since: 7.0.0
        skip_sort_ro_test = False
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            skip_sort_ro_test = True

        # Test sort with all arguments
        assert await glide_client.lpush(key, ["3", "1", "2"]) == 3
        result = await glide_client.sort(
            key,
            limit=Limit(0, 2),
            get_patterns=["{user}:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        assert result == [b"Alice", b"Bob"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(
                key,
                limit=Limit(0, 2),
                get_patterns=[b"{user}:*->name"],
                order=OrderBy.ASC,
                alpha=True,
            )
            assert result_ro == [b"Alice", b"Bob"]

        # Test sort_store with all arguments
        sort_store_result = await glide_client.sort_store(
            key,
            store,
            limit=Limit(0, 2),
            get_patterns=["{user}:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        assert sort_store_result == 2
        sorted_list = await glide_client.lrange(store, 0, -1)
        assert sorted_list == [b"Alice", b"Bob"]

        # Test sort with `by` argument
        result = await glide_client.sort(
            "{user}_ids",
            by_pattern="{user}:*->age",
            get_patterns=["{user}:*->name"],
            alpha=True,
        )
        assert result == [b"Dave", b"Bob", b"Alice", b"Charlie", b"Eve"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(
                "{user}_ids",
                by_pattern=b"{user}:*->age",
                get_patterns=["{user}:*->name"],
                alpha=True,
            )
            assert result_ro == [b"Dave", b"Bob", b"Alice", b"Charlie", b"Eve"]

        # Test sort with `by` argument with missing keys to sort by
        assert await glide_client.lpush("{user}_ids", ["a"]) == 6
        result = await glide_client.sort(
            "{user}_ids",
            by_pattern="{user}:*->age",
            get_patterns=["{user}:*->name"],
            alpha=True,
        )
        assert result == convert_string_to_bytes_object(
            [None, "Dave", "Bob", "Alice", "Charlie", "Eve"]
        )

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(
                "{user}_ids",
                by_pattern="{user}:*->age",
                get_patterns=["{user}:*->name"],
                alpha=True,
            )
            assert result_ro == [None, b"Dave", b"Bob", b"Alice", b"Charlie", b"Eve"]

        # Test sort with `by` argument with missing keys to sort by
        result = await glide_client.sort(
            "{user}_ids",
            by_pattern="{user}:*->name",
            get_patterns=["{user}:*->age"],
            alpha=True,
        )
        assert result == convert_string_to_bytes_object(
            [None, "30", "25", "35", "20", "40"]
        )

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(
                "{user}_ids",
                by_pattern="{user}:*->name",
                get_patterns=["{user}:*->age"],
                alpha=True,
            )
            assert result_ro == [None, b"30", b"25", b"35", b"20", b"40"]

        # Test Limit with count 0
        result = await glide_client.sort(
            "{user}_ids",
            limit=Limit(0, 0),
            alpha=True,
        )
        assert result == []

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(
                "{user}_ids",
                limit=Limit(0, 0),
                alpha=True,
            )
            assert result_ro == []

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sort_and_sort_store_without_get_or_by_args(
        self, glide_client: TGlideClient
    ):
        key = "{SameSlotKey}" + get_random_string(10)
        store = "{SameSlotKey}" + get_random_string(10)

        # SORT_RO Available since: 7.0.0
        skip_sort_ro_test = False
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            skip_sort_ro_test = True

        # Test sort with non-existing key
        result = await glide_client.sort("non_existing_key")
        assert result == []

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(b"non_existing_key")
            assert result_ro == []

        # Test sort_store with non-existing key
        sort_store_result = await glide_client.sort_store(
            "{SameSlotKey}:non_existing_key", store
        )
        assert sort_store_result == 0

        # Test each argument separately
        assert await glide_client.lpush(key, ["5", "2", "4", "1", "3"]) == 5

        # Test w/o flags
        result = await glide_client.sort(key)
        assert result == [b"1", b"2", b"3", b"4", b"5"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(key)
            assert result_ro == [b"1", b"2", b"3", b"4", b"5"]

        # limit argument
        result = await glide_client.sort(key, limit=Limit(1, 3))
        assert result == [b"2", b"3", b"4"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(key, limit=Limit(1, 3))
            assert result_ro == [b"2", b"3", b"4"]

        # order argument
        result = await glide_client.sort(key, order=OrderBy.DESC)
        assert result == [b"5", b"4", b"3", b"2", b"1"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(key, order=OrderBy.DESC)
            assert result_ro == [b"5", b"4", b"3", b"2", b"1"]

        assert await glide_client.lpush(key, ["a"]) == 6

        with pytest.raises(RequestError) as e:
            await glide_client.sort(key)
        assert "can't be converted into double" in str(e).lower()

        if not skip_sort_ro_test:
            with pytest.raises(RequestError) as e:
                await glide_client.sort_ro(key)
            assert "can't be converted into double" in str(e).lower()

        # alpha argument
        result = await glide_client.sort(key, alpha=True)
        assert result == [b"1", b"2", b"3", b"4", b"5", b"a"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(key, alpha=True)
            assert result_ro == [b"1", b"2", b"3", b"4", b"5", b"a"]

        # Combining multiple arguments
        result = await glide_client.sort(
            key, limit=Limit(1, 3), order=OrderBy.DESC, alpha=True
        )
        assert result == [b"5", b"4", b"3"]

        if not skip_sort_ro_test:
            result_ro = await glide_client.sort_ro(
                key, limit=Limit(1, 3), order=OrderBy.DESC, alpha=True
            )
            assert result_ro == [b"5", b"4", b"3"]

        # Test sort_store with combined arguments
        sort_store_result = await glide_client.sort_store(
            key, store, limit=Limit(1, 3), order=OrderBy.DESC, alpha=True
        )
        assert sort_store_result == 3
        sorted_list = await glide_client.lrange(store, 0, -1)
        assert sorted_list == [b"5", b"4", b"3"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_echo(self, glide_client: TGlideClient):
        message = get_random_string(5)
        assert await glide_client.echo(message) == message.encode()
        if isinstance(glide_client, GlideClusterClient):
            echo_dict = await glide_client.echo(message, AllNodes())
            assert isinstance(echo_dict, dict)
            for value in echo_dict.values():
                assert value == message.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_dbsize(self, glide_client: TGlideClient):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK

        assert await glide_client.dbsize() == 0
        key_value_pairs = [(get_random_string(10), "foo") for _ in range(10)]

        for key, value in key_value_pairs:
            assert await glide_client.set(key, value) == OK
        assert await glide_client.dbsize() == 10

        if isinstance(glide_client, GlideClusterClient):
            assert await glide_client.custom_command(["FLUSHALL"]) == OK
            key = get_random_string(5)
            assert await glide_client.set(key, value) == OK
            assert await glide_client.dbsize(SlotKeyRoute(SlotType.PRIMARY, key)) == 1
        else:
            assert await glide_client.select(1) == OK
            assert await glide_client.dbsize() == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_time(self, glide_client: TGlideClient):
        current_time = int(time.time()) - 1
        result = await glide_client.time()
        assert len(result) == 2
        assert isinstance(result, list)
        assert int(result[0]) > current_time
        assert 0 < int(result[1]) < 1000000

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lastsave(self, glide_client: TGlideClient):
        yesterday = date.today() - timedelta(1)
        yesterday_unix_time = time.mktime(yesterday.timetuple())

        result = await glide_client.lastsave()
        assert isinstance(result, int)
        assert result > yesterday_unix_time

        if isinstance(glide_client, GlideClusterClient):
            # test with single-node route
            result = await glide_client.lastsave(RandomNode())
            assert isinstance(result, int)
            assert result > yesterday_unix_time

            # test with multi-node route
            result = await glide_client.lastsave(AllNodes())
            assert isinstance(result, dict)
            for lastsave_time in result.values():
                assert lastsave_time > yesterday_unix_time

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_append(self, glide_client: TGlideClient):
        key, value = get_random_string(10), get_random_string(5)
        assert await glide_client.append(key, value) == 5

        assert await glide_client.append(key, value) == 10
        assert await glide_client.get(key) == (value * 2).encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xadd_xtrim_xlen(self, glide_client: TGlideClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        field, field2 = get_random_string(10), get_random_string(10)

        assert (
            await glide_client.xadd(
                key,
                [(field, "foo"), (field2, "bar")],
                StreamAddOptions(make_stream=False),
            )
            is None
        )

        assert (
            await glide_client.xadd(
                key, [(field, "foo1"), (field2, "bar1")], StreamAddOptions(id="0-1")
            )
            == b"0-1"
        )

        assert (
            await glide_client.xadd(key, [(field, "foo2"), (field2, "bar2")])
        ) is not None
        assert await glide_client.xlen(key) == 2

        # This will trim the first entry.
        id = await glide_client.xadd(
            key,
            [(field, "foo3"), (field2, "bar3")],
            StreamAddOptions(trim=TrimByMaxLen(exact=True, threshold=2)),
        )

        assert id is not None
        # TODO: remove when functions API is fixed
        assert isinstance(id, bytes)
        assert await glide_client.xlen(key) == 2

        # This will trim the 2nd entry.
        assert (
            await glide_client.xadd(
                key,
                [(field, "foo4"), (field2, "bar4")],
                StreamAddOptions(trim=TrimByMinId(exact=True, threshold=id.decode())),
            )
            is not None
        )
        assert await glide_client.xlen(key) == 2

        assert await glide_client.xtrim(key, TrimByMaxLen(threshold=1, exact=True)) == 1
        assert await glide_client.xlen(key) == 1

        assert await glide_client.xtrim(key, TrimByMaxLen(threshold=0, exact=True)) == 1
        # Unlike other Valkey collection types, stream keys still exist even after removing all entries
        assert await glide_client.exists([key]) == 1
        assert await glide_client.xlen(key) == 0

        assert (
            await glide_client.xtrim(
                non_existing_key, TrimByMaxLen(threshold=1, exact=True)
            )
            == 0
        )
        assert await glide_client.xlen(non_existing_key) == 0

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await glide_client.xtrim(string_key, TrimByMaxLen(threshold=1, exact=True))
        with pytest.raises(RequestError):
            await glide_client.xlen(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xdel(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        stream_id1 = "0-1"
        stream_id2 = "0-2"
        stream_id3 = "0-3"

        assert (
            await glide_client.xadd(
                key1, [("f1", "foo1"), ("f2", "foo2")], StreamAddOptions(stream_id1)
            )
            == stream_id1.encode()
        )
        assert (
            await glide_client.xadd(
                key1, [("f1", "foo1"), ("f2", "foo2")], StreamAddOptions(stream_id2)
            )
            == stream_id2.encode()
        )
        assert await glide_client.xlen(key1) == 2

        # deletes one stream id, and ignores anything invalid
        assert await glide_client.xdel(key1, [stream_id1, stream_id3]) == 1
        assert await glide_client.xdel(non_existing_key, [stream_id3]) == 0

        # invalid argument - id list should not be empty
        with pytest.raises(RequestError):
            await glide_client.xdel(key1, [])

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xdel(string_key, [stream_id3])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xrange_and_xrevrange(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        stream_id1 = "0-1"
        stream_id2 = "0-2"
        stream_id3 = "0-3"

        assert (
            await glide_client.xadd(
                key, [("f1", "v1")], StreamAddOptions(id=stream_id1)
            )
            == stream_id1.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f2", "v2")], StreamAddOptions(id=stream_id2)
            )
            == stream_id2.encode()
        )
        assert await glide_client.xlen(key) == 2

        # get everything from the stream
        result = await glide_client.xrange(key, MinId(), MaxId())
        assert convert_bytes_to_string_object(result) == {
            stream_id1: [["f1", "v1"]],
            stream_id2: [["f2", "v2"]],
        }
        result = await glide_client.xrevrange(key, MaxId(), MinId())
        assert convert_bytes_to_string_object(result) == {
            stream_id2: [["f2", "v2"]],
            stream_id1: [["f1", "v1"]],
        }

        # returns empty mapping if + before -
        assert await glide_client.xrange(key, MaxId(), MinId()) == {}
        # rev search returns empty mapping if - before +
        assert await glide_client.xrevrange(key, MinId(), MaxId()) == {}

        assert (
            await glide_client.xadd(
                key, [("f3", "v3")], StreamAddOptions(id=stream_id3)
            )
            == stream_id3.encode()
        )

        # get the newest entry
        result = await glide_client.xrange(
            key, ExclusiveIdBound(stream_id2), ExclusiveIdBound.from_timestamp(5), 1
        )
        assert convert_bytes_to_string_object(result) == {stream_id3: [["f3", "v3"]]}
        result = await glide_client.xrevrange(
            key, ExclusiveIdBound.from_timestamp(5), ExclusiveIdBound(stream_id2), 1
        )
        assert convert_bytes_to_string_object(result) == {stream_id3: [["f3", "v3"]]}

        # xrange/xrevrange against an emptied stream
        assert await glide_client.xdel(key, [stream_id1, stream_id2, stream_id3]) == 3
        assert await glide_client.xrange(key, MinId(), MaxId(), 10) == {}
        assert await glide_client.xrevrange(key, MaxId(), MinId(), 10) == {}

        assert await glide_client.xrange(non_existing_key, MinId(), MaxId()) == {}
        assert await glide_client.xrevrange(non_existing_key, MaxId(), MinId()) == {}

        # count value < 1 returns None
        assert await glide_client.xrange(key, MinId(), MaxId(), 0) is None
        assert await glide_client.xrange(key, MinId(), MaxId(), -1) is None
        assert await glide_client.xrevrange(key, MaxId(), MinId(), 0) is None
        assert await glide_client.xrevrange(key, MaxId(), MinId(), -1) is None

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await glide_client.xrange(string_key, MinId(), MaxId())
        with pytest.raises(RequestError):
            await glide_client.xrevrange(string_key, MaxId(), MinId())

        # invalid start bound
        with pytest.raises(RequestError):
            await glide_client.xrange(key, IdBound("not_a_stream_id"), MaxId())
        with pytest.raises(RequestError):
            await glide_client.xrevrange(key, MaxId(), IdBound("not_a_stream_id"))

        # invalid end bound
        with pytest.raises(RequestError):
            await glide_client.xrange(key, MinId(), IdBound("not_a_stream_id"))
        with pytest.raises(RequestError):
            await glide_client.xrevrange(key, IdBound("not_a_stream_id"), MinId())

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xread(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
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
            await glide_client.xadd(
                key1, [("f1_1", "v1_1")], StreamAddOptions(id=stream_id1_1)
            )
            == stream_id1_1.encode()
        )
        assert (
            await glide_client.xadd(
                key2, [("f2_1", "v2_1")], StreamAddOptions(id=stream_id2_1)
            )
            == stream_id2_1.encode()
        )

        # setup second entries in streams key1 and key2
        assert (
            await glide_client.xadd(
                key1, [("f1_2", "v1_2")], StreamAddOptions(id=stream_id1_2)
            )
            == stream_id1_2.encode()
        )
        assert (
            await glide_client.xadd(
                key2, [("f2_2", "v2_2")], StreamAddOptions(id=stream_id2_2)
            )
            == stream_id2_2.encode()
        )

        # setup third entries in streams key1 and key2
        assert (
            await glide_client.xadd(
                key1, [("f1_3", "v1_3")], StreamAddOptions(id=stream_id1_3)
            )
            == stream_id1_3.encode()
        )
        assert (
            await glide_client.xadd(
                key2, [("f2_3", "v2_3")], StreamAddOptions(id=stream_id2_3)
            )
            == stream_id2_3.encode()
        )

        assert await glide_client.xread({key1: stream_id1_1, key2: stream_id2_1}) == {
            key1.encode(): {
                stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
                stream_id1_3.encode(): [[b"f1_3", b"v1_3"]],
            },
            key2.encode(): {
                stream_id2_2.encode(): [[b"f2_2", b"v2_2"]],
                stream_id2_3.encode(): [[b"f2_3", b"v2_3"]],
            },
        }

        assert await glide_client.xread({non_existing_key: stream_id1_1}) is None
        assert await glide_client.xread({key1: non_existing_id}) is None

        # passing an empty read options argument has no effect
        assert await glide_client.xread({key1: stream_id1_1}, StreamReadOptions()) == {
            key1.encode(): {
                stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
                stream_id1_3.encode(): [[b"f1_3", b"v1_3"]],
            },
        }

        assert await glide_client.xread(
            {key1: stream_id1_1}, StreamReadOptions(count=1)
        ) == {
            key1.encode(): {
                stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
            },
        }
        assert await glide_client.xread(
            {key1: stream_id1_1}, StreamReadOptions(count=1, block_ms=1000)
        ) == {
            key1.encode(): {
                stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
            },
        }

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xread_edge_cases_and_failures(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
    ):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        string_key = f"{{testKey}}:2-{get_random_string(10)}"
        stream_id0 = "0-0"
        stream_id1 = "1-1"
        stream_id2 = "1-2"

        assert (
            await glide_client.xadd(
                key1, [("f1", "v1")], StreamAddOptions(id=stream_id1)
            )
            == stream_id1.encode()
        )
        assert (
            await glide_client.xadd(
                key1, [("f2", "v2")], StreamAddOptions(id=stream_id2)
            )
            == stream_id2.encode()
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
        assert await glide_client.xread(
            {key1: stream_id0}, StreamReadOptions(count=0)
        ) == {
            key1.encode(): {
                stream_id1.encode(): [[b"f1", b"v1"]],
                stream_id2.encode(): [[b"f2", b"v2"]],
            },
        }
        assert await glide_client.xread(
            {key1: stream_id0}, StreamReadOptions(count=-1)
        ) == {
            key1.encode(): {
                stream_id1.encode(): [[b"f1", b"v1"]],
                stream_id2.encode(): [[b"f2", b"v2"]],
            },
        }

        # invalid stream ID
        with pytest.raises(RequestError):
            await glide_client.xread({key1: "invalid_stream_id"})

        # invalid argument - block cannot be negative
        with pytest.raises(RequestError):
            await glide_client.xread({key1: stream_id1}, StreamReadOptions(block_ms=-1))

        # invalid argument - keys_and_ids must not be empty
        with pytest.raises(RequestError):
            await glide_client.xread({})

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await glide_client.xread({string_key: stream_id1, key1: stream_id1})
        with pytest.raises(RequestError):
            await glide_client.xread({key1: stream_id1, string_key: stream_id1})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xgroup_create_xgroup_destroy(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
    ):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        group_name1 = get_random_string(10)
        group_name2 = get_random_string(10)
        stream_id = "0-1"

        # trying to create a consumer group for a non-existing stream without the "MKSTREAM" arg results in error
        with pytest.raises(RequestError):
            await glide_client.xgroup_create(non_existing_key, group_name1, stream_id)

        # calling with the "MKSTREAM" arg should create the new stream automatically
        assert (
            await glide_client.xgroup_create(
                key, group_name1, stream_id, StreamGroupOptions(make_stream=True)
            )
            == OK
        )

        # invalid arg - group names must be unique, but group_name1 already exists
        with pytest.raises(RequestError):
            await glide_client.xgroup_create(key, group_name1, stream_id)

        # invalid stream ID format
        with pytest.raises(RequestError):
            await glide_client.xgroup_create(
                key, group_name2, "invalid_stream_id_format"
            )

        assert await glide_client.xgroup_destroy(key, group_name1) is True
        # calling xgroup_destroy again returns False because the group was already destroyed above
        assert await glide_client.xgroup_destroy(key, group_name1) is False

        # attempting to destroy a group for a non-existing key should raise an error
        with pytest.raises(RequestError):
            await glide_client.xgroup_destroy(non_existing_key, group_name1)

        # "ENTRIESREAD" option was added in Valkey 7.0.0
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            with pytest.raises(RequestError):
                await glide_client.xgroup_create(
                    key,
                    group_name1,
                    stream_id,
                    StreamGroupOptions(entries_read=10),
                )
        else:
            assert (
                await glide_client.xgroup_create(
                    key,
                    group_name1,
                    stream_id,
                    StreamGroupOptions(entries_read=10),
                )
                == OK
            )

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xgroup_create(
                string_key, group_name1, stream_id, StreamGroupOptions(make_stream=True)
            )
        with pytest.raises(RequestError):
            await glide_client.xgroup_destroy(string_key, group_name1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xgroup_create_consumer_xreadgroup_xgroup_del_consumer(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
    ):
        key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        group_name = get_random_string(10)
        consumer_name = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"
        stream_id1_3 = "1-3"

        # create group and consumer for the group
        assert (
            await glide_client.xgroup_create(
                key, group_name, stream_id0, StreamGroupOptions(make_stream=True)
            )
            == OK
        )
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer_name)
            is True
        )

        # attempting to create/delete a consumer for a group that does not exist results in a NOGROUP request error
        with pytest.raises(RequestError):
            await glide_client.xgroup_create_consumer(
                key, "non_existing_group", consumer_name
            )
        with pytest.raises(RequestError):
            await glide_client.xgroup_del_consumer(
                key, "non_existing_group", consumer_name
            )

        # attempt to create consumer for group again
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer_name)
            is False
        )

        # attempting to delete a consumer that has not been created yet returns 0
        assert (
            await glide_client.xgroup_del_consumer(
                key, group_name, "non_existing_consumer"
            )
            == 0
        )

        # add two stream entries
        assert (
            await glide_client.xadd(
                key, [("f1_0", "v1_0")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_1", "v1_1")], StreamAddOptions(stream_id1_1)
            )
            == stream_id1_1.encode()
        )

        # read the entire stream for the consumer and mark messages as pending
        assert await glide_client.xreadgroup(
            {key: ">"},
            group_name,
            consumer_name,
            StreamReadGroupOptions(block_ms=1000, count=10),
        ) == {
            key.encode(): {
                stream_id1_0.encode(): [[b"f1_0", b"v1_0"]],
                stream_id1_1.encode(): [[b"f1_1", b"v1_1"]],
            }
        }

        # delete one of the stream entries
        assert await glide_client.xdel(key, [stream_id1_0]) == 1

        # now xreadgroup yields one empty stream entry and one non-empty stream entry
        assert await glide_client.xreadgroup({key: "0"}, group_name, consumer_name) == {
            key.encode(): {
                stream_id1_0.encode(): None,
                stream_id1_1.encode(): [[b"f1_1", b"v1_1"]],
            }
        }

        assert (
            await glide_client.xadd(
                key, [("f1_2", "v1_2")], StreamAddOptions(stream_id1_2)
            )
            == stream_id1_2.encode()
        )

        # delete the consumer group and expect 2 pending messages
        assert (
            await glide_client.xgroup_del_consumer(key, group_name, consumer_name) == 2
        )

        # consume the last message with the previously deleted consumer (create the consumer anew)
        assert await glide_client.xreadgroup(
            {key: ">"},
            group_name,
            consumer_name,
            StreamReadGroupOptions(count=5, block_ms=1000),
        ) == {key.encode(): {stream_id1_2.encode(): [[b"f1_2", b"v1_2"]]}}

        # delete the consumer group and expect the pending message
        assert (
            await glide_client.xgroup_del_consumer(key, group_name, consumer_name) == 1
        )

        # test NOACK option
        assert (
            await glide_client.xadd(
                key, [("f1_3", "v1_3")], StreamAddOptions(stream_id1_3)
            )
            == stream_id1_3.encode()
        )
        # since NOACK is passed, stream entry will be consumed without being added to the pending entries
        assert await glide_client.xreadgroup(
            {key: ">"},
            group_name,
            consumer_name,
            StreamReadGroupOptions(no_ack=True, count=5, block_ms=1000),
        ) == {key.encode(): {stream_id1_3.encode(): [[b"f1_3", b"v1_3"]]}}
        assert (
            await glide_client.xreadgroup(
                {key: ">"},
                group_name,
                consumer_name,
                StreamReadGroupOptions(no_ack=False, count=5, block_ms=1000),
            )
            is None
        )
        assert await glide_client.xreadgroup(
            {key: "0"},
            group_name,
            consumer_name,
            StreamReadGroupOptions(no_ack=False, count=5, block_ms=1000),
        ) == {key.encode(): {}}

        # attempting to call XGROUP CREATECONSUMER or XGROUP DELCONSUMER with a non-existing key should raise an error
        with pytest.raises(RequestError):
            await glide_client.xgroup_create_consumer(
                non_existing_key, group_name, consumer_name
            )
        with pytest.raises(RequestError):
            await glide_client.xgroup_del_consumer(
                non_existing_key, group_name, consumer_name
            )

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xgroup_create_consumer(
                string_key, group_name, consumer_name
            )
        with pytest.raises(RequestError):
            await glide_client.xgroup_del_consumer(
                string_key, group_name, consumer_name
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xreadgroup_edge_cases_and_failures(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
    ):
        key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        group_name = get_random_string(10)
        consumer_name = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"

        # attempting to execute against a non-existing key results in an error
        with pytest.raises(RequestError):
            await glide_client.xreadgroup(
                {non_existing_key: stream_id0}, group_name, consumer_name
            )

        # create group and consumer for group
        assert await glide_client.xgroup_create(
            key, group_name, stream_id0, StreamGroupOptions(make_stream=True)
        )
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer_name)
            is True
        )

        # read from empty stream
        assert (
            await glide_client.xreadgroup({key: ">"}, group_name, consumer_name) is None
        )
        assert await glide_client.xreadgroup({key: "0"}, group_name, consumer_name) == {
            key.encode(): {}
        }

        # setup first entry
        assert (
            await glide_client.xadd(key, [("f1", "v1")], StreamAddOptions(stream_id1_1))
            == stream_id1_1.encode()
        )

        # if count is non-positive, it is ignored
        assert await glide_client.xreadgroup(
            {key: ">"}, group_name, consumer_name, StreamReadGroupOptions(count=0)
        ) == {
            key.encode(): {
                stream_id1_1.encode(): [[b"f1", b"v1"]],
            },
        }
        assert await glide_client.xreadgroup(
            {key: stream_id1_0},
            group_name,
            consumer_name,
            StreamReadGroupOptions(count=-1),
        ) == {
            key.encode(): {
                stream_id1_1.encode(): [[b"f1", b"v1"]],
            },
        }

        # invalid stream ID
        with pytest.raises(RequestError):
            await glide_client.xreadgroup(
                {key: "invalid_stream_id"}, group_name, consumer_name
            )

        # invalid argument - block cannot be negative
        with pytest.raises(RequestError):
            await glide_client.xreadgroup(
                {key: stream_id0},
                group_name,
                consumer_name,
                StreamReadGroupOptions(block_ms=-1),
            )

        # invalid argument - keys_and_ids must not be empty
        with pytest.raises(RequestError):
            await glide_client.xreadgroup({}, group_name, consumer_name)

        # first key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xreadgroup(
                {string_key: stream_id1_1, key: stream_id1_1}, group_name, consumer_name
            )

        # second key exists, but it is not a stream
        with pytest.raises(RequestError):
            await glide_client.xreadgroup(
                {key: stream_id1_1, string_key: stream_id1_1}, group_name, consumer_name
            )

        # attempting to execute command with a non-existing group results in an error
        with pytest.raises(RequestError):
            await glide_client.xreadgroup(
                {key: stream_id1_1}, "non_existing_group", consumer_name
            )

        test_client = await create_client(
            request=request, protocol=protocol, cluster_mode=cluster_mode, timeout=900
        )
        timeout_key = f"{{testKey}}:{get_random_string(10)}"
        timeout_group_name = get_random_string(10)
        timeout_consumer_name = get_random_string(10)

        # create a group read with the test client
        # add a single stream entry and consumer
        # the first call to ">" will return and update consumer group
        # the second call to ">" will block waiting for new entries
        # using anything other than ">" won't block, but will return the empty consumer result
        # see: https://github.com/redis/redis/issues/6587
        assert (
            await test_client.xgroup_create(
                timeout_key,
                timeout_group_name,
                stream_id0,
                StreamGroupOptions(make_stream=True),
            )
            == OK
        )
        assert (
            await test_client.xgroup_create_consumer(
                timeout_key, timeout_group_name, timeout_consumer_name
            )
            is True
        )
        assert (
            await test_client.xadd(
                timeout_key, [("f1", "v1")], StreamAddOptions(stream_id1_1)
            )
            == stream_id1_1.encode()
        )

        # read the entire stream for the consumer and mark messages as pending
        assert await test_client.xreadgroup(
            {timeout_key: ">"}, timeout_group_name, timeout_consumer_name
        ) == {timeout_key.encode(): {stream_id1_1.encode(): [[b"f1", b"v1"]]}}

        # subsequent calls to read ">" will block
        assert (
            await test_client.xreadgroup(
                {timeout_key: ">"},
                timeout_group_name,
                timeout_consumer_name,
                StreamReadGroupOptions(block_ms=1000),
            )
            is None
        )

        # ensure that command doesn't time out even if timeout > request timeout
        async def endless_xreadgroup_call():
            await test_client.xreadgroup(
                {timeout_key: ">"},
                timeout_group_name,
                timeout_consumer_name,
                StreamReadGroupOptions(block_ms=0),
            )

        # when xreadgroup is called with a block timeout of 0, it should never timeout, but we wrap the test with a
        # timeout to avoid the test getting stuck forever.
        with pytest.raises(asyncio.TimeoutError):
            await asyncio.wait_for(endless_xreadgroup_call(), timeout=3)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xack(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
    ):
        key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        group_name = get_random_string(10)
        consumer_name = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"

        # setup: add 2 entries to the stream, create consumer group, read to mark them as pending
        assert (
            await glide_client.xadd(key, [("f0", "v0")], StreamAddOptions(stream_id1_0))
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(key, [("f1", "v1")], StreamAddOptions(stream_id1_1))
            == stream_id1_1.encode()
        )
        assert await glide_client.xgroup_create(key, group_name, stream_id0) == OK
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer_name) == {
            key.encode(): {
                stream_id1_0.encode(): [[b"f0", b"v0"]],
                stream_id1_1.encode(): [[b"f1", b"v1"]],
            }
        }

        # add one more entry
        assert (
            await glide_client.xadd(key, [("f2", "v2")], StreamAddOptions(stream_id1_2))
            == stream_id1_2.encode()
        )

        # acknowledge the first 2 entries
        assert (
            await glide_client.xack(key, group_name, [stream_id1_0, stream_id1_1]) == 2
        )
        # attempting to acknowledge the first 2 entries again returns 0 since they were already acknowledged
        assert (
            await glide_client.xack(key, group_name, [stream_id1_0, stream_id1_1]) == 0
        )
        # read the last, unacknowledged entry
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer_name) == {
            key.encode(): {stream_id1_2.encode(): [[b"f2", b"v2"]]}
        }
        # deleting the consumer returns 1 since the last entry still hasn't been acknowledged
        assert (
            await glide_client.xgroup_del_consumer(key, group_name, consumer_name) == 1
        )

        # attempting to acknowledge a non-existing key returns 0
        assert (
            await glide_client.xack(non_existing_key, group_name, [stream_id1_0]) == 0
        )
        # attempting to acknowledge a non-existing group returns 0
        assert await glide_client.xack(key, "non_existing_group", [stream_id1_0]) == 0
        # attempting to acknowledge a non-existing ID returns 0
        assert await glide_client.xack(key, group_name, ["99-99"]) == 0

        # invalid arg - ID list must not be empty
        with pytest.raises(RequestError):
            await glide_client.xack(key, group_name, [])

        # invalid arg - invalid stream ID format
        with pytest.raises(RequestError):
            await glide_client.xack(key, group_name, ["invalid_ID_format"])

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xack(string_key, group_name, [stream_id1_0])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xpending_xclaim(self, glide_client: TGlideClient):
        key = get_random_string(10)
        group_name = get_random_string(10)
        consumer1 = get_random_string(10)
        consumer2 = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"
        stream_id1_3 = "1-3"
        stream_id1_4 = "1-4"
        stream_id1_5 = "1-5"

        # create group and consumer for group
        assert (
            await glide_client.xgroup_create(
                key, group_name, stream_id0, StreamGroupOptions(make_stream=True)
            )
            == OK
        )
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer1)
            is True
        )
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer2)
            is True
        )

        # add two stream entries for consumer1
        assert (
            await glide_client.xadd(
                key, [("f1_0", "v1_0")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_1", "v1_1")], StreamAddOptions(stream_id1_1)
            )
            == stream_id1_1.encode()
        )

        # read the entire stream with consumer1 and mark messages as pending
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer1) == {
            key.encode(): {
                stream_id1_0.encode(): [[b"f1_0", b"v1_0"]],
                stream_id1_1.encode(): [[b"f1_1", b"v1_1"]],
            }
        }

        # add three stream entries for consumer2
        assert (
            await glide_client.xadd(
                key, [("f1_2", "v1_2")], StreamAddOptions(stream_id1_2)
            )
            == stream_id1_2.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_3", "v1_3")], StreamAddOptions(stream_id1_3)
            )
            == stream_id1_3.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_4", "v1_4")], StreamAddOptions(stream_id1_4)
            )
            == stream_id1_4.encode()
        )

        # read the entire stream with consumer2 and mark messages as pending
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer2) == {
            key.encode(): {
                stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
                stream_id1_3.encode(): [[b"f1_3", b"v1_3"]],
                stream_id1_4.encode(): [[b"f1_4", b"v1_4"]],
            }
        }

        # inner array order is non-deterministic, so we have to assert against it separately from the other info
        result = await glide_client.xpending(key, group_name)
        consumer_results = cast(List, result[3])
        assert [consumer1.encode(), b"2"] in consumer_results
        assert [consumer2.encode(), b"3"] in consumer_results

        result.remove(consumer_results)
        assert result == [5, stream_id1_0.encode(), stream_id1_4.encode()]

        # to ensure an idle_time > 0
        time.sleep(2)
        range_result = await glide_client.xpending_range(
            key, group_name, MinId(), MaxId(), 10
        )
        # the inner lists of the result have format [stream_entry_id, consumer, idle_time, times_delivered]
        # because the idle time return value is not deterministic, we have to assert against it separately
        idle_time = cast(int, range_result[0][2])
        assert idle_time > 0
        range_result[0].remove(idle_time)
        assert range_result[0] == [stream_id1_0.encode(), consumer1.encode(), 1]

        idle_time = cast(int, range_result[1][2])
        assert idle_time > 0
        range_result[1].remove(idle_time)
        assert range_result[1] == [stream_id1_1.encode(), consumer1.encode(), 1]

        idle_time = cast(int, range_result[2][2])
        assert idle_time > 0
        range_result[2].remove(idle_time)
        assert range_result[2] == [stream_id1_2.encode(), consumer2.encode(), 1]

        idle_time = cast(int, range_result[3][2])
        assert idle_time > 0
        range_result[3].remove(idle_time)
        assert range_result[3] == [stream_id1_3.encode(), consumer2.encode(), 1]

        idle_time = cast(int, range_result[4][2])
        assert idle_time > 0
        range_result[4].remove(idle_time)
        assert range_result[4] == [stream_id1_4.encode(), consumer2.encode(), 1]

        # use xclaim to claim stream 2 and 4 for consumer 1
        assert await glide_client.xclaim(
            key, group_name, consumer1, 0, [stream_id1_2, stream_id1_4]
        ) == {
            stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
            stream_id1_4.encode(): [[b"f1_4", b"v1_4"]],
        }

        # claiming non exists id
        assert (
            await glide_client.xclaim(
                key, group_name, consumer1, 0, ["1526569498055-0"]
            )
            == {}
        )

        assert await glide_client.xclaim_just_id(
            key, group_name, consumer1, 0, [stream_id1_2, stream_id1_4]
        ) == [stream_id1_2.encode(), stream_id1_4.encode()]

        # add one more stream
        assert (
            await glide_client.xadd(
                key, [("f1_5", "v1_5")], StreamAddOptions(stream_id1_5)
            )
            == stream_id1_5.encode()
        )

        # using force, we can xclaim the message without reading it
        claim_force_result = await glide_client.xclaim(
            key,
            group_name,
            consumer2,
            0,
            [stream_id1_5],
            StreamClaimOptions(retry_count=99, is_force=True),
        )
        assert claim_force_result == {stream_id1_5.encode(): [[b"f1_5", b"v1_5"]]}

        force_pending_result = await glide_client.xpending_range(
            key, group_name, IdBound(stream_id1_5), IdBound(stream_id1_5), 1
        )
        assert force_pending_result[0][0] == stream_id1_5.encode()
        assert force_pending_result[0][1] == consumer2.encode()
        assert force_pending_result[0][3] == 99

        # acknowledge streams 1-1, 1-2, 1-3, 1-5 and remove them from the xpending results
        assert (
            await glide_client.xack(
                key,
                group_name,
                [stream_id1_1, stream_id1_2, stream_id1_3, stream_id1_5],
            )
            == 4
        )

        range_result = await glide_client.xpending_range(
            key, group_name, IdBound(stream_id1_4), MaxId(), 10
        )
        assert len(range_result) == 1
        assert range_result[0][0] == stream_id1_4.encode()
        assert range_result[0][1] == consumer1.encode()

        range_result = await glide_client.xpending_range(
            key, group_name, MinId(), IdBound(stream_id1_3), 10
        )
        assert len(range_result) == 1
        assert range_result[0][0] == stream_id1_0.encode()
        assert range_result[0][1] == consumer1.encode()

        # passing an empty StreamPendingOptions object should have no effect
        range_result = await glide_client.xpending_range(
            key, group_name, MinId(), IdBound(stream_id1_3), 10, StreamPendingOptions()
        )
        assert len(range_result) == 1
        assert range_result[0][0] == stream_id1_0.encode()
        assert range_result[0][1] == consumer1.encode()

        range_result = await glide_client.xpending_range(
            key,
            group_name,
            MinId(),
            MaxId(),
            10,
            StreamPendingOptions(min_idle_time_ms=1, consumer_name=consumer1),
        )
        # note: streams ID 0-0 and 0-4 are still pending, all others were acknowledged
        assert len(range_result) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xpending_edge_cases_and_failures(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        group_name = get_random_string(10)
        consumer = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"

        # create group and consumer for the group
        assert (
            await glide_client.xgroup_create(
                key, group_name, stream_id0, StreamGroupOptions(make_stream=True)
            )
            == OK
        )
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer) is True
        )

        # add two stream entries for consumer
        assert (
            await glide_client.xadd(
                key, [("f1_0", "v1_0")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_1", "v1_1")], StreamAddOptions(stream_id1_1)
            )
            == stream_id1_1.encode()
        )

        # no pending messages yet...
        assert await glide_client.xpending(key, group_name) == [0, None, None, None]
        assert (
            await glide_client.xpending_range(key, group_name, MinId(), MaxId(), 10)
            == []
        )

        # read the entire stream with consumer and mark messages as pending
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer) == {
            key.encode(): {
                stream_id1_0.encode(): [[b"f1_0", b"v1_0"]],
                stream_id1_1.encode(): [[b"f1_1", b"v1_1"]],
            }
        }

        # sanity check - expect some results
        assert await glide_client.xpending(key, group_name) == [
            2,
            stream_id1_0.encode(),
            stream_id1_1.encode(),
            [[consumer.encode(), b"2"]],
        ]
        result = await glide_client.xpending_range(
            key, group_name, MinId(), MaxId(), 10
        )
        assert len(result[0]) > 0

        # returns empty if + before -
        assert (
            await glide_client.xpending_range(key, group_name, MaxId(), MinId(), 10)
            == []
        )
        assert (
            await glide_client.xpending_range(
                key,
                group_name,
                MaxId(),
                MinId(),
                10,
                StreamPendingOptions(consumer_name=consumer),
            )
            == []
        )

        # min idle time of 100 seconds shouldn't produce any results
        assert (
            await glide_client.xpending_range(
                key,
                group_name,
                MinId(),
                MaxId(),
                10,
                StreamPendingOptions(min_idle_time_ms=100_000),
            )
            == []
        )

        # non-existing consumer: no results
        assert (
            await glide_client.xpending_range(
                key,
                group_name,
                MinId(),
                MaxId(),
                10,
                StreamPendingOptions(consumer_name="non_existing_consumer"),
            )
            == []
        )

        # xpending when range bound is not a valid ID raises a RequestError
        with pytest.raises(RequestError):
            await glide_client.xpending_range(
                key, group_name, IdBound("invalid_stream_id_format"), MaxId(), 10
            )
        with pytest.raises(RequestError):
            await glide_client.xpending_range(
                key, group_name, MinId(), IdBound("invalid_stream_id_format"), 10
            )

        # non-positive count returns no results
        assert (
            await glide_client.xpending_range(key, group_name, MinId(), MaxId(), -10)
            == []
        )
        assert (
            await glide_client.xpending_range(key, group_name, MinId(), MaxId(), 0)
            == []
        )

        # non-positive min-idle-time values are allowed
        result = await glide_client.xpending_range(
            key,
            group_name,
            MinId(),
            MaxId(),
            10,
            StreamPendingOptions(min_idle_time_ms=-100),
        )
        assert len(result[0]) > 0
        result = await glide_client.xpending_range(
            key,
            group_name,
            MinId(),
            MaxId(),
            10,
            StreamPendingOptions(min_idle_time_ms=0),
        )
        assert len(result[0]) > 0

        # non-existing group name raises a RequestError (NOGROUP)
        with pytest.raises(RequestError):
            await glide_client.xpending(key, "non_existing_group")
        with pytest.raises(RequestError):
            await glide_client.xpending_range(
                key, "non_existing_group", MinId(), MaxId(), 10
            )

        # non-existing key raises a RequestError
        with pytest.raises(RequestError):
            await glide_client.xpending(non_existing_key, group_name)
        with pytest.raises(RequestError):
            await glide_client.xpending_range(
                non_existing_key, group_name, MinId(), MaxId(), 10
            )

        # key exists but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xpending(string_key, group_name)
        with pytest.raises(RequestError):
            await glide_client.xpending_range(
                string_key, group_name, MinId(), MaxId(), 10
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xclaim_edge_cases_and_failures(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        string_key = get_random_string(10)
        group_name = get_random_string(10)
        consumer = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"

        # create group and consumer for the group
        assert (
            await glide_client.xgroup_create(
                key, group_name, stream_id0, StreamGroupOptions(make_stream=True)
            )
            == OK
        )
        assert (
            await glide_client.xgroup_create_consumer(key, group_name, consumer) is True
        )

        # Add stream entry and mark as pending:
        assert (
            await glide_client.xadd(
                key, [("f1_0", "v1_0")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )

        # read the entire stream with consumer and mark messages as pending
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer) == {
            key.encode(): {stream_id1_0.encode(): [[b"f1_0", b"v1_0"]]}
        }

        # claim with invalid stream entry IDs
        with pytest.raises(RequestError):
            await glide_client.xclaim_just_id(key, group_name, consumer, 1, ["invalid"])

        # claim with empty stream entry IDs returns no results
        empty_claim = await glide_client.xclaim_just_id(
            key, group_name, consumer, 1, []
        )
        assert len(empty_claim) == 0

        claim_options = StreamClaimOptions(idle=1)

        # non-existent key throws a RequestError (NOGROUP)
        with pytest.raises(RequestError) as e:
            await glide_client.xclaim(
                non_existing_key, group_name, consumer, 1, [stream_id1_0]
            )
        assert "NOGROUP" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.xclaim(
                non_existing_key,
                group_name,
                consumer,
                1,
                [stream_id1_0],
                claim_options,
            )
        assert "NOGROUP" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.xclaim_just_id(
                non_existing_key, group_name, consumer, 1, [stream_id1_0]
            )
        assert "NOGROUP" in str(e)

        with pytest.raises(RequestError) as e:
            await glide_client.xclaim_just_id(
                non_existing_key,
                group_name,
                consumer,
                1,
                [stream_id1_0],
                claim_options,
            )
        assert "NOGROUP" in str(e)

        # key exists but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xclaim(
                string_key, group_name, consumer, 1, [stream_id1_0]
            )
        with pytest.raises(RequestError):
            await glide_client.xclaim(
                string_key, group_name, consumer, 1, [stream_id1_0], claim_options
            )
        with pytest.raises(RequestError):
            await glide_client.xclaim_just_id(
                string_key, group_name, consumer, 1, [stream_id1_0]
            )
        with pytest.raises(RequestError):
            await glide_client.xclaim_just_id(
                string_key, group_name, consumer, 1, [stream_id1_0], claim_options
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xautoclaim(self, glide_client: TGlideClient, protocol):
        min_version = "6.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        if await check_if_server_version_lt(glide_client, "7.0.0"):
            version7_or_above = False
        else:
            version7_or_above = True

        key = get_random_string(10)
        group_name = get_random_string(10)
        consumer = get_random_string(10)
        stream_id0_0 = "0-0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"
        stream_id1_3 = "1-3"

        # setup: add stream entries, create consumer group, add entries to Pending Entries List for group
        assert (
            await glide_client.xadd(
                key, [("f1", "v1"), ("f2", "v2")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_1", "v1_1")], StreamAddOptions(stream_id1_1)
            )
            == stream_id1_1.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_2", "v1_2")], StreamAddOptions(stream_id1_2)
            )
            == stream_id1_2.encode()
        )
        assert (
            await glide_client.xadd(
                key, [("f1_3", "v1_3")], StreamAddOptions(stream_id1_3)
            )
            == stream_id1_3.encode()
        )
        assert await glide_client.xgroup_create(key, group_name, stream_id0_0) == OK
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer) == {
            key.encode(): {
                stream_id1_0.encode(): [[b"f1", b"v1"], [b"f2", b"v2"]],
                stream_id1_1.encode(): [[b"f1_1", b"v1_1"]],
                stream_id1_2.encode(): [[b"f1_2", b"v1_2"]],
                stream_id1_3.encode(): [[b"f1_3", b"v1_3"]],
            }
        }

        # autoclaim the first entry only
        result = await glide_client.xautoclaim(
            key, group_name, consumer, 0, stream_id0_0, count=1
        )
        assert result[0] == stream_id1_1.encode()
        assert result[1] == {stream_id1_0.encode(): [[b"f1", b"v1"], [b"f2", b"v2"]]}
        # if using Valkey 7.0.0 or above, responses also include a list of entry IDs that were removed from the Pending
        # Entries List because they no longer exist in the stream
        if version7_or_above:
            assert result[2] == []

        # delete entry 1-2
        assert await glide_client.xdel(key, [stream_id1_2])

        # autoclaim the rest of the entries
        result = await glide_client.xautoclaim(
            key, group_name, consumer, 0, stream_id1_1
        )
        assert (
            result[0] == stream_id0_0.encode()
        )  # "0-0" is returned to indicate the entire stream was scanned.
        assert result[1] == {
            stream_id1_1.encode(): [[b"f1_1", b"v1_1"]],
            stream_id1_3.encode(): [[b"f1_3", b"v1_3"]],
        }
        if version7_or_above:
            assert result[2] == [stream_id1_2.encode()]

        # autoclaim with JUSTID: result at index 1 does not contain fields/values of the claimed entries, only IDs
        just_id_result = await glide_client.xautoclaim_just_id(
            key, group_name, consumer, 0, stream_id0_0
        )
        assert just_id_result[0] == stream_id0_0.encode()
        if version7_or_above:
            assert just_id_result[1] == [
                stream_id1_0.encode(),
                stream_id1_1.encode(),
                stream_id1_3.encode(),
            ]
            assert just_id_result[2] == []
        else:
            # in Valkey < 7.0.0, specifically for XAUTOCLAIM with JUSTID, entry IDs that were in the Pending Entries List
            # but are no longer in the stream still show up in the response
            assert just_id_result[1] == [
                stream_id1_0.encode(),
                stream_id1_1.encode(),
                stream_id1_2.encode(),
                stream_id1_3.encode(),
            ]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xautoclaim_edge_cases_and_failures(
        self, glide_client: TGlideClient, protocol
    ):
        min_version = "6.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        if await check_if_server_version_lt(glide_client, "7.0.0"):
            version7_or_above = False
        else:
            version7_or_above = True

        key = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        group_name = get_random_string(10)
        consumer = get_random_string(10)
        stream_id0_0 = "0-0"
        stream_id1_0 = "1-0"

        # setup: add entry, create consumer group, add entry to Pending Entries List for group
        assert (
            await glide_client.xadd(key, [("f1", "v1")], StreamAddOptions(stream_id1_0))
            == stream_id1_0.encode()
        )
        assert await glide_client.xgroup_create(key, group_name, stream_id0_0) == OK
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer) == {
            key.encode(): {stream_id1_0.encode(): [[b"f1", b"v1"]]}
        }

        # passing a non-existing key is not allowed and will raise an error
        with pytest.raises(RequestError):
            await glide_client.xautoclaim(
                non_existing_key, group_name, consumer, 0, stream_id0_0
            )
        with pytest.raises(RequestError):
            await glide_client.xautoclaim_just_id(
                non_existing_key, group_name, consumer, 0, stream_id0_0
            )

        # passing a non-existing group is not allowed and will raise an error
        with pytest.raises(RequestError):
            await glide_client.xautoclaim(
                key, "non_existing_group", consumer, 0, stream_id0_0
            )
        with pytest.raises(RequestError):
            await glide_client.xautoclaim_just_id(
                key, "non_existing_group", consumer, 0, stream_id0_0
            )

        # non-existing consumers are created automatically
        result = await glide_client.xautoclaim(
            key, group_name, "non_existing_consumer", 0, stream_id0_0
        )
        assert result[0] == stream_id0_0.encode()
        assert result[1] == {stream_id1_0.encode(): [[b"f1", b"v1"]]}
        # if using Valkey 7.0.0 or above, responses also include a list of entry IDs that were removed from the Pending
        # Entries List because they no longer exist in the stream
        if version7_or_above:
            assert result[2] == []

        just_id_result = await glide_client.xautoclaim_just_id(
            key, group_name, "non_existing_consumer", 0, stream_id0_0
        )
        assert just_id_result[0] == stream_id0_0.encode()
        assert just_id_result[1] == [stream_id1_0.encode()]
        if version7_or_above:
            assert just_id_result[2] == []

        # negative min_idle_time_ms values are allowed
        result = await glide_client.xautoclaim(
            key, group_name, consumer, -1, stream_id0_0
        )
        assert result[0] == stream_id0_0.encode()
        assert result[1] == {stream_id1_0.encode(): [[b"f1", b"v1"]]}
        if version7_or_above:
            assert result[2] == []

        just_id_result = await glide_client.xautoclaim_just_id(
            key, group_name, consumer, -1, stream_id0_0
        )
        assert just_id_result[0] == stream_id0_0.encode()
        assert just_id_result[1] == [stream_id1_0.encode()]
        if version7_or_above:
            assert just_id_result[2] == []

        with pytest.raises(RequestError):
            await glide_client.xautoclaim(
                key, group_name, consumer, 0, "invalid_stream_id"
            )
        with pytest.raises(RequestError):
            await glide_client.xautoclaim_just_id(
                key, group_name, consumer, 0, "invalid_stream_id"
            )

        # no stream entries to claim above the given start value
        result = await glide_client.xautoclaim(key, group_name, consumer, 0, "99-99")
        assert result[0] == stream_id0_0.encode()
        assert result[1] == {}
        if version7_or_above:
            assert result[2] == []

        just_id_result = await glide_client.xautoclaim_just_id(
            key, group_name, consumer, 0, "99-99"
        )
        assert just_id_result[0] == stream_id0_0.encode()
        assert just_id_result[1] == []
        if version7_or_above:
            assert just_id_result[2] == []

        # invalid arg - count must be positive
        with pytest.raises(RequestError):
            await glide_client.xautoclaim(
                key, group_name, consumer, 0, stream_id0_0, count=0
            )
        with pytest.raises(RequestError):
            await glide_client.xautoclaim_just_id(
                key, group_name, consumer, 0, stream_id0_0, count=0
            )

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xautoclaim(
                string_key, group_name, consumer, 0, stream_id0_0
            )
        with pytest.raises(RequestError):
            await glide_client.xautoclaim_just_id(
                string_key, group_name, consumer, 0, stream_id0_0
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xinfo_groups_xinfo_consumers(
        self, glide_client: TGlideClient, protocol
    ):
        key = get_random_string(10)
        group_name1 = get_random_string(10)
        group_name2 = get_random_string(10)
        consumer1 = get_random_string(10)
        consumer2 = get_random_string(10)
        stream_id0_0 = "0-0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"
        stream_id1_3 = "1-3"

        # setup: add 3 entries to stream, create consumer group and consumer1, read 1 entry from stream with consumer1
        assert (
            await glide_client.xadd(
                key, [("f1", "v1"), ("f2", "v2")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(key, [("f3", "v3")], StreamAddOptions(stream_id1_1))
            == stream_id1_1.encode()
        )
        assert (
            await glide_client.xadd(key, [("f4", "v4")], StreamAddOptions(stream_id1_2))
            == stream_id1_2.encode()
        )
        assert await glide_client.xgroup_create(key, group_name1, stream_id0_0) == OK
        assert await glide_client.xreadgroup(
            {key: ">"}, group_name1, consumer1, StreamReadGroupOptions(count=1)
        ) == {key.encode(): {stream_id1_0.encode(): [[b"f1", b"v1"], [b"f2", b"v2"]]}}

        # sleep to ensure the idle time value and inactive time value returned by xinfo_consumers is > 0
        time.sleep(2)
        consumers_result = await glide_client.xinfo_consumers(key, group_name1)
        assert len(consumers_result) == 1
        consumer1_info = consumers_result[0]
        assert consumer1_info.get(b"name") == consumer1.encode()
        assert consumer1_info.get(b"pending") == 1
        assert cast(int, consumer1_info.get(b"idle")) > 0
        if not await check_if_server_version_lt(glide_client, "7.2.0"):
            assert (
                cast(int, consumer1_info.get(b"inactive"))
                > 0  # "inactive" was added in Valkey 7.2.0
            )

        # create consumer2 and read the rest of the entries with it
        assert (
            await glide_client.xgroup_create_consumer(key, group_name1, consumer2)
            is True
        )
        assert await glide_client.xreadgroup({key: ">"}, group_name1, consumer2) == {
            key.encode(): {
                stream_id1_1.encode(): [[b"f3", b"v3"]],
                stream_id1_2.encode(): [[b"f4", b"v4"]],
            }
        }

        # verify that xinfo_consumers contains info for 2 consumers now
        # test with byte string args
        consumers_result = await glide_client.xinfo_consumers(
            key.encode(), group_name1.encode()
        )
        assert len(consumers_result) == 2

        # add one more entry
        assert (
            await glide_client.xadd(key, [("f5", "v5")], StreamAddOptions(stream_id1_3))
            == stream_id1_3.encode()
        )

        groups = await glide_client.xinfo_groups(key)
        assert len(groups) == 1
        group1_info = groups[0]
        assert group1_info.get(b"name") == group_name1.encode()
        assert group1_info.get(b"consumers") == 2
        assert group1_info.get(b"pending") == 3
        assert group1_info.get(b"last-delivered-id") == stream_id1_2.encode()
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert (
                group1_info.get(b"entries-read")
                == 3  # we have read stream entries 1-0, 1-1, and 1-2
            )
            assert (
                group1_info.get(b"lag")
                == 1  # we still have not read one entry in the stream, entry 1-3
            )

        # verify xgroup_set_id effects the returned value from xinfo_groups
        assert await glide_client.xgroup_set_id(key, group_name1, stream_id1_1) == OK
        # test with byte string arg
        groups = await glide_client.xinfo_groups(key.encode())
        assert len(groups) == 1
        group1_info = groups[0]
        assert group1_info.get(b"name") == group_name1.encode()
        assert group1_info.get(b"consumers") == 2
        assert group1_info.get(b"pending") == 3
        assert group1_info.get(b"last-delivered-id") == stream_id1_1.encode()
        # entries-read and lag were added to the result in 7.0.0
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            assert (
                group1_info.get(b"entries-read")
                is None  # gets set to None when we change the last delivered ID
            )
            assert (
                group1_info.get(b"lag")
                is None  # gets set to None when we change the last delivered ID
            )

        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            # verify xgroup_set_id with entries_read effects the returned value from xinfo_groups
            assert (
                await glide_client.xgroup_set_id(
                    key, group_name1, stream_id1_1, entries_read=1
                )
                == OK
            )
            groups = await glide_client.xinfo_groups(key)
            assert len(groups) == 1
            group1_info = groups[0]
            assert group1_info.get(b"name") == group_name1.encode()
            assert group1_info.get(b"consumers") == 2
            assert group1_info.get(b"pending") == 3
            assert group1_info.get(b"last-delivered-id") == stream_id1_1.encode()
            assert group1_info.get(b"entries-read") == 1
            assert (
                group1_info.get(b"lag")
                == 3  # lag is calculated as number of stream entries minus entries-read
            )

        # add one more consumer group
        assert await glide_client.xgroup_create(key, group_name2, stream_id0_0) == OK

        # verify that xinfo_groups contains info for 2 consumer groups now
        groups = await glide_client.xinfo_groups(key)
        assert len(groups) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xinfo_groups_xinfo_consumers_edge_cases_and_failures(
        self, glide_client: TGlideClient, protocol
    ):
        key = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        group_name = get_random_string(10)
        stream_id1_0 = "1-0"

        # passing a non-existing key raises an error
        with pytest.raises(RequestError):
            await glide_client.xinfo_groups(non_existing_key)
        with pytest.raises(RequestError):
            await glide_client.xinfo_consumers(non_existing_key, group_name)

        assert (
            await glide_client.xadd(
                key, [("f1", "v1"), ("f2", "v2")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )

        # passing a non-existing group raises an error
        with pytest.raises(RequestError):
            await glide_client.xinfo_consumers(key, "non_existing_group")

        # no groups exist yet
        assert await glide_client.xinfo_groups(key) == []

        assert await glide_client.xgroup_create(key, group_name, stream_id1_0) == OK
        # no consumers exist yet
        assert await glide_client.xinfo_consumers(key, group_name) == []

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xinfo_groups(string_key)
        with pytest.raises(RequestError):
            await glide_client.xinfo_consumers(string_key, group_name)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xinfo_stream(
        self, glide_client: TGlideClient, cluster_mode, protocol
    ):
        key = get_random_string(10)
        group_name = get_random_string(10)
        consumer = get_random_string(10)
        stream_id0_0 = "0-0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"

        # setup: add stream entry, create consumer group and consumer, read from stream with consumer
        assert (
            await glide_client.xadd(
                key, [("a", "b"), ("c", "d")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert await glide_client.xgroup_create(key, group_name, stream_id0_0) == OK
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer) == {
            key.encode(): {stream_id1_0.encode(): [[b"a", b"b"], [b"c", b"d"]]}
        }

        result = await glide_client.xinfo_stream(key)
        assert result.get(b"length") == 1
        expected_first_entry = [stream_id1_0.encode(), [b"a", b"b", b"c", b"d"]]
        assert result.get(b"first-entry") == expected_first_entry

        # only one entry exists, so first and last entry should be the same
        assert result.get(b"last-entry") == expected_first_entry

        # call XINFO STREAM with a byte string arg
        result2 = await glide_client.xinfo_stream(key.encode())
        assert result2 == result

        # add one more entry
        assert (
            await glide_client.xadd(
                key, [("foo", "bar")], StreamAddOptions(stream_id1_1)
            )
            == stream_id1_1.encode()
        )

        result_full = await glide_client.xinfo_stream_full(key, count=1)
        assert result_full.get(b"length") == 2
        entries = cast(list, result_full.get(b"entries"))
        # only the first entry will be returned since we passed count=1
        assert len(entries) == 1
        assert entries[0] == expected_first_entry

        groups = cast(list, result_full.get(b"groups"))
        assert len(groups) == 1
        group_info = groups[0]
        assert group_info.get(b"name") == group_name.encode()
        pending = group_info.get(b"pending")
        assert len(pending) == 1
        assert stream_id1_0.encode() in pending[0]

        consumers = group_info.get(b"consumers")
        assert len(consumers) == 1
        consumer_info = consumers[0]
        assert consumer_info.get(b"name") == consumer.encode()
        consumer_pending = consumer_info.get(b"pending")
        assert len(consumer_pending) == 1
        assert stream_id1_0.encode() in consumer_pending[0]

        # call XINFO STREAM FULL with byte arg
        result_full2 = await glide_client.xinfo_stream_full(key.encode())
        # 2 entries should be returned, since we didn't pass the COUNT arg this time
        assert len(cast(list, result_full2.get(b"entries"))) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xinfo_stream_edge_cases_and_failures(
        self, glide_client: TGlideClient, cluster_mode, protocol
    ):
        key = get_random_string(10)
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        stream_id1_0 = "1-0"

        # setup: create empty stream
        assert (
            await glide_client.xadd(
                key, [("field", "value")], StreamAddOptions(stream_id1_0)
            )
            == stream_id1_0.encode()
        )
        assert await glide_client.xdel(key, [stream_id1_0]) == 1

        # XINFO STREAM called against empty stream
        result = await glide_client.xinfo_stream(key)
        assert result.get(b"length") == 0
        assert result.get(b"first-entry") is None
        assert result.get(b"last-entry") is None

        # XINFO STREAM FULL called against empty stream. Negative count values are ignored.
        result_full = await glide_client.xinfo_stream_full(key, count=-3)
        assert result_full.get(b"length") == 0
        assert result_full.get(b"entries") == []
        assert result_full.get(b"groups") == []

        # calling XINFO STREAM with a non-existing key raises an error
        with pytest.raises(RequestError):
            await glide_client.xinfo_stream(non_existing_key)
        with pytest.raises(RequestError):
            await glide_client.xinfo_stream_full(non_existing_key)

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            await glide_client.xinfo_stream(string_key)
        with pytest.raises(RequestError):
            await glide_client.xinfo_stream_full(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_xgroup_set_id(
        self, glide_client: TGlideClient, cluster_mode, protocol, request
    ):
        key = f"{{testKey}}:{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:{get_random_string(10)}"
        string_key = f"{{testKey}}:{get_random_string(10)}"
        group_name = get_random_string(10)
        consumer_name = get_random_string(10)
        stream_id0 = "0"
        stream_id1_0 = "1-0"
        stream_id1_1 = "1-1"
        stream_id1_2 = "1-2"

        # setup: create stream with 3 entries, create consumer group, read entries to add them to the Pending Entries
        # List
        assert (
            await glide_client.xadd(key, [("f0", "v0")], StreamAddOptions(stream_id1_0))
            == stream_id1_0.encode()
        )
        assert (
            await glide_client.xadd(key, [("f1", "v1")], StreamAddOptions(stream_id1_1))
            == stream_id1_1.encode()
        )
        assert (
            await glide_client.xadd(key, [("f2", "v2")], StreamAddOptions(stream_id1_2))
            == stream_id1_2.encode()
        )
        assert await glide_client.xgroup_create(key, group_name, stream_id0) == OK
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer_name) == {
            key.encode(): {
                stream_id1_0.encode(): [[b"f0", b"v0"]],
                stream_id1_1.encode(): [[b"f1", b"v1"]],
                stream_id1_2.encode(): [[b"f2", b"v2"]],
            }
        }
        # sanity check: xreadgroup should not return more entries since they're all already in the Pending Entries List
        assert (
            await glide_client.xreadgroup({key: ">"}, group_name, consumer_name) is None
        )

        # reset the last delivered ID for the consumer group to "1-1"
        # ENTRIESREAD is only supported in Valkey version 7.0.0 and above
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            assert await glide_client.xgroup_set_id(key, group_name, stream_id1_1) == OK
        else:
            assert (
                await glide_client.xgroup_set_id(
                    key, group_name, stream_id1_1, entries_read=0
                )
                == OK
            )

        # xreadgroup should only return entry 1-2 since we reset the last delivered ID to 1-1
        assert await glide_client.xreadgroup({key: ">"}, group_name, consumer_name) == {
            key.encode(): {
                stream_id1_2.encode(): [[b"f2", b"v2"]],
            }
        }

        # an error is raised if XGROUP SETID is called with a non-existing key
        with pytest.raises(RequestError):
            await glide_client.xgroup_set_id(non_existing_key, group_name, stream_id0)

        # an error is raised if XGROUP SETID is called with a non-existing group
        with pytest.raises(RequestError):
            await glide_client.xgroup_set_id(key, "non_existing_group", stream_id0)

        # setting the ID to a non-existing ID is allowed
        assert await glide_client.xgroup_set_id(key, group_name, "99-99") == OK

        # key exists, but it is not a stream
        assert await glide_client.set(string_key, "foo") == OK
        with pytest.raises(RequestError):
            await glide_client.xgroup_set_id(string_key, group_name, stream_id0)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pfadd(self, glide_client: TGlideClient):
        key = get_random_string(10)
        assert await glide_client.pfadd(key, []) == 1
        assert await glide_client.pfadd(key, ["one", "two"]) == 1
        assert await glide_client.pfadd(key, ["two"]) == 0
        assert await glide_client.pfadd(key, []) == 0

        assert await glide_client.set("foo", "value") == OK
        with pytest.raises(RequestError):
            await glide_client.pfadd("foo", [])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pfcount(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await glide_client.pfadd(key1, ["a", "b", "c"]) == 1
        assert await glide_client.pfadd(key2, ["b", "c", "d"]) == 1
        assert await glide_client.pfcount([key1]) == 3
        assert await glide_client.pfcount([key2]) == 3
        assert await glide_client.pfcount([key1, key2]) == 4
        assert await glide_client.pfcount([key1, key2, non_existing_key]) == 4
        # empty HyperLogLog data set
        assert await glide_client.pfadd(key3, []) == 1
        assert await glide_client.pfcount([key3]) == 0

        # incorrect argument - key list cannot be empty
        with pytest.raises(RequestError):
            await glide_client.pfcount([])

        # key exists, but it is not a HyperLogLog
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.pfcount([string_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_pfmerge(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        key3 = f"{{testKey}}:3-{get_random_string(10)}"
        string_key = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key = f"{{testKey}}:5-{get_random_string(10)}"

        assert await glide_client.pfadd(key1, ["a", "b", "c"]) == 1
        assert await glide_client.pfadd(key2, ["b", "c", "d"]) == 1

        # merge into new HyperLogLog data set
        assert await glide_client.pfmerge(key3, [key1, key2]) == OK
        assert await glide_client.pfcount([key3]) == 4

        # merge into existing HyperLogLog data set
        assert await glide_client.pfmerge(key1, [key2]) == OK
        assert await glide_client.pfcount([key1]) == 4

        # non-existing source key
        assert await glide_client.pfmerge(key2, [key1, non_existing_key]) == OK
        assert await glide_client.pfcount([key2]) == 4

        # empty source key list
        assert await glide_client.pfmerge(key1, []) == OK
        assert await glide_client.pfcount([key1]) == 4

        # source key exists, but it is not a HyperLogLog
        assert await glide_client.set(string_key, "foo")
        with pytest.raises(RequestError):
            assert await glide_client.pfmerge(key3, [string_key])

        # destination key exists, but it is not a HyperLogLog
        with pytest.raises(RequestError):
            assert await glide_client.pfmerge(string_key, [key3])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitcount(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        set_key = get_random_string(10)
        non_existing_key = get_random_string(10)
        value = "foobar"

        assert await glide_client.set(key1, value) == OK
        assert await glide_client.bitcount(key1) == 26
        assert await glide_client.bitcount(key1, OffsetOptions(1, 1)) == 6
        assert await glide_client.bitcount(key1, OffsetOptions(0, -5)) == 10
        assert await glide_client.bitcount(non_existing_key, OffsetOptions(5, 30)) == 0
        assert await glide_client.bitcount(non_existing_key) == 0

        # key exists, but it is not a string
        assert await glide_client.sadd(set_key, [value]) == 1
        with pytest.raises(RequestError):
            await glide_client.bitcount(set_key)
        with pytest.raises(RequestError):
            await glide_client.bitcount(set_key, OffsetOptions(1, 1))

        if await check_if_server_version_lt(glide_client, "7.0.0"):
            # exception thrown because BIT and BYTE options were implemented after 7.0.0
            with pytest.raises(RequestError):
                await glide_client.bitcount(
                    key1, OffsetOptions(2, 5, BitmapIndexType.BYTE)
                )
            with pytest.raises(RequestError):
                await glide_client.bitcount(
                    key1, OffsetOptions(2, 5, BitmapIndexType.BIT)
                )
        else:
            assert (
                await glide_client.bitcount(
                    key1, OffsetOptions(2, 5, BitmapIndexType.BYTE)
                )
                == 16
            )
            assert (
                await glide_client.bitcount(
                    key1, OffsetOptions(5, 30, BitmapIndexType.BIT)
                )
                == 17
            )
            assert (
                await glide_client.bitcount(
                    key1, OffsetOptions(5, -5, BitmapIndexType.BIT)
                )
                == 23
            )
            assert (
                await glide_client.bitcount(
                    non_existing_key, OffsetOptions(5, 30, BitmapIndexType.BIT)
                )
                == 0
            )

            # key exists but it is not a string
            with pytest.raises(RequestError):
                await glide_client.bitcount(
                    set_key, OffsetOptions(1, 1, BitmapIndexType.BIT)
                )

        if await check_if_server_version_lt(glide_client, "7.9.0"):
            # exception thrown optional end was implemented after 8.0.0
            with pytest.raises(RequestError):
                await glide_client.bitcount(
                    key1,
                    OffsetOptions(
                        2,
                    ),
                )
        else:
            assert await glide_client.bitcount(key1, OffsetOptions(0)) == 26
            assert await glide_client.bitcount(key1, OffsetOptions(5)) == 4
            assert await glide_client.bitcount(key1, OffsetOptions(80)) == 0
            assert await glide_client.bitcount(non_existing_key, OffsetOptions(5)) == 0

            # key exists but it is not a string
            with pytest.raises(RequestError):
                await glide_client.bitcount(set_key, OffsetOptions(1))

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_setbit(self, glide_client: TGlideClient):
        key = get_random_string(10)
        set_key = get_random_string(10)

        assert await glide_client.setbit(key, 0, 1) == 0
        assert await glide_client.setbit(key, 0, 0) == 1

        # invalid argument - offset can't be negative
        with pytest.raises(RequestError):
            assert await glide_client.setbit(key, -1, 0) == 1

        # key exists, but it is not a string
        assert await glide_client.sadd(set_key, ["foo"]) == 1
        with pytest.raises(RequestError):
            await glide_client.setbit(set_key, 0, 0)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getbit(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        value = "foobar"

        assert await glide_client.set(key, value) == OK
        assert await glide_client.getbit(key, 1) == 1
        # When offset is beyond the string length, the string is assumed to be a contiguous space with 0 bits.
        assert await glide_client.getbit(key, 1000) == 0
        # When key does not exist it is assumed to be an empty string, so offset is always out of range and the value is
        # also assumed to be a contiguous space with 0 bits.
        assert await glide_client.getbit(non_existing_key, 1) == 0

        # invalid argument - offset can't be negative
        with pytest.raises(RequestError):
            assert await glide_client.getbit(key, -1) == 1

        # key exists, but it is not a string
        assert await glide_client.sadd(set_key, ["foo"]) == 1
        with pytest.raises(RequestError):
            await glide_client.getbit(set_key, 0)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitpos_and_bitpos_interval(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        value = (
            "?f0obar"  # 00111111 01100110 00110000 01101111 01100010 01100001 01110010
        )

        assert await glide_client.set(key, value) == OK
        assert await glide_client.bitpos(key, 0) == 0
        assert await glide_client.bitpos(key, 1) == 2
        assert await glide_client.bitpos(key, 1, 1) == 9
        assert await glide_client.bitpos_interval(key, 0, 3, 5) == 24

        # `BITPOS` returns -1 for non-existing strings
        assert await glide_client.bitpos(non_existing_key, 1) == -1
        assert await glide_client.bitpos_interval(non_existing_key, 1, 3, 5) == -1

        # invalid argument - bit value must be 0 or 1
        with pytest.raises(RequestError):
            await glide_client.bitpos(key, 2)
        with pytest.raises(RequestError):
            await glide_client.bitpos_interval(key, 2, 3, 5)

        # key exists, but it is not a string
        assert await glide_client.sadd(set_key, [value]) == 1
        with pytest.raises(RequestError):
            await glide_client.bitpos(set_key, 1)
        with pytest.raises(RequestError):
            await glide_client.bitpos_interval(set_key, 1, 1, -1)

        if await check_if_server_version_lt(glide_client, "7.0.0"):
            # error thrown because BIT and BYTE options were implemented after 7.0.0
            with pytest.raises(RequestError):
                await glide_client.bitpos_interval(key, 1, 1, -1, BitmapIndexType.BYTE)
            with pytest.raises(RequestError):
                await glide_client.bitpos_interval(key, 1, 1, -1, BitmapIndexType.BIT)
        else:
            assert (
                await glide_client.bitpos_interval(key, 0, 3, 5, BitmapIndexType.BYTE)
                == 24
            )
            assert (
                await glide_client.bitpos_interval(key, 1, 43, -2, BitmapIndexType.BIT)
                == 47
            )
            assert (
                await glide_client.bitpos_interval(
                    non_existing_key, 1, 3, 5, BitmapIndexType.BYTE
                )
                == -1
            )
            assert (
                await glide_client.bitpos_interval(
                    non_existing_key, 1, 3, 5, BitmapIndexType.BIT
                )
                == -1
            )

            # key exists, but it is not a string
            with pytest.raises(RequestError):
                await glide_client.bitpos_interval(
                    set_key, 1, 1, -1, BitmapIndexType.BIT
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitop(self, glide_client: TGlideClient):
        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        keys: List[TEncodable] = [key1, key2]
        destination: TEncodable = f"{{testKey}}:3-{get_random_string(10)}"
        non_existing_key1 = f"{{testKey}}:4-{get_random_string(10)}"
        non_existing_key2 = f"{{testKey}}:5-{get_random_string(10)}"
        non_existing_keys: List[TEncodable] = [non_existing_key1, non_existing_key2]
        set_key = f"{{testKey}}:6-{get_random_string(10)}"
        value1 = "foobar"
        value2 = "abcdef"

        assert await glide_client.set(key1, value1) == OK
        assert await glide_client.set(key2, value2) == OK
        assert await glide_client.bitop(BitwiseOperation.AND, destination, keys) == 6
        assert await glide_client.get(destination) == b"`bc`ab"
        assert await glide_client.bitop(BitwiseOperation.OR, destination, keys) == 6
        assert await glide_client.get(destination) == b"goofev"

        # reset values for simplicity of results in XOR
        assert await glide_client.set(key1, "a") == OK
        assert await glide_client.set(key2, "b") == OK
        assert await glide_client.bitop(BitwiseOperation.XOR, destination, keys) == 1
        assert await glide_client.get(destination) == "\u0003".encode()

        # test single source key
        assert await glide_client.bitop(BitwiseOperation.AND, destination, [key1]) == 1
        assert await glide_client.get(destination) == b"a"
        assert await glide_client.bitop(BitwiseOperation.OR, destination, [key1]) == 1
        assert await glide_client.get(destination) == b"a"
        assert await glide_client.bitop(BitwiseOperation.XOR, destination, [key1]) == 1
        assert await glide_client.get(destination) == b"a"
        assert await glide_client.bitop(BitwiseOperation.NOT, destination, [key1]) == 1
        # currently, attempting to get the value from destination after the above NOT incorrectly raises an error
        # TODO: update with a GET call once fix is implemented for https://github.com/valkey-io/valkey-glide/issues/1447

        assert await glide_client.setbit(key1, 0, 1) == 0
        assert await glide_client.bitop(BitwiseOperation.NOT, destination, [key1]) == 1
        assert await glide_client.get(destination) == "\u001e".encode()

        # stores None when all keys hold empty strings
        assert (
            await glide_client.bitop(
                BitwiseOperation.AND, destination, non_existing_keys
            )
            == 0
        )
        assert await glide_client.get(destination) is None
        assert (
            await glide_client.bitop(
                BitwiseOperation.OR, destination, non_existing_keys
            )
            == 0
        )
        assert await glide_client.get(destination) is None
        assert (
            await glide_client.bitop(
                BitwiseOperation.XOR, destination, non_existing_keys
            )
            == 0
        )
        assert await glide_client.get(destination) is None
        assert (
            await glide_client.bitop(
                BitwiseOperation.NOT, destination, [non_existing_key1]
            )
            == 0
        )
        assert await glide_client.get(destination) is None

        # invalid argument - source key list cannot be empty
        with pytest.raises(RequestError):
            await glide_client.bitop(BitwiseOperation.OR, destination, [])

        # invalid arguments - NOT cannot be passed more than 1 key
        with pytest.raises(RequestError):
            await glide_client.bitop(BitwiseOperation.NOT, destination, [key1, key2])

        assert await glide_client.sadd(set_key, [value1]) == 1
        # invalid argument - source key has the wrong type
        with pytest.raises(RequestError):
            await glide_client.bitop(BitwiseOperation.AND, destination, [set_key])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitfield(self, glide_client: TGlideClient):
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
        assert await glide_client.set(key1, foobar) == OK

        # SET tests
        assert await glide_client.bitfield(
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
        assert await glide_client.bitfield(
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
        assert await glide_client.bitfield(
            key2,
            [
                overflow_set,
                BitFieldOverflow(BitOverflowControl.WRAP),
                overflow_set,
                overflow_get,
            ],
        ) == [0, 2, 2]

        # OVERFLOW affects only SET or INCRBY after OVERFLOW subcommand
        assert await glide_client.bitfield(
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
        assert await glide_client.bitfield(
            non_existing_key, [BitFieldSet(UnsignedEncoding(2), BitOffset(3), 2)]
        ) == [0]

        # empty subcommands argument returns an empty list
        assert await glide_client.bitfield(key1, []) == []

        # invalid argument - offset must be >= 0
        with pytest.raises(RequestError):
            await glide_client.bitfield(
                key1, [BitFieldSet(UnsignedEncoding(5), BitOffset(-1), 1)]
            )

        # invalid argument - encoding size must be > 0
        with pytest.raises(RequestError):
            await glide_client.bitfield(
                key1, [BitFieldSet(UnsignedEncoding(0), BitOffset(1), 1)]
            )

        # invalid argument - unsigned encoding size must be < 64
        with pytest.raises(RequestError):
            await glide_client.bitfield(
                key1, [BitFieldSet(UnsignedEncoding(64), BitOffset(1), 1)]
            )

        # invalid argument - signed encoding size must be < 65
        with pytest.raises(RequestError):
            await glide_client.bitfield(
                key1, [BitFieldSet(SignedEncoding(65), BitOffset(1), 1)]
            )

        # key exists, but it is not a string
        assert await glide_client.sadd(set_key, [foobar]) == 1
        with pytest.raises(RequestError):
            await glide_client.bitfield(
                set_key, [BitFieldSet(SignedEncoding(3), BitOffset(1), 2)]
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_bitfield_read_only(self, glide_client: TGlideClient):
        min_version = "6.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        set_key = get_random_string(10)
        foobar = "foobar"
        unsigned_offset_get = BitFieldGet(UnsignedEncoding(2), BitOffset(1))

        # binary value: 01100110 01101111 01101111 01100010 01100001 01110010
        assert await glide_client.set(key, foobar) == OK
        assert await glide_client.bitfield_read_only(
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
        assert await glide_client.bitfield_read_only(
            key, [BitFieldGet(UnsignedEncoding(3), BitOffset(100))]
        ) == [0]
        # similarly, if the key doesn't exist, the operation is performed as though the missing value was a string with
        # all bits set to 0.
        assert await glide_client.bitfield_read_only(
            non_existing_key, [unsigned_offset_get]
        ) == [0]

        # empty subcommands argument returns an empty list
        assert await glide_client.bitfield_read_only(key, []) == []

        # invalid argument - offset must be >= 0
        with pytest.raises(RequestError):
            await glide_client.bitfield_read_only(
                key, [BitFieldGet(UnsignedEncoding(5), BitOffset(-1))]
            )

        # invalid argument - encoding size must be > 0
        with pytest.raises(RequestError):
            await glide_client.bitfield_read_only(
                key, [BitFieldGet(UnsignedEncoding(0), BitOffset(1))]
            )

        # invalid argument - unsigned encoding size must be < 64
        with pytest.raises(RequestError):
            await glide_client.bitfield_read_only(
                key, [BitFieldGet(UnsignedEncoding(64), BitOffset(1))]
            )

        # invalid argument - signed encoding size must be < 65
        with pytest.raises(RequestError):
            await glide_client.bitfield_read_only(
                key, [BitFieldGet(SignedEncoding(65), BitOffset(1))]
            )

        # key exists, but it is not a string
        assert await glide_client.sadd(set_key, [foobar]) == 1
        with pytest.raises(RequestError):
            await glide_client.bitfield_read_only(set_key, [unsigned_offset_get])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_encoding(self, glide_client: TGlideClient):
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

        assert await glide_client.object_encoding(non_existing_key) is None

        assert await glide_client.set(
            string_key, "a really loooooooooooooooooooooooooooooooooooooooong value"
        )
        assert await glide_client.object_encoding(string_key) == "raw".encode()

        assert await glide_client.set(string_key, "2") == OK
        assert await glide_client.object_encoding(string_key) == "int".encode()

        assert await glide_client.set(string_key, "value") == OK
        assert await glide_client.object_encoding(string_key) == "embstr".encode()

        assert await glide_client.lpush(list_key, ["1"]) == 1
        if await check_if_server_version_lt(glide_client, "7.2.0"):
            assert await glide_client.object_encoding(list_key) == "quicklist".encode()
        else:
            assert await glide_client.object_encoding(list_key) == "listpack".encode()

        # The default value of set-max-intset-entries is 512
        for i in range(0, 513):
            assert await glide_client.sadd(hashtable_key, [str(i)]) == 1
        assert await glide_client.object_encoding(hashtable_key) == "hashtable".encode()

        assert await glide_client.sadd(intset_key, ["1"]) == 1
        assert await glide_client.object_encoding(intset_key) == "intset".encode()

        assert await glide_client.sadd(set_listpack_key, ["foo"]) == 1
        if await check_if_server_version_lt(glide_client, "7.2.0"):
            assert (
                await glide_client.object_encoding(set_listpack_key)
                == "hashtable".encode()
            )
        else:
            assert (
                await glide_client.object_encoding(set_listpack_key)
                == "listpack".encode()
            )

        # The default value of hash-max-listpack-entries is 512
        for i in range(0, 513):
            assert await glide_client.hset(hash_hashtable_key, {str(i): "2"}) == 1
        assert (
            await glide_client.object_encoding(hash_hashtable_key)
            == "hashtable".encode()
        )

        assert await glide_client.hset(hash_listpack_key, {"1": "2"}) == 1
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            assert (
                await glide_client.object_encoding(hash_listpack_key)
                == "ziplist".encode()
            )
        else:
            assert (
                await glide_client.object_encoding(hash_listpack_key)
                == "listpack".encode()
            )

        # The default value of zset-max-listpack-entries is 128
        for i in range(0, 129):
            assert await glide_client.zadd(skiplist_key, {str(i): 2.0}) == 1
        assert await glide_client.object_encoding(skiplist_key) == "skiplist".encode()

        assert await glide_client.zadd(zset_listpack_key, {"1": 2.0}) == 1
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            assert (
                await glide_client.object_encoding(zset_listpack_key)
                == "ziplist".encode()
            )
        else:
            assert (
                await glide_client.object_encoding(zset_listpack_key)
                == "listpack".encode()
            )

        assert await glide_client.xadd(stream_key, [("field", "value")]) is not None
        assert await glide_client.object_encoding(stream_key) == "stream".encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_freq(self, glide_client: TGlideClient):
        key = get_random_string(10)
        non_existing_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = await glide_client.config_get([maxmemory_policy_key])
        config_decoded = cast(dict, convert_bytes_to_string_object(config))
        assert config_decoded is not None
        maxmemory_policy = cast(str, config_decoded.get(maxmemory_policy_key))

        try:
            assert (
                await glide_client.config_set({maxmemory_policy_key: "allkeys-lfu"})
                == OK
            )
            assert await glide_client.object_freq(non_existing_key) is None
            assert await glide_client.set(key, "") == OK
            freq = await glide_client.object_freq(key)
            assert freq is not None and freq >= 0
        finally:
            await glide_client.config_set({maxmemory_policy_key: maxmemory_policy})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_idletime(self, glide_client: TGlideClient):
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await glide_client.object_idletime(non_existing_key) is None
        assert await glide_client.set(string_key, "foo") == OK
        time.sleep(2)
        idletime = await glide_client.object_idletime(string_key)
        assert idletime is not None and idletime > 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_object_refcount(self, glide_client: TGlideClient):
        string_key = get_random_string(10)
        non_existing_key = get_random_string(10)

        assert await glide_client.object_refcount(non_existing_key) is None
        assert await glide_client.set(string_key, "foo") == OK
        refcount = await glide_client.object_refcount(string_key)
        assert refcount is not None and refcount >= 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_load(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)

        # verify function does not yet exist
        assert await glide_client.function_list(lib_name) == []

        assert await glide_client.function_load(code) == lib_name.encode()

        assert await glide_client.fcall(func_name, arguments=["one", "two"]) == b"one"
        assert (
            await glide_client.fcall_ro(func_name, arguments=["one", "two"]) == b"one"
        )

        # verify with FUNCTION LIST
        check_function_list_response(
            await glide_client.function_list(lib_name, with_code=True),
            lib_name,
            {func_name: None},
            {func_name: {b"no-writes"}},
            code,
        )

        # re-load library without replace
        with pytest.raises(RequestError) as e:
            await glide_client.function_load(code)
        assert "Library '" + lib_name + "' already exists" in str(e)

        # re-load library with replace
        assert await glide_client.function_load(code, True) == lib_name.encode()

        func2_name = f"myfunc2c{get_random_string(5)}"
        new_code = f"""{code}\n redis.register_function({func2_name}, function(keys, args) return #args end)"""
        new_code = generate_lua_lib_code(
            lib_name, {func_name: "return args[1]", func2_name: "return #args"}, True
        )

        assert await glide_client.function_load(new_code, True) == lib_name.encode()

        assert await glide_client.fcall(func2_name, arguments=["one", "two"]) == 2
        assert await glide_client.fcall_ro(func2_name, arguments=["one", "two"]) == 2

        assert await glide_client.function_flush(FlushMode.SYNC) is OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("single_route", [True, False])
    async def test_function_load_cluster_with_route(
        self, glide_client: GlideClusterClient, single_route: bool
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
        route = SlotKeyRoute(SlotType.PRIMARY, "1") if single_route else AllPrimaries()

        # verify function does not yet exist
        function_list = await glide_client.function_list(lib_name, False, route)
        if single_route:
            assert function_list == []
        else:
            assert isinstance(function_list, dict)
            for functions in function_list.values():
                assert functions == []

        assert await glide_client.function_load(code, False, route) == lib_name.encode()

        result = await glide_client.fcall_route(
            func_name, arguments=["one", "two"], route=route
        )

        if single_route:
            assert result == b"one"
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert nodeResponse == b"one"

        result = await glide_client.fcall_ro_route(
            func_name, arguments=["one", "two"], route=route
        )

        if single_route:
            assert result == b"one"
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert nodeResponse == b"one"

        # verify with FUNCTION LIST
        function_list = await glide_client.function_list(
            lib_name, with_code=True, route=route
        )
        if single_route:
            check_function_list_response(
                function_list,
                lib_name,
                {func_name: None},
                {func_name: {b"no-writes"}},
                code,
            )
        else:
            assert isinstance(function_list, dict)
            for nodeResponse in function_list.values():
                check_function_list_response(
                    nodeResponse,
                    lib_name,
                    {func_name: None},
                    {func_name: {b"no-writes"}},
                    code,
                )

        # re-load library without replace
        with pytest.raises(RequestError) as e:
            await glide_client.function_load(code, False, route)
        assert "Library '" + lib_name + "' already exists" in str(e)

        # re-load library with replace
        assert await glide_client.function_load(code, True, route) == lib_name.encode()

        func2_name = f"myfunc2c{get_random_string(5)}"
        new_code = f"""{code}\n redis.register_function({func2_name}, function(keys, args) return #args end)"""
        new_code = generate_lua_lib_code(
            lib_name, {func_name: "return args[1]", func2_name: "return #args"}, True
        )

        assert (
            await glide_client.function_load(new_code, True, route) == lib_name.encode()
        )

        result = await glide_client.fcall_route(
            func2_name, arguments=["one", "two"], route=route
        )

        if single_route:
            assert result == 2
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert nodeResponse == 2

        result = await glide_client.fcall_ro_route(
            func2_name, arguments=["one", "two"], route=route
        )

        if single_route:
            assert result == 2
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert nodeResponse == 2

        assert await glide_client.function_flush(FlushMode.SYNC, route) is OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_list(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        original_functions_count = len(await glide_client.function_list())

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)

        # Assert function `lib_name` does not yet exist
        assert await glide_client.function_list(lib_name) == []

        # load library
        await glide_client.function_load(code)

        check_function_list_response(
            await glide_client.function_list(lib_name.encode()),
            lib_name,
            {func_name: None},
            {func_name: {b"no-writes"}},
            None,
        )
        check_function_list_response(
            await glide_client.function_list(f"{lib_name}*"),
            lib_name,
            {func_name: None},
            {func_name: {b"no-writes"}},
            None,
        )
        check_function_list_response(
            await glide_client.function_list(lib_name, with_code=True),
            lib_name,
            {func_name: None},
            {func_name: {b"no-writes"}},
            code,
        )

        no_args_response = await glide_client.function_list()
        wildcard_pattern_response = await glide_client.function_list(
            "*".encode(), False
        )
        assert len(no_args_response) == original_functions_count + 1
        assert len(wildcard_pattern_response) == original_functions_count + 1
        check_function_list_response(
            no_args_response,
            lib_name,
            {func_name: None},
            {func_name: {b"no-writes"}},
            None,
        )
        check_function_list_response(
            wildcard_pattern_response,
            lib_name,
            {func_name: None},
            {func_name: {b"no-writes"}},
            None,
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("single_route", [True, False])
    async def test_function_list_with_routing(
        self, glide_client: GlideClusterClient, single_route: bool
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        route = SlotKeyRoute(SlotType.PRIMARY, "1") if single_route else AllPrimaries()

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)

        # Assert function `lib_name` does not yet exist
        result = await glide_client.function_list(lib_name, route=route)
        if single_route:
            assert result == []
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert nodeResponse == []

        # load library
        await glide_client.function_load(code, route=route)

        result = await glide_client.function_list(lib_name, route=route)
        if single_route:
            check_function_list_response(
                result,
                lib_name,
                {func_name: None},
                {func_name: {b"no-writes"}},
                None,
            )
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                check_function_list_response(
                    nodeResponse,
                    lib_name,
                    {func_name: None},
                    {func_name: {b"no-writes"}},
                    None,
                )

        result = await glide_client.function_list(f"{lib_name}*", route=route)
        if single_route:
            check_function_list_response(
                result,
                lib_name,
                {func_name: None},
                {func_name: {b"no-writes"}},
                None,
            )
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                check_function_list_response(
                    nodeResponse,
                    lib_name,
                    {func_name: None},
                    {func_name: {b"no-writes"}},
                    None,
                )

        result = await glide_client.function_list(lib_name, with_code=True, route=route)
        if single_route:
            check_function_list_response(
                result,
                lib_name,
                {func_name: None},
                {func_name: {b"no-writes"}},
                code,
            )
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                check_function_list_response(
                    nodeResponse,
                    lib_name,
                    {func_name: None},
                    {func_name: {b"no-writes"}},
                    code,
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_list_with_multiple_functions(
        self, glide_client: TGlideClient
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        await glide_client.function_flush()
        assert len(await glide_client.function_list()) == 0

        lib_name_1 = f"mylib1C{get_random_string(5)}"
        func_name_1 = f"myfunc1c{get_random_string(5)}"
        func_name_2 = f"myfunc2c{get_random_string(5)}"
        code_1 = generate_lua_lib_code(
            lib_name_1,
            {func_name_1: "return args[1]", func_name_2: "return args[2]"},
            False,
        )
        await glide_client.function_load(code_1)

        lib_name_2 = f"mylib2C{get_random_string(5)}"
        func_name_3 = f"myfunc3c{get_random_string(5)}"
        code_2 = generate_lua_lib_code(
            lib_name_2, {func_name_3: "return args[3]"}, True
        )
        await glide_client.function_load(code_2)

        no_args_response = await glide_client.function_list()

        assert len(no_args_response) == 2
        check_function_list_response(
            no_args_response,
            lib_name_1,
            {func_name_1: None, func_name_2: None},
            {func_name_1: set(), func_name_2: set()},
            None,
        )
        check_function_list_response(
            no_args_response,
            lib_name_2,
            {func_name_3: None},
            {func_name_3: {b"no-writes"}},
            None,
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_flush(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            pytest.skip(f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)

        # Load the function
        assert await glide_client.function_load(code) == lib_name.encode()

        # verify function exists
        assert len(await glide_client.function_list(lib_name)) == 1

        # Flush functions
        assert await glide_client.function_flush(FlushMode.SYNC) == OK
        assert await glide_client.function_flush(FlushMode.ASYNC) == OK

        # verify function is removed
        assert len(await glide_client.function_list(lib_name)) == 0

        # Attempt to re-load library without overwriting to ensure FLUSH was effective
        assert await glide_client.function_load(code) == lib_name.encode()

        # verify function exists
        assert len(await glide_client.function_list(lib_name)) == 1

        # Clean up by flushing functions again
        await glide_client.function_flush()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("single_route", [True, False])
    async def test_function_flush_with_routing(
        self, glide_client: GlideClusterClient, single_route: bool
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            pytest.skip(f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
        route = SlotKeyRoute(SlotType.PRIMARY, "1") if single_route else AllPrimaries()

        # Load the function
        assert await glide_client.function_load(code, False, route) == lib_name.encode()

        # verify function exists
        result = await glide_client.function_list(lib_name, False, route)
        if single_route:
            assert len(result) == 1
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert len(nodeResponse) == 1

        # Flush functions
        assert await glide_client.function_flush(FlushMode.SYNC, route) == OK
        assert await glide_client.function_flush(FlushMode.ASYNC, route) == OK

        # verify function is removed
        result = await glide_client.function_list(lib_name, False, route)
        if single_route:
            assert len(result) == 0
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert len(nodeResponse) == 0

        # Attempt to re-load library without overwriting to ensure FLUSH was effective
        assert await glide_client.function_load(code, False, route) == lib_name.encode()

        # verify function exists
        result = await glide_client.function_list(lib_name, False, route)
        if single_route:
            assert len(result) == 1
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert len(nodeResponse) == 1

        # Clean up by flushing functions again
        assert await glide_client.function_flush(route=route) == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_delete(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            pytest.skip(f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)

        # Load the function
        assert await glide_client.function_load(code) == lib_name.encode()

        # verify function exists
        assert len(await glide_client.function_list(lib_name)) == 1

        # Delete the function
        assert await glide_client.function_delete(lib_name) == OK

        # verify function is removed
        assert len(await glide_client.function_list(lib_name)) == 0

        # deleting a non-existing library
        with pytest.raises(RequestError) as e:
            await glide_client.function_delete(lib_name)
        assert "Library not found" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("single_route", [True, False])
    async def test_function_delete_with_routing(
        self, glide_client: GlideClusterClient, single_route: bool
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            pytest.skip(f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
        route = SlotKeyRoute(SlotType.PRIMARY, "1") if single_route else AllPrimaries()

        # Load the function
        assert await glide_client.function_load(code, False, route) == lib_name.encode()

        # verify function exists
        result = await glide_client.function_list(lib_name, False, route)
        if single_route:
            assert len(result) == 1
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert len(nodeResponse) == 1

        # Delete the function
        assert await glide_client.function_delete(lib_name, route) == OK

        # verify function is removed
        result = await glide_client.function_list(lib_name, False, route)
        if single_route:
            assert len(result) == 0
        else:
            assert isinstance(result, dict)
            for nodeResponse in result.values():
                assert len(nodeResponse) == 0

        # deleting a non-existing library
        with pytest.raises(RequestError) as e:
            await glide_client.function_delete(lib_name)
        assert "Library not found" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_stats(self, glide_client: TGlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = "functionStats_without_route"
        func_name = lib_name
        assert await glide_client.function_flush(FlushMode.SYNC) == OK

        # function $funcName returns first argument
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, False)
        assert await glide_client.function_load(code, True) == lib_name.encode()

        response = await glide_client.function_stats()
        for node_response in response.values():
            check_function_stats_response(
                cast(TFunctionStatsSingleNodeResponse, node_response), [], 1, 1
            )

        code = generate_lua_lib_code(
            lib_name + "_2",
            {func_name + "_2": "return 'OK'", func_name + "_3": "return 42"},
            False,
        )
        assert (
            await glide_client.function_load(code, True) == (lib_name + "_2").encode()
        )

        response = await glide_client.function_stats()
        for node_response in response.values():
            check_function_stats_response(
                cast(TFunctionStatsSingleNodeResponse, node_response), [], 2, 3
            )

        assert await glide_client.function_flush(FlushMode.SYNC) == OK

        response = await glide_client.function_stats()
        for node_response in response.values():
            check_function_stats_response(
                cast(TFunctionStatsSingleNodeResponse, node_response), [], 0, 0
            )

    @pytest.mark.parametrize("cluster_mode", [False, True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_stats_running_script(
        self, request, cluster_mode, protocol, glide_client: TGlideClient
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = create_lua_lib_with_long_running_function(lib_name, func_name, 10, True)

        # load the library
        assert await glide_client.function_load(code, replace=True) == lib_name.encode()

        # create a second client to run fcall
        test_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=30000
        )

        test_client2 = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=30000
        )

        async def endless_fcall_route_call():
            await test_client.fcall_ro(func_name, arguments=[])

        async def wait_and_function_stats():
            # it can take a few seconds for FCALL to register as running
            await asyncio.sleep(3)
            result = await test_client2.function_stats()
            running_scripts = False
            for res in result.values():
                if res.get(b"running_script"):
                    if running_scripts:
                        raise Exception("Already running script on a different node")
                    running_scripts = True
                    assert res.get(b"running_script").get(b"name") == func_name.encode()
                    assert res.get(b"running_script").get(b"command") == [
                        b"FCALL_RO",
                        func_name.encode(),
                        b"0",
                    ]
                    assert res.get(b"running_script").get(b"duration_ms") > 0

            assert running_scripts

        await asyncio.gather(
            endless_fcall_route_call(),
            wait_and_function_stats(),
        )

        await test_client.close()
        await test_client2.close()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("single_route", [True, False])
    async def test_function_stats_with_routing(
        self, glide_client: GlideClusterClient, single_route: bool
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        route = (
            SlotKeyRoute(SlotType.PRIMARY, get_random_string(10))
            if single_route
            else AllPrimaries()
        )
        lib_name = "functionStats_with_route_" + str(single_route)
        func_name = lib_name
        assert await glide_client.function_flush(FlushMode.SYNC, route) == OK

        # function $funcName returns first argument
        code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, False)
        assert await glide_client.function_load(code, True, route) == lib_name.encode()

        response = await glide_client.function_stats(route)
        if single_route:
            check_function_stats_response(
                cast(TFunctionStatsSingleNodeResponse, response), [], 1, 1
            )
        else:
            for node_response in response.values():
                check_function_stats_response(
                    cast(TFunctionStatsSingleNodeResponse, node_response), [], 1, 1
                )

        code = generate_lua_lib_code(
            lib_name + "_2",
            {func_name + "_2": "return 'OK'", func_name + "_3": "return 42"},
            False,
        )
        assert (
            await glide_client.function_load(code, True, route)
            == (lib_name + "_2").encode()
        )

        response = await glide_client.function_stats(route)
        if single_route:
            check_function_stats_response(
                cast(TFunctionStatsSingleNodeResponse, response), [], 2, 3
            )
        else:
            for node_response in response.values():
                check_function_stats_response(
                    cast(TFunctionStatsSingleNodeResponse, node_response), [], 2, 3
                )

        assert await glide_client.function_flush(FlushMode.SYNC, route) == OK

        response = await glide_client.function_stats(route)
        if single_route:
            check_function_stats_response(
                cast(TFunctionStatsSingleNodeResponse, response), [], 0, 0
            )
        else:
            for node_response in response.values():
                check_function_stats_response(
                    cast(TFunctionStatsSingleNodeResponse, node_response), [], 0, 0
                )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_kill_no_write(
        self, request, cluster_mode, protocol, glide_client: TGlideClient
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = create_lua_lib_with_long_running_function(lib_name, func_name, 10, True)

        # nothing to kill
        with pytest.raises(RequestError) as e:
            await glide_client.function_kill()
        assert "NotBusy" in str(e)

        # load the library
        assert await glide_client.function_load(code, replace=True) == lib_name.encode()

        # create a second client to run fcall
        test_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=15000
        )

        async def endless_fcall_route_call():
            # fcall is supposed to be killed, and will return a RequestError
            with pytest.raises(RequestError) as e:
                await test_client.fcall_ro(func_name, arguments=[])
            assert "Script killed by user" in str(e)

        async def wait_and_function_kill():
            # it can take a few seconds for FCALL to register as running
            await asyncio.sleep(3)
            timeout = 0
            while timeout <= 5:
                # keep trying to kill until we get an "OK"
                try:
                    result = await glide_client.function_kill()
                    #  we expect to get success
                    assert result == "OK"
                    break
                except RequestError:
                    # a RequestError may occur if the function is not yet running
                    # sleep and try again
                    timeout += 0.5
                    await asyncio.sleep(0.5)

        await asyncio.gather(
            endless_fcall_route_call(),
            wait_and_function_kill(),
        )

        # no functions running so we get notbusy error again
        with pytest.raises(RequestError) as e:
            assert await glide_client.function_kill()
        assert "NotBusy" in str(e)

    @pytest.mark.parametrize("cluster_mode", [False, True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_kill_write_is_unkillable(
        self, request, cluster_mode, protocol, glide_client: TGlideClient
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = create_lua_lib_with_long_running_function(lib_name, func_name, 10, False)

        # load the library on all primaries
        assert await glide_client.function_load(code, replace=True) == lib_name.encode()

        # create a second client to run fcall - and give it a long timeout
        test_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=15000
        )

        # call fcall to run the function loaded function
        async def endless_fcall_route_call():
            # fcall won't be killed, because kill only works against fcalls that don't make a write operation
            # use fcall(key) so that it makes a write operation
            await test_client.fcall(func_name, keys=[lib_name])

        async def wait_and_function_kill():
            # it can take a few seconds for FCALL to register as running
            await asyncio.sleep(3)
            timeout = 0
            foundUnkillable = False
            while timeout <= 5:
                # keep trying to kill until we get a unkillable return error
                try:
                    await glide_client.function_kill()
                except RequestError as e:
                    if "UNKILLABLE" in str(e):
                        foundUnkillable = True
                        break
                timeout += 0.5
                await asyncio.sleep(0.5)
            # expect an unkillable error
            assert foundUnkillable

        await asyncio.gather(
            endless_fcall_route_call(),
            wait_and_function_kill(),
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_fcall_with_key(self, glide_client: GlideClusterClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = f"{{testKey}}:1-{get_random_string(10)}"
        key2 = f"{{testKey}}:2-{get_random_string(10)}"
        keys: List[TEncodable] = [key1, key2]
        route = SlotKeyRoute(SlotType.PRIMARY, key1)
        lib_name = f"mylib1C{get_random_string(5)}"
        func_name = f"myfunc1c{get_random_string(5)}"
        code = generate_lua_lib_code(lib_name, {func_name: "return keys[1]"}, True)

        assert await glide_client.function_flush(FlushMode.SYNC, route) is OK
        assert await glide_client.function_load(code, False, route) == lib_name.encode()

        assert (
            await glide_client.fcall(func_name, keys=keys, arguments=[])
            == key1.encode()
        )

        assert (
            await glide_client.fcall_ro(func_name, keys=keys, arguments=[])
            == key1.encode()
        )

        transaction = ClusterTransaction()

        transaction.fcall(func_name, keys=keys, arguments=[])
        transaction.fcall_ro(func_name, keys=keys, arguments=[])

        # check response from a routed transaction request
        result = await glide_client.exec(transaction, route)
        assert result is not None
        assert result[0] == key1.encode()
        assert result[1] == key1.encode()

        # if no route given, GLIDE should detect it automatically
        result = await glide_client.exec(transaction)
        assert result is not None
        assert result[0] == key1.encode()
        assert result[1] == key1.encode()

        assert await glide_client.function_flush(FlushMode.SYNC, route) is OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_fcall_readonly_function(self, glide_client: GlideClusterClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        lib_name = f"fcall_readonly_function{get_random_string(5)}"
        # intentionally using a REPLICA route
        replicaRoute = SlotKeyRoute(SlotType.REPLICA, lib_name)
        primaryRoute = SlotKeyRoute(SlotType.PRIMARY, lib_name)
        func_name = f"fcall_readonly_function{get_random_string(5)}"

        # function $funcName returns a magic number
        code = generate_lua_lib_code(lib_name, {func_name: "return 42"}, False)

        assert await glide_client.function_load(code, False) == lib_name.encode()

        # On a replica node should fail, because a function isn't guaranteed to be RO
        with pytest.raises(RequestError) as e:
            assert await glide_client.fcall_route(
                func_name, arguments=[], route=replicaRoute
            )
        assert "You can't write against a read only replica." in str(e)

        with pytest.raises(RequestError) as e:
            assert await glide_client.fcall_ro_route(
                func_name, arguments=[], route=replicaRoute
            )
        assert "You can't write against a read only replica." in str(e)

        # fcall_ro also fails to run it even on primary - another error
        with pytest.raises(RequestError) as e:
            assert await glide_client.fcall_ro_route(
                func_name, arguments=[], route=primaryRoute
            )
        assert "Can not execute a script with write flag using *_ro command." in str(e)

        # create the same function, but with RO flag
        code = generate_lua_lib_code(lib_name, {func_name: "return 42"}, True)
        assert await glide_client.function_load(code, True) == lib_name.encode()

        # fcall should succeed now
        assert (
            await glide_client.fcall_ro_route(
                func_name, arguments=[], route=replicaRoute
            )
            == 42
        )

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_dump_restore_standalone(self, glide_client: GlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        assert await glide_client.function_flush(FlushMode.SYNC) is OK

        # Dump an empty lib
        emptyDump = await glide_client.function_dump()
        assert emptyDump is not None and len(emptyDump) > 0

        name1 = f"Foster{get_random_string(5)}"
        name2 = f"Dogster{get_random_string(5)}"

        # function name1 returns first argument; function name2 returns argument array len
        code = generate_lua_lib_code(
            name1, {name1: "return args[1]", name2: "return #args"}, False
        )
        assert await glide_client.function_load(code, True) == name1.encode()
        flist = await glide_client.function_list(with_code=True)

        dump = await glide_client.function_dump()
        assert dump is not None

        # restore without cleaning the lib and/or overwrite option causes an error
        with pytest.raises(RequestError) as e:
            assert await glide_client.function_restore(dump)
        assert "already exists" in str(e)

        # APPEND policy also fails for the same reason (name collision)
        with pytest.raises(RequestError) as e:
            assert await glide_client.function_restore(
                dump, FunctionRestorePolicy.APPEND
            )
        assert "already exists" in str(e)

        # REPLACE policy succeed
        assert (
            await glide_client.function_restore(dump, FunctionRestorePolicy.REPLACE)
            is OK
        )

        # but nothing changed - all code overwritten
        assert await glide_client.function_list(with_code=True) == flist

        # create lib with another name, but with the same function names
        assert await glide_client.function_flush(FlushMode.SYNC) is OK
        code = generate_lua_lib_code(
            name2, {name1: "return args[1]", name2: "return #args"}, False
        )
        assert await glide_client.function_load(code, True) == name2.encode()

        # REPLACE policy now fails due to a name collision
        with pytest.raises(RequestError) as e:
            await glide_client.function_restore(dump, FunctionRestorePolicy.REPLACE)
        assert "already exists" in str(e)

        # FLUSH policy succeeds, but deletes the second lib
        assert (
            await glide_client.function_restore(dump, FunctionRestorePolicy.FLUSH) is OK
        )
        assert await glide_client.function_list(with_code=True) == flist

        # call restored functions
        assert (
            await glide_client.fcall(name1, arguments=["meow", "woem"])
            == "meow".encode()
        )
        assert await glide_client.fcall(name2, arguments=["meow", "woem"]) == 2

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_function_dump_restore_cluster(
        self, glide_client: GlideClusterClient
    ):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        assert await glide_client.function_flush(FlushMode.SYNC) is OK

        # Dump an empty lib
        emptyDump = await glide_client.function_dump()
        assert emptyDump is not None and len(emptyDump) > 0

        name1 = f"Foster{get_random_string(5)}"
        libname1 = f"FosterLib{get_random_string(5)}"
        name2 = f"Dogster{get_random_string(5)}"
        libname2 = f"DogsterLib{get_random_string(5)}"

        # function name1 returns first argument; function name2 returns argument array len
        code = generate_lua_lib_code(
            libname1, {name1: "return args[1]", name2: "return #args"}, True
        )
        assert await glide_client.function_load(code, True) == libname1.encode()
        flist = await glide_client.function_list(with_code=True)
        dump = await glide_client.function_dump(RandomNode())
        assert dump is not None and isinstance(dump, bytes)

        # restore without cleaning the lib and/or overwrite option causes an error
        with pytest.raises(RequestError) as e:
            assert await glide_client.function_restore(dump)
        assert "already exists" in str(e)

        # APPEND policy also fails for the same reason (name collision)
        with pytest.raises(RequestError) as e:
            assert await glide_client.function_restore(
                dump, FunctionRestorePolicy.APPEND
            )
        assert "already exists" in str(e)

        # REPLACE policy succeed
        assert (
            await glide_client.function_restore(
                dump, FunctionRestorePolicy.REPLACE, route=AllPrimaries()
            )
            is OK
        )

        # but nothing changed - all code overwritten
        restoredFunctionList = await glide_client.function_list(with_code=True)
        assert restoredFunctionList is not None
        assert isinstance(restoredFunctionList, List) and len(restoredFunctionList) == 1
        assert restoredFunctionList[0]["library_name".encode()] == libname1.encode()

        # Note that function ordering may differ across nodes so we can't do a deep equals
        assert len(restoredFunctionList[0]["functions".encode()]) == 2

        # create lib with another name, but with the same function names
        assert await glide_client.function_flush(FlushMode.SYNC) is OK
        code = generate_lua_lib_code(
            libname2, {name1: "return args[1]", name2: "return #args"}, True
        )
        assert await glide_client.function_load(code, True) == libname2.encode()
        restoredFunctionList = await glide_client.function_list(with_code=True)
        assert restoredFunctionList is not None
        assert isinstance(restoredFunctionList, List) and len(restoredFunctionList) == 1
        assert restoredFunctionList[0]["library_name".encode()] == libname2.encode()

        # REPLACE policy now fails due to a name collision
        with pytest.raises(RequestError) as e:
            await glide_client.function_restore(dump, FunctionRestorePolicy.REPLACE)
        assert "already exists" in str(e)

        # FLUSH policy succeeds, but deletes the second lib
        assert (
            await glide_client.function_restore(dump, FunctionRestorePolicy.FLUSH) is OK
        )
        restoredFunctionList = await glide_client.function_list(with_code=True)
        assert restoredFunctionList is not None
        assert isinstance(restoredFunctionList, List) and len(restoredFunctionList) == 1
        assert restoredFunctionList[0]["library_name".encode()] == libname1.encode()

        # Note that function ordering may differ across nodes so we can't do a deep equals
        assert len(restoredFunctionList[0]["functions".encode()]) == 2

        # call restored functions
        assert (
            await glide_client.fcall_ro(name1, arguments=["meow", "woem"])
            == "meow".encode()
        )
        assert await glide_client.fcall_ro(name2, arguments=["meow", "woem"]) == 2

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_srandmember(self, glide_client: TGlideClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        elements: List[TEncodable] = ["one", "two"]
        assert await glide_client.sadd(key, elements) == 2

        member = await glide_client.srandmember(key)
        # TODO: remove when function signature is fixed
        assert isinstance(member, bytes)
        assert member.decode() in elements
        assert await glide_client.srandmember("non_existing_key") is None

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.srandmember(string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_srandmember_count(self, glide_client: TGlideClient):
        key = get_random_string(10)
        string_key = get_random_string(10)
        elements: List[TEncodable] = ["one", "two"]
        assert await glide_client.sadd(key, elements) == 2

        # unique values are expected as count is positive
        members = await glide_client.srandmember_count(key, 4)
        assert len(members) == 2
        assert set(members) == {b"one", b"two"}

        # duplicate values are expected as count is negative
        members = await glide_client.srandmember_count(key, -4)
        assert len(members) == 4
        for member in members:
            # TODO: remove when function signature is fixed
            assert isinstance(member, bytes)
            assert member.decode() in elements

        # empty return values for non-existing or empty keys
        assert await glide_client.srandmember_count(key, 0) == []
        assert await glide_client.srandmember_count("non_existing_key", 0) == []

        # key exists, but it is not a set
        assert await glide_client.set(string_key, "value") == OK
        with pytest.raises(RequestError):
            await glide_client.srandmember_count(string_key, 8)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_flushall(self, glide_client: TGlideClient):
        min_version = "6.2.0"
        key = f"{{key}}-1{get_random_string(5)}"
        value = get_random_string(5)

        await glide_client.set(key, value)
        assert await glide_client.dbsize() > 0
        assert await glide_client.flushall() == OK
        assert await glide_client.flushall(FlushMode.ASYNC) == OK
        if not await check_if_server_version_lt(glide_client, min_version):
            assert await glide_client.flushall(FlushMode.SYNC) == OK
        assert await glide_client.dbsize() == 0

        if isinstance(glide_client, GlideClusterClient):
            await glide_client.set(key, value)
            assert await glide_client.flushall(route=AllPrimaries()) == OK
            assert await glide_client.flushall(FlushMode.ASYNC, AllPrimaries()) == OK
            if not await check_if_server_version_lt(glide_client, min_version):
                assert await glide_client.flushall(FlushMode.SYNC, AllPrimaries()) == OK
            assert await glide_client.dbsize() == 0

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_flushdb(self, glide_client: GlideClient):
        min_version = "6.2.0"
        key1 = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        value = get_random_string(5)

        # fill DB 0 and check size non-empty
        assert await glide_client.select(0) == OK
        await glide_client.set(key1, value)
        assert await glide_client.dbsize() > 0

        # fill DB 1 and check size non-empty
        assert await glide_client.select(1) == OK
        await glide_client.set(key2, value)
        assert await glide_client.dbsize() > 0

        # flush DB 1 and check again
        assert await glide_client.flushdb() == OK
        assert await glide_client.dbsize() == 0

        # swith to DB 0, flush, and check
        assert await glide_client.select(0) == OK
        assert await glide_client.dbsize() > 0
        assert await glide_client.flushdb(FlushMode.ASYNC) == OK
        assert await glide_client.dbsize() == 0

        # verify flush SYNC
        if not await check_if_server_version_lt(glide_client, min_version):
            await glide_client.set(key2, value)
            assert await glide_client.dbsize() > 0
            assert await glide_client.flushdb(FlushMode.SYNC) == OK
            assert await glide_client.dbsize() == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_getex(self, glide_client: TGlideClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        key1 = get_random_string(10)
        non_existing_key = get_random_string(10)
        value = get_random_string(10)
        value_encoded = value.encode()

        assert await glide_client.set(key1, value) == OK
        assert await glide_client.getex(non_existing_key) is None
        assert await glide_client.getex(key1) == value_encoded
        assert await glide_client.ttl(key1) == -1

        # setting expiration timer
        assert (
            await glide_client.getex(key1, ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 50))
            == value_encoded
        )
        assert await glide_client.ttl(key1) != -1

        # setting and clearing expiration timer
        assert await glide_client.set(key1, value) == OK
        assert (
            await glide_client.getex(key1, ExpiryGetEx(ExpiryTypeGetEx.SEC, 10))
            == value_encoded
        )
        assert (
            await glide_client.getex(key1, ExpiryGetEx(ExpiryTypeGetEx.PERSIST, None))
            == value_encoded
        )
        assert await glide_client.ttl(key1) == -1

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_copy_no_database(self, glide_client: TGlideClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        source = f"{{testKey}}:1-{get_random_string(10)}"
        destination = f"{{testKey}}:2-{get_random_string(10)}"
        value1 = get_random_string(5)
        value2 = get_random_string(5)
        value1_encoded = value1.encode()

        # neither key exists
        assert await glide_client.copy(source, destination, replace=False) is False
        assert await glide_client.copy(source, destination) is False

        # source exists, destination does not
        await glide_client.set(source, value1)
        assert await glide_client.copy(source, destination, replace=False) is True
        assert await glide_client.get(destination) == value1_encoded

        # new value for source key
        await glide_client.set(source, value2)

        # both exists, no REPLACE
        assert await glide_client.copy(source, destination) is False
        assert await glide_client.copy(source, destination, replace=False) is False
        assert await glide_client.get(destination) == value1_encoded

        # both exists, with REPLACE
        assert await glide_client.copy(source, destination, replace=True) is True
        assert await glide_client.get(destination) == value2.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_copy_database(self, glide_client: GlideClient):
        min_version = "6.2.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        source = get_random_string(10)
        destination = get_random_string(10)
        value1 = get_random_string(5)
        value2 = get_random_string(5)
        value1_encoded = value1.encode()
        value2_encoded = value2.encode()
        index0 = 0
        index1 = 1
        index2 = 2

        try:
            assert await glide_client.select(index0) == OK

            # neither key exists
            assert (
                await glide_client.copy(source, destination, index1, replace=False)
                is False
            )

            # source exists, destination does not
            await glide_client.set(source, value1)
            assert (
                await glide_client.copy(source, destination, index1, replace=False)
                is True
            )
            assert await glide_client.select(index1) == OK
            assert await glide_client.get(destination) == value1_encoded

            # new value for source key
            assert await glide_client.select(index0) == OK
            await glide_client.set(source, value2)

            # no REPLACE, copying to existing key on DB 0 & 1, non-existing key on DB 2
            assert (
                await glide_client.copy(source, destination, index1, replace=False)
                is False
            )
            assert (
                await glide_client.copy(source, destination, index2, replace=False)
                is True
            )

            # new value only gets copied to DB 2
            assert await glide_client.select(index1) == OK
            assert await glide_client.get(destination) == value1_encoded
            assert await glide_client.select(index2) == OK
            assert await glide_client.get(destination) == value2_encoded

            # both exists, with REPLACE, when value isn't the same, source always get copied to destination
            assert await glide_client.select(index0) == OK
            assert (
                await glide_client.copy(source, destination, index1, replace=True)
                is True
            )
            assert await glide_client.select(index1) == OK
            assert await glide_client.get(destination) == value2_encoded

            # invalid DB index
            with pytest.raises(RequestError):
                await glide_client.copy(source, destination, -1, replace=True)
        finally:
            assert await glide_client.select(0) == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_wait(self, glide_client: TGlideClient):
        key = f"{{key}}-1{get_random_string(5)}"
        value = get_random_string(5)
        value2 = get_random_string(5)

        assert await glide_client.set(key, value) == OK
        if isinstance(glide_client, GlideClusterClient):
            assert await glide_client.wait(1, 1000) >= 1
        else:
            assert await glide_client.wait(1, 1000) >= 0

        # ensure that command doesn't time out even if timeout > request timeout (250ms by default)
        assert await glide_client.set(key, value2) == OK
        assert await glide_client.wait(100, 500) >= 0

        # command should fail on a negative timeout value
        with pytest.raises(RequestError):
            await glide_client.wait(1, -1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lolwut(self, glide_client: TGlideClient):
        result = await glide_client.lolwut()
        assert b"Redis ver. " in result
        result = await glide_client.lolwut(parameters=[])
        assert b"Redis ver. " in result
        result = await glide_client.lolwut(parameters=[50, 20])
        assert b"Redis ver. " in result
        result = await glide_client.lolwut(6)
        assert b"Redis ver. " in result
        result = await glide_client.lolwut(5, [30, 4, 4])
        assert b"Redis ver. " in result

        if isinstance(glide_client, GlideClusterClient):
            # test with multi-node route
            result = await glide_client.lolwut(route=AllNodes())
            assert isinstance(result, dict)
            result_decoded = cast(dict, convert_bytes_to_string_object(result))
            assert result_decoded is not None
            for node_result in result_decoded.values():
                assert "Redis ver. " in node_result

            result = await glide_client.lolwut(parameters=[10, 20], route=AllNodes())
            assert isinstance(result, dict)
            result_decoded = cast(dict, convert_bytes_to_string_object(result))
            assert result_decoded is not None
            for node_result in result_decoded.values():
                assert "Redis ver. " in node_result

            # test with single-node route
            result = await glide_client.lolwut(2, route=RandomNode())
            assert isinstance(result, bytes)
            assert b"Redis ver. " in result

            result = await glide_client.lolwut(2, [10, 20], RandomNode())
            assert isinstance(result, bytes)
            assert b"Redis ver. " in result

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_client_random_key(self, glide_client: GlideClusterClient):
        key = get_random_string(10)

        # setup: delete all keys
        assert await glide_client.flushall(FlushMode.SYNC)

        # no keys exists, so random_key returns None
        assert await glide_client.random_key() is None

        assert await glide_client.set(key, "foo") == OK
        # `key` should be the only existing key, so random_key should return `key`
        assert await glide_client.random_key() == key.encode()
        assert await glide_client.random_key(AllPrimaries()) == key.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_client_random_key(self, glide_client: GlideClient):
        key = get_random_string(10)

        # setup: delete all keys in DB 0 and DB 1
        assert await glide_client.select(0) == OK
        assert await glide_client.flushdb(FlushMode.SYNC) == OK
        assert await glide_client.select(1) == OK
        assert await glide_client.flushdb(FlushMode.SYNC) == OK

        # no keys exist so random_key returns None
        assert await glide_client.random_key() is None
        # set `key` in DB 1
        assert await glide_client.set(key, "foo") == OK
        # `key` should be the only key in the database
        assert await glide_client.random_key() == key.encode()

        # switch back to DB 0
        assert await glide_client.select(0) == OK
        # DB 0 should still have no keys, so random_key should still return None
        assert await glide_client.random_key() is None

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_dump_restore(self, glide_client: TGlideClient):
        key1 = f"{{key}}-1{get_random_string(10)}"
        key2 = f"{{key}}-2{get_random_string(10)}"
        key3 = f"{{key}}-3{get_random_string(10)}"
        nonExistingKey = f"{{key}}-4{get_random_string(10)}"
        value = get_random_string(5)

        await glide_client.set(key1, value)

        # Dump an existing key
        bytesData = await glide_client.dump(key1)
        assert bytesData is not None

        # Dump non-existing key
        assert await glide_client.dump(nonExistingKey) is None

        # Restore to a new key and verify its value
        assert await glide_client.restore(key2, 0, bytesData) == OK
        newValue = await glide_client.get(key2)
        assert newValue == value.encode()

        # Restore to an existing key
        with pytest.raises(RequestError) as e:
            await glide_client.restore(key2, 0, bytesData)
        assert "Target key name already exists" in str(e)

        # Restore using a value with checksum error
        with pytest.raises(RequestError) as e:
            await glide_client.restore(key3, 0, value.encode())
        assert "payload version or checksum are wrong" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_dump_restore_options(self, glide_client: TGlideClient):
        key1 = f"{{key}}-1{get_random_string(10)}"
        key2 = f"{{key}}-2{get_random_string(10)}"
        key3 = f"{{key}}-3{get_random_string(10)}"
        value = get_random_string(5)

        await glide_client.set(key1, value)

        # Dump an existing key
        bytesData = await glide_client.dump(key1)
        assert bytesData is not None

        # Restore without option
        assert await glide_client.restore(key2, 0, bytesData) == OK

        # Restore with REPLACE option
        assert await glide_client.restore(key2, 0, bytesData, replace=True) == OK

        # Restore to an existing key holding different value with REPLACE option
        assert await glide_client.sadd(key3, ["a"]) == 1
        assert await glide_client.restore(key3, 0, bytesData, replace=True) == OK

        # Restore with REPLACE, ABSTTL, and positive TTL
        assert (
            await glide_client.restore(key2, 1000, bytesData, replace=True, absttl=True)
            == OK
        )

        # Restore with REPLACE, ABSTTL, and negative TTL
        with pytest.raises(RequestError) as e:
            await glide_client.restore(key2, -10, bytesData, replace=True, absttl=True)
        assert "Invalid TTL value" in str(e)

        # Restore with REPLACE and positive idletime
        assert (
            await glide_client.restore(key2, 0, bytesData, replace=True, idletime=10)
            == OK
        )

        # Restore with REPLACE and negative idletime
        with pytest.raises(RequestError) as e:
            await glide_client.restore(key2, 0, bytesData, replace=True, idletime=-10)
        assert "Invalid IDLETIME value" in str(e)

        # Restore with REPLACE and positive frequency
        assert (
            await glide_client.restore(key2, 0, bytesData, replace=True, frequency=10)
            == OK
        )

        # Restore with REPLACE and negative frequency
        with pytest.raises(RequestError) as e:
            await glide_client.restore(key2, 0, bytesData, replace=True, frequency=-10)
        assert "Invalid FREQ value" in str(e)

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lcs(self, glide_client: GlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")
        key1 = "testKey1"
        value1 = "abcd"
        key2 = "testKey2"
        value2 = "axcd"
        nonexistent_key = "nonexistent_key"
        expected_subsequence = "acd"
        expected_subsequence_with_nonexistent_key = ""
        assert await glide_client.mset({key1: value1, key2: value2}) == OK
        assert await glide_client.lcs(key1, key2) == expected_subsequence.encode()
        assert (
            await glide_client.lcs(key1, nonexistent_key)
            == expected_subsequence_with_nonexistent_key.encode()
        )
        lcs_non_string_key = "lcs_non_string_key"
        assert await glide_client.sadd(lcs_non_string_key, ["Hello", "world"]) == 2
        with pytest.raises(RequestError):
            await glide_client.lcs(key1, lcs_non_string_key)

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lcs_len(self, glide_client: GlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")
        key1 = "testKey1"
        value1 = "abcd"
        key2 = "testKey2"
        value2 = "axcd"
        nonexistent_key = "nonexistent_key"
        expected_subsequence_length = 3
        expected_subsequence_length_with_nonexistent_key = 0
        assert await glide_client.mset({key1: value1, key2: value2}) == OK
        assert await glide_client.lcs_len(key1, key2) == expected_subsequence_length
        assert (
            await glide_client.lcs_len(key1, nonexistent_key)
            == expected_subsequence_length_with_nonexistent_key
        )
        lcs_non_string_key = "lcs_non_string_key"
        assert await glide_client.sadd(lcs_non_string_key, ["Hello", "world"]) == 2
        with pytest.raises(RequestError):
            await glide_client.lcs_len(key1, lcs_non_string_key)

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lcs_idx(self, glide_client: GlideClient):
        min_version = "7.0.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")
        key1 = "testKey1"
        value1 = "abcd1234"
        key2 = "testKey2"
        value2 = "bcdef1234"
        nonexistent_key = "nonexistent_key"
        expected_response_no_min_match_len_no_with_match_len = {
            b"matches": [
                [
                    [4, 7],
                    [5, 8],
                ],
                [
                    [1, 3],
                    [0, 2],
                ],
            ],
            b"len": 7,
        }
        expected_response_with_min_match_len_equals_four_no_with_match_len = {
            b"matches": [
                [
                    [4, 7],
                    [5, 8],
                ],
            ],
            b"len": 7,
        }
        expected_response_no_min_match_len_with_match_len = {
            b"matches": [
                [
                    [4, 7],
                    [5, 8],
                    4,
                ],
                [
                    [1, 3],
                    [0, 2],
                    3,
                ],
            ],
            b"len": 7,
        }
        expected_response_with_min_match_len_equals_four_and_with_match_len = {
            b"matches": [
                [
                    [4, 7],
                    [5, 8],
                    4,
                ],
            ],
            b"len": 7,
        }
        expected_response_with_nonexistent_key = {
            b"matches": [],
            b"len": 0,
        }
        assert await glide_client.mset({key1: value1, key2: value2}) == OK
        assert (
            await glide_client.lcs_idx(key1, key2)
            == expected_response_no_min_match_len_no_with_match_len
        )
        assert (
            await glide_client.lcs_idx(key1, key2, min_match_len=4)
            == expected_response_with_min_match_len_equals_four_no_with_match_len
        )
        assert (
            # negative min_match_len should have no affect on the output
            await glide_client.lcs_idx(key1, key2, min_match_len=-3)
            == expected_response_no_min_match_len_no_with_match_len
        )
        assert (
            await glide_client.lcs_idx(key1, key2, with_match_len=True)
            == expected_response_no_min_match_len_with_match_len
        )
        assert (
            await glide_client.lcs_idx(key1, key2, min_match_len=4, with_match_len=True)
            == expected_response_with_min_match_len_equals_four_and_with_match_len
        )
        assert (
            await glide_client.lcs_idx(key1, nonexistent_key)
            == expected_response_with_nonexistent_key
        )
        lcs_non_string_key = "lcs_non_string_key"
        assert await glide_client.sadd(lcs_non_string_key, ["Hello", "world"]) == 2
        with pytest.raises(RequestError):
            await glide_client.lcs_idx(key1, lcs_non_string_key)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_watch(self, glide_client: GlideClient):
        # watched key didn't change outside of transaction before transaction execution, transaction will execute
        assert await glide_client.set("key1", "original_value") == OK
        assert await glide_client.watch(["key1"]) == OK
        transaction = Transaction()
        transaction.set("key1", "transaction_value")
        transaction.get("key1")
        assert await glide_client.exec(transaction) is not None

        # watched key changed outside of transaction before transaction execution, transaction will not execute
        assert await glide_client.set("key1", "original_value") == OK
        assert await glide_client.watch(["key1"]) == OK
        transaction = Transaction()
        transaction.set("key1", "transaction_value")
        assert await glide_client.set("key1", "standalone_value") == OK
        transaction.get("key1")
        assert await glide_client.exec(transaction) is None

        # empty list not supported
        with pytest.raises(RequestError):
            await glide_client.watch([])

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_unwatch(self, glide_client: GlideClient):

        # watched key unwatched before transaction execution even if changed
        # outside of transaction, transaction will still execute
        assert await glide_client.set("key1", "original_value") == OK
        assert await glide_client.watch(["key1"]) == OK
        transaction = Transaction()
        transaction.set("key1", "transaction_value")
        assert await glide_client.set("key1", "standalone_value") == OK
        transaction.get("key1")
        assert await glide_client.unwatch() == OK
        result = await glide_client.exec(transaction)
        assert result is not None
        assert isinstance(result, list)
        assert len(result) == 2
        assert result[0] == "OK"
        assert result[1] == b"transaction_value"

        # UNWATCH returns OK when there no watched keys
        assert await glide_client.unwatch() == OK

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_unwatch_with_route(self, glide_client: GlideClusterClient):
        assert await glide_client.unwatch(RandomNode()) == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lpos(self, glide_client: TGlideClient):
        min_version = "6.0.6"
        if await check_if_server_version_lt(glide_client, min_version):
            # TODO: change it to pytest fixture after we'll implement a sync client
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")
        key = f"{{key}}-1{get_random_string(5)}"
        non_list_key = f"{{key}}-2{get_random_string(5)}"
        mylist: List[TEncodable] = ["a", "a", "b", "c", "a", "b"]

        # basic case
        await glide_client.rpush(key, mylist)
        assert await glide_client.lpos(key, "b") == 2

        # reverse traversal
        assert await glide_client.lpos(key, "b", -2) == 2

        # unlimited comparisons
        assert await glide_client.lpos(key, "a", 1, None, 0) == 0

        # limited comparisons
        assert await glide_client.lpos(key, "c", 1, None, 2) is None

        # element does not exist
        assert await glide_client.lpos(key, "non_existing") is None

        # with count
        assert await glide_client.lpos(key, "a", 1, 0, 0) == [0, 1, 4]

        # with count and rank
        assert await glide_client.lpos(key, "a", -2, 0, 0) == [1, 0]

        # key does not exist
        assert await glide_client.lpos("non_existing", "non_existing") is None

        # invalid rank value
        with pytest.raises(RequestError):
            await glide_client.lpos(key, "a", 0)

        # invalid count
        with pytest.raises(RequestError):
            await glide_client.lpos(non_list_key, "a", None, -1)

        # invalid max_len
        with pytest.raises(RequestError):
            await glide_client.lpos(non_list_key, "a", None, None, -1)

        # wrong data type
        await glide_client.set(non_list_key, "non_list_value")
        with pytest.raises(RequestError):
            await glide_client.lpos(non_list_key, "a")


class TestMultiKeyCommandCrossSlot:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_multi_key_command_returns_cross_slot_error(
        self, glide_client: GlideClusterClient
    ):
        promises: List[Any] = [
            glide_client.blpop(["abc", "zxy", "lkn"], 0.1),
            glide_client.brpop(["abc", "zxy", "lkn"], 0.1),
            glide_client.rename("abc", "zxy"),
            glide_client.zdiffstore("abc", ["zxy", "lkn"]),
            glide_client.zdiff(["abc", "zxy", "lkn"]),
            glide_client.zdiff_withscores(["abc", "zxy", "lkn"]),
            glide_client.zrangestore("abc", "zxy", RangeByIndex(0, -1)),
            glide_client.zinterstore(
                "{xyz}", cast(Union[List[Union[TEncodable]]], ["{abc}", "{def}"])
            ),
            glide_client.zunionstore(
                "{xyz}", cast(Union[List[Union[TEncodable]]], ["{abc}", "{def}"])
            ),
            glide_client.bzpopmin(["abc", "zxy", "lkn"], 0.5),
            glide_client.bzpopmax(["abc", "zxy", "lkn"], 0.5),
            glide_client.smove("abc", "def", "_"),
            glide_client.sunionstore("abc", ["zxy", "lkn"]),
            glide_client.sinter(["abc", "zxy", "lkn"]),
            glide_client.sinterstore("abc", ["zxy", "lkn"]),
            glide_client.sdiff(["abc", "zxy", "lkn"]),
            glide_client.sdiffstore("abc", ["def", "ghi"]),
            glide_client.renamenx("abc", "def"),
            glide_client.pfcount(["def", "ghi"]),
            glide_client.pfmerge("abc", ["def", "ghi"]),
            glide_client.zinter(["def", "ghi"]),
            glide_client.zinter_withscores(
                cast(Union[List[TEncodable]], ["def", "ghi"])
            ),
            glide_client.zunion(["def", "ghi"]),
            glide_client.zunion_withscores(cast(List[TEncodable], ["def", "ghi"])),
            glide_client.sort_store("abc", "zxy"),
            glide_client.lmove("abc", "zxy", ListDirection.LEFT, ListDirection.LEFT),
            glide_client.blmove(
                "abc", "zxy", ListDirection.LEFT, ListDirection.LEFT, 1
            ),
            glide_client.msetnx({"abc": "abc", "zxy": "zyx"}),
            glide_client.sunion(["def", "ghi"]),
            glide_client.bitop(BitwiseOperation.OR, "abc", ["zxy", "lkn"]),
            glide_client.xread({"abc": "0-0", "zxy": "0-0"}),
        ]

        if not await check_if_server_version_lt(glide_client, "6.2.0"):
            promises.extend(
                [
                    glide_client.geosearchstore(
                        "abc",
                        "zxy",
                        GeospatialData(15, 37),
                        GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
                    ),
                    glide_client.copy("abc", "zxy", replace=True),
                ]
            )

        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            promises.extend(
                [
                    glide_client.bzmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX, 0.1),
                    glide_client.zintercard(["abc", "def"]),
                    glide_client.zmpop(["abc", "zxy", "lkn"], ScoreFilter.MAX),
                    glide_client.sintercard(["def", "ghi"]),
                    glide_client.lmpop(["def", "ghi"], ListDirection.LEFT),
                    glide_client.blmpop(["def", "ghi"], ListDirection.LEFT, 1),
                    glide_client.lcs("abc", "def"),
                    glide_client.lcs_len("abc", "def"),
                    glide_client.lcs_idx("abc", "def"),
                    glide_client.fcall("func", ["abc", "zxy", "lkn"], []),
                    glide_client.fcall_ro("func", ["abc", "zxy", "lkn"], []),
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
        self, glide_client: GlideClusterClient
    ):
        await glide_client.exists(["abc", "zxy", "lkn"])
        await glide_client.unlink(["abc", "zxy", "lkn"])
        await glide_client.delete(["abc", "zxy", "lkn"])
        await glide_client.mget(["abc", "zxy", "lkn"])
        await glide_client.mset({"abc": "1", "zxy": "2", "lkn": "3"})
        await glide_client.touch(["abc", "zxy", "lkn"])
        await glide_client.watch(["abc", "zxy", "lkn"])


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
        glide_client: GlideClusterClient,
        route: Route,
    ):
        cluster_nodes = await glide_client.custom_command(["CLUSTER", "NODES"])
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

        all_results = await glide_client.custom_command(["INFO", "REPLICATION"], route)
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
    async def test_cluster_route_custom_command_all_nodes(
        self, glide_client: GlideClusterClient
    ):
        await self.cluster_route_custom_command_multi_nodes(glide_client, AllNodes())

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_all_primaries(
        self, glide_client: GlideClusterClient
    ):
        await self.cluster_route_custom_command_multi_nodes(
            glide_client, AllPrimaries()
        )

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_random_node(
        self, glide_client: GlideClusterClient
    ):
        info_res = await glide_client.custom_command(
            ["INFO", "REPLICATION"], RandomNode()
        )
        assert isinstance(info_res, bytes)
        info_res = info_res.decode()
        assert type(info_res) is str
        assert "role:master" in info_res or "role:slave" in info_res

    async def cluster_route_custom_command_slot_route(
        self, glide_client: GlideClusterClient, is_slot_key: bool
    ):
        route_class = SlotKeyRoute if is_slot_key else SlotIdRoute
        route_second_arg = "foo" if is_slot_key else 4000
        primary_res = await glide_client.custom_command(
            ["CLUSTER", "NODES"], route_class(SlotType.PRIMARY, route_second_arg)
        )
        assert isinstance(primary_res, bytes)
        primary_res = primary_res.decode()

        assert type(primary_res) is str
        assert "myself,master" in primary_res
        expected_primary_node_id = ""
        for node_line in primary_res.splitlines():
            if "myself" in node_line:
                expected_primary_node_id = node_line.split(" ")[0]

        replica_res = await glide_client.custom_command(
            ["CLUSTER", "NODES"], route_class(SlotType.REPLICA, route_second_arg)
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
    async def test_cluster_route_custom_command_slot_key_route(
        self, glide_client: GlideClusterClient
    ):
        await self.cluster_route_custom_command_slot_route(glide_client, True)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_custom_command_slot_id_route(
        self, glide_client: GlideClusterClient
    ):
        await self.cluster_route_custom_command_slot_route(glide_client, False)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_info_random_route(self, glide_client: GlideClusterClient):
        info = await glide_client.info([InfoSection.SERVER], RandomNode())
        assert b"# Server" in info

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_route_by_address_reaches_correct_node(
        self, glide_client: GlideClusterClient
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

        cluster_nodes = await glide_client.custom_command(
            ["cluster", "nodes"], RandomNode()
        )
        assert isinstance(cluster_nodes, bytes)
        cluster_nodes = clean_result(cluster_nodes.decode())

        assert isinstance(cluster_nodes, str)
        host = cluster_nodes.split(" ")[1].split("@")[0]

        second_result = await glide_client.custom_command(
            ["cluster", "nodes"], ByAddressRoute(host)
        )
        assert isinstance(second_result, bytes)
        second_result = clean_result(second_result.decode())

        assert cluster_nodes == second_result

        host, port = host.split(":")
        port_as_int = int(port)

        third_result = await glide_client.custom_command(
            ["cluster", "nodes"], ByAddressRoute(host, port_as_int)
        )
        assert isinstance(third_result, bytes)
        third_result = clean_result(third_result.decode())

        assert cluster_nodes == third_result

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_fail_routing_by_address_if_no_port_is_provided(
        self, glide_client: GlideClusterClient
    ):
        with pytest.raises(RequestError):
            await glide_client.info(route=ByAddressRoute("foo"))

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_flushdb(self, glide_client: GlideClusterClient):
        min_version = "6.2.0"
        key = f"{{key}}-1{get_random_string(5)}"
        value = get_random_string(5)

        await glide_client.set(key, value)
        assert await glide_client.dbsize() > 0
        assert await glide_client.flushdb(route=AllPrimaries()) == OK
        assert await glide_client.dbsize() == 0

        await glide_client.set(key, value)
        assert await glide_client.dbsize() > 0
        assert await glide_client.flushdb(FlushMode.ASYNC, AllPrimaries()) == OK
        assert await glide_client.dbsize() == 0

        if not await check_if_server_version_lt(glide_client, min_version):
            await glide_client.set(key, value)
            assert await glide_client.dbsize() > 0
            assert await glide_client.flushdb(FlushMode.SYNC, AllPrimaries()) == OK
            assert await glide_client.dbsize() == 0

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_sscan(self, glide_client: GlideClusterClient):
        key1 = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        initial_cursor = "0"
        result_cursor_index = 0
        result_collection_index = 1
        default_count = 10
        num_members: List[TEncodable] = list(
            map(str, range(50000))
        )  # Use large dataset to force an iterative cursor.
        char_members: List[TEncodable] = ["a", "b", "c", "d", "e"]

        # Empty set
        result = await glide_client.sscan(key1, initial_cursor)
        assert result[result_cursor_index] == initial_cursor.encode()
        assert result[result_collection_index] == []

        # Negative cursor
        if await check_if_server_version_lt(glide_client, "7.9.0"):
            result = await glide_client.sscan(key1, "-1")
            assert result[result_cursor_index] == initial_cursor.encode()
            assert result[result_collection_index] == []
        else:
            with pytest.raises(RequestError):
                await glide_client.sscan(key2, "-1")

        # Result contains the whole set
        assert await glide_client.sadd(key1, char_members) == len(char_members)
        result = await glide_client.sscan(key1, initial_cursor)
        assert result[result_cursor_index] == initial_cursor.encode()
        assert len(result[result_collection_index]) == len(char_members)
        assert set(result[result_collection_index]).issubset(
            cast(list, convert_string_to_bytes_object(char_members))
        )

        result = await glide_client.sscan(key1, initial_cursor, match="a")
        assert result[result_cursor_index] == initial_cursor.encode()
        assert set(result[result_collection_index]).issubset(set([b"a"]))

        # Result contains a subset of the key
        assert await glide_client.sadd(key1, num_members) == len(num_members)
        result_cursor = "0"
        result_values = set()  # type: set[bytes]
        result = cast(
            list,
            convert_bytes_to_string_object(
                await glide_client.sscan(key1, result_cursor)
            ),
        )
        result_cursor = str(result[result_cursor_index])
        result_values.update(result[result_collection_index])  # type: ignore

        # 0 is returned for the cursor of the last iteration.
        while result_cursor != "0":
            next_result = cast(
                list,
                convert_bytes_to_string_object(
                    await glide_client.sscan(key1, result_cursor)
                ),
            )
            next_result_cursor = str(next_result[result_cursor_index])
            assert next_result_cursor != result_cursor

            assert not set(result[result_collection_index]).issubset(
                set(next_result[result_collection_index])
            )
            result_values.update(next_result[result_collection_index])
            result = next_result
            result_cursor = next_result_cursor
        assert set(num_members).issubset(result_values)
        assert set(char_members).issubset(result_values)

        # Test match pattern
        result = await glide_client.sscan(key1, initial_cursor, match="*")
        assert result[result_cursor_index] != "0"
        assert len(result[result_collection_index]) >= default_count

        # Test count
        result = await glide_client.sscan(key1, initial_cursor, count=20)
        assert result[result_cursor_index] != "0"
        assert len(result[result_collection_index]) >= 20

        # Test count with match returns a non-empty list
        result = await glide_client.sscan(key1, initial_cursor, match="1*", count=20)
        assert result[result_cursor_index] != "0"
        assert len(result[result_collection_index]) >= 0

        # Exceptions
        # Non-set key
        assert await glide_client.set(key2, "test") == OK
        with pytest.raises(RequestError):
            await glide_client.sscan(key2, initial_cursor)
        with pytest.raises(RequestError):
            await glide_client.sscan(key2, initial_cursor, match="test", count=20)

        # Negative count
        with pytest.raises(RequestError):
            await glide_client.sscan(key2, initial_cursor, count=-1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_zscan(self, glide_client: GlideClusterClient):
        key1 = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        initial_cursor = "0"
        result_cursor_index = 0
        result_collection_index = 1
        default_count = 20
        num_map: Dict[TEncodable, float] = {}
        num_map_with_str_scores = {}
        for i in range(50000):  # Use large dataset to force an iterative cursor.
            num_map.update({"value " + str(i): i})
            num_map_with_str_scores.update({"value " + str(i): str(i)})
        char_map: Mapping[TEncodable, float] = {"a": 0, "b": 1, "c": 2, "d": 3, "e": 4}
        char_map_with_str_scores = {
            "a": "0",
            "b": "1",
            "c": "2",
            "d": "3",
            "e": "4",
        }

        convert_list_to_dict = lambda list: {
            list[i]: list[i + 1] for i in range(0, len(list), 2)
        }

        # Empty set
        result = await glide_client.zscan(key1, initial_cursor)
        assert result[result_cursor_index] == initial_cursor.encode()
        assert result[result_collection_index] == []

        # Negative cursor
        if await check_if_server_version_lt(glide_client, "7.9.0"):
            result = await glide_client.zscan(key1, "-1")
            assert result[result_cursor_index] == initial_cursor.encode()
            assert result[result_collection_index] == []
        else:
            with pytest.raises(RequestError):
                await glide_client.zscan(key2, "-1")

        # Result contains the whole set
        assert await glide_client.zadd(key1, char_map) == len(char_map)
        result = await glide_client.zscan(key1, initial_cursor)
        result_collection = result[result_collection_index]
        assert result[result_cursor_index] == initial_cursor.encode()
        assert len(result_collection) == len(char_map) * 2
        assert convert_list_to_dict(result_collection) == cast(
            list, convert_string_to_bytes_object(char_map_with_str_scores)
        )

        result = await glide_client.zscan(key1, initial_cursor, match="a")
        result_collection = result[result_collection_index]
        assert result[result_cursor_index] == initial_cursor.encode()
        assert convert_list_to_dict(result_collection) == {b"a": b"0"}

        # Result contains a subset of the key
        assert await glide_client.zadd(key1, num_map) == len(num_map)
        full_result_map = {}
        result = result = cast(
            list,
            convert_bytes_to_string_object(
                await glide_client.zscan(key1, initial_cursor)
            ),
        )
        result_cursor = str(result[result_cursor_index])
        result_iteration_collection: Dict[str, str] = convert_list_to_dict(
            result[result_collection_index]
        )
        full_result_map.update(result_iteration_collection)

        # 0 is returned for the cursor of the last iteration.
        while result_cursor != "0":
            next_result = cast(
                list,
                convert_bytes_to_string_object(
                    await glide_client.zscan(key1, result_cursor)
                ),
            )
            next_result_cursor = next_result[result_cursor_index]
            assert next_result_cursor != result_cursor

            next_result_collection = convert_list_to_dict(
                next_result[result_collection_index]
            )
            assert result_iteration_collection != next_result_collection

            full_result_map.update(next_result_collection)
            result_iteration_collection = next_result_collection
            result_cursor = next_result_cursor
        num_map_with_str_scores.update(char_map_with_str_scores)
        assert num_map_with_str_scores == full_result_map

        # Test match pattern
        result = await glide_client.zscan(key1, initial_cursor, match="*")
        assert result[result_cursor_index] != b"0"
        assert len(result[result_collection_index]) >= default_count

        # Test count
        result = await glide_client.zscan(key1, initial_cursor, count=20)
        assert result[result_cursor_index] != b"0"
        assert len(result[result_collection_index]) >= 20

        # Test count with match returns a non-empty list
        result = await glide_client.zscan(key1, initial_cursor, match="1*", count=20)
        assert result[result_cursor_index] != b"0"
        assert len(result[result_collection_index]) >= 0

        # Test no_scores option
        if not await check_if_server_version_lt(glide_client, "7.9.0"):
            result = await glide_client.zscan(key1, initial_cursor, no_scores=True)
            assert result[result_cursor_index] != b"0"
            values_array = cast(List[bytes], result[result_collection_index])
            # Verify that scores are not included
            assert all(
                item.startswith(b"value") and item.isascii() for item in values_array
            )

        # Exceptions
        # Non-set key
        assert await glide_client.set(key2, "test") == OK
        with pytest.raises(RequestError):
            await glide_client.zscan(key2, initial_cursor)
        with pytest.raises(RequestError):
            await glide_client.zscan(key2, initial_cursor, match="test", count=20)

        # Negative count
        with pytest.raises(RequestError):
            await glide_client.zscan(key2, initial_cursor, count=-1)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hscan(self, glide_client: GlideClusterClient):
        key1 = f"{{key}}-1{get_random_string(5)}"
        key2 = f"{{key}}-2{get_random_string(5)}"
        initial_cursor = "0"
        result_cursor_index = 0
        result_collection_index = 1
        default_count = 20
        num_map: dict[TEncodable, TEncodable] = {}
        for i in range(50000):  # Use large dataset to force an iterative cursor.
            num_map.update({"field " + str(i): "value " + str(i)})
        char_map: Dict[TEncodable, TEncodable] = {
            "field a": "value a",
            "field b": "value b",
            "field c": "value c",
            "field d": "value d",
            "field e": "value e",
        }

        convert_list_to_dict = lambda list: {
            list[i]: list[i + 1] for i in range(0, len(list), 2)
        }

        # Empty set
        result = await glide_client.hscan(key1, initial_cursor)
        assert result[result_cursor_index] == initial_cursor.encode()
        assert result[result_collection_index] == []

        # Negative cursor
        if await check_if_server_version_lt(glide_client, "7.9.0"):
            result = await glide_client.hscan(key1, "-1")
            assert result[result_cursor_index] == initial_cursor.encode()
            assert result[result_collection_index] == []
        else:
            with pytest.raises(RequestError):
                await glide_client.hscan(key2, "-1")

        # Result contains the whole set
        assert await glide_client.hset(key1, char_map) == len(char_map)
        result = await glide_client.hscan(key1, initial_cursor)
        result_collection = result[result_collection_index]
        assert result[result_cursor_index] == initial_cursor.encode()
        assert len(result_collection) == len(char_map) * 2
        assert convert_list_to_dict(result_collection) == cast(
            dict, convert_string_to_bytes_object(char_map)  # type: ignore
        )

        result = await glide_client.hscan(key1, initial_cursor, match="field a")
        result_collection = result[result_collection_index]
        assert result[result_cursor_index] == initial_cursor.encode()
        assert convert_list_to_dict(result_collection) == {b"field a": b"value a"}

        # Result contains a subset of the key
        assert await glide_client.hset(key1, num_map) == len(num_map)
        full_result_map = {}
        result = result = cast(
            list,
            convert_bytes_to_string_object(
                await glide_client.hscan(key1, initial_cursor)
            ),
        )
        result_cursor = str(result[result_cursor_index])
        result_iteration_collection: Dict[str, str] = convert_list_to_dict(
            result[result_collection_index]
        )
        full_result_map.update(result_iteration_collection)

        # 0 is returned for the cursor of the last iteration.
        while result_cursor != "0":
            next_result = cast(
                list,
                convert_bytes_to_string_object(
                    await glide_client.hscan(key1, result_cursor)
                ),
            )
            next_result_cursor = next_result[result_cursor_index]
            assert next_result_cursor != result_cursor

            next_result_collection = convert_list_to_dict(
                next_result[result_collection_index]
            )
            assert result_iteration_collection != next_result_collection

            full_result_map.update(next_result_collection)
            result_iteration_collection = next_result_collection
            result_cursor = next_result_cursor
        num_map.update(char_map)
        assert num_map == full_result_map

        # Test match pattern
        result = await glide_client.hscan(key1, initial_cursor, match="*")
        assert result[result_cursor_index] != b"0"
        assert len(result[result_collection_index]) >= default_count

        # Test count
        result = await glide_client.hscan(key1, initial_cursor, count=20)
        assert result[result_cursor_index] != b"0"
        assert len(result[result_collection_index]) >= 20

        # Test count with match returns a non-empty list
        result = await glide_client.hscan(key1, initial_cursor, match="1*", count=20)
        assert result[result_cursor_index] != b"0"
        assert len(result[result_collection_index]) >= 0

        # Test no_values option
        if not await check_if_server_version_lt(glide_client, "7.9.0"):
            result = await glide_client.hscan(key1, initial_cursor, no_values=True)
            assert result[result_cursor_index] != b"0"
            values_array = cast(List[bytes], result[result_collection_index])
            # Verify that values are not included
            assert all(
                item.startswith(b"field") and item.isascii() for item in values_array
            )

        # Exceptions
        # Non-hash key
        assert await glide_client.set(key2, "test") == OK
        with pytest.raises(RequestError):
            await glide_client.hscan(key2, initial_cursor)
        with pytest.raises(RequestError):
            await glide_client.hscan(key2, initial_cursor, match="test", count=20)

        # Negative count
        with pytest.raises(RequestError):
            await glide_client.hscan(key2, initial_cursor, count=-1)


async def script_kill_tests(
    glide_client: TGlideClient, test_client: TGlideClient, route: Optional[Route] = None
):
    """
    shared tests for SCRIPT KILL used in routed and non-routed variants, clients are created in
    respective tests with different test matrices.
    """
    # Verify that script_kill raises an error when no script is running
    with pytest.raises(RequestError) as e:
        await glide_client.script_kill()
    assert "No scripts in execution right now" in str(e)

    # Create a long-running script
    long_script = Script(create_long_running_lua_script(10))

    async def run_long_script():
        with pytest.raises(RequestError) as e:
            if route is not None:
                await test_client.invoke_script_route(long_script, route=route)
            else:
                await test_client.invoke_script(long_script)
        assert "Script killed by user" in str(e)

    async def wait_and_kill_script():
        await asyncio.sleep(3)  # Give some time for the script to start
        timeout = 0
        while timeout <= 5:
            # keep trying to kill until we get an "OK"
            try:
                if route is not None:
                    result = await cast(GlideClusterClient, glide_client).script_kill(
                        route=route
                    )
                else:
                    result = await glide_client.script_kill()
                #  we expect to get success
                assert result == "OK"
                break
            except RequestError:
                # a RequestError may occur if the script is not yet running
                # sleep and try again
                timeout += 0.5
                await asyncio.sleep(0.5)

    # Run the long script and kill it
    await asyncio.gather(
        run_long_script(),
        wait_and_kill_script(),
    )

    # Verify that script_kill raises an error when no script is running
    with pytest.raises(RequestError) as e:
        if route is not None:
            await cast(GlideClusterClient, glide_client).script_kill(route=route)
        else:
            await glide_client.script_kill()
    assert "No scripts in execution right now" in str(e)

    await test_client.close()


@pytest.mark.asyncio
class TestScripts:
    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        script = Script("return 'Hello'")
        assert await glide_client.invoke_script(script) == "Hello".encode()

        script = Script("return redis.call('SET', KEYS[1], ARGV[1])")
        assert (
            await glide_client.invoke_script(script, keys=[key1], args=["value1"])
            == "OK"
        )
        # Reuse the same script with different parameters.
        assert (
            await glide_client.invoke_script(script, keys=[key2], args=["value2"])
            == "OK"
        )
        script = Script("return redis.call('GET', KEYS[1])")
        assert (
            await glide_client.invoke_script(script, keys=[key1]) == "value1".encode()
        )
        assert (
            await glide_client.invoke_script(script, keys=[key2]) == "value2".encode()
        )

    @pytest.mark.smoke_test
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_binary(self, glide_client: TGlideClient):
        key1 = bytes(get_random_string(10), "utf-8")
        key2 = bytes(get_random_string(10), "utf-8")
        script = Script(bytes("return 'Hello'", "utf-8"))
        assert await glide_client.invoke_script(script) == "Hello".encode()

        script = Script(bytes("return redis.call('SET', KEYS[1], ARGV[1])", "utf-8"))
        assert (
            await glide_client.invoke_script(
                script, keys=[key1], args=[bytes("value1", "utf-8")]
            )
            == "OK"
        )
        # Reuse the same script with different parameters.
        assert (
            await glide_client.invoke_script(
                script, keys=[key2], args=[bytes("value2", "utf-8")]
            )
            == "OK"
        )
        script = Script(bytes("return redis.call('GET', KEYS[1])", "utf-8"))
        assert (
            await glide_client.invoke_script(script, keys=[key1]) == "value1".encode()
        )
        assert (
            await glide_client.invoke_script(script, keys=[key2]) == "value2".encode()
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_large_keys_no_args(self, request, cluster_mode, protocol):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=5000
        )
        length = 2**13  # 8kb
        key = "0" * length
        script = Script("return KEYS[1]")
        assert await glide_client.invoke_script(script, keys=[key]) == key.encode()
        await glide_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_large_args_no_keys(self, request, cluster_mode, protocol):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=5000
        )
        length = 2**12  # 4kb
        arg1 = "0" * length
        arg2 = "1" * length

        script = Script("return ARGV[2]")
        assert (
            await glide_client.invoke_script(script, args=[arg1, arg2]) == arg2.encode()
        )
        await glide_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_large_keys_and_args(self, request, cluster_mode, protocol):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=5000
        )
        length = 2**12  # 4kb
        key = "0" * length
        arg = "1" * length

        script = Script("return KEYS[1]")
        assert (
            await glide_client.invoke_script(script, keys=[key], args=[arg])
            == key.encode()
        )
        await glide_client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_exists(self, glide_client: TGlideClient, cluster_mode: bool):
        cluster_mode = isinstance(glide_client, GlideClusterClient)
        script1 = Script("return 'Hello'")
        script2 = Script("return 'World'")
        script3 = Script("return 'Hello World'")

        # Load script1 to all nodes, do not load script2 and load script3 with a SlotKeyRoute
        await glide_client.invoke_script(script1)

        if cluster_mode:
            await cast(GlideClusterClient, glide_client).invoke_script_route(
                script3, route=SlotKeyRoute(SlotType.PRIMARY, "1")
            )
        else:
            await glide_client.invoke_script(script3)

        # Get the SHA1 digests of the scripts
        sha1_1 = script1.get_hash()
        sha1_2 = script2.get_hash()
        sha1_3 = script3.get_hash()
        non_existent_sha1 = "0" * 40  # A SHA1 that doesn't exist
        # Check existence of scripts
        result = await glide_client.script_exists(
            [sha1_1, sha1_2, sha1_3, non_existent_sha1]
        )

        # script1 is loaded and returns true.
        # script2 is only cached and not loaded, returns false.
        # script3 is invoked with a SlotKeyRoute. Despite SCRIPT EXIST uses LogicalAggregate AND on the results,
        #   SCRIPT LOAD during internal execution so the script still gets loaded on all nodes, returns true.
        # non-existing sha1 returns false.
        assert result == [True, False, True, False]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_flush(self, glide_client: TGlideClient):
        # Load a script
        script = Script("return 'Hello'")
        await glide_client.invoke_script(script)

        # Check that the script exists
        assert await glide_client.script_exists([script.get_hash()]) == [True]

        # Flush the script cache
        assert await glide_client.script_flush() == OK

        # Check that the script no longer exists
        assert await glide_client.script_exists([script.get_hash()]) == [False]

        # Test with ASYNC mode
        await glide_client.invoke_script(script)
        assert await glide_client.script_flush(FlushMode.ASYNC) == OK
        assert await glide_client.script_exists([script.get_hash()]) == [False]

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("single_route", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_kill_route(
        self,
        request,
        cluster_mode,
        protocol,
        glide_client: TGlideClient,
        single_route: bool,
    ):
        route = SlotKeyRoute(SlotType.PRIMARY, "1") if single_route else AllPrimaries()

        # Create a second client to run the script
        test_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=30000
        )

        await script_kill_tests(glide_client, test_client, route)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_kill_no_route(
        self,
        request,
        cluster_mode,
        protocol,
        glide_client: TGlideClient,
    ):
        # Create a second client to run the script
        test_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=30000
        )

        await script_kill_tests(glide_client, test_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_kill_unkillable(
        self, request, cluster_mode, protocol, glide_client: TGlideClient
    ):
        # Create a second client to run the script
        test_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=30000
        )

        # Create a second client to kill the script
        test_client2 = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, timeout=15000
        )

        # Add test for script_kill with writing script
        writing_script = Script(
            """
            redis.call('SET', KEYS[1], 'value')
            local start = redis.call('TIME')[1]
            while redis.call('TIME')[1] - start < 15 do
                redis.call('SET', KEYS[1], 'value')
            end
        """
        )

        async def run_writing_script():
            await test_client.invoke_script(writing_script, keys=[get_random_string(5)])

        async def attempt_kill_writing_script():
            await asyncio.sleep(3)  # Give some time for the script to start
            foundUnkillable = False
            while True:
                try:
                    await test_client2.script_kill()
                except RequestError as e:
                    if "UNKILLABLE" in str(e):
                        foundUnkillable = True
                        break
                    await asyncio.sleep(0.5)

            assert foundUnkillable

        # Run the writing script and attempt to kill it
        await asyncio.gather(
            run_writing_script(),
            attempt_kill_writing_script(),
        )

        await test_client.close()
        await test_client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_script_show(self, glide_client: TGlideClient):
        min_version = "7.9.0"
        if await check_if_server_version_lt(glide_client, min_version):
            return pytest.mark.skip(reason=f"Valkey version required >= {min_version}")

        code = f"return '{get_random_string(5)}'"
        script = Script(code)

        # Load the scripts
        await glide_client.invoke_script(script)

        # Get the SHA1 digests of the script
        sha1 = script.get_hash()

        assert await glide_client.script_show(sha1) == code.encode()

        with pytest.raises(RequestError):
            await glide_client.script_show("non existing sha1")
