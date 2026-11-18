#!/usr/bin/env python3
# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""CodeBuild entry point: provision an EFA pair, build/test on EC2, tear down.

Only the standard library is needed. AWS operations use the CodeBuild service
role through the existing AWS CLI scripts. GitHub cancellation does not stop
this build. A hard CodeBuild stop may skip cleanup; host lifetime limits remain
the backstop, with manual deletion of the remaining stack resources.
"""

import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STEPS = (
    ("server", "00-check-efa.sh"),
    ("client", "00-check-efa.sh"),
    ("server", "10-pingpong-listen.sh"),
    ("client", "11-pingpong-connect.sh"),
    ("server", "12-pingpong-result.sh"),
    ("server", "20-server-build.sh"),
    ("server", "21-server-start.sh"),
    ("client", "30-client-setup.sh"),
    ("client", "31-client-rdma-tests.sh"),
)


def configuration(environ):
    env = dict(environ)
    sha = env.get("CODEBUILD_RESOLVED_SOURCE_VERSION", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("CODEBUILD_RESOLVED_SOURCE_VERSION must be a full commit SHA")
    build_id = env.get("CODEBUILD_BUILD_ID", "")
    # A build UUID, rather than the GitHub run ID, isolates retries and manual builds.
    match = re.fullmatch(r"GlideRdmaEfa:([0-9a-f-]{36})", build_id)
    if not match:
        raise ValueError("Expected CODEBUILD_BUILD_ID for GlideRdmaEfa")
    for key in ("RDMA_VPC_ID", "RDMA_SUBNETS", "RDMA_TEMPLATE_URL"):
        if not env.get(key, "").strip():
            raise ValueError(f"{key} is required")
    keep = env.get("KEEP_HOSTS", "false")
    if keep not in ("true", "false"):
        raise ValueError("KEEP_HOSTS must be true or false")
    env.update(RDMA_STACK=f"rdma-ci-{match[1]}", RDMA_OWNER="codebuild")
    return env, sha, keep == "true"


def run(command, env, timeout):
    if timeout <= 0:
        raise TimeoutError("EFA orchestration exceeded its 65 minute work budget")
    print(f"[{env['RDMA_STACK']}] {' '.join(command)}", flush=True)
    # Kill the entire local process group on timeout/signal: shell polling loops
    # must not keep issuing AWS operations while teardown starts.
    process = subprocess.Popen(command, cwd=ROOT, env=env, start_new_session=True)
    try:
        code = process.wait(timeout=timeout)
        if code:
            raise subprocess.CalledProcessError(code, command)
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()


def orchestrate(environ, runner=run, clock=time.monotonic):
    env, sha, keep = configuration(environ)
    deadline = clock() + 65 * 60
    failed = True
    try:
        runner(["bin/rdma-pair.sh", "apply"], env, deadline - clock())
        for host, script in STEPS:
            command = ["bin/run-on.sh", host, f"host/{script}"]
            if script == "30-client-setup.sh":
                command.append(f"GLIDE_SOURCE={sha}")
            runner(command, env, deadline - clock())
        failed = False
    finally:
        if failed and keep:
            print(
                f"Retaining failed stack {env['RDMA_STACK']} for diagnosis", flush=True
            )
        else:
            # Runs even after a partial provisioning failure. Its own timeout
            # reserves room for cleanup within CodeBuild's 90 minute limit.
            runner(["bin/rdma-pair.sh", "down"], env, 15 * 60)


def interrupted(signum, _frame):
    raise KeyboardInterrupt(f"Received signal {signum}")


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    try:
        orchestrate(os.environ)
    except (
        OSError,
        ValueError,
        subprocess.SubprocessError,
        KeyboardInterrupt,
    ) as error:
        print(f"EFA build failed: {error}", file=sys.stderr, flush=True)
        sys.exit(1)
