# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Feature 1: Client-Instance Pooling (Python sync client).
Requires a Valkey server (uses test infrastructure endpoints).

Parameterized over cluster_mode (True/False) to ensure both standalone and
cluster deployments behave identically.
"""

import threading
import time
import uuid

import pytest
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_sync.client_pool import ClientPool, PoolConfig

from tests.utils.utils import get_cluster_addresses as _get_cluster_addresses
from tests.utils.utils import get_standalone_address as _get_standalone_address

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


class TestClientPool:
    """Tests for the ClientPool class backed by glide-core::pool via FFI."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_create_and_metrics(self, cluster_mode):
        """Pool creates successfully and reports metrics."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        deadline = time.monotonic() + 30
        while pool.metrics().get("idle", 0) < 1 and time.monotonic() < deadline:
            time.sleep(0.5)

        try:
            metrics = pool.metrics()
            assert metrics["idle"] >= 1, f"Expected at least 1 idle, got {metrics}"
            assert metrics["total"] >= 1, f"Expected total >= 1, got {metrics}"
            assert not pool.is_closed
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_borrow_and_commands(self, cluster_mode):
        """Borrow a client, run commands, auto-release."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        deadline = time.monotonic() + 30
        while pool.metrics().get("idle", 0) < 1 and time.monotonic() < deadline:
            time.sleep(0.5)

        try:
            key = _make_key(cluster_mode, "borrow")
            with pool.borrow() as client:
                client.set(key, "hello")
                result = client.get(key)
                assert result == b"hello" or result == "hello"
                client.delete([key])
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_reuse(self, cluster_mode):
        """LIFO reuse: same client_id returned after release."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        deadline = time.monotonic() + 30
        while pool.metrics().get("idle", 0) < 1 and time.monotonic() < deadline:
            time.sleep(0.5)

        try:
            id1 = pool.acquire()
            pool.release(id1)
            time.sleep(0.1)
            id2 = pool.acquire()
            pool.release(id2)
            assert id1 == id2, f"Expected LIFO reuse: {id1} != {id2}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_close_rejects_acquire(self, cluster_mode):
        """Closed pool raises on acquire."""
        config = _get_pool_client_config(cluster_mode)
        p = ClientPool(config, PoolConfig(max_size=2, min_idle=0))
        p.close()
        with pytest.raises(RuntimeError, match="closed"):
            p.acquire()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_concurrent_access(self, cluster_mode):
        """Multiple threads can borrow and use clients concurrently."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        deadline = time.monotonic() + 30
        while pool.metrics().get("idle", 0) < 1 and time.monotonic() < deadline:
            time.sleep(0.5)

        try:
            num_threads = 4
            results = [None] * num_threads
            errors = []

            def worker(idx):
                try:
                    with pool.borrow() as client:
                        key = _make_key(cluster_mode, f"concurrent-{idx}")
                        client.set(key, f"thread-{idx}")
                        val = client.get(key)
                        assert val == f"thread-{idx}".encode() or val == f"thread-{idx}"
                        client.delete([key])
                        results[idx] = True
                except Exception as e:
                    errors.append((idx, e))
                    results[idx] = False

            threads = [
                threading.Thread(target=worker, args=(i,)) for i in range(num_threads)
            ]
            for t in threads:
                t.start()
            for t in threads:
                t.join(timeout=30)

            assert not errors, f"Thread errors: {errors}"
            assert all(results), f"Not all threads succeeded: {results}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_timeout_on_exhaustion(self, cluster_mode):
        """Pool raises TimeoutError when exhausted within timeout."""
        config = _get_pool_client_config(cluster_mode)
        p = ClientPool(
            config, PoolConfig(max_size=1, min_idle=1, acquire_timeout_s=1.0)
        )
        deadline = time.monotonic() + 30
        while p.metrics().get("idle", 0) < 1 and time.monotonic() < deadline:
            time.sleep(0.5)

        try:
            # Acquire the only client
            client_id = p.acquire()

            # Second acquire should time out
            with pytest.raises(TimeoutError):
                p.acquire(timeout=0.5)

            p.release(client_id)
        finally:
            p.close()


class TestPoolPubsubRejection:
    """Pool creation should reject configs with pubsub subscriptions."""

    def test_pool_rejects_pubsub_config(self):
        """Pool creation with pubsub subscriptions raises ValueError."""
        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
            pubsub_subscriptions=GlideClientConfiguration.PubSubSubscriptions(
                channels_and_patterns={
                    GlideClientConfiguration.PubSubChannelModes.Exact: {"test-channel"}
                }
            ),
        )
        with pytest.raises(ValueError, match="pubsub"):
            ClientPool(config, PoolConfig(max_size=2, min_idle=1))

    def test_pool_rejects_cluster_pubsub_config(self):
        """Pool creation with cluster pubsub subscriptions raises ValueError."""
        _skip_cluster_if_unavailable()
        config = GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            request_timeout=5000,
            pubsub_subscriptions=GlideClusterClientConfiguration.PubSubSubscriptions(
                channels_and_patterns={
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {"test-channel"}
                }
            ),
        )
        with pytest.raises(ValueError, match="pubsub"):
            ClientPool(config, PoolConfig(max_size=2, min_idle=1))
