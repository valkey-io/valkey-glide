# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Glide module for `JSON` commands.

Examples:

    >>> from glide import glide_json
    >>> import json
    >>> value = {'a': 1.0, 'b': 2}
    >>> json_str = glide_json.dumps(value) # Convert Python dictionary to JSON string using json.dumps()
    >>> glide_json.set(client, "doc", "$", json_str)
        'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
    >>> json_get = glide_json.get(client, "doc", "$") # Returns the value at path '$' in the JSON document stored at
                                                            # `doc` as JSON string.
    >>> print(json_get)
        b"[{\"a\":1.0,\"b\":2}]"
    >>> json.loads(str(json_get))
        [{"a": 1.0, "b" :2}] # JSON object retrieved from the key `doc` using json.loads()

"""
from typing import List, Optional, Union, cast

from glide_shared.commands.core_options import ConditionalChange
from glide_shared.commands.server_modules.json_options import (
    JsonArrIndexOptions,
    JsonArrPopOptions,
    JsonGetOptions,
)
from glide_shared.constants import (
    TOK,
    TEncodable,
    TJsonResponse,
    TJsonUniversalResponse,
)

from ..glide_client import TGlideClient


def set(
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
            The key will be modified only if `value` is added as the last child in the specified `path`, or if the specified
            `path` acts as the parent of a new child being added.
        value (TEncodable): The value to set at the specific path, in JSON formatted bytes or str.
        set_condition (Optional[ConditionalChange]): Set the value only if the given condition is met (within the key or path).
            Equivalent to [`XX` | `NX`] in the RESP API. Defaults to None.

    Returns:
        Optional[TOK]: If the value is successfully set, returns OK.
            If `value` isn't set because of `set_condition`, returns None.

    Examples:
        >>> from glide import glide_json
        >>> import json
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = json.dumps(value)
        >>> glide_json.set(client, "doc", "$", json_str)
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
    """
    args = ["JSON.SET", key, path, value]
    if set_condition:
        args.append(set_condition.value)

    return cast(Optional[TOK], client.custom_command(args))


