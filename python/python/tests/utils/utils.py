import random
import string
from typing import Any, Dict, List, Mapping, TypeVar, Union

from glide.async_commands.core import InfoSection
from glide.constants import TResult
from glide.redis_client import TRedisClient
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
    res: Union[str, List[str], List[List[str]], Dict[str, str]]
) -> str:
    while isinstance(res, list):
        res = (
            res[1]
            if not isinstance(res[0], list) and res[0].startswith("127.0.0.1")
            else res[0]
        )

    if isinstance(res, dict):
        res = list(res.values())[0]

    return res


def parse_info_response(res: Union[str, Dict[str, str]]) -> Dict[str, str]:
    res = get_first_result(res)
    info_lines = [
        line for line in res.splitlines() if line and not line.startswith("#")
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


async def check_if_server_version_lt(client: TRedisClient, min_version: str) -> bool:
    # TODO: change it to pytest fixture after we'll implement a sync client
    info_str = await client.info([InfoSection.SERVER])
    redis_version = parse_info_response(info_str).get("redis_version")
    assert redis_version is not None
    return version.parse(redis_version) < version.parse(min_version)


def compare_maps(
    map: TResult,
    map2: TResult,
) -> bool:
    """
    Recursively compares two maps including their property order.

    Args:
        map1 (TResult): The first map to compare.
        map2 (TResult): The second map to compare.

        Returns:
            bool: True if the maps are equal, False otherwise.

    Notes:
        This function recursively compares two maps, including their property order.
        It asserts that each key-value pair in `map1` is equal to the corresponding key-value pair in `map2`,
        and ensures that the order of properties is also the same.
        If a value associated with a key in either map is another dictionary or a mapping object,
        the function recursively compares the nested maps.

    Example:
        mapA = {'name': 'John', 'age': 30}
        mapB = {'age': 30, 'name': 'John'}

        # Direct comparison will pass because it ignores property order
        assert mapA == mapB  # This will pass

        # Correct comparison using compare_maps function
        compare_maps(mapA, mapB)  # This will return False due to different property order
    """
    if isinstance(map, (dict, Mapping)) and isinstance(map2, (dict, Mapping)):
        for item, item2 in zip(map.items(), map2.items()):
            if isinstance(item[1], (dict, Mapping)):
                if item[0] != item2[0] or not compare_maps(item[1], item2[1]):
                    return False
            elif item != item2:
                return False
        return True
    return item == item2
