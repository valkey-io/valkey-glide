import json
import random
import string
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import (
    Any,
    Callable,
    Dict,
    List,
    Mapping,
    Optional,
    Set,
    TypeVar,
    Union,
    cast,
)

import pytest
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.logger import Level as logLevel
from glide_shared.commands.batch import Batch, ClusterBatch
from glide_shared.commands.bitmap import (
    BitFieldGet,
    BitFieldSet,
    BitmapIndexType,
    BitOffset,
    BitOffsetMultiplier,
    BitwiseOperation,
    OffsetOptions,
    SignedEncoding,
    UnsignedEncoding,
)
from glide_shared.commands.command_args import Limit, ListDirection, OrderBy
from glide_shared.commands.core_options import (
    ExpiryGetEx,
    ExpiryTypeGetEx,
    FlushMode,
    InfoSection,
    InsertPosition,
)
from glide_shared.commands.sorted_set import (
    AggregationType,
    GeoSearchByBox,
    GeoSearchByRadius,
    GeospatialData,
    GeoUnit,
    InfBound,
    LexBoundary,
    RangeByIndex,
    ScoreBoundary,
    ScoreFilter,
)
from glide_shared.commands.stream import (
    IdBound,
    MaxId,
    MinId,
    StreamAddOptions,
    StreamClaimOptions,
    StreamGroupOptions,
    StreamReadGroupOptions,
    TrimByMinId,
)
from glide_shared.config import (
    AdvancedGlideClientConfiguration,
    AdvancedGlideClusterClientConfiguration,
    BackoffStrategy,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    NodeAddress,
    ProtocolVersion,
    ReadFrom,
    ServerCredentials,
    TlsAdvancedConfiguration,
)
from glide_shared.constants import (
    OK,
    TClusterResponse,
    TFunctionListResponse,
    TFunctionStatsSingleNodeResponse,
    TResult,
)
from glide_shared.routes import AllNodes
from glide_sync import GlideClient as SyncGlideClient
from glide_sync import GlideClusterClient as SyncGlideClusterClient
from glide_sync import TGlideClient as TSyncGlideClient
from glide_sync.config import GlideClientConfiguration as SyncGlideClientConfiguration
from glide_sync.config import (
    GlideClusterClientConfiguration as SyncGlideClusterClientConfiguration,
)
from glide_sync.logger import Level as SyncLogLevel
from packaging import version

from tests.utils.cluster import ValkeyCluster

TAnyGlideClient = Union[TGlideClient, TSyncGlideClient]

T = TypeVar("T")
DEFAULT_TEST_LOG_LEVEL = logLevel.OFF
DEFAULT_SYNC_TEST_LOG_LEVEL = SyncLogLevel.OFF
USERNAME = "username"
INITIAL_PASSWORD = "initial_password"
NEW_PASSWORD = "new_secure_password"
WRONG_PASSWORD = "wrong_password"


version_str = ""


def is_single_response(response: T, single_res: T) -> bool:
    """
    Recursively checks if a given response matches the type structure of single_res.

    Args:
        response (T): The response to check.
        single_res (T): An object with the expected type structure as an example for the single node response.

    Returns:
        bool: True if response matches the structure of single_res, False otherwise.

     Example:
        >>> is_single_response(["value"], LIST_STR)
        True
        >>> is_single_response([["value"]], LIST_STR)
        False
    """
    if isinstance(single_res, list) and isinstance(response, list):
        return is_single_response(response[0], single_res[0])
    elif isinstance(response, type(single_res)):
        return True
    return False


def get_first_result(
    res: TResult,
) -> bytes:
    while isinstance(res, list):
        res = (
            res[1]
            if not isinstance(res[0], list) and res[0].startswith("127.0.0.1")
            else res[0]
        )

    if isinstance(res, dict):
        res = list(res.values())[0]
    return cast(bytes, res)


def parse_info_response(res: Union[bytes, Dict[bytes, bytes]]) -> Dict[str, str]:
    res_first = get_first_result(res)
    res_decoded = res_first.decode() if isinstance(res_first, bytes) else res_first
    info_lines = [
        line for line in res_decoded.splitlines() if line and not line.startswith("#")
    ]
    info_dict = {}
    for line in info_lines:
        splitted_line = line.split(":")
        key = splitted_line[0]
        value = splitted_line[1]
        info_dict[key] = value
    return info_dict


def get_random_string(length):
    result_str = "".join(random.choice(string.ascii_letters) for i in range(length))
    return result_str


async def check_if_server_version_lt(client: TGlideClient, min_version: str) -> bool:
    global version_str
    if not version_str:
        info = parse_info_response(await client.info([InfoSection.SERVER]))
        version_str = info.get("valkey_version") or info.get("redis_version")  # type: ignore
    assert version_str is not None, "Server version not found in INFO response"
    return version.parse(version_str) < version.parse(min_version)


def sync_check_if_server_version_lt(client: TSyncGlideClient, min_version: str) -> bool:
    info = parse_info_response(client.info([InfoSection.SERVER]))
    version_str = info.get("valkey_version") or info.get("redis_version")
    assert version_str is not None, "Server version not found in INFO response"
    return version.parse(version_str) < version.parse(min_version)


async def get_version(client: TGlideClient) -> str:
    info = parse_info_response(await client.info([InfoSection.SERVER]))
    return info.get("valkey_version") or info.get("redis_version")  # type: ignore


def sync_get_version(client: TSyncGlideClient) -> str:
    info = parse_info_response(client.info([InfoSection.SERVER]))
    return info.get("valkey_version") or info.get("redis_version")  # type: ignore


def check_version_lt(version_str: str, min_version: str) -> bool:
    return version.parse(version_str) < version.parse(min_version)


def compare_maps(
    map1: Optional[
        Union[
            Mapping[str, TResult],
            Dict[str, TResult],
            Mapping[bytes, TResult],
            Dict[bytes, TResult],
        ]
    ],
    map2: Optional[
        Union[
            Mapping[str, TResult],
            Dict[str, TResult],
            Mapping[bytes, TResult],
            Dict[bytes, TResult],
        ]
    ],
) -> bool:
    """
    Compare two maps by converting them to JSON strings and checking for equality, including property order.

    Args:
        map1 (Optional[Union[Mapping[str, TResult], Dict[str, TResult], Mapping[bytes, TResult], Dict[bytes, TResult]]]):
            The first map to compare.
        map2 (Optional[Union[Mapping[str, TResult], Dict[str, TResult], Mapping[bytes, TResult], Dict[bytes, TResult]]]):
            The second map to compare.

    Returns:
        bool: True if the maps are equal, False otherwise.

    Notes:
        This function compares two maps, including their property order.
        It checks that each key-value pair in `map1` is equal to the corresponding key-value pair in `map2`,
        and ensures that the order of properties is also the same.
        Direct comparison with `assert map1 == map2` might ignore the order of properties.

    Example:
        mapA = {'name': 'John', 'age': 30}
        mapB = {'age': 30, 'name': 'John'}

        # Direct comparison will pass because it ignores property order
        assert mapA == mapB  # This will pass

        # Correct comparison using compare_maps function
        compare_maps(mapA, mapB)  # This will return False due to different property order
    """
    if map1 is None and map2 is None:
        return True
    if map1 is None or map2 is None:
        return False
    return json.dumps(convert_bytes_to_string_object(map1)) == json.dumps(
        convert_bytes_to_string_object(map2)
    )


