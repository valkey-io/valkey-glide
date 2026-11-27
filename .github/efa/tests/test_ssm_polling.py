# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Run the real host launcher against deterministic SSM responses."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE.parent / "bin/run-on.sh"


def error(code):
    return {
        "error": f"An error occurred ({code}) when calling GetCommandInvocation: diagnostic"
    }


class SsmPollingTests(unittest.TestCase):
    def execute(self, responses):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state_path = root / "state.json"
            state_path.write_text(json.dumps({"responses": responses}))
            aws = root / "aws"
            aws.write_text(
                f"#!{sys.executable}\n" + (HERE / "fixtures/ssm_cli.py").read_text()
            )
            aws.chmod(0o755)
            sleep = root / "sleep"
            sleep.write_text("#!/bin/sh\nexit 0\n")
            sleep.chmod(0o755)
            host = root / "host.sh"
            host.write_text("#!/bin/bash\necho test\n")
            result = subprocess.run(
                ["bash", str(SCRIPT), "server", str(host)],
                env=dict(
                    os.environ,
                    PATH=f"{root}:{os.environ['PATH']}",
                    FAKE_STATE=str(state_path),
                ),
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            return result, json.loads(state_path.read_text())

    def test_active_states_continue_until_success(self):
        result, state = self.execute(["Pending", "InProgress", "Delayed", "Success"])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(state["polls"], 4)
        self.assertIn("host output", result.stdout)
        self.assertIn("stderr: host diagnostic", result.stdout)

    def test_retryable_errors_recover(self):
        for code in (
            "InvocationDoesNotExist",
            "ThrottlingException",
            "Throttling",
            "InternalServerError",
            "ServiceUnavailable",
        ):
            with self.subTest(code=code):
                result, state = self.execute([error(code), "Success"])
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(state["polls"], 2)
                self.assertIn(code, result.stderr)
                self.assertIn("command-test on i-host", result.stderr)

    def test_persistent_errors_are_bounded(self):
        result, state = self.execute([error("InvocationDoesNotExist")])
        self.assertEqual(result.returncode, 254)
        self.assertEqual(state["polls"], 12)
        self.assertIn("12 consecutive errors", result.stderr)
        self.assertNotIn("output_queries", state)

    def test_successful_poll_resets_error_budget(self):
        result, state = self.execute(
            [error("ThrottlingException")] * 11
            + ["InProgress"]
            + [error("InternalServerError")] * 11
            + ["Success"]
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(state["polls"], 24)

    def test_permanent_errors_stop_immediately(self):
        for code in (
            "AccessDeniedException",
            "InvalidCommandId",
            "InvalidInstanceId",
            "InvalidPluginName",
            "ExpiredToken",
            "UnknownError",
        ):
            with self.subTest(code=code):
                result, state = self.execute([error(code), "Success"])
                self.assertEqual(result.returncode, 254)
                self.assertEqual(state["polls"], 1)
                self.assertIn(code, result.stderr)
                self.assertNotIn("output_queries", state)

    def test_cli_credentials_error_stops_immediately(self):
        result, state = self.execute([{"error": "Unable to locate credentials"}])
        self.assertEqual(result.returncode, 254)
        self.assertEqual(state["polls"], 1)
        self.assertIn("Unable to locate credentials", result.stderr)

    def test_remote_failure_prints_output_and_fails(self):
        result, state = self.execute(["InProgress", "Failed"])
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(state["polls"], 2)
        self.assertIn("host output", result.stdout)
        self.assertIn("stderr: host diagnostic", result.stdout)
        self.assertIn("== Failed", result.stdout)

    def test_network_errors_recover(self):
        for message in (
            'Read timeout on endpoint URL: "https://ssm.us-east-1.amazonaws.com/"',
            'Could not connect to the endpoint URL: "https://ssm.us-east-1.amazonaws.com/"',
        ):
            with self.subTest(message=message):
                result, state = self.execute([{"error": message}, "Success"])
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(state["polls"], 2)
                self.assertIn(message, result.stderr)

    def test_persistent_network_error_is_bounded(self):
        result, state = self.execute(
            [{"error": 'Read timeout on endpoint URL: "https://ssm.example/"'}]
        )
        self.assertEqual(result.returncode, 254)
        self.assertEqual(state["polls"], 12)
        self.assertIn("12 consecutive errors", result.stderr)

    def test_remote_pipeline_retains_tail_and_exit_status(self):
        _, state = self.execute(["Success"])
        command = state["parameters"]["commands"][-1]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            host = root / "host.sh"
            summary = "=== 2 failed, 6 passed in 1.00s ==="
            output = "early output\n" + "x" * 30000 + "\n" + summary + "\n"
            (root / "output").write_text(output)
            # Execute the actual remote pipeline, redirecting only host paths to
            # a sandbox. Check both success and failure, not just command text.
            command = command.replace("cd /root", f"cd {root}").replace(
                "/var/log/rdma/", f"{root}/"
            )
            for exit_code in (0, 7):
                with self.subTest(exit_code=exit_code):
                    host.write_text(f"cat {root}/output\nexit {exit_code}\n")
                    result = subprocess.run(
                        ["bash", "-c", command],
                        capture_output=True,
                        check=False,
                        timeout=5,
                    )
                    self.assertEqual(result.returncode, exit_code)
                    self.assertEqual(result.stdout, output.encode()[-23999:])
                    self.assertEqual((root / "host.log").read_text(), output)
                    self.assertLess(len(result.stdout), 24000)
                    self.assertTrue(result.stdout.endswith(summary.encode() + b"\n"))
