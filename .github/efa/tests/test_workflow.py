# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Exercise the actual GitHub shell steps against a fake AWS CLI (requires PyYAML)."""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml  # type: ignore[import-untyped,import-not-found]

REPO = Path(__file__).resolve().parents[3]
WORKFLOW = yaml.safe_load((REPO / ".github/workflows/glide-rdma-efa.yml").read_text())
STEPS = {step["name"]: step for step in WORKFLOW["jobs"]["test-efa"]["steps"]}

ORCHESTRATE_SPEC = importlib.util.spec_from_file_location(
    "orchestrate", Path(__file__).resolve().parents[1] / "bin/orchestrate.py"
)
assert ORCHESTRATE_SPEC is not None and ORCHESTRATE_SPEC.loader is not None
orchestrate = importlib.util.module_from_spec(ORCHESTRATE_SPEC)
ORCHESTRATE_SPEC.loader.exec_module(orchestrate)


def provisioning_failure():
    """The apply command orchestrate.py runs, and the line it prints when it fails."""
    commands = []

    def failing_apply(command, env, timeout):
        commands.append(command)
        if command[1] == "apply":
            raise subprocess.CalledProcessError(1, command)

    env = {
        "CODEBUILD_RESOLVED_SOURCE_VERSION": "a" * 40,
        "CODEBUILD_BUILD_ID": "GlideRdmaEfa:12345678-1234-1234-1234-123456789abc",
        "RDMA_VPC_ID": "vpc-test",
        "RDMA_SUBNETS": "subnet-a",
        "RDMA_TEMPLATE_URL": "https://example.invalid/template.yaml",
        "RDMA_INSTANCE_PROFILE_ARN": "arn:aws:iam::123456789012:instance-profile/x",
    }
    try:
        orchestrate.orchestrate(env, failing_apply)
    except subprocess.CalledProcessError as error:
        return commands[0], f"EFA build failed: {error}"
    raise AssertionError("orchestrate did not propagate the apply failure")


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

    EXAMPLE_AWS_ID = "123456789012"
    PRIVATE_IP = "172.31.0.10"
    TEST_FAILURE_LOG = (
        "== client (i-0123456789abcdef0): 31-client-rdma-tests.sh",
        f"FAILED tests/sync_tests/test_sync_rdma.py::test_x - ClosingError: {PRIVATE_IP}:7380",
        "================= 1 failed, 7 passed, 95 deselected in 6.02s =================",
        "== Failed",
        "[rdma-ci-test] bin/rdma-pair.sh down",
        "18:24:59  ServerInstance   DELETE_IN_PROGRESS",
        "stack: NONE",
        (
            "EFA build failed: Command '['bin/run-on.sh', 'client',"
            " 'host/31-client-rdma-tests.sh']' returned non-zero exit status 1."
        ),
    )
    PROVISIONING_FAILURE_LOG = (
        (
            f"17:02:06  ClientInstance   CREATE_FAILED   Value (arn:aws:iam::{EXAMPLE_AWS_ID}:"
            "instance-profile/missing) for parameter iamInstanceProfile.arn is invalid."
        ),
        "stack: CREATE_FAILED",
        provisioning_failure()[1],
    )

    def log_page(self, lines):
        # CloudWatch events may each hold several lines.
        return (0, json.dumps({"events": [{"message": "\n\t".join(lines)}]}))

    def assert_no_identifiers(self, *texts):
        for text in texts:
            for identifier in (
                self.EXAMPLE_AWS_ID,
                self.PRIVATE_IP,
                "i-0123456789abcdef0",
            ):
                self.assertNotIn(identifier, text)

    def test_failed_test_step_reports_step_and_counts_only(self):
        result, summary = self.report_build(
            self.failed_build(), self.log_page(self.TEST_FAILURE_LOG)
        )
        for text in (
            "GlideRdmaEfa:test",
            "BUILD FAILED COMMAND_EXECUTION_ERROR",
            "bin/run-on.sh client host/31-client-rdma-tests.sh returned non-zero exit status 1",
            "1 failed, 7 passed, 95 deselected",
        ):
            self.assertIn(text, summary)
            self.assertIn(text, result.stdout)
        self.assertNotIn("orchestrator exited 1", summary + result.stdout)
        self.assertNotIn("DELETE_IN_PROGRESS", summary + result.stdout)
        self.assert_no_identifiers(summary, result.stdout)

    def test_failed_provisioning_reports_step_without_the_reason(self):
        result, summary = self.report_build(
            self.failed_build(), self.log_page(self.PROVISIONING_FAILURE_LOG)
        )
        apply_command, _ = provisioning_failure()
        self.assertIn(
            " ".join(apply_command) + " returned non-zero exit status 1", summary
        )
        self.assertIn("No pytest summary", summary)
        self.assertNotIn("CREATE_FAILED", summary + result.stdout)
        self.assert_no_identifiers(summary, result.stdout)

    def test_failed_command_with_unexpected_arguments_is_not_printed(self):
        line = (
            "EFA build failed: Command '['bin/run-on.sh', 'client', 'host/31-x.sh',"
            f" 'SERVER_IP={self.PRIVATE_IP}']' returned non-zero exit status 1."
        )
        result, summary = self.report_build(self.failed_build(), self.log_page([line]))
        self.assertIn("No recognized failure line", summary)
        self.assert_no_identifiers(summary, result.stdout)

    def test_near_miss_step_arguments_are_rejected(self):
        for args in (
            "'bin/rdma-pair.sh', 'apply', 'MaxHostLifetimeMinutes=120-secret'",
            "'bin/rdma-pair.sh', 'apply', 'MaxHostLifetimeMinutes=12345'",
            "'bin/rdma-pair.sh', 'apply', 'MaxHostLifetimeMinutes='",
            "'bin/run-on.sh.evil', 'client'",
            "'bin/run-on.sh', 'client', 'host/31-x.sh', 'GLIDE_SOURCE=abc'",
            "'bin/run-on.sh', 'client, i-0123456789abcdef0'",
        ):
            with self.subTest(args=args):
                line = f"EFA build failed: Command '[{args}]' returned non-zero exit status 1."
                result, summary = self.report_build(
                    self.failed_build(), self.log_page([line])
                )
                self.assertIn("No recognized failure line", summary)
                self.assertNotIn("bin/run-on.sh", summary + result.stdout)

    def test_timeout_signal_and_fixed_failures_name_the_step(self):
        step = "['bin/run-on.sh', 'server', 'host/20-server-build.sh']"
        for line, expected in (
            (
                f"EFA build failed: Command '{step}' timed out after 3599.5 seconds",
                "bin/run-on.sh server host/20-server-build.sh timed out after 3599.5 seconds",
            ),
            (
                f"EFA build failed: Command '{step}' died with <Signals.SIGKILL: 9>.",
                "bin/run-on.sh server host/20-server-build.sh died with <Signals.SIGKILL: 9>",
            ),
            (
                f"EFA build failed: Command {step} returned non-zero exit status 2.",
                "bin/run-on.sh server host/20-server-build.sh returned non-zero exit status 2",
            ),
            (
                "EFA build failed: EFA orchestration exceeded its 65 minute work budget",
                "EFA orchestration exceeded its 65 minute work budget",
            ),
            ("EFA build failed: Received signal 15", "Received signal 15"),
        ):
            with self.subTest(line=line):
                result, _ = self.report_build(
                    self.failed_build(), self.log_page([line])
                )
                self.assertIn(expected, result.stdout)

    def test_test_counts_require_the_exact_summary_line(self):
        for line in (
            f"{self.PRIVATE_IP} ======= 1 failed, 7 passed in 6.02s =======",
            "======= 1 failed, 7 passed in 6.02s ======= i-0123456789abcdef0",
            f"======= 1 failed, 7 passed, {self.PRIVATE_IP} in 6.02s =======",
        ):
            with self.subTest(line=line):
                result, summary = self.report_build(
                    self.failed_build(), self.log_page([line])
                )
                self.assertIn("No pytest summary", summary)
                self.assert_no_identifiers(summary, result.stdout)

    def test_unexpected_phase_fields_are_not_printed(self):
        build = self.failed_build()
        build["phases"][0]["contexts"][0]["statusCode"] = (
            f"arn:aws:iam::{self.EXAMPLE_AWS_ID}:x"
        )
        result, summary = self.report_build(build, self.log_page([]))
        self.assertIn("BUILD FAILED ?", summary)
        self.assert_no_identifiers(summary, result.stdout)

    def test_bad_json_reports_a_fixed_reason(self):
        result = self.run_step("Report build result", [(0, "invalid json")])
        self.assertIn("unreadable response", result.stdout)

    def test_log_request_arguments(self):
        self.report_build(self.failed_build(), self.log_page(self.TEST_FAILURE_LOG))
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

    def test_log_access_failure_reports_only_the_error_code(self):
        denied = (
            "An error occurred (AccessDeniedException) when calling the GetLogEvents"
            f" operation: User: arn:aws:sts::{self.EXAMPLE_AWS_ID}:assumed-role/r/s is not"
            " authorized to perform: logs:GetLogEvents"
        )
        result, summary = self.report_build(self.failed_build(), (254, denied))
        self.assertIn("AccessDeniedException", summary)
        self.assertIn("BUILD FAILED COMMAND_EXECUTION_ERROR", summary)
        self.assert_no_identifiers(summary, result.stdout)

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

    def test_unrecognized_log_lines_are_never_printed(self):
        message = "x" * 40000 + "\n::error::remote\n<script>bad</script>"
        result, summary = self.report_build(
            self.failed_build(), (0, json.dumps({"events": [{"message": message}]}))
        )
        self.assertLess(len(result.stdout), 2000)
        self.assertLess(len(summary), 2000)
        for text in ("xxxx", "::error::remote", "script"):
            self.assertNotIn(text, summary + result.stdout)
        token = result.stdout.splitlines()[0].removeprefix("::stop-commands::")
        self.assertEqual(result.stdout.splitlines()[-1], f"::{token}::")


if __name__ == "__main__":
    unittest.main()
