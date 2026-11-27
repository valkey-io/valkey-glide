# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
import importlib.util
import os
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "orchestrate", Path(__file__).resolve().parents[1] / "bin/orchestrate.py"
)
assert SPEC is not None and SPEC.loader is not None
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class OrchestrationTests(unittest.TestCase):
    def setUp(self):
        self.env = {
            "CODEBUILD_RESOLVED_SOURCE_VERSION": "a" * 40,
            "CODEBUILD_BUILD_ID": "GlideRdmaEfa:12345678-1234-1234-1234-123456789abc",
            "RDMA_VPC_ID": "vpc-test",
            "RDMA_SUBNETS": "subnet-a,subnet-b",
            "RDMA_TEMPLATE_URL": "https://templates.s3.us-east-1.amazonaws.com/efa/sha.yaml",
            "RDMA_INSTANCE_PROFILE_ARN": "arn:aws:iam::123456789012:instance-profile/hosts",
        }
        self.calls = []

    def record(self, command, env, timeout):
        self.calls.append((command, env, timeout))

    def test_success_builds_exact_commit_and_deletes_even_with_keep(self):
        self.env["KEEP_HOSTS"] = "true"
        module.orchestrate(self.env, self.record)
        commands = [call[0] for call in self.calls]
        self.assertEqual(
            commands[0], ["bin/rdma-pair.sh", "apply", "MaxHostLifetimeMinutes=120"]
        )
        self.assertEqual(commands[-1], ["bin/rdma-pair.sh", "down"])
        setup = next(cmd for cmd in commands if "host/30-client-setup.sh" in cmd)
        self.assertEqual(setup[-1], "GLIDE_SOURCE=" + "a" * 40)
        self.assertEqual(commands[-2][-1], "host/31-client-rdma-tests.sh")
        self.assertEqual(len(commands), 11)
        self.assertEqual(self.calls[-1][2], 900)
        self.assertEqual(
            self.calls[0][1]["RDMA_STACK"],
            "rdma-ci-12345678-1234-1234-1234-123456789abc",
        )

    def test_every_failed_step_attempts_cleanup(self):
        for fail_at in range(10):
            with self.subTest(fail_at=fail_at):
                self.calls = []

                def fail(command, env, timeout, failure_index=fail_at):
                    self.record(command, env, timeout)
                    if len(self.calls) == failure_index + 1:
                        raise RuntimeError("step failed")

                with self.assertRaisesRegex(RuntimeError, "step failed"):
                    module.orchestrate(self.env, fail)
                self.assertEqual(self.calls[-1][0], ["bin/rdma-pair.sh", "down"])
                self.assertEqual(len(self.calls), fail_at + 2)

    def test_failed_build_can_retain_hosts(self):
        self.env["KEEP_HOSTS"] = "true"

        def fail(*args):
            self.record(*args)
            raise RuntimeError("provisioning failed")

        with self.assertRaises(RuntimeError):
            module.orchestrate(self.env, fail)
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(
            self.calls[0][0],
            ["bin/rdma-pair.sh", "apply", "MaxHostLifetimeMinutes=120"],
        )

    def test_cleanup_failure_fails_build(self):
        def fail(command, env, timeout):
            if command[-1] == "down":
                raise RuntimeError("delete failed")

        with self.assertRaisesRegex(RuntimeError, "delete failed"):
            module.orchestrate(self.env, fail)

    def test_interruption_attempts_cleanup(self):
        def fail(*args):
            self.record(*args)
            if len(self.calls) == 2:
                raise KeyboardInterrupt()

        with self.assertRaises(KeyboardInterrupt):
            module.orchestrate(self.env, fail)
        self.assertEqual(self.calls[-1][0][-1], "down")

    def test_invalid_configuration_never_provisions(self):
        for key, value in (
            ("CODEBUILD_RESOLVED_SOURCE_VERSION", "main"),
            ("CODEBUILD_BUILD_ID", "GlideRdmaEfa:bad"),
            ("RDMA_SUBNETS", ""),
            ("RDMA_VPC_ID", ""),
            ("RDMA_TEMPLATE_URL", ""),
            ("RDMA_INSTANCE_PROFILE_ARN", ""),
            ("KEEP_HOSTS", "yes"),
        ):
            with self.subTest(key=key):
                with self.assertRaises(ValueError):
                    module.orchestrate(dict(self.env, **{key: value}), self.record)
                self.assertEqual(self.calls, [])

    def test_work_deadline_does_not_consume_cleanup_budget(self):
        times = iter([0, 4000])

        def fail(command, env, timeout):
            self.record(command, env, timeout)
            if timeout <= 0:
                raise TimeoutError()

        with self.assertRaises(TimeoutError):
            module.orchestrate(self.env, fail, lambda: next(times))
        self.assertEqual(self.calls[-1][2], 900)

    def test_subprocess_failure_propagates(self):
        with self.assertRaises(subprocess.CalledProcessError):
            module.run(["/bin/sh", "-c", "exit 7"], {"RDMA_STACK": "test"}, 5)

    def test_timeout_stops_descendants(self):
        # If the child survives, it writes the marker after the parent is killed.
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / "survived"
            with self.assertRaises(subprocess.TimeoutExpired):
                module.run(
                    ["/bin/sh", "-c", '(sleep 0.5; touch "$MARKER") & wait'],
                    dict(os.environ, RDMA_STACK="test", MARKER=str(marker)),
                    0.1,
                )
            time.sleep(0.7)
            self.assertFalse(marker.exists())


if __name__ == "__main__":
    unittest.main()