def round_values(map_data: dict, decimal_places: int) -> dict:
    """Round the values in a map to the specified number of decimal places."""
    return {key: round(value, decimal_places) for key, value in map_data.items()}


def convert_bytes_to_string_object(
    # TODO: remove the str options
    byte_string_dict: Optional[
        Union[
            List[Any],
            Set[bytes],
            Mapping[bytes, Any],
            Dict[bytes, Any],
            Mapping[str, Any],
            Dict[str, Any],
        ]
    ],
) -> Optional[
    Union[
        List[Any],
        Set[str],
        Mapping[str, Any],
        Dict[str, Any],
    ]
]:
    """
    Recursively convert data structure from byte strings to regular strings,
    handling nested data structures of any depth.
    """
    if byte_string_dict is None:
        return None

    def convert(item: Any) -> Any:
        if isinstance(item, dict):
            return {convert(key): convert(value) for key, value in item.items()}
        elif isinstance(item, list):
            return [convert(elem) for elem in item]
        elif isinstance(item, set):
            return {convert(elem) for elem in item}
        elif isinstance(item, bytes):
            return item.decode("utf-8")
        else:
            return item

    return convert(byte_string_dict)


def convert_string_to_bytes_object(
    string_structure: Optional[
        Union[
            List[Any],
            Set[str],
            Mapping[str, Any],
            Dict[str, Any],
        ]
    ],
) -> Optional[
    Union[
        List[Any],
        Set[bytes],
        Mapping[bytes, Any],
        Dict[bytes, Any],
    ]
]:
    """
    Recursively convert the data structure from strings to bytes,
    handling nested data structures of any depth.
    """
    if string_structure is None:
        return None

    def convert(item: Any) -> Any:
        if isinstance(item, dict):
            return {convert(key): convert(value) for key, value in item.items()}
        elif isinstance(item, list):
            return [convert(elem) for elem in item]
        elif isinstance(item, set):
            return {convert(elem) for elem in item}
        elif isinstance(item, str):
            return item.encode("utf-8")
        else:
            return item

    return convert(string_structure)


def generate_lua_lib_code(
    lib_name: str, functions: Mapping[str, str], readonly: bool
) -> str:
    code = f"#!lua name={lib_name}\n"
    for function_name, function_body in functions.items():
        code += (
            f"redis.register_function{{"
            f" function_name = '{function_name}', callback = function(keys, args) "
            f"{function_body} end"
        )
        if readonly:
            code += ", flags = { 'no-writes' }"
        code += " }\n"
    return code


def create_lua_lib_with_long_running_function(
    lib_name: str, func_name: str, timeout: int, readonly: bool
) -> str:
    """
    Create a lua lib with a (optionally) RO function which runs an endless loop up to timeout sec.
    Execution takes at least 5 sec regardless of the timeout configured.
    """
    code = (
        f"#!lua name={lib_name}\n"
        f"local function {lib_name}_{func_name}(keys, args)\n"
        "  local started = tonumber(redis.pcall('time')[1])\n"
        # fun fact - redis does no writes if 'no-writes' flag is set
        "  redis.pcall('set', keys[1], 42)\n"
        "  while (true) do\n"
        "    local now = tonumber(redis.pcall('time')[1])\n"
        # We disable flake8 checks for the next two lines as the extra spaces are helpful
        f"    if now > started + {timeout} then\n"  # noqa: E272
        f"      return 'Timed out {timeout} sec'\n"  # noqa: E272
        "    end\n"
        "  end\n"
        "  return 'OK'\n"
        "end\n"
        "redis.register_function{\n"
        f"function_name='{func_name}', \n"
        f"callback={lib_name}_{func_name}, \n"
    )
    if readonly:
        code += "flags={ 'no-writes' }\n"
    code += "}"
    return code


def create_long_running_lua_script(timeout: int) -> str:
    """
    Create a lua script which runs an endless loop up to timeout sec.
    Execution takes at least 5 sec regardless of the timeout configured.
    """
    script = (
        "  local started = tonumber(redis.pcall('time')[1])\n"
        "  while (true) do\n"
        "    local now = tonumber(redis.pcall('time')[1])\n"
        # We disable flake8 checks for the next two lines as the extra spaces are helpful
        f"    if now > started + {timeout} then\n"  # noqa: E272
        f"      return 'Timed out {timeout} sec'\n"  # noqa: E272
        "    end\n"
        "  end\n"
    )
    return script


def check_function_list_response(
    response: TClusterResponse[TFunctionListResponse],
    lib_name: str,
    function_descriptions: Mapping[str, Optional[bytes]],
    function_flags: Mapping[str, Set[bytes]],
    lib_code: Optional[str] = None,
):
    """
    Validate whether `FUNCTION LIST` response contains required info.

    Args:
        response (List[Mapping[bytes, Any]]): The response from redis.
        libName (bytes): Expected library name.
        functionDescriptions (Mapping[bytes, Optional[bytes]]): Expected function descriptions. Key - function name, value -
             description.
        functionFlags (Mapping[bytes, Set[bytes]]): Expected function flags. Key - function name, value - flags set.
        libCode (Optional[bytes]): Expected library to check if given.
    """
    response = cast(TFunctionListResponse, response)
    assert len(response) > 0
    has_lib = False
    for lib in response:
        has_lib = lib.get(b"library_name") == lib_name.encode()
        if has_lib:
            functions: List[Mapping[bytes, Any]] = cast(
                List[Mapping[bytes, Any]], lib.get(b"functions")
            )
            assert len(functions) == len(function_descriptions)
            for function in functions:
                function_name: bytes = cast(bytes, function.get(b"name"))
                assert function.get(b"description") == function_descriptions.get(
                    function_name.decode("utf-8")
                )
                assert function.get(b"flags") == function_flags.get(
                    function_name.decode("utf-8")
                )

                if lib_code:
                    assert lib.get(b"library_code") == lib_code.encode()
            break

    assert has_lib is True


