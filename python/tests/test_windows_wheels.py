# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import os
import re
import shutil
import subprocess
import sys
import textwrap
import threading
import zipfile
from pathlib import Path
from unittest.mock import Mock

import pytest

from tests import windows_wheel_smoke as smoke

WORKFLOW = Path(__file__).resolve().parents[2] / ".github" / "workflows" / "pypi-cd.yml"


def windows_step(name):
    windows_job = WORKFLOW.read_text().split("    build-windows-wheels:\n", 1)[1]
    return windows_job.split(f"            - name: {name}\n", 1)[1].split(
        "\n            - name:", 1
    )[0]


@pytest.mark.parametrize(
    "member",
    [
        "glide_shared/_fast_response.cp312-win_arm64.pyd",
        "glide_shared/another_extension.pyd",
        "glide_shared/_fast_response.so",
        None,
    ],
)
def test_shared_wheel_requires_native_extension(tmp_path, member):
    step = windows_step("Build native dependencies for the async client")
    match = re.search(r' -c "([^"]+)"', step)
    assert match is not None
    wheel = tmp_path / "shared.whl"
    with zipfile.ZipFile(wheel, "w") as archive:
        if member is not None:
            archive.writestr(member, b"native extension")
    destination = tmp_path / "extracted"
    result = subprocess.run(
        [sys.executable, "-c", match.group(1), str(wheel), str(destination)],
        capture_output=True,
        text=True,
        timeout=10,
    )
    if member and member.endswith(".pyd") and "_fast_response" in member:
        assert result.returncode == 0, result.stderr
        assert (destination / member).read_bytes() == b"native extension"
    else:
        assert result.returncode != 0
        assert "Shared wheel is missing _fast_response*.pyd" in result.stderr


@pytest.mark.skipif(shutil.which("pwsh") is None, reason="PowerShell is required")
@pytest.mark.parametrize("failed_command", [0, 1, 2, 3, 4, 5, 6])
def test_smoke_step_stops_at_each_native_failure(tmp_path, failed_command):
    step = windows_step("Smoke test wheels")
    run = textwrap.dedent(
        step.split("run: |\n", 1)[1].split("\n              env:", 1)[0]
    )
    native_calls = 0
    lines = []
    for line in run.splitlines():
        if line.startswith("& "):
            native_calls += 1
            exit_code = 7 if native_calls == failed_command else 0
            line = (
                f"& '{sys.executable}' -c "
                f"\"import sys; print('native-{native_calls}'); sys.exit({exit_code})\""
            )
        lines.append(line)
    assert native_calls == 6
    for client in ("async", "sync"):
        wheels = tmp_path / "python" / f"glide-{client}" / "wheels"
        wheels.mkdir(parents=True)
        (wheels / "glide-cp312-win_arm64.whl").touch()
    script = tmp_path / "smoke.ps1"
    script.write_text("\n".join(lines) + "\nWrite-Output 'upload-reached'\n")
    result = subprocess.run(
        ["pwsh", "-NoProfile", "-NonInteractive", "-File", str(script)],
        cwd=tmp_path,
        env={**os.environ, "PYTHON_VERSION": "3.12", "WHEEL_TAG": "win_arm64"},
        capture_output=True,
        text=True,
        timeout=30,
    )
    if failed_command:
        assert result.returncode != 0
        assert f"native-{failed_command}" in result.stdout
        assert f"native-{failed_command + 1}" not in result.stdout
        assert "upload-reached" not in result.stdout
    else:
        assert result.returncode == 0, result.stderr
        assert "upload-reached" in result.stdout


def test_blackhole_accepts_after_legacy_socket_timeout(monkeypatch):
    class LegacySocketTimeout(OSError):
        pass

    listener = Mock()
    listener.getsockname.return_value = ("127.0.0.1", 12345)
    connection = Mock()
    finished = threading.Event()
    attempts = 0

    def accept():
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise LegacySocketTimeout()
        if attempts == 2:
            return connection, ("127.0.0.1", 12346)
        finished.set()
        raise OSError("listener closed")

    listener.accept.side_effect = accept
    monkeypatch.setattr(smoke.socket, "socket", Mock(return_value=listener))
    monkeypatch.setattr(smoke.socket, "timeout", LegacySocketTimeout)
    with smoke.blackhole_server() as port:
        assert port == 12345
        assert finished.wait(timeout=1)
    assert listener.accept.call_count == 3
    connection.close.assert_called_once()


def test_statistics_layout():
    smoke.assert_statistics_layout()


def test_windows_checkout_does_not_persist_credentials():
    step = windows_step("Checkout")
    assert 'submodules: "true"' in step
    assert "persist-credentials: false" in step
