#!/usr/bin/env python3
# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0
"""
EC2 orchestrator for Windows CI builds.
Launches Linux EC2 (Valkey server) + Windows EC2 (build+test),
polls S3 for completion, downloads report.

Usage: python3 utils/ec2_orchestrator.py

Required env vars:
  EC2_LINUX_AMI_ID, EC2_WINDOWS_AMI_ID
  EC2_SUBNET_ID, EC2_SECURITY_GROUP
  EC2_INSTANCE_PROFILE, EC2_WINDOWS_INSTANCE_PROFILE
  EC2_LINUX_INSTANCE_TYPE (default: t3.small)
  EC2_WINDOWS_INSTANCE_TYPE (default: c5.2xlarge)
  REPORT_BUCKET, AWS_REGION (default: us-east-1)
  BUILD_ID, COMMIT_SHA, NODE_VERSION
"""

import base64
import json
import logging
import os
import sys
import time
from pathlib import Path

import boto3  # type: ignore[import-not-found]

logging.basicConfig(level=logging.INFO, format="[orchestrator] %(message)s")
log = logging.getLogger(__name__)

REGION = os.environ.get("AWS_REGION", "us-east-1")
BUILD_ID = os.environ["BUILD_ID"]
COMMIT_SHA = os.environ["COMMIT_SHA"]
REPORT_BUCKET = os.environ["REPORT_BUCKET"]
NODE_VERSION = os.environ.get("NODE_VERSION", "20.18.0")


def launch_linux_ec2(ec2_client) -> tuple[str, str]:
    """Launch Linux EC2, return (instance_id, private_ip)."""
    resp = ec2_client.run_instances(
        ImageId=os.environ["EC2_LINUX_AMI_ID"],
        InstanceType=os.environ.get("EC2_LINUX_INSTANCE_TYPE", "t3.small"),
        MinCount=1,
        MaxCount=1,
        SubnetId=os.environ["EC2_SUBNET_ID"],
        SecurityGroupIds=[os.environ["EC2_SECURITY_GROUP"]],
        IamInstanceProfile={"Name": os.environ["EC2_INSTANCE_PROFILE"]},
        TagSpecifications=[
            {
                "ResourceType": "instance",
                "Tags": [{"Key": "Name", "Value": f"glide-ci-valkey-{BUILD_ID}"}],
            }
        ],
    )
    instance_id = resp["Instances"][0]["InstanceId"]
    log.info(f"Linux EC2 launched: {instance_id}")

    waiter = ec2_client.get_waiter("instance_running")
    waiter.wait(InstanceIds=[instance_id])
    resp2 = ec2_client.describe_instances(InstanceIds=[instance_id])
    private_ip = resp2["Reservations"][0]["Instances"][0]["PrivateIpAddress"]
    log.info(f"Linux EC2 running: {instance_id} ({private_ip})")
    return instance_id, private_ip


