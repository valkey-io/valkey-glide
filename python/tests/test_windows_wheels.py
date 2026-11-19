# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import fnmatch
import json
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

WORKFLOW = (
    Path(__file__).resolve().parents[2] / ".github" / "workflows" / "python-windows.yml"
)


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


@pytest.mark.parametrize(
    "executable", [sys.executable, getattr(sys, "_base_executable", sys.executable)]
)
def test_protobuf_plugin_uses_target_python_scripts(executable):
    step = windows_step("Generate protobuf files")
    match = re.search(r'\$mypyPlugin = & "\$env:TARGET_PYTHON" -c "([^"]+)"', step)
    assert match is not None
    plugin = subprocess.check_output([executable, "-c", match.group(1)], text=True)
    scripts = subprocess.check_output(
        [executable, "-c", "import sysconfig; print(sysconfig.get_path('scripts'))"],
        text=True,
    )
    assert Path(plugin.strip()) == Path(scripts.strip()) / "protoc-gen-mypy.exe"
    assert "Test-Path -LiteralPath $mypyPlugin -PathType Leaf" in step
    assert "throw" in step.split("$mypyPlugin", 2)[2].split("$protoFiles", 1)[0]


@pytest.mark.skipif(shutil.which("pwsh") is None, reason="PowerShell is required")
@pytest.mark.parametrize("event", ["pull_request", "workflow_dispatch"])
def test_windows_matrix(tmp_path, event):
    matrix_job = WORKFLOW.read_text().split("    build-windows-wheels:", 1)[0]
    script = tmp_path / "matrix.ps1"
    script.write_text(textwrap.dedent(matrix_job.split("run: |\n", 1)[1]))
    output = tmp_path / "output"
    result = subprocess.run(
        ["pwsh", "-NoProfile", "-NonInteractive", "-File", str(script)],
        env={**os.environ, "GITHUB_EVENT_NAME": event, "GITHUB_OUTPUT": str(output)},
        capture_output=True,
        text=True,
        timeout=30,
    )
    assert result.returncode == 0, result.stderr
    name, value = output.read_text().strip().split("=", 1)
    assert name == "WINDOWS_MATRIX"
    matrix = json.loads(value)
    expected = []
    for arch, python_arch, runner, target, wheel_tag, versions in [
        (
            "AMD64",
            "x64",
            "windows-latest",
            "x86_64-pc-windows-msvc",
            "win_amd64",
            ["3.9", "3.10", "3.11", "3.12", "3.13", "3.14"],
        ),
        (
            "ARM64",
            "arm64",
            "windows-11-arm",
            "aarch64-pc-windows-msvc",
            "win_arm64",
            ["3.11", "3.12", "3.13", "3.14"],
        ),
    ]:
        for version in ["3.14"] if event == "pull_request" else versions:
            expected.append(
                {
                    "ARCH": arch,
                    "PYTHON_ARCH": python_arch,
                    "RUNNER": runner,
                    "TARGET": target,
                    "WHEEL_TAG": wheel_tag,
                    "PYTHON_VERSION": version,
                }
            )
    assert matrix == expected


def test_windows_ci_is_separate_from_publishing():
    windows = WORKFLOW.read_text()
    cd = WORKFLOW.with_name("pypi-cd.yml").read_text()
    cd_triggers = cd.split("\nconcurrency:", 1)[0]
    for path in ("ffi/**", "glide-core/**", "python/**"):
        assert f"- {path}" in windows
    assert not re.search(r"- (ffi|glide-core|python)/", cd_triggers)
    assert "build-windows-wheels" not in cd
    assert "WINDOWS_MATRIX" not in cd
    assert "self-hosted" not in windows
    assert "AWS_ACTIONS" not in windows
    assert "publish-binaries" not in windows
    assert "gh-action-pypi-publish" not in windows
    assert "id-token: write" not in windows
    assert "contents: read" in windows
    assert set(re.findall(r"^    ([\w-]+):", windows.split("\njobs:", 1)[1], re.M)) == {
        "load-windows-matrix",
        "build-windows-wheels",
    }
    for client in ("async", "sync"):
        step = windows_step(f"Upload Python {client.title()} client wheel")
        artifact = re.search(r"^\s+name: (.+)$", step, re.M)
        assert artifact is not None
        assert artifact.group(1).startswith("windows-ci-")
        assert not fnmatch.fnmatchcase(artifact.group(1), f"wheels-*-{client}")
