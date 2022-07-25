from datetime import datetime

import babushka


async def test_set_get():
    client = await babushka.AsyncClient.new(
        "redis://localhost:6379"
    )  # replace with your Redis server
    time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    await client.set("key", time_str)
    result = await client.get("key")
    assert result == time_str


async def test_set_get_pipeline():
    client = await babushka.AsyncClient.new(
        "redis://localhost:6379"
    )  # replace weith your Redis server
    pipeline = client.create_pipeline()
    time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    pipeline.set("pipeline_key", time_str)
    pipeline.get("pipeline_key")
    result = await pipeline.execute()
    assert result == ["OK", time_str]


async def test_set_get_pipeline_chained_requests():
    client = await babushka.AsyncClient.new(
        "redis://localhost:6379"
    )  # replace weith your Redis server
    pipeline = client.create_pipeline()
    time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    result = await pipeline.set("pipeline_key", time_str).get("pipeline_key").execute()
    assert result == ["OK", time_str]
