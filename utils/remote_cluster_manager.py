#!/usr/bin/env python3
"""
Remote Cluster Manager - Executes cluster_manager.py on remote Linux instance via SSH
"""

import argparse
import json
import logging
import os
import subprocess
import sys
import tempfile
from typing import List, Optional

LOG_LEVELS = {
    "critical": logging.CRITICAL,
    "error": logging.ERROR,
    "warn": logging.WARNING,
    "warning": logging.WARNING,
    "info": logging.INFO,
    "debug": logging.DEBUG,
}


def init_logger(logfile: str):
    print(f"LOG_FILE={logfile}")
    root_logger = logging.getLogger()
    handler = logging.FileHandler(logfile, "w", "utf-8")
    root_logger.addHandler(handler)
    root_logger.addHandler(logging.StreamHandler(sys.stdout))
    root_logger.addHandler(logging.StreamHandler(sys.stderr))


class RemoteClusterManager:
    def __init__(
        self,
        host: str,
        user: str = "ubuntu",
        key_path: Optional[str] = None,
        key_content: Optional[str] = None,
        engine_version: str = "8.0",
    ):
        # Validate engine version
        supported_versions = ["7.2", "8.0", "8.1", "9.0"]
        if engine_version not in supported_versions:
            raise ValueError(
                f"Unsupported engine version: {engine_version}. Supported: {supported_versions}"
            )

        self.host = host
        self.user = user
        self.key_path: Optional[str] = key_path
        self.key_content = key_content
        self.temp_key_file = None
        self.remote_repo_path = "/home/ubuntu/valkey-glide"
        self.engine_version = engine_version
        self.engines_base_path = "/opt/engines"
        self.engine_path = f"{self.engines_base_path}/valkey-{engine_version}"

        # Handle SSH key from environment or content
        self._setup_ssh_key()
        # After _setup_ssh_key, key_path is guaranteed to be set
        assert self.key_path is not None

    def _setup_ssh_key(self):
        """Setup SSH key from various sources"""
        if self.key_content:
            # Create temporary key file from content (for GitHub secrets)
            self.temp_key_file = tempfile.NamedTemporaryFile(
                mode="w", delete=False, suffix=".pem"
            )

            logging.info(f"Writing Key file from content: {self.temp_key_file}")
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
            logging.info(f"Connecting using key: {self.key_path}")
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
            logging.error(f"SSH connection test failed: {e}")
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
        logging.info(f"Setting up remote environment on {self.host}...")

        # Test connection first
        if not self.test_connection():
            logging.error("[FAIL] SSH connection failed")
            return False

        # Setup engines directory and install engine if needed
        if not self._setup_engine():
            return False

        # Check if repo exists, clone if not
        check_repo = f"test -d {self.remote_repo_path}"
        returncode, _, _ = self._execute_remote_command(check_repo)

        if returncode != 0:
            logging.info("Cloning valkey-glide repository...")
            clone_cmd = f"git clone https://github.com/valkey-io/valkey-glide.git {self.remote_repo_path}"
            returncode, stdout, stderr = self._execute_remote_command(
                clone_cmd, timeout=120
            )
            if returncode != 0:
                logging.error(f"Failed to clone repository: {stderr}")
                return False

        # Update repository
        logging.info("Updating repository...")
        update_cmd = f"cd {self.remote_repo_path} && git pull origin main && git log -1 --oneline"
        returncode, stdout, stderr = self._execute_remote_command(update_cmd)
        if returncode != 0:
            logging.warning(f"Warning: Failed to update repository: {stderr}")
        else:
            logging.info(f"Repository updated. Latest commit: {stdout.strip()}")

        # Install dependencies
        logging.info("Installing Python dependencies...")
        install_cmd = f"cd {self.remote_repo_path}/utils && pip3 install -r requirements.txt || true"
        self._execute_remote_command(install_cmd)

        # Copy our local cluster_manager.py to ensure we have the latest version
        logging.info("Copying local cluster_manager.py to remote...")
        local_cluster_manager = os.path.join(
            os.path.dirname(__file__), "cluster_manager.py"
        )
        remote_cluster_manager = (
            f"{self.remote_repo_path}/utils/cluster_manager_local.py"
        )
        self._copy_file_to_remote(local_cluster_manager, remote_cluster_manager)

        return True

    def _setup_engine(self) -> bool:
        """Setup engine directory and install Valkey if needed"""
        logging.info(f"Setting up Valkey {self.engine_version}...")

        # Create engines base directory
        setup_cmd = f"""
        sudo mkdir -p {self.engines_base_path}
        sudo chown ubuntu:ubuntu {self.engines_base_path}
        sudo apt-get update -qq
        sudo apt-get install -y build-essential git pkg-config libssl-dev
        """

        returncode, stdout, stderr = self._execute_remote_command(
            setup_cmd, timeout=300
        )
        if returncode != 0:
            logging.error(f"Failed to setup base environment: {stderr}")
            return False

        # Check if engine is already installed
        check_engine = f"test -f {self.engine_path}/src/valkey-server"
        returncode, _, _ = self._execute_remote_command(check_engine)

        if returncode == 0:
            logging.info(f"Valkey {self.engine_version} already installed")
            return True

        # Install engine
        logging.info(f"Installing Valkey {self.engine_version}...")
        install_cmd = f"""
        cd {self.engines_base_path}
        if [ -d "valkey-{self.engine_version}" ]; then
            rm -rf valkey-{self.engine_version}
        fi
        git clone https://github.com/valkey-io/valkey.git valkey-{self.engine_version}
        cd valkey-{self.engine_version}
        git checkout {self.engine_version}
        make BUILD_TLS=yes -j$(nproc)
        """

        returncode, stdout, stderr = self._execute_remote_command(
            install_cmd, timeout=600
        )
        if returncode != 0:
            logging.error(f"Failed to install Valkey {self.engine_version}: {stderr}")
            return False

        logging.info(f"Successfully installed Valkey {self.engine_version}")
        return True

    def start_cluster(
        self,
        cluster_mode: bool = True,
        shard_count: int = 3,
        replica_count: int = 1,
        tls: bool = False,
        tls_cert_file: Optional[str] = None,
        tls_key_file: Optional[str] = None,
        tls_ca_cert_file: Optional[str] = None,
        load_module: Optional[List[str]] = None,
    ) -> Optional[List[str]]:
        """Start cluster on remote host and return connection endpoints"""

        if not self.setup_remote_environment():
            return None

        logging.info(
            f"Starting cluster on {self.host} (shards={shard_count}, replicas={replica_count})..."
        )

        # Handle TLS certificate files
        remote_tls_cert = None
        remote_tls_key = None
        remote_tls_ca = None

        if tls:
            if tls_cert_file or tls_key_file or tls_ca_cert_file:
                # Custom TLS files provided - copy them to remote
                if tls_cert_file:
                    remote_tls_cert = f"{self.remote_repo_path}/tls_cert.pem"
                    self._copy_file_to_remote(tls_cert_file, remote_tls_cert)
                if tls_key_file:
                    remote_tls_key = f"{self.remote_repo_path}/tls_key.pem"
                    self._copy_file_to_remote(tls_key_file, remote_tls_key)
                if tls_ca_cert_file:
                    remote_tls_ca = f"{self.remote_repo_path}/tls_ca.pem"
                    self._copy_file_to_remote(tls_ca_cert_file, remote_tls_ca)
            # If no custom files, let remote cluster_manager.py generate defaults

        # Build cluster_manager.py command with engine-specific PATH
        cmd_parts = [
            f"cd {self.remote_repo_path}/utils",
            "&&",
            f"export PATH={self.engine_path}/src:$PATH",
            "&&",
            "python3 cluster_manager_local.py start",
        ]

        if cluster_mode:
            cmd_parts.append("--cluster-mode")
        if tls:
            if remote_tls_cert or remote_tls_key or remote_tls_ca:
                # Custom TLS files provided - pass them explicitly
                if remote_tls_cert:
                    cmd_parts.extend(["--tls-cert-file", remote_tls_cert])
                if remote_tls_key:
                    cmd_parts.extend(["--tls-key-file", remote_tls_key])
                if remote_tls_ca:
                    cmd_parts.extend(["--tls-ca-cert-file", remote_tls_ca])
            # If no custom files, don't pass TLS args - cluster_manager.py will use defaults
            else:
                # No custom files - use --tls flag to trigger TLS mode with defaults
                cmd_parts.append("--tls")

            # Set paths for copying generated certs back after cluster starts
            if not (remote_tls_cert and remote_tls_key and remote_tls_ca):
                remote_tls_cert = f"{self.remote_repo_path}/utils/tls_crts/server.crt"
                remote_tls_key = f"{self.remote_repo_path}/utils/tls_crts/server.key"
                remote_tls_ca = f"{self.remote_repo_path}/utils/tls_crts/ca.crt"

        cmd_parts.extend(["-n", str(shard_count), "-r", str(replica_count)])
        if load_module:
            for module in load_module:
                cmd_parts.extend(["--load-module", module])

        remote_command = " ".join(cmd_parts)

        # Execute cluster start
        returncode, stdout, stderr = self._execute_remote_command(
            remote_command, timeout=180
        )

        if returncode != 0:
            logging.error(f"Failed to start cluster: {stderr}")
            return None

        # Parse cluster endpoints from output
        try:
            # Look for CLUSTER_NODES= output from cluster_manager.py
            endpoints = []
            for line in stdout.strip().split("\n"):
                if line.startswith("CLUSTER_NODES="):
                    nodes_str = line.split("=", 1)[1]
                    # Parse the comma-separated host:port pairs
                    for node in nodes_str.split(","):
                        node = node.strip()
                        if ":" in node:
                            # Replace localhost/127.0.0.1 with remote host IP
                            host, port = node.rsplit(":", 1)
                            if host in ["127.0.0.1", "localhost"]:
                                endpoints.append(f"{self.host}:{port}")
                            else:
                                endpoints.append(node)
                    break

            if endpoints:
                logging.info(f"Cluster started successfully. Endpoints: {endpoints}")

                # Copy TLS certificates back to local machine if using defaults
                if tls and not (tls_cert_file or tls_key_file or tls_ca_cert_file):
                    logging.info("Copying generated TLS certificates from remote...")

                    # Create local tls_crts directory
                    import os

                    local_tls_dir = os.path.join(os.path.dirname(__file__), "tls_crts")
                    os.makedirs(local_tls_dir, exist_ok=True)

                    # Copy certificates
                    self._copy_file_from_remote(
                        remote_tls_cert, os.path.join(local_tls_dir, "server.crt")
                    )
                    self._copy_file_from_remote(
                        remote_tls_key, os.path.join(local_tls_dir, "server.key")
                    )
                    self._copy_file_from_remote(
                        remote_tls_ca, os.path.join(local_tls_dir, "ca.crt")
                    )

                return endpoints
            else:
                logging.error("Could not parse cluster endpoints from output")
                logging.error(f"stdout: {stdout}")
                return None

        except json.JSONDecodeError as e:
            logging.error(f"Failed to parse cluster output: {e}")
            logging.error(f"stdout: {stdout}")
            return None

    def stop_cluster(self) -> bool:
        """Stop cluster on remote host"""
        logging.info(f"Stopping cluster on {self.host}...")

        stop_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && python3 cluster_manager.py stop"
        returncode, stdout, stderr = self._execute_remote_command(stop_cmd)

        if returncode != 0:
            logging.error(f"Failed to stop cluster: {stderr}")
            return False

        logging.info("Cluster stopped successfully")
        return True

    def get_cluster_status(self) -> Optional[dict]:
        """Get cluster status from remote host"""
        status_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && python3 cluster_manager.py status || echo 'No cluster running'"
        returncode, stdout, stderr = self._execute_remote_command(status_cmd)

        # Return basic status info
        return {
            "host": self.host,
            "engine_version": self.engine_version,
            "status": "running" if returncode == 0 else "stopped",
            "output": stdout.strip(),
        }

    def _copy_file_from_remote(self, remote_path: str, local_path: str) -> bool:
        """Copy a file from remote host to local using scp"""
        try:
            import subprocess

            assert self.key_path is not None  # Guaranteed by _setup_ssh_key
            scp_cmd = [
                "scp",
                "-i",
                self.key_path,
                "-o",
                "StrictHostKeyChecking=no",
                "-o",
                "UserKnownHostsFile=/dev/null",
                f"{self.user}@{self.host}:{remote_path}",
                local_path,
            ]

            result = subprocess.run(scp_cmd, capture_output=True, text=True, timeout=30)
            if result.returncode != 0:
                logging.error(
                    f"Failed to copy {remote_path} from remote: {result.stderr}"
                )
                return False

            logging.info(f"Successfully copied {remote_path} to {local_path}")
            return True

        except Exception as e:
            logging.error(f"Error copying file from remote: {e}")
            return False

    def _copy_file_to_remote(self, local_path: str, remote_path: str) -> bool:
        """Copy a local file to remote host using scp"""
        try:
            import subprocess

            assert self.key_path is not None  # Guaranteed by _setup_ssh_key
            scp_cmd = [
                "scp",
                "-i",
                self.key_path,
                "-o",
                "StrictHostKeyChecking=no",
                "-o",
                "UserKnownHostsFile=/dev/null",
                local_path,
                f"{self.user}@{self.host}:{remote_path}",
            ]

            result = subprocess.run(scp_cmd, capture_output=True, text=True, timeout=30)
            if result.returncode != 0:
                logging.error(f"Failed to copy {local_path} to remote: {result.stderr}")
                return False

            logging.info(f"Copied {local_path} to {remote_path}")
            return True

        except Exception as e:
            logging.error(f"Error copying file to remote: {e}")
            return False


