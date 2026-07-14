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


async def _wait_for_pool_ready(pool, min_idle=1, timeout_s=30):
    """Poll until pool has at least min_idle clients ready."""
    deadline = asyncio.get_event_loop().time() + timeout_s
    while pool.idle_count < min_idle and asyncio.get_event_loop().time() < deadline:
        await asyncio.sleep(0.05)


class TestAsyncClientPool:
    """Async pool lifecycle tests."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_create_and_metrics(self, cluster_mode):
        """Create pool, acquire client, execute commands, release, close."""
        config = _get_pool_client_config(cluster_mode)
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=3, min_idle=1))
        await _wait_for_pool_ready(pool, 1)

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
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=3, min_idle=1))
        await _wait_for_pool_ready(pool, 1)

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
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=3, min_idle=1))
        await _wait_for_pool_ready(pool, 1)

        try:
            id1 = await pool.acquire()
            pool.release(id1)
            await asyncio.sleep(0.05)

            id2 = await pool.acquire()
            pool.release(id2)

            assert id1 == id2
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_metrics(self, cluster_mode):
        """Metrics reflect pool state."""
        config = _get_pool_client_config(cluster_mode)
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=3, min_idle=2))
        await _wait_for_pool_ready(pool, 1)

        try:
            assert pool.idle_count >= 1
            assert pool.total_count >= 1
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_timeout_on_exhaustion(self, cluster_mode):
        """Timeout when pool is exhausted."""
        config = _get_pool_client_config(cluster_mode)
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=1, min_idle=1))
        await _wait_for_pool_ready(pool, 1)

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
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=4, min_idle=4))
        await _wait_for_pool_ready(pool, 1)

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
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=2, min_idle=1))
        await _wait_for_pool_ready(pool, 1)

        pool.close()

        with pytest.raises(RuntimeError, match="closed"):
            await pool.acquire()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_publish_concurrent(self, cluster_mode):
        """Multiple tasks publishing through pooled clients concurrently."""
        config = _get_pool_client_config(cluster_mode)
        pool = await AsyncClientPool.create(
            config, PoolConfig(max_size=4, min_idle=4, acquire_timeout_s=15.0)
        )
        await _wait_for_pool_ready(pool, 1)

        try:
            channel = f"pool-pub-{uuid.uuid4().hex[:8]}"
            errors = []

            async def publisher(task_id):
                for i in range(5):
                    try:
                        async with pool.borrow() as client:
                            await client.custom_command(
                                ["PUBLISH", channel, f"msg-{task_id}-{i}"]
                            )
                    except Exception as e:
                        errors.append(f"Task {task_id} msg {i}: {e}")

            tasks = [asyncio.create_task(publisher(t)) for t in range(4)]
            await asyncio.gather(*tasks)
            assert not errors, f"Publish errors: {errors}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_pool_blocking_cmd_isolation(self, cluster_mode):
        """BLPOP on one client doesn't stall GET/SET on another."""
        if cluster_mode:
            return  # BLPOP with timeout is standalone-only for this test
        config = _get_pool_client_config(False)
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=2, min_idle=2))
        await _wait_for_pool_ready(pool, 2)

        try:
            key = _make_key(False, "blocking")

            # Client 1: BLPOP with long timeout (will be unblocked by LPUSH)
            async def blocking():
                async with pool.borrow() as client:
                    return await client.custom_command(["BLPOP", key, "30"])

            blocking_task = asyncio.create_task(blocking())
            await asyncio.sleep(0.05)

            # Client 2: GET/SET should complete immediately
            import time

            start = time.monotonic()
            async with pool.borrow() as client:
                k = _make_key(False, "nonblocking")
                await client.set(k, "fast")
                val = await client.get(k)
                await client.delete([k])
                # Unblock the BLPOP by pushing to its key
                await client.custom_command(["LPUSH", key, "unblock"])
            elapsed = time.monotonic() - start

            assert val == b"fast"
            assert (
                elapsed < 1.0
            ), f"Non-blocking ops took {elapsed:.2f}s (should be <1s)"

            result = await blocking_task
            assert result is not None  # BLPOP got the pushed value
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_scope_on_pool_borrowed_client(self, cluster_mode):
        """Scoped connection works on a pool-borrowed client (not just direct clients)."""
        config = _get_pool_client_config(cluster_mode)
        pool = await AsyncClientPool.create(config, PoolConfig(max_size=3, min_idle=1))
        await _wait_for_pool_ready(pool, 1)
        try:
            key = _make_key(cluster_mode, "scope-on-pool")
            async with pool.borrow() as client:
                await client.set(key, "0")

                # Open a scope on the pool-borrowed client
                async with await client.scoped_connection() as scope:
                    await scope.watch(key)
                    val = await scope.get(key)
                    await scope.multi()
                    await scope.set(key, str(int(val) + 1))
                    result = await scope.exec()
                    assert result is not None  # No conflict

                assert await client.get(key) == b"1"
                await client.delete([key])
        finally:
            pool.close()


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


@pytest.mark.anyio
class TestPoolErrorHandling:
    """Negative tests — pool creation surfaces connectivity errors."""

    async def test_pool_bad_address_create_fails(self, request):
        """Pool with unreachable address: create should raise immediately."""
        from glide_shared.config import GlideClientConfiguration, NodeAddress

        config = GlideClientConfiguration(
            addresses=[NodeAddress("192.0.2.1", 1)],  # RFC 5737 TEST-NET
            request_timeout=2000,
        )
        with pytest.raises(Exception):
            await AsyncClientPool.create(config, PoolConfig(max_size=1, min_idle=1))
