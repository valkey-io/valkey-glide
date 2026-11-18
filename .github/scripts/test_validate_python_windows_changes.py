"""Verify Windows wheel packaging permits only its declared source edits."""

from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("validate-python-windows-changes.sh").resolve()


class ValidatePythonWindowsChangesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "-q")
        self.write(".gitignore", "target/\nwheels/\n")
        for package in ("glide-async", "glide-sync", "glide-shared"):
            self.write(f"python/{package}/pyproject.toml", 'dynamic = ["version"]\n')
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

    def write(self, name, content):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def git(self, *args):
        return subprocess.run(
            ["git", *args], cwd=self.repo, check=True, capture_output=True, text=True
        ).stdout

    def validate(self, expected=0):
        result = subprocess.run(
            ["bash", str(SCRIPT)], cwd=self.repo, capture_output=True, text=True
        )
        self.assertEqual(result.returncode, expected, result.stdout + result.stderr)
        return result.stdout

    def test_clean_and_ignored_artifacts_pass(self):
        self.write("target/native.dll", "binary")
        self.write("python/glide-sync/wheels/client.whl", "wheel")
        self.validate()

    def test_preparation_edits_pass_staged_and_unstaged(self):
        for package in ("glide-async", "glide-sync", "glide-shared"):
            self.write(f"python/{package}/pyproject.toml", 'version = "0.1.0"\n')
        for package in ("glide-async", "glide-sync"):
            self.write(f"python/{package}/.gitignore", "build/\n")
        self.git("add", "python/glide-async/pyproject.toml")
        self.validate()

    def test_unexpected_tracked_edit_is_rejected_without_modifying_state(self):
        self.write(".gitignore", "# changed\n")
        before = self.git("status", "--porcelain=v1")
        self.assertIn(".gitignore", self.validate(expected=1))
        self.assertEqual(self.git("status", "--porcelain=v1"), before)

    def test_unexpected_generated_source_is_rejected(self):
        self.write("python/glide-sync/glide_sync/unexpected.py", "generated")
        self.assertIn(
            "?? python/glide-sync/glide_sync/unexpected.py", self.validate(expected=1)
        )

    def test_unexpected_lockfile_is_rejected(self):
        self.write("ffi/Cargo.lock", "changed")
        self.assertIn("ffi/Cargo.lock", self.validate(expected=1))


if __name__ == "__main__":
    unittest.main()
