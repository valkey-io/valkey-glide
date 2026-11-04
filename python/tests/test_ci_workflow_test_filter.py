# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Checks how .github/workflows/python.yml passes a test filter to pytest.

Each step script runs under bash with a fake python3, so the tests check
the pytest arguments the workflow really builds: no -k without a filter,
and a filter that keeps the pytest.ini exclusions.
"""

import configparser
import os
import shlex
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


def _run_step(step_name, test_filter, tmp_path, pytest_rc=0):
    """Run the step script with a fake python3 that exits with pytest_rc."""
    out = tmp_path / "argv"
    shim = tmp_path / "python3"
    shim.write_text(f"#!/bin/sh\nprintf '%s\\0' \"$@\" > '{out}'\nexit {pytest_rc}\n")
    shim.chmod(shim.stat().st_mode | stat.S_IEXEC)
    env = dict(
        os.environ,
        PATH=f"{tmp_path}{os.pathsep}{os.environ['PATH']}",
        TEST_FILTER=test_filter,
        RUN_FULL_MATRIX="false",
        RUN_ASYNC_ONLY="false",
        RUN_SYNC_ONLY="false",
    )
    return subprocess.run(
        ["bash", "-eo", "pipefail", "-c", _run_block(step_name)], env=env
    ).returncode


def _pytest_args(step_name, test_filter, tmp_path):
    """Run the step script and return the args the fake python3 got."""
    assert _run_step(step_name, test_filter, tmp_path) == 0
    return (tmp_path / "argv").read_text().split("\0")[:-1]


def _addopts_k_expression():
    ini = configparser.ConfigParser(interpolation=None)
    ini.read(PYTEST_INI)
    addopts = shlex.split(ini["pytest"]["addopts"])
    return addopts[addopts.index("-k") + 1]


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


@pytest.mark.parametrize(
    "test_filter, pytest_rc, step_rc",
    [("test_ping", 5, 0), ("", 5, 5), ("test_ping", 1, 1)],
)
def test_serial_step_allows_filter_with_no_serial_tests(
    test_filter, pytest_rc, step_rc, tmp_path
):
    # pytest exits 5 when nothing is selected; only a filter may cause that.
    step = "Test with pytest (serial-only tests)"
    assert _run_step(step, test_filter, tmp_path, pytest_rc) == step_rc
