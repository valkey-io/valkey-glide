# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Tests for the Unix socket mode of the standalone test harness."""

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


def test_unix_socket_session_does_not_create_tcp_clusters(request):
    if not request.config.getoption("--unix-socket"):
        pytest.skip("Runs only with --unix-socket")

    for attr in (
        "valkey_cluster",
        "valkey_tls_cluster",
        "standalone_tls_cluster",
        "valkey_auth_cluster",
        "valkey_auth_tls_cluster",
    ):
        assert not hasattr(pytest, attr), f"UDS session created {attr}"


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


@pytest.mark.parametrize("ports", [None, [51234]])
def test_start_preserves_socket_record_when_pid_is_unknown(
    tmp_path, monkeypatch, ports
):
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
            ports=ports,
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

    targets = []
    monkeypatch.setattr(
        cluster_manager,
        "stop_server",
        lambda server, folder, *args: targets.append(
            cluster_manager.cli_target_args(server, folder)
        ),
    )
    monkeypatch.setattr(cluster_manager, "init_logger", lambda logfile: None)
    cluster_manager.stop_cluster(
        "127.0.0.1", str(cluster_folder), False, "", None, keep_folder=False
    )
    assert targets == [["-s", str(socket_file)]]
    assert not socket_file.exists()
    assert not cluster_folder.exists()


@pytest.mark.parametrize("stopped", [True, False])
@pytest.mark.parametrize("keep_folder", [True, False])
def test_stop_keeps_the_socket_of_a_server_it_could_not_stop(
    tmp_path, monkeypatch, stopped, keep_folder
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
        "127.0.0.1", str(tmp_path / "cluster"), False, "", None, keep_folder=keep_folder
    )
    assert socket_file.exists() is not stopped
    assert node_folder.exists() is (keep_folder or not stopped)
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
