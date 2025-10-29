#!/usr/bin/env python3
"""
Multi-Engine Manager - Manages multiple Valkey/Redis installations on VPC Linux instance
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
from typing import Dict, List, Optional, Tuple


class MultiEngineManager:
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
        self.base_path = "/opt/engines"
        self.repo_path = "/home/ubuntu/valkey-glide"

        # Engine configurations
        self.engines = {
            "valkey-7.2": {
                "repo": "https://github.com/valkey-io/valkey.git",
                "branch": "7.2",
                "binary_prefix": "valkey",
                "port_offset": 0,
            },
            "valkey-8.0": {
                "repo": "https://github.com/valkey-io/valkey.git",
                "branch": "8.0",
                "binary_prefix": "valkey",
                "port_offset": 100,
            },
            "valkey-8.1": {
                "repo": "https://github.com/valkey-io/valkey.git",
                "branch": "8.1",
                "binary_prefix": "valkey",
                "port_offset": 200,
            },
            "redis-6.2": {
                "repo": "https://github.com/redis/redis.git",
                "branch": "6.2",
                "binary_prefix": "redis",
                "port_offset": 300,
            },
            "redis-7.0": {
                "repo": "https://github.com/redis/redis.git",
                "branch": "7.0",
                "binary_prefix": "redis",
                "port_offset": 400,
            },
            "redis-7.2": {
                "repo": "https://github.com/redis/redis.git",
                "branch": "7.2",
                "binary_prefix": "redis",
                "port_offset": 500,
            },
        }

        self._setup_ssh_key()

    def _setup_ssh_key(self):
        """Setup SSH key from various sources"""
        if self.key_content:
            self.temp_key_file = tempfile.NamedTemporaryFile(
                mode="w", delete=False, suffix=".pem"
            )
            self.temp_key_file.write(self.key_content)
            self.temp_key_file.close()
            os.chmod(self.temp_key_file.name, 0o600)
            self.key_path = self.temp_key_file.name

        elif not self.key_path:
            possible_keys = [
                os.environ.get("SSH_PRIVATE_KEY_PATH"),
                os.path.expanduser("~/.ssh/valkey_runner_key"),
                os.path.expanduser("~/.ssh/id_rsa"),
            ]

            for key_file in possible_keys:
                if key_file and os.path.exists(key_file):
                    self.key_path = key_file
                    break

        if not self.key_path:
            raise Exception("No SSH key found")

    def __del__(self):
        if self.temp_key_file and os.path.exists(self.temp_key_file.name):
            os.unlink(self.temp_key_file.name)

    def _execute_remote_command(
        self, command: str, timeout: int = 300
    ) -> Tuple[int, str, str]:
        """Execute command on remote host via SSH"""
        ssh_cmd = [
            "ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "-o",
            "UserKnownHostsFile=/dev/null",
            "-o",
            "LogLevel=ERROR",
        ]

        if self.key_path:
            ssh_cmd.extend(["-i", self.key_path])

        ssh_cmd.extend([f"{self.user}@{self.host}", command])

        try:
            result = subprocess.run(
                ssh_cmd, capture_output=True, text=True, timeout=timeout
            )
            return result.returncode, result.stdout, result.stderr
        except subprocess.TimeoutExpired:
            return 1, "", f"Command timed out after {timeout} seconds"

    def setup_engines(self) -> bool:
        """Install and build all engine versions"""
        print(f"Setting up engines on {self.host}...")

        # Create base directory
        setup_cmd = f"""
        sudo mkdir -p {self.base_path}
        sudo chown ubuntu:ubuntu {self.base_path}
        sudo apt-get update
        sudo apt-get install -y build-essential git pkg-config libssl-dev python3 python3-pip
        """

        returncode, stdout, stderr = self._execute_remote_command(
            setup_cmd, timeout=300
        )
        if returncode != 0:
            print(f"Failed to setup base environment: {stderr}")
            return False

        # Install each engine
        for engine_name, config in self.engines.items():
            print(f"Installing {engine_name}...")
            if not self._install_engine(engine_name, config):
                print(f"Failed to install {engine_name}")
                return False

        # Setup valkey-glide repository
        print("Setting up valkey-glide repository...")
        repo_cmd = f"""
        cd /home/ubuntu
        if [ ! -d "valkey-glide" ]; then
            git clone https://github.com/valkey-io/valkey-glide.git
        fi
        cd valkey-glide && git pull origin main
        cd utils && pip3 install -r requirements.txt || true
        """

        returncode, stdout, stderr = self._execute_remote_command(repo_cmd)
        if returncode != 0:
            print(f"Warning: Failed to setup repository: {stderr}")

        return True

    def _install_engine(self, engine_name: str, config: Dict) -> bool:
        """Install and build a specific engine version"""
        engine_path = f"{self.base_path}/{engine_name}"

        install_cmd = f"""
        cd {self.base_path}
        if [ ! -d "{engine_name}" ]; then
            git clone {config['repo']} {engine_name}
        fi
        cd {engine_name}
        git fetch origin
        git checkout {config['branch']}
        git pull origin {config['branch']}
        make clean || true
        make -j$(nproc) BUILD_TLS=yes
        """

        returncode, stdout, stderr = self._execute_remote_command(
            install_cmd, timeout=600
        )
        return returncode == 0

    def start_cluster(
        self,
        engine_version: str,
        cluster_mode: bool = True,
        shard_count: int = 3,
        replica_count: int = 1,
        tls: bool = False,
    ) -> Optional[List[str]]:
        """Start cluster with specific engine version"""

        if engine_version not in self.engines:
            print(f"Unknown engine version: {engine_version}")
            return None

        config = self.engines[engine_version]
        engine_path = f"{self.base_path}/{engine_version}"

        print(f"Starting {engine_version} cluster...")

        # Calculate port range for this engine
        base_port = 6379 + config["port_offset"]

        # Get private IP for VPC communication
        get_private_ip_cmd = (
            "curl -s http://169.254.169.254/latest/meta-data/local-ipv4"
        )
        returncode, private_ip, stderr = self._execute_remote_command(
            get_private_ip_cmd, timeout=10
        )

        if returncode != 0 or not private_ip.strip():
            print("Warning: Could not get private IP, using provided host")
            bind_host = "0.0.0.0"
            cluster_host = self.host
        else:
            bind_host = "0.0.0.0"  # Bind to all interfaces
            cluster_host = private_ip.strip()  # Use private IP for cluster endpoints

        # Use modified cluster_manager.py with engine-specific settings
        cluster_cmd = f"""
        cd {self.repo_path}/utils
        export PATH={engine_path}/src:$PATH
        export ENGINE_PATH={engine_path}
        export BASE_PORT={base_port}
        
        python3 cluster_manager.py start \\
            {'--cluster-mode' if cluster_mode else ''} \\
            -n {shard_count} \\
            -r {replica_count} \\
            --host {bind_host} \\
            {'--tls' if tls else ''}
        """

        returncode, stdout, stderr = self._execute_remote_command(
            cluster_cmd, timeout=180
        )

        if returncode != 0:
            print(f"Failed to start {engine_version} cluster: {stderr}")
            return None

        # Parse endpoints and use private IP for VPC access
        try:
            lines = stdout.strip().split("\n")
            json_line = None
            for line in lines:
                if line.strip().startswith("[") and line.strip().endswith("]"):
                    json_line = line.strip()
                    break

            if json_line:
                endpoints_data = json.loads(json_line)
                endpoints = []
                for endpoint in endpoints_data:
                    if isinstance(endpoint, dict) and "port" in endpoint:
                        # Use private IP for VPC communication
                        endpoints.append(f"{cluster_host}:{endpoint['port']}")
                    elif isinstance(endpoint, str) and ":" in endpoint:
                        _, port = endpoint.split(":")
                        endpoints.append(f"{cluster_host}:{port}")

                print(f"{engine_version} cluster started. VPC endpoints: {endpoints}")
                return endpoints
            else:
                print("Could not parse cluster endpoints")
                return None

        except json.JSONDecodeError as e:
            print(f"Failed to parse output: {e}")
            return None

    def stop_cluster(self, engine_version: Optional[str] = None) -> bool:
        """Stop cluster(s)"""
        if engine_version:
            print(f"Stopping {engine_version} cluster...")
            config = self.engines.get(engine_version)
            if not config:
                print(f"Unknown engine version: {engine_version}")
                return False

            engine_path = f"{self.base_path}/{engine_version}"
            stop_cmd = f"""
            cd {self.repo_path}/utils
            export PATH={engine_path}/src:$PATH
            python3 cluster_manager.py stop
            """
        else:
            print("Stopping all clusters...")
            stop_cmd = f"cd {self.repo_path}/utils && python3 cluster_manager.py stop"

        returncode, stdout, stderr = self._execute_remote_command(stop_cmd)
        return returncode == 0

    def list_engines(self) -> Dict:
        """List available engines and their status"""
        status_cmd = f"""
        cd {self.base_path}
        for engine in */; do
            if [ -d "$engine" ]; then
                engine_name=$(basename "$engine")
                if [ -f "$engine/src/redis-server" ] || [ -f "$engine/src/valkey-server" ]; then
                    echo "$engine_name:installed"
                else
                    echo "$engine_name:not_built"
                fi
            fi
        done
        """

        returncode, stdout, stderr = self._execute_remote_command(status_cmd)

        engines_status = {}
        if returncode == 0:
            for line in stdout.strip().split("\n"):
                if ":" in line:
                    name, status = line.split(":", 1)
                    engines_status[name] = status

        return engines_status

    def get_cluster_info(self, engine_version: str) -> Optional[Dict]:
        """Get cluster information for specific engine"""
        if engine_version not in self.engines:
            return None

        config = self.engines[engine_version]
        engine_path = f"{self.base_path}/{engine_version}"
        base_port = 6379 + config["port_offset"]

        info_cmd = f"""
        cd {engine_path}/src
        ./{config['binary_prefix']}-cli -h {self.host} -p {base_port} cluster nodes 2>/dev/null || echo "No cluster running"
        """

        returncode, stdout, stderr = self._execute_remote_command(info_cmd)

        return {
            "engine": engine_version,
            "host": self.host,
            "base_port": base_port,
            "status": (
                "running"
                if returncode == 0 and "No cluster running" not in stdout
                else "stopped"
            ),
            "cluster_info": stdout.strip() if returncode == 0 else None,
        }


def main():
    parser = argparse.ArgumentParser(
        description="Multi-Engine Manager for VPC Linux Instance"
    )
    parser.add_argument("--host", help="VPC Linux instance IP/hostname")
    parser.add_argument("--user", default="ubuntu", help="SSH user")
    parser.add_argument("--key-path", help="SSH private key path")

    subparsers = parser.add_subparsers(dest="command", help="Commands")

    # Setup command
    setup_parser = subparsers.add_parser("setup", help="Install all engine versions")

    # Start command
    start_parser = subparsers.add_parser(
        "start", help="Start cluster with specific engine"
    )
    start_parser.add_argument(
        "--engine", required=True, help="Engine version (e.g., valkey-8.0, redis-7.2)"
    )
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

    # Stop command
    stop_parser = subparsers.add_parser("stop", help="Stop cluster")
    stop_parser.add_argument(
        "--engine", help="Engine version (optional, stops all if not specified)"
    )

    # List command
    list_parser = subparsers.add_parser("list", help="List available engines")

    # Info command
    info_parser = subparsers.add_parser("info", help="Get cluster info")
    info_parser.add_argument("--engine", required=True, help="Engine version")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return 1

    # Get credentials
    host = args.host or os.environ.get("VALKEY_VPC_HOST")
    if not host:
        print(
            "Error: Host must be specified via --host or VALKEY_VPC_HOST environment variable"
        )
        return 1

    key_content = os.environ.get("SSH_PRIVATE_KEY_CONTENT")

    try:
        manager = MultiEngineManager(host, args.user, args.key_path, key_content)

        if args.command == "setup":
            success = manager.setup_engines()
            return 0 if success else 1

        elif args.command == "start":
            endpoints = manager.start_cluster(
                engine_version=args.engine,
                cluster_mode=args.cluster_mode,
                shard_count=args.shard_count,
                replica_count=args.replica_count,
                tls=args.tls,
            )
            if endpoints:
                print("CLUSTER_ENDPOINTS=" + ",".join(endpoints))
                return 0
            else:
                return 1

        elif args.command == "stop":
            success = manager.stop_cluster(args.engine)
            return 0 if success else 1

        elif args.command == "list":
            engines = manager.list_engines()
            print(json.dumps(engines, indent=2))
            return 0

        elif args.command == "info":
            info = manager.get_cluster_info(args.engine)
            if info:
                print(json.dumps(info, indent=2))
                return 0
            else:
                return 1

    except Exception as e:
        print(f"Error: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
