# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional, Union

from glide_shared.constants import TEncodable


class StreamTrimOptions(ABC):
    """
    Abstract base class for stream trim options.

    Attributes:
        exact (bool): If `true`, the stream will be trimmed exactly.
            Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
        threshold (Union[TEncodable, int]): Threshold for trimming.
        method (str): Method for trimming (e.g., MINID, MAXLEN).
        limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
            Note: If `exact` is set to `True`, `limit` cannot be specified.
    """

    @abstractmethod
    def __init__(
        self,
        exact: bool,
        threshold: Union[TEncodable, int],
        method: str,
        limit: Optional[int] = None,
    ):
        """
        Initialize stream trim options.
        """
        if exact and limit:
            raise ValueError(
                "If `exact` is set to `True`, `limit` cannot be specified."
            )
        self.exact = exact
        self.threshold = threshold
        self.method = method
        self.limit = limit

    def to_args(self) -> List[str]:
        """
        Convert options to arguments for the command.

        Returns:
            List[str]: List of arguments for the command.
        """
        option_args = [
            self.method,
            "=" if self.exact else "~",
            str(self.threshold),
        ]
        if self.limit is not None:
            option_args.extend(["LIMIT", str(self.limit)])
        return option_args


class TrimByMinId(StreamTrimOptions):
    """
    Stream trim option to trim by minimum ID.

    Attributes:
        exact (bool): If `true`, the stream will be trimmed exactly.
            Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
        threshold (TEncodable): Threshold for trimming by minimum ID.
        limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
            Note: If `exact` is set to `True`, `limit` cannot be specified.
    """

    def __init__(self, exact: bool, threshold: TEncodable, limit: Optional[int] = None):
        """
        Initialize trim option by minimum ID.
        """
        super().__init__(exact, threshold, "MINID", limit)


class TrimByMaxLen(StreamTrimOptions):
    """
    Stream trim option to trim by maximum length.

    Attributes:
        exact (bool): If `true`, the stream will be trimmed exactly.
            Otherwise the stream will be trimmed in a near-exact manner, which is more efficient.
        threshold (int): Threshold for trimming by maximum length.
        limit (Optional[int]): Max number of entries to be trimmed. Defaults to None.
            Note: If `exact` is set to `True`, `limit` cannot be specified.
    """

    def __init__(self, exact: bool, threshold: int, limit: Optional[int] = None):
        """
        Initialize trim option by maximum length.
        """
        super().__init__(exact, threshold, "MAXLEN", limit)


class StreamAddOptions:
    """
    Options for adding entries to a stream.

    Attributes:
        id (Optional[TEncodable]): ID for the new entry. If set, the new entry will be added with this ID. If not
            specified, '*' is used.
        make_stream (bool, optional): If set to False, a new stream won't be created if no stream matches the given key.
        trim (Optional[StreamTrimOptions]): If set, the add operation will also trim the older entries in the stream.
            See `StreamTrimOptions`.
    """

    def __init__(
        self,
        id: Optional[TEncodable] = None,
        make_stream: bool = True,
        trim: Optional[StreamTrimOptions] = None,
    ):
        """
        Initialize stream add options.
        """
        self.id = id
        self.make_stream = make_stream
        self.trim = trim

    def to_args(self) -> List[TEncodable]:
        """
        Convert options to arguments for the command.

        Returns:
            List[str]: List of arguments for the command.
        """
        option_args: List[TEncodable] = []
        if not self.make_stream:
            option_args.append("NOMKSTREAM")
        if self.trim:
            option_args.extend(self.trim.to_args())
        option_args.append(self.id if self.id else "*")

        return option_args


class StreamRangeBound(ABC):
    """
    Abstract Base Class used in the `XPENDING`, `XRANGE`, and `XREVRANGE` commands to specify the starting and ending
    range bound for the stream search by stream entry ID.
    """

    @abstractmethod
    def to_arg(self) -> TEncodable:
        """
        Returns the stream range bound as a string argument to be used in the `XRANGE` or `XREVRANGE` commands.
        """
        pass


class MinId(StreamRangeBound):
    """
    Stream ID boundary used to specify the minimum stream entry ID. Can be used in the `XRANGE` or `XREVRANGE` commands
    to get the first stream ID.
    """

    MIN_RANGE_VALKEY_API = "-"

    def to_arg(self) -> str:
        return self.MIN_RANGE_VALKEY_API


class MaxId(StreamRangeBound):
    """
    Stream ID boundary used to specify the maximum stream entry ID. Can be used in the `XRANGE` or `XREVRANGE` commands
    to get the last stream ID.
    """

    MAX_RANGE_VALKEY_API = "+"

    def to_arg(self) -> str:
        return self.MAX_RANGE_VALKEY_API


