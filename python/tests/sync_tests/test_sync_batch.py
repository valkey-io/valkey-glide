# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


import re
import time
from datetime import date, timedelta
from typing import List, Optional, Union, cast

import pytest
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
    FlushMode,
    FunctionRestorePolicy,
    InfoSection,
)
from glide_shared.commands.stream import StreamAddOptions
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK, TResult, TSingleNodeRoute
from glide_shared.routes import AllNodes, SlotIdRoute, SlotKeyRoute, SlotType
from glide_sync.glide_client import GlideClient, GlideClusterClient, TGlideClient

from tests.sync_tests.conftest import create_sync_client
from tests.utils.utils import (
    batch_test,
    convert_bytes_to_string_object,
    generate_lua_lib_code,
    get_random_string,
    sync_check_if_server_version_lt,
    sync_get_version,
)


def exec_batch(
    glide_sync_client: TGlideClient,
    batch: BaseBatch,
    route: Optional[TSingleNodeRoute] = None,
    timeout: Optional[int] = None,
    raise_on_error: bool = False,
) -> Optional[List[TResult]]:
    if isinstance(glide_sync_client, GlideClient):
        batch_options = BatchOptions(timeout=timeout)
        return cast(GlideClient, glide_sync_client).exec(
            cast(Batch, batch), raise_on_error, batch_options
        )
    else:
        cluster_options = ClusterBatchOptions(
            route=route,
            timeout=timeout,
        )
        return cast(GlideClusterClient, glide_sync_client).exec(
            cast(ClusterBatch, batch),
            raise_on_error,
            cluster_options,
        )


