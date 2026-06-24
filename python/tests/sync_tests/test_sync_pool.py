# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Feature 1: Client-Instance Pooling (Python sync client).
Requires a Valkey server (uses test infrastructure endpoints).
"""

import threading
import time
import uuid

import pytest
from glide_shared.config import GlideClientConfiguration, NodeAddress
from glide_sync.client_pool import ClientPool, PoolConfig


def get_standalone_config(request) -> GlideClientConfiguration:
    """Build a GlideClientConfiguration using the test server endpoints."""
    # The test infrastructure passes endpoints via pytest options
    host = request.config.getoption("--host", default="localhost")
    port = int(request.config.getoption("--port", default="6379"))
    return GlideClientConfiguration(
        addresses=[NodeAddress(host, port)],
        request_timeout=5000,
    )


class TestClientPool:
    """Tests for the ClientPool class backed by glide-core::pool via FFI."""

    @pytest.fixture
    def pool(self, request):
        """Create a pool for each test, close on teardown."""
        config = get_standalone_config(request)
        pool_config = PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        p = ClientPool(config, pool_config)
        # Wait for min_idle warmup
        time.sleep(3)
        yield p
        p.close()

    def test_pool_create_and_metrics(self, pool):
        """Pool creates successfully and reports metrics."""
        metrics = pool.metrics()
        assert metrics["idle"] >= 1, f"Expected at least 1 idle, got {metrics}"
        assert metrics["total"] >= 1, f"Expected total >= 1, got {metrics}"
        assert not pool.is_closed

    def test_pool_borrow_and_commands(self, pool):
        """Borrow a client, run commands, auto-release."""
        key = f"pool-py-test-{uuid.uuid4()}"
        with pool.borrow() as client:
            client.set(key, "hello")
            result = client.get(key)
            assert result == b"hello" or result == "hello"
            client.delete([key])

    def test_pool_reuse(self, pool):
        """LIFO reuse: same client_id returned after release."""
        id1 = pool.acquire()
        pool.release(id1)
        time.sleep(0.1)
        id2 = pool.acquire()
        pool.release(id2)
        assert id1 == id2, f"Expected LIFO reuse: {id1} != {id2}"

    def test_pool_close_rejects_acquire(self, request):
        """Closed pool raises on acquire."""
        config = get_standalone_config(request)
        p = ClientPool(config, PoolConfig(max_size=2, min_idle=0))
        p.close()
        with pytest.raises(RuntimeError, match="closed"):
            p.acquire()

    def test_pool_concurrent_access(self, pool):
        """Multiple threads can borrow and use clients concurrently."""
        num_threads = 4
        results = [None] * num_threads
        errors = []

        def worker(idx):
            try:
                with pool.borrow() as client:
                    key = f"concurrent-py-{idx}-{uuid.uuid4()}"
                    client.set(key, f"thread-{idx}")
                    val = client.get(key)
                    assert val == f"thread-{idx}".encode() or val == f"thread-{idx}"
                    client.delete([key])
                    results[idx] = True
            except Exception as e:
                errors.append((idx, e))
                results[idx] = False

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(num_threads)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=30)

        assert not errors, f"Thread errors: {errors}"
        assert all(results), f"Not all threads succeeded: {results}"

    def test_pool_timeout_on_exhaustion(self, request):
        """Pool raises TimeoutError when exhausted within timeout."""
        config = get_standalone_config(request)
        p = ClientPool(config, PoolConfig(max_size=1, min_idle=1, acquire_timeout_s=1.0))
        time.sleep(3)  # warmup

        # Acquire the only client
        client_id = p.acquire()

        # Second acquire should time out
        with pytest.raises(TimeoutError):
            p.acquire(timeout=0.5)

        p.release(client_id)
        p.close()
