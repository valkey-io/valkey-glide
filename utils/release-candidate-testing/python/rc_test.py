# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
from typing import List, Tuple
import os
import subprocess
import sys

SCRIPT_FILE = os.path.abspath(f"{__file__}/../../../cluster_manager.py")

from glide import (
    GlideClusterClient,
    GlideClusterClientConfiguration,
    NodeAddress,
    GlideClient,
    GlideClientConfiguration,
)


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


def parse_cluster_script_start_output(output: str) -> Tuple[List[NodeAddress], str]:
    assert "CLUSTER_FOLDER" in output and "CLUSTER_NODES" in output
    lines_output: List[str] = output.splitlines()
    cluster_folder: str = ""
    nodes_addr: List[NodeAddress] = []
    for line in lines_output:
        if "CLUSTER_FOLDER" in line:
            splitted_line = line.split("CLUSTER_FOLDER=")
            assert len(splitted_line) == 2
            cluster_folder = splitted_line[1]
        if "CLUSTER_NODES" in line:
            nodes_list: List[NodeAddress] = []
            splitted_line = line.split("CLUSTER_NODES=")
            assert len(splitted_line) == 2
            nodes_addresses = splitted_line[1].split(",")
            assert len(nodes_addresses) > 0
            for addr in nodes_addresses:
                host, port = addr.split(":")
                nodes_list.append(NodeAddress(host, int(port)))
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


async def run_commands(client: GlideClient) -> None:
    print("Executing commands")
    # Set a bunch of keys
    for i in range(100):
        res = await client.set(f"foo{i}".encode(), b"bar")
        if res != "OK":
            print(res)
            raise Exception(f"Unexpected set response, expected 'OK', got {res}")
    print("Keys set successfully")
    # Get the keys
    for i in range(10):
        val = await client.get(f"foo{i}")
        if val != b"bar":
            print(val)
            raise Exception(f"Unexpected value, expected b'bar', got {val}")
    print("Keys retrieved successfully")
    # Run some various commands
    pong = await client.ping()
    if pong != b"PONG":
        print(pong)
        raise Exception(f"Unexpected ping response, expected b'PONG', got {pong}")
    print(f"Ping successful: {pong}")
    # Set a bunch of keys to delete
    array_of_keys: List[bytes] = [f"foo{i}".encode() for i in range(1, 4)]
    # delete the keys
    deleted_keys_num = await client.delete(array_of_keys)
    print(f"Deleted keys: {deleted_keys_num}")
    # check that the correct number of keys were deleted
    if deleted_keys_num != 3:
        print(deleted_keys_num)
        raise Exception(
            f"Unexpected number of keys deleted, expected 3, got {deleted_keys_num}"
        )
    # check that the keys were deleted
    for i in range(1, 4):
        val = await client.get(f"foo{i}")
        if val is not None:
            print(val)
            raise Exception(f"Unexpected value, expected None, got {val}")
    print("Keys deleted successfully")

    # Test INCR command
    incr_key = b"counter"
    await client.set(incr_key, b"0")
    incr_result = await client.incr(incr_key)
    if incr_result != 1:
        raise Exception(f"Unexpected INCR result, expected 1, got {incr_result}")
    print("INCR command successful")

    # Test LPUSH and LRANGE commands
    list_key = b"mylist"
    await client.lpush(list_key, [b"world", b"hello"])
    list_values = await client.lrange(list_key, 0, -1)
    if list_values != [b"hello", b"world"]:
        raise Exception(
            f"Unexpected LRANGE result, expected [b'hello', b'world'], got {list_values}"
        )
    print("LPUSH and LRANGE commands successful")

    # Test HSET and HGETALL commands
    hash_key = b"myhash"
    await client.hset(hash_key, {b"field1": b"value1", b"field2": b"value2"})
    hash_values = await client.hgetall(hash_key)
    if hash_values != {b"field1": b"value1", b"field2": b"value2"}:
        raise Exception(
            f"Unexpected HGETALL result, expected {{b'field1': b'value1', b'field2': b'value2'}}, got {hash_values}"
        )
    print("HSET and HGETALL commands successful")

    print("All commands executed successfully")


async def create_cluster_client(
    nodes_list: List[NodeAddress] = [("localhost", 6379)]
) -> GlideClusterClient:
    addresses: List[NodeAddress] = nodes_list
    config = GlideClusterClientConfiguration(
        addresses=addresses,
        client_name="test_cluster_client",
    )
    client = await GlideClusterClient.create(config)
    print("Cluster client created successfully")
    return client


async def create_standalone_client(server: List[NodeAddress]) -> GlideClient:
    config = GlideClientConfiguration(
        addresses=server,
        client_name="test_standalone_client",
    )
    client = await GlideClient.create(config)
    print("Standalone client created successfully")
    return client


async def test_standalone_client() -> None:
    print("Testing standalone client")
    output = start_servers(False, 1, 1)
    servers, folder = parse_cluster_script_start_output(output)
    standalone_client = await create_standalone_client(servers)
    await run_commands(standalone_client)
    stop_servers(folder)
    print("Standalone client test completed")


async def test_cluster_client() -> None:
    print("Testing cluster client")
    output = start_servers(True, 3, 1)
    servers, folder = parse_cluster_script_start_output(output)
    cluster_client = await create_cluster_client(servers)
    await run_commands(cluster_client)
    stop_servers(folder)
    print("Cluster client test completed")


async def test_clients() -> None:
    await test_cluster_client()
    print("Cluster client test passed")
    await test_standalone_client()
    print("Standalone client test passed")


def main() -> None:
    asyncio.run(test_clients())
    print("All tests completed successfully")


if __name__ == "__main__":
    main()
