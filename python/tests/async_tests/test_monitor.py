# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import List, Optional

import anyio
import pytest
from glide import GlideClusterClientConfiguration, MonitorClient
from glide.glide_client import _create_connection_request
from glide.monitor_client import _create_monitor_connection_request
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import GlideClientConfiguration

from tests.async_tests.conftest import create_client
from tests.utils.utils import create_client_config, wait_for

_ASYNC_REQUEST_IDENTIFICATION_CASES = [
    (None, None, "GlidePy"),
    ("custom-client", None, "custom-client"),
    (None, "framework:1.2", "GlidePy(framework:1.2)"),
    ("custom-client", "framework:1.2", "custom-client(framework:1.2)"),
    ("", None, "GlidePy"),
    (None, "", "GlidePy"),
    ("", "", "GlidePy"),
    ("lib:name/1.0", "tag@v2!", "lib:name/1.0(tag@v2!)"),
]


@pytest.mark.parametrize(
    ("lib_name", "client_info_tag", "expected"),
    _ASYNC_REQUEST_IDENTIFICATION_CASES,
)
def test_async_monitor_request_matches_ordinary(
    lib_name: Optional[str], client_info_tag: Optional[str], expected: str
) -> None:
    config = GlideClientConfiguration(
        addresses=[], lib_name=lib_name, client_info_tag=client_info_tag
    )

    ordinary_request = _create_connection_request(config)
    monitor_request = _create_monitor_connection_request(config)

    assert ordinary_request.lib_name == expected
    assert monitor_request.SerializeToString() == ordinary_request.SerializeToString()


def _client_list_contains_monitor(client_list: bytes, expected_lib_name: str) -> bool:
    expected_field = f"lib-name={expected_lib_name}".encode()
    return any(
        b"cmd=monitor" in line.lower() and expected_field in line
        for line in client_list.splitlines()
    )


async def _wait_for_command(received: List[MonitorMsg], command: str) -> None:
    async def _check() -> bool:
        return command in [m.command.upper() for m in received]

    await wait_for(_check, f"{command} command not received by monitor")


@pytest.mark.anyio
class TestMonitorAsync:
    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_receives_commands(self, request, cluster_mode):
        """Test that MonitorClient receives commands issued by another client."""
        config = create_client_config(request, cluster_mode=False)
        received: List[MonitorMsg] = []

        monitor = await MonitorClient.create(
            config, callback=lambda m: received.append(m)
        )
        try:
            client = await create_client(request, cluster_mode=False)
            try:
                await client.set("monitor_test_key", "monitor_test_val")
                await _wait_for_command(received, "SET")
            finally:
                await client.close()
        finally:
            await monitor.stop()

        commands = [m.command.upper() for m in received]
        assert "SET" in commands

    @pytest.mark.skip_if_version_below("7.2.0")
    @pytest.mark.parametrize(
        ("lib_name", "client_info_tag", "expected"),
        [
            (None, None, "GlidePy"),
            ("async-monitor", None, "async-monitor"),
            (None, "monitor-tag:1.0", "GlidePy(monitor-tag:1.0)"),
            (
                "async-monitor",
                "monitor-tag:1.0",
                "async-monitor(monitor-tag:1.0)",
            ),
        ],
    )
    async def test_monitor_client_library_identification(
        self,
        request,
        lib_name: Optional[str],
        client_info_tag: Optional[str],
        expected: str,
    ) -> None:
        config = create_client_config(
            request,
            cluster_mode=False,
            lib_name=lib_name,
            client_info_tag=client_info_tag,
        )
        assert isinstance(config, GlideClientConfiguration)
        monitor = await MonitorClient.create(config)
        try:
            observer = await create_client(request, cluster_mode=False)
            try:

                async def _monitor_is_visible() -> bool:
                    client_list = await observer.custom_command(["CLIENT", "LIST"])
                    assert isinstance(client_list, bytes)
                    return _client_list_contains_monitor(client_list, expected)

                await wait_for(
                    _monitor_is_visible,
                    f"Monitor client with lib-name={expected} was not observed",
                )
            finally:
                await observer.close()
        finally:
            await monitor.stop()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_queue(self, request, cluster_mode):
        """Test that MonitorClient queues messages when no callback is provided."""
        config = create_client_config(request, cluster_mode=False)

        monitor = await MonitorClient.create(config)
        try:
            client = await create_client(request, cluster_mode=False)
            try:
                await client.ping()
            finally:
                await client.close()

            with anyio.fail_after(5.0):
                msg = await monitor.get_monitor_message()
            assert msg is not None
            assert isinstance(msg, MonitorMsg)
        finally:
            await monitor.stop()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_context_manager(self, request, cluster_mode):
        """Test MonitorClient as async context manager."""
        config = create_client_config(request, cluster_mode=False)

        monitor = await MonitorClient.create(config)
        try:
            assert not monitor._is_closed
        finally:
            await monitor.stop()

        assert monitor._is_closed

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_stop_idempotent(self, request, cluster_mode):
        """Test that calling stop() multiple times is safe."""
        config = create_client_config(request, cluster_mode=False)
        monitor = await MonitorClient.create(config)
        await monitor.stop()
        await monitor.stop()  # Should not raise

    def test_monitor_rejects_cluster_config(self, request):
        """Test that MonitorClient raises TypeError for cluster config."""
        cluster_config = GlideClusterClientConfiguration(addresses=[])
        with pytest.raises(TypeError):
            # TypeError is raised synchronously in create() before any async work.
            # anyio.run() uses asyncio backend by default.
            anyio.run(MonitorClient.create, cluster_config)

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_try_get_monitor_message_empty(self, request, cluster_mode):
        """Test that try_get_monitor_message returns None when queue is empty."""
        config = create_client_config(request, cluster_mode=False)
        monitor = await MonitorClient.create(config)
        try:
            assert monitor.try_get_monitor_message() is None
        finally:
            await monitor.stop()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_aclose(self, request, cluster_mode):
        """Test that aclose() is a valid alias for stop()."""
        config = create_client_config(request, cluster_mode=False)
        monitor = await MonitorClient.create(config)
        await monitor.aclose()
        assert monitor._is_closed

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_msg_fields(self, request, cluster_mode):
        """Test that MonitorMsg has correct field types."""
        config = create_client_config(request, cluster_mode=False)
        received: List[MonitorMsg] = []

        monitor = await MonitorClient.create(
            config, callback=lambda m: received.append(m)
        )
        try:
            client = await create_client(request, cluster_mode=False)
            try:
                await client.set("field_test_key", "field_test_val")
                await _wait_for_command(received, "SET")
            finally:
                await client.close()
        finally:
            await monitor.stop()

        set_msgs = [m for m in received if m.command.upper() == "SET"]
        assert set_msgs, "No SET message received"
        msg = set_msgs[0]
        assert isinstance(msg.timestamp, float)
        assert isinstance(msg.db, int)
        assert isinstance(msg.client_addr, str)
        assert isinstance(msg.command, str)
        assert isinstance(msg.args, list)
