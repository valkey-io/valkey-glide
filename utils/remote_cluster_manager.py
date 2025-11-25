#!/usr/bin/env python3

import os
import sys
import argparse
import paramiko
import time
import json
import tempfile
from pathlib import Path

class ClusterRegistry:
    """Manages cluster instances to handle parallel invocations"""
    
    def __init__(self, remote_host):
        self.remote_host = remote_host
        self.clusters = {}
        self.lock_file = f"/tmp/remote_cluster_{remote_host.replace('.', '_')}.lock"
        self.strace_processes = []
    
    def get_cluster_key(self, cluster_mode=False, tls=False, shards=3, replicas=1, **kwargs):
        """Generate unique key for cluster configuration"""
        return f"{'cluster' if cluster_mode else 'standalone'}_{'tls' if tls else 'notls'}_{shards}s_{replicas}r"
    
    def get_or_create_cluster(self, ssh_client, **params):
        """Get existing cluster or create new one"""
        cluster_key = self.get_cluster_key(**params)
        
        if cluster_key in self.clusters:
            print(f"Reusing existing cluster: {cluster_key}")
            return self.clusters[cluster_key]
        
        print(f"Creating new cluster: {cluster_key}")
        cluster_info = self._create_cluster(ssh_client, **params)
        self.clusters[cluster_key] = cluster_info
        return cluster_info
    
    def _create_cluster(self, ssh_client, cluster_mode=False, tls=False, shards=3, replicas=1, enable_strace=False):
        """Create a new cluster on remote host"""
        # Get both internal and external IPs
        internal_ip, external_ip = self._get_ips(ssh_client, cluster_mode)
        
        # Build cluster command - use internal IP (original working behavior)
        cmd_args = ["python3", "/home/ubuntu/valkey-glide/utils/cluster_manager.py", "--host", internal_ip]
        
        if tls:
            cmd_args.append("--tls")
        
        cmd_args.append("start")
        cmd_args.extend(["--prefix", f"remote-{'cluster' if cluster_mode else 'standalone'}"])
        
        if cluster_mode:
            cmd_args.append("--cluster-mode")
        
        cmd_args.extend(["-n", str(shards)])
        cmd_args.extend(["-r", str(replicas)])
        
        # Execute cluster creation command with proper PATH
        command = f"cd /home/ubuntu/valkey-glide/utils && export PATH=/opt/engines/valkey-9.0/src:$PATH && {' '.join(cmd_args)}"
        print(f"Executing on remote host: {command}")
        stdin, stdout, stderr = ssh_client.exec_command(command)
        
        # Wait for completion and get output
        exit_status = stdout.channel.recv_exit_status()
        output = stdout.read().decode()
        error = stderr.read().decode()
        
        if exit_status != 0:
            raise Exception(f"Failed to create cluster on remote host: {error}")
        
        print(f"Cluster created successfully on remote host")
        
        # Parse cluster info
        cluster_info = self._parse_cluster_output_new(output, external_ip, internal_ip)
        
        # Start strace monitoring if requested
        if enable_strace:
            self._start_remote_strace(ssh_client, cluster_info)
        
        return cluster_info
    
    def _start_remote_strace(self, ssh_client, cluster_info):
        """Start strace monitoring on remote host"""
        print("Starting strace monitoring on remote host...")
        
        # Create strace directory on remote host
        strace_dir = f"/tmp/remote_strace_logs_{int(time.time())}"
        ssh_client.exec_command(f"mkdir -p {strace_dir}")
        
        # Find valkey/redis processes and attach strace
        stdin, stdout, stderr = ssh_client.exec_command("pgrep -f '(valkey-server|redis-server)'")
        pids = stdout.read().decode().strip().split('\n')
        
        for pid in pids:
            if pid:
                strace_cmd = f"nohup strace -e trace=signal -f -o {strace_dir}/strace_signals_pid_{pid}.log -p {pid} > /dev/null 2>&1 &"
                print(f"Starting strace for PID {pid} on remote host")
                ssh_client.exec_command(strace_cmd)
                
                self.strace_processes.append({
                    'pid': pid,
                    'log_file': f"{strace_dir}/strace_signals_pid_{pid}.log",
                    'remote_host': self.remote_host
                })
        
        cluster_info['strace_dir'] = strace_dir
        cluster_info['strace_enabled'] = True
    
    def analyze_remote_strace(self, ssh_client, strace_dir):
        """Analyze strace logs and server logs on remote host"""
        print(f"Analyzing strace logs in {strace_dir} on remote host...")
        
        # List log files
        stdin, stdout, stderr = ssh_client.exec_command(f"ls {strace_dir}/strace_signals_pid_*.log 2>/dev/null || echo 'No log files found'")
        log_files = stdout.read().decode().strip().split('\n')
        
        for log_file in log_files:
            if log_file and 'strace_signals_pid_' in log_file:
                print(f"\nAnalyzing {log_file}:")
                
                # Search for signals in the log file
                search_cmd = f"grep -n 'SIGTERM\\|SIGKILL\\|SIGINT\\|kill(' {log_file} || echo 'No signals found'"
                stdin, stdout, stderr = ssh_client.exec_command(search_cmd)
                results = stdout.read().decode().strip()
                
                if results and results != 'No signals found':
                    print(f"  SIGNALS FOUND:")
                    for line in results.split('\n'):
                        print(f"    {line}")
                else:
                    print(f"  No signals found in {log_file}")
        
        # Also analyze server logs
        self.analyze_server_logs(ssh_client)
    
    def analyze_server_logs(self, ssh_client):
        """Analyze server log files for the specific servers in our config files"""
        print("\n=== Analyzing Server Logs for Current Test Servers ===")
        
        # Get server info from config files
        config_files = [
            "/tmp/connection_test_standalone_config.json",
            "/tmp/connection_test_cluster_config.json", 
            "/tmp/connection_test_az_cluster_config.json"
        ]
        
        for config_file in config_files:
            print(f"\nChecking servers from {config_file}:")
            
            # Read config file to get cluster folder and ports
            cat_cmd = f"cat {config_file} 2>/dev/null || echo 'Config not found'"
            stdin, stdout, stderr = ssh_client.exec_command(cat_cmd)
            config_content = stdout.read().decode().strip()
            
            if "Config not found" in config_content:
                print(f"  Config file not found: {config_file}")
                continue
                
            try:
                import json
                config = json.loads(config_content)
                cluster_folder = config.get('cluster_folder', '')
                nodes = config.get('nodes', [])
                
                if not cluster_folder or not nodes:
                    print(f"  Invalid config in {config_file}")
                    continue
                    
                print(f"  Cluster folder: {cluster_folder}")
                
                # Check server logs for each node
                for node in nodes:
                    port = node.get('port')
                    if port:
                        log_path = f"{cluster_folder}/{port}/server.log"
                        
                        # Check if log file exists and analyze it
                        grep_cmd = f"test -f '{log_path}' && grep -i 'sigterm\\|sigkill\\|signal\\|terminated\\|killed\\|crash\\|exit\\|shutdown' '{log_path}' 2>/dev/null | tail -5 || echo 'Log not found'"
                        stdin, stdout, stderr = ssh_client.exec_command(grep_cmd)
                        results = stdout.read().decode().strip()
                        
                        if results and "Log not found" not in results:
                            print(f"    Port {port} - TERMINATION MESSAGES:")
                            for line in results.split('\n'):
                                if line.strip():
                                    print(f"      {line.strip()}")
                        else:
                            print(f"    Port {port} - No termination messages or log not found")
                            
            except Exception as e:
                print(f"  Error parsing config {config_file}: {e}")
        
        # Check system logs for valkey process termination
        print("\n=== Checking System Logs ===")
        journalctl_cmd = "journalctl --since '30 minutes ago' | grep -i 'valkey\\|redis' | grep -i 'sigterm\\|killed\\|terminated\\|oom' | tail -10"
        stdin, stdout, stderr = ssh_client.exec_command(journalctl_cmd)
        results = stdout.read().decode().strip()
        
        if results:
            print("System log entries for valkey processes:")
            for line in results.split('\n'):
                if line.strip():
                    print(f"  {line.strip()}")
        else:
            print("No valkey termination entries in system logs")
        
        # Check for OOM killer activity
        print("\n=== Checking for OOM Killer Activity ===")
        oom_cmd = "dmesg | grep -i 'killed process\\|out of memory\\|oom' | grep -i 'valkey\\|redis' | tail -5"
        stdin, stdout, stderr = ssh_client.exec_command(oom_cmd)
        results = stdout.read().decode().strip()
        
        if results:
            print("OOM killer activity:")
            for line in results.split('\n'):
                if line.strip():
                    print(f"  {line.strip()}")
        else:
            print("No OOM killer activity for valkey processes")
    
    def stop_remote_strace(self, ssh_client):
        """Stop strace monitoring on remote host"""
        print("Stopping strace monitoring on remote host...")
        
        # Kill all strace processes
        ssh_client.exec_command("pkill -f 'strace.*trace=signal' || true")
        print("Strace monitoring stopped on remote host")
    
    def _get_ips(self, ssh_client, cluster_mode):
        """Get both internal and external IP addresses of the remote instance"""
        stdin, stdout, stderr = ssh_client.exec_command("hostname -I | awk '{print $1}'")
        internal_ip = stdout.read().decode('utf-8').strip()
        
        # Get external IP for client connections
        stdin, stdout, stderr = ssh_client.exec_command("curl -s http://169.254.169.254/latest/meta-data/public-ipv4")
        external_ip = stdout.read().decode('utf-8').strip()
        
        print(f"Internal IP: {internal_ip}")
        print(f"External IP: {external_ip}")
        print(f"Servers bind to: {internal_ip} (original behavior)")
        print(f"Clients connect to: {internal_ip} (VPC internal)")
        
        return internal_ip, external_ip
        remote_host = os.getenv('VALKEY_REMOTE_HOST', internal_ip)
        command = f"cd /home/ubuntu/valkey-glide/utils && export PATH=/opt/engines/valkey-9.0/src:$PATH && export VALKEY_REMOTE_HOST={remote_host} && {' '.join(cmd_args)}"
        
        print(f"Executing: {command}")
        stdin, stdout, stderr = ssh_client.exec_command(command, timeout=120)
        
        output = stdout.read().decode('utf-8')
        error = stderr.read().decode('utf-8')
        
        print(f"Command output: {output}")
        if error:
            print(f"Command error: {error}")
        
        if stderr.channel.recv_exit_status() != 0:
            raise RuntimeError(f"Cluster creation failed: {error}")
        
        # Parse cluster nodes from output
        cluster_nodes = self._parse_cluster_output(output)
        
        # Replace IP with DNS hostname if VALKEY_REMOTE_HOST is set
        if remote_host and remote_host != internal_ip:
            print(f"Replacing IP {internal_ip} with DNS hostname {remote_host} in cluster nodes")
            final_nodes = cluster_nodes.replace(internal_ip, remote_host)
        else:
            final_nodes = cluster_nodes
        
        # Verify servers are actually running
        self._verify_cluster_running(ssh_client, cluster_nodes, tls=tls)
        
        return {
            'nodes': final_nodes,
            'output': output,
            'internal_ip': internal_ip,
            'external_ip': external_ip
        }
    
    def _get_ips(self, ssh_client, cluster_mode):
        """Get both internal and external IP addresses of the remote instance"""
        stdin, stdout, stderr = ssh_client.exec_command("hostname -I | awk '{print $1}'")
        internal_ip = stdout.read().decode('utf-8').strip()
        
        # Get external IP for client connections
        stdin, stdout, stderr = ssh_client.exec_command("curl -s http://169.254.169.254/latest/meta-data/public-ipv4")
        external_ip = stdout.read().decode('utf-8').strip()
        
        print(f"Internal IP: {internal_ip}")
        print(f"External IP: {external_ip}")
        print(f"Servers bind to: {internal_ip} (original behavior)")
        print(f"Clients connect to: {internal_ip} (VPC internal)")
        
        return internal_ip, external_ip
    
    def _parse_cluster_output(self, output, external_ip, internal_ip):
        """Parse cluster creation output to extract node information"""
        cluster_info = {
            'nodes': [],
            'cluster_folder': '',
            'output': output,
            'internal_ip': internal_ip,
            'external_ip': external_ip,
            'strace_enabled': False
        }
        
        # Look for CLUSTER_NODES in output
        for line in output.split('\n'):
            if 'CLUSTER_NODES=' in line:
                nodes_str = line.split('CLUSTER_NODES=')[1].strip()
                # Parse nodes and convert to list of dicts
                for node_addr in nodes_str.split(','):
                    if ':' in node_addr:
                        host, port = node_addr.split(':', 1)
                        # Use external IP for client connections
                        cluster_info['nodes'].append({
                            'host': external_ip,  # Use external IP for connections
                            'port': int(port)
                        })
            elif 'CLUSTER_FOLDER=' in line:
                cluster_info['cluster_folder'] = line.split('CLUSTER_FOLDER=')[1].strip()
        
        return cluster_info
    
    def write_config_file(self, cluster_info, config_file_path, **params):
        """Write cluster configuration to JSON file for Java to read"""
        config = {
            "cluster_type": "cluster" if params.get('cluster_mode', False) else "standalone",
            "tls_enabled": params.get('tls', False),
            "nodes": cluster_info['nodes'],
            "cluster_folder": cluster_info.get('cluster_folder', ''),
            "strace_enabled": cluster_info.get('strace_enabled', False),
            "strace_output_dir": cluster_info.get('strace_dir', None),
            "remote_host": self.remote_host,
            "timestamp": int(time.time())
        }
        
        # Write config file
        with open(config_file_path, 'w') as f:
            json.dump(config, f, indent=2)
        
        print(f"Cluster configuration written to: {config_file_path}")
        print(f"Config: {json.dumps(config, indent=2)}")
        return config
    
    def _parse_cluster_output_new(self, output, external_ip, internal_ip):
        """Parse cluster creation output to extract node information"""
        cluster_info = {
            'nodes': [],
            'cluster_folder': '',
            'output': output,
            'internal_ip': internal_ip,
            'external_ip': external_ip,
            'strace_enabled': False
        }
        
        # Look for CLUSTER_NODES in output
        for line in output.split('\n'):
            if 'CLUSTER_NODES=' in line:
                nodes_str = line.split('CLUSTER_NODES=')[1].strip()
                # Parse nodes and convert to list of dicts
                for node_addr in nodes_str.split(','):
                    if ':' in node_addr:
                        host, port = node_addr.split(':', 1)
                        # Use internal IP for client connections (servers bind to internal IP)
                        cluster_info['nodes'].append({
                            'host': internal_ip,  # Use internal IP where servers actually bind
                            'port': int(port)
                        })
            elif 'CLUSTER_FOLDER=' in line:
                cluster_info['cluster_folder'] = line.split('CLUSTER_FOLDER=')[1].strip()
        
        return cluster_info
    
    def _parse_cluster_output(self, output):
        """Parse CLUSTER_NODES from cluster manager output"""
        for line in output.split('\n'):
            if line.startswith('CLUSTER_NODES='):
                # Strip whitespace and newlines from the cluster nodes
                cluster_nodes = line.split('=')[1].strip()
                return cluster_nodes
        raise RuntimeError("No CLUSTER_NODES found in output")
    
    def _verify_cluster_running(self, ssh_client, cluster_nodes, tls=False):
        """Verify that cluster servers are actually running and accessible"""
        print(f"Verifying cluster is running: {cluster_nodes}")
        
        # Check if processes are running
        stdin, stdout, stderr = ssh_client.exec_command("ps aux | grep valkey-server | grep -v grep")
        processes = stdout.read().decode('utf-8')
        print(f"Running Valkey processes: {processes}")
        
        # Check listening ports
        stdin, stdout, stderr = ssh_client.exec_command("netstat -tlnp | grep valkey-server")
        ports = stdout.read().decode('utf-8')
        print(f"Listening ports: {ports}")
        
        # If TLS, verify certificates are in place and check server config
        if tls:
            print("[TLS] Verifying TLS configuration...")
            stdin, stdout, stderr = ssh_client.exec_command("ls -la /home/ubuntu/valkey-glide/utils/tls_crts/")
            certs = stdout.read().decode('utf-8')
            print(f"[TLS] Certificates on remote: {certs}")
            
            # Check if valkey-server processes have TLS flags
            stdin, stdout, stderr = ssh_client.exec_command("ps aux | grep valkey-server | grep -E '(tls-|--tls)' | grep -v grep")
            tls_processes = stdout.read().decode('utf-8')
            if tls_processes:
                print(f"[TLS] TLS-enabled processes found: {tls_processes}")
            else:
                print("[TLS] WARNING: No TLS-enabled processes found!")
        
        # Test connectivity to first endpoint
        if cluster_nodes:
            first_endpoint = cluster_nodes.split(',')[0]
            host, port = first_endpoint.split(':')
            # For verification, always use the host as-is since verify_nodes should already be correct
            stdin, stdout, stderr = ssh_client.exec_command(f"timeout 5 bash -c 'echo > /dev/tcp/{host}/{port}'")
            exit_code = stdout.channel.recv_exit_status()
            print(f"Connection test to {host}:{port}: {'SUCCESS' if exit_code == 0 else 'FAILED'}")
            
            if tls and exit_code == 0:
                # Verify certificate files are readable
                stdin, stdout, stderr = ssh_client.exec_command(
                    "cat /home/ubuntu/valkey-glide/utils/tls_crts/ca.crt | head -5"
                )
                ca_content = stdout.read().decode('utf-8')
                print(f"[TLS] CA cert preview: {ca_content[:100]}...")
                
                # Check certificate validity
                stdin, stdout, stderr = ssh_client.exec_command(
                    "openssl x509 -in /home/ubuntu/valkey-glide/utils/tls_crts/server.crt -noout -subject -issuer -dates"
                )
                cert_info = stdout.read().decode('utf-8')
                print(f"[TLS] Server certificate info:\n{cert_info}")
                
                # Try to test TLS handshake from server side
                stdin, stdout, stderr = ssh_client.exec_command(
                    f"timeout 5 openssl s_client -connect {host}:{port} -CAfile /home/ubuntu/valkey-glide/utils/tls_crts/ca.crt < /dev/null 2>&1 | head -30"
                )
                tls_test = stdout.read().decode('utf-8')
                print(f"[TLS] OpenSSL connection test from server:\n{tls_test}")
                
                # Check if server requires client certificates
                stdin, stdout, stderr = ssh_client.exec_command(
                    f"grep -E 'tls-auth-clients|tls-cert-file|tls-key-file' /home/ubuntu/valkey-glide/utils/clusters/*/*/server.log 2>/dev/null | head -10"
                )
                server_tls_config = stdout.read().decode('utf-8')
                if server_tls_config:
                    print(f"[TLS] Server TLS config from logs:\n{server_tls_config}")