class IdBound(StreamRangeBound):
    """
    Inclusive (closed) stream ID boundary used to specify a range of IDs to search. Stream ID bounds can be complete
    with a timestamp and sequence number separated by a dash ("-"), for example "1526985054069-0". Stream ID bounds can
    also be incomplete, with just a timestamp.

    Attributes:
        stream_id (str): The stream ID.
    """

    @staticmethod
    def from_timestamp(timestamp: int) -> IdBound:
        """
        Creates an incomplete stream ID boundary without the sequence number for a range search.

        Args:
            timestamp (int): The stream ID timestamp.
        """
        return IdBound(str(timestamp))

    def __init__(self, stream_id: TEncodable):
        """
        Creates a stream ID boundary for a range search.
        """
        self.stream_id = stream_id

    def to_arg(self) -> TEncodable:
        return self.stream_id


class ExclusiveIdBound(StreamRangeBound):
    """
    Exclusive (open) stream ID boundary used to specify a range of IDs to search. Stream ID bounds can be complete with
    a timestamp and sequence number separated by a dash ("-"), for example "1526985054069-0". Stream ID bounds can also
    be incomplete, with just a timestamp.

    Since: Valkey version 6.2.0.

    Attributes:
        stream_id (TEncodable): The stream ID.
    """

    EXCLUSIVE_BOUND_VALKEY_API = "("

    @staticmethod
    def from_timestamp(timestamp: int) -> ExclusiveIdBound:
        """
        Creates an incomplete stream ID boundary without the sequence number for a range search.

        Args:
            timestamp (int): The stream ID timestamp.
        """
        return ExclusiveIdBound(str(timestamp))

    def __init__(self, stream_id: TEncodable):
        """
        Creates a stream ID boundary for a range search.
        """
        if isinstance(stream_id, bytes):
            stream_id = stream_id.decode("utf-8")
        self.stream_id = f"{self.EXCLUSIVE_BOUND_VALKEY_API}{stream_id}"

    def to_arg(self) -> TEncodable:
        return self.stream_id


class StreamReadOptions:
    """
    Options for reading entries from streams. Can be used as an optional argument to `XREAD`.

    Attributes:
        block_ms (Optional[int]): If provided, the request will be blocked for the set amount of milliseconds or
            until the server has the required number of entries. Equivalent to `BLOCK` in the Valkey API.
        count (Optional[int]): The maximum number of elements requested. Equivalent to `COUNT` in the Valkey API.
    """

    READ_COUNT_VALKEY_API = "COUNT"
    READ_BLOCK_VALKEY_API = "BLOCK"

    def __init__(self, block_ms: Optional[int] = None, count: Optional[int] = None):
        self.block_ms = block_ms
        self.count = count

    def to_args(self) -> List[TEncodable]:
        """
        Returns the options as a list of string arguments to be used in the `XREAD` command.

        Returns:
            List[TEncodable]: The options as a list of arguments for the `XREAD` command.
        """
        args: List[TEncodable] = []
        if self.block_ms is not None:
            args.extend([self.READ_BLOCK_VALKEY_API, str(self.block_ms)])

        if self.count is not None:
            args.extend([self.READ_COUNT_VALKEY_API, str(self.count)])

        return args


class StreamGroupOptions:
    """
    Options for creating stream consumer groups. Can be used as an optional argument to `XGROUP CREATE`.

    Attributes:
        make_stream (bool): If set to True and the stream doesn't exist, this creates a new stream with a
            length of 0.
        entries_read: (Optional[int]): A value representing the number of stream entries already read by the
            group. This option can only be specified if you are using Valkey version 7.0.0 or above.
    """

    MAKE_STREAM_VALKEY_API = "MKSTREAM"
    ENTRIES_READ_VALKEY_API = "ENTRIESREAD"

    def __init__(self, make_stream: bool = False, entries_read: Optional[int] = None):
        self.make_stream = make_stream
        self.entries_read = entries_read

    def to_args(self) -> List[TEncodable]:
        """
        Returns the options as a list of string arguments to be used in the `XGROUP CREATE` command.

        Returns:
            List[TEncodable]: The options as a list of arguments for the `XGROUP CREATE` command.
        """
        args: List[TEncodable] = []
        if self.make_stream is True:
            args.append(self.MAKE_STREAM_VALKEY_API)

        if self.entries_read is not None:
            args.extend([self.ENTRIES_READ_VALKEY_API, str(self.entries_read)])

        return args


