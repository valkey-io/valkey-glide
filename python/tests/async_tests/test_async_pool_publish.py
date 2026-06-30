# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
PubSub integration tests for async client pool under concurrent access.
Validates that pubsub messages are delivered correctly when multiple
tasks share a pool and subscribe/publish concurrently.
"""

import asyncio
import uuid

import pytest
from glide import (
    AsyncClientPool,
    GlideClient,
    GlideClientConfiguration,
    PoolConfig,
)

from tests.utils.utils import get_standalone_address as _get_standalone_address

pytestmark = pytest.mark.asyncio


def get_config() -> GlideClientConfiguration:
    return GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=5000,
    )


class TestPoolPublish:
    """PubSub tests with pooled clients under concurrent access."""

    async def test_publish_from_pool(self):
        """Pool clients can publish messages."""
        config = get_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=3, min_idle=1))
        await asyncio.sleep(5)  # Allow warmup (CI can be slow)

        channel = f"test-channel-{uuid.uuid4().hex[:8]}"
        try:
            async with pool.borrow() as client:
                # PUBLISH returns number of subscribers (0 if none)
                result = await client.custom_command(["PUBLISH", channel, "hello"])
                # Just verify it doesn't crash — 0 subscribers expected
                assert result is not None or result == 0
        finally:
            pool.close()

    async def test_concurrent_publish(self):
        """Multiple tasks publishing through pooled clients concurrently."""
        config = get_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=4, min_idle=2))
        await asyncio.sleep(5)  # Allow warmup (CI can be slow)

        channel = f"concurrent-pub-{uuid.uuid4().hex[:8]}"
        num_tasks = 4
        msgs_per_task = 20
        errors = []

        async def publisher(task_id):
            for i in range(msgs_per_task):
                try:
                    async with pool.borrow() as client:
                        await client.custom_command(
                            ["PUBLISH", channel, f"msg-{task_id}-{i}"]
                        )
                except Exception as e:
                    errors.append(f"Task {task_id} msg {i}: {e}")
                    return

        tasks = [asyncio.create_task(publisher(t)) for t in range(num_tasks)]
        await asyncio.gather(*tasks)

        assert not errors, "Publish errors:\n" + "\n".join(errors[:10])
        pool.close()

    async def test_pool_with_subscriber_client(self):
        """
        One task subscribes via a dedicated client, others publish via pool.
        Validates messages arrive correctly under concurrent pool usage.
        """
        config = get_config()
        pool = AsyncClientPool(config, PoolConfig(max_size=3, min_idle=1))
        await asyncio.sleep(5)  # Allow warmup (CI can be slow)

        channel = f"sub-test-{uuid.uuid4().hex[:8]}"
        received_messages = []
        subscriber_ready = asyncio.Event()
        stop_event = asyncio.Event()

        # Create a dedicated subscriber client (not from pool)
        sub_client = await GlideClient.create(config)

        async def subscriber():
            try:
                await sub_client.custom_command(["SUBSCRIBE", channel])
                subscriber_ready.set()
                # Poll for messages with timeout
                deadline = asyncio.get_event_loop().time() + 10
                while (
                    not stop_event.is_set()
                    and asyncio.get_event_loop().time() < deadline
                ):
                    msg = await sub_client.try_get_pubsub_message()
                    if msg is not None:
                        received_messages.append(msg)
                    else:
                        await asyncio.sleep(0.01)
            except Exception:
                pass

        sub_task = asyncio.create_task(subscriber())
        await asyncio.wait_for(subscriber_ready.wait(), timeout=5)
        await asyncio.sleep(0.5)  # Give subscription time to register on server

        # Publish from pool
        num_messages = 5
        for i in range(num_messages):
            async with pool.borrow() as client:
                await client.custom_command(["PUBLISH", channel, f"pooled-{i}"])

        # Wait for messages
        await asyncio.sleep(2)
        stop_event.set()
        await asyncio.wait_for(sub_task, timeout=5)

        # Cleanup
        try:
            await sub_client.custom_command(["UNSUBSCRIBE", channel])
        except Exception:
            pass
        await sub_client.close()
        pool.close()

        # Verify at least some messages arrived
        # (timing-sensitive, so we check > 0 rather than exact count)
        assert (
            len(received_messages) > 0
        ), f"Expected messages on channel {channel}, got none"
