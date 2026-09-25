# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""pytester coverage for the unexpected cluster-skip session guard."""

import os
from pathlib import Path

pytest_plugins = ["pytester"]

_PYTHON_ROOT = Path(__file__).resolve().parent.parent
_GUARD_SRC = (
    Path(__file__).resolve().parent / "utils" / "cluster_skip_guard.py"
).read_text()

_CONFTEST = """
import pytest
from cluster_skip_guard import (
    fail_session_on_unexpected_cluster_skips,
    record_cluster_skip_report,
    reset_unexpected_cluster_skips,
)


def pytest_addoption(parser):
    parser.addoption("--cluster-endpoints", default=None, action="store")
    parser.addoption("--standalone-endpoints", default=None, action="store")


def pytest_sessionstart(session):
    reset_unexpected_cluster_skips()


def pytest_runtest_logreport(report):
    record_cluster_skip_report(report)


def pytest_sessionfinish(session, exitstatus):
    fail_session_on_unexpected_cluster_skips(session)
"""

_REQUIRE_CLUSTER_ADDRESSES_TEST = """
import pytest
from tests.utils.utils import require_cluster_addresses


def test_cluster_only():
    if hasattr(pytest, "valkey_cluster"):
        delattr(pytest, "valkey_cluster")
    require_cluster_addresses()
"""


def _prepare(pytester, monkeypatch):
    current = os.environ.get("PYTHONPATH", "")
    monkeypatch.setenv(
        "PYTHONPATH",
        str(_PYTHON_ROOT) if not current else f"{_PYTHON_ROOT}{os.pathsep}{current}",
    )
    pytester.makepyfile(cluster_skip_guard=_GUARD_SRC)
    pytester.makeconftest(_CONFTEST)


def test_unexpected_cluster_skip_fails_session(pytester, monkeypatch):
    _prepare(pytester, monkeypatch)
    pytester.makepyfile(_REQUIRE_CLUSTER_ADDRESSES_TEST)
    result = pytester.runpytest_subprocess("-q")
    assert result.ret != 0
    result.stdout.fnmatch_lines(
        [
            "*cluster test(s) skipped for lack of a cluster although one was expected*",
            "*test_cluster_only*",
        ]
    )


def test_standalone_only_cluster_skip_is_accepted(pytester, monkeypatch):
    _prepare(pytester, monkeypatch)
    pytester.makepyfile(_REQUIRE_CLUSTER_ADDRESSES_TEST)
    result = pytester.runpytest_subprocess("-q", "--standalone-endpoints=127.0.0.1:1")
    assert result.ret == 0
    assert "although one was expected" not in result.stdout.str()


def test_unrelated_skip_does_not_fail_session(pytester, monkeypatch):
    _prepare(pytester, monkeypatch)
    pytester.makepyfile("""
import pytest

def test_other():
    pytest.skip("server version too old")
""")
    result = pytester.runpytest_subprocess("-q")
    assert result.ret == 0
    assert "although one was expected" not in result.stdout.str()