def get(
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
        paths (Optional[Union[TEncodable, List[TEncodable]]]): The path or list of paths within the JSON document.
            Default to None.
        options (Optional[JsonGetOptions]): Options for formatting the byte representation of the JSON data.
            See `JsonGetOptions`.

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
                Returns a stringified JSON object in bytes, in which each path is a key, and it's corresponding value, is the
                value as if the path was executed in the command as a single path.
        In case of multiple paths, and `paths` are a mix of both JSONPath and legacy path, the command behaves as if all are
        JSONPath paths.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json, JsonGetOptions
        >>> import json
        >>> json_str = glide_json.get(client, "doc", "$")
        >>> json.loads(str(json_str)) # Parse JSON string to Python data
            [{"a": 1.0, "b" :2}]  # JSON object retrieved from the key `doc` using json.loads()
        >>> glide_json.get(client, "doc", "$")
            b"[{\"a\":1.0,\"b\":2}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> glide_json.get(client, "doc", ["$.a", "$.b"], JsonGetOptions(indent="  ", newline="\n", space=" "))
            b"{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}"  # Returns the values at paths '$.a' and '$.b' in the JSON
                                                                    # document stored at `doc`, with specified
                                                                    # formatting options.
        >>> glide_json.get(client, "doc", "$.non_existing_path")
            b"[]"  # Returns an empty array since the path '$.non_existing_path' does not exist in the JSON document
                   # stored at `doc`.
    """
    args = ["JSON.GET", key]
    if options:
        args.extend(options.get_options())
    if paths:
        if isinstance(paths, (str, bytes)):
            paths = [paths]
        args.extend(paths)

    return cast(TJsonResponse[Optional[bytes]], client.custom_command(args))


def arrappend(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    values: List[TEncodable],
) -> TJsonResponse[int]:
    """
    Appends one or more `values` to the JSON array at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): Represents the path within the JSON document where the `values` will be appended.
        values (TEncodable): The values to append to the JSON array at the specified path.
            JSON string values must be wrapped with quotes. For example, to append `"foo"`, pass `"\"foo\""`.

    Returns:
        TJsonResponse[int]:
            For JSONPath (`path` starts with `$`):
                Returns a list of integer replies for every possible path, indicating the new length of the array after
                appending `values`,
                or None for JSON values matching the path that are not an array.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns the length of the array after appending `values` to the array at `path`.
                If multiple paths match, the length of the first updated array is returned.
                If the JSON value at `path` is not a array or if `path` doesn't exist, an error is raised.
            If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> import json
        >>> glide_json.set(client, "doc", "$", '{"a": 1, "b": ["one", "two"]}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> glide_json.arrappend(client, "doc", ["three"], "$.b")
            [3]  # Returns the new length of the array at path '$.b' after appending the value.
        >>> glide_json.arrappend(client, "doc", ["four"], ".b")
            4 # Returns the new length of the array at path '.b' after appending the value.
        >>> json.loads(glide_json.get(client, "doc", "."))
            {"a": 1, "b": ["one", "two", "three", "four"]}  # Returns the updated JSON document
    """
    args = ["JSON.ARRAPPEND", key, path] + values
    return cast(TJsonResponse[int], client.custom_command(args))


def arrindex(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    value: TEncodable,
    options: Optional[JsonArrIndexOptions] = None,
) -> TJsonResponse[int]:
    """
    Searches for the first occurrence of a scalar JSON value (i.e., a value that is neither an object nor an array) within
    arrays at the specified `path` in the JSON document stored at `key`.

    If specified, `options.start` and `options.end` define an inclusive-to-exclusive search range within the array.
    (Where `options.start` is inclusive and `options.end` is exclusive).

    Out-of-range indices adjust to the nearest valid position, and negative values count from the end (e.g., `-1` is the last
    element, `-2` the second last).

    Setting `options.end` to `0` behaves like `-1`, extending the range to the array's end (inclusive).

    If `options.start` exceeds `options.end`, `-1` is returned, indicating that the value was not found.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        value (TEncodable): The value to search for within the arrays.
        options (Optional[JsonArrIndexOptions]): Options specifying an inclusive `start` index and an optional exclusive `end`
            index for a range-limited search.
            Defaults to the full array if not provided. See `JsonArrIndexOptions`.

    Returns:
        Optional[TJsonResponse[int]]:
            For JSONPath (`path` starts with `$`):
                Returns an array of integers for every possible path, indicating of the first occurrence of `value`
                within the array,
                or None for JSON values matching the path that are not an array.
                A returned value of `-1` indicates that the value was not found in that particular array.
                If `path` does not exist, an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns an integer representing the index of the first occurrence of `value` within the array at the
                specified path.
                A returned value of `-1` indicates that the value was not found in that particular array.
                If multiple paths match, the index of the value from the first matching array is returned.
                If the JSON value at the `path` is not an array or if `path` does not exist, an error is raised.
            If `key` does not exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '[[], ["a"], ["a", "b"], ["a", "b", "c"]]')
            'OK'
        >>> glide_json.arrindex(client, "doc", "$[*]", '"b"')
            [-1, -1, 1, 1]
        >>> glide_json.set(client, "doc", ".", '{"children": ["John", "Jack", "Tom", "Bob", "Mike"]}')
            'OK'
        >>> glide_json.arrindex(client, "doc", ".children", '"Tom"')
            2
        >>> glide_json.set(client, "doc", "$", '{"fruits": ["apple", "banana", "cherry", "banana", "grape"]}')
            'OK'
        >>> glide_json.arrindex(client, "doc", "$.fruits", '"banana"', JsonArrIndexOptions(start=2, end=4))
            3
        >>> glide_json.set(client, "k", ".", '[1, 2, "a", 4, "a", 6, 7, "b"]')
            'OK'
        >>> glide_json.arrindex(client, "k", ".", '"b"', JsonArrIndexOptions(start=4, end=0))
            7  # "b" found at index 7 within the specified range, treating end=0 as the entire array's end.
        >>> glide_json.arrindex(client, "k", ".", '"b"', JsonArrIndexOptions(start=4, end=-1))
            7  # "b" found at index 7, with end=-1 covering the full array to its last element.
        >>> glide_json.arrindex(client, "k", ".", '"b"', JsonArrIndexOptions(start=4, end=7))
            -1  # "b" not found within the range from index 4 to exclusive end at index 7.
    """
    args = ["JSON.ARRINDEX", key, path, value]

    if options:
        args.extend(options.to_args())

    return cast(TJsonResponse[int], client.custom_command(args))


def arrinsert(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    index: int,
    values: List[TEncodable],
) -> TJsonResponse[int]:
    """
    Inserts one or more values into the array at the specified `path` within the JSON document stored at `key`,
    before the given `index`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        index (int): The array index before which values are inserted.
        values (List[TEncodable]): The JSON values to be inserted into the array, in JSON formatted bytes or str.
            Json string values must be wrapped with single quotes. For example, to append "foo", pass '"foo"'.

    Returns:
        TJsonResponse[int]:
            For JSONPath (`path` starts with '$'):
                Returns a list of integer replies for every possible path, indicating the new length of the array,
                or None for JSON values matching the path that are not an array.
                If `path` does not exist, an empty array will be returned.
            For legacy path (`path` doesn't start with '$'):
                Returns an integer representing the new length of the array.
                If multiple paths are matched, returns the length of the first modified array.
                If `path` doesn't exist or the value at `path` is not an array, an error is raised.
            If the index is out of bounds, an error is raised.
            If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '[[], ["a"], ["a", "b"]]')
            'OK'
        >>> glide_json.arrinsert(client, "doc", "$[*]", 0, ['"c"', '{"key": "value"}', "true", "null", '["bar"]'])
            [5, 6, 7]  # New lengths of arrays after insertion
        >>> glide_json.get(client, "doc")
            b'[["c",{"key":"value"},true,null,["bar"]],["c",{"key":"value"},true,null,["bar"],"a"],["c",{"key":"value"},true,null,["bar"],"a","b"]]'

        >>> glide_json.set(client, "doc", "$", '[[], ["a"], ["a", "b"]]')
            'OK'
        >>> glide_json.arrinsert(client, "doc", ".", 0, ['"c"'])
            4  # New length of the root array after insertion
        >>> glide_json.get(client, "doc")
            b'[\"c\",[],[\"a\"],[\"a\",\"b\"]]'
    """
    args = ["JSON.ARRINSERT", key, path, str(index)] + values
    return cast(TJsonResponse[int], client.custom_command(args))


def arrlen(
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
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": [1, 2, 3], "b": {"a": [1, 2], "c": {"a": 42}}}')
            'OK'  # JSON is successfully set for doc
        >>> glide_json.arrlen(client, "doc", "$")
            [None]  # No array at the root path.
        >>> glide_json.arrlen(client, "doc", "$.a")
            [3]  # Retrieves the length of the array at path $.a.
        >>> glide_json.arrlen(client, "doc", "$..a")
            [3, 2, None]  # Retrieves lengths of arrays found at all levels of the path `$..a`.
        >>> glide_json.arrlen(client, "doc", "..a")
            3  # Legacy path retrieves the first array match at path `..a`.
        >>> glide_json.arrlen(client, "non_existing_key", "$.a")
            None  # Returns None because the key does not exist.

        >>> glide_json.set(client, "doc", "$", '[1, 2, 3, 4]')
            'OK'  # JSON is successfully set for doc
        >>> glide_json.arrlen(client, "doc")
            4  # Retrieves lengths of array in root.
    """
    args = ["JSON.ARRLEN", key]
    if path:
        args.append(path)
    return cast(
        Optional[TJsonResponse[int]],
        client.custom_command(args),
    )


