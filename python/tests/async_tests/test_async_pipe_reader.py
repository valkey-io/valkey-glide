# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Integration tests for shared-pipe reader ownership across event loops (issue #7122)."""

import asyncio
import multiprocessing
import queue
from typing import Any, Dict

import pytest
from glide import GlideClient

from tests.utils.utils import create_client_config


def _idle_owner_worker(config: Any, result_queue: multiprocessing.Queue) -> None:
    """Registers the reader on a loop that then stops running, and works on another loop."""

    async def own_reader() -> GlideClient:
        client = await GlideClient.create(config)
        await client.set("pipe_reader_key", "owner")
        return client

    async def read_on_another_loop() -> Any:
        client = await GlideClient.create(config)
        try:
            return await client.get("pipe_reader_key")
        finally:
            await client.close()

    async def read_on_resumed_loop(client: GlideClient) -> Any:
        await client.set("pipe_reader_key", "resumed")
        return await client.get("pipe_reader_key")

    idle_loop = asyncio.new_event_loop()
    try:
        owner = idle_loop.run_until_complete(own_reader())
        # idle_loop is open but no longer running, so its reader delivers nothing.
        result_queue.put(("NEW_LOOP", asyncio.run(read_on_another_loop())))
        # An idle loop may legally resume, and its own clients must keep working.
        result_queue.put(
            ("RESUMED", idle_loop.run_until_complete(read_on_resumed_loop(owner)))
        )
        idle_loop.run_until_complete(owner.close())
    except BaseException as exc:  # noqa: BLE001 - reported to the parent below
        result_queue.put(("ERROR", f"{type(exc).__name__}: {exc}"))
    finally:
        idle_loop.close()


def test_idle_reader_loop_does_not_stall_another_loop(request):
    """A client on a new loop is answered while the loop owning the reader sits idle."""
    # In a subprocess because reader ownership is process-wide, and this session
    # has already registered a reader on a loop of its own.
    config = create_client_config(request, cluster_mode=False, request_timeout=2000)
    ctx = multiprocessing.get_context("spawn")
    result_queue: multiprocessing.Queue = ctx.Queue()
    worker = ctx.Process(target=_idle_owner_worker, args=(config, result_queue))
    worker.start()
    worker.join(timeout=60.0)
    if worker.is_alive():
        worker.kill()
        worker.join()
        pytest.fail(
            "worker hung: a command issued on a new event loop was never answered"
        )

    results: Dict[str, Any] = {}
    for _ in range(2):
        try:
            step, value = result_queue.get(timeout=5.0)
        except queue.Empty:
            break
        results[step] = value

    assert "ERROR" not in results, results["ERROR"]
    assert results.get("NEW_LOOP") == b"owner"
    assert results.get("RESUMED") == b"resumed"
