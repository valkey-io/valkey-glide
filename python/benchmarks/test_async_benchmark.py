import asyncio
import functools
import random
import sys
import time

import aioredis
import redis.asyncio as redispy
import uvloop

sys.path.append("../")
from src.async_client import RedisAsyncClient
from src.config import ClientConfiguration

HOST = "localhost"
PORT = 6379
TOTAL_COMMANDS = 10000
PROB_GET = 0.8
SIZE_GET_KEYSPACE = 3750000  # 3.75 million
SIZE_SET_KEYSPACE = 3000000  # 3 million
COUNTER = 0
running_tasks = set()
bench_results_dict = dict()
bench_str_results = []


def generate_value(size):
    return str(b"0" * size)


def generate_key_set():
    return str(random.randint(1, SIZE_SET_KEYSPACE + 1))


def generate_key_get():
    return str(random.randint(1, SIZE_GET_KEYSPACE + 1))


def should_get():
    return random.random() < PROB_GET


def print_results():
    global bench_str_results
    bench_str_results.sort()
    for res in bench_str_results:
        print(res)


def timer(func):
    @functools.wraps(func)
    async def wrapper(*args, **kwargs):
        tic = time.perf_counter()
        await func(*args, **kwargs)
        toc = time.perf_counter()
        return toc - tic

    return wrapper


async def redis_benchmark(client, data):
    global TOTAL_COMMANDS
    global COUNTER
    while COUNTER < TOTAL_COMMANDS:
        if should_get():
            await client.get(generate_key_get())
        else:
            await client.set(generate_key_set(), data)
        COUNTER += 1
    return True


@timer
async def create_bench_tasks(client, num_of_tasks, data):
    global COUNTER
    COUNTER = 0
    for _ in range(num_of_tasks):
        task = asyncio.create_task(redis_benchmark(client, data))
        running_tasks.add(task)
        task.add_done_callback(running_tasks.discard)
    await asyncio.gather(*(list(running_tasks)))


async def run_client(client, client_name, loop, data, num_of_tasks, data_size):
    global bench_results_dict
    global bench_str_results
    time = await create_bench_tasks(client, num_of_tasks, data)
    tps = int(COUNTER / time)
    loop_datasize_results = (
        bench_results_dict.setdefault(client_name, dict())
        .setdefault(loop, dict())
        .setdefault(num_of_tasks, dict())
        .setdefault(data_size, list())
    )
    loop_datasize_results.append(tps)
    bench_str_results.append(
        f"client:{client_name}, loop:{loop}, num_of_tasks:{num_of_tasks}, data_size:{data_size} TPS: {tps}"
    )


async def main(loop, num_of_tasks, data_size):
    data = generate_value(data_size)
    # Redis-py
    redispy_client = await redispy.Redis(host=HOST, port=PORT)
    await run_client(redispy_client, "redispy", loop, data, num_of_tasks, data_size)

    # AIORedis
    aioredis_client = await aioredis.from_url(f"redis://{HOST}:{PORT}")
    await run_client(aioredis_client, "aioredis", loop, data, num_of_tasks, data_size)

    # Babushka
    config = ClientConfiguration(host=HOST, port=PORT)
    babushka_client = await RedisAsyncClient.create(config)
    await run_client(babushka_client, "babushka", loop, data, num_of_tasks, data_size)


if __name__ == "__main__":
    asyncio.run(main("asyncio", 10, 100))
    asyncio.run(main("asyncio", 100, 100))
    asyncio.run(main("asyncio", 10, 4000))
    asyncio.run(main("asyncio", 100, 4000))

    uvloop.install()

    asyncio.run(main("uvloop", 10, 100))
    asyncio.run(main("uvloop", 100, 100))
    asyncio.run(main("uvloop", 10, 4000))
    asyncio.run(main("uvloop", 100, 4000))

    print_results()