def check_function_stats_response(
    response: TFunctionStatsSingleNodeResponse,
    running_function: List[bytes],
    lib_count: int,
    function_count: int,
):
    """
    Validate whether `FUNCTION STATS` response contains required info.

    Args:
        response (TFunctionStatsSingleNodeResponse): The response from server.
        running_function (List[bytes]): Command line of running function expected. Empty, if nothing expected.
        lib_count (int): Expected libraries count.
        function_count (int): Expected functions count.
    """
    running_script_info = response.get(b"running_script")
    if running_script_info is None and len(running_function) != 0:
        pytest.fail("No running function info")

    if running_script_info is not None and len(running_function) == 0:
        command = cast(dict, running_script_info).get(b"command")
        pytest.fail("Unexpected running function info: " + " ".join(cast(str, command)))

    if running_script_info is not None:
        command = cast(dict, running_script_info).get(b"command")
        assert running_function == command
        # command line format is:
        # fcall|fcall_ro <function name> <num keys> <key>* <arg>*
        assert running_function[1] == cast(dict, running_script_info).get(b"name")

    expected = {
        b"LUA": {b"libraries_count": lib_count, b"functions_count": function_count}
    }
    assert expected == response.get(b"engines")


def set_new_acl_username_with_password(
    client: TAnyGlideClient, username: str, password: str
):
    """
    Sets a new ACL user with the provided password.
    When passing a sync client, this returns the reuslt of the ACL SETUSER command.
    When passing an async client, this returns a coroutine that should be awaited.
    """
    try:
        if isinstance(client, (GlideClient, SyncGlideClient)):
            return client.custom_command(
                ["ACL", "SETUSER", username, "ON", f">{password}", "~*", "&*", "+@all"]
            )
        elif isinstance(client, (GlideClusterClient, SyncGlideClusterClient)):
            return client.custom_command(
                ["ACL", "SETUSER", username, "ON", f">{password}", "~*", "&*", "+@all"],
                route=AllNodes(),
            )
    except Exception as e:
        raise RuntimeError(f"Failed to set ACL user: {e}")


def delete_acl_username_and_password(client: TAnyGlideClient, username: str):
    """
    Deletes the username and its password from the ACL list.
    Sets a new ACL user with the provided password.
    When passing a sync client, this returns the reuslt of the ACL DELUSER command.
    When passing an async client, this returns a coroutine that should be awaited.
    """
    if isinstance(client, (GlideClient, SyncGlideClient)):
        return client.custom_command(["ACL", "DELUSER", username])

    elif isinstance(client, (GlideClusterClient, SyncGlideClusterClient)):
        return client.custom_command(["ACL", "DELUSER", username], route=AllNodes())


def create_client_config(
    request,
    cluster_mode: bool,
    credentials: Optional[ServerCredentials] = None,
    database_id: int = 0,
    addresses: Optional[List[NodeAddress]] = None,
    client_name: Optional[str] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = 1000,
    connection_timeout: Optional[int] = 1000,
    cluster_mode_pubsub: Optional[
        GlideClusterClientConfiguration.PubSubSubscriptions
    ] = None,
    standalone_mode_pubsub: Optional[
        GlideClientConfiguration.PubSubSubscriptions
    ] = None,
    inflight_requests_limit: Optional[int] = None,
    read_from: ReadFrom = ReadFrom.PRIMARY,
    client_az: Optional[str] = None,
    reconnect_strategy: Optional[BackoffStrategy] = None,
    valkey_cluster: Optional[ValkeyCluster] = None,
    use_tls: Optional[bool] = None,
    tls_insecure: Optional[bool] = None,
    lazy_connect: Optional[bool] = False,
) -> Union[GlideClusterClientConfiguration, GlideClientConfiguration]:
    if use_tls is not None:
        use_tls = use_tls
    else:
        use_tls = request.config.getoption("--tls")
    tls_adv_conf = TlsAdvancedConfiguration(use_insecure_tls=tls_insecure)
    if cluster_mode:
        valkey_cluster = valkey_cluster or pytest.valkey_cluster  # type: ignore
        assert type(valkey_cluster) is ValkeyCluster
        # Note: database_id != 0 is supported in Valkey 9.0+ cluster mode
        k = min(3, len(valkey_cluster.nodes_addr))
        seed_nodes = random.sample(valkey_cluster.nodes_addr, k=k)
        return GlideClusterClientConfiguration(
            addresses=seed_nodes if addresses is None else addresses,
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,  # Add database_id parameter
            client_name=client_name,
            protocol=protocol,
            request_timeout=timeout,
            pubsub_subscriptions=cluster_mode_pubsub,
            inflight_requests_limit=inflight_requests_limit,
            read_from=read_from,
            client_az=client_az,
            advanced_config=AdvancedGlideClusterClientConfiguration(
                connection_timeout, tls_config=tls_adv_conf
            ),
            lazy_connect=lazy_connect,
        )
    else:
        valkey_cluster = valkey_cluster or pytest.standalone_cluster  # type: ignore
        assert type(valkey_cluster) is ValkeyCluster
        return GlideClientConfiguration(
            addresses=(valkey_cluster.nodes_addr if addresses is None else addresses),
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
            request_timeout=timeout,
            pubsub_subscriptions=standalone_mode_pubsub,
            inflight_requests_limit=inflight_requests_limit,
            read_from=read_from,
            client_az=client_az,
            advanced_config=AdvancedGlideClientConfiguration(
                connection_timeout, tls_config=tls_adv_conf
            ),
            reconnect_strategy=reconnect_strategy,
            lazy_connect=lazy_connect,
        )


def create_sync_client_config(
    request,
    cluster_mode: bool,
    credentials: Optional[ServerCredentials] = None,
    database_id: int = 0,
    addresses: Optional[List[NodeAddress]] = None,
    client_name: Optional[str] = None,
    protocol: ProtocolVersion = ProtocolVersion.RESP3,
    timeout: Optional[int] = 1000,
    connection_timeout: Optional[int] = 1000,
    read_from: ReadFrom = ReadFrom.PRIMARY,
    client_az: Optional[str] = None,
    reconnect_strategy: Optional[BackoffStrategy] = None,
    valkey_cluster: Optional[ValkeyCluster] = None,
    use_tls: Optional[bool] = None,
    tls_insecure: Optional[bool] = None,
    lazy_connect: Optional[bool] = False,
) -> Union[SyncGlideClusterClientConfiguration, SyncGlideClientConfiguration]:
    if use_tls is not None:
        use_tls = use_tls
    else:
        use_tls = request.config.getoption("--tls")
    tls_adv_conf = TlsAdvancedConfiguration(use_insecure_tls=tls_insecure)
    if cluster_mode:
        valkey_cluster = valkey_cluster or pytest.valkey_cluster  # type: ignore
        assert type(valkey_cluster) is ValkeyCluster
        # Note: database_id != 0 is supported in Valkey 9.0+ cluster mode
        k = min(3, len(valkey_cluster.nodes_addr))
        seed_nodes = random.sample(valkey_cluster.nodes_addr, k=k)
        return SyncGlideClusterClientConfiguration(
            addresses=seed_nodes if addresses is None else addresses,
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,  # Add database_id parameter
            client_name=client_name,
            protocol=protocol,
            request_timeout=timeout,
            read_from=read_from,
            client_az=client_az,
            advanced_config=AdvancedGlideClusterClientConfiguration(
                connection_timeout, tls_config=tls_adv_conf
            ),
            lazy_connect=lazy_connect,
        )
    else:
        valkey_cluster = valkey_cluster or pytest.standalone_cluster  # type: ignore
        assert type(valkey_cluster) is ValkeyCluster
        return SyncGlideClientConfiguration(
            addresses=(valkey_cluster.nodes_addr if addresses is None else addresses),
            use_tls=use_tls,
            credentials=credentials,
            database_id=database_id,
            client_name=client_name,
            protocol=protocol,
            request_timeout=timeout,
            read_from=read_from,
            client_az=client_az,
            advanced_config=AdvancedGlideClientConfiguration(
                connection_timeout, tls_config=tls_adv_conf
            ),
            reconnect_strategy=reconnect_strategy,
            lazy_connect=lazy_connect,
        )


