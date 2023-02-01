import asyncio
import random
import string
from datetime import datetime

import pytest
from pybushka.Logger import Level as logLevel
from pybushka.Logger import set_logger_config

set_logger_config(logLevel.INFO)


def get_random_string(length):
    letters = string.ascii_letters + string.digits + string.punctuation
    result_str = "".join(random.choice(letters) for i in range(length))
    return result_str


@pytest.mark.asyncio
class TestSocketClient:
    async def test_set_get(self, async_socket_client):
        key = get_random_string(5)
        value = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        assert await async_socket_client.set(key, value) is None
        assert await async_socket_client.get(key) == value

    async def test_large_values(self, async_socket_client):
        length = 2**16
        key = get_random_string(length)
        value = get_random_string(length)
        assert len(key) == length
        assert len(value) == length
        await async_socket_client.set(key, value)
        assert await async_socket_client.get(key) == value

    async def test_non_ascii_unicode(self, async_socket_client):
        key = "foo"
        value = "שלום hello 汉字"
        assert value == "שלום hello 汉字"
        await async_socket_client.set(key, value)
        assert await async_socket_client.get(key) == value

    @pytest.mark.parametrize("value_size", [100, 2**16])
    async def test_concurrent_tasks(self, async_socket_client, value_size):
        num_of_concurrent_tasks = 20
        running_tasks = set()

        async def exec_command(i):
            value = get_random_string(value_size)
            await async_socket_client.set(str(i), value)
            assert await async_socket_client.get(str(i)) == value

        for i in range(num_of_concurrent_tasks):
            task = asyncio.create_task(exec_command(i))
            running_tasks.add(task)
            task.add_done_callback(running_tasks.discard)
        await asyncio.gather(*(list(running_tasks)))


@pytest.mark.asyncio
class TestCoreCommands:
    async def test_set_get(self, async_ffi_client):
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        await async_ffi_client.set("key", time_str)
        result = await async_ffi_client.get("key")
        assert result == time_str


@pytest.mark.asyncio
class TestPipeline:
    async def test_set_get_pipeline(self, async_ffi_client):
        pipeline = async_ffi_client.create_pipeline()
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        pipeline.set("pipeline_key", time_str)
        pipeline.get("pipeline_key")
        result = await pipeline.execute()
        assert result == ["OK", time_str]

    async def test_set_get_pipeline_chained_requests(self, async_ffi_client):
        pipeline = async_ffi_client.create_pipeline()
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        result = (
            await pipeline.set("pipeline_key", time_str).get("pipeline_key").execute()
        )
        assert result == ["OK", time_str]

    async def test_set_with_ignored_result(self, async_ffi_client):
        pipeline = async_ffi_client.create_pipeline()
        time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        result = (
            await pipeline.set("pipeline_key", time_str, True)
            .get("pipeline_key")
            .execute()
        )
        assert result == [time_str]
