from typing import Dict, List, Optional, Union, cast

from glide.async_commands.core import ConditionalChange
from glide.constants import TOK
from glide.protobuf.redis_request_pb2 import RequestType
from glide.redis_client import TRedisClient

TJson = Union[str, int, float, bool, None, List["TJson"], Dict[str, "TJson"]]


def redis_json_to_string(json_data: TJson) -> str:
    if isinstance(json_data, (str, int, float, bool, type(None))):
        # If it's a basic type (string, integer, float, boolean, or None), convert to string
        return str(json_data)
    elif isinstance(json_data, dict):
        # If it's a dictionary, recursively convert each value to string
        return (
            "{"
            + ", ".join(
                f'"{k}": {redis_json_to_string(v)}' for k, v in json_data.items()
            )
            + "}"
        )
    elif isinstance(json_data, list):
        # If it's a list, recursively convert each element to string
        return "[" + ", ".join(redis_json_to_string(item) for item in json_data) + "]"
    else:
        raise ValueError("Unsupported JSON data type")


class Json:
    """Class for RedisJson commands."""

    async def set(
        self,
        client: TRedisClient,
        key: str,
        value: TJson,
        path: str,
        set_condition: Optional[ConditionalChange] = None,
    ) -> Optional[TOK]:
        args = [key]
        if not path.startswith("$"):  # not necessarily
            path = "$" + path
        args.append(f"'{path}'")
        args.append(redis_json_to_string(value))
        if set_condition:
            args.append(set_condition.value)

        return cast(Optional[TOK], client._execute_command(RequestType.JsonSet, args))
