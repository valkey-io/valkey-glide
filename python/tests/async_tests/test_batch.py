# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


import re
import time
from datetime import date, timedelta
from typing import List, Optional, Union, cast

import pytest
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide_shared import RequestError, TimeoutError
from glide_shared.commands.batch import (
    BaseBatch,
    Batch,
    ClusterBatch,
    ClusterTransaction,
    Transaction,
)
from glide_shared.commands.batch_options import (
    BatchOptions,
    BatchRetryStrategy,
    ClusterBatchOptions,
)
from glide_shared.commands.command_args import OrderBy
from glide_shared.commands.core_options import (
    ExpireOptions,
    ExpiryGetEx,
    ExpirySet,
    ExpiryType,
    ExpiryTypeGetEx,
    FlushMode,
    FunctionRestorePolicy,
    HashFieldConditionalChange,
    InfoSection,
)
from glide_shared.commands.stream import StreamAddOptions
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK, TResult, TSingleNodeRoute
from glide_shared.routes import AllNodes, SlotIdRoute, SlotKeyRoute, SlotType

from tests.async_tests.conftest import create_client
from tests.utils.utils import (
    batch_test,
    check_if_server_version_lt,
    convert_bytes_to_string_object,
    generate_lua_lib_code,
    get_random_string,
    get_version,
)


async def exec_batch(
    glide_client: TGlideClient,
    batch: BaseBatch,
    route: Optional[TSingleNodeRoute] = None,
    timeout: Optional[int] = None,
    raise_on_error: bool = False,
) -> Optional[List[TResult]]:
    if isinstance(glide_client, GlideClient):
        batch_options = BatchOptions(timeout=timeout)
        return await cast(GlideClient, glide_client).exec(
            cast(Batch, batch), raise_on_error, batch_options
        )
    else:
        cluster_options = ClusterBatchOptions(
            route=route,
            timeout=timeout,
        )
        return await cast(GlideClusterClient, glide_client).exec(
            cast(ClusterBatch, batch),
            raise_on_error,
            cluster_options,
        )


