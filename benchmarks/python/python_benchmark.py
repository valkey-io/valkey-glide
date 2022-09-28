import asyncio
import functools
import json
import os
import random
import time
import argparse

import aioredis
import numpy as np
import redis.asyncio as redispy
import uvloop
from pybushka import AsyncClient, ClientConfiguration, RedisAsyncClient

arguments_parser = argparse.ArgumentParser()
arguments_parser.add_argument(
    "--resultsFile",
    help="Where to write the results file",
    required=True,
)
arguments_parser.add_argument(
    "--dataSize",
    help="List of sizes of data to use",
    nargs="+",
    required=True,
)
arguments_parser.add_argument(
    "--concurrentTasks",
    help="List of number of concurrent tasks to run",
    nargs="+",
    required=True,
)
args = arguments_parser.parse_args()

HOST = "localhost"
PORT = 6379
PROB_GET = 0.8
SIZE_GET_KEYSPACE = 3750000  # 3.75 million
SIZE_SET_KEYSPACE = 3000000  # 3 million
counter = 0
running_tasks = set()
bench_str_results = []
bench_json_results = []
get_latency = dict()
set_latency = dict()


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


def process_results():
    global bench_str_results
    global bench_json_results
    global args

    # print results
    bench_str_results.sort()
    for res in bench_str_results:
        print(res)

    # write json results to a file
    res_file_path = args.resultsFile
    with open(res_file_path, "w+") as f:
        json.dump(bench_json_results, f)


def timer(func):
    @functools.wraps(func)
    async def wrapper(*args, **kwargs):
        tic = time.perf_counter()
        await func(*args, **kwargs)
        toc = time.perf_counter()
        return toc - tic

    return wrapper


async def execute_commands(client, client_name, total_commands, data_size):
    global counter
    while counter < total_commands:
        do_get = should_get()
        tic = time.perf_counter()
        if do_get:
            await client.get(generate_key_get())
        else:
            await client.set(generate_key_set(), generate_value(data_size))
        toc = time.perf_counter()
        execution_time = toc - tic
        (get_latency if do_get else set_latency).get(client_name).append(execution_time)
        counter += 1
    return True


@timer
async def create_and_run_concurrent_tasks(
    client, client_name, total_commands, num_of_concurrent_tasks, data_size
):
    global counter
    global get_latency
    global set_latency
    counter = 0
    get_latency.setdefault(client_name, list()).clear()
    set_latency.setdefault(client_name, list()).clear()
    for _ in range(num_of_concurrent_tasks):
        task = asyncio.create_task(
            execute_commands(client, client_name, total_commands, data_size)
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
):
    global bench_str_results
    time = await create_and_run_concurrent_tasks(
        client, client_name, total_commands, num_of_concurrent_tasks, data_size
    )
    tps = int(counter / time)
    get_50 = calculate_latency(get_latency[client_name], 50)
    get_90 = calculate_latency(get_latency[client_name], 90)
    get_99 = calculate_latency(get_latency[client_name], 99)
    get_std_dev = np.std(get_latency[client_name])
    set_50 = calculate_latency(set_latency[client_name], 50)
    set_90 = calculate_latency(set_latency[client_name], 90)
    set_99 = calculate_latency(set_latency[client_name], 99)
    set_std_dev = np.std(set_latency[client_name])
    json_res = {
        "client": client_name,
        "loop": event_loop_name,
        "num_of_tasks": num_of_concurrent_tasks,
        "data_size": data_size,
        "tps": tps,
        "get_p50_latency": get_50,
        "get_p90_latency": get_90,
        "get_p99_latency": get_99,
        "get_std_dev": get_std_dev,
        "set_p50_latency": set_50,
        "set_p90_latency": set_90,
        "set_p99_latency": set_99,
        "set_std_dev": set_std_dev,
    }

    bench_json_results.append(json_res)
    bench_str_results.append(
        f"client: {client_name}, event_loop: {event_loop_name}, concurrent_tasks: {num_of_concurrent_tasks}, "
        f"data_size: {data_size}, TPS: {tps}, "
        f"get_p50: {get_50}, get_p90: {get_90}, get_p99: {get_99}, get_std_dev: {get_std_dev}, "
        f"set_p50: {set_50}, set_p90: {set_90}, set_p99: {set_99}, set_std_dev: {set_std_dev} "
    )


async def main(event_loop_name, total_commands, num_of_concurrent_tasks, data_size):
    # Redis-py
    redispy_client = await redispy.Redis(host=HOST, port=PORT)
    await run_client(
        redispy_client,
        "redispy",
        event_loop_name,
        total_commands,
        num_of_concurrent_tasks,
        data_size,
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
    )

    direct_babushka = await AsyncClient.create_client(f"redis://{HOST}:{PORT}")
    await run_client(
        direct_babushka,
        "direct_babushka",
        event_loop_name,
        total_commands,
        num_of_concurrent_tasks,
        data_size,
    )


def number_of_iterations(num_of_concurrent_tasks):
    return max(100000, num_of_concurrent_tasks * 10000)


if __name__ == "__main__":
    concurrent_tasks = args.concurrentTasks
    data_size = args.dataSize

    product_of_arguments = [
        (int(data_size), int(num_of_concurrent_tasks))
        for data_size in data_size
        for num_of_concurrent_tasks in concurrent_tasks
    ]

    for (data_size, num_of_concurrent_tasks) in product_of_arguments:
        asyncio.run(
            main(
                "asyncio",
                number_of_iterations(num_of_concurrent_tasks),
                num_of_concurrent_tasks,
                data_size,
            )
        )

    uvloop.install()

    for (data_size, num_of_concurrent_tasks) in product_of_arguments:
        asyncio.run(
            main(
                "uvloop",
                number_of_iterations(num_of_concurrent_tasks),
                num_of_concurrent_tasks,
                data_size,
            )
        )

    process_results()
