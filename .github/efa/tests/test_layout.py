# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Check repository entry points and CI routing after relocating EFA tooling."""

import fnmatch
import shlex
import unittest
from pathlib import Path

import yaml  # type: ignore[import-untyped,import-not-found]

REPO = Path(__file__).resolve().parents[3]
EFA = REPO / ".github/efa"


def workflow(name):
    # BaseLoader preserves the GitHub Actions `on` key as text.
    return yaml.load(
        (REPO / ".github/workflows" / name).read_text(), Loader=yaml.BaseLoader
    )


def matches(path, patterns):
    # These workflows use literal paths, recursive ** globs and *.md exclusions.
    included = False
    for pattern in patterns:
        excluded = pattern.startswith("!")
        if fnmatch.fnmatchcase(path, pattern.lstrip("!")):
            included = not excluded
    return included


class LayoutTests(unittest.TestCase):
    def test_tooling_changes_run_only_the_lightweight_test_workflow(self):
        paths = [
            str(path.relative_to(REPO))
            for path in EFA.rglob("*")
            if path.is_file() and "__pycache__" not in path.parts
        ] + [
            ".github/buildspec/rdma-efa.yml",
            ".github/workflows/glide-rdma-efa.yml",
            ".github/workflows/efa-orchestration.yml",
        ]
        lightweight = workflow("efa-orchestration.yml")
        for event in ("push", "pull_request"):
            for path in paths:
                with self.subTest(event=event, path=path):
                    self.assertTrue(matches(path, lightweight["on"][event]["paths"]))
                    for name in (
                        "glide-rdma.yml",
                        "ffi.yml",
                        "glide-core.yml",
                        "glide-core-engine.yml",
                    ):
                        self.assertFalse(
                            matches(path, workflow(name)["on"][event]["paths"]), name
                        )

    def test_codebuild_entrypoint_exists(self):
        spec = yaml.safe_load((REPO / ".github/buildspec/rdma-efa.yml").read_text())
        command = shlex.split(spec["phases"]["build"]["commands"][0])
        entrypoint = REPO / command[1]
        self.assertTrue(entrypoint.is_file())
        self.assertEqual(entrypoint, EFA / "bin/orchestrate.py")

    def test_test_discovery_points_at_this_suite(self):
        steps = workflow("efa-orchestration.yml")["jobs"]["efa-orchestration"]["steps"]
        commands = [line for step in steps for line in step.get("run", "").splitlines()]
        discovery = next(
            shlex.split(line) for line in commands if "unittest discover" in line
        )
        self.assertEqual(REPO / discovery[discovery.index("-s") + 1], EFA / "tests")


if __name__ == "__main__":
    unittest.main()
