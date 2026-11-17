"""Exercise the RC allowlist in isolated Git repositories (no server required)."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("validate-go-rc-changes.sh").resolve()
IMPORT = "github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"


class ValidateGoRcChangesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / "repo"
        self.repo.mkdir()
        self.env = dict(os.environ, RUNNER_TEMP=self.temp.name, RC_VERSION="v2.0.0-rc1")
        self.git("init", "-q")
        self.write(".gitignore", "ignored/\n")
        self.write("go/go.mod", "module example\n")
        self.write("go/go.sum", "original\n")
        self.write("go/integTest/import_test.go", f'import "{IMPORT}"\n')
        self.write("go/integTest/other_test.go", "package test\n")
        self.write("utils/cluster_manager.py", "# original\n")
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
        self.run_script("--record-import-paths")

    def write(self, name, content):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.repo, check=True, capture_output=True, text=True
        ).stdout

    def run_script(self, argument="Go RC workload", expected=0):
        result = subprocess.run(
            ["bash", str(SCRIPT), argument],
            cwd=self.repo,
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, expected, result.stdout + result.stderr)
        return result.stdout

    def test_clean_and_ignored_files_pass(self):
        self.write("ignored/artifact", "generated\n")
        self.run_script()

    def test_records_only_matching_go_files(self):
        self.write("go/integTest/nested/matching.go", IMPORT)
        self.write("go/integTest/not_go.txt", IMPORT)
        self.run_script("--record-import-paths")
        recorded = (Path(self.temp.name) / "go-rc-import-paths").read_text()
        self.assertEqual(
            recorded.splitlines(),
            ["go/integTest/import_test.go", "go/integTest/nested/matching.go"],
        )

    def test_no_matching_imports_records_empty_list(self):
        self.write("go/integTest/import_test.go", "package test\n")
        self.run_script("--record-import-paths")
        self.assertEqual((Path(self.temp.name) / "go-rc-import-paths").read_text(), "")

    def test_declared_changes_pass_staged_and_unstaged(self):
        self.write("go/go.mod", "updated\n")
        self.write("go/go.sum", "updated\n")
        self.write("go/integTest/import_test.go", "package test\n")
        self.git("add", "go/go.mod")
        self.run_script()

    def test_undeclared_go_file_is_rejected(self):
        self.write("go/integTest/other_test.go", "changed\n")
        self.assertIn("go/integTest/other_test.go", self.run_script(expected=1))

    def test_new_go_file_is_rejected(self):
        self.write("go/integTest/new_test.go", IMPORT)
        self.assertIn("?? go/integTest/new_test.go", self.run_script(expected=1))

    def test_cluster_manager_exemption_depends_on_major_version(self):
        self.write("utils/cluster_manager.py", "# changed\n")
        for version, expected in (
            ("v1.3.0-rc1", 0),
            ("1.3.0", 0),
            ("v2.0.0-rc1", 1),
            ("3.0.0", 1),
        ):
            with self.subTest(version=version):
                self.env["RC_VERSION"] = version
                self.run_script(expected=expected)

    def test_unexpected_tracked_change_reports_label_and_preserves_state(self):
        self.write(".gitignore", "# changed\n")
        self.git("add", ".gitignore")
        before = self.git("status", "--porcelain=v1")
        output = self.run_script("Go RC script", expected=1)
        self.assertIn("Unexpected worktree changes after Go RC script:", output)
        self.assertIn("Unexpected paths (first 200 entries):\n  .gitignore", output)
        self.assertEqual(self.git("status", "--porcelain=v1"), before)

    def test_unexpected_untracked_change_is_rejected(self):
        self.write("artifact.txt", "generated\n")
        self.assertIn("?? artifact.txt", self.run_script(expected=1))

    def test_deleted_unexpected_file_is_rejected(self):
        (self.repo / ".gitignore").unlink()
        self.run_script(expected=1)


if __name__ == "__main__":
    unittest.main()