def wait_for_ssm(ssm_client, instance_id: str, timeout: int = 300) -> None:
    """Wait for SSM agent to register on an instance."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        info = ssm_client.describe_instance_information(
            Filters=[{"Key": "InstanceIds", "Values": [instance_id]}]
        )
        if info.get("InstanceInformationList"):
            log.info(f"SSM ready on {instance_id}")
            return
        time.sleep(10)
    raise TimeoutError(f"SSM agent not ready on {instance_id} within {timeout}s")


def run_ssm_command(
    ssm_client, instance_id: str, command: str, timeout: int = 300
) -> str:
    """Run a shell command on an EC2 instance via SSM, return stdout."""
    resp = ssm_client.send_command(
        InstanceIds=[instance_id],
        DocumentName="AWS-RunShellScript",
        Parameters={"commands": [command]},
        TimeoutSeconds=timeout,
    )
    cmd_id = resp["Command"]["CommandId"]
    deadline = time.time() + timeout
    while time.time() < deadline:
        inv = ssm_client.get_command_invocation(
            CommandId=cmd_id, InstanceId=instance_id
        )
        if inv["Status"] in ("Success", "Failed", "Cancelled", "TimedOut"):
            if inv["Status"] != "Success":
                raise RuntimeError(
                    f"SSM command failed ({inv['Status']}): "
                    f"{inv.get('StandardErrorContent', '')}"
                )
            return inv.get("StandardOutputContent", "")
        time.sleep(5)
    raise TimeoutError(f"SSM command timed out after {timeout}s")


def start_valkey_servers(
    ssm_client, instance_id: str, private_ip: str
) -> tuple[str, str]:
    """Start standalone + cluster Valkey on Linux EC2, return (standalone_ep, cluster_ep)."""
    # Copy cluster_manager.py
    script = Path("utils/cluster_manager.py").read_text().replace("'", "'\"'\"'")
    run_ssm_command(
        ssm_client,
        instance_id,
        f"cat > /tmp/cluster_manager.py << 'GLIDE_EOF'\n{script}\nGLIDE_EOF",
        timeout=30,
    )
    log.info("cluster_manager.py copied to Linux EC2")

    # Start standalone
    standalone_out = run_ssm_command(
        ssm_client,
        instance_id,
        f"python3 /tmp/cluster_manager.py start -H {private_ip} 2>&1",
        timeout=120,
    )
    standalone_ep = _parse_endpoint(standalone_out, private_ip)
    log.info(f"Standalone endpoint: {standalone_ep}")

    # Start cluster
    cluster_out = run_ssm_command(
        ssm_client,
        instance_id,
        f"python3 /tmp/cluster_manager.py start --cluster-mode -H {private_ip} 2>&1",
        timeout=180,
    )
    cluster_ep = _parse_endpoint(cluster_out, private_ip)
    log.info(f"Cluster endpoint: {cluster_ep}")

    return standalone_ep, cluster_ep


def _parse_endpoint(output: str, host: str) -> str:
    """Parse host:port from cluster_manager.py output."""
    for token in output.split():
        if token.startswith(host + ":"):
            return token
    # fallback: first token containing ':' that looks like host:port
    for token in output.split():
        if ":" in token and not token.startswith("CLUSTER"):
            return token
    raise ValueError(f"Could not parse endpoint from output:\n{output}")


def build_windows_userdata(
    standalone_ep: str, cluster_ep: str
) -> bytes:
    """Build the PowerShell user-data script for the Windows EC2."""
    lines = [
        "<powershell>",
        "$ErrorActionPreference = 'Stop'",
        f"$buildId = '{BUILD_ID}'",
        f"$commitSha = '{COMMIT_SHA}'",
        f"$reportBucket = '{REPORT_BUCKET}'",
        f"$region = '{REGION}'",
        f"$standaloneEndpoint = '{standalone_ep}'",
        f"$clusterEndpoint = '{cluster_ep}'",
        "$exitCode = 1",
        "try {",
        "    $env:CARGO_HOME = 'C:\\cargo'",
        "    $env:RUSTUP_HOME = 'C:\\rustup'",
        "    $env:RUSTUP_TOOLCHAIN = 'stable'",
        "    $env:PROTOC = 'C:\\Windows\\System32\\protoc.exe'",
        '    $env:PATH = "C:\\cargo\\bin;C:\\Windows\\System32;C:\\Program Files\\nodejs;$env:PATH"',
        "    Write-Host '=== Updating Rust ==='",
        "    & 'C:\\cargo\\bin\\rustup.exe' update stable 2>&1",
        "    Write-Host \"Cargo: $(& 'C:\\cargo\\bin\\cargo.exe' --version)\"",
        "    $vsWhere = 'C:\\Program Files (x86)\\Microsoft Visual Studio\\Installer\\vswhere.exe'",
        "    if (Test-Path $vsWhere) {",
        "        $vsPath = & $vsWhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null",
        "        if ($vsPath) {",
        "            $vcVer = (Get-Content \"$vsPath\\VC\\Auxiliary\\Build\\Microsoft.VCToolsVersion.default.txt\").Trim()",
        '            $env:PATH = "$vsPath\\VC\\Tools\\MSVC\\$vcVer\\bin\\Hostx64\\x64;$env:PATH"',
        "        }",
        "    }",
        "    Write-Host '=== Cloning repo ==='",
        "    git clone https://github.com/valkey-io/valkey-glide.git C:\\build\\valkey-glide --depth=1",
        "    Set-Location C:\\build\\valkey-glide",
        "    git fetch origin $commitSha",
        "    git checkout $commitSha",
        "    Write-Host '=== Building ==='",
        "    Set-Location utils; npm ci; npm run build; Set-Location ..",
        "    Set-Location node; npm ci; npm run build:release",
        "    Write-Host '=== Running tests ==='",
        "    $testArgs = @('test', '--', '--runInBand', '--forceExit')",
        "    if ($standaloneEndpoint) { $testArgs += \"--standalone-endpoints=$standaloneEndpoint\" }",
        "    if ($clusterEndpoint)    { $testArgs += \"--cluster-endpoints=$clusterEndpoint\" }",
        "    $testArgs += '--testPathIgnorePatterns=ServerModules'",
        "    $testArgs += '--testPathIgnorePatterns=TlsTest'",
        "    $testArgs += '--testPathIgnorePatterns=MutualTLS'",
        "    & npm @testArgs",
        "    $exitCode = $LASTEXITCODE",
        "} catch {",
        "    Write-Host \"Build/test failed: $_\"",
        "    $exitCode = 1",
        "} finally {",
        "    try {",
        "        if (Test-Path 'C:\\build\\valkey-glide\\node\\test-report.html') {",
        "            & 'C:\\Program Files\\Amazon\\AWSCLIV2\\aws.exe' s3 cp 'C:\\build\\valkey-glide\\node\\test-report.html' \"s3://$reportBucket/$buildId/test-report.html\" --region $region",
        "        }",
        "        $status = if ($exitCode -eq 0) { 'success' } else { 'failed' }",
        "        $json = '{\"status\":\"' + $status + '\",\"exitCode\":' + $exitCode + '}'",
        "        $json | Out-File -FilePath 'C:\\status.json' -Encoding UTF8",
        "        & 'C:\\Program Files\\Amazon\\AWSCLIV2\\aws.exe' s3 cp 'C:\\status.json' \"s3://$reportBucket/$buildId/status.json\" --region $region",
        "    } catch { Write-Host \"S3 upload failed: $_\" }",
        "    $iid = (Invoke-WebRequest -Uri 'http://169.254.169.254/latest/meta-data/instance-id' -UseBasicParsing).Content",
        "    & 'C:\\Program Files\\Amazon\\AWSCLIV2\\aws.exe' ec2 terminate-instances --instance-ids $iid --region $region",
        "}",
        "</powershell>",
    ]
    return "\n".join(lines).encode("utf-8")


def launch_windows_ec2(ec2_client, userdata: bytes) -> str:
    """Launch Windows EC2 with user-data, return instance_id."""
    ud_b64 = base64.b64encode(userdata).decode()
    resp = ec2_client.run_instances(
        ImageId=os.environ["EC2_WINDOWS_AMI_ID"],
        InstanceType=os.environ.get("EC2_WINDOWS_INSTANCE_TYPE", "c5.2xlarge"),
        MinCount=1,
        MaxCount=1,
        SubnetId=os.environ["EC2_SUBNET_ID"],
        SecurityGroupIds=[os.environ["EC2_SECURITY_GROUP"]],
        IamInstanceProfile={"Name": os.environ["EC2_WINDOWS_INSTANCE_PROFILE"]},
        UserData=ud_b64,
        TagSpecifications=[
            {
                "ResourceType": "instance",
                "Tags": [
                    {"Key": "Name", "Value": f"glide-ci-windows-{BUILD_ID}"}
                ],
            }
        ],
    )
    instance_id = resp["Instances"][0]["InstanceId"]
    log.info(f"Windows EC2 launched: {instance_id}")
    return instance_id


def poll_s3_for_completion(
    s3_client, timeout: int = 5400
) -> dict:
    """Poll S3 for status.json written by Windows EC2. Returns status dict."""
    key = f"{BUILD_ID}/status.json"
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            obj = s3_client.get_object(Bucket=REPORT_BUCKET, Key=key)
            data = json.loads(obj["Body"].read())
            log.info(f"Build complete: {data}")
            return data
        except s3_client.exceptions.NoSuchKey:
            pass
        except Exception as e:
            log.warning(f"Poll error: {e}")
        time.sleep(30)
    raise TimeoutError(f"Windows build did not complete within {timeout}s")


def download_report(s3_client) -> None:
    """Download test-report.html from S3."""
    try:
        s3_client.download_file(
            REPORT_BUCKET,
            f"{BUILD_ID}/test-report.html",
            "node/test-report.html",
        )
        log.info("Test report downloaded to node/test-report.html")
    except Exception as e:
        log.warning(f"Could not download test report: {e}")


def terminate_instance(ec2_client, instance_id: str) -> None:
    """Terminate an EC2 instance."""
    try:
        ec2_client.terminate_instances(InstanceIds=[instance_id])
        log.info(f"Terminated EC2 {instance_id}")
    except Exception as e:
        log.error(f"Failed to terminate {instance_id}: {e}")


def main() -> int:
    ec2 = boto3.client("ec2", region_name=REGION)
    ssm = boto3.client("ssm", region_name=REGION)
    s3 = boto3.client("s3", region_name=REGION)

    linux_instance_id = None
    try:
        # Step 1: Linux EC2 + Valkey
        linux_instance_id, linux_private_ip = launch_linux_ec2(ec2)
        wait_for_ssm(ssm, linux_instance_id)
        standalone_ep, cluster_ep = start_valkey_servers(
            ssm, linux_instance_id, linux_private_ip
        )

        # Step 2: Windows EC2 (build + test)
        userdata = build_windows_userdata(standalone_ep, cluster_ep)
        launch_windows_ec2(ec2, userdata)

        # Step 3: Poll for completion
        status = poll_s3_for_completion(s3)
        download_report(s3)

        return 0 if status.get("status") == "success" else 1

    except Exception as e:
        log.error(f"Orchestration failed: {e}")
        return 1
    finally:
        if linux_instance_id:
            terminate_instance(ec2, linux_instance_id)


if __name__ == "__main__":
    sys.exit(main())
