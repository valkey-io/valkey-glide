# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Benchmark: Async pooling throughput test.

Run manually (not part of CI test suite):
    cd python && .env/bin/pytest ../benchmarks/python/pool_async_benchmark.py -v -s --host localhost --port 6379

Expected results (varies by hardware):
- Pool borrow: ~2ms/op vs fresh client: ~3-5ms/op
- Concurrency scaling: near-linear up to pool max_size

Measures throughput of SET/GET operations under increasing concurrency
to demonstrate parallelism gains from async pool usage.

Run with: pytest tests/async_tests/test_async_pool_benchmark.py -v -s
"""

import asyncio
import sys
import time

import pytest
import pytest_asyncio
from glide import (
    AsyncClientPool,
    GlideClient,
    GlideClientConfiguration,
    PoolConfig,
)
from glide_shared.config import NodeAddress


def _get_standalone_address():
    return NodeAddress("localhost", 6379)


pytestmark = pytest.mark.asyncio


def get_config() -> GlideClientConfiguration:
    return GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=10000,
    )


class TestPoolBenchmark:
    """Throughput benchmarks for async pool."""

    @pytest_asyncio.fixture
    async def pool(self):
        config = get_config()
        p = AsyncClientPool(config, PoolConfig(max_size=8, min_idle=4))
        await asyncio.sleep(4)
        yield p
        p.close()

    async def test_benchmark_single_vs_multi_thread(self, pool):
        """
        Compare throughput: 1 concurrent task vs N tasks with pooling.

        Async concurrency should scale well with increasing task count
        since tasks cooperatively share the event loop.
        """
        ops_per_task = 500
        is_ft = hasattr(sys, "_is_gil_enabled") and not sys._is_gil_enabled()

        print(f"\n{'='*60}")
        print("  Async Pool Throughput Benchmark")
        print(f"  Free-threaded: {is_ft}")
        print(f"  Python: {sys.version.split()[0]}")
        print(f"  Ops per task: {ops_per_task}")
        print(f"{'='*60}\n")

        results = {}

        for num_tasks in [1, 2, 4, 8]:
            errors = []

            async def worker(task_id, ops):
                for i in range(ops):
                    try:
                        async with pool.borrow() as client:
                            key = f"bench-{task_id}-{i}"
                            await client.set(key, "x" * 100)
                            await client.get(key)
                            await client.delete([key])
                    except Exception as e:
                        errors.append(str(e))
                        return

            start = time.perf_counter()
            tasks = [
                asyncio.create_task(worker(t, ops_per_task)) for t in range(num_tasks)
            ]
            await asyncio.gather(*tasks)
            elapsed = time.perf_counter() - start

            total_ops = num_tasks * ops_per_task * 3  # SET + GET + DEL
            throughput = total_ops / elapsed
            results[num_tasks] = throughput

            print(
                f"  {num_tasks:2d} tasks: "
                f"{throughput:,.0f} ops/s  "
                f"({elapsed:.2f}s, {total_ops} ops)"
                f"{'  ⚠️ errors: ' + str(len(errors)) if errors else ''}"
            )

        print("\n  Scaling:")
        baseline = results[1]
        for n, tput in results.items():
            print(f"    {n} tasks: {tput/baseline:.2f}x vs single-task")

        print(f"\n{'='*60}\n")

    async def test_benchmark_pool_vs_fresh_client(self):
        """
        Compare: borrowing from pool vs creating fresh client per operation.
        Pool should always be faster regardless of concurrency model.
        """
        config = get_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=4, min_idle=2))
        await asyncio.sleep(3)

        iterations = 10

        print(f"\n{'='*60}")
        print("  Async Pool vs Fresh Client Benchmark")
        print(f"  Iterations: {iterations}")
        print(f"{'='*60}\n")

        # Scenario A: Fresh client per operation
        start = time.perf_counter()
        for i in range(iterations):
            client = await GlideClient.create(config)
            key = f"fresh-{i}"
            await client.set(key, "hello")
            await client.get(key)
            await client.delete([key])
            await client.close()
        fresh_elapsed = time.perf_counter() - start

        # Scenario B: Pool borrow per operation
        start = time.perf_counter()
        for i in range(iterations):
            async with pool.borrow() as client:
                key = f"pooled-{i}"
                await client.set(key, "hello")
                await client.get(key)
                await client.delete([key])
        pool_elapsed = time.perf_counter() - start

        pool.close()

        fresh_per_op = fresh_elapsed / iterations * 1000
        pool_per_op = pool_elapsed / iterations * 1000
        speedup = fresh_elapsed / pool_elapsed

        print(f"  Fresh client per op: {fresh_per_op:.2f} ms/op")
        print(f"  Pool borrow per op:  {pool_per_op:.2f} ms/op")
        print(f"  Speedup:             {speedup:.1f}x")
        print(f"{'='*60}\n")

        # Note: On free-threaded builds or slow CI, pool overhead may exceed
        # fresh client performance due to GIL-free contention and state reset cost.
        # This is informational — not a correctness assertion.
        if speedup < 1.0:
            print(f"  ⚠️  Pool was slower than fresh client: {speedup:.2f}x")
