# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
from typing import List, Optional, Type, Union, get_args

from glide_shared.commands.command_args import Limit, OrderBy
from glide_shared.constants import TEncodable


@dataclass
class PubSubMsg:
    """
    Describes the incoming pubsub message

    Attributes:
        message (TEncodable): Incoming message.
        channel (TEncodable): Name of an channel that triggered the message.
        pattern (Optional[TEncodable]): Pattern that triggered the message.
    """

    message: TEncodable
    channel: TEncodable
    pattern: Optional[TEncodable]


class ConditionalChange(Enum):
    """
    A condition to the `SET`, `ZADD` and `GEOADD` commands.
    """

    ONLY_IF_EXISTS = "XX"
    """ Only update key / elements that already exist. Equivalent to `XX` in the Valkey API. """

    ONLY_IF_DOES_NOT_EXIST = "NX"
    """ Only set key / add elements that does not already exist. Equivalent to `NX` in the Valkey API. """


class HashFieldConditionalChange(Enum):
    """
    Field conditional change options for HSETEX command.
    """

    ONLY_IF_ALL_EXIST = "FXX"
    """ Only set fields if all of them already exist. Equivalent to `FXX` in the Valkey API. """

    ONLY_IF_NONE_EXIST = "FNX"
    """ Only set fields if none of them already exist. Equivalent to `FNX` in the Valkey API. """


@dataclass
class OnlyIfEqual:
    """
    Change condition to the `SET` command,
    For additional conditonal options see ConditionalChange

    - comparison_value - value to compare to the current value of a key.

    If comparison_value is equal to the key, it will overwrite the value of key to the new provided value
    Equivalent to the IFEQ comparison-value in the Valkey API
    """

    comparison_value: TEncodable


class ExpiryType(Enum):
    """
    SET option: The type of the expiry.
    """

    SEC = 0, Union[int, timedelta]
    """
    Set the specified expire time, in seconds. Equivalent to `EX` in the Valkey API.
    """

    MILLSEC = 1, Union[int, timedelta]
    """
    Set the specified expire time, in milliseconds. Equivalent to `PX` in the Valkey API.
    """

    UNIX_SEC = 2, Union[int, datetime]
    """
    Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Valkey API.
    """

    UNIX_MILLSEC = 3, Union[int, datetime]
    """
    Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the Valkey API.
    """

    KEEP_TTL = 4, Type[None]
    """
    Retain the time to live associated with the key. Equivalent to `KEEPTTL` in the Valkey API.
    """


class ExpiryTypeGetEx(Enum):
    """
    GetEx option: The type of the expiry.
    """

    SEC = 0, Union[int, timedelta]
    """ Set the specified expire time, in seconds. Equivalent to `EX` in the Valkey API. """

    MILLSEC = 1, Union[int, timedelta]
    """ Set the specified expire time, in milliseconds. Equivalent to `PX` in the Valkey API. """

    UNIX_SEC = 2, Union[int, datetime]
    """ Set the specified Unix time at which the key will expire, in seconds. Equivalent to `EXAT` in the Valkey API. """

    UNIX_MILLSEC = 3, Union[int, datetime]
    """ Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to `PXAT` in the Valkey API. """

    PERSIST = 4, Type[None]
    """ Remove the time to live associated with the key. Equivalent to `PERSIST` in the Valkey API. """


class InfoSection(Enum):
    """
    INFO option: a specific section of information:

    When no parameter is provided, the default option is assumed.
    """

    SERVER = "server"
    """ General information about the server """

    CLIENTS = "clients"
    """ Client connections section """

    MEMORY = "memory"
    """ Memory consumption related information """

    PERSISTENCE = "persistence"
    """ RDB and AOF related information """

    STATS = "stats"
    """ General statistics """

    REPLICATION = "replication"
    """ Master/replica replication information """

    CPU = "cpu"
    """ CPU consumption statistics """

    COMMAND_STATS = "commandstats"
    """ Valkey command statistics """

    LATENCY_STATS = "latencystats"
    """ Valkey command latency percentile distribution statistics """

    SENTINEL = "sentinel"
    """ Valkey Sentinel section (only applicable to Sentinel instances) """

    CLUSTER = "cluster"
    """ Valkey Cluster section """

    MODULES = "modules"
    """ Modules section """

    KEYSPACE = "keyspace"
    """ Database related statistics """

    ERROR_STATS = "errorstats"
    """ Valkey error statistics """

    ALL = "all"
    """ Return all sections (excluding module generated ones) """

    DEFAULT = "default"
    """ Return only the default set of sections """

    EVERYTHING = "everything"
    """ Includes all and modules """


class ExpireOptions(Enum):
    """
    EXPIRE option: options for setting key expiry.
    """

    HasNoExpiry = "NX"
    """ Set expiry only when the key has no expiry (Equivalent to "NX" in Valkey). """

    HasExistingExpiry = "XX"
    """ Set expiry only when the key has an existing expiry (Equivalent to "XX" in Valkey). """

    NewExpiryGreaterThanCurrent = "GT"
    """
    Set expiry only when the new expiry is greater than the current one (Equivalent to "GT" in Valkey).
    """

    NewExpiryLessThanCurrent = "LT"
    """
    Set expiry only when the new expiry is less than the current one (Equivalent to "LT" in Valkey).
    """


