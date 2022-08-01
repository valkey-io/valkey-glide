import asyncio
import functools
import json
import random
import time

import aioredis
import numpy as np
import redis.asyncio as redispy
import uvloop
from babushkapy import AsyncClient, ClientConfiguration, RedisAsyncClient

HOST = "localhost"
PORT = 6379
PROB_GET = 0.8
SIZE_GET_KEYSPACE = 3750000  # 3.75 million
SIZE_SET_KEYSPACE = 3000000  # 3 million
counter = 0
running_tasks = set()
bench_str_results = []
bench_json_results = []
get_latecny = dict()
set_latecny = dict()


def generate_value(size):
    return str(b"0" * size)


def generate_key_set():
    return str(random.randint(1, SIZE_SET_KEYSPACE + 1))


def generate_key_get():
    return str(random.randint(1, SIZE_GET_KEYSPACE + 1))


def should_get():
    return random.random() < PROB_GET


def calculate_latency(latency_list, percentile):
    return round(np.percentile(np.array(latency_list), percentile), 4)


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


async def redis_benchmark(client, client_name, total_commands, data):
    global counter
    while counter < total_commands:
        if should_get():
            tic = time.perf_counter()
            await client.get(generate_key_get())
            toc = time.perf_counter()
            get_latecny.get(client_name).append(toc - tic)
        else:
            tic = time.perf_counter()
            await client.set(generate_key_set(), data)
            toc = time.perf_counter()
            set_latecny.get(client_name).append(toc - tic)
        counter += 1
    return True


@timer
async def create_bench_tasks(
    client, client_name, total_commands, num_of_concurrent_tasks, data
):
    global counter
    global get_latecny
    global set_latecny
    counter = 0
    get_latecny.setdefault(client_name, list()).clear()
    set_latecny.setdefault(client_name, list()).clear()
    for _ in range(num_of_concurrent_tasks):
        task = asyncio.create_task(
            redis_benchmark(client, client_name, total_commands, data)
        )
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
    global bench_str_results
    time = await create_bench_tasks(
        client, client_name, total_commands, num_of_concurrent_tasks, data
    )
    tps = int(counter / time)
    get_50 = calculate_latency(get_latecny[client_name], 50)
    get_90 = calculate_latency(get_latecny[client_name], 90)
    get_99 = calculate_latency(get_latecny[client_name], 99)
    set_50 = calculate_latency(set_latecny[client_name], 50)
    set_90 = calculate_latency(set_latecny[client_name], 90)
    set_99 = calculate_latency(set_latecny[client_name], 99)
    json_res = {
        "client": client_name,
        "loop": event_loop_name,
        "num_of_tasks": num_of_concurrent_tasks,
        "data_size": data_size,
        "tps": tps,
        "latency": {
            "get_50": get_50,
            "get_90": get_90,
            "get_99": get_99,
            "set_50": set_50,
            "set_90": set_90,
            "set_99": set_99,
        },
    }

    bench_json_results.append(json.dumps(json_res))
    bench_str_results.append(
        f"client: {client_name}, event_loop: {event_loop_name}, concurrent_tasks: {num_of_concurrent_tasks}, "
        f"data_size: {data_size}, TPS: {tps}, get_p50: {get_50}, get_p90: {get_90}, get_p99: {get_99}, "
        f" set_p50: {set_50}, set_p90: {set_90}, set_p99: {set_99}"
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

    direct_babushka = await AsyncClient.new(f"redis://{HOST}:{PORT}")
    await run_client(
        direct_babushka,
        "direct_babushka",
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
