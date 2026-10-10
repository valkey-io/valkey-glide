# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import threading
from concurrent.futures import Future, ThreadPoolExecutor
from types import SimpleNamespace
from unittest.mock import Mock

import anyio
import pytest
from glide import glide_client as pipe


@pytest.fixture
def pipe_client(monkeypatch):
    client = SimpleNamespace(
        _is_closed=False,
        _ffi=Mock(),
        _lib=Mock(),
        _loop=None,
        _lock=threading.Lock(),
        _pending_futures={1: Future(), 2: Future()},
    )
    monkeypatch.setattr(pipe, "_client_registry", {7: client})
    monkeypatch.setattr(pipe, "_pipe_remainder", b"")
    monkeypatch.setattr(pipe, "_FREE_THREADED", False)
    monkeypatch.setattr(pipe, "_async_pipe_loop", None)
    monkeypatch.setattr(pipe, "_async_pipe_read_fd", 123)
    monkeypatch.setattr(pipe, "_c_parse_response", Mock(return_value=("OK", None)))
    monkeypatch.setattr(pipe.ClientLogger, "log", Mock())
    return client


@pytest.mark.parametrize("workers", [False, True])
@pytest.mark.parametrize("require_lock", [False, True])
@pytest.mark.parametrize("split_frame", [False, True])
@pytest.mark.parametrize("failure", ["parse", "arena", "error_string", "error_type"])
def test_response_failure_preserves_later_frames(
    monkeypatch, pipe_client, failure, split_frame, require_lock, workers
):
    client = pipe_client
    first, second = client._pending_futures.values()
    error = RuntimeError("frame failed")
    response_ptr = 100
    if failure == "parse":
        pipe._c_parse_response.side_effect = [error, ("OK", None)]
    elif failure == "arena":
        client._lib.free_response_arena.side_effect = [error, None]
    else:
        response_ptr = 0
        client._ffi.string.return_value = b"request failed"
        if failure == "error_string":
            client._lib.free_pipe_error_string.side_effect = error
        else:
            monkeypatch.setattr(
                pipe, "get_request_error_class", Mock(side_effect=error)
            )
    first_frame = pipe._FRAME_STRUCT.pack(7, 1, response_ptr, 101)
    second_frame = pipe._FRAME_STRUCT.pack(7, 2, 200, 201)
    reads = (
        [first_frame + second_frame[:13], second_frame[13:]]
        if split_frame
        else [first_frame + second_frame]
    )
    monkeypatch.setattr(
        pipe.os,
        "read",
        Mock(side_effect=reads),
    )
    monkeypatch.setattr(pipe, "_PENDING_FUTURES_REQUIRE_LOCK", require_lock)
    with ThreadPoolExecutor(max_workers=1) as pool:
        monkeypatch.setattr(pipe, "_FREE_THREADED", workers)
        monkeypatch.setattr(pipe, "_response_thread_pool", pool)
        assert pipe._on_async_pipe_readable()
        if split_frame:
            assert pipe._pipe_remainder == second_frame[:13]
            assert pipe._on_async_pipe_readable()
    assert first.exception(timeout=1) is error
    assert second.result(timeout=1) == "OK"
    assert client._pending_futures == {}
    assert pipe._pipe_remainder == b""
    assert client._lib.free_response_arena.call_count == (2 if response_ptr else 1)
    assert client._lib.free_pipe_error_string.call_count == (0 if response_ptr else 1)


@pytest.mark.parametrize("pointer_mode", [False, True])
def test_push_failure_preserves_following_response(
    monkeypatch, pipe_client, pointer_mode
):
    first = pipe_client._pending_futures[1]
    payload = b"payload"
    if pointer_mode:
        frame = pipe._FRAME_STRUCT.pack(7, pipe._PUBSUB_SENTINEL, 100, (1 << 63) | 7)
        pipe_client._lib.free_pubsub_pointer_payload.side_effect = RuntimeError(
            "free failed"
        )
    else:
        frame = (
            pipe._FRAME_STRUCT.pack(7, pipe._PUBSUB_SENTINEL, len(payload), 0) + payload
        )
        monkeypatch.setattr(
            pipe, "_handle_inline_pubsub", Mock(side_effect=RuntimeError("push failed"))
        )
    monkeypatch.setattr(
        pipe.os,
        "read",
        Mock(return_value=frame + pipe._FRAME_STRUCT.pack(7, 1, 200, 201)),
    )
    assert pipe._on_async_pipe_readable()
    assert first.result(timeout=1) == "OK"
    if pointer_mode:
        pipe_client._lib.free_pubsub_pointer_payload.assert_called_once()
    assert pipe._pipe_remainder == b""
    pipe.ClientLogger.log.assert_called()


def test_incomplete_inline_payload_is_retained(monkeypatch, pipe_client):
    payload = b"payload"
    frame = pipe._FRAME_STRUCT.pack(7, pipe._PUBSUB_SENTINEL, len(payload), 0) + payload
    handler = Mock()
    monkeypatch.setattr(pipe, "_handle_inline_pubsub", handler)
    monkeypatch.setattr(pipe.os, "read", Mock(side_effect=[frame[:-3], frame[-3:]]))
    assert pipe._on_async_pipe_readable()
    assert pipe._pipe_remainder == frame[:-3]
    handler.assert_not_called()
    assert pipe._on_async_pipe_readable()
    handler.assert_called_once_with(pipe_client, payload)
    assert pipe._pipe_remainder == b""


