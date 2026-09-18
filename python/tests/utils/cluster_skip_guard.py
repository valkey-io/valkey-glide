# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Fail the pytest session if cluster tests skip when a cluster was expected."""

from __future__ import annotations

CLUSTER_ENDPOINTS_UNAVAILABLE_SKIP = "No cluster endpoints available"

_unexpected_cluster_skips: list[str] = []


def reset_unexpected_cluster_skips() -> None:
    _unexpected_cluster_skips.clear()


def cluster_was_expected(config) -> bool:
    """True unless this run is standalone-endpoints only."""
    cluster = config.getoption("--cluster-endpoints")
    standalone = config.getoption("--standalone-endpoints")
    return bool(cluster) or not standalone


def _skip_reason(report) -> str:
    longrepr = report.longrepr
    if isinstance(longrepr, tuple) and len(longrepr) >= 3:
        return str(longrepr[2])
    return str(longrepr) if longrepr else ""


def record_cluster_skip_report(report) -> None:
    if not report.skipped:
        return
    reason = _skip_reason(report)
    if not reason.startswith(f"Skipped: {CLUSTER_ENDPOINTS_UNAVAILABLE_SKIP}"):
        return
    if report.nodeid not in _unexpected_cluster_skips:
        _unexpected_cluster_skips.append(report.nodeid)


def fail_session_on_unexpected_cluster_skips(session) -> None:
    if not _unexpected_cluster_skips or not cluster_was_expected(session.config):
        return
    reporter = session.config.pluginmanager.get_plugin("terminalreporter")
    if reporter is not None:
        reporter.write_line(
            f"{len(_unexpected_cluster_skips)} cluster test(s) skipped for lack of a "
            "cluster although one was expected; see require_cluster_addresses()",
            red=True,
        )
        for nodeid in _unexpected_cluster_skips:
            reporter.write_line(f"  {nodeid}", red=True)
    session.exitstatus = 1
