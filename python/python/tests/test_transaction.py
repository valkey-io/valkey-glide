from datetime import datetime
from typing import List, Union

import pytest
from pybushka.async_commands.cmd_commands import Transaction
from pybushka.async_commands.cme_commands import ClusterTransaction
from pybushka.async_commands.core import BaseTransaction
from pybushka.constants import OK, TResult
from pybushka.protobuf.redis_request_pb2 import RequestType
from pybushka.redis_client import RedisClient, RedisClusterClient, TRedisClient
from tests.test_async_client import get_random_string


def transaction_test_helper(
    transaction: BaseTransaction,
    expected_request_type: RequestType,
    expected_args: List[str],
):
    assert transaction.commands[-1][0] == expected_request_type
    assert transaction.commands[-1][1] == expected_args


def transaction_test(
    transaction: Union[Transaction, ClusterTransaction], keyslot: str
) -> List[TResult]:
    key = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
    key2 = "{{{}}}:{}".format(keyslot, get_random_string(3))  # to get the same slot
    key3 = "{{{}}}:{}".format(keyslot, get_random_string(3))
    value = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    value2 = get_random_string(5)

    transaction.set(key, value)
    transaction.get(key)

    transaction.delete([key])
    transaction.get(key)

    transaction.mset({key: value, key2: value2})
    transaction.mget([key, key2])

    transaction.incr(key3)
    transaction.incrby(key3, 2)

    transaction.decr(key3)
    transaction.decrby(key3, 2)

    transaction.incrbyfloat(key3, 0.5)

    transaction.ping()

    transaction.config_set({"timeout": "1000"})
    transaction.config_get(["timeout"])

    return [
        OK,
        value,
        1,
        None,
        OK,
        [value, value2],
        1,
        3,
        2,
        0,
        "0.5",
        "PONG",
        OK,
        ["timeout", "1000"],
    ]


@pytest.mark.asyncio
class TestTransaction:
    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_transaction_with_different_slots(self, redis_client: TRedisClient):
        transaction = (
            Transaction() if type(redis_client) == RedisClient else ClusterTransaction()
        )
        transaction.set("key1", "value1")
        transaction.set("key2", "value2")
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "Moved" in str(e)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_custom_command(self, redis_client: TRedisClient):
        key = get_random_string(10)
        transaction = (
            Transaction() if type(redis_client) == RedisClient else ClusterTransaction()
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
            Transaction() if type(redis_client) == RedisClient else ClusterTransaction()
        )
        transaction.custom_command(["WATCH", key])
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "WATCH inside MULTI is not allowed" in str(
            e
        )  # TODO : add an assert on EXEC ABORT

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_discard_command(self, redis_client: TRedisClient):
        key = get_random_string(10)
        await redis_client.set(key, "1")
        transaction = (
            Transaction() if type(redis_client) == RedisClient else ClusterTransaction()
        )

        transaction.custom_command(["INCR", key])
        transaction.custom_command(["DISCARD"])
        with pytest.raises(Exception) as e:
            await redis_client.exec(transaction)
        assert "EXEC without MULTI" in str(e)  # TODO : add an assert on EXEC ABORT
        value = await redis_client.get(key)
        assert value == "1"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_transaction_exec_abort(self, redis_client: TRedisClient):
        key = get_random_string(10)
        transaction = BaseTransaction()
        transaction.custom_command(["INCR", key, key, key])
        with pytest.raises(Exception) as e:
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
        assert type(result[0]) == str
        assert "# Memory" in result[0]
        assert result[1:] == expected

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
        assert type(result[0]) == str
        assert "# Memory" in result[0]
        assert result[1:6] == [OK, OK, value, OK, None]
        assert result[6:] == expected
