# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import time
from typing import List, Optional

import pytest
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_sync import MonitorClient
from glide_sync.glide_client import _create_connection_request
from glide_sync.monitor_client import _create_monitor_connection_request

from tests.sync_tests.conftest import create_sync_client
from tests.utils.utils import create_sync_client_config, sync_wait_for

_SYNC_REQUEST_IDENTIFICATION_CASES = [
    (None, None, "GlidePySync"),
    ("custom-client", None, "custom-client"),
    (None, "framework:1.2", "GlidePySync(framework:1.2)"),
    ("custom-client", "framework:1.2", "custom-client(framework:1.2)"),
    ("", None, "GlidePySync"),
    (None, "", "GlidePySync"),
    ("", "", "GlidePySync"),
    ("lib:name/1.0", "tag@v2!", "lib:name/1.0(tag@v2!)"),
]


@pytest.mark.parametrize(
    ("lib_name", "client_info_tag", "expected"),
    _SYNC_REQUEST_IDENTIFICATION_CASES,
)
def test_sync_monitor_request_matches_ordinary(
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

    @pytest.mark.skip_if_version_below("7.2.0")
    @pytest.mark.parametrize(
        ("lib_name", "client_info_tag", "expected"),
        [
            (None, None, "GlidePySync"),
            ("sync-monitor", None, "sync-monitor"),
            (None, "monitor-tag:1.0", "GlidePySync(monitor-tag:1.0)"),
            (
                "sync-monitor",
                "monitor-tag:1.0",
                "sync-monitor(monitor-tag:1.0)",
            ),
        ],
    )
    def test_monitor_client_library_identification(
        self,
        request,
        lib_name: Optional[str],
        client_info_tag: Optional[str],
        expected: str,
    ) -> None:
        config = create_sync_client_config(
            request,
            cluster_mode=False,
            lib_name=lib_name,
            client_info_tag=client_info_tag,
        )
        assert isinstance(config, GlideClientConfiguration)
        monitor = MonitorClient.create(config)
        try:
            observer = create_sync_client(request, cluster_mode=False)
            try:

                def _monitor_is_visible() -> bool:
                    client_list = observer.custom_command(["CLIENT", "LIST"])
                    assert isinstance(client_list, bytes)
                    return _client_list_contains_monitor(client_list, expected)

                sync_wait_for(
                    _monitor_is_visible,
                    f"Monitor client with lib-name={expected} was not observed",
                )
            finally:
                observer.close()
        finally:
            monitor.close()

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