@pytest.mark.anyio
class TestBatch:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_with_different_slots(
        self, glide_client: GlideClusterClient
    ):
        transaction = ClusterBatch(is_atomic=True)
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(RequestError, match="CrossSlot"):
            await glide_client.exec(transaction, raise_on_error=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_command(self, glide_client: TGlideClient):
        key = get_random_string(10)
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = await exec_batch(glide_client, transaction)
        assert result == [1, b"bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_custom_unsupported_command(
        self, glide_client: TGlideClient
    ):
        key = get_random_string(10)
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.custom_command(["WATCH", key])
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, transaction)

        assert "not allowed" in str(e)  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_discard_command(self, glide_client: TGlideClient):
        key = get_random_string(10)
        await glide_client.set(key, "1")
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await glide_client.get(key)
        assert value == b"1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_exec_abort(self, glide_client: TGlideClient):
        key = get_random_string(10)
        transaction = BaseBatch(is_atomic=True)
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, transaction)
        assert "wrong number of arguments" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_batch(self, glide_client: GlideClusterClient, is_atomic):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK
        version = await get_version(glide_client)
        keyslot = get_random_string(3) if is_atomic else None
        batch = ClusterBatch(is_atomic=is_atomic)
        batch.info()
        publish_result_index = 1
        if await check_if_server_version_lt(glide_client, "7.0.0"):
            batch.publish("test_message", keyslot or get_random_string(3), False)
        else:
            batch.publish("test_message", keyslot or get_random_string(3), True)

        expected = batch_test(batch, version, keyslot)

        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            batch.pubsub_shardchannels()
            expected.append(cast(TResult, []))
            batch.pubsub_shardnumsub()
            expected.append(cast(TResult, {}))

        result = await glide_client.exec(batch, raise_on_error=True)
        assert isinstance(result, list)

        info_response = result[0]
        if is_atomic:
            assert isinstance(info_response, bytes)
            assert b"# Memory" in info_response
        else:
            assert isinstance(info_response, dict)
            assert len(info_response) > 0
            for node_info in info_response.values():
                assert isinstance(node_info, bytes)
                assert b"# Memory" in node_info

        assert result[publish_result_index] == 0
        assert result[2:] == expected

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_can_return_null_on_watch_transaction_failures(
        self, glide_client: TGlideClient, request
    ):
        is_cluster = isinstance(glide_client, GlideClusterClient)
        client2 = await create_client(
            request,
            is_cluster,
        )
        keyslot = get_random_string(3)
        transaction = (
            ClusterBatch(is_atomic=True) if is_cluster else Batch(is_atomic=True)
        )
        transaction.get(keyslot)
        result1 = await glide_client.watch([keyslot])
        assert result1 == OK

        result2 = await client2.set(keyslot, "foo")
        assert result2 == OK

        result3 = await exec_batch(glide_client, transaction)
        assert result3 is None

        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("is_atomic", [True, False])
    async def test_batch_large_values(self, request, cluster_mode, protocol, is_atomic):
        glide_client = await create_client(
            request, cluster_mode=cluster_mode, protocol=protocol, request_timeout=5000
        )
        length = 2**25  # 33mb
        key = "0" * length
        value = "0" * length
        batch = Batch(is_atomic=is_atomic)
        batch.set(key, value)
        batch.get(key)
        result = await exec_batch(glide_client, batch, raise_on_error=True)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_batch(self, glide_client: GlideClient, is_atomic: bool):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK
        version = await get_version(glide_client)
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(10))  # to get the same slot
        key1 = "{{{}}}:{}".format(
            keyslot, get_random_string(10)
        )  # to get the same slot
        value = get_random_string(5)
        transaction = Batch(is_atomic=is_atomic)
        transaction.info()
        transaction.select(1)
        transaction.move(key, 0)
        transaction.set(key, value)
        transaction.get(key)
        transaction.hset("user:1", {"name": "Alice", "age": "30"})
        transaction.hset("user:2", {"name": "Bob", "age": "25"})
        transaction.lpush(key1, ["2", "1"])
        transaction.sort(
            key1,
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.sort_store(
            key1,
            "newSortedKey",
            by_pattern="user:*->age",
            get_patterns=["user:*->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        transaction.select(0)
        transaction.get(key)
        transaction.publish("test_message", "test_channel")
        expected = batch_test(transaction, version, keyslot)
        result = await glide_client.exec(transaction, raise_on_error=True)
        assert isinstance(result, list)
        assert isinstance(result[0], bytes)
        result[0] = result[0].decode()
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:5] == [OK, False, OK, value.encode()]
        assert result[5:13] == [2, 2, 2, [b"Bob", b"Alice"], 2, OK, None, 0]
        assert result[13:] == expected

    async def test_transaction_clear(self):
        transaction = Batch(is_atomic=True)
        transaction.info()
        transaction.select(1)
        transaction.clear()
        assert len(transaction.commands) == 0

    @pytest.mark.skip_if_version_below("6.2.0")
    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_standalone_copy_transaction(self, glide_client: GlideClient):
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(10))  # to get the same slot
        key1 = "{{{}}}:{}".format(
            keyslot, get_random_string(10)
        )  # to get the same slot
        value = get_random_string(5)
        transaction = Batch(is_atomic=True)
        transaction.select(1)
        transaction.set(key, value)
        transaction.copy(key, key1, 1, replace=True)
        transaction.get(key1)
        result = await glide_client.exec(transaction, raise_on_error=True)
        assert result is not None
        assert result[2] is True
        assert result[3] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_chaining_calls(self, glide_client: TGlideClient):
        key = get_random_string(3)

        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.set(key, "value").get(key).delete([key])

        result = await exec_batch(glide_client, transaction)
        assert result == [OK, b"value", 1]

    # The object commands are tested here instead of transaction_test because they have special requirements:
    # - OBJECT FREQ and OBJECT IDLETIME require specific maxmemory policies to be set on the config
    # - we cannot reliably predict the exact response values for OBJECT FREQ, OBJECT IDLETIME, and OBJECT REFCOUNT
    # - OBJECT ENCODING is tested here since all the other OBJECT commands are tested here
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_object_commands(
        self, glide_client: TGlideClient, cluster_mode: bool
    ):
        string_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = await glide_client.config_get([maxmemory_policy_key])
        config_decoded = cast(dict, convert_bytes_to_string_object(config))
        assert config_decoded is not None
        maxmemory_policy = cast(str, config_decoded.get(maxmemory_policy_key))

        try:
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.set(string_key, "foo")
            transaction.object_encoding(string_key)
            transaction.object_refcount(string_key)
            # OBJECT FREQ requires a LFU maxmemory-policy
            transaction.config_set({maxmemory_policy_key: "allkeys-lfu"})
            transaction.object_freq(string_key)
            # OBJECT IDLETIME requires a non-LFU maxmemory-policy
            transaction.config_set({maxmemory_policy_key: "allkeys-random"})
            transaction.object_idletime(string_key)

            response = await exec_batch(glide_client, transaction)
            assert response is not None
            assert response[0] == OK  # transaction.set(string_key, "foo")
            assert response[1] == b"embstr"  # transaction.object_encoding(string_key)
            # transaction.object_refcount(string_key)
            assert cast(int, response[2]) >= 0
            # transaction.config_set({maxmemory_policy_key: "allkeys-lfu"})
            assert response[3] == OK
            assert cast(int, response[4]) >= 0  # transaction.object_freq(string_key)
            # transaction.config_set({maxmemory_policy_key: "allkeys-random"})
            assert response[5] == OK
            # transaction.object_idletime(string_key)
            assert cast(int, response[6]) >= 0
        finally:
            await glide_client.config_set({maxmemory_policy_key: maxmemory_policy})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_xinfo_stream(
        self, glide_client: TGlideClient, cluster_mode: bool, protocol
    ):
        key = get_random_string(10)
        stream_id1_0 = "1-0"
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.xadd(key, [("foo", "bar")], StreamAddOptions(stream_id1_0))
        transaction.xinfo_stream(key)
        transaction.xinfo_stream_full(key)

        response = await exec_batch(glide_client, transaction)
        assert response is not None
        # transaction.xadd(key, [("foo", "bar")], StreamAddOptions(stream_id1_0))
        assert response[0] == stream_id1_0.encode()
        # transaction.xinfo_stream(key)
        info = cast(dict, response[1])
        assert info.get(b"length") == 1
        assert info.get(b"groups") == 0
        assert info.get(b"first-entry") == [stream_id1_0.encode(), [b"foo", b"bar"]]
        assert info.get(b"first-entry") == info.get(b"last-entry")

        # transaction.xinfo_stream_full(key)
        info_full = cast(dict, response[2])
        assert info_full.get(b"length") == 1
        assert info_full.get(b"entries") == [[stream_id1_0.encode(), [b"foo", b"bar"]]]
        assert info_full.get(b"groups") == []

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_lastsave(
        self, glide_client: TGlideClient, cluster_mode: bool
    ):
        yesterday = date.today() - timedelta(1)
        yesterday_unix_time = time.mktime(yesterday.timetuple())
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.lastsave()
        response = await exec_batch(glide_client, transaction)
        assert isinstance(response, list)
        lastsave_time = response[0]
        assert isinstance(lastsave_time, int)
        assert lastsave_time > yesterday_unix_time

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_lolwut_transaction(self, glide_client: GlideClusterClient):
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.lolwut().lolwut(5).lolwut(parameters=[1, 2]).lolwut(6, [42])
        results = await glide_client.exec(transaction, raise_on_error=True)
        assert results is not None

        for element in results:
            assert isinstance(element, bytes)
            assert re.search(rb"(Redis|Valkey) ver\. ?", element)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_dump_restore(
        self, glide_client: TGlideClient, cluster_mode, protocol
    ):
        cluster_mode = isinstance(glide_client, GlideClusterClient)
        keyslot = get_random_string(3)
        key1 = "{{{}}}:{}".format(
            keyslot, get_random_string(10)
        )  # to get the same slot
        key2 = "{{{}}}:{}".format(keyslot, get_random_string(10))

        # Verify Dump
        transaction = (
            ClusterBatch(is_atomic=True) if cluster_mode else Batch(is_atomic=True)
        )
        transaction.set(key1, "value")
        transaction.dump(key1)
        result1 = await exec_batch(glide_client, transaction)
        assert result1 is not None
        assert isinstance(result1, list)
        assert result1[0] == OK
        assert isinstance(result1[1], bytes)

        # Verify Restore - use result1[1] from above
        transaction = (
            ClusterBatch(is_atomic=True) if cluster_mode else Batch(is_atomic=True)
        )
        transaction.restore(key2, 0, result1[1])
        transaction.get(key2)
        result2 = await exec_batch(glide_client, transaction)
        assert result2 is not None
        assert isinstance(result2, list)
        assert result2[0] == OK
        assert result2[1] == b"value"

        # Restore with frequency and idletime both set.
        with pytest.raises(RequestError) as e:
            await glide_client.restore(
                key2, 0, b"", replace=True, idletime=-10, frequency=10
            )
        assert "syntax error: IDLETIME and FREQ cannot be set at the same time." in str(
            e
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_transaction_function_dump_restore(
        self, glide_client: TGlideClient, cluster_mode, protocol
    ):
        if not await check_if_server_version_lt(glide_client, "7.0.0"):
            # Setup (will not verify)
            assert await glide_client.function_flush() == OK
            lib_name = f"mylib_{get_random_string(10)}"
            func_name = f"myfun_{get_random_string(10)}"
            code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.function_load(code, True)

            # Verify function_dump
            transaction.function_dump()
            result1 = await exec_batch(glide_client, transaction)
            assert result1 is not None
            assert isinstance(result1, list)
            assert isinstance(result1[1], bytes)

            # Verify function_restore - use result1[2] from above
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.function_restore(result1[1], FunctionRestorePolicy.REPLACE)
            # For the cluster mode, PRIMARY SlotType is required to avoid the error:
            #  "RequestError: An error was signalled by the server -
            #   ReadOnly: You can't write against a read only replica."
            result2 = await exec_batch(
                glide_client, transaction, SlotIdRoute(SlotType.PRIMARY, 1)
            )

            assert result2 is not None
            assert isinstance(result2, list)
            assert result2[0] == OK

            # Test clean up
            await glide_client.function_flush()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_timeout(self, glide_client: TGlideClient, is_atomic: bool):
        assert await glide_client.custom_command(["FLUSHALL"]) == OK

        batch = (
            Batch(is_atomic=is_atomic)
            if isinstance(glide_client, GlideClient)
            else ClusterBatch(is_atomic=is_atomic)
        )

        batch.custom_command(["DEBUG", "SLEEP", "0.5"])  # Sleep for 0.5 second

        # Expect a timeout on short timeout window
        with pytest.raises(TimeoutError, match="timed out"):
            await exec_batch(glide_client, batch, raise_on_error=True, timeout=100)

        # Wait for sleep to finish
        time.sleep(0.5)

        # Execute the same batch again with a longer timeout, should succeed now
        result = await exec_batch(
            glide_client, batch, raise_on_error=True, timeout=1000
        )
        assert result is not None
        assert len(result) == 1  # Should contain only the result of DEBUG SLEEP

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_deprecated_transaction_classes(self, glide_client: TGlideClient):
        """Tests that the deprecated Transaction and ClusterTransaction classes still work.

        Verifies that they correctly set is_atomic=True and execute commands atomically.
        """
        key = get_random_string(10)
        value = get_random_string(10)

        transaction: Union[Transaction, ClusterTransaction]
        if isinstance(glide_client, GlideClient):
            transaction = Transaction()
        else:
            transaction = ClusterTransaction()

        assert transaction.is_atomic is True

        # Add some simple commands
        transaction.set(key, value)
        transaction.get(key)

        result = await exec_batch(glide_client, transaction, raise_on_error=True)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_multislot(self, glide_client: GlideClusterClient):
        num_keys = 10
        keys = [get_random_string(10) for _ in range(num_keys)]
        values = [get_random_string(5) for _ in range(num_keys)]

        mapping = dict(zip(keys, values))
        assert await glide_client.mset(mapping) == OK

        batch = ClusterBatch(is_atomic=False)
        batch.mget(keys)

        result = await exec_batch(glide_client, batch)

        expected = [v.encode() for v in values]

        assert isinstance(result, list)
        assert result[0] == expected

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_all_nodes(self, glide_client: GlideClusterClient):
        config_key = "maxmemory"
        config_value = "987654321"
        original_config = await glide_client.config_get([config_key])
        original_value = original_config.get(config_key.encode())

        try:
            batch = ClusterBatch(is_atomic=False)
            batch.config_set({config_key: config_value})

            result = await glide_client.exec(batch, raise_on_error=True)

            assert result == ["OK"]

            batch = ClusterBatch(is_atomic=False)
            batch.info()
            result = await exec_batch(glide_client, batch, raise_on_error=True)

            assert isinstance(result, list)
            assert len(result) == 1
            assert isinstance(result[0], dict)

            for info in result[0].values():
                assert isinstance(info, bytes)
            # Check if the config change is reflected in the info output
            assert f"{config_key}:{config_value}" in info.decode()  # type: ignore # noqa
            await glide_client.flushall(FlushMode.ASYNC)
        finally:
            if not isinstance(original_value, (str, bytes)):
                raise ValueError(
                    f"Cannot set config to non-string value: {original_value}"
                )
            await glide_client.config_set({config_key: original_value})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_raise_on_error(
        self, glide_client: GlideClusterClient, is_atomic: bool
    ):
        key = get_random_string(10)
        # Ensure key2 is in the same slot as key for atomic transactions
        key2 = f"{{{key}}}:{get_random_string(10)}"  # type: ignore # noqa

        batch = (
            ClusterBatch(is_atomic=is_atomic)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=is_atomic)
        )

        batch.set(key, "hello")
        batch.lpop(key)
        batch.delete([key])
        batch.rename(key, key2)

        # Test with raise_on_error=False
        result = await exec_batch(glide_client, batch, raise_on_error=False)

        assert result is not None
        assert len(result) == 4

        assert result[0] == "OK"  # set(key, "hello")

        assert isinstance(result[1], RequestError)  # lpop(key)
        assert "WRONGTYPE" in str(result[1])

        assert result[2] == 1  # delete([key])

        assert isinstance(result[3], RequestError)  # rename(key, key2)
        assert "no such key" in str(result[3])

        # Test with raise_on_error=True
        with pytest.raises(RequestError) as e:
            await exec_batch(glide_client, batch, raise_on_error=True)

        assert "WRONGTYPE" in str(e.value)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_cluster_batch_route(
        self,
        glide_client: GlideClusterClient,
        protocol: ProtocolVersion,
        is_atomic: bool,
    ):
        if await check_if_server_version_lt(glide_client, "6.0.0"):
            pytest.skip("CONFIG RESETSTAT requires redis >= 6.0")

        assert await glide_client.config_resetstat() == OK

        key = get_random_string(10)
        value_bytes = b"value"

        batch = ClusterBatch(is_atomic=is_atomic)
        batch.set(key, value_bytes)
        batch.get(key)

        route = SlotKeyRoute(slot_type=SlotType.PRIMARY, slot_key=key)
        options = ClusterBatchOptions(route=route, timeout=2000)
        results = await glide_client.exec(batch, raise_on_error=True, options=options)

        assert results == [OK, value_bytes]

        # Check that no MOVED error occurred by inspecting errorstats on all nodes
        error_stats_dict = await glide_client.info(
            sections=[InfoSection.ERROR_STATS], route=AllNodes()
        )
        assert isinstance(error_stats_dict, dict)

        for node_address, node_info in error_stats_dict.items():
            assert isinstance(node_info, bytes)
            # Ensure the errorstats section indicates no errors reported for this test
            # It should only contain the header line.
            assert (
                node_info.strip() == b"# Errorstats"
            ), f"Node {node_address.decode()} reported errors: {node_info.decode()}"

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_batch_retry_strategy_with_atomic_batch_raises_error(
        self, glide_client: GlideClusterClient
    ):
        """Test that using retry strategies with atomic batches raises RequestError."""
        batch = ClusterBatch(is_atomic=True)
        batch.set("key", "value")

        retry_strategy = BatchRetryStrategy(retry_server_error=True)
        options = ClusterBatchOptions(retry_strategy=retry_strategy)

        with pytest.raises(
            RequestError, match="Retry strategies are not supported for atomic batches"
        ):
            await glide_client.exec(batch, raise_on_error=True, options=options)

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_httl_batch(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        non_existent_field = get_random_string(5)

        # Set up hash with fields
        field_value_map = {field1: "value1", field2: "value2"}
        await glide_client.hset(key, field_value_map)

        # Test HTTL in batch
        if isinstance(glide_client, GlideClusterClient):
            cluster_batch = ClusterBatch(is_atomic=False)
            cluster_batch.httl(key, [field1, field2])
            cluster_batch.httl(key, [non_existent_field])
            cluster_batch.httl("non_existent_key", [field1])
            result = await glide_client.exec(cluster_batch, raise_on_error=False)
        else:
            standalone_batch = Batch(is_atomic=False)
            standalone_batch.httl(key, [field1, field2])
            standalone_batch.httl(key, [non_existent_field])
            standalone_batch.httl("non_existent_key", [field1])
            result = await glide_client.exec(standalone_batch, raise_on_error=False)
        assert result is not None
        assert result == [[-1, -1], [-2], [-2]]

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hpttl_batch(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        non_existent_field = get_random_string(5)

        # Set up hash with fields
        field_value_map = {field1: "value1", field2: "value2"}
        await glide_client.hset(key, field_value_map)

        # Test HPTTL in batch
        if isinstance(glide_client, GlideClusterClient):
            cluster_batch = ClusterBatch(is_atomic=False)
            cluster_batch.hpttl(key, [field1, field2])
            cluster_batch.hpttl(key, [non_existent_field])
            cluster_batch.hpttl("non_existent_key", [field1])
            result = await glide_client.exec(cluster_batch, raise_on_error=False)
        else:
            standalone_batch = Batch(is_atomic=False)
            standalone_batch.hpttl(key, [field1, field2])
            standalone_batch.hpttl(key, [non_existent_field])
            standalone_batch.hpttl("non_existent_key", [field1])
            result = await glide_client.exec(standalone_batch, raise_on_error=False)
        assert result is not None
        assert result == [[-1, -1], [-2], [-2]]

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hexpiretime_batch(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        non_existent_field = get_random_string(5)

        # Set up hash with fields - some with expiration, some without
        field_value_map = {field1: "value1", field2: "value2"}
        await glide_client.hset(key, field_value_map)

        # Set expiration on field1 using HSETEX
        import time

        future_timestamp = int(time.time()) + 10
        await glide_client.hsetex(
            key,
            {field3: "value3_with_expiry"},
            expiry=ExpirySet(ExpiryType.UNIX_SEC, future_timestamp),
        )

        # Test HEXPIRETIME in batch
        if isinstance(glide_client, GlideClusterClient):
            cluster_batch = ClusterBatch(is_atomic=False)
            cluster_batch.hexpiretime(
                key, [field1, field2]
            )  # fields without expiration
            cluster_batch.hexpiretime(key, [field3])  # field with expiration
            cluster_batch.hexpiretime(key, [non_existent_field])  # non-existent field
            cluster_batch.hexpiretime("non_existent_key", [field1])  # non-existent key
            cluster_batch.hexpiretime(key, [])  # empty fields list
            result = await glide_client.exec(cluster_batch, raise_on_error=False)
        else:
            standalone_batch = Batch(is_atomic=False)
            standalone_batch.hexpiretime(
                key, [field1, field2]
            )  # fields without expiration
            standalone_batch.hexpiretime(key, [field3])  # field with expiration
            standalone_batch.hexpiretime(
                key, [non_existent_field]
            )  # non-existent field
            standalone_batch.hexpiretime(
                "non_existent_key", [field1]
            )  # non-existent key
            standalone_batch.hexpiretime(key, [])  # empty fields list
            result = await glide_client.exec(standalone_batch, raise_on_error=False)

        assert result is not None
        assert result[0] == [-1, -1]  # fields without expiration
        assert result[1] == [future_timestamp]  # field with expiration
        assert result[2] == [-2]  # non-existent field
        assert result[3] == [-2]  # non-existent key
        assert isinstance(result[4], RequestError)  # empty fields list should error

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hpexpiretime_batch(self, glide_client: TGlideClient):
        key = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        non_existent_field = get_random_string(5)
        non_existent_key = get_random_string(10)

        # Set up hash with fields
        field_value_map = {field1: "value1", field2: "value2"}
        assert await glide_client.hset(key, field_value_map) == 2

        # Set expiration on field1 using HSETEX
        import time

        future_timestamp_ms = int(time.time() * 1000) + 10000
        hsetex_result = await glide_client.hsetex(
            key,
            {field1: "value1_with_expiry"},
            expiry=ExpirySet(ExpiryType.UNIX_MILLSEC, future_timestamp_ms),
        )
        assert hsetex_result == 1

        # Test HPEXPIRETIME in batch
        if isinstance(glide_client, GlideClusterClient):
            cluster_batch = ClusterBatch(is_atomic=False)
            cluster_batch.hpexpiretime(key, [field2])  # field without expiration
            cluster_batch.hpexpiretime(key, [field1])  # field with expiration
            cluster_batch.hpexpiretime(key, [non_existent_field])  # non-existent field
            cluster_batch.hpexpiretime(non_existent_key, [field1])  # non-existent key

            result = await glide_client.exec(cluster_batch, raise_on_error=True)
        else:
            batch = Batch(is_atomic=False)
            batch.hpexpiretime(key, [field2])  # field without expiration
            batch.hpexpiretime(key, [field1])  # field with expiration
            batch.hpexpiretime(key, [non_existent_field])  # non-existent field
            batch.hpexpiretime(non_existent_key, [field1])  # non-existent key

            result = await glide_client.exec(batch, raise_on_error=True)

        assert result is not None
        assert result[0] == [-1]  # field without expiration
        assert result[1] == [future_timestamp_ms]  # field with expiration
        assert result[2] == [-2]  # non-existent field
        assert result[3] == [-2]  # non-existent key

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hsetex_batch(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)

        # Test HSETEX in batch with different options
        if isinstance(glide_client, GlideClusterClient):
            cluster_batch = ClusterBatch(is_atomic=False)
            # Basic HSETEX with expiration
            cluster_batch.hsetex(
                key1,
                {field1: "value1", field2: "value2"},
                expiry=ExpirySet(ExpiryType.SEC, 10),
            )
            # HSETEX with field conditional change
            cluster_batch.hsetex(
                key1,
                {field3: "value3"},
                field_conditional_change=HashFieldConditionalChange.ONLY_IF_NONE_EXIST,
                expiry=ExpirySet(ExpiryType.MILLSEC, 5000),
            )
            # HSETEX on new key
            cluster_batch.hsetex(
                key2, {field1: "new_value"}, expiry=ExpirySet(ExpiryType.SEC, 15)
            )
            # Verify with HTTL
            cluster_batch.httl(key1, [field1, field2, field3])
            cluster_batch.httl(key2, [field1])
            result = await glide_client.exec(cluster_batch, raise_on_error=False)
        else:
            standalone_batch = Batch(is_atomic=False)
            # Basic HSETEX with expiration
            standalone_batch.hsetex(
                key1,
                {field1: "value1", field2: "value2"},
                expiry=ExpirySet(ExpiryType.SEC, 10),
            )
            # HSETEX with field conditional change
            standalone_batch.hsetex(
                key1,
                {field3: "value3"},
                field_conditional_change=HashFieldConditionalChange.ONLY_IF_NONE_EXIST,
                expiry=ExpirySet(ExpiryType.MILLSEC, 5000),
            )
            # HSETEX on new key
            standalone_batch.hsetex(
                key2, {field1: "new_value"}, expiry=ExpirySet(ExpiryType.SEC, 15)
            )
            # Verify with HTTL
            standalone_batch.httl(key1, [field1, field2, field3])
            standalone_batch.httl(key2, [field1])
            result = await glide_client.exec(standalone_batch, raise_on_error=False)
        assert result is not None

        # Check results
        assert result[0] == 1  # First HSETEX should succeed
        assert result[1] == 1  # Second HSETEX should succeed (field doesn't exist)
        assert result[2] == 1  # Third HSETEX should succeed

        # Check TTL results - all should have positive TTL values
        ttl_key1 = result[3]
        ttl_key2 = result[4]
        assert isinstance(ttl_key1, list) and isinstance(ttl_key2, list)
        assert all(
            isinstance(ttl, int) and 0 < ttl <= 10 for ttl in ttl_key1[:2]
        )  # field1, field2 have ~10s TTL
        assert (
            isinstance(ttl_key1[2], int) and 0 < ttl_key1[2] <= 5
        )  # field3 has ~5s TTL
        assert (
            isinstance(ttl_key2[0], int) and 0 < ttl_key2[0] <= 15
        )  # key2 field1 has ~15s TTL

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hgetex_batch(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)

        # Set up initial data
        await glide_client.hset(
            key1, {field1: "value1", field2: "value2", field3: "value3"}
        )
        await glide_client.hset(key2, {field1: "other_value"})

        # Test HGETEX in batch with different options
        if isinstance(glide_client, GlideClusterClient):
            cluster_batch = ClusterBatch(is_atomic=False)
            # Basic HGETEX without expiry
            cluster_batch.hgetex(key1, [field1, field2])
            # HGETEX with EX expiry option
            cluster_batch.hgetex(
                key1, [field1], expiry=ExpiryGetEx(ExpiryTypeGetEx.SEC, 10)
            )
            # HGETEX with PX expiry option
            cluster_batch.hgetex(
                key1, [field2], expiry=ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 8000)
            )
            # HGETEX with PERSIST option
            cluster_batch.hgetex(
                key1, [field3], expiry=ExpiryGetEx(ExpiryTypeGetEx.PERSIST, None)
            )
            # HGETEX on non-existent key
            cluster_batch.hgetex("non_existent_key", [field1])
            # HGETEX with mixed existent/non-existent fields
            cluster_batch.hgetex(key1, [field1, "non_existent_field"])
            # Verify expiration changes with HTTL
            cluster_batch.httl(key1, [field1, field2, field3])
            result = await glide_client.exec(cluster_batch, raise_on_error=False)
        else:
            standalone_batch = Batch(is_atomic=False)
            # Basic HGETEX without expiry
            standalone_batch.hgetex(key1, [field1, field2])
            # HGETEX with EX expiry option
            standalone_batch.hgetex(
                key1, [field1], expiry=ExpiryGetEx(ExpiryTypeGetEx.SEC, 10)
            )
            # HGETEX with PX expiry option
            standalone_batch.hgetex(
                key1, [field2], expiry=ExpiryGetEx(ExpiryTypeGetEx.MILLSEC, 8000)
            )
            # HGETEX with PERSIST option
            standalone_batch.hgetex(
                key1, [field3], expiry=ExpiryGetEx(ExpiryTypeGetEx.PERSIST, None)
            )
            # HGETEX on non-existent key
            standalone_batch.hgetex("non_existent_key", [field1])
            # HGETEX with mixed existent/non-existent fields
            standalone_batch.hgetex(key1, [field1, "non_existent_field"])
            # Verify expiration changes with HTTL
            standalone_batch.httl(key1, [field1, field2, field3])
            result = await glide_client.exec(standalone_batch, raise_on_error=False)
        assert result is not None

        # Check results
        assert result[0] == [b"value1", b"value2"]  # Basic HGETEX
        assert result[1] == [b"value1"]  # HGETEX with EX expiry
        assert result[2] == [b"value2"]  # HGETEX with PX expiry
        assert result[3] == [b"value3"]  # HGETEX with PERSIST
        assert result[4] == [None]  # HGETEX on non-existent key
        assert result[5] == [b"value1", None]  # Mixed existent/non-existent fields

        # Check TTL results
        ttl_results = result[6]
        assert isinstance(ttl_results, list)
        assert (
            isinstance(ttl_results[0], int) and 0 < ttl_results[0] <= 10
        )  # field1 should have ~10s TTL
        assert (
            isinstance(ttl_results[1], int) and 0 < ttl_results[1] <= 8
        )  # field2 should have ~8s TTL
        assert ttl_results[2] == -1  # field3 should have no expiration (PERSIST)

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hexpire_batch(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        non_existent_field = get_random_string(5)
        non_existent_key = get_random_string(10)

        # Set up test data
        await glide_client.hset(
            key1, {field1: "value1", field2: "value2", field3: "value3"}
        )
        await glide_client.hset(key2, {field1: "value1", field2: "value2"})

        # Create batch with various HEXPIRE operations
        batch = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch.hexpire(key1, 10, [field1, field2])  # Basic HEXPIRE
        batch.hexpire(
            key1, 20, [field3], option=ExpireOptions.HasNoExpiry
        )  # HEXPIRE with NX
        batch.hexpire(
            key1, 30, [field1], option=ExpireOptions.HasExistingExpiry
        )  # HEXPIRE with XX
        batch.hexpire(
            key1, 5, [field2], option=ExpireOptions.NewExpiryLessThanCurrent
        )  # HEXPIRE with LT
        batch.hexpire(
            key1, 40, [field1], option=ExpireOptions.NewExpiryGreaterThanCurrent
        )  # HEXPIRE with GT
        batch.hexpire(non_existent_key, 15, [field1])  # HEXPIRE on non-existent key
        batch.hexpire(
            key1, 25, [field1, non_existent_field]
        )  # Mixed existent/non-existent fields
        batch.hexpire(key2, 0, [field1])  # Immediate deletion with 0 seconds
        batch.httl(key1, [field1, field2, field3])  # Check TTL values
        batch.hget(key2, field1)  # Check if field1 was deleted

        # Execute batch
        result = await exec_batch(glide_client, batch, raise_on_error=False)
        assert result is not None

        # Check results
        assert result[0] == [1, 1]  # Basic HEXPIRE on field1, field2
        assert result[1] == [1]  # HEXPIRE with NX on field3 (no prior expiry)
        assert result[2] == [1]  # HEXPIRE with XX on field1 (has expiry)
        assert result[3] == [1]  # HEXPIRE with LT on field2 (5 < 10)
        assert result[4] == [1]  # HEXPIRE with GT on field1 (40 > 30)
        assert result[5] == [-2]  # HEXPIRE on non-existent key
        assert result[6] == [
            1,
            -2,
        ]  # Mixed fields: field1 exists, non_existent_field doesn't
        assert result[7] == [2]  # Immediate deletion (0 seconds)

        # Check TTL results
        ttl_results = result[8]
        assert isinstance(ttl_results, list)
        assert (
            isinstance(ttl_results[0], int) and 0 < ttl_results[0] <= 40
        )  # field1 should have ~40s TTL
        assert (
            isinstance(ttl_results[1], int) and 0 < ttl_results[1] <= 5
        )  # field2 should have ~5s TTL
        assert (
            isinstance(ttl_results[2], int) and 0 < ttl_results[2] <= 20
        )  # field3 should have ~20s TTL

        # Check that field1 in key2 was deleted
        assert result[9] is None  # field1 should be deleted

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hpexpire_batch(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        non_existent_field = get_random_string(5)

        # Set up hash with fields for both keys
        field_value_map = {field1: "value1", field2: "value2", field3: "value3"}
        await glide_client.hset(key1, field_value_map)
        await glide_client.hset(key2, field_value_map)

        # Create batch with HPEXPIRE operations
        batch = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch.hpexpire(key1, 10000, [field1, field2])  # Set 10000ms expiration
        batch.hpexpire(key1, 15000, [non_existent_field])  # Non-existent field
        batch.hpexpire(key2, 5000, [field1])  # Set 5000ms expiration
        batch.hpexpire(key2, 20000, [field2, field3])  # Set 20000ms expiration
        batch.hpexpire(key2, 0, [field1])  # Delete field1 immediately
        batch.httl(key1, [field1, field2])  # Check TTL for key1 fields
        batch.httl(key2, [field1, field2, field3])  # Check TTL for key2 fields
        batch.hget(key2, field1)  # Should be None (deleted)

        # Execute batch
        result = await exec_batch(glide_client, batch, raise_on_error=False)
        assert result is not None

        # Verify results
        assert result[0] == [1, 1]  # key1 field1 and field2 expiration set
        assert result[1] == [-2]  # non_existent_field doesn't exist
        assert result[2] == [1]  # key2 field1 expiration set
        assert result[3] == [1, 1]  # key2 field2 and field3 expiration set
        assert result[4] == [2]  # key2 field1 deleted immediately

        # Check TTL results
        ttl_key1 = result[5]
        ttl_key2 = result[6]
        assert isinstance(ttl_key1, list) and isinstance(ttl_key2, list)

        # key1 fields should have TTL
        assert (
            isinstance(ttl_key1[0], int) and 0 < ttl_key1[0] <= 10
        )  # field1 has ~10s TTL
        assert (
            isinstance(ttl_key1[1], int) and 0 < ttl_key1[1] <= 10
        )  # field2 has ~10s TTL

        # key2 fields: field1 deleted, field2 and field3 have TTL
        assert ttl_key2[0] == -2  # field1 was deleted
        assert (
            isinstance(ttl_key2[1], int) and 0 < ttl_key2[1] <= 20
        )  # field2 has ~20s TTL
        assert (
            isinstance(ttl_key2[2], int) and 0 < ttl_key2[2] <= 20
        )  # field3 has ~20s TTL

        # Check that field1 in key2 was deleted
        assert result[7] is None  # field1 should be deleted

        # Test HPEXPIRE with conditional options in batch
        batch2 = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch2.hpexpire(
            key1, 25000, [field1], option=ExpireOptions.HasExistingExpiry
        )  # XX option
        batch2.hpexpire(
            key1, 30000, [field3], option=ExpireOptions.HasNoExpiry
        )  # NX option
        batch2.httl(key1, [field1, field3])  # Check TTL

        result2 = await exec_batch(glide_client, batch2, raise_on_error=False)
        assert result2 is not None

        # Verify conditional results
        assert result2[0] == [1]  # field1 has existing expiry, XX should succeed
        assert result2[1] == [1]  # field3 has no expiry, NX should succeed

        # Check TTL results
        ttl_results = result2[2]
        assert isinstance(ttl_results, list)
        assert (
            isinstance(ttl_results[0], int) and 0 < ttl_results[0] <= 25
        )  # field1 should have ~25s TTL
        assert (
            isinstance(ttl_results[1], int) and 0 < ttl_results[1] <= 30
        )  # field3 should have ~30s TTL

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hexpireat_batch(self, glide_client: TGlideClient):
        import time

        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        non_existent_field = get_random_string(5)
        non_existent_key = get_random_string(10)

        # Set up test data
        await glide_client.hset(
            key1, {field1: "value1", field2: "value2", field3: "value3"}
        )
        await glide_client.hset(key2, {field1: "value1", field2: "value2"})

        # Create timestamps for testing
        future_timestamp1 = int(time.time()) + 30
        future_timestamp2 = int(time.time()) + 45
        past_timestamp = int(time.time()) - 60

        # Create batch with various HEXPIREAT operations
        batch = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch.hexpireat(key1, future_timestamp1, [field1, field2])  # Basic HEXPIREAT
        batch.hexpireat(
            key1, future_timestamp2, [field3], option=ExpireOptions.HasNoExpiry
        )  # HEXPIREAT with NX
        batch.hexpireat(
            key1, future_timestamp2, [field1], option=ExpireOptions.HasExistingExpiry
        )  # HEXPIREAT with XX
        batch.hexpireat(
            key1,
            future_timestamp1,
            [field2],
            option=ExpireOptions.NewExpiryLessThanCurrent,
        )  # HEXPIREAT with LT (should fail since future_timestamp1 < future_timestamp2)
        batch.hexpireat(
            key1,
            future_timestamp2 + 20,
            [field1],
            option=ExpireOptions.NewExpiryGreaterThanCurrent,
        )  # HEXPIREAT with GT
        batch.hexpireat(
            non_existent_key, future_timestamp1, [field1]
        )  # HEXPIREAT on non-existent key
        batch.hexpireat(
            key1, future_timestamp1, [field1, non_existent_field]
        )  # Mixed existent/non-existent fields
        batch.hexpireat(
            key2, past_timestamp, [field1]
        )  # Immediate deletion with past timestamp
        batch.httl(key1, [field1, field2, field3])  # Check TTL values
        batch.hget(key2, field1)  # Check if field1 was deleted

        # Execute batch
        result = await exec_batch(glide_client, batch, raise_on_error=False)
        assert result is not None

        # Check results
        assert result[0] == [1, 1]  # Basic HEXPIREAT on field1, field2
        assert result[1] == [1]  # HEXPIREAT with NX on field3 (no prior expiry)
        assert result[2] == [1]  # HEXPIREAT with XX on field1 (has expiry)
        assert result[3] == [0]  # HEXPIREAT with LT on field2 (should fail)
        assert result[4] == [
            1
        ]  # HEXPIREAT with GT on field1 (later timestamp > current)
        assert result[5] == [-2]  # HEXPIREAT on non-existent key
        assert result[6] == [
            1,
            -2,
        ]  # Mixed fields: field1 exists, non_existent_field doesn't
        assert result[7] == [2]  # Immediate deletion (past timestamp)

        # Check TTL results
        ttl_results = result[8]
        assert isinstance(ttl_results, list)
        assert isinstance(ttl_results[0], int) and (
            0 < ttl_results[0] <= 65
        )  # field1 should have TTL (future_timestamp2 + 20)
        assert (
            isinstance(ttl_results[1], int) and 0 < ttl_results[1] <= 30
        )  # field2 should have TTL (future_timestamp1)
        assert (
            isinstance(ttl_results[2], int) and 0 < ttl_results[2] <= 45
        )  # field3 should have TTL (future_timestamp2)

        # Check that field1 in key2 was deleted
        assert result[9] is None  # field1 should be deleted

        # Test HEXPIREAT with more conditional options in batch
        batch2 = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        base_timestamp = int(time.time()) + 60
        later_timestamp = base_timestamp + 30
        earlier_timestamp = base_timestamp - 10

        # Set base expiration first
        batch2.hexpireat(key1, base_timestamp, [field2])
        batch2.hexpireat(
            key1,
            later_timestamp,
            [field2],
            option=ExpireOptions.NewExpiryGreaterThanCurrent,
        )  # GT option
        batch2.hexpireat(
            key1,
            earlier_timestamp,
            [field2],
            option=ExpireOptions.NewExpiryLessThanCurrent,
        )  # LT option
        batch2.httl(key1, [field2])  # Check final TTL

        result2 = await exec_batch(glide_client, batch2, raise_on_error=False)
        assert result2 is not None

        # Verify conditional results
        assert result2[0] == [1]  # Base expiration set
        assert result2[1] == [1]  # GT should succeed (later_timestamp > base_timestamp)
        assert result2[2] == [
            1
        ]  # LT should succeed (earlier_timestamp < later_timestamp)

        # Check final TTL result
        ttl_result = result2[3]
        assert isinstance(ttl_result, list)
        assert isinstance(ttl_result[0], int) and (
            0 < ttl_result[0] <= (earlier_timestamp - int(time.time()))
        )  # Should have earlier_timestamp TTL

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hpexpireat_batch(self, glide_client: TGlideClient):
        import time

        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        non_existent_field = get_random_string(5)
        non_existent_key = get_random_string(10)

        # Set up test data
        await glide_client.hset(
            key1, {field1: "value1", field2: "value2", field3: "value3"}
        )
        await glide_client.hset(key2, {field1: "value1", field2: "value2"})

        # Create timestamps for testing (in milliseconds)
        future_timestamp_ms1 = int(time.time() * 1000) + 30000  # 30 seconds from now
        future_timestamp_ms2 = int(time.time() * 1000) + 45000  # 45 seconds from now
        past_timestamp_ms = int(time.time() * 1000) - 60000  # 60 seconds ago

        # Create batch with various HPEXPIREAT operations
        batch = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch.hpexpireat(
            key1, future_timestamp_ms1, [field1, field2]
        )  # Basic HPEXPIREAT
        batch.hpexpireat(
            key1, future_timestamp_ms2, [field3], option=ExpireOptions.HasNoExpiry
        )  # HPEXPIREAT with NX
        batch.hpexpireat(
            key1, future_timestamp_ms2, [field1], option=ExpireOptions.HasExistingExpiry
        )  # HPEXPIREAT with XX
        batch.hpexpireat(
            key1,
            future_timestamp_ms1,
            [field2],
            option=ExpireOptions.NewExpiryLessThanCurrent,
        )  # HPEXPIREAT with LT (should fail since future_timestamp_ms1 < future_timestamp_ms2)
        batch.hpexpireat(
            key1,
            future_timestamp_ms2 + 20000,
            [field1],
            option=ExpireOptions.NewExpiryGreaterThanCurrent,
        )  # HPEXPIREAT with GT
        batch.hpexpireat(
            non_existent_key, future_timestamp_ms1, [field1]
        )  # HPEXPIREAT on non-existent key
        batch.hpexpireat(
            key1, future_timestamp_ms1, [field1, non_existent_field]
        )  # Mixed existent/non-existent fields
        batch.hpexpireat(
            key2, past_timestamp_ms, [field1]
        )  # Immediate deletion with past timestamp
        batch.httl(key1, [field1, field2, field3])  # Check TTL values
        batch.hget(key2, field1)  # Check if field1 was deleted

        # Execute batch
        result = await exec_batch(glide_client, batch, raise_on_error=False)
        assert result is not None

        # Check results
        assert result[0] == [1, 1]  # Basic HPEXPIREAT on field1, field2
        assert result[1] == [1]  # HPEXPIREAT with NX on field3 (no prior expiry)
        assert result[2] == [1]  # HPEXPIREAT with XX on field1 (has expiry)
        assert result[3] == [0]  # HPEXPIREAT with LT on field2 (should fail)
        assert result[4] == [
            1
        ]  # HPEXPIREAT with GT on field1 (later timestamp > current)
        assert result[5] == [-2]  # HPEXPIREAT on non-existent key
        assert result[6] == [
            1,
            -2,
        ]  # Mixed: field1 updated, non_existent_field doesn't exist
        assert result[7] == [2]  # field1 in key2 deleted immediately (past timestamp)

        # Check TTL values (should be positive for fields with expiration)
        ttl_result = result[8]
        assert isinstance(ttl_result, list)
        assert isinstance(ttl_result[0], int) and (
            0 < ttl_result[0] <= 65
        )  # field1 should have TTL (updated by GT operation)
        assert (
            isinstance(ttl_result[1], int) and 0 < ttl_result[1] <= 30
        )  # field2 should have TTL from initial HPEXPIREAT
        assert (
            isinstance(ttl_result[2], int) and 0 < ttl_result[2] <= 45
        )  # field3 should have TTL from NX operation

        assert result[9] is None  # field1 in key2 should be deleted

        # Test conditional operations with timestamps in milliseconds
        batch2 = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        base_timestamp_ms = int(time.time() * 1000) + 60000  # 60 seconds from now
        later_timestamp_ms = base_timestamp_ms + 20000  # 20 seconds later
        earlier_timestamp_ms = later_timestamp_ms - 10000  # 10 seconds earlier

        # Set base expiration first
        batch2.hpexpireat(key1, base_timestamp_ms, [field2])
        batch2.hpexpireat(
            key1,
            later_timestamp_ms,
            [field2],
            option=ExpireOptions.NewExpiryGreaterThanCurrent,
        )  # GT option
        batch2.hpexpireat(
            key1,
            earlier_timestamp_ms,
            [field2],
            option=ExpireOptions.NewExpiryLessThanCurrent,
        )  # LT option
        batch2.httl(key1, [field2])  # Check final TTL

        result2 = await exec_batch(glide_client, batch2, raise_on_error=False)
        assert result2 is not None

        # Verify conditional results
        assert result2[0] == [1]  # Base expiration set
        assert result2[1] == [
            1
        ]  # GT should succeed (later_timestamp_ms > base_timestamp_ms)
        assert result2[2] == [
            1
        ]  # LT should succeed (earlier_timestamp_ms < later_timestamp_ms)

        # Check final TTL result (convert milliseconds to seconds for comparison)
        ttl_result = result2[3]
        assert isinstance(ttl_result, list)
        expected_ttl_seconds = (earlier_timestamp_ms - int(time.time() * 1000)) // 1000
        # Allow for timing variations - TTL should be within a reasonable range
        assert isinstance(ttl_result[0], int) and (
            0 < ttl_result[0] <= expected_ttl_seconds + 2
        )  # Should have earlier_timestamp_ms TTL (with 2 second buffer for timing)

    @pytest.mark.skip_if_version_below("9.0.0")
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_hpersist_batch(self, glide_client: TGlideClient):
        key1 = get_random_string(10)
        key2 = get_random_string(10)
        field1 = get_random_string(5)
        field2 = get_random_string(5)
        field3 = get_random_string(5)
        non_existent_field = get_random_string(5)

        # Set up hash with fields and expiration
        field_value_map1 = {field1: "value1", field2: "value2", field3: "value3"}
        field_value_map2 = {field1: "value1", field2: "value2"}

        await glide_client.hset(key1, field_value_map1)
        await glide_client.hset(key2, field_value_map2)

        # Set expiration on some fields
        await glide_client.hsetex(
            key1,
            {field1: "value1_updated", field2: "value2_updated"},
            expiry=ExpirySet(ExpiryType.SEC, 10),
        )
        await glide_client.hexpire(key2, 15, [field1, field2])

        # Create batch with HPERSIST operations
        batch = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch.hpersist(
            key1, [field1, field2, field3]
        )  # field1,field2 have expiry, field3 doesn't
        batch.hpersist(key1, [non_existent_field])  # non-existent field
        batch.hpersist(key2, [field1, field2])  # both have expiry
        batch.httl(key1, [field1, field2, field3])  # verify persistence
        batch.httl(key2, [field1, field2])  # verify persistence

        # Execute batch
        result = await exec_batch(glide_client, batch, raise_on_error=True)
        assert result is not None

        # Verify results
        assert result[0] == [
            1,
            1,
            -1,
        ]  # field1,field2 made persistent, field3 already persistent
        assert result[1] == [-2]  # non-existent field
        assert result[2] == [1, 1]  # both fields made persistent
        assert result[3] == [-1, -1, -1]  # all fields now persistent
        assert result[4] == [-1, -1]  # both fields now persistent
