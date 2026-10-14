# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Integration tests for shared-pipe reader ownership across event loops (issue #7122)."""

import asyncio
import multiprocessing
import queue
import sys
import threading
from typing import Any, Dict, Optional, Tuple

import pytest
from glide import GlideClient

from tests.utils.utils import create_client_config

ROUNDS = 100


async def _round_trip(config: Any, tag: str, rounds: int = 1) -> Optional[str]:
    """Runs SET/GET rounds on a client of its own, and reports the first wrong answer."""
    client = await GlideClient.create(config)
    try:
        for i in range(rounds):
            await client.set(f"{tag}:{i}", f"{tag}:{i}")
            value = await client.get(f"{tag}:{i}")
            if value != f"{tag}:{i}".encode():
                return f"{tag}: wrong answer at {i}: {value!r}"
        return None
    finally:
        await client.close()


def _takeover_worker(config: Any, result_queue: multiprocessing.Queue) -> None:
    """Idles the loop holding the reader, then drives both loops at once."""
    import glide.glide_client as glide_client

    handover = threading.Barrier(2)
    idle_result: Dict[str, Any] = {}

    def run_idle_loop(loop: asyncio.AbstractEventLoop) -> None:
        asyncio.set_event_loop(loop)
        try:
            starter = loop.run_until_complete(GlideClient.create(config))
            handover.wait()  # the reader is registered on this loop, which now idles
            handover.wait()  # released once the other loop has taken the reader over
            idle_result["wrong"] = loop.run_until_complete(
                _round_trip(config, "idle", ROUNDS)
            )
            loop.run_until_complete(starter.close())
        except BaseException as exc:  # noqa: BLE001 - reported to the parent below
            idle_result["error"] = f"{type(exc).__name__}: {exc}"
            try:
                handover.abort()
            except BaseException:  # noqa: BLE001 - the parent reports the error
                pass

    idle_loop = asyncio.new_event_loop()
    idle_thread = threading.Thread(target=run_idle_loop, args=(idle_loop,), daemon=True)
    takeover_loop = asyncio.new_event_loop()
    try:
        idle_thread.start()
        handover.wait()
        readers_before = len(glide_client._async_pipe_reader_loops)
        # Nothing is reading the pipe until this loop registers its own reader.
        wrong = takeover_loop.run_until_complete(_round_trip(config, "takeover"))
        readers_after = len(glide_client._async_pipe_reader_loops)
        # Let the idle loop resume: from here both loops read the shared pipe.
        handover.wait()
        concurrent_wrong = takeover_loop.run_until_complete(
            _round_trip(config, "concurrent", ROUNDS)
        )
        idle_thread.join(timeout=120.0)
        result_queue.put(
            (
                "OK",
                {
                    "readers_before": readers_before,
                    "readers_after": readers_after,
                    "takeover_wrong": wrong,
                    "concurrent_wrong": concurrent_wrong,
                    "idle_loop_still_running": idle_thread.is_alive(),
                    "idle_result": idle_result,
                },
            )
        )
    except BaseException as exc:  # noqa: BLE001 - reported to the parent below
        result_queue.put(("ERROR", f"{type(exc).__name__}: {exc}"))
    finally:
        takeover_loop.close()
        idle_loop.close()


def _mixed_loops_worker(config: Any, result_queue: multiprocessing.Queue) -> None:
    """Closes one registered reader loop while another stays open, then adds a client."""
    import glide.glide_client as glide_client

    idle_loop = asyncio.new_event_loop()
    takeover_loop = asyncio.new_event_loop()
    third_loop = asyncio.new_event_loop()
    try:
        idle_client = idle_loop.run_until_complete(GlideClient.create(config))
        idle_loop.run_until_complete(idle_client.set("mixed_loops", "before"))
        # The reader moves to takeover_loop while idle_loop sits open and idle.
        takeover_client = takeover_loop.run_until_complete(GlideClient.create(config))
        takeover_loop.run_until_complete(takeover_client.get("mixed_loops"))
        takeover_loop.run_until_complete(takeover_client.close())
        takeover_loop.close()
        # idle_loop is open with a live client, takeover_loop is closed: a new
        # client must not reset and drain the pipe they share.
        third_client = third_loop.run_until_complete(GlideClient.create(config))
        state = {
            "registered": glide_client._async_pipe_registered,
            "idle_loop_still_registered": (
                idle_loop in glide_client._async_pipe_reader_loops
            ),
        }
        idle_loop.run_until_complete(idle_client.set("mixed_loops", "after"))
        state["resumed_value"] = idle_loop.run_until_complete(
            idle_client.get("mixed_loops")
        )
        idle_loop.run_until_complete(idle_client.close())
        third_loop.run_until_complete(third_client.close())
        result_queue.put(("OK", state))
    except BaseException as exc:  # noqa: BLE001 - reported to the parent below
        result_queue.put(("ERROR", f"{type(exc).__name__}: {exc}"))
    finally:
        idle_loop.close()
        third_loop.close()