class RemoteClusterManager:
    """Main class for managing remote Valkey clusters"""
    
    def __init__(self, host):
        self.host = host
        self.ssh_client = None
        self.registry = ClusterRegistry(host)
        self.local_tls_dir = None
    
    def connect(self):
        """Establish SSH connection"""
        print(f"Connecting to {self.host}...")
        
        self.ssh_client = paramiko.SSHClient()
        self.ssh_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        
        # Get SSH key
        key_path = os.getenv('SSH_PRIVATE_KEY_PATH')
        key_content = os.getenv('SSH_PRIVATE_KEY_CONTENT')
        
        print(f"SSH key path: {key_path}")
        print(f"SSH key content available: {bool(key_content)}")
        
        if key_path and os.path.exists(key_path):
            print(f"Using SSH key file: {key_path}")
            key = paramiko.RSAKey.from_private_key_file(key_path)
        elif key_content:
            print("Using SSH key from environment variable")
            from io import StringIO
            key = paramiko.RSAKey.from_private_key(StringIO(key_content))
        else:
            raise RuntimeError("No SSH key found. Set SSH_PRIVATE_KEY_PATH or SSH_PRIVATE_KEY_CONTENT")
        
        self.ssh_client.connect(
            hostname=self.host,
            username='ubuntu',
            pkey=key,
            timeout=30
        )
        print(f"Connected to {self.host}")
    
    def download_tls_certificates(self, local_tls_dir):
        """Download TLS certificates from remote server to local machine"""
        print(f"Downloading TLS certificates to {local_tls_dir}...")
        
        import os
        os.makedirs(local_tls_dir, exist_ok=True)
        
        sftp = self.ssh_client.open_sftp()
        try:
            remote_tls_dir = "/home/ubuntu/valkey-glide/utils/tls_crts"
            cert_files = ['ca.crt', 'server.crt', 'server.key']
            
            for cert_file in cert_files:
                remote_path = f"{remote_tls_dir}/{cert_file}"
                local_path = os.path.join(local_tls_dir, cert_file)
                
                try:
                    sftp.get(remote_path, local_path)
                    print(f"Downloaded {cert_file} to {local_path}")
                except Exception as e:
                    print(f"Warning: Failed to download {cert_file}: {e}")
        finally:
            sftp.close()
    
    def setup_environment(self, engine_version="valkey-9.0", tls=False):
        """Setup remote environment (Valkey, Python deps, etc.)"""
        print("Setting up remote environment...")
        
        # Check if Valkey is installed
        stdin, stdout, stderr = self.ssh_client.exec_command(f"ls /opt/engines/{engine_version}/src/valkey-server")
        if stdout.channel.recv_exit_status() != 0:
            print(f"ERROR: {engine_version} not found at /opt/engines/{engine_version}/src/valkey-server")
            # List available engines
            stdin, stdout, stderr = self.ssh_client.exec_command("ls -la /opt/engines/")
            engines = stdout.read().decode('utf-8')
            print(f"Available engines: {engines}")
        else:
            print(f"OK: {engine_version} found")
        
        # Check repository
        stdin, stdout, stderr = self.ssh_client.exec_command("ls -la /home/ubuntu/valkey-glide/utils/cluster_manager.py")
        if stdout.channel.recv_exit_status() != 0:
            print("ERROR: valkey-glide repository not found")
        else:
            print("OK: valkey-glide repository found")
        
        # Update repository
        stdin, stdout, stderr = self.ssh_client.exec_command("cd /home/ubuntu/valkey-glide && git pull")
        output = stdout.read().decode('utf-8')
        print(f"Git pull result: {output}")
        
        # Setup TLS certificates if needed
        if tls:
            self._setup_tls_certificates()
        
        # Check Python and dependencies
        stdin, stdout, stderr = self.ssh_client.exec_command("python3 --version")
        python_version = stdout.read().decode('utf-8')
        print(f"Python version: {python_version}")
        
        print("Environment setup complete")
    
    def _setup_tls_certificates(self):
        """Copy TLS certificates to remote server"""
        print("Setting up TLS certificates on remote server...")
        
        # Create tls_crts directory on remote server
        stdin, stdout, stderr = self.ssh_client.exec_command("mkdir -p /home/ubuntu/valkey-glide/utils/tls_crts")
        stdout.read()
        
        # Get local tls_crts directory path
        local_tls_dir = Path(__file__).parent / "tls_crts"
        
        if not local_tls_dir.exists():
            print(f"WARNING: Local TLS certificates directory not found at {local_tls_dir}")
            return
        
        # Copy certificate files
        cert_files = ['ca.crt', 'server.crt', 'server.key']
        sftp = self.ssh_client.open_sftp()
        
        try:
            for cert_file in cert_files:
                local_file = local_tls_dir / cert_file
                if local_file.exists():
                    remote_file = f"/home/ubuntu/valkey-glide/utils/tls_crts/{cert_file}"
                    print(f"Copying {cert_file} to remote server...")
                    sftp.put(str(local_file), remote_file)
                    print(f"OK: {cert_file} copied")
                else:
                    print(f"WARNING: {cert_file} not found locally")
        finally:
            sftp.close()
        
        # Verify certificates are in place
        stdin, stdout, stderr = self.ssh_client.exec_command("ls -la /home/ubuntu/valkey-glide/utils/tls_crts/")
        output = stdout.read().decode('utf-8')
        print(f"Remote TLS certificates: {output}")
    
    def start_cluster(self, cluster_mode=False, tls=False, shards=3, replicas=1):
        """Start a cluster and return endpoints"""
        if not self.ssh_client:
            self.connect()
        
        # Clean up any existing processes first
        print("Cleaning up existing processes...")
        stdin, stdout, stderr = self.ssh_client.exec_command("pkill -f valkey-server")
        stdout.read()
        
        cluster_info = self.registry.get_or_create_cluster(
            self.ssh_client,
            cluster_mode=cluster_mode,
            tls=tls,
            shards=shards,
            replicas=replicas
        )
        
        # Download TLS certificates if TLS is enabled
        if tls:
            # Determine local TLS directory
            script_dir = Path(__file__).parent
            self.local_tls_dir = str(script_dir / "tls_crts")
            self.download_tls_certificates(self.local_tls_dir)
            print(f"TLS certificates downloaded to: {self.local_tls_dir}")
        
        # Output in expected format
        # Output the endpoints for VPC client connections
        endpoints = cluster_info['nodes']
        print(f"CLUSTER_NODES={endpoints}")
        return endpoints
    
    def stop_all_clusters(self):
        """Stop all managed clusters"""
        if not self.ssh_client:
            print("No SSH connection - nothing to stop")
            return
        
        print("Stopping all clusters...")
        
        # Kill all valkey-server processes
        stdin, stdout, stderr = self.ssh_client.exec_command("pkill -f valkey-server")
        stdout.read()
        
        # Use cluster manager stop
        stdin, stdout, stderr = self.ssh_client.exec_command(
            "cd /home/ubuntu/valkey-glide/utils && python3 cluster_manager.py stop --prefix cluster"
        )
        output = stdout.read().decode('utf-8')
        error = stderr.read().decode('utf-8')
        
        if error:
            print(f"Stop output (stderr): {error}")
        if output:
            print(f"Stop output (stdout): {output}")
        
        # Verify all stopped
        stdin, stdout, stderr = self.ssh_client.exec_command("ps aux | grep valkey-server | grep -v grep")
        remaining = stdout.read().decode('utf-8')
        if remaining.strip():
            print(f"WARNING: Some processes still running: {remaining}")
        else:
            print("OK: All Valkey processes stopped")
        
        print("Stop command completed successfully")
    
    def close(self):
        """Close SSH connection"""
        if self.ssh_client:
            self.ssh_client.close()
            print("SSH connection closed")

