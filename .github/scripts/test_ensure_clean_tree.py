"""Run the composite clean-tree guard against real temporary Git repositories."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ACTION = Path(__file__).parents[1] / "actions/ensure-clean-tree/action.yml"
SCRIPT = "\n".join(
    line[14:]
    for line in ACTION.read_text().split("          run: |\n", 1)[1].splitlines()
)


class CleanTreeTests(unittest.TestCase):
    def test_worktree_states(self):
        for state in (
            "clean",
            "ignored",
            "untracked",
            "modified",
            "staged",
            "deleted",
            "renamed",
            "binary",
        ):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)

                def git(*args):
                    return subprocess.check_output(["git", *args], cwd=root)

                git("init", "-q")
                (root / ".gitignore").write_text("ignored\n")
                (root / "tracked").write_text("original\n")
                git("add", ".")
                git(
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
                if state in ("ignored", "untracked"):
                    (root / state).write_text("artifact")
                elif state in ("modified", "staged", "binary"):
                    (root / "tracked").write_bytes(
                        b"changed\0" if state == "binary" else b"changed"
                    )
                    if state == "staged":
                        git("add", "tracked")
                elif state == "deleted":
                    (root / "tracked").unlink()
                elif state == "renamed":
                    git("mv", "tracked", "renamed")
                before = git("status", "--porcelain=v1", "--untracked-files=all")
                result = subprocess.run(
                    ["bash", "-c", SCRIPT],
                    cwd=root,
                    env=dict(os.environ, INPUT_CONTEXT="fixture operation"),
                    capture_output=True,
                    text=True,
                )
                self.assertEqual(
                    result.returncode,
                    0 if state in ("clean", "ignored") else 1,
                    result.stderr,
                )
                if result.returncode:
                    self.assertIn("Context: fixture operation", result.stdout)
                    self.assertIn("Worktree status", result.stdout)
                self.assertEqual(
                    before, git("status", "--porcelain=v1", "--untracked-files=all")
                )


if __name__ == "__main__":
    unittest.main()
