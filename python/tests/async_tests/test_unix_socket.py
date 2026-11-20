# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Tests for async client connections over a Unix domain socket.
The server is started through cluster_manager.py with TCP disabled (`--port 0`).
"""

import sys
from typing import List

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

from tests.utils.utils import assert_unix_socket_client, wait_for

pytestmark = pytest.mark.skipif(
    sys.platform == "win32", reason="Unix domain sockets are not supported on Windows"
)


def unix_socket_config(path: str, **kwargs) -> GlideClientConfiguration:
    return GlideClientConfiguration(
        [NodeAddress(unix_socket_path=path)],
        request_timeout=2000,
        **kwargs,
    )


@pytest.mark.anyio
class TestUnixSocket:
    async def test_standalone_cluster_is_reached_over_sockets(
        self, request: pytest.FixtureRequest
    ):
        if not request.config.getoption("--unix-socket"):
            pytest.skip("Runs only with --unix-socket")
        for address in pytest.standalone_cluster.nodes_addr:  # type: ignore[attr-defined]
            client = await GlideClient.create(
                GlideClientConfiguration(
                    [address], read_only=True, request_timeout=2000
                )
            )
            try:
                assert await client.config_get(["port"]) == {b"port": b"0"}
                assert_unix_socket_client(
                    await client.custom_command(["CLIENT", "INFO"])
                )
            finally:
                await client.close()

    async def test_connect_over_unix_socket(self, unix_socket_path: str):
        client = await GlideClient.create(
            unix_socket_config(unix_socket_path, database_id=2)
        )
        try:
            assert await client.set("uds-key", "uds-value") == OK
            assert await client.get("uds-key") == b"uds-value"
            info = assert_unix_socket_client(
                await client.custom_command(["CLIENT", "INFO"])
            )
            assert " db=2 " in info, info
        finally:
            await client.close()

    async def test_scoped_connection_over_unix_socket(
        self, request: pytest.FixtureRequest, unix_socket_path: str
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
        client = await GlideClient.create(unix_socket_config(unix_socket_path))
        try:
            async with await client.scoped_connection() as scope:
                assert_unix_socket_client(await scope.execute_command("CLIENT", "INFO"))
                await scope.set("uds-scope-key", "scoped")
                assert await scope.get("uds-scope-key") == "scoped"
            assert await client.get("uds-scope-key") == b"scoped"
        finally:
            await client.close()

    async def test_monitor_over_unix_socket(self, unix_socket_path: str):
        received: List[MonitorMsg] = []
        monitor = await MonitorClient.create(
            unix_socket_config(unix_socket_path), callback=received.append
        )
        try:
            client = await GlideClient.create(unix_socket_config(unix_socket_path))
            try:
                await client.set("uds-monitor-key", "value")

                async def seen() -> bool:
                    return any(
                        m.command.upper() == "SET"
                        and m.client_addr == f"unix:{unix_socket_path}"
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