def arrpop(
    client: TGlideClient,
    key: TEncodable,
    options: Optional[JsonArrPopOptions] = None,
) -> Optional[TJsonResponse[bytes]]:
    """
    Pops an element from the array located at the specified path within the JSON document stored at `key`.
    If `options.index` is provided, it pops the element at that index instead of the last element.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        options (Optional[JsonArrPopOptions]): Options including the path and optional index. See `JsonArrPopOptions`.
            Default to None.
            If not specified, attempts to pop the last element from the root value if it's an array.
            If the root value is not an array, an error will be raised.

    Returns:
        Optional[TJsonResponse[bytes]]:
            For JSONPath (`options.path` starts with `$`):
                Returns a list of bytes string replies for every possible path, representing the popped JSON values,
                or None for JSON values matching the path that are not an array or are an empty array.
                If `options.path` doesn't exist, an empty list will be returned.
            For legacy path (`options.path` doesn't starts with `$`):
                Returns a bytes string representing the popped JSON value, or None if the array at `options.path` is empty.
                If multiple paths match, the value from the first matching array that is not empty is returned.
                If the JSON value at `options.path` is not a array or if `options.path` doesn't exist, an error is raised.
            If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(
        ...     client,
        ...     "doc",
        ...     "$",
        ...     '{"a": [1, 2, true], "b": {"a": [3, 4, ["value", 3, false], 5], "c": {"a": 42}}}'
        ... )
            b'OK'
        >>> glide_json.arrpop(client, "doc", JsonArrPopOptions(path="$.a", index=1))
            [b'2']  # Pop second element from array at path $.a
        >>> glide_json.arrpop(client, "doc", JsonArrPopOptions(path="$..a"))
            [b'true', b'5', None]  # Pop last elements from all arrays matching path `$..a`

        #### Using a legacy path (..) to pop the first matching array
        >>> glide_json.arrpop(client, "doc", JsonArrPopOptions(path="..a"))
            b"1"  # First match popped (from array at path ..a)

        #### Even though only one value is returned from `..a`, subsequent arrays are also affected
        >>> glide_json.get(client, "doc", "$..a")
            b"[[], [3, 4], 42]"  # Remaining elements after pop show the changes

        >>> glide_json.set(client, "doc", "$", '[[], ["a"], ["a", "b", "c"]]')
            b'OK'  # JSON is successfully set
        >>> glide_json.arrpop(client, "doc", JsonArrPopOptions(path=".", index=-1))
            b'["a","b","c"]'  # Pop last elements at path `.`
        >>> glide_json.arrpop(client, "doc")
            b'["a"]'  # Pop last elements at path `.`
    """
    args = ["JSON.ARRPOP", key]
    if options:
        args.extend(options.to_args())

    return cast(
        Optional[TJsonResponse[bytes]],
        client.custom_command(args),
    )