class UpdateOptions(Enum):
    """
    Options for updating elements of a sorted set key.
    """

    LESS_THAN = "LT"
    """ Only update existing elements if the new score is less than the current score. """

    GREATER_THAN = "GT"
    """ Only update existing elements if the new score is greater than the current score. """


class ExpirySet:
    """
    SET option: Represents the expiry type and value to be executed with "SET" command.

    Attributes:
        cmd_arg (str): The expiry type.
        value (str): The value for the expiry type.
    """

    def __init__(
        self,
        expiry_type: ExpiryType,
        value: Optional[Union[int, datetime, timedelta]],
    ) -> None:
        self.set_expiry_type_and_value(expiry_type, value)

    def __eq__(self, other: "object") -> bool:
        if not isinstance(other, ExpirySet):
            return NotImplemented
        return self.expiry_type == other.expiry_type and self.value == other.value

    def set_expiry_type_and_value(
        self, expiry_type: ExpiryType, value: Optional[Union[int, datetime, timedelta]]
    ):
        """
        Args:
            expiry_type (ExpiryType): The expiry type.
            value (Optional[Union[int, datetime, timedelta]]): The value of the expiration type. The type of expiration
                determines the type of expiration value:

                    - SEC: Union[int, timedelta]
                    - MILLSEC: Union[int, timedelta]
                    - UNIX_SEC: Union[int, datetime]
                    - UNIX_MILLSEC: Union[int, datetime]
                    - KEEP_TTL: Type[None]
        """
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


class ExpiryGetEx:
    """
    GetEx option: Represents the expiry type and value to be executed with "GetEx" command.

    Attributes:
        cmd_arg (str): The expiry type.
        value (str): The value for the expiry type.
    """

    def __init__(
        self,
        expiry_type: ExpiryTypeGetEx,
        value: Optional[Union[int, datetime, timedelta]],
    ) -> None:
        self.set_expiry_type_and_value(expiry_type, value)

    def set_expiry_type_and_value(
        self,
        expiry_type: ExpiryTypeGetEx,
        value: Optional[Union[int, datetime, timedelta]],
    ):
        """
        Args:
            expiry_type (ExpiryType): The expiry type.
            value (Optional[Union[int, datetime, timedelta]]): The value of the expiration type. The type of expiration
                determines the type of expiration value:

                    - SEC: Union[int, timedelta]
                    - MILLSEC: Union[int, timedelta]
                    - UNIX_SEC: Union[int, datetime]
                    - UNIX_MILLSEC: Union[int, datetime]
                    - PERSIST: Type[None]
        """
        if not isinstance(value, get_args(expiry_type.value[1])):
            raise ValueError(
                f"The value of {expiry_type} should be of type {expiry_type.value[1]}"
            )
        self.expiry_type = expiry_type
        if self.expiry_type == ExpiryTypeGetEx.SEC:
            self.cmd_arg = "EX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds())
        elif self.expiry_type == ExpiryTypeGetEx.MILLSEC:
            self.cmd_arg = "PX"
            if isinstance(value, timedelta):
                value = int(value.total_seconds() * 1000)
        elif self.expiry_type == ExpiryTypeGetEx.UNIX_SEC:
            self.cmd_arg = "EXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp())
        elif self.expiry_type == ExpiryTypeGetEx.UNIX_MILLSEC:
            self.cmd_arg = "PXAT"
            if isinstance(value, datetime):
                value = int(value.timestamp() * 1000)
        elif self.expiry_type == ExpiryTypeGetEx.PERSIST:
            self.cmd_arg = "PERSIST"
        self.value = str(value) if value else None

    def get_cmd_args(self) -> List[str]:
        return [self.cmd_arg] if self.value is None else [self.cmd_arg, self.value]


class InsertPosition(Enum):
    BEFORE = "BEFORE"
    AFTER = "AFTER"


class FlushMode(Enum):
    """
    Defines flushing mode for:

    `FLUSHALL` command and `FUNCTION FLUSH` command.

    See [FLUSHAL](https://valkey.io/commands/flushall/) and [FUNCTION-FLUSH](https://valkey.io/commands/function-flush/)
    for details

    SYNC was introduced in version 6.2.0.
    """

    ASYNC = "ASYNC"
    SYNC = "SYNC"


class FunctionRestorePolicy(Enum):
    """
    Options for the FUNCTION RESTORE command.
    """

    APPEND = "APPEND"
    """ Appends the restored libraries to the existing libraries and aborts on collision. This is the default policy. """

    FLUSH = "FLUSH"
    """ Deletes all existing libraries before restoring the payload. """

    REPLACE = "REPLACE"
    """
    Appends the restored libraries to the existing libraries, replacing any existing ones in case
    of name collisions. Note that this policy doesn't prevent function name collisions, only libraries.
    """


def _build_sort_args(
    key: TEncodable,
    by_pattern: Optional[TEncodable] = None,
    limit: Optional[Limit] = None,
    get_patterns: Optional[List[TEncodable]] = None,
    order: Optional[OrderBy] = None,
    alpha: Optional[bool] = None,
    store: Optional[TEncodable] = None,
) -> List[TEncodable]:
    args = [key]

    if by_pattern:
        args.extend(["BY", by_pattern])

    if limit:
        args.extend(["LIMIT", str(limit.offset), str(limit.count)])

    if get_patterns:
        for pattern in get_patterns:
            args.extend(["GET", pattern])

    if order:
        args.append(order.value)

    if alpha:
        args.append("ALPHA")

    if store:
        args.extend(["STORE", store])

    return args
