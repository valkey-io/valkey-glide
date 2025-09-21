import argparse
import json
import math
import random
from enum import Enum
from statistics import mean
from typing import List

import numpy as np


class ChosenAction(Enum):
    GET_NON_EXISTING = 1
    GET_EXISTING = 2
    SET = 3


PORT = 6379
PROB_GET = 0.8
PROB_GET_EXISTING_KEY = 0.8
SIZE_GET_KEYSPACE = 3750000  # 3.75 million
SIZE_SET_KEYSPACE = 3000000  # 3 million


def create_argument_parser():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--resultsFile",
        help="Where to write the results file",
        required=False,
        default="../results/python-results.json",
    )
    parser.add_argument(
        "--dataSize", help="Size of data to set", required=False, default="100"
    )
    parser.add_argument(
        "--clients", help="Which clients should run", required=False, default="all"
    )
    parser.add_argument(
        "--host", help="What host to target", required=False, default="localhost"
    )
    parser.add_argument(
        "--clientCount",
        help="Number of clients to run concurrently",
        nargs="+",
        required=False,
        default=("1"),
    )
    parser.add_argument(
        "--tls",
        help="Should benchmark a TLS server",
        action="store_true",
        required=False,
        default=False,
    )
    parser.add_argument(
        "--clusterModeEnabled",
        help="Should benchmark a cluster mode enabled cluster",
        action="store_true",
        required=False,
        default=False,
    )
    parser.add_argument(
        "--port",
        default=PORT,
        type=int,
        required=False,
        help="Which port to connect to, defaults to `%(default)s`",
    )
    parser.add_argument(
        "--minimal", help="Should run a minimal benchmark", action="store_true"
    )
    parser.add_argument(
        "--concurrentTasks",
        help="List of number of concurrent tasks to run for async python/ concurrent threads to run for sync python",
        nargs="+",
        required=False,
        default=("1", "10", "100", "1000"),
    )

    return parser


def truncate_decimal(number: float, digits: int = 3) -> float:
    stepper = 10**digits
    return math.floor(number * stepper) / stepper


def generate_value(size):
    return str("0" * size)


def generate_key_set():
    return str(random.randint(1, SIZE_SET_KEYSPACE + 1))


def generate_key_get():
    return str(random.randint(SIZE_SET_KEYSPACE, SIZE_GET_KEYSPACE + 1))


def choose_action():
    if random.random() > PROB_GET:
        return ChosenAction.SET
    if random.random() > PROB_GET_EXISTING_KEY:
        return ChosenAction.GET_NON_EXISTING
    return ChosenAction.GET_EXISTING


def calculate_latency(latency_list, percentile):
    return round(np.percentile(np.array(latency_list), percentile), 4)


def latency_results(prefix, latencies):
    result = {}
    result[prefix + "_p50_latency"] = calculate_latency(latencies, 50)
    result[prefix + "_p90_latency"] = calculate_latency(latencies, 90)
    result[prefix + "_p99_latency"] = calculate_latency(latencies, 99)
    result[prefix + "_average_latency"] = truncate_decimal(mean(latencies))
    result[prefix + "_std_dev"] = truncate_decimal(np.std(latencies))
    return result


def number_of_iterations(num_of_concurrent_tasks):
    return min(max(100000, num_of_concurrent_tasks * 10000), 5000000)


def process_results(bench_json_results: List, results_file: str):
    with open(results_file, "w+") as f:
        json.dump(bench_json_results, f)
