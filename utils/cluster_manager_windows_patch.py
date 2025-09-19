#!/usr/bin/env python3
"""
Minimal Windows compatibility patch for cluster_manager.py
This is a triage solution to get basic functionality working on Windows.
"""

import platform
import shutil
import subprocess
import sys
import os
import logging
import time

# Add the parent directory to path to import the original cluster_manager
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Import everything from the original cluster_manager
from cluster_manager import *

# Detect if we're on Windows
IS_WINDOWS = platform.system() == "Windows"

# Override the get_command function to work on Windows
def get_command(commands: List[str]) -> str:
    """Cross-platform command finder using shutil.which instead of 'which' command"""
    for command in commands:
        # On Windows, add .exe if not present
        if IS_WINDOWS and not command.endswith('.exe'):
            cmd_with_exe = f"{command}.exe"
            if shutil.which(cmd_with_exe):
                return cmd_with_exe

        # Try the command as-is
        if shutil.which(command):
            return command

    raise Exception(f"Neither {' nor '.join(commands)} found in the system.")


# Override start_server to handle Windows process creation
def start_server(
    host: str,
    port: Optional[int],
    cluster_folder: str,
    tls: bool,
    tls_args: List[str],
    cluster_mode: bool,
    load_module: Optional[List[str]] = None,
) -> Tuple[Server, str]:
    """Modified start_server that works on Windows without --daemonize"""
    port = port if port else next_free_port()
    logging.debug(f"Creating server {host}:{port}")

    # Create sub-folder for each node
    node_folder = f"{cluster_folder}/{port}"
    Path(node_folder).mkdir(exist_ok=True)

    def get_server_version(server_name):
        result = subprocess.run(
            [server_name, "--version"], capture_output=True, text=True
        )
        version_output = result.stdout
        version_match = re.search(
            r"server v=(\d+\.\d+\.\d+)", version_output, re.IGNORECASE
        )
        if version_match:
            return tuple(map(int, version_match.group(1).split(".")))
        raise Exception("Unable to determine server version.")

    server_name = get_server_command()
    server_version = get_server_version(server_name)

    # Convert paths for Windows
    if IS_WINDOWS:
        node_folder_native = node_folder.replace('/', '\\')
        logfile = f"{node_folder_native}\\server.log"
    else:
        node_folder_native = node_folder
        logfile = f"{node_folder}/server.log"

    # Define command arguments
    cmd_args = [
        server_name,
        f"{'--tls-port' if tls else '--port'}",
        str(port),
        "--cluster-enabled",
        f"{'yes' if cluster_mode else 'no'}",
        "--dir",
        node_folder_native,
        "--logfile",
        logfile,
        "--protected-mode",
        "no",
        "--appendonly",
        "no",
        "--save",
        "",
    ]

    # Add version-specific arguments
    if server_version >= (7, 0, 0):
        cmd_args.extend(["--enable-debug-command", "yes"])
    if cluster_mode and server_version >= (9, 0, 0):
        cmd_args.extend(["--cluster-databases", "16"])

    # Add modules if specified
    if load_module:
        if len(load_module) == 0:
            raise ValueError(
                "Please provide the path(s) to the module(s) you want to load."
            )
        for module_path in load_module:
            cmd_args.extend(["--loadmodule", module_path])

    # Add TLS arguments
    cmd_args += tls_args

    # Handle process creation differently on Windows vs Unix
    if IS_WINDOWS:
        # Windows: Use DETACHED_PROCESS flag instead of --daemonize
        # This creates a background process that survives parent termination
        creationflags = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP

        # Redirect output to files since we can't daemonize
        with open(logfile, 'w') as log:
            p = subprocess.Popen(
                cmd_args,
                stdout=log,
                stderr=subprocess.STDOUT,
                creationflags=creationflags
            )

        # On Windows, we can use the PID directly
        server = Server(host, port)
        server.set_process_id(p.pid)

        # Wait for server to actually start and respond to pings
        import time
        max_attempts = 30  # 30 seconds timeout
        for attempt in range(max_attempts):
            time.sleep(1)
            try:
                # Try to ping the server
                result = subprocess.run(
                    [get_command(["redis-cli", "valkey-cli"]), "-p", str(port), "ping"],
                    capture_output=True,
                    text=True,
                    timeout=1
                )
                if result.stdout.strip() == "PONG":
                    logging.debug(f"Server {host}:{port} started successfully (PID: {p.pid})")
                    break
            except (subprocess.TimeoutExpired, subprocess.SubprocessError):
                pass

            if attempt == max_attempts - 1:
                # Check if process is still alive
                if p.poll() is not None:
                    with open(logfile, 'r') as f:
                        log_content = f.read()
                    raise Exception(f"Redis server process died. Exit code: {p.poll()}\nLast 500 chars of log:\n{log_content[-500:]}")
                else:
                    raise Exception(f"Server {host}:{port} failed to start after {max_attempts} seconds")

    else:
        # Unix/Linux: Use original --daemonize approach
        cmd_args.extend(["--daemonize", "yes"])

        p = subprocess.Popen(
            cmd_args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        output, err = p.communicate(timeout=2)
        if p.returncode != 0:
            raise Exception(
                f"Failed to execute command: {str(p.args)}\n Return code: {p.returncode}\n Error: {err}"
            )

        server = Server(host, port)

        # Read the process ID from the log file
        process_id = wait_for_regex_in_log(
            logfile, r"version=(.*?)pid=([\d]+), just started", 2
        )
        if process_id:
            server.set_process_id(int(process_id))

    return server, node_folder


# Override stop_server for Windows compatibility
def stop_server(
    server: Server, folder_path: str, logfile: Optional[str], pids: Optional[str]
):
    """Modified stop_server that works on Windows"""
    process_id = server.process_id if not pids else int(pids)
    if process_id is not None and process_id > 0:
        try:
            if IS_WINDOWS:
                # Windows: Use taskkill command
                subprocess.run(
                    ["taskkill", "/F", "/PID", str(process_id)],
                    check=False,
                    capture_output=True
                )
            else:
                # Unix/Linux: Use original kill command
                os.kill(process_id, signal.SIGTERM)
        except (OSError, subprocess.CalledProcessError) as e:
            logging.debug(f"Failed to stop process {process_id}: {e}")
    else:
        logging.debug(f"No process ID for {server}, skipping termination")


# Override wait_for_server_shutdown for Windows compatibility
def wait_for_server_shutdown(
    server,
    cluster_folder: str,
    use_tls: bool,
    auth: str,
    timeout: int = 20,
):
    """Modified wait_for_server_shutdown that works on Windows"""
    logging.debug(f"Waiting for server {server} to shutdown")
    timeout_start = time.time()

    if IS_WINDOWS:
        # On Windows, check if process is still running instead of using redis-cli
        process_id = server.process_id
        if process_id and process_id > 0:
            while time.time() < timeout_start + timeout:
                try:
                    # Check if process still exists
                    result = subprocess.run(
                        ["tasklist", "/FI", f"PID eq {process_id}"],
                        capture_output=True,
                        text=True,
                        timeout=2
                    )
                    if str(process_id) not in result.stdout:
                        logging.debug(f"Success: server process {process_id} is down")
                        return True
                except (subprocess.TimeoutExpired, subprocess.SubprocessError):
                    pass
                time.sleep(0.5)
            # Timeout reached, but don't error - just log and return False
            logging.debug(f"Timeout waiting for process {process_id} to shutdown (might already be dead)")
            return False
        else:
            # No valid PID, assume server is not running
            logging.debug(f"No valid process ID for {server}, assuming already shutdown")
            return True
    else:
        # Use original implementation for Unix/Linux
        return cluster_manager._wait_for_server_shutdown(
            server, cluster_folder, use_tls, auth, timeout
        )

# Override remove_folder for Windows compatibility
def remove_folder(folder_path: str):
    """Modified remove_folder that works on Windows"""
    if IS_WINDOWS:
        # Windows: Use rmdir command with /S /Q flags
        # First try to remove read-only attributes
        try:
            subprocess.run(
                ["attrib", "-R", f"{folder_path}\\*.*", "/S"],
                check=False,
                capture_output=True,
                shell=True
            )
            # Use rmdir for Windows
            result = subprocess.run(
                ["rmdir", "/S", "/Q", folder_path],
                check=False,
                capture_output=True,
                shell=True,
                text=True
            )
            if result.returncode != 0 and os.path.exists(folder_path):
                # Fallback: try Python's shutil
                import shutil
                shutil.rmtree(folder_path, ignore_errors=True)
        except Exception as e:
            logging.warning(f"Failed to remove folder {folder_path}: {e}")
    else:
        # Unix/Linux: Use original rm -rf
        subprocess.run(
            ["rm", "-rf", folder_path],
            check=False,
            capture_output=True
        )

# Replace the original functions with our patched versions
import cluster_manager
cluster_manager.get_command = get_command
cluster_manager.start_server = start_server
cluster_manager.stop_server = stop_server
cluster_manager.wait_for_server_shutdown = wait_for_server_shutdown
cluster_manager.remove_folder = remove_folder

# Run the main function with our patches
if __name__ == "__main__":
    main()