# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Feature 1: Client-Instance Pooling (Python async).
Mirrors sync test_sync_pool.py and Java ClientPoolIntegrationTest for
cross-language parity.

Parameterized over cluster_mode (True/False) to ensure both standalone and
cluster deployments behave identically.
"""

import asyncio
import uuid

import pytest
from glide import (
    AsyncClientPool,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    PoolConfig,
)

from tests.utils.utils import get_cluster_addresses as _get_cluster_addresses
from tests.utils.utils import get_standalone_address as _get_standalone_address

pytestmark = pytest.mark.asyncio


# ─── Helpers ──────────────────────────────────────────────────────────────────


def _skip_cluster_if_unavailable():
    """Skip test if no cluster endpoints are configured."""
    try:
        cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        if cluster is None or len(cluster.nodes_addr) == 0:
            pytest.skip("No cluster endpoints available")
    except AttributeError:
        pytest.skip("No cluster endpoints available (pytest.valkey_cluster not set)")


def _get_pool_client_config(cluster_mode: bool):
    """Build a client configuration for standalone or cluster mode."""
    if cluster_mode:
        _skip_cluster_if_unavailable()
        return GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            request_timeout=5000,
        )
    else:
        return GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
        )


def _make_key(cluster_mode: bool, prefix: str) -> str:
    """Generate a key with hash tag for cluster mode to avoid cross-slot issues."""
    uid = uuid.uuid4().hex[:8]
    if cluster_mode:
        return f"{{pool-test}}-{prefix}-{uid}"
    return f"{prefix}-{uid}"


class TestAsyncClientPool:
    """Async pool lifecycle tests."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_create_and_metrics(self, cluster_mode):
        """Create pool, acquire client, execute commands, release, close."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=3, min_idle=1))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        try:
            assert pool.idle_count >= 1

            async with pool.borrow() as client:
                key = _make_key(cluster_mode, "metrics")
                await client.set(key, "hello")
                val = await client.get(key)
                assert val == b"hello"
                await client.delete([key])
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_borrow_and_commands(self, cluster_mode):
        """Borrow client from pool, execute commands, auto-release."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=3, min_idle=1))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        try:
            async with pool.borrow() as client:
                key = _make_key(cluster_mode, "borrow")
                await client.set(key, "world")
                val = await client.get(key)
                assert val == b"world"
                await client.delete([key])
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_reuse(self, cluster_mode):
        """LIFO: same client_id returned after release."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=3, min_idle=1))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        try:
            id1 = await pool.acquire()
            pool.release(id1)
            await asyncio.sleep(0.1)

            id2 = await pool.acquire()
            pool.release(id2)

            assert id1 == id2
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_metrics(self, cluster_mode):
        """Metrics reflect pool state."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=3, min_idle=2))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        try:
            assert pool.idle_count >= 1
            assert pool.total_count >= 1
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_timeout_on_exhaustion(self, cluster_mode):
        """Timeout when pool is exhausted."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=1, min_idle=1))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        try:
            # Acquire the only client
            client_id = await pool.acquire()

            # Second acquire should timeout
            with pytest.raises(TimeoutError):
                await pool.acquire(timeout=0.5)

            pool.release(client_id)
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_concurrent_access(self, cluster_mode):
        """Multiple tasks borrow/release concurrently."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=4, min_idle=4))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        try:
            errors = []

            async def worker(task_idx):
                try:
                    async with pool.borrow() as client:
                        key = _make_key(cluster_mode, f"concurrent-{task_idx}")
                        await client.set(key, f"task-{task_idx}")
                        val = await client.get(key)
                        assert val == f"task-{task_idx}".encode()
                        await client.delete([key])
                except Exception as e:
                    errors.append(e)

            tasks = [asyncio.create_task(worker(i)) for i in range(8)]
            await asyncio.gather(*tasks)

            assert not errors, f"Worker errors: {errors}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_close_rejects_acquire(self, cluster_mode):
        """Closed pool rejects acquire."""
        config = _get_pool_client_config(cluster_mode)
        pool = AsyncClientPool(config, PoolConfig(max_size=2, min_idle=1))
        deadline = asyncio.get_event_loop().time() + 30
        while pool.idle_count < 1 and asyncio.get_event_loop().time() < deadline:
            await asyncio.sleep(0.5)

        pool.close()

        with pytest.raises(RuntimeError, match="closed"):
            await pool.acquire()


class TestPoolPubsubRejection:
    """Pool creation should reject configs with pubsub subscriptions."""

    async def test_pool_rejects_pubsub_config(self):
        """Pool creation with pubsub subscriptions raises ValueError."""
        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
            pubsub_subscriptions=GlideClientConfiguration.PubSubSubscriptions(
                callback=None,
                context=None,
                channels_and_patterns={
                    GlideClientConfiguration.PubSubChannelModes.Exact: {"test-channel"}
                },
            ),
        )
        with pytest.raises(ValueError, match="pubsub"):
            AsyncClientPool(config, PoolConfig(max_size=2, min_idle=1))

    async def test_pool_rejects_cluster_pubsub_config(self):
        """Pool creation with cluster pubsub subscriptions raises ValueError."""
        _skip_cluster_if_unavailable()
        config = GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            request_timeout=5000,
            pubsub_subscriptions=GlideClusterClientConfiguration.PubSubSubscriptions(
                callback=None,
                context=None,
                channels_and_patterns={
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        "test-channel"
                    }
                },
            ),
        )
        with pytest.raises(ValueError, match="pubsub"):
            AsyncClientPool(config, PoolConfig(max_size=2, min_idle=1))
