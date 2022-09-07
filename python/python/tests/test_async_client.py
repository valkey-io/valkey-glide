import logging
import os
from datetime import datetime

import pytest
from pybushka import start_socket_listener_external
from pybushka.utils import to_url

LOGGER = logging.getLogger(__name__)


class TestSocketClient:
    def test_start_socket_listener_external(self, async_uds_client):
        # Make sure the socket does not already exist
        assert 1


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
