#!/usr/bin/env python3
"""
Windows compatibility patch for cluster_manager.py
This module overrides Unix-specific functions with Windows-compatible implementations.
"""

import sys
import os
import platform
import subprocess
import time
import logging
import signal
from pathlib import Path

# Check if we're on Windows
IS_WINDOWS = platform.system().lower() == 'windows'

if not IS_WINDOWS:
    print("This patch is only for Windows systems")
    sys.exit(1)

# Import the original cluster_manager module
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cluster_manager

# Store original functions
original_wait_for_server = cluster_manager.wait_for_server_shutdown
original_remove_folder = cluster_manager.remove_folder
original_start_servers = cluster_manager.start_servers
original_kill_servers = cluster_manager.kill_servers

def wait_for_server_shutdown(server, cluster_folder: str, use_tls: bool, auth: str, timeout: int = 20):
    """Windows-compatible version of wait_for_server_shutdown."""
    if server is None:
        logging.debug("No server passed, cannot check if shutdown. Consider server down.")
        return True

    # Extract PID from server info
    if hasattr(server, 'pid'):
        process_id = server.pid
    elif hasattr(server, 'process') and hasattr(server.process, 'pid'):
        process_id = server.process.pid
    else:
        # Try to extract from server_info
        try:
            server_info = str(server).replace("'", '"')
            import json
            server_dict = json.loads(server_info)
            process_id = server_dict.get('pid')
        except:
            logging.debug(f"Could not extract PID from server: {server}")
            return True

    if not process_id:
        logging.debug("No PID found, consider server down")
        return True

    start = time.time()
    while (time.time() - start) < timeout:
        try:
            # Check if process still exists using tasklist
            result = subprocess.run(
                ["tasklist", "/FI", f"PID eq {process_id}"],
                capture_output=True,
                text=True,
                timeout=2
            )

            # If the PID is not in the output, the process is dead
            if str(process_id) not in result.stdout:
                logging.debug(f"Success: server process {process_id} is down")
                return True

        except subprocess.TimeoutExpired:
            pass
        except Exception as e:
            logging.debug(f"Error checking process: {e}")

        time.sleep(0.5)

    logging.error(f"Server process {process_id} did not shutdown within {timeout} seconds")
    return False

def remove_folder(folder_path: Path):
    """Windows-compatible version of remove_folder."""
    if not folder_path.exists():
        return

    # Try Windows rmdir command
    try:
        subprocess.run(
            ["rmdir", "/S", "/Q", str(folder_path)],
            shell=True,
            check=False,
            capture_output=True,
            timeout=5
        )
    except:
        pass

    # If folder still exists, try Python's rmtree
    if folder_path.exists():
        import shutil
        max_attempts = 3
        for attempt in range(max_attempts):
            try:
                shutil.rmtree(str(folder_path), ignore_errors=True)
                if not folder_path.exists():
                    break
            except:
                pass
            if attempt < max_attempts - 1:
                time.sleep(1)

def start_servers(
    servers: int,
    folder_path: Path,
    tls: bool,
    auth: str,
    cmd_args: list[str],
    is_cluster: bool = False,
    shard_count: int = 1,
    replica_count: int = 1,
    load_module: list[str] = [],
    **kwargs,
):
    """Windows-compatible version of start_servers."""
    import json

    servers_list = []
    for i in range(servers):
        port = 16379 + i
        server_folder = folder_path / f"node-{port}"
        server_folder.mkdir(parents=True, exist_ok=True)

        # Build command
        cmd = ["redis-server.exe"] if Path("redis-server.exe").exists() else ["redis-server"]

        # Add config options
        cmd.extend(["--port", str(port)])
        cmd.extend(["--dir", str(server_folder)])
        cmd.extend(["--logfile", str(server_folder / "redis.log")])
        cmd.extend(["--save", ""])  # Disable persistence

        if is_cluster:
            cmd.extend(["--cluster-enabled", "yes"])
            cmd.extend(["--cluster-config-file", str(server_folder / "nodes.conf")])
            cmd.extend(["--cluster-node-timeout", "15000"])

        if auth:
            cmd.extend(["--requirepass", auth])
            if is_cluster:
                cmd.extend(["--masterauth", auth])

        if tls:
            # Add TLS configuration if needed
            pass

        # Add any additional arguments
        cmd.extend(cmd_args)

        # Start the server with Windows-specific options
        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                creationflags=subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
            )

            # Create server info dict
            server_info = {
                "pid": process.pid,
                "port": port,
                "host": "localhost",
                "process": process,
                "cluster_folder": str(server_folder)
            }

            servers_list.append(server_info)
            logging.debug(f"Started Redis server on port {port} with PID {process.pid}")

        except Exception as e:
            logging.error(f"Failed to start server on port {port}: {e}")

    # Wait for servers to be ready
    time.sleep(2)

    # If cluster mode, create the cluster
    if is_cluster and len(servers_list) >= 3:
        time.sleep(3)  # Give servers more time to start

        # Build cluster create command
        cluster_cmd = ["redis-cli.exe"] if Path("redis-cli.exe").exists() else ["redis-cli"]
        cluster_cmd.extend(["--cluster", "create"])

        # Add all nodes
        for server in servers_list:
            cluster_cmd.append(f"{server['host']}:{server['port']}")

        cluster_cmd.extend(["--cluster-replicas", str(replica_count)])
        cluster_cmd.append("--cluster-yes")

        if auth:
            cluster_cmd.extend(["-a", auth])

        try:
            result = subprocess.run(
                cluster_cmd,
                capture_output=True,
                text=True,
                timeout=30
            )
            if result.returncode == 0:
                logging.info("Cluster created successfully")
            else:
                logging.error(f"Cluster creation failed: {result.stderr}")
        except Exception as e:
            logging.error(f"Failed to create cluster: {e}")

    return servers_list

def kill_servers(servers):
    """Windows-compatible version of kill_servers."""
    if not servers:
        return

    for server in servers:
        try:
            # Extract PID
            if isinstance(server, dict):
                pid = server.get('pid')
            elif hasattr(server, 'pid'):
                pid = server.pid
            elif hasattr(server, 'process') and hasattr(server.process, 'pid'):
                pid = server.process.pid
            else:
                continue

            if pid:
                # Use taskkill on Windows
                subprocess.run(
                    ["taskkill", "/F", "/PID", str(pid)],
                    capture_output=True,
                    timeout=5
                )
                logging.debug(f"Killed server process {pid}")
        except Exception as e:
            logging.debug(f"Error killing server: {e}")

# Override the original functions
cluster_manager.wait_for_server_shutdown = wait_for_server_shutdown
cluster_manager.remove_folder = remove_folder
cluster_manager.start_servers = start_servers
cluster_manager.kill_servers = kill_servers

# Run the main function
if __name__ == "__main__":
    cluster_manager.main()