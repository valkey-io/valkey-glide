# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Tests for the Unix socket mode of the standalone test harness."""

import os
import subprocess
import sys
from pathlib import Path
from unittest.mock import Mock

import pytest
from glide_shared.config import GlideClientConfiguration
from glide_sync.glide_client import GlideClient

from tests.utils.cluster import ValkeyCluster
from tests.utils.utils import assert_unix_socket_client

UTILS_DIR = Path(__file__).resolve().parent.parent.parent / "utils"
sys.path.insert(0, str(UTILS_DIR))

import cluster_manager  # noqa: E402

pytestmark = pytest.mark.skipif(
    sys.platform == "win32", reason="Unix domain sockets are not supported on Windows"
)


def test_unix_socket_session_server_has_tcp_disabled(request):
    if not request.config.getoption("--unix-socket"):
        pytest.skip("Runs only with --unix-socket")
    for address in pytest.standalone_cluster.nodes_addr:  # type: ignore[attr-defined]
        client = GlideClient.create(
            GlideClientConfiguration([address], request_timeout=2000)
        )
        try:
            assert client.config_get(["port"]) == {b"port": b"0"}
            assert_unix_socket_client(client.custom_command(["CLIENT", "INFO"]))
        finally:
            client.close()


@pytest.mark.parametrize(
    "args",
    [
        ["--tls", "start", "--unix-socket", "--replica-count", "0"],
        ["start", "--unix-socket", "--replica-count", "0", "--cluster-mode"],
        ["start", "--unix-socket", "--replica-count", "0", "--remote", "10.0.0.1"],
        ["start", "--unix-socket", "--replica-count", "1"],
    ],
)
def test_start_rejects_unsupported_flags(args):
    result = subprocess.run(
        [sys.executable, str(UTILS_DIR / "cluster_manager.py"), *args],
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 2, result.stderr
    assert "--unix-socket is not supported with" in result.stderr


def test_start_preserves_socket_record_when_pid_is_unknown(tmp_path, monkeypatch):
    cluster_folder = tmp_path / "cluster"
    node_folder = cluster_folder / "51234"
    node_folder.mkdir(parents=True)
    socket_file = tmp_path / "node.sock"
    socket_file.touch()
    (node_folder / "unix_socket").write_text(str(socket_file))

    start_server = Mock(
        return_value=(cluster_manager.Server("127.0.0.1", 51234), str(node_folder))
    )
    monkeypatch.setattr(cluster_manager, "start_server", start_server)
    monkeypatch.setattr(
        cluster_manager,
        "check_node_startup",
        lambda *args: cluster_manager.NodeCheck.NO_VERDICT,
    )

    with pytest.raises(Exception, match="it never reported Ready"):
        cluster_manager.create_servers(
            host="127.0.0.1",
            shard_count=1,
            replica_count=0,
            ports=None,
            cluster_folder=str(cluster_folder),
            tls=False,
            cluster_mode=False,
            unix_socket=True,
        )

    start_server.assert_called_once()
    assert socket_file.exists()
    assert cluster_manager.node_unix_socket(str(cluster_folder), 51234) == str(
        socket_file
    )


@pytest.mark.parametrize("stopped", [True, False])
def test_stop_keeps_the_socket_of_a_server_it_could_not_stop(
    tmp_path, monkeypatch, stopped
):
    socket_file = tmp_path / "node.sock"
    socket_file.touch()
    node_folder = tmp_path / "cluster" / "51234"
    node_folder.mkdir(parents=True)
    (node_folder / "unix_socket").write_text(str(socket_file))

    def stop_server(*args):
        if not stopped:
            raise Exception("shutdown failed")

    monkeypatch.setattr(cluster_manager, "stop_server", stop_server)
    monkeypatch.setattr(cluster_manager, "init_logger", lambda logfile: None)
    cluster_manager.stop_cluster(
        "127.0.0.1", str(tmp_path / "cluster"), False, "", None, keep_folder=False
    )
    assert socket_file.exists() is not stopped
    assert node_folder.exists() is not stopped
    if not stopped:
        assert cluster_manager.node_unix_socket(
            str(tmp_path / "cluster"), 51234
        ) == str(socket_file)
        monkeypatch.setattr(cluster_manager, "stop_server", lambda *args: None)
        cluster_manager.stop_cluster(
            "127.0.0.1", str(tmp_path / "cluster"), False, "", None, keep_folder=False
        )
        assert not socket_file.exists()
        assert not (tmp_path / "cluster").exists()


@pytest.mark.skipif(
    hasattr(os, "geteuid") and os.geteuid() == 0,
    reason="root ignores socket permissions",
)
def test_stop_keeps_a_running_server_it_cannot_reach():
    cluster = ValkeyCluster(
        tls=False, cluster_mode=False, shard_count=1, replica_count=0, unix_socket=True
    )
    socket_file = Path(cluster.unix_socket_paths[0])
    # PING fails with "Permission denied" while the server keeps running.
    socket_file.chmod(0)
    try:
        subprocess.run(
            [
                sys.executable,
                str(UTILS_DIR / "cluster_manager.py"),
                "stop",
                "--cluster-folder",
                cluster.cluster_folder,
            ],
            check=True,
            capture_output=True,
            timeout=30,
        )
        assert socket_file.exists()
        assert cluster_manager.node_unix_socket(
            cluster.cluster_folder, cluster.nodes_addr[0].port
        ) == str(socket_file)
    finally:
        socket_file.chmod(0o700)
        del cluster
    assert not socket_file.exists()


def test_socket_server_has_tcp_disabled():
    cluster = ValkeyCluster(
        tls=False, cluster_mode=False, shard_count=1, replica_count=0, unix_socket=True
    )
    paths = list(cluster.unix_socket_paths)
    try:
        assert len(cluster.nodes_addr) == 1
        client = GlideClient.create(
            GlideClientConfiguration(cluster.nodes_addr, request_timeout=2000)
        )
        try:
            assert client.config_get(["port"]) == {b"port": b"0"}
            assert_unix_socket_client(client.custom_command(["CLIENT", "INFO"]))
        finally:
            client.close()
    finally:
        del cluster
    assert not any(Path(path).exists() for path in paths)
