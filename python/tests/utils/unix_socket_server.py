# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import shutil
import socket
import subprocess
import tempfile
import time
from typing import Optional

import pytest

SERVER_START_TIMEOUT_SECONDS = 10


def _find_server_binary() -> Optional[str]:
    return shutil.which("valkey-server") or shutil.which("redis-server")


class UnixSocketServer:
    """
    A standalone server that listens only on a Unix domain socket (`--port 0`),
    so any successful client connection must have gone through the socket.

    The server is started directly rather than through `utils/cluster_manager.py`,
    which has no Unix socket option and reports nodes as `host:port`.
    """

    def __init__(self) -> None:
        server_binary = _find_server_binary()
        if server_binary is None:
            message = "valkey-server/redis-server is not installed locally"
            # Never let CI pass with these tests silently skipped.
            if os.environ.get("CI"):
                pytest.fail(message)
            pytest.skip(message)
        # Unix socket paths are limited to ~104-108 bytes, so avoid long TMPDIRs.
        self._dir = tempfile.mkdtemp(prefix="glide-uds-", dir="/tmp")
        self.socket_path = os.path.join(self._dir, "valkey.sock")
        self._log_path = os.path.join(self._dir, "server.log")
        self._process = subprocess.Popen(
            [
                server_binary,
                "--port",
                "0",
                "--unixsocket",
                self.socket_path,
                "--unixsocketperm",
                "700",
                "--dir",
                self._dir,
                "--logfile",
                self._log_path,
                "--save",
                "",
                "--appendonly",
                "no",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.STDOUT,
        )
        try:
            self._wait_until_ready()
        except Exception:
            self.stop()
            raise

    def _server_log(self) -> str:
        try:
            with open(self._log_path) as log:
                return log.read()[-2000:]
        except OSError:
            return "<no server log>"

    def _wait_until_ready(self) -> None:
        deadline = time.monotonic() + SERVER_START_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            if self._process.poll() is not None:
                raise RuntimeError(
                    f"server exited early with code {self._process.returncode}:\n"
                    f"{self._server_log()}"
                )
            try:
                with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as sock:
                    sock.settimeout(1)
                    sock.connect(self.socket_path)
                    sock.sendall(b"PING\r\n")
                    if sock.recv(16).startswith(b"+PONG"):
                        return
            except OSError:
                pass
            time.sleep(0.05)
        raise TimeoutError(
            f"server did not listen on {self.socket_path} in time:\n{self._server_log()}"
        )

    def stop(self) -> None:
        if self._process.poll() is None:
            self._process.terminate()
            try:
                self._process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self._process.kill()
                self._process.wait()
        shutil.rmtree(self._dir, ignore_errors=True)
