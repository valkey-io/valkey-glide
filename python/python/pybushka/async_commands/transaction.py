import threading
from typing import List, Mapping, Optional, Tuple, Union

from pybushka.async_commands.core import ConditionalSet, ExpirySet, InfoSection
from pybushka.protobuf.redis_request_pb2 import RequestType


class BaseTransaction:
    """
    Base class encompassing shared commands for both standalone and cluster mode implementations in transaction.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.

    Example:
        transaction = BaseTransaction()
        >>> transaction.set("key", "value")
        >>> transaction.get("key")
        >>> client.exec(transaction)
        [OK , "value"]
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

    def clear(self):
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

    def hset(self, key: str, field_value_map: Mapping[str, str]):
        """Sets the specified fields to their respective values in the hash stored at `key`.
        See https://redis.io/commands/hset/ for more details.

        Args:
            key (str): The key of the hash.
            field_value_map (Mapping[str, str]): A field-value map consisting of fields and their corresponding values
            to be set in the hash stored at the specified key.

        Command response:
            int: The number of fields that were added or modified in the hash.
        """
        field_value_list: List[str] = [key]
        for pair in field_value_map.items():
            field_value_list.extend(pair)
        self.append_command(RequestType.HashSet, field_value_list)

    def hget(self, key: str, field: str):
        """Retrieves the value associated with field in the hash stored at `key`.
        See https://redis.io/commands/hget/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field whose value should be retrieved.

        Command response:
            Optional[str]: The value associated with the specified field in the hash.
            Returns None if the field or key does not exist.
        """
        self.append_command(RequestType.HashGet, [key, field])


class Transaction(BaseTransaction):
    """
    Extends BaseTransaction class for standalone Redis commands that are not supported in Redis cluster mode.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.

    Example:
        transaction = Transaction()
        >>> transaction.set("key", "value")
        >>> transaction.select(1)  # Standalone command
        >>> transaction.get("key")
        >>> client.exec(transaction)
        [OK , OK , None]

    """

    # TODO: add MOVE, SLAVEOF and all SENTINEL commands
    def select(self, index: int):
        """Change the currently selected Redis database.
        See https://redis.io/commands/select/ for details.

        Args:
            index (int): The index of the database to select.

        Command response:
            A simple OK response.
        """
        self.append_command(RequestType.Select, [str(index)])


class ClusterTransaction(BaseTransaction):
    """
    Extends BaseTransaction class for cluster mode commands that are not supported in standalone.

    Command Response:
        The response for each command depends on the executed Redis command. Specific response types
        are documented alongside each method.
    """

    # TODO: add all CLUSTER commands
    pass
