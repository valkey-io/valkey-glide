# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Glide module for `JSON` commands.

    Examples:

        >>> from glide import json
        >>> import json as jsonpy
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = jsonpy.dumps(value) # Convert Python dictionary to JSON string using json.dumps()
        >>> await json.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> json_get = await json.get(client, "doc", "$") # Returns the value at path '$' in the JSON document stored at `doc` as JSON string.
        >>> print(json_get)
            b"[{\"a\":1.0,\"b\":2}]" 
        >>> jsonpy.loads(str(json_get))
            [{"a": 1.0, "b" :2}] # JSON object retrieved from the key `doc` using json.loads()
        """
from typing import List, Optional, Union, cast

from glide.async_commands.core import ConditionalChange
from glide.constants import TOK, TEncodable, TJsonResponse
from glide.glide_client import TGlideClient
from glide.protobuf.command_request_pb2 import RequestType


class JsonGetOptions:
    """
    Represents options for formatting JSON data, to be used in  the [JSON.GET](https://valkey.io/commands/json.get/) command.

    Args:
        indent (Optional[str]): Sets an indentation string for nested levels. Defaults to None.
        newline (Optional[str]): Sets a string that's printed at the end of each line. Defaults to None.
        space (Optional[str]): Sets a string that's put between a key and a value. Defaults to None.
    """

    def __init__(
        self,
        indent: Optional[str] = None,
        newline: Optional[str] = None,
        space: Optional[str] = None,
    ):
        self.indent = indent
        self.new_line = newline
        self.space = space

    def get_options(self) -> List[str]:
        args = []
        if self.indent:
            args.extend(["INDENT", self.indent])
        if self.new_line:
            args.extend(["NEWLINE", self.new_line])
        if self.space:
            args.extend(["SPACE", self.space])
        return args


async def set(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    value: TEncodable,
    set_condition: Optional[ConditionalChange] = None,
) -> Optional[TOK]:
    """
    Sets the JSON value at the specified `path` stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): Represents the path within the JSON document where the value will be set.
            The key will be modified only if `value` is added as the last child in the specified `path`, or if the specified `path` acts as the parent of a new child being added.
        value (TEncodable): The value to set at the specific path, in JSON formatted bytes or str.
        set_condition (Optional[ConditionalChange]): Set the value only if the given condition is met (within the key or path).
            Equivalent to [`XX` | `NX`] in the RESP API. Defaults to None.

    Returns:
        Optional[TOK]: If the value is successfully set, returns OK.
            If `value` isn't set because of `set_condition`, returns None.

    Examples:
        >>> from glide import json
        >>> import json as jsonpy
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = jsonpy.dumps(value)
        >>> await json.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
    """
    args = ["JSON.SET", key, path, value]
    if set_condition:
        args.append(set_condition.value)

    return cast(Optional[TOK], await client.custom_command(args))


async def get(
    client: TGlideClient,
    key: TEncodable,
    paths: Optional[Union[TEncodable, List[TEncodable]]] = None,
    options: Optional[JsonGetOptions] = None,
) -> TJsonResponse[Optional[bytes]]:
    """
    Retrieves the JSON value at the specified `paths` stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        paths (Optional[Union[TEncodable, List[TEncodable]]]): The path or list of paths within the JSON document. Default to None.
        options (Optional[JsonGetOptions]): Options for formatting the byte representation of the JSON data. See `JsonGetOptions`.

    Returns:
        TJsonResponse[Optional[bytes]]:
            If one path is given:
                For JSONPath (path starts with `$`):
                    Returns a stringified JSON list of bytes replies for every possible path,
                    or a byte string representation of an empty array, if path doesn't exists.
                    If `key` doesn't exist, returns None.
                For legacy path (path doesn't start with `$`):
                    Returns a byte string representation of the value in `path`.
                    If `path` doesn't exist, an error is raised.
                    If `key` doesn't exist, returns None.
            If multiple paths are given:
                Returns a stringified JSON object in bytes, in which each path is a key, and it's corresponding value, is the value as if the path was executed in the command as a single path.
        In case of multiple paths, and `paths` are a mix of both JSONPath and legacy path, the command behaves as if all are JSONPath paths.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import json, JsonGetOptions
        >>> import  as jsonpy
        >>> json_str = await json.get(client, "doc", "$")
        >>> jsonpy.loads(str(json_str)) # Parse JSON string to Python data
            [{"a": 1.0, "b" :2}]  # JSON object retrieved from the key `doc` using json.loads()
        >>> await json.get(client, "doc", "$")
            b"[{\"a\":1.0,\"b\":2}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> await json.get(client, "doc", ["$.a", "$.b"], JsonGetOptions(indent="  ", newline="\n", space=" "))
            b"{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}"  # Returns the values at paths '$.a' and '$.b' in the JSON document stored at `doc`, with specified formatting options.
        >>> await json.get(client, "doc", "$.non_existing_path")
            b"[]"  # Returns an empty array since the path '$.non_existing_path' does not exist in the JSON document stored at `doc`.
    """
    args = ["JSON.GET", key]
    if options:
        args.extend(options.get_options())
    if paths:
        if isinstance(paths, (str, bytes)):
            paths = [paths]
        args.extend(paths)

    return cast(TJsonResponse[Optional[bytes]], await client.custom_command(args))


async def arrlen(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[TJsonResponse[int]]:
    """
    Retrieves the length of the array at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Defaults to None.

    Returns:
        Optional[TJsonResponse[int]]:
            For JSONPath (`path` starts with `$`):
                Returns a list of integer replies for every possible path, indicating the length of the array,
                or None for JSON values matching the path that are not an array.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't starts with `$`):
                Returns the length of the array at `path`.
                If multiple paths match, the length of the first array match is returned.
                If the JSON value at `path` is not a array or if `path` doesn't exist, an error is raised.
            If `key` doesn't exist, None is returned.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"a": [1, 2, 3], "b": {"a": [1, 2], "c": {"a": 42}}}')
            'OK'  # JSON is successfully set for doc
        >>> await json.arrlen(client, "doc", "$")
            [None]  # No array at the root path.
        >>> await json.arrlen(client, "doc", "$.a")
            [3]  # Retrieves the length of the array at path $.a.
        >>> await json.arrlen(client, "doc", "$..a")
            [3, 2, None]  # Retrieves lengths of arrays found at all levels of the path `..a`.
        >>> await json.arrlen(client, "doc", "..a")
            3  # Legacy path retrieves the first array match at path `..a`.
        >>> await json.arrlen(client, "non_existing_key", "$.a")
            None  # Returns None because the key does not exist.

        >>> await json.set(client, "doc", "$", '[1, 2, 3, 4]')
            'OK'  # JSON is successfully set for doc
        >>> await json.arrlen(client, "doc")
            4  # Retrieves lengths of arrays in root.
    """
    args = ["JSON.ARRLEN", key]
    if path:
        args.append(path)
    return cast(
        Optional[TJsonResponse[int]],
        await client.custom_command(args),
    )


async def clear(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[str] = None,
) -> int:
    """
    Clears arrays or objects at the specified JSON path in the document stored at `key`.
    Numeric values are set to `0`, and boolean values are set to `False`, and string values are converted to empty strings.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[str]): The path within the JSON document. Default to None.

    Returns:
        int: The number of containers cleared, numeric values zeroed, and booleans toggled to `false`,
        and string values converted to empty strings.
        If `path` doesn't exist, or the value at `path` is already empty (e.g., an empty array, object, or string), 0 is returned.
        If `key doesn't exist, an error is raised.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"obj":{"a":1, "b":2}, "arr":[1,2,3], "str": "foo", "bool": true, "int": 42, "float": 3.14, "nullVal": null}')
            'OK'  # JSON document is successfully set.
        >>> await json.clear(client, "doc", "$.*")
            6      # 6 values are cleared (arrays/objects/strings/numbers/booleans), but `null` remains as is.
        >>> await json.get(client, "doc", "$")
            b'[{"obj":{},"arr":[],"str":"","bool":false,"int":0,"float":0.0,"nullVal":null}]'
        >>> await json.clear(client, "doc", "$.*")
            0  # No further clearing needed since the containers are already empty and the values are defaults.

        >>> await json.set(client, "doc", "$", '{"a": 1, "b": {"a": [5, 6, 7], "b": {"a": true}}, "c": {"a": "value", "b": {"a": 3.5}}, "d": {"a": {"foo": "foo"}}, "nullVal": null}')
            'OK'
        >>> await json.clear(client, "doc", "b.a[1:3]")
            2  # 2 elements (`6` and `7`) are cleared.
        >>> await json.clear(client, "doc", "b.a[1:3]")
            0 # No elements cleared since specified slice has already been cleared.
        >>> await json.get(client, "doc", "$..a")
            b'[1,[5,0,0],true,"value",3.5,{"foo":"foo"}]'

        >>> await json.clear(client, "doc", "$..a")
            6  # All numeric, boolean, and string values across paths are cleared.
        >>> await json.get(client, "doc", "$..a")
            b'[0,[],false,"",0.0,{}]'
    """
    args = ["JSON.CLEAR", key]
    if path:
        args.append(path)

    return cast(int, await client.custom_command(args))


async def delete(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> int:
    """
    Deletes the JSON value at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document.
            If None, deletes the entire JSON document at `key`. Defaults to None.

    Returns:
        int: The number of elements removed.
        If `key` or `path` doesn't exist, returns 0.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> await json.delete(client, "doc", "$..a")
            2  # Indicates successful deletion of the specific values in the key stored at `doc`.
        >>> await json.get(client, "doc", "$")
            "[{\"nested\":{\"b\":3}}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> await json.delete(client, "doc")
            1  # Deletes the entire JSON document stored at `doc`.
    """

    return cast(
        int, await client.custom_command(["JSON.DEL", key] + ([path] if path else []))
    )


async def forget(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[int]:
    """
    Deletes the JSON value at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document.
            If None, deletes the entire JSON document at `key`. Defaults to None.

    Returns:
        int: The number of elements removed.
        If `key` or `path` doesn't exist, returns 0.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> await json.forget(client, "doc", "$..a")
            2  # Indicates successful deletion of the specific values in the key stored at `doc`.
        >>> await json.get(client, "doc", "$")
            "[{\"nested\":{\"b\":3}}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> await json.forget(client, "doc")
            1  # Deletes the entire JSON document stored at `doc`.
    """

    return cast(
        Optional[int],
        await client.custom_command(["JSON.FORGET", key] + ([path] if path else [])),
    )


async def numincrby(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    number: Union[int, float],
) -> Optional[bytes]:
    """
    Increments or decrements the JSON value(s) at the specified `path` by `number` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        number (Union[int, float]): The number to increment or decrement by.

    Returns:
        Optional[bytes]:
            For JSONPath (`path` starts with `$`):
                Returns a bytes string representation of an array of bulk strings, indicating the new values after incrementing for each matched `path`.
                If a value is not a number, its corresponding return value will be `null`.
                If `path` doesn't exist, a byte string representation of an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns a bytes string representation of the resulting value after the increment or decrement.
                If multiple paths match, the result of the last updated value is returned.
                If the value at the `path` is not a number or `path` doesn't exist, an error is raised.
            If `key` does not exist, an error is raised.
            If the result is out of the range of 64-bit IEEE double, an error is raised.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"a": [], "b": [1], "c": [1, 2], "d": [1, 2, 3]}')
            'OK'
        >>> await json.numincrby(client, "doc", "$.d[*]", 10)›
            b'[11,12,13]'  # Increment each element in `d` array by 10.
        >>> await json.numincrby(client, "doc", ".c[1]", 10)
            b'12'  # Increment the second element in the `c` array by 10.
    """
    args = ["JSON.NUMINCRBY", key, path, str(number)]

    return cast(Optional[bytes], await client.custom_command(args))


async def nummultby(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    number: Union[int, float],
) -> Optional[bytes]:
    """
    Multiplies the JSON value(s) at the specified `path` by `number` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        number (Union[int, float]): The number to multiply by.

    Returns:
        Optional[bytes]:
            For JSONPath (`path` starts with `$`):
                Returns a bytes string representation of an array of bulk strings, indicating the new values after multiplication for each matched `path`.
                If a value is not a number, its corresponding return value will be `null`.
                If `path` doesn't exist, a byte string representation of an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns a bytes string representation of the resulting value after multiplication.
                If multiple paths match, the result of the last updated value is returned.
                If the value at the `path` is not a number or `path` doesn't exist, an error is raised.
            If `key` does not exist, an error is raised.
            If the result is out of the range of 64-bit IEEE double, an error is raised.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"a": [], "b": [1], "c": [1, 2], "d": [1, 2, 3]}')
            'OK'
        >>> await json.nummultby(client, "doc", "$.d[*]", 2)
            b'[2,4,6]'  # Multiplies each element in the `d` array by 2.
        >>> await json.nummultby(client, "doc", ".c[1]", 2)
            b'4'  # Multiplies the second element in the `c` array by 2.
    """
    args = ["JSON.NUMMULTBY", key, path, str(number)]

    return cast(Optional[bytes], await client.custom_command(args))


async def strappend(
    client: TGlideClient,
    key: TEncodable,
    value: TEncodable,
    path: Optional[TEncodable] = None,
) -> TJsonResponse[int]:
    """
    Appends the specified `value` to the string stored at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        value (TEncodable): The value to append to the string. Must be wrapped with single quotes. For example, to append "foo", pass '"foo"'.
        path (Optional[TEncodable]): The path within the JSON document. Default to None.

    Returns:
        TJsonResponse[int]:
            For JSONPath (`path` starts with `$`):
                Returns a list of integer replies for every possible path, indicating the length of the resulting string after appending `value`,
                or None for JSON values matching the path that are not string.
                If `key` doesn't exist, an error is raised.
            For legacy path (`path` doesn't start with `$`):
                Returns the length of the resulting string after appending `value` to the string at `path`.
                If multiple paths match, the length of the last updated string is returned.
                If the JSON value at `path` is not a string of if `path` doesn't exist, an error is raised.
                If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import json
        >>> import json as jsonpy
        >>> await json.set(client, "doc", "$", jsonpy.dumps({"a":"foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}))
            'OK'
        >>> await json.strappend(client, "doc", jsonpy.dumps("baz"), "$..a")
            [6, 8, None]  # The new length of the string values at path '$..a' in the key stored at `doc` after the append operation.
        >>> await json.strappend(client, "doc", '"foo"', "nested.a")
            11  # The length of the string value after appending "foo" to the string at path 'nested.array' in the key stored at `doc`.
        >>> jsonpy.loads(await json.get(client, jsonpy.dumps("doc"), "$"))
            [{"a":"foobaz", "nested": {"a": "hellobazfoo"}, "nested2": {"a": 31}}] # The updated JSON value in the key stored at `doc`.
    """

    return cast(
        TJsonResponse[int],
        await client.custom_command(
            ["JSON.STRAPPEND", key] + ([path, value] if path else [value])
        ),
    )


async def strlen(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> TJsonResponse[Optional[int]]:
    """
    Returns the length of the JSON string value stored at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Default to None.

    Returns:
        TJsonResponse[Optional[int]]:
            For JSONPath (`path` starts with `$`):
                Returns a list of integer replies for every possible path, indicating the length of the JSON string value,
                or None for JSON values matching the path that are not string.
            For legacy path (`path` doesn't start with `$`):
                Returns the length of the JSON value at `path` or None if `key` doesn't exist.
                If multiple paths match, the length of the first mached string is returned.
                If the JSON value at `path` is not a string of if `path` doesn't exist, an error is raised.
            If `key` doesn't exist, None is returned.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import json
        >>> import jsonpy
        >>> await json.set(client, "doc", "$", jsonpy.dumps({"a":"foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}))
            'OK'
        >>> await json.strlen(client, "doc", "$..a")
            [3, 5, None]  # The length of the string values at path '$..a' in the key stored at `doc`.
        >>> await json.strlen(client, "doc", "nested.a")
            5  # The length of the JSON value at path 'nested.a' in the key stored at `doc`.
        >>> await json.strlen(client, "doc", "$")
            [None]  # Returns an array with None since the value at root path does in the JSON document stored at `doc` is not a string.
        >>> await json.strlen(client, "non_existing_key", ".")
            None  # `key` doesn't exist.
    """

    return cast(
        TJsonResponse[Optional[int]],
        await client.custom_command(
            ["JSON.STRLEN", key, path] if path else ["JSON.STRLEN", key]
        ),
    )


async def toggle(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
) -> TJsonResponse[bool]:
    """
    Toggles a Boolean value stored at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document. Default to None.

    Returns:
        TJsonResponse[bool]:
            For JSONPath (`path` starts with `$`):
                Returns a list of boolean replies for every possible path, with the toggled boolean value,
                or None for JSON values matching the path that are not boolean.
                If `key` doesn't exist, an error is raised.
            For legacy path (`path` doesn't start with `$`):
                Returns the value of the toggled boolean in `path`.
                If the JSON value at `path` is not a boolean of if `path` doesn't exist, an error is raised.
                If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import json
        >>> import json as jsonpy
        >>> await json.set(client, "doc", "$", jsonpy.dumps({"bool": True, "nested": {"bool": False, "nested": {"bool": 10}}}))
            'OK'
        >>> await json.toggle(client, "doc", "$.bool")
            [False, True, None]  # Indicates successful toggling of the Boolean values at path '$.bool' in the key stored at `doc`.
        >>> await json.toggle(client, "doc", "bool")
            True  # Indicates successful toggling of the Boolean value at path 'bool' in the key stored at `doc`.
        >>> jsonpy.loads(await json.get(client, "doc", "$"))
            [{"bool": True, "nested": {"bool": True, "nested": {"bool": 10}}}] # The updated JSON value in the key stored at `doc`.
    """

    return cast(
        TJsonResponse[bool],
        await client.custom_command(["JSON.TOGGLE", key, path]),
    )


async def type(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[Union[bytes, List[bytes]]]:
    """
    Retrieves the type of the JSON value at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Default to None.

    Returns:
        Optional[Union[bytes, List[bytes]]]:
            For JSONPath ('path' starts with '$'):
                Returns a list of byte string replies for every possible path, indicating the type of the JSON value.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't starts with `$`):
                Returns the type of the JSON value at `path`.
                If multiple paths match, the type of the first JSON value match is returned.
                If `path` doesn't exist, None will be returned.
            If `key` doesn't exist, None is returned.

    Examples:
        >>> from glide import json
        >>> await json.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'
        >>> await json.type(client, "doc", "$.nested")
            [b'object']  # Indicates the type of the value at path '$.nested' in the key stored at `doc`.
        >>> await json.type(client, "doc", "$.nested.a")
            [b'integer']  # Indicates the type of the value at path '$.nested.a' in the key stored at `doc`.
        >>> await json.type(client, "doc", "$[*]")
            [b'integer',  b'object']  # Array of types in all top level elements.
    """
    args = ["JSON.TYPE", key]
    if path:
        args.append(path)

    return cast(Optional[Union[bytes, List[bytes]]], await client.custom_command(args))