def run_sync_func_with_timeout_in_thread(
    func: Callable, timeout: float, on_timeout=None
):
    """
    Executes a synchronous function in a separate thread with a timeout.

    If the function does not complete within the given timeout, a TimeoutError is raised,
    and an optional `on_timeout` callback is invoked. Otherwise, the result of `func` is returned.

    Intended primarily for use in tests of blocking synchronous commands where there's
    a need to prevent the test suite from hanging indefinitely.
    """
    executor = ThreadPoolExecutor(max_workers=1)
    try:
        future = executor.submit(func)
        try:
            return future.result(timeout=timeout)
        except Exception:
            if on_timeout:
                on_timeout()
            raise TimeoutError("Function did not return within timeout")
    finally:
        # Shutdown with cancel_futures=True to prevent hanging
        executor.shutdown(wait=False, cancel_futures=True)


def auth_client(client: TAnyGlideClient, password: str, username: str = "default"):
    """
    Authenticates the given TGlideClient server connected.
    When passing a sync client, this returns the result of the AUTH command.
    When passing an async client, this returns a coroutine that should be awaited.
    """
    if isinstance(client, (GlideClient, SyncGlideClient)):
        return client.custom_command(["AUTH", username, password])
    elif isinstance(client, (GlideClusterClient, SyncGlideClusterClient)):
        return client.custom_command(["AUTH", username, password], route=AllNodes())


def config_set_new_password(client: TAnyGlideClient, password):
    """
    Sets a new password for the given TGlideClient server connected.
    This function updates the server to require a new password.
    When passing a sync client, this returns the reuslt of the CONFIG SET command.
    When passing an async client, this returns a coroutine that should be awaited.
    """
    if isinstance(client, (GlideClient, SyncGlideClient)):
        return client.config_set({"requirepass": password})
    elif isinstance(client, (GlideClusterClient, SyncGlideClusterClient)):
        return client.config_set({"requirepass": password}, route=AllNodes())


def kill_connections(client: TAnyGlideClient):
    """
    Kills all connections to the given TGlideClient server connected.
        When passing a sync client, this returns the reuslt of the CLIENT KILL command.
        When passing an async client, this returns a coroutine that should be awaited.
    """
    if isinstance(client, (GlideClient, SyncGlideClient)):
        return client.custom_command(["CLIENT", "KILL", "TYPE", "normal"])
    elif isinstance(client, (GlideClusterClient, SyncGlideClusterClient)):
        return client.custom_command(
            ["CLIENT", "KILL", "TYPE", "normal"], route=AllNodes()
        )


def generate_key(keyslot: Optional[str], is_atomic: bool) -> str:
    """Generate a key with the same slot if keyslot is provided; otherwise, generate a random key."""
    if is_atomic and keyslot:
        return f"{{{keyslot}}}: {get_random_string(10)}"
    return get_random_string(20)


def generate_key_same_slot(keyslot: str) -> str:
    """Generate a key with the same slot as the provided keyslot."""
    if keyslot.startswith("{") and "}" in keyslot:
        # Extract the tag between the first '{' and the first '}'.
        # This handles cases where keyslot already contains hash tags, in order to avoid nested hash tags.
        end_index = keyslot.index("}")
        tag = keyslot[1:end_index]
    else:
        tag = keyslot

    return f"{{{tag}}}: {get_random_string(10)}"


