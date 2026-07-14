# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Feature 2: Isolated Execution Scopes (Python async).
Mirrors Java IsolatedScopeIntegrationTest and sync test_sync_scope.py
for cross-language parity.

Requires a running Valkey server (standalone).
"""

import asyncio
import uuid

import pytest
import pytest_asyncio
from glide import (
    GlideClient,
    GlideClientConfiguration,
)

from tests.utils.utils import get_standalone_address as _get_standalone_address

pytestmark = pytest.mark.asyncio


@pytest_asyncio.fixture
async def client():
    """Create a connected async GlideClient."""
    config = GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=5000,
    )
    c = await GlideClient.create(config)
    yield c
    await c.aclose()


class TestAsyncIsolatedScopeBasic:
    """Basic scope lifecycle tests."""

    async def test_scope_acquire_ping_release(self, client):
        """Acquire a scope, execute PING, release it."""
        async with await client.scoped_connection() as scope:
            assert not scope.is_released
            result = await scope.ping()
            assert result == "PONG" or result == "OK"
        assert scope.is_released

    async def test_scope_get_set(self, client):
        """SET and GET on a scoped connection."""
        key = f"async-scope-test-{uuid.uuid4().hex[:8]}"
        async with await client.scoped_connection() as scope:
            await scope.set(key, "hello")
            val = await scope.get(key)
            assert val == "hello"
        await client.delete([key])

    async def test_scope_returns_none_for_missing_key(self, client):
        """GET on non-existent key returns None."""
        async with await client.scoped_connection() as scope:
            val = await scope.get(f"nonexistent-{uuid.uuid4().hex}")
            assert val is None

    async def test_scope_close_is_idempotent(self, client):
        """Calling close() multiple times is safe."""
        scope = await client.scoped_connection()
        await scope.ping()
        await scope.close()
        await scope.close()  # Should not raise
        assert scope.is_released

    async def test_scope_raises_after_release(self, client):
        """Commands on a released scope raise RuntimeError."""
        scope = await client.scoped_connection()
        await scope.close()
        with pytest.raises(RuntimeError, match="released"):
            await scope.ping()


class TestAsyncIsolatedScopeOCC:
    """WATCH/MULTI/EXEC (Optimistic Concurrency Control) tests."""

    async def test_watch_multi_exec_success(self, client):
        """Successful WATCH/MULTI/EXEC transaction."""
        key = f"async-occ-{uuid.uuid4().hex[:8]}"
        await client.set(key, "0")

        async with await client.scoped_connection() as scope:
            await scope.watch(key)
            val = await scope.get(key)
            assert val == "0"
            await scope.multi()
            await scope.set(key, "1")
            result = await scope.exec()
            assert result is not None

        assert await client.get(key) == b"1"
        await client.delete([key])

    async def test_watch_conflict_aborts_exec(self, client):
        """EXEC aborts when a watched key is modified externally."""
        key = f"async-occ-conflict-{uuid.uuid4().hex[:8]}"
        await client.set(key, "original")

        async with await client.scoped_connection() as scope:
            await scope.watch(key)
            await scope.get(key)

            # Modify externally via the main client
            await client.set(key, "modified-externally")

            await scope.multi()
            await scope.set(key, "from-scope")
            result = await scope.exec()
            assert result is None or result == "None"

        assert await client.get(key) == b"modified-externally"
        await client.delete([key])

    async def test_occ_concurrent_increment(self, client):
        """
        Multiple async tasks increment a counter using WATCH/MULTI/EXEC.
        OCC ensures the final value is exactly correct.
        """
        key = f"async-occ-counter-{uuid.uuid4().hex[:8]}"
        await client.set(key, "0")

        num_tasks = 4
        increments_per_task = 10
        expected_final = num_tasks * increments_per_task
        errors = []

        async def worker():
            try:
                for _ in range(increments_per_task):
                    committed = False
                    while not committed:
                        async with await client.scoped_connection() as scope:
                            await scope.watch(key)
                            val = await scope.get(key)
                            current = int(val) if val else 0
                            await scope.multi()
                            await scope.set(key, str(current + 1))
                            result = await scope.exec()
                            if result is not None and result != "None":
                                committed = True
            except Exception as e:
                errors.append(e)

        tasks = [asyncio.create_task(worker()) for _ in range(num_tasks)]
        await asyncio.gather(*tasks)

        assert not errors, f"Worker errors: {errors}"
        final_val = await client.get(key)
        assert int(final_val) == expected_final
        await client.delete([key])


class TestAsyncIsolatedScopePoolReuse:
    """Scope pool connection reuse tests."""

    async def test_lifo_reuse(self, client):
        """Scopes are reused (same scope_id returned after release)."""
        scope1 = await client.scoped_connection()
        id1 = scope1.scope_id
        await scope1.ping()
        await scope1.close()

        await asyncio.sleep(0.1)

        scope2 = await client.scoped_connection()
        id2 = scope2.scope_id
        await scope2.close()

        assert id1 == id2

    async def test_multiple_concurrent_scopes(self, client):
        """Multiple scopes can be held simultaneously."""
        scope1 = await client.scoped_connection()
        scope2 = await client.scoped_connection()

        assert scope1.scope_id != scope2.scope_id

        await scope1.ping()
        await scope2.ping()

        await scope1.close()
        await scope2.close()
