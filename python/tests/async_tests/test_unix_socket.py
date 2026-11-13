# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Tests for async client connections over a Unix domain socket.
Each test starts its own server that listens only on the socket (`--port 0`).
"""

import sys
from typing import Iterator, List

import pytest
import sniffio
from glide import GlideClient, MonitorClient
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import (
    AdvancedGlideClientConfiguration,
    GlideClientConfiguration,
    NodeAddress,
    NodeDiscoveryMode,
)
from glide_shared.constants import OK
from glide_shared.exceptions import ClosingError, ConfigurationError

from tests.utils.unix_socket_server import UnixSocketServer
from tests.utils.utils import wait_for

pytestmark = pytest.mark.skipif(
    sys.platform == "win32", reason="Unix domain sockets are not supported on Windows"
)


@pytest.fixture
def unix_socket_server() -> Iterator[UnixSocketServer]:
    server = UnixSocketServer()
    yield server
    server.stop()


def unix_socket_config(server: UnixSocketServer, **kwargs) -> GlideClientConfiguration:
    return GlideClientConfiguration(
        [NodeAddress(unix_socket_path=server.socket_path)],
        request_timeout=2000,
        **kwargs,
    )


async def assert_uses_socket(client: GlideClient) -> bytes:
    """Asserts that the server flags this client's connection as a Unix socket (`U`)."""
    info = await client.custom_command(["CLIENT", "INFO"])
    assert isinstance(info, bytes)
    flags = next(f for f in info.split() if f.startswith(b"flags="))
    assert b"U" in flags[len(b"flags=") :], info
    return info


@pytest.mark.anyio
class TestUnixSocket:
    async def test_connect_over_unix_socket(self, unix_socket_server: UnixSocketServer):
        client = await GlideClient.create(
            unix_socket_config(unix_socket_server, database_id=2)
        )
        try:
            assert await client.set("uds-key", "uds-value") == OK
            assert await client.get("uds-key") == b"uds-value"
            info = await assert_uses_socket(client)
            assert b" db=2 " in info, info
        finally:
            await client.close()

    async def test_scoped_connection_over_unix_socket(
        self, request: pytest.FixtureRequest, unix_socket_server: UnixSocketServer
    ):
        if sniffio.current_async_library() == "trio":
            request.applymarker(
                pytest.mark.xfail(
                    reason="scoped_connection() calls asyncio.get_running_loop(), "
                    "so it does not run on trio yet",
                    raises=RuntimeError,
                    strict=True,
                )
            )
        client = await GlideClient.create(unix_socket_config(unix_socket_server))
        try:
            async with await client.scoped_connection() as scope:
                await scope.set("uds-scope-key", "scoped")
                assert await scope.get("uds-scope-key") == "scoped"
            assert await client.get("uds-scope-key") == b"scoped"
        finally:
            await client.close()

    async def test_monitor_over_unix_socket(self, unix_socket_server: UnixSocketServer):
        received: List[MonitorMsg] = []
        monitor = await MonitorClient.create(
            unix_socket_config(unix_socket_server), callback=received.append
        )
        try:
            client = await GlideClient.create(unix_socket_config(unix_socket_server))
            try:
                await client.set("uds-monitor-key", "value")

                async def seen() -> bool:
                    return any(
                        m.command.upper() == "SET"
                        and m.client_addr == f"unix:{unix_socket_server.socket_path}"
                        for m in received
                    )

                await wait_for(seen, "SET over the socket not seen by MONITOR")
            finally:
                await client.close()
        finally:
            await monitor.stop()


SOCKET = "/tmp/glide-uds-config-error.sock"


@pytest.mark.anyio
class TestUnixSocketErrors:
    """
    Requests the core rejects before connecting raise ConfigurationError, the same type the
    client's own Unix socket checks raise. No server is needed.
    """

    @pytest.mark.parametrize(
        "addresses, kwargs, match",
        [
            pytest.param(
                [NodeAddress(unix_socket_path="relative.sock")],
                {},
                "must be absolute",
                id="relative_path",
            ),
            pytest.param(
                [NodeAddress(unix_socket_path=SOCKET), NodeAddress("localhost", 6379)],
                {},
                "cannot be combined with TCP addresses",
                id="mixed_addresses",
            ),
            pytest.param(
                [NodeAddress(unix_socket_path=SOCKET)],
                {"node_discovery_mode": NodeDiscoveryMode.DISCOVER_ALL},
                "DiscoverAll",
                id="discover_all",
            ),
        ],
    )
    async def test_core_rejection_raises_configuration_error(
        self, addresses, kwargs, match
    ):
        config = GlideClientConfiguration(addresses, **kwargs)
        with pytest.raises(ConfigurationError, match=match):
            await GlideClient.create(config)

    async def test_monitor_core_rejection_raises_configuration_error(self):
        config = GlideClientConfiguration(
            [NodeAddress(unix_socket_path="relative.sock")]
        )
        with pytest.raises(ConfigurationError, match="must be absolute"):
            await MonitorClient.create(config, callback=lambda _: None)

    async def test_unreachable_socket_still_raises_closing_error(self):
        # Failing to connect is not a configuration error, so its type is unchanged.
        config = GlideClientConfiguration(
            [NodeAddress(unix_socket_path="/nonexistent-dir/glide.sock")],
            advanced_config=AdvancedGlideClientConfiguration(connection_timeout=500),
        )
        with pytest.raises(ClosingError):
            await GlideClient.create(config)
