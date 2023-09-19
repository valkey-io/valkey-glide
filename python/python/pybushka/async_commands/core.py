import threading
from datetime import datetime, timedelta
from enum import Enum
from typing import List, Mapping, Optional, Protocol, Tuple, Type, Union, cast, get_args

from pybushka.constants import TOK, TResult
from pybushka.protobuf.redis_request_pb2 import RequestType
from pybushka.routes import Route
from pyparsing import Any


class ConditionalSet(Enum):
    """SET option: A condition to the "SET" command.
    - ONLY_IF_EXISTS - Only set the key if it already exist. Equivalent to `XX` in the Redis API
    - ONLY_IF_DOES_NOT_EXIST - Only set the key if it does not already exist. Equivalent to `NX` in the Redis API
    """

    ONLY_IF_EXISTS = 0  # Equivalent to `XX` in the Redis API
    ONLY_IF_DOES_NOT_EXIST = 1  # Equivalent to `NX` in the Redis API


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


class ExpirySet:
    """SET option: Represents the expiry type and value to be executed with "SET" command."""

    def __init__(
        self,
        expiry_type: ExpiryType,
        value: Union[int, datetime, timedelta, None],
    ) -> None:
        """
        Args:
            - expiry_type (ExpiryType): The expiry type.
            - value (Union[int, datetime, timedelta, None]): The value of the expiration type. The type of expiration
                determines the type of expiration value:
                - SEC: Union[int, timedelta]
                - MILLSEC: Union[int, timedelta]
                - UNIX_SEC: Union[int, datetime]
                - UNIX_MILLSEC: Union[int, datetime]
                - KEEP_TTL: Type[None]
        """
        self.set_expiry_type_and_value(expiry_type, value)

    def set_expiry_type_and_value(
        self, expiry_type: ExpiryType, value: Union[int, datetime, timedelta, None]
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


class BaseTransaction:
    """
    Base class encompassing shared commands for both Client and ClusterClient implementations in transaction.
    """

    def __init__(self) -> None:
        self.commands: List[Tuple[RequestType.ValueType, List[str]]] = []
        self.lock = threading.Lock()

    def append_command(self, request_type: RequestType.ValueType, args: List[str]):
        self.lock.acquire()
        try:
            self.commands.append((request_type, args))
        finally:
            self.lock.release()

    def dispose(self):
        with self.lock:
            self.commands.clear()

    def get(self, key: str):
        self.append_command(RequestType.GetString, [key])

    def set(
        self,
        key: str,
        value: str,
        conditional_set: Union[ConditionalSet, None] = None,
        expiry: Union[ExpirySet, None] = None,
        return_old_value: bool = False,
    ):
        args = [key, value]
        if conditional_set:
            if conditional_set == ConditionalSet.ONLY_IF_EXISTS:
                args.append("XX")
            if conditional_set == ConditionalSet.ONLY_IF_DOES_NOT_EXIST:
                args.append("NX")
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        self.append_command(RequestType.SetString, args)

    def custom_command(self, command_args: List[str]):
        """Executes a single command, without checking inputs.
            @example - Append a command to list of all pub/sub clients:

                transaction.customCommand(["CLIENT", "LIST","TYPE", "PUBSUB"])
        Args:
            command_args (List[str]): List of strings of the command's arguments.
            Every part of the command, including the command name and subcommands, should be added as a separate value in args.

        Command response:
            TResult: The returning value depends on the executed command
        """
        self.append_command(RequestType.CustomCommand, command_args)

    def info(
        self,
        sections: Optional[List[InfoSection]] = None,
    ):
        """Get information and statistics about the Redis server.
        See https://redis.io/commands/info/ for details.
        Args:
            sections (Optional[List[InfoSection]]): A list of InfoSection values specifying which sections of
            information to retrieve. When no parameter is provided, the default option is assumed.
        Command response:
            str: Returns a string containing the information for the sections requested.
        """
        args = [section.value for section in sections] if sections else []
        self.append_command(RequestType.Info, args)

    def delete(self, keys: List[str]):
        """Delete one or more keys from the database. A key is ignored if it does not exist.
        See https://redis.io/commands/del/ for details.

        Args:
            keys (List[str]): A list of keys to be deleted from the database.

        Command response:
            int: The number of keys that were deleted.
        """
        self.append_command(RequestType.Del, keys)

    def config_get(self, parameters: List[str]):
        """Get the values of configuration parameters.
        See https://redis.io/commands/config-get/ for details.

        Args:
            parameters (List[str]): A list of configuration parameter names to retrieve values for.

        Command response:
            List[str]: A list of values corresponding to the configuration parameters.
        """
        self.append_command(RequestType.ConfigGet, parameters)

    def config_set(self, parameters_map: Mapping[str, str]):
        """Set configuration parameters to the specified values.
        See https://redis.io/commands/config-set/ for details.

        Args:
            parameters_map (Mapping[str, str]): A map consisting of configuration
            parameters and their respective values to set.
        Command response:
            OK: Returns OK if all configurations have been successfully set. Otherwise, raises an error.
        """
        parameters: List[str] = []
        for pair in parameters_map.items():
            parameters.extend(pair)
        self.append_command(RequestType.ConfigSet, parameters)

    def config_resetstat(self):
        """Reset the statistics reported by Redis.
        See https://redis.io/commands/config-resetstat/ for details.
        Command response:
            OK: Returns "OK" to confirm that the statistics were successfully reset.
        """
        self.append_command(RequestType.ConfigResetStat, [])

    def mset(self, key_value_map: Mapping[str, str]):
        """Set multiple keys to multiple values in a single operation.
        See https://redis.io/commands/mset/ for more details.

        Args:
            parameters (Mapping[str, str]): A map of key value pairs.

        Command response:
            OK: a simple OK response.
        """
        parameters: List[str] = []
        for pair in key_value_map.items():
            parameters.extend(pair)
        self.append_command(RequestType.MSet, parameters)

    def mget(self, keys: List[str]):
        """Retrieve the values of multiple keys.
        See https://redis.io/commands/mget/ for more details.

        Args:
            keys (List[str]): A list of keys to retrieve values for.

        Command response:
            List[str]: A list of values corresponding to the provided keys. If a key is not found,
            its corresponding value in the list will be None.
        """
        self.append_command(RequestType.MGet, keys)

    def config_rewrite(self):
        """Rewrite the configuration file with the current configuration.
        See https://redis.io/commands/config-rewrite/ for details.

        Command response:
            OK: OK is returned when the configuration was rewritten properly. Otherwise an error is returned.
        """
        self.append_command(RequestType.ConfigRewrite, [])

    def client_id(self):
        """Returns the current connection id.
        See https://redis.io/commands/client-id/ for more information.


        Command response:
            int: the id of the client.
        """
        self.append_command(RequestType.ClientId, [])

    def incr(self, key: str):
        """Increments the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/incr/ for more details.

        Args:
          key (str): The key to increment it's value.

        Command response:
              int: the value of `key` after the increment. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        self.append_command(RequestType.Incr, [key])

    def incrby(self, key: str, amount: int):
        """Increments the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://redis.io/commands/incrby/ for more details.

        Args:
          key (str): The key to increment it's value.
          amount (int) : The amount to increment.

        Command response:
              int: The value of `key` after the increment. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        self.append_command(RequestType.IncrBy, [key, str(amount)])

    def incrbyfloat(self, key: str, amount: float):
        """Increment the string representing a floating point number stored at `key` by `amount`.
           By using a negative increment value, the value stored at the `key` is decremented.
           If the key does not exist, it is set to 0 before performing the operation.
           See https://redis.io/commands/incrbyfloat/ for more details.

        Args:
          key (str): The key to increment it's value.
          amount (float) : The amount to increment.

        Command response:
              str: The value of key after the increment. An error is returned if the key contains a value
              of the wrong type.
        """
        self.append_command(RequestType.IncrByFloat, [key, str(amount)])

    def ping(self, message: Optional[str] = None):
        """Ping the Redis server.
        See https://redis.io/commands/ping/ for more details.
        Args:
           message (Optional[str]): An optional message to include in the PING command. If not provided,
            the server will respond with "PONG". If provided, the server will respond with a copy of the message.

        Command response:
           str: "PONG" if 'message' is not provided, otherwise return a copy of 'message'.
        """
        argument = [] if message is None else [message]
        self.append_command(RequestType.Ping, argument)

    def decr(self, key: str):
        """Decrements the number stored at `key` by one. If the key does not exist, it is set to 0 before performing the
        operation.
        See https://redis.io/commands/decr/ for more details.

        Args:
          key (str): The key to decrement it's value.

        Command response:
              int: the value of `key` after the decrement. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        self.append_command(RequestType.Decr, [key])

    def decrby(self, key: str, amount: int):
        """Decrements the number stored at `key` by `amount`. If the key does not exist, it is set to 0 before performing
        the operation.
        See https://redis.io/commands/decrby/ for more details.

        Args:
          key (str): The key to decrement it's value.
         amount (int) : The amount to decrement.

        Command response:
              int: The value of `key` after the decrement. An error is returned if the key contains a value
              of the wrong type or contains a string that can not be represented as integer.
        """
        self.append_command(RequestType.DecrBy, [key, str(amount)])


class CoreCommands(Protocol):
    async def _execute_command(
        self, request_type: Any, args: List[str], route: Optional[Route] = ...
    ) -> TResult:
        ...

    async def execute_transaction(
        self,
        commands: List[Tuple[RequestType.ValueType, List[str]]],
        route: Optional[Route] = None,
    ) -> List[TResult]:
        ...

    async def set(
        self,
        key: str,
        value: str,
        conditional_set: Union[ConditionalSet, None] = None,
        expiry: Union[ExpirySet, None] = None,
        return_old_value: bool = False,
    ) -> TResult:
        """Set the given key with the given value. Return value is dependent on the passed options.
            See https://redis.io/commands/set/ for details.

            @example - Set "foo" to "bar" only if "foo" already exists, and set the key expiration to 5 seconds:

                connection.set("foo", "bar", conditional_set=ConditionalSet.ONLY_IF_EXISTS, expiry=Expiry(ExpiryType.SEC, 5))
        Args:
            key (str): the key to store.
            value (str): the value to store with the given key.
            conditional_set (Union[ConditionalSet, None], optional): set the key only if the given condition is met.
                Equivalent to [`XX` | `NX`] in the Redis API. Defaults to None.
            expiry (Union[Expiry, None], optional): set expiriation to the given key.
                Equivalent to [`EX` | `PX` | `EXAT` | `PXAT` | `KEEPTTL`] in the Redis API. Defaults to None.
            return_old_value (bool, optional): Return the old string stored at key, or None if key did not exist.
                An error is returned and SET aborted if the value stored at key is not a string.
                Equivalent to `GET` in the Redis API. Defaults to False.
        Returns:
            TRESULT:
                If the value is successfully set, return OK.
                If value isn't set because of only_if_exists or only_if_does_not_exist conditions, return None.
                If return_old_value is set, return the old value as a string.
        """
        args = [key, value]
        if conditional_set:
            if conditional_set == ConditionalSet.ONLY_IF_EXISTS:
                args.append("XX")
            if conditional_set == ConditionalSet.ONLY_IF_DOES_NOT_EXIST:
                args.append("NX")
        if return_old_value:
            args.append("GET")
        if expiry is not None:
            args.extend(expiry.get_cmd_args())
        return await self._execute_command(RequestType.SetString, args)

    async def get(self, key: str) -> Optional[str]:
        """Get the value associated with the given key, or null if no such value exists.
         See https://redis.io/commands/get/ for details.

        Args:
            key (str): the key to retrieve from the database

        Returns:
            Union[str, None]: If the key exists, returns the value of the key as a string. Otherwise, return None.
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

    async def incrbyfloat(self, key: str, amount: float) -> str:
        """Increment the string representing a floating point number stored at `key` by `amount`.
           By using a negative increment value, the value stored at the `key` is decremented.
           If the key does not exist, it is set to 0 before performing the operation.
           See https://redis.io/commands/incrbyfloat/ for more details.

        Args:
          key (str): The key to increment it's value.
          amount (float) : The amount to increment.

          Returns:
              str: The value of key after the increment. An error is returned if the key contains a value
              of the wrong type.
        """
        return cast(
            str,
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
