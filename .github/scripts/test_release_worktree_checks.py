"""Run the release workflows' actual validation blocks in isolated repositories."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def workflow_step(workflow, name):
    lines = (ROOT / ".github/workflows" / workflow).read_text().splitlines()
    start = lines.index("            - name: " + name)
    start = (
        next(i for i in range(start, len(lines)) if lines[i] == "              run: |")
        + 1
    )
    end = start
    while end < len(lines) and (
        not lines[end].strip() or lines[end].startswith("                  ")
    ):
        end += 1
    return "\n".join(line[18:] for line in lines[start:end])


VALIDATORS = {
    "pypi-binary": workflow_step(
        "pypi-cd.yml", "Validate worktree changes from PyPI binary build"
    ),
    "pypi-async": workflow_step(
        "pypi-cd.yml", "Validate worktree changes from async PyPI packaging"
    ),
    "pypi-sync": workflow_step(
        "pypi-cd.yml", "Validate worktree changes from sync PyPI packaging"
    ),
    "npm-assembly": workflow_step(
        "npm-cd.yml", "Validate npm package assembly changes"
    ),
    "npm-rc": workflow_step(
        "npm-cd.yml", "Validate release-candidate worktree changes"
    ),
    "ort": workflow_step(
        "ort.yml", "Validate attribution changes stay within declared scope"
    ),
}
# Exercise the function without starting a benchmark or editing its module.
VALIDATORS["go-benchmark"] = (
    workflow_step("go-cd.yml", "test newly released go client").split(
        "# change go/benchmarks/go.mod"
    )[0]
    + "\nvalidate_benchmark_changes\n"
)
ATTRIBUTIONS = {
    "PYTHON_ASYNC_ATTRIBUTIONS": "python/glide-async/THIRD_PARTY_LICENSES_PYTHON_ASYNC",
    "PYTHON_SYNC_ATTRIBUTIONS": "python/glide-sync/THIRD_PARTY_LICENSES_PYTHON_SYNC",
    "NODE_ATTRIBUTIONS": "node/THIRD_PARTY_LICENSES_NODE",
    "RUST_ATTRIBUTIONS": "glide-core/THIRD_PARTY_LICENSES_RUST",
    "JAVA_ATTRIBUTIONS": "java/THIRD_PARTY_LICENSES_JAVA",
    "GO_ATTRIBUTIONS": "go/THIRD_PARTY_LICENSES_GO",
}
ALLOWED_TRACKED = {
    "pypi-binary": "ffi/.cargo/config.toml",
    "pypi-async": "python/glide-async/pyproject.toml",
    "pypi-sync": "python/glide-sync/pyproject.toml",
    "npm-assembly": "node/npm/linux-x64-gnu/package.json",
    "npm-rc": "utils/release-candidate-testing/node/package.json",
    "ort": ATTRIBUTIONS["NODE_ATTRIBUTIONS"],
    "go-benchmark": "go/benchmarks/go.mod",
}


class ReleaseWorktreeChecksTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.env = dict(os.environ, GITHUB_WORKSPACE=str(self.repo), **ATTRIBUTIONS)
        self.git("init", "-q")
        for name in (
            ".gitignore",
            "node/.gitignore",
            "python/.gitignore",
            "go/.gitignore",
        ):
            self.write(name, (ROOT / name).read_text())
        for name in set(ALLOWED_TRACKED.values()):
            self.write(name, "original\n")
        self.write("unrelated-source.txt", "original\n")
        self.git("add", ".")
        self.git(
            "-c",
            "user.name=Test",
            "-c",
            "user.email=test@example.com",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-qm",
            "fixture",
        )

    def write(self, name, text="generated\n"):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.repo, check=True, capture_output=True, text=True
        ).stdout

    def validate(self, policy, expected=0):
        before = self.git("status", "--porcelain=v1")
        result = subprocess.run(
            ["bash", "-eo", "pipefail", "-c", VALIDATORS[policy]],
            cwd=self.repo,
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(
            result.returncode, expected, f"{policy}: {result.stdout}{result.stderr}"
        )
        self.assertEqual(self.git("status", "--porcelain=v1"), before)
        return result.stdout

    def test_clean_repositories_pass(self):
        for policy in VALIDATORS:
            with self.subTest(policy=policy):
                self.validate(policy)

    def test_expected_tracked_changes_pass(self):
        for policy, path in ALLOWED_TRACKED.items():
            with self.subTest(policy=policy):
                self.write(path, "changed\n")
                self.validate(policy)
                self.git("restore", path)

    def test_unrelated_tracked_changes_fail(self):
        self.write("unrelated-source.txt", "changed\n")
        for policy in VALIDATORS:
            with self.subTest(policy=policy):
                self.validate(policy, expected=1)

    def test_unrelated_untracked_files_fail(self):
        self.write("unexpected-generated.txt")
        for policy in VALIDATORS:
            with self.subTest(policy=policy):
                self.assertIn(
                    "unexpected-generated.txt", self.validate(policy, expected=1)
                )

    def test_ignored_build_artifacts_pass(self):
        self.write("ffi/target/generated.txt")
        self.write("python/glide-sync/dist/package.tar.gz")
        self.write("node/node_modules/package/index.js")
        for policy in VALIDATORS:
            with self.subTest(policy=policy):
                self.validate(policy)

    def test_python_binary_packaging_outputs_pass(self):
        ignore = (self.repo / "python/.gitignore").read_text()
        self.write(
            "python/.gitignore",
            "\n".join(line for line in ignore.splitlines() if "pb2.py" not in line),
        )
        for proto in ("command_request", "connection_request", "response"):
            for suffix in ("py", "pyi"):
                self.write(
                    f"python/glide-shared/glide_shared/protobuf/{proto}_pb2.{suffix}"
                )
        for client in ("glide-sync", "glide-async"):
            (self.repo / "python" / client / ".gitignore").symlink_to("../.gitignore")
        self.write("pypy3.11-v7.3.19-macos_arm64.tar.bz2")
        self.write("pypy3.11-v7.3.19-macos_x86_64.tar.bz2")
        self.validate("pypi-binary")
        self.write("python/glide-shared/glide_shared/protobuf/unexpected.py")
        self.validate("pypi-binary", expected=1)

    def test_npm_assembly_outputs_pass(self):
        self.write("node/npm/glide/package.json")
        self.write("node/npm/glide/build-ts/index.js")
        for platform in (
            "darwin-arm64",
            "darwin-x64",
            "linux-arm64-gnu",
            "linux-arm64-musl",
            "linux-x64-gnu",
            "linux-x64-musl",
            "win32-x64-msvc",
        ):
            for name in ("LICENSE", "README.md"):
                self.write(f"node/npm/{platform}/{name}")
        self.validate("npm-assembly")
        self.write("node/npm/linux-x64-gnu/unexpected.txt")
        self.validate("npm-assembly", expected=1)

    def test_go_release_benchmark_writes_results_outside_checkout(self):
        # Release tags remove go/.gitignore, so its usual benchmark result ignore
        # rule is absent. The release command must select an external result file.
        (self.repo / "go/.gitignore").unlink()
        self.git("add", "go/.gitignore")
        self.git(
            "-c",
            "user.name=Test",
            "-c",
            "user.email=test@example.com",
            "-c",
            "commit.gpgsign=false",
            "commit",
            "-qm",
            "release fixture",
        )
        command = workflow_step("go-cd.yml", "test newly released go client")
        self.assertIn(
            '-resultsFile "$RUNNER_TEMP/go-release-benchmark-results.json"', command
        )
        self.validate("go-benchmark")
        self.write("go/benchmarks/results/go-results.json")
        self.validate("go-benchmark", expected=1)

    def test_ort_enforcement_only_fails_on_worktree_violations(self):
        code = workflow_step(
            "ort.yml", "Enforce worktree and attribution scope outcomes"
        )
        for clean, scope, expected in (
            ("failure", "skipped", 1),
            ("success", "failure", 1),
            ("success", "success", 0),
            ("skipped", "skipped", 0),
        ):
            with self.subTest(clean=clean, scope=scope):
                env = dict(
                    self.env, CLEAN_TREE_OUTCOME=clean, ATTRIBUTION_SCOPE_OUTCOME=scope
                )
                result = subprocess.run(
                    ["bash", "-e", "-c", code], env=env, capture_output=True, text=True
                )
                self.assertEqual(result.returncode, expected)


if __name__ == "__main__":
    unittest.main()
