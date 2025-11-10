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

        # Get the internal IP of the remote host for binding
        internal_ip = self._get_remote_internal_ip()
        if not internal_ip:
            logging.warning(
                "Could not determine remote internal IP, using default bind"
            )
            bind_ip = None
        else:
            logging.info(f"Using internal IP for binding: {internal_ip}")
            bind_ip = internal_ip

        # Clean up old TLS certificates to force fresh generation
        # Note: Cluster stopping is handled by stopAllBeforeTests Gradle task
        if tls and not (tls_cert_file or tls_key_file or tls_ca_cert_file):
            logging.info("Cleaning up old TLS certificates to force fresh generation...")
            cleanup_cmd = f"rm -rf {self.remote_repo_path}/utils/tls_crts"
            self._execute_remote_command(cleanup_cmd, timeout=10)

        # Build cluster_manager.py command with engine-specific PATH
        cmd_parts = [
            f"cd {self.remote_repo_path}/utils",
            "&&",
            f"export PATH={self.engine_path}/src:$PATH",
            "&&",
            "python3 cluster_manager_local.py start",
        ]

        if bind_ip:
            cmd_parts.extend(["--host", bind_ip])

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
        logging.info(f"Executing remote cluster command: {remote_command}")

        # Execute cluster start
        returncode, stdout, stderr = self._execute_remote_command(
            remote_command, timeout=180
        )

        if returncode != 0:
            logging.error(f"Remote cluster start failed with return code: {returncode}")
            logging.error(f"Command: {remote_command}")
            logging.error(f"stdout: {stdout}")
            logging.error(f"stderr: {stderr}")
            return None

        # Parse cluster endpoints from output
        try:
            # Look for CLUSTER_NODES= output from cluster_manager.py
            endpoints = []
            for line in stdout.strip().splitlines():
                if line.startswith("CLUSTER_NODES="):
                    nodes_str = line.split("=", 1)[1].strip()  # Strip the entire value
                    # Parse the comma-separated host:port pairs
                    for node in nodes_str.split(","):
                        node = node.strip()
                        if ":" in node:
                            # Replace localhost/127.0.0.1 with remote host IP
                            host, port = node.rsplit(":", 1)
                            port = port.strip()  # Remove any trailing whitespace
                            if host in ["127.0.0.1", "localhost"]:
                                endpoints.append(f"{self.host}:{port}")
                            else:
                                endpoints.append(
                                    f"{host}:{port}"
                                )  # Ensure clean format
                    break

            if endpoints:
                logging.info(f"Cluster started successfully. Endpoints: {endpoints}")
                logging.info(f"Raw cluster output: {stdout}")
                
                # Verify cluster nodes are actually running
                logging.info("Verifying cluster nodes are running...")
                for endpoint in endpoints:
                    host, port = endpoint.split(':')
                    # Check if process is listening on the port
                    check_cmd = f"ss -tlnp | grep ':{port}' || netstat -tlnp | grep ':{port}' || echo 'Port {port} not found'"
                    check_returncode, check_stdout, check_stderr = self._execute_remote_command(check_cmd, timeout=10)
                    if check_returncode == 0 and port in check_stdout:
                        logging.info(f"OK - Node {endpoint} is listening")
                    else:
                        logging.warning(f"FAIL - Node {endpoint} may not be running: {check_stdout}")
                
                # Check cluster status and topology
                if endpoints:
                    first_endpoint = endpoints[0]
                    host, port = first_endpoint.split(':')
                    
                    # Get cluster nodes info to see topology
                    cluster_nodes_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'CLUSTER NODES' | valkey-cli -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
                    nodes_returncode, nodes_stdout, nodes_stderr = self._execute_remote_command(cluster_nodes_cmd, timeout=15)
                    if nodes_returncode == 0:
                        logging.info(f"Cluster topology:")
                        for line in nodes_stdout.strip().split('\n'):
                            if line.strip():
                                logging.info(f"  {line}")
                    else:
                        logging.warning(f"Could not get cluster nodes: {nodes_stderr}")
                    
                    # Get cluster info to see overall status
                    cluster_info_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'CLUSTER INFO' | valkey-cli -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
                    info_returncode, info_stdout, info_stderr = self._execute_remote_command(cluster_info_cmd, timeout=15)
                    if info_returncode == 0:
                        logging.info(f"Cluster status:")
                        for line in info_stdout.strip().split('\n'):
                            if 'cluster_state' in line or 'cluster_slots' in line or 'cluster_known_nodes' in line:
                                logging.info(f"  {line}")
                    else:
                        logging.warning(f"Could not get cluster info: {info_stderr}")
                        
                    # Test connectivity to each endpoint
                    logging.info("Testing connectivity to each cluster endpoint...")
                    for endpoint in endpoints:  # Test ALL endpoints, not just first 3
                        ep_host, ep_port = endpoint.split(':')
                        ping_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'PING' | valkey-cli -h {ep_host} -p {ep_port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
                        ping_returncode, ping_stdout, ping_stderr = self._execute_remote_command(ping_cmd, timeout=10)
                        if ping_returncode == 0 and 'PONG' in ping_stdout:
                            logging.info(f"OK - {endpoint} responds to PING")
                        else:
                            logging.warning(f"FAIL - {endpoint} failed PING: {ping_stderr}")

                # Verify connectivity to endpoints
                logging.info("Verifying connectivity to cluster endpoints...")
                reachable_endpoints = []
                for endpoint in endpoints:
                    if self._test_endpoint_connectivity(endpoint):
                        reachable_endpoints.append(endpoint)
                        logging.info(f"[OK] {endpoint} is reachable")
                    else:
                        logging.warning(f"[FAIL] {endpoint} is not reachable")

                if not reachable_endpoints:
                    logging.error("No endpoints are reachable from local machine")
                    return None
                elif len(reachable_endpoints) < len(endpoints):
                    logging.warning(
                        f"Only {len(reachable_endpoints)}/{len(endpoints)} endpoints are reachable"
                    )

                # Copy TLS certificates back to local machine if using defaults
                if tls and not (tls_cert_file or tls_key_file or tls_ca_cert_file):
                    logging.info("Copying generated TLS certificates from remote...")
                    
                    # Copy and verify certificates
                    local_cert_files = {
                        "ca.crt": "ca_cert_local.pem",
                        "server.crt": "server_cert_local.pem", 
                        "server.key": "server_key_local.pem"
                    }
                    
                    for remote_name, local_name in local_cert_files.items():
                        remote_path = f"{self.remote_repo_path}/utils/tls_crts/{remote_name}"
                        if self._copy_file_from_remote(remote_path, local_name):
                            logging.info(f"Copied {remote_name} to {local_name}")
                            
                            # Print certificate content for debugging
                            try:
                                with open(local_name, 'rb') as f:
                                    cert_content = f.read()
                                    logging.info(f"Certificate {remote_name} length: {len(cert_content)} bytes")
                                    logging.info(f"Certificate {remote_name} first 100 bytes: {cert_content[:100]}")
                                    logging.info(f"Certificate {remote_name} last 100 bytes: {cert_content[-100:]}")
                                    
                                    # Check for line ending types
                                    lf_count = cert_content.count(b'\n')
                                    crlf_count = cert_content.count(b'\r\n')
                                    cr_count = cert_content.count(b'\r') - crlf_count
                                    
                                    logging.info(f"Certificate {remote_name} line endings: LF={lf_count}, CRLF={crlf_count}, CR={cr_count}")
                                    
                                    # Print as hex for exact comparison with Rust output
                                    hex_first = ' '.join(f'{b:02x}' for b in cert_content[:50])
                                    logging.info(f"Certificate {remote_name} first 50 bytes hex: {hex_first}")
                                    
                                    # Check for PEM structure
                                    if b'-----BEGIN' in cert_content and b'-----END' in cert_content:
                                        logging.info(f"Certificate {remote_name} appears to be valid PEM format")
                                        
                                        # Check if base64 content has proper line breaks
                                        lines = cert_content.decode('utf-8', errors='ignore').split('\n')
                                        base64_lines = [line for line in lines if line and not line.startswith('-----')]
                                        if base64_lines:
                                            avg_line_length = sum(len(line) for line in base64_lines) / len(base64_lines)
                                            logging.info(f"Certificate {remote_name} average base64 line length: {avg_line_length:.1f}")
                                    else:
                                        logging.warning(f"Certificate {remote_name} does NOT appear to be valid PEM format")
                                        
                            except Exception as e:
                                logging.error(f"Failed to read copied certificate {local_name}: {e}")
                        else:
                            logging.error(f"Failed to copy {remote_name}")
                            
                    # Test certificate on Linux server side
                    self.test_certificates_on_server(endpoints)

                    # Create local tls_crts directory
                    import os

                    local_tls_dir = os.path.join(os.path.dirname(__file__), "tls_crts")
                    os.makedirs(local_tls_dir, exist_ok=True)

                    # Copy certificates
                    if remote_tls_cert:
                        local_cert_path = os.path.join(local_tls_dir, "server.crt")
                        self._copy_file_from_remote(remote_tls_cert, local_cert_path)
                        if os.path.exists(local_cert_path):
                            logging.info(f"Successfully copied {remote_tls_cert} to {local_cert_path}")
                        else:
                            logging.error(f"Failed to copy {remote_tls_cert} to {local_cert_path}")
                    
                    if remote_tls_key:
                        local_key_path = os.path.join(local_tls_dir, "server.key")
                        self._copy_file_from_remote(remote_tls_key, local_key_path)
                        if os.path.exists(local_key_path):
                            logging.info(f"Successfully copied {remote_tls_key} to {local_key_path}")
                        else:
                            logging.error(f"Failed to copy {remote_tls_key} to {local_key_path}")
                    
                    if remote_tls_ca:
                        local_ca_path = os.path.join(local_tls_dir, "ca.crt")
                        self._copy_file_from_remote(remote_tls_ca, local_ca_path)
                        if os.path.exists(local_ca_path):
                            logging.info(f"Successfully copied {remote_tls_ca} to {local_ca_path}")
                        else:
                            logging.error(f"Failed to copy {remote_tls_ca} to {local_ca_path}")
                            
                    # Verify all required certificates exist
                    required_certs = [
                        os.path.join(local_tls_dir, "server.crt"),
                        os.path.join(local_tls_dir, "server.key"), 
                        os.path.join(local_tls_dir, "ca.crt")
                    ]
                    missing_certs = [cert for cert in required_certs if not os.path.exists(cert)]
                    if missing_certs:
                        logging.error(f"Missing TLS certificates: {missing_certs}")
                    else:
                        logging.info("All TLS certificates successfully copied and verified")

                # Run TLS diagnostics if TLS is enabled
                if tls:
                    self.diagnose_tls_issue(endpoints)
                    self.test_cluster_discovery_tls(endpoints)
                    
                    # Test glide-core cluster TLS to isolate Java vs Rust issue
                    self.test_glide_core_cluster_tls(endpoints)
                
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

        # Use cluster_manager_local.py if it exists (our updated version), otherwise fall back to cluster_manager.py
        stop_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && python3 cluster_manager_local.py stop --prefix cluster || python3 cluster_manager.py stop --prefix cluster"
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

    def _get_remote_internal_ip(self) -> Optional[str]:
        """Get the internal IP address of the remote host"""
        try:
            # Try to get the IP that would be used to reach the internet (usually the VPC internal IP)
            cmd = "ip route get 8.8.8.8 | awk '{print $7; exit}'"
            returncode, stdout, stderr = self._execute_remote_command(cmd)
            if returncode == 0 and stdout.strip():
                internal_ip = stdout.strip()
                logging.debug(f"Detected internal IP via route: {internal_ip}")
                return internal_ip

            # Fallback: get IP of the default interface
            cmd = "hostname -I | awk '{print $1}'"
            returncode, stdout, stderr = self._execute_remote_command(cmd)
            if returncode == 0 and stdout.strip():
                internal_ip = stdout.strip()
                logging.debug(f"Detected internal IP via hostname: {internal_ip}")
                return internal_ip

        except Exception as e:
            logging.warning(f"Failed to detect remote internal IP: {e}")

        return None

    def _test_endpoint_connectivity(self, endpoint: str, timeout: int = 5) -> bool:
        """Test if an endpoint is reachable via TCP connection"""
        try:
            host, port = endpoint.rsplit(":", 1)
            port = int(port)

            import socket

            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(timeout)
            result = sock.connect_ex((host, port))
            sock.close()
            return result == 0
        except Exception as e:
            logging.debug(f"Connectivity test failed for {endpoint}: {e}")
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


    def diagnose_tls_issue(self, endpoints: List[str]) -> None:
        """Diagnose TLS connectivity issues for cluster endpoints"""
        logging.info("=== TLS DIAGNOSTICS ===")
        
        # 1. Check cluster topology and what nodes advertise
        if endpoints:
            first_endpoint = endpoints[0]
            host, port = first_endpoint.split(':')
            
            logging.info("1. Checking cluster topology...")
            cluster_nodes_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'CLUSTER NODES' | valkey-cli -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
            returncode, stdout, stderr = self._execute_remote_command(cluster_nodes_cmd, timeout=15)
            
            if returncode == 0:
                logging.info("Cluster nodes output:")
                for line in stdout.strip().split('\n'):
                    if line.strip():
                        # Parse node info: node_id ip:port@cluster_port flags master/slave ...
                        parts = line.split()
                        if len(parts) >= 2:
                            node_addr = parts[1].split('@')[0]  # Remove cluster port
                            logging.info(f"  Node advertises: {node_addr}")
            else:
                logging.error(f"Failed to get cluster nodes: {stderr}")
        
        # 2. Test TLS handshake to each endpoint
        logging.info("2. Testing TLS handshake to each endpoint...")
        for i, endpoint in enumerate(endpoints):
            host, port = endpoint.split(':')
            logging.info(f"Testing endpoint {i+1}/{len(endpoints)}: {endpoint}")
            
            # Test with openssl s_client
            openssl_cmd = f"echo 'QUIT' | openssl s_client -connect {host}:{port} -servername {host} -verify_return_error -CAfile {self.remote_repo_path}/utils/tls_crts/ca.crt 2>&1"
            returncode, stdout, stderr = self._execute_remote_command(openssl_cmd, timeout=10)
            
            if "Verify return code: 0 (ok)" in stdout:
                logging.info(f"  OK - TLS handshake OK for {endpoint}")
            else:
                logging.warning(f"  FAIL - TLS handshake FAILED for {endpoint}")
                # Extract relevant error info
                for line in stdout.split('\n'):
                    if 'verify error' in line.lower() or 'certificate verify failed' in line.lower():
                        logging.warning(f"    Error: {line.strip()}")
        
        # 3. Check certificate details
        logging.info("3. Checking certificate SAN entries...")
        cert_cmd = f"cd {self.remote_repo_path}/utils && openssl x509 -in tls_crts/server.crt -text -noout | grep -A1 'Subject Alternative Name'"
        returncode, stdout, stderr = self._execute_remote_command(cert_cmd, timeout=5)
        
        if returncode == 0 and stdout.strip():
            logging.info(f"Certificate SAN: {stdout.strip()}")
        else:
            logging.warning("Could not extract certificate SAN entries")
        
        # 4. Test connection order dependency
        logging.info("4. Testing connection order dependency...")
        for i, endpoint in enumerate(endpoints):
            host, port = endpoint.split(':')
            ping_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && timeout 5 echo 'PING' | valkey-cli -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
            returncode, stdout, stderr = self._execute_remote_command(ping_cmd, timeout=10)
            
            if returncode == 0 and 'PONG' in stdout:
                logging.info(f"  Connection {i+1}: {endpoint} - OK")
            else:
                logging.warning(f"  Connection {i+1}: {endpoint} - FAILED: {stderr}")
        
        logging.info("=== END TLS DIAGNOSTICS ===")

    def test_cluster_discovery_tls(self, endpoints: List[str]) -> None:
        """Test if cluster discovery reveals different IPs than initial connections"""
        if not endpoints:
            return
            
        logging.info("=== CLUSTER DISCOVERY TLS TEST ===")
        
        # Connect to first node and get full cluster topology
        first_endpoint = endpoints[0]
        host, port = first_endpoint.split(':')
        
        cluster_nodes_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'CLUSTER NODES' | valkey-cli -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
        returncode, stdout, stderr = self._execute_remote_command(cluster_nodes_cmd, timeout=15)
        
        if returncode == 0:
            discovered_nodes = []
            for line in stdout.strip().split('\n'):
                if line.strip():
                    parts = line.split()
                    if len(parts) >= 2:
                        node_addr = parts[1].split('@')[0]  # Remove cluster port
                        discovered_nodes.append(node_addr)
            
            logging.info(f"Initial endpoints: {endpoints}")
            logging.info(f"Discovered nodes:  {discovered_nodes}")
            
            # Check if discovered nodes match initial endpoints
            initial_set = set(endpoints)
            discovered_set = set(discovered_nodes)
            
            if initial_set == discovered_set:
                logging.info("OK - Discovered nodes match initial endpoints")
            else:
                logging.warning("FAIL - Discovered nodes differ from initial endpoints")
                only_initial = initial_set - discovered_set
                only_discovered = discovered_set - initial_set
                if only_initial:
                    logging.warning(f"  Only in initial: {only_initial}")
                if only_discovered:
                    logging.warning(f"  Only in discovered: {only_discovered}")
        else:
            logging.error(f"Failed to get cluster topology: {stderr}")
        
        logging.info("=== END CLUSTER DISCOVERY TLS TEST ===")


    def test_glide_core_cluster_tls(self, endpoints: List[str]) -> bool:
        """Test glide-core Rust cluster TLS against the remote cluster"""
        if not endpoints:
            return False
            
        logging.info("=== GLIDE-CORE CLUSTER TLS TEST ===")
        
        # Copy TLS certificates to local machine for glide-core test
        local_tls_dir = "tls_test_certs"
        os.makedirs(local_tls_dir, exist_ok=True)
        
        try:
            # Copy certificates from remote
            cert_files = ["ca.crt", "server.crt", "server.key"]
            for cert_file in cert_files:
                remote_path = f"{self.remote_repo_path}/utils/tls_crts/{cert_file}"
                local_path = f"{local_tls_dir}/{cert_file}"
                self._copy_file_from_remote(remote_path, local_path)
                logging.info(f"Copied {cert_file} to {local_path}")
            
            # Create a simple Rust test program
            test_program = f'''
use redis::{{Client, cluster::{{ClusterClient, ClusterClientBuilder}}}};
use std::fs;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {{
    let endpoints = vec![{", ".join(f'"{ep}"' for ep in endpoints)}];
    
    // Read certificates
    let ca_cert = fs::read("{local_tls_dir}/ca.crt")?;
    
    println!("Testing cluster connection to: {{:?}}", endpoints);
    
    // Create cluster client with TLS
    let client = ClusterClientBuilder::new(endpoints)
        .tls(redis::cluster::TlsMode::Secure)
        .certs(redis::TlsCertificates {{
            client_tls: None,
            root_cert: Some(ca_cert),
        }})
        .build()?;
    
    println!("Created cluster client, attempting connection...");
    
    // Test connection
    let mut conn = client.get_async_connection().await?;
    
    println!("Connected successfully! Testing PING...");
    
    // Test basic operation
    let pong: String = redis::cmd("PING").query_async(&mut conn).await?;
    println!("PING response: {{}}", pong);
    
    println!("SUCCESS: Rust glide-core cluster TLS test passed");
    Ok(())
}}
'''
            
            # Write test program
            test_dir = "rust_cluster_test"
            os.makedirs(test_dir, exist_ok=True)
            
            with open(f"{test_dir}/main.rs", "w") as f:
                f.write(test_program)
            
            # Create Cargo.toml
            cargo_toml = '''[package]
name = "cluster_tls_test"
version = "0.1.0"
edition = "2021"

[[bin]]
name = "main"
path = "main.rs"

[dependencies]
redis = { path = "../gh/jduo/valkey-glide/glide-core/redis-rs/redis", features = ["cluster-async", "tokio-comp"] }
tokio = { version = "1", features = ["full"] }
'''
            
            with open(f"{test_dir}/Cargo.toml", "w") as f:
                f.write(cargo_toml)
            
            # Run the test
            logging.info("Running Rust cluster TLS test...")
            result = subprocess.run(
                ["cargo", "run", "--manifest-path", f"{test_dir}/Cargo.toml"],
                capture_output=True,
                text=True,
                timeout=60,
                env={**os.environ, "RUST_LOG": "debug"}
            )
            
            if result.returncode == 0:
                logging.info("SUCCESS - Rust cluster TLS test passed")
                logging.info("This indicates the issue is Java-specific, not in Rust core")
                if "SUCCESS: Rust glide-core cluster TLS test passed" in result.stdout:
                    logging.info("Rust test output: Connection and PING successful")
                return True
            else:
                logging.warning("FAILED - Rust cluster TLS test failed")
                logging.warning("This indicates the issue is in the Rust core")
                logging.warning(f"Test stdout: {result.stdout}")
                logging.warning(f"Test stderr: {result.stderr}")
                
                # Check for specific BadSignature error
                if "BadSignature" in result.stderr:
                    logging.warning("CONFIRMED: Rust core also shows BadSignature error")
                    logging.warning("This is a RustTLS issue in the core, not Java-specific")
                
                return False
                
        except Exception as e:
            logging.error(f"Error running Rust cluster test: {e}")
            return False
        finally:
            # Cleanup
            import shutil
            for cleanup_dir in [local_tls_dir, "rust_cluster_test"]:
                if os.path.exists(cleanup_dir):
                    shutil.rmtree(cleanup_dir)
        
        logging.info("=== END GLIDE-CORE CLUSTER TLS TEST ===")

    def _copy_file_from_remote(self, remote_path: str, local_path: str) -> bool:
        """Copy file from remote host to local machine"""
        try:
            cmd = [
                "scp",
                "-i", self.key_path,
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                f"{self.user}@{self.host}:{remote_path}",
                local_path
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            return result.returncode == 0
            
        except Exception as e:
            logging.error(f"Failed to copy {remote_path} from remote: {e}")
            return False


    def test_certificates_on_server(self, endpoints: List[str]) -> None:
        """Test certificates entirely on the Linux server side"""
        if not endpoints:
            return
            
        logging.info("=== SERVER-SIDE CERTIFICATE TEST ===")
        
        # Test with valkey-cli on server
        first_endpoint = endpoints[0]
        host, port = first_endpoint.split(':')
        
        # Test basic TLS connection
        test_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'PING' | valkey-cli -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
        returncode, stdout, stderr = self._execute_remote_command(test_cmd, timeout=10)
        
        if returncode == 0 and 'PONG' in stdout:
            logging.info("SUCCESS - Server-side valkey-cli TLS connection works")
        else:
            logging.warning(f"FAILED - Server-side valkey-cli TLS connection failed: {stderr}")
        
        # Test cluster mode connection with valkey-cli
        cluster_test_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'CLUSTER INFO' | valkey-cli -c -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
        returncode, stdout, stderr = self._execute_remote_command(cluster_test_cmd, timeout=10)
        
        if returncode == 0 and 'cluster_state:ok' in stdout:
            logging.info("SUCCESS - Server-side valkey-cli CLUSTER mode TLS connection works")
        else:
            logging.warning(f"FAILED - Server-side valkey-cli CLUSTER mode TLS connection failed: {stderr}")
            
        # Test cluster discovery with valkey-cli
        cluster_nodes_cmd = f"cd {self.remote_repo_path}/utils && export PATH={self.engine_path}/src:$PATH && echo 'CLUSTER NODES' | valkey-cli -c -h {host} -p {port} --tls --cert tls_crts/server.crt --key tls_crts/server.key --cacert tls_crts/ca.crt"
        returncode, stdout, stderr = self._execute_remote_command(cluster_nodes_cmd, timeout=10)
        
        if returncode == 0:
            logging.info("SUCCESS - Server-side valkey-cli cluster discovery works")
            logging.info("Cluster nodes discovered:")
            for line in stdout.split('\n')[:6]:  # First 6 nodes
                if line.strip() and '172.31.34.123' in line:
                    parts = line.split()
                    if len(parts) >= 2:
                        node_addr = parts[1].split('@')[0]
                        logging.info(f"  Node: {node_addr}")
        else:
            logging.warning(f"FAILED - Server-side valkey-cli cluster discovery failed: {stderr}")
        
        # Print certificate details on server
        cert_info_cmd = f"cd {self.remote_repo_path}/utils && openssl x509 -in tls_crts/ca.crt -text -noout | head -20"
        returncode, stdout, stderr = self._execute_remote_command(cert_info_cmd, timeout=5)
        
        if returncode == 0:
            logging.info("Server certificate info:")
            for line in stdout.split('\n')[:10]:  # First 10 lines
                if line.strip():
                    logging.info(f"  {line}")
        
        # Print raw certificate content for comparison
        cert_content_cmd = f"cd {self.remote_repo_path}/utils && wc -c tls_crts/ca.crt && echo '=== FIRST 200 CHARS ===' && head -c 200 tls_crts/ca.crt && echo && echo '=== LAST 200 CHARS ===' && tail -c 200 tls_crts/ca.crt && echo && echo '=== LINE ENDINGS CHECK ===' && od -c tls_crts/ca.crt | head -5"
        returncode, stdout, stderr = self._execute_remote_command(cert_content_cmd, timeout=5)
        
        if returncode == 0:
            logging.info("Server certificate raw content and line endings:")
            for line in stdout.split('\n'):
                if line.strip():
                    logging.info(f"  {line}")
        
        # Print certificate as hex for exact comparison
        cert_hex_cmd = f"cd {self.remote_repo_path}/utils && xxd -l 100 tls_crts/ca.crt"
        returncode, stdout, stderr = self._execute_remote_command(cert_hex_cmd, timeout=5)
        
        if returncode == 0:
            logging.info("Server certificate hex (first 100 bytes):")
            for line in stdout.split('\n'):
                if line.strip():
                    logging.info(f"  {line}")
        
        # Test with a simple Rust program on server
        rust_test_program = '''
use std::fs;
use std::process::Command;

fn main() {
    println!("Testing certificate files on server...");
    
    let cert_files = ["tls_crts/ca.crt", "tls_crts/server.crt", "tls_crts/server.key"];
    
    for file in &cert_files {
        match fs::read(file) {
            Ok(content) => {
                println!("File {}: {} bytes", file, content.len());
                println!("First 50 bytes: {:?}", content.iter().take(50).collect::<Vec<_>>());
            }
            Err(e) => println!("Failed to read {}: {}", file, e),
        }
    }
}
'''
        
        # Write and run the test program on server
        write_test_cmd = f"cd {self.remote_repo_path}/utils && cat > cert_test.rs << 'EOF'\n{rust_test_program}\nEOF"
        self._execute_remote_command(write_test_cmd, timeout=5)
        
        compile_cmd = f"cd {self.remote_repo_path}/utils && rustc cert_test.rs -o cert_test"
        returncode, stdout, stderr = self._execute_remote_command(compile_cmd, timeout=10)
        
        if returncode == 0:
            run_cmd = f"cd {self.remote_repo_path}/utils && ./cert_test"
            returncode, stdout, stderr = self._execute_remote_command(run_cmd, timeout=5)
            
            if returncode == 0:
                logging.info("Server-side certificate test output:")
                for line in stdout.split('\n'):
                    if line.strip():
                        logging.info(f"  {line}")
        
        logging.info("=== END SERVER-SIDE CERTIFICATE TEST ===")


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
