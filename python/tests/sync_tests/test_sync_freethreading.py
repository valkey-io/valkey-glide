# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Free-threading stress tests for the synchronous GLIDE client.

These tests exercise high-contention parallel access patterns that would
crash or produce incorrect results on a free-threaded Python build (3.13t+)
if the native extensions or client code have data races.

On a GIL-enabled build, these tests verify correctness (the GIL serializes
access, so races can't manifest). On a free-threaded build, they validate
true thread safety.
"""

import sys
import threading
import time
import uuid

import pytest
from glide_shared.config import GlideClientConfiguration, NodeAddress
from glide_sync.client_pool import ClientPool, PoolConfig


def get_config(request) -> GlideClientConfiguration:
    host = request.config.getoption("--host", default="localhost")
    port = int(request.config.getoption("--port", default="6379"))
    return GlideClientConfiguration(
        addresses=[NodeAddress(host, port)],
        request_timeout=5000,
    )


def is_free_threaded() -> bool:
    """Check if running on a free-threaded (no-GIL) Python build."""
    if hasattr(sys, "_is_gil_enabled"):
        return not sys._is_gil_enabled()
    return False


class TestFreeThreading:
    """
    Stress tests for free-threading safety.
    These exercise parallel access patterns that would crash without
    proper synchronization in the native layer.
    """

    @pytest.fixture
    def pool(self, request):
        config = get_config(request)
        p = ClientPool(
            config, PoolConfig(max_size=8, min_idle=4, acquire_timeout_s=15.0)
        )
        time.sleep(4)  # Wait for min_idle warmup
        yield p
        p.close()

    def test_parallel_commands_same_pool(self, pool):
        """
        16 threads, each doing 50 SET/GET cycles through the pool.
        Validates no response cross-contamination under parallel execution.
        """
        num_threads = 16
        ops_per_thread = 50
        errors = []
        success_count = threading.atomic(0) if hasattr(threading, "atomic") else [0]
        lock = threading.Lock()

        def worker(thread_id):
            for i in range(ops_per_thread):
                key = f"ft-stress-{thread_id}-{i}-{uuid.uuid4().hex[:8]}"
                expected_value = f"val-{thread_id}-{i}"
                try:
                    with pool.borrow() as client:
                        client.set(key, expected_value)
                        result = client.get(key)
                        # Verify response correctness — the critical check
                        actual = (
                            result.decode() if isinstance(result, bytes) else result
                        )
                        if actual != expected_value:
                            errors.append(
                                f"Thread {thread_id} op {i}: expected '{expected_value}', "
                                f"got '{actual}' (RESPONSE CORRUPTION)"
                            )
                            return
                        client.delete([key])
                except Exception as e:
                    errors.append(f"Thread {thread_id} op {i}: {type(e).__name__}: {e}")
                    return

            with lock:
                if isinstance(success_count, list):
                    success_count[0] += 1

        threads = [
            threading.Thread(target=worker, args=(t,), name=f"ft-worker-{t}")
            for t in range(num_threads)
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=60)

        assert not errors, "Free-threading errors:\n" + "\n".join(errors[:10])
        expected = num_threads
        actual = success_count[0] if isinstance(success_count, list) else success_count
        assert actual == expected, f"Only {actual}/{expected} threads completed"

    def test_parallel_pool_acquire_release_storm(self, pool):
        """
        Rapid acquire/release without doing commands — stresses the pool's
        lock contention and atomic operations under true parallelism.
        """
        num_threads = 16
        cycles_per_thread = 100
        errors = []

        def worker(thread_id):
            for i in range(cycles_per_thread):
                try:
                    client_id = pool.acquire(timeout=10.0)
                    # Minimal hold time — maximizes contention
                    pool.release(client_id)
                except Exception as e:
                    errors.append(f"Thread {thread_id} cycle {i}: {e}")
                    return

        threads = [
            threading.Thread(target=worker, args=(t,)) for t in range(num_threads)
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=60)

        assert not errors, "Pool contention errors:\n" + "\n".join(errors[:10])

    def test_response_parser_parallel_invocation(self, pool):
        """
        Multiple threads calling commands simultaneously — exercises the
        _fast_response parser's OnceLock-cached RequestError class lookup
        under parallel access.
        """
        num_threads = 8
        ops_per_thread = 100
        errors = []

        def worker(thread_id):
            with pool.borrow() as client:
                for i in range(ops_per_thread):
                    try:
                        key = f"parser-{thread_id}-{i}"
                        client.set(key, str(i))
                        val = client.get(key)
                        actual = int(val) if val else -1
                        if actual != i:
                            errors.append(
                                f"Thread {thread_id}: expected {i}, got {actual}"
                            )
                            return
                        client.delete([key])
                    except Exception as e:
                        errors.append(f"Thread {thread_id} op {i}: {e}")
                        return

        threads = [
            threading.Thread(target=worker, args=(t,)) for t in range(num_threads)
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=60)

        assert not errors, "Parser parallel errors:\n" + "\n".join(errors[:10])

    def test_concurrent_pool_metrics(self, pool):
        """
        One thread hammering metrics() while others do acquire/release.
        metrics() reads pool state without mutation — under free-threading,
        this validates that concurrent readers don't crash.
        """
        stop_event = threading.Event()
        errors = []
        metrics_calls = [0]

        def metrics_reader():
            while not stop_event.is_set():
                try:
                    m = pool.metrics()
                    assert "idle" in m
                    assert "active" in m
                    assert "total" in m
                    metrics_calls[0] += 1
                except Exception as e:
                    errors.append(f"Metrics reader: {e}")
                    return

        def pool_user(thread_id):
            for _ in range(50):
                try:
                    with pool.borrow() as client:
                        client.set(f"metrics-test-{thread_id}", "x")
                        client.get(f"metrics-test-{thread_id}")
                        client.delete([f"metrics-test-{thread_id}"])
                except Exception as e:
                    errors.append(f"Pool user {thread_id}: {e}")
                    return

        reader = threading.Thread(target=metrics_reader)
        workers = [threading.Thread(target=pool_user, args=(t,)) for t in range(4)]

        reader.start()
        for w in workers:
            w.start()
        for w in workers:
            w.join(timeout=30)
        stop_event.set()
        reader.join(timeout=5)

        assert not errors, "Errors:\n" + "\n".join(errors[:10])
        assert metrics_calls[0] > 0, "Metrics reader should have run"

    def test_info_free_threading_status(self):
        """Report whether we're running on a free-threaded build."""
        ft = is_free_threaded()
        print(f"\n  Free-threaded Python: {ft}")
        print(f"  Python version: {sys.version}")
        if hasattr(sys, "_is_gil_enabled"):
            print(f"  GIL enabled: {sys._is_gil_enabled()}")
        # This test always passes — it's informational
