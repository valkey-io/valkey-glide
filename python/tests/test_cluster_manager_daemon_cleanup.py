#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Regression tests for hardened cluster-manager daemon cleanup and PID-ownership checks.

Addresses Issue #7463:
- Missing or delayed PID log output
- Daemon still starting or running at timeout (NO_VERDICT)
- Daemon that has already exited
- Stale or reused PID identity (must never signal unrelated processes)
- Node folder preservation when ownership/shutdown cannot be confirmed
- Safe folder deletion only after daemon is confirmed stopped
"""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "utils"))

from cluster_manager import (  # noqa: E402
    SIGKILL,
    SIGTERM,
    NodeCheck,
    Server,
    create_servers,
    is_process_alive,
    read_daemon_pid,
    stop_failed_daemon,
    verify_process_ownership,
)


class TestReadDaemonPid:
    def test_read_pid_from_pidfile(self, tmp_path):
        pidfile = tmp_path / "server.pid"
        pidfile.write_text("54321\n")
        assert read_daemon_pid(str(tmp_path)) == 54321

    def test_read_pid_from_logfmt_log(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text(
            'pid=12345 role=M timestamp="2026-10-30T12:00:00Z" '
            'level=notice message="Server initialized"\n'
        )
        assert read_daemon_pid(str(tmp_path), str(log)) == 12345

    def test_read_pid_from_legacy_log(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text("67890:M 30 Oct 2026 12:00:00.000 * Running mode=standalone\n")
        assert read_daemon_pid(str(tmp_path), str(log)) == 67890

    def test_read_pid_from_just_started_log(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text(
            "oO0OoO0OoO Valkey version=8.0.0, pid=11223, just started: 30 Oct 2026\n"
        )
        assert read_daemon_pid(str(tmp_path), str(log)) == 11223

    def test_read_pid_missing(self, tmp_path):
        log = tmp_path / "server.log"
        log.write_text("No pid here\n")
        assert read_daemon_pid(str(tmp_path), str(log)) is None


class TestIsProcessAlive:
    def test_non_positive_pid_is_not_alive(self):
        assert is_process_alive(-1) is False
        assert is_process_alive(0) is False

    def test_alive_process(self, monkeypatch):
        monkeypatch.setattr("os.kill", lambda pid, sig: None)
        assert is_process_alive(12345) is True

    def test_dead_process_lookup_error(self, monkeypatch):
        def mock_kill(pid, sig):
            raise ProcessLookupError()

        monkeypatch.setattr("os.kill", mock_kill)
        assert is_process_alive(12345) is False

    def test_permission_error_means_process_exists(self, monkeypatch):
        def mock_kill(pid, sig):
            raise PermissionError("EPERM")

        monkeypatch.setattr("os.kill", mock_kill)
        assert is_process_alive(12345) is True


class TestVerifyProcessOwnership:
    def test_matching_valkey_server_args(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        node_folder = str(tmp_path / "6379")
        monkeypatch.setattr("os.kill", lambda pid, sig: None)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: ["valkey-server", "--port", "6379", "--dir", node_folder],
        )
        assert verify_process_ownership(12345, server, node_folder) is True

    def test_matching_redis_server_args(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6380)
        node_folder = str(tmp_path / "6380")
        monkeypatch.setattr("os.kill", lambda pid, sig: None)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: [
                "/usr/bin/redis-server",
                "--port",
                "6380",
                "--dir",
                node_folder,
            ],
        )
        assert verify_process_ownership(12345, server, node_folder) is True

    def test_reused_pid_unrelated_process_refused(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        node_folder = str(tmp_path / "6379")
        monkeypatch.setattr("os.kill", lambda pid, sig: None)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: ["python3", "worker.py", "--port", "6379"],
        )
        assert verify_process_ownership(12345, server, node_folder) is False

    def test_reused_pid_different_server_instance_refused(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        node_folder = str(tmp_path / "6379")
        other_folder = str(tmp_path / "7000")
        monkeypatch.setattr("os.kill", lambda pid, sig: None)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: ["valkey-server", "--port", "7000", "--dir", other_folder],
        )
        assert verify_process_ownership(12345, server, node_folder) is False

    def test_process_dead_raises_lookup_error(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        node_folder = str(tmp_path / "6379")

        def mock_kill(pid, sig):
            raise ProcessLookupError()

        monkeypatch.setattr("os.kill", mock_kill)
        assert verify_process_ownership(12345, server, node_folder) is False

    def test_process_owned_by_different_user_raises_permission_error(
        self, monkeypatch, tmp_path
    ):
        server = Server("127.0.0.1", 6379)
        node_folder = str(tmp_path / "6379")

        def mock_kill(pid, sig):
            raise PermissionError("Operation not permitted")

        monkeypatch.setattr("os.kill", mock_kill)
        assert verify_process_ownership(12345, server, node_folder) is False


class TestStopFailedDaemon:
    def test_already_exited_daemon_reports_stopped(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        server.set_process_id(12345)
        node_folder = str(tmp_path / "6379")

        def mock_kill(pid, sig):
            raise ProcessLookupError()

        monkeypatch.setattr("os.kill", mock_kill)
        assert stop_failed_daemon(server, node_folder) is True

    def test_missing_pid_returns_false_and_retains_folder(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        node_folder = str(tmp_path / "6379")
        assert stop_failed_daemon(server, node_folder) is False
        assert server.process_id() == -1

    def test_delayed_pid_in_log_is_discovered_and_stopped(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        node_folder = tmp_path / "6379"
        node_folder.mkdir()
        log = node_folder / "server.log"
        log.write_text('pid=99988 role=M level=notice message="Delayed startup"\n')

        signals_sent = []

        def mock_kill(pid, sig):
            if sig != 0:
                signals_sent.append((pid, sig))
            return None

        monkeypatch.setattr("os.kill", mock_kill)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: ["valkey-server", "--port", "6379", "--dir", str(node_folder)],
        )
        monkeypatch.setattr("cluster_manager.is_process_alive", lambda pid: True)
        monkeypatch.setattr(
            "cluster_manager.wait_for_process_exit", lambda pid, timeout=2.0: True
        )

        assert stop_failed_daemon(server, str(node_folder)) is True
        assert server.process_id() == 99988
        assert (99988, SIGTERM) in signals_sent

    def test_reused_pid_is_not_signaled_and_returns_false(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        server.set_process_id(55555)
        node_folder = str(tmp_path / "6379")

        signals_sent = []

        def mock_kill(pid, sig):
            if sig != 0:
                signals_sent.append((pid, sig))
            return None

        monkeypatch.setattr("os.kill", mock_kill)
        monkeypatch.setattr("cluster_manager.is_process_alive", lambda pid: True)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: ["/usr/bin/python3", "pytest"],
        )

        assert stop_failed_daemon(server, node_folder) is False
        assert len(signals_sent) == 0

    def test_daemon_still_running_escalates_to_sigkill(self, monkeypatch, tmp_path):
        server = Server("127.0.0.1", 6379)
        server.set_process_id(44444)
        node_folder = str(tmp_path / "6379")

        signals_sent = []

        def mock_kill(pid, sig):
            if sig != 0:
                signals_sent.append((pid, sig))
            return None

        monkeypatch.setattr("os.kill", mock_kill)
        monkeypatch.setattr(
            "cluster_manager.get_process_cmdline",
            lambda pid: ["valkey-server", "--port", "6379", "--dir", node_folder],
        )

        wait_calls = []

        def mock_wait(pid, timeout=2.0):
            wait_calls.append(timeout)
            return len(wait_calls) > 1

        monkeypatch.setattr("cluster_manager.is_process_alive", lambda pid: True)
        monkeypatch.setattr("cluster_manager.wait_for_process_exit", mock_wait)

        assert stop_failed_daemon(server, node_folder) is True
        assert (44444, SIGTERM) in signals_sent
        assert (44444, SIGKILL) in signals_sent


class TestCreateServersCleanupIntegration:
    def test_replacement_retains_folder_when_daemon_unstopped(
        self, monkeypatch, tmp_path
    ):
        cluster_folder = tmp_path / "test_cluster"
        cluster_folder.mkdir()
        node_0 = cluster_folder / "6379"
        node_0.mkdir()
        log_0 = node_0 / "server.log"
        log_0.write_text("# Server starting...\n")

        removed_folders = []
        monkeypatch.setattr(
            "cluster_manager.remove_folder", lambda path: removed_folders.append(path)
        )
        monkeypatch.setattr(
            "cluster_manager.check_node_startup",
            lambda s, log_file: NodeCheck.NO_VERDICT,
        )
        monkeypatch.setattr("cluster_manager.stop_failed_daemon", lambda s, f: False)

        node_1 = cluster_folder / "6380"
        node_1.mkdir()
        log_1 = node_1 / "server.log"
        log_1.write_text("* Ready to accept connections\n")

        call_count = [0]

        def mock_start_server(*args, **kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                return Server("127.0.0.1", 6379), str(node_0)
            else:
                monkeypatch.setattr(
                    "cluster_manager.check_node_startup",
                    lambda s, log_file: NodeCheck.READY,
                )
                return Server("127.0.0.1", 6380), str(node_1)

        monkeypatch.setattr("cluster_manager.start_server", mock_start_server)
        monkeypatch.setattr(
            "cluster_manager.wait_for_server", lambda *args, **kwargs: True
        )

        servers = create_servers(
            host="127.0.0.1",
            shard_count=1,
            replica_count=0,
            ports=None,
            cluster_folder=str(cluster_folder),
            tls=False,
            cluster_mode=False,
        )

        assert len(servers) == 1
        assert servers[0].port == 6380
        assert str(node_0) not in removed_folders

    def test_replacement_removes_folder_when_daemon_confirmed_stopped(
        self, monkeypatch, tmp_path
    ):
        cluster_folder = tmp_path / "test_cluster"
        cluster_folder.mkdir()
        node_0 = cluster_folder / "6379"
        node_0.mkdir()
        log_0 = node_0 / "server.log"
        log_0.write_text("# Server starting...\n")

        removed_folders = []
        monkeypatch.setattr(
            "cluster_manager.remove_folder", lambda path: removed_folders.append(path)
        )
        monkeypatch.setattr(
            "cluster_manager.check_node_startup",
            lambda s, log_file: NodeCheck.NO_VERDICT,
        )
        monkeypatch.setattr("cluster_manager.stop_failed_daemon", lambda s, f: True)

        node_1 = cluster_folder / "6380"
        node_1.mkdir()

        call_count = [0]

        def mock_start_server(*args, **kwargs):
            call_count[0] += 1
            if call_count[0] == 1:
                return Server("127.0.0.1", 6379), str(node_0)
            else:
                monkeypatch.setattr(
                    "cluster_manager.check_node_startup",
                    lambda s, log_file: NodeCheck.READY,
                )
                return Server("127.0.0.1", 6380), str(node_1)

        monkeypatch.setattr("cluster_manager.start_server", mock_start_server)
        monkeypatch.setattr(
            "cluster_manager.wait_for_server", lambda *args, **kwargs: True
        )

        servers = create_servers(
            host="127.0.0.1",
            shard_count=1,
            replica_count=0,
            ports=None,
            cluster_folder=str(cluster_folder),
            tls=False,
            cluster_mode=False,
        )

        assert len(servers) == 1
        assert servers[0].port == 6380
        assert str(node_0) in removed_folders


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
