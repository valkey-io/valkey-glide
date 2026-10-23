# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Tests for sync client connections over a Unix domain socket.
Each test starts its own server that listens only on the socket (`--port 0`).
"""

import sys
from typing import Iterator, List

import pytest
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import (
    GlideClientConfiguration,
    NodeAddress,
)
from glide_shared.constants import OK
from glide_sync import MonitorClient
from glide_sync.glide_client import GlideClient

from tests.utils.unix_socket_server import UnixSocketServer
from tests.utils.utils import sync_wait_for

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


def assert_uses_socket(client: GlideClient) -> bytes:
    """Asserts that the server flags this client's connection as a Unix socket (`U`)."""
    info = client.custom_command(["CLIENT", "INFO"])
    assert isinstance(info, bytes)
    flags = next(f for f in info.split() if f.startswith(b"flags="))
    assert b"U" in flags[len(b"flags=") :], info
    return info


class TestSyncUnixSocket:
    def test_connect_over_unix_socket(self, unix_socket_server: UnixSocketServer):
        client = GlideClient.create(
            unix_socket_config(unix_socket_server, database_id=2)
        )
        try:
            assert client.set("uds-key", "uds-value") == OK
            assert client.get("uds-key") == b"uds-value"
            info = assert_uses_socket(client)
            assert b" db=2 " in info, info
        finally:
            client.close()

    def test_scoped_connection_over_unix_socket(
        self, unix_socket_server: UnixSocketServer
    ):
        client = GlideClient.create(unix_socket_config(unix_socket_server))
        try:
            with client.scoped_connection() as scope:
                scope.set("uds-scope-key", "scoped")
                assert scope.get("uds-scope-key") == "scoped"
            assert client.get("uds-scope-key") == b"scoped"
        finally:
            client.close()

    def test_monitor_over_unix_socket(self, unix_socket_server: UnixSocketServer):
        received: List[MonitorMsg] = []
        monitor = MonitorClient.create(
            unix_socket_config(unix_socket_server), callback=received.append
        )
        try:
            client = GlideClient.create(unix_socket_config(unix_socket_server))
            try:
                client.set("uds-monitor-key", "value")

                def seen() -> bool:
                    return any(
                        m.command.upper() == "SET"
                        and m.client_addr == f"unix:{unix_socket_server.socket_path}"
                        for m in received
                    )

                sync_wait_for(seen, "SET over the socket not seen by MONITOR")
            finally:
                client.close()
        finally:
            monitor.stop()
