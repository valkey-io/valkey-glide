# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Checks how .github/workflows/python.yml passes a test filter to pytest.

The run name of a manual dispatch must not become a pytest -k filter, an
optional filter must keep the pytest.ini exclusions, and the uploaded
report paths must match where pytest writes the reports.
"""

import os
import re
import stat
import subprocess
import textwrap
from pathlib import Path

import pytest
from _pytest.mark.expression import Expression

REPO_ROOT = Path(__file__).parent.parent.parent
WORKFLOW_FILE = REPO_ROOT / ".github" / "workflows" / "python.yml"
PYTEST_INI = REPO_ROOT / "python" / "pytest.ini"

FILTER_STEPS = ["Test with pytest", "Test with pytest (serial-only tests)"]


def _run_block(step_name):
    """Return the dedented `run: |` script of the named step."""
    lines = WORKFLOW_FILE.read_text().splitlines()
    start = lines.index(f"            - name: {step_name}")
    run_idx = next(i for i in range(start, len(lines)) if lines[i].strip() == "run: |")
    run_indent = len(lines[run_idx]) - len(lines[run_idx].lstrip())
    body = []
    for line in lines[run_idx + 1 :]:
        if line.strip() and len(line) - len(line.lstrip()) <= run_indent:
            break
        body.append(line)
    return textwrap.dedent("\n".join(body))


def _pytest_args(step_name, test_filter, tmp_path):
    """Run the step script with a fake python3 and return the args it got."""
    out = tmp_path / "argv"
    shim = tmp_path / "python3"
    shim.write_text(f"#!/bin/sh\nprintf '%s\\0' \"$@\" > '{out}'\n")
    shim.chmod(shim.stat().st_mode | stat.S_IEXEC)
    env = dict(
        os.environ,
        PATH=f"{tmp_path}{os.pathsep}{os.environ['PATH']}",
        TEST_FILTER=test_filter,
        RUN_FULL_MATRIX="false",
        RUN_ASYNC_ONLY="false",
        RUN_SYNC_ONLY="false",
    )
    subprocess.run(
        ["bash", "-eo", "pipefail", "-c", _run_block(step_name)], env=env, check=True
    )
    return out.read_text().split("\0")[:-1]


def _addopts_k_expression():
    match = re.search(r'-k "([^"]+)"', PYTEST_INI.read_text())
    assert match, "pytest.ini addopts no longer has a -k expression"
    return match.group(1)


def test_run_name_is_not_used_as_test_filter():
    # Only the run-name line may read the run name.
    uses = [
        line for line in WORKFLOW_FILE.read_text().splitlines() if "inputs.name" in line
    ]
    assert len(uses) == 1 and "github.event_name == 'workflow_dispatch'" in uses[0]


@pytest.mark.parametrize("step_name", FILTER_STEPS)
def test_empty_filter_adds_no_k(step_name, tmp_path):
    args = _pytest_args(step_name, "", tmp_path)
    assert args[:3] == ["dev.py", "test", "--args"]
    assert "-k" not in args
    assert "" not in args


@pytest.mark.parametrize("step_name", FILTER_STEPS)
def test_filter_keeps_pytest_ini_exclusions(step_name, tmp_path):
    args = _pytest_args(step_name, "test_ping or test_info", tmp_path)
    assert args.count("-k") == 1
    expression = args[args.index("-k") + 1]
    assert expression == f"{_addopts_k_expression()} and (test_ping or test_info)"
    Expression.compile(expression)


def test_uploaded_reports_match_written_reports():
    text = WORKFLOW_FILE.read_text()
    # Every pytest step runs in ./python, so reports land in python/.
    written = set(re.findall(r"--html=(\S+)", text))
    uploaded = set(re.findall(r"^\s+python/(\S+\.html)$", text, re.MULTILINE))
    assert written and written == uploaded
