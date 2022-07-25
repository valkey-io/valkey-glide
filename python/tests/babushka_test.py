from datetime import datetime

import babushka


async def test_set_get():
    client = await babushka.Client.new(
        "redis://localhost:6379"
    )  # replace with your Redis server
    time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    await client.set("key", time_str)
    result = await client.get("key")
    assert result == time_str