def test_worker_submission_failure_handles_frame_inline(monkeypatch, pipe_client):
    first = pipe_client._pending_futures[1]
    monkeypatch.setattr(pipe, "_FREE_THREADED", True)
    monkeypatch.setattr(
        pipe,
        "_response_thread_pool",
        Mock(submit=Mock(side_effect=RuntimeError("closed pool"))),
    )
    monkeypatch.setattr(
        pipe.os, "read", Mock(return_value=pipe._FRAME_STRUCT.pack(7, 1, 100, 101))
    )
    assert pipe._on_async_pipe_readable()
    assert first.result(timeout=1) == "OK"
    pipe_client._lib.free_response_arena.assert_called_once()
    pipe.ClientLogger.log.assert_called_once()


def test_failed_worker_start_does_not_process_frame_twice(monkeypatch, pipe_client):
    first, second = pipe_client._pending_futures.values()
    start_thread = threading.Thread.start
    attempts = 0

    def fail_first_start(thread):
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise RuntimeError("worker start failed")
        start_thread(thread)

    monkeypatch.setattr(threading.Thread, "start", fail_first_start)
    frames = pipe._FRAME_STRUCT.pack(7, 1, 100, 101) + pipe._FRAME_STRUCT.pack(
        7, 2, 200, 201
    )
    monkeypatch.setattr(pipe.os, "read", Mock(return_value=frames))
    with ThreadPoolExecutor(max_workers=1) as pool:
        monkeypatch.setattr(pipe, "_FREE_THREADED", True)
        monkeypatch.setattr(pipe, "_response_thread_pool", pool)
        assert pipe._on_async_pipe_readable()
    assert first.result(timeout=1) == "OK"
    assert second.result(timeout=1) == "OK"
    assert [args[0] for args, _ in pipe._c_parse_response.call_args_list] == [100, 200]
    assert pipe_client._lib.free_response_arena.call_count == 2
    assert pipe_client._pending_futures == {}
    pipe.ClientLogger.log.assert_called_once()


def test_failed_future_dispatch_does_not_block_other_clients(monkeypatch, pipe_client):
    closed_client = SimpleNamespace(
        **{**vars(pipe_client), "_pending_futures": {1: Future()}, "_loop": Mock()}
    )
    closed_client._loop.call_soon_threadsafe.side_effect = RuntimeError("closed loop")
    pipe._client_registry[8] = closed_client
    second = pipe_client._pending_futures[2]
    frames = pipe._FRAME_STRUCT.pack(8, 1, 100, 101) + pipe._FRAME_STRUCT.pack(
        7, 2, 200, 201
    )
    monkeypatch.setattr(pipe.os, "read", Mock(return_value=frames))
    assert pipe._on_async_pipe_readable()
    assert closed_client._pending_futures == {}
    assert second.result(timeout=1) == "OK"
    assert pipe_client._lib.free_response_arena.call_count == 2


@pytest.mark.parametrize("closed", [False, True])
def test_completed_requests_still_free_native_response(pipe_client, closed):
    future = pipe_client._pending_futures.pop(1)
    future.cancel()
    pipe_client._is_closed = closed
    pipe._handle_pipe_frame(pipe_client, 1, 100, 101)
    pipe_client._lib.free_response_arena.assert_called_once()
    assert future.cancelled()


@pytest.mark.parametrize("pointer_mode", [False, True])
def test_orphaned_frames_free_native_memory(pipe_client, pointer_mode):
    if pointer_mode:
        pipe._handle_pipe_frame(None, pipe._PUBSUB_SENTINEL, 100, (1 << 63) | 7)
        pipe_client._lib.free_pubsub_pointer_payload.assert_called_once()
    else:
        pipe._handle_pipe_frame(None, 1, 100, 101)
        pipe_client._lib.free_response_arena.assert_called_once()


def test_windows_reader_survives_unexpected_read_failure(monkeypatch, pipe_client):
    read = Mock(side_effect=[RuntimeError("read failed"), True, False])
    monkeypatch.setattr(pipe, "_on_async_pipe_readable", read)
    pipe._windows_pipe_reader(123)
    assert read.call_count == 3
    pipe.ClientLogger.log.assert_called_once()


@pytest.fixture(params=["asyncio", "trio"])
def anyio_backend(request):
    return request.param


@pytest.mark.anyio
async def test_failed_frame_wakes_owning_loop(monkeypatch, pipe_client, anyio_backend):
    if anyio_backend == "asyncio":
        pipe_client._loop = asyncio.get_running_loop()
    future = pipe._get_new_future_instance()
    pipe_client._pending_futures = {1: future}
    pipe_client._lib.free_response_arena.side_effect = RuntimeError("cleanup failed")
    with anyio.fail_after(5):
        await anyio.to_thread.run_sync(
            pipe._handle_pipe_frame, pipe_client, 1, 100, 101
        )
        with pytest.raises(RuntimeError, match="cleanup failed"):
            await future
    assert pipe_client._pending_futures == {}
