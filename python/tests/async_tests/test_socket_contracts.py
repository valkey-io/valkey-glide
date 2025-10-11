# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from __future__ import annotations

import tempfile
from pathlib import Path
from typing import Optional, Set, cast

import anyio
import pytest
from glide_shared.config import ProtocolVersion
from glide_shared.exceptions import ClosingError

from tests.async_tests.conftest import (
    create_client,
)
from tests.async_tests.conftest import test_teardown as async_test_teardown


@pytest.mark.anyio
@pytest.mark.parametrize("cluster_mode", [False, True])
async def test_client_socket_is_removed_on_close(request, cluster_mode: bool) -> None:
    """Contract: closing the client should clean up the listener socket."""
    client = await create_client(
        request,
        cluster_mode=cluster_mode,
        protocol=ProtocolVersion.RESP3,
        lazy_connect=False,
        request_timeout=2000,
        connection_timeout=2000,
    )

    socket_path: Optional[str] = client.socket_path
    try:
        assert socket_path, "Client did not expose a socket path"
        socket_file = Path(socket_path)
        assert (
            socket_file.exists()
        ), "Socket file missing immediately after client creation"

        await client.close()
        await async_test_teardown(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
        )

        # Allow the close coroutine to finish tearing down background tasks.
        await anyio.sleep(0)

        assert (
            not socket_file.exists()
        ), "Expected client.close() to remove its unix domain socket, but the file is still present."
    finally:
        if socket_path:
            Path(socket_path).unlink(missing_ok=True)


@pytest.mark.anyio
@pytest.mark.parametrize("cluster_mode", [False, True])
async def test_repeated_clients_do_not_leave_stale_sockets(
    request, cluster_mode: bool
) -> None:
    """Contract: repeatedly constructing clients should not leak socket files."""
    tmp_dir = Path(tempfile.gettempdir())
    baseline: Set[Path] = set(tmp_dir.glob("glide-socket-*.sock"))

    created_paths: Set[Path] = set()
    try:
        for _ in range(3):
            client = await create_client(
                request,
                cluster_mode=cluster_mode,
                protocol=ProtocolVersion.RESP3,
                lazy_connect=False,
                request_timeout=2000,
                connection_timeout=2000,
            )
            socket_path = cast(Optional[str], client.socket_path)
            assert socket_path, "Client did not expose a socket path"
            socket_file = Path(socket_path)
            created_paths.add(socket_file)
            assert (
                socket_file.exists()
            ), "Socket file missing immediately after client creation"

            await client.close()
            await async_test_teardown(
                request,
                cluster_mode=cluster_mode,
                protocol=ProtocolVersion.RESP3,
            )

            await anyio.sleep(0)

        current = set(tmp_dir.glob("glide-socket-*.sock"))
        leaked = {path for path in current if path not in baseline}
        assert (
            not leaked
        ), f"Found leaked glide listener sockets: {sorted(str(p) for p in leaked)}"
    finally:
        for path in created_paths:
            path.unlink(missing_ok=True)


@pytest.mark.anyio
@pytest.mark.parametrize("cluster_mode", [False, True])
async def test_client_survives_tmp_socket_cleanup(request, cluster_mode: bool) -> None:
    """Contract: tmp directory cleanup should not terminate an active client session."""
    client = await create_client(
        request,
        cluster_mode=cluster_mode,
        protocol=ProtocolVersion.RESP3,
        lazy_connect=False,
        request_timeout=2000,
        connection_timeout=2000,
    )

    socket_path = cast(Optional[str], client.socket_path)
    assert socket_path, "Client did not expose a socket path"
    socket_file = Path(socket_path)
    assert socket_file.exists(), "Socket file missing immediately after client creation"

    tmp_dir = Path(tempfile.gettempdir())
    relative_to_tmp = socket_file.is_relative_to(tmp_dir)

    closing_error: Optional[ClosingError] = None
    stop_event = anyio.Event()

    async def ping_loop() -> None:
        nonlocal closing_error
        try:
            with anyio.move_on_after(1.0):
                while True:
                    await client.ping()
                    await anyio.sleep(0)
        except ClosingError as exc:  # pragma: no cover - captured for assertion
            closing_error = exc
        finally:
            stop_event.set()

    async def cleanup_loop() -> None:
        try:
            # Mimic an automated /tmp cleanup job.
            await anyio.sleep(0.1)
            if relative_to_tmp and not cluster_mode:
                socket_file.unlink(missing_ok=True)
        finally:
            stop_event.set()

    try:
        async with anyio.create_task_group() as tg:
            tg.start_soon(ping_loop)
            tg.start_soon(cleanup_loop)
            await stop_event.wait()
            tg.cancel_scope.cancel()
    finally:
        await client.close()
        await async_test_teardown(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
        )

    if cluster_mode:
        assert (
            closing_error is None
        ), "Cluster client experienced ClosingError despite socket remaining registered."
    else:
        assert (
            closing_error is None
        ), "Client request loop aborted due to socket cleanup; expected the session to remain healthy."

    # If the socket lived in /tmp, ensure the test does not leave residue.
    if relative_to_tmp and not cluster_mode:
        socket_file.unlink(missing_ok=True)


@pytest.mark.anyio
@pytest.mark.parametrize("cluster_mode", [False, True])
async def test_closing_one_client_does_not_break_other(
    request, cluster_mode: bool
) -> None:
    """Contract: closing a single client must not disrupt other clients sharing the socket."""

    client_a = await create_client(
        request,
        cluster_mode=cluster_mode,
        protocol=ProtocolVersion.RESP3,
        lazy_connect=False,
        request_timeout=2000,
        connection_timeout=2000,
    )

    client_b = await create_client(
        request,
        cluster_mode=cluster_mode,
        protocol=ProtocolVersion.RESP3,
        lazy_connect=False,
        request_timeout=2000,
        connection_timeout=2000,
    )

    shared_socket: Optional[Path] = None
    try:
        assert client_a.socket_path and client_b.socket_path
        assert (
            client_a.socket_path == client_b.socket_path
        ), "Expected clients to share the same listener socket"
        shared_socket = Path(client_a.socket_path)
        assert shared_socket.exists(), "Shared socket missing before interactions"

        await client_a.ping()
        await client_b.ping()

        await client_a.close()
        await anyio.sleep(0)

        # Remaining client should continue operating normally
        await client_b.ping()

        assert (
            shared_socket.exists()
        ), "Shared socket should remain while other clients are alive"
    finally:
        await client_b.close()
        await async_test_teardown(
            request,
            cluster_mode=cluster_mode,
            protocol=ProtocolVersion.RESP3,
        )

    if shared_socket is not None:
        await anyio.sleep(0)
