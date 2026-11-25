# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Exercise the actual GitHub shell steps against a fake AWS CLI (requires PyYAML)."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml  # type: ignore[import-untyped,import-not-found]

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

    def report_build(self, build, log_response=None):
        responses = [(0, json.dumps({"builds": [build]}))]
        if log_response is not None:
            responses.append(log_response)
        result = self.run_step("Report build result", responses)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result, (self.root / "summary").read_text()

    def failed_build(self):
        return {
            "buildStatus": "FAILED",
            "logs": {
                "deepLink": "https://example.invalid/logs",
                "groupName": "/aws/codebuild/GlideRdmaEfa",
                "streamName": "build-stream",
            },
            "phases": [
                {
                    "phaseType": "BUILD",
                    "phaseStatus": "FAILED",
                    "contexts": [
                        {
                            "statusCode": "COMMAND_EXECUTION_ERROR",
                            "message": "orchestrator exited 1",
                        }
                    ],
                }
            ],
        }

    def test_success_links_logs_without_fetching_them(self):
        build = self.failed_build()
        build["buildStatus"] = "SUCCEEDED"
        _, summary = self.report_build(build)
        self.assertIn('href="https://example.invalid/logs"', summary)
        self.assertIn("SUCCEEDED", summary)
        self.assertEqual(len((self.root / "calls").read_text().splitlines()), 1)

    def test_failed_build_reports_phase_and_log_excerpt(self):
        result, summary = self.report_build(
            self.failed_build(),
            (0, json.dumps({"events": [{"message": "test failed"}]})),
        )
        for text in (
            "GlideRdmaEfa:test",
            "FAILED",
            "BUILD",
            "orchestrator exited 1",
            "test failed",
        ):
            self.assertIn(text, summary)
        self.assertIn("test failed", result.stdout)
        calls = [
            json.loads(line) for line in (self.root / "calls").read_text().splitlines()
        ]
        args = calls[1]
        self.assertEqual(args[:2], ["logs", "get-log-events"])
        self.assertEqual(args[args.index("--limit") + 1], "200")
        self.assertIn("--no-paginate", args)
        self.assertIn("--no-start-from-head", args)
        self.assertEqual(args[args.index("--log-stream-name") + 1], "build-stream")
        self.assertTrue(STEPS["Report build result"]["continue-on-error"])
        self.assertIn("always()", STEPS["Report build result"]["if"])

    def test_missing_stream_reports_reason_without_fetching_logs(self):
        _, summary = self.report_build({"buildStatus": "FAILED"})
        self.assertIn("has not provided", summary)
        self.assertNotIn("href=", summary)
        self.assertEqual(len((self.root / "calls").read_text().splitlines()), 1)

    def test_log_access_failure_preserves_build_diagnostics(self):
        _, summary = self.report_build(
            self.failed_build(), (254, "AccessDeniedException")
        )
        self.assertIn("AccessDeniedException", summary)
        self.assertIn("orchestrator exited 1", summary)
        self.assertIn("FAILED", summary)

    def test_empty_log_page_is_explicit(self):
        _, summary = self.report_build(self.failed_build(), (0, '{"events": []}'))
        self.assertIn("logs may not have arrived yet", summary)

    def test_metadata_errors_do_not_fail_reporting(self):
        for response in (
            (254, "AccessDenied"),
            (0, '{"builds": []}'),
            (0, "invalid json"),
        ):
            with self.subTest(response=response):
                result = self.run_step("Report build result", [response])
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("Build diagnostics unavailable", result.stdout)

    def test_excerpt_is_bounded_and_remote_output_is_not_interpreted(self):
        message = (
            "omitted-prefix" + "x" * 40000 + "\n::error::remote\n<script>bad</script>"
        )
        result, summary = self.report_build(
            self.failed_build(), (0, json.dumps({"events": [{"message": message}]}))
        )
        self.assertNotIn("omitted-prefix", result.stdout)
        self.assertLess(len(result.stdout), 34000)
        self.assertLess(len(summary), 10000)
        self.assertNotIn("<script>", summary)
        self.assertIn("&lt;script&gt;", summary)
        token = result.stdout.splitlines()[0].removeprefix("::stop-commands::")
        self.assertEqual(result.stdout.splitlines()[-1], f"::{token}::")


if __name__ == "__main__":
    unittest.main()
