
from typing import List, Union

from glide_sync import (GlideClient, GlideClientConfiguration,
                        GlideClusterClient, GlideClusterClientConfiguration,
                        NodeAddress)

from utils import (parse_cluster_script_start_output, start_servers,
                   stop_servers)


def create_client(
    nodes_list: List[NodeAddress] = [("localhost", 6379)], is_cluster: bool = False
) -> Union[GlideClusterClient, GlideClient]:
    addresses: List[NodeAddress] = nodes_list
    if is_cluster:
        config_class = GlideClusterClientConfiguration
        client_class = GlideClusterClient
    else:
        config_class = GlideClientConfiguration
        client_class = GlideClient
    config = config_class(
        addresses=addresses,
        client_name=f"test_{'cluster' if is_cluster else 'standalone'}_client",
    )
    return client_class.create(config)


def run_commands(client: GlideClient) -> None:
    print("Executing commands")
    # Set a bunch of keys
    for i in range(100):
        res = client.set(f"foo{i}".encode(), b"bar")
        if res != "OK":
            print(res)
            raise Exception(f"Unexpected set response, expected 'OK', got {res}")
    print("Keys set successfully")
    # Get the keys
    for i in range(10):
        val = client.get(f"foo{i}")
        if val != b"bar":
            print(val)
            raise Exception(f"Unexpected value, expected b'bar', got {val}")
    print("Keys retrieved successfully")
    # Run some various commands
    pong = client.ping()
    if pong != b"PONG":
        print(pong)
        raise Exception(f"Unexpected ping response, expected b'PONG', got {pong}")
    print(f"Ping successful: {pong}")
    # Set a bunch of keys to delete
    array_of_keys: List[bytes] = [f"foo{i}".encode() for i in range(1, 4)]
    # delete the keys
    deleted_keys_num = client.delete(array_of_keys)
    print(f"Deleted keys: {deleted_keys_num}")
    # check that the correct number of keys were deleted
    if deleted_keys_num != 3:
        print(deleted_keys_num)
        raise Exception(
            f"Unexpected number of keys deleted, expected 3, got {deleted_keys_num}"
        )
    # check that the keys were deleted
    for i in range(1, 4):
        val = client.get(f"foo{i}")
        if val is not None:
            print(val)
            raise Exception(f"Unexpected value, expected None, got {val}")
    print("Keys deleted successfully")

    # Test INCR command
    incr_key = b"counter"
    client.set(incr_key, b"0")
    incr_result = client.incr(incr_key)
    if incr_result != 1:
        raise Exception(f"Unexpected INCR result, expected 1, got {incr_result}")
    print("INCR command successful")

    # Test LPUSH and LRANGE commands
    list_key = b"mylist"
    client.lpush(list_key, [b"world", b"hello"])
    list_values = client.lrange(list_key, 0, -1)
    if list_values != [b"hello", b"world"]:
        raise Exception(
            f"Unexpected LRANGE result, expected [b'hello', b'world'], got {list_values}"
        )
    print("LPUSH and LRANGE commands successful")

    # Test HSET and HGETALL commands
    hash_key = b"myhash"
    client.hset(hash_key, {b"field1": b"value1", b"field2": b"value2"})
    hash_values = client.hgetall(hash_key)
    if hash_values != {b"field1": b"value1", b"field2": b"value2"}:
        raise Exception(
            f"Unexpected HGETALL result, expected {{b'field1': b'value1', b'field2': b'value2'}}, got {hash_values}"
        )
    print("HSET and HGETALL commands successful")

    print("All commands executed successfully")


def test_standalone_client() -> None:
    print("Testing Sync Standalone Client")
    output = start_servers(False, 1, 1)
    servers, folder = parse_cluster_script_start_output(output)
    servers = [NodeAddress(hp.host, hp.port) for hp in servers]
    standalone_client = create_client(servers, is_cluster=False)
    run_commands(standalone_client)
    stop_servers(folder)
    print("Standalone client test completed")


def test_cluster_client() -> None:
    print("Testing Sync Cluster Client")
    output = start_servers(True, 3, 1)
    servers, folder = parse_cluster_script_start_output(output)
    servers = [NodeAddress(hp.host, hp.port) for hp in servers]
    cluster_client = create_client(servers, is_cluster=True)
    run_commands(cluster_client)
    stop_servers(folder)
    print("Cluster client test completed")


def test_async_clients() -> None:
    print("### Starting Glide's Python Sync Client release candidate tests ###")
    test_cluster_client()
    print("Sync Cluster Client test passed")
    test_standalone_client()
    print("Sync Standalone Client test passed")


def main() -> None:
    test_async_clients()
    print("All Sync client tests completed successfully")


if __name__ == "__main__":
    main()