def main():
    logfile = "./cluster_manager.log"
    init_logger(logfile)

    parser = argparse.ArgumentParser(description="Remote Cluster Manager")
    parser.add_argument("--host", help="Remote Linux host IP/hostname")
    parser.add_argument("--user", default="ubuntu", help="SSH user (default: ubuntu)")
    parser.add_argument("--key-path", help="SSH private key path")
    parser.add_argument(
        "--engine-version", default="8.0", help="Valkey engine version (default: 8.0)"
    )

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
    start_parser.add_argument(
        "--tls-cert-file", type=str, help="Path to TLS certificate file"
    )
    start_parser.add_argument("--tls-key-file", type=str, help="Path to TLS key file")
    start_parser.add_argument(
        "--tls-ca-cert-file", type=str, help="Path to TLS CA certificate file"
    )
    start_parser.add_argument("--load-module", action="append", help="Load module")

    # Other subcommands (parsers created but not used yet)
    subparsers.add_parser("stop", help="Stop remote cluster")
    subparsers.add_parser("status", help="Get cluster status")
    subparsers.add_parser("test", help="Test SSH connection")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 1

    level = logging.INFO
    logging.root.setLevel(level=level)

    # Get credentials from environment or arguments
    host = args.host or os.environ.get("VALKEY_REMOTE_HOST")
    if not host:
        logging.error(
            "Error: Remote host must be specified via --host or VALKEY_REMOTE_HOST environment variable"
        )
        return 1

    # Get SSH key from multiple sources
    key_path = args.key_path
    key_content = os.environ.get("SSH_PRIVATE_KEY_CONTENT")  # For GitHub secrets

    try:
        manager = RemoteClusterManager(
            host, args.user, key_path, key_content, args.engine_version
        )

        if args.command == "test":
            if manager.test_connection():
                logging.info("[OK] SSH connection successful")
                return 0
            else:
                logging.error("[FAIL] SSH connection failed")
                return 1

        elif args.command == "start":
            endpoints = manager.start_cluster(
                cluster_mode=args.cluster_mode,
                shard_count=args.shard_count,
                replica_count=args.replica_count,
                tls=args.tls,
                tls_cert_file=args.tls_cert_file,
                tls_key_file=args.tls_key_file,
                tls_ca_cert_file=args.tls_ca_cert_file,
                load_module=args.load_module,
            )
            if endpoints:
                # Output endpoints in format expected by Gradle (to stdout)
                print("CLUSTER_NODES=" + ",".join(endpoints))
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
        logging.error(f"Error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
