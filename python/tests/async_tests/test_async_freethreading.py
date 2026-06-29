# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Free-threading stress tests for the asynchronous GLIDE client.

Tests exercise concurrent access patterns that validate thread safety of
_pending_futures and response parsing under free-threaded Python builds.
"""

import asyncio
import sys
import uuid

import anyio
import pytest
from glide import (
    AsyncClientPool,
    GlideClientConfiguration,
    PoolConfig,
)
from glide.glide_client import GlideClient
from glide_shared.config import (
    NodeAddress,
    ProtocolVersion,
)

from tests.utils.utils import get_standalone_address as _get_standalone_address

pytestmark = pytest.mark.asyncio


def is_free_threaded() -> bool:
    if hasattr(sys, "_is_gil_enabled"):
        return not sys._is_gil_enabled()
    return False


def _get_config(request=None) -> GlideClientConfiguration:
    if request is not None:
        host = request.config.getoption("--host", default="localhost")
        port = int(request.config.getoption("--port", default="6379"))
        return GlideClientConfiguration(
            addresses=[NodeAddress(host, port)],
            request_timeout=5000,
            protocol=ProtocolVersion.RESP3,
        )
    return GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=5000,
        protocol=ProtocolVersion.RESP3,
    )


def get_standalone_config() -> GlideClientConfiguration:
    return GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=5000,
    )


@pytest.mark.anyio
class TestAsyncFreeThreading:
    """Stress tests for async client free-threading safety."""

    async def test_concurrent_commands_single_client(self, request):
        """
        Many concurrent commands on the same client — exercises _pending_futures
        dict under high concurrency.
        """
        config = _get_config(request)
        client = await GlideClient.create(config)
        try:
            num_tasks = 100
            errors = []

            async def worker(task_id):
                key = f"async-ft-{task_id}-{uuid.uuid4().hex[:8]}"
                expected = f"val-{task_id}"
                try:
                    await client.set(key, expected)
                    result = await client.get(key)
                    actual = result.decode() if isinstance(result, bytes) else result
                    if actual != expected:
                        errors.append(
                            f"Task {task_id}: expected '{expected}', got '{actual}'"
                        )
                    await client.delete([key])
                except Exception as e:
                    errors.append(f"Task {task_id}: {type(e).__name__}: {e}")

            async with anyio.create_task_group() as tg:
                for i in range(num_tasks):
                    tg.start_soon(worker, i)

            assert not errors, "Errors:\n" + "\n".join(errors[:10])
        finally:
            await client.close()

    async def test_high_concurrency_pipeline(self, request):
        """
        Fire 500 commands without sequential awaiting — tests _pending_futures
        map under high entry count.
        """
        config = _get_config(request)
        client = await GlideClient.create(config)
        try:
            num_ops = 500
            keys = [f"async-pipe-{i}-{uuid.uuid4().hex[:6]}" for i in range(num_ops)]
            results = [None] * num_ops

            # Fire all SETs concurrently
            async with anyio.create_task_group() as tg:
                for i, k in enumerate(keys):
                    tg.start_soon(client.set, k, f"v{i}")

            # Fire all GETs concurrently, collect results
            async def get_and_store(idx, key):
                results[idx] = await client.get(key)

            async with anyio.create_task_group() as tg:
                for i, k in enumerate(keys):
                    tg.start_soon(get_and_store, i, k)

            # Verify correctness (no response cross-contamination)
            for i, result in enumerate(results):
                val = result.decode() if isinstance(result, bytes) else result
                assert val == f"v{i}", f"Key {keys[i]}: expected 'v{i}', got '{val}'"

            await client.delete(keys)
        finally:
            await client.close()

    async def test_multiple_clients_concurrent(self, request):
        """
        Multiple async clients operating concurrently — exercises the shared
        pipe reader's _client_registry lookups under concurrent access.
        """
        config = _get_config(request)
        num_clients = 4
        ops_per_client = 50
        errors = []

        clients = [await GlideClient.create(config) for _ in range(num_clients)]
        try:

            async def client_worker(client_idx, client):
                for i in range(ops_per_client):
                    key = f"multi-client-{client_idx}-{i}"
                    expected = f"c{client_idx}-{i}"
                    try:
                        await client.set(key, expected)
                        result = await client.get(key)
                        actual = (
                            result.decode() if isinstance(result, bytes) else result
                        )
                        if actual != expected:
                            errors.append(
                                f"Client {client_idx} op {i}: expected '{expected}', got '{actual}'"
                            )
                            return
                        await client.delete([key])
                    except Exception as e:
                        errors.append(f"Client {client_idx} op {i}: {e}")
                        return

            async with anyio.create_task_group() as tg:
                for i, c in enumerate(clients):
                    tg.start_soon(client_worker, i, c)

            assert not errors, "Errors:\n" + "\n".join(errors[:10])
        finally:
            for c in clients:
                await c.close()

    async def test_parallel_commands_same_pool(self):
        """
        Many concurrent tasks doing SET/GET cycles through an async pool.
        Validates no response cross-contamination under parallel execution.
        """
        config = get_standalone_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=8, min_idle=4))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        num_tasks = 16
        ops_per_task = 50
        errors = []

        async def worker(task_id):
            for i in range(ops_per_task):
                key = f"ft-stress-{task_id}-{i}-{uuid.uuid4().hex[:8]}"
                expected_value = f"val-{task_id}-{i}"
                try:
                    async with pool.borrow() as client:
                        await client.set(key, expected_value)
                        result = await client.get(key)
                        actual = (
                            result.decode() if isinstance(result, bytes) else result
                        )
                        if actual != expected_value:
                            errors.append(
                                f"Task {task_id} op {i}: expected '{expected_value}', "
                                f"got '{actual}' (RESPONSE CORRUPTION)"
                            )
                            return
                        await client.delete([key])
                except Exception as e:
                    errors.append(f"Task {task_id} op {i}: {type(e).__name__}: {e}")
                    return

        tasks = [asyncio.create_task(worker(t)) for t in range(num_tasks)]
        await asyncio.gather(*tasks)

        pool.close()
        assert not errors, "Free-threading errors:\n" + "\n".join(errors[:10])

    async def test_parallel_pool_acquire_release_storm(self):
        """
        Rapid acquire/release without doing commands — stresses the pool's
        internal state under high concurrency with async tasks.
        """
        config = get_standalone_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=8, min_idle=4))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        num_tasks = 16
        cycles_per_task = 100
        errors = []

        async def worker(task_id):
            for i in range(cycles_per_task):
                try:
                    client_id = await pool.acquire(timeout=10.0)
                    # Minimal hold time — maximizes contention
                    pool.release(client_id)
                except Exception as e:
                    errors.append(f"Task {task_id} cycle {i}: {e}")
                    return

        tasks = [asyncio.create_task(worker(t)) for t in range(num_tasks)]
        await asyncio.gather(*tasks)

        pool.close()
        assert not errors, "Pool contention errors:\n" + "\n".join(errors[:10])

    async def test_response_parser_parallel_invocation(self):
        """
        Multiple tasks calling commands simultaneously — exercises the
        response parser under parallel access.
        """
        config = get_standalone_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=8, min_idle=4))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        num_tasks = 8
        ops_per_task = 100
        errors = []

        async def worker(task_id):
            async with pool.borrow() as client:
                for i in range(ops_per_task):
                    try:
                        key = f"parser-{task_id}-{i}"
                        await client.set(key, str(i))
                        val = await client.get(key)
                        actual = int(val) if val else -1
                        if actual != i:
                            errors.append(f"Task {task_id}: expected {i}, got {actual}")
                            return
                        await client.delete([key])
                    except Exception as e:
                        errors.append(f"Task {task_id} op {i}: {e}")
                        return

        tasks = [asyncio.create_task(worker(t)) for t in range(num_tasks)]
        await asyncio.gather(*tasks)

        pool.close()
        assert not errors, "Parser parallel errors:\n" + "\n".join(errors[:10])

    async def test_concurrent_pool_metrics(self):
        """
        One task hammering metrics while others do acquire/release.
        Validates that concurrent readers don't crash under async concurrency.
        """
        config = get_standalone_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=8, min_idle=4))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        stop_event = asyncio.Event()
        errors = []
        metrics_calls = [0]

        async def metrics_reader():
            while not stop_event.is_set():
                try:
                    m = pool.metrics()
                    assert "idle" in m
                    assert "active" in m
                    assert "total" in m
                    metrics_calls[0] += 1
                    await asyncio.sleep(0)  # Yield to event loop
                except Exception as e:
                    errors.append(f"Metrics reader: {e}")
                    return

        async def pool_user(task_id):
            for _ in range(50):
                try:
                    async with pool.borrow() as client:
                        await client.set(f"metrics-test-{task_id}", "x")
                        await client.get(f"metrics-test-{task_id}")
                        await client.delete([f"metrics-test-{task_id}"])
                except Exception as e:
                    errors.append(f"Pool user {task_id}: {e}")
                    return

        reader_task = asyncio.create_task(metrics_reader())
        worker_tasks = [asyncio.create_task(pool_user(t)) for t in range(4)]

        await asyncio.gather(*worker_tasks)
        stop_event.set()
        await reader_task

        pool.close()
        assert not errors, "Errors:\n" + "\n".join(errors[:10])
        assert metrics_calls[0] > 0, "Metrics reader should have run"

    async def test_info_free_threading_status(self):
        """Report free-threading status (always passes)."""
        ft = is_free_threaded()
        print(f"\n  Free-threaded Python: {ft}")
        print(f"  Python version: {sys.version}")
        if hasattr(sys, "_is_gil_enabled"):
            print(f"  GIL enabled: {sys._is_gil_enabled()}")