def batch_test(
    batch: Union[Batch, ClusterBatch],
    version: str,
    keyslot: Optional[str] = None,
) -> List[TResult]:
    """
    Test all batch commands.
    For transactions, all keys are generated in the same slot to ensure atomic execution.
    For batchs, only keys used together in commands that require same-slot access
    (e.g.SDIFF, ZADD) will share a slot - other keys can be in different slots.

    When passing the sync client, this function returns a list of result.
    When passing the async client, this function returns a coroutine that should be awaited.
    """
    key = generate_key(keyslot, batch.is_atomic)
    key2 = generate_key_same_slot(key)
    key3 = generate_key_same_slot(key)
    key4 = generate_key_same_slot(key)
    key5 = generate_key(keyslot, batch.is_atomic)
    key6 = generate_key_same_slot(key5)
    key7 = generate_key(keyslot, batch.is_atomic)
    key8 = generate_key(keyslot, batch.is_atomic)
    key9 = generate_key(keyslot, batch.is_atomic)  # list
    key10 = generate_key(keyslot, batch.is_atomic)  # hyper log log
    key11 = generate_key(keyslot, batch.is_atomic)  # streams
    key12 = generate_key(keyslot, batch.is_atomic)  # geo
    key13 = generate_key_same_slot(key8)  # sorted set
    key14 = generate_key_same_slot(key8)  # sorted set
    key15 = generate_key_same_slot(key8)  # sorted set
    key16 = generate_key(keyslot, batch.is_atomic)  # sorted set
    key17 = generate_key(keyslot, batch.is_atomic)  # sort
    key18 = generate_key_same_slot(key17)  # sort
    key19 = generate_key(keyslot, batch.is_atomic)  # bitmap
    key20 = generate_key_same_slot(key19)  # bitmap
    key22 = generate_key(keyslot, batch.is_atomic)  # getex
    key23 = generate_key(keyslot, batch.is_atomic)  # string
    key24 = generate_key_same_slot(key23)  # string
    key25 = generate_key(keyslot, batch.is_atomic)  # list
    key26 = generate_key(keyslot, batch.is_atomic)  # sort
    key27 = generate_key_same_slot(key26)  # sort
    value = datetime.now(timezone.utc).strftime("%m/%d/%Y, %H:%M:%S")
    value_bytes = value.encode()
    value2 = get_random_string(5)
    value2_bytes = value2.encode()
    value3 = get_random_string(5)
    value3_bytes = value3.encode()
    lib_name = f"mylib1C{get_random_string(5)}"
    func_name = f"myfunc1c{get_random_string(5)}"
    code = generate_lua_lib_code(lib_name, {func_name: "return args[1]"}, True)
    args: List[TResult] = []

    if not check_version_lt(version, "7.0.0"):
        batch.function_load(code)
        args.append(lib_name.encode())
        batch.function_load(code, True)
        args.append(lib_name.encode())
        if batch.is_atomic:
            batch.function_list(lib_name)
            args.append(
                [
                    {
                        b"library_name": lib_name.encode(),
                        b"engine": b"LUA",
                        b"functions": [
                            {
                                b"name": func_name.encode(),
                                b"description": None,
                                b"flags": {b"no-writes"},
                            }
                        ],
                    }
                ]
            )
            batch.function_list(lib_name, True)
            args.append(
                [
                    {
                        b"library_name": lib_name.encode(),
                        b"engine": b"LUA",
                        b"functions": [
                            {
                                b"name": func_name.encode(),
                                b"description": None,
                                b"flags": {b"no-writes"},
                            }
                        ],
                        b"library_code": code.encode(),
                    }
                ]
            )
        batch.fcall(func_name, [], arguments=["one", "two"])
        args.append(b"one")
        batch.fcall(func_name, [key], arguments=["one", "two"])
        args.append(b"one")
        batch.fcall_ro(func_name, [], arguments=["one", "two"])
        args.append(b"one")
        batch.fcall_ro(func_name, [key], arguments=["one", "two"])
        args.append(b"one")
        batch.function_delete(lib_name)
        args.append(OK)
        batch.function_flush()
        args.append(OK)
        batch.function_flush(FlushMode.ASYNC)
        args.append(OK)
        batch.function_flush(FlushMode.SYNC)
        args.append(OK)
        if batch.is_atomic:
            batch.function_stats()
            args.append(
                {
                    b"running_script": None,
                    b"engines": {
                        b"LUA": {
                            b"libraries_count": 0,
                            b"functions_count": 0,
                        }
                    },
                }
            )

    # To fix flake8's complexity analysis, we break down the argument appending into 6
    # helper functions.
    helper1(
        batch,
        version,
        key,
        key2,
        value,
        value_bytes,
        value2,
        value2_bytes,
        args,
    )

    helper2(
        batch,
        version,
        key,
        key2,
        key3,
        key4,
        value,
        value_bytes,
        value2,
        value2_bytes,
        args,
    )

    helper3(
        batch,
        version,
        key5,
        key6,
        key7,
        key9,
        value,
        value_bytes,
        value2,
        value2_bytes,
        value3,
        value3_bytes,
        args,
    )

    helper4(batch, version, key8, key10, key13, key14, key15, key19, key20, args)

    helper5(batch, version, key11, key12, key17, key18, key20, args)

    helper6(
        batch,
        keyslot,
        version,
        key,
        key7,
        key16,
        key22,
        key23,
        key24,
        key25,
        key26,
        key27,
        args,
    )

    return args


