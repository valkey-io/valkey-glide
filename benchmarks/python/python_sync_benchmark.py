# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import functools
from threading import Thread
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import List

import redis as redispy  # type: ignore
from redis.cluster import RedisCluster
from glide_sync import (
    GlideClient,
    GlideClientConfiguration,
    GlideClusterClient,
    GlideClusterClientConfiguration,
    Logger,
    LogLevel,
    NodeAddress,
)
from utils import (
    ChosenAction,
    choose_action,
    create_argument_parser,
    generate_value,
    generate_key_set,
    generate_key_get,
    latency_results,
    number_of_iterations,
    process_results,
    truncate_decimal,
)

arguments_parser = create_argument_parser()
args = arguments_parser.parse_args()

started_tasks_counter = 0
bench_json_results: List[str] = []


def timer(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        tic = time.perf_counter()
        func(*args, **kwargs)
        toc = time.perf_counter()
        return toc - tic

    return wrapper


def execute_commands(clients, total_commands, data_size, action_latencies):
    global started_tasks_counter
    while started_tasks_counter < total_commands:
        started_tasks_counter += 1
        chosen_action = choose_action()
        client = clients[started_tasks_counter % len(clients)]
        tic = time.perf_counter()
        if chosen_action == ChosenAction.GET_EXISTING:
            client.get(generate_key_set())
        elif chosen_action == ChosenAction.GET_NON_EXISTING:
            client.get(generate_key_get())
        elif chosen_action == ChosenAction.SET:
            client.set(generate_key_set(), generate_value(data_size))
        toc = time.perf_counter()
        execution_time_milli = (toc - tic) * 1000
        action_latencies[chosen_action].append(truncate_decimal(execution_time_milli))
    return True


@timer
def create_and_run_concurrent_threads(
    clients, total_commands, num_of_concurrent_threads, data_size, action_latencies
):
    threads = []
    global started_tasks_counter
    started_tasks_counter = 0

    for i in range(num_of_concurrent_threads):
        thread = Thread(
            target=execute_commands,
            args=(clients, total_commands, data_size, action_latencies),
            name=f"Worker-{i}",
        )
        threads.append(thread)
        thread.start()

    for thread in threads:
        thread.join()


def warmup_connections_with_threads(clients, num_of_concurrent_threads, is_cluster):
    """
    Pre-warm all connections to eliminate lazy creation overhead during benchmarking.

    Creates one thread per desired connection, each executing a blocking WAIT command
    targeting all cluster primaries simultaneously. Since WAIT blocks each connection,
    the connection pool is forced to create new connections rather than reuse existing ones.
    This ensures every benchmark thread has a dedicated, pre-established connection to
    each cluster node, eliminating connection setup latency from timing measurements.
    """
    print(f"Starting redis-py warm-up with {num_of_concurrent_threads} threads...")

    def open_connection_to_all_nodes_and_block_it(client):
        if is_cluster:
            client.wait(
                num_replicas=999, timeout=60000, target_nodes=RedisCluster.PRIMARIES
            )
        else:
            client.wait(num_replicas=999, timeout=60000)

    threads = []
    for client in clients:
        for i in range(num_of_concurrent_threads):
            thread = Thread(
                target=open_connection_to_all_nodes_and_block_it,
                args=(client,),
                name=f"Warmup-{i}",
            )
            threads.append(thread)
            thread.start()

        for thread in threads:
            thread.join()

    print("Warm-up completed. All connections established.")


def create_clients(client_count, action):
    return [action() for _ in range(client_count)]


def run_clients(
    clients,
    client_name,
    total_commands,
    num_of_concurrent_threads,
    data_size,
    is_cluster,
):
    now = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(
        f"Starting {client_name} data size: {data_size} concurrency:"
        f"{num_of_concurrent_threads} client count: {len(clients)} {now}"
    )
    action_latencies = {
        ChosenAction.GET_NON_EXISTING: list(),
        ChosenAction.GET_EXISTING: list(),
        ChosenAction.SET: list(),
    }
    time = create_and_run_concurrent_threads(
        clients, total_commands, num_of_concurrent_threads, data_size, action_latencies
    )
    tps = int(started_tasks_counter / time)
    get_non_existing_latencies = action_latencies[ChosenAction.GET_NON_EXISTING]
    get_non_existing_latency_results = latency_results(
        "get_non_existing", get_non_existing_latencies
    )

    get_existing_latencies = action_latencies[ChosenAction.GET_EXISTING]
    get_existing_latency_results = latency_results(
        "get_existing", get_existing_latencies
    )

    set_latencies = action_latencies[ChosenAction.SET]
    set_results = latency_results("set", set_latencies)

    json_res = {
        **{
            "client": client_name,
            "num_of_tasks": num_of_concurrent_threads,
            "data_size": data_size,
            "tps": tps,
            "client_count": len(clients),
            "is_cluster": is_cluster,
        },
        **get_existing_latency_results,
        **get_non_existing_latency_results,
        **set_results,
    }

    bench_json_results.append(json_res)


def main(
    total_commands,
    num_of_concurrent_threads,
    data_size,
    clients_to_run,
    host,
    client_count,
    use_tls,
    is_cluster,
):
    if clients_to_run == "all":
        client_class = redispy.RedisCluster if is_cluster else redispy.Redis
        clients = create_clients(
            client_count,
            lambda: client_class(
                host=host,
                port=port,
                ssl=use_tls,
                max_connections=num_of_concurrent_threads,
            ),
        )

        warmup_connections_with_threads(clients, num_of_concurrent_threads, is_cluster)

        run_clients(
            clients,
            "redispy",
            total_commands,
            num_of_concurrent_threads,
            data_size,
            is_cluster,
        )

        for client in clients:
            client.close()

    if clients_to_run == "all" or clients_to_run == "glide":
        client_class = GlideClusterClient if is_cluster else GlideClient
        config = (
            GlideClusterClientConfiguration(
                [NodeAddress(host=host, port=port)],
                use_tls=use_tls,
                request_timeout=1000,
            )
            if is_cluster
            else GlideClientConfiguration(
                [NodeAddress(host=host, port=port)],
                use_tls=use_tls,
                request_timeout=1000,
            )
        )
        clients = create_clients(
            client_count,
            lambda: client_class.create(config),
        )
        run_clients(
            clients,
            "glide",
            total_commands,
            num_of_concurrent_threads,
            data_size,
            is_cluster,
        )


if __name__ == "__main__":
    concurrent_threads = args.concurrentTasks
    data_size = int(args.dataSize)
    clients_to_run = args.clients
    client_count = args.clientCount
    host = args.host
    use_tls = args.tls
    port = args.port
    is_cluster = args.clusterModeEnabled

    # Setting the internal logger to log every log that has a level of info and above,
    # and save the logs to a file with the name of the results file.
    Logger.set_logger_config(LogLevel.INFO, Path(args.resultsFile).stem)

    product_of_arguments = [
        (data_size, int(num_of_concurrent_threads), int(number_of_clients))
        for num_of_concurrent_threads in concurrent_threads
        for number_of_clients in client_count
        if int(number_of_clients) <= int(num_of_concurrent_threads)
    ]

    for data_size, num_of_concurrent_threads, number_of_clients in product_of_arguments:
        iterations = (
            1000 if args.minimal else number_of_iterations(num_of_concurrent_threads)
        )
        main(
            iterations,
            num_of_concurrent_threads,
            data_size,
            clients_to_run,
            host,
            number_of_clients,
            use_tls,
            is_cluster,
        )

    process_results(bench_json_results, args.resultsFile)