@pytest.mark.anyio
class TestSyncBatch:
    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_with_different_slots(
        self, glide_sync_client: GlideClusterClient
    ):
        transaction = ClusterBatch(is_atomic=True)
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(RequestError, match="CrossSlot"):
            glide_sync_client.exec(transaction, raise_on_error=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_custom_command(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = exec_batch(glide_sync_client, transaction)
        assert result == [1, b"bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_custom_unsupported_command(
        self, glide_sync_client: TGlideClient
    ):
        key = get_random_string(10)
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.custom_command(["WATCH", key])
        with pytest.raises(RequestError) as e:
            exec_batch(glide_sync_client, transaction)

        assert "not allowed" in str(e)  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_discard_command(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        glide_sync_client.set(key, "1")
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(RequestError) as e:
            exec_batch(glide_sync_client, transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = glide_sync_client.get(key)
        assert value == b"1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_exec_abort(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        transaction = BaseBatch(is_atomic=True)
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(RequestError) as e:
            exec_batch(glide_sync_client, transaction)
        assert "wrong number of arguments" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_batch(self, glide_sync_client: GlideClusterClient, is_atomic):
        assert glide_sync_client.custom_command(["FLUSHALL"]) == OK
        version = sync_get_version(glide_sync_client)
        keyslot = get_random_string(3) if is_atomic else None
        batch = ClusterBatch(is_atomic=is_atomic)
        batch.info()
        publish_result_index = 1
        if sync_check_if_server_version_lt(glide_sync_client, "7.0.0"):
            batch.publish("test_message", keyslot or get_random_string(3), False)
        else:
            batch.publish("test_message", keyslot or get_random_string(3), True)

        expected = batch_test(batch, version, keyslot)

        if not sync_check_if_server_version_lt(glide_sync_client, "7.0.0"):
            batch.pubsub_shardchannels()
            expected.append(cast(TResult, []))
            batch.pubsub_shardnumsub()
            expected.append(cast(TResult, {}))

        result = glide_sync_client.exec(batch, raise_on_error=True)
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
    def test_sync_can_return_null_on_watch_transaction_failures(
        self, glide_sync_client: TGlideClient, request
    ):
        is_cluster = isinstance(glide_sync_client, GlideClusterClient)
        client2 = create_sync_client(
            request,
            is_cluster,
        )
        keyslot = get_random_string(3)
        transaction = (
            ClusterBatch(is_atomic=True) if is_cluster else Batch(is_atomic=True)
        )
        transaction.get(keyslot)
        result1 = glide_sync_client.watch([keyslot])
        assert result1 == OK

        result2 = client2.set(keyslot, "foo")
        assert result2 == OK

        result3 = exec_batch(glide_sync_client, transaction)
        assert result3 is None

        client2.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    @pytest.mark.parametrize("is_atomic", [True, False])
    def test_sync_batch_large_values(self, request, cluster_mode, protocol, is_atomic):
        glide_sync_client = create_sync_client(
            request, cluster_mode=cluster_mode, protocol=protocol, request_timeout=5000
        )
        length = 2**25  # 33mb
        key = "0" * length
        value = "0" * length
        batch = Batch(is_atomic=is_atomic)
        batch.set(key, value)
        batch.get(key)
        result = exec_batch(glide_sync_client, batch, raise_on_error=True)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_standalone_batch(
        self, glide_sync_client: GlideClient, is_atomic: bool
    ):
        assert glide_sync_client.custom_command(["FLUSHALL"]) == OK
        version = sync_get_version(glide_sync_client)
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
        result = glide_sync_client.exec(transaction, raise_on_error=True)
        assert isinstance(result, list)
        assert isinstance(result[0], bytes)
        result[0] = result[0].decode()
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:5] == [OK, False, OK, value.encode()]
        assert result[5:13] == [2, 2, 2, [b"Bob", b"Alice"], 2, OK, None, 0]
        assert result[13:] == expected

    def test_sync_transaction_clear(self):
        transaction = Batch(is_atomic=True)
        transaction.info()
        transaction.select(1)
        transaction.clear()
        assert len(transaction.commands) == 0

    @pytest.mark.skip_if_version_below("6.2.0")
    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_standalone_copy_transaction(self, glide_sync_client: GlideClient):
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
        result = glide_sync_client.exec(transaction, raise_on_error=True)
        assert result is not None
        assert result[2] is True
        assert result[3] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_chaining_calls(self, glide_sync_client: TGlideClient):
        key = get_random_string(3)

        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.set(key, "value").get(key).delete([key])

        result = exec_batch(glide_sync_client, transaction)
        assert result == [OK, b"value", 1]

    # The object commands are tested here instead of transaction_test because they have special requirements:
    # - OBJECT FREQ and OBJECT IDLETIME require specific maxmemory policies to be set on the config
    # - we cannot reliably predict the exact response values for OBJECT FREQ, OBJECT IDLETIME, and OBJECT REFCOUNT
    # - OBJECT ENCODING is tested here since all the other OBJECT commands are tested here
    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_object_commands(
        self, glide_sync_client: TGlideClient, cluster_mode: bool
    ):
        string_key = get_random_string(10)
        maxmemory_policy_key = "maxmemory-policy"
        config = glide_sync_client.config_get([maxmemory_policy_key])
        config_decoded = cast(dict, convert_bytes_to_string_object(config))
        assert config_decoded is not None
        maxmemory_policy = cast(str, config_decoded.get(maxmemory_policy_key))

        try:
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_sync_client, GlideClient)
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

            response = exec_batch(glide_sync_client, transaction)
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
            glide_sync_client.config_set({maxmemory_policy_key: maxmemory_policy})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_xinfo_stream(
        self, glide_sync_client: TGlideClient, cluster_mode: bool, protocol
    ):
        key = get_random_string(10)
        stream_id1_0 = "1-0"
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.xadd(key, [("foo", "bar")], StreamAddOptions(stream_id1_0))
        transaction.xinfo_stream(key)
        transaction.xinfo_stream_full(key)

        response = exec_batch(glide_sync_client, transaction)
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
    def test_sync_transaction_lastsave(
        self, glide_sync_client: TGlideClient, cluster_mode: bool
    ):
        yesterday = date.today() - timedelta(1)
        yesterday_unix_time = time.mktime(yesterday.timetuple())
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.lastsave()
        response = exec_batch(glide_sync_client, transaction)
        assert isinstance(response, list)
        lastsave_time = response[0]
        assert isinstance(lastsave_time, int)
        assert lastsave_time > yesterday_unix_time

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_lolwut_transaction(self, glide_sync_client: GlideClusterClient):
        transaction = (
            Batch(is_atomic=True)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=True)
        )
        transaction.lolwut().lolwut(5).lolwut(parameters=[1, 2]).lolwut(6, [42])
        results = glide_sync_client.exec(transaction, raise_on_error=True)
        assert results is not None

        for element in results:
            assert isinstance(element, bytes)
            assert re.search(rb"(Redis|Valkey) ver\. ?", element)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_dump_restore(
        self, glide_sync_client: TGlideClient, cluster_mode, protocol
    ):
        cluster_mode = isinstance(glide_sync_client, GlideClusterClient)
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
        result1 = exec_batch(glide_sync_client, transaction)
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
        result2 = exec_batch(glide_sync_client, transaction)
        assert result2 is not None
        assert isinstance(result2, list)
        assert result2[0] == OK
        assert result2[1] == b"value"

        # Restore with frequency and idletime both set.
        with pytest.raises(RequestError) as e:
            glide_sync_client.restore(
                key2, 0, b"", replace=True, idletime=-10, frequency=10
            )
        assert "syntax error: IDLETIME and FREQ cannot be set at the same time." in str(
            e
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_transaction_function_dump_restore(
        self, glide_sync_client: TGlideClient, cluster_mode, protocol
    ):
        if not sync_check_if_server_version_lt(glide_sync_client, "7.0.0"):
            # Setup (will not verify)
            assert glide_sync_client.function_flush() == OK
            lib_name = f"mylib_{get_random_string(10)}"
            func_name = f"myfun_{get_random_string(10)}"
            code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_sync_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.function_load(code, True)

            # Verify function_dump
            transaction.function_dump()
            result1 = exec_batch(glide_sync_client, transaction)
            assert result1 is not None
            assert isinstance(result1, list)
            assert isinstance(result1[1], bytes)

            # Verify function_restore - use result1[2] from above
            transaction = (
                Batch(is_atomic=True)
                if isinstance(glide_sync_client, GlideClient)
                else ClusterBatch(is_atomic=True)
            )
            transaction.function_restore(result1[1], FunctionRestorePolicy.REPLACE)
            # For the cluster mode, PRIMARY SlotType is required to avoid the error:
            #  "RequestError: An error was signalled by the server -
            #   ReadOnly: You can't write against a read only replica."
            result2 = exec_batch(
                glide_sync_client, transaction, SlotIdRoute(SlotType.PRIMARY, 1)
            )

            assert result2 is not None
            assert isinstance(result2, list)
            assert result2[0] == OK

            # Test clean up
            glide_sync_client.function_flush()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_batch_timeout(self, glide_sync_client: TGlideClient, is_atomic: bool):
        assert glide_sync_client.custom_command(["FLUSHALL"]) == OK

        batch = (
            Batch(is_atomic=is_atomic)
            if isinstance(glide_sync_client, GlideClient)
            else ClusterBatch(is_atomic=is_atomic)
        )

        batch.custom_command(["DEBUG", "SLEEP", "0.5"])  # Sleep for 0.5 second

        # Expect a timeout on short timeout window
        with pytest.raises(TimeoutError, match="timed out"):
            exec_batch(glide_sync_client, batch, raise_on_error=True, timeout=100)

        # Wait for sleep to finish
        time.sleep(0.5)

        # Execute the same batch again with a longer timeout, should succeed now
        result = exec_batch(glide_sync_client, batch, raise_on_error=True, timeout=1000)
        assert result is not None
        assert len(result) == 1  # Should contain only the result of DEBUG SLEEP

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_deprecated_transaction_classes(self, glide_sync_client: TGlideClient):
        """Tests that the deprecated Transaction and ClusterTransaction classes still work.

        Verifies that they correctly set is_atomic=True and execute commands atomically.
        """
        key = get_random_string(10)
        value = get_random_string(10)

        transaction: Union[Transaction, ClusterTransaction]
        if isinstance(glide_sync_client, GlideClient):
            transaction = Transaction()
        else:
            transaction = ClusterTransaction()

        assert transaction.is_atomic is True

        # Add some simple commands
        transaction.set(key, value)
        transaction.get(key)

        result = exec_batch(glide_sync_client, transaction, raise_on_error=True)
        assert isinstance(result, list)
        assert result[0] == OK
        assert result[1] == value.encode()

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_batch_multislot(self, glide_sync_client: GlideClusterClient):
        num_keys = 10
        keys = [get_random_string(10) for _ in range(num_keys)]
        values = [get_random_string(5) for _ in range(num_keys)]

        mapping = dict(zip(keys, values))
        assert glide_sync_client.mset(mapping) == OK

        batch = ClusterBatch(is_atomic=False)
        batch.mget(keys)

        result = exec_batch(glide_sync_client, batch)

        expected = [v.encode() for v in values]

        assert isinstance(result, list)
        assert result[0] == expected

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_batch_all_nodes(self, glide_sync_client: GlideClusterClient):
        config_key = "maxmemory"
        config_value = "987654321"
        original_config = glide_sync_client.config_get([config_key])
        original_value = original_config.get(config_key.encode())

        try:
            batch = ClusterBatch(is_atomic=False)
            batch.config_set({config_key: config_value})

            result = glide_sync_client.exec(batch, raise_on_error=True)

            assert result == ["OK"]

            batch = ClusterBatch(is_atomic=False)
            batch.info()
            result = exec_batch(glide_sync_client, batch, raise_on_error=True)

            assert isinstance(result, list)
            assert len(result) == 1
            assert isinstance(result[0], dict)

            for info in result[0].values():
                assert isinstance(info, bytes)
            # Check if the config change is reflected in the info output
            assert f"{config_key}:{config_value}" in info.decode()  # type: ignore # noqa
            glide_sync_client.flushall(FlushMode.ASYNC)
        finally:
            if not isinstance(original_value, (str, bytes)):
                raise ValueError(
                    f"Cannot set config to non-string value: {original_value}"
                )
            glide_sync_client.config_set({config_key: original_value})

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_batch_raise_on_error(
        self, glide_sync_client: GlideClusterClient, is_atomic: bool
    ):
        key = get_random_string(10)
        # Ensure key2 is in the same slot as key for atomic transactions
        key2 = f"{{{key}}}:{get_random_string(10)}"  # type: ignore # noqa

        batch = (
            ClusterBatch(is_atomic=is_atomic)
            if isinstance(glide_sync_client, GlideClusterClient)
            else Batch(is_atomic=is_atomic)
        )

        batch.set(key, "hello")
        batch.lpop(key)
        batch.delete([key])
        batch.rename(key, key2)

        # Test with raise_on_error=False
        result = exec_batch(glide_sync_client, batch, raise_on_error=False)

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
            exec_batch(glide_sync_client, batch, raise_on_error=True)

        assert "WRONGTYPE" in str(e.value)

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("is_atomic", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_cluster_batch_route(
        self,
        glide_sync_client: GlideClusterClient,
        protocol: ProtocolVersion,
        is_atomic: bool,
    ):
        if sync_check_if_server_version_lt(glide_sync_client, "6.0.0"):
            pytest.skip("CONFIG RESETSTAT requires redis >= 6.0")

        assert glide_sync_client.config_resetstat() == OK

        key = get_random_string(10)
        value_bytes = b"value"

        batch = ClusterBatch(is_atomic=is_atomic)
        batch.set(key, value_bytes)
        batch.get(key)

        route = SlotKeyRoute(slot_type=SlotType.PRIMARY, slot_key=key)
        options = ClusterBatchOptions(route=route, timeout=2000)
        results = glide_sync_client.exec(batch, raise_on_error=True, options=options)

        assert results == [OK, value_bytes]

        # Check that no MOVED error occurred by inspecting errorstats on all nodes
        error_stats_dict = glide_sync_client.info(
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
    def test_sync_batch_retry_strategy_with_atomic_batch_raises_error(
        self, glide_sync_client: GlideClusterClient
    ):
        """Test that using retry strategies with atomic batches raises RequestError."""
        batch = ClusterBatch(is_atomic=True)
        batch.set("key", "value")

        retry_strategy = BatchRetryStrategy(retry_server_error=True)
        options = ClusterBatchOptions(retry_strategy=retry_strategy)

        with pytest.raises(
            RequestError, match="Retry strategies are not supported for atomic batches"
        ):
            glide_sync_client.exec(batch, raise_on_error=True, options=options)