def arrtrim(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    start: int,
    end: int,
) -> TJsonResponse[int]:
    """
    Trims an array at the specified `path` within the JSON document stored at `key` so that it becomes a subarray [start, end],
    both inclusive.
    If `start` < 0, it is treated as 0.
    If `end` >= size (size of the array), it is treated as size-1.
    If `start` >= size or `start` > `end`, the array is emptied and 0 is returned.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        start (int): The start index, inclusive.
        end (int): The end index, inclusive.

    Returns:
        TJsonResponse[int]:
            For JSONPath (`path` starts with '$'):
                Returns a list of integer replies for every possible path, indicating the new length of the array, or None for
                JSON values matching the path that are not an array.
                If a value is an empty array, its corresponding return value is 0.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't starts with `$`):
                Returns an integer representing the new length of the array.
                If the array is empty, returns 0.
                If multiple paths match, the length of the first trimmed array match is returned.
                If `path` doesn't exist, or the value at `path` is not an array, an error is raised.
            If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '[[], ["a"], ["a", "b"], ["a", "b", "c"]]')
            'OK'
        >>> glide_json.arrtrim(client, "doc", "$[*]", 0, 1)
            [0, 1, 2, 2]
        >>> glide_json.get(client, "doc")
            b'[[],[\"a\"],[\"a\",\"b\"],[\"a\",\"b\"]]'

        >>> glide_json.set(client, "doc", "$", '{"children": ["John", "Jack", "Tom", "Bob", "Mike"]}')
            'OK'
        >>> glide_json.arrtrim(client, "doc", ".children", 0, 1)
            2
        >>> glide_json.get(client, "doc", ".children")
            b'["John","Jack"]'
    """
    return cast(
        TJsonResponse[int],
        client.custom_command(["JSON.ARRTRIM", key, path, str(start), str(end)]),
    )


