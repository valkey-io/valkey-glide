# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import asyncio
from typing import List
from utils import start_servers, stop_servers, parse_cluster_script_start_output, create_client

from glide import (
    GlideClient,
    NodeAddress,
)


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


async def test_standalone_client() -> None:
    print("Testing Async Standalone Client")
    output = start_servers(False, 1, 1)
    servers, folder = parse_cluster_script_start_output(output)
    servers = [NodeAddress(hp.host, hp.port) for hp in servers]
    standalone_client = await create_client(servers, is_cluster=False, is_sync=False)
    await run_commands(standalone_client)
    stop_servers(folder)
    print("Async Standalone Client test completed")


async def test_cluster_client() -> None:
    print("Testing Async Cluster Client")
    output = start_servers(True, 3, 1)
    servers, folder = parse_cluster_script_start_output(output)
    servers = [NodeAddress(hp.host, hp.port) for hp in servers]
    cluster_client = await create_client(servers, is_cluster=True, is_sync=False)
    await run_commands(cluster_client)
    stop_servers(folder)
    print("Async Cluster Client test completed")


async def test_async_clients() -> None:
    print("### Starting Glide's Python Async Client release candidate tests ###")
    await test_cluster_client()
    print("Async Cluster client test passed")
    await test_standalone_client()
    print("Async Standalone client test passed")


def main() -> None:
    asyncio.run(test_async_clients())
    print("All tests completed successfully")


if __name__ == "__main__":
    main()