def helper6(
    transaction,
    keyslot,
    version,
    key,
    key7,
    key16,
    key22,
    key23,
    key24,
    key25,
    key26,
    key27,
    args,
):
    if not check_version_lt(version, "8.0.0"):
        keyslot = keyslot or key26
        transaction.hset(f"{{{keyslot}}}: 1", {"name": "Alice", "age": "30"})
        args.append(2)
        transaction.hset(f"{{{keyslot}}}: 2", {"name": "Bob", "age": "25"})
        args.append(2)
        transaction.lpush(key26, ["2", "1"])
        args.append(2)
        transaction.sort(
            key26,
            by_pattern=f"{{{keyslot}}}: *->age",
            get_patterns=[f"{{{keyslot}}}: *->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        args.append([b"Bob", b"Alice"])
        transaction.sort_store(
            key26,
            key27,
            by_pattern=f"{{{keyslot}}}: *->age",
            get_patterns=[f"{{{keyslot}}}: *->name"],
            order=OrderBy.ASC,
            alpha=True,
        )
        args.append(2)

    transaction.sadd(key7, ["one"])
    args.append(1)
    transaction.srandmember(key7)
    args.append(b"one")
    transaction.srandmember_count(key7, 1)
    args.append([b"one"])
    transaction.flushall(FlushMode.ASYNC)
    args.append(OK)
    transaction.flushall()
    args.append(OK)
    transaction.flushdb(FlushMode.ASYNC)
    args.append(OK)
    transaction.flushdb()
    args.append(OK)
    transaction.set(key, "foo")
    args.append(OK)
    transaction.random_key()
    args.append(key.encode())

    min_version = "6.0.6"
    if not check_version_lt(version, min_version):
        transaction.rpush(key25, ["a", "a", "b", "c", "a", "b"])
        args.append(6)
        transaction.lpos(key25, "a")
        args.append(0)
        transaction.lpos(key25, "a", 1, 0, 0)
        args.append([0, 1, 4])

    min_version = "6.2.0"
    if not check_version_lt(version, min_version):
        transaction.flushall(FlushMode.SYNC)
        args.append(OK)
        transaction.flushdb(FlushMode.SYNC)
        args.append(OK)

    min_version = "6.2.0"
    if not check_version_lt(version, min_version):
        transaction.set(key22, "value")
        args.append(OK)
        transaction.getex(key22)
        args.append(b"value")
        transaction.getex(key22, ExpiryGetEx(ExpiryTypeGetEx.SEC, 1))
        args.append(b"value")

    min_version = "7.0.0"
    if not check_version_lt(version, min_version):
        transaction.zadd(key16, {"a": 1, "b": 2, "c": 3, "d": 4})
        args.append(4)
        transaction.bzmpop([key16], ScoreFilter.MAX, 0.1)
        args.append([key16.encode(), {b"d": 4.0}])
        transaction.bzmpop([key16], ScoreFilter.MIN, 0.1, 2)
        args.append([key16.encode(), {b"a": 1.0, b"b": 2.0}])

        transaction.mset({key23: "abcd1234", key24: "bcdef1234"})
        args.append(OK)
        transaction.lcs(key23, key24)
        args.append(b"bcd1234")
        transaction.lcs_len(key23, key24)
        args.append(7)
        transaction.lcs_idx(key23, key24)
        args.append({b"matches": [[[4, 7], [5, 8]], [[1, 3], [0, 2]]], b"len": 7})
        transaction.lcs_idx(key23, key24, min_match_len=4)
        args.append({b"matches": [[[4, 7], [5, 8]]], b"len": 7})
        transaction.lcs_idx(key23, key24, with_match_len=True)
        args.append({b"matches": [[[4, 7], [5, 8], 4], [[1, 3], [0, 2], 3]], b"len": 7})

    transaction.pubsub_channels(pattern="*")
    args.append([])
    transaction.pubsub_numpat()
    args.append(0)
    transaction.pubsub_numsub()
    args.append({})


def helper5(transaction, version, key11, key12, key17, key18, key20, args):
    if not check_version_lt(version, "7.0.0"):
        transaction.set(key20, "foobar")
        args.append(OK)
        transaction.bitcount(key20, OffsetOptions(5, 30, BitmapIndexType.BIT))
        args.append(17)
        transaction.bitpos(key20, 1, OffsetOptions(44, 50, BitmapIndexType.BIT))
        args.append(46)

    if not check_version_lt(version, "8.0.0"):
        transaction.set(key20, "foobar")
        args.append(OK)
        transaction.bitcount(key20, OffsetOptions(0))
        args.append(26)

    transaction.geoadd(
        key12,
        {
            "Palermo": GeospatialData(13.361389, 38.115556),
            "Catania": GeospatialData(15.087269, 37.502669),
        },
    )
    args.append(2)
    transaction.geodist(key12, "Palermo", "Catania")
    args.append(166274.1516)
    transaction.geohash(key12, ["Palermo", "Catania", "Place"])
    args.append([b"sqc8b49rny0", b"sqdtr74hyu0", None])
    transaction.geopos(key12, ["Palermo", "Catania", "Place"])
    # The comparison allows for a small tolerance level due to potential precision errors in floating-point calculations
    # No worries, Python can handle it, therefore, this shouldn't fail
    args.append(
        [
            [13.36138933897018433, 38.11555639549629859],
            [15.08726745843887329, 37.50266842333162032],
            None,
        ]
    )

    transaction.geosearch(
        key12, "Catania", GeoSearchByRadius(200, GeoUnit.KILOMETERS), OrderBy.ASC
    )
    args.append([b"Catania", b"Palermo"])
    transaction.geosearchstore(
        key12,
        key12,
        GeospatialData(15, 37),
        GeoSearchByBox(400, 400, GeoUnit.KILOMETERS),
        store_dist=True,
    )
    args.append(2)

    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-1"))
    args.append(b"0-1")
    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-2"))
    args.append(b"0-2")
    transaction.xadd(key11, [("foo", "bar")], StreamAddOptions(id="0-3"))
    args.append(b"0-3")
    transaction.xlen(key11)
    args.append(3)
    transaction.xread({key11: "0-2"})
    args.append({key11.encode(): {b"0-3": [[b"foo", b"bar"]]}})
    transaction.xrange(key11, IdBound("0-1"), IdBound("0-1"))
    args.append({b"0-1": [[b"foo", b"bar"]]})
    transaction.xrevrange(key11, IdBound("0-1"), IdBound("0-1"))
    args.append({b"0-1": [[b"foo", b"bar"]]})
    transaction.xtrim(key11, TrimByMinId(threshold="0-2", exact=True))
    args.append(1)
    transaction.xinfo_groups(key11)
    args.append([])

    group_name1 = get_random_string(10)
    group_name2 = get_random_string(10)
    consumer = get_random_string(10)
    transaction.xgroup_create(key11, group_name1, "0-2")
    args.append(OK)
    transaction.xgroup_create(
        key11, group_name2, "0-0", StreamGroupOptions(make_stream=True)
    )
    args.append(OK)
    transaction.xinfo_consumers(key11, group_name1)
    args.append([])
    transaction.xgroup_create_consumer(key11, group_name1, consumer)
    args.append(True)
    transaction.xgroup_set_id(key11, group_name1, "0-2")
    args.append(OK)
    transaction.xreadgroup({key11: ">"}, group_name1, consumer)
    args.append({key11.encode(): {b"0-3": [[b"foo", b"bar"]]}})
    transaction.xreadgroup(
        {key11: "0-3"}, group_name1, consumer, StreamReadGroupOptions(count=2)
    )
    args.append({key11.encode(): {}})
    transaction.xclaim(key11, group_name1, consumer, 0, ["0-1"])
    args.append({})
    transaction.xclaim(
        key11, group_name1, consumer, 0, ["0-3"], StreamClaimOptions(is_force=True)
    )
    args.append({b"0-3": [[b"foo", b"bar"]]})
    transaction.xclaim_just_id(key11, group_name1, consumer, 0, ["0-3"])
    args.append([b"0-3"])
    transaction.xclaim_just_id(
        key11, group_name1, consumer, 0, ["0-4"], StreamClaimOptions(is_force=True)
    )
    args.append([])

    transaction.xpending(key11, group_name1)
    args.append([1, b"0-3", b"0-3", [[consumer.encode(), b"1"]]])

    min_version = "6.2.0"
    if not check_version_lt(version, min_version):
        transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
        transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")
        # if using Valkey 7.0.0 or above, responses also include a list of entry IDs that were removed from the Pending
        # Entries List because they no longer exist in the stream
        if check_version_lt(version, "7.0.0"):
            args.append(
                [b"0-0", {b"0-3": [[b"foo", b"bar"]]}]
            )  # transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
            args.append(
                [b"0-0", [b"0-3"]]
            )  # transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")
        else:
            args.append(
                [b"0-0", {b"0-3": [[b"foo", b"bar"]]}, []]
            )  # transaction.xautoclaim(key11, group_name1, consumer, 0, "0-0")
            args.append(
                [b"0-0", [b"0-3"], []]
            )  # transaction.xautoclaim_just_id(key11, group_name1, consumer, 0, "0-0")

    transaction.xack(key11, group_name1, ["0-3"])
    args.append(1)
    transaction.xpending_range(key11, group_name1, MinId(), MaxId(), 1)
    args.append([])
    transaction.xgroup_del_consumer(key11, group_name1, consumer)
    args.append(0)
    transaction.xgroup_destroy(key11, group_name1)
    args.append(True)
    transaction.xgroup_destroy(key11, group_name2)
    args.append(True)

    transaction.xdel(key11, ["0-3", "0-5"])
    args.append(1)

    transaction.lpush(key17, ["2", "1", "4", "3", "a"])
    args.append(5)
    transaction.sort(
        key17,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append([b"2", b"3", b"4", b"a"])
    if not check_version_lt(version, "7.0.0"):
        transaction.sort_ro(
            key17,
            limit=Limit(1, 4),
            order=OrderBy.ASC,
            alpha=True,
        )
        args.append([b"2", b"3", b"4", b"a"])
    transaction.sort_store(
        key17,
        key18,
        limit=Limit(1, 4),
        order=OrderBy.ASC,
        alpha=True,
    )
    args.append(4)


def helper4(transaction, version, key8, key10, key13, key14, key15, key19, key20, args):
    transaction.zadd(key8, {"one": 1, "two": 2, "three": 3, "four": 4})
    args.append(4.0)
    transaction.zrank(key8, "one")
    args.append(0)
    transaction.zrevrank(key8, "one")
    args.append(3)
    if not check_version_lt(version, "7.2.0"):
        transaction.zrank_withscore(key8, "one")
        args.append([0, 1])
        transaction.zrevrank_withscore(key8, "one")
        args.append([3, 1])
    transaction.zadd_incr(key8, "one", 3)
    args.append(4.0)
    transaction.zincrby(key8, 3, "one")
    args.append(7.0)
    transaction.zrem(key8, ["one"])
    args.append(1)
    transaction.zcard(key8)
    args.append(3)
    transaction.zcount(key8, ScoreBoundary(2, is_inclusive=True), InfBound.POS_INF)
    args.append(3)
    transaction.zlexcount(key8, LexBoundary("a", is_inclusive=True), InfBound.POS_INF)
    args.append(3)
    transaction.zscore(key8, "two")
    args.append(2.0)
    transaction.zrange(key8, RangeByIndex(0, -1))
    args.append([b"two", b"three", b"four"])
    transaction.zrange_withscores(key8, RangeByIndex(0, -1))
    args.append({b"two": 2.0, b"three": 3.0, b"four": 4.0})
    transaction.zmscore(key8, ["two", "three"])
    args.append([2.0, 3.0])
    transaction.zrangestore(key8, key8, RangeByIndex(0, -1))
    args.append(3)
    transaction.bzpopmin([key8], 0.5)
    args.append([key8.encode(), b"two", 2.0])
    transaction.bzpopmax([key8], 0.5)
    args.append([key8.encode(), b"four", 4.0])
    # key8 now only contains one member ("three")
    transaction.zrandmember(key8)
    args.append(b"three")
    transaction.zrandmember_count(key8, 1)
    args.append([b"three"])
    transaction.zrandmember_withscores(key8, 1)
    args.append([[b"three", 3.0]])
    transaction.zscan(key8, "0")
    args.append([b"0", [b"three", b"3"]])
    transaction.zscan(key8, "0", match="*", count=20)
    args.append([b"0", [b"three", b"3"]])
    if not check_version_lt(version, "8.0.0"):
        transaction.zscan(key8, "0", match="*", count=20, no_scores=True)
        args.append([b"0", [b"three"]])
    transaction.zpopmax(key8)
    args.append({b"three": 3.0})
    transaction.zpopmin(key8)
    args.append({})  # type: ignore
    transaction.zremrangebyscore(key8, InfBound.NEG_INF, InfBound.POS_INF)
    args.append(0)
    transaction.zremrangebylex(key8, InfBound.NEG_INF, InfBound.POS_INF)
    args.append(0)
    transaction.zremrangebyrank(key8, 0, 10)
    args.append(0)
    transaction.zdiffstore(key8, [key8, key8])
    args.append(0)
    if not check_version_lt(version, "7.0.0"):
        transaction.zmpop([key8], ScoreFilter.MAX)
        args.append(None)
        transaction.zmpop([key8], ScoreFilter.MAX, 1)
        args.append(None)

    transaction.zadd(key13, {"one": 1.0, "two": 2.0})
    args.append(2)
    transaction.zdiff([key13, key8])
    args.append([b"one", b"two"])
    transaction.zdiff_withscores([key13, key8])
    args.append({b"one": 1.0, b"two": 2.0})
    if not check_version_lt(version, "7.0.0"):
        transaction.zintercard([key13, key8])
        args.append(0)
        transaction.zintercard([key13, key8], 1)
        args.append(0)

    transaction.zadd(key14, {"one": 1, "two": 2})
    args.append(2)
    transaction.zadd(key15, {"one": 1.0, "two": 2.0, "three": 3.5})
    args.append(3)
    transaction.zinter([key14, key15])
    args.append([b"one", b"two"])
    transaction.zinter_withscores(cast(List[Union[str, bytes]], [key14, key15]))
    args.append({b"one": 2.0, b"two": 4.0})
    transaction.zinterstore(key8, cast(List[Union[str, bytes]], [key14, key15]))
    args.append(2)
    transaction.zunion([key14, key15])
    args.append([b"one", b"three", b"two"])
    transaction.zunion_withscores(cast(List[Union[str, bytes]], [key14, key15]))
    args.append({b"one": 2.0, b"two": 4.0, b"three": 3.5})
    transaction.zunionstore(
        key8, cast(List[Union[str, bytes]], [key14, key15]), AggregationType.MAX
    )
    args.append(3)

    transaction.pfadd(key10, ["a", "b", "c"])
    args.append(True)
    transaction.pfmerge(key10, [])
    args.append(OK)
    transaction.pfcount([key10])
    args.append(3)

    transaction.setbit(key19, 1, 1)
    args.append(0)
    transaction.getbit(key19, 1)
    args.append(1)

    transaction.set(key20, "foobar")
    args.append(OK)
    transaction.bitcount(key20)
    args.append(26)
    transaction.bitcount(key20, OffsetOptions(1, 1))
    args.append(6)
    transaction.bitpos(key20, 1)
    args.append(1)

    if not check_version_lt(version, "6.0.0"):
        transaction.bitfield_read_only(
            key20, [BitFieldGet(SignedEncoding(5), BitOffset(3))]
        )
        args.append([6])

    transaction.set(key19, "abcdef")
    args.append(OK)
    transaction.bitop(BitwiseOperation.AND, key19, [key19, key20])
    args.append(6)
    transaction.get(key19)
    args.append(b"`bc`ab")
    transaction.bitfield(
        key20, [BitFieldSet(UnsignedEncoding(10), BitOffsetMultiplier(3), 4)]
    )
    args.append([609])


def helper3(
    transaction,
    version,
    key5,
    key6,
    key7,
    key9,
    value,
    value_bytes,
    value2,
    value2_bytes,
    value3,
    value3_bytes,
    args,
):
    transaction.lpush(key5, [value, value, value2, value2])
    args.append(4)
    transaction.llen(key5)
    args.append(4)
    transaction.lindex(key5, 0)
    args.append(value2_bytes)
    transaction.lpop(key5)
    args.append(value2_bytes)
    transaction.lrem(key5, 1, value)
    args.append(1)
    transaction.ltrim(key5, 0, 1)
    args.append(OK)
    transaction.lrange(key5, 0, -1)
    args.append([value2_bytes, value_bytes])
    transaction.lmove(key5, key6, ListDirection.LEFT, ListDirection.LEFT)
    args.append(value2_bytes)
    transaction.blmove(key6, key5, ListDirection.LEFT, ListDirection.LEFT, 1)
    args.append(value2_bytes)
    transaction.lpop_count(key5, 2)
    args.append([value2_bytes, value_bytes])
    transaction.linsert(key5, InsertPosition.BEFORE, "non_existing_pivot", "element")
    args.append(0)
    if not check_version_lt(version, "7.0.0"):
        transaction.lpush(key5, [value, value2])
        args.append(2)
        transaction.lmpop([key5], ListDirection.LEFT)
        args.append({key5.encode(): [value2_bytes]})
        transaction.blmpop([key5], ListDirection.LEFT, 0.1)
        args.append({key5.encode(): [value_bytes]})

    transaction.rpush(key6, [value, value2, value2])
    args.append(3)
    transaction.rpop(key6)
    args.append(value2_bytes)
    transaction.rpop_count(key6, 2)
    args.append([value2_bytes, value_bytes])

    transaction.rpushx(key9, ["_"])
    args.append(0)
    transaction.lpushx(key9, ["_"])
    args.append(0)
    transaction.lpush(key9, [value, value2, value3])
    args.append(3)
    transaction.blpop([key9], 1)
    args.append([key9.encode(), value3_bytes])
    transaction.brpop([key9], 1)
    args.append([key9.encode(), value_bytes])
    transaction.lset(key9, 0, value2)
    args.append(OK)

    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.smismember(key7, ["foo", "baz"])
    args.append([True, False])
    transaction.sdiffstore(key7, [key7])
    args.append(2)
    transaction.srem(key7, ["foo"])
    args.append(1)
    transaction.sscan(key7, "0")
    args.append([b"0", [b"bar"]])
    transaction.sscan(key7, "0", match="*", count=10)
    args.append([b"0", [b"bar"]])
    transaction.smembers(key7)
    args.append({b"bar"})
    transaction.scard(key7)
    args.append(1)
    transaction.sismember(key7, "bar")
    args.append(True)
    transaction.spop(key7)
    args.append(b"bar")
    transaction.sadd(key7, ["foo", "bar"])
    args.append(2)
    transaction.sunionstore(key7, [key7, key7])
    args.append(2)
    transaction.sinter([key7, key7])
    args.append({b"foo", b"bar"})
    transaction.sunion([key7, key7])
    args.append({b"foo", b"bar"})
    transaction.sinterstore(key7, [key7, key7])
    args.append(2)
    if not check_version_lt(version, "7.0.0"):
        transaction.sintercard([key7, key7])
        args.append(2)
        transaction.sintercard([key7, key7], 1)
        args.append(1)
    transaction.sdiff([key7, key7])
    args.append(set())
    transaction.spop_count(key7, 4)
    args.append({b"foo", b"bar"})
    transaction.smove(key7, key7, "non_existing_member")
    args.append(False)


def helper2(
    transaction,
    version,
    key,
    key2,
    key3,
    key4,
    value,
    value_bytes,
    value2,
    value2_bytes,
    args,
):
    transaction.incr(key3)
    args.append(1)
    transaction.incrby(key3, 2)
    args.append(3)

    transaction.decr(key3)
    args.append(2)
    transaction.decrby(key3, 2)
    args.append(0)

    transaction.incrbyfloat(key3, 0.5)
    args.append(0.5)

    transaction.unlink([key3])
    args.append(1)

    transaction.ping()
    args.append(b"PONG")

    transaction.config_set({"timeout": "1000"})
    args.append(OK)
    transaction.config_get(["timeout"])
    args.append({b"timeout": b"1000"})
    if not check_version_lt(version, "7.0.0"):
        transaction.config_set({"timeout": "2000", "cluster-node-timeout": "16000"})
        args.append(OK)
        transaction.config_get(["timeout", "cluster-node-timeout"])
        args.append({b"timeout": b"2000", b"cluster-node-timeout": b"16000"})

    transaction.hset(key4, {key: value, key2: value2})
    args.append(2)
    transaction.hget(key4, key2)
    args.append(value2_bytes)
    transaction.hlen(key4)
    args.append(2)
    transaction.hvals(key4)
    args.append([value_bytes, value2_bytes])
    transaction.hkeys(key4)
    args.append([key.encode(), key2.encode()])
    transaction.hsetnx(key4, key, value)
    args.append(False)
    transaction.hincrby(key4, key3, 5)
    args.append(5)
    transaction.hincrbyfloat(key4, key3, 5.5)
    args.append(10.5)

    transaction.hexists(key4, key)
    args.append(True)
    transaction.hmget(key4, [key, "nonExistingField", key2])
    args.append([value_bytes, None, value2_bytes])
    transaction.hgetall(key4)
    key3_bytes = key3.encode()
    args.append(
        {
            key.encode(): value_bytes,
            key2.encode(): value2_bytes,
            key3_bytes: b"10.5",
        }
    )
    transaction.hdel(key4, [key, key2])
    args.append(2)
    transaction.hscan(key4, "0")
    args.append([b"0", [key3.encode(), b"10.5"]])
    transaction.hscan(key4, "0", match="*", count=10)
    args.append([b"0", [key3.encode(), b"10.5"]])
    if not check_version_lt(version, "8.0.0"):
        transaction.hscan(key4, "0", match="*", count=10, no_values=True)
        args.append([b"0", [key3.encode()]])
    transaction.hrandfield(key4)
    args.append(key3_bytes)
    transaction.hrandfield_count(key4, 1)
    args.append([key3_bytes])
    transaction.hrandfield_withvalues(key4, 1)
    args.append([[key3_bytes, b"10.5"]])
    transaction.hstrlen(key4, key3)
    args.append(4)

    transaction.client_getname()
    args.append(None)


def helper1(
    transaction, version, key, key2, value, value_bytes, value2, value2_bytes, args
):
    transaction.dbsize()
    args.append(0)

    transaction.set(key, value)
    args.append(OK)
    transaction.setrange(key, 0, value)
    args.append(len(value))
    transaction.get(key)
    args.append(value_bytes)
    transaction.get(key.encode())
    args.append(value_bytes)
    transaction.type(key)
    args.append(b"string")
    transaction.type(key.encode())
    args.append(b"string")
    transaction.echo(value)
    args.append(value_bytes)
    transaction.echo(value.encode())
    args.append(value_bytes)
    transaction.strlen(key)
    args.append(len(value))
    transaction.strlen(key.encode())
    args.append(len(value))
    transaction.append(key, value)
    args.append(len(value) * 2)

    transaction.persist(key)
    args.append(False)
    transaction.ttl(key)
    args.append(-1)
    if not check_version_lt(version, "7.0.0"):
        transaction.expiretime(key)
        args.append(-1)
        transaction.pexpiretime(key)
        args.append(-1)

    if not check_version_lt(version, "6.2.0"):
        transaction.copy(key, key2, replace=True)
        args.append(True)

    transaction.rename(key, key2)
    args.append(OK)

    transaction.exists([key2])
    args.append(1)
    transaction.touch([key2])
    args.append(1)

    transaction.delete([key2])
    args.append(1)
    transaction.get(key2)
    args.append(None)

    transaction.set(key, value)
    args.append(OK)
    transaction.getrange(key, 0, -1)
    args.append(value_bytes)
    transaction.getdel(key)
    args.append(value_bytes)
    transaction.getdel(key)
    args.append(None)

    transaction.mset({key: value, key2: value2})
    args.append(OK)
    transaction.msetnx({key: value, key2: value2})
    args.append(False)
    transaction.mget([key, key2])
    args.append([value_bytes, value2_bytes])

    transaction.renamenx(key, key2)
    args.append(False)
