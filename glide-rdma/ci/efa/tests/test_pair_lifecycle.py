# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Exercise plan/apply across CLI invocations with a stateful AWS fake."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRIPT = HERE.parent / "bin/rdma-pair.sh"


class PairLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.state_path = self.root / "state.json"
        self.calls_path = self.root / "calls.jsonl"
        self.previous = {
            "Owner": "previous-owner",
            "VpcId": "vpc-existing",
            "SubnetId": "subnet-existing",
            "InstanceProfileArn": "arn:aws:iam::123456789012:instance-profile/hosts",
            "InstanceType": "c8gn.48xlarge",
            "RootVolumeGiB": "500",
            "MaxHostLifetimeMinutes": "180",
            "FutureParameter": "custom-value",
        }
        self.state_path.write_text(
            json.dumps(
                {
                    "status": "UPDATE_COMPLETE",
                    "parameters": self.previous,
                    "defaults": {
                        "InstanceType": "c8gn.16xlarge",
                        "RootVolumeGiB": "300",
                        "NewParameter": "new-default",
                    },
                    "plan": None,
                    "executed": [],
                }
            )
        )
        aws = self.root / "aws"
        aws.write_text(
            f"#!{sys.executable}\n"
            + (HERE / "fixtures/cloudformation_cli.py").read_text()
        )
        aws.chmod(0o755)
        self.env = {
            key: value
            for key, value in os.environ.items()
            if not key.startswith("RDMA_")
        }
        self.env.update(
            PATH=f"{self.root}:{os.environ['PATH']}",
            FAKE_STATE=str(self.state_path),
            CALLS=str(self.calls_path),
            RDMA_STACK="rdma-ci-test",
            RDMA_TEMPLATE_URL="https://templates.s3.us-east-1.amazonaws.com/pair.yaml",
            RDMA_INSTANCE_PROFILE_ARN=self.previous["InstanceProfileArn"],
            RDMA_VPC_ID="vpc-environment",
            RDMA_SUBNETS="subnet-env-a,subnet-env-b",
            RDMA_OWNER="environment-owner",
        )

    def state(self):
        return json.loads(self.state_path.read_text())

    def configure(self, **changes):
        self.state_path.write_text(json.dumps(dict(self.state(), **changes)))

    def calls(self, operation):
        if not self.calls_path.exists():
            return []
        return [
            call
            for line in self.calls_path.read_text().splitlines()
            if (call := json.loads(line))[1] == operation
        ]

    def execute(self, *args, success=True):
        result = subprocess.run(
            ["bash", str(SCRIPT), *args],
            env=self.env,
            capture_output=True,
            text=True,
            check=False,
            timeout=10,
        )
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def test_update_preserves_all_omitted_parameters_and_uses_new_defaults(self):
        self.execute("plan", "SubnetId=subnet-new")
        effective = self.state()["plan"]["effective"]
        self.assertEqual(
            effective,
            dict(self.previous, SubnetId="subnet-new", NewParameter="new-default"),
        )
        create = self.calls("create-change-set")[0]
        params = create[create.index("--parameters") + 1 :]
        keys = [parameter.split(",")[0] for parameter in params]
        self.assertEqual(len(keys), len(set(keys)))
        self.assertIn("ParameterKey=InstanceType,UsePreviousValue=true", params)
        self.assertNotIn("ParameterKey=SubnetId,UsePreviousValue=true", params)

    def test_apply_overrides_rebuild_available_plan(self):
        self.execute("plan", "InstanceType=c8gn.16xlarge")
        self.execute("apply", "InstanceType=c8gn.48xlarge")
        self.assertEqual(len(self.calls("create-change-set")), 2)
        self.assertEqual(self.state()["executed"][0]["InstanceType"], "c8gn.48xlarge")

    def test_apply_without_overrides_uses_existing_plan_unchanged(self):
        self.execute("plan", "InstanceType=c8gn.16xlarge")
        planned = self.state()["plan"]["effective"]
        self.env.pop("RDMA_TEMPLATE_URL")
        self.execute("apply")
        self.assertEqual(len(self.calls("create-change-set")), 1)
        self.assertEqual(self.state()["executed"], [planned])

    def test_apply_new_stack_plan_ignores_environment_subnets(self):
        self.configure(status="NONE", parameters={})
        self.execute("plan", "SubnetId=subnet-reviewed")
        self.assertEqual(self.state()["status"], "REVIEW_IN_PROGRESS")
        self.execute("apply")
        self.assertEqual(len(self.calls("create-change-set")), 1)
        self.assertEqual(self.state()["executed"][0]["SubnetId"], "subnet-reviewed")

    def test_apply_without_plan_creates_one(self):
        self.execute("apply")
        self.assertEqual(len(self.calls("create-change-set")), 1)
        self.assertEqual(
            self.state()["executed"][0]["InstanceType"], self.previous["InstanceType"]
        )

    def test_metadata_error_preserves_existing_plan_and_does_not_execute(self):
        self.execute("plan", "InstanceType=c8gn.16xlarge")
        planned = self.state()["plan"]
        self.configure(parameters_error="AccessDenied")
        result = self.execute("apply", "InstanceType=c8gn.48xlarge", success=False)
        self.assertIn("AccessDenied", result.stderr)
        self.assertEqual(self.state()["plan"], planned)
        self.assertEqual(self.calls("execute-change-set"), [])

    def test_rebuild_api_failure_never_executes(self):
        self.execute("plan", "InstanceType=c8gn.16xlarge")
        self.configure(create_error="AccessDenied")
        self.execute("apply", "InstanceType=c8gn.48xlarge", success=False)
        self.assertEqual(self.calls("execute-change-set"), [])

    def test_unavailable_rebuilt_plan_never_executes(self):
        self.execute("plan", "InstanceType=c8gn.16xlarge")
        self.configure(plan_fails=True)
        self.execute("apply", "InstanceType=c8gn.48xlarge", success=False)
        self.assertEqual(self.calls("execute-change-set"), [])

    def test_create_uses_defaults_without_previous_values(self):
        self.configure(status="NONE", parameters={})
        self.execute("apply", "SubnetId=subnet-explicit")
        effective = self.state()["executed"][0]
        self.assertEqual(effective["InstanceType"], "c8gn.16xlarge")
        self.assertEqual(effective["SubnetId"], "subnet-explicit")
        self.assertFalse(
            any("UsePreviousValue" in arg for arg in self.calls("create-change-set")[0])
        )

    def test_capacity_retry_rebuilds_for_next_subnet(self):
        self.configure(status="NONE", parameters={}, capacity_failures=1)
        self.execute("apply", "InstanceType=c8gn.48xlarge")
        self.assertEqual(
            [attempt["SubnetId"] for attempt in self.state()["executed"]],
            ["subnet-env-a", "subnet-env-b"],
        )
        self.assertEqual(len(self.calls("create-change-set")), 2)
        self.assertEqual(len(self.calls("delete-stack")), 1)


if __name__ == "__main__":
    unittest.main()