def clear(
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
        If `path` doesn't exist, or the value at `path` is already empty (e.g., an empty array, object, or string),
        0 is returned.
        If `key doesn't exist, an error is raised.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(
        ...     client,
        ...     "doc",
        ...     "$",
        ...     '{"obj":{"a":1, "b":2}, "arr":[1,2,3], "str": "foo", "bool": true, "int": 42, "float": 3.14, "nullVal": null}'
        ... )
            'OK'  # JSON document is successfully set.
        >>> glide_json.clear(client, "doc", "$.*")
            6      # 6 values are cleared (arrays/objects/strings/numbers/booleans), but `null` remains as is.
        >>> glide_json.get(client, "doc", "$")
            b'[{"obj":{},"arr":[],"str":"","bool":false,"int":0,"float":0.0,"nullVal":null}]'
        >>> glide_json.clear(client, "doc", "$.*")
            0  # No further clearing needed since the containers are already empty and the values are defaults.

        >>> glide_json.set(
        ...     client,
        ...     "doc",
        ...     "$",
        ...     (
        ...         '{"a": 1, '
        ...         '"b": {"a": [5, 6, 7], "b": {"a": true}}, '
        ...         '"c": {"a": "value", "b": {"a": 3.5}}, '
        ...         '"d": {"a": {"foo": "foo"}}, '
        ...         '"nullVal": null}'
        ...     )
        ... )
            'OK'
        >>> glide_json.clear(client, "doc", "b.a[1:3]")
            2  # 2 elements (`6` and `7`) are cleared.
        >>> glide_json.clear(client, "doc", "b.a[1:3]")
            0 # No elements cleared since specified slice has already been cleared.
        >>> glide_json.get(client, "doc", "$..a")
            b'[1,[5,0,0],true,"value",3.5,{"foo":"foo"}]'

        >>> glide_json.clear(client, "doc", "$..a")
            6  # All numeric, boolean, and string values across paths are cleared.
        >>> glide_json.get(client, "doc", "$..a")
            b'[0,[],false,"",0.0,{}]'
    """
    args = ["JSON.CLEAR", key]
    if path:
        args.append(path)

    return cast(int, client.custom_command(args))


def debug_fields(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[TJsonUniversalResponse[int]]:
    """
    Returns the number of fields of the JSON value at the specified `path` within the JSON document stored at `key`.
    - **Primitive Values**: Each non-container JSON value (e.g., strings, numbers, booleans, and null) counts as one field.
    - **Arrays and Objects:**: Each item in an array and each key-value pair in an object is counted as one field.
        (Each top-level value counts as one field, regardless of it's type.)
        - Their nested values are counted recursively and added to the total.
        - **Example**: For the JSON `{"a": 1, "b": [2, 3, {"c": 4}]}`, the count would be:
            - Top-level: 2 fields (`"a"` and `"b"`)
            - Nested: 3 fields in the array (`2`, `3`, and `{"c": 4}`) plus 1 for the object (`"c"`)
            - Total: 2 (top-level) + 3 (from array) + 1 (from nested object) = 6 fields.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Defaults to root if not provided.

    Returns:
        Optional[TJsonUniversalResponse[int]]:
            For JSONPath (`path` starts with `$`):
                Returns an array of integers, each indicating the number of fields for each matched `path`.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns an integer indicating the number of fields for each matched `path`.
                If multiple paths match, number of fields of the first JSON value match is returned.
                If `path` doesn't exist, an error is raised.
            If `path` is not provided, it reports the total number of fields in the entire JSON document.
            If `key` doesn't exist, None is returned.
        For more information about the returned type, see `TJsonUniversalResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "k1", "$", '[1, 2.3, "foo", true, null, {}, [], {"a":1, "b":2}, [1,2,3]]')
            'OK'
        >>> glide_json.debug_fields(client, "k1", "$[*]")
            [1, 1, 1, 1, 1, 0, 0, 2, 3]
        >>> glide_json.debug_fields(client, "k1", ".")
            14 # 9 top-level fields + 5 nested address fields

        >>> glide_json.set(
        ...     client,
        ...     "k1",
        ...     "$",
        ...     (
        ...         '{"firstName":"John", '
        ...         '"lastName":"Smith", '
        ...         '"age":27, '
        ...         '"weight":135.25, '
        ...         '"isAlive":true, '
        ...         '"address":{"street":"21 2nd Street","city":"New York","state":"NY","zipcode":"10021-3100"}, '
        ...         '"phoneNumbers":[{"type":"home","number":"212 555-1234"},{"type":"office","number":"646 555-4567"}], '
        ...         '"children":[], '
        ...         '"spouse":null}'
        ...     )
        ... )
            'OK'
        >>> glide_json.debug_fields(client, "k1")
            19
        >>> glide_json.debug_fields(client, "k1", ".address")
            4
    """
    args = ["JSON.DEBUG", "FIELDS", key]
    if path:
        args.append(path)

    return cast(Optional[TJsonUniversalResponse[int]], client.custom_command(args))


def debug_memory(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[TJsonUniversalResponse[int]]:
    """
    Reports memory usage in bytes of a JSON value at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Defaults to None.

    Returns:
        Optional[TJsonUniversalResponse[int]]:
            For JSONPath (`path` starts with `$`):
                Returns an array of integers, indicating the memory usage in bytes of a JSON value for each matched `path`.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns an integer, indicating the memory usage in bytes for the JSON value in `path`.
                If multiple paths match, the memory usage of the first JSON value match is returned.
                If `path` doesn't exist, an error is raised.
            If `path` is not provided, it reports the total memory usage in bytes in the entire JSON document.
            If `key` doesn't exist, None is returned.
        For more information about the returned type, see `TJsonUniversalResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "k1", "$", '[1, 2.3, "foo", true, null, {}, [], {"a":1, "b":2}, [1,2,3]]')
            'OK'
        >>> glide_json.debug_memory(client, "k1", "$[*]")
            [16, 16, 19, 16, 16, 16, 16, 66, 64]

        >>> glide_json.set(
        ...     client,
        ...     "k1",
        ...     "$",
        ...     (
        ...         '{"firstName":"John", '
        ...         '"lastName":"Smith", '
        ...         '"age":27, '
        ...         '"weight":135.25, '
        ...         '"isAlive":true, '
        ...         '"address":{"street":"21 2nd Street","city":"New York","state":"NY","zipcode":"10021-3100"}, '
        ...         '"phoneNumbers":[{"type":"home","number":"212 555-1234"},{"type":"office","number":"646 555-4567"}], '
        ...         '"children":[], '
        ...         '"spouse":null}'
        ...     )
        ... )
            'OK'
        >>> glide_json.debug_memory(client, "k1")
            472
        >>> glide_json.debug_memory(client, "k1", ".phoneNumbers")
            164
    """
    args = ["JSON.DEBUG", "MEMORY", key]
    if path:
        args.append(path)

    return cast(Optional[TJsonUniversalResponse[int]], client.custom_command(args))


def delete(
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
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> glide_json.delete(client, "doc", "$..a")
            2  # Indicates successful deletion of the specific values in the key stored at `doc`.
        >>> glide_json.get(client, "doc", "$")
            "[{\"nested\":{\"b\":3}}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> glide_json.delete(client, "doc")
            1  # Deletes the entire JSON document stored at `doc`.
    """

    return cast(
        int, client.custom_command(["JSON.DEL", key] + ([path] if path else []))
    )


def forget(
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
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> glide_json.forget(client, "doc", "$..a")
            2  # Indicates successful deletion of the specific values in the key stored at `doc`.
        >>> glide_json.get(client, "doc", "$")
            "[{\"nested\":{\"b\":3}}]"  # Returns the value at path '$' in the JSON document stored at `doc`.
        >>> glide_json.forget(client, "doc")
            1  # Deletes the entire JSON document stored at `doc`.
    """

    return cast(
        Optional[int],
        client.custom_command(["JSON.FORGET", key] + ([path] if path else [])),
    )


def mget(
    client: TGlideClient,
    keys: List[TEncodable],
    path: TEncodable,
) -> List[Optional[bytes]]:
    """
    Retrieves the JSON values at the specified `path` stored at multiple `keys`.

    Note:
        In cluster mode, if keys in `keys` map to different hash slots, the command
        will be split across these slots and executed separately for each. This means the command
        is atomic only at the slot level. If one or more slot-specific requests fail, the entire
        call will return the first encountered error, even though some requests may have succeeded
        while others did not. If this behavior impacts your application logic, consider splitting
        the request into sub-requests per slot to ensure atomicity.

    Args:
        client (TGlideClient): The client to execute the command.
        keys (List[TEncodable]): A list of keys for the JSON documents.
        path (TEncodable): The path within the JSON documents.

    Returns:
        List[Optional[bytes]]:
            For JSONPath (`path` starts with `$`):
                Returns a list of byte representations of the values found at the given path for each key.
                If `path` does not exist within the key, the entry will be an empty array.
            For legacy path (`path` doesn't starts with `$`):
                Returns a list of byte representations of the values found at the given path for each key.
                If `path` does not exist within the key, the entry will be None.
            If a key doesn't exist, the corresponding list element will be None.


    Examples:
        >>> from glide import glide_json
        >>> import json
        >>> json_strs = glide_json.mget(client, ["doc1", "doc2"], "$")
        >>> [json.loads(js) for js in json_strs]  # Parse JSON strings to Python data
            [[{"a": 1.0, "b": 2}], [{"a": 2.0, "b": {"a": 3.0, "b" : 4.0}}]]  # JSON objects retrieved from keys
                                                                              # `doc1` and `doc2`
        >>> glide_json.mget(client, ["doc1", "doc2"], "$.a")
            [b"[1.0]", b"[2.0]"]  # Returns values at path '$.a' for the JSON documents stored at `doc1` and `doc2`.
        >>> glide_json.mget(client, ["doc1"], "$.non_existing_path")
            [None]  # Returns an empty array since the path '$.non_existing_path' does not exist in the JSON document
                    # stored at `doc1`.
    """
    args = ["JSON.MGET"] + keys + [path]
    return cast(List[Optional[bytes]], client.custom_command(args))


def numincrby(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    number: Union[int, float],
) -> bytes:
    """
    Increments or decrements the JSON value(s) at the specified `path` by `number` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        number (Union[int, float]): The number to increment or decrement by.

    Returns:
        bytes:
            For JSONPath (`path` starts with `$`):
                Returns a bytes string representation of an array of bulk strings, indicating the new values after
                incrementing for each matched `path`.
                If a value is not a number, its corresponding return value will be `null`.
                If `path` doesn't exist, a byte string representation of an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns a bytes string representation of the resulting value after the increment or decrement.
                If multiple paths match, the result of the last updated value is returned.
                If the value at the `path` is not a number or `path` doesn't exist, an error is raised.
            If `key` does not exist, an error is raised.
            If the result is out of the range of 64-bit IEEE double, an error is raised.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": [], "b": [1], "c": [1, 2], "d": [1, 2, 3]}')
            'OK'
        >>> glide_json.numincrby(client, "doc", "$.d[*]", 10)
            b'[11,12,13]'  # Increment each element in `d` array by 10.
        >>> glide_json.numincrby(client, "doc", ".c[1]", 10)
            b'12'  # Increment the second element in the `c` array by 10.
    """
    args = ["JSON.NUMINCRBY", key, path, str(number)]

    return cast(bytes, client.custom_command(args))


def nummultby(
    client: TGlideClient,
    key: TEncodable,
    path: TEncodable,
    number: Union[int, float],
) -> bytes:
    """
    Multiplies the JSON value(s) at the specified `path` by `number` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): The path within the JSON document.
        number (Union[int, float]): The number to multiply by.

    Returns:
        bytes:
            For JSONPath (`path` starts with `$`):
                Returns a bytes string representation of an array of bulk strings, indicating the new values after
                multiplication for each matched `path`.
                If a value is not a number, its corresponding return value will be `null`.
                If `path` doesn't exist, a byte string representation of an empty array will be returned.
            For legacy path (`path` doesn't start with `$`):
                Returns a bytes string representation of the resulting value after multiplication.
                If multiple paths match, the result of the last updated value is returned.
                If the value at the `path` is not a number or `path` doesn't exist, an error is raised.
            If `key` does not exist, an error is raised.
            If the result is out of the range of 64-bit IEEE double, an error is raised.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": [], "b": [1], "c": [1, 2], "d": [1, 2, 3]}')
            'OK'
        >>> glide_json.nummultby(client, "doc", "$.d[*]", 2)
            b'[2,4,6]'  # Multiplies each element in the `d` array by 2.
        >>> glide_json.nummultby(client, "doc", ".c[1]", 2)
            b'4'  # Multiplies the second element in the `c` array by 2.
    """
    args = ["JSON.NUMMULTBY", key, path, str(number)]

    return cast(bytes, client.custom_command(args))


def objlen(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[TJsonResponse[int]]:
    """
    Retrieves the number of key-value pairs in the object stored at the specified `path` within the JSON document stored at
    `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Defaults to None.

    Returns:
        Optional[TJsonResponse[int]]:
            For JSONPath (`path` starts with `$`):
                Returns a list of integer replies for every possible path, indicating the length of the object,
                or None for JSON values matching the path that are not an object.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't starts with `$`):
                Returns the length of the object at `path`.
                If multiple paths match, the length of the first object match is returned.
                If the JSON value at `path` is not an object or if `path` doesn't exist, an error is raised.
            If `key` doesn't exist, None is returned.
        For more information about the returned type, see `TJsonResponse`.


    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": 1.0, "b": {"a": {"x": 1, "y": 2}, "b": 2.5, "c": true}}')
            b'OK'  # Indicates successful setting of the value at the root path '$' in the key `doc`.
        >>> glide_json.objlen(client, "doc", "$")
            [2]  # Returns the number of key-value pairs at the root object, which has 2 keys: 'a' and 'b'.
        >>> glide_json.objlen(client, "doc", ".")
            2  # Returns the number of key-value pairs for the object matching the path '.', which has 2 keys: 'a' and 'b'.
        >>> glide_json.objlen(client, "doc", "$.b")
            [3]  # Returns the length of the object at path '$.b', which has 3 keys: 'a', 'b', and 'c'.
        >>> glide_json.objlen(client, "doc", ".b")
            3  # Returns the length of the nested object at path '.b', which has 3 keys.
        >>> glide_json.objlen(client, "doc", "$..a")
            [None, 2]
        >>> glide_json.objlen(client, "doc")
            2  # Returns the number of key-value pairs for the object matching the path '.', which has 2 keys: 'a' and 'b'.
    """
    args = ["JSON.OBJLEN", key]
    if path:
        args.append(path)
    return cast(
        Optional[TJsonResponse[int]],
        client.custom_command(args),
    )


def objkeys(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[TJsonUniversalResponse[List[bytes]]]:
    """
    Retrieves key names in the object values at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): Represents the path within the JSON document where the key names will be retrieved.
            Defaults to None.

    Returns:
        Optional[TJsonUniversalResponse[List[bytes]]]:
            For JSONPath (`path` starts with `$`):
                Returns a list of arrays containing key names for each matching object.
                If a value matching the path is not an object, an empty array is returned.
                If `path` doesn't exist, an empty array is returned.
            For legacy path (`path` starts with `.`):
                Returns a list of key names for the object value matching the path.
                If multiple objects match the path, the key names of the first object are returned.
                If a value matching the path is not an object, an error is raised.
                If `path` doesn't exist, None is returned.
            If `key` doesn't exist, None is returned.
        For more information about the returned type, see `TJsonUniversalResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": 1.0, "b": {"a": {"x": 1, "y": 2}, "b": 2.5, "c": true}}')
            b'OK'  # Indicates successful setting of the value at the root path '$' in the key `doc`.
        >>> glide_json.objkeys(client, "doc", "$")
            [[b"a", b"b"]]  # Returns a list of arrays containing the key names for objects matching the path '$'.
        >>> glide_json.objkeys(client, "doc", ".")
            [b"a", b"b"]  # Returns key names for the object matching the path '.' as it is the only match.
        >>> glide_json.objkeys(client, "doc", "$.b")
            [[b"a", b"b", b"c"]]  # Returns key names as a nested list for objects matching the JSONPath '$.b'.
        >>> glide_json.objkeys(client, "doc", ".b")
            [b"a", b"b", b"c"]  # Returns key names for the nested object at path '.b'.
    """
    args = ["JSON.OBJKEYS", key]
    if path:
        args.append(path)
    return cast(
        Optional[Union[List[bytes], List[List[bytes]]]],
        client.custom_command(args),
    )


def resp(
    client: TGlideClient, key: TEncodable, path: Optional[TEncodable] = None
) -> TJsonUniversalResponse[
    Optional[Union[bytes, int, List[Optional[Union[bytes, int]]]]]
]:
    """
    Retrieve the JSON value at the specified `path` within the JSON document stored at `key`.
    The returning result is in the Valkey or Redis OSS Serialization Protocol (RESP).\n
    JSON null is mapped to the RESP Null Bulk String.\n
    JSON Booleans are mapped to RESP Simple string.\n
    JSON integers are mapped to RESP Integers.\n
    JSON doubles are mapped to RESP Bulk Strings.\n
    JSON strings are mapped to RESP Bulk Strings.\n
    JSON arrays are represented as RESP arrays, where the first element is the simple string [, followed by the array's
    elements.\n
    JSON objects are represented as RESP object, where the first element is the simple string {, followed by key-value pairs,
    each of which is a RESP bulk string.\n


    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Default to None.

    Returns:
        TJsonUniversalResponse[Optional[Union[bytes, int, List[Optional[Union[bytes, int]]]]]]
            For JSONPath ('path' starts with '$'):
                Returns a list of replies for every possible path, indicating the RESP form of the JSON value.
                If `path` doesn't exist, returns an empty list.
            For legacy path (`path` doesn't starts with `$`):
                Returns a single reply for the JSON value at the specified path, in its RESP form.
                This can be a bytes object, an integer, None, or a list representing complex structures.
                If multiple paths match, the value of the first JSON value match is returned.
                If `path` doesn't exist, an error is raised.
            If `key` doesn't exist, an None is returned.
        For more information about the returned type, see `TJsonUniversalResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": [1, 2, 3], "b": {"a": [1, 2], "c": {"a": 42}}}')
            'OK'
        >>> glide_json.resp(client, "doc", "$..a")
            [[b"[", 1, 2, 3],[b"[", 1, 2],42]
        >>> glide_json.resp(client, "doc", "..a")
            [b"[", 1, 2, 3]
    """
    args = ["JSON.RESP", key]
    if path:
        args.append(path)

    return cast(
        TJsonUniversalResponse[
            Optional[Union[bytes, int, List[Optional[Union[bytes, int]]]]]
        ],
        client.custom_command(args),
    )


def strappend(
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
        value (TEncodable): The value to append to the string. Must be wrapped with single quotes. For example,
            to append "foo", pass '"foo"'.
        path (Optional[TEncodable]): The path within the JSON document. Default to None.

    Returns:
        TJsonResponse[int]:
            For JSONPath (`path` starts with `$`):
                Returns a list of integer replies for every possible path, indicating the length of the resulting string after
                appending `value`,
                or None for JSON values matching the path that are not string.
                If `key` doesn't exist, an error is raised.
            For legacy path (`path` doesn't start with `$`):
                Returns the length of the resulting string after appending `value` to the string at `path`.
                If multiple paths match, the length of the last updated string is returned.
                If the JSON value at `path` is not a string of if `path` doesn't exist, an error is raised.
                If `key` doesn't exist, an error is raised.
        For more information about the returned type, see `TJsonResponse`.

    Examples:
        >>> from glide import glide_json
        >>> import json
        >>> glide_json.set(client, "doc", "$", json.dumps({"a":"foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}))
            'OK'
        >>> glide_json.strappend(client, "doc", json.dumps("baz"), "$..a")
            [6, 8, None]  # The new length of the string values at path '$..a' in the key stored at `doc` after the append
                          # operation.
        >>> glide_json.strappend(client, "doc", '"foo"', "nested.a")
            11  # The length of the string value after appending "foo" to the string at path 'nested.array' in the key stored
                # at `doc`.
        >>> json.loads(glide_json.get(client, json.dumps("doc"), "$"))
            [{"a":"foobaz", "nested": {"a": "hellobazfoo"}, "nested2": {"a": 31}}] # The updated JSON value in the key stored
                                                                                   # at `doc`.
    """

    return cast(
        TJsonResponse[int],
        client.custom_command(
            ["JSON.STRAPPEND", key] + ([path, value] if path else [value])
        ),
    )


