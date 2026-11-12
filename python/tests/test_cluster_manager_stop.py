#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""``stop_cluster`` kills a node that refuses a graceful shutdown (a test left
``requirepass`` set and no ``--auth`` is given) using the pid in its
``server.log``, but never a pid that is no longer that node's server.
"""

import os
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "utils"))

from cluster_manager import (  # type: ignore[import-not-found]  # noqa: E402
    get_cli_command,
    kill_logged_server,
    start_server,
    stop_cluster,
)

pytestmark = pytest.mark.skipif(
    not (shutil.which("valkey-server") or shutil.which("redis-server")),
    reason="needs valkey-server or redis-server on PATH",
)


def _running(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    try:
        return Path(f"/proc/{pid}/stat").read_text().rsplit(") ", 1)[1][0] != "Z"
    except OSError:
        return True


def _wait_until_gone(pid: int, timeout: float = 5) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not _running(pid):
            return True
        time.sleep(0.05)
    return False


@pytest.fixture
def locked_server(tmp_path):
    server, node_folder = start_server(
        "127.0.0.1", None, str(tmp_path), False, [], False
    )
    assert server.pid > 0
    subprocess.run(
        [get_cli_command(), "-p", str(server.port)]
        + ["CONFIG", "SET", "requirepass", "secret"],
        check=True,
        capture_output=True,
    )
    yield server, node_folder
    if _running(server.pid):
        os.kill(server.pid, signal.SIGKILL)


def test_node_that_refuses_shutdown_is_killed(tmp_path, locked_server):
    server, _ = locked_server
    stop_cluster("127.0.0.1", str(tmp_path), False, None, None, keep_folder=True)
    assert _wait_until_gone(server.pid)


def test_logged_pid_of_another_process_is_not_killed(tmp_path, locked_server):
    server, node_folder = locked_server
    other = subprocess.Popen(["sleep", "60"])
    try:
        Path(node_folder, "server.log").write_text(
            "1:C * oO0OoO0OoO0Oo Valkey is starting oO0OoO0OoO0Oo\n"
            f"1:C * Valkey version=8.0.0, bits=64, commit=0, modified=0, pid={other.pid}, just started\n"
        )
        stop_cluster(
            "127.0.0.1", str(tmp_path), False, None, None, keep_folder=True
        )
        with pytest.raises(subprocess.TimeoutExpired):
            other.wait(timeout=1)
        assert _running(server.pid)
    finally:
        other.kill()
        other.wait()


def test_folder_without_logged_pid_warns(tmp_path, caplog):
    node_folder = tmp_path / "6379"
    node_folder.mkdir()
    (node_folder / "server.log").write_text("# Server initialized\n")
    kill_logged_server(str(node_folder))
    assert "No logged pid" in caplog.text


def test_unknown_command_line_without_ps_warns(tmp_path, caplog, monkeypatch):
    dead = subprocess.Popen(["sleep", "60"])
    dead.kill()
    dead.wait()
    node_folder = tmp_path / "6379"
    node_folder.mkdir()
    (node_folder / "server.log").write_text(f"pid={dead.pid}, just started\n")
    monkeypatch.setenv("PATH", str(tmp_path))
    kill_logged_server(str(node_folder))
    assert "not the server on port 6379" in caplog.text


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
