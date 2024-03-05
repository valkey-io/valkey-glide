# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from typing import List, Optional, Union, cast

from glide.async_commands.core import ConditionalChange
from glide.constants import TOK
from glide.protobuf.redis_request_pb2 import RequestType
from glide.redis_client import TRedisClient


async def set(
    client: TRedisClient,
    key: str,
    path: str,
    value: str,
    set_condition: Optional[ConditionalChange] = None,
) -> Optional[TOK]:
    """
    Sets the JSON value at the specified `path` stored at `key`.

    See https://redis.io/commands/json.set/ for more details.

    Args:
        client (TRedisClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        path (str): Represents the path within the JSON document where the value will be set.
            The key will be modified only if `value` is added as the last child in the specified `path`, or if the specified `path` acts as the parent of a new child being added.
        value (set): The value to set at the specific path, in JSON formatted str.
        set_condition (Optional[ConditionalChange]): Set the value only if the given condition is met (within the key or path).
            Equivalent to [`XX` | `NX`] in the Redis API. Defaults to None.

    Returns:
        Optional[TOK]: If the value is successfully set, return OK.
            If value isn't set because of `set_condition`, return None.

    Examples:
        >>> from glide.async_commands.json import json
        >>> import json as OuterJson
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = OuterJson.dumps(value)
        >>> await json.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at 'doc'.
    """
    args = [key, path, value]
    if set_condition:
        args.append(set_condition.value)

    return cast(Optional[TOK], await client._execute_command(RequestType.JsonSet, args))


async def get(
    client: TRedisClient,
    key: str,
    paths: Optional[Union[str, List[str]]] = None,
    indent: Optional[str] = None,
    newline: Optional[str] = None,
    space: Optional[str] = None,
) -> Optional[str]:
    """
    Retrieves the JSON value at the specified `paths` stored at `key`.

    See https://redis.io/commands/json.get/ for more details.

    Args:
        client (TRedisClient): The Redis client to execute the command.
        key (str): The key of the JSON document.
        paths (ptional[Union[str, List[str]]]): The path or list of paths within the JSON document.
        indent (Optional[str]): Sets an indentation string for nested levels. Defaults to None.
        newline (Optional[str]): Sets a string that's printed at the end of each line. Defaults to None.
        space (Optional[str]): Sets a string that's put between a key and a value. Defaults to None.

    Returns:
        str: A bulk string representation of the returned value.
            If `key` doesn't exists, return None.

    Examples:
        >>> import json as OuterJson
        >>> import json
        >>> OuterJson.loads(await json_get(client, "doc", "$"))
            [{"a": 1.0, "b" :2}]  # JSON object retrieved from the key "doc" using json.loads()
        >>> await json.get(client, "doc", "$")
            "[{\"a\":1.0,\"b\":2}]"  # Returns the value at path '$' in the JSON document stored at 'doc'.
        >>> await json.get(client, "doc", ["$.a", "$.b"], indent="  ", newline="\n", space=" ")
            "{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}"  # Returns the values at paths '$.a' and '$.b' in the JSON document stored at 'doc', with specified formatting options.
        >>> await json.get(client, "doc", "$.non_existing_path")
            "[]"  # Returns an empty array since the path '$.non_existing_path' does not exist in the JSON document stored at 'doc'.
    """
    args = [key]

    if indent:
        args.extend(["INDENT", indent])
    if newline:
        args.extend(["NEWLINE", newline])
    if space:
        args.extend(["SPACE", space])

    if paths:
        if isinstance(paths, str):
            paths = [paths]

        for path in paths:
            args.append(path)

    return cast(str, await client._execute_command(RequestType.JsonGet, args))
