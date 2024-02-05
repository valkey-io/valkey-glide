# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from datetime import datetime, timezone
from typing import List, Union

import pytest
from glide import RequestError
from glide.async_commands.core import InfBound, ScoreLimit
from glide.async_commands.transaction import (
    BaseTransaction,
    ClusterTransaction,
    Transaction,
)
from glide.config import ProtocolVersion
from glide.constants import OK, TResult
from glide.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.conftest import create_client
from tests.test_async_client import get_random_string


def transaction_test(
    transaction: Union[Transaction, ClusterTransaction], keyslot: str
) -> List[TResult]:
    key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
    key2 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
    key3 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key4 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key5 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key6 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key7 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    key8 = "{{{}}}:{}".format(keyslot, get_random_string(3))

    value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
    value2 = get_random_string(5)

    transaction.set(key, value)
    transaction.get(key)

    transaction.exists([key])

    transaction.delete([key])
    transaction.get(key)

    transaction.mset({key: value, key2: value2})
    transaction.mget([key, key2])

    transaction.incr(key3)
    transaction.incrby(key3, 2)

    transaction.decr(key3)
    transaction.decrby(key3, 2)

    transaction.incrbyfloat(key3, 0.5)

    transaction.unlink([key3])

    transaction.ping()

    transaction.config_set({"timeout": "1000"})
    transaction.config_get(["timeout"])

    transaction.hset(key4, {key: value, key2: value2})
    transaction.hget(key4, key2)

    transaction.hincrby(key4, key3, 5)
    transaction.hincrbyfloat(key4, key3, 5.5)

    transaction.hexists(key4, key)
    transaction.hmget(key4, [key, "nonExistingField", key2])
    transaction.hgetall(key4)
    transaction.hdel(key4, [key, key2])

    transaction.client_getname()

    transaction.lpush(key5, [value, value, value2, value2])
    transaction.llen(key5)
    transaction.lpop(key5)
    transaction.lrem(key5, 1, value)
    transaction.ltrim(key5, 0, 1)
    transaction.lrange(key5, 0, -1)
    transaction.lpop_count(key5, 2)

    transaction.rpush(key6, [value, value2, value2])
    transaction.rpop(key6)
    transaction.rpop_count(key6, 2)

    transaction.sadd(key7, ["foo", "bar"])
    transaction.srem(key7, ["foo"])
    transaction.smembers(key7)
    transaction.scard(key7)

    transaction.zadd(key8, {"one": 1, "two": 2, "three": 3})
    transaction.zadd_incr(key8, "one", 3)
    transaction.zrem(key8, ["one"])
    transaction.zcard(key8)
    transaction.zcount(key8, ScoreLimit(2, True), InfBound.POS_INF)
    transaction.zscore(key8, "two")
    return [
        OK,
        value,
        1,
        1,
        None,
        OK,
        [value, value2],
        1,
        3,
        2,
        0,
        0.5,
        1,
        "PONG",
        OK,
        {"timeout": "1000"},
        2,
        value2,
        5,
        10.5,
        True,
        [value, None, value2],
        {key: value, key2: value2, key3: "10.5"},
        2,
        None,
        4,
        4,
        value2,
        1,
        OK,
        [value2, value],
        [value2, value],
        3,
        value2,
        [value2, value],
        2,
        1,
        {"bar"},
        1,
        3,
        4,
        1,
        2,
        2,
        2.0,
    ]


@pytest.mark.asyncio
class TestTransaction:
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_transaction_with_different_slots(self, redis_client: TRedisClient):
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "Moved" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_custom_command(self, redis_client: TRedisClient):
        key = get_random_string(10)
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )
        transaction.custom_command(["HSET", key, "foo", "bar"])
        transaction.custom_command(["HGET", key, "foo"])
        result = await redis_client.exec(transaction)
        assert result == [1, "bar"]

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_custom_unsupported_command(
        self, redis_client: TRedisClient
    ):
        key = get_random_string(10)
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )
        transaction.custom_command(["WATCH", key])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "WATCH inside MULTI is not allowed" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_discard_command(self, redis_client: TRedisClient):
        key = get_random_string(10)
        await redis_client.set(key, "1")
        transaction = (
            Transaction()
            if isinstance(redis_client, RedisClient)
            else ClusterTransaction()
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await redis_client.get(key)
        assert value == "1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_exec_abort(self, redis_client: TRedisClient):
        key = get_random_string(10)
        transaction = BaseTransaction()
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(RequestError) as e:
            await redis_client.exec(transaction)
        assert "wrong number of arguments" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_cluster_transaction(self, redis_client: RedisClusterClient):
        keyslot = get_random_string(3)
        transaction = ClusterTransaction()
        transaction.info()
        expected = transaction_test(transaction, keyslot)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:] == expected

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_can_return_null_on_watch_transaction_failures(
        self, redis_client: TRedisClient, request
    ):
        is_cluster = isinstance(redis_client, RedisClusterClient)
        client2 = await create_client(
            request,
            is_cluster,
        )
        keyslot = get_random_string(3)
        transaction = ClusterTransaction() if is_cluster else Transaction()
        transaction.get(keyslot)
        result1 = await redis_client.custom_command(["WATCH", keyslot])
        assert result1 == OK

        result2 = await client2.set(keyslot, "foo")
        assert result2 == OK

        result3 = await redis_client.exec(transaction)
        assert result3 is None

        await client2.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_standalone_transaction(self, redis_client: RedisClient):
        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        value = get_random_string(5)
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.set(key, value)
        transaction.get(key)
        transaction.select(0)
        transaction.get(key)
        expected = transaction_test(transaction, keyslot)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:6] == [OK, OK, value, OK, None]
        assert result[6:] == expected

    # this test ensures that all types in RESP2 are converted to their RESP3 equivalent.
    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_standalone_transaction_on_resp2(self, cluster_mode, request):
        redis_client = await create_client(
            request,
            cluster_mode,
            protocol=ProtocolVersion.RESP2,
        )

        keyslot = get_random_string(3)
        key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
        value = get_random_string(5)
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.set(key, value)
        transaction.get(key)
        transaction.select(0)
        transaction.get(key)
        expected = transaction_test(transaction, keyslot)
        result = await redis_client.exec(transaction)
        assert isinstance(result, list)
        assert isinstance(result[0], str)
        assert "# Memory" in result[0]
        assert result[1:6] == [OK, OK, value, OK, None]
        assert result[6:] == expected

    def test_transaction_clear(self):
        transaction = Transaction()
        transaction.info()
        transaction.select(1)
        transaction.clear()
        assert len(transaction.commands) == 0
