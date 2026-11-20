# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Regression tests for issue #7773.

``GlideClient.create()`` used to run the blocking CFFI ``create_client`` call
on the event-loop thread, stalling every coroutine in the process for the
entire connection attempt. These tests pin the offloaded behavior: while a
create attempt is in flight, a heartbeat coroutine on the same event loop
must keep running.
"""

import asyncio
import socket
import time
from typing import Awaitable, Callable, List

import pytest
from glide.glide_client import GlideClient
from glide_shared.config import (
    AdvancedBaseClientConfiguration,
    GlideClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    TlsAdvancedConfiguration,
)
from glide_shared.exceptions import ClosingError as GlideClosingError
from glide_shared.exceptions import ConnectionError as GlideConnectionError

pytestmark = pytest.mark.asyncio

# Heartbeat cadence: small enough that a 300 ms loop stall produces a gap far
# above it, large enough that CI jitter does not produce false positives.
HEARTBEAT_INTERVAL_S = 0.005
# The unfixed code blocks the loop for the full connection timeout; the fixed
# code only competes with the worker thread for the GIL. Anything below half
# the connection timeout distinguishes the two with a wide margin.
MAX_ALLOWED_LOOP_GAP_S = 0.15
CONNECTION_TIMEOUT_MS = 300


def _silent_tls_listener() -> socket.socket:
    """A TCP listener that accepts nothing and answers nothing.

    The kernel completes the TCP handshake (the SYN lands in the listen
    backlog), so the client reaches its TLS handshake, which never completes
    because nobody reads the ClientHello. The connect therefore stalls for the
    full ``connection_timeout`` — deterministically, with no server involved.
    """
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("127.0.0.1", 0))
    listener.listen(1)
    return listener


def _slow_connect_config(port: int) -> GlideClientConfiguration:
    return GlideClientConfiguration(
        addresses=[NodeAddress("127.0.0.1", port)],
        use_tls=True,
        protocol=ProtocolVersion.RESP3,
        advanced_config=AdvancedBaseClientConfiguration(
            connection_timeout=CONNECTION_TIMEOUT_MS,
            tls_config=TlsAdvancedConfiguration(use_insecure_tls=True),
        ),
    )


async def _measure_max_loop_gap(
    run_attempt: Callable[[], Awaitable[None]],
) -> float:
    """Run a heartbeat alongside ``run_attempt``; return the max loop gap.

    The heartbeat ticks every ``HEARTBEAT_INTERVAL_S`` before the attempt
    starts, so the blockage shows up as one gap between consecutive ticks
    that is roughly as long as the stall.
    """
    gaps: List[float] = []
    last = time.perf_counter()
    running = True

    async def heartbeat() -> None:
        nonlocal last
        while running:
            await asyncio.sleep(HEARTBEAT_INTERVAL_S)
            now = time.perf_counter()
            gaps.append(now - last)
            last = now

    heartbeat_task = asyncio.ensure_future(heartbeat())
    # Let the heartbeat establish its cadence before the attempt starts.
    await asyncio.sleep(0.02)
    await run_attempt()
    running = False
    await heartbeat_task
    return max(gaps, default=0.0)


class TestCreateDoesNotBlockEventLoop:
    async def test_create_does_not_block_loop_for_connection_duration(self):
        listener = _silent_tls_listener()
        port = listener.getsockname()[1]
        try:
            config = _slow_connect_config(port)

            async def attempt():
                try:
                    client = await GlideClient.create(config)
                except Exception:
                    # Expected: the connect fails once the connection timeout
                    # fires. What matters is that the loop stayed free while
                    # the attempt was in flight.
                    pass
                else:
                    # Should not happen against the silent listener, but make
                    # the test robust if it ever does.
                    await client.close()

            max_gap = await _measure_max_loop_gap(attempt)

            assert max_gap < MAX_ALLOWED_LOOP_GAP_S, (
                f"Event loop stalled for {max_gap * 1000:.0f} ms during "
                f"GlideClient.create() (allowed < {MAX_ALLOWED_LOOP_GAP_S * 1000:.0f} ms); "
                "the blocking create_client call is not off the loop thread"
            )
        finally:
            listener.close()

    async def test_create_connection_error_propagates(self):
        """The offloaded create raises the same connection error as before."""
        # A port with no listener: the OS refuses the connection immediately.
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as socket_holder:
            socket_holder.bind(("127.0.0.1", 0))
            refused_port = socket_holder.getsockname()[1]
        config = GlideClientConfiguration(
            addresses=[NodeAddress("127.0.0.1", refused_port)],
            protocol=ProtocolVersion.RESP3,
            advanced_config=AdvancedBaseClientConfiguration(
                connection_timeout=CONNECTION_TIMEOUT_MS
            ),
        )
        with pytest.raises((GlideClosingError, GlideConnectionError)):
            await GlideClient.create(config)
