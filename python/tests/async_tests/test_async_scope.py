# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Isolated Execution Scopes (Python async).
Mirrors Java IsolatedScopeIntegrationTest and sync test_sync_scope.py
for cross-language parity.

Requires a running Valkey server (standalone).
"""

import asyncio
import time
import uuid
from pathlib import Path

import pytest
import pytest_asyncio
from glide import (
    AdvancedGlideClientConfiguration,
    ConnectionError,
    GlideClient,
    GlideClientConfiguration,
    NodeAddress,
    RequestError,
    TimeoutError,
    TlsAdvancedConfiguration,
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


# Any certificate serves as root material here: the client is lazy and never
# dials, so the bytes are stored, not verified. Read the committed glide-core
# fixture rather than utils/tls_crts/, which exists only after a TLS server start.
_ANY_ROOT_CERT = (
    Path(__file__).resolve().parents[3]
    / "glide-core"
    / "src"
    / "tls_reload"
    / "test_data"
    / "cert_a.pem"
)

# The scope pool holds at most this many connections per client (glide-core
# ScopePoolConfig::default().max_total). Not configurable from Python.
_SCOPE_POOL_MAX_TOTAL = 64


class TestAsyncIsolatedScopeAcquireFailsFast:
    """An acquire stops as soon as its outcome is known: the error class names the
    cause, and a cause that waiting cannot fix is reported without waiting."""

    async def test_exhausted_pool_times_out_naming_the_cause(self, client):
        held = [await client.scoped_connection() for _ in range(_SCOPE_POOL_MAX_TOTAL)]
        try:
            started = time.monotonic()
            with pytest.raises(TimeoutError, match="pool exhausted"):
                await client.scoped_connection(timeout=0.5)
            elapsed = time.monotonic() - started
            assert 0.4 <= elapsed < 1.0, elapsed
        finally:
            for scope in held:
                await scope.close()

    async def test_many_waiters_time_out_together(self, client):
        # Every waiter's deadline is core's, so the count of concurrent waiters
        # does not stretch any one of them.
        held = [await client.scoped_connection() for _ in range(_SCOPE_POOL_MAX_TOTAL)]
        try:
            started = time.monotonic()
            outcomes = await asyncio.gather(
                *(
                    client.scoped_connection(timeout=0.5)
                    for _ in range(_SCOPE_POOL_MAX_TOTAL)
                ),
                return_exceptions=True,
            )
            elapsed = time.monotonic() - started
            assert all(isinstance(o, TimeoutError) for o in outcomes), outcomes
            assert all("pool exhausted" in str(o) for o in outcomes), outcomes
            assert (
                0.4 <= elapsed < 1.5
            ), f"{_SCOPE_POOL_MAX_TOTAL} 0.5s waits took {elapsed:.2f}s"
        finally:
            for scope in held:
                await scope.close()

    async def test_closing_the_client_ends_a_waiting_acquire(self):
        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()], request_timeout=5000
        )
        client = await GlideClient.create(config)
        held = [await client.scoped_connection() for _ in range(_SCOPE_POOL_MAX_TOTAL)]

        async def _close_soon():
            await asyncio.sleep(0.2)
            await client.aclose()

        closer = asyncio.create_task(_close_soon())
        started = time.monotonic()
        with pytest.raises(ConnectionError):
            await client.scoped_connection(timeout=5.0)
        elapsed = time.monotonic() - started
        assert elapsed < 2.0, f"waited {elapsed:.2f}s past the close for a 5s deadline"
        await closer
        del held

    async def test_abandoned_acquire_returns_its_slot(self, client):
        # A caller that stops waiting must not keep the slot the core later
        # grants it.
        held = [await client.scoped_connection() for _ in range(_SCOPE_POOL_MAX_TOTAL)]
        waiter = asyncio.create_task(client.scoped_connection(timeout=5.0))
        await asyncio.sleep(0.2)
        waiter.cancel()
        with pytest.raises(asyncio.CancelledError):
            await waiter

        # The abandoned acquire is still waiting in the core; freeing the pool
        # lets it win a slot, which the binding must then release on its behalf.
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

    async def test_lazy_mtls_parent_fails_without_waiting(self):
        # A lazily connected client with TLS material never dials, so there is
        # nothing for a scoped connection to inherit; no amount of waiting fixes
        # that, so the acquire must not run out its 5 s deadline.
        config = GlideClientConfiguration(
            addresses=[NodeAddress("127.0.0.1", 1)],
            use_tls=True,
            lazy_connect=True,
            request_timeout=5000,
            advanced_config=AdvancedGlideClientConfiguration(
                tls_config=TlsAdvancedConfiguration(
                    root_pem_cacerts=_ANY_ROOT_CERT.read_bytes()
                )
            ),
        )
        client = await GlideClient.create(config)
        try:
            started = time.monotonic()
            with pytest.raises(RequestError, match="certificate material"):
                await client.scoped_connection(timeout=5.0)
            assert time.monotonic() - started < 2.0
        finally:
            await client.aclose()
