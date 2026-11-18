# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Pool borrow under every supported async backend.

Runs under the ``anyio_backend`` fixture (asyncio by default; add
``--async-backend=trio`` to include trio), unlike ``test_async_pool.py``,
which is pinned to pytest-asyncio.
"""

import uuid

import anyio
import pytest
from glide import (
    AsyncClientPool,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    PoolConfig,
)

from tests.utils.utils import get_standalone_address, require_cluster_addresses


def _config(cluster_mode: bool):
    if cluster_mode:
        return GlideClusterClientConfiguration(addresses=require_cluster_addresses())
    return GlideClientConfiguration(addresses=[get_standalone_address()])


@pytest.mark.anyio
class TestPoolBorrowBackends:
    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_borrow_waits_for_native_client_creation(self, cluster_mode):
        pool = await AsyncClientPool.create(
            _config(cluster_mode), PoolConfig(max_size=1, min_idle=0)
        )
        try:
            key = f"{{pool-backends}}-{uuid.uuid4().hex[:8]}"
            with anyio.fail_after(10):
                async with pool.borrow() as client:
                    await client.set(key, "value")
                    assert await client.get(key) == b"value"
                    await client.delete([key])
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_borrow_waits_for_release(self, cluster_mode):
        pool = await AsyncClientPool.create(
            _config(cluster_mode), PoolConfig(max_size=1, min_idle=0)
        )
        try:
            replies = []

            async def _second_borrower():
                async with pool.borrow(timeout=5.0) as client:
                    replies.append(await client.ping())

            with anyio.fail_after(10):
                held = await pool.acquire()
                async with anyio.create_task_group() as tg:
                    tg.start_soon(_second_borrower)
                    # Let the second borrower reach the backoff loop.
                    await anyio.sleep(0.2)
                    assert replies == []
                    pool.release(held)
            assert replies == [b"PONG"]
        finally:
            pool.close()


@pytest.mark.parametrize("backend", ["asyncio", "trio"])
@pytest.mark.parametrize("borrow_in_first_run", [False, True])
def test_borrow_in_later_event_loop_run(request, backend, borrow_in_first_run):
    # A pipe reader dies with its event-loop run, so a pooled client borrowed in
    # a later run, new or cached from an earlier borrow, must register a new one.
    if backend not in request.config.async_backends:
        pytest.skip(f"{backend} is excluded")
    pools = []

    async def _borrow():
        with anyio.fail_after(5):
            async with pools[0].borrow() as client:
                return await client.ping()

    async def _create():
        pools.append(
            await AsyncClientPool.create(
                _config(False), PoolConfig(max_size=1, min_idle=0)
            )
        )
        if borrow_in_first_run:
            assert await _borrow() == b"PONG"

    anyio.run(_create, backend=backend)
    try:
        assert anyio.run(_borrow, backend=backend) == b"PONG"
    finally:
        pools[0].close()
