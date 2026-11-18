# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Exercise the actual GitHub shell steps against a fake AWS CLI (requires PyYAML)."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[4]
WORKFLOW = yaml.safe_load((REPO / ".github/workflows/glide-rdma-efa.yml").read_text())
STEPS = {step["name"]: step for step in WORKFLOW["jobs"]["test-efa"]["steps"]}


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        aws = self.root / "aws"
        aws.write_text(
            f"#!{sys.executable}\n"
            "import json, os, pathlib, sys\n"
            "root = pathlib.Path(os.environ['FAKE_ROOT'])\n"
            "with (root / 'calls').open('a') as f: f.write(json.dumps(sys.argv[1:]) + '\\n')\n"
            "responses = json.loads((root / 'responses').read_text())\n"
            "code, output = responses.pop(0)\n"
            "(root / 'responses').write_text(json.dumps(responses))\n"
            "print(output)\n"
            "sys.exit(code)\n"
        )
        aws.chmod(0o755)
        sleep = self.root / "sleep"
        sleep.write_text("#!/bin/sh\nexit 0\n")
        sleep.chmod(0o755)
        self.env = dict(
            os.environ,
            PATH=f"{self.root}:{os.environ['PATH']}",
            FAKE_ROOT=str(self.root),
            SOURCE_VERSION="b" * 40,
            KEEP_HOSTS="false",
            GITHUB_RUN_ID="123",
            GITHUB_RUN_ATTEMPT="2",
            GITHUB_ENV=str(self.root / "env"),
            GITHUB_STEP_SUMMARY=str(self.root / "summary"),
            CODEBUILD_BUILD_ID="GlideRdmaEfa:test",
        )

    def run_step(self, name, responses):
        (self.root / "responses").write_text(json.dumps(responses))
        return subprocess.run(
            [
                "/bin/bash",
                "--noprofile",
                "--norc",
                "-e",
                "-o",
                "pipefail",
                "-c",
                STEPS[name]["run"],
            ],
            env=self.env,
            check=False,
            capture_output=True,
            text=True,
            timeout=5,
        )

    def test_dispatch_uses_exact_sha_and_retry_specific_token(self):
        result = self.run_step("Trigger CodeBuild", [(0, "GlideRdmaEfa:test")])
        self.assertEqual(result.returncode, 0, result.stderr)
        args = json.loads((self.root / "calls").read_text())
        self.assertEqual(args[args.index("--source-version") + 1], "b" * 40)
        self.assertEqual(args[args.index("--idempotency-token") + 1], "123-2")
        self.assertIn(
            "CODEBUILD_BUILD_ID=GlideRdmaEfa:test", (self.root / "env").read_text()
        )

    def test_dispatch_failure_does_not_publish_build_id(self):
        result = self.run_step("Trigger CodeBuild", [(1, "AccessDenied")])
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / "env").exists())

    def test_poll_waits_for_success(self):
        result = self.run_step(
            "Wait for CodeBuild", [(0, "IN_PROGRESS"), (0, "SUCCEEDED")]
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_failed_missing_or_unreadable_build_fails_job(self):
        for response in [
            (0, "FAILED"),
            (0, "FAULT"),
            (0, "STOPPED"),
            (0, "TIMED_OUT"),
            (0, "None"),
            (1, "AccessDenied"),
        ]:
            with self.subTest(response=response):
                result = self.run_step("Wait for CodeBuild", [response])
                self.assertNotEqual(result.returncode, 0)

    def test_logs_link_is_written_to_summary(self):
        result = self.run_step("Link build logs", [(0, "https://example.invalid/logs")])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "[CodeBuild logs](https://example.invalid/logs)",
            (self.root / "summary").read_text(),
        )

    def test_missing_logs_do_not_make_broken_link(self):
        result = self.run_step("Link build logs", [(0, "None")])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / "summary").exists())


if __name__ == "__main__":
    unittest.main()
