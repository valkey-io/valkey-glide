# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Check the external template interface without invoking AWS."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "bin/rdma-pair.sh"
PROFILE = "arn:aws:iam::123456789012:instance-profile/hosts"


class PairTemplateTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        aws = self.root / "aws"
        aws.write_text(
            f"#!{sys.executable}\n"
            "import json, os, sys\n"
            "with open(os.environ['CALLS'], 'a') as f: f.write(json.dumps(sys.argv[1:]) + '\\n')\n"
            "if sys.argv[2] == 'describe-stacks':\n"
            "    print(os.environ.get('DESCRIBE_ERROR', 'An error occurred (ValidationError): Stack with id rdma-ci-test does not exist'), file=sys.stderr)\n"
            "    sys.exit(255)\n"
            "if sys.argv[2] == 'describe-change-set': print('CREATE_COMPLETE')\n"
        )
        aws.chmod(0o755)
        self.env = dict(
            os.environ,
            PATH=f"{self.root}:{os.environ['PATH']}",
            CALLS=str(self.root / "calls"),
            RDMA_STACK="rdma-ci-test",
        )
        self.env.pop("RDMA_TEMPLATE_URL", None)
        self.env["RDMA_INSTANCE_PROFILE_ARN"] = PROFILE

    def execute(self, action):
        return subprocess.run(
            [
                "bash",
                str(SCRIPT),
                action,
                "VpcId=vpc-test",
                "SubnetId=subnet-test",
                "Owner=test",
            ],
            env=self.env,
            capture_output=True,
            text=True,
            check=False,
            timeout=5,
        )

    def test_plan_uses_supplied_template_url(self):
        url = "https://templates.s3.us-east-1.amazonaws.com/efa/commit/rdma-pair.yaml"
        self.env["RDMA_TEMPLATE_URL"] = url
        result = self.execute("plan")
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = [
            json.loads(line) for line in (self.root / "calls").read_text().splitlines()
        ]
        create = next(call for call in calls if call[1] == "create-change-set")
        self.assertEqual(create[create.index("--template-url") + 1], url)
        self.assertNotIn("--template-body", create)

    def test_stack_is_created_without_iam_capabilities(self):
        self.env["RDMA_TEMPLATE_URL"] = (
            "https://templates.s3.us-east-1.amazonaws.com/k.yaml"
        )
        result = self.execute("plan")
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = [
            json.loads(line) for line in (self.root / "calls").read_text().splitlines()
        ]
        create = next(call for call in calls if call[1] == "create-change-set")
        self.assertNotIn("--capabilities", create)
        self.assertIn(
            f"ParameterKey=InstanceProfileArn,ParameterValue={PROFILE}", create
        )

    def test_plan_without_instance_profile_creates_no_change_set(self):
        self.env["RDMA_TEMPLATE_URL"] = (
            "https://templates.s3.us-east-1.amazonaws.com/k.yaml"
        )
        self.env.pop("RDMA_INSTANCE_PROFILE_ARN")
        result = self.execute("plan")
        self.assertEqual(result.returncode, 2)
        self.assertIn("RDMA_INSTANCE_PROFILE_ARN", result.stderr)
        calls = [
            json.loads(line) for line in (self.root / "calls").read_text().splitlines()
        ]
        self.assertFalse([call for call in calls if call[1] != "describe-stacks"])

    def test_plan_without_template_fails_before_aws(self):
        result = self.execute("plan")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("RDMA_TEMPLATE_URL", result.stderr)
        self.assertFalse((self.root / "calls").exists())

    def test_cleanup_status_error_is_not_success(self):
        for error in ("AccessDenied", "Throttling", "Could not connect to endpoint"):
            with self.subTest(error=error):
                self.env["DESCRIBE_ERROR"] = error
                result = self.execute("down")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(error, result.stderr)
                self.assertNotIn("stack: NONE", result.stdout)

    def test_cleanup_does_not_require_template_url(self):
        result = self.execute("down")
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = [
            json.loads(line) for line in (self.root / "calls").read_text().splitlines()
        ]
        self.assertTrue(any(call[1] == "delete-stack" for call in calls))


if __name__ == "__main__":
    unittest.main()
