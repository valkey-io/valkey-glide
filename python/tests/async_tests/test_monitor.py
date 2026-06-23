# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import List

import pytest
import sniffio
from glide import GlideClusterClientConfiguration, MonitorClient
from glide_shared.commands.core_options import MonitorMsg

from tests.async_tests.conftest import create_client
from tests.utils.utils import create_client_config, wait_for


@pytest.mark.anyio
class TestMonitorAsync:
    @staticmethod
    def _skip_if_trio():
        """MonitorClient uses asyncio.Queue internally and doesn't support trio."""
        try:
            if sniffio.current_async_library() == "trio":
                pytest.skip("MonitorClient requires asyncio (uses asyncio.Queue)")
        except sniffio.AsyncLibraryNotFoundError:
            pass

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_receives_commands(self, request, cluster_mode):
        """Test that MonitorClient receives commands issued by another client."""
        self._skip_if_trio()
        config = create_client_config(request, cluster_mode=False)
        received: List[MonitorMsg] = []

        monitor = await MonitorClient.create(
            config, callback=lambda m: received.append(m)
        )
        try:
            client = await create_client(request, cluster_mode=False)
            try:
                await client.set("monitor_test_key", "monitor_test_val")

                async def _set_received():
                    return any(m.command.upper() == "SET" for m in received)

                await wait_for(
                    _set_received, "SET command not received by monitor", timeout=1
                )
            finally:
                await client.close()
        finally:
            await monitor.stop()

        commands = [m.command.upper() for m in received]
        assert "SET" in commands

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_queue(self, request, cluster_mode):
        """Test that MonitorClient queues messages when no callback is provided."""
        self._skip_if_trio()
        config = create_client_config(request, cluster_mode=False)

        monitor = await MonitorClient.create(config)
        try:
            client = await create_client(request, cluster_mode=False)
            try:
                await client.ping()
            finally:
                await client.close()

            msg = None

            async def _msg_available():
                nonlocal msg
                msg = monitor.try_get_monitor_message()
                return msg is not None

            await wait_for(_msg_available, "No monitor message received", timeout=5.0)
            assert isinstance(msg, MonitorMsg)
        finally:
            await monitor.stop()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_context_manager(self, request, cluster_mode):
        """Test MonitorClient as async context manager."""
        self._skip_if_trio()
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
        self._skip_if_trio()
        config = create_client_config(request, cluster_mode=False)
        monitor = await MonitorClient.create(config)
        await monitor.stop()
        await monitor.stop()  # Should not raise

    def test_monitor_rejects_cluster_config(self, request):
        """Test that MonitorClient raises TypeError for cluster config."""
        cluster_config = GlideClusterClientConfiguration(addresses=[])
        with pytest.raises(TypeError):
            # TypeError is raised synchronously before any async work
            import asyncio

            asyncio.run(MonitorClient.create(cluster_config))

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_monitor_msg_fields(self, request, cluster_mode):
        """Test that MonitorMsg has correct field types."""
        self._skip_if_trio()
        config = create_client_config(request, cluster_mode=False)
        received: List[MonitorMsg] = []

        monitor = await MonitorClient.create(
            config, callback=lambda m: received.append(m)
        )
        try:
            client = await create_client(request, cluster_mode=False)
            try:
                await client.set("field_test_key", "field_test_val")

                async def _set_received():
                    return any(m.command.upper() == "SET" for m in received)

                await wait_for(
                    _set_received, "SET command not received by monitor", timeout=1
                )
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