def main():
    parser = argparse.ArgumentParser(description='Remote Valkey Cluster Manager with strace monitoring')
    parser.add_argument('--host', required=True, help='Remote host address')
    parser.add_argument('--engine-version', default='valkey-9.0', help='Engine version')
    parser.add_argument('--config-file', help='Config file path for Java integration')
    parser.add_argument('--enable-strace', action='store_true', help='Enable strace signal monitoring')
    parser.add_argument('action', choices=['start', 'stop', 'analyze'], help='Action to perform')
    parser.add_argument('--cluster-mode', action='store_true', help='Use cluster mode')
    parser.add_argument('--tls', action='store_true', help='Enable TLS')
    parser.add_argument('-n', '--shards', type=int, default=3, help='Number of shards')
    parser.add_argument('-r', '--replicas', type=int, default=1, help='Number of replicas')
    
    args = parser.parse_args()
    
    manager = RemoteClusterManager(args.host)
    
    try:
        print(f"Starting remote cluster manager for {args.host}")
        print(f"Action: {args.action}")
        
        manager.connect()
        
        # Transfer config file if provided
        if args.config_file and os.path.exists(args.config_file):
            remote_config_path = f"/tmp/{os.path.basename(args.config_file)}"
            print(f"Transferring config file {args.config_file} to {remote_config_path}")
            with manager.ssh_client.open_sftp() as sftp:
                sftp.put(args.config_file, remote_config_path)
        
        if args.action == 'start':
            print(f"Configuration: cluster_mode={args.cluster_mode}, tls={args.tls}, shards={args.shards}, replicas={args.replicas}")
            if args.enable_strace:
                print("Strace monitoring enabled")
            
            manager.connect()
            manager.setup_environment(args.engine_version, tls=args.tls)
            
            # Use ClusterRegistry for enhanced functionality
            registry = ClusterRegistry(args.host)
            cluster_info = registry.get_or_create_cluster(
                manager.ssh_client,
                cluster_mode=args.cluster_mode,
                tls=args.tls,
                shards=args.shards,
                replicas=args.replicas,
                enable_strace=args.enable_strace
            )
            
            # Write config file if requested
            if args.config_file:
                registry.write_config_file(
                    cluster_info, 
                    args.config_file,
                    cluster_mode=args.cluster_mode,
                    tls=args.tls,
                    shards=args.shards,
                    replicas=args.replicas
                )
            
            # Output for Java integration
            nodes_str = ','.join([f"{node['host']}:{node['port']}" for node in cluster_info['nodes']])
            print(f"CLUSTER_NODES={nodes_str}")
            print(f"Cluster started successfully")
            
        elif args.action == 'analyze':
            if args.enable_strace:
                manager.connect()
                registry = ClusterRegistry(args.host)
                # Find strace directory from config file or use default
                strace_dir = "/tmp/remote_strace_logs_*"
                registry.analyze_remote_strace(manager.ssh_client, strace_dir)
            else:
                print("Strace analysis requires --enable-strace flag during start")
                
        elif args.action == 'stop':
            manager.connect()
            if args.enable_strace:
                registry = ClusterRegistry(args.host)
                registry.stop_remote_strace(manager.ssh_client)
            manager.stop_all_clusters()
        
        print("Remote cluster manager completed successfully")
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
        sys.exit(1)
    finally:
        manager.close()

if __name__ == '__main__':
    main()