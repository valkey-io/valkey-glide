import asyncio
import functools
import random
import time

import aioredis
import redis.asyncio as redispy
import uvloop

from babushkapy import ClientConfiguration, RedisAsyncClient

HOST = "localhost"
PORT = 6379
PROB_GET = 0.8
SIZE_GET_KEYSPACE = 3750000  # 3.75 million
SIZE_SET_KEYSPACE = 3000000  # 3 million
counter = 0
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


async def redis_benchmark(client, total_commands, data):
    global counter
    while counter < total_commands:
        if should_get():
            await client.get(generate_key_get())
        else:
            await client.set(generate_key_set(), data)
        counter += 1
    return True


@timer
async def create_bench_tasks(client, total_commands, num_of_concurrent_tasks, data):
    global counter
    counter = 0
    for _ in range(num_of_concurrent_tasks):
        task = asyncio.create_task(redis_benchmark(client, total_commands, data))
        running_tasks.add(task)
        task.add_done_callback(running_tasks.discard)
    await asyncio.gather(*(list(running_tasks)))


async def run_client(
    client,
    client_name,
    event_loop_name,
    total_commands,
    num_of_concurrent_tasks,
    data_size,
    data,
):
    global bench_results_dict
    global bench_str_results
    time = await create_bench_tasks(
        client, total_commands, num_of_concurrent_tasks, data
    )
    tps = int(counter / time)
    loop_datasize_results = (
        bench_results_dict.setdefault(client_name, dict())
        .setdefault(event_loop_name, dict())
        .setdefault(num_of_concurrent_tasks, dict())
        .setdefault(data_size, list())
    )
    loop_datasize_results.append(tps)
    bench_str_results.append(
        f"client:{client_name}, event_loop:{event_loop_name}, total_commands:{total_commands}, "
        f"num_of_concurrent_tasks:{num_of_concurrent_tasks}, data_size:{data_size} TPS: {tps}"
    )


async def main(event_loop_name, total_commands, num_of_concurrent_tasks, data_size):
    data = generate_value(data_size)
    # Redis-py
    redispy_client = await redispy.Redis(host=HOST, port=PORT)
    await run_client(
        redispy_client,
        "redispy",
        event_loop_name,
        total_commands,
        num_of_concurrent_tasks,
        data_size,
        data,
    )

    # AIORedis
    aioredis_client = await aioredis.from_url(f"redis://{HOST}:{PORT}")
    await run_client(
        aioredis_client,
        "aioredis",
        event_loop_name,
        total_commands,
        num_of_concurrent_tasks,
        data_size,
        data,
    )

    # Babushka
    config = ClientConfiguration(host=HOST, port=PORT)
    babushka_client = await RedisAsyncClient.create(config)
    await run_client(
        babushka_client,
        "babushka",
        event_loop_name,
        total_commands,
        num_of_concurrent_tasks,
        data_size,
        data,
    )


if __name__ == "__main__":
    asyncio.run(main("asyncio", 100000, 10, 100))
    asyncio.run(main("asyncio", 1000000, 100, 100))
    asyncio.run(main("asyncio", 100000, 10, 4000))
    asyncio.run(main("asyncio", 1000000, 100, 4000))

    uvloop.install()

    asyncio.run(main("uvloop", 100000, 10, 100))
    asyncio.run(main("uvloop", 1000000, 100, 100))
    asyncio.run(main("uvloop", 100000, 10, 4000))
    asyncio.run(main("uvloop", 1000000, 100, 4000))

    print_results()
