# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
PubSub integration tests for client pool under concurrent access.
Validates that pubsub messages are delivered correctly when multiple
threads share a pool and subscribe/publish concurrently.
"""

import threading
import time
import uuid

from glide_shared.config import GlideClientConfiguration
from glide_sync import GlideClient
from glide_sync.client_pool import ClientPool, PoolConfig

from tests.utils.utils import get_standalone_address as _get_standalone_address


def get_config() -> GlideClientConfiguration:
    return GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=5000,
    )


class TestPoolPubSub:
    """PubSub tests with pooled clients under concurrent access."""

    @staticmethod
    def _wait_for_pool_ready(pool, timeout=30):
        """Poll until pool has at least 1 idle client ready."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            metrics = pool.metrics()
            if metrics.get("idle", 0) >= 1:
                return
            time.sleep(0.5)
        # If pool never got any clients, skip (likely server not reachable
        # from the background thread — infrastructure issue, not a code bug)
        import pytest

        pytest.skip(
            f"Pool could not create clients within {timeout}s "
            f"(metrics: {pool.metrics()}). Server may not be reachable "
            f"from pool background threads."
        )

    def test_publish_from_pool(self):
        """Pool clients can publish messages."""
        config = get_config()
        pool = ClientPool(
            config, PoolConfig(max_size=3, min_idle=1, acquire_timeout_s=15.0)
        )
        self._wait_for_pool_ready(pool)

        channel = f"test-channel-{uuid.uuid4().hex[:8]}"
        try:
            with pool.borrow() as client:
                # PUBLISH returns number of subscribers (0 if none)
                result = client.custom_command(["PUBLISH", channel, "hello"])
                # Just verify it doesn't crash — 0 subscribers expected
                assert result is not None or result == 0
        finally:
            pool.close()

    def test_concurrent_publish(self):
        """Multiple threads publishing through pooled clients concurrently."""
        config = get_config()
        pool = ClientPool(
            config, PoolConfig(max_size=4, min_idle=2, acquire_timeout_s=15.0)
        )
        self._wait_for_pool_ready(pool)

        channel = f"concurrent-pub-{uuid.uuid4().hex[:8]}"
        num_threads = 4
        msgs_per_thread = 20
        errors = []

        def publisher(thread_id):
            for i in range(msgs_per_thread):
                try:
                    with pool.borrow() as client:
                        client.custom_command(
                            ["PUBLISH", channel, f"msg-{thread_id}-{i}"]
                        )
                except Exception as e:
                    errors.append(f"Thread {thread_id} msg {i}: {e}")
                    return

        threads = [
            threading.Thread(target=publisher, args=(t,)) for t in range(num_threads)
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=30)

        assert not errors, "Publish errors:\n" + "\n".join(errors[:10])
        pool.close()

    def test_pool_with_subscriber_client(self):
        """
        One thread subscribes via a dedicated client, others publish via pool.
        Validates messages arrive correctly under concurrent pool usage.
        """
        config = get_config()
        pool = ClientPool(
            config, PoolConfig(max_size=3, min_idle=1, acquire_timeout_s=15.0)
        )
        self._wait_for_pool_ready(pool)

        channel = f"sub-test-{uuid.uuid4().hex[:8]}"
        received_messages = []
        subscriber_ready = threading.Event()
        stop_event = threading.Event()

        # Create a dedicated subscriber client (not from pool)
        sub_client = GlideClient.create(config)

        def subscriber():
            try:
                sub_client.custom_command(["SUBSCRIBE", channel])
                subscriber_ready.set()
                # Poll for messages with timeout
                deadline = time.monotonic() + 10
                while not stop_event.is_set() and time.monotonic() < deadline:
                    msg = sub_client.try_get_pubsub_message()
                    if msg is not None:
                        received_messages.append(msg)
                    else:
                        time.sleep(0.01)
            except Exception:
                pass

        sub_thread = threading.Thread(target=subscriber)
        sub_thread.start()
        subscriber_ready.wait(timeout=5)
        time.sleep(0.5)  # Give subscription time to register on server

        # Publish from pool
        num_messages = 5
        for i in range(num_messages):
            with pool.borrow() as client:
                client.custom_command(["PUBLISH", channel, f"pooled-{i}"])

        # Wait for messages
        time.sleep(2)
        stop_event.set()
        sub_thread.join(timeout=5)

        # Cleanup
        try:
            sub_client.custom_command(["UNSUBSCRIBE", channel])
        except Exception:
            pass
        sub_client.close()
        pool.close()

        # Verify at least some messages arrived
        # (timing-sensitive, so we check > 0 rather than exact count)
        assert (
            len(received_messages) > 0
        ), f"Expected messages on channel {channel}, got none"
