# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import threading
import uuid

import anyio
import pytest
from glide import AsyncClientPool, GlideClient, GlideClientConfiguration, PoolConfig

from tests.utils.utils import get_standalone_address

pytestmark = pytest.mark.asyncio


async def _thread_result(future, timeout=10):
    return await anyio.to_thread.run_sync(lambda: future.result(timeout=timeout))


async def test_running_asyncio_pipe_owner_serves_clients_on_both_loops():  # noqa: C901
    """A running owner remains the sole reader while another loop uses a client."""
    import glide.glide_client as async_client_module

    config = GlideClientConfiguration(
        addresses=[get_standalone_address()], request_timeout=5000
    )
    owner_loop = asyncio.new_event_loop()
    owner_ready = threading.Event()
    stop_commands = threading.Event()
    owner_state = {"commands": 0, "error": None}
    owner_holder = {}
    key_prefix = f"pipe-owner-{uuid.uuid4().hex}"

    def owner_thread_main():
        asyncio.set_event_loop(owner_loop)

        async def start_owner():
            client = await GlideClient.create(config)
            owner_holder["client"] = client

            async def issue_commands():
                try:
                    while not stop_commands.is_set():
                        index = owner_state["commands"]
                        key = f"{key_prefix}-owner-{index}"
                        await client.set(key, str(index))
                        assert await client.get(key) == str(index).encode()
                        owner_state["commands"] = index + 1
                except BaseException as error:
                    owner_state["error"] = error
                    raise

            owner_holder["task"] = owner_loop.create_task(issue_commands())
            owner_loop.call_soon(owner_ready.set)

        owner_loop.run_until_complete(start_owner())
        owner_loop.run_forever()

    owner_thread = threading.Thread(target=owner_thread_main)
    owner_thread.start()
    assert await anyio.to_thread.run_sync(lambda: owner_ready.wait(timeout=10))
    owner_client = owner_holder["client"]

    async def wait_for_owner_progress(after):
        with anyio.fail_after(10):
            while owner_state["commands"] <= after:
                if owner_state["error"] is not None:
                    raise owner_state["error"]
                await anyio.sleep(0)

    try:
        await wait_for_owner_progress(-1)
        before_second = owner_state["commands"]
        assert async_client_module._async_pipe_loop is owner_loop

        second = await GlideClient.create(config)
        try:
            assert async_client_module._async_pipe_loop is owner_loop
            for index in range(5):
                key = f"{key_prefix}-second-{index}"
                await second.set(key, str(index))
                assert await second.get(key) == str(index).encode()
            await wait_for_owner_progress(before_second)
            during_second = owner_state["commands"]
        finally:
            await second.close()

        assert async_client_module._async_pipe_loop is owner_loop
        await wait_for_owner_progress(during_second)
    finally:
        stop_commands.set()

        async def shutdown_owner():
            task = owner_holder["task"]
            await task
            await owner_client.close()

        shutdown = asyncio.run_coroutine_threadsafe(shutdown_owner(), owner_loop)
        await _thread_result(shutdown)
        owner_loop.call_soon_threadsafe(owner_loop.stop)
        owner_thread.join(timeout=10)
        assert not owner_thread.is_alive()
        owner_loop.close()

    assert owner_state["error"] is None


async def test_async_pool_takes_over_stopped_owner_and_reuses_pipe():
    """A pool wrapper can command and release after stopped-loop takeover."""
    import glide.glide_client as async_client_module

    config = GlideClientConfiguration(
        addresses=[get_standalone_address()], request_timeout=5000
    )
    stopped_loop = asyncio.new_event_loop()
    stopped_holder = {}

    def create_stopped_owner():
        asyncio.set_event_loop(stopped_loop)
        stopped_holder["client"] = stopped_loop.run_until_complete(
            GlideClient.create(config)
        )

    create_thread = threading.Thread(target=create_stopped_owner)
    create_thread.start()
    create_thread.join(timeout=10)
    assert not create_thread.is_alive()
    assert not stopped_loop.is_running()
    assert not stopped_loop.is_closed()
    assert async_client_module._async_pipe_loop is stopped_loop
    original_fds = (
        async_client_module._async_pipe_read_fd,
        async_client_module._async_pipe_write_fd,
    )

    pool = await AsyncClientPool.create(
        config, PoolConfig(max_size=1, min_idle=1, request_timeout_ms=5000)
    )
    try:
        client_id = await pool.acquire(timeout=10)
        client = pool._get_or_create_client(client_id)
        key = f"pipe-pool-{uuid.uuid4().hex}"
        await client.set(key, "value")
        assert await client.get(key) == b"value"
        pool.release(client_id)
        assert async_client_module._async_pipe_loop is asyncio.get_running_loop()
        assert (
            async_client_module._async_pipe_read_fd,
            async_client_module._async_pipe_write_fd,
        ) == original_fds
    finally:
        pool.close()

        def close_stopped_owner():
            asyncio.set_event_loop(stopped_loop)
            stopped_loop.run_until_complete(stopped_holder["client"].close())
            stopped_loop.close()

        close_thread = threading.Thread(target=close_stopped_owner)
        close_thread.start()
        close_thread.join(timeout=10)
        assert not close_thread.is_alive()
