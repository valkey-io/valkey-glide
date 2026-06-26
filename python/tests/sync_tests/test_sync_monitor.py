# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import time
from typing import List

import pytest
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import GlideClusterClientConfiguration
from glide_sync import MonitorClient

from tests.sync_tests.conftest import create_sync_client
from tests.utils.utils import create_sync_client_config


class TestMonitorSync:
    @pytest.mark.parametrize("cluster_mode", [False])
    def test_monitor_receives_commands(self, request, cluster_mode):
        """Test that MonitorClient receives commands issued by another client."""
        config = create_sync_client_config(request, cluster_mode=False)
        received: List[MonitorMsg] = []

        monitor = MonitorClient.create(config, callback=lambda m: received.append(m))
        try:
            client = create_sync_client(request, cluster_mode=False)
            try:
                client.set("sync_monitor_key", "sync_monitor_val")
                time.sleep(0.5)
            finally:
                client.close()
        finally:
            monitor.close()

        commands = [m.command.upper() for m in received]
        assert "SET" in commands

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_monitor_queue(self, request, cluster_mode):
        """Test that MonitorClient queues messages when no callback is provided."""
        config = create_sync_client_config(request, cluster_mode=False)

        monitor = MonitorClient.create(config)
        try:
            client = create_sync_client(request, cluster_mode=False)
            try:
                client.ping()
                time.sleep(0.5)
            finally:
                client.close()

            msg = monitor.get_monitor_message(timeout=5.0)
            assert msg is not None
            assert isinstance(msg, MonitorMsg)
        finally:
            monitor.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_monitor_context_manager(self, request, cluster_mode):
        """Test MonitorClient as context manager."""
        config = create_sync_client_config(request, cluster_mode=False)

        with MonitorClient.create(config) as monitor:
            assert not monitor._is_closed

        assert monitor._is_closed

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_monitor_stop_idempotent(self, request, cluster_mode):
        """Test that calling stop() multiple times is safe."""
        config = create_sync_client_config(request, cluster_mode=False)
        monitor = MonitorClient.create(config)
        monitor.stop()
        monitor.stop()  # Should not raise

    def test_monitor_rejects_cluster_config(self):
        """Test that MonitorClient raises TypeError for cluster config."""
        cluster_config = GlideClusterClientConfiguration(addresses=[])
        with pytest.raises(TypeError):
            MonitorClient.create(cluster_config)

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_try_get_monitor_message_empty(self, request, cluster_mode):
        """Test that try_get_monitor_message returns None when queue is empty."""
        config = create_sync_client_config(request, cluster_mode=False)
        monitor = MonitorClient.create(config)
        try:
            assert monitor.try_get_monitor_message() is None
        finally:
            monitor.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_monitor_aclose(self, request, cluster_mode):
        """Test that stop() closes the monitor."""
        config = create_sync_client_config(request, cluster_mode=False)
        monitor = MonitorClient.create(config)
        monitor.stop()
        assert monitor._is_closed

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_monitor_msg_fields(self, request, cluster_mode):
        """Test that MonitorMsg has correct field types."""
        config = create_sync_client_config(request, cluster_mode=False)
        received: List[MonitorMsg] = []

        monitor = MonitorClient.create(config, callback=lambda m: received.append(m))
        try:
            client = create_sync_client(request, cluster_mode=False)
            try:
                client.set("sync_field_test_key", "sync_field_test_val")
                time.sleep(0.5)
            finally:
                client.close()
        finally:
            monitor.close()

        set_msgs = [m for m in received if m.command.upper() == "SET"]
        assert set_msgs, "No SET message received"
        msg = set_msgs[0]
        assert isinstance(msg.timestamp, float)
        assert isinstance(msg.db, int)
        assert isinstance(msg.client_addr, str)
        assert isinstance(msg.command, str)
        assert isinstance(msg.args, list)
