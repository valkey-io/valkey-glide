# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
import struct
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
        _trio_token=None,
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


@pytest.fixture
async def pubsub_client(monkeypatch, pipe_client, anyio_backend):
    client = pipe.BaseClient(Mock())
    client._lib = Mock()
    client._is_asyncio = anyio_backend == "asyncio"
    if client._is_asyncio:
        client._loop = asyncio.get_running_loop()
    client._pipe_client_id = 7
    monkeypatch.setattr(
        pipe, "os", SimpleNamespace(name="nt", getpid=pipe.os.getpid, read=Mock())
    )
    monkeypatch.setattr(pipe, "_async_pipe_registered", True)
    monkeypatch.setattr(pipe, "_trio_pipe_token", None)
    client._setup_pipe()
    return client


@pytest.mark.anyio
@pytest.mark.parametrize("pointer_mode", [False, True])
@pytest.mark.parametrize("callback_fails", [False, True])
async def test_windows_pubsub_callback_runs_on_owning_loop(
    pubsub_client, pointer_mode, callback_fails
):
    client = pubsub_client
    owner_thread = threading.get_ident()
    received = []
    delivered = anyio.Event()
    context = object()

    def callback(message, callback_context):
        received.append((threading.get_ident(), message, callback_context))
        if len(received) == 2:
            delivered.set()
        elif callback_fails:
            raise RuntimeError("callback failed")

    client.config._get_pubsub_callback_and_context.return_value = (callback, context)
    frames = b""
    buffers = []
    for message in (b"first", b"second"):
        payload = struct.pack("=I", 3) + b"".join(
            struct.pack("=I", len(value)) + value
            for value in (message, b"channel", b"")
        )
        if pointer_mode:
            buffer = client._ffi.new("uint8_t[]", payload)
            buffers.append(buffer)
            frames += pipe._FRAME_STRUCT.pack(
                7,
                pipe._PUBSUB_SENTINEL,
                int(client._ffi.cast("uintptr_t", buffer)),
                (1 << 63) | len(payload),
            )
        else:
            frames += (
                pipe._FRAME_STRUCT.pack(7, pipe._PUBSUB_SENTINEL, len(payload), 0)
                + payload
            )

    def free_payload(pointer, length):
        client._ffi.buffer(pointer, length)[:] = b"\0" * length

    client._lib.free_pubsub_pointer_payload.side_effect = free_payload
    pipe.os.read.side_effect = [frames, b""]
    # Keep the owning loop parked until the reader has copied and freed payloads.
    with ThreadPoolExecutor(max_workers=1) as pool:
        pool.submit(pipe._windows_pipe_reader, 123).result(timeout=5)
    assert not received
    assert client._lib.free_pubsub_pointer_payload.call_count == (
        2 if pointer_mode else 0
    )
    with anyio.fail_after(5):
        await delivered.wait()
    assert received == [
        (owner_thread, pipe.PubSubMsg(message, b"channel", None), context)
        for message in (b"first", b"second")
    ]
    if callback_fails:
        pipe.ClientLogger.log.assert_called_once()
        assert "callback failed" in pipe.ClientLogger.log.call_args.args[2]
    else:
        pipe.ClientLogger.log.assert_not_called()


def test_queued_pubsub_is_discarded_after_client_close():
    client = pipe.BaseClient(Mock())
    client._is_closed = True
    payload = struct.pack("=II", 3, 0) + struct.pack("=II", 0, 0)
    pipe._handle_inline_pubsub(client, payload)
    client.config._get_pubsub_callback_and_context.assert_not_called()


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


@pytest.mark.parametrize("require_lock", [False, True])
@pytest.mark.parametrize("replacement", [None, "replacement"])
@pytest.mark.parametrize("matches", [False, True])
def test_pending_future_replacement_uses_reader_lock(
    monkeypatch, pipe_client, require_lock, replacement, matches
):
    class CheckedDict(dict):
        def get(self, key):
            assert pipe_client._lock.locked() == require_lock
            return super().get(key)

        def __setitem__(self, key, value):
            assert pipe_client._lock.locked() == require_lock
            super().__setitem__(key, value)

        def __delitem__(self, key):
            assert pipe_client._lock.locked() == require_lock
            super().__delitem__(key)

    original = pipe_client._pending_futures[1]
    pipe_client._pending_futures = CheckedDict({1: original})
    monkeypatch.setattr(pipe, "_PENDING_FUTURES_REQUIRE_LOCK", require_lock)
    pipe.BaseClient._replace_pending_future(
        pipe_client, 1, original if matches else Future(), replacement
    )
    assert pipe_client._pending_futures == (
        ({1: replacement} if replacement is not None else {})
        if matches
        else {1: original}
    )


@pytest.fixture
def scope_client(monkeypatch, pipe_client):
    client = pipe.BaseClient(Mock())
    client._lib = Mock(glide_scope_acquire_pipe=Mock(return_value=0))
    client._core_client = client._ffi.cast("void*", 77)
    client._conn_req_bytes = b"request"
    client._pipe_client_id = 7
    monkeypatch.setattr(pipe, "_PENDING_FUTURES_REQUIRE_LOCK", True)
    return client


