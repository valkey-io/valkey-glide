# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Glide module for `JSON` commands in transaction.

    Examples:
        >>> import json
        >>> from glide import json_transaction
        >>> value = {'a': 1.0, 'b': 2}
        >>> json_str = json.dumps(value) # Convert Python dictionary to JSON string using json.dumps()
        >>> json_transaction.set(transaction, "doc", "$", json_str)
        >>> json_transaction.get(transaction, "doc", "$") # Returns the value at path '$' in the JSON document stored at `doc` as JSON string.
        >>> result = await glide_client.exec(transaction)
        >>> print result[0] # set result
            'OK'  # Indicates successful setting of the value at path '$' in the key stored at `doc`.
        >>> print result[1] # get result
            b"[{\"a\":1.0,\"b\":2}]"
        >>> print json.loads(str(result[1]))
            [{"a": 1.0, "b" :2}] # JSON object retrieved from the key `doc` using json.loads()
        """

from typing import List, Optional, Union, cast

from glide.async_commands.core import ConditionalChange
from glide.async_commands.server_modules.json import (
    JsonArrIndexOptions,
    JsonArrPopOptions,
    JsonGetOptions,
)
from glide.async_commands.transaction import TTransaction
from glide.constants import TEncodable
from glide.protobuf.command_request_pb2 import RequestType


def set(
    transaction: TTransaction,
    key: TEncodable,
    path: TEncodable,
    value: TEncodable,
    set_condition: Optional[ConditionalChange] = None,
) -> TTransaction:
    """
    Sets the JSON value at the specified `path` stored at `key`.

    Args:
        transaction (TTransaction): The transaction to execute the command.
        key (TEncodable): The key of the JSON document.
        path (TEncodable): Represents the path within the JSON document where the value will be set.
            The key will be modified only if `value` is added as the last child in the specified `path`, or if the specified `path` acts as the parent of a new child being added.
        value (TEncodable): The value to set at the specific path, in JSON formatted bytes or str.
        set_condition (Optional[ConditionalChange]): Set the value only if the given condition is met (within the key or path).
            Equivalent to [`XX` | `NX`] in the RESP API. Defaults to None.

    Command response:
        Optional[TOK]: If the value is successfully set, returns OK.
            If `value` isn't set because of `set_condition`, returns None.
    """
    args = ["JSON.SET", key, path, value]
    if set_condition:
        args.append(set_condition.value)

    return transaction.custom_command(args)


def get(
    transaction: TTransaction,
    key: TEncodable,
    paths: Optional[Union[TEncodable, List[TEncodable]]] = None,
    options: Optional[JsonGetOptions] = None,
) -> TTransaction:
    """
    Retrieves the JSON value at the specified `paths` stored at `key`.

    Args:
        client (TGlideClient): The client to execute the command.
        key (TEncodable): The key of the JSON document.
        paths (Optional[Union[TEncodable, List[TEncodable]]]): The path or list of paths within the JSON document. Default to None.
        options (Optional[JsonGetOptions]): Options for formatting the byte representation of the JSON data. See `JsonGetOptions`.

    Command response:
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
    """
    args = ["JSON.GET", key]
    if options:
        args.extend(options.get_options())
    if paths:
        if isinstance(paths, (str, bytes)):
            paths = [paths]
        args.extend(paths)

    return transaction.custom_command(args)
