#!/usr/bin/env python3
"""
Remote Cluster Manager - Executes cluster_manager.py on remote Linux instance via SSH
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
from typing import List, Optional


class RemoteClusterManager:
    def __init__(
        self,
        host: str,
        user: str = "ubuntu",
        key_path: Optional[str] = None,
        key_content: Optional[str] = None,
    ):
        self.host = host
        self.user = user
        self.key_path = key_path
        self.key_content = key_content
        self.temp_key_file = None
        self.remote_repo_path = "/home/ubuntu/valkey-glide"

        # Handle SSH key from environment or content
        self._setup_ssh_key()

    def _setup_ssh_key(self):
        """Setup SSH key from various sources"""
        if self.key_content:
            # Create temporary key file from content (for GitHub secrets)
            self.temp_key_file = tempfile.NamedTemporaryFile(
                mode="w", delete=False, suffix=".pem"
            )
            self.temp_key_file.write(self.key_content)
            self.temp_key_file.close()
            os.chmod(self.temp_key_file.name, 0o600)
            self.key_path = self.temp_key_file.name

        elif not self.key_path:
            # Try common key locations
            possible_keys = [
                os.environ.get("SSH_PRIVATE_KEY_PATH"),
                os.path.expanduser("~/.ssh/valkey_runner_key"),
                os.path.expanduser("~/.ssh/id_rsa"),
                os.path.expanduser("~/.ssh/id_ed25519"),
            ]

            for key_file in possible_keys:
                if key_file and os.path.exists(key_file):
                    self.key_path = key_file
                    break

        if not self.key_path:
            raise Exception(
                "No SSH key found. Set SSH_PRIVATE_KEY_PATH or provide key content"
            )

    def __del__(self):
        """Cleanup temporary key file"""
        if self.temp_key_file and os.path.exists(self.temp_key_file.name):
            os.unlink(self.temp_key_file.name)

    def _build_ssh_command(self, remote_command: str) -> List[str]:
        """Build SSH command with proper authentication"""
        ssh_cmd = [
            "ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "-o",
            "UserKnownHostsFile=/dev/null",
            "-o",
            "LogLevel=ERROR",  # Reduce noise
        ]

        if self.key_path:
            ssh_cmd.extend(["-i", self.key_path])

        ssh_cmd.extend([f"{self.user}@{self.host}", remote_command])
        return ssh_cmd

    def test_connection(self) -> bool:
        """Test SSH connection to remote host"""
        try:
            returncode, stdout, stderr = self._execute_remote_command(
                "echo 'SSH connection test'", timeout=10
            )
            return returncode == 0 and "SSH connection test" in stdout
        except Exception as e:
            print(f"SSH connection test failed: {e}")
            return False

    def _execute_remote_command(
        self, command: str, timeout: int = 300
    ) -> tuple[int, str, str]:
        """Execute command on remote host via SSH"""
        ssh_cmd = self._build_ssh_command(command)

        try:
            result = subprocess.run(
                ssh_cmd, capture_output=True, text=True, timeout=timeout
            )
            return result.returncode, result.stdout, result.stderr
        except subprocess.TimeoutExpired:
            return 1, "", f"Command timed out after {timeout} seconds"

    def setup_remote_environment(self) -> bool:
        """Ensure remote environment is ready"""
        print(f"Setting up remote environment on {self.host}...")

        # Test connection first
        if not self.test_connection():
            print("[FAIL] SSH connection failed")
            return False

        # Check if repo exists, clone if not
        check_repo = f"test -d {self.remote_repo_path}"
        returncode, _, _ = self._execute_remote_command(check_repo)

        if returncode != 0:
            print("Cloning valkey-glide repository...")
            clone_cmd = f"git clone https://github.com/valkey-io/valkey-glide.git {self.remote_repo_path}"
            returncode, stdout, stderr = self._execute_remote_command(
                clone_cmd, timeout=120
            )
            if returncode != 0:
                print(f"Failed to clone repository: {stderr}")
                return False

        # Update repository
        print("Updating repository...")
        update_cmd = f"cd {self.remote_repo_path} && git pull origin main"
        returncode, stdout, stderr = self._execute_remote_command(update_cmd)
        if returncode != 0:
            print(f"Warning: Failed to update repository: {stderr}")

        # Install dependencies
        print("Installing Python dependencies...")
        install_cmd = f"cd {self.remote_repo_path}/utils && pip3 install -r requirements.txt || true"
        self._execute_remote_command(install_cmd)

        return True

    def start_cluster(
        self,
        cluster_mode: bool = True,
        shard_count: int = 3,
        replica_count: int = 1,
        tls: bool = False,
        load_module: Optional[List[str]] = None,
    ) -> Optional[List[str]]:
        """Start cluster on remote host and return connection endpoints"""

        if not self.setup_remote_environment():
            return None

        print(
            f"Starting cluster on {self.host} (shards={shard_count}, replicas={replica_count})..."
        )

        # Build cluster_manager.py command
        cmd_parts = [
            f"cd {self.remote_repo_path}/utils",
            "&&",
            "python3 cluster_manager.py start",
        ]

        if cluster_mode:
            cmd_parts.append("--cluster-mode")
        if tls:
            cmd_parts.append("--tls")

        cmd_parts.extend(["-n", str(shard_count), "-r", str(replica_count)])
        cmd_parts.extend(
            ["--host", "0.0.0.0"]
        )  # Bind to all interfaces for external access

        if load_module:
            for module in load_module:
                cmd_parts.extend(["--load-module", module])

        remote_command = " ".join(cmd_parts)

        # Execute cluster start
        returncode, stdout, stderr = self._execute_remote_command(
            remote_command, timeout=180
        )

        if returncode != 0:
            print(f"Failed to start cluster: {stderr}")
            return None

        # Parse cluster endpoints from output
        try:
            # Look for JSON output in stdout
            lines = stdout.strip().split("\n")
            json_line = None
            for line in lines:
                if line.strip().startswith("[") and line.strip().endswith("]"):
                    json_line = line.strip()
                    break

            if json_line:
                endpoints_data = json.loads(json_line)
                # Convert localhost to remote host IP
                endpoints = []
                for endpoint in endpoints_data:
                    if (
                        isinstance(endpoint, dict)
                        and "host" in endpoint
                        and "port" in endpoint
                    ):
                        endpoints.append(f"{self.host}:{endpoint['port']}")
                    elif isinstance(endpoint, str):
                        # Handle string format like "127.0.0.1:6379"
                        _, port = endpoint.split(":")
                        endpoints.append(f"{self.host}:{port}")

                print(f"Cluster started successfully. Endpoints: {endpoints}")
                return endpoints
            else:
                print("Could not parse cluster endpoints from output")
                print(f"stdout: {stdout}")
                return None

        except json.JSONDecodeError as e:
            print(f"Failed to parse cluster output: {e}")
            print(f"stdout: {stdout}")
            return None

    def stop_cluster(self) -> bool:
        """Stop cluster on remote host"""
        print(f"Stopping cluster on {self.host}...")

        stop_cmd = (
            f"cd {self.remote_repo_path}/utils && python3 cluster_manager.py stop"
        )
        returncode, stdout, stderr = self._execute_remote_command(stop_cmd)

        if returncode != 0:
            print(f"Failed to stop cluster: {stderr}")
            return False

        print("Cluster stopped successfully")
        return True

    def get_cluster_status(self) -> Optional[dict]:
        """Get cluster status from remote host"""
        status_cmd = f"cd {self.remote_repo_path}/utils && python3 cluster_manager.py status || echo 'No cluster running'"
        returncode, stdout, stderr = self._execute_remote_command(status_cmd)

        # Return basic status info
        return {
            "host": self.host,
            "status": "running" if returncode == 0 else "stopped",
            "output": stdout.strip(),
        }


def main():
    parser = argparse.ArgumentParser(description="Remote Cluster Manager")
    parser.add_argument("--host", help="Remote Linux host IP/hostname")
    parser.add_argument("--user", default="ubuntu", help="SSH user (default: ubuntu)")
    parser.add_argument("--key-path", help="SSH private key path")

    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Start command
    start_parser = subparsers.add_parser("start", help="Start remote cluster")
    start_parser.add_argument(
        "--cluster-mode", action="store_true", help="Enable cluster mode"
    )
    start_parser.add_argument(
        "-n", "--shard-count", type=int, default=3, help="Number of shards"
    )
    start_parser.add_argument(
        "-r", "--replica-count", type=int, default=1, help="Number of replicas"
    )
    start_parser.add_argument("--tls", action="store_true", help="Enable TLS")
    start_parser.add_argument("--load-module", action="append", help="Load module")

    # Stop command
    stop_parser = subparsers.add_parser("stop", help="Stop remote cluster")

    # Status command
    status_parser = subparsers.add_parser("status", help="Get cluster status")

    # Test command
    test_parser = subparsers.add_parser("test", help="Test SSH connection")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 1

    # Get credentials from environment or arguments
    host = args.host or os.environ.get("VALKEY_REMOTE_HOST")
    if not host:
        print(
            "Error: Remote host must be specified via --host or VALKEY_REMOTE_HOST environment variable"
        )
        return 1

    # Get SSH key from multiple sources
    key_path = args.key_path
    key_content = os.environ.get("SSH_PRIVATE_KEY_CONTENT")  # For GitHub secrets

    try:
        manager = RemoteClusterManager(host, args.user, key_path, key_content)

        if args.command == "test":
            if manager.test_connection():
                print("[OK] SSH connection successful")
                return 0
            else:
                print("[FAIL] SSH connection failed")
                return 1

        elif args.command == "start":
            endpoints = manager.start_cluster(
                cluster_mode=args.cluster_mode,
                shard_count=args.shard_count,
                replica_count=args.replica_count,
                tls=args.tls,
                load_module=args.load_module,
            )
            if endpoints:
                # Output endpoints in format expected by Gradle
                print("CLUSTER_ENDPOINTS=" + ",".join(endpoints))
                return 0
            else:
                return 1

        elif args.command == "stop":
            success = manager.stop_cluster()
            return 0 if success else 1

        elif args.command == "status":
            status = manager.get_cluster_status()
            if status:
                print(json.dumps(status, indent=2))
                return 0
            else:
                return 1

    except Exception as e:
        print(f"Error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
