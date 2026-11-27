# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Keeps the local module-test runner and the Python modules CI workflow on the same pytest flags."""

from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.parent

WORKFLOW_FILE = REPO_ROOT / ".github" / "workflows" / "python-modules.yml"
LOCAL_RUNNER_FILE = REPO_ROOT / "utils" / "run-modules-ci-locally.sh"

CI_PYTEST_FLAGS = [
    "-k server_modules",
    "--async-backend=asyncio",
    "--html=pytest_report.html",
    "--self-contained-html",
]


def test_local_runner_pytest_args_match_ci_workflow():
    workflow_text = WORKFLOW_FILE.read_text()
    runner_text = LOCAL_RUNNER_FILE.read_text()

    for flag in CI_PYTEST_FLAGS:
        assert flag in workflow_text, (
            f"python-modules.yml no longer contains {flag!r} in its "
            "'Run modules tests' step -- update CI_PYTEST_FLAGS above "
            "AND utils/run-modules-ci-locally.sh to match."
        )

    for flag in CI_PYTEST_FLAGS:
        assert flag in runner_text, (
            f"utils/run-modules-ci-locally.sh is missing {flag!r}, which "
            "CI's 'Run modules tests' step passes. The local proof must "
            "run the same "
            "pytest invocation CI does, not a hand-rolled approximation."
        )

    assert "-k server_modules" in workflow_text
    assert "-k server_modules" in runner_text


RUNNER_ONLY_ALLOWED_FLAGS = ["-rs"]


def _extract_pytest_invocation_flags(script_text: str, anchor: str) -> list:
    start = script_text.index(anchor)
    lines = script_text[start:].splitlines()
    flags = []
    for line in lines:
        stripped = line.strip().rstrip("\\").strip()
        if stripped.startswith("python3") or not stripped:
            continue
        token = stripped.split()[0] if stripped else ""
        token = token.split("=")[0]
        if token.startswith("-"):
            flags.append(token)
        if not line.rstrip().endswith("\\"):
            break
    return flags


def test_runner_and_workflow_flags_match_exactly():
    workflow_text = WORKFLOW_FILE.read_text()
    runner_text = LOCAL_RUNNER_FILE.read_text()
    workflow_flags = set(
        _extract_pytest_invocation_flags(workflow_text, "python3 dev.py test --args")
    )
    runner_flags = set(
        _extract_pytest_invocation_flags(runner_text, "python3 dev.py test --args")
    )
    allowed_runner_only = set(RUNNER_ONLY_ALLOWED_FLAGS)

    missing_from_runner = workflow_flags - runner_flags
    assert not missing_from_runner, (
        f"utils/run-modules-ci-locally.sh is missing flag(s) "
        f"{missing_from_runner} that .github/workflows/python-modules.yml's "
        "'Run modules tests' step passes -- mirror them into the local "
        "runner."
    )

    extra_in_runner = runner_flags - workflow_flags - allowed_runner_only
    assert not extra_in_runner, (
        f"utils/run-modules-ci-locally.sh passes flag(s) {extra_in_runner} "
        "that .github/workflows/python-modules.yml's 'Run modules tests' "
        "step does not -- either mirror them into the workflow file, or "
        "add them to RUNNER_ONLY_ALLOWED_FLAGS with a reason if they are a "
        "deliberate, benign asymmetry like -rs."
    )
