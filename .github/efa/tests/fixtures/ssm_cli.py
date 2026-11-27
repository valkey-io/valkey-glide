# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Stateful SSM CLI fake used by the run-on shell regression tests."""

import json
import os
import sys
from pathlib import Path

args = sys.argv[1:]
state_path = Path(os.environ["FAKE_STATE"])
state = json.loads(state_path.read_text())
if args[:2] == ["ec2", "describe-instances"]:
    print("i-host 10.0.0.1")
elif args[:2] == ["ssm", "send-command"]:
    state["parameters"] = json.loads(args[args.index("--parameters") + 1])
    state_path.write_text(json.dumps(state))
    print("command-test")
elif args[:2] == ["ssm", "get-command-invocation"]:
    query = args[args.index("--query") + 1]
    if query == "Status":
        assert os.environ["AWS_MAX_ATTEMPTS"] == "1"
        assert args[args.index("--cli-connect-timeout") + 1] == "10"
        assert args[args.index("--cli-read-timeout") + 1] == "30"
        index = state.get("polls", 0)
        response = state["responses"][min(index, len(state["responses"]) - 1)]
        state["polls"] = index + 1
        state_path.write_text(json.dumps(state))
        if isinstance(response, dict):
            print(response["error"], file=sys.stderr)
            sys.exit(254)
        print(response)
    else:
        state.setdefault("output_queries", []).append(query)
        state_path.write_text(json.dumps(state))
        print("host output" if query == "StandardOutputContent" else "host diagnostic")
else:
    raise AssertionError(args)
