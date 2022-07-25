from datetime import datetime

import pytest


@pytest.mark.asyncio
async def test_set_get(async_client):
    time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    await async_client.set("key", time_str)
    result = await async_client.get("key")
    assert result == time_str
