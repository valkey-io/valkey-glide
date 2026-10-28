# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Single-source utils/run-modules-ci-locally.sh's pytest
invocation against .github/workflows/python-modules.yml's "Run modules
tests" step, so the two cannot silently drift apart. This test does not
run either script -- it just asserts that run-modules-ci-locally.sh's
hard-coded arg list is a superset of the exact flags the CI workflow
step uses, so a future edit to one side that isn't mirrored on the
other fails the test suite instead of only showing up as a discrepancy
report in a future review.
"""

from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.parent

WORKFLOW_FILE = REPO_ROOT / ".github" / "workflows" / "python-modules.yml"
LOCAL_RUNNER_FILE = REPO_ROOT / "utils" / "run-modules-ci-locally.sh"

# The extraction helper below lets us compare the TWO SIDES'
# actual pytest invocations directly (by walking each script's own text),
# rather than comparing one side against this hand-maintained Python
# constant. CI_PYTEST_FLAGS stays as the DOCUMENTED expectation (and the
# thing test_local_runner_pytest_args_match_ci_workflow still pins against
# explicitly, by name, so a flag rename shows up as a readable diff rather
# than just "sets don't match"), but it is no longer the only source of
# truth: test_runner_and_workflow_flags_match_exactly below derives both
# sides from the files themselves and asserts their flag sets are equal,
# so a flag added to EITHER file without being mirrored into the other
# (not just a flag missing from this constant) now fails loudly.
CI_PYTEST_FLAGS = [
    "-k server_modules",
    "--async-backend=asyncio",
    "--html=pytest_report.html",
    "--self-contained-html",
]


def test_local_runner_pytest_args_match_ci_workflow():
    workflow_text = WORKFLOW_FILE.read_text()
    runner_text = LOCAL_RUNNER_FILE.read_text()

    # Confirm the workflow step itself still contains every flag this test
    # pins -- if CI's own step changes, this fails here first rather than
    # only failing the (silent) comparison below.
    for flag in CI_PYTEST_FLAGS:
        assert flag in workflow_text, (
            f"python-modules.yml no longer contains {flag!r} in its "
            "'Run modules tests' step -- update CI_PYTEST_FLAGS above "
            "AND utils/run-modules-ci-locally.sh to match."
        )

    # The local runner must carry every one of the same flags (it may add
    # more, like -rs, which doesn't change test selection or behaviour).
    for flag in CI_PYTEST_FLAGS:
        assert flag in runner_text, (
            f"utils/run-modules-ci-locally.sh is missing {flag!r}, which "
            "CI's 'Run modules tests' step passes. The local proof must "
            "run the same "
            "pytest invocation CI does, not a hand-rolled approximation."
        )

    # Both sides must select server_modules tests the same way (not, say,
    # one using -k and the other -m).
    assert "-k server_modules" in workflow_text
    assert "-k server_modules" in runner_text


# The checks above only verify runner ⊇ CI_PYTEST_FLAGS (every flag
# this test already knows about is present on both sides) -- a flag added
# ONLY to the local runner, never mirrored into CI's own step (or vice
# versa), would drift undetected as long as it isn't one of the flags this
# module happens to enumerate. The allowlist below is the one deliberate,
# acknowledged asymmetry (the comment on CI_PYTEST_FLAGS already names it):
# -rs is a pytest reporting flag that changes only how skip reasons are
# printed, not test selection or behaviour, so the local runner carries it
# and CI does not.
RUNNER_ONLY_ALLOWED_FLAGS = ["-rs"]


def _extract_pytest_invocation_flags(script_text: str, anchor: str) -> list:
    """Pulls every `--xxx`/`-x`-shaped token out of the `dev.py test --args`
    invocation that follows `anchor` in `script_text`, up to the next blank
    line or closing of the shell continuation. Deliberately simple (line-
    prefix matching, not a shell parser) -- this is a drift GUARD over a
    small, hand-written script, not a general-purpose arg parser."""
    start = script_text.index(anchor)
    # Walk forward from the anchor, collecting backslash-continued lines
    # until one doesn't end in a continuation.
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


def test_local_runner_has_no_undeclared_extra_pytest_flags():
    """The reverse direction of test_local_runner_pytest_args_match_ci_
    workflow: every flag the local runner's `dev.py test --args`
    invocation passes must be either one of CI_PYTEST_FLAGS, one of the
    two endpoint flags (whose presence the main test already checks, and
    whose values legitimately differ), or explicitly allowlisted above as
    a deliberate, benign asymmetry -- not a silent, unreviewed drift."""
    runner_text = LOCAL_RUNNER_FILE.read_text()
    runner_flags = _extract_pytest_invocation_flags(
        runner_text, "python3 dev.py test --args"
    )
    known_flags = (
        {f.split()[0].split("=")[0] for f in CI_PYTEST_FLAGS}
        | {"--standalone-endpoints", "--cluster-endpoints"}
        | set(RUNNER_ONLY_ALLOWED_FLAGS)
    )
    undeclared = [f for f in runner_flags if f not in known_flags]
    assert not undeclared, (
        f"utils/run-modules-ci-locally.sh passes flag(s) {undeclared} that "
        "are neither in CI_PYTEST_FLAGS, an endpoint flag, nor "
        "RUNNER_ONLY_ALLOWED_FLAGS above -- either mirror the flag into "
        "CI's own step and CI_PYTEST_FLAGS, or add it to "
        "RUNNER_ONLY_ALLOWED_FLAGS with a reason if it's a deliberate, "
        "benign asymmetry like -rs."
    )


def test_runner_and_workflow_flags_match_exactly():
    """The two tests above both compare one side's actual pytest
    invocation against CI_PYTEST_FLAGS, a hand-maintained Python constant
    -- so a flag added to BOTH the workflow file and the runner script,
    but never mirrored into CI_PYTEST_FLAGS, would still pass them both
    silently. This test instead derives both sides' flag sets directly
    from the files themselves and asserts they are equal (modulo the two
    endpoint flags, whose presence both sides already share and whose
    values legitimately differ, and RUNNER_ONLY_ALLOWED_FLAGS' one
    deliberate asymmetry) -- so a flag added to EITHER file without being
    mirrored into the other fails here even if CI_PYTEST_FLAGS was never
    touched."""
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
