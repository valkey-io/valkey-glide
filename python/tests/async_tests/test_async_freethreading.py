# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Free-threading stress tests for the asynchronous GLIDE client.

Tests exercise concurrent access patterns that validate thread safety of
_pending_futures and response parsing under free-threaded Python builds.
"""

import sys
import uuid

import anyio
import pytest
from glide.glide_client import GlideClient
from glide_shared.config import (
    GlideClientConfiguration,
    NodeAddress,
    ProtocolVersion,
)


def is_free_threaded() -> bool:
    if hasattr(sys, "_is_gil_enabled"):
        return not sys._is_gil_enabled()
    return False


def _get_config(request) -> GlideClientConfiguration:
    host = request.config.getoption("--host", default="localhost")
    port = int(request.config.getoption("--port", default="6379"))
    return GlideClientConfiguration(
        addresses=[NodeAddress(host, port)],
        request_timeout=5000,
        protocol=ProtocolVersion.RESP3,
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

    async def test_info_free_threading_status(self):
        """Report free-threading status (always passes)."""
        ft = is_free_threaded()
        print(f"\n  Free-threaded Python: {ft}")
        print(f"  Python version: {sys.version}")
        if hasattr(sys, "_is_gil_enabled"):
            print(f"  GIL enabled: {sys._is_gil_enabled()}")
