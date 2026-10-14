# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Scope acquire under every supported async backend.

Runs under the ``anyio_backend`` fixture (asyncio by default; add
``--async-backend=trio`` to include trio), unlike ``test_async_scope.py``,
which is pinned to pytest-asyncio.
"""

import time

import anyio
import pytest
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
