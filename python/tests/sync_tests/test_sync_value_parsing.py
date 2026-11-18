# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import cast

import pytest
from glide_shared import RequestError
from glide_shared._fast_response import value_abi_token
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK
from glide_sync import ClusterScanCursor
from glide_sync.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide_sync.sync_commands.script import Script

from tests.utils.utils import get_random_string, sync_check_if_server_version_lt


@pytest.mark.parametrize("cluster_mode", [True, False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
class TestValueParsing:
    """Replies are converted straight from the core's parsed value (``parse_value``), so
    every reply shape has to come back as the same Python object the arena path produced.
    """

    def test_value_path_is_active(self, glide_sync_client: TGlideClient):
        """Every assertion in this class also holds on the arena path.

        The client opts into value results only when both native libraries report
        the same ``Value`` layout, and stays on the arena when they disagree, so
        the shapes below would keep passing while the conversion under test never
        ran. This is the test that says it did.
        """
        assert value_abi_token() == glide_sync_client._lib.value_abi_token()
        assert glide_sync_client._value_results_enabled

    def test_script_reply_shapes(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        assert glide_sync_client.invoke_script(Script("return 7")) == 7
        assert glide_sync_client.invoke_script(Script("return 'Hello'")) == b"Hello"
        assert (
            glide_sync_client.invoke_script(
                Script("return redis.call('SET', KEYS[1], ARGV[1])"),
                keys=[key],
                args=["value1"],
            )
            == OK
        )
        assert (
            glide_sync_client.invoke_script(
                Script("return redis.call('GET', KEYS[1])"), keys=[key]
            )
            == b"value1"
        )

    def test_attribute_and_big_number_shapes(
        self, glide_sync_client: TGlideClient, protocol
    ):
        """The two shapes the arena path answered with ``todo!()``.

        RESP3 attributes become ``{"value", "attributes"}`` dicts and big numbers
        become Python ints, both following the async client; under RESP2 the server
        sends neither type, so the same commands return plain bulk strings.
        """
        if sync_check_if_server_version_lt(glide_sync_client, "7.0.0"):
            pytest.skip("DEBUG PROTOCOL needs Redis 7.0 / Valkey 7.2")

        def one_node(result):
            # A keyless command fans out to every node on a cluster client.
            if isinstance(glide_sync_client, GlideClusterClient):
                return next(iter(result.values()))
            return result

        big_number = one_node(
            glide_sync_client.custom_command(["DEBUG", "PROTOCOL", "bignum"])
        )
        attribute = one_node(
            glide_sync_client.custom_command(["DEBUG", "PROTOCOL", "attrib"])
        )
        if protocol == ProtocolVersion.RESP3:
            assert big_number == 1234567999999999999999999999999999999
            assert attribute == {
                "value": b"Some real reply following the attribute",
                "attributes": {b"key-popularity": [b"key:123", 90]},
            }
        else:
            assert big_number == b"1234567999999999999999999999999999999"
            assert attribute == b"Some real reply following the attribute"

    def test_cluster_scan_reply_shapes(self, glide_sync_client: TGlideClient):
        if not isinstance(glide_sync_client, GlideClusterClient):
            pytest.skip("cluster scan needs a cluster client")
        key = get_random_string(10)
        glide_sync_client.set(key, "v")
        cursor = ClusterScanCursor()
        keys: list = []
        while not cursor.is_finished():
            result = glide_sync_client.scan(cursor, match=key)
            cursor = cast(ClusterScanCursor, result[0])
            keys.extend(cast(list, result[1]))
        assert keys == [key.encode()]

    def test_command_reply_shapes(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        assert glide_sync_client.set(key, "v") == OK
        assert glide_sync_client.get(key) == b"v"
        assert glide_sync_client.get(key + ":missing") is None
        assert glide_sync_client.incr(key + ":n") == 1
        assert glide_sync_client.incrbyfloat(key + ":f", 1.5) == 1.5
        assert glide_sync_client.hset(key + ":h", {"a": "1", "b": "2"}) == 2
        assert glide_sync_client.hgetall(key + ":h") == {b"a": b"1", b"b": b"2"}
        assert glide_sync_client.hmget(key + ":h", ["a", "missing"]) == [b"1", None]
        assert glide_sync_client.sadd(key + ":s", ["x", "y"]) == 2
        assert glide_sync_client.smembers(key + ":s") == {b"x", b"y"}
        assert glide_sync_client.rpush(key + ":l", ["1", "2", "3"]) == 3
        assert glide_sync_client.lrange(key + ":l", 0, -1) == [b"1", b"2", b"3"]
        assert glide_sync_client.mget([key, key + ":missing"]) == [b"v", None]
        with pytest.raises(RequestError):
            glide_sync_client.incr(key + ":h")

    def test_batch_reply_shapes(self, glide_sync_client: TGlideClient):
        key = get_random_string(10)
        batch = (
            ClusterBatch(is_atomic=False)
            if isinstance(glide_sync_client, GlideClusterClient)
            else Batch(is_atomic=False)
        )
        batch.set(key, "1")
        batch.incr(key)
        batch.hset(key + ":h", {"f": "v"})
        batch.hgetall(key + ":h")
        batch.get(key + ":missing")
        batch.custom_command(["INCR", key + ":h"])
        results = cast(GlideClient, glide_sync_client).exec(
            cast(Batch, batch), raise_on_error=False
        )
        assert results is not None
        assert results[:5] == [OK, 2, 1, {b"f": b"v"}, None]
        assert isinstance(results[5], RequestError)
        with pytest.raises(RequestError):
            cast(GlideClient, glide_sync_client).exec(
                cast(Batch, batch), raise_on_error=True
            )
