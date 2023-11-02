import threading
from typing import List, Mapping, Optional, Tuple, Union

from pybushka.async_commands.core import (
    ConditionalSet,
    ExpireOptions,
    ExpirySet,
    InfoSection,
)
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

    def hincrby(self, key: str, field: str, amount: int):
        """Increment or decrement the value of a `field` in the hash stored at `key` by `amount`.
        By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
        If `field` or `key` does not exist, it is set to 0 before performing the operation.
        See https://redis.io/commands/hincrby/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field in the hash stored at `key` to increment or decrement its value.
            amount (int): The amount by which to increment or decrement the field's value.
                Use a negative value to decrement.

        Command response:
            int: The value of the specified field in the hash stored at `key` after the increment or decrement.
                The transaction fails if `key` holds a value of an incorrect type (not a string) or if it contains a string
                that cannot be represented as an integer.

        """
        self.append_command(RequestType.HashIncrBy, [key, field, str(amount)])

    def hincrbyfloat(self, key: str, field: str, amount: float):
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
            str: The value of the specified field in the hash stored at `key` after the increment as a string.
                The transaction fails if `key` contains a value of the wrong type or the current field content is not
                parsable as a double precision floating point number.
        """
        self.append_command(RequestType.HashIncrByFloat, [key, field, str(amount)])

    def hexists(self, key: str, field: str):
        """Check if a field exists in the hash stored at `key`.
        See https://redis.io/commands/hexists/ for more details.

        Args:
            key (str): The key of the hash.
            field (str): The field to check in the hash stored at `key`.

        Command response:
            int: Returns 1 if the hash contains the specified field. If the hash does not contain the field,
                or if the key does not exist, it returns 0.
                If `key` holds a value that is not a hash, the transaction fails with an error.
        """
        self.append_command(RequestType.HashExists, [key, field])

    def client_getname(self):
        """
        Get the name of the connection on which the transaction is being executed.
        See https://redis.io/commands/client-getname/ for more details.

        Command response:
            Optional[str]: Returns the name of the client connection as a string if a name is set,
            or None if no name is assigned.
        """
        self.append_command(RequestType.ClientGetName, [])

    def hgetall(self, key: str):
        """Returns all fields and values of the hash stored at `key`.
        See https://redis.io/commands/hgetall/ for details.

        Args:
            key (str): The key of the hash.

        Command response:
            List[str]: A list of fields and their values stored in the hash. Every field name in the list is followed by
            its value. If `key` does not exist, it returns an empty list.
            If `key` holds a value that is not a hash , the transaction fails.
        """
        self.append_command(RequestType.HashGetAll, [key]),

    def hmget(self, key: str, fields: List[str]):
        """Retrieve the values associated with specified fields in the hash stored at `key`.
        See https://redis.io/commands/hmget/ for details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields in the hash stored at `key` to retrieve from the database.

        Command response:
            List[Optional[str]]: A list of values associated with the given fields, in the same order as they are requested.
            For every field that does not exist in the hash, a null value is returned.
            If the key does not exist, it is treated as an empty hash, and the function returns a list of null values.
            If `key` holds a value that is not a hash, the transaction fails.
        """
        self.append_command(RequestType.HashMGet, [key] + fields)

    def hdel(self, key: str, fields: List[str]):
        """Remove specified fields from the hash stored at `key`.
        See https://redis.io/commands/hdel/ for more details.

        Args:
            key (str): The key of the hash.
            fields (List[str]): The list of fields to remove from the hash stored at `key`.

        Command response:
            int: The number of fields that were removed from the hash, excluding specified but non-existing fields.
            If the key does not exist, it is treated as an empty hash, returns 0.
            If `key` holds a value that is not a hash , the transaction fails.
        """
        self.append_command(RequestType.HashDel, [key] + fields)

    def lpush(self, key: str, elements: List[str]):
        """Insert all the specified values at the head of the list stored at `key`.
        `elements` are inserted one after the other to the head of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/lpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the head of the list stored at `key`.

        Command response:
            int: The length of the list after the push operations.
                If `key` holds a value that is not a list, the transaction fails.
        """
        self.append_command(RequestType.LPush, [key] + elements)

    def lpop(self, key: str, count: Optional[int] = None):
        """Remove and return the first elements of the list stored at `key`.
        By default, the command pops a single element from the beginning of the list.
        When `count` is provided, the command pops up to `count` elements, depending on the list's length.
        See https://redis.io/commands/lpop/ for details.

        Args:
            key (str): The key of the list.
            count (Optional[int]): The count of elements to pop from the list. Default is to pop a single element.

        Command response:
            Optional[Union[str, List[str]]]: The value of the first element if `count` is not provided.
            If `count` is provided, a list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.
            If `key` holds a value that is not a list, the transaction fails.
        """

        args: List[str] = [key] if count is None else [key, str(count)]
        self.append_command(RequestType.LPop, args)

    def lrange(self, key: str, start: int, end: int):
        """Retrieve the specified elements of the list stored at `key` within the given range.
        The offsets `start` and `end` are zero-based indexes, with 0 being the first element of the list, 1 being the next
        element and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list,
        with -1 being the last element of the list, -2 being the penultimate, and so on.
        See https://redis.io/commands/lrange/ for details.

        Args:
            key (str): The key of the list.
            start (int): The starting point of the range.
            end (int): The end of the range.

        Command response:
            List[str]: A list of elements within the specified range.
            If `start` exceeds the `end` of the list, or if `start` is greater than `end`, an empty list will be returned.
            If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
            If `key` does not exist an empty list will be returned.
            If `key` holds a value that is not a list, the transaction fails.
        """

        self.append_command(RequestType.LRange, [key, str(start), str(end)])

    def rpush(self, key: str, elements: List[str]):
        """Inserts all the specified values at the tail of the list stored at `key`.
        `elements` are inserted one after the other to the tail of the list, from the leftmost element
        to the rightmost element. If `key` does not exist, it is created as empty list before performing the push operations.
        See https://redis.io/commands/rpush/ for more details.

        Args:
            key (str): The key of the list.
            elements (List[str]): The elements to insert at the tail of the list stored at `key`.

        Command response:
            int: The length of the list after the push operations.
                If `key` holds a value that is not a list, the transaction fails.
        """
        self.append_command(RequestType.RPush, [key] + elements)

    def rpop(self, key: str, count: Optional[int] = None):
        """Removes and returns the last elements of the list stored at `key`.
        By default, the command pops a single element from the end of the list.
        When `count` is provided, the command pops up to `count` elements, depending on the list's length.
        See https://redis.io/commands/rpop/ for details.

        Args:
            key (str): The key of the list.
            count (Optional[int]): The count of elements to pop from the list. Default is to pop a single element.

        Command response:
            Optional[Union[str, List[str]]: The value of the last element if `count` is not provided.
            If `count` is provided, a list of popped elements will be returned depending on the list's length.
            If `key` does not exist, None will be returned.
            If `key` holds a value that is not a list, the transaction fails.
        """

        args: List[str] = [key] if count is None else [key, str(count)]
        self.append_command(RequestType.RPop, args)

    def sadd(self, key: str, members: List[str]):
        """Add specified members to the set stored at `key`.
        Specified members that are already a member of this set are ignored.
        If `key` does not exist, a new set is created before adding `members`.
        See https://redis.io/commands/sadd/ for more details.

        Args:
            key (str): The key where members will be added to its set.
            members (List[str]): A list of members to add to the set stored at key.

        Command response:
            int: The number of members that were added to the set, excluding members already present.
                If `key` holds a value that is not a set, the transaction fails.
        """
        self.append_command(RequestType.SAdd, [key] + members)

    def srem(self, key: str, members: List[str]):
        """Remove specified members from the set stored at `key`.
        Specified members that are not a member of this set are ignored.
        See https://redis.io/commands/srem/ for details.

        Args:
            key (str): The key from which members will be removed.
            members (List[str]): A list of members to remove from the set stored at key.

        Commands response:
            int: The number of members that were removed from the set, excluding non-existing members.
                If `key` does not exist, it is treated as an empty set and this command returns 0.
                If `key` holds a value that is not a set, the transaction fails.
        """
        self.append_command(RequestType.SRem, [key] + members)

    def smembers(self, key: str):
        """Retrieve all the members of the set value stored at `key`.
        See https://redis.io/commands/smembers/ for details.

        Args:
            key (str): The key from which to retrieve the set members.

        Commands response:
            List[str]: A list of all members of the set.
                If `key` does not exist an empty list will be returned.
                If `key` holds a value that is not a set, the transaction fails.
        """
        self.append_command(RequestType.SMembers, [key])

    def scard(self, key: str):
        """Retrieve the set cardinality (number of elements) of the set stored at `key`.
        See https://redis.io/commands/scard/ for details.

        Args:
            key (str): The key from which to retrieve the number of set members.

        Commands response:
            int: The cardinality (number of elements) of the set, or 0 if the key does not exist.
                If `key` holds a value that is not a set, the transaction fails.
        """
        self.append_command(RequestType.SCard, [key])

    def ltrim(self, key: str, start: int, end: int):
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

        Commands response:
            TOK: A simple "OK" response.
                If `start` exceeds the end of the list, or if `start` is greater than `end`, the result will be an empty list
                (which causes `key` to be removed).
                If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
                f `key` does not exist, the response will be "OK" without changes to the database.
                If `key` holds a value that is not a list, the transaction fails.
        """
        self.append_command(RequestType.LTrim, [key, str(start), str(end)])

    def lrem(self, key: str, count: int, element: str):
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

        Commands response:
            int: The number of removed elements.
                If `key` does not exist, 0 is returned.
                If `key` holds a value that is not a list, the transaction fails with an error.
        """
        self.append_command(RequestType.LRem, [key, str(count), element])

    def llen(self, key: str):
        """Get the length of the list stored at `key`.
        See https://redis.io/commands/llen/ for details.

        Args:
            key (str): The key of the list.

        Commands response:
            int: The length of the list at the specified key.
                If `key` does not exist, it is interpreted as an empty list and 0 is returned.
                If `key` holds a value that is not a list, the transaction fails with an error.
        """
        self.append_command(RequestType.LLen, [key])

    def exists(self, keys: List[str]):
        """Returns the number of keys in `keys` that exist in the database.
        See https://redis.io/commands/exists/ for more details.

        Args:
            keys (List[str]): The list of keys to check.

        Commands response:
            int: The number of keys that exist. If the same existing key is mentioned in `keys` multiple times,
                it will be counted multiple times.
        """
        self.append_command(RequestType.Exists, keys)

    def unlink(self, keys: List[str]):
        """Unlink (delete) multiple keys from the database.
        A key is ignored if it does not exist.
        This command, similar to DEL, removes specified keys and ignores non-existent ones.
        However, this command does not block the server, while [DEL](https://redis.io/commands/del) does.
        See https://redis.io/commands/unlink/ for more details.

        Args:
            keys (List[str]): The list of keys to unlink.

        Commands response:
            int: The number of keys that were unlinked.
        """
        self.append_command(RequestType.Unlink, keys)

    def expire(self, key: str, seconds: int, option: Optional[ExpireOptions] = None):
        """
        Sets a timeout on `key` in seconds. After the timeout has expired, the key will automatically be deleted.
        If `key` already has an existing expire set, the time to live is updated to the new value.
        If `seconds` is a non-positive number, the key will be deleted rather than expired.
        The timeout will only be cleared by commands that delete or overwrite the contents of `key`.
        See https://redis.io/commands/expire/ for more details.

        Args:
            key (str): The key to set a timeout on.
            seconds (int): The timeout in seconds.
            option (Optional[ExpireOptions]): The expire option.

        Commands response:
            int: 1 if the timeout was set, 0 if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        """
        args: List[str] = (
            [key, str(seconds)] if option is None else [key, str(seconds), option.value]
        )
        self.append_command(RequestType.Expire, args)

    def expireat(
        self, key: str, unix_seconds: int, option: Optional[ExpireOptions] = None
    ):
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

        Commands response:
            int: 1 if the timeout was set, 0 if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).
        """
        args = (
            [key, str(unix_seconds)]
            if option is None
            else [key, str(unix_seconds), option.value]
        )
        self.append_command(RequestType.ExpireAt, args)

    def pexpire(
        self, key: str, milliseconds: int, option: Optional[ExpireOptions] = None
    ):
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

        Commands response:
            int: 1 if the timeout was set, 0 if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).

        """
        args = (
            [key, str(milliseconds)]
            if option is None
            else [key, str(milliseconds), option.value]
        )
        self.append_command(RequestType.PExpire, args)

    def pexpireat(
        self, key: str, unix_milliseconds: int, option: Optional[ExpireOptions] = None
    ):
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

        Commands response:
            int: 1 if the timeout was set, 0 if the timeout was not set (e.g., the key doesn't exist or the operation is
                skipped due to the provided arguments).
        """
        args = (
            [key, str(unix_milliseconds)]
            if option is None
            else [key, str(unix_milliseconds), option.value]
        )
        self.append_command(RequestType.PExpireAt, args)

    def ttl(self, key: str):
        """
        Returns the remaining time to live of `key` that has a timeout.
        See https://redis.io/commands/ttl/ for more details.

        Args:
            key (str): The key to return its timeout.

        Commands response:
            int: TTL in seconds, -2 if `key` does not exist or -1 if `key` exists but has no associated expire.
        """
        self.append_command(RequestType.TTL, [key])


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