@pytest.mark.anyio
@pytest.mark.parametrize("delivery", ["after_cancel", "queued"])
@pytest.mark.parametrize("failed", [False, True])
@pytest.mark.parametrize("workers", [False, True])
async def test_cancelled_scope_releases_grant_across_dispatch(
    monkeypatch, scope_client, anyio_backend, delivery, failed, workers
):
    client = scope_client
    future = pipe._get_new_future_instance()
    monkeypatch.setattr(pipe, "_get_new_future_instance", lambda: future)
    monkeypatch.setattr(pipe, "_FREE_THREADED", workers)
    callbacks = []

    def enqueue(callback, *args):
        callbacks.append((callback, args))

    if anyio_backend == "asyncio":
        client._loop = SimpleNamespace(call_soon_threadsafe=enqueue)
    else:
        future._token = SimpleNamespace(run_sync_soon=enqueue)

    def flush_callbacks():
        while callbacks:
            callback, args = callbacks.pop(0)
            callback(*args)

    finished = anyio.Event()

    async def acquire(*, task_status):
        with anyio.CancelScope() as cancel_scope:
            task_status.started(cancel_scope)
            await client.scoped_connection()
            pytest.fail("Cancelled acquire must not return a scope")
        finished.set()

    result = RuntimeError("acquire failed") if failed else 42
    with anyio.fail_after(5):
        async with anyio.create_task_group() as tasks:
            cancel_scope = await tasks.start(acquire)
            assert 1 in client._pending_futures
            if delivery != "after_cancel":
                await anyio.to_thread.run_sync(
                    pipe._complete_pipe_request, client, 1, result
                )
                assert client._pending_futures == {}
                assert callbacks
            cancel_scope.cancel()
            await finished.wait()
            if delivery == "after_cancel":
                await anyio.to_thread.run_sync(
                    pipe._complete_pipe_request, client, 1, result
                )
            flush_callbacks()
    assert client._pending_futures == {}
    if failed:
        client._lib.glide_scope_release.assert_not_called()
    else:
        client._lib.glide_scope_release.assert_called_once_with(42, 77)


@pytest.mark.anyio
@pytest.mark.parametrize("failed", [False, True])
async def test_abandon_delivered_scope_releases_once(failed):
    future = pipe._get_new_future_instance()
    lib = Mock()
    response = pipe._ScopeAcquireResponse(future, lib, 77)
    if failed:
        response.set_exception(RuntimeError("acquire failed"))
        with pytest.raises(RuntimeError, match="acquire failed"):
            await future
    else:
        response.set_result(42)
        assert await future == 42
    assert response.done()
    response._abandon()
    response._abandon()
    if failed:
        lib.glide_scope_release.assert_not_called()
    else:
        lib.glide_scope_release.assert_called_once_with(42, 77)


@pytest.mark.anyio
async def test_scope_queue_failure_removes_pending_response(scope_client):
    scope_client._lib.glide_scope_acquire_pipe.return_value = 1
    with pytest.raises(RuntimeError, match="Failed to queue scope acquire"):
        await scope_client.scoped_connection()
    assert scope_client._pending_futures == {}
    scope_client._lib.glide_scope_release.assert_not_called()


@pytest.mark.anyio
async def test_close_fails_pending_scope(scope_client):
    registered = anyio.Event()

    def queue_acquire(*args):
        registered.set()
        return 0

    scope_client._lib.glide_scope_acquire_pipe.side_effect = queue_acquire

    async def acquire():
        with pytest.raises(pipe.ConnectionError, match="closed for test"):
            await scope_client.scoped_connection()

    with anyio.fail_after(5):
        async with anyio.create_task_group() as tasks:
            tasks.start_soon(acquire)
            await registered.wait()
            await scope_client.close("closed for test")
    assert scope_client._pending_futures == {}
    scope_client._lib.glide_scope_release.assert_not_called()


@pytest.mark.anyio
@pytest.mark.parametrize("failed", [False, True])
async def test_scope_grant_wakes_owning_loop(scope_client, anyio_backend, failed):
    client = scope_client
    if anyio_backend == "asyncio":
        client._loop = asyncio.get_running_loop()
    registered = anyio.Event()

    def queue_acquire(*args):
        registered.set()
        return 0

    client._lib.glide_scope_acquire_pipe.side_effect = queue_acquire

    async def complete(*, task_status):
        await registered.wait()
        await anyio.to_thread.run_sync(
            pipe._complete_pipe_request,
            client,
            1,
            RuntimeError("acquire failed") if failed else 42,
        )
        task_status.started()

    async def acquire():
        if failed:
            with pytest.raises(RuntimeError, match="acquire failed"):
                await client.scoped_connection()
        else:
            scope = await client.scoped_connection()
            assert scope.scope_id == 42

    with anyio.fail_after(5):
        async with anyio.create_task_group() as tasks:
            tasks.start_soon(acquire)
            await tasks.start(complete)
    assert client._pending_futures == {}
    client._lib.glide_scope_release.assert_not_called()
