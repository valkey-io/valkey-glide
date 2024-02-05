# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

from datetime import datetime, timedelta
from enum import Enum
from typing import (
    Dict,
    List,
    Mapping,
    Optional,
    Protocol,
    Set,
    Tuple,
    Type,
    Union,
    cast,
    get_args,
)

from glide.constants import TOK, TResult
from glide.protobuf.redis_request_pb2 import RequestType
from glide.routes import Route

from ..glide import Script


class ConditionalChange(Enum):
    """
    A condition to the "SET" and "ZADD" commands.
    - ONLY_IF_EXISTS - Only update key / elements that already exist. Equivalent to `XX` in the Redis API
    - ONLY_IF_DOES_NOT_EXIST - Only set key / add elements that does not already exist. Equivalent to `NX` in the Redis API
    """

    ONLY_IF_EXISTS = "XX"
    ONLY_IF_DOES_NOT_EXIST = "NX"


class ExpiryType(Enum):
    """SET option: The type of the expiry.
    - SEC - Set the specified expire time, in seconds. Equivalent to `EX` in the Redis API.
    - MILLSEC - Set the specified expire time, in milliseconds. Equivalent to `PX` in the Redis API.
    - UNIX_SEC - Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Redis API.
    - UNIX_MILLSEC - Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the
        Redis API.
    - KEEP_TTL - Retain the time to live associated with the key. Equivalent to `KEEPTTL` in the Redis API.
    """

    SEC = 0, Union[int, timedelta]  # Equivalent to `EX` in the Redis API
    MILLSEC = 1, Union[int, timedelta]  # Equivalent to `PX` in the Redis API
    UNIX_SEC = 2, Union[int, datetime]  # Equivalent to `EXAT` in the Redis API
    UNIX_MILLSEC = 3, Union[int, datetime]  # Equivalent to `PXAT` in the Redis API
    KEEP_TTL = 4, Type[None]  # Equivalent to `KEEPTTL` in the Redis API


class InfoSection(Enum):
    """
    INFO option: a specific section of information:

    -SERVER: General information about the Redis server
    -CLIENTS: Client connections section
    -MEMORY: Memory consumption related information
    -PERSISTENCE: RDB and AOF related information
    -STATS: General statistics
    -REPLICATION: Master/replica replication information
    -CPU: CPU consumption statistics
    -COMMANDSTATS: Redis command statistics
    -LATENCYSTATS: Redis command latency percentile distribution statistics
    -SENTINEL: Redis Sentinel section (only applicable to Sentinel instances)
    -CLUSTER: Redis Cluster section
    -MODULES: Modules section
    -KEYSPACE: Database related statistics
    -ERRORSTATS: Redis error statistics
    -ALL: Return all sections (excluding module generated ones)
    -DEFAULT: Return only the default set of sections
    -EVERYTHING: Includes all and modules
    When no parameter is provided, the default option is assumed.
    """

    SERVER = "server"
    CLIENTS = "clients"
    MEMORY = "memory"
    PERSISTENCE = "persistence"
    STATS = "stats"
    REPLICATION = "replication"
    CPU = "cpu"
    COMMAND_STATS = "commandstats"
    LATENCY_STATS = "latencystats"
    SENTINEL = "sentinel"
    CLUSTER = "cluster"
    MODULES = "modules"
    KEYSPACE = "keyspace"
    ERROR_STATS = "errorstats"
    ALL = "all"
    DEFAULT = "default"
    EVERYTHING = "everything"


class ExpireOptions(Enum):
    """
    EXPIRE option: options for setting key expiry.

    - HasNoExpiry: Set expiry only when the key has no expiry (Equivalent to "NX" in Redis).
    - HasExistingExpiry: Set expiry only when the key has an existing expiry (Equivalent to "XX" in Redis).
    - NewExpiryGreaterThanCurrent: Set expiry only when the new expiry is greater than the current one (Equivalent
        to "GT" in Redis).
    - NewExpiryLessThanCurrent: Set expiry only when the new expiry is less than the current one (Equivalent to "LT" in Redis).
    """

    HasNoExpiry = "NX"
    HasExistingExpiry = "XX"
    NewExpiryGreaterThanCurrent = "GT"
    NewExpiryLessThanCurrent = "LT"


class UpdateOptions(Enum):
    """
    Options for updating elements of a sorted set key.

    - LESS_THAN: Only update existing elements if the new score is less than the current score.
    - GREATER_THAN: Only update existing elements if the new score is greater than the current score.
    """

    LESS_THAN = "LT"
    GREATER_THAN = "GT"


class InfBound(Enum):
    """
    Enumeration representing positive and negative infinity bounds for sorted set scores.
    """

    POS_INF = "+inf"
    NEG_INF = "-inf"


class ScoreLimit:
    """
    Represents a score limit in a sorted set.

    Args:
        value (float): The score value.
        is_inclusive (bool): Whether the score value is inclusive. Defaults to False.
    """

    def __init__(self, value: float, is_inclusive: bool = True):
        """Convert the score limit to the Redis protocol format."""
        self.value = str(value) if is_inclusive else f"({value}"


