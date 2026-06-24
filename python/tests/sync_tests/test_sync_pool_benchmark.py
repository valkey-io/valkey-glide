# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Benchmark: Free-threading + Pooling throughput test.

Measures throughput of SET/GET operations under increasing thread counts
to demonstrate parallelism gains from pool + free-threading.

On GIL builds: threads add overhead (context switching) without parallelism.
On free-threaded builds: threads enable true parallelism, throughput scales.

Run with: pytest tests/sync_tests/test_sync_pool_benchmark.py -v -s
"""

import sys
import threading
import time


import pytest
from glide_shared.config import GlideClientConfiguration, NodeAddress
from glide_sync import GlideClient
from glide_sync.client_pool import ClientPool, PoolConfig


def get_config(request) -> GlideClientConfiguration:
    host = request.config.getoption("--host", default="localhost")
    port = int(request.config.getoption("--port", default="6379"))
    return GlideClientConfiguration(
        addresses=[NodeAddress(host, port)],
        request_timeout=5000,
    )


class TestPoolBenchmark:
    """Throughput benchmarks for pool + free-threading."""

    @pytest.fixture
    def pool(self, request):
        config = get_config(request)
        p = ClientPool(config, PoolConfig(max_size=8, min_idle=4))
        time.sleep(4)
        yield p
        p.close()

    def test_benchmark_single_vs_multi_thread(self, pool):
        """
        Compare throughput: 1 thread vs N threads with pooling.

        On GIL builds: multi-thread adds overhead, ~1x or worse.
        On free-threaded builds: near-linear scaling expected.
        """
        ops_per_thread = 500
        is_ft = hasattr(sys, "_is_gil_enabled") and not sys._is_gil_enabled()

        print(f"\n{'='*60}")
        print("  Pool + Free-threading Throughput Benchmark")
        print(f"  Free-threaded: {is_ft}")
        print(f"  Python: {sys.version.split()[0]}")
        print(f"  Ops per thread: {ops_per_thread}")
        print(f"{'='*60}\n")

        results = {}

        for num_threads in [1, 2, 4, 8]:
            errors = []

            def worker(thread_id, ops):
                for i in range(ops):
                    try:
                        with pool.borrow() as client:
                            key = f"bench-{thread_id}-{i}"
                            client.set(key, "x" * 100)
                            client.get(key)
                            client.delete([key])
                    except Exception as e:
                        errors.append(str(e))
                        return

            start = time.perf_counter()
            threads = [
                threading.Thread(target=worker, args=(t, ops_per_thread))
                for t in range(num_threads)
            ]
            for t in threads:
                t.start()
            for t in threads:
                t.join(timeout=60)
            elapsed = time.perf_counter() - start

            total_ops = num_threads * ops_per_thread * 3  # SET + GET + DEL
            throughput = total_ops / elapsed
            results[num_threads] = throughput

            print(
                f"  {num_threads:2d} threads: "
                f"{throughput:,.0f} ops/s  "
                f"({elapsed:.2f}s, {total_ops} ops)"
                f"{'  ⚠️ errors: ' + str(len(errors)) if errors else ''}"
            )

        print("\n  Scaling:")
        baseline = results[1]
        for n, tput in results.items():
            print(f"    {n} threads: {tput/baseline:.2f}x vs single-thread")

        print("\n  Note: On GIL builds, expect ~1x scaling (GIL serializes).")
        print("  On free-threaded (3.13t+), expect near-linear scaling.")
        print(f"{'='*60}\n")

    def test_benchmark_pool_vs_fresh_client(self, request):
        """
        Compare: borrowing from pool vs creating fresh client per operation.
        Pool should always be faster regardless of GIL/free-threading.
        """
        config = get_config(request)
        pool = ClientPool(config, PoolConfig(max_size=4, min_idle=2))
        time.sleep(3)

        iterations = 50

        print(f"\n{'='*60}")
        print("  Pool vs Fresh Client Benchmark")
        print(f"  Iterations: {iterations}")
        print(f"{'='*60}\n")

        # Scenario A: Fresh client per operation
        start = time.perf_counter()
        for i in range(iterations):
            client = GlideClient.create(config)
            key = f"fresh-{i}"
            client.set(key, "hello")
            client.get(key)
            client.delete([key])
            client.close()
        fresh_elapsed = time.perf_counter() - start

        # Scenario B: Pool borrow per operation
        start = time.perf_counter()
        for i in range(iterations):
            with pool.borrow() as client:
                key = f"pooled-{i}"
                client.set(key, "hello")
                client.get(key)
                client.delete([key])
        pool_elapsed = time.perf_counter() - start

        pool.close()

        fresh_per_op = fresh_elapsed / iterations * 1000
        pool_per_op = pool_elapsed / iterations * 1000
        speedup = fresh_elapsed / pool_elapsed

        print(f"  Fresh client per op: {fresh_per_op:.2f} ms/op")
        print(f"  Pool borrow per op:  {pool_per_op:.2f} ms/op")
        print(f"  Speedup:             {speedup:.1f}x")
        print(f"{'='*60}\n")

        # Pool should always be faster
        assert speedup > 1.0, (
            f"Pool should be faster than fresh client. "
            f"Got {speedup:.2f}x (pool: {pool_per_op:.2f}ms, fresh: {fresh_per_op:.2f}ms)"
        )
