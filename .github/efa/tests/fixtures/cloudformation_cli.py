# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""Stateful CloudFormation CLI fake for lifecycle regression tests. No AWS calls."""

import json
import os
import sys
from pathlib import Path

state_path = Path(os.environ["FAKE_STATE"])
state = json.loads(state_path.read_text())
args = sys.argv[1:]
with Path(os.environ["CALLS"]).open("a") as calls:
    calls.write(json.dumps(args) + "\n")


def option(name):
    return args[args.index(name) + 1]


def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)


if args[0] != "cloudformation":
    fail(f"Unexpected service: {args}")
operation = args[1]
if operation == "describe-stacks":
    if state["status"] == "NONE":
        fail("An error occurred (ValidationError): Stack does not exist")
    query = option("--query")
    if query == "Stacks[0].StackStatus":
        print(state["status"])
    elif query == "Stacks[0].Parameters[].ParameterKey":
        if state.get("parameters_error"):
            fail(state["parameters_error"])
        print("\t".join(state["parameters"]))
    elif query == "Stacks[0].Outputs":
        print("[]")
    else:
        fail(f"Unexpected stack query: {query}")
elif operation == "describe-change-set":
    plan = state.get("plan")
    if plan is None:
        fail("Change set does not exist")
    query = option("--query")
    if query == "ExecutionStatus":
        print(plan["execution"])
    elif query == "[Status,StatusReason]":
        print(plan["status"])
    elif query.startswith("Changes[]."):
        print("[]")
    else:
        fail(f"Unexpected change-set query: {query}")
elif operation == "delete-change-set":
    state["plan"] = None
elif operation == "create-change-set":
    if state.get("create_error"):
        fail(state["create_error"])
    # Model CloudFormation: omitted keys use template defaults, not stack values.
    effective = dict(state["defaults"])
    for parameter in args[args.index("--parameters") + 1 :]:
        fields = dict(field.split("=", 1) for field in parameter.split(","))
        key = fields["ParameterKey"]
        effective[key] = (
            state["parameters"][key]
            if fields.get("UsePreviousValue") == "true"
            else fields["ParameterValue"]
        )
    failed = state.get("plan_fails", False)
    state["plan"] = {
        "effective": effective,
        "status": "FAILED" if failed else "CREATE_COMPLETE",
        "execution": "UNAVAILABLE" if failed else "AVAILABLE",
    }
    if option("--change-set-type") == "CREATE":
        state["status"] = "REVIEW_IN_PROGRESS"
elif operation == "wait":
    if args[2] == "change-set-create-complete" and state["plan"]["status"] == "FAILED":
        fail("Change set failed validation")
elif operation == "execute-change-set":
    plan = state["plan"]
    if plan is None or plan["execution"] != "AVAILABLE":
        fail("No executable plan")
    state["executed"].append(plan["effective"])
    state["parameters"] = plan["effective"]
    plan["execution"] = "EXECUTE_COMPLETE"
    if state.get("capacity_failures", 0):
        state["capacity_failures"] -= 1
        state["status"] = "CREATE_FAILED"
    else:
        state["status"] = "CREATE_COMPLETE"
elif operation == "describe-stack-events":
    query = option("--query")
    print("1" if query.startswith("length(") else "")
elif operation == "delete-stack":
    state["status"] = "NONE"
    state["plan"] = None
else:
    fail(f"Unexpected operation: {args}")
state_path.write_text(json.dumps(state))