def strlen(
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
        >>> from glide import glide_json
        >>> import json
        >>> glide_json.set(client, "doc", "$", json.dumps({"a":"foo", "nested": {"a": "hello"}, "nested2": {"a": 31}}))
            'OK'
        >>> glide_json.strlen(client, "doc", "$..a")
            [3, 5, None]  # The length of the string values at path '$..a' in the key stored at `doc`.
        >>> glide_json.strlen(client, "doc", "nested.a")
            5  # The length of the JSON value at path 'nested.a' in the key stored at `doc`.
        >>> glide_json.strlen(client, "doc", "$")
            [None]  # Returns an array with None since the value at root path does in the JSON document stored at `doc` is not
                    # a string.
        >>> glide_json.strlen(client, "non_existing_key", ".")
            None  # `key` doesn't exist.
    """

    return cast(
        TJsonResponse[Optional[int]],
        client.custom_command(
            ["JSON.STRLEN", key, path] if path else ["JSON.STRLEN", key]
        ),
    )


def toggle(
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
        >>> from glide import glide_json
        >>> import json
        >>> glide_json.set(
        ...     client,
        ...     "doc",
        ...     "$",
        ...     json.dumps({"bool": True, "nested": {"bool": False, "nested": {"bool": 10}}})
        ... )
            'OK'
        >>> glide_json.toggle(client, "doc", "$.bool")
            [False, True, None]  # Indicates successful toggling of the Boolean values at path '$.bool' in the key stored at
                                 # `doc`.
        >>> glide_json.toggle(client, "doc", "bool")
            True  # Indicates successful toggling of the Boolean value at path 'bool' in the key stored at `doc`.
        >>> json.loads(glide_json.get(client, "doc", "$"))
            [{"bool": True, "nested": {"bool": True, "nested": {"bool": 10}}}] # The updated JSON value in the key stored at
                                                                               # `doc`.
    """

    return cast(
        TJsonResponse[bool],
        client.custom_command(["JSON.TOGGLE", key, path]),
    )


def type(
    client: TGlideClient,
    key: TEncodable,
    path: Optional[TEncodable] = None,
) -> Optional[TJsonUniversalResponse[bytes]]:
    """
    Retrieves the type of the JSON value at the specified `path` within the JSON document stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        path (Optional[TEncodable]): The path within the JSON document. Default to None.

    Returns:
        Optional[TJsonUniversalResponse[bytes]]:
            For JSONPath ('path' starts with '$'):
                Returns a list of byte string replies for every possible path, indicating the type of the JSON value.
                If `path` doesn't exist, an empty array will be returned.
            For legacy path (`path` doesn't starts with `$`):
                Returns the type of the JSON value at `path`.
                If multiple paths match, the type of the first JSON value match is returned.
                If `path` doesn't exist, None will be returned.
            If `key` doesn't exist, None is returned.
        For more information about the returned type, see `TJsonUniversalResponse`.

    Examples:
        >>> from glide import glide_json
        >>> glide_json.set(client, "doc", "$", '{"a": 1, "nested": {"a": 2, "b": 3}}')
            'OK'
        >>> glide_json.type(client, "doc", "$.nested")
            [b'object']  # Indicates the type of the value at path '$.nested' in the key stored at `doc`.
        >>> glide_json.type(client, "doc", "$.nested.a")
            [b'integer']  # Indicates the type of the value at path '$.nested.a' in the key stored at `doc`.
        >>> glide_json.type(client, "doc", "$[*]")
            [b'integer',  b'object']  # Array of types in all top level elements.
    """
    args = ["JSON.TYPE", key]
    if path:
        args.append(path)

    return cast(Optional[TJsonUniversalResponse[bytes]], client.custom_command(args))