class ExpirySet:
    """SET option: Represents the expiry type and value to be executed with "SET" command."""

    def __init__(
        self,
        expiry_type: ExpiryType,
        value: Optional[Union[int, datetime, timedelta]],
    ) -> None:
        """
        Args:
            - expiry_type (ExpiryType): The expiry type.
            - value (Optional[Union[int, datetime, timedelta]]): The value of the expiration type. The type of expiration
                determines the type of expiration value:
                - SEC: Union[int, timedelta]
                - MILLSEC: Union[int, timedelta]
                - UNIX_SEC: Union[int, datetime]
                - UNIX_MILLSEC: Union[int, datetime]
                - KEEP_TTL: Type[None]
        """
        self.set_expiry_type_and_value(expiry_type, value)

    def set_expiry_type_and_value(
        self, expiry_type: ExpiryType, value: Optional[Union[int, datetime, timedelta]]
    ):
        if not isinstance(value, get_args(expiry_type.value[1])):
            raise ValueError(
                f"The value of {expiry_type} should be of type {expiry_type.value[1]}"
            )
        self.expiry_type = expiry_type
        if self.expiry_type == ExpiryType.SEC:
            self.cmd_arg = "EX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds())
        elif self.expiry_type == ExpiryType.MILLSEC:
            self.cmd_arg = "PX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds() * 1000)
        elif self.expiry_type == ExpiryType.UNIX_SEC:
            self.cmd_arg = "EXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp())
        elif self.expiry_type == ExpiryType.UNIX_MILLSEC:
            self.cmd_arg = "PXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp() * 1000)
        elif self.expiry_type == ExpiryType.KEEP_TTL:
            self.cmd_arg = "KEEPTTL"
        self.value = str(value) if value else None

    def get_cmd_args(self) -> List[str]:
        return [self.cmd_arg] if self.value is None else [self.cmd_arg, self.value]


