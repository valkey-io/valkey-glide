import os
import subprocess
import sys
from dataclasses import dataclass
from typing import List, Tuple

SCRIPT_FILE = os.path.abspath(f"{__file__}/../../../cluster_manager.py")


@dataclass
class HostPort:
    host: str
    port: int


def start_servers(cluster_mode: bool, shard_count: int, replica_count: int) -> str:
    args_list: List[str] = [sys.executable, SCRIPT_FILE]
    args_list.append("start")
    if cluster_mode:
        args_list.append("--cluster-mode")
    args_list.append(f"-n {shard_count}")
    args_list.append(f"-r {replica_count}")
    p = subprocess.Popen(
        args_list,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output, err = p.communicate(timeout=40)
    if p.returncode != 0:
        raise Exception(f"Failed to create a cluster. Executed: {p}:\n{err}")
    print("Servers started successfully")
    return output


def parse_cluster_script_start_output(output: str) -> Tuple[List[HostPort], str]:
    assert "CLUSTER_FOLDER" in output and "CLUSTER_NODES" in output
    lines_output: List[str] = output.splitlines()
    cluster_folder: str = ""
    nodes_addr: List[HostPort] = []
    for line in lines_output:
        if "CLUSTER_FOLDER" in line:
            splitted_line = line.split("CLUSTER_FOLDER=")
            assert len(splitted_line) == 2
            cluster_folder = splitted_line[1]
        if "CLUSTER_NODES" in line:
            nodes_list: List[HostPort] = []
            splitted_line = line.split("CLUSTER_NODES=")
            assert len(splitted_line) == 2
            nodes_addresses = splitted_line[1].split(",")
            assert len(nodes_addresses) > 0
            for addr in nodes_addresses:
                host, port = addr.split(":")
                nodes_list.append(HostPort(host, int(port)))
            nodes_addr = nodes_list
    print("Cluster script output parsed successfully")
    return nodes_addr, cluster_folder


def stop_servers(folder: str) -> str:
    args_list: List[str] = [sys.executable, SCRIPT_FILE]
    args_list.extend(["stop", "--cluster-folder", folder])
    p = subprocess.Popen(
        args_list,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    output, err = p.communicate(timeout=40)
    if p.returncode != 0:
        raise Exception(f"Failed to stop the cluster. Executed: {p}:\n{err}")
    print("Servers stopped successfully")
    return output
