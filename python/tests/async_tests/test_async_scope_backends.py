# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Scope acquire and commands under every supported async backend.

Runs under the ``anyio_backend`` fixture (asyncio by default; add
``--async-backend=trio`` to include trio), unlike ``test_async_scope.py``,
which is pinned to pytest-asyncio.
"""

import threading
import time
import uuid

import anyio
import pytest
from glide import AsyncIsolatedScope
from glide_shared.exceptions import TimeoutError

from tests.async_tests.conftest import create_client

# glide-core ScopePoolConfig::default().max_total; not configurable from Python.
_SCOPE_POOL_MAX_TOTAL = 64


@pytest.mark.anyio
class TestScopeAcquireBackends:
    async def test_acquire_and_release_round_trip(self, request):
        client = await create_client(request, cluster_mode=False)
        try:
            scope = await client.scoped_connection(timeout=5.0)
            await scope.close()
        finally:
            await client.close()

    async def test_exhausted_pool_times_out_on_the_core_deadline(self, request):
        client = await create_client(request, cluster_mode=False)
        try:
            held = [
                await client.scoped_connection() for _ in range(_SCOPE_POOL_MAX_TOTAL)
            ]
            try:
                started = time.monotonic()
                with pytest.raises(TimeoutError, match="pool exhausted"):
                    await client.scoped_connection(timeout=0.5)
                elapsed = time.monotonic() - started
                assert 0.4 <= elapsed < 1.5, elapsed
            finally:
                for scope in held:
                    await scope.close()
        finally:
            await client.close()

    async def test_cancelled_acquire_returns_its_slot(self, request):
        # A caller that stops waiting must not keep the slot core later grants it.
        client = await create_client(request, cluster_mode=False)
        try:
            held = [
                await client.scoped_connection() for _ in range(_SCOPE_POOL_MAX_TOTAL)
            ]

            async def _abandon_after(delay: float) -> None:
                with anyio.move_on_after(delay):
                    await client.scoped_connection(timeout=5.0)

            await _abandon_after(0.2)

            # The abandoned acquire is still waiting in core; freeing the pool
            # lets it win a slot, which the binding must release on its behalf.
            for scope in held:
                await scope.close()

            refilled = []
            try:
                for i in range(_SCOPE_POOL_MAX_TOTAL):
                    try:
                        refilled.append(await client.scoped_connection(timeout=2.0))
                    except TimeoutError:
                        pytest.fail(
                            f"slot {i + 1} of {_SCOPE_POOL_MAX_TOTAL} unavailable "
                            "after an abandoned acquire"
                        )
            finally:
                for scope in refilled:
                    await scope.close()
        finally:
            await client.close()


@pytest.mark.anyio
class TestScopeCommandBackends:
    async def test_commands_round_trip(self, request):
        client = await create_client(request, cluster_mode=False)
        try:
            key = f"scope-backends-{uuid.uuid4().hex[:8]}"
            async with await client.scoped_connection() as scope:
                assert await scope.ping() == "PONG"
                assert await scope.set(key, "value") == "OK"
                assert await scope.get(key) == "value"
        finally:
            await client.close()

    async def test_release_ends_cancelled_blocking_command(self, request, monkeypatch):
        # Cancelling abandons the worker thread; close() must still end its native call.
        returned = threading.Event()
        execute_sync = AsyncIsolatedScope._execute_sync

        def _tracked(self, payload):
            try:
                return execute_sync(self, payload)
            finally:
                returned.set()

        monkeypatch.setattr(AsyncIsolatedScope, "_execute_sync", _tracked)
        client = await create_client(request, cluster_mode=False)
        try:
            key = f"scope-backends-blpop-{uuid.uuid4().hex[:8]}"
            scope = await client.scoped_connection()
            # Bounded past the 2s check so a failed release can't pin the thread forever.
            with anyio.move_on_after(0.2):
                await scope.execute_command("BLPOP", key, "10")
            assert not returned.is_set()

            await scope.close()
            assert await anyio.to_thread.run_sync(returned.wait, 2)
        finally:
            await client.close()

    async def test_cancelled_command_frees_its_result(self, request):
        freed = threading.Event()

        class _CountingLib:
            # cffi lib attributes can't be patched, so wrap the scope's lib.
            def __init__(self, lib):
                self._lib = lib

            def __getattr__(self, name):
                return getattr(self._lib, name)

            def free_command_result(self, ptr):
                self._lib.free_command_result(ptr)
                freed.set()

        client = await create_client(request, cluster_mode=False)
        try:
            key = f"scope-backends-free-{uuid.uuid4().hex[:8]}"
            async with await client.scoped_connection() as scope:
                scope._lib = _CountingLib(scope._lib)
                # BLPOP times out server-side after 1s, so the abandoned call
                # still gets a result back that something has to free.
                with anyio.move_on_after(0.2):
                    await scope.execute_command("BLPOP", key, "1")
                assert await anyio.to_thread.run_sync(freed.wait, 5)
        finally:
            await client.close()