class StreamReadGroupOptions(StreamReadOptions):
    """
    Options for reading entries from streams using a consumer group. Can be used as an optional argument to
    `XREADGROUP`.

    Attributes:
        no_ack (bool): If set, messages are not added to the Pending Entries List (PEL). This is equivalent to
            acknowledging the message when it is read. Equivalent to `NOACK` in the Valkey API.
        block_ms (Optional[int]): If provided, the request will be blocked for the set amount of milliseconds or
            until the server has the required number of entries. Equivalent to `BLOCK` in the Valkey API.
        count (Optional[int]): The maximum number of elements requested. Equivalent to `COUNT` in the Valkey API.
    """

    READ_NOACK_VALKEY_API = "NOACK"

    def __init__(
        self, no_ack=False, block_ms: Optional[int] = None, count: Optional[int] = None
    ):
        super().__init__(block_ms=block_ms, count=count)
        self.no_ack = no_ack

    def to_args(self) -> List[TEncodable]:
        """
        Returns the options as a list of string arguments to be used in the `XREADGROUP` command.

        Returns:
            List[TEncodable]: The options as a list of arguments for the `XREADGROUP` command.
        """
        args: List[TEncodable] = super().to_args()
        if self.no_ack:
            args.append(self.READ_NOACK_VALKEY_API)

        return args


class StreamPendingOptions:
    """
    Options for `XPENDING` that can be used to filter returned items by minimum idle time and consumer name.

    Attributes:
        min_idle_time_ms (Optional[int]): Filters pending entries by their minimum idle time in milliseconds. This
            option can only be specified if you are using Valkey version 6.2.0 or above.
        consumer_name (Optional[TEncodable]): Filters pending entries by consumer name.
    """

    IDLE_TIME_VALKEY_API = "IDLE"

    def __init__(
        self,
        min_idle_time_ms: Optional[int] = None,
        consumer_name: Optional[TEncodable] = None,
    ):
        self.min_idle_time = min_idle_time_ms
        self.consumer_name = consumer_name


class StreamClaimOptions:
    """
    Options for `XCLAIM`.

    Attributes:
        idle (Optional[int]): Set the idle time (last time it was delivered) of the message in milliseconds. If idle
            is not specified, an idle of `0` is assumed, that is, the time count is reset because the message now has a
            new owner trying to process it.
        idle_unix_time (Optional[int]): This is the same as idle but instead of a relative amount of milliseconds,
            it sets the idle time to a specific Unix time (in milliseconds). This is useful in order to rewrite the AOF
            file generating `XCLAIM` commands.
        retry_count (Optional[int]): Set the retry counter to the specified value. This counter is incremented every
            time a message is delivered again. Normally `XCLAIM` does not alter this counter, which is just served to
            clients when the `XPENDING` command is called: this way clients can detect anomalies, like messages that
            are never processed for some reason after a big number of delivery attempts.
        is_force (Optional[bool]): Creates the pending message entry in the PEL even if certain specified IDs are not
            already in the PEL assigned to a different client. However, the message must exist in the stream, otherwise
            the IDs of non-existing messages are ignored.
    """

    IDLE_VALKEY_API = "IDLE"
    TIME_VALKEY_API = "TIME"
    RETRY_COUNT_VALKEY_API = "RETRYCOUNT"
    FORCE_VALKEY_API = "FORCE"
    JUST_ID_VALKEY_API = "JUSTID"

    def __init__(
        self,
        idle: Optional[int] = None,
        idle_unix_time: Optional[int] = None,
        retry_count: Optional[int] = None,
        is_force: Optional[bool] = False,
    ):
        self.idle = idle
        self.idle_unix_time = idle_unix_time
        self.retry_count = retry_count
        self.is_force = is_force

    def to_args(self) -> List[TEncodable]:
        """
        Converts options for `XCLAIM` into a List.

        Returns:
            List[str]: The options as a list of arguments for the `XCLAIM` command.
        """
        args: List[TEncodable] = []
        if self.idle:
            args.append(self.IDLE_VALKEY_API)
            args.append(str(self.idle))

        if self.idle_unix_time:
            args.append(self.TIME_VALKEY_API)
            args.append(str(self.idle_unix_time))

        if self.retry_count:
            args.append(self.RETRY_COUNT_VALKEY_API)
            args.append(str(self.retry_count))

        if self.is_force:
            args.append(self.FORCE_VALKEY_API)

        return args


def _create_xpending_range_args(
    key: TEncodable,
    group_name: TEncodable,
    start: StreamRangeBound,
    end: StreamRangeBound,
    count: int,
    options: Optional[StreamPendingOptions],
) -> List[TEncodable]:
    args = [key, group_name]
    if options is not None and options.min_idle_time is not None:
        args.extend([options.IDLE_TIME_VALKEY_API, str(options.min_idle_time)])

    args.extend([start.to_arg(), end.to_arg(), str(count)])
    if options is not None and options.consumer_name is not None:
        args.append(options.consumer_name)

    return args