def _fork_child_worker(config: Any, result_queue: multiprocessing.Queue) -> None:
    """Runs two loops in a row: each has to register its own reader in the child."""
    try:
        first = asyncio.run(_round_trip(config, "fork_child_first"))
        second = asyncio.run(_round_trip(config, "fork_child_second"))
        result_queue.put(("OK", (first, second)))
    except BaseException as exc:  # noqa: BLE001 - reported to the parent below
        result_queue.put(("ERROR", f"{type(exc).__name__}: {exc}"))


def _collect(
    worker: multiprocessing.Process,
    result_queue: multiprocessing.Queue,
    hang_message: str,
) -> Tuple[str, Any]:
    """Waits for a worker's single result, failing the test if it hung."""
    worker.join(timeout=120.0)
    if worker.is_alive():
        worker.kill()
        worker.join()
        pytest.fail(hang_message)
    try:
        step, payload = result_queue.get(timeout=5.0)
    except queue.Empty:
        pytest.fail(f"worker exited without a result (exit code {worker.exitcode})")
    assert step == "OK", payload
    return step, payload


# Reader ownership is process-wide and this session has already registered a
# reader on a loop of its own, so each case runs in a worker process.
def test_running_loop_takes_the_reader_over_from_an_idle_one(request):
    """A loop that is open but not running does not keep the pipe to itself."""
    config = create_client_config(request, cluster_mode=False, request_timeout=2000)
    ctx = multiprocessing.get_context("spawn")
    result_queue: multiprocessing.Queue = ctx.Queue()
    worker = ctx.Process(target=_takeover_worker, args=(config, result_queue))
    worker.start()
    _, state = _collect(
        worker,
        result_queue,
        "worker hung: a command issued on a new event loop was never answered",
    )

    assert state["idle_result"].get("error") is None, state["idle_result"]["error"]
    assert state["takeover_wrong"] is None
    assert state["concurrent_wrong"] is None
    assert state["idle_result"]["wrong"] is None
    assert not state["idle_loop_still_running"]
    # The idle loop keeps its reader and the running loop adds its own.
    assert state["readers_before"] == 1
    assert state["readers_after"] == 2


def test_closed_reader_loop_leaves_the_open_one_registered(request):
    """A new client must not reset the pipe while a registered loop is still open."""
    config = create_client_config(request, cluster_mode=False, request_timeout=2000)
    ctx = multiprocessing.get_context("spawn")
    result_queue: multiprocessing.Queue = ctx.Queue()
    worker = ctx.Process(target=_mixed_loops_worker, args=(config, result_queue))
    worker.start()
    _, state = _collect(
        worker, result_queue, "worker hung with one registered loop closed"
    )

    assert state["registered"] is True
    assert state["idle_loop_still_registered"] is True
    assert state["resumed_value"] == b"after"


@pytest.mark.skipif(
    hasattr(sys, "_is_gil_enabled") and not sys._is_gil_enabled(),
    reason="fork() is unsupported in free-threaded Python builds",
)
def test_forked_child_registers_a_reader_for_each_of_its_loops(request):
    """A child must not take the parent's inherited loop for a live reader."""
    config = create_client_config(request, cluster_mode=False, request_timeout=2000)
    ctx = multiprocessing.get_context("fork")
    result_queue: multiprocessing.Queue = ctx.Queue()

    async def fork_while_this_loop_holds_the_reader():
        client = await GlideClient.create(config)
        await client.set("fork_parent_init", "ready")
        child = ctx.Process(target=_fork_child_worker, args=(config, result_queue))
        child.start()
        return client, child

    loop = asyncio.new_event_loop()
    try:
        client, child = loop.run_until_complete(fork_while_this_loop_holds_the_reader())
        _, (first, second) = _collect(
            child, result_queue, "forked child hung: its second loop got no reader"
        )
        assert first is None
        assert second is None
        loop.run_until_complete(client.close())
    finally:
        loop.close()
