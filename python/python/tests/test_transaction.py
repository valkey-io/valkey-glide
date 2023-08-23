from datetime import datetime
from typing import List

import pytest
from pybushka.async_commands.core import InfoSection, Transaction
from pybushka.constants import OK
from pybushka.protobuf.redis_request_pb2 import RequestType
from pybushka.redis_client import BaseRedisClient
from tests.test_async_client import get_random_string


def transaction_test_helper(
    transaction: Transaction,
    expected_request_type: RequestType,
    expected_args: List[str],
):
    assert transaction.commands[-1][0] == expected_request_type
    assert transaction.commands[-1][1] == expected_args


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

    async def test_transaction_info(self):
        sections = [InfoSection.SERVER, InfoSection.REPLICATION]
        transaction = Transaction()
        transaction.info(sections)
        transaction_test_helper(
            transaction, RequestType.Info, ["server", "replication"]
        )
