import json
import random
import string
from typing import Any, Dict, List, Mapping, Optional, Set, TypeVar, Union, cast

import pytest
from glide.async_commands.core import InfoSection
from glide.constants import (
    TClusterResponse,
    TFunctionListResponse,
    TFunctionStatsSingleNodeResponse,
    TResult,
)
from glide.glide_client import TGlideClient
from packaging import version

T = TypeVar("T")


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
    # TODO: change to pytest fixture after sync client is implemented
    info = parse_info_response(await client.info([InfoSection.SERVER]))
    version_str = info.get("valkey_version") or info.get("redis_version")
    assert version_str is not None, "Server version not found in INFO response"
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
        map1 (Optional[Union[Mapping[str, TResult], Dict[str, TResult], Mapping[bytes, TResult], Dict[bytes, TResult]]]): The first map to compare.
        map2 (Optional[Union[Mapping[str, TResult], Dict[str, TResult], Mapping[bytes, TResult], Dict[bytes, TResult]]]): The second map to compare.

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
    ]
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
    ]
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
            f"redis.register_function{{ function_name = '{function_name}', callback = function(keys, args) "
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
        f"    if now > started + {timeout} then\n"
        f"      return 'Timed out {timeout} sec'\n"
        "    end\n"
        "  end\n"
        "  return 'OK'\n"
        "end\n"
        "redis.register_function{\n"
        f"function_name='{func_name}',\n"
        f"callback={lib_name}_{func_name},\n"
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
        f"    if now > started + {timeout} then\n"
        f"      return 'Timed out {timeout} sec'\n"
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