class CoreCommands(Protocol):
    async def _execute_command(
        self,
        request_type: RequestType.ValueType,
        args: List[str],
        route: Optional[Route] = ...,
    ) -> TResult: ...

    async def _execute_transaction(
        self,
        commands: List[Tuple[RequestType.ValueType, List[str]]],
        route: Optional[Route] = None,
    ) -> List[TResult]: ...

    async def _execute_script(
        self,
        hash: str,
        keys: Optional[List[str]] = None,
        args: Optional[List[str]] = None,
        route: Optional[Route] = None,
    ) -> TResult: ...

    async def set(
        self,
        key: str,
        value: str,
        conditional_set: Optional[ConditionalChange] = None,
        expiry: Optional[ExpirySet] = None,
        return_old_value: bool = False,
    ) -> Optional[str]:
        """Set the given key with the given value. Return value is dependent on the passed options.
            See https://redis.io/commands/set/ for details.

            @example - Set "foo" to "bar" only if "foo" already exists, and set the key expiration to 5 seconds:

                connection.set("foo", "bar", conditional_set=ConditionalChange.ONLY_IF_EXISTS, expiry=Expiry(ExpiryType.SEC, 5))
        Args:
            key (str): the key to store.
            value (str): the value to store with the given key.
            conditional_set (Optional[ConditionalChange], optional): set the key only if the given condition is met.
                Equivalent to [`XX` | `NX`] in the Redis API. Defaults to None.
            expiry (Optional[ExpirySet], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `KEEPTTL`] in the Redis API. Defaults to None.
            return_old_value (bool, optional): Return the old string stored at key, or None if key did not exist.
                An error is returned and SET aborted if the value stored at key is not a string.
                Equivalent to `GET` in the Redis API. Defaults to False.
        Returns:
            Optional[str]:
                If the value is successfully set, return OK.
                If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return None.
                If return_old_value is set, return the old value as a string.
        """
        args = [key, value]
        if conditional_set:
            args.append(conditional_set.value)
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return cast(
            Optional[str], await self._execute_command(RequestType.SetString, args)
        )

    async def get(self, key: str) -> Optional[str]:
        """Get the value associated with the given key, or null if no such value exists.
         See https://redis.io/commands/get/ for details.

        Args:
            key (str): the key to retrieve from the database

        Returns:
            Optional[str]: If the key exists, returns the value of the key as a string. Otherwise, return None.
        """
        return cast(
            Optional[str], await self._execute_command(RequestType.GetString, [key])
        )

    async def delete(self, keys: List[str]) -> int:
        """Delete one or more keys from the database. A key is ignored if it does not exist.
        See https://redis.io/commands/del/ for details.

        Args:
            keys (List[str]): A list of keys to be deleted from the database.

        Returns:
            int: The number of keys that were deleted.
        """
        return cast(int, await self._execute_command(RequestType.Del, keys))

    async def incr(self, key: str) -> int:
        """Increments the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/incr/ for more details.

        Args:
          key (str): The key to increment it's value.

          Returns:
              int: The value of `key` after the increment. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        return cast(int, await self._execute_command(RequestType.Incr, [key]))

    async def incrby(self, key: str, amount: int) -> int:
        """Increments the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation. See https://redis.io/commands/incrby/ for more details.

        Args:
          key (str): The key to increment it's value.
          amount (int) : The amount to increment.

          Returns:
              int: The value of key after the increment. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        return cast(
            int, await self._execute_command(RequestType.IncrBy, [key, str(amount)])
        )

    async def incrbyfloat(self, key: str, amount: float) -> float:
        """Increment the string representing a floating point number stored at `key` by `amount`.
           By using a negative increment value, the value stored at the `key` is decremented.
           If the key does not exist, it is set to 0 before performing the operation.
           See https://redis.io/commands/incrbyfloat/ for more details.

        Args:
          key (str): The key to increment it's value.
          amount (float) : The amount to increment.

          Returns:
              float: The value of key after the increment. An error is returned if the key contains a value
              of the wrong type.
        """
        return cast(
            float,
            await self._execute_command(RequestType.IncrByFloat, [key, str(amount)]),
        )

    async def mset(self, key_value_map: Mapping[str, str]) -> TOK:
        """Set multiple keys to multiple values in a single operation.
        See https://redis.io/commands/mset/ for more details.

        Args:
            parameters (Mapping[str, str]): A map of key value pairs.

        Returns:
            OK: a simple OK response.
        """
        parameters: List[str] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        return cast(TOK, await self._execute_command(RequestType.MSet, parameters))

    async def mget(self, keys: List[str]) -> List[str]:
        """Retrieve the values of multiple keys.
        See https://redis.io/commands/mget/ for more details.

        Args:
            keys (List[str]): A list of keys to retrieve values for.

        Returns:
            List[str]: A list of values corresponding to the provided keys. If a key is not found,
            its corresponding value in the list will be None.
        """
        return cast(List[str], await self._execute_command(RequestType.MGet, keys))

    async def decr(self, key: str) -> int:
        """Decrement the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/decr/ for more details.

        Args:
          key (str): The key to increment it's value.

          Returns:
              int: The value of key after the decrement. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        return cast(int, await self._execute_command(RequestType.Decr, [key]))

    async def decrby(self, key: str, amount: int) -> int:
        """Decrements the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://redis.io/commands/decrby/ for more details.

        Args:
          key (str): The key to decrement it's value.
          amount (int) : The amount to decrement.

          Returns:
              int: The value of key after the decrement. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        return cast(
            int, await self._execute_command(RequestType.DecrBy, [key, str(amount)])
        )

    async def hset(self, key: str, field_value_map: Mapping[str, str]) -> int:
        """Sets the specified fields to their respective values in the hash stored at `key`.
        See https://redis.io/commands/hset/ for more details.

        Args:
            key (str): The key of the hash.
            field_value_map (Mapping[str, str]): A field-value map consisting of fields and their corresponding values
            to be set in the hash stored at the specified key.

        Returns:
            int: The number of fields that were added or modified in the hash.

        Example:
            >>> hset("my_hash", {"field": "value", "field2": "value2"})
                2
        """
        field_value_list: List[str] = [key]
        for pair in field_value_map.items():
            field_value_list.extend(pair)
        return cast(
            int,
            await self._execute_command(RequestType.HashSet, field_value_list),
        )

    async def hget(self, key: str, field: str) -> Optional[str]:
        """Retrieves the value associated with field in the hash stored at `key`.
        See https://redis.io/commands/hget/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field whose value should be retrieved.

        Returns:
            Optional[str]: The value associated with the specified field in the hash.
            Returns None if the field or key does not exist.

        Examples:
            >>> hget("my_hash", "field")
                "value"
            >>> hget("my_hash", "nonexistent_field")
                None
        """
        return cast(
            Optional[str],
            await self._execute_command(RequestType.HashGet, [key, field]),
        )

    async def hincrby(self, key: str, field: str, amount: int) -> int:
        """Increment or decrement the value of a `field` in the hash stored at `key` by the specified amount.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/hincrby/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash stored at `key` to increment or decrement its value.
            amount (int): The amount by which to increment or decrement the field's value.
                Use a negative value to decrement.

        Returns:
            int: The value of the specified field in the hash stored at `key` after the increment or decrement.
                An error will be returned if `key` holds a value of an incorrect type (not a string) or if it contains a string
                that cannot be represented as an integer.

        Examples:
            >>> await client.hincrby("my_hash", "field1", 5)
                5

        """
        return cast(
            int,
            await self._execute_command(
                RequestType.HashIncrBy, [key, field, str(amount)]
            ),
        )

    async def hincrbyfloat(self, key: str, field: str, amount: float) -> float:
        """Increment or decrement the floating-point value stored at `field` in the hash stored at `key` by the specified
        amount.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/hincrbyfloat/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash stored at `key` to increment or decrement its value.
            amount (float): The amount by which to increment or decrement the field's value.
                Use a negative value to decrement.

        Returns:
            float: The value of the specified field in the hash stored at `key` after the increment as a string.
                An error is returned if `key` contains a value of the wrong type or the current field content is not
                parsable as a double precision floating point number.

        Examples:
            >>> await client.hincrbyfloat("my_hash", "field1", 2.5)
                "2.5"
        """
        return cast(
            float,
            await self._execute_command(
                RequestType.HashIncrByFloat, [key, field, str(amount)]
            ),
        )

    async def hexists(self, key: str, field: str) -> bool:
        """Check if a field exists in the hash stored at `key`.
        See https://redis.io/commands/hexists/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field to check in the hash stored at `key`.

        Returns:
            bool: Returns 'True' if the hash contains the specified field. If the hash does not contain the field,
                or if the key does not exist, it returns 'False'.
                If `key` holds a value that is not a hash, an error is returned.

        Examples:
            >>> await client.hexists("my_hash", "field1")
                1
            >>> await client.hexists("my_hash", "nonexistent_field")
                0
        """
        return cast(
            bool, await self._execute_command(RequestType.HashExists, [key, field])
        )

    async def hgetall(self, key: str) -> Dict[str, str]:
        """Returns all fields and values of the hash stored at `key`.
        See https://redis.io/commands/hgetall/ for details.

        Args:
            key (str): The key of the hash.

        Returns:
            Dict[str, str]: A dictionary of fields and their values stored in the hash. Every field name in the list is followed by
            its value. If `key` does not exist, it returns an empty dictionary.
            If `key` holds a value that is not a hash, an error is returned.

        Examples:
            >>> await client.hgetall("my_hash")
                {"field1": "value1", "field2": "value2"}
        """
        return cast(
            Dict[str, str], await self._execute_command(RequestType.HashGetAll, [key])
        )

    async def hmget(self, key: str, fields: List[str]) -> List[Optional[str]]:
        """Retrieve the values associated with specified fields in the hash stored at `key`.
        See https://redis.io/commands/hmget/ for details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields in the hash stored at `key` to retrieve from the database.

        Returns:
            List[Optional[str]]: A list of values associated with the given fields, in the same order as they are requested.
            For every field that does not exist in the hash, a null value is returned.
            If the key does not exist, it is treated as an empty hash, and the function returns a list of null values.
            If `key` holds a value that is not a hash, an error is returned.

        Examples:
            >>> await client.hmget("my_hash", ["field1", "field2"])
                ["value1", "value2"]  # A list of values associated with the specified fields.
        """
        return cast(
            List[Optional[str]],
            await self._execute_command(RequestType.HashMGet, [key] + fields),
        )

    async def hdel(self, key: str, fields: List[str]) -> int:
        """Remove specified fields from the hash stored at `key`.
        See https://redis.io/commands/hdel/ for more details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields to remove from the hash stored at `key`.

        Returns:
            int: The number of fields that were removed from the hash, excluding specified but non-existing fields.
            If the key does not exist, it is treated as an empty hash, and the function returns 0.
            If `key` holds a value that is not a hash, an error is returned.


        Examples:
            >>> await client.hdel("my_hash", ["field1", "field2"])
                2  # Indicates that two fields were successfully removed from the hash.
        """
        return cast(
            int, await self._execute_command(RequestType.HashDel, [key] + fields)
        )

    async def lpush(self, key: str, elements: List[str]) -> int:
        """Insert all the specified values at the head of the list stored at `key`.
        `elements` are inserted one after the other to the head of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/lpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the head of the list stored at `key`.

        Returns:
            int: The length of the list after the push operations.
                If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.lpush("my_list", ["value1", "value2"])
                2
            >>> await client.lpush("nonexistent_list", ["new_value"])
                1
        """
        return cast(
            int, await self._execute_command(RequestType.LPush, [key] + elements)
        )

    async def lpop(self, key: str) -> Optional[str]:
        """Remove and return the first elements of the list stored at `key`.
        The command pops a single element from the beginning of the list.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.

        Returns:
            Optional[str]: The value of the first element.
            If `key` does not exist, None will be returned.
            If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.lpop("my_list")
                "value1"
            >>> await client.lpop("non_exiting_key")
                None
        """

        return cast(
            Optional[str],
            await self._execute_command(RequestType.LPop, [key]),
        )

    async def lpop_count(self, key: str, count: int) -> Optional[List[str]]:
        """Remove and return up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.
            count (int): The count of elements to pop from the list.

        Returns:
            Optional[List[str]]: A a list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.
            If `key` holds a value that is not a list, an error is returned.

        Examples:

            >>> await client.lpop("my_list", 2)
                ["value1", "value2"]
            >>> await client.lpop("non_exiting_key" , 3)
                None
        """

        return cast(
            Optional[List[str]],
            await self._execute_command(RequestType.LPop, [key, str(count)]),
        )

    async def lrange(self, key: str, start: int, end: int) -> List[str]:
        """Retrieve the specified elements of the list stored at `key` within the given range.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list,
        with -1 being the last element of the list, -2 being the penultimate, and so on.
        See https://redis.io/commands/lrange/ for details.

        Args:
            key (str): The key of the list.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Returns:
            List[str]: A list of elements within the specified range.
            If `start` exceeds the `end` of the list, or if `start` is greater than `end`, an empty list will be returned.
            If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
            If `key` does not exist an empty list will be returned.
            If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.lrange("my_list", 0, 2)
                ["value1", "value2", "value3"]
            >>> await client.lrange("my_list", -2, -1)
                ["value2", "value3"]
            >>> await client.lrange("non_exiting_key", 0, 2)
                []
        """

        return cast(
            List[str],
            await self._execute_command(
                RequestType.LRange, [key, str(start), str(end)]
            ),
        )

    async def rpush(self, key: str, elements: List[str]) -> int:
        """Inserts all the specified values at the tail of the list stored at `key`.
        `elements` are inserted one after the other to the tail of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/rpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the tail of the list stored at `key`.

        Returns:
            int: The length of the list after the push operations.
                If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.rpush("my_list", ["value1", "value2"])
                2
            >>> await client.rpush("nonexistent_list", ["new_value"])
                1
        """
        return cast(
            int, await self._execute_command(RequestType.RPush, [key] + elements)
        )

    async def rpop(self, key: str, count: Optional[int] = None) -> Optional[str]:
        """Removes and returns the last elements of the list stored at `key`.
        The command pops a single element from the end of the list.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.

        Returns:
            Optional[str]: The value of the last element.
            If `key` does not exist, None will be returned.
            If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.rpop("my_list")
                "value1"
            >>> await client.rpop("non_exiting_key")
                None
        """

        return cast(
            Optional[str],
            await self._execute_command(RequestType.RPop, [key]),
        )

    async def rpop_count(self, key: str, count: int) -> Optional[List[str]]:
        """Removes and returns up to `count` elements from the list stored at `key`, depending on the list's length.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.
            count (int): The count of elements to pop from the list.

        Returns:
            Optional[List[str]: A list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.
            If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.rpop("my_list", 2)
                ["value1", "value2"]
            >>> await client.rpop("non_exiting_key" , 7)
                None
        """

        return cast(
            Optional[List[str]],
            await self._execute_command(RequestType.RPop, [key, str(count)]),
        )

    async def sadd(self, key: str, members: List[str]) -> int:
        """Add specified members to the set stored at `key`.
        Specified members that are already a member of this set are ignored.
        If `key` does not exist, a new set is created before adding `members`.
        See https://redis.io/commands/sadd/ for more details.

        Args:
            key (str): The key where members will be added to its set.
            members (List[str]): A list of members to add to the set stored at `key`.

        Returns:
            int: The number of members that were added to the set, excluding members already present.
                If `key` holds a value that is not a set, an error is returned.

        Examples:
            >>> await client.sadd("my_set", ["member1", "member2"])
                2
        """
        return cast(int, await self._execute_command(RequestType.SAdd, [key] + members))

    async def srem(self, key: str, members: List[str]) -> int:
        """Remove specified members from the set stored at `key`.
        Specified members that are not a member of this set are ignored.
        See https://redis.io/commands/srem/ for details.

        Args:
            key (str): The key from which members will be removed.
            members (List[str]): A list of members to remove from the set stored at `key`.

        Returns:
            int: The number of members that were removed from the set, excluding non-existing members.
                If `key` does not exist, it is treated as an empty set and this command returns 0.
                If `key` holds a value that is not a set, an error is returned.

        Examples:
            >>> await client.srem("my_set", ["member1", "member2"])
                2
        """
        return cast(int, await self._execute_command(RequestType.SRem, [key] + members))

    async def smembers(self, key: str) -> Set[str]:
        """Retrieve all the members of the set value stored at `key`.
        See https://redis.io/commands/smembers/ for details.

        Args:
            key (str): The key from which to retrieve the set members.

        Returns:
            Set[str]: A set of all members of the set.
                If `key` does not exist an empty list will be returned.
                If `key` holds a value that is not a set, an error is returned.

        Examples:
            >>> await client.smembers("my_set")
                {"member1", "member2", "member3"}
        """
        return cast(Set[str], await self._execute_command(RequestType.SMembers, [key]))

    async def scard(self, key: str) -> int:
        """Retrieve the set cardinality (number of elements) of the set stored at `key`.
        See https://redis.io/commands/scard/ for details.

        Args:
            key (str): The key from which to retrieve the number of set members.

        Returns:
            int: The cardinality (number of elements) of the set, or 0 if the key does not exist.
                If `key` holds a value that is not a set, an error is returned.

        Examples:
            >>> await client.scard("my_set")
                3
        """
        return cast(int, await self._execute_command(RequestType.SCard, [key]))

    async def ltrim(self, key: str, start: int, end: int) -> TOK:
        """Trim an existing list so that it will contain only the specified range of elements specified.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on.
        These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being the last
        element of the list, -2 being the penultimate, and so on.
        See https://redis.io/commands/ltrim/ for more details.

        Args:
            key (str): The key of the list.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Returns:
            TOK: A simple "OK" response.
                If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list
                (which causes `key` to be removed).
                If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
                If `key` does not exist, "OK" will be returned without changes to the database.
                If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.ltrim("my_list", 0, 1)
                "OK"  # Indicates that the list has been trimmed to contain elements from 0 to 1.
        """
        return cast(
            TOK,
            await self._execute_command(RequestType.LTrim, [key, str(start), str(end)]),
        )

    async def lrem(self, key: str, count: int, element: str) -> int:
        """Removes the first `count` occurrences of elements equal to `element` from the list stored at `key`.
        If `count` is positive, it removes elements equal to `element` moving from head to tail.
        If `count` is negative, it removes elements equal to `element` moving from tail to head.
        If `count` is 0 or greater than the occurrences of elements equal to `element`, it removes all elements
        equal to `element`.
        See https://redis.io/commands/lrem/ for more details.

        Args:
            key (str): The key of the list.
            count (int): The count of occurrences of elements equal to `element` to remove.
            element (str): The element to remove from the list.

        Returns:
            int: The number of removed elements.
                If `key` does not exist, 0 is returned.
                If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.lrem("my_list", 2, "value")
                2  # Removes the first 2 occurrences of "value" in the list.
        """
        return cast(
            int,
            await self._execute_command(RequestType.LRem, [key, str(count), element]),
        )

    async def llen(self, key: str) -> int:
        """Get the length of the list stored at `key`.
        See https://redis.io/commands/llen/ for details.

        Args:
            key (str): The key of the list.

        Returns:
            int: The length of the list at the specified key.
                If `key` does not exist, it is interpreted as an empty list and 0 is returned.
                If `key` holds a value that is not a list, an error is returned.

        Examples:
            >>> await client.llen("my_list")
                3  # Indicates that there are 3 elements in the list.
        """
        return cast(int, await self._execute_command(RequestType.LLen, [key]))

    async def exists(self, keys: List[str]) -> int:
        """Returns the number of keys in `keys` that exist in the database.
        See https://redis.io/commands/exists/ for more details.

        Args:
            keys (List[str]): The list of keys to check.

        Returns:
            int: The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
                it will be counted multiple times.

        Examples:
            >>> await client.exists(["key1", "key2", "key3"])
                3  # Indicates that all three keys exist in the database.
        """
        return cast(int, await self._execute_command(RequestType.Exists, keys))

    async def unlink(self, keys: List[str]) -> int:
        """Unlink (delete) multiple keys from the database.
        A key is ignored if it does not exist.
        This command, similar to DEL, removes specified keys and ignores non-existent ones.
        However, this command does not block the server, while [DEL](https://redis.io/commands/del) does.
        See https://redis.io/commands/unlink/ for more details.

        Args:
            keys (List[str]): The list of keys to unlink.

        Returns:
            int: The number of keys that were unlinked.

        Examples:
            >>> await client.unlink(["key1", "key2", "key3"])
                3  # Indicates that all three keys were unlinked from the database.
        """
        return cast(int, await self._execute_command(RequestType.Unlink, keys))

    async def expire(
        self, key: str, seconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `seconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/expire/ for more details.

        Args:
            key (str): The key to set a timeout on.
            seconds (int): The timeout in seconds.
            option (ExpireOptions, optional): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.expire("my_key", 60)
                1  # Indicates that a timeout of 60 seconds has been set for "my_key."
        """
        args: List[str] = (
            [key, str(seconds)] if option is None else [key, str(seconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.Expire, args))

    async def expireat(
        self, key: str, unix_seconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` using an absolute Unix timestamp (seconds since January 1, 1970) instead of specifying the
        number of seconds.
        A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be
        deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/expireat/ for more details.

        Args:
            key (str): The key to set a timeout on.
            unix_seconds (int): The timeout in an absolute Unix timestamp.
            option (Optional[ExpireOptions]): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.expireAt("my_key", 1672531200, ExpireOptions.HasNoExpiry)
                1
        """
        args = (
            [key, str(unix_seconds)]
            if option is None
            else [key, str(unix_seconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.ExpireAt, args))

    async def pexpire(
        self, key: str, milliseconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` in milliseconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `milliseconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/pexpire/ for more details.

        Args:
            key (str): The key to set a timeout on.
            milliseconds (int): The timeout in milliseconds.
            option (Optional[ExpireOptions]): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.pexpire("my_key", 60000, ExpireOptions.HasNoExpiry)
                1  # Indicates that a timeout of 60,000 milliseconds has been set for "my_key."
        """
        args = (
            [key, str(milliseconds)]
            if option is None
            else [key, str(milliseconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.PExpire, args))

    async def pexpireat(
        self, key: str, unix_milliseconds: int, option: Optional[ExpireOptions] = None
    ) -> bool:
        """
        Sets a timeout on `key` using an absolute Unix timestamp in milliseconds (milliseconds since January 1, 1970) instead
        of specifying the number of milliseconds.
        A timestamp in the past will delete the key immediately. After the timeout has expired, the key will automatically be
        deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/pexpireat/ for more details.

        Args:
            key (str): The key to set a timeout on.
            unix_milliseconds (int): The timeout in an absolute Unix timestamp in milliseconds.
            option (Optional[ExpireOptions]): The expire option.

        Returns:
            bool: 'True' if the timeout was set, 'False' if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        Examples:
            >>> await client.pexpireAt("my_key", 1672531200000, ExpireOptions.HasNoExpiry)
                1
        """
        args = (
            [key, str(unix_milliseconds)]
            if option is None
            else [key, str(unix_milliseconds), option.value]
        )
        return cast(bool, await self._execute_command(RequestType.PExpireAt, args))

    async def ttl(self, key: str) -> int:
        """
        Returns the remaining time to live of `key` that has a timeout.
        See https://redis.io/commands/ttl/ for more details.

        Args:
            key (str): The key to return its timeout.

        Returns:
            int: TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.

        Examples:
            >>> await client.ttl("my_key")
                3600  # Indicates that "my_key" has a remaining time to live of 3600 seconds.
            >>> await client.ttl("nonexistent_key")
                -2  # Returns -2 for a non-existing key.
        """
        return cast(int, await self._execute_command(RequestType.TTL, [key]))

    async def zadd(
        self,
        key: str,
        members_scores: Mapping[str, float],
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
        changed: bool = False,
    ) -> int:
        """
        Adds members with their scores to the sorted set stored at `key`.
        If a member is already a part of the sorted set, its score is updated.

        See https://redis.io/commands/zadd/ for more details.

        Args:
            key (str): The key of the sorted set.
            members_scores (Mapping[str, float]): A mapping of members to their corresponding scores.
            existing_options (Optional[ConditionalChange]): Options for handling existing members.
                - NX: Only add new elements.
                - XX: Only update existing elements.
            update_condition (Optional[UpdateOptions]): Options for updating scores.
                - GT: Only update scores greater than the current values.
                - LT: Only update scores less than the current values.
            changed (bool): Modify the return value to return the number of changed elements, instead of the number of new elements added.

        Returns:
            int: The number of elements added to the sorted set.
            If `changed` is set, returns the number of elements updated in the sorted set.
            If `key` holds a value that is not a sorted set, an error is returned.

        Examples:
            >>> await zadd("my_sorted_set", {"member1": 10.5, "member2": 8.2})
                2  # Indicates that two elements have been added or updated in the sorted set "my_sorted_set."
            >>> await zadd("existing_sorted_set", {"member1": 15.0, "member2": 5.5}, existing_options=ConditionalChange.XX)
                2  # Updates the scores of two existing members in the sorted set "existing_sorted_set."
        """
        args = [key]
        if existing_options:
            args.append(existing_options.value)

        if update_condition:
            args.append(update_condition.value)

        if changed:
            args.append("CH")

        if existing_options and update_condition:
            if existing_options == ConditionalChange.ONLY_IF_DOES_NOT_EXIST:
                raise ValueError(
                    "The GT, LT and NX options are mutually exclusive. "
                    f"Cannot choose both {update_condition.value} and NX."
                )

        members_scores_list = [
            str(item) for pair in members_scores.items() for item in pair[::-1]
        ]
        args += members_scores_list

        return cast(
            int,
            await self._execute_command(RequestType.Zadd, args),
        )

    async def zadd_incr(
        self,
        key: str,
        member: str,
        increment: float,
        existing_options: Optional[ConditionalChange] = None,
        update_condition: Optional[UpdateOptions] = None,
    ) -> Optional[float]:
        """
        Increments the score of member in the sorted set stored at `key` by `increment`.
        If `member` does not exist in the sorted set, it is added with `increment` as its score (as if its previous score was 0.0).
        If `key` does not exist, a new sorted set with the specified member as its sole member is created.

        See https://redis.io/commands/zadd/ for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): A member in the sorted set to increment.
            increment (float): The score to increment the member.
            existing_options (Optional[ConditionalChange]): Options for handling the member's existence.
                - NX: Only increment a member that doesn't exist.
                - XX: Only increment an existing member.
            update_condition (Optional[UpdateOptions]): Options for updating the score.
                - GT: Only increment the score of the member if the new score will be greater than the current score.
                - LT: Only increment (decrement) the score of the member if the new score will be less than the current score.

        Returns:
            Optional[float]: The score of the member.
            If there was a conflict with choosing the XX/NX/LT/GT options, the operation aborts and None is returned.
            If `key` holds a value that is not a sorted set, an error is returned.

        Examples:
            >>> await zaddIncr("my_sorted_set", member , 5.0)
                5.0
            >>> await zaddIncr("existing_sorted_set", member , "3.0" , UpdateOptions.LESS_THAN)
                None
        """
        args = [key]
        if existing_options:
            args.append(existing_options.value)

        if update_condition:
            args.append(update_condition.value)

        args.append("INCR")

        if existing_options and update_condition:
            if existing_options == ConditionalChange.ONLY_IF_DOES_NOT_EXIST:
                raise ValueError(
                    "The GT, LT and NX options are mutually exclusive. "
                    f"Cannot choose both {update_condition.value} and NX."
                )

        args += [str(increment), member]
        return cast(
            Optional[float],
            await self._execute_command(RequestType.Zadd, args),
        )

    async def zcard(self, key: str) -> int:
        """
        Returns the cardinality (number of elements) of the sorted set stored at `key`.

        See https://redis.io/commands/zcard/ for more details.

        Args:
            key (str): The key of the sorted set.

        Returns:
            int: The number of elements in the sorted set.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
            If `key` holds a value that is not a sorted set, an error is returned.

        Examples:
            >>> await zcard("my_sorted_set")
                3  # Indicates that there are 3 elements in the sorted set "my_sorted_set".
            >>> await zcard("non_existing_key")
                0
        """
        return cast(int, await self._execute_command(RequestType.Zcard, [key]))

    async def zcount(
        self,
        key: str,
        min_score: Union[InfBound, ScoreLimit],
        max_score: Union[InfBound, ScoreLimit],
    ) -> int:
        """
        Returns the number of members in the sorted set stored at `key` with scores between `min_score` and `max_score`.

        See https://redis.io/commands/zcount/ for more details.

        Args:
            key (str): The key of the sorted set.
            min_score (Union[InfBound, ScoreLimit]): The minimum score to count from.
                Can be an instance of InfBound representing positive/negative infinity,
                or ScoreLimit representing a specific score and inclusivity.
            max_score (Union[InfBound, ScoreLimit]): The maximum score to count up to.
                Can be an instance of InfBound representing positive/negative infinity,
                or ScoreLimit representing a specific score and inclusivity.

        Returns:
            int: The number of members in the specified score range.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
            If `max_score` < `min_score`, 0 is returned.
            If `key` holds a value that is not a sorted set, an error is returned.

        Examples:
            >>> await zcount("my_sorted_set", ScoreLimit(5.0 , is_inclusive=true) , InfBound.POS_INF)
                2  # Indicates that there are 2 members with scores between 5.0 (not exclusive) and +inf in the sorted set "my_sorted_set".
            >>> await zcount("my_sorted_set", ScoreLimit(5.0 , is_inclusive=true) , ScoreLimit(10.0 , is_inclusive=false))
                1  # Indicates that there is one ScoreLimit with 5.0 < score <= 10.0 in the sorted set "my_sorted_set".
        """
        return cast(
            int,
            await self._execute_command(
                RequestType.Zcount, [key, min_score.value, max_score.value]
            ),
        )

    async def zrem(
        self,
        key: str,
        members: List[str],
    ) -> int:
        """
        Removes the specified members from the sorted set stored at `key`.
        Specified members that are not a member of this set are ignored.

        See https://redis.io/commands/zrem/ for more details.

        Args:
            key (str): The key of the sorted set.
            members (List[str]): A list of members to remove from the sorted set.

        Returns:
            int: The number of members that were removed from the sorted set, not including non-existing members.
            If `key` does not exist, it is treated as an empty sorted set, and the command returns 0.
            If `key` holds a value that is not a sorted set, an error is returned.

        Examples:
            >>> await zrem("my_sorted_set", ["member1", "member2"])
                2  # Indicates that two members have been removed from the sorted set "my_sorted_set."
            >>> await zrem("non_existing_sorted_set", ["member1", "member2"])
                0  # Indicates that no members were removed as the sorted set "non_existing_sorted_set" does not exist.
        """
        return cast(
            int,
            await self._execute_command(RequestType.Zrem, [key] + members),
        )

    async def zscore(self, key: str, member: str) -> Optional[float]:
        """
        Returns the score of `member` in the sorted set stored at `key`.

        See https://redis.io/commands/zscore/ for more details.

        Args:
            key (str): The key of the sorted set.
            member (str): The member whose score is to be retrieved.

        Returns:
            Optional[float]: The score of the member.
            If `member` does not exist in the sorted set, None is returned.
            If `key` does not exist,  None is returned.
            If `key` holds a value that is not a sorted set, an error is returned.

        Examples:
            >>> await zscore("my_sorted_set", "member")
                10.5  # Indicates that the score of "member" in the sorted set "my_sorted_set" is 10.5.
            >>> await zscore("my_sorted_set", "non_existing_member")
                None
        """
        return cast(
            Optional[float],
            await self._execute_command(RequestType.ZScore, [key, member]),
        )

    async def invoke_script(
        self,
        script: Script,
        keys: Optional[List[str]] = None,
        args: Optional[List[str]] = None,
    ) -> TResult:
        """
        Invokes a Lua script with its keys and arguments.
        This method simplifies the process of invoking scripts on a Redis server by using an object that represents a Lua script.
        The script loading, argument preparation, and execution will all be handled internally.
        If the script has not already been loaded, it will be loaded automatically using the Redis `SCRIPT LOAD` command.
        After that, it will be invoked using the Redis `EVALSHA` command.

        Args:
            script (Script): The Lua script to execute.
            keys (List[str]): The keys that are used in the script.
            args (List[str]): The arguments for the script.

        Returns:
            TResult: a value that depends on the script that was executed.

        Examples:
            >>> lua_script = Script("return { KEYS[1], ARGV[1] }")
            >>> await invoke_script(lua_script, keys=["foo"], args=["bar"] );
                ["foo", "bar"]
        """
        return await self._execute_script(script.get_hash(), keys, args)
