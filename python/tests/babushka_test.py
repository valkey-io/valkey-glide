import babushka
from datetime import datetime

async def test_set_get():
  client = await babushka.Client.new("redis://redis-benchmark-test.zww8pv.ng.0001.use1.cache.amazonaws.com:6379") # replace weith your Redis server
  time_str = datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
  await client.set("key", time_str)
  result = await client.get("key")
  assert result == time_str