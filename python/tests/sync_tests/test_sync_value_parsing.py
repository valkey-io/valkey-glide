# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest
from glide_shared import RequestError
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK
from glide_sync.glide_client import GlideClusterClient, TGlideClient

from tests.utils.utils import get_random_string


@pytest.mark.parametrize("cluster_mode", [True, False])
@pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
class TestValueParsing:
    """Replies are converted straight from the core's parsed value (``parse_value``), so
    every reply shape has to come back as the same Python object the arena path produced.
    """

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
        results = glide_sync_client.exec(batch, raise_on_error=False)
        assert results is not None
        assert results[:5] == [OK, 2, 1, {b"f": b"v"}, None]
        assert isinstance(results[5], RequestError)
        with pytest.raises(RequestError):
            glide_sync_client.exec(batch, raise_on_error=True)
